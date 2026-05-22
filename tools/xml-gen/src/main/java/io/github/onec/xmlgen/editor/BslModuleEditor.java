package io.github.onec.xmlgen.editor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Универсальный текстовый редактор BSL-модулей. Работает по строкам, сохраняя:
 * <ul>
 *     <li>оригинальное окончание строк (LF/CRLF) — определяется по первому увиденному;</li>
 *     <li>BOM — через {@link ByteSafeFileHandler};</li>
 *     <li>отступы — клиент сам передаёт правильно отформатированный {@code content}.</li>
 * </ul>
 * <p>
 * Парсинг — текстовый, без полноценного BSL-AST. Опирается на синтаксические маркеры:
 * <pre>
 *     #Область / #КонецОбласти
 *     Процедура / КонецПроцедуры
 *     Функция / КонецФункции
 * </pre>
 * Чувствителен к регистру по 1С-конвенции (ключевые слова — регистр не важен, мы их нормализуем при поиске).
 * <p>
 * Класс умышленно сделан переиспользуемым: одни и те же методы пригодятся в CFE patch-method
 * (генерация переопределений процедур в расширении), template add-help (вставка вызова в форму) и др.
 */
public class BslModuleEditor {

    /** Позиция вставки в область. */
    public enum InsertPosition {
        /** Сразу после открывающего {@code #Область ...}. */
        BEGIN,
        /** Прямо перед закрывающим {@code #КонецОбласти}. */
        END
    }

    /** Найденный диапазон конструкции (Процедура/Функция/Область) — индексы строк включительно. */
    public static final class Range {
        public final int startLine;   // 0-based индекс открывающей строки
        public final int endLine;     // 0-based индекс закрывающей строки
        public final String header;   // полная открывающая строка (без trim)
        public final String name;     // имя конструкции

        public Range(int startLine, int endLine, String header, String name) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.header = header;
            this.name = name;
        }
    }

    private static final Pattern REGION_BEGIN = Pattern.compile(
            "^\\s*#\\s*Область\\s+([A-Za-zА-Яа-яЁё_][A-Za-zА-Яа-яЁё0-9_]*)\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern REGION_END = Pattern.compile(
            "^\\s*#\\s*КонецОбласти\\b.*$",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Pattern PROC_BEGIN = Pattern.compile(
            "^\\s*(?:&[A-Za-zА-Яа-яЁё]+\\s*)?Процедура\\s+([A-Za-zА-Яа-яЁё_][A-Za-zА-Яа-яЁё0-9_]*)\\s*\\(",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
    private static final Pattern PROC_END = Pattern.compile(
            "^\\s*КонецПроцедуры\\s*;?\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);

    private static final Pattern FUNC_BEGIN = Pattern.compile(
            "^\\s*(?:&[A-Za-zА-Яа-яЁё]+\\s*)?Функция\\s+([A-Za-zА-Яа-яЁё_][A-Za-zА-Яа-яЁё0-9_]*)\\s*\\(",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
    private static final Pattern FUNC_END = Pattern.compile(
            "^\\s*КонецФункции\\s*;?\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);

    private static final Pattern RETURN_LINE = Pattern.compile(
            "^\\s*Возврат\\b.*$",
            Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);

    private final Path filePath;
    private final ByteSafeFileHandler handler;
    private final String lineSep;
    private final boolean endsWithNewline;
    private List<String> lines;

    public BslModuleEditor(Path filePath) throws IOException {
        this.filePath = filePath;
        this.handler = ByteSafeFileHandler.open(filePath);
        String content = handler.getContent();
        this.lineSep = detectLineSeparator(content);
        this.endsWithNewline = !content.isEmpty()
                && (content.endsWith("\n") || content.endsWith("\r"));
        this.lines = splitLines(content);
    }

    /** Окончание строк, обнаруженное в исходнике (`\n` или `\r\n`). */
    public String lineSeparator() {
        return lineSep;
    }

    public List<String> lines() {
        return lines;
    }

    public Path filePath() {
        return filePath;
    }

    /** Содержимое файла как одна строка (для тестов/инспекции; не пишет). */
    public String content() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1 || endsWithNewline) {
                sb.append(lineSep);
            }
        }
        return sb.toString();
    }

    /** Записать обратно в файл (BOM сохраняется). */
    public void save() throws IOException {
        handler.writeBack(content());
    }

    // --- Поиск ---

    /** Найти область {@code #Область <name>}. */
    public Optional<Range> findRegion(String regionName) {
        return findInRange(regionName, REGION_BEGIN, REGION_END, "Область", 0, lines.size());
    }

    /** Найти процедуру по имени (на верхнем уровне модуля). */
    public Optional<Range> findProcedure(String name) {
        return findInRange(name, PROC_BEGIN, PROC_END, "Процедура", 0, lines.size());
    }

    /** Найти функцию по имени (на верхнем уровне модуля). */
    public Optional<Range> findFunction(String name) {
        return findInRange(name, FUNC_BEGIN, FUNC_END, "Функция", 0, lines.size());
    }

    private Optional<Range> findInRange(String name, Pattern begin, Pattern end, String kind,
                                        int from, int to) {
        for (int i = from; i < Math.min(to, lines.size()); i++) {
            String line = lines.get(i);
            Matcher m = begin.matcher(line);
            if (m.find()) {
                String foundName = m.group(1);
                if (!equalsIgnoreCaseLocale(foundName, name)) {
                    // не наша конструкция — но всё равно нужно проскочить её до конца
                    int closing = matchEnd(end, i + 1, to);
                    if (closing >= 0) {
                        i = closing;
                    }
                    continue;
                }
                int closing = matchEnd(end, i + 1, to);
                if (closing < 0) {
                    throw new IllegalStateException(
                            "Unterminated " + kind + " '" + foundName + "' starting at line " + (i + 1));
                }
                return Optional.of(new Range(i, closing, line, foundName));
            }
        }
        return Optional.empty();
    }

    private int matchEnd(Pattern end, int from, int to) {
        for (int j = from; j < Math.min(to, lines.size()); j++) {
            if (end.matcher(lines.get(j)).find()) {
                return j;
            }
        }
        return -1;
    }

    // --- Модификации ---

    /**
     * Вставить блок текста в указанную область.
     * Если области нет — создаёт в конце файла. Возвращает индекс первой вставленной строки.
     */
    public int insertIntoRegion(String regionName, String content, InsertPosition pos) {
        Optional<Range> r = findRegion(regionName);
        if (r.isEmpty()) {
            return createRegionAtEndWithContent(regionName, content);
        }
        Range range = r.get();
        List<String> block = splitLines(normalizeNewlines(content));
        // Удалим лидирующие/последние пустые строки в block — формат контролирует вызывающий
        int insertAt;
        if (pos == InsertPosition.BEGIN) {
            insertAt = range.startLine + 1;
            // оставим одну пустую строку между #Область и блоком, если её нет
            if (insertAt < lines.size() && !lines.get(insertAt).trim().isEmpty()) {
                block.add(0, "");
            }
        } else {
            insertAt = range.endLine;
            // оставим одну пустую строку между предыдущим контентом и блоком, если её нет
            if (insertAt > 0 && !lines.get(insertAt - 1).trim().isEmpty()) {
                block.add(0, "");
            }
            // и одну пустую строку между блоком и #КонецОбласти, если в конце блока её нет
            if (!block.isEmpty() && !block.get(block.size() - 1).trim().isEmpty()) {
                block.add("");
            }
        }
        lines.addAll(insertAt, block);
        return insertAt;
    }

    private int createRegionAtEndWithContent(String regionName, String content) {
        // Гарантируем разделитель: пустая строка перед #Область, если предыдущая непуста.
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.add("");
        }
        int startIdx = lines.size();
        lines.add("#Область " + regionName);
        lines.add("");
        List<String> block = splitLines(normalizeNewlines(content));
        // Уберём избыточные пустые в начале блока
        while (!block.isEmpty() && block.get(0).isEmpty()) {
            block.remove(0);
        }
        lines.addAll(block);
        if (lines.isEmpty() || !lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.add("");
        }
        lines.add("#КонецОбласти");
        return startIdx + 2;
    }

    /**
     * Вставить блок текста прямо перед строкой {@code Возврат ...} в указанной функции/процедуре.
     * Если возврата нет — вставляет перед закрывающим {@code КонецФункции}/{@code КонецПроцедуры}.
     */
    public void appendBeforeReturn(String functionName, String content) {
        Range fn = findFunction(functionName)
                .or(() -> findProcedure(functionName))
                .orElseThrow(() -> new IllegalStateException(
                        "Function/Procedure '" + functionName + "' not found"));
        int insertAt = fn.endLine;
        for (int i = fn.startLine + 1; i < fn.endLine; i++) {
            if (RETURN_LINE.matcher(lines.get(i)).find()) {
                insertAt = i;
                break;
            }
        }
        List<String> block = splitLines(normalizeNewlines(content));
        // Между блоком и Возврат — пустая строка
        if (insertAt < lines.size() && !lines.get(insertAt).trim().isEmpty()
                && (block.isEmpty() || !block.get(block.size() - 1).isEmpty())) {
            block.add("");
        }
        // Между предыдущим контентом и блоком — пустая строка
        if (insertAt > 0 && !lines.get(insertAt - 1).trim().isEmpty()
                && (block.isEmpty() || !block.get(0).isEmpty())) {
            block.add(0, "");
        }
        lines.addAll(insertAt, block);
    }

    /**
     * Добавить ветку {@code ИначеЕсли <conditionExpr> Тогда\n<body>} в первую цепочку {@code Если ... КонецЕсли}
     * внутри указанной процедуры.
     * <p>
     * Ветка добавляется ПОСЛЕ последней существующей ветки (Если/ИначеЕсли), перед {@code КонецЕсли}.
     * Если в процедуре нет {@code Если ...}, выбрасывает {@link IllegalStateException}.
     *
     * @param procName     имя процедуры
     * @param conditionExpr условие, например {@code ИдентификаторКоманды = "ЗаказПокупателя"}
     * @param branchBody  тело ветки (одна или несколько строк), с уже выставленными отступами
     */
    public void appendBranchToIfChain(String procName, String conditionExpr, String branchBody) {
        Range proc = findProcedure(procName).orElseThrow(
                () -> new IllegalStateException("Procedure '" + procName + "' not found"));
        int endIfLine = findFirstTopLevelEndIf(proc.startLine + 1, proc.endLine);
        if (endIfLine < 0) {
            throw new IllegalStateException(
                    "Procedure '" + procName + "' has no 'Если ... КонецЕсли' to extend");
        }
        // Определим базовый отступ блока по строке "Если ..." (top-level)
        int ifLine = findFirstTopLevelIf(proc.startLine + 1, endIfLine);
        String baseIndent = ifLine >= 0 ? leadingIndent(lines.get(ifLine)) : "\t";
        String innerIndent = baseIndent + "\t";

        List<String> insertion = new ArrayList<>();
        insertion.add(baseIndent + "ИначеЕсли " + conditionExpr + " Тогда");
        for (String bl : splitLines(normalizeNewlines(branchBody))) {
            if (bl.isEmpty()) {
                insertion.add("");
            } else if (bl.startsWith("\t") || bl.startsWith(" ")) {
                insertion.add(bl);
            } else {
                insertion.add(innerIndent + bl);
            }
        }
        lines.addAll(endIfLine, insertion);
    }

    private int findFirstTopLevelIf(int from, int to) {
        Pattern p = Pattern.compile("^\\s*Если\\b.*Тогда\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
        for (int i = from; i < Math.min(to, lines.size()); i++) {
            if (p.matcher(lines.get(i)).find()) {
                return i;
            }
        }
        return -1;
    }

    private int findFirstTopLevelEndIf(int from, int to) {
        // Учитываем вложенность Если/КонецЕсли
        int depth = 0;
        Pattern ifP = Pattern.compile("^\\s*Если\\b.*Тогда\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
        Pattern endIfP = Pattern.compile("^\\s*КонецЕсли\\s*;?\\s*$",
                Pattern.UNICODE_CHARACTER_CLASS | Pattern.CASE_INSENSITIVE);
        for (int i = from; i < Math.min(to, lines.size()); i++) {
            String line = lines.get(i);
            if (ifP.matcher(line).find()) {
                depth++;
            } else if (endIfP.matcher(line).find()) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Найти процедуру по имени, иначе создать её внутри указанной области (или в конце файла).
     *
     * @param procName    имя процедуры
     * @param fullSource  полный исходник процедуры (включая «Процедура ... КонецПроцедуры»)
     * @param regionName  область, в которую помещать. Может быть {@code null} → в конец файла.
     * @return диапазон процедуры (новый или существующий)
     */
    public Range findOrCreateProcedure(String procName, String fullSource, String regionName) {
        Optional<Range> existing = findProcedure(procName);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (regionName != null) {
            int insertAt = insertIntoRegion(regionName, fullSource, InsertPosition.END);
            // переиндексируем
            return findProcedure(procName).orElseThrow();
        }
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.add("");
        }
        List<String> block = splitLines(normalizeNewlines(fullSource));
        lines.addAll(block);
        return findProcedure(procName).orElseThrow();
    }

    /**
     * Универсальная версия {@link #findOrCreateProcedure}: ищет процедуру ИЛИ функцию.
     * Если {@code asFunction == true}, при отсутствии искомого имени ожидает в {@code fullSource}
     * блок {@code Функция ... КонецФункции} и проверяет результат через {@link #findFunction}.
     */
    public Range findOrCreateMethod(String name, String fullSource, String regionName, boolean asFunction) {
        Optional<Range> existing = asFunction ? findFunction(name) : findProcedure(name);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (regionName != null) {
            insertIntoRegion(regionName, fullSource, InsertPosition.END);
            return asFunction
                    ? findFunction(name).orElseThrow()
                    : findProcedure(name).orElseThrow();
        }
        if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.add("");
        }
        List<String> block = splitLines(normalizeNewlines(fullSource));
        lines.addAll(block);
        return asFunction
                ? findFunction(name).orElseThrow()
                : findProcedure(name).orElseThrow();
    }

    // --- Утилиты ---

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return out;
        }
        int i = 0, len = s.length();
        StringBuilder cur = new StringBuilder();
        while (i < len) {
            char c = s.charAt(i);
            if (c == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
                i++;
            } else if (c == '\r') {
                out.add(cur.toString());
                cur.setLength(0);
                i++;
                if (i < len && s.charAt(i) == '\n') {
                    i++;
                }
            } else {
                cur.append(c);
                i++;
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        } else if (!s.isEmpty()) {
            // последний символ был перенос строки — добавим пустую завершающую строку
            // ... только если строк ещё нет (иначе перевод не воспринимается как отдельная строка).
            // Логика: для round-trip мы храним «реальные» строки; финальный '\n' учитывается
            // полем endsWithNewline, не отдельной пустой строкой.
        }
        return out;
    }

    private static String detectLineSeparator(String content) {
        if (content == null) return System.lineSeparator();
        int idx = content.indexOf('\n');
        if (idx < 0) return "\n";
        if (idx > 0 && content.charAt(idx - 1) == '\r') return "\r\n";
        return "\n";
    }

    private static String normalizeNewlines(String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean equalsIgnoreCaseLocale(String a, String b) {
        if (a == null || b == null) return a == b;
        return a.toLowerCase(Locale.ROOT).equals(b.toLowerCase(Locale.ROOT));
    }

    private static String leadingIndent(String s) {
        int i = 0;
        while (i < s.length() && (s.charAt(i) == '\t' || s.charAt(i) == ' ')) {
            i++;
        }
        return s.substring(0, i);
    }
}

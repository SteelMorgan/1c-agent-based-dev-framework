package io.github.onec.xmlgen.model;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Извлекает тело процедуры/функции из BSL-файла по имени.
 *
 * <p>Используется в {@code extension patch-method --type ModificationAndControl}:
 * нужно скопировать тело оригинального метода базовой конфигурации в перехватчик
 * расширения.</p>
 *
 * <p>Парсинг текстовый, без AST. Учитывает регистр имени по 1С-конвенции
 * (case-insensitive). Опирается на маркеры {@code Процедура}/{@code КонецПроцедуры},
 * {@code Функция}/{@code КонецФункции}. Вложенные конструкции (Если, Цикл) не влияют
 * на матч — мы ищем именно «КонецПроцедуры/КонецФункции на отдельной строке».</p>
 */
public final class BslMethodExtractor {

    private BslMethodExtractor() {}

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

    /** Результат извлечения. */
    public static final class Extracted {
        public final boolean isFunction;
        public final String header;           // строка-сигнатура «Процедура X(...) Экспорт»
        public final List<String> bodyLines;  // строки между сигнатурой и закрывающей конструкцией (НЕ включая их)

        public Extracted(boolean isFunction, String header, List<String> bodyLines) {
            this.isFunction = isFunction;
            this.header = header;
            this.bodyLines = bodyLines;
        }
    }

    /**
     * Извлечь тело метода из BSL-файла. Если файл не существует или метод не найден — возвращает empty.
     */
    public static Optional<Extracted> extract(Path bslFile, String methodName) throws IOException {
        if (!java.nio.file.Files.isRegularFile(bslFile)) {
            return Optional.empty();
        }
        String content = ConfigurationXmlReader.readContent(bslFile);
        List<String> lines = splitLines(content);
        // Сначала пробуем процедуру
        Optional<Extracted> p = findIn(lines, methodName, PROC_BEGIN, PROC_END, false);
        if (p.isPresent()) return p;
        return findIn(lines, methodName, FUNC_BEGIN, FUNC_END, true);
    }

    private static Optional<Extracted> findIn(List<String> lines, String name,
                                              Pattern begin, Pattern end, boolean isFunction) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher m = begin.matcher(lines.get(i));
            if (m.find()) {
                String n = m.group(1);
                if (!n.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                int endIdx = -1;
                for (int j = i + 1; j < lines.size(); j++) {
                    if (end.matcher(lines.get(j)).find()) {
                        endIdx = j;
                        break;
                    }
                }
                if (endIdx < 0) {
                    return Optional.empty();
                }
                List<String> body = new ArrayList<>(lines.subList(i + 1, endIdx));
                return Optional.of(new Extracted(isFunction, lines.get(i), body));
            }
        }
        return Optional.empty();
    }

    private static List<String> splitLines(String s) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
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
                if (i < len && s.charAt(i) == '\n') i++;
            } else {
                cur.append(c);
                i++;
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }
}

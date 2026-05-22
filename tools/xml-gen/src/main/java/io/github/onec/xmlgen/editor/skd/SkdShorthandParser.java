package io.github.onec.xmlgen.editor.skd;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Парсер shorthand-форм для всех операций {@code xml-gen skd edit}.
 *
 * <p>См. SPEC §5.4 (EBNF) и skill {@code skd-edit/SKILL.md}.
 *
 * <p>Все методы — статические; возвращают POJO-описатели. Ошибки выбрасываются как
 * {@link SkdParseException} с позицией ошибки (column, 0-based).
 */
public final class SkdShorthandParser {

    /** Разделитель batch-операций. */
    public static final String BATCH_SEP = ";;";

    private static final Set<String> KNOWN_AGGREGATES = Set.of(
            "Сумма", "Среднее", "Количество", "Минимум", "Максимум",
            "Sum", "Avg", "Count", "Min", "Max");

    private static final Set<String> FIELD_ROLES = Set.of(
            "account", "balance", "period", "dimension", "resource");

    private static final Set<String> FIELD_FLAGS = Set.of(
            "balance", "dimension", "account", "period",
            "required", "autoOrder", "ignoreNullValues", "resource");

    private static final Set<String> PARAM_FLAGS = Set.of(
            "hidden", "always", "autoDates", "valueList");

    private SkdShorthandParser() {}

    // ====================================================================
    // batch
    // ====================================================================

    /**
     * Расщепить shorthand-строку на части по {@code ;;}.
     * Не выполняет escape-обработку: разделитель строго литеральный.
     */
    public static List<String> splitBatch(String value) {
        if (value == null) return List.of();
        // simple split, trim parts
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start <= value.length()) {
            int idx = value.indexOf(BATCH_SEP, start);
            if (idx < 0) {
                String part = value.substring(start).trim();
                if (!part.isEmpty()) result.add(part);
                break;
            }
            String part = value.substring(start, idx).trim();
            if (!part.isEmpty()) result.add(part);
            start = idx + BATCH_SEP.length();
        }
        return result;
    }

    // ====================================================================
    // fields
    // ====================================================================

    /** Дескриптор поля для add-field/modify-field. */
    public static final class FieldDescriptor {
        public String name;            // dataPath
        public String title;           // optional
        public List<SkdTypeParser.TypePart> type;   // valueType (может быть null для modify)
        public String role;            // короткая роль (@dimension, @balance, ...) — без @
        public List<String> restrictions = new ArrayList<>(); // noFilter / noOrder / noGroup / noField
    }

    /**
     * Грамматика: {@code name [title]?: type [@role]? [#constraint]*}.
     * Поле {@code type} может отсутствовать (но в add-field обязательно — это решает caller).
     */
    public static FieldDescriptor parseField(String spec) {
        FieldDescriptor fd = new FieldDescriptor();
        Cursor c = new Cursor(spec);

        // name
        fd.name = readIdentifier(c);
        c.skipWs();

        // optional [title]
        if (c.peek() == '[') {
            fd.title = readBracketed(c);
            c.skipWs();
        }

        // : type
        if (c.peek() == ':') {
            c.advance();
            c.skipWs();
            // read type until @ or # or end (skip trailing whitespace there).
            int typeEnd = findRestStart(c);
            String typeSpec = c.input.substring(c.pos, typeEnd).trim();
            if (typeSpec.isEmpty()) {
                throw new SkdParseException("expected type after ':'", c.pos);
            }
            fd.type = SkdTypeParser.parse(typeSpec);
            c.pos = typeEnd;
        }
        c.skipWs();

        // optional @role and #constraints (any order)
        while (!c.eof()) {
            c.skipWs();
            if (c.eof()) break;
            char ch = c.peek();
            if (ch == '@') {
                c.advance();
                String role = readIdentifier(c);
                fd.role = role;
            } else if (ch == '#') {
                c.advance();
                String r = readIdentifier(c);
                fd.restrictions.add(r);
            } else {
                throw new SkdParseException(
                        "unexpected token '" + ch + "' (expected '@role' or '#restriction')", c.pos);
            }
        }
        return fd;
    }

    /** Дескриптор для set-field-role. */
    public static final class FieldRoleDescriptor {
        public String dataPath;
        public List<String> flags = new ArrayList<>();
        public Map<String, String> kv = new LinkedHashMap<>();
    }

    /**
     * Грамматика: {@code dataPath [@flag]* [key=value]*}.
     * Если ни флагов ни kv нет — роль снимается.
     */
    public static FieldRoleDescriptor parseFieldRole(String spec) {
        FieldRoleDescriptor d = new FieldRoleDescriptor();
        Cursor c = new Cursor(spec);
        d.dataPath = readDataPath(c);
        c.skipWs();
        readFlagsAndKv(c, d.flags, d.kv);
        return d;
    }

    // ====================================================================
    // parameters
    // ====================================================================

    public static final class ParameterDescriptor {
        public String name;
        public String title;
        public List<SkdTypeParser.TypePart> type;
        public String value;
        public List<String> flags = new ArrayList<>();
        public Map<String, String> kv = new LinkedHashMap<>(); // includes availableValue raw
        /** Списка элементов availableValue=v1[: p1], v2[: p2], … */
        public List<AvailableValueItem> availableValues; // null = не указан, [] = пустой список
    }

    public static final class AvailableValueItem {
        public final String value;
        public final String presentation;
        public AvailableValueItem(String value, String presentation) {
            this.value = value;
            this.presentation = presentation;
        }
    }

    /**
     * Грамматика add-parameter: {@code name [title]?: type [= value]? [kv]* [@flag]*}.
     */
    public static ParameterDescriptor parseParameter(String spec) {
        ParameterDescriptor d = new ParameterDescriptor();
        Cursor c = new Cursor(spec);
        d.name = readIdentifier(c);
        c.skipWs();

        if (c.peek() == '[') {
            d.title = readBracketed(c);
            c.skipWs();
        }

        if (c.peek() == ':') {
            c.advance();
            c.skipWs();
            int typeEnd = findParamTypeEnd(c);
            String typeSpec = c.input.substring(c.pos, typeEnd).trim();
            if (typeSpec.isEmpty()) {
                throw new SkdParseException("expected type after ':'", c.pos);
            }
            d.type = SkdTypeParser.parse(typeSpec);
            c.pos = typeEnd;
        }
        c.skipWs();

        if (c.peek() == '=') {
            c.advance();
            c.skipWs();
            int vend = findValueEnd(c);
            d.value = c.input.substring(c.pos, vend).trim();
            c.pos = vend;
        }
        c.skipWs();

        // kv & flags. Special handling for availableValue=
        readParamKvAndFlags(c, d);

        return d;
    }

    /**
     * Грамматика modify-parameter: {@code name [title]? [kv]* [@flag]*}.
     * Поддерживает variant с только title (без kv) — это валидно.
     */
    public static ParameterDescriptor parseModifyParameter(String spec) {
        ParameterDescriptor d = new ParameterDescriptor();
        Cursor c = new Cursor(spec);
        d.name = readIdentifier(c);
        c.skipWs();
        if (c.peek() == '[') {
            d.title = readBracketed(c);
            c.skipWs();
        }
        readParamKvAndFlags(c, d);
        return d;
    }

    private static void readParamKvAndFlags(Cursor c, ParameterDescriptor d) {
        while (!c.eof()) {
            c.skipWs();
            if (c.eof()) break;
            char ch = c.peek();
            if (ch == '@') {
                c.advance();
                String f = readIdentifier(c);
                d.flags.add(f);
            } else if (isIdStart(ch)) {
                int start = c.pos;
                String key = readIdentifier(c);
                c.skipWs();
                if (c.peek() != '=') {
                    throw new SkdParseException("expected '=' after key '" + key + "'", c.pos);
                }
                c.advance();
                c.skipWs();
                if ("availableValue".equalsIgnoreCase(key) || "availableValues".equalsIgnoreCase(key)) {
                    d.availableValues = parseAvailableValues(c);
                } else {
                    String val = readValueToFlagOrEnd(c);
                    d.kv.put(key, val.trim());
                }
            } else {
                throw new SkdParseException("unexpected token '" + ch + "'", c.pos);
            }
        }
    }

    /**
     * Прочитать значение до начала следующего {@code @flag} или {@code key=}.
     * Поддерживает значения с пробелами и точками: {@code Справочник.Контрагенты.ПустаяСсылка}.
     */
    private static String readValueToFlagOrEnd(Cursor c) {
        StringBuilder sb = new StringBuilder();
        while (!c.eof()) {
            // Стоп если впереди ' @flag ' или ' key= '
            if (c.peek() == '@') break;
            if (Character.isWhitespace(c.peek())) {
                // Lookahead — если за пробелами '@' или key=, остановиться
                int save = c.pos;
                c.skipWs();
                if (c.eof()) { c.pos = save; break; }
                if (c.peek() == '@') break;
                // detect key=
                if (isIdStart(c.peek())) {
                    int p = c.pos;
                    while (p < c.input.length() && isIdPart(c.input.charAt(p))) p++;
                    // skip ws then look for =
                    int q = p;
                    while (q < c.input.length() && Character.isWhitespace(c.input.charAt(q))) q++;
                    if (q < c.input.length() && c.input.charAt(q) == '=') {
                        // it's next kv — stop
                        c.pos = save;
                        break;
                    }
                }
                // not a key= — restore and append a space
                c.pos = save;
                sb.append(c.peek());
                c.advance();
                continue;
            }
            sb.append(c.peek());
            c.advance();
        }
        return sb.toString();
    }

    /**
     * Прочитать список availableValue. Элементы разделены ',', представление после ':'.
     * Поддерживает одинарные кавычки '...' с удвоением '' для экранирования.
     */
    private static List<AvailableValueItem> parseAvailableValues(Cursor c) {
        List<AvailableValueItem> items = new ArrayList<>();
        while (!c.eof()) {
            c.skipWs();
            if (c.eof()) break;
            // stop if next is @flag boundary
            if (c.peek() == '@') break;
            // value token
            String val = readAvToken(c);
            String pres = null;
            c.skipWs();
            if (!c.eof() && c.peek() == ':') {
                c.advance();
                c.skipWs();
                pres = readAvToken(c);
            }
            items.add(new AvailableValueItem(val.trim(), pres != null ? pres.trim() : null));
            c.skipWs();
            if (c.eof()) break;
            if (c.peek() == ',') {
                c.advance();
                continue;
            }
            // Boundary — next token is flag or new key
            break;
        }
        return items;
    }

    private static String readAvToken(Cursor c) {
        if (c.peek() == '\'') {
            // quoted
            c.advance();
            StringBuilder sb = new StringBuilder();
            while (!c.eof()) {
                char ch = c.peek();
                if (ch == '\'') {
                    c.advance();
                    if (!c.eof() && c.peek() == '\'') {
                        sb.append('\'');
                        c.advance();
                    } else {
                        return sb.toString();
                    }
                } else {
                    sb.append(ch);
                    c.advance();
                }
            }
            throw new SkdParseException("unterminated quoted token", c.pos);
        }
        StringBuilder sb = new StringBuilder();
        // read until comma, colon, '@' or end
        while (!c.eof()) {
            char ch = c.peek();
            if (ch == ',' || ch == ':' || ch == '@') break;
            sb.append(ch);
            c.advance();
        }
        return sb.toString();
    }

    // ====================================================================
    // totals
    // ====================================================================

    public static final class TotalDescriptor {
        public String dataPath;
        public String expression;
    }

    /**
     * Грамматика: {@code dataPath: expression}.
     * Если expression — известная агрегатная функция без скобок, оборачивается в {@code Func(dataPath)}.
     */
    public static TotalDescriptor parseTotal(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) {
            throw new SkdParseException("total: expected '<dataPath>: <expression>'", 0);
        }
        TotalDescriptor d = new TotalDescriptor();
        d.dataPath = spec.substring(0, colon).trim();
        String expr = spec.substring(colon + 1).trim();
        if (d.dataPath.isEmpty()) {
            throw new SkdParseException("total: empty dataPath", 0);
        }
        if (expr.isEmpty()) {
            throw new SkdParseException("total: empty expression", colon + 1);
        }
        // Auto-wrap aggregates that have no parens
        boolean hasParen = expr.indexOf('(') >= 0;
        if (!hasParen && KNOWN_AGGREGATES.contains(expr)) {
            d.expression = expr + "(" + d.dataPath + ")";
        } else {
            d.expression = expr;
        }
        return d;
    }

    // ====================================================================
    // structure
    // ====================================================================

    public static final class StructureSpec {
        public List<String> groupItems = new ArrayList<>();
        /** True если groupItem — details, не имя поля. Элементы помечаются префиксом DETAILS_MARK. */
        public String groupName;
    }

    public static final String DETAILS_MARK = "@details";

    /** Грамматика: {@code field1, field2 @name=Group}. */
    public static StructureSpec parseStructureSpec(String spec) {
        int idx = spec.indexOf("@name=");
        if (idx < 0) {
            throw new SkdParseException("structure: '@name=<group>' is required", spec.length());
        }
        StructureSpec s = new StructureSpec();
        String left = spec.substring(0, idx).trim();
        s.groupName = spec.substring(idx + "@name=".length()).trim();
        if (s.groupName.isEmpty()) {
            throw new SkdParseException("structure: empty group name", idx + 6);
        }
        // groupItems separated by ',' or '>'
        if (left.isEmpty()) {
            throw new SkdParseException("structure: empty groupItems list", 0);
        }
        for (String token : left.split("[,>]")) {
            String t = token.trim();
            if (!t.isEmpty()) s.groupItems.add(t);
        }
        return s;
    }

    // ====================================================================
    // patch-query / rename-parameter
    // ====================================================================

    public static final class ArrowSpec {
        public String oldText;
        public String newText;
        public boolean once;
    }

    /**
     * Распарсить {@code <old> => <new> [@once]}.
     * Поддерживает многострочные значения.
     * @param allowOnce если false, флаг {@code @once} не парсится (для rename-parameter).
     */
    public static ArrowSpec parseArrow(String spec, boolean allowOnce) {
        int idx = spec.indexOf("=>");
        if (idx < 0) {
            throw new SkdParseException("expected '<old> => <new>'", 0);
        }
        ArrowSpec s = new ArrowSpec();
        s.oldText = spec.substring(0, idx);
        String tail = spec.substring(idx + 2);
        if (allowOnce) {
            // detect ' @once' suffix on last line
            int p = tail.lastIndexOf("@once");
            if (p >= 0) {
                // must be at end (possibly with trailing whitespace)
                String afterOnce = tail.substring(p + 5);
                if (afterOnce.trim().isEmpty() && (p == 0 || Character.isWhitespace(tail.charAt(p - 1)))) {
                    s.once = true;
                    tail = tail.substring(0, p);
                }
            }
        }
        // Trim only the boundary single space inserted by shorthand syntax `=> ` and ` @once`
        // Per skill examples, leading single space after `=>` and trailing single space before `@once` are syntactic.
        if (tail.startsWith(" ")) tail = tail.substring(1);
        if (s.once && tail.endsWith(" ")) tail = tail.substring(0, tail.length() - 1);
        s.newText = tail;
        // boundary trim of oldText: single trailing space before '=>'
        if (s.oldText.endsWith(" ")) s.oldText = s.oldText.substring(0, s.oldText.length() - 1);
        return s;
    }

    // ====================================================================
    // reorder-parameters
    // ====================================================================

    /** Парсит «Имя1, Имя2, Имя3». */
    public static List<String> parseReorderParameters(String spec) {
        List<String> result = new ArrayList<>();
        for (String t : spec.split(",")) {
            String s = t.trim();
            if (!s.isEmpty()) result.add(s);
        }
        if (result.isEmpty()) {
            throw new SkdParseException("reorder-parameters: empty list", 0);
        }
        return result;
    }

    // ====================================================================
    // helpers (cursor / lookahead)
    // ====================================================================

    private static final class Cursor {
        final String input;
        int pos;
        Cursor(String s) { this.input = s == null ? "" : s; this.pos = 0; }
        boolean eof() { return pos >= input.length(); }
        char peek() { return eof() ? '\0' : input.charAt(pos); }
        void advance() { pos++; }
        void skipWs() { while (!eof() && Character.isWhitespace(peek())) pos++; }
    }

    private static boolean isIdStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static String readIdentifier(Cursor c) {
        c.skipWs();
        if (c.eof() || !isIdStart(c.peek())) {
            throw new SkdParseException("expected identifier", c.pos);
        }
        int start = c.pos;
        while (!c.eof() && isIdPart(c.peek())) c.advance();
        return c.input.substring(start, c.pos);
    }

    /** dataPath = identifier ('.' identifier)*. */
    private static String readDataPath(Cursor c) {
        c.skipWs();
        if (c.eof() || !isIdStart(c.peek())) {
            throw new SkdParseException("expected dataPath", c.pos);
        }
        int start = c.pos;
        while (!c.eof()) {
            char ch = c.peek();
            if (isIdPart(ch) || ch == '.') c.advance();
            else break;
        }
        return c.input.substring(start, c.pos);
    }

    private static String readBracketed(Cursor c) {
        if (c.peek() != '[') throw new SkdParseException("expected '['", c.pos);
        c.advance();
        int start = c.pos;
        int depth = 1;
        while (!c.eof()) {
            char ch = c.peek();
            if (ch == '[') depth++;
            else if (ch == ']') {
                depth--;
                if (depth == 0) {
                    String s = c.input.substring(start, c.pos);
                    c.advance();
                    return s;
                }
            }
            c.advance();
        }
        throw new SkdParseException("unmatched '['", start - 1);
    }

    /** Найти позицию начала {@code @role}/{@code #constraint}/{@code конец}. */
    private static int findRestStart(Cursor c) {
        int p = c.pos;
        while (p < c.input.length()) {
            char ch = c.input.charAt(p);
            if (ch == '@' || ch == '#') return p;
            p++;
        }
        return p;
    }

    /** Найти конец типа параметра: '=' / '@' / id-key-then-'=' / end. */
    private static int findParamTypeEnd(Cursor c) {
        int p = c.pos;
        while (p < c.input.length()) {
            char ch = c.input.charAt(p);
            if (ch == '=' || ch == '@') return p;
            // detect ' key=' pattern: whitespace followed by id followed by '='
            if (Character.isWhitespace(ch)) {
                int q = p;
                while (q < c.input.length() && Character.isWhitespace(c.input.charAt(q))) q++;
                if (q < c.input.length() && isIdStart(c.input.charAt(q))) {
                    int r = q;
                    while (r < c.input.length() && isIdPart(c.input.charAt(r))) r++;
                    // skip ws then check '='
                    int s = r;
                    while (s < c.input.length() && Character.isWhitespace(c.input.charAt(s))) s++;
                    if (s < c.input.length() && c.input.charAt(s) == '=') {
                        return p;
                    }
                }
            }
            p++;
        }
        return p;
    }

    /** Конец value: первый '@' или ' key=' (как в findParamTypeEnd, без '='). */
    private static int findValueEnd(Cursor c) {
        int p = c.pos;
        while (p < c.input.length()) {
            char ch = c.input.charAt(p);
            if (ch == '@') return p;
            if (Character.isWhitespace(ch)) {
                int q = p;
                while (q < c.input.length() && Character.isWhitespace(c.input.charAt(q))) q++;
                if (q < c.input.length() && isIdStart(c.input.charAt(q))) {
                    int r = q;
                    while (r < c.input.length() && isIdPart(c.input.charAt(r))) r++;
                    int s = r;
                    while (s < c.input.length() && Character.isWhitespace(c.input.charAt(s))) s++;
                    if (s < c.input.length() && c.input.charAt(s) == '=') return p;
                }
            }
            p++;
        }
        return p;
    }

    private static void readFlagsAndKv(Cursor c, List<String> flags, Map<String, String> kv) {
        while (!c.eof()) {
            c.skipWs();
            if (c.eof()) break;
            char ch = c.peek();
            if (ch == '@') {
                c.advance();
                flags.add(readIdentifier(c));
            } else if (isIdStart(ch)) {
                String key = readIdentifier(c);
                c.skipWs();
                if (c.peek() != '=') {
                    throw new SkdParseException("expected '=' after key '" + key + "'", c.pos);
                }
                c.advance();
                c.skipWs();
                String v = readValueToFlagOrEnd(c);
                kv.put(key, v.trim());
            } else {
                throw new SkdParseException("unexpected '" + ch + "'", c.pos);
            }
        }
    }
}

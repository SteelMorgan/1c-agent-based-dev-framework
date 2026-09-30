package io.github.onec.xmlgen.dsl;

import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер расширенного type-spec для SKD DSL.
 *
 * <p>Поддерживает:
 * <ul>
 *   <li>простые типы: {@code string}, {@code string(50)}, {@code boolean}, {@code date}, {@code dateTime}</li>
 *   <li>числовые с квалификаторами: {@code decimal}, {@code decimal(15)}, {@code decimal(15,2)},
 *       {@code decimal(15,2),nonneg}, {@code decimal(15,2,nonneg)}, {@code number(N,M)}</li>
 *   <li>ссылочные: {@code CatalogRef.X}, {@code DocumentRef.X}, {@code EnumRef.X},
 *       {@code ChartOfAccountsRef.X}, {@code StandardPeriod}</li>
 *   <li>составные типы через {@code |}: {@code "decimal(15,2)|string(50)"}</li>
 * </ul>
 *
 * <p>Результат — {@link Parsed}, содержащий один или несколько {@link Component},
 * каждый со своим XML-типом и квалификаторами.</p>
 */
public class SkdTypeSpec {

    private static final Pattern DECIMAL_PATTERN =
            Pattern.compile("(?:decimal|number)(?:\\((\\d+)(?:,(\\d+))?(?:,(nonneg|nonnegative))?\\))?");
    private static final Pattern STRING_PATTERN =
            Pattern.compile("string(!)?(?:\\((\\d+)\\))?");

    private SkdTypeSpec() {
    }

    /**
     * Распарсить type-spec (строка или список строк) в нормализованную структуру.
     *
     * @param spec строка ({@code "decimal(15,2),nonneg"}) или {@code List<String>}
     *             (составной тип через массив).
     */
    public static Parsed parse(Object spec) {
        if (spec == null) {
            throw new IllegalArgumentException("Type spec is null");
        }
        List<Component> components = new ArrayList<>();
        if (spec instanceof List) {
            for (Object o : (List<?>) spec) {
                components.add(parseSingle(o.toString().trim()));
            }
        } else {
            String s = spec.toString().trim();
            // Поддержка "A|B" в одной строке.
            for (String part : s.split("\\|")) {
                components.add(parseSingle(part.trim()));
            }
        }
        return new Parsed(components);
    }

    private static Component parseSingle(String raw) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Empty type spec");
        }
        // Отделить ",nonneg" суффикс.
        String suffix = null;
        String core = raw;
        int commaIdx = topLevelComma(raw);
        if (commaIdx > 0) {
            core = raw.substring(0, commaIdx).trim();
            suffix = raw.substring(commaIdx + 1).trim();
        }
        boolean nonneg = suffix != null && (suffix.equalsIgnoreCase("nonneg")
                || suffix.equalsIgnoreCase("nonnegative"));

        // string / string(N) / string!(N)
        Matcher sm = STRING_PATTERN.matcher(core);
        if (sm.matches()) {
            boolean fixed = sm.group(1) != null;
            int length = sm.group(2) != null ? Integer.parseInt(sm.group(2)) : 0;
            return Component.string(length, fixed);
        }
        // decimal / decimal(N,M) / number(N,M)
        Matcher dm = DECIMAL_PATTERN.matcher(core);
        if (dm.matches()) {
            int digits = dm.group(1) != null ? Integer.parseInt(dm.group(1)) : 10;
            int fraction = dm.group(2) != null ? Integer.parseInt(dm.group(2)) : 0;
            // Если decimal без скобок — деньги 10,2; согласно skill.
            if (dm.group(1) == null) { digits = 10; fraction = 2; }
            boolean innerNonneg = dm.group(3) != null;
            return Component.decimal(digits, fraction, nonneg || innerNonneg);
        }
        if ("boolean".equalsIgnoreCase(core)) {
            return Component.bool();
        }
        if ("date".equalsIgnoreCase(core)) {
            return Component.date("Date");
        }
        if ("dateTime".equalsIgnoreCase(core) || "datetime".equalsIgnoreCase(core)) {
            return Component.date("DateTime");
        }
        if ("StandardPeriod".equals(core)) {
            return Component.reference("StandardPeriod", true);
        }
        if (core.startsWith("CatalogRef.") || core.startsWith("DocumentRef.")
                || core.startsWith("EnumRef.") || core.startsWith("ChartOfAccountsRef.")
                || core.startsWith("ChartOfCharacteristicTypesRef.")
                || core.startsWith("ChartOfCalculationTypesRef.")
                || core.startsWith("ExchangePlanRef.")
                || core.startsWith("BusinessProcessRef.")
                || core.startsWith("TaskRef.")
                || core.contains("Ref.")) {
            return Component.reference(core, false);
        }
        return Component.raw(core);
    }

    /** Найти запятую вне скобок (для отделения {@code ,nonneg}). */
    private static int topLevelComma(String s) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) return i;
        }
        return -1;
    }

    /** Результат разбора — один или несколько компонент типа. */
    @Value
    public static class Parsed {
        List<Component> components;

        public boolean isComposite() {
            return components.size() > 1;
        }
        public Component first() {
            return components.get(0);
        }
    }

    /** Один компонент: XML-тип + квалификаторы. */
    @Value
    public static class Component {
        Kind kind;
        String xmlType;
        Integer length;
        Boolean fixedLength;
        Integer digits;
        Integer fractionDigits;
        Boolean nonNegative;
        String dateFractions;
        boolean isReference;

        public enum Kind { STRING, DECIMAL, BOOLEAN, DATE, REFERENCE, RAW }

        static Component string(int length, boolean fixed) {
            return new Component(Kind.STRING, "xs:string", length, fixed,
                    null, null, null, null, false);
        }
        static Component decimal(int digits, int fraction, boolean nonneg) {
            return new Component(Kind.DECIMAL, "xs:decimal", null, null,
                    digits, fraction, nonneg, null, false);
        }
        static Component bool() {
            return new Component(Kind.BOOLEAN, "xs:boolean", null, null,
                    null, null, null, null, false);
        }
        static Component date(String fractions) {
            return new Component(Kind.DATE, "xs:dateTime", null, null,
                    null, null, null, fractions, false);
        }
        static Component reference(String typeName, boolean standalone) {
            // Ссылочные типы используют префикс v8: или сам тип (для StandardPeriod).
            String xml = standalone ? ("v8:" + typeName) : ("d5p1:" + typeName);
            return new Component(Kind.REFERENCE, xml, null, null,
                    null, null, null, null, true);
        }
        static Component raw(String typeName) {
            return new Component(Kind.RAW, typeName, null, null,
                    null, null, null, null, false);
        }
    }
}

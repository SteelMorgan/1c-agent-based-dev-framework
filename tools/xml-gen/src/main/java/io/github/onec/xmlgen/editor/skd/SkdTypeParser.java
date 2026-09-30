package io.github.onec.xmlgen.editor.skd;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Парсер short-форм типов СКД.
 *
 * <p>Поддерживает:
 * <ul>
 *     <li>{@code string} / {@code string(N)} — строка с длиной</li>
 *     <li>{@code decimal(D,F)} / {@code number(D,F)} — число с разрядностью и квалификатором {@code ,nonneg}</li>
 *     <li>{@code date} — дата</li>
 *     <li>{@code boolean} — булево</li>
 *     <li>{@code uuid} — UUID</li>
 *     <li>{@code CatalogRef.X}, {@code DocumentRef.X}, {@code EnumRef.X},
 *         {@code ChartOfAccountsRef.X}, {@code StandardPeriod}, …</li>
 *     <li>составные типы через {@code |} : {@code decimal(15,2)|string(50)}</li>
 * </ul>
 *
 * <p>Целевой XML — {@code <valueType><v8:Type>…</v8:Type>[квалификаторы]</valueType>}.
 * Парсер не пишет XML, только нормализует.
 */
public final class SkdTypeParser {

    private SkdTypeParser() {}

    /** Результат разбора одного «слагаемого» типа. */
    public static final class TypePart {
        /** Имя в XML, например {@code xs:string}, {@code xs:decimal}, {@code xs:dateTime},
         * {@code v8:UUID}, {@code d5p1:CatalogRef.Контрагенты}. */
        public final String xmlType;
        /** Длина для string-(N), null если нет. */
        public final Integer stringLength;
        /** Digits для decimal(D,F), null если нет. */
        public final Integer numberDigits;
        /** FractionDigits для decimal(D,F), null если нет. */
        public final Integer numberFractionDigits;
        /** True если был квалификатор {@code ,nonneg}. */
        public final boolean nonneg;
        /** Исходная форма (для диагностики). */
        public final String raw;

        public TypePart(String xmlType, Integer stringLength,
                        Integer numberDigits, Integer numberFractionDigits,
                        boolean nonneg, String raw) {
            this.xmlType = xmlType;
            this.stringLength = stringLength;
            this.numberDigits = numberDigits;
            this.numberFractionDigits = numberFractionDigits;
            this.nonneg = nonneg;
            this.raw = raw;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TypePart)) return false;
            TypePart that = (TypePart) o;
            return nonneg == that.nonneg
                    && Objects.equals(xmlType, that.xmlType)
                    && Objects.equals(stringLength, that.stringLength)
                    && Objects.equals(numberDigits, that.numberDigits)
                    && Objects.equals(numberFractionDigits, that.numberFractionDigits);
        }

        @Override
        public int hashCode() {
            return Objects.hash(xmlType, stringLength, numberDigits, numberFractionDigits, nonneg);
        }
    }

    /** Разобрать составной тип в список частей. Никогда не возвращает пустой список. */
    public static List<TypePart> parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new SkdParseException("type is empty", 0);
        }
        List<TypePart> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == '|' && depth == 0) {
                parts.add(parseSingle(spec.substring(start, i), start));
                start = i + 1;
            }
        }
        parts.add(parseSingle(spec.substring(start), start));
        return parts;
    }

    private static TypePart parseSingle(String raw, int baseCol) {
        String s = raw.trim();
        if (s.isEmpty()) {
            throw new SkdParseException("empty type alternative", baseCol);
        }

        // 1. Базовое имя + опциональные () и ,qualifier
        String head;
        String paren = null;
        String qualifier = null;
        int p1 = s.indexOf('(');
        if (p1 >= 0) {
            int p2 = s.indexOf(')', p1);
            if (p2 < 0) throw new SkdParseException("unmatched '(' in type: " + s, baseCol + p1);
            head = s.substring(0, p1).trim();
            paren = s.substring(p1 + 1, p2).trim();
            String tail = s.substring(p2 + 1).trim();
            if (!tail.isEmpty()) {
                if (!tail.startsWith(",")) {
                    throw new SkdParseException("expected ',qualifier' after ')' in type: " + s,
                            baseCol + p2 + 1);
                }
                qualifier = tail.substring(1).trim();
            }
        } else {
            int comma = s.indexOf(',');
            if (comma >= 0) {
                head = s.substring(0, comma).trim();
                qualifier = s.substring(comma + 1).trim();
            } else {
                head = s;
            }
        }

        boolean nonneg = false;
        if (qualifier != null) {
            if (!"nonneg".equalsIgnoreCase(qualifier)) {
                throw new SkdParseException("unknown type qualifier '" + qualifier + "'", baseCol);
            }
            nonneg = true;
        }

        switch (head.toLowerCase()) {
            case "string":
                if (paren != null) {
                    return new TypePart("xs:string", parseInt(paren, baseCol),
                            null, null, false, s);
                }
                return new TypePart("xs:string", null, null, null, false, s);
            case "decimal":
            case "number":
                if (paren == null) {
                    return new TypePart("xs:decimal", null, null, null, nonneg, s);
                }
                String[] dp = paren.split(",");
                if (dp.length != 2) {
                    throw new SkdParseException("decimal expects (D,F): " + s, baseCol);
                }
                return new TypePart("xs:decimal", null,
                        parseInt(dp[0].trim(), baseCol),
                        parseInt(dp[1].trim(), baseCol),
                        nonneg, s);
            case "date":
                return new TypePart("xs:dateTime", null, null, null, false, s);
            case "boolean":
                return new TypePart("xs:boolean", null, null, null, false, s);
            case "uuid":
                return new TypePart("v8:UUID", null, null, null, false, s);
        }
        // Reference types: CatalogRef.X, DocumentRef.X, ...
        if (head.contains(".") && (head.endsWith(".") || head.matches("[A-Za-zА-Яа-я_]+Ref\\..+"))) {
            return new TypePart("d5p1:" + head, null, null, null, false, s);
        }
        // ref:Catalog.X — short alias
        if (head.toLowerCase().startsWith("ref:")) {
            String tail = head.substring(4);
            if (tail.isEmpty()) {
                throw new SkdParseException("ref: requires <Class.Name>", baseCol);
            }
            // Map Catalog.X → CatalogRef.X
            int dot = tail.indexOf('.');
            if (dot < 0) {
                throw new SkdParseException("ref: expects Class.Name", baseCol);
            }
            String cls = tail.substring(0, dot);
            String name = tail.substring(dot + 1);
            return new TypePart("d5p1:" + cls + "Ref." + name, null, null, null, false, s);
        }
        // Enum bare types from skill (StandardPeriod and friends)
        if (head.matches("[A-Za-zА-Яа-я_][A-Za-zА-Яа-я0-9_]*")) {
            // Treat as raw v8 type name (e.g. StandardPeriod)
            return new TypePart("v8:" + head, null, null, null, false, s);
        }
        throw new SkdParseException("unknown type: " + s, baseCol);
    }

    private static Integer parseInt(String s, int col) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new SkdParseException("expected integer, got '" + s + "'", col);
        }
    }
}

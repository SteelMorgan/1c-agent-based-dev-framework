package io.github.onec.xmlgen.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Парсер составных типов метаданных 1С в shorthand-нотации.
 *
 * <p>Поддерживаемые форматы:
 * <ul>
 *   <li>{@code string(50)} — строка фиксированной длины</li>
 *   <li>{@code number(15,2)} — число</li>
 *   <li>{@code boolean} — булево</li>
 *   <li>{@code date} — дата</li>
 *   <li>{@code CatalogRef.Order} — ссылка на объект метаданных</li>
 *   <li>{@code string(50)|number(15,2)|DocumentRef.Order} — составной тип через {@code |}</li>
 *   <li>{@code String + Number(15,2)} — составной через {@code +} (legacy)</li>
 * </ul>
 *
 * <p>Делегирует разрешение отдельных типов в {@link MetaEditor} через shared-метод,
 * а также предоставляет список строк для передачи в {@code writeTypeBlock}.
 */
public class CompositeType {

    private CompositeType() {}

    /**
     * Разобрать shorthand-строку типа и вернуть список нормализованных типов.
     * Каждый тип — в формате, понятном {@code MetaEditor.resolveType()}.
     *
     * @param typeStr строка типа, например {@code "string(50)|number(15,2)|CatalogRef.Склады"}
     * @return список нормализованных типов (никогда не пуст)
     * @throws IllegalArgumentException если строка пустая или null
     */
    public static List<String> parse(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw new IllegalArgumentException("Type string must not be empty");
        }

        List<String> result = new ArrayList<>();

        // Support both "|" and " + " as separators (paren-aware split)
        List<String> parts = splitCompositeTypes(typeStr);
        for (String part : parts) {
            String normalized = part.trim();
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No types parsed from: " + typeStr);
        }
        return result;
    }

    /**
     * Split a type string by {@code |} or {@code +}, respecting parentheses.
     * E.g. {@code "Number(15,2)|String(50)"} → ["Number(15,2)", "String(50)"]
     */
    public static List<String> splitCompositeTypes(String typeStr) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < typeStr.length(); i++) {
            char ch = typeStr.charAt(i);
            if (ch == '(') {
                depth++;
                current.append(ch);
            } else if (ch == ')') {
                depth--;
                current.append(ch);
            } else if (depth == 0 && ch == '|') {
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else if (depth == 0 && ch == '+'
                    && !(i + 1 < typeStr.length() && typeStr.charAt(i + 1) == '(')) {
                // Optional: also support legacy " + " separator.
                //**agent TASK-174 [05.06.2026 12:30:00]
                // "+" непосредственно перед "(" — это nonneg-синтаксис одного типа
                // ("number+(15,2)", TypeResolver.NUMBER_PATTERN), а не разделитель
                // составного типа. Без guard "number+(15,2)" рвался на "number" и "(15,2)".
                //**agent TASK-174
                parts.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString().trim());
        }
        return parts;
    }
}

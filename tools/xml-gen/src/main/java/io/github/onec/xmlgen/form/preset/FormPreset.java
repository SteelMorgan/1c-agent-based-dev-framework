package io.github.onec.xmlgen.form.preset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Распарсенный пресет формы. Типизирован максимально слабо — секции
 * представлены как Map&lt;String, Object&gt;, поскольку набор ключей
 * различается для item/list/folder/choice и под-объектов.
 */
public final class FormPreset {

    private final Map<String, Map<String, Object>> sections;
    private final String name;
    private final String description;

    public FormPreset(String name, String description, Map<String, Map<String, Object>> sections) {
        this.name = name;
        this.description = description;
        this.sections = sections != null ? sections : new LinkedHashMap<>();
    }

    public String getName() { return name; }
    public String getDescription() { return description; }

    public Map<String, Object> section(String key) {
        Map<String, Object> s = sections.get(key);
        return s != null ? s : Collections.emptyMap();
    }

    public Map<String, Map<String, Object>> allSections() {
        return sections;
    }

    /** Удобный доступ к вложенной секции (вернёт пустую Map если путь не найден). */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> subMap(Map<String, Object> parent, String key) {
        if (parent == null) return Collections.emptyMap();
        Object v = parent.get(key);
        return v instanceof Map ? (Map<String, Object>) v : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> subList(Map<String, Object> parent, String key) {
        if (parent == null) return Collections.emptyList();
        Object v = parent.get(key);
        return v instanceof List ? (List<Object>) v : Collections.emptyList();
    }

    public static String str(Map<String, Object> parent, String key, String def) {
        if (parent == null) return def;
        Object v = parent.get(key);
        return v instanceof String ? (String) v : def;
    }

    public static boolean bool(Map<String, Object> parent, String key, boolean def) {
        if (parent == null) return def;
        Object v = parent.get(key);
        if (v instanceof Boolean) return (Boolean) v;
        return def;
    }
}

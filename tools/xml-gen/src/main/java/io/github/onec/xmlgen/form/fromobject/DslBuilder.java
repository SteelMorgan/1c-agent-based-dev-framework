package io.github.onec.xmlgen.form.fromobject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent-билдер для узлов {@code elements[...]} в FormDsl. Возвращает {@link Map}
 * с ключами, совместимыми с {@code FormWriter} JSON DSL (input/group/table/...).
 */
public final class DslBuilder {

    private DslBuilder() {}

    public static Map<String, Object> input(String name, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("input", name);
        if (path != null) m.put("path", path);
        return m;
    }

    public static Map<String, Object> check(String name, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("check", name);
        if (path != null) m.put("path", path);
        return m;
    }

    public static Map<String, Object> labelField(String name, String path) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("labelField", name);
        if (path != null) m.put("path", path);
        return m;
    }

    public static Map<String, Object> group(String orientation, String name, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", orientation);
        m.put("name", name);
        m.put("showTitle", false);
        if (children != null) m.put("children", children);
        return m;
    }

    public static Map<String, Object> bareGroup(String orientation, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", orientation);
        m.put("name", name);
        return m;
    }

    public static Map<String, Object> page(String name, String title, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("page", name);
        if (title != null) m.put("title", title);
        if (children != null) m.put("children", children);
        return m;
    }

    public static Map<String, Object> pages(String name, List<Map<String, Object>> children) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pages", name);
        m.put("children", children);
        return m;
    }

    public static Map<String, Object> table(String name, String path, List<Map<String, Object>> columns) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", name);
        m.put("path", path);
        m.put("commandBarLocation", "None");
        m.put("tableAutofill", false);
        m.put("columns", columns);
        return m;
    }

    public static Map<String, Object> simpleTable(String name, String path, List<Map<String, Object>> columns) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("table", name);
        m.put("path", path);
        m.put("columns", columns);
        return m;
    }

    public static List<Map<String, Object>> children() {
        return new ArrayList<>();
    }

    /** Построить DSL-поле с учётом fieldDefaults для Ref/Boolean. */
    public static Map<String, Object> field(String name, String path, String type, Map<String, Object> fieldDefaults) {
        boolean isRef = type != null && (type.contains("Ref.") || type.contains("Ссылка."));
        boolean isBool = isBoolean(type);
        String el = "input";
        if (isBool && fieldDefaults != null) {
            Object b = fieldDefaults.get("boolean");
            if (b instanceof Map && "check".equals(((Map<?, ?>) b).get("element"))) {
                el = "check";
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(el, name);
        if (path != null) m.put("path", path);
        if (isRef && fieldDefaults != null) {
            Object r = fieldDefaults.get("ref");
            if (r instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) r).get("choiceButton"))) {
                m.put("choiceButton", true);
            }
        }
        return m;
    }

    /** Элемент колонки списка с учётом типа Boolean → check, остальное → labelField. */
    public static Map<String, Object> listColumn(String name, String path, String type) {
        String el = isBoolean(type) ? "check" : "labelField";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(el, name);
        m.put("path", path);
        return m;
    }

    public static boolean isBoolean(String type) {
        if (type == null) return false;
        String t = type.trim();
        return "boolean".equalsIgnoreCase(t) || "xs:boolean".equals(t) || t.contains("Boolean");
    }

    public static boolean isDisplayable(String type) {
        if (type == null) return true;
        return !(type.contains("ValueStorage") || type.contains("ХранилищеЗначения"));
    }
}

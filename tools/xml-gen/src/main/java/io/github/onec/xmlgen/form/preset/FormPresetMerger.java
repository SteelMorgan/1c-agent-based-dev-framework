package io.github.onec.xmlgen.form.preset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep merge для JSON-пресетов формы. Overlay побеждает; Map мерджатся рекурсивно,
 * скаляры и списки заменяются целиком.
 */
public final class FormPresetMerger {

    private FormPresetMerger() {}

    public static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> overlay) {
        if (overlay == null || overlay.isEmpty()) {
            return copy(base);
        }
        if (base == null || base.isEmpty()) {
            return copy(overlay);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : base.entrySet()) {
            result.put(e.getKey(), copyValue(e.getValue()));
        }
        for (Map.Entry<String, Object> e : overlay.entrySet()) {
            String k = e.getKey();
            Object ov = e.getValue();
            Object bv = result.get(k);
            if (bv instanceof Map && ov instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> bm = (Map<String, Object>) bv;
                @SuppressWarnings("unchecked")
                Map<String, Object> om = (Map<String, Object>) ov;
                result.put(k, merge(bm, om));
            } else {
                result.put(k, copyValue(ov));
            }
        }
        return result;
    }

    /**
     * Resolve {@code basedOn}: если секция содержит ключ "basedOn", её базу мы deep-merge'им
     * снизу, а саму секцию кладём сверху. Циклы обрываются.
     */
    public static void resolveBasedOn(Map<String, Map<String, Object>> sections) {
        for (String key : new ArrayList<>(sections.keySet())) {
            Map<String, Object> resolved = resolveOne(key, sections, new ArrayList<>());
            resolved.remove("basedOn");
            sections.put(key, resolved);
        }
    }

    private static Map<String, Object> resolveOne(String key,
                                                  Map<String, Map<String, Object>> sections,
                                                  List<String> stack) {
        Map<String, Object> section = sections.get(key);
        if (section == null) return new LinkedHashMap<>();
        if (stack.contains(key)) return copy(section);
        Object basedOn = section.get("basedOn");
        if (!(basedOn instanceof String)) {
            return copy(section);
        }
        String baseName = (String) basedOn;
        stack.add(key);
        Map<String, Object> base = resolveOne(baseName, sections, stack);
        stack.remove(stack.size() - 1);
        Map<String, Object> merged = merge(base, section);
        return merged;
    }

    private static Map<String, Object> copy(Map<String, Object> src) {
        if (src == null) return new LinkedHashMap<>();
        Map<String, Object> r = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : src.entrySet()) {
            r.put(e.getKey(), copyValue(e.getValue()));
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static Object copyValue(Object v) {
        if (v instanceof Map) {
            return copy((Map<String, Object>) v);
        }
        if (v instanceof List) {
            return new ArrayList<>((List<Object>) v);
        }
        return v;
    }
}

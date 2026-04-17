package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Общие утилиты генераторов. */
final class GenUtil {
    private GenUtil() {}

    static Map<String, Object> mergeProperties(Map<String, Object> presetSection, Object... extraKV) {
        Map<String, Object> props = new LinkedHashMap<>();
        if (extraKV != null && extraKV.length > 0) {
            for (int i = 0; i + 1 < extraKV.length; i += 2) {
                props.put((String) extraKV[i], extraKV[i + 1]);
            }
        }
        Map<String, Object> fromPreset = FormPreset.subMap(presetSection, "properties");
        for (Map.Entry<String, Object> e : fromPreset.entrySet()) {
            props.put(e.getKey(), e.getValue());
        }
        return props;
    }

    static FormDsl.Attribute objectAttr(String attrName, String type) {
        return new FormDsl.Attribute(attrName, null, type, true, null, null, null);
    }

    static FormDsl.Attribute objectAttrSaved(String attrName, String type) {
        return new FormDsl.Attribute(attrName, null, type, true, null, null, true);
    }

    static FormDsl.Attribute dynamicList(String attrName, String mainTable) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("mainTable", mainTable);
        settings.put("dynamicDataRead", true);
        return new FormDsl.Attribute(attrName, null, "DynamicList", true, null, settings, null);
    }

    @SuppressWarnings("unchecked")
    static List<String> strList(Object v) {
        if (!(v instanceof List)) return new ArrayList<>();
        List<String> r = new ArrayList<>();
        for (Object o : (List<Object>) v) if (o != null) r.add(o.toString());
        return r;
    }
}

package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальная форма-заготовка для DataProcessor/Report. Объект-реквизит + заголовок.
 */
public class DataProcessorFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        String objType = "Report".equals(meta.type) ? "ReportObject." + meta.name
                : "DataProcessorObject." + meta.name;

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("autoTitle", false);

        List<FormDsl.Attribute> attrs = Collections.singletonList(
                new FormDsl.Attribute("Объект", null, objType, true, null, null, null));

        return new FormDsl(meta.synonym, props, null, null,
                Collections.emptyList(), attrs, null, null);
    }
}

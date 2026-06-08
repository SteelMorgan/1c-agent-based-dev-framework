package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safe fallback for metadata types that support forms but do not have a
 * specialized from-object generator yet. Used by registration workflows such as
 * {@code meta edit --op add-form}; it creates a parseable managed form without
 * guessing object-specific main attributes.
 */
public class GenericFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("autoTitle", false);
        return new FormDsl(meta.synonym, props, null, null,
                Collections.emptyList(), Collections.emptyList(), null, null);
    }
}

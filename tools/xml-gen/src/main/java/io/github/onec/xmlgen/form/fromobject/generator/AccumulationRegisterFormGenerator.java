package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AccumulationRegisterFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        Map<String, Object> p = preset.section("accumulationRegister.list");

        List<Map<String, Object>> cols = new ArrayList<>();
        cols.add(DslBuilder.labelField("Период", "Список.Period"));
        cols.add(DslBuilder.labelField("Регистратор", "Список.Recorder"));
        cols.add(DslBuilder.labelField("НомерСтроки", "Список.LineNumber"));

        for (ObjectMeta.Field dim : meta.dimensions) {
            if (!DslBuilder.isDisplayable(dim.type)) continue;
            cols.add(DslBuilder.labelField(dim.name, "Список." + dim.name));
        }
        for (ObjectMeta.Field res : meta.resources) {
            if (!DslBuilder.isDisplayable(res.type)) continue;
            cols.add(DslBuilder.listColumn(res.name, "Список." + res.name, res.type));
        }
        for (ObjectMeta.Field attr : meta.attributes) {
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            cols.add(DslBuilder.listColumn(attr.name, "Список." + attr.name, attr.type));
        }

        Map<String, Object> table = DslBuilder.table("Список", "Список", cols);
        Map<String, Object> props = GenUtil.mergeProperties(p);
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.dynamicList("Список", "AccumulationRegister." + meta.name));

        return new FormDsl(meta.synonym, props, null, null,
                Collections.singletonList(table), attrs, null, null);
    }
}

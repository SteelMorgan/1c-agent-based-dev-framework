package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InformationRegisterFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        String key = "informationRegister." + purpose;
        Map<String, Object> p = preset.section(key);
        if ("list".equals(purpose)) return list(meta, p);
        return record(meta, p);
    }

    private FormDsl record(ObjectMeta meta, Map<String, Object> p) {
        Map<String, Object> fd = FormPreset.subMap(p, "fieldDefaults");
        boolean periodic = meta.periodicity != null && !"Nonperiodical".equals(meta.periodicity);

        List<Map<String, Object>> elements = new ArrayList<>();
        if (periodic) elements.add(DslBuilder.input("Период", "Запись.Period"));
        for (ObjectMeta.Field dim : meta.dimensions) {
            if (!DslBuilder.isDisplayable(dim.type)) continue;
            elements.add(DslBuilder.field(dim.name, "Запись." + dim.name, dim.type, fd));
        }
        for (ObjectMeta.Field res : meta.resources) {
            if (!DslBuilder.isDisplayable(res.type)) continue;
            elements.add(DslBuilder.field(res.name, "Запись." + res.name, res.type, fd));
        }
        for (ObjectMeta.Field attr : meta.attributes) {
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            elements.add(DslBuilder.field(attr.name, "Запись." + attr.name, attr.type, fd));
        }

        Map<String, Object> props = GenUtil.mergeProperties(p, "windowOpeningMode", "LockOwnerWindow");
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.objectAttrSaved("Запись", "InformationRegisterRecordManager." + meta.name));
        return new FormDsl(meta.synonym, props, null, null, elements, attrs, null, null);
    }

    private FormDsl list(ObjectMeta meta, Map<String, Object> p) {
        boolean periodic = meta.periodicity != null && !"Nonperiodical".equals(meta.periodicity);
        boolean subordinate = "RecorderSubordinate".equals(meta.writeMode);

        List<Map<String, Object>> cols = new ArrayList<>();
        if (periodic) cols.add(DslBuilder.labelField("Период", "Список.Period"));
        if (subordinate) {
            cols.add(DslBuilder.labelField("Регистратор", "Список.Recorder"));
            cols.add(DslBuilder.labelField("НомерСтроки", "Список.LineNumber"));
        }
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
                GenUtil.dynamicList("Список", "InformationRegister." + meta.name));

        return new FormDsl(meta.synonym, props, null, null,
                Collections.singletonList(table), attrs, null, null);
    }
}

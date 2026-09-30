package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Делегирует в {@link CatalogFormGenerator}, затем:
 *  — патчит типы Catalog* → ChartOfCharacteristicTypes*
 *  — для item: инжектит поле "ТипЗначения" после Наименование/ГруппаКодНаименование.
 */
public class ChartOfCharacteristicTypesFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        FormDsl dsl = new CatalogFormGenerator().generate(meta, preset, purpose);
        String catObj = "CatalogObject." + meta.name;
        String ccObj = "ChartOfCharacteristicTypesObject." + meta.name;
        String catList = "Catalog." + meta.name;
        String ccList = "ChartOfCharacteristicTypes." + meta.name;

        List<FormDsl.Attribute> patchedAttrs = new ArrayList<>();
        for (FormDsl.Attribute a : dsl.getAttributes()) {
            if (catObj.equals(a.getType())) {
                patchedAttrs.add(new FormDsl.Attribute(a.getName(), a.getTitle(), ccObj, a.getMain(), a.getColumns(), a.getSettings(), a.getSavedData()));
            } else if ("DynamicList".equals(a.getType()) && a.getSettings() != null && catList.equals(a.getSettings().get("mainTable"))) {
                Map<String, Object> newSettings = new LinkedHashMap<>(a.getSettings());
                newSettings.put("mainTable", ccList);
                patchedAttrs.add(new FormDsl.Attribute(a.getName(), a.getTitle(), a.getType(), a.getMain(), a.getColumns(), newSettings, a.getSavedData()));
            } else {
                patchedAttrs.add(a);
            }
        }

        List<Map<String, Object>> elements = dsl.getElements();
        if ("item".equals(purpose) && elements != null) {
            elements = injectValueType(elements);
        }

        return new FormDsl(dsl.getTitle(), dsl.getProperties(), dsl.getExcludedCommands(), dsl.getEvents(),
                elements, patchedAttrs, dsl.getParameters(), dsl.getCommands());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> injectValueType(List<Map<String, Object>> elements) {
        List<Map<String, Object>> r = new ArrayList<>();
        boolean inserted = false;
        for (Map<String, Object> el : elements) {
            r.add(el);
            if (inserted) continue;
            Object grp = el.get("group");
            if ("vertical".equals(grp) && "ГруппаШапка".equals(el.get("name"))) {
                // inject inside header group after "Наименование" or "ГруппаКодНаименование"
                List<Map<String, Object>> headerChildren = (List<Map<String, Object>>) el.get("children");
                if (headerChildren != null) {
                    List<Map<String, Object>> newChildren = new ArrayList<>();
                    boolean ins = false;
                    for (Map<String, Object> c : headerChildren) {
                        newChildren.add(c);
                        if (!ins) {
                            String name = (String) (c.get("input") != null ? c.get("input") : c.get("name"));
                            if ("Наименование".equals(name) || "ГруппаКодНаименование".equals(name)) {
                                newChildren.add(DslBuilder.input("ТипЗначения", "Объект.ValueType"));
                                ins = true;
                            }
                        }
                    }
                    if (!ins) newChildren.add(DslBuilder.input("ТипЗначения", "Объект.ValueType"));
                    Map<String, Object> patched = new LinkedHashMap<>(el);
                    patched.put("children", newChildren);
                    r.set(r.size() - 1, patched);
                    inserted = true;
                }
            }
        }
        if (!inserted) r.add(DslBuilder.input("ТипЗначения", "Объект.ValueType"));
        return r;
    }
}

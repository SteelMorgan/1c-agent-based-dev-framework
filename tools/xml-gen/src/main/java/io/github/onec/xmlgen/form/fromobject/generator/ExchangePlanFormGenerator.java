package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExchangePlanFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        FormDsl dsl = new CatalogFormGenerator().generate(meta, preset, purpose);
        String catObj = "CatalogObject." + meta.name;
        String epObj = "ExchangePlanObject." + meta.name;
        String catList = "Catalog." + meta.name;
        String epList = "ExchangePlan." + meta.name;

        List<FormDsl.Attribute> patched = new ArrayList<>();
        for (FormDsl.Attribute a : dsl.getAttributes()) {
            if (catObj.equals(a.getType())) {
                patched.add(new FormDsl.Attribute(a.getName(), a.getTitle(), epObj, a.getMain(), a.getColumns(), a.getSettings(), a.getSavedData()));
            } else if ("DynamicList".equals(a.getType()) && a.getSettings() != null && catList.equals(a.getSettings().get("mainTable"))) {
                Map<String, Object> ns = new LinkedHashMap<>(a.getSettings());
                ns.put("mainTable", epList);
                patched.add(new FormDsl.Attribute(a.getName(), a.getTitle(), a.getType(), a.getMain(), a.getColumns(), ns, a.getSavedData()));
            } else {
                patched.add(a);
            }
        }

        List<Map<String, Object>> elements = dsl.getElements();
        if ("item".equals(purpose) && elements != null) {
            elements = injectSentReceived(elements);
        }
        return new FormDsl(dsl.getTitle(), dsl.getProperties(), dsl.getExcludedCommands(), dsl.getEvents(),
                elements, patched, dsl.getParameters(), dsl.getCommands());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> injectSentReceived(List<Map<String, Object>> elements) {
        Map<String, Object> sent = DslBuilder.input("НомерОтправленного", "Объект.SentNo"); sent.put("readOnly", true);
        Map<String, Object> recv = DslBuilder.input("НомерПринятого", "Объект.ReceivedNo"); recv.put("readOnly", true);

        List<Map<String, Object>> r = new ArrayList<>();
        boolean inserted = false;
        for (Map<String, Object> el : elements) {
            r.add(el);
            if (inserted) continue;
            if ("vertical".equals(el.get("group")) && "ГруппаШапка".equals(el.get("name"))) {
                List<Map<String, Object>> header = (List<Map<String, Object>>) el.get("children");
                if (header != null) {
                    List<Map<String, Object>> nc = new ArrayList<>();
                    boolean ins = false;
                    for (Map<String, Object> c : header) {
                        nc.add(c);
                        if (!ins) {
                            String name = (String) (c.get("input") != null ? c.get("input") : c.get("name"));
                            if ("Наименование".equals(name) || "ГруппаКодНаименование".equals(name)) {
                                nc.add(sent); nc.add(recv);
                                ins = true;
                            }
                        }
                    }
                    if (!ins) { nc.add(sent); nc.add(recv); }
                    Map<String, Object> patched = new LinkedHashMap<>(el);
                    patched.put("children", nc);
                    r.set(r.size() - 1, patched);
                    inserted = true;
                }
            }
        }
        if (!inserted) { r.add(sent); r.add(recv); }
        return r;
    }
}

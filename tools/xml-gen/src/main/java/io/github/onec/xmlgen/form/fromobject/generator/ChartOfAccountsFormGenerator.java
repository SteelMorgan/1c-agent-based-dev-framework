package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChartOfAccountsFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        String key = "chartOfAccounts." + purpose;
        Map<String, Object> p = preset.section(key);
        switch (purpose) {
            case "folder": return folder(meta, p);
            case "list":   return listChoice(meta, preset, false);
            case "choice": return listChoice(meta, preset, true);
            case "item":
            default:       return item(meta, p);
        }
    }

    private FormDsl item(ObjectMeta meta, Map<String, Object> p) {
        Map<String, Object> fd = FormPreset.subMap(p, "fieldDefaults");
        Map<String, Object> parentCfg = FormPreset.subMap(p, "parent");

        List<Map<String, Object>> elements = new ArrayList<>();

        // Header (Code left, Parent right)
        List<Map<String, Object>> headerLeft = new ArrayList<>();
        if (meta.codeLength > 0) headerLeft.add(DslBuilder.input("Код", "Объект.Code"));
        List<Map<String, Object>> headerRight = new ArrayList<>();
        if (meta.hierarchical) {
            String parentTitle = FormPreset.str(parentCfg, "title", "Подчинен счету");
            Map<String, Object> parentEl = DslBuilder.input("Родитель", "Объект.Parent");
            parentEl.put("title", parentTitle);
            headerRight.add(parentEl);
        }
        if (!headerRight.isEmpty()) {
            List<Map<String, Object>> ch = new ArrayList<>();
            ch.add(DslBuilder.group("vertical", "ГруппаШапкаЛево", headerLeft));
            ch.add(DslBuilder.group("vertical", "ГруппаШапкаПраво", headerRight));
            Map<String, Object> hg = DslBuilder.group("horizontal", "ГруппаШапка", ch);
            hg.put("representation", "none");
            elements.add(hg);
        } else {
            elements.addAll(headerLeft);
        }

        if (meta.descriptionLength > 0) {
            elements.add(DslBuilder.input("Наименование", "Объект.Description"));
        }

        // OffBalance
        elements.add(DslBuilder.check("Забалансовый", "Объект.OffBalance"));

        // AccountingFlags
        if (!meta.accountingFlags.isEmpty()) {
            List<Map<String, Object>> flagChildren = new ArrayList<>();
            for (ObjectMeta.Field flag : meta.accountingFlags) {
                flagChildren.add(DslBuilder.check(flag.name, "Объект." + flag.name));
            }
            Map<String, Object> flagGroup = DslBuilder.group("vertical", "ГруппаПризнакиУчета", flagChildren);
            flagGroup.put("title", "Признаки учета");
            flagGroup.put("showTitle", true);
            elements.add(flagGroup);
        }

        // ExtDimensionTypes table
        if (meta.maxExtDimensionCount > 0) {
            List<Map<String, Object>> cols = new ArrayList<>();
            cols.add(DslBuilder.input("ВидСубконто", "Объект.ExtDimensionTypes.ExtDimensionType"));
            cols.add(DslBuilder.check("ТолькоОбороты", "Объект.ExtDimensionTypes.TurnoversOnly"));
            for (ObjectMeta.Field ed : meta.extDimensionAccountingFlags) {
                cols.add(DslBuilder.check(ed.name, "Объект.ExtDimensionTypes." + ed.name));
            }
            elements.add(DslBuilder.simpleTable("ВидыСубконто", "Объект.ExtDimensionTypes", cols));
        }

        // Attributes
        for (ObjectMeta.Field attr : meta.attributes) {
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            elements.add(DslBuilder.field(attr.name, "Объект." + attr.name, attr.type, fd));
        }

        // TSections
        List<String> tsExclude = new ArrayList<>();
        tsExclude.add("ДополнительныеРеквизиты");
        tsExclude.add("Представления");
        for (ObjectMeta.TabularSection ts : meta.tabularSections) {
            if (tsExclude.contains(ts.name)) continue;
            List<Map<String, Object>> cols = new ArrayList<>();
            for (ObjectMeta.Field col : ts.columns) {
                if (!DslBuilder.isDisplayable(col.type)) continue;
                cols.add(DslBuilder.field(ts.name + col.name, "Объект." + ts.name + "." + col.name, col.type, fd));
            }
            elements.add(DslBuilder.simpleTable(ts.name, "Объект." + ts.name, cols));
        }

        Map<String, Object> props = GenUtil.mergeProperties(p);
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.objectAttrSaved("Объект", "ChartOfAccountsObject." + meta.name));

        return new FormDsl(meta.synonym, props, null, null, elements, attrs, null, null);
    }

    private FormDsl folder(ObjectMeta meta, Map<String, Object> p) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (meta.codeLength > 0) elements.add(DslBuilder.input("Код", "Объект.Code"));
        if (meta.descriptionLength > 0) elements.add(DslBuilder.input("Наименование", "Объект.Description"));
        if (meta.hierarchical) {
            String parentTitle = FormPreset.str(FormPreset.subMap(p, "parent"), "title", "Подчинен счету");
            Map<String, Object> parentEl = DslBuilder.input("Родитель", "Объект.Parent");
            parentEl.put("title", parentTitle);
            elements.add(parentEl);
        }

        Map<String, Object> props = GenUtil.mergeProperties(p, "windowOpeningMode", "LockOwnerWindow", "useForFoldersAndItems", "Folders");
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.objectAttrSaved("Объект", "ChartOfAccountsObject." + meta.name));
        return new FormDsl(meta.synonym, props, null, null, elements, attrs, null, null);
    }

    private FormDsl listChoice(ObjectMeta meta, FormPreset preset, boolean choice) {
        FormDsl base = new CatalogFormGenerator().generate(meta, preset, choice ? "choice" : "list");
        String catList = "Catalog." + meta.name;
        String coaList = "ChartOfAccounts." + meta.name;

        List<FormDsl.Attribute> patched = new ArrayList<>();
        for (FormDsl.Attribute a : base.getAttributes()) {
            if ("DynamicList".equals(a.getType()) && a.getSettings() != null && catList.equals(a.getSettings().get("mainTable"))) {
                Map<String, Object> ns = new LinkedHashMap<>(a.getSettings());
                ns.put("mainTable", coaList);
                patched.add(new FormDsl.Attribute(a.getName(), a.getTitle(), a.getType(), a.getMain(), a.getColumns(), ns, a.getSavedData()));
            } else {
                patched.add(a);
            }
        }
        return new FormDsl(base.getTitle(), base.getProperties(), base.getExcludedCommands(), base.getEvents(),
                base.getElements(), patched, base.getParameters(), base.getCommands());
    }
}

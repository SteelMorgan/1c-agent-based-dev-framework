package io.github.onec.xmlgen.form.fromobject.generator;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.fromobject.DslBuilder;
import io.github.onec.xmlgen.form.fromobject.FromObjectException;
import io.github.onec.xmlgen.form.fromobject.ObjectMeta;
import io.github.onec.xmlgen.form.preset.FormPreset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CatalogFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        String key = "catalog." + purpose;
        Map<String, Object> p = preset.section(key);
        switch (purpose) {
            case "folder": return folder(meta, p);
            case "list":   return list(meta, p);
            case "choice": return choice(meta, p, preset);
            case "item":
            default:       return item(meta, p);
        }
    }

    private FormDsl folder(ObjectMeta meta, Map<String, Object> p) {
        List<Map<String, Object>> elements = new ArrayList<>();
        if (meta.codeLength > 0) elements.add(DslBuilder.input("Код", "Объект.Code"));
        elements.add(DslBuilder.input("Наименование", "Объект.Description"));
        Map<String, Object> parentCfg = FormPreset.subMap(p, "parent");
        Map<String, Object> parentEl = DslBuilder.input("Родитель", "Объект.Parent");
        if (parentCfg.containsKey("title")) parentEl.put("title", parentCfg.get("title"));
        elements.add(parentEl);

        Map<String, Object> props = GenUtil.mergeProperties(p, "useForFoldersAndItems", "Folders", "windowOpeningMode", "LockOwnerWindow");
        List<FormDsl.Attribute> attrs = Collections.singletonList(GenUtil.objectAttr("Объект", "CatalogObject." + meta.name));

        return new FormDsl(meta.synonym, props, null, null, elements, attrs, null, null);
    }

    private FormDsl list(ObjectMeta meta, Map<String, Object> p) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(DslBuilder.labelField("Наименование", "Список.Description"));
        if (meta.codeLength > 0) columns.add(DslBuilder.labelField("Код", "Список.Code"));
        for (ObjectMeta.Field attr : meta.attributes) {
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            columns.add(DslBuilder.labelField(attr.name, "Список." + attr.name));
        }
        if (FormPreset.bool(p, "hiddenRef", true)) {
            Map<String, Object> refCol = DslBuilder.labelField("Ссылка", "Список.Ref");
            refCol.put("userVisible", false);
            columns.add(refCol);
        }

        Map<String, Object> table = DslBuilder.table("Список", "Список", columns);
        table.put("rowPictureDataPath", "Список.DefaultPicture");
        if (meta.hierarchical) {
            table.put("initialTreeView", "ExpandTopLevel");
            table.put("enableStartDrag", true);
            table.put("enableDrag", true);
        }
        Map<String, Object> props = GenUtil.mergeProperties(p);
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.dynamicList("Список", "Catalog." + meta.name));
        return new FormDsl(meta.synonym, props, null, null,
                Collections.singletonList(table), attrs, null, null);
    }

    private FormDsl choice(ObjectMeta meta, Map<String, Object> p, FormPreset preset) {
        FormDsl listDsl = list(meta, preset.section("catalog.list"));
        Map<String, Object> props = new java.util.LinkedHashMap<>(listDsl.getProperties() != null ? listDsl.getProperties() : Collections.emptyMap());
        props.putIfAbsent("windowOpeningMode", "LockOwnerWindow");
        for (Map.Entry<String, Object> e : FormPreset.subMap(p, "properties").entrySet()) props.put(e.getKey(), e.getValue());
        // set choiceMode on table
        List<Map<String, Object>> elems = listDsl.getElements();
        if (elems != null && !elems.isEmpty()) {
            Map<String, Object> table = new java.util.LinkedHashMap<>(elems.get(0));
            table.put("choiceMode", true);
            elems = Collections.singletonList(table);
        }
        return new FormDsl(listDsl.getTitle(), props, null, null, elems, listDsl.getAttributes(), null, null);
    }

    private FormDsl item(ObjectMeta meta, Map<String, Object> p) {
        Map<String, Object> fieldDefaults = FormPreset.subMap(p, "fieldDefaults");
        Map<String, Object> cd = FormPreset.subMap(p, "codeDescription");
        Map<String, Object> parentCfg = FormPreset.subMap(p, "parent");
        Map<String, Object> tsCfg = FormPreset.subMap(p, "tabularSections");
        Map<String, Object> addCfg = FormPreset.subMap(p, "additional");
        Map<String, Object> footerCfg = FormPreset.subMap(p, "footer");

        String cdLayout = FormPreset.str(cd, "layout", "horizontal");
        String cdOrder = FormPreset.str(cd, "order", "descriptionFirst");
        boolean hasCode = meta.codeLength > 0;

        List<String> footerFields = GenUtil.strList(footerCfg.get("fields"));
        List<String> tsExclude = GenUtil.strList(tsCfg.get("exclude"));
        if (tsExclude.isEmpty()) {
            tsExclude.add("ДополнительныеРеквизиты");
            tsExclude.add("Представления");
        }
        boolean tsLineNumber = FormPreset.bool(tsCfg, "lineNumber", true);
        boolean bspGroup = FormPreset.bool(addCfg, "bspGroup", true);

        List<Map<String, Object>> headerChildren = new ArrayList<>();

        // Owner
        if (!meta.owners.isEmpty()) {
            Map<String, Object> owner = DslBuilder.input("Владелец", "Объект.Owner");
            owner.put("readOnly", true);
            headerChildren.add(owner);
        }

        // Code + Description
        if ("horizontal".equals(cdLayout) && hasCode) {
            List<Map<String, Object>> cdChildren = new ArrayList<>();
            Map<String, Object> desc = DslBuilder.input("Наименование", "Объект.Description");
            Map<String, Object> code = DslBuilder.input("Код", "Объект.Code");
            if ("codeFirst".equals(cdOrder)) { cdChildren.add(code); cdChildren.add(desc); }
            else { cdChildren.add(desc); cdChildren.add(code); }
            Map<String, Object> grp = DslBuilder.group("horizontal", "ГруппаКодНаименование", cdChildren);
            grp.put("representation", "none");
            headerChildren.add(grp);
        } else {
            headerChildren.add(DslBuilder.input("Наименование", "Объект.Description"));
            if (hasCode) headerChildren.add(DslBuilder.input("Код", "Объект.Code"));
        }

        // Parent
        if (meta.hierarchical) {
            Map<String, Object> parentEl = DslBuilder.input("Родитель", "Объект.Parent");
            if (parentCfg.containsKey("title")) parentEl.put("title", parentCfg.get("title"));
            String parentPos = FormPreset.str(parentCfg, "position", "afterCodeDescription");
            if ("beforeCodeDescription".equals(parentPos)) {
                int insertIdx = meta.owners.isEmpty() ? 0 : 1;
                headerChildren.add(insertIdx, parentEl);
            } else {
                headerChildren.add(parentEl);
            }
        }

        // Attributes to header (excluding footer fields and non-displayable)
        for (ObjectMeta.Field attr : meta.attributes) {
            if (footerFields.contains(attr.name)) continue;
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            headerChildren.add(DslBuilder.field(attr.name, "Объект." + attr.name, attr.type, fieldDefaults));
        }

        List<Map<String, Object>> rootElements = new ArrayList<>();
        Map<String, Object> headerGroup = DslBuilder.group("vertical", "ГруппаШапка", headerChildren);
        headerGroup.put("representation", "none");
        rootElements.add(headerGroup);

        // TSections (inline — как в дефолте для catalog.item)
        for (ObjectMeta.TabularSection ts : meta.tabularSections) {
            if (tsExclude.contains(ts.name)) continue;
            List<Map<String, Object>> tsCols = new ArrayList<>();
            if (tsLineNumber) {
                tsCols.add(DslBuilder.labelField(ts.name + "НомерСтроки", "Объект." + ts.name + ".LineNumber"));
            }
            for (ObjectMeta.Field col : ts.columns) {
                if (!DslBuilder.isDisplayable(col.type)) continue;
                tsCols.add(DslBuilder.field(ts.name + col.name, "Объект." + ts.name + "." + col.name, col.type, fieldDefaults));
            }
            rootElements.add(DslBuilder.simpleTable(ts.name, "Объект." + ts.name, tsCols));
        }

        // Footer fields
        for (String fn : footerFields) {
            ObjectMeta.Field f = findAttr(meta, fn);
            if (f != null) {
                rootElements.add(DslBuilder.field(f.name, "Объект." + f.name, f.type, fieldDefaults));
            }
        }

        // BSP group
        if (bspGroup) {
            rootElements.add(DslBuilder.bareGroup("vertical", "ГруппаДополнительныеРеквизиты"));
        }

        // Properties
        Object[] extra = new Object[0];
        if (meta.hierarchical && "HierarchyFoldersAndItems".equals(meta.hierarchyType)) {
            extra = new Object[] { "useForFoldersAndItems", "Items" };
        }
        Map<String, Object> props = GenUtil.mergeProperties(p, extra);

        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.objectAttr("Объект", "CatalogObject." + meta.name));

        return new FormDsl(meta.synonym, props, null, null, rootElements, attrs, null, null);
    }

    private ObjectMeta.Field findAttr(ObjectMeta meta, String name) {
        for (ObjectMeta.Field a : meta.attributes) if (a.name.equals(name)) return a;
        return null;
    }
}

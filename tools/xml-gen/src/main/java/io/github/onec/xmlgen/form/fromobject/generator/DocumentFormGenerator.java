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

public class DocumentFormGenerator {

    public FormDsl generate(ObjectMeta meta, FormPreset preset, String purpose) {
        String key = "document." + purpose;
        Map<String, Object> p = preset.section(key);
        switch (purpose) {
            case "list":   return list(meta, p);
            case "choice": return choice(meta, p, preset);
            case "item":
            default:       return item(meta, p);
        }
    }

    private FormDsl list(ObjectMeta meta, Map<String, Object> p) {
        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(DslBuilder.labelField("Номер", "Список.Number"));
        columns.add(DslBuilder.labelField("Дата", "Список.Date"));
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

        Map<String, Object> props = GenUtil.mergeProperties(p);
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.dynamicList("Список", "Document." + meta.name));

        return new FormDsl(meta.synonym, props, null, null,
                Collections.singletonList(table), attrs, null, null);
    }

    private FormDsl choice(ObjectMeta meta, Map<String, Object> p, FormPreset preset) {
        FormDsl base = list(meta, preset.section("document.list"));
        Map<String, Object> props = new LinkedHashMap<>(base.getProperties());
        props.putIfAbsent("windowOpeningMode", "LockOwnerWindow");
        for (Map.Entry<String, Object> e : FormPreset.subMap(p, "properties").entrySet()) props.put(e.getKey(), e.getValue());
        return new FormDsl(base.getTitle(), props, null, null, base.getElements(), base.getAttributes(), null, null);
    }

    private FormDsl item(ObjectMeta meta, Map<String, Object> p) {
        Map<String, Object> headerCfg = FormPreset.subMap(p, "header");
        Map<String, Object> footerCfg = FormPreset.subMap(p, "footer");
        Map<String, Object> addCfg = FormPreset.subMap(p, "additional");
        Map<String, Object> tsCfg = FormPreset.subMap(p, "tabularSections");
        Map<String, Object> fieldDefaults = FormPreset.subMap(p, "fieldDefaults");

        String headerPos = FormPreset.str(headerCfg, "position", "insidePage");
        String headerLayout = FormPreset.str(headerCfg, "layout", "2col");
        String headerDistribute = FormPreset.str(headerCfg, "distribute", "even");
        String dateTitle = FormPreset.str(headerCfg, "dateTitle", "от");

        List<String> footerFields = GenUtil.strList(footerCfg.get("fields"));
        String footerPos = FormPreset.str(footerCfg, "position", "insidePage");

        String addPos = FormPreset.str(addCfg, "position", "page");
        String addLayout = FormPreset.str(addCfg, "layout", "2col");
        boolean addBspGroup = FormPreset.bool(addCfg, "bspGroup", true);
        List<String> addLeft = GenUtil.strList(addCfg.get("left"));
        List<String> addRight = GenUtil.strList(addCfg.get("right"));
        List<String> headerRight = GenUtil.strList(headerCfg.get("right"));

        List<String> tsExclude = GenUtil.strList(tsCfg.get("exclude"));
        if (tsExclude.isEmpty()) tsExclude.add("ДополнительныеРеквизиты");
        boolean tsLineNumber = FormPreset.bool(tsCfg, "lineNumber", true);

        // Classify
        Map<String, String> claimed = new LinkedHashMap<>();
        for (String fn : footerFields) claimed.put(fn, "footer");
        for (String fn : headerRight) claimed.put(fn, "header.right");
        for (String fn : addLeft) claimed.put(fn, "additional.left");
        for (String fn : addRight) claimed.put(fn, "additional.right");

        List<ObjectMeta.Field> unclaimed = new ArrayList<>();
        for (ObjectMeta.Field attr : meta.attributes) {
            if (claimed.containsKey(attr.name)) continue;
            if (!DslBuilder.isDisplayable(attr.type)) continue;
            unclaimed.add(attr);
        }

        List<ObjectMeta.Field> leftAttrs;
        List<ObjectMeta.Field> rightExtra;
        if ("left".equals(headerDistribute)) {
            leftAttrs = unclaimed;
            rightExtra = Collections.emptyList();
        } else if ("right".equals(headerDistribute)) {
            leftAttrs = Collections.emptyList();
            rightExtra = unclaimed;
        } else {
            int half = (unclaimed.size() + 1) / 2;
            leftAttrs = unclaimed.subList(0, Math.min(half, unclaimed.size()));
            rightExtra = unclaimed.subList(Math.min(half, unclaimed.size()), unclaimed.size());
        }

        // Number + Date
        Map<String, Object> numberEl = DslBuilder.input("Номер", "Объект.Number");
        numberEl.put("autoMaxWidth", false);
        numberEl.put("width", 9);
        Map<String, Object> dateEl = DslBuilder.input("Дата", "Объект.Date");
        dateEl.put("title", dateTitle);
        List<Map<String, Object>> numDateChildren = new ArrayList<>();
        numDateChildren.add(numberEl);
        numDateChildren.add(dateEl);
        Map<String, Object> numDateGroup = DslBuilder.group("horizontal", "ГруппаНомерДата", numDateChildren);

        // Left
        List<Map<String, Object>> leftChildren = new ArrayList<>();
        leftChildren.add(numDateGroup);
        for (ObjectMeta.Field a : leftAttrs) {
            leftChildren.add(DslBuilder.field(a.name, "Объект." + a.name, a.type, fieldDefaults));
        }

        // Right
        List<Map<String, Object>> rightChildren = new ArrayList<>();
        for (String rn : headerRight) {
            ObjectMeta.Field a = findAttr(meta, rn);
            if (a != null) rightChildren.add(DslBuilder.field(a.name, "Объект." + a.name, a.type, fieldDefaults));
        }
        for (ObjectMeta.Field a : rightExtra) {
            rightChildren.add(DslBuilder.field(a.name, "Объект." + a.name, a.type, fieldDefaults));
        }

        // Header group
        Map<String, Object> headerGroup;
        if ("2col".equals(headerLayout) && !rightChildren.isEmpty()) {
            List<Map<String, Object>> hgChildren = new ArrayList<>();
            hgChildren.add(DslBuilder.group("vertical", "ГруппаШапкаЛево", leftChildren));
            hgChildren.add(DslBuilder.group("vertical", "ГруппаШапкаПраво", rightChildren));
            headerGroup = DslBuilder.group("horizontal", "ГруппаШапка", hgChildren);
            headerGroup.put("representation", "none");
        } else {
            List<Map<String, Object>> all = new ArrayList<>(leftChildren);
            all.addAll(rightChildren);
            List<Map<String, Object>> hgChildren = new ArrayList<>();
            hgChildren.add(DslBuilder.group("vertical", "ГруппаШапкаЛево", all));
            headerGroup = DslBuilder.group("horizontal", "ГруппаШапка", hgChildren);
            headerGroup.put("representation", "none");
        }

        // Footer elements
        List<Map<String, Object>> footerElements = new ArrayList<>();
        for (String fn : footerFields) {
            ObjectMeta.Field f = findAttr(meta, fn);
            if (f != null) footerElements.add(DslBuilder.field(f.name, "Объект." + f.name, f.type, fieldDefaults));
        }

        // Visible TS
        List<ObjectMeta.TabularSection> visibleTs = new ArrayList<>();
        for (ObjectMeta.TabularSection ts : meta.tabularSections) {
            if (!tsExclude.contains(ts.name)) visibleTs.add(ts);
        }

        // Additional page content
        Map<String, Object> additionalPage = null;
        if ("page".equals(addPos)) {
            List<Map<String, Object>> addLeftEls = new ArrayList<>();
            for (String n : addLeft) {
                ObjectMeta.Field f = findAttr(meta, n);
                if (f != null) addLeftEls.add(DslBuilder.field(f.name, "Объект." + f.name, f.type, fieldDefaults));
            }
            List<Map<String, Object>> addRightEls = new ArrayList<>();
            for (String n : addRight) {
                ObjectMeta.Field f = findAttr(meta, n);
                if (f != null) addRightEls.add(DslBuilder.field(f.name, "Объект." + f.name, f.type, fieldDefaults));
            }
            List<Map<String, Object>> pageChildren = new ArrayList<>();
            if ("2col".equals(addLayout)) {
                List<Map<String, Object>> paramChildren = new ArrayList<>();
                paramChildren.add(DslBuilder.group("vertical", "ГруппаПараметрыЛево", addLeftEls));
                paramChildren.add(DslBuilder.group("vertical", "ГруппаПараметрыПраво", addRightEls));
                pageChildren.add(DslBuilder.group("horizontal", "ГруппаПараметры", paramChildren));
            } else {
                pageChildren.addAll(addLeftEls);
                pageChildren.addAll(addRightEls);
            }
            if (addBspGroup) pageChildren.add(DslBuilder.bareGroup("vertical", "ГруппаДополнительныеРеквизиты"));
            additionalPage = DslBuilder.page("ГруппаДополнительно", "Дополнительно", pageChildren);
        }

        // TS pages
        List<Map<String, Object>> tsPages = new ArrayList<>();
        for (ObjectMeta.TabularSection ts : visibleTs) {
            List<Map<String, Object>> cols = new ArrayList<>();
            if (tsLineNumber) cols.add(DslBuilder.labelField(ts.name + "НомерСтроки", "Объект." + ts.name + ".LineNumber"));
            for (ObjectMeta.Field col : ts.columns) {
                if (!DslBuilder.isDisplayable(col.type)) continue;
                cols.add(DslBuilder.field(ts.name + col.name, "Объект." + ts.name + "." + col.name, col.type, fieldDefaults));
            }
            Map<String, Object> tsTable = DslBuilder.simpleTable(ts.name, "Объект." + ts.name, cols);
            List<Map<String, Object>> pageChildren = new ArrayList<>();
            pageChildren.add(tsTable);
            tsPages.add(DslBuilder.page("Группа" + ts.name, ts.synonym, pageChildren));
        }

        // Assemble
        List<Map<String, Object>> rootElements = new ArrayList<>();
        if (visibleTs.isEmpty()) {
            rootElements.add(headerGroup);
            rootElements.addAll(footerElements);
            if (addBspGroup && !"none".equals(addPos)) {
                rootElements.add(DslBuilder.bareGroup("vertical", "ГруппаДополнительныеРеквизиты"));
            }
        } else {
            if ("abovePages".equals(headerPos)) {
                rootElements.add(headerGroup);
                List<Map<String, Object>> pagesChildren = new ArrayList<>(tsPages);
                if (additionalPage != null) pagesChildren.add(additionalPage);
                rootElements.add(DslBuilder.pages("ГруппаСтраницы", pagesChildren));
            } else {
                List<Map<String, Object>> osnovChildren = new ArrayList<>();
                osnovChildren.add(headerGroup);
                if ("insidePage".equals(footerPos) && !footerElements.isEmpty()) osnovChildren.addAll(footerElements);
                List<Map<String, Object>> pagesChildren = new ArrayList<>();
                pagesChildren.add(DslBuilder.page("ГруппаОсновное", "Основное", osnovChildren));
                pagesChildren.addAll(tsPages);
                if (additionalPage != null) pagesChildren.add(additionalPage);
                rootElements.add(DslBuilder.pages("ГруппаСтраницы", pagesChildren));
            }
            if ("belowPages".equals(footerPos) && !footerElements.isEmpty()) rootElements.addAll(footerElements);
        }

        Map<String, Object> props = GenUtil.mergeProperties(p, "autoTitle", false);
        List<FormDsl.Attribute> attrs = Collections.singletonList(
                GenUtil.documentObjectAttr("Объект", "DocumentObject." + meta.name, meta.hasRegisterRecords));

        return new FormDsl(meta.synonym, props, null, null, rootElements, attrs, null, null);
    }

    private ObjectMeta.Field findAttr(ObjectMeta meta, String name) {
        for (ObjectMeta.Field a : meta.attributes) if (a.name.equals(name)) return a;
        return null;
    }
}

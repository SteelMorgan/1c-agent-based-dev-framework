package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.editor.skd.PatchQueryEngine;
import io.github.onec.xmlgen.editor.skd.SkdParseException;
import io.github.onec.xmlgen.editor.skd.SkdShorthandParser;
import io.github.onec.xmlgen.editor.skd.SkdTypeParser;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;

/**
 * Редактор схем СКД (DataCompositionSchema) — точечные patch-операции.
 *
 * <p>Реализует skill {@code skd-edit/SKILL.md} и подраздел SPEC §5.
 *
 * <p>Контракт операций:
 * <ul>
 *     <li>Каждая операция — отдельный метод.</li>
 *     <li>Все методы работают над in-memory {@link XmlDocument}; запись/валидация —
 *         ответственность caller-а (CLI {@code Commands.skdEdit}).</li>
 *     <li>Идемпотентность и duplicate-поведение реализованы по таблице SPEC §5.6.</li>
 *     <li>Warnings собираются в {@link #getWarnings()} для проверки в тестах
 *         и для печати в CLI.</li>
 * </ul>
 *
 * <p>Каждая операция возвращает {@link OpResult#changed} = true если документ был
 * изменён, и false для noop-кейсов.
 */
public class SkdEditor {
    private static final String DCS_COMMON_NS =
            "http://v8.1c.ru/8.1/data-composition-system/common";
    private static final String CURRENT_CONFIG_NS =
            "http://v8.1c.ru/8.1/data/enterprise/current-config";

    private final XmlDocument document;
    private final List<String> warnings = new ArrayList<>();

    public SkdEditor(XmlDocument document) {
        this.document = document;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public static final class OpResult {
        public final boolean changed;
        public final String detail;
        public OpResult(boolean changed, String detail) {
            this.changed = changed;
            this.detail = detail;
        }
        public static OpResult unchanged(String d) { return new OpResult(false, d); }
        public static OpResult changed(String d) { return new OpResult(true, d); }
    }

    // ====================================================================
    // Backward-compatible API (старый CLI и SkdEditorTest)
    // ====================================================================

    /**
     * @deprecated используй {@link #addParameter(String, String)} с shorthand. Сохранено для
     * обратной совместимости с CLI {@code skd add-parameter --name --title --type}.
     */
    @Deprecated
    public void addParameter(String name, String title, String type) {
        XmlNode root = document.getRoot();
        XmlNode param = createNode("parameter");

        XmlNode nameNode = createNode("name");
        nameNode.setText(name);
        param.addChild(nameNode);

        XmlNode titleNode = createNode("title");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(title != null ? title : name);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        titleNode.addChild(v8Item);
        param.addChild(titleNode);

        XmlNode typeNode = createNode("valueType");
        XmlNode v8Type = createNode("v8:Type");
        v8Type.setText(type != null ? type : "xs:string");
        typeNode.addChild(v8Type);
        param.addChild(typeNode);

        root.addChild(param);
    }

    /**
     * @deprecated сохранено для обратной совместимости с CLI legacy form.
     */
    @Deprecated
    public void addField(String datasetName, String name, String path, String title) {
        XmlNode dataSet = findDataSet(datasetName);
        if (dataSet == null) {
            throw new IllegalArgumentException("DataSet not found: " + datasetName);
        }
        XmlNode field = createNode("field");
        field.setAttribute("xsi:type", "DataSetFieldField");

        XmlNode dataPathNode = createNode("dataPath");
        dataPathNode.setText(path);
        field.addChild(dataPathNode);

        XmlNode fieldNode = createNode("field");
        fieldNode.setText(name);
        field.addChild(fieldNode);

        XmlNode titleNode = createNode("title");
        titleNode.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(title != null ? title : name);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        titleNode.addChild(v8Item);
        field.addChild(titleNode);

        insertDataSetField(dataSet, field);
    }

    // ====================================================================
    // dataset / variant resolution
    // ====================================================================

    /** Найти именованный dataSet или вернуть первый (с warning) если name null. */
    public XmlNode resolveDataSet(String name) {
        List<XmlNode> sets = document.getRoot().children("dataSet");
        if (sets.isEmpty()) {
            throw new IllegalStateException("schema has no dataSets");
        }
        if (name != null) {
            for (XmlNode ds : sets) {
                if (name.equals(ds.childText("name"))) return ds;
            }
            throw new IllegalArgumentException("dataSet '" + name + "' not found");
        }
        if (sets.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < sets.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(sets.get(i).childText("name"));
            }
            warn("dataSets: " + names + "; using first (" + sets.get(0).childText("name") + ")");
        }
        return sets.get(0);
    }

    /** Найти именованный variant или вернуть первый (с warning) если name null. */
    public XmlNode resolveVariant(String name) {
        List<XmlNode> variants = document.getRoot().children("settingsVariant");
        if (variants.isEmpty()) return null;
        if (name != null) {
            for (XmlNode v : variants) {
                if (name.equals(childTextLocal(v, "name"))) return v;
            }
            throw new IllegalArgumentException("settings variant '" + name + "' not found");
        }
        if (variants.size() > 1) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < variants.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(childTextLocal(variants.get(i), "name"));
            }
            warn("variants: " + names + "; using first (" + childTextLocal(variants.get(0), "name") + ")");
        }
        return variants.get(0);
    }

    private XmlNode findDataSet(String name) {
        return document.getRoot().children("dataSet").stream()
                .filter(ds -> name.equals(ds.childText("name")))
                .findFirst()
                .orElse(null);
    }

    private XmlNode findFieldByDataPath(XmlNode dataSet, String dataPath) {
        for (XmlNode field : dataSet.children("field")) {
            if (dataPath.equals(field.childText("dataPath"))) {
                return field;
            }
        }
        XmlNode legacyFields = dataSet.child("fields");
        if (legacyFields != null) {
            for (XmlNode field : legacyFields.children("field")) {
                if (dataPath.equals(field.childText("dataPath"))) {
                    return field;
                }
            }
        }
        return null;
    }

    private void insertDataSetField(XmlNode dataSet, XmlNode field) {
        List<XmlNode> children = dataSet.getChildren();
        int insertAt = 0;
        for (int i = 0; i < children.size(); i++) {
            String name = children.get(i).getName();
            if ("field".equals(name)) {
                insertAt = i + 1;
            } else if ("name".equals(name) && insertAt == 0) {
                insertAt = i + 1;
            }
        }
        children.add(insertAt, field);
    }

    // ====================================================================
    // FIELDS
    // ====================================================================

    public OpResult addField(SkdShorthandParser.FieldDescriptor fd, String dataSetName,
                              String variantName, boolean noSelection) {
        XmlNode ds = resolveDataSet(dataSetName);

        // duplicate check by dataPath
        if (findFieldByDataPath(ds, fd.name) != null) {
            warn("add-field: '" + fd.name + "' already exists; skipped");
            return OpResult.unchanged("duplicate");
        }

        XmlNode field = createNode("field");
        field.setAttribute("xsi:type", "DataSetFieldField");

        XmlNode dataPath = createNode("dataPath");
        dataPath.setText(fd.name);
        field.addChild(dataPath);

        XmlNode fieldName = createNode("field");
        fieldName.setText(fd.name);
        field.addChild(fieldName);

        if (fd.title != null) {
            field.addChild(buildLocalStringType("title", fd.title));
        }
        if (fd.type != null) {
            field.addChild(buildValueType(fd.type));
        }
        if (fd.role != null) {
            XmlNode role = createNode("role");
            role.setAttribute("xmlns:dcscom", DCS_COMMON_NS);
            addRoleFlag(role, fd.role);
            field.addChild(role);
        }
        if (!fd.restrictions.isEmpty()) {
            field.addChild(buildUseRestriction(fd.restrictions));
        }
        insertDataSetField(ds, field);

        // Add to selection
        if (!noSelection) {
            XmlNode variant = resolveVariant(variantName);
            if (variant != null) {
                addToSelection(variant, fd.name);
            }
        }
        return OpResult.changed("added");
    }

    public OpResult modifyField(SkdShorthandParser.FieldDescriptor fd, String dataSetName) {
        XmlNode ds = resolveDataSet(dataSetName);
        XmlNode target = findFieldByDataPath(ds, fd.name);
        if (target == null) {
            warn("modify-field: '" + fd.name + "' not found; skipped");
            return OpResult.unchanged("not found");
        }
        boolean changed = false;
        if (fd.title != null) {
            replaceChild(target, "title", buildLocalStringType("title", fd.title));
            changed = true;
        }
        if (fd.type != null) {
            replaceChild(target, "valueType", buildValueType(fd.type));
            changed = true;
        }
        if (!fd.restrictions.isEmpty()) {
            replaceChild(target, "useRestriction", buildUseRestriction(fd.restrictions));
            changed = true;
        }
        // modify-field intentionally does NOT touch <role>
        return changed ? OpResult.changed("modified") : OpResult.unchanged("noop");
    }

    public OpResult removeField(String dataPath, String dataSetName, String variantName) {
        XmlNode ds = resolveDataSet(dataSetName);
        boolean removed = false;
        Iterator<XmlNode> direct = ds.getChildren().iterator();
        while (direct.hasNext()) {
            XmlNode f = direct.next();
            if (!"field".equals(f.getName())) continue;
            if (dataPath.equals(f.childText("dataPath"))) {
                direct.remove();
                removed = true;
            }
        }
        XmlNode legacyFields = ds.child("fields");
        if (legacyFields != null) {
            Iterator<XmlNode> legacy = legacyFields.getChildren().iterator();
            while (legacy.hasNext()) {
                XmlNode f = legacy.next();
                if (!"field".equals(f.getName())) continue;
                if (dataPath.equals(f.childText("dataPath"))) {
                    legacy.remove();
                    removed = true;
                }
            }
        }
        // Remove from variant selection (and any nested folders)
        XmlNode variant = resolveVariant(variantName);
        if (variant != null) {
            removeFromSelectionRecursive(variant, dataPath);
        }
        if (!removed) {
            warn("remove-field: '" + dataPath + "' not found; noop");
            return OpResult.unchanged("noop");
        }
        return OpResult.changed("removed");
    }

    public OpResult setFieldRole(SkdShorthandParser.FieldRoleDescriptor d, String dataSetName) {
        XmlNode ds = resolveDataSet(dataSetName);
        XmlNode target = findFieldByDataPath(ds, d.dataPath);
        if (target == null) {
            throw new IllegalArgumentException("set-field-role: field '" + d.dataPath + "' not found");
        }

        XmlNode newRole = buildRoleNode(d);
        XmlNode oldRole = target.child("role");

        if (oldRole != null && rolesEquivalent(oldRole, newRole)) {
            return OpResult.unchanged("idempotent");
        }
        if (newRole == null) {
            if (oldRole == null) return OpResult.unchanged("noop");
            target.getChildren().remove(oldRole);
            return OpResult.changed("role removed");
        }
        if (oldRole == null) {
            // Insert role: append (order doesn't matter for compliance)
            target.addChild(newRole);
        } else {
            int idx = target.getChildren().indexOf(oldRole);
            target.getChildren().set(idx, newRole);
        }
        return OpResult.changed("role set");
    }

    private XmlNode buildRoleNode(SkdShorthandParser.FieldRoleDescriptor d) {
        if (d.flags.isEmpty() && d.kv.isEmpty()) {
            return null;
        }
        XmlNode role = createNode("role");
        role.setAttribute("xmlns:dcscom", DCS_COMMON_NS);
        // Flags become boolean children
        for (String flag : d.flags) {
            addRoleFlag(role, flag);
        }
        // kv become children with text
        for (Map.Entry<String, String> e : d.kv.entrySet()) {
            role.addChild(simpleTextNode(prefixDcsCommon(e.getKey()), e.getValue()));
        }
        return role;
    }

    /** Сравнить две роли по составу детей (имя+текст). */
    private boolean rolesEquivalent(XmlNode a, XmlNode b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        Map<String, String> ma = new LinkedHashMap<>();
        Map<String, String> mb = new LinkedHashMap<>();
        for (XmlNode c : a.getChildren()) ma.put(c.getName(), c.getText() == null ? "" : c.getText());
        for (XmlNode c : b.getChildren()) mb.put(c.getName(), c.getText() == null ? "" : c.getText());
        return ma.equals(mb);
    }

    private void addRoleFlag(XmlNode role, String flag) {
        String normalized = flag.startsWith("@") ? flag.substring(1) : flag;
        switch (normalized) {
            case "period":
                role.addChild(simpleTextNode("dcscom:periodNumber", "1"));
                role.addChild(simpleTextNode("dcscom:periodType", "Main"));
                break;
            case "account":
                role.addChild(simpleTextNode("dcscom:account", "true"));
                break;
            case "dimension":
            case "balance":
                role.addChild(simpleTextNode("dcscom:" + normalized, "true"));
                break;
            case "resource":
                role.addChild(simpleTextNode("dcscom:ignoreNullValues", "true"));
                break;
            default:
                role.addChild(simpleTextNode(prefixDcsCommon(normalized), "true"));
                break;
        }
    }

    private String prefixDcsCommon(String name) {
        if (name == null || name.isEmpty() || name.contains(":")) {
            return name;
        }
        return "dcscom:" + name;
    }

    // ====================================================================
    // PARAMETERS
    // ====================================================================

    public OpResult addParameter(SkdShorthandParser.ParameterDescriptor p) {
        XmlNode root = document.getRoot();
        for (XmlNode existing : root.children("parameter")) {
            if (p.name.equals(existing.childText("name"))) {
                warn("add-parameter: '" + p.name + "' already exists; skipped");
                return OpResult.unchanged("duplicate");
            }
        }
        // @autoDates only valid for StandardPeriod
        if (p.flags.contains("autoDates")) {
            if (!descriptorIsStandardPeriod(p)) {
                throw new IllegalArgumentException(
                        "add-parameter: @autoDates is only valid for StandardPeriod type");
            }
        }
        insertRootChild(buildParameterNode(p), "parameter");
        if (p.flags.contains("autoDates")) {
            addDerivedDateParameterIfMissing("ДатаНачала", "&" + p.name + ".ДатаНачала");
            addDerivedDateParameterIfMissing("ДатаОкончания", "&" + p.name + ".ДатаОкончания");
        }
        return OpResult.changed("added");
    }

    public OpResult modifyParameter(SkdShorthandParser.ParameterDescriptor p) {
        XmlNode root = document.getRoot();
        XmlNode target = null;
        for (XmlNode existing : root.children("parameter")) {
            if (p.name.equals(existing.childText("name"))) { target = existing; break; }
        }
        if (target == null) {
            warn("modify-parameter: '" + p.name + "' not found; skipped");
            return OpResult.unchanged("not found");
        }
        boolean changed = false;
        if (p.title != null) {
            replaceChild(target, "title", buildLocalStringType("title", p.title));
            changed = true;
        }
        // kv: value, use, denyIncompleteValues, ...
        for (Map.Entry<String, String> e : p.kv.entrySet()) {
            String key = e.getKey();
            String val = e.getValue();
            if ("value".equalsIgnoreCase(key)) {
                replaceParameterValue(target, val, p);
                changed = true;
            } else {
                replaceChild(target, key, simpleTextNode(key, val));
                changed = true;
            }
        }
        if (p.availableValues != null) {
            // FULL replacement
            removeAllChildren(target, "availableValue");
            replaceChild(target, "availableValues", buildAvailableValues(p.availableValues));
            changed = true;
        }
        // Flags (idempotent)
        for (String flag : p.flags) {
            if ("autoDates".equals(flag) && !parameterNodeIsStandardPeriod(target)) {
                throw new IllegalArgumentException(
                        "modify-parameter: @autoDates is only valid for StandardPeriod type");
            }
            if (applyParameterFlag(target, flag)) {
                changed = true;
            }
            if ("autoDates".equals(flag)) {
                boolean startAdded = addDerivedDateParameterIfMissing(
                        "ДатаНачала", "&" + p.name + ".ДатаНачала");
                boolean endAdded = addDerivedDateParameterIfMissing(
                        "ДатаОкончания", "&" + p.name + ".ДатаОкончания");
                if (startAdded || endAdded) {
                    changed = true;
                }
            }
        }
        if (!changed && p.title == null && p.kv.isEmpty()
                && p.availableValues == null && p.flags.isEmpty()) {
            warn("modify-parameter: no changes for '" + p.name + "'; noop");
        }
        return changed ? OpResult.changed("modified") : OpResult.unchanged("noop");
    }

    private boolean addDerivedDateParameterIfMissing(String name, String expression) {
        XmlNode root = document.getRoot();
        for (XmlNode existing : root.children("parameter")) {
            if (name.equals(existing.childText("name"))) {
                warn("add-parameter @autoDates: derived parameter '" + name + "' already exists; skipped");
                return false;
            }
        }
        XmlNode param = createNode("parameter");
        param.addChild(simpleTextNode("name", name));
        param.addChild(buildDateTimeValueType());
        param.addChild(simpleTextNode("useRestriction", "true"));
        param.addChild(simpleTextNode("expression", expression));
        param.addChild(simpleTextNode("availableAsField", "false"));
        insertRootChild(param, "parameter");
        return true;
    }

    private XmlNode buildDateTimeValueType() {
        XmlNode vt = createNode("valueType");
        vt.addChild(simpleTextNode("v8:Type", "xs:dateTime"));
        XmlNode dq = createNode("v8:DateQualifiers");
        dq.addChild(simpleTextNode("v8:DateFractions", "DateTime"));
        vt.addChild(dq);
        return vt;
    }

    private boolean applyParameterFlag(XmlNode target, String flag) {
        switch (flag) {
            case "hidden": {
                boolean c1 = replaceTextChildIfDifferent(target, "useRestriction", "true");
                boolean c2 = replaceTextChildIfDifferent(target, "availableAsField", "false");
                return c1 || c2;
            }
            case "always":
                return replaceTextChildIfDifferent(target, "use", "Always");
            case "autoDates": {
                boolean c1 = replaceTextChildIfDifferent(target, "use", "Always");
                boolean c2 = replaceTextChildIfDifferent(target, "denyIncompleteValues", "true");
                return c1 || c2;
            }
            case "valueList":
                return replaceTextChildIfDifferent(target, "valueListAllowed", "true");
            default:
                return replaceTextChildIfDifferent(target, flag, "true");
        }
    }

    private boolean replaceTextChildIfDifferent(XmlNode parent, String childName, String text) {
        XmlNode existing = parent.child(childName);
        if (existing != null && text.equals(existing.getText())) {
            return false;
        }
        replaceChild(parent, childName, simpleTextNode(childName, text));
        return true;
    }

    private boolean descriptorIsStandardPeriod(SkdShorthandParser.ParameterDescriptor p) {
        return p.type != null && p.type.size() == 1
                && "v8:StandardPeriod".equals(p.type.get(0).xmlType);
    }

    private boolean parameterNodeIsStandardPeriod(XmlNode parameter) {
        XmlNode valueType = parameter.child("valueType");
        if (valueType == null) return false;
        for (XmlNode child : valueType.getChildren()) {
            if ("Type".equals(child.getName()) && "v8:StandardPeriod".equals(child.getText())) {
                return true;
            }
        }
        return false;
    }

    public OpResult removeParameter(String name) {
        XmlNode root = document.getRoot();
        Iterator<XmlNode> it = root.getChildren().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            XmlNode n = it.next();
            if ("parameter".equals(n.getName()) && name.equals(n.childText("name"))) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) {
            warn("remove-parameter: '" + name + "' not found; skipped");
            return OpResult.unchanged("not found");
        }
        return OpResult.changed("removed");
    }

    public OpResult renameParameter(String oldName, String newName) {
        XmlNode root = document.getRoot();
        XmlNode target = null;
        for (XmlNode p : root.children("parameter")) {
            if (oldName.equals(p.childText("name"))) target = p;
            else if (newName.equals(p.childText("name"))) {
                throw new IllegalArgumentException("rename-parameter: target name '"
                        + newName + "' is already taken");
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("rename-parameter: '" + oldName + "' not found");
        }
        XmlNode nameNode = target.child("name");
        nameNode.setText(newName);

        // Update &OldName in expression values of other parameters
        String oldRef = "&" + oldName;
        String newRef = "&" + newName;
        for (XmlNode p : root.children("parameter")) {
            XmlNode value = p.child("value");
            if (value != null && value.getText() != null) {
                // Whole-token replacement: only exact &OldName not preceded/followed by id-char
                String replaced = replaceTokenRef(value.getText(), oldRef, newRef);
                if (!replaced.equals(value.getText())) {
                    value.setText(replaced);
                }
            }
        }

        // Update dataParameters references in all variants
        for (XmlNode v : root.children("settingsVariant")) {
            XmlNode settings = findChildLocal(v, "settings");
            if (settings == null) continue;
            XmlNode dataParameters = findChildLocal(settings, "dataParameters");
            if (dataParameters == null) continue;
            for (XmlNode item : dataParameters.getChildren()) {
                XmlNode pName = item.child("parameter");
                if (pName != null && oldName.equals(pName.getText())) {
                    pName.setText(newName);
                }
            }
        }
        return OpResult.changed("renamed");
    }

    public OpResult reorderParameters(List<String> order) {
        // Check duplicates
        Set<String> seen = new LinkedHashSet<>();
        for (String n : order) {
            if (!seen.add(n)) {
                throw new IllegalArgumentException("reorder-parameters: duplicate '" + n + "'");
            }
        }
        XmlNode root = document.getRoot();
        List<XmlNode> params = new ArrayList<>(root.children("parameter"));
        if (params.isEmpty()) {
            warn("reorder-parameters: no parameters to reorder");
            return OpResult.unchanged("empty");
        }
        Map<String, XmlNode> byName = new LinkedHashMap<>();
        for (XmlNode p : params) byName.put(p.childText("name"), p);

        // Build new order: requested first, then remaining
        List<XmlNode> newOrder = new ArrayList<>();
        for (String n : order) {
            XmlNode p = byName.get(n);
            if (p == null) {
                warn("reorder-parameters: '" + n + "' not in schema; skipped");
                continue;
            }
            newOrder.add(p);
        }
        for (XmlNode p : params) {
            if (!newOrder.contains(p)) newOrder.add(p);
        }
        if (newOrder.equals(params)) {
            return OpResult.unchanged("idempotent");
        }
        // Remove old, insert new
        root.getChildren().removeAll(params);
        for (XmlNode p : newOrder) insertRootChild(p, "parameter");
        return OpResult.changed("reordered");
    }

    private XmlNode buildParameterNode(SkdShorthandParser.ParameterDescriptor p) {
        XmlNode param = createNode("parameter");
        param.addChild(simpleTextNode("name", p.name));
        if (p.title != null) {
            param.addChild(buildLocalStringType("title", p.title));
        }
        if (p.type != null) {
            param.addChild(buildValueType(p.type));
        }
        if (p.value != null) {
            replaceParameterValue(param, p.value, p);
        }
        if (p.availableValues != null) {
            param.addChild(buildAvailableValues(p.availableValues));
        }
        for (String flag : p.flags) {
            applyParameterFlag(param, flag);
        }
        return param;
    }

    private void replaceParameterValue(XmlNode param, String rawValue,
                                       SkdShorthandParser.ParameterDescriptor p) {
        XmlNode v = createNode("value");
        // Pick xsi:type from type
        String xsi = parameterValueType(param, p);
        v.setAttribute("xsi:type", xsi);
        if ("v8:StandardPeriod".equals(xsi)) {
            XmlNode variant = simpleTextNode("v8:variant", rawValue);
            variant.setAttribute("xsi:type", "v8:StandardPeriodVariant");
            v.addChild(variant);
        } else {
            v.setText(rawValue);
        }
        replaceChild(param, "value", v);
    }

    private String parameterValueType(XmlNode param, SkdShorthandParser.ParameterDescriptor p) {
        if (p.type != null && p.type.size() == 1) {
            return p.type.get(0).xmlType;
        }
        XmlNode valueType = param.child("valueType");
        if (valueType != null) {
            for (XmlNode child : valueType.getChildren()) {
                if ("Type".equals(child.getName()) && child.getText() != null
                        && !child.getText().isEmpty()) {
                    return child.getText();
                }
            }
        }
        return "xs:string";
    }

    private XmlNode buildAvailableValues(List<SkdShorthandParser.AvailableValueItem> values) {
        XmlNode wrapper = createNode("availableValues");
        for (SkdShorthandParser.AvailableValueItem item : values) {
            XmlNode av = createNode("item");
            av.addChild(simpleTextNode("value", item.value));
            if (item.presentation != null) {
                av.addChild(simpleTextNode("presentation", item.presentation));
            }
            wrapper.addChild(av);
        }
        return wrapper;
    }

    private void insertRootChild(XmlNode node, String kind) {
        List<String> following;
        switch (kind) {
            case "dataSetLink":
                following = List.of("calculatedField", "totalField", "parameter",
                        "template", "groupTemplate", "settingsVariant");
                break;
            case "calculatedField":
                following = List.of("totalField", "parameter", "template",
                        "groupTemplate", "settingsVariant");
                break;
            case "totalField":
                following = List.of("parameter", "template", "groupTemplate", "settingsVariant");
                break;
            case "parameter":
                following = List.of("template", "groupTemplate", "settingsVariant");
                break;
            default:
                document.getRoot().addChild(node);
                return;
        }
        List<XmlNode> children = document.getRoot().getChildren();
        for (int i = 0; i < children.size(); i++) {
            if (following.contains(children.get(i).getName())) {
                children.add(i, node);
                return;
            }
        }
        document.getRoot().addChild(node);
    }

    // ====================================================================
    // TOTALS
    // ====================================================================

    public OpResult addTotal(SkdShorthandParser.TotalDescriptor t) {
        XmlNode root = document.getRoot();
        for (XmlNode tf : root.children("totalField")) {
            if (t.dataPath.equals(tf.childText("dataPath"))) {
                warn("add-total: '" + t.dataPath + "' already exists; skipped");
                return OpResult.unchanged("duplicate");
            }
        }
        XmlNode tn = createNode("totalField");
        tn.addChild(simpleTextNode("dataPath", t.dataPath));
        tn.addChild(simpleTextNode("expression", t.expression));
        insertRootChild(tn, "totalField");
        return OpResult.changed("added");
    }

    public OpResult removeTotal(String dataPath) {
        XmlNode root = document.getRoot();
        Iterator<XmlNode> it = root.getChildren().iterator();
        boolean removed = false;
        while (it.hasNext()) {
            XmlNode n = it.next();
            if ("totalField".equals(n.getName()) && dataPath.equals(n.childText("dataPath"))) {
                it.remove();
                removed = true;
            }
        }
        if (!removed) {
            warn("remove-total: '" + dataPath + "' not found; noop");
            return OpResult.unchanged("noop");
        }
        return OpResult.changed("removed");
    }

    // ====================================================================
    // STRUCTURE
    // ====================================================================

    public OpResult modifyStructure(SkdShorthandParser.StructureSpec s, String variantName) {
        XmlNode variant = resolveVariant(variantName);
        if (variant == null) {
            throw new IllegalArgumentException("modify-structure: no settingsVariant found");
        }
        XmlNode settings = findChildLocal(variant, "settings");
        if (settings == null) {
            throw new IllegalArgumentException("modify-structure: variant has no settings");
        }
        //**agent TASK-174 [07.06.2026 11:10:00]
        //XmlNode structure = findChildLocal(settings, "structure");
        //if (structure == null) {
        //    throw new IllegalArgumentException("modify-structure: variant has no structure");
        //}
        // Канон платформы: элементы структуры — прямые <dcsset:item xsi:type="...StructureItem*">
        // под dcsset:settings; обёртки <dcsset:structure> в сериализации НЕТ (1c-dcs-spec.md §11.1).
        // Прежний код требовал обёртку → на любой реальной (канонической) схеме падал
        // "variant has no structure". Fallback на <structure> оставлен для файлов,
        // сгенерированных старым writer'ом до фикса TASK-174.
        XmlNode structure = findChildLocal(settings, "structure");
        XmlNode container = structure != null ? structure : settings;
        //**agent TASK-174
        // Find group with matching @name via dcsset:name
        XmlNode group = findStructureGroup(container, s.groupName);
        if (group == null) {
            throw new IllegalArgumentException("modify-structure: group '" + s.groupName + "' not found");
        }
        // Replace groupItems
        XmlNode newGroupItems = createNode("dcsset:groupItems");
        for (String item : s.groupItems) {
            if ("details".equalsIgnoreCase(item)) {
                XmlNode gi = createNode("dcsset:item");
                gi.setAttribute("xsi:type", "dcsset:GroupItemDetails");
                newGroupItems.addChild(gi);
            } else {
                XmlNode gi = createNode("dcsset:item");
                gi.setAttribute("xsi:type", "dcsset:GroupItemField");
                gi.addChild(simpleTextNode("dcsset:field", item));
                newGroupItems.addChild(gi);
            }
        }
        // Replace existing groupItems
        XmlNode existing = findChildLocal(group, "groupItems");
        if (existing != null) {
            int idx = group.getChildren().indexOf(existing);
            group.getChildren().set(idx, newGroupItems);
        } else {
            // insert before selection/order/filter/CA/outputParameters
            group.addChild(newGroupItems);
        }
        return OpResult.changed("structure modified");
    }

    /** Найти группу в structure (рекурсивно) по локальному {@code <name>}. */
    private XmlNode findStructureGroup(XmlNode container, String groupName) {
        for (XmlNode item : container.getChildren()) {
            // <item xsi:type=".StructureItemGroup">...<dcsset:name>X</dcsset:name>...
            String name = childTextLocal(item, "name");
            if (groupName.equals(name)) return item;
            // search nested structures
            for (XmlNode nested : item.getChildren()) {
                String ln = nested.getName();
                if ("structure".equals(ln) || "groups".equals(ln) || "subgroups".equals(ln)) {
                    XmlNode found = findStructureGroup(nested, groupName);
                    if (found != null) return found;
                }
                //++agent TASK-174 [07.06.2026 11:10:00]
                // Канон: вложенная группировка — прямой дочерний <dcsset:item
                // xsi:type="...StructureItem*"> родительской группировки (1c-dcs-spec.md §11.8).
                // Без спуска в item вложенные группы канонических схем не находились.
                if ("item".equals(ln) && nested.attr("xsi:type") != null
                        && nested.attr("xsi:type").contains("StructureItem")) {
                    String nestedName = childTextLocal(nested, "name");
                    if (groupName.equals(nestedName)) return nested;
                    XmlNode found = findStructureGroup(nested, groupName);
                    if (found != null) return found;
                }
                //++agent TASK-174
            }
        }
        return null;
    }

    // ====================================================================
    // QUERY
    // ====================================================================

    public OpResult setQuery(String text, String dataSetName) {
        XmlNode ds = resolveDataSet(dataSetName);
        XmlNode query = ds.child("query");
        if (query == null) {
            query = createNode("query");
            ds.addChild(query);
        }
        String oldText = query.getText() == null ? "" : query.getText();
        if (oldText.equals(text)) {
            return OpResult.unchanged("idempotent");
        }
        query.setText(text);
        return OpResult.changed("query set");
    }

    public OpResult patchQuery(String spec, String dataSetName) {
        SkdShorthandParser.ArrowSpec arrow = SkdShorthandParser.parseArrow(spec, true);
        XmlNode ds = resolveDataSet(dataSetName);
        XmlNode query = ds.child("query");
        if (query == null) {
            throw new SkdParseException("patch-query: dataset has no <query>", -1);
        }
        String old = query.getText() == null ? "" : query.getText();
        PatchQueryEngine.OnceMode mode = arrow.once
                ? PatchQueryEngine.OnceMode.ON
                : PatchQueryEngine.OnceMode.OFF;
        PatchQueryEngine.Result r = PatchQueryEngine.replace(old, arrow.oldText, arrow.newText, mode);
        query.setText(r.text);
        return OpResult.changed("patched " + r.replacements + " occurrence(s)");
    }

    // ====================================================================
    // CONDITIONAL APPEARANCE
    // ====================================================================

    public OpResult clearConditionalAppearance(String variantName) {
        XmlNode variant = resolveVariant(variantName);
        if (variant == null) return OpResult.unchanged("no variants");
        XmlNode settings = findChildLocal(variant, "settings");
        if (settings == null) return OpResult.unchanged("no settings");
        XmlNode ca = findChildLocal(settings, "conditionalAppearance");
        if (ca == null) {
            return OpResult.unchanged("no CA");
        }
        boolean wasEmpty = ca.getChildren().isEmpty();
        ca.getChildren().clear();
        return wasEmpty ? OpResult.unchanged("already empty") : OpResult.changed("CA cleared");
    }

    // ====================================================================
    // helpers — building / mutating XML
    // ====================================================================

    private void warn(String msg) {
        warnings.add(msg);
        System.err.println("[WARN] " + msg);
    }

    private XmlNode simpleTextNode(String name, String text) {
        XmlNode n = createNode(name);
        n.setText(text);
        return n;
    }

    private XmlNode buildLocalStringType(String name, String content) {
        XmlNode node = createNode(name);
        node.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode item = createNode("v8:item");
        item.addChild(simpleTextNode("v8:lang", "ru"));
        item.addChild(simpleTextNode("v8:content", content));
        node.addChild(item);
        return node;
    }

    private XmlNode buildValueType(List<SkdTypeParser.TypePart> parts) {
        XmlNode vt = createNode("valueType");
        for (SkdTypeParser.TypePart tp : parts) {
            XmlNode type = simpleTextNode("v8:Type", tp.xmlType);
            if (tp.xmlType.startsWith("d5p1:")) {
                type.setAttribute("xmlns:d5p1", CURRENT_CONFIG_NS);
            }
            vt.addChild(type);
            if (tp.stringLength != null) {
                XmlNode sq = createNode("v8:StringQualifiers");
                sq.addChild(simpleTextNode("v8:Length", tp.stringLength.toString()));
                sq.addChild(simpleTextNode("v8:AllowedLength", "Variable"));
                vt.addChild(sq);
            }
            if (tp.numberDigits != null) {
                XmlNode nq = createNode("v8:NumberQualifiers");
                nq.addChild(simpleTextNode("v8:Digits", tp.numberDigits.toString()));
                nq.addChild(simpleTextNode("v8:FractionDigits",
                        tp.numberFractionDigits != null ? tp.numberFractionDigits.toString() : "0"));
                nq.addChild(simpleTextNode("v8:AllowedSign", tp.nonneg ? "Nonnegative" : "Any"));
                vt.addChild(nq);
            }
        }
        return vt;
    }

    private XmlNode buildUseRestriction(List<String> restrictions) {
        XmlNode ur = createNode("useRestriction");
        for (String r : restrictions) {
            String name;
            switch (r) {
                case "noField": name = "field"; break;
                case "noFilter":
                case "noCondition": name = "condition"; break;
                case "noGroup": name = "group"; break;
                case "noOrder": name = "order"; break;
                default: name = r; break;
            }
            ur.addChild(simpleTextNode(name, "true"));
        }
        return ur;
    }

    private void replaceChild(XmlNode parent, String childName, XmlNode replacement) {
        XmlNode existing = parent.child(childName);
        if (existing != null) {
            int idx = parent.getChildren().indexOf(existing);
            parent.getChildren().set(idx, replacement);
        } else {
            parent.addChild(replacement);
        }
    }

    private void removeAllChildren(XmlNode parent, String childName) {
        parent.getChildren().removeIf(c -> childName.equals(c.getName()));
    }

    private void addToSelection(XmlNode variant, String fieldName) {
        XmlNode settings = findChildLocal(variant, "settings");
        if (settings == null) {
            settings = createNode("dcsset:settings");
            variant.addChild(settings);
        }
        XmlNode selection = findChildLocal(settings, "selection");
        if (selection == null) {
            selection = createNode("dcsset:selection");
            settings.addChild(selection);
        }
        // duplicate check
        for (XmlNode item : selection.getChildren()) {
            String f = childTextLocal(item, "field");
            if (fieldName.equals(f)) return;
        }
        XmlNode item = createNode("dcsset:item");
        item.setAttribute("xsi:type", "dcsset:SelectedItemField");
        item.addChild(simpleTextNode("dcsset:field", fieldName));
        selection.addChild(item);
    }

    private void removeFromSelectionRecursive(XmlNode node, String fieldName) {
        Iterator<XmlNode> it = node.getChildren().iterator();
        while (it.hasNext()) {
            XmlNode c = it.next();
            String localName = c.getName();
            if (("selection".equals(localName) || "groupItems".equals(localName))
                    && c.getPrefix() != null
                    && (c.getPrefix().equals("dcsset") || c.getPrefix().equals(""))) {
                // remove matching items
                Iterator<XmlNode> it2 = c.getChildren().iterator();
                while (it2.hasNext()) {
                    XmlNode item = it2.next();
                    String f = childTextLocal(item, "field");
                    if (fieldName.equals(f)) it2.remove();
                }
            }
            removeFromSelectionRecursive(c, fieldName);
        }
    }

    /** Локально-нечувствительный поиск дочернего узла (игнорирует префикс). */
    private XmlNode findChildLocal(XmlNode parent, String localName) {
        for (XmlNode c : parent.getChildren()) {
            if (localName.equals(c.getName())) return c;
        }
        return null;
    }

    private String childTextLocal(XmlNode parent, String localName) {
        XmlNode c = findChildLocal(parent, localName);
        return c == null ? null : c.getText();
    }

    /** Замена &OldName → &NewName только при whole-token совпадении. */
    static String replaceTokenRef(String text, String oldRef, String newRef) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int p = text.indexOf(oldRef, i);
            if (p < 0) {
                sb.append(text, i, text.length());
                break;
            }
            sb.append(text, i, p);
            int next = p + oldRef.length();
            char nextCh = next < text.length() ? text.charAt(next) : '\0';
            if (Character.isLetterOrDigit(nextCh) || nextCh == '_') {
                // identifier continues — not a whole token
                sb.append(oldRef);
            } else {
                sb.append(newRef);
            }
            i = next;
        }
        return sb.toString();
    }
}

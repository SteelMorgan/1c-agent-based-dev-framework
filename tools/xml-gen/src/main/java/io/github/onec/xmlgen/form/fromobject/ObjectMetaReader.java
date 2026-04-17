package io.github.onec.xmlgen.form.fromobject;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Парсит XML-файл объекта метаданных 1С (Designer-формат) в {@link ObjectMeta}.
 * Делает guardrail-проверку: не пропускает FormDataStructure/Collection/Tree и компании.
 */
public class ObjectMetaReader {

    /** runtime-типы 1С, которых не бывает в XML — явный throw с подсказкой */
    public static final Map<String, String> KNOWN_INVALID_TYPES = new LinkedHashMap<>();
    static {
        KNOWN_INVALID_TYPES.put("FormDataStructure", "Runtime type. Use cfg:*Object.XXX (e.g. CatalogObject.XXX)");
        KNOWN_INVALID_TYPES.put("FormDataCollection", "Runtime type. Use ValueTable");
        KNOWN_INVALID_TYPES.put("FormDataTree", "Runtime type. Use ValueTree");
        KNOWN_INVALID_TYPES.put("FormDataTreeItem", "Runtime type, not valid in XML");
        KNOWN_INVALID_TYPES.put("FormDataCollectionItem", "Runtime type, not valid in XML");
        KNOWN_INVALID_TYPES.put("FormGroup", "UI element type, not a data type");
        KNOWN_INVALID_TYPES.put("FormField", "UI element type, not a data type");
        KNOWN_INVALID_TYPES.put("FormButton", "UI element type, not a data type");
        KNOWN_INVALID_TYPES.put("FormDecoration", "UI element type, not a data type");
        KNOWN_INVALID_TYPES.put("FormTable", "UI element type, not a data type");
    }

    public ObjectMeta read(Path objectXml) {
        XmlDocument doc;
        try {
            doc = new XmlStructureReader().parse(objectXml);
        } catch (Exception e) {
            throw new FromObjectException("Failed to parse object XML: " + objectXml, e);
        }
        XmlNode root = doc.getRoot();
        if (!"MetaDataObject".equals(root.getName())) {
            throw new FromObjectException("Expected <MetaDataObject> root, got <" + root.getName() + ">");
        }
        XmlNode typeNode = null;
        for (XmlNode child : root.getChildren()) {
            typeNode = child;
            break;
        }
        if (typeNode == null) {
            throw new FromObjectException("MetaDataObject has no type child");
        }

        ObjectMeta meta = new ObjectMeta();
        meta.type = typeNode.getName();

        XmlNode props = typeNode.child("Properties");
        XmlNode childObjs = typeNode.child("ChildObjects");

        meta.name = textOrEmpty(props, "Name");
        meta.synonym = synonym(props);
        if (meta.synonym == null || meta.synonym.isEmpty()) {
            meta.synonym = meta.name;
        }

        // Attributes — есть почти у всех
        fillFields(childObjs, "Attribute", meta.attributes, meta);

        // Tabular sections
        if (childObjs != null) {
            for (XmlNode ts : childObjs.children("TabularSection")) {
                XmlNode tsp = ts.child("Properties");
                String tsName = textOrEmpty(tsp, "Name");
                String tsSyn = synonym(tsp);
                ObjectMeta.TabularSection section = new ObjectMeta.TabularSection(tsName, tsSyn);
                XmlNode tsCo = ts.child("ChildObjects");
                if (tsCo != null) {
                    for (XmlNode col : tsCo.children("Attribute")) {
                        XmlNode cp = col.child("Properties");
                        String cn = textOrEmpty(cp, "Name");
                        String csyn = synonym(cp);
                        String ctype = typeOf(cp);
                        guardType(cn, ctype, meta.name);
                        section.columns.add(new ObjectMeta.Field(cn, csyn, ctype));
                    }
                }
                meta.tabularSections.add(section);
            }
        }

        // Type-specific
        switch (meta.type) {
            case "Document":
                meta.numberType = textOrDefault(props, "NumberType", "String");
                break;
            case "Catalog":
                meta.codeLength = intVal(props, "CodeLength");
                meta.descriptionLength = intVal(props, "DescriptionLength");
                meta.hierarchical = "true".equals(textOrEmpty(props, "Hierarchical"));
                meta.hierarchyType = textOrDefault(props, "HierarchyType", "HierarchyFoldersAndItems");
                fillOwners(props, meta);
                break;
            case "InformationRegister":
                fillFields(childObjs, "Dimension", meta.dimensions, meta);
                fillFields(childObjs, "Resource", meta.resources, meta);
                meta.periodicity = textOrDefault(props, "InformationRegisterPeriodicity", "Nonperiodical");
                meta.writeMode = textOrDefault(props, "WriteMode", "Independent");
                break;
            case "AccumulationRegister":
                fillFields(childObjs, "Dimension", meta.dimensions, meta);
                fillFields(childObjs, "Resource", meta.resources, meta);
                meta.registerType = textOrDefault(props, "RegisterType", "Balances");
                break;
            case "ChartOfCharacteristicTypes":
                meta.codeLength = intVal(props, "CodeLength");
                meta.descriptionLength = intVal(props, "DescriptionLength");
                meta.hierarchical = "true".equals(textOrEmpty(props, "Hierarchical"));
                meta.hierarchyType = textOrDefault(props, "HierarchyType", "HierarchyFoldersAndItems");
                fillOwners(props, meta);
                meta.hasValueType = true;
                break;
            case "ExchangePlan":
                meta.codeLength = intVal(props, "CodeLength");
                meta.descriptionLength = intVal(props, "DescriptionLength");
                meta.hierarchical = false;
                break;
            case "ChartOfAccounts":
                meta.codeLength = intVal(props, "CodeLength");
                meta.descriptionLength = intVal(props, "DescriptionLength");
                meta.hierarchical = true;
                meta.hierarchyType = textOrDefault(props, "HierarchyType", "HierarchyFoldersAndItems");
                meta.maxExtDimensionCount = intVal(props, "MaxExtDimensionCount");
                fillFields(childObjs, "AccountingFlag", meta.accountingFlags, meta);
                fillFields(childObjs, "ExtDimensionAccountingFlag", meta.extDimensionAccountingFlags, meta);
                break;
            default:
                // DataProcessor / Report / Catalog-like are handled as generic
                break;
        }
        return meta;
    }

    private void fillFields(XmlNode parent, String tagName, java.util.List<ObjectMeta.Field> sink, ObjectMeta meta) {
        if (parent == null) return;
        for (XmlNode n : parent.children(tagName)) {
            XmlNode p = n.child("Properties");
            String name = textOrEmpty(p, "Name");
            String syn = synonym(p);
            String type = typeOf(p);
            guardType(name, type, meta.name);
            sink.add(new ObjectMeta.Field(name, syn, type));
        }
    }

    private void fillOwners(XmlNode props, ObjectMeta meta) {
        if (props == null) return;
        XmlNode owners = props.child("Owners");
        if (owners == null) return;
        for (XmlNode it : owners.getChildren()) {
            if ("Item".equals(it.getName())) {
                String t = it.getText();
                if (t != null && !t.isEmpty()) meta.owners.add(t);
            }
        }
    }

    private void guardType(String fieldName, String type, String objectName) {
        if (type == null) return;
        for (Map.Entry<String, String> bad : KNOWN_INVALID_TYPES.entrySet()) {
            if (type.contains(bad.getKey())) {
                throw new FromObjectException("Invalid attribute type '" + type + "' in '" +
                        (objectName != null ? objectName + "." : "") + fieldName + "'. " + bad.getValue());
            }
        }
    }

    private String synonym(XmlNode props) {
        if (props == null) return "";
        XmlNode syn = props.child("Synonym");
        if (syn == null) return "";
        for (XmlNode item : syn.children("item")) {
            String lang = null;
            XmlNode langNode = item.child("lang");
            if (langNode != null) lang = langNode.getText();
            if (lang == null || "ru".equals(lang)) {
                XmlNode c = item.child("content");
                if (c != null && c.getText() != null) return c.getText();
            }
        }
        return "";
    }

    private String typeOf(XmlNode props) {
        if (props == null) return "string";
        XmlNode typeNode = props.child("Type");
        if (typeNode == null) return "string";
        StringBuilder sb = new StringBuilder();
        for (XmlNode t : typeNode.getChildren()) {
            if (!"Type".equals(t.getName())) continue;
            String txt = t.getText();
            if (txt == null || txt.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" | ");
            sb.append(txt);
        }
        return sb.length() > 0 ? sb.toString() : "string";
    }

    private int intVal(XmlNode props, String name) {
        if (props == null) return 0;
        String t = props.childText(name);
        if (t == null || t.isEmpty()) return 0;
        try { return Integer.parseInt(t.trim()); } catch (NumberFormatException ex) { return 0; }
    }

    private String textOrEmpty(XmlNode node, String name) {
        if (node == null) return "";
        String t = node.childText(name);
        return t != null ? t : "";
    }

    private String textOrDefault(XmlNode node, String name, String def) {
        String t = textOrEmpty(node, name);
        return t.isEmpty() ? def : t;
    }
}

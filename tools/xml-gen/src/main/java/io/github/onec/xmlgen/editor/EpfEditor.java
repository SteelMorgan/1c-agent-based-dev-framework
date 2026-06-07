package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.Map;
import java.util.UUID;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;
import static io.github.onec.xmlgen.editor.EditorUtils.findOrCreateChild;

public class EpfEditor {
    private final XmlDocument document;

    public EpfEditor(XmlDocument document) {
        this.document = document;
    }

    public void addAttribute(String name, String type, String synonym) {
        XmlNode attr = XmlNode.createElement("Attribute", Map.of("uuid", UUID.randomUUID().toString()));
        attr.addChild(buildAttributeProperties(name, synonym, type, false));
        insertChildObject(attr, "Attribute");
    }

    public void addTabularSection(String name, String synonym) {
        XmlNode section = XmlNode.createElement("TabularSection", Map.of("uuid", UUID.randomUUID().toString()));
        section.addChild(buildTabularSectionInternalInfo(name));
        section.addChild(buildTabularSectionProperties(name, synonym, formatAtLeast220()));
        section.addChild(createNode("ChildObjects"));
        insertChildObject(section, "TabularSection");
    }

    private XmlNode metadataObject() {
        XmlNode root = document.getRoot();
        if (root == null) {
            throw new IllegalStateException("EPF XML has no root element");
        }
        if ("ExternalDataProcessor".equals(root.getName()) || "ExternalReport".equals(root.getName())) {
            return root;
        }
        XmlNode dataProcessor = root.child("ExternalDataProcessor");
        if (dataProcessor != null) {
            return dataProcessor;
        }
        XmlNode report = root.child("ExternalReport");
        if (report != null) {
            return report;
        }
        throw new IllegalStateException(
                "Expected ExternalDataProcessor or ExternalReport in EPF root XML, got: " + root.getName());
    }

    private XmlNode childObjects() {
        return findOrCreateChild(metadataObject(), "ChildObjects");
    }

    private void insertChildObject(XmlNode newNode, String kind) {
        XmlNode childObjects = childObjects();
        int insertAt = childObjects.getChildren().size();
        for (int i = 0; i < childObjects.getChildren().size(); i++) {
            String existing = childObjects.getChildren().get(i).getName();
            if (order(existing) > order(kind)) {
                insertAt = i;
                break;
            }
        }
        childObjects.getChildren().add(insertAt, newNode);
    }

    private int order(String kind) {
        return switch (kind) {
            case "Attribute" -> 10;
            case "TabularSection" -> 20;
            case "Form" -> 30;
            case "Template" -> 40;
            default -> 90;
        };
    }

    private String objectName() {
        XmlNode props = metadataObject().child("Properties");
        String name = props != null ? props.childText("Name") : null;
        return name != null && !name.isBlank() ? name : "ExternalObject";
    }

    private String generatedPrefix() {
        return "ExternalReport".equals(metadataObject().getName()) ? "Report" : "DataProcessor";
    }

    private XmlNode buildAttributeProperties(String name, String synonym, String type, boolean tabularSectionAttribute) {
        XmlNode props = XmlNode.createElement("Properties", Map.of());

        props.addChild(textNode("Name", name));
        props.addChild(synonymNode(synonym != null ? synonym : name));
        props.addChild(createNode("Comment"));
        props.addChild(buildTypeNode(type != null ? type : "String"));
        props.addChild(textNode("PasswordMode", "false"));
        props.addChild(createNode("Format"));
        props.addChild(createNode("EditFormat"));
        props.addChild(createNode("ToolTip"));
        props.addChild(textNode("MarkNegatives", "false"));
        props.addChild(createNode("Mask"));
        props.addChild(textNode("MultiLine", "false"));
        props.addChild(textNode("ExtendedEdit", "false"));
        props.addChild(nilNode("MinValue"));
        props.addChild(nilNode("MaxValue"));
        if (tabularSectionAttribute) {
            props.addChild(textNode("FillFromFillingValue", "false"));
            props.addChild(nilNode("FillValue"));
        }
        props.addChild(textNode("FillChecking", "DontCheck"));
        props.addChild(textNode("ChoiceFoldersAndItems", "Items"));
        props.addChild(createNode("ChoiceParameterLinks"));
        props.addChild(createNode("ChoiceParameters"));
        props.addChild(textNode("QuickChoice", "Auto"));
        props.addChild(textNode("CreateOnInput", "Auto"));
        props.addChild(createNode("ChoiceForm"));
        props.addChild(createNode("LinkByType"));
        props.addChild(textNode("ChoiceHistoryOnInput", "Auto"));
        return props;
    }

    private XmlNode buildTabularSectionInternalInfo(String name) {
        XmlNode internalInfo = createNode("InternalInfo");
        String prefix = generatedPrefix();
        String objectName = objectName();
        internalInfo.addChild(generatedType(
                prefix + "TabularSection." + objectName + "." + name,
                "TabularSection"));
        internalInfo.addChild(generatedType(
                prefix + "TabularSectionRow." + objectName + "." + name,
                "TabularSectionRow"));
        return internalInfo;
    }

    private XmlNode generatedType(String name, String category) {
        XmlNode generatedType = createNode("xr:GeneratedType");
        generatedType.setAttribute("name", name);
        generatedType.setAttribute("category", category);
        generatedType.addChild(textNode("xr:TypeId", UUID.randomUUID().toString()));
        generatedType.addChild(textNode("xr:ValueId", UUID.randomUUID().toString()));
        return generatedType;
    }

    private XmlNode buildTabularSectionProperties(String name, String synonym, boolean formatAtLeast220) {
        XmlNode props = XmlNode.createElement("Properties", Map.of());
        props.addChild(textNode("Name", name));
        props.addChild(synonymNode(synonym != null ? synonym : name));
        props.addChild(createNode("Comment"));
        props.addChild(createNode("ToolTip"));
        props.addChild(textNode("FillChecking", "DontCheck"));
        if (formatAtLeast220) {
            props.addChild(textNode("LineNumberLength", "5"));
        }
        props.addChild(standardAttributes(formatAtLeast220));
        return props;
    }

    private XmlNode standardAttributes(boolean formatAtLeast220) {
        XmlNode standardAttributes = createNode("StandardAttributes");
        XmlNode lineNumber = createNode("xr:StandardAttribute");
        lineNumber.setAttribute("name", "LineNumber");
        lineNumber.addChild(createNode("xr:LinkByType"));
        lineNumber.addChild(textNode("xr:FillChecking", "DontCheck"));
        lineNumber.addChild(textNode("xr:MultiLine", "false"));
        lineNumber.addChild(textNode("xr:FillFromFillingValue", "false"));
        lineNumber.addChild(textNode("xr:CreateOnInput", "Auto"));
        if (formatAtLeast220) {
            lineNumber.addChild(textNode("xr:TypeReductionMode", "TransformValues"));
        }
        lineNumber.addChild(nilNode("xr:MaxValue"));
        lineNumber.addChild(createNode("xr:ToolTip"));
        lineNumber.addChild(textNode("xr:ExtendedEdit", "false"));
        lineNumber.addChild(createNode("xr:Format"));
        lineNumber.addChild(createNode("xr:ChoiceForm"));
        lineNumber.addChild(textNode("xr:QuickChoice", "Auto"));
        lineNumber.addChild(textNode("xr:ChoiceHistoryOnInput", "Auto"));
        lineNumber.addChild(createNode("xr:EditFormat"));
        lineNumber.addChild(textNode("xr:PasswordMode", "false"));
        lineNumber.addChild(textNode("xr:DataHistory", "Use"));
        lineNumber.addChild(textNode("xr:MarkNegatives", "false"));
        lineNumber.addChild(nilNode("xr:MinValue"));
        lineNumber.addChild(createNode("xr:Synonym"));
        lineNumber.addChild(createNode("xr:Comment"));
        lineNumber.addChild(textNode("xr:FullTextSearch", "Use"));
        standardAttributes.addChild(lineNumber);
        return standardAttributes;
    }

    private boolean formatAtLeast220() {
        String version = document.getRoot() != null ? document.getRoot().attr("version") : null;
        if (version == null || version.isBlank()) {
            return false;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+)\\.(\\d+)")
                .matcher(version.trim());
        if (!m.find()) {
            return false;
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        return major > 2 || (major == 2 && minor >= 20);
    }

    private XmlNode synonymNode(String synonym) {
        XmlNode synNode = XmlNode.createElement("Synonym", Map.of());
        XmlNode v8Item = createNode("v8:item");

        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        v8Item.addChild(v8Lang);

        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(synonym);
        v8Item.addChild(v8Content);

        synNode.addChild(v8Item);
        return synNode;
    }

    //++agent TASK-174 [05.06.2026 12:45:00]
    /**
     * XG-09: канонический <Type> для реквизита объекта (НЕ формы): v8:Type +
     * квалификаторы по типу. Зеркало MetaEditor.writeTypeValue в XmlNode-форме.
     * Квалификаторы — после всех v8:Type (канон составных типов).
     */
    private XmlNode buildTypeNode(String typeStr) {
        XmlNode typeNode = XmlNode.createElement("Type", Map.of());
        java.util.List<XmlNode> qualifiers = new java.util.ArrayList<>();
        for (String rawPart : io.github.onec.xmlgen.model.CompositeType.splitCompositeTypes(typeStr)) {
            String part = rawPart.trim();
            if (part.isEmpty()) continue;
            appendResolvedType(typeNode, qualifiers, part);
        }
        for (XmlNode q : qualifiers) {
            typeNode.addChild(q);
        }
        return typeNode;
    }

    private void appendResolvedType(XmlNode typeNode, java.util.List<XmlNode> qualifiers, String part) {
        java.util.regex.Matcher strM =
                java.util.regex.Pattern.compile("^(?:String|Строка)(?:\\((\\d+)\\))?$",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(part);
        java.util.regex.Matcher numM =
                java.util.regex.Pattern.compile("^(?:Number|Число)\\+?(?:\\((\\d+)(?:,(\\d+))?(?:,(nonneg))?\\))?$",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(part);

        if (strM.matches()) {
            typeNode.addChild(v8TypeText("xs:string"));
            String len = strM.group(1) != null ? strM.group(1) : "10";
            XmlNode q = createNode("v8:StringQualifiers");
            q.addChild(textNode("v8:Length", len));
            q.addChild(textNode("v8:AllowedLength", "Variable"));
            qualifiers.add(q);
        } else if (numM.matches()) {
            typeNode.addChild(v8TypeText("xs:decimal"));
            String digits = numM.group(1) != null ? numM.group(1) : "10";
            String fraction = numM.group(2) != null ? numM.group(2) : "0";
            String sign = (numM.group(3) != null || part.contains("+")) ? "Nonnegative" : "Any";
            XmlNode q = createNode("v8:NumberQualifiers");
            q.addChild(textNode("v8:Digits", digits));
            q.addChild(textNode("v8:FractionDigits", fraction));
            q.addChild(textNode("v8:AllowedSign", sign));
            qualifiers.add(q);
        } else if (part.equalsIgnoreCase("Boolean") || part.equalsIgnoreCase("Булево")) {
            typeNode.addChild(v8TypeText("xs:boolean"));
        } else if (part.matches("(?i)^(?:Date|Дата|DateTime|ДатаВремя|Time|Время)(?:\\(\\s*(?:Date|Time|DateTime|Дата|Время|ДатаВремя)\\s*\\))?$")) {
            //**agent TASK-174 [07.06.2026 12:10:00]
            // XG-13 (порт-аудит): скобочная форма Date(DateTime)/Дата(Время) раньше не
            // распознавалась и уходила в passthrough-литерал <v8:Type>Date(DateTime)</v8:Type>.
            // Теперь ЧастиДаты берутся из параметра (или из самого имени типа).
            typeNode.addChild(v8TypeText("xs:dateTime"));
            String fraction;
            java.util.regex.Matcher fr = java.util.regex.Pattern
                    .compile("\\(\\s*([^)\\s]+)\\s*\\)").matcher(part);
            String key = fr.find() ? fr.group(1) : part;
            if (key.equalsIgnoreCase("Date") || key.equalsIgnoreCase("Дата")) {
                fraction = "Date";
            } else if (key.equalsIgnoreCase("Time") || key.equalsIgnoreCase("Время")) {
                fraction = "Time";
            } else {
                fraction = "DateTime";
            }
            XmlNode q = createNode("v8:DateQualifiers");
            q.addChild(textNode("v8:DateFractions", fraction));
            qualifiers.add(q);
            //**agent TASK-174
        } else if (part.equalsIgnoreCase("ValueStorage") || part.equalsIgnoreCase("ХранилищеЗначения")) {
            typeNode.addChild(v8TypeText("xs:base64Binary"));
        } else if (part.startsWith("DefinedType.")) {
            XmlNode ts = createNode("v8:TypeSet");
            ts.setText("cfg:" + part);
            typeNode.addChild(ts);
        } else if (part.startsWith("xs:") || part.startsWith("v8:") || part.startsWith("cfg:")) {
            typeNode.addChild(v8TypeText(part));
        } else if (part.contains(".")) {
            // CatalogRef.X / DocumentRef.X / *Object.X → cfg:-префикс
            typeNode.addChild(v8TypeText("cfg:" + part));
        } else {
            // Неизвестный простой тип — passthrough (прежнее поведение)
            typeNode.addChild(v8TypeText(part));
        }
    }

    private XmlNode v8TypeText(String text) {
        return textNode("v8:Type", text);
    }

    private XmlNode textNode(String name, String text) {
        XmlNode n = createNode(name);
        n.setText(text);
        return n;
    }

    private XmlNode nilNode(String name) {
        XmlNode n = createNode(name);
        n.setAttribute("xsi:nil", "true");
        return n;
    }
    //++agent TASK-174

}

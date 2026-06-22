package io.github.onec.xmlgen.info;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draft decompiler for managed Form.xml.
 * <p>
 * The result is a scaffold for {@code form compile}, not a lossless projection.
 */
public class FormDecompiler {

    private static final Map<String, String> ELEMENT_TYPES = Map.ofEntries(
            Map.entry("UsualGroup", "group"),
            Map.entry("ColumnGroup", "columnGroup"),
            Map.entry("ButtonGroup", "buttonGroup"),
            Map.entry("InputField", "input"),
            Map.entry("CheckBoxField", "check"),
            Map.entry("RadioButtonField", "radio"),
            Map.entry("LabelDecoration", "label"),
            Map.entry("LabelField", "labelField"),
            Map.entry("PictureDecoration", "picture"),
            Map.entry("PictureField", "picField"),
            Map.entry("CalendarField", "calendar"),
            Map.entry("SpreadSheetDocumentField", "spreadsheet"),
            Map.entry("SpreadsheetDocumentField", "spreadsheet"),
            Map.entry("HTMLDocumentField", "html"),
            Map.entry("TextDocumentField", "textDoc"),
            Map.entry("FormattedDocumentField", "formattedDoc"),
            Map.entry("ProgressBarField", "progressBar"),
            Map.entry("TrackBarField", "trackBar"),
            Map.entry("PeriodField", "periodField"),
            Map.entry("GraphicalSchemaField", "graphicalSchema"),
            Map.entry("Table", "table"),
            Map.entry("Pages", "pages"),
            Map.entry("Page", "page"),
            Map.entry("Button", "button"),
            Map.entry("CommandBar", "cmdBar"),
            Map.entry("Popup", "popup")
    );

    private static final List<String> FORM_PROPERTIES = List.of(
            "WindowOpeningMode", "EnterKeyBehavior", "AutoTitle", "AutoURL",
            "AutoFillCheck", "Customizable", "CommandBarLocation",
            "SaveDataInSettings", "AutoSaveDataInSettings", "AutoTime",
            "UsePostingMode", "RepostOnWrite", "UseForFoldersAndItems",
            "ReportResult", "DetailsData", "ReportFormType", "VerticalScroll",
            "ScalingMode", "Width", "Height"
    );

    private static final List<String> SIMPLE_ELEMENT_PROPS = List.of(
            "Visible", "Enabled", "ReadOnly", "TitleLocation", "Representation",
            "ShowTitle", "United", "Width", "Height", "HorizontalStretch",
            "VerticalStretch", "EditMode", "MinValue", "MaxValue", "LargeStep",
            "MarkingStep", "Step", "CommandSource"
    );

    public void decompile(XmlDocument document, Path outputJson) throws IOException {
        Map<String, Object> root = decompile(document);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        if (outputJson == null) {
            mapper.writeValue(System.out, root);
            System.out.println();
            return;
        }
        if (outputJson.getParent() != null) {
            Files.createDirectories(outputJson.getParent());
        }
        try (OutputStream out = Files.newOutputStream(outputJson)) {
            mapper.writeValue(out, root);
        }
    }

    public Map<String, Object> decompile(XmlDocument document) {
        XmlNode root = document.getRoot();
        if (!"Form".equals(root.getName())) {
            throw new IllegalArgumentException("Expected root <Form>, got <" + root.getName() + ">");
        }
        if (root.child("ConditionalAppearance") != null) {
            throw new IllegalArgumentException("form decompile does not support form-level ConditionalAppearance");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        putIfNotBlank(out, "title", mlText(root.child("Title")));
        Map<String, Object> properties = decompileProperties(root);
        putIfNotEmpty(out, "properties", properties);
        putIfNotEmpty(out, "events", decompileEvents(root.child("Events")));
        putIfNotEmpty(out, "elements", decompileChildren(root.child("ChildItems"), false));
        putIfNotEmpty(out, "attributes", decompileAttributes(root.child("Attributes")));
        putIfNotEmpty(out, "parameters", decompileParameters(root.child("Parameters")));
        putIfNotEmpty(out, "commands", decompileCommands(root.child("Commands")));
        return out;
    }

    private Map<String, Object> decompileProperties(XmlNode root) {
        Map<String, Object> props = new LinkedHashMap<>();
        for (String xmlName : FORM_PROPERTIES) {
            XmlNode node = root.child(xmlName);
            if (node != null) {
                putIfNotBlank(props, lowerFirst(xmlName), scalarText(node));
            }
        }
        return props;
    }

    private Map<String, String> decompileEvents(XmlNode eventsNode) {
        if (eventsNode == null) return null;
        Map<String, String> events = new LinkedHashMap<>();
        for (XmlNode event : eventsNode.children("Event")) {
            if (event.attr("name") != null && event.getText() != null) {
                events.put(event.attr("name"), event.getText());
            }
        }
        return events;
    }

    private List<Map<String, Object>> decompileChildren(XmlNode childItems, boolean tableColumns) {
        if (childItems == null) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (XmlNode child : childItems.getChildren()) {
            if (isCompanion(child.getName())) {
                continue;
            }
            Map<String, Object> item = decompileElement(child, tableColumns);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> decompileElement(XmlNode node, boolean tableColumn) {
        String dslType = ELEMENT_TYPES.get(node.getName());
        if (dslType == null) {
            throw new IllegalArgumentException("form decompile does not support element <" + node.getName()
                    + "> at line " + node.getLine());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", dslType);
        putIfNotBlank(out, "name", node.attr("name"));
        putIfNotBlank(out, "path", node.childText("DataPath"));
        putIfNotBlank(out, "title", mlText(node.child("Title")));

        if ("button".equals(dslType)) {
            putIfNotBlank(out, "buttonType", node.childText("Type"));
            String commandName = node.childText("CommandName");
            if (commandName != null && commandName.startsWith("Form.Command.")) {
                out.put("command", commandName.substring("Form.Command.".length()));
            } else {
                putIfNotBlank(out, "commandName", commandName);
            }
        }
        if ("group".equals(dslType) || "columnGroup".equals(dslType) || "page".equals(dslType)) {
            putIfNotBlank(out, "group", lowerFirst(node.childText("Group")));
        }

        decompileSimpleProperties(node, out);
        putIfNotEmpty(out, "choiceList", decompileChoiceList(node.child("ChoiceList")));
        putIfNotEmpty(out, "choiceParameters", decompileChoiceParameters(node.child("ChoiceParameters")));
        putIfNotEmpty(out, "choiceParameterLinks", decompileChoiceParameterLinks(node.child("ChoiceParameterLinks")));
        putIfNotEmpty(out, "typeLink", decompileTypeLink(node.child("TypeLink")));
        putIfNotEmpty(out, "handlers", decompileEvents(node.child("Events")));

        List<Map<String, Object>> children = decompileChildren(node.child("ChildItems"), "table".equals(dslType));
        if (children != null && !children.isEmpty()) {
            out.put(tableColumn ? "children" : ("table".equals(dslType) ? "columns" : "children"), children);
        }
        return out;
    }

    private void decompileSimpleProperties(XmlNode node, Map<String, Object> out) {
        for (String xmlName : SIMPLE_ELEMENT_PROPS) {
            XmlNode child = node.child(xmlName);
            if (child == null) {
                continue;
            }
            String key = lowerFirst(xmlName);
            if ("TitleLocation".equals(xmlName) || "Representation".equals(xmlName) || "Group".equals(xmlName)) {
                putIfNotBlank(out, key, lowerFirst(scalarText(child)));
            } else {
                out.put(key, typedScalar(scalarText(child)));
            }
        }
    }

    private List<Map<String, Object>> decompileAttributes(XmlNode attributesNode) {
        if (attributesNode == null) return null;
        List<Map<String, Object>> attrs = new ArrayList<>();
        for (XmlNode attr : attributesNode.children("Attribute")) {
            if (attr.child("ConditionalAppearance") != null) {
                throw new IllegalArgumentException("form decompile does not support attribute ConditionalAppearance");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", attr.attr("name"));
            putIfNotBlank(out, "title", mlText(attr.child("Title")));
            putIfNotBlank(out, "type", decompileType(attr.child("Type")));
            if ("true".equals(attr.childText("MainAttribute"))) out.put("main", true);
            if ("true".equals(attr.childText("SavedData"))) out.put("savedData", true);
            putIfNotBlank(out, "fillChecking", attr.childText("FillChecking"));
            XmlNode useAlways = attr.child("UseAlways");
            if (useAlways != null) putIfNotBlank(out, "useAlwaysField", useAlways.childText("Field"));
            putIfNotEmpty(out, "columns", decompileColumns(attr.child("Columns")));
            attrs.add(out);
        }
        return attrs;
    }

    private List<Map<String, Object>> decompileColumns(XmlNode columnsNode) {
        if (columnsNode == null) return null;
        List<Map<String, Object>> columns = new ArrayList<>();
        for (XmlNode col : columnsNode.children("Column")) {
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", col.attr("name"));
            putIfNotBlank(out, "title", mlText(col.child("Title")));
            putIfNotBlank(out, "type", decompileType(col.child("Type")));
            columns.add(out);
        }
        return columns;
    }

    private List<Map<String, Object>> decompileParameters(XmlNode parametersNode) {
        if (parametersNode == null) return null;
        List<Map<String, Object>> params = new ArrayList<>();
        for (XmlNode param : parametersNode.children("Parameter")) {
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", param.attr("name"));
            putIfNotBlank(out, "title", mlText(param.child("Title")));
            putIfNotBlank(out, "type", decompileType(param.child("Type")));
            if ("true".equals(param.childText("KeyParameter"))) out.put("key", true);
            params.add(out);
        }
        return params;
    }

    private List<Map<String, Object>> decompileCommands(XmlNode commandsNode) {
        if (commandsNode == null) return null;
        List<Map<String, Object>> commands = new ArrayList<>();
        for (XmlNode cmd : commandsNode.children("Command")) {
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", cmd.attr("name"));
            putIfNotBlank(out, "title", mlText(cmd.child("Title")));
            putIfNotBlank(out, "tooltip", mlText(cmd.child("ToolTip")));
            putIfNotBlank(out, "action", cmd.childText("Action"));
            putIfNotBlank(out, "shortcut", cmd.childText("Shortcut"));
            putIfNotBlank(out, "representation", cmd.childText("Representation"));
            putIfNotBlank(out, "picture", pictureRef(cmd.child("Picture")));
            commands.add(out);
        }
        return commands;
    }

    private List<Map<String, Object>> decompileChoiceList(XmlNode choiceList) {
        if (choiceList == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (XmlNode item : choiceList.children("Item")) {
            XmlNode valueWrapper = item.child("Value");
            XmlNode valueNode = valueWrapper != null ? valueWrapper.child("Value") : null;
            Map<String, Object> out = new LinkedHashMap<>();
            if (valueNode != null) {
                out.put("value", typedScalar(valueNode.getText()));
                putIfNotBlank(out, "valueType", valueNode.attr("xsi:type"));
            }
            if (valueWrapper != null) {
                putIfNotBlank(out, "presentation", mlText(valueWrapper.child("Presentation")));
            }
            items.add(out);
        }
        return items;
    }

    private List<Map<String, Object>> decompileChoiceParameters(XmlNode choiceParameters) {
        if (choiceParameters == null) return null;
        List<Map<String, Object>> items = new ArrayList<>();
        for (XmlNode item : choiceParameters.children("item")) {
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", item.attr("name"));
            XmlNode valueNode = item.child("value");
            if (valueNode != null) {
                XmlNode value = valueNode.child("Value");
                if (value != null) {
                    out.put("value", typedScalar(value.getText()));
                }
            }
            items.add(out);
        }
        return items;
    }

    private List<Map<String, Object>> decompileChoiceParameterLinks(XmlNode linksNode) {
        if (linksNode == null) return null;
        List<Map<String, Object>> links = new ArrayList<>();
        for (XmlNode link : linksNode.children("Link")) {
            Map<String, Object> out = new LinkedHashMap<>();
            putIfNotBlank(out, "name", link.childText("Name"));
            putIfNotBlank(out, "dataPath", link.childText("DataPath"));
            putIfNotBlank(out, "valueChange", link.childText("ValueChange"));
            links.add(out);
        }
        return links;
    }

    private Map<String, Object> decompileTypeLink(XmlNode typeLink) {
        if (typeLink == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        putIfNotBlank(out, "dataPath", typeLink.childText("DataPath"));
        String linkItem = typeLink.childText("LinkItem");
        if (linkItem != null && !linkItem.isBlank()) {
            out.put("linkItem", typedScalar(linkItem));
        }
        return out;
    }

    private String decompileType(XmlNode typeNode) {
        if (typeNode == null) return null;
        List<String> types = new ArrayList<>();
        for (XmlNode child : typeNode.children("Type")) {
            String t = child.getText();
            if (t != null && !t.isBlank()) {
                types.add(stripConfigPrefix(t));
            }
        }
        return String.join(" | ", types);
    }

    private static boolean isCompanion(String name) {
        return "ExtendedTooltip".equals(name)
                || "ContextMenu".equals(name)
                || "AutoCommandBar".equals(name)
                || "SearchStringAddition".equals(name)
                || "ViewStatusAddition".equals(name)
                || "SearchControlAddition".equals(name);
    }

    private static String mlText(XmlNode node) {
        if (node == null) return null;
        XmlNode item = node.child("item");
        if (item != null && item.childText("content") != null) {
            return item.childText("content");
        }
        if (node.childText("content") != null) {
            return node.childText("content");
        }
        return node.getText();
    }

    private static String scalarText(XmlNode node) {
        String ml = mlText(node);
        return ml != null ? ml : node.getText();
    }

    private static Object typedScalar(String raw) {
        if (raw == null) return null;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }

    private static String pictureRef(XmlNode picture) {
        if (picture == null) return null;
        XmlNode ref = picture.child("Ref");
        return ref != null ? ref.getText() : picture.getText();
    }

    private static String stripConfigPrefix(String type) {
        if (type == null) return null;
        return type.replaceFirst("^(cfg|d\\d+p\\d+):", "");
    }

    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static void putIfNotEmpty(Map<String, Object> map, String key, Object value) {
        if (value instanceof Map<?, ?> m && m.isEmpty()) return;
        if (value instanceof List<?> l && l.isEmpty()) return;
        if (value != null) map.put(key, value);
    }

    private static String lowerFirst(String value) {
        if (value == null || value.isEmpty()) return value;
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}

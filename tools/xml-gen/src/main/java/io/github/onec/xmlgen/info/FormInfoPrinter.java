package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализ структуры управляемой формы 1С (Form.xml).
 * Выводит: заголовок, свойства, события, дерево элементов,
 * реквизиты, параметры, команды.
 */
public class FormInfoPrinter {

    private static final Set<String> SKIP_ELEMENTS = Set.of(
            "ExtendedTooltip", "ContextMenu", "AutoCommandBar",
            "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition",
            "ColumnGroup"
    );

    private static final List<String> FORM_PROPERTIES = List.of(
            "Width", "Height", "Group",
            "WindowOpeningMode", "EnterKeyBehavior", "AutoTitle", "AutoURL",
            "AutoFillCheck", "Customizable", "CommandBarLocation",
            "SaveDataInSettings", "AutoSaveDataInSettings",
            "AutoTime", "UsePostingMode", "RepostOnWrite",
            "UseForFoldersAndItems",
            "ReportResult", "DetailsData", "ReportFormType",
            "VerticalScroll", "ScalingMode"
    );

    private static final Pattern STD_CMD = Pattern.compile("^Form\\.StandardCommand\\.(.+)$");
    private static final Pattern FORM_CMD = Pattern.compile("^Form\\.Command\\.(.+)$");
    private static final Pattern CFG_PREFIX = Pattern.compile("^(?:cfg|d\\d+p\\d+):(.+)$");

    /**
     * Вывести информацию о форме.
     *
     * @param document распарсенный Form.xml
     * @param limit    максимум строк (0 = без ограничения)
     * @param offset   смещение строк
     * @param out      поток вывода
     */
    public void print(XmlDocument document, int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();
        List<String> lines = new ArrayList<>();

        // --- Header ---
        buildHeader(root, document.getFile(), lines);

        // --- Properties ---
        buildProperties(root, lines);

        // --- Events ---
        buildEvents(root, lines);

        // --- Element tree ---
        buildElementTree(root, lines);

        // --- Attributes ---
        buildAttributes(root, lines);

        // --- Parameters ---
        buildParameters(root, lines);

        // --- Commands ---
        buildCommands(root, lines);

        // --- BaseForm ---
        XmlNode baseForm = root.child("BaseForm");
        if (baseForm != null) {
            String version = baseForm.attr("version");
            String bfStr = version != null && !version.isEmpty()
                    ? "present (version " + version + ")" : "present";
            lines.add("");
            lines.add("BaseForm: " + bfStr);
        }

        // --- Output with pagination ---
        int totalLines = lines.size();
        if (offset > 0) {
            if (offset >= totalLines) {
                out.println("[INFO] Offset " + offset + " exceeds total lines (" + totalLines + "). Nothing to show.");
                return;
            }
            lines = lines.subList(offset, totalLines);
        }

        int effectiveLimit = limit > 0 ? limit : lines.size();
        if (lines.size() > effectiveLimit) {
            for (int i = 0; i < effectiveLimit; i++) {
                out.println(lines.get(i));
            }
            int remaining = totalLines - offset - effectiveLimit;
            out.println();
            out.println("[TRUNCATED] Shown " + effectiveLimit + " of " + totalLines
                    + " lines. Use --offset " + (offset + effectiveLimit) + " to continue.");
        } else {
            for (String line : lines) {
                out.println(line);
            }
        }
    }

    // --- Header ---

    private void buildHeader(XmlNode root, Path file, List<String> lines) {
        boolean isExtension = root.child("BaseForm") != null;
        String formName = "";
        String objectContext = "";

        if (file != null) {
            String resolved = file.toAbsolutePath().toString().replace("\\", "/");
            String[] parts = resolved.split("/");

            int formsIdx = -1;
            for (int i = parts.length - 1; i >= 0; i--) {
                if ("Forms".equals(parts[i])) {
                    formsIdx = i;
                    break;
                }
            }

            if (formsIdx >= 0 && formsIdx + 1 < parts.length) {
                formName = parts[formsIdx + 1];
                if (formsIdx >= 2) {
                    objectContext = parts[formsIdx - 2] + "." + parts[formsIdx - 1];
                }
            } else {
                int extIdx = -1;
                for (int i = parts.length - 1; i >= 0; i--) {
                    if ("Ext".equals(parts[i])) {
                        extIdx = i;
                        break;
                    }
                }
                if (extIdx >= 2) {
                    formName = parts[extIdx - 1];
                    objectContext = parts[extIdx - 2];
                } else {
                    formName = file.getFileName().toString().replaceFirst("\\.[^.]+$", "");
                }
            }
        }

        String title = getMlText(root.child("Title"));
        String extMarker = isExtension ? " [EXTENSION]" : "";

        StringBuilder header = new StringBuilder("=== Form: ").append(formName).append(extMarker);
        if (!title.isEmpty()) {
            header.append(" \u2014 \"").append(title).append("\"");
        }
        if (!objectContext.isEmpty()) {
            header.append(" (").append(objectContext).append(")");
        }
        header.append(" ===");
        lines.add(header.toString());
    }

    // --- Properties ---

    private void buildProperties(XmlNode root, List<String> lines) {
        List<String> props = new ArrayList<>();
        for (String pn : FORM_PROPERTIES) {
            XmlNode node = root.child(pn);
            if (node != null) {
                String val = getMlText(node);
                if (val.isEmpty()) {
                    val = node.getText() != null ? node.getText() : "";
                }
                props.add(pn + "=" + val);
            }
        }
        if (!props.isEmpty()) {
            lines.add("");
            lines.add("Properties: " + String.join(", ", props));
        }
    }

    // --- Events ---

    private void buildEvents(XmlNode root, List<String> lines) {
        XmlNode events = root.child("Events");
        if (events == null) return;

        List<XmlNode> eventList = events.children("Event");
        if (eventList.isEmpty()) return;

        lines.add("");
        lines.add("Events:");
        for (XmlNode e : eventList) {
            String name = e.attr("name");
            if (name == null) name = "";
            String handler = e.getText() != null ? e.getText() : "";
            String ct = e.attr("callType");
            String ctStr = ct != null && !ct.isEmpty() ? "[" + ct + "]" : "";
            lines.add("  " + name + ctStr + " -> " + handler);
        }
    }

    // --- Element tree ---

    private void buildElementTree(XmlNode root, List<String> lines) {
        XmlNode childItems = root.child("ChildItems");
        if (childItems == null) return;

        lines.add("");
        lines.add("Elements:");
        List<String> treeLines = new ArrayList<>();
        buildTree(childItems, "  ", treeLines);
        lines.addAll(treeLines);
    }

    private void buildTree(XmlNode childItems, String prefix, List<String> treeLines) {
        List<XmlNode> significant = new ArrayList<>();
        for (XmlNode child : childItems.getChildren()) {
            if (!SKIP_ELEMENTS.contains(child.getName())) {
                significant.add(child);
            }
        }

        for (int i = 0; i < significant.size(); i++) {
            XmlNode child = significant.get(i);
            boolean last = (i == significant.size() - 1);
            String connector = last ? "\u2514\u2500" : "\u251C\u2500";
            String continuation = last ? "  " : "\u2502 ";

            String tag = getElementTag(child);
            String name = child.attr("name");
            if (name == null) name = "";
            String flags = getFlags(child);
            String events = getEventsStr(child);
            String binding = getBinding(child);
            String titleStr = getTitleDiff(child, name);

            String line = prefix + connector + " " + tag + " " + name + binding + flags + titleStr + events;
            treeLines.add(line);

            // Recurse into containers
            String localName = child.getName();
            if ("Page".equals(localName)) {
                XmlNode ci = child.child("ChildItems");
                int cnt = countSignificantChildren(ci);
                treeLines.set(treeLines.size() - 1,
                        treeLines.get(treeLines.size() - 1) + " (" + cnt + " items)");
            } else if ("UsualGroup".equals(localName) || "Pages".equals(localName)
                    || "Table".equals(localName) || "CommandBar".equals(localName)
                    || "ButtonGroup".equals(localName) || "Popup".equals(localName)) {
                XmlNode ci = child.child("ChildItems");
                if (ci != null) {
                    buildTree(ci, prefix + continuation, treeLines);
                }
            }
        }
    }

    private String getElementTag(XmlNode node) {
        String name = node.getName();
        switch (name) {
            case "UsualGroup": {
                String g = node.childText("Group");
                String orient = "";
                if ("Vertical".equals(g)) orient = ":V";
                else if ("Horizontal".equals(g)) orient = ":H";
                else if ("AlwaysHorizontal".equals(g)) orient = ":AH";
                else if ("AlwaysVertical".equals(g)) orient = ":AV";
                String collapse = "";
                String beh = node.childText("Behavior");
                if ("Collapsible".equals(beh)) collapse = ",collapse";
                return "[Group" + orient + collapse + "]";
            }
            case "InputField": return "[Input]";
            case "CheckBoxField": return "[Check]";
            case "LabelDecoration": return "[Label]";
            case "LabelField": return "[LabelField]";
            case "PictureDecoration": return "[Picture]";
            case "PictureField": return "[PicField]";
            case "CalendarField": return "[Calendar]";
            case "Table": return "[Table]";
            case "Button": return "[Button]";
            case "CommandBar": return "[CmdBar]";
            case "Pages": return "[Pages]";
            case "Page": return "[Page]";
            case "Popup": return "[Popup]";
            case "ButtonGroup": return "[BtnGroup]";
            default: return "[" + name + "]";
        }
    }

    private String getFlags(XmlNode node) {
        List<String> flags = new ArrayList<>();
        if ("false".equals(node.childText("Visible"))) flags.add("visible:false");
        if ("false".equals(node.childText("Enabled"))) flags.add("enabled:false");
        if ("true".equals(node.childText("ReadOnly"))) flags.add("ro");
        return flags.isEmpty() ? "" : " [" + String.join(",", flags) + "]";
    }

    private String getEventsStr(XmlNode node) {
        XmlNode events = node.child("Events");
        if (events == null) return "";
        List<String> evts = new ArrayList<>();
        for (XmlNode e : events.children("Event")) {
            String eName = e.attr("name");
            if (eName == null) eName = "";
            String ct = e.attr("callType");
            if (ct != null && !ct.isEmpty()) {
                evts.add(eName + "[" + ct + "]");
            } else {
                evts.add(eName);
            }
        }
        return evts.isEmpty() ? "" : " {" + String.join(", ", evts) + "}";
    }

    private String getBinding(XmlNode node) {
        String dp = node.childText("DataPath");
        if (dp != null && !dp.isEmpty()) {
            return " -> " + dp;
        }
        String cn = node.childText("CommandName");
        if (cn != null && !cn.isEmpty()) {
            Matcher m = STD_CMD.matcher(cn);
            if (m.matches()) return " -> " + m.group(1) + " [std]";
            m = FORM_CMD.matcher(cn);
            if (m.matches()) return " -> " + m.group(1) + " [cmd]";
            return " -> " + cn;
        }
        return "";
    }

    private String getTitleDiff(XmlNode node, String name) {
        XmlNode titleNode = node.child("Title");
        if (titleNode == null) return "";
        String title = getMlText(titleNode);
        if (title.isEmpty()) return "";
        if (title.replace(" ", "").equalsIgnoreCase(name)) return "";
        return " [title:" + title + "]";
    }

    private int countSignificantChildren(XmlNode childItems) {
        if (childItems == null) return 0;
        int count = 0;
        for (XmlNode child : childItems.getChildren()) {
            if (!SKIP_ELEMENTS.contains(child.getName())) count++;
        }
        return count;
    }

    // --- Attributes ---

    private void buildAttributes(XmlNode root, List<String> lines) {
        XmlNode attrs = root.child("Attributes");
        if (attrs == null) return;

        List<String> attrLines = new ArrayList<>();
        for (XmlNode attr : attrs.children("Attribute")) {
            String aName = attr.attr("name");
            if (aName == null) aName = "";

            XmlNode typeNode = attr.child("Type");
            String typeStr = formatType(typeNode);

            boolean isMain = "true".equals(attr.childText("MainAttribute"));
            String prefixChar = isMain ? "*" : " ";
            String mainSuffix = isMain ? " (main)" : "";

            // DynamicList → MainTable
            String dynTable = "";
            XmlNode settings = attr.child("Settings");
            if (settings != null && "DynamicList".equals(typeStr)) {
                String mt = settings.childText("MainTable");
                if (mt != null && !mt.isEmpty()) {
                    dynTable = " -> " + mt;
                }
            }

            // ValueTable/ValueTree columns
            String colStr = "";
            XmlNode columns = attr.child("Columns");
            if (columns != null && ("ValueTable".equals(typeStr) || "ValueTree".equals(typeStr))) {
                List<String> cols = new ArrayList<>();
                for (XmlNode col : columns.children("Column")) {
                    String cName = col.attr("name");
                    if (cName == null) cName = "";
                    String cType = formatType(col.child("Type"));
                    cols.add(cType.isEmpty() ? cName : cName + ": " + cType);
                }
                if (!cols.isEmpty()) {
                    colStr = " [" + String.join(", ", cols) + "]";
                }
            }

            String line;
            if (!typeStr.isEmpty() || !colStr.isEmpty() || !dynTable.isEmpty()) {
                line = "  " + prefixChar + aName + ": " + typeStr + colStr + dynTable + mainSuffix;
            } else {
                line = "  " + prefixChar + aName + mainSuffix;
            }
            attrLines.add(line);
        }

        if (!attrLines.isEmpty()) {
            lines.add("");
            lines.add("Attributes:");
            lines.addAll(attrLines);
        }
    }

    // --- Parameters ---

    private void buildParameters(XmlNode root, List<String> lines) {
        XmlNode params = root.child("Parameters");
        if (params == null) return;

        List<String> paramLines = new ArrayList<>();
        for (XmlNode param : params.children("Parameter")) {
            String pName = param.attr("name");
            if (pName == null) pName = "";

            String typeStr = formatType(param.child("Type"));
            boolean isKey = "true".equals(param.childText("KeyParameter"));
            String keySuffix = isKey ? " (key)" : "";

            if (!typeStr.isEmpty()) {
                paramLines.add("  " + pName + ": " + typeStr + keySuffix);
            } else {
                paramLines.add("  " + pName + keySuffix);
            }
        }

        if (!paramLines.isEmpty()) {
            lines.add("");
            lines.add("Parameters:");
            lines.addAll(paramLines);
        }
    }

    // --- Commands ---

    private void buildCommands(XmlNode root, List<String> lines) {
        XmlNode cmds = root.child("Commands");
        if (cmds == null) return;

        List<String> cmdLines = new ArrayList<>();
        for (XmlNode cmd : cmds.children("Command")) {
            String cName = cmd.attr("name");
            if (cName == null) cName = "";

            String scStr = "";
            String shortcut = cmd.childText("Shortcut");
            if (shortcut != null && !shortcut.isEmpty()) {
                scStr = " [" + shortcut + "]";
            }

            List<XmlNode> actions = cmd.children("Action");
            String actionStr;
            if (actions.size() > 1) {
                List<String> parts = new ArrayList<>();
                for (XmlNode a : actions) {
                    String ct = a.attr("callType");
                    String ctS = ct != null && !ct.isEmpty() ? "[" + ct + "]" : "";
                    parts.add((a.getText() != null ? a.getText() : "") + ctS);
                }
                actionStr = " -> " + String.join(", ", parts);
            } else if (actions.size() == 1) {
                XmlNode a = actions.get(0);
                String ct = a.attr("callType");
                String ctS = ct != null && !ct.isEmpty() ? "[" + ct + "]" : "";
                actionStr = " -> " + (a.getText() != null ? a.getText() : "") + ctS;
            } else {
                actionStr = "";
            }

            cmdLines.add("  " + cName + actionStr + scStr);
        }

        if (!cmdLines.isEmpty()) {
            lines.add("");
            lines.add("Commands:");
            lines.addAll(cmdLines);
        }
    }

    // --- Type formatting ---

    static String formatType(XmlNode typeNode) {
        if (typeNode == null || typeNode.getChildren().isEmpty()) return "";

        // TypeSet
        XmlNode typeSet = typeNode.child("TypeSet");
        if (typeSet != null) {
            String val = typeSet.getText() != null ? typeSet.getText() : "";
            Matcher m = CFG_PREFIX.matcher(val);
            return m.matches() ? m.group(1) : val;
        }

        List<XmlNode> types = typeNode.children("Type");
        if (types.isEmpty()) return "";

        List<String> parts = new ArrayList<>();
        for (XmlNode t : types) {
            String raw = t.getText() != null ? t.getText() : "";
            switch (raw) {
                case "xs:string": {
                    XmlNode sq = findNested(typeNode, "StringQualifiers", "Length");
                    if (sq != null && sq.getText() != null && !sq.getText().isEmpty()) {
                        int len = Integer.parseInt(sq.getText());
                        parts.add(len > 0 ? "string(" + len + ")" : "string");
                    } else {
                        parts.add("string");
                    }
                    break;
                }
                case "xs:decimal": {
                    XmlNode nq = typeNode.child("NumberQualifiers");
                    if (nq != null) {
                        String d = nq.childText("Digits");
                        String f = nq.childText("FractionDigits");
                        parts.add("decimal(" + (d != null ? d : "0") + "," + (f != null ? f : "0") + ")");
                    } else {
                        parts.add("decimal");
                    }
                    break;
                }
                case "xs:boolean":
                    parts.add("boolean");
                    break;
                case "xs:dateTime": {
                    XmlNode dq = findNested(typeNode, "DateQualifiers", "DateFractions");
                    if (dq != null && dq.getText() != null) {
                        String frac = dq.getText();
                        if ("Date".equals(frac)) parts.add("date");
                        else if ("Time".equals(frac)) parts.add("time");
                        else parts.add("dateTime");
                    } else {
                        parts.add("dateTime");
                    }
                    break;
                }
                case "xs:binary":
                    parts.add("binary");
                    break;
                case "v8:ValueTable": parts.add("ValueTable"); break;
                case "v8:ValueTree": parts.add("ValueTree"); break;
                case "v8:ValueListType": parts.add("ValueList"); break;
                case "v8:TypeDescription": parts.add("TypeDescription"); break;
                case "v8:Universal": parts.add("Universal"); break;
                case "v8:FixedArray": parts.add("FixedArray"); break;
                case "v8:FixedStructure": parts.add("FixedStructure"); break;
                case "v8ui:FormattedString": parts.add("FormattedString"); break;
                case "v8ui:Picture": parts.add("Picture"); break;
                case "v8ui:Color": parts.add("Color"); break;
                case "v8ui:Font": parts.add("Font"); break;
                default: {
                    if (raw.startsWith("dcsset:")) {
                        parts.add(raw.replace("dcsset:", "DCS."));
                    } else if (raw.startsWith("dcssch:")) {
                        parts.add(raw.replace("dcssch:", "DCS."));
                    } else if (raw.startsWith("dcscor:")) {
                        parts.add(raw.replace("dcscor:", "DCS."));
                    } else {
                        Matcher m = CFG_PREFIX.matcher(raw);
                        if (m.matches()) {
                            parts.add(m.group(1));
                        } else {
                            parts.add(raw);
                        }
                    }
                }
            }
        }
        return String.join(" | ", parts);
    }

    // --- Helpers ---

    private static String getMlText(XmlNode node) {
        if (node == null) return "";
        // v8:item/v8:content pattern
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null && !content.getText().isEmpty()) {
                return content.getText();
            }
        }
        // Fallback: direct text
        String text = node.getText();
        return text != null ? text.trim() : "";
    }

    private static XmlNode findNested(XmlNode parent, String child1, String child2) {
        XmlNode c1 = parent.child(child1);
        return c1 != null ? c1.child(child2) : null;
    }
}

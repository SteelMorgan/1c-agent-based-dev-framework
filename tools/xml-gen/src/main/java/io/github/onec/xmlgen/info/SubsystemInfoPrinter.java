package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Анализ структуры подсистемы 1С.
 * Режимы: brief, overview, full, tree, ci.
 */
public class SubsystemInfoPrinter {

    /**
     * @param document распарсенный Subsystem XML
     * @param mode     режим: brief, overview, full, tree, ci
     * @param xmlFilePath путь к XML-файлу подсистемы (для tree/ci — нужен для навигации по файлам)
     * @param out      поток вывода
     */
    public void print(XmlDocument document, String mode, Path xmlFilePath, PrintStream out) {
        // subsystemDir = parent of the XML file (e.g. Subsystems/)
        Path subsystemDir = xmlFilePath != null ? xmlFilePath.getParent() : null;
        XmlNode root = document.getRoot();
        XmlNode subsystem = root;
        if ("MetaDataObject".equals(root.getName())) {
            subsystem = root.child("Subsystem");
        }
        if (subsystem == null) {
            out.println("ERROR: <Subsystem> element not found");
            return;
        }

        // Derive subsystem name directory: Subsystems/Name.xml → Subsystems/Name/
        String subsystemName = null;
        XmlNode props0 = subsystem.child("Properties");
        if (props0 != null) subsystemName = props0.childText("Name");
        Path subsystemNameDir = (subsystemDir != null && subsystemName != null)
                ? subsystemDir.resolve(subsystemName) : null;

        switch (mode) {
            case "brief":
                printBrief(subsystem, out);
                break;
            case "overview":
                printOverview(subsystem, out);
                break;
            case "full":
                printFull(subsystem, out);
                break;
            case "tree":
                printTree(subsystem, subsystemDir, out, 0);
                break;
            case "ci":
                printCommandInterface(subsystemNameDir, out);
                break;
            default:
                throw new IllegalArgumentException("Unknown subsystem info mode: '" + mode
                        + "'. Supported: brief, overview, full, tree, ci");
        }
    }

    // ==================== Brief ====================

    private void printBrief(XmlNode subsystem, PrintStream out) {
        XmlNode props = subsystem.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        boolean inCI = "true".equals(props.childText("IncludeInCommandInterface"));
        boolean oneCmd = "true".equals(props.childText("UseOneCommand"));
        int contentCount = countContent(props);
        int childCount = countChildren(subsystem);

        StringBuilder sb = new StringBuilder();
        sb.append("Подсистема: ").append(name);
        if (!synonym.isEmpty() && !synonym.equals(name)) sb.append(" — \"").append(synonym).append("\"");
        sb.append(" | ").append(contentCount).append(" объектов");
        sb.append(" | ").append(childCount).append(" дочерних");
        if (inCI) sb.append(" [CI]");
        if (oneCmd) sb.append(" [OneCmd]");
        out.println(sb);
    }

    // ==================== Overview ====================

    private void printOverview(XmlNode subsystem, PrintStream out) {
        XmlNode props = subsystem.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        String synStr = !synonym.isEmpty() ? " — \"" + synonym + "\"" : "";
        out.println("Подсистема: " + name + synStr);

        String comment = props.childText("Comment");
        if (comment != null && !comment.isEmpty()) out.println("  Comment: " + comment);

        out.println("  IncludeInCommandInterface: " + safe(props.childText("IncludeInCommandInterface")));
        out.println("  UseOneCommand: " + safe(props.childText("UseOneCommand")));

        String explanation = getMlText(props.child("Explanation"));
        if (!explanation.isEmpty()) out.println("  Explanation: " + explanation);

        // Picture
        XmlNode picture = props.child("Picture");
        if (picture != null) {
            XmlNode ref = picture.child("Ref");
            if (ref != null && ref.getText() != null) {
                out.println("  Picture: " + ref.getText());
            }
        }

        // Content summary
        List<String> contentItems = getContentItems(props);
        out.println();
        out.println("  Состав (" + contentItems.size() + "):");
        Map<String, List<String>> byType = groupByType(contentItems);
        for (Map.Entry<String, List<String>> e : byType.entrySet()) {
            out.println("    " + e.getKey() + " (" + e.getValue().size() + "): "
                    + String.join(", ", e.getValue()));
        }

        // Children
        List<String> children = getChildSubsystems(subsystem);
        if (!children.isEmpty()) {
            out.println();
            out.println("  Дочерние (" + children.size() + "): " + String.join(", ", children));
        }
    }

    // ==================== Full ====================

    private void printFull(XmlNode subsystem, PrintStream out) {
        XmlNode props = subsystem.child("Properties");
        if (props == null) { out.println("ERROR: <Properties> not found"); return; }

        String name = safe(props.childText("Name"));
        String uuid = subsystem.attr("uuid");
        out.println("=== Подсистема: " + name + " ===");
        if (uuid != null) out.println("  UUID: " + uuid);
        out.println();

        // All properties
        out.println("--- Свойства ---");
        String[] scalarProps = {"Name", "Comment", "IncludeHelpInContents",
                "IncludeInCommandInterface", "UseOneCommand"};
        for (String p : scalarProps) {
            String val = props.childText(p);
            if (val != null && !val.isEmpty()) {
                out.println("  " + pad(p, 30) + val);
            }
        }

        // LocalString props
        String[] lsProps = {"Synonym", "Explanation"};
        for (String p : lsProps) {
            String text = getMlText(props.child(p));
            if (!text.isEmpty()) {
                out.println("  " + pad(p, 30) + text);
            }
        }

        // Picture
        XmlNode picture = props.child("Picture");
        if (picture != null) {
            XmlNode ref = picture.child("Ref");
            if (ref != null && ref.getText() != null) {
                out.println("  " + pad("Picture", 30) + ref.getText());
            }
        }

        // Content — full list
        List<String> contentItems = getContentItems(props);
        out.println();
        out.println("--- Состав (" + contentItems.size() + ") ---");
        for (String item : contentItems) {
            out.println("  " + item);
        }

        // Children
        List<String> children = getChildSubsystems(subsystem);
        out.println();
        out.println("--- Дочерние (" + children.size() + ") ---");
        for (String child : children) {
            out.println("  " + child);
        }
    }

    // ==================== Tree ====================

    private void printTree(XmlNode subsystem, Path subsystemDir, PrintStream out, int depth) {
        XmlNode props = subsystem.child("Properties");
        if (props == null) return;

        String name = safe(props.childText("Name"));
        boolean inCI = "true".equals(props.childText("IncludeInCommandInterface"));
        boolean oneCmd = "true".equals(props.childText("UseOneCommand"));
        int contentCount = countContent(props);

        String indent = "  ".repeat(depth);
        StringBuilder line = new StringBuilder();
        line.append(indent).append(name);
        line.append(" (").append(contentCount).append(")");
        if (inCI) line.append(" [CI]");
        if (oneCmd) line.append(" [OneCmd]");

        // Check if CI exists
        if (subsystemDir != null) {
            Path ciPath = subsystemDir.resolve(name).resolve("Ext").resolve("CommandInterface.xml");
            if (Files.exists(ciPath)) line.append(" [CI-файл]");
        }

        out.println(line);

        // Recurse into children
        List<String> children = getChildSubsystems(subsystem);
        if (!children.isEmpty() && subsystemDir != null) {
            Path childSubsystemsDir = subsystemDir.resolve(name).resolve("Subsystems");
            for (String childName : children) {
                Path childXml = childSubsystemsDir.resolve(childName + ".xml");
                if (Files.exists(childXml)) {
                    try {
                        io.github.onec.xmlgen.validator.XmlStructureReader reader =
                                new io.github.onec.xmlgen.validator.XmlStructureReader();
                        XmlDocument childDoc = reader.parse(childXml);
                        XmlNode childRoot = childDoc.getRoot();
                        XmlNode childSub = "MetaDataObject".equals(childRoot.getName())
                                ? childRoot.child("Subsystem") : childRoot;
                        if (childSub != null) {
                            printTree(childSub, childSubsystemsDir, out, depth + 1);
                        }
                    } catch (Exception e) {
                        out.println(indent + "  " + childName + " [ERROR: " + e.getMessage() + "]");
                    }
                } else {
                    out.println(indent + "  " + childName + " [файл не найден]");
                }
            }
        }
    }

    // ==================== CI ====================

    private void printCommandInterface(Path subsystemNameDir, PrintStream out) {
        if (subsystemNameDir == null) {
            out.println("ERROR: subsystem directory not specified (needed for CI mode)");
            return;
        }

        // CI file is at <SubsystemName>/Ext/CommandInterface.xml
        Path ciPath = subsystemNameDir.resolve("Ext").resolve("CommandInterface.xml");
        if (!Files.exists(ciPath)) {
            out.println("CommandInterface.xml not found at: " + ciPath);
            return;
        }
        if (!Files.exists(ciPath)) {
            out.println("CommandInterface.xml not found at: " + ciPath);
            return;
        }

        try {
            io.github.onec.xmlgen.validator.XmlStructureReader reader =
                    new io.github.onec.xmlgen.validator.XmlStructureReader();
            XmlDocument ciDoc = reader.parse(ciPath);
            XmlNode ciRoot = ciDoc.getRoot();

            out.println("=== CommandInterface ===");
            out.println("  Path: " + ciPath);
            out.println();

            // CommandsVisibility
            XmlNode visibility = ciRoot.child("CommandsVisibility");
            if (visibility != null) {
                List<XmlNode> commands = visibility.children("Command");
                out.println("CommandsVisibility (" + commands.size() + "):");
                for (XmlNode cmd : commands) {
                    String cmdName = cmd.attr("name");
                    XmlNode vis = cmd.child("Visibility");
                    String common = vis != null ? getChildText(vis, "Common") : "?";
                    out.println("  " + pad(safe(cmdName), 60) + common);
                }
                out.println();
            }

            // CommandsPlacement
            XmlNode placement = ciRoot.child("CommandsPlacement");
            if (placement != null) {
                List<XmlNode> commands = placement.children("Command");
                out.println("CommandsPlacement (" + commands.size() + "):");
                for (XmlNode cmd : commands) {
                    String cmdName = cmd.attr("name");
                    String group = cmd.childText("CommandGroup");
                    out.println("  " + pad(safe(cmdName), 60) + safe(group));
                }
                out.println();
            }

            // CommandsOrder
            XmlNode order = ciRoot.child("CommandsOrder");
            if (order != null) {
                List<XmlNode> commands = order.children("Command");
                out.println("CommandsOrder (" + commands.size() + "):");
                for (XmlNode cmd : commands) {
                    String cmdName = cmd.attr("name");
                    String group = cmd.childText("CommandGroup");
                    out.println("  " + pad(safe(cmdName), 60) + safe(group));
                }
                out.println();
            }

            // SubsystemsOrder
            XmlNode subsystemsOrder = ciRoot.child("SubsystemsOrder");
            if (subsystemsOrder != null) {
                List<XmlNode> subs = subsystemsOrder.children("Subsystem");
                out.println("SubsystemsOrder (" + subs.size() + "):");
                for (XmlNode sub : subs) {
                    out.println("  " + safe(sub.getText()));
                }
                out.println();
            }

            // GroupsOrder
            XmlNode groupsOrder = ciRoot.child("GroupsOrder");
            if (groupsOrder != null) {
                List<XmlNode> groups = groupsOrder.children("Group");
                out.println("GroupsOrder (" + groups.size() + "):");
                for (XmlNode g : groups) {
                    out.println("  " + safe(g.getText()));
                }
            }

        } catch (Exception e) {
            out.println("ERROR parsing CommandInterface.xml: " + e.getMessage());
        }
    }

    // ==================== Helpers ====================

    private List<String> getContentItems(XmlNode props) {
        List<String> items = new ArrayList<>();
        XmlNode content = props.child("Content");
        if (content == null) return items;
        for (XmlNode item : content.children("Item")) {
            if (item.getText() != null) items.add(item.getText());
        }
        return items;
    }

    private int countContent(XmlNode props) {
        return getContentItems(props).size();
    }

    private List<String> getChildSubsystems(XmlNode subsystem) {
        List<String> children = new ArrayList<>();
        XmlNode co = subsystem.child("ChildObjects");
        if (co == null) return children;
        for (XmlNode child : co.children("Subsystem")) {
            if (child.getText() != null) children.add(child.getText());
        }
        return children;
    }

    private int countChildren(XmlNode subsystem) {
        return getChildSubsystems(subsystem).size();
    }

    private Map<String, List<String>> groupByType(List<String> items) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String item : items) {
            int dot = item.indexOf('.');
            String type = dot > 0 ? item.substring(0, dot) : "Unknown";
            String name = dot > 0 ? item.substring(dot + 1) : item;
            map.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
        }
        return map;
    }

    private String getChildText(XmlNode node, String childLocalName) {
        // Handles prefixed names like xr:Common
        for (XmlNode child : node.getChildren()) {
            if (child.getName().equals(childLocalName) || child.getName().endsWith(":" + childLocalName)) {
                return child.getText();
            }
        }
        return null;
    }

    static String getMlText(XmlNode node) {
        if (node == null) return "";
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null && !content.getText().isEmpty()) {
                return content.getText();
            }
        }
        String text = node.getText();
        return text != null ? text.trim() : "";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s + " ";
        return s + " ".repeat(width - s.length());
    }

    private static String safe(String s) { return s != null ? s : ""; }
}

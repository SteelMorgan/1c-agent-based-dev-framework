package io.github.onec.xmlgen.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Редактор CommandInterface.xml — видимость, размещение, порядок команд.
 * 6 операций: hide, show, place, order, subsystem-order, group-order.
 */
public class InterfaceEditor {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Canonical section order in CommandInterface. */
    private static final String[] SECTION_ORDER = {
            "CommandsVisibility", "CommandsPlacement", "CommandsOrder",
            "SubsystemsOrder", "GroupsOrder"
    };

    private final Path filePath;
    private String content;
    private boolean hasBom;

    public InterfaceEditor(Path filePath) throws IOException {
        this.filePath = filePath;
        byte[] raw = Files.readAllBytes(filePath);
        this.hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        this.content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Скрыть команду (CommandsVisibility, false).
     * Value: "Cmd.Name" or JSON array.
     */
    public void hide(String spec) {
        String[] items = parseItems(spec);
        for (String cmd : items) {
            setVisibility(cmd.trim(), false);
        }
    }

    /**
     * Показать команду (CommandsVisibility, true).
     */
    public void show(String spec) {
        String[] items = parseItems(spec);
        for (String cmd : items) {
            setVisibility(cmd.trim(), true);
        }
    }

    /**
     * Разместить команду в группе.
     * Value: JSON {"command":"...","group":"CommandGroup.X"}
     */
    public void place(String command, String group) {
        ensureSection("CommandsPlacement");

        String entry = "\t<Command name=\"" + escapeAttr(command) + "\">\n"
                + "\t\t<CommandGroup>" + escapeXml(group) + "</CommandGroup>\n"
                + "\t\t<Placement>Auto</Placement>\n"
                + "\t</Command>\n";

        // Check if command already placed
        if (content.contains("name=\"" + command + "\"")
                && content.indexOf("name=\"" + command + "\"") > content.indexOf("<CommandsPlacement>")) {
            // Replace existing placement
            Pattern p = Pattern.compile(
                    "<Command name=\"" + Pattern.quote(command) + "\">.*?</Command>\\s*",
                    Pattern.DOTALL);
            // Only replace within CommandsPlacement
            int start = content.indexOf("<CommandsPlacement>");
            int end = content.indexOf("</CommandsPlacement>");
            if (start >= 0 && end > start) {
                String section = content.substring(start, end);
                Matcher m = p.matcher(section);
                if (m.find()) {
                    String newSection = m.replaceFirst(Matcher.quoteReplacement(entry));
                    content = content.substring(0, start) + newSection + content.substring(end);
                    return;
                }
            }
        }

        // Append to CommandsPlacement
        content = content.replace("</CommandsPlacement>", entry + "</CommandsPlacement>");
    }

    /**
     * Задать порядок команд в группе.
     * commands: array of command names, group: group name
     */
    public void setOrder(String group, String[] commands) {
        ensureSection("CommandsOrder");

        // Remove ALL existing entries for this group in CommandsOrder
        Pattern groupEntries = Pattern.compile(
                "\\s*<Command name=\"[^\"]*\">\\s*"
                        + "<CommandGroup>" + Pattern.quote(group) + "</CommandGroup>\\s*"
                        + "</Command>\\s*", Pattern.DOTALL);
        content = groupEntries.matcher(content).replaceAll("\n");

        // Build new entries
        StringBuilder sb = new StringBuilder();
        for (String cmd : commands) {
            sb.append("\t<Command name=\"").append(escapeAttr(cmd)).append("\">\n");
            sb.append("\t\t<CommandGroup>").append(escapeXml(group)).append("</CommandGroup>\n");
            sb.append("\t</Command>\n");
        }

        // Append before </CommandsOrder>
        content = content.replace("</CommandsOrder>",
                sb + "</CommandsOrder>");
    }

    /**
     * Задать порядок подсистем.
     * subsystems: array of full paths, e.g. ["Subsystem.X.Subsystem.A", ...]
     */
    public void setSubsystemOrder(String[] subsystems) {
        // Replace or create SubsystemsOrder section
        StringBuilder sb = new StringBuilder();
        sb.append("<SubsystemsOrder>\n");
        for (String sub : subsystems) {
            sb.append("\t<Subsystem>").append(escapeXml(sub.trim())).append("</Subsystem>\n");
        }
        sb.append("</SubsystemsOrder>");

        if (content.contains("<SubsystemsOrder>")) {
            Pattern p = Pattern.compile("<SubsystemsOrder>.*?</SubsystemsOrder>", Pattern.DOTALL);
            content = p.matcher(content).replaceFirst(Matcher.quoteReplacement(sb.toString()));
        } else {
            ensureSectionWithContent("SubsystemsOrder", sb.toString());
        }
    }

    /**
     * Задать порядок групп.
     * groups: array of group identifiers
     */
    public void setGroupOrder(String[] groups) {
        StringBuilder sb = new StringBuilder();
        sb.append("<GroupsOrder>\n");
        for (String g : groups) {
            sb.append("\t<Group>").append(escapeXml(g.trim())).append("</Group>\n");
        }
        sb.append("</GroupsOrder>");

        if (content.contains("<GroupsOrder>")) {
            Pattern p = Pattern.compile("<GroupsOrder>.*?</GroupsOrder>", Pattern.DOTALL);
            content = p.matcher(content).replaceFirst(Matcher.quoteReplacement(sb.toString()));
        } else {
            ensureSectionWithContent("GroupsOrder", sb.toString());
        }
    }

    public void save() throws IOException {
        //++agent TASK-172 [02.06.2026 07:22:00]
        // Канон Designer (_Демо) — CRLF. Правка CommandInterface.xml строковыми заменами
        // вставляет \n-фрагменты; нормализуем итог к CRLF идемпотентно, BOM-решение сохраняем.
        byte[] contentBytes = io.github.onec.xmlgen.io.Crlf.normalize(content).getBytes(StandardCharsets.UTF_8);
        if (hasBom) {
            byte[] result = new byte[BOM.length + contentBytes.length];
            System.arraycopy(BOM, 0, result, 0, BOM.length);
            System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
            Files.write(filePath, result);
        } else {
            Files.write(filePath, contentBytes);
        }
        //++agent TASK-172
    }

    // --- Internal ---

    private void setVisibility(String command, boolean visible) {
        if (command.isEmpty()) return;

        ensureSection("CommandsVisibility");

        String entry = "\t<Command name=\"" + escapeAttr(command) + "\">\n"
                + "\t\t<Visibility>\n"
                + "\t\t\t<xr:Common>" + visible + "</xr:Common>\n"
                + "\t\t</Visibility>\n"
                + "\t</Command>\n";

        // Check if already exists in CommandsVisibility
        Pattern existing = Pattern.compile(
                "<Command name=\"" + Pattern.quote(command) + "\">\\s*<Visibility>.*?</Visibility>\\s*</Command>",
                Pattern.DOTALL);
        int visStart = content.indexOf("<CommandsVisibility>");
        int visEnd = content.indexOf("</CommandsVisibility>");
        if (visStart >= 0 && visEnd > visStart) {
            String section = content.substring(visStart, visEnd);
            Matcher m = existing.matcher(section);
            if (m.find()) {
                String newSection = m.replaceFirst(Matcher.quoteReplacement(entry.trim()));
                content = content.substring(0, visStart) + newSection + content.substring(visEnd);
                return;
            }
        }

        // Append
        content = content.replace("</CommandsVisibility>",
                entry + "</CommandsVisibility>");
    }

    private void ensureSection(String sectionName) {
        if (content.contains("<" + sectionName + ">") || content.contains("<" + sectionName + "/>")) {
            // Expand self-closing section tag
            content = content.replace("<" + sectionName + "/>",
                    "<" + sectionName + ">\n</" + sectionName + ">");
            return;
        }

        // Expand self-closing root if needed
        expandSelfClosingRoot();

        // Find insertion point based on canonical order
        String insertBefore = null;
        boolean foundSection = false;
        for (String s : SECTION_ORDER) {
            if (s.equals(sectionName)) {
                foundSection = true;
                continue;
            }
            if (foundSection && content.contains("<" + s)) {
                insertBefore = s;
                break;
            }
        }

        // TASK-171: ведущий отступ родителя — секции CommandInterface идут с одним табом,
        // как в типовом стиле Конфигуратора (эталон Николая сохраняет отступ окружения).
        // Без этого новый блок появлялся с колонки 0 и давал «грязный» diff.
        String indent = detectSectionIndent();
        String block = indent + "<" + sectionName + ">\n" + indent + "</" + sectionName + ">\n";
        if (insertBefore != null) {
            int idx = content.indexOf("<" + insertBefore);
            int lineStart = content.lastIndexOf('\n', idx);
            if (lineStart >= 0) {
                content = content.substring(0, lineStart + 1) + block + content.substring(lineStart + 1);
            }
        } else {
            // Insert before </CommandInterface>
            content = content.replace("</CommandInterface>", block + "</CommandInterface>");
        }
    }

    /**
     * TASK-171: определить ведущий отступ секций по существующим секциям файла.
     * Если ни одной секции ещё нет — используем один таб (типовой стиль Конфигуратора).
     */
    private String detectSectionIndent() {
        for (String s : SECTION_ORDER) {
            int idx = content.indexOf("<" + s);
            if (idx < 0) continue;
            int lineStart = content.lastIndexOf('\n', idx);
            String lead = content.substring(lineStart + 1, idx);
            if (lead.trim().isEmpty()) {
                return lead;
            }
        }
        return "\t";
    }

    private void ensureSectionWithContent(String sectionName, String fullContent) {
        // Remove self-closing section tag if exists
        content = content.replace("<" + sectionName + "/>", "");

        // Expand self-closing root if needed
        expandSelfClosingRoot();

        // Find insertion point
        String insertBefore = null;
        boolean foundSection = false;
        for (String s : SECTION_ORDER) {
            if (s.equals(sectionName)) {
                foundSection = true;
                continue;
            }
            if (foundSection && content.contains("<" + s)) {
                insertBefore = s;
                break;
            }
        }

        // TASK-171: ведущий отступ родителя для вставляемого блока секции (см. ensureSection).
        String indent = detectSectionIndent();
        String indented = indentBlock(fullContent, indent);
        if (insertBefore != null) {
            int idx = content.indexOf("<" + insertBefore);
            int lineStart = content.lastIndexOf('\n', idx);
            if (lineStart >= 0) {
                content = content.substring(0, lineStart + 1) + indented + "\n" + content.substring(lineStart + 1);
                return;
            }
        }

        // Insert before </CommandInterface>
        content = content.replace("</CommandInterface>", indented + "\n</CommandInterface>");
    }

    /** TASK-171: добавить ведущий отступ к каждой непустой строке блока. */
    private static String indentBlock(String block, String indent) {
        if (indent.isEmpty()) return block;
        String[] lines = block.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            if (!lines[i].isEmpty()) sb.append(indent);
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private void expandSelfClosingRoot() {
        if (content.contains("</CommandInterface>")) return; // already expanded
        // Match self-closing <CommandInterface ... />
        Pattern selfClosing = Pattern.compile("(<CommandInterface[^>]*)/>", Pattern.DOTALL);
        Matcher m = selfClosing.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(m.group(1) + ">\n</CommandInterface>");
        }
    }

    private String[] parseItems(String spec) {
        String trimmed = spec.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
            String[] parts = trimmed.split("\\s*,\\s*");
            for (int i = 0; i < parts.length; i++) {
                parts[i] = stripQuotes(parts[i]);
            }
            return parts;
        }
        return new String[]{trimmed};
    }

    private static String stripQuotes(String s) {
        String t = s.trim();
        if (t.startsWith("\"") && t.endsWith("\"")) return t.substring(1, t.length() - 1);
        return t;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

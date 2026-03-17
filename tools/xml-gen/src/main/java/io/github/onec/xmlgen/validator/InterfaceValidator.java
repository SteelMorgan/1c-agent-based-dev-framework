package io.github.onec.xmlgen.validator;

import java.util.*;

/**
 * Валидатор CommandInterface.xml.
 * <p>
 * 13 проверок:
 * 1. XML well-formedness, корневой элемент CommandInterface
 * 2. version attribute (2.17 или 2.20)
 * 3. Порядок секций: Visibility → Placement → Order → SubsystemsOrder → GroupsOrder
 * 4. CommandsVisibility: атрибут name на каждом Command
 * 5. CommandsVisibility: Visibility/Common = true|false
 * 6. CommandsVisibility: нет дубликатов команд
 * 7. CommandsPlacement: атрибут name + CommandGroup + Placement
 * 8. CommandsPlacement: нет дубликатов команд
 * 9. CommandsOrder: атрибут name + CommandGroup
 * 10. SubsystemsOrder: формат Subsystem.Name[.Subsystem.Name]
 * 11. SubsystemsOrder: нет дубликатов
 * 12. GroupsOrder: нет дубликатов
 * 13. Формат ссылок на команды: Type.Object.StandardCommand.Op | Type.Object.Command.Name | CommonCommand.Name
 */
public class InterfaceValidator {

    /** Standard panel groups. */
    private static final Set<String> STANDARD_GROUPS = Set.of(
            "NavigationPanelImportant", "NavigationPanelOrdinary", "NavigationPanelSeeAlso",
            "ActionsPanelCreate", "ActionsPanelTools", "ActionsPanelReports",
            "FormNavigationPanelGoTo", "FormNavigationPanelImportant",
            "FormCommandBarImportant", "FormCommandBarCreateBasedOn"
    );

    /** Canonical section order. */
    private static final List<String> SECTION_ORDER = List.of(
            "CommandsVisibility", "CommandsPlacement", "CommandsOrder",
            "SubsystemsOrder", "GroupsOrder"
    );

    private final List<ValidationMessage> messages = new ArrayList<>();

    public List<ValidationMessage> validate(XmlDocument document) {
        messages.clear();
        XmlNode root = document.getRoot();

        // Check 1: Root element
        if (!"CommandInterface".equals(root.getName())) {
            error("Structure: root element must be <CommandInterface>, got <" + root.getName() + ">");
            return messages;
        }

        // Check 2: Version
        String version = root.attr("version");
        if (version == null || version.isEmpty()) {
            warn("Structure: version attribute missing");
        } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
            warn("Structure: unexpected version '" + version + "' (expected 2.17 or 2.20)");
        }

        // Check 3: Section order
        List<String> presentSections = new ArrayList<>();
        for (XmlNode child : root.getChildren()) {
            String name = child.getName();
            if (SECTION_ORDER.contains(name)) {
                presentSections.add(name);
            }
        }
        for (int i = 0; i < presentSections.size() - 1; i++) {
            int idx1 = SECTION_ORDER.indexOf(presentSections.get(i));
            int idx2 = SECTION_ORDER.indexOf(presentSections.get(i + 1));
            if (idx1 > idx2) {
                warn("SectionOrder: '" + presentSections.get(i + 1)
                        + "' appears after '" + presentSections.get(i)
                        + "' (expected canonical order)");
            }
        }

        // Check 4-6: CommandsVisibility
        XmlNode visibility = root.child("CommandsVisibility");
        if (visibility != null) {
            Set<String> seenCmds = new HashSet<>();
            for (XmlNode cmd : visibility.children("Command")) {
                // Check 4: name attribute
                String cmdName = cmd.attr("name");
                if (cmdName == null || cmdName.isEmpty()) {
                    error("CommandsVisibility: Command missing 'name' attribute");
                    continue;
                }

                // Check 13: command reference format
                validateCommandRef(cmdName, "CommandsVisibility");

                // Check 5: Visibility/Common
                XmlNode vis = cmd.child("Visibility");
                if (vis != null) {
                    String common = getChildText(vis, "Common");
                    if (common != null && !"true".equals(common) && !"false".equals(common)) {
                        error("CommandsVisibility: Command '" + cmdName
                                + "' has invalid Common value '" + common + "'");
                    }
                } else {
                    warn("CommandsVisibility: Command '" + cmdName + "' missing <Visibility>");
                }

                // Check 6: duplicates
                if (!seenCmds.add(cmdName)) {
                    error("CommandsVisibility: duplicate command '" + cmdName + "'");
                }
            }
        }

        // Check 7-8: CommandsPlacement
        XmlNode placement = root.child("CommandsPlacement");
        if (placement != null) {
            Set<String> seenCmds = new HashSet<>();
            for (XmlNode cmd : placement.children("Command")) {
                String cmdName = cmd.attr("name");
                if (cmdName == null || cmdName.isEmpty()) {
                    error("CommandsPlacement: Command missing 'name' attribute");
                    continue;
                }

                validateCommandRef(cmdName, "CommandsPlacement");

                // Check 7: CommandGroup + Placement
                String group = cmd.childText("CommandGroup");
                if (group == null || group.isEmpty()) {
                    error("CommandsPlacement: Command '" + cmdName + "' missing <CommandGroup>");
                } else {
                    validateGroupRef(group, "CommandsPlacement", cmdName);
                }

                String pl = cmd.childText("Placement");
                if (pl != null && !pl.isEmpty() && !"Auto".equals(pl)) {
                    warn("CommandsPlacement: Command '" + cmdName
                            + "' has unusual Placement '" + pl + "' (expected Auto)");
                }

                // Check 8: duplicates
                if (!seenCmds.add(cmdName)) {
                    warn("CommandsPlacement: duplicate command '" + cmdName + "'");
                }
            }
        }

        // Check 9: CommandsOrder
        XmlNode order = root.child("CommandsOrder");
        if (order != null) {
            for (XmlNode cmd : order.children("Command")) {
                String cmdName = cmd.attr("name");
                if (cmdName == null || cmdName.isEmpty()) {
                    error("CommandsOrder: Command missing 'name' attribute");
                    continue;
                }

                validateCommandRef(cmdName, "CommandsOrder");

                String group = cmd.childText("CommandGroup");
                if (group == null || group.isEmpty()) {
                    error("CommandsOrder: Command '" + cmdName + "' missing <CommandGroup>");
                } else {
                    validateGroupRef(group, "CommandsOrder", cmdName);
                }
            }
        }

        // Check 10-11: SubsystemsOrder
        XmlNode subsystemsOrder = root.child("SubsystemsOrder");
        if (subsystemsOrder != null) {
            Set<String> seenSubs = new HashSet<>();
            for (XmlNode sub : subsystemsOrder.children("Subsystem")) {
                String subPath = sub.getText();
                if (subPath == null || subPath.isEmpty()) continue;

                // Check 10: format
                if (!subPath.startsWith("Subsystem.")) {
                    warn("SubsystemsOrder: '" + subPath
                            + "' does not start with 'Subsystem.' prefix");
                }

                // Check 11: duplicates
                if (!seenSubs.add(subPath)) {
                    error("SubsystemsOrder: duplicate entry '" + subPath + "'");
                }
            }
        }

        // Check 12: GroupsOrder
        XmlNode groupsOrder = root.child("GroupsOrder");
        if (groupsOrder != null) {
            Set<String> seenGroups = new HashSet<>();
            for (XmlNode g : groupsOrder.children("Group")) {
                String groupName = g.getText();
                if (groupName == null || groupName.isEmpty()) continue;

                if (!seenGroups.add(groupName)) {
                    error("GroupsOrder: duplicate group '" + groupName + "'");
                }
            }
        }

        return messages;
    }

    // --- Helpers ---

    /** Valid metadata type prefixes for command references. */
    private static final Set<String> COMMAND_REF_TYPES = Set.of(
            "Catalog", "Document", "Enum", "ChartOfAccounts",
            "ChartOfCharacteristicTypes", "ChartOfCalculationTypes",
            "ExchangePlan", "InformationRegister", "AccumulationRegister",
            "AccountingRegister", "CalculationRegister", "BusinessProcess",
            "Task", "Report", "DataProcessor", "Constant", "DocumentJournal",
            "CommonForm", "SettingsStorage");

    /**
     * Validate command reference format.
     * 4 valid patterns:
     * 1. "0:<uuid>" — UUID reference
     * 2. "CommonCommand.<Name>" — common command (2 segments)
     * 3. "Type.Object.StandardCommand.Op" — standard command (4 segments)
     * 4. "Type.Object.Command.Name" — object command (4 segments)
     */
    private void validateCommandRef(String ref, String section) {
        // Pattern 1: UUID reference
        if (ref.startsWith("0:")) {
            String uuid = ref.substring(2);
            if (!uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                warn(section + ": UUID reference '" + ref + "' has invalid UUID format");
            }
            return;
        }

        // Pattern 2: CommonCommand.Name
        if (ref.startsWith("CommonCommand.")) {
            if (ref.split("\\.").length != 2) {
                warn(section + ": CommonCommand reference '" + ref
                        + "' should have format CommonCommand.<Name>");
            }
            return;
        }

        // Patterns 3 & 4: Type.Object.StandardCommand.Op or Type.Object.Command.Name
        String[] parts = ref.split("\\.");
        if (parts.length == 4) {
            if (!COMMAND_REF_TYPES.contains(parts[0])) {
                warn(section + ": command reference '" + ref
                        + "' has unknown type prefix '" + parts[0] + "'");
            }
            if (!"StandardCommand".equals(parts[2]) && !"Command".equals(parts[2])) {
                warn(section + ": command reference '" + ref
                        + "' segment 3 should be 'StandardCommand' or 'Command', got '" + parts[2] + "'");
            }
        } else if (parts.length < 3) {
            warn(section + ": command reference '" + ref
                    + "' has unexpected format (expected Type.Object.Command.Name or CommonCommand.Name)");
        }
    }

    private void validateGroupRef(String group, String section, String cmdName) {
        if (STANDARD_GROUPS.contains(group)) return;
        if (group.startsWith("CommandGroup.")) return;
        warn(section + ": Command '" + cmdName + "' references unknown group '" + group + "'");
    }

    private String getChildText(XmlNode node, String childLocalName) {
        for (XmlNode child : node.getChildren()) {
            if (child.getName().equals(childLocalName)
                    || child.getName().endsWith(":" + childLocalName)) {
                return child.getText();
            }
        }
        return null;
    }

    private void error(String msg) { messages.add(new ValidationMessage("ERROR", msg)); }
    private void warn(String msg) { messages.add(new ValidationMessage("WARN", msg)); }

    public static class ValidationMessage {
        public final String level;
        public final String message;
        public ValidationMessage(String level, String message) {
            this.level = level;
            this.message = message;
        }
        @Override
        public String toString() { return "[" + level + "] " + message; }
    }
}

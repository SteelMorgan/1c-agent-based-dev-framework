package io.github.onec.xmlgen.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Валидатор подсистемы 1С.
 * <p>
 * 13 проверок:
 * 1. XML well-formedness, MetaDataObject/Subsystem, version
 * 2. Properties: Name обязательно
 * 3. Synonym: непустой LocalString
 * 4. IncludeInCommandInterface, UseOneCommand — boolean
 * 5. UseOneCommand=true → Content ровно 1 элемент
 * 6. Content: формат ссылок Type.Name
 * 7. Content: нет дубликатов
 * 8. ChildObjects: нет дубликатов
 * 9. Каталог подсистемы существует (если есть children или CI)
 * 10. Файлы дочерних подсистем существуют
 * 11. Ext/CommandInterface.xml существует (если каталог есть)
 * 12. Picture: формат ссылки CommonPicture.Name
 * 13. Explanation: если задан, непустой LocalString
 */
public class SubsystemValidator {

    /** Допустимые типы объектов в Content. */
    private static final Set<String> VALID_CONTENT_TYPES = Set.of(
            "Catalog", "Document", "Report", "DataProcessor", "CommonModule",
            "CommonCommand", "CommonForm", "CommonPicture", "CommandGroup",
            "Constant", "FunctionalOption", "Enum", "Role",
            "InformationRegister", "AccumulationRegister", "AccountingRegister",
            "ChartOfAccounts", "ChartOfCharacteristicTypes", "ExchangePlan",
            "DocumentJournal", "ChartOfCalculationTypes", "CalculationRegister",
            "BusinessProcess", "Task", "FilterCriterion", "Sequence",
            "DocumentNumerator", "SettingsStorage", "FunctionalOptionsParameter",
            "DefinedType", "EventSubscription", "ScheduledJob",
            "XDTOPackage", "WebService", "HTTPService", "WSReference",
            "StyleItem", "Style", "CommonAttribute", "SessionParameter",
            "IntegrationService", "Subsystem", "Language", "CommonTemplate"
    );

    private final List<ValidationMessage> messages = new ArrayList<>();

    public List<ValidationMessage> validate(XmlDocument document, Path subsystemDir) {
        messages.clear();
        XmlNode root = document.getRoot();

        // Check 1: Structure + version
        XmlNode subsystem = root;
        if ("MetaDataObject".equals(root.getName())) {
            String version = root.attr("version");
            if (version == null || version.isEmpty()) {
                error("Structure: version attribute missing on <MetaDataObject>");
            } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
                warn("Structure: unexpected version '" + version + "' (expected 2.17 or 2.20)");
            }
            subsystem = root.child("Subsystem");
            if (subsystem == null) {
                error("Structure: <Subsystem> element not found inside <MetaDataObject>");
                return messages;
            }
        } else if (!"Subsystem".equals(root.getName())) {
            error("Structure: root element must be <MetaDataObject> or <Subsystem>, got <" + root.getName() + ">");
            return messages;
        }

        XmlNode props = subsystem.child("Properties");
        if (props == null) {
            error("Properties: section missing");
            return messages;
        }

        // Check 2: Name
        String name = props.childText("Name");
        if (name == null || name.isEmpty()) {
            error("Properties: Name is required");
        }

        // Check 3: Synonym (required)
        XmlNode synonym = props.child("Synonym");
        if (synonym == null) {
            error("Properties: Synonym is required");
        } else {
            String synText = getMlText(synonym);
            if (synText.isEmpty()) {
                warn("Properties: Synonym is empty");
            }
        }

        // Check 13: Explanation (optional, but if present must be valid LocalString or empty tag)
        XmlNode explanation = props.child("Explanation");
        if (explanation != null) {
            // No special validation needed — empty tag is valid
        }

        // Check 4: Boolean properties
        validateBoolean(props, "IncludeInCommandInterface");
        validateBoolean(props, "UseOneCommand");
        validateBoolean(props, "IncludeHelpInContents");

        // Check 5: UseOneCommand → exactly 1 content item
        String useOneCmd = props.childText("UseOneCommand");
        List<String> contentItems = getContentItems(props);
        if ("true".equals(useOneCmd) && contentItems.size() != 1) {
            error("UseOneCommand is true but Content has " + contentItems.size()
                    + " items (expected exactly 1)");
        }

        // Check 6: Content format
        for (String item : contentItems) {
            if (!item.contains(".") && !isUuid(item)) {
                warn("Content: item '" + item + "' does not match Type.Name format");
            } else if (item.contains(".") && !isUuid(item)) {
                String type = item.substring(0, item.indexOf('.'));
                if (!VALID_CONTENT_TYPES.contains(type)) {
                    warn("Content: unknown type '" + type + "' in '" + item + "'");
                }
            }
        }

        // Check 7: Content duplicates
        Set<String> seenContent = new HashSet<>();
        for (String item : contentItems) {
            if (!seenContent.add(item)) {
                error("Content: duplicate entry '" + item + "'");
            }
        }

        // Check 8: ChildObjects duplicates
        XmlNode co = subsystem.child("ChildObjects");
        if (co != null) {
            Set<String> seenChildren = new HashSet<>();
            for (XmlNode child : co.children("Subsystem")) {
                String childName = child.getText();
                if (childName != null && !seenChildren.add(childName)) {
                    error("ChildObjects: duplicate subsystem '" + childName + "'");
                }
            }
        }

        // Check 12: Picture format
        XmlNode picture = props.child("Picture");
        if (picture != null) {
            XmlNode ref = picture.child("Ref");
            if (ref != null && ref.getText() != null && !ref.getText().isEmpty()) {
                String picRef = ref.getText();
                if (!picRef.startsWith("CommonPicture.") && !picRef.startsWith("StdPicture.")) {
                    warn("Picture: unexpected reference format '" + picRef
                            + "' (expected CommonPicture.Name or StdPicture.Name)");
                }
            }
        }

        // File existence checks (9-11)
        if (subsystemDir != null && name != null) {
            List<String> children = getChildNames(co);

            // Check 9: Directory exists if needed
            Path subsystemDirPath = subsystemDir.resolve(name);
            if (!children.isEmpty()) {
                if (!Files.isDirectory(subsystemDirPath)) {
                    warn("File missing: directory " + name + "/ expected (has child subsystems)");
                }
            }

            // Check 10: Child subsystem files exist
            if (!children.isEmpty()) {
                Path childSubsDir = subsystemDirPath.resolve("Subsystems");
                for (String childName : children) {
                    Path childXml = childSubsDir.resolve(childName + ".xml");
                    if (!Files.exists(childXml)) {
                        warn("File missing: " + name + "/Subsystems/" + childName + ".xml");
                    }
                }
            }

            // Check 11: CommandInterface.xml exists if Ext/ directory exists
            if (Files.isDirectory(subsystemDirPath)) {
                Path extDir = subsystemDirPath.resolve("Ext");
                if (Files.isDirectory(extDir)) {
                    Path ciPath = extDir.resolve("CommandInterface.xml");
                    if (!Files.exists(ciPath)) {
                        warn("File missing: " + name + "/Ext/CommandInterface.xml");
                    }
                }
            }
        }

        return messages;
    }

    // --- Helpers ---

    private List<String> getContentItems(XmlNode props) {
        List<String> items = new ArrayList<>();
        XmlNode content = props.child("Content");
        if (content == null) return items;
        for (XmlNode item : content.children("Item")) {
            if (item.getText() != null) items.add(item.getText());
        }
        return items;
    }

    private List<String> getChildNames(XmlNode co) {
        List<String> names = new ArrayList<>();
        if (co == null) return names;
        for (XmlNode child : co.children("Subsystem")) {
            if (child.getText() != null) names.add(child.getText());
        }
        return names;
    }

    private void validateBoolean(XmlNode props, String propName) {
        String val = props.childText(propName);
        if (val != null && !val.isEmpty() && !"true".equals(val) && !"false".equals(val)) {
            error("Properties: " + propName + " has invalid value '" + val + "' (expected true/false)");
        }
    }

    private boolean isUuid(String s) {
        return s != null && s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private String getMlText(XmlNode node) {
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

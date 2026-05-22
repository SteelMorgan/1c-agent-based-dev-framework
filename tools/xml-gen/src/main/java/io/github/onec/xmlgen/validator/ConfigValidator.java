package io.github.onec.xmlgen.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Валидатор Configuration.xml конфигурации 1С.
 * <p>
 * 10 проверок:
 * 1. XML well-formedness, MetaDataObject/Configuration, version
 * 1a. Namespace validation (http://v8.1c.ru/8.3/MDClasses)
 * 1b. UUID format on Configuration element
 * 2. InternalInfo: 7 ContainedObject, валидные ClassId
 * 3. Properties: Name, Synonym, DefaultLanguage, DefaultRunMode
 * 4. Enum-значения (11 свойств)
 * 5. ChildObjects: валидные типы, нет дубликатов, порядок
 * 6. DefaultLanguage ссылается на существующий Language
 * 7. Файлы языков Languages/<name>.xml существуют
 * 8. Каталоги объектов из ChildObjects существуют
 */
public class ConfigValidator {

    private static final Set<String> VALID_CHILD_TYPES = Set.of(
            "Language", "Subsystem", "StyleItem", "Style", "CommonPicture",
            "SessionParameter", "Role", "CommonTemplate", "FilterCriterion",
            "CommonModule", "CommonAttribute", "ExchangePlan", "XDTOPackage",
            "WebService", "HTTPService", "WSReference", "EventSubscription",
            "ScheduledJob", "SettingsStorage", "FunctionalOption",
            "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
            "CommandGroup", "Constant", "CommonForm", "Catalog", "Document",
            "DocumentNumerator", "Sequence", "DocumentJournal", "Enum",
            "Report", "DataProcessor", "InformationRegister",
            "AccumulationRegister", "ChartOfCharacteristicTypes",
            "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
            "CalculationRegister", "BusinessProcess", "Task",
            "IntegrationService"
    );

    /** Маппинг тип → каталог выгрузки (plural). */
    private static final Map<String, String> TYPE_TO_DIR = Map.ofEntries(
            Map.entry("Language", "Languages"),
            Map.entry("Subsystem", "Subsystems"),
            Map.entry("Catalog", "Catalogs"),
            Map.entry("Document", "Documents"),
            Map.entry("Enum", "Enums"),
            Map.entry("Report", "Reports"),
            Map.entry("DataProcessor", "DataProcessors"),
            Map.entry("InformationRegister", "InformationRegisters"),
            Map.entry("AccumulationRegister", "AccumulationRegisters"),
            Map.entry("Role", "Roles"),
            Map.entry("CommonModule", "CommonModules"),
            Map.entry("CommonForm", "CommonForms"),
            Map.entry("CommonTemplate", "CommonTemplates"),
            Map.entry("CommonCommand", "CommonCommands"),
            Map.entry("CommonPicture", "CommonPictures"),
            Map.entry("CommonAttribute", "CommonAttributes"),
            Map.entry("SessionParameter", "SessionParameters"),
            Map.entry("Constant", "Constants"),
            Map.entry("ExchangePlan", "ExchangePlans"),
            Map.entry("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes"),
            Map.entry("ChartOfAccounts", "ChartsOfAccounts"),
            Map.entry("AccountingRegister", "AccountingRegisters"),
            Map.entry("ChartOfCalculationTypes", "ChartsOfCalculationTypes"),
            Map.entry("CalculationRegister", "CalculationRegisters"),
            Map.entry("BusinessProcess", "BusinessProcesses"),
            Map.entry("Task", "Tasks"),
            Map.entry("EventSubscription", "EventSubscriptions"),
            Map.entry("ScheduledJob", "ScheduledJobs"),
            Map.entry("SettingsStorage", "SettingsStorages"),
            Map.entry("FunctionalOption", "FunctionalOptions"),
            Map.entry("FunctionalOptionsParameter", "FunctionalOptionsParameters"),
            Map.entry("DefinedType", "DefinedTypes"),
            Map.entry("DocumentNumerator", "DocumentNumerators"),
            Map.entry("Sequence", "Sequences"),
            Map.entry("DocumentJournal", "DocumentJournals"),
            Map.entry("StyleItem", "StyleItems"),
            Map.entry("Style", "Styles"),
            Map.entry("FilterCriterion", "FilterCriteria"),
            Map.entry("XDTOPackage", "XDTOPackages"),
            Map.entry("WebService", "WebServices"),
            Map.entry("HTTPService", "HTTPServices"),
            Map.entry("WSReference", "WSReferences"),
            Map.entry("CommandGroup", "CommandGroups"),
            Map.entry("IntegrationService", "IntegrationServices")
    );

    private static final Map<String, Set<String>> ENUM_VALUES = Map.ofEntries(
            Map.entry("CompatibilityMode", Set.of(
                    "DontUse", "Version8_1", "Version8_2_13", "Version8_2_16",
                    "Version8_3_1", "Version8_3_2", "Version8_3_3", "Version8_3_4",
                    "Version8_3_5", "Version8_3_6", "Version8_3_7", "Version8_3_8",
                    "Version8_3_9", "Version8_3_10", "Version8_3_11", "Version8_3_12",
                    "Version8_3_13", "Version8_3_14", "Version8_3_15", "Version8_3_16",
                    "Version8_3_17", "Version8_3_18", "Version8_3_19", "Version8_3_20",
                    "Version8_3_21", "Version8_3_22", "Version8_3_23", "Version8_3_24",
                    "Version8_3_25", "Version8_3_26", "Version8_3_27")),
            Map.entry("DefaultRunMode", Set.of("ManagedApplication", "OrdinaryApplication", "Auto")),
            Map.entry("ScriptVariant", Set.of("Russian", "English")),
            Map.entry("DataLockControlMode", Set.of("Managed", "Automatic", "AutomaticAndManaged")),
            Map.entry("ObjectAutonumerationMode", Set.of("NotAutoFree", "AutoFree")),
            Map.entry("ModalityUseMode", Set.of("DontUse", "Use", "UseWithWarnings")),
            Map.entry("SynchronousPlatformExtensionAndAddInCallUseMode", Set.of("DontUse", "Use", "UseWithWarnings")),
            Map.entry("InterfaceCompatibilityMode", Set.of("Taxi", "TaxiEnableVersion8_2")),
            Map.entry("DatabaseTablespacesUseMode", Set.of("DontUse", "Use")),
            Map.entry("MainClientApplicationWindowMode", Set.of("Normal", "Fullscreen", "Kiosk"))
    );

    // ConfigurationExtensionCompatibilityMode uses same values as CompatibilityMode

    private final List<ValidationMessage> messages = new ArrayList<>();

    public List<ValidationMessage> validate(XmlDocument document, Path configDir) {
        messages.clear();
        XmlNode root = document.getRoot();

        // Check 1: Structure + version
        XmlNode config = root;
        if ("MetaDataObject".equals(root.getName())) {
            // Check version attribute
            String version = root.attr("version");
            if (version == null || version.isEmpty()) {
                error("Structure: version attribute missing on <MetaDataObject>");
            } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
                warn("Structure: unexpected version '" + version + "' (expected 2.17 or 2.20)");
            }
            config = root.child("Configuration");
            if (config == null) {
                error("Structure: <Configuration> element not found inside <MetaDataObject>");
                return messages;
            }
        } else if (!"Configuration".equals(root.getName())) {
            error("Structure: root element must be <MetaDataObject> or <Configuration>, got <" + root.getName() + ">");
            return messages;
        }

        // Check 1a: Namespace (on MetaDataObject)
        if ("MetaDataObject".equals(root.getName())) {
            String xmlns = root.attr("xmlns");
            if (xmlns != null && !xmlns.isEmpty()
                    && !xmlns.contains("v8.1c.ru/8.3/MDClasses")
                    && !xmlns.contains("v8.1c.ru/8.2/MDClasses")) {
                warn("Namespace: unexpected xmlns '" + xmlns
                        + "' (expected http://v8.1c.ru/8.3/MDClasses)");
            }
        }

        // Check 1b: UUID on Configuration element
        String cfgUuid = config.attr("uuid");
        if (cfgUuid == null || cfgUuid.isEmpty()) {
            error("Structure: uuid attribute missing on <Configuration>");
        } else if (!isUuid(cfgUuid)) {
            error("Structure: invalid uuid format '" + cfgUuid + "'");
        }

        // Check 2: InternalInfo
        XmlNode internalInfo = config.child("InternalInfo");
        if (internalInfo == null) {
            warn("InternalInfo: section missing");
        } else {
            List<XmlNode> contained = internalInfo.children("ContainedObject");
            if (contained.size() < 7) {
                warn("InternalInfo: expected 7 ContainedObject entries, found " + contained.size());
            }
        }

        // Check 3: Properties
        XmlNode props = config.child("Properties");
        if (props == null) {
            error("Properties: section missing");
            return messages;
        }

        String name = props.childText("Name");
        if (name == null || name.isEmpty()) {
            error("Properties: Name is required");
        }

        String defaultLang = props.childText("DefaultLanguage");
        if (defaultLang == null || defaultLang.isEmpty()) {
            error("Properties: DefaultLanguage is required");
        }

        String runMode = props.childText("DefaultRunMode");
        if (runMode == null || runMode.isEmpty()) {
            warn("Properties: DefaultRunMode is not set");
        }

        // Check 4: Enum values
        for (Map.Entry<String, Set<String>> entry : ENUM_VALUES.entrySet()) {
            String propName = entry.getKey();
            String val = props.childText(propName);
            if (val != null && !val.isEmpty() && !entry.getValue().contains(val)) {
                error("Enum: " + propName + " has invalid value '" + val
                        + "'. Valid: " + entry.getValue());
            }
        }
        // ConfigurationExtensionCompatibilityMode shares CompatibilityMode values
        String extCompat = props.childText("ConfigurationExtensionCompatibilityMode");
        if (extCompat != null && !extCompat.isEmpty()) {
            Set<String> compatValues = ENUM_VALUES.get("CompatibilityMode");
            if (compatValues != null && !compatValues.contains(extCompat)) {
                error("Enum: ConfigurationExtensionCompatibilityMode has invalid value '" + extCompat + "'");
            }
        }

        // Check 5: ChildObjects
        XmlNode co = config.child("ChildObjects");
        if (co != null) {
            Set<String> seen = new HashSet<>();
            String lastType = null;
            String lastName = null;
            int lastTypeOrder = -1;

            for (XmlNode child : co.getChildren()) {
                String type = child.getName();
                String objName = child.getText();
                String fullName = type + "." + objName;

                // Valid type?
                if (!VALID_CHILD_TYPES.contains(type)) {
                    warn("ChildObjects: unknown type '" + type + "'");
                }

                // Duplicate?
                if (!seen.add(fullName)) {
                    error("ChildObjects: duplicate entry '" + fullName + "'");
                }

                // Order check
                int typeOrder = getTypeOrder(type);
                if (typeOrder >= 0) {
                    if (lastType != null && lastType.equals(type)) {
                        // Same type — check alphabetical order within type
                        if (objName != null && lastName != null && objName.compareTo(lastName) < 0) {
                            warn("ChildObjects: '" + type + "." + objName
                                    + "' is out of alphabetical order (after '" + lastName + "')");
                        }
                    } else if (typeOrder < lastTypeOrder) {
                        warn("ChildObjects: type '" + type + "' is out of canonical order (after '" + lastType + "')");
                    }
                    lastTypeOrder = typeOrder;
                }
                lastType = type;
                lastName = objName;
            }
        }

        // Check 6: DefaultLanguage references existing Language
        if (defaultLang != null && defaultLang.startsWith("Language.")) {
            String langName = defaultLang.substring("Language.".length());
            if (co != null) {
                boolean found = false;
                for (XmlNode child : co.getChildren()) {
                    if ("Language".equals(child.getName()) && langName.equals(child.getText())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    error("DefaultLanguage references '" + langName
                            + "' but no such Language in ChildObjects");
                }
            }
        }

        // Check 7-8: File existence (only if configDir provided)
        if (configDir != null && co != null) {
            for (XmlNode child : co.getChildren()) {
                String type = child.getName();
                String objName = child.getText();
                if (objName == null || objName.isEmpty()) continue;

                // Check 7: Language files
                if ("Language".equals(type)) {
                    Path langFile = configDir.resolve("Languages").resolve(objName + ".xml");
                    if (!Files.exists(langFile)) {
                        //++agent TASK-155 [22.05.2026 00:00:00]
                        // TASK-155 A2 iter-3: broken ChildObject reference → error, not warn.
                        // bug-T-154-config-002 obs #4: Designer refuses to load a Configuration.xml
                        // with missing object files — treating it as WARN (exit=0) is misleading.
                        error("File missing: Languages/" + objName + ".xml");
                        //++agent TASK-155
                    }
                }

                // Check 8: Object directories
                String dir = TYPE_TO_DIR.get(type);
                if (dir != null && !"Language".equals(type)) {
                    // Check for either directory or xml file
                    Path objDir = configDir.resolve(dir).resolve(objName);
                    Path objXml = configDir.resolve(dir).resolve(objName + ".xml");
                    if (!Files.exists(objDir) && !Files.exists(objXml)) {
                        //++agent TASK-155 [22.05.2026 00:00:00]
                        // TASK-155 A2 iter-3: broken ref → error (bug-T-154-config-002 obs #4).
                        error("File missing: " + dir + "/" + objName + " (neither dir nor .xml found)");
                        //++agent TASK-155
                    }
                }
            }
        }

        return messages;
    }

    private int getTypeOrder(String type) {
        // Use list from ConfigInfoPrinter
        List<String> order = List.of(
                "Language", "Subsystem", "StyleItem", "Style", "CommonPicture",
                "SessionParameter", "Role", "CommonTemplate", "FilterCriterion",
                "CommonModule", "CommonAttribute", "ExchangePlan", "XDTOPackage",
                "WebService", "HTTPService", "WSReference", "EventSubscription",
                "ScheduledJob", "SettingsStorage", "FunctionalOption",
                "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
                "CommandGroup", "Constant", "CommonForm", "Catalog", "Document",
                "DocumentNumerator", "Sequence", "DocumentJournal", "Enum",
                "Report", "DataProcessor", "InformationRegister",
                "AccumulationRegister", "ChartOfCharacteristicTypes",
                "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
                "CalculationRegister", "BusinessProcess", "Task",
                "IntegrationService"
        );
        return order.indexOf(type);
    }

    private boolean isUuid(String s) {
        return s != null && s.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
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

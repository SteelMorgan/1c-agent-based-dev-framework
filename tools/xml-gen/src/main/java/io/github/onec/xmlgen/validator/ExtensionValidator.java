package io.github.onec.xmlgen.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Валидатор расширения конфигурации 1С (CFE).
 *
 * 9 проверок:
 * 1. Root structure: MetaDataObject/Configuration, version 2.17/2.20
 * 2. InternalInfo: 7 ContainedObject, валидные ClassId
 * 3. Extension properties: ObjectBelonging=Adopted, Name, Purpose, NamePrefix
 * 4. Enum property values: CompatibilityMode, RunMode, ScriptVariant, InterfaceCompat
 * 5. ChildObjects: валидные типы (44), нет дубликатов, канонический порядок
 * 6. DefaultLanguage ссылается на существующий Language в ChildObjects
 * 7. Файлы языков Languages/<name>.xml существуют
 * 8. Каталоги объектов из ChildObjects существуют
 * 9. Borrowed objects: ObjectBelonging=Adopted + валидный ExtendedConfigurationObject UUID
 */
public class ExtensionValidator {

    private static final String NS_MD_CLASSES = "http://v8.1c.ru/8.3/MDClasses";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Pattern IDENT_PATTERN = Pattern.compile(
            "^[A-Za-z\\u0410-\\u042F\\u0401\\u0430-\\u044F\\u0451_]"
                    + "[A-Za-z0-9\\u0410-\\u042F\\u0401\\u0430-\\u044F\\u0451_]*$");

    /** 7 фиксированных ClassId для расширения */
    private static final Set<String> VALID_CLASS_IDS = Set.of(
            "9cd510cd-abfc-11d4-9434-004095e12fc7",
            "9fcd25a0-4822-11d4-9414-008048da11f9",
            "e3687481-0a87-462c-a166-9f34594f9bba",
            "9de14907-ec23-4a07-96f0-85521cb6b53b",
            "51f2d5d8-ea4d-4064-8892-82951750031e",
            "e68182ea-4237-4383-967f-90c1e3370bc7",
            "fb282519-d103-4dd3-bc12-cb271d631dfc"
    );

    /** 44 типа ChildObjects в каноническом порядке */
    private static final List<String> CHILD_ORDER = List.of(
            "Language", "Subsystem", "StyleItem", "Style",
            "CommonPicture", "SessionParameter", "Role", "CommonTemplate",
            "FilterCriterion", "CommonModule", "CommonAttribute", "ExchangePlan",
            "XDTOPackage", "WebService", "HTTPService", "WSReference",
            "EventSubscription", "ScheduledJob", "SettingsStorage", "FunctionalOption",
            "FunctionalOptionsParameter", "DefinedType", "CommonCommand", "CommandGroup",
            "Constant", "CommonForm", "Catalog", "Document",
            "DocumentNumerator", "Sequence", "DocumentJournal", "Enum",
            "Report", "DataProcessor", "InformationRegister", "AccumulationRegister",
            "ChartOfCharacteristicTypes", "ChartOfAccounts", "AccountingRegister",
            "ChartOfCalculationTypes", "CalculationRegister",
            "BusinessProcess", "Task", "IntegrationService"
    );

    private static final Set<String> VALID_CHILD_TYPES = new LinkedHashSet<>(CHILD_ORDER);

    /** Маппинг тип → каталог выгрузки */
    private static final Map<String, String> TYPE_TO_DIR = Map.ofEntries(
            Map.entry("Language", "Languages"), Map.entry("Subsystem", "Subsystems"),
            Map.entry("StyleItem", "StyleItems"), Map.entry("Style", "Styles"),
            Map.entry("CommonPicture", "CommonPictures"), Map.entry("SessionParameter", "SessionParameters"),
            Map.entry("Role", "Roles"), Map.entry("CommonTemplate", "CommonTemplates"),
            Map.entry("FilterCriterion", "FilterCriteria"), Map.entry("CommonModule", "CommonModules"),
            Map.entry("CommonAttribute", "CommonAttributes"), Map.entry("ExchangePlan", "ExchangePlans"),
            Map.entry("XDTOPackage", "XDTOPackages"), Map.entry("WebService", "WebServices"),
            Map.entry("HTTPService", "HTTPServices"), Map.entry("WSReference", "WSReferences"),
            Map.entry("EventSubscription", "EventSubscriptions"), Map.entry("ScheduledJob", "ScheduledJobs"),
            Map.entry("SettingsStorage", "SettingsStorages"), Map.entry("FunctionalOption", "FunctionalOptions"),
            Map.entry("FunctionalOptionsParameter", "FunctionalOptionsParameters"),
            Map.entry("DefinedType", "DefinedTypes"), Map.entry("CommonCommand", "CommonCommands"),
            Map.entry("CommandGroup", "CommandGroups"), Map.entry("Constant", "Constants"),
            Map.entry("CommonForm", "CommonForms"), Map.entry("Catalog", "Catalogs"),
            Map.entry("Document", "Documents"), Map.entry("DocumentNumerator", "DocumentNumerators"),
            Map.entry("Sequence", "Sequences"), Map.entry("DocumentJournal", "DocumentJournals"),
            Map.entry("Enum", "Enums"), Map.entry("Report", "Reports"),
            Map.entry("DataProcessor", "DataProcessors"), Map.entry("InformationRegister", "InformationRegisters"),
            Map.entry("AccumulationRegister", "AccumulationRegisters"),
            Map.entry("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes"),
            Map.entry("ChartOfAccounts", "ChartsOfAccounts"), Map.entry("AccountingRegister", "AccountingRegisters"),
            Map.entry("ChartOfCalculationTypes", "ChartsOfCalculationTypes"),
            Map.entry("CalculationRegister", "CalculationRegisters"),
            Map.entry("BusinessProcess", "BusinessProcesses"), Map.entry("Task", "Tasks"),
            Map.entry("IntegrationService", "IntegrationServices")
    );

    /** Extension-specific enum values */
    private static final Map<String, Set<String>> ENUM_VALUES = Map.of(
            "ConfigurationExtensionCompatibilityMode", Set.of(
                    "DontUse", "Version8_1", "Version8_2_13", "Version8_2_16",
                    "Version8_3_1", "Version8_3_2", "Version8_3_3", "Version8_3_4",
                    "Version8_3_5", "Version8_3_6", "Version8_3_7", "Version8_3_8",
                    "Version8_3_9", "Version8_3_10", "Version8_3_11", "Version8_3_12",
                    "Version8_3_13", "Version8_3_14", "Version8_3_15", "Version8_3_16",
                    "Version8_3_17", "Version8_3_18", "Version8_3_19", "Version8_3_20",
                    "Version8_3_21", "Version8_3_22", "Version8_3_23", "Version8_3_24",
                    "Version8_3_25", "Version8_3_26", "Version8_3_27", "Version8_3_28"),
            "DefaultRunMode", Set.of("ManagedApplication", "OrdinaryApplication", "Auto"),
            "ScriptVariant", Set.of("Russian", "English"),
            "InterfaceCompatibilityMode", Set.of("Taxi", "TaxiEnableVersion8_2", "Version8_2")
    );

    private final List<ValidationMessage> messages = new ArrayList<>();

    /**
     * Validate extension Configuration.xml.
     *
     * @param document parsed XML document
     * @param extDir   extension root directory (for file existence checks)
     * @return list of validation messages
     */
    public List<ValidationMessage> validate(XmlDocument document, Path extDir) {
        messages.clear();
        XmlNode root = document.getRoot();
        String rootFormatVersion = null;

        // ── Check 1: Root structure ─────────────────────────────────────
        XmlNode config = root;
        if ("MetaDataObject".equals(root.getName())) {
            if (!NS_MD_CLASSES.equals(root.getNamespace())) {
                error("1. MetaDataObject namespace must be '" + NS_MD_CLASSES
                        + "', got '" + (root.getNamespace() != null ? root.getNamespace() : "(none)") + "'");
            }
            String version = root.attr("version");
            if (version == null || version.isEmpty()) {
                warn("1. Missing version attribute on <MetaDataObject>");
            } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
                warn("1. Unusual version '" + version + "' (expected 2.17 or 2.20)");
            }
            rootFormatVersion = version;
            config = root.child("Configuration");
            if (config == null) {
                error("1. No <Configuration> element found inside MetaDataObject");
                return messages;
            }
        } else if (!"Configuration".equals(root.getName())) {
            error("1. Root element is '" + root.getName() + "', expected 'MetaDataObject'");
            return messages;
        }

        String cfgUuid = config.attr("uuid");
        if (cfgUuid == null || cfgUuid.isEmpty()) {
            error("1. Missing uuid on <Configuration>");
        } else if (!UUID_PATTERN.matcher(cfgUuid).matches()) {
            error("1. Invalid uuid '" + cfgUuid + "' on <Configuration>");
        }

        // ── Check 2: InternalInfo ───────────────────────────────────────
        XmlNode internalInfo = config.child("InternalInfo");
        if (internalInfo == null) {
            error("2. InternalInfo: missing");
        } else {
            List<XmlNode> contained = internalInfo.children("ContainedObject");
            if (contained.size() != 7) {
                warn("2. InternalInfo: expected 7 ContainedObject, found " + contained.size());
            }
            Set<String> foundClassIds = new HashSet<>();
            for (XmlNode co : contained) {
                String classId = co.childText("ClassId");
                String objectId = co.childText("ObjectId");
                if (classId == null || classId.isEmpty()) {
                    error("2. ContainedObject missing ClassId");
                    continue;
                }
                if (!VALID_CLASS_IDS.contains(classId)) {
                    error("2. Unknown ClassId: " + classId);
                }
                if (foundClassIds.contains(classId)) {
                    error("2. Duplicate ClassId: " + classId);
                }
                foundClassIds.add(classId);
                if (objectId == null || objectId.isEmpty()) {
                    error("2. ContainedObject missing ObjectId for ClassId " + classId);
                } else if (!UUID_PATTERN.matcher(objectId).matches()) {
                    error("2. Invalid ObjectId '" + objectId + "' for ClassId " + classId);
                }
            }
        }

        // ── Check 3: Extension-specific properties ──────────────────────
        XmlNode props = config.child("Properties");
        if (props == null) {
            error("3. Properties block missing");
            return messages;
        }

        String name = props.childText("Name");
        if (name == null || name.isEmpty()) {
            error("3. Name is missing or empty");
        } else if (!IDENT_PATTERN.matcher(name).matches()) {
            error("3. Name '" + name + "' is not a valid 1C identifier");
        }

        String objBelonging = props.childText("ObjectBelonging");
        if (!"Adopted".equals(objBelonging)) {
            error("3. ObjectBelonging must be 'Adopted', got '" + objBelonging + "'");
        }

        String purpose = props.childText("ConfigurationExtensionPurpose");
        if (purpose == null || purpose.isEmpty()) {
            error("3. ConfigurationExtensionPurpose is missing");
        } else if (!Set.of("Patch", "Customization", "AddOn").contains(purpose)) {
            error("3. ConfigurationExtensionPurpose '" + purpose + "' invalid (expected: Patch, Customization, AddOn)");
        }

        String prefix = props.childText("NamePrefix");
        if (prefix == null || prefix.isEmpty()) {
            warn("3. NamePrefix is empty");
        }

        String keepMapping = props.childText("KeepMappingToExtendedConfigurationObjectsByIDs");
        if (keepMapping == null) {
            warn("3. KeepMappingToExtendedConfigurationObjectsByIDs is missing");
        }

        String defaultLang = props.childText("DefaultLanguage");

        // ── Check 4: Enum property values ───────────────────────────────
        int enumChecked = 0;
        for (var entry : ENUM_VALUES.entrySet()) {
            String propValue = props.childText(entry.getKey());
            if (propValue != null && !propValue.isEmpty()) {
                if (!entry.getValue().contains(propValue)) {
                    error("4. Property '" + entry.getKey() + "' has invalid value '" + propValue + "'");
                }
                enumChecked++;
            }
        }

        // ── Check 5: ChildObjects ───────────────────────────────────────
        XmlNode childObjects = config.child("ChildObjects");
        List<XmlNode> childEntries = Collections.emptyList();
        if (childObjects == null) {
            error("5. ChildObjects block missing");
        } else {
            childEntries = childObjects.getChildren();
            Map<String, Set<String>> typeCounts = new LinkedHashMap<>();
            int lastTypeOrder = -1;
            boolean orderOk = true;

            for (XmlNode child : childEntries) {
                String typeName = child.getName();
                String objName = child.getText() != null ? child.getText() : "";

                int typeIdx = CHILD_ORDER.indexOf(typeName);
                if (typeIdx < 0) {
                    error("5. Unknown type '" + typeName + "' in ChildObjects");
                } else {
                    if (!typeCounts.containsKey(typeName)) {
                        if (typeIdx < lastTypeOrder) {
                            warn("5. Type '" + typeName + "' is out of canonical order");
                            orderOk = false;
                        }
                        lastTypeOrder = typeIdx;
                    }
                }

                typeCounts.computeIfAbsent(typeName, k -> new LinkedHashSet<>());
                if (typeCounts.get(typeName).contains(objName)) {
                    error("5. Duplicate: " + typeName + "." + objName);
                } else {
                    typeCounts.get(typeName).add(objName);
                }
            }
        }

        // ── Check 6: DefaultLanguage reference ──────────────────────────
        if (defaultLang != null && !defaultLang.isEmpty() && childObjects != null) {
            String langName = defaultLang.startsWith("Language.") ? defaultLang.substring(9) : defaultLang;
            boolean found = false;
            for (XmlNode child : childEntries) {
                if ("Language".equals(child.getName()) && langName.equals(child.getText())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                error("6. DefaultLanguage '" + defaultLang + "' not found in ChildObjects");
            }
        } else if (defaultLang == null || defaultLang.isEmpty()) {
            warn("6. Cannot check DefaultLanguage (empty)");
        }

        // ── Check 7: Language files exist ───────────────────────────────
        if (extDir != null && childObjects != null) {
            for (XmlNode child : childEntries) {
                if ("Language".equals(child.getName())) {
                    String langFileName = (child.getText() != null ? child.getText() : "") + ".xml";
                    Path langFile = extDir.resolve("Languages").resolve(langFileName);
                    if (!Files.isRegularFile(langFile)) {
                        warn("7. Language file missing: Languages/" + langFileName);
                    }
                }
            }
        }

        // ── Check 8: Object directories exist ───────────────────────────
        if (extDir != null && childObjects != null) {
            Set<String> checkedDirs = new LinkedHashSet<>();
            for (XmlNode child : childEntries) {
                String typeName = child.getName();
                if ("Language".equals(typeName)) continue;
                String dirName = TYPE_TO_DIR.get(typeName);
                if (dirName != null && !checkedDirs.contains(dirName)) {
                    Path dirPath = extDir.resolve(dirName);
                    if (!Files.isDirectory(dirPath)) {
                        warn("8. Missing directory: " + dirName);
                    }
                    checkedDirs.add(dirName);
                }
            }
        }

        // ── Check 8b: Object files exist and use root format version ────
        if (extDir != null && childObjects != null) {
            for (XmlNode child : childEntries) {
                String typeName = child.getName();
                String objName = child.getText() != null ? child.getText() : "";
                String dirName = TYPE_TO_DIR.get(typeName);
                if (dirName == null) continue;

                Path objFile = extDir.resolve(dirName).resolve(objName + ".xml");
                if (!Files.isRegularFile(objFile)) {
                    error("8. Missing object file: " + dirName + "/" + objName + ".xml");
                    continue;
                }

                if (rootFormatVersion != null && !rootFormatVersion.isEmpty()) {
                    try {
                        XmlDocument objDoc = new XmlStructureReader().parse(objFile);
                        XmlNode objRoot = objDoc.getRoot();
                        if ("MetaDataObject".equals(objRoot.getName())) {
                            String objVersion = objRoot.attr("version");
                            if (objVersion == null || objVersion.isEmpty()) {
                                warn("8. Missing version on " + dirName + "/" + objName + ".xml");
                            } else if (!rootFormatVersion.equals(objVersion)) {
                                error("8. Version mismatch in " + dirName + "/" + objName + ".xml"
                                        + ": " + objVersion + " (expected " + rootFormatVersion + ")");
                            }
                        }
                    } catch (Exception e) {
                        warn("8. Cannot parse " + dirName + "/" + objName + ".xml: " + e.getMessage());
                    }
                }
            }
        }

        // ── Check 9: Borrowed objects validation ────────────────────────
        if (extDir != null && childObjects != null) {
            int borrowedCount = 0;
            int borrowedOk = 0;
            for (XmlNode child : childEntries) {
                String typeName = child.getName();
                String objName = child.getText() != null ? child.getText() : "";
                if ("Language".equals(typeName)) continue;

                String dirName = TYPE_TO_DIR.get(typeName);
                if (dirName == null) continue;

                Path objFile = extDir.resolve(dirName).resolve(objName + ".xml");
                if (!Files.isRegularFile(objFile)) continue;

                // Parse object XML to check ObjectBelonging
                try {
                    XmlDocument objDoc = new XmlStructureReader().parse(objFile);
                    XmlNode objRoot = objDoc.getRoot();
                    // Navigate: MetaDataObject → <TypeElement> → Properties
                    XmlNode typeEl = null;
                    if ("MetaDataObject".equals(objRoot.getName())) {
                        // First child that's not InternalInfo
                        for (XmlNode c : objRoot.getChildren()) {
                            if (!"MetaDataObject".equals(c.getName())) {
                                typeEl = c;
                                break;
                            }
                        }
                    }
                    if (typeEl == null) continue;

                    XmlNode objProps = typeEl.child("Properties");
                    if (objProps == null) continue;

                    String ob = objProps.childText("ObjectBelonging");
                    if ("Adopted".equals(ob)) {
                        borrowedCount++;
                        String extObj = objProps.childText("ExtendedConfigurationObject");
                        if (extObj == null || extObj.isEmpty()) {
                            error("9. Borrowed " + typeName + "." + objName + ": missing ExtendedConfigurationObject");
                        } else if (!UUID_PATTERN.matcher(extObj).matches()) {
                            error("9. Borrowed " + typeName + "." + objName
                                    + ": invalid ExtendedConfigurationObject UUID '" + extObj + "'");
                        } else {
                            borrowedOk++;
                        }
                    }
                } catch (Exception e) {
                    warn("9. Cannot parse " + dirName + "/" + objName + ".xml: " + e.getMessage());
                }
            }
        }

        return messages;
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

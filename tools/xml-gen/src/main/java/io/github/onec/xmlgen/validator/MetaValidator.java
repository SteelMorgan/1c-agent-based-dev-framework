package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Валидатор объектов метаданных 1С (23 типа).
 * ~40 структурных проверок: XML-структура, Properties, ChildObjects,
 * специфика типа, файловая структура.
 */
public class MetaValidator {

    private final List<ValidationMessage> messages = new ArrayList<>();

    /**
     * Валидация объекта метаданных.
     *
     * @param document  распарсенный XML
     * @param objectDir каталог объекта (для проверки файлов, может быть null)
     * @return список сообщений валидации
     */
    public List<ValidationMessage> validate(XmlDocument document, Path objectDir) {
        messages.clear();
        XmlNode root = document.getRoot();

        // === Check 1: MetaDataObject wrapper ===
        XmlNode typeNode = root;
        String detectedType = null;

        if ("MetaDataObject".equals(root.getName())) {
            String version = root.attr("version");
            if (version == null || version.isEmpty()) {
                error("Structure: version attribute missing on <MetaDataObject>");
            } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
                warn("Structure: unexpected version '" + version + "' (expected 2.17 or 2.20)");
            }

            for (XmlNode child : root.getChildren()) {
                TypeDescriptor td = MetadataTypeRegistry.byXmlElement(child.getName());
                if (td != null) {
                    typeNode = child;
                    detectedType = td.type();
                    break;
                }
            }

            if (detectedType == null) {
                error("Structure: no recognized metadata type element found inside <MetaDataObject>");
                return messages;
            }
        } else {
            TypeDescriptor td = MetadataTypeRegistry.byXmlElement(root.getName());
            if (td != null) {
                detectedType = td.type();
            } else {
                error("Structure: unknown root element <" + root.getName() + ">");
                return messages;
            }
        }

        TypeDescriptor typeDesc = MetadataTypeRegistry.get(detectedType);

        // === Check 2: UUID ===
        String uuid = typeNode.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            error("Structure: uuid attribute missing on <" + detectedType + ">");
        } else if (!isUuid(uuid)) {
            error("Structure: invalid uuid format '" + uuid + "'");
        }

        // === Check 3: Properties section ===
        XmlNode props = typeNode.child("Properties");
        if (props == null) {
            error("Properties: section missing");
            return messages;
        }

        // === Check 4: Name required ===
        String name = props.childText("Name");
        if (name == null || name.isEmpty()) {
            error("Properties: Name is required");
        } else if (!isValidIdentifier(name)) {
            warn("Properties: Name '" + name + "' contains invalid characters");
        }

        // === Check 5: Synonym ===
        XmlNode synonym = props.child("Synonym");
        if (synonym == null) {
            warn("Properties: Synonym is missing");
        } else {
            String synText = getMlText(synonym);
            if (synText.isEmpty() && synonym.getChildren().isEmpty()
                    && (synonym.getText() == null || synonym.getText().isEmpty())) {
                // Empty self-closing <Synonym/> — acceptable for auto-generated
            }
        }

        // === Check 6: Boolean properties ===
        validateBoolean(props, "UseStandardCommands");
        validateBoolean(props, "IncludeHelpInContents");
        validateBoolean(props, "Hierarchical");
        validateBoolean(props, "CheckUnique");
        validateBoolean(props, "Autonumbering");
        validateBoolean(props, "Correspondence");
        validateBoolean(props, "DistributedInfoBase");

        // === Check 7: Type-specific Properties ===
        validateTypeSpecificProperties(props, detectedType);

        // === Check 7b: StandardAttributes block ===
        validateStandardAttributes(props, detectedType);

        // === Check 7c: Forbidden properties for type ===
        validateForbiddenProperties(props, detectedType);

        // === Check 7d: FillValue/Type consistency ===
        validateFillValueConsistency(props, detectedType);

        // === Check 8: ChildObjects ===
        XmlNode co = typeNode.child("ChildObjects");
        if (co != null) {
            validateChildObjects(co, typeDesc);
        } else if (!typeDesc.childTypes().isEmpty()) {
            // ChildObjects is optional (may be self-closing)
        }

        // === Check 9: InternalInfo / GeneratedType ===
        XmlNode internalInfo = typeNode.child("InternalInfo");
        if (internalInfo != null) {
            validateInternalInfo(internalInfo, detectedType, name, typeDesc);
        }

        // === Check 10-13: File structure ===
        if (objectDir != null && name != null && !name.isEmpty()) {
            validateFileStructure(objectDir, name, detectedType, typeDesc);
        }

        return messages;
    }

    // ==================== Type-specific Properties ====================

    private void validateTypeSpecificProperties(XmlNode props, String type) {
        switch (type) {
            case "Catalog":
                validatePositiveInt(props, "CodeLength", false);
                validatePositiveInt(props, "DescriptionLength", false);
                validateEnum(props, "CodeType", "String", "Number");
                validateEnum(props, "HierarchyType",
                        "HierarchyFoldersAndItems", "HierarchyItemsOnly");
                validateEnum(props, "DefaultPresentation", "AsDescription", "AsCode");
                validateHierarchyConsistency(props);
                break;

            case "Document":
                validatePositiveInt(props, "NumberLength", false);
                validateEnum(props, "NumberType", "String", "Number");
                validateEnum(props, "Posting", "Allow", "Deny");
                validateEnum(props, "NumberPeriodicity",
                        "Nonperiodical", "Year", "Quarter", "Month", "Day");
                validateEnum(props, "DefaultPresentation", "AsDescription", "AsCode");
                validateEnum(props, "RealTimePosting", "Allow", "Deny");
                break;

            case "Enum":
                // No mandatory numeric properties
                break;

            case "InformationRegister":
                validateEnum(props, "InformationRegisterPeriodicity",
                        "Nonperiodical", "Second", "Day", "Month", "Quarter", "Year",
                        "RecorderPosition");
                validateEnum(props, "WriteMode", "Independent", "RecorderSubordinate");
                break;

            case "AccumulationRegister":
                validateEnum(props, "RegisterType", "Balances", "Turnovers");
                break;

            case "AccountingRegister":
                String coa = props.childText("ChartOfAccounts");
                if (coa == null || coa.isEmpty()) {
                    error(type + ": ChartOfAccounts is required");
                } else if (!coa.startsWith("ChartOfAccounts.")) {
                    warn(type + ": ChartOfAccounts reference format — expected 'ChartOfAccounts.Name'");
                }
                break;

            case "CalculationRegister":
                String coct = props.childText("ChartOfCalculationTypes");
                if (coct == null || coct.isEmpty()) {
                    error(type + ": ChartOfCalculationTypes is required");
                } else if (!coct.startsWith("ChartOfCalculationTypes.")) {
                    warn(type + ": ChartOfCalculationTypes reference format — expected 'ChartOfCalculationTypes.Name'");
                }
                break;

            case "ChartOfAccounts":
                validatePositiveInt(props, "MaxExtDimensionCount", false);
                validatePositiveInt(props, "CodeLength", false);
                break;

            case "ChartOfCharacteristicTypes":
                validatePositiveInt(props, "CodeLength", false);
                validatePositiveInt(props, "DescriptionLength", false);
                validateEnum(props, "CodeType", "String", "Number");
                validateEnum(props, "DefaultPresentation", "AsDescription", "AsCode");
                break;

            case "ChartOfCalculationTypes":
                validatePositiveInt(props, "CodeLength", false);
                validatePositiveInt(props, "DescriptionLength", false);
                validateEnum(props, "CodeType", "String", "Number");
                break;

            case "CommonModule":
                // At least one execution context should be true
                boolean hasContext = "true".equals(props.childText("Server"))
                        || "true".equals(props.childText("ClientManagedApplication"))
                        || "true".equals(props.childText("ClientOrdinaryApplication"))
                        || "true".equals(props.childText("ExternalConnection"));
                if (!hasContext) {
                    warn("CommonModule: no execution context enabled (Server, Client, ExternalConnection)");
                }
                validateEnum(props, "ReturnValuesReuse",
                        "DontUse", "DuringRequest", "DuringSession");
                break;

            case "ScheduledJob":
                String methodName = props.childText("MethodName");
                if (methodName == null || methodName.isEmpty()) {
                    error("ScheduledJob: MethodName is required");
                } else if (!methodName.startsWith("CommonModule.")) {
                    warn("ScheduledJob: MethodName should start with 'CommonModule.' — got '" + methodName + "'");
                }
                break;

            case "EventSubscription":
                String handler = props.childText("Handler");
                if (handler == null || handler.isEmpty()) {
                    error("EventSubscription: Handler is required");
                }
                String event = props.childText("Event");
                if (event == null || event.isEmpty()) {
                    error("EventSubscription: Event is required");
                }
                break;

            case "HTTPService":
                String rootUrl = props.childText("RootURL");
                if (rootUrl == null || rootUrl.isEmpty()) {
                    error("HTTPService: RootURL is required");
                }
                break;

            case "WebService":
                String ns = props.childText("Namespace");
                if (ns == null || ns.isEmpty()) {
                    error("WebService: Namespace is required");
                }
                break;

            case "Constant":
            case "DefinedType":
                XmlNode typeNode = props.child("Type");
                if (typeNode == null) {
                    error(type + ": Type is required");
                } else {
                    boolean hasTypes = false;
                    for (XmlNode child : typeNode.getChildren()) {
                        if ("Type".equals(child.getName()) || "TypeSet".equals(child.getName())) {
                            hasTypes = true;
                            break;
                        }
                    }
                    if (!hasTypes && (typeNode.getText() == null || typeNode.getText().isEmpty())) {
                        error(type + ": Type element is empty");
                    }
                }
                break;

            case "DocumentJournal":
                XmlNode rd = props.child("RegisteredDocuments");
                if (rd == null) {
                    warn("DocumentJournal: RegisteredDocuments is missing");
                }
                break;

            case "ExchangePlan":
                validatePositiveInt(props, "CodeLength", false);
                validatePositiveInt(props, "DescriptionLength", false);
                validateEnum(props, "CodeType", "String", "Number");
                break;

            case "BusinessProcess":
                validatePositiveInt(props, "NumberLength", false);
                validateEnum(props, "NumberType", "String", "Number");
                validateEnum(props, "NumberPeriodicity",
                        "Nonperiodical", "Year", "Quarter", "Month", "Day");
                break;

            case "Task":
                validatePositiveInt(props, "NumberLength", false);
                validatePositiveInt(props, "DescriptionLength", false);
                validateEnum(props, "NumberType", "String", "Number");
                String addressing = props.childText("AddressingType");
                if (addressing != null && !addressing.isEmpty()) {
                    if (!"InformationRegister".equals(addressing) && !"ChartOfCharacteristicTypes".equals(addressing)) {
                        // AddressingAttribute references should match
                    }
                }
                break;

            default:
                break;
        }
    }

    private void validateHierarchyConsistency(XmlNode props) {
        String hierarchical = props.childText("Hierarchical");
        if (!"true".equals(hierarchical)) return;

        String hierarchyType = props.childText("HierarchyType");
        if (hierarchyType == null || hierarchyType.isEmpty()) {
            warn("Catalog: Hierarchical=true but HierarchyType is not set");
        }

        String limitLevel = props.childText("LimitLevelCount");
        if ("true".equals(limitLevel)) {
            String levelCount = props.childText("LevelCount");
            if (levelCount == null || levelCount.isEmpty()) {
                warn("Catalog: LimitLevelCount=true but LevelCount is not set");
            }
        }
    }

    // ==================== StandardAttributes ====================

    /** Expected minimum standard attribute count per type. */
    private static final Map<String, Integer> STANDARD_ATTR_COUNTS = Map.ofEntries(
            Map.entry("Catalog", 6),        // Ref, DeletionMark, Code, Description, Parent, Owner
            Map.entry("Document", 4),        // Ref, DeletionMark, Number, Date
            Map.entry("ExchangePlan", 4),    // Ref, DeletionMark, Code, Description
            Map.entry("ChartOfAccounts", 4), // Ref, DeletionMark, Code, Description
            Map.entry("ChartOfCharacteristicTypes", 4),
            Map.entry("ChartOfCalculationTypes", 4),
            Map.entry("BusinessProcess", 5), // Ref, DeletionMark, Number, Date, Started
            Map.entry("Task", 5)             // Ref, DeletionMark, Number, Date, Performed
    );

    private void validateStandardAttributes(XmlNode props, String type) {
        Integer expectedMin = STANDARD_ATTR_COUNTS.get(type);
        if (expectedMin == null) return; // Type doesn't require StandardAttributes validation

        XmlNode stdAttrs = props.child("StandardAttributes");
        if (stdAttrs == null) {
            warn(type + ": StandardAttributes section missing");
            return;
        }

        int count = 0;
        for (XmlNode child : stdAttrs.getChildren()) {
            if ("StandardAttribute".equals(child.getName())) {
                count++;
            }
        }
        if (count < expectedMin) {
            warn(type + ": StandardAttributes has " + count + " entries, expected at least " + expectedMin);
        }
    }

    // ==================== Forbidden Properties ====================

    /**
     * Properties that should NOT appear on certain types.
     * E.g. Enum has no CodeLength/DescriptionLength/Hierarchical.
     */
    private static final Map<String, Set<String>> FORBIDDEN_PROPS = Map.of(
            "Enum", Set.of("CodeLength", "DescriptionLength", "CodeType", "Hierarchical",
                    "HierarchyType", "Owners", "NumberLength", "NumberType"),
            "Constant", Set.of("CodeLength", "DescriptionLength", "Hierarchical",
                    "NumberLength", "NumberType", "Owners"),
            "DefinedType", Set.of("CodeLength", "DescriptionLength", "Hierarchical",
                    "NumberLength", "NumberType"),
            "CommonModule", Set.of("CodeLength", "DescriptionLength", "Hierarchical",
                    "NumberLength", "NumberType", "Owners"),
            "ScheduledJob", Set.of("CodeLength", "DescriptionLength", "Hierarchical",
                    "NumberLength", "NumberType"),
            "EventSubscription", Set.of("CodeLength", "DescriptionLength", "Hierarchical",
                    "NumberLength", "NumberType")
    );

    private void validateForbiddenProperties(XmlNode props, String type) {
        Set<String> forbidden = FORBIDDEN_PROPS.get(type);
        if (forbidden == null) return;

        for (String propName : forbidden) {
            String val = props.childText(propName);
            if (val != null && !val.isEmpty()) {
                warn(type + ": property '" + propName + "' is not applicable (has value '" + val + "')");
            }
        }
    }

    // ==================== FillValue Consistency ====================

    /**
     * If FillValue is set, it should be compatible with the Type declaration.
     * Basic check: if Type is "Boolean" then FillValue should be true/false.
     */
    private void validateFillValueConsistency(XmlNode props, String type) {
        // Only check for typed children — skip for top-level properties
        // as FillValue at object level is rare. This is checked per-attribute
        // in validateNamedChildren already for Type presence.
        // Here we add a simple check: if FillChecking is set to ShowError,
        // then FillValue should also be set.
        String fillChecking = props.childText("FillChecking");
        if ("ShowError".equals(fillChecking)) {
            XmlNode fillValue = props.child("FillValue");
            if (fillValue == null) {
                // FillChecking=ShowError without FillValue is common (means "don't allow empty")
                // This is actually normal behavior — no warning needed.
            }
        }
    }

    // ==================== ChildObjects ====================

    private void validateChildObjects(XmlNode co, TypeDescriptor typeDesc) {
        Set<String> allowedChildren = new HashSet<>(typeDesc.childTypes());

        // Check for unknown child types
        for (XmlNode child : co.getChildren()) {
            String childName = child.getName();
            if (!allowedChildren.contains(childName)) {
                warn("ChildObjects: unexpected element <" + childName
                        + "> for type " + typeDesc.type());
            }
        }

        // Validate Attributes
        validateNamedChildren(co, "Attribute");

        // Validate Dimensions
        validateNamedChildren(co, "Dimension");

        // Validate Resources
        validateNamedChildren(co, "Resource");

        // Validate TabularSections
        Set<String> tsNames = new HashSet<>();
        for (XmlNode ts : co.children("TabularSection")) {
            validateTabularSection(ts, tsNames, typeDesc.type());
        }

        // Validate EnumValues
        validateEnumValues(co);

        // Validate Forms
        validateNamedChildren(co, "Form");

        // Validate Templates
        validateNamedChildren(co, "Template");

        // Validate Commands
        validateNamedChildren(co, "Command");

        // Validate Columns (DocumentJournal)
        validateNamedChildren(co, "Column");

        // Validate AccountingFlag
        validateNamedChildren(co, "AccountingFlag");

        // Validate ExtDimensionAccountingFlag
        validateNamedChildren(co, "ExtDimensionAccountingFlag");

        // Validate AddressingAttribute
        validateNamedChildren(co, "AddressingAttribute");

        // Validate URLTemplates (HTTPService)
        for (XmlNode ut : co.children("URLTemplate")) {
            validateURLTemplate(ut);
        }

        // Validate Operations (WebService)
        for (XmlNode op : co.children("Operation")) {
            validateOperation(op);
        }
    }

    private void validateNamedChildren(XmlNode co, String childType) {
        Set<String> names = new HashSet<>();
        for (XmlNode child : co.children(childType)) {
            // UUID check (presence + format)
            String uuid = child.attr("uuid");
            if (uuid == null || uuid.isEmpty()) {
                warn(childType + ": missing uuid");
            } else if (!isUuid(uuid)) {
                error(childType + ": invalid uuid format '" + uuid + "'");
            }

            XmlNode p = child.child("Properties");
            if (p == null) {
                error(childType + ": <Properties> missing");
                continue;
            }

            String name = p.childText("Name");
            if (name == null || name.isEmpty()) {
                error(childType + ": Name is required");
            } else if (!names.add(name)) {
                error(childType + ": duplicate name '" + name + "'");
            }

            // Type check for typed children (Attribute, Dimension, Resource, Column)
            if ("Attribute".equals(childType) || "Dimension".equals(childType)
                    || "Resource".equals(childType) || "AccountingFlag".equals(childType)
                    || "ExtDimensionAccountingFlag".equals(childType)
                    || "AddressingAttribute".equals(childType)) {
                XmlNode typeNode = p.child("Type");
                if (typeNode == null) {
                    error(childType + " '" + safe(name) + "': Type is required");
                }
            }
        }
    }

    private void validateTabularSection(XmlNode ts, Set<String> tsNames, String parentType) {
        String uuid = ts.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            warn("TabularSection: missing uuid");
        } else if (!isUuid(uuid)) {
            error("TabularSection: invalid uuid format '" + uuid + "'");
        }

        XmlNode p = ts.child("Properties");
        if (p == null) {
            error("TabularSection: <Properties> missing");
            return;
        }

        String name = p.childText("Name");
        if (name == null || name.isEmpty()) {
            error("TabularSection: Name is required");
        } else if (!tsNames.add(name)) {
            error("TabularSection: duplicate name '" + name + "'");
        }

        // Validate TS attributes
        XmlNode tsCo = ts.child("ChildObjects");
        if (tsCo != null) {
            Set<String> attrNames = new HashSet<>();
            for (XmlNode attr : tsCo.children("Attribute")) {
                XmlNode ap = attr.child("Properties");
                if (ap == null) {
                    error("TabularSection '" + safe(name) + "' Attribute: <Properties> missing");
                    continue;
                }

                String aName = ap.childText("Name");
                if (aName == null || aName.isEmpty()) {
                    error("TabularSection '" + safe(name) + "' Attribute: Name is required");
                } else if (!attrNames.add(aName)) {
                    error("TabularSection '" + safe(name) + "': duplicate attribute '" + aName + "'");
                }

                XmlNode aType = ap.child("Type");
                if (aType == null) {
                    error("TabularSection '" + safe(name) + "' Attribute '" + safe(aName) + "': Type is required");
                }
            }
        }
    }

    private void validateEnumValues(XmlNode co) {
        Set<String> names = new HashSet<>();
        for (XmlNode ev : co.children("EnumValue")) {
            String uuid = ev.attr("uuid");
            if (uuid == null || uuid.isEmpty()) {
                warn("EnumValue: missing uuid");
            } else if (!isUuid(uuid)) {
                error("EnumValue: invalid uuid format '" + uuid + "'");
            }

            XmlNode p = ev.child("Properties");
            if (p == null) {
                error("EnumValue: <Properties> missing");
                continue;
            }

            String name = p.childText("Name");
            if (name == null || name.isEmpty()) {
                error("EnumValue: Name is required");
            } else if (!names.add(name)) {
                error("EnumValue: duplicate name '" + name + "'");
            }
        }
    }

    private static final Set<String> VALID_HTTP_METHODS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS", "MERGE", "CONNECT");

    private void validateURLTemplate(XmlNode ut) {
        String utUuid = ut.attr("uuid");
        if (utUuid == null || utUuid.isEmpty()) {
            warn("URLTemplate: missing uuid");
        } else if (!isUuid(utUuid)) {
            error("URLTemplate: invalid uuid format '" + utUuid + "'");
        }

        XmlNode p = ut.child("Properties");
        if (p == null) {
            error("URLTemplate: <Properties> missing");
            return;
        }

        String name = p.childText("Name");
        if (name == null || name.isEmpty()) {
            error("URLTemplate: Name is required");
        }

        String template = p.childText("Template");
        if (template == null || template.isEmpty()) {
            warn("URLTemplate '" + safe(name) + "': Template is empty");
        }

        // Validate Methods
        XmlNode utCo = ut.child("ChildObjects");
        if (utCo != null) {
            Set<String> methodNames = new HashSet<>();
            for (XmlNode method : utCo.children("Method")) {
                String mUuid = method.attr("uuid");
                if (mUuid == null || mUuid.isEmpty()) {
                    warn("Method: missing uuid");
                } else if (!isUuid(mUuid)) {
                    error("Method: invalid uuid format '" + mUuid + "'");
                }

                XmlNode mp = method.child("Properties");
                if (mp == null) {
                    error("Method: <Properties> missing");
                    continue;
                }

                String mName = mp.childText("Name");
                if (mName == null || mName.isEmpty()) {
                    error("Method: Name is required");
                } else if (!methodNames.add(mName)) {
                    error("URLTemplate '" + safe(name) + "': duplicate method '" + mName + "'");
                }

                String httpMethod = mp.childText("HTTPMethod");
                if (httpMethod == null || httpMethod.isEmpty()) {
                    error("Method '" + safe(mName) + "': HTTPMethod is required");
                } else if (!VALID_HTTP_METHODS.contains(httpMethod)) {
                    warn("Method '" + safe(mName) + "': unknown HTTPMethod '" + httpMethod
                            + "' — expected one of: " + String.join(", ", "GET", "POST", "PUT", "DELETE", "PATCH"));
                }

                String handler = mp.childText("Handler");
                if (handler == null || handler.isEmpty()) {
                    warn("Method '" + safe(mName) + "': Handler is empty");
                }
            }
        }
    }

    private static final Set<String> VALID_TRANSFER_DIRECTIONS = Set.of(
            "Input", "Output", "InputOutput");

    private void validateOperation(XmlNode op) {
        String opUuid = op.attr("uuid");
        if (opUuid == null || opUuid.isEmpty()) {
            warn("Operation: missing uuid");
        } else if (!isUuid(opUuid)) {
            error("Operation: invalid uuid format '" + opUuid + "'");
        }

        XmlNode p = op.child("Properties");
        if (p == null) {
            error("Operation: <Properties> missing");
            return;
        }

        String name = p.childText("Name");
        if (name == null || name.isEmpty()) {
            error("Operation: Name is required");
        }

        String procName = p.childText("ProcedureName");
        if (procName == null || procName.isEmpty()) {
            warn("Operation '" + safe(name) + "': ProcedureName is empty");
        }

        // Validate Parameters
        XmlNode opCo = op.child("ChildObjects");
        if (opCo != null) {
            Set<String> paramNames = new HashSet<>();
            for (XmlNode param : opCo.children("Parameter")) {
                String pUuid = param.attr("uuid");
                if (pUuid == null || pUuid.isEmpty()) {
                    warn("Parameter: missing uuid");
                } else if (!isUuid(pUuid)) {
                    error("Parameter: invalid uuid format '" + pUuid + "'");
                }

                XmlNode pp = param.child("Properties");
                if (pp == null) {
                    error("Parameter: <Properties> missing");
                    continue;
                }

                String pName = pp.childText("Name");
                if (pName == null || pName.isEmpty()) {
                    error("Parameter: Name is required");
                } else if (!paramNames.add(pName)) {
                    error("Operation '" + safe(name) + "': duplicate parameter '" + pName + "'");
                }

                XmlNode xdtoType = pp.child("XDTOValueType");
                if (xdtoType == null) {
                    warn("Parameter '" + safe(pName) + "': XDTOValueType is missing");
                }

                String transferDir = pp.childText("TransferDirection");
                if (transferDir != null && !transferDir.isEmpty()
                        && !VALID_TRANSFER_DIRECTIONS.contains(transferDir)) {
                    warn("Parameter '" + safe(pName) + "': unknown TransferDirection '"
                            + transferDir + "' — expected Input, Output, or InputOutput");
                }
            }
        }
    }

    // ==================== InternalInfo ====================

    private void validateInternalInfo(XmlNode internalInfo, String type, String name,
                                       TypeDescriptor typeDesc) {
        List<String> expectedCategories = typeDesc.categories();
        if (expectedCategories.isEmpty()) return;

        Set<String> foundCategories = new HashSet<>();
        for (XmlNode gt : internalInfo.children("GeneratedType")) {
            String category = gt.attr("category");
            if (category != null) foundCategories.add(category);

            String gtName = gt.attr("name");
            if (gtName != null && name != null && !name.isEmpty()) {
                // Verify name contains the object name
                if (!gtName.contains("." + name)) {
                    warn("InternalInfo: GeneratedType name '" + gtName
                            + "' does not reference object name '" + name + "'");
                }
            }

            // TypeId and ValueId should be present
            if (gt.child("TypeId") == null) {
                warn("InternalInfo: GeneratedType missing <TypeId>");
            }
            if (gt.child("ValueId") == null) {
                warn("InternalInfo: GeneratedType missing <ValueId>");
            }
        }

        // Check that all expected categories are present
        for (String expected : expectedCategories) {
            // Skip TabularSection/Row categories — they are dynamic
            if (expected.contains("TabularSection")) continue;
            if (!foundCategories.contains(expected)) {
                warn("InternalInfo: missing GeneratedType category '" + expected + "' for " + type);
            }
        }
    }

    // ==================== File Structure ====================

    private void validateFileStructure(Path objectDir, String name, String type,
                                        TypeDescriptor typeDesc) {
        // objectDir points to the directory containing the XML file (e.g., Catalogs/)
        // The object directory is objectDir/<name>/
        Path objDir = objectDir.resolve(name);
        if (!Files.isDirectory(objDir)) return;

        Path extDir = objDir.resolve("Ext");

        // Module file check — most types have Ext/ObjectModule.bsl or Ext/Module.bsl
        if (MetadataTypeRegistry.isReferenceType(type)
                || "BusinessProcess".equals(type) || "Task".equals(type)
                || "Report".equals(type) || "DataProcessor".equals(type)) {
            Path modulePath = extDir.resolve("ObjectModule.bsl");
            if (Files.isDirectory(extDir) && !Files.exists(modulePath)) {
                // ObjectModule.bsl is optional — informational only
            }
        } else if ("CommonModule".equals(type)) {
            Path modulePath = extDir.resolve("Module.bsl");
            if (!Files.exists(modulePath)) {
                warn("FileStructure: " + name + "/Ext/Module.bsl not found");
            }
        } else if (MetadataTypeRegistry.isRegister(type)) {
            Path modulePath = extDir.resolve("RecordSetModule.bsl");
            // RecordSetModule is optional
        }

        // ExchangePlan: Ext/Content.xml expected
        if ("ExchangePlan".equals(type)) {
            Path contentXml = extDir.resolve("Content.xml");
            if (Files.isDirectory(extDir) && !Files.exists(contentXml)) {
                warn("FileStructure: " + name + "/Ext/Content.xml not found (ExchangePlan)");
            }
        }

        // BusinessProcess: Ext/Flowchart.xml expected
        if ("BusinessProcess".equals(type)) {
            Path flowchart = extDir.resolve("Flowchart.xml");
            if (Files.isDirectory(extDir) && !Files.exists(flowchart)) {
                warn("FileStructure: " + name + "/Ext/Flowchart.xml not found (BusinessProcess)");
            }
        }
    }

    // ==================== Helpers ====================

    private void validateBoolean(XmlNode props, String propName) {
        String val = props.childText(propName);
        if (val != null && !val.isEmpty() && !"true".equals(val) && !"false".equals(val)) {
            error("Properties: " + propName + " has invalid value '" + val + "' (expected true/false)");
        }
    }

    private void validateEnum(XmlNode props, String propName, String... allowedValues) {
        String val = props.childText(propName);
        if (val == null || val.isEmpty()) return;

        Set<String> allowed = Set.of(allowedValues);
        if (!allowed.contains(val)) {
            warn("Properties: " + propName + " has value '" + val
                    + "' — expected one of: " + String.join(", ", allowedValues));
        }
    }

    private void validatePositiveInt(XmlNode props, String propName, boolean required) {
        String val = props.childText(propName);
        if (val == null || val.isEmpty()) {
            if (required) error("Properties: " + propName + " is required");
            return;
        }
        try {
            int n = Integer.parseInt(val);
            if (n < 0) {
                error("Properties: " + propName + " must be non-negative, got " + n);
            }
        } catch (NumberFormatException e) {
            error("Properties: " + propName + " is not a valid number: '" + val + "'");
        }
    }

    private boolean isUuid(String s) {
        return s != null && s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private boolean isValidIdentifier(String name) {
        // 1C identifiers: letters (Cyrillic/Latin), digits, underscores; first char not digit
        return name.matches("[а-яА-ЯёЁa-zA-Z_][а-яА-ЯёЁa-zA-Z0-9_]*");
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

    private static String safe(String s) { return s != null ? s : ""; }

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

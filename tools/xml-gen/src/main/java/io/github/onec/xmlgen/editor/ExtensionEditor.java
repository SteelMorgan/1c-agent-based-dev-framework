package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Заимствование объектов конфигурации в расширение (CFE).
 *
 * Поддерживает:
 * - Заимствование объектов: Catalog.Контрагенты
 * - Заимствование форм: Catalog.Контрагенты.Form.ФормаЭлемента
 * - Batch: "Catalog.Контрагенты ;; CommonModule.ОбщийМодуль"
 * - Russian synonyms: Справочник.Контрагенты
 */
public class ExtensionEditor {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String XMLNS = "xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
            + "xmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\" "
            + "xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\" "
            + "xmlns:cmi=\"http://v8.1c.ru/8.2/managed-application/cmi\" "
            + "xmlns:ent=\"http://v8.1c.ru/8.1/data/enterprise\" "
            + "xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\" "
            + "xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\" "
            + "xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
            + "xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\" "
            + "xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\" "
            + "xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\" "
            + "xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\" "
            + "xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\" "
            + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" "
            + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

    /** Russian → English type synonyms */
    private static final Map<String, String> SYNONYM_MAP = Map.ofEntries(
            Map.entry("Справочник", "Catalog"), Map.entry("Документ", "Document"),
            Map.entry("Перечисление", "Enum"), Map.entry("ОбщийМодуль", "CommonModule"),
            Map.entry("ОбщаяКартинка", "CommonPicture"), Map.entry("ОбщаяКоманда", "CommonCommand"),
            Map.entry("ОбщийМакет", "CommonTemplate"), Map.entry("ПланОбмена", "ExchangePlan"),
            Map.entry("Отчет", "Report"), Map.entry("Отчёт", "Report"),
            Map.entry("Обработка", "DataProcessor"),
            Map.entry("РегистрСведений", "InformationRegister"),
            Map.entry("РегистрНакопления", "AccumulationRegister"),
            Map.entry("ПланВидовХарактеристик", "ChartOfCharacteristicTypes"),
            Map.entry("ПланСчетов", "ChartOfAccounts"),
            Map.entry("РегистрБухгалтерии", "AccountingRegister"),
            Map.entry("ПланВидовРасчета", "ChartOfCalculationTypes"),
            Map.entry("РегистрРасчета", "CalculationRegister"),
            Map.entry("БизнесПроцесс", "BusinessProcess"), Map.entry("Задача", "Task"),
            Map.entry("Подсистема", "Subsystem"), Map.entry("Роль", "Role"),
            Map.entry("Константа", "Constant"),
            Map.entry("ФункциональнаяОпция", "FunctionalOption"),
            Map.entry("ОпределяемыйТип", "DefinedType"),
            Map.entry("ОбщаяФорма", "CommonForm"),
            Map.entry("ЖурналДокументов", "DocumentJournal"),
            Map.entry("ПараметрСеанса", "SessionParameter"),
            Map.entry("ГруппаКоманд", "CommandGroup"),
            Map.entry("ПодпискаНаСобытие", "EventSubscription"),
            Map.entry("РегламентноеЗадание", "ScheduledJob"),
            Map.entry("ОбщийРеквизит", "CommonAttribute"),
            Map.entry("ПакетXDTO", "XDTOPackage"),
            Map.entry("HTTPСервис", "HTTPService"),
            Map.entry("СервисИнтеграции", "IntegrationService")
    );

    /** Type → directory mapping (covers all 44 types) */
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

    /** Types that need <ChildObjects/> in borrowed XML */
    private static final Set<String> TYPES_WITH_CHILD_OBJECTS = Set.of(
            "Catalog", "Document", "ExchangePlan", "ChartOfAccounts",
            "ChartOfCharacteristicTypes", "ChartOfCalculationTypes",
            "BusinessProcess", "Task", "Enum",
            "InformationRegister", "AccumulationRegister", "AccountingRegister", "CalculationRegister"
    );

    /** CommonModule-specific boolean properties to copy */
    private static final List<String> COMMON_MODULE_PROPS = List.of(
            "Global", "ClientManagedApplication", "Server",
            "ExternalConnection", "ClientOrdinaryApplication", "ServerCall"
    );

    /** 44 types in canonical order for ChildObjects */
    private static final List<String> TYPE_ORDER = List.of(
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

    /** GeneratedType definitions per metadata type */
    private static final Map<String, List<GenType>> GENERATED_TYPES = new LinkedHashMap<>();

    static {
        addGen("Catalog", "CatalogObject:Object", "CatalogRef:Ref", "CatalogSelection:Selection",
                "CatalogList:List", "CatalogManager:Manager");
        addGen("Document", "DocumentObject:Object", "DocumentRef:Ref", "DocumentSelection:Selection",
                "DocumentList:List", "DocumentManager:Manager");
        addGen("Enum", "EnumRef:Ref", "EnumManager:Manager", "EnumList:List");
        addGen("Constant", "ConstantManager:Manager", "ConstantValueManager:ValueManager",
                "ConstantValueKey:ValueKey");
        addGen("InformationRegister", "InformationRegisterRecord:Record", "InformationRegisterManager:Manager",
                "InformationRegisterSelection:Selection", "InformationRegisterList:List",
                "InformationRegisterRecordSet:RecordSet", "InformationRegisterRecordKey:RecordKey",
                "InformationRegisterRecordManager:RecordManager");
        addGen("AccumulationRegister", "AccumulationRegisterRecord:Record", "AccumulationRegisterManager:Manager",
                "AccumulationRegisterSelection:Selection", "AccumulationRegisterList:List",
                "AccumulationRegisterRecordSet:RecordSet", "AccumulationRegisterRecordKey:RecordKey");
        addGen("AccountingRegister", "AccountingRegisterRecord:Record", "AccountingRegisterManager:Manager",
                "AccountingRegisterSelection:Selection", "AccountingRegisterList:List",
                "AccountingRegisterRecordSet:RecordSet", "AccountingRegisterRecordKey:RecordKey");
        addGen("CalculationRegister", "CalculationRegisterRecord:Record", "CalculationRegisterManager:Manager",
                "CalculationRegisterSelection:Selection", "CalculationRegisterList:List",
                "CalculationRegisterRecordSet:RecordSet", "CalculationRegisterRecordKey:RecordKey");
        addGen("ChartOfAccounts", "ChartOfAccountsObject:Object", "ChartOfAccountsRef:Ref",
                "ChartOfAccountsSelection:Selection", "ChartOfAccountsList:List", "ChartOfAccountsManager:Manager");
        addGen("ChartOfCharacteristicTypes", "ChartOfCharacteristicTypesObject:Object",
                "ChartOfCharacteristicTypesRef:Ref", "ChartOfCharacteristicTypesSelection:Selection",
                "ChartOfCharacteristicTypesList:List", "ChartOfCharacteristicTypesManager:Manager");
        addGen("ChartOfCalculationTypes", "ChartOfCalculationTypesObject:Object",
                "ChartOfCalculationTypesRef:Ref", "ChartOfCalculationTypesSelection:Selection",
                "ChartOfCalculationTypesList:List", "ChartOfCalculationTypesManager:Manager",
                "DisplacingCalculationTypes:DisplacingCalculationTypes",
                "BaseCalculationTypes:BaseCalculationTypes",
                "LeadingCalculationTypes:LeadingCalculationTypes");
        addGen("BusinessProcess", "BusinessProcessObject:Object", "BusinessProcessRef:Ref",
                "BusinessProcessSelection:Selection", "BusinessProcessList:List", "BusinessProcessManager:Manager");
        addGen("Task", "TaskObject:Object", "TaskRef:Ref", "TaskSelection:Selection",
                "TaskList:List", "TaskManager:Manager");
        addGen("ExchangePlan", "ExchangePlanObject:Object", "ExchangePlanRef:Ref",
                "ExchangePlanSelection:Selection", "ExchangePlanList:List", "ExchangePlanManager:Manager");
        addGen("DocumentJournal", "DocumentJournalSelection:Selection", "DocumentJournalList:List",
                "DocumentJournalManager:Manager");
        addGen("Report", "ReportObject:Object", "ReportManager:Manager");
        addGen("DataProcessor", "DataProcessorObject:Object", "DataProcessorManager:Manager");
    }

    private static void addGen(String type, String... entries) {
        List<GenType> list = new ArrayList<>();
        for (String e : entries) {
            int colon = e.indexOf(':');
            list.add(new GenType(e.substring(0, colon), e.substring(colon + 1)));
        }
        GENERATED_TYPES.put(type, list);
    }

    record GenType(String prefix, String category) {}

    // ─── Instance fields ──────────────────────────────────────────────

    private final PrintStream out;
    private final List<String> createdFiles = new ArrayList<>();

    public ExtensionEditor() { this(System.out); }
    public ExtensionEditor(PrintStream out) { this.out = out; }

    // ─── Public API ───────────────────────────────────────────────────

    /**
     * Borrow objects from base config into extension.
     *
     * @param extDir    extension root directory (contains Configuration.xml)
     * @param configDir base configuration root directory
     * @param objectSpec object specification: "Type.Name" or batch separated by ";;"
     */
    public void borrow(Path extDir, Path configDir, String objectSpec) throws IOException {
        createdFiles.clear();

        // Resolve file paths to directories (support both dir and Configuration.xml as input)
        if (Files.isRegularFile(extDir)) extDir = extDir.getParent();
        if (Files.isRegularFile(configDir)) configDir = configDir.getParent();

        // Validate paths
        Path extCfgFile = extDir.resolve("Configuration.xml");
        if (!Files.isRegularFile(extCfgFile)) {
            throw new IllegalArgumentException("Extension Configuration.xml not found: " + extCfgFile);
        }
        Path baseCfgFile = configDir.resolve("Configuration.xml");
        if (!Files.isRegularFile(baseCfgFile)) {
            throw new IllegalArgumentException("Base Configuration.xml not found: " + baseCfgFile);
        }

        // Read extension Configuration.xml
        String extCfgContent = readString(extCfgFile);

        // Parse object specs
        List<BorrowItem> items = parseObjectSpec(objectSpec);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("No objects specified in objectSpec");
        }

        // Process each item
        int borrowedCount = 0;
        for (BorrowItem item : items) {
            String dirName = TYPE_TO_DIR.get(item.typeName);
            if (dirName == null) {
                throw new IllegalArgumentException("Unknown type: " + item.typeName);
            }

            if (item.formName != null) {
                // Form borrowing — ensure parent is borrowed first
                if (!isObjectBorrowed(extDir, dirName, item.objName)) {
                    out.println("[INFO] Parent " + item.typeName + "." + item.objName
                            + " not yet borrowed — borrowing first...");
                    extCfgContent = borrowObject(extDir, configDir, item.typeName,
                            item.objName, dirName, extCfgContent);
                }
                borrowForm(extDir, configDir, item.typeName, item.objName, item.formName, dirName);
                borrowedCount++;
            } else {
                // Object borrowing
                extCfgContent = borrowObject(extDir, configDir, item.typeName,
                        item.objName, dirName, extCfgContent);
                borrowedCount++;
            }
        }

        // Save modified Configuration.xml
        writeWithBom(extCfgFile, extCfgContent);

        // Summary
        out.println();
        out.println("=== extension borrow summary ===");
        out.println("  Extension:  " + extDir);
        out.println("  Config:     " + configDir);
        out.println("  Borrowed:   " + borrowedCount + " object(s)");
        for (String f : createdFiles) {
            out.println("    - " + f);
        }
    }

    // ─── Object borrowing ─────────────────────────────────────────────

    private String borrowObject(Path extDir, Path configDir, String typeName,
                                String objName, String dirName, String extCfgContent) throws IOException {
        out.println("[INFO] Borrowing " + typeName + "." + objName + "...");

        // Read source object UUID
        String sourceUuid = readSourceObjectUuid(configDir, dirName, objName, typeName);
        out.println("[INFO]   Source UUID: " + sourceUuid);

        // Read CommonModule properties if applicable
        Map<String, String> sourceProps = Collections.emptyMap();
        if ("CommonModule".equals(typeName)) {
            sourceProps = readCommonModuleProperties(configDir, dirName, objName);
        }

        // Generate borrowed object XML
        String borrowedXml = buildBorrowedObjectXml(typeName, objName, sourceUuid, sourceProps);

        // Write to extension directory
        Path targetDir = extDir.resolve(dirName);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(objName + ".xml");
        writeWithBom(targetFile, borrowedXml);
        out.println("[INFO]   Created: " + targetFile);
        createdFiles.add(targetFile.toString());

        // Add to extension Configuration.xml ChildObjects
        extCfgContent = addToChildObjects(extCfgContent, typeName, objName);

        return extCfgContent;
    }

    // ─── Form borrowing ───────────────────────────────────────────────

    private void borrowForm(Path extDir, Path configDir, String typeName,
                            String objName, String formName, String dirName) throws IOException {
        out.println("[INFO] Borrowing form " + typeName + "." + objName + ".Form." + formName + "...");

        // 1. Read source form UUID
        String sourceFormUuid = readSourceFormUuid(configDir, dirName, objName, formName);
        out.println("[INFO]   Source form UUID: " + sourceFormUuid);

        // 2. Read source Form.xml content
        Path srcFormXmlPath = configDir.resolve(dirName).resolve(objName)
                .resolve("Forms").resolve(formName).resolve("Ext").resolve("Form.xml");
        if (!Files.isRegularFile(srcFormXmlPath)) {
            throw new IllegalArgumentException("Source Form.xml not found: " + srcFormXmlPath);
        }
        String srcFormContent = readString(srcFormXmlPath);

        // 3. Generate form metadata XML
        String newFormUuid = UuidGenerator.generate();
        String formMetaXml = buildFormMetadataXml(formName, newFormUuid, sourceFormUuid);

        // 4. Write form metadata
        Path formMetaDir = extDir.resolve(dirName).resolve(objName).resolve("Forms");
        Files.createDirectories(formMetaDir);
        Path formMetaFile = formMetaDir.resolve(formName + ".xml");
        writeWithBom(formMetaFile, formMetaXml);
        out.println("[INFO]   Created: " + formMetaFile);
        createdFiles.add(formMetaFile.toString());

        // 5. Generate Form.xml with BaseForm
        String formXml = buildFormXmlWithBaseForm(srcFormContent);
        Path formXmlDir = formMetaDir.resolve(formName).resolve("Ext");
        Files.createDirectories(formXmlDir);
        Path formXmlFile = formXmlDir.resolve("Form.xml");
        writeWithBom(formXmlFile, formXml);
        out.println("[INFO]   Created: " + formXmlFile);
        createdFiles.add(formXmlFile.toString());

        // 6. Create empty Module.bsl
        Path moduleDir = formXmlDir.resolve("Form");
        Files.createDirectories(moduleDir);
        Path moduleBslFile = moduleDir.resolve("Module.bsl");
        writeWithBom(moduleBslFile, "");
        out.println("[INFO]   Created: " + moduleBslFile);
        createdFiles.add(moduleBslFile.toString());

        // 7. Register form in parent object ChildObjects
        registerFormInObject(extDir, dirName, objName, formName);
    }

    // ─── Source reading ───────────────────────────────────────────────

    private String readSourceObjectUuid(Path configDir, String dirName,
                                        String objName, String typeName) throws IOException {
        Path srcFile = configDir.resolve(dirName).resolve(objName + ".xml");
        if (!Files.isRegularFile(srcFile)) {
            throw new IllegalArgumentException("Source object not found: " + srcFile);
        }
        String content = readString(srcFile);
        // Extract uuid from the type element: <TypeName uuid="...">
        Pattern p = Pattern.compile("<" + Pattern.quote(typeName) + "\\s+uuid=\"([^\"]+)\"");
        Matcher m = p.matcher(content);
        if (!m.find()) {
            throw new IllegalArgumentException("No uuid found on <" + typeName + "> in " + srcFile);
        }
        return m.group(1);
    }

    private String readSourceFormUuid(Path configDir, String dirName,
                                      String objName, String formName) throws IOException {
        Path srcFile = configDir.resolve(dirName).resolve(objName)
                .resolve("Forms").resolve(formName + ".xml");
        if (!Files.isRegularFile(srcFile)) {
            throw new IllegalArgumentException("Source form not found: " + srcFile);
        }
        String content = readString(srcFile);
        Matcher m = Pattern.compile("<Form\\s+uuid=\"([^\"]+)\"").matcher(content);
        if (!m.find()) {
            throw new IllegalArgumentException("No uuid found on <Form> in " + srcFile);
        }
        return m.group(1);
    }

    private Map<String, String> readCommonModuleProperties(Path configDir, String dirName,
                                                           String objName) throws IOException {
        Path srcFile = configDir.resolve(dirName).resolve(objName + ".xml");
        String content = readString(srcFile);
        Map<String, String> props = new LinkedHashMap<>();
        for (String prop : COMMON_MODULE_PROPS) {
            Pattern p = Pattern.compile("<" + prop + ">([^<]*)</" + prop + ">");
            Matcher m = p.matcher(content);
            props.put(prop, m.find() ? m.group(1) : "false");
        }
        return props;
    }

    // ─── XML generation ───────────────────────────────────────────────

    private String buildBorrowedObjectXml(String typeName, String objName,
                                          String sourceUuid, Map<String, String> sourceProps) {
        String newUuid = UuidGenerator.generate();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"2.17\">\n");
        sb.append("\t<").append(typeName).append(" uuid=\"").append(newUuid).append("\">\n");

        // InternalInfo with GeneratedTypes
        sb.append(buildInternalInfoXml(typeName, objName, "\t\t")).append("\n");

        // Properties
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n");
        sb.append("\t\t\t<Name>").append(esc(objName)).append("</Name>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<ExtendedConfigurationObject>").append(sourceUuid).append("</ExtendedConfigurationObject>\n");

        // CommonModule-specific properties
        if ("CommonModule".equals(typeName)) {
            for (String prop : COMMON_MODULE_PROPS) {
                String val = sourceProps.getOrDefault(prop, "false");
                sb.append("\t\t\t<").append(prop).append(">").append(val).append("</").append(prop).append(">\n");
            }
        }

        sb.append("\t\t</Properties>\n");

        // ChildObjects for types that support them
        if (TYPES_WITH_CHILD_OBJECTS.contains(typeName)) {
            sb.append("\t\t<ChildObjects/>\n");
        }

        sb.append("\t</").append(typeName).append(">\n");
        sb.append("</MetaDataObject>");
        return sb.toString();
    }

    private String buildInternalInfoXml(String typeName, String objName, String indent) {
        List<GenType> types = GENERATED_TYPES.get(typeName);
        if (types == null || types.isEmpty()) {
            return indent + "<InternalInfo/>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<InternalInfo>\n");

        // ExchangePlan special case
        if ("ExchangePlan".equals(typeName)) {
            sb.append(indent).append("\t<xr:ThisNode>").append(UuidGenerator.generate()).append("</xr:ThisNode>\n");
        }

        for (GenType gt : types) {
            String fullName = gt.prefix() + "." + objName;
            String typeId = UuidGenerator.generate();
            String valueId = UuidGenerator.generate();
            sb.append(indent).append("\t<xr:GeneratedType name=\"").append(fullName)
                    .append("\" category=\"").append(gt.category()).append("\">\n");
            sb.append(indent).append("\t\t<xr:TypeId>").append(typeId).append("</xr:TypeId>\n");
            sb.append(indent).append("\t\t<xr:ValueId>").append(valueId).append("</xr:ValueId>\n");
            sb.append(indent).append("\t</xr:GeneratedType>\n");
        }

        sb.append(indent).append("</InternalInfo>");
        return sb.toString();
    }

    private String buildFormMetadataXml(String formName, String newFormUuid, String sourceFormUuid) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"2.17\">\n");
        sb.append("\t<Form uuid=\"").append(newFormUuid).append("\">\n");
        sb.append("\t\t<InternalInfo/>\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n");
        sb.append("\t\t\t<Name>").append(esc(formName)).append("</Name>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<ExtendedConfigurationObject>").append(sourceFormUuid).append("</ExtendedConfigurationObject>\n");
        sb.append("\t\t\t<FormType>Managed</FormType>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Form>\n");
        sb.append("</MetaDataObject>");
        return sb.toString();
    }

    private String buildFormXmlWithBaseForm(String srcFormContent) {
        // Extract xml declaration
        String xmlDecl = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
        Matcher mDecl = Pattern.compile("^(<\\?xml[^?]*\\?>)").matcher(srcFormContent);
        if (mDecl.find()) xmlDecl = mDecl.group(1);

        // Extract <Form ...> opening tag and version
        String formTag = "<Form version=\"2.17\">";
        String formVersion = "2.17";
        Matcher mTag = Pattern.compile("<Form[^>]*version=\"([^\"]+)\"[^>]*>").matcher(srcFormContent);
        if (mTag.find()) {
            formVersion = mTag.group(1);
            formTag = mTag.group(0);
        }

        // Extract AutoCommandBar and ChildItems via regex
        String autoCommandBarXml = extractTopLevelElement(srcFormContent, "AutoCommandBar");
        String childItemsXml = extractTopLevelElement(srcFormContent, "ChildItems");
        if (childItemsXml == null) childItemsXml = "<ChildItems/>";

        // Strip namespace declarations
        Pattern nsPattern = Pattern.compile("\\s+xmlns(?::\\w+)?=\"[^\"]*\"");
        if (autoCommandBarXml != null) {
            autoCommandBarXml = nsPattern.matcher(autoCommandBarXml).replaceAll("");
            autoCommandBarXml = autoCommandBarXml.replaceAll("<CommandName>[^<]*</CommandName>", "<CommandName>0</CommandName>");
            autoCommandBarXml = autoCommandBarXml.replace("<Autofill>true</Autofill>", "<Autofill>false</Autofill>");
        }
        childItemsXml = nsPattern.matcher(childItemsXml).replaceAll("");
        childItemsXml = childItemsXml.replaceAll("<CommandName>[^<]*</CommandName>", "<CommandName>0</CommandName>");

        // Build output with \r\n line endings (as in Python reference)
        StringBuilder sb = new StringBuilder();
        sb.append(xmlDecl).append("\r\n");
        sb.append(formTag).append("\r\n");

        if (autoCommandBarXml != null) {
            sb.append("\t").append(autoCommandBarXml).append("\r\n");
        }
        sb.append("\t").append(childItemsXml).append("\r\n");
        sb.append("\t<Attributes/>\r\n");

        // BaseForm
        sb.append("\t<BaseForm version=\"").append(formVersion).append("\">\r\n");
        if (autoCommandBarXml != null) {
            appendIndented(sb, autoCommandBarXml, "\t\t", "\t");
        }
        appendIndented(sb, childItemsXml, "\t\t", "\t");
        sb.append("\t\t<Attributes/>\r\n");
        sb.append("\t</BaseForm>\r\n");
        sb.append("</Form>");

        return sb.toString();
    }

    /** Append multi-line XML with extra indentation: first line gets firstIndent, rest get otherIndent prefix */
    private void appendIndented(StringBuilder sb, String xml, String firstIndent, String otherIndent) {
        String[] lines = xml.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                sb.append(firstIndent).append(lines[i]);
            } else {
                sb.append(otherIndent).append(lines[i]);
            }
            sb.append("\r\n");
        }
    }

    // ─── Configuration.xml editing ────────────────────────────────────

    /**
     * Add object to extension Configuration.xml ChildObjects in canonical order.
     */
    private String addToChildObjects(String content, String typeName, String objName) {
        // Dedup check
        Pattern dedup = Pattern.compile("<" + Pattern.quote(typeName) + ">" + Pattern.quote(objName)
                + "</" + Pattern.quote(typeName) + ">");
        if (dedup.matcher(content).find()) {
            out.println("[WARN] Already in ChildObjects: " + typeName + "." + objName);
            return content;
        }

        // Handle self-closing <ChildObjects/>
        int selfClosing = content.indexOf("<ChildObjects/>");
        if (selfClosing >= 0) {
            String replacement = "<ChildObjects>\n\t\t\t<" + typeName + ">" + esc(objName)
                    + "</" + typeName + ">\n\t\t</ChildObjects>";
            content = content.substring(0, selfClosing) + replacement
                    + content.substring(selfClosing + "<ChildObjects/>".length());
            out.println("[INFO] Added to ChildObjects: " + typeName + "." + objName);
            return content;
        }

        // Find insertion point in canonical order
        int typeIdx = TYPE_ORDER.indexOf(typeName);
        if (typeIdx < 0) {
            throw new IllegalArgumentException("Unknown type for ChildObjects ordering: " + typeName);
        }

        // Find </ChildObjects> closing tag
        int closeTag = content.lastIndexOf("</ChildObjects>");
        if (closeTag < 0) {
            throw new IllegalArgumentException("No <ChildObjects> found in Configuration.xml");
        }

        // Extract ChildObjects section for analysis — use lastIndexOf to match root-level
        int openTag = content.lastIndexOf("<ChildObjects>");
        if (openTag < 0) {
            throw new IllegalArgumentException("No <ChildObjects> opening tag found");
        }
        String childSection = content.substring(openTag, closeTag);

        // Find best insertion point: scan existing entries
        int insertPos = closeTag; // default: before </ChildObjects>
        String newEntry = "\t\t\t<" + typeName + ">" + esc(objName) + "</" + typeName + ">\n";

        // Look for same-type entries (alphabetical) or next type in order
        Pattern entryPattern = Pattern.compile("<(\\w+)>([^<]*)</\\1>");
        Matcher entryMatcher = entryPattern.matcher(childSection);
        while (entryMatcher.find()) {
            String entryType = entryMatcher.group(1);
            String entryName = entryMatcher.group(2);
            int entryTypeIdx = TYPE_ORDER.indexOf(entryType);
            if (entryTypeIdx < 0) continue;

            int absPos = openTag + entryMatcher.start();

            if (entryType.equals(typeName) && entryName.compareTo(objName) > 0) {
                // Insert before this same-type entry (alphabetical)
                insertPos = absPos;
                break;
            } else if (entryTypeIdx > typeIdx) {
                // Insert before this later-type entry
                insertPos = absPos;
                break;
            }
        }

        content = content.substring(0, insertPos) + newEntry + content.substring(insertPos);
        out.println("[INFO] Added to ChildObjects: " + typeName + "." + objName);
        return content;
    }

    private void registerFormInObject(Path extDir, String dirName,
                                      String objName, String formName) throws IOException {
        Path objFile = extDir.resolve(dirName).resolve(objName + ".xml");
        if (!Files.isRegularFile(objFile)) {
            out.println("[WARN] Parent object file not found: " + objFile + " — form not registered");
            return;
        }

        String content = readString(objFile);

        // Dedup
        if (content.contains("<Form>" + formName + "</Form>")) {
            out.println("[WARN] Form '" + formName + "' already in ChildObjects of " + objName);
            return;
        }

        String formEntry = "\t\t\t<Form>" + esc(formName) + "</Form>\n";

        // Handle self-closing <ChildObjects/>
        int selfClosing = content.indexOf("<ChildObjects/>");
        if (selfClosing >= 0) {
            String replacement = "<ChildObjects>\n" + formEntry + "\t\t</ChildObjects>";
            content = content.substring(0, selfClosing) + replacement
                    + content.substring(selfClosing + "<ChildObjects/>".length());
        } else {
            int closeTag = content.lastIndexOf("</ChildObjects>");
            if (closeTag >= 0) {
                content = content.substring(0, closeTag) + formEntry + content.substring(closeTag);
            } else {
                out.println("[WARN] No <ChildObjects> in " + objFile + " — form not registered");
                return;
            }
        }

        writeWithBom(objFile, content);
        out.println("[INFO]   Registered form in: " + objFile);
    }

    // ─── Object spec parsing ──────────────────────────────────────────

    private List<BorrowItem> parseObjectSpec(String objectSpec) {
        List<BorrowItem> items = new ArrayList<>();
        for (String part : objectSpec.split(";;")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            int dot = trimmed.indexOf('.');
            if (dot < 1) {
                throw new IllegalArgumentException(
                        "Invalid format '" + trimmed + "', expected 'Type.Name' or 'Type.Name.Form.FormName'");
            }

            String typeName = trimmed.substring(0, dot);
            String remainder = trimmed.substring(dot + 1);

            // Russian → English
            if (SYNONYM_MAP.containsKey(typeName)) {
                typeName = SYNONYM_MAP.get(typeName);
            }

            if (!TYPE_TO_DIR.containsKey(typeName)) {
                throw new IllegalArgumentException("Unknown type: " + typeName);
            }

            // Check for .Form. in remainder
            int formIdx = remainder.indexOf(".Form.");
            if (formIdx > 0) {
                String objName = remainder.substring(0, formIdx);
                String formName = remainder.substring(formIdx + 6);
                items.add(new BorrowItem(typeName, objName, formName));
            } else {
                items.add(new BorrowItem(typeName, remainder, null));
            }
        }
        return items;
    }

    record BorrowItem(String typeName, String objName, String formName) {}

    // ─── Helpers ──────────────────────────────────────────────────────

    /**
     * Extract a top-level element from Form.xml content by tag name.
     * Returns the full element XML or null if not found.
     */
    private String extractTopLevelElement(String content, String tagName) {
        // Match top-level element (direct child of <Form>)
        // Support both tab and space indentation
        // Look for self-closing first
        Pattern selfClose = Pattern.compile("(?m)^[ \\t]+<" + tagName + "\\s*/>");
        Matcher scm = selfClose.matcher(content);
        if (scm.find()) {
            return scm.group().stripLeading();
        }

        // Look for opening tag
        Pattern openPattern = Pattern.compile("(?m)^[ \\t]+<" + tagName + "[\\s>]");
        Matcher om = openPattern.matcher(content);
        if (!om.find()) return null;

        int tagStart = content.indexOf('<', om.start());
        if (tagStart < 0) return null;

        // Depth-based matching: count nested <tagName> opens and </tagName> closes
        // to find the correct closing tag (handles nested ChildItems in groups/tables)
        Pattern openOrClose = Pattern.compile("<(/?" + tagName + ")(?:[\\s>]|/>)");
        Matcher m = openOrClose.matcher(content);
        int depth = 0;
        int endIdx = -1;
        // Start scanning from tagStart
        int pos = tagStart;
        while (m.find(pos)) {
            String matched = m.group(1);
            String fullMatch = m.group();
            if (matched.startsWith("/")) {
                // Closing tag
                depth--;
                if (depth == 0) {
                    // Find end of this closing tag
                    int closeEnd = content.indexOf('>', m.start()) + 1;
                    endIdx = closeEnd;
                    break;
                }
            } else if (fullMatch.endsWith("/>")) {
                // Self-closing — doesn't change depth (but we already found our open)
                if (depth == 0) depth = 1; // shouldn't happen, but defensive
            } else {
                // Opening tag
                depth++;
            }
            pos = m.end();
        }

        if (endIdx < 0) return null;
        return content.substring(tagStart, endIdx);
    }

    private boolean isObjectBorrowed(Path extDir, String dirName, String objName) {
        return Files.isRegularFile(extDir.resolve(dirName).resolve(objName + ".xml"));
    }

    private String readString(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        // Strip BOM if present
        if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private void writeWithBom(Path path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + bytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(bytes, 0, result, BOM.length, bytes.length);
        Files.write(path, result);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

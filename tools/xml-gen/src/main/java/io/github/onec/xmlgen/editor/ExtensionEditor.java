package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.model.BslMethodExtractor;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.MdoPathResolver;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.io.InputStream;
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

    private static final List<String> FORM_BINDING_DATA_TAGS = List.of(
            "DataPath", "TitleDataPath", "FooterDataPath", "HeaderDataPath",
            "MultipleValueDataPath", "MultipleValuePresentDataPath");

    private static final List<String> FORM_BINDING_PICTURE_TAGS = List.of(
            "RowPictureDataPath", "MultipleValuePictureDataPath");

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

    /** Режим заимствования основного реквизита формы. */
    public enum MainAttributeMode {
        /** Только реквизиты, на которые ссылается DataPath элементов формы. */
        FORM,
        /** Все атрибуты + табличные части. */
        ALL
    }

    /**
     * Borrow objects from base config into extension.
     *
     * @param extDir    extension root directory (contains Configuration.xml)
     * @param configDir base configuration root directory
     * @param objectSpec object specification: "Type.Name" or batch separated by ";;"
     */
    public void borrow(Path extDir, Path configDir, String objectSpec) throws IOException {
        borrow(extDir, configDir, objectSpec, null);
    }

    /**
     * Borrow с опциональным {@code --borrow-main-attribute}.
     * Если {@code mainAttributeMode != null}, после стандартного borrow для форм
     * (objectSpec вида {@code Type.Name.Form.X}) копирует реквизиты объекта
     * базовой конфигурации в объект расширения (см. {@link MainAttributeMode}).
     */
    public void borrow(Path extDir, Path configDir, String objectSpec,
                       MainAttributeMode mainAttributeMode) throws IOException {
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
        String formatVersion = ConfigurationXmlReader.readFormatVersion(extCfgFile);

        // Parse object specs
        List<BorrowItem> items = parseObjectSpec(objectSpec);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("No objects specified in objectSpec");
        }

        // Pre-check: --borrow-main-attribute applies only to form specs
        if (mainAttributeMode != null) {
            for (BorrowItem item : items) {
                if (item.formName == null) {
                    throw new IllegalArgumentException(
                            "--borrow-main-attribute requires a form object spec "
                                    + "(e.g. Catalog.X.Form.Y), got: "
                                    + item.typeName + "." + item.objName);
                }
            }
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
                            item.objName, dirName, extCfgContent, formatVersion);
                }
                borrowForm(extDir, configDir, item.typeName, item.objName, item.formName, dirName,
                        formatVersion, mainAttributeMode != null);
                if (mainAttributeMode != null) {
                    borrowMainAttributeInto(extDir, configDir, item.typeName, item.objName,
                            item.formName, dirName, mainAttributeMode);
                }
                borrowedCount++;
            } else {
                // Object borrowing
                extCfgContent = borrowObject(extDir, configDir, item.typeName,
                        item.objName, dirName, extCfgContent, formatVersion);
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
                                String objName, String dirName, String extCfgContent,
                                String formatVersion) throws IOException {
        out.println("[INFO] Borrowing " + typeName + "." + objName + "...");

        Path targetDir = extDir.resolve(dirName);
        Path targetFile = targetDir.resolve(objName + ".xml");
        if (Files.isRegularFile(targetFile)) {
            out.println("[WARN]   Already exists, not overwritten: " + targetFile);
            return addToChildObjects(extCfgContent, typeName, objName);
        }

        // Read source object UUID
        String sourceUuid = readSourceObjectUuid(configDir, dirName, objName, typeName);
        out.println("[INFO]   Source UUID: " + sourceUuid);

        // Read source properties if applicable
        Map<String, String> sourceProps = Collections.emptyMap();
        if ("CommonModule".equals(typeName)) {
            sourceProps = readCommonModuleProperties(configDir, dirName, objName);
        } else if ("DefinedType".equals(typeName)) {
            sourceProps = readDefinedTypeProperties(configDir, dirName, objName);
        }

        // Generate borrowed object XML
        String borrowedXml = buildBorrowedObjectXml(typeName, objName, sourceUuid, sourceProps, formatVersion);

        // Write to extension directory
        Files.createDirectories(targetDir);
        writeWithBom(targetFile, borrowedXml);
        out.println("[INFO]   Created: " + targetFile);
        createdFiles.add(targetFile.toString());

        // Add to extension Configuration.xml ChildObjects
        extCfgContent = addToChildObjects(extCfgContent, typeName, objName);

        return extCfgContent;
    }

    // ─── Form borrowing ───────────────────────────────────────────────

    private void borrowForm(Path extDir, Path configDir, String typeName,
                            String objName, String formName, String dirName,
                            String formatVersion, boolean keepObjectDataBindings) throws IOException {
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
        String formMetaXml = buildFormMetadataXml(formName, newFormUuid, sourceFormUuid, formatVersion);

        // 4. Write form metadata without overwriting existing extension changes
        Path formMetaDir = extDir.resolve(dirName).resolve(objName).resolve("Forms");
        Files.createDirectories(formMetaDir);
        Path formMetaFile = formMetaDir.resolve(formName + ".xml");
        writeIfMissing(formMetaFile, formMetaXml);

        // 5. Generate Form.xml with BaseForm
        String formXml = buildFormXmlWithBaseForm(srcFormContent, keepObjectDataBindings);
        Path formXmlDir = formMetaDir.resolve(formName).resolve("Ext");
        Files.createDirectories(formXmlDir);
        Path formXmlFile = formXmlDir.resolve("Form.xml");
        writeIfMissing(formXmlFile, formXml);

        // 6. Create empty Module.bsl
        Path moduleDir = formXmlDir.resolve("Form");
        Files.createDirectories(moduleDir);
        Path moduleBslFile = moduleDir.resolve("Module.bsl");
        writeIfMissing(moduleBslFile, "");

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

    private Map<String, String> readDefinedTypeProperties(Path configDir, String dirName,
                                                          String objName) throws IOException {
        Path srcFile = configDir.resolve(dirName).resolve(objName + ".xml");
        String content = readString(srcFile);
        Map<String, String> props = new LinkedHashMap<>();
        String properties = extractFirstElementBlock(content, "Properties");
        if (properties != null) {
            String typeXml = extractFirstElementBlock(properties, "Type");
            if (typeXml != null) {
                props.put("__TypeXml", stripNamespaceDeclarations(typeXml));
            }
        }
        return props;
    }

    // ─── XML generation ───────────────────────────────────────────────

    private String buildBorrowedObjectXml(String typeName, String objName,
                                          String sourceUuid, Map<String, String> sourceProps,
                                          String formatVersion) {
        String newUuid = UuidGenerator.generate();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"").append(formatVersion).append("\">\n");
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
        } else if ("DefinedType".equals(typeName) && sourceProps.containsKey("__TypeXml")) {
            sb.append("\t\t\t").append(sourceProps.get("__TypeXml")).append("\n");
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

    private String buildFormMetadataXml(String formName, String newFormUuid, String sourceFormUuid,
                                        String formatVersion) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(XMLNS).append(" version=\"").append(formatVersion).append("\">\n");
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

    private String buildFormXmlWithBaseForm(String srcFormContent, boolean keepObjectDataBindings) {
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
        if (autoCommandBarXml != null) {
            autoCommandBarXml = stripNamespaceDeclarations(autoCommandBarXml);
            autoCommandBarXml = autoCommandBarXml.replaceAll("<CommandName>[^<]*</CommandName>", "<CommandName>0</CommandName>");
            autoCommandBarXml = autoCommandBarXml.replace("<Autofill>true</Autofill>", "<Autofill>false</Autofill>");
            //++agent TASK-175 [07.06.2026 18:45:00]
            // XG-35: strip-операции безусловной ветки cfe-borrow.py @ HEAD (upstream 7abe26af, de7e943d).
            // Ссылки на стандартные команды и пути данных базовой формы невалидны в расширении —
            // Designer отказывается загружать форму. Набор для ACB отличается от набора для
            // ChildItems (см. ниже) — НЕ объединять (риск 1 §7.2 дизайна TASK-175).
            // В режиме --borrow-main-attribute сохраняем Объект.* data bindings: соответствующие
            // реквизиты будут заимствованы в объект расширения ниже.
            autoCommandBarXml = autoCommandBarXml.replaceAll("\\s*<ExcludedCommand>[^<]*</ExcludedCommand>", "");
            autoCommandBarXml = stripFormBindings(autoCommandBarXml, keepObjectDataBindings);
            // CommandSet, опустевший после strip ExcludedCommand, не несёт информации — убираем,
            // иначе в выводе останется пустая обёртка, которой нет в каноне Designer
            autoCommandBarXml = autoCommandBarXml.replaceAll("\\s*<CommandSet>\\s*</CommandSet>", "");
            //++agent TASK-175
        }
        childItemsXml = stripNamespaceDeclarations(childItemsXml);
        childItemsXml = childItemsXml.replaceAll("<CommandName>[^<]*</CommandName>", "<CommandName>0</CommandName>");
        //++agent TASK-175 [07.06.2026 18:45:00]
        // XG-35: strip-набор для ChildItems формы (безусловная ветка cfe-borrow.py @ HEAD):
        // DataPath/TitleDataPath ссылаются на реквизиты базовой формы (в расширении их нет),
        // RowPictureDataPath и ExcludedCommand невалидны в расширении (7abe26af),
        // TypeLink с человекочитаемым DataPath Items.* и element-level Events не переносимы.
        childItemsXml = stripFormBindings(childItemsXml, keepObjectDataBindings);
        childItemsXml = childItemsXml.replaceAll("\\s*<ExcludedCommand>[^<]*</ExcludedCommand>", "");
        childItemsXml = childItemsXml.replaceAll("(?s)\\s*<TypeLink>\\s*<xr:DataPath>Items\\.[^<]*</xr:DataPath>.*?</TypeLink>", "");
        childItemsXml = childItemsXml.replaceAll("(?s)\\s*<Events>.*?</Events>", "");
        childItemsXml = childItemsXml.replaceAll("\\s*<CommandSet>\\s*</CommandSet>", "");
        //++agent TASK-175

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

    private String stripFormBindings(String xml, boolean keepObjectDataBindings) {
        String result = xml;
        for (String tag : FORM_BINDING_DATA_TAGS) {
            String pattern = keepObjectDataBindings
                    ? "\\s*<" + tag + ">(?!Объект\\.)[^<]*</" + tag + ">"
                    : "\\s*<" + tag + ">[^<]*</" + tag + ">";
            result = result.replaceAll(pattern, "");
        }
        for (String tag : FORM_BINDING_PICTURE_TAGS) {
            result = result.replaceAll("\\s*<" + tag + ">[^<]*</" + tag + ">", "");
        }
        return result;
    }

    private static String stripNamespaceDeclarations(String xml) {
        return xml.replaceAll("\\s+xmlns(?::\\w+)?=\"[^\"]*\"", "");
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
        //**agent TASK-175 [07.06.2026 18:45:00]
        // XG-35 (находка 3b): раньше брали ПЕРВОЕ вхождение тега в документе — у форм, где
        // AutoCommandBar идёт раньше form-level ChildItems (например, СообщениеSMS), извлекался
        // <ChildItems> ВНУТРИ ACB, и дерево формы терялось целиком. Привязываемся к отступу
        // прямых детей <Form> (первый отступ в документе): upstream (cfe-borrow.py) итерирует
        // именно direct children корня.
        String topIndent = "\t";
        Matcher firstChild = Pattern.compile("(?m)^([ \\t]+)<\\w").matcher(content);
        if (firstChild.find()) {
            topIndent = firstChild.group(1);
        }
        String indentRe = Pattern.quote(topIndent);

        // Look for self-closing first
        //Pattern selfClose = Pattern.compile("(?m)^[ \\t]+<" + tagName + "\\s*/>");
        Pattern selfClose = Pattern.compile("(?m)^" + indentRe + "<" + tagName + "\\s*/>");
        Matcher scm = selfClose.matcher(content);
        if (scm.find()) {
            return scm.group().stripLeading();
        }

        // Look for opening tag
        //Pattern openPattern = Pattern.compile("(?m)^[ \\t]+<" + tagName + "[\\s>]");
        Pattern openPattern = Pattern.compile("(?m)^" + indentRe + "<" + tagName + "[\\s>]");
        Matcher om = openPattern.matcher(content);
        if (!om.find()) return null;
        //**agent TASK-175

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

    private String extractFirstElementBlock(String content, String tagName) {
        Matcher start = Pattern.compile("<" + Pattern.quote(tagName) + "(?:\\s[^>]*)?>").matcher(content);
        if (!start.find()) return null;
        int tagStart = start.start();
        Pattern openOrClose = Pattern.compile("<(/?" + Pattern.quote(tagName) + ")(?:\\s|>|/>)");
        Matcher m = openOrClose.matcher(content);
        int depth = 0;
        int pos = tagStart;
        while (m.find(pos)) {
            String matched = m.group(1);
            String fullMatch = m.group();
            if (matched.startsWith("/")) {
                depth--;
                if (depth == 0) {
                    int closeEnd = content.indexOf('>', m.start()) + 1;
                    return content.substring(tagStart, closeEnd);
                }
            } else if (!fullMatch.endsWith("/>")) {
                depth++;
            }
            pos = m.end();
        }
        return null;
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
        //++agent TASK-172 [02.06.2026 07:15:00]
        // Канон Designer (_Демо): новые scaffold-файлы расширения (xml/.bsl) — BOM + CRLF.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    private void writeIfMissing(Path path, String content) throws IOException {
        if (Files.isRegularFile(path)) {
            out.println("[WARN]   Already exists, not overwritten: " + path);
            return;
        }
        writeWithBom(path, content);
        out.println("[INFO]   Created: " + path);
        createdFiles.add(path.toString());
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ─── --borrow-main-attribute ──────────────────────────────────────

    /**
     * Реализация {@code --borrow-main-attribute}. Копирует реквизиты объекта базовой конфигурации
     * в XML объекта расширения. Существующие реквизиты (по имени) не перезаписываются.
     *
     * <p>Метод вызывается из {@link #borrow(Path, Path, String, MainAttributeMode)} ПОСЛЕ обработки
     * формы. Формальное условие — заимствованный родительский объект уже существует на диске
     * (или будет создан в borrow до этого вызова).</p>
     */
    private void borrowMainAttributeInto(Path extDir, Path configDir, String typeName, String objName,
                                          String formName, String dirName,
                                          MainAttributeMode mode) throws IOException {
        out.println("[INFO] Borrowing main attribute (" + mode.name().toLowerCase(Locale.ROOT)
                + ") for " + typeName + "." + objName + "...");

        // 1. Прочитать XML объекта базовой конфигурации
        Path baseObjXml = configDir.resolve(dirName).resolve(objName + ".xml");
        if (!Files.isRegularFile(baseObjXml)) {
            throw new IllegalArgumentException("Base object XML not found: " + baseObjXml);
        }
        String baseObjContent = readString(baseObjXml);

        // 2. Собрать список имён реквизитов для копирования
        Set<String> namesToBorrow;
        boolean copyTabularSections;
        if (mode == MainAttributeMode.FORM) {
            Path formXml = configDir.resolve(dirName).resolve(objName)
                    .resolve("Forms").resolve(formName).resolve("Ext").resolve("Form.xml");
            if (!Files.isRegularFile(formXml)) {
                throw new IllegalArgumentException("Base Form.xml not found: " + formXml);
            }
            String formContent = readString(formXml);
            namesToBorrow = extractTopLevelAttributeNamesFromDataPaths(formContent);
            if (namesToBorrow.isEmpty()) {
                out.println("[WARN] Form has no DataPath references — nothing to borrow.");
                return;
            }
            copyTabularSections = false;
        } else {
            namesToBorrow = null; // = «все»
            copyTabularSections = true;
        }

        // 3. Прочитать XML заимствованного объекта расширения
        Path extObjXml = extDir.resolve(dirName).resolve(objName + ".xml");
        if (!Files.isRegularFile(extObjXml)) {
            throw new IllegalArgumentException("Extension object XML not found: " + extObjXml
                    + " (parent should have been borrowed first)");
        }
        String extObjContent = readString(extObjXml);

        // 4. Собрать существующие имена реквизитов и табчастей в расширении
        Set<String> existingAttrs = extractNames(extObjContent, "Attribute");
        Set<String> existingTabular = extractNames(extObjContent, "TabularSection");

        // 5. Извлечь нужные XML-фрагменты из базовой конфигурации
        List<String> attrXmlBlocks = extractElementsByTopLevelName(baseObjContent, "Attribute",
                namesToBorrow, existingAttrs);
        List<String> tabularXmlBlocks = copyTabularSections
                ? extractElementsByTopLevelName(baseObjContent, "TabularSection", null, existingTabular)
                : Collections.emptyList();
        attrXmlBlocks = toAdoptedChildBlocks(attrXmlBlocks);
        tabularXmlBlocks = toAdoptedChildBlocks(tabularXmlBlocks);

        if (attrXmlBlocks.isEmpty() && tabularXmlBlocks.isEmpty()) {
            out.println("[WARN] Nothing to add: all referenced attributes/tabular sections already present.");
            return;
        }

        // 6. Вставить блоки в ChildObjects объекта расширения
        String updated = injectIntoChildObjects(extObjContent, attrXmlBlocks, tabularXmlBlocks);
        writeWithBom(extObjXml, updated);
        out.println("[INFO]   Added " + attrXmlBlocks.size() + " attribute(s), "
                + tabularXmlBlocks.size() + " tabular section(s) → " + extObjXml);
    }

    /** Список имён, извлечённых из data-binding тегов (берётся сегмент после {@code Объект.}). */
    private Set<String> extractTopLevelAttributeNamesFromDataPaths(String formContent) {
        Set<String> names = new LinkedHashSet<>();
        for (String tag : FORM_BINDING_DATA_TAGS) {
            Matcher m = Pattern.compile("<" + tag + ">([^<]+)</" + tag + ">").matcher(formContent);
            while (m.find()) {
                addTopLevelAttributeName(names, m.group(1));
            }
        }
        Matcher fields = Pattern.compile("<Field>[^<]*\\bОбъект\\.(\\w+(?:\\.\\w+)*)</Field>").matcher(formContent);
        while (fields.find()) {
            addTopLevelAttributeName(names, "Объект." + fields.group(1));
        }
        return names;
    }

    private void addTopLevelAttributeName(Set<String> names, String rawPath) {
        String path = rawPath == null ? "" : rawPath.trim();
        if (path.isEmpty()) return;
        int dot = path.indexOf('.');
        String top = dot < 0 ? path : path.substring(0, dot);
        // Пропускаем псевдо-реквизиты формы
        if ("Объект".equalsIgnoreCase(top) || "Object".equalsIgnoreCase(top)
                || "Список".equalsIgnoreCase(top) || "List".equalsIgnoreCase(top)
                || "Запись".equalsIgnoreCase(top) || "Record".equalsIgnoreCase(top)
                || "НаборЗаписей".equalsIgnoreCase(top) || "RecordSet".equalsIgnoreCase(top)
                || "Отчет".equalsIgnoreCase(top) || "Отчёт".equalsIgnoreCase(top)
                || "Report".equalsIgnoreCase(top)) {
            if (dot >= 0) {
                String rest = path.substring(dot + 1);
                int dot2 = rest.indexOf('.');
                String next = dot2 < 0 ? rest : rest.substring(0, dot2);
                if (!next.isEmpty()) names.add(next);
            }
        } else {
            names.add(top);
        }
    }

    /** Имена top-level элементов с тегом {@code <tag>}, имеющих внутри {@code <Name>...</Name>}. */
    private Set<String> extractNames(String xml, String tag) {
        Set<String> names = new LinkedHashSet<>();
        List<String> blocks = extractElementsByTopLevelName(xml, tag, null, Collections.emptySet());
        for (String b : blocks) {
            Matcher m = Pattern.compile("<Name>([^<]+)</Name>").matcher(b);
            if (m.find()) names.add(m.group(1).trim());
        }
        return names;
    }

    /**
     * Найти XML-фрагменты элементов вида {@code <tag>...</tag>}, у которых внутри есть {@code <Name>X</Name>},
     * где X входит в {@code nameFilter} (если {@code nameFilter == null} — берутся все),
     * и X не входит в {@code skipNames}.
     */
    private List<String> extractElementsByTopLevelName(String xml, String tag,
                                                       Set<String> nameFilter,
                                                       Set<String> skipNames) {
        List<String> out = new ArrayList<>();
        Pattern startP = Pattern.compile("<" + Pattern.quote(tag) + "(?:\\s+[^>]*)?>");
        Pattern endP = Pattern.compile("</" + Pattern.quote(tag) + ">");
        Matcher startM = startP.matcher(xml);
        while (startM.find()) {
            int s = startM.start();
            // Find matching end with depth tracking (Attribute не вложен, но защитимся)
            int depth = 1;
            int pos = startM.end();
            int e = -1;
            Pattern openOrClose = Pattern.compile("<(/?" + Pattern.quote(tag) + ")(?:\\s|>|/>)");
            Matcher m2 = openOrClose.matcher(xml);
            m2.region(pos, xml.length());
            while (m2.find()) {
                if (m2.group(1).startsWith("/")) {
                    depth--;
                    if (depth == 0) {
                        e = xml.indexOf('>', m2.start()) + 1;
                        break;
                    }
                } else {
                    depth++;
                }
            }
            if (e < 0) continue;
            String block = xml.substring(s, e);
            Matcher nm = Pattern.compile("<Name>([^<]+)</Name>").matcher(block);
            if (!nm.find()) continue;
            String name = nm.group(1).trim();
            if (skipNames != null && skipNames.contains(name)) continue;
            if (nameFilter != null && !nameFilter.contains(name)) continue;
            out.add(block);
        }
        return out;
    }

    private List<String> toAdoptedChildBlocks(List<String> blocks) {
        List<String> result = new ArrayList<>(blocks.size());
        for (String block : blocks) {
            result.add(toAdoptedChildBlock(block));
        }
        return result;
    }

    private String toAdoptedChildBlock(String block) {
        String sourceUuid = extractUuid(block);
        String result = replaceElementUuid(block, UuidGenerator.generate());

        if (!result.contains("<ObjectBelonging>")) {
            result = insertAfter(result, "<Properties>",
                    "\n\t\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>");
        }
        if (sourceUuid != null && !result.contains("<ExtendedConfigurationObject>")) {
            String extendedRef = "\n\t\t\t\t<ExtendedConfigurationObject>"
                    + sourceUuid + "</ExtendedConfigurationObject>";
            String afterComment = insertAfterFirstMatch(result,
                    Pattern.compile("<Comment\\s*/>|</Comment>"), extendedRef);
            if (afterComment != null) {
                result = afterComment;
            } else {
                String afterName = insertAfterFirstMatch(result, Pattern.compile("</Name>"), extendedRef);
                result = afterName != null ? afterName : insertAfter(result, "<Properties>", extendedRef);
            }
        }
        return result;
    }

    private String extractUuid(String block) {
        Matcher m = Pattern.compile("\\buuid=\"([^\"]+)\"").matcher(block);
        return m.find() ? m.group(1) : null;
    }

    private String replaceElementUuid(String block, String newUuid) {
        Matcher m = Pattern.compile("^<([A-Za-z]+)([^>]*)>").matcher(block);
        if (!m.find()) {
            return block;
        }
        String attrs = m.group(2);
        if (attrs.contains("uuid=\"")) {
            attrs = attrs.replaceFirst("\\s+uuid=\"[^\"]*\"", " uuid=\"" + newUuid + "\"");
        } else {
            attrs = " uuid=\"" + newUuid + "\"" + attrs;
        }
        return "<" + m.group(1) + attrs + ">" + block.substring(m.end());
    }

    private String insertAfter(String value, String marker, String insertion) {
        int pos = value.indexOf(marker);
        if (pos < 0) {
            return value;
        }
        int insertPos = pos + marker.length();
        return value.substring(0, insertPos) + insertion + value.substring(insertPos);
    }

    private String insertAfterFirstMatch(String value, Pattern pattern, String insertion) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return null;
        }
        int insertPos = matcher.end();
        return value.substring(0, insertPos) + insertion + value.substring(insertPos);
    }

    /**
     * Вставить XML-блоки атрибутов и табчастей в {@code <ChildObjects>} объекта расширения.
     * Если {@code <ChildObjects/>} self-closing — раскрываем.
     */
    private String injectIntoChildObjects(String xml, List<String> attrBlocks, List<String> tabularBlocks) {
        // Раскрыть self-closing
        int selfClose = xml.indexOf("<ChildObjects/>");
        if (selfClose >= 0) {
            xml = xml.substring(0, selfClose) + "<ChildObjects>\n\t\t</ChildObjects>"
                    + xml.substring(selfClose + "<ChildObjects/>".length());
        }
        int close = xml.lastIndexOf("</ChildObjects>");
        if (close < 0) {
            // Объект без ChildObjects — добавим перед закрывающим тегом объекта
            int objClose = xml.lastIndexOf("</");
            if (objClose < 0) {
                throw new IllegalStateException("Cannot find insertion point in extension object XML");
            }
            // вставим контейнер перед закрывающим тегом верхнего уровня
            StringBuilder sb = new StringBuilder();
            sb.append("\t\t<ChildObjects>\n");
            for (String b : attrBlocks) sb.append(indent(b, "\t\t\t")).append("\n");
            for (String b : tabularBlocks) sb.append(indent(b, "\t\t\t")).append("\n");
            sb.append("\t\t</ChildObjects>\n");
            return xml.substring(0, objClose) + sb + xml.substring(objClose);
        }
        StringBuilder ins = new StringBuilder();
        for (String b : attrBlocks) {
            ins.append(indent(b, "\t\t\t")).append("\n");
        }
        for (String b : tabularBlocks) {
            ins.append(indent(b, "\t\t\t")).append("\n");
        }
        return xml.substring(0, close) + ins + xml.substring(close);
    }

    /** Добавить указанный префикс к каждой строке. */
    private static String indent(String block, String prefix) {
        StringBuilder sb = new StringBuilder();
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("\n");
            // Не индентируем последнюю пустую строку
            if (i == lines.length - 1 && lines[i].isEmpty()) continue;
            sb.append(prefix).append(lines[i]);
        }
        return sb.toString();
    }

    // ─── extension patch-method ───────────────────────────────────────

    /** Тип BSL-перехватчика. */
    public enum InterceptorType {
        BEFORE, AFTER, INSTEAD, MODIFICATION_AND_CONTROL;

        public static InterceptorType parse(String s) {
            if (s == null) throw new IllegalArgumentException("--type is required");
            String n = s.trim();
            if (n.equalsIgnoreCase("Before")) return BEFORE;
            if (n.equalsIgnoreCase("After")) return AFTER;
            if (n.equalsIgnoreCase("Instead")) return INSTEAD;
            if (n.equalsIgnoreCase("ModificationAndControl")) return MODIFICATION_AND_CONTROL;
            throw new IllegalArgumentException(
                    "Unknown --type '" + s + "'. Valid: Before, After, Instead, ModificationAndControl");
        }
    }

    /** Результат {@link #patchMethod}. */
    public static final class PatchMethodResult {
        public final Path bslFile;
        public final String procedureName;
        public final boolean created;   // файл создан с нуля
        public final boolean appended;  // процедура добавлена в существующий файл
        public final boolean skipped;   // процедура уже была — ничего не делали

        public PatchMethodResult(Path bslFile, String procedureName,
                                 boolean created, boolean appended, boolean skipped) {
            this.bslFile = bslFile;
            this.procedureName = procedureName;
            this.created = created;
            this.appended = appended;
            this.skipped = skipped;
        }
    }

    /**
     * {@code extension patch-method}: создать процедуру-перехватчик в BSL-модуле расширения.
     *
     * @param extPath     путь к каталогу расширения (или к Configuration.xml)
     * @param modulePath  выражение модуля (см. {@link MdoPathResolver#parseModule})
     * @param methodName  имя метода типовой конфигурации
     * @param type        тип перехватчика
     * @param configDir   путь к базовой конфигурации (обязателен для MODIFICATION_AND_CONTROL)
     * @param context     директива контекста (например, "НаСервере"); если null — "НаСервере"
     * @param asFunction  true → сгенерировать функцию вместо процедуры
     */
    public PatchMethodResult patchMethod(Path extPath, String modulePath, String methodName,
                                         InterceptorType type, Path configDir,
                                         String context, boolean asFunction) throws IOException {
        // 1. Валидация аргументов
        if (Files.isRegularFile(extPath)) extPath = extPath.getParent();
        Path extCfgFile = extPath.resolve("Configuration.xml");
        if (!Files.isRegularFile(extCfgFile)) {
            throw new IllegalArgumentException("Extension Configuration.xml not found: " + extCfgFile);
        }
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("--method is required and must be non-empty");
        }
        if (type == InterceptorType.MODIFICATION_AND_CONTROL && configDir == null) {
            throw new IllegalArgumentException("--config is required for ModificationAndControl");
        }
        if (configDir != null && Files.isRegularFile(configDir)) configDir = configDir.getParent();

        // 2. Прочитать NamePrefix
        String namePrefix = ConfigurationXmlReader.readNamePrefix(extCfgFile);
        if (namePrefix == null) {
            throw new IllegalArgumentException("NamePrefix not found in extension Configuration.xml: "
                    + extCfgFile);
        }
        // TASK-171: NamePrefix уже включает разделитель (mcp_, тк_, OPI_), поэтому
        // конкатенируем без добавочного "_" — иначе получалось двойное подчёркивание
        // (mcp__Метод). Конвенция 1С: <Префикс><ИмяМетода>. Эталон Николая (cfe-patch-method.py:169):
        // proc_name = f"{name_prefix}{method_name}". Если префикс почему-то БЕЗ разделителя —
        // добавляем один "_", чтобы не склеить prefixИмя.
        String procName = namePrefix.endsWith("_") ? namePrefix + methodName : namePrefix + "_" + methodName;

        // 3. Разрешить путь к BSL-файлу в расширении
        MdoPathResolver.ParsedModule parsed = MdoPathResolver.parseModule(modulePath);
        Path bslFile = MdoPathResolver.resolveBslPath(extPath, parsed);

        // 4. Предупреждение если объект не заимствован
        Path objXml = MdoPathResolver.objectXmlPath(extPath, parsed);
        if (objXml != null && !Files.isRegularFile(objXml)) {
            out.println("[WARN] Object not borrowed in extension (" + objXml
                    + ") — creating BSL file anyway.");
        }

        // 5. Для MODIFICATION_AND_CONTROL — извлечь тело метода из базовой конфигурации
        BslMethodExtractor.Extracted extracted = null;
        if (type == InterceptorType.MODIFICATION_AND_CONTROL) {
            Path baseBsl = MdoPathResolver.resolveBslPath(configDir, parsed);
            if (!Files.isRegularFile(baseBsl)) {
                throw new IllegalArgumentException("Base BSL module not found: " + baseBsl);
            }
            extracted = BslMethodExtractor.extract(baseBsl, methodName).orElseThrow(() ->
                    new IllegalArgumentException("Method '" + methodName + "' not found in " + baseBsl));
        }

        // 6. Сформировать BSL-блок
        String bslContext = (context == null || context.isBlank()) ? "НаСервере" : context;
        boolean isFunctionEffective = asFunction;
        if (type == InterceptorType.MODIFICATION_AND_CONTROL && extracted != null && extracted.isFunction) {
            isFunctionEffective = true;
        }
        String block = renderInterceptor(type, bslContext, methodName, procName,
                isFunctionEffective, extracted);

        // 7. Гарантировать наличие файла и каталогов
        boolean created = false;
        if (!Files.isRegularFile(bslFile)) {
            Files.createDirectories(bslFile.getParent());
            writeWithBom(bslFile, "");
            created = true;
            createdFiles.add(bslFile.toString());
        }

        // 8. Проверить, нет ли уже процедуры с таким именем
        BslModuleEditor editor = new BslModuleEditor(bslFile);
        if (editor.findProcedure(procName).isPresent() || editor.findFunction(procName).isPresent()) {
            out.println("[WARN] Procedure '" + procName + "' already exists in " + bslFile
                    + " — skipped.");
            return new PatchMethodResult(bslFile, procName, created, false, true);
        }

        editor.findOrCreateMethod(procName, block, null, isFunctionEffective);
        editor.save();
        out.println("[INFO] Added " + (isFunctionEffective ? "function" : "procedure")
                + " '" + procName + "' to " + bslFile);
        return new PatchMethodResult(bslFile, procName, created, true, false);
    }

    /** Сгенерировать BSL-блок перехватчика по шаблону. */
    private String renderInterceptor(InterceptorType type, String context, String methodName,
                                     String procName, boolean asFunction,
                                     BslMethodExtractor.Extracted extracted) throws IOException {
        String tmplName;
        switch (type) {
            case BEFORE -> tmplName = "before.bsl.tmpl";
            case AFTER -> tmplName = "after.bsl.tmpl";
            case INSTEAD -> tmplName = "instead.bsl.tmpl";
            case MODIFICATION_AND_CONTROL -> tmplName = "modification.bsl.tmpl";
            default -> throw new IllegalStateException("Unknown type " + type);
        }
        String tmpl = loadResource("templates/cfe/" + tmplName);

        String keywordBegin = asFunction ? "Функция" : "Процедура";
        String keywordEnd = asFunction ? "КонецФункции" : "КонецПроцедуры";
        String returnLine = asFunction ? "\n\tВозврат Неопределено;" : "";
        String body;
        if (type == InterceptorType.MODIFICATION_AND_CONTROL && extracted != null) {
            StringBuilder bsb = new StringBuilder();
            for (int i = 0; i < extracted.bodyLines.size(); i++) {
                bsb.append(extracted.bodyLines.get(i));
                if (i < extracted.bodyLines.size() - 1) bsb.append("\n");
            }
            body = bsb.toString();
        } else {
            body = "";
        }

        return tmpl
                .replace("${CONTEXT}", context)
                .replace("${METHOD}", methodName)
                .replace("${PROC_NAME}", procName)
                .replace("${KEYWORD_BEGIN}", keywordBegin)
                .replace("${KEYWORD_END}", keywordEnd)
                .replace("${RETURN_LINE}", returnLine)
                .replace("${BODY}", body);
    }

    private static String loadResource(String name) throws IOException {
        try (InputStream is = ExtensionEditor.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) throw new IOException("Resource not found: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

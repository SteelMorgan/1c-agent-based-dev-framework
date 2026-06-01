package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.editor.ConfigEditor;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Генератор объектов метаданных 1С из JSON DSL.
 * Phase 5b: 7 ссылочных типов — Catalog, Document, Enum,
 * ChartOfAccounts, ChartOfCharacteristicTypes, ChartOfCalculationTypes, ExchangePlan.
 * Phase 5c: 4 регистра — InformationRegister, AccumulationRegister,
 * AccountingRegister, CalculationRegister.
 * Phase 5d: 12 оставшихся типов — Constant, DefinedType, CommonModule,
 * ScheduledJob, EventSubscription, Report, DataProcessor,
 * BusinessProcess, Task, DocumentJournal, HTTPService, WebService.
 */
public class MetaWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // --- Russian type name synonyms ---
    private static final Map<String, String> RU_TYPE_NAMES = new LinkedHashMap<>();
    static {
        RU_TYPE_NAMES.put("Справочник", "Catalog");
        RU_TYPE_NAMES.put("Документ", "Document");
        RU_TYPE_NAMES.put("Перечисление", "Enum");
        RU_TYPE_NAMES.put("ПланСчетов", "ChartOfAccounts");
        RU_TYPE_NAMES.put("ПланВидовХарактеристик", "ChartOfCharacteristicTypes");
        RU_TYPE_NAMES.put("ПланВидовРасчёта", "ChartOfCalculationTypes");
        RU_TYPE_NAMES.put("ПланВидовРасчета", "ChartOfCalculationTypes");
        RU_TYPE_NAMES.put("ПланОбмена", "ExchangePlan");
        RU_TYPE_NAMES.put("Константа", "Constant");
        RU_TYPE_NAMES.put("РегистрСведений", "InformationRegister");
        RU_TYPE_NAMES.put("РегистрНакопления", "AccumulationRegister");
        RU_TYPE_NAMES.put("РегистрБухгалтерии", "AccountingRegister");
        RU_TYPE_NAMES.put("РегистрРасчёта", "CalculationRegister");
        RU_TYPE_NAMES.put("РегистрРасчета", "CalculationRegister");
        RU_TYPE_NAMES.put("БизнесПроцесс", "BusinessProcess");
        RU_TYPE_NAMES.put("Задача", "Task");
        RU_TYPE_NAMES.put("Отчёт", "Report");
        RU_TYPE_NAMES.put("Отчет", "Report");
        RU_TYPE_NAMES.put("Обработка", "DataProcessor");
        RU_TYPE_NAMES.put("ОбщийМодуль", "CommonModule");
        RU_TYPE_NAMES.put("РегламентноеЗадание", "ScheduledJob");
        RU_TYPE_NAMES.put("ПодпискаНаСобытие", "EventSubscription");
        RU_TYPE_NAMES.put("HTTPСервис", "HTTPService");
        RU_TYPE_NAMES.put("ВебСервис", "WebService");
        RU_TYPE_NAMES.put("ОпределяемыйТип", "DefinedType");
        RU_TYPE_NAMES.put("ЖурналДокументов", "DocumentJournal");
    }

    // --- Russian DSL type synonyms ---
    private static final Map<String, String> RU_DSL_TYPES = new LinkedHashMap<>();
    static {
        RU_DSL_TYPES.put("Строка", "String");
        RU_DSL_TYPES.put("Число", "Number");
        RU_DSL_TYPES.put("Булево", "Boolean");
        RU_DSL_TYPES.put("Дата", "Date");
        RU_DSL_TYPES.put("ДатаВремя", "DateTime");
        RU_DSL_TYPES.put("СправочникСсылка", "CatalogRef");
        RU_DSL_TYPES.put("ДокументСсылка", "DocumentRef");
        RU_DSL_TYPES.put("ПеречислениеСсылка", "EnumRef");
        RU_DSL_TYPES.put("ПланСчетовСсылка", "ChartOfAccountsRef");
        RU_DSL_TYPES.put("ПланВидовХарактеристикСсылка", "ChartOfCharacteristicTypesRef");
        RU_DSL_TYPES.put("ПланВидовРасчётаСсылка", "ChartOfCalculationTypesRef");
        RU_DSL_TYPES.put("ПланВидовРасчетаСсылка", "ChartOfCalculationTypesRef");
        RU_DSL_TYPES.put("ПланОбменаСсылка", "ExchangePlanRef");
        RU_DSL_TYPES.put("БизнесПроцессСсылка", "BusinessProcessRef");
        RU_DSL_TYPES.put("ЗадачаСсылка", "TaskRef");
        RU_DSL_TYPES.put("ОпределяемыйТип", "DefinedType");
    }

    // Shorthand attribute pattern: "Name: Type | flags"
    private static final Pattern ATTR_SHORT =
            Pattern.compile("^([^:]+?)(?:\\s*:\\s*(.+?))?(?:\\s*\\|\\s*(.+))?$");

    // Type patterns for XML generation
    private static final Pattern STRING_TYPE = Pattern.compile("String(?:\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_TYPE = Pattern.compile("Number\\((\\d+)(?:,(\\d+))?(?:,(nonneg))?\\)", Pattern.CASE_INSENSITIVE);

    // All supported types (Phase 5b-5d)
    private static final Set<String> SUPPORTED_TYPES = Set.of(
            // Phase 5b: Reference types
            "Catalog", "Document", "Enum",
            "ChartOfAccounts", "ChartOfCharacteristicTypes",
            "ChartOfCalculationTypes", "ExchangePlan",
            // Phase 5c: Registers
            "InformationRegister", "AccumulationRegister",
            "AccountingRegister", "CalculationRegister",
            // Phase 5d: Remaining types
            "Constant", "DefinedType", "CommonModule",
            "ScheduledJob", "EventSubscription",
            "Report", "DataProcessor",
            "BusinessProcess", "Task",
            "DocumentJournal", "HTTPService", "WebService");

    /**
     * Compile JSON DSL to XML metadata object.
     *
     * @param jsonPath  path to JSON definition
     * @param outputDir root directory for config dump (e.g., src/); type directory is auto-created
     */
    public void compile(Path jsonPath, Path outputDir) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());
        compileFromNode(root, outputDir);
    }

    /**
     * Compile from pre-parsed JsonNode.
     */
    public void compileFromNode(JsonNode root, Path outputDir) throws IOException {
        String rawType = requireString(root, "type");
        String type = normalizeTypeName(rawType);
        String name = requireString(root, "name");

        TypeDescriptor td = MetadataTypeRegistry.get(type);
        if (td == null) {
            throw new IllegalArgumentException("Unknown metadata type: " + rawType);
        }
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("Type '" + type + "' not yet supported in meta compile. "
                    + "Supported: " + String.join(", ", SUPPORTED_TYPES));
        }

        // Resolve output directory: outputDir/<TypeDirectory>/
        Path typeDir = outputDir.resolve(td.directory());
        Files.createDirectories(typeDir);

        // Версия формата метаданных берётся из Configuration.xml в корне выгрузки
        // (TASK-171 D-6): и объект, и Ext/Predefined.xml ДОЛЖНЫ иметь ту же версию,
        // что и конфигурация, иначе full-load падает «Версия формата ... отличается».
        Path configurationXml = outputDir.resolve("Configuration.xml");
        String formatVersion = ConfigurationXmlReader.readFormatVersion(configurationXml);

        // Generate UUIDs
        String objectUuid = UuidGenerator.generate();

        // Build XML
        String xml = generateXml(root, type, name, td, objectUuid, formatVersion);
        writeWithBom(typeDir.resolve(name + ".xml"), xml);

        // Create directory structure
        createDirStructure(typeDir, name, type, td);

        // Предопределённые элементы (TASK-171 D-1): Ext/Predefined.xml для
        // справочников и планов видов характеристик/счетов/расчёта.
        writePredefinedItems(typeDir, name, type, root, formatVersion);

        // Регистрация в Configuration.xml (TASK-171 D-3): без этого шага build
        // падает «Неизвестное имя типа». Делаем автоматически, если конфиг найден.
        registerInConfiguration(configurationXml, td.xmlElement(), name);
    }

    /**
     * Зарегистрировать объект в {@code <ChildObjects>} {@code Configuration.xml}
     * (TASK-171 D-3). Переиспользует {@link ConfigEditor} с его каноническим
     * порядком типов. Если конфиг не найден — предупреждение, без падения:
     * compile в принципе может вызываться вне корня выгрузки.
     */
    private void registerInConfiguration(Path configurationXml, String xmlElement, String name) {
        if (!Files.isRegularFile(configurationXml)) {
            System.err.println("WARN: Configuration.xml не найден рядом с outputDir ("
                    + configurationXml + ") — объект " + xmlElement + "." + name
                    + " НЕ зарегистрирован. Добавьте вручную: "
                    + "xml-gen config edit Configuration.xml --op add-childObject --value \""
                    + xmlElement + "." + name + "\"");
            return;
        }
        try {
            ConfigEditor editor = new ConfigEditor(configurationXml);
            editor.addChildObject(xmlElement + "." + name);
            editor.save();
        } catch (IOException e) {
            throw new RuntimeException("Не удалось зарегистрировать " + xmlElement + "." + name
                    + " в Configuration.xml: " + e.getMessage(), e);
        }
    }

    // ==================== XML Generation ====================

    private String generateXml(JsonNode root, String type, String name,
                                TypeDescriptor td, String objectUuid, String formatVersion) {
        String synonym = getString(root, "synonym", null);
        if (synonym == null) synonym = camelCaseToWords(name);
        String comment = getString(root, "comment", "");

        StringBuilder sb = new StringBuilder();

        // XML declaration + MetaDataObject root
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\"\n");
        sb.append("\txmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\"\n");
        sb.append("\txmlns:cmi=\"http://v8.1c.ru/8.2/managed-application/cmi\"\n");
        sb.append("\txmlns:ent=\"http://v8.1c.ru/8.1/data/enterprise\"\n");
        sb.append("\txmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\"\n");
        sb.append("\txmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\"\n");
        sb.append("\txmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\"\n");
        sb.append("\txmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\"\n");
        sb.append("\txmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\"\n");
        // TASK-171 D-2: канонический namespace v8.1c.ru, иначе платформа при
        // DESIGNER full-load отвергает объект («Отсутствует внутренняя информация»).
        sb.append("\txmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\"\n");
        sb.append("\txmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\"\n");
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        // TASK-171 D-6: версия формата из Configuration.xml, не хардкод.
        sb.append("\tversion=\"").append(formatVersion).append("\">\n");

        // Type element
        sb.append("\t<").append(td.xmlElement()).append(" uuid=\"").append(objectUuid).append("\">\n");

        // InternalInfo
        writeInternalInfo(sb, type, name, td);

        // Properties
        writeProperties(sb, root, type, name, synonym, comment, td);

        // ChildObjects
        writeChildObjects(sb, root, type, name, td);

        sb.append("\t</").append(td.xmlElement()).append(">\n");
        sb.append("</MetaDataObject>\n");

        return sb.toString();
    }

    // ==================== InternalInfo ====================

    private void writeInternalInfo(StringBuilder sb, String type, String name,
                                    TypeDescriptor td) {
        // TASK-171 D-4: блок <InternalInfo> существует только у типов с
        // GeneratedType-категориями (ссылочные, регистры, Constant, DefinedType)
        // ИЛИ у ExchangePlan (ThisNode). У CommonModule/ScheduledJob/EventSubscription/
        // HTTPService/WebService категорий нет — пустой <InternalInfo></InternalInfo>
        // платформа отвергает. Не выводим блок вовсе.
        if (td.categories().isEmpty() && !"ExchangePlan".equals(type)) {
            return;
        }

        sb.append("\t\t<InternalInfo>\n");

        // ExchangePlan: ThisNode
        if ("ExchangePlan".equals(type)) {
            sb.append("\t\t\t<xr:ThisNode>").append(UuidGenerator.generate()).append("</xr:ThisNode>\n");
        }

        // GeneratedType entries
        for (String category : td.categories()) {
            String gtName = resolveGeneratedTypeName(type, name, category);
            sb.append("\t\t\t<xr:GeneratedType name=\"").append(esc(gtName))
                    .append("\" category=\"").append(category).append("\">\n");
            sb.append("\t\t\t\t<xr:TypeId>").append(UuidGenerator.generate()).append("</xr:TypeId>\n");
            sb.append("\t\t\t\t<xr:ValueId>").append(UuidGenerator.generate()).append("</xr:ValueId>\n");
            sb.append("\t\t\t</xr:GeneratedType>\n");
        }

        sb.append("\t\t</InternalInfo>\n");
    }

    private String resolveGeneratedTypeName(String type, String name, String category) {
        // Pattern: {TypeCategory}.{ObjectName}
        // e.g., CatalogObject.Номенклатура, CatalogRef.Номенклатура
        return switch (category) {
            case "Object" -> type + "Object." + name;
            case "Ref" -> type + "Ref." + name;
            case "Selection" -> type + "Selection." + name;
            case "List" -> type + "List." + name;
            case "Manager" -> type + "Manager." + name;
            case "Record" -> type + "Record." + name;
            case "RecordSet" -> type + "RecordSet." + name;
            case "RecordKey" -> type + "RecordKey." + name;
            case "RecordManager" -> type + "RecordManager." + name;
            case "ValueManager" -> type + "ValueManager." + name;
            case "ValueKey" -> type + "ValueKey." + name;
            case "Characteristic" -> type + "Characteristic." + name;
            case "DefinedType" -> "DefinedType." + name;
            case "RoutePointRef" -> type + "RoutePointRef." + name;
            // ChartOfCalculationTypes specifics
            case "DisplacingCalculationTypes" -> type + "DisplacingCalculationTypes." + name;
            case "DisplacingCalculationTypesRow" -> type + "DisplacingCalculationTypesRow." + name;
            case "BaseCalculationTypes" -> type + "BaseCalculationTypes." + name;
            case "BaseCalculationTypesRow" -> type + "BaseCalculationTypesRow." + name;
            case "LeadingCalculationTypes" -> type + "LeadingCalculationTypes." + name;
            case "LeadingCalculationTypesRow" -> type + "LeadingCalculationTypesRow." + name;
            // AccountingRegister
            case "ExtDimensions" -> type + "ExtDimensions." + name;
            default -> type + category + "." + name;
        };
    }

    // ==================== Properties ====================

    private void writeProperties(StringBuilder sb, JsonNode root, String type, String name,
                                  String synonym, String comment, TypeDescriptor td) {
        sb.append("\t\t<Properties>\n");

        // Common properties
        writeElement(sb, 3, "Name", name);
        writeSynonym(sb, 3, synonym);
        writeComment(sb, 3, comment);

        // Type-specific
        switch (type) {
            case "Catalog" -> writeCatalogProperties(sb, root);
            case "Document" -> writeDocumentProperties(sb, root);
            case "Enum" -> writeEnumProperties(sb, root);
            case "ChartOfAccounts" -> writeChartOfAccountsProperties(sb, root);
            case "ChartOfCharacteristicTypes" -> writeChartOfCharacteristicTypesProperties(sb, root);
            case "ChartOfCalculationTypes" -> writeChartOfCalculationTypesProperties(sb, root);
            case "ExchangePlan" -> writeExchangePlanProperties(sb, root);
            case "InformationRegister" -> writeInformationRegisterProperties(sb, root);
            case "AccumulationRegister" -> writeAccumulationRegisterProperties(sb, root);
            case "AccountingRegister" -> writeAccountingRegisterProperties(sb, root);
            case "CalculationRegister" -> writeCalculationRegisterProperties(sb, root);
            case "Constant" -> writeConstantProperties(sb, root);
            case "DefinedType" -> writeDefinedTypeProperties(sb, root);
            case "CommonModule" -> writeCommonModuleProperties(sb, root);
            case "ScheduledJob" -> writeScheduledJobProperties(sb, root, name);
            case "EventSubscription" -> writeEventSubscriptionProperties(sb, root);
            case "Report" -> writeReportProperties(sb, root);
            case "DataProcessor" -> writeDataProcessorProperties(sb, root);
            case "BusinessProcess" -> writeBusinessProcessProperties(sb, root);
            case "Task" -> writeTaskProperties(sb, root);
            case "DocumentJournal" -> writeDocumentJournalProperties(sb, root);
            case "HTTPService" -> writeHTTPServiceProperties(sb, root, name);
            case "WebService" -> writeWebServiceProperties(sb, root);
        }

        sb.append("\t\t</Properties>\n");
    }

    private void writeCatalogProperties(StringBuilder sb, JsonNode root) {
        // Hierarchy
        boolean hierarchical = getBool(root, "hierarchical", false);
        writeElement(sb, 3, "Hierarchical", String.valueOf(hierarchical));
        if (hierarchical) {
            writeElement(sb, 3, "HierarchyType",
                    getString(root, "hierarchyType", "HierarchyFoldersAndItems"));
            writeElement(sb, 3, "LimitLevelCount",
                    String.valueOf(getBool(root, "limitLevelCount", false)));
            writeElement(sb, 3, "LevelCount", String.valueOf(getInt(root, "levelCount", 2)));
            writeElement(sb, 3, "FoldersOnTop",
                    String.valueOf(getBool(root, "foldersOnTop", true)));
        }

        // Code & description
        writeElement(sb, 3, "CodeLength", String.valueOf(getInt(root, "codeLength", 9)));
        writeElement(sb, 3, "CodeType", getString(root, "codeType", "String"));
        writeElement(sb, 3, "CodeAllowedLength", getString(root, "codeAllowedLength", "Variable"));
        writeElement(sb, 3, "DescriptionLength", String.valueOf(getInt(root, "descriptionLength", 25)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", false)));
        writeElement(sb, 3, "CodeSeries", getString(root, "codeSeries", "WholeCatalog"));
        writeElement(sb, 3, "DefaultPresentation",
                getString(root, "defaultPresentation", "AsDescription"));
        writeElement(sb, 3, "PredefinedDataUpdate",
                getString(root, "predefinedDataUpdate", "Auto"));

        // Owners + subordination
        List<String> owners = getStringList(root, "owners");
        if (owners.isEmpty()) {
            writeEmptyElement(sb, 3, "Owners");
        } else {
            sb.append(indent(3)).append("<Owners>\n");
            for (String owner : owners) {
                sb.append(indent(4)).append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
                        .append(esc(owner)).append("</xr:Item>\n");
            }
            sb.append(indent(3)).append("</Owners>\n");
        }
        if (!owners.isEmpty() || root.has("subordinationUse")) {
            writeElement(sb, 3, "SubordinationUse",
                    getString(root, "subordinationUse", "ToItems"));
        }

        // Common behavior
        writeElement(sb, 3, "EditType", getString(root, "editType", "InDialog"));
        writeElement(sb, 3, "QuickChoice", String.valueOf(getBool(root, "quickChoice", true)));
        writeElement(sb, 3, "ChoiceMode", getString(root, "choiceMode", "BothWays"));
        writeBehaviorProperties(sb, root);
    }

    private void writeDocumentProperties(StringBuilder sb, JsonNode root) {
        // Numbering
        writeEmptyElement(sb, 3, "Numerator");
        writeElement(sb, 3, "NumberType", getString(root, "numberType", "String"));
        writeElement(sb, 3, "NumberLength", String.valueOf(getInt(root, "numberLength", 11)));
        writeElement(sb, 3, "NumberAllowedLength", getString(root, "numberAllowedLength", "Variable"));
        writeElement(sb, 3, "NumberPeriodicity", getString(root, "numberPeriodicity", "Year"));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", true)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));

        // Posting
        writeElement(sb, 3, "Posting", getString(root, "posting", "Allow"));
        writeElement(sb, 3, "RealTimePosting", getString(root, "realTimePosting", "Deny"));
        writeElement(sb, 3, "PostInPrivilegedMode",
                String.valueOf(getBool(root, "postInPrivilegedMode", true)));
        writeElement(sb, 3, "UnpostInPrivilegedMode",
                String.valueOf(getBool(root, "unpostInPrivilegedMode", true)));

        // Register records
        List<String> registerRecords = getStringList(root, "registerRecords");
        if (registerRecords.isEmpty()) {
            writeEmptyElement(sb, 3, "RegisterRecords");
        } else {
            sb.append(indent(3)).append("<RegisterRecords>\n");
            for (String rr : registerRecords) {
                sb.append(indent(4)).append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
                        .append(esc(rr)).append("</xr:Item>\n");
            }
            sb.append(indent(3)).append("</RegisterRecords>\n");
        }
        writeElement(sb, 3, "RegisterRecordsDeletion",
                getString(root, "registerRecordsDeletion", "AutoDelete"));
        writeElement(sb, 3, "RegisterRecordsWritingOnPost",
                getString(root, "registerRecordsWritingOnPost", "WriteModified"));
        writeElement(sb, 3, "SequenceFilling", getString(root, "sequenceFilling", "AutoFill"));

        writeBehaviorProperties(sb, root);
    }

    private void writeEnumProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "QuickChoice", "true");
        writeElement(sb, 3, "ChoiceMode", "BothWays");
    }

    private void writeChartOfAccountsProperties(StringBuilder sb, JsonNode root) {
        // ExtDimensionTypes
        String edt = getString(root, "extDimensionTypes", "");
        if (edt.isEmpty()) {
            writeEmptyElement(sb, 3, "ExtDimensionTypes");
        } else {
            writeElement(sb, 3, "ExtDimensionTypes", edt);
        }
        writeElement(sb, 3, "MaxExtDimensionCount",
                String.valueOf(getInt(root, "maxExtDimensionCount", 3)));

        // Code
        String codeMask = getString(root, "codeMask", "");
        if (!codeMask.isEmpty()) {
            writeElement(sb, 3, "CodeMask", codeMask);
        }
        writeElement(sb, 3, "CodeLength", String.valueOf(getInt(root, "codeLength", 8)));
        writeElement(sb, 3, "DescriptionLength", String.valueOf(getInt(root, "descriptionLength", 120)));
        writeElement(sb, 3, "CodeSeries", getString(root, "codeSeries", "WholeChartOfAccounts"));
        writeElement(sb, 3, "AutoOrderByCode",
                String.valueOf(getBool(root, "autoOrderByCode", true)));
        writeElement(sb, 3, "OrderLength", String.valueOf(getInt(root, "orderLength", 5)));

        boolean hierarchical = getBool(root, "hierarchical", false);
        writeElement(sb, 3, "Hierarchical", String.valueOf(hierarchical));

        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", false)));

        writeBehaviorProperties(sb, root);
    }

    private void writeChartOfCharacteristicTypesProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "CodeLength", String.valueOf(getInt(root, "codeLength", 9)));
        writeElement(sb, 3, "CodeType", getString(root, "codeType", "String"));
        writeElement(sb, 3, "CodeAllowedLength", getString(root, "codeAllowedLength", "Variable"));
        writeElement(sb, 3, "DescriptionLength", String.valueOf(getInt(root, "descriptionLength", 25)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", false)));

        // CharacteristicExtValues
        String charExtVal = getString(root, "characteristicExtValues", "");
        if (!charExtVal.isEmpty()) {
            writeElement(sb, 3, "CharacteristicExtValues", charExtVal);
        }

        // Type (value types for characteristics)
        List<String> valueTypes = getValueTypesList(root);
        if (valueTypes.isEmpty()) {
            // Default: Boolean, String(100), Number(15,2), DateTime
            valueTypes = List.of("Boolean", "String(100)", "Number(15,2)", "DateTime");
        }
        writeTypeComposite(sb, 3, valueTypes);

        boolean hierarchical = getBool(root, "hierarchical", false);
        writeElement(sb, 3, "Hierarchical", String.valueOf(hierarchical));

        writeElement(sb, 3, "EditType", "InDialog");
        writeElement(sb, 3, "QuickChoice", "true");
        writeElement(sb, 3, "ChoiceMode", "BothWays");
        writeBehaviorProperties(sb, root);
    }

    private void writeChartOfCalculationTypesProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "CodeLength", String.valueOf(getInt(root, "codeLength", 9)));
        writeElement(sb, 3, "CodeType", getString(root, "codeType", "String"));
        writeElement(sb, 3, "CodeAllowedLength", getString(root, "codeAllowedLength", "Variable"));
        writeElement(sb, 3, "DescriptionLength", String.valueOf(getInt(root, "descriptionLength", 25)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", false)));

        writeElement(sb, 3, "DependenceOnCalculationTypes",
                getString(root, "dependenceOnCalculationTypes", "NotUsed"));

        // BaseCalculationTypes
        List<String> baseCT = getStringList(root, "baseCalculationTypes");
        if (baseCT.isEmpty()) {
            writeEmptyElement(sb, 3, "BaseCalculationTypes");
        } else {
            sb.append(indent(3)).append("<BaseCalculationTypes>\n");
            for (String ct : baseCT) {
                sb.append(indent(4)).append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
                        .append(esc(ct)).append("</xr:Item>\n");
            }
            sb.append(indent(3)).append("</BaseCalculationTypes>\n");
        }

        writeElement(sb, 3, "ActionPeriodUse",
                String.valueOf(getBool(root, "actionPeriodUse", false)));

        writeElement(sb, 3, "EditType", "InDialog");
        writeElement(sb, 3, "QuickChoice", "true");
        writeElement(sb, 3, "ChoiceMode", "BothWays");
        writeBehaviorProperties(sb, root);
    }

    private void writeExchangePlanProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "CodeLength", String.valueOf(getInt(root, "codeLength", 9)));
        writeElement(sb, 3, "CodeAllowedLength", getString(root, "codeAllowedLength", "Variable"));
        writeElement(sb, 3, "DescriptionLength", String.valueOf(getInt(root, "descriptionLength", 100)));
        writeElement(sb, 3, "DistributedInfoBase",
                String.valueOf(getBool(root, "distributedInfoBase", false)));
        writeElement(sb, 3, "IncludeConfigurationExtensions",
                String.valueOf(getBool(root, "includeConfigurationExtensions", false)));

        writeBehaviorProperties(sb, root);
    }

    private void writeInformationRegisterProperties(StringBuilder sb, JsonNode root) {
        String periodicity = getString(root, "periodicity", "Nonperiodical");
        writeElement(sb, 3, "InformationRegisterPeriodicity", periodicity);
        writeElement(sb, 3, "WriteMode", getString(root, "writeMode", "Independent"));

        // MainFilterOnPeriod: auto = true if periodic
        boolean isPeriodic = !"Nonperiodical".equals(periodicity);
        writeElement(sb, 3, "MainFilterOnPeriod",
                String.valueOf(getBool(root, "mainFilterOnPeriod", isPeriodic)));

        writeElement(sb, 3, "EnableTotalsSliceFirst",
                String.valueOf(getBool(root, "enableTotalsSliceFirst", false)));
        writeElement(sb, 3, "EnableTotalsSliceLast",
                String.valueOf(getBool(root, "enableTotalsSliceLast", false)));

        writeBehaviorProperties(sb, root);
    }

    private void writeAccumulationRegisterProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "RegisterType", getString(root, "registerType", "Balances"));
        writeElement(sb, 3, "EnableTotalsSplitting",
                String.valueOf(getBool(root, "enableTotalsSplitting", true)));

        writeBehaviorProperties(sb, root);
    }

    private void writeAccountingRegisterProperties(StringBuilder sb, JsonNode root) {
        String coa = getString(root, "chartOfAccounts", "");
        if (coa.isEmpty()) {
            throw new IllegalArgumentException(
                    "AccountingRegister requires 'chartOfAccounts' property");
        }
        writeElement(sb, 3, "ChartOfAccounts", coa);
        writeElement(sb, 3, "Correspondence",
                String.valueOf(getBool(root, "correspondence", false)));
        writeElement(sb, 3, "PeriodAdjustmentLength",
                String.valueOf(getInt(root, "periodAdjustmentLength", 0)));

        writeBehaviorProperties(sb, root);
    }

    private void writeCalculationRegisterProperties(StringBuilder sb, JsonNode root) {
        String coct = getString(root, "chartOfCalculationTypes", "");
        if (coct.isEmpty()) {
            throw new IllegalArgumentException(
                    "CalculationRegister requires 'chartOfCalculationTypes' property");
        }
        writeElement(sb, 3, "ChartOfCalculationTypes", coct);
        writeElement(sb, 3, "Periodicity", getString(root, "periodicity", "Month"));
        writeElement(sb, 3, "ActionPeriod",
                String.valueOf(getBool(root, "actionPeriod", false)));
        writeElement(sb, 3, "BasePeriod",
                String.valueOf(getBool(root, "basePeriod", false)));

        // Schedule (optional)
        String schedule = getString(root, "schedule", "");
        if (!schedule.isEmpty()) {
            writeElement(sb, 3, "Schedule", schedule);
            String scheduleValue = getString(root, "scheduleValue", "");
            if (!scheduleValue.isEmpty()) writeElement(sb, 3, "ScheduleValue", scheduleValue);
            String scheduleDate = getString(root, "scheduleDate", "");
            if (!scheduleDate.isEmpty()) writeElement(sb, 3, "ScheduleDate", scheduleDate);
        }

        writeBehaviorProperties(sb, root);
    }

    private void writeBehaviorProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "DataLockControlMode",
                getString(root, "dataLockControlMode", "Automatic"));
        writeElement(sb, 3, "FullTextSearch", getString(root, "fullTextSearch", "Use"));
    }

    // ==================== Phase 5d Property Writers ====================

    private void writeConstantProperties(StringBuilder sb, JsonNode root) {
        // Constant value type — support split-form: "valueType":"String","length":100
        String resolvedType = resolveValueType(root);
        writeTypeElement(sb, 3, resolvedType);

        writeElement(sb, 3, "PasswordMode", "false");
        writeEmptyElement(sb, 3, "Format");
        writeEmptyElement(sb, 3, "EditFormat");
        writeEmptyElement(sb, 3, "ToolTip");
        writeElement(sb, 3, "MarkNegatives", "false");
        writeEmptyElement(sb, 3, "Mask");
        writeElement(sb, 3, "MultiLine", "false");
        writeElement(sb, 3, "ExtendedEdit", "false");
        sb.append(indent(3)).append("<MinValue xsi:nil=\"true\"/>\n");
        sb.append(indent(3)).append("<MaxValue xsi:nil=\"true\"/>\n");
        writeElement(sb, 3, "FillChecking", "DontCheck");
        writeEmptyElement(sb, 3, "ChoiceParameterLinks");
        writeEmptyElement(sb, 3, "ChoiceParameters");
        writeElement(sb, 3, "QuickChoice", "Auto");
        writeEmptyElement(sb, 3, "ChoiceForm");
        writeEmptyElement(sb, 3, "LinkByType");
        writeElement(sb, 3, "ChoiceHistoryOnInput", "Auto");
        writeElement(sb, 3, "DataLockControlMode",
                getString(root, "dataLockControlMode", "Automatic"));
    }

    /** Resolve valueType/valueTypes with split-form support (length/precision/nonneg). */
    private String resolveValueType(JsonNode root) {
        // Check array form first
        List<String> valueTypes = getValueTypesList(root);
        if (valueTypes.size() > 1) {
            // Composite — for now just use the first type (Constant has single type)
            return normalizeDslType(valueTypes.get(0));
        }

        String type = valueTypes.isEmpty() ? "String(10)" : valueTypes.get(0);
        type = normalizeDslType(type);

        // Handle split-form: "valueType":"String","length":100
        if (!type.contains("(")) {
            if ("String".equalsIgnoreCase(type) && root.has("length")) {
                type = "String(" + root.get("length").asInt() + ")";
            } else if ("Number".equalsIgnoreCase(type) && root.has("length")) {
                int len = root.get("length").asInt();
                int prec = root.has("precision") ? root.get("precision").asInt() : 0;
                boolean nonneg = getBool(root, "nonneg", false);
                type = "Number(" + len + "," + prec + (nonneg ? ",nonneg" : "") + ")";
            }
        }
        return type;
    }

    private void writeDefinedTypeProperties(StringBuilder sb, JsonNode root) {
        List<String> valueTypes = getValueTypesList(root);
        if (valueTypes.isEmpty()) {
            throw new IllegalArgumentException(
                    "DefinedType requires 'valueTypes' or 'valueType' property");
        }
        writeTypeComposite(sb, 3, valueTypes);
    }

    private void writeCommonModuleProperties(StringBuilder sb, JsonNode root) {
        String context = getString(root, "context", null);
        boolean server = getBool(root, "server", false);
        boolean serverCall = getBool(root, "serverCall", false);
        boolean client = getBool(root, "clientManagedApplication", false);
        boolean clientOrdinary = getBool(root, "clientOrdinaryApplication", false);
        boolean external = getBool(root, "externalConnection", false);

        // Context shortcuts
        if (context != null) {
            switch (context) {
                case "server" -> { server = true; serverCall = true; }
                case "client" -> client = true;
                case "serverClient" -> { server = true; client = true; }
            }
        }

        writeElement(sb, 3, "Global", String.valueOf(getBool(root, "global", false)));
        writeElement(sb, 3, "ClientManagedApplication", String.valueOf(client));
        writeElement(sb, 3, "Server", String.valueOf(server));
        writeElement(sb, 3, "ExternalConnection", String.valueOf(external));
        writeElement(sb, 3, "ClientOrdinaryApplication", String.valueOf(clientOrdinary));
        writeElement(sb, 3, "ServerCall", String.valueOf(serverCall));
        writeElement(sb, 3, "Privileged", String.valueOf(getBool(root, "privileged", false)));
        writeElement(sb, 3, "ReturnValuesReuse",
                getString(root, "returnValuesReuse", "DontUse"));
    }

    private void writeScheduledJobProperties(StringBuilder sb, JsonNode root, String objectName) {
        String methodName = getString(root, "methodName", "");
        if (!methodName.isEmpty() && !methodName.startsWith("CommonModule.")) {
            methodName = "CommonModule." + methodName;
        }
        writeElement(sb, 3, "MethodName", methodName);

        String description = getString(root, "description",
                getString(root, "synonym", camelCaseToWords(objectName)));
        writeElement(sb, 3, "Description", description);
        writeElement(sb, 3, "Key", getString(root, "key", ""));
        writeElement(sb, 3, "Use", String.valueOf(getBool(root, "use", false)));
        writeElement(sb, 3, "Predefined", String.valueOf(getBool(root, "predefined", false)));
        writeElement(sb, 3, "RestartCountOnFailure",
                String.valueOf(getInt(root, "restartCountOnFailure", 3)));
        writeElement(sb, 3, "RestartIntervalOnFailure",
                String.valueOf(getInt(root, "restartIntervalOnFailure", 10)));
    }

    private void writeEventSubscriptionProperties(StringBuilder sb, JsonNode root) {
        // Source (array of types)
        List<String> source = getStringList(root, "source");
        if (source.isEmpty()) {
            writeEmptyElement(sb, 3, "Source");
        } else {
            sb.append(indent(3)).append("<Source>\n");
            for (String s : source) {
                String normalized = normalizeDslType(s);
                String xmlType = normalized.startsWith("cfg:") ? normalized : "cfg:" + normalized;
                sb.append(indent(4)).append("<v8:Type>").append(esc(xmlType)).append("</v8:Type>\n");
            }
            sb.append(indent(3)).append("</Source>\n");
        }

        writeElement(sb, 3, "Event", getString(root, "event", "BeforeWrite"));

        String handler = getString(root, "handler", "");
        if (!handler.isEmpty() && !handler.startsWith("CommonModule.")) {
            handler = "CommonModule." + handler;
        }
        writeElement(sb, 3, "Handler", handler);
    }

    private void writeReportProperties(StringBuilder sb, JsonNode root) {
        String defaultForm = getString(root, "defaultForm", "");
        if (!defaultForm.isEmpty()) writeElement(sb, 3, "DefaultForm", defaultForm);
        String auxForm = getString(root, "auxiliaryForm", "");
        if (!auxForm.isEmpty()) writeElement(sb, 3, "AuxiliaryForm", auxForm);
        String mainDCS = getString(root, "mainDataCompositionSchema", "");
        if (!mainDCS.isEmpty()) writeElement(sb, 3, "MainDataCompositionSchema", mainDCS);
        String defSettings = getString(root, "defaultSettingsForm", "");
        if (!defSettings.isEmpty()) writeElement(sb, 3, "DefaultSettingsForm", defSettings);
        String auxSettings = getString(root, "auxiliarySettingsForm", "");
        if (!auxSettings.isEmpty()) writeElement(sb, 3, "AuxiliarySettingsForm", auxSettings);
        String defVariant = getString(root, "defaultVariantForm", "");
        if (!defVariant.isEmpty()) writeElement(sb, 3, "DefaultVariantForm", defVariant);
    }

    private void writeDataProcessorProperties(StringBuilder sb, JsonNode root) {
        String defaultForm = getString(root, "defaultForm", "");
        if (!defaultForm.isEmpty()) writeElement(sb, 3, "DefaultForm", defaultForm);
        String auxForm = getString(root, "auxiliaryForm", "");
        if (!auxForm.isEmpty()) writeElement(sb, 3, "AuxiliaryForm", auxForm);
    }

    private void writeBusinessProcessProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "EditType", getString(root, "editType", "InDialog"));
        writeElement(sb, 3, "NumberType", getString(root, "numberType", "String"));
        writeElement(sb, 3, "NumberLength", String.valueOf(getInt(root, "numberLength", 11)));
        writeElement(sb, 3, "NumberAllowedLength",
                getString(root, "numberAllowedLength", "Variable"));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", true)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));

        String task = getString(root, "task", "");
        if (task.isEmpty()) {
            writeEmptyElement(sb, 3, "Task");
        } else {
            writeElement(sb, 3, "Task", task);
        }

        writeBehaviorProperties(sb, root);
    }

    private void writeTaskProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "NumberType", getString(root, "numberType", "String"));
        writeElement(sb, 3, "NumberLength", String.valueOf(getInt(root, "numberLength", 14)));
        writeElement(sb, 3, "NumberAllowedLength",
                getString(root, "numberAllowedLength", "Variable"));
        writeElement(sb, 3, "CheckUnique", String.valueOf(getBool(root, "checkUnique", true)));
        writeElement(sb, 3, "Autonumbering", String.valueOf(getBool(root, "autonumbering", true)));
        writeElement(sb, 3, "TaskNumberAutoPrefix",
                getString(root, "taskNumberAutoPrefix", "BusinessProcessNumber"));
        writeElement(sb, 3, "DescriptionLength",
                String.valueOf(getInt(root, "descriptionLength", 150)));

        String addressing = getString(root, "addressing", "");
        if (!addressing.isEmpty()) writeElement(sb, 3, "Addressing", addressing);
        String mainAddr = getString(root, "mainAddressingAttribute", "");
        if (!mainAddr.isEmpty()) writeElement(sb, 3, "MainAddressingAttribute", mainAddr);
        String currentPerformer = getString(root, "currentPerformer", "");
        if (!currentPerformer.isEmpty()) writeElement(sb, 3, "CurrentPerformer", currentPerformer);

        writeBehaviorProperties(sb, root);
    }

    private void writeDocumentJournalProperties(StringBuilder sb, JsonNode root) {
        String defaultForm = getString(root, "defaultForm", "");
        if (!defaultForm.isEmpty()) writeElement(sb, 3, "DefaultForm", defaultForm);
        String auxForm = getString(root, "auxiliaryForm", "");
        if (!auxForm.isEmpty()) writeElement(sb, 3, "AuxiliaryForm", auxForm);

        // RegisteredDocuments
        List<String> regDocs = getStringList(root, "registeredDocuments");
        if (regDocs.isEmpty()) {
            writeEmptyElement(sb, 3, "RegisteredDocuments");
        } else {
            sb.append(indent(3)).append("<RegisteredDocuments>\n");
            for (String doc : regDocs) {
                sb.append(indent(4)).append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
                        .append(esc(doc)).append("</xr:Item>\n");
            }
            sb.append(indent(3)).append("</RegisteredDocuments>\n");
        }
    }

    private void writeHTTPServiceProperties(StringBuilder sb, JsonNode root, String objectName) {
        writeElement(sb, 3, "RootURL", getString(root, "rootURL", objectName.toLowerCase()));
        writeElement(sb, 3, "ReuseSessions",
                getString(root, "reuseSessions", "DontUse"));
        writeElement(sb, 3, "SessionMaxAge",
                String.valueOf(getInt(root, "sessionMaxAge", 20)));
    }

    private void writeWebServiceProperties(StringBuilder sb, JsonNode root) {
        writeElement(sb, 3, "Namespace", getString(root, "namespace", ""));
        String xdto = getString(root, "xdtoPackages", "");
        if (!xdto.isEmpty()) writeElement(sb, 3, "XDTOPackages", xdto);
        writeElement(sb, 3, "ReuseSessions",
                getString(root, "reuseSessions", "DontUse"));
        writeElement(sb, 3, "SessionMaxAge",
                String.valueOf(getInt(root, "sessionMaxAge", 20)));
    }

    // ==================== ChildObjects ====================

    private void writeChildObjects(StringBuilder sb, JsonNode root, String type, String name,
                                    TypeDescriptor td) {
        // TASK-171 D-4: блок <ChildObjects> (даже пустой <ChildObjects/>) существует
        // только у типов, которые в принципе могут иметь дочерние объекты. У
        // CommonModule/ScheduledJob/EventSubscription/Constant/DefinedType их нет —
        // пустой <ChildObjects/> платформа отвергает. Не выводим блок вовсе.
        if (td.childTypes().isEmpty()) {
            return;
        }

        boolean hasChildren = false;

        // Check if any ChildObjects content will be written
        boolean hasDimensions = root.has("dimensions") && root.get("dimensions").size() > 0;
        boolean hasResources = root.has("resources") && root.get("resources").size() > 0;
        boolean hasAttributes = root.has("attributes") && root.get("attributes").size() > 0;
        boolean hasTS = root.has("tabularSections") && root.get("tabularSections").size() > 0;
        // TASK-171 D-5: значения перечисления принимаем по ключу "values" ИЛИ
        // алиасу "enumValues" (агенты использовали enumValues — ключ молча игнорился).
        JsonNode enumValuesNode = enumValuesNode(root);
        boolean hasEnumValues = "Enum".equals(type) && enumValuesNode != null && enumValuesNode.size() > 0;
        boolean hasAccountingFlags = root.has("accountingFlags") && root.get("accountingFlags").size() > 0;
        boolean hasExtDimFlags = root.has("extDimensionAccountingFlags")
                && root.get("extDimensionAccountingFlags").size() > 0;
        boolean hasColumns = root.has("columns") && root.get("columns").size() > 0;
        boolean hasUrlTemplates = root.has("urlTemplates") && root.get("urlTemplates").size() > 0;
        boolean hasOperations = root.has("operations") && root.get("operations").size() > 0;
        boolean hasAddrAttrs = root.has("addressingAttributes")
                && root.get("addressingAttributes").size() > 0;

        hasChildren = hasDimensions || hasResources || hasAttributes || hasTS
                || hasEnumValues || hasAccountingFlags || hasExtDimFlags
                || hasColumns || hasUrlTemplates || hasOperations || hasAddrAttrs;

        if (!hasChildren) {
            sb.append("\t\t<ChildObjects/>\n");
            return;
        }

        sb.append("\t\t<ChildObjects>\n");

        // Dimensions (registers)
        if (hasDimensions) {
            writeDimensions(sb, root.get("dimensions"), type, name);
        }

        // Resources (registers)
        if (hasResources) {
            writeResources(sb, root.get("resources"), type, name);
        }

        // Attributes
        if (hasAttributes) {
            writeAttributes(sb, root.get("attributes"), type, name);
        }

        // TabularSections
        if (hasTS) {
            writeTabularSections(sb, root.get("tabularSections"), type, name);
        }

        // EnumValues
        if (hasEnumValues) {
            writeEnumValues(sb, enumValuesNode, name);
        }

        // AccountingFlags (ChartOfAccounts only)
        if (hasAccountingFlags) {
            writeAccountingFlags(sb, root.get("accountingFlags"), "AccountingFlag");
        }

        // ExtDimensionAccountingFlags (ChartOfAccounts only)
        if (hasExtDimFlags) {
            writeAccountingFlags(sb, root.get("extDimensionAccountingFlags"), "ExtDimensionAccountingFlag");
        }

        // Columns (DocumentJournal)
        if (hasColumns) {
            writeColumns(sb, root.get("columns"));
        }

        // URLTemplates (HTTPService)
        if (hasUrlTemplates) {
            writeUrlTemplates(sb, root.get("urlTemplates"));
        }

        // Operations (WebService)
        if (hasOperations) {
            writeOperations(sb, root.get("operations"));
        }

        // AddressingAttributes (Task)
        if (hasAddrAttrs) {
            writeAddressingAttributes(sb, root.get("addressingAttributes"));
        }

        sb.append("\t\t</ChildObjects>\n");
    }

    // ==================== Dimension Writer (Registers) ====================

    private void writeDimensions(StringBuilder sb, JsonNode dimsNode, String type, String objectName) {
        for (JsonNode dimNode : dimsNode) {
            AttrDef dim = parseAttrDef(dimNode);
            String uuid = UuidGenerator.generate();

            sb.append(indent(3)).append("<Dimension uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");

            writeElement(sb, 5, "Name", dim.name);
            writeSynonym(sb, 5, dim.synonym != null ? dim.synonym : camelCaseToWords(dim.name));
            writeComment(sb, 5, dim.comment != null ? dim.comment : "");

            String dimType = applyNonnegFlag(dim.type, dim.flags);
            writeTypeElement(sb, 5, dimType);

            writeElement(sb, 5, "PasswordMode", "false");
            writeEmptyElement(sb, 5, "Format");
            writeEmptyElement(sb, 5, "EditFormat");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "MarkNegatives", "false");
            writeEmptyElement(sb, 5, "Mask");
            writeElement(sb, 5, "MultiLine",
                    String.valueOf(dim.flags.contains("multiline")));
            writeElement(sb, 5, "ExtendedEdit", "false");
            boolean dimNonneg = dim.flags.contains("nonneg");
            writeMinValue(sb, 5, dimNonneg);
            sb.append(indent(5)).append("<MaxValue xsi:nil=\"true\"/>\n");
            // FillFromFillingValue/FillValue/DataHistory валидны только для InformationRegister;
            // для Accumulation/Accounting/Calculation вызывают XSD-ошибку при загрузке.
            boolean dimIsInfoReg = "InformationRegister".equals(type);
            if (dimIsInfoReg) {
                writeElement(sb, 5, "FillFromFillingValue", "true");
                sb.append(indent(5)).append("<FillValue xsi:nil=\"true\"/>\n");
            }

            writeElement(sb, 5, "FillChecking",
                    dim.flags.contains("req") ? "ShowError" : "DontCheck");

            writeEmptyElement(sb, 5, "ChoiceParameterLinks");
            writeEmptyElement(sb, 5, "ChoiceParameters");
            writeElement(sb, 5, "QuickChoice", "Auto");
            writeElement(sb, 5, "CreateOnInput", "Auto");
            writeEmptyElement(sb, 5, "ChoiceForm");
            writeEmptyElement(sb, 5, "LinkByType");
            writeElement(sb, 5, "ChoiceHistoryOnInput", "Auto");

            // Indexing
            String indexing = "DontIndex";
            if (dim.flags.contains("index")) {
                indexing = "Index";
            }
            writeElement(sb, 5, "Indexing", indexing);

            writeElement(sb, 5, "FullTextSearch", "Use");
            if (dimIsInfoReg) {
                writeElement(sb, 5, "DataHistory", "Use");
            }

            // Dimension-specific properties
            writeElement(sb, 5, "Master",
                    String.valueOf(dim.flags.contains("master")));
            writeElement(sb, 5, "MainFilter",
                    String.valueOf(dim.flags.contains("mainfilter")));
            writeElement(sb, 5, "DenyIncompleteValues",
                    String.valueOf(dim.flags.contains("denyincomplete")));

            // UseInTotals — only for AccumulationRegister, default true per spec §9.4
            if ("AccumulationRegister".equals(type)) {
                // Default true; explicit "useintotals" flag confirms, no flag = true
                writeElement(sb, 5, "UseInTotals", "true");
            }

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</Dimension>\n");
        }
    }

    // ==================== Resource Writer (Registers) ====================

    private void writeResources(StringBuilder sb, JsonNode resNode, String type, String objectName) {
        for (JsonNode rNode : resNode) {
            AttrDef res = parseAttrDef(rNode);
            String uuid = UuidGenerator.generate();

            sb.append(indent(3)).append("<Resource uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");

            writeElement(sb, 5, "Name", res.name);
            writeSynonym(sb, 5, res.synonym != null ? res.synonym : camelCaseToWords(res.name));
            writeComment(sb, 5, res.comment != null ? res.comment : "");

            String resType = applyNonnegFlag(res.type, res.flags);
            writeTypeElement(sb, 5, resType);

            writeElement(sb, 5, "PasswordMode", "false");
            writeEmptyElement(sb, 5, "Format");
            writeEmptyElement(sb, 5, "EditFormat");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "MarkNegatives", "false");
            writeEmptyElement(sb, 5, "Mask");
            writeElement(sb, 5, "MultiLine",
                    String.valueOf(res.flags.contains("multiline")));
            writeElement(sb, 5, "ExtendedEdit", "false");
            boolean resNonneg = res.flags.contains("nonneg");
            writeMinValue(sb, 5, resNonneg);
            sb.append(indent(5)).append("<MaxValue xsi:nil=\"true\"/>\n");
            // FillFromFillingValue/FillValue/DataHistory — только для InformationRegister.
            boolean resIsInfoReg = "InformationRegister".equals(type);
            if (resIsInfoReg) {
                writeElement(sb, 5, "FillFromFillingValue", "true");
                sb.append(indent(5)).append("<FillValue xsi:nil=\"true\"/>\n");
            }

            writeElement(sb, 5, "FillChecking",
                    res.flags.contains("req") ? "ShowError" : "DontCheck");

            writeEmptyElement(sb, 5, "ChoiceParameterLinks");
            writeEmptyElement(sb, 5, "ChoiceParameters");
            writeElement(sb, 5, "QuickChoice", "Auto");
            writeElement(sb, 5, "CreateOnInput", "Auto");
            writeEmptyElement(sb, 5, "ChoiceForm");
            writeEmptyElement(sb, 5, "LinkByType");
            writeElement(sb, 5, "ChoiceHistoryOnInput", "Auto");

            writeElement(sb, 5, "Indexing", "DontIndex");
            writeElement(sb, 5, "FullTextSearch", "Use");
            if (resIsInfoReg) {
                writeElement(sb, 5, "DataHistory", "Use");
            }

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</Resource>\n");
        }
    }

    // ==================== Attribute Writer ====================

    private void writeAttributes(StringBuilder sb, JsonNode attrsNode, String type, String objectName) {
        for (JsonNode attrNode : attrsNode) {
            AttrDef attr = parseAttrDef(attrNode);
            String uuid = UuidGenerator.generate();

            sb.append(indent(3)).append("<Attribute uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");

            writeElement(sb, 5, "Name", attr.name);
            writeSynonym(sb, 5, attr.synonym != null ? attr.synonym : camelCaseToWords(attr.name));
            writeComment(sb, 5, attr.comment != null ? attr.comment : "");

            // Type (apply nonneg flag to Number types)
            String attrType = applyNonnegFlag(attr.type, attr.flags);
            writeTypeElement(sb, 5, attrType);

            // Attribute-specific properties
            writeElement(sb, 5, "PasswordMode", "false");
            writeEmptyElement(sb, 5, "Format");
            writeEmptyElement(sb, 5, "EditFormat");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "MarkNegatives", "false");
            writeEmptyElement(sb, 5, "Mask");
            writeElement(sb, 5, "MultiLine",
                    String.valueOf(attr.flags.contains("multiline")));
            writeElement(sb, 5, "ExtendedEdit", "false");
            boolean attrNonneg = attr.flags.contains("nonneg");
            writeMinValue(sb, 5, attrNonneg);
            sb.append(indent(5)).append("<MaxValue xsi:nil=\"true\"/>\n");

            // Storable objects get extra properties
            boolean storable = isStorableType(type);
            if (storable) {
                writeElement(sb, 5, "FillFromFillingValue", "true");
                sb.append(indent(5)).append("<FillValue xsi:nil=\"true\"/>\n");
            }

            // FillChecking
            writeElement(sb, 5, "FillChecking",
                    attr.flags.contains("req") ? "ShowError" : "DontCheck");

            writeEmptyElement(sb, 5, "ChoiceParameterLinks");
            writeEmptyElement(sb, 5, "ChoiceParameters");
            writeElement(sb, 5, "QuickChoice", "Auto");
            writeElement(sb, 5, "CreateOnInput", "Auto");
            writeEmptyElement(sb, 5, "ChoiceForm");
            writeEmptyElement(sb, 5, "LinkByType");
            writeElement(sb, 5, "ChoiceHistoryOnInput", "Auto");

            // Indexing (flags are stored lowercase)
            String indexing = "DontIndex";
            if (attr.flags.contains("indexadditional")) {
                indexing = "IndexWithAdditionalOrder";
            } else if (attr.flags.contains("index")) {
                indexing = "Index";
            }
            writeElement(sb, 5, "Indexing", indexing);

            if (storable) {
                writeElement(sb, 5, "FullTextSearch", "Use");
                writeElement(sb, 5, "DataHistory", "Use");
            }

            // Use (Catalog only)
            if ("Catalog".equals(type)) {
                writeElement(sb, 5, "Use", "ForItem");
            }

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</Attribute>\n");
        }
    }

    // ==================== TabularSection Writer ====================

    private void writeTabularSections(StringBuilder sb, JsonNode tsNode, String type, String objectName) {
        Iterator<Map.Entry<String, JsonNode>> fields = tsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String tsName = entry.getKey();
            JsonNode tsAttrs = entry.getValue();

            String tsUuid = UuidGenerator.generate();
            String tsSynonym = camelCaseToWords(tsName);

            sb.append(indent(3)).append("<TabularSection uuid=\"").append(tsUuid).append("\">\n");

            // InternalInfo for TS
            sb.append(indent(4)).append("<InternalInfo>\n");
            String tsGenPrefix = type + "TabularSection." + objectName + "." + tsName;
            String tsRowGenPrefix = type + "TabularSectionRow." + objectName + "." + tsName;
            writeGeneratedType(sb, 5, tsGenPrefix, "TabularSection");
            writeGeneratedType(sb, 5, tsRowGenPrefix, "TabularSectionRow");
            sb.append(indent(4)).append("</InternalInfo>\n");

            // Properties
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", tsName);
            writeSynonym(sb, 5, tsSynonym);
            writeComment(sb, 5, "");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "FillChecking", "DontCheck");

            // Use (Catalog only)
            if ("Catalog".equals(type)) {
                writeElement(sb, 5, "Use", "ForItem");
            }

            sb.append(indent(4)).append("</Properties>\n");

            // TS ChildObjects (attributes)
            if (tsAttrs.isArray() && tsAttrs.size() > 0) {
                sb.append(indent(4)).append("<ChildObjects>\n");
                for (JsonNode tsAttrNode : tsAttrs) {
                    AttrDef attr = parseAttrDef(tsAttrNode);
                    writeTsAttribute(sb, attr, type);
                }
                sb.append(indent(4)).append("</ChildObjects>\n");
            } else {
                sb.append(indent(4)).append("<ChildObjects/>\n");
            }

            sb.append(indent(3)).append("</TabularSection>\n");
        }
    }

    private void writeTsAttribute(StringBuilder sb, AttrDef attr, String parentType) {
        String uuid = UuidGenerator.generate();
        sb.append(indent(5)).append("<Attribute uuid=\"").append(uuid).append("\">\n");
        sb.append(indent(6)).append("<Properties>\n");

        writeElement(sb, 7, "Name", attr.name);
        writeSynonym(sb, 7, attr.synonym != null ? attr.synonym : camelCaseToWords(attr.name));
        writeComment(sb, 7, attr.comment != null ? attr.comment : "");
        String tsAttrType = applyNonnegFlag(attr.type, attr.flags);
        writeTypeElement(sb, 7, tsAttrType);

        writeElement(sb, 7, "PasswordMode", "false");
        writeEmptyElement(sb, 7, "Format");
        writeEmptyElement(sb, 7, "EditFormat");
        writeEmptyElement(sb, 7, "ToolTip");
        writeElement(sb, 7, "MarkNegatives", "false");
        writeEmptyElement(sb, 7, "Mask");
        writeElement(sb, 7, "MultiLine",
                String.valueOf(attr.flags.contains("multiline")));
        writeElement(sb, 7, "ExtendedEdit", "false");
        boolean tsNonneg = attr.flags.contains("nonneg");
        writeMinValue(sb, 7, tsNonneg);
        sb.append(indent(7)).append("<MaxValue xsi:nil=\"true\"/>\n");

        writeElement(sb, 7, "FillChecking",
                attr.flags.contains("req") ? "ShowError" : "DontCheck");

        writeEmptyElement(sb, 7, "ChoiceParameterLinks");
        writeEmptyElement(sb, 7, "ChoiceParameters");
        writeElement(sb, 7, "QuickChoice", "Auto");
        writeElement(sb, 7, "CreateOnInput", "Auto");
        writeEmptyElement(sb, 7, "ChoiceForm");
        writeEmptyElement(sb, 7, "LinkByType");
        writeElement(sb, 7, "ChoiceHistoryOnInput", "Auto");

        boolean storable = isStorableType(parentType);
        if (storable) {
            writeElement(sb, 7, "Indexing", "DontIndex");
            writeElement(sb, 7, "FullTextSearch", "Use");
            writeElement(sb, 7, "DataHistory", "Use");
        }

        sb.append(indent(6)).append("</Properties>\n");
        sb.append(indent(5)).append("</Attribute>\n");
    }

    // ==================== EnumValue Writer ====================

    private void writeEnumValues(StringBuilder sb, JsonNode valuesNode, String objectName) {
        for (JsonNode val : valuesNode) {
            String valName;
            String valSynonym;

            if (val.isTextual()) {
                valName = val.asText();
                valSynonym = camelCaseToWords(valName);
            } else {
                valName = requireString(val, "name");
                valSynonym = getString(val, "synonym", camelCaseToWords(valName));
            }

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<EnumValue uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", valName);
            writeSynonym(sb, 5, valSynonym);
            writeComment(sb, 5, "");
            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</EnumValue>\n");
        }
    }

    // ==================== AccountingFlag Writer ====================

    private void writeAccountingFlags(StringBuilder sb, JsonNode flagsNode, String elementName) {
        for (JsonNode flag : flagsNode) {
            String flagName;
            String flagSynonym;

            if (flag.isTextual()) {
                flagName = flag.asText();
                flagSynonym = camelCaseToWords(flagName);
            } else {
                flagName = requireString(flag, "name");
                flagSynonym = getString(flag, "synonym", camelCaseToWords(flagName));
            }

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<").append(elementName)
                    .append(" uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", flagName);
            writeSynonym(sb, 5, flagSynonym);
            writeComment(sb, 5, "");

            // AccountingFlags are always Boolean
            sb.append(indent(5)).append("<Type>\n");
            sb.append(indent(6)).append("<v8:Type>xs:boolean</v8:Type>\n");
            sb.append(indent(5)).append("</Type>\n");

            // Standard attribute-like properties
            writeElement(sb, 5, "PasswordMode", "false");
            writeEmptyElement(sb, 5, "Format");
            writeEmptyElement(sb, 5, "EditFormat");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "MarkNegatives", "false");
            writeEmptyElement(sb, 5, "Mask");
            writeElement(sb, 5, "MultiLine", "false");
            writeElement(sb, 5, "ExtendedEdit", "false");
            sb.append(indent(5)).append("<MinValue xsi:nil=\"true\"/>\n");
            sb.append(indent(5)).append("<MaxValue xsi:nil=\"true\"/>\n");
            writeElement(sb, 5, "FillFromFillingValue", "true");
            sb.append(indent(5)).append("<FillValue xsi:nil=\"true\"/>\n");
            writeElement(sb, 5, "FillChecking", "DontCheck");
            writeEmptyElement(sb, 5, "ChoiceParameterLinks");
            writeEmptyElement(sb, 5, "ChoiceParameters");
            writeElement(sb, 5, "QuickChoice", "Auto");
            writeElement(sb, 5, "CreateOnInput", "Auto");
            writeEmptyElement(sb, 5, "ChoiceForm");
            writeEmptyElement(sb, 5, "LinkByType");
            writeElement(sb, 5, "ChoiceHistoryOnInput", "Auto");
            writeElement(sb, 5, "Indexing", "DontIndex");
            writeElement(sb, 5, "FullTextSearch", "Use");
            writeElement(sb, 5, "DataHistory", "Use");

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</").append(elementName).append(">\n");
        }
    }

    // ==================== Column Writer (DocumentJournal) ====================

    private void writeColumns(StringBuilder sb, JsonNode columnsNode) {
        for (JsonNode col : columnsNode) {
            String colName;
            String colSynonym;
            String indexing = "DontIndex";
            List<String> references = new ArrayList<>();

            if (col.isTextual()) {
                colName = col.asText();
                colSynonym = camelCaseToWords(colName);
            } else {
                colName = requireString(col, "name");
                colSynonym = getString(col, "synonym", camelCaseToWords(colName));
                indexing = getString(col, "indexing", "DontIndex");
                references = getStringList(col, "references");
            }

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<Column uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", colName);
            writeSynonym(sb, 5, colSynonym);
            writeComment(sb, 5, "");
            writeElement(sb, 5, "Indexing", indexing);

            if (references.isEmpty()) {
                writeEmptyElement(sb, 5, "References");
            } else {
                sb.append(indent(5)).append("<References>\n");
                for (String ref : references) {
                    sb.append(indent(6)).append("<xr:Item xsi:type=\"xr:MDObjectRef\">")
                            .append(esc(ref)).append("</xr:Item>\n");
                }
                sb.append(indent(5)).append("</References>\n");
            }

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</Column>\n");
        }
    }

    // ==================== URLTemplate Writer (HTTPService) ====================

    private void writeUrlTemplates(StringBuilder sb, JsonNode templatesNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = templatesNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String tplName = entry.getKey();
            JsonNode tplDef = entry.getValue();

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<URLTemplate uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", tplName);
            writeSynonym(sb, 5, camelCaseToWords(tplName));
            writeComment(sb, 5, "");

            String template;
            JsonNode methodsNode = null;
            if (tplDef.isTextual()) {
                template = tplDef.asText();
            } else {
                template = getString(tplDef, "template", "/" + tplName);
                methodsNode = tplDef.get("methods");
            }
            writeElement(sb, 5, "Template", template);

            sb.append(indent(4)).append("</Properties>\n");

            // Methods
            if (methodsNode != null && methodsNode.size() > 0) {
                sb.append(indent(4)).append("<ChildObjects>\n");
                Iterator<Map.Entry<String, JsonNode>> methods = methodsNode.fields();
                while (methods.hasNext()) {
                    Map.Entry<String, JsonNode> methodEntry = methods.next();
                    String methodName = methodEntry.getKey();
                    String httpMethod = methodEntry.getValue().asText();
                    String handler = tplName + methodName;

                    String methodUuid = UuidGenerator.generate();
                    sb.append(indent(5)).append("<Method uuid=\"").append(methodUuid).append("\">\n");
                    sb.append(indent(6)).append("<Properties>\n");
                    writeElement(sb, 7, "Name", methodName);
                    writeSynonym(sb, 7, camelCaseToWords(methodName));
                    writeComment(sb, 7, "");
                    writeElement(sb, 7, "HTTPMethod", httpMethod);
                    writeElement(sb, 7, "Handler", handler);
                    sb.append(indent(6)).append("</Properties>\n");
                    sb.append(indent(5)).append("</Method>\n");
                }
                sb.append(indent(4)).append("</ChildObjects>\n");
            } else {
                sb.append(indent(4)).append("<ChildObjects/>\n");
            }

            sb.append(indent(3)).append("</URLTemplate>\n");
        }
    }

    // ==================== Operation Writer (WebService) ====================

    private void writeOperations(StringBuilder sb, JsonNode opsNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = opsNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            String opName = entry.getKey();
            JsonNode opDef = entry.getValue();

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<Operation uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", opName);
            writeSynonym(sb, 5, camelCaseToWords(opName));
            writeComment(sb, 5, "");

            String returnType;
            String handler;
            boolean nillable;
            boolean transactioned;
            JsonNode parametersNode = null;

            if (opDef.isTextual()) {
                returnType = opDef.asText();
                handler = opName;
                nillable = false;
                transactioned = false;
            } else {
                returnType = getString(opDef, "returnType", "xs:string");
                handler = getString(opDef, "handler", opName);
                nillable = getBool(opDef, "nillable", false);
                transactioned = getBool(opDef, "transactioned", false);
                parametersNode = opDef.get("parameters");
            }

            sb.append(indent(5)).append("<XDTOReturningValueType>\n");
            sb.append(indent(6)).append("<v8:Type>").append(esc(returnType)).append("</v8:Type>\n");
            sb.append(indent(6)).append("<v8:Nillable>").append(nillable).append("</v8:Nillable>\n");
            sb.append(indent(5)).append("</XDTOReturningValueType>\n");
            writeElement(sb, 5, "ProcedureName", handler);
            writeElement(sb, 5, "Transactioned", String.valueOf(transactioned));

            sb.append(indent(4)).append("</Properties>\n");

            // Parameters
            if (parametersNode != null && parametersNode.size() > 0) {
                sb.append(indent(4)).append("<ChildObjects>\n");
                Iterator<Map.Entry<String, JsonNode>> params = parametersNode.fields();
                while (params.hasNext()) {
                    Map.Entry<String, JsonNode> paramEntry = params.next();
                    String paramName = paramEntry.getKey();
                    JsonNode paramDef = paramEntry.getValue();

                    String paramType;
                    boolean paramNillable;
                    String direction;
                    if (paramDef.isTextual()) {
                        paramType = paramDef.asText();
                        paramNillable = true;
                        direction = "Input";
                    } else {
                        paramType = getString(paramDef, "type", "xs:string");
                        paramNillable = getBool(paramDef, "nillable", true);
                        direction = getString(paramDef, "direction", "In");
                    }
                    // Normalize DSL shorthand → XML values
                    direction = switch (direction) {
                        case "In" -> "Input";
                        case "Out" -> "Output";
                        case "InOut" -> "InputOutput";
                        default -> direction; // already canonical
                    };

                    String paramUuid = UuidGenerator.generate();
                    sb.append(indent(5)).append("<Parameter uuid=\"").append(paramUuid).append("\">\n");
                    sb.append(indent(6)).append("<Properties>\n");
                    writeElement(sb, 7, "Name", paramName);
                    sb.append(indent(7)).append("<XDTOValueType>\n");
                    sb.append(indent(8)).append("<v8:Type>").append(esc(paramType)).append("</v8:Type>\n");
                    sb.append(indent(8)).append("<v8:Nillable>").append(paramNillable).append("</v8:Nillable>\n");
                    sb.append(indent(7)).append("</XDTOValueType>\n");
                    writeElement(sb, 7, "TransferDirection", direction);
                    sb.append(indent(6)).append("</Properties>\n");
                    sb.append(indent(5)).append("</Parameter>\n");
                }
                sb.append(indent(4)).append("</ChildObjects>\n");
            } else {
                sb.append(indent(4)).append("<ChildObjects/>\n");
            }

            sb.append(indent(3)).append("</Operation>\n");
        }
    }

    // ==================== AddressingAttribute Writer (Task) ====================

    private void writeAddressingAttributes(StringBuilder sb, JsonNode addrNode) {
        for (JsonNode node : addrNode) {
            String attrName;
            String attrType = "String(10)";
            String attrSynonym = null;
            String addressingDimension = "";

            if (node.isTextual()) {
                attrName = node.asText();
            } else {
                attrName = requireString(node, "name");
                attrType = normalizeDslType(getString(node, "type", "String(10)"));
                attrSynonym = getString(node, "synonym", null);
                addressingDimension = getString(node, "addressingDimension", "");
            }

            if (attrSynonym == null) attrSynonym = camelCaseToWords(attrName);

            String uuid = UuidGenerator.generate();
            sb.append(indent(3)).append("<AddressingAttribute uuid=\"").append(uuid).append("\">\n");
            sb.append(indent(4)).append("<Properties>\n");
            writeElement(sb, 5, "Name", attrName);
            writeSynonym(sb, 5, attrSynonym);
            writeComment(sb, 5, "");
            writeTypeElement(sb, 5, attrType);

            writeElement(sb, 5, "PasswordMode", "false");
            writeEmptyElement(sb, 5, "Format");
            writeEmptyElement(sb, 5, "EditFormat");
            writeEmptyElement(sb, 5, "ToolTip");
            writeElement(sb, 5, "MarkNegatives", "false");
            writeEmptyElement(sb, 5, "Mask");
            writeElement(sb, 5, "MultiLine", "false");
            writeElement(sb, 5, "ExtendedEdit", "false");
            sb.append(indent(5)).append("<MinValue xsi:nil=\"true\"/>\n");
            sb.append(indent(5)).append("<MaxValue xsi:nil=\"true\"/>\n");
            writeElement(sb, 5, "FillFromFillingValue", "true");
            sb.append(indent(5)).append("<FillValue xsi:nil=\"true\"/>\n");
            writeElement(sb, 5, "FillChecking", "DontCheck");
            writeEmptyElement(sb, 5, "ChoiceParameterLinks");
            writeEmptyElement(sb, 5, "ChoiceParameters");
            writeElement(sb, 5, "QuickChoice", "Auto");
            writeElement(sb, 5, "CreateOnInput", "Auto");
            writeEmptyElement(sb, 5, "ChoiceForm");
            writeEmptyElement(sb, 5, "LinkByType");
            writeElement(sb, 5, "ChoiceHistoryOnInput", "Auto");
            writeElement(sb, 5, "Indexing", "DontIndex");
            writeElement(sb, 5, "FullTextSearch", "Use");

            // AddressingDimension
            if (!addressingDimension.isEmpty()) {
                writeElement(sb, 5, "AddressingDimension", addressingDimension);
            }

            sb.append(indent(4)).append("</Properties>\n");
            sb.append(indent(3)).append("</AddressingAttribute>\n");
        }
    }

    // ==================== Nonneg Flag ====================

    /**
     * Apply nonneg flag from DSL shorthand to Number type.
     * "Number(15,2)" + nonneg → "Number(15,2,nonneg)"
     */
    private String applyNonnegFlag(String type, Set<String> flags) {
        if (!flags.contains("nonneg")) return type;
        if (type == null) return type;
        // Already has nonneg
        if (type.toLowerCase().contains("nonneg")) return type;
        // Only applies to Number types
        Matcher m = NUMBER_TYPE.matcher(type);
        if (m.matches()) {
            int digits = Integer.parseInt(m.group(1));
            int frac = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
            return "Number(" + digits + "," + frac + ",nonneg)";
        }
        return type;
    }

    // ==================== Type XML Generation ====================

    private void writeTypeElement(StringBuilder sb, int indent, String dslType) {
        if (dslType == null || dslType.isEmpty()) {
            // Default: String(10)
            dslType = "String(10)";
        }

        String normalizedType = normalizeDslType(dslType);

        sb.append(indent(indent)).append("<Type>\n");

        // Check for composite type (array)
        if (normalizedType.contains(",") && !normalizedType.contains("(")) {
            // Multiple types separated by comma
            for (String t : normalizedType.split(",")) {
                writeTypeValue(sb, indent + 1, t.trim());
            }
        } else {
            writeTypeValue(sb, indent + 1, normalizedType);
        }

        sb.append(indent(indent)).append("</Type>\n");
    }

    private void writeTypeComposite(StringBuilder sb, int indent, List<String> types) {
        sb.append(indent(indent)).append("<Type>\n");
        for (String t : types) {
            String normalized = normalizeDslType(t);
            writeTypeValue(sb, indent + 1, normalized);
        }
        sb.append(indent(indent)).append("</Type>\n");
    }

    private void writeTypeValue(StringBuilder sb, int indent, String normalizedType) {
        // String
        Matcher strM = STRING_TYPE.matcher(normalizedType);
        if (strM.matches()) {
            int len = strM.group(1) != null ? Integer.parseInt(strM.group(1)) : 0;
            sb.append(indent(indent)).append("<v8:Type>xs:string</v8:Type>\n");
            sb.append(indent(indent)).append("<v8:StringQualifiers>\n");
            sb.append(indent(indent + 1)).append("<v8:Length>").append(len).append("</v8:Length>\n");
            sb.append(indent(indent + 1)).append("<v8:AllowedLength>Variable</v8:AllowedLength>\n");
            sb.append(indent(indent)).append("</v8:StringQualifiers>\n");
            return;
        }

        // Number
        Matcher numM = NUMBER_TYPE.matcher(normalizedType);
        if (numM.matches()) {
            int digits = Integer.parseInt(numM.group(1));
            int frac = numM.group(2) != null ? Integer.parseInt(numM.group(2)) : 0;
            String sign = numM.group(3) != null ? "Nonnegative" : "Any";
            sb.append(indent(indent)).append("<v8:Type>xs:decimal</v8:Type>\n");
            sb.append(indent(indent)).append("<v8:NumberQualifiers>\n");
            sb.append(indent(indent + 1)).append("<v8:Digits>").append(digits).append("</v8:Digits>\n");
            sb.append(indent(indent + 1)).append("<v8:FractionDigits>").append(frac).append("</v8:FractionDigits>\n");
            sb.append(indent(indent + 1)).append("<v8:AllowedSign>").append(sign).append("</v8:AllowedSign>\n");
            sb.append(indent(indent)).append("</v8:NumberQualifiers>\n");
            return;
        }

        // Boolean
        if ("Boolean".equalsIgnoreCase(normalizedType)) {
            sb.append(indent(indent)).append("<v8:Type>xs:boolean</v8:Type>\n");
            return;
        }

        // Date
        if ("Date".equalsIgnoreCase(normalizedType)) {
            sb.append(indent(indent)).append("<v8:Type>xs:dateTime</v8:Type>\n");
            sb.append(indent(indent)).append("<v8:DateQualifiers>\n");
            sb.append(indent(indent + 1)).append("<v8:DateFractions>Date</v8:DateFractions>\n");
            sb.append(indent(indent)).append("</v8:DateQualifiers>\n");
            return;
        }
        if ("DateTime".equalsIgnoreCase(normalizedType)) {
            sb.append(indent(indent)).append("<v8:Type>xs:dateTime</v8:Type>\n");
            sb.append(indent(indent)).append("<v8:DateQualifiers>\n");
            sb.append(indent(indent + 1)).append("<v8:DateFractions>DateTime</v8:DateFractions>\n");
            sb.append(indent(indent)).append("</v8:DateQualifiers>\n");
            return;
        }

        // DefinedType.Xxx → v8:TypeSet
        if (normalizedType.startsWith("DefinedType.")) {
            sb.append(indent(indent)).append("<v8:TypeSet>cfg:").append(esc(normalizedType)).append("</v8:TypeSet>\n");
            return;
        }

        // Reference types: *Ref.Xxx → cfg:*Ref.Xxx
        if (normalizedType.contains("Ref.")) {
            String xmlType = normalizedType.startsWith("cfg:") ? normalizedType : "cfg:" + normalizedType;
            sb.append(indent(indent)).append("<v8:Type>").append(esc(xmlType)).append("</v8:Type>\n");
            return;
        }

        // Object types: *Object.Xxx → cfg:*Object.Xxx
        if (normalizedType.contains("Object.")) {
            String xmlType = normalizedType.startsWith("cfg:") ? normalizedType : "cfg:" + normalizedType;
            sb.append(indent(indent)).append("<v8:Type>").append(esc(xmlType)).append("</v8:Type>\n");
            return;
        }

        // Known special types
        if ("ValueStorage".equalsIgnoreCase(normalizedType) || "v8:ValueStorage".equals(normalizedType)) {
            sb.append(indent(indent)).append("<v8:Type>v8:ValueStorage</v8:Type>\n");
            return;
        }
        if ("UUID".equalsIgnoreCase(normalizedType) || "v8:UUID".equals(normalizedType)) {
            sb.append(indent(indent)).append("<v8:Type>v8:UUID</v8:Type>\n");
            return;
        }

        // xs: prefixed types pass through
        if (normalizedType.startsWith("xs:") || normalizedType.startsWith("v8:") || normalizedType.startsWith("cfg:")) {
            sb.append(indent(indent)).append("<v8:Type>").append(esc(normalizedType)).append("</v8:Type>\n");
            return;
        }

        throw new IllegalArgumentException("Unknown DSL type: '" + normalizedType
                + "'. Supported: String, Number, Boolean, Date, DateTime, *Ref.Name, DefinedType.Name");
    }

    // ==================== Directory Structure ====================

    private void createDirStructure(Path typeDir, String name, String type,
                                     TypeDescriptor td) throws IOException {
        Path objDir = typeDir.resolve(name);
        Path extDir = objDir.resolve("Ext");
        Files.createDirectories(extDir);

        // Module files depend on type
        switch (type) {
            case "Enum", "DefinedType", "ScheduledJob",
                 "EventSubscription", "DocumentJournal" -> {
                // No module files
            }
            case "Constant" -> {
                // Constant has only ManagerModule
                writeWithBom(extDir.resolve("ManagerModule.bsl"), "");
            }
            case "CommonModule", "HTTPService", "WebService" -> {
                writeWithBom(extDir.resolve("Module.bsl"), "");
            }
            case "InformationRegister", "AccumulationRegister",
                 "AccountingRegister", "CalculationRegister" -> {
                writeWithBom(extDir.resolve("RecordSetModule.bsl"), "");
            }
            default -> {
                // Reference types, Report, DataProcessor, BusinessProcess, Task
                writeWithBom(extDir.resolve("ObjectModule.bsl"), "");
                writeWithBom(extDir.resolve("ManagerModule.bsl"), "");
            }
        }

        // ExchangePlan: Content.xml stub
        if ("ExchangePlan".equals(type)) {
            writeExchangePlanContent(extDir.resolve("Content.xml"));
        }

        // BusinessProcess: Flowchart.xml stub
        if ("BusinessProcess".equals(type)) {
            writeFlowchartStub(extDir.resolve("Flowchart.xml"));
        }
    }

    private void writeExchangePlanContent(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<ExchangePlanContent xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n");
        // TASK-171 D-2: канонический namespace v8.1c.ru
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\"/>\n");
        writeWithBom(path, sb.toString());
    }

    private void writeFlowchartStub(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Flowchart xmlns=\"http://v8.1c.ru/8.3/flowchart\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\"/>\n");
        writeWithBom(path, sb.toString());
    }

    // ==================== Attribute DSL Parsing ====================

    private AttrDef parseAttrDef(JsonNode node) {
        if (node.isTextual()) {
            return parseShorthandAttr(node.asText());
        }
        // Object form
        String name = requireString(node, "name");
        String type = getString(node, "type", "String(10)");
        type = normalizeDslType(type);

        // Handle separate length/precision fields
        if (!type.contains("(")) {
            if ("String".equalsIgnoreCase(type) && node.has("length")) {
                type = "String(" + node.get("length").asInt() + ")";
            } else if ("Number".equalsIgnoreCase(type) && node.has("length")) {
                int len = node.get("length").asInt();
                int prec = node.has("precision") ? node.get("precision").asInt() : 0;
                boolean nonneg = getBool(node, "nonneg", false);
                type = "Number(" + len + "," + prec + (nonneg ? ",nonneg" : "") + ")";
            }
        }

        String synonym = getString(node, "synonym", null);
        String comment = getString(node, "comment", null);
        Set<String> flags = new HashSet<>();
        String fillChecking = getString(node, "fillChecking", null);
        if ("ShowError".equals(fillChecking)) flags.add("req");
        String indexing = getString(node, "indexing", null);
        if ("Index".equals(indexing)) flags.add("index");
        if ("IndexWithAdditionalOrder".equals(indexing)) flags.add("indexadditional");
        if (getBool(node, "nonneg", false)) flags.add("nonneg");
        if (getBool(node, "multiLine", false)) flags.add("multiline");

        // Register-specific dimension flags (object form)
        if (getBool(node, "master", false)) flags.add("master");
        if (getBool(node, "mainFilter", false)) flags.add("mainfilter");
        if (getBool(node, "denyIncomplete", false)) flags.add("denyincomplete");
        if (getBool(node, "useInTotals", false)) flags.add("useintotals");

        return new AttrDef(name, type, synonym, comment, flags);
    }

    private AttrDef parseShorthandAttr(String shorthand) {
        Matcher m = ATTR_SHORT.matcher(shorthand.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid attribute shorthand: " + shorthand);
        }

        String name = m.group(1).trim();
        String type = m.group(2) != null ? normalizeDslType(m.group(2).trim()) : "String(10)";
        Set<String> flags = new HashSet<>();

        if (m.group(3) != null) {
            for (String flag : m.group(3).split(",")) {
                flags.add(flag.trim().toLowerCase());
            }
        }

        return new AttrDef(name, type, null, null, flags);
    }

    private record AttrDef(String name, String type, String synonym, String comment, Set<String> flags) {
        AttrDef {
            if (RESERVED_ATTR_NAMES.contains(name)) {
                throw new IllegalArgumentException(
                        "Attribute name '" + name + "' is reserved (standard attribute of the object). "
                        + "Reserved names: " + RESERVED_ATTR_NAMES);
            }
        }
    }

    /**
     * Зарезервированные имена стандартных реквизитов 1С. Использование любого из них
     * как имени пользовательского реквизита ломает загрузку конфигурации.
     */
    private static final Set<String> RESERVED_ATTR_NAMES = Set.of(
            "Ref", "Code", "Description", "Parent", "Owner", "IsFolder",
            "DeletionMark", "PostingMode", "DataVersion", "Predefined",
            "PredefinedDataName", "Posted", "Date", "Number", "ThisObject",
            // Русские стандартные имена
            "Ссылка", "Код", "Наименование", "Родитель", "Владелец", "ЭтоГруппа",
            "ПометкаУдаления", "РежимПроведения", "ВерсияДанных", "Предопределенный",
            "ИмяПредопределенныхДанных", "Проведен", "Дата", "Номер");

    // ==================== Type Name Normalization ====================

    private String normalizeTypeName(String raw) {
        String canonical = RU_TYPE_NAMES.get(raw);
        return canonical != null ? canonical : raw;
    }

    private String normalizeDslType(String raw) {
        if (raw == null) return "String(10)";

        // Check Russian type synonyms (case-insensitive prefix matching for Строка(100) etc.)
        for (Map.Entry<String, String> entry : RU_DSL_TYPES.entrySet()) {
            if (startsWithIgnoreCase(raw, entry.getKey())) {
                raw = entry.getValue() + raw.substring(entry.getKey().length());
                break;
            }
        }

        return raw;
    }

    private static boolean startsWithIgnoreCase(String str, String prefix) {
        if (str.length() < prefix.length()) return false;
        return str.substring(0, prefix.length()).equalsIgnoreCase(prefix);
    }

    // ==================== CamelCase to Words ====================

    /**
     * Split CamelCase identifier into words for auto-synonym.
     * АвансовыйОтчет → Авансовый отчет
     * IncomingDocument → Incoming document
     */
    static String camelCaseToWords(String name) {
        if (name == null || name.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        result.append(name.charAt(0));

        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            char prev = name.charAt(i - 1);

            // Detect boundary: lowercase followed by uppercase
            boolean isCyrBoundary = isCyrLower(prev) && isCyrUpper(c);
            boolean isLatBoundary = isLatLower(prev) && isLatUpper(c);

            if (isCyrBoundary || isLatBoundary) {
                result.append(' ');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    private static boolean isCyrUpper(char c) {
        return (c >= 'А' && c <= 'Я') || c == 'Ё';
    }
    private static boolean isCyrLower(char c) {
        return (c >= 'а' && c <= 'я') || c == 'ё';
    }
    private static boolean isLatUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }
    private static boolean isLatLower(char c) {
        return c >= 'a' && c <= 'z';
    }

    // ==================== Type Category Checks ====================

    private boolean isStorableType(String type) {
        // Report and DataProcessor are not "storable" (no Indexing, FullTextSearch, DataHistory)
        return switch (type) {
            case "Report", "DataProcessor" -> false;
            default -> true;
        };
    }

    private static boolean isRegisterType(String type) {
        return switch (type) {
            case "InformationRegister", "AccumulationRegister",
                 "AccountingRegister", "CalculationRegister" -> true;
            default -> false;
        };
    }

    // ==================== XML Helpers ====================

    /** Write MinValue: 0 if nonneg, xsi:nil otherwise */
    private void writeMinValue(StringBuilder sb, int indent, boolean nonneg) {
        if (nonneg) {
            sb.append(indent(indent)).append("<MinValue>\n");
            sb.append(indent(indent + 1)).append("<v8:Type>xs:decimal</v8:Type>\n");
            sb.append(indent(indent + 1)).append("<v8:Value>0</v8:Value>\n");
            sb.append(indent(indent)).append("</MinValue>\n");
        } else {
            sb.append(indent(indent)).append("<MinValue xsi:nil=\"true\"/>\n");
        }
    }

    private void writeElement(StringBuilder sb, int indent, String name, String value) {
        sb.append(indent(indent)).append("<").append(name).append(">")
                .append(esc(value)).append("</").append(name).append(">\n");
    }

    private void writeEmptyElement(StringBuilder sb, int indent, String name) {
        sb.append(indent(indent)).append("<").append(name).append("/>\n");
    }

    private void writeSynonym(StringBuilder sb, int indent, String synonym) {
        if (synonym == null || synonym.isEmpty()) {
            writeEmptyElement(sb, indent, "Synonym");
            return;
        }
        sb.append(indent(indent)).append("<Synonym>\n");
        sb.append(indent(indent + 1)).append("<v8:item>\n");
        sb.append(indent(indent + 2)).append("<v8:lang>ru</v8:lang>\n");
        sb.append(indent(indent + 2)).append("<v8:content>").append(esc(synonym)).append("</v8:content>\n");
        sb.append(indent(indent + 1)).append("</v8:item>\n");
        sb.append(indent(indent)).append("</Synonym>\n");
    }

    private void writeComment(StringBuilder sb, int indent, String comment) {
        if (comment == null || comment.isEmpty()) {
            writeEmptyElement(sb, indent, "Comment");
        } else {
            writeElement(sb, indent, "Comment", comment);
        }
    }

    private void writeGeneratedType(StringBuilder sb, int indent, String name, String category) {
        sb.append(indent(indent)).append("<xr:GeneratedType name=\"").append(esc(name))
                .append("\" category=\"").append(category).append("\">\n");
        sb.append(indent(indent + 1)).append("<xr:TypeId>").append(UuidGenerator.generate()).append("</xr:TypeId>\n");
        sb.append(indent(indent + 1)).append("<xr:ValueId>").append(UuidGenerator.generate()).append("</xr:ValueId>\n");
        sb.append(indent(indent)).append("</xr:GeneratedType>\n");
    }

    private static String indent(int level) {
        return "\t".repeat(level);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    // ==================== JSON Helpers ====================

    private static String requireString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isEmpty()) {
            throw new IllegalArgumentException("Required field missing: " + field);
        }
        return node.asText();
    }

    private static String getString(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return defaultValue;
        return node.asText();
    }

    private static boolean getBool(JsonNode root, String field, boolean defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return defaultValue;
        return node.asBoolean();
    }

    private static int getInt(JsonNode root, String field, int defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return defaultValue;
        return node.asInt();
    }

    private static List<String> getStringList(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        JsonNode node = root.get(field);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Значения перечисления: ключ {@code values} ИЛИ алиас {@code enumValues}
     * (TASK-171 D-5). Возвращает {@code null}, если массива нет ни под одним ключом.
     */
    private static JsonNode enumValuesNode(JsonNode root) {
        if (root.has("values") && root.get("values").isArray()) return root.get("values");
        if (root.has("enumValues") && root.get("enumValues").isArray()) return root.get("enumValues");
        return null;
    }

    /** Get value types from valueTypes (array) or valueType (string or array alias). */
    private static List<String> getValueTypesList(JsonNode root) {
        List<String> result = getStringList(root, "valueTypes");
        if (!result.isEmpty()) return result;

        JsonNode vtNode = root.get("valueType");
        if (vtNode == null || vtNode.isNull()) return result;

        if (vtNode.isArray()) {
            for (JsonNode item : vtNode) {
                result.add(item.asText());
            }
        } else if (vtNode.isTextual()) {
            result.add(vtNode.asText());
        }
        return result;
    }

    // ==================== Predefined items (TASK-171 D-1) ====================

    /**
     * Записать {@code <Объект>/Ext/Predefined.xml}, если в DSL есть массив
     * {@code predefinedItems} (или алиас {@code predefined} в виде массива) и
     * тип объекта поддерживает предопределённые элементы.
     *
     * <p>Каждый элемент: {@code {name, code?, description?, isFolder?}} или просто
     * строка-имя. Код при отсутствии — авто-нумерация, дополненная нулями до
     * {@code codeLength} (по умолчанию 9). {@code description} по умолчанию = {@code name}.
     */
    private void writePredefinedItems(Path typeDir, String name, String type,
                                      JsonNode root, String formatVersion) throws IOException {
        String xmlElement = MetadataTypeRegistry.get(type).xmlElement();
        String xsiType = PredefinedXmlWriter.xsiTypeFor(xmlElement);
        if (xsiType == null) {
            return; // тип не поддерживает предопределённые
        }

        JsonNode itemsNode = null;
        if (root.has("predefinedItems") && root.get("predefinedItems").isArray()) {
            itemsNode = root.get("predefinedItems");
        } else if (root.has("predefined") && root.get("predefined").isArray()) {
            // алиас; на ScheduledJob "predefined" — boolean, поэтому проверяем isArray
            itemsNode = root.get("predefined");
        }
        if (itemsNode == null || itemsNode.size() == 0) {
            return;
        }

        int codeWidth = getInt(root, "codeLength", PredefinedXmlWriter.DEFAULT_CODE_WIDTH);
        List<PredefinedXmlWriter.Item> items = new ArrayList<>();
        int seq = 1;
        for (JsonNode n : itemsNode) {
            String itemName;
            String code;
            String description;
            boolean isFolder;
            if (n.isTextual()) {
                itemName = n.asText();
                code = PredefinedXmlWriter.formatCode(seq, codeWidth);
                description = itemName;
                isFolder = false;
            } else {
                itemName = requireString(n, "name");
                String rawCode = getString(n, "code", "");
                code = rawCode.isEmpty() ? PredefinedXmlWriter.formatCode(seq, codeWidth) : rawCode;
                description = getString(n, "description", itemName);
                isFolder = getBool(n, "isFolder", false);
            }
            items.add(new PredefinedXmlWriter.Item(itemName, code, description, isFolder));
            seq++;
        }

        String xml = PredefinedXmlWriter.buildFile(xsiType, formatVersion, items);
        Path extDir = typeDir.resolve(name).resolve("Ext");
        Files.createDirectories(extDir);
        writeWithBom(extDir.resolve("Predefined.xml"), xml);
    }

    // ==================== File I/O ====================

    private static void writeWithBom(Path path, String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
        Files.write(path, result);
    }
}

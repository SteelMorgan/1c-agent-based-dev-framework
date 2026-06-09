package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.MetaBatchDsl;
import io.github.onec.xmlgen.dsl.MetaBatchDsl.Operation;
import io.github.onec.xmlgen.editor.ObjectContainerEditor;
import io.github.onec.xmlgen.form.fromobject.FormFromObjectGenerator;
import io.github.onec.xmlgen.form.fromobject.PurposeResolver;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.CompositeType;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;
import io.github.onec.xmlgen.model.MlText;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Редактирование существующего XML-файла объекта метаданных 1С.
 *
 * Поддерживаемые операции:
 * - add-attribute, add-dimension, add-resource, add-enumValue, add-column
 * - add-ts (табличная часть с реквизитами)
 * - add-form, add-template, add-command
 * - add-ts-attribute (реквизит внутри существующей ТЧ)
 * - remove-attribute, remove-ts, remove-dimension, remove-resource, remove-enumValue,
 *   remove-column, remove-form, remove-template, remove-command, remove-ts-attribute
 * - modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column
 *
 * Все операции работают с текстовым представлением XML для сохранения форматирования.
 */
public class MetaEditor {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Regex для shorthand: ИмяРеквизита: Тип(параметры) | флаги >> after/before Имя */
    private static final Pattern ATTR_SHORT = Pattern.compile(
            "^([\\p{L}_]\\w*)\\s*:\\s*(.+?)(?:\\s*\\|\\s*(.+?))?(?:\\s*>>\\s*after\\s+(\\S+)|\\s*<<\\s*before\\s+(\\S+))?$"
    );

    /** Русские синонимы типов */
    private static final Map<String, String> RU_TYPE_SYNONYMS = new LinkedHashMap<>();
    static {
        RU_TYPE_SYNONYMS.put("Число", "Number");
        RU_TYPE_SYNONYMS.put("Строка", "String");
        RU_TYPE_SYNONYMS.put("Булево", "Boolean");
        RU_TYPE_SYNONYMS.put("Дата", "Date");
        RU_TYPE_SYNONYMS.put("ДатаВремя", "DateTime");
        RU_TYPE_SYNONYMS.put("ХранилищеЗначения", "ValueStorage");
        RU_TYPE_SYNONYMS.put("СправочникСсылка", "CatalogRef");
        RU_TYPE_SYNONYMS.put("ДокументСсылка", "DocumentRef");
        RU_TYPE_SYNONYMS.put("ПеречислениеСсылка", "EnumRef");
        RU_TYPE_SYNONYMS.put("ПланСчетовСсылка", "ChartOfAccountsRef");
        RU_TYPE_SYNONYMS.put("ПланВидовХарактеристикСсылка", "ChartOfCharacteristicTypesRef");
        RU_TYPE_SYNONYMS.put("ПланВидовРасчётаСсылка", "ChartOfCalculationTypesRef");
        RU_TYPE_SYNONYMS.put("ПланВидовРасчетаСсылка", "ChartOfCalculationTypesRef");
        RU_TYPE_SYNONYMS.put("ПланОбменаСсылка", "ExchangePlanRef");
        RU_TYPE_SYNONYMS.put("БизнесПроцессСсылка", "BusinessProcessRef");
        RU_TYPE_SYNONYMS.put("ЗадачаСсылка", "TaskRef");
        RU_TYPE_SYNONYMS.put("ОпределяемыйТип", "DefinedType");
    }

    /** Канонический порядок дочерних элементов в ChildObjects */
    private static final List<String> CHILD_ORDER = List.of(
            "Resource", "Dimension", "Attribute", "TabularSection",
            "AccountingFlag", "ExtDimensionAccountingFlag",
            "EnumValue", "Column", "AddressingAttribute", "Recalculation",
            "Form", "Template", "Command"
    );

    private final PrintStream out;
    private int addCount;
    private int removeCount;
    private int modifyCount;
    private int warnCount;

    public MetaEditor() { this(System.out); }
    public MetaEditor(PrintStream out) { this.out = out; }

    // ─── Main entry point ───────────────────────────────────────────────

    /**
     * Выполнить операцию над XML-файлом объекта метаданных.
     *
     * @param objectPath путь к XML-файлу объекта (или директория — тогда ищет Name/Name.xml)
     * @param operation  операция (add-attribute, remove-ts, modify-attribute, etc.)
     * @param value      значение операции (shorthand-строка, batch через ;;)
     */
    public void edit(Path objectPath, String operation, String value) throws IOException {
        Path xmlPath = resolveObjectPath(objectPath);
        String content = readFileContent(xmlPath);

        addCount = 0;
        removeCount = 0;
        modifyCount = 0;
        warnCount = 0;

        // Detect object type
        String objType = detectObjectType(content);
        String objName = detectObjectName(content);
        out.println("[INFO] Object: " + objType + "." + objName);

        // TASK-171 D-1: предопределённые элементы живут не в XML объекта, а в
        // отдельном Ext/Predefined.xml — обрабатываем до общего content-конвейера.
        if ("add-predefined".equals(operation)) {
            addPredefinedItems(xmlPath, objType, value);
            return;
        }
        if ("add-exchange-content".equals(operation)) {
            addExchangePlanContent(xmlPath, objType, value);
            return;
        }

        // TASK-171: форма и шаблон НЕ сериализуются inline в ChildObjects — это
        // приводило к форме-фантому (полный <Form uuid><Properties>...</Form> без
        // внешних файлов), на которой платформа уходила в runaway памяти при
        // LoadConfigFromFiles. Каноничная сериализация — текст-ссылка
        // <Form>Имя</Form> + внешние файлы Forms/Имя.xml, Forms/Имя/Ext/Form.xml,
        // Forms/Имя/Ext/Form/Module.bsl. Обрабатываем до общего content-конвейера,
        // потому что нужен доступ к ФС (как у add-predefined).
        if ("add-form".equals(operation)) {
            addFormWithFiles(xmlPath, content, objType, objName, value);
            return;
        }
        if ("add-template".equals(operation)) {
            addTemplateWithFiles(xmlPath, content, objType, objName, value);
            return;
        }

        // Parse and execute operation
        String[] opParts = operation.split("-", 2);
        if (opParts.length != 2) {
            throw new IllegalArgumentException("Invalid operation format: " + operation
                    + ". Expected: add-attribute, remove-ts, modify-dimension, etc.");
        }
        String action = opParts[0];   // add, remove, modify
        String target = opParts[1];   // attribute, ts, dimension, etc.

        // Batch split by ;;
        String[] items = value.split(";;");

        String result = content;
        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;
            result = executeOperation(result, objType, objName, action, target, trimmed);
        }

        // Save
        if (addCount + removeCount + modifyCount > 0) {
            writeFileWithBom(xmlPath, result);
            out.println("[INFO] Saved: " + xmlPath);
        }

        // Summary
        out.println();
        out.println("=== meta-edit summary ===");
        out.println("  Object:   " + objType + "." + objName);
        out.println("  Added:    " + addCount);
        out.println("  Removed:  " + removeCount);
        out.println("  Modified: " + modifyCount);
        if (warnCount > 0) out.println("  Warnings: " + warnCount);
        if (addCount + removeCount + modifyCount == 0) out.println("  No changes applied.");
    }

    // ─── Operation dispatcher ───────────────────────────────────────────

    private String executeOperation(String content, String objType, String objName,
                                    String action, String target, String value) {
        return switch (action) {
            case "add" -> executeAdd(content, objType, objName, target, value);
            case "remove" -> executeRemove(content, objType, target, value);
            case "modify" -> executeModify(content, objType, target, value);
            default -> {
                warn("Unknown action: " + action);
                yield content;
            }
        };
    }

    // ─── ADD operations ─────────────────────────────────────────────────

    private String executeAdd(String content, String objType, String objName,
                              String target, String value) {
        return switch (target) {
            case "attribute" -> addChildElement(content, objType, objName, "Attribute", value);
            case "dimension" -> addChildElement(content, objType, objName, "Dimension", value);
            case "resource" -> addChildElement(content, objType, objName, "Resource", value);
            case "enumValue" -> addEnumValue(content, objName, value);
            case "column" -> addColumn(content, objName, value);
            case "ts" -> addTabularSection(content, objType, objName, value);
            // TASK-171: add-form / add-template обрабатываются в edit() до этого
            // диспетчера (нужен доступ к ФС для внешних файлов). Сюда они попасть
            // не должны; inline-сериализация Form/Template запрещена (форма-фантом).
            case "form" -> throw new IllegalStateException(
                    "add-form должна обрабатываться через addFormWithFiles (внешние файлы), "
                    + "inline-форма запрещена");
            case "template" -> throw new IllegalStateException(
                    "add-template должна обрабатываться через addTemplateWithFiles (внешние файлы), "
                    + "inline-шаблон запрещён");
            case "command" -> addSimpleChild(content, "Command", value);
            case "ts-attribute" -> addTsAttribute(content, objType, value);
            case "property" -> addOrSetProperty(content, value);
            default -> {
                warn("Unknown add target: " + target);
                yield content;
            }
        };
    }

    /**
     * Add Attribute, Dimension, or Resource — full XML fragment with properties.
     */
    private String addChildElement(String content, String objType, String objName,
                                   String xmlTag, String shorthand) {
        AttrDef def = parseShorthand(shorthand);
        if (def.name.isEmpty()) { warn("Empty element name"); return content; }

        // Check duplicate (rootOnly to avoid matching TS-nested attributes)
        if (findChildByName(content, xmlTag, def.name, true) >= 0) {
            warn(xmlTag + " '" + def.name + "' already exists, skipping");
            return content;
        }

        String indent = "\t\t\t";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<").append(xmlTag).append(" uuid=\"").append(uuid()).append("\">\n");
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(def.name)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", def.synonym != null ? def.synonym : splitCamelCase(def.name)); //**agent TASK-174 [05.06.2026 12:42:00] XG-09: синоним из пайп-токена
        sb.append(indent).append("\t\t<Comment/>\n");

        // Type
        writeTypeBlock(sb, indent + "\t\t", def.types);

        // Standard attribute properties
        sb.append(indent).append("\t\t<PasswordMode>false</PasswordMode>\n");
        sb.append(indent).append("\t\t<Format/>\n");
        sb.append(indent).append("\t\t<EditFormat/>\n");
        sb.append(indent).append("\t\t<ToolTip/>\n");
        sb.append(indent).append("\t\t<MarkNegatives>false</MarkNegatives>\n");
        sb.append(indent).append("\t\t<Mask/>\n");
        sb.append(indent).append("\t\t<MultiLine>false</MultiLine>\n");
        sb.append(indent).append("\t\t<ExtendedEdit>false</ExtendedEdit>\n");

        // MinValue (nonneg support)
        boolean nonneg = def.flags.contains("nonneg");
        if (nonneg) {
            sb.append(indent).append("\t\t<MinValue>\n");
            sb.append(indent).append("\t\t\t<v8:Type>xs:decimal</v8:Type>\n");
            sb.append(indent).append("\t\t\t<v8:Value>0</v8:Value>\n");
            sb.append(indent).append("\t\t</MinValue>\n");
        } else {
            sb.append(indent).append("\t\t<MinValue xsi:nil=\"true\"/>\n");
        }
        sb.append(indent).append("\t\t<MaxValue xsi:nil=\"true\"/>\n");

        // FillFromFillingValue / FillValue — for non-register attributes
        boolean isRegister = MetadataTypeRegistry.isRegister(objType);
        if (!isRegister && "Attribute".equals(xmlTag)) {
            sb.append(indent).append("\t\t<FillFromFillingValue>true</FillFromFillingValue>\n");
            sb.append(indent).append("\t\t<FillValue xsi:nil=\"true\"/>\n");
        }
        if (isRegister && "Dimension".equals(xmlTag) && "InformationRegister".equals(objType)) {
            boolean master = def.flags.contains("master");
            sb.append(indent).append("\t\t<FillFromFillingValue>").append(master).append("</FillFromFillingValue>\n");
            sb.append(indent).append("\t\t<FillValue xsi:nil=\"true\"/>\n");
        }
        if (isRegister && "Resource".equals(xmlTag) && "InformationRegister".equals(objType)) {
            sb.append(indent).append("\t\t<FillFromFillingValue>false</FillFromFillingValue>\n");
            sb.append(indent).append("\t\t<FillValue xsi:nil=\"true\"/>\n");
        }

        // FillChecking
        String fillChecking = def.flags.contains("req") ? "ShowError" : "DontCheck";
        sb.append(indent).append("\t\t<FillChecking>").append(fillChecking).append("</FillChecking>\n");

        //++agent TASK-174 [07.06.2026 12:00:00]
        // Порт-аудит: ChoiceFoldersAndItems был опущен при переносе — спека (1c-config-objects-spec §6.1)
        // и грунт-труф Designer 2.20 пишут его МЕЖДУ FillChecking и ChoiceParameterLinks
        // для ВСЕХ реквизитов/измерений/ресурсов (даже примитивных типов).
        sb.append(indent).append("\t\t<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>\n");
        //++agent TASK-174

        // ChoiceParameterLinks etc
        sb.append(indent).append("\t\t<ChoiceParameterLinks/>\n");
        sb.append(indent).append("\t\t<ChoiceParameters/>\n");
        sb.append(indent).append("\t\t<QuickChoice>Auto</QuickChoice>\n");
        sb.append(indent).append("\t\t<CreateOnInput>Auto</CreateOnInput>\n");
        sb.append(indent).append("\t\t<ChoiceForm/>\n");
        sb.append(indent).append("\t\t<LinkByType/>\n");
        sb.append(indent).append("\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>\n");

        // Dimension-specific properties
        if ("Dimension".equals(xmlTag)) {
            if ("InformationRegister".equals(objType)) {
                sb.append(indent).append("\t\t<Master>").append(def.flags.contains("master")).append("</Master>\n");
                sb.append(indent).append("\t\t<MainFilter>").append(def.flags.contains("mainfilter")).append("</MainFilter>\n");
                sb.append(indent).append("\t\t<DenyIncompleteValues>").append(def.flags.contains("denyincomplete")).append("</DenyIncompleteValues>\n");
            }
            if ("AccumulationRegister".equals(objType)) {
                sb.append(indent).append("\t\t<DenyIncompleteValues>").append(def.flags.contains("denyincomplete")).append("</DenyIncompleteValues>\n");
            }
            //++agent TASK-174 [07.06.2026 12:00:00]
            // Порт-аудит: для бух./расчётных регистров измерения имеют СВОИ специфичные
            // узлы (грунт-труф _ДемоЖурналПроводок*/_ДемоОсновныеНачисления):
            // AcctReg: Balance → AccountingFlag → DenyIncompleteValues;
            // CalcReg: DenyIncompleteValues → BaseDimension → ScheduleLink.
            // Раньше эти узлы не эмитились вовсе.
            if ("AccountingRegister".equals(objType)) {
                sb.append(indent).append("\t\t<Balance>").append(def.flags.contains("balance")).append("</Balance>\n");
                sb.append(indent).append("\t\t<AccountingFlag/>\n");
                sb.append(indent).append("\t\t<DenyIncompleteValues>").append(def.flags.contains("denyincomplete")).append("</DenyIncompleteValues>\n");
            }
            if ("CalculationRegister".equals(objType)) {
                sb.append(indent).append("\t\t<DenyIncompleteValues>").append(def.flags.contains("denyincomplete")).append("</DenyIncompleteValues>\n");
                sb.append(indent).append("\t\t<BaseDimension>").append(def.flags.contains("base")).append("</BaseDimension>\n");
                sb.append(indent).append("\t\t<ScheduleLink/>\n");
            }
            //++agent TASK-174
        }

        // Indexing
        String indexing = "DontIndex";
        if (def.flags.contains("index")) indexing = "Index";
        if (def.flags.contains("indexadditional")) indexing = "IndexWithAdditionalOrder";
        sb.append(indent).append("\t\t<Indexing>").append(indexing).append("</Indexing>\n");
        sb.append(indent).append("\t\t<FullTextSearch>Use</FullTextSearch>\n");

        // UseInTotals for AccumulationRegister dimensions
        if ("Dimension".equals(xmlTag) && "AccumulationRegister".equals(objType)) {
            boolean useInTotals = !def.flags.contains("nouseintotals");
            sb.append(indent).append("\t\t<UseInTotals>").append(useInTotals).append("</UseInTotals>\n");
        }

        //**agent TASK-174 [07.06.2026 12:00:00]
        // Порт-аудит: DataHistory писался безусловно, но по грунт-труфу 2.20 у измерений/
        // ресурсов Accumulation/Accounting/CalculationRegister элемента DataHistory НЕТ
        // (есть только у InformationRegister и нерегистровых объектов). Лишний узел —
        // риск XSD-отказа при full-load (тот же класс, что Master/MainFilter у AccumReg).
        //sb.append(indent).append("\t\t<DataHistory>Use</DataHistory>\n");
        if (!isRegister || "InformationRegister".equals(objType)) {
            sb.append(indent).append("\t\t<DataHistory>Use</DataHistory>\n");
        }
        //**agent TASK-174

        sb.append(indent).append("\t</Properties>\n");
        sb.append(indent).append("</").append(xmlTag).append(">");

        String fragment = sb.toString();
        String result = insertIntoChildObjects(content, xmlTag, fragment, def.after, def.before);
        if (result != null) {
            info("Added " + xmlTag.toLowerCase() + ": " + def.name);
            addCount++;
            return result;
        }
        return content;
    }

    /**
     * Добавить предопределённые элементы в {@code <Объект>/Ext/Predefined.xml}
     * (TASK-171 D-1). Файл создаётся, если его нет, иначе элементы дописываются.
     *
     * <p>Shorthand одного элемента (батч через {@code ;;}):
     * {@code Имя[|Описание[|Код[|folder]]]}. Код по умолчанию — авто-нумерация
     * (max существующего + 1), дополненная нулями до длины кода (по умолчанию 9).
     * Версия формата файла берётся из {@code Configuration.xml} (D-6).
     */
    private void addPredefinedItems(Path xmlPath, String objType, String value) throws IOException {
        String xsiType = PredefinedXmlWriter.xsiTypeFor(objType);
        if (xsiType == null) {
            throw new IllegalArgumentException("Тип " + objType
                    + " не поддерживает предопределённые элементы. "
                    + "Поддерживаются: Catalog, ChartOfCharacteristicTypes, "
                    + "ChartOfAccounts, ChartOfCalculationTypes.");
        }

        // Ext-каталог объекта на диске называется как файл (без .xml).
        String fileName = xmlPath.getFileName().toString();
        String fileBase = fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path extDir = xmlPath.getParent().resolve(fileBase).resolve("Ext");
        Path predefinedFile = extDir.resolve("Predefined.xml");

        // Версия формата из Configuration.xml (на 2 уровня вверх: Catalogs/<N>.xml → xml/).
        Path configRoot = xmlPath.getParent() != null ? xmlPath.getParent().getParent() : null;
        Path configurationXml = configRoot != null
                ? configRoot.resolve("Configuration.xml")
                : Paths.get("Configuration.xml");
        String formatVersion = ConfigurationXmlReader.readFormatVersion(configurationXml);

        boolean exists = Files.isRegularFile(predefinedFile);
        String content = exists ? readFileContent(predefinedFile) : null;

        int codeWidth = exists
                ? PredefinedXmlWriter.detectCodeWidth(content, PredefinedXmlWriter.DEFAULT_CODE_WIDTH)
                : PredefinedXmlWriter.DEFAULT_CODE_WIDTH;
        int nextCode = exists ? PredefinedXmlWriter.nextCodeNumber(content) : 1;

        List<PredefinedXmlWriter.Item> newItems = value.startsWith("@")
                ? readPredefinedItemsJson(value.substring(1))
                : readPredefinedItemsShorthand(value, exists, content, nextCode, codeWidth);
        addCount += newItems.size();

        if (newItems.isEmpty()) {
            out.println("[INFO] No predefined items added (all duplicates or empty).");
            return;
        }

        Files.createDirectories(extDir);
        String result;
        if (exists) {
            result = content;
            for (PredefinedXmlWriter.Item it : newItems) {
                result = PredefinedXmlWriter.appendItem(result, it);
            }
        } else {
            result = PredefinedXmlWriter.buildFile(xsiType, formatVersion, newItems);
        }
        writeFileWithBom(predefinedFile, result);
        out.println("[INFO] Saved: " + predefinedFile);
        out.println();
        out.println("=== meta-edit summary ===");
        out.println("  Predefined added: " + addCount);
    }

    private List<PredefinedXmlWriter.Item> readPredefinedItemsShorthand(String value, boolean exists,
                                                                        String content, int nextCode, int codeWidth) {
        List<PredefinedXmlWriter.Item> items = new ArrayList<>();
        for (String raw : value.split(";;")) {
            String item = raw.trim();
            if (item.isEmpty()) continue;
            String[] parts = item.split("\\|", -1);
            String name = parts[0].trim();
            if (name.isEmpty()) continue;
            if (exists && findPredefinedByName(content, name)) {
                warn("Predefined '" + name + "' already exists, skipping");
                continue;
            }
            String description = parts.length > 1 && !parts[1].trim().isEmpty()
                    ? parts[1].trim() : name;
            String code = parts.length > 2
                    ? parts[2].trim() : PredefinedXmlWriter.formatCode(nextCode++, codeWidth);
            boolean isFolder = parts.length > 3 && "folder".equalsIgnoreCase(parts[3].trim());
            items.add(new PredefinedXmlWriter.Item(name, code, description, isFolder));
        }
        return items;
    }

    private List<PredefinedXmlWriter.Item> readPredefinedItemsJson(String fileName) throws IOException {
        Path jsonPath = Paths.get(fileName);
        if (!Files.isRegularFile(jsonPath)) {
            throw new IllegalArgumentException("Predefined JSON file not found: " + jsonPath);
        }
        JsonNode root = new ObjectMapper().readTree(jsonPath.toFile());
        JsonNode itemsNode = root.isArray() ? root : root.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new IllegalArgumentException("Predefined JSON must be an array or object with items[]: " + jsonPath);
        }
        List<PredefinedXmlWriter.Item> items = new ArrayList<>();
        for (JsonNode itemNode : itemsNode) {
            items.add(readPredefinedItemJson(itemNode));
        }
        return items;
    }

    private PredefinedXmlWriter.Item readPredefinedItemJson(JsonNode node) {
        String name = text(node, "name", "");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Predefined JSON item requires non-empty name");
        }
        List<PredefinedXmlWriter.Item> childItems = new ArrayList<>();
        JsonNode children = node.get("childItems");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                childItems.add(readPredefinedItemJson(child));
            }
        }
        List<String> types = stringList(node.get("types"));
        Map<String, Boolean> accountingFlags = booleanMap(node.get("accountingFlags"));
        List<PredefinedXmlWriter.ExtDimensionType> extDimensionTypes = new ArrayList<>();
        JsonNode extDims = node.get("extDimensionTypes");
        if (extDims != null && extDims.isArray()) {
            for (JsonNode extDim : extDims) {
                extDimensionTypes.add(new PredefinedXmlWriter.ExtDimensionType(
                        text(extDim, "name", ""),
                        bool(extDim, "turnover", false),
                        booleanMap(extDim.get("accountingFlags"))));
            }
        }
        return new PredefinedXmlWriter.Item(
                name,
                text(node, "code", ""),
                text(node, "description", name),
                bool(node, "isFolder", false),
                childItems,
                types,
                nullableText(node, "accountType"),
                nullableBool(node, "offBalance"),
                nullableText(node, "order"),
                accountingFlags,
                extDimensionTypes,
                nullableBool(node, "actionPeriodIsBase"),
                stringList(node.get("displaced")));
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
    }

    private Map<String, Boolean> booleanMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Boolean> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asBoolean()));
        return values;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asBoolean();
    }

    private Boolean nullableBool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    /** Есть ли в Predefined.xml элемент с таким {@code <Name>}. */
    private boolean findPredefinedByName(String content, String name) {
        Matcher m = Pattern.compile("<Name>([^<]*)</Name>").matcher(content);
        while (m.find()) {
            if (m.group(1).trim().equals(name)) return true;
        }
        return false;
    }

    private void addExchangePlanContent(Path xmlPath, String objType, String value) throws IOException {
        if (!"ExchangePlan".equals(objType)) {
            throw new IllegalArgumentException("add-exchange-content supports only ExchangePlan objects");
        }

        String fileName = xmlPath.getFileName().toString();
        String fileBase = fileName.endsWith(".xml")
                ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path extDir = xmlPath.getParent().resolve(fileBase).resolve("Ext");
        Path contentFile = extDir.resolve("Content.xml");

        Path configRoot = xmlPath.getParent() != null ? xmlPath.getParent().getParent() : null;
        Path configurationXml = configRoot != null
                ? configRoot.resolve("Configuration.xml")
                : Paths.get("Configuration.xml");
        String formatVersion = ConfigurationXmlReader.readFormatVersion(configurationXml);

        boolean exists = Files.isRegularFile(contentFile);
        String content = exists ? readFileContent(contentFile) : null;
        List<ExchangePlanContentWriter.Item> items = value.startsWith("@")
                ? readExchangeContentJson(value.substring(1))
                : readExchangeContentShorthand(value);
        if (items.isEmpty()) {
            out.println("[INFO] No exchange content items added.");
            return;
        }

        Files.createDirectories(extDir);
        String result = exists && content.contains("</ExchangePlanContent>")
                ? content : ExchangePlanContentWriter.buildFile(formatVersion, List.of());
        for (ExchangePlanContentWriter.Item item : items) {
            result = ExchangePlanContentWriter.appendItem(result, item);
        }
        writeFileWithBom(contentFile, result);
        out.println("[INFO] Saved: " + contentFile);
        out.println();
        out.println("=== meta-edit summary ===");
        out.println("  Exchange content added: " + items.size());
    }

    private List<ExchangePlanContentWriter.Item> readExchangeContentShorthand(String value) {
        List<ExchangePlanContentWriter.Item> items = new ArrayList<>();
        for (String raw : value.split(";;")) {
            String item = raw.trim();
            if (item.isEmpty()) continue;
            String[] parts = item.split("\\|", -1);
            String metadata = parts[0].trim();
            if (metadata.isEmpty()) continue;
            String autoRecord = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : "Deny";
            items.add(new ExchangePlanContentWriter.Item(metadata, autoRecord));
        }
        return items;
    }

    private List<ExchangePlanContentWriter.Item> readExchangeContentJson(String fileName) throws IOException {
        Path jsonPath = Paths.get(fileName);
        if (!Files.isRegularFile(jsonPath)) {
            throw new IllegalArgumentException("Exchange content JSON file not found: " + jsonPath);
        }
        JsonNode root = new ObjectMapper().readTree(jsonPath.toFile());
        JsonNode itemsNode = root.isArray() ? root : root.get("items");
        if (itemsNode == null || !itemsNode.isArray()) {
            throw new IllegalArgumentException("Exchange content JSON must be an array or object with items[]: "
                    + jsonPath);
        }
        List<ExchangePlanContentWriter.Item> items = new ArrayList<>();
        for (JsonNode item : itemsNode) {
            String metadata = text(item, "metadata", "");
            if (metadata.isBlank()) {
                throw new IllegalArgumentException("Exchange content item requires non-empty metadata");
            }
            items.add(new ExchangePlanContentWriter.Item(metadata, text(item, "autoRecord", "Deny")));
        }
        return items;
    }

    // ─── TASK-171: каноничное добавление формы (внешние файлы) ──────────────

    /**
     * Каноничный заголовок метаобъекта-обёртки формы (namespace-шапка как у
     * типовых объектов конфигурации; {@code version} подставляется из родителя).
     * Взят из эталона big_Order_OKX/Forms/ФормаДокумента.xml.
     */
    private static final String META_NS_HEADER =
            "xmlns=\"http://v8.1c.ru/8.3/MDClasses\""
            + " xmlns:app=\"http://v8.1c.ru/8.2/managed-application/core\""
            + " xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\""
            + " xmlns:cmi=\"http://v8.1c.ru/8.2/managed-application/cmi\""
            + " xmlns:ent=\"http://v8.1c.ru/8.1/data/enterprise\""
            + " xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\""
            + " xmlns:style=\"http://v8.1c.ru/8.1/data/ui/style\""
            + " xmlns:sys=\"http://v8.1c.ru/8.1/data/ui/fonts/system\""
            + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\""
            + " xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\""
            + " xmlns:web=\"http://v8.1c.ru/8.1/data/ui/colors/web\""
            + " xmlns:win=\"http://v8.1c.ru/8.1/data/ui/colors/windows\""
            + " xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\""
            + " xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\""
            + " xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\""
            + " xmlns:xs=\"http://www.w3.org/2001/XMLSchema\""
            + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

    /**
     * Минимальный модуль формы — посадка на подсистему «Подключаемые команды»
     * БСП (как у эталона big_Order_OKX/.../Module.bsl). Без BOM (как FormWriter).
     */
    private static final String DEFAULT_FORM_MODULE =
            "\n\n\n"
            + "&НаСервере\n"
            + "Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)\n"
            + "\tПодключаемыеКоманды.ПриСозданииНаСервере(ЭтотОбъект);\n"
            + "КонецПроцедуры\n";

    /**
     * Добавить форму каноничным образом (TASK-171): внешние файлы формы +
     * текст-ссылка в ChildObjects родителя.
     *
     * <p>Создаёт:
     * <ul>
     *   <li>{@code Forms/<Имя>/Ext/Form.xml} — UI-описание (через FormFromObjectGenerator);</li>
     *   <li>{@code Forms/<Имя>.xml} — обёртка-метаобъект {@code <Form>} с FormType=Managed;</li>
     *   <li>{@code Forms/<Имя>/Ext/Form/Module.bsl} — минимальный модуль формы;</li>
     * </ul>
     * и вставляет {@code <Form>Имя</Form>} в {@code <ChildObjects>}.
     *
     * <p>Опционально: значение вида {@code "Имя|Ordinary"} задаёт FormType=Ordinary
     * (по умолчанию Managed).
     */
    private void addFormWithFiles(Path xmlPath, String content, String objType,
                                  String objName, String value) throws IOException {
        // value может нести суффиксы через '|': FormType (Managed|Ordinary) и/или
        // флаг default (принудительная установка формы умолчанием), в любом порядке.
        // Примеры: "ФормаДокумента", "ФормаСписка|Ordinary", "ФормаДокумента|default",
        //          "ФормаДокумента|Managed|default".
        String[] parts = value.trim().split("\\|");
        String formName = parts[0].trim();
        String formType = "Managed";
        boolean forceDefault = false;
        for (int i = 1; i < parts.length; i++) {
            String token = parts[i].trim();
            if (token.isEmpty()) continue;
            if (token.equalsIgnoreCase("Ordinary")) formType = "Ordinary";
            else if (token.equalsIgnoreCase("Managed")) formType = "Managed";
            else if (token.equalsIgnoreCase("default")) forceDefault = true;
            else warn("Неизвестный суффикс формы '" + token + "', игнорирую");
        }
        if (formName.isEmpty()) { warn("Пустое имя формы"); return; }

        // Дубликат — текст-ссылка <Form>Имя</Form> или inline-форма уже есть.
        if (findFormReference(content, formName) || findChildByName(content, "Form", formName) >= 0) {
            warn("Form '" + formName + "' already exists, skipping");
            return;
        }

        // Каталог объекта = <parentDir>/<objName> (например Documents/биг_ВозвратАктивов).
        String fileBase = baseName(xmlPath);
        Path objectDir = xmlPath.getParent().resolve(fileBase);
        Path formsDir = objectDir.resolve("Forms");
        Path formExtXml = formsDir.resolve(formName).resolve("Ext").resolve("Form.xml");
        Path wrapperXml = formsDir.resolve(formName + ".xml");
        Path moduleBsl = formsDir.resolve(formName).resolve("Ext").resolve("Form").resolve("Module.bsl");

        boolean managedForm = !"Ordinary".equals(formType);

        if (managedForm) {
            // 1) UI-описание Ext/Form.xml через существующую машинерию form --from-object.
            //    Purpose выводится из имени папки формы (PurposeResolver).
            try {
                Files.createDirectories(formExtXml.getParent());
                FormDsl dsl = new FormFromObjectGenerator()
                        .generate(xmlPath, formExtXml, "erp-standard", null);
                new FormWriter(OutputFormat.DESIGNER).create(dsl, formExtXml);
            } catch (Exception e) {
                // Не оставляем битых файлов: чистим то, что успели создать.
                cleanupFormFiles(formsDir, formName);
                throw new IOException("Не удалось сгенерировать Ext/Form.xml для формы '"
                        + formName + "': " + e.getMessage(), e);
            }
        }

        // 2) Обёртка-метаобъект Forms/<Имя>.xml (FormType=Managed по умолчанию).
        String version = detectMetaVersion(content);
        Files.createDirectories(formsDir);
        writeFileWithBom(wrapperXml, buildFormWrapper(formName, formType, version));
        out.println("[INFO] Создан: " + wrapperXml);

        //**agent TASK-172 [02.06.2026 07:18:00]
        // 3) Минимальный модуль формы. Канон Designer (_Демо): ВСЕ .bsl c BOM + CRLF
        // (эталон CommonForms/.../Ext/Form/Module.bsl: ef bb bf + CRLF). Прежняя посылка
        // «без BOM, как FormWriter» была ошибочной — FormWriter сам .bsl не пишет.
        if (managedForm) {
            Files.createDirectories(moduleBsl.getParent());
            Files.write(moduleBsl, io.github.onec.xmlgen.io.Crlf.withBom(DEFAULT_FORM_MODULE));
            //**agent TASK-172
            out.println("[INFO] Создан: " + moduleBsl);
            out.println("[INFO] Создан: " + formExtXml);
        } else {
            out.println("[INFO] Ordinary form: Ext/Form.xml and Module.bsl are not created");
        }

        // 4) Текст-ссылка <Form>Имя</Form> в ChildObjects родителя.
        String reference = "\t\t\t<Form>" + esc(formName) + "</Form>";
        String result = insertIntoChildObjects(content, "Form", reference, null, null);
        if (result == null) {
            cleanupFormFiles(formsDir, formName);
            throw new IOException("Не найден <ChildObjects> в " + xmlPath
                    + " — форма-ссылка не добавлена, файлы откатаны");
        }
        addCount++;
        out.println("[INFO] Добавлена форма (текст-ссылка): " + formName);

        // TASK-171: установка Default*Form. Семантика — РОВНО ОДНА форма-умолчание
        // на purpose-тип. Первая форма данного типа → умолчание; последующие
        // умолчание не перебивают (если нет флага |default).
        result = setDefaultFormProperty(result, objType, objName, formName, forceDefault);

        writeFileWithBom(xmlPath, result);
        out.println("[INFO] Saved: " + xmlPath);
        out.println();
        out.println("=== meta-edit summary ===");
        out.println("  Object:   " + objType + "." + objName);
        out.println("  Form added: " + formName + " (FormType=" + formType + ")");
    }

    /** Purpose → имя тега Default*Form. */
    private static final Map<String, String> DEFAULT_FORM_TAG = Map.of(
            "item", "DefaultObjectForm",
            "list", "DefaultListForm",
            "choice", "DefaultChoiceForm",
            "folder", "DefaultFolderForm",
            "record", "DefaultRecordForm"
    );

    /**
     * Канонический порядок блока Default-форм и Auxiliary-форм по типу объекта.
     * Используется ТОЛЬКО когда блок целиком отсутствует (неполный Properties от
     * meta compile) и нужно вставить его на схемно-правильную позицию.
     * Порядок схемно значим (xs:sequence): вне позиции = ошибка загрузки 1С.
     */
    private static final Map<String, List<String>> DEFAULT_FORM_BLOCK = Map.of(
            "Document", List.of(
                    "DefaultObjectForm", "DefaultListForm", "DefaultChoiceForm",
                    "AuxiliaryObjectForm", "AuxiliaryListForm", "AuxiliaryChoiceForm"),
            "Catalog", List.of(
                    "DefaultObjectForm", "DefaultFolderForm", "DefaultListForm",
                    "DefaultChoiceForm", "DefaultFolderChoiceForm",
                    "AuxiliaryObjectForm", "AuxiliaryFolderForm", "AuxiliaryListForm",
                    "AuxiliaryChoiceForm", "AuxiliaryFolderChoiceForm"),
            "InformationRegister", List.of(
                    "DefaultRecordForm", "DefaultListForm",
                    "AuxiliaryRecordForm", "AuxiliaryListForm")
    );

    /**
     * Якорь — свойство, ПЕРЕД которым вставляется блок Default*Form, если его нет.
     * Эталоны позиций: Document — перед {@code <Posting>}
     * (биг_ПринятиеАктивовПодУправление.xml); Catalog — перед {@code <BasedOn>}
     * (запасной {@code <DataLockControlMode>}); InformationRegister — перед
     * {@code <StandardAttributes>} (_ДемоЗагружаемыеПоступленияОтЮридическихЛиц.xml).
     */
    private static final Map<String, List<String>> DEFAULT_FORM_ANCHOR = Map.of(
            "Document", List.of("Posting"),
            "Catalog", List.of("BasedOn", "DataLockControlMode"),
            // В эталоне _Демо… блок Default*Form стоит ПЕРЕД <StandardAttributes>.
            // У неполного meta-compile StandardAttributes может отсутствовать —
            // тогда схемно-следующий якорь это <InformationRegisterPeriodicity>
            // (идёт сразу после блока StandardAttributes в xs:sequence).
            "InformationRegister", List.of("StandardAttributes", "InformationRegisterPeriodicity")
    );

    /**
     * Установить нужный {@code Default*Form} для только что добавленной формы
     * (TASK-171). Возвращает изменённый XML (или исходный, если установка
     * пропущена).
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>purpose формы определяется по имени через {@link PurposeResolver#matchPurpose};
     *       не распознан → пропуск (INFO);</li>
     *   <li>тег умолчания подбирается по purpose ({@link #DEFAULT_FORM_TAG});</li>
     *   <li>значение ссылки — {@code <objType>.<objName>.Form.<formName>};</li>
     *   <li>если тег ПРИСУТСТВУЕТ и ПУСТ — заполняется in-place (первая форма типа);</li>
     *   <li>если ПРИСУТСТВУЕТ и НЕПУСТ — без {@code forceDefault} пропуск (INFO),
     *       с {@code forceDefault} перезапись in-place;</li>
     *   <li>если ОТСУТСТВУЕТ — вставка канонического блока перед якорем
     *       ({@link #DEFAULT_FORM_ANCHOR}); якорь не найден / тип не поддержан →
     *       пропуск (INFO, безопасность важнее полноты).</li>
     * </ol>
     */
    private String setDefaultFormProperty(String content, String objType, String objName,
                                          String formName, boolean forceDefault) {
        String purpose = PurposeResolver.matchPurpose(formName);
        if (purpose == null) {
            info("Default*Form не установлен: purpose формы '" + formName
                    + "' не распознан по имени. Задайте умолчание вручную при необходимости.");
            return content;
        }
        String tag = DEFAULT_FORM_TAG.get(purpose);
        if (tag == null) {
            info("Default*Form не установлен: нет тега умолчания для purpose '" + purpose + "'.");
            return content;
        }
        String reference = objType + "." + objName + ".Form." + formName;

        // Случай 1: тег присутствует и пуст (<Tag/> или <Tag></Tag>).
        Pattern emptyTag = Pattern.compile("<" + Pattern.quote(tag) + "\\s*/>|<"
                + Pattern.quote(tag) + ">\\s*</" + Pattern.quote(tag) + ">");
        Matcher mEmpty = emptyTag.matcher(content);
        if (mEmpty.find()) {
            String replaced = content.substring(0, mEmpty.start())
                    + "<" + tag + ">" + esc(reference) + "</" + tag + ">"
                    + content.substring(mEmpty.end());
            info(tag + " установлен: " + reference);
            return replaced;
        }

        // Случай 2: тег присутствует и непуст.
        Pattern filledTag = Pattern.compile(
                "<" + Pattern.quote(tag) + ">([^<]+)</" + Pattern.quote(tag) + ">");
        Matcher mFilled = filledTag.matcher(content);
        if (mFilled.find()) {
            String existing = mFilled.group(1);
            if (forceDefault) {
                String replaced = content.substring(0, mFilled.start())
                        + "<" + tag + ">" + esc(reference) + "</" + tag + ">"
                        + content.substring(mFilled.end());
                info(tag + " перезаписан (флаг |default): " + existing + " -> " + reference);
                return replaced;
            }
            info(tag + " уже задан (" + existing
                    + "); новая форма добавлена как не-умолчание.");
            return content;
        }

        // Случай 3: тег (и весь блок Default*Form) отсутствует — вставка по якорю.
        List<String> block = DEFAULT_FORM_BLOCK.get(objType);
        List<String> anchors = DEFAULT_FORM_ANCHOR.get(objType);
        if (block == null || anchors == null) {
            info("Default*Form не установлен (нет безопасной позиции для типа "
                    + objType + "); задайте умолчание вручную.");
            return content;
        }
        // Найти первый существующий якорь.
        int anchorPos = -1;
        for (String anchor : anchors) {
            int p = indexOfRootProperty(content, anchor);
            if (p >= 0) { anchorPos = p; break; }
        }
        if (anchorPos < 0) {
            info("Default*Form не установлен (якорь не найден среди " + anchors
                    + "); задайте умолчание вручную.");
            return content;
        }
        // Отступ строки якоря (для совпадения с форматированием).
        int lineStart = content.lastIndexOf('\n', anchorPos - 1) + 1;
        String indent = content.substring(lineStart, anchorPos);

        StringBuilder sb = new StringBuilder();
        for (String t : block) {
            sb.append(indent);
            if (t.equals(tag)) {
                sb.append("<").append(t).append(">").append(esc(reference)).append("</").append(t).append(">");
            } else {
                sb.append("<").append(t).append("/>");
            }
            sb.append("\n");
        }
        String replaced = content.substring(0, lineStart) + sb + content.substring(lineStart);
        info(tag + " установлен (вставлен блок Default*Form перед <"
                + content.substring(anchorPos).split("[ >]", 2)[0].replace("<", "") + ">): " + reference);
        return replaced;
    }

    /**
     * Позиция открывающего тега корневого свойства объекта (не вложенного в
     * ChildObjects / StandardAttributes-реквизиты). Возвращает индекс {@code '<'}
     * первого вхождения {@code <Name>} или {@code <Name ...>} на верхнем уровне
     * Properties объекта, или -1.
     *
     * <p>Якоря (Posting/BasedOn/DataLockControlMode/StandardAttributes) уникальны
     * в корневом Properties и не встречаются раньше него, поэтому достаточно
     * первого вхождения.
     */
    private int indexOfRootProperty(String content, String name) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + "(?:\\s[^>]*)?(?:/>|>)").matcher(content);
        if (m.find()) return m.start();
        return -1;
    }

    /**
     * Добавить шаблон каноничным образом (TASK-171): текст-ссылка
     * {@code <Template>Имя</Template>} + минимальный внешний {@code Templates/Имя.xml}.
     *
     * <p>Контент-шаблон (Ext/Template.*) НЕ генерируется — создаётся только
     * валидная обёртка макета SpreadsheetDocument. Об этом сообщается в выводе.
     */
    private void addTemplateWithFiles(Path xmlPath, String content, String objType,
                                      String objName, String value) throws IOException {
        String[] parts = value.trim().split("\\|");
        String tplName = parts[0].trim();
        String templateType = "SpreadsheetDocument";
        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
            templateType = TemplateWriter.canonicalTemplateTypeName(parts[1].trim());
        }
        for (int i = 2; i < parts.length; i++) {
            String token = parts[i].trim();
            if (!token.isEmpty()) {
                warn("Неизвестный суффикс макета '" + token + "', игнорирую");
            }
        }
        if (tplName.isEmpty()) { warn("Пустое имя шаблона"); return; }

        if (findTemplateReference(content, tplName) || findChildByName(content, "Template", tplName) >= 0) {
            warn("Template '" + tplName + "' already exists, skipping");
            return;
        }

        String fileBase = baseName(xmlPath);
        Path objectDir = xmlPath.getParent().resolve(fileBase);
        Path templatesDir = objectDir.resolve("Templates");
        Path wrapperXml = templatesDir.resolve(tplName + ".xml");
        Path templateDir = templatesDir.resolve(tplName);

        String version = detectMetaVersion(content);
        if (Files.exists(wrapperXml) || Files.exists(templateDir)) {
            throw new IOException("Template '" + tplName + "' already exists on disk in " + templatesDir);
        }
        ObjectContainerEditor.createTemplateScaffold(objectDir, tplName, splitCamelCase(tplName), templateType,
                version);
        out.println("[INFO] Создан: " + wrapperXml);

        String reference = "\t\t\t<Template>" + esc(tplName) + "</Template>";
        String result = insertIntoChildObjects(content, "Template", reference, null, null);
        if (result == null) {
            cleanupTemplateFiles(templatesDir, tplName);
            throw new IOException("Не найден <ChildObjects> в " + xmlPath
                    + " — шаблон-ссылка не добавлена, файлы откатаны");
        }
        writeFileWithBom(xmlPath, result);
        addCount++;
        out.println("[INFO] Добавлен шаблон (текст-ссылка): " + tplName);
        out.println("[WARN] Контент-шаблон НЕ сгенерирован — создана только обёртка "
                + "макета. При необходимости заполните Ext/Template.* вручную.");
        out.println("[INFO] Saved: " + xmlPath);
        out.println();
        out.println("=== meta-edit summary ===");
        out.println("  Object:   " + objType + "." + objName);
        out.println("  Template added: " + tplName + " (TemplateType=" + templateType + ")");
    }

    /** Каноничная обёртка-метаобъект для формы. BOM добавляется при записи. */
    private String buildFormWrapper(String formName, String formType, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(META_NS_HEADER)
                .append(" version=\"").append(version).append("\">\n");
        sb.append("\t<Form uuid=\"").append(uuid()).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(esc(formName)).append("</Name>\n");
        writeSynonym(sb, "\t\t\t", splitCamelCase(formName));
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<FormType>").append(formType).append("</FormType>\n");
        sb.append("\t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>\n");
        sb.append("\t\t\t<UsePurposes>\n");
        sb.append("\t\t\t\t<v8:Value xsi:type=\"app:ApplicationUsePurpose\">PlatformApplication</v8:Value>\n");
        sb.append("\t\t\t\t<v8:Value xsi:type=\"app:ApplicationUsePurpose\">MobilePlatformApplication</v8:Value>\n");
        sb.append("\t\t\t</UsePurposes>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Form>\n");
        sb.append("</MetaDataObject>");
        return sb.toString();
    }

    /** Минимальная валидная обёртка макета (SpreadsheetDocument). */
    private String buildTemplateWrapper(String tplName, String version) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject ").append(META_NS_HEADER)
                .append(" version=\"").append(version).append("\">\n");
        sb.append("\t<Template uuid=\"").append(uuid()).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(esc(tplName)).append("</Name>\n");
        writeSynonym(sb, "\t\t\t", splitCamelCase(tplName));
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<TemplateType>SpreadsheetDocument</TemplateType>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t</Template>\n");
        sb.append("</MetaDataObject>");
        return sb.toString();
    }

    /** Прочитать {@code version="X.XX"} из родительского {@code <MetaDataObject>}. */
    private String detectMetaVersion(String content) {
        Matcher m = Pattern.compile("<MetaDataObject[^>]*\\bversion=\"([^\"]+)\"").matcher(content);
        if (m.find()) return m.group(1);
        return ConfigurationXmlReader.DEFAULT_FORMAT_VERSION; // 2.17 — разумный дефолт
    }

    /** Есть ли текст-ссылка {@code <Form>Имя</Form>} (не inline-блок). */
    private boolean findFormReference(String content, String name) {
        return content.contains("<Form>" + esc(name) + "</Form>");
    }

    /** Есть ли текст-ссылка {@code <Template>Имя</Template>}. */
    private boolean findTemplateReference(String content, String name) {
        return content.contains("<Template>" + esc(name) + "</Template>");
    }

    /** Имя файла без расширения {@code .xml}. */
    private static String baseName(Path xmlPath) {
        String fileName = xmlPath.getFileName().toString();
        return fileName.endsWith(".xml") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    /** Откат частично созданных файлов формы при ошибке. */
    private void cleanupFormFiles(Path formsDir, String formName) {
        try {
            Files.deleteIfExists(formsDir.resolve(formName + ".xml"));
            Path formDir = formsDir.resolve(formName);
            if (Files.exists(formDir)) {
                Files.walk(formDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }

    /** Откат частично созданных файлов макета при ошибке. */
    private void cleanupTemplateFiles(Path templatesDir, String templateName) {
        try {
            Files.deleteIfExists(templatesDir.resolve(templateName + ".xml"));
            Path templateDir = templatesDir.resolve(templateName);
            if (Files.exists(templateDir)) {
                Files.walk(templateDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
            }
        } catch (IOException ignored) {}
    }

    private String addEnumValue(String content, String objName, String value) {
        String name = value.trim();
        if (findChildByName(content, "EnumValue", name) >= 0) {
            warn("EnumValue '" + name + "' already exists, skipping");
            return content;
        }

        String indent = "\t\t\t";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<EnumValue uuid=\"").append(uuid()).append("\">\n");
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(name)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", splitCamelCase(name));
        sb.append(indent).append("\t\t<Comment/>\n");
        sb.append(indent).append("\t</Properties>\n");
        sb.append(indent).append("</EnumValue>");

        String result = insertIntoChildObjects(content, "EnumValue", sb.toString(), null, null);
        if (result != null) {
            info("Added enum value: " + name);
            addCount++;
            return result;
        }
        return content;
    }

    private String addColumn(String content, String objName, String value) {
        String name = value.trim();
        if (findChildByName(content, "Column", name) >= 0) {
            warn("Column '" + name + "' already exists, skipping");
            return content;
        }

        String indent = "\t\t\t";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<Column uuid=\"").append(uuid()).append("\">\n");
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(name)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", splitCamelCase(name));
        sb.append(indent).append("\t\t<Comment/>\n");
        sb.append(indent).append("\t\t<Indexing>DontIndex</Indexing>\n");
        sb.append(indent).append("\t\t<References/>\n");
        sb.append(indent).append("\t</Properties>\n");
        sb.append(indent).append("</Column>");

        String result = insertIntoChildObjects(content, "Column", sb.toString(), null, null);
        if (result != null) {
            info("Added column: " + name);
            addCount++;
            return result;
        }
        return content;
    }

    private String addSimpleChild(String content, String xmlTag, String value) {
        String name = value.trim();
        if (findChildByName(content, xmlTag, name) >= 0) {
            warn(xmlTag + " '" + name + "' already exists, skipping");
            return content;
        }

        String indent = "\t\t\t";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<").append(xmlTag).append(" uuid=\"").append(uuid()).append("\">\n");
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(name)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", splitCamelCase(name));
        sb.append(indent).append("\t\t<Comment/>\n");

        if ("Command".equals(xmlTag)) {
            sb.append(indent).append("\t\t<Group>FormNavigationPanelGoTo</Group>\n");
            sb.append(indent).append("\t\t<Representation>Auto</Representation>\n");
            sb.append(indent).append("\t\t<ToolTip/>\n");
            sb.append(indent).append("\t\t<Picture/>\n");
            sb.append(indent).append("\t\t<Shortcut/>\n");
        }

        sb.append(indent).append("\t</Properties>\n");
        sb.append(indent).append("</").append(xmlTag).append(">");

        String result = insertIntoChildObjects(content, xmlTag, sb.toString(), null, null);
        if (result != null) {
            info("Added " + xmlTag.toLowerCase() + ": " + name);
            addCount++;
            return result;
        }
        return content;
    }

    private String addTabularSection(String content, String objType, String objName,
                                     String value) {
        // Format: "ИмяТЧ: Рекв1: Тип1, Рекв2: Тип2"
        // or just "ИмяТЧ"
        int colonIdx = value.indexOf(':');
        String tsName;
        List<String> attrDefs = new ArrayList<>();

        if (colonIdx > 0) {
            tsName = value.substring(0, colonIdx).trim();
            String attrsPart = value.substring(colonIdx + 1).trim();
            // Split by comma (paren-aware)
            attrDefs = splitByCommaOutsideParens(attrsPart);
        } else {
            tsName = value.trim();
        }

        if (findChildByName(content, "TabularSection", tsName) >= 0) {
            warn("TabularSection '" + tsName + "' already exists, skipping");
            return content;
        }

        String indent = "\t\t\t";
        StringBuilder sb = new StringBuilder();
        sb.append(indent).append("<TabularSection uuid=\"").append(uuid()).append("\">\n");

        // InternalInfo
        String typePrefix = objType + "TabularSection";
        String rowPrefix = objType + "TabularSectionRow";
        sb.append(indent).append("\t<InternalInfo>\n");
        sb.append(indent).append("\t\t<xr:GeneratedType name=\"").append(typePrefix).append(".").append(objName)
                .append(".").append(tsName).append("\" category=\"TabularSection\">\n");
        sb.append(indent).append("\t\t\t<xr:TypeId>").append(uuid()).append("</xr:TypeId>\n");
        sb.append(indent).append("\t\t\t<xr:ValueId>").append(uuid()).append("</xr:ValueId>\n");
        sb.append(indent).append("\t\t</xr:GeneratedType>\n");
        sb.append(indent).append("\t\t<xr:GeneratedType name=\"").append(rowPrefix).append(".").append(objName)
                .append(".").append(tsName).append("\" category=\"TabularSectionRow\">\n");
        sb.append(indent).append("\t\t\t<xr:TypeId>").append(uuid()).append("</xr:TypeId>\n");
        sb.append(indent).append("\t\t\t<xr:ValueId>").append(uuid()).append("</xr:ValueId>\n");
        sb.append(indent).append("\t\t</xr:GeneratedType>\n");
        sb.append(indent).append("\t</InternalInfo>\n");

        // Properties
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(tsName)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", splitCamelCase(tsName));
        sb.append(indent).append("\t\t<Comment/>\n");
        sb.append(indent).append("\t\t<ToolTip/>\n");
        sb.append(indent).append("\t\t<FillChecking>DontCheck</FillChecking>\n");
        sb.append(indent).append("\t</Properties>\n");

        // ChildObjects with attributes
        if (!attrDefs.isEmpty()) {
            sb.append(indent).append("\t<ChildObjects>\n");
            for (String attrShorthand : attrDefs) {
                AttrDef def = parseShorthand(attrShorthand.trim());
                if (def.name.isEmpty()) continue;
                buildTsAttribute(sb, indent + "\t\t", def);
            }
            sb.append(indent).append("\t</ChildObjects>\n");
        } else {
            sb.append(indent).append("\t<ChildObjects/>\n");
        }

        sb.append(indent).append("</TabularSection>");

        String result = insertIntoChildObjects(content, "TabularSection", sb.toString(), null, null);
        if (result != null) {
            info("Added tabular section: " + tsName);
            addCount++;
            return result;
        }
        return content;
    }

    private String addTsAttribute(String content, String objType, String value) {
        // Format: "TSName.AttrDef: Type | flags"
        int dotIdx = value.indexOf('.');
        if (dotIdx <= 0) {
            warn("Invalid ts-attribute format (expected TSName.AttrDef): " + value);
            return content;
        }
        String tsName = value.substring(0, dotIdx).trim();
        String attrShorthand = value.substring(dotIdx + 1).trim();
        AttrDef def = parseShorthand(attrShorthand);
        if (def.name.isEmpty()) { warn("Empty attribute name"); return content; }

        // Find the TS element block
        int tsStart = findChildByName(content, "TabularSection", tsName);
        if (tsStart < 0) {
            warn("TabularSection '" + tsName + "' not found");
            return content;
        }
        int tsEnd = findClosingTag(content, "TabularSection", tsStart);
        if (tsEnd < 0) { warn("Malformed TabularSection XML"); return content; }

        String tsBlock = content.substring(tsStart, tsEnd);

        // Check for existing attribute
        if (tsBlock.contains("<Name>" + def.name + "</Name>")) {
            warn("Attribute '" + def.name + "' already exists in TS '" + tsName + "', skipping");
            return content;
        }

        // Build attribute fragment
        String indent = "\t\t\t\t";
        StringBuilder sb = new StringBuilder();
        buildTsAttribute(sb, indent, def);
        String fragment = sb.toString();

        // Find insertion point within TS ChildObjects
        int coIdx = tsBlock.indexOf("<ChildObjects/>");
        if (coIdx >= 0) {
            // Replace self-closing with open + content + close
            String newTsBlock = tsBlock.substring(0, coIdx)
                    + "<ChildObjects>\n" + fragment
                    + "\t\t\t\t</ChildObjects>"
                    + tsBlock.substring(coIdx + "<ChildObjects/>".length());
            info("Added attribute to TS '" + tsName + "': " + def.name);
            addCount++;
            return content.substring(0, tsStart) + newTsBlock + content.substring(tsEnd);
        }

        coIdx = tsBlock.indexOf("</ChildObjects>");
        if (coIdx >= 0) {
            String newTsBlock = tsBlock.substring(0, coIdx)
                    + fragment
                    + tsBlock.substring(coIdx);
            info("Added attribute to TS '" + tsName + "': " + def.name);
            addCount++;
            return content.substring(0, tsStart) + newTsBlock + content.substring(tsEnd);
        }

        warn("No ChildObjects found in TS '" + tsName + "'");
        return content;
    }

    private void buildTsAttribute(StringBuilder sb, String indent, AttrDef def) {
        sb.append(indent).append("<Attribute uuid=\"").append(uuid()).append("\">\n");
        sb.append(indent).append("\t<Properties>\n");
        sb.append(indent).append("\t\t<Name>").append(esc(def.name)).append("</Name>\n");
        writeSynonym(sb, indent + "\t\t", def.synonym != null ? def.synonym : splitCamelCase(def.name)); //**agent TASK-174 [05.06.2026 12:42:00] XG-09: синоним из пайп-токена
        sb.append(indent).append("\t\t<Comment/>\n");
        writeTypeBlock(sb, indent + "\t\t", def.types);
        sb.append(indent).append("\t\t<PasswordMode>false</PasswordMode>\n");
        sb.append(indent).append("\t\t<Format/>\n");
        sb.append(indent).append("\t\t<EditFormat/>\n");
        sb.append(indent).append("\t\t<ToolTip/>\n");
        sb.append(indent).append("\t\t<MarkNegatives>false</MarkNegatives>\n");
        sb.append(indent).append("\t\t<Mask/>\n");
        sb.append(indent).append("\t\t<MultiLine>false</MultiLine>\n");
        sb.append(indent).append("\t\t<ExtendedEdit>false</ExtendedEdit>\n");
        sb.append(indent).append("\t\t<MinValue xsi:nil=\"true\"/>\n");
        sb.append(indent).append("\t\t<MaxValue xsi:nil=\"true\"/>\n");

        String fillChecking = def.flags.contains("req") ? "ShowError" : "DontCheck";
        sb.append(indent).append("\t\t<FillChecking>").append(fillChecking).append("</FillChecking>\n");

        //++agent TASK-174 [07.06.2026 12:00:00]
        // Порт-аудит: ChoiceFoldersAndItems был опущен (спека §6.1 + грунт-труф 2.20).
        sb.append(indent).append("\t\t<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>\n");
        //++agent TASK-174
        sb.append(indent).append("\t\t<ChoiceParameterLinks/>\n");
        sb.append(indent).append("\t\t<ChoiceParameters/>\n");
        sb.append(indent).append("\t\t<QuickChoice>Auto</QuickChoice>\n");
        sb.append(indent).append("\t\t<CreateOnInput>Auto</CreateOnInput>\n");
        sb.append(indent).append("\t\t<ChoiceForm/>\n");
        sb.append(indent).append("\t\t<LinkByType/>\n");
        sb.append(indent).append("\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>\n");
        sb.append(indent).append("\t\t<Indexing>DontIndex</Indexing>\n");
        sb.append(indent).append("\t\t<FullTextSearch>Use</FullTextSearch>\n");
        sb.append(indent).append("\t\t<DataHistory>Use</DataHistory>\n");
        sb.append(indent).append("\t</Properties>\n");
        sb.append(indent).append("</Attribute>\n");
    }

    // ─── REMOVE operations ──────────────────────────────────────────────

    private String executeRemove(String content, String objType, String target, String value) {
        return switch (target) {
            case "attribute" -> removeChild(content, "Attribute", value);
            case "dimension" -> removeChild(content, "Dimension", value);
            case "resource" -> removeChild(content, "Resource", value);
            case "enumValue" -> removeChild(content, "EnumValue", value);
            case "column" -> removeChild(content, "Column", value);
            case "ts" -> removeChild(content, "TabularSection", value);
            case "form" -> removeChild(content, "Form", value);
            case "template" -> removeChild(content, "Template", value);
            case "command" -> removeChild(content, "Command", value);
            case "ts-attribute" -> removeTsAttribute(content, value);
            default -> {
                warn("Unknown remove target: " + target);
                yield content;
            }
        };
    }

    private String removeChild(String content, String xmlTag, String name) {
        name = name.trim();
        int start = findChildByName(content, xmlTag, name, true);
        if (start < 0) {
            warn(xmlTag + " '" + name + "' not found, skipping remove");
            return content;
        }
        int end = findClosingTag(content, xmlTag, start);
        if (end < 0) { warn("Malformed " + xmlTag + " XML"); return content; }

        // Remove the whole line(s) — extend to include preceding newline
        int lineStart = content.lastIndexOf('\n', start - 1);
        if (lineStart < 0) lineStart = 0; else lineStart++; // keep the newline char in previous line

        // For clean removal, also remove the preceding newline
        int removeFrom = lineStart > 0 ? lineStart - 1 : lineStart;

        String result = content.substring(0, removeFrom) + content.substring(end);
        info("Removed " + xmlTag.toLowerCase() + ": " + name);
        removeCount++;
        return result;
    }

    private String removeTsAttribute(String content, String value) {
        int dotIdx = value.indexOf('.');
        if (dotIdx <= 0) {
            warn("Invalid ts-attribute format (expected TSName.AttrName): " + value);
            return content;
        }
        String tsName = value.substring(0, dotIdx).trim();
        String attrName = value.substring(dotIdx + 1).trim();

        int tsStart = findChildByName(content, "TabularSection", tsName);
        if (tsStart < 0) { warn("TabularSection '" + tsName + "' not found"); return content; }
        int tsEnd = findClosingTag(content, "TabularSection", tsStart);
        if (tsEnd < 0) { warn("Malformed TabularSection XML"); return content; }

        String tsBlock = content.substring(tsStart, tsEnd);

        // Find the attribute within the TS block
        int attrStart = findChildByName(tsBlock, "Attribute", attrName);
        if (attrStart < 0) {
            warn("Attribute '" + attrName + "' not found in TS '" + tsName + "'");
            return content;
        }
        int attrEnd = findClosingTag(tsBlock, "Attribute", attrStart);
        if (attrEnd < 0) { warn("Malformed Attribute XML"); return content; }

        // Remove including preceding whitespace
        int lineStart = tsBlock.lastIndexOf('\n', attrStart - 1);
        int removeFrom = lineStart >= 0 ? lineStart : attrStart;

        String newTsBlock = tsBlock.substring(0, removeFrom) + tsBlock.substring(attrEnd);
        info("Removed attribute from TS '" + tsName + "': " + attrName);
        removeCount++;
        return content.substring(0, tsStart) + newTsBlock + content.substring(tsEnd);
    }

    // ─── MODIFY operations ──────────────────────────────────────────────

    private String executeModify(String content, String objType, String target, String value) {
        // Special case: modify-property handles root <Properties> of the object
        if ("property".equals(target)) {
            return modifyRootProperty(content, value);
        }

        // Format: "ElementName: key=val, key=val"
        String xmlTag = switch (target) {
            case "attribute" -> "Attribute";
            case "dimension" -> "Dimension";
            case "resource" -> "Resource";
            case "enumValue" -> "EnumValue";
            case "column" -> "Column";
            case "ts" -> "TabularSection";
            default -> null;
        };
        if (xmlTag == null) {
            warn("Unknown modify target: " + target);
            return content;
        }

        int colonIdx = value.indexOf(':');
        if (colonIdx <= 0) {
            warn("Invalid modify format (expected Name: key=val): " + value);
            return content;
        }
        String elemName = value.substring(0, colonIdx).trim();
        String changesPart = value.substring(colonIdx + 1).trim();

        // Parse changes
        Map<String, String> changes = new LinkedHashMap<>();
        for (String pair : splitByCommaOutsideParens(changesPart)) {
            pair = pair.trim();
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                changes.put(pair.substring(0, eqIdx).trim(), pair.substring(eqIdx + 1).trim());
            }
        }

        int elemStart = findChildByName(content, xmlTag, elemName, true);
        if (elemStart < 0) {
            warn(xmlTag + " '" + elemName + "' not found for modify");
            return content;
        }
        int elemEnd = findClosingTag(content, xmlTag, elemStart);
        if (elemEnd < 0) { warn("Malformed " + xmlTag + " XML"); return content; }

        String elemBlock = content.substring(elemStart, elemEnd);

        for (Map.Entry<String, String> entry : changes.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();

            if ("name".equals(key)) {
                // Rename: replace <Name>OldName</Name>
                elemBlock = elemBlock.replace(
                        "<Name>" + esc(elemName) + "</Name>",
                        "<Name>" + esc(val) + "</Name>");
                // Update synonym if auto-generated
                String oldSynonym = splitCamelCase(elemName);
                String newSynonym = splitCamelCase(val);
                elemBlock = elemBlock.replace(
                        "<v8:content>" + esc(oldSynonym) + "</v8:content>",
                        "<v8:content>" + esc(newSynonym) + "</v8:content>");
                info("Renamed " + xmlTag + ": " + elemName + " -> " + val);
                modifyCount++;
            } else if ("type".equals(key)) {
                // Change type — replace the <Type>...</Type> block
                elemBlock = replaceTypeBlock(elemBlock, val);
                info("Changed type of " + xmlTag + " '" + elemName + "': " + val);
                modifyCount++;
            } else if ("synonym".equals(key) || "comment".equals(key)) {
                // MLText editing: Synonym or Comment
                String mlTag = "synonym".equals(key) ? "Synonym" : "Comment";
                elemBlock = replaceMlTextProperty(elemBlock, mlTag, val);
                info("Changed " + mlTag + " of " + xmlTag + " '" + elemName + "': " + val);
                modifyCount++;
            } else {
                // Scalar property: <Key>OldValue</Key> -> <Key>NewValue</Key>
                String propPattern = "(<" + Pattern.quote(key) + ">)[^<]*(</" + Pattern.quote(key) + ">)";
                if (elemBlock.matches("(?s).*" + propPattern + ".*")) {
                    elemBlock = elemBlock.replaceFirst(propPattern, "$1" + esc(val) + "$2");
                    info("Modified " + xmlTag + " '" + elemName + "'." + key + " = " + val);
                    modifyCount++;
                } else {
                    warn(xmlTag + " '" + elemName + "': property '" + key + "' not found");
                }
            }
        }

        return content.substring(0, elemStart) + elemBlock + content.substring(elemEnd);
    }

    // ─── XML text manipulation ──────────────────────────────────────────

    /**
     * Find ChildObjects element and insert fragment at the canonical position.
     */
    private String insertIntoChildObjects(String content, String xmlTag, String fragment,
                                          String afterName, String beforeName) {
        // Find root </ChildObjects> — use lastIndexOf to skip nested TS ChildObjects
        int coClose = content.lastIndexOf("</ChildObjects>");
        if (coClose < 0) {
            // Check for self-closing <ChildObjects/>
            int coSelfClose = content.indexOf("<ChildObjects/>");
            if (coSelfClose >= 0) {
                // Replace with open + fragment + close
                return content.substring(0, coSelfClose)
                        + "<ChildObjects>\n" + fragment + "\n"
                        + "\t\t</ChildObjects>"
                        + content.substring(coSelfClose + "<ChildObjects/>".length());
            }
            warn("No <ChildObjects> found in XML");
            return null;
        }

        // Positional insertion: after/before named element
        if (afterName != null && !afterName.isEmpty()) {
            int afterStart = findChildByName(content, xmlTag, afterName, true);
            if (afterStart >= 0) {
                int afterEnd = findClosingTag(content, xmlTag, afterStart);
                if (afterEnd >= 0) {
                    return content.substring(0, afterEnd) + "\n" + fragment + content.substring(afterEnd);
                }
            }
            warn("after='" + afterName + "': not found, appending");
        }
        if (beforeName != null && !beforeName.isEmpty()) {
            int beforeStart = findChildByName(content, xmlTag, beforeName, true);
            if (beforeStart >= 0) {
                int lineStart = content.lastIndexOf('\n', beforeStart - 1);
                int insertAt = lineStart >= 0 ? lineStart : beforeStart;
                return content.substring(0, insertAt) + "\n" + fragment + content.substring(insertAt);
            }
            warn("before='" + beforeName + "': not found, appending");
        }

        // Find last element of this type — insert after it
        int lastEnd = findLastClosingTag(content, xmlTag, coClose);
        if (lastEnd >= 0) {
            return content.substring(0, lastEnd) + "\n" + fragment + content.substring(lastEnd);
        }

        // No elements of this type — find canonical position
        int insertIdx = findCanonicalInsertionPoint(content, xmlTag, coClose);
        if (insertIdx >= 0) {
            // Insert before the element at canonical position
            int lineStart = content.lastIndexOf('\n', insertIdx - 1);
            int insertAt = lineStart >= 0 ? lineStart : insertIdx;
            return content.substring(0, insertAt) + "\n" + fragment + content.substring(insertAt);
        }

        // Default: insert before </ChildObjects>
        return content.substring(0, coClose) + fragment + "\n\t\t" + content.substring(coClose);
    }

    /**
     * Find start position of a child element with given Name.
     * Returns position of opening tag, or -1 if not found.
     */
    private int findChildByName(String content, String xmlTag, String name) {
        return findChildByName(content, xmlTag, name, false);
    }

    /**
     * Find start position of a child element with given Name.
     * When rootOnly=true, skip matches that are inside a TabularSection block.
     */
    private int findChildByName(String content, String xmlTag, String name, boolean rootOnly) {
        String nameTag = "<Name>" + name + "</Name>";
        String openTag = "<" + xmlTag + " ";
        int searchFrom = 0;
        while (true) {
            int nameIdx = content.indexOf(nameTag, searchFrom);
            if (nameIdx < 0) return -1;

            // Walk back to find the opening <XmlTag — search up to 2000 chars
            // (TabularSection has InternalInfo between opening tag and Name)
            int lookBackStart = Math.max(0, nameIdx - 2000);
            String prefix = content.substring(lookBackStart, nameIdx);
            int tagStart = prefix.lastIndexOf(openTag);
            if (tagStart < 0) {
                searchFrom = nameIdx + nameTag.length();
                continue;
            }
            int absPos = lookBackStart + tagStart;

            // When rootOnly, skip elements nested inside a TabularSection
            if (rootOnly && isInsideTabularSection(content, absPos)) {
                searchFrom = nameIdx + nameTag.length();
                continue;
            }
            return absPos;
        }
    }

    /**
     * Check if position is inside a TabularSection block (not the TS tag itself).
     */
    private boolean isInsideTabularSection(String content, int pos) {
        // Find the last <TabularSection before pos
        int tsOpen = content.lastIndexOf("<TabularSection ", pos);
        if (tsOpen < 0) return false;
        // If pos IS the TabularSection opening tag, it's not "inside"
        if (tsOpen == pos) return false;
        // Find the corresponding </TabularSection> after tsOpen
        int tsClose = content.indexOf("</TabularSection>", tsOpen);
        // pos is inside if </TabularSection> is after pos
        return tsClose >= 0 && tsClose > pos;
    }

    /**
     * Find closing tag position (end of </XmlTag>\n) starting from element start.
     */
    private int findClosingTag(String content, String xmlTag, int fromIdx) {
        String closeTag = "</" + xmlTag + ">";
        int closeIdx = content.indexOf(closeTag, fromIdx);
        if (closeIdx < 0) return -1;
        int end = closeIdx + closeTag.length();
        // Include trailing newline if present
        if (end < content.length() && content.charAt(end) == '\r') end++;
        if (end < content.length() && content.charAt(end) == '\n') end++;
        return end;
    }

    /**
     * Find the last root-level closing tag of given type before position limit.
     * Skips tags nested inside TabularSection blocks.
     */
    private int findLastClosingTag(String content, String xmlTag, int limitPos) {
        String closeTag = "</" + xmlTag + ">";
        int lastIdx = -1;
        int searchFrom = 0;
        while (searchFrom < limitPos) {
            int idx = content.indexOf(closeTag, searchFrom);
            if (idx < 0 || idx >= limitPos) break;
            int end = idx + closeTag.length();
            if (end < content.length() && content.charAt(end) == '\r') end++;
            if (end < content.length() && content.charAt(end) == '\n') end++;
            if (!isInsideTabularSection(content, idx)) {
                lastIdx = end;
            }
            searchFrom = end;
        }
        return lastIdx;
    }

    /**
     * Find canonical insertion point for new element of given type.
     * Skips elements nested inside TabularSection blocks.
     */
    private int findCanonicalInsertionPoint(String content, String xmlTag, int coClosePos) {
        int tagIdx = CHILD_ORDER.indexOf(xmlTag);
        if (tagIdx < 0) return -1;

        // Find first root-level element of any type that comes AFTER in canonical order
        for (int i = tagIdx + 1; i < CHILD_ORDER.size(); i++) {
            String nextTag = CHILD_ORDER.get(i);
            String searchTag = "<" + nextTag + " ";
            int searchFrom = 0;
            while (searchFrom < coClosePos) {
                int found = content.indexOf(searchTag, searchFrom);
                if (found < 0 || found >= coClosePos) break;
                if (!isInsideTabularSection(content, found)) {
                    return found;
                }
                searchFrom = found + searchTag.length();
            }
        }
        return -1;
    }

    // ─── Type block generation ──────────────────────────────────────────

    private void writeTypeBlock(StringBuilder sb, String indent, List<String> types) {
        sb.append(indent).append("<Type>\n");
        for (String type : types) {
            writeTypeValue(sb, indent + "\t", resolveType(type));
        }
        sb.append(indent).append("</Type>\n");
    }

    private void writeTypeValue(StringBuilder sb, String indent, String type) {
        if ("Boolean".equals(type)) {
            sb.append(indent).append("<v8:Type>xs:boolean</v8:Type>\n");
        } else if ("ValueStorage".equals(type)) {
            sb.append(indent).append("<v8:Type>xs:base64Binary</v8:Type>\n");
        } else if (type.startsWith("String")) {
            Matcher m = Pattern.compile("^String\\((\\d+)\\)$").matcher(type);
            String len = m.matches() ? m.group(1) : "10";
            sb.append(indent).append("<v8:Type>xs:string</v8:Type>\n");
            sb.append(indent).append("<v8:StringQualifiers>\n");
            sb.append(indent).append("\t<v8:Length>").append(len).append("</v8:Length>\n");
            sb.append(indent).append("\t<v8:AllowedLength>Variable</v8:AllowedLength>\n");
            sb.append(indent).append("</v8:StringQualifiers>\n");
        } else if (type.startsWith("Number")) {
            Matcher m = Pattern.compile("^Number\\((\\d+),(\\d+)(?:,(nonneg))?\\)$").matcher(type);
            String digits = "10", fraction = "0";
            String sign = "Any";
            if (m.matches()) {
                digits = m.group(1);
                fraction = m.group(2);
                if (m.group(3) != null) sign = "Nonnegative";
            }
            sb.append(indent).append("<v8:Type>xs:decimal</v8:Type>\n");
            sb.append(indent).append("<v8:NumberQualifiers>\n");
            sb.append(indent).append("\t<v8:Digits>").append(digits).append("</v8:Digits>\n");
            sb.append(indent).append("\t<v8:FractionDigits>").append(fraction).append("</v8:FractionDigits>\n");
            sb.append(indent).append("\t<v8:AllowedSign>").append(sign).append("</v8:AllowedSign>\n");
            sb.append(indent).append("</v8:NumberQualifiers>\n");
        } else if ("Date".equals(type)) {
            sb.append(indent).append("<v8:Type>xs:dateTime</v8:Type>\n");
            sb.append(indent).append("<v8:DateQualifiers>\n");
            sb.append(indent).append("\t<v8:DateFractions>Date</v8:DateFractions>\n");
            sb.append(indent).append("</v8:DateQualifiers>\n");
        } else if ("DateTime".equals(type)) {
            sb.append(indent).append("<v8:Type>xs:dateTime</v8:Type>\n");
            sb.append(indent).append("<v8:DateQualifiers>\n");
            sb.append(indent).append("\t<v8:DateFractions>DateTime</v8:DateFractions>\n");
            sb.append(indent).append("</v8:DateQualifiers>\n");
        //++agent TASK-174 [07.06.2026 12:00:00]
        // XG-13: ветка Time (ЧастиДаты=Время) — раньше отсутствовала, Date(Time) уходил в литерал.
        } else if ("Time".equals(type)) {
            sb.append(indent).append("<v8:Type>xs:dateTime</v8:Type>\n");
            sb.append(indent).append("<v8:DateQualifiers>\n");
            sb.append(indent).append("\t<v8:DateFractions>Time</v8:DateFractions>\n");
            sb.append(indent).append("</v8:DateQualifiers>\n");
        //++agent TASK-174
        } else if (type.startsWith("DefinedType.")) {
            sb.append(indent).append("<v8:TypeSet>cfg:").append(type).append("</v8:TypeSet>\n");
        } else if (type.contains(".")) {
            // Reference type: CatalogRef.Товары -> cfg:CatalogRef.Товары
            sb.append(indent).append("<v8:Type>cfg:").append(type).append("</v8:Type>\n");
        } else {
            // Unknown — pass through
            sb.append(indent).append("<v8:Type>").append(type).append("</v8:Type>\n");
        }
    }

    private String replaceTypeBlock(String elemBlock, String newTypeStr) {
        // Find <Type>...</Type> and replace
        int typeStart = elemBlock.indexOf("<Type>");
        int typeEnd = elemBlock.indexOf("</Type>");
        if (typeStart < 0 || typeEnd < 0) return elemBlock;
        typeEnd += "</Type>".length();

        // Detect indent
        int lineStart = elemBlock.lastIndexOf('\n', typeStart) + 1;
        String indent = elemBlock.substring(lineStart, typeStart);

        StringBuilder sb = new StringBuilder();
        // Handle composite types (Type1 + Type2)
        List<String> types = new ArrayList<>();
        if (newTypeStr.contains(" + ")) {
            for (String part : newTypeStr.split("\\+")) {
                types.add(resolveType(part.trim()));
            }
        } else {
            types.add(resolveType(newTypeStr));
        }

        sb.append(indent).append("<Type>\n");
        for (String type : types) {
            writeTypeValue(sb, indent + "\t", type);
        }
        sb.append(indent).append("</Type>");

        return elemBlock.substring(0, lineStart) + sb + elemBlock.substring(typeEnd);
    }

    // ─── Shorthand parser ───────────────────────────────────────────────

    private AttrDef parseShorthand(String shorthand) {
        AttrDef def = new AttrDef();

        // Extract positional: >> after Name / << before Name
        Matcher posM = Pattern.compile("\\s*>>\\s*after\\s+(\\S+)\\s*$").matcher(shorthand);
        if (posM.find()) {
            def.after = posM.group(1);
            shorthand = shorthand.substring(0, posM.start()).trim();
        } else {
            posM = Pattern.compile("\\s*<<\\s*before\\s+(\\S+)\\s*$").matcher(shorthand);
            if (posM.find()) {
                def.before = posM.group(1);
                shorthand = shorthand.substring(0, posM.start()).trim();
            }
        }

        // Split by | for flags
        //**agent TASK-174 [05.06.2026 12:40:00]
        // XG-09: прежний разбор считал ВСЁ после первого "|" флагами. Популярная
        // у агентов форма --value "Имя|Синоним|Date" (стиль add-predefined) молча
        // теряла и синоним, и тип — реквизит всегда получал xs:string (TASK-173).
        // Теперь каждый пайп-токен классифицируется: известный флаг → флаг;
        // распознанный тип (если тип ещё не задан через "Имя: Тип") → тип;
        // иначе → синоним. Канонический синтаксис "Имя: Тип | req" не меняется.
        String[] pipeparts = shorthand.split("\\|");
        String main = pipeparts[0].trim();
        for (int pi = 1; pi < pipeparts.length; pi++) {
            String segment = pipeparts[pi].trim();
            if (segment.isEmpty()) continue;
            // Сегмент классифицируется ЦЕЛИКОМ: тип "Number(15,2)" содержит запятую
            // внутри скобок — рвать сегмент по запятой до классификации нельзя.
            java.util.List<String> tokens = new ArrayList<>();
            boolean allFlags = true;
            for (String f : segment.split(",")) {
                String token = f.trim().toLowerCase();
                if (token.isEmpty()) continue;
                tokens.add(token);
                if (!KNOWN_FLAGS.contains(token)) allFlags = false;
            }
            if (allFlags && !tokens.isEmpty()) {
                def.flags.addAll(tokens);
            } else if (def.types.isEmpty() && isRecognizedType(segment)) {
                for (String part : CompositeType.splitCompositeTypes(segment)) {
                    if (!part.trim().isEmpty()) def.types.add(resolveType(part.trim()));
                }
            } else if (def.synonym == null) {
                def.synonym = segment;
            } else {
                warn("Unrecognized shorthand token '" + segment + "' (not a flag/type/synonym slot)");
            }
        }
        //**agent TASK-174

        // Split by : for name and type
        int colonIdx = main.indexOf(':');
        if (colonIdx > 0) {
            def.name = main.substring(0, colonIdx).trim();
            String typeStr = main.substring(colonIdx + 1).trim();
            //**agent TASK-174 [05.06.2026 12:40:00]
            // XG-09: тип из "Имя: Тип" имеет приоритет над типом, угаданным из
            // пайп-токенов; составной тип — через paren-aware сплиттер ("+" и "|"
            // внутри скобок не рвутся).
            //--agent TASK-174 (прежний код)
            //// Composite types: Type1 + Type2
            //if (typeStr.contains(" + ")) {
            //    for (String part : typeStr.split("\\+")) {
            //        def.types.add(resolveType(part.trim()));
            //    }
            //} else {
            //    def.types.add(resolveType(typeStr));
            //}
            List<String> colonTypes = new ArrayList<>();
            for (String part : CompositeType.splitCompositeTypes(typeStr)) {
                if (!part.trim().isEmpty()) colonTypes.add(resolveType(part.trim()));
            }
            if (!colonTypes.isEmpty()) {
                def.types.clear();
                def.types.addAll(colonTypes);
            }
            //**agent TASK-174
        } else {
            def.name = main;
        }
        //++agent TASK-174 [05.06.2026 12:40:00]
        // Default только если тип не пришёл ни из ":", ни из пайп-токена.
        if (def.types.isEmpty()) {
            def.types.add("String"); // default — String(10)
        }
        //++agent TASK-174

        return def;
    }

    //++agent TASK-174 [05.06.2026 12:40:00]
    /** Флаги shorthand-нотации (всё, что реально читается из def.flags). */
    private static final Set<String> KNOWN_FLAGS = Set.of(
            "req", "nonneg", "master", "mainfilter", "denyincomplete",
            "index", "indexadditional", "nouseintotals",
            //++agent TASK-174 [07.06.2026 12:00:00] порт-аудит: флаги измерений бух./расчётного регистра
            "balance", "base");
            //++agent TASK-174

    /**
     * XG-09: распознаваем ли токен как тип (для классификации пайп-токенов).
     * Консервативно: только то, что resolveType() переводит в известную форму —
     * параметризованный тип, известный простой тип (рус/англ) или dotted-ссылка.
     */
    private boolean isRecognizedType(String token) {
        String first = CompositeType.splitCompositeTypes(token).get(0).trim();
        if (first.isEmpty()) return false;
        Matcher m = Pattern.compile("^([^(]+)\\((.+)\\)$").matcher(first);
        String base = m.matches() ? m.group(1).trim() : first;
        if (base.contains(".")) {
            // dotted: CatalogRef.X / СправочникСсылка.X / DefinedType.X и т.п.
            return true;
        }
        for (String known : RU_TYPE_SYNONYMS.keySet()) {
            if (base.equalsIgnoreCase(known)) return true;
        }
        return base.equalsIgnoreCase("Number") || base.equalsIgnoreCase("String")
                || base.equalsIgnoreCase("Boolean") || base.equalsIgnoreCase("Date")
                || base.equalsIgnoreCase("DateTime") || base.equalsIgnoreCase("ValueStorage");
    }
    //++agent TASK-174

    private static class AttrDef {
        String name = "";
        List<String> types = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();
        //++agent TASK-174 [05.06.2026 12:40:00]
        String synonym; // XG-09: синоним из пайп-токена ("Имя|Синоним|Тип")
        //++agent TASK-174
        String after;
        String before;
    }

    // ─── Type resolution ────────────────────────────────────────────────

    //++agent TASK-174 [07.06.2026 12:00:00]
    /**
     * XG-13: нормализация параметра ЧастиДаты из формы Date(X) / Дата(X)
     * в канонический простой тип (Date | Time | DateTime).
     */
    private static String normalizeDateFraction(String fraction) {
        if (fraction.equalsIgnoreCase("Date") || fraction.equalsIgnoreCase("Дата")) return "Date";
        if (fraction.equalsIgnoreCase("Time") || fraction.equalsIgnoreCase("Время")) return "Time";
        if (fraction.equalsIgnoreCase("DateTime") || fraction.equalsIgnoreCase("ДатаВремя")) return "DateTime";
        throw new IllegalArgumentException("Unknown date fraction '" + fraction
                + "' in Date(...) shorthand. Valid: Date|Time|DateTime (Дата|Время|ДатаВремя)");
    }
    //++agent TASK-174

    private String resolveType(String type) {
        if (type == null || type.isEmpty()) return "String";

        // Parameterized: Число(15,2) -> Number(15,2) or string(50) -> String(50)
        Matcher m = Pattern.compile("^([^(]+)\\((.+)\\)$").matcher(type);
        if (m.matches()) {
            String base = m.group(1).trim();
            String params = m.group(2);
            //**agent TASK-174 [07.06.2026 12:00:00]
            // XG-13: параметр Date(...) — это КВАЛИФИКАТОР ЧастиДаты, а не параметризованный
            // тип. Раньше resolveType возвращал "Date(DateTime)" как есть, writeTypeValue
            // не имел ветки для скобочной формы и молча писал литерал
            // <v8:Type>Date(DateTime)</v8:Type> — битый тип в выгрузке. Теперь Date(X)
            // нормализуется в канонический простой тип Date/Time/DateTime.
            if (base.equalsIgnoreCase("Date") || base.equalsIgnoreCase("Дата")) {
                return normalizeDateFraction(params.trim());
            }
            //**agent TASK-174
            // Russian synonyms
            for (Map.Entry<String, String> entry : RU_TYPE_SYNONYMS.entrySet()) {
                if (base.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue() + "(" + params + ")";
                }
            }
            // English canonical names — normalize case
            if (base.equalsIgnoreCase("String"))  return "String("  + params + ")";
            if (base.equalsIgnoreCase("Number"))  return "Number("  + params + ")";
            return type;
        }

        // Reference: СправочникСсылка.Организации -> CatalogRef.Организации
        if (type.contains(".")) {
            int dotIdx = type.indexOf('.');
            String prefix = type.substring(0, dotIdx);
            String suffix = type.substring(dotIdx);
            for (Map.Entry<String, String> entry : RU_TYPE_SYNONYMS.entrySet()) {
                if (prefix.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue() + suffix;
                }
            }
            return type;
        }

        // Simple
        for (Map.Entry<String, String> entry : RU_TYPE_SYNONYMS.entrySet()) {
            if (type.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        // English case-insensitive
        if (type.equalsIgnoreCase("Number")) return "Number";
        if (type.equalsIgnoreCase("String")) return "String";
        if (type.equalsIgnoreCase("Boolean")) return "Boolean";
        if (type.equalsIgnoreCase("Date")) return "Date";
        if (type.equalsIgnoreCase("DateTime")) return "DateTime";
        if (type.equalsIgnoreCase("ValueStorage")) return "ValueStorage";

        return type;
    }

    // ─── XML helpers ────────────────────────────────────────────────────

    private String detectObjectType(String content) {
        // Find first child of <MetaDataObject>: <Catalog uuid=...> etc.
        Matcher m = Pattern.compile("<(\\w+)\\s+uuid=\"").matcher(content);
        while (m.find()) {
            String tag = m.group(1);
            if (!"MetaDataObject".equals(tag) && !"InternalInfo".equals(tag)) {
                return tag;
            }
        }
        throw new IllegalStateException("Cannot detect object type from XML");
    }

    private String detectObjectName(String content) {
        // Find <Name>...</Name> inside <Properties>
        Matcher m = Pattern.compile("<Properties>\\s*<Name>([^<]+)</Name>", Pattern.DOTALL).matcher(content);
        if (m.find()) return m.group(1).trim();
        throw new IllegalStateException("Cannot detect object name from XML");
    }

    // ─── ROOT PROPERTY operations ────────────────────────────────────────

    /**
     * Add or set a root-level property in <Properties>.
     * Format: "PropName=Value" or "PropName:LocalString=Value" (for MLText).
     */
    private String addOrSetProperty(String content, String value) {
        int eqIdx = value.indexOf('=');
        if (eqIdx <= 0) {
            warn("Invalid add-property format (expected PropName=Value): " + value);
            return content;
        }
        String propName = value.substring(0, eqIdx).trim();
        String propValue = value.substring(eqIdx + 1).trim();

        // Check if it's a LocalString type hint
        boolean isLocalString = false;
        if (propName.endsWith(":LocalString")) {
            propName = propName.substring(0, propName.length() - ":LocalString".length());
            isLocalString = true;
        }

        // Check for Synonym/Comment — always treat as MLText
        if ("Synonym".equals(propName) || "Comment".equals(propName) || "Explanation".equals(propName)) {
            isLocalString = true;
        }

        if (isLocalString) {
            content = replaceMlTextProperty(content, propName, propValue);
        } else {
            // Simple scalar: replace existing or expand self-closing
            String existing = "(<" + Pattern.quote(propName) + ">)[^<]*(</" + Pattern.quote(propName) + ">)";
            if (content.matches("(?s).*" + existing + ".*")) {
                content = content.replaceFirst(existing, "$1" + esc(propValue) + "$2");
            } else {
                // Try self-closing
                String selfClose = "<" + Pattern.quote(propName) + "\\s*/>";
                if (content.matches("(?s).*" + selfClose + ".*")) {
                    content = content.replaceFirst(selfClose,
                            "<" + propName + ">" + esc(propValue) + "</" + propName + ">");
                } else {
                    warn("Property '" + propName + "' not found in object");
                    return content;
                }
            }
        }
        info("Set property " + propName + " = " + propValue);
        modifyCount++;
        return content;
    }

    /**
     * Modify a root-level property.
     * Format: "key=val, key=val" (multiple comma-separated).
     */
    private String modifyRootProperty(String content, String value) {
        // Split by comma (top-level only, not inside quoted values)
        String[] pairs = value.split(",(?=\\s*[A-Za-z])");
        for (String pair : pairs) {
            content = addOrSetProperty(content, pair.trim());
        }
        return content;
    }

    /**
     * Replace or create MLText property (Synonym, Comment, Explanation) in an element block.
     * Handles 3 cases: existing content, self-closing tag, or missing tag.
     */
    private String replaceMlTextProperty(String block, String propName, String newValue) {
        // Case 1: has <v8:content> inside <PropName>
        Pattern withContent = Pattern.compile(
                "(<" + Pattern.quote(propName) + ">.*?<v8:content>)[^<]*(</v8:content>)",
                Pattern.DOTALL);
        Matcher m = withContent.matcher(block);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(m.group(1))
                    + esc(newValue)
                    + Matcher.quoteReplacement(m.group(2)));
        }

        // Case 2: self-closing <PropName/>
        String mlBlock = "<" + propName + ">\n"
                + "\t\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t\t<v8:content>" + esc(newValue) + "</v8:content>\n"
                + "\t\t\t\t\t</v8:item>\n"
                + "\t\t\t\t</" + propName + ">";
        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        m = selfClose.matcher(block);
        if (m.find()) {
            return m.replaceFirst(Matcher.quoteReplacement(mlBlock));
        }

        // Case 3: no such tag found — return unchanged
        return block;
    }

    private void writeSynonym(StringBuilder sb, String indent, String text) {
        sb.append(indent).append("<Synonym>\n");
        sb.append(indent).append("\t<v8:item>\n");
        sb.append(indent).append("\t\t<v8:lang>ru</v8:lang>\n");
        sb.append(indent).append("\t\t<v8:content>").append(esc(text)).append("</v8:content>\n");
        sb.append(indent).append("\t</v8:item>\n");
        sb.append(indent).append("</Synonym>\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String uuid() {
        return java.util.UUID.randomUUID().toString();
    }

    private static String splitCamelCase(String name) {
        if (name == null || name.isEmpty()) return name;
        // Insert space between lower→Upper case transitions (both Cyrillic and Latin)
        String result = name.replaceAll("([а-яё])([А-ЯЁ])", "$1 $2")
                            .replaceAll("([a-z])([A-Z])", "$1 $2");
        if (result.length() > 1) {
            result = result.substring(0, 1) + result.substring(1).toLowerCase();
        }
        return result;
    }

    private List<String> splitByCommaOutsideParens(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            if (ch == ',' && depth == 0) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
    }

    // ─── File I/O ───────────────────────────────────────────────────────

    private Path resolveObjectPath(Path path) {
        if (Files.isRegularFile(path)) return path;
        if (Files.isDirectory(path)) {
            String name = path.getFileName().toString();
            // Try Name/Name.xml inside the dir
            Path candidate = path.resolve(name + ".xml");
            if (Files.exists(candidate)) return candidate;
            // Try sibling Name.xml
            Path sibling = path.getParent().resolve(name + ".xml");
            if (Files.exists(sibling)) return sibling;
            throw new IllegalArgumentException("Cannot find XML in directory: " + path);
        }
        throw new IllegalArgumentException("Object file not found: " + path);
    }

    private String readFileContent(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private void writeFileWithBom(Path path, String content) throws IOException {
        //++agent TASK-172 [02.06.2026 07:17:00]
        // Канон Designer (_Демо): метаданные/обёртки .xml — BOM + CRLF.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    private void info(String msg) { out.println("[INFO] " + msg); }
    private void warn(String msg) { out.println("[WARN] " + msg); warnCount++; }

    // ═══════════════════════════════════════════════════════════════════════
    // BATCH PATCH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Apply a batch of operations from a JSON file atomically.
     *
     * <p>Transactional: all operations are applied to an in-memory copy of the XML.
     * If any operation throws an exception the file is NOT written (rollback).
     *
     * @param objectPath path to the object XML (or directory)
     * @param batchFile  path to the JSON batch file
     */
    public void applyBatch(Path objectPath, Path batchFile) throws IOException {
        Path xmlPath = resolveObjectPath(objectPath);
        MetaBatchDsl batch = new ObjectMapper().readValue(batchFile.toFile(), MetaBatchDsl.class);
        applyBatch(xmlPath, batch);
    }

    /**
     * Apply a pre-parsed batch to the given XML path (low-level, used by tests).
     *
     * @param xmlPath absolute path to the object XML file
     * @param batch   parsed batch DSL
     */
    public void applyBatch(Path xmlPath, MetaBatchDsl batch) throws IOException {
        String content = readFileContent(xmlPath);

        addCount = 0;
        removeCount = 0;
        modifyCount = 0;
        warnCount = 0;

        String objType = detectObjectType(content);
        String objName = detectObjectName(content);
        out.println("[INFO] Batch: " + objType + "." + objName
                + ", " + batch.getOperations().size() + " operations");

        // Transactional: work on in-memory copy; only write if ALL ops succeed
        String result = content;
        int opIndex = 0;
        for (Operation op : batch.getOperations()) {
            opIndex++;
            try {
                result = applyBatchOperation(result, objType, objName, op);
            } catch (Exception e) {
                throw new RuntimeException(
                        "Batch rolled back: operation #" + opIndex
                        + " (" + op.getOp() + " " + op.getName() + ") failed: " + e.getMessage(), e);
            }
        }

        // Write atomically only if something changed
        if (addCount + removeCount + modifyCount > 0) {
            writeFileWithBom(xmlPath, result);
            out.println("[INFO] Batch saved: " + xmlPath);
        }

        out.println();
        out.println("=== meta-batch summary ===");
        out.println("  Object:    " + objType + "." + objName);
        out.println("  Added:     " + addCount);
        out.println("  Removed:   " + removeCount);
        out.println("  Modified:  " + modifyCount);
        if (warnCount > 0) out.println("  Warnings:  " + warnCount);
        if (addCount + removeCount + modifyCount == 0) out.println("  No changes applied.");
    }

    // ─── Batch operation dispatcher ─────────────────────────────────────

    private String applyBatchOperation(String content, String objType, String objName,
                                       Operation op) {
        if (op.getOp() == null) throw new IllegalArgumentException("Operation 'op' field is required");

        return switch (op.getOp()) {
            case "add-attribute"         -> batchAddAttr(content, objType, objName, "Attribute", op);
            case "add-dimension"         -> batchAddAttr(content, objType, objName, "Dimension", op);
            case "add-resource"          -> batchAddAttr(content, objType, objName, "Resource", op);
            case "add-enumValue"         -> addEnumValue(content, objName,
                                                requireName(op.getName(), "add-enumValue"));
            case "remove-attribute"      -> removeChild(content, "Attribute",
                                                requireName(op.getName(), "remove-attribute"));
            case "remove-dimension"      -> removeChild(content, "Dimension",
                                                requireName(op.getName(), "remove-dimension"));
            case "remove-resource"       -> removeChild(content, "Resource",
                                                requireName(op.getName(), "remove-resource"));
            case "remove-ts", "remove-tabularSection" ->
                                            removeChild(content, "TabularSection",
                                                requireName(op.getName(), op.getOp()));
            case "modify-attribute"      -> batchModifyAttr(content, "Attribute", op);
            case "modify-dimension"      -> batchModifyAttr(content, "Dimension", op);
            case "modify-resource"       -> batchModifyAttr(content, "Resource", op);
            case "modify-property"       -> batchModifyProperty(content, op);
            case "set-property"          -> batchSetProperty(content, op);
            case "add-property"          -> batchAddOrSetRootProperty(content, op);
            case "modify-tabularSection" -> batchModifyTabularSection(content, objType, objName, op);
            default -> throw new IllegalArgumentException("Unknown batch op: " + op.getOp());
        };
    }

    // ─── Batch add-attribute / add-dimension / add-resource ─────────────

    private String batchAddAttr(String content, String objType, String objName,
                                String xmlTag, Operation op) {
        String name = requireName(op.getName(), op.getOp());

        // Build shorthand for the existing addChildElement method.
        // The shorthand parser supports " + " for composite types (not "|"),
        // so normalise pipe-separated composite types to " + ".
        StringBuilder shorthand = new StringBuilder(name);
        if (op.getType() != null && !op.getType().isBlank()) {
            String normalizedType = normalizePipeType(op.getType());
            shorthand.append(": ").append(normalizedType);
        }
        if (op.getFillChecking() != null && "ShowError".equalsIgnoreCase(op.getFillChecking())) {
            shorthand.append(" | req");
        }
        if (op.getAfter() != null) shorthand.append(" >> after ").append(op.getAfter());
        if (op.getBefore() != null) shorthand.append(" << before ").append(op.getBefore());

        String result = addChildElement(content, objType, objName, xmlTag, shorthand.toString());

        // If synonym was provided as MlText — apply it to the newly added element
        if (op.getSynonym() != null) {
            result = batchApplySynonymToElement(result, xmlTag, name, op.getSynonym());
        }
        return result;
    }

    /**
     * Normalise a pipe-separated composite type string to the " + " format
     * understood by the existing shorthand parser, while preserving parentheses.
     * E.g. {@code "string(50)|number(15,2)"} → {@code "string(50) + number(15,2)"}.
     */
    private static String normalizePipeType(String typeStr) {
        List<String> parts = CompositeType.splitCompositeTypes(typeStr);
        if (parts.size() <= 1) return typeStr;
        return String.join(" + ", parts);
    }

    // ─── Batch modify-attribute / modify-dimension / modify-resource ─────

    private String batchModifyAttr(String content, String xmlTag, Operation op) {
        String name = requireName(op.getName(), op.getOp());

        int elemStart = findChildByName(content, xmlTag, name, true);
        if (elemStart < 0) {
            warn(xmlTag + " '" + name + "' not found for batch modify");
            return content;
        }
        int elemEnd = findClosingTag(content, xmlTag, elemStart);
        if (elemEnd < 0) throw new IllegalStateException("Malformed " + xmlTag + " for: " + name);

        String elemBlock = content.substring(elemStart, elemEnd);

        if (op.getSynonym() != null) {
            elemBlock = op.getSynonym().applyToBlock(elemBlock, "Synonym");
            info("Batch modified Synonym of " + xmlTag + " '" + name + "'");
            modifyCount++;
        }
        if (op.getFillChecking() != null) {
            String propPattern = "(<FillChecking>)[^<]*(</FillChecking>)";
            elemBlock = elemBlock.replaceFirst(propPattern, "$1" + esc(op.getFillChecking()) + "$2");
            info("Batch modified FillChecking of " + xmlTag + " '" + name + "'");
            modifyCount++;
        }
        if (op.getType() != null) {
            elemBlock = replaceTypeBlock(elemBlock, op.getType());
            info("Batch modified type of " + xmlTag + " '" + name + "'");
            modifyCount++;
        }
        if (op.getNewName() != null) {
            elemBlock = elemBlock.replace("<Name>" + esc(name) + "</Name>",
                                         "<Name>" + esc(op.getNewName()) + "</Name>");
            info("Batch renamed " + xmlTag + ": " + name + " -> " + op.getNewName());
            modifyCount++;
        }

        return content.substring(0, elemStart) + elemBlock + content.substring(elemEnd);
    }

    // ─── Batch modify-property ───────────────────────────────────────────

    /**
     * Handle {@code "op": "modify-property"}.
     * Value can be: String, Map (deserialized as MlText if {ru/en}), or List.
     */
    private String batchModifyProperty(String content, Operation op) {
        String propName = requireName(op.getName(), "modify-property");
        return applyPropertyValue(content, propName, op.getValue(), op.getSynonym());
    }

    /**
     * Handle {@code "op": "set-property"}.
     * Similar to modify-property; also accepts List values.
     */
    private String batchSetProperty(String content, Operation op) {
        String propName = requireName(op.getName(), "set-property");
        return applyPropertyValue(content, propName, op.getValue(), op.getSynonym());
    }

    /**
     * Handle {@code "op": "add-property"}.
     * Adds or replaces a root-level property.
     */
    private String batchAddOrSetRootProperty(String content, Operation op) {
        String propName = requireName(op.getName(), "add-property");
        return applyPropertyValue(content, propName, op.getValue(), op.getSynonym());
    }

    /**
     * Apply a value to a named root property.
     *
     * <ul>
     *   <li>If {@code synonym} (MlText) is provided — use it regardless of {@code value}.</li>
     *   <li>If {@code value} is already an MlText-like Map ({@code {"ru":...}}) — treat as MlText.</li>
     *   <li>If {@code value} is a List — serialise as repeated tags.</li>
     *   <li>Otherwise — simple scalar property.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private String applyPropertyValue(String content, String propName, Object value, MlText mlOverride) {
        // MLText properties (Synonym, Comment, Explanation) — always via MlText path
        boolean isMlProp = "Synonym".equals(propName) || "Comment".equals(propName)
                || "Explanation".equals(propName);

        if (mlOverride != null) {
            content = mlOverride.applyToBlock(content, propName);
            info("Batch modified " + propName + " (MLText)");
            modifyCount++;
            return content;
        }

        if (value instanceof Map<?,?> map) {
            // Treat as MLText: {"ru": "...", "en": "..."}
            MlText ml = new MlText();
            for (Map.Entry<?,?> e : map.entrySet()) {
                ml.setLang(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
            content = ml.applyToBlock(content, propName);
            info("Batch modified " + propName + " (MLText from map)");
            modifyCount++;
            return content;
        }

        if (isMlProp && value instanceof String sv) {
            content = replaceMlTextProperty(content, propName, sv);
            info("Batch modified " + propName + " (MLText string)");
            modifyCount++;
            return content;
        }

        if (value instanceof List<?> list) {
            // Serialize list as multiple child elements e.g. BasedOn
            return batchSetListProperty(content, propName, (List<String>) list);
        }

        if (value instanceof String sv) {
            content = addOrSetProperty(content, propName + "=" + sv);
            return content;
        }

        if (value instanceof Boolean bv) {
            content = addOrSetProperty(content, propName + "=" + bv);
            return content;
        }

        if (value instanceof Number nv) {
            content = addOrSetProperty(content, propName + "=" + nv);
            return content;
        }

        warn("Batch: property '" + propName + "' has unrecognised value type, skipping");
        return content;
    }

    /**
     * Set a list-valued property (e.g. BasedOn with multiple entries).
     * Replaces existing tag content or inserts after Synonym.
     */
    private String batchSetListProperty(String content, String propName, List<String> values) {
        // Build new tag block
        StringBuilder newBlock = new StringBuilder();
        newBlock.append("<").append(propName).append(">");
        for (String v : values) {
            newBlock.append(esc(v));
        }
        newBlock.append("</").append(propName).append(">");

        // Replace existing
        String existingPattern = "<" + Pattern.quote(propName) + ">.*?</" + Pattern.quote(propName) + ">";
        if (content.matches("(?s).*" + existingPattern + ".*")) {
            String result = content.replaceFirst(existingPattern,
                    Matcher.quoteReplacement(newBlock.toString()));
            info("Batch set list property: " + propName);
            modifyCount++;
            return result;
        }

        // Try self-closing
        String selfClose = "<" + Pattern.quote(propName) + "\\s*/>";
        if (content.matches("(?s).*" + selfClose + ".*")) {
            String result = content.replaceFirst(selfClose,
                    Matcher.quoteReplacement(newBlock.toString()));
            info("Batch set list property (was self-close): " + propName);
            modifyCount++;
            return result;
        }

        warn("Batch: could not find property '" + propName + "' for list set");
        return content;
    }

    // ─── Batch modify-tabularSection ─────────────────────────────────────

    private String batchModifyTabularSection(String content, String objType, String objName,
                                             Operation op) {
        String tsName = requireName(op.getName(), "modify-tabularSection");
        if (op.getOperations().isEmpty()) {
            warn("modify-tabularSection '" + tsName + "': no nested operations");
            return content;
        }

        int tsStart = findChildByName(content, "TabularSection", tsName);
        if (tsStart < 0) {
            throw new IllegalArgumentException("TabularSection '" + tsName + "' not found");
        }
        int tsEnd = findClosingTag(content, "TabularSection", tsStart);
        if (tsEnd < 0) throw new IllegalStateException("Malformed TabularSection: " + tsName);

        String tsBlock = content.substring(tsStart, tsEnd);

        for (Operation nested : op.getOperations()) {
            tsBlock = applyNestedTsOperation(tsBlock, objType, objName, tsName, nested);
        }

        return content.substring(0, tsStart) + tsBlock + content.substring(tsEnd);
    }

    private String applyNestedTsOperation(String tsBlock, String objType, String objName,
                                          String tsName, Operation op) {
        return switch (op.getOp()) {
            case "add-attribute" -> batchAddTsAttr(tsBlock, objType, tsName, op);
            case "remove-attribute" -> {
                // Remove within TS block
                String attrName = requireName(op.getName(), "remove-attribute in TS");
                int attrStart = findChildByName(tsBlock, "Attribute", attrName);
                if (attrStart < 0) {
                    warn("Attribute '" + attrName + "' not found in TS '" + tsName + "'");
                    yield tsBlock;
                }
                int attrEnd = findClosingTag(tsBlock, "Attribute", attrStart);
                if (attrEnd < 0) throw new IllegalStateException("Malformed Attribute XML in TS");
                int lineStart = tsBlock.lastIndexOf('\n', attrStart - 1);
                int removeFrom = lineStart >= 0 ? lineStart : attrStart;
                info("Batch removed attr from TS '" + tsName + "': " + attrName);
                removeCount++;
                yield tsBlock.substring(0, removeFrom) + tsBlock.substring(attrEnd);
            }
            case "modify-attribute" -> batchModifyTsAttr(tsBlock, tsName, op);
            default -> throw new IllegalArgumentException(
                    "Unsupported nested op in tabularSection: " + op.getOp());
        };
    }

    private String batchAddTsAttr(String tsBlock, String objType, String tsName, Operation op) {
        String name = requireName(op.getName(), "add-attribute in TS");

        // Check duplicate
        if (tsBlock.contains("<Name>" + name + "</Name>")) {
            warn("Attribute '" + name + "' already exists in TS '" + tsName + "', skipping");
            return tsBlock;
        }

        String indent = "\t\t\t\t";
        StringBuilder sb = new StringBuilder();
        AttrDef def = new AttrDef();
        def.name = name;
        if (op.getType() != null) {
            List<String> types = CompositeType.parse(op.getType());
            for (String t : types) {
                def.types.add(resolveType(t));
            }
        } else {
            def.types.add("String");
        }
        if (op.getFillChecking() != null && "ShowError".equalsIgnoreCase(op.getFillChecking())) {
            def.flags.add("req");
        }

        buildTsAttribute(sb, indent, def);
        String fragment = sb.toString();

        // Insert into ChildObjects
        int coIdx = tsBlock.indexOf("<ChildObjects/>");
        if (coIdx >= 0) {
            String newTs = tsBlock.substring(0, coIdx)
                    + "<ChildObjects>\n" + fragment
                    + "\t\t\t\t</ChildObjects>"
                    + tsBlock.substring(coIdx + "<ChildObjects/>".length());
            info("Batch added attr to TS '" + tsName + "': " + name);
            addCount++;
            return newTs;
        }

        coIdx = tsBlock.indexOf("</ChildObjects>");
        if (coIdx >= 0) {
            String newTs = tsBlock.substring(0, coIdx) + fragment + tsBlock.substring(coIdx);
            info("Batch added attr to TS '" + tsName + "': " + name);
            addCount++;
            return newTs;
        }

        warn("No ChildObjects found in TS '" + tsName + "'");
        return tsBlock;
    }

    private String batchModifyTsAttr(String tsBlock, String tsName, Operation op) {
        String name = requireName(op.getName(), "modify-attribute in TS");

        int attrStart = findChildByName(tsBlock, "Attribute", name);
        if (attrStart < 0) {
            warn("Attribute '" + name + "' not found in TS '" + tsName + "'");
            return tsBlock;
        }
        int attrEnd = findClosingTag(tsBlock, "Attribute", attrStart);
        if (attrEnd < 0) throw new IllegalStateException("Malformed Attribute in TS: " + name);

        String attrBlock = tsBlock.substring(attrStart, attrEnd);

        if (op.getSynonym() != null) {
            attrBlock = op.getSynonym().applyToBlock(attrBlock, "Synonym");
            modifyCount++;
        }
        if (op.getFillChecking() != null) {
            String pp = "(<FillChecking>)[^<]*(</FillChecking>)";
            attrBlock = attrBlock.replaceFirst(pp, "$1" + esc(op.getFillChecking()) + "$2");
            modifyCount++;
        }
        if (op.getType() != null) {
            attrBlock = replaceTypeBlock(attrBlock, op.getType());
            modifyCount++;
        }
        info("Batch modified attr in TS '" + tsName + "': " + name);
        return tsBlock.substring(0, attrStart) + attrBlock + tsBlock.substring(attrEnd);
    }

    // ─── Synonym helper ─────────────────────────────────────────────────

    /**
     * Apply MlText synonym to a just-added element (identified by xmlTag + name).
     */
    private String batchApplySynonymToElement(String content, String xmlTag,
                                              String elemName, MlText synonym) {
        int start = findChildByName(content, xmlTag, elemName, true);
        if (start < 0) return content;
        int end = findClosingTag(content, xmlTag, start);
        if (end < 0) return content;
        String block = content.substring(start, end);
        block = synonym.applyToBlock(block, "Synonym");
        return content.substring(0, start) + block + content.substring(end);
    }

    // ─── Validation helper ───────────────────────────────────────────────

    private static String requireName(String name, String opName) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("'name' is required for op: " + opName);
        }
        return name;
    }
}

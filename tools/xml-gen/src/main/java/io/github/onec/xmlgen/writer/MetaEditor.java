package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MetaBatchDsl;
import io.github.onec.xmlgen.dsl.MetaBatchDsl.Operation;
import io.github.onec.xmlgen.model.CompositeType;
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
            case "form" -> addSimpleChild(content, "Form", value);
            case "template" -> addSimpleChild(content, "Template", value);
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
        writeSynonym(sb, indent + "\t\t", splitCamelCase(def.name));
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

        // DataHistory
        sb.append(indent).append("\t\t<DataHistory>Use</DataHistory>\n");

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

        if ("Form".equals(xmlTag)) {
            sb.append(indent).append("\t\t<FormType>Ordinary</FormType>\n");
            sb.append(indent).append("\t\t<IncludeHelpInContents>false</IncludeHelpInContents>\n");
            sb.append(indent).append("\t\t<UsePurposes/>\n");
        } else if ("Template".equals(xmlTag)) {
            sb.append(indent).append("\t\t<TemplateType>SpreadsheetDocument</TemplateType>\n");
        } else if ("Command".equals(xmlTag)) {
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
        writeSynonym(sb, indent + "\t\t", splitCamelCase(def.name));
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
        String[] pipeparts = shorthand.split("\\|", 2);
        String main = pipeparts[0].trim();
        if (pipeparts.length > 1) {
            for (String f : pipeparts[1].split(",")) {
                String flag = f.trim().toLowerCase();
                if (!flag.isEmpty()) def.flags.add(flag);
            }
        }

        // Split by : for name and type
        int colonIdx = main.indexOf(':');
        if (colonIdx > 0) {
            def.name = main.substring(0, colonIdx).trim();
            String typeStr = main.substring(colonIdx + 1).trim();
            // Composite types: Type1 + Type2
            if (typeStr.contains(" + ")) {
                for (String part : typeStr.split("\\+")) {
                    def.types.add(resolveType(part.trim()));
                }
            } else {
                def.types.add(resolveType(typeStr));
            }
        } else {
            def.name = main;
            def.types.add("String"); // default — String(10)
        }

        return def;
    }

    private static class AttrDef {
        String name = "";
        List<String> types = new ArrayList<>();
        Set<String> flags = new LinkedHashSet<>();
        String after;
        String before;
    }

    // ─── Type resolution ────────────────────────────────────────────────

    private String resolveType(String type) {
        if (type == null || type.isEmpty()) return "String";

        // Parameterized: Число(15,2) -> Number(15,2) or string(50) -> String(50)
        Matcher m = Pattern.compile("^([^(]+)\\((.+)\\)$").matcher(type);
        if (m.matches()) {
            String base = m.group(1).trim();
            String params = m.group(2);
            // Russian synonyms
            for (Map.Entry<String, String> entry : RU_TYPE_SYNONYMS.entrySet()) {
                if (base.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue() + "(" + params + ")";
                }
            }
            // English canonical names — normalize case
            if (base.equalsIgnoreCase("String"))  return "String("  + params + ")";
            if (base.equalsIgnoreCase("Number"))  return "Number("  + params + ")";
            if (base.equalsIgnoreCase("Date"))    return "Date("    + params + ")";
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
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
        Files.write(path, result);
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

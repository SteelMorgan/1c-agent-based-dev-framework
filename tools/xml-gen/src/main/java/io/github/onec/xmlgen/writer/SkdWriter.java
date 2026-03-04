package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.TypeResolver;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Генератор XML для схемы компоновки данных 1С (DataCompositionSchema).
 * 
 * Phase 5: Базовая реализация (простые запросы без сложных структур).
 */
public class SkdWriter extends XmlWriter {
    
    private final OutputFormat format;
    
    private static final Map<String, String> DCS_NAMESPACES = new HashMap<>();
    static {
        DCS_NAMESPACES.put("", "http://v8.1c.ru/8.1/data-composition-system/schema");
        DCS_NAMESPACES.put("dcscom", "http://v8.1c.ru/8.1/data-composition-system/common");
        DCS_NAMESPACES.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        DCS_NAMESPACES.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        DCS_NAMESPACES.put("v8", "http://v8.1c.ru/8.1/data/core");
        DCS_NAMESPACES.put("v8ui", "http://v8.1c.ru/8.1/data/ui");
        DCS_NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
        DCS_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }
    
    public SkdWriter(OutputFormat format) {
        this.format = format;
    }
    
    /**
     * Создать Template.xml из DSL.
     * 
     * @param dsl JSON DSL схемы компоновки данных
     * @param outputPath путь к Template.xml
     */
    public void create(SkdDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            createDesigner(dsl, outputPath);
        } else {
            // EDT формат: тот же XML, просто файл может иметь расширение .dcs
            createDesigner(dsl, outputPath);
        }
    }
    
    private void createDesigner(SkdDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        createWriter(outputPath, false, DCS_NAMESPACES); // БЕЗ BOM для Template.xml
        writeXmlDeclaration();
        
        Map<String, String> rootAttrs = new HashMap<>();
        writeRootElement("DataCompositionSchema", DCS_NAMESPACES, rootAttrs);
        
        // Data sources
        if (dsl.getDataSources() != null && !dsl.getDataSources().isEmpty()) {
            for (SkdDsl.DataSource ds : dsl.getDataSources()) {
                writeDataSource(ds);
            }
        } else {
            // Умолчание: один локальный источник
            writeDataSource(new SkdDsl.DataSource("ИсточникДанных1", "Local"));
        }
        
        // Data sets
        if (dsl.getDataSets() != null && !dsl.getDataSets().isEmpty()) {
            for (SkdDsl.DataSet ds : dsl.getDataSets()) {
                writeDataSet(ds);
            }
        }
        
        // Total fields
        if (dsl.getTotalFields() != null && !dsl.getTotalFields().isEmpty()) {
            for (SkdDsl.TotalField tf : dsl.getTotalFields()) {
                writeTotalField(tf);
            }
        }
        
        // Parameters
        if (dsl.getParameters() != null && !dsl.getParameters().isEmpty()) {
            for (SkdDsl.Parameter param : dsl.getParameters()) {
                writeParameter(param);
            }
        }
        
        // Settings variants
        if (dsl.getSettingsVariants() != null && !dsl.getSettingsVariants().isEmpty()) {
            for (SkdDsl.SettingsVariant sv : dsl.getSettingsVariants()) {
                writeSettingsVariant(sv);
            }
        } else {
            // Умолчание: один вариант "Основной"
            writeDefaultSettingsVariant();
        }
        
        writer.writeEndElement(); // DataCompositionSchema
        close();
        
        System.out.println("Created SKD schema: " + outputPath);
    }
    
    /**
     * Записать источник данных.
     */
    private void writeDataSource(SkdDsl.DataSource ds) throws XMLStreamException {
        startElement("dataSource");
        writeElement("name", ds.getName());
        writeElement("dataSourceType", ds.getType() != null ? ds.getType() : "Local");
        endElement(); // dataSource
    }
    
    /**
     * Записать набор данных.
     */
    private void writeDataSet(SkdDsl.DataSet ds) throws XMLStreamException {
        writer.writeCharacters("\t");
        writer.writeStartElement("dataSet");
        
        // Определить тип набора данных
        String dataSetType = ds.getType();
        writer.writeAttribute("xsi:type", dataSetType);
        writer.writeCharacters("\n");
        indentLevel = 2;
        
        writeElement("name", ds.getName() != null ? ds.getName() : "НаборДанных1");
        
        // Fields
        if (ds.getFields() != null && !ds.getFields().isEmpty()) {
            for (SkdDsl.Field field : ds.getFields()) {
                writeField(field);
            }
        }
        
        // Data source
        String sourceName = ds.getSource() != null ? ds.getSource() : "ИсточникДанных1";
        writeElement("dataSource", sourceName);
        
        // Query (для DataSetQuery)
        if (ds.getQuery() != null) {
            writeElement("query", ds.getQuery());
        }
        
        // ObjectName (для DataSetObject)
        if (ds.getObjectName() != null) {
            writeElement("objectName", ds.getObjectName());
        }
        
        // Items (для DataSetUnion) - вложенные наборы
        if (ds.getItems() != null && !ds.getItems().isEmpty()) {
            for (SkdDsl.DataSet item : ds.getItems()) {
                writeDataSet(item); // Рекурсивный вызов
            }
        }
        
        // AutoFillFields
        if (ds.getAutoFillFields() != null && !ds.getAutoFillFields()) {
            writeElement("autoFillAvailableFields", "false");
        }
        
        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // dataSet
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать поле набора данных.
     */
    private void writeField(SkdDsl.Field field) throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("field");
        writer.writeAttribute("xsi:type", "DataSetFieldField");
        writer.writeCharacters("\n");
        indentLevel = 3;
        
        String dataPath = field.getDataPath() != null ? field.getDataPath() : field.getField();
        writeElement("dataPath", dataPath);
        
        String fieldName = field.getField() != null ? field.getField() : field.getDataPath();
        writeElement("field", fieldName);
        
        // Title
        if (field.getTitle() != null) {
            writeLocalStringType("title", field.getTitle());
        }
        
        // Type
        if (field.getType() != null) {
            writeFieldType(field.getType());
        }
        
        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // field
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать тип поля.
     */
    private void writeFieldType(String dslType) throws XMLStreamException {
        TypeResolver.TypeInfo typeInfo = TypeResolver.resolve(dslType);
        
        startElement("valueType");
        startElement("v8:Type");
        writer.writeCharacters(typeInfo.getXmlType());
        endElement(); // v8:Type
        
        // Квалификаторы
        if (typeInfo.getQualifiers() != null) {
            if (typeInfo.getQualifiers() instanceof TypeResolver.StringQualifiers) {
                TypeResolver.StringQualifiers sq = (TypeResolver.StringQualifiers) typeInfo.getQualifiers();
                startElement("v8:StringQualifiers");
                writeElement("v8:Length", String.valueOf(sq.getLength()));
                writeElement("v8:AllowedLength", sq.getAllowedLengthXml());
                endElement();
            } else if (typeInfo.getQualifiers() instanceof TypeResolver.NumberQualifiers) {
                TypeResolver.NumberQualifiers nq = (TypeResolver.NumberQualifiers) typeInfo.getQualifiers();
                startElement("v8:NumberQualifiers");
                writeElement("v8:Digits", String.valueOf(nq.getDigits()));
                writeElement("v8:FractionDigits", String.valueOf(nq.getFractionDigits()));
                writeElement("v8:AllowedSign", nq.getAllowedSign());
                endElement();
            } else if (typeInfo.getQualifiers() instanceof TypeResolver.DateQualifiers) {
                TypeResolver.DateQualifiers dq = (TypeResolver.DateQualifiers) typeInfo.getQualifiers();
                startElement("v8:DateQualifiers");
                writeElement("v8:DateFractions", dq.getDateFractionsXml());
                endElement();
            }
        }
        
        endElement(); // valueType
    }
    
    /**
     * Записать итоговое поле.
     */
    private void writeTotalField(SkdDsl.TotalField tf) throws XMLStreamException {
        startElement("totalField");
        writeElement("dataPath", tf.getDataPath());
        writeElement("expression", tf.getExpression());
        endElement(); // totalField
    }
    
    /**
     * Записать параметр.
     */
    private void writeParameter(SkdDsl.Parameter param) throws XMLStreamException {
        startElement("parameter");
        writeElement("name", param.getName());
        
        if (param.getTitle() != null) {
            writeLocalStringType("title", param.getTitle());
        }
        
        if (param.getType() != null) {
            writeParameterType(param.getType());
        }
        
        if (param.getValue() != null) {
            writeParameterValue(param.getValue(), param.getType());
        }
        
        endElement(); // parameter
    }
    
    /**
     * Записать тип параметра.
     */
    private void writeParameterType(String dslType) throws XMLStreamException {
        TypeResolver.TypeInfo typeInfo = TypeResolver.resolve(dslType);
        
        startElement("valueType");
        startElement("v8:Type");
        writer.writeCharacters(typeInfo.getXmlType());
        endElement(); // v8:Type
        endElement(); // valueType
    }
    
    /**
     * Записать значение параметра.
     */
    private void writeParameterValue(Object value, String type) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("value");
        
        String xsiType = "xs:string";
        if (type != null) {
            if (type.contains("date")) {
                xsiType = "xs:dateTime";
            } else if (type.contains("boolean")) {
                xsiType = "xs:boolean";
            } else if (type.contains("decimal") || type.contains("number")) {
                xsiType = "xs:decimal";
            }
        }
        
        writer.writeAttribute("xsi:type", xsiType);
        writer.writeCharacters(value.toString());
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать вариант настроек.
     */
    private void writeSettingsVariant(SkdDsl.SettingsVariant sv) throws XMLStreamException {
        startElement("settingsVariant");
        
        writeElement("dcsset:name", sv.getName());
        
        if (sv.getPresentation() != null) {
            writer.writeCharacters("\t\t");
            writer.writeStartElement("dcsset:presentation");
            writer.writeAttribute("xsi:type", "xs:string");
            writer.writeCharacters(sv.getPresentation());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        
        // Settings
        if (sv.getSettings() != null) {
            writeSettings(sv.getSettings());
        } else {
            writeEmptySettings();
        }
        
        endElement(); // settingsVariant
    }
    
    /**
     * Записать настройки.
     */
    private void writeSettings(SkdDsl.Settings settings) throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:settings");
        
        // Добавить дополнительные namespaces для settings
        writer.writeNamespace("style", "http://v8.1c.ru/8.1/data/ui/style");
        writer.writeNamespace("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        writer.writeNamespace("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        writer.writeNamespace("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        
        writer.writeCharacters("\n");
        indentLevel = 3;
        
        // Selection
        if (settings.getSelection() != null && !settings.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String field : settings.getSelection()) {
                writeSelectionItem(field);
            }
            endElement(); // dcsset:selection
        }
        
        // Filter
        if (settings.getFilter() != null && !settings.getFilter().isEmpty()) {
            startElement("dcsset:filter");
            for (String filterStr : settings.getFilter()) {
                writeFilterItem(filterStr);
            }
            endElement(); // dcsset:filter
        }
        
        // Order
        if (settings.getOrder() != null && !settings.getOrder().isEmpty()) {
            startElement("dcsset:order");
            for (String orderStr : settings.getOrder()) {
                writeOrderItem(orderStr);
            }
            endElement(); // dcsset:order
        }
        
        // ConditionalAppearance
        if (settings.getConditionalAppearance() != null && !settings.getConditionalAppearance().isEmpty()) {
            startElement("dcsset:conditionalAppearance");
            for (SkdDsl.ConditionalAppearanceItem item : settings.getConditionalAppearance()) {
                writeConditionalAppearanceItem(item);
            }
            endElement(); // dcsset:conditionalAppearance
        }
        
        // Structure
        if (settings.getStructure() != null && !settings.getStructure().isEmpty()) {
            startElement("dcsset:structure");
            for (SkdDsl.Structure struct : settings.getStructure()) {
                writeStructure(struct);
            }
            endElement(); // dcsset:structure
        }
        
        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // dcsset:settings
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать элемент выборки.
     */
    private void writeSelectionItem(String field) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:SelectedItemField");
        writer.writeCharacters("\n");
        indentLevel++;
        writeElement("dcsset:field", field);
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcsset:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать элемент структуры.
     */
    private void writeStructure(SkdDsl.Structure struct) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:StructureItemGroup");
        writer.writeCharacters("\n");
        indentLevel++;
        
        // Group items
        if (struct.getGroupBy() != null && !struct.getGroupBy().isEmpty()) {
            startElement("dcsset:groupItems");
            for (String field : struct.getGroupBy()) {
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeStartElement("dcsset:item");
                writer.writeAttribute("xsi:type", "dcsset:GroupItemField");
                writer.writeCharacters("\n");
                indentLevel++;
                writeElement("dcsset:field", field);
                indentLevel--;
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeEndElement(); // dcsset:item
                writer.writeCharacters("\n");
            }
            endElement(); // dcsset:groupItems
        }
        
        // Selection
        if (struct.getSelection() != null && !struct.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String field : struct.getSelection()) {
                writeSelectionItem(field);
            }
            endElement(); // dcsset:selection
        }
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcsset:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать пустые настройки по умолчанию.
     */
    private void writeEmptySettings() throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:settings");
        writer.writeNamespace("style", "http://v8.1c.ru/8.1/data/ui/style");
        writer.writeNamespace("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        writer.writeNamespace("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        writer.writeNamespace("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать вариант настроек по умолчанию.
     */
    private void writeDefaultSettingsVariant() throws XMLStreamException {
        startElement("settingsVariant");
        writeElement("dcsset:name", "Основной");
        
        writer.writeCharacters("\t\t");
        writer.writeStartElement("dcsset:presentation");
        writer.writeAttribute("xsi:type", "xs:string");
        writer.writeCharacters("Основной");
        writer.writeEndElement();
        writer.writeCharacters("\n");
        
        writeEmptySettings();
        
        endElement(); // settingsVariant
    }
    
    /**
     * Записать LocalStringType.
     */
    private void writeLocalStringType(String elementName, String text) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement(elementName);
        writer.writeAttribute("xsi:type", "v8:LocalStringType");
        writer.writeCharacters("\n");
        indentLevel++;
        
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // elementName
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать элемент фильтра (базовая реализация).
     * Формат: "Поле оператор значение"
     */
    private void writeFilterItem(String filterStr) throws XMLStreamException {
        // Простой парсинг: "Field op value"
        String[] parts = filterStr.split("\\s+", 3);
        if (parts.length < 2) {
            return; // Некорректный формат
        }
        
        String field = parts[0];
        String op = parts[1];
        String value = parts.length > 2 ? parts[2] : null;
        
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:FilterItemComparison");
        writer.writeCharacters("\n");
        indentLevel++;
        
        // Left value (field)
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:left");
        writer.writeAttribute("xsi:type", "dcscor:Field");
        writer.writeCharacters(field);
        writer.writeEndElement(); // dcsset:left
        writer.writeCharacters("\n");
        
        // Comparison type
        String comparisonType = mapOperatorToComparisonType(op);
        writeElement("dcsset:comparisonType", comparisonType);
        
        // Right value
        if (value != null && !value.equals("_")) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dcsset:right");
            writer.writeAttribute("xsi:type", detectValueType(value));
            writer.writeCharacters(value);
            writer.writeEndElement(); // dcsset:right
            writer.writeCharacters("\n");
        }
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcsset:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Маппинг оператора в ComparisonType.
     */
    private String mapOperatorToComparisonType(String op) {
        return switch (op) {
            case "=" -> "Equal";
            case "<>" -> "NotEqual";
            case ">" -> "Greater";
            case ">=" -> "GreaterOrEqual";
            case "<" -> "Less";
            case "<=" -> "LessOrEqual";
            case "in" -> "InList";
            case "notIn" -> "NotInList";
            case "contains" -> "Contains";
            case "filled" -> "Filled";
            case "notFilled" -> "NotFilled";
            default -> "Equal";
        };
    }
    
    /**
     * Определить тип значения.
     */
    private String detectValueType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "xs:boolean";
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
            return "xs:dateTime";
        }
        if (value.matches("-?\\d+(\\.\\d+)?")) {
            return "xs:decimal";
        }
        return "xs:string";
    }
    
    /**
     * Записать элемент сортировки.
     * Формат: "Field" или "Field desc" или "Field asc"
     */
    private void writeOrderItem(String orderStr) throws XMLStreamException {
        String[] parts = orderStr.split("\\s+");
        String field = parts[0];
        String direction = parts.length > 1 ? parts[1] : "asc";
        
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeAttribute("xsi:type", "dcsset:OrderItemField");
        writer.writeCharacters("\n");
        indentLevel++;
        
        writeElement("dcsset:field", field);
        
        String orderType = "desc".equalsIgnoreCase(direction) ? "Desc" : "Asc";
        writeElement("dcsset:orderType", orderType);
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcsset:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать элемент условного оформления.
     */
    private void writeConditionalAppearanceItem(SkdDsl.ConditionalAppearanceItem item) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcsset:item");
        writer.writeCharacters("\n");
        indentLevel++;
        
        // Selection (поля, к которым применяется оформление)
        if (item.getSelection() != null && !item.getSelection().isEmpty()) {
            startElement("dcsset:selection");
            for (String field : item.getSelection()) {
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeStartElement("dcsset:item");
                writer.writeAttribute("xsi:type", "dcsset:SelectedItemField");
                writer.writeCharacters("\n");
                indentLevel++;
                writeElement("dcsset:field", field);
                indentLevel--;
                writer.writeCharacters("\t".repeat(indentLevel));
                writer.writeEndElement(); // dcsset:item
                writer.writeCharacters("\n");
            }
            endElement(); // dcsset:selection
        }
        
        // Filter (условия применения)
        if (item.getFilter() != null && !item.getFilter().isEmpty()) {
            startElement("dcsset:filter");
            for (String filterStr : item.getFilter()) {
                writeFilterItem(filterStr);
            }
            endElement(); // dcsset:filter
        }
        
        // Appearance (параметры оформления)
        if (item.getAppearance() != null && !item.getAppearance().isEmpty()) {
            startElement("dcsset:appearance");
            for (Map.Entry<String, Object> entry : item.getAppearance().entrySet()) {
                writeAppearanceParameter(entry.getKey(), entry.getValue());
            }
            endElement(); // dcsset:appearance
        }
        
        // Presentation (описание правила)
        if (item.getPresentation() != null) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dcsset:presentation");
            writer.writeAttribute("xsi:type", "xs:string");
            writer.writeCharacters(item.getPresentation());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcsset:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать параметр оформления.
     */
    private void writeAppearanceParameter(String paramName, Object value) throws XMLStreamException {
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcscor:item");
        writer.writeAttribute("xsi:type", "dcsset:SettingsParameterValue");
        writer.writeCharacters("\n");
        indentLevel++;
        
        // Parameter name
        writeElement("dcscor:parameter", paramName);
        
        // Value with type detection
        String valueStr = value.toString();
        String valueType = detectAppearanceValueType(paramName, valueStr);
        
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("dcscor:value");
        writer.writeAttribute("xsi:type", valueType);
        
        // Для LocalStringType нужна специальная структура
        if ("v8:LocalStringType".equals(valueType)) {
            writer.writeCharacters("\n");
            indentLevel++;
            startElement("v8:item");
            writeElement("v8:lang", "ru");
            writeElement("v8:content", valueStr);
            endElement(); // v8:item
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
        } else {
            writer.writeCharacters(valueStr);
        }
        
        writer.writeEndElement(); // dcscor:value
        writer.writeCharacters("\n");
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // dcscor:item
        writer.writeCharacters("\n");
    }
    
    /**
     * Определить тип значения параметра оформления.
     */
    private String detectAppearanceValueType(String paramName, String value) {
        // Цвета (style:, web:, win:)
        if (value.startsWith("style:") || value.startsWith("web:") || value.startsWith("win:")) {
            return "v8ui:Color";
        }
        
        // Текстовые параметры
        if ("Текст".equals(paramName) || "Заголовок".equals(paramName)) {
            return "v8:LocalStringType";
        }
        
        // Boolean
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "xs:boolean";
        }
        
        // По умолчанию — строка
        return "xs:string";
    }
}

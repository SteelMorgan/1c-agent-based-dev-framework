package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.IdGenerator;
import io.github.onec.xmlgen.model.TypeResolver;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Генератор XML для управляемой формы 1С.
 * 
 * Phase 3: Полная реализация UI-элементов (топ-15).
 */
public class FormWriter extends XmlWriter {
    
    private final OutputFormat format;
    private final IdGenerator elementIdGen = new IdGenerator();
    private final IdGenerator attributeIdGen = new IdGenerator();
    private final IdGenerator commandIdGen = new IdGenerator();
    
    public FormWriter(OutputFormat format) {
        this.format = format;
    }
    
    /**
     * Создать Form.xml из DSL.
     * 
     * @param dsl JSON DSL формы
     * @param outputPath путь к Form.xml
     */
    public void create(FormDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            createDesigner(dsl, outputPath);
        } else {
            throw new UnsupportedOperationException("EDT format not implemented yet");
        }
    }
    
    private void createDesigner(FormDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        createWriter(outputPath, false, FORM_NAMESPACES); // БЕЗ BOM для Form.xml
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(FORM_NAMESPACES);
        allNamespaces.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        allNamespaces.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
        writeRootElement("Form", allNamespaces, rootAttrs);
        
        // Title
        if (dsl.getTitle() != null) {
            writeElement("Title", dsl.getTitle());
        }
        
        // Properties
        if (dsl.getProperties() != null) {
            writeProperties(dsl.getProperties());
        }
        
        // AutoCommandBar (обязательный, id=-1)
        writer.writeCharacters("\t");
        writer.writeEmptyElement("AutoCommandBar");
        writer.writeAttribute("name", "ФормаКоманднаяПанель");
        writer.writeAttribute("id", "-1");
        writer.writeCharacters("\n");
        
        // Events
        if (dsl.getEvents() != null && !dsl.getEvents().isEmpty()) {
            writeEvents(dsl.getEvents());
        }
        
        // ChildItems
        if (dsl.getElements() != null && !dsl.getElements().isEmpty()) {
            startElement("ChildItems");
            for (Map<String, Object> element : dsl.getElements()) {
                writeElement(element, 2);
            }
            endElement(); // ChildItems
        } else {
            // Пустой ChildItems
            startElement("ChildItems");
            endElement();
        }
        
        // Attributes
        if (dsl.getAttributes() != null && !dsl.getAttributes().isEmpty()) {
            startElement("Attributes");
            for (FormDsl.Attribute attr : dsl.getAttributes()) {
                writeAttribute(attr);
            }
            endElement(); // Attributes
        }
        
        // Commands
        if (dsl.getCommands() != null && !dsl.getCommands().isEmpty()) {
            startElement("Commands");
            for (FormDsl.Command cmd : dsl.getCommands()) {
                writeCommand(cmd);
            }
            endElement(); // Commands
        }
        
        writer.writeEndElement(); // Form
        close();
        
        System.out.println("Created form: " + outputPath);
    }
    
    /**
     * Записать свойства формы.
     */
    private void writeProperties(Map<String, Object> properties) throws XMLStreamException {
        for (Map.Entry<String, Object> prop : properties.entrySet()) {
            String xmlName = toPascalCase(prop.getKey());
            writeElement(xmlName, prop.getValue().toString());
        }
    }
    
    /**
     * Записать события формы.
     */
    private void writeEvents(Map<String, String> events) throws XMLStreamException {
        startElement("Events");
        for (Map.Entry<String, String> event : events.entrySet()) {
            writer.writeCharacters("\t\t");
            writer.writeStartElement("Event");
            writer.writeAttribute("name", event.getKey());
            writer.writeCharacters(event.getValue());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        endElement(); // Events
    }
    
    /**
     * Записать реквизит формы.
     */
    private void writeAttribute(FormDsl.Attribute attr) throws XMLStreamException {
        int id = attributeIdGen.next();
        
        writer.writeCharacters("\t\t");
        writer.writeStartElement("Attribute");
        writer.writeAttribute("name", attr.getName());
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        indentLevel = 3;
        
        // Title
        if (attr.getTitle() != null) {
            writeMultilingualString("Title", attr.getTitle());
        }
        
        // Type
        if (attr.getType() != null) {
            writeType(attr.getType());
        }
        
        // MainAttribute
        if (attr.getMain() != null && attr.getMain()) {
            writeElement("MainAttribute", "true");
        }
        
        // Columns (для ValueTable/ValueTree)
        if (attr.getColumns() != null && !attr.getColumns().isEmpty()) {
            startElement("Columns");
            IdGenerator columnIdGen = new IdGenerator();
            for (FormDsl.Column col : attr.getColumns()) {
                writeColumn(col, columnIdGen.next());
            }
            endElement(); // Columns
        }
        
        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Attribute
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать колонку коллекции.
     */
    private void writeColumn(FormDsl.Column col, int id) throws XMLStreamException {
        writer.writeCharacters("\t\t\t\t");
        writer.writeStartElement("Column");
        writer.writeAttribute("name", col.getName());
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        indentLevel = 5;
        
        if (col.getTitle() != null) {
            writeMultilingualString("Title", col.getTitle());
        }
        
        if (col.getType() != null) {
            writeType(col.getType());
        }
        
        indentLevel = 4;
        writer.writeCharacters("\t\t\t\t");
        writer.writeEndElement(); // Column
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать тип.
     */
    private void writeType(String dslType) throws XMLStreamException {
        TypeResolver.TypeInfo typeInfo = TypeResolver.resolve(dslType);
        
        startElement("Type");
        writeElement("v8:Type", typeInfo.getXmlType());
        
        // Квалификаторы
        if (typeInfo.getQualifiers() != null) {
            if (typeInfo.getQualifiers() instanceof TypeResolver.StringQualifiers) {
                TypeResolver.StringQualifiers sq = (TypeResolver.StringQualifiers) typeInfo.getQualifiers();
                startElement("v8:StringQualifiers");
                writeElement("v8:Length", String.valueOf(sq.getLength()));
                writeElement("v8:AllowedLength", sq.getAllowedLength());
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
                writeElement("v8:DateFractions", dq.getDateFractions());
                endElement();
            }
        }
        
        endElement(); // Type
    }
    
    /**
     * Записать команду формы.
     */
    private void writeCommand(FormDsl.Command cmd) throws XMLStreamException {
        int id = commandIdGen.next();
        
        writer.writeCharacters("\t\t");
        writer.writeStartElement("Command");
        writer.writeAttribute("name", cmd.getName());
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        indentLevel = 3;
        
        if (cmd.getTitle() != null) {
            writeMultilingualString("Title", cmd.getTitle());
        }
        
        if (cmd.getTooltip() != null) {
            writeMultilingualString("ToolTip", cmd.getTooltip());
        }
        
        if (cmd.getAction() != null) {
            writeElement("Action", cmd.getAction());
        }
        
        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Command
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать многоязычную строку.
     */
    private void writeMultilingualString(String elementName, String text) throws XMLStreamException {
        startElement(elementName);
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        endElement(); // elementName
    }
    
    /**
     * Преобразовать camelCase в PascalCase.
     */
    private String toPascalCase(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        return Character.toUpperCase(camelCase.charAt(0)) + camelCase.substring(1);
    }
    
    /**
     * Записать UI-элемент.
     * 
     * @param element элемент (Map с ключом типа: "input", "group", "table" и т.д.)
     * @param depth уровень вложенности (для отступов)
     */
    private void writeElement(Map<String, Object> element, int depth) throws XMLStreamException {
        // Определяем тип элемента по ключу
        String type = null;
        Object value = null;
        
        for (Map.Entry<String, Object> entry : element.entrySet()) {
            String key = entry.getKey();
            if (isElementType(key)) {
                type = key;
                value = entry.getValue();
                break;
            }
        }
        
        if (type == null) {
            return; // Неизвестный элемент
        }
        
        // Имя элемента (из значения ключа типа или из свойства "name")
        String name = value != null ? value.toString() : null;
        if (element.containsKey("name")) {
            name = element.get("name").toString();
        }
        
        int id = elementIdGen.next();
        
        // Генерируем элемент в зависимости от типа
        switch (type) {
            case "input":
                writeInputField(element, name, id, depth);
                break;
            case "group":
                writeUsualGroup(element, name, id, depth);
                break;
            case "table":
                writeTable(element, name, id, depth);
                break;
            case "button":
                writeButton(element, name, id, depth);
                break;
            case "label":
                writeLabelDecoration(element, name, id, depth);
                break;
            case "labelField":
                writeLabelField(element, name, id, depth);
                break;
            case "check":
                writeCheckBoxField(element, name, id, depth);
                break;
            case "pages":
                writePages(element, name, id, depth);
                break;
            case "page":
                writePage(element, name, id, depth);
                break;
            case "picture":
                writePictureDecoration(element, name, id, depth);
                break;
            case "picField":
                writePictureField(element, name, id, depth);
                break;
            case "calendar":
                writeCalendarField(element, name, id, depth);
                break;
            case "cmdBar":
                writeCommandBar(element, name, id, depth);
                break;
            case "popup":
                writePopup(element, name, id, depth);
                break;
            default:
                // Неизвестный тип
                break;
        }
    }
    
    /**
     * Проверить, является ли ключ типом элемента.
     */
    private boolean isElementType(String key) {
        return key.equals("input") || key.equals("group") || key.equals("table") ||
               key.equals("button") || key.equals("label") || key.equals("labelField") ||
               key.equals("check") || key.equals("pages") || key.equals("page") ||
               key.equals("picture") || key.equals("picField") || key.equals("calendar") ||
               key.equals("cmdBar") || key.equals("popup");
    }
    
    /**
     * Записать InputField.
     */
    private void writeInputField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("InputField");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip (автоматически)
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // InputField
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать UsualGroup.
     */
    private void writeUsualGroup(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("UsualGroup");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Group (ориентация)
        String groupType = element.get("group") != null ? element.get("group").toString() : "Vertical";
        writeElement("Group", toPascalCase(groupType));
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            if (!children.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> child : children) {
                    writeElement(child, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // UsualGroup
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать Table.
     */
    private void writeTable(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("Table");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu
        int contextMenuId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ContextMenu");
        writer.writeAttribute("name", name + "КонтекстноеМеню");
        writer.writeAttribute("id", String.valueOf(contextMenuId));
        writer.writeCharacters("\n");
        
        // AutoCommandBar
        int cmdBarId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("AutoCommandBar");
        writer.writeAttribute("name", name + "КоманднаяПанель");
        writer.writeAttribute("id", String.valueOf(cmdBarId));
        writer.writeCharacters("\n");
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems (колонки)
        if (element.containsKey("columns")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) element.get("columns");
            if (!columns.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> col : columns) {
                    writeElement(col, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Table
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать Button.
     */
    private void writeButton(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("Button");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // CommandName
        if (element.containsKey("command")) {
            writeElement("CommandName", "Form.Command." + element.get("command").toString());
        } else if (element.containsKey("stdCommand")) {
            writeElement("CommandName", "Form.StandardCommand." + element.get("stdCommand").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Button
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать LabelDecoration.
     */
    private void writeLabelDecoration(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("LabelDecoration");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // LabelDecoration
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать LabelField.
     */
    private void writeLabelField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("LabelField");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // LabelField
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать CheckBoxField.
     */
    private void writeCheckBoxField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("CheckBoxField");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // CheckBoxField
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать Pages.
     */
    private void writePages(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("Pages");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems (страницы)
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            if (!children.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> child : children) {
                    writeElement(child, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Pages
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать Page.
     */
    private void writePage(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("Page");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            if (!children.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> child : children) {
                    writeElement(child, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Page
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать PictureDecoration.
     */
    private void writePictureDecoration(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("PictureDecoration");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Picture
        if (element.containsKey("src")) {
            writeElement("Picture", element.get("src").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // PictureDecoration
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать PictureField.
     */
    private void writePictureField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("PictureField");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // PictureField
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать CalendarField.
     */
    private void writeCalendarField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("CalendarField");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // DataPath
        if (element.containsKey("path")) {
            writeElement("DataPath", element.get("path").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // CalendarField
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать CommandBar.
     */
    private void writeCommandBar(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("CommandBar");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            if (!children.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> child : children) {
                    writeElement(child, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // CommandBar
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать Popup.
     */
    private void writePopup(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement("Popup");
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");
        
        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
        
        // ChildItems
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            if (!children.isEmpty()) {
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeStartElement("ChildItems");
                writer.writeCharacters("\n");
                for (Map<String, Object> child : children) {
                    writeElement(child, depth + 2);
                }
                writer.writeCharacters("\t".repeat(depth + 1));
                writer.writeEndElement(); // ChildItems
                writer.writeCharacters("\n");
            }
        }
        
        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Popup
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать свойства элемента.
     */
    private void writeElementProperties(Map<String, Object> element) throws XMLStreamException {
        // Пропускаем служебные ключи
        for (Map.Entry<String, Object> entry : element.entrySet()) {
            String key = entry.getKey();
            if (isElementType(key) || key.equals("name") || key.equals("title") || 
                key.equals("path") || key.equals("children") || key.equals("columns") ||
                key.equals("command") || key.equals("stdCommand") || key.equals("src")) {
                continue;
            }
            
            // Преобразуем ключ в PascalCase
            String xmlName = toPascalCase(key);
            Object value = entry.getValue();
            
            // Обработка boolean
            if (value instanceof Boolean) {
                writeElement(xmlName, value.toString().toLowerCase());
            } else {
                writeElement(xmlName, value.toString());
            }
        }
    }
    
    /**
     * Записать автоматические элементы (ContextMenu, ExtendedTooltip).
     */
    private void writeAutoElements(String name, int parentId, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        // ContextMenu
        int contextMenuId = elementIdGen.next();
        writer.writeCharacters(indent);
        writer.writeEmptyElement("ContextMenu");
        writer.writeAttribute("name", name + "КонтекстноеМеню");
        writer.writeAttribute("id", String.valueOf(contextMenuId));
        writer.writeCharacters("\n");
        
        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters(indent);
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
    }
}

package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.IdGenerator;
import io.github.onec.xmlgen.model.TypeResolver;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
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
    
    /**
     * Маппинг DSL-ключа на FormElementType из mdclasses.
     * DSL-ключ — краткое имя в JSON, FormElementType — каноническое имя 1С.
     */
    private static final Map<String, FormElementType> DSL_TO_FORM_ELEMENT_TYPE = Map.ofEntries(
        Map.entry("input", FormElementType.INPUT_FIELD),
        Map.entry("group", FormElementType.USUAL_GROUP),
        Map.entry("table", FormElementType.TABLE),
        Map.entry("button", FormElementType.BUTTON),
        Map.entry("label", FormElementType.LABEL_DECORATION),
        Map.entry("labelField", FormElementType.LABEL_FIELD),
        Map.entry("check", FormElementType.CHECK_BOX_FIELD),
        Map.entry("pages", FormElementType.PAGES),
        Map.entry("page", FormElementType.PAGE),
        Map.entry("picture", FormElementType.PICTURE_DECORATION),
        Map.entry("picField", FormElementType.PICTURE_FIELD),
        Map.entry("calendar", FormElementType.CALENDAR_FIELD),
        Map.entry("cmdBar", FormElementType.COMMAND_BAR),
        Map.entry("popup", FormElementType.POPUP)
    );
    
    /** Получить XML-имя элемента формы по DSL-ключу (из mdclasses enum). */
    private static String xmlElementName(String dslKey) {
        FormElementType fet = DSL_TO_FORM_ELEMENT_TYPE.get(dslKey);
        return fet != null ? fet.fullName().getEn() : dslKey;
    }
    
    public FormWriter(OutputFormat format) {
        this.format = format;
    }

    /**
     * Детерминированно определить версию формата для Ext/Form.xml по контексту
     * пути назначения (TASK-171 D-12/D-6). НЕ хардкод и НЕ параметр от агента:
     * <ol>
     *   <li>версия родительского объекта {@code <Type>/<ObjName>.xml} (форма обязана
     *       совпадать с объектом, которому принадлежит);</li>
     *   <li>иначе — версия из {@code Configuration.xml} вверх по дереву;</li>
     *   <li>иначе (форма генерится вне конфигурации, совпадать не с чем) —
     *       {@link ConfigurationXmlReader#DEFAULT_FORMAT_VERSION}.</li>
     * </ol>
     */
    private String resolveFormatVersion(Path outputPath) {
        if (outputPath == null) return ConfigurationXmlReader.DEFAULT_FORMAT_VERSION;
        Path objXml = locateParentObjectXml(outputPath);
        if (objXml != null && Files.isRegularFile(objXml)) {
            return ConfigurationXmlReader.readFormatVersion(objXml);
        }
        Path cfg = locateConfigurationXml(outputPath);
        if (cfg != null) {
            return ConfigurationXmlReader.readFormatVersion(cfg);
        }
        return ConfigurationXmlReader.DEFAULT_FORMAT_VERSION;
    }

    /**
     * По {@code .../<Type>/<ObjName>/Forms/<FormName>/Ext/Form.xml} вернуть
     * {@code .../<Type>/<ObjName>.xml} (файл родительского объекта).
     */
    private Path locateParentObjectXml(Path outputPath) {
        Path p = outputPath.toAbsolutePath().normalize();
        for (int i = p.getNameCount() - 1; i >= 2; i--) {
            if ("Forms".equalsIgnoreCase(p.getName(i).toString())) {
                String objName = p.getName(i - 1).toString();      // <ObjName> (каталог)
                Path typeDir = p.getRoot() != null
                        ? p.getRoot().resolve(p.subpath(0, i - 1))  // .../<Type>
                        : p.subpath(0, i - 1);
                return typeDir.resolve(objName + ".xml");
            }
        }
        return null;
    }

    /** Подняться вверх по дереву до первого {@code Configuration.xml}. */
    private Path locateConfigurationXml(Path outputPath) {
        Path dir = outputPath.toAbsolutePath().normalize().getParent();
        while (dir != null) {
            Path cfg = dir.resolve("Configuration.xml");
            if (Files.isRegularFile(cfg)) return cfg;
            dir = dir.getParent();
        }
        return null;
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
            createEdt(dsl, outputPath);
        }
    }
    
    private void createDesigner(FormDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        // TASK-171: Designer-формат Ext/Form.xml ДОЛЖЕН начинаться с UTF-8 BOM, иначе
        // платформа отвергает файл на байтовом уровне («Исключение XDTO при чтении файла»)
        // ещё до разбора дерева. Эталон: 400/400 форм конфигурации с BOM; RoleWriter Designer
        // тоже пишет с BOM. Прежняя посылка «БЕЗ BOM для Form.xml» была ошибочной.
        createWriter(outputPath, true, FORM_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(FORM_NAMESPACES);
        allNamespaces.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        allNamespaces.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        
        Map<String, String> rootAttrs = new HashMap<>();
        // TASK-171 D-12/D-6: версия формата Ext/Form.xml ДОЛЖНА совпадать с версией
        // конфигурации, иначе платформа при full-load отвергает форму
        // («Версия формата ... отличается»). Определяем детерминированно из контекста
        // (родительский объект → иначе Configuration.xml), НЕ хардкодим.
        rootAttrs.put("version", resolveFormatVersion(outputPath));
        writeRootElement("Form", allNamespaces, rootAttrs);
        
        // Title
        // TASK-171: корневой <Title> формы — это v8:LocalStringType (мультиязычный),
        // требует <v8:item><v8:lang>/<v8:content>, а НЕ плоский текст. Плоский текст
        // вызывал XDTO-отказ платформы «при чтении файла». Эталон _Демо: всегда <v8:item>.
        if (dsl.getTitle() != null) {
            writeMultilingualString("Title", dsl.getTitle());
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

        // SavedData
        if (attr.getSavedData() != null && attr.getSavedData()) {
            writeElement("SavedData", "true");
        }

        // UseAlways — секция <UseAlways><Field>…</Field></UseAlways>.
        // Для форм документов с движениями платформа требует Объект.RegisterRecords,
        // иначе наборы записей регистров не подгружаются на форму (эталон big_Order_OKX).
        if (attr.getUseAlwaysField() != null && !attr.getUseAlwaysField().isEmpty()) {
            startElement("UseAlways");
            writeElement("Field", attr.getUseAlwaysField());
            endElement(); // UseAlways
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

        // ExtInfo для DynamicList (settings.mainTable, settings.dynamicDataRead)
        if (attr.getSettings() != null && "DynamicList".equals(attr.getType())) {
            writeDynamicListExtInfo(attr.getSettings());
        }

        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Attribute
        writer.writeCharacters("\n");
    }

    /** Эмитит <ExtInfo xsi:type="DynamicListExtInfo">…</ExtInfo>. */
    @SuppressWarnings("unchecked")
    private void writeDynamicListExtInfo(Map<String, Object> settings) throws XMLStreamException {
        writer.writeCharacters("\t\t\t");
        writer.writeStartElement("ExtInfo");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "DynamicListExtInfo");
        writer.writeCharacters("\n");
        Object mainTable = settings.get("mainTable");
        if (mainTable != null) {
            writer.writeCharacters("\t\t\t\t");
            writer.writeStartElement("MainTable");
            writer.writeCharacters(String.valueOf(mainTable));
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        Object ddr = settings.get("dynamicDataRead");
        if (ddr instanceof Boolean && (Boolean) ddr) {
            writer.writeCharacters("\t\t\t\t");
            writer.writeStartElement("DynamicDataRead");
            writer.writeCharacters("true");
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        writer.writeCharacters("\t\t\t");
        writer.writeEndElement(); // ExtInfo
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
        return DSL_TO_FORM_ELEMENT_TYPE.containsKey(key);
    }
    
    /**
     * Записать InputField.
     */
    private void writeInputField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement(xmlElementName("input"));
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

        // Порядок дочерних InputField строго по схеме logform (xs:sequence), как emit_input эталона:
        // [common-flags] → TitleLocation(mapped) → MultiLine → PasswordMode → ChoiceButton → ClearButton →
        // SpinButton → DropListButton → AutoMarkIncomplete → TextEdit → SkipOnInput → AutoMaxWidth → MaxWidth →
        // ... → Width → Height → стретчи → InputHint. Раньше всё шло generic-дампом HashMap в произвольном
        // порядке + сырое значение titleLocation — XDTO-отказ (TASK-171). Эмитим явно, из generic исключаем.
        writeCommonFlags(element);
        if (element.containsKey("titleLocation")) {
            writeElement("TitleLocation", mapTitleLocation(element.get("titleLocation").toString()));
        }
        if (Boolean.TRUE.equals(element.get("multiLine"))) {
            writeElement("MultiLine", "true");
        }
        if (Boolean.TRUE.equals(element.get("passwordMode"))) {
            writeElement("PasswordMode", "true");
        }
        if (element.containsKey("choiceButton")) {
            writeElement("ChoiceButton", element.get("choiceButton").toString().toLowerCase());
        }
        if (Boolean.TRUE.equals(element.get("clearButton"))) {
            writeElement("ClearButton", "true");
        }
        if (Boolean.TRUE.equals(element.get("spinButton"))) {
            writeElement("SpinButton", "true");
        }
        if (Boolean.TRUE.equals(element.get("dropListButton"))) {
            writeElement("DropListButton", "true");
        }
        if (Boolean.TRUE.equals(element.get("markIncomplete"))) {
            writeElement("AutoMarkIncomplete", "true");
        }
        if (Boolean.FALSE.equals(element.get("textEdit"))) {
            writeElement("TextEdit", "false");
        }
        if (Boolean.TRUE.equals(element.get("skipOnInput"))) {
            writeElement("SkipOnInput", "true");
        }
        if (element.containsKey("autoMaxWidth")) {
            writeElement("AutoMaxWidth", element.get("autoMaxWidth").toString().toLowerCase());
        }
        if (element.containsKey("maxWidth")) {
            writeElement("MaxWidth", element.get("maxWidth").toString());
        }
        if (Boolean.FALSE.equals(element.get("autoMaxHeight"))) {
            writeElement("AutoMaxHeight", "false");
        }
        if (element.containsKey("maxHeight")) {
            writeElement("MaxHeight", element.get("maxHeight").toString());
        }
        if (element.containsKey("width")) {
            writeElement("Width", element.get("width").toString());
        }
        if (element.containsKey("height")) {
            writeElement("Height", element.get("height").toString());
        }
        if (Boolean.TRUE.equals(element.get("horizontalStretch"))) {
            writeElement("HorizontalStretch", "true");
        }
        if (Boolean.TRUE.equals(element.get("verticalStretch"))) {
            writeElement("VerticalStretch", "true");
        }

        // Остаточные (НЕ порядок-критичные) свойства; всё порядок-значимое выведено выше и исключено
        writeElementProperties(element, java.util.Set.of(
            "visible", "userVisible", "enabled", "readOnly",
            "titleLocation", "multiLine", "passwordMode", "choiceButton", "clearButton",
            "spinButton", "dropListButton", "markIncomplete", "textEdit", "skipOnInput",
            "autoMaxWidth", "maxWidth", "autoMaxHeight", "maxHeight",
            "width", "height", "horizontalStretch", "verticalStretch"));

        // ContextMenu и ExtendedTooltip (автоматически)
        writeAutoElements(name, id, depth + 1);

        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // InputField
        writer.writeCharacters("\n");
    }

    /**
     * Эмиссия общих флагов элемента формы в порядке схемы logform, как emit_common_flags эталона:
     * Visible → UserVisible → Enabled → ReadOnly. Пишутся только при значениях, отличных от дефолта платформы
     * (Visible/Enabled=true, ReadOnly=false), как в эталоне.
     */
    private void writeCommonFlags(Map<String, Object> element) throws XMLStreamException {
        if (Boolean.FALSE.equals(element.get("visible")) || Boolean.TRUE.equals(element.get("hidden"))) {
            writeElement("Visible", "false");
        }
        if (Boolean.FALSE.equals(element.get("userVisible"))) {
            startElement("UserVisible");
            writeElement("xr:Common", "false");
            endElement(); // UserVisible
        }
        if (Boolean.FALSE.equals(element.get("enabled")) || Boolean.TRUE.equals(element.get("disabled"))) {
            writeElement("Enabled", "false");
        }
        if (Boolean.TRUE.equals(element.get("readOnly"))) {
            writeElement("ReadOnly", "true");
        }
    }
    
    /**
     * Записать UsualGroup.
     */
    private void writeUsualGroup(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        
        writer.writeCharacters(indent);
        writer.writeStartElement(xmlElementName("group"));
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

        // Порядок-критичные свойства UsualGroup строго по схеме logform (xs:sequence), как emit_group эталона:
        // Representation(mapped) → ShowTitle → United. Эмитим явно, из generic-прохода исключаем (skipKeys),
        // иначе HashMap-итерация ставит ShowTitle перед Representation и пишет сырое none → XDTO-отказ (TASK-171).
        if (element.containsKey("representation")) {
            writeElement("Representation", mapRepresentation(element.get("representation").toString()));
        }
        if (Boolean.FALSE.equals(element.get("showTitle"))) {
            writeElement("ShowTitle", "false");
        }
        if (Boolean.FALSE.equals(element.get("united"))) {
            writeElement("United", "false");
        }

        // Остаточные (НЕ порядок-критичные) свойства; порядок-значимые уже выведены выше и исключены
        writeElementProperties(element, java.util.Set.of("group", "representation", "showTitle", "united"));

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
        writer.writeStartElement(xmlElementName("table"));
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
        writer.writeStartElement(xmlElementName("button"));
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
        writer.writeStartElement(xmlElementName("label"));
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
        writer.writeStartElement(xmlElementName("labelField"));
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
        writer.writeStartElement(xmlElementName("check"));
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
        writer.writeStartElement(xmlElementName("pages"));
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
        writer.writeStartElement(xmlElementName("page"));
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
        writer.writeStartElement(xmlElementName("picture"));
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
        writer.writeStartElement(xmlElementName("picField"));
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
        writer.writeStartElement(xmlElementName("calendar"));
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
        writer.writeStartElement(xmlElementName("cmdBar"));
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
        writer.writeStartElement(xmlElementName("popup"));
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
        writeElementProperties(element, java.util.Collections.emptySet());
    }

    /**
     * Generic-дамп остаточных (НЕ порядок-критичных) свойств элемента.
     *
     * Порядок-значимые свойства (Representation, ShowTitle, United, TitleLocation и т.п.) схема logform
     * объявляет как xs:sequence — их позиция строго фиксирована. Такие свойства эмитятся ЯВНО в
     * writeUsualGroup/writeInputField/... в правильной позиции, а здесь ИСКЛЮЧАЮТСЯ через {@code skipKeys},
     * чтобы generic-проход (итерация HashMap в произвольном порядке) не воткнул их не на своё место и не
     * записал сырое enum-значение (none вместо None) — из-за чего платформа отвергала Ext/Form.xml на
     * уровне XDTO-схемы (TASK-171).
     */
    private void writeElementProperties(Map<String, Object> element, java.util.Set<String> skipKeys) throws XMLStreamException {
        // Пропускаем служебные ключи
        for (Map.Entry<String, Object> entry : element.entrySet()) {
            String key = entry.getKey();
            if (isElementType(key) || key.equals("name") || key.equals("title") ||
                key.equals("path") || key.equals("children") || key.equals("columns") ||
                key.equals("command") || key.equals("stdCommand") || key.equals("src")) {
                continue;
            }
            if (skipKeys.contains(key)) {
                continue;
            }

            // Преобразуем ключ в PascalCase
            String xmlName = toPascalCase(key);
            Object value = entry.getValue();

            // Маппинг enum-значений-страховка: даже если порядок-критичный ключ дошёл до generic-прохода
            // (например, representation/titleLocation на Table/Page из DSL form compile), значение всё равно
            // канонизируется в схему logform, а не пишется сырым (none → None) — TASK-171.
            if (key.equals("representation")) {
                writeElement(xmlName, mapRepresentation(value.toString()));
            } else if (key.equals("titleLocation")) {
                writeElement(xmlName, mapTitleLocation(value.toString()));
            } else if (value instanceof Boolean) {
                writeElement(xmlName, value.toString().toLowerCase());
            } else {
                writeElement(xmlName, value.toString());
            }
        }
    }

    /**
     * Маппинг DSL-значения representation в каноническое значение схемы logform (LogFormFieldsRepresentation).
     * Сырое none/normal/weak/strong схема отвергает (XDTO). Соответствует repr_map эталона form-compile.py
     * (Н. Широков): none→None, normal→NormalSeparation, weak→WeakSeparation, strong→StrongSeparation.
     * Уже каноническое значение (PascalCase) пропускаем как есть — обратная совместимость с form compile из DSL.
     */
    private static String mapRepresentation(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "none":   return "None";
            case "normal": return "NormalSeparation";
            case "weak":   return "WeakSeparation";
            case "strong": return "StrongSeparation";
            default:       return raw;
        }
    }

    /**
     * Маппинг DSL-значения titleLocation в каноническое значение схемы (FormElementTitleLocation):
     * none→None, left→Left, right→Right, top→Top, bottom→Bottom. Соответствует loc_map эталона emit_input.
     */
    private static String mapTitleLocation(String raw) {
        if (raw == null) {
            return null;
        }
        switch (raw) {
            case "none":   return "None";
            case "left":   return "Left";
            case "right":  return "Right";
            case "top":    return "Top";
            case "bottom": return "Bottom";
            default:       return raw;
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
    
    // ==================== EDT format ====================
    
    // Namespaces для EDT Form.form
    private static final Map<String, String> EDT_FORM_NAMESPACES = new HashMap<>();
    static {
        EDT_FORM_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        EDT_FORM_NAMESPACES.put("core", "http://g5.1c.ru/v8/dt/mcore");
        EDT_FORM_NAMESPACES.put("form", "http://g5.1c.ru/v8/dt/form");
    }
    
    private void createEdt(FormDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        createWriter(outputPath, false, EDT_FORM_NAMESPACES); // БЕЗ BOM
        writeXmlDeclaration();
        
        // Корневой элемент form:Form
        writer.writeStartElement("form", "Form", "http://g5.1c.ru/v8/dt/form");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeNamespace("core", "http://g5.1c.ru/v8/dt/mcore");
        writer.writeNamespace("form", "http://g5.1c.ru/v8/dt/form");
        writer.writeCharacters("\n");
        indentLevel = 1;
        
        // Items (UI elements)
        if (dsl.getElements() != null && !dsl.getElements().isEmpty()) {
            for (Map<String, Object> element : dsl.getElements()) {
                writeEdtItem(element);
            }
        }
        
        // AutoCommandBar
        startElement("autoCommandBar");
        writeElement("name", "ФормаКоманднаяПанель");
        writeElement("id", "-1");
        writeElement("autoFill", "true");
        endElement(); // autoCommandBar
        
        // Form-level properties
        writeElement("autoTitle", "true");
        writeElement("autoUrl", "true");
        writeElement("group", "Vertical");
        writeElement("autoFillCheck", "true");
        writeElement("allowFormCustomize", "true");
        writeElement("enabled", "true");
        writeElement("showTitle", "true");
        writeElement("showCloseButton", "true");
        
        // Attributes
        if (dsl.getAttributes() != null && !dsl.getAttributes().isEmpty()) {
            for (FormDsl.Attribute attr : dsl.getAttributes()) {
                writeEdtAttribute(attr);
            }
        }
        
        // Events (как handlers)
        if (dsl.getEvents() != null && !dsl.getEvents().isEmpty()) {
            startElement("handlers");
            for (Map.Entry<String, String> event : dsl.getEvents().entrySet()) {
                startElement("handler");
                writeElement("event", event.getKey());
                writeElement("name", event.getValue());
                endElement(); // handler
            }
            endElement(); // handlers
        }
        
        // Commands
        if (dsl.getCommands() != null && !dsl.getCommands().isEmpty()) {
            for (FormDsl.Command cmd : dsl.getCommands()) {
                writeEdtCommand(cmd);
            }
        }
        
        // CommandInterface
        startElement("commandInterface");
        writer.writeCharacters("\t");
        writer.writeEmptyElement("navigationPanel");
        writer.writeCharacters("\n");
        writer.writeCharacters("\t");
        writer.writeEmptyElement("commandBar");
        writer.writeCharacters("\n");
        endElement(); // commandInterface
        
        writer.writeEndElement(); // form:Form
        close();
        
        System.out.println("Created form (EDT): " + outputPath);
    }
    
    /**
     * Записать UI-элемент в EDT формате.
     */
    private void writeEdtItem(Map<String, Object> element) throws XMLStreamException {
        String type = null;
        Object value = null;
        
        for (Map.Entry<String, Object> entry : element.entrySet()) {
            if (isElementType(entry.getKey())) {
                type = entry.getKey();
                value = entry.getValue();
                break;
            }
        }
        
        if (type == null) return;
        
        String name = value != null ? value.toString() : null;
        if (element.containsKey("name")) {
            name = element.get("name").toString();
        }
        
        int id = elementIdGen.next();
        
        // Маппинг DSL type → EDT xsi:type
        String edtType = mapToEdtType(type);
        
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("items");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", edtType);
        writer.writeCharacters("\n");
        indentLevel++;
        
        writeElement("name", name);
        writeElement("id", String.valueOf(id));
        
        // DataPath
        if (element.containsKey("path")) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dataPath");
            writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "form:DataPath");
            writer.writeCharacters("\n");
            indentLevel++;
            writeElement("segments", element.get("path").toString());
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEndElement(); // dataPath
            writer.writeCharacters("\n");
        }
        
        // Title (как EDT: title xsi:type="v8:LocalStringType")
        if (element.containsKey("title")) {
            startElement("title");
            startElement("key");
            writer.writeCharacters("ru");
            endElement(); // key — hack: using writeElement below
            // Actually EDT uses <key>ru</key><value>Text</value>
            indentLevel--; // undo
            // Simplified: just write the title element
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEndElement(); // title
            writer.writeCharacters("\n");
        }
        
        // Type for fields
        if ("input".equals(type)) {
            writeElement("type", xmlElementName("input"));
            startElement("extInfo");
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEndElement();
            writer.writeCharacters("\n");
            indentLevel--;
            indentLevel++;
        } else if ("check".equals(type)) {
            writeElement("type", xmlElementName("check"));
        } else if ("labelField".equals(type)) {
            writeElement("type", xmlElementName("labelField"));
        }
        
        // Nested children
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeEdtItem(child);
            }
        }
        
        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // items
        writer.writeCharacters("\n");
    }
    
    /**
     * Маппинг DSL type → EDT xsi:type.
     */
    private String mapToEdtType(String dslType) {
        return switch (dslType) {
            case "input", "check", "labelField", "calendar", "picField" -> "form:FormField";
            case "group" -> "form:FormGroup";
            case "table" -> "form:Table";
            case "button" -> "form:Button";
            case "label", "picture" -> "form:Decoration";
            case "pages" -> "form:Pages";
            case "page" -> "form:Page";
            case "cmdBar" -> "form:CommandBar";
            case "popup" -> "form:Popup";
            default -> "form:FormField";
        };
    }
    
    /**
     * Записать реквизит в EDT формате.
     */
    private void writeEdtAttribute(FormDsl.Attribute attr) throws XMLStreamException {
        int id = attributeIdGen.next();
        
        startElement("attributes");
        writeElement("name", attr.getName());
        writeElement("id", String.valueOf(id));
        
        if (attr.getType() != null) {
            startElement("valueType");
            // В EDT: <types>String</types> без namespace квалификаторов  
            TypeResolver.TypeInfo typeInfo = TypeResolver.resolve(attr.getType());
            String edtTypeName = typeInfo.getXmlType();
            // Убираем xs: prefix если есть
            if (edtTypeName.startsWith("xs:")) {
                edtTypeName = toPascalCase(edtTypeName.substring(3));
            }
            writeElement("types", edtTypeName);
            endElement(); // valueType
        }
        
        if (attr.getMain() != null && attr.getMain()) {
            writeElement("main", "true");
        }
        
        endElement(); // attributes
    }
    
    /**
     * Записать команду в EDT формате.
     */
    private void writeEdtCommand(FormDsl.Command cmd) throws XMLStreamException {
        int id = commandIdGen.next();
        
        startElement("formCommands");
        writeElement("name", cmd.getName());
        writeElement("id", String.valueOf(id));
        
        if (cmd.getAction() != null) {
            startElement("action");
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("handler");
            writer.writeCharacters("\n");
            indentLevel++;
            writeElement("name", cmd.getAction());
            indentLevel--;
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeEndElement(); // handler
            writer.writeCharacters("\n");
            endElement(); // action
        }
        
        endElement(); // formCommands
    }
}

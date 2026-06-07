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
import java.util.LinkedHashMap;
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
        Map.entry("radio", FormElementType.RADIO_BUTTON_FIELD),
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

    private static final Map<String, String> DIRECT_FORM_TYPES = Map.ofEntries(
            Map.entry("DynamicList", "cfg:DynamicList"),
            Map.entry("ValueTable", "v8:ValueTable"),
            Map.entry("ValueTree", "v8:ValueTree"),
            Map.entry("ValueList", "v8:ValueListType"),
            Map.entry("TypeDescription", "v8:TypeDescription"),
            Map.entry("Universal", "v8:Universal"),
            Map.entry("FixedArray", "v8:FixedArray"),
            Map.entry("FixedStructure", "v8:FixedStructure"),
            Map.entry("UUID", "v8:UUID"),
            Map.entry("ConstantsSet", "cfg:ConstantsSet"),
            Map.entry("FillChecking", "v8:FillChecking"),
            Map.entry("Null", "v8:Null"),
            Map.entry("StandardBeginningDate", "v8:StandardBeginningDate"),
            Map.entry("StandardPeriod", "v8:StandardPeriod"),
            Map.entry("Type", "v8:Type"),
            Map.entry("FormattedString", "v8ui:FormattedString"),
            Map.entry("Picture", "v8ui:Picture"),
            Map.entry("Color", "v8ui:Color"),
            Map.entry("Font", "v8ui:Font"),
            Map.entry("SizeChangeMode", "v8ui:SizeChangeMode"),
            Map.entry("VerticalAlign", "v8ui:VerticalAlign"),
            Map.entry("HorizontalAlign", "v8ui:HorizontalAlign"),
            Map.entry("DataCompositionSettings", "dcsset:DataCompositionSettings"),
            Map.entry("DataCompositionSchema", "dcssch:DataCompositionSchema"),
            Map.entry("DataCompositionComparisonType", "dcscor:DataCompositionComparisonType")
    );

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

    //++agent TASK-174 [05.06.2026 12:20:00]
    /**
     * XG-11: детерминированный fallback-текст корневого Title, когда DSL его не задал.
     * Имя формы из канонической раскладки {@code .../Forms/<ИмяФормы>/Ext/Form.xml};
     * вне раскладки — имя файла без расширения; без пути — «Форма».
     */
    private String deriveFormName(Path outputPath) {
        if (outputPath != null) {
            Path p = outputPath.toAbsolutePath().normalize();
            for (int i = p.getNameCount() - 1; i >= 1; i--) {
                if ("Forms".equalsIgnoreCase(p.getName(i - 1).toString())) {
                    return p.getName(i).toString();
                }
            }
            String file = p.getFileName().toString();
            int dot = file.lastIndexOf('.');
            String stem = dot > 0 ? file.substring(0, dot) : file;
            if (!stem.isBlank() && !"Form".equalsIgnoreCase(stem)) {
                return stem;
            }
        }
        return "Форма";
    }
    //++agent TASK-174

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
        allNamespaces.put("dcssch", "http://v8.1c.ru/8.1/data-composition-system/schema");
        allNamespaces.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        allNamespaces.put("lf", "http://v8.1c.ru/8.2/managed-application/logform");
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
        //**agent TASK-174 [05.06.2026 12:20:00]
        // XG-11: Title эмитим ВСЕГДА, не только при заполненном dsl.title. Форма без
        // корневого <Title>, сгенерированная compile (Title→сразу AutoCommandBar),
        // отвергалась Designer-batch с XDTO-ошибкой «при чтении файла» (TASK-173,
        // биг_УборщикТестовыхДанных). Fallback-текст — имя формы из пути назначения
        // (.../Forms/<ИмяФормы>/Ext/Form.xml), детерминированно, без параметра от агента.
        String rootTitle = dsl.getTitle();
        //**agent TASK-175 [07.06.2026 19:20:00]
        // XG-39 (36cd63d8): триггер «явный Title» ДВОЙНОЙ — dsl.title ИЛИ properties.title
        // (form-compile.py:2677-2679: form_title = defn.title || properties.title).
        // properties.title продвигается в корневой <Title> и исключается из Properties:
        // плоский <Title> внутри Properties невалиден (FORM-120) и дублировал бы корневой.
        boolean explicitTitle = rootTitle != null && !rootTitle.isBlank();
        Map<String, Object> formProperties = dsl.getProperties() != null
                ? new LinkedHashMap<>(dsl.getProperties())
                : null;
        Object propertiesTitle = formProperties != null ? formProperties.remove("title") : null;
        if (!explicitTitle && propertiesTitle instanceof String propTitleText
                && !propTitleText.isBlank()) {
            rootTitle = propTitleText;
            explicitTitle = true;
        }
        if (rootTitle == null || rootTitle.isBlank()) {
            rootTitle = deriveFormName(outputPath);
        }
        writeMultilingualString("Title", rootTitle);
        // XG-39: при явном Title без явного autoTitle платформа добавила бы суффикс
        // синонима (двойной заголовок «Номенклатура: Номенклатура») — эмитим
        // <AutoTitle>false</AutoTitle> сразу после </Title> (канон Designer,
        // фикстура valid-vyborkontragenta.xml:10). Для fallback-Title (XG-11, форма
        // без явного title) AutoTitle НЕ подавляем — там платформенный AutoTitle=true
        // осмыслен. Явный properties.autoTitle всегда уважается (эмитится writeProperties).
        if (explicitTitle
                && (formProperties == null || !formProperties.containsKey("autoTitle"))) {
            writeElement("AutoTitle", "false");
        }
        //**agent TASK-175
        //**agent TASK-174

        // Properties
        //**agent TASK-175 [07.06.2026 19:20:00]
        //if (dsl.getProperties() != null) {
        //    writeProperties(dsl.getProperties());
        //}
        if (formProperties != null && !formProperties.isEmpty()) {
            writeProperties(formProperties);
        }
        //**agent TASK-175

        //++agent TASK-174 [07.06.2026 11:15:00]
        // Аудит порта (форм): excludedCommands парсились DSL (FormDsl.excludedCommands),
        // но НЕ эмитились — секция <CommandSet> из 1c-form-spec.md §4 была опущена
        // при переносе. Позиция по §2: до <AutoCommandBar>.
        if (dsl.getExcludedCommands() != null && !dsl.getExcludedCommands().isEmpty()) {
            startElement("CommandSet");
            for (String excluded : dsl.getExcludedCommands()) {
                writeElement("ExcludedCommand", excluded);
            }
            endElement(); // CommandSet
        }
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 11:15:00]
        // Аудит порта (форм): parameters парсились DSL (FormDsl.parameters), но НЕ
        // эмитились — секция <Parameters> из 1c-form-spec.md §10 опущена при переносе.
        // Позиция по §2: Attributes → Parameters → Commands. Parameter БЕЗ атрибута id
        // (спека §10: «Параметры не имеют атрибута id»).
        if (dsl.getParameters() != null && !dsl.getParameters().isEmpty()) {
            startElement("Parameters");
            for (FormDsl.Parameter param : dsl.getParameters()) {
                writeParameter(param);
            }
            endElement(); // Parameters
        }
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 11:15:00]
        // Аудит порта (форм): FillChecking (1c-form-spec.md §9, form-dsl-spec.md §5)
        // был опущен при переносе — DSL-ключ fillChecking молча терялся.
        if (attr.getFillChecking() != null && !attr.getFillChecking().isBlank()) {
            writeElement("FillChecking", attr.getFillChecking());
        }
        //++agent TASK-174

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
    
    //++agent TASK-174 [07.06.2026 11:15:00]
    /**
     * Записать параметр формы (1c-form-spec.md §10): {@code <Parameter name="...">}
     * БЕЗ атрибута id, внутри — Type (та же система типов, что у Attributes) и
     * KeyParameter для ключевых параметров.
     */
    private void writeParameter(FormDsl.Parameter param) throws XMLStreamException {
        writer.writeCharacters("\t\t");
        writer.writeStartElement("Parameter");
        writer.writeAttribute("name", param.getName());
        writer.writeCharacters("\n");
        indentLevel = 3;

        if (param.getTitle() != null) {
            writeMultilingualString("Title", param.getTitle());
        }

        if (param.getType() != null) {
            writeType(param.getType());
        }

        if (Boolean.TRUE.equals(param.getKey())) {
            writeElement("KeyParameter", "true");
        }

        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Parameter
        writer.writeCharacters("\n");
    }
    //++agent TASK-174

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
        //**agent TASK-174 [05.06.2026 12:25:00]
        // XG-10: составной DSL-тип "CatalogRef.A | CatalogRef.B" раньше уходил в
        // TypeResolver.resolve ЦЕЛИКОМ (паттерн contains("Ref.") матчился) и эмитился
        // одной строкой <v8:Type>cfg:CatalogRef.A | CatalogRef.B</v8:Type> — XDTO-схема
        // платформы такое отвергает («Ошибка отображения типов ... QName», TASK-173).
        // Канон (НастройкиВерсионированияОбъектов и др.): отдельные соседние <v8:Type>,
        // квалификаторы — ПОСЛЕ всех <v8:Type>. Сплит — paren-aware (CompositeType),
        // чтобы НЕ ломать "number+(15,2)" (nonneg-синтаксис) и "number(15,2)".
        java.util.List<TypeResolver.TypeInfo> typeInfos = new java.util.ArrayList<>();
        for (String part : io.github.onec.xmlgen.model.CompositeType.splitCompositeTypes(dslType)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                typeInfos.add(resolveFormType(trimmed));
            }
        }

        startElement("Type");
        for (TypeResolver.TypeInfo info : typeInfos) {
            writeElement("v8:Type", info.getXmlType());
        }
        for (TypeResolver.TypeInfo info : typeInfos) {
            writeTypeQualifiers(info.getQualifiers());
        }
        endElement(); // Type
    }

    private TypeResolver.TypeInfo resolveFormType(String dslType) {
        if (dslType == null || dslType.isBlank()) {
            throw new IllegalArgumentException("Type cannot be null or empty");
        }
        if (dslType.startsWith("xs:") || dslType.startsWith("v8:")
                || dslType.startsWith("cfg:") || dslType.startsWith("v8ui:")
                || dslType.startsWith("dcsset:") || dslType.startsWith("dcscor:")
                || dslType.startsWith("dcssch:") || dslType.startsWith("mxl:")) {
            return new TypeResolver.TypeInfo(dslType, null);
        }
        String direct = DIRECT_FORM_TYPES.get(dslType);
        if (direct != null) {
            return new TypeResolver.TypeInfo(direct, null);
        }
        return TypeResolver.resolve(dslType);
    }

    /** Квалификаторы одного типа (string/number/date); null — ничего. */
    private void writeTypeQualifiers(Object qualifiers) throws XMLStreamException {
        if (qualifiers == null) {
            return;
        }
        if (qualifiers instanceof TypeResolver.StringQualifiers) {
            TypeResolver.StringQualifiers sq = (TypeResolver.StringQualifiers) qualifiers;
            startElement("v8:StringQualifiers");
            writeElement("v8:Length", String.valueOf(sq.getLength()));
            writeElement("v8:AllowedLength", sq.getAllowedLengthXml());
            endElement();
        } else if (qualifiers instanceof TypeResolver.NumberQualifiers) {
            TypeResolver.NumberQualifiers nq = (TypeResolver.NumberQualifiers) qualifiers;
            startElement("v8:NumberQualifiers");
            writeElement("v8:Digits", String.valueOf(nq.getDigits()));
            writeElement("v8:FractionDigits", String.valueOf(nq.getFractionDigits()));
            writeElement("v8:AllowedSign", nq.getAllowedSign());
            endElement();
        } else if (qualifiers instanceof TypeResolver.DateQualifiers) {
            TypeResolver.DateQualifiers dq = (TypeResolver.DateQualifiers) qualifiers;
            startElement("v8:DateQualifiers");
            writeElement("v8:DateFractions", dq.getDateFractionsXml());
            endElement();
        }
    }
    //**agent TASK-174
    
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

        //++agent TASK-174 [07.06.2026 11:15:00]
        // Аудит порта (форм): Picture/Shortcut/Representation команды (1c-form-spec.md §11,
        // form-dsl-spec.md §7) были опущены при переносе. Порядок по образцу §11:
        // Title → ToolTip → Picture → Action → Shortcut → Representation.
        if (cmd.getPicture() != null && !cmd.getPicture().isBlank()) {
            writePictureRef("Picture", cmd.getPicture(), true);
        }

        if (cmd.getAction() != null) {
            writeElement("Action", cmd.getAction());
        }

        if (cmd.getShortcut() != null && !cmd.getShortcut().isBlank()) {
            writeElement("Shortcut", cmd.getShortcut());
        }

        if (cmd.getRepresentation() != null && !cmd.getRepresentation().isBlank()) {
            writeElement("Representation", cmd.getRepresentation());
        }
        //++agent TASK-174

        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Command
        writer.writeCharacters("\n");
    }

    //++agent TASK-174 [07.06.2026 11:15:00]
    /**
     * Структурная ссылка на картинку по 1c-form-spec.md §12:
     * {@code <Picture><xr:Ref>StdPicture.Name</xr:Ref><xr:LoadTransparent>true</xr:LoadTransparent></Picture>}.
     * Раньше generic-проход эмитил плоский текст {@code <Picture>StdPicture.Name</Picture>} —
     * схема xr требует вложенный xr:Ref (канон: биг_ТорговыйТерминал, кнопки ГрафикВлево/ГрафикВправо).
     */
    private void writePictureRef(String elementName, String ref, boolean loadTransparent) throws XMLStreamException {
        startElement(elementName);
        writeElement("xr:Ref", ref);
        if (loadTransparent) {
            writeElement("xr:LoadTransparent", "true");
        }
        endElement(); // elementName
    }
    //++agent TASK-174
    
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
        // Определяем тип элемента.
        String type = null;
        Object value = null;

        //**agent TASK-174 [05.06.2026 00:00:00]
        // XG-01: каноничная DSL-форма из form-dsl/SKILL.md и из fixtures —
        // {"type":"input","name":"Поле1",...}, т.е. тип в значении ключа "type". Прежняя
        // детекция искала ключ, ИМЯ которого = тип ("input"/"group"), и для {"type":"input"}
        // не находила ничего → молча роняла элемент (любой сиблинг после группы и любой
        // вложенный child выпадали). Группа "проскакивала" лишь по совпадению: у неё есть
        // ключ "group" (ориентация), который случайно матчился как тип-элемента.
        // Фикс: сначала честно читаем дискриминатор "type"; ключ-как-тип оставляем как
        // обратносовместимый фолбэк.
        Object typeField = element.get("type");
        if (typeField != null && isElementType(typeField.toString())) {
            type = typeField.toString();
        } else {
            for (Map.Entry<String, Object> entry : element.entrySet()) {
                String key = entry.getKey();
                if (isElementType(key)) {
                    type = key;
                    value = entry.getValue();
                    break;
                }
            }
        }
        //**agent TASK-174

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
            case "radio":
                writeRadioButtonField(element, name, id, depth);
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
        
        // DataPath. Both "path" (root reference spec) and "dataPath" (tooling docs)
        // are accepted; emit explicitly so it stays in the schema sequence before
        // flags/properties instead of falling through the generic property dumper.
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
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

        //++agent TASK-174 [07.06.2026 11:20:00]
        // Аудит порта (форм): InputHint — multilang v8:item (1c-form-spec.md §8.2),
        // generic-проход писал плоский текст. Позиция — после стретчей, как в emit_input.
        if (element.containsKey("inputHint")) {
            writeMultilingualString("InputHint", element.get("inputHint").toString());
        }
        //++agent TASK-174

        // Остаточные (НЕ порядок-критичные) свойства; всё порядок-значимое выведено выше и исключено
        writeElementProperties(element, java.util.Set.of(
            "visible", "userVisible", "enabled", "readOnly",
            "hidden", "disabled",
            "titleLocation", "multiLine", "passwordMode", "choiceButton", "clearButton",
            "spinButton", "dropListButton", "markIncomplete", "textEdit", "skipOnInput",
            "autoMaxWidth", "maxWidth", "autoMaxHeight", "maxHeight",
            "width", "height", "horizontalStretch", "verticalStretch"));

        // ContextMenu и ExtendedTooltip (автоматически)
        writeAutoElements(name, id, depth + 1);

        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        //**agent TASK-174 [07.06.2026 11:20:00]
        // Аудит порта (форм): DSL-значение "collapsible" (form-dsl-spec.md §4.3) — это
        // НЕ ориентация: схема logform для <Group> допускает только Vertical/Horizontal/
        // AlwaysHorizontal/AlwaysVertical, сворачиваемость — отдельный тег
        // <Behavior>Collapsible</Behavior> (1c-form-spec.md §8.1). Порт писал
        // <Group>Collapsible</Group> — невалидный enum, XDTO-отказ.
        //writeElement("Group", toPascalCase(groupType));
        if ("collapsible".equalsIgnoreCase(groupType)) {
            writeElement("Group", "Vertical");
            writeElement("Behavior", "Collapsible");
        } else {
            writeElement("Group", toPascalCase(groupType));
        }
        //**agent TASK-174

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

        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-15: <ChildItems> ОБЯЗАНА присутствовать в <UsualGroup> ВСЕГДА, даже пустой.
        // Без обёртки Designer-batch при /LoadExternalDataProcessorOrReportFromFiles молча
        // отбрасывает ВСЁ поддерево группы — форма открывается с пустым телом.
        // Канон: биг_УборщикТестовыхДанных/Forms/Форма/Ext/Form.xml строки 14-40.
        // Пустой <ChildItems/> позволяет form edit --json добавить дочерние элементы позже.
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeElement(child, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //++agent TASK-174

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
        
        // DataPath (supports both "path" and "dataPath")
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }

        //++agent TASK-174 [07.06.2026 11:30:00]
        // Аудит порта (форм): height у table — «высота в строках таблицы» (form-dsl-spec.md
        // §4.3 table) → тег <HeightInTableRows> (1c-form-spec.md §8.4), а НЕ общий <Height>,
        // куда его отправлял generic-проход.
        if (element.containsKey("height")) {
            writeElement("HeightInTableRows", element.get("height").toString());
        }

        // Свойства (height выведен явно выше — исключаем)
        writeElementProperties(element, java.util.Set.of("height"));
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 11:30:00]
        // Аудит порта (форм): служебные элементы таблицы SearchStringAddition /
        // ViewStatusAddition / SearchControlAddition (1c-form-spec.md §8.4 «Служебные
        // элементы», form-dsl-spec.md §9 companions Table) были опущены при переносе.
        // Структура и порядок — по канону (биг_ТорговыйТерминал, ТаблицаОткрытыхАлгоОрдеров):
        // ContextMenu → AutoCommandBar → ExtendedTooltip → SearchStringAddition →
        // ViewStatusAddition → SearchControlAddition → ChildItems; каждый Addition несёт
        // AdditionSource{Item=имя таблицы, Type} и собственные ContextMenu/ExtendedTooltip.
        writeTableAddition("SearchStringAddition", name, name + "СтрокаПоиска",
                "SearchStringRepresentation", depth + 1);
        writeTableAddition("ViewStatusAddition", name, name + "СостояниеПросмотра",
                "ViewStatusRepresentation", depth + 1);
        writeTableAddition("SearchControlAddition", name, name + "УправлениеПоиском",
                "SearchControl", depth + 1);
        //++agent TASK-174

        // ChildItems (колонки)
        //**agent TASK-174 [07.06.2026 11:30:00]
        // Аудит порта (форм): <ChildItems> у Table эмитим ВСЕГДА, даже пустой —
        // класс XG-15 (у UsualGroup/Pages/Page/CommandBar/Popup уже безусловный,
        // Table был пропущен при том же фиксе).
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("columns")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> columns = (List<Map<String, Object>>) element.get("columns");
            for (Map<String, Object> col : columns) {
                writeElement(col, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //**agent TASK-174
        
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

        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-14: <Type>UsualButton</Type> должен быть ПЕРВЫМ дочерним тегом <Button>.
        // Без него Designer-batch при /LoadExternalDataProcessorOrReportFromFiles молча
        // отбрасывает кнопку → в модуле формы «Поле объекта не обнаружено (ИмяКнопки)».
        // Канон: биг_УборщикТестовыхДанных/Forms/Форма/Ext/Form.xml строки 45-54.
        //**agent TASK-174 [07.06.2026 11:25:00]
        // Аудит порта (форм): DSL-ключ type кнопки (usual/hyperlink/commandBar,
        // form-dsl-spec.md §4.3 button) игнорировался — XG-14-фикс хардкодил UsualButton
        // для ЛЮБОЙ кнопки, а сырое значение типа дополнительно дублировалось
        // generic-проходом (<Type>hyperlink</Type> — невалидный enum схемы).
        // Маппинг по 1c-form-spec.md §8.3: usual→UsualButton, hyperlink→Hyperlink,
        // commandBar→CommandBarButton.
        //writeElement("Type", "UsualButton");
        writeElement("Type", mapButtonType(element.get("type")));
        //**agent TASK-174
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 11:25:00]
        // Аудит порта (форм): picture кнопки — структурный <Picture><xr:Ref>
        // (1c-form-spec.md §8.3, §12), generic-проход писал плоский текст.
        // Позиция: после CommandName (§8.3).
        if (element.containsKey("picture")) {
            writePictureRef("Picture", element.get("picture").toString(), true);
        }

        // Свойства (type/picture выведены явно выше — исключаем из generic-прохода)
        writeElementProperties(element, java.util.Set.of("type", "picture"));
        //++agent TASK-174

        //++agent TASK-174 [07.06.2026 00:00:00]
        // XG-16: Button в каноне Designer содержит ТОЛЬКО <ExtendedTooltip>, без <ContextMenu>.
        // Подтверждено на двух формах проекта (ВыборФорматаВложений, ВводКонтактнойИнформации)
        // и на эталонном Designer-dump /tmp/task173-template-dump.
        // writeAutoElements добавляет ContextMenu+ExtendedTooltip — для Button это лишнее:
        // сдвигает id и засоряет XML дочерним узлом, которого нет в канонном Button.
        writeExtendedTooltipOnly(name, id, depth + 1);
        //++agent TASK-174

        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        // DataPath (supports both "path" and "dataPath")
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        // DataPath (supports both "path" and "dataPath")
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }
        
        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // CheckBoxField
        writer.writeCharacters("\n");
    }

    /**
     * Записать RadioButtonField.
     *
     * <p>Канон живых форм проекта: DataPath/Title/TitleLocation → RadioButtonType →
     * ChoiceList → ContextMenu/ExtendedTooltip → Events. ChoiceList поддерживается
     * минимальной DSL-формой {@code choices: [{title, value, checkState}]}.</p>
     */
    @SuppressWarnings("unchecked")
    private void writeRadioButtonField(Map<String, Object> element, String name, int id, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);

        writer.writeCharacters(indent);
        writer.writeStartElement(xmlElementName("radio"));
        writer.writeAttribute("name", name);
        writer.writeAttribute("id", String.valueOf(id));
        writer.writeCharacters("\n");

        int oldIndent = indentLevel;
        indentLevel = depth + 1;

        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }
        if (element.containsKey("titleLocation")) {
            writeElement("TitleLocation", mapTitleLocation(element.get("titleLocation").toString()));
        }
        writeElement("RadioButtonType", element.getOrDefault("radioButtonType", "Auto").toString());

        Object choices = element.get("choices");
        if (choices instanceof List<?> && !((List<?>) choices).isEmpty()) {
            writeRadioChoiceList((List<Map<String, Object>>) choices);
        }

        writeElementProperties(element, java.util.Set.of(
                "titleLocation", "radioButtonType", "choices"));

        writeAutoElements(name, id, depth + 1);
        writeElementEvents(element, name, depth + 1);

        indentLevel = oldIndent;
        writer.writeCharacters(indent);
        writer.writeEndElement(); // RadioButtonField
        writer.writeCharacters("\n");
    }

    private void writeRadioChoiceList(List<Map<String, Object>> choices) throws XMLStreamException {
        startElement("ChoiceList");
        for (Map<String, Object> choice : choices) {
            writeRadioChoice(choice);
        }
        endElement(); // ChoiceList
    }

    private void writeRadioChoice(Map<String, Object> choice) throws XMLStreamException {
        startElement("xr:Item");
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEmptyElement("xr:Presentation");
        writer.writeCharacters("\n");
        writeElement("xr:CheckState", choice.getOrDefault("checkState", 0).toString());

        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("xr:Value");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "FormChoiceListDesTimeValue");
        writer.writeCharacters("\n");
        indentLevel++;

        writeMultilingualString("Presentation", choice.getOrDefault("title", "").toString());

        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeStartElement("Value");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type",
                choice.getOrDefault("valueType", "xs:decimal").toString());
        Object value = choice.get("value");
        if (value != null) {
            writer.writeCharacters(value.toString());
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");

        indentLevel--;
        writer.writeCharacters("\t".repeat(indentLevel));
        writer.writeEndElement(); // xr:Value
        writer.writeCharacters("\n");
        endElement(); // xr:Item
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
        
        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-15: <ChildItems> ОБЯЗАНА присутствовать в <Pages> ВСЕГДА (аналогично UsualGroup).
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeElement(child, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-15: <ChildItems> ОБЯЗАНА присутствовать в <Page> ВСЕГДА (аналогично UsualGroup).
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeElement(child, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //++agent TASK-174

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
        //**agent TASK-174 [07.06.2026 11:25:00]
        // Аудит порта (форм): ссылка на картинку — структурный <Picture><xr:Ref>
        // (1c-form-spec.md §8.12, §12); порт писал плоский <Picture>CommonPicture.X</Picture>.
        // DSL допускает ключ src ИЛИ picture-как-свойство (form-dsl-spec.md §4.3 picture).
        //if (element.containsKey("src")) {
        //    writeElement("Picture", element.get("src").toString());
        //}
        // Защита: в key-as-type форме {"picture":"Логотип"} ключ picture несёт ИМЯ
        // элемента, а не ссылку; ссылкой считаем только значение вида Family.Name
        // (StdPicture.X / CommonPicture.X) при заданном дискриминаторе "type".
        if (element.containsKey("src")) {
            writePictureRef("Picture", element.get("src").toString(), true);
        } else if ("picture".equals(String.valueOf(element.get("type")))
                && element.get("picture") instanceof String
                && element.get("picture").toString().contains(".")) {
            writePictureRef("Picture", element.get("picture").toString(), true);
        }

        // Свойства
        writeElementProperties(element);
        //**agent TASK-174

        // ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        // DataPath (supports both "path" and "dataPath")
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        // DataPath (supports both "path" and "dataPath")
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writeElement("DataPath", dataPath);
        }
        
        // Свойства
        writeElementProperties(element);
        
        // ContextMenu и ExtendedTooltip
        writeAutoElements(name, id, depth + 1);
        
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

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
        
        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-15: <ChildItems> ОБЯЗАНА присутствовать в <CommandBar> ВСЕГДА (аналогично UsualGroup).
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeElement(child, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //++agent TASK-174

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

        //++agent TASK-174 [07.06.2026 11:25:00]
        // Аудит порта (форм): picture у Popup — структурный <Picture><xr:Ref>
        // (1c-form-spec.md §8.8, §12); generic-проход писал плоский <Picture>текст</Picture>.
        // Позиция по §8.8: Picture первым, до Representation.
        if (element.containsKey("picture")) {
            writePictureRef("Picture", element.get("picture").toString(), true);
        }
        //++agent TASK-174

        // Title
        if (element.containsKey("title")) {
            writeMultilingualString("Title", element.get("title").toString());
        }

        // Свойства
        //**agent TASK-174 [07.06.2026 11:25:00] picture выведен явно выше — исключаем
        //writeElementProperties(element);
        writeElementProperties(element, java.util.Set.of("picture"));
        //**agent TASK-174

        // ExtendedTooltip
        int tooltipId = elementIdGen.next();
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");

        //++agent TASK-174 [07.06.2026 10:00:00]
        // XG-15: <ChildItems> ОБЯЗАНА присутствовать в <Popup> ВСЕГДА (аналогично UsualGroup).
        //++agent TASK-174 [07.06.2026 11:20:00] события элемента (form-dsl-spec.md §4.1-4.2);
        // позиция по канону: ПОСЛЕ companion-элементов, ДО <ChildItems>
        // (_ДемоРеестрСкладскихДокументов: Pages; _ДемоРеестрДокументов: Table)
        writeElementEvents(element, name, depth + 1);
        //++agent TASK-174

        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeStartElement("ChildItems");
        writer.writeCharacters("\n");
        if (element.containsKey("children")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) element.get("children");
            for (Map<String, Object> child : children) {
                writeElement(child, depth + 2);
            }
        }
        writer.writeCharacters("\t".repeat(depth + 1));
        writer.writeEndElement(); // ChildItems
        writer.writeCharacters("\n");
        //++agent TASK-174

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
     * Data binding alias used by form compile. The root reference spec documents "path",
     * while operational xml-gen docs and older tests use "dataPath"; both mean
     * {@code <DataPath>...}. Prefer "path" when both are present.
     */
    private static String dataPath(Map<String, Object> element) {
        Object path = element.get("path");
        if (path == null) {
            path = element.get("dataPath");
        }
        return path != null ? path.toString() : null;
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
                key.equals("path") || key.equals("dataPath") || key.equals("children") || key.equals("columns") ||
                key.equals("command") || key.equals("stdCommand") || key.equals("src")
                //**agent TASK-174 [07.06.2026 11:20:00]
                // Аудит порта (форм): "on"/"handlers" — DSL-ключи событий (form-dsl-spec.md
                // §4.1), обрабатываются writeElementEvents; "inputHint" — multilang
                // (1c-form-spec.md §8.2), эмитится явно в writeInputField. Без скипа
                // generic-проход эмитил мусор <On>[..]</On>/<Handlers>{..}</Handlers>
                // и плоский <InputHint>.
                || key.equals("on") || key.equals("handlers") || key.equals("inputHint")
                //**agent TASK-174
                ) {
                continue;
            }
            //++agent TASK-174 [05.06.2026 12:34:00]
            // XG-12: ключ "type" со значением-типом элемента ("group"/"input"/...) — это
            // DSL-дискриминатор для writeElement-диспетчера, а не XML-свойство. Без скипа
            // generic-проход эмитил <Type>group</Type> внутрь <UsualGroup> — мусор,
            // которого нет в каноне (Designer молча игнорирует, но XML не каноничен).
            Object entryValue = entry.getValue();
            if (key.equals("type") && entryValue != null && isElementType(entryValue.toString())) {
                continue;
            }
            //++agent TASK-174
            if (skipKeys.contains(key)) {
                continue;
            }

            if (key.equals("hidden")) {
                if (!element.containsKey("visible") && Boolean.TRUE.equals(entryValue)) {
                    writeElement("Visible", "false");
                }
                continue;
            }
            if (key.equals("disabled")) {
                if (!element.containsKey("enabled") && Boolean.TRUE.equals(entryValue)) {
                    writeElement("Enabled", "false");
                }
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

    //++agent TASK-174 [07.06.2026 11:25:00]
    /**
     * Маппинг DSL-значения type кнопки (form-dsl-spec.md §4.3) в enum схемы logform
     * (1c-form-spec.md §8.3): usual→UsualButton, hyperlink→Hyperlink,
     * commandBar→CommandBarButton. Уже каноническое значение пропускаем как есть;
     * null/значение-дискриминатор ("button") → UsualButton (дефолт XG-14).
     */
    private static String mapButtonType(Object raw) {
        if (raw == null) {
            return "UsualButton";
        }
        switch (raw.toString()) {
            case "usual":             return "UsualButton";
            case "hyperlink":         return "Hyperlink";
            case "commandBar":        return "CommandBarButton";
            case "UsualButton":
            case "Hyperlink":
            case "CommandBarButton":  return raw.toString();
            default:                  return "UsualButton";
        }
    }
    //++agent TASK-174

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
    
    //++agent TASK-174 [07.06.2026 11:30:00]
    /**
     * Служебный Addition-элемент таблицы (1c-form-spec.md §8.4): SearchStringAddition /
     * ViewStatusAddition / SearchControlAddition. Канон (биг_ТорговыйТерминал):
     * <pre>
     * &lt;SearchStringAddition name="ИмяСтрокаПоиска" id="N"&gt;
     *   &lt;AdditionSource&gt;&lt;Item&gt;Имя&lt;/Item&gt;&lt;Type&gt;SearchStringRepresentation&lt;/Type&gt;&lt;/AdditionSource&gt;
     *   &lt;ContextMenu .../&gt;&lt;ExtendedTooltip .../&gt;
     * &lt;/SearchStringAddition&gt;
     * </pre>
     */
    private void writeTableAddition(String xmlTag, String tableName, String additionName,
                                    String additionType, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        int additionId = elementIdGen.next();
        writer.writeCharacters(indent);
        writer.writeStartElement(xmlTag);
        writer.writeAttribute("name", additionName);
        writer.writeAttribute("id", String.valueOf(additionId));
        writer.writeCharacters("\n");

        int oldIndent = indentLevel;
        indentLevel = depth + 1;
        startElement("AdditionSource");
        writeElement("Item", tableName);
        writeElement("Type", additionType);
        endElement(); // AdditionSource
        indentLevel = oldIndent;

        writeAutoElements(additionName, additionId, depth + 1);

        writer.writeCharacters(indent);
        writer.writeEndElement(); // xmlTag
        writer.writeCharacters("\n");
    }
    //++agent TASK-174

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

    //++agent TASK-174 [07.06.2026 11:20:00]
    /**
     * События UI-элемента по form-dsl-spec.md §4.1–4.2: "on" — массив имён событий
     * (обработчик автоименуется &lt;ИмяЭлемента&gt;&lt;РусскийСуффикс&gt;),
     * "handlers" — явные имена {"OnChange": "МойОбработчик"} (имеют приоритет).
     * Эмитится блоком &lt;Events&gt; ПОСЛЕДНИМ ребёнком элемента — как в каноне
     * (биг_ТорговыйТерминал: Events после ContextMenu/ExtendedTooltip, перед закрытием).
     * При переносе функциональность была опущена целиком.
     */
    private void writeElementEvents(Map<String, Object> element, String elementName, int depth) throws XMLStreamException {
        java.util.LinkedHashMap<String, String> events = new java.util.LinkedHashMap<>();
        Object on = element.get("on");
        if (on instanceof List<?>) {
            for (Object ev : (List<?>) on) {
                String eventName = String.valueOf(ev);
                // Реиспользуем таблицу суффиксов form-dsl-spec.md §4.2 из form/edit
                // (EventHandlerNames) — не дублируем её в FormWriter.
                events.put(eventName, io.github.onec.xmlgen.form.edit.EventHandlerNames.defaultFor(elementName, eventName));
            }
        }
        Object handlers = element.get("handlers");
        if (handlers instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> h : ((Map<?, ?>) handlers).entrySet()) {
                events.put(String.valueOf(h.getKey()), String.valueOf(h.getValue()));
            }
        }
        if (events.isEmpty()) {
            return;
        }
        String indent = "\t".repeat(depth);
        writer.writeCharacters(indent);
        writer.writeStartElement("Events");
        writer.writeCharacters("\n");
        for (Map.Entry<String, String> event : events.entrySet()) {
            writer.writeCharacters(indent + "\t");
            writer.writeStartElement("Event");
            writer.writeAttribute("name", event.getKey());
            writer.writeCharacters(event.getValue());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
        writer.writeCharacters(indent);
        writer.writeEndElement(); // Events
        writer.writeCharacters("\n");
    }
    //++agent TASK-174

    //++agent TASK-174 [07.06.2026 00:00:00]
    // XG-16: Button в каноне Designer содержит только ExtendedTooltip (без ContextMenu).
    // Для элементов без контекстного меню (Button, в отличие от InputField/CheckBoxField/LabelDecoration)
    // используем этот метод вместо writeAutoElements.
    private void writeExtendedTooltipOnly(String name, int parentId, int depth) throws XMLStreamException {
        String indent = "\t".repeat(depth);
        int tooltipId = elementIdGen.next();
        writer.writeCharacters(indent);
        writer.writeEmptyElement("ExtendedTooltip");
        writer.writeAttribute("name", name + "РасширеннаяПодсказка");
        writer.writeAttribute("id", String.valueOf(tooltipId));
        writer.writeCharacters("\n");
    }
    //++agent TASK-174
    
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
        //**agent TASK-175 [07.06.2026 19:20:00]
        // XG-39, EDT-сосед того же класса (36cd63d8): раньше — безусловный autoTitle=true.
        // Триггер тот же, что в Designer-пути: явный title (dsl.title или properties.title)
        // без явного properties.autoTitle → false; явный autoTitle уважается всегда.
        //writeElement("autoTitle", "true");
        boolean edtExplicitTitle = (dsl.getTitle() != null && !dsl.getTitle().isBlank())
                || (dsl.getProperties() != null
                        && dsl.getProperties().get("title") instanceof String edtPropTitle
                        && !edtPropTitle.isBlank());
        String edtAutoTitle;
        if (dsl.getProperties() != null && dsl.getProperties().containsKey("autoTitle")) {
            edtAutoTitle = String.valueOf(dsl.getProperties().get("autoTitle"));
        } else {
            edtAutoTitle = edtExplicitTitle ? "false" : "true";
        }
        writeElement("autoTitle", edtAutoTitle);
        //**agent TASK-175
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
        String dataPath = dataPath(element);
        if (dataPath != null) {
            writer.writeCharacters("\t".repeat(indentLevel));
            writer.writeStartElement("dataPath");
            writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "form:DataPath");
            writer.writeCharacters("\n");
            indentLevel++;
            writeElement("segments", dataPath);
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

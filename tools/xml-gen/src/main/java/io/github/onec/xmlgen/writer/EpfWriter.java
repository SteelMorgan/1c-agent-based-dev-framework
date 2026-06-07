package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import io.github.onec.xmlgen.dsl.EpfDsl;
import io.github.onec.xmlgen.editor.ObjectContainerEditor;
import io.github.onec.xmlgen.format.DesignerLayout;
import io.github.onec.xmlgen.format.EdtLayout;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.TypeResolver;
import io.github.onec.xmlgen.model.UuidGenerator;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Генератор XML для внешней обработки (EPF) и внешнего отчёта (ERF).
 */
public class EpfWriter extends XmlWriter {

    private static final String EPF_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";
    private static final String ERF_CLASS_ID = "e41aff26-25cf-4bb6-b6c1-3f478a75f374";

    //++agent TASK-171 [01.06.2026 12:00:00]
    // Каноническое имя основной схемы компоновки данных внешнего отчёта.
    // Совпадает с тем, как Конфигуратор именует основной макет отчёта в грунт-труф
    // (src/xml/Reports/_Демо*/Templates/ОсновнаяСхемаКомпоновкиДанных): главный макет
    // отчёта называется именно так, и на него ссылается MainDataCompositionSchema.
    public static final String MAIN_DCS_TEMPLATE_NAME = "ОсновнаяСхемаКомпоновкиДанных";
    public static final String MAIN_DCS_TEMPLATE_SYNONYM = "Основная схема компоновки данных";
    //++agent TASK-171

    private final OutputFormat format;
    private final boolean isReport;

    public EpfWriter(OutputFormat format) {
        this(format, false);
    }

    public EpfWriter(OutputFormat format, boolean isReport) {
        this.format = format;
        this.isReport = isReport;
    }

    private String rootElement() {
        return isReport ? "ExternalReport" : "ExternalDataProcessor";
    }

    private String generatedTypeName(String name) {
        return isReport ? "ExternalReportObject." + name : "ExternalDataProcessorObject." + name;
    }

    private static String generatedTypeName(boolean report, String name) {
        return report ? "ExternalReportObject." + name : "ExternalDataProcessorObject." + name;
    }

    private static String rootElement(boolean report) {
        return report ? "ExternalReport" : "ExternalDataProcessor";
    }

    private String classId() {
        return isReport ? ERF_CLASS_ID : EPF_CLASS_ID;
    }

    private String objectKind() {
        return isReport ? "ERF" : "EPF";
    }

    private String edtSubdir() {
        return isReport ? "ExternalReports" : "ExternalDataProcessors";
    }
    
    /**
     * Создать новую обработку (scaffold).
     * 
     * @param name имя обработки
     * @param synonym синоним
     * @param outputDir выходной каталог
     */
    public void init(String name, String synonym, Path outputDir) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            initDesigner(name, synonym, outputDir);
        } else {
            initEdt(name, synonym, outputDir);
        }
    }

    //++agent TASK-171 [01.06.2026 12:00:00]
    /**
     * Создать внешний отчёт (ERF) "из коробки" вместе с основной схемой компоновки данных.
     *
     * <p>Зачем: каноничный внешний отчёт почти всегда строится на СКД. Раньше основной кейс
     * закрывался только ручной связкой {@code init --type report} + {@code add-template --type
     * DataCompositionSchema}; флаг {@code --with-skd} делает это за один шаг, воспроизводя
     * связку из грунт-труф (src/xml/Reports/_ДемоФайлыВспомогательный): макет
     * {@code ОсновнаяСхемаКомпоновкиДанных} типа DataCompositionSchema + регистрация в
     * ChildObjects + проставленный {@code MainDataCompositionSchema}.
     *
     * <p>Связка целиком переиспользует уже проверенный путь {@link #addTemplate} (D3/D6):
     * тело DCS из единого источника {@link ObjectContainerEditor#getTemplateBody} (BOM+CRLF),
     * аккуратная вставка {@code <Template>} в ChildObjects, а для ERF — выставление
     * {@code MainDataCompositionSchema} с префиксом {@code ExternalReport.}.
     *
     * @throws IllegalStateException если writer создан не для отчёта (DCS-макет вне отчёта
     *                               не имеет MainDataCompositionSchema, поэтому флаг бессмысленен)
     */
    public void initWithSkd(String name, String synonym, Path outputDir) throws IOException, XMLStreamException {
        if (!isReport) {
            // Флаг --with-skd применим только к внешнему отчёту: у обычной обработки нет свойства
            // MainDataCompositionSchema, привязывать схему не к чему. Внятная ошибка вместо тихого no-op.
            throw new IllegalStateException(
                    "--with-skd is only valid for external reports (--type report); "
                    + "external data processors have no MainDataCompositionSchema to bind a schema to.");
        }
        init(name, synonym, outputDir);
        // Синоним макета не зависит от синонима самого отчёта — у макета своё каноничное имя.
        addTemplate(name, MAIN_DCS_TEMPLATE_NAME, MAIN_DCS_TEMPLATE_SYNONYM, "DataCompositionSchema", outputDir);
    }
    //++agent TASK-171

    /**
     * Добавить форму в обработку.
     * 
     * @param epfName имя обработки
     * @param formName имя формы
     * @param formSynonym синоним формы
     * @param outputDir выходной каталог (где находится EPF)
     * @param setAsDefault установить как основную форму
     */
    public void addForm(String epfName, String formName, String formSynonym, Path outputDir, boolean setAsDefault) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            addFormDesigner(epfName, formName, formSynonym, outputDir, setAsDefault);
        } else {
            addFormEdt(epfName, formName, formSynonym, outputDir);
        }
    }
    
    /**
     * Добавить макет в обработку.
     * 
     * @param epfName имя обработки
     * @param templateName имя макета
     * @param templateSynonym синоним макета
     * @param templateType тип макета (SpreadsheetDocument, HTMLDocument, TextDocument, BinaryData)
     * @param outputDir выходной каталог
     */
    public void addTemplate(String epfName, String templateName, String templateSynonym, String templateType, Path outputDir) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            addTemplateDesigner(epfName, templateName, templateSynonym, templateType, outputDir);
        } else {
            addTemplateEdt(epfName, templateName, templateSynonym, templateType, outputDir);
        }
    }
    
    private void initDesigner(String name, String synonym, Path outputDir) throws IOException, XMLStreamException {
        // Создать структуру каталогов
        Path rootXml = DesignerLayout.createEpfStructure(outputDir, name);
        
        // Генерировать UUID
        String objectId = UuidGenerator.generate();
        String classId = classId();
        
        String[] typeUuids = new String[] {UuidGenerator.generate(), UuidGenerator.generate()};
        
        // Создать корневой XML
        createWriter(rootXml, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        // Добавить namespace xr
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
        writeRootElement("MetaDataObject", allNamespaces, rootAttrs);
        
        // Root element (ExternalDataProcessor or ExternalReport)
        writer.writeCharacters("\t");
        writer.writeStartElement(rootElement());
        writer.writeAttribute("uuid", objectId);
        writer.writeCharacters("\n");
        indentLevel = 2;

        // InternalInfo
        writeInternalInfo(objectId, classId, typeUuids[0], typeUuids[1], name);

        // Properties
        startElement("Properties");
        writeElement("Name", name);
        writeSynonym(synonym != null ? synonym : name);
        writeElement("Comment", "");
        writeElement("DefaultForm", "");
        writeElement("AuxiliaryForm", "");
        if (isReport) {
            writeElement("MainDataCompositionSchema", "");
            writeElement("DefaultSettingsForm", "");
            writeElement("AuxiliarySettingsForm", "");
            writeElement("DefaultVariantForm", "");
            writeElement("VariantsStorage", "");
            writeElement("SettingsStorage", "");
        }
        endElement(); // Properties
        
        // ChildObjects (пустой пока)
        startElement("ChildObjects");
        endElement(); // ChildObjects
        
        indentLevel--;
        endElement(); // ExternalDataProcessor / ExternalReport

        writer.writeEndElement(); // MetaDataObject
        close();

        // Создать пустой ObjectModule.bsl
        Path objectModule = outputDir.resolve(name).resolve("Ext/ObjectModule.bsl");
        Files.createDirectories(objectModule.getParent());
        String moduleComment = isReport
                ? "// Модуль объекта отчёта " + name + "\n"
                : "// Модуль объекта обработки " + name + "\n";
        //++agent TASK-172 [02.06.2026 07:25:00]
        // Канон Designer (_Демо): ObjectModule.bsl — BOM + CRLF (эталон
        // Documents/_Демо*/Ext/ObjectModule.bsl: ef bb bf + CRLF).
        Files.write(objectModule, io.github.onec.xmlgen.io.Crlf.withBom(moduleComment));
        //++agent TASK-172

        System.out.println("Created " + objectKind() + ": " + name);
        System.out.println("  Root XML: " + rootXml);
        System.out.println("  Object module: " + objectModule);
    }
    
    private void initEdt(String name, String synonym, Path outputDir) throws IOException, XMLStreamException {
        // Создать структуру каталогов EDT
        Path mdoPath = EdtLayout.createEpfStructure(outputDir, name, edtSubdir());
        
        // Генерировать UUID
        String uuid = UuidGenerator.generate();
        String objectTypeId = UuidGenerator.generate();
        String valueTypeId = UuidGenerator.generate();
        
        // Создать .mdo файл
        createWriter(mdoPath, false, new HashMap<>()); // БЕЗ BOM для EDT
        writeXmlDeclaration();
        
        writer.writeStartElement("mdclass", rootElement(), "http://g5.1c.ru/v8/dt/metadata/mdclass");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeNamespace("core", "http://g5.1c.ru/v8/dt/mcore");
        writer.writeNamespace("mdclass", "http://g5.1c.ru/v8/dt/metadata/mdclass");
        writer.writeAttribute("uuid", uuid);
        writer.writeCharacters("\n");
        indentLevel = 1;
        
        // producedTypes
        startElement("producedTypes");
        writer.writeCharacters("\t\t");
        writer.writeEmptyElement("objectType");
        writer.writeAttribute("typeId", objectTypeId);
        writer.writeAttribute("valueTypeId", valueTypeId);
        writer.writeCharacters("\n");
        endElement(); // producedTypes
        
        writeElement("name", name);
        
        // synonym
        if (synonym != null) {
            startElement("synonym");
            writeElement("key", "ru");
            writeElement("value", synonym);
            endElement(); // synonym
        }
        
        writer.writeEndElement(); // mdclass:ExternalDataProcessor/ExternalReport
        close();

        // Создать пустой ObjectModule.bsl
        Path objectModule = mdoPath.getParent().resolve("ObjectModule.bsl");
        String moduleComment = isReport
                ? "// Модуль объекта отчёта " + name + "\n"
                : "// Модуль объекта обработки " + name + "\n";
        Files.writeString(objectModule, moduleComment);

        System.out.println("Created " + objectKind() + " (EDT): " + name);
        System.out.println("  MDO: " + mdoPath);
        System.out.println("  Object module: " + objectModule);
    }
    
    private void addFormDesigner(String epfName, String formName, String formSynonym, Path outputDir, boolean setAsDefault) throws IOException, XMLStreamException {
        Path epfXmlPath = outputDir.resolve(epfName + ".xml");
        ObjectContainerEditor editor = new ObjectContainerEditor(epfXmlPath);
        boolean targetIsReport = targetIsReport(editor);
        String formatVersion = ConfigurationXmlReader.readFormatVersion(epfXmlPath);
        if (editor.hasForm(formName)) {
            throw new IllegalArgumentException("Form '" + formName + "' already exists in ChildObjects");
        }

        // 1. Создать структуру каталогов для формы
        Path formsDir = outputDir.resolve(epfName).resolve("Forms");
        Path formMetaXml = formsDir.resolve(formName + ".xml");
        Path formDir = formsDir.resolve(formName);
        if (Files.exists(formMetaXml) || Files.exists(formDir)) {
            throw new IllegalArgumentException("Form '" + formName + "' already exists on disk in " + formsDir);
        }

        Path formXmlPath = DesignerLayout.createFormStructure(formsDir, formName);
        
        // 2. Создать метаданные формы (Forms/<FormName>.xml)
        String formUuid = UuidGenerator.generate();
        createFormMetadata(formsDir.resolve(formName + ".xml"), formName,
                formSynonym != null ? formSynonym : formName, formUuid, formatVersion);
        
        // 3. Создать описание формы (Forms/<FormName>/Ext/Form.xml)
        createFormDefinition(formXmlPath, epfName, targetIsReport, formatVersion);
        
        // 4. Создать пустой модуль формы
        Path moduleDir = formXmlPath.getParent().resolve("Form");
        Files.createDirectories(moduleDir);
        Path modulePath = moduleDir.resolve("Module.bsl");
        //++agent TASK-172 [02.06.2026 07:25:00]
        // Канон Designer (_Демо): .bsl модуля формы — BOM + CRLF.
        Files.write(modulePath, io.github.onec.xmlgen.io.Crlf.withBom("// Модуль формы " + formName + "\n"));
        //++agent TASK-172

        // 5. Обновить корневой XML обработки/отчёта (добавить <Form> в ChildObjects)
        editor.addForm(formName);
        if (setAsDefault) {
            editor.setDefaultForm(rootElement(targetIsReport) + "." + epfName + ".Form." + formName);
        }
        editor.save();
        
        System.out.println("Added form: " + formName);
        System.out.println("  Metadata: " + formsDir.resolve(formName + ".xml"));
        System.out.println("  Definition: " + formXmlPath);
        System.out.println("  Module: " + modulePath);
    }
    
    /**
     * Создать метаданные формы (Forms/<Name>.xml).
     */
    private void createFormMetadata(Path outputPath, String name, String synonym, String uuid,
                                    String formatVersion) throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", formatVersion);
        writeRootElement("MetaDataObject", allNamespaces, rootAttrs);
        
        // Form
        writer.writeCharacters("\t");
        writer.writeStartElement("Form");
        writer.writeAttribute("uuid", uuid);
        writer.writeCharacters("\n");
        indentLevel = 2;
        
        startElement("Properties");
        writeElement("Name", name);
        writeSynonym(synonym);
        writeElement("Comment", "");
        writeElement("FormType", "Managed");
        writeElement("IncludeHelpInContents", "false");
        
        // UsePurposes
        startElement("UsePurposes");
        writer.writeCharacters("\t\t\t");
        writer.writeStartElement("v8:Value");
        writer.writeAttribute("xsi:type", "app:ApplicationUsePurpose");
        writer.writeCharacters("PlatformApplication");
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeCharacters("\t\t\t");
        writer.writeStartElement("v8:Value");
        writer.writeAttribute("xsi:type", "app:ApplicationUsePurpose");
        writer.writeCharacters("MobilePlatformApplication");
        writer.writeEndElement();
        writer.writeCharacters("\n");
        endElement(); // UsePurposes
        
        writeElement("ExtendedPresentation", "");
        endElement(); // Properties
        
        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // Form
        writer.writeCharacters("\n");
        
        writer.writeEndElement(); // MetaDataObject
        close();
    }
    
    /**
     * Создать описание формы (Form.xml).
     */
    private void createFormDefinition(Path outputPath, String epfName, boolean targetIsReport,
                                      String formatVersion) throws IOException, XMLStreamException {
        //**agent TASK-172 [01.06.2026 22:05:00]
        // BOM на Form.xml: канон _Демо — ВСЕ Ext/Form.xml идут с BOM (ef bb bf,
        // проверено на CommonForms/Documents-формах), и standalone FormWriter:133
        // (TASK-171) тоже пишет BOM. Прежний false расходился с каноном — исправлено.
        //createWriter(outputPath, false, FORM_NAMESPACES); // BOM-долг
        createWriter(outputPath, true, FORM_NAMESPACES);
        //**agent TASK-172
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(FORM_NAMESPACES);
        allNamespaces.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        allNamespaces.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", formatVersion);
        writeRootElement("Form", allNamespaces, rootAttrs);
        
        // AutoCommandBar (обязательный, id=-1)
        writer.writeCharacters("\t");
        writer.writeEmptyElement("AutoCommandBar");
        writer.writeAttribute("name", "ФормаКоманднаяПанель");
        writer.writeAttribute("id", "-1");
        writer.writeCharacters("\n");
        
        // ChildItems (пустой пока)
        startElement("ChildItems");
        endElement();
        
        // Attributes
        startElement("Attributes");
        
        // Основной реквизит Объект
        writer.writeCharacters("\t\t");
        writer.writeStartElement("Attribute");
        writer.writeAttribute("name", "Объект");
        writer.writeAttribute("id", "1");
        writer.writeCharacters("\n");
        indentLevel = 3;
        
        startElement("Type");
        writeElement("v8:Type", "cfg:" + generatedTypeName(targetIsReport, epfName));
        endElement(); // Type
        
        writeElement("MainAttribute", "true");
        
        indentLevel = 2;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // Attribute
        writer.writeCharacters("\n");
        
        endElement(); // Attributes
        
        writer.writeEndElement(); // Form
        close();
    }
    
    private void addTemplateDesigner(String epfName, String templateName, String templateSynonym, String templateType, Path outputDir) throws IOException, XMLStreamException {
        // TASK-171 D3: нормализуем тип через единый парсер (поддержка алиасов и DataCompositionSchema).
        // Раньше DCS падал "Unknown template type", и внешний отчёт со схемой собрать было нельзя.
        String canonicalType = TemplateWriter.canonicalTemplateTypeName(templateType);

        Path epfXmlPath = outputDir.resolve(epfName + ".xml");
        ObjectContainerEditor editor = new ObjectContainerEditor(epfXmlPath);
        boolean targetIsReport = targetIsReport(editor);
        String formatVersion = ConfigurationXmlReader.readFormatVersion(epfXmlPath);
        if (editor.hasTemplate(templateName)) {
            throw new IllegalArgumentException("Template '" + templateName + "' already exists in ChildObjects");
        }

        // 1. Создать структуру каталогов для макета
        Path templatesDir = outputDir.resolve(epfName).resolve("Templates");
        Path templateMetaXml = templatesDir.resolve(templateName + ".xml");
        Path templateDir = templatesDir.resolve(templateName);
        if (Files.exists(templateMetaXml) || Files.exists(templateDir)) {
            throw new IllegalArgumentException("Template '" + templateName + "' already exists on disk in " + templatesDir);
        }

        Path templateXmlPath = DesignerLayout.createTemplateStructure(templatesDir, templateName);

        // 2. Создать метаданные макета (Templates/<Name>.xml)
        String templateUuid = UuidGenerator.generate();
        createTemplateMetadata(templatesDir.resolve(templateName + ".xml"), templateName,
                              templateSynonym != null ? templateSynonym : templateName,
                              templateUuid, canonicalType, formatVersion);

        // 3. Создать тело макета (Templates/<Name>/Ext/Template.<ext>)
        // TASK-171 D1/W5: тело генерируем через ObjectContainerEditor.getTemplateBody — корректную
        // ветку (SpreadsheetDocument → корень <document>), чтобы EPF/ERF и конфиг-объекты шли единым путём.
        String extension = ObjectContainerEditor.getExtension(canonicalType);
        Path templateBodyPath = templateXmlPath.getParent().resolve("Template." + extension);
        createTemplateBody(templateBodyPath, canonicalType, templateName);

        // 4. Обновить корневой XML обработки (добавить <Template> в ChildObjects)
        // TASK-171 D9/W5: вставку делаем через ObjectContainerEditor (аккуратный whitespace,
        // expandSelfClosingChildObjects) вместо самописного String.replace.
        editor.addTemplate(templateName);

        // TASK-171 D3/D6: для ERF со схемой компоновки проставляем MainDataCompositionSchema,
        // если оно ещё пустое. Префикс для внешнего отчёта — ExternalReport. (НЕ Report.),
        // т.к. это плоская EPF/ERF-раскладка, а не конфиг-объект Report.
        if (targetIsReport && TemplateType.valueByName(canonicalType) == TemplateType.DATA_COMPOSITION_SCHEME) {
            editor.setMainDataCompositionSchemaIfEmpty(
                    "ExternalReport." + epfName + ".Template." + templateName);
        }
        editor.save();

        System.out.println("Added template: " + templateName);
        System.out.println("  Metadata: " + templatesDir.resolve(templateName + ".xml"));
        System.out.println("  Body: " + templateBodyPath);
    }
    
    /**
     * Создать метаданные макета (Templates/<Name>.xml).
     */
    private void createTemplateMetadata(Path outputPath, String name, String synonym, String uuid,
                                        String templateType, String formatVersion) throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", formatVersion);
        writeRootElement("MetaDataObject", allNamespaces, rootAttrs);
        
        // Template
        writer.writeCharacters("\t");
        writer.writeStartElement("Template");
        writer.writeAttribute("uuid", uuid);
        writer.writeCharacters("\n");
        indentLevel = 2;
        
        startElement("Properties");
        writeElement("Name", name);
        writeSynonym(synonym);
        writeElement("Comment", "");
        writeElement("TemplateType", templateType);
        endElement(); // Properties
        
        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // Template
        writer.writeCharacters("\n");
        
        writer.writeEndElement(); // MetaDataObject
        close();
    }
    
    /**
     * Создать тело макета.
     *
     * <p>TASK-171 D1/D3/D7/W5: тело генерируем через {@link ObjectContainerEditor#getTemplateBody},
     * единый корректный источник истины для EPF/ERF и конфиг-объектов. Раньше EPF-ветка писала
     * собственный дефектный шаблон с корнем {@code <SpreadsheetDocument>} (демо-эталон и наш же
     * {@code validate --type mxl} требуют корень {@code <document>}), не знала DataCompositionSchema
     * и писала тела без BOM.
     *
     * @param templateType <b>канонический</b> тип (уже нормализован через
     *                      {@link TemplateWriter#canonicalTemplateTypeName})
     */
    private void createTemplateBody(Path outputPath, String templateType, String templateName) throws IOException {
        TemplateType tt = TemplateType.valueByName(templateType);

        // BinaryData — пустой бинарный файл без BOM (BOM в бинаре — мусор).
        if (tt == TemplateType.BINARY_DATA) {
            Files.write(outputPath, new byte[0]);
            return;
        }

        // Единый источник тела (корректные корни: SpreadsheetDocument → <document>, DCS → <DataCompositionSchema>).
        String content = ObjectContainerEditor.getTemplateBody(templateType);

        // Тела макетов в Designer-выводе пишем с UTF-8 BOM — реальные демо-макеты
        // (src/xml/.../Templates/**) начинаются с ef bb bf, как и весь Designer-дамп.
        writeBodyWithBom(outputPath, content);
    }

    /** Записать текстовое тело макета с UTF-8 BOM (канон Designer-вывода, TASK-171). */
    private void writeBodyWithBom(Path path, String content) throws IOException {
        //++agent TASK-172 [02.06.2026 07:26:00]
        // Канон Designer (_Демо): тела макетов Template.xml — BOM + CRLF (TASK-172 добавил CRLF).
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }
    
    // TASK-171 D1/W5: getTemplateExtension (Designer) удалён — расширение теперь берётся из
    // ObjectContainerEditor.getExtension (единый источник). updateEpfXmlAddTemplate (самописный
    // String.replace) удалён — ChildObjects правится через ObjectContainerEditor.addTemplate (D9).

    /**
     * Записать InternalInfo для обработки.
     */
    private void writeInternalInfo(String objectId, String classIdValue, String typeId, String valueId, String name) throws XMLStreamException {
        startElement("InternalInfo");

        // ContainedObject
        startElement("xr:ContainedObject");
        writeElement("xr:ClassId", classIdValue);
        writeElement("xr:ObjectId", objectId);
        endElement(); // xr:ContainedObject

        // GeneratedType
        writer.writeCharacters("\t\t");
        writer.writeStartElement("xr:GeneratedType");
        writer.writeAttribute("name", generatedTypeName(name));
        writer.writeAttribute("category", "Object");
        writer.writeCharacters("\n");
        indentLevel++;
        writeElement("xr:TypeId", typeId);
        writeElement("xr:ValueId", valueId);
        indentLevel--;
        writer.writeCharacters("\t\t");
        writer.writeEndElement(); // xr:GeneratedType
        writer.writeCharacters("\n");

        endElement(); // InternalInfo
    }

    private boolean targetIsReport(ObjectContainerEditor editor) {
        String objectType = editor.detectObjectType();
        if ("ExternalReport".equals(objectType)) {
            return true;
        }
        if ("ExternalDataProcessor".equals(objectType)) {
            return false;
        }
        return isReport;
    }
    
    /**
     * Записать синоним (многоязычный).
     */
    private void writeSynonym(String text) throws XMLStreamException {
        startElement("Synonym");
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        endElement(); // Synonym
    }
    
    // ==================== EDT format ====================
    
    private void addFormEdt(String epfName, String formName, String formSynonym, Path outputDir) throws IOException, XMLStreamException {
        // EDT: Forms/<FormName>/Form.form + Module.bsl
        Path epfDir = outputDir.resolve("src/" + edtSubdir()).resolve(epfName);
        Path formsDir = epfDir.resolve("Forms");
        Path formPath = EdtLayout.createFormStructure(formsDir, formName);
        
        // Создать минимальный Form.form
        FormWriter formWriter = new FormWriter(OutputFormat.EDT);
        formWriter.create(new io.github.onec.xmlgen.dsl.FormDsl(null, null, null, null, null, null, null, null), formPath);
        
        // Создать Module.bsl
        Path modulePath = formPath.getParent().resolve("Module.bsl");
        Files.writeString(modulePath, "// Модуль формы " + formName + "\n");
        
        System.out.println("Added form (EDT): " + formName);
        System.out.println("  Form: " + formPath);
        System.out.println("  Module: " + modulePath);
    }
    
    private void addTemplateEdt(String epfName, String templateName, String templateSynonym, String templateType, Path outputDir) throws IOException, XMLStreamException {
        // TASK-171 D3: нормализуем тип (поддержка алиасов и DataCompositionSchema) — иначе DCS падал.
        String canonicalType = TemplateWriter.canonicalTemplateTypeName(templateType);

        // EDT: Templates/<TemplateName>/Template.<ext>
        Path epfDir = outputDir.resolve("src/" + edtSubdir()).resolve(epfName);
        Path templatesDir = epfDir.resolve("Templates");
        Path templatePath = EdtLayout.createTemplateStructure(templatesDir, templateName);

        // Определить расширение для EDT
        String edtExtension = getEdtTemplateExtension(canonicalType);
        Path templateBodyPath = templatePath.getParent().resolve("Template." + edtExtension);
        createTemplateBody(templateBodyPath, canonicalType, templateName);

        System.out.println("Added template (EDT): " + templateName);
        System.out.println("  Body: " + templateBodyPath);
    }

    /**
     * Получить расширение файла макета для EDT.
     */
    private String getEdtTemplateExtension(String templateType) {
        TemplateType tt = TemplateType.valueByName(templateType);
        if (tt == TemplateType.SPREADSHEET_DOCUMENT) return "mxlx";
        // TASK-171 D3: схема компоновки данных в EDT — файл .dcs.
        if (tt == TemplateType.DATA_COMPOSITION_SCHEME) return "dcs";
        if (tt == TemplateType.HTML_DOCUMENT) return "html";
        if (tt == TemplateType.TEXT_DOCUMENT) return "txt";
        if (tt == TemplateType.BINARY_DATA) return "bin";
        return "xml";
    }
}

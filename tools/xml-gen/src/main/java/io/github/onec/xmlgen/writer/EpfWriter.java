package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import io.github.onec.xmlgen.dsl.EpfDsl;
import io.github.onec.xmlgen.format.DesignerLayout;
import io.github.onec.xmlgen.format.EdtLayout;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.TypeResolver;
import io.github.onec.xmlgen.model.UuidGenerator;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Генератор XML для внешней обработки (EPF).
 */
public class EpfWriter extends XmlWriter {
    
    private final OutputFormat format;
    
    public EpfWriter(OutputFormat format) {
        this.format = format;
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
        String[] uuids = UuidGenerator.generateEpfPair();
        String objectId = uuids[0];
        String classId = uuids[1];
        
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
        
        // ExternalDataProcessor
        writer.writeCharacters("\t");
        writer.writeStartElement("ExternalDataProcessor");
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
        endElement(); // Properties
        
        // ChildObjects (пустой пока)
        startElement("ChildObjects");
        endElement(); // ChildObjects
        
        indentLevel--;
        endElement(); // ExternalDataProcessor
        
        writer.writeEndElement(); // MetaDataObject
        close();
        
        // Создать пустой ObjectModule.bsl
        Path objectModule = outputDir.resolve(name).resolve("Ext/ObjectModule.bsl");
        Files.createDirectories(objectModule.getParent());
        Files.writeString(objectModule, "// Модуль объекта обработки " + name + "\n");
        
        System.out.println("Created EPF: " + name);
        System.out.println("  Root XML: " + rootXml);
        System.out.println("  Object module: " + objectModule);
    }
    
    private void initEdt(String name, String synonym, Path outputDir) throws IOException, XMLStreamException {
        // Создать структуру каталогов EDT
        Path mdoPath = EdtLayout.createEpfStructure(outputDir, name);
        
        // Генерировать UUID
        String uuid = UuidGenerator.generate();
        String objectTypeId = UuidGenerator.generate();
        String valueTypeId = UuidGenerator.generate();
        
        // Создать .mdo файл
        createWriter(mdoPath, false, new HashMap<>()); // БЕЗ BOM для EDT
        writeXmlDeclaration();
        
        writer.writeStartElement("mdclass", "ExternalDataProcessor", "http://g5.1c.ru/v8/dt/metadata/mdclass");
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
        
        writer.writeEndElement(); // mdclass:ExternalDataProcessor
        close();
        
        // Создать пустой ObjectModule.bsl
        Path objectModule = mdoPath.getParent().resolve("ObjectModule.bsl");
        Files.writeString(objectModule, "// Модуль объекта обработки " + name + "\n");
        
        System.out.println("Created EPF (EDT): " + name);
        System.out.println("  MDO: " + mdoPath);
        System.out.println("  Object module: " + objectModule);
    }
    
    private void addFormDesigner(String epfName, String formName, String formSynonym, Path outputDir, boolean setAsDefault) throws IOException, XMLStreamException {
        // 1. Создать структуру каталогов для формы
        Path formsDir = outputDir.resolve(epfName).resolve("Forms");
        Path formXmlPath = DesignerLayout.createFormStructure(formsDir, formName);
        
        // 2. Создать метаданные формы (Forms/<FormName>.xml)
        String formUuid = UuidGenerator.generate();
        createFormMetadata(formsDir.resolve(formName + ".xml"), formName, formSynonym != null ? formSynonym : formName, formUuid);
        
        // 3. Создать описание формы (Forms/<FormName>/Ext/Form.xml)
        createFormDefinition(formXmlPath, epfName);
        
        // 4. Создать пустой модуль формы
        Path moduleDir = formXmlPath.getParent().resolve("Form");
        Files.createDirectories(moduleDir);
        Path modulePath = moduleDir.resolve("Module.bsl");
        Files.writeString(modulePath, "// Модуль формы " + formName + "\n");
        
        // 5. Обновить корневой XML обработки (добавить <Form> в ChildObjects)
        updateEpfXmlAddForm(outputDir.resolve(epfName + ".xml"), epfName, formName, setAsDefault);
        
        System.out.println("Added form: " + formName);
        System.out.println("  Metadata: " + formsDir.resolve(formName + ".xml"));
        System.out.println("  Definition: " + formXmlPath);
        System.out.println("  Module: " + modulePath);
    }
    
    /**
     * Создать метаданные формы (Forms/<Name>.xml).
     */
    private void createFormMetadata(Path outputPath, String name, String synonym, String uuid) throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
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
    private void createFormDefinition(Path outputPath, String epfName) throws IOException, XMLStreamException {
        createWriter(outputPath, false, FORM_NAMESPACES); // БЕЗ BOM для Form.xml
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(FORM_NAMESPACES);
        allNamespaces.put("dcscor", "http://v8.1c.ru/8.1/data-composition-system/core");
        allNamespaces.put("dcsset", "http://v8.1c.ru/8.1/data-composition-system/settings");
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
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
        writeElement("v8:Type", "cfg:ExternalDataProcessorObject." + epfName);
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
    
    /**
     * Обновить корневой XML обработки — добавить форму в ChildObjects.
     */
    private void updateEpfXmlAddForm(Path epfXmlPath, String epfName, String formName, boolean setAsDefault) throws IOException {
        // Читаем существующий XML
        String content = Files.readString(epfXmlPath);
        
        // Добавляем <Form> в ChildObjects
        String formEntry = "\t\t<Form>" + formName + "</Form>\n";
        content = content.replace("</ChildObjects>", formEntry + "\t</ChildObjects>");
        
        // Если setAsDefault, обновляем DefaultForm
        if (setAsDefault) {
            String defaultFormValue = "ExternalDataProcessor." + epfName + ".Form." + formName;
            content = content.replace("<DefaultForm></DefaultForm>", 
                                     "<DefaultForm>" + defaultFormValue + "</DefaultForm>");
        }
        
        Files.writeString(epfXmlPath, content);
    }
    
    private void addTemplateDesigner(String epfName, String templateName, String templateSynonym, String templateType, Path outputDir) throws IOException, XMLStreamException {
        // 1. Создать структуру каталогов для макета
        Path templatesDir = outputDir.resolve(epfName).resolve("Templates");
        Path templateXmlPath = DesignerLayout.createTemplateStructure(templatesDir, templateName);
        
        // 2. Создать метаданные макета (Templates/<Name>.xml)
        String templateUuid = UuidGenerator.generate();
        createTemplateMetadata(templatesDir.resolve(templateName + ".xml"), templateName, 
                              templateSynonym != null ? templateSynonym : templateName, 
                              templateUuid, templateType);
        
        // 3. Создать тело макета (Templates/<Name>/Ext/Template.<ext>)
        String extension = getTemplateExtension(templateType);
        Path templateBodyPath = templateXmlPath.getParent().resolve("Template." + extension);
        createTemplateBody(templateBodyPath, templateType, templateName);
        
        // 4. Обновить корневой XML обработки (добавить <Template> в ChildObjects)
        updateEpfXmlAddTemplate(outputDir.resolve(epfName + ".xml"), templateName);
        
        System.out.println("Added template: " + templateName);
        System.out.println("  Metadata: " + templatesDir.resolve(templateName + ".xml"));
        System.out.println("  Body: " + templateBodyPath);
    }
    
    /**
     * Создать метаданные макета (Templates/<Name>.xml).
     */
    private void createTemplateMetadata(Path outputPath, String name, String synonym, String uuid, String templateType) throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
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
     */
    private void createTemplateBody(Path outputPath, String templateType, String templateName) throws IOException {
        TemplateType tt = TemplateType.valueByName(templateType);
        String content;
        
        if (tt == TemplateType.SPREADSHEET_DOCUMENT) {
            content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                     "<SpreadsheetDocument xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">\n" +
                     "\t<!-- Табличный документ " + templateName + " -->\n" +
                     "</SpreadsheetDocument>\n";
        } else if (tt == TemplateType.HTML_DOCUMENT) {
            content = "<!DOCTYPE html>\n" +
                     "<html>\n" +
                     "<head>\n" +
                     "\t<meta charset=\"UTF-8\">\n" +
                     "\t<title>" + templateName + "</title>\n" +
                     "</head>\n" +
                     "<body>\n" +
                     "\t<h1>" + templateName + "</h1>\n" +
                     "</body>\n" +
                     "</html>\n";
        } else if (tt == TemplateType.TEXT_DOCUMENT) {
            content = "// Текстовый документ " + templateName + "\n";
        } else if (tt == TemplateType.BINARY_DATA) {
            Files.write(outputPath, new byte[0]);
            return;
        } else {
            throw new IllegalArgumentException("Unknown template type: " + templateType);
        }
        
        Files.writeString(outputPath, content);
    }
    
    /**
     * Получить расширение файла для типа макета.
     */
    private String getTemplateExtension(String templateType) {
        TemplateType tt = TemplateType.valueByName(templateType);
        if (tt == TemplateType.SPREADSHEET_DOCUMENT) return "xml";
        if (tt == TemplateType.HTML_DOCUMENT) return "html";
        if (tt == TemplateType.TEXT_DOCUMENT) return "txt";
        if (tt == TemplateType.BINARY_DATA) return "bin";
        return "xml";
    }
    
    /**
     * Обновить корневой XML обработки — добавить макет в ChildObjects.
     */
    private void updateEpfXmlAddTemplate(Path epfXmlPath, String templateName) throws IOException {
        String content = Files.readString(epfXmlPath);
        
        // Добавляем <Template> в ChildObjects
        String templateEntry = "\t\t<Template>" + templateName + "</Template>\n";
        content = content.replace("</ChildObjects>", templateEntry + "\t</ChildObjects>");
        
        Files.writeString(epfXmlPath, content);
    }
    
    /**
     * Записать InternalInfo для обработки.
     */
    private void writeInternalInfo(String objectId, String classId, String typeId, String valueId, String name) throws XMLStreamException {
        startElement("InternalInfo");
        
        // ContainedObject
        startElement("xr:ContainedObject");
        writeElement("xr:ClassId", classId);
        writeElement("xr:ObjectId", objectId);
        endElement(); // xr:ContainedObject
        
        // GeneratedType
        writer.writeCharacters("\t\t");
        writer.writeStartElement("xr:GeneratedType");
        writer.writeAttribute("name", "ExternalDataProcessorObject." + name);
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
        Path epfDir = outputDir.resolve("src/ExternalDataProcessors").resolve(epfName);
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
        // EDT: Templates/<TemplateName>/Template.<ext>
        Path epfDir = outputDir.resolve("src/ExternalDataProcessors").resolve(epfName);
        Path templatesDir = epfDir.resolve("Templates");
        Path templatePath = EdtLayout.createTemplateStructure(templatesDir, templateName);
        
        // Определить расширение для EDT
        String edtExtension = getEdtTemplateExtension(templateType);
        Path templateBodyPath = templatePath.getParent().resolve("Template." + edtExtension);
        createTemplateBody(templateBodyPath, templateType, templateName);
        
        System.out.println("Added template (EDT): " + templateName);
        System.out.println("  Body: " + templateBodyPath);
    }
    
    /**
     * Получить расширение файла макета для EDT.
     */
    private String getEdtTemplateExtension(String templateType) {
        TemplateType tt = TemplateType.valueByName(templateType);
        if (tt == TemplateType.SPREADSHEET_DOCUMENT) return "mxlx";
        if (tt == TemplateType.HTML_DOCUMENT) return "html";
        if (tt == TemplateType.TEXT_DOCUMENT) return "txt";
        if (tt == TemplateType.BINARY_DATA) return "bin";
        return "xml";
    }
}

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Универсальный редактор ChildObjects для любого типа метаданных 1С.
 * <p>
 * Работает с корневым XML-файлом объекта (EPF/ERF/Catalog/Document/etc.)
 * и позволяет добавлять/удалять формы, макеты и другие дочерние объекты.
 * <p>
 * Использует строковые операции для сохранения оригинального форматирования XML.
 */
public class ObjectContainerEditor {

    private static final Pattern CHILD_OBJECTS_SELF_CLOSING = Pattern.compile("([ \t]*)<ChildObjects/>");
    private static final Pattern CHILD_OBJECTS_END = Pattern.compile("([ \t]*)</ChildObjects>");
    private static final Pattern FORM_ENTRY = Pattern.compile("\\s*<Form>[^<]+</Form>\\s*");
    private static final Pattern TEMPLATE_ENTRY = Pattern.compile("\\s*<Template>[^<]+</Template>\\s*");
    private static final Pattern FIRST_TEMPLATE = Pattern.compile("<Template>");
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Path objectXmlPath;
    private String content;
    private boolean hasBom;

    /**
     * @param objectXmlPath путь к корневому XML объекта
     */
    public ObjectContainerEditor(Path objectXmlPath) throws IOException {
        this.objectXmlPath = objectXmlPath;
        byte[] raw = Files.readAllBytes(objectXmlPath);
        this.hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        this.content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Добавить форму в ChildObjects.
     * Формы вставляются ДО первого Template (если есть).
     *
     * @param formName имя формы
     */
    public void addForm(String formName) {
        expandSelfClosingChildObjects();
        String entry = "\t\t<Form>" + escapeXml(formName) + "</Form>\n";

        // Insert before first <Template> if exists
        Matcher tplMatcher = FIRST_TEMPLATE.matcher(content);
        if (tplMatcher.find()) {
            int pos = content.lastIndexOf('\n', tplMatcher.start());
            if (pos >= 0) {
                content = content.substring(0, pos + 1) + entry + content.substring(pos + 1);
            } else {
                content = content.replace("</ChildObjects>", entry + "\t</ChildObjects>");
            }
        } else {
            content = content.replace("</ChildObjects>", entry + "\t</ChildObjects>");
        }
    }

    /**
     * Удалить форму из ChildObjects.
     *
     * @param formName имя формы
     * @return true если форма была найдена и удалена
     */
    public boolean removeForm(String formName) {
        String pattern = "<Form>" + Pattern.quote(formName) + "</Form>";
        Pattern p = Pattern.compile("[ \t]*" + pattern + "[ \t]*\\r?\\n?");
        Matcher m = p.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("");
            return true;
        }
        return false;
    }

    /**
     * Добавить макет в ChildObjects (в конец).
     *
     * @param templateName имя макета
     */
    public void addTemplate(String templateName) {
        expandSelfClosingChildObjects();
        String entry = "\t\t<Template>" + escapeXml(templateName) + "</Template>\n";
        content = content.replace("</ChildObjects>", entry + "\t</ChildObjects>");
    }

    /**
     * Удалить макет из ChildObjects.
     *
     * @param templateName имя макета
     * @return true если макет был найден и удалён
     */
    public boolean removeTemplate(String templateName) {
        String pattern = "<Template>" + Pattern.quote(templateName) + "</Template>";
        Pattern p = Pattern.compile("[ \t]*" + pattern + "[ \t]*\\r?\\n?");
        Matcher m = p.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("");
            return true;
        }
        return false;
    }

    /**
     * Установить DefaultForm.
     *
     * @param defaultFormValue полное значение DefaultForm (напр. "ExternalDataProcessor.Name.Form.FormName")
     */
    public void setDefaultForm(String defaultFormValue) {
        // Try to update existing empty DefaultForm
        if (content.contains("<DefaultForm></DefaultForm>")) {
            content = content.replace("<DefaultForm></DefaultForm>",
                    "<DefaultForm>" + defaultFormValue + "</DefaultForm>");
        } else if (content.contains("<DefaultForm/>")) {
            content = content.replace("<DefaultForm/>",
                    "<DefaultForm>" + defaultFormValue + "</DefaultForm>");
        }
    }

    /**
     * Очистить DefaultForm если она ссылается на указанную форму.
     *
     * @param formName имя формы
     */
    public void clearDefaultFormIfMatches(String formName) {
        String pattern = "<DefaultForm>[^<]*\\.Form\\." + Pattern.quote(formName) + "</DefaultForm>";
        content = content.replaceAll(pattern, "<DefaultForm></DefaultForm>");
    }

    /**
     * Определить тип корневого элемента метаданных.
     *
     * @return тип объекта (ExternalDataProcessor, ExternalReport, Catalog, Document и т.д.)
     */
    public String detectObjectType() {
        // Inside MetaDataObject wrapper
        Pattern p = Pattern.compile("<MetaDataObject[^>]*>[\\s]*<(\\w+)");
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(1);

        // Direct root
        Pattern directRoot = Pattern.compile("<(ExternalDataProcessor|ExternalReport|Catalog|Document|" +
                "InformationRegister|AccumulationRegister|DataProcessor|Report|CommonForm|" +
                "ChartOfCharacteristicTypes|ChartOfAccounts|BusinessProcess|Task)\\b");
        m = directRoot.matcher(content);
        if (m.find()) return m.group(1);

        return "Unknown";
    }

    /**
     * Получить имя объекта из Properties/Name.
     */
    public String getObjectName() {
        Pattern p = Pattern.compile("<Name>([^<]+)</Name>");
        Matcher m = p.matcher(content);
        if (m.find()) return m.group(1);
        return null;
    }

    /**
     * Проверить, есть ли форма с таким именем в ChildObjects.
     */
    public boolean hasForm(String formName) {
        return content.contains("<Form>" + formName + "</Form>");
    }

    /**
     * Проверить, есть ли макет с таким именем в ChildObjects.
     */
    public boolean hasTemplate(String templateName) {
        return content.contains("<Template>" + templateName + "</Template>");
    }

    /**
     * Сохранить изменения.
     */
    public void save() throws IOException {
        if (hasBom) {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] result = new byte[BOM.length + contentBytes.length];
            System.arraycopy(BOM, 0, result, 0, BOM.length);
            System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
            Files.write(objectXmlPath, result);
        } else {
            Files.writeString(objectXmlPath, content, StandardCharsets.UTF_8);
        }
    }

    /**
     * Создать scaffold формы (Designer формат).
     * <p>
     * Создаёт: Forms/<formName>.xml (метаданные) + Forms/<formName>/Ext/Form.xml + Module.bsl
     *
     * @param baseDir    каталог объекта (где лежат Forms/)
     * @param formName   имя формы
     * @param synonym    синоним (null = formName)
     * @param objectType тип объекта для GeneratedType
     * @param objectName имя объекта
     */
    public static void createFormScaffold(Path baseDir, String formName, String synonym,
                                          String objectType, String objectName) throws IOException {
        Path formsDir = baseDir.resolve("Forms");
        Files.createDirectories(formsDir);

        String formUuid = UuidGenerator.generate();
        String syn = synonym != null ? synonym : formName;
        String generatedType = objectType + "Object." + objectName;

        // 1. Form metadata: Forms/<formName>.xml
        String metaXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n"
                + "\t<Form uuid=\"" + formUuid + "\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + escapeXml(formName) + "</Name>\n"
                + "\t\t\t<Synonym>\n"
                + "\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t<v8:content>" + escapeXml(syn) + "</v8:content>\n"
                + "\t\t\t\t</v8:item>\n"
                + "\t\t\t</Synonym>\n"
                + "\t\t\t<Comment></Comment>\n"
                + "\t\t\t<FormType>Managed</FormType>\n"
                + "\t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>\n"
                + "\t\t</Properties>\n"
                + "\t</Form>\n"
                + "</MetaDataObject>\n";
        writeWithBom(formsDir.resolve(formName + ".xml"), metaXml);

        // 2. Form definition: Forms/<formName>/Ext/Form.xml
        Path formExtDir = formsDir.resolve(formName).resolve("Ext");
        Files.createDirectories(formExtDir);

        String formXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"\n"
                + "\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n"
                + "\txmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\"\n"
                + "\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
                + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<AutoCommandBar>\n"
                + "\t\t<name>АвтоКоманднаяПанель</name>\n"
                + "\t</AutoCommandBar>\n"
                + "\t<Events>\n"
                + "\t\t<Event name=\"OnCreateAtServer\">ПриСозданииНаСервере</Event>\n"
                + "\t</Events>\n"
                + "\t<Attributes>\n"
                + "\t\t<Attribute name=\"Объект\">\n"
                + "\t\t\t<MainAttribute>true</MainAttribute>\n"
                + "\t\t\t<Type>\n"
                + "\t\t\t\t<v8:Type>cfg:" + generatedType + "</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t</Attribute>\n"
                + "\t</Attributes>\n"
                + "</Form>\n";
        writeWithBom(formExtDir.resolve("Form.xml"), formXml);

        // 3. Module: Forms/<formName>/Ext/Form/Module.bsl
        Path moduleDir = formExtDir.resolve("Form");
        Files.createDirectories(moduleDir);
        Files.writeString(moduleDir.resolve("Module.bsl"),
                "\uFEFF#Область ОбработчикиСобытийФормы\n\n"
                        + "&НаСервере\n"
                        + "Процедура ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)\n"
                        + "\t\n"
                        + "КонецПроцедуры\n\n"
                        + "#КонецОбласти\n",
                StandardCharsets.UTF_8);
    }

    /**
     * Создать scaffold макета (Designer формат).
     *
     * @param baseDir      каталог объекта
     * @param templateName имя макета
     * @param synonym      синоним
     * @param templateType тип (SpreadsheetDocument, HTMLDocument, TextDocument, BinaryData, DataCompositionSchema)
     */
    public static void createTemplateScaffold(Path baseDir, String templateName, String synonym,
                                              String templateType) throws IOException {
        Path templatesDir = baseDir.resolve("Templates");
        Files.createDirectories(templatesDir);

        String uuid = UuidGenerator.generate();
        String syn = synonym != null ? synonym : templateName;

        // 1. Template metadata
        String metaXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n"
                + "\t<Template uuid=\"" + uuid + "\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + escapeXml(templateName) + "</Name>\n"
                + "\t\t\t<Synonym>\n"
                + "\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t<v8:content>" + escapeXml(syn) + "</v8:content>\n"
                + "\t\t\t\t</v8:item>\n"
                + "\t\t\t</Synonym>\n"
                + "\t\t\t<Comment></Comment>\n"
                + "\t\t\t<TemplateType>" + templateType + "</TemplateType>\n"
                + "\t\t</Properties>\n"
                + "\t</Template>\n"
                + "</MetaDataObject>\n";
        writeWithBom(templatesDir.resolve(templateName + ".xml"), metaXml);

        // 2. Template body
        Path extDir = templatesDir.resolve(templateName).resolve("Ext");
        Files.createDirectories(extDir);

        String ext = getExtension(templateType);
        Path bodyPath = extDir.resolve("Template." + ext);
        String body = getTemplateBody(templateType);
        Files.writeString(bodyPath, body, StandardCharsets.UTF_8);
    }

    /**
     * Создать scaffold справки (Help.xml + HTML).
     *
     * @param baseDir каталог объекта
     * @param lang    язык (по умолчанию "ru")
     */
    public static void createHelpScaffold(Path baseDir, String lang) throws IOException {
        Path extDir = baseDir.resolve("Ext");
        Files.createDirectories(extDir);

        // Help.xml
        String helpXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Help xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n"
                + "\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
                + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                + "\tversion=\"2.17\">\n"
                + "\t<Page>" + escapeXml(lang) + "</Page>\n"
                + "</Help>\n";
        writeWithBom(extDir.resolve("Help.xml"), helpXml);

        // HTML
        Path helpDir = extDir.resolve("Help");
        Files.createDirectories(helpDir);
        String html = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">\n"
                + "<html>\n"
                + "<head>\n"
                + "\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n"
                + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"v8help://service_book/service_style\"/>\n"
                + "</head>\n"
                + "<body>\n"
                + "\t<h1>Справка</h1>\n"
                + "\t<p>Описание</p>\n"
                + "</body>\n"
                + "</html>\n";
        Files.writeString(helpDir.resolve(lang + ".html"), html, StandardCharsets.UTF_8);
    }

    /**
     * Проверить, нет ли уже форм в ChildObjects (для автоматического DefaultForm).
     */
    public boolean hasAnyForm() {
        return FORM_ENTRY.matcher(content).find();
    }

    /**
     * Обновить MainDataCompositionSchema если пусто.
     */
    public void setMainDataCompositionSchemaIfEmpty(String value) {
        if (content.contains("<MainDataCompositionSchema></MainDataCompositionSchema>")) {
            content = content.replace("<MainDataCompositionSchema></MainDataCompositionSchema>",
                    "<MainDataCompositionSchema>" + escapeXml(value) + "</MainDataCompositionSchema>");
        } else if (content.contains("<MainDataCompositionSchema/>")) {
            content = content.replace("<MainDataCompositionSchema/>",
                    "<MainDataCompositionSchema>" + escapeXml(value) + "</MainDataCompositionSchema>");
        }
    }

    /**
     * Принудительно установить MainDataCompositionSchema (перезаписывает существующее значение).
     *
     * @param value новое значение (напр. "Report.ОстаткиТоваров.Template.ОсновнаяСхема")
     */
    public void setMainDataCompositionSchema(String value) {
        // Replace existing value (empty, self-closing, or non-empty)
        content = content.replaceAll(
                "<MainDataCompositionSchema>[^<]*</MainDataCompositionSchema>",
                "<MainDataCompositionSchema>" + escapeXml(value) + "</MainDataCompositionSchema>");
        content = content.replace(
                "<MainDataCompositionSchema/>",
                "<MainDataCompositionSchema>" + escapeXml(value) + "</MainDataCompositionSchema>");
    }

    /**
     * Очистить MainDataCompositionSchema если она ссылается на указанный макет.
     *
     * @param value полное значение, которое надо очистить
     */
    public void clearMainDataCompositionSchemaIfMatches(String value) {
        String escaped = escapeXml(value);
        String pattern = "<MainDataCompositionSchema>" + Pattern.quote(escaped) + "</MainDataCompositionSchema>";
        content = content.replaceAll(pattern,
                "<MainDataCompositionSchema></MainDataCompositionSchema>");
    }

    // --- Utility ---

    /**
     * Раскрыть самозакрывающийся &lt;ChildObjects/&gt; в парный тег.
     */
    private void expandSelfClosingChildObjects() {
        Matcher m = CHILD_OBJECTS_SELF_CLOSING.matcher(content);
        if (m.find()) {
            String indent = m.group(1);
            content = m.replaceFirst(indent + "<ChildObjects>\n" + indent + "</ChildObjects>");
        }
    }

    /**
     * Экранирование спецсимволов XML в пользовательских строках.
     */
    static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void writeWithBom(Path path, String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
        Files.write(path, result);
    }

    private static String getExtension(String templateType) {
        switch (templateType) {
            case "HTMLDocument": return "html";
            case "TextDocument": return "txt";
            case "BinaryData": return "bin";
            default: return "xml"; // SpreadsheetDocument, DataCompositionSchema
        }
    }

    private static String getTemplateBody(String templateType) {
        switch (templateType) {
            case "SpreadsheetDocument":
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">\n"
                        + "\t<columns>\n"
                        + "\t\t<size>1</size>\n"
                        + "\t</columns>\n"
                        + "\t<height>0</height>\n"
                        + "</document>\n";
            case "DataCompositionSchema":
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n"
                        + "\t<dataSource>\n"
                        + "\t\t<name>ИсточникДанных1</name>\n"
                        + "\t\t<dataSourceType>Local</dataSourceType>\n"
                        + "\t</dataSource>\n"
                        + "</DataCompositionSchema>\n";
            case "HTMLDocument":
                return "<!DOCTYPE html>\n<html>\n<head>\n\t<meta charset=\"UTF-8\">\n</head>\n<body>\n</body>\n</html>\n";
            case "TextDocument":
                return "";
            case "BinaryData":
                return "";
            default:
                return "";
        }
    }
}

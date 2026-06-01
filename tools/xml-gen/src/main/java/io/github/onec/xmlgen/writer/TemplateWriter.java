package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import io.github.onec.xmlgen.editor.ObjectContainerEditor;
import io.github.onec.xmlgen.model.MdoPath;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Универсальные операции с макетами для любых объектов метаданных 1С.
 *
 * <p>Поддерживаемые типы объектов: Catalog, Document, Report, DataProcessor,
 * InformationRegister, AccumulationRegister, AccountingRegister,
 * CalculationRegister, ChartOfCharacteristicTypes, ChartOfAccounts,
 * ChartOfCalculationTypes, BusinessProcess, Task, ExchangePlan.
 */
public class TemplateWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Добавить макет к объекту метаданных.
     *
     * @param configDir   корневой каталог конфигурации
     * @param object      путь объекта (например, {@code Document.ЗаказКлиента})
     * @param name        имя макета
     * @param typeStr     тип макета (SpreadsheetDocument, HTMLDocument, TextDocument, BinaryData, DataCompositionSchema)
     * @param synonym     синоним (null — имя)
     * @param setMainDcs  принудительно установить MainDataCompositionSchema (только для Report)
     * @param srcDir      подкаталог исходников внутри configDir (по умолчанию «src»)
     */
    public void addTemplate(Path configDir, MdoPath object, String name, String typeStr,
                            String synonym, boolean setMainDcs, String srcDir) throws IOException {

        Path src = resolveSrcDir(configDir, srcDir);
        Path objectXml = src.resolve(object.getObjectXmlRelPath());
        validateObjectExists(objectXml, object);

        TemplateType templateType = parseTemplateType(typeStr);

        // Warning for SpreadsheetDocument without ПФ_ prefix
        if (templateType == TemplateType.SPREADSHEET_DOCUMENT && !name.startsWith("ПФ_")) {
            System.err.println("[WARN] Template name '" + name
                    + "' does not start with 'ПФ_'. For print forms, the prefix ПФ_ is recommended.");
        }

        ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);

        // Check if template already exists
        if (editor.hasTemplate(name)) {
            throw new IllegalArgumentException("Template '" + name + "' already exists in ChildObjects of "
                    + objectXml);
        }

        // Create the template scaffold under <src>/<Type>/<Name>/
        Path baseDir = src.resolve(object.getRelativeDir());
        ObjectContainerEditor.createTemplateScaffold(baseDir, name, synonym, typeStr);

        // Register in ChildObjects
        editor.addTemplate(name);

        // Handle MainDataCompositionSchema for Report
        if (templateType == TemplateType.DATA_COMPOSITION_SCHEME && object.isReport()) {
            // TASK-171 D6: префикс берём из фактического типа объекта (Report / ExternalReport),
            // а не хардкодим "Report." — иначе для внешнего отчёта ссылка будет битой.
            String mainDcs = mainDcsValue(object, name);
            if (setMainDcs) {
                editor.setMainDataCompositionSchema(mainDcs);
            } else {
                // Only set if currently empty
                editor.setMainDataCompositionSchemaIfEmpty(mainDcs);
            }
        }

        editor.save();

        System.out.println("Added template: " + name + " (" + typeStr + ") to " + object);
        System.out.println("  Metadata: " + baseDir.resolve("Templates").resolve(name + ".xml"));
    }

    /**
     * Удалить макет объекта метаданных.
     *
     * @param configDir корневой каталог конфигурации
     * @param object    путь объекта
     * @param name      имя удаляемого макета
     * @param srcDir    подкаталог исходников
     */
    public void removeTemplate(Path configDir, MdoPath object, String name, String srcDir) throws IOException {
        Path src = resolveSrcDir(configDir, srcDir);
        Path objectXml = src.resolve(object.getObjectXmlRelPath());
        validateObjectExists(objectXml, object);

        ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);

        if (!editor.hasTemplate(name)) {
            System.err.println("[WARN] Template '" + name + "' not found in ChildObjects of " + objectXml
                    + " — skipping (noop).");
            return;
        }

        // Remove from ChildObjects
        editor.removeTemplate(name);

        // If this was the MainDataCompositionSchema — clear it
        if (object.isReport()) {
            // TASK-171 D6: префикс из типа объекта, согласованно с addTemplate.
            editor.clearMainDataCompositionSchemaIfMatches(mainDcsValue(object, name));
        }

        editor.save();

        // Delete files
        Path baseDir = src.resolve(object.getRelativeDir());
        Path tplMeta = baseDir.resolve("Templates").resolve(name + ".xml");
        Path tplDir = baseDir.resolve("Templates").resolve(name);

        if (Files.exists(tplMeta)) Files.delete(tplMeta);
        if (Files.exists(tplDir)) {
            try (Stream<Path> walk = Files.walk(tplDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }
        }

        System.out.println("Removed template: " + name + " from " + object);
    }

    /**
     * Добавить встроенную справку к объекту метаданных.
     *
     * @param configDir корневой каталог конфигурации
     * @param object    путь объекта
     * @param lang      язык (например, «ru»)
     * @param srcDir    подкаталог исходников
     */
    public void addHelp(Path configDir, MdoPath object, String lang, String srcDir) throws IOException {
        Path src = resolveSrcDir(configDir, srcDir);
        Path objectXml = src.resolve(object.getObjectXmlRelPath());
        validateObjectExists(objectXml, object);

        Path baseDir = src.resolve(object.getRelativeDir());
        Path extDir = baseDir.resolve("Ext");
        Path helpDir = extDir.resolve("Help");
        Path htmlFile = helpDir.resolve(lang + ".html");

        // Idempotent: don't overwrite existing HTML file
        if (Files.exists(htmlFile)) {
            System.err.println("[WARN] Help file '" + htmlFile + "' already exists — skipping (idempotent).");
            return;
        }

        Files.createDirectories(helpDir);

        // Create Help.xml (only if not exists or add lang)
        Path helpXmlPath = extDir.resolve("Help.xml");
        if (!Files.exists(helpXmlPath)) {
            String helpXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Help xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n"
                    + "\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
                    + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                    + "\tversion=\"2.17\">\n"
                    + "\t<Page>" + escapeXml(lang) + "</Page>\n"
                    + "</Help>\n";
            writeWithBom(helpXmlPath, helpXml);
        } else {
            // Append <Page> entry if not already present
            addHelpPage(helpXmlPath, lang);
        }

        // Create HTML file
        String html = "<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.0 Transitional//EN\">\n"
                + "<html>\n"
                + "<head>\n"
                + "\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"/>\n"
                + "\t<link rel=\"stylesheet\" type=\"text/css\" href=\"v8help://service_book/service_style\"/>\n"
                + "</head>\n"
                + "<body>\n"
                + "\t<h1>Справка</h1>\n"
                + "\t<p>Описание объекта.</p>\n"
                + "</body>\n"
                + "</html>\n";
        Files.writeString(htmlFile, html, StandardCharsets.UTF_8);

        // Add IncludeHelpInContents to forms if they exist
        addIncludeHelpInContentsToForms(baseDir);

        System.out.println("Added help for: " + object);
        System.out.println("  Help.xml: " + helpXmlPath);
        System.out.println("  HTML: " + htmlFile);
    }

    // ===== package-private helpers for testing =====

    static TemplateType parseTemplateType(String typeStr) {
        return TemplateType.valueByName(canonicalTemplateTypeName(typeStr));
    }

    /**
     * Привести тип макета (с учётом алиасов) к канонической строке 1С.
     * <p>TASK-171: вынесено отдельно от {@link #parseTemplateType}, т.к. EpfWriter нужна именно
     * каноническая <b>строка</b> (для {@code <TemplateType>} и для выбора тела/расширения через
     * {@code ObjectContainerEditor.getTemplateBody}/{@code getExtension}), а у {@link TemplateType}
     * нет публичного метода вернуть это имя. Единый источник нормализации для обеих веток (W5).
     *
     * @return каноническое имя типа (e.g. {@code SpreadsheetDocument}, {@code DataCompositionSchema})
     * @throws IllegalArgumentException если тип пустой или неизвестен
     */
    static String canonicalTemplateTypeName(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            throw new IllegalArgumentException("--type is required");
        }
        // Normalize aliases
        switch (typeStr.toLowerCase()) {
            case "html":
            case "htmldocument":
                typeStr = "HTMLDocument";
                break;
            case "text":
            case "txt":
            case "textdocument":
                typeStr = "TextDocument";
                break;
            case "spreadsheetdocument":
            case "mxl":
            case "табличныйдокумент":
                typeStr = "SpreadsheetDocument";
                break;
            case "binarydata":
            case "bin":
            case "двоичные":
            case "двоичныеданные":
                typeStr = "BinaryData";
                break;
            case "datacompositionschema":
            case "скд":
            case "схемакомпоновкиданных":
                typeStr = "DataCompositionSchema";
                break;
        }
        TemplateType tt = TemplateType.valueByName(typeStr);
        if (tt == TemplateType.UNKNOWN) {
            throw new IllegalArgumentException("Unknown template type: '" + typeStr
                    + "'. Supported: HTMLDocument, TextDocument, SpreadsheetDocument, BinaryData, DataCompositionSchema");
        }
        return typeStr;
    }

    // ===== private helpers =====

    /**
     * Значение MainDataCompositionSchema: префикс из фактического типа объекта.
     * TASK-171 D6: для конфиг-отчёта — {@code Report.}, для внешнего — {@code ExternalReport.}
     * (хотя сейчас MdoPath допускает только Report; делаем устойчиво к расширению).
     */
    private static String mainDcsValue(MdoPath object, String templateName) {
        return object.getType() + "." + object.getName() + ".Template." + templateName;
    }

    private Path resolveSrcDir(Path configDir, String srcDir) {
        String dir = (srcDir != null && !srcDir.isBlank()) ? srcDir : "src";
        return configDir.resolve(dir);
    }

    private void validateObjectExists(Path objectXml, MdoPath object) {
        if (!Files.exists(objectXml)) {
            throw new IllegalArgumentException(
                    "Object '" + object + "' not found. Expected XML at: " + objectXml.toAbsolutePath());
        }
    }

    private void addHelpPage(Path helpXmlPath, String lang) throws IOException {
        byte[] raw = Files.readAllBytes(helpXmlPath);
        boolean hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        String content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);

        String pageEntry = "<Page>" + escapeXml(lang) + "</Page>";
        if (content.contains(pageEntry)) {
            return; // already present
        }

        String newEntry = "\t<Page>" + escapeXml(lang) + "</Page>\n";
        content = content.replace("</Help>", newEntry + "</Help>");

        if (hasBom) {
            writeWithBom(helpXmlPath, content);
        } else {
            Files.writeString(helpXmlPath, content, StandardCharsets.UTF_8);
        }
    }

    private void addIncludeHelpInContentsToForms(Path baseDir) throws IOException {
        Path formsDir = baseDir.resolve("Forms");
        if (!Files.isDirectory(formsDir)) {
            return;
        }

        // Find all *FormName*.xml (metadata files directly under Forms/)
        try (Stream<Path> stream = Files.list(formsDir)) {
            List<Path> formMetaFiles = stream
                    .filter(p -> p.toString().endsWith(".xml"))
                    .filter(Files::isRegularFile)
                    .toList();

            for (Path formMeta : formMetaFiles) {
                addIncludeHelpInContents(formMeta);
            }
        }
    }

    /**
     * Добавить {@code <IncludeHelpInContents>false</IncludeHelpInContents>} в метаданные формы
     * если отсутствует.
     */
    static void addIncludeHelpInContents(Path formXmlPath) throws IOException {
        byte[] raw = Files.readAllBytes(formXmlPath);
        boolean hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        String content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);

        if (content.contains("IncludeHelpInContents")) {
            return; // already present
        }

        // Insert after <FormType> or <Comment> in Properties
        String marker = "</Properties>";
        if (!content.contains(marker)) {
            return; // not a form metadata file we understand
        }
        String insertion = "\t\t\t<IncludeHelpInContents>false</IncludeHelpInContents>\n";
        content = content.replace(marker, insertion + marker);

        if (hasBom) {
            writeWithBom(formXmlPath, content);
        } else {
            Files.writeString(formXmlPath, content, StandardCharsets.UTF_8);
        }
    }

    private static void writeWithBom(Path path, String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
        Files.write(path, result);
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

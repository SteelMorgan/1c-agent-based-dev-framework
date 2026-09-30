package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import io.github.onec.xmlgen.editor.ConfigEditor;
import io.github.onec.xmlgen.editor.ObjectContainerEditor;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.MdoPath;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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

    //++agent TASK-174.XG-105 [14.07.2026 12:48:00]
    @FunctionalInterface
    interface ConfigEditorFactory {
        ConfigEditor create(Path path) throws IOException;
    }

    private final ConfigEditorFactory configEditorFactory;

    public TemplateWriter() {
        this(ConfigEditor::new);
    }

    TemplateWriter(ConfigEditorFactory configEditorFactory) {
        this.configEditorFactory = Objects.requireNonNull(configEditorFactory, "configEditorFactory");
    }
    //++agent TASK-174.XG-105

    //++agent TASK-174.XG-105 [14.07.2026 12:10:00]
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

        String canonicalType = canonicalTemplateTypeName(typeStr);
        if (setMainDcs && !object.isReport()) {
            throw new IllegalArgumentException("--set-main-dcs is only valid with Report objects");
        }
        if (setMainDcs && !"DataCompositionSchema".equals(canonicalType)) {
            throw new IllegalArgumentException("--set-main-dcs requires --type DataCompositionSchema");
        }

        // Warning for SpreadsheetDocument without ПФ_ prefix
        if ("SpreadsheetDocument".equals(canonicalType) && !name.startsWith("ПФ_")) {
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
        Path templateMeta = baseDir.resolve("Templates").resolve(name + ".xml");
        Path templateDir = baseDir.resolve("Templates").resolve(name);
        if (Files.exists(templateMeta) || Files.exists(templateDir)) {
            throw new IllegalArgumentException("Template '" + name
                    + "' already exists on disk in " + baseDir.resolve("Templates"));
        }
        String formatVersion = ConfigurationXmlReader.readFormatVersion(objectXml);
        ObjectContainerEditor.createTemplateScaffold(baseDir, name, synonym, canonicalType, formatVersion);

        // Register in ChildObjects
        editor.addTemplate(name);

        // Handle MainDataCompositionSchema for Report
        if ("DataCompositionSchema".equals(canonicalType) && object.isReport()) {
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
     * Добавить общий макет непосредственно в корень конфигурации.
     */
    public void addCommonTemplate(Path configurationXml, String name, String typeStr,
                                  String synonym, Path input) throws IOException {
        String canonicalType = canonicalTemplateTypeName(typeStr);
        if ("BinaryData".equals(canonicalType)) {
            if (input == null || !Files.isRegularFile(input)) {
                throw new IllegalArgumentException("--input must reference an existing regular file "
                        + "for BinaryData CommonTemplate");
            }
        } else if (input != null) {
            throw new IllegalArgumentException("--input is only valid for BinaryData CommonTemplate");
        }
        Path configDir = configurationXml.toAbsolutePath().normalize().getParent();
        if (configDir == null || !Files.isRegularFile(configurationXml)) {
            throw new IllegalArgumentException("Configuration.xml not found: " + configurationXml);
        }

        ConfigEditor editor = configEditorFactory.create(configurationXml);
        if (editor.hasChildObject("CommonTemplate", name)) {
            throw new IllegalArgumentException("CommonTemplate '" + name + "' already exists in ChildObjects");
        }

        Path commonTemplates = configDir.resolve("CommonTemplates");
        Path targetMeta = commonTemplates.resolve(name + ".xml");
        Path targetDir = commonTemplates.resolve(name);
        if (Files.exists(targetMeta) || Files.exists(targetDir)) {
            throw new IllegalArgumentException("CommonTemplate '" + name
                    + "' already exists on disk in " + commonTemplates);
        }

        byte[] originalConfiguration = Files.readAllBytes(configurationXml);
        boolean commonTemplatesExisted = Files.exists(commonTemplates);
        Path staging = Files.createTempDirectory(configDir, ".xml-gen-common-template-");
        boolean metaMoved = false;
        boolean dirMoved = false;
        try {
            String formatVersion = ConfigurationXmlReader.readFormatVersion(configurationXml);
            ObjectContainerEditor.createCommonTemplateScaffold(staging, name, synonym,
                    canonicalType, formatVersion);
            if (input != null) {
                Files.copy(input, staging.resolve("CommonTemplates").resolve(name)
                                .resolve("Ext").resolve("Template.bin"),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            editor.setSkipFileCheck(true);
            editor.addChildObject("CommonTemplate." + name);

            Files.createDirectories(commonTemplates);
            move(staging.resolve("CommonTemplates").resolve(name + ".xml"), targetMeta);
            metaMoved = true;
            move(staging.resolve("CommonTemplates").resolve(name), targetDir);
            dirMoved = true;
            editor.save();
        } catch (Exception failure) {
            boolean restored = restoreConfiguration(failure, configurationXml, originalConfiguration);
            if (dirMoved) {
                restored &= attemptCompensation(failure, "return CommonTemplate body to staging",
                        () -> move(targetDir, staging.resolve("CommonTemplates").resolve(name)));
            }
            if (metaMoved) {
                restored &= attemptCompensation(failure, "return CommonTemplate metadata to staging",
                        () -> move(targetMeta, staging.resolve("CommonTemplates").resolve(name + ".xml")));
            }
            if (!commonTemplatesExisted) {
                restored &= attemptCompensation(failure, "remove newly-created CommonTemplates directory",
                        () -> removeDirectoryIfEmpty(commonTemplates));
            }
            restored &= verifyAddRestored(failure, configurationXml, originalConfiguration,
                    targetMeta, targetDir, commonTemplates, commonTemplatesExisted);
            finishCompensation(failure, staging, restored);
            throw failure;
        }
        deleteRecursively(staging);

        System.out.println("Added common template: " + name + " (" + canonicalType + ")");
        System.out.println("  Metadata: " + targetMeta);
    }

    /**
     * Удалить общий макет конфигурации; повторный вызов является no-op.
     */
    public void removeCommonTemplate(Path configurationXml, String name) throws IOException {
        Path configDir = configurationXml.toAbsolutePath().normalize().getParent();
        if (configDir == null || !Files.isRegularFile(configurationXml)) {
            throw new IllegalArgumentException("Configuration.xml not found: " + configurationXml);
        }

        ConfigEditor editor = configEditorFactory.create(configurationXml);
        Path commonTemplates = configDir.resolve("CommonTemplates");
        Path targetMeta = commonTemplates.resolve(name + ".xml");
        Path targetDir = commonTemplates.resolve(name);
        boolean registered = editor.hasChildObject("CommonTemplate", name);
        if (!registered && !Files.exists(targetMeta) && !Files.exists(targetDir)) {
            System.out.println("[NO-OP] CommonTemplate '" + name + "' is already absent");
            return;
        }
        if (!registered) {
            throw new IllegalArgumentException("CommonTemplate '" + name
                    + "' exists on disk but is not registered in ChildObjects");
        }

        byte[] originalConfiguration = Files.readAllBytes(configurationXml);
        Path staging = Files.createTempDirectory(configDir, ".xml-gen-common-template-remove-");
        Path stagedMeta = staging.resolve(name + ".xml");
        Path stagedDir = staging.resolve(name);
        boolean metaMoved = false;
        boolean dirMoved = false;
        try {
            if (Files.exists(targetMeta)) {
                move(targetMeta, stagedMeta);
                metaMoved = true;
            }
            if (Files.exists(targetDir)) {
                move(targetDir, stagedDir);
                dirMoved = true;
            }
            editor.removeChildObject("CommonTemplate." + name);
            editor.save();
        } catch (Exception failure) {
            boolean restored = restoreConfiguration(failure, configurationXml, originalConfiguration);
            if (metaMoved) {
                restored &= attemptCompensation(failure, "restore CommonTemplate metadata",
                        () -> move(stagedMeta, targetMeta));
            }
            if (dirMoved) {
                restored &= attemptCompensation(failure, "restore CommonTemplate body",
                        () -> move(stagedDir, targetDir));
            }
            restored &= verifyRemoveRestored(failure, configurationXml, originalConfiguration,
                    targetMeta, targetDir, stagedMeta, stagedDir, metaMoved, dirMoved);
            finishCompensation(failure, staging, restored);
            throw failure;
        }
        deleteRecursively(staging);
        removeDirectoryIfEmpty(commonTemplates);
        System.out.println("Removed common template: " + name);
    }
    //++agent TASK-174.XG-105

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
            String formatVersion = ConfigurationXmlReader.readFormatVersion(objectXml);
            String helpXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Help xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n"
                    + "\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n"
                    + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                    + "\tversion=\"" + escapeXml(formatVersion) + "\">\n"
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
        //++agent TASK-172 [02.06.2026 07:28:00]
        // Канон Designer (_Демо) — CRLF. Нормализуем переводы строк html-справки.
        // BOM-политику html не меняем (вне scope TASK-172: .xml/.bsl/Template.xml).
        Files.writeString(htmlFile, io.github.onec.xmlgen.io.Crlf.normalize(html), StandardCharsets.UTF_8);
        //++agent TASK-172

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
    //**agent TASK-174.XG-105 [14.07.2026 12:10:00]
    public static String canonicalTemplateTypeName(String typeStr) {
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
            case "binary":
            case "bin":
            case "двоичные":
            case "двоичныеданные":
                typeStr = "BinaryData";
                break;
            case "addin":
            case "внешняякомпонента":
                typeStr = "AddIn";
                break;
            case "datacompositionappearancetemplate":
            case "макетоформлениякомпоновкиданных":
                typeStr = "DataCompositionAppearanceTemplate";
                break;
            case "graphicalschema":
            case "графическаясхема":
                typeStr = "GraphicalSchema";
                break;
            case "activedocument":
                typeStr = "ActiveDocument";
                break;
            case "geographicalschema":
            case "географическаясхема":
                typeStr = "GeographicalSchema";
                break;
            case "datacompositionschema":
            case "скд":
            case "схемакомпоновкиданных":
                typeStr = "DataCompositionSchema";
                break;
            case "help":
            case "справка":
                typeStr = "Help";
                break;
        }
        if ("Help".equals(typeStr)) {
            return typeStr;
        }
        TemplateType tt = TemplateType.valueByName(typeStr);
        if (tt == TemplateType.UNKNOWN) {
            throw new IllegalArgumentException("Unknown template type: '" + typeStr
                    + "'. Supported: HTMLDocument, TextDocument, SpreadsheetDocument, BinaryData, "
                    + "DataCompositionSchema, AddIn, DataCompositionAppearanceTemplate, GraphicalSchema, "
                    + "ActiveDocument, GeographicalSchema, Help");
        }
        return typeStr;
    }
    //**agent TASK-174.XG-105

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

    //++agent TASK-174.XG-105 [14.07.2026 12:10:00]
    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    //++agent TASK-174.XG-105 [14.07.2026 12:48:00]
    @FunctionalInterface
    private interface CompensationStep {
        void run() throws Exception;
    }

    private static boolean attemptCompensation(Throwable cause, String description,
                                               CompensationStep step) {
        try {
            step.run();
            return true;
        } catch (Exception rollbackFailure) {
            cause.addSuppressed(new IOException("Rollback step failed: " + description
                    + ": " + rollbackFailure.getMessage(), rollbackFailure));
            return false;
        }
    }

    private boolean restoreConfiguration(Throwable cause, Path configurationXml,
                                         byte[] originalConfiguration) {
        // Повторная запись через тот же seam оставляет диагностируемой ошибку editor-save,
        // но exact-byte fallback не даёт ей прервать восстановление файлового дерева.
        attemptCompensation(cause, "retry Configuration.xml editor save",
                () -> configEditorFactory.create(configurationXml).save());
        return attemptCompensation(cause, "restore exact Configuration.xml bytes",
                () -> Files.write(configurationXml, originalConfiguration));
    }

    private static boolean verifyAddRestored(Throwable cause, Path configurationXml,
                                             byte[] originalConfiguration, Path targetMeta,
                                             Path targetDir, Path commonTemplates,
                                             boolean commonTemplatesExisted) {
        return attemptCompensation(cause, "verify add rollback",
                () -> {
                    if (!Arrays.equals(originalConfiguration, Files.readAllBytes(configurationXml))
                            || Files.exists(targetMeta)
                            || Files.exists(targetDir)
                            || (!commonTemplatesExisted && Files.exists(commonTemplates))) {
                        throw new IOException("Add rollback did not restore the original tree");
                    }
                });
    }

    private static boolean verifyRemoveRestored(Throwable cause, Path configurationXml,
                                                byte[] originalConfiguration, Path targetMeta,
                                                Path targetDir, Path stagedMeta, Path stagedDir,
                                                boolean metaMoved, boolean dirMoved) {
        return attemptCompensation(cause, "verify remove rollback",
                () -> {
                    boolean metadataRestored = !metaMoved
                            || (Files.exists(targetMeta) && !Files.exists(stagedMeta));
                    boolean bodyRestored = !dirMoved
                            || (Files.exists(targetDir) && !Files.exists(stagedDir));
                    if (!Arrays.equals(originalConfiguration, Files.readAllBytes(configurationXml))
                            || !metadataRestored || !bodyRestored) {
                        throw new IOException("Remove rollback did not restore the original tree");
                    }
                });
    }

    private static void finishCompensation(Throwable cause, Path staging,
                                           boolean restorationConfirmed) {
        if (restorationConfirmed) {
            if (attemptCompensation(cause, "delete confirmed recovery staging",
                    () -> deleteRecursively(staging))) {
                return;
            }
        }
        IOException retained = new IOException(
                "Rollback incomplete; recovery staging retained at " + staging.toAbsolutePath());
        cause.addSuppressed(retained);
        System.err.println("[ERROR] " + retained.getMessage());
    }
    //++agent TASK-174.XG-105

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path item : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
    }

    private static void removeDirectoryIfEmpty(Path path) throws IOException {
        if (!Files.isDirectory(path)) return;
        try (Stream<Path> children = Files.list(path)) {
            if (children.findAny().isEmpty()) Files.delete(path);
        }
    }
    //++agent TASK-174.XG-105

    private void validateObjectExists(Path objectXml, MdoPath object) {
        if (!Files.exists(objectXml)) {
            throw new IllegalArgumentException(
                    "Object '" + object + "' not found. Expected XML at: " + objectXml.toAbsolutePath());
        }
        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            String actualType = editor.detectObjectType();
            if ("Unknown".equals(actualType)) {
                throw new IllegalArgumentException("Expected a supported 1C metadata object XML, got unknown object type: "
                        + objectXml);
            }
            if (!object.getType().equals(actualType)) {
                throw new IllegalArgumentException("Object '" + object + "' type mismatch. Expected "
                        + object.getType() + ", got " + actualType + " in " + objectXml);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read object XML: " + objectXml + " — " + e.getMessage(), e);
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
            //++agent TASK-172 [02.06.2026 07:28:00]
            // Канон Designer (_Демо) — CRLF; нормализуем итог идемпотентно (без BOM ветка).
            Files.writeString(helpXmlPath, io.github.onec.xmlgen.io.Crlf.normalize(content), StandardCharsets.UTF_8);
            //++agent TASK-172
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
            //++agent TASK-172 [02.06.2026 07:28:00]
            // Канон Designer (_Демо) — CRLF; нормализуем итог идемпотентно (без BOM ветка).
            Files.writeString(formXmlPath, io.github.onec.xmlgen.io.Crlf.normalize(content), StandardCharsets.UTF_8);
            //++agent TASK-172
        }
    }

    private static void writeWithBom(Path path, String content) throws IOException {
        //++agent TASK-172 [02.06.2026 07:15:00]
        // Канон Designer (_Демо): тела макетов Template.xml — BOM + CRLF.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

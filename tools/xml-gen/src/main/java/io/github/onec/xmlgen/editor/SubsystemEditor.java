package io.github.onec.xmlgen.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Редактор подсистемы — модификация Content, ChildObjects, свойств.
 * Использует строковые операции для сохранения оригинального форматирования.
 */
public class SubsystemEditor {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Path filePath;
    private String content;
    private boolean hasBom;
    private boolean writeStubs = true;

    public SubsystemEditor(Path filePath) throws IOException {
        this.filePath = filePath;
        byte[] raw = Files.readAllBytes(filePath);
        this.hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        this.content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);
    }

    /**
     * Управление созданием stub-XML для добавляемых детей/content при отсутствии файлов.
     * По умолчанию включено.
     */
    public void setWriteStubs(boolean writeStubs) {
        this.writeStubs = writeStubs;
    }

    /**
     * Добавить объекты в Content.
     * Формат: JSON array строк, например ["Catalog.Товары","Document.Заказ"]
     * или одиночная строка "Type.Name".
     *
     * <p>TASK-155 A3: fail-fast on missing subsystem target / boundary guard.
     * Перед добавлением в Content проверяем, что объект существует на диске.
     * Путь к объекту вычисляется относительно корня расширения:
     * filePath.getParent() = Subsystems/ каталог, его родитель = корень расширения.
     */
    public void addContent(String spec) {
        String[] items = parseItems(spec);
        for (String item : items) {
            String raw = item.trim();
            if (raw.isEmpty()) continue;

            // TASK-171: нормализуем тип ссылки до канонического singular-English
            // (Catalogs.X→Catalog.X, Справочник.X→Catalog.X), как делает эталон Николая.
            // Без этого writer мог записать невалидный для 1С тип (Catalogs.X), который
            // платформа отвергает. Нормализация ЛОКАЛЬНАЯ (MetadataTypeRegistry не трогаем).
            String trimmed = normalizeContentType(raw);

            // TASK-155 A3: fail-fast — проверить существование целевого объекта.
            checkTargetObjectExists(trimmed);

            String entry = "<xr:Item xsi:type=\"xr:MDObjectRef\">" + escapeXml(trimmed) + "</xr:Item>";

            // Skip if already present
            if (content.contains(">" + trimmed + "</")) continue;

            // Expand <Content/> if needed
            if (content.contains("<Content/>")) {
                content = content.replace("<Content/>",
                        "<Content>\n\t\t\t\t" + entry + "\n\t\t\t</Content>");
                continue;
            }

            // Insert before </Content>
            content = content.replace("</Content>",
                    "\t" + entry + "\n\t\t\t</Content>");
        }
    }

    /**
     * Проверить, что целевой объект ("Type.Name") существует на диске.
     * Расположение: <extensionRoot>/<TypeDir>/<Name>.xml,
     * где extensionRoot = filePath.getParent() (каталог, в котором лежит подсистема).
     *
     * <p>Примеры корректных структур:
     * <ul>
     *   <li>exts/XMLGEN_TEST/SS_001.xml → extensionRoot = exts/XMLGEN_TEST/
     *       → объект ожидается в exts/XMLGEN_TEST/Catalogs/Name.xml</li>
     *   <li>exts/XMLGEN_TEST/Subsystems/SS_001.xml → extensionRoot = exts/XMLGEN_TEST/Subsystems/
     *       → объект ожидается в exts/XMLGEN_TEST/Subsystems/Catalogs/Name.xml</li>
     * </ul>
     *
     * <p>TASK-155 A3: fail-fast on missing subsystem target / boundary guard.
     */
    private void checkTargetObjectExists(String contentItem) {
        int dot = contentItem.indexOf('.');
        if (dot <= 0) return; // malformed — will be silently skipped in addContent
        String type = contentItem.substring(0, dot);
        String name = contentItem.substring(dot + 1);
        String dir = resolveDirectoryForType(type);

        // TASK-171: корень для резолва объекта = walk-up до Configuration.xml (config-layout),
        // а НЕ просто filePath.getParent(). В config-layout подсистема лежит в
        // src/xml/Subsystems/X.xml, и filePath.getParent() = src/xml/Subsystems промахивается
        // мимо src/xml/ — fail-fast ложно падал на существующих объектах (src/xml/Catalogs/...).
        // Для вложенных подсистем (Parent/Subsystems/Child.xml) промах ещё больше.
        // Fallback на filePath.getParent() сохраняет прежнее поведение для extension-layout
        // (exts/XMLGEN_TEST/SS_001.xml без Configuration.xml).
        Path extensionRoot = locateConfigRoot(filePath);
        if (extensionRoot == null) {
            extensionRoot = filePath.getParent();
        }
        if (extensionRoot == null) return; // cannot determine — skip check

        // Объект может быть представлен файлом <dir>/<Name>.xml ИЛИ каталогом <dir>/<Name>/
        // (распакованный объект со своими формами/макетами) — учитываем оба варианта.
        Path objFile = extensionRoot.resolve(dir).resolve(name + ".xml");
        Path objDir = extensionRoot.resolve(dir).resolve(name);
        if (!Files.exists(objFile) && !Files.isDirectory(objDir)) {
            throw new IllegalArgumentException(
                    "ERROR: target object " + contentItem + " does not exist"
                    + " (expected file: " + objFile + ")."
                    + " Create the object first before referencing it in a subsystem.");
        }
    }

    /**
     * TASK-171: подъём по дереву каталогов до первого Configuration.xml — надёжное
     * определение корня конфигурации для config-layout. Возвращает null, если корень
     * не найден (extension-layout / изолированный файл) — тогда вызывающий код
     * использует прежний fallback.
     */
    private static Path locateConfigRoot(Path start) {
        if (start == null) return null;
        Path dir = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) {
            dir = dir.getParent();
        }
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("Configuration.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }

    /**
     * Resolve the directory name for a metadata type (mirrors SubsystemWriter logic).
     */
    private static String resolveDirectoryForType(String type) {
        // Mirror MetadataTypeRegistry lookup for common types
        return switch (type) {
            case "Catalog" -> "Catalogs";
            case "Document" -> "Documents";
            case "InformationRegister" -> "InformationRegisters";
            case "AccumulationRegister" -> "AccumulationRegisters";
            case "AccountingRegister" -> "AccountingRegisters";
            case "CalculationRegister" -> "CalculationRegisters";
            case "BusinessProcess" -> "BusinessProcesses";
            case "Task" -> "Tasks";
            case "ExchangePlan" -> "ExchangePlans";
            case "ChartOfCharacteristicTypes" -> "ChartsOfCharacteristicTypes";
            case "ChartOfAccounts" -> "ChartsOfAccounts";
            case "ChartOfCalculationTypes" -> "ChartsOfCalculationTypes";
            case "DataProcessor" -> "DataProcessors";
            case "Report" -> "Reports";
            case "Sequence" -> "Sequences";
            case "DocumentJournal" -> "DocumentJournals";
            case "Enum" -> "Enums";
            case "CommonModule" -> "CommonModules";
            case "SessionParameter" -> "SessionParameters";
            case "Role" -> "Roles";
            case "CommonAttribute" -> "CommonAttributes";
            case "ExchangePlanNode" -> "ExchangePlans";
            case "FilterCriterion" -> "FilterCriteria";
            case "EventSubscription" -> "EventSubscriptions";
            case "ScheduledJob" -> "ScheduledJobs";
            case "FunctionalOption" -> "FunctionalOptions";
            case "FunctionalOptionsParameter" -> "FunctionalOptionsParameters";
            case "DefinedType" -> "DefinedTypes";
            case "CommonCommand" -> "CommonCommands";
            case "CommandGroup" -> "CommandGroups";
            case "CommonForm" -> "CommonForms";
            case "CommonTemplate" -> "CommonTemplates";
            case "Subsystem" -> "Subsystems";
            case "DocumentNumerator" -> "DocumentNumerators";
            case "SettingsStorage" -> "SettingsStorages";
            case "HTTPService" -> "HTTPServices";
            case "WebService" -> "WebServices";
            case "XDTOPackage" -> "XDTOPackages";
            case "Interface" -> "Interfaces";
            case "Constant" -> "Constants";
            default -> type + "s";
        };
    }

    /**
     * TASK-171: канонизация типа Content-ссылки к singular-English форме.
     * Принимает "Type.Name", нормализует только сегмент типа (имя не трогаем — оно может
     * содержать точки в крайне редких случаях, но для Content это всегда "Type.Name").
     * Покрывает plural-English (Catalogs→Catalog) и RU-синонимы (Справочник→Catalog).
     * Неизвестный/уже канонический тип оставляем как есть.
     */
    static String normalizeContentType(String item) {
        int dot = item.indexOf('.');
        if (dot <= 0) return item;
        String type = item.substring(0, dot);
        String rest = item.substring(dot + 1);
        String canon = CONTENT_TYPE_NORM.get(type);
        if (canon == null) return item; // уже канонический либо неизвестный — не трогаем
        return canon + "." + rest;
    }

    /** Карта нормализации типа Content: plural-English и RU-синонимы → singular-English. */
    private static final java.util.Map<String, String> CONTENT_TYPE_NORM = buildContentTypeNorm();

    private static java.util.Map<String, String> buildContentTypeNorm() {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        // plural English → singular
        m.put("Catalogs", "Catalog");
        m.put("Documents", "Document");
        m.put("InformationRegisters", "InformationRegister");
        m.put("AccumulationRegisters", "AccumulationRegister");
        m.put("AccountingRegisters", "AccountingRegister");
        m.put("CalculationRegisters", "CalculationRegister");
        m.put("BusinessProcesses", "BusinessProcess");
        m.put("Tasks", "Task");
        m.put("ExchangePlans", "ExchangePlan");
        m.put("ChartsOfCharacteristicTypes", "ChartOfCharacteristicTypes");
        m.put("ChartsOfAccounts", "ChartOfAccounts");
        m.put("ChartsOfCalculationTypes", "ChartOfCalculationTypes");
        m.put("DataProcessors", "DataProcessor");
        m.put("Reports", "Report");
        m.put("DocumentJournals", "DocumentJournal");
        m.put("Enums", "Enum");
        m.put("CommonModules", "CommonModule");
        m.put("Constants", "Constant");
        // RU → English singular
        m.put("Справочник", "Catalog");
        m.put("Документ", "Document");
        m.put("РегистрСведений", "InformationRegister");
        m.put("РегистрНакопления", "AccumulationRegister");
        m.put("РегистрБухгалтерии", "AccountingRegister");
        m.put("РегистрРасчета", "CalculationRegister");
        m.put("БизнесПроцесс", "BusinessProcess");
        m.put("Задача", "Task");
        m.put("ПланОбмена", "ExchangePlan");
        m.put("ПланВидовХарактеристик", "ChartOfCharacteristicTypes");
        m.put("ПланСчетов", "ChartOfAccounts");
        m.put("ПланВидовРасчета", "ChartOfCalculationTypes");
        m.put("Обработка", "DataProcessor");
        m.put("Отчет", "Report");
        m.put("ЖурналДокументов", "DocumentJournal");
        m.put("Перечисление", "Enum");
        m.put("ОбщийМодуль", "CommonModule");
        m.put("Константа", "Constant");
        // TASK-171: русские МНОЖЕСТВЕННЫЕ формы — это имена секций дерева конфигурации 1С,
        // которые агент/пользователь может передать вместо канонического типа.
        m.put("Справочники", "Catalog");
        m.put("Документы", "Document");
        m.put("РегистрыСведений", "InformationRegister");
        m.put("РегистрыНакопления", "AccumulationRegister");
        m.put("РегистрыБухгалтерии", "AccountingRegister");
        m.put("РегистрыРасчета", "CalculationRegister");
        m.put("БизнесПроцессы", "BusinessProcess");
        m.put("Задачи", "Task");
        m.put("ПланыОбмена", "ExchangePlan");
        m.put("ПланыВидовХарактеристик", "ChartOfCharacteristicTypes");
        m.put("ПланыСчетов", "ChartOfAccounts");
        m.put("ПланыВидовРасчета", "ChartOfCalculationTypes");
        m.put("Обработки", "DataProcessor");
        m.put("Отчеты", "Report");
        m.put("ЖурналыДокументов", "DocumentJournal");
        m.put("Перечисления", "Enum");
        m.put("ОбщиеМодули", "CommonModule");
        m.put("Константы", "Constant");
        return m;
    }

    /**
     * Удалить объекты из Content.
     */
    public void removeContent(String spec) {
        String[] items = parseItems(spec);
        for (String item : items) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) continue;

            String escaped = Pattern.quote(escapeXml(trimmed));
            Pattern p = Pattern.compile(
                    "[ \\t]*<xr:Item[^>]*>" + escaped + "</xr:Item>[ \\t]*\\r?\\n?");
            content = p.matcher(content).replaceFirst("");
        }
    }

    /**
     * Добавить дочернюю подсистему в ChildObjects. При включённом writeStubs (по умолчанию) —
     * создаёт stub-XML ребёнка, если файл отсутствует, чтобы валидация не падала.
     */
    public void addChild(String childName) {
        String entry = "<Subsystem>" + escapeXml(childName) + "</Subsystem>";

        // Skip if already present
        if (content.contains(entry)) return;

        // Expand <ChildObjects/> if needed
        if (content.contains("<ChildObjects/>")) {
            content = content.replace("<ChildObjects/>",
                    "<ChildObjects>\n\t\t\t" + entry + "\n\t\t</ChildObjects>");
        } else {
            // Insert before </ChildObjects>
            content = content.replace("</ChildObjects>",
                    "\t" + entry + "\n\t\t</ChildObjects>");
        }

        // Stub XML для ребёнка, если файл отсутствует.
        if (writeStubs) {
            try {
                ensureSubsystemChildStub(childName);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write subsystem stub for " + childName, e);
            }
        }
    }

    /**
     * Создать минимальный XML дочерней подсистемы, если файл отсутствует.
     * Путь: <parentDir>/<parentName>/Subsystems/<childName>.xml
     */
    private void ensureSubsystemChildStub(String childName) throws IOException {
        // filePath = .../Subsystems/<ParentName>.xml
        // child goes to .../Subsystems/<ParentName>/Subsystems/<childName>.xml
        Path parentDir = filePath.getParent();
        if (parentDir == null) return;
        String parentFileName = filePath.getFileName().toString();
        if (!parentFileName.endsWith(".xml")) return;
        String parentBase = parentFileName.substring(0, parentFileName.length() - 4);
        Path childrenDir = parentDir.resolve(parentBase).resolve("Subsystems");
        Path childFile = childrenDir.resolve(childName + ".xml");
        if (Files.exists(childFile)) return;
        Files.createDirectories(childrenDir);

        String uuid = java.util.UUID.randomUUID().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\">\n");
        sb.append("\t<Subsystem uuid=\"").append(uuid).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(escapeXml(childName)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(childName)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t\t<IncludeHelpInContents>true</IncludeHelpInContents>\n");
        sb.append("\t\t\t<IncludeInCommandInterface>true</IncludeInCommandInterface>\n");
        sb.append("\t\t\t<UseOneCommand>false</UseOneCommand>\n");
        sb.append("\t\t\t<Explanation/>\n");
        sb.append("\t\t\t<Picture/>\n");
        sb.append("\t\t\t<Content/>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t\t<ChildObjects/>\n");
        sb.append("\t</Subsystem>\n");
        sb.append("</MetaDataObject>\n");

        //++agent TASK-172 [02.06.2026 07:23:00]
        // Новый файл дочерней подсистемы: канон Designer (_Демо) — BOM + CRLF.
        Files.write(childFile, io.github.onec.xmlgen.io.Crlf.withBom(sb.toString()));
        //++agent TASK-172
    }

    /**
     * Удалить дочернюю подсистему из ChildObjects.
     */
    public void removeChild(String childName) {
        String escaped = Pattern.quote(escapeXml(childName));
        Pattern p = Pattern.compile(
                "[ \\t]*<Subsystem>" + escaped + "</Subsystem>[ \\t]*\\r?\\n?");
        content = p.matcher(content).replaceFirst("");
    }

    /**
     * Изменить свойство подсистемы.
     * Формат: "PropName=Value"
     * Поддерживает: Name, Comment, IncludeInCommandInterface, UseOneCommand,
     *               IncludeHelpInContents, Synonym, Explanation.
     */
    private static final Set<String> BOOLEAN_PROPS = Set.of(
            "IncludeInCommandInterface", "UseOneCommand", "IncludeHelpInContents");

    private static final Set<String> LOCAL_STRING_PROPS = Set.of(
            "Synonym", "Explanation");

    public void setProperty(String spec) {
        String[] kv = spec.split("=", 2);
        if (kv.length != 2) throw new IllegalArgumentException("Expected PropName=Value format: " + spec);
        String propName = kv[0].trim();
        String value = kv[1].trim();

        if (LOCAL_STRING_PROPS.contains(propName)) {
            setLocalStringProperty(propName, value);
        } else if (BOOLEAN_PROPS.contains(propName)) {
            String normalized = normalizeBoolean(value);
            setSimpleProperty(propName, normalized);
        } else if ("Picture".equals(propName)) {
            setPictureProperty(value);
        } else {
            setSimpleProperty(propName, value);
        }
    }

    private String normalizeBoolean(String value) {
        switch (value.toLowerCase()) {
            case "true": case "да": case "1": case "yes": return "true";
            case "false": case "нет": case "0": case "no": return "false";
            default: return value;
        }
    }

    private void setPictureProperty(String value) {
        // Picture value: CommonPicture.Name or StdPicture.Name
        String pictureBlock = "<Picture>\n"
                + "\t\t\t\t<xr:Ref>" + escapeXml(value) + "</xr:Ref>\n"
                + "\t\t\t</Picture>";

        // Replace existing Picture block
        Pattern existingPic = Pattern.compile(
                "<Picture>.*?</Picture>", Pattern.DOTALL);
        Matcher m = existingPic.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(pictureBlock));
            return;
        }

        // Replace self-closing <Picture/>
        Pattern selfClose = Pattern.compile("<Picture\\s*/>");
        m = selfClose.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(pictureBlock));
        }
    }

    public void save() throws IOException {
        //++agent TASK-172 [02.06.2026 07:22:00]
        // Канон Designer (_Демо) — CRLF. Правка подсистемы строковыми заменами вставляет
        // \n-фрагменты; нормализуем итог к CRLF идемпотентно, BOM-решение сохраняем.
        byte[] contentBytes = io.github.onec.xmlgen.io.Crlf.normalize(content).getBytes(StandardCharsets.UTF_8);
        if (hasBom) {
            byte[] result = new byte[BOM.length + contentBytes.length];
            System.arraycopy(BOM, 0, result, 0, BOM.length);
            System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
            Files.write(filePath, result);
        } else {
            Files.write(filePath, contentBytes);
        }
        //++agent TASK-172
    }

    // --- Internal ---

    private void setSimpleProperty(String propName, String value) {
        // Try replace existing non-empty property
        Pattern nonEmpty = Pattern.compile(
                "<" + Pattern.quote(propName) + ">([^<]*)</" + Pattern.quote(propName) + ">");
        Matcher m = nonEmpty.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("<" + propName + ">" + Matcher.quoteReplacement(escapeXml(value))
                    + "</" + propName + ">");
            return;
        }

        // Try replace self-closing
        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        m = selfClose.matcher(content);
        if (m.find()) {
            content = m.replaceFirst("<" + propName + ">" + Matcher.quoteReplacement(escapeXml(value))
                    + "</" + propName + ">");
        }
    }

    private void setLocalStringProperty(String propName, String value) {
        // Case 1: already has <v8:content>
        Pattern contentPat = Pattern.compile(
                "(<" + Pattern.quote(propName) + ">.*?<v8:content>)[^<]*(</v8:content>)",
                Pattern.DOTALL);
        Matcher m = contentPat.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(m.group(1))
                    + Matcher.quoteReplacement(escapeXml(value))
                    + Matcher.quoteReplacement(m.group(2)));
            return;
        }

        // Case 2: self-closing — expand to LocalString block
        String replacement = "<" + propName + ">\n"
                + "\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t<v8:content>" + escapeXml(value) + "</v8:content>\n"
                + "\t\t\t\t</v8:item>\n"
                + "\t\t\t</" + propName + ">";

        Pattern selfClose = Pattern.compile("<" + Pattern.quote(propName) + "\\s*/>");
        m = selfClose.matcher(content);
        if (m.find()) {
            content = m.replaceFirst(Matcher.quoteReplacement(replacement));
        }
    }

    /**
     * Parse items from either JSON array format or single string.
     */
    private String[] parseItems(String spec) {
        String trimmed = spec.trim();
        if (trimmed.startsWith("[")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode arr = mapper.readTree(trimmed);
                List<String> items = new ArrayList<>();
                for (JsonNode node : arr) {
                    items.add(node.asText());
                }
                return items.toArray(new String[0]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid JSON array: " + trimmed, e);
            }
        }
        // Single item or ;; separated
        return trimmed.split("\\s*;;\\s*");
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}

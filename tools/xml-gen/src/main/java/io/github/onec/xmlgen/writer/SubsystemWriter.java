package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Генератор подсистемы 1С из JSON DSL.
 * Создаёт XML подсистемы, CommandInterface.xml (пустой), регистрирует в Configuration.xml
 * либо в родительской подсистеме при наличии parentPath.
 */
public class SubsystemWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private boolean writeStubs = true;

    /**
     * Управление созданием XML-заглушек для declared content и children,
     * у которых отсутствует файл объекта. По умолчанию включено:
     * без stub-а регистрация остаётся «висящей» и валидация конфигурации падает.
     */
    public void setWriteStubs(boolean writeStubs) {
        this.writeStubs = writeStubs;
    }

    public void compile(Path jsonPath, Path outputDir) throws IOException {
        compile(jsonPath, outputDir, null);
    }

    /**
     * Создать подсистему из JSON.
     *
     * @param jsonPath   путь к JSON-определению
     * @param outputDir  каталог Subsystems/ верхнего уровня
     * @param parentPath путь к XML родительской подсистемы (для вложенных); если null —
     *                   регистрация выполняется вызывающим через config edit
     */
    public void compile(Path jsonPath, Path outputDir, Path parentPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonPath.toFile());

        String name = requireString(root, "name");
        String synonym = getString(root, "synonym", name);
        String comment = getString(root, "comment", "");
        boolean includeInCI = getBoolean(root, "includeInCommandInterface", true);
        boolean useOneCommand = getBoolean(root, "useOneCommand", false);
        String explanation = getString(root, "explanation", "");
        String picture = getString(root, "picture", null);
        List<String> content = getStringList(root, "content");
        List<String> children = getStringList(root, "children");

        Files.createDirectories(outputDir);

        String uuid = UuidGenerator.generate();

        // 1. Write <Name>.xml
        writeSubsystemXml(outputDir, name, synonym, comment, includeInCI,
                useOneCommand, explanation, picture, content, children, uuid);

        // 2. Create directory structure if needed
        Path subsystemDir = outputDir.resolve(name);
        if (!children.isEmpty() || includeInCI) {
            Path extDir = subsystemDir.resolve("Ext");
            Files.createDirectories(extDir);
            writeEmptyCommandInterface(extDir.resolve("CommandInterface.xml"));

            if (!children.isEmpty()) {
                Files.createDirectories(subsystemDir.resolve("Subsystems"));
            }
        }

        // 3. Stub XML for declared content / children (если файлы отсутствуют).
        if (writeStubs) {
            // Content: ссылки на объекты конфигурации типа "Catalog.Товары".
            // configRoot = outputDir — содержимое подсистемы (.xml) лежит непосредственно в outputDir,
            // а объекты-соседи (Catalogs/, Documents/ и т.д.) — в sibling-каталогах того же outputDir.
            // Старый вариант (outputDir.getParent()) ошибочно поднимался на уровень выше
            // (например, exts/ вместо exts/XMLGEN_TEST/), что вызывало запись за пределы расширения.
            // TASK-155 A3: используем outputDir напрямую как корень расширения.
            Path configRoot = outputDir;
            for (String item : content) {
                ensureContentStub(configRoot, item);
            }
            // Children: <ChildName> внутри ChildObjects → Subsystems/ChildName.xml рядом с собой
            Path childrenDir = subsystemDir.resolve("Subsystems");
            for (String childName : children) {
                Path childFile = childrenDir.resolve(childName + ".xml");
                if (!Files.exists(childFile)) {
                    Files.createDirectories(childrenDir);
                    writeSubsystemStub(childFile, childName);
                }
            }
        }

        // 4. Регистрация в родительской подсистеме (bottom-up --parent).
        if (parentPath != null) {
            registerChildInParent(parentPath, name);
        }
    }

    private void writeSubsystemXml(Path outputDir, String name, String synonym,
                                    String comment, boolean includeInCI,
                                    boolean useOneCommand, String explanation,
                                    String picture, List<String> content,
                                    List<String> children, String uuid) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\">\n");
        sb.append("\t<Subsystem uuid=\"").append(uuid).append("\">\n");

        // Properties
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(escapeXml(name)).append("</Name>\n");

        // Synonym
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(synonym)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");

        // Comment
        if (comment.isEmpty()) {
            sb.append("\t\t\t<Comment/>\n");
        } else {
            sb.append("\t\t\t<Comment>").append(escapeXml(comment)).append("</Comment>\n");
        }

        sb.append("\t\t\t<IncludeHelpInContents>true</IncludeHelpInContents>\n");
        sb.append("\t\t\t<IncludeInCommandInterface>").append(includeInCI).append("</IncludeInCommandInterface>\n");
        sb.append("\t\t\t<UseOneCommand>").append(useOneCommand).append("</UseOneCommand>\n");

        // Explanation
        if (explanation.isEmpty()) {
            sb.append("\t\t\t<Explanation/>\n");
        } else {
            sb.append("\t\t\t<Explanation>\n");
            sb.append("\t\t\t\t<v8:item>\n");
            sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
            sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(explanation)).append("</v8:content>\n");
            sb.append("\t\t\t\t</v8:item>\n");
            sb.append("\t\t\t</Explanation>\n");
        }

        // Picture
        if (picture != null && !picture.isEmpty()) {
            sb.append("\t\t\t<Picture>\n");
            sb.append("\t\t\t\t<xr:Ref>").append(escapeXml(picture)).append("</xr:Ref>\n");
            sb.append("\t\t\t\t<xr:LoadTransparent>false</xr:LoadTransparent>\n");
            sb.append("\t\t\t</Picture>\n");
        } else {
            sb.append("\t\t\t<Picture/>\n");
        }

        // Content
        if (content.isEmpty()) {
            sb.append("\t\t\t<Content/>\n");
        } else {
            sb.append("\t\t\t<Content>\n");
            for (String item : content) {
                sb.append("\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">")
                        .append(escapeXml(item)).append("</xr:Item>\n");
            }
            sb.append("\t\t\t</Content>\n");
        }

        sb.append("\t\t</Properties>\n");

        // ChildObjects
        if (children.isEmpty()) {
            sb.append("\t\t<ChildObjects/>\n");
        } else {
            sb.append("\t\t<ChildObjects>\n");
            for (String child : children) {
                sb.append("\t\t\t<Subsystem>").append(escapeXml(child)).append("</Subsystem>\n");
            }
            sb.append("\t\t</ChildObjects>\n");
        }

        sb.append("\t</Subsystem>\n");
        sb.append("</MetaDataObject>\n");

        writeWithBom(outputDir.resolve(name + ".xml"), sb.toString());
    }

    private void writeEmptyCommandInterface(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n");
        sb.append("\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\"/>\n");

        writeWithBom(path, sb.toString());
    }

    // --- Helpers ---

    private static String requireString(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull() || node.asText().isEmpty()) {
            throw new IllegalArgumentException("Required field missing: " + field);
        }
        return node.asText();
    }

    private static String getString(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return defaultValue;
        return node.asText();
    }

    private static boolean getBoolean(JsonNode root, String field, boolean defaultValue) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) return defaultValue;
        return node.asBoolean();
    }

    private static List<String> getStringList(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        JsonNode node = root.get(field);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private static void writeWithBom(Path path, String content) throws IOException {
        //++agent TASK-172 [02.06.2026 07:15:00]
        // Канон Designer (_Демо): BOM + CRLF через единый чокпоинт нормализации.
        Files.write(path, io.github.onec.xmlgen.io.Crlf.withBom(content));
        //++agent TASK-172
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /**
     * Создать stub-XML для элемента Content ("Type.Name"), если файл объекта отсутствует.
     * Нужен чтобы валидация конфигурации не падала при промежуточном состоянии создания.
     *
     * <p>TASK-155 A3: fail-fast on missing subsystem target / boundary guard.
     * <ul>
     *   <li>Если объект отсутствует — бросаем IllegalArgumentException вместо молчаливого создания.
     *       Команда subsystem compile не должна создавать объекты, которые не заявлены явно.</li>
     *   <li>Defensive boundary guard: путь к stub-файлу MUST начинаться с configRoot.
     *       Нарушение указывает на некорректный outputDir (например, exts/XMLGEN_TEST/ вместо
     *       exts/XMLGEN_TEST/Subsystems/), что привело бы к записи за пределы расширения.</li>
     * </ul>
     */
    private void ensureContentStub(Path configRoot, String contentItem) throws IOException {
        int dot = contentItem.indexOf('.');
        if (dot <= 0) return;
        String type = contentItem.substring(0, dot);
        String name = contentItem.substring(dot + 1);
        String dir = resolveDirectoryForType(type);

        // TASK-171: для config-layout реальный корень = walk-up до Configuration.xml,
        // а не переданный configRoot (= outputDir = .../Subsystems). Объекты Content
        // (Catalogs/, Documents/...) лежат соседями в корне конфигурации (src/xml/),
        // а не внутри Subsystems/. Прежний резолв + boundary-guard ложно падали на
        // config-layout. Fallback на переданный configRoot — для extension-layout
        // (outputDir = exts/XMLGEN_TEST/Subsystems без Configuration.xml).
        Path resolveRoot = locateConfigRoot(configRoot);
        boolean configLayout = (resolveRoot != null);
        if (resolveRoot == null) {
            resolveRoot = configRoot;
        }
        Path objFile = resolveRoot.resolve(dir).resolve(name + ".xml");
        Path objDir = resolveRoot.resolve(dir).resolve(name);

        // TASK-155 A3: defensive boundary guard — путь файла должен быть внутри корня.
        // TASK-171: для config-layout проверяем против найденного корня конфигурации
        // (объекты-соседи легитимно вне Subsystems/); guard остаётся для extension-layout.
        Path canonicalRoot = resolveRoot.toAbsolutePath().normalize();
        Path canonicalFile = objFile.toAbsolutePath().normalize();
        if (!configLayout && !canonicalFile.startsWith(canonicalRoot)) {
            throw new IllegalStateException(
                    "Output path escapes extension boundary: " + canonicalFile
                    + " is not under configRoot " + canonicalRoot
                    + ". Check that outputDir points to Subsystems/ inside the extension.");
        }

        // TASK-155 A3: fail-fast on missing target object — не создавать заглушку молча.
        // Пользователь должен сначала создать объект (xml-gen meta compile ...), затем ссылаться на него.
        // TASK-171: объект может быть файлом <dir>/<Name>.xml или распакованным каталогом <dir>/<Name>/.
        if (!Files.exists(objFile) && !Files.isDirectory(objDir)) {
            throw new IllegalArgumentException(
                    "ERROR: target object " + contentItem + " does not exist"
                    + " (expected file: " + objFile + ")."
                    + " Create the object first or use --no-stubs to skip this check.");
        }
    }

    /**
     * TASK-171: подъём по дереву каталогов до первого Configuration.xml — надёжное
     * определение корня конфигурации для config-layout. Возвращает null, если корень
     * не найден (extension-layout / изолированный outputDir) — тогда используется
     * прежний fallback на переданный configRoot.
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

    private static String resolveDirectoryForType(String type) {
        MetadataTypeRegistry.TypeDescriptor td = MetadataTypeRegistry.get(type);
        if (td != null) return td.directory();
        return switch (type) {
            case "Subsystem" -> "Subsystems";
            case "Role" -> "Roles";
            case "CommonCommand" -> "CommonCommands";
            case "CommandGroup" -> "CommandGroups";
            case "CommonForm" -> "CommonForms";
            case "CommonTemplate" -> "CommonTemplates";
            case "CommonAttribute" -> "CommonAttributes";
            case "FilterCriterion" -> "FilterCriteria";
            case "DocumentNumerator" -> "DocumentNumerators";
            case "Sequence" -> "Sequences";
            default -> type + "s";
        };
    }

    private void writeSubsystemStub(Path file, String name) throws IOException {
        String uuid = UuidGenerator.generate();
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
        sb.append("\t\t\t<Name>").append(escapeXml(name)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(name)).append("</v8:content>\n");
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
        writeWithBom(file, sb.toString());
    }

    /**
     * Минимальная stub-XML для объекта метаданных (Properties только Name + Synonym),
     * чтобы конфигуратор проходил проверку в промежуточном состоянии.
     */
    private void writeMetaStub(Path file, String type, String name) throws IOException {
        String uuid = UuidGenerator.generate();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n");
        sb.append("\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n");
        sb.append("\txmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n");
        sb.append("\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
        sb.append("\tversion=\"2.17\">\n");
        sb.append("\t<").append(type).append(" uuid=\"").append(uuid).append("\">\n");
        sb.append("\t\t<Properties>\n");
        sb.append("\t\t\t<Name>").append(escapeXml(name)).append("</Name>\n");
        sb.append("\t\t\t<Synonym>\n");
        sb.append("\t\t\t\t<v8:item>\n");
        sb.append("\t\t\t\t\t<v8:lang>ru</v8:lang>\n");
        sb.append("\t\t\t\t\t<v8:content>").append(escapeXml(name)).append("</v8:content>\n");
        sb.append("\t\t\t\t</v8:item>\n");
        sb.append("\t\t\t</Synonym>\n");
        sb.append("\t\t\t<Comment/>\n");
        sb.append("\t\t</Properties>\n");
        sb.append("\t\t<ChildObjects/>\n");
        sb.append("\t</").append(type).append(">\n");
        sb.append("</MetaDataObject>\n");
        writeWithBom(file, sb.toString());
    }

    /**
     * Добавить подсистему в ChildObjects родительской подсистемы.
     * Сохраняет форматирование файла, идемпотентно (дубликаты не создаются).
     */
    private void registerChildInParent(Path parentPath, String childName) throws IOException {
        if (!Files.exists(parentPath)) {
            throw new IOException("Parent subsystem file not found: " + parentPath);
        }
        byte[] raw = Files.readAllBytes(parentPath);
        boolean hasBom = raw.length >= 3 && raw[0] == BOM[0] && raw[1] == BOM[1] && raw[2] == BOM[2];
        String content = hasBom
                ? new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8)
                : new String(raw, StandardCharsets.UTF_8);

        String entry = "<Subsystem>" + escapeXml(childName) + "</Subsystem>";
        if (content.contains(entry)) return;

        if (content.contains("<ChildObjects/>")) {
            content = content.replace("<ChildObjects/>",
                    "<ChildObjects>\n\t\t\t" + entry + "\n\t\t</ChildObjects>");
        } else if (content.contains("</ChildObjects>")) {
            content = content.replace("</ChildObjects>",
                    "\t\t\t" + entry + "\n\t\t</ChildObjects>");
        } else {
            throw new IOException("Parent subsystem has no ChildObjects section: " + parentPath);
        }

        //++agent TASK-172 [02.06.2026 07:16:00]
        // Вставка ChildObjects в родительскую подсистему: entry содержит \n-литералы,
        // которые в CRLF-каноне дали бы смешанную раскладку. Нормализуем итог к CRLF
        // (идемпотентно — существующие \r\n не дублируются). hasBom-решение сохраняем.
        byte[] bytes = io.github.onec.xmlgen.io.Crlf.normalize(content).getBytes(StandardCharsets.UTF_8);
        if (hasBom) {
            byte[] out = new byte[BOM.length + bytes.length];
            System.arraycopy(BOM, 0, out, 0, BOM.length);
            System.arraycopy(bytes, 0, out, BOM.length, bytes.length);
            Files.write(parentPath, out);
        } else {
            Files.write(parentPath, bytes);
        }
        //++agent TASK-172
    }
}

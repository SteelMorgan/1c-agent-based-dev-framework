package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.model.UuidGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Генератор подсистемы 1С из JSON DSL.
 * Создаёт XML подсистемы, CommandInterface.xml (пустой), регистрирует в Configuration.xml.
 */
public class SubsystemWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Создать подсистему из JSON.
     * Регистрация в Configuration.xml делается отдельно через {@code config edit --op add-childObject}.
     *
     * @param jsonPath  путь к JSON-определению
     * @param outputDir каталог Subsystems/ (или родительский Subsystems/ для вложенных)
     */
    public void compile(Path jsonPath, Path outputDir) throws IOException {
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
        if (!children.isEmpty() || includeInCI) {
            Path subsystemDir = outputDir.resolve(name);

            // Ext/CommandInterface.xml (empty stub)
            Path extDir = subsystemDir.resolve("Ext");
            Files.createDirectories(extDir);
            writeEmptyCommandInterface(extDir.resolve("CommandInterface.xml"));

            // Subsystems/ for children
            if (!children.isEmpty()) {
                Files.createDirectories(subsystemDir.resolve("Subsystems"));
            }
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

package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-218 [29.07.2026 05:38:54] XG-68 XG-99
/**
 * Регрессии текстового writer при добавлении значения в существующее перечисление.
 */
class MetaEditorXg68Xg99Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void addEnumValueSeparatesNewValueFromChildObjectsClosingTag() throws Exception {
        Path xml = writeEnum("enum-crlf.xml", "\r\n", true);

        silentEditor().edit(xml, "add-enumValue", "Новое");

        String result = contentWithoutBom(xml);
        assertThat(result)
                .contains("</EnumValue>\r\n\t\t\t<EnumValue uuid=")
                .contains("</EnumValue>\r\n\t\t</ChildObjects>")
                .doesNotContain("</EnumValue>\t\t</ChildObjects>");
    }

    @Test
    void addEnumValuePreservesLfWithoutBom() throws Exception {
        Path xml = writeEnum("enum-lf.xml", "\n", false);

        silentEditor().edit(xml, "add-enumValue", "Новое");

        byte[] result = Files.readAllBytes(xml);
        assertThat(hasBom(result)).isFalse();
        String content = new String(result, StandardCharsets.UTF_8);
        assertThat(content).contains("\n").doesNotContain("\r\n");
    }

    @Test
    void addEnumValuePreservesCrlfAndBom() throws Exception {
        Path xml = writeEnum("enum-crlf-bom.xml", "\r\n", true);

        silentEditor().edit(xml, "add-enumValue", "Новое");

        byte[] result = Files.readAllBytes(xml);
        assertThat(result).startsWith(BOM);
        String content = contentWithoutBom(xml);
        assertThat(content).contains("\r\n");
        assertThat(content.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void addEnumValueDoesNotProduceTrailingWhitespace() throws Exception {
        Path xml = writeEnum("enum-diff-check.xml", "\n", false);

        silentEditor().edit(xml, "add-enumValue", "Новое");

        assertThat(contentWithoutBom(xml))
                .as("эквивалент git diff --check для добавленного XML-блока")
                .doesNotContainPattern("(?m)[\\t ]+$");
    }

    private Path writeEnum(String fileName, String eol, boolean withBom) throws Exception {
        String xml = String.join(eol,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\""
                        + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">",
                "\t<Enum uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">",
                "\t\t<Properties>",
                "\t\t\t<Name>ТестовоеПеречисление</Name>",
                "\t\t\t<Synonym/>",
                "\t\t\t<Comment/>",
                "\t\t</Properties>",
                "\t\t<ChildObjects>",
                "\t\t\t<EnumValue uuid=\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\">",
                "\t\t\t\t<Properties>",
                "\t\t\t\t\t<Name>Существующее</Name>",
                "\t\t\t\t\t<Synonym/>",
                "\t\t\t\t\t<Comment/>",
                "\t\t\t\t</Properties>",
                "\t\t\t</EnumValue>",
                "\t\t</ChildObjects>",
                "\t</Enum>",
                "</MetaDataObject>",
                "");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        Path path = tempDir.resolve(fileName);
        if (!withBom) {
            Files.write(path, body);
            return path;
        }
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
        return path;
    }

    private MetaEditor silentEditor() {
        return new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    private String contentWithoutBom(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        int offset = hasBom(bytes) ? BOM.length : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private boolean hasBom(byte[] bytes) {
        return bytes.length >= BOM.length
                && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2];
    }
}
//++agent TASK-218

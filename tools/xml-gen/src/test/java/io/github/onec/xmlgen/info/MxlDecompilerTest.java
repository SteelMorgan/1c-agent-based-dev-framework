package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты MxlDecompiler: декомпиляция Template.xml → JSON DSL.
 * Канон Широкова: auto-naming стилей при отсутствии id.
 */
class MxlDecompilerTest {

    private final MxlDecompiler decompiler = new MxlDecompiler();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    @Test
    void autoNamingAssignsStyle1ForUnnamedFormat() throws Exception {
        Path file = writeMxl(
                "\t<format>\n" +
                "\t\t<horizontalAlignment>Center</horizontalAlignment>\n" +
                "\t\t<verticalAlignment>Top</verticalAlignment>\n" +
                "\t</format>\n" +
                "\t<height>0</height>\n");

        Path output = tempDir.resolve("dsl.json");
        XmlDocument doc = reader.parse(file);
        decompiler.decompile(doc, output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"Style1\"");
        assertThat(json).contains("\"align\" : \"center\"");
    }

    @Test
    void autoNamingDeduplicatesIdenticalStyles() throws Exception {
        Path file = writeMxl(
                "\t<format>\n" +
                "\t\t<horizontalAlignment>Center</horizontalAlignment>\n" +
                "\t</format>\n" +
                "\t<format>\n" +
                "\t\t<horizontalAlignment>Center</horizontalAlignment>\n" +
                "\t</format>\n" +
                "\t<height>0</height>\n");

        Path output = tempDir.resolve("dsl.json");
        XmlDocument doc = reader.parse(file);
        decompiler.decompile(doc, output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        // Both formats are identical → only Style1 is created, Style2 must NOT appear.
        assertThat(json).contains("\"Style1\"");
        assertThat(json).doesNotContain("\"Style2\"");
    }

    @Test
    void explicitIdIsPreservedNotRenamed() throws Exception {
        Path file = writeMxl(
                "\t<format>\n" +
                "\t\t<id>HeaderBold</id>\n" +
                "\t\t<horizontalAlignment>Right</horizontalAlignment>\n" +
                "\t</format>\n" +
                "\t<height>0</height>\n");

        Path output = tempDir.resolve("dsl.json");
        XmlDocument doc = reader.parse(file);
        decompiler.decompile(doc, output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"HeaderBold\"");
        assertThat(json).doesNotContain("\"Style1\"");
    }

    // --- helpers ---

    private Path writeMxl(String body) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>3</size></columns>\n" +
                body +
                "</document>\n";
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}

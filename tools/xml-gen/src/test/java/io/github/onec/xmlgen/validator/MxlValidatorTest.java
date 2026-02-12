package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.MxlWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты MxlValidator: MXL-001..005, MXL-101..106.
 */
class MxlValidatorTest {

    private final MxlValidator validator = new MxlValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== Roundtrip ====================

    @Test
    void testWriterGeneratedMxlPassesValidation() throws Exception {
        MxlDsl dsl = new MxlDsl(
                3, 40, null,
                Map.of("header", new MxlDsl.Font("Arial", 12, true, false, false, false)),
                null,
                List.of(new MxlDsl.Area("Header",
                        List.of(new MxlDsl.Row(null, null, List.of(
                                new MxlDsl.Cell(1, 2, null, null, null, null, "Title", null)
                        ), null))))
        );

        Path output = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, output);

        XmlDocument doc = reader.parse(output);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Errors in writer-generated MXL: " + errors)
                .isEmpty();
    }

    // ==================== MXL-001: Root element ====================

    @Test
    void testWrongRootElement() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<wrongRoot xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-001"));
    }

    // ==================== MXL-101: Invalid alignment ====================

    @Test
    void testInvalidHorizontalAlignment() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem>\n" +
                "\t\t<c>\n" +
                "\t\t\t<horizontalAlignment>InvalidAlign</horizontalAlignment>\n" +
                "\t\t</c>\n" +
                "\t</rowsItem>\n" +
                "</document>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("MXL-101") && i.getMessage().contains("InvalidAlign"));
    }

    // ==================== MXL-103: Negative merge ====================

    @Test
    void testNegativeMerge() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem>\n" +
                "\t\t<c>\n" +
                "\t\t\t<merge>-1</merge>\n" +
                "\t\t</c>\n" +
                "\t</rowsItem>\n" +
                "</document>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-103"));
    }

    // ==================== Valid complete MXL ====================

    @Test
    void testValidCompleteMxl() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>3</size></columns>\n" +
                "\t<height>2</height>\n" +
                "\t<rowsItem>\n" +
                "\t\t<c>\n" +
                "\t\t\t<horizontalAlignment>Left</horizontalAlignment>\n" +
                "\t\t\t<merge>2</merge>\n" +
                "\t\t</c>\n" +
                "\t</rowsItem>\n" +
                "\t<rowsItem>\n" +
                "\t\t<c>\n" +
                "\t\t\t<horizontalAlignment>Center</horizontalAlignment>\n" +
                "\t\t</c>\n" +
                "\t</rowsItem>\n" +
                "</document>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors).isEmpty();
    }

    // ==================== Real file ====================

    @Test
    void testRealMxlIfAvailable() throws Exception {
        Path realFile = Path.of("/workspaces/work/repos/1C Projects/DSSL UT/src/xml/InformationRegisters/ПротоколРаботыПользователей/Templates/Макет/Ext/Template.xml");
        if (!Files.exists(realFile)) return;

        XmlDocument doc = reader.parse(realFile);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Structure errors in real MXL: " + errors)
                .isEmpty();
    }

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

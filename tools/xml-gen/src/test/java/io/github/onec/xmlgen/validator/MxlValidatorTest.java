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

    // ==================== Canon-borrowed (MXL-201..207) ====================
    // Семь классов ошибок из канона Широкова.

    // --- MXL-201: out-of-bounds column ---

    @Test
    void mxl201_happyPathNoOutOfBoundsColumn() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>3</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f></c></c>\n" +
                "\t\t<c><i>1</i><c><f>0</f></c></c>\n" +
                "\t\t<c><i>2</i><c><f>0</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-201"));
    }

    @Test
    void mxl201_failsOnCellPastColumns() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>5</i><c><f>0</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-201"));
    }

    // --- MXL-202: overlapping cells ---

    @Test
    void mxl202_happyPathNoOverlap() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>3</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f></c></c>\n" +
                "\t\t<c><i>1</i><c><f>0</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-202"));
    }

    @Test
    void mxl202_failsOnTwoCellsAtSamePosition() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>3</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>1</i><c><f>0</f></c></c>\n" +
                "\t\t<c><i>1</i><c><f>0</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-202"));
    }

    // --- MXL-203: rowspan beyond area ---

    @Test
    void mxl203_happyPathRowspanWithinHeight() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>3</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f><rowMerge>1</rowMerge></c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<rowsItem><index>1</index><row/></rowsItem>\n" +
                "\t<rowsItem><index>2</index><row/></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-203"));
    }

    @Test
    void mxl203_failsWhenRowspanExceedsDocumentHeight() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>2</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f><rowMerge>5</rowMerge></c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<rowsItem><index>1</index><row/></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-203"));
    }

    // --- MXL-204: unknown parameter name (empty heuristic) ---

    @Test
    void mxl204_happyPathParameterHasName() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" +
                "<parameter xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"><v8:content>OK</v8:content></parameter>" +
                "</c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-204"));
    }

    @Test
    void mxl204_failsOnEmptyParameterName() throws Exception {
        Path file = writeMxlBody(
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" +
                "<parameter xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"><v8:content></v8:content></parameter>" +
                "</c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-204"));
    }

    // --- MXL-205: format mismatch ---

    @Test
    void mxl205_happyPathNumericFormatOnNumericCell() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>num</id>\n" +
                "\t\t<format>ЧДЦ=2</format>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>num</f><tl xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">" +
                "<v8:item><v8:lang>ru</v8:lang><v8:content>123.45</v8:content></v8:item></tl>" +
                "</c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-205"));
    }

    @Test
    void mxl205_failsWhenNumericFormatOnTextCell() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>num</id>\n" +
                "\t\t<format>ЧДЦ=2</format>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>num</f><tl xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">" +
                "<v8:item><v8:lang>ru</v8:lang><v8:content>HelloText</v8:content></v8:item></tl>" +
                "</c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-205"));
    }

    @Test
    void mxl205_failsForCanonicalNumericFormatOnTextCell() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<format xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">\n" +
                "\t\t\t<v8:item><v8:lang>ru</v8:lang><v8:content>ЧДЦ=2</v8:content></v8:item>\n" +
                "\t\t</format>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f><tl xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">" +
                "<v8:item><v8:lang>ru</v8:lang><v8:content>HelloText</v8:content></v8:item></tl>" +
                "</c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-205"));
    }

    // --- MXL-206/MXL-208: page size impossible and non-canonical pageSetup ---

    @Test
    void mxl206_happyPathWidthsFitPage() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>__cw_100</id>\n" +
                "\t\t<width>100</width>\n" +
                "\t</format>\n" +
                "\t<format>\n" +
                "\t\t<id>__cw_200</id>\n" +
                "\t\t<width>200</width>\n" +
                "\t</format>\n" +
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>0</height>\n" +
                "\t<pageSetup>\n" +
                "\t\t<orientation>Landscape</orientation>\n" +
                "\t\t<pageWidth>780</pageWidth>\n" +
                "\t</pageSetup>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-206"));
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-208"));
    }

    @Test
    void mxl206_failsWhenWidthsExceedPage() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>__cw_500</id>\n" +
                "\t\t<width>500</width>\n" +
                "\t</format>\n" +
                "\t<format>\n" +
                "\t\t<id>__cw_600</id>\n" +
                "\t\t<width>600</width>\n" +
                "\t</format>\n" +
                "\t<columns><size>2</size></columns>\n" +
                "\t<height>0</height>\n" +
                "\t<pageSetup>\n" +
                "\t\t<orientation>Portrait</orientation>\n" +
                "\t\t<pageWidth>540</pageWidth>\n" +
                "\t</pageSetup>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-206"));
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-208"));
    }

    // --- MXL-207: style reference broken ---

    @Test
    void mxl207_happyPathStyleIsDefined() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>bordered</id>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>bordered</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-207"));
    }

    @Test
    void mxl207_failsOnUnknownStyleId() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>defined</id>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>missing</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-207"));
    }

    @Test
    void mxl207_failsOnUnknownLegacyColumnWidthStyleIdInCell() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<id>defined</id>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>__cw_missing</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-207"));
    }

    @Test
    void mxl207_failsOnNumericFormatIndexPastPalette() throws Exception {
        Path file = writeMxlBody(
                "\t<format/>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>2</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-207"));
    }

    @Test
    void mxl101_failsOnInvalidHorizontalAlignmentInFormatPalette() throws Exception {
        Path file = writeMxlBody(
                "\t<format>\n" +
                "\t\t<horizontalAlignment>Diagonal</horizontalAlignment>\n" +
                "\t</format>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>1</height>\n" +
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f></c></c>\n" +
                "\t</row></rowsItem>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i ->
                i.getCode().equals("MXL-101") && i.getElement().contains("/document/format[1]"));
    }

    @Test
    void mxl106_warnsOnCanonicalFontHeightAttribute() throws Exception {
        Path file = writeMxlBody(
                "\t<font faceName=\"Arial\" height=\"0\" bold=\"false\" italic=\"false\"\n" +
                "\t      underline=\"false\" strikeout=\"false\" kind=\"Absolute\" scale=\"100\"/>\n" +
                "\t<columns><size>1</size></columns>\n" +
                "\t<height>0</height>\n");
        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("MXL-106"));
    }

    private Path writeMxlBody(String body) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                body +
                "</document>\n";
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}

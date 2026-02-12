package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.SkdWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты SkdValidator: SKD-001..005, SKD-101..107.
 */
class SkdValidatorTest {

    private final SkdValidator validator = new SkdValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== Roundtrip ====================

    @Test
    void testWriterGeneratedSkdPassesValidation() throws Exception {
        SkdDsl dsl = new SkdDsl(
                List.of(new SkdDsl.DataSource("DS1", "Local")),
                List.of(new SkdDsl.DataSet("DS1", "DS1",
                        "SELECT Ref FROM Catalog.Items", null, null,
                        List.of(new SkdDsl.Field("Ref", "Ref", "Ссылка", "ref:Catalog.Items")),
                        true)),
                null, null,
                List.of(new SkdDsl.SettingsVariant("Основной", "Основной вариант", null))
        );

        Path output = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, output);

        XmlDocument doc = reader.parse(output);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Errors in writer-generated SKD: " + errors)
                .isEmpty();
    }

    // ==================== SKD-001: Root element ====================

    @Test
    void testWrongRootElement() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<WrongRoot xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-001"));
    }

    // ==================== SKD-002: Missing dataSource ====================

    @Test
    void testMissingDataSource() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-002"));
    }

    // ==================== SKD-003: Missing xsi:type on dataSet ====================

    @Test
    void testDataSetMissingXsiType() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t\t<query>SELECT 1</query>\n" +
                "\t</dataSet>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-003"));
    }

    // ==================== SKD-004: DataSetQuery missing query ====================

    @Test
    void testDataSetQueryMissingQuery() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSet>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-004"));
    }

    // ==================== SKD-101: Invalid DataSet type ====================

    @Test
    void testInvalidDataSetType() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetInvalid\">\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSet>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-101"));
    }

    // ==================== SKD-102: Invalid comparisonType ====================

    @Test
    void testInvalidComparisonType() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n" +
                "\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n" +
                "\t\t<query>SELECT 1</query>\n" +
                "\t</dataSet>\n" +
                "\t<settingsVariant>\n" +
                "\t\t<name>Main</name>\n" +
                "\t\t<settings>\n" +
                "\t\t\t<filter>\n" +
                "\t\t\t\t<item>\n" +
                "\t\t\t\t\t<leftValue>Ref</leftValue>\n" +
                "\t\t\t\t\t<comparisonType>InvalidType</comparisonType>\n" +
                "\t\t\t\t</item>\n" +
                "\t\t\t</filter>\n" +
                "\t\t</settings>\n" +
                "\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("SKD-102") && i.getMessage().contains("InvalidType"));
    }

    // ==================== SKD-106: Empty filter field ====================

    @Test
    void testEmptyFilterField() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n" +
                "\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n" +
                "\t\t<query>SELECT 1</query>\n" +
                "\t</dataSet>\n" +
                "\t<settingsVariant>\n" +
                "\t\t<name>Main</name>\n" +
                "\t\t<settings>\n" +
                "\t\t\t<selection>\n" +
                "\t\t\t\t<item>\n" +
                "\t\t\t\t\t<field></field>\n" +
                "\t\t\t\t</item>\n" +
                "\t\t\t</selection>\n" +
                "\t\t</settings>\n" +
                "\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-106"));
    }

    // ==================== Valid complete SKD ====================

    @Test
    void testValidCompleteSkd() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n" +
                "\t\t<name>DS1</name>\n" +
                "\t\t<dataSourceType>Local</dataSourceType>\n" +
                "\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n" +
                "\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n" +
                "\t\t<query>SELECT Ref FROM Catalog.Items</query>\n" +
                "\t</dataSet>\n" +
                "\t<settingsVariant>\n" +
                "\t\t<name>Main</name>\n" +
                "\t\t<settings>\n" +
                "\t\t\t<selection>\n" +
                "\t\t\t\t<item>\n" +
                "\t\t\t\t\t<field>Ref</field>\n" +
                "\t\t\t\t</item>\n" +
                "\t\t\t</selection>\n" +
                "\t\t\t<filter>\n" +
                "\t\t\t\t<item>\n" +
                "\t\t\t\t\t<leftValue>Ref</leftValue>\n" +
                "\t\t\t\t\t<comparisonType>Equal</comparisonType>\n" +
                "\t\t\t\t</item>\n" +
                "\t\t\t</filter>\n" +
                "\t\t\t<order>\n" +
                "\t\t\t\t<item>\n" +
                "\t\t\t\t\t<field>Ref</field>\n" +
                "\t\t\t\t\t<orderType>Asc</orderType>\n" +
                "\t\t\t\t</item>\n" +
                "\t\t\t</order>\n" +
                "\t\t</settings>\n" +
                "\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors).isEmpty();
    }

    // ==================== Real file ====================

    @Test
    void testRealSkdIfAvailable() throws Exception {
        Path realFile = Path.of("/workspaces/work/repos/1C Projects/DSSL UT/src/xml/Documents/ПланСборкиРазборки/Templates/СКД_СборкаКомплекты/Ext/Template.xml");
        if (!Files.exists(realFile)) return;

        XmlDocument doc = reader.parse(realFile);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Structure errors in real SKD: " + errors)
                .isEmpty();
    }

    // ==================== Utility ====================

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

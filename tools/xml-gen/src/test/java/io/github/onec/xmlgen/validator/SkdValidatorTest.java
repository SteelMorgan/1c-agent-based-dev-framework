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
    void missingDataSourceIsAllowedForEmptyDesignerSchema() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).noneMatch(i -> i.getCode().equals("SKD-002"));
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

    @Test
    void dataSetFieldNestedDataSetIsKnownFieldType() throws Exception {
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
                "\t\t<field xsi:type=\"DataSetFieldNestedDataSet\">\n" +
                "\t\t\t<name>Rows</name>\n" +
                "\t\t</field>\n" +
                "\t</dataSet>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).noneMatch(i -> i.getCode().equals("SKD-104"));
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

    // ==================== TASK-171 (Р-1): filter <left> + xsi:type ====================

    @Test
    void task171_filterItemWithLeftAndUseFalse_noSkd106() throws Exception {
        // Платформенный канон: левый операнд — <dcsset:left xsi:type="dcscor:Field">,
        // элемент со <use>false</use> — слот пользовательского отбора. До TASK-171 валидатор
        // искал <leftValue>/<field> и давал ложный SKD-106.
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\" " +
                "xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n\t\t<query>SELECT 1</query>\n\t</dataSet>\n" +
                "\t<settingsVariant>\n\t\t<dcsset:name>Main</dcsset:name>\n\t\t<settings>\n" +
                "\t\t\t<filter>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\n" +
                "\t\t\t\t\t<dcsset:use>false</dcsset:use>\n" +
                "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Контрагент</dcsset:left>\n" +
                "\t\t\t\t\t<dcsset:comparisonType>Equal</dcsset:comparisonType>\n" +
                "\t\t\t\t</dcsset:item>\n" +
                "\t\t\t</filter>\n" +
                "\t\t</settings>\n\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues =
                validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("SKD-106"));
    }

    // ==================== TASK-171 (Р-2/Р-3): selection/order xsi:type ====================

    @Test
    void task171_selectionAutoAndFolder_noSkd106() throws Exception {
        // SelectedItemAuto (поля нет) и SelectedItemFolder (контейнер) — валидны без верхнего <field>.
        // OrderItemAuto — также без поля. До TASK-171 все три давали ложный SKD-106.
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n\t\t<query>SELECT 1</query>\n\t</dataSet>\n" +
                "\t<settingsVariant>\n\t\t<dcsset:name>Main</dcsset:name>\n\t\t<settings>\n" +
                "\t\t\t<selection>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemFolder\">\n" +
                "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\n" +
                "\t\t\t\t\t\t<dcsset:field>Ref</dcsset:field>\n" +
                "\t\t\t\t\t</dcsset:item>\n" +
                "\t\t\t\t</dcsset:item>\n" +
                "\t\t\t</selection>\n" +
                "\t\t\t<order>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>\n" +
                "\t\t\t</order>\n" +
                "\t\t</settings>\n\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues =
                validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("SKD-106"));
    }

    @Test
    void task171_selectionFieldEmpty_stillReportsSkd106() throws Exception {
        // Регрессия в обратную сторону: SelectedItemField с пустым <field> по-прежнему SKD-106.
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n\t\t<query>SELECT 1</query>\n\t</dataSet>\n" +
                "\t<settingsVariant>\n\t\t<dcsset:name>Main</dcsset:name>\n\t\t<settings>\n" +
                "\t\t\t<selection>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:SelectedItemField\">\n" +
                "\t\t\t\t\t<dcsset:field></dcsset:field>\n" +
                "\t\t\t\t</dcsset:item>\n" +
                "\t\t\t</selection>\n" +
                "\t\t</settings>\n\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues =
                validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("SKD-106"));
    }

    // ==================== TASK-171 (Р-6): NotBeginsWith ====================

    @Test
    void task171_notBeginsWithComparisonType_noSkd102() throws Exception {
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\" " +
                "xmlns:dcscor=\"http://v8.1c.ru/8.1/data-composition-system/core\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n\t\t<name>DS1</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n\t\t<query>SELECT 1</query>\n\t</dataSet>\n" +
                "\t<settingsVariant>\n\t\t<dcsset:name>Main</dcsset:name>\n\t\t<settings>\n" +
                "\t\t\t<filter>\n" +
                "\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">\n" +
                "\t\t\t\t\t<dcsset:left xsi:type=\"dcscor:Field\">Код</dcsset:left>\n" +
                "\t\t\t\t\t<dcsset:comparisonType>NotBeginsWith</dcsset:comparisonType>\n" +
                "\t\t\t\t</dcsset:item>\n" +
                "\t\t\t</filter>\n" +
                "\t\t</settings>\n\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues =
                validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("SKD-102"));
    }

    // ==================== TASK-171 (Р-5): destinationDataSet ====================

    @Test
    void task171_dataSetLinkBadDestination_reportsSkd109() throws Exception {
        // С опечаткой 'destDataSet' проверка целостности назначения молчала (false negative).
        // Теперь читаем платформенный 'destinationDataSet' и ловим ссылку на несуществующий набор.
        Path file = writeXml("Template.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n" +
                "\t<dataSet xsi:type=\"DataSetQuery\">\n\t\t<name>Основной</name>\n" +
                "\t\t<dataSource>DS1</dataSource>\n\t\t<query>SELECT 1</query>\n\t</dataSet>\n" +
                "\t<dataSetLink>\n" +
                "\t\t<sourceDataSet>Основной</sourceDataSet>\n" +
                "\t\t<destinationDataSet>НетТакого</destinationDataSet>\n" +
                "\t</dataSetLink>\n" +
                "\t<settingsVariant>\n\t\t<name>Main</name>\n\t</settingsVariant>\n" +
                "</DataCompositionSchema>\n");

        List<ValidationIssue> issues =
                validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i ->
                i.getCode().equals("SKD-109") && i.getMessage().contains("НетТакого"));
    }

    // ==================== TASK-171: регрессии на грунт-труф _Демо ====================

    @Test
    void task171_realDemoSchemas_noFalseErrors() throws Exception {
        // Заведомо валидные платформенные схемы _Демо не должны давать ни одной ERROR.
        // До TASK-171 валидатор ложно валил 5 из 7 (SKD-106 на left/Auto/Folder).
        Path base = Path.of("/workspaces/work/repos/1C Projects/GBIG PAM/src/xml/Reports");
        String[] schemas = {
                "_ДемоСтатусыЗаказовПокупателей/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml",
                "_ДемоОборотноСальдоваяВедомость/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml",
                "_ДемоФайлы/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml"
        };
        boolean any = false;
        for (String rel : schemas) {
            Path f = base.resolve(rel);
            if (!Files.exists(f)) continue;
            any = true;
            List<ValidationIssue> errors = validator.validate(reader.parse(f), ValidationLevel.SEMANTIC)
                    .stream().filter(i -> i.getSeverity() == Severity.ERROR).toList();
            assertThat(errors)
                    .as("Ложные ERROR на грунт-труф " + rel + ": " + errors)
                    .isEmpty();
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(any,
                "Грунт-труф _Демо схемы недоступны (окружение без GBIG PAM)");
    }

    // ==================== Utility ====================

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.RoleWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты RoleValidator: ROLE-001..005 (структура) + ROLE-101..107 (семантика).
 */
class RoleValidatorTest {

    private final RoleValidator validator = new RoleValidator();
    private final XmlStructureReader reader = new XmlStructureReader();
    private final GenValidator genValidator = new GenValidator();

    @TempDir
    Path tempDir;

    // ==================== Roundtrip tests ====================

    @Test
    void testWriterGeneratedRightsPassValidation() throws Exception {
        // Writer → validate = 0 errors
        RoleDsl dsl = new RoleDsl(
                "TestRole", "Тестовая роль", "comment",
                false, true, false,
                List.of(
                        new RoleDsl.ObjectRights("Catalog.Items", "view", null, null),
                        new RoleDsl.ObjectRights("Document.Invoice", "edit", null, null)
                ),
                List.of(
                        new RoleDsl.RestrictionTemplate("ForObject(Modifier)",
                                "#ForObject(Modifier)")
                )
        );

        RoleWriter writer = new RoleWriter(OutputFormat.DESIGNER);
        writer.create(dsl, tempDir);

        Path rightsXml = tempDir.resolve("Roles/TestRole/Ext/Rights.xml");
        assertThat(rightsXml).exists();

        XmlDocument doc = reader.parse(rightsXml);

        // GEN checks
        List<ValidationIssue> genIssues = genValidator.validate(doc, "role", true);
        List<ValidationIssue> genErrors = genIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(genErrors)
                .as("GEN errors for writer-generated Rights.xml")
                .isEmpty();

        // Role checks
        List<ValidationIssue> roleIssues = validator.validate(doc, ValidationLevel.SEMANTIC);
        List<ValidationIssue> roleErrors = roleIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(roleErrors)
                .as("Role errors for writer-generated Rights.xml")
                .isEmpty();
    }

    @Test
    void testEdtWriterGeneratedRightsPassValidation() throws Exception {
        RoleDsl dsl = new RoleDsl(
                "EdtRole", "EDT Роль", null,
                false, true, false,
                List.of(
                        new RoleDsl.ObjectRights("Catalog.Products", null,
                                Map.of("Read", true, "View", true), null)
                ),
                null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.EDT);
        writer.create(dsl, tempDir);

        Path rightsFile = tempDir.resolve("Roles/EdtRole/Rights.rights");
        assertThat(rightsFile).exists();

        XmlDocument doc = reader.parse(rightsFile);

        List<ValidationIssue> roleIssues = validator.validate(doc, ValidationLevel.SEMANTIC);
        List<ValidationIssue> roleErrors = roleIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(roleErrors).isEmpty();
    }

    // ==================== ROLE-001: Root element + namespace ====================

    @Test
    void testMissingRootNamespace() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights>\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-001") && i.getSeverity() == Severity.ERROR);
    }

    // ==================== ROLE-002: Global flags ====================

    @Test
    void testMissingGlobalFlags() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        long role002count = issues.stream().filter(i -> i.getCode().equals("ROLE-002")).count();
        assertThat(role002count).isEqualTo(3); // 3 отсутствующих флага
    }

    // ==================== ROLE-003: Object missing name ====================

    @Test
    void testMissingObjectName() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-003") && i.getSeverity() == Severity.ERROR);
    }

    // ==================== ROLE-005: Invalid right value ====================

    @Test
    void testInvalidRightValue() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>yes</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-005") && i.getMessage().contains("yes"));
    }

    // ==================== ROLE-101: Unknown RoleRight ====================

    @Test
    void testUnknownRoleRight() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>НесуществующееПраво</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-101") && i.getMessage().contains("НесуществующееПраво"));
    }

    // ==================== ROLE-102: Unknown MDOType ====================

    @Test
    void testUnknownMdoType() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>UnknownType.Something</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-102"));
    }

    // ==================== ROLE-103: Posting for Catalog ====================

    @Test
    void testPostingForCatalog() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Posting</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-103") && i.getMessage().contains("Posting"));
    }

    // ==================== ROLE-104: Duplicate right ====================

    @Test
    void testDuplicateRight() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>false</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-104") && i.getMessage().contains("Read"));
    }

    // ==================== ROLE-105: Invalid object name format ====================

    @Test
    void testInvalidObjectNameFormat() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>JustAName</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("ROLE-105"));
    }

    @Test
    void testNestedObjectNamesNoRole105() throws Exception {
        // TASK-171 регресс (ROLE-105): вложенные имена объектов с 2-4 точками валидны и
        // встречаются в реальной выгрузке БСП. Прежняя проверка «ровно 1 точка» валила
        // 15/36 _Демо-ролей ложным ERROR. Ни одно из этих имён не должно давать ROLE-105.
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog._ДемоБанковскиеСчета.Command._ДемоБанковскиеСчета</name>\n" +
                "\t\t<right><name>View</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "\t<object>\n" +
                "\t\t<name>Task.ЗадачаИсполнителя.Command.Выполнено</name>\n" +
                "\t\t<right><name>View</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "\t<object>\n" +
                "\t\t<name>CalculationRegister._ДемоОсновныеНачисления.Recalculation.ПерерасчетОсновныхНачислений</name>\n" +
                "\t\t<right><name>View</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "\t<object>\n" +
                "\t\t<name>InformationRegister.ИсполнителиЗадач.Command.РолиИИсполнителиЗадач</name>\n" +
                "\t\t<right><name>View</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "\t<object>\n" +
                "\t\t<name>Document.Реализация.TabularSection.Товары.Attribute.Номенклатура</name>\n" +
                "\t\t<right><name>View</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).noneMatch(i -> i.getCode().equals("ROLE-105"));
    }

    @Test
    void testSimpleTwoSegmentNameNoRole105() throws Exception {
        // Простое имя <Тип>.<Имя> (ровно 1 точка) тоже остаётся валидным.
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Контрагенты</name>\n" +
                "\t\t<right><name>Read</name><value>true</value></right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).noneMatch(i -> i.getCode().equals("ROLE-105"));
    }

    // ==================== ROLE-107: Restriction template ====================

    @Test
    void testEmptyRestrictionTemplate() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<restrictionTemplate>\n" +
                "\t\t<name></name>\n" +
                "\t\t<condition></condition>\n" +
                "\t</restrictionTemplate>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues.stream().filter(i -> i.getCode().equals("ROLE-107")).count()).isEqualTo(2);
    }

    // ==================== Valid complete Rights.xml ====================

    @Test
    void testValidCompleteRightsXml() throws Exception {
        Path file = writeXml("Rights.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\" " +
                "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "xsi:type=\"Rights\" version=\"2.17\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n" +
                "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>View</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "\t<object>\n" +
                "\t\t<name>Document.Invoice</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Posting</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors).isEmpty();
    }

    // ==================== Real file from 1C project ====================

    @Test
    void testRealRightsXmlIfAvailable() throws Exception {
        Path realFile = Path.of("/workspaces/work/repos/1C Projects/DSSL UT/src/xml/Roles/ДССЛ_РаботаСКлиент360/Ext/Rights.xml");
        if (!Files.exists(realFile)) {
            return; // Пропускаем, если реальный проект недоступен
        }

        XmlDocument doc = reader.parse(realFile);

        // Структурная валидация — 0 ошибок
        List<ValidationIssue> structIssues = validator.validate(doc, ValidationLevel.STRUCTURE);
        List<ValidationIssue> structErrors = structIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(structErrors)
                .as("Structure errors in real Rights.xml: " + structErrors)
                .isEmpty();
    }

    // ==================== Utility ====================

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

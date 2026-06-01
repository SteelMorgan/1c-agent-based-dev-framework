package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.EpfWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты EpfValidator: EPF-001..006.
 */
class EpfValidatorTest {

    private final EpfValidator validator = new EpfValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== Roundtrip ====================

    @Test
    void testWriterGeneratedEpfPassesValidation() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("TestEPF", "Тестовая обработка", tempDir);

        // Находим корневой XML метаданных
        Path epfXml = tempDir.resolve("TestEPF.xml");
        assertThat(epfXml).exists();

        XmlDocument doc = reader.parse(epfXml);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Errors in writer-generated EPF: " + errors)
                .isEmpty();
    }

    // ==================== EPF-001: Missing ExternalDataProcessor ====================

    @Test
    void testMissingExternalDataProcessor() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<SomethingElse/>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-001"));
    }

    // ==================== EPF-003: Wrong ClassId ====================

    @Test
    void testWrongClassId() throws Exception {
        // TASK-171: InternalInfo — СОСЕД Properties (прямой ребёнок ExternalDataProcessor),
        // как в реальном Designer-дампе и у Николая. Прежний тест клал InternalInfo ВНУТРЬ
        // Properties — это была подгонка под баг навигации; на такой структуре EPF-003 не должен
        // (и теперь не будет) срабатывать, потому что её платформа не порождает.
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<InternalInfo>\n" +
                "\t\t\t<xr:ContainedObject>\n" +
                "\t\t\t\t<xr:ClassId>00000000-0000-0000-0000-000000000000</xr:ClassId>\n" +
                "\t\t\t\t<xr:ObjectId>11111111-1111-1111-1111-111111111111</xr:ObjectId>\n" +
                "\t\t\t</xr:ContainedObject>\n" +
                "\t\t</InternalInfo>\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-003"));
    }

    @Test
    void epf003_correctClassId_ok() throws Exception {
        // TASK-171: регресс — на корректном ClassId EPF (c3831ec8-...) EPF-003 НЕ срабатывает,
        // InternalInfo при этом — сосед Properties.
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<InternalInfo>\n" +
                "\t\t\t<xr:ContainedObject>\n" +
                "\t\t\t\t<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>\n" +
                "\t\t\t\t<xr:ObjectId>11111111-1111-1111-1111-111111111111</xr:ObjectId>\n" +
                "\t\t\t</xr:ContainedObject>\n" +
                "\t\t</InternalInfo>\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).noneMatch(i -> i.getCode().equals("EPF-003"));
    }

    @Test
    void epf003_erfClassIdOnProcessor_reported() throws Exception {
        // TASK-171: регресс на подмену вида EPF↔ERF — у обработки указан ClassId отчёта
        // (e41aff26-...). Раньше этот баг не ловился (мёртвый код навигации).
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<InternalInfo>\n" +
                "\t\t\t<xr:ContainedObject>\n" +
                "\t\t\t\t<xr:ClassId>e41aff26-25cf-4bb6-b6c1-3f478a75f374</xr:ClassId>\n" +
                "\t\t\t\t<xr:ObjectId>11111111-1111-1111-1111-111111111111</xr:ObjectId>\n" +
                "\t\t\t</xr:ContainedObject>\n" +
                "\t\t</InternalInfo>\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-003"));
    }

    // ==================== EPF-004: Missing ChildObjects ====================

    @Test
    void testMissingChildObjects() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-004"));
    }

    // ==================== EPF-005: Forms after Templates ====================

    @Test
    void testFormsAfterTemplates() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t\t<ChildObjects>\n" +
                "\t\t\t<Template>Макет1</Template>\n" +
                "\t\t\t<Form>Форма1</Form>\n" +
                "\t\t</ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-005"));
    }

    // ==================== EPF-006: Child file not exists ====================

    @Test
    void testChildFileNotExists() throws Exception {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectories(subDir);

        Path file = subDir.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t</Properties>\n" +
                "\t\t<ChildObjects>\n" +
                "\t\t\t<Form>НесуществующаяФорма</Form>\n" +
                "\t\t</ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("EPF-006") && i.getMessage().contains("НесуществующаяФорма"));
    }

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // ==================== EPF-007: Duplicate child names ====================

    @Test
    void epf007_duplicateFormNames_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties><Name>MyEpf</Name></Properties>\n" +
                "    <ChildObjects>\n" +
                "      <Form>Форма1</Form>\n" +
                "      <Form>Форма1</Form>\n" +
                "    </ChildObjects>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-007"));
    }

    // ==================== EPF-008: Identifier pattern ====================

    @Test
    void epf008_nameStartingWithDigit_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties><Name>1Epf</Name></Properties>\n" +
                "    <ChildObjects/>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-008"));
    }

    @Test
    void epf008_validRussianName_ok() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties><Name>МояОбработка</Name></Properties>\n" +
                "    <ChildObjects><Form>ФормаТовара</Form></ChildObjects>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("EPF-008"));
    }

    // ==================== EPF-010: GUID format ====================

    @Test
    void epf010_malformedUuid_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"not-a-guid\">\n" +
                "    <Properties><Name>Test</Name></Properties>\n" +
                "    <ChildObjects/>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-010"));
    }

    @Test
    void epf010_malformedClassId_reported() throws Exception {
        // TASK-171: регресс — невалидный по GUID-формату ClassId в InternalInfo (соседе Properties)
        // ловится EPF-010. Раньше навигация props.child("InternalInfo") давала null → не ловилось.
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <InternalInfo>\n" +
                "      <xr:ContainedObject>\n" +
                "        <xr:ClassId>NOT-A-GUID</xr:ClassId>\n" +
                "        <xr:ObjectId>a1b2c3d4-e5f6-4789-abcd-0123456789ab</xr:ObjectId>\n" +
                "      </xr:ContainedObject>\n" +
                "    </InternalInfo>\n" +
                "    <Properties><Name>Test</Name></Properties>\n" +
                "    <ChildObjects/>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-010")
                && i.getMessage().contains("ClassId"));
    }
}

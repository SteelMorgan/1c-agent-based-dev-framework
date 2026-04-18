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
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties>\n" +
                "\t\t\t<Name>Test</Name>\n" +
                "\t\t\t<InternalInfo>\n" +
                "\t\t\t\t<ClassId>00000000-0000-0000-0000-000000000000</ClassId>\n" +
                "\t\t\t</InternalInfo>\n" +
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
}

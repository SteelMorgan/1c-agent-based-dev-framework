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

    @Test
    void epf013_attributesContainerInsideObject_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t\t<Attributes>\n" +
                "\t\t\t<Attribute uuid=\"22222222-2222-2222-2222-222222222222\"/>\n" +
                "\t\t</Attributes>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-013")
                && i.getMessage().contains("Attributes"));
    }

    @Test
    void epf013_attributeInChildObjects_noFalsePositive() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects>\n" +
                "\t\t\t<Attribute uuid=\"22222222-2222-2222-2222-222222222222\"/>\n" +
                "\t\t</ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).noneMatch(i -> i.getCode().equals("EPF-013"));
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

    // ==================== EPF-011: посторонний потомок вне объекта (XG-04) ====================

    @Test
    void epf011_attributesBlockOutsideObject_reported() throws Exception {
        // XG-04 класс (а): блок <Attributes> стоит СНАРУЖИ </ExternalDataProcessor> — прямой
        // потомок MetaDataObject. Designer-batch падает XDTO-ошибкой, а старый валидатор давал
        // PASS. Воспроизводит структуру битого корневого XML из обхода XG-03.
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n" +
                "\t<ExternalDataProcessor uuid=\"c629f0b1-84ab-4581-8b0f-8a0180ceb530\">\n" +
                "\t\t<InternalInfo>\n" +
                "\t\t\t<xr:ContainedObject>\n" +
                "\t\t\t\t<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>\n" +
                "\t\t\t\t<xr:ObjectId>c629f0b1-84ab-4581-8b0f-8a0180ceb530</xr:ObjectId>\n" +
                "\t\t\t</xr:ContainedObject>\n" +
                "\t\t</InternalInfo>\n" +
                "\t\t<Properties><Name>биг_Тест</Name></Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute uuid=\"04b197d6-6065-40db-99f2-d255ace7a449\">\n" +
                "\t\t\t<Properties><Name>НачалоПериода</Name></Properties>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-011")
                && i.getMessage().contains("Attributes"));
    }

    @Test
    void epf011_validRoot_noFalsePositive() throws Exception {
        // Регресс: корректный корневой XML (РОВНО один потомок ExternalDataProcessor) —
        // EPF-011 НЕ срабатывает. <Attribute> ВНУТРИ ChildObjects (валидно для обработки)
        // тоже не должен триггерить EPF-011, т.к. он не прямой потомок MetaDataObject.
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>МояОбработка</Name></Properties>\n" +
                "\t\t<ChildObjects>\n" +
                "\t\t\t<Attribute uuid=\"7efb0bf4-d40c-417b-b9bc-f4a5af66ec09\">\n" +
                "\t\t\t\t<Properties><Name>Реквизит1</Name></Properties>\n" +
                "\t\t\t</Attribute>\n" +
                "\t\t</ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);
        assertThat(issues).noneMatch(i -> i.getCode().equals("EPF-011"));
    }

    // ==================== EPF-012: фантомный каталог формы на диске (XG-04) ====================

    @Test
    void epf012_formDirOnDiskNotDeclared_reported() throws Exception {
        // XG-04 класс (б): на диске лежит каталог формы <obj>/Forms/Форма, но <Form> НЕ объявлен
        // в ChildObjects. Designer при загрузке из файлов натыкается на необъявленную форму →
        // прецедент runaway-памяти. Старый валидатор такую дыру не ловил.
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        // Каталог формы на диске
        Files.createDirectories(sub.resolve("Test/Forms/Форма/Ext/Form"));
        Path file = sub.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n", StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-012")
                && i.getMessage().contains("Форма"));
    }

    @Test
    void epf012_declaredFormWithDir_noFalsePositive() throws Exception {
        // Регресс: каталог формы на диске И объявлен в ChildObjects — EPF-012 НЕ срабатывает.
        Path sub = tempDir.resolve("sub2");
        Files.createDirectories(sub);
        Files.createDirectories(sub.resolve("Test/Forms/Форма/Ext/Form"));
        Path file = sub.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects><Form>Форма</Form></ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n", StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);
        assertThat(issues).noneMatch(i -> i.getCode().equals("EPF-012"));
    }

    @Test
    void epf006_declaredFormDirWithoutMetadataFile_reported() throws Exception {
        Path sub = tempDir.resolve("missing-form-meta");
        Files.createDirectories(sub.resolve("Test/Forms/Форма/Ext"));
        Files.writeString(sub.resolve("Test/Forms/Форма/Ext/Form.xml"),
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\"/>",
                StandardCharsets.UTF_8);
        Path file = sub.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects><Form>Форма</Form></ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n", StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-006")
                && i.getMessage().contains("metadata file not found")
                && i.getMessage().contains("Форма.xml"));
    }

    @Test
    void epf012_formMetadataOnDiskNotDeclared_reported() throws Exception {
        Path sub = tempDir.resolve("phantom-form-meta");
        Files.createDirectories(sub.resolve("Test/Forms"));
        Files.writeString(sub.resolve("Test/Forms/Фантом.xml"),
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\"/>",
                StandardCharsets.UTF_8);
        Path file = sub.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects/>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n", StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-012")
                && i.getMessage().contains("metadata file")
                && i.getMessage().contains("Фантом.xml"));
    }

    @Test
    void epf015_childMetadataVersionMismatch_reported() throws Exception {
        Path sub = tempDir.resolve("version-mismatch");
        Files.createDirectories(sub.resolve("Test/Forms/Форма/Ext"));
        Files.writeString(sub.resolve("Test/Forms/Форма.xml"),
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\"/>",
                StandardCharsets.UTF_8);
        Files.writeString(sub.resolve("Test/Forms/Форма/Ext/Form.xml"),
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.20\"/>",
                StandardCharsets.UTF_8);
        Path file = sub.resolve("Test.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n" +
                "\t<ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "\t\t<Properties><Name>Test</Name></Properties>\n" +
                "\t\t<ChildObjects><Form>Форма</Form></ChildObjects>\n" +
                "\t</ExternalDataProcessor>\n" +
                "</MetaDataObject>\n", StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-015")
                && i.getMessage().contains("2.17")
                && i.getMessage().contains("expected '2.20'"));
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

    @Test
    void epf007_duplicateAttributeNames_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties><Name>MyEpf</Name></Properties>\n" +
                "    <ChildObjects>\n" +
                "      <Attribute uuid=\"11111111-1111-1111-1111-111111111111\"><Properties><Name>Параметр</Name></Properties></Attribute>\n" +
                "      <Attribute uuid=\"22222222-2222-2222-2222-222222222222\"><Properties><Name>Параметр</Name></Properties></Attribute>\n" +
                "    </ChildObjects>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-007")
                && i.getMessage().contains("Attribute")
                && i.getMessage().contains("Параметр"));
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
    void epf008_invalidTabularSectionName_reported() throws Exception {
        Path file = writeXml("Test.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalDataProcessor uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties><Name>MyEpf</Name></Properties>\n" +
                "    <ChildObjects>\n" +
                "      <TabularSection uuid=\"11111111-1111-1111-1111-111111111111\"><Properties><Name>Bad Name</Name></Properties></TabularSection>\n" +
                "    </ChildObjects>\n" +
                "  </ExternalDataProcessor>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-008")
                && i.getMessage().contains("TabularSection")
                && i.getMessage().contains("Bad Name"));
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

    @Test
    void epf014_externalReportWithProcessorDefaultForm_reported() throws Exception {
        Path file = writeXml("Report.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n" +
                "  <ExternalReport uuid=\"a1b2c3d4-e5f6-4789-abcd-0123456789ab\">\n" +
                "    <Properties>\n" +
                "      <Name>Отчет</Name>\n" +
                "      <DefaultForm>ExternalDataProcessor.Отчет.Form.Форма</DefaultForm>\n" +
                "      <MainDataCompositionSchema>ExternalDataProcessor.Отчет.Template.Схема</MainDataCompositionSchema>\n" +
                "    </Properties>\n" +
                "    <ChildObjects/>\n" +
                "  </ExternalReport>\n" +
                "</MetaDataObject>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-014")
                && i.getMessage().contains("DefaultForm"));
        assertThat(issues).anyMatch(i -> i.getCode().equals("EPF-014")
                && i.getMessage().contains("MainDataCompositionSchema"));
    }

    // ==================== TASK-171: init --type report --with-skd ====================

    @Test
    void task171_initWithSkd_createsErfWithDcsTemplate_validators0Errors() throws Exception {
        // TASK-171: флаг --with-skd создаёт ERF "из коробки" с основной схемой компоновки данных.
        // Воспроизводит канон грунт-труф (src/xml/Reports/_ДемоФайлыВспомогательный):
        // макет ОсновнаяСхемаКомпоновкиДанных (DataCompositionSchema) + ChildObjects + MainDCS.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER, true); // isReport → ERF
        writer.initWithSkd("ТестовыйОтчет", "Тестовый отчёт", tempDir);

        Path rootXml = tempDir.resolve("ТестовыйОтчет.xml");
        Path templateMeta = tempDir.resolve("ТестовыйОтчет/Templates/ОсновнаяСхемаКомпоновкиДанных.xml");
        Path templateBody = tempDir.resolve("ТестовыйОтчет/Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");

        assertThat(rootXml).exists();
        assertThat(templateMeta).exists();
        assertThat(templateBody).exists();

        // Связка: ChildObjects + MainDataCompositionSchema с префиксом ExternalReport. (D6).
        String root = Files.readString(rootXml);
        assertThat(root).contains("<ExternalReport uuid=");
        assertThat(root).contains("<Template>ОсновнаяСхемаКомпоновкиДанных</Template>");
        assertThat(root).contains(
                "<MainDataCompositionSchema>ExternalReport.ТестовыйОтчет.Template.ОсновнаяСхемаКомпоновкиДанных</MainDataCompositionSchema>");

        // Тело макета — DataCompositionSchema (корректный корень) и тип в метаданных.
        String body = Files.readString(templateBody);
        assertThat(body).contains("<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">");
        assertThat(Files.readString(templateMeta)).contains("<TemplateType>DataCompositionSchema</TemplateType>");

        // BOM на всех файлах связки (Designer-канон): ef bb bf.
        assertThat(hasUtf8Bom(rootXml)).as("BOM в корневом XML ERF").isTrue();
        assertThat(hasUtf8Bom(templateMeta)).as("BOM в метаданных макета").isTrue();
        assertThat(hasUtf8Bom(templateBody)).as("BOM в теле DataCompositionSchema").isTrue();

        // Валидатор ERF: 0 ошибок по проверкам структуры/семантики корневого XML.
        // TASK-171: EPF-006 теперь НЕ исключаем — резолв каталога макетов в EpfValidator
        // исправлен (<outputDir>/<name>/Templates вместо <outputDir>/Templates), поэтому
        // на каноничной раскладке ложного EPF-006 быть не должно. Раньше дефект стрелял
        // на ЛЮБОМ EPF/ERF с макетами (в т.ч. epf add-template) — теперь закрыт.
        XmlDocument erfDoc = reader.parse(rootXml);
        List<ValidationIssue> erfIssues = validator.validate(erfDoc, ValidationLevel.SEMANTIC);
        assertThat(erfIssues).as("EPF-006 не должен срабатывать на каноничной раскладке ERF")
                .noneMatch(i -> "EPF-006".equals(i.getCode()));
        List<ValidationIssue> erfErrors = erfIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR)
                .toList();
        assertThat(erfErrors).as("Ошибки валидатора ERF: " + erfErrors).isEmpty();

        // Валидатор СКД на теле макета: 0 ошибок.
        SkdValidator skdValidator = new SkdValidator();
        XmlDocument skdDoc = reader.parse(templateBody);
        List<ValidationIssue> skdIssues = skdValidator.validate(skdDoc, ValidationLevel.SEMANTIC);
        List<ValidationIssue> skdErrors = skdIssues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(skdErrors).as("Ошибки валидатора СКД: " + skdErrors).isEmpty();
    }

    @Test
    void task171_initWithSkd_onProcessor_throws() throws Exception {
        // --with-skd для обычной обработки (не отчёт) — внятная ошибка, не тихий no-op:
        // у EPF нет свойства MainDataCompositionSchema, привязывать схему не к чему.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER, false); // EPF, не отчёт
        assertThatThrownBy(() -> writer.initWithSkd("ОбычнаяОбработка", "Обработка", tempDir))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--with-skd");
    }

    /** Проверка UTF-8 BOM (ef bb bf) в начале файла. */
    private static boolean hasUtf8Bom(Path path) throws java.io.IOException {
        byte[] bytes = Files.readAllBytes(path);
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }
}

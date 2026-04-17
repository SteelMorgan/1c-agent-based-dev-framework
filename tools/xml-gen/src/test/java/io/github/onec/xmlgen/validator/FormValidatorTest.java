package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты FormValidator: FORM-001..008 (структура) + FORM-101..113 (семантика).
 */
class FormValidatorTest {

    private final FormValidator validator = new FormValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== FORM-001: AutoCommandBar ====================

    @Test
    void testMissingAutoCommandBar() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-001"));
    }

    @Test
    void testAutoCommandBarWrongName() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"WrongName\" id=\"-1\"/>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("FORM-001") && i.getMessage().contains("WrongName"));
    }

    // ==================== FORM-004: Duplicate ID ====================

    @Test
    void testDuplicateId() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" +
                "\t\t<InputField name=\"Field1\" id=\"1\"/>\n" +
                "\t\t<InputField name=\"Field2\" id=\"1\"/>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-004"));
    }

    // ==================== FORM-006: Missing ChildItems ====================

    @Test
    void testMissingChildItems() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-006"));
    }

    // ==================== FORM-101: Unknown element type ====================

    @Test
    void testUnknownElementType() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"Attr1\" id=\"1\"/>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems>\n" +
                "\t\t<UnknownWidget name=\"W1\" id=\"2\"/>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("FORM-101") && i.getMessage().contains("UnknownWidget"));
    }

    // ==================== FORM-102: DataPath to missing attribute ====================

    @Test
    void testDataPathToMissingAttribute() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"ExistingAttr\" id=\"1\"/>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems>\n" +
                "\t\t<InputField name=\"Field1\" id=\"2\">\n" +
                "\t\t\t<DataPath>NonExistentAttr</DataPath>\n" +
                "\t\t</InputField>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("FORM-102") && i.getMessage().contains("NonExistentAttr"));
    }

    // ==================== FORM-103: Button to missing command ====================

    @Test
    void testButtonToMissingCommand() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<Commands>\n" +
                "\t\t<Command name=\"RealCmd\" id=\"1\"/>\n" +
                "\t</Commands>\n" +
                "\t<ChildItems>\n" +
                "\t\t<Button name=\"Btn1\" id=\"2\">\n" +
                "\t\t\t<CommandName>Form.Command.FakeCmd</CommandName>\n" +
                "\t\t</Button>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("FORM-103") && i.getMessage().contains("FakeCmd"));
    }

    // ==================== FORM-108: Invalid AllowedLength ====================

    @Test
    void testInvalidAllowedLength() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"Str\" id=\"1\">\n" +
                "\t\t\t<Type>\n" +
                "\t\t\t\t<StringQualifiers>\n" +
                "\t\t\t\t\t<AllowedLength>Invalid</AllowedLength>\n" +
                "\t\t\t\t</StringQualifiers>\n" +
                "\t\t\t</Type>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i ->
                i.getCode().equals("FORM-108") && i.getMessage().contains("Invalid"));
    }

    // ==================== Valid complete form ====================

    @Test
    void testValidCompleteForm() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"StringAttr\" id=\"1\">\n" +
                "\t\t\t<Type>\n" +
                "\t\t\t\t<StringQualifiers>\n" +
                "\t\t\t\t\t<Length>100</Length>\n" +
                "\t\t\t\t\t<AllowedLength>Variable</AllowedLength>\n" +
                "\t\t\t\t</StringQualifiers>\n" +
                "\t\t\t</Type>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<Commands>\n" +
                "\t\t<Command name=\"DoAction\" id=\"2\">\n" +
                "\t\t\t<Action>ВыполнитьДействие</Action>\n" +
                "\t\t</Command>\n" +
                "\t</Commands>\n" +
                "\t<ChildItems>\n" +
                "\t\t<InputField name=\"Field1\" id=\"3\">\n" +
                "\t\t\t<DataPath>StringAttr</DataPath>\n" +
                "\t\t</InputField>\n" +
                "\t\t<Button name=\"Btn1\" id=\"4\">\n" +
                "\t\t\t<CommandName>Form.Command.DoAction</CommandName>\n" +
                "\t\t</Button>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors).isEmpty();
    }

    // ==================== Real file ====================

    @Test
    void testRealFormIfAvailable() throws Exception {
        Path realFile = Path.of("/workspaces/work/repos/1C Projects/DSSL UT/src/xml/Documents/DSSL_ПланыПродажПоПартнерскойПрограмме/Forms/ФормаДокумента/Ext/Form.xml");
        if (!Files.exists(realFile)) return;

        XmlDocument doc = reader.parse(realFile);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.STRUCTURE);

        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors)
                .as("Structure errors in real Form.xml: " + errors)
                .isEmpty();
    }

    // ==================== FORM-115 / FORM-116 (err-form-xml-tooling-fix) ====================

    /** UT-FORM-115-01 — fixture failed Form.xml DSSL_Коммуникатор: правило срабатывает на >=6 точках. */
    @Test
    void testFailedFixtureTriggersForm115() throws Exception {
        XmlDocument doc = reader.parse(loadFixture("dssl-kommunikator-failed.xml"));
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> form115 = issues.stream()
                .filter(i -> "FORM-115".equals(i.getCode()))
                .toList();

        // failed Form.xml содержит 8 атрибутов с <ValueType> (id=100..107) +
        // 11 Column с <ValueType> (id=200..202, 300..307) → минимум 18 срабатываний.
        assertThat(form115)
                .as("FORM-115 must trigger on failed fixture")
                .hasSizeGreaterThanOrEqualTo(6);

        assertThat(form115).allSatisfy(i ->
                assertThat(i.getSeverity()).isEqualTo(Severity.ERROR));

        // Сообщения должны содержать имена ключевых атрибутов и колонок.
        assertThat(form115).anyMatch(i -> i.getMessage().contains("СообщенияБезОбъектаКонтекста"));
        assertThat(form115).anyMatch(i -> i.getMessage().contains("СообщенияПоПериоду"));
        assertThat(form115).anyMatch(i -> i.getMessage().contains("Аккаунт"));
    }

    /** UT-FORM-115-02 — canonical Эталон 1: правило НЕ срабатывает (нет ложных). */
    @Test
    void testCanonicalVyborkontragentaDoesNotTriggerForm115() throws Exception {
        XmlDocument doc = reader.parse(loadFixture("valid-vyborkontragenta.xml"));
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues)
                .as("FORM-115 must NOT trigger on canonical fixture (ВыборКонтрагентаУВЦ)")
                .noneMatch(i -> "FORM-115".equals(i.getCode()));
    }

    /** UT-FORM-116-01 — fixture failed Form.xml: cross-check срабатывает на 2 таблицах. */
    @Test
    void testFailedFixtureTriggersForm116() throws Exception {
        XmlDocument doc = reader.parse(loadFixture("dssl-kommunikator-failed.xml"));
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        List<ValidationIssue> form116 = issues.stream()
                .filter(i -> "FORM-116".equals(i.getCode()))
                .toList();

        // Failed Form.xml: <Table id=11 DataPath=СообщенияБезОбъектаКонтекста> (3 колонки)
        // и <Table id=25 DataPath=СообщенияПоПериоду> (8 колонок) — обе без ChildItems.
        assertThat(form116)
                .as("FORM-116 must trigger on both UI tables of failed fixture")
                .hasSizeGreaterThanOrEqualTo(2);

        assertThat(form116).allSatisfy(i ->
                assertThat(i.getSeverity()).isEqualTo(Severity.ERROR));

        // Сообщения должны содержать имена обеих UI-таблиц.
        assertThat(form116).anyMatch(i ->
                i.getMessage().contains("СообщенияБезОбъектаКонтекста"));
        assertThat(form116).anyMatch(i ->
                i.getMessage().contains("СообщенияПоПериоду"));

        // Сообщения должны перечислять конкретные отсутствующие колонки.
        assertThat(form116).anyMatch(i -> i.getMessage().contains("Аккаунт"));
        assertThat(form116).anyMatch(i -> i.getMessage().contains("Отметка"));
    }

    /** UT-FORM-116-02 — canonical Эталон 1: правило НЕ срабатывает (есть InputField для Питомец). */
    @Test
    void testCanonicalVyborkontragentaDoesNotTriggerForm116() throws Exception {
        XmlDocument doc = reader.parse(loadFixture("valid-vyborkontragenta.xml"));
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues)
                .as("FORM-116 must NOT trigger on canonical fixture (ВыборКонтрагентаУВЦ)")
                .noneMatch(i -> "FORM-116".equals(i.getCode()));
    }

    /** UT-FORM-116-03 — canonical Эталон 3: ColumnGroup InCell + CheckBoxField/LabelField внутри. */
    @Test
    void testCanonicalObjectRegistrationColumnGroupHandled() throws Exception {
        XmlDocument doc = reader.parse(loadFixture("valid-objectregistration.xml"));
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(issues)
                .as("FORM-116 must NOT trigger on canonical fixture with ColumnGroup InCell"
                        + " (ObjectRegistrationNodes — CheckBoxField/LabelField внутри ColumnGroup)")
                .noneMatch(i -> "FORM-116".equals(i.getCode()));
    }

    private Path loadFixture(String name) {
        try {
            return Path.of(Objects.requireNonNull(
                    getClass().getResource("/forms/" + name),
                    "Fixture not found in test resources: /forms/" + name).toURI());
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve fixture URI: " + name, e);
        }
    }

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    // ==================== FORM-115: outer v8:Type wrapper ====================

    @Test
    void form115_outerV8TypePrefix_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"ОбъектКонтекста\" id=\"1\">\n" +
                "\t\t\t<v8:Type>\n" +
                "\t\t\t\t<v8:Type>cfg:CatalogRef.Товары</v8:Type>\n" +
                "\t\t\t</v8:Type>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-115")
                && i.getMessage().contains("outer"));
    }

    @Test
    void form115_canonicalOuterType_ok() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"X\" id=\"1\">\n" +
                "\t\t\t<Type>\n" +
                "\t\t\t\t<v8:Type>cfg:CatalogRef.X</v8:Type>\n" +
                "\t\t\t</Type>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-115"));
    }

    // ==================== FORM-117: Companions ====================

    @Test
    void form117_inputFieldMissingContextMenu_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" +
                "\t\t<InputField name=\"F\" id=\"1\">\n" +
                "\t\t\t<ExtendedTooltip name=\"FExtended\" id=\"2\"/>\n" +
                "\t\t</InputField>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-117")
                && i.getMessage().contains("ContextMenu"));
    }

    @Test
    void form117_tableMissingAutoCommandBar_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" +
                "\t\t<Table name=\"T\" id=\"1\">\n" +
                "\t\t\t<ContextMenu name=\"TContext\" id=\"2\"/>\n" +
                "\t\t</Table>\n" +
                "\t</ChildItems>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-117")
                && i.getMessage().contains("AutoCommandBar"));
    }

    // ==================== FORM-118: Event handler non-empty ====================

    @Test
    void form118_emptyFormEventHandler_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Events>\n" +
                "\t\t<Event name=\"OnOpen\"></Event>\n" +
                "\t</Events>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-118"));
    }

    // ==================== FORM-119: MainAttribute count ≤ 1 ====================

    @Test
    void form119_multipleMainAttributes_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"A\" id=\"1\"><MainAttribute>true</MainAttribute></Attribute>\n" +
                "\t\t<Attribute name=\"B\" id=\"2\"><MainAttribute>true</MainAttribute></Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-119"));
    }

    @Test
    void form119_singleMainAttribute_ok() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"A\" id=\"1\"><MainAttribute>true</MainAttribute></Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-119"));
    }

    // ==================== FORM-120: multilingual Title ====================

    @Test
    void form120_plainTextTitle_reported() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"A\" id=\"1\"><Title>Just text</Title></Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-120"));
    }

    @Test
    void form120_multilingualTitle_ok() throws Exception {
        Path file = writeXml("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"A\" id=\"1\">\n" +
                "\t\t\t<Title><v8:item><v8:lang>ru</v8:lang><v8:content>Hi</v8:content></v8:item></Title>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "\t<ChildItems/>\n" +
                "</Form>\n");
        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-120"));
    }
}

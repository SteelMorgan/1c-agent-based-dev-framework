package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

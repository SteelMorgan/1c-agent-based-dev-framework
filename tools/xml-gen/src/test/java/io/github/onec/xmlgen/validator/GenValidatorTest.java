package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты GenValidator: GEN-001..006.
 */
class GenValidatorTest {

    private final GenValidator genValidator = new GenValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ===== GEN-003: BOM policy =====

    @Test
    void testBomExpectedButMissing() throws Exception {
        // Designer Role без BOM → ERROR
        Path file = writeXml("Rights.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", true);

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-003") && i.getSeverity() == Severity.ERROR);
    }

    @Test
    void testBomPresentWhenExpected() throws Exception {
        Path file = writeXml("Rights.xml", true,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", true);

        assertThat(issues).noneMatch(i -> i.getCode().equals("GEN-003"));
    }

    @Test
    void testBomUnexpected() throws Exception {
        // SKD с BOM → WARNING (SKD обычно без BOM)
        Path file = writeXml("Template.xml", true,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "skd", false);

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-003") && i.getSeverity() == Severity.WARNING);
    }

    // ===== GEN-004: Root element =====

    @Test
    void testCorrectRootElement() throws Exception {
        Path file = writeXml("Rights.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", false);

        assertThat(issues).noneMatch(i -> i.getCode().equals("GEN-004"));
    }

    @Test
    void testWrongRootElement() throws Exception {
        // Ожидаем "Rights" для role, но получаем "Form"
        Path file = writeXml("wrong.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", false);

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-004") && i.getSeverity() == Severity.ERROR);
    }

    // ===== GEN-005: Namespace =====

    @Test
    void testCorrectNamespace() throws Exception {
        Path file = writeXml("Form.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "form", false);

        assertThat(issues).noneMatch(i -> i.getCode().equals("GEN-005"));
    }

    @Test
    void testWrongNamespace() throws Exception {
        Path file = writeXml("Rights.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://wrong.namespace\"/>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", false);

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-005") && i.getSeverity() == Severity.ERROR);
    }

    // ===== GEN-006: UUID format =====

    @Test
    void testValidUuid() throws Exception {
        Path file = writeXml("epf.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ExternalDataProcessor xmlns=\"http://v8.1c.ru/8.3/MDClasses\" " +
                "uuid=\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\">\n" +
                "\t<Name>Test</Name>\n" +
                "</ExternalDataProcessor>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "epf", false);

        assertThat(issues).noneMatch(i -> i.getCode().equals("GEN-006"));
    }

    @Test
    void testInvalidUuid() throws Exception {
        Path file = writeXml("epf.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ExternalDataProcessor xmlns=\"http://v8.1c.ru/8.3/MDClasses\" " +
                "uuid=\"not-a-valid-uuid\">\n" +
                "\t<Name>Test</Name>\n" +
                "</ExternalDataProcessor>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "epf", false);

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-006") && i.getSeverity() == Severity.WARNING);
    }

    // ===== All GEN for valid Role XML =====

    @Test
    void testValidRoleXmlNoIssues() throws Exception {
        Path file = writeXml("Rights.xml", true,
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
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        List<ValidationIssue> issues = genValidator.validate(doc, "role", true);

        assertThat(issues).isEmpty();
    }

    // ===== Utility =====

    private Path writeXml(String filename, boolean withBom, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        try (OutputStream os = Files.newOutputStream(file)) {
            if (withBom) {
                os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            }
            os.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}

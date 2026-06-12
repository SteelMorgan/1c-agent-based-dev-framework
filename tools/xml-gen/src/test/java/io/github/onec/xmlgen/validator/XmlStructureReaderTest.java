package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты XmlStructureReader: парсинг, BOM-детекция, номера строк, ошибки.
 */
class XmlStructureReaderTest {

    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ===== Well-formed XML =====

    @Test
    void testParseSimpleXml() throws Exception {
        Path file = writeXml("simple.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<setForNewObjects>false</setForNewObjects>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);

        assertThat(doc.getRootElement()).isEqualTo("Rights");
        assertThat(doc.getRootNamespace()).isEqualTo("http://v8.1c.ru/8.2/roles");
        assertThat(doc.isHasBom()).isFalse();
        assertThat(doc.getChildren()).hasSize(1);

        XmlNode child = doc.child("setForNewObjects");
        assertThat(child).isNotNull();
        assertThat(child.getText()).isEqualTo("false");
    }

    @Test
    void testBomDetection() throws Exception {
        Path file = writeXml("with-bom.xml", true,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\">\n" +
                "\t<AutoCommandBar/>\n" +
                "</Form>\n");

        XmlDocument doc = reader.parse(file);

        assertThat(doc.isHasBom()).isTrue();
        assertThat(doc.getRootElement()).isEqualTo("Form");
    }

    @Test
    void testNoBomDetection() throws Exception {
        Path file = writeXml("no-bom.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\"/>\n");

        XmlDocument doc = reader.parse(file);
        assertThat(doc.isHasBom()).isFalse();
    }

    // ===== Attributes =====

    @Test
    void testAttributesParsing() throws Exception {
        Path file = writeXml("attrs.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<ExternalDataProcessor xmlns=\"http://v8.1c.ru/8.3/MDClasses\" " +
                "uuid=\"a1b2c3d4-e5f6-7890-abcd-ef1234567890\">\n" +
                "\t<Name>Test</Name>\n" +
                "</ExternalDataProcessor>\n");

        XmlDocument doc = reader.parse(file);
        assertThat(doc.getRootAttributes().get("uuid")).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    }

    @Test
    void testPrefixedAttributes() throws Exception {
        Path file = writeXml("prefixed.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<dataSets>\n" +
                "\t\t<dataSet xsi:type=\"DataSetQuery\">\n" +
                "\t\t\t<name>DS1</name>\n" +
                "\t\t</dataSet>\n" +
                "\t</dataSets>\n" +
                "</DataCompositionSchema>\n");

        XmlDocument doc = reader.parse(file);
        XmlNode dataSets = doc.child("dataSets");
        assertThat(dataSets).isNotNull();

        XmlNode dataSet = dataSets.child("dataSet");
        assertThat(dataSet).isNotNull();
        assertThat(dataSet.attr("xsi:type")).isEqualTo("DataSetQuery");
    }

    // ===== Line numbers =====

    @Test
    void testLineNumbers() throws Exception {
        Path file = writeXml("lines.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +    // line 1
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +  // line 2
                "\t<setForNewObjects>false</setForNewObjects>\n" +   // line 3
                "\t<object>\n" +                                      // line 4
                "\t\t<name>Catalog.Test</name>\n" +                  // line 5
                "\t</object>\n" +                                     // line 6
                "</Rights>\n");                                       // line 7

        XmlDocument doc = reader.parse(file);
        // Root element starts at line 2
        assertThat(doc.getRoot().getLine()).isEqualTo(2);

        XmlNode setForNew = doc.child("setForNewObjects");
        assertThat(setForNew).isNotNull();
        assertThat(setForNew.getLine()).isEqualTo(3);

        XmlNode object = doc.child("object");
        assertThat(object).isNotNull();
        assertThat(object.getLine()).isEqualTo(4);
    }

    // ===== Nested structure =====

    @Test
    void testNestedChildren() throws Exception {
        Path file = writeXml("nested.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\">\n" +
                "\t<object>\n" +
                "\t\t<name>Catalog.Items</name>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Read</name>\n" +
                "\t\t\t<value>true</value>\n" +
                "\t\t</right>\n" +
                "\t\t<right>\n" +
                "\t\t\t<name>Update</name>\n" +
                "\t\t\t<value>false</value>\n" +
                "\t\t</right>\n" +
                "\t</object>\n" +
                "</Rights>\n");

        XmlDocument doc = reader.parse(file);
        XmlNode object = doc.child("object");
        assertThat(object).isNotNull();
        assertThat(object.childText("name")).isEqualTo("Catalog.Items");
        assertThat(object.children("right")).hasSize(2);

        XmlNode firstRight = object.children("right").get(0);
        assertThat(firstRight.childText("name")).isEqualTo("Read");
        assertThat(firstRight.childText("value")).isEqualTo("true");
    }

    // ===== Helper methods =====

    @Test
    void testChildTextReturnsNull() throws Exception {
        Path file = writeXml("no-child.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\"/>\n");

        XmlDocument doc = reader.parse(file);
        assertThat(doc.getRoot().childText("nonexistent")).isNull();
        assertThat(doc.getRoot().hasChild("nonexistent")).isFalse();
    }

    // ===== Error handling =====

    @Test
    void testMalformedXmlThrowsParseException() throws Exception {
        Path file = writeXml("malformed.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights>\n" +
                "\t<unclosed>\n");

        assertThatThrownBy(() -> reader.parse(file))
                .isInstanceOf(XmlStructureReader.XmlParseException.class);
    }

    @Test
    void testMultipleRootElementsThrowsParseException() throws Exception {
        Path file = writeXml("two-roots.xml", false,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\"/>\n" +
                "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\"/>\n");

        assertThatThrownBy(() -> reader.parse(file))
                .isInstanceOf(XmlStructureReader.XmlParseException.class)
                .hasMessageContaining("line");
    }

    @Test
    void testEmptyFileThrowsParseException() throws Exception {
        Path file = tempDir.resolve("empty.xml");
        Files.writeString(file, "");

        assertThatThrownBy(() -> reader.parse(file))
                .isInstanceOf(XmlStructureReader.XmlParseException.class);
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

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// XG-107 [28.09.2026]
/** Правка Form.xml через XmlDocument не должна менять ничего вне правленого узла. */
class XmlDocumentWriterXg107Test {

    @TempDir
    Path tempDir;

    private static final Path RES = Path.of("src/test/resources/xg113");

    @ParameterizedTest
    @ValueSource(strings = {"BASELINE.xml", "canon_addition_dup.xml", "canon_hyperlink.xml",
            "canon_excluded_cmd.xml", "canon_items_currentdata.xml"})
    void designerFormRoundTripIsByteIdentical(String file) throws Exception {
        Path copy = tempDir.resolve(file);
        Files.copy(RES.resolve(file), copy);
        byte[] before = Files.readAllBytes(copy);
        XmlDocument doc = new XmlStructureReader().parse(copy);
        new XmlDocumentWriter().write(doc, copy);
        assertThat(Files.readAllBytes(copy)).isEqualTo(before);
    }

    @Test
    void removeElementTouchesOnlyItsLines() throws Exception {
        String crlf = "\r\n";
        String form = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + crlf
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.20\">" + crlf
                + "\t<ChildItems>" + crlf
                + "\t\t<InputField name=\"А\" id=\"1\"/>" + crlf
                + "\t\t<InputField name=\"Б\" id=\"2\"/>" + crlf
                + "\t</ChildItems>" + crlf
                + "\t<Attributes>" + crlf
                + "\t\t<Attribute name=\"Настройки\" id=\"3\">" + crlf
                + "\t\t\t<Settings xmlns:d4p1=\"http://v8.1c.ru/8.2/data/chart\" xsi:type=\"d4p1:Chart\">" + crlf
                + "\t\t\t\t<d4p1:labelsDelimiter>, </d4p1:labelsDelimiter>" + crlf
                + "\t\t\t\t<d4p1:text>\"x\" 'y'" + "\n" + "z</d4p1:text>" + crlf
                + "\t\t\t</Settings>" + crlf
                + "\t\t</Attribute>" + crlf
                + "\t</Attributes>" + crlf
                + "</Form>";
        Path file = tempDir.resolve("Form.xml");
        Files.write(file, form.getBytes(StandardCharsets.UTF_8));
        XmlDocument doc = new XmlStructureReader().parse(file);
        XmlNode items = doc.getRoot().child("ChildItems");
        items.getChildren().removeIf(n -> "Б".equals(n.attr("name")));
        new XmlDocumentWriter().write(doc, file);
        String expected = form.replace("\t\t<InputField name=\"Б\" id=\"2\"/>" + crlf, "");
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(expected);
    }
}

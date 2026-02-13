package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlDocumentWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testRoundtrip() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<root xmlns:foo=\"http://foo\" id=\"1\">\n" +
                "\t<child name=\"test\"/>\n" +
                "\t<foo:bar>text</foo:bar>\n" +
                "</root>\n";
        
        Path input = tempDir.resolve("input.xml");
        Files.writeString(input, xml);

        XmlStructureReader reader = new XmlStructureReader();
        XmlDocument doc = reader.parse(input);

        XmlDocumentWriter writer = new XmlDocumentWriter();
        Path output = tempDir.resolve("output.xml");
        writer.write(doc, output);

        // Parse output again and compare trees
        XmlDocument doc2 = reader.parse(output);
        
        assertEquals(doc.getRoot().getName(), doc2.getRoot().getName());
        assertEquals(doc.getRoot().getChildren().size(), doc2.getRoot().getChildren().size());
        assertEquals("text", doc2.getRoot().getChildren().get(1).getText());
        assertEquals("bar", doc2.getRoot().getChildren().get(1).getName());
        assertEquals("foo", doc2.getRoot().getChildren().get(1).getPrefix());
    }

    @Test
    void testIndent() throws IOException {
        XmlNode root = XmlNode.builder()
                .name("root")
                .addChild(XmlNode.builder().name("child").build())
                .build();
        XmlDocument doc = new XmlDocument(null, false, "root", "", Map.of(), root.getChildren(), root);
        
        XmlDocumentWriter writer = new XmlDocumentWriter();
        Path output = tempDir.resolve("indent.xml");
        writer.write(doc, output);
        
        String content = Files.readString(output);
        // Expect indentation for child
        assertTrue(content.contains("\t<child/>") || content.contains("\t<child"));
    }
}

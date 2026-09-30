package io.github.onec.xmlgen.form.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormEditDsl;
import io.github.onec.xmlgen.editor.FormEditor;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** XG-127: form edit --json attributeColumns — колонка в существующий ValueTable-реквизит. */
class FormEditAttributeColumnsTest {

    @TempDir
    Path tempDir;

    private XmlDocument document;
    private FormEditor editor;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        XmlNode col1 = XmlNode.builder().name("Column").attribute("name", "A").attribute("id", "1").build();
        XmlNode col3 = XmlNode.builder().name("Column").attribute("name", "B").attribute("id", "3").build();
        XmlNode columns = XmlNode.builder().name("Columns").addChild(col1).addChild(col3).build();
        XmlNode table = XmlNode.builder().name("Attribute").attribute("name", "Tab").attribute("id", "5")
                .addChild(columns).build();
        XmlNode bare = XmlNode.builder().name("Attribute").attribute("name", "Bare").attribute("id", "6").build();
        XmlNode root = XmlNode.builder().name("Form")
                .addChild(XmlNode.builder().name("Attributes").addChild(table).addChild(bare).build())
                .build();
        document = new XmlDocument(null, false, null, "Form", "", Map.of(), root.getChildren(), root);
        editor = new FormEditor(document);
    }

    private void apply(String json) throws Exception {
        new FormEditApplier(editor).apply(mapper.readValue(json, FormEditDsl.class));
    }

    private XmlNode attr(String name) {
        return document.getRoot().child("Attributes").children("Attribute").stream()
                .filter(a -> name.equals(a.attr("name"))).findFirst().orElseThrow();
    }

    @Test
    void addsColumnToExistingValueTable_withMaxIdPlusOne() throws Exception {
        apply("{\"attributeColumns\":[{\"attribute\":\"Tab\",\"name\":\"Reason\",\"type\":\"string(1024)\",\"title\":\"Причина\"}]}");
        List<XmlNode> cols = attr("Tab").child("Columns").children("Column");
        assertEquals(3, cols.size());
        XmlNode added = cols.get(2);
        assertEquals("Reason", added.attr("name"));
        assertEquals("4", added.attr("id"));
        assertEquals("Причина", added.child("Title").child("item").childText("content"));
        XmlNode type = added.child("Type");
        assertEquals("xs:string", type.childText("Type"));
        assertEquals("1024", type.child("StringQualifiers").childText("Length"));
    }

    @Test
    void createsColumnsNodeWhenMissing_titleDefaultsToName() throws Exception {
        apply("{\"attributeColumns\":[{\"attribute\":\"Bare\",\"name\":\"X\",\"type\":\"xs:boolean\"}]}");
        XmlNode added = attr("Bare").child("Columns").children("Column").get(0);
        assertEquals("1", added.attr("id"));
        assertEquals("X", added.child("Title").child("item").childText("content"));
    }

    @Test
    void duplicateColumnName_fails() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                apply("{\"attributeColumns\":[{\"attribute\":\"Tab\",\"name\":\"B\",\"type\":\"xs:string\"}]}"));
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    void missingAttribute_fails() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                apply("{\"attributeColumns\":[{\"attribute\":\"Nope\",\"name\":\"C\",\"type\":\"xs:string\"}]}"));
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void cli_roundTrip_preservesBomCrlfAndTabs() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" version=\"2.17\">\r\n"
                + "\t<Attributes>\r\n"
                + "\t\t<Attribute name=\"Tab\" id=\"1\">\r\n"
                + "\t\t\t<Type>\r\n\t\t\t\t<v8:Type>v8:ValueTable</v8:Type>\r\n\t\t\t</Type>\r\n"
                + "\t\t\t<Columns>\r\n"
                + "\t\t\t\t<Column name=\"A\" id=\"2\">\r\n"
                + "\t\t\t\t\t<Type>\r\n\t\t\t\t\t\t<v8:Type>xs:boolean</v8:Type>\r\n\t\t\t\t\t</Type>\r\n"
                + "\t\t\t\t</Column>\r\n"
                + "\t\t\t</Columns>\r\n"
                + "\t\t</Attribute>\r\n"
                + "\t</Attributes>\r\n"
                + "</Form>";
        Path form = tempDir.resolve("Form.xml");
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, 3);
        System.arraycopy(body, 0, all, 3, body.length);
        Files.write(form, all);
        Path spec = tempDir.resolve("spec.json");
        Files.writeString(spec, "{\"attributeColumns\":[{\"attribute\":\"Tab\",\"name\":\"R\",\"type\":\"string(10)\"}]}");

        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process p = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                "io.github.onec.xmlgen.cli.Main", "form", "edit", form.toString(), "--json", spec.toString())
                .directory(tempDir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), out);

        byte[] res = Files.readAllBytes(form);
        assertEquals((byte) 0xEF, res[0]);
        assertEquals((byte) 0xBB, res[1]);
        assertEquals((byte) 0xBF, res[2]);
        String text = new String(res, 3, res.length - 3, StandardCharsets.UTF_8);
        assertFalse(text.replace("\r\n", "").contains("\n"), "bare LF found");
        assertTrue(text.contains("\t\t\t\t<Column name=\"R\" id=\"3\">\r\n"), text);
        assertTrue(text.indexOf("name=\"R\"") > text.indexOf("name=\"A\""));
    }
}

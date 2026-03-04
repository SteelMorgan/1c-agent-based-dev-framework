package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EpfEditorTest {

    private XmlDocument document;
    private EpfEditor editor;

    @BeforeEach
    void setUp() {
        XmlNode root = XmlNode.builder()
                .name("ExternalDataProcessor")
                .build();
        document = new XmlDocument(null, false, null, "ExternalDataProcessor", "", Map.of(), root.getChildren(), root);
        editor = new EpfEditor(document);
    }

    @Test
    void testAddAttribute() {
        editor.addAttribute("Employee", "CatalogRef.Employees", "Сотрудник");

        XmlNode attrs = document.getRoot().child("Attributes");
        assertNotNull(attrs, "Attributes container should be created");
        assertFalse(attrs.getChildren().isEmpty());

        XmlNode attr = attrs.getChildren().get(0);
        assertNotNull(attr.attr("uuid"), "UUID should be generated");
        assertTrue(attr.hasChild("InternalInfo"));
        assertTrue(attr.hasChild("Properties"));

        XmlNode props = attr.child("Properties");
        assertEquals("Employee", props.childText("Name"));

        XmlNode synonym = props.child("Synonym");
        assertNotNull(synonym);
        // v8:item → item (localName)
        XmlNode v8Item = synonym.child("item");
        assertNotNull(v8Item);
        assertEquals("Сотрудник", v8Item.childText("content"));

        XmlNode type = props.child("Type");
        assertNotNull(type);
    }

    @Test
    void testAddAttributeDefaultSynonym() {
        editor.addAttribute("MyAttr", "xs:string", null);

        XmlNode props = document.getRoot().child("Attributes")
                .getChildren().get(0).child("Properties");
        XmlNode synonym = props.child("Synonym").child("item");
        // Default synonym = name
        assertEquals("MyAttr", synonym.childText("content"));
    }

    @Test
    void testAddTabularSection() {
        editor.addTabularSection("Goods", "Товары");

        XmlNode ts = document.getRoot().child("TabularSections");
        assertNotNull(ts, "TabularSections container should be created");
        assertFalse(ts.getChildren().isEmpty());

        XmlNode section = ts.getChildren().get(0);
        assertNotNull(section.attr("uuid"), "UUID should be generated");
        assertTrue(section.hasChild("Properties"));

        XmlNode props = section.child("Properties");
        assertEquals("Goods", props.childText("Name"));
    }

    @Test
    void testMultipleAttributes() {
        editor.addAttribute("Attr1", "xs:string", null);
        editor.addAttribute("Attr2", "xs:boolean", null);

        XmlNode attrs = document.getRoot().child("Attributes");
        assertEquals(2, attrs.getChildren().size());

        // UUIDs should be different
        String uuid1 = attrs.getChildren().get(0).attr("uuid");
        String uuid2 = attrs.getChildren().get(1).attr("uuid");
        assertNotEquals(uuid1, uuid2, "UUIDs should be unique");
    }
}

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

        assertNull(document.getRoot().child("Attributes"), "EPF attributes must not be in a synthetic Attributes container");
        XmlNode childObjects = document.getRoot().child("ChildObjects");
        assertNotNull(childObjects, "ChildObjects container should be created");
        assertFalse(childObjects.getChildren().isEmpty());

        XmlNode attr = childObjects.getChildren().get(0);
        assertEquals("Attribute", attr.getName());
        assertNotNull(attr.attr("uuid"), "UUID should be generated");
        assertFalse(attr.hasChild("InternalInfo"));
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
        assertEquals("Items", props.childText("ChoiceFoldersAndItems"));
    }

    @Test
    void testAddAttributeDefaultSynonym() {
        editor.addAttribute("MyAttr", "xs:string", null);

        XmlNode props = document.getRoot().child("ChildObjects")
                .getChildren().get(0).child("Properties");
        XmlNode synonym = props.child("Synonym").child("item");
        // Default synonym = name
        assertEquals("MyAttr", synonym.childText("content"));
    }

    @Test
    void testAddTabularSection() {
        editor.addTabularSection("Goods", "Товары");

        assertNull(document.getRoot().child("TabularSections"), "EPF tabular sections must not be in a synthetic TabularSections container");
        XmlNode childObjects = document.getRoot().child("ChildObjects");
        assertNotNull(childObjects, "ChildObjects container should be created");
        assertFalse(childObjects.getChildren().isEmpty());

        XmlNode section = childObjects.getChildren().get(0);
        assertEquals("TabularSection", section.getName());
        assertNotNull(section.attr("uuid"), "UUID should be generated");
        assertTrue(section.hasChild("InternalInfo"));
        assertTrue(section.hasChild("Properties"));
        assertTrue(section.hasChild("ChildObjects"));

        XmlNode props = section.child("Properties");
        assertEquals("Goods", props.childText("Name"));
        assertEquals("5", props.childText("LineNumberLength"));
        assertTrue(props.hasChild("StandardAttributes"));
    }

    @Test
    void testMultipleAttributes() {
        editor.addAttribute("Attr1", "xs:string", null);
        editor.addAttribute("Attr2", "xs:boolean", null);

        XmlNode childObjects = document.getRoot().child("ChildObjects");
        assertEquals(2, childObjects.getChildren().size());

        // UUIDs should be different
        String uuid1 = childObjects.getChildren().get(0).attr("uuid");
        String uuid2 = childObjects.getChildren().get(1).attr("uuid");
        assertNotEquals(uuid1, uuid2, "UUIDs should be unique");
    }

    @Test
    void addChildObjects_preservesEpfOrder() {
        XmlNode childObjects = XmlNode.createElement("ChildObjects", Map.of());
        XmlNode form = XmlNode.createElement("Form", Map.of());
        form.setText("Форма");
        childObjects.addChild(form);
        document.getRoot().addChild(childObjects);

        editor.addTabularSection("Rows", "Строки");
        editor.addAttribute("Attr", "String", "Реквизит");

        assertEquals("Attribute", childObjects.getChildren().get(0).getName());
        assertEquals("TabularSection", childObjects.getChildren().get(1).getName());
        assertEquals("Form", childObjects.getChildren().get(2).getName());
    }
}

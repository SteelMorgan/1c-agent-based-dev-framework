package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormEditorTest {

    private XmlDocument document;
    private FormEditor editor;

    @BeforeEach
    void setUp() {
        XmlNode root = XmlNode.builder()
                .name("Form")
                .addChild(XmlNode.builder().name("Attributes").build())
                .addChild(XmlNode.builder().name("Commands").build())
                .addChild(XmlNode.builder().name("ChildItems").build())
                .build();
        document = new XmlDocument(null, false, "Form", "", Map.of(), root.getChildren(), root);
        editor = new FormEditor(document);
    }

    @Test
    void testAddAttribute() {
        editor.addAttribute("MyAttr", "xs:string");
        XmlNode attrs = document.getRoot().child("Attributes");
        assertNotNull(attrs);
        assertFalse(attrs.getChildren().isEmpty());
        
        XmlNode attr = attrs.getChildren().get(0);
        assertEquals("MyAttr", attr.attr("name"));
        assertEquals("1", attr.attr("id")); 
        
        assertTrue(attr.hasChild("Type"));
        assertTrue(attr.hasChild("Title"));
    }

    @Test
    void testAddElement() {
        editor.addElement("InputField", "MyField", "MyAttr", null, null);
        XmlNode childItems = document.getRoot().child("ChildItems");
        XmlNode field = childItems.child("InputField");
        assertNotNull(field);
        assertEquals("MyField", field.attr("name"));
        assertTrue(field.hasChild("ContextMenu"));
        assertTrue(field.hasChild("ExtendedTooltip"));
    }
    
    @Test
    void testMoveElement() {
        editor.addElement("InputField", "Field1", null, null, null);
        editor.addElement("InputField", "Field2", null, null, null);
        
        // Initial order: Field1, Field2
        XmlNode childItems = document.getRoot().child("ChildItems");
        assertEquals("Field1", childItems.getChildren().get(0).attr("name"));
        
        editor.moveElement("Field1", "Field2", null, null);
        // New order: Field2, Field1
        assertEquals("Field2", childItems.getChildren().get(0).attr("name"));
        assertEquals("Field1", childItems.getChildren().get(1).attr("name"));
    }
    
    @Test
    void testRemoveElement() {
        editor.addElement("InputField", "Field1", null, null, null);
        editor.removeElement("Field1");
        XmlNode childItems = document.getRoot().child("ChildItems");
        assertTrue(childItems.getChildren().isEmpty());
    }
}

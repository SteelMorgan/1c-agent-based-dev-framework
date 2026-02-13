package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoleEditorTest {

    private XmlDocument document;
    private RoleEditor editor;

    @BeforeEach
    void setUp() {
        XmlNode root = XmlNode.builder()
                .name("Rights")
                .build();
        document = new XmlDocument(null, false, "Rights", "", Map.of(), root.getChildren(), root);
        editor = new RoleEditor(document);
    }

    @Test
    void testAddObject() {
        editor.addObject("Catalog.Items", List.of("Read", "View"));
        
        List<XmlNode> objects = document.getRoot().children("object");
        assertFalse(objects.isEmpty());
        
        XmlNode obj = objects.get(0);
        assertEquals("Catalog.Items", obj.childText("name"));
        assertEquals(2, obj.children("right").size());
    }

    @Test
    void testAddRight() {
        editor.addObject("Catalog.Items", List.of("Read"));
        editor.addRight("Catalog.Items", "Update", "true");
        
        XmlNode obj = document.getRoot().children("object").get(0);
        assertEquals(2, obj.children("right").size());
        
        XmlNode update = obj.children("right").get(1);
        assertEquals("Update", update.childText("name"));
        assertEquals("true", update.childText("value"));
        
        // Change existing
        editor.addRight("Catalog.Items", "Read", "false");
        XmlNode read = obj.children("right").get(0);
        assertEquals("false", read.childText("value"));
    }
}

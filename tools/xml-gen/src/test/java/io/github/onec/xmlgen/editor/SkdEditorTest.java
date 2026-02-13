package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkdEditorTest {

    private XmlDocument document;
    private SkdEditor editor;

    @BeforeEach
    void setUp() {
        // Simulate a DataCompositionSchema with one DataSet
        XmlNode dataSet = XmlNode.builder()
                .name("dataSet")
                .addChild(XmlNode.builder().name("name").appendText("MainDS").build())
                .build();
        XmlNode root = XmlNode.builder()
                .name("DataCompositionSchema")
                .addChild(dataSet)
                .build();
        document = new XmlDocument(null, false, null, "DataCompositionSchema", "", Map.of(), root.getChildren(), root);
        editor = new SkdEditor(document);
    }

    @Test
    void testAddParameter() {
        editor.addParameter("Period", "Период", "xs:dateTime");

        XmlNode param = document.getRoot().child("parameter");
        assertNotNull(param, "Parameter should be added to root");
        assertEquals("Period", param.childText("name"));

        XmlNode title = param.child("title");
        assertNotNull(title);
        // v8:item → item (localName)
        XmlNode v8Item = title.child("item");
        assertNotNull(v8Item);
        assertEquals("Период", v8Item.childText("content"));

        XmlNode valueType = param.child("valueType");
        assertNotNull(valueType);
        assertEquals("xs:dateTime", valueType.childText("Type"));
    }

    @Test
    void testAddParameterDefaults() {
        editor.addParameter("MyParam", null, null);

        XmlNode param = document.getRoot().child("parameter");
        // Default title = name
        XmlNode v8Item = param.child("title").child("item");
        assertEquals("MyParam", v8Item.childText("content"));
        // Default type = xs:string
        assertEquals("xs:string", param.child("valueType").childText("Type"));
    }

    @Test
    void testAddField() {
        editor.addField("MainDS", "ItemRef", "Items.Ref", "Номенклатура");

        XmlNode dataSet = document.getRoot().children("dataSet").get(0);
        XmlNode fields = dataSet.child("fields");
        assertNotNull(fields, "fields container should be created in dataset");

        XmlNode field = fields.getChildren().get(0);
        assertEquals("DataSetFieldField", field.attr("xsi:type"));
        assertEquals("Items.Ref", field.childText("dataPath"));
        assertEquals("ItemRef", field.childText("field"));

        XmlNode title = field.child("title");
        assertNotNull(title);
        assertEquals("v8:LocalStringType", title.attr("xsi:type"));
    }

    @Test
    void testAddFieldToNonExistentDataSet() {
        assertThrows(IllegalArgumentException.class,
                () -> editor.addField("NonExistent", "f", "p", "t"),
                "Should throw when dataset not found");
    }

    @Test
    void testAddMultipleFields() {
        editor.addField("MainDS", "Field1", "Path1", null);
        editor.addField("MainDS", "Field2", "Path2", null);

        XmlNode fields = document.getRoot().children("dataSet").get(0).child("fields");
        assertEquals(2, fields.getChildren().size());
    }
}

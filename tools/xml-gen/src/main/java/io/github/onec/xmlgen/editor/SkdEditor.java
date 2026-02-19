package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.Map;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;
import static io.github.onec.xmlgen.editor.EditorUtils.findOrCreateChild;

public class SkdEditor {
    private final XmlDocument document;

    public SkdEditor(XmlDocument document) {
        this.document = document;
    }

    public void addParameter(String name, String title, String type) {
        XmlNode root = document.getRoot(); // DataCompositionSchema
        
        // Parameter logic usually involves checking if it exists.
        // Assuming append.
        
        XmlNode param = createNode("parameter");
        
        XmlNode nameNode = createNode("name");
        nameNode.setText(name);
        param.addChild(nameNode);
        
        XmlNode titleNode = createNode("title");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(title != null ? title : name);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        titleNode.addChild(v8Item);
        param.addChild(titleNode);
        
        XmlNode typeNode = createNode("valueType");
        XmlNode v8Type = createNode("v8:Type");
        v8Type.setText(type != null ? type : "xs:string");
        typeNode.addChild(v8Type);
        param.addChild(typeNode);
        
        root.addChild(param);
    }
    
    public void addField(String datasetName, String name, String path, String title) {
        // Find DataSet
        XmlNode dataSet = findDataSet(datasetName);
        if (dataSet == null) {
            throw new IllegalArgumentException("DataSet not found: " + datasetName);
        }
        
        XmlNode fields = findOrCreateChild(dataSet, "fields");
        
        XmlNode field = createNode("field");
        field.setAttribute("xsi:type", "DataSetFieldField"); 
        
        XmlNode dataPathNode = createNode("dataPath");
        dataPathNode.setText(path);
        field.addChild(dataPathNode);
        
        XmlNode fieldNode = createNode("field");
        fieldNode.setText(name);
        field.addChild(fieldNode);
        
        XmlNode titleNode = createNode("title");
        titleNode.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(title != null ? title : name);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        titleNode.addChild(v8Item);
        field.addChild(titleNode);
        
        fields.addChild(field);
    }

    private XmlNode findDataSet(String name) {
        // <dataSet><name>MySet</name>...
        return document.getRoot().children("dataSet").stream()
                .filter(ds -> name.equals(ds.childText("name")))
                .findFirst()
                .orElse(null);
    }
}

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.Map;
import java.util.UUID;

public class EpfEditor {
    private final XmlDocument document;

    public EpfEditor(XmlDocument document) {
        this.document = document;
    }

    public void addAttribute(String name, String type, String synonym) {
        XmlNode root = document.getRoot();
        XmlNode attributes = findOrCreateChild(root, "Attributes");
        
        XmlNode attr = XmlNode.createElement("Attribute", Map.of("uuid", UUID.randomUUID().toString()));
        
        addProperties(attr, name, synonym, type);
        attributes.addChild(attr);
    }

    public void addTabularSection(String name, String synonym) {
        XmlNode root = document.getRoot();
        XmlNode ts = findOrCreateChild(root, "TabularSections");
        
        XmlNode section = XmlNode.createElement("TabularSection", Map.of("uuid", UUID.randomUUID().toString()));
        addProperties(section, name, synonym, null);
        
        ts.addChild(section);
    }
    
    private void addProperties(XmlNode parent, String name, String synonym, String type) {
        // InternalInfo usually present but empty or specific
        parent.addChild(XmlNode.createElement("InternalInfo", Map.of())); 
        
        // Properties
        XmlNode props = XmlNode.createElement("Properties", Map.of());
        
        // Name
        XmlNode nameNode = XmlNode.createElement("Name", Map.of());
        nameNode.setText(name);
        props.addChild(nameNode);
        
        // Synonym
        XmlNode synNode = XmlNode.createElement("Synonym", Map.of());
        XmlNode v8Item = createNode("v8:item");
        
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        v8Item.addChild(v8Lang);
        
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(synonym != null ? synonym : name);
        v8Item.addChild(v8Content);
        
        synNode.addChild(v8Item);
        props.addChild(synNode);
        
        // Type
        if (type != null) {
            XmlNode typeNode = XmlNode.createElement("Type", Map.of());
            XmlNode v8Type = createNode("v8:Type");
            v8Type.setText(type);
            typeNode.addChild(v8Type);
            props.addChild(typeNode);
        }
        
        parent.addChild(props);
    }

    private XmlNode findOrCreateChild(XmlNode parent, String name) {
        return parent.getChildren().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    XmlNode node = XmlNode.createElement(name, Map.of());
                    parent.addChild(node);
                    return node;
                });
    }

    private XmlNode createNode(String name) {
        String prefix = "";
        String localName = name;
        if (name.contains(":")) {
            String[] parts = name.split(":");
            prefix = parts[0];
            localName = parts[1];
        }
        return XmlNode.builder().name(localName).prefix(prefix).build();
    }
}

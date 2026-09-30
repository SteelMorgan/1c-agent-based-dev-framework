package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RoleEditor {
    private final XmlDocument document;

    public RoleEditor(XmlDocument document) {
        this.document = document;
    }

    public void addObject(String objectName, List<String> rights) {
        // Check if object exists
        XmlNode object = findObject(objectName);
        if (object != null) {
            throw new IllegalArgumentException("Object already exists: " + objectName);
        }

        object = XmlNode.createElement("object", Map.of());
        
        XmlNode nameNode = XmlNode.createElement("name", Map.of());
        nameNode.setText(objectName);
        object.addChild(nameNode);

        for (String rightName : rights) {
            addRightNode(object, rightName, "true");
        }

        insertObjectBeforeRestrictionTemplates(object);
    }

    public void addRight(String objectName, String rightName, String value) {
        XmlNode object = findObject(objectName);
        if (object == null) {
            throw new IllegalArgumentException("Object not found: " + objectName);
        }

        Optional<XmlNode> existingRight = object.children("right").stream()
                .filter(r -> rightName.equals(r.childText("name")))
                .findFirst();

        if (existingRight.isPresent()) {
            XmlNode valNode = existingRight.get().child("value");
            if (valNode != null) {
                valNode.setText(value);
            } else {
                 valNode = XmlNode.createElement("value", Map.of());
                 valNode.setText(value);
                 existingRight.get().addChild(valNode);
            }
        } else {
            addRightNode(object, rightName, value);
        }
    }

    private void addRightNode(XmlNode object, String name, String value) {
        XmlNode right = XmlNode.createElement("right", Map.of());
        
        XmlNode nameNode = XmlNode.createElement("name", Map.of());
        nameNode.setText(name);
        right.addChild(nameNode);

        XmlNode valueNode = XmlNode.createElement("value", Map.of());
        valueNode.setText(value);
        right.addChild(valueNode);

        object.addChild(right);
    }

    private void insertObjectBeforeRestrictionTemplates(XmlNode object) {
        List<XmlNode> rootChildren = document.getRoot().getChildren();
        for (int i = 0; i < rootChildren.size(); i++) {
            if ("restrictionTemplate".equals(rootChildren.get(i).getName())) {
                rootChildren.add(i, object);
                return;
            }
        }
        document.getRoot().addChild(object);
    }

    private XmlNode findObject(String name) {
        return document.getRoot().children("object").stream()
                .filter(o -> name.equals(o.childText("name")))
                .findFirst()
                .orElse(null);
    }
}

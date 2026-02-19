package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.*;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;
import static io.github.onec.xmlgen.editor.EditorUtils.findOrCreateChild;

public class FormEditor {

    private final XmlDocument document;
    private final IdAllocator idAllocator;

    public FormEditor(XmlDocument document) {
        this.document = document;
        this.idAllocator = new IdAllocator(document);
    }

    // --- Attributes ---

    public void addAttribute(String name, String type) {
        XmlNode attributes = findOrCreateChild(document.getRoot(), "Attributes");

        XmlNode attr = createNode("Attribute");
        attr.setAttribute("name", name);
        attr.setAttribute("id", idAllocator.nextId());

        XmlNode title = createNode("Title");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(name); // Default title = name

        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        title.addChild(v8Item);
        attr.addChild(title);

        XmlNode typeNode = createNode("Type");
        XmlNode v8Type = createNode("v8:Type");
        v8Type.setText(type);
        typeNode.addChild(v8Type);
        attr.addChild(typeNode);

        attributes.addChild(attr);
    }

    // --- Commands ---

    public void addCommand(String name, String titleText, String action) {
        XmlNode commands = findOrCreateChild(document.getRoot(), "Commands");

        XmlNode cmd = createNode("Command");
        cmd.setAttribute("name", name);
        cmd.setAttribute("id", idAllocator.nextId());

        XmlNode title = createNode("Title");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(titleText != null ? titleText : name);
        
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        title.addChild(v8Item);
        cmd.addChild(title);

        if (action != null) {
            XmlNode actionNode = createNode("Action");
            actionNode.setText(action);
            cmd.addChild(actionNode);
        }

        commands.addChild(cmd);
    }

    // --- Elements ---

    public void addElement(String type, String name, String dataPath, String parentName, String afterName) {
        XmlNode parent = null;
        if (parentName != null) {
            parent = findElement(parentName);
            if (parent == null) {
                 throw new IllegalArgumentException("Parent element not found: " + parentName);
            }
        } else {
            // Default parent is ChildItems (root container)
            parent = findOrCreateChild(document.getRoot(), "ChildItems");
        }

        XmlNode element = createNode(type); // e.g. "InputField", "Button"
        element.setAttribute("name", name);
        element.setAttribute("id", idAllocator.nextId());

        if (dataPath != null) {
            XmlNode dataPathNode = createNode("DataPath");
            dataPathNode.setText(dataPath);
            element.addChild(dataPathNode);
        }

        // Auto ContextMenu & ExtendedTooltip
        XmlNode contextMenu = createNode("ContextMenu");
        contextMenu.setAttribute("name", name + "ContextMenu");
        contextMenu.setAttribute("id", idAllocator.nextId());
        element.addChild(contextMenu);

        XmlNode extTooltip = createNode("ExtendedTooltip");
        extTooltip.setAttribute("name", name + "ExtendedTooltip");
        extTooltip.setAttribute("id", idAllocator.nextId());
        element.addChild(extTooltip);

        // Insert logic
        if (afterName != null) {
             insertAfter(parent, element, afterName);
        } else {
             parent.addChild(element);
        }
    }

    public void removeElement(String name) {
        XmlNode childItems = document.getRoot().child("ChildItems");
        if (childItems != null) {
            removeElementRecursive(childItems, name);
        }
    }

    public void moveElement(String name, String afterName, String beforeName, String intoName) {
        // Find element to move
        XmlNode element = findElement(name);
        if (element == null) throw new IllegalArgumentException("Element not found: " + name);
        
        XmlNode oldParent = findParentOf(document.getRoot(), element);
        if (oldParent == null) throw new IllegalStateException("Orphan element: " + name);

        // Find new parent and position BEFORE detaching to avoid index shifts
        if (intoName != null) {
            XmlNode newParent = findElement(intoName);
            if (newParent == null) throw new IllegalArgumentException("Target parent not found: " + intoName);
            oldParent.getChildren().remove(element);
            newParent.addChild(element);
        } else if (afterName != null) {
            XmlNode sibling = findElement(afterName);
            if (sibling == null) throw new IllegalArgumentException("Target sibling not found: " + afterName);
            XmlNode newParent = findParentOf(document.getRoot(), sibling);
            int siblingIndex = newParent.getChildren().indexOf(sibling);
            oldParent.getChildren().remove(element);
            // Recalc index after removal (if same parent, sibling may have shifted)
            int insertIndex = newParent.getChildren().indexOf(sibling);
            newParent.getChildren().add(insertIndex + 1, element);
        } else if (beforeName != null) {
            XmlNode sibling = findElement(beforeName);
            if (sibling == null) throw new IllegalArgumentException("Target sibling not found: " + beforeName);
            XmlNode newParent = findParentOf(document.getRoot(), sibling);
            oldParent.getChildren().remove(element);
            // Recalc index after removal
            int insertIndex = newParent.getChildren().indexOf(sibling);
            newParent.getChildren().add(insertIndex, element);
        } else {
            // Move to root ChildItems
            oldParent.getChildren().remove(element);
            XmlNode newParent = findOrCreateChild(document.getRoot(), "ChildItems");
            newParent.addChild(element);
        }
    }

    // --- Helpers ---

    private XmlNode findElement(String name) {
         XmlNode childItems = document.getRoot().child("ChildItems");
         if (childItems == null) return null;
         return findElementRecursive(childItems, name);
    }
    
    private XmlNode findElementRecursive(XmlNode root, String name) {
        if (name.equals(root.attr("name"))) return root;
        for (XmlNode child : root.getChildren()) {
             XmlNode found = findElementRecursive(child, name);
             if (found != null) return found;
        }
        return null;
    }

    private XmlNode findParentOf(XmlNode root, XmlNode target) {
        if (root.getChildren().contains(target)) return root;
        for (XmlNode child : root.getChildren()) {
            XmlNode found = findParentOf(child, target);
            if (found != null) return found;
        }
        return null;
    }

    private void removeElementRecursive(XmlNode root, String name) {
        root.getChildren().removeIf(c -> name.equals(c.attr("name")));
        // Iterate over a copy to avoid issues if list implementation changes
        for (XmlNode child : new ArrayList<>(root.getChildren())) {
            removeElementRecursive(child, name);
        }
    }

    private void insertAfter(XmlNode parent, XmlNode element, String afterName) {
        int index = -1;
        for (int i = 0; i < parent.getChildren().size(); i++) {
            if (afterName.equals(parent.getChildren().get(i).attr("name"))) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            parent.getChildren().add(index + 1, element);
        } else {
            parent.addChild(element);
        }
    }
}

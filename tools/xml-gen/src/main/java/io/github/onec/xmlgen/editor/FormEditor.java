package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.form.edit.CompanionKind;
import io.github.onec.xmlgen.form.edit.FormElementKind;
import io.github.onec.xmlgen.form.edit.FormTypeEmitter;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.*;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;
import static io.github.onec.xmlgen.editor.EditorUtils.findOrCreateChild;

public class FormEditor {

    /** Нижний порог ID в режиме расширения — параллельно Python form-edit. */
    public static final int EXTENSION_ID_FLOOR = 1_000_000;

    private final XmlDocument document;
    /** Единый аллокатор для обратной совместимости (возвращается {@link #getIdAllocator()}). */
    private final IdAllocator idAllocator;
    private final IdAllocator attributeIds;
    private final IdAllocator commandIds;
    private final IdAllocator elementIds;
    private final FormTypeEmitter typeEmitter = new FormTypeEmitter();
    private final boolean isExtension;

    public FormEditor(XmlDocument document) {
        this.document = document;
        this.idAllocator = new IdAllocator(document);

        XmlNode root = document.getRoot();
        this.attributeIds = new IdAllocator();
        this.commandIds = new IdAllocator();
        this.elementIds = new IdAllocator();

        if (root != null) {
            XmlNode attributes = root.child("Attributes");
            if (attributes != null) attributeIds.scan(attributes);

            XmlNode commands = root.child("Commands");
            if (commands != null) commandIds.scan(commands);

            XmlNode childItems = root.child("ChildItems");
            if (childItems != null) elementIds.scan(childItems);

            XmlNode autoCommandBar = root.child("AutoCommandBar");
            if (autoCommandBar != null) elementIds.scan(autoCommandBar);
        }

        this.isExtension = root != null && root.child("BaseForm") != null;
        if (isExtension) {
            attributeIds.bumpFloor(EXTENSION_ID_FLOOR);
            commandIds.bumpFloor(EXTENSION_ID_FLOOR);
            elementIds.bumpFloor(EXTENSION_ID_FLOOR);
        }
    }

    public IdAllocator getIdAllocator() {
        return idAllocator;
    }

    public XmlDocument getDocument() {
        return document;
    }

    public boolean isExtension() {
        return isExtension;
    }

    // --- Attributes ---

    /**
     * Минимальная форма: добавить атрибут с именем и простым XML-типом.
     * Типовая строка может быть raw XML-типом ({@code xs:string}) или DSL
     * ({@code string(100)}, {@code decimal(10,2)}, {@code CatalogRef.Товары}) —
     * второе проходит через {@link FormTypeEmitter}.
     */
    public void addAttribute(String name, String type) {
        addAttribute(name, null, type, null, null, null);
    }

    /**
     * Расширенная форма: добавить атрибут с type DSL, main/savedData флагами и колонками
     * (для ValueTable/ValueTree).
     */
    public void addAttribute(String name, String titleText, String type, Boolean main,
                             Boolean savedData, List<FormDsl.Column> columns) {
        XmlNode attributes = findOrCreateChild(document.getRoot(), "Attributes");

        XmlNode attr = createNode("Attribute");
        attr.setAttribute("name", name);
        attr.setAttribute("id", attributeIds.nextId());

        attr.addChild(multilingualTitle(titleText != null ? titleText : name));
        attr.addChild(emitType(type));

        if (Boolean.TRUE.equals(main)) {
            XmlNode flag = createNode("MainAttribute");
            flag.setText("true");
            attr.addChild(flag);
        }
        if (Boolean.TRUE.equals(savedData)) {
            XmlNode flag = createNode("SavedData");
            flag.setText("true");
            attr.addChild(flag);
        }

        if (columns != null && !columns.isEmpty()) {
            XmlNode columnsNode = createNode("Columns");
            for (FormDsl.Column col : columns) {
                XmlNode c = createNode("Column");
                c.setAttribute("name", col.getName());
                c.setAttribute("id", attributeIds.nextId());
                c.addChild(multilingualTitle(col.getTitle() != null ? col.getTitle() : col.getName()));
                c.addChild(emitType(col.getType()));
                columnsNode.addChild(c);
            }
            attr.addChild(columnsNode);
        }

        attributes.addChild(attr);
    }

    private XmlNode multilingualTitle(String text) {
        XmlNode title = createNode("Title");
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(text);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        title.addChild(v8Item);
        return title;
    }

    /**
     * Эмитит {@code <Type>} через DSL-парсер. Для обратной совместимости: если
     * {@code type} выглядит как raw XML-тип с префиксом ({@code xs:*}, {@code v8:*},
     * {@code cfg:*}), эмитим его как единственный {@code <v8:Type>} без квалификаторов.
     */
    private XmlNode emitType(String type) {
        if (type != null && (type.startsWith("xs:") || type.startsWith("v8:")
                || type.startsWith("cfg:") || type.startsWith("v8ui:")
                || type.startsWith("dcsset:") || type.startsWith("dcscor:")
                || type.startsWith("dcssch:") || type.startsWith("mxl:"))) {
            XmlNode t = createNode("Type");
            XmlNode v8Type = createNode("v8:Type");
            v8Type.setText(type);
            t.addChild(v8Type);
            return t;
        }
        return typeEmitter.emit(type);
    }

    // --- Commands ---

    public void addCommand(String name, String titleText, String action) {
        XmlNode commands = findOrCreateChild(document.getRoot(), "Commands");

        XmlNode cmd = createNode("Command");
        cmd.setAttribute("name", name);
        cmd.setAttribute("id", commandIds.nextId());

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

    /**
     * Добавить UI-элемент формы с автоматическим набором companion-элементов.
     *
     * <p>{@code type} может быть XML-тегом ({@code InputField}, {@code Table}) или
     * JSON-ключом DSL ({@code input}, {@code table}) — оба разрешаются через
     * {@link FormElementKind#resolve(String)}. Если kind не распознан, используется
     * минимальный набор companion'ов (ContextMenu + ExtendedTooltip) для совместимости.</p>
     */
    public void addElement(String type, String name, String dataPath, String parentName, String afterName) {
        XmlNode parent;
        if (parentName != null) {
            parent = findElement(parentName);
            if (parent == null) {
                 throw new IllegalArgumentException("Parent element not found: " + parentName);
            }
        } else {
            parent = findOrCreateChild(document.getRoot(), "ChildItems");
        }

        FormElementKind kind = FormElementKind.resolve(type);
        String xmlTag = kind != null ? kind.getXmlTag() : type;

        XmlNode element = createNode(xmlTag);
        element.setAttribute("name", name);
        element.setAttribute("id", elementIds.nextId());

        if (dataPath != null) {
            XmlNode dataPathNode = createNode("DataPath");
            dataPathNode.setText(dataPath);
            element.addChild(dataPathNode);
        }

        // Companion-элементы согласно типу; для неизвестного kind — минимальный набор.
        List<CompanionKind> companions = kind != null
                ? kind.getCompanions()
                : List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP);
        for (CompanionKind c : companions) {
            XmlNode companion = createNode(c.getXmlTag());
            companion.setAttribute("name", c.nameFor(name));
            companion.setAttribute("id", elementIds.nextId());
            element.addChild(companion);
        }

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

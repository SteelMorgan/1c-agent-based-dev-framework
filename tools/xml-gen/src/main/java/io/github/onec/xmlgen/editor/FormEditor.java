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
        addAttribute(name, titleText, type, main, savedData, columns, null, null);
    }

    /**
     * Full form attribute emission used by {@code form edit --json}. Mirrors the
     * compile path for flags shared by {@link FormDsl.Attribute}.
     */
    public void addAttribute(String name, String titleText, String type, Boolean main,
                             Boolean savedData, List<FormDsl.Column> columns,
                             String fillChecking, String useAlwaysField) {
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
        if (fillChecking != null && !fillChecking.isBlank()) {
            XmlNode flag = createNode("FillChecking");
            flag.setText(fillChecking);
            attr.addChild(flag);
        }
        if (useAlwaysField != null && !useAlwaysField.isBlank()) {
            XmlNode useAlways = createNode("UseAlways");
            XmlNode field = createNode("Field");
            field.setText(useAlwaysField);
            useAlways.addChild(field);
            attr.addChild(useAlways);
        }

        if (columns != null && !columns.isEmpty()) {
            XmlNode columnsNode = createNode("Columns");
            int columnId = 1;
            for (FormDsl.Column col : columns) {
                XmlNode c = createNode("Column");
                c.setAttribute("name", col.getName());
                c.setAttribute("id", String.valueOf(columnId++));
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
        addCommand(name, titleText, action, null, null, null, null);
    }

    public void addCommand(String name, String titleText, String action, String tooltip,
                           String shortcut, String picture, String representation) {
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

        if (tooltip != null) {
            cmd.addChild(multilingual("ToolTip", tooltip));
        }

        if (picture != null && !picture.isBlank()) {
            cmd.addChild(pictureRef(picture));
        }

        if (action != null) {
            XmlNode actionNode = createNode("Action");
            actionNode.setText(action);
            cmd.addChild(actionNode);
        }

        if (shortcut != null && !shortcut.isBlank()) {
            XmlNode shortcutNode = createNode("Shortcut");
            shortcutNode.setText(shortcut);
            cmd.addChild(shortcutNode);
        }

        if (representation != null && !representation.isBlank()) {
            XmlNode representationNode = createNode("Representation");
            representationNode.setText(representation);
            cmd.addChild(representationNode);
        }

        commands.addChild(cmd);
    }

    private XmlNode multilingual(String elementName, String text) {
        XmlNode node = createNode(elementName);
        XmlNode v8Item = createNode("v8:item");
        XmlNode v8Lang = createNode("v8:lang");
        v8Lang.setText("ru");
        XmlNode v8Content = createNode("v8:content");
        v8Content.setText(text);
        v8Item.addChild(v8Lang);
        v8Item.addChild(v8Content);
        node.addChild(v8Item);
        return node;
    }

    private XmlNode pictureRef(String ref) {
        XmlNode picture = createNode("Picture");
        XmlNode xrRef = createNode("xr:Ref");
        xrRef.setText(ref);
        XmlNode transparent = createNode("xr:LoadTransparent");
        transparent.setText("true");
        picture.addChild(xrRef);
        picture.addChild(transparent);
        return picture;
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
        addElement(type, name, dataPath, parentName, afterName, null);
    }

    //++agent TASK-174 [05.06.2026 00:00:00]
    // XG-02: перегрузка с привязкой команды. Прежде CommandName умел писать только
    // compile-путь (FormWriter.writeButton), а edit-путь (FormEditor) игнорировал
    // command → кнопка, добавленная через `form edit`, оставалась без CommandName и не
    // вызывала команду. Формат значения совпадает с compile-путём: Form.Command.<имя>.
    public void addElement(String type, String name, String dataPath, String parentName,
                           String afterName, String command) {
        addElement(type, name, dataPath, parentName, afterName, null, command);
    }

    public void addElement(String type, String name, String dataPath, String parentName,
                           String afterName, String beforeName, String command) {
        addElement(type, name, null, dataPath, parentName, afterName, beforeName, command);
    }

    //++agent TASK-174 [16.06.2026 00:00:00]
    // XG-56: перегрузка с titleText. Прежде edit-путь (FormEditor.addElement) НЕ умел
    // эмитить <Title> у создаваемого элемента (в частности у контейнерной UsualGroup) —
    // ключ title из DSL молча игнорировался. Compile-путь (FormWriter.writeUsualGroup/
    // writeInputField) Title умеет. Класс тот же, что XG-14/XG-15/XG-41: edit-путь не
    // эмитит канонический под-элемент, который умеет compile-путь. Используем тот же
    // helper multilingualTitle, что и addAttribute/addCommand.
    public void addElement(String type, String name, String titleText, String dataPath,
                           String parentName, String afterName, String beforeName, String command) {
        if (afterName != null && beforeName != null) {
            throw new IllegalArgumentException("Use either after or before, not both");
        }

        String normalizedType = "radio".equalsIgnoreCase(type) ? "RadioButtonField" : type;
        FormElementKind kind = FormElementKind.resolve(normalizedType);

        // XG-15: контейнеры (UsualGroup, Pages, Page, CommandBar, Popup, Table) обязаны
        // хранить дочерние элементы в <ChildItems>, а не непосредственно в своём узле.
        // При parentName != null ищем родительский элемент и добавляем в его <ChildItems>.
        // Аналогично compile-пути (FormWriter.writeUsualGroup/writePages/...) и канону
        // дизайнера (биг_УборщикТестовыхДанных/Forms/Форма/Ext/Form.xml, строки 14-40).
        XmlNode parent;
        if (parentName != null) {
            XmlNode parentElement = findElement(parentName);
            if (parentElement == null) {
                throw new IllegalArgumentException("Parent element not found: " + parentName);
            }
            // Для контейнерных типов добавляем в <ChildItems>; иначе напрямую в родителя
            if (isContainerElement(parentElement)) {
                parent = findOrCreateChild(parentElement, "ChildItems");
            } else {
                parent = parentElement;
            }
        } else {
            parent = findOrCreateChild(document.getRoot(), "ChildItems");
        }

        String xmlTag = kind != null ? kind.getXmlTag() : normalizedType;

        XmlNode element = createNode(xmlTag);
        element.setAttribute("name", name);
        element.setAttribute("id", elementIds.nextId());

        // XG-14: Button должен иметь <Type>UsualButton</Type> как ПЕРВЫЙ дочерний тег.
        // Без него Designer-batch при /LoadExternalDataProcessorOrReportFromFiles молча
        // отбрасывает кнопку → Элементы.ИмяКнопки не существует в модуле формы.
        // Канон: биг_УборщикТестовыхДанных/Forms/Форма/Ext/Form.xml строки 45-54.
        if (kind == FormElementKind.BUTTON) {
            XmlNode typeNode = createNode("Type");
            typeNode.setText("UsualButton");
            element.addChild(typeNode);
        }

        if (dataPath != null) {
            XmlNode dataPathNode = createNode("DataPath");
            dataPathNode.setText(dataPath);
            element.addChild(dataPathNode);
        }

        // XG-02: CommandName для кнопки. Порядок как в compile-пути: до companion-элементов.
        // Принимаем как короткое имя команды, так и уже полную ссылку Form.Command.X /
        // Form.StandardCommand.X — не дублируем префикс.
        if (command != null && !command.isEmpty()) {
            String commandRef = command.startsWith("Form.")
                    ? command
                    : "Form.Command." + command;
            XmlNode commandNode = createNode("CommandName");
            commandNode.setText(commandRef);
            element.addChild(commandNode);
        }
        //++agent TASK-174

        //++agent TASK-174 [16.06.2026 00:00:00]
        // XG-56: <Title> эмитим после DataPath/CommandName и ДО companion-элементов
        // (ExtendedTooltip/ContextMenu) и <ChildItems> — строго по схеме logform
        // (xs:sequence), как compile-путь: writeInputField (DataPath → Title) и
        // writeUsualGroup (Title первым, до Representation/ExtendedTooltip/ChildItems;
        // у группы DataPath/Type/CommandName отсутствуют, поэтому Title оказывается первым).
        if (titleText != null && !titleText.isBlank()) {
            element.addChild(multilingualTitle(titleText));
        }
        //++agent TASK-174

        if (kind == FormElementKind.TABLE) {
            addTableCompanions(element, name);
        } else {
            // Companion-элементы согласно типу; для неизвестного kind — минимальный набор.
            List<CompanionKind> companions = kind != null
                    ? kind.getCompanions()
                    : List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP);
            for (CompanionKind c : companions) {
                element.addChild(simpleCompanion(c, name));
            }
        }

        if (isContainerElement(element)) {
            findOrCreateChild(element, "ChildItems");
        }

        if (afterName != null) {
            insertAfter(parent, element, afterName);
        } else if (beforeName != null) {
            insertBefore(parent, element, beforeName);
        } else {
            parent.addChild(element);
        }
    }

    //++agent TASK-174 [07.06.2026 10:00:00]
    // XG-14/XG-15: контейнерные типы элементов формы — те, что требуют обёртки <ChildItems>
    // для дочерних элементов. Определяется по XML-тегу узла (не по kind enum, т.к.
    // это может быть существующий узел из загруженного Form.xml).
    //**agent TASK-174 [08.06.2026 00:30:00]
    //private static final java.util.Set<String> CONTAINER_XML_TAGS = java.util.Set.of(
    //        "UsualGroup", "Pages", "Page", "CommandBar", "Popup", "Table"
    //);
    // XG-41: набор XG-15 был неполным. Канон Designer-выгрузок (GBIG PAM, 956 форм):
    // родителями <ChildItems> бывают также AutoCommandBar (540), ButtonGroup (404),
    // ColumnGroup (347), ContextMenu (197). Без них дети добавлялись бы напрямую в
    // узел контейнера, и Designer молча отбрасывал бы их при загрузке.
    private static final java.util.Set<String> CONTAINER_XML_TAGS = java.util.Set.of(
            "UsualGroup", "Pages", "Page", "CommandBar", "Popup", "Table",
            "AutoCommandBar", "ButtonGroup", "ColumnGroup", "ContextMenu"
    );
    //**agent TASK-174

    /**
     * Проверяет, является ли элемент контейнерным (требует <ChildItems> для детей).
     */
    private static boolean isContainerElement(XmlNode node) {
        return CONTAINER_XML_TAGS.contains(node.getName());
    }

    private XmlNode simpleCompanion(CompanionKind kind, String ownerName) {
        XmlNode companion = createNode(kind.getXmlTag());
        companion.setAttribute("name", kind.nameFor(ownerName));
        companion.setAttribute("id", elementIds.nextId());
        return companion;
    }

    private void addTableCompanions(XmlNode table, String tableName) {
        table.addChild(simpleCompanion(CompanionKind.CONTEXT_MENU, tableName));
        table.addChild(simpleCompanion(CompanionKind.AUTO_COMMAND_BAR, tableName));
        table.addChild(simpleCompanion(CompanionKind.EXTENDED_TOOLTIP, tableName));
        table.addChild(tableAddition("SearchStringAddition", tableName,
                tableName + "СтрокаПоиска", "SearchStringRepresentation"));
        table.addChild(tableAddition("ViewStatusAddition", tableName,
                tableName + "СостояниеПросмотра", "ViewStatusRepresentation"));
        table.addChild(tableAddition("SearchControlAddition", tableName,
                tableName + "УправлениеПоиском", "SearchControl"));
    }

    private XmlNode tableAddition(String tag, String tableName, String additionName, String type) {
        XmlNode addition = createNode(tag);
        addition.setAttribute("name", additionName);
        addition.setAttribute("id", elementIds.nextId());

        XmlNode source = createNode("AdditionSource");
        XmlNode item = createNode("Item");
        item.setText(tableName);
        XmlNode sourceType = createNode("Type");
        sourceType.setText(type);
        source.addChild(item);
        source.addChild(sourceType);
        addition.addChild(source);

        addition.addChild(simpleCompanion(CompanionKind.CONTEXT_MENU, additionName));
        addition.addChild(simpleCompanion(CompanionKind.EXTENDED_TOOLTIP, additionName));
        return addition;
    }
    //++agent TASK-174

    public void removeElement(String name) {
        //**agent TASK-174 [08.06.2026 00:30:00]
        //XmlNode childItems = document.getRoot().child("ChildItems");
        //if (childItems != null) {
        //    removeElementRecursive(childItems, name);
        //}
        // XG-41: элементы формы живут не только в корневом <ChildItems>, но и в
        // form-level <AutoCommandBar> (прямой ребёнок <Form>) — обходим оба корня.
        for (XmlNode rootContainer : elementSearchRoots()) {
            removeElementRecursive(rootContainer, name);
        }
        //**agent TASK-174
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
            XmlNode insertionParent = isContainerElement(newParent)
                    ? findOrCreateChild(newParent, "ChildItems")
                    : newParent;
            insertionParent.addChild(element);
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

    //**agent TASK-174 [08.06.2026 00:30:00]
    //private XmlNode findElement(String name) {
    //     XmlNode childItems = document.getRoot().child("ChildItems");
    //     if (childItems == null) return null;
    //     return findElementRecursive(childItems, name);
    //}
    // XG-41: командная панель формы (AutoCommandBar name="ФормаКоманднаяПанель") —
    // прямой ребёнок <Form> ВНЕ корневого <ChildItems>; поиск только от ChildItems
    // делал её принципиально недостижимой как into/after/before-цель
    // («Parent element not found»). Ищем по всем корням UI-элементов.
    private XmlNode findElement(String name) {
        for (XmlNode rootContainer : elementSearchRoots()) {
            XmlNode found = findElementRecursive(rootContainer, name);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Корни поддеревьев UI-элементов формы. По канону Designer-выгрузки прямым
     * ребёнком <Form> кроме <ChildItems> бывает только <AutoCommandBar> (968 форм
     * GBIG PAM); Attributes/Commands/Parameters сюда не входят намеренно — их
     * name-атрибуты не являются именами элементов формы.
     */
    private List<XmlNode> elementSearchRoots() {
        List<XmlNode> roots = new ArrayList<>(2);
        XmlNode childItems = document.getRoot().child("ChildItems");
        if (childItems != null) roots.add(childItems);
        XmlNode autoCommandBar = document.getRoot().child("AutoCommandBar");
        if (autoCommandBar != null) roots.add(autoCommandBar);
        return roots;
    }
    //**agent TASK-174
    
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
            throw new IllegalArgumentException("Target element for after not found in parent: " + afterName);
        }
    }

    private void insertBefore(XmlNode parent, XmlNode element, String beforeName) {
        int index = -1;
        for (int i = 0; i < parent.getChildren().size(); i++) {
            if (beforeName.equals(parent.getChildren().get(i).attr("name"))) {
                index = i;
                break;
            }
        }
        if (index != -1) {
            parent.getChildren().add(index, element);
        } else {
            throw new IllegalArgumentException("Target element for before not found in parent: " + beforeName);
        }
    }
}

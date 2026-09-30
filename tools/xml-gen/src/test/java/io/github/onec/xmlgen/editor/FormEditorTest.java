package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
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
        document = new XmlDocument(null, false, null, "Form", "", Map.of(), root.getChildren(), root);
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
    void addAttribute_valueTableColumnsUseLocalIdsAndDoNotConsumeAttributePool() {
        editor.addAttribute("Таблица", null, "ValueTable", null, null,
                List.of(
                        new io.github.onec.xmlgen.dsl.FormDsl.Column("Колонка1", null, "string"),
                        new io.github.onec.xmlgen.dsl.FormDsl.Column("Колонка2", null, "boolean")
                ));
        editor.addAttribute("Следующий", "string");

        XmlNode attrs = document.getRoot().child("Attributes");
        XmlNode tableAttr = attrs.getChildren().get(0);
        XmlNode columns = tableAttr.child("Columns");
        assertEquals("1", tableAttr.attr("id"));
        assertEquals("1", columns.getChildren().get(0).attr("id"));
        assertEquals("2", columns.getChildren().get(1).attr("id"));
        assertEquals("2", attrs.getChildren().get(1).attr("id"));
    }

    @Test
    void addAttribute_preservesFillCheckingAndUseAlways() {
        editor.addAttribute("Объект", null, "DocumentObject.Заказ", true, true,
                null, "Show", "Объект.RegisterRecords");

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        assertEquals("Show", attr.child("FillChecking").getText());
        assertEquals("Объект.RegisterRecords", attr.child("UseAlways").child("Field").getText());
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
    void addTable_createsCanonicalCompanionsAdditionsAndChildItems() {
        editor.addElement("table", "Товары", "Товары", null, null);

        XmlNode table = document.getRoot().child("ChildItems").child("Table");
        assertNotNull(table.child("ContextMenu"));
        assertNotNull(table.child("AutoCommandBar"));
        assertNotNull(table.child("ExtendedTooltip"));
        assertNotNull(table.child("ChildItems"));

        XmlNode search = table.child("SearchStringAddition");
        assertNotNull(search);
        assertEquals("Товары", search.child("AdditionSource").child("Item").getText());
        assertEquals("SearchStringRepresentation", search.child("AdditionSource").child("Type").getText());
        assertNotNull(search.child("ContextMenu"));
        assertNotNull(search.child("ExtendedTooltip"));

        XmlNode status = table.child("ViewStatusAddition");
        assertEquals("ViewStatusRepresentation", status.child("AdditionSource").child("Type").getText());
        XmlNode control = table.child("SearchControlAddition");
        assertEquals("SearchControl", control.child("AdditionSource").child("Type").getText());
    }

    @Test
    void addEmptyContainers_createChildItemsWrapper() {
        editor.addElement("group", "Группа", null, null, null);
        editor.addElement("pages", "Страницы", null, null, null);
        editor.addElement("page", "Страница", null, null, null);
        editor.addElement("cmdBar", "КоманднаяПанель", null, null, null);
        editor.addElement("popup", "Подменю", null, null, null);

        XmlNode rootItems = document.getRoot().child("ChildItems");
        assertNotNull(rootItems.child("UsualGroup").child("ChildItems"));
        assertNotNull(rootItems.child("Pages").child("ChildItems"));
        assertNotNull(rootItems.child("Page").child("ChildItems"));
        assertNotNull(rootItems.child("CommandBar").child("ChildItems"));
        assertNotNull(rootItems.child("Popup").child("ChildItems"));
    }

    // TASK-174 XG-56: edit-путь эмитит <Title> у контейнерной группы.
    // Раньше FormEditor.addElement не имел title-параметра → заголовок группы терялся.
    @Test
    void task174_xg56_addGroupWithTitle_emitsMultilingualTitle() {
        editor.addElement("group", "ГруппаУправлениеАктивами", "Управление активами",
                null, null, null, null, null);

        XmlNode group = document.getRoot().child("ChildItems").child("UsualGroup");
        assertNotNull(group, "Группа должна быть создана");
        assertEquals("ГруппаУправлениеАктивами", group.attr("name"));

        XmlNode title = group.child("Title");
        assertNotNull(title, "<Title> должен эмититься edit-путём");
        XmlNode item = title.child("item"); // v8:item
        assertNotNull(item, "<Title> должен быть мультиязычным (<v8:item>)");
        assertEquals("ru", item.child("lang").getText());
        assertEquals("Управление активами", item.child("content").getText());

        // ChildItems контейнерной группы цел (XG-15 не сломан).
        assertNotNull(group.child("ChildItems"), "<ChildItems> группы должна сохраниться");
    }

    // TASK-174 XG-56: edit-путь эмитит <Title> у поля; порядок по схеме logform —
    // DataPath ДО Title (как compile-путь writeInputField).
    @Test
    void task174_xg56_addInputWithTitle_emitsTitleAfterDataPath() {
        editor.addElement("input", "Поле", "Заголовок поля", "Объект.биг_ТипДоговора",
                null, null, null, null);

        XmlNode field = document.getRoot().child("ChildItems").child("InputField");
        assertNotNull(field);

        XmlNode title = field.child("Title");
        assertNotNull(title, "<Title> должен эмититься edit-путём");
        assertEquals("Заголовок поля", title.child("item").child("content").getText());

        // Канон logform: DataPath раньше Title в последовательности детей.
        java.util.List<String> childNames = new java.util.ArrayList<>();
        for (XmlNode c : field.getChildren()) childNames.add(c.getName());
        int dataPathIdx = childNames.indexOf("DataPath");
        int titleIdx = childNames.indexOf("Title");
        assertTrue(dataPathIdx >= 0 && titleIdx >= 0);
        assertTrue(dataPathIdx < titleIdx, "DataPath должен идти раньше Title (схема logform)");
    }

    // TASK-174 XG-56: без title (null/пусто) <Title> НЕ эмитится — обратная совместимость.
    @Test
    void task174_xg56_addElementWithoutTitle_noTitleEmitted() {
        editor.addElement("input", "ПолеБезЗаголовка", "Объект.Реквизит", null, null);
        XmlNode field = document.getRoot().child("ChildItems").child("InputField");
        assertFalse(field.hasChild("Title"), "Без titleText <Title> эмититься не должен");
    }

    // TASK-174 XG-02: edit-путь привязывает кнопку к команде (CommandName).
    // Раньше FormEditor игнорировал command → кнопка оставалась без CommandName.
    @Test
    void task174_xg02_addButtonWithCommand_writesCommandName() {
        editor.addElement("Button", "КнопкаСохранить", null, null, null, "КомандаПример");
        XmlNode childItems = document.getRoot().child("ChildItems");
        XmlNode button = childItems.child("Button");
        assertNotNull(button);
        assertEquals("КнопкаСохранить", button.attr("name"));
        XmlNode commandName = button.child("CommandName");
        assertNotNull(commandName, "CommandName должен быть записан edit-путём");
        assertEquals("Form.Command.КомандаПример", commandName.getText());
    }

    @Test
    void task174_xg02_addButtonWithFullCommandRef_notDoublePrefixed() {
        // Уже полная ссылка Form.Command.X не должна получить второй префикс.
        editor.addElement("Button", "Кнопка2", null, null, null, "Form.Command.КомандаПример");
        XmlNode button = document.getRoot().child("ChildItems").child("Button");
        assertEquals("Form.Command.КомандаПример", button.child("CommandName").getText());
    }

    @Test
    void addCommand_preservesTooltipPictureShortcutAndRepresentation() {
        editor.addCommand("Печать", "Печать", "ПечатьОбработка",
                "Напечатать", "Ctrl+P", "StdPicture.Print", "PictureAndText");

        XmlNode command = document.getRoot().child("Commands").child("Command");
        assertEquals("Напечатать", command.child("ToolTip").child("item").child("content").getText());
        assertEquals("StdPicture.Print", command.child("Picture").child("Ref").getText());
        assertEquals("true", command.child("Picture").child("LoadTransparent").getText());
        assertEquals("Ctrl+P", command.child("Shortcut").getText());
        assertEquals("PictureAndText", command.child("Representation").getText());
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
    void addElementBefore_insertsBeforeNamedSibling() {
        editor.addElement("InputField", "Field1", null, null, null);
        editor.addElement("InputField", "Field3", null, null, null);

        editor.addElement("InputField", "Field2", null, null, null, "Field3", null);

        XmlNode childItems = document.getRoot().child("ChildItems");
        assertEquals("Field1", childItems.getChildren().get(0).attr("name"));
        assertEquals("Field2", childItems.getChildren().get(1).attr("name"));
        assertEquals("Field3", childItems.getChildren().get(2).attr("name"));
    }

    @Test
    void addElementAfter_missingSiblingThrowsInsteadOfAppending() {
        editor.addElement("InputField", "Field1", null, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> editor.addElement("InputField", "Field2", null, null, "Missing"));

        assertTrue(ex.getMessage().contains("after"));
        assertEquals(1, document.getRoot().child("ChildItems").getChildren().size());
    }

    @Test
    void addElementBefore_missingSiblingThrowsInsteadOfAppending() {
        editor.addElement("InputField", "Field1", null, null, null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> editor.addElement("InputField", "Field2", null, null, null, "Missing", null));

        assertTrue(ex.getMessage().contains("before"));
        assertEquals(1, document.getRoot().child("ChildItems").getChildren().size());
    }
    
    @Test
    void testRemoveElement() {
        editor.addElement("InputField", "Field1", null, null, null);
        editor.removeElement("Field1");
        XmlNode childItems = document.getRoot().child("ChildItems");
        assertTrue(childItems.getChildren().isEmpty());
    }

    // TASK-174 XG-14: edit-путь добавляет <Type>UsualButton</Type> первым дочерним тегом Button.
    @Test
    void task174_xg14_editButton_emitsUsualButtonTypeFirst() {
        editor.addElement("Button", "КнопкаПрименить", null, null, null);
        XmlNode childItems = document.getRoot().child("ChildItems");
        XmlNode button = childItems.child("Button");
        assertNotNull(button);
        XmlNode typeNode = button.child("Type");
        assertNotNull(typeNode, "Button должен иметь <Type>UsualButton</Type>");
        assertEquals("UsualButton", typeNode.getText());
        // <Type> должен быть ПЕРВЫМ дочерним элементом
        assertEquals("Type", button.getChildren().get(0).getName(),
                "<Type>UsualButton</Type> должен быть первым дочерним тегом Button");
    }

    // TASK-174 XG-14: кнопка с командой — Type идёт первым, CommandName вторым
    @Test
    void task174_xg14_editButtonWithCommand_typeBeforeCommandName() {
        editor.addElement("Button", "КнопкаСохранить", null, null, null, "Сохранить");
        XmlNode button = document.getRoot().child("ChildItems").child("Button");
        assertNotNull(button);
        List<XmlNode> children = button.getChildren();
        // Порядок: Type → CommandName → ExtendedTooltip
        assertEquals("Type", children.get(0).getName());
        assertEquals("UsualButton", children.get(0).getText());
        assertEquals("CommandName", children.get(1).getName());
    }

    // TASK-174 XG-15: edit-путь добавляет элемент в <ChildItems> группы, а не напрямую в UsualGroup.
    @Test
    void task174_xg15_addIntoGroup_usesChildItemsWrapper() {
        // Сначала создаём группу
        editor.addElement("UsualGroup", "Группа1", null, null, null);
        XmlNode rootChildItems = document.getRoot().child("ChildItems");
        XmlNode group = rootChildItems.child("UsualGroup");
        assertNotNull(group, "Группа должна быть создана");

        // Теперь добавляем элемент внутрь группы
        editor.addElement("InputField", "ПолеВнутри", "Реквизит", "Группа1", null);

        // Элемент должен быть в <ChildItems> группы, а не непосредственно в <UsualGroup>
        XmlNode groupChildItems = group.child("ChildItems");
        assertNotNull(groupChildItems, "UsualGroup должна иметь <ChildItems>");
        XmlNode field = groupChildItems.child("InputField");
        assertNotNull(field, "InputField должен быть в ChildItems группы");
        assertEquals("ПолеВнутри", field.attr("name"));
    }

    // TASK-174 XG-15: кнопка внутри группы — тоже в ChildItems группы и с Type UsualButton
    @Test
    void task174_xg15_addButtonIntoGroup_childItemsAndType() {
        editor.addElement("UsualGroup", "ГруппаКоманды", null, null, null);
        XmlNode group = document.getRoot().child("ChildItems").child("UsualGroup");

        editor.addElement("Button", "КнопкаАнализ", null, "ГруппаКоманды", null, "Анализ");

        XmlNode groupChildItems = group.child("ChildItems");
        assertNotNull(groupChildItems, "UsualGroup должна иметь <ChildItems>");
        XmlNode button = groupChildItems.child("Button");
        assertNotNull(button, "Button должен быть в ChildItems группы");
        // XG-14: Type тоже должен быть
        XmlNode typeNode = button.child("Type");
        assertNotNull(typeNode);
        assertEquals("UsualButton", typeNode.getText());
    }

    @Test
    void moveElementIntoContainer_usesChildItemsWrapper() {
        editor.addElement("InputField", "Поле", "Реквизит", null, null);
        editor.addElement("group", "ГруппаНазначения", null, null, null);

        editor.moveElement("Поле", null, null, "ГруппаНазначения");

        XmlNode group = document.getRoot().child("ChildItems").child("UsualGroup");
        XmlNode groupChildItems = group.child("ChildItems");
        assertNotNull(groupChildItems);
        assertNotNull(groupChildItems.child("InputField"));
        assertNull(group.child("InputField"));
    }

    // TASK-174 XG-31: edit-путь принимает короткий kind "radio" без правки FormElementKind.
    @Test
    void task174_xg31_addRadioShortKind_createsRadioButtonField() {
        editor.addElement("radio", "Переключатель", "Режим", null, null);

        XmlNode radio = document.getRoot().child("ChildItems").child("RadioButtonField");
        assertNotNull(radio);
        assertEquals("Переключатель", radio.attr("name"));
        assertEquals("Режим", radio.child("DataPath").getText());
        assertTrue(radio.hasChild("ContextMenu"));
        assertTrue(radio.hasChild("ExtendedTooltip"));
    }

    // ------------------------------------------------------------------
    // TASK-174 XG-41: командная панель формы (AutoCommandBar) — прямой ребёнок
    // <Form> ВНЕ корневого <ChildItems>, поэтому findElement её не видел и
    // `form edit --json {into: "ФормаКоманднаяПанель"}` падал
    // «Parent element not found». Канон Designer-выгрузок GBIG PAM: 968 форм
    // имеют form-level AutoCommandBar, 381 — с непустым <ChildItems>.
    // ------------------------------------------------------------------

    /** Документ с form-level AutoCommandBar (как в реальной Designer-выгрузке). */
    private FormEditor editorWithFormCommandBar() {
        XmlNode autoBar = XmlNode.builder()
                .name("AutoCommandBar")
                .attribute("name", "ФормаКоманднаяПанель")
                .attribute("id", "-1")
                .build();
        XmlNode root = XmlNode.builder()
                .name("Form")
                .addChild(XmlNode.builder().name("Attributes").build())
                .addChild(XmlNode.builder().name("Commands").build())
                .addChild(XmlNode.builder().name("ChildItems").build())
                .addChild(autoBar)
                .build();
        document = new XmlDocument(null, false, null, "Form", "", Map.of(), root.getChildren(), root);
        return new FormEditor(document);
    }

    @Test
    void task174_xg41_addButtonIntoFormAutoCommandBar() {
        FormEditor barEditor = editorWithFormCommandBar();

        barEditor.addElement("Button", "ФормаВыполнитьВозврат", null,
                "ФормаКоманднаяПанель", null, "ВыполнитьВозврат");

        XmlNode autoBar = document.getRoot().child("AutoCommandBar");
        assertNotNull(autoBar, "form-level AutoCommandBar должен сохраниться");
        // XG-15-класс: дети контейнера — внутри <ChildItems>, не напрямую
        XmlNode barChildItems = autoBar.child("ChildItems");
        assertNotNull(barChildItems, "AutoCommandBar должен иметь <ChildItems>");
        XmlNode button = barChildItems.child("Button");
        assertNotNull(button, "Button должен быть в ChildItems командной панели");
        assertEquals("ФормаВыполнитьВозврат", button.attr("name"));
        // XG-14: Type=UsualButton первым дочерним тегом
        assertEquals("Type", button.getChildren().get(0).getName());
        assertEquals("UsualButton", button.child("Type").getText());
        // XG-02: CommandName с полным префиксом
        assertEquals("Form.Command.ВыполнитьВозврат", button.child("CommandName").getText());
        // Кнопка НЕ должна попасть в корневой ChildItems
        assertNull(document.getRoot().child("ChildItems").child("Button"));
    }

    @Test
    void task174_xg41_moveElementIntoFormAutoCommandBar() {
        FormEditor barEditor = editorWithFormCommandBar();
        barEditor.addElement("Button", "Кнопка", null, null, null, "Команда");

        barEditor.moveElement("Кнопка", null, null, "ФормаКоманднаяПанель");

        XmlNode autoBar = document.getRoot().child("AutoCommandBar");
        assertNotNull(autoBar.child("ChildItems"));
        assertNotNull(autoBar.child("ChildItems").child("Button"));
        assertNull(document.getRoot().child("ChildItems").child("Button"));
    }

    @Test
    void task174_xg41_removeElementFromFormAutoCommandBar() {
        FormEditor barEditor = editorWithFormCommandBar();
        barEditor.addElement("Button", "Кнопка", null, "ФормаКоманднаяПанель", null, "Команда");
        assertNotNull(document.getRoot().child("AutoCommandBar").child("ChildItems").child("Button"));

        barEditor.removeElement("Кнопка");

        assertNull(document.getRoot().child("AutoCommandBar").child("ChildItems").child("Button"),
                "removeElement должен находить элементы внутри form-level AutoCommandBar");
    }

    // TASK-174 XG-41 (класс): по канону Designer <ChildItems> легитимен также у
    // ButtonGroup, ColumnGroup и ContextMenu (404/347/197 вхождений в выгрузках
    // GBIG PAM) — они тоже контейнеры, дети обязаны оборачиваться в <ChildItems>.
    @Test
    void task174_xg41_buttonGroupAndContextMenu_areContainers() {
        editor.addElement("ButtonGroup", "ГруппаКнопок", null, null, null);
        XmlNode group = document.getRoot().child("ChildItems").child("ButtonGroup");
        assertNotNull(group);

        editor.addElement("Button", "КнопкаВГруппе", null, "ГруппаКнопок", null, "Команда");
        XmlNode groupChildItems = group.child("ChildItems");
        assertNotNull(groupChildItems, "ButtonGroup должна иметь <ChildItems>");
        assertNotNull(groupChildItems.child("Button"), "Button должен быть в ChildItems группы кнопок");
        assertNull(group.child("Button"), "Button не должен лежать напрямую в ButtonGroup");
    }
}

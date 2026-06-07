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
    void testAddElement() {
        editor.addElement("InputField", "MyField", "MyAttr", null, null);
        XmlNode childItems = document.getRoot().child("ChildItems");
        XmlNode field = childItems.child("InputField");
        assertNotNull(field);
        assertEquals("MyField", field.attr("name"));
        assertTrue(field.hasChild("ContextMenu"));
        assertTrue(field.hasChild("ExtendedTooltip"));
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
}

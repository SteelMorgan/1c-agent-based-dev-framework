package io.github.onec.xmlgen.form.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormEditDsl;
import io.github.onec.xmlgen.editor.FormEditor;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 0 baseline: проверяет, что FormEditApplier применяет JSON-спец мутаций
 * через FormEditor без Type DSL, columns, events (это Phase 1+).
 */
class FormEditApplierTest {

    private XmlDocument document;
    private FormEditor editor;
    private ObjectMapper mapper;

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
        mapper = new ObjectMapper();
    }

    @Test
    void applyJsonSpec_addsAttributeElementCommand() throws Exception {
        String json = "{"
                + "\"attributes\":[{\"name\":\"MyAttr\",\"type\":\"xs:string\"}],"
                + "\"commands\":[{\"name\":\"DoIt\",\"title\":\"Do It\",\"action\":\"DoItHandler\"}],"
                + "\"elements\":[{\"kind\":\"InputField\",\"name\":\"MyField\",\"dataPath\":\"MyAttr\"}]"
                + "}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);

        new FormEditApplier(editor).apply(spec);

        XmlNode attrs = document.getRoot().child("Attributes");
        assertEquals(1, attrs.getChildren().size());
        assertEquals("MyAttr", attrs.getChildren().get(0).attr("name"));

        XmlNode cmds = document.getRoot().child("Commands");
        assertEquals(1, cmds.getChildren().size());
        assertEquals("DoIt", cmds.getChildren().get(0).attr("name"));

        XmlNode items = document.getRoot().child("ChildItems");
        assertEquals(1, items.getChildren().size());
        XmlNode field = items.getChildren().get(0);
        assertEquals("InputField", field.getName());
        assertEquals("MyField", field.attr("name"));
        assertTrue(field.hasChild("ContextMenu"));
        assertTrue(field.hasChild("ExtendedTooltip"));
    }

    @Test
    void applyJsonSpec_nullSpecIsNoop() {
        assertDoesNotThrow(() -> new FormEditApplier(editor).apply(null));
    }

    @Test
    void applyJsonSpec_rejectsAttributeWithoutName() throws Exception {
        String json = "{\"attributes\":[{\"type\":\"xs:string\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        assertThrows(IllegalArgumentException.class,
                () -> new FormEditApplier(editor).apply(spec));
    }

    @Test
    void applyJsonSpec_rejectsElementWithoutKind() throws Exception {
        String json = "{\"elements\":[{\"name\":\"X\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        assertThrows(IllegalArgumentException.class,
                () -> new FormEditApplier(editor).apply(spec));
    }

    @Test
    void applyJsonSpec_stringTypeDsl_emitsQualifiers() throws Exception {
        String json = "{\"attributes\":[{\"name\":\"Code\",\"type\":\"string(15)\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        XmlNode type = attr.child("Type");
        assertEquals("xs:string", type.getChildren().get(0).getText());
        assertEquals("StringQualifiers", type.getChildren().get(1).getName());
        assertEquals("15", type.getChildren().get(1).child("Length").getText());
    }

    @Test
    void applyJsonSpec_mainAttribute() throws Exception {
        String json = "{\"attributes\":[{\"name\":\"Object\",\"type\":\"CatalogObject.Товары\",\"main\":true}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        assertNotNull(attr.child("MainAttribute"));
        assertEquals("true", attr.child("MainAttribute").getText());
    }

    @Test
    void applyJsonSpec_valueTableWithColumns() throws Exception {
        String json = "{\"attributes\":[{"
                + "\"name\":\"Состав\","
                + "\"type\":\"ValueTable\","
                + "\"columns\":["
                + "{\"name\":\"Товар\",\"type\":\"CatalogRef.Товары\"},"
                + "{\"name\":\"Количество\",\"type\":\"decimal(15,3,nonneg)\"}"
                + "]}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        assertEquals("v8:ValueTable", attr.child("Type").getChildren().get(0).getText());

        XmlNode columns = attr.child("Columns");
        assertNotNull(columns);
        assertEquals(2, columns.getChildren().size());

        XmlNode col0 = columns.getChildren().get(0);
        assertEquals("Товар", col0.attr("name"));
        assertEquals("cfg:CatalogRef.Товары", col0.child("Type").getChildren().get(0).getText());

        XmlNode col1 = columns.getChildren().get(1);
        assertEquals("Количество", col1.attr("name"));
        assertEquals("Nonnegative",
                col1.child("Type").getChildren().get(1).child("AllowedSign").getText());
    }

    @Test
    void applyJsonSpec_savedDataFlag() throws Exception {
        String json = "{\"attributes\":[{\"name\":\"Запись\",\"type\":\"CatalogRef.Товары\",\"savedData\":true}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        assertNotNull(attr.child("SavedData"));
        assertEquals("true", attr.child("SavedData").getText());
    }

    @Test
    void applyJsonSpec_preservesFillCheckingAndUseAlwaysField() throws Exception {
        String json = "{\"attributes\":[{"
                + "\"name\":\"Объект\","
                + "\"type\":\"DocumentObject.Заказ\","
                + "\"fillChecking\":\"Show\","
                + "\"useAlwaysField\":\"Объект.RegisterRecords\""
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        assertEquals("Show", attr.child("FillChecking").getText());
        assertEquals("Объект.RegisterRecords", attr.child("UseAlways").child("Field").getText());
    }

    @Test
    void applyJsonSpec_tableKind_fullCompanionSet() throws Exception {
        String json = "{\"elements\":[{\"kind\":\"table\",\"name\":\"Состав\",\"dataPath\":\"Товары\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode table = document.getRoot().child("ChildItems").getChildren().get(0);
        assertEquals("Table", table.getName());
        assertEquals("Состав", table.attr("name"));
        assertNotNull(table.child("ContextMenu"));
        assertNotNull(table.child("AutoCommandBar"));
        assertNotNull(table.child("ExtendedTooltip"));
        assertNotNull(table.child("SearchStringAddition"));
        assertNotNull(table.child("ViewStatusAddition"));
        assertNotNull(table.child("SearchControlAddition"));
        assertNotNull(table.child("ChildItems"));
        assertEquals("СоставКоманднаяПанель", table.child("AutoCommandBar").attr("name"));
        assertEquals("СоставСтрокаПоиска", table.child("SearchStringAddition").attr("name"));
        assertEquals("Состав", table.child("SearchStringAddition")
                .child("AdditionSource").child("Item").getText());
        assertEquals("SearchStringRepresentation", table.child("SearchStringAddition")
                .child("AdditionSource").child("Type").getText());
    }

    @Test
    void applyJsonSpec_buttonKind_onlyExtendedTooltip() throws Exception {
        String json = "{\"elements\":[{\"kind\":\"button\",\"name\":\"Выполнить\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode button = document.getRoot().child("ChildItems").getChildren().get(0);
        assertEquals("Button", button.getName());
        assertNull(button.child("ContextMenu"));
        assertNotNull(button.child("ExtendedTooltip"));
    }

    @Test
    void applyJsonSpec_groupKind_extendedTooltipOnly() throws Exception {
        String json = "{\"elements\":[{\"kind\":\"group\",\"name\":\"Группа1\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode group = document.getRoot().child("ChildItems").getChildren().get(0);
        assertEquals("UsualGroup", group.getName());
        assertNull(group.child("ContextMenu"));
        assertNotNull(group.child("ExtendedTooltip"));
        assertNotNull(group.child("ChildItems"));
    }

    @Test
    void applyJsonSpec_cmdBarKind_noCompanions() throws Exception {
        String json = "{\"elements\":[{\"kind\":\"cmdBar\",\"name\":\"Бар\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode bar = document.getRoot().child("ChildItems").getChildren().get(0);
        assertEquals("CommandBar", bar.getName());
        // Никаких companion'ов — CommandBar самодостаточен
        assertEquals(1, bar.getChildren().size());
        assertNotNull(bar.child("ChildItems"));
    }

    @Test
    void applyJsonSpec_elementBefore_insertsBeforeNamedSibling() throws Exception {
        editor.addElement("input", "Первый", null, null, null);
        editor.addElement("input", "Третий", null, null, null);

        String json = "{\"elements\":[{\"kind\":\"input\",\"name\":\"Второй\",\"before\":\"Третий\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode items = document.getRoot().child("ChildItems");
        assertEquals("Первый", items.getChildren().get(0).attr("name"));
        assertEquals("Второй", items.getChildren().get(1).attr("name"));
        assertEquals("Третий", items.getChildren().get(2).attr("name"));
    }

    @Test
    void applyJsonSpec_preservesCommandPictureShortcutRepresentation() throws Exception {
        String json = "{\"commands\":[{"
                + "\"name\":\"Печать\","
                + "\"title\":\"Печать\","
                + "\"action\":\"ПечатьОбработка\","
                + "\"tooltip\":\"Напечатать\","
                + "\"picture\":\"StdPicture.Print\","
                + "\"shortcut\":\"Ctrl+P\","
                + "\"representation\":\"PictureAndText\""
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode command = document.getRoot().child("Commands").getChildren().get(0);
        assertEquals("StdPicture.Print", command.child("Picture").child("Ref").getText());
        assertEquals("Ctrl+P", command.child("Shortcut").getText());
        assertEquals("PictureAndText", command.child("Representation").getText());
        assertEquals("Напечатать", command.child("ToolTip").child("item").child("content").getText());
    }

    @Test
    void threePools_independentIds() throws Exception {
        String json = "{"
                + "\"attributes\":[{\"name\":\"A\",\"type\":\"string(10)\"}],"
                + "\"commands\":[{\"name\":\"C\",\"action\":\"Handler\"}],"
                + "\"elements\":[{\"kind\":\"input\",\"name\":\"F\",\"dataPath\":\"A\"}]"
                + "}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode attr = document.getRoot().child("Attributes").getChildren().get(0);
        XmlNode cmd = document.getRoot().child("Commands").getChildren().get(0);
        XmlNode field = document.getRoot().child("ChildItems").getChildren().get(0);

        // Каждый пул стартует с 1 (Python parity — три независимых пула)
        assertEquals("1", attr.attr("id"));
        assertEquals("1", cmd.attr("id"));
        assertEquals("1", field.attr("id"));
        // Companions занимают 2 и 3 в element-pool
        assertEquals("2", field.child("ContextMenu").attr("id"));
        assertEquals("3", field.child("ExtendedTooltip").attr("id"));
    }

    @Test
    void applyJsonSpec_elementEvent_autoGeneratedHandlerName() throws Exception {
        String json = "{\"elements\":[{"
                + "\"kind\":\"input\",\"name\":\"Товар\","
                + "\"on\":[{\"event\":\"OnChange\"}]"
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode field = document.getRoot().child("ChildItems").getChildren().get(0);
        XmlNode events = field.child("Events");
        assertNotNull(events);
        assertEquals(1, events.getChildren().size());
        XmlNode evt = events.getChildren().get(0);
        assertEquals("OnChange", evt.attr("name"));
        // Автогенерация: "Товар" + "ПриИзменении"
        assertEquals("\u0422\u043e\u0432\u0430\u0440\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438", evt.getText());
    }

    @Test
    void applyJsonSpec_elementEvent_explicitHandlerOverride() throws Exception {
        String json = "{\"elements\":[{"
                + "\"kind\":\"input\",\"name\":\"Field\","
                + "\"on\":[{\"event\":\"OnChange\"}],"
                + "\"handlers\":{\"OnChange\":\"MyCustomHandler\"}"
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode field = document.getRoot().child("ChildItems").getChildren().get(0);
        XmlNode evt = field.child("Events").getChildren().get(0);
        assertEquals("MyCustomHandler", evt.getText());
    }

    @Test
    void applyJsonSpec_formEvent_writtenAtRoot() throws Exception {
        String json = "{\"formEvents\":[{\"name\":\"OnCreateAtServer\",\"handler\":\"\u041f\u0440\u0438\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0438\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode events = document.getRoot().child("Events");
        assertNotNull(events);
        XmlNode evt = events.getChildren().get(0);
        assertEquals("OnCreateAtServer", evt.attr("name"));
        assertEquals("\u041f\u0440\u0438\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0438\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435", evt.getText());
        assertTrue(document.getRoot().getChildren().indexOf(events)
                < document.getRoot().getChildren().indexOf(document.getRoot().child("Attributes")));
    }

    @Test
    void applyJsonSpec_containerElementEvents_insertBeforeChildItems() throws Exception {
        String json = "{\"elements\":[{"
                + "\"kind\":\"group\","
                + "\"name\":\"Группа\","
                + "\"on\":[{\"event\":\"Opening\"}]"
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode group = document.getRoot().child("ChildItems").child("UsualGroup");
        XmlNode events = group.child("Events");
        XmlNode childItems = group.child("ChildItems");
        assertNotNull(events);
        assertNotNull(childItems);
        assertTrue(group.getChildren().indexOf(events) < group.getChildren().indexOf(childItems));
    }

    @Test
    void applyJsonSpec_elementEventInjection_intoExistingElement() throws Exception {
        // Сначала создаём элемент
        editor.addElement("InputField", "Existing", null, null, null);

        String json = "{\"elementEvents\":[{"
                + "\"element\":\"Existing\",\"name\":\"OnChange\",\"handler\":\"MyHandler\",\"callType\":\"Before\""
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(editor).apply(spec);

        XmlNode field = document.getRoot().child("ChildItems").getChildren().get(0);
        XmlNode evt = field.child("Events").getChildren().get(0);
        assertEquals("OnChange", evt.attr("name"));
        assertEquals("Before", evt.attr("callType"));
        assertEquals("MyHandler", evt.getText());
    }

    @Test
    void applyJsonSpec_elementEventInjection_elementNotFound_throws() throws Exception {
        String json = "{\"elementEvents\":[{\"element\":\"Missing\",\"name\":\"OnChange\",\"handler\":\"X\"}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        assertThrows(IllegalArgumentException.class,
                () -> new FormEditApplier(editor).apply(spec));
    }

    @Test
    void applyJsonSpec_bslStubsAppendedToModuleBsl(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path formXml = tmp.resolve("Ext/Form/Form.xml");
        java.nio.file.Files.createDirectories(formXml.getParent());

        String json = "{\"elements\":[{"
                + "\"kind\":\"input\",\"name\":\"Field\","
                + "\"on\":[{\"event\":\"OnChange\"}]"
                + "}]}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        FormEditApplier applier = new FormEditApplier(editor, formXml);
        applier.apply(spec);

        java.nio.file.Path modulePath = tmp.resolve("Ext/Form/Module.bsl");
        assertTrue(java.nio.file.Files.exists(modulePath));
        assertEquals(1, applier.getLastBslStubsAdded().size());

        byte[] bytes = java.nio.file.Files.readAllBytes(modulePath);
        String body = new String(bytes, 3, bytes.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u0430 Field\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438(\u042d\u043b\u0435\u043c\u0435\u043d\u0442)"), body);
    }

    @Test
    void extensionMode_idsStartFrom1M() throws Exception {
        // Создаём форму с BaseForm → extension-режим
        XmlNode root = XmlNode.builder()
                .name("Form")
                .addChild(XmlNode.builder().name("BaseForm").build())
                .addChild(XmlNode.builder().name("Attributes").build())
                .addChild(XmlNode.builder().name("Commands").build())
                .addChild(XmlNode.builder().name("ChildItems").build())
                .build();
        XmlDocument extDoc = new XmlDocument(null, false, null, "Form", "", Map.of(), root.getChildren(), root);
        FormEditor extEditor = new FormEditor(extDoc);
        assertTrue(extEditor.isExtension());

        String json = "{"
                + "\"attributes\":[{\"name\":\"Новый\",\"type\":\"string(10)\"}],"
                + "\"elements\":[{\"kind\":\"input\",\"name\":\"Поле\"}]"
                + "}";
        FormEditDsl spec = mapper.readValue(json, FormEditDsl.class);
        new FormEditApplier(extEditor).apply(spec);

        XmlNode attr = extDoc.getRoot().child("Attributes").getChildren().get(0);
        XmlNode field = extDoc.getRoot().child("ChildItems").getChildren().get(0);
        assertEquals("1000000", attr.attr("id"));
        assertEquals("1000000", field.attr("id"));
        assertEquals("1000001", field.child("ContextMenu").attr("id"));
    }
}

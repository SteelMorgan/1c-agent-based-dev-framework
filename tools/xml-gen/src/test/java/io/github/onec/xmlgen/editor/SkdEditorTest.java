package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.editor.skd.PatchQueryEngine;
import io.github.onec.xmlgen.editor.skd.SkdParseException;
import io.github.onec.xmlgen.editor.skd.SkdShorthandParser;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для SkdEditor — реализация SPEC §5 (skd-edit patch operations).
 *
 * <p>Стратегия: in-memory {@link XmlDocument} с фикстурой минимальной DCS-схемы.
 * Покрытие: happy / negative / idempotency для каждой из 17 операций + integration
 * тесты для batch, patch-query, set-field-role с kv.
 */
class SkdEditorTest {

    private XmlDocument document;
    private SkdEditor editor;

    @BeforeEach
    void setUp() {
        document = newSchema();
        editor = new SkdEditor(document);
    }

    /** Фикстура: схема с одним dataSet ("MainDS") + одним settingsVariant ("Основной"). */
    private static XmlDocument newSchema() {
        // dataSet
        XmlNode dataSet = XmlNode.builder()
                .name("dataSet")
                .addChild(XmlNode.builder().name("name").appendText("MainDS").build())
                .addChild(XmlNode.builder().name("query").appendText("ВЫБРАТЬ a, b ИЗ Таблица").build())
                .build();
        // settingsVariant с пустыми settings
        XmlNode variant = XmlNode.builder()
                .name("settingsVariant")
                .addChild(XmlNode.builder().name("name").prefix("dcsset").appendText("Основной").build())
                .addChild(XmlNode.builder().name("settings").prefix("dcsset").build())
                .build();
        XmlNode root = XmlNode.builder()
                .name("DataCompositionSchema")
                .addChild(dataSet)
                .addChild(variant)
                .build();
        return new XmlDocument(null, false, null, "DataCompositionSchema", "",
                Map.of(), root.getChildren(), root);
    }

    // helper to access a parameter by name
    private XmlNode findParameter(String name) {
        for (XmlNode p : document.getRoot().children("parameter")) {
            if (name.equals(p.childText("name"))) return p;
        }
        return null;
    }
    private XmlNode findField(String dataPath) {
        for (XmlNode ds : document.getRoot().children("dataSet")) {
            for (XmlNode f : ds.children("field")) {
                if (dataPath.equals(f.childText("dataPath"))) return f;
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // BACK-COMPAT (preserved from original tests)
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testAddParameter_legacy() {
        editor.addParameter("Period", "Период", "xs:dateTime");
        XmlNode param = document.getRoot().child("parameter");
        assertNotNull(param);
        assertEquals("Period", param.childText("name"));
        assertEquals("xs:dateTime", param.child("valueType").childText("Type"));
    }

    @Test
    void testAddFieldLegacy() {
        editor.addField("MainDS", "ItemRef", "Items.Ref", "Номенклатура");
        XmlNode ds = document.getRoot().children("dataSet").get(0);
        assertNull(ds.child("fields"));
        assertEquals(1, ds.children("field").size());
    }

    @Test
    void testAddFieldLegacy_nonExistentDataSet() {
        assertThrows(IllegalArgumentException.class,
                () -> editor.addField("Bad", "x", "y", "z"));
    }

    // ════════════════════════════════════════════════════════════════════
    // add-field (happy / negative / idempotency)
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testAddField_happy() {
        var fd = SkdShorthandParser.parseField("Цена: decimal(15,2)");
        var r = editor.addField(fd, null, null, false);
        assertTrue(r.changed);
        assertNotNull(findField("Цена"));
        XmlNode ds = document.getRoot().children("dataSet").get(0);
        assertNull(ds.child("fields"));
        assertThat(ds.getChildren().indexOf(findField("Цена")))
                .isLessThan(ds.getChildren().indexOf(ds.child("query")));
    }

    @Test
    void testAddFieldRestrictionsUseCanonicalNamesAndTrueValues() {
        editor.addField(SkdShorthandParser.parseField(
                "Служебное: string #noFilter #noOrder #noGroup #noField"),
                null, null, true);

        XmlNode restriction = findField("Служебное").child("useRestriction");

        assertEquals("true", restriction.childText("condition"));
        assertEquals("true", restriction.childText("order"));
        assertEquals("true", restriction.childText("group"));
        assertEquals("true", restriction.childText("field"));
        assertNull(restriction.child("noFilter"));
        assertNull(restriction.child("noOrder"));
    }

    @Test
    void testAddField_idempotent() {
        var fd = SkdShorthandParser.parseField("Цена: decimal(15,2)");
        editor.addField(fd, null, null, false);
        var r2 = editor.addField(fd, null, null, false);
        assertFalse(r2.changed);
        assertThat(editor.getWarnings()).anyMatch(s -> s.contains("Цена"));
    }

    @Test
    void testAddField_negative_noColon() {
        assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseField("Имя !!"));
    }

    // ════════════════════════════════════════════════════════════════════
    // modify-field
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testModifyField_happy() {
        editor.addField(SkdShorthandParser.parseField("Цена: decimal(10,2)"), null, null, true);
        var r = editor.modifyField(
                SkdShorthandParser.parseField("Цена [Цена USD]: decimal(15,4)"), null);
        assertTrue(r.changed);
        XmlNode f = findField("Цена");
        assertNotNull(f.child("title"));
    }

    @Test
    void testModifyField_idempotent_noop() {
        // no field — warning + skip
        var r = editor.modifyField(SkdShorthandParser.parseField("Цена: decimal(15,2)"), null);
        assertFalse(r.changed);
    }

    @Test
    void testModifyField_negative_notFound() {
        var r = editor.modifyField(SkdShorthandParser.parseField("X: string"), null);
        assertFalse(r.changed);
    }

    // ════════════════════════════════════════════════════════════════════
    // remove-field
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testRemoveField_happy() {
        editor.addField(SkdShorthandParser.parseField("Цена: decimal(15,2)"), null, null, false);
        var r = editor.removeField("Цена", null, null);
        assertTrue(r.changed);
        assertNull(findField("Цена"));
    }

    @Test
    void testRemoveField_idempotent_noop() {
        var r = editor.removeField("Цена", null, null);
        assertFalse(r.changed);
    }

    @Test
    void testRemoveField_negative_emptyDataSet() {
        // No fields container is fine — noop
        var r = editor.removeField("X", null, null);
        assertFalse(r.changed);
    }

    // ════════════════════════════════════════════════════════════════════
    // set-field-role
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testSetFieldRole_happy() {
        editor.addField(SkdShorthandParser.parseField("Сумма: decimal(15,2)"), null, null, true);
        var d = SkdShorthandParser.parseFieldRole("Сумма @balance balanceGroupName=Сумма");
        var r = editor.setFieldRole(d, null);
        assertTrue(r.changed);
        XmlNode field = findField("Сумма");
        assertNotNull(field.child("role"));
    }

    @Test
    void testSetFieldRole_idempotent() {
        editor.addField(SkdShorthandParser.parseField("Сумма: decimal(15,2)"), null, null, true);
        var d = SkdShorthandParser.parseFieldRole("Сумма @balance");
        editor.setFieldRole(d, null);
        var r2 = editor.setFieldRole(d, null);
        assertFalse(r2.changed);
    }

    @Test
    void testSetFieldRole_negative_notFound() {
        var d = SkdShorthandParser.parseFieldRole("Х @balance");
        assertThrows(Exception.class, () -> editor.setFieldRole(d, null));
    }

    @Test
    void testSetFieldRole_BalanceWithKvArgs_ProducesCorrectXml() {
        editor.addField(SkdShorthandParser.parseField("СуммаНач: decimal(15,2)"), null, null, true);
        var d = SkdShorthandParser.parseFieldRole(
                "СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance");
        editor.setFieldRole(d, null);
        XmlNode role = findField("СуммаНач").child("role");
        assertNotNull(role);
        assertEquals("http://v8.1c.ru/8.1/data-composition-system/common",
                role.attr("xmlns:dcscom"));
        assertEquals("true", role.childText("balance"));
        assertEquals("Сумма", role.childText("balanceGroupName"));
        assertEquals("OpeningBalance", role.childText("balanceType"));
        assertEquals("dcscom", role.child("balance").getPrefix());
        assertEquals("dcscom", role.child("balanceGroupName").getPrefix());
    }

    @Test
    void testSetFieldRole_PeriodUsesDcsCommonPeriodNodes() {
        editor.addField(SkdShorthandParser.parseField("Период: date"), null, null, true);
        var d = SkdShorthandParser.parseFieldRole("Период @period");
        editor.setFieldRole(d, null);
        XmlNode role = findField("Период").child("role");
        assertNotNull(role);
        assertEquals("1", role.childText("periodNumber"));
        assertEquals("Main", role.childText("periodType"));
        assertEquals("dcscom", role.child("periodNumber").getPrefix());
        assertEquals("dcscom", role.child("periodType").getPrefix());
    }

    @Test
    void testAddFieldWithInlineRoleDeclaresDcsCommonNamespace() {
        editor.addField(SkdShorthandParser.parseField("Организация: CatalogRef.Организации @dimension"),
                null, null, true);
        XmlNode role = findField("Организация").child("role");
        assertNotNull(role);
        assertEquals("http://v8.1c.ru/8.1/data-composition-system/common",
                role.attr("xmlns:dcscom"));
        assertEquals("true", role.childText("dimension"));
        assertEquals("dcscom", role.child("dimension").getPrefix());
    }

    @Test
    void testSetFieldRole_removeRoleWhenNoFlagsAndKv() {
        editor.addField(SkdShorthandParser.parseField("Сумма: decimal(15,2)"), null, null, true);
        editor.setFieldRole(SkdShorthandParser.parseFieldRole("Сумма @balance"), null);
        var r = editor.setFieldRole(SkdShorthandParser.parseFieldRole("Сумма"), null);
        assertTrue(r.changed);
        assertNull(findField("Сумма").child("role"));
    }

    // ════════════════════════════════════════════════════════════════════
    // add-parameter
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testAddParameter_happy() {
        var p = SkdShorthandParser.parseParameter("Период: StandardPeriod = LastMonth");
        var r = editor.addParameter(p);
        assertTrue(r.changed);
        XmlNode period = findParameter("Период");
        assertNotNull(period);
        XmlNode value = period.child("value");
        assertEquals("v8:StandardPeriod", value.attr("xsi:type"));
        assertEquals("LastMonth", value.childText("variant"));
        assertEquals("v8:StandardPeriodVariant", value.child("variant").attr("xsi:type"));
    }

    @Test
    void testAddParameterAutoDatesCreatesDerivedParameters() {
        var p = SkdShorthandParser.parseParameter("Период: StandardPeriod = LastMonth @autoDates");

        var r = editor.addParameter(p);

        assertTrue(r.changed);
        assertNotNull(findParameter("ДатаНачала"));
        assertNotNull(findParameter("ДатаОкончания"));
        assertEquals("&Период.ДатаНачала", findParameter("ДатаНачала").childText("expression"));
        assertEquals("false", findParameter("ДатаНачала").childText("availableAsField"));
        assertEquals("true", findParameter("ДатаНачала").childText("useRestriction"));
        assertEquals("Always", findParameter("Период").childText("use"));
        assertEquals("true", findParameter("Период").childText("denyIncompleteValues"));
        assertNull(findParameter("Период").child("useChoice"));
    }

    @Test
    void testAddParameter_idempotent_duplicate() {
        var p = SkdShorthandParser.parseParameter("Период: StandardPeriod");
        editor.addParameter(p);
        var r = editor.addParameter(p);
        assertFalse(r.changed);
    }

    @Test
    void testAddParameter_autoDates_negative() {
        var p = SkdShorthandParser.parseParameter("X: string @autoDates");
        assertThrows(IllegalArgumentException.class, () -> editor.addParameter(p));
    }

    // ════════════════════════════════════════════════════════════════════
    // modify-parameter
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testModifyParameter_happy() {
        editor.addParameter(SkdShorthandParser.parseParameter("ПорядокОкругления: string"));
        var p = SkdShorthandParser.parseModifyParameter("ПорядокОкругления use=Always");
        var r = editor.modifyParameter(p);
        assertTrue(r.changed);
        assertEquals("Always", findParameter("ПорядокОкругления").childText("use"));
    }

    @Test
    void testModifyParameter_idempotent_noop() {
        var p = SkdShorthandParser.parseModifyParameter("X");
        var r = editor.modifyParameter(p);
        assertFalse(r.changed); // parameter doesn't exist → noop
    }

    @Test
    void testModifyParameter_negative_notFound() {
        var p = SkdShorthandParser.parseModifyParameter("X use=Always");
        var r = editor.modifyParameter(p);
        assertFalse(r.changed);
    }

    @Test
    void testModifyParameter_availableValuesFullReplace() {
        var p = SkdShorthandParser.parseParameter(
                "Округ: EnumRef.Округления availableValue=Окр1: руб., Окр2: коп.");
        editor.addParameter(p);
        var mp = SkdShorthandParser.parseModifyParameter(
                "Округ availableValue=Окр3: тыс.");
        editor.modifyParameter(mp);
        XmlNode param = findParameter("Округ");
        assertNull(param.child("availableValues"));
        List<XmlNode> avs = param.children("availableValue");
        assertEquals(1, avs.size()); // full replace
        assertEquals("Окр3", avs.get(0).childText("value"));
    }

    @Test
    void testModifyParameterValueInfersExistingType() {
        editor.addParameter(SkdShorthandParser.parseParameter("Период: StandardPeriod = LastMonth"));
        var mp = SkdShorthandParser.parseModifyParameter("Период value=ThisMonth");

        var r = editor.modifyParameter(mp);

        assertTrue(r.changed);
        XmlNode value = findParameter("Период").child("value");
        assertEquals("v8:StandardPeriod", value.attr("xsi:type"));
        assertEquals("ThisMonth", value.childText("variant"));
    }

    @Test
    void testModifyParameterValueList() {
        editor.addParameter(SkdShorthandParser.parseParameter("Орг: CatalogRef.Организации"));
        var mp = SkdShorthandParser.parseModifyParameter(
                "Орг value=Справочник.Организации.X, Справочник.Организации.Y");

        var r = editor.modifyParameter(mp);

        assertTrue(r.changed);
        XmlNode param = findParameter("Орг");
        List<XmlNode> values = param.children("value");
        assertEquals(2, values.size());
        assertEquals("dcscor:DesignTimeValue", values.get(0).attr("xsi:type"));
        assertEquals("Справочник.Организации.X", values.get(0).getText());
        assertEquals("Справочник.Организации.Y", values.get(1).getText());
        assertEquals("true", param.childText("valueListAllowed"));
    }

    // ════════════════════════════════════════════════════════════════════
    // remove-parameter
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testRemoveParameter_happy() {
        editor.addParameter(SkdShorthandParser.parseParameter("X: string"));
        var r = editor.removeParameter("X");
        assertTrue(r.changed);
    }

    @Test
    void testRemoveParameter_idempotent_noop() {
        var r = editor.removeParameter("X");
        assertFalse(r.changed);
    }

    @Test
    void testRemoveParameter_negative_emptyName() {
        // Empty: trim returns empty → cannot match. Noop expected.
        var r = editor.removeParameter("");
        assertFalse(r.changed);
    }

    // ════════════════════════════════════════════════════════════════════
    // rename-parameter
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testRenameParameter_happy() {
        editor.addParameter(SkdShorthandParser.parseParameter("OldName: string"));
        var r = editor.renameParameter("OldName", "NewName");
        assertTrue(r.changed);
        assertNotNull(findParameter("NewName"));
    }

    @Test
    void testRenameParameter_negative_notFound() {
        assertThrows(IllegalArgumentException.class,
                () -> editor.renameParameter("X", "Y"));
    }

    @Test
    void testRenameParameter_negative_targetExists() {
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        editor.addParameter(SkdShorthandParser.parseParameter("B: string"));
        assertThrows(IllegalArgumentException.class,
                () -> editor.renameParameter("A", "B"));
    }

    @Test
    void testRenameParameter_idempotent_referenceUpdate() {
        // A and B; B has value referencing A
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        editor.addParameter(SkdShorthandParser.parseParameter("B: string = &A"));
        editor.renameParameter("A", "AA");
        assertEquals("&AA", findParameter("B").child("value").getText());
    }

    // ════════════════════════════════════════════════════════════════════
    // reorder-parameters
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testReorderParameters_happy() {
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        editor.addParameter(SkdShorthandParser.parseParameter("B: string"));
        editor.addParameter(SkdShorthandParser.parseParameter("C: string"));
        var r = editor.reorderParameters(List.of("C", "A"));
        assertTrue(r.changed);
        List<XmlNode> params = document.getRoot().children("parameter");
        assertEquals("C", params.get(0).childText("name"));
        assertEquals("A", params.get(1).childText("name"));
        assertEquals("B", params.get(2).childText("name"));
        assertThat(document.getRoot().getChildren().indexOf(params.get(2)))
                .isLessThan(document.getRoot().getChildren().indexOf(
                        document.getRoot().child("settingsVariant")));
    }

    @Test
    void testReorderParameters_idempotent_sameOrder() {
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        editor.addParameter(SkdShorthandParser.parseParameter("B: string"));
        var r = editor.reorderParameters(List.of("A", "B"));
        assertFalse(r.changed);
    }

    @Test
    void testReorderParameters_negative_duplicates() {
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        assertThrows(IllegalArgumentException.class,
                () -> editor.reorderParameters(List.of("A", "A")));
    }

    // ════════════════════════════════════════════════════════════════════
    // add-total
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testAddTotal_happy_autoWrap() {
        var t = SkdShorthandParser.parseTotal("Цена: Среднее");
        editor.addTotal(t);
        XmlNode tf = document.getRoot().child("totalField");
        assertEquals("Цена", tf.childText("dataPath"));
        assertEquals("Среднее(Цена)", tf.childText("expression"));
    }

    @Test
    void testAddTotal_idempotent_duplicate() {
        editor.addTotal(SkdShorthandParser.parseTotal("X: Сумма"));
        var r = editor.addTotal(SkdShorthandParser.parseTotal("X: Сумма"));
        assertFalse(r.changed);
    }

    @Test
    void testAddTotal_negative_emptyExpression() {
        assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseTotal("X:"));
    }

    @Test
    void testAddTotal_explicitAggregateNotWrapped() {
        editor.addTotal(SkdShorthandParser.parseTotal("Стоимость: Сумма(Кол * Цена)"));
        XmlNode tf = document.getRoot().child("totalField");
        assertEquals("Сумма(Кол * Цена)", tf.childText("expression"));
    }

    // ════════════════════════════════════════════════════════════════════
    // remove-total
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testRemoveTotal_happy() {
        editor.addTotal(SkdShorthandParser.parseTotal("X: Сумма"));
        var r = editor.removeTotal("X");
        assertTrue(r.changed);
    }

    @Test
    void testRemoveTotal_idempotent_noop() {
        var r = editor.removeTotal("X");
        assertFalse(r.changed);
    }

    @Test
    void testRemoveTotal_negative_emptyDocument() {
        var r = editor.removeTotal("ничего");
        assertFalse(r.changed);
    }

    // ════════════════════════════════════════════════════════════════════
    // modify-structure
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testModifyStructure_happy() {
        // Add structure with named group manually
        XmlNode variant = document.getRoot().children("settingsVariant").get(0);
        XmlNode settings = variant.children("settings").get(0);
        XmlNode structure = XmlNode.builder().name("structure").prefix("dcsset").build();
        XmlNode group = XmlNode.builder().name("item").prefix("dcsset")
                .attribute("xsi:type", "dcsset:StructureItemGroup")
                .addChild(XmlNode.builder().name("name").prefix("dcsset").appendText("ДанныеОтчета").build())
                .addChild(XmlNode.builder().name("groupItems").prefix("dcsset")
                        .addChild(XmlNode.builder().name("item").prefix("dcsset").build())
                        .build())
                .build();
        structure.addChild(group);
        settings.addChild(structure);

        var s = SkdShorthandParser.parseStructureSpec("Валюта @name=ДанныеОтчета");
        var r = editor.modifyStructure(s, null);
        assertTrue(r.changed);
    }

    @Test
    void testModifyStructure_negative_groupNotFound() {
        XmlNode variant = document.getRoot().children("settingsVariant").get(0);
        XmlNode settings = variant.children("settings").get(0);
        settings.addChild(XmlNode.builder().name("structure").prefix("dcsset").build());

        var s = SkdShorthandParser.parseStructureSpec("X @name=NonExistent");
        assertThrows(IllegalArgumentException.class, () -> editor.modifyStructure(s, null));
    }

    @Test
    void testModifyStructure_idempotent_noNameError() {
        // No @name in spec → parser fails
        assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseStructureSpec("Валюта"));
    }

    // ════════════════════════════════════════════════════════════════════
    // set-query
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testSetQuery_happy() {
        var r = editor.setQuery("ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура", null);
        assertTrue(r.changed);
        XmlNode ds = document.getRoot().children("dataSet").get(0);
        assertEquals("ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура", ds.childText("query"));
    }

    @Test
    void testSetQuery_idempotent_sameText() {
        editor.setQuery("X", null);
        var r = editor.setQuery("X", null);
        assertFalse(r.changed);
    }

    @Test
    void testSetQuery_negative_dataSetNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> editor.setQuery("X", "NonExistent"));
    }

    // ════════════════════════════════════════════════════════════════════
    // patch-query
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testPatchQuery_happy() {
        editor.setQuery("SELECT a, b FROM t", null);
        editor.patchQuery("a => z", null);
        XmlNode ds = document.getRoot().children("dataSet").get(0);
        assertEquals("SELECT z, b FROM t", ds.childText("query"));
    }

    @Test
    void testPatchQuery_OnceFailsOnDoubleMatch() {
        editor.setQuery("SELECT a, a", null);
        assertThrows(SkdParseException.class,
                () -> editor.patchQuery("a => z @once", null));
    }

    @Test
    void testPatchQuery_idempotent_replaceSelf() {
        editor.setQuery("X", null);
        var r = editor.patchQuery("X => X", null);
        // Always returns changed=true; content equal but operation was performed
        assertTrue(r.changed);
        XmlNode ds = document.getRoot().children("dataSet").get(0);
        assertEquals("X", ds.childText("query"));
    }

    @Test
    void testPatchQuery_negative_zeroMatches_noOnce() {
        editor.setQuery("X", null);
        assertThrows(SkdParseException.class, () -> editor.patchQuery("Y => Z", null));
    }

    // ════════════════════════════════════════════════════════════════════
    // clear-conditionalAppearance
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testClearCA_happy() {
        XmlNode variant = document.getRoot().children("settingsVariant").get(0);
        XmlNode settings = variant.children("settings").get(0);
        XmlNode ca = XmlNode.builder().name("conditionalAppearance").prefix("dcsset")
                .addChild(XmlNode.builder().name("item").prefix("dcsset").build())
                .build();
        settings.addChild(ca);
        var r = editor.clearConditionalAppearance(null);
        assertTrue(r.changed);
        assertEquals(0, ca.getChildren().size());
    }

    @Test
    void testClearCA_idempotent_emptyCA() {
        XmlNode variant = document.getRoot().children("settingsVariant").get(0);
        XmlNode settings = variant.children("settings").get(0);
        settings.addChild(XmlNode.builder().name("conditionalAppearance").prefix("dcsset").build());
        var r = editor.clearConditionalAppearance(null);
        assertFalse(r.changed);
    }

    @Test
    void testClearCA_negative_noVariant() {
        // Schema has variant by default — case is "no CA element"
        var r = editor.clearConditionalAppearance(null);
        assertFalse(r.changed);
    }

    // ════════════════════════════════════════════════════════════════════
    // INTEGRATION / batch / replaceTokenRef
    // ════════════════════════════════════════════════════════════════════

    @Test
    void testBatch_RollbackOnMidFailure() {
        // Simulate batch via parser split + manual application
        editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        // create snapshot of param names
        List<String> initial = new ArrayList<>();
        for (XmlNode p : document.getRoot().children("parameter")) {
            initial.add(p.childText("name"));
        }
        // Try to do add A (duplicate → warning skip) + add-invalid (fail).
        // Since true rollback is on caller (CLI), here we verify warning-skip behavior.
        var r1 = editor.addParameter(SkdShorthandParser.parseParameter("A: string"));
        assertFalse(r1.changed);
        // Negative: autoDates on non-StandardPeriod → throws
        assertThrows(IllegalArgumentException.class, () ->
                editor.addParameter(SkdShorthandParser.parseParameter("BadX: string @autoDates")));
        // Verify A still exists, BadX not added
        assertNotNull(findParameter("A"));
        assertNull(findParameter("BadX"));
    }

    @Test
    void testReplaceTokenRef_wholeTokenOnly() {
        assertEquals("&НовоеИмя",
                SkdEditor.replaceTokenRef("&СтароеИмя", "&СтароеИмя", "&НовоеИмя"));
        // ПериодX should not be touched (whole-token)
        assertEquals("&ПериодX",
                SkdEditor.replaceTokenRef("&ПериодX", "&Период", "&P"));
    }

    @Test
    void testWarnings_multipleDataSets() {
        // Add a second dataSet
        XmlNode root = document.getRoot();
        XmlNode ds2 = XmlNode.builder().name("dataSet")
                .addChild(XmlNode.builder().name("name").appendText("Second").build())
                .build();
        root.addChild(ds2);
        editor.resolveDataSet(null);
        assertThat(editor.getWarnings()).anyMatch(w -> w.contains("MainDS"));
    }

    @Test
    void testWarnings_multipleVariants() {
        XmlNode root = document.getRoot();
        XmlNode v2 = XmlNode.builder().name("settingsVariant")
                .addChild(XmlNode.builder().name("name").prefix("dcsset").appendText("Alt").build())
                .build();
        root.addChild(v2);
        editor.resolveVariant(null);
        assertThat(editor.getWarnings()).anyMatch(w -> w.contains("Alt"));
    }
}

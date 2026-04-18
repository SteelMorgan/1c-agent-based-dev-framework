package io.github.onec.xmlgen.form.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormElementKindTest {

    @Test
    void resolve_byJsonKey() {
        assertEquals(FormElementKind.INPUT, FormElementKind.resolve("input"));
        assertEquals(FormElementKind.TABLE, FormElementKind.resolve("table"));
        assertEquals(FormElementKind.BUTTON, FormElementKind.resolve("button"));
        assertEquals(FormElementKind.GROUP, FormElementKind.resolve("group"));
        assertEquals(FormElementKind.PIC_FIELD, FormElementKind.resolve("picField"));
    }

    @Test
    void resolve_byXmlTag() {
        assertEquals(FormElementKind.INPUT, FormElementKind.resolve("InputField"));
        assertEquals(FormElementKind.TABLE, FormElementKind.resolve("Table"));
        assertEquals(FormElementKind.LABEL, FormElementKind.resolve("LabelDecoration"));
        assertEquals(FormElementKind.GROUP, FormElementKind.resolve("UsualGroup"));
    }

    @Test
    void resolve_unknown() {
        assertNull(FormElementKind.resolve("nonexistent"));
        assertNull(FormElementKind.resolve(null));
    }

    @Test
    void tableCompanions_fullSet() {
        assertEquals(
                java.util.List.of(
                        CompanionKind.CONTEXT_MENU,
                        CompanionKind.AUTO_COMMAND_BAR,
                        CompanionKind.SEARCH_STRING_ADDITION,
                        CompanionKind.VIEW_STATUS_ADDITION,
                        CompanionKind.SEARCH_CONTROL_ADDITION
                ),
                FormElementKind.TABLE.getCompanions());
    }

    @Test
    void cmdBar_hasNoCompanions() {
        assertTrue(FormElementKind.CMD_BAR.getCompanions().isEmpty());
        assertTrue(FormElementKind.POPUP.getCompanions().isEmpty());
    }

    @Test
    void companionRussianSuffixes() {
        assertEquals("ТестКонтекстноеМеню", CompanionKind.CONTEXT_MENU.nameFor("Тест"));
        assertEquals("ТестРасширеннаяПодсказка", CompanionKind.EXTENDED_TOOLTIP.nameFor("Тест"));
        assertEquals("ТаблицаКоманднаяПанель", CompanionKind.AUTO_COMMAND_BAR.nameFor("Таблица"));
    }
}

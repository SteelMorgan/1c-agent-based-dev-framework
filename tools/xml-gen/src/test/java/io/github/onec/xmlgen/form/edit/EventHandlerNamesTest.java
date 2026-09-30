package io.github.onec.xmlgen.form.edit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EventHandlerNamesTest {

    @Test
    void knownEvents_russianSuffix() {
        assertEquals("\u041f\u043e\u043b\u0435\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438",
                EventHandlerNames.defaultFor("\u041f\u043e\u043b\u0435", "OnChange"));
        assertEquals("\u041a\u043d\u043e\u043f\u043a\u0430\u041d\u0430\u0436\u0430\u0442\u0438\u0435",
                EventHandlerNames.defaultFor("\u041a\u043d\u043e\u043f\u043a\u0430", "Click"));
        assertEquals("\u0422\u0430\u0431\u043b\u0438\u0446\u0430\u0412\u044b\u0431\u043e\u0440\u0421\u0442\u0440\u043e\u043a\u0438",
                EventHandlerNames.defaultFor("\u0422\u0430\u0431\u043b\u0438\u0446\u0430", "Selection"));
    }

    @Test
    void unknownEvent_fallbackToEnglishName() {
        assertEquals("PoleSomeCustomEvent",
                EventHandlerNames.defaultFor("Pole", "SomeCustomEvent"));
    }
}

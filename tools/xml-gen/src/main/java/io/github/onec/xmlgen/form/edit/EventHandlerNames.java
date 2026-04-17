package io.github.onec.xmlgen.form.edit;

import java.util.HashMap;
import java.util.Map;

/**
 * Автогенерация имён BSL-обработчиков для событий элементов формы.
 * Совпадает с {@code event_suffix_map} из Python form-edit: {@code Поле + ПриИзменении}.
 */
public final class EventHandlerNames {

    private static final Map<String, String> SUFFIXES = new HashMap<>();

    static {
        SUFFIXES.put("OnChange", "\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438");
        SUFFIXES.put("StartChoice", "\u041d\u0430\u0447\u0430\u043b\u043e\u0412\u044b\u0431\u043e\u0440\u0430");
        SUFFIXES.put("ChoiceProcessing", "\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430\u0412\u044b\u0431\u043e\u0440\u0430");
        SUFFIXES.put("AutoComplete", "\u0410\u0432\u0442\u043e\u041f\u043e\u0434\u0431\u043e\u0440");
        SUFFIXES.put("Clearing", "\u041e\u0447\u0438\u0441\u0442\u043a\u0430");
        SUFFIXES.put("Opening", "\u041e\u0442\u043a\u0440\u044b\u0442\u0438\u0435");
        SUFFIXES.put("Click", "\u041d\u0430\u0436\u0430\u0442\u0438\u0435");
        SUFFIXES.put("OnActivateRow", "\u041f\u0440\u0438\u0410\u043a\u0442\u0438\u0432\u0438\u0437\u0430\u0446\u0438\u0438\u0421\u0442\u0440\u043e\u043a\u0438");
        SUFFIXES.put("BeforeAddRow", "\u041f\u0435\u0440\u0435\u0434\u041d\u0430\u0447\u0430\u043b\u043e\u043c\u0414\u043e\u0431\u0430\u0432\u043b\u0435\u043d\u0438\u044f");
        SUFFIXES.put("BeforeDeleteRow", "\u041f\u0435\u0440\u0435\u0434\u0423\u0434\u0430\u043b\u0435\u043d\u0438\u0435\u043c");
        SUFFIXES.put("BeforeRowChange", "\u041f\u0435\u0440\u0435\u0434\u041d\u0430\u0447\u0430\u043b\u043e\u043c\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u044f");
        SUFFIXES.put("OnStartEdit", "\u041f\u0440\u0438\u041d\u0430\u0447\u0430\u043b\u0435\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u044f");
        SUFFIXES.put("OnEndEdit", "\u041f\u0440\u0438\u041e\u043a\u043e\u043d\u0447\u0430\u043d\u0438\u0438\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u044f");
        SUFFIXES.put("Selection", "\u0412\u044b\u0431\u043e\u0440\u0421\u0442\u0440\u043e\u043a\u0438");
        SUFFIXES.put("OnCurrentPageChange", "\u041f\u0440\u0438\u0421\u043c\u0435\u043d\u0435\u0421\u0442\u0440\u0430\u043d\u0438\u0446\u044b");
        SUFFIXES.put("TextEditEnd", "\u041e\u043a\u043e\u043d\u0447\u0430\u043d\u0438\u0435\u0412\u0432\u043e\u0434\u0430\u0422\u0435\u043a\u0441\u0442\u0430");
        SUFFIXES.put("URLProcessing", "\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430\u041d\u0430\u0432\u0438\u0433\u0430\u0446\u0438\u043e\u043d\u043d\u043e\u0439\u0421\u0441\u044b\u043b\u043a\u0438");
        SUFFIXES.put("DragStart", "\u041d\u0430\u0447\u0430\u043b\u043e\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f");
        SUFFIXES.put("Drag", "\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u0435");
        SUFFIXES.put("DragCheck", "\u041f\u0440\u043e\u0432\u0435\u0440\u043a\u0430\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f");
        SUFFIXES.put("Drop", "\u041f\u043e\u043c\u0435\u0449\u0435\u043d\u0438\u0435");
        SUFFIXES.put("AfterDeleteRow", "\u041f\u043e\u0441\u043b\u0435\u0423\u0434\u0430\u043b\u0435\u043d\u0438\u044f");
    }

    /**
     * Сгенерировать имя BSL-обработчика для события элемента.
     * Если событие известно — {@code elementName + russianSuffix}, иначе
     * {@code elementName + eventName} (калька с английского имени события).
     */
    public static String defaultFor(String elementName, String eventName) {
        String suffix = SUFFIXES.get(eventName);
        return elementName + (suffix != null ? suffix : eventName);
    }

    private EventHandlerNames() {
    }
}

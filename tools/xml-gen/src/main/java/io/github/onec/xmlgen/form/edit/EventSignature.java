package io.github.onec.xmlgen.form.edit;

import java.util.HashMap;
import java.util.Map;

/**
 * Сигнатуры заглушек BSL-обработчиков событий формы 1С.
 * <p>Возвращает параметры процедуры и директиву компиляции (НаКлиенте/НаСервере/…).</p>
 *
 * <p>Элементные события — клиентские по умолчанию. Формальные события
 * ({@code OnCreateAtServer}, {@code OnLoadDataFromSettingsAtServer} и др.) —
 * серверные. Для неизвестных событий fallback: {@code (Элемент)} + {@code &НаКлиенте}.</p>
 */
public final class EventSignature {

    public enum Directive {
        AT_CLIENT("\u041d\u0430\u041a\u043b\u0438\u0435\u043d\u0442\u0435"),
        AT_SERVER("\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435"),
        AT_SERVER_NO_CONTEXT("\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435\u0411\u0435\u0437\u041a\u043e\u043d\u0442\u0435\u043a\u0441\u0442\u0430"),
        AT_CLIENT_AT_SERVER("\u041d\u0430\u041a\u043b\u0438\u0435\u043d\u0442\u0435\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435");

        private final String russianKeyword;

        Directive(String russianKeyword) {
            this.russianKeyword = russianKeyword;
        }

        /** Возвращает полную форму, например, {@code &НаКлиенте}. */
        public String asPragma() {
            return "&" + russianKeyword;
        }
    }

    public static final class Signature {
        public final Directive directive;
        public final String parameters;

        public Signature(Directive directive, String parameters) {
            this.directive = directive;
            this.parameters = parameters;
        }
    }

    private static final Map<String, Signature> FORM_EVENTS = new HashMap<>();
    private static final Map<String, Signature> ELEMENT_EVENTS = new HashMap<>();

    static {
        // --- Form-level ---
        FORM_EVENTS.put("OnCreateAtServer",
                new Signature(Directive.AT_SERVER, "\u041e\u0442\u043a\u0430\u0437, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        FORM_EVENTS.put("BeforeClose",
                new Signature(Directive.AT_CLIENT, "\u041e\u0442\u043a\u0430\u0437, \u0417\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u0435\u0420\u0430\u0431\u043e\u0442\u044b, \u041f\u0440\u0435\u0434\u0443\u043f\u0440\u0435\u0436\u0434\u0435\u043d\u0438\u0435\u0422\u0435\u043a\u0441\u0442, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        FORM_EVENTS.put("BeforeWrite",
                new Signature(Directive.AT_CLIENT, "\u041e\u0442\u043a\u0430\u0437, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u0417\u0430\u043f\u0438\u0441\u0438"));
        FORM_EVENTS.put("BeforeWriteAtServer",
                new Signature(Directive.AT_SERVER, "\u041e\u0442\u043a\u0430\u0437, \u0422\u0435\u043a\u0443\u0449\u0438\u0439\u041e\u0431\u044a\u0435\u043a\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u0417\u0430\u043f\u0438\u0441\u0438"));
        FORM_EVENTS.put("AfterWrite",
                new Signature(Directive.AT_CLIENT, "\u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u0417\u0430\u043f\u0438\u0441\u0438"));
        FORM_EVENTS.put("AfterWriteAtServer",
                new Signature(Directive.AT_SERVER, "\u0422\u0435\u043a\u0443\u0449\u0438\u0439\u041e\u0431\u044a\u0435\u043a\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u0417\u0430\u043f\u0438\u0441\u0438"));
        FORM_EVENTS.put("OnOpen",
                new Signature(Directive.AT_CLIENT, "\u041e\u0442\u043a\u0430\u0437"));
        FORM_EVENTS.put("OnReadAtServer",
                new Signature(Directive.AT_SERVER, "\u0422\u0435\u043a\u0443\u0449\u0438\u0439\u041e\u0431\u044a\u0435\u043a\u0442"));
        FORM_EVENTS.put("OnLoadDataFromSettingsAtServer",
                new Signature(Directive.AT_SERVER, "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438"));
        FORM_EVENTS.put("OnSaveDataInSettingsAtServer",
                new Signature(Directive.AT_SERVER, "\u041d\u0430\u0441\u0442\u0440\u043e\u0439\u043a\u0438"));
        FORM_EVENTS.put("NotificationProcessing",
                new Signature(Directive.AT_CLIENT, "\u0418\u043c\u044f\u0421\u043e\u0431\u044b\u0442\u0438\u044f, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440, \u0418\u0441\u0442\u043e\u0447\u043d\u0438\u043a"));

        // --- Element-level ---
        ELEMENT_EVENTS.put("OnChange",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442"));
        ELEMENT_EVENTS.put("Click",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("StartChoice",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0414\u0430\u043d\u043d\u044b\u0435\u0412\u044b\u0431\u043e\u0440\u0430, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("ChoiceProcessing",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0412\u044b\u0431\u0440\u0430\u043d\u043d\u043e\u0435\u0417\u043d\u0430\u0447\u0435\u043d\u0438\u0435, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("AutoComplete",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0422\u0435\u043a\u0441\u0442, \u0414\u0430\u043d\u043d\u044b\u0435\u0412\u044b\u0431\u043e\u0440\u0430, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u041f\u043e\u043b\u0443\u0447\u0435\u043d\u0438\u044f\u0414\u0430\u043d\u043d\u044b\u0445, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("Clearing",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("Opening",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("TextEditEnd",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0422\u0435\u043a\u0441\u0442, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("URLProcessing",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, URL, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("OnActivateRow",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442"));
        ELEMENT_EVENTS.put("OnActivate",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442"));
        ELEMENT_EVENTS.put("OnStartEdit",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041d\u043e\u0432\u0430\u044f\u0421\u0442\u0440\u043e\u043a\u0430, \u041a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435"));
        ELEMENT_EVENTS.put("OnEndEdit",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041d\u043e\u0432\u0430\u044f\u0421\u0442\u0440\u043e\u043a\u0430, \u041e\u0442\u043c\u0435\u043d\u0430\u0420\u0435\u0434\u0430\u043a\u0442\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u044f"));
        ELEMENT_EVENTS.put("BeforeAddRow",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041e\u0442\u043a\u0430\u0437, \u041a\u043e\u043f\u0438\u0440\u043e\u0432\u0430\u043d\u0438\u0435, \u0420\u043e\u0434\u0438\u0442\u0435\u043b\u044c, \u0413\u0440\u0443\u043f\u043f\u0430, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440"));
        ELEMENT_EVENTS.put("BeforeDeleteRow",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041e\u0442\u043a\u0430\u0437"));
        ELEMENT_EVENTS.put("BeforeRowChange",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041e\u0442\u043a\u0430\u0437"));
        ELEMENT_EVENTS.put("Selection",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0412\u044b\u0431\u0440\u0430\u043d\u043d\u0430\u044f\u0421\u0442\u0440\u043e\u043a\u0430, \u041f\u043e\u043b\u0435, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("OnCurrentPageChange",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u0422\u0435\u043a\u0443\u0449\u0430\u044f\u0421\u0442\u0440\u0430\u043d\u0438\u0446\u0430"));
        ELEMENT_EVENTS.put("DragStart",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("Drag",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("DragCheck",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("Drop",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442, \u041f\u0430\u0440\u0430\u043c\u0435\u0442\u0440\u044b\u041f\u0435\u0440\u0435\u0442\u0430\u0441\u043a\u0438\u0432\u0430\u043d\u0438\u044f, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"));
        ELEMENT_EVENTS.put("AfterDeleteRow",
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442"));
    }

    /**
     * Сигнатура для события элемента. Для неизвестных — fallback ({@code Элемент}, {@code &НаКлиенте}).
     */
    public static Signature forElement(String eventName) {
        return ELEMENT_EVENTS.getOrDefault(eventName,
                new Signature(Directive.AT_CLIENT, "\u042d\u043b\u0435\u043c\u0435\u043d\u0442"));
    }

    /**
     * Сигнатура для события формы. Для неизвестных — fallback ({@code Отказ}, {@code &НаКлиенте}).
     */
    public static Signature forForm(String eventName) {
        return FORM_EVENTS.getOrDefault(eventName,
                new Signature(Directive.AT_CLIENT, "\u041e\u0442\u043a\u0430\u0437"));
    }

    private EventSignature() {
    }
}

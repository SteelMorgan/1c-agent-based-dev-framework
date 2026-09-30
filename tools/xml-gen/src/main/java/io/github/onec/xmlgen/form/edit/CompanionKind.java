package io.github.onec.xmlgen.form.edit;

/**
 * Типы companion-элементов, автоматически создаваемых для элементов формы.
 * <p>Каждый companion имеет XML-тег (например, {@code ContextMenu}) и русский
 * суффикс имени (например, {@code КонтекстноеМеню}), который дописывается
 * к имени родителя для получения имени companion'а.</p>
 */
public enum CompanionKind {

    CONTEXT_MENU("ContextMenu", "\u041a\u043e\u043d\u0442\u0435\u043a\u0441\u0442\u043d\u043e\u0435\u041c\u0435\u043d\u044e"),

    EXTENDED_TOOLTIP("ExtendedTooltip", "\u0420\u0430\u0441\u0448\u0438\u0440\u0435\u043d\u043d\u0430\u044f\u041f\u043e\u0434\u0441\u043a\u0430\u0437\u043a\u0430"),

    AUTO_COMMAND_BAR("AutoCommandBar", "\u041a\u043e\u043c\u0430\u043d\u0434\u043d\u0430\u044f\u041f\u0430\u043d\u0435\u043b\u044c"),

    SEARCH_STRING_ADDITION("SearchStringAddition", "\u0421\u0442\u0440\u043e\u043a\u0430\u041f\u043e\u0438\u0441\u043a\u0430"),

    VIEW_STATUS_ADDITION("ViewStatusAddition", "\u0421\u043e\u0441\u0442\u043e\u044f\u043d\u0438\u0435\u041f\u0440\u043e\u0441\u043c\u043e\u0442\u0440\u0430"),

    SEARCH_CONTROL_ADDITION("SearchControlAddition", "\u0423\u043f\u0440\u0430\u0432\u043b\u0435\u043d\u0438\u0435\u041f\u043e\u0438\u0441\u043a\u043e\u043c");

    private final String xmlTag;
    private final String russianSuffix;

    CompanionKind(String xmlTag, String russianSuffix) {
        this.xmlTag = xmlTag;
        this.russianSuffix = russianSuffix;
    }

    public String getXmlTag() {
        return xmlTag;
    }

    public String getRussianSuffix() {
        return russianSuffix;
    }

    public String nameFor(String elementName) {
        return elementName + russianSuffix;
    }
}

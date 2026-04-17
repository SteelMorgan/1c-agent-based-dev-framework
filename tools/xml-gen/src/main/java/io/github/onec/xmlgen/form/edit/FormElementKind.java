package io.github.onec.xmlgen.form.edit;

import java.util.Arrays;
import java.util.List;

/**
 * Каталог типов элементов управляемой формы 1С.
 * <p>Содержит маппинг: JSON-ключ (из спецификации form-edit) → XML-тег +
 * набор обязательных companion-элементов + имя scope'а для валидации событий.</p>
 */
public enum FormElementKind {

    INPUT("input", "InputField", "input",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    CHECK("check", "CheckBoxField", "check",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    LABEL("label", "LabelDecoration", "label",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    LABEL_FIELD("labelField", "LabelField", "labelField",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    PIC_FIELD("picField", "PictureField", "picField",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    CALENDAR("calendar", "CalendarField", "calendar",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    TABLE("table", "Table", "table",
            List.of(
                    CompanionKind.CONTEXT_MENU,
                    CompanionKind.AUTO_COMMAND_BAR,
                    CompanionKind.SEARCH_STRING_ADDITION,
                    CompanionKind.VIEW_STATUS_ADDITION,
                    CompanionKind.SEARCH_CONTROL_ADDITION)),

    BUTTON("button", "Button", "button",
            List.of(CompanionKind.EXTENDED_TOOLTIP)),

    PICTURE("picture", "PictureDecoration", "picture",
            List.of(CompanionKind.CONTEXT_MENU, CompanionKind.EXTENDED_TOOLTIP)),

    CMD_BAR("cmdBar", "CommandBar", "cmdBar", List.of()),

    POPUP("popup", "Popup", "popup", List.of()),

    GROUP("group", "UsualGroup", "group",
            List.of(CompanionKind.EXTENDED_TOOLTIP)),

    PAGES("pages", "Pages", "pages",
            List.of(CompanionKind.EXTENDED_TOOLTIP)),

    PAGE("page", "Page", "page",
            List.of(CompanionKind.EXTENDED_TOOLTIP));

    private final String jsonKey;
    private final String xmlTag;
    private final String eventScope;
    private final List<CompanionKind> companions;

    FormElementKind(String jsonKey, String xmlTag, String eventScope, List<CompanionKind> companions) {
        this.jsonKey = jsonKey;
        this.xmlTag = xmlTag;
        this.eventScope = eventScope;
        this.companions = companions;
    }

    public String getJsonKey() {
        return jsonKey;
    }

    public String getXmlTag() {
        return xmlTag;
    }

    public String getEventScope() {
        return eventScope;
    }

    public List<CompanionKind> getCompanions() {
        return companions;
    }

    /**
     * Распознать kind по JSON-ключу или XML-тегу. Возвращает null, если не найдено.
     * Регистр игнорируется для jsonKey, но не для xmlTag (они различаются в 1C).
     */
    public static FormElementKind resolve(String input) {
        if (input == null) return null;
        return Arrays.stream(values())
                .filter(k -> k.jsonKey.equalsIgnoreCase(input) || k.xmlTag.equals(input))
                .findFirst()
                .orElse(null);
    }
}

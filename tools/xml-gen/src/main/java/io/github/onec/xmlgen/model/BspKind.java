package io.github.onec.xmlgen.model;

import java.util.Locale;

/**
 * Вид обработки БСП («Дополнительные отчёты и обработки»).
 * <p>
 * См. {@code framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md} §1.
 */
public enum BspKind {
    /** Глобальная обработка. */
    ДополнительнаяОбработка("ВидОбработкиДополнительнаяОбработка", false),
    /** Глобальный отчёт. */
    ДополнительныйОтчет("ВидОбработкиДополнительныйОтчет", false),
    /** Назначаемая: заполнение объекта. */
    ЗаполнениеОбъекта("ВидОбработкиЗаполнениеОбъекта", true),
    /** Назначаемая: отчёт для объекта. */
    Отчет("ВидОбработкиОтчет", true),
    /** Назначаемая: печатная форма. */
    ПечатнаяФорма("ВидОбработкиПечатнаяФорма", true),
    /** Назначаемая: создание связанных объектов. */
    СозданиеСвязанныхОбъектов("ВидОбработкиСозданиеСвязанныхОбъектов", true);

    private final String apiMethodName;
    private final boolean assignable;

    BspKind(String apiMethodName, boolean assignable) {
        this.apiMethodName = apiMethodName;
        this.assignable = assignable;
    }

    /** Имя метода API БСП: {@code ВидОбработки...}. Без скобок. */
    public String apiMethodName() {
        return apiMethodName;
    }

    /** Назначаемый вид требует {@code Назначение}. */
    public boolean requiresTarget() {
        return assignable;
    }

    /** Является ли вид «модификатором» {@code "ПечатьMXL"}. */
    public boolean usesPrintModifier() {
        return this == ПечатнаяФорма;
    }

    /** Тип команды по умолчанию для данного вида. */
    public BspCommandType defaultCommandType() {
        switch (this) {
            case ДополнительнаяОбработка:
            case ДополнительныйОтчет:
            case Отчет:
                return BspCommandType.ОткрытиеФормы;
            case ЗаполнениеОбъекта:
            case ПечатнаяФорма:
            case СозданиеСвязанныхОбъектов:
                return BspCommandType.ВызовСерверногоМетода;
            default:
                throw new IllegalStateException("Unhandled kind: " + this);
        }
    }

    /**
     * Парсер свободной формы (русская/латиница). Сопоставление по таблице SKILL §1.
     */
    public static BspKind parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("BspKind is required");
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace("ё", "е");
        // Точные названия
        switch (s) {
            case "дополнительнаяобработка":
            case "доп обработка":
            case "доп. обработка":
            case "обработка":
            case "глобальная":
            case "глобальная обработка":
                return ДополнительнаяОбработка;
            case "дополнительныйотчет":
            case "дополнительный отчет":
            case "доп отчет":
            case "доп. отчет":
            case "глобальный отчет":
                return ДополнительныйОтчет;
            case "заполнениеобъекта":
            case "заполнение":
            case "заполнить":
            case "заполнение объекта":
                return ЗаполнениеОбъекта;
            case "отчет":
            case "отчет назначаемый":
            case "назначаемый отчет":
                return Отчет;
            case "печатнаяформа":
            case "печатная форма":
            case "печать":
            case "печатные формы":
                return ПечатнаяФорма;
            case "созданиесвязанныхобъектов":
            case "создание связанных объектов":
            case "связанные объекты":
                return СозданиеСвязанныхОбъектов;
            default:
                // Попробуем enum-имя как есть
                for (BspKind k : values()) {
                    if (k.name().equalsIgnoreCase(input.trim())) {
                        return k;
                    }
                }
                throw new IllegalArgumentException(
                        "Unknown BSP kind: '" + input + "'. Allowed: ДополнительнаяОбработка, ДополнительныйОтчет, "
                                + "ЗаполнениеОбъекта, Отчет, ПечатнаяФорма, СозданиеСвязанныхОбъектов");
        }
    }
}

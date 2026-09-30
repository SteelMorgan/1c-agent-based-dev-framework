package io.github.onec.xmlgen.model;

import java.util.Locale;

/**
 * Тип команды БСП.
 * <p>
 * См. {@code framework/skills/tool-usage/platform-data/xml-generation/epf-full/references/epf-bsp.md} §2
 * «Маппинг типов команд».
 */
public enum BspCommandType {
    ОткрытиеФормы("ТипКомандыОткрытиеФормы"),
    ВызовКлиентскогоМетода("ТипКомандыВызовКлиентскогоМетода"),
    ВызовСерверногоМетода("ТипКомандыВызовСерверногоМетода"),
    ЗаполнениеФормы("ТипКомандыЗаполнениеФормы"),
    СценарийВБезопасномРежиме("ТипКомандыСценарийВБезопасномРежиме");

    private final String apiMethodName;

    BspCommandType(String apiMethodName) {
        this.apiMethodName = apiMethodName;
    }

    /** Имя метода API БСП: {@code ТипКоманды...}. Без скобок. */
    public String apiMethodName() {
        return apiMethodName;
    }

    /** Серверный обработчик в ObjectModule (а не клиентский в форме). */
    public boolean isServerHandler() {
        return this == ВызовСерверногоМетода;
    }

    /** Клиентский обработчик в модуле формы. */
    public boolean isClientHandler() {
        return this == ВызовКлиентскогоМетода;
    }

    public static BspCommandType parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("BspCommandType is required");
        }
        String s = input.trim().toLowerCase(Locale.ROOT).replace("ё", "е");
        switch (s) {
            case "открытиеформы":
            case "форма":
            case "открыть форму":
            case "openform":
                return ОткрытиеФормы;
            case "вызовклиентскогометода":
            case "клиентскийметод":
            case "клиентский метод":
            case "на клиенте":
            case "client":
            case "clientmethod":
                return ВызовКлиентскогоМетода;
            case "вызовсерверногометода":
            case "серверныйметод":
            case "серверный метод":
            case "на сервере":
            case "server":
            case "servermethod":
                return ВызовСерверногоМетода;
            case "заполнениеформы":
            case "заполнение формы":
            case "заполнить форму":
                return ЗаполнениеФормы;
            case "сценарийвбезопасномрежиме":
            case "сценарий":
            case "безопасный режим":
                return СценарийВБезопасномРежиме;
            default:
                for (BspCommandType t : values()) {
                    if (t.name().equalsIgnoreCase(input.trim())) {
                        return t;
                    }
                }
                throw new IllegalArgumentException(
                        "Unknown BSP command type: '" + input + "'. Allowed: ОткрытиеФормы, ВызовКлиентскогоМетода, "
                                + "ВызовСерверногоМетода, ЗаполнениеФормы, СценарийВБезопасномРежиме");
        }
    }
}

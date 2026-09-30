package io.github.onec.xmlgen.model;

import java.util.Set;

/**
 * Парсер пути объекта метаданных в формате «Type.Name».
 *
 * <p>Пример: {@code Document.ЗаказКлиента}, {@code Catalog.Контрагенты},
 * {@code Report.ОстаткиТоваров}.
 */
public class MdoPath {

    /** Поддерживаемые типы объектов. */
    public static final Set<String> SUPPORTED_TYPES = Set.of(
            "Catalog", "Document", "Report", "DataProcessor",
            "InformationRegister", "AccumulationRegister", "AccountingRegister",
            "CalculationRegister", "ChartOfCharacteristicTypes", "ChartOfAccounts",
            "ChartOfCalculationTypes", "BusinessProcess", "Task", "ExchangePlan"
    );

    private final String type;
    private final String name;

    private MdoPath(String type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Разобрать строку формата «Type.Name».
     *
     * @param spec строка вида «Document.ЗаказКлиента»
     * @return разобранный путь
     * @throws IllegalArgumentException если формат неверный
     */
    public static MdoPath parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("--object is required (format: Type.Name)");
        }
        int dot = spec.indexOf('.');
        if (dot <= 0 || dot == spec.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid --object format: '" + spec + "'. Expected 'Type.Name', e.g. 'Document.ЗаказКлиента'");
        }
        String type = spec.substring(0, dot);
        String name = spec.substring(dot + 1);
        return new MdoPath(type, name);
    }

    /** Тип объекта (e.g. {@code Document}). */
    public String getType() {
        return type;
    }

    /** Имя объекта (e.g. {@code ЗаказКлиента}). */
    public String getName() {
        return name;
    }

    /**
     * Каталог объекта внутри src-директории.
     * Например, {@code Documents/ЗаказКлиента} для Document.
     */
    public String getRelativeDir() {
        return pluralize(type) + "/" + name;
    }

    /**
     * Вернуть имя XML-файла объекта относительно src (без расширения).
     * Например, {@code Documents/ЗаказКлиента.xml}.
     */
    public String getObjectXmlRelPath() {
        return pluralize(type) + "/" + name + ".xml";
    }

    /** Является ли тип отчётом (Report). */
    public boolean isReport() {
        return "Report".equals(type);
    }

    @Override
    public String toString() {
        return type + "." + name;
    }

    // --- private ---

    private static String pluralize(String type) {
        switch (type) {
            case "Catalog": return "Catalogs";
            case "Document": return "Documents";
            case "Report": return "Reports";
            case "DataProcessor": return "DataProcessors";
            case "InformationRegister": return "InformationRegisters";
            case "AccumulationRegister": return "AccumulationRegisters";
            case "AccountingRegister": return "AccountingRegisters";
            case "CalculationRegister": return "CalculationRegisters";
            case "ChartOfCharacteristicTypes": return "ChartsOfCharacteristicTypes";
            case "ChartOfAccounts": return "ChartsOfAccounts";
            case "ChartOfCalculationTypes": return "ChartsOfCalculationTypes";
            case "BusinessProcess": return "BusinessProcesses";
            case "Task": return "Tasks";
            case "ExchangePlan": return "ExchangePlans";
            default: return type + "s";
        }
    }
}

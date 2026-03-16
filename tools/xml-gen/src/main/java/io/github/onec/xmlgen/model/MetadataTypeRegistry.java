package io.github.onec.xmlgen.model;

import java.util.*;

/**
 * Реестр типов объектов метаданных 1С.
 * Маппинг: тип → XML-элемент, каталог выгрузки, категории GeneratedType,
 * допустимые ChildObjects.
 */
public class MetadataTypeRegistry {

    private static final Map<String, TypeDescriptor> REGISTRY = new LinkedHashMap<>();

    static {
        // Reference types
        reg("Catalog", "Catalogs", "Catalog",
                cats("Object", "Ref", "Selection", "List", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("Document", "Documents", "Document",
                cats("Object", "Ref", "Selection", "List", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("Enum", "Enums", "Enum",
                cats("Ref", "Manager", "List"),
                children("EnumValue", "Form", "Template", "Command"));

        reg("ChartOfAccounts", "ChartsOfAccounts", "ChartOfAccounts",
                cats("Object", "Ref", "Selection", "List", "Manager"),
                children("Attribute", "TabularSection", "AccountingFlag", "ExtDimensionAccountingFlag",
                        "Form", "Template", "Command"));

        reg("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes", "ChartOfCharacteristicTypes",
                cats("Object", "Ref", "Selection", "List", "Characteristic", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("ChartOfCalculationTypes", "ChartsOfCalculationTypes", "ChartOfCalculationTypes",
                cats("Object", "Ref", "Selection", "List", "Manager",
                        "DisplacingCalculationTypes", "DisplacingCalculationTypesRow",
                        "BaseCalculationTypes", "BaseCalculationTypesRow",
                        "LeadingCalculationTypes", "LeadingCalculationTypesRow"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("ExchangePlan", "ExchangePlans", "ExchangePlan",
                cats("Object", "Ref", "Selection", "List", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        // Registers
        reg("InformationRegister", "InformationRegisters", "InformationRegister",
                cats("Record", "Manager", "Selection", "List", "RecordSet", "RecordKey", "RecordManager"),
                children("Dimension", "Resource", "Attribute", "Form", "Template", "Command"));

        reg("AccumulationRegister", "AccumulationRegisters", "AccumulationRegister",
                cats("Record", "Manager", "Selection", "List", "RecordSet", "RecordKey"),
                children("Dimension", "Resource", "Attribute", "Form", "Template", "Command"));

        reg("AccountingRegister", "AccountingRegisters", "AccountingRegister",
                cats("Record", "ExtDimensions", "RecordSet", "RecordKey", "Selection", "List", "Manager"),
                children("Dimension", "Resource", "Attribute", "Form", "Template", "Command"));

        reg("CalculationRegister", "CalculationRegisters", "CalculationRegister",
                cats("Record", "Manager", "Selection", "List", "RecordSet", "RecordKey"),
                children("Dimension", "Resource", "Attribute", "Form", "Template", "Command",
                        "Recalculation"));

        // Processes
        reg("BusinessProcess", "BusinessProcesses", "BusinessProcess",
                cats("Object", "Ref", "Selection", "List", "Manager", "RoutePointRef"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("Task", "Tasks", "Task",
                cats("Object", "Ref", "Selection", "List", "Manager"),
                children("Attribute", "TabularSection", "AddressingAttribute",
                        "Form", "Template", "Command"));

        // Reports & DataProcessors
        reg("Report", "Reports", "Report",
                cats("Object", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        reg("DataProcessor", "DataProcessors", "DataProcessor",
                cats("Object", "Manager"),
                children("Attribute", "TabularSection", "Form", "Template", "Command"));

        // Simple types (no ChildObjects or limited)
        reg("Constant", "Constants", "Constant",
                cats("Manager", "ValueManager", "ValueKey"),
                children());

        reg("DefinedType", "DefinedTypes", "DefinedType",
                cats("DefinedType"),
                children());

        reg("CommonModule", "CommonModules", "CommonModule",
                cats(),
                children());

        reg("ScheduledJob", "ScheduledJobs", "ScheduledJob",
                cats(),
                children());

        reg("EventSubscription", "EventSubscriptions", "EventSubscription",
                cats(),
                children());

        reg("DocumentJournal", "DocumentJournals", "DocumentJournal",
                cats("Selection", "List", "Manager"),
                children("Column", "Form", "Template", "Command"));

        reg("HTTPService", "HTTPServices", "HTTPService",
                cats(),
                children("URLTemplate"));

        reg("WebService", "WebServices", "WebService",
                cats(),
                children("Operation"));
    }

    private static List<String> cats(String... items) {
        return List.of(items);
    }

    private static List<String> children(String... items) {
        return List.of(items);
    }

    private static void reg(String type, String directory, String xmlElement,
                            List<String> categories, List<String> childTypes) {
        REGISTRY.put(type, new TypeDescriptor(type, directory, xmlElement, categories, childTypes));
    }

    /** Получить описатель типа по имени (case-sensitive). */
    public static TypeDescriptor get(String typeName) {
        return REGISTRY.get(typeName);
    }

    /** Все зарегистрированные типы. */
    public static Collection<TypeDescriptor> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /** Найти тип по имени XML-элемента. */
    public static TypeDescriptor byXmlElement(String xmlElement) {
        for (TypeDescriptor td : REGISTRY.values()) {
            if (td.xmlElement().equals(xmlElement)) return td;
        }
        return null;
    }

    /** Найти тип по имени каталога выгрузки. */
    public static TypeDescriptor byDirectory(String dirName) {
        for (TypeDescriptor td : REGISTRY.values()) {
            if (td.directory().equals(dirName)) return td;
        }
        return null;
    }

    /** Это ссылочный тип (имеет Object + Ref)? */
    public static boolean isReferenceType(String typeName) {
        TypeDescriptor td = get(typeName);
        return td != null && td.categories().contains("Object") && td.categories().contains("Ref");
    }

    /** Это регистр? */
    public static boolean isRegister(String typeName) {
        return typeName != null && typeName.endsWith("Register");
    }

    /** Имеет Attribute + TabularSection в ChildObjects? */
    public static boolean hasAttributes(String typeName) {
        TypeDescriptor td = get(typeName);
        return td != null && td.childTypes().contains("Attribute");
    }

    /** Имеет Dimension / Resource? */
    public static boolean hasDimensions(String typeName) {
        TypeDescriptor td = get(typeName);
        return td != null && td.childTypes().contains("Dimension");
    }

    /**
     * Описатель типа объекта метаданных.
     */
    public record TypeDescriptor(
            String type,
            String directory,
            String xmlElement,
            List<String> categories,
            List<String> childTypes
    ) {
        /** Тип имеет табличные части? */
        public boolean hasTabularSections() {
            return childTypes.contains("TabularSection");
        }

        /** Тип имеет формы? */
        public boolean hasForms() {
            return childTypes.contains("Form");
        }
    }
}

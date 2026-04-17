package io.github.onec.xmlgen.form.fromobject;

import java.util.ArrayList;
import java.util.List;

/**
 * POJO-результат парсинга XML объекта метаданных 1С.
 * Универсальный контейнер на все типы, поля проставлены только для релевантного типа.
 */
public class ObjectMeta {

    public static class Field {
        public String name;
        public String synonym;
        public String type;   // сырой "CatalogRef.Номенклатура" / "xs:decimal" / ...
        public boolean isRef;

        public Field(String name, String synonym, String type) {
            this.name = name;
            this.synonym = synonym != null ? synonym : name;
            this.type = type != null ? type : "string";
            this.isRef = this.type.contains("Ref.") || this.type.contains("Ссылка.");
        }
    }

    public static class TabularSection {
        public String name;
        public String synonym;
        public final List<Field> columns = new ArrayList<>();

        public TabularSection(String name, String synonym) {
            this.name = name;
            this.synonym = synonym != null ? synonym : name;
        }
    }

    /** "Catalog" / "Document" / "InformationRegister" / ... */
    public String type;
    public String name;
    public String synonym;

    public final List<Field> attributes = new ArrayList<>();
    public final List<TabularSection> tabularSections = new ArrayList<>();

    // Document
    public String numberType; // String / Number

    // Catalog / CharacteristicTypes / ExchangePlan / ChartOfAccounts
    public int codeLength;
    public int descriptionLength;
    public boolean hierarchical;
    public String hierarchyType;  // HierarchyFoldersAndItems / HierarchyOfItems
    public final List<String> owners = new ArrayList<>();
    public boolean hasValueType;  // CCT

    // Registers
    public final List<Field> dimensions = new ArrayList<>();
    public final List<Field> resources = new ArrayList<>();
    public String periodicity;    // InformationRegister
    public String writeMode;      // InformationRegister: Independent / RecorderSubordinate
    public String registerType;   // AccumulationRegister: Balances / Turnovers

    // ChartOfAccounts
    public int maxExtDimensionCount;
    public final List<Field> accountingFlags = new ArrayList<>();
    public final List<Field> extDimensionAccountingFlags = new ArrayList<>();
}

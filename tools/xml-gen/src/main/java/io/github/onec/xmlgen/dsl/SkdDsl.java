package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для схемы компоновки данных 1С (DataCompositionSchema).
 * 
 * Базовая реализация — поддержка простых запросов без сложных структур.
 */
@Value
public class SkdDsl {
    
    /**
     * Источники данных.
     */
    List<DataSource> dataSources;
    
    /**
     * Наборы данных.
     */
    List<DataSet> dataSets;
    
    /**
     * Параметры.
     */
    List<Parameter> parameters;
    
    /**
     * Итоговые поля.
     */
    List<TotalField> totalFields;
    
    /**
     * Варианты настроек.
     */
    List<SettingsVariant> settingsVariants;
    
    @JsonCreator
    public SkdDsl(
            @JsonProperty("dataSources") List<DataSource> dataSources,
            @JsonProperty("dataSets") List<DataSet> dataSets,
            @JsonProperty("parameters") List<Parameter> parameters,
            @JsonProperty("totalFields") List<TotalField> totalFields,
            @JsonProperty("settingsVariants") List<SettingsVariant> settingsVariants) {
        this.dataSources = dataSources;
        this.dataSets = dataSets;
        this.parameters = parameters;
        this.totalFields = totalFields;
        this.settingsVariants = settingsVariants;
    }
    
    /**
     * Источник данных.
     */
    @Value
    public static class DataSource {
        String name;
        String type; // "Local" или "External"
        
        @JsonCreator
        public DataSource(
                @JsonProperty("name") String name,
                @JsonProperty("type") String type) {
            this.name = name;
            this.type = type;
        }
    }
    
    /**
     * Набор данных.
     * Поддерживает три типа: DataSetQuery, DataSetObject, DataSetUnion
     */
    @Value
    public static class DataSet {
        String name;
        String source;
        String query;           // DataSetQuery
        String objectName;      // DataSetObject
        List<DataSet> items;    // DataSetUnion
        List<Field> fields;
        Boolean autoFillFields;
        
        @JsonCreator
        public DataSet(
                @JsonProperty("name") String name,
                @JsonProperty("source") String source,
                @JsonProperty("query") String query,
                @JsonProperty("objectName") String objectName,
                @JsonProperty("items") List<DataSet> items,
                @JsonProperty("fields") List<Field> fields,
                @JsonProperty("autoFillFields") Boolean autoFillFields) {
            this.name = name;
            this.source = source;
            this.query = query;
            this.objectName = objectName;
            this.items = items;
            this.fields = fields;
            this.autoFillFields = autoFillFields;
        }
        
        /**
         * Определить тип набора данных.
         */
        public String getType() {
            if (query != null) return "DataSetQuery";
            if (objectName != null) return "DataSetObject";
            if (items != null) return "DataSetUnion";
            return "DataSetQuery"; // по умолчанию
        }
    }
    
    /**
     * Поле набора данных.
     */
    @Value
    public static class Field {
        String dataPath;
        String field;
        String title;
        String type;
        
        @JsonCreator
        public Field(
                @JsonProperty("dataPath") String dataPath,
                @JsonProperty("field") String field,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type) {
            this.dataPath = dataPath;
            this.field = field;
            this.title = title;
            this.type = type;
        }
    }
    
    /**
     * Параметр.
     */
    @Value
    public static class Parameter {
        String name;
        String title;
        String type;
        Object value;
        
        @JsonCreator
        public Parameter(
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type,
                @JsonProperty("value") Object value) {
            this.name = name;
            this.title = title;
            this.type = type;
            this.value = value;
        }
    }
    
    /**
     * Итоговое поле.
     */
    @Value
    public static class TotalField {
        String dataPath;
        String expression;
        
        @JsonCreator
        public TotalField(
                @JsonProperty("dataPath") String dataPath,
                @JsonProperty("expression") String expression) {
            this.dataPath = dataPath;
            this.expression = expression;
        }
    }
    
    /**
     * Вариант настроек.
     */
    @Value
    public static class SettingsVariant {
        String name;
        String presentation;
        Settings settings;
        
        @JsonCreator
        public SettingsVariant(
                @JsonProperty("name") String name,
                @JsonProperty("presentation") String presentation,
                @JsonProperty("settings") Settings settings) {
            this.name = name;
            this.presentation = presentation;
            this.settings = settings;
        }
    }
    
    /**
     * Элемент условного оформления.
     */
    @Value
    public static class ConditionalAppearanceItem {
        List<String> selection;
        List<String> filter;
        Map<String, Object> appearance;
        String presentation;
        
        @JsonCreator
        public ConditionalAppearanceItem(
                @JsonProperty("selection") List<String> selection,
                @JsonProperty("filter") List<String> filter,
                @JsonProperty("appearance") Map<String, Object> appearance,
                @JsonProperty("presentation") String presentation) {
            this.selection = selection;
            this.filter = filter;
            this.appearance = appearance;
            this.presentation = presentation;
        }
    }
    
    /**
     * Настройки варианта.
     */
    @Value
    public static class Settings {
        List<String> selection;
        List<String> filter;
        List<String> order;
        List<ConditionalAppearanceItem> conditionalAppearance;
        Map<String, Object> outputParameters;
        List<Structure> structure;
        
        @JsonCreator
        public Settings(
                @JsonProperty("selection") List<String> selection,
                @JsonProperty("filter") List<String> filter,
                @JsonProperty("order") List<String> order,
                @JsonProperty("conditionalAppearance") List<ConditionalAppearanceItem> conditionalAppearance,
                @JsonProperty("outputParameters") Map<String, Object> outputParameters,
                @JsonProperty("structure") List<Structure> structure) {
            this.selection = selection;
            this.filter = filter;
            this.order = order;
            this.conditionalAppearance = conditionalAppearance;
            this.outputParameters = outputParameters;
            this.structure = structure;
        }
    }
    
    /**
     * Элемент структуры (группировка).
     */
    @Value
    public static class Structure {
        String type; // "group"
        List<String> groupBy;
        List<String> selection;
        
        @JsonCreator
        public Structure(
                @JsonProperty("type") String type,
                @JsonProperty("groupBy") List<String> groupBy,
                @JsonProperty("selection") List<String> selection) {
            this.type = type;
            this.groupBy = groupBy;
            this.selection = selection;
        }
    }
}

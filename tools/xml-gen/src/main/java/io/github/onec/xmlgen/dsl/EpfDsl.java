package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * JSON DSL для внешней обработки (EPF).
 */
@Value
public class EpfDsl {
    
    /**
     * Имя обработки (латиница или кириллица).
     */
    String name;
    
    /**
     * Синоним (представление).
     */
    String synonym;
    
    /**
     * Имя основной формы (опционально).
     */
    @JsonProperty("default_form")
    String defaultForm;
    
    /**
     * Реквизиты обработки.
     */
    List<Attribute> attributes;
    
    /**
     * Табличные части обработки.
     */
    @JsonProperty("tabular_sections")
    List<TabularSection> tabularSections;
    
    /**
     * Формы обработки (только имена).
     */
    List<String> forms;
    
    /**
     * Макеты обработки.
     */
    List<Template> templates;
    
    /**
     * Реквизит обработки.
     */
    @Value
    public static class Attribute {
        String name;
        String synonym;
        String type;  // DSL тип (string(10), number(10,2) и т.д.)
    }
    
    /**
     * Табличная часть обработки.
     */
    @Value
    public static class TabularSection {
        String name;
        String synonym;
        List<Attribute> attributes;
    }
    
    /**
     * Макет обработки.
     */
    @Value
    public static class Template {
        String name;
        String synonym;
        
        /**
         * Тип макета: SpreadsheetDocument, HTMLDocument, TextDocument, BinaryData.
         */
        String type;
    }
}

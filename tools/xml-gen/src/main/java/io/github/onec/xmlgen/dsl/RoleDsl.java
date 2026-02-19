package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для роли 1С.
 */
@Value
public class RoleDsl {
    
    /**
     * Имя роли (латиница или кириллица).
     */
    String name;
    
    /**
     * Синоним (представление).
     */
    String synonym;
    
    /**
     * Комментарий.
     */
    String comment;
    
    /**
     * Устанавливать права для новых объектов конфигурации.
     */
    @JsonProperty("setForNewObjects")
    Boolean setForNewObjects;
    
    /**
     * Устанавливать права для реквизитов по умолчанию.
     */
    @JsonProperty("setForAttributesByDefault")
    Boolean setForAttributesByDefault;
    
    /**
     * Независимые права подчинённых объектов.
     */
    @JsonProperty("independentRightsOfChildObjects")
    Boolean independentRightsOfChildObjects;
    
    /**
     * Объекты метаданных с правами.
     */
    List<ObjectRights> objects;
    
    /**
     * Шаблоны ограничений (RLS).
     */
    List<RestrictionTemplate> templates;
    
    @JsonCreator
    public RoleDsl(
            @JsonProperty("name") String name,
            @JsonProperty("synonym") String synonym,
            @JsonProperty("comment") String comment,
            @JsonProperty("setForNewObjects") Boolean setForNewObjects,
            @JsonProperty("setForAttributesByDefault") Boolean setForAttributesByDefault,
            @JsonProperty("independentRightsOfChildObjects") Boolean independentRightsOfChildObjects,
            @JsonProperty("objects") List<ObjectRights> objects,
            @JsonProperty("templates") List<RestrictionTemplate> templates) {
        this.name = name;
        this.synonym = synonym;
        this.comment = comment;
        this.setForNewObjects = setForNewObjects;
        this.setForAttributesByDefault = setForAttributesByDefault;
        this.independentRightsOfChildObjects = independentRightsOfChildObjects;
        this.objects = objects;
        this.templates = templates;
    }
    
    /**
     * Права объекта метаданных.
     */
    @Value
    public static class ObjectRights {
        /**
         * Полное имя объекта: Catalog.Name, Document.Name и т.д.
         */
        String name;
        
        /**
         * Пресет прав: "view", "edit", "full".
         */
        String preset;
        
        /**
         * Права: {"Read": true, "Insert": true} или ["Read", "Insert"].
         */
        Object rights;
        
        /**
         * RLS (Row Level Security): {"Read": "условие или #шаблон"}.
         */
        Map<String, String> rls;
        
        @JsonCreator
        public ObjectRights(
                @JsonProperty("name") String name,
                @JsonProperty("preset") String preset,
                @JsonProperty("rights") Object rights,
                @JsonProperty("rls") Map<String, String> rls) {
            this.name = name;
            this.preset = preset;
            this.rights = rights;
            this.rls = rls;
        }
    }
    
    /**
     * Шаблон ограничения (RLS).
     */
    @Value
    public static class RestrictionTemplate {
        /**
         * Имя шаблона с параметрами: "ДляОбъекта(Модификатор)".
         */
        String name;
        
        /**
         * Текст условия на языке шаблонов ограничений 1С.
         */
        String condition;
        
        @JsonCreator
        public RestrictionTemplate(
                @JsonProperty("name") String name,
                @JsonProperty("condition") String condition) {
            this.name = name;
            this.condition = condition;
        }
    }
}

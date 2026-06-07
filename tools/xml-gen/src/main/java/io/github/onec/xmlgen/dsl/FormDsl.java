package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для управляемой формы 1С.
 */
@Value
public class FormDsl {
    
    /**
     * Заголовок формы.
     */
    String title;
    
    /**
     * Свойства формы (autoTitle, windowOpeningMode и т.д.).
     */
    Map<String, Object> properties;
    
    /**
     * Исключённые стандартные команды.
     */
    List<String> excludedCommands;
    
    /**
     * События формы: {"OnCreateAtServer": "ПриСозданииНаСервере"}.
     */
    Map<String, String> events;
    
    /**
     * UI-элементы формы (дерево).
     */
    List<Map<String, Object>> elements;
    
    /**
     * Реквизиты формы.
     */
    List<Attribute> attributes;
    
    /**
     * Параметры формы.
     */
    List<Parameter> parameters;
    
    /**
     * Команды формы.
     */
    List<Command> commands;
    
    @JsonCreator
    public FormDsl(
            @JsonProperty("title") String title,
            @JsonProperty("properties") Map<String, Object> properties,
            @JsonProperty("excludedCommands") List<String> excludedCommands,
            @JsonProperty("events") Map<String, String> events,
            @JsonProperty("elements") List<Map<String, Object>> elements,
            @JsonProperty("attributes") List<Attribute> attributes,
            @JsonProperty("parameters") List<Parameter> parameters,
            @JsonProperty("commands") List<Command> commands) {
        this.title = title;
        this.properties = properties;
        this.excludedCommands = excludedCommands;
        this.events = events;
        this.elements = elements;
        this.attributes = attributes;
        this.parameters = parameters;
        this.commands = commands;
    }
    
    /**
     * Реквизит формы.
     */
    @Value
    public static class Attribute {
        /**
         * Имя реквизита.
         */
        String name;

        /**
         * Заголовок.
         */
        String title;

        /**
         * Тип (DSL формат: string(100), number(10,2) и т.д.).
         */
        String type;

        /**
         * Основной реквизит (Объект).
         */
        Boolean main;

        /**
         * Колонки (для ValueTable/ValueTree).
         */
        List<Column> columns;

        /**
         * Настройки DynamicList: {@code {"mainTable":"Catalog.XXX", "dynamicDataRead":true}}.
         */
        Map<String, Object> settings;

        /**
         * Флаг SavedData (для InformationRegisterRecordManager и подобных).
         */
        Boolean savedData;

        //++agent TASK-174 [07.06.2026 11:10:00]
        // Аудит порта (форм): form-dsl-spec.md §5 объявляет ключ fillChecking
        // (Show | DontShow), порт его молча терял — поле не было объявлено в DSL.
        /**
         * Проверка заполнения: {@code Show} | {@code DontShow} → {@code <FillChecking>}.
         */
        String fillChecking;
        //++agent TASK-174

        /**
         * Поле для секции {@code <UseAlways><Field>…</Field></UseAlways>}.
         * Для форм объектов с движениями (документы) платформа требует
         * {@code Объект.RegisterRecords}, иначе наборы записей не подгружаются
         * на форму. См. эталоны Documents/big_Order_OKX. Если null — секция не пишется.
         */
        String useAlwaysField;

        /** Полный конструктор (используется from-object генерацией и JSON DSL). */
        @JsonCreator
        public Attribute(
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type,
                @JsonProperty("main") Boolean main,
                @JsonProperty("columns") List<Column> columns,
                @JsonProperty("settings") Map<String, Object> settings,
                @JsonProperty("savedData") Boolean savedData,
                @JsonProperty("useAlwaysField") String useAlwaysField,
                @JsonProperty("fillChecking") String fillChecking) {
            this.name = name;
            this.title = title;
            this.type = type;
            this.main = main;
            this.columns = columns;
            this.settings = settings;
            this.savedData = savedData;
            this.useAlwaysField = useAlwaysField;
            this.fillChecking = fillChecking;
        }

        /** Обратно-совместимый 8-аргументный конструктор (без fillChecking). */
        public Attribute(String name, String title, String type, Boolean main,
                         List<Column> columns, Map<String, Object> settings, Boolean savedData,
                         String useAlwaysField) {
            this(name, title, type, main, columns, settings, savedData, useAlwaysField, null);
        }

        /** Обратно-совместимый 7-аргументный конструктор (без useAlwaysField). */
        public Attribute(String name, String title, String type, Boolean main,
                         List<Column> columns, Map<String, Object> settings, Boolean savedData) {
            this(name, title, type, main, columns, settings, savedData, null, null);
        }

        /** Обратно-совместимый 5-аргументный конструктор. */
        public Attribute(String name, String title, String type, Boolean main, List<Column> columns) {
            this(name, title, type, main, columns, null, null, null);
        }
    }
    
    /**
     * Колонка коллекции (ValueTable/ValueTree).
     */
    @Value
    public static class Column {
        String name;
        String title;
        String type;
        
        @JsonCreator
        public Column(
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type) {
            this.name = name;
            this.title = title;
            this.type = type;
        }
    }
    
    /**
     * Параметр формы.
     */
    @Value
    public static class Parameter {
        String name;
        String title;
        String type;

        //++agent TASK-174 [07.06.2026 11:10:00]
        // Аудит порта (форм): form-dsl-spec.md §6 объявляет ключ key (→ <KeyParameter>),
        // порт его молча терял — поле не было объявлено в DSL.
        /** Ключевой параметр формы → {@code <KeyParameter>true</KeyParameter>}. */
        Boolean key;

        @JsonCreator
        public Parameter(
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type,
                @JsonProperty("key") Boolean key) {
            this.name = name;
            this.title = title;
            this.type = type;
            this.key = key;
        }

        /** Обратно-совместимый 3-аргументный конструктор (без key). */
        public Parameter(String name, String title, String type) {
            this(name, title, type, null);
        }
        //++agent TASK-174
    }
    
    /**
     * Команда формы.
     */
    @Value
    public static class Command {
        String name;
        String title;
        String action;
        String tooltip;

        //++agent TASK-174 [07.06.2026 11:10:00]
        // Аудит порта (форм): form-dsl-spec.md §7 объявляет ключи shortcut, picture,
        // representation — порт их молча терял (полей не было в DSL).
        /** Клавиатурное сочетание → {@code <Shortcut>} (например {@code Ctrl+S}). */
        String shortcut;
        /** Ссылка на картинку ({@code StdPicture.Name}) → {@code <Picture><xr:Ref>}. */
        String picture;
        /** Представление: Auto | Picture | Text | PictureAndText → {@code <Representation>}. */
        String representation;
        //++agent TASK-174

        @JsonCreator
        public Command(
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("action") String action,
                @JsonProperty("tooltip") String tooltip,
                @JsonProperty("shortcut") String shortcut,
                @JsonProperty("picture") String picture,
                @JsonProperty("representation") String representation) {
            this.name = name;
            this.title = title;
            this.action = action;
            this.tooltip = tooltip;
            this.shortcut = shortcut;
            this.picture = picture;
            this.representation = representation;
        }

        /** Обратно-совместимый 4-аргументный конструктор. */
        public Command(String name, String title, String action, String tooltip) {
            this(name, title, action, tooltip, null, null, null);
        }
    }
}

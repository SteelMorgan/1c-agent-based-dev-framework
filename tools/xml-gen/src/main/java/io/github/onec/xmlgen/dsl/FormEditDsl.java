package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для мутации существующей формы 1С.
 * Используется подкомандой {@code form edit --json <spec>}.
 *
 * <p>Отличается от {@link FormDsl} тем, что описывает инкрементальные добавки
 * (атрибуты, элементы, команды, события), а не форму целиком.</p>
 */
@Value
public class FormEditDsl {

    List<FormDsl.Attribute> attributes;
    List<FormDsl.Command> commands;
    List<Element> elements;
    List<FormEvent> formEvents;
    List<ElementEvent> elementEvents;

    @JsonCreator
    public FormEditDsl(
            @JsonProperty("attributes") List<FormDsl.Attribute> attributes,
            @JsonProperty("commands") List<FormDsl.Command> commands,
            @JsonProperty("elements") List<Element> elements,
            @JsonProperty("formEvents") List<FormEvent> formEvents,
            @JsonProperty("elementEvents") List<ElementEvent> elementEvents) {
        this.attributes = attributes;
        this.commands = commands;
        this.elements = elements;
        this.formEvents = formEvents;
        this.elementEvents = elementEvents;
    }

    /**
     * Элемент формы для добавления. Поддерживает input / check / label / labelField /
     * picField / calendar / table / button / picture / cmdBar / popup / group /
     * pages / page и др. (см. FormElementKind).
     */
    @Value
    public static class Element {
        String kind;
        String name;
        String title;
        String type;
        String dataPath;
        String into;
        String after;
        String before;
        String command;
        List<Action> actions;
        List<EventBinding> on;
        Map<String, String> handlers;
        List<FormDsl.Column> columns;
        Map<String, Object> properties;
        List<Element> children;

        @JsonCreator
        public Element(
                @JsonProperty("kind") String kind,
                @JsonProperty("name") String name,
                @JsonProperty("title") String title,
                @JsonProperty("type") String type,
                @JsonProperty("dataPath") String dataPath,
                @JsonProperty("into") String into,
                @JsonProperty("after") String after,
                @JsonProperty("before") String before,
                @JsonProperty("command") String command,
                @JsonProperty("actions") List<Action> actions,
                @JsonProperty("on") List<EventBinding> on,
                @JsonProperty("handlers") Map<String, String> handlers,
                @JsonProperty("columns") List<FormDsl.Column> columns,
                @JsonProperty("properties") Map<String, Object> properties,
                @JsonProperty("children") List<Element> children) {
            this.kind = kind;
            this.name = name;
            this.title = title;
            this.type = type;
            this.dataPath = dataPath;
            this.into = into;
            this.after = after;
            this.before = before;
            this.command = command;
            this.actions = actions;
            this.on = on;
            this.handlers = handlers;
            this.columns = columns;
            this.properties = properties;
            this.children = children;
        }
    }

    /**
     * Привязка события элемента. Поддерживает как строку ("OnChange"), так и объект
     * с callType / handler.
     */
    @Value
    public static class EventBinding {
        String event;
        String handler;
        String callType;

        @JsonCreator
        public EventBinding(JsonNode node) {
            String event = null;
            String handler = null;
            String callType = null;
            if (node != null) {
                if (node.isTextual()) {
                    event = node.asText();
                } else if (node.isObject()) {
                    JsonNode eventNode = node.get("event");
                    JsonNode handlerNode = node.get("handler");
                    JsonNode callTypeNode = node.get("callType");
                    event = eventNode != null && !eventNode.isNull() ? eventNode.asText() : null;
                    handler = handlerNode != null && !handlerNode.isNull() ? handlerNode.asText() : null;
                    callType = callTypeNode != null && !callTypeNode.isNull() ? callTypeNode.asText() : null;
                }
            }
            this.event = event;
            this.handler = handler;
            this.callType = callType;
        }
    }

    /**
     * Action для Command (handler + опциональный callType для расширений).
     */
    @Value
    public static class Action {
        String handler;
        String callType;

        @JsonCreator
        public Action(
                @JsonProperty("handler") String handler,
                @JsonProperty("callType") String callType) {
            this.handler = handler;
            this.callType = callType;
        }
    }

    /**
     * Событие уровня формы.
     */
    @Value
    public static class FormEvent {
        String name;
        String handler;
        String callType;

        @JsonCreator
        public FormEvent(
                @JsonProperty("name") String name,
                @JsonProperty("handler") String handler,
                @JsonProperty("callType") String callType) {
            this.name = name;
            this.handler = handler;
            this.callType = callType;
        }
    }

    /**
     * Инъекция события в существующий элемент.
     */
    @Value
    public static class ElementEvent {
        String element;
        String name;
        String handler;
        String callType;

        @JsonCreator
        public ElementEvent(
                @JsonProperty("element") String element,
                @JsonProperty("name") String name,
                @JsonProperty("handler") String handler,
                @JsonProperty("callType") String callType) {
            this.element = element;
            this.name = name;
            this.handler = handler;
            this.callType = callType;
        }
    }
}

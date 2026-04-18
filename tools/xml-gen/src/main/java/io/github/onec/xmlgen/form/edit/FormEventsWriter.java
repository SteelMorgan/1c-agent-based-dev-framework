package io.github.onec.xmlgen.form.edit;

import io.github.onec.xmlgen.validator.XmlNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;
import static io.github.onec.xmlgen.editor.EditorUtils.findOrCreateChild;

/**
 * Запись {@code <Events>} в Form.xml: form-level (у корня) и element-level
 * (у конкретного элемента / инъекция).
 *
 * <p>Собирает список имён handler'ов, подлежащих дальнейшей генерации
 * заглушек в Module.bsl — см. {@link BslStubWriter}.</p>
 */
public class FormEventsWriter {

    /** Описание созданного привязкой handler'а — для дальнейшего stub-writing. */
    public static final class HandlerRef {
        public final String name;
        public final boolean formLevel;
        public final String eventName;

        public HandlerRef(String name, boolean formLevel, String eventName) {
            this.name = name;
            this.formLevel = formLevel;
            this.eventName = eventName;
        }
    }

    /**
     * Добавить список element-level событий к элементу (из {@code on:[]} + {@code handlers:{}}).
     * Создаёт {@code <Events>}-контейнер внутри {@code element}, если его нет.
     *
     * @return список созданных handler-имён для последующей BSL-генерации.
     */
    public List<HandlerRef> writeElementEvents(
            XmlNode element,
            String elementName,
            List<FormEditDslBindings> bindings,
            Map<String, String> overrideHandlers) {

        List<HandlerRef> created = new ArrayList<>();
        if (bindings == null || bindings.isEmpty()) return created;

        XmlNode eventsNode = findOrCreateChild(element, "Events");
        for (FormEditDslBindings b : bindings) {
            String event = b.eventName;
            if (event == null || event.isEmpty()) continue;

            String handler = b.explicitHandler;
            if (handler == null && overrideHandlers != null) {
                handler = overrideHandlers.get(event);
            }
            if (handler == null) {
                handler = EventHandlerNames.defaultFor(elementName, event);
            }

            XmlNode evt = createNode("Event");
            evt.setAttribute("name", event);
            if (b.callType != null) {
                evt.setAttribute("callType", b.callType);
            }
            evt.setText(handler);
            eventsNode.addChild(evt);
            created.add(new HandlerRef(handler, false, event));
        }
        return created;
    }

    /**
     * Добавить form-level событие в корневой {@code <Events>}.
     */
    public HandlerRef writeFormEvent(XmlNode root, String eventName, String handler, String callType) {
        if (eventName == null) {
            throw new IllegalArgumentException("formEvent.name is required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("formEvent.handler is required");
        }
        XmlNode eventsNode = findOrCreateChild(root, "Events");
        XmlNode evt = createNode("Event");
        evt.setAttribute("name", eventName);
        if (callType != null) {
            evt.setAttribute("callType", callType);
        }
        evt.setText(handler);
        eventsNode.addChild(evt);
        return new HandlerRef(handler, true, eventName);
    }

    /**
     * Инъекция element-level события в УЖЕ существующий элемент (найденный по имени).
     * Родитель должен быть предоставлен вызывающим (находится через {@code findElement}).
     */
    public HandlerRef injectElementEvent(XmlNode element, String eventName, String handler, String callType) {
        if (eventName == null || handler == null) {
            throw new IllegalArgumentException("elementEvent requires both name and handler");
        }
        XmlNode eventsNode = findOrCreateChild(element, "Events");
        XmlNode evt = createNode("Event");
        evt.setAttribute("name", eventName);
        if (callType != null) {
            evt.setAttribute("callType", callType);
        }
        evt.setText(handler);
        eventsNode.addChild(evt);
        return new HandlerRef(handler, false, eventName);
    }

    /** Упрощённое представление одного element-event binding из JSON DSL. */
    public static final class FormEditDslBindings {
        public final String eventName;
        public final String explicitHandler;
        public final String callType;

        public FormEditDslBindings(String eventName, String explicitHandler, String callType) {
            this.eventName = eventName;
            this.explicitHandler = explicitHandler;
            this.callType = callType;
        }
    }
}

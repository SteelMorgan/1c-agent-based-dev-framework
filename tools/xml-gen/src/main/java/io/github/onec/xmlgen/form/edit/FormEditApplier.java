package io.github.onec.xmlgen.form.edit;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.FormEditDsl;
import io.github.onec.xmlgen.editor.FormEditor;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Оркестратор применения {@link FormEditDsl} к существующей форме через {@link FormEditor}.
 *
 * <p>Порядок применения: attributes → commands → elements → formEvents → elementEvents.
 * Такой порядок гарантирует, что DataPath ссылки на атрибуты и Button.CommandName ссылки
 * разрешаются — к моменту создания элементов атрибуты и команды уже существуют.</p>
 *
 * <p>События приводят к дописыванию заглушек в {@code Ext/Form/Module.bsl} через
 * {@link BslStubWriter}, если путь к Form.xml передан в конструктор.</p>
 */
public class FormEditApplier {

    private final FormEditor editor;
    private final FormEventsWriter eventsWriter = new FormEventsWriter();
    private final BslStubWriter bslStubWriter;
    private final List<FormEventsWriter.HandlerRef> pendingHandlers = new ArrayList<>();

    public FormEditApplier(FormEditor editor) {
        this(editor, null);
    }

    public FormEditApplier(FormEditor editor, Path formXmlPath) {
        this.editor = editor;
        this.bslStubWriter = formXmlPath != null ? new BslStubWriter(formXmlPath) : null;
    }

    /**
     * Применить спецификацию к форме. BSL-заглушки дописываются в конце,
     * если {@code formXmlPath} задан и есть отложенные handler'ы.
     */
    public void apply(FormEditDsl spec) {
        if (spec == null) return;

        if (spec.getAttributes() != null) {
            for (FormDsl.Attribute a : spec.getAttributes()) {
                applyAttribute(a);
            }
        }
        if (spec.getCommands() != null) {
            for (FormDsl.Command c : spec.getCommands()) {
                applyCommand(c);
            }
        }
        if (spec.getElements() != null) {
            for (FormEditDsl.Element e : spec.getElements()) {
                applyElement(e, null);
            }
        }
        if (spec.getFormEvents() != null) {
            for (FormEditDsl.FormEvent fe : spec.getFormEvents()) {
                applyFormEvent(fe);
            }
        }
        if (spec.getElementEvents() != null) {
            for (FormEditDsl.ElementEvent ee : spec.getElementEvents()) {
                applyElementEvent(ee);
            }
        }

        flushBslStubs();
    }

    /** Список handler-имён, ранее отсутствовавших и дописанных в Module.bsl на прошлом apply(). */
    private List<String> lastBslStubsAdded = List.of();

    public List<String> getLastBslStubsAdded() {
        return lastBslStubsAdded;
    }

    private void applyAttribute(FormDsl.Attribute a) {
        if (a.getName() == null) {
            throw new IllegalArgumentException("attribute.name is required");
        }
        editor.addAttribute(
                a.getName(),
                a.getTitle(),
                a.getType(),
                a.getMain(),
                a.getSavedData(),
                a.getColumns(),
                a.getFillChecking(),
                a.getUseAlwaysField()
        );
    }

    private void applyCommand(FormDsl.Command c) {
        if (c.getName() == null) {
            throw new IllegalArgumentException("command.name is required");
        }
        editor.addCommand(c.getName(), c.getTitle(), c.getAction(),
                c.getTooltip(), c.getShortcut(), c.getPicture(), c.getRepresentation());
    }

    private void applyElement(FormEditDsl.Element e, String parentOverride) {
        if (e.getName() == null) {
            throw new IllegalArgumentException("element.name is required");
        }
        if (e.getKind() == null) {
            throw new IllegalArgumentException(
                "element.kind is required (e.g. input, table, button, group, pages, или XML-тег InputField)");
        }
        String parent = parentOverride != null ? parentOverride : e.getInto();
        //**agent TASK-174 [05.06.2026 00:00:00]
        // XG-02: пробрасываем command в FormEditor (раньше терялся → кнопка без CommandName).
        editor.addElement(e.getKind(), e.getName(), e.getDataPath(), parent, e.getAfter(), e.getCommand());
        //**agent TASK-174

        // Привязать события к только что созданному элементу
        if (e.getOn() != null && !e.getOn().isEmpty()) {
            XmlNode created = findElement(e.getName());
            if (created != null) {
                List<FormEventsWriter.FormEditDslBindings> bindings = new ArrayList<>();
                for (FormEditDsl.EventBinding b : e.getOn()) {
                    bindings.add(new FormEventsWriter.FormEditDslBindings(
                            b.getEvent(), b.getHandler(), b.getCallType()));
                }
                pendingHandlers.addAll(
                        eventsWriter.writeElementEvents(created, e.getName(), bindings, e.getHandlers()));
            }
        }

        if (e.getChildren() != null) {
            for (FormEditDsl.Element child : e.getChildren()) {
                applyElement(child, e.getName());
            }
        }
    }

    private void applyFormEvent(FormEditDsl.FormEvent fe) {
        XmlNode root = editor.getDocument().getRoot();
        FormEventsWriter.HandlerRef ref = eventsWriter.writeFormEvent(
                root, fe.getName(), fe.getHandler(), fe.getCallType());
        pendingHandlers.add(ref);
    }

    private void applyElementEvent(FormEditDsl.ElementEvent ee) {
        if (ee.getElement() == null) {
            throw new IllegalArgumentException("elementEvent.element is required");
        }
        XmlNode target = findElement(ee.getElement());
        if (target == null) {
            throw new IllegalArgumentException("element not found: " + ee.getElement());
        }
        FormEventsWriter.HandlerRef ref = eventsWriter.injectElementEvent(
                target, ee.getName(), ee.getHandler(), ee.getCallType());
        pendingHandlers.add(ref);
    }

    private void flushBslStubs() {
        if (bslStubWriter == null || pendingHandlers.isEmpty()) {
            lastBslStubsAdded = List.of();
            pendingHandlers.clear();
            return;
        }
        try {
            lastBslStubsAdded = bslStubWriter.appendStubs(pendingHandlers);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write BSL stubs: " + e.getMessage(), e);
        }
        pendingHandlers.clear();
    }

    private XmlNode findElement(String name) {
        XmlNode root = editor.getDocument().getRoot();
        XmlNode childItems = root.child("ChildItems");
        if (childItems == null) return null;
        return findElementRecursive(childItems, name);
    }

    private static XmlNode findElementRecursive(XmlNode node, String name) {
        if (name.equals(node.attr("name"))) return node;
        for (XmlNode child : node.getChildren()) {
            XmlNode found = findElementRecursive(child, name);
            if (found != null) return found;
        }
        return null;
    }
}

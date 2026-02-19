package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlNode;

/**
 * Общие утилиты для XML-редакторов.
 */
public final class EditorUtils {

    private EditorUtils() {}

    /**
     * Создать новый XML-узел. Поддерживает prefix-нотацию ("v8:item" → prefix="v8", name="item").
     */
    public static XmlNode createNode(String name) {
        String prefix = "";
        String localName = name;
        if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            prefix = parts[0];
            localName = parts[1];
        }
        return XmlNode.builder().name(localName).prefix(prefix).build();
    }

    /**
     * Найти дочерний элемент по имени или создать новый, если не найден.
     */
    public static XmlNode findOrCreateChild(XmlNode parent, String name) {
        return parent.getChildren().stream()
                .filter(c -> c.getName().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    XmlNode node = createNode(name);
                    parent.addChild(node);
                    return node;
                });
    }
}

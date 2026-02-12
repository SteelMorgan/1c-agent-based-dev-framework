package io.github.onec.xmlgen.validator;

import lombok.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML-узел, полученный из StAX-парсинга.
 * Содержит имя, namespace, атрибуты, текст, дочерние узлы и номер строки.
 */
@Value
public class XmlNode {
    /** Локальное имя элемента */
    String name;
    /** Namespace URI (пустая строка = без namespace) */
    String namespace;
    /** Атрибуты элемента (prefix:local → value) */
    Map<String, String> attributes;
    /** Текстовое содержимое (конкатенация всех text-нод) */
    String text;
    /** Дочерние элементы */
    List<XmlNode> children;
    /** Номер строки в исходном файле (1-based, 0 = неизвестно) */
    int line;

    /**
     * Найти первый дочерний элемент по имени.
     */
    public XmlNode child(String childName) {
        return children.stream()
                .filter(c -> c.getName().equals(childName))
                .findFirst()
                .orElse(null);
    }

    /**
     * Найти все дочерние элементы по имени.
     */
    public List<XmlNode> children(String childName) {
        List<XmlNode> result = new ArrayList<>();
        for (XmlNode c : children) {
            if (c.getName().equals(childName)) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * Получить текст первого дочернего элемента по имени.
     * Удобно для паттерна: {@code node.childText("name")}
     */
    public String childText(String childName) {
        XmlNode c = child(childName);
        return c != null ? c.getText() : null;
    }

    /**
     * Есть ли дочерний элемент с данным именем.
     */
    public boolean hasChild(String childName) {
        return children.stream().anyMatch(c -> c.getName().equals(childName));
    }

    /**
     * Получить значение атрибута.
     */
    public String attr(String attrName) {
        return attributes.get(attrName);
    }

    /**
     * Builder для построения узла при парсинге.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name = "";
        private String namespace = "";
        private final Map<String, String> attributes = new LinkedHashMap<>();
        private final StringBuilder text = new StringBuilder();
        private final List<XmlNode> children = new ArrayList<>();
        private int line = 0;

        public Builder name(String name) { this.name = name; return this; }
        public Builder namespace(String namespace) { this.namespace = namespace != null ? namespace : ""; return this; }
        public Builder attribute(String key, String value) { this.attributes.put(key, value); return this; }
        public Builder appendText(String t) { if (t != null) this.text.append(t); return this; }
        public Builder addChild(XmlNode child) { this.children.add(child); return this; }
        public Builder line(int line) { this.line = line; return this; }

        public XmlNode build() {
            return new XmlNode(name, namespace, Map.copyOf(attributes), text.toString().trim(), List.copyOf(children), line);
        }
    }
}

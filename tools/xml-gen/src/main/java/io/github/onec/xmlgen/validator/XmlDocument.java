package io.github.onec.xmlgen.validator;

import lombok.Value;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Результат StAX-парсинга XML файла.
 * Содержит метаинформацию (BOM, корневой элемент, namespace) и дерево узлов.
 */
@Value
public class XmlDocument {
    /** Путь к файлу */
    Path file;
    /** Файл начинается с UTF-8 BOM (EF BB BF) */
    boolean hasBom;
    /** Оригинальный XML declaration (e.g. {@code <?xml version="1.0" encoding="UTF-8"?>}) */
    String xmlDeclaration;
    /** Имя корневого элемента */
    String rootElement;
    /** Namespace корневого элемента */
    String rootNamespace;
    /** Атрибуты корневого элемента */
    Map<String, String> rootAttributes;
    /** Дочерние элементы корня (полное дерево) */
    List<XmlNode> children;
    /** Корневой узел целиком */
    XmlNode root;

    /**
     * Найти первый дочерний элемент корня по имени.
     */
    public XmlNode child(String name) {
        return root.child(name);
    }

    /**
     * Найти все дочерние элементы корня по имени.
     */
    public List<XmlNode> children(String name) {
        return root.children(name);
    }
}

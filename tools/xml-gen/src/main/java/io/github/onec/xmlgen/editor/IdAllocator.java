package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Аллокатор уникальных числовых ID для элементов XML.
 * Сканирует поддерево(я) на наличие атрибутов "id" и находит максимальное значение.
 *
 * <p>Поддерживает несколько режимов:
 * <ul>
 *   <li>Полный скан документа через {@link #IdAllocator(XmlDocument)}</li>
 *   <li>Скан конкретного поддерева через {@link #IdAllocator(XmlNode)}</li>
 *   <li>Пустой аллокатор с последующим накоплением через {@link #scan(XmlNode)}</li>
 *   <li>Повышение floor (для extension-режима) через {@link #bumpFloor(int)}</li>
 * </ul>
 */
public class IdAllocator {
    private int maxId = 0;
    private final Set<String> usedIds = new HashSet<>();
    private static final Pattern NUMERIC = Pattern.compile("\\d+");

    public IdAllocator() {
        // Пустой аллокатор. Используйте scan() / bumpFloor() для инициализации.
    }

    public IdAllocator(XmlDocument document) {
        if (document != null && document.getRoot() != null) {
            scan(document.getRoot());
        }
    }

    public IdAllocator(XmlNode subtree) {
        scan(subtree);
    }

    /**
     * Повысить нижнюю границу выделяемых ID до указанного значения (если текущий max ниже).
     * Используется для extension-режима: {@code bumpFloor(1_000_000)} гарантирует,
     * что следующий ID будет ≥ 1000000.
     */
    public void bumpFloor(int minimum) {
        if (maxId < minimum - 1) {
            maxId = minimum - 1;
        }
    }

    /**
     * Просканировать поддерево и учесть все найденные ID.
     * Можно вызывать многократно для накопления из разных поддеревьев.
     */
    public void scan(XmlNode node) {
        if (node == null) return;

        // Ищем атрибуты id (регистронезависимо, т.к. иногда бывает id и Id)
        node.getAttributes().forEach((k, v) -> {
            if (k.equalsIgnoreCase("id")) {
                usedIds.add(v);
                if (NUMERIC.matcher(v).matches()) {
                    try {
                        int val = Integer.parseInt(v);
                        if (val > maxId) {
                            maxId = val;
                        }
                    } catch (NumberFormatException ignored) {
                        // ignore non-numeric ids
                    }
                }
            }
        });

        for (XmlNode child : node.getChildren()) {
            scan(child);
        }
    }

    /**
     * Выделить следующий свободный ID.
     */
    public String nextId() {
        maxId++;
        String id = String.valueOf(maxId);
        while (usedIds.contains(id)) {
            maxId++;
            id = String.valueOf(maxId);
        }
        usedIds.add(id);
        return id;
    }
}

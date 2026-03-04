package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Аллокатор уникальных числовых ID для элементов XML.
 * Сканирует документ на наличие атрибутов "id" и находит максимальное значение.
 */
public class IdAllocator {
    private int maxId = 0;
    private final Set<String> usedIds = new HashSet<>();
    private static final Pattern NUMERIC = Pattern.compile("\\d+");

    public IdAllocator(XmlDocument document) {
        if (document.getRoot() != null) {
            scan(document.getRoot());
        }
    }

    private void scan(XmlNode node) {
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

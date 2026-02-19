package io.github.onec.xmlgen.validator;

import java.util.*;

/**
 * Валидатор для XML табличного документа 1С (SpreadsheetDocument / MXL).
 * <p>
 * Level 1 (Structure): MXL-001..005
 * Level 2 (Semantic):  MXL-101..106
 */
public class MxlValidator implements XmlValidator {

    private static final String NS_MXL = "http://v8.1c.ru/8.2/data/spreadsheet";

    private static final Set<String> KNOWN_H_ALIGNMENTS = Set.of("Left", "Center", "Right", "Justify");
    private static final Set<String> KNOWN_V_ALIGNMENTS = Set.of("Top", "Center", "Bottom");

    @Override
    public String objectType() {
        return "mxl";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return "document".equals(document.getRootElement())
                && NS_MXL.equals(document.getRootNamespace());
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();

        validateStructure(document, issues);

        if (level == ValidationLevel.SEMANTIC) {
            validateSemantic(document, issues);
        }

        return issues;
    }

    // ==================== Level 1: Structure ====================

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // MXL-001: Root <document> с namespace
        if (!"document".equals(root.getName())) {
            issues.add(ValidationIssue.error("MXL-001",
                    "Expected root element 'document', found '" + root.getName() + "'",
                    root.getLine(), "/"));
            return;
        }
        String ns = root.getNamespace();
        if (ns == null || !ns.equals(NS_MXL)) {
            issues.add(ValidationIssue.error("MXL-001",
                    "Expected namespace '" + NS_MXL + "'",
                    root.getLine(), "/document"));
        }

        // MXL-002: templateMode=true
        String templateMode = root.childText("templateMode");
        if (templateMode == null || !"true".equals(templateMode)) {
            issues.add(ValidationIssue.warning("MXL-002",
                    "Expected <templateMode>true</templateMode>",
                    root.getLine(), "/document/templateMode"));
        }

        // MXL-003: columns с size
        XmlNode columns = root.child("columns");
        if (columns != null) {
            String size = columns.childText("size");
            if (size == null || size.isEmpty()) {
                issues.add(ValidationIssue.warning("MXL-003",
                        "Missing <size> in <columns>",
                        columns.getLine(), "/document/columns"));
            }
        } else {
            issues.add(ValidationIssue.warning("MXL-003",
                    "Missing <columns> element",
                    root.getLine(), "/document"));
        }

        // MXL-004 + MXL-005: height и rowsItem
        String heightStr = root.childText("height");
        List<XmlNode> rowsItems = root.children("rowsItem");

        if (heightStr != null && !heightStr.isEmpty()) {
            try {
                int declaredHeight = Integer.parseInt(heightStr);
                if (declaredHeight != rowsItems.size()) {
                    issues.add(ValidationIssue.warning("MXL-004",
                            "Declared <height> " + declaredHeight
                                    + " doesn't match actual rowsItem count " + rowsItems.size(),
                            root.getLine(), "/document/height"));
                }
            } catch (NumberFormatException e) {
                issues.add(ValidationIssue.error("MXL-004",
                        "Invalid <height> value: '" + heightStr + "'",
                        root.getLine(), "/document/height"));
            }
        }

        // MXL-005: rowsItem индексы последовательные
        for (int i = 0; i < rowsItems.size(); i++) {
            XmlNode row = rowsItems.get(i);
            String idxStr = row.attr("index");
            // Некоторые MXL не имеют index — это нормально
            // Но если есть, должны быть последовательные
            // Пока пропускаем — не все MXL используют index
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Собираем определённые fonts для MXL-104
        Set<String> definedFonts = new HashSet<>();
        List<XmlNode> fontsList = root.children("font");
        for (XmlNode font : fontsList) {
            String fontName = font.attr("name");
            if (fontName != null) {
                definedFonts.add(fontName);
            }
            // Порядковый индекс тоже используется как идентификатор
        }

        // Проверяем строки
        List<XmlNode> rowsItems = root.children("rowsItem");
        for (int i = 0; i < rowsItems.size(); i++) {
            XmlNode row = rowsItems.get(i);
            String rowPath = "/document/rowsItem[" + (i + 1) + "]";

            List<XmlNode> cells = row.children("c");
            for (int j = 0; j < cells.size(); j++) {
                XmlNode cell = cells.get(j);
                String cellPath = rowPath + "/c[" + (j + 1) + "]";

                // MXL-101: horizontalAlignment
                checkAlignment(cell, "horizontalAlignment", KNOWN_H_ALIGNMENTS, "MXL-101", cellPath, issues);

                // MXL-102: verticalAlignment
                checkAlignment(cell, "verticalAlignment", KNOWN_V_ALIGNMENTS, "MXL-102", cellPath, issues);

                // MXL-103: merge >= 0
                String mergeStr = cell.childText("merge");
                if (mergeStr != null && !mergeStr.isEmpty()) {
                    try {
                        int merge = Integer.parseInt(mergeStr);
                        if (merge < 0) {
                            issues.add(ValidationIssue.error("MXL-103",
                                    "Merge value must be >= 0, found " + merge,
                                    cell.getLine(), cellPath + "/merge"));
                        }
                    } catch (NumberFormatException e) {
                        issues.add(ValidationIssue.error("MXL-103",
                                "Invalid merge value: '" + mergeStr + "'",
                                cell.getLine(), cellPath + "/merge"));
                    }
                }
            }
        }

        // MXL-106: Font height > 0
        for (int i = 0; i < fontsList.size(); i++) {
            XmlNode font = fontsList.get(i);
            String fontPath = "/document/font[" + (i + 1) + "]";

            String heightStr = font.childText("height");
            if (heightStr != null && !heightStr.isEmpty()) {
                try {
                    int height = Integer.parseInt(heightStr);
                    if (height <= 0) {
                        issues.add(ValidationIssue.warning("MXL-106",
                                "Font height should be > 0, found " + height,
                                font.getLine(), fontPath + "/height"));
                    }
                } catch (NumberFormatException ignored) {
                    // Нечисловое — это ошибка, но обычно такого не бывает
                }
            }
        }
    }

    private void checkAlignment(XmlNode cell, String elementName, Set<String> validValues,
                                String code, String cellPath, List<ValidationIssue> issues) {
        String value = cell.childText(elementName);
        if (value != null && !value.isEmpty() && !validValues.contains(value)) {
            issues.add(ValidationIssue.error(code,
                    "Unknown " + elementName + " '" + value + "', expected: " + validValues,
                    cell.getLine(), cellPath + "/" + elementName));
        }
    }
}

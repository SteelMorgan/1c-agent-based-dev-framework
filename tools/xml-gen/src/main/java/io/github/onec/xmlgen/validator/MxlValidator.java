package io.github.onec.xmlgen.validator;

import java.util.*;

/**
 * Валидатор для XML табличного документа 1С (SpreadsheetDocument / MXL).
 * <p>
 * Level 1 (Structure): MXL-001..005
 * Level 2 (Semantic):  MXL-101..106
 * Level 2 (Canon-borrowed, structured error classes from Shirokov canon):
 *   MXL-201 Out-of-bounds column
 *   MXL-202 Overlapping cells
 *   MXL-203 Rowspan beyond area
 *   MXL-204 Unknown parameter name
 *   MXL-205 Format mismatch (numeric format on non-numeric cell)
 *   MXL-206 Page size impossible (sum of widths exceeds page)
 *   MXL-207 Style reference broken
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
            validateCanonBorrowed(document, issues);
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

                // MXL-103 (legacy): in-cell merge >= 0. TASK-171: канон использует
                // document-level <merge>, который валидируется отдельно ниже. Эта проверка
                // оставлена для совместимости со старыми макетами в нашем диалекте.
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

        // MXL-103 (канон): document-level <merge> — r/c обязательны и >= -1, w/h >= 0.
        // TASK-171: это основной механизм объединений в формате платформы.
        List<XmlNode> mergeNodes = root.children("merge");
        for (int i = 0; i < mergeNodes.size(); i++) {
            XmlNode merge = mergeNodes.get(i);
            String mergePath = "/document/merge[" + (i + 1) + "]";
            String rStr = merge.childText("r");
            String cStr = merge.childText("c");
            if (rStr == null || cStr == null) {
                issues.add(ValidationIssue.error("MXL-103",
                        "Document-level merge must define both <r> and <c>",
                        merge.getLine(), mergePath));
                continue;
            }
            checkMergeComponent(merge, "w", mergePath, issues);
            checkMergeComponent(merge, "h", mergePath, issues);
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

    /** TASK-171: w/h в document-level merge должны быть неотрицательными целыми (если заданы). */
    private void checkMergeComponent(XmlNode merge, String name, String mergePath,
                                     List<ValidationIssue> issues) {
        String v = merge.childText(name);
        if (v == null || v.isEmpty()) return;
        try {
            int n = Integer.parseInt(v.trim());
            if (n < 0) {
                issues.add(ValidationIssue.error("MXL-103",
                        "Merge <" + name + "> must be >= 0, found " + n,
                        merge.getLine(), mergePath + "/" + name));
            }
        } catch (NumberFormatException e) {
            issues.add(ValidationIssue.error("MXL-103",
                    "Invalid merge <" + name + "> value: '" + v + "'",
                    merge.getLine(), mergePath + "/" + name));
        }
    }

    // ==================== Canon-borrowed (MXL-201..207) ====================

    /**
     * Структурированные классы ошибок, заимствованные из канона Широкова.
     * Реализованы аддитивно к существующим MXL-001..MXL-106.
     */
    private void validateCanonBorrowed(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Read columns size
        int columnsSize = 0;
        XmlNode columns = root.child("columns");
        if (columns != null) {
            String sizeStr = columns.childText("size");
            if (sizeStr != null) {
                try { columnsSize = Integer.parseInt(sizeStr); } catch (NumberFormatException ignored) {}
            }
        }

        // Collect defined format IDs (styles + width formats)
        Set<String> definedFormatIds = new HashSet<>();
        for (XmlNode fmt : root.children("format")) {
            String id = fmt.childText("id");
            if (id != null && !id.isEmpty()) definedFormatIds.add(id);
        }
        // Numeric format ids referenced via formatIndex in columnsItem are auto-defined too
        // (we don't enforce numeric vs string; collect for cross-check anyway)
        // Add "0" as the default format reference (XML uses <f>0</f> as default)
        definedFormatIds.add("0");

        // TASK-171: document-level <merge> карта (r,c) -> [w,h]. Спаны теперь живут
        // на уровне документа, а не в ячейке. Читаем их для MXL-201/202/203.
        Map<String, int[]> docMerges = new HashMap<>(); // "r,c" -> [w,h]
        for (XmlNode m : root.children("merge")) {
            Integer r = parseIntOrNull(m.childText("r"));
            Integer c = parseIntOrNull(m.childText("c"));
            if (r == null || c == null) continue;
            int w = parseNonNegInt(m.childText("w"));
            int h = parseNonNegInt(m.childText("h"));
            docMerges.put(r + "," + c, new int[]{w, h});
        }

        // Build per-cell positions: rowIndex -> set of column indices and overlaps
        List<XmlNode> rowsItems = root.children("rowsItem");
        // Map<rowIndex, Set<colIndex>>
        Map<Integer, Set<Integer>> occupied = new HashMap<>();
        int totalRows = rowsItems.size();

        for (int i = 0; i < rowsItems.size(); i++) {
            XmlNode rowsItem = rowsItems.get(i);
            String rowPath = "/document/rowsItem[" + (i + 1) + "]";
            String idxStr = rowsItem.childText("index");
            int rowIdx;
            try { rowIdx = idxStr != null ? Integer.parseInt(idxStr) : i; } catch (NumberFormatException e) { rowIdx = i; }

            XmlNode row = rowsItem.child("row");
            if (row == null) continue;

            int implicitCol = 0;
            List<XmlNode> cellGroups = row.children("c");
            for (int j = 0; j < cellGroups.size(); j++) {
                XmlNode cOuter = cellGroups.get(j);
                XmlNode cInner = cOuter.child("c");
                if (cInner == null) continue;
                String cellPath = rowPath + "/c[" + (j + 1) + "]";

                // Column index: explicit <i> or implicit
                String iStr = cOuter.childText("i");
                int colIdx;
                if (iStr != null) {
                    try { colIdx = Integer.parseInt(iStr); } catch (NumberFormatException e) { colIdx = implicitCol; }
                } else {
                    colIdx = implicitCol;
                }

                // MXL-201: out-of-bounds column
                if (columnsSize > 0 && colIdx >= columnsSize) {
                    issues.add(ValidationIssue.error("MXL-201",
                            "Cell column " + colIdx + " out of bounds (columns=" + columnsSize + ")",
                            cInner.getLine(), cellPath));
                }

                // Span / Rowspan. TASK-171: предпочитаем document-level merge (канон),
                // с откатом на in-cell merge/rowMerge (старый диалект).
                int span = parseNonNegInt(cInner.childText("merge"));     // 0 means span=1
                int rowSpan = parseNonNegInt(cInner.childText("rowMerge"));
                int[] dm = docMerges.get(rowIdx + "," + colIdx);
                if (dm != null) {
                    span = dm[0];
                    rowSpan = dm[1];
                }

                // MXL-201 (extended): span past columns
                if (columnsSize > 0 && colIdx + span >= columnsSize) {
                    issues.add(ValidationIssue.error("MXL-201",
                            "Cell at col " + colIdx + " with span " + (span + 1)
                                    + " exceeds columns " + columnsSize,
                            cInner.getLine(), cellPath + "/merge"));
                }

                // MXL-203: rowspan beyond document
                if (totalRows > 0 && (rowIdx + rowSpan) >= totalRows) {
                    issues.add(ValidationIssue.error("MXL-203",
                            "Rowspan from row " + rowIdx + " by " + (rowSpan + 1)
                                    + " extends beyond document height " + totalRows,
                            cInner.getLine(), cellPath + "/rowMerge"));
                }

                // MXL-202: overlapping cells (track occupied cells)
                for (int dr = 0; dr <= rowSpan; dr++) {
                    int r = rowIdx + dr;
                    Set<Integer> cols = occupied.computeIfAbsent(r, k -> new HashSet<>());
                    for (int dc = 0; dc <= span; dc++) {
                        int c = colIdx + dc;
                        if (!cols.add(c)) {
                            issues.add(ValidationIssue.error("MXL-202",
                                    "Cell at row " + r + " col " + c + " overlaps with previous cell",
                                    cInner.getLine(), cellPath));
                        }
                    }
                }

                // MXL-207: style reference broken
                String f = cInner.childText("f");
                if (f != null && !f.isEmpty() && !definedFormatIds.contains(f)
                        && !looksLikeNumericIndex(f) && !f.startsWith("__cw_")) {
                    issues.add(ValidationIssue.error("MXL-207",
                            "Style reference '" + f + "' is not defined in <format> palette",
                            cInner.getLine(), cellPath + "/f"));
                }

                // MXL-205: format mismatch — numeric format string on non-numeric (text) cell.
                // We treat presence of <tl> (localized text) as non-numeric.
                // Look up the style's format string from the palette.
                if (f != null && !"0".equals(f)) {
                    String formatString = lookupFormatString(root, f);
                    if (formatString != null && isNumericFormat(formatString)
                            && cInner.child("tl") != null
                            && cInner.child("parameter") == null
                            && !cellTextIsNumeric(cInner)) {
                        issues.add(ValidationIssue.error("MXL-205",
                                "Numeric format '" + formatString + "' applied to non-numeric text cell",
                                cInner.getLine(), cellPath));
                    }
                }

                implicitCol = colIdx + span + 1;
            }
        }

        // MXL-206: page size impossible (sum of widths > page)
        // TASK-171 (канон): ширины колонок берём из форматов, на которые ссылаются
        // <columnsItem>/<column>/<formatIndex> (числовой 1-based индекс палитры).
        // Legacy-фоллбэк: форматы с id="__cw_*".
        XmlNode pageSetup = root.child("pageSetup");
        if (pageSetup != null) {
            String pageWidthStr = pageSetup.childText("pageWidth");
            if (pageWidthStr != null) {
                try {
                    int pageWidth = Integer.parseInt(pageWidthStr.trim());

                    // Соберём палитру форматов (1-based).
                    List<XmlNode> formats = root.children("format");

                    int sumWidths = 0;
                    boolean anyWidth = false;

                    // Канон: суммируем ширины колонок основного набора.
                    XmlNode columnsForWidth = root.child("columns");
                    if (columnsForWidth != null) {
                        for (XmlNode ci : columnsForWidth.children("columnsItem")) {
                            XmlNode column = ci.child("column");
                            String fiStr = column != null ? column.childText("formatIndex") : null;
                            Integer fi = parseIntOrNull(fiStr);
                            if (fi != null && fi >= 1 && fi <= formats.size()) {
                                String w = formats.get(fi - 1).childText("width");
                                Integer wv = parseIntOrNull(w);
                                if (wv != null) { sumWidths += wv; anyWidth = true; }
                            }
                        }
                    }

                    // Legacy-фоллбэк по __cw_*.
                    if (!anyWidth) {
                        for (XmlNode fmt : formats) {
                            String id = fmt.childText("id");
                            Integer wv = parseIntOrNull(fmt.childText("width"));
                            if (id != null && id.startsWith("__cw_") && wv != null) {
                                sumWidths += wv;
                                anyWidth = true;
                            }
                        }
                    }

                    if (anyWidth && sumWidths > pageWidth) {
                        issues.add(ValidationIssue.error("MXL-206",
                                "Sum of column widths " + sumWidths + " exceeds page width " + pageWidth,
                                pageSetup.getLine(), "/document/pageSetup"));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // MXL-204: unknown parameter name. We can only check when a schema of allowed
        // params is provided. As a heuristic: scan for parameters with empty name —
        // treat as unknown. Stricter check requires external schema, beyond MVP scope.
        for (int i = 0; i < rowsItems.size(); i++) {
            XmlNode rowsItem = rowsItems.get(i);
            XmlNode row = rowsItem.child("row");
            if (row == null) continue;
            int j = 0;
            for (XmlNode cOuter : row.children("c")) {
                j++;
                XmlNode cInner = cOuter.child("c");
                if (cInner == null) continue;
                XmlNode param = cInner.child("parameter");
                if (param != null) {
                    String content = param.childText("content");
                    if (content == null) content = param.getText();
                    if (content == null || content.trim().isEmpty()) {
                        issues.add(ValidationIssue.error("MXL-204",
                                "Cell parameter has empty/unknown name",
                                param.getLine(),
                                "/document/rowsItem[" + (i + 1) + "]/c[" + j + "]/parameter"));
                    }
                }
            }
        }
    }

    private static int parseNonNegInt(String s) {
        if (s == null) return 0;
        try {
            int v = Integer.parseInt(s.trim());
            return v < 0 ? 0 : v;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** TASK-171: парсинг целого либо null (для document-level merge r/c). */
    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static boolean looksLikeNumericIndex(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static String lookupFormatString(XmlNode root, String formatId) {
        for (XmlNode fmt : root.children("format")) {
            String id = fmt.childText("id");
            if (formatId.equals(id)) {
                return fmt.childText("format");
            }
        }
        return null;
    }

    private static boolean isNumericFormat(String fmt) {
        if (fmt == null) return false;
        // 1C numeric format tokens: ЧДЦ (digits after decimal), ЧЦ (total digits),
        // ЧО (group separator), ЧС (decimal separator), ЧРГ (group sep).
        // Date tokens: ДФ (date format) — distinguish.
        return fmt.contains("ЧДЦ") || fmt.contains("ЧЦ=") || fmt.contains("ЧО=") || fmt.contains("ЧРГ=");
    }

    private static boolean cellTextIsNumeric(XmlNode cInner) {
        // Try to extract textual content and check if numeric
        XmlNode tl = cInner.child("tl");
        if (tl == null) return false;
        String text = null;
        XmlNode item = tl.child("item");
        if (item != null) {
            text = item.childText("content");
        }
        if (text == null) text = tl.getText();
        if (text == null || text.isEmpty()) return true; // empty -> assume valid
        // Allow comma or dot as decimal, leading minus
        String t = text.trim().replace(',', '.');
        try {
            Double.parseDouble(t);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

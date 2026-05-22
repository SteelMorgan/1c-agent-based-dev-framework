package io.github.onec.xmlgen.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Декомпилятор MXL (Template.xml) → JSON DSL (MxlDsl-совместимый формат).
 * Обратная операция к MxlWriter.create().
 */
public class MxlDecompiler {

    /**
     * Декомпилировать MXL XML в JSON DSL.
     *
     * @param document распарсенный Template.xml
     * @param output   путь к выходному JSON (null = stdout)
     */
    public void decompile(XmlDocument document, Path output) throws IOException {
        XmlNode root = document.getRoot();
        Map<String, Object> result = new LinkedHashMap<>();

        // --- Columns ---
        for (XmlNode cols : root.children("columns")) {
            String id = cols.childText("id");
            if (id == null) {
                String size = cols.childText("size");
                if (size != null) result.put("columns", Integer.parseInt(size));
            }
        }

        // --- Default width ---
        for (XmlNode fmt : root.children("format")) {
            String id = fmt.childText("id");
            if (id == null) {
                String width = fmt.childText("width");
                if (width != null) result.put("defaultWidth", Integer.parseInt(width));
            }
        }

        // --- Fonts ---
        Map<String, Object> fonts = new LinkedHashMap<>();
        for (XmlNode fontOuter : root.children("font")) {
            String fontId = fontOuter.childText("id");
            XmlNode fontInner = fontOuter.child("font");
            if (fontId != null && fontInner != null) {
                Map<String, Object> f = new LinkedHashMap<>();
                putIfNotNull(f, "face", fontInner.childText("face"));
                putIntIfNotNull(f, "size", fontInner.childText("height"));
                putBoolIfTrue(f, "bold", fontInner.childText("bold"));
                putBoolIfTrue(f, "italic", fontInner.childText("italic"));
                putBoolIfTrue(f, "underline", fontInner.childText("underline"));
                putBoolIfTrue(f, "strikeout", fontInner.childText("strikeout"));
                if (!f.isEmpty()) fonts.put(fontId, f);
            }
        }
        if (!fonts.isEmpty()) result.put("fonts", fonts);

        // --- Styles (format elements with id) ---
        // Auto-naming: when a <format> has no id, assign Style1/Style2/... based on
        // a stable signature (so identical styles share one name).
        // Канон Широкова: уникальные стили → именованные.
        Map<String, Object> styles = new LinkedHashMap<>();
        Map<String, String> autoNameBySignature = new LinkedHashMap<>(); // signature -> StyleN
        int autoCounter = 0;
        for (XmlNode fmt : root.children("format")) {
            String id = fmt.childText("id");
            Map<String, Object> s = new LinkedHashMap<>();
            putIfNotNull(s, "font", fmt.childText("font"));
            String hAlign = fmt.childText("horizontalAlignment");
            if (hAlign != null) s.put("align", hAlign.toLowerCase());
            String vAlign = fmt.childText("verticalAlignment");
            if (vAlign != null) s.put("valign", vAlign.toLowerCase());

            // Border
            XmlNode border = fmt.child("border");
            if (border != null) {
                List<String> sides = new ArrayList<>();
                if (border.child("top") != null) sides.add("top");
                if (border.child("bottom") != null) sides.add("bottom");
                if (border.child("left") != null) sides.add("left");
                if (border.child("right") != null) sides.add("right");
                if (sides.size() == 4) {
                    s.put("border", "all");
                } else if (!sides.isEmpty()) {
                    s.put("border", String.join(",", sides));
                }
            }

            String textPlacement = fmt.childText("textPlacement");
            if ("Wrap".equals(textPlacement)) s.put("wrap", true);

            putIfNotNull(s, "format", fmt.childText("format"));

            if (s.isEmpty()) continue;

            if (id != null && !id.isEmpty()) {
                // Preserve explicit name from XML.
                styles.put(id, s);
            } else {
                // Skip width-only synthesized formats (no relevant style content).
                String width = fmt.childText("width");
                if (width != null && s.isEmpty()) continue;
                // Auto-name. Build a stable signature key by sorted entries.
                String signature = s.toString();
                String autoName = autoNameBySignature.get(signature);
                if (autoName == null) {
                    autoCounter++;
                    autoName = "Style" + autoCounter;
                    autoNameBySignature.put(signature, autoName);
                    styles.put(autoName, s);
                }
            }
        }
        if (!styles.isEmpty()) result.put("styles", styles);

        // --- Named areas (for area boundaries) ---
        List<NamedArea> namedAreas = new ArrayList<>();
        for (XmlNode ni : root.children("namedItem")) {
            String niType = ni.attr("xsi:type");
            if (niType == null) niType = "";
            if (!niType.contains("NamedItemCells")) continue;

            String name = ni.childText("name");
            XmlNode area = ni.child("area");
            if (name == null || area == null) continue;

            String areaType = area.childText("type");
            if (!"Rows".equals(areaType)) continue; // Only row-based areas map to DSL areas

            int beginRow = safeInt(area.childText("beginRow"));
            int endRow = safeInt(area.childText("endRow"));
            namedAreas.add(new NamedArea(name, beginRow, endRow));
        }
        namedAreas.sort(Comparator.comparingInt(a -> a.beginRow));

        // --- Build row index map ---
        Map<Integer, XmlNode> rowMap = new TreeMap<>();
        for (XmlNode ri : root.children("rowsItem")) {
            String idxText = ri.childText("index");
            if (idxText != null) rowMap.put(Integer.parseInt(idxText), ri);
        }

        // --- Areas ---
        List<Object> areas = new ArrayList<>();

        if (namedAreas.isEmpty()) {
            // No named areas — put all rows into single unnamed area
            if (!rowMap.isEmpty()) {
                List<Object> rows = decompileRows(rowMap, 0, Collections.max(rowMap.keySet()));
                if (!rows.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "Main");
                    area.put("rows", rows);
                    areas.add(area);
                }
            }
        } else {
            // Collect covered row indices
            Set<Integer> coveredRows = new TreeSet<>();
            for (NamedArea na : namedAreas) {
                for (int r = na.beginRow; r <= na.endRow; r++) coveredRows.add(r);
            }

            // Rows before first named area
            int firstAreaStart = namedAreas.get(0).beginRow;
            if (firstAreaStart > 0) {
                List<Object> preRows = decompileRows(rowMap, 0, firstAreaStart - 1);
                if (!preRows.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "_Before");
                    area.put("rows", preRows);
                    areas.add(area);
                }
            }

            for (NamedArea na : namedAreas) {
                List<Object> rows = decompileRows(rowMap, na.beginRow, na.endRow);
                Map<String, Object> area = new LinkedHashMap<>();
                area.put("name", na.name);
                area.put("rows", rows);
                areas.add(area);
            }

            // Rows after last named area
            int lastAreaEnd = namedAreas.get(namedAreas.size() - 1).endRow;
            int maxRow = rowMap.isEmpty() ? lastAreaEnd : Collections.max(rowMap.keySet());
            if (lastAreaEnd < maxRow) {
                List<Object> postRows = decompileRows(rowMap, lastAreaEnd + 1, maxRow);
                if (!postRows.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "_After");
                    area.put("rows", postRows);
                    areas.add(area);
                }
            }
        }
        if (!areas.isEmpty()) result.put("areas", areas);

        // --- Write JSON ---
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        if (output != null) {
            mapper.writeValue(output.toFile(), result);
            System.out.println("Decompiled MXL to: " + output);
        } else {
            mapper.writeValue(System.out, result);
            System.out.println();
        }
    }

    private List<Object> decompileRows(Map<Integer, XmlNode> rowMap, int beginRow, int endRow) {
        List<Object> rows = new ArrayList<>();
        int expectedRow = beginRow;

        for (int r = beginRow; r <= endRow; r++) {
            XmlNode rowItem = rowMap.get(r);
            if (rowItem == null) {
                continue;
            }

            // Insert empty rows gap
            if (r > expectedRow) {
                Map<String, Object> emptyRow = new LinkedHashMap<>();
                emptyRow.put("empty", r - expectedRow);
                rows.add(emptyRow);
            }

            XmlNode row = rowItem.child("row");
            if (row == null) {
                expectedRow = r + 1;
                continue;
            }

            List<Object> cells = new ArrayList<>();
            for (XmlNode cOuter : row.children("c")) {
                XmlNode cInner = cOuter.child("c");
                if (cInner == null) continue;

                Map<String, Object> cell = new LinkedHashMap<>();

                // Column index
                String colIdx = cOuter.childText("i");
                if (colIdx != null) {
                    cell.put("col", Integer.parseInt(colIdx) + 1); // 0-based → 1-based
                }

                // Style reference
                String f = cInner.childText("f");
                if (f != null && !"0".equals(f)) {
                    cell.put("style", f);
                }

                // Text content
                XmlNode tl = cInner.child("tl");
                if (tl != null) {
                    String content = extractMlContent(tl);
                    if (content != null && !content.isEmpty()) {
                        cell.put("text", content);
                    }
                }

                // Parameter
                XmlNode param = cInner.child("parameter");
                if (param != null) {
                    String pText = extractMlContent(param);
                    if (pText == null) pText = param.getText();
                    if (pText != null) cell.put("param", pText);
                }

                // Detail parameter
                XmlNode detail = cInner.child("detailParameter");
                if (detail != null && detail.getText() != null) {
                    cell.put("detail", detail.getText());
                }

                // Template text
                XmlNode templateText = cInner.child("templateText");
                if (templateText != null) {
                    String tt = extractMlContent(templateText);
                    if (tt != null) cell.put("template", tt);
                }

                // Span
                String merge = cInner.childText("merge");
                if (merge != null) cell.put("span", Integer.parseInt(merge) + 1);

                // Rowspan
                String rowMerge = cInner.childText("rowMerge");
                if (rowMerge != null) cell.put("rowspan", Integer.parseInt(rowMerge) + 1);

                if (!cell.isEmpty()) cells.add(cell);
            }

            Map<String, Object> rowObj = new LinkedHashMap<>();
            if (!cells.isEmpty()) rowObj.put("cells", cells);
            rows.add(rowObj);

            expectedRow = r + 1;
        }

        return rows;
    }

    private String extractMlContent(XmlNode node) {
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null) return content.getText();
        }
        // Fallback: v8:content direct child
        String content = node.childText("content");
        if (content != null) return content;
        return node.getText();
    }

    // --- Helpers ---

    private static void putIfNotNull(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, value);
    }

    private static void putIntIfNotNull(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isEmpty()) map.put(key, Integer.parseInt(value));
    }

    private static void putBoolIfTrue(Map<String, Object> map, String key, String value) {
        if ("true".equals(value)) map.put(key, true);
    }

    private static int safeInt(String s) {
        if (s == null) return -1;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    private static class NamedArea {
        final String name;
        final int beginRow, endRow;
        NamedArea(String name, int beginRow, int endRow) {
            this.name = name; this.beginRow = beginRow; this.endRow = endRow;
        }
    }
}

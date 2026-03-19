package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализ структуры табличного документа 1С (MXL / Template.xml).
 * Выводит: размеры, именованные области, параметры, пересечения, статистику.
 */
public class MxlInfoPrinter {

    private static final Pattern TEMPLATE_PARAM = Pattern.compile("\\[([^\\]]+)\\]");
    private static final Pattern NUMERIC_ONLY = Pattern.compile("^\\d+$");

    /**
     * @param document распарсенный Template.xml
     * @param withText включать текстовое содержимое
     * @param limit    макс строк
     * @param offset   смещение
     * @param out      поток вывода
     */
    public void print(XmlDocument document, boolean withText, int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();
        List<String> lines = new ArrayList<>();

        String templateName = resolveTemplateName(document.getFile());

        // --- Dimensions ---
        List<XmlNode> rowNodes = root.children("rowsItem");
        int totalRows = rowNodes.size();

        XmlNode heightNode = root.child("height");
        int docHeight = heightNode != null && heightNode.getText() != null
                ? Integer.parseInt(heightNode.getText()) : totalRows;

        // Column sets
        int defaultColCount = 0;
        List<ColSet> columnSets = new ArrayList<>();
        for (XmlNode cols : root.children("columns")) {
            String sizeText = cols.childText("size");
            int size = sizeText != null ? Integer.parseInt(sizeText) : 0;
            String id = cols.childText("id");
            if (id != null) {
                columnSets.add(new ColSet(id, size));
            } else {
                defaultColCount = size;
            }
        }

        lines.add("=== " + templateName + " ===");
        lines.add("  Rows: " + docHeight + ", Columns: " + defaultColCount);

        if (columnSets.isEmpty()) {
            lines.add("  Column sets: 1 (default only)");
        } else {
            lines.add("  Column sets: " + (columnSets.size() + 1) + " (default=" + defaultColCount
                    + " cols + " + columnSets.size() + " additional)");
            for (ColSet cs : columnSets) {
                lines.add("    " + cs.id.substring(0, Math.min(8, cs.id.length())) + "...: " + cs.size + " cols");
            }
        }

        // --- Named items ---
        List<NamedArea> namedAreas = new ArrayList<>();
        List<NamedDrawing> namedDrawings = new ArrayList<>();

        for (XmlNode ni : root.children("namedItem")) {
            String niType = safe(ni.attr("xsi:type"));
            String name = ni.childText("name");
            if (name == null) name = "";

            if (niType.contains("NamedItemCells")) {
                XmlNode area = ni.child("area");
                if (area != null) {
                    namedAreas.add(new NamedArea(
                            name,
                            safe(area.childText("type")),
                            safeInt(area.childText("beginRow")),
                            safeInt(area.childText("endRow")),
                            safeInt(area.childText("beginColumn")),
                            safeInt(area.childText("endColumn")),
                            area.childText("columnsID")
                    ));
                }
            } else if (niType.contains("NamedItemDrawing")) {
                String drawId = ni.childText("drawingID");
                namedDrawings.add(new NamedDrawing(name, safe(drawId)));
            }
        }

        // Sort areas
        namedAreas.sort((a, b) -> {
            if ("Columns".equals(a.areaType)) return a.beginCol - ("Columns".equals(b.areaType) ? b.beginCol : b.beginRow);
            return a.beginRow - ("Columns".equals(b.areaType) ? b.beginCol : b.beginRow);
        });

        // Build row index map
        Map<Integer, XmlNode> rowMap = new HashMap<>();
        for (XmlNode ri : rowNodes) {
            String idxText = ri.childText("index");
            if (idxText != null) rowMap.put(Integer.parseInt(idxText), ri);
        }

        // Collect data per area
        List<AreaData> areaDataList = new ArrayList<>();
        Set<Integer> coveredRows = new HashSet<>();

        for (NamedArea area : namedAreas) {
            AreaCellData data = getAreaCellData(area, rowMap, docHeight, withText);
            areaDataList.add(new AreaData(area, data));

            if (area.beginRow >= 0 && area.endRow >= 0) {
                for (int r = area.beginRow; r <= area.endRow; r++) coveredRows.add(r);
            }
        }

        // Outside params
        List<String> outsideParams = new ArrayList<>();
        for (int r : new TreeSet<>(rowMap.keySet())) {
            if (!coveredRows.contains(r)) {
                for (CellInfo ci : getCellData(rowMap.get(r), withText)) {
                    if ("Parameter".equals(ci.kind) || "TemplateParam".equals(ci.kind)) {
                        outsideParams.add("TemplateParam".equals(ci.kind) ? ci.value + " [tpl]" : ci.value);
                    }
                }
            }
        }

        // --- Named areas table ---
        lines.add("");
        lines.add("--- Named areas ---");

        for (AreaData ad : areaDataList) {
            NamedArea a = ad.area;
            String rowRange;
            if ("Rows".equals(a.areaType)) {
                rowRange = "rows " + a.beginRow + "-" + a.endRow;
            } else if ("Columns".equals(a.areaType)) {
                rowRange = "cols " + a.beginCol + "-" + a.endCol;
            } else {
                rowRange = "rows " + a.beginRow + "-" + a.endRow + ", cols " + a.beginCol + "-" + a.endCol;
            }

            String colsInfo = "";
            if (a.columnsId != null) {
                String csSize = "";
                for (ColSet cs : columnSets) {
                    if (cs.id.equals(a.columnsId)) {
                        csSize = " " + cs.size + "cols";
                        break;
                    }
                }
                colsInfo = " [colset" + csSize + "]";
            }

            String paramInfo = "(" + ad.data.params.size() + " params)";
            lines.add("  " + pad(a.name, 25) + " " + pad(a.areaType, 12) + " " + rowRange + "  " + paramInfo + colsInfo);
        }

        for (NamedDrawing nd : namedDrawings) {
            lines.add("  " + pad(nd.name, 25) + " Drawing      drawingID=" + nd.drawingId);
        }

        // --- Intersections ---
        List<AreaData> rowsAreas = new ArrayList<>();
        List<AreaData> colsAreas = new ArrayList<>();
        for (AreaData ad : areaDataList) {
            if ("Rows".equals(ad.area.areaType)) rowsAreas.add(ad);
            if ("Columns".equals(ad.area.areaType)) colsAreas.add(ad);
        }
        if (!rowsAreas.isEmpty() && !colsAreas.isEmpty()) {
            lines.add("");
            lines.add("--- Intersections (use with GetArea) ---");
            for (AreaData ra : rowsAreas) {
                for (AreaData ca : colsAreas) {
                    lines.add("  " + ra.area.name + "|" + ca.area.name);
                }
            }
        }

        // --- Parameters by area ---
        boolean hasParams = areaDataList.stream().anyMatch(ad -> !ad.data.params.isEmpty()) || !outsideParams.isEmpty();
        if (hasParams) {
            lines.add("");
            lines.add("--- Parameters by area ---");
            for (AreaData ad : areaDataList) {
                if (!ad.data.params.isEmpty()) {
                    lines.add("  " + ad.area.name + ": " + truncateList(ad.data.params, 10));
                }
            }
            if (!outsideParams.isEmpty()) {
                lines.add("  (outside areas): " + truncateList(outsideParams, 10));
            }
        }

        // --- Text content (when --with-text) ---
        if (withText) {
            lines.add("");
            lines.add("--- Text content ---");
            for (AreaData ad : areaDataList) {
                List<String> texts = getAreaTextContent(ad.area, rowMap, docHeight);
                if (!texts.isEmpty()) {
                    lines.add("  [" + ad.area.name + "]");
                    for (String t : texts) lines.add("    " + t);
                }
            }
            // Outside areas
            List<String> outsideTexts = new ArrayList<>();
            for (int r : new TreeSet<>(rowMap.keySet())) {
                if (!coveredRows.contains(r)) {
                    for (CellInfo ci : getCellData(rowMap.get(r), true)) {
                        if ("Text".equals(ci.kind) || "Template".equals(ci.kind)) {
                            outsideTexts.add(ci.value);
                        }
                    }
                }
            }
            if (!outsideTexts.isEmpty()) {
                lines.add("  [(outside areas)]");
                for (String t : outsideTexts) lines.add("    " + t);
            }
        }

        // --- Stats ---
        int mergeCount = root.children("merge").size();
        int drawingCount = root.children("drawing").size();
        lines.add("");
        lines.add("--- Stats ---");
        lines.add("  Merges: " + mergeCount);
        lines.add("  Drawings: " + drawingCount);

        // --- Pagination ---
        paginate(lines, limit, offset, out);
    }

    // --- Cell data extraction ---

    private List<CellInfo> getCellData(XmlNode rowNode, boolean includeText) {
        XmlNode row = rowNode.child("row");
        if (row == null) return Collections.emptyList();

        List<CellInfo> results = new ArrayList<>();
        for (XmlNode cGroup : row.children("c")) {
            XmlNode cell = cGroup.child("c");
            if (cell == null) continue;

            String param = cell.childText("parameter");
            if (param != null) {
                results.add(new CellInfo("Parameter", param));
            }

            XmlNode tl = cell.child("tl");
            if (tl != null) {
                String content = extractMlContent(tl);
                if (content != null && !content.isEmpty()) {
                    Matcher m = TEMPLATE_PARAM.matcher(content);
                    boolean isTemplate = m.find();
                    if (isTemplate) {
                        m.reset();
                        while (m.find()) {
                            String val = m.group(1);
                            if (!NUMERIC_ONLY.matcher(val).matches()) {
                                results.add(new CellInfo("TemplateParam", val));
                            }
                        }
                    }
                    if (includeText) {
                        results.add(new CellInfo(isTemplate ? "Template" : "Text", content));
                    }
                }
            }
        }
        return results;
    }

    private List<String> getAreaTextContent(NamedArea area, Map<Integer, XmlNode> rowMap, int docHeight) {
        List<String> texts = new ArrayList<>();
        int startRow = area.beginRow >= 0 ? area.beginRow : 0;
        int endRow = area.endRow >= 0 ? area.endRow : docHeight - 1;
        for (int r = startRow; r <= endRow; r++) {
            XmlNode rowNode = rowMap.get(r);
            if (rowNode == null) continue;
            for (CellInfo ci : getCellData(rowNode, true)) {
                if ("Text".equals(ci.kind) || "Template".equals(ci.kind)) {
                    texts.add(ci.value);
                }
            }
        }
        return texts;
    }

    private AreaCellData getAreaCellData(NamedArea area, Map<Integer, XmlNode> rowMap, int docHeight, boolean includeText) {
        List<String> params = new ArrayList<>();
        int startRow = area.beginRow >= 0 ? area.beginRow : 0;
        int endRow = area.endRow >= 0 ? area.endRow : docHeight - 1;

        for (int r = startRow; r <= endRow; r++) {
            XmlNode rowNode = rowMap.get(r);
            if (rowNode == null) continue;
            for (CellInfo ci : getCellData(rowNode, includeText)) {
                if ("Parameter".equals(ci.kind) || "TemplateParam".equals(ci.kind)) {
                    params.add("TemplateParam".equals(ci.kind) ? ci.value + " [tpl]" : ci.value);
                }
            }
        }
        return new AreaCellData(params);
    }

    private String extractMlContent(XmlNode tl) {
        XmlNode item = tl.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null) return content.getText();
        }
        return tl.getText();
    }

    // --- Helpers ---

    private String resolveTemplateName(Path file) {
        if (file == null) return "unknown";
        // Template.xml is in .../TemplateName/Ext/Template.xml
        Path parent = file.getParent(); // Ext
        if (parent != null) {
            Path tplDir = parent.getParent(); // TemplateName
            if (tplDir != null) return tplDir.getFileName().toString();
        }
        return file.getFileName().toString();
    }

    private static String truncateList(List<String> items, int max) {
        if (items.size() <= max) return String.join(", ", items);
        String shown = String.join(", ", items.subList(0, max));
        return shown + ", ... (+" + (items.size() - max) + ")";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String safe(String s) { return s != null ? s : ""; }

    private static int safeInt(String s) {
        if (s == null) return -1;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return -1; }
    }

    private void paginate(List<String> lines, int limit, int offset, PrintStream out) {
        int totalLines = lines.size();
        if (offset > 0) {
            if (offset >= totalLines) {
                out.println("[INFO] Offset " + offset + " exceeds total lines (" + totalLines + "). Nothing to show.");
                return;
            }
            lines = lines.subList(offset, totalLines);
        }
        int effectiveLimit = limit > 0 ? limit : lines.size();
        if (lines.size() > effectiveLimit) {
            for (int i = 0; i < effectiveLimit; i++) out.println(lines.get(i));
            out.println();
            out.println("[TRUNCATED] Shown " + effectiveLimit + " of " + totalLines
                    + " lines. Use --offset " + (offset + effectiveLimit) + " to continue.");
        } else {
            for (String line : lines) out.println(line);
        }
    }

    // --- Inner data classes ---

    private static class ColSet {
        final String id;
        final int size;
        ColSet(String id, int size) { this.id = id; this.size = size; }
    }

    private static class NamedArea {
        final String name, areaType, columnsId;
        final int beginRow, endRow, beginCol, endCol;
        NamedArea(String name, String areaType, int beginRow, int endRow, int beginCol, int endCol, String columnsId) {
            this.name = name; this.areaType = areaType;
            this.beginRow = beginRow; this.endRow = endRow;
            this.beginCol = beginCol; this.endCol = endCol;
            this.columnsId = columnsId;
        }
    }

    private static class NamedDrawing {
        final String name, drawingId;
        NamedDrawing(String name, String drawingId) { this.name = name; this.drawingId = drawingId; }
    }

    private static class CellInfo {
        final String kind, value;
        CellInfo(String kind, String value) { this.kind = kind; this.value = value; }
    }

    private static class AreaCellData {
        final List<String> params;
        AreaCellData(List<String> params) { this.params = params; }
    }

    private static class AreaData {
        final NamedArea area;
        final AreaCellData data;
        AreaData(NamedArea area, AreaCellData data) { this.area = area; this.data = data; }
    }
}

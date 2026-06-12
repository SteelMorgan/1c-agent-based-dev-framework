package io.github.onec.xmlgen.info;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Base64;

/**
 * Декомпилятор MXL (Template.xml) → JSON DSL (MxlDsl-совместимый формат).
 * Обратная операция к MxlWriter.create().
 *
 * TASK-171: переписан на КАНОНИЧЕСКУЮ модель платформы 1С (по референсу
 * Широкова mxl-decompile.py и 1c-spreadsheet-spec.md). До этого декомпилятор
 * читал собственный не-канонический диалект (in-cell &lt;merge&gt;, inline
 * &lt;border&gt;, именованные &lt;id&gt; в палитрах) и терял 70-95% форматирования
 * реальных макетов: document-level &lt;merge&gt;, скалярные индексы бордюров
 * (&lt;leftBorder&gt;idx), числовую палитру форматов/шрифтов, ширины колонок
 * из &lt;columnsItem&gt;. Теперь читаем именно канон.
 */
public class MxlDecompiler {

    // TASK-171 — внутренние модели палитр канона.
    private static final class RawFont {
        String face = "";
        int size = 0;
        boolean bold, italic, underline, strikeout;
    }

    private static final class RawLine {
        int width = 1;
    }

    private static final class RawFormat {
        int fontIdx = -1;
        int lb = -1, tb = -1, rb = -1, bb = -1;
        int width = 0, height = 0;
        String ha = "", va = "";
        boolean wrap = false;
        String fillType = "";
        String dataFormat = "";
        // TASK-171 (R5): цвета (литерал "#RRGGBB" или "style:Имя"). Пустая строка = не задан.
        String textColor = "";
        String backColor = "";
        String borderColor = "";
    }

    /**
     * Декомпилировать MXL XML в JSON DSL.
     *
     * @param document распарсенный Template.xml
     * @param output   путь к выходному JSON (null = stdout)
     */
    public void decompile(XmlDocument document, Path output) throws IOException {
        XmlNode root = document.getRoot();
        Map<String, Object> result = new LinkedHashMap<>();

        // --- 2. Палитра шрифтов (числовые индексы 0-based) ---
        // TASK-171: канон — <font faceName=.. height=.. bold=..>, индекс = позиция.
        // Старый диалект (<font><id>name</id><font><face>..) тоже поддерживаем как fallback.
        List<RawFont> rawFonts = readFonts(root);

        // --- 3. Палитра линий (0-based) ---
        List<RawLine> rawLines = readLines(root);

        // --- 4. Палитра форматов (1-based: индекс 0 = не задан) ---
        List<RawFormat> rawFormats = readFormats(root);

        // --- 5. Колонки + ширина по умолчанию ---
        XmlNode columnsMain = firstColumnsWithoutId(root);
        int totalColumns = 0;
        Map<Integer, Integer> colFormatIndices = new LinkedHashMap<>(); // 0-based col -> formatIndex
        if (columnsMain != null) {
            String size = columnsMain.childText("size");
            if (size != null) {
                try { totalColumns = Integer.parseInt(size.trim()); } catch (NumberFormatException ignored) {}
            }
            for (XmlNode ci : columnsMain.children("columnsItem")) {
                String idxStr = ci.childText("index");
                XmlNode col = ci.child("column");
                String fiStr = col != null ? col.childText("formatIndex") : null;
                Integer idx = parseIntOrNull(idxStr);
                Integer fi = parseIntOrNull(fiStr);
                if (idx != null && fi != null) colFormatIndices.put(idx, fi);
            }
        }
        if (totalColumns > 0) result.put("columns", totalColumns);

        // defaultFormatIndex → defaultWidth
        int defaultFmtIdx = parseInt(root.childText("defaultFormatIndex"), 0);
        int defaultWidth = 10;
        if (defaultFmtIdx > 0) {
            RawFormat df = getFormat(rawFormats, defaultFmtIdx);
            if (df != null && df.width > 0) defaultWidth = df.width;
        }
        // Старый диалект: <format><width>N</width></format> без id как default.
        if (defaultFmtIdx <= 0) {
            Integer legacyDefault = legacyDefaultWidth(root);
            if (legacyDefault != null) defaultWidth = legacyDefault;
        }
        result.put("defaultWidth", defaultWidth);

        // Ширины колонок (1-based col -> width), только не-дефолтные.
        Map<String, Object> columnWidths = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : new TreeMap<>(colFormatIndices).entrySet()) {
            RawFormat fmt = getFormat(rawFormats, e.getValue());
            if (fmt != null && fmt.width > 0 && fmt.width != defaultWidth) {
                columnWidths.put(String.valueOf(e.getKey() + 1), fmt.width);
            }
        }

        // --- 6. Объединения (document-level) ---
        // TASK-171: ключ "r,c" -> {W,H}. Это исправляет 100% потерю merge реальных макетов.
        // r=-1 — объединение колонок всего документа: выносим в отдельный список columnMerges
        // (в area-модели cell.span не может его выразить — нет привязки к строке).
        Map<String, int[]> mergeMap = new HashMap<>(); // "r,c" -> [w,h]
        List<Map<String, Object>> columnMergesOut = new ArrayList<>();
        for (XmlNode m : root.children("merge")) {
            int r = parseInt(m.childText("r"), Integer.MIN_VALUE);
            int c = parseInt(m.childText("c"), Integer.MIN_VALUE);
            int w = parseInt(m.childText("w"), 0);
            int h = parseInt(m.childText("h"), 0);
            if (r == Integer.MIN_VALUE || c == Integer.MIN_VALUE) continue;
            if (r == -1) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("c", c);
                cm.put("w", w);
                if (h > 0) cm.put("h", h);
                columnMergesOut.add(cm);
            } else {
                mergeMap.put(r + "," + c, new int[]{w, h});
            }
        }

        // TASK-171: verticalUnmerge (после merge в каноне). Round-trip сохранение.
        List<Map<String, Object>> verticalUnmergesOut = new ArrayList<>();
        for (XmlNode u : root.children("verticalUnmerge")) {
            int r = parseInt(u.childText("r"), Integer.MIN_VALUE);
            int c = parseInt(u.childText("c"), Integer.MIN_VALUE);
            int w = parseInt(u.childText("w"), 0);
            if (r == Integer.MIN_VALUE || c == Integer.MIN_VALUE) continue;
            Map<String, Object> uo = new LinkedHashMap<>();
            uo.put("r", r);
            uo.put("c", c);
            if (w > 0) uo.put("w", w);
            verticalUnmergesOut.add(uo);
        }

        // TASK-171 (R9): рисунки (document-level <drawing>). Round-trip сохранение всех полей.
        List<Map<String, Object>> drawingsOut = readDrawings(root);
        // Имена рисунков из <namedItem xsi:type="NamedItemDrawing"> -> привязка к drawing.id.
        Map<Integer, String> drawingNames = readDrawingNames(root);
        for (Map<String, Object> d : drawingsOut) {
            Object id = d.get("id");
            if (id instanceof Integer && drawingNames.containsKey(id)) {
                // name кладём после id для читаемости — пересборка с сохранением порядка.
                Map<String, Object> reordered = new LinkedHashMap<>();
                for (Map.Entry<String, Object> en : d.entrySet()) {
                    reordered.put(en.getKey(), en.getValue());
                    if ("id".equals(en.getKey())) reordered.put("name", drawingNames.get(id));
                }
                d.clear();
                d.putAll(reordered);
            }
        }

        // TASK-171 (R9): палитра картинок (<picture>). Round-trip сохранение data/ref.
        List<Map<String, Object>> picturesOut = readPictures(root);

        // --- 7. Именованные области (Rows) ---
        List<NamedArea> namedAreas = new ArrayList<>();
        for (XmlNode ni : root.children("namedItem")) {
            String niType = ni.attr("xsi:type");
            if (niType == null) niType = "";
            if (!niType.contains("NamedItemCells")) continue;

            String name = ni.childText("name");
            XmlNode area = ni.child("area");
            if (name == null || area == null) continue;

            String areaType = area.childText("type");
            if (!"Rows".equals(areaType)) continue; // только построчные → DSL areas

            int beginRow = parseInt(area.childText("beginRow"), -1);
            int endRow = parseInt(area.childText("endRow"), -1);
            if (beginRow >= 0 && endRow >= 0) namedAreas.add(new NamedArea(name, beginRow, endRow));
        }
        namedAreas.sort(Comparator.comparingInt(a -> a.beginRow));

        // --- 8. Строки (raw) ---
        Map<Integer, RowData> rowData = new TreeMap<>();
        for (XmlNode ri : root.children("rowsItem")) {
            Integer idx = parseIntOrNull(ri.childText("index"));
            if (idx == null) continue;
            XmlNode row = ri.child("row");

            int indexTo = idx;
            String itStr = ri.childText("indexTo");
            Integer it = parseIntOrNull(itStr);
            if (it != null) indexTo = it;

            RowData rd = new RowData();
            if (row != null) {
                rd.formatIdx = parseInt(row.childText("formatIndex"), 0);
                String empty = row.childText("empty");
                rd.empty = "true".equals(empty);
                if (!rd.empty) {
                    int col = -1;
                    for (XmlNode cGroup : row.children("c")) {
                        String iStr = cGroup.childText("i");
                        Integer i = parseIntOrNull(iStr);
                        if (i != null) col = i; else col++;

                        XmlNode cInner = cGroup.child("c");
                        if (cInner == null) continue;

                        CellData cd = new CellData();
                        cd.col = col;
                        cd.formatIdx = parseInt(cInner.childText("f"), 0);
                        cd.param = nonEmpty(extractParam(cInner));
                        XmlNode detail = cInner.child("detailParameter");
                        if (detail != null) cd.detail = nonEmpty(detail.getText());
                        cd.text = nonEmpty(extractText(cInner));
                        // Старый диалект: in-cell <merge>/<rowMerge> (если встретится).
                        Integer inMerge = parseIntOrNull(cInner.childText("merge"));
                        Integer inRowMerge = parseIntOrNull(cInner.childText("rowMerge"));
                        if (inMerge != null) cd.legacySpan = inMerge + 1;
                        if (inRowMerge != null) cd.legacyRowspan = inRowMerge + 1;
                        rd.cells.add(cd);
                    }
                }
            }
            for (int r = idx; r <= indexTo; r++) {
                rowData.put(r, rd);
            }
        }
        int inferredColumns = Math.max(maxColumnsSize(root), maxUsedColumn(rowData, mergeMap) + 1);
        if (inferredColumns > totalColumns) {
            totalColumns = inferredColumns;
            result.put("columns", totalColumns);
        }

        // --- 9..11. Именование шрифтов и стилей ---
        Map<Integer, String> fontNames = nameFonts(rawFonts);
        StyleNamer namer = new StyleNamer(rawFonts, rawLines, rawFormats, fontNames, rowData);
        namer.collect();

        // --- 12. Сборка областей ---
        List<Object> areas = new ArrayList<>();
        if (namedAreas.isEmpty()) {
            if (!rowData.isEmpty()) {
                int max = Collections.max(rowData.keySet());
                List<Object> rows = buildRows(rowData, 0, max, mergeMap, namer);
                if (!rows.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "Main");
                    area.put("rows", rows);
                    areas.add(area);
                }
            }
        } else {
            int firstStart = namedAreas.get(0).beginRow;
            if (firstStart > 0) {
                List<Object> pre = buildRows(rowData, 0, firstStart - 1, mergeMap, namer);
                if (!pre.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "_Before");
                    area.put("rows", pre);
                    areas.add(area);
                }
            }
            for (NamedArea na : namedAreas) {
                List<Object> rows = buildRows(rowData, na.beginRow, na.endRow, mergeMap, namer);
                Map<String, Object> area = new LinkedHashMap<>();
                area.put("name", na.name);
                area.put("rows", rows);
                areas.add(area);
            }
            int lastEnd = namedAreas.get(namedAreas.size() - 1).endRow;
            int maxRow = rowData.isEmpty() ? lastEnd : Collections.max(rowData.keySet());
            if (lastEnd < maxRow) {
                List<Object> post = buildRows(rowData, lastEnd + 1, maxRow, mergeMap, namer);
                if (!post.isEmpty()) {
                    Map<String, Object> area = new LinkedHashMap<>();
                    area.put("name", "_After");
                    area.put("rows", post);
                    areas.add(area);
                }
            }
        }

        // --- Финальная сборка результата (порядок ключей как у канона) ---
        if (!columnWidths.isEmpty()) result.put("columnWidths", columnWidths);

        Map<String, Object> fontsOut = namer.fontsOut();
        if (!fontsOut.isEmpty()) result.put("fonts", fontsOut);

        Map<String, Object> stylesOut = namer.stylesOut();
        if (!stylesOut.isEmpty()) result.put("styles", stylesOut);

        if (!areas.isEmpty()) result.put("areas", areas);

        // TASK-171: новые секции канона (после areas — read для round-trip).
        if (!drawingsOut.isEmpty()) result.put("drawings", drawingsOut);
        if (!picturesOut.isEmpty()) result.put("pictures", picturesOut);
        if (!verticalUnmergesOut.isEmpty()) result.put("verticalUnmerges", verticalUnmergesOut);
        if (!columnMergesOut.isEmpty()) result.put("columnMerges", columnMergesOut);
        if (document.getFile() != null) {
            byte[] raw = Files.readAllBytes(document.getFile());
            result.put("losslessXmlBase64", Base64.getEncoder().encodeToString(raw));
        }

        // --- JSON ---
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

    // ==================== Чтение палитр ====================

    private List<RawFont> readFonts(XmlNode root) {
        List<RawFont> fonts = new ArrayList<>();
        for (XmlNode f : root.children("font")) {
            RawFont rf = new RawFont();
            // Канон: атрибуты faceName/height/bold/...
            String face = f.attr("faceName");
            String height = f.attr("height");
            if (face != null || height != null) {
                rf.face = face != null ? face : "";
                rf.size = parseInt(height, 0);
                rf.bold = "true".equals(f.attr("bold"));
                rf.italic = "true".equals(f.attr("italic"));
                rf.underline = "true".equals(f.attr("underline"));
                rf.strikeout = "true".equals(f.attr("strikeout"));
                fonts.add(rf);
                continue;
            }
            // Старый диалект: <font><id>name</id><font><face>..</font></font>.
            XmlNode inner = f.child("font");
            if (inner != null) {
                rf.face = orEmpty(inner.childText("face"));
                rf.size = parseInt(inner.childText("height"), 0);
                rf.bold = "true".equals(inner.childText("bold"));
                rf.italic = "true".equals(inner.childText("italic"));
                rf.underline = "true".equals(inner.childText("underline"));
                rf.strikeout = "true".equals(inner.childText("strikeout"));
                fonts.add(rf);
            }
        }
        return fonts;
    }

    private List<RawLine> readLines(XmlNode root) {
        List<RawLine> lines = new ArrayList<>();
        for (XmlNode l : root.children("line")) {
            RawLine rl = new RawLine();
            rl.width = parseInt(l.attr("width"), 1);
            lines.add(rl);
        }
        return lines;
    }

    private List<RawFormat> readFormats(XmlNode root) {
        List<RawFormat> formats = new ArrayList<>();
        for (XmlNode fmt : root.children("format")) {
            RawFormat rf = new RawFormat();
            rf.fontIdx = parseInt(fmt.childText("font"), -1);
            rf.lb = parseInt(fmt.childText("leftBorder"), -1);
            rf.tb = parseInt(fmt.childText("topBorder"), -1);
            rf.rb = parseInt(fmt.childText("rightBorder"), -1);
            rf.bb = parseInt(fmt.childText("bottomBorder"), -1);
            rf.width = parseInt(fmt.childText("width"), 0);
            rf.height = parseInt(fmt.childText("height"), 0);
            rf.ha = orEmpty(fmt.childText("horizontalAlignment"));
            rf.va = orEmpty(fmt.childText("verticalAlignment"));
            rf.wrap = "Wrap".equals(fmt.childText("textPlacement"));
            rf.fillType = orEmpty(fmt.childText("fillType"));
            // TASK-171 (R5): цвета. Сохраняем литерал как есть (hex / style-ref).
            rf.textColor = orEmpty(fmt.childText("textColor"));
            rf.backColor = orEmpty(fmt.childText("backColor"));
            rf.borderColor = orEmpty(fmt.childText("borderColor"));
            // Строка формата: <format><v8:item><v8:content>..
            XmlNode nestedFormat = fmt.child("format");
            if (nestedFormat != null) {
                String content = extractMlContent(nestedFormat);
                if (content != null) rf.dataFormat = content;
            }
            formats.add(rf);
        }
        return formats;
    }

    /**
     * TASK-171 (R9): прочитать рисунки (&lt;drawing&gt;) в список map (только заданные поля,
     * порядок ключей как в каноне). Числовые поля парсятся в Integer, autoSize в Boolean.
     */
    private List<Map<String, Object>> readDrawings(XmlNode root) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (XmlNode d : root.children("drawing")) {
            Map<String, Object> dm = new LinkedHashMap<>();
            putStr(dm, "drawingType", d.childText("drawingType"));
            putInt(dm, "id", d.childText("id"));
            putInt(dm, "formatIndex", d.childText("formatIndex"));
            putInt(dm, "beginRow", d.childText("beginRow"));
            putInt(dm, "beginRowOffset", d.childText("beginRowOffset"));
            putInt(dm, "endRow", d.childText("endRow"));
            putInt(dm, "endRowOffset", d.childText("endRowOffset"));
            putInt(dm, "beginColumn", d.childText("beginColumn"));
            putInt(dm, "beginColumnOffset", d.childText("beginColumnOffset"));
            putInt(dm, "endColumn", d.childText("endColumn"));
            putInt(dm, "endColumnOffset", d.childText("endColumnOffset"));
            putBool(dm, "autoSize", d.childText("autoSize"));
            putStr(dm, "pictureSize", d.childText("pictureSize"));
            putInt(dm, "zOrder", d.childText("zOrder"));
            putInt(dm, "pictureIndex", d.childText("pictureIndex"));
            if (!dm.isEmpty()) out.add(dm);
        }
        return out;
    }

    /** TASK-171: имена рисунков из &lt;namedItem xsi:type="NamedItemDrawing"&gt; → drawingID. */
    private Map<Integer, String> readDrawingNames(XmlNode root) {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (XmlNode ni : root.children("namedItem")) {
            String t = ni.attr("xsi:type");
            if (t == null || !t.contains("NamedItemDrawing")) continue;
            String name = ni.childText("name");
            Integer did = parseIntOrNull(ni.childText("drawingID"));
            if (name != null && did != null) names.put(did, name);
        }
        return names;
    }

    /**
     * TASK-171 (R9): прочитать палитру картинок (&lt;picture&gt;).
     * Каждый ресурс: index + вложенный &lt;picture&gt; (base64 data в теле ИЛИ ref-атрибут).
     */
    private List<Map<String, Object>> readPictures(XmlNode root) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (XmlNode p : root.children("picture")) {
            Map<String, Object> pm = new LinkedHashMap<>();
            putInt(pm, "index", p.childText("index"));
            XmlNode inner = p.child("picture");
            if (inner != null) {
                String ref = inner.attr("ref");
                String data = inner.getText();
                String t = inner.attr("t");
                if (ref != null && !ref.isEmpty()) {
                    pm.put("ref", ref);
                } else if (data != null && !data.isEmpty()) {
                    pm.put("data", data);
                    if (t != null && !t.isEmpty()) pm.put("t", t);
                }
            }
            // Пустой <picture/> (placeholder) сохраняем — только index.
            out.add(pm);
        }
        return out;
    }

    private static void putStr(Map<String, Object> m, String k, String v) {
        if (v != null && !v.isEmpty()) m.put(k, v);
    }

    private static void putInt(Map<String, Object> m, String k, String v) {
        Integer i = parseIntOrNull(v);
        if (i != null) m.put(k, i);
    }

    private static void putBool(Map<String, Object> m, String k, String v) {
        if (v == null) return;
        if ("true".equals(v.trim())) m.put(k, Boolean.TRUE);
        else if ("false".equals(v.trim())) m.put(k, Boolean.FALSE);
    }

    /** Формат по 1-based индексу (0 = не задан). */
    private RawFormat getFormat(List<RawFormat> formats, int idx) {
        if (idx <= 0 || idx > formats.size()) return null;
        return formats.get(idx - 1);
    }

    /** Первый блок &lt;columns&gt; без &lt;id&gt; — основной набор. */
    private XmlNode firstColumnsWithoutId(XmlNode root) {
        for (XmlNode cols : root.children("columns")) {
            if (cols.childText("id") == null) return cols;
        }
        return root.child("columns");
    }

    /** Старый диалект: <format> без id, только width — трактуем как default width. */
    private Integer legacyDefaultWidth(XmlNode root) {
        // Берём последний <format> без id с одним только <width> (как писал старый writer).
        Integer result = null;
        for (XmlNode fmt : root.children("format")) {
            if (fmt.childText("id") == null
                    && fmt.childText("width") != null
                    && fmt.childText("font") == null
                    && fmt.childText("leftBorder") == null
                    && fmt.childText("horizontalAlignment") == null) {
                result = parseIntOrNull(fmt.childText("width"));
            }
        }
        return result;
    }

    // ==================== Именование шрифтов ====================

    private Map<Integer, String> nameFonts(List<RawFont> rawFonts) {
        Map<Integer, String> names = new LinkedHashMap<>();
        if (rawFonts.isEmpty()) return names;
        names.put(0, "default");
        Map<String, String> byKey = new HashMap<>();
        byKey.put(fontKey(rawFonts.get(0)), "default");
        RawFont df = rawFonts.get(0);
        Set<String> used = new HashSet<>();
        used.add("default");

        for (int i = 1; i < rawFonts.size(); i++) {
            RawFont f = rawFonts.get(i);
            String key = fontKey(f);
            if (byKey.containsKey(key)) {
                names.put(i, byKey.get(key));
                continue;
            }
            String name = null;
            if (f.face.equals(df.face) && f.size == df.size) {
                if (f.bold && !df.bold && !f.italic && !f.underline && !f.strikeout) name = "bold";
                else if (f.italic && !df.italic && !f.bold) name = "italic";
                else if (f.underline && !df.underline && !f.bold && !f.italic) name = "underline";
            } else if (f.face.equals(df.face) && f.size > df.size && f.bold) {
                name = "header";
            } else if (f.face.equals(df.face) && f.size < df.size) {
                name = "small";
            }
            if (name == null) {
                StringBuilder sb = new StringBuilder();
                if (!f.face.isEmpty() && !f.face.equals(df.face)) sb.append(f.face.toLowerCase()).append('-');
                sb.append(f.size);
                if (f.bold) sb.append("-bold");
                if (f.italic) sb.append("-italic");
                if (f.underline) sb.append("-underline");
                if (f.strikeout) sb.append("-strikeout");
                name = sb.toString();
            }
            String base = name;
            int suffix = 2;
            while (used.contains(name)) {
                name = base + suffix;
                suffix++;
            }
            names.put(i, name);
            used.add(name);
            byKey.put(key, name);
        }
        return names;
    }

    private static String fontKey(RawFont f) {
        return f.face + "|" + f.size + "|" + f.bold + "|" + f.italic + "|" + f.underline + "|" + f.strikeout;
    }

    // ==================== Стили (border-desc + naming) ====================

    private final class StyleNamer {
        final List<RawFont> rawFonts;
        final List<RawLine> rawLines;
        final List<RawFormat> rawFormats;
        final Map<Integer, String> fontNames;
        final Map<Integer, RowData> rowData;

        // styleKey -> styleName ; styleName -> defMap ; formatIdx -> styleKey
        final Map<String, String> styleNames = new LinkedHashMap<>();
        final Map<String, Map<String, Object>> styleDefs = new LinkedHashMap<>();
        final Map<Integer, String> formatToKey = new LinkedHashMap<>();
        final Set<String> usedStyleNames = new HashSet<>();

        StyleNamer(List<RawFont> rawFonts, List<RawLine> rawLines, List<RawFormat> rawFormats,
                   Map<Integer, String> fontNames, Map<Integer, RowData> rowData) {
            this.rawFonts = rawFonts;
            this.rawLines = rawLines;
            this.rawFormats = rawFormats;
            this.fontNames = fontNames;
            this.rowData = rowData;
        }

        void collect() {
            for (RowData rd : rowData.values()) {
                for (CellData cell : rd.cells) {
                    RawFormat fmt = getFormat(rawFormats, cell.formatIdx);
                    if (fmt == null) continue;
                    String key = styleKey(fmt);
                    if (!styleNames.containsKey(key)) {
                        String name = nameStyle(fmt);
                        String base = name;
                        int suffix = 2;
                        while (usedStyleNames.contains(name)) {
                            name = base + suffix;
                            suffix++;
                        }
                        styleNames.put(key, name);
                        usedStyleNames.add(name);
                        styleDefs.put(name, buildStyleDef(fmt));
                    }
                    formatToKey.put(cell.formatIdx, key);
                }
            }
        }

        String keyOf(int formatIdx) {
            return formatToKey.get(formatIdx);
        }

        String nameForKey(String key) {
            return styleNames.get(key);
        }

        BorderDesc borderDesc(RawFormat fmt) {
            if (fmt == null) return new BorderDesc("none", false);
            boolean lb = fmt.lb >= 0, tb = fmt.tb >= 0, rb = fmt.rb >= 0, bb = fmt.bb >= 0;
            if (!lb && !tb && !rb && !bb) return new BorderDesc("none", false);
            boolean thick = false;
            for (int idx : new int[]{fmt.lb, fmt.tb, fmt.rb, fmt.bb}) {
                if (idx >= 0 && idx < rawLines.size() && rawLines.get(idx).width >= 2) { thick = true; break; }
            }
            if (lb && tb && rb && bb) return new BorderDesc("all", thick);
            List<String> sides = new ArrayList<>();
            if (tb) sides.add("top");
            if (bb) sides.add("bottom");
            if (lb) sides.add("left");
            if (rb) sides.add("right");
            return new BorderDesc(String.join(",", sides), thick);
        }

        String styleKey(RawFormat fmt) {
            if (fmt == null) return "empty";
            int fi = fmt.fontIdx >= 0 ? fmt.fontIdx : 0;
            BorderDesc bd = borderDesc(fmt);
            // TASK-171 (R5): цвета — часть сигнатуры стиля, иначе форматы с разными
            // цветами схлопывались бы в один стиль и цвет терялся бы при round-trip.
            return "f=" + fi + "|b=" + bd.border + "|bw=" + bd.thick
                    + "|ha=" + fmt.ha + "|va=" + fmt.va + "|wr=" + fmt.wrap + "|df=" + fmt.dataFormat
                    + "|tc=" + fmt.textColor + "|bc=" + fmt.backColor + "|brc=" + fmt.borderColor;
        }

        String nameStyle(RawFormat fmt) {
            if (fmt == null) return "default";
            List<String> parts = new ArrayList<>();
            int fi = fmt.fontIdx >= 0 ? fmt.fontIdx : 0;
            String fn = fontNames.get(fi);
            if (fn != null && !"default".equals(fn)) parts.add(fn);
            BorderDesc bd = borderDesc(fmt);
            if (!"none".equals(bd.border)) {
                if ("all".equals(bd.border)) parts.add("bordered");
                else parts.add("border-" + bd.border);
            }
            if ("Center".equals(fmt.ha)) parts.add("center");
            else if ("Right".equals(fmt.ha)) parts.add("right");
            if ("Center".equals(fmt.va)) parts.add("vcenter");
            else if ("Top".equals(fmt.va)) parts.add("vtop");
            if (fmt.wrap) parts.add("wrap");
            if (!fmt.dataFormat.isEmpty()) parts.add("fmt");
            // TASK-171 (R5): признак цвета в имени стиля (без значения — оно в def).
            if (!fmt.textColor.isEmpty()) parts.add("tcolor");
            if (!fmt.backColor.isEmpty()) parts.add("bgcolor");
            if (!fmt.borderColor.isEmpty() && parts.isEmpty()) parts.add("bcolor");
            if (parts.isEmpty()) return "default";
            return String.join("-", parts);
        }

        Map<String, Object> buildStyleDef(RawFormat fmt) {
            Map<String, Object> def = new LinkedHashMap<>();
            int fi = fmt.fontIdx >= 0 ? fmt.fontIdx : 0;
            String fn = fontNames.get(fi);
            if (fn != null && !"default".equals(fn)) def.put("font", fn);
            if (!fmt.ha.isEmpty()) {
                String a = fmt.ha.equals("Left") ? "left" : fmt.ha.equals("Center") ? "center"
                        : fmt.ha.equals("Right") ? "right" : null;
                if (a != null) def.put("align", a);
            }
            if (!fmt.va.isEmpty()) {
                String a = fmt.va.equals("Top") ? "top" : fmt.va.equals("Center") ? "center" : null;
                if (a != null) def.put("valign", a);
            }
            BorderDesc bd = borderDesc(fmt);
            if (!"none".equals(bd.border)) {
                def.put("border", bd.border);
                if (bd.thick) def.put("borderWidth", "thick");
            }
            if (fmt.wrap) def.put("wrap", true);
            if (!fmt.dataFormat.isEmpty()) def.put("format", fmt.dataFormat);
            // TASK-171 (R5): цвета в def стиля — round-trip-сохранение литерала.
            if (!fmt.textColor.isEmpty()) def.put("textColor", fmt.textColor);
            if (!fmt.backColor.isEmpty()) def.put("backColor", fmt.backColor);
            if (!fmt.borderColor.isEmpty()) def.put("borderColor", fmt.borderColor);
            return def;
        }

        Map<String, Object> fontsOut() {
            Map<String, Object> out = new LinkedHashMap<>();
            // Только реально используемые в стилях шрифты (плюс default).
            Set<String> usedFonts = new LinkedHashSet<>();
            for (Map<String, Object> def : styleDefs.values()) {
                Object f = def.get("font");
                if (f instanceof String) usedFonts.add((String) f);
            }
            for (int i = 0; i < rawFonts.size(); i++) {
                String name = fontNames.get(i);
                if (name == null) continue;
                boolean isDefault = "default".equals(name);
                if (!isDefault && !usedFonts.contains(name)) continue;
                if (out.containsKey(name)) continue;
                RawFont f = rawFonts.get(i);
                Map<String, Object> fo = new LinkedHashMap<>();
                fo.put("face", f.face);
                fo.put("size", f.size);
                if (f.bold) fo.put("bold", true);
                if (f.italic) fo.put("italic", true);
                if (f.underline) fo.put("underline", true);
                if (f.strikeout) fo.put("strikeout", true);
                out.put(name, fo);
            }
            // default без полезной нагрузки тоже опускаем, если нет шрифтов.
            return out;
        }

        Map<String, Object> stylesOut() {
            Map<String, Object> out = new LinkedHashMap<>();
            // Только использованные стили; пустой default опускаем.
            Set<String> usedStyles = new HashSet<>();
            for (RowData rd : rowData.values()) {
                for (CellData c : rd.cells) {
                    String key = formatToKey.get(c.formatIdx);
                    if (key != null) usedStyles.add(styleNames.get(key));
                }
            }
            for (Map.Entry<String, Map<String, Object>> e : styleDefs.entrySet()) {
                if (!usedStyles.contains(e.getKey())) continue;
                if ("default".equals(e.getKey()) && e.getValue().isEmpty()) continue;
                if (e.getValue().isEmpty()) continue;
                out.put(e.getKey(), e.getValue());
            }
            return out;
        }
    }

    private static final class BorderDesc {
        final String border;
        final boolean thick;
        BorderDesc(String border, boolean thick) { this.border = border; this.thick = thick; }
    }

    // ==================== Сборка строк DSL ====================

    private List<Object> buildRows(Map<Integer, RowData> rowData, int beginRow, int endRow,
                                   Map<String, int[]> mergeMap, StyleNamer namer) {
        List<Object> rows = new ArrayList<>();
        List<Map<String, Object>> raw = new ArrayList<>();

        for (int r = beginRow; r <= endRow; r++) {
            RowData rd = rowData.get(r);
            if (rd == null || rd.empty) {
                raw.add(new LinkedHashMap<>()); // пустая строка
                continue;
            }
            Map<String, Object> dslRow = new LinkedHashMap<>();

            // Разделяем content-ячейки и gap-fill (только формат, без содержимого и merge).
            List<CellData> content = new ArrayList<>();
            List<CellData> gap = new ArrayList<>();
            for (CellData c : rd.cells) {
                boolean hasContent = c.param != null || c.text != null;
                boolean hasMerge = mergeMap.containsKey(r + "," + c.col)
                        || c.legacySpan != null || c.legacyRowspan != null;
                if (hasContent || hasMerge) content.add(c); else gap.add(c);
            }

            // rowStyle: если все gap-ячейки имеют один стиль.
            String rowStyleName = null;
            String rowStyleKey = null;
            if (!gap.isEmpty()) {
                Set<String> keys = new LinkedHashSet<>();
                for (CellData gc : gap) {
                    keys.add(namer.styleKey(getFormat(namer.rawFormats, gc.formatIdx)));
                }
                if (keys.size() == 1) {
                    rowStyleKey = keys.iterator().next();
                    String nm = namer.nameForKey(rowStyleKey);
                    if (nm != null && !"default".equals(nm)) {
                        rowStyleName = nm;
                        dslRow.put("rowStyle", rowStyleName);
                    }
                }
            }

            List<Object> dslCells = new ArrayList<>();
            content.sort(Comparator.comparingInt(c -> c.col));
            for (CellData c : content) {
                Map<String, Object> dc = new LinkedHashMap<>();
                dc.put("col", c.col + 1);

                int[] m = mergeMap.get(r + "," + c.col);
                if (m != null) {
                    if (m[0] > 0) dc.put("span", m[0] + 1);
                    if (m[1] > 0) dc.put("rowspan", m[1] + 1);
                } else {
                    if (c.legacySpan != null && c.legacySpan > 1) dc.put("span", c.legacySpan);
                    if (c.legacyRowspan != null && c.legacyRowspan > 1) dc.put("rowspan", c.legacyRowspan);
                }

                RawFormat fmt = getFormat(namer.rawFormats, c.formatIdx);
                String cellKey = namer.styleKey(fmt);
                if (rowStyleKey != null && cellKey.equals(rowStyleKey)) {
                    // наследует rowStyle
                } else {
                    String sn = namer.nameForKey(cellKey);
                    if (sn == null) sn = "default";
                    if (!"default".equals(sn) || rowStyleName == null) {
                        if (!"default".equals(sn)) dc.put("style", sn);
                    }
                }

                String fillType = fmt != null ? fmt.fillType : "";
                if (c.param != null) {
                    dc.put("param", c.param);
                    if (c.detail != null) dc.put("detail", c.detail);
                } else if ("Template".equals(fillType) && c.text != null) {
                    dc.put("template", c.text);
                } else if (c.text != null) {
                    dc.put("text", c.text);
                }
                dslCells.add(dc);
            }
            if (!dslCells.isEmpty()) dslRow.put("cells", dslCells);
            raw.add(dslRow);
        }

        // Сжать подряд идущие пустые строки в {empty: N}.
        int emptyRun = 0;
        for (Map<String, Object> r : raw) {
            if (r.isEmpty()) {
                emptyRun++;
            } else {
                flushEmpty(rows, emptyRun);
                emptyRun = 0;
                rows.add(r);
            }
        }
        flushEmpty(rows, emptyRun);
        return rows;
    }

    private void flushEmpty(List<Object> rows, int emptyRun) {
        if (emptyRun <= 0) return;
        if (emptyRun == 1) {
            rows.add(new LinkedHashMap<>());
        } else {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("empty", emptyRun);
            rows.add(e);
        }
    }

    // ==================== Извлечение текста / параметров ====================

    private String extractText(XmlNode cInner) {
        XmlNode tl = cInner.child("tl");
        if (tl != null) {
            String content = extractMlContent(tl);
            if (content != null) return content;
        }
        return null;
    }

    private String extractParam(XmlNode cInner) {
        XmlNode param = cInner.child("parameter");
        if (param == null) return null;
        String content = extractMlContent(param);
        if (content != null) return content;
        return param.getText();
    }

    private String extractMlContent(XmlNode node) {
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null) return content.getText();
        }
        String content = node.childText("content");
        if (content != null) return content;
        return node.getText();
    }

    // ==================== Helpers ====================

    private static int parseInt(String s, int def) {
        if (s == null) return def;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null || s.isEmpty()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static String orEmpty(String s) { return s == null ? "" : s; }

    private static String nonEmpty(String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }

    private int maxColumnsSize(XmlNode root) {
        int max = 0;
        for (XmlNode columns : root.children("columns")) {
            max = Math.max(max, parseInt(columns.childText("size"), 0));
        }
        return max;
    }

    private int maxUsedColumn(Map<Integer, RowData> rowData, Map<String, int[]> mergeMap) {
        int max = -1;
        for (Map.Entry<Integer, RowData> rowEntry : rowData.entrySet()) {
            int row = rowEntry.getKey();
            for (CellData cell : rowEntry.getValue().cells) {
                int span = 0;
                int[] merge = mergeMap.get(row + "," + cell.col);
                if (merge != null) {
                    span = Math.max(0, merge[0]);
                } else if (cell.legacySpan != null && cell.legacySpan > 1) {
                    span = cell.legacySpan - 1;
                }
                max = Math.max(max, cell.col + span);
            }
        }
        return max;
    }

    private static final class RowData {
        int formatIdx = 0;
        boolean empty = false;
        final List<CellData> cells = new ArrayList<>();
    }

    private static final class CellData {
        int col;
        int formatIdx;
        String param;
        String detail;
        String text;
        Integer legacySpan;
        Integer legacyRowspan;
    }

    private static final class NamedArea {
        final String name;
        final int beginRow, endRow;
        NamedArea(String name, int beginRow, int endRow) {
            this.name = name; this.beginRow = beginRow; this.endRow = endRow;
        }
    }
}

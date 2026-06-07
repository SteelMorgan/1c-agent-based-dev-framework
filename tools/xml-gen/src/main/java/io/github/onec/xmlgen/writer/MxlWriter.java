package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Генератор XML для табличного документа 1С (SpreadsheetDocument).
 *
 * TASK-171: переписан на КАНОНИЧЕСКУЮ модель платформы 1С (референс —
 * Широков mxl-compile.py + 1c-spreadsheet-spec.md). Прежний writer генерировал
 * собственный не-канонический диалект:
 *   - объединения как in-cell &lt;merge&gt;N (платформа: document-level &lt;merge&gt;&lt;r&gt;&lt;c&gt;&lt;w&gt;&lt;h&gt;);
 *   - бордюры как вложенный &lt;border&gt;&lt;top&gt;&lt;style&gt; (платформа: скаляр &lt;leftBorder&gt;idx + палитра &lt;line&gt;);
 *   - палитры с именованными &lt;id&gt; (платформа: числовые индексы &lt;f&gt;N/&lt;font&gt;N).
 * Такой XML не загружался платформой корректно и не round-trip-ился декомпилятором
 * реальных макетов (теря­лось 70-95% форматирования). Теперь пишем канон:
 * числовые палитры шрифтов/линий/форматов, document-level merge, scalar borders.
 *
 * DSL-контракт (JSON: columns/columnWidths/fonts/styles/areas + cell span/rowspan/style)
 * сохранён без изменений на входе.
 */
public class MxlWriter extends XmlWriter {

    private final OutputFormat format;

    private static final Map<String, String> MXL_NAMESPACES = new HashMap<>();
    static {
        MXL_NAMESPACES.put("", "http://v8.1c.ru/8.2/data/spreadsheet");
        MXL_NAMESPACES.put("style", "http://v8.1c.ru/8.1/data/ui/style");
        MXL_NAMESPACES.put("v8", "http://v8.1c.ru/8.1/data/core");
        MXL_NAMESPACES.put("v8ui", "http://v8.1c.ru/8.1/data/ui");
        MXL_NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
        MXL_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }

    public MxlWriter(OutputFormat format) {
        this.format = format;
    }

    public void create(MxlDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        // EDT и Designer формат табличного документа идентичны побайтно
        // (см. 1c-spreadsheet-spec.md §«Совместимость версий»). Разница только в
        // расширении файла, поэтому генерация общая.
        createDesigner(dsl, outputPath);
    }

    // ==================== Палитры (канон) ====================

    /** Шрифт палитры (числовой индекс = позиция). */
    private static final class FontEntry {
        String face = "Arial";
        int size = 10;
        boolean bold, italic, underline, strikeout;
    }

    /** Запись формата палитры (1-based индекс при ссылке). */
    private static final class FormatEntry {
        int fontIdx = -1;
        int lb = -1, tb = -1, rb = -1, bb = -1;
        int width = -1, height = -1;
        String ha = "", va = "";
        boolean wrap = false;
        String fillType = "";
        String numberFormat = "";
        // TASK-171 (R5): цвета. Пустая строка = не задан. Хранятся как литерал из DSL
        // ("#RRGGBB" или "style:ИмяЦвета") — пишутся в XML дословно, по канону реальных макетов.
        String textColor = "";
        String backColor = "";
        String borderColor = "";

        String key() {
            return "f=" + fontIdx + "|lb=" + lb + "|tb=" + tb + "|rb=" + rb + "|bb=" + bb
                    + "|ha=" + ha + "|va=" + va + "|wr=" + wrap + "|ft=" + fillType
                    + "|nf=" + numberFormat + "|w=" + width + "|h=" + height
                    + "|tc=" + textColor + "|bc=" + backColor + "|brc=" + borderColor;
        }
    }

    // Состояние генерации (заполняется в createDesigner).
    private final List<FontEntry> fontEntries = new ArrayList<>();
    private final Map<String, Integer> fontNameToIdx = new HashMap<>(); // имя DSL -> 0-based индекс
    private int thinLineIndex = -1;
    private int thickLineIndex = -1;
    private int lineCount = 0;
    private final List<FormatEntry> formatOrder = new ArrayList<>();
    private final Map<String, Integer> formatKeyToIndex = new HashMap<>(); // key -> 1-based индекс

    private void resetState() {
        fontEntries.clear();
        fontNameToIdx.clear();
        thinLineIndex = thickLineIndex = -1;
        lineCount = 0;
        formatOrder.clear();
        formatKeyToIndex.clear();
    }

    private void createDesigner(MxlDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        resetState();

        int totalColumns = dsl.getColumns() != null ? dsl.getColumns() : 1;
        int defaultWidth = dsl.getDefaultWidth() != null ? dsl.getDefaultWidth() : 10;
        // TASK-171 (R8): авто-расчёт defaultWidth из page при наличии "Nx" пропорций
        // (порт mxl-compile.py:120-161). Без этого "Nx" молча терялись (возвращали null).
        defaultWidth = computeDefaultWidth(dsl, totalColumns, defaultWidth);

        // --- 1. Палитра шрифтов ---
        boolean hasDefaultFont = false;
        if (dsl.getFonts() != null) {
            for (Map.Entry<String, MxlDsl.Font> e : dsl.getFonts().entrySet()) {
                if ("default".equals(e.getKey())) hasDefaultFont = true;
                addFont(e.getKey(), e.getValue());
            }
        }
        if (!hasDefaultFont) {
            FontEntry def = new FontEntry();
            fontNameToIdx.put("default", fontEntries.size());
            fontEntries.add(def);
        }

        // --- 2. Палитра линий: определяем какие толщины нужны ---
        boolean hasThin = false, hasThick = false;
        if (dsl.getStyles() != null) {
            for (MxlDsl.Style s : dsl.getStyles().values()) {
                if (s.getBorder() != null && !"none".equalsIgnoreCase(s.getBorder())) {
                    if ("thick".equalsIgnoreCase(s.getBorderWidth())) hasThick = true;
                    else hasThin = true;
                }
            }
        }
        if (hasThin) { thinLineIndex = lineCount++; }
        if (hasThick) { thickLineIndex = lineCount++; }

        // --- 3. Ширины колонок ---
        Map<Integer, Integer> perColumnWidths = expandColumnWidths(dsl, defaultWidth); // 1-based col -> width

        // --- 4. Регистрируем формат default-width (индекс 1, defaultFormatIndex) ---
        FormatEntry defFmt = new FormatEntry();
        defFmt.width = defaultWidth;
        int defaultFormatIndex = registerFormat(defFmt);

        // Форматы ширин колонок (1-based col -> formatIndex).
        Map<Integer, Integer> colFormatMap = new LinkedHashMap<>();
        List<Integer> colsSorted = new ArrayList<>(perColumnWidths.keySet());
        java.util.Collections.sort(colsSorted);
        for (Integer col : colsSorted) {
            FormatEntry fe = new FormatEntry();
            fe.width = perColumnWidths.get(col);
            colFormatMap.put(col, registerFormat(fe));
        }

        // --- 5. Предрегистрируем все форматы ячеек/строк из областей ---
        if (dsl.getAreas() != null) {
            for (MxlDsl.Area area : dsl.getAreas()) {
                if (area.getRows() == null) continue;
                for (MxlDsl.Row row : area.getRows()) {
                    if (row.getEmpty() != null && row.getEmpty() > 0) continue;
                    if (row.getHeight() != null) {
                        FormatEntry hf = new FormatEntry();
                        hf.height = row.getHeight();
                        registerFormat(hf);
                    }
                    if (row.getRowStyle() != null) {
                        registerCellFormat(dsl, row.getRowStyle(), "");
                    }
                    if (row.getCells() != null) {
                        for (MxlDsl.Cell cell : row.getCells()) {
                            String styleName = cell.getStyle() != null ? cell.getStyle()
                                    : (row.getRowStyle() != null ? row.getRowStyle() : "default");
                            registerCellFormat(dsl, styleName, fillTypeOf(cell));
                        }
                    }
                }
            }
        }

        // --- 6. Генерация XML ---
        // TASK-171: реальные платформенные MXL Template.xml — с UTF-8 BOM (как СКД/форма).
        // isMetadataFile теперь покрывает mxl → валидатор ждёт BOM; пишем его.
        createWriter(outputPath, true, MXL_NAMESPACES);
        writeXmlDeclaration();
        writeRootElement("document", MXL_NAMESPACES, new HashMap<>());

        // Language settings
        writeLanguageSettings();

        // Columns
        startElement("columns");
        writeElement("size", String.valueOf(totalColumns));
        for (Integer col : colsSorted) {
            startElement("columnsItem");
            writeElement("index", String.valueOf(col - 1)); // 0-based
            startElement("column");
            writeElement("formatIndex", String.valueOf(colFormatMap.get(col)));
            endElement(); // column
            endElement(); // columnsItem
        }
        endElement(); // columns

        // Rows + сбор merges и именованных областей.
        List<int[]> merges = new ArrayList<>(); // [r,c,w,h]  (h=-1 если нет)
        List<NamedAreaRange> namedRanges = new ArrayList<>();
        int rowIndex = 0;
        if (dsl.getAreas() != null) {
            for (MxlDsl.Area area : dsl.getAreas()) {
                int areaStart = rowIndex;
                rowIndex = writeArea(dsl, area, rowIndex, merges);
                int areaEnd = rowIndex - 1;
                if (area.getName() != null && !area.getName().isEmpty() && areaEnd >= areaStart) {
                    namedRanges.add(new NamedAreaRange(area.getName(), areaStart, areaEnd));
                }
            }
        }
        int totalRows = rowIndex;

        // Drawings (document-level, после rowsItem — порядок канона).
        // TASK-171 (R9): рисунки пишутся round-trip-стабильно из DSL.
        if (dsl.getDrawings() != null) {
            for (MxlDsl.Drawing d : dsl.getDrawings()) {
                writeDrawing(d);
            }
        }

        // Scalar metadata (порядок канона).
        writeElement("templateMode", "true");
        writeElement("defaultFormatIndex", String.valueOf(defaultFormatIndex));
        writeElement("height", String.valueOf(totalRows));
        writeElement("vgRows", String.valueOf(totalRows));

        // Merges (document-level).
        for (int[] m : merges) {
            startElement("merge");
            writeElement("r", String.valueOf(m[0]));
            writeElement("c", String.valueOf(m[1]));
            if (m[3] > 0) writeElement("h", String.valueOf(m[3]));
            writeElement("w", String.valueOf(m[2]));
            endElement(); // merge
        }

        // TASK-171: document-wide column merges (<r>-1</r>) — отдельный список DSL,
        // т.к. не привязаны к строке/ячейке. Канон: 1c-spreadsheet-spec.md §«Объединения».
        if (dsl.getColumnMerges() != null) {
            for (MxlDsl.ColumnMerge cm : dsl.getColumnMerges()) {
                startElement("merge");
                writeElement("r", "-1");
                writeElement("c", String.valueOf(cm.getC() != null ? cm.getC() : 0));
                if (cm.getH() != null && cm.getH() > 0) writeElement("h", String.valueOf(cm.getH()));
                writeElement("w", String.valueOf(cm.getW() != null ? cm.getW() : 0));
                endElement(); // merge
            }
        }

        // TASK-171: verticalUnmerge — после merge, до namedItem (порядок канона).
        if (dsl.getVerticalUnmerges() != null) {
            for (MxlDsl.Unmerge u : dsl.getVerticalUnmerges()) {
                startElement("verticalUnmerge");
                writeElement("r", String.valueOf(u.getR() != null ? u.getR() : 0));
                writeElement("c", String.valueOf(u.getC() != null ? u.getC() : 0));
                if (u.getW() != null && u.getW() > 0) writeElement("w", String.valueOf(u.getW()));
                endElement(); // verticalUnmerge
            }
        }

        // Named items (Cells-области).
        for (NamedAreaRange na : namedRanges) {
            writeNamedItem(na);
        }
        // TASK-171: именованные рисунки (NamedItemDrawing) — для drawing с name.
        if (dsl.getDrawings() != null) {
            for (MxlDsl.Drawing d : dsl.getDrawings()) {
                if (d.getName() != null && !d.getName().isEmpty() && d.getId() != null) {
                    writeNamedDrawing(d.getName(), d.getId());
                }
            }
        }

        // Line palette.
        if (hasThin) writeLine(1);
        if (hasThick) writeLine(2);

        // Font palette.
        for (FontEntry fe : fontEntries) {
            writeFontEntry(fe);
        }

        // Format palette.
        for (FormatEntry fe : formatOrder) {
            writeFormatEntry(fe);
        }

        // Picture palette (после format — порядок канона).
        // TASK-171 (R9): ресурсы картинок (base64 data или ref), round-trip-стабильно.
        if (dsl.getPictures() != null) {
            for (MxlDsl.Picture p : dsl.getPictures()) {
                writePicture(p);
            }
        }

        writer.writeEndElement(); // document
        close();

        System.out.println("Created MXL template: " + outputPath);
    }

    // ==================== Регистрация шрифтов/форматов ====================

    private void addFont(String name, MxlDsl.Font f) {
        FontEntry fe = new FontEntry();
        if (f != null) {
            if (f.getFace() != null) fe.face = f.getFace();
            if (f.getSize() != null) fe.size = f.getSize();
            fe.bold = Boolean.TRUE.equals(f.getBold());
            fe.italic = Boolean.TRUE.equals(f.getItalic());
            fe.underline = Boolean.TRUE.equals(f.getUnderline());
            fe.strikeout = Boolean.TRUE.equals(f.getStrikeout());
        }
        fontNameToIdx.put(name, fontEntries.size());
        fontEntries.add(fe);
    }

    /** Зарегистрировать формат, вернуть его 1-based индекс (дедуп по сигнатуре). */
    private int registerFormat(FormatEntry fe) {
        String key = fe.key();
        Integer existing = formatKeyToIndex.get(key);
        if (existing != null) return existing;
        formatOrder.add(fe);
        int idx = formatOrder.size(); // 1-based
        formatKeyToIndex.put(key, idx);
        return idx;
    }

    /** Разрешить именованный стиль DSL в FormatEntry и зарегистрировать; вернуть 1-based индекс. */
    private int registerCellFormat(MxlDsl dsl, String styleName, String fillType) {
        FormatEntry fe = resolveStyle(dsl, styleName, fillType);
        return registerFormat(fe);
    }

    /** Разрешить именованный стиль DSL в числовую модель формата (font idx, border idx, ...). */
    private FormatEntry resolveStyle(MxlDsl dsl, String styleName, String fillType) {
        FormatEntry fe = new FormatEntry();
        fe.fontIdx = fontNameToIdx.getOrDefault("default", 0);
        fe.fillType = fillType != null ? fillType : "";

        MxlDsl.Style style = (styleName != null && dsl.getStyles() != null)
                ? dsl.getStyles().get(styleName) : null;
        if (style != null) {
            if (style.getFont() != null && fontNameToIdx.containsKey(style.getFont())) {
                fe.fontIdx = fontNameToIdx.get(style.getFont());
            }
            if (style.getBorder() != null && !"none".equalsIgnoreCase(style.getBorder())) {
                int lineIdx = "thick".equalsIgnoreCase(style.getBorderWidth()) ? thickLineIndex : thinLineIndex;
                for (String sideRaw : style.getBorder().split(",")) {
                    String side = sideRaw.trim().toLowerCase();
                    switch (side) {
                        case "all": fe.lb = fe.tb = fe.rb = fe.bb = lineIdx; break;
                        case "left": fe.lb = lineIdx; break;
                        case "top": fe.tb = lineIdx; break;
                        case "right": fe.rb = lineIdx; break;
                        case "bottom": fe.bb = lineIdx; break;
                        default: break;
                    }
                }
            }
            if (style.getAlign() != null) {
                String a = style.getAlign().toLowerCase();
                fe.ha = "left".equals(a) ? "Left" : "center".equals(a) ? "Center"
                        : "right".equals(a) ? "Right" : "";
            }
            if (style.getValign() != null) {
                String v = style.getValign().toLowerCase();
                fe.va = "top".equals(v) ? "Top" : "center".equals(v) ? "Center" : "";
            }
            if (Boolean.TRUE.equals(style.getWrap())) fe.wrap = true;
            if (style.getFormat() != null) fe.numberFormat = style.getFormat();
            // TASK-171 (R5): цвета — пишем литерал как есть (hex/style-ref).
            if (style.getTextColor() != null) fe.textColor = style.getTextColor();
            if (style.getBackColor() != null) fe.backColor = style.getBackColor();
            if (style.getBorderColor() != null) fe.borderColor = style.getBorderColor();
        }
        return fe;
    }

    private String fillTypeOf(MxlDsl.Cell cell) {
        if (cell.getParam() != null) return "Parameter";
        if (cell.getTemplate() != null) return "Template";
        if (cell.getText() != null) return "Text";
        return "";
    }

    // ==================== Запись областей и ячеек ====================

    /**
     * Записать область. Возвращает новый индекс строки.
     * Собирает merges в переданный список (document-level).
     */
    private int writeArea(MxlDsl dsl, MxlDsl.Area area, int startRowIndex, List<int[]> merges)
            throws XMLStreamException {
        if (area.getRows() == null || area.getRows().isEmpty()) return startRowIndex;

        int currentRow = startRowIndex;
        Map<Integer, Integer> verticalOccupancyUntil = new HashMap<>(); // 1-based col -> last row index
        for (MxlDsl.Row row : area.getRows()) {
            // Пустые строки ({empty:N}). TASK-171: канон Широкова (mxl-compile.py:368-376)
            // эмитит ЯВНЫЙ <rowsItem><empty>true</empty> на каждую пустую строку, а не просто
            // пропускает индекс. Без этого rowsItem-count < height → MXL-004 + ложные MXL-203
            // (rowspan «за пределы документа») и потеря 1 строки при round-trip реальных макетов.
            if (row.getEmpty() != null && row.getEmpty() > 0) {
                for (int k = 0; k < row.getEmpty(); k++) {
                    writeEmptyRowsItem(currentRow);
                    currentRow++;
                }
                continue;
            }
            boolean hasCells = row.getCells() != null && !row.getCells().isEmpty();
            boolean hasRowStyle = row.getRowStyle() != null;
            if (!hasCells && !hasRowStyle) {
                // Одиночная пустая строка ({}) — тоже явный <empty>true</empty> (канон).
                writeEmptyRowsItem(currentRow);
                currentRow++;
                continue;
            }

            // Формат строки (высота).
            int rowFormatIdx = 0;
            if (row.getHeight() != null) {
                FormatEntry hf = new FormatEntry();
                hf.height = row.getHeight();
                rowFormatIdx = registerFormat(hf);
            }

            // Открыть rowsItem.
            writer.writeCharacters("\t");
            writer.writeStartElement("rowsItem");
            writer.writeCharacters("\n");
            indentLevel = 2;
            writeElement("index", String.valueOf(currentRow));
            startElement("row");

            if (rowFormatIdx > 0) {
                writeElement("formatIndex", String.valueOf(rowFormatIdx));
            }

            // Занятые колонки (1-based) для gap-fill.
            int totalColumns = dsl.getColumns() != null ? dsl.getColumns() : 1;
            java.util.Set<Integer> occupied = new java.util.HashSet<>();
            final int rowIndex = currentRow;
            verticalOccupancyUntil.entrySet().removeIf(e -> e.getValue() < rowIndex);
            occupied.addAll(verticalOccupancyUntil.keySet());

            // Сначала пишем явные ячейки + собираем merges.
            List<CellEmit> emits = new ArrayList<>();
            if (hasCells) {
                for (MxlDsl.Cell cell : row.getCells()) {
                    int colStart = cell.getCol() != null ? cell.getCol() : 1; // 1-based
                    int span = cell.getSpan() != null && cell.getSpan() > 1 ? cell.getSpan() : 1;
                    int rowspan = cell.getRowspan() != null && cell.getRowspan() > 1 ? cell.getRowspan() : 1;
                    for (int c = colStart; c < colStart + span; c++) occupied.add(c);
                    if (rowspan > 1) {
                        int lastOccupiedRow = currentRow + rowspan - 1;
                        for (int c = colStart; c < colStart + span; c++) {
                            verticalOccupancyUntil.merge(c, lastOccupiedRow, Math::max);
                        }
                    }

                    String styleName = cell.getStyle() != null ? cell.getStyle()
                            : (row.getRowStyle() != null ? row.getRowStyle() : "default");
                    int fmtIdx = registerCellFormat(dsl, styleName, fillTypeOf(cell));

                    emits.add(new CellEmit(colStart - 1, fmtIdx, cell.getParam(), cell.getDetail(),
                            cell.getText(), cell.getTemplate(),
                            Boolean.TRUE.equals(cell.getDetailRecord())));

                    // Document-level merge.
                    if (span > 1 || rowspan > 1) {
                        merges.add(new int[]{currentRow, colStart - 1, span - 1, rowspan > 1 ? rowspan - 1 : -1});
                    }
                }
            }

            // gap-fill: пустые ячейки по rowStyle для незанятых колонок (канон Широкова).
            if (hasRowStyle) {
                int gapFmtIdx = registerCellFormat(dsl, row.getRowStyle(), "");
                for (int c = 1; c <= totalColumns; c++) {
                    if (!occupied.contains(c)) {
                        emits.add(new CellEmit(c - 1, gapFmtIdx, null, null, null, null, false));
                    }
                }
            }

            // Сортировка по колонке и запись.
            emits.sort(java.util.Comparator.comparingInt(e -> e.col));
            for (CellEmit e : emits) {
                writeCellCanonical(e);
            }

            endElement(); // row
            indentLevel = 1;
            writer.writeCharacters("\t");
            writer.writeEndElement(); // rowsItem
            writer.writeCharacters("\n");
            currentRow++;
        }
        return currentRow;
    }

    /** TASK-171: явный пустой rowsItem (&lt;empty&gt;true&lt;/empty&gt;) для строки index. */
    private void writeEmptyRowsItem(int index) throws XMLStreamException {
        writer.writeCharacters("\t");
        writer.writeStartElement("rowsItem");
        writer.writeCharacters("\n");
        indentLevel = 2;
        writeElement("index", String.valueOf(index));
        startElement("row");
        writeElement("empty", "true");
        endElement(); // row
        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // rowsItem
        writer.writeCharacters("\n");
    }

    private static final class CellEmit {
        final int col; // 0-based
        final int formatIdx; // 1-based
        final String param, detail, text, template;
        final boolean detailRecord;
        CellEmit(int col, int formatIdx, String param, String detail, String text,
                 String template, boolean detailRecord) {
            this.col = col; this.formatIdx = formatIdx;
            this.param = param; this.detail = detail; this.text = text;
            this.template = template; this.detailRecord = detailRecord;
        }
    }

    private void writeCellCanonical(CellEmit e) throws XMLStreamException {
        startElement("c"); // cell group
        writeElement("i", String.valueOf(e.col));
        startElement("c"); // cell content
        writeElement("f", String.valueOf(e.formatIdx));

        if (e.param != null) {
            writeElement("parameter", e.param);
            if (e.detail != null && !e.detail.isEmpty()) {
                writeElement("detailParameter", e.detail);
            }
        }
        if (e.text != null) {
            writeCellText(e.text);
        }
        if (e.template != null) {
            writeCellText(e.template); // в каноне Template хранится в <tl>, fillType=Template
        }
        if (e.detailRecord) {
            writeElement("detailRecord", "true");
        }

        endElement(); // c (inner)
        endElement(); // c (outer)
    }

    // ==================== Палитра: запись элементов ====================

    private void writeLine(int width) throws XMLStreamException {
        writeIndentLocal();
        writer.writeStartElement("line");
        writer.writeAttribute("width", String.valueOf(width));
        writer.writeAttribute("gap", "false");
        writer.writeCharacters("\n");
        indentLevel++;
        // <v8ui:style xsi:type="v8ui:SpreadsheetDocumentCellLineType">Solid</v8ui:style>
        writeIndentLocal();
        writer.writeStartElement("v8ui", "style", "http://v8.1c.ru/8.1/data/ui");
        writer.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance",
                "type", "v8ui:SpreadsheetDocumentCellLineType");
        writer.writeCharacters("Solid");
        writer.writeEndElement();
        writer.writeCharacters("\n");
        indentLevel--;
        writeIndentLocal();
        writer.writeEndElement(); // line
        writer.writeCharacters("\n");
    }

    private void writeFontEntry(FontEntry fe) throws XMLStreamException {
        writeIndentLocal();
        writer.writeEmptyElement("font");
        writer.writeAttribute("faceName", fe.face);
        writer.writeAttribute("height", String.valueOf(fe.size));
        writer.writeAttribute("bold", String.valueOf(fe.bold));
        writer.writeAttribute("italic", String.valueOf(fe.italic));
        writer.writeAttribute("underline", String.valueOf(fe.underline));
        writer.writeAttribute("strikeout", String.valueOf(fe.strikeout));
        writer.writeAttribute("kind", "Absolute");
        writer.writeAttribute("scale", "100");
        writer.writeCharacters("\n");
    }

    private void writeFormatEntry(FormatEntry fe) throws XMLStreamException {
        startElement("format");
        if (fe.fontIdx >= 0) writeElement("font", String.valueOf(fe.fontIdx));
        if (fe.lb >= 0) writeElement("leftBorder", String.valueOf(fe.lb));
        if (fe.tb >= 0) writeElement("topBorder", String.valueOf(fe.tb));
        if (fe.rb >= 0) writeElement("rightBorder", String.valueOf(fe.rb));
        if (fe.bb >= 0) writeElement("bottomBorder", String.valueOf(fe.bb));
        // TASK-171 (R5): borderColor сразу после индексов границ — порядок канона
        // (_ДемоОписатель/Ext/Template.xml: <border>..<borderColor>..<width>).
        if (!fe.borderColor.isEmpty()) writeElement("borderColor", fe.borderColor);
        if (fe.width >= 0) writeElement("width", String.valueOf(fe.width));
        if (fe.height >= 0) writeElement("height", String.valueOf(fe.height));
        if (!fe.ha.isEmpty()) writeElement("horizontalAlignment", fe.ha);
        if (!fe.va.isEmpty()) writeElement("verticalAlignment", fe.va);
        // TASK-171 (R5): textColor/backColor после выравнивания, до textPlacement
        // (канон: СчётНаОплату/Описатель — verticalAlignment, textColor, backColor, textPlacement).
        if (!fe.textColor.isEmpty()) writeElement("textColor", fe.textColor);
        if (!fe.backColor.isEmpty()) writeElement("backColor", fe.backColor);
        if (fe.wrap) writeElement("textPlacement", "Wrap");
        if (!fe.fillType.isEmpty()) writeElement("fillType", fe.fillType);
        if (!fe.numberFormat.isEmpty()) {
            startElement("format");
            startElement("v8:item");
            writeElement("v8:lang", "ru");
            writeElement("v8:content", fe.numberFormat);
            endElement(); // v8:item
            endElement(); // format (number format string)
        }
        endElement(); // format
    }

    // ==================== namedItem / pageSetup / текст ====================

    private void writeNamedItem(NamedAreaRange na) throws XMLStreamException {
        writeIndentLocal();
        writer.writeStartElement("namedItem");
        writer.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance",
                "type", "NamedItemCells");
        writer.writeCharacters("\n");
        indentLevel++;

        writeElement("name", na.name);
        startElement("area");
        writeElement("type", "Rows");
        writeElement("beginRow", String.valueOf(na.beginRow));
        writeElement("endRow", String.valueOf(na.endRow));
        writeElement("beginColumn", "-1");
        writeElement("endColumn", "-1");
        endElement(); // area

        indentLevel--;
        writeIndentLocal();
        writer.writeEndElement(); // namedItem
        writer.writeCharacters("\n");
    }

    /**
     * TASK-171 (R9): записать &lt;drawing&gt; (рисунок). Порядок элементов — канон
     * (1c-spreadsheet-spec.md §«Рисунки»). Только заданные поля.
     */
    private void writeDrawing(MxlDsl.Drawing d) throws XMLStreamException {
        startElement("drawing");
        if (d.getDrawingType() != null) writeElement("drawingType", d.getDrawingType());
        if (d.getId() != null) writeElement("id", String.valueOf(d.getId()));
        if (d.getFormatIndex() != null) writeElement("formatIndex", String.valueOf(d.getFormatIndex()));
        if (d.getBeginRow() != null) writeElement("beginRow", String.valueOf(d.getBeginRow()));
        if (d.getBeginRowOffset() != null) writeElement("beginRowOffset", String.valueOf(d.getBeginRowOffset()));
        if (d.getEndRow() != null) writeElement("endRow", String.valueOf(d.getEndRow()));
        if (d.getEndRowOffset() != null) writeElement("endRowOffset", String.valueOf(d.getEndRowOffset()));
        if (d.getBeginColumn() != null) writeElement("beginColumn", String.valueOf(d.getBeginColumn()));
        if (d.getBeginColumnOffset() != null) writeElement("beginColumnOffset", String.valueOf(d.getBeginColumnOffset()));
        if (d.getEndColumn() != null) writeElement("endColumn", String.valueOf(d.getEndColumn()));
        if (d.getEndColumnOffset() != null) writeElement("endColumnOffset", String.valueOf(d.getEndColumnOffset()));
        if (d.getAutoSize() != null) writeElement("autoSize", String.valueOf(d.getAutoSize()));
        if (d.getPictureSize() != null) writeElement("pictureSize", d.getPictureSize());
        if (d.getZOrder() != null) writeElement("zOrder", String.valueOf(d.getZOrder()));
        if (d.getPictureIndex() != null) writeElement("pictureIndex", String.valueOf(d.getPictureIndex()));
        endElement(); // drawing
    }

    /** TASK-171: именованный рисунок (&lt;namedItem xsi:type="NamedItemDrawing"&gt;). */
    private void writeNamedDrawing(String name, int drawingId) throws XMLStreamException {
        writeIndentLocal();
        writer.writeStartElement("namedItem");
        writer.writeAttribute("xsi", "http://www.w3.org/2001/XMLSchema-instance",
                "type", "NamedItemDrawing");
        writer.writeCharacters("\n");
        indentLevel++;
        writeElement("name", name);
        writeElement("drawingID", String.valueOf(drawingId));
        indentLevel--;
        writeIndentLocal();
        writer.writeEndElement(); // namedItem
        writer.writeCharacters("\n");
    }

    /**
     * TASK-171 (R9): записать ресурс картинки (&lt;picture&gt;).
     * Канон: &lt;index&gt; + вложенный &lt;picture&gt; (base64 data в теле, либо ref-атрибут,
     * либо пустой). См. 1c-spreadsheet-spec.md §«Ресурсы картинок».
     */
    private void writePicture(MxlDsl.Picture p) throws XMLStreamException {
        startElement("picture");
        if (p.getIndex() != null) writeElement("index", String.valueOf(p.getIndex()));
        if (p.getRef() != null && !p.getRef().isEmpty()) {
            // <picture ref="v8ui:Штрихкод"/>
            writeIndentLocal();
            writer.writeEmptyElement("picture");
            writer.writeAttribute("ref", p.getRef());
            writer.writeCharacters("\n");
        } else if (p.getData() != null && !p.getData().isEmpty()) {
            // <picture [t="false"]>base64...</picture> — атрибут t сохраняется для round-trip.
            writeIndentLocal();
            writer.writeStartElement("picture");
            if (p.getT() != null && !p.getT().isEmpty()) writer.writeAttribute("t", p.getT());
            writer.writeCharacters(p.getData());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        } else {
            // <picture/> — пустой ресурс (placeholder index 0 в реальных макетах).
            writeIndentLocal();
            writer.writeEmptyElement("picture");
            writer.writeCharacters("\n");
        }
        endElement(); // picture
    }

    private void writeIndentLocal() throws XMLStreamException {
        for (int i = 0; i < indentLevel; i++) {
            writer.writeCharacters("\t");
        }
    }

    private void writeCellText(String text) throws XMLStreamException {
        startElement("tl");
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        endElement(); // tl
    }

    public static Integer pageWidth(String page) {
        if (page == null || page.isEmpty()) return null;
        if ("A4-landscape".equalsIgnoreCase(page)) return 780;
        if ("A4-portrait".equalsIgnoreCase(page)) return 540;
        try {
            return Integer.parseInt(page.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeLanguageSettings() throws XMLStreamException {
        startElement("languageSettings");
        writeElement("currentLanguage", "ru");
        writeElement("defaultLanguage", "ru");
        startElement("languageInfo");
        writeElement("id", "ru");
        writeElement("code", "Русский");
        writeElement("description", "Русский");
        endElement(); // languageInfo
        endElement(); // languageSettings
    }

    // ==================== columnWidths helpers (как раньше) ====================

    /**
     * Развернуть columnWidths-Map в плоское отображение columnIndex(1-based) -> width.
     * Ключи: "1", "2-8", "5,7,9", "1,3-5,9". Значения — числовые литералы ИЛИ
     * "Nx" пропорции (N * defaultWidth), порт mxl-compile.py:164-174.
     *
     * @param defaultWidth уже вычисленная (с учётом page/Nx) ширина по умолчанию.
     */
    private Map<Integer, Integer> expandColumnWidths(MxlDsl dsl, int defaultWidth) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        Map<String, Object> cw = dsl.getColumnWidths();
        if (cw != null) {
            for (Map.Entry<String, Object> e : cw.entrySet()) {
                Integer w = parseWidthValue(e.getValue(), defaultWidth);
                if (w == null) continue;
                for (int col : parseColumnKey(e.getKey())) {
                    result.put(col, w);
                }
            }
        }
        return result;
    }

    /**
     * TASK-171 (R8): вычислить defaultWidth из page-формата и "Nx"-пропорций.
     * Порт mxl-compile.py:120-161. Если page не задан — возвращает исходное значение.
     * Логика: target = ширина страницы; абсолютные ширины вычитаются, "Nx" суммируются
     * в total_units (неуказанные колонки = 1 unit). defaultWidth = (target - absSum) / units.
     */
    private int computeDefaultWidth(MxlDsl dsl, int totalColumns, int fallbackWidth) {
        Integer target = pageWidth(dsl.getPage());
        if (target == null) return fallbackWidth;

        double totalUnits = 0.0;
        int absoluteSum = 0;
        java.util.Set<Integer> specifiedCols = new java.util.HashSet<>();
        Map<String, Object> cw = dsl.getColumnWidths();
        if (cw != null) {
            for (Map.Entry<String, Object> e : cw.entrySet()) {
                Double units = unitsOf(e.getValue());   // "Nx" → N; иначе null
                Integer abs = absoluteOf(e.getValue()); // число → значение; иначе null
                for (int col : parseColumnKey(e.getKey())) {
                    specifiedCols.add(col);
                    if (units != null) totalUnits += units;
                    else if (abs != null) absoluteSum += abs;
                }
            }
        }
        for (int c = 1; c <= totalColumns; c++) {
            if (!specifiedCols.contains(c)) totalUnits += 1.0;
        }
        int remaining = target - absoluteSum;
        if (remaining < 0 || (remaining == 0 && totalUnits > 0)) {
            throw new IllegalArgumentException("MXL page width " + target
                    + " is not enough for absolute column widths " + absoluteSum);
        }
        if (totalUnits > 0) {
            int computed = (int) Math.round((double) remaining / totalUnits);
            if (computed <= 0) {
                throw new IllegalArgumentException("MXL page width " + target
                        + " produces non-positive default column width " + computed);
            }
            return computed;
        }
        return fallbackWidth;
    }

    /** "Nx" → N (как double), иначе null. */
    private Double unitsOf(Object v) {
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.endsWith("x") || s.endsWith("X")) {
                try { return Double.parseDouble(s.substring(0, s.length() - 1)); }
                catch (NumberFormatException e) { return null; }
            }
        }
        return null;
    }

    /** Абсолютное числовое значение ширины, иначе null ("Nx" → null). */
    private Integer absoluteOf(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.endsWith("x") || s.endsWith("X")) return null;
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) {
                try { return (int) Math.round(Double.parseDouble(s)); }
                catch (NumberFormatException ignored) { return null; }
            }
        }
        return null;
    }

    private List<Integer> parseColumnKey(String key) {
        List<Integer> out = new ArrayList<>();
        if (key == null || key.isEmpty()) return out;
        for (String part : key.split(",")) {
            part = part.trim();
            if (part.isEmpty()) continue;
            int dash = part.indexOf('-');
            try {
                if (dash > 0) {
                    int from = Integer.parseInt(part.substring(0, dash).trim());
                    int to = Integer.parseInt(part.substring(dash + 1).trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) out.add(i);
                } else {
                    out.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ignored) {
                // DSL parser already accepted the value — skip malformed silently.
            }
        }
        return out;
    }

    /**
     * Разобрать значение ширины. Число → как есть; "Nx" → round(N * defaultWidth)
     * (TASK-171 R8, порт mxl-compile.py:168-169).
     */
    private Integer parseWidthValue(Object v, int defaultWidth) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return null;
            if (s.endsWith("x") || s.endsWith("X")) {
                Double units = unitsOf(v);
                if (units == null) return null;
                return (int) Math.round(units * defaultWidth);
            }
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                try {
                    return (int) Math.round(Double.parseDouble(s));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static class NamedAreaRange {
        final String name;
        final int beginRow;
        final int endRow;
        NamedAreaRange(String name, int beginRow, int endRow) {
            this.name = name;
            this.beginRow = beginRow;
            this.endRow = endRow;
        }
    }
}

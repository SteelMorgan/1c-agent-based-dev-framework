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
 * Phase 4: Полная реализация (шрифты, стили, форматирование).
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
    
    /**
     * Создать Template.xml из DSL.
     * 
     * @param dsl JSON DSL табличного документа
     * @param outputPath путь к Template.xml
     */
    public void create(MxlDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            createDesigner(dsl, outputPath);
        } else {
            // EDT формат: тот же XML, просто файл может иметь расширение .mxlx
            createDesigner(dsl, outputPath);
        }
    }
    
    private void createDesigner(MxlDsl dsl, Path outputPath) throws IOException, XMLStreamException {
        createWriter(outputPath, false, MXL_NAMESPACES); // БЕЗ BOM для Template.xml
        writeXmlDeclaration();

        Map<String, String> rootAttrs = new HashMap<>();
        writeRootElement("document", MXL_NAMESPACES, rootAttrs);

        // Language settings
        writeLanguageSettings();

        // Fonts
        if (dsl.getFonts() != null && !dsl.getFonts().isEmpty()) {
            writeFonts(dsl.getFonts());
        }

        // Styles
        if (dsl.getStyles() != null && !dsl.getStyles().isEmpty()) {
            writeStyles(dsl.getStyles(), dsl.getFonts());
        }

        // Column-width formats (one synthesized <format> per unique width).
        // Map<width -> formatId> so each unique width yields exactly one <format>.
        Map<Integer, String> widthFormats = new LinkedHashMap<>();
        // Map<columnIndex (1-based) -> width> derived from columnWidths
        Map<Integer, Integer> perColumnWidths = expandColumnWidths(dsl);
        for (Integer w : perColumnWidths.values()) {
            widthFormats.computeIfAbsent(w, x -> "__cw_" + x);
        }
        for (Map.Entry<Integer, String> e : widthFormats.entrySet()) {
            startElement("format");
            writeElement("id", e.getValue());
            writeElement("width", String.valueOf(e.getKey()));
            endElement(); // format
        }

        // Columns
        startElement("columns");
        writeElement("size", String.valueOf(dsl.getColumns() != null ? dsl.getColumns() : 1));
        // Per-column width overrides (1-based DSL → 0-based XML index)
        if (!perColumnWidths.isEmpty()) {
            List<Integer> cols = new ArrayList<>(perColumnWidths.keySet());
            java.util.Collections.sort(cols);
            for (Integer col : cols) {
                Integer w = perColumnWidths.get(col);
                String fmtId = widthFormats.get(w);
                startElement("columnsItem");
                writeElement("index", String.valueOf(col - 1));
                startElement("column");
                writeElement("formatIndex", fmtId);
                endElement(); // column
                endElement(); // columnsItem
            }
        }
        endElement(); // columns

        // Rows (areas) — collect named area row ranges along the way
        List<NamedAreaRange> namedRanges = new ArrayList<>();
        if (dsl.getAreas() != null && !dsl.getAreas().isEmpty()) {
            int rowIndex = 0;
            for (MxlDsl.Area area : dsl.getAreas()) {
                int areaStart = rowIndex;
                rowIndex = writeArea(area, rowIndex);
                int areaEnd = rowIndex - 1;
                if (area.getName() != null && !area.getName().isEmpty() && areaEnd >= areaStart) {
                    namedRanges.add(new NamedAreaRange(area.getName(), areaStart, areaEnd));
                }
            }
        }

        // Named items (must come after rows so platform can resolve ПолучитьОбласть("X"))
        for (NamedAreaRange na : namedRanges) {
            writeNamedItem(na);
        }

        // Template mode
        writeElement("templateMode", "true");

        // Default format index
        writeElement("defaultFormatIndex", "1");

        // Height (total rows)
        int totalRows = calculateTotalRows(dsl);
        writeElement("height", String.valueOf(totalRows));
        writeElement("vgRows", String.valueOf(totalRows));

        // Format (default column width)
        if (dsl.getDefaultWidth() != null) {
            startElement("format");
            writeElement("width", String.valueOf(dsl.getDefaultWidth()));
            endElement(); // format
        }

        // Page setup (A.8)
        if (dsl.getPage() != null && !dsl.getPage().isEmpty()) {
            writePageSetup(dsl.getPage());
        }

        writer.writeEndElement(); // document
        close();

        System.out.println("Created MXL template: " + outputPath);
    }

    /**
     * Записать <namedItem xsi:type="NamedItemCells"> блок для именованной области.
     */
    private void writeNamedItem(NamedAreaRange na) throws XMLStreamException {
        // <namedItem xsi:type="NamedItemCells">
        writeIndentLocal();
        writer.writeStartElement("namedItem");
        writer.writeAttribute(
                "xsi",
                "http://www.w3.org/2001/XMLSchema-instance",
                "type",
                "NamedItemCells");
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

    private void writeIndentLocal() throws XMLStreamException {
        for (int i = 0; i < indentLevel; i++) {
            writer.writeCharacters("\t");
        }
    }

    /**
     * Развернуть columnWidths-Map в плоское отображение columnIndex(1-based) -> width.
     * Ключи могут быть "1", "2-8", "5,7,9", "1,3-5,9".
     * Значения — только числовые литералы (int либо строка-число). "Nx" пропорции
     * не реализованы в этом инкрементальном фиксе.
     * defaultWidth применяется ко всем колонкам, не перечисленным в columnWidths
     * (если columns задан и > 0).
     */
    private Map<Integer, Integer> expandColumnWidths(MxlDsl dsl) {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        Map<String, Object> cw = dsl.getColumnWidths();
        Integer totalCols = dsl.getColumns();
        Integer defaultWidth = dsl.getDefaultWidth();

        if (cw != null) {
            for (Map.Entry<String, Object> e : cw.entrySet()) {
                Integer w = parseWidthValue(e.getValue());
                if (w == null) continue; // skip Nx and other unsupported values
                for (int col : parseColumnKey(e.getKey())) {
                    result.put(col, w);
                }
            }
        }

        // Fill remaining columns with defaultWidth (if both columns and defaultWidth set
        // AND there is something to fill, i.e. result is non-empty — otherwise the
        // existing trailing <format><width>...</width></format> block already covers it).
        if (defaultWidth != null && totalCols != null && totalCols > 0 && !result.isEmpty()) {
            for (int i = 1; i <= totalCols; i++) {
                result.putIfAbsent(i, defaultWidth);
            }
        }

        return result;
    }

    /**
     * Преобразовать ключ columnWidths в список 1-based индексов колонок.
     * Поддержка: "1", "2-8", "5,7,9", "1,3-5,9".
     */
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
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        out.add(i);
                    }
                } else {
                    out.add(Integer.parseInt(part));
                }
            } catch (NumberFormatException ignored) {
                // Skip malformed entries silently — DSL parser already accepted the value.
            }
        }
        return out;
    }

    /**
     * Преобразовать значение ширины в Integer. Допустимы Integer, Number,
     * строка-число. "Nx" пропорции возвращают null (не поддержаны в этом фиксе).
     */
    private Integer parseWidthValue(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            String s = ((String) v).trim();
            if (s.isEmpty()) return null;
            // "Nx" — пропорции, не поддерживаем в инкрементальном фиксе
            if (s.endsWith("x") || s.endsWith("X")) return null;
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
    
    /**
     * Записать настройки языка.
     */
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
    
    /**
     * Записать область.
     * 
     * @return новый индекс строки
     */
    private int writeArea(MxlDsl.Area area, int startRowIndex) throws XMLStreamException {
        if (area.getRows() == null || area.getRows().isEmpty()) {
            return startRowIndex;
        }
        
        int currentRowIndex = startRowIndex;
        
        for (MxlDsl.Row row : area.getRows()) {
            // Обработка empty (пустые строки)
            if (row.getEmpty() != null && row.getEmpty() > 0) {
                currentRowIndex += row.getEmpty();
                continue;
            }
            
            // Пропустить пустые строки без ячеек
            if (row.getCells() == null || row.getCells().isEmpty()) {
                currentRowIndex++;
                continue;
            }
            
            // Записать строку
            writer.writeCharacters("\t");
            writer.writeStartElement("rowsItem");
            writer.writeCharacters("\n");
            indentLevel = 2;
            
            writeElement("index", String.valueOf(currentRowIndex));
            
            startElement("row");
            
            // Записать ячейки (с пробросом rowStyle для наследования стилей)
            for (MxlDsl.Cell cell : row.getCells()) {
                writeCell(cell, row.getRowStyle());
            }
            
            endElement(); // row
            
            indentLevel = 1;
            writer.writeCharacters("\t");
            writer.writeEndElement(); // rowsItem
            writer.writeCharacters("\n");
            
            currentRowIndex++;
        }
        
        return currentRowIndex;
    }
    
    /**
     * Записать ячейку.
     *
     * @param cell     описание ячейки
     * @param rowStyle стиль строки (наследуется, если у ячейки нет своего style)
     */
    private void writeCell(MxlDsl.Cell cell, String rowStyle) throws XMLStreamException {
        startElement("c");

        // Индекс колонки (0-based в XML)
        if (cell.getCol() != null) {
            writeElement("i", String.valueOf(cell.getCol() - 1));
        }

        startElement("c");

        // Format index (ссылка на стиль). Приоритет: cell.style > rowStyle > "0".
        // Cell.style всегда перебивает rowStyle (более специфичный уровень).
        if (cell.getStyle() != null) {
            writeElement("f", cell.getStyle());
        } else if (rowStyle != null && !rowStyle.isEmpty()) {
            writeElement("f", rowStyle);
        } else {
            writeElement("f", "0");
        }
        
        // Содержимое ячейки
        if (cell.getText() != null) {
            writeCellText(cell.getText());
        } else if (cell.getParam() != null) {
            writeCellParameter(cell.getParam());
        } else if (cell.getTemplate() != null) {
            writeCellTemplate(cell.getTemplate());
        }
        
        // Span (объединение по горизонтали)
        if (cell.getSpan() != null && cell.getSpan() > 1) {
            writeElement("merge", String.valueOf(cell.getSpan() - 1));
        }
        
        // Rowspan (объединение по вертикали)
        if (cell.getRowspan() != null && cell.getRowspan() > 1) {
            writeElement("rowMerge", String.valueOf(cell.getRowspan() - 1));
        }

        // Detail parameter (string — drill-down reference)
        if (cell.getDetail() != null && !cell.getDetail().isEmpty()) {
            writeElement("detailParameter", cell.getDetail());
        }

        // Detail-record marker (A.6) — boolean flag
        if (cell.getDetailRecord() != null && cell.getDetailRecord()) {
            writeElement("detailRecord", "true");
        }

        endElement(); // c (inner)
        endElement(); // c (outer)
    }

    /**
     * Записать <pageSetup> блок для документа.
     * Поддержка: "A4-landscape" (780), "A4-portrait" (540), либо число.
     */
    private void writePageSetup(String page) throws XMLStreamException {
        String orientation;
        Integer width;
        if ("A4-landscape".equalsIgnoreCase(page)) {
            orientation = "Landscape";
            width = 780;
        } else if ("A4-portrait".equalsIgnoreCase(page)) {
            orientation = "Portrait";
            width = 540;
        } else {
            // Try parsing as integer width
            try {
                width = Integer.parseInt(page.trim());
                orientation = width >= 600 ? "Landscape" : "Portrait";
            } catch (NumberFormatException e) {
                // Unknown page format — emit raw page value, no width
                orientation = page;
                width = null;
            }
        }

        startElement("pageSetup");
        writeElement("orientation", orientation);
        if (width != null) {
            writeElement("pageWidth", String.valueOf(width));
            writeElement("paperKind", "A4");
        }
        endElement(); // pageSetup
    }

    /**
     * Получить максимальную допустимую ширину страницы для заданного `page`.
     * Возвращает null если page не задан или не распознан.
     */
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
    
    /**
     * Записать текстовое содержимое ячейки.
     */
    private void writeCellText(String text) throws XMLStreamException {
        startElement("tl");
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        endElement(); // tl
    }
    
    /**
     * Записать параметр ячейки.
     */
    private void writeCellParameter(String param) throws XMLStreamException {
        startElement("parameter");
        writeElement("v8:content", param);
        endElement(); // parameter
    }
    
    /**
     * Записать шаблон ячейки.
     */
    private void writeCellTemplate(String template) throws XMLStreamException {
        startElement("templateText");
        writeElement("v8:content", template);
        endElement(); // templateText
    }
    
    /**
     * Вычислить общее количество строк.
     */
    private int calculateTotalRows(MxlDsl dsl) {
        if (dsl.getAreas() == null) {
            return 0;
        }
        
        int total = 0;
        for (MxlDsl.Area area : dsl.getAreas()) {
            if (area.getRows() != null) {
                for (MxlDsl.Row row : area.getRows()) {
                    if (row.getEmpty() != null && row.getEmpty() > 0) {
                        total += row.getEmpty();
                    } else {
                        total++;
                    }
                }
            }
        }
        
        return total;
    }
    
    /**
     * Записать шрифты.
     */
    private void writeFonts(Map<String, MxlDsl.Font> fonts) throws XMLStreamException {
        for (Map.Entry<String, MxlDsl.Font> entry : fonts.entrySet()) {
            String name = entry.getKey();
            MxlDsl.Font font = entry.getValue();
            
            startElement("font");
            writeElement("id", name);
            
            startElement("font");
            
            if (font.getFace() != null) {
                writeElement("face", font.getFace());
            }
            
            if (font.getSize() != null) {
                writeElement("height", String.valueOf(font.getSize()));
            }
            
            if (font.getBold() != null && font.getBold()) {
                writeElement("bold", "true");
            }
            
            if (font.getItalic() != null && font.getItalic()) {
                writeElement("italic", "true");
            }
            
            if (font.getUnderline() != null && font.getUnderline()) {
                writeElement("underline", "true");
            }
            
            if (font.getStrikeout() != null && font.getStrikeout()) {
                writeElement("strikeout", "true");
            }
            
            endElement(); // font (inner)
            endElement(); // font (outer)
        }
    }
    
    /**
     * Записать стили.
     */
    private void writeStyles(Map<String, MxlDsl.Style> styles, Map<String, MxlDsl.Font> fonts) throws XMLStreamException {
        for (Map.Entry<String, MxlDsl.Style> entry : styles.entrySet()) {
            String name = entry.getKey();
            MxlDsl.Style style = entry.getValue();
            
            startElement("format");
            writeElement("id", name);
            
            // Font reference
            if (style.getFont() != null) {
                writeElement("font", style.getFont());
            }
            
            // Alignment
            if (style.getAlign() != null) {
                String align = style.getAlign();
                if ("left".equalsIgnoreCase(align)) {
                    writeElement("horizontalAlignment", "Left");
                } else if ("center".equalsIgnoreCase(align)) {
                    writeElement("horizontalAlignment", "Center");
                } else if ("right".equalsIgnoreCase(align)) {
                    writeElement("horizontalAlignment", "Right");
                }
            }
            
            if (style.getValign() != null) {
                String valign = style.getValign();
                if ("top".equalsIgnoreCase(valign)) {
                    writeElement("verticalAlignment", "Top");
                } else if ("center".equalsIgnoreCase(valign)) {
                    writeElement("verticalAlignment", "Center");
                } else if ("bottom".equalsIgnoreCase(valign)) {
                    writeElement("verticalAlignment", "Bottom");
                }
            }
            
            // Border
            if (style.getBorder() != null) {
                writeBorder(style.getBorder(), style.getBorderWidth());
            }
            
            // Text wrap
            if (style.getWrap() != null && style.getWrap()) {
                writeElement("textPlacement", "Wrap");
            }
            
            // Format string
            if (style.getFormat() != null) {
                writeElement("format", style.getFormat());
            }
            
            endElement(); // format
        }
    }
    
    /**
     * Записать рамку.
     */
    private void writeBorder(String border, String borderWidth) throws XMLStreamException {
        startElement("border");
        
        String width = "1"; // thin
        if ("thick".equalsIgnoreCase(borderWidth)) {
            width = "2";
        }
        
        // Парсим стороны рамки
        boolean top = false, bottom = false, left = false, right = false;
        
        if ("all".equalsIgnoreCase(border)) {
            top = bottom = left = right = true;
        } else if ("none".equalsIgnoreCase(border)) {
            // Ничего не делаем
        } else {
            String[] sides = border.split(",");
            for (String side : sides) {
                side = side.trim().toLowerCase();
                if ("top".equals(side)) top = true;
                else if ("bottom".equals(side)) bottom = true;
                else if ("left".equals(side)) left = true;
                else if ("right".equals(side)) right = true;
            }
        }
        
        if (top) {
            startElement("top");
            writeElement("style", "Solid");
            writeElement("width", width);
            endElement(); // top
        }
        
        if (bottom) {
            startElement("bottom");
            writeElement("style", "Solid");
            writeElement("width", width);
            endElement(); // bottom
        }
        
        if (left) {
            startElement("left");
            writeElement("style", "Solid");
            writeElement("width", width);
            endElement(); // left
        }
        
        if (right) {
            startElement("right");
            writeElement("style", "Solid");
            writeElement("width", width);
            endElement(); // right
        }
        
        endElement(); // border
    }
}

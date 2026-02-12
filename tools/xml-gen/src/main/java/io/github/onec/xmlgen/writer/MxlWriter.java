package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
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
        
        // Columns
        startElement("columns");
        writeElement("size", String.valueOf(dsl.getColumns() != null ? dsl.getColumns() : 1));
        endElement(); // columns
        
        // Rows (areas)
        if (dsl.getAreas() != null && !dsl.getAreas().isEmpty()) {
            int rowIndex = 0;
            for (MxlDsl.Area area : dsl.getAreas()) {
                rowIndex = writeArea(area, rowIndex);
            }
        }
        
        // Template mode
        writeElement("templateMode", "true");
        
        // Default format index
        writeElement("defaultFormatIndex", "1");
        
        // Height (total rows)
        int totalRows = calculateTotalRows(dsl);
        writeElement("height", String.valueOf(totalRows));
        writeElement("vgRows", String.valueOf(totalRows));
        
        // Format (column widths)
        if (dsl.getDefaultWidth() != null) {
            startElement("format");
            writeElement("width", String.valueOf(dsl.getDefaultWidth()));
            endElement(); // format
        }
        
        writer.writeEndElement(); // document
        close();
        
        System.out.println("Created MXL template: " + outputPath);
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
            
            // Записать ячейки
            for (MxlDsl.Cell cell : row.getCells()) {
                writeCell(cell);
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
     */
    private void writeCell(MxlDsl.Cell cell) throws XMLStreamException {
        startElement("c");
        
        // Индекс колонки (0-based в XML)
        if (cell.getCol() != null) {
            writeElement("i", String.valueOf(cell.getCol() - 1));
        }
        
        startElement("c");
        
        // Format index (ссылка на стиль или 0 = default)
        if (cell.getStyle() != null) {
            writeElement("f", cell.getStyle());
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
        
        endElement(); // c (inner)
        endElement(); // c (outer)
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

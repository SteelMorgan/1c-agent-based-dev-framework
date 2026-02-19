package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для табличного документа 1С (SpreadsheetDocument).
 */
@Value
public class MxlDsl {
    
    /**
     * Количество колонок.
     */
    Integer columns;
    
    /**
     * Ширина колонок по умолчанию.
     */
    Integer defaultWidth;
    
    /**
     * Ширины колонок: {"1": 15, "2-8": 40, "9-10": 50}.
     */
    Map<String, Object> columnWidths;
    
    /**
     * Именованные шрифты.
     */
    Map<String, Font> fonts;
    
    /**
     * Именованные стили.
     */
    Map<String, Style> styles;
    
    /**
     * Именованные области.
     */
    List<Area> areas;
    
    @JsonCreator
    public MxlDsl(
            @JsonProperty("columns") Integer columns,
            @JsonProperty("defaultWidth") Integer defaultWidth,
            @JsonProperty("columnWidths") Map<String, Object> columnWidths,
            @JsonProperty("fonts") Map<String, Font> fonts,
            @JsonProperty("styles") Map<String, Style> styles,
            @JsonProperty("areas") List<Area> areas) {
        this.columns = columns;
        this.defaultWidth = defaultWidth;
        this.columnWidths = columnWidths;
        this.fonts = fonts;
        this.styles = styles;
        this.areas = areas;
    }
    
    /**
     * Шрифт.
     */
    @Value
    public static class Font {
        String face;
        Integer size;
        Boolean bold;
        Boolean italic;
        Boolean underline;
        Boolean strikeout;
        
        @JsonCreator
        public Font(
                @JsonProperty("face") String face,
                @JsonProperty("size") Integer size,
                @JsonProperty("bold") Boolean bold,
                @JsonProperty("italic") Boolean italic,
                @JsonProperty("underline") Boolean underline,
                @JsonProperty("strikeout") Boolean strikeout) {
            this.face = face;
            this.size = size;
            this.bold = bold;
            this.italic = italic;
            this.underline = underline;
            this.strikeout = strikeout;
        }
    }
    
    /**
     * Стиль.
     */
    @Value
    public static class Style {
        String font;
        String align;
        String valign;
        String border;
        String borderWidth;
        Boolean wrap;
        String format;
        
        @JsonCreator
        public Style(
                @JsonProperty("font") String font,
                @JsonProperty("align") String align,
                @JsonProperty("valign") String valign,
                @JsonProperty("border") String border,
                @JsonProperty("borderWidth") String borderWidth,
                @JsonProperty("wrap") Boolean wrap,
                @JsonProperty("format") String format) {
            this.font = font;
            this.align = align;
            this.valign = valign;
            this.border = border;
            this.borderWidth = borderWidth;
            this.wrap = wrap;
            this.format = format;
        }
    }
    
    /**
     * Область.
     */
    @Value
    public static class Area {
        String name;
        List<Row> rows;
        
        @JsonCreator
        public Area(
                @JsonProperty("name") String name,
                @JsonProperty("rows") List<Row> rows) {
            this.name = name;
            this.rows = rows;
        }
    }
    
    /**
     * Строка.
     */
    @Value
    public static class Row {
        Integer height;
        String rowStyle;
        List<Cell> cells;
        Integer empty;
        
        @JsonCreator
        public Row(
                @JsonProperty("height") Integer height,
                @JsonProperty("rowStyle") String rowStyle,
                @JsonProperty("cells") List<Cell> cells,
                @JsonProperty("empty") Integer empty) {
            this.height = height;
            this.rowStyle = rowStyle;
            this.cells = cells;
            this.empty = empty;
        }
    }
    
    /**
     * Ячейка.
     */
    @Value
    public static class Cell {
        Integer col;
        Integer span;
        Integer rowspan;
        String style;
        String param;
        String detail;
        String text;
        String template;
        
        @JsonCreator
        public Cell(
                @JsonProperty("col") Integer col,
                @JsonProperty("span") Integer span,
                @JsonProperty("rowspan") Integer rowspan,
                @JsonProperty("style") String style,
                @JsonProperty("param") String param,
                @JsonProperty("detail") String detail,
                @JsonProperty("text") String text,
                @JsonProperty("template") String template) {
            this.col = col;
            this.span = span;
            this.rowspan = rowspan;
            this.style = style;
            this.param = param;
            this.detail = detail;
            this.text = text;
            this.template = template;
        }
    }
}

package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * JSON DSL для табличного документа 1С (SpreadsheetDocument).
 */
@Getter
@ToString
@EqualsAndHashCode
public class MxlDsl {

    /**
     * Количество колонок.
     */
    private final Integer columns;

    /**
     * Ширина колонок по умолчанию.
     */
    private final Integer defaultWidth;

    /**
     * Ширины колонок: {"1": 15, "2-8": 40, "9-10": 50}.
     */
    private final Map<String, Object> columnWidths;

    /**
     * Именованные шрифты.
     */
    private final Map<String, Font> fonts;

    /**
     * Именованные стили.
     */
    private final Map<String, Style> styles;

    /**
     * Именованные области.
     */
    private final List<Area> areas;

    /**
     * Формат страницы: "A4-landscape" (780), "A4-portrait" (540), либо число
     * как абсолютная ширина страницы. Опционально — для генерации PageSetup
     * и валидации суммы ширин колонок.
     */
    private final String page;

    /**
     * Обратно-совместимый конструктор (без page).
     */
    public MxlDsl(
            Integer columns,
            Integer defaultWidth,
            Map<String, Object> columnWidths,
            Map<String, Font> fonts,
            Map<String, Style> styles,
            List<Area> areas) {
        this(columns, defaultWidth, columnWidths, fonts, styles, areas, null);
    }

    @JsonCreator
    public MxlDsl(
            @JsonProperty("columns") Integer columns,
            @JsonProperty("defaultWidth") Integer defaultWidth,
            @JsonProperty("columnWidths") Map<String, Object> columnWidths,
            @JsonProperty("fonts") Map<String, Font> fonts,
            @JsonProperty("styles") Map<String, Style> styles,
            @JsonProperty("areas") List<Area> areas,
            @JsonProperty("page") String page) {
        this.columns = columns;
        this.defaultWidth = defaultWidth;
        this.columnWidths = columnWidths;
        this.fonts = fonts;
        this.styles = styles;
        this.areas = areas;
        this.page = page;
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
    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Cell {
        private final Integer col;
        private final Integer span;
        private final Integer rowspan;
        private final String style;
        private final String param;
        /** Имя параметра расшифровки (для drill-down). */
        private final String detail;
        private final String text;
        private final String template;
        /**
         * Маркер «детальная запись» в отчётах. Default null/false.
         * Отдельное поле от String detail (расшифровка) — чтобы не путать с расшифровкой.
         */
        private final Boolean detailRecord;

        /**
         * Обратно-совместимый конструктор (без detailRecord).
         */
        public Cell(
                Integer col,
                Integer span,
                Integer rowspan,
                String style,
                String param,
                String detail,
                String text,
                String template) {
            this(col, span, rowspan, style, param, detail, text, template, null);
        }

        @JsonCreator
        public Cell(
                @JsonProperty("col") Integer col,
                @JsonProperty("span") Integer span,
                @JsonProperty("rowspan") Integer rowspan,
                @JsonProperty("style") String style,
                @JsonProperty("param") String param,
                @JsonProperty("detail") String detail,
                @JsonProperty("text") String text,
                @JsonProperty("template") String template,
                @JsonProperty("detailRecord") Boolean detailRecord) {
            this.col = col;
            this.span = span;
            this.rowspan = rowspan;
            this.style = style;
            this.param = param;
            this.detail = detail;
            this.text = text;
            this.template = template;
            this.detailRecord = detailRecord;
        }
    }
}

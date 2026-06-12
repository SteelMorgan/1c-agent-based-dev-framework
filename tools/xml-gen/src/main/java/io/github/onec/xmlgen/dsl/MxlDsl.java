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
     * TASK-171 (R9): рисунки (Picture/Drawing). Опционально, повторяются.
     * Канон: document-level &lt;drawing&gt; после &lt;rowsItem&gt; (1c-spreadsheet-spec.md §«Рисунки»).
     */
    private final List<Drawing> drawings;

    /**
     * TASK-171: палитра ресурсов картинок (&lt;picture&gt; с base64 data или ref).
     * Индекс совпадает с pictureIndex рисунка. Опционально.
     */
    private final List<Picture> pictures;

    /**
     * TASK-171: вертикальные разъединения (&lt;verticalUnmerge&gt;). Канон —
     * после &lt;merge&gt; (1c-spreadsheet-spec.md §«Отмена объединений»). В Квитанции 38 шт.
     */
    private final List<Unmerge> verticalUnmerges;

    /**
     * TASK-171: объединения колонок на уровне всего документа (&lt;merge&gt; с &lt;r&gt;-1).
     * В area-модели это нельзя выразить через cell.span (нет привязки к строке),
     * поэтому держим отдельным списком. В Квитанции ~2 шт.
     */
    private final List<ColumnMerge> columnMerges;

    /**
     * Lossless payload for decompile→compile round-trip of canon Designer MXL.
     * <p>
     * The declarative DSL fields above remain the editable projection. This
     * field preserves canonical sections that the projection cannot yet express
     * (comments, non-row named areas, print areas, and other Designer trivia)
     * so that compiling a decompiled artifact never silently drops data.
     */
    private final String losslessXmlBase64;

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

    /**
     * Обратно-совместимый конструктор (с page, без drawings/pictures/unmerge/columnMerges).
     */
    public MxlDsl(
            Integer columns,
            Integer defaultWidth,
            Map<String, Object> columnWidths,
            Map<String, Font> fonts,
            Map<String, Style> styles,
            List<Area> areas,
            String page) {
        this(columns, defaultWidth, columnWidths, fonts, styles, areas, page, null, null, null, null);
    }

    public MxlDsl(
            @JsonProperty("columns") Integer columns,
            @JsonProperty("defaultWidth") Integer defaultWidth,
            @JsonProperty("columnWidths") Map<String, Object> columnWidths,
            @JsonProperty("fonts") Map<String, Font> fonts,
            @JsonProperty("styles") Map<String, Style> styles,
            @JsonProperty("areas") List<Area> areas,
            @JsonProperty("page") String page,
            @JsonProperty("drawings") List<Drawing> drawings,
            @JsonProperty("pictures") List<Picture> pictures,
            @JsonProperty("verticalUnmerges") List<Unmerge> verticalUnmerges,
            @JsonProperty("columnMerges") List<ColumnMerge> columnMerges) {
        this(columns, defaultWidth, columnWidths, fonts, styles, areas, page, drawings, pictures,
                verticalUnmerges, columnMerges, null);
    }

    @JsonCreator
    public MxlDsl(
            @JsonProperty("columns") Integer columns,
            @JsonProperty("defaultWidth") Integer defaultWidth,
            @JsonProperty("columnWidths") Map<String, Object> columnWidths,
            @JsonProperty("fonts") Map<String, Font> fonts,
            @JsonProperty("styles") Map<String, Style> styles,
            @JsonProperty("areas") List<Area> areas,
            @JsonProperty("page") String page,
            @JsonProperty("drawings") List<Drawing> drawings,
            @JsonProperty("pictures") List<Picture> pictures,
            @JsonProperty("verticalUnmerges") List<Unmerge> verticalUnmerges,
            @JsonProperty("columnMerges") List<ColumnMerge> columnMerges,
            @JsonProperty("losslessXmlBase64") String losslessXmlBase64) {
        this.columns = columns;
        this.defaultWidth = defaultWidth;
        this.columnWidths = columnWidths;
        this.fonts = fonts;
        this.styles = styles;
        this.areas = areas;
        this.page = page;
        this.drawings = drawings;
        this.pictures = pictures;
        this.verticalUnmerges = verticalUnmerges;
        this.columnMerges = columnMerges;
        this.losslessXmlBase64 = losslessXmlBase64;
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
        /**
         * TASK-171 (R5): цвет текста. Канон: "#RRGGBB" (hex) ИЛИ "style:ИмяЦвета"
         * (ссылка на стиль платформы, напр. "style:FormTextColor"). Опционально.
         */
        String textColor;
        /** TASK-171 (R5): цвет фона ячейки. Те же два формата. Опционально. */
        String backColor;
        /** TASK-171 (R5): цвет границы. Те же два формата. Опционально. */
        String borderColor;

        /** Обратно-совместимый конструктор (без цветов). */
        public Style(
                String font,
                String align,
                String valign,
                String border,
                String borderWidth,
                Boolean wrap,
                String format) {
            this(font, align, valign, border, borderWidth, wrap, format, null, null, null);
        }

        @JsonCreator
        public Style(
                @JsonProperty("font") String font,
                @JsonProperty("align") String align,
                @JsonProperty("valign") String valign,
                @JsonProperty("border") String border,
                @JsonProperty("borderWidth") String borderWidth,
                @JsonProperty("wrap") Boolean wrap,
                @JsonProperty("format") String format,
                @JsonProperty("textColor") String textColor,
                @JsonProperty("backColor") String backColor,
                @JsonProperty("borderColor") String borderColor) {
            this.font = font;
            this.align = align;
            this.valign = valign;
            this.border = border;
            this.borderWidth = borderWidth;
            this.wrap = wrap;
            this.format = format;
            this.textColor = textColor;
            this.backColor = backColor;
            this.borderColor = borderColor;
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

    /**
     * TASK-171 (R9): рисунок (drawing). Поля — позиционирование строка/колонка +
     * пиксельные смещения, как в 1c-spreadsheet-spec.md §«Рисунки». pictureIndex
     * ссылается на запись из {@link #pictures}. drawingType обычно "Picture".
     */
    @Getter
    @ToString
    @EqualsAndHashCode
    public static class Drawing {
        private final String drawingType;
        private final Integer id;
        private final String name;
        private final Integer formatIndex;
        private final Integer beginRow;
        private final Integer beginRowOffset;
        private final Integer endRow;
        private final Integer endRowOffset;
        private final Integer beginColumn;
        private final Integer beginColumnOffset;
        private final Integer endColumn;
        private final Integer endColumnOffset;
        private final Boolean autoSize;
        private final String pictureSize;
        private final Integer zOrder;
        private final Integer pictureIndex;

        @JsonCreator
        public Drawing(
                @JsonProperty("drawingType") String drawingType,
                @JsonProperty("id") Integer id,
                @JsonProperty("name") String name,
                @JsonProperty("formatIndex") Integer formatIndex,
                @JsonProperty("beginRow") Integer beginRow,
                @JsonProperty("beginRowOffset") Integer beginRowOffset,
                @JsonProperty("endRow") Integer endRow,
                @JsonProperty("endRowOffset") Integer endRowOffset,
                @JsonProperty("beginColumn") Integer beginColumn,
                @JsonProperty("beginColumnOffset") Integer beginColumnOffset,
                @JsonProperty("endColumn") Integer endColumn,
                @JsonProperty("endColumnOffset") Integer endColumnOffset,
                @JsonProperty("autoSize") Boolean autoSize,
                @JsonProperty("pictureSize") String pictureSize,
                @JsonProperty("zOrder") Integer zOrder,
                @JsonProperty("pictureIndex") Integer pictureIndex) {
            this.drawingType = drawingType;
            this.id = id;
            this.name = name;
            this.formatIndex = formatIndex;
            this.beginRow = beginRow;
            this.beginRowOffset = beginRowOffset;
            this.endRow = endRow;
            this.endRowOffset = endRowOffset;
            this.beginColumn = beginColumn;
            this.beginColumnOffset = beginColumnOffset;
            this.endColumn = endColumn;
            this.endColumnOffset = endColumnOffset;
            this.autoSize = autoSize;
            this.pictureSize = pictureSize;
            this.zOrder = zOrder;
            this.pictureIndex = pictureIndex;
        }
    }

    /**
     * TASK-171: ресурс картинки (палитра &lt;picture&gt;). Либо base64 data в теле,
     * либо ref на предопределённую картинку платформы ("v8ui:Штрихкод").
     */
    @Value
    public static class Picture {
        Integer index;
        /** base64-данные картинки (содержимое &lt;picture&gt;), либо null. */
        String data;
        /** Ссылка на предопределённую картинку (&lt;picture ref="..."/&gt;), либо null. */
        String ref;
        /**
         * Атрибут "t" внутреннего &lt;picture&gt; (transparent-флаг реальных макетов,
         * напр. t="false"). Сохраняется для байт-точного round-trip. Опционально.
         */
        String t;

        /** Обратно-совместимый конструктор (без t). */
        public Picture(Integer index, String data, String ref) {
            this(index, data, ref, null);
        }

        @JsonCreator
        public Picture(
                @JsonProperty("index") Integer index,
                @JsonProperty("data") String data,
                @JsonProperty("ref") String ref,
                @JsonProperty("t") String t) {
            this.index = index;
            this.data = data;
            this.ref = ref;
            this.t = t;
        }
    }

    /**
     * TASK-171: вертикальное разъединение (&lt;verticalUnmerge&gt;): строка r,
     * колонка c, доп. колонок w. 0-based, как в каноне.
     */
    @Value
    public static class Unmerge {
        Integer r;
        Integer c;
        Integer w;

        @JsonCreator
        public Unmerge(
                @JsonProperty("r") Integer r,
                @JsonProperty("c") Integer c,
                @JsonProperty("w") Integer w) {
            this.r = r;
            this.c = c;
            this.w = w;
        }
    }

    /**
     * TASK-171: объединение колонок всего документа (&lt;merge&gt; с &lt;r&gt;-1&lt;/r&gt;).
     * c — колонка (0-based), w — доп. колонок, h — доп. строк (обычно 0).
     */
    @Value
    public static class ColumnMerge {
        Integer c;
        Integer w;
        Integer h;

        @JsonCreator
        public ColumnMerge(
                @JsonProperty("c") Integer c,
                @JsonProperty("w") Integer w,
                @JsonProperty("h") Integer h) {
            this.c = c;
            this.w = w;
            this.h = h;
        }
    }
}

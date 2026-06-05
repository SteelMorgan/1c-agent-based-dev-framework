package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты MxlDecompiler: декомпиляция Template.xml → JSON DSL.
 *
 * TASK-171: переписаны под КАНОНИЧЕСКУЮ модель платформы. Стиль ячейки задаётся
 * числовым индексом &lt;f&gt;N (1-based в палитру &lt;format&gt;), бордюры — скалярными
 * индексами &lt;leftBorder&gt; в палитру &lt;line&gt;, объединения — document-level &lt;merge&gt;.
 * Декомпилятор именует стили/шрифты по содержимому формата (канон Широкова).
 */
class MxlDecompilerTest {

    private final MxlDecompiler decompiler = new MxlDecompiler();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    @Test
    void autoNamesStyleFromReferencedFormat() throws Exception {
        // Формат с выравниванием Center, на который ссылается ячейка <f>1</f>.
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f>" + tl("Шапка") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<format>\n" +
                "\t\t<horizontalAlignment>Center</horizontalAlignment>\n" +
                "\t</format>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        // Стиль назван "center" (по содержимому формата) и применён к ячейке.
        assertThat(json).contains("\"center\"");
        assertThat(json).contains("\"align\" : \"center\"");
    }

    @Test
    void readsScalarBorderIntoStyle() throws Exception {
        // Палитра линий (индекс 0 = тонкая) + формат со всеми 4 границами.
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f>" + tl("X") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<line width=\"1\" gap=\"false\">" +
                "<v8ui:style xsi:type=\"v8ui:SpreadsheetDocumentCellLineType\">Solid</v8ui:style></line>\n" +
                "\t<format>\n" +
                "\t\t<leftBorder>0</leftBorder>\n" +
                "\t\t<topBorder>0</topBorder>\n" +
                "\t\t<rightBorder>0</rightBorder>\n" +
                "\t\t<bottomBorder>0</bottomBorder>\n" +
                "\t</format>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        // Все 4 границы → "border": "all".
        assertThat(json).contains("\"border\" : \"all\"");
    }

    @Test
    void readsDocumentLevelMergeIntoSpan() throws Exception {
        // document-level merge на строке 0, колонке 0, w=2 → span=3 у ячейки.
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" + tl("Заголовок") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<merge><r>0</r><c>0</c><w>2</w></merge>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"span\" : 3");
    }

    @Test
    void readsColumnWidthsFromColumnsItem() throws Exception {
        // columns size=3, columnsItem index 0 → formatIndex 1 (width 15);
        // defaultFormatIndex 2 (width 10) → колонка 0 нестандартная.
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" + tl("X") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<defaultFormatIndex>2</defaultFormatIndex>\n" +
                "\t<height>1</height>\n" +
                "\t<format><width>15</width></format>\n" +
                "\t<format><width>10</width></format>\n");
        // columnsItem нужно внутри <columns>; перепишем заголовок columns с item.
        String xml = Files.readString(file, StandardCharsets.UTF_8)
                .replace("<columns><size>3</size></columns>",
                        "<columns><size>3</size><columnsItem><index>0</index>" +
                        "<column><formatIndex>1</formatIndex></column></columnsItem></columns>");
        Files.writeString(file, xml, StandardCharsets.UTF_8);

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("columnWidths");
        assertThat(json).contains("\"1\" : 15"); // 0-based col 0 → 1-based "1"
    }

    @Test
    void readsCanonicalFontPalette() throws Exception {
        // Числовая палитра шрифтов: индекс 0 = default (Arial 10), индекс 1 = Arial 12 bold.
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f>" + tl("Заголовок") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<font faceName=\"Arial\" height=\"10\" bold=\"false\" kind=\"Absolute\" scale=\"100\"/>\n" +
                "\t<font faceName=\"Arial\" height=\"12\" bold=\"true\" kind=\"Absolute\" scale=\"100\"/>\n" +
                "\t<format>\n" +
                "\t\t<font>1</font>\n" +
                "\t</format>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        // Шрифт 1 (bold, больше дефолта) → именуется "header" и появляется в fonts.
        assertThat(json).contains("\"fonts\"");
        assertThat(json).contains("\"bold\" : true");
    }

    /**
     * TASK-171 (R5): цвета формата (hex + style-ref) попадают в style.def
     * (textColor/backColor/borderColor) — round-trip сохранение значения.
     */
    @Test
    void readsColorsIntoStyle() throws Exception {
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>1</f>" + tl("Цвет") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<format>\n" +
                "\t\t<borderColor>style:FormTextColor</borderColor>\n" +
                "\t\t<textColor>#000080</textColor>\n" +
                "\t\t<backColor>#BBEEC7</backColor>\n" +
                "\t</format>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"textColor\" : \"#000080\"");
        assertThat(json).contains("\"backColor\" : \"#BBEEC7\"");
        assertThat(json).contains("\"borderColor\" : \"style:FormTextColor\"");
    }

    /**
     * TASK-171 (R9): drawing + picture-палитра читаются в drawings/pictures.
     * Имя из NamedItemDrawing привязывается к drawing по id.
     */
    @Test
    void readsDrawingsAndPictures() throws Exception {
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" + tl("X") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<drawing>\n" +
                "\t\t<drawingType>Picture</drawingType>\n" +
                "\t\t<id>1</id>\n" +
                "\t\t<beginRow>0</beginRow>\n" +
                "\t\t<pictureSize>Stretch</pictureSize>\n" +
                "\t\t<pictureIndex>1</pictureIndex>\n" +
                "\t</drawing>\n" +
                "\t<height>1</height>\n" +
                "\t<namedItem xsi:type=\"NamedItemDrawing\"><name>Лого</name><drawingID>1</drawingID></namedItem>\n" +
                "\t<picture><index>1</index><picture>iVBORw0KGgo=</picture></picture>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"drawings\"");
        assertThat(json).contains("\"drawingType\" : \"Picture\"");
        assertThat(json).contains("\"name\" : \"Лого\"");      // имя из NamedItemDrawing
        assertThat(json).contains("\"pictures\"");
        assertThat(json).contains("iVBORw0KGgo=");
    }

    /**
     * TASK-171: verticalUnmerge и document-wide column merge (&lt;r&gt;-1) выносятся
     * в отдельные секции verticalUnmerges/columnMerges (не теряются и не путаются с обычным merge).
     */
    @Test
    void readsVerticalUnmergeAndColumnMerge() throws Exception {
        Path file = writeMxl(
                "\t<rowsItem><index>0</index><row>\n" +
                "\t\t<c><i>0</i><c><f>0</f>" + tl("X") + "</c></c>\n" +
                "\t</row></rowsItem>\n" +
                "\t<height>1</height>\n" +
                "\t<merge><r>-1</r><c>14</c><w>1</w></merge>\n" +
                "\t<verticalUnmerge><r>7</r><c>14</c><w>1</w></verticalUnmerge>\n");

        Path output = tempDir.resolve("dsl.json");
        decompiler.decompile(reader.parse(file), output);

        String json = Files.readString(output, StandardCharsets.UTF_8);
        assertThat(json).contains("\"columnMerges\"");
        assertThat(json).contains("\"verticalUnmerges\"");
        // r=-1 НЕ должен превратиться в span у ячейки (он не привязан к строке/ячейке).
        assertThat(json).doesNotContain("\"span\"");
    }

    // --- helpers ---

    private String tl(String text) {
        return "<tl><v8:item><v8:lang>ru</v8:lang><v8:content>" + text + "</v8:content></v8:item></tl>";
    }

    private Path writeMxl(String body) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\" " +
                "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" " +
                "xmlns:v8ui=\"http://v8.1c.ru/8.1/data/ui\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>3</size></columns>\n" +
                body +
                "</document>\n";
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}

package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты MxlInfoPrinter: 4 типа областей (Rows / Columns / Rectangle / Drawing).
 * <p>
 * Канон Широкова, аддитивные info-режимы.
 */
class MxlInfoPrinterTest {

    private final MxlInfoPrinter printer = new MxlInfoPrinter();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    @Test
    void rowsAreaShowsRowRange() throws Exception {
        Path file = writeMxl(
                "\t<namedItem xsi:type=\"NamedItemCells\">\n" +
                "\t\t<name>Заголовок</name>\n" +
                "\t\t<area>\n" +
                "\t\t\t<type>Rows</type>\n" +
                "\t\t\t<beginRow>0</beginRow>\n" +
                "\t\t\t<endRow>2</endRow>\n" +
                "\t\t\t<beginColumn>-1</beginColumn>\n" +
                "\t\t\t<endColumn>-1</endColumn>\n" +
                "\t\t</area>\n" +
                "\t</namedItem>\n" +
                "\t<height>3</height>\n");
        String out = capture(file);
        assertThat(out).contains("Заголовок");
        assertThat(out).contains("Rows");
        assertThat(out).contains("rows 0-2");
    }

    @Test
    void columnsAreaShowsColumnRange() throws Exception {
        Path file = writeMxl(
                "\t<namedItem xsi:type=\"NamedItemCells\">\n" +
                "\t\t<name>ШиринаЭтикетки</name>\n" +
                "\t\t<area>\n" +
                "\t\t\t<type>Columns</type>\n" +
                "\t\t\t<beginRow>-1</beginRow>\n" +
                "\t\t\t<endRow>-1</endRow>\n" +
                "\t\t\t<beginColumn>0</beginColumn>\n" +
                "\t\t\t<endColumn>4</endColumn>\n" +
                "\t\t</area>\n" +
                "\t</namedItem>\n" +
                "\t<height>1</height>\n");
        String out = capture(file);
        assertThat(out).contains("ШиринаЭтикетки");
        assertThat(out).contains("Columns");
        assertThat(out).contains("cols 0-4");
    }

    @Test
    void rectangleAreaShowsBothRowsAndColumns() throws Exception {
        Path file = writeMxl(
                "\t<namedItem xsi:type=\"NamedItemCells\">\n" +
                "\t\t<name>ПрямоугольнаяОбласть</name>\n" +
                "\t\t<area>\n" +
                "\t\t\t<type>Rectangle</type>\n" +
                "\t\t\t<beginRow>2</beginRow>\n" +
                "\t\t\t<endRow>5</endRow>\n" +
                "\t\t\t<beginColumn>1</beginColumn>\n" +
                "\t\t\t<endColumn>3</endColumn>\n" +
                "\t\t</area>\n" +
                "\t</namedItem>\n" +
                "\t<height>6</height>\n");
        String out = capture(file);
        assertThat(out).contains("ПрямоугольнаяОбласть");
        assertThat(out).contains("Rectangle");
        assertThat(out).contains("rows 2-5");
        assertThat(out).contains("cols 1-3");
    }

    @Test
    void drawingNamedItemShowsDrawingId() throws Exception {
        Path file = writeMxl(
                "\t<namedItem xsi:type=\"NamedItemDrawing\">\n" +
                "\t\t<name>Логотип</name>\n" +
                "\t\t<drawingID>img-001</drawingID>\n" +
                "\t</namedItem>\n" +
                "\t<height>0</height>\n");
        String out = capture(file);
        assertThat(out).contains("Логотип");
        assertThat(out).contains("Drawing");
        assertThat(out).contains("drawingID=img-001");
    }

    // --- helpers ---

    private String capture(Path file) throws Exception {
        XmlDocument doc = reader.parse(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        printer.print(doc, false, 0, 0, new PrintStream(bos, true, StandardCharsets.UTF_8));
        return bos.toString(StandardCharsets.UTF_8);
    }

    private Path writeMxl(String body) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "\t<templateMode>true</templateMode>\n" +
                "\t<columns><size>5</size></columns>\n" +
                body +
                "</document>\n";
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}

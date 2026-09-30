package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.validator.MxlValidator;
import io.github.onec.xmlgen.validator.ValidationLevel;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты для MxlWriter.
 */
class MxlWriterTest {
    
    @TempDir
    Path tempDir;
    
    /**
     * Тест 1: Минимальный табличный документ (1 колонка, 1 строка с текстом).
     */
    @Test
    void testMinimalMxl() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, null, null, "Тест", null)
        );
        
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Область1", rows)
        );
        
        MxlDsl dsl = new MxlDsl(1, 72, null, null, null, areas);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверки
        assertThat(content).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("<document");
        assertThat(content).contains("<columns>");
        assertThat(content).contains("<size>1</size>");
        assertThat(content).contains("<rowsItem>");
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<tl>");
        assertThat(content).contains("<v8:content>Тест</v8:content>");
        assertThat(content).contains("<templateMode>true</templateMode>");
        assertThat(content).contains("<height>1</height>");
        
        // TASK-171: MXL Template.xml (Designer) — с UTF-8 BOM (как реальные _Демо макеты)
        byte[] bytes = Files.readAllBytes(outputXml);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
    }
    
    /**
     * Тест 2: Табличный документ с параметрами.
     */
    @Test
    void testMxlWithParameters() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, "Заголовок", null, null, null),
                new MxlDsl.Cell(2, null, null, null, "Значение", null, null, null)
        );
        
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Строка", rows)
        );
        
        MxlDsl dsl = new MxlDsl(2, 50, null, null, null, areas);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // TASK-171: канон — <parameter>Имя</parameter> (текст узла), без вложенного v8:content.
        assertThat(content).contains("<parameter>Заголовок</parameter>");
        assertThat(content).contains("<parameter>Значение</parameter>");
        assertThat(content).contains("<size>2</size>");
    }
    
    /**
     * Тест 3: Табличный документ с объединением ячеек (span).
     */
    @Test
    void testMxlWithSpan() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, 3, null, null, null, null, "Заголовок на 3 колонки", null)
        );
        
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Заголовок", rows)
        );
        
        MxlDsl dsl = new MxlDsl(3, 40, null, null, null, areas);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);

        // TASK-171: канон — document-level <merge> с <r>/<c>/<w>, а НЕ in-cell <merge>N.
        // span=3 → w=2, на строке 0, колонке 0.
        assertThat(content).contains("<merge>");
        assertThat(content).contains("<r>0</r>");
        assertThat(content).contains("<c>0</c>");
        assertThat(content).contains("<w>2</w>");
        assertThat(content).contains("Заголовок на 3 колонки");
    }
    
    /**
     * Тест 4: Табличный документ с несколькими областями.
     */
    @Test
    void testMxlWithMultipleAreas() throws Exception {
        // Область 1: Заголовок
        List<MxlDsl.Cell> headerCells = Arrays.asList(
                new MxlDsl.Cell(1, 2, null, null, null, null, "Отчёт", null)
        );
        List<MxlDsl.Row> headerRows = Arrays.asList(
                new MxlDsl.Row(null, null, headerCells, null)
        );
        
        // Область 2: Шапка таблицы
        List<MxlDsl.Cell> tableCells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, null, null, "№", null),
                new MxlDsl.Cell(2, null, null, null, null, null, "Наименование", null)
        );
        List<MxlDsl.Row> tableRows = Arrays.asList(
                new MxlDsl.Row(null, null, tableCells, null)
        );
        
        // Область 3: Строка данных
        List<MxlDsl.Cell> dataCells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, "НомерСтроки", null, null, null),
                new MxlDsl.Cell(2, null, null, null, "Товар", null, null, null)
        );
        List<MxlDsl.Row> dataRows = Arrays.asList(
                new MxlDsl.Row(null, null, dataCells, null)
        );
        
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Заголовок", headerRows),
                new MxlDsl.Area("ШапкаТаблицы", tableRows),
                new MxlDsl.Area("Строка", dataRows)
        );
        
        MxlDsl dsl = new MxlDsl(2, 50, null, null, null, areas);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки областей
        assertThat(content).contains("<index>0</index>"); // Заголовок
        assertThat(content).contains("<index>1</index>"); // ШапкаТаблицы
        assertThat(content).contains("<index>2</index>"); // Строка
        assertThat(content).contains("<height>3</height>"); // 3 строки всего
        assertThat(content).contains("Отчёт");
        assertThat(content).contains("Наименование");
        assertThat(content).contains("<parameter>");
    }
    
    /**
     * Тест 5: MXL с шрифтами и стилями.
     */
    @Test
    void testMxlWithFontsAndStyles() throws Exception {
        String json = """
                {
                  "columns": 3,
                  "fonts": {
                    "header": {
                      "face": "Arial",
                      "size": 12,
                      "bold": true
                    },
                    "normal": {
                      "face": "Arial",
                      "size": 10
                    }
                  },
                  "styles": {
                    "headerStyle": {
                      "font": "header",
                      "align": "center",
                      "valign": "center",
                      "border": "all",
                      "borderWidth": "thick"
                    },
                    "dataStyle": {
                      "font": "normal",
                      "align": "left",
                      "border": "top,bottom",
                      "wrap": true
                    }
                  },
                  "areas": [
                    {
                      "name": "Заголовок",
                      "rows": [
                        {
                          "cells": [
                            {"col": 1, "span": 3, "text": "Отчёт", "style": "headerStyle"}
                          ]
                        }
                      ]
                    },
                    {
                      "name": "Данные",
                      "rows": [
                        {
                          "cells": [
                            {"col": 1, "text": "Значение 1", "style": "dataStyle"},
                            {"col": 2, "text": "Значение 2", "style": "dataStyle"},
                            {"col": 3, "text": "Значение 3", "style": "dataStyle"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        
        ObjectMapper mapper = new ObjectMapper();
        MxlDsl dsl = mapper.readValue(json, MxlDsl.class);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // TASK-171: канон — числовая палитра шрифтов (атрибуты faceName/height/bold),
        // НЕ именованные <id>/<face>.
        assertThat(content).contains("faceName=\"Arial\"");
        assertThat(content).contains("height=\"12\"");
        assertThat(content).contains("bold=\"true\"");
        assertThat(content).contains("height=\"10\"");

        // TASK-171: канон — палитра линий + скалярные border-индексы в формате.
        assertThat(content).contains("<line width=\"2\" gap=\"false\">"); // thick для headerStyle
        assertThat(content).contains("SpreadsheetDocumentCellLineType");
        // headerStyle (all+thick) → все 4 border-индекса.
        assertThat(content).contains("<leftBorder>");
        assertThat(content).contains("<topBorder>");
        assertThat(content).contains("<rightBorder>");
        assertThat(content).contains("<bottomBorder>");
        assertThat(content).contains("<horizontalAlignment>Center</horizontalAlignment>");
        assertThat(content).contains("<verticalAlignment>Center</verticalAlignment>");
        assertThat(content).contains("<horizontalAlignment>Left</horizontalAlignment>");
        assertThat(content).contains("<textPlacement>Wrap</textPlacement>");

        // TASK-171: ссылка на стиль из ячейки — числовой индекс <f>N>, не имя.
        // Не должно быть именованных id в формате/шрифте.
        assertThat(content).doesNotContain("<id>headerStyle</id>");
        assertThat(content).doesNotContain("<id>header</id>");
        // Ячейки ссылаются на формат числовым индексом >= 1.
        assertThat(content).containsPattern("<f>\\d+</f>");
    }
    
    // ─── Регрессионные тесты для silent-loss багов (см. mxl-parser-provenance.md §7.1) ───

    /**
     * Баг #1 — namedItem: одна именованная область должна давать <namedItem> с правильным
     * именем и диапазоном строк (0-based).
     */
    @Test
    void testNamedItemSingleArea() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, null, null, "Шапка", null)
        );
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Заголовок", rows)
        );
        MxlDsl dsl = new MxlDsl(1, 50, null, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        assertThat(content).contains("<namedItem ");
        assertThat(content).contains("xsi:type=\"NamedItemCells\"");
        assertThat(content).contains("<name>Заголовок</name>");
        assertThat(content).contains("<type>Rows</type>");
        assertThat(content).contains("<beginRow>0</beginRow>");
        assertThat(content).contains("<endRow>0</endRow>");
        assertThat(content).contains("<beginColumn>-1</beginColumn>");
        assertThat(content).contains("<endColumn>-1</endColumn>");
    }

    /**
     * Баг #1 — namedItem: несколько последовательных областей должны давать корректные
     * диапазоны строк без пересечений.
     */
    @Test
    void testNamedItemMultipleAreasRanges() throws Exception {
        // Заголовок — 2 строки (rows 0,1)
        List<MxlDsl.Row> headerRows = Arrays.asList(
                new MxlDsl.Row(null, null,
                        Arrays.asList(new MxlDsl.Cell(1, null, null, null, null, null, "H1", null)),
                        null),
                new MxlDsl.Row(null, null,
                        Arrays.asList(new MxlDsl.Cell(1, null, null, null, null, null, "H2", null)),
                        null)
        );
        // Строка — 1 строка (row 2)
        List<MxlDsl.Row> lineRows = Arrays.asList(
                new MxlDsl.Row(null, null,
                        Arrays.asList(new MxlDsl.Cell(1, null, null, null, "Товар", null, null, null)),
                        null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Заголовок", headerRows),
                new MxlDsl.Area("Строка", lineRows)
        );
        MxlDsl dsl = new MxlDsl(1, 50, null, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Должно быть два namedItem
        assertThat(content).contains("<name>Заголовок</name>");
        assertThat(content).contains("<name>Строка</name>");
        // Заголовок: 0..1
        int hStart = content.indexOf("<name>Заголовок</name>");
        int sStart = content.indexOf("<name>Строка</name>");
        assertThat(hStart).isGreaterThan(0);
        assertThat(sStart).isGreaterThan(hStart);
        String headerBlock = content.substring(hStart, sStart);
        assertThat(headerBlock).contains("<beginRow>0</beginRow>");
        assertThat(headerBlock).contains("<endRow>1</endRow>");
        String lineBlock = content.substring(sStart);
        assertThat(lineBlock).contains("<beginRow>2</beginRow>");
        assertThat(lineBlock).contains("<endRow>2</endRow>");
    }

    /**
     * Баг #2 — rowStyle: ячейка БЕЗ собственного style наследует стиль строки.
     * TASK-171: после перехода на канон стили — числовые индексы; проверяем
     * через round-trip декомпиляцию, что rowStyle действительно применился
     * (граница "bordered" восстанавливается у ячеек строки).
     */
    @Test
    void testRowStyleInheritedByCellWithoutStyle() throws Exception {
        String json = """
                {
                  "columns": 2,
                  "defaultWidth": 50,
                  "styles": {"bordered": {"border": "all"}},
                  "areas": [{"name": "Строка", "rows": [
                    {"rowStyle": "bordered", "cells": [
                      {"col": 1, "text": "А"}, {"col": 2, "text": "Б"}
                    ]}
                  ]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Канон: рамка "all" → палитра <line> + скалярные border-индексы у формата ячеек.
        assertThat(content).contains("<line width=\"1\"");
        assertThat(content).contains("<leftBorder>");
        // Round-trip: декомпиляция должна вернуть рамку у обеих ячеек строки.
        io.github.onec.xmlgen.validator.XmlDocument doc =
                new io.github.onec.xmlgen.validator.XmlStructureReader().parse(outputXml);
        Path json2 = tempDir.resolve("rt.json");
        new io.github.onec.xmlgen.info.MxlDecompiler().decompile(doc, json2);
        String rt = Files.readString(json2);
        assertThat(rt).contains("\"border\"");
    }

    @Test
    void testRowStyleSkipsColumnsOccupiedByPreviousRowspan() throws Exception {
        String json = """
                {
                  "columns": 2,
                  "defaultWidth": 50,
                  "styles": {"bordered": {"border": "all"}},
                  "areas": [{"name": "Строка", "rows": [
                    {"rowStyle": "bordered", "cells": [
                      {"col": 1, "rowspan": 2, "text": "A"}
                    ]},
                    {"rowStyle": "bordered", "cells": [
                      {"col": 2, "text": "B"}
                    ]}
                  ]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        var doc = new XmlStructureReader().parse(outputXml);
        var issues = new MxlValidator().validate(doc, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> i.getCode().equals("MXL-202"));

        String content = Files.readString(outputXml);
        int secondRowStart = content.indexOf("<index>1</index>");
        assertThat(secondRowStart).isGreaterThan(0);
        String secondRowBlock = content.substring(secondRowStart, content.indexOf("</rowsItem>", secondRowStart));
        assertThat(secondRowBlock).doesNotContain("<i>0</i>");
        assertThat(secondRowBlock).contains("<i>1</i>");
    }

    /**
     * Баг #2 — rowStyle: явный style ячейки перебивает rowStyle строки.
     * TASK-171: проверяем, что у двух ячеек разные форматы (money vs bordered)
     * через наличие разных числовых индексов <f> и присутствие обоих стилей в палитре.
     */
    @Test
    void testCellStyleOverridesRowStyle() throws Exception {
        String json = """
                {
                  "columns": 2,
                  "defaultWidth": 50,
                  "styles": {
                    "bordered": {"border": "all"},
                    "money": {"align": "right", "format": "ЧДЦ=2"}
                  },
                  "areas": [{"name": "Строка", "rows": [
                    {"rowStyle": "bordered", "cells": [
                      {"col": 1, "param": "Сумма", "style": "money"},
                      {"col": 2, "text": "Прочее"}
                    ]}
                  ]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // money: выравнивание Right + строка формата.
        assertThat(content).contains("<horizontalAlignment>Right</horizontalAlignment>");
        assertThat(content).contains("ЧДЦ=2");
        // bordered: рамка.
        assertThat(content).contains("<leftBorder>");
        // Две ячейки строки ссылаются на разные форматы.
        assertThat(content).containsPattern("<f>\\d+</f>");
    }

    /**
     * Баг #3 — columnWidths: одиночные индексы и диапазоны записываются как <columnsItem>.
     */
    @Test
    void testColumnWidthsSingleAndRange() throws Exception {
        Map<String, Object> cw = new HashMap<>();
        cw.put("1", 15);
        cw.put("2-3", 30);

        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, null, null, "X", null)
        );
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Область1", rows)
        );
        MxlDsl dsl = new MxlDsl(3, null, cw, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Должны быть отдельные <format> блоки с шириной 15 и 30
        assertThat(content).contains("<width>15</width>");
        assertThat(content).contains("<width>30</width>");
        // TASK-171: канон — <columnsItem> с числовым <formatIndex>, индексы 0-based.
        assertThat(content).contains("<columnsItem>");
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<index>1</index>");
        assertThat(content).contains("<index>2</index>");
        // formatIndex теперь числовой (ссылка в палитру), не "__cw_15".
        assertThat(content).doesNotContain("__cw_");
        assertThat(content).containsPattern("<formatIndex>\\d+</formatIndex>");
    }

    /**
     * Баг #3 — columnWidths + defaultWidth.
     * TASK-171 (канон): ширина по умолчанию задаётся через <defaultFormatIndex>
     * (формат с width=defaultWidth), а не через <columnsItem> на каждую колонку.
     * Явно перечисляется только колонка 1 (нестандартная ширина 15);
     * колонки 2,3 наследуют defaultWidth=20 через defaultFormatIndex.
     */
    @Test
    void testColumnWidthsWithDefaultWidth() throws Exception {
        Map<String, Object> cw = new HashMap<>();
        cw.put("1", 15);

        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, null, null, "X", null)
        );
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, cells, null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Область1", rows)
        );
        MxlDsl dsl = new MxlDsl(3, 20, cw, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Формат default-width=20 (для defaultFormatIndex) и формат width=15 (колонка 1).
        assertThat(content).contains("<width>15</width>");
        assertThat(content).contains("<width>20</width>");
        assertThat(content).contains("<defaultFormatIndex>");
        assertThat(content).doesNotContain("__cw_");
        // Перечислена только нестандартная колонка 1 (index 0).
        assertThat(content).contains("<columnsItem>");
        assertThat(content).contains("<index>0</index>");
    }

    // ─── Канон Широкова: новые опциональные поля ───

    /**
     * A.4 — формат-строки 1С: `ЧДЦ=N`, `ДФ=...` через style.format пробрасываются в XML.
     */
    @Test
    void testFormatStringNumericPasses() throws Exception {
        String json = """
                {
                  "columns": 1,
                  "styles": {
                    "money": {"format": "ЧЦ=15; ЧДЦ=2"}
                  },
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "123.45", "style": "money"}
                  ]}]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // TASK-171: канон — строка формата как <format><v8:item><v8:content>..
        assertThat(content).contains("<v8:content>ЧЦ=15; ЧДЦ=2</v8:content>");
    }

    @Test
    void testFormatStringDatePasses() throws Exception {
        String json = """
                {
                  "columns": 1,
                  "styles": {
                    "date": {"format": "ДФ=dd.MM.yyyy"}
                  },
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "01.01.2026", "style": "date"}
                  ]}]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<v8:content>ДФ=dd.MM.yyyy</v8:content>");
    }

    @Test
    void testFormatStringGroupSeparator() throws Exception {
        String json = """
                {
                  "columns": 1,
                  "styles": {
                    "grp": {"format": "ЧО=1; ЧРГ=,"}
                  },
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "1,000.00", "style": "grp"}
                  ]}]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<v8:content>ЧО=1; ЧРГ=,</v8:content>");
    }

    /**
     * A.5 — rowspan: ячейка с rowspan=N генерирует `<rowMerge>N-1</rowMerge>`.
     */
    @Test
    void testRowspanGeneratesRowMerge() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, 3, null, null, null, "Hi", null)
        );
        MxlDsl dsl = new MxlDsl(2, 40, null, null, null, Arrays.asList(
                new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, cells, null),
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "Z", null)
                        ), null),
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "Z2", null)
                        ), null)
                ))
        ));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // TASK-171: канон — rowspan=3 → document-level <merge><h>2</h>.
        assertThat(content).contains("<h>2</h>");
    }

    @Test
    void testRowspanOneOmitsRowMerge() throws Exception {
        // rowspan=1 (default) should NOT emit rowMerge element.
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, 1, null, null, null, "Hi", null)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(
                new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, cells, null)
                ))
        ));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // rowspan=1 (default) → нет вертикального объединения, нет <h> в merge,
        // и вообще нет <merge> (span тоже 1).
        assertThat(content).doesNotContain("<rowMerge>");
        assertThat(content).doesNotContain("<merge>");
    }

    @Test
    void testRowspanIdempotentWithSpan() throws Exception {
        // span + rowspan together — both must be emitted.
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, 2, 2, null, null, null, "Hi", null)
        );
        MxlDsl dsl = new MxlDsl(2, 40, null, null, null, Arrays.asList(
                new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, cells, null),
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "Z", null)
                        ), null)
                ))
        ));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // TASK-171: канон — span=2 + rowspan=2 → document-level <merge> с <w>1 и <h>1.
        assertThat(content).contains("<w>1</w>");
        assertThat(content).contains("<h>1</h>");
    }

    /**
     * A.6 — detailRecord boolean флаг.
     */
    @Test
    void testDetailRecordTrueGeneratesElement() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, "Sum", null, null, null, Boolean.TRUE)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(
                new MxlDsl.Area("X", Arrays.asList(new MxlDsl.Row(null, null, cells, null)))
        ));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<detailRecord>true</detailRecord>");
    }

    @Test
    void testDetailRecordFalseOrNullOmitsElement() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                new MxlDsl.Cell(1, null, null, null, "Sum", null, null, null, Boolean.FALSE)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(
                new MxlDsl.Area("X", Arrays.asList(new MxlDsl.Row(null, null, cells, null)))
        ));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).doesNotContain("<detailRecord>");
    }

    /**
     * A.7 — `empty: N` в строке — пропуск N пустых строк.
     */
    @Test
    void testEmptyOneSkipsSingleRow() throws Exception {
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                ), null),
                new MxlDsl.Row(null, null, null, 1),  // empty=1
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "B", null)
                ), null)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(new MxlDsl.Area("X", rows)));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // Row "A" at index 0, "B" should now be at index 2 (one empty in between).
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<index>2</index>");
        assertThat(content).contains("<height>3</height>");
    }

    @Test
    void testEmptyNSkipsMultipleRows() throws Exception {
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                ), null),
                new MxlDsl.Row(null, null, null, 4),
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "B", null)
                ), null)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(new MxlDsl.Area("X", rows)));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // "B" at index 5 (after 4 empty rows).
        assertThat(content).contains("<index>5</index>");
        assertThat(content).contains("<height>6</height>");
    }

    @Test
    void testEmptyMixedWithRegularRows() throws Exception {
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                ), null),
                new MxlDsl.Row(null, null, null, 2),
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "B", null)
                ), null),
                new MxlDsl.Row(null, null, null, 1),
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "C", null)
                ), null)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(new MxlDsl.Area("X", rows)));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // A: 0, B: 3 (0 + 1 + 2 empty), C: 5 (3 + 1 + 1 empty)
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<index>3</index>");
        assertThat(content).contains("<index>5</index>");
        assertThat(content).contains("<height>6</height>");
    }

    /**
     * A.8 — `page` enum: A4-landscape, A4-portrait, integer.
     */
    @Test
    void testPageA4Landscape() throws Exception {
        Map<String, Object> widths = new HashMap<>();
        widths.put("1", "1x");
        widths.put("2", "1x");
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-landscape");
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(
                new MxlDsl(2, null, widths, null, null, dsl.getAreas(), "A4-landscape"), out);
        String content = Files.readString(out);
        assertThat(content).doesNotContain("<pageSetup>");
        assertThat(content).doesNotContain("<orientation>Landscape</orientation>");
        assertThat(content).contains("<width>390</width>");
    }

    @Test
    void testPageA4Portrait() throws Exception {
        Map<String, Object> widths = new HashMap<>();
        widths.put("1", "1x");
        widths.put("2", "1x");
        MxlDsl dsl = new MxlDsl(2, null, widths, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-portrait");
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).doesNotContain("<pageSetup>");
        assertThat(content).doesNotContain("<orientation>Portrait</orientation>");
        assertThat(content).contains("<width>270</width>");
    }

    @Test
    void testPageOmittedWhenNotSet() throws Exception {
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).doesNotContain("<pageSetup>");
    }

    @Test
    void testPageWithExcessiveWidthsFailsCompilation() throws Exception {
        // page=portrait (540) + columnWidths summing to 1100 has no canonical pageSetup
        // carrier in XML, so writer must reject it before producing impossible geometry.
        Map<String, Object> cw = new HashMap<>();
        cw.put("1", 500);
        cw.put("2", 600);
        MxlDsl dsl = new MxlDsl(2, null, cw, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-portrait");
        Path out = tempDir.resolve("Template.xml");
        assertThatThrownBy(() -> new MxlWriter(OutputFormat.DESIGNER).create(dsl, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MXL page width 540")
                .hasMessageContaining("absolute column widths 1100");
    }

    @Test
    void testPageWithNoRemainingWidthForDefaultColumnsFailsCompilation() throws Exception {
        Map<String, Object> cw = new HashMap<>();
        cw.put("1", 540);
        MxlDsl dsl = new MxlDsl(2, null, cw, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-portrait");
        Path out = tempDir.resolve("Template.xml");
        assertThatThrownBy(() -> new MxlWriter(OutputFormat.DESIGNER).create(dsl, out))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MXL page width 540")
                .hasMessageContaining("absolute column widths 540");
    }

    /**
     * Тест 6: JSON DSL roundtrip.
     */
    @Test
    void testJsonDslRoundtrip() throws Exception {
        String json = """
                {
                  "columns": 2,
                  "defaultWidth": 50,
                  "areas": [
                    {
                      "name": "Заголовок",
                      "rows": [
                        {
                          "cells": [
                            {"col": 1, "span": 2, "text": "Тестовый документ"}
                          ]
                        }
                      ]
                    },
                    {
                      "name": "Строка",
                      "rows": [
                        {
                          "cells": [
                            {"col": 1, "param": "Параметр1"},
                            {"col": 2, "param": "Параметр2"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;
        
        ObjectMapper mapper = new ObjectMapper();
        MxlDsl dsl = mapper.readValue(json, MxlDsl.class);
        
        Path outputXml = tempDir.resolve("Template.xml");
        MxlWriter writer = new MxlWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        assertThat(content).contains("<size>2</size>");
        assertThat(content).contains("Тестовый документ");
        // TASK-171: канон — span=2 → document-level <merge><w>1</w>.
        assertThat(content).contains("<merge>");
        assertThat(content).contains("<w>1</w>");
        assertThat(content).contains("<parameter>");
        // TASK-171: канон — <parameter> хранит имя как текст узла, без вложенного v8:content.
        assertThat(content).contains("<parameter>Параметр1</parameter>");
        assertThat(content).contains("<parameter>Параметр2</parameter>");
    }

    // ─── TASK-171 (остаточный долг W2): цвета / Nx / рисунки / verticalUnmerge / columnMerge ───

    /**
     * R5 — цвета: textColor/backColor/borderColor пишутся дословно (hex и style-ref),
     * в каноническом порядке (borderColor у границ; textColor/backColor до textPlacement).
     */
    @Test
    void testColorsWrittenLiterally() throws Exception {
        String json = """
                {
                  "columns": 1,
                  "styles": {
                    "c": {"border": "all", "borderColor": "style:FormTextColor",
                          "textColor": "#000080", "backColor": "#BBEEC7"}
                  },
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "Цвет", "style": "c"}
                  ]}]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<textColor>#000080</textColor>");
        assertThat(content).contains("<backColor>#BBEEC7</backColor>");
        assertThat(content).contains("<borderColor>style:FormTextColor</borderColor>");
        // Канон-порядок: borderColor перед width/выравниванием; textColor перед backColor,
        // оба до textPlacement (тут textPlacement нет, проверяем взаимный порядок цветов).
        assertThat(content.indexOf("<textColor>")).isLessThan(content.indexOf("<backColor>"));
    }

    /**
     * R8 — "Nx" пропорции ширин: при заданном page авто-расчёт defaultWidth и ширин колонок.
     * page=600, 3 колонки: col1=2x, остальные по 1 unit. units=2+1+1=4 → defaultWidth=150,
     * col1 width = 2*150 = 300.
     */
    @Test
    void testNxProportionalWidths() throws Exception {
        String json = """
                {
                  "columns": 3,
                  "page": "600",
                  "columnWidths": {"1": "2x"},
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "A"}
                  ]}]}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // defaultWidth=150 (default format), col1 width=300 — обе ширины должны присутствовать.
        assertThat(content).contains("<width>150</width>");
        assertThat(content).contains("<width>300</width>");
        // Колонка 1 (index 0) перечислена как нестандартная.
        assertThat(content).contains("<columnsItem>");
    }

    /**
     * R9 — рисунки: drawing пишется document-level со всеми заданными полями + picture-палитра.
     */
    @Test
    void testDrawingAndPictureWritten() throws Exception {
        String json = """
                {
                  "columns": 4,
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "A"}
                  ]}]}],
                  "drawings": [
                    {"drawingType": "Picture", "id": 1, "formatIndex": 0,
                     "beginRow": 0, "beginColumn": 1, "endRow": 0, "endColumn": 2,
                     "pictureSize": "Proportionally", "zOrder": 1, "pictureIndex": 1}
                  ],
                  "pictures": [
                    {"index": 0},
                    {"index": 1, "data": "iVBORw0KGgo="}
                  ]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<drawing>");
        assertThat(content).contains("<drawingType>Picture</drawingType>");
        assertThat(content).contains("<pictureIndex>1</pictureIndex>");
        assertThat(content).contains("<picture>");
        assertThat(content).contains("iVBORw0KGgo=");
        // drawing должен идти ДО templateMode (порядок канона), picture — ПОСЛЕ format.
        assertThat(content.indexOf("<drawing>")).isLessThan(content.indexOf("<templateMode>"));
    }

    /**
     * verticalUnmerge + columnMerge (r=-1): пишутся document-level в правильном порядке.
     */
    @Test
    void testVerticalUnmergeAndColumnMerge() throws Exception {
        String json = """
                {
                  "columns": 20,
                  "areas": [{"name": "X", "rows": [{"cells": [
                    {"col": 1, "text": "A"}
                  ]}]}],
                  "columnMerges": [{"c": 14, "w": 1}],
                  "verticalUnmerges": [{"r": 7, "c": 14, "w": 1}]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // columnMerge: <merge> с <r>-1</r>.
        assertThat(content).contains("<r>-1</r>");
        // verticalUnmerge присутствует.
        assertThat(content).contains("<verticalUnmerge>");
        assertThat(content).contains("<r>7</r>");
        // verticalUnmerge идёт после merge (порядок канона).
        assertThat(content.indexOf("<r>-1</r>")).isLessThan(content.indexOf("<verticalUnmerge>"));
    }

    /**
     * Регресс: одиночная пустая строка ({}) теперь эмитит явный &lt;empty&gt;true&lt;/empty&gt;
     * (а не молча пропускается) — иначе rowsItem-count расходится с height (ложные MXL-203).
     */
    @Test
    void testSingleEmptyRowEmitsExplicitRowsItem() throws Exception {
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                ), null),
                new MxlDsl.Row(null, null, null, null),  // одиночная пустая {}
                new MxlDsl.Row(null, null, Arrays.asList(
                        new MxlDsl.Cell(1, null, null, null, null, null, "B", null)
                ), null)
        );
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null, Arrays.asList(new MxlDsl.Area("X", rows)));
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // 3 rowsItem (включая пустую), height=3, и есть <empty>true.
        assertThat(content).contains("<empty>true</empty>");
        assertThat(content).contains("<height>3</height>");
        assertThat(content).contains("<index>1</index>");
        // Число rowsItem == height.
        int rowsItemCount = content.split("<rowsItem>", -1).length - 1;
        assertThat(rowsItemCount).isEqualTo(3);
    }
}

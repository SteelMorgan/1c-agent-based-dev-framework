package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        
        // БЕЗ BOM
        byte[] bytes = Files.readAllBytes(outputXml);
        assertThat(bytes[0]).isNotEqualTo((byte) 0xEF);
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
        
        // Проверки параметров
        assertThat(content).contains("<parameter>");
        assertThat(content).contains("<v8:content>Заголовок</v8:content>");
        assertThat(content).contains("<v8:content>Значение</v8:content>");
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
        
        // Проверки span
        assertThat(content).contains("<merge>2</merge>"); // span=3 → merge=2
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
        
        // Проверка шрифтов
        assertThat(content).contains("<font>");
        assertThat(content).contains("<id>header</id>");
        assertThat(content).contains("<face>Arial</face>");
        assertThat(content).contains("<height>12</height>");
        assertThat(content).contains("<bold>true</bold>");
        assertThat(content).contains("<id>normal</id>");
        assertThat(content).contains("<height>10</height>");
        
        // Проверка стилей
        assertThat(content).contains("<format>");
        assertThat(content).contains("<id>headerStyle</id>");
        assertThat(content).contains("<font>header</font>");
        assertThat(content).contains("<horizontalAlignment>Center</horizontalAlignment>");
        assertThat(content).contains("<verticalAlignment>Center</verticalAlignment>");
        assertThat(content).contains("<border>");
        assertThat(content).contains("<id>dataStyle</id>");
        assertThat(content).contains("<font>normal</font>");
        assertThat(content).contains("<horizontalAlignment>Left</horizontalAlignment>");
        assertThat(content).contains("<textPlacement>Wrap</textPlacement>");
        
        // Проверка применения стилей к ячейкам
        assertThat(content).contains("<f>headerStyle</f>");
        assertThat(content).contains("<f>dataStyle</f>");
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
     */
    @Test
    void testRowStyleInheritedByCellWithoutStyle() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                // Cell без style — должна унаследовать "bordered"
                new MxlDsl.Cell(1, null, null, null, "А", null, null, null),
                // Cell без style — должна унаследовать "bordered"
                new MxlDsl.Cell(2, null, null, null, "Б", null, null, null)
        );
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, "bordered", cells, null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Строка", rows)
        );
        MxlDsl dsl = new MxlDsl(2, 50, null, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Не должно быть дефолтного <f>0</f> — обе ячейки должны указать на rowStyle
        assertThat(content).contains("<f>bordered</f>");
        assertThat(content).doesNotContain("<f>0</f>");
    }

    /**
     * Баг #2 — rowStyle: явный style ячейки перебивает rowStyle строки.
     */
    @Test
    void testCellStyleOverridesRowStyle() throws Exception {
        List<MxlDsl.Cell> cells = Arrays.asList(
                // Ячейка с явным style — должна сохранить свой
                new MxlDsl.Cell(1, null, null, "money", "Сумма", null, null, null),
                // Ячейка без style — наследует rowStyle
                new MxlDsl.Cell(2, null, null, null, "Прочее", null, null, null)
        );
        List<MxlDsl.Row> rows = Arrays.asList(
                new MxlDsl.Row(null, "bordered", cells, null)
        );
        List<MxlDsl.Area> areas = Arrays.asList(
                new MxlDsl.Area("Строка", rows)
        );
        MxlDsl dsl = new MxlDsl(2, 50, null, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Ячейка 1 — money (явный)
        assertThat(content).contains("<f>money</f>");
        // Ячейка 2 — bordered (унаследован)
        assertThat(content).contains("<f>bordered</f>");
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
        // Должны быть <columnsItem> с индексами 0, 1, 2 (0-based в XML)
        assertThat(content).contains("<columnsItem>");
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<index>1</index>");
        assertThat(content).contains("<index>2</index>");
        assertThat(content).contains("<formatIndex>__cw_15</formatIndex>");
        assertThat(content).contains("<formatIndex>__cw_30</formatIndex>");
    }

    /**
     * Баг #3 — columnWidths + defaultWidth: неуказанные колонки получают defaultWidth.
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
        // columns=3, defaultWidth=20, columnWidths={"1": 15} → колонка 1 = 15, 2 и 3 = 20.
        MxlDsl dsl = new MxlDsl(3, 20, cw, null, null, areas);

        Path outputXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        // Должны быть форматы для обеих ширин
        assertThat(content).contains("<width>15</width>");
        assertThat(content).contains("<width>20</width>");
        assertThat(content).contains("<formatIndex>__cw_15</formatIndex>");
        assertThat(content).contains("<formatIndex>__cw_20</formatIndex>");
        // Все 3 колонки должны быть прописаны
        assertThat(content).contains("<index>0</index>");
        assertThat(content).contains("<index>1</index>");
        assertThat(content).contains("<index>2</index>");
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
        assertThat(content).contains("<format>ЧЦ=15; ЧДЦ=2</format>");
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
        assertThat(content).contains("<format>ДФ=dd.MM.yyyy</format>");
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
        assertThat(content).contains("<format>ЧО=1; ЧРГ=,</format>");
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
        assertThat(content).contains("<rowMerge>2</rowMerge>");
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
        assertThat(content).doesNotContain("<rowMerge>");
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
        assertThat(content).contains("<merge>1</merge>");
        assertThat(content).contains("<rowMerge>1</rowMerge>");
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
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-landscape");
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<pageSetup>");
        assertThat(content).contains("<orientation>Landscape</orientation>");
        assertThat(content).contains("<pageWidth>780</pageWidth>");
    }

    @Test
    void testPageA4Portrait() throws Exception {
        MxlDsl dsl = new MxlDsl(1, 40, null, null, null,
                Arrays.asList(new MxlDsl.Area("X", Arrays.asList(
                        new MxlDsl.Row(null, null, Arrays.asList(
                                new MxlDsl.Cell(1, null, null, null, null, null, "A", null)
                        ), null)))),
                "A4-portrait");
        Path out = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        assertThat(content).contains("<orientation>Portrait</orientation>");
        assertThat(content).contains("<pageWidth>540</pageWidth>");
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
    void testPageWithExcessiveWidthsTriggersValidatorError() throws Exception {
        // page=portrait (540) + columnWidths summing to 1100 → should fail MXL-206 via validator.
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
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, out);
        String content = Files.readString(out);
        // The XML must include the pageSetup with width 540 and per-column formats summing > 540.
        assertThat(content).contains("<pageWidth>540</pageWidth>");
        assertThat(content).contains("<width>500</width>");
        assertThat(content).contains("<width>600</width>");
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
        assertThat(content).contains("<merge>1</merge>");
        assertThat(content).contains("<parameter>");
        assertThat(content).contains("<v8:content>Параметр1</v8:content>");
        assertThat(content).contains("<v8:content>Параметр2</v8:content>");
    }
}

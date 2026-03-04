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

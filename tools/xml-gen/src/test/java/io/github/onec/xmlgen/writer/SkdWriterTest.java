package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdDsl;
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
 * Тесты для SkdWriter.
 */
class SkdWriterTest {
    
    @TempDir
    Path tempDir;
    
    /**
     * Тест 1: Минимальная схема (один набор данных с запросом).
     */
    @Test
    void testMinimalSkd() throws Exception {
        List<SkdDsl.Field> fields = Arrays.asList(
                new SkdDsl.Field("Значение", "Значение", "Значение", null)
        );
        
        List<SkdDsl.DataSet> dataSets = Arrays.asList(
                new SkdDsl.DataSet("НаборДанных1", null, "ВЫБРАТЬ 1 КАК Значение", null, null, fields, null)
        );
        
        SkdDsl dsl = new SkdDsl(null, dataSets, null, null, null);
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверки
        assertThat(content).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("<DataCompositionSchema");
        assertThat(content).contains("<dataSource>");
        assertThat(content).contains("<name>ИсточникДанных1</name>");
        assertThat(content).contains("<dataSourceType>Local</dataSourceType>");
        assertThat(content).contains("<dataSet xsi:type=\"DataSetQuery\">");
        assertThat(content).contains("<query>ВЫБРАТЬ 1 КАК Значение</query>");
        assertThat(content).contains("<settingsVariant>");
        assertThat(content).contains("<dcsset:name>Основной</dcsset:name>");
        
        // TASK-171 (Р-4): Designer-вывод СКД обязан содержать UTF-8 BOM (как платформенные Template.xml).
        byte[] bytes = Files.readAllBytes(outputXml);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }
    
    /**
     * Тест 2: Схема с параметрами.
     */
    @Test
    void testSkdWithParameters() throws Exception {
        List<SkdDsl.DataSet> dataSets = Arrays.asList(
                new SkdDsl.DataSet("НаборДанных1", null, "ВЫБРАТЬ * ИЗ Таблица ГДЕ Дата >= &ДатаНачала", null, null, null, null)
        );
        
        List<SkdDsl.Parameter> parameters = Arrays.asList(
                new SkdDsl.Parameter("ДатаНачала", "Дата начала", "date", "2024-01-01T00:00:00")
        );
        
        SkdDsl dsl = new SkdDsl(null, dataSets, parameters, null, null);
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки параметров
        assertThat(content).contains("<parameter>");
        assertThat(content).contains("<name>ДатаНачала</name>");
        assertThat(content).contains("<title xsi:type=\"v8:LocalStringType\">");
        assertThat(content).contains("<v8:content>Дата начала</v8:content>");
        assertThat(content).contains("<value xsi:type=\"xs:dateTime\">2024-01-01T00:00:00</value>");
    }
    
    /**
     * Тест 3: Схема с итоговыми полями.
     */
    @Test
    void testSkdWithTotalFields() throws Exception {
        List<SkdDsl.DataSet> dataSets = Arrays.asList(
                new SkdDsl.DataSet("Продажи", null, "ВЫБРАТЬ Количество, Сумма ИЗ Продажи", null, null, null, null)
        );
        
        List<SkdDsl.TotalField> totalFields = Arrays.asList(
                new SkdDsl.TotalField("Количество", "Сумма(Количество)"),
                new SkdDsl.TotalField("Сумма", "Сумма(Сумма)")
        );
        
        SkdDsl dsl = new SkdDsl(null, dataSets, null, totalFields, null);
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки итоговых полей
        assertThat(content).contains("<totalField>");
        assertThat(content).contains("<dataPath>Количество</dataPath>");
        assertThat(content).contains("<expression>Сумма(Количество)</expression>");
        assertThat(content).contains("<dataPath>Сумма</dataPath>");
        assertThat(content).contains("<expression>Сумма(Сумма)</expression>");
    }
    
    /**
     * Тест 4: Схема с вариантом настроек.
     */
    @Test
    void testSkdWithSettingsVariant() throws Exception {
        List<SkdDsl.DataSet> dataSets = Arrays.asList(
                new SkdDsl.DataSet("Продажи", null, "ВЫБРАТЬ * ИЗ Продажи", null, null, null, null)
        );
        
        List<String> selection = Arrays.asList("Наименование", "Количество", "Сумма");
        
        List<SkdDsl.Structure> structure = Arrays.asList(
                new SkdDsl.Structure("group", Arrays.asList("Организация"), Arrays.asList("Auto"))
        );
        
        SkdDsl.Settings settings = new SkdDsl.Settings(selection, null, null, null, null, structure);
        
        List<SkdDsl.SettingsVariant> variants = Arrays.asList(
                new SkdDsl.SettingsVariant("Основной", "Продажи по организациям", settings)
        );
        
        SkdDsl dsl = new SkdDsl(null, dataSets, null, null, variants);
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки варианта настроек
        assertThat(content).contains("<settingsVariant>");
        assertThat(content).contains("<dcsset:name>Основной</dcsset:name>");
        assertThat(content).contains("<dcsset:presentation xsi:type=\"xs:string\">Продажи по организациям</dcsset:presentation>");
        assertThat(content).contains("<dcsset:settings");
        assertThat(content).contains("<dcsset:selection>");
        assertThat(content).contains("<dcsset:item xsi:type=\"dcsset:SelectedItemField\">");
        assertThat(content).contains("<dcsset:field>Наименование</dcsset:field>");
        //**agent TASK-174 [07.06.2026 11:40:00]
        //assertThat(content).contains("<dcsset:structure>");
        // Канон платформы: structure items лежат прямо под dcsset:settings,
        // обёртки <dcsset:structure> в сериализации НЕТ (1c-dcs-spec.md §11.1).
        assertThat(content).doesNotContain("<dcsset:structure>");
        //**agent TASK-174
        assertThat(content).contains("<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">");
        assertThat(content).contains("<dcsset:groupItems>");
        assertThat(content).contains("<dcsset:item xsi:type=\"dcsset:GroupItemField\">");
        assertThat(content).contains("<dcsset:field>Организация</dcsset:field>");
    }
    
    /**
     * Тест 5: JSON DSL roundtrip.
     */
    @Test
    void testJsonDslRoundtrip() throws Exception {
        String json = """
                {
                  "dataSets": [
                    {
                      "name": "НаборДанных1",
                      "query": "ВЫБРАТЬ Наименование, Количество ИЗ Номенклатура",
                      "fields": [
                        {"dataPath": "Наименование", "title": "Наименование"},
                        {"dataPath": "Количество", "title": "Количество", "type": "number(15,2)"}
                      ]
                    }
                  ],
                  "totalFields": [
                    {"dataPath": "Количество", "expression": "Сумма(Количество)"}
                  ]
                }
                """;
        
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        assertThat(content).contains("<DataCompositionSchema");
        assertThat(content).contains("<name>НаборДанных1</name>");
        assertThat(content).contains("<query>ВЫБРАТЬ Наименование, Количество ИЗ Номенклатура</query>");
        assertThat(content).contains("<dataPath>Наименование</dataPath>");
        assertThat(content).contains("<totalField>");
        assertThat(content).contains("<expression>Сумма(Количество)</expression>");
    }
    
    /**
     * Тест 6: SKD с filter и order.
     */
    @Test
    void testSkdWithFilterAndOrder() throws Exception {
        SkdDsl.DataSet dataSet = new SkdDsl.DataSet(
                "НаборДанных1",
                null,
                "ВЫБРАТЬ Наименование, Количество, Дата ИЗ Номенклатура",
                null,
                null,
                List.of(
                        new SkdDsl.Field("Наименование", null, "Наименование", null),
                        new SkdDsl.Field("Количество", null, "Количество", "number(15,2)"),
                        new SkdDsl.Field("Дата", null, "Дата", "date")
                ),
                null
        );
        
        SkdDsl.Settings settings = new SkdDsl.Settings(
                List.of("Наименование", "Количество"),
                List.of("Количество > 0", "Дата >= 2024-01-01T00:00:00"),
                List.of("Количество desc", "Наименование"),
                null,
                null,
                null
        );
        
        SkdDsl.SettingsVariant variant = new SkdDsl.SettingsVariant(
                "Основной",
                "Основной вариант",
                settings
        );
        
        SkdDsl dsl = new SkdDsl(
                null,
                List.of(dataSet),
                null,
                null,
                List.of(variant)
        );
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверка filter
        assertThat(content).contains("<dcsset:filter>");
        assertThat(content).contains("<dcsset:item xsi:type=\"dcsset:FilterItemComparison\">");
        assertThat(content).contains("<dcsset:left xsi:type=\"dcscor:Field\">Количество</dcsset:left>");
        assertThat(content).contains("<dcsset:comparisonType>Greater</dcsset:comparisonType>");
        assertThat(content).contains("<dcsset:right xsi:type=\"xs:decimal\">0</dcsset:right>");
        assertThat(content).contains("<dcsset:left xsi:type=\"dcscor:Field\">Дата</dcsset:left>");
        assertThat(content).contains("<dcsset:comparisonType>GreaterOrEqual</dcsset:comparisonType>");
        assertThat(content).contains("<dcsset:right xsi:type=\"xs:dateTime\">2024-01-01T00:00:00</dcsset:right>");
        
        // Проверка order
        assertThat(content).contains("<dcsset:order>");
        assertThat(content).contains("<dcsset:item xsi:type=\"dcsset:OrderItemField\">");
        assertThat(content).contains("<dcsset:field>Количество</dcsset:field>");
        assertThat(content).contains("<dcsset:orderType>Desc</dcsset:orderType>");
        assertThat(content).contains("<dcsset:field>Наименование</dcsset:field>");
        assertThat(content).contains("<dcsset:orderType>Asc</dcsset:orderType>");
    }
    
    /**
     * Тест 7: SKD с conditionalAppearance.
     */
    @Test
    void testSkdWithConditionalAppearance() throws Exception {
        SkdDsl.DataSet dataSet = new SkdDsl.DataSet(
                "НаборДанных1",
                null,
                "ВЫБРАТЬ Наименование, Сумма ИЗ Продажи",
                null,
                null,
                List.of(
                        new SkdDsl.Field("Наименование", null, "Наименование", null),
                        new SkdDsl.Field("Сумма", null, "Сумма", "number(15,2)")
                ),
                null
        );
        
        // Условное оформление: выделять суммы > 1000
        Map<String, Object> appearance = new HashMap<>();
        appearance.put("ЦветТекста", "web:Red");
        appearance.put("Шрифт", "Arial");
        
        SkdDsl.ConditionalAppearanceItem caItem = new SkdDsl.ConditionalAppearanceItem(
                List.of("Сумма"),
                List.of("Сумма > 1000"),
                appearance,
                "Выделять крупные суммы"
        );
        
        SkdDsl.Settings settings = new SkdDsl.Settings(
                List.of("Наименование", "Сумма"),
                null,
                null,
                List.of(caItem),
                null,
                null
        );
        
        SkdDsl.SettingsVariant variant = new SkdDsl.SettingsVariant(
                "Основной",
                "Основной вариант",
                settings
        );
        
        SkdDsl dsl = new SkdDsl(
                null,
                List.of(dataSet),
                null,
                null,
                List.of(variant)
        );
        
        Path outputXml = tempDir.resolve("Template.xml");
        SkdWriter writer = new SkdWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверка conditionalAppearance
        assertThat(content).contains("<dcsset:conditionalAppearance>");
        assertThat(content).contains("<dcsset:selection>");
        assertThat(content).contains("<dcsset:field>Сумма</dcsset:field>");
        assertThat(content).contains("<dcsset:filter>");
        assertThat(content).contains("<dcsset:left xsi:type=\"dcscor:Field\">Сумма</dcsset:left>");
        assertThat(content).contains("<dcsset:comparisonType>Greater</dcsset:comparisonType>");
        assertThat(content).contains("<dcsset:right xsi:type=\"xs:decimal\">1000</dcsset:right>");
        assertThat(content).contains("<dcsset:appearance>");
        assertThat(content).contains("<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">");
        assertThat(content).contains("<dcscor:parameter>ЦветТекста</dcscor:parameter>");
        assertThat(content).contains("<dcscor:value xsi:type=\"v8ui:Color\">web:Red</dcscor:value>");
        assertThat(content).contains("<dcscor:parameter>Шрифт</dcscor:parameter>");
        assertThat(content).contains("<dcscor:value xsi:type=\"xs:string\">Arial</dcscor:value>");
        assertThat(content).contains("<dcsset:presentation xsi:type=\"xs:string\">Выделять крупные суммы</dcsset:presentation>");
    }

    // ============================================================
    // §6 — extended SKD DSL concepts.
    // ============================================================

    /** Тест 8: DataSetObject (внешний набор). */
    @Test
    void testDataSetObject() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "object",
                    "name": "ЖурналОшибок",
                    "objectName": "ЖурналОшибок",
                    "fields": [
                      { "field": "ТекстСообщения", "type": "string(150)" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<dataSet xsi:type=\"DataSetObject\">");
        assertThat(content).contains("<objectName>ЖурналОшибок</objectName>");
        assertThat(content).contains("<field>ТекстСообщения</field>");
    }

    /** Тест 9: DataSetUnion с {@code sourceDataSets}. */
    @Test
    void testDataSetUnion() throws Exception {
        String json = """
                {
                  "dataSets": [
                    { "type": "query", "name": "A", "query": "ВЫБРАТЬ 1 КАК Х" },
                    { "type": "query", "name": "B", "query": "ВЫБРАТЬ 2 КАК Х" },
                    { "type": "union", "name": "Все", "sourceDataSets": ["A", "B"] }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<dataSet xsi:type=\"DataSetUnion\">");
        assertThat(content).contains("<dataSet>A</dataSet>");
        assertThat(content).contains("<dataSet>B</dataSet>");
    }

    /** Тест 10: calculatedFields с типом и выражением. */
    @Test
    void testCalculatedFields() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Продажи",
                    "query": "ВЫБРАТЬ Цена, Закупка ИЗ Т",
                    "calculatedFields": [
                      { "name": "Маржа", "expression": "Цена - Закупка", "type": "decimal(15,2)" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<calculatedField>");
        assertThat(content).contains("<expression>Цена - Закупка</expression>");
        assertThat(content).contains("<dataPath>Маржа</dataPath>");
        //++agent TASK-174 [07.06.2026 11:40:00]
        // У calculatedField нет дочернего <field> (1c-dcs-spec.md §6; 53 канон-схемы GBIG PAM
        // без единого <field> внутри calculatedField).
        assertThat(content).doesNotContain("<field>Маржа</field>");
        //++agent TASK-174
    }

    /** Тест 11: templates DSL — rows/widths/parameters/drilldown. */
    @Test
    void testTemplatesDsl() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Н",
                    "query": "ВЫБРАТЬ 1",
                    "fields": [{ "field": "Сумма", "appearance": { "Формат": "ЧДЦ=2" } }]
                  }],
                  "templates": [{
                    "name": "Макет1",
                    "type": "group",
                    "style": "header",
                    "widths": [15, "10-20", 30],
                    "rows": [
                      ["Заголовок", "|", ">", null],
                      ["{Сумма}", "Итого:", "{ВидКассы}"]
                    ],
                    "parameters": [
                      { "name": "Сумма", "expression": "Сумма(Сумма)", "drilldown": "Сумма" }
                    ]
                  }],
                  "groupTemplates": [
                    { "groupField": "Счет", "templateType": "Header", "template": "Макет1" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<template");
        assertThat(content).contains("<name>Макет1</name>");
        assertThat(content).contains("<style>header</style>");
        // ширины
        assertThat(content).contains("<width>15</width>");
        assertThat(content).contains("<width min=\"10\" max=\"20\">");
        assertThat(content).contains("<width>30</width>");
        // строки
        assertThat(content).contains("<row>");
        assertThat(content).contains("<cell type=\"text\">Заголовок</cell>");
        assertThat(content).contains("<cell type=\"mergeUp\">");
        assertThat(content).contains("<cell type=\"mergeLeft\">");
        assertThat(content).contains("<cell type=\"empty\">");
        assertThat(content).contains("<cell type=\"param\" name=\"Сумма\">");
        // drilldown
        assertThat(content).contains("DetailsAreaTemplateParameter");
        assertThat(content).contains("<dcsat:name>Расшифровка_Сумма</dcsat:name>");
        // groupTemplate
        assertThat(content).contains("<groupTemplate>");
        assertThat(content).contains("<groupField>Счет</groupField>");
    }

    /** Тест 12: расширенные роли полей с key-value. */
    @Test
    void testFieldRolesWithKv() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Н",
                    "query": "ВЫБРАТЬ 1",
                    "fields": [
                      { "field": "Сумма", "type": "decimal(15,2)", "role": "@resource" },
                      { "field": "Период", "type": "date", "role": "@period" },
                      { "field": "Остаток", "type": "decimal(15,2)", "role": "@balance",
                        "roleAttributes": { "balanceGroupName": "ОстаткиСчета" } }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<role>");
        // resource → ignoreNullValues
        assertThat(content).contains("<dcscom:ignoreNullValues");
        assertThat(content).contains(">true</dcscom:ignoreNullValues>");
        // period
        assertThat(content).contains("<dcscom:periodNumber");
        assertThat(content).contains(">1</dcscom:periodNumber>");
        assertThat(content).contains("<dcscom:periodType");
        assertThat(content).contains(">Main</dcscom:periodType>");
        // balance + kv
        assertThat(content).contains("<dcscom:balance");
        assertThat(content).contains(">true</dcscom:balance>");
        assertThat(content).contains("<dcscom:balanceGroupName>ОстаткиСчета</dcscom:balanceGroupName>");
    }

    /** Тест 13: флаги параметров — @hidden + valueListAllowed + use=Always. */
    @Test
    void testParameterFlags() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Н",
                    "query": "ВЫБРАТЬ 1",
                    "fields": [{ "field": "Сумма", "appearance": { "Формат": "ЧДЦ=2" } }]
                  }],
                  "parameters": [
                    { "name": "Скрытый", "type": "string(50)", "hidden": true },
                    { "name": "Список", "type": "string(50)", "valueListAllowed": true },
                    { "name": "Всегда", "type": "string(50)", "use": "Always" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<availableAsField>false</availableAsField>");
        assertThat(content).contains("<valueListAllowed>true</valueListAllowed>");
        assertThat(content).contains("<use>Always</use>");
    }

    @Test
    void testStandardPeriodParameterValueUsesStructuredVariant() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "parameters": [
                    { "name": "Период", "type": "StandardPeriod", "value": "LastMonth" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<value xsi:type=\"v8:StandardPeriod\">");
        assertThat(content).contains("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">LastMonth</v8:variant>");
    }

    /** Тест 14: availableValues с representation. */
    @Test
    void testAvailableValues() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "parameters": [{
                    "name": "Округление",
                    "type": "string(50)",
                    "availableValues": [
                      { "value": "Окр1", "presentation": "руб." },
                      { "value": "Окр1000", "presentation": "тыс. руб" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<availableValues>");
        assertThat(content).contains("<value>Окр1</value>");
        assertThat(content).contains("<presentation>руб.</presentation>");
        assertThat(content).contains("<presentation>тыс. руб</presentation>");
    }

    /** Тест 15: {@code @file:}-include в query. */
    @Test
    void testFileIncludeInQuery() throws Exception {
        Path sqlFile = tempDir.resolve("queries").resolve("sales.sql");
        Files.createDirectories(sqlFile.getParent());
        Files.writeString(sqlFile, "ВЫБРАТЬ * ИЗ Документ.Реализация");

        Path jsonFile = tempDir.resolve("input.json");
        String json = """
                {
                  "dataSets": [{
                    "type": "query", "name": "Продажи",
                    "query": "@queries/sales.sql"
                  }]
                }
                """;
        Files.writeString(jsonFile, json);

        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(jsonFile.toFile(), SkdDsl.class);

        Path outputXml = tempDir.resolve("Template.xml");
        new SkdWriter(OutputFormat.DESIGNER)
                .withIncludeBase(jsonFile.getParent())
                .create(dsl, outputXml);

        String content = Files.readString(outputXml);
        assertThat(content).contains("<query>ВЫБРАТЬ * ИЗ Документ.Реализация</query>");
    }

    /** Тест 16: dataSetLinks — связь двух наборов. */
    @Test
    void testDataSetLinks() throws Exception {
        String json = """
                {
                  "dataSets": [
                    { "type": "query", "name": "Основной", "query": "ВЫБРАТЬ 1" },
                    { "type": "query", "name": "Доп", "query": "ВЫБРАТЬ 2" }
                  ],
                  "dataSetLinks": [
                    { "source": "Основной", "dest": "Доп",
                      "sourceExpression": "Контрагент", "destExpression": "Контрагент" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<dataSetLink>");
        assertThat(content).contains("<sourceDataSet>Основной</sourceDataSet>");
        // TASK-171 (Р-5): платформенный элемент — destinationDataSet, а не destDataSet.
        assertThat(content).contains("<destinationDataSet>Доп</destinationDataSet>");
        assertThat(content).contains("<sourceExpression>Контрагент</sourceExpression>");
        //++agent TASK-174 [07.06.2026 11:40:00]
        // Платформенный элемент — destinationExpression (44 вхождения в каноне GBIG PAM),
        // а не destExpression (имя ключа DSL не равно имени XML-узла).
        assertThat(content).contains("<destinationExpression>Контрагент</destinationExpression>");
        assertThat(content).doesNotContain("<destExpression>");
        // Порядок верхнего уровня (1c-dcs-spec.md §2): dataSetLink идёт сразу после dataSet,
        // ДО settingsVariant.
        assertThat(content.indexOf("<dataSetLink>")).isLessThan(content.indexOf("<settingsVariant>"));
        //++agent TASK-174
    }

    /** Тест 17: presentationExpression. */
    @Test
    void testPresentationExpression() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1",
                    "fields": [
                      { "field": "Контрагент", "type": "string(150)",
                        "presentationExpression": "Контрагент.Наименование" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<presentationExpression>Контрагент.Наименование</presentationExpression>");
    }

    /** Тест 18: conditionalAppearance с группой Or/And. */
    @Test
    void testConditionalAppearanceFilterGroup() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "conditionalAppearance": [{
                        "selection": ["Сумма"],
                        "filterGroup": {
                          "group": "Or",
                          "items": [
                            { "group": "And", "items": [
                              { "field": "Статус", "op": "=", "value": "Активен" },
                              { "field": "Сумма",  "op": ">", "value": "1000" }
                            ]},
                            { "field": "Количество", "op": "filled" }
                          ]
                        },
                        "appearance": { "ЦветТекста": "web:Red" },
                        "presentation": "Тест"
                      }]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("FilterItemGroup");
        assertThat(content).contains("<dcsset:groupType>GroupOr</dcsset:groupType>");
        assertThat(content).contains("<dcsset:groupType>GroupAnd</dcsset:groupType>");
    }

    /** Тест 19: составной тип через массив. */
    @Test
    void testCompositeType() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1",
                    "fields": [
                      { "field": "Объект", "type": ["CatalogRef.А", "CatalogRef.Б"] }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        // Оба типа отрисованы в одном valueType.
        assertThat(content).contains("CatalogRef.А");
        assertThat(content).contains("CatalogRef.Б");
        assertThat(content).contains("xmlns:d5p1=\"http://v8.1c.ru/8.1/data/enterprise/current-config\"");
    }

    @Test
    void testRawTemplateDslEmitsAreaTemplateSubtree() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "templates": [{
                    "name": "Макет1",
                    "template": "<template xmlns:dcsat=\\"http://v8.1c.ru/8.1/data-composition-system/area-template\\" xmlns:dcscor=\\"http://v8.1c.ru/8.1/data-composition-system/core\\" xsi:type=\\"dcsat:AreaTemplate\\"><dcsat:item xsi:type=\\"dcsat:TableRow\\"><dcsat:tableCell><dcsat:item xsi:type=\\"dcsat:Field\\"><dcsat:value xsi:type=\\"dcscor:Parameter\\">Сумма</dcsat:value></dcsat:item></dcsat:tableCell></dcsat:item></template>",
                    "parameters": [
                      { "name": "Сумма", "expression": "Представление(Сумма)" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).doesNotContain("<rawTemplate>");
        assertThat(content).doesNotContain("&lt;template");
        assertThat(content).contains("xsi:type=\"dcsat:AreaTemplate\"");
        assertThat(content).contains("<dcsat:item");
        assertThat(content).contains("xsi:type=\"dcsat:TableRow\"");
        assertThat(content).contains("<dcsat:name>Сумма</dcsat:name>");
        assertThat(content).contains("<dcsat:expression>Представление(Сумма)</dcsat:expression>");
    }

    @Test
    void testGroupHeaderTemplateUsesCanonicalGroupTemplateElement() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "groupTemplates": [
                    { "groupField": "Счет", "templateType": "GroupHeader", "template": "Макет1" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<groupTemplate>");
        assertThat(content).contains("<templateType>GroupHeader</templateType>");
        assertThat(content).doesNotContain("<groupHeaderTemplate>");
    }

    /** Тест 20: NonNegative qualifier применяется. */
    @Test
    void testDecimalNonNegativeQualifier() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1",
                    "fields": [
                      { "field": "Сумма", "type": "decimal(15,2),nonneg" }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<v8:AllowedSign>Nonnegative</v8:AllowedSign>");
    }

    /** Тест 21: e2e — quickstart из skill. */
    @Test
    void testE2eQuickstart() throws Exception {
        String json = """
                {
                  "name": "Продажи",
                  "dataSets": [{
                    "type": "query",
                    "name": "Основной",
                    "query": "ВЫБРАТЬ Организация, Номенклатура, Количество, Сумма ИЗ Продажи",
                    "fields": [
                      { "field": "Организация", "type": "CatalogRef.Организации", "role": "@dimension" },
                      { "field": "Номенклатура", "type": "CatalogRef.Номенклатура", "role": "@dimension" },
                      { "field": "Количество", "type": "decimal(15,3)" },
                      { "field": "Сумма", "type": "decimal(15,2)", "role": "@resource" }
                    ]
                  }],
                  "totalFields": [
                    { "dataPath": "Количество", "expression": "Сумма(Количество)" },
                    { "dataPath": "Сумма", "expression": "Сумма(Сумма)" }
                  ],
                  "parameters": [
                    { "name": "Период", "type": "string(50)", "autoDates": true }
                  ],
                  "settingsVariants": [{
                    "name": "Основной",
                    "presentation": "Продажи по организациям",
                    "settings": {
                      "selection": ["Организация", "Номенклатура", "Количество", "Сумма"],
                      "filter": ["Организация = _"]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        // Базовая структура.
        assertThat(content).contains("<DataCompositionSchema");
        assertThat(content).contains("<dataSet xsi:type=\"DataSetQuery\">");
        assertThat(content).contains("<role>");
        assertThat(content).contains("<totalField>");
        assertThat(content).contains("<parameter>");
        //**agent TASK-174 [07.06.2026 11:40:00]
        //// autoDates → производные параметры.
        //assertThat(content).contains("ДатаНачала_Период");
        //assertThat(content).contains("ДатаОкончания_Период");
        // autoDates → производные параметры ПО КАНОНУ (skd-dsl-spec.md §6 + _ДемоФайлы):
        // имена ДатаНачала/ДатаОкончания, вычисление через expression, без <dataPath>.
        assertThat(content).contains("<name>ДатаНачала</name>");
        assertThat(content).contains("<name>ДатаОкончания</name>");
        assertThat(content).contains("<expression>&amp;Период.ДатаНачала</expression>");
        assertThat(content).contains("<expression>&amp;Период.ДатаОкончания</expression>");
        assertThat(content).doesNotContain("<dataPath>Период.НачалоПериода</dataPath>");
        //**agent TASK-174
        assertThat(content).contains("<settingsVariant>");
    }

    @Test
    void task174_xg27_dataSetLinkParameterAndCalculatedFieldTypeIsOptional() throws Exception {
        String json = """
                {
                  "dataSets": [
                    { "type": "query", "name": "Периоды", "query": "ВЫБРАТЬ 1" },
                    { "type": "query", "name": "Данные", "query": "ВЫБРАТЬ 2" }
                  ],
                  "dataSetLinks": [
                    { "source": "Периоды", "dest": "Данные",
                      "sourceExpr": "Месяц", "destExpr": "Месяц",
                      "parameter": "НачалоМесяца", "parameterListAllowed": false }
                  ],
                  "calculatedFields": [
                    { "name": "Маржа", "expression": "Цена - Закупка" }
                  ]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<parameter>НачалоМесяца</parameter>");
        assertThat(content).contains("<parameterListAllowed>false</parameterListAllowed>");

        int calcStart = content.indexOf("<calculatedField>");
        int calcEnd = content.indexOf("</calculatedField>", calcStart);
        assertThat(content.substring(calcStart, calcEnd)).doesNotContain("<valueType>");
    }

    @Test
    void task174_xg27_settingsAndStructureKeysAreNotSilentlyDropped() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Н",
                    "query": "ВЫБРАТЬ 1",
                    "fields": [{ "field": "Сумма", "appearance": { "Формат": "ЧДЦ=2" } }]
                  }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "selection": ["Auto"],
                      "order": ["Auto"],
                      "outputParameters": { "Заголовок": "Отчет" },
                      "dataParameters": ["Период = LastMonth @user"],
                      "structure": [{
                        "name": "ГруппаОрг",
                        "groupFields": ["Организация"],
                        "selection": ["Auto"],
                        "order": ["Auto"],
                        "filter": ["Сумма > 0"],
                        "outputParameters": { "ВыводитьЗаголовок": "Auto" }
                      }]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("xsi:type=\"dcsset:SelectedItemAuto\"");
        assertThat(content).contains("xsi:type=\"dcsset:OrderItemAuto\"");
        assertThat(content).contains("<appearance>");
        assertThat(content).contains("<dcscor:value xsi:type=\"xs:string\">ЧДЦ=2</dcscor:value>");
        assertThat(content).contains("<dcsset:outputParameters>");
        assertThat(content).contains("<dcscor:parameter>Заголовок</dcscor:parameter>");
        assertThat(content).contains("<dcsset:dataParameters>");
        assertThat(content).contains("<dcscor:parameter>Период</dcscor:parameter>");
        assertThat(content).contains("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">LastMonth</v8:variant>");
        assertThat(content).contains("<dcsset:name>ГруппаОрг</dcsset:name>");
        assertThat(content).contains("<dcsset:comparisonType>Greater</dcsset:comparisonType>");
        assertThat(content).contains("<dcscor:parameter>ВыводитьЗаголовок</dcscor:parameter>");
    }

    @Test
    void task174_xg27_shorthandStringsCompile() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Продажи",
                    "query": "ВЫБРАТЬ 1",
                    "fields": ["Количество [Кол-во]: decimal(15,3) @resource #noOrder"]
                  }],
                  "calculatedFields": ["Маржа [Маржа]: decimal(15,2) = Цена - Закупка #noFilter"],
                  "totalFields": ["Количество: Сумма"],
                  "parameters": ["Период [Период]: StandardPeriod = LastMonth @autoDates"],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": ["Организация > details"]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        assertThat(content).contains("<dataPath>Количество</dataPath>");
        assertThat(content).contains("<v8:content>Кол-во</v8:content>");
        assertThat(content).contains("<dcscom:ignoreNullValues");
        assertThat(content).contains(">true</dcscom:ignoreNullValues>");
        assertThat(content).contains("<order>true</order>");
        assertThat(content).contains("<dataPath>Маржа</dataPath>");
        assertThat(content).contains("<expression>Цена - Закупка</expression>");
        assertThat(content).contains("<condition>true</condition>");
        assertThat(content).contains("<expression>Сумма(Количество)</expression>");
        assertThat(content).contains("<name>Период</name>");
        assertThat(content).contains("<name>ДатаНачала</name>");
        assertThat(content).contains("<dcsset:field>Организация</dcsset:field>");
    }

    @Test
    void task174_xg27_formatAppearanceUsesStringType() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "conditionalAppearance": [{
                        "appearance": { "Формат": "ЧДЦ=2", "Текст": "Не указано" }
                      }]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        int format = content.indexOf("<dcscor:parameter>Формат</dcscor:parameter>");
        int text = content.indexOf("<dcscor:parameter>Текст</dcscor:parameter>");
        assertThat(content.substring(format, text)).contains("<dcscor:value xsi:type=\"xs:string\">ЧДЦ=2</dcscor:value>");
        assertThat(content.substring(text)).contains("<dcscor:value xsi:type=\"v8:LocalStringType\">");
    }

    @Test
    void skdXg174001_unionItemsUseCanonicalItemElements() throws Exception {
        String json = """
                {
                  "dataSets": [{
                    "type": "union",
                    "name": "Все",
                    "fields": [{ "field": "Х", "type": "decimal(10,0)" }],
                    "items": [
                      { "type": "query", "name": "A", "query": "ВЫБРАТЬ 1 КАК Х",
                        "fields": [{ "field": "Х", "type": "decimal(10,0)" }] },
                      { "type": "query", "name": "B", "query": "ВЫБРАТЬ 2 КАК Х",
                        "fields": [{ "field": "Х", "type": "decimal(10,0)" }] }
                    ]
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);

        int unionStart = content.indexOf("<dataSet xsi:type=\"DataSetUnion\">");
        int unionEnd = content.indexOf("</dataSet>", unionStart);
        String unionBlock = content.substring(unionStart, unionEnd + "</dataSet>".length());

        assertThat(countOccurrences(unionBlock, "<item xsi:type=\"DataSetQuery\">")).isEqualTo(2);
        assertThat(unionBlock).doesNotContain("<dataSet xsi:type=\"DataSetQuery\">");
        assertThat(unionBlock).contains("<field xsi:type=\"DataSetFieldField\">");
    }

    @Test
    void skdXg174002_filterFlagsAreMetadataNotRightValue() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "filter": [
                        "Организация = _ @off @user @quickAccess",
                        "Статус notContains X @normal",
                        "Код beginsWith ABC",
                        "Имя notBeginsWith Z"
                      ]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);

        String organization = enclosingDcssetItemContaining(content,
                "<dcsset:left xsi:type=\"dcscor:Field\">Организация</dcsset:left>");
        assertThat(organization).contains("<dcsset:use>false</dcsset:use>");
        assertThat(organization).contains("<dcsset:comparisonType>Equal</dcsset:comparisonType>");
        assertThat(organization).contains("<dcsset:viewMode>QuickAccess</dcsset:viewMode>");
        assertThat(organization).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");
        assertThat(organization).doesNotContain("<dcsset:right");
        assertThat(organization).doesNotContain("@off");

        String status = enclosingDcssetItemContaining(content,
                "<dcsset:left xsi:type=\"dcscor:Field\">Статус</dcsset:left>");
        assertThat(status).contains("<dcsset:comparisonType>NotContains</dcsset:comparisonType>");
        assertThat(status).contains("<dcsset:right xsi:type=\"xs:string\">X</dcsset:right>");
        assertThat(status).contains("<dcsset:viewMode>Normal</dcsset:viewMode>");
        assertThat(content).contains("<dcsset:comparisonType>BeginsWith</dcsset:comparisonType>");
        assertThat(content).contains("<dcsset:comparisonType>NotBeginsWith</dcsset:comparisonType>");
    }

    @Test
    void skdXg174003_dataParameterFlagsAndVariantObjectAreWritten() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "dataParameters": [
                        "Период = LastMonth @user @quickAccess",
                        "Организация @off @user @normal",
                        { "parameter": "Период2",
                          "value": { "variant": "ThisMonth" },
                          "userSettingID": "auto",
                          "viewMode": "Inaccessible",
                          "userSettingPresentation": "Период 2" }
                      ]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);

        String period = enclosingDcscorItemContaining(content,
                "<dcscor:parameter>Период</dcscor:parameter>");
        assertThat(period).contains("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">LastMonth</v8:variant>");
        assertThat(period).contains("<dcsset:viewMode>QuickAccess</dcsset:viewMode>");
        assertThat(period).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");

        String organization = enclosingDcscorItemContaining(content,
                "<dcscor:parameter>Организация</dcscor:parameter>");
        assertThat(organization).contains("<dcscor:use>false</dcscor:use>");
        assertThat(organization).contains("<dcsset:viewMode>Normal</dcsset:viewMode>");
        assertThat(organization).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");

        String period2 = enclosingDcscorItemContaining(content,
                "<dcscor:parameter>Период2</dcscor:parameter>");
        assertThat(period2).contains("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">ThisMonth</v8:variant>");
        assertThat(period2).contains("<dcsset:viewMode>Inaccessible</dcsset:viewMode>");
        assertThat(period2).contains("<dcsset:userSettingPresentation xsi:type=\"v8:LocalStringType\">");
        assertThat(period2).contains("<v8:content>Период 2</v8:content>");
    }

    @Test
    void skdXg174004_dataParametersAutoExportsVisibleSchemaParameters() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "parameters": [
                    { "name": "Период", "type": "StandardPeriod", "value": "LastMonth" },
                    { "name": "БезЗначения", "type": "string(50)" },
                    { "name": "Скрытый", "type": "string(50)", "hidden": true }
                  ],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": { "dataParameters": "auto" }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);
        String dataParameters = content.substring(
                content.indexOf("<dcsset:dataParameters>"),
                content.indexOf("</dcsset:dataParameters>") + "</dcsset:dataParameters>".length());

        String period = enclosingDcscorItemContaining(dataParameters,
                "<dcscor:parameter>Период</dcscor:parameter>");
        assertThat(period).contains("<v8:variant xsi:type=\"v8:StandardPeriodVariant\">LastMonth</v8:variant>");
        assertThat(period).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");

        String withoutValue = enclosingDcscorItemContaining(dataParameters,
                "<dcscor:parameter>БезЗначения</dcscor:parameter>");
        assertThat(withoutValue).contains("<dcscor:use>false</dcscor:use>");
        assertThat(withoutValue).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");
        assertThat(dataParameters).doesNotContain("<dcscor:parameter>Скрытый</dcscor:parameter>");
    }

    @Test
    void skdXg174005_filterObjectFormKeepsTypedValueAndUserSettings() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "filter": [
                        { "field": "Дата", "op": ">=", "value": "0001-01-01T00:00:00",
                          "valueType": "xs:dateTime", "use": false,
                          "presentation": "Дата с",
                          "viewMode": "Normal", "userSettingID": "auto",
                          "userSettingPresentation": "Дата пользователя" },
                        { "group": "Or", "items": [
                          { "field": "Статус", "op": "=", "value": true, "valueType": "xs:boolean" },
                          { "field": "Пометка", "op": "filled" }
                        ]}
                      ]
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);

        String date = enclosingDcssetItemContaining(content,
                "<dcsset:left xsi:type=\"dcscor:Field\">Дата</dcsset:left>");
        assertThat(date).contains("<dcsset:use>false</dcsset:use>");
        assertThat(date).contains("<dcsset:comparisonType>GreaterOrEqual</dcsset:comparisonType>");
        assertThat(date).contains("<dcsset:right xsi:type=\"xs:dateTime\">0001-01-01T00:00:00</dcsset:right>");
        assertThat(date).contains("<dcsset:presentation xsi:type=\"v8:LocalStringType\">");
        assertThat(date).contains("<v8:content>Дата с</v8:content>");
        assertThat(date).contains("<dcsset:viewMode>Normal</dcsset:viewMode>");
        assertThat(date).containsPattern("<dcsset:userSettingID>[0-9a-f-]{36}</dcsset:userSettingID>");
        assertThat(date).contains("<dcsset:userSettingPresentation xsi:type=\"v8:LocalStringType\">");
        assertThat(date).contains("<v8:content>Дата пользователя</v8:content>");

        assertThat(content).contains("<dcsset:groupType>GroupOr</dcsset:groupType>");
        assertThat(content).contains("<dcsset:right xsi:type=\"xs:boolean\">true</dcsset:right>");
        assertThat(content).contains("<dcsset:comparisonType>Filled</dcsset:comparisonType>");
    }

    @Test
    void skdXg174006_outputParameterEnumsUseCanonicalTypes() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "outputParameters": {
                        "ВыводитьЗаголовок": "Auto",
                        "ВыводитьПараметрыДанных": "Output",
                        "ВыводитьОтбор": "DontOutput",
                        "РасположениеПолейГруппировки": "Together",
                        "РасположениеРеквизитов": "Separately",
                        "ГоризонтальноеРасположениеОбщихИтогов": "End",
                        "ВертикальноеРасположениеОбщихИтогов": "Begin"
                      }
                    }
                  }]
                }
                """;
        Path out = compile(json);
        String content = Files.readString(out);

        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>ВыводитьЗаголовок</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcsset:DataCompositionTextOutputType\">Auto</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>ВыводитьПараметрыДанных</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcsset:DataCompositionTextOutputType\">Output</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>ВыводитьОтбор</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcsset:DataCompositionTextOutputType\">DontOutput</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>РасположениеПолейГруппировки</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcsset:DataCompositionGroupFieldsPlacement\">Together</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>РасположениеРеквизитов</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcsset:DataCompositionAttributesPlacement\">Separately</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>ГоризонтальноеРасположениеОбщихИтогов</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcscor:DataCompositionTotalPlacement\">End</dcscor:value>");
        assertThat(enclosingDcscorItemContaining(content,
                "<dcscor:parameter>ВертикальноеРасположениеОбщихИтогов</dcscor:parameter>"))
                .contains("<dcscor:value xsi:type=\"dcscor:DataCompositionTotalPlacement\">Begin</dcscor:value>");
    }

    // ---- helpers ---------------------------------------------------------

    private Path compile(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        Path outputXml = tempDir.resolve("Template_" + System.nanoTime() + ".xml");
        new SkdWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return outputXml;
    }

    private static String enclosingDcssetItemContaining(String content, String needle) {
        return enclosingItemContaining(content, "<dcsset:item", "</dcsset:item>", needle);
    }

    private static String enclosingDcscorItemContaining(String content, String needle) {
        return enclosingItemContaining(content, "<dcscor:item", "</dcscor:item>", needle);
    }

    private static String enclosingItemContaining(String content, String itemStartPrefix,
                                                  String itemEndTag, String needle) {
        int needleAt = content.indexOf(needle);
        assertThat(needleAt).as("needle exists: " + needle).isGreaterThanOrEqualTo(0);
        int start = content.lastIndexOf(itemStartPrefix, needleAt);
        int end = content.indexOf(itemEndTag, needleAt);
        assertThat(start).as("item start exists for: " + needle).isGreaterThanOrEqualTo(0);
        assertThat(end).as("item end exists for: " + needle).isGreaterThanOrEqualTo(0);
        return content.substring(start, end + itemEndTag.length());
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = content.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }
}

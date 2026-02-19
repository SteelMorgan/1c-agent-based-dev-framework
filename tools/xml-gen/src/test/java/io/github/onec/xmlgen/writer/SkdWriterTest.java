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
        
        // БЕЗ BOM
        byte[] bytes = Files.readAllBytes(outputXml);
        assertThat(bytes[0]).isNotEqualTo((byte) 0xEF);
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
        assertThat(content).contains("<dcsset:structure>");
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
}

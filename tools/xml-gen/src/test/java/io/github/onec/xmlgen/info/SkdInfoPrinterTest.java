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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for SkdInfoPrinter — 11 modes + filter tests.
 */
class SkdInfoPrinterTest {

    private final SkdInfoPrinter printer = new SkdInfoPrinter();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== Shared fixture ====================

    /**
     * Writes a rich SKD fixture XML with:
     * - 2 dataSources
     * - 2 dataSets (Query + Union with 2 Query children)
     * - 2 calculatedFields
     * - 2 totalFields (resources)
     * - 2 dataSetLinks
     * - 2 parameters (1 visible, 1 hidden)
     * - 1 template + 1 groupTemplate
     * - 2 settingsVariants
     */
    private Path writeFullFixture() throws Exception {
        Path file = tempDir.resolve("Template.xml");
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                // dataSources
                + "  <dataSource>\n"
                + "    <name>DS1</name>\n"
                + "    <dataSourceType>Local</dataSourceType>\n"
                + "  </dataSource>\n"
                + "  <dataSource>\n"
                + "    <name>DS2</name>\n"
                + "    <dataSourceType>Remote</dataSourceType>\n"
                + "  </dataSource>\n"
                // DataSet Query: Продажи
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>Продажи</name>\n"
                + "    <query>ВЫБРАТЬ\n  Сумма,\n  Период\nИЗ\n  РегистрНакопления.Продажи</query>\n"
                + "    <field>\n"
                + "      <dataPath>Сумма</dataPath>\n"
                + "      <title><item><lang>ru</lang><content>Сумма продажи</content></item></title>\n"
                + "      <valueType><Type>xs:decimal</Type></valueType>\n"
                + "      <role><isResource>true</isResource></role>\n"
                + "    </field>\n"
                + "    <field>\n"
                + "      <dataPath>Период</dataPath>\n"
                + "      <title><item><lang>ru</lang><content>Период</content></item></title>\n"
                + "      <valueType><Type>xs:dateTime</Type></valueType>\n"
                + "      <role><isPeriod>true</isPeriod></role>\n"
                + "    </field>\n"
                + "  </dataSet>\n"
                // DataSet Union: ОбъединенныйНабор
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetUnion\">\n"
                + "    <name>ОбъединенныйНабор</name>\n"
                + "    <item xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "      <name>Номенклатура</name>\n"
                + "      <query>ВЫБРАТЬ Наименование ИЗ Справочник.Номенклатура</query>\n"
                + "      <field><dataPath>Наименование</dataPath></field>\n"
                + "    </item>\n"
                + "    <item xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "      <name>Контрагенты</name>\n"
                + "      <query>ВЫБРАТЬ Наименование ИЗ Справочник.Контрагенты</query>\n"
                + "      <field><dataPath>Наименование</dataPath></field>\n"
                + "    </item>\n"
                + "  </dataSet>\n"
                // calculatedFields
                + "  <calculatedField>\n"
                + "    <name>СуммаСНДС</name>\n"
                + "    <title><item><lang>ru</lang><content>Сумма с НДС</content></item></title>\n"
                + "    <expression>Сумма * 1.2</expression>\n"
                + "    <valueType><Type>xs:decimal</Type></valueType>\n"
                + "  </calculatedField>\n"
                + "  <calculatedField>\n"
                + "    <name>ПроцентВыполнения</name>\n"
                + "    <title><item><lang>ru</lang><content>Процент выполнения</content></item></title>\n"
                + "    <expression>ВЫБОР КОГДА Сумма > 0 ТОГДА 100 ИНАЧЕ 0 КОНЕЦ</expression>\n"
                + "  </calculatedField>\n"
                // totalFields
                + "  <totalField>\n"
                + "    <dataPath>Сумма</dataPath>\n"
                + "    <expression>Сумма(Сумма)</expression>\n"
                + "  </totalField>\n"
                + "  <totalField>\n"
                + "    <dataPath>СуммаСНДС</dataPath>\n"
                + "    <expression>Сумма(СуммаСНДС)</expression>\n"
                + "    <group>Организация</group>\n"
                + "  </totalField>\n"
                // dataSetLinks
                + "  <dataSetLink>\n"
                + "    <sourceDataSet>Продажи</sourceDataSet>\n"
                + "    <destinationDataSet>Номенклатура</destinationDataSet>\n"
                + "    <sourceExpression>Организация</sourceExpression>\n"
                + "    <destExpression>Организация</destExpression>\n"
                + "  </dataSetLink>\n"
                + "  <dataSetLink>\n"
                + "    <sourceDataSet>Продажи</sourceDataSet>\n"
                + "    <destinationDataSet>Номенклатура</destinationDataSet>\n"
                + "    <sourceExpression>Контрагент</sourceExpression>\n"
                + "    <destExpression>Контрагент</destExpression>\n"
                + "  </dataSetLink>\n"
                // parameters
                + "  <parameter>\n"
                + "    <name>Период</name>\n"
                + "    <title><item><lang>ru</lang><content>Период отчета</content></item></title>\n"
                + "    <valueType><Type>v8:StandardPeriod</Type></valueType>\n"
                + "    <value>LastMonth</value>\n"
                + "  </parameter>\n"
                + "  <parameter>\n"
                + "    <name>ВнутреннийПараметр</name>\n"
                + "    <useRestriction>true</useRestriction>\n"
                + "    <valueType><Type>xs:string</Type></valueType>\n"
                + "  </parameter>\n"
                // template
                + "  <template>\n"
                + "    <name>Макет1</name>\n"
                + "    <template>SpreadsheetDoc</template>\n"
                + "  </template>\n"
                // groupTemplate
                + "  <groupTemplate>\n"
                + "    <groupField>Период</groupField>\n"
                + "    <templateType>Header</templateType>\n"
                + "    <template>Макет1</template>\n"
                + "  </groupTemplate>\n"
                // settingsVariants
                + "  <settingsVariant>\n"
                + "    <name>Основной</name>\n"
                + "    <presentation><item><lang>ru</lang><content>Основной вариант</content></item></presentation>\n"
                + "    <settings>\n"
                + "      <selection>\n"
                + "        <item><field>Сумма</field></item>\n"
                + "        <item><field>СуммаСНДС</field></item>\n"
                + "        <item><field>Период</field></item>\n"
                + "      </selection>\n"
                + "      <filter>\n"
                + "        <item>\n"
                + "          <leftValue>ВАрхиве</leftValue>\n"
                + "          <comparisonType>Equal</comparisonType>\n"
                + "          <rightValue>false</rightValue>\n"
                + "          <use>true</use>\n"
                + "          <presentation><item><lang>ru</lang><content>Исключая архивные</content></item></presentation>\n"
                + "        </item>\n"
                + "      </filter>\n"
                + "      <order>\n"
                + "        <item><field>Период</field><orderType>Asc</orderType></item>\n"
                + "      </order>\n"
                + "    </settings>\n"
                + "  </settingsVariant>\n"
                + "  <settingsVariant>\n"
                + "    <name>ПоКонтрагентам</name>\n"
                + "    <presentation><item><lang>ru</lang><content>По контрагентам</content></item></presentation>\n"
                + "    <settings>\n"
                + "      <selection>\n"
                + "        <item><field>Контрагент</field></item>\n"
                + "        <item><field>Сумма</field></item>\n"
                + "      </selection>\n"
                + "    </settings>\n"
                + "  </settingsVariant>\n"
                + "</DataCompositionSchema>\n";
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    private String runMode(Path file, String mode) throws Exception {
        return runMode(file, mode, null);
    }

    private String runMode(Path file, String mode, String name) throws Exception {
        XmlDocument doc = reader.parse(file);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        printer.print(doc, mode, name, 0, 0, ps);
        return buf.toString(StandardCharsets.UTF_8);
    }

    // ==================== 1. overview ====================

    @Test
    void mode_overview_showsDatasets() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "overview");

        assertThat(out).contains("=== DCS:");
        assertThat(out).contains("Datasets:");
        assertThat(out).contains("[Query]");
        assertThat(out).contains("Продажи");
        assertThat(out).contains("[Union]");
        assertThat(out).contains("ОбъединенныйНабор");
        assertThat(out).contains("Calculated: 2");
        assertThat(out).contains("Resources: 2");
        assertThat(out).contains("Params: 2");
        assertThat(out).contains("1 visible");
        assertThat(out).contains("1 hidden");
        assertThat(out).contains("Variants:");
        assertThat(out).contains("Основной");
        assertThat(out).contains("Next:");
    }

    // ==================== 2. query ====================

    @Test
    void mode_query_showsQueryText() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "query");

        assertThat(out).contains("=== Query: Продажи");
        assertThat(out).contains("ВЫБРАТЬ");
        assertThat(out).contains("РегистрНакопления.Продажи");
    }

    @Test
    void mode_query_withDataSetFilter_selectsNamedDataset() throws Exception {
        Path file = writeFullFixture();
        // Filter by dataset name using --dataSet equivalent (name param)
        String out = runMode(file, "query", "Номенклатура");

        assertThat(out).contains("=== Query: Номенклатура");
        assertThat(out).contains("Справочник.Номенклатура");
        assertThat(out).doesNotContain("РегистрНакопления.Продажи");
    }

    // ==================== 3. fields ====================

    @Test
    void mode_fields_showsFieldMap() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "fields");

        assertThat(out).contains("=== Fields map ===");
        assertThat(out).contains("Продажи [Query]");
        assertThat(out).contains("Сумма");
        assertThat(out).contains("Период");
    }

    @Test
    void mode_fields_withName_showsDetail() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "fields", "Сумма");

        assertThat(out).contains("=== Field: Сумма");
        assertThat(out).contains("Dataset: Продажи");
        assertThat(out).contains("Number");
        assertThat(out).contains("Role:");
    }

    // ==================== 4. links ====================

    @Test
    void mode_links_showsDataSetLinks() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "links");

        assertThat(out).contains("=== Links (2) ===");
        assertThat(out).contains("Продажи -> Номенклатура");
        assertThat(out).contains("Организация");
        assertThat(out).contains("Контрагент");
    }

    // ==================== 5. calculated ====================

    @Test
    void mode_calculated_showsList() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "calculated");

        assertThat(out).contains("=== Calculated fields (2) ===");
        assertThat(out).contains("СуммаСНДС");
        assertThat(out).contains("ПроцентВыполнения");
    }

    @Test
    void mode_calculated_withName_showsExpression() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "calculated", "СуммаСНДС");

        assertThat(out).contains("=== Calculated: СуммаСНДС ===");
        assertThat(out).contains("Expression:");
        assertThat(out).contains("Сумма * 1.2");
        assertThat(out).contains("Number");
    }

    // ==================== 6. resources ====================

    @Test
    void mode_resources_showsList() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "resources");

        assertThat(out).contains("=== Resources (2) ===");
        assertThat(out).contains("Сумма");
        assertThat(out).contains("СуммаСНДС");
        // СуммаСНДС has a group formula
        assertThat(out).contains("*");
        assertThat(out).contains("* = has group-level formulas");
    }

    @Test
    void mode_resources_withName_showsAggregation() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "resources", "Сумма");

        assertThat(out).contains("=== Resource: Сумма ===");
        assertThat(out).contains("Сумма(Сумма)");
    }

    // ==================== 7. params ====================

    @Test
    void mode_params_showsParameters() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "params");

        assertThat(out).contains("=== Parameters (2) ===");
        assertThat(out).contains("Период");
        assertThat(out).contains("StandardPeriod");
        assertThat(out).contains("LastMonth");
        assertThat(out).contains("ВнутреннийПараметр");
        assertThat(out).contains("hidden");
    }

    // ==================== 8. variant ====================

    @Test
    void mode_variant_showsList() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "variant");

        assertThat(out).contains("=== Variants (2) ===");
        assertThat(out).contains("[1] Основной");
        assertThat(out).contains("[2] ПоКонтрагентам");
    }

    @Test
    void mode_variant_withName_showsDetail() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "variant", "Основной");

        assertThat(out).contains("=== Variant [1]: Основной");
        assertThat(out).contains("Filter:");
        assertThat(out).contains("ВАрхиве");
        assertThat(out).contains("Selection:");
        assertThat(out).contains("Order:");
        assertThat(out).contains("Период");
    }

    @Test
    void mode_variant_withIndex_showsDetail() throws Exception {
        Path file = writeFullFixture();
        // Access by numeric index
        String out = runMode(file, "variant", "2");

        assertThat(out).contains("=== Variant [2]: ПоКонтрагентам");
        assertThat(out).contains("Контрагент");
    }

    // ==================== 9. templates ====================

    @Test
    void mode_templates_showsTemplateList() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "templates");

        assertThat(out).contains("=== Templates (");
        assertThat(out).contains("Макет1");
        assertThat(out).contains("Group bindings");
        assertThat(out).contains("Период");
        assertThat(out).contains("Header");
    }

    @Test
    void mode_templates_withName_showsGroupTemplate() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "templates", "Период");

        assertThat(out).contains("=== Templates: Период ===");
        assertThat(out).contains("Header");
        assertThat(out).contains("Макет1");
    }

    // ==================== 10. trace ====================

    @Test
    void mode_trace_forField_showsChain() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "trace", "Сумма");

        assertThat(out).contains("=== Trace: Сумма ===");
        assertThat(out).contains("Dataset: Продажи");
        // Should show the calculated field that references Сумма
        assertThat(out).contains("СуммаСНДС");
        // Should show the total
        assertThat(out).contains("Сумма(Сумма)");
        // Should show variant reference
        assertThat(out).contains("Основной");
    }

    @Test
    void mode_trace_withoutName_showsUsage() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "trace", null);

        assertThat(out).contains("Usage:");
    }

    @Test
    void mode_trace_forNonexistentField_showsNotFound() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "trace", "НесуществующееПоле");

        assertThat(out).contains("not found");
    }

    // ==================== 11. full ====================

    @Test
    void mode_full_includesAllSections() throws Exception {
        Path file = writeFullFixture();
        String out = runMode(file, "full");

        // Should contain sections from multiple modes
        assertThat(out).contains("=== DCS:");       // overview
        assertThat(out).contains("=== Query:");      // query
        assertThat(out).contains("=== Fields map"); // fields
        assertThat(out).contains("=== Links");      // links
        assertThat(out).contains("=== Calculated"); // calculated
        assertThat(out).contains("=== Resources");  // resources
        assertThat(out).contains("=== Parameters"); // params
        assertThat(out).contains("=== Variants");   // variant
    }

    // ==================== Filter tests ====================

    @Test
    void dataSetFilter_forQuery_selectsSpecificDataset() throws Exception {
        Path file = writeFullFixture();
        // Simulate --dataSet Контрагенты
        String out = runMode(file, "query", "Контрагенты");

        assertThat(out).contains("=== Query: Контрагенты");
        assertThat(out).contains("Справочник.Контрагенты");
        assertThat(out).doesNotContain("РегистрНакопления.Продажи");
    }

    @Test
    void variantFilter_forVariant_selectsSpecificVariant() throws Exception {
        Path file = writeFullFixture();
        // Simulate --variant ПоКонтрагентам
        String out = runMode(file, "variant", "ПоКонтрагентам");

        assertThat(out).contains("=== Variant [2]: ПоКонтрагентам");
        assertThat(out).doesNotContain("Основной вариант");
    }

    // ==================== Unknown mode ====================

    @Test
    void unknownMode_throwsIllegalArgument() throws Exception {
        Path file = writeFullFixture();
        XmlDocument doc = reader.parse(file);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> printer.print(doc, "nonexistent", null, 0, 0, ps))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown skd info mode");
    }

    // ==================== Empty schema ====================

    @Test
    void mode_links_emptySchema_showsEmpty() throws Exception {
        Path file = tempDir.resolve("empty.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n"
                + "</DataCompositionSchema>\n",
                StandardCharsets.UTF_8);

        String out = runMode(file, "links");
        assertThat(out).contains("=== Links (0) ===");
    }

    @Test
    void mode_calculated_emptySchema_showsEmpty() throws Exception {
        Path file = tempDir.resolve("empty2.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n"
                + "</DataCompositionSchema>\n",
                StandardCharsets.UTF_8);

        String out = runMode(file, "calculated");
        assertThat(out).contains("(no calculated fields)");
    }

    @Test
    void mode_resources_emptySchema_showsEmpty() throws Exception {
        Path file = tempDir.resolve("empty3.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n"
                + "</DataCompositionSchema>\n",
                StandardCharsets.UTF_8);

        String out = runMode(file, "resources");
        assertThat(out).contains("(no resources");
    }
}

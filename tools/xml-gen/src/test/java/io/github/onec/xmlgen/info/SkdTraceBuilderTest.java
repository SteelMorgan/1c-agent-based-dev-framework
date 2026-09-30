package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.info.skd.SkdTraceBuilder;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SkdTraceBuilder — graph construction tests.
 */
class SkdTraceBuilderTest {

    private final SkdTraceBuilder builder = new SkdTraceBuilder();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private Path writeSkd(String xml) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    private io.github.onec.xmlgen.validator.XmlNode getRoot(String xml) throws Exception {
        Path file = writeSkd(xml);
        return reader.parse(file).getRoot();
    }

    // ==================== Test 1: Field found in dataset ====================

    @Test
    void build_fieldInDataset_returnsTraceNode() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>Продажи</name>\n"
                + "    <query>ВЫБРАТЬ Сумма ИЗ РН.Продажи</query>\n"
                + "    <field>\n"
                + "      <dataPath>Сумма</dataPath>\n"
                + "      <valueType><Type>xs:decimal</Type></valueType>\n"
                + "      <role><isResource>true</isResource></role>\n"
                + "    </field>\n"
                + "  </dataSet>\n"
                + "  <totalField>\n"
                + "    <dataPath>Сумма</dataPath>\n"
                + "    <expression>Сумма(Сумма)</expression>\n"
                + "  </totalField>\n"
                + "</DataCompositionSchema>\n");

        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "Сумма");

        assertThat(nodes).hasSize(1);
        SkdTraceBuilder.TraceNode tn = nodes.get(0);
        assertThat(tn.dataSetName).isEqualTo("Продажи");
        assertThat(tn.dataSetType).isEqualTo("Query");
        assertThat(tn.fields).hasSize(1);

        SkdTraceBuilder.FieldNode fn = tn.fields.get(0);
        assertThat(fn.fieldName).isEqualTo("Сумма");
        assertThat(fn.totals).hasSize(1);
        assertThat(fn.totals.get(0).expression).isEqualTo("Сумма(Сумма)");
    }

    // ==================== Test 2: Calculated field reference ====================

    @Test
    void build_calcFieldReferencesField_appearInTrace() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>Данные</name>\n"
                + "    <query>ВЫБРАТЬ ОбъемПродаж ИЗ Источник</query>\n"
                + "    <field><dataPath>ОбъемПродаж</dataPath></field>\n"
                + "  </dataSet>\n"
                + "  <calculatedField>\n"
                + "    <name>ОбъемСНДС</name>\n"
                + "    <expression>ОбъемПродаж * 1.2</expression>\n"
                + "  </calculatedField>\n"
                + "  <settingsVariant>\n"
                + "    <name>Основной</name>\n"
                + "    <settings>\n"
                + "      <selection><item><field>ОбъемСНДС</field></item></selection>\n"
                + "    </settings>\n"
                + "  </settingsVariant>\n"
                + "</DataCompositionSchema>\n");

        // Use exact field name to avoid substring matching of calc field name
        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "ОбъемПродаж");

        assertThat(nodes).hasSize(1);
        SkdTraceBuilder.FieldNode fn = nodes.get(0).fields.get(0);

        // CalcField ОбъемСНДС references ОбъемПродаж
        assertThat(fn.calcFields).hasSize(1);
        assertThat(fn.calcFields.get(0).name).isEqualTo("ОбъемСНДС");

        // CalcField should have variant ref
        assertThat(fn.calcFields.get(0).variantRefs).hasSize(1);
        assertThat(fn.calcFields.get(0).variantRefs.get(0).variantName).isEqualTo("Основной");
        assertThat(fn.calcFields.get(0).variantRefs.get(0).kind).isEqualTo("selection");
    }

    // ==================== Test 3: Variant selection reference ====================

    @Test
    void build_fieldInVariantSelection_showsVariantRef() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>ДС1</name>\n"
                + "    <query>ВЫБРАТЬ Период ИЗ Источник</query>\n"
                + "    <field><dataPath>Период</dataPath></field>\n"
                + "  </dataSet>\n"
                + "  <settingsVariant>\n"
                + "    <name>ВариантА</name>\n"
                + "    <settings>\n"
                + "      <order><item><field>Период</field><orderType>Asc</orderType></item></order>\n"
                + "    </settings>\n"
                + "  </settingsVariant>\n"
                + "</DataCompositionSchema>\n");

        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "Период");
        assertThat(nodes).isNotEmpty();

        SkdTraceBuilder.FieldNode fn = nodes.get(0).fields.get(0);
        // Should detect order reference
        assertThat(fn.variantRefs)
                .anyMatch(vr -> vr.variantName.equals("ВариантА") && vr.kind.equals("order"));
    }

    // ==================== Test 4: Field not found ====================

    @Test
    void build_unknownField_returnsEmpty() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>ДС</name>\n"
                + "    <query>ВЫБРАТЬ Поле ИЗ Источник</query>\n"
                + "    <field><dataPath>Поле</dataPath></field>\n"
                + "  </dataSet>\n"
                + "</DataCompositionSchema>\n");

        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "НесуществующееПоле");
        assertThat(nodes).isEmpty();
    }

    // ==================== Test 5: Calculated field itself is searched ====================

    @Test
    void build_calcFieldDirectSearch_findsItAsSchemaLevel() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "  <dataSet xsi:type=\"DataCompositionSchemaDataSetQuery\">\n"
                + "    <name>ДС</name>\n"
                + "    <query>ВЫБРАТЬ Поле ИЗ Источник</query>\n"
                + "    <field><dataPath>Поле</dataPath></field>\n"
                + "  </dataSet>\n"
                + "  <calculatedField>\n"
                + "    <name>КоэффициентКи</name>\n"
                + "    <title><item><lang>ru</lang><content>Коэффициент Ки</content></item></title>\n"
                + "    <expression>КолМесяцевИспользования / КолМесяцевВладения</expression>\n"
                + "  </calculatedField>\n"
                + "  <totalField>\n"
                + "    <dataPath>КоэффициентКи</dataPath>\n"
                + "    <expression>Сумма(КоэффициентКи)</expression>\n"
                + "  </totalField>\n"
                + "</DataCompositionSchema>\n");

        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "КоэффициентКи");

        // Should be found as schema-level calc field
        assertThat(nodes).isNotEmpty();
        boolean foundSchemaLevel = nodes.stream()
                .anyMatch(tn -> "(schema-level)".equals(tn.dataSetName));
        assertThat(foundSchemaLevel).isTrue();

        // The schema-level node should have totals
        SkdTraceBuilder.TraceNode schemaNode = nodes.stream()
                .filter(tn -> "(schema-level)".equals(tn.dataSetName))
                .findFirst().orElseThrow();
        assertThat(schemaNode.fields).hasSize(1);
        assertThat(schemaNode.fields.get(0).totals)
                .anyMatch(t -> t.expression.contains("КоэффициентКи"));
    }

    // ==================== Test 6: Empty query returns empty ====================

    @Test
    void build_emptyQuery_returnsEmpty() throws Exception {
        io.github.onec.xmlgen.validator.XmlNode root = getRoot(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">\n"
                + "</DataCompositionSchema>\n");

        List<SkdTraceBuilder.TraceNode> nodes = builder.build(root, "");
        assertThat(nodes).isEmpty();
    }
}

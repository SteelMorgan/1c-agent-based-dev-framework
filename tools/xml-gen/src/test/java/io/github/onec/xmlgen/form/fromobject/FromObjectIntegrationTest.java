package io.github.onec.xmlgen.form.fromobject;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.FormWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FromObjectIntegrationTest {

    @TempDir
    Path tempDir;

    private Path writeCatalogXml(String content) throws Exception {
        Path objectDir = tempDir.resolve("Catalogs").resolve("Номенклатура");
        Files.createDirectories(objectDir);
        Path objectXml = objectDir.resolve("Номенклатура.xml");
        Files.writeString(objectXml, content);
        Files.createDirectories(objectDir.resolve("Forms").resolve("ФормаЭлемента").resolve("Ext"));
        return objectXml;
    }

    private static final String CATALOG_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" " +
                           "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">\n" +
            "  <Catalog>\n" +
            "    <Properties>\n" +
            "      <Name>Номенклатура</Name>\n" +
            "      <Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>Номенклатура</v8:content></v8:item></Synonym>\n" +
            "      <CodeLength>11</CodeLength>\n" +
            "      <DescriptionLength>150</DescriptionLength>\n" +
            "      <Hierarchical>true</Hierarchical>\n" +
            "      <HierarchyType>HierarchyFoldersAndItems</HierarchyType>\n" +
            "    </Properties>\n" +
            "    <ChildObjects>\n" +
            "      <Attribute>\n" +
            "        <Properties>\n" +
            "          <Name>Артикул</Name>\n" +
            "          <Type><v8:Type>xs:string</v8:Type></Type>\n" +
            "        </Properties>\n" +
            "      </Attribute>\n" +
            "      <Attribute>\n" +
            "        <Properties>\n" +
            "          <Name>Активен</Name>\n" +
            "          <Type><v8:Type>xs:boolean</v8:Type></Type>\n" +
            "        </Properties>\n" +
            "      </Attribute>\n" +
            "    </ChildObjects>\n" +
            "  </Catalog>\n" +
            "</MetaDataObject>\n";

    @Test
    void catalogItemFormGeneratesAndWrites() throws Exception {
        Path objectXml = writeCatalogXml(CATALOG_XML);
        Path outputXml = objectXml.getParent().resolve("Forms").resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml");

        FormFromObjectGenerator gen = new FormFromObjectGenerator();
        FormDsl dsl = gen.generate(null, outputXml, "erp-standard", null);

        assertThat(dsl.getTitle()).isEqualTo("Номенклатура");
        assertThat(dsl.getAttributes()).hasSize(1);
        FormDsl.Attribute objAttr = dsl.getAttributes().get(0);
        assertThat(objAttr.getName()).isEqualTo("Объект");
        assertThat(objAttr.getType()).isEqualTo("CatalogObject.Номенклатура");
        assertThat(objAttr.getMain()).isTrue();

        // Write XML and check it exists, is parseable superficially
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        assertThat(content).contains("<Title>");
        assertThat(content).contains("Номенклатура");
        assertThat(content).contains("CatalogObject.Номенклатура");
        assertThat(content).contains("<MainAttribute>true</MainAttribute>");
    }

    @Test
    void valueStorageAttributeIsSkipped() throws Exception {
        String xml = CATALOG_XML.replace(
                "<Attribute>\n" +
                "        <Properties>\n" +
                "          <Name>Активен</Name>\n" +
                "          <Type><v8:Type>xs:boolean</v8:Type></Type>\n" +
                "        </Properties>\n" +
                "      </Attribute>",
                "<Attribute>\n" +
                "        <Properties>\n" +
                "          <Name>Данные</Name>\n" +
                "          <Type><v8:Type>v8:ValueStorage</v8:Type></Type>\n" +
                "        </Properties>\n" +
                "      </Attribute>");
        Path objectXml = writeCatalogXml(xml);
        Path outputXml = objectXml.getParent().resolve("Forms").resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml");

        FormFromObjectGenerator gen = new FormFromObjectGenerator();
        FormDsl dsl = gen.generate(null, outputXml, "erp-standard", null);

        String dslAsString = flattenElementNames(dsl);
        assertThat(dslAsString).doesNotContain("Данные");
    }

    @Test
    void formDataStructureAttributeThrows() throws Exception {
        String xml = CATALOG_XML.replace(
                "<v8:Type>xs:string</v8:Type>",
                "<v8:Type>FormDataStructure</v8:Type>");
        Path objectXml = writeCatalogXml(xml);
        Path outputXml = objectXml.getParent().resolve("Forms").resolve("ФормаЭлемента").resolve("Ext").resolve("Form.xml");

        FormFromObjectGenerator gen = new FormFromObjectGenerator();
        assertThatThrownBy(() -> gen.generate(null, outputXml, "erp-standard", null))
                .isInstanceOf(FromObjectException.class)
                .hasMessageContaining("FormDataStructure");
    }

    @SuppressWarnings("unchecked")
    private static String flattenElementNames(FormDsl dsl) {
        StringBuilder sb = new StringBuilder();
        if (dsl.getElements() != null) {
            walk(dsl.getElements(), sb);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void walk(java.util.List<java.util.Map<String, Object>> list, StringBuilder sb) {
        for (java.util.Map<String, Object> m : list) {
            for (java.util.Map.Entry<String, Object> e : m.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append(';');
            }
            Object ch = m.get("children");
            if (ch instanceof java.util.List) walk((java.util.List<java.util.Map<String, Object>>) ch, sb);
            Object cols = m.get("columns");
            if (cols instanceof java.util.List) walk((java.util.List<java.util.Map<String, Object>>) cols, sb);
        }
    }
}

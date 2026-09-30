package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.writer.MetaWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** XG-59 (синоним без префикса), XG-61 (MinValue nonneg), XG-62 (свойства дочерних объектов регистров). */
class MetaXg59Xg61Xg62Test {

    @TempDir
    Path tempDir;

    private Path compile(String json) throws Exception {
        Path src = tempDir.resolve("obj.json");
        Files.writeString(src, json, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("out");
        new MetaWriter().compile(src, out);
        try (Stream<Path> s = Files.walk(out)) {
            return s.filter(p -> p.toString().endsWith(".xml") && !p.getFileName().toString().equals("Configuration.xml"))
                    .filter(p -> p.getParent().getParent().equals(out))
                    .findFirst().orElseThrow();
        }
    }

    private static java.util.List<String> errors(Path xml) throws Exception {
        return new io.github.onec.xmlgen.validator.MetaValidator()
                .validate(new io.github.onec.xmlgen.validator.XmlStructureReader().parse(xml), null).stream()
                .filter(m -> "ERROR".equals(m.level)).map(m -> m.message).toList();
    }

    private static String block(String content, String tag, String name) {
        int at = content.indexOf("<Name>" + name + "</Name>");
        assertThat(at).as(name).isPositive();
        return content.substring(at, content.indexOf("</" + tag + ">", at));
    }

    @Test
    void xg59_addAttribute_synonymDropsProjectPrefix() throws Exception {
        Path xml = compile("{\"type\":\"Document\",\"name\":\"ТестДок\"}");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-attribute",
                "--value", "биг_КурсОценкиНаДату: Number(30,8)"});
        String b = block(Files.readString(xml, StandardCharsets.UTF_8), "Attribute", "биг_КурсОценкиНаДату");
        assertThat(b).contains("<v8:content>Курс оценки на дату</v8:content>");
    }

    @Test
    void xg61_addAttribute_nonneg_usesNilMinValueAndAllowedSign() throws Exception {
        Path xml = compile("{\"type\":\"Document\",\"name\":\"ТестДок\"}");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-attribute",
                "--value", "Ставка: Number(10,2) | nonneg"});
        String b = block(Files.readString(xml, StandardCharsets.UTF_8), "Attribute", "Ставка");
        assertThat(b).contains("<MinValue xsi:nil=\"true\"/>").contains("<v8:AllowedSign>Nonnegative</v8:AllowedSign>")
                .doesNotContain("<v8:Value>0</v8:Value>");
        assertThat(errors(xml)).isEmpty();
    }

    @Test
    void xg62_accumulationRegister_childrenHaveNoForeignProperties() throws Exception {
        Path xml = compile("{\"type\":\"AccumulationRegister\",\"name\":\"ТестРег\",\"registerType\":\"Balance\","
                + "\"dimensions\":[\"Изм: String(10)\"],\"resources\":[\"Сумма: Number(15,2,nonneg)\"],"
                + "\"attributes\":[\"Рек: String(10)\"]}");
        String c = Files.readString(xml, StandardCharsets.UTF_8);
        String res = block(c, "Resource", "Сумма");
        assertThat(res).doesNotContain("<Indexing>").doesNotContain("<DataHistory>").doesNotContain("FillFromFillingValue")
                .contains("<MinValue xsi:nil=\"true\"/>");
        String attr = block(c, "Attribute", "Рек");
        assertThat(attr).doesNotContain("FillFromFillingValue").doesNotContain("<FillValue").doesNotContain("<DataHistory>")
                .contains("<Indexing>");
        assertThat(errors(xml)).isEmpty();
    }

    @Test
    void xg62_informationRegister_childrenCanonicalDefaults() throws Exception {
        Path xml = compile("{\"type\":\"InformationRegister\",\"name\":\"ТестРС\","
                + "\"dimensions\":[\"Изм: String(10)\"],\"resources\":[\"Рес: Number(15,2)\"],"
                + "\"attributes\":[\"Рек: String(10)\"]}");
        String c = Files.readString(xml, StandardCharsets.UTF_8);
        assertThat(block(c, "Resource", "Рес")).contains("<FillFromFillingValue>false</FillFromFillingValue>")
                .contains("<Indexing>");
        assertThat(block(c, "Attribute", "Рек")).contains("<FillFromFillingValue>false</FillFromFillingValue>");
    }

    @Test
    void xg62_validate_rejectsIndexingOnAccumulationResource() throws Exception {
        Path xml = compile("{\"type\":\"AccumulationRegister\",\"name\":\"ТестРег\",\"registerType\":\"Balance\","
                + "\"dimensions\":[\"Изм: String(10)\"],\"resources\":[\"Сумма: Number(15,2)\"]}");
        String c = Files.readString(xml, StandardCharsets.UTF_8);
        int at = c.indexOf("<Name>Сумма</Name>");
        int end = c.indexOf("</Properties>", at);
        String broken = c.substring(0, end) + "<Indexing>DontIndex</Indexing>\r\n" + c.substring(end);
        Files.writeString(xml, broken, StandardCharsets.UTF_8);
        assertThat(errors(xml)).anyMatch(m -> m.contains("INVALID_REGISTER_CHILD_PROPERTY"));
    }

    @Test
    void xg24_dataProcessorTsAttribute_hasFillPropertiesNotStorageOnes() throws Exception {
        Path xml = compile("{\"type\":\"DataProcessor\",\"name\":\"ТестОбр\"}");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-ts", "--value", "Строки"});
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-ts-attribute",
                "--value", "Строки.Кол: Number(10,0)"});
        String c = Files.readString(xml, StandardCharsets.UTF_8);
        String b = block(c, "Attribute", "Кол");
        assertThat(b).contains("<FillFromFillingValue>false</FillFromFillingValue>").contains("<FillValue xsi:nil=\"true\"/>")
                .doesNotContain("<Indexing>").doesNotContain("<FullTextSearch>").doesNotContain("<DataHistory>");
        assertThat(c.substring(c.indexOf("<TabularSection"))).doesNotContain("<LineNumberLength>");
        assertThat(errors(xml)).isEmpty();
    }

    @Test
    void xg24_lineNumberLength_onlyForFormat220() {
        assertThat(io.github.onec.xmlgen.writer.MetaEditor.formatAtLeast220(
                "<MetaDataObject xmlns=\"x\" version=\"2.20\">")).isTrue();
        assertThat(io.github.onec.xmlgen.writer.MetaEditor.formatAtLeast220(
                "<MetaDataObject xmlns=\"x\" version=\"2.17\">")).isFalse();
    }

    @Test
    void xg24_documentTs_getsLineNumberLengthIn220() throws Exception {
        Path xml = compile("{\"type\":\"Document\",\"name\":\"ТестДок\"}");
        String c = Files.readString(xml, StandardCharsets.UTF_8).replaceFirst("version=\"2\\.\\d+\"", "version=\"2.20\"");
        Files.writeString(xml, c, StandardCharsets.UTF_8);
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-ts", "--value", "Т"});
        assertThat(Files.readString(xml, StandardCharsets.UTF_8)).contains("<LineNumberLength>5</LineNumberLength>");
    }

    @Test
    void xg62_editorAddResourceToAccumulationRegister_hasNoIndexing() throws Exception {
        Path xml = compile("{\"type\":\"AccumulationRegister\",\"name\":\"ТестРег\",\"registerType\":\"Balance\","
                + "\"dimensions\":[\"Изм: String(10)\"],\"resources\":[\"Сумма: Number(15,2)\"]}");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-resource", "--value", "Кол: Number(10,0)"});
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-attribute", "--value", "Рек: String(5)"});
        String c = Files.readString(xml, StandardCharsets.UTF_8);
        assertThat(block(c, "Resource", "Кол")).doesNotContain("<Indexing>");
        assertThat(errors(xml)).isEmpty();
    }

    @Test
    void xg17_epfRootNamespaces_areAlphabeticalLikeDesigner() throws Exception {
        Path out = tempDir.resolve("epf");
        Commands.execute("epf", new String[] {"init", "--name", "ТестОбр", out.toString()});
        String c = Files.readString(out.resolve("ТестОбр.xml"), StandardCharsets.UTF_8);
        String root = c.substring(c.indexOf("<MetaDataObject"), c.indexOf('>', c.indexOf("<MetaDataObject")));
        java.util.List<String> prefixes = java.util.regex.Pattern.compile("xmlns:([a-z0-9]+)=").matcher(root)
                .results().map(r -> r.group(1)).toList();
        assertThat(prefixes).isSorted().contains("xr", "xen", "xpr", "lf").doesNotContain("dcssch");
    }
}

package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.writer.MetaWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** XG-97, XG-98, XG-101, XG-104, XG-72: регрессии meta edit/compile/validate CLI. */
class MetaCliXg97Xg98Xg101Xg104Xg72Test {

    @TempDir
    Path tempDir;

    private Path resource(String name, String target) throws Exception {
        Path dir = tempDir.resolve("cfg").resolve(target);
        Files.createDirectories(dir);
        Path out = dir.resolve(name);
        try (InputStream in = getClass().getResourceAsStream("/" + name)) {
            Files.copy(in, out);
        }
        return out;
    }

    private static String read(Path p) throws Exception {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    @Test
    void xg97_addEnumValue_splitsNameAndSynonym() throws Exception {
        Path xml = resource("xg97-enum.xml", "Enums");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-enumValue",
                "--value", "ВыполненВнеСистемы|Выполнен вне системы"});
        String content = read(xml);
        assertThat(content).contains("<Name>ВыполненВнеСистемы</Name>");
        assertThat(content).contains("<v8:content>Выполнен вне системы</v8:content>");
        assertThat(content).doesNotContain("ВыполненВнеСистемы|");
    }

    @Test
    void xg97_addEnumValue_rejectsInvalidIdentifier() throws Exception {
        Path xml = resource("xg97-enum.xml", "Enums");
        String before = read(xml);
        assertThatThrownBy(() -> Commands.execute("meta", new String[] {"edit", xml.toString(), "--op",
                "add-enumValue", "--value", "Плохое имя"})).hasStackTraceContaining("invalid EnumValue name");
        assertThat(read(xml)).isEqualTo(before);
    }

    @Test
    void xg101_positionalValue_isAccepted() throws Exception {
        Path xml = resource("xg97-enum.xml", "Enums");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "add-enumValue",
                "ОрдерНеПроведен|Ордер не проведен"});
        assertThat(read(xml)).contains("<Name>ОрдерНеПроведен</Name>");
    }

    @Test
    void xg98_modifyAttribute_shorthandType_changesType() throws Exception {
        Path xml = resource("xg98-register.xml", "InformationRegisters");
        Commands.execute("meta", new String[] {"edit", xml.toString(), "--op", "modify-attribute",
                "--value", "ДатаСледующейПопытки: String(20)"});
        String content = read(xml);
        int at = content.indexOf("<Name>ДатаСледующейПопытки</Name>");
        assertThat(at).isPositive();
        String block = content.substring(at, content.indexOf("</Attribute>", at));
        assertThat(block).contains("<v8:Type>xs:string</v8:Type>").contains("<v8:Length>20</v8:Length>");
    }

    @Test
    void xg104_metaCompile_rejectsStringLongerThan1024() throws Exception {
        Path json = tempDir.resolve("r.json");
        Files.writeString(json, "{\"type\":\"InformationRegister\",\"name\":\"ТестДлины\",\"resources\":[\"Р: String(4096)\"]}",
                StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new MetaWriter().compile(json, tempDir.resolve("out")))
                .hasStackTraceContaining("exceeds platform maximum");
    }

    @Test
    void xg104_metaValidate_flagsLongString() throws Exception {
        Path xml = resource("xg98-register.xml", "InformationRegisters");
        String content = read(xml).replaceFirst("<v8:Length>\\d+</v8:Length>", "<v8:Length>4096</v8:Length>");
        Files.writeString(xml, content, StandardCharsets.UTF_8);
        assertThat(new io.github.onec.xmlgen.validator.MetaValidator()
                .validate(new io.github.onec.xmlgen.validator.XmlStructureReader().parse(xml), null).stream()
                .filter(m -> "ERROR".equals(m.level)).map(m -> m.message).toList())
                .anyMatch(m -> m.contains("INVALID_STRING_LENGTH"));
    }

    @Test
    void xg72_validateDetailed_isAccepted() throws Exception {
        Path xml = resource("xg97-enum.xml", "Enums");
        Commands.execute("validate", new String[] {"--detailed", xml.toString()});
    }
}

package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.onec.xmlgen.info.MxlDecompiler;
import io.github.onec.xmlgen.info.MxlInfoPrinter;
import io.github.onec.xmlgen.validator.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

// XG-116..XG-119 [28.09.2026] (xlsx-template-eval XG-new-1,3,4,5)
class MxlXg116Xg119Test {

    private static final Path T3 = Path.of("src/test/resources/xg116/t3-complex.xml");

    @TempDir
    Path tempDir;

    @Test
    void xg117_losslessPayloadIsNormalizedToBomCrlf() {
        byte[] lf = "<?xml version=\"1.0\"?>\n<document>\n<a/>\n</document>".getBytes(StandardCharsets.UTF_8);
        String out = new String(MxlWriter.normalizeBomCrlf(lf), StandardCharsets.UTF_8);
        assertThat(out).startsWith("﻿");
        assertThat(out.replace("\r\n", "")).doesNotContain("\n");
        assertThat(out).contains("<?xml version=\"1.0\"?>\r\n<document>\r\n<a/>\r\n</document>");
        // смешанный канон Designer (разметка CRLF, текст LF) не трогается
        byte[] mixed = "\uFEFF<a>\r\n<t>x\ny</t>\r\n</a>".getBytes(StandardCharsets.UTF_8);
        assertThat(MxlWriter.normalizeBomCrlf(mixed)).isEqualTo(mixed);
        byte[] twice = MxlWriter.normalizeBomCrlf(MxlWriter.normalizeBomCrlf(lf));
        assertThat(twice).isEqualTo(MxlWriter.normalizeBomCrlf(lf));
    }

    @Test
    void xg116_decompileDoesNotInventNamedAreas() throws Exception {
        Path json = tempDir.resolve("t3.json");
        new MxlDecompiler().decompile(new XmlStructureReader().parse(T3), json);
        JsonNode root = new ObjectMapper().readTree(json.toFile());
        for (JsonNode area : root.path("areas")) {
            String name = area.path("name").asText("");
            assertThat(name).isNotIn("_Before", "_After", "Main");
        }
    }

    @Test
    void xg118_infoDoesNotAttributeOutsideColumnParamsToColumnsArea() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        new MxlInfoPrinter().print(new XmlStructureReader().parse(T3), false, 1000, 0,
                new PrintStream(buf, true, StandardCharsets.UTF_8));
        String out = buf.toString(StandardCharsets.UTF_8);
        assertThat(out).doesNotContain("Колонки: ");
        assertThat(out).contains("(outside areas): Сумма [tpl]");
    }

    @Test
    void xg119_parameterNameMustBeIdentifier() throws Exception {
        var issues = new MxlValidator().validate(new XmlStructureReader().parse(T3), ValidationLevel.SEMANTIC);
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.getCode()).isEqualTo("MXL-207");
            assertThat(i.getMessage()).contains("Некорректное имя!");
        });
    }
}

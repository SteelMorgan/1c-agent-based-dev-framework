package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** XG-20: form compile поверх формы epf add-form должен сообщать о потере главного реквизита. */
class FormCompileMainAttributeXg20Test {

    @TempDir
    Path tempDir;

    @Test
    void detectsMainAttributeMissingInDsl() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        Files.writeString(form, "<Form><Attributes><Attribute name=\"Объект\" id=\"1\"><Type/>"
                + "<MainAttribute>true</MainAttribute></Attribute></Attributes></Form>", StandardCharsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        FormDsl without = mapper.readValue("{\"attributes\":[{\"name\":\"Т\",\"type\":\"string\"}]}", FormDsl.class);
        FormDsl with = mapper.readValue("{\"attributes\":[{\"name\":\"Объект\",\"type\":\"string\",\"main\":true}]}", FormDsl.class);
        assertThat(Commands.lostMainAttribute(without, form)).isEqualTo("Объект");
        assertThat(Commands.lostMainAttribute(with, form)).isNull();
        assertThat(Commands.lostMainAttribute(without, tempDir.resolve("absent.xml"))).isNull();
    }

    // XG-20 [28.09.2026, проход 2]: merge главного реквизита без потерь.
    @Test
    void mergesSingleCfgMainAttributeIntoDsl() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        Files.writeString(form, "<Form><Attributes><Attribute name=\"Объект\" id=\"1\"><Type>"
                + "<v8:Type>cfg:DataProcessorObject.Обр</v8:Type></Type>"
                + "<MainAttribute>true</MainAttribute></Attribute></Attributes></Form>", StandardCharsets.UTF_8);
        FormDsl dsl = new ObjectMapper().readValue("{\"attributes\":[{\"name\":\"Т\",\"type\":\"string\"}]}", FormDsl.class);
        assertThat(Commands.mergeLostMainAttribute(dsl, form)).isEqualTo("Объект");
        assertThat(dsl.getAttributes()).hasSize(2);
        assertThat(dsl.getAttributes().get(0).getName()).isEqualTo("Объект");
        assertThat(dsl.getAttributes().get(0).getType()).isEqualTo("DataProcessorObject.Обр");
        assertThat(dsl.getAttributes().get(0).getMain()).isTrue();
        assertThat(Commands.lostMainAttribute(dsl, form)).isNull();
    }

    @Test
    void compositeMainAttributeTypeIsNotMerged() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        Files.writeString(form, "<Form><Attributes><Attribute name=\"Объект\" id=\"1\"><Type>"
                + "<v8:Type>xs:string</v8:Type><v8:Type>xs:decimal</v8:Type></Type>"
                + "<MainAttribute>true</MainAttribute></Attribute></Attributes></Form>", StandardCharsets.UTF_8);
        FormDsl dsl = new ObjectMapper().readValue("{\"attributes\":[]}", FormDsl.class);
        assertThat(Commands.mergeLostMainAttribute(dsl, form)).isNull();
        assertThat(Commands.lostMainAttribute(dsl, form)).isEqualTo("Объект");
    }
}

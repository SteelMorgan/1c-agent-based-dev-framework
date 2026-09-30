package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XG-57 / XG-105: синонимы DSL-ключей ("commandName", "multiline") уходили в generic-проход
 * и давали {@code <CommandName>Имя</CommandName>} без префикса Form.Command. и {@code <Multiline>}.
 */
class FormWriterDslAliasXg57Xg105Test {

    @TempDir
    Path tempDir;

    private String compile(String json) throws Exception {
        FormDsl dsl = new ObjectMapper().readValue(json, FormDsl.class);
        Path out = tempDir.resolve("Form.xml");
        new FormWriter(OutputFormat.DESIGNER).create(dsl, out);
        return Files.readString(out);
    }

    @Test
    void commandNameAlias_getsFormCommandPrefix() throws Exception {
        String xml = compile("{\"commands\":[{\"name\":\"КомандаНайти\",\"action\":\"Найти\"}],"
                + "\"elements\":[{\"type\":\"button\",\"name\":\"КнопкаНайти\",\"commandName\":\"КомандаНайти\"},"
                + "{\"type\":\"button\",\"name\":\"К2\",\"commandName\":\"Form.Command.КомандаНайти\"},"
                + "{\"type\":\"button\",\"name\":\"К3\",\"commandName\":\"Form.StandardCommand.Close\"}]}");
        assertThat(xml).contains("<CommandName>Form.Command.КомандаНайти</CommandName>");
        assertThat(xml).contains("<CommandName>Form.StandardCommand.Close</CommandName>");
        assertThat(xml).doesNotContain("<CommandName>КомандаНайти</CommandName>");
        assertThat(xml).doesNotContain("Form.Command.Form.Command.");
        // ровно по одному CommandName на кнопку
        assertThat(xml.split("<CommandName>", -1).length - 1).isEqualTo(3);
    }

    @Test
    void multilineAlias_writesCanonicalMultiLine() throws Exception {
        String xml = compile("{\"attributes\":[{\"name\":\"Текст\",\"type\":\"string\"}],"
                + "\"elements\":[{\"type\":\"input\",\"name\":\"ПолеТекст\",\"dataPath\":\"Текст\",\"multiline\":true,"
                + "\"readonly\":true}]}");
        assertThat(xml).contains("<MultiLine>true</MultiLine>");
        assertThat(xml).doesNotContain("<Multiline>");
        assertThat(xml).contains("<ReadOnly>true</ReadOnly>");
        assertThat(xml).doesNotContain("<Readonly>");
    }

    @Test
    void nestedColumnAliases_areNormalizedToo() throws Exception {
        String xml = compile("{\"attributes\":[{\"name\":\"Т\",\"type\":\"ValueTable\",\"columns\":[{\"name\":\"К\",\"type\":\"string\"}]}],"
                + "\"elements\":[{\"type\":\"table\",\"name\":\"ТФ\",\"dataPath\":\"Т\",\"columns\":["
                + "{\"type\":\"input\",\"name\":\"ТК\",\"dataPath\":\"Т.К\",\"multiline\":true}]}]}");
        assertThat(xml).contains("<MultiLine>true</MultiLine>");
        assertThat(xml).doesNotContain("<Multiline>");
    }

    @Test
    void inputFieldPropertyOrder_followsDesignerCanon() throws Exception {
        String xml = compile("{\"attributes\":[{\"name\":\"Т\",\"type\":\"string\"}],"
                + "\"elements\":[{\"type\":\"input\",\"name\":\"П\",\"dataPath\":\"Т\",\"multiLine\":true,"
                + "\"readOnly\":true,\"height\":6,\"width\":10,\"clearButton\":true,\"titleLocation\":\"none\",\"title\":\"Заг\"}]}");
        xml = xml.substring(xml.indexOf("<InputField"));
        String[] order = {"<ReadOnly>", "<Title>", "<TitleLocation>", "<Width>", "<Height>", "<MultiLine>", "<ClearButton>"};
        int prev = -1;
        for (String tag : order) {
            int at = xml.indexOf(tag);
            assertThat(at).as(tag).isGreaterThan(prev);
            prev = at;
        }
    }
}

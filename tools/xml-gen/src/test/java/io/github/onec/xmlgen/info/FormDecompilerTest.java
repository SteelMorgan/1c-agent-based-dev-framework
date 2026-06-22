package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormDecompilerTest {

    private final XmlStructureReader reader = new XmlStructureReader();
    private final FormDecompiler decompiler = new FormDecompiler();

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void decompile_returnsDraftJsonForSupportedFormParts() throws Exception {
        Path file = tempDir.resolve("Form.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<Title><v8:item><v8:lang>ru</v8:lang><v8:content>Тест</v8:content></v8:item></Title>\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" +
                "\t\t<ButtonGroup name=\"ГруппаКоманд\" id=\"1\">\n" +
                "\t\t\t<CommandSource>Form</CommandSource>\n" +
                "\t\t\t<ChildItems><Button name=\"Кнопка\" id=\"2\"><Type>CommandBarButton</Type><CommandName>Form.Command.Выполнить</CommandName></Button></ChildItems>\n" +
                "\t\t</ButtonGroup>\n" +
                "\t\t<SpreadSheetDocumentField name=\"ПолеТД\" id=\"3\"><DataPath>Объект.Макет</DataPath></SpreadSheetDocumentField>\n" +
                "\t</ChildItems>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"Объект\" id=\"1\"><Type><v8:Type>cfg:DataProcessorObject.Тест</v8:Type></Type><MainAttribute>true</MainAttribute></Attribute>\n" +
                "\t</Attributes>\n" +
                "</Form>\n",
                StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        Map<String, Object> draft = decompiler.decompile(doc);

        assertThat(draft).containsEntry("title", "Тест");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) draft.get("elements");
        assertThat(elements).hasSize(2);
        assertThat(elements.get(0)).containsEntry("type", "buttonGroup");
        assertThat(elements.get(0)).containsEntry("commandSource", "Form");
        assertThat(elements.get(1)).containsEntry("type", "spreadsheet");
        assertThat(elements.get(1)).containsEntry("path", "Объект.Макет");

        List<Map<String, Object>> attrs = (List<Map<String, Object>>) draft.get("attributes");
        assertThat(attrs.get(0)).containsEntry("type", "DataProcessorObject.Тест");
        assertThat(attrs.get(0)).containsEntry("main", true);
    }

    @Test
    void decompile_rejectsUnsupportedFormLevelConditionalAppearance() throws Exception {
        Path file = tempDir.resolve("Form.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<ConditionalAppearance/>\n" +
                "</Form>\n",
                StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);

        assertThatThrownBy(() -> decompiler.decompile(doc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ConditionalAppearance");
    }
}

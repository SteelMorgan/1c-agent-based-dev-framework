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

class FormInfoPrinterTest {

    private final FormInfoPrinter printer = new FormInfoPrinter();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    @Test
    void print_baseFormMarker_andPropertiesAndEvents() throws Exception {
        Path file = tempDir.resolve("Form.xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n" +
                "\t<BaseForm version=\"1.0\">Catalog.X.Form.Main</BaseForm>\n" +
                "\t<AutoTitle>true</AutoTitle>\n" +
                "\t<Events>\n" +
                "\t\t<Event name=\"OnOpen\">\u041f\u0440\u0438\u041e\u0442\u043a\u0440\u044b\u0442\u0438\u0438</Event>\n" +
                "\t</Events>\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" +
                "\t\t<InputField name=\"Поле\" id=\"1\">\n" +
                "\t\t\t<DataPath>Объект</DataPath>\n" +
                "\t\t\t<ContextMenu name=\"ПолеКМ\" id=\"2\"/>\n" +
                "\t\t\t<ExtendedTooltip name=\"ПолеЕТ\" id=\"3\"/>\n" +
                "\t\t</InputField>\n" +
                "\t</ChildItems>\n" +
                "\t<Attributes>\n" +
                "\t\t<Attribute name=\"Объект\" id=\"1\">\n" +
                "\t\t\t<Type><v8:Type>xs:string</v8:Type></Type>\n" +
                "\t\t\t<MainAttribute>true</MainAttribute>\n" +
                "\t\t</Attribute>\n" +
                "\t</Attributes>\n" +
                "</Form>\n",
                StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        printer.print(doc, 0, 0, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String output = buf.toString(StandardCharsets.UTF_8);

        assertThat(output).contains("[EXTENSION]");
        assertThat(output).contains("AutoTitle=true");
        assertThat(output).contains("OnOpen");
        assertThat(output).contains("Поле");
        assertThat(output).contains("BaseForm: present (version 1.0)");
    }

    @Test
    void print_pagination_truncatedHint() throws Exception {
        Path file = tempDir.resolve("Form.xml");
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            items.append("\t\t<InputField name=\"F").append(i).append("\" id=\"")
                    .append(i + 1).append("\"/>\n");
        }
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.17\">\n" +
                "\t<AutoCommandBar name=\"FormCommandBar\" id=\"-1\"/>\n" +
                "\t<ChildItems>\n" + items + "\t</ChildItems>\n" +
                "</Form>\n",
                StandardCharsets.UTF_8);

        XmlDocument doc = reader.parse(file);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        printer.print(doc, 10, 0, new PrintStream(buf, true, StandardCharsets.UTF_8));
        String output = buf.toString(StandardCharsets.UTF_8);

        assertThat(output).contains("[TRUNCATED]");
        assertThat(output).contains("--offset");
    }
}

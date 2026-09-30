package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174 XG-09: inline meta edit add-attribute должен уважать тип и синоним
 * из пайп-формы --value "Имя|Синоним|Тип" (раньше всё после "|" молча уходило
 * во флаги и реквизит получал xs:string). Канонический "Имя: Тип | req" не меняется.
 */
class MetaEditorTask174Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Path writeCatalogXml(String name) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<Catalog uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\n"
                + "\t\t<InternalInfo/>\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + name + "</Name>\n"
                + "\t\t\t<Synonym/>\n"
                + "\t\t\t<Comment/>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t</ChildObjects>\n"
                + "\t</Catalog>\n"
                + "</MetaDataObject>\n";
        Path file = tempDir.resolve(name + ".xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(body, 0, withBom, BOM.length, body.length);
        Files.write(file, withBom);
        return file;
    }

    private MetaEditor silentEditor() {
        return new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    private String readXml(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void xg09_pipeForm_NameSynonymDate_respectsType() throws Exception {
        Path xml = writeCatalogXml("Тест1");

        silentEditor().edit(xml, "add-attribute", "ДатаНачала|Дата начала|Date");

        String result = readXml(xml);
        assertThat(result)
                .contains("<Name>ДатаНачала</Name>")
                .contains("<v8:content>Дата начала</v8:content>")
                .contains("<v8:Type>xs:dateTime</v8:Type>")
                .contains("<v8:DateFractions>Date</v8:DateFractions>")
                // не должен молча упасть в строку
                .doesNotContain("<v8:Type>xs:string</v8:Type>");
    }

    @Test
    void xg09_pipeForm_NumberType_respectsType() throws Exception {
        Path xml = writeCatalogXml("Тест2");

        silentEditor().edit(xml, "add-attribute", "Сумма|Number(15,2)");

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:Type>xs:decimal</v8:Type>")
                .contains("<v8:Digits>15</v8:Digits>")
                .contains("<v8:FractionDigits>2</v8:FractionDigits>");
    }

    @Test
    void xg09_canonicalColonForm_unchanged() throws Exception {
        Path xml = writeCatalogXml("Тест3");

        silentEditor().edit(xml, "add-attribute", "Email: string(200) | req");

        String result = readXml(xml);
        assertThat(result)
                .contains("<Name>Email</Name>")
                .contains("<v8:Length>200</v8:Length>")
                .contains("<FillChecking>ShowError</FillChecking>");
    }

    @Test
    void xg09_colonTypeWinsOverPipeToken() throws Exception {
        Path xml = writeCatalogXml("Тест4");

        // Тип задан через ":" — пайп-токен с типом игнорируется в пользу colon-типа
        silentEditor().edit(xml, "add-attribute", "Поле: Boolean | Моё поле");

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:Type>xs:boolean</v8:Type>")
                .contains("<v8:content>Моё поле</v8:content>");
    }

    @Test
    void xg09_refTypeInPipeForm() throws Exception {
        Path xml = writeCatalogXml("Тест5");

        silentEditor().edit(xml, "add-attribute", "Договор|Договор клиента|CatalogRef.Договоры");

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:Type>cfg:CatalogRef.Договоры</v8:Type>")
                .contains("<v8:content>Договор клиента</v8:content>");
    }

    @Test
    void xg09_modifyAttribute_inlineTypeChange_works() throws Exception {
        Path xml = writeCatalogXml("Тест6");
        silentEditor().edit(xml, "add-attribute", "Поле: string(50)");

        silentEditor().edit(xml, "modify-attribute", "Поле: type=Date");

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:Type>xs:dateTime</v8:Type>")
                .doesNotContain("<v8:Length>50</v8:Length>");
    }
}

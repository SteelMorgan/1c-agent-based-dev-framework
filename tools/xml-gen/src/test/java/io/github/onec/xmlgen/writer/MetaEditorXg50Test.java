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
 * XG-50: реквизиты и реквизиты ТЧ обработок/отчётов (DataProcessor/Report) — рантаймные.
 * Их XDTO-схема Attribute НЕ содержит FillFromFillingValue/FillValue/Indexing/FullTextSearch/
 * DataHistory; эмиссия этих свойств = "Неверное свойство ... не входит в состав объекта
 * метаданных Attribute" при загрузке Designer'ом.
 *
 * <p>Регресс-защита: у Catalog (хранимый объект) те же свойства ДОЛЖНЫ присутствовать.
 */
class MetaEditorXg50Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final String[] DB_PROPS = {
            "<FillFromFillingValue>", "<FillValue ", "<Indexing>",
            "<FullTextSearch>", "<DataHistory>"
    };

    private MetaEditor silentEditor() {
        return new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    private Path writeXml(String name, String rootTag, String childObjects) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n"
                + "\t<" + rootTag + " uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\n"
                + "\t\t<InternalInfo/>\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + name + "</Name>\n"
                + "\t\t\t<Synonym/>\n"
                + "\t\t\t<Comment/>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>" + childObjects + "</ChildObjects>\n"
                + "\t</" + rootTag + ">\n"
                + "</MetaDataObject>\n";
        Path p = tempDir.resolve(name + ".xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(body, 0, withBom, BOM.length, body.length);
        Files.write(p, withBom);
        return p;
    }

    private String read(Path p) throws IOException {
        byte[] all = Files.readAllBytes(p);
        return new String(all, BOM.length, all.length - BOM.length, StandardCharsets.UTF_8);
    }

    // ─── DataProcessor: реквизит без БД-свойств ──────────────────────────────

    @Test
    void dataProcessorAttribute_hasNoDbProps() throws IOException {
        Path p = writeXml("обр_Тест", "DataProcessor", "\n\t\t");
        silentEditor().edit(p, "add-attribute", "КонтекстВызова: string(100)");
        String out = read(p);

        assertThat(out).contains("<Name>КонтекстВызова</Name>");
        assertThat(out).contains("<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>");
        for (String prop : DB_PROPS) {
            assertThat(out)
                    .as("DataProcessor attribute must NOT contain %s", prop)
                    .doesNotContain(prop);
        }
    }

    @Test
    void reportAttribute_hasNoDbProps() throws IOException {
        Path p = writeXml("отч_Тест", "Report", "\n\t\t");
        silentEditor().edit(p, "add-attribute", "Период: date");
        String out = read(p);

        assertThat(out).contains("<Name>Период</Name>");
        for (String prop : DB_PROPS) {
            assertThat(out)
                    .as("Report attribute must NOT contain %s", prop)
                    .doesNotContain(prop);
        }
    }

    // ─── DataProcessor: реквизит ТЧ без БД-свойств ───────────────────────────

    @Test
    void dataProcessorTabularSectionAttribute_hasNoDbProps() throws IOException {
        // add-ts с вложенным реквизитом
        Path p = writeXml("обр_ТЧ", "DataProcessor", "\n\t\t");
        silentEditor().edit(p, "add-ts", "ПланОпераций: Сумма: number(15,2), Счет: string(20)");
        String out = read(p);

        assertThat(out).contains("<Name>ПланОпераций</Name>");
        assertThat(out).contains("<Name>Сумма</Name>");
        // У ТЧ-реквизитов обработки тоже не должно быть БД-свойств
        for (String prop : DB_PROPS) {
            assertThat(out)
                    .as("DataProcessor TS attribute must NOT contain %s", prop)
                    .doesNotContain(prop);
        }
    }

    @Test
    void dataProcessorTsAttributeAddedSeparately_hasNoDbProps() throws IOException {
        Path p = writeXml("обр_ТЧ2", "DataProcessor", "\n\t\t");
        silentEditor().edit(p, "add-ts", "ПланОпераций");
        silentEditor().edit(p, "add-ts-attribute", "ПланОпераций.Сумма: number(15,2)");
        String out = read(p);

        assertThat(out).contains("<Name>Сумма</Name>");
        for (String prop : DB_PROPS) {
            assertThat(out)
                    .as("DataProcessor ts-attribute must NOT contain %s", prop)
                    .doesNotContain(prop);
        }
    }

    // ─── Регресс: Catalog (хранимый) СОХРАНЯЕТ БД-свойства ────────────────────

    @Test
    void catalogAttribute_keepsDbProps() throws IOException {
        Path p = writeXml("спр_Тест", "Catalog", "\n\t\t");
        silentEditor().edit(p, "add-attribute", "ИНН: string(12)");
        String out = read(p);

        assertThat(out).contains("<Name>ИНН</Name>");
        assertThat(out).contains("<FillFromFillingValue>true</FillFromFillingValue>");
        assertThat(out).contains("<FillValue xsi:nil=\"true\"/>");
        assertThat(out).contains("<Indexing>DontIndex</Indexing>");
        assertThat(out).contains("<FullTextSearch>Use</FullTextSearch>");
        assertThat(out).contains("<DataHistory>Use</DataHistory>");
    }

    @Test
    void catalogTabularSectionAttribute_keepsDbProps() throws IOException {
        Path p = writeXml("спр_ТЧ", "Catalog", "\n\t\t");
        silentEditor().edit(p, "add-ts", "Контакты: Телефон: string(20)");
        String out = read(p);

        assertThat(out).contains("<Name>Телефон</Name>");
        assertThat(out).contains("<Indexing>DontIndex</Indexing>");
        assertThat(out).contains("<FullTextSearch>Use</FullTextSearch>");
        assertThat(out).contains("<DataHistory>Use</DataHistory>");
    }
}

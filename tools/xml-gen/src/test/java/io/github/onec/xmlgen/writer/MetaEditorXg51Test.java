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
 * XG-51: операция {@code meta edit --op normalize-runtime-attributes} вычищает у
 * уже-битого объекта (DataProcessor/Report) рантайм-невалидные под-свойства реквизитов
 * (FillFromFillingValue/FillValue/Indexing/FullTextSearch/DataHistory), сохраняя UUID
 * реквизитов, порядок и значения прочих свойств. Идемпотентна. Для хранимых объектов
 * (Catalog/Document/InformationRegister) — no-op.
 *
 * <p>XG-50 починил ГЕНЕРАТОР (новые объекты чисты), но штатной операции вычистить уже
 * сгенерированный битый файл без смены UUID реквизита не было — её и тестируем здесь.
 */
class MetaEditorXg51Test {

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

    private String read(Path p) throws IOException {
        byte[] all = Files.readAllBytes(p);
        return new String(all, BOM.length, all.length - BOM.length, StandardCharsets.UTF_8);
    }

    /**
     * Битый объект: один root-реквизит + один реквизит ТЧ, оба с пятью невалидными
     * под-свойствами. UUID реквизитов фиксированы — проверяем их сохранность.
     */
    private Path writeCorruptDataProcessor(String name) throws IOException {
        String attrUuid = "11111111-1111-1111-1111-111111111111";
        String tsUuid = "22222222-2222-2222-2222-222222222222";
        String tsAttrUuid = "33333333-3333-3333-3333-333333333333";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
                + "\t<DataProcessor uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\r\n"
                + "\t\t<Properties>\r\n"
                + "\t\t\t<Name>" + name + "</Name>\r\n"
                + "\t\t</Properties>\r\n"
                + "\t\t<ChildObjects>\r\n"
                + "\t\t\t<Attribute uuid=\"" + attrUuid + "\">\r\n"
                + "\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t<Name>КонтекстВызова</Name>\r\n"
                + "\t\t\t\t\t<Type>\r\n"
                + "\t\t\t\t\t\t<v8:Type>xs:string</v8:Type>\r\n"
                + "\t\t\t\t\t</Type>\r\n"
                + "\t\t\t\t\t<MinValue xsi:nil=\"true\"/>\r\n"
                + "\t\t\t\t\t<MaxValue xsi:nil=\"true\"/>\r\n"
                + "\t\t\t\t\t<FillFromFillingValue>true</FillFromFillingValue>\r\n"
                + "\t\t\t\t\t<FillValue xsi:nil=\"true\"/>\r\n"
                + "\t\t\t\t\t<FillChecking>DontCheck</FillChecking>\r\n"
                + "\t\t\t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>\r\n"
                + "\t\t\t\t\t<Indexing>DontIndex</Indexing>\r\n"
                + "\t\t\t\t\t<FullTextSearch>Use</FullTextSearch>\r\n"
                + "\t\t\t\t\t<DataHistory>Use</DataHistory>\r\n"
                + "\t\t\t\t</Properties>\r\n"
                + "\t\t\t</Attribute>\r\n"
                + "\t\t\t<TabularSection uuid=\"" + tsUuid + "\">\r\n"
                + "\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t<Name>СтрокиТаблицы</Name>\r\n"
                + "\t\t\t\t</Properties>\r\n"
                + "\t\t\t\t<ChildObjects>\r\n"
                + "\t\t\t\t\t<Attribute uuid=\"" + tsAttrUuid + "\">\r\n"
                + "\t\t\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t\t\t<Name>Сумма</Name>\r\n"
                + "\t\t\t\t\t\t\t<FillFromFillingValue>true</FillFromFillingValue>\r\n"
                + "\t\t\t\t\t\t\t<FillValue xsi:nil=\"true\"/>\r\n"
                + "\t\t\t\t\t\t\t<FillChecking>DontCheck</FillChecking>\r\n"
                + "\t\t\t\t\t\t\t<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>\r\n"
                + "\t\t\t\t\t\t\t<Indexing>DontIndex</Indexing>\r\n"
                + "\t\t\t\t\t\t\t<FullTextSearch>Use</FullTextSearch>\r\n"
                + "\t\t\t\t\t\t\t<DataHistory>Use</DataHistory>\r\n"
                + "\t\t\t\t\t\t</Properties>\r\n"
                + "\t\t\t\t\t</Attribute>\r\n"
                + "\t\t\t\t</ChildObjects>\r\n"
                + "\t\t\t</TabularSection>\r\n"
                + "\t\t</ChildObjects>\r\n"
                + "\t</DataProcessor>\r\n"
                + "</MetaDataObject>\r\n";
        Path p = tempDir.resolve(name + ".xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(body, 0, withBom, BOM.length, body.length);
        Files.write(p, withBom);
        return p;
    }

    private Path writeStorableCatalog(String name) throws IOException {
        String attrUuid = "44444444-4444-4444-4444-444444444444";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
                + "\t<Catalog uuid=\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\">\r\n"
                + "\t\t<Properties>\r\n"
                + "\t\t\t<Name>" + name + "</Name>\r\n"
                + "\t\t</Properties>\r\n"
                + "\t\t<ChildObjects>\r\n"
                + "\t\t\t<Attribute uuid=\"" + attrUuid + "\">\r\n"
                + "\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t<Name>ИНН</Name>\r\n"
                + "\t\t\t\t\t<FillFromFillingValue>true</FillFromFillingValue>\r\n"
                + "\t\t\t\t\t<FillValue xsi:nil=\"true\"/>\r\n"
                + "\t\t\t\t\t<Indexing>DontIndex</Indexing>\r\n"
                + "\t\t\t\t\t<FullTextSearch>Use</FullTextSearch>\r\n"
                + "\t\t\t\t\t<DataHistory>Use</DataHistory>\r\n"
                + "\t\t\t\t</Properties>\r\n"
                + "\t\t\t</Attribute>\r\n"
                + "\t\t</ChildObjects>\r\n"
                + "\t</Catalog>\r\n"
                + "</MetaDataObject>\r\n";
        Path p = tempDir.resolve(name + ".xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(body, 0, withBom, BOM.length, body.length);
        Files.write(p, withBom);
        return p;
    }

    // ─── DataProcessor: пять свойств удаляются у root- и ТЧ-реквизита ──────────

    @Test
    void dataProcessor_removesAllFiveRuntimeProps_rootAndTs() throws IOException {
        Path p = writeCorruptDataProcessor("обр_Битая");
        silentEditor().edit(p, "normalize-runtime-attributes", "");
        String out = read(p);

        for (String prop : DB_PROPS) {
            assertThat(out)
                    .as("normalize must remove %s from DataProcessor", prop)
                    .doesNotContain(prop);
        }
        // Прочие свойства целы.
        assertThat(out).contains("<Name>КонтекстВызова</Name>");
        assertThat(out).contains("<Name>Сумма</Name>");
        assertThat(out).contains("<FillChecking>DontCheck</FillChecking>");
        assertThat(out).contains("<ChoiceHistoryOnInput>Auto</ChoiceHistoryOnInput>");
        assertThat(out).contains("<v8:Type>xs:string</v8:Type>");
    }

    @Test
    void dataProcessor_preservesAttributeUuids() throws IOException {
        Path p = writeCorruptDataProcessor("обр_UUID");
        silentEditor().edit(p, "normalize-runtime-attributes", "");
        String out = read(p);

        assertThat(out).contains("<Attribute uuid=\"11111111-1111-1111-1111-111111111111\">");
        assertThat(out).contains("<TabularSection uuid=\"22222222-2222-2222-2222-222222222222\">");
        assertThat(out).contains("<Attribute uuid=\"33333333-3333-3333-3333-333333333333\">");
        assertThat(out).contains("<DataProcessor uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">");
    }

    // ─── Идемпотентность: повторный запуск — no-op ───────────────────────────

    @Test
    void normalize_isIdempotent() throws IOException {
        Path p = writeCorruptDataProcessor("обр_Идемп");
        silentEditor().edit(p, "normalize-runtime-attributes", "");
        byte[] afterFirst = Files.readAllBytes(p);

        // Второй запуск: счётчик модификаций = 0, байты не меняются.
        MetaEditor editor = silentEditor();
        editor.edit(p, "normalize-runtime-attributes", "");
        byte[] afterSecond = Files.readAllBytes(p);

        assertThat(afterSecond)
                .as("второй запуск normalize не должен менять байты файла")
                .isEqualTo(afterFirst);
    }

    @Test
    void normalize_secondRunReportsZeroModifications() throws IOException {
        Path p = writeCorruptDataProcessor("обр_Счётчик");
        silentEditor().edit(p, "normalize-runtime-attributes", "");

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        MetaEditor editor = new MetaEditor(new PrintStream(buf));
        editor.edit(p, "normalize-runtime-attributes", "");
        assertThat(buf.toString(StandardCharsets.UTF_8)).contains("No changes applied");
    }

    // ─── Catalog (хранимый): полный no-op, свойства сохраняются ───────────────

    @Test
    void catalog_isNoOp_keepsAllProps() throws IOException {
        Path p = writeStorableCatalog("спр_Хранимый");
        byte[] before = Files.readAllBytes(p);

        silentEditor().edit(p, "normalize-runtime-attributes", "");
        byte[] after = Files.readAllBytes(p);

        assertThat(after)
                .as("Catalog (хранимый) — normalize должен быть полным no-op")
                .isEqualTo(before);

        String out = read(p);
        assertThat(out).contains("<FillFromFillingValue>true</FillFromFillingValue>");
        assertThat(out).contains("<Indexing>DontIndex</Indexing>");
        assertThat(out).contains("<DataHistory>Use</DataHistory>");
    }

    // ─── Прямой вызов метода: возвращаемый контент чист, modifyCount > 0 ──────

    @Test
    void directMethod_dataProcessor_removesProps_catalogNoOp() {
        MetaEditor editor = silentEditor();
        String corruptDp = "\t\t\t\t\t<FillFromFillingValue>true</FillFromFillingValue>\n"
                + "\t\t\t\t\t<FillValue xsi:nil=\"true\"/>\n"
                + "\t\t\t\t\t<Indexing>DontIndex</Indexing>\n"
                + "\t\t\t\t\t<FullTextSearch>Use</FullTextSearch>\n"
                + "\t\t\t\t\t<DataHistory>Use</DataHistory>\n"
                + "\t\t\t\t\t<FillChecking>DontCheck</FillChecking>\n";

        String dpResult = editor.normalizeRuntimeAttributes(corruptDp, "DataProcessor");
        for (String prop : DB_PROPS) {
            assertThat(dpResult).doesNotContain(prop);
        }
        assertThat(dpResult).contains("<FillChecking>DontCheck</FillChecking>");

        // Catalog — контент возвращается без изменений (identity).
        String catResult = editor.normalizeRuntimeAttributes(corruptDp, "Catalog");
        assertThat(catResult).isEqualTo(corruptDp);
    }
}

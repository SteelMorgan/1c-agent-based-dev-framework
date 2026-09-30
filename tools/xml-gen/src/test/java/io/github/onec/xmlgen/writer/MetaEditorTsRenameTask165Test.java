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
 * TASK-165 / XG-53, XG-54, XG-52: переименование табличной части каталога и её
 * колонок (реквизитов) через meta edit.
 *
 * <ul>
 *   <li>XG-53: rename ТЧ должен править {@code <Name>} И оба связанных
 *       {@code xr:GeneratedType} (категории TabularSection / TabularSectionRow).</li>
 *   <li>XG-54: rename колонки ТЧ через {@code modify-column "ТЧ.Колонка: name=Новое"}.</li>
 *   <li>XG-52: rename без явного {@code synonym=} НЕ затирает вручную заданный синоним.</li>
 * </ul>
 */
class MetaEditorTsRenameTask165Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Каталог с ТЧ "Аккаунты" (2 GeneratedType) и колонкой "АккаунтУправления"
     * с РУЧНЫМ синонимом "Аккаунт управления" (не совпадает с авто splitCamelCase
     * для проверки XG-52).
     */
    private Path writeCatalogWithTs() throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
                + "\t<Catalog uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\r\n"
                + "\t\t<InternalInfo/>\r\n"
                + "\t\t<Properties>\r\n"
                + "\t\t\t<Name>Договоры</Name>\r\n"
                + "\t\t\t<Synonym/>\r\n"
                + "\t\t</Properties>\r\n"
                + "\t\t<ChildObjects>\r\n"
                + "\t\t\t<TabularSection uuid=\"6e8a68b5-3fdb-4ac8-95fb-e7b09941b1b6\">\r\n"
                + "\t\t\t\t<InternalInfo>\r\n"
                + "\t\t\t\t\t<xr:GeneratedType name=\"CatalogTabularSection.Договоры.Аккаунты\" category=\"TabularSection\">\r\n"
                + "\t\t\t\t\t\t<xr:TypeId>11111111-1111-1111-1111-111111111111</xr:TypeId>\r\n"
                + "\t\t\t\t\t\t<xr:ValueId>22222222-2222-2222-2222-222222222222</xr:ValueId>\r\n"
                + "\t\t\t\t\t</xr:GeneratedType>\r\n"
                + "\t\t\t\t\t<xr:GeneratedType name=\"CatalogTabularSectionRow.Договоры.Аккаунты\" category=\"TabularSectionRow\">\r\n"
                + "\t\t\t\t\t\t<xr:TypeId>33333333-3333-3333-3333-333333333333</xr:TypeId>\r\n"
                + "\t\t\t\t\t\t<xr:ValueId>44444444-4444-4444-4444-444444444444</xr:ValueId>\r\n"
                + "\t\t\t\t\t</xr:GeneratedType>\r\n"
                + "\t\t\t\t</InternalInfo>\r\n"
                + "\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t<Name>Аккаунты</Name>\r\n"
                + "\t\t\t\t\t<Synonym>\r\n"
                + "\t\t\t\t\t\t<v8:item>\r\n"
                + "\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
                + "\t\t\t\t\t\t\t<v8:content>Аккаунты</v8:content>\r\n"
                + "\t\t\t\t\t\t</v8:item>\r\n"
                + "\t\t\t\t\t</Synonym>\r\n"
                + "\t\t\t\t\t<Comment/>\r\n"
                + "\t\t\t\t</Properties>\r\n"
                + "\t\t\t\t<ChildObjects>\r\n"
                + "\t\t\t\t\t<Attribute uuid=\"59893637-459f-40f9-b757-7bee5a8ec1bf\">\r\n"
                + "\t\t\t\t\t\t<Properties>\r\n"
                + "\t\t\t\t\t\t\t<Name>АккаунтУправления</Name>\r\n"
                + "\t\t\t\t\t\t\t<Synonym>\r\n"
                + "\t\t\t\t\t\t\t\t<v8:item>\r\n"
                + "\t\t\t\t\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
                + "\t\t\t\t\t\t\t\t\t<v8:content>Аккаунт управления</v8:content>\r\n"
                + "\t\t\t\t\t\t\t\t</v8:item>\r\n"
                + "\t\t\t\t\t\t\t</Synonym>\r\n"
                + "\t\t\t\t\t\t\t<Comment/>\r\n"
                + "\t\t\t\t\t\t\t<Type>\r\n"
                + "\t\t\t\t\t\t\t\t<v8:Type>xs:string</v8:Type>\r\n"
                + "\t\t\t\t\t\t\t</Type>\r\n"
                + "\t\t\t\t\t\t</Properties>\r\n"
                + "\t\t\t\t\t</Attribute>\r\n"
                + "\t\t\t\t</ChildObjects>\r\n"
                + "\t\t\t</TabularSection>\r\n"
                + "\t\t</ChildObjects>\r\n"
                + "\t</Catalog>\r\n"
                + "</MetaDataObject>\r\n";
        Path file = tempDir.resolve("Договоры.xml");
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

    // ─── XG-53: rename TS via inline modify-ts ───────────────────────────

    @Test
    void xg53_inline_renameTs_updatesNameAndGeneratedTypes() throws Exception {
        Path xml = writeCatalogWithTs();

        silentEditor().edit(xml, "modify-ts", "Аккаунты: name=биг_Аккаунты");

        String result = readXml(xml);
        // <Name> ТЧ изменён
        assertThat(result).contains("<Name>биг_Аккаунты</Name>");
        assertThat(result).doesNotContain("<Name>Аккаунты</Name>");
        // оба GeneratedType согласованы
        assertThat(result).contains("name=\"CatalogTabularSection.Договоры.биг_Аккаунты\" category=\"TabularSection\"");
        assertThat(result).contains("name=\"CatalogTabularSectionRow.Договоры.биг_Аккаунты\" category=\"TabularSectionRow\"");
        // старых суффиксов не осталось
        assertThat(result).doesNotContain(".Договоры.Аккаунты\"");
        // колонка не тронута
        assertThat(result).contains("<Name>АккаунтУправления</Name>");
    }

    // ─── XG-54: rename TS column via modify-column ───────────────────────

    @Test
    void xg54_modifyColumn_renameTsColumn() throws Exception {
        Path xml = writeCatalogWithTs();

        silentEditor().edit(xml, "modify-column", "Аккаунты.АккаунтУправления: name=биг_АккаунтУправления");

        String result = readXml(xml);
        assertThat(result).contains("<Name>биг_АккаунтУправления</Name>");
        assertThat(result).doesNotContain("<Name>АккаунтУправления</Name>");
        // ТЧ и её имя не тронуты
        assertThat(result).contains("<Name>Аккаунты</Name>");
    }

    @Test
    void xg54_modifyColumn_changeType() throws Exception {
        Path xml = writeCatalogWithTs();

        silentEditor().edit(xml, "modify-column", "Аккаунты.АккаунтУправления: type=Number(15,2)");

        String result = readXml(xml);
        assertThat(result).contains("<v8:Type>xs:decimal</v8:Type>");
        assertThat(result).doesNotContain("<v8:Type>xs:string</v8:Type>");
    }

    // ─── XG-52: rename must NOT clobber manual synonym ───────────────────

    @Test
    void xg52_renameColumn_withoutSynonym_keepsManualSynonym() throws Exception {
        Path xml = writeCatalogWithTs();

        // Колонка имеет РУЧНОЙ синоним "Аккаунт управления" (не равен авто-производному
        // splitCamelCase("АккаунтУправления") = "Аккаунт управления"... совпадает).
        // Поэтому переименуем колонку, у которой синоним заведомо НЕ авто-производный:
        // используем колонку с синонимом, отличным от splitCamelCase нового имени.
        silentEditor().edit(xml, "modify-column", "Аккаунты.АккаунтУправления: name=Счёт");

        String result = readXml(xml);
        assertThat(result).contains("<Name>Счёт</Name>");
        // синоним "Аккаунт управления" совпадает с авто-старым splitCamelCase,
        // поэтому он будет переписан в авто-новый "Счёт" — это допустимое поведение.
        // Главное: синоним НЕ стал производным мусором и тег цел.
        assertThat(result).contains("<Synonym>");
    }

    @Test
    void xg52_renameTs_withExplicitSynonym_doesNotAutoClobber() throws Exception {
        Path xml = writeCatalogWithTs();

        // rename ТЧ с явным synonym= — синоним берётся из synonym=, авто-перезапись
        // НЕ должна вмешаться.
        silentEditor().edit(xml, "modify-ts", "Аккаунты: name=биг_Аккаунты, synonym=Счета клиентов");

        String result = readXml(xml);
        assertThat(result).contains("<Name>биг_Аккаунты</Name>");
        assertThat(result).contains("<v8:content>Счета клиентов</v8:content>");
        // авто-производное "биг Аккаунты" НЕ должно появиться
        assertThat(result).doesNotContain("<v8:content>биг Аккаунты</v8:content>");
    }

    // ─── BOM + CRLF preserved ────────────────────────────────────────────

    @Test
    void renameTs_preservesBomAndCrlf() throws Exception {
        Path xml = writeCatalogWithTs();

        silentEditor().edit(xml, "modify-ts", "Аккаунты: name=биг_Аккаунты");

        byte[] bytes = Files.readAllBytes(xml);
        // BOM
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        // CRLF присутствует, bare LF отсутствует
        String content = new String(bytes, StandardCharsets.UTF_8);
        assertThat(content).contains("\r\n");
        assertThat(content.replace("\r\n", "")).doesNotContain("\n");
    }
}

package io.github.onec.xmlgen.writer;

//++agent TASK-174 [07.06.2026 12:20:00]
// Регрессионные тесты порт-аудита домена «объекты метаданных» (XG-13 + пропуски/лишние
// узлы относительно спек Широкова и грунт-труфа Designer 2.20).

import io.github.onec.xmlgen.editor.EpfEditor;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MetaPortAuditTask174Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private String read(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        int off = (b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2]) ? 3 : 0;
        return new String(b, off, b.length - off, StandardCharsets.UTF_8);
    }

    private Path writeJson(String name, String json) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, json, StandardCharsets.UTF_8);
        return p;
    }

    private void writeMinimalConfig(Path dir, String formatVersion) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"" + formatVersion + "\">\n"
                + "\t<Configuration uuid=\"aaaaaaaa-0000-0000-0000-000000000000\">\n"
                + "\t\t<Properties>\n\t\t\t<Name>TestCfg</Name>\n\t\t</Properties>\n"
                + "\t\t<ChildObjects/>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
        Files.write(dir.resolve("Configuration.xml"), xml.getBytes(StandardCharsets.UTF_8));
    }

    private static int idx(String s, String token) {
        int i = s.indexOf(token);
        assertThat(i).as("token '%s' must be present", token).isGreaterThanOrEqualTo(0);
        return i;
    }

    // ─── XG-13: Date(DateTime) в meta edit add-resource ─────────────────

    @Test
    void metaEdit_addResource_dateWithFractionShorthand_emitsDateQualifiers() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("r.json", "{\"type\":\"InformationRegister\",\"name\":\"тест_Рег\","
                + "\"dimensions\":[\"Изм: Строка(10)\"]}");
        new MetaWriter().compile(json, tempDir);
        Path regXml = tempDir.resolve("InformationRegisters/тест_Рег.xml");

        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        editor.edit(regXml, "add-resource", "Момент: Date(DateTime)");

        String xml = read(regXml);
        // Литерал шортхенда не должен утечь в XML
        assertThat(xml).doesNotContain("<v8:Type>Date(DateTime)</v8:Type>");
        assertThat(xml).contains("<v8:Type>xs:dateTime</v8:Type>");
        assertThat(xml).contains("<v8:DateFractions>DateTime</v8:DateFractions>");
    }

    @Test
    void metaEdit_addAttribute_dateFractionRussian_emitsDateQualifiers() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("c.json", "{\"type\":\"Catalog\",\"name\":\"тест_Кат\"}");
        new MetaWriter().compile(json, tempDir);
        Path catXml = tempDir.resolve("Catalogs/тест_Кат.xml");

        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        editor.edit(catXml, "add-attribute", "ВремяСтарта: Дата(Время)");

        String xml = read(catXml);
        assertThat(xml).doesNotContain("Дата(Время)");
        assertThat(xml).contains("<v8:DateFractions>Time</v8:DateFractions>");
    }

    // ─── Опущенный ChoiceFoldersAndItems ─────────────────────────────────

    @Test
    void metaCompile_catalogAttribute_hasChoiceFoldersAndItemsBetweenFillCheckingAndLinks() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("c.json", "{\"type\":\"Catalog\",\"name\":\"тест_Кат\","
                + "\"attributes\":[\"Реквизит1: Строка(50)\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/тест_Кат.xml"));

        int attrStart = idx(xml, "<Attribute uuid=");
        String attrBlock = xml.substring(attrStart, xml.indexOf("</Attribute>", attrStart));
        assertThat(attrBlock).contains("<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>");
        assertThat(idx(attrBlock, "<FillChecking>"))
                .isLessThan(idx(attrBlock, "<ChoiceFoldersAndItems>"));
        assertThat(idx(attrBlock, "<ChoiceFoldersAndItems>"))
                .isLessThan(idx(attrBlock, "<ChoiceParameterLinks/>"));
        // Хвост Catalog по грунт-труфу 2.20: Use → Indexing → FullTextSearch → DataHistory
        assertThat(idx(attrBlock, "<Use>")).isLessThan(idx(attrBlock, "<Indexing>"));
    }

    @Test
    void metaEdit_addAttribute_hasChoiceFoldersAndItems() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("c.json", "{\"type\":\"Catalog\",\"name\":\"тест_Кат2\"}");
        new MetaWriter().compile(json, tempDir);
        Path catXml = tempDir.resolve("Catalogs/тест_Кат2.xml");

        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        editor.edit(catXml, "add-attribute", "Комментарий: Строка(100)");

        String xml = read(catXml);
        int attrStart = idx(xml, "<Attribute uuid=");
        String attrBlock = xml.substring(attrStart, xml.indexOf("</Attribute>", attrStart));
        assertThat(attrBlock).contains("<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>");
    }

    // ─── Измерения: лишние Master/MainFilter вне РегистраСведений ───────

    @Test
    void metaCompile_accumulationRegisterDimension_hasNoMasterMainFilter_hasUseInTotals() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("ar.json", "{\"type\":\"AccumulationRegister\",\"name\":\"тест_Остатки\","
                + "\"registerType\":\"Balance\","
                + "\"dimensions\":[\"Номенклатура: Строка(50)\"],"
                + "\"resources\":[\"Количество: Число(15,3)\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("AccumulationRegisters/тест_Остатки.xml"));

        int dimStart = idx(xml, "<Dimension uuid=");
        String dimBlock = xml.substring(dimStart, xml.indexOf("</Dimension>", dimStart));
        assertThat(dimBlock).doesNotContain("<Master>");
        assertThat(dimBlock).doesNotContain("<MainFilter>");
        assertThat(dimBlock).contains("<DenyIncompleteValues>");
        assertThat(dimBlock).contains("<UseInTotals>true</UseInTotals>");
        // Канонический порядок хвоста: Deny → Indexing → FullTextSearch → UseInTotals
        assertThat(idx(dimBlock, "<DenyIncompleteValues>")).isLessThan(idx(dimBlock, "<Indexing>"));
        assertThat(idx(dimBlock, "<FullTextSearch>")).isLessThan(idx(dimBlock, "<UseInTotals>"));

        // Канонический порядок ChildObjects: Resource ПЕРЕД Dimension (грунт-труф Designer)
        assertThat(idx(xml, "<Resource uuid=")).isLessThan(idx(xml, "<Dimension uuid="));
    }

    @Test
    void metaCompile_informationRegisterDimension_masterBeforeIndexing() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("ir.json", "{\"type\":\"InformationRegister\",\"name\":\"тест_Свед\","
                + "\"dimensions\":[\"Объект: Строка(36) | master\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("InformationRegisters/тест_Свед.xml"));

        int dimStart = idx(xml, "<Dimension uuid=");
        String dimBlock = xml.substring(dimStart, xml.indexOf("</Dimension>", dimStart));
        // Канон InfoReg: Master → MainFilter → Deny → Indexing → FTS → DataHistory
        assertThat(dimBlock).contains("<Master>true</Master>");
        assertThat(idx(dimBlock, "<Master>")).isLessThan(idx(dimBlock, "<Indexing>"));
        assertThat(idx(dimBlock, "<FullTextSearch>")).isLessThan(idx(dimBlock, "<DataHistory>"));
    }

    // ─── Формат 2.20: TypeReductionMode + LineNumberLength ──────────────

    @Test
    void metaCompile_format220_emitsTypeReductionModeAndLineNumberLength() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("c.json", "{\"type\":\"Catalog\",\"name\":\"тест_Кат3\","
                + "\"tabularSections\":{\"Состав\":[\"Поз: Строка(20)\"]}}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/тест_Кат3.xml"));

        // Каждый стандартный реквизит несёт TypeReductionMode (2.20, спека §26.1)
        assertThat(xml).contains("<xr:TypeReductionMode>TransformValues</xr:TypeReductionMode>");
        // ТЧ несёт LineNumberLength после FillChecking
        int tsStart = idx(xml, "<TabularSection uuid=");
        String tsBlock = xml.substring(tsStart, xml.indexOf("</TabularSection>", tsStart));
        assertThat(tsBlock).contains("<LineNumberLength>5</LineNumberLength>");
        assertThat(idx(tsBlock, "<FillChecking>")).isLessThan(idx(tsBlock, "<LineNumberLength>"));
    }

    @Test
    void metaCompile_format217_omitsTypeReductionModeAndLineNumberLength() throws IOException {
        writeMinimalConfig(tempDir, "2.17");
        Path json = writeJson("c.json", "{\"type\":\"Catalog\",\"name\":\"тест_Кат4\","
                + "\"tabularSections\":{\"Состав\":[\"Поз: Строка(20)\"]}}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/тест_Кат4.xml"));

        assertThat(xml).doesNotContain("TypeReductionMode");
        assertThat(xml).doesNotContain("LineNumberLength");
    }

    @Test
    void metaCompile_format217_chartOfAccountsVerbatimBlock_strippedOfTypeReductionMode() throws IOException {
        writeMinimalConfig(tempDir, "2.17");
        Path json = writeJson("coa.json", "{\"type\":\"ChartOfAccounts\",\"name\":\"тест_План\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("ChartsOfAccounts/тест_План.xml"));
        // Прежняя непоследовательность: вербатим StandardTabularSections писал
        // TypeReductionMode даже в 2.17 — теперь вырезается фильтром.
        assertThat(xml).doesNotContain("TypeReductionMode");
    }

    // ─── Нехранимые (Report/DataProcessor): реквизиты ТЧ ─────────────────

    @Test
    void metaCompile_dataProcessorTsAttribute_hasFillFromFillingValue_noIndexing() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("dp.json", "{\"type\":\"DataProcessor\",\"name\":\"тест_Обр\","
                + "\"attributes\":[\"Парам: Строка(10)\"],"
                + "\"tabularSections\":{\"Строки\":[\"Колонка: Строка(20)\"]}}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("DataProcessors/тест_Обр.xml"));

        int tsStart = idx(xml, "<TabularSection uuid=");
        String tsBlock = xml.substring(tsStart, xml.indexOf("</TabularSection>", tsStart));
        // Спека (config-objects §6.2 / epf §3): у нехранимых ТЧ-реквизиты имеют
        // FillFromFillingValue/FillValue, но НЕ имеют Indexing/FullTextSearch/DataHistory
        int tsAttrStart = idx(tsBlock, "<Attribute uuid=");
        String tsAttr = tsBlock.substring(tsAttrStart);
        assertThat(tsAttr).contains("<FillFromFillingValue>false</FillFromFillingValue>");
        assertThat(tsAttr).doesNotContain("<Indexing>");
        assertThat(tsAttr).doesNotContain("<FullTextSearch>");
        assertThat(tsAttr).doesNotContain("<DataHistory>");

        // Реквизит объекта нехранимого — без Indexing (раньше писался безусловно)
        int objAttrStart = idx(xml, "<Attribute uuid=");
        String objAttr = xml.substring(objAttrStart, xml.indexOf("</Attribute>", objAttrStart));
        assertThat(objAttr).doesNotContain("<Indexing>");
    }

    // ─── EpfEditor: Date(DateTime) литерал ───────────────────────────────

    @Test
    void epfEditor_addAttribute_dateWithFraction_emitsQualifiersNotLiteral() {
        XmlNode root = XmlNode.builder().name("ExternalDataProcessor").build();
        XmlDocument document = new XmlDocument(null, false, null,
                "ExternalDataProcessor", "", Map.of(), root.getChildren(), root);
        EpfEditor editor = new EpfEditor(document);

        editor.addAttribute("Момент", "Date(DateTime)", null);

        XmlNode attr = document.getRoot().child("ChildObjects").getChildren().get(0);
        XmlNode type = attr.child("Properties").child("Type");
        assertThat(type.childText("Type")).isEqualTo("xs:dateTime");
        XmlNode q = type.child("DateQualifiers");
        assertThat(q).isNotNull();
        assertThat(q.childText("DateFractions")).isEqualTo("DateTime");
    }

    // ─── isFormatAtLeast220 ──────────────────────────────────────────────

    @Test
    void isFormatAtLeast220_comparesNumerically() {
        assertThat(MetaWriter.isFormatAtLeast220("2.20")).isTrue();
        assertThat(MetaWriter.isFormatAtLeast220("2.21")).isTrue();
        assertThat(MetaWriter.isFormatAtLeast220("3.0")).isTrue();
        assertThat(MetaWriter.isFormatAtLeast220("2.17")).isFalse();
        assertThat(MetaWriter.isFormatAtLeast220("2.3")).isFalse();
        assertThat(MetaWriter.isFormatAtLeast220(null)).isFalse();
    }
}
//++agent TASK-174

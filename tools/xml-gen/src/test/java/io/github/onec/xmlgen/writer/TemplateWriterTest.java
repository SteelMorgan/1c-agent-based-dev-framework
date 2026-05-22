package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.MdoPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for TemplateWriter — template add/remove/add-help for arbitrary MDO types.
 */
class TemplateWriterTest {

    @TempDir
    Path tempDir;

    private final TemplateWriter writer = new TemplateWriter();

    // ============================================================
    // Fixtures
    // ============================================================

    /**
     * Create a minimal Document object XML at &lt;configDir&gt;/src/Documents/&lt;name&gt;.xml.
     * Returns path to the .xml file.
     */
    private Path createDocumentObject(Path configDir, String name) throws IOException {
        return createObject(configDir, "Documents", name, "Document", false);
    }

    private Path createReportObject(Path configDir, String name) throws IOException {
        return createObject(configDir, "Reports", name, "Report", true);
    }

    private Path createCatalogObject(Path configDir, String name) throws IOException {
        return createObject(configDir, "Catalogs", name, "Catalog", false);
    }

    private Path createObject(Path configDir, String typePlural, String name,
                               String typeSingular, boolean isReport) throws IOException {
        Path objDir = configDir.resolve("src").resolve(typePlural);
        Files.createDirectories(objDir);

        String mainDcsLine = isReport
                ? "\t\t\t<MainDataCompositionSchema></MainDataCompositionSchema>\n"
                : "";

        String xml = "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "  xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n"
                + "\t<" + typeSingular + " uuid=\"test-uuid\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + name + "</Name>\n"
                + mainDcsLine
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t</ChildObjects>\n"
                + "\t</" + typeSingular + ">\n"
                + "</MetaDataObject>\n";

        Path xmlPath = objDir.resolve(name + ".xml");
        Files.writeString(xmlPath, xml, StandardCharsets.UTF_8);
        return xmlPath;
    }

    // ============================================================
    // Test 1: template add for Document — creates files and registers
    // ============================================================

    @Test
    void testAddTemplate_SpreadsheetForDocument_CreatesStructureAndRegisters() throws IOException {
        createDocumentObject(tempDir, "ЗаказКлиента");

        MdoPath object = MdoPath.parse("Document.ЗаказКлиента");
        writer.addTemplate(tempDir, object, "ПФ_Счёт", "SpreadsheetDocument", null, false, "src");

        Path src = tempDir.resolve("src");
        Path tplMeta = src.resolve("Documents/ЗаказКлиента/Templates/ПФ_Счёт.xml");
        Path tplBody = src.resolve("Documents/ЗаказКлиента/Templates/ПФ_Счёт/Ext/Template.xml");
        Path objectXml = src.resolve("Documents/ЗаказКлиента.xml");

        assertThat(tplMeta).exists();
        assertThat(tplBody).exists();

        String meta = Files.readString(tplMeta, StandardCharsets.UTF_8);
        assertThat(meta).contains("<Name>ПФ_Счёт</Name>");
        assertThat(meta).contains("<TemplateType>SpreadsheetDocument</TemplateType>");

        String objContent = Files.readString(objectXml, StandardCharsets.UTF_8);
        assertThat(objContent).contains("<Template>ПФ_Счёт</Template>");
    }

    // ============================================================
    // Test 2: DCS for Report — sets MainDataCompositionSchema when --set-main-dcs
    // ============================================================

    @Test
    void testAddTemplate_DcsForReport_SetsMainWhenFlag() throws IOException {
        createReportObject(tempDir, "ОстаткиТоваров");

        MdoPath object = MdoPath.parse("Report.ОстаткиТоваров");
        writer.addTemplate(tempDir, object, "ОсновнаяСхема", "DataCompositionSchema", null, true, "src");

        Path objectXml = tempDir.resolve("src/Reports/ОстаткиТоваров.xml");
        String content = Files.readString(objectXml, StandardCharsets.UTF_8);

        assertThat(content).contains("<Template>ОсновнаяСхема</Template>");
        assertThat(content).contains("<MainDataCompositionSchema>Report.ОстаткиТоваров.Template.ОсновнаяСхема</MainDataCompositionSchema>");
    }

    // ============================================================
    // Test 3: DCS for Report — keeps existing MainDCS when --set-main-dcs NOT passed
    // ============================================================

    @Test
    void testAddTemplate_DcsForReport_KeepsExistingMainWithoutFlag() throws IOException {
        // Create report with existing MainDataCompositionSchema
        Path objXml = createReportObject(tempDir, "Продажи");
        // Manually write existing MainDataCompositionSchema
        String content = Files.readString(objXml, StandardCharsets.UTF_8);
        content = content.replace(
                "<MainDataCompositionSchema></MainDataCompositionSchema>",
                "<MainDataCompositionSchema>Report.Продажи.Template.СтараяСхема</MainDataCompositionSchema>");
        Files.writeString(objXml, content, StandardCharsets.UTF_8);

        MdoPath object = MdoPath.parse("Report.Продажи");
        writer.addTemplate(tempDir, object, "НоваяСхема", "DataCompositionSchema", null, false, "src");

        String afterContent = Files.readString(objXml, StandardCharsets.UTF_8);
        // Old MainDataCompositionSchema value must be preserved
        assertThat(afterContent).contains("Report.Продажи.Template.СтараяСхема");
        // НоваяСхема should be in ChildObjects (template registered) but NOT in MainDataCompositionSchema
        assertThat(afterContent).contains("<Template>НоваяСхема</Template>");
        assertThat(afterContent).doesNotContain("<MainDataCompositionSchema>Report.Продажи.Template.НоваяСхема</MainDataCompositionSchema>");
    }

    // ============================================================
    // Test 4: template remove — clears registration and deletes files
    // ============================================================

    @Test
    void testRemoveTemplate_ClearsRegistrationAndDeletesFiles() throws IOException {
        createDocumentObject(tempDir, "ЗаказКлиента");
        MdoPath object = MdoPath.parse("Document.ЗаказКлиента");

        writer.addTemplate(tempDir, object, "ПФ_Счёт", "SpreadsheetDocument", null, false, "src");

        Path tplMeta = tempDir.resolve("src/Documents/ЗаказКлиента/Templates/ПФ_Счёт.xml");
        assertThat(tplMeta).exists();

        writer.removeTemplate(tempDir, object, "ПФ_Счёт", "src");

        assertThat(tplMeta).doesNotExist();

        String objContent = Files.readString(tempDir.resolve("src/Documents/ЗаказКлиента.xml"),
                StandardCharsets.UTF_8);
        assertThat(objContent).doesNotContain("<Template>ПФ_Счёт</Template>");
    }

    // ============================================================
    // Test 5: remove main DCS — clears MainDataCompositionSchema
    // ============================================================

    @Test
    void testRemoveTemplate_OnMainDcs_ClearsMainDcsAttribute() throws IOException {
        createReportObject(tempDir, "ОстаткиТоваров");
        MdoPath object = MdoPath.parse("Report.ОстаткиТоваров");

        writer.addTemplate(tempDir, object, "ОсновнаяСхема", "DataCompositionSchema", null, true, "src");

        Path objXml = tempDir.resolve("src/Reports/ОстаткиТоваров.xml");
        assertThat(Files.readString(objXml, StandardCharsets.UTF_8))
                .contains("Report.ОстаткиТоваров.Template.ОсновнаяСхема");

        writer.removeTemplate(tempDir, object, "ОсновнаяСхема", "src");

        String after = Files.readString(objXml, StandardCharsets.UTF_8);
        assertThat(after).doesNotContain("ОсновнаяСхема");
        // MainDataCompositionSchema element cleared (empty value)
        assertThat(after).contains("<MainDataCompositionSchema></MainDataCompositionSchema>");
    }

    // ============================================================
    // Test 6: add-help — creates Help.xml and html file
    // ============================================================

    @Test
    void testAddHelp_CreatesHelpXmlAndHtml() throws IOException {
        createCatalogObject(tempDir, "Контрагенты");
        MdoPath object = MdoPath.parse("Catalog.Контрагенты");

        writer.addHelp(tempDir, object, "ru", "src");

        Path helpXml = tempDir.resolve("src/Catalogs/Контрагенты/Ext/Help.xml");
        Path htmlFile = tempDir.resolve("src/Catalogs/Контрагенты/Ext/Help/ru.html");

        assertThat(helpXml).exists();
        assertThat(htmlFile).exists();

        String helpContent = readFileContent(helpXml);
        assertThat(helpContent).contains("<Help");
        assertThat(helpContent).contains("<Page>ru</Page>");

        String html = Files.readString(htmlFile, StandardCharsets.UTF_8);
        assertThat(html).contains("<html>");
        assertThat(html).contains("charset=utf-8");
    }

    // ============================================================
    // Test 7: add-help with forms — IncludeHelpInContents added to form meta
    // ============================================================

    @Test
    void testAddHelp_WithForms_AddsIncludeHelpInContents() throws IOException {
        createCatalogObject(tempDir, "Номенклатура");

        // Create a fake form metadata file under Catalogs/Номенклатура/Forms/
        Path formsDir = tempDir.resolve("src/Catalogs/Номенклатура/Forms");
        Files.createDirectories(formsDir);
        String formMeta = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject>\n"
                + "\t<Form uuid=\"abc\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>ФормаСписка</Name>\n"
                + "\t\t\t<FormType>Managed</FormType>\n"
                + "\t\t</Properties>\n"
                + "\t</Form>\n"
                + "</MetaDataObject>\n";
        Files.writeString(formsDir.resolve("ФормаСписка.xml"), formMeta, StandardCharsets.UTF_8);

        MdoPath object = MdoPath.parse("Catalog.Номенклатура");
        writer.addHelp(tempDir, object, "ru", "src");

        String updatedForm = Files.readString(formsDir.resolve("ФормаСписка.xml"), StandardCharsets.UTF_8);
        assertThat(updatedForm).contains("IncludeHelpInContents");
    }

    // ============================================================
    // Test 8: add-help repeat for same lang — idempotent (no overwrite)
    // ============================================================

    @Test
    void testAddHelp_RepeatSameLang_Idempotent() throws IOException {
        createCatalogObject(tempDir, "Контрагенты");
        MdoPath object = MdoPath.parse("Catalog.Контрагенты");

        writer.addHelp(tempDir, object, "ru", "src");

        // Write a unique marker in the HTML file
        Path htmlFile = tempDir.resolve("src/Catalogs/Контрагенты/Ext/Help/ru.html");
        Files.writeString(htmlFile, "<!-- custom content -->\n", StandardCharsets.UTF_8);

        // Second call must NOT overwrite
        writer.addHelp(tempDir, object, "ru", "src");

        String content = Files.readString(htmlFile, StandardCharsets.UTF_8);
        assertThat(content).contains("custom content");
    }

    // ============================================================
    // Edge cases
    // ============================================================

    @Test
    void testAddTemplate_ObjectNotFound_Throws() {
        MdoPath object = MdoPath.parse("Document.НесуществующийДокумент");

        assertThatThrownBy(() -> writer.addTemplate(tempDir, object, "ПФ_Тест", "SpreadsheetDocument",
                null, false, "src"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("НесуществующийДокумент");
    }

    @Test
    void testRemoveTemplate_NonExistent_WarningAndNoop() throws IOException {
        createDocumentObject(tempDir, "ЗаказКлиента");
        MdoPath object = MdoPath.parse("Document.ЗаказКлиента");

        // Should not throw, just print warning
        assertThatCode(() -> writer.removeTemplate(tempDir, object, "НесуществующийМакет", "src"))
                .doesNotThrowAnyException();
    }

    @Test
    void testMdoPath_ParseValid() {
        MdoPath p = MdoPath.parse("Document.ЗаказКлиента");
        assertThat(p.getType()).isEqualTo("Document");
        assertThat(p.getName()).isEqualTo("ЗаказКлиента");
        assertThat(p.getRelativeDir()).isEqualTo("Documents/ЗаказКлиента");
    }

    @Test
    void testMdoPath_ParseInvalid_Throws() {
        assertThatThrownBy(() -> MdoPath.parse("ЗаказКлиента"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MdoPath.parse(".NoType"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MdoPath.parse("Type."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseTemplateType_Aliases() {
        // Should not throw for known aliases
        assertThatCode(() -> TemplateWriter.parseTemplateType("HTMLDocument")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("html")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("SpreadsheetDocument")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("mxl")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("DataCompositionSchema")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("BinaryData")).doesNotThrowAnyException();
        assertThatCode(() -> TemplateWriter.parseTemplateType("TextDocument")).doesNotThrowAnyException();
    }

    @Test
    void testParseTemplateType_Unknown_Throws() {
        assertThatThrownBy(() -> TemplateWriter.parseTemplateType("UnknownType"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown template type");
    }

    // ============================================================
    // Helper
    // ============================================================

    private String readFileContent(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        // strip BOM if present
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            return new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        }
        return new String(raw, StandardCharsets.UTF_8);
    }
}

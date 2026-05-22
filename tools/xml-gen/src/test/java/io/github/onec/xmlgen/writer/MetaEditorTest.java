package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MetaBatchDsl;
import io.github.onec.xmlgen.model.CompositeType;
import io.github.onec.xmlgen.model.MlText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for MetaEditor — batch-patch operations (SPEC §9).
 */
class MetaEditorTest {

    @TempDir
    Path tempDir;

    // ─── Minimal Catalog XML fixture ────────────────────────────────────────

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Write a minimal Catalog XML file with BOM.
     * Contains Synonym (MLText), one Attribute (ИНН) with empty Synonym,
     * and one TabularSection (Контакты) with a ChildObjects element.
     */
    private Path writeCatalogXml(Path dir, String name) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\">\n"
                + "\t<Catalog uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\n"
                + "\t\t<InternalInfo/>\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + name + "</Name>\n"
                + "\t\t\t<Synonym>\n"
                + "\t\t\t\t<v8:item>\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t\t<v8:content>Старый синоним</v8:content>\n"
                + "\t\t\t\t</v8:item>\n"
                + "\t\t\t</Synonym>\n"
                + "\t\t\t<Comment/>\n"
                + "\t\t\t<UseStandardCommands>true</UseStandardCommands>\n"
                + "\t\t\t<BasedOn/>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t\t<Attribute uuid=\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\">\n"
                + "\t\t\t\t<Properties>\n"
                + "\t\t\t\t\t<Name>ИНН</Name>\n"
                + "\t\t\t\t\t<Synonym/>\n"
                + "\t\t\t\t\t<Comment/>\n"
                + "\t\t\t\t\t<Type>\n"
                + "\t\t\t\t\t\t<v8:Type>xs:string</v8:Type>\n"
                + "\t\t\t\t\t\t<v8:StringQualifiers>\n"
                + "\t\t\t\t\t\t\t<v8:Length>12</v8:Length>\n"
                + "\t\t\t\t\t\t\t<v8:AllowedLength>Variable</v8:AllowedLength>\n"
                + "\t\t\t\t\t\t</v8:StringQualifiers>\n"
                + "\t\t\t\t\t</Type>\n"
                + "\t\t\t\t\t<FillChecking>DontCheck</FillChecking>\n"
                + "\t\t\t\t</Properties>\n"
                + "\t\t\t</Attribute>\n"
                + "\t\t\t<TabularSection uuid=\"cccccccc-cccc-cccc-cccc-cccccccccccc\">\n"
                + "\t\t\t\t<InternalInfo/>\n"
                + "\t\t\t\t<Properties>\n"
                + "\t\t\t\t\t<Name>Контакты</Name>\n"
                + "\t\t\t\t\t<Synonym/>\n"
                + "\t\t\t\t\t<Comment/>\n"
                + "\t\t\t\t\t<FillChecking>DontCheck</FillChecking>\n"
                + "\t\t\t\t</Properties>\n"
                + "\t\t\t\t<ChildObjects/>\n"
                + "\t\t\t</TabularSection>\n"
                + "\t\t</ChildObjects>\n"
                + "\t</Catalog>\n"
                + "</MetaDataObject>\n";

        Path file = dir.resolve(name + ".xml");
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        Files.write(file, withBom);
        return file;
    }

    private String readXml(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        int offset = (bytes.length >= 3
                && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private MetaEditor silentEditor() {
        return new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    // ─── Helper: build batch DSL programmatically ────────────────────────

    private MetaBatchDsl parseBatch(String json) throws Exception {
        return new ObjectMapper().readValue(json, MetaBatchDsl.class);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Tests
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * All operations in a batch succeed → file updated correctly.
     */
    @Test
    void testBatch_AllOperations_Succeeds() throws Exception {
        Path xml = writeCatalogXml(tempDir, "Контрагенты");

        String batchJson = """
                {
                  "operations": [
                    { "op": "modify-property", "name": "Synonym",
                      "value": {"ru": "Контрагенты", "en": "Counterparties"} },
                    { "op": "add-attribute", "name": "Комментарий",
                      "type": "string(255)" },
                    { "op": "modify-attribute", "name": "ИНН",
                      "fillChecking": "ShowError" },
                    { "op": "modify-tabularSection", "name": "Контакты",
                      "operations": [
                        { "op": "add-attribute", "name": "Тип",
                          "type": "string(50)" }
                      ]
                    }
                  ]
                }
                """;

        MetaBatchDsl batch = parseBatch(batchJson);
        silentEditor().applyBatch(xml, batch);

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:content>Контрагенты</v8:content>")
                .contains("<v8:content>Counterparties</v8:content>")
                .contains("<Name>Комментарий</Name>")
                .contains("<FillChecking>ShowError</FillChecking>")
                .contains("<Name>Тип</Name>");
    }

    /**
     * If operation N fails, the file must NOT be changed (rollback).
     */
    @Test
    void testBatch_FailureMidway_NoFileChange() throws Exception {
        Path xml = writeCatalogXml(tempDir, "Товары");
        byte[] original = Files.readAllBytes(xml);

        String batchJson = """
                {
                  "operations": [
                    { "op": "add-attribute", "name": "Артикул", "type": "string(50)" },
                    { "op": "INVALID_OP", "name": "Something" }
                  ]
                }
                """;

        MetaBatchDsl batch = parseBatch(batchJson);

        assertThatThrownBy(() -> silentEditor().applyBatch(xml, batch))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Batch rolled back");

        // File must be unchanged
        byte[] after = Files.readAllBytes(xml);
        assertThat(after).isEqualTo(original);
    }

    /**
     * MLText Synonym edit preserves existing other-language entries.
     */
    @Test
    void testMlText_EditSynonym_PreservesOtherLangs() throws Exception {
        Path xml = writeCatalogXml(tempDir, "Документы");

        // First add an "en" entry
        String batchJson1 = """
                {
                  "operations": [
                    { "op": "modify-property", "name": "Synonym",
                      "value": {"ru": "Документы", "en": "Documents"} }
                  ]
                }
                """;
        silentEditor().applyBatch(xml, parseBatch(batchJson1));

        // Now update only "ru" — "en" must be preserved
        String batchJson2 = """
                {
                  "operations": [
                    { "op": "modify-property", "name": "Synonym",
                      "value": {"ru": "Мои документы"} }
                  ]
                }
                """;
        silentEditor().applyBatch(xml, parseBatch(batchJson2));

        String result = readXml(xml);
        assertThat(result)
                .contains("<v8:content>Мои документы</v8:content>")
                .contains("<v8:content>Documents</v8:content>");
    }

    /**
     * Composite type inline syntax generates correct XML with multiple type entries.
     */
    @Test
    void testCompositeType_Inline_GeneratesCorrectXml() throws Exception {
        Path xml = writeCatalogXml(tempDir, "Номенклатура");

        String batchJson = """
                {
                  "operations": [
                    { "op": "add-attribute", "name": "Значение",
                      "type": "string(50)|number(15,2)" }
                  ]
                }
                """;
        silentEditor().applyBatch(xml, parseBatch(batchJson));

        String result = readXml(xml);
        // Both types should appear in the Type block
        assertThat(result)
                .contains("<Name>Значение</Name>")
                .contains("xs:string")
                .contains("xs:decimal");
    }

    /**
     * modify-tabularSection adds an attribute inside the TS ChildObjects.
     */
    @Test
    void testBatch_ModifyTabularSection_AddsAttribute() throws Exception {
        Path xml = writeCatalogXml(tempDir, "СправочникС_ТЧ");

        String batchJson = """
                {
                  "operations": [
                    { "op": "modify-tabularSection", "name": "Контакты",
                      "operations": [
                        { "op": "add-attribute", "name": "Телефон", "type": "string(20)" }
                      ]
                    }
                  ]
                }
                """;
        silentEditor().applyBatch(xml, parseBatch(batchJson));

        String result = readXml(xml);
        assertThat(result).contains("<Name>Телефон</Name>");

        // Should be inside TabularSection block
        int tsStart = result.indexOf("<TabularSection ");
        int tsEnd = result.indexOf("</TabularSection>");
        assertThat(result.substring(tsStart, tsEnd)).contains("Телефон");
    }

    /**
     * add-property sets a scalar root property.
     */
    @Test
    void testBatch_AddProperty_SetsScalar() throws Exception {
        Path xml = writeCatalogXml(tempDir, "КаталогX");

        String batchJson = """
                {
                  "operations": [
                    { "op": "add-property", "name": "UseStandardCommands", "value": "false" }
                  ]
                }
                """;
        silentEditor().applyBatch(xml, parseBatch(batchJson));

        String result = readXml(xml);
        assertThat(result).contains("<UseStandardCommands>false</UseStandardCommands>");
    }

    /**
     * CompositeType.parse handles single type without delimiter.
     */
    @Test
    void testCompositeType_SingleType_ParsedCorrectly() {
        List<String> types = CompositeType.parse("string(100)");
        assertThat(types).hasSize(1).containsExactly("string(100)");
    }

    /**
     * CompositeType.parse handles pipe-separated composite types.
     */
    @Test
    void testCompositeType_PipeSeparated_ParsedCorrectly() {
        List<String> types = CompositeType.parse("string(50)|number(15,2)|CatalogRef.Склады");
        assertThat(types).hasSize(3)
                .containsExactly("string(50)", "number(15,2)", "CatalogRef.Склады");
    }

    /**
     * CompositeType.parse handles plus-separated legacy syntax.
     */
    @Test
    void testCompositeType_PlusSeparated_ParsedCorrectly() {
        List<String> types = CompositeType.parse("String(50) + Number(15,2)");
        assertThat(types).hasSize(2);
    }

    /**
     * CompositeType.parse throws on empty input.
     */
    @Test
    void testCompositeType_EmptyInput_Throws() {
        assertThatThrownBy(() -> CompositeType.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CompositeType.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * MlText.applyToBlock: adds a new language entry when it doesn't exist.
     */
    @Test
    void testMlText_ApplyToBlock_AddsNewLanguage() {
        String block = "<Attribute uuid=\"x\">\n"
                + "\t<Properties>\n"
                + "\t\t<Name>Test</Name>\n"
                + "\t\t<Synonym>\n"
                + "\t\t\t<v8:item>\n"
                + "\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t<v8:content>Тест</v8:content>\n"
                + "\t\t\t</v8:item>\n"
                + "\t\t</Synonym>\n"
                + "\t</Properties>\n"
                + "</Attribute>\n";

        MlText mlText = new MlText();
        mlText.setLang("en", "Test");

        String result = mlText.applyToBlock(block, "Synonym");
        assertThat(result)
                .contains("<v8:lang>ru</v8:lang>")
                .contains("<v8:content>Тест</v8:content>")
                .contains("<v8:lang>en</v8:lang>")
                .contains("<v8:content>Test</v8:content>");
    }

    /**
     * MlText.applyToBlock: replaces existing language content.
     */
    @Test
    void testMlText_ApplyToBlock_ReplacesExistingContent() {
        String block = "<Attribute uuid=\"x\">\n"
                + "\t<Properties>\n"
                + "\t\t<Synonym>\n"
                + "\t\t\t<v8:item>\n"
                + "\t\t\t\t<v8:lang>ru</v8:lang>\n"
                + "\t\t\t\t<v8:content>Старое</v8:content>\n"
                + "\t\t\t</v8:item>\n"
                + "\t\t</Synonym>\n"
                + "\t</Properties>\n"
                + "</Attribute>\n";

        MlText mlText = new MlText();
        mlText.setLang("ru", "Новое");

        String result = mlText.applyToBlock(block, "Synonym");
        assertThat(result)
                .contains("<v8:content>Новое</v8:content>")
                .doesNotContain("<v8:content>Старое</v8:content>");
    }

    /**
     * MlText.applyToBlock: handles self-closing tag by expanding it.
     */
    @Test
    void testMlText_ApplyToBlock_ExpandsSelfClosingTag() {
        String block = "<Attribute uuid=\"x\">\n"
                + "\t<Properties>\n"
                + "\t\t<Synonym/>\n"
                + "\t</Properties>\n"
                + "</Attribute>\n";

        MlText mlText = new MlText();
        mlText.setLang("ru", "Тест");

        String result = mlText.applyToBlock(block, "Synonym");
        assertThat(result)
                .contains("<Synonym>")
                .contains("<v8:lang>ru</v8:lang>")
                .contains("<v8:content>Тест</v8:content>")
                .doesNotContain("<Synonym/>");
    }

    /**
     * Inline `--op` mode still works after batch changes (backward compat).
     */
    @Test
    void testInlineOp_BackwardCompat_StillWorks() throws Exception {
        Path xml = writeCatalogXml(tempDir, "Партнеры");

        silentEditor().edit(xml, "add-attribute", "Email: string(200)");

        String result = readXml(xml);
        assertThat(result).contains("<Name>Email</Name>");
    }

    /**
     * Batch with missing-required op field must throw with descriptive message.
     */
    @Test
    void testBatch_MissingOpField_ThrowsDescriptively() throws Exception {
        Path xml = writeCatalogXml(tempDir, "ТестВалидации");

        // No "op" field
        String batchJson = """
                {
                  "operations": [
                    { "name": "SomeAttr", "type": "string(10)" }
                  ]
                }
                """;
        MetaBatchDsl batch = parseBatch(batchJson);

        assertThatThrownBy(() -> silentEditor().applyBatch(xml, batch))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("'op' field is required");
    }

    /**
     * modify-tabularSection targeting non-existent TS must throw (rolled back).
     */
    @Test
    void testBatch_ModifyNonExistentTabularSection_ThrowsRollback() throws Exception {
        Path xml = writeCatalogXml(tempDir, "БезТЧ");
        byte[] original = Files.readAllBytes(xml);

        String batchJson = """
                {
                  "operations": [
                    { "op": "modify-tabularSection", "name": "НесуществующаяТЧ",
                      "operations": [
                        { "op": "add-attribute", "name": "Поле", "type": "string(10)" }
                      ]
                    }
                  ]
                }
                """;

        MetaBatchDsl batch = parseBatch(batchJson);
        assertThatThrownBy(() -> silentEditor().applyBatch(xml, batch))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Batch rolled back");

        // File unchanged
        assertThat(Files.readAllBytes(xml)).isEqualTo(original);
    }
}

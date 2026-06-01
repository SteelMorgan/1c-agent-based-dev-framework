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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Регрессионные тесты дефектов генерации метаданных TASK-171 (D-1..D-6).
 */
class MetaWriterTask171Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Прочитать файл, отрезая BOM. */
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

    /** Минимальный Configuration.xml с заданной версией формата и пустым ChildObjects. */
    private Path writeMinimalConfig(Path dir, String formatVersion) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"" + formatVersion + "\">\n"
                + "\t<Configuration uuid=\"aaaaaaaa-0000-0000-0000-000000000000\">\n"
                + "\t\t<Properties>\n\t\t\t<Name>TestCfg</Name>\n\t\t</Properties>\n"
                + "\t\t<ChildObjects/>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
        Path cfg = dir.resolve("Configuration.xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        Files.write(cfg, out);
        return cfg;
    }

    // ─── D-2: namespace ──────────────────────────────────────────────────

    @Test
    void compile_usesCanonicalNamespace_notBrokenV83() throws IOException {
        Path json = writeJson("c.json",
                "{\"type\":\"Catalog\",\"name\":\"test_Кат\",\"synonym\":\"Кат\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/test_Кат.xml"));
        assertThat(xml).contains("xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\"");
        assertThat(xml).contains("xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\"");
        assertThat(xml).contains("xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"");
        assertThat(xml).doesNotContain("http://v8.3/xcf/");
    }

    // ─── D-4: CommonModule без InternalInfo/ChildObjects ─────────────────

    @Test
    void compile_commonModule_hasNoInternalInfoOrChildObjects() throws IOException {
        Path json = writeJson("cm.json",
                "{\"type\":\"CommonModule\",\"name\":\"test_Хелпер\",\"context\":\"server\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("CommonModules/test_Хелпер.xml"));
        assertThat(xml).doesNotContain("<InternalInfo");
        assertThat(xml).doesNotContain("<ChildObjects");
        assertThat(xml).contains("<CommonModule uuid=");
        assertThat(xml).contains("<Server>true</Server>");
    }

    // ─── D-5: enumValues алиас ───────────────────────────────────────────

    @Test
    void compile_enum_acceptsEnumValuesAlias() throws IOException {
        Path json = writeJson("e.json",
                "{\"type\":\"Enum\",\"name\":\"test_Стат\",\"enumValues\":[{\"name\":\"Новый\"},{\"name\":\"Закрыт\"}]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Enums/test_Стат.xml"));
        assertThat(xml).contains("<EnumValue uuid=");
        assertThat(xml).contains("<Name>Новый</Name>");
        assertThat(xml).contains("<Name>Закрыт</Name>");
    }

    @Test
    void compile_enum_stillAcceptsValuesKey() throws IOException {
        Path json = writeJson("e2.json",
                "{\"type\":\"Enum\",\"name\":\"test_Стат2\",\"values\":[{\"name\":\"A\"}]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Enums/test_Стат2.xml"));
        assertThat(xml).contains("<Name>A</Name>");
    }

    // ─── D-1: предопределённые в compile ─────────────────────────────────

    @Test
    void compile_catalog_writesPredefinedXml() throws IOException {
        Path json = writeJson("p.json",
                "{\"type\":\"Catalog\",\"name\":\"test_Дог\",\"codeLength\":9,"
                + "\"predefinedItems\":[{\"name\":\"Аренда\",\"description\":\"Договор аренды\"},\"Прочее\"]}");
        new MetaWriter().compile(json, tempDir);
        Path predefined = tempDir.resolve("Catalogs/test_Дог/Ext/Predefined.xml");
        assertThat(Files.exists(predefined)).isTrue();
        String xml = read(predefined);
        assertThat(xml).contains("xsi:type=\"CatalogPredefinedItems\"");
        assertThat(xml).contains("xmlns=\"http://v8.1c.ru/8.3/xcf/predef\"");
        assertThat(xml).contains("<Name>Аренда</Name>");
        assertThat(xml).contains("<Code>000000001</Code>");
        assertThat(xml).contains("<Description>Договор аренды</Description>");
        // строковый элемент: description = name, авто-код
        assertThat(xml).contains("<Name>Прочее</Name>");
        assertThat(xml).contains("<Code>000000002</Code>");
    }

    @Test
    void compile_enum_doesNotWritePredefined() throws IOException {
        Path json = writeJson("ep.json",
                "{\"type\":\"Enum\",\"name\":\"test_E3\",\"predefinedItems\":[{\"name\":\"X\"}]}");
        new MetaWriter().compile(json, tempDir);
        // Enum не поддерживает предопределённые — файла быть не должно
        assertThat(Files.exists(tempDir.resolve("Enums/test_E3/Ext/Predefined.xml"))).isFalse();
    }

    // ─── D-6: версия формата из Configuration.xml ────────────────────────

    @Test
    void compile_objectAndPredefined_inheritConfigVersion() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("v.json",
                "{\"type\":\"Catalog\",\"name\":\"test_V\",\"predefinedItems\":[{\"name\":\"A\"}]}");
        new MetaWriter().compile(json, tempDir);
        String obj = read(tempDir.resolve("Catalogs/test_V.xml"));
        assertThat(obj).contains("version=\"2.20\"");
        String pre = read(tempDir.resolve("Catalogs/test_V/Ext/Predefined.xml"));
        assertThat(pre).contains("version=\"2.20\"");
    }

    @Test
    void compile_fallsBackToDefaultVersion_whenNoConfig() throws IOException {
        Path json = writeJson("nf.json", "{\"type\":\"Catalog\",\"name\":\"test_NF\"}");
        new MetaWriter().compile(json, tempDir);
        String obj = read(tempDir.resolve("Catalogs/test_NF.xml"));
        assertThat(obj).contains("version=\"2.17\"");
    }

    // ─── D-3: регистрация в Configuration.xml ────────────────────────────

    @Test
    void compile_registersObjectInConfiguration() throws IOException {
        Path cfg = writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("r.json", "{\"type\":\"Catalog\",\"name\":\"test_Reg\"}");
        new MetaWriter().compile(json, tempDir);
        String config = read(cfg);
        assertThat(config).contains("<Catalog>test_Reg</Catalog>");
    }

    @Test
    void compile_withoutConfig_doesNotThrow() throws IOException {
        Path json = writeJson("nc.json", "{\"type\":\"Catalog\",\"name\":\"test_NoCfg\"}");
        // нет Configuration.xml — должен пройти с предупреждением, без исключения
        new MetaWriter().compile(json, tempDir);
        assertThat(Files.exists(tempDir.resolve("Catalogs/test_NoCfg.xml"))).isTrue();
    }

    // ─── D-1 (edit): meta edit add-predefined ────────────────────────────

    @Test
    void edit_addPredefined_createsAndAppends() throws IOException {
        // компилируем каталог без предопределённых
        Path json = writeJson("cat.json", "{\"type\":\"Catalog\",\"name\":\"test_Ed\"}");
        new MetaWriter().compile(json, tempDir);
        Path objXml = tempDir.resolve("Catalogs/test_Ed.xml");

        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        editor.edit(objXml, "add-predefined", "Один;;Два|Второй");
        Path pre = tempDir.resolve("Catalogs/test_Ed/Ext/Predefined.xml");
        assertThat(Files.exists(pre)).isTrue();
        String xml = read(pre);
        assertThat(xml).contains("<Name>Один</Name>").contains("<Code>000000001</Code>");
        assertThat(xml).contains("<Name>Два</Name>").contains("<Description>Второй</Description>")
                .contains("<Code>000000002</Code>");

        // повторный вызов: дозапись со следующим кодом, дубликат пропускается
        editor.edit(objXml, "add-predefined", "Три;;Один");
        String xml2 = read(pre);
        assertThat(xml2).contains("<Name>Три</Name>").contains("<Code>000000003</Code>");
        // "Один" не задублирован
        assertThat(xml2.split("<Name>Один</Name>", -1).length - 1).isEqualTo(1);
    }

    @Test
    void edit_addPredefined_onUnsupportedType_throws() throws IOException {
        Path json = writeJson("en.json", "{\"type\":\"Enum\",\"name\":\"test_EnEd\"}");
        new MetaWriter().compile(json, tempDir);
        Path objXml = tempDir.resolve("Enums/test_EnEd.xml");
        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        assertThatThrownBy(() -> editor.edit(objXml, "add-predefined", "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не поддерживает предопределённые");
    }
}

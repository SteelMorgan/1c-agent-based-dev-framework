package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-175 Phase 4 (Tester): edge-кейс W-05 (XG-35) — вырожденный вход borrow:
 * базовая форма БЕЗ {@code AutoCommandBar} и БЕЗ {@code ChildItems}.
 *
 * <p>Контракт {@code buildFormXmlWithBaseForm} (зеркало cfe-borrow.py):
 * отсутствующий ACB не эмитится вовсе (ни top-level, ни в BaseForm);
 * отсутствующие ChildItems заменяются {@code <ChildItems/>}; strip-операции
 * на пустом дереве не падают. Phase 3b покрыла только «богатые» Designer-фикстуры
 * (Document/List/TypeLink/SMS) — вырожденная ветка {@code null} не покрыта.</p>
 *
 * <p>Хелперы — зеркало {@code ExtensionEditorTask175Test} (3b); вырожденная форма
 * синтезируется inline (test fixture for tools, прецедент F-02/F-03 в 3b).</p>
 */
class ExtensionEditorTask175Phase4Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static void writeBom(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, all, 0, BOM.length);
        System.arraycopy(body, 0, all, BOM.length, body.length);
        Files.write(file, all);
    }

    private static String readNoBom(Path file) throws Exception {
        byte[] b = Files.readAllBytes(file);
        if (b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2]) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Вырожденная Designer-форма: только Title + Attributes, без ACB и ChildItems. */
    private static final String DEGENERATE_FORM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\""
                    + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.20\">\n"
                    + "\t<Title>\n"
                    + "\t\t<v8:item>\n"
                    + "\t\t\t<v8:lang>ru</v8:lang>\n"
                    + "\t\t\t<v8:content>Вырожденная форма</v8:content>\n"
                    + "\t\t</v8:item>\n"
                    + "\t</Title>\n"
                    + "\t<Attributes/>\n"
                    + "</Form>\n";

    private Path makeConfigWithDegenerateForm() throws Exception {
        Path cfg = tempDir.resolve("cfg-degenerate");
        writeBom(cfg.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Base</Name></Properties>\n"
                        + "\t\t<ChildObjects><Catalog>Тестовый</Catalog></ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve("Catalogs/Тестовый.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<Catalog uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "\t\t<Properties><Name>Тестовый</Name></Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Form>ФормаЭлемента</Form>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Catalog>\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve("Catalogs/Тестовый/Forms/ФормаЭлемента.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<Form uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "\t\t<Properties><Name>ФормаЭлемента</Name></Properties>\n"
                        + "\t</Form>\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve("Catalogs/Тестовый/Forms/ФормаЭлемента/Ext/Form.xml"),
                DEGENERATE_FORM);
        return cfg;
    }

    private Path makeExtension() throws Exception {
        Path ext = tempDir.resolve("ext-degenerate");
        writeBom(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000099\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>Ext1</Name>\n"
                        + "\t\t\t<ObjectBelonging>Own</ObjectBelonging>\n"
                        + "\t\t\t<NamePrefix>Расш1_</NamePrefix>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        return ext;
    }

    private ExtensionEditor silent() {
        return new ExtensionEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    /**
     * Edge-кейс W-05: borrow формы без ACB и без ChildItems не падает и
     * даёт корректный вырожденный результат: {@code <ChildItems/>} +
     * {@code <Attributes/>} + {@code BaseForm} с тем же составом; ACB
     * не появляется из ниоткуда; strip-цепочки на null-ACB не применяются.
     */
    @Test
    void w05_degenerateFormWithoutAcbAndChildItems_borrowDoesNotFail() throws Exception {
        Path cfg = makeConfigWithDegenerateForm();
        Path ext = makeExtension();

        assertThatCode(() -> silent().borrow(ext, cfg, "Catalog.Тестовый.Form.ФормаЭлемента"))
                .as("borrow вырожденной формы (без ACB, без ChildItems) не должен падать")
                .doesNotThrowAnyException();

        Path result = ext.resolve("Catalogs/Тестовый/Forms/ФормаЭлемента/Ext/Form.xml");
        assertThat(result).as("Form.xml расширения должен быть создан").exists();
        String content = readNoBom(result);

        assertThat(content)
                .as("отсутствующие ChildItems источника → самозакрытый <ChildItems/>")
                .contains("<ChildItems/>");
        assertThat(content)
                .as("секция BaseForm обязана присутствовать с версией исходной формы")
                .contains("<BaseForm version=\"2.20\">");
        assertThat(content)
                .as("ACB не должен синтезироваться из ниоткуда — источник его не содержит")
                .doesNotContain("<AutoCommandBar");
        assertThat(content)
                .as("strip-цели в вырожденном выводе отсутствуют")
                .doesNotContain("<ExcludedCommand>")
                .doesNotContain("<DataPath>");
    }
}

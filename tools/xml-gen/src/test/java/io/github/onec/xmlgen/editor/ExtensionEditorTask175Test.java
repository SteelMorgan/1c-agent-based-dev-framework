package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 W-05 (XG-35): {@code extension borrow} обязан выполнять strip-операции
 * над заимствованной формой — семантика безусловной ветки strip {@code cfe-borrow.py}
 * @ HEAD upstream (коммиты Широкова 7abe26af, de7e943d, 84d078bd).
 *
 * <p>Полный strip-набор (technical-design §3.3 W-05):
 * НЕТ {@code ExcludedCommand} (в ACB и во вложенных CommandSet),
 * НЕТ {@code DataPath} у кнопок AutoCommandBar,
 * НЕТ {@code DataPath}/{@code TitleDataPath}/{@code RowPictureDataPath} в ChildItems,
 * НЕТ top-level {@code CommandSet},
 * НЕТ блоков {@code TypeLink} с {@code xr:DataPath Items.*},
 * НЕТ element-level {@code Events} в ChildItems;
 * ЕСТЬ ChildItems AutoCommandBar с кнопками и {@code CommandName=0}.</p>
 *
 * <p>Условный keep {@code Объект.*} (флаг {@code -BorrowMainAttribute}, upstream
 * new-feature f7695a95) — ВНЕ объёма кампании (R-N.1), здесь не тестируется.</p>
 *
 * <p>Фикстуры — байт-в-байт копии Designer-выгрузок проекта GBIG PAM
 * (исключение «test fixtures for tools» правила no-manual-xml-edit, R-N.5):
 * см. src/test/resources/extension/borrow-src-*.xml.</p>
 *
 * <p>Тест работает ТОЛЬКО через публичный {@link ExtensionEditor#borrow} на временной
 * структуре каталогов ext+config в {@code @TempDir}; private
 * {@code buildFormXmlWithBaseForm} напрямую/через reflection НЕ вызывается.</p>
 */
class ExtensionEditorTask175Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // ─── Helpers ───────────────────────────────────────────────────────

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

    private Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/extension/" + name),
                "Fixture not found in test resources: /extension/" + name).toURI());
    }

    /**
     * Минимальная базовая конфигурация: один объект {@code typeName.objName}
     * с формой {@code formName}, чьим Ext/Form.xml становится байт-в-байт копия фикстуры.
     */
    private Path makeConfigWithForm(String dirName, String typeName, String objName,
                                    String formName, String fixtureName) throws Exception {
        Path cfg = tempDir.resolve("cfg-" + fixtureName.replace(".xml", ""));
        writeBom(cfg.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Base</Name></Properties>\n"
                        + "\t\t<ChildObjects><" + typeName + ">" + objName + "</" + typeName + "></ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve(dirName + "/" + objName + ".xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<" + typeName + " uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "\t\t<Properties><Name>" + objName + "</Name></Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Form>" + formName + "</Form>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</" + typeName + ">\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve(dirName + "/" + objName + "/Forms/" + formName + ".xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<Form uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "\t\t<Properties><Name>" + formName + "</Name></Properties>\n"
                        + "\t</Form>\n"
                        + "</MetaDataObject>");
        // Designer-выгрузка копируется байт-в-байт (BOM/CRLF сохраняются)
        Path formXml = cfg.resolve(dirName + "/" + objName + "/Forms/" + formName + "/Ext/Form.xml");
        Files.createDirectories(formXml.getParent());
        Files.copy(fixture(fixtureName), formXml);
        return cfg;
    }

    private Path makeExtension(String suffix) throws Exception {
        Path ext = tempDir.resolve("ext-" + suffix);
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

    /** borrow формы и чтение результата ext .../Ext/Form.xml. */
    private String borrowAndRead(String dirName, String typeName, String objName,
                                 String formName, String fixtureName) throws Exception {
        Path cfg = makeConfigWithForm(dirName, typeName, objName, formName, fixtureName);
        Path ext = makeExtension(fixtureName.replace(".xml", ""));
        silent().borrow(ext, cfg, typeName + "." + objName + ".Form." + formName);
        Path result = ext.resolve(dirName + "/" + objName + "/Forms/" + formName + "/Ext/Form.xml");
        assertThat(result).as("borrow должен создать Form.xml расширения").exists();
        return readNoBom(result);
    }

    /** Top-level AutoCommandBar результата (до BaseForm-секции). */
    private static String topAutoCommandBar(String formXml) {
        int baseFormIdx = formXml.indexOf("<BaseForm");
        String head = baseFormIdx > 0 ? formXml.substring(0, baseFormIdx) : formXml;
        Matcher m = Pattern.compile("<AutoCommandBar\\b.*?</AutoCommandBar>", Pattern.DOTALL).matcher(head);
        assertThat(m.find()).as("в результате borrow ожидается top-level AutoCommandBar").isTrue();
        return m.group(0);
    }

    // ═══════════════════════════════════════════════════════════════════
    // W-05: strip-набор по каждой фикстуре
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Фикстура: Documents/_ДемоСписаниеТоваров/Forms/ФормаДокумента (Designer-копия).
     * ACB «ФормаКоманднаяПанель» с кнопками, внутри ACB есть Button с DataPath;
     * в ChildItems — множественные DataPath.
     * Upstream: de7e943d (strip DataPath в ACB), базовый strip DataPath в ChildItems.
     */
    @Test
    void w05_documentForm_stripsDataPathEverywhere_keepsAcbButtons() throws Exception {
        String result = borrowAndRead("Documents", "Document", "Док1",
                "ФормаДокумента", "borrow-src-документ.xml");

        // strip: DataPath не должен остаться ни в ACB, ни в ChildItems (включая BaseForm-копию)
        assertThat(result)
                .as("DataPath должен быть вырезан из ACB (de7e943d) и ChildItems (безусловная ветка cfe-borrow.py)")
                .doesNotContain("<DataPath>");
        assertThat(result).doesNotContain("<TitleDataPath>");

        // keep: ChildItems ФОРМЫ копируются целиком (upstream: «ChildItems: copy full tree»);
        // защита от ложного Green «strip через потерю всего дерева» — элемент формы должен остаться
        assertThat(result)
                .as("дерево ChildItems формы должно быть скопировано (элемент ГруппаНомерДата из Designer-выгрузки)")
                .contains("name=\"ГруппаНомерДата\"");

        // keep: кнопки ACB сохраняются с CommandName=0 (84d078bd)
        String acb = topAutoCommandBar(result);
        assertThat(acb)
                .as("ChildItems AutoCommandBar с кнопками должны быть СОХРАНЕНЫ (84d078bd)")
                .contains("<ChildItems>")
                .contains("<Button");
        assertThat(acb).contains("<CommandName>0</CommandName>");
        // все CommandName заменены на 0
        Matcher cn = Pattern.compile("<CommandName>([^<]*)</CommandName>").matcher(result);
        while (cn.find()) {
            assertThat(cn.group(1)).as("каждый CommandName должен быть заменён на 0").isEqualTo("0");
        }
    }

    /**
     * Фикстура: InformationRegisters/ИспользуемыеВидыДоступаПоТаблицам/Forms/ФормаСписка.
     * Top-level CommandSet/ExcludedCommand, вложенный CommandSet/ExcludedCommand в ChildItems,
     * RowPictureDataPath, DataPath в ChildItems.
     * Upstream: 7abe26af (strip CommandSet/ExcludedCommand/RowPictureDataPath).
     */
    @Test
    void w05_listForm_stripsCommandSetExcludedCommandRowPicture() throws Exception {
        String result = borrowAndRead("InformationRegisters", "InformationRegister", "Регистр1",
                "ФормаСписка", "borrow-src-список.xml");

        assertThat(result)
                .as("ExcludedCommand (вкл. вложенные CommandSet) — «Неверное имя команды элемента формы» (7abe26af)")
                .doesNotContain("<ExcludedCommand>");
        assertThat(result)
                .as("RowPictureDataPath невалиден в расширении (7abe26af)")
                .doesNotContain("<RowPictureDataPath>");
        assertThat(result)
                .as("top-level CommandSet не должен копироваться в заимствованную форму (7abe26af)")
                .doesNotContain("<CommandSet>");
        assertThat(result).doesNotContain("<DataPath>");

        // keep: дерево ChildItems формы скопировано (динамический список остаётся)
        assertThat(result)
                .as("дерево ChildItems формы должно быть скопировано (элемент Список из Designer-выгрузки)")
                .contains("name=\"Список\"");
    }

    /**
     * Фикстура: InformationRegisters/ОбновлениеКлючейДоступаКДанным/Forms/ОбновлениеДоступаРучноеУправление.
     * TypeLink с xr:DataPath Items.*, element-level Events в ChildItems, RowPictureDataPath.
     * Upstream-состояние HEAD (strip TypeLink Items.* DOTALL + element-level Events).
     */
    @Test
    void w05_typeLinkForm_stripsTypeLinkItemsAndElementEvents() throws Exception {
        String result = borrowAndRead("InformationRegisters", "InformationRegister", "Регистр2",
                "ОбновлениеДоступа", "borrow-src-typelink.xml");

        assertThat(result)
                .as("блоки TypeLink с xr:DataPath Items.* должны быть вырезаны (cfe-borrow.py @ HEAD)")
                .doesNotContain("<TypeLink>");
        assertThat(result)
                .as("element-level Events в ChildItems должны быть вырезаны (cfe-borrow.py @ HEAD)")
                .doesNotContain("<Events>");
        assertThat(result).doesNotContain("<RowPictureDataPath>");
        assertThat(result).doesNotContain("<DataPath>");

        // keep: дерево ChildItems формы скопировано
        assertThat(result)
                .as("дерево ChildItems формы должно быть скопировано (ГруппаОбновлениеДоступаКОбъекту)")
                .contains("name=\"ГруппаОбновлениеДоступаКОбъекту\"");
    }

    /**
     * Фикстура: Documents/СообщениеSMS/Forms/ФормаДокумента.
     * TitleDataPath в ChildItems, множественные element-level Events, ExcludedCommand.
     */
    @Test
    void w05_smsForm_stripsTitleDataPathAndEvents() throws Exception {
        String result = borrowAndRead("Documents", "Document", "Док2",
                "ФормаSMS", "borrow-src-titledatapath.xml");

        assertThat(result)
                .as("TitleDataPath должен быть вырезан из ChildItems (безусловная ветка cfe-borrow.py)")
                .doesNotContain("<TitleDataPath>");
        assertThat(result)
                .as("element-level Events в ChildItems должны быть вырезаны")
                .doesNotContain("<Events>");
        assertThat(result).doesNotContain("<ExcludedCommand>");
        assertThat(result).doesNotContain("<DataPath>");
        assertThat(result).doesNotContain("<CommandSet>");

        // keep: дерево ChildItems ФОРМЫ скопировано — у СообщениеSMS первый <ChildItems>
        // в документе принадлежит ACB; защита от извлечения не того дерева
        assertThat(result)
                .as("дерево ChildItems формы должно быть скопировано (СтраницаСообщение из Designer-выгрузки)")
                .contains("name=\"СтраницаСообщение\"");
    }
}

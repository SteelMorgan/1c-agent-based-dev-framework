package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 W-05 (XG-35): правило Check 10 (EXT-10) ExtensionValidator — паттерн XG-04.
 *
 * <p>Заимствованная форма расширения (Ext/Form.xml с {@code <BaseForm>}) не должна содержать
 * конструкций ПОЛНОГО strip-набора безусловной ветки {@code cfe-borrow.py} @ HEAD
 * (technical-design §3.3 W-05): ExcludedCommand (в ACB и вложенных CommandSet),
 * DataPath в AutoCommandBar, RowPictureDataPath, top-level CommandSet, TitleDataPath,
 * TypeLink с Items.*, element-level Events в ChildItems.</p>
 *
 * <p>ChildItems AutoCommandBar с кнопками и CommandName=0 — валидны, не флажатся.
 * Формы БЕЗ BaseForm (собственные формы расширения) правилом не затрагиваются.</p>
 */
class ExtensionValidatorTask175Test {

    @TempDir
    Path tempDir;

    private final XmlStructureReader reader = new XmlStructureReader();

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    // ─── Scaffold ──────────────────────────────────────────────────────

    private void writeXml(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, all, 0, BOM.length);
        System.arraycopy(body, 0, all, BOM.length, body.length);
        Files.write(file, all);
    }

    /** Минимальное расширение с заимствованным Document.Док1 и формой ФормаДокумента. */
    private Path makeExtensionWithBorrowedForm(String extFormXml) throws Exception {
        Path ext = tempDir.resolve("ext");
        writeXml(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000099\">\n"
                        + "\t\t<InternalInfo/>\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>Ext1</Name>\n"
                        + "\t\t\t<ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose>\n"
                        + "\t\t\t<KeepMappingToExtendedConfigurationObjectsByIDs>true</KeepMappingToExtendedConfigurationObjectsByIDs>\n"
                        + "\t\t\t<NamePrefix>Расш1_</NamePrefix>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Document>Док1</Document>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        writeXml(ext.resolve("Documents/Док1.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Document uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>Док1</Name>\n"
                        + "\t\t\t<ExtendedConfigurationObject>22222222-2222-2222-2222-222222222222</ExtendedConfigurationObject>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Form>ФормаДокумента</Form>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Document>\n"
                        + "</MetaDataObject>\n");
        writeXml(ext.resolve("Documents/Док1/Forms/ФормаДокумента.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Form uuid=\"33333333-3333-3333-3333-333333333333\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>ФормаДокумента</Name>\n"
                        + "\t\t\t<ExtendedConfigurationObject>44444444-4444-4444-4444-444444444444</ExtendedConfigurationObject>\n"
                        + "\t\t\t<FormType>Managed</FormType>\n"
                        + "\t\t</Properties>\n"
                        + "\t</Form>\n"
                        + "</MetaDataObject>\n");
        writeXml(ext.resolve("Documents/Док1/Forms/ФормаДокумента/Ext/Form.xml"), extFormXml);
        return ext;
    }

    private List<ExtensionValidator.ValidationMessage> validate(Path ext) throws Exception {
        XmlDocument doc = reader.parse(ext.resolve("Configuration.xml"));
        return new ExtensionValidator().validate(doc, ext);
    }

    private static String dirtyBorrowedForm() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\""
                + " xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" version=\"2.20\">\n"
                + "\t<CommandSet>\n"
                + "\t\t<ExcludedCommand>Copy</ExcludedCommand>\n"
                + "\t</CommandSet>\n"
                + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\">\n"
                + "\t\t<ChildItems>\n"
                + "\t\t\t<Button name=\"Кнопка1\" id=\"100\">\n"
                + "\t\t\t\t<DataPath>Объект.Поле</DataPath>\n"
                + "\t\t\t\t<CommandName>0</CommandName>\n"
                + "\t\t\t</Button>\n"
                + "\t\t</ChildItems>\n"
                + "\t</AutoCommandBar>\n"
                + "\t<ChildItems>\n"
                + "\t\t<Table name=\"Список\" id=\"1\">\n"
                + "\t\t\t<RowPictureDataPath>Список.Картинка</RowPictureDataPath>\n"
                + "\t\t\t<TitleDataPath>Объект.Заголовок</TitleDataPath>\n"
                + "\t\t\t<Events>\n"
                + "\t\t\t\t<Event name=\"Selection\">СписокВыбор</Event>\n"
                + "\t\t\t</Events>\n"
                + "\t\t\t<TypeLink>\n"
                + "\t\t\t\t<xr:DataPath>Items.Список</xr:DataPath>\n"
                + "\t\t\t\t<xr:LinkItem>1</xr:LinkItem>\n"
                + "\t\t\t</TypeLink>\n"
                + "\t\t</Table>\n"
                + "\t</ChildItems>\n"
                + "\t<Attributes/>\n"
                + "\t<BaseForm version=\"2.20\">\n"
                + "\t\t<ChildItems/>\n"
                + "\t\t<Attributes/>\n"
                + "\t</BaseForm>\n"
                + "</Form>\n";
    }

    private static String cleanBorrowedForm() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form version=\"2.20\">\n"
                + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\">\n"
                + "\t\t<Autofill>false</Autofill>\n"
                + "\t\t<ChildItems>\n"
                + "\t\t\t<Button name=\"Кнопка1\" id=\"100\">\n"
                + "\t\t\t\t<CommandName>0</CommandName>\n"
                + "\t\t\t</Button>\n"
                + "\t\t</ChildItems>\n"
                + "\t</AutoCommandBar>\n"
                + "\t<ChildItems>\n"
                + "\t\t<UsualGroup name=\"ГруппаШапка\" id=\"2\"/>\n"
                + "\t</ChildItems>\n"
                + "\t<Attributes/>\n"
                + "\t<BaseForm version=\"2.20\">\n"
                + "\t\t<ChildItems/>\n"
                + "\t\t<Attributes/>\n"
                + "\t</BaseForm>\n"
                + "</Form>\n";
    }

    // ═══════════════════════════════════════════════════════════════════

    @Test
    void ext10_dirtyBorrowedForm_allSevenConstructsFlagged() throws Exception {
        Path ext = makeExtensionWithBorrowedForm(dirtyBorrowedForm());

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);
        List<String> ext10 = messages.stream()
                .filter(m -> "ERROR".equals(m.level) && m.message.startsWith("10. EXT-10"))
                .map(m -> m.message)
                .toList();

        assertThat(ext10)
                .as("каждая конструкция полного strip-набора должна дать ERROR EXT-10")
                .anySatisfy(m -> assertThat(m).contains("top-level <CommandSet>"))
                .anySatisfy(m -> assertThat(m).contains("<ExcludedCommand>"))
                .anySatisfy(m -> assertThat(m).contains("<DataPath> in AutoCommandBar"))
                .anySatisfy(m -> assertThat(m).contains("<RowPictureDataPath>"))
                .anySatisfy(m -> assertThat(m).contains("<TitleDataPath>"))
                .anySatisfy(m -> assertThat(m).contains("<TypeLink> with Items.*"))
                .anySatisfy(m -> assertThat(m).contains("element-level <Events> in ChildItems"));
    }

    @Test
    void ext10_cleanBorrowedForm_notFlagged() throws Exception {
        Path ext = makeExtensionWithBorrowedForm(cleanBorrowedForm());

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);

        assertThat(messages)
                .as("чистая заимствованная форма (вывод borrow после фикса XG-35) не должна давать EXT-10;"
                        + " ChildItems ACB с кнопками и CommandName=0 валидны")
                .noneMatch(m -> m.message.startsWith("10. EXT-10"));
    }

    @Test
    void ext10_ownFormWithoutBaseForm_notFlagged() throws Exception {
        // та же «грязная» форма, но БЕЗ BaseForm — собственная форма расширения,
        // конструкции в ней легитимны, правило не применяется
        String ownForm = dirtyBorrowedForm()
                .replace("\t<BaseForm version=\"2.20\">\n"
                        + "\t\t<ChildItems/>\n"
                        + "\t\t<Attributes/>\n"
                        + "\t</BaseForm>\n", "");
        Path ext = makeExtensionWithBorrowedForm(ownForm);

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);

        assertThat(messages)
                .as("формы без BaseForm правилом EXT-10 не затрагиваются")
                .noneMatch(m -> m.message.startsWith("10. EXT-10"));
    }
}

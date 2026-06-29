package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты CFE-расширений: --borrow-main-attribute и extension patch-method.
 *
 * <p>Фикстуры строятся программно: минимальная пара (configDir, extDir),
 * куда мы кладём XML без вложенных метаданных EDT/Designer, но достаточные
 * по структуре для тестируемых операций.</p>
 */
class ExtensionEditorTest {

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

    /**
     * Создать минимальную базовую конфигурацию со справочником X, который имеет
     * один реквизит (Артикул), одну табчасть (Реквизиты) и одну форму (ФормаЭлемента),
     * у формы — DataPath="Объект.Артикул".
     */
    private Path makeBaseConfig() throws Exception {
        Path cfg = tempDir.resolve("cfg");
        // Configuration.xml (минимум, чтобы borrow прошёл path-check)
        writeBom(cfg.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Base</Name></Properties>\n"
                        + "\t\t<ChildObjects><Catalog>X</Catalog></ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        // Catalogs/X.xml — объект со своими реквизитами и табчастью
        writeBom(cfg.resolve("Catalogs/X.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<Catalog uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "\t\t<Properties><Name>X</Name></Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Attribute uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\">\n"
                        + "\t\t\t\t<Properties><Name>Артикул</Name></Properties>\n"
                        + "\t\t\t</Attribute>\n"
                        + "\t\t\t<Attribute uuid=\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\">\n"
                        + "\t\t\t\t<Properties><Name>Цена</Name></Properties>\n"
                        + "\t\t\t</Attribute>\n"
                        + "\t\t\t<TabularSection uuid=\"cccccccc-cccc-cccc-cccc-cccccccccccc\">\n"
                        + "\t\t\t\t<Properties><Name>Строки</Name></Properties>\n"
                        + "\t\t\t</TabularSection>\n"
                        + "\t\t\t<Form>ФормаЭлемента</Form>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Catalog>\n"
                        + "</MetaDataObject>");
        // Catalogs/X/Forms/ФормаЭлемента.xml — Form meta
        writeBom(cfg.resolve("Catalogs/X/Forms/ФормаЭлемента.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject>\n"
                        + "\t<Form uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "\t\t<Properties><Name>ФормаЭлемента</Name></Properties>\n"
                        + "\t</Form>\n"
                        + "</MetaDataObject>");
        // Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml — ссылка на Объект.Артикул через DataPath
        writeBom(cfg.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form version=\"2.17\" xmlns=\"http://v8.1c.ru/8.2/managed-application/logform\">\n"
                        + "\t<ChildItems>\n"
                        + "\t\t<InputField>\n"
                        + "\t\t\t<DataPath>Объект.Артикул</DataPath>\n"
                        + "\t\t</InputField>\n"
                        + "\t</ChildItems>\n"
                        + "\t<Attributes/>\n"
                        + "</Form>");
        return cfg;
    }

    /** Создать минимальное расширение с NamePrefix. */
    private Path makeExtension(String namePrefix) throws Exception {
        return makeExtension(namePrefix, null);
    }

    /** Создать минимальное расширение с NamePrefix и опциональной версией формата. */
    private Path makeExtension(String namePrefix, String formatVersion) throws Exception {
        Path ext = tempDir.resolve("ext");
        writeBom(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\""
                        + (formatVersion != null ? " version=\"" + formatVersion + "\"" : "")
                        + ">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000099\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>Ext1</Name>\n"
                        + "\t\t\t<ObjectBelonging>Own</ObjectBelonging>\n"
                        + (namePrefix != null
                                ? "\t\t\t<NamePrefix>" + namePrefix + "</NamePrefix>\n"
                                : "")
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        return ext;
    }

    /** Editor с silenced stdout — тесты не должны засорять консоль. */
    private ExtensionEditor silent() {
        return new ExtensionEditor(new PrintStream(new ByteArrayOutputStream()));
    }

    // ═══════════════════════════════════════════════════════════════════
    // --borrow-main-attribute
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testBorrowMainAttribute_FormMode_AddsReferencedAttributes() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.FORM);

        // Проверка: в Catalogs/X.xml расширения появился Attribute с Name=Артикул
        Path extObj = ext.resolve("Catalogs/X.xml");
        assertThat(extObj).exists();
        String content = readNoBom(extObj);
        assertThat(content).contains("<Name>Артикул</Name>");
        assertThat(content).contains("<ObjectBelonging>Adopted</ObjectBelonging>");
        assertThat(content).contains(
                "<ExtendedConfigurationObject>aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa</ExtendedConfigurationObject>");
        assertThat(content).doesNotContain("<Attribute uuid=\"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa\"");
        // Цена НЕ должна быть скопирована — на форме нет DataPath на неё
        assertThat(content).doesNotContain("<Name>Цена</Name>");
        // Табчасть не копируется в form-режиме
        assertThat(content).doesNotContain("<Name>Строки</Name>");
    }

    @Test
    void testBorrowMainAttribute_AllMode_AddsAllAttributesAndTabularSections() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.ALL);

        Path extObj = ext.resolve("Catalogs/X.xml");
        String content = readNoBom(extObj);
        assertThat(content).contains("<Name>Артикул</Name>");
        assertThat(content).contains("<Name>Цена</Name>");
        assertThat(content).contains("<Name>Строки</Name>");
        assertThat(content).contains(
                "<ExtendedConfigurationObject>cccccccc-cccc-cccc-cccc-cccccccccccc</ExtendedConfigurationObject>");
        assertThat(content).doesNotContain("<TabularSection uuid=\"cccccccc-cccc-cccc-cccc-cccccccccccc\"");
    }

    @Test
    void testBorrowMainAttribute_ObjectAlreadyBorrowed_NoOverwrite() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1");

        // первый прогон: добавит Артикул
        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.FORM);
        Path extObj = ext.resolve("Catalogs/X.xml");
        String first = readNoBom(extObj);

        // второй прогон — не должен ничего добавить, файл идентичен
        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.FORM);
        String second = readNoBom(extObj);
        assertThat(second).isEqualTo(first);
        // Артикул всё ещё один раз
        int count = countOccurrences(second, "<Name>Артикул</Name>");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testBorrowMainAttribute_FormAlreadyBorrowed_PreservesFormFiles() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента", null);

        Path formXml = ext.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml");
        Path module = ext.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form/Module.bsl");
        String customForm = readNoBom(formXml) + "\n<!-- custom extension change -->";
        String customModule = "Процедура Расш1_СвояКоманда()\nКонецПроцедуры\n";
        writeBom(formXml, customForm);
        writeBom(module, customModule);

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.FORM);

        assertThat(readNoBom(formXml)).contains("custom extension change");
        assertThat(readNoBom(module)).isEqualTo(customModule);
        assertThat(readNoBom(ext.resolve("Catalogs/X.xml"))).contains("<Name>Артикул</Name>");
    }

    @Test
    void testBorrowMainAttribute_NoFormSpec_Errors() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1");

        assertThatThrownBy(() -> silent().borrow(ext, cfg, "Catalog.X",
                ExtensionEditor.MainAttributeMode.FORM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--borrow-main-attribute");
    }

    @Test
    void testBorrow_UsesExtensionFormatVersionForBorrowedMetadata() throws Exception {
        Path cfg = makeBaseConfig();
        Path ext = makeExtension("Расш1", "2.20");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента", null);

        String objectXml = readNoBom(ext.resolve("Catalogs/X.xml"));
        String formMetaXml = readNoBom(ext.resolve("Catalogs/X/Forms/ФормаЭлемента.xml"));

        assertThat(objectXml).contains("version=\"2.20\"");
        assertThat(objectXml).doesNotContain("version=\"2.17\"");
        assertThat(formMetaXml).contains("version=\"2.20\"");
        assertThat(formMetaXml).doesNotContain("version=\"2.17\"");
    }

    @Test
    void testBorrowMainAttribute_FormMode_KeepsObjectBindingsAndCollectsBindingTags() throws Exception {
        Path cfg = makeBaseConfig();
        Path baseObj = cfg.resolve("Catalogs/X.xml");
        String objXml = readNoBom(baseObj)
                .replace("\t\t\t<Form>ФормаЭлемента</Form>",
                        "\t\t\t<Attribute uuid=\"dddddddd-dddd-dddd-dddd-dddddddddddd\">\n"
                                + "\t\t\t\t<Properties><Name>Статус</Name></Properties>\n"
                                + "\t\t\t</Attribute>\n"
                                + "\t\t\t<Attribute uuid=\"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee\">\n"
                                + "\t\t\t\t<Properties><Name>КоличествоАдресатов</Name></Properties>\n"
                                + "\t\t\t</Attribute>\n"
                                + "\t\t\t<Form>ФормаЭлемента</Form>");
        writeBom(baseObj, objXml);
        writeBom(cfg.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form version=\"2.17\" xmlns=\"http://v8.1c.ru/8.2/managed-application/logform\">\n"
                        + "\t<ChildItems>\n"
                        + "\t\t<InputField name=\"Артикул\" id=\"2\">\n"
                        + "\t\t\t<DataPath>Объект.Артикул</DataPath>\n"
                        + "\t\t\t<TitleDataPath>Объект.Статус</TitleDataPath>\n"
                        + "\t\t\t<RowPictureDataPath>Список.Picture</RowPictureDataPath>\n"
                        + "\t\t\t<MultipleValuePictureDataPath>Список.Picture</MultipleValuePictureDataPath>\n"
                        + "\t\t</InputField>\n"
                        + "\t\t<InputField name=\"Количество\" id=\"3\">\n"
                        + "\t\t\t<MultipleValueDataPath>Объект.КоличествоАдресатов</MultipleValueDataPath>\n"
                        + "\t\t</InputField>\n"
                        + "\t\t<LabelDecoration name=\"Фильтр\" id=\"4\">\n"
                        + "\t\t\t<Field>Объект.Статус</Field>\n"
                        + "\t\t</LabelDecoration>\n"
                        + "\t</ChildItems>\n"
                        + "\t<Attributes/>\n"
                        + "</Form>");
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента",
                ExtensionEditor.MainAttributeMode.FORM);

        String formXml = readNoBom(ext.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"));
        assertThat(formXml).contains("<DataPath>Объект.Артикул</DataPath>");
        assertThat(formXml).contains("<TitleDataPath>Объект.Статус</TitleDataPath>");
        assertThat(formXml).contains("<MultipleValueDataPath>Объект.КоличествоАдресатов</MultipleValueDataPath>");
        assertThat(formXml).doesNotContain("<RowPictureDataPath>");
        assertThat(formXml).doesNotContain("<MultipleValuePictureDataPath>");

        String extObj = readNoBom(ext.resolve("Catalogs/X.xml"));
        assertThat(extObj).contains("<Name>Артикул</Name>");
        assertThat(extObj).contains("<Name>Статус</Name>");
        assertThat(extObj).contains("<Name>КоличествоАдресатов</Name>");
        assertThat(extObj).doesNotContain("<Name>Цена</Name>");
    }

    @Test
    void testBorrowForm_WithoutMainAttribute_StripsAllBindingTags() throws Exception {
        Path cfg = makeBaseConfig();
        writeBom(cfg.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form version=\"2.17\" xmlns=\"http://v8.1c.ru/8.2/managed-application/logform\">\n"
                        + "\t<ChildItems>\n"
                        + "\t\t<InputField name=\"Поле\" id=\"2\">\n"
                        + "\t\t\t<DataPath>Объект.Артикул</DataPath>\n"
                        + "\t\t\t<TitleDataPath>Объект.Артикул</TitleDataPath>\n"
                        + "\t\t\t<FooterDataPath>Объект.Артикул</FooterDataPath>\n"
                        + "\t\t\t<HeaderDataPath>Объект.Артикул</HeaderDataPath>\n"
                        + "\t\t\t<MultipleValueDataPath>Объект.Артикул</MultipleValueDataPath>\n"
                        + "\t\t\t<MultipleValuePresentDataPath>Объект.Артикул</MultipleValuePresentDataPath>\n"
                        + "\t\t\t<RowPictureDataPath>Список.Picture</RowPictureDataPath>\n"
                        + "\t\t\t<MultipleValuePictureDataPath>Список.Picture</MultipleValuePictureDataPath>\n"
                        + "\t\t</InputField>\n"
                        + "\t</ChildItems>\n"
                        + "\t<Attributes/>\n"
                        + "</Form>");
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "Catalog.X.Form.ФормаЭлемента", null);

        String formXml = readNoBom(ext.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"));
        assertThat(formXml).doesNotContain("DataPath>");
        assertThat(formXml).doesNotContain("PictureDataPath>");
    }

    @Test
    void testBorrowDefinedType_CopiesTypeDefinition() throws Exception {
        Path cfg = tempDir.resolve("cfg-defined");
        writeBom(cfg.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Base</Name></Properties>\n"
                        + "\t\t<ChildObjects><DefinedType>ТипЦены</DefinedType></ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>");
        writeBom(cfg.resolve("DefinedTypes/ТипЦены.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\""
                        + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">\n"
                        + "\t<DefinedType uuid=\"99999999-9999-9999-9999-999999999999\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>ТипЦены</Name>\n"
                        + "\t\t\t<Type><v8:Type>xs:decimal</v8:Type></Type>\n"
                        + "\t\t</Properties>\n"
                        + "\t</DefinedType>\n"
                        + "</MetaDataObject>");
        Path ext = makeExtension("Расш1");

        silent().borrow(ext, cfg, "DefinedType.ТипЦены", null);

        String extXml = readNoBom(ext.resolve("DefinedTypes/ТипЦены.xml"));
        assertThat(extXml).contains("<Type><v8:Type>xs:decimal</v8:Type></Type>");
    }

    // ═══════════════════════════════════════════════════════════════════
    // extension patch-method
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void testPatchMethod_Before_GeneratesCorrectAnnotation() throws Exception {
        Path ext = makeExtension("Расш1");
        // Заимствуем объект (минимально), чтобы был файл объекта
        ext.resolve("Catalogs/X").toFile().mkdirs();
        writeBom(ext.resolve("Catalogs/X.xml"), "<MetaDataObject><Catalog/></MetaDataObject>");

        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false);

        assertThat(r.created).isTrue();
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("&НаСервере");
        assertThat(bsl).contains("&Перед(\"ПриЗаписи\")");
        assertThat(bsl).contains("Процедура Расш1_ПриЗаписи()");
        assertThat(bsl).contains("КонецПроцедуры");
        assertThat(r.bslFile.toString()).endsWith("Catalogs/X/Ext/ObjectModule.bsl");
    }

    @Test
    void testPatchMethod_PrefixWithUnderscore_NoDoubleUnderscore() throws Exception {
        // TASK-171 D-3: реальные NamePrefix оканчиваются на разделитель (mcp_, тк_, OPI_).
        // Раньше procName = prefix + "_" + method давало двойное подчёркивание (mcp__ПриЗаписи).
        // Конвенция 1С: <Префикс><ИмяМетода> → mcp_ПриЗаписи.
        Path ext = makeExtension("mcp_");
        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false);
        assertThat(r.procedureName).isEqualTo("mcp_ПриЗаписи");
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("Процедура mcp_ПриЗаписи()");
        assertThat(bsl).doesNotContain("mcp__ПриЗаписи");
    }

    @Test
    void testPatchMethod_After_GeneratesCorrectAnnotation() throws Exception {
        Path ext = makeExtension("Расш1");
        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.AFTER, null, null, false);
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("&После(\"ПриЗаписи\")");
        assertThat(bsl).contains("Процедура Расш1_ПриЗаписи()");
    }

    @Test
    void testPatchMethod_Instead_GeneratesCorrectAnnotation() throws Exception {
        Path ext = makeExtension("Расш1");
        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.INSTEAD, null, "НаКлиенте", false);
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("&НаКлиенте");
        assertThat(bsl).contains("&Вместо(\"ПриЗаписи\")");
    }

    @Test
    void testPatchMethod_ModificationAndControl_CopiesOriginalBody() throws Exception {
        Path cfg = tempDir.resolve("cfg");
        // Configuration.xml — минимум
        writeBom(cfg.resolve("Configuration.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject><Configuration uuid=\"x\"><Properties><Name>B</Name></Properties></Configuration></MetaDataObject>");
        // BSL-модуль с процедурой ПриЗаписи в Catalogs/X/Ext/ObjectModule.bsl
        Path baseBsl = cfg.resolve("Catalogs/X/Ext/ObjectModule.bsl");
        Files.createDirectories(baseBsl.getParent());
        Files.writeString(baseBsl,
                "Процедура ПриЗаписи(Отказ) Экспорт\n"
                        + "\tЕсли Отказ Тогда\n"
                        + "\t\tВозврат;\n"
                        + "\tКонецЕсли;\n"
                        + "\tСообщить(\"original\");\n"
                        + "КонецПроцедуры\n");

        Path ext = makeExtension("Расш1");

        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.MODIFICATION_AND_CONTROL, cfg, null, false);

        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("&ИзменениеИКонтроль(\"ПриЗаписи\")");
        assertThat(bsl).contains("Процедура Расш1_ПриЗаписи()");
        // Тело оригинала перенесено
        assertThat(bsl).contains("Сообщить(\"original\");");
        assertThat(bsl).contains("Если Отказ Тогда");
    }

    @Test
    void testPatchMethod_FunctionFlag_AddsReturnUndefined() throws Exception {
        Path ext = makeExtension("Расш1");
        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ВычислитьЦену",
                ExtensionEditor.InterceptorType.BEFORE, null, null, true);
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("Функция Расш1_ВычислитьЦену()");
        assertThat(bsl).contains("Возврат Неопределено;");
        assertThat(bsl).contains("КонецФункции");
    }

    @Test
    void testPatchMethod_ExistingProcedureName_WarningSkip() throws Exception {
        Path ext = makeExtension("Расш1");
        // первый прогон создаст процедуру
        ExtensionEditor.PatchMethodResult r1 = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false);
        assertThat(r1.skipped).isFalse();
        String first = readNoBom(r1.bslFile);

        // второй прогон — должен пропустить
        ExtensionEditor.PatchMethodResult r2 = silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false);
        assertThat(r2.skipped).isTrue();
        String second = readNoBom(r2.bslFile);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void testPatchMethod_NamePrefixMissing_Errors() throws Exception {
        Path ext = makeExtension(null); // no NamePrefix
        assertThatThrownBy(() -> silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("NamePrefix");
    }

    @Test
    void testPatchMethod_ModificationAndControl_WithoutConfig_Errors() throws Exception {
        Path ext = makeExtension("Расш1");
        assertThatThrownBy(() -> silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "ПриЗаписи",
                ExtensionEditor.InterceptorType.MODIFICATION_AND_CONTROL, null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--config");
    }

    @Test
    void testPatchMethod_MethodNotFoundInBase_Errors() throws Exception {
        Path cfg = tempDir.resolve("cfg");
        writeBom(cfg.resolve("Configuration.xml"),
                "<MetaDataObject><Configuration uuid=\"x\"><Properties><Name>B</Name></Properties></Configuration></MetaDataObject>");
        Path baseBsl = cfg.resolve("Catalogs/X/Ext/ObjectModule.bsl");
        Files.createDirectories(baseBsl.getParent());
        Files.writeString(baseBsl, "Процедура ДругойМетод()\nКонецПроцедуры\n");

        Path ext = makeExtension("Расш1");

        assertThatThrownBy(() -> silent().patchMethod(
                ext, "Catalog.X.ObjectModule", "НесуществующийМетод",
                ExtensionEditor.InterceptorType.MODIFICATION_AND_CONTROL, cfg, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("НесуществующийМетод");
    }

    @Test
    void testPatchMethod_UnknownModuleFormat_Errors() throws Exception {
        Path ext = makeExtension("Расш1");
        assertThatThrownBy(() -> silent().patchMethod(
                ext, "Garbage.Stuff", "X",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPatchMethod_FormModule_ResolvesCorrectPath() throws Exception {
        Path ext = makeExtension("Расш1");
        ExtensionEditor.PatchMethodResult r = silent().patchMethod(
                ext, "Catalog.X.Form.ФормаЭлемента", "ПриСозданииНаСервере",
                ExtensionEditor.InterceptorType.BEFORE, null, null, false);
        assertThat(r.bslFile.toString())
                .endsWith("Catalogs/X/Forms/ФормаЭлемента/Ext/Form/Module.bsl");
        String bsl = readNoBom(r.bslFile);
        assertThat(bsl).contains("Процедура Расш1_ПриСозданииНаСервере()");
    }

    private static int countOccurrences(String s, String sub) {
        int idx = 0, count = 0;
        while ((idx = s.indexOf(sub, idx)) >= 0) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

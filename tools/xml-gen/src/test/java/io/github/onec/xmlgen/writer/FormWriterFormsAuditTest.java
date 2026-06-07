package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174: аудит порта xml-gen, домен «формы».
 * Регрессионные тесты на расхождения порта со спекой Широкова
 * (1c-form-spec.md, form-dsl-spec.md), найденные при сверке 07.06.2026.
 */
class FormWriterFormsAuditTest {

    @TempDir
    Path tempDir;

    private String generate(FormDsl dsl) throws Exception {
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        return Files.readString(outputXml);
    }

    /** F1: Parameters (1c-form-spec.md §10) должны эмититься; Parameter — без id, с KeyParameter. */
    @Test
    void testParametersEmitted() throws Exception {
        List<FormDsl.Parameter> params = Arrays.asList(
                new FormDsl.Parameter("Ключ", null, "CatalogRef.Организации", true),
                new FormDsl.Parameter("Основание", null, "string", null)
        );
        FormDsl dsl = new FormDsl("Форма", null, null, null, null, null, params, null);
        String content = generate(dsl);

        assertThat(content).contains("<Parameters>");
        assertThat(content).contains("<Parameter name=\"Ключ\">");
        assertThat(content).contains("<KeyParameter>true</KeyParameter>");
        assertThat(content).contains("<Parameter name=\"Основание\">");
        // Параметры НЕ имеют атрибута id (спека §10)
        assertThat(content).doesNotContain("<Parameter name=\"Ключ\" id=");
        // Порядок секций по §2: Attributes → Parameters → Commands отражён в createDesigner;
        // здесь достаточно факта наличия секции
    }

    /** F2: excludedCommands → <CommandSet><ExcludedCommand> (1c-form-spec.md §4), до AutoCommandBar. */
    @Test
    void testExcludedCommandsEmitted() throws Exception {
        FormDsl dsl = new FormDsl("Форма", null, Arrays.asList("Copy", "Delete"),
                null, null, null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<CommandSet>");
        assertThat(content).contains("<ExcludedCommand>Copy</ExcludedCommand>");
        assertThat(content).contains("<ExcludedCommand>Delete</ExcludedCommand>");
        assertThat(content.indexOf("<CommandSet>"))
                .isLessThan(content.indexOf("<AutoCommandBar"));
    }

    /** XG-17: корневой Form.xml должен нести dcssch/lf namespaces как Designer-канон. */
    @Test
    void testRootNamespacesIncludeDcsschAndLf() throws Exception {
        FormDsl dsl = new FormDsl("Форма", null, null, null, null, null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("xmlns:dcssch=\"http://v8.1c.ru/8.1/data-composition-system/schema\"");
        assertThat(content).contains("xmlns:lf=\"http://v8.1c.ru/8.2/managed-application/logform\"");
    }

    /** F3+F11+F19: Table — три Addition-элемента, безусловный ChildItems, height→HeightInTableRows. */
    @Test
    void testTableAdditionsAndHeight() throws Exception {
        Map<String, Object> table = new LinkedHashMap<>();
        table.put("table", "Товары");
        table.put("path", "Объект.Товары");
        table.put("height", 5);
        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(table), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<SearchStringAddition name=\"ТоварыСтрокаПоиска\"");
        assertThat(content).contains("<ViewStatusAddition name=\"ТоварыСостояниеПросмотра\"");
        assertThat(content).contains("<SearchControlAddition name=\"ТоварыУправлениеПоиском\"");
        assertThat(content).contains("<Type>SearchStringRepresentation</Type>");
        assertThat(content).contains("<Type>ViewStatusRepresentation</Type>");
        assertThat(content).contains("<Type>SearchControl</Type>");
        assertThat(content).contains("<Item>Товары</Item>");
        // ChildItems безусловный (класс XG-15) — таблица без колонок всё равно несёт ChildItems
        assertThat(content).contains("<ChildItems>");
        // height таблицы — в строках таблицы (form-dsl-spec.md §4.3 table)
        assertThat(content).contains("<HeightInTableRows>5</HeightInTableRows>");
        assertThat(content).doesNotContain("<Height>5</Height>");
    }

    /** F4: события элементов on/handlers (form-dsl-spec.md §4.1-4.2) — Events с автоименованием. */
    @Test
    void testElementEventsOnAndHandlers() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("input", "Контрагент");
        input.put("path", "Объект.Контрагент");
        input.put("on", List.of("OnChange", "StartChoice"));

        Map<String, Object> button = new LinkedHashMap<>();
        button.put("button", "Загрузить");
        button.put("command", "Загрузить");
        button.put("handlers", Map.of("Click", "МойОбработчик"));

        FormDsl dsl = new FormDsl("Форма", null, null, null,
                List.of(input, button), null, null, null);
        String content = generate(dsl);

        // Автоименование: <ИмяЭлемента><РусскийСуффикс>
        assertThat(content).contains("<Event name=\"OnChange\">КонтрагентПриИзменении</Event>");
        assertThat(content).contains("<Event name=\"StartChoice\">КонтрагентНачалоВыбора</Event>");
        // Явный handler имеет приоритет
        assertThat(content).contains("<Event name=\"Click\">МойОбработчик</Event>");
        // Мусор generic-прохода исключён
        assertThat(content).doesNotContain("<On>");
        assertThat(content).doesNotContain("<Handlers>");
    }

    /** F5: Button type usual/hyperlink/commandBar → канонический enum, без дублей сырого Type. */
    @Test
    void testButtonTypeMapping() throws Exception {
        Map<String, Object> hyperlink = new LinkedHashMap<>();
        hyperlink.put("button", "Ссылка");
        hyperlink.put("command", "Открыть");
        hyperlink.put("type", "hyperlink");

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(hyperlink), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<Type>Hyperlink</Type>");
        // Сырое DSL-значение и дефолт не должны просочиться
        assertThat(content).doesNotContain("<Type>hyperlink</Type>");
        assertThat(content).doesNotContain("<Type>UsualButton</Type>");
    }

    /** F5: Button без type сохраняет дефолт XG-14 — UsualButton первым тегом. */
    @Test
    void testButtonDefaultTypeStillUsual() throws Exception {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("button", "Выполнить");
        button.put("command", "Выполнить");
        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(button), null, null, null);
        String content = generate(dsl);
        assertThat(content).contains("<Type>UsualButton</Type>");
    }

    /** F6: picture у Button и src у PictureDecoration — структурный <Picture><xr:Ref>. */
    @Test
    void testStructuredPictureRefs() throws Exception {
        Map<String, Object> button = new LinkedHashMap<>();
        button.put("button", "Обновить");
        button.put("command", "Обновить");
        button.put("picture", "StdPicture.Refresh");

        Map<String, Object> decoration = new LinkedHashMap<>();
        decoration.put("picture", "Логотип");
        decoration.put("src", "CommonPicture.Логотип");

        FormDsl dsl = new FormDsl("Форма", null, null, null,
                List.of(button, decoration), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<xr:Ref>StdPicture.Refresh</xr:Ref>");
        assertThat(content).contains("<xr:Ref>CommonPicture.Логотип</xr:Ref>");
        assertThat(content).contains("<xr:LoadTransparent>true</xr:LoadTransparent>");
        // Плоских <Picture>текст</Picture> быть не должно
        assertThat(content).doesNotContain("<Picture>StdPicture.Refresh</Picture>");
        assertThat(content).doesNotContain("<Picture>CommonPicture.Логотип</Picture>");
    }

    /** F7: inputHint — multilang v8:item (1c-form-spec.md §8.2), не плоский текст. */
    @Test
    void testInputHintMultilang() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("input", "Поиск");
        input.put("inputHint", "Введите название");
        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(input), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<InputHint>");
        assertThat(content).contains("<v8:content>Введите название</v8:content>");
        assertThat(content).doesNotContain("<InputHint>Введите название</InputHint>");
    }

    /** F12: hidden/disabled are DSL aliases; generated XML must use Visible/Enabled. */
    @Test
    void testHiddenDisabledAliasesDoNotLeakAsXmlTags() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("input", "Поле");
        input.put("hidden", true);
        input.put("disabled", true);
        input.put("readOnly", true);

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("group", "vertical");
        group.put("name", "Группа");
        group.put("hidden", true);
        group.put("disabled", true);

        FormDsl dsl = new FormDsl("Форма", null, null, null,
                List.of(input, group), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<Visible>false</Visible>");
        assertThat(content).contains("<Enabled>false</Enabled>");
        assertThat(content).contains("<ReadOnly>true</ReadOnly>");
        assertThat(content).doesNotContain("<Hidden>");
        assertThat(content).doesNotContain("<Disabled>");
    }

    /** F8: group:"collapsible" → Group=Vertical + Behavior=Collapsible (1c-form-spec.md §8.1). */
    @Test
    void testCollapsibleGroup() throws Exception {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("group", "collapsible");
        group.put("name", "ГруппаДоп");
        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(group), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<Group>Vertical</Group>");
        assertThat(content).contains("<Behavior>Collapsible</Behavior>");
        assertThat(content).doesNotContain("<Group>Collapsible</Group>");
    }

    /** F9: Attribute.fillChecking → <FillChecking> (1c-form-spec.md §9). */
    @Test
    void testAttributeFillChecking() throws Exception {
        FormDsl.Attribute attr = new FormDsl.Attribute(
                "Организация", null, "CatalogRef.Организации", null,
                null, null, null, null, "Show");
        FormDsl dsl = new FormDsl("Форма", null, null, null, null, List.of(attr), null, null);
        String content = generate(dsl);
        assertThat(content).contains("<FillChecking>Show</FillChecking>");
    }

    /** F10: Command shortcut/picture/representation (form-dsl-spec.md §7) эмитятся. */
    @Test
    void testCommandShortcutPictureRepresentation() throws Exception {
        FormDsl.Command cmd = new FormDsl.Command(
                "Печать", "Печать", "ПечатьОбработка", null,
                "Ctrl+P", "StdPicture.Print", "PictureAndText");
        FormDsl dsl = new FormDsl("Форма", null, null, null, null, null, null, List.of(cmd));
        String content = generate(dsl);

        assertThat(content).contains("<Shortcut>Ctrl+P</Shortcut>");
        assertThat(content).contains("<xr:Ref>StdPicture.Print</xr:Ref>");
        assertThat(content).contains("<Representation>PictureAndText</Representation>");
    }

    /** F4: позиция Events у контейнера — ДО ChildItems (канон Pages/Table в конфигурации). */
    @Test
    void testContainerEventsBeforeChildItems() throws Exception {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("page", "Основное");
        Map<String, Object> pages = new LinkedHashMap<>();
        pages.put("pages", "Страницы");
        pages.put("on", List.of("OnCurrentPageChange"));
        pages.put("children", List.of(page));

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(pages), null, null, null);
        String content = generate(dsl);

        int eventsIdx = content.indexOf("<Event name=\"OnCurrentPageChange\">СтраницыПриСменеСтраницы</Event>");
        int pageIdx = content.indexOf("<Page name=\"Основное\"");
        assertThat(eventsIdx).isGreaterThan(0);
        assertThat(pageIdx).isGreaterThan(0);
        assertThat(eventsIdx).isLessThan(pageIdx);
    }

    /** XG-19: Pages содержит Page только через ChildItems, как в живых Form.xml. */
    @Test
    void testPagesWrapPagesInChildItems() throws Exception {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("page", "Основное");

        Map<String, Object> pages = new LinkedHashMap<>();
        pages.put("pages", "Страницы");
        pages.put("children", List.of(page));

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(pages), null, null, null);
        String content = generate(dsl);

        int pagesIdx = content.indexOf("<Pages name=\"Страницы\"");
        int childItemsIdx = content.indexOf("<ChildItems>", pagesIdx);
        int pageIdx = content.indexOf("<Page name=\"Основное\"", pagesIdx);
        assertThat(pagesIdx).isGreaterThan(0);
        assertThat(childItemsIdx).isGreaterThan(pagesIdx);
        assertThat(pageIdx).isGreaterThan(childItemsIdx);
    }

    /** XG-31 O1: RadioButtonField поддержан compile-путём на базовой структуре живых форм. */
    @Test
    void testRadioButtonField() throws Exception {
        Map<String, Object> radio = new LinkedHashMap<>();
        radio.put("type", "radio");
        radio.put("name", "ПереключательРежима");
        radio.put("path", "Режим");
        radio.put("titleLocation", "none");
        radio.put("radioButtonType", "RadioButtons");
        radio.put("choices", List.of(
                Map.of("title", "Первый", "value", 0),
                Map.of("title", "Второй", "value", 1, "checkState", 0)
        ));
        radio.put("on", List.of("OnChange"));

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(radio), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<RadioButtonField name=\"ПереключательРежима\"");
        assertThat(content).contains("<DataPath>Режим</DataPath>");
        assertThat(content).contains("<TitleLocation>None</TitleLocation>");
        assertThat(content).contains("<RadioButtonType>RadioButtons</RadioButtonType>");
        assertThat(content).contains("<ChoiceList>");
        assertThat(content).contains("<v8:content>Первый</v8:content>");
        assertThat(content).contains("<Value xsi:type=\"xs:decimal\">0</Value>");
        assertThat(content).contains("<ContextMenu name=\"ПереключательРежимаКонтекстноеМеню\"");
        assertThat(content).contains("<ExtendedTooltip name=\"ПереключательРежимаРасширеннаяПодсказка\"");
        assertThat(content).contains("<Event name=\"OnChange\">ПереключательРежимаПриИзменении</Event>");
    }

    /** XG-31 O3: новые bare FORM-типы из свежей спеки эмитятся без обращения к общему TypeResolver. */
    @Test
    void testNewFormTypes() throws Exception {
        List<FormDsl.Attribute> attributes = List.of(
                new FormDsl.Attribute("Период", null, "StandardPeriod", false, null),
                new FormDsl.Attribute("Константы", null, "ConstantsSet", false, null),
                new FormDsl.Attribute("Менеджер", null, "InformationRegisterRecordManager.Настройки", false, null)
        );
        FormDsl dsl = new FormDsl("Форма", null, null, null, null, attributes, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<v8:Type>v8:StandardPeriod</v8:Type>");
        assertThat(content).contains("<v8:Type>cfg:ConstantsSet</v8:Type>");
        assertThat(content).contains("<v8:Type>cfg:InformationRegisterRecordManager.Настройки</v8:Type>");
    }

    /** F13: dataPath alias is emitted in canonical DataPath position, not by generic property dump. */
    @Test
    void testDataPathAliasEmittedBeforeInputProperties() throws Exception {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("type", "input");
        input.put("name", "Поле");
        input.put("dataPath", "Объект.Поле");
        input.put("titleLocation", "top");

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(input), null, null, null);
        String content = generate(dsl);

        int inputIdx = content.indexOf("<InputField name=\"Поле\"");
        int dataPathIdx = content.indexOf("<DataPath>Объект.Поле</DataPath>", inputIdx);
        int titleLocationIdx = content.indexOf("<TitleLocation>Top</TitleLocation>", inputIdx);
        assertThat(dataPathIdx).isGreaterThan(inputIdx);
        assertThat(titleLocationIdx).isGreaterThan(dataPathIdx);
        assertThat(content.indexOf("<DataPath>Объект.Поле</DataPath>", dataPathIdx + 1))
                .as("dataPath alias must not be emitted a second time by the generic property pass")
                .isEqualTo(-1);
    }

    @Test
    void testDataPathAliasWorksForTableAndColumns() throws Exception {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("type", "check");
        column.put("name", "ТоварыАктивен");
        column.put("dataPath", "Товары.Активен");

        Map<String, Object> table = new LinkedHashMap<>();
        table.put("type", "table");
        table.put("name", "Товары");
        table.put("dataPath", "Товары");
        table.put("columns", List.of(column));

        FormDsl dsl = new FormDsl("Форма", null, null, null, List.of(table), null, null, null);
        String content = generate(dsl);

        assertThat(content).contains("<Table name=\"Товары\"");
        assertThat(content).contains("<DataPath>Товары</DataPath>");
        assertThat(content).contains("<CheckBoxField name=\"ТоварыАктивен\"");
        assertThat(content).contains("<DataPath>Товары.Активен</DataPath>");
    }
}

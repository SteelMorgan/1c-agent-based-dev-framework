package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174 раунд 2: XG-10 (составные типы — отдельные v8:Type),
 * XG-11 (корневой Title всегда), XG-12 (нет Type-дискриминатора в UsualGroup).
 */
class FormWriterTask174Round2Test {

    @TempDir
    Path tempDir;

    private static FormDsl dsl(String title, List<Map<String, Object>> elements,
                               List<FormDsl.Attribute> attributes) {
        return new FormDsl(title, null, null, null, elements, attributes, null, null);
    }

    // ==================== XG-11: корневой Title ====================

    @Test
    void xg11_noTitleInDsl_emitsRootTitleFromFormDirName() throws Exception {
        Path out = tempDir.resolve("Forms/МояФорма/Ext/Form.xml");
        Files.createDirectories(out.getParent());

        new FormWriter(OutputFormat.DESIGNER).create(dsl(null, null, null), out);

        String content = Files.readString(out);
        // Title присутствует, мультиязычный, текст = имя формы из пути
        assertThat(content).contains("<Title>");
        assertThat(content).contains("<v8:item>");
        assertThat(content).contains("<v8:content>МояФорма</v8:content>");
        // Title идёт ДО AutoCommandBar
        assertThat(content.indexOf("<Title>")).isLessThan(content.indexOf("<AutoCommandBar"));
    }

    @Test
    void xg11_noTitleAndFlatPath_fallsBackToDefaultName() throws Exception {
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl(null, null, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<v8:content>Форма</v8:content>");
    }

    @Test
    void xg11_explicitTitle_keepsDslTitle() throws Exception {
        Path out = tempDir.resolve("Forms/Форма/Ext/Form.xml");
        Files.createDirectories(out.getParent());

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Уборщик тестовых данных", null, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<v8:content>Уборщик тестовых данных</v8:content>");
    }

    // ==================== XG-10: составные типы ====================

    @Test
    void xg10_compositeRefType_emitsSeparateV8Types() throws Exception {
        List<FormDsl.Attribute> attrs = List.of(
                new FormDsl.Attribute("Ссылка", null,
                        "CatalogRef.Договоры | CatalogRef.Контрагенты", false, null));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", null, attrs), out);

        String content = Files.readString(out);
        assertThat(content).contains("<v8:Type>cfg:CatalogRef.Договоры</v8:Type>");
        assertThat(content).contains("<v8:Type>cfg:CatalogRef.Контрагенты</v8:Type>");
        assertNoPipeInsideV8Type(content);
    }

    @Test
    void xg10_compositeWithQualifiers_typesFirstThenQualifiers() throws Exception {
        List<FormDsl.Attribute> attrs = List.of(
                new FormDsl.Attribute("Поле", null,
                        "string(50) | CatalogRef.Контрагенты", false, null));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", null, attrs), out);

        String content = Files.readString(out);
        // Канон: все v8:Type подряд, квалификаторы после (НастройкиВерсионированияОбъектов)
        int t1 = content.indexOf("<v8:Type>xs:string</v8:Type>");
        int t2 = content.indexOf("<v8:Type>cfg:CatalogRef.Контрагенты</v8:Type>");
        int q = content.indexOf("<v8:StringQualifiers>");
        assertThat(t1).isPositive();
        assertThat(t2).isGreaterThan(t1);
        assertThat(q).isGreaterThan(t2);
        assertNoPipeInsideV8Type(content);
    }

    @Test
    void xg10_singleNonnegNumber_notBrokenBySplit() throws Exception {
        List<FormDsl.Attribute> attrs = List.of(
                new FormDsl.Attribute("Сумма", null, "number+(15,2)", false, null));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", null, attrs), out);

        String content = Files.readString(out);
        assertThat(content).contains("<v8:Type>xs:decimal</v8:Type>");
        assertThat(content).contains("<v8:AllowedSign>Nonnegative</v8:AllowedSign>");
    }

    @Test
    void xg10_compositeColumnType_emitsSeparateV8Types() throws Exception {
        List<FormDsl.Attribute> attrs = List.of(
                new FormDsl.Attribute("Таблица", null, "ValueTable", false,
                        List.of(new FormDsl.Column("Ссылка", null,
                                "CatalogRef.А | DocumentRef.Б"))));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", null, attrs), out);

        String content = Files.readString(out);
        assertThat(content).contains("<v8:Type>cfg:CatalogRef.А</v8:Type>");
        assertThat(content).contains("<v8:Type>cfg:DocumentRef.Б</v8:Type>");
        assertNoPipeInsideV8Type(content);
    }

    // ==================== XG-12: Type-дискриминатор группы ====================

    @Test
    void xg12_groupElement_noTypeChildInUsualGroup() throws Exception {
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "group", "name", "ГруппаШапка",
                        "children", List.of(
                                Map.of("type", "input", "name", "Поле1", "path", "Реквизит1"))));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<UsualGroup name=\"ГруппаШапка\"");
        // Дискриминатор DSL не должен сериализоваться как XML-поле
        assertThat(content).doesNotContain("<Type>group</Type>");
        // Вложенный элемент не потерян (регрессия XG-01)
        assertThat(content).contains("name=\"Поле1\"");
    }

    private static void assertNoPipeInsideV8Type(String content) {
        Matcher m = Pattern.compile("<v8:Type>([^<]*)</v8:Type>").matcher(content);
        while (m.find()) {
            assertThat(m.group(1)).as("v8:Type must not contain pipe").doesNotContain("|");
        }
    }

    // ==================== XG-14: Button обязан иметь <Type>UsualButton</Type> ====================

    @Test
    void xg14_compileButton_emitsUsualButtonTypeFirst() throws Exception {
        // TASK-174 XG-14: form compile генерирует Button с <Type>UsualButton</Type> первым дочерним тегом.
        // Без него Designer молча отбрасывает кнопку при LoadExternalDataProcessorOrReportFromFiles.
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "button", "name", "КнопкаОК", "command", "ОК"));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Button name=\"КнопкаОК\"");
        assertThat(content).contains("<Type>UsualButton</Type>");
        // <Type>UsualButton</Type> должен быть ПЕРЕД <CommandName>
        int typePos = content.indexOf("<Type>UsualButton</Type>");
        int cmdPos = content.indexOf("<CommandName>");
        assertThat(typePos).isPositive();
        assertThat(cmdPos).isGreaterThan(typePos);
    }

    @Test
    void xg14_compileButtonInGroup_emitsUsualButtonType() throws Exception {
        // XG-14: кнопка внутри группы тоже должна иметь <Type>UsualButton</Type>
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "group", "name", "Группа1",
                        "children", List.of(
                                Map.of("type", "button", "name", "КнопкаВнутри", "command", "Команда"))));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Button name=\"КнопкаВнутри\"");
        assertThat(content).contains("<Type>UsualButton</Type>");
    }

    // ==================== XG-15: UsualGroup/Pages/Page/CommandBar/Popup обязаны иметь <ChildItems> ====================

    @Test
    void xg15_compileEmptyGroup_emitsChildItemsWrapper() throws Exception {
        // TASK-174 XG-15: UsualGroup ВСЕГДА должна иметь <ChildItems>, даже если в DSL нет children.
        // Без обёртки Designer молча отбрасывает всё поддерево при LoadExternalDataProcessorOrReportFromFiles.
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "group", "name", "ГруппаПустая"));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<UsualGroup name=\"ГруппаПустая\"");
        // <ChildItems> должна быть ВНУТРИ UsualGroup
        int groupStart = content.indexOf("<UsualGroup name=\"ГруппаПустая\"");
        int groupEnd = content.indexOf("</UsualGroup>", groupStart);
        int childItemsStart = content.indexOf("<ChildItems>", groupStart);
        assertThat(childItemsStart).isGreaterThan(groupStart);
        assertThat(childItemsStart).isLessThan(groupEnd);
    }

    @Test
    void xg15_compileGroupWithChildren_emitsChildItemsWithContent() throws Exception {
        // XG-15: UsualGroup с детьми: дети должны быть внутри <ChildItems>
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "group", "name", "ГруппаСДетьми",
                        "children", List.of(
                                Map.of("type", "input", "name", "Поле1", "path", "Реквизит1"),
                                Map.of("type", "button", "name", "Кнопка1", "command", "Команда"))));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        // Дети внутри ChildItems (не между </ExtendedTooltip> и </UsualGroup> напрямую)
        int childItemsStart = content.indexOf("<ChildItems>");
        int childItemsEnd = content.indexOf("</ChildItems>");
        int inputPos = content.indexOf("<InputField name=\"Поле1\"");
        int buttonPos = content.indexOf("<Button name=\"Кнопка1\"");
        assertThat(inputPos).isGreaterThan(childItemsStart);
        assertThat(inputPos).isLessThan(childItemsEnd);
        assertThat(buttonPos).isGreaterThan(childItemsStart);
        assertThat(buttonPos).isLessThan(childItemsEnd);
        // Кнопка внутри группы тоже имеет Type UsualButton
        assertThat(content).contains("<Type>UsualButton</Type>");
    }

    @Test
    void xg15_compilePages_emitsChildItemsWrapper() throws Exception {
        // XG-15: Pages ВСЕГДА должна иметь <ChildItems>
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "pages", "name", "СтраницыГруппа"));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Pages name=\"СтраницыГруппа\"");
        int pagesStart = content.indexOf("<Pages name=\"СтраницыГруппа\"");
        int pagesEnd = content.indexOf("</Pages>", pagesStart);
        int childItemsPos = content.indexOf("<ChildItems>", pagesStart);
        assertThat(childItemsPos).isGreaterThan(pagesStart);
        assertThat(childItemsPos).isLessThan(pagesEnd);
    }

    @Test
    void xg15_compilePage_emitsChildItemsWrapper() throws Exception {
        // XG-15: Page (вкладка) ВСЕГДА должна иметь <ChildItems>
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "page", "name", "ВкладкаОсновная"));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Page name=\"ВкладкаОсновная\"");
        int pageStart = content.indexOf("<Page name=\"ВкладкаОсновная\"");
        int pageEnd = content.indexOf("</Page>", pageStart);
        int childItemsPos = content.indexOf("<ChildItems>", pageStart);
        assertThat(childItemsPos).isGreaterThan(pageStart);
        assertThat(childItemsPos).isLessThan(pageEnd);
    }

    // ==================== XG-16: Button НЕ должен иметь <ContextMenu> ====================

    @Test
    void xg16_compileButton_noContextMenu() throws Exception {
        // TASK-174 XG-16: Button в каноне Designer содержит только ExtendedTooltip, без ContextMenu.
        // Подтверждено на формах проекта (ВыборФорматаВложений, ВводКонтактнойИнформации) и Designer-dump.
        // writeAutoElements добавлял ContextMenu+ExtendedTooltip — для Button это лишнее.
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "button", "name", "КнопкаТест", "command", "ТестКоманда"));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Button name=\"КнопкаТест\"");
        // Канон: ExtendedTooltip есть
        assertThat(content).contains("<ExtendedTooltip name=\"КнопкаТестРасширеннаяПодсказка\"");
        // Канон: ContextMenu НЕТ у Button
        assertThat(content).doesNotContain("<ContextMenu name=\"КнопкаТестКонтекстноеМеню\"");
    }

    @Test
    void xg16_compileButtonInGroup_noContextMenu() throws Exception {
        // XG-16: кнопка внутри группы тоже не должна иметь ContextMenu
        List<Map<String, Object>> elements = List.of(
                Map.of("type", "group", "name", "ГруппаКоманд",
                        "children", List.of(
                                Map.of("type", "button", "name", "КнопкаВГруппе", "command", "Команда"))));
        Path out = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl("Т", elements, null), out);

        String content = Files.readString(out);
        assertThat(content).contains("<Button name=\"КнопкаВГруппе\"");
        assertThat(content).doesNotContain("<ContextMenu name=\"КнопкаВГруппеКонтекстноеМеню\"");
        assertThat(content).contains("<ExtendedTooltip name=\"КнопкаВГруппеРасширеннаяПодсказка\"");
    }
}

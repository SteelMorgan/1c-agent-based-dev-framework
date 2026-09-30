package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.editor.BslModuleEditor;
import io.github.onec.xmlgen.model.BspCommandType;
import io.github.onec.xmlgen.model.BspKind;
import io.github.onec.xmlgen.model.BspTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpfBspWriterTest {

    @TempDir
    Path tempDir;

    private final EpfBspWriter writer = new EpfBspWriter();

    @Test
    void renderInfoFunction_PrintForm_HasNaznachenieAndModifier() {
        String out = writer.renderInfoFunction(
                BspKind.ПечатнаяФорма,
                List.of(BspTarget.parse("Документ.СчетНаОплату")),
                null, null, null);

        assertThat(out)
                .contains("Функция СведенияОВнешнейОбработке() Экспорт")
                .contains("ВидОбработкиПечатнаяФорма()")
                .contains("ТипКомандыВызовСерверногоМетода()")
                .contains("ПараметрыРегистрации.Назначение.Добавить(\"Документ.СчетНаОплату\")")
                .contains("Модификатор          = \"ПечатьMXL\"")
                .contains("Возврат ПараметрыРегистрации;");
    }

    @Test
    void renderInfoFunction_GlobalProcessor_NoNaznachenieNoModifier() {
        String out = writer.renderInfoFunction(
                BspKind.ДополнительнаяОбработка, null, null, null, null);

        assertThat(out)
                .contains("ВидОбработкиДополнительнаяОбработка()")
                .contains("ТипКомандыОткрытиеФормы()")
                .doesNotContain("Назначение.Добавить")
                .doesNotContain("ПечатьMXL");
    }

    @Test
    void renderInfoFunction_AssignableWithoutTarget_Throws() {
        assertThatThrownBy(() ->
                writer.renderInfoFunction(BspKind.ПечатнаяФорма, List.of(), null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires at least one target");
    }

    @Test
    void renderInfoFunction_CustomVersions() {
        String out = writer.renderInfoFunction(
                BspKind.ДополнительнаяОбработка, null, null, "3.0.0.1", "2.5");
        assertThat(out)
                .contains("СведенияОВнешнейОбработке(\"3.0.0.1\")")
                .contains("ПараметрыРегистрации.Версия = \"2.5\"");
    }

    @Test
    void kindMappingFromSynonyms() {
        assertThat(BspKind.parse("печать")).isEqualTo(BspKind.ПечатнаяФорма);
        assertThat(BspKind.parse("обработка")).isEqualTo(BspKind.ДополнительнаяОбработка);
        assertThat(BspKind.parse("заполнение")).isEqualTo(BspKind.ЗаполнениеОбъекта);
        assertThat(BspKind.parse("отчет")).isEqualTo(BspKind.Отчет);
        assertThat(BspKind.parse("ПечатнаяФорма")).isEqualTo(BspKind.ПечатнаяФорма);
        assertThat(BspKind.parse("связанные объекты")).isEqualTo(BspKind.СозданиеСвязанныхОбъектов);
        assertThatThrownBy(() -> BspKind.parse("xz")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void renderCommandBlock_BasicServer() {
        String out = writer.renderCommandBlock(
                "ЗаказПокупателя", "Заказ покупателя",
                BspCommandType.ВызовСерверногоМетода, BspKind.ДополнительнаяОбработка);
        assertThat(out)
                .contains("НСтр(\"ru = 'Заказ покупателя'\")")
                .contains("НоваяКоманда.Идентификатор        = \"ЗаказПокупателя\"")
                .contains("ТипКомандыВызовСерверногоМетода()")
                .doesNotContain("ПечатьMXL");
    }

    @Test
    void renderCommandBlock_PrintForm_HasModifier() {
        String out = writer.renderCommandBlock(
                "СчетФактура", "Счёт-фактура",
                BspCommandType.ВызовСерверногоМетода, BspKind.ПечатнаяФорма);
        assertThat(out)
                .contains("НоваяКоманда.Модификатор          = \"ПечатьMXL\"");
    }

    @Test
    void renderHandlerProcedure_PrintForm_IsPechat() {
        String out = writer.renderHandlerProcedure(
                BspKind.ПечатнаяФорма, BspCommandType.ВызовСерверногоМетода);
        assertThat(out)
                .contains("Процедура Печать(МассивОбъектов, КоллекцияПечатныхФорм, ОбъектыПечати, ПараметрыВывода)")
                .contains("// TODO");
    }

    @Test
    void renderHandlerProcedure_Assignable_HasObjectsTarget() {
        String out = writer.renderHandlerProcedure(
                BspKind.ЗаполнениеОбъекта, BspCommandType.ВызовСерверногоМетода);
        assertThat(out).contains("ИдентификаторКоманды, ОбъектыНазначения, ПараметрыВыполненияКоманды");
    }

    @Test
    void renderHandlerProcedure_ClientCommand_NoServerHandler() {
        String out = writer.renderHandlerProcedure(
                BspKind.ДополнительнаяОбработка, BspCommandType.ВызовКлиентскогоМетода);
        assertThat(out).isNull();
    }

    @Test
    void bspInit_OnAlreadyRegisteredModule_Errors() throws Exception {
        Path module = setupModuleWithExistingInfoFn();
        EpfBspApplier.InitOptions opts = new EpfBspApplier.InitOptions();
        opts.kind = BspKind.ДополнительнаяОбработка;

        assertThatThrownBy(() ->
                new EpfBspApplier().init(module.getParent().getParent(), opts))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void bspAddCommand_ServerType_AppendsBeforeReturn() throws Exception {
        Path module = setupModuleWithExistingInfoFn();
        EpfBspApplier.AddCommandOptions opts = new EpfBspApplier.AddCommandOptions();
        opts.identifier = "Команда2";
        opts.label = "Вторая команда";

        new EpfBspApplier().addCommand(module.getParent().getParent(), opts);

        String content = Files.readString(module);
        int idxBlock = content.indexOf("НоваяКоманда.Идентификатор        = \"Команда2\"");
        int idxReturn = content.indexOf("Возврат ПараметрыРегистрации");
        assertThat(idxBlock).isPositive();
        assertThat(idxBlock).isLessThan(idxReturn);
    }

    @Test
    void bspAddCommand_ServerType_AppendsBranchToExistingHandler() throws Exception {
        Path module = setupModuleWithExistingInfoFn();
        // Сначала зальём первую команду (создаст обработчик)
        EpfBspApplier.AddCommandOptions first = new EpfBspApplier.AddCommandOptions();
        first.identifier = "Первая";
        first.label = "Первая";
        new EpfBspApplier().addCommand(module.getParent().getParent(), first);

        // Вторая → должна добавиться ветка ИначеЕсли
        EpfBspApplier.AddCommandOptions second = new EpfBspApplier.AddCommandOptions();
        second.identifier = "Вторая";
        second.label = "Вторая";
        new EpfBspApplier().addCommand(module.getParent().getParent(), second);

        String content = Files.readString(module);
        assertThat(content).contains("Если ИдентификаторКоманды = \"Первая\" Тогда");
        assertThat(content).contains("ИначеЕсли ИдентификаторКоманды = \"Вторая\" Тогда");
    }

    /**
     * Создать модуль с уже зарегистрированной функцией для теста сценариев add-command.
     * Вид: ЗаполнениеОбъекта (assignable, ВызовСерверногоМетода).
     */
    private Path setupModuleWithExistingInfoFn() throws Exception {
        Path epfDir = tempDir.resolve("EpfTest");
        Path extDir = epfDir.resolve("Ext");
        Files.createDirectories(extDir);
        Path module = extDir.resolve("ObjectModule.bsl");

        String content = "#Область ПрограммныйИнтерфейс\n\n"
                + "Функция СведенияОВнешнейОбработке() Экспорт\n\n"
                + "\tМетаданныеОбработки = Метаданные();\n\n"
                + "\tПараметрыРегистрации = ДополнительныеОтчетыИОбработки.СведенияОВнешнейОбработке(\"2.2.2.1\");\n"
                + "\tПараметрыРегистрации.Вид    = ДополнительныеОтчетыИОбработкиКлиентСервер.ВидОбработкиЗаполнениеОбъекта();\n"
                + "\tПараметрыРегистрации.Версия = \"1.0\";\n\n"
                + "\tПараметрыРегистрации.Назначение.Добавить(\"Документ.СчетНаОплату\");\n\n"
                + "\tНоваяКоманда = ПараметрыРегистрации.Команды.Добавить();\n"
                + "\tНоваяКоманда.Представление        = МетаданныеОбработки.Представление();\n"
                + "\tНоваяКоманда.Идентификатор        = МетаданныеОбработки.Имя;\n"
                + "\tНоваяКоманда.Использование        = ДополнительныеОтчетыИОбработкиКлиентСервер.ТипКомандыВызовСерверногоМетода();\n"
                + "\tНоваяКоманда.ПоказыватьОповещение = Ложь;\n\n"
                + "\tВозврат ПараметрыРегистрации;\n\n"
                + "КонецФункции\n\n"
                + "#КонецОбласти\n";
        Files.writeString(module, content);
        return module;
    }

    @Test
    void smokePrintForm_FullFlow() throws Exception {
        // Полноценный smoke: init для ПечатнаяФорма + 2× add-command
        Path epfDir = tempDir.resolve("EpfPrint");
        Path extDir = epfDir.resolve("Ext");
        Files.createDirectories(extDir);
        Path module = extDir.resolve("ObjectModule.bsl");
        // изначально пусто (как создаёт epf init)
        Files.writeString(module, "// Модуль объекта обработки EpfPrint\n");

        EpfBspApplier.InitOptions initOpts = new EpfBspApplier.InitOptions();
        initOpts.kind = BspKind.ПечатнаяФорма;
        initOpts.targets = List.of(BspTarget.parse("Документ.СчетНаОплату"));
        new EpfBspApplier().init(epfDir, initOpts);

        EpfBspApplier.AddCommandOptions add1 = new EpfBspApplier.AddCommandOptions();
        add1.identifier = "ПФ_ДоговорКонтрагента";
        add1.label = "Договор";
        new EpfBspApplier().addCommand(epfDir, add1);

        EpfBspApplier.AddCommandOptions add2 = new EpfBspApplier.AddCommandOptions();
        add2.identifier = "ПФ_АктСверки";
        add2.label = "Акт сверки";
        new EpfBspApplier().addCommand(epfDir, add2);

        String c = Files.readString(module);
        assertThat(c).contains("Функция СведенияОВнешнейОбработке()");
        assertThat(c).contains("ВидОбработкиПечатнаяФорма()");
        assertThat(c).contains("Назначение.Добавить(\"Документ.СчетНаОплату\")");
        assertThat(c).contains("Процедура Печать(");
        assertThat(c).contains("\"ПФ_ДоговорКонтрагента\"");
        assertThat(c).contains("\"ПФ_АктСверки\"");
        // Каждая печатная команда должна добавить блок в Печать()
        assertThat(c).contains("Сформировать" + "ПФ_ДоговорКонтрагента" + "(МассивОбъектов, ОбъектыПечати)");
        assertThat(c).contains("Сформировать" + "ПФ_АктСверки" + "(МассивОбъектов, ОбъектыПечати)");
    }

    @Test
    void bspInit_GlobalWithTarget_IgnoresTarget() throws Exception {
        Path epfDir = tempDir.resolve("EpfGlobal");
        Path extDir = epfDir.resolve("Ext");
        Files.createDirectories(extDir);
        Path module = extDir.resolve("ObjectModule.bsl");
        Files.writeString(module, "");

        EpfBspApplier.InitOptions opts = new EpfBspApplier.InitOptions();
        opts.kind = BspKind.ДополнительнаяОбработка;
        opts.targets = List.of(BspTarget.parse("Документ.СчетНаОплату"));
        new EpfBspApplier().init(epfDir, opts);

        String c = Files.readString(module);
        assertThat(c).doesNotContain("Назначение.Добавить");
        assertThat(c).contains("ВидОбработкиДополнительнаяОбработка()");
    }
}

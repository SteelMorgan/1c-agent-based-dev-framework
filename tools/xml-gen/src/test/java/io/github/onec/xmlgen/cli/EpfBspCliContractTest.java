package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

@DisplayName("integr-EPF BSP CLI contract")
class EpfBspCliContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-bsp-init rejects unknown option before ObjectModule mutation")
    void bspInit_unknownOption_rejectsBeforeObjectModuleMutation() throws Exception {
        Path epfDir = createEpfDir("BspInitUnknownOption");
        Path objectModule = objectModule(epfDir);
        String before = Files.readString(objectModule, StandardCharsets.UTF_8);

        Throwable thrown = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-init", epfDir.toString(),
                "--kind", "ДополнительнаяОбработка",
                "--bogus"
        }));

        assertThat(thrown)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown option")
                .hasMessageContaining("--bogus");
        assertThat(Files.readString(objectModule, StandardCharsets.UTF_8)).isEqualTo(before);
    }

    @Test
    @DisplayName("integr-bsp-add-command duplicate id keeps one command block and one handler")
    void bspAddCommand_duplicateId_keepsSingleCommandBlockAndHandler() throws Exception {
        Path epfDir = createInitializedAssignableProcessor("BspDuplicateCommand");

        Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "Пересчитать",
                "--label", "Пересчитать"
        });

        Throwable duplicate = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "Пересчитать",
                "--label", "Пересчитать повторно"
        }));

        assertThat(duplicate)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("already exists");
        String content = Files.readString(objectModule(epfDir), StandardCharsets.UTF_8);
        assertThat(countOccurrences(content, "НоваяКоманда.Идентификатор        = \"Пересчитать\""))
                .as("duplicate --id must not create a second registration command block")
                .isEqualTo(1);
        assertThat(countOccurrences(content, "ИдентификаторКоманды = \"Пересчитать\""))
                .as("duplicate --id must not create a second server handler branch")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("integr-bsp-add-command client without form leaves ObjectModule unchanged")
    void bspAddCommand_clientWithoutForm_failsBeforeObjectModuleMutation() throws Exception {
        Path epfDir = createInitializedAssignableProcessor("BspClientWithoutForm");
        Path objectModule = objectModule(epfDir);
        String before = Files.readString(objectModule, StandardCharsets.UTF_8);

        Throwable thrown = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "ОткрытьКлиентски",
                "--label", "Открыть клиентски",
                "--type", "client"
        }));

        assertThat(thrown).isNotNull();
        assertThat(Files.readString(objectModule, StandardCharsets.UTF_8)).isEqualTo(before);
    }

    @Test
    @DisplayName("integr-bsp-add-command client with missing form module leaves ObjectModule unchanged")
    void bspAddCommand_clientWithMissingFormModule_failsBeforeObjectModuleMutation() throws Exception {
        Path epfDir = createInitializedAssignableProcessor("BspClientMissingFormModule");
        Files.createDirectories(epfDir.resolve("Forms").resolve("Main").resolve("Ext"));
        Files.writeString(
                epfDir.resolve("Forms").resolve("Main").resolve("Ext").resolve("Form.xml"),
                "<Form/>",
                StandardCharsets.UTF_8);
        Path objectModule = objectModule(epfDir);
        String before = Files.readString(objectModule, StandardCharsets.UTF_8);

        Throwable thrown = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "ОткрытьMain",
                "--label", "Открыть Main",
                "--type", "client",
                "--form", "Main"
        }));

        assertThat(thrown).isNotNull();
        assertThat(Files.readString(objectModule, StandardCharsets.UTF_8)).isEqualTo(before);
    }

    @Test
    @DisplayName("integr-bsp-add-command client read-only ObjectModule leaves form module unchanged")
    void bspAddCommand_clientReadOnlyObjectModule_leavesFormModuleUnchanged() throws Exception {
        Path epfDir = createInitializedAssignableProcessor("BspClientReadOnlyObjectModule");
        Path formModule = epfDir.resolve("Forms/Main/Ext/Form/Module.bsl");
        Files.createDirectories(formModule.getParent());
        Files.writeString(formModule,
                "#Область ОбработчикиСобытийФормы\n\n#КонецОбласти\n",
                StandardCharsets.UTF_8);
        Path objectModule = objectModule(epfDir);
        String objectBefore = Files.readString(objectModule, StandardCharsets.UTF_8);
        String formBefore = Files.readString(formModule, StandardCharsets.UTF_8);
        objectModule.toFile().setReadOnly();

        Throwable thrown = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "ОткрытьMain",
                "--label", "Открыть Main",
                "--type", "client",
                "--form", "Main"
        }));

        assertThat(thrown).isNotNull();
        assertThat(Files.readString(objectModule, StandardCharsets.UTF_8)).isEqualTo(objectBefore);
        assertThat(Files.readString(formModule, StandardCharsets.UTF_8)).isEqualTo(formBefore);
    }

    @Test
    @DisplayName("integr-bsp-add-command client read-only form module rolls back ObjectModule")
    void bspAddCommand_clientReadOnlyFormModule_rollsBackObjectModule() throws Exception {
        Path epfDir = createInitializedAssignableProcessor("BspClientReadOnlyFormModule");
        Path formModule = epfDir.resolve("Forms/Main/Ext/Form/Module.bsl");
        Files.createDirectories(formModule.getParent());
        Files.writeString(formModule,
                "#Область ОбработчикиСобытийФормы\n\n#КонецОбласти\n",
                StandardCharsets.UTF_8);
        Path objectModule = objectModule(epfDir);
        String objectBefore = Files.readString(objectModule, StandardCharsets.UTF_8);
        String formBefore = Files.readString(formModule, StandardCharsets.UTF_8);
        formModule.toFile().setReadOnly();

        Throwable thrown = catchThrowable(() -> Commands.execute("epf", new String[]{
                "bsp-add-command", epfDir.toString(),
                "--id", "ОткрытьMain",
                "--label", "Открыть Main",
                "--type", "client",
                "--form", "Main"
        }));

        assertThat(thrown).isNotNull();
        assertThat(Files.readString(objectModule, StandardCharsets.UTF_8)).isEqualTo(objectBefore);
        assertThat(Files.readString(formModule, StandardCharsets.UTF_8)).isEqualTo(formBefore);
    }

    private Path createInitializedAssignableProcessor(String name) throws Exception {
        Path epfDir = createEpfDir(name);
        Commands.execute("epf", new String[]{
                "bsp-init", epfDir.toString(),
                "--kind", "ЗаполнениеОбъекта",
                "--target", "Документ.СчетНаОплату"
        });
        return epfDir;
    }

    private Path createEpfDir(String name) throws Exception {
        Path epfDir = tempDir.resolve(name);
        Files.createDirectories(epfDir.resolve("Ext"));
        Files.writeString(
                objectModule(epfDir),
                "#Область ПрограммныйИнтерфейс\n\n#КонецОбласти\n",
                StandardCharsets.UTF_8);
        return epfDir;
    }

    private Path objectModule(Path epfDir) {
        return epfDir.resolve("Ext").resolve("ObjectModule.bsl");
    }

    private int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}

package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 Phase 4 (Tester): R-M.2 регрессия W-01 (XG-36, backlog.md §5) —
 * {@code extension init} с {@code --config-path}, где Configuration.xml базы
 * НЕ содержит тега {@code InterfaceCompatibilityMode}: fallback на дефолт
 * {@code TaxiEnableVersion8_2} (upstream cfe-init v1.1: regex-чтение без
 * совпадения → дефолтная переменная не перезаписывается).
 *
 * <p>Phase 3b покрыла позитивный кейс (тег есть → наследуется) и кейс
 * без --config-path; ветка «config задан, тега нет» не покрыта.</p>
 */
class ExtensionWriterTask175Phase4Test {

    @TempDir
    Path tempDir;

    /** Базовая конфигурация БЕЗ тега InterfaceCompatibilityMode. */
    private static final String CFG_WITHOUT_INTERFACE_MODE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                    + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                    + "\t\t<Properties>\n"
                    + "\t\t\t<Name>Тест</Name>\n"
                    + "\t\t\t<CompatibilityMode>Version8_3_24</CompatibilityMode>\n"
                    + "\t\t</Properties>\n"
                    + "\t\t<ChildObjects/>\n"
                    + "\t</Configuration>\n"
                    + "</MetaDataObject>\n";

    private ExtensionWriter silent() {
        return new ExtensionWriter(new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    void rm2_cfeInit_configWithoutInterfaceModeTag_fallsBackToDefault() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("Configuration.xml"),
                CFG_WITHOUT_INTERFACE_MODE, StandardCharsets.UTF_8);

        Path extDir = tempDir.resolve("ext");
        silent().create(extDir, "МоёРасширение", null, null,
                null, null, null, null, baseDir, true);

        String cfg = Files.readString(extDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(cfg)
                .as("Configuration.xml базы без тега InterfaceCompatibilityMode → "
                        + "fallback TaxiEnableVersion8_2 (upstream else-семантика 72bad1aa)")
                .contains("<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>");
        assertThat(cfg)
                .as("CompatibilityMode при этом наследуется штатно — соседний канал не задет")
                .contains("<ConfigurationExtensionCompatibilityMode>Version8_3_24"
                        + "</ConfigurationExtensionCompatibilityMode>");
    }
}

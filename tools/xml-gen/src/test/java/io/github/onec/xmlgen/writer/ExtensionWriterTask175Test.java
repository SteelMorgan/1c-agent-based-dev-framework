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
 * TASK-175 W-01 (XG-36, часть 2 — сосед того же коммита 72bad1aa):
 * {@code extension init} с {@code --config-path} обязан читать
 * {@code InterfaceCompatibilityMode} из базовой конфигурации (cfe-init v1.1,
 * зеркально существующему авто-чтению CompatibilityMode); без
 * {@code --config-path} — дефолт {@code TaxiEnableVersion8_2}.
 *
 * <p>Дефект: ExtensionWriter.java:208 — безусловный хардкод
 * {@code TaxiEnableVersion8_2}, режим базы игнорируется.</p>
 */
class ExtensionWriterTask175Test {

    @TempDir
    Path tempDir;

    /** Базовая конфигурация с НЕдефолтным режимом интерфейса (Taxi). */
    private static final String CFG_TAXI =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                    + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                    + "\t\t<Properties>\n"
                    + "\t\t\t<Name>Тест</Name>\n"
                    + "\t\t\t<CompatibilityMode>Version8_3_24</CompatibilityMode>\n"
                    + "\t\t\t<InterfaceCompatibilityMode>Taxi</InterfaceCompatibilityMode>\n"
                    + "\t\t</Properties>\n"
                    + "\t\t<ChildObjects/>\n"
                    + "\t</Configuration>\n"
                    + "</MetaDataObject>\n";

    private ExtensionWriter silent() {
        return new ExtensionWriter(new PrintStream(new ByteArrayOutputStream()));
    }

    @Test
    void w01_cfeInit_inheritsInterfaceCompatibilityModeFromBaseConfig() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("Configuration.xml"), CFG_TAXI, StandardCharsets.UTF_8);

        Path extDir = tempDir.resolve("ext");
        silent().create(extDir, "МоёРасширение", null, null,
                null, null, null, null, baseDir, true);

        String cfg = Files.readString(extDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(cfg)
                .as("расширение должно наследовать InterfaceCompatibilityMode базы (cfe-init v1.1, 72bad1aa)")
                .contains("<InterfaceCompatibilityMode>Taxi</InterfaceCompatibilityMode>");
        assertThat(cfg)
                .as("хардкод-дефолт не должен перекрывать режим базовой конфигурации")
                .doesNotContain("<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>");
    }

    @Test
    void w01_cfeInit_withoutConfigPath_defaultsToTaxiEnableVersion8_2() throws Exception {
        // Регрессионный кейс: без --config-path дефолт TaxiEnableVersion8_2
        // (upstream: $InterfaceCompatibilityMode = "TaxiEnableVersion8_2" в else-ветке).
        Path extDir = tempDir.resolve("ext2");
        silent().create(extDir, "Расш2", null, null,
                null, null, null, null, null, true);

        String cfg = Files.readString(extDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(cfg)
                .contains("<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>");
    }
}

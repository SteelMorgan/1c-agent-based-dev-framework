package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 W-01 (XG-36, часть 1): {@code config init} обязан писать
 * {@code InterfaceCompatibilityMode=TaxiEnableVersion8_2}, а не {@code Taxi}
 * (коммит Широкова 72bad1aa, cf-init v1.1: «matches all real configs:
 * acc 8.3.20/24/27, erp 8.3.24»).
 *
 * <p>Дефект: ConfigWriter.java:143 — хардкод
 * {@code <InterfaceCompatibilityMode>Taxi</InterfaceCompatibilityMode>}.</p>
 */
class ConfigWriterTask175Test {

    @TempDir
    Path tempDir;

    @Test
    void w01_configInit_emitsTaxiEnableVersion8_2() throws Exception {
        Path outDir = tempDir.resolve("cfg");
        new ConfigWriter().create(outDir, "ТестКонфиг", null, null, null, null, null);

        String xml = Files.readString(outDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(xml)
                .as("режим интерфейса должен соответствовать реальным конфигурациям 8.3.20+ (72bad1aa)")
                .contains("<InterfaceCompatibilityMode>TaxiEnableVersion8_2</InterfaceCompatibilityMode>");
        assertThat(xml)
                .as("устаревшее значение Taxi не должно эмитироваться")
                .doesNotContain(">Taxi<");
    }
}

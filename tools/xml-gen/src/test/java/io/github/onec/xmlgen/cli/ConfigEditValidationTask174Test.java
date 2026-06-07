package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.writer.ConfigWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigEditValidationTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void configEditInvalidEnum_rollsBackWithoutChangingFile() throws Exception {
        Path outDir = tempDir.resolve("cfg");
        new ConfigWriter().create(outDir, "ТестКонфиг", null, null, null, null, null);
        Path configXml = outDir.resolve("Configuration.xml");
        byte[] before = Files.readAllBytes(configXml);

        assertThatThrownBy(() -> Commands.execute("config", new String[] {
                "edit",
                configXml.toString(),
                "--op", "modify-property",
                "--value", "CompatibilityMode=Garbage"
        }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation errors")
                .hasMessageContaining("CompatibilityMode");

        assertThat(Files.readAllBytes(configXml)).isEqualTo(before);
    }
}

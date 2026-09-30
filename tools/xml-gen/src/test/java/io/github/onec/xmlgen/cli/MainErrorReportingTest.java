package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MainErrorReportingTest {

    @Test
    void illegalArgumentUsesUnifiedErrorPrefix() throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        Process process = new ProcessBuilder(
                javaBin,
                "-cp",
                System.getProperty("java.class.path"),
                Main.class.getName(),
                "unknown-command")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).isTrue();
        assertThat(process.exitValue()).isEqualTo(1);
        assertThat(stderr).startsWith("ERROR: Unknown command: unknown-command");
        assertThat(stderr).doesNotContain("Exception");
    }
}

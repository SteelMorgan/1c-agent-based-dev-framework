package io.github.onec.xmlgen.cli;

//++agent TASK-174 [14.07.2026 06:33:00] XG-103

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigInitCliXg103Test {

    @TempDir
    Path tempDir;

    @Test
    void configInitHelpExitsZeroAndDocumentsCanonicalPositionalContract() throws Exception {
        CliResult result = runMain("config", "init", "--help");

        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        assertThat(result.stdout())
                .contains("Usage: xml-gen config init <outputDir> <name>")
                .doesNotContain("--name");
        assertThat(result.stderr()).isEmpty();
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void canonicalNestedOutputDirAndNameCreateExactValidConfiguration() throws Exception {
        Path outputDir = tempDir.resolve("nested").resolve("deep").resolve("configuration");

        CliResult init = runMain("config", "init", outputDir.toString(), "ТестоваяКонфигурация",
                "--compat", "Version8_3_24");

        assertThat(init.exitCode()).as(init.combinedOutput()).isZero();
        assertThat(outputDir.resolve("Configuration.xml")).isRegularFile();
        assertThat(outputDir.resolve("ConfigDumpInfo.xml")).isRegularFile();
        assertThat(outputDir.resolve("Languages/Русский.xml")).isRegularFile();
        assertThat(outputDir.resolve("Ext/ManagedApplicationModule.bsl")).isRegularFile();
        assertThat(outputDir.resolve("Ext/SessionModule.bsl")).isRegularFile();
        assertThat(Files.readString(outputDir.resolve("Configuration.xml"), StandardCharsets.UTF_8))
                .contains("<Name>ТестоваяКонфигурация</Name>");
        assertThat(tempDir.resolve("ТестоваяКонфигурация")).doesNotExist();

        CliResult validate = runMain("config", "validate", outputDir.resolve("Configuration.xml").toString());
        assertThat(validate.exitCode()).as(validate.combinedOutput()).isZero();
        assertThat(validate.stdout()).contains("OK: Configuration is valid");
    }

    @Test
    void legacyNameOptionIsRejectedBeforeAnyDirectoryIsWritten() throws Exception {
        Path legacyOutput = tempDir.resolve("legacy-output");

        CliResult result = runMain("config", "init", "--name", "LegacyConfig", legacyOutput.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.stderr()).contains("ERROR: Unknown config init option: --name");
        assertThat(legacyOutput).doesNotExist();
        assertThat(tempDir.resolve("--name")).doesNotExist();
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void extraPositionalArgumentIsRejectedBeforeOutputDirIsWritten() throws Exception {
        Path outputDir = tempDir.resolve("must-not-exist");

        CliResult result = runMain("config", "init", outputDir.toString(), "ValidConfig", "extra");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.stderr()).contains("ERROR: Unexpected config init argument: extra");
        assertThat(outputDir).doesNotExist();
        assertThat(tempDir.resolve("extra")).doesNotExist();
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void timedOutChildIsForciblyDestroyedWithoutProcessLeak() throws Exception {
        Path pidFile = tempDir.resolve("hanging-child.pid");
        String javaBin = System.getProperty("java.home") + "/bin/java";
        List<String> command = List.of(
                javaBin,
                "-cp",
                System.getProperty("java.class.path"),
                HangingChild.class.getName(),
                pidFile.toString());

        assertThatThrownBy(() -> runProcess(command, Duration.ofSeconds(2)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("timed out");

        long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8));
        assertThat(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false)).isFalse();
    }

    private CliResult runMain(String... args) throws Exception {
        String javaBin = System.getProperty("java.home") + "/bin/java";
        List<String> command = new java.util.ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        return runProcess(command, Duration.ofSeconds(15));
    }

    private CliResult runProcess(List<String> command, Duration timeout) throws Exception {
        Path stdoutFile = Files.createTempFile("xml-gen-xg103-stdout-", ".log");
        Path stderrFile = Files.createTempFile("xml-gen-xg103-stderr-", ".log");
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(tempDir.toFile())
                    .redirectOutput(stdoutFile.toFile())
                    .redirectError(stderrFile.toFile())
                    .start();

            boolean exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                process.destroyForcibly();
                boolean destroyed = process.waitFor(5, TimeUnit.SECONDS);
                if (!destroyed) {
                    throw new AssertionError("CLI process did not terminate after destroyForcibly");
                }
                throw new AssertionError("CLI process timed out after " + timeout);
            }

            String stdout = Files.readString(stdoutFile, StandardCharsets.UTF_8);
            String stderr = Files.readString(stderrFile, StandardCharsets.UTF_8);
            return new CliResult(process.exitValue(), stdout, stderr);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);
        }
    }

    private record CliResult(int exitCode, String stdout, String stderr) {
        private String combinedOutput() {
            return stdout + stderr;
        }
    }

    public static final class HangingChild {
        public static void main(String[] args) throws Exception {
            Files.writeString(Path.of(args[0]), Long.toString(ProcessHandle.current().pid()),
                    StandardCharsets.UTF_8);
            Thread.sleep(60_000);
        }
    }
}

//++agent TASK-174

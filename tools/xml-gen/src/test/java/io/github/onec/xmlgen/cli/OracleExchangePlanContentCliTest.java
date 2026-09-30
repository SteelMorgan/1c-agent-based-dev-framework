package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OracleExchangePlanContentCliTest {

    @TempDir
    Path tempDir;

    @Test
    void oracleExchangePlanContentReconstructsItemsThroughPublicMetaCli() throws Exception {
        writeContent("OracleExchange", """
                \t<Item>
                \t\t<Metadata>Catalog.Товары</Metadata>
                \t\t<AutoRecord>Deny</AutoRecord>
                \t</Item>
                \t<Item>
                \t\t<Metadata>Document.Заказ</Metadata>
                \t\t<AutoRecord>Allow</AutoRecord>
                \t</Item>
                """);
        writeContent("MobileExchange", """
                \t<Item>
                \t\t<Metadata>InformationRegister.Состояния</Metadata>
                \t\t<AutoRecord>Deny</AutoRecord>
                \t</Item>
                """);

        ProcessResult result = runMain("oracle", "exchange-plan-content",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("oracle-exchange").toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        String report = Files.readString(
                tempDir.resolve("oracle-exchange/latest-exchange-plan-content-oracle-report.json"),
                StandardCharsets.UTF_8);
        assertThat(report).contains("\"pilot\" : \"exchange-plan-content\"");
        assertThat(report).contains("\"objectId\" : \"ExchangePlans_OracleExchange\"");
        assertThat(report).contains("\"objectId\" : \"ExchangePlans_MobileExchange\"");
        assertThat(report).contains("\"checked\" : 2", "\"pass\" : 2");
        assertThat(report).doesNotContain("\"status\" : \"FAIL\"");
        assertThat(report).doesNotContain("\"status\" : \"ERROR\"");
        assertThat(report).contains("\"meta\"", "\"edit\"", "\"add-exchange-content\"");
    }

    private void writeContent(String name, String items) throws Exception {
        Path file = tempDir.resolve("src/xml").resolve("ExchangePlans").resolve(name)
                .resolve("Ext").resolve("Content.xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExchangePlanContent xmlns="http://v8.1c.ru/8.3/xcf/extrnprops" xmlns:xr="http://v8.1c.ru/8.3/xcf/readable" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.20">
                %s</ExchangePlanContent>
                """.formatted(items), StandardCharsets.UTF_8);
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

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

class OraclePredefinedDataCliTest {

    @TempDir
    Path tempDir;

    @Test
    void oraclePredefinedDataReconstructsSimpleItemsThroughPublicMetaCli() throws Exception {
        writePredefined("Catalogs", "OracleCatalog", "CatalogPredefinedItems", """
                \t<Item id="00000000-0000-0000-0000-000000000001">
                \t\t<Name>Основной</Name>
                \t\t<Code>000000001</Code>
                \t\t<Description>Основной элемент</Description>
                \t\t<IsFolder>false</IsFolder>
                \t</Item>
                \t<Item id="00000000-0000-0000-0000-000000000002">
                \t\t<Name>Группа</Name>
                \t\t<Code>000000002</Code>
                \t\t<Description>Группа</Description>
                \t\t<IsFolder>true</IsFolder>
                \t</Item>
                """);
        writePredefined("ChartsOfCharacteristicTypes", "OracleKinds",
                "PlanOfCharacteristicKindPredefinedItems", """
                \t<Item id="00000000-0000-0000-0000-000000000003">
                \t\t<Name>Контрагенты</Name>
                \t\t<Code>000000001</Code>
                \t\t<Description>Контрагенты</Description>
                \t\t<IsFolder>false</IsFolder>
                \t</Item>
                """);
        writePredefined("ChartsOfCalculationTypes", "OracleCalc",
                "CalculationTypePredefinedItems", """
                \t<Item id="00000000-0000-0000-0000-000000000004">
                \t\t<Name>Оклад</Name>
                \t\t<Code>00001</Code>
                \t\t<Description>Оклад</Description>
                \t\t<IsFolder>false</IsFolder>
                \t</Item>
                """);
        writePredefined("ChartsOfAccounts", "OracleAccounts", "ChartOfAccountsPredefinedItems", """
                \t<Item id="00000000-0000-0000-0000-000000000005">
                \t\t<Name>Активный</Name>
                \t\t<Code>01</Code>
                \t\t<Description>Активный</Description>
                \t\t<IsFolder>false</IsFolder>
                \t</Item>
                """);

        ProcessResult result = runMain("oracle", "predefined-data",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("oracle-predefined").toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        String report = Files.readString(
                tempDir.resolve("oracle-predefined/latest-predefined-data-oracle-report.json"),
                StandardCharsets.UTF_8);
        assertThat(report).contains("\"pilot\" : \"predefined-data\"");
        assertThat(report).contains("\"objectId\" : \"Catalogs_OracleCatalog\"");
        assertThat(report).contains("\"objectId\" : \"ChartsOfCharacteristicTypes_OracleKinds\"");
        assertThat(report).contains("\"objectId\" : \"ChartsOfCalculationTypes_OracleCalc\"");
        assertThat(report).contains("\"objectId\" : \"ChartsOfAccounts_OracleAccounts\"");
        assertThat(report).contains("\"status\" : \"PASS\"");
        assertThat(report).doesNotContain("\"status\" : \"FAIL\"");
        assertThat(report).doesNotContain("\"status\" : \"ERROR\"");
        assertThat(report).contains("\"xml-gen\"", "\"meta\"", "\"edit\"", "\"add-predefined\"");
    }

    private void writePredefined(String dir, String name, String xsiType, String items) throws Exception {
        Path file = tempDir.resolve("src/xml").resolve(dir).resolve(name).resolve("Ext").resolve("Predefined.xml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <PredefinedData xmlns="http://v8.1c.ru/8.3/xcf/predef" xmlns:v8="http://v8.1c.ru/8.1/data/core" xmlns:xr="http://v8.1c.ru/8.3/xcf/readable" xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="%s" version="2.20">
                %s</PredefinedData>
                """.formatted(xsiType, items), StandardCharsets.UTF_8);
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

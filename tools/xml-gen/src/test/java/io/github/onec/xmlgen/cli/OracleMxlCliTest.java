package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.MxlWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OracleMxlCliTest {

    @TempDir
    Path tempDir;

    @Test
    void oracleMxlRunsDslAndCliModesWithoutOverwritingCanon() throws Exception {
        Path canon = tempDir.resolve("src/xml/CommonTemplates/_ДемоMxl/Ext/Template.xml");
        Files.createDirectories(canon.getParent());
        String json = """
                {
                  "columns": 1,
                  "areas": [
                    {"name": "Main", "rows": [
                      {"cells": [{"col": 1, "text": "Hello"}]}
                    ]}
                  ]
                }
                """;
        MxlDsl dsl = new ObjectMapper().readValue(json, MxlDsl.class);
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, canon);
        byte[] before = Files.readAllBytes(canon);

        ProcessResult result = runMain("oracle", "mxl",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("oracle").toString(),
                "--mode", "both",
                "--limit", "1");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        assertThat(Files.readAllBytes(canon)).isEqualTo(before);
        assertThat(Files.exists(tempDir.resolve("oracle/latest-oracle-report.json"))).isTrue();
        String report = Files.readString(tempDir.resolve("oracle/latest-oracle-report.json"), StandardCharsets.UTF_8);
        assertThat(report).contains("\"dsl\"").contains("\"cli\"");
        assertThat(report).contains("\"objectId\" : \"CommonTemplates__ДемоMxl\"");
        assertThat(report).contains("\"commandPlanPath\"");
        assertThat(report).contains("\"objects\"");

        String coverage = Files.readString(findFirst(tempDir.resolve("oracle"), "coverage-matrix.json"), StandardCharsets.UTF_8);
        assertThat(coverage).contains("\"presentInDemo\"");
        assertThat(coverage).contains("\"status\"");
    }

    @Test
    void oracleMxlRejectsOutputInsideSource() throws Exception {
        Path canon = tempDir.resolve("src/xml/CommonTemplates/_ДемоMxl/Ext/Template.xml");
        Files.createDirectories(canon.getParent());
        MxlDsl dsl = new ObjectMapper().readValue("""
                {"columns":1,"areas":[{"name":"Main","rows":[{"cells":[{"col":1,"text":"X"}]}]}]}
                """, MxlDsl.class);
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, canon);

        ProcessResult result = runMain("oracle", "mxl",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("src/xml/build-oracle").toString(),
                "--mode", "dsl");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("--out must be outside --source");
        assertThat(tempDir.resolve("src/xml/build-oracle")).doesNotExist();
    }

    @Test
    void oracleMxlSingleFileDoesNotRequireDemoName() throws Exception {
        Path canon = tempDir.resolve("Template.xml");
        MxlDsl dsl = new ObjectMapper().readValue("""
                {"columns":1,"areas":[{"name":"Main","rows":[{"cells":[{"col":1,"text":"X"}]}]}]}
                """, MxlDsl.class);
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, canon);

        ProcessResult result = runMain("oracle", "mxl",
                "--source", canon.toString(),
                "--out", tempDir.resolve("oracle-single").toString(),
                "--mode", "dsl");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        String report = Files.readString(tempDir.resolve("oracle-single/latest-oracle-report.json"),
                StandardCharsets.UTF_8);
        assertThat(report).contains("\"objectId\" : \"Template.xml\"");
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

    private Path findFirst(Path root, String fileName) throws Exception {
        try (var stream = Files.walk(root)) {
            return stream.filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .orElseThrow();
        }
    }
}

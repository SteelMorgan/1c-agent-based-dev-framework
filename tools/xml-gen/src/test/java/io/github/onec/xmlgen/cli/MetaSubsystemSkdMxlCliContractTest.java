package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("integr-meta/subsystem/SKD/MXL CLI contract")
class MetaSubsystemSkdMxlCliContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-subsystem compile missing content target leaves no partial subsystem files")
    void subsystemCompileMissingContentTarget_leavesNoPartialFiles() throws Exception {
        Path outputDir = tempDir.resolve("Subsystems");
        Files.createDirectories(outputDir);
        Path json = tempDir.resolve("subsystem.json");
        Files.writeString(json, """
                { "name": "AuditSubsystem", "content": ["Catalog.MissingCatalog"] }
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("subsystem", "compile", json.toString(), outputDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(outputDir.resolve("AuditSubsystem.xml")).doesNotExist();
        assertThat(outputDir.resolve("AuditSubsystem/Ext/CommandInterface.xml")).doesNotExist();
    }

    @Test
    @DisplayName("integr-skd compile rejects unknown data set type before output")
    void skdCompileUnknownDataSetType_rejectsBeforeOutput() throws Exception {
        Path json = tempDir.resolve("bad-skd.json");
        Path output = tempDir.resolve("Template.xml");
        Files.writeString(json, """
                { "dataSets": [ { "name": "Bad", "type": "Bogus", "query": "ВЫБРАТЬ 1" } ] }
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("skd", "compile", json.toString(), output.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown DataSet type").contains("Bogus");
        assertThat(output).doesNotExist();
    }

    @Test
    @DisplayName("integr-mxl compile rejects non-positive column widths before output")
    void mxlCompileNonPositiveColumnWidths_rejectsBeforeOutput() throws Exception {
        Path json = tempDir.resolve("bad-mxl.json");
        Path output = tempDir.resolve("Template.xml");
        Files.writeString(json, """
                {
                  "columns": 2,
                  "columnWidths": { "1": 0, "2": -5 },
                  "areas": [{ "name": "A", "rows": [{ "cells": [{ "col": 1, "text": "X" }] }] }]
                }
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("mxl", "compile", json.toString(), output.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("column width").contains("> 0");
        assertThat(output).doesNotExist();
    }

    @Test
    @DisplayName("integr-meta compile requires Configuration.xml ChildObjects registration")
    void metaCompileWithoutConfigurationChildObjects_failsBeforeObjectOutput() throws Exception {
        Files.writeString(tempDir.resolve("Configuration.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Configuration uuid="11111111-1111-1111-1111-111111111111">
                    <Properties><Name>AuditCfg</Name></Properties>
                  </Configuration>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        Path json = tempDir.resolve("catalog.json");
        Files.writeString(json, """
                { "type": "Catalog", "name": "AuditCatalog" }
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("meta", "compile", json.toString(), tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("ChildObjects");
        assertThat(tempDir.resolve("Catalogs/AuditCatalog.xml")).doesNotExist();
    }

    @Test
    @DisplayName("integr-meta edit rejects unknown operation target")
    void metaEditUnknownOperationTarget_fails() throws Exception {
        Path catalog = tempDir.resolve("Catalogs/AuditCatalog.xml");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Catalog uuid="22222222-2222-2222-2222-222222222222">
                        <Properties><Name>AuditCatalog</Name></Properties>
                        <ChildObjects/>
                    </Catalog>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        String before = Files.readString(catalog, StandardCharsets.UTF_8);

        ProcessResult result = runMain("meta", "edit", catalog.toString(),
                "--op", "add-notARealTarget", "--value", "Foo");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown --op target").contains("notARealTarget");
        assertThat(Files.readString(catalog, StandardCharsets.UTF_8)).isEqualTo(before);
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

        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
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

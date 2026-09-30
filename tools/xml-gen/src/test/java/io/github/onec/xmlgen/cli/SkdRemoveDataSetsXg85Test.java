package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [11.07.2026 06:30:00]
class SkdRemoveDataSetsXg85Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void removesExactRootDataSetAndPreservesDesignerBytesAndMode() throws Exception {
        Path schema = writeSchema("exact.xml", baseSchema(""));
        byte[] before = Files.readAllBytes(schema);
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(schema, mode);
        Path payload = writePayload("exact.json", """
                {"dataSets":[{"name":"Удаляемый","type":"DataSetObject"}]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        byte[] after = Files.readAllBytes(schema);
        assertThat(after).startsWith(BOM).isNotEqualTo(before);
        assertThat(read(schema)).isEqualTo(baseSchema("").replace(targetDataSet(), ""));
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);
        assertDesignerLineEndings(read(schema));
        ProcessResult validate = runMain("validate", "--type", "skd", schema.toString());
        assertThat(validate.exitCode()).as(validate.output()).isZero();
    }

    @Test
    void rejectsMissingAmbiguousTypeMismatchAndInvalidSelectorWithoutMutation() throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase(baseSchema(""),
                        "{\"dataSets\":[{\"name\":\"Нет\",\"type\":\"DataSetObject\"}]}",
                        "not found"),
                new InvalidCase(baseSchema(targetDataSet()),
                        "{\"dataSets\":[{\"name\":\"Удаляемый\"}]}",
                        "exactly one"),
                new InvalidCase(baseSchema(""),
                        "{\"dataSets\":[{\"name\":\"Удаляемый\",\"type\":\"DataSetQuery\"}]}",
                        "type"),
                new InvalidCase(baseSchema(""),
                        "{\"dataSets\":[{\"type\":\"DataSetObject\"}]}",
                        "requires name"),
                new InvalidCase(baseSchema(""),
                        "{\"dataSets\":[{\"name\":\"Удаляемый\"},{\"name\":\"Удаляемый\"}]}",
                        "Duplicate")
        );
        for (int index = 0; index < cases.size(); index++) {
            InvalidCase invalid = cases.get(index);
            Path schema = writeSchema("invalid-" + index + ".xml", invalid.xml());
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("invalid-" + index + ".json", invalid.payload());

            ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase(invalid.message());
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void rejectsDanglingReferencesAcrossLinksSettingsTemplatesAndExpressions() throws Exception {
        List<String> references = List.of(
                """
                \t<dataSetLink>\r
                \t\t<sourceDataSet>Соседний</sourceDataSet>\r
                \t\t<destinationDataSet>Удаляемый</destinationDataSet>\r
                \t\t<sourceExpression>Ключ</sourceExpression>\r
                \t\t<destinationExpression>Ключ</destinationExpression>\r
                \t</dataSetLink>\r
                """,
                """
                \t<groupTemplate>\r
                \t\t<groupName>Удаляемый</groupName>\r
                \t\t<templateType>Header</templateType>\r
                \t\t<template>СоседняяОбласть</template>\r
                \t</groupTemplate>\r
                """,
                """
                \t<settingsVariant><name>Основной</name><settings>\r
                \t\t<dcsset:item xsi:type="dcsset:StructureItemGroup">\r
                \t\t\t<dcsset:name>Соседняя</dcsset:name>\r
                \t\t\t<dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemField">\r
                \t\t\t\t<dcsset:field>Удаляемый.Ключ</dcsset:field>\r
                \t\t\t</dcsset:item></dcsset:selection>\r
                \t\t</dcsset:item>\r
                \t</settings></settingsVariant>\r
                """,
                """
                \t<calculatedField>\r
                \t\t<dataPath>Итог</dataPath>\r
                \t\t<expression>Удаляемый.Ключ</expression>\r
                \t</calculatedField>\r
                """
        );
        for (int index = 0; index < references.size(); index++) {
            Path schema = writeSchema("reference-" + index + ".xml", baseSchema(references.get(index)));
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("reference-" + index + ".json",
                    "{\"dataSets\":[{\"name\":\"Удаляемый\"}]}");

            ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).contains("Dangling reference").contains("Удаляемый");
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void removesReferencesAndDataSetInOnePreflightedBatch() throws Exception {
        String link = """
                \t<dataSetLink>\r
                \t\t<sourceDataSet>Соседний</sourceDataSet>\r
                \t\t<destinationDataSet>Удаляемый</destinationDataSet>\r
                \t\t<sourceExpression>Ключ</sourceExpression>\r
                \t\t<destinationExpression>Ключ</destinationExpression>\r
                \t</dataSetLink>\r
                """;
        Path schema = writeSchema("batch-success.xml", baseSchema(link));
        Path payload = writePayload("batch-success.json", """
                {"dataSets":[{"name":"Удаляемый","type":"DataSetObject"}],
                 "dataSetLinks":[{"sourceDataSet":"Соседний","destinationDataSet":"Удаляемый",
                   "sourceExpression":"Ключ","destinationExpression":"Ключ"}]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Removed 2 SKD component");
        assertThat(read(schema)).doesNotContain("Удаляемый").doesNotContain("<dataSetLink>");
        ProcessResult validate = runMain("validate", "--type", "skd", schema.toString());
        assertThat(validate.exitCode()).as(validate.output()).isZero();
    }

    @Test
    void invalidMixedBatchRollsBackEveryResolvedTarget() throws Exception {
        Path schema = writeSchema("batch-rollback.xml", baseSchema(""));
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("batch-rollback.json", """
                {"dataSets":[{"name":"Удаляемый"}],
                 "structureItems":[{"variant":"Нет","path":["Нет"]}]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    @Test
    void dryRunStrictRepeatAndExplicitNoopAreByteStable() throws Exception {
        Path schema = writeSchema("repeat.xml", baseSchema(""));
        Path payload = writePayload("repeat.json",
                "{\"dataSets\":[{\"name\":\"Удаляемый\",\"type\":\"DataSetObject\"}]}");
        byte[] original = Files.readAllBytes(schema);

        ProcessResult dryRun = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]").contains("1 SKD component");
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        assertThat(runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString()).exitCode()).isZero();
        byte[] once = Files.readAllBytes(schema);

        ProcessResult strict = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());
        assertThat(strict.exitCode()).as(strict.output()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);

        Files.writeString(payload,
                "{\"ifAbsent\":\"noop\",\"dataSets\":[{\"name\":\"Удаляемый\",\"type\":\"DataSetObject\"}]}",
                StandardCharsets.UTF_8);
        ProcessResult noop = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    private String baseSchema(String extra) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Соседний</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Ключ</dataPath><field>Ключ</field></field>
                \t\t<objectName>Соседний</objectName>
                \t</dataSet>
                """.replace("\n", "\r\n") + targetDataSet() + extra
                + "\t<settingsVariant><name>Проверка</name><settings/></settingsVariant>\r\n"
                + "</DataCompositionSchema>\r\n";
    }

    private String targetDataSet() {
        return """
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Удаляемый</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Ключ</dataPath><field>Ключ</field></field>
                \t\t<objectName>Удаляемый</objectName>
                \t</dataSet>
                """.replace("\n", "\r\n");
    }

    private Path writeSchema(String name, String xml) throws Exception {
        Path path = tempDir.resolve(name);
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
        return path;
    }

    private Path writePayload(String name, String json) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private String read(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static void assertDesignerLineEndings(String value) {
        assertThat(value.replace("\r\n", "")).doesNotContain("\n");
    }

    private void assertNoTempFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            assertThat(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"))).isTrue();
        }
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE).start();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record InvalidCase(String xml, String payload, String message) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String output() { return stdout + stderr; }
    }
}
//--agent TASK-174

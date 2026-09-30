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

//++agent TASK-174 [11.07.2026 07:15:00]
class SkdRemoveDataSetFieldsXg86Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void h7RemovesOnlyThreeFieldsFromExactRootDataSet() throws Exception {
        String original = schema("");
        Path schema = writeSchema("h7.xml", original);
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(schema, mode);
        Path payload = writePayload("h7.json", """
                {"dataSetFields":[
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","dataPath":"Аккаунт"},
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","dataPath":"Портфель"},
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","dataPath":"Монета"}
                ]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Removed 3 SKD component");
        String after = read(schema);
        String expectedTarget = targetDataSet("")
                .replace(field("Аккаунт"), "")
                .replace(field("Портфель"), "")
                .replace(field("Монета"), "");
        String expected = original.replace(targetDataSet(""), expectedTarget);
        assertThat(after).isEqualTo(expected);
        assertThat(after).contains("<name>ДвиженияКапитала</name>")
                .contains("<objectName>ДвиженияКапитала</objectName>")
                .contains(field("Дата").stripTrailing())
                .contains(field("ВидДвижения").stripTrailing())
                .contains("<dcsset:field>Аккаунт</dcsset:field>")
                .contains("<dcsset:field>Портфель</dcsset:field>")
                .contains("<dcsset:field>Монета</dcsset:field>")
                .contains("<resource><field>Монета</field></resource>")
                .contains("<sourceExpression>Аккаунт</sourceExpression>")
                .contains("<template>Макет1</template>");
        assertThat(Files.readAllBytes(schema)).startsWith(BOM);
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);
        assertThat(after.replace("\r\n", "")).doesNotContain("\n");
        assertThat(runMain("validate", "--type", "skd", schema.toString()).exitCode()).isZero();
    }

    @Test
    void rejectsMissingAmbiguousTypeMismatchAndDuplicateSelectorAtomically() throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase(schema(""),
                        "{\"dataSetFields\":[{\"dataSet\":\"Нет\",\"dataPath\":\"Аккаунт\"}]}",
                        "Root dataSet not found"),
                new InvalidCase(schema(""),
                        "{\"dataSetFields\":[{\"dataSet\":\"ДвиженияКапитала\",\"dataPath\":\"Нет\"}]}",
                        "field declaration not found"),
                new InvalidCase(schema(""),
                        "{\"dataSetFields\":[{\"dataSet\":\"ДвиженияКапитала\",\"type\":\"DataSetQuery\",\"dataPath\":\"Аккаунт\"}]}",
                        "type mismatch"),
                new InvalidCase(schema(duplicateField()),
                        "{\"dataSetFields\":[{\"dataSet\":\"ДвиженияКапитала\",\"dataPath\":\"Аккаунт\"}]}",
                        "exactly one field declaration"),
                new InvalidCase(schema(""),
                        "{\"dataSetFields\":[{\"dataSet\":\"ДвиженияКапитала\",\"dataPath\":\"Аккаунт\"},"
                                + "{\"dataSet\":\"ДвиженияКапитала\",\"dataPath\":\"Аккаунт\"}]}",
                        "Duplicate dataSetFields selector"),
                new InvalidCase(schema(""),
                        "{\"dataSetFields\":[{\"dataSet\":\"ДвиженияКапитала\"}]}",
                        "requires dataSet and dataPath")
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
    void invalidMixedBatchRollsBackResolvedFieldAndNeverFollowsGlobalReferences() throws Exception {
        Path schema = writeSchema("mixed.xml", schema(""));
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("mixed.json", """
                {"dataSetFields":[
                  {"dataSet":"ДвиженияКапитала","dataPath":"Аккаунт"},
                  {"dataSet":"ДвиженияКапитала","dataPath":"Нет"}
                ]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    @Test
    void missingRootDataSetHonorsNoopButStrictAndMixedBatchesFailAtomically() throws Exception {
        Path noopSchema = writeSchema("missing-root-noop.xml", schema(""));
        byte[] noopBefore = Files.readAllBytes(noopSchema);
        Path noopPayload = writePayload("missing-root-noop.json", """
                {"ifAbsent":"noop","dataSetFields":[
                  {"dataSet":"Отсутствующий","type":"DataSetObject","dataPath":"Поле"}
                ]}
                """);

        ProcessResult noop = runMain("skd", "remove-components", noopSchema.toString(),
                "--json", noopPayload.toString());

        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(noopSchema)).isEqualTo(noopBefore);

        Path strictSchema = writeSchema("missing-root-strict.xml", schema(""));
        byte[] strictBefore = Files.readAllBytes(strictSchema);
        Path strictPayload = writePayload("missing-root-strict.json", """
                {"ifAbsent":"fail","dataSetFields":[
                  {"dataSet":"Отсутствующий","dataPath":"Поле"}
                ]}
                """);
        ProcessResult strict = runMain("skd", "remove-components", strictSchema.toString(),
                "--json", strictPayload.toString());
        assertThat(strict.exitCode()).as(strict.output()).isNotZero();
        assertThat(strict.output()).contains("Root dataSet not found");
        assertThat(Files.readAllBytes(strictSchema)).isEqualTo(strictBefore);

        Path mixedSchema = writeSchema("missing-root-mixed.xml", schema(""));
        byte[] mixedBefore = Files.readAllBytes(mixedSchema);
        Path mixedPayload = writePayload("missing-root-mixed.json", """
                {"ifAbsent":"fail","dataSetFields":[
                  {"dataSet":"ДвиженияКапитала","dataPath":"Аккаунт"},
                  {"dataSet":"Отсутствующий","dataPath":"Поле"}
                ]}
                """);
        ProcessResult mixed = runMain("skd", "remove-components", mixedSchema.toString(),
                "--json", mixedPayload.toString());
        assertThat(mixed.exitCode()).as(mixed.output()).isNotZero();
        assertThat(mixed.output()).contains("Root dataSet not found");
        assertThat(Files.readAllBytes(mixedSchema)).isEqualTo(mixedBefore);
        assertNoTempFiles();
    }

    @Test
    void dryRunStrictRepeatAndExplicitNoopAreByteStable() throws Exception {
        Path schema = writeSchema("repeat.xml", schema(""));
        Path payload = writePayload("repeat.json", """
                {"dataSetFields":[
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","dataPath":"Аккаунт"}
                ]}
                """);
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

        Files.writeString(payload, """
                {"ifAbsent":"noop","dataSetFields":[
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","dataPath":"Аккаунт"}
                ]}
                """, StandardCharsets.UTF_8);
        ProcessResult noop = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    private String schema(String extraTargetField) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:dcsat="http://v8.1c.ru/8.1/data-composition-system/area-template"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>СтарыйНабор</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Аккаунт</dataPath><field>Аккаунт</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Портфель</dataPath><field>Портфель</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Монета</dataPath><field>Монета</field></field>
                \t\t<objectName>СтарыйНабор</objectName>
                \t</dataSet>
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>ДвиженияКапитала</name>
                """.replace("\n", "\r\n")
                + targetDataSet(extraTargetField)
                + """
                \t<dataSetLink>
                \t\t<sourceDataSet>СтарыйНабор</sourceDataSet>
                \t\t<destinationDataSet>ДвиженияКапитала</destinationDataSet>
                \t\t<sourceExpression>Аккаунт</sourceExpression>
                \t\t<destinationExpression>Дата</destinationExpression>
                \t</dataSetLink>
                \t<resource><field>Монета</field></resource>
                \t<template><name>Макет1</name><template xsi:type="dcsat:AreaTemplate"/></template>
                \t<groupTemplate><groupName>Группа</groupName><templateType>Header</templateType><template>Макет1</template></groupTemplate>
                \t<settingsVariant><name>Основной</name><settings>
                \t\t<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>СтараяГруппа</dcsset:name>
                \t\t\t<dcsset:selection>
                \t\t\t\t<dcsset:item xsi:type="dcsset:SelectedItemField"><dcsset:field>Аккаунт</dcsset:field></dcsset:item>
                \t\t\t\t<dcsset:item xsi:type="dcsset:SelectedItemField"><dcsset:field>Портфель</dcsset:field></dcsset:item>
                \t\t\t\t<dcsset:item xsi:type="dcsset:SelectedItemField"><dcsset:field>Монета</dcsset:field></dcsset:item>
                \t\t\t</dcsset:selection>
                \t\t</dcsset:item>
                \t</settings></settingsVariant>
                </DataCompositionSchema>
                """.replace("\n", "\r\n");
    }

    private static String field(String name) {
        return "\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>" + name
                + "</dataPath><field>" + name + "</field></field>\r\n";
    }

    private static String targetDataSet(String extraTargetField) {
        return field("Дата") + field("ВидДвижения") + field("Аккаунт") + field("Портфель")
                + field("Монета") + extraTargetField
                + "\t\t<objectName>ДвиженияКапитала</objectName>\r\n\t</dataSet>\r\n";
    }

    private static String duplicateField() {
        return field("Аккаунт");
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

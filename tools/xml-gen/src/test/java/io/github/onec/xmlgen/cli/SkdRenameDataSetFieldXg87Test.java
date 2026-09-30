package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [11.07.2026 09:30:00]
class SkdRenameDataSetFieldXg87Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String IMMUTABLE_FIXTURE_SHA =
            "0a79e139d6866382722ac5ea5033f2b71ac34ebedb86afefe92817048a15aa2e";

    @TempDir
    Path tempDir;

    @Test
    void task204RenamesOnlyThreeDirectPairsAndPreservesEverySiblingByte() throws Exception {
        String original = schema("");
        Path schema = writeSchema("task204.xml", original);
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(schema, mode);
        Path payload = task204Payload("fail");

        ProcessResult result = runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Renamed 3 SKD dataSet field");
        String expected = renameTask204TargetPairs(original);
        assertThat(read(schema)).isEqualTo(expected);
        assertThat(read(schema)).contains("<dcsset:field>Аккаунт</dcsset:field>")
                .contains("<sourceExpression>Аккаунт</sourceExpression>")
                .contains("<resource><field>Монета</field></resource>")
                .contains("<expression>Аккаунт + Портфель + Монета</expression>")
                .contains("<name>Соседний</name>")
                .contains(pair("Аккаунт", "Аккаунт"));
        assertThat(Files.readAllBytes(schema)).startsWith(BOM);
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);
        assertThat(read(schema).replace("\r\n", "")).doesNotContain("\n");
        assertThat(runMain("validate", "--type", "skd", schema.toString()).exitCode()).isZero();
    }

    @Test
    void exactImmutableBaselineDiffContainsOnlyTheSixTask204TextReplacements() throws Exception {
        byte[] productionBytes;
        try (var fixture = SkdRenameDataSetFieldXg87Test.class.getResourceAsStream(
                "/skd/xg87-task204-baseline.xml")) {
            assertThat(fixture).isNotNull();
            productionBytes = fixture.readAllBytes();
        }
        assertThat(sha256(productionBytes)).isEqualTo(IMMUTABLE_FIXTURE_SHA);
        Path isolated = tempDir.resolve("task204-production-copy.xml");
        Files.write(isolated, productionBytes);
        Path payload = task204Payload("fail");

        ProcessResult result = runMain("skd", "rename-dataset-field", isolated.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        String original = decode(productionBytes);
        String expected = renameTask204TargetPairs(original);
        assertThat(read(isolated)).isEqualTo(expected);
        assertThat(sha256(productionBytes)).isEqualTo(IMMUTABLE_FIXTURE_SHA);
    }

    @Test
    void rejectsMissingAmbiguousTypeMismatchOldFieldMismatchAndTargetCollisionAtomically() throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase(schema(""), fieldPayload("Нет", null, "Аккаунт", "Аккаунт", "Новый", "Новый"),
                        "Root dataSet not found"),
                new InvalidCase(schema(duplicateField()), fieldPayload("ДвиженияКапитала", null,
                        "Аккаунт", "Аккаунт", "Новый", "Новый"), "exactly one field declaration"),
                new InvalidCase(schema(""), fieldPayload("ДвиженияКапитала", "DataSetQuery",
                        "Аккаунт", "Аккаунт", "Новый", "Новый"), "type mismatch"),
                new InvalidCase(schema(""), fieldPayload("ДвиженияКапитала", null,
                        "Аккаунт", "ДругойPhysical", "Новый", "Новый"), "old field mismatch"),
                new InvalidCase(schema(""), fieldPayload("ДвиженияКапитала", null,
                        "Аккаунт", "Аккаунт", "Дата", "Дата"), "target dataPath collision"),
                new InvalidCase(schema(""), "{\"fields\":[{\"dataSet\":\"ДвиженияКапитала\","
                        + "\"oldDataPath\":\"Аккаунт\",\"dataPath\":\"Новый\"}]}", "requires")
        );
        for (int index = 0; index < cases.size(); index++) {
            InvalidCase invalid = cases.get(index);
            Path schema = writeSchema("invalid-" + index + ".xml", invalid.xml());
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("invalid-" + index + ".json", invalid.payload());

            ProcessResult result = runMain("skd", "rename-dataset-field", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase(invalid.message());
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void invalidMixedBatchAndDuplicateTargetsRollBackWholeOperation() throws Exception {
        List<String> payloads = List.of(
                """
                {"fields":[
                  {"dataSet":"ДвиженияКапитала","oldDataPath":"Аккаунт","oldField":"Аккаунт","dataPath":"Новый1","field":"Новый1"},
                  {"dataSet":"ДвиженияКапитала","oldDataPath":"Нет","oldField":"Нет","dataPath":"Новый2","field":"Новый2"}
                ]}
                """,
                """
                {"fields":[
                  {"dataSet":"ДвиженияКапитала","oldDataPath":"Аккаунт","oldField":"Аккаунт","dataPath":"Общий","field":"Общий1"},
                  {"dataSet":"ДвиженияКапитала","oldDataPath":"Портфель","oldField":"Портфель","dataPath":"Общий","field":"Общий2"}
                ]}
                """);
        for (int index = 0; index < payloads.size(); index++) {
            Path schema = writeSchema("mixed-" + index + ".xml", schema(""));
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("mixed-" + index + ".json", payloads.get(index));

            ProcessResult result = runMain("skd", "rename-dataset-field", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void dryRunStrictMissingNoopAndSuccessfulRepeatAreByteStable() throws Exception {
        Path schema = writeSchema("repeat.xml", schema(""));
        Path payload = writePayload("repeat.json", fieldPayload("ДвиженияКапитала", "DataSetObject",
                "Аккаунт", "Аккаунт", "КапиталАккаунт", "КапиталАккаунт"));
        byte[] original = Files.readAllBytes(schema);

        ProcessResult dryRun = runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]").contains("1 SKD dataSet field");
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        assertThat(runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", payload.toString()).exitCode()).isZero();
        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);

        Path strictMissing = writePayload("strict-missing.json", fieldPayload("ДвиженияКапитала", null,
                "Нет", "Нет", "Новый", "Новый"));
        ProcessResult strict = runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", strictMissing.toString());
        assertThat(strict.exitCode()).as(strict.output()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);

        Path noopMissing = writePayload("noop-missing.json", "{\"ifAbsent\":\"noop\",\"fields\":[{"
                + "\"dataSet\":\"ДвиженияКапитала\",\"oldDataPath\":\"Нет\",\"oldField\":\"Нет\","
                + "\"dataPath\":\"Новый\",\"field\":\"Новый\"}]}");
        ProcessResult noop = runMain("skd", "rename-dataset-field", schema.toString(),
                "--json", noopMissing.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    private Path task204Payload(String ifAbsent) throws Exception {
        return writePayload("task204-" + ifAbsent + ".json", """
                {"ifAbsent":"%s","fields":[
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","oldDataPath":"Аккаунт","oldField":"Аккаунт","dataPath":"КапиталАккаунт","field":"КапиталАккаунт"},
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","oldDataPath":"Портфель","oldField":"Портфель","dataPath":"КапиталПортфель","field":"КапиталПортфель"},
                  {"dataSet":"ДвиженияКапитала","type":"DataSetObject","oldDataPath":"Монета","oldField":"Монета","dataPath":"КапиталМонета","field":"КапиталМонета"}
                ]}
                """.formatted(ifAbsent));
    }

    private static String fieldPayload(String dataSet, String type, String oldDataPath, String oldField,
                                       String dataPath, String field) {
        String typeJson = type == null ? "" : ",\"type\":\"" + type + "\"";
        return "{\"fields\":[{\"dataSet\":\"" + dataSet + "\"" + typeJson
                + ",\"oldDataPath\":\"" + oldDataPath + "\",\"oldField\":\"" + oldField
                + "\",\"dataPath\":\"" + dataPath + "\",\"field\":\"" + field + "\"}]}";
    }

    private String schema(String extraTargetField) {
        return ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Соседний</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Аккаунт</dataPath><field>Аккаунт</field></field>
                \t\t<objectName>Соседний</objectName>
                \t</dataSet>
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>ДвиженияКапитала</name>
                """.replace("\n", "\r\n") + targetDataSet(extraTargetField)
                + """
                \t<dataSetLink><sourceDataSet>Соседний</sourceDataSet><destinationDataSet>ДвиженияКапитала</destinationDataSet><sourceExpression>Аккаунт</sourceExpression><destinationExpression>Дата</destinationExpression></dataSetLink>
                \t<resource><field>Монета</field></resource>
                \t<calculatedField><dataPath>Тест</dataPath><expression>Аккаунт + Портфель + Монета</expression></calculatedField>
                \t<settingsVariant><name>Основной</name><settings><dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Группа</dcsset:name><dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemField"><dcsset:field>Аккаунт</dcsset:field></dcsset:item></dcsset:selection></dcsset:item></settings></settingsVariant>
                </DataCompositionSchema>
                """.replace("\n", "\r\n"));
    }

    private static String targetDataSet(String extraTargetField) {
        return pair("Дата", "Дата") + pair("Аккаунт", "Аккаунт") + pair("Портфель", "Портфель")
                + pair("Монета", "Монета") + pair("Остаток", "PhysicalОстаток") + extraTargetField
                + "\t\t<objectName>ДвиженияКапитала</objectName>\r\n\t</dataSet>\r\n";
    }

    private static String pair(String dataPath, String field) {
        return "\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>" + dataPath
                + "</dataPath><field>" + field + "</field></field>\r\n";
    }

    private static String duplicateField() {
        return pair("Аккаунт", "Аккаунт");
    }

    private static String renameTask204TargetPairs(String original) {
        int name = original.indexOf("<name>ДвиженияКапитала</name>");
        assertThat(name).isGreaterThanOrEqualTo(0);
        int start = original.lastIndexOf("<dataSet", name);
        int end = original.indexOf("</dataSet>", name) + "</dataSet>".length();
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        String target = original.substring(start, end)
                .replace("<dataPath>Аккаунт</dataPath><field>Аккаунт</field>",
                        "<dataPath>КапиталАккаунт</dataPath><field>КапиталАккаунт</field>")
                .replace("<dataPath>Портфель</dataPath><field>Портфель</field>",
                        "<dataPath>КапиталПортфель</dataPath><field>КапиталПортфель</field>")
                .replace("<dataPath>Монета</dataPath><field>Монета</field>",
                        "<dataPath>КапиталМонета</dataPath><field>КапиталМонета</field>");
        return original.substring(0, start) + target + original.substring(end);
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
        return decode(Files.readAllBytes(path));
    }

    private static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2] ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static Path locateProductionTemplate() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !"repos".equals(current.getFileName() == null ? "" : current.getFileName().toString())) {
            current = current.getParent();
        }
        if (current == null) return null;
        return current.resolve("1C Projects/GBIG PAM/src/xml/Reports/биг_РезультатыУправления/"
                + "Templates/ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml");
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

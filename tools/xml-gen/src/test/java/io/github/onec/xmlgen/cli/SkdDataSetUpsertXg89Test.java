package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [11.07.2026 21:57:04]
class SkdDataSetUpsertXg89Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String BASELINE_SHA =
            "cc50c244f0cac6d40e70b142671b6f400e4d29c68c8d669fb49fdb30869fd115";
    private static final String DONOR_SHA =
            "7ca5d505671976186ffb33370f4075cc5f5ca025a19c180a2844fca0e0ec20b8";
    private static final String HISTORY = "ИсторияОпераций";

    @TempDir
    Path tempDir;

    @Test
    void task204RestoresExactHistoryDataSetFromDonorAndChangesNoSiblingByte() throws Exception {
        byte[] baselineBytes = fixture("/skd/xg89-task204-cc332cd0.xml.gz");
        byte[] donorBytes = fixture("/skd/xg89-task204-6f35421e.xml.gz");
        assertThat(sha256(baselineBytes)).isEqualTo(BASELINE_SHA);
        assertThat(sha256(donorBytes)).isEqualTo(DONOR_SHA);

        Path schema = tempDir.resolve("Template.xml");
        Path donor = tempDir.resolve("donor.xml");
        Files.write(schema, baselineBytes);
        Files.write(donor, donorBytes);
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(schema, mode);
        Path payload = writePayload("task204.json", """
                {
                  "mode":"upsert",
                  "sourceFile":"donor.xml",
                  "dataSets":[{"name":"ИсторияОпераций","type":"DataSetQuery"}]
                }
                """);

        ProcessResult dryRun = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]").contains("1 SKD dataSet");
        assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);

        ProcessResult first = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.output()).isZero();
        assertThat(first.output()).contains("Upserted 1 SKD dataSet");

        String baseline = decode(baselineBytes);
        String donorXml = decode(donorBytes);
        String expectedDataSet = rootDataSet(donorXml, HISTORY);
        String expected = insertAfterLastRootDataSet(baseline, expectedDataSet);
        String after = decode(Files.readAllBytes(schema));
        assertThat(after).isEqualTo(expected);
        assertThat(rootDataSet(after, HISTORY)).isEqualTo(expectedDataSet);
        assertThat(count(expectedDataSet, "<field xsi:type=\"DataSetFieldField\">"))
                .isEqualTo(10);
        assertThat(directText(rootDataSet(after, HISTORY), "dataSource"))
                .isEqualTo("ИсточникДанных1");
        assertThat(directText(rootDataSet(after, HISTORY), "query"))
                .isEqualTo(directText(expectedDataSet, "query"));
        assertThat(after).contains("<dataPath>Операци</dataPath>")
                .contains("РегистрНакопления.биг_ПрибылиИУбытки")
                .contains("Самовольный_Убыток");
        assertThat(Files.readAllBytes(schema)).startsWith(BOM);
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);
        assertThat(withoutQueryText(expectedDataSet).replace("\r\n", ""))
                .doesNotContain("\n");
        assertThat(runMain("validate", "--type", "skd", schema.toString()).exitCode()).isZero();

        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void supportsExactInlineDataSetObjectReplaceAndUpsert() throws Exception {
        String original = smallSchema();
        Path schema = writeSchema("inline.xml", original);
        String replacement = objectDataSet("Источник", "PhysicalИсточник", "Ключ", "ДопПоле");
        Path replace = writePayload("replace.json", payload("replace", "fail",
                List.of(dataSet("Источник", "DataSetObject", replacement))));

        ProcessResult replaced = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", replace.toString());

        assertThat(replaced.exitCode()).as(replaced.output()).isZero();
        String expected = replaceRootDataSet(original, "Источник", replacement);
        assertThat(read(schema)).isEqualTo(expected);
        assertThat(rootDataSet(read(schema), "Назначение"))
                .isEqualTo(rootDataSet(original, "Назначение"));

        String addition = objectDataSet("НовыйНабор", "PhysicalНовый", "Ключ");
        Path upsert = writePayload("upsert.json", payload("upsert", "fail",
                List.of(dataSet("НовыйНабор", "DataSetObject", addition))));
        ProcessResult added = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", upsert.toString());
        assertThat(added.exitCode()).as(added.output()).isZero();
        assertThat(read(schema)).isEqualTo(insertAfterLastRootDataSet(expected, addition));

        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", upsert.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void rejectsIdentityCollisionsMissingSourcesAndInvalidMixedBatchAtomically() throws Exception {
        String queryCollision = queryDataSet("Источник", "ИсточникДанных1", "Ключ");
        String missingTarget = objectDataSet("НетНабора", "НетНабора", "Ключ");
        String mismatchedIdentity = objectDataSet("ДругоеИмя", "ДругоеИмя", "Ключ");
        List<InvalidCase> cases = List.of(
                new InvalidCase(payload("upsert", "fail",
                        List.of(dataSet("Источник", "DataSetQuery", queryCollision))),
                        "name collision"),
                new InvalidCase(payload("replace", "fail",
                        List.of(dataSet("НетНабора", "DataSetObject", missingTarget))),
                        "target dataSet not found"),
                new InvalidCase(payload("upsert", "fail",
                        List.of(dataSet("ОжидаемоеИмя", "DataSetObject", mismatchedIdentity))),
                        "identity mismatch"),
                new InvalidCase(payload("upsert", "fail", List.of(
                        dataSet("НовыйНабор", "DataSetObject",
                                objectDataSet("НовыйНабор", "НовыйНабор", "Ключ")),
                        dataSet("НовыйНабор", "DataSetObject",
                                objectDataSet("НовыйНабор", "НовыйНабор", "Ключ")))),
                        "duplicate dataSet identity"),
                new InvalidCase("""
                        {"mode":"upsert","sourceFile":"нет-файла.xml",
                         "dataSets":[{"name":"НовыйНабор","type":"DataSetObject"}]}
                        """, "source file not found")
        );

        for (int index = 0; index < cases.size(); index++) {
            Path schema = writeSchema("invalid-" + index + ".xml", smallSchema());
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("invalid-" + index + ".json", cases.get(index).payload());

            ProcessResult result = runMain("skd", "upsert-dataset", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase(cases.get(index).message());
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }

        Path noopSchema = writeSchema("missing-noop.xml", smallSchema());
        byte[] before = Files.readAllBytes(noopSchema);
        Path noopPayload = writePayload("missing-noop.json", payload("replace", "noop",
                List.of(dataSet("НетНабора", "DataSetObject", missingTarget))));
        ProcessResult noop = runMain("skd", "upsert-dataset", noopSchema.toString(),
                "--json", noopPayload.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(noopSchema)).isEqualTo(before);
    }

    @Test
    void referentialPreflightRollsBackWholeBatchWhenReplacementBreaksLink() throws Exception {
        Path schema = writeSchema("references.xml", smallSchema());
        byte[] before = Files.readAllBytes(schema);
        String validAddition = objectDataSet("НовыйНабор", "НовыйНабор", "Ключ");
        String brokenSource = objectDataSet("Источник", "Источник", "ДругоеПоле");
        Path payload = writePayload("references.json", payload("upsert", "fail", List.of(
                dataSet("НовыйНабор", "DataSetObject", validAddition),
                dataSet("Источник", "DataSetObject", brokenSource))));

        ProcessResult result = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).containsIgnoringCase("dataSetLink source field")
                .contains("Ключ");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();

        String unknownDataSource = queryDataSet("НовыйЗапрос", "НетИсточника", "Ключ");
        Path sourcePayload = writePayload("unknown-source.json", payload("upsert", "fail",
                List.of(dataSet("НовыйЗапрос", "DataSetQuery", unknownDataSource))));
        ProcessResult sourceResult = runMain("skd", "upsert-dataset", schema.toString(),
                "--json", sourcePayload.toString());
        assertThat(sourceResult.exitCode()).as(sourceResult.output()).isNotZero();
        assertThat(sourceResult.output()).containsIgnoringCase("unknown dataSource")
                .contains("НетИсточника");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
    }

    private byte[] fixture(String resource) throws Exception {
        try (InputStream raw = SkdDataSetUpsertXg89Test.class.getResourceAsStream(resource)) {
            assertThat(raw).as(resource).isNotNull();
            try (GZIPInputStream gzip = new GZIPInputStream(raw)) {
                return gzip.readAllBytes();
            }
        }
    }

    private Path writeSchema(String name, String xml) throws Exception {
        Path path = tempDir.resolve(name);
        byte[] content = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(content, 0, bytes, BOM.length, content.length);
        Files.write(path, bytes);
        return path;
    }

    private Path writePayload(String name, String json) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private static Map<String, Object> dataSet(String name, String type, String xml) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("type", type);
        result.put("xml", xml);
        return result;
    }

    private static String payload(String mode, String ifAbsent,
                                  List<Map<String, Object>> dataSets) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("ifAbsent", ifAbsent);
        result.put("dataSets", dataSets);
        return new ObjectMapper().writeValueAsString(result);
    }

    private static String smallSchema() {
        String header = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\"\n"
                + "  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<dataSource>\n"
                + "\t\t<name>ИсточникДанных1</name>\n"
                + "\t\t<dataSourceType>Local</dataSourceType>\n"
                + "\t</dataSource>\n";
        String footer = "\t<dataSetLink>\n"
                + "\t\t<sourceDataSet>Источник</sourceDataSet>\n"
                + "\t\t<destinationDataSet>Назначение</destinationDataSet>\n"
                + "\t\t<sourceExpression>Ключ</sourceExpression>\n"
                + "\t\t<destinationExpression>Ключ</destinationExpression>\n"
                + "\t</dataSetLink>\n"
                + "</DataCompositionSchema>\n";
        return header.replace("\n", "\r\n")
                + objectDataSet("Источник", "Источник", "Ключ")
                + objectDataSet("Назначение", "Назначение", "Ключ")
                + footer.replace("\n", "\r\n");
    }

    private static String objectDataSet(String name, String objectName, String... fields) {
        StringBuilder result = new StringBuilder();
        result.append("\t<dataSet xsi:type=\"DataSetObject\">\r\n")
                .append("\t\t<name>").append(name).append("</name>\r\n");
        for (String field : fields) {
            result.append("\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>")
                    .append(field).append("</dataPath><field>").append(field)
                    .append("</field></field>\r\n");
        }
        return result.append("\t\t<objectName>").append(objectName).append("</objectName>\r\n")
                .append("\t</dataSet>\r\n").toString();
    }

    private static String queryDataSet(String name, String dataSource, String... fields) {
        StringBuilder result = new StringBuilder();
        result.append("\t<dataSet xsi:type=\"DataSetQuery\">\r\n")
                .append("\t\t<name>").append(name).append("</name>\r\n");
        for (String field : fields) {
            result.append("\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>")
                    .append(field).append("</dataPath><field>").append(field)
                    .append("</field></field>\r\n");
        }
        return result.append("\t\t<dataSource>").append(dataSource).append("</dataSource>\r\n")
                .append("\t\t<query>ВЫБРАТЬ 1 КАК Ключ</query>\r\n")
                .append("\t</dataSet>\r\n").toString();
    }

    private static String insertAfterLastRootDataSet(String xml, String fragment) {
        int boundary = xml.indexOf("\t<dataSetLink>");
        if (boundary < 0) boundary = xml.indexOf("</DataCompositionSchema>");
        int close = xml.lastIndexOf("\t</dataSet>\r\n", boundary);
        assertThat(close).isGreaterThanOrEqualTo(0);
        int insertion = close + "\t</dataSet>\r\n".length();
        return xml.substring(0, insertion) + fragment + xml.substring(insertion);
    }

    private static String replaceRootDataSet(String xml, String name, String replacement) {
        String current = rootDataSet(xml, name);
        int start = xml.indexOf(current);
        return xml.substring(0, start) + replacement + xml.substring(start + current.length());
    }

    private static String rootDataSet(String xml, String name) {
        int namePosition = xml.indexOf("<name>" + name + "</name>");
        assertThat(namePosition).isGreaterThanOrEqualTo(0);
        int start = xml.lastIndexOf("\t<dataSet", namePosition);
        int close = xml.indexOf("\t</dataSet>", namePosition);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(close).isGreaterThan(start);
        int end = close + "\t</dataSet>".length();
        if (xml.startsWith("\r\n", end)) end += 2;
        else if (xml.startsWith("\n", end)) end++;
        return xml.substring(start, end);
    }

    private static String directText(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start + tag.length() + 2, end);
    }

    private static String withoutQueryText(String xml) {
        int start = xml.indexOf("<query>");
        while (start >= 0) {
            int end = xml.indexOf("</query>", start);
            if (end < 0) break;
            xml = xml.substring(0, start + 7) + xml.substring(end);
            start = xml.indexOf("<query>", start + 7);
        }
        return xml;
    }

    private String read(Path path) throws Exception {
        return decode(Files.readAllBytes(path));
    }

    private static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == BOM[0]
                && bytes[1] == BOM[1] && bytes[2] == BOM[2] ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static int count(String value, String token) {
        int result = 0;
        for (int from = 0; (from = value.indexOf(token, from)) >= 0; from += token.length()) result++;
        return result;
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
        boolean exited = process.waitFor(60, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record InvalidCase(String payload, String message) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String output() { return stdout + stderr; }
    }
}
//++agent TASK-174

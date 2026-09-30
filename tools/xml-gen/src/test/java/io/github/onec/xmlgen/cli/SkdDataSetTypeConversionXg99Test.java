package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [13.07.2026 00:00:00]
class SkdDataSetTypeConversionXg99Test {
    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String PAYLOAD = """
            {"ifAbsent":"fail","dataSets":[{"name":"ОстаткиМонет","fromType":"DataSetQuery","toType":"DataSetObject","objectName":"ОстаткиМонет","expectedFieldCount":27}],"removeFilters":[{"variant":"Основной_1","structurePath":"ГруппировкаМонета","field":"ТекСумма","comparison":"Greater","rightValue":1},{"variant":"Основной_1","structurePath":"ГруппировкаМонета/ГруппировкаДеталиПартий","field":"ТекСумма","comparison":"Greater","rightValue":1}]}
            """;

    @TempDir Path tempDir;

    @Test
    void convertsTask208ShapePreservingAllNonTargetBytesAndIsDryRunAndRepeatSafe() throws Exception {
        Path schema = writeFixture();
        Path payload = write("payload.json", PAYLOAD);
        byte[] before = Files.readAllBytes(schema);
        Set<PosixFilePermission> mode = Set.of(PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(schema, mode);

        ProcessResult dry = run("skd", "convert-dataset-type", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dry.exitCode).as(dry.output).isZero();
        assertThat(dry.output).contains("[DRY-RUN]").contains("remove 2 direct filter");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);

        ProcessResult apply = run("skd", "convert-dataset-type", schema.toString(),
                "--json", payload.toString());
        assertThat(apply.exitCode).as(apply.output).isZero();
        String after = decode(Files.readAllBytes(schema));
        assertThat(after).contains("<dataSet xsi:type=\"DataSetObject\">\r\n\t\t<name>ОстаткиМонет</name>")
                .contains("<objectName>ОстаткиМонет</objectName>")
                .contains("\t\t<dataSource>ИсточникДанных1</dataSource>\r\n"
                        + "\t\t<objectName>ОстаткиМонет</objectName>\r\n")
                .doesNotContain("<query>ВЫБРАТЬ 1 КАК Аккаунт</query>")
                ;
        assertThat(count(after, "<field xsi:type=\"DataSetFieldField\">")).isEqualTo(28);
        assertThat(count(after, "<dcsset:left xsi:type=\"dcscor:Field\">ТекСумма</dcsset:left>"))
                .isZero();
        assertThat(count(after, "<dcsset:left xsi:type=\"dcscor:Field\">PnL</dcsset:left>"))
                .as("unrelated neighboring direct filter must remain byte-exact").isEqualTo(1);
        for (String invariant : List.of("<dataSetLink>", "<calculatedField>",
                "<groupTemplate>", "НЕ_МЕНЯТЬ", "<dcsset:outputParameters>",
                "<dcsset:field>PnL</dcsset:field>")) assertThat(after).contains(invariant);
        assertThat(Files.readAllBytes(schema)).startsWith(BOM);
        assertThat(after.replace("\r\n", "")).doesNotContain("\n");
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);

        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = run("skd", "convert-dataset-type", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode).as(repeat.output).isZero();
        assertThat(repeat.output).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void selectorTypeCountAndReferenceFailuresNeverMutate() throws Exception {
        List<Failure> failures = List.of(
                new Failure(PAYLOAD.replace("ГруппировкаМонета/ГруппировкаДеталиПартий",
                        "ГруппировкаМонета/НетГруппы"), "structurePath"),
                new Failure(PAYLOAD.replace("\"Greater\",\"rightValue\":1}",
                        "\"Less\",\"rightValue\":1}"), "direct filter"),
                new Failure(PAYLOAD.replace("\"expectedFieldCount\":27", "\"expectedFieldCount\":26"),
                        "field count mismatch"),
                new Failure(PAYLOAD.replace("\"fromType\":\"DataSetQuery\"",
                        "\"fromType\":\"DataSetUnion\""), "only DataSetQuery"));
        for (int i = 0; i < failures.size(); i++) {
            Path schema = writeFixture("failure-" + i + ".xml");
            byte[] before = Files.readAllBytes(schema);
            Path payload = write("failure-" + i + ".json", failures.get(i).payload);
            ProcessResult result = run("skd", "convert-dataset-type", schema.toString(),
                    "--json", payload.toString());
            assertThat(result.exitCode).as(result.output).isNotZero();
            assertThat(result.output).containsIgnoringCase(failures.get(i).message);
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        }

        Path broken = writeFixture("broken-link.xml");
        String invalid = decode(Files.readAllBytes(broken)).replace(
                "<sourceExpression>Аккаунт</sourceExpression>",
                "<sourceExpression>НетПоля</sourceExpression>");
        writeBomCrLf(broken, invalid);
        byte[] before = Files.readAllBytes(broken);
        Path payload = write("broken-link.json", PAYLOAD);
        ProcessResult result = run("skd", "convert-dataset-type", broken.toString(),
                "--json", payload.toString());
        assertThat(result.exitCode).as(result.output).isNotZero();
        assertThat(result.output).containsIgnoringCase("source field missing");
        assertThat(Files.readAllBytes(broken)).isEqualTo(before);

        String sourceNode = "\t\t<dataSource>ИсточникДанных1</dataSource>\r\n";
        List<FailureFixture> invalidSources = List.of(
                new FailureFixture("", "exactly one direct dataSource"),
                new FailureFixture("\t\t<dataSource></dataSource>\r\n", "empty direct dataSource"),
                new FailureFixture("\t\t<dataSource> \t </dataSource>\r\n", "empty direct dataSource"));
        for (int i = 0; i < invalidSources.size(); i++) {
            Path invalidSource = writeFixture("invalid-source-" + i + ".xml");
            String invalidXml = decode(Files.readAllBytes(invalidSource)).replace(
                    sourceNode, invalidSources.get(i).fixture);
            writeBomCrLf(invalidSource, invalidXml);
            byte[] beforeInvalidSource = Files.readAllBytes(invalidSource);
            ProcessResult invalidSourceResult = run("skd", "convert-dataset-type",
                    invalidSource.toString(), "--json", payload.toString());
            assertThat(invalidSourceResult.exitCode).as(invalidSourceResult.output).isNotZero();
            assertThat(invalidSourceResult.output)
                    .containsIgnoringCase(invalidSources.get(i).message)
                    .contains("ОстаткиМонет");
            assertThat(Files.readAllBytes(invalidSource)).isEqualTo(beforeInvalidSource);
        }
    }

    @Test
    void duplicateSourceDataSetAndDuplicateExactFilterFailWithoutMutation() throws Exception {
        String fixture = fixtureText();
        List<FailureFixture> failures = List.of(
                new FailureFixture(fixture.replace("<name>Справочник</name>",
                        "<name>ОстаткиМонет</name>"), "exactly one root dataSet"),
                new FailureFixture(duplicateFirst(fixture, targetCoinFilter()),
                        "found 2"));
        for (int i = 0; i < failures.size(); i++) {
            Path schema = tempDir.resolve("duplicate-" + i + ".xml");
            writeBomCrLf(schema, failures.get(i).fixture);
            byte[] before = Files.readAllBytes(schema);
            Path payload = write("duplicate-" + i + ".json", PAYLOAD);
            ProcessResult result = run("skd", "convert-dataset-type", schema.toString(),
                    "--json", payload.toString());
            assertThat(result.exitCode).as(result.output).isNotZero();
            assertThat(result.output).containsIgnoringCase(failures.get(i).message);
            assertThat(result.output).doesNotContain("\u0000");
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        }
    }

    @Test
    void alreadyConvertedObjectNameMismatchFailsWithoutMutation() throws Exception {
        Path schema = writeFixture("already-converted.xml");
        Path payload = write("convert-first.json", PAYLOAD);
        ProcessResult first = run("skd", "convert-dataset-type", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode).as(first.output).isZero();
        byte[] beforeMismatch = Files.readAllBytes(schema);
        Path mismatch = write("object-name-mismatch.json",
                PAYLOAD.replace("\"objectName\":\"ОстаткиМонет\"",
                        "\"objectName\":\"ДругоеИмя\""));

        ProcessResult result = run("skd", "convert-dataset-type", schema.toString(),
                "--json", mismatch.toString());

        assertThat(result.exitCode).as(result.output).isNotZero();
        assertThat(result.output).containsIgnoringCase("objectName mismatch");
        assertThat(Files.readAllBytes(schema)).isEqualTo(beforeMismatch);
    }

    private Path writeFixture() throws Exception { return writeFixture("Template.xml"); }
    private Path writeFixture(String name) throws Exception {
        Path path = tempDir.resolve(name);
        writeBomCrLf(path, fixtureText());
        return path;
    }

    private String fixtureText() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/skd/xg99-task208-minimal.xml")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String duplicateFirst(String source, String fragment) {
        int at = source.indexOf(fragment);
        assertThat(at).isGreaterThanOrEqualTo(0);
        return source.substring(0, at) + fragment + "\n" + fragment
                + source.substring(at + fragment.length());
    }

    private static String targetCoinFilter() {
        return "\t\t\t\t\t<dcsset:filter><dcsset:item xsi:type=\"dcsset:FilterItemComparison\">"
                + "<dcsset:left xsi:type=\"dcscor:Field\">ТекСумма</dcsset:left>"
                + "<dcsset:comparisonType>Greater</dcsset:comparisonType>"
                + "<dcsset:right xsi:type=\"xs:decimal\">1</dcsset:right>"
                + "</dcsset:item></dcsset:filter>";
    }

    private static void writeBomCrLf(Path path, String source) throws Exception {
        source = source.replace("\r\n", "\n").replace("\n", "\r\n");
        byte[] body = source.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
    }

    private Path write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private ProcessResult run(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp"); command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName()); command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile())
                .redirectErrorStream(true).start();
        boolean exited = process.waitFor(60, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(output).isTrue();
        return new ProcessResult(process.exitValue(), output);
    }

    private static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1]
                && bytes[2] == BOM[2] ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static int count(String value, String token) {
        int result = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) result++;
        return result;
    }

    private record Failure(String payload, String message) { }
    private record FailureFixture(String fixture, String message) { }
    private record ProcessResult(int exitCode, String output) { }
}
//++agent TASK-174

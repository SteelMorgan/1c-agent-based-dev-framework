package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-204 [12.07.2026 00:45:00]
/** Regression XG-92: legacy add-field имеет exact/fail-closed preflight и byte-NO-OP. */
class SkdAddFieldXg92Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String RESOURCE = "/skd/xg92-task204-r3-before-capital-date.xml.gz";
    private static final String DATA_SET = "ДвиженияКапитала";

    @TempDir
    Path tempDir;

    @Test
    void fullTask204R3Fixture_addsFourToFiveThenRepeatsAsByteNoOp() throws Exception {
        Path schema = writeFixture("task204-r3.xml");
        Set<PosixFilePermission> expectedMode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        setMode(schema, expectedMode);
        byte[] before = Files.readAllBytes(schema);
        assertThat(directFieldCount(before)).isEqualTo(4);
        assertThat(capitalDateDeclarationCount(before)).isZero();

        ProcessResult dryRun = addCapitalDate(schema, "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(mode(schema)).isEqualTo(expectedMode);

        ProcessResult first = addCapitalDate(schema);
        assertThat(first.exitCode()).as(first.output()).isZero();
        assertThat(first.output()).contains("Modified");
        byte[] afterFirst = Files.readAllBytes(schema);
        assertThat(afterFirst).startsWith(BOM).isNotEqualTo(before);
        assertThat(directFieldCount(afterFirst)).isEqualTo(5);
        assertThat(capitalDateDeclarationCount(afterFirst)).isEqualTo(1);
        assertThat(mode(schema)).isEqualTo(expectedMode);
        assertCrLfOnly(afterFirst);
        assertThat(removeCapitalDateDeclaration(afterFirst)).isEqualTo(before);

        ProcessResult second = addCapitalDate(schema);
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterFirst);
        assertThat(directFieldCount(Files.readAllBytes(schema))).isEqualTo(5);
        assertThat(mode(schema)).isEqualTo(expectedMode);
        assertNoTempFiles();
    }

    @Test
    void collisionsAndIncompatibleDeclarationFailClosedWithoutMutation() throws Exception {
        List<String[]> conflicts = List.of(
                new String[]{"--name", "ДругоеИмя", "--path", "ВидОперации"},
                new String[]{"--name", "ВидОперации", "--path", "ДругойПуть"}
        );
        for (int index = 0; index < conflicts.size(); index++) {
            Path schema = writeFixture("collision-" + index + ".xml");
            byte[] before = Files.readAllBytes(schema);

            ProcessResult result = addField(schema, conflicts.get(index));

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase("conflict");
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        }

        Path incompatible = writeFixture("incompatible.xml");
        assertThat(addCapitalDate(incompatible).exitCode()).isZero();
        byte[] afterFirst = Files.readAllBytes(incompatible);

        ProcessResult result = addField(incompatible,
                "--name", "КапиталДата", "--path", "КапиталДата",
                "--title", "Другая дата");

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).containsIgnoringCase("incompatible");
        assertThat(Files.readAllBytes(incompatible)).isEqualTo(afterFirst);
        assertNoTempFiles();
    }

    @Test
    void ambiguousExistingDuplicateIsRejectedWithoutRewrite() throws Exception {
        Path schema = writeFixture("duplicate.xml");
        byte[] original = Files.readAllBytes(schema);
        byte[] withDuplicate = insertDuplicateCapitalDateDeclarations(original);
        Files.write(schema, withDuplicate);
        Set<PosixFilePermission> expectedMode = mode(schema);

        ProcessResult result = addCapitalDate(schema);

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).containsIgnoringCase("ambiguous");
        assertThat(Files.readAllBytes(schema)).isEqualTo(withDuplicate);
        assertThat(capitalDateDeclarationCount(withDuplicate)).isEqualTo(2);
        assertThat(mode(schema)).isEqualTo(expectedMode);
        assertNoTempFiles();
    }

    private ProcessResult addCapitalDate(Path schema, String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "skd", "add-field", schema.toString(),
                "--dataset", DATA_SET, "--name", "КапиталДата", "--path", "КапиталДата"));
        args.addAll(List.of(extra));
        return runMain(args.toArray(String[]::new));
    }

    private ProcessResult addField(Path schema, String... identityAndDefinition) throws Exception {
        List<String> args = new ArrayList<>(List.of(
                "skd", "add-field", schema.toString(), "--dataset", DATA_SET));
        args.addAll(List.of(identityAndDefinition));
        return runMain(args.toArray(String[]::new));
    }

    private Path writeFixture(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, gunzipResource());
        assertThat(Files.readAllBytes(path)).startsWith(BOM);
        return path;
    }

    private static byte[] gunzipResource() throws Exception {
        try (InputStream input = SkdAddFieldXg92Test.class.getResourceAsStream(RESOURCE);
             GZIPInputStream gzip = new GZIPInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(input).as(RESOURCE).isNotNull();
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private static int directFieldCount(byte[] bytes) {
        return count(rootDataSetBlock(decode(bytes), DATA_SET), "\t\t<field xsi:type=");
    }

    private static int capitalDateDeclarationCount(byte[] bytes) {
        return count(rootDataSetBlock(decode(bytes), DATA_SET),
                "<dataPath>КапиталДата</dataPath>");
    }

    private static byte[] insertDuplicateCapitalDateDeclarations(byte[] bytes) {
        String xml = decode(bytes);
        String dataSet = rootDataSetBlock(xml, DATA_SET);
        int dataSource = dataSet.indexOf("\t\t<dataSource>");
        assertThat(dataSource).isPositive();
        String declaration = capitalDateDeclaration();
        String changed = dataSet.substring(0, dataSource)
                + declaration + declaration + dataSet.substring(dataSource);
        return encodeWithBom(xml.replace(dataSet, changed));
    }

    private static byte[] removeCapitalDateDeclaration(byte[] bytes) {
        String xml = decode(bytes);
        String dataSet = rootDataSetBlock(xml, DATA_SET);
        int dataPath = dataSet.indexOf("<dataPath>КапиталДата</dataPath>");
        assertThat(dataPath).isPositive();
        int start = dataSet.lastIndexOf("\t\t<field ", dataPath);
        int end = dataSet.indexOf("\t\t</field>\r\n", dataPath);
        assertThat(start).isNotNegative();
        assertThat(end).isPositive();
        end += "\t\t</field>\r\n".length();
        String changed = dataSet.substring(0, start) + dataSet.substring(end);
        return encodeWithBom(xml.replace(dataSet, changed));
    }

    private static String capitalDateDeclaration() {
        return "\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
                + "\t\t\t<dataPath>КапиталДата</dataPath>\r\n"
                + "\t\t\t<field>КапиталДата</field>\r\n"
                + "\t\t\t<title xsi:type=\"v8:LocalStringType\">\r\n"
                + "\t\t\t\t<v8:item>\r\n"
                + "\t\t\t\t\t<v8:lang>ru</v8:lang>\r\n"
                + "\t\t\t\t\t<v8:content>КапиталДата</v8:content>\r\n"
                + "\t\t\t\t</v8:item>\r\n"
                + "\t\t\t</title>\r\n"
                + "\t\t</field>\r\n";
    }

    private static String rootDataSetBlock(String xml, String name) {
        String token = "<name>" + name + "</name>";
        int nameAt = xml.indexOf(token);
        assertThat(nameAt).as(name).isPositive();
        int start = xml.lastIndexOf("\t<dataSet ", nameAt);
        int end = xml.indexOf("\t</dataSet>\r\n", nameAt);
        assertThat(start).isNotNegative();
        assertThat(end).isPositive();
        return xml.substring(start, end + "\t</dataSet>\r\n".length());
    }

    private static String decode(byte[] bytes) {
        assertThat(bytes).startsWith(BOM);
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static byte[] encodeWithBom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] result = Arrays.copyOf(BOM, BOM.length + body.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    private static int count(String text, String token) {
        int result = 0;
        for (int index = 0; (index = text.indexOf(token, index)) >= 0; index += token.length()) {
            result++;
        }
        return result;
    }

    private static void assertCrLfOnly(byte[] bytes) {
        assertThat(decode(bytes).replace("\r\n", "")).doesNotContain("\n");
    }

    private static Set<PosixFilePermission> mode(Path path) throws Exception {
        return Files.getFileStore(path).supportsFileAttributeView("posix")
                ? Files.getPosixFilePermissions(path) : Set.of();
    }

    private static void setMode(Path path, Set<PosixFilePermission> permissions) throws Exception {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, permissions);
        }
    }

    private void assertNoTempFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList())
                    .isEmpty();
        }
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile()).start();
        boolean done = process.waitFor(60, TimeUnit.SECONDS);
        if (!done) process.destroyForcibly();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(done).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout + stderr);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
//++agent TASK-204

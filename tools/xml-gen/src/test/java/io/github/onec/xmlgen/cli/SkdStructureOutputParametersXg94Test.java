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

//++agent TASK-174 [12.07.2026 03:15:00]
/** Regression XG-94: lossless tri-state direct outputParameters точной settings-группы. */
class SkdStructureOutputParametersXg94Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String CURRENT = "/skd/xg94-task204-sha21a49.xml.gz";
    private static final String HISTORICAL = "/skd/xg89-task204-6f35421e.xml.gz";
    private static final String GROUP = "ШапкаТаблицыОпераций";
    private static final String PARAMETER = "ВариантИспользованияГруппировки";
    private static final String REPLACE_PAYLOAD = """
            {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[{
              "kind":"group","name":"ШапкаТаблицыОпераций","outputParameters":[{
                "parameter":"ВариантИспользованияГруппировки",
                "valueType":"DataCompositionGroupUseVariant",
                "value":"AdditionalInformation","use":true
              }]
            }]}
            """;

    @TempDir
    Path tempDir;

    @Test
    void task204Current_addsOnlyCanonicalBlock_historicalIsAlreadyNoOp() throws Exception {
        Path current = writeFixture(CURRENT, "current.xml");
        setMode(current, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ));
        byte[] before = Files.readAllBytes(current);
        String beforeText = decodeBom(before);
        String beforeGroup = structureBlock(beforeText, GROUP);
        Set<PosixFilePermission> modeBefore = mode(current);
        Path payload = writePayload(REPLACE_PAYLOAD);

        ProcessResult dryRun = runMain("skd", "upsert-structure", current.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]");
        assertThat(Files.readAllBytes(current)).isEqualTo(before);

        ProcessResult first = runMain("skd", "upsert-structure", current.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.output()).isZero();
        byte[] after = Files.readAllBytes(current);
        String afterText = decodeBom(after);
        String afterGroup = structureBlock(afterText, GROUP);
        String outputParameters = directNode(afterGroup, "outputParameters");
        assertThat(after).startsWith(BOM).isNotEqualTo(before);
        assertThat(mode(current)).isEqualTo(modeBefore);
        assertThat(outputParameters)
                .contains("xsi:type=\"dcsset:SettingsParameterValue\"")
                .contains("<dcscor:parameter>" + PARAMETER + "</dcscor:parameter>")
                .contains("xsi:type=\"dcsset:DataCompositionGroupUseVariant\">AdditionalInformation")
                .doesNotContain("<dcscor:use>");
        assertThat(count(outputParameters, "SettingsParameterValue")).isOne();
        assertThat(afterGroup.replace(outputParameters, "")).isEqualTo(beforeGroup);
        assertThat(afterText.replace(afterGroup, beforeGroup)).isEqualTo(beforeText);
        assertThat(outputParameters.replace("\r\n", "")).doesNotContain("\n");

        ProcessResult repeat = runMain("skd", "upsert-structure", current.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(current)).isEqualTo(after);

        Path historical = writeFixture(HISTORICAL, "historical.xml");
        byte[] historicalBefore = Files.readAllBytes(historical);
        ProcessResult historicalResult = runMain("skd", "upsert-structure", historical.toString(),
                "--json", payload.toString());
        assertThat(historicalResult.exitCode()).as(historicalResult.output()).isZero();
        assertThat(historicalResult.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(historical)).isEqualTo(historicalBefore);
    }

    @Test
    void outputParameters_triStatePreservesClearsAndReplacesDirectNode() throws Exception {
        Path schema = writeFixture(HISTORICAL, "tri-state.xml");
        byte[] original = Files.readAllBytes(schema);

        ProcessResult preserve = apply(schema, """
                {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[
                  {"kind":"group","name":"ШапкаТаблицыОпераций"}
                ]}
                """);
        assertThat(preserve.exitCode()).as(preserve.output()).isZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        ProcessResult clear = apply(schema, """
                {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[
                  {"kind":"group","name":"ШапкаТаблицыОпераций","outputParameters":[]}
                ]}
                """);
        assertThat(clear.exitCode()).as(clear.output()).isZero();
        byte[] cleared = Files.readAllBytes(schema);
        assertThat(structureBlock(decodeBom(cleared), GROUP)).doesNotContain("outputParameters");

        ProcessResult clearRepeat = apply(schema, """
                {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[
                  {"kind":"group","name":"ШапкаТаблицыОпераций","outputParameters":[]}
                ]}
                """);
        assertThat(clearRepeat.exitCode()).as(clearRepeat.output()).isZero();
        assertThat(clearRepeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(cleared);

        ProcessResult replace = apply(schema, REPLACE_PAYLOAD.replace("\"use\":true", "\"use\":false"));
        assertThat(replace.exitCode()).as(replace.output()).isZero();
        String replaced = directNode(structureBlock(decodeBom(Files.readAllBytes(schema)), GROUP),
                "outputParameters");
        assertThat(replaced).contains("<dcscor:use>false</dcscor:use>");
    }

    @Test
    void missingCollisionAndInvalidBatchFailClosedWithoutMutation() throws Exception {
        List<String> invalidPayloads = List.of(
                REPLACE_PAYLOAD.replace(GROUP, "НетТакойГруппы"),
                """
                {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[{
                  "kind":"group","name":"ШапкаТаблицыОпераций","outputParameters":[
                    {"parameter":"ВариантИспользованияГруппировки",
                     "valueType":"DataCompositionGroupUseVariant","value":"AdditionalInformation"},
                    {"parameter":"ВариантИспользованияГруппировки",
                     "valueType":"DataCompositionGroupUseVariant","value":"AdditionalInformation"}
                  ]
                }]}
                """,
                """
                {"variant":"Основной_1","dataSet":"ИсторияОпераций","items":[
                  {"kind":"group","name":"ШапкаТаблицыОпераций","outputParameters":[]},
                  {"kind":"group","name":"НетТакойГруппы","outputParameters":[]}
                ]}
                """);

        for (int index = 0; index < invalidPayloads.size(); index++) {
            Path schema = writeFixture(CURRENT, "invalid-" + index + ".xml");
            byte[] before = Files.readAllBytes(schema);
            ProcessResult result = apply(schema, invalidPayloads.get(index));
            assertThat(result.exitCode()).as("case " + index + ": " + result.output()).isNotZero();
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }

        Path duplicateNode = writeFixture(HISTORICAL, "duplicate-node.xml");
        String text = decodeBom(Files.readAllBytes(duplicateNode));
        String group = structureBlock(text, GROUP);
        String output = directNode(group, "outputParameters");
        writeWithBom(duplicateNode, text.replace(group,
                group.replace(output, output + output)));
        byte[] before = Files.readAllBytes(duplicateNode);
        ProcessResult result = apply(duplicateNode, REPLACE_PAYLOAD);
        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).contains("at most one direct outputParameters");
        assertThat(Files.readAllBytes(duplicateNode)).isEqualTo(before);
    }

    @Test
    void lfWithoutBomAndSiblingSubtreesStayByteExact() throws Exception {
        byte[] source = gunzipResource(CURRENT);
        String lf = decodeBom(source).replace("\r\n", "\n");
        Path schema = tempDir.resolve("lf-no-bom.xml");
        Files.writeString(schema, lf, StandardCharsets.UTF_8);
        setMode(schema, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        byte[] before = Files.readAllBytes(schema);
        String beforeGroup = structureBlock(lf, GROUP);
        Set<PosixFilePermission> modeBefore = mode(schema);

        ProcessResult result = apply(schema, REPLACE_PAYLOAD);
        assertThat(result.exitCode()).as(result.output()).isZero();
        byte[] after = Files.readAllBytes(schema);
        String afterText = new String(after, StandardCharsets.UTF_8);
        String afterGroup = structureBlock(afterText, GROUP);
        String output = directNode(afterGroup, "outputParameters");
        assertThat(Arrays.copyOf(after, BOM.length)).isNotEqualTo(BOM);
        assertThat(afterText).doesNotContain("\r\n");
        assertThat(mode(schema)).isEqualTo(modeBefore);
        assertThat(afterGroup.replace(output, "")).isEqualTo(beforeGroup);
        assertThat(afterText.replace(afterGroup, beforeGroup)).isEqualTo(lf);
        assertThat(before).isNotEqualTo(after);
    }

    private ProcessResult apply(Path schema, String json) throws Exception {
        Path payload = writePayload(json);
        return runMain("skd", "upsert-structure", schema.toString(), "--json", payload.toString());
    }

    private Path writePayload(String json) throws Exception {
        Path path = tempDir.resolve("payload-" + System.nanoTime() + ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeFixture(String resource, String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, gunzipResource(resource));
        return path;
    }

    private static byte[] gunzipResource(String resource) throws Exception {
        try (InputStream input = SkdStructureOutputParametersXg94Test.class.getResourceAsStream(resource);
             GZIPInputStream gzip = new GZIPInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(input).as(resource).isNotNull();
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private static String decodeBom(byte[] bytes) {
        assertThat(bytes).startsWith(BOM);
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static void writeWithBom(Path path, String text) throws Exception {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = Arrays.copyOf(BOM, BOM.length + body.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
    }

    private static String structureBlock(String xml, String name) {
        String token = "<dcsset:name>" + name + "</dcsset:name>";
        int nameAt = xml.indexOf(token);
        assertThat(nameAt).as(name).isPositive();
        int elementStart = xml.lastIndexOf("<dcsset:item xsi:type=\"dcsset:StructureItem", nameAt);
        int lineStart = xml.lastIndexOf('\n', elementStart) + 1;
        int end = includeFollowingLineBreak(xml,
                matchingElementEnd(xml, elementStart, "dcsset:item"));
        return xml.substring(lineStart, end);
    }

    private static String directNode(String groupBlock, String localName) {
        String open = "<dcsset:" + localName + ">";
        int startTag = groupBlock.indexOf(open);
        assertThat(startTag).as(localName).isNotNegative();
        int lineStart = groupBlock.lastIndexOf('\n', startTag) + 1;
        int end = includeFollowingLineBreak(groupBlock,
                matchingElementEnd(groupBlock, startTag, "dcsset:" + localName));
        return groupBlock.substring(lineStart, end);
    }

    private static int matchingElementEnd(String xml, int start, String qName) {
        int position = start;
        int depth = 0;
        String opening = "<" + qName;
        String closing = "</" + qName + ">";
        while (position < xml.length()) {
            int nextOpen = nextOpeningTag(xml, opening, position);
            int nextClose = xml.indexOf(closing, position);
            if (nextOpen >= 0 && (nextClose < 0 || nextOpen < nextClose)) {
                int tagEnd = tagEnd(xml, nextOpen + opening.length());
                if (!isSelfClosing(xml, nextOpen, tagEnd)) depth++;
                position = tagEnd + 1;
            } else if (nextClose >= 0) {
                depth--;
                position = nextClose + closing.length();
                if (depth == 0) return position;
            } else break;
        }
        throw new IllegalArgumentException("Unbalanced fixture element " + qName);
    }

    private static int nextOpeningTag(String xml, String opening, int from) {
        int candidate = from;
        while ((candidate = xml.indexOf(opening, candidate)) >= 0) {
            int boundary = candidate + opening.length();
            if (boundary < xml.length()) {
                char next = xml.charAt(boundary);
                if (Character.isWhitespace(next) || next == '>' || next == '/') return candidate;
            }
            candidate = boundary;
        }
        return -1;
    }

    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int index = from; index < xml.length(); index++) {
            char current = xml.charAt(index);
            if (quote != 0) {
                if (current == quote) quote = 0;
            } else if (current == '\'' || current == '"') quote = current;
            else if (current == '>') return index;
        }
        throw new IllegalArgumentException("Unterminated fixture tag");
    }

    private static boolean isSelfClosing(String xml, int start, int end) {
        int index = end - 1;
        while (index > start && Character.isWhitespace(xml.charAt(index))) index--;
        return xml.charAt(index) == '/';
    }

    private static int includeFollowingLineBreak(String xml, int position) {
        int result = position;
        while (result < xml.length() && (xml.charAt(result) == ' ' || xml.charAt(result) == '\t')) result++;
        if (result < xml.length() && xml.charAt(result) == '\r') result++;
        if (result < xml.length() && xml.charAt(result) == '\n') result++;
        return result;
    }

    private static int count(String text, String token) {
        int result = 0;
        for (int index = 0; (index = text.indexOf(token, index)) >= 0; index += token.length()) result++;
        return result;
    }

    private static Set<PosixFilePermission> mode(Path path) throws Exception {
        return Files.getFileStore(path).supportsFileAttributeView("posix")
                ? Files.getPosixFilePermissions(path) : Set.of();
    }

    private static void setMode(Path path, Set<PosixFilePermission> mode) throws Exception {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(path, mode);
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
        boolean done = process.waitFor(45, TimeUnit.SECONDS);
        if (!done) process.destroyForcibly();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(done).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout + stderr);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
//++agent TASK-174

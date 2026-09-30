package io.github.onec.xmlgen.cli;

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

//++agent TASK-174 [11.07.2026 22:44:23]
class SkdExactTemplateImportXg90Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String BASELINE_SHA =
            "cc50c244f0cac6d40e70b142671b6f400e4d29c68c8d669fb49fdb30869fd115";
    private static final String DONOR_SHA =
            "7ca5d505671976186ffb33370f4075cc5f5ca025a19c180a2844fca0e0ec20b8";
    private static final String SOURCE_NAME = "Макет7";
    private static final String TARGET_NAME = "МакетДеталиОпераций";
    private static final List<String> BASELINE_TEMPLATES = List.of(
            "Макет1", "Макет3", "Макет4", "Макет9", "Макет5", "Макет6",
            "Макет7", "МакетДвижениеКапитала");

    @TempDir
    Path tempDir;

    @Test
    void task204ImportsDonorAreaTemplateWithOnlyDirectNameChanged() throws Exception {
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
        Path payload = writePayload("task204.json", payload("fail", List.of(
                exactTemplate("donor.xml", SOURCE_NAME, TARGET_NAME))));

        String baseline = decode(baselineBytes);
        String donorXml = decode(donorBytes);
        Map<String, String> baselineBlocks = new LinkedHashMap<>();
        for (String name : BASELINE_TEMPLATES) {
            baselineBlocks.put(name, rootTemplate(baseline, name));
        }
        assertThat(baselineBlocks).hasSize(8);
        String expectedImported = renameDirectName(rootTemplate(donorXml, SOURCE_NAME),
                SOURCE_NAME, TARGET_NAME);
        String expected = insertBeforeBindings(baseline, expectedImported);

        ProcessResult dryRun = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]")
                .contains("1 exact AreaTemplate");
        assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);

        ProcessResult first = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.output()).isZero();
        assertThat(first.output()).contains("Imported 1 exact AreaTemplate");

        byte[] afterBytes = Files.readAllBytes(schema);
        String after = decode(afterBytes);
        assertThat(after).isEqualTo(expected);
        assertThat(rootTemplate(after, TARGET_NAME)).isEqualTo(expectedImported);
        for (Map.Entry<String, String> baselineBlock : baselineBlocks.entrySet()) {
            assertThat(rootTemplate(after, baselineBlock.getKey()))
                    .as("baseline AreaTemplate %s remains byte-identical", baselineBlock.getKey())
                    .isEqualTo(baselineBlock.getValue());
        }
        assertThat(afterBytes).startsWith(BOM);
        assertThat(Files.getPosixFilePermissions(schema)).isEqualTo(mode);
        assertThat(withoutV8Content(expectedImported).replace("\r\n", ""))
                .doesNotContain("\n");
        assertThat(runMain("validate", "--type", "skd", schema.toString()).exitCode())
                .isZero();

        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void exactTemplateAndBindingShareOneAtomicPayload() throws Exception {
        byte[] baselineBytes = fixture("/skd/xg89-task204-cc332cd0.xml.gz");
        byte[] donorBytes = fixture("/skd/xg89-task204-6f35421e.xml.gz");
        Path schema = tempDir.resolve("binding.xml");
        Path donor = tempDir.resolve("binding-donor.xml");
        Files.write(schema, baselineBytes);
        Files.write(donor, donorBytes);
        Path payload = writePayload("binding.json", """
                {
                  "ifAbsent":"fail",
                  "exactTemplates":[{
                    "sourceFile":"binding-donor.xml",
                    "sourceName":"Макет7",
                    "targetName":"МакетДеталиОпераций"
                  }],
                  "groupTemplates":[{
                    "groupName":"ГруппировкаДеталиОпераций",
                    "templateType":"Header",
                    "template":"МакетДеталиОпераций"
                  }]
                }
                """);

        ProcessResult first = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());

        assertThat(first.exitCode()).as(first.output()).isZero();
        String after = decode(Files.readAllBytes(schema));
        assertThat(rootTemplate(after, TARGET_NAME)).isEqualTo(renameDirectName(
                rootTemplate(decode(donorBytes), SOURCE_NAME), SOURCE_NAME, TARGET_NAME));
        assertThat(after).contains("<groupName>ГруппировкаДеталиОпераций</groupName>\r\n"
                + "\t\t<templateType>Header</templateType>\r\n"
                + "\t\t<template>МакетДеталиОпераций</template>");

        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void exactImportRejectsCollisionAmbiguityAndMixedBatchWithoutMutation() throws Exception {
        byte[] baselineBytes = fixture("/skd/xg89-task204-cc332cd0.xml.gz");
        byte[] donorBytes = fixture("/skd/xg89-task204-6f35421e.xml.gz");
        Path donor = tempDir.resolve("donor.xml");
        Files.write(donor, donorBytes);

        List<InvalidCase> cases = List.of(
                new InvalidCase(payload("fail", List.of(
                        exactTemplate("donor.xml", SOURCE_NAME, SOURCE_NAME))),
                        "target AreaTemplate name collision"),
                new InvalidCase(payload("fail", List.of(
                        exactTemplate("donor.xml", SOURCE_NAME, "НоваяОбласть"),
                        exactTemplate("donor.xml", SOURCE_NAME, "НоваяОбласть"))),
                        "duplicate exact AreaTemplate target"),
                new InvalidCase(payload("fail", List.of(
                        exactTemplate("donor.xml", "НетОбласти", "НоваяОбласть"))),
                        "source AreaTemplate not found"),
                new InvalidCase("""
                        {
                          "ifAbsent":"fail",
                          "exactTemplates":[
                            {"sourceFile":"donor.xml","sourceName":"Макет7","targetName":"НоваяОбласть"}
                          ],
                          "templates":[{"name":"НоваяОбласть","rows":[["collision"]]}]
                        }
                        """, "duplicate template target"),
                new InvalidCase("""
                        {
                          "ifAbsent":"fail",
                          "exactTemplates":[
                            {"sourceFile":"donor.xml","sourceName":"Макет7","targetName":"НоваяОбласть"}
                          ],
                          "groupTemplates":[{
                            "groupName":"Группа","templateType":"Header","template":"НетОбласти"
                          }]
                        }
                        """, "references missing AreaTemplate")
        );

        for (int index = 0; index < cases.size(); index++) {
            Path schema = tempDir.resolve("invalid-" + index + ".xml");
            Files.write(schema, baselineBytes);
            Path payload = writePayload("invalid-" + index + ".json",
                    cases.get(index).payload());

            ProcessResult result = runMain("skd", "import-templates", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase(cases.get(index).message());
            assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);
            assertNoTempFiles();
        }

        String duplicateDonor = decode(donorBytes).replace(
                rootTemplate(decode(donorBytes), SOURCE_NAME),
                rootTemplate(decode(donorBytes), SOURCE_NAME)
                        + rootTemplate(decode(donorBytes), SOURCE_NAME));
        Path ambiguousDonor = tempDir.resolve("ambiguous.xml");
        writeDesigner(ambiguousDonor, duplicateDonor);
        Path schema = tempDir.resolve("ambiguous-target.xml");
        Files.write(schema, baselineBytes);
        Path ambiguousPayload = writePayload("ambiguous.json", payload("noop", List.of(
                exactTemplate("ambiguous.xml", SOURCE_NAME, "НоваяОбласть"))));

        ProcessResult ambiguous = runMain("skd", "import-templates", schema.toString(),
                "--json", ambiguousPayload.toString());
        assertThat(ambiguous.exitCode()).as(ambiguous.output()).isNotZero();
        assertThat(ambiguous.output()).containsIgnoringCase("exactly one source AreaTemplate");
        assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);
    }

    @Test
    void ifAbsentNoopAndMismatchedFramingAreByteStable() throws Exception {
        byte[] baselineBytes = fixture("/skd/xg89-task204-cc332cd0.xml.gz");
        byte[] donorBytes = fixture("/skd/xg89-task204-6f35421e.xml.gz");
        Path schema = tempDir.resolve("Template.xml");
        Path donor = tempDir.resolve("donor.xml");
        Files.write(schema, baselineBytes);
        Files.write(donor, donorBytes);

        Path noopPayload = writePayload("noop.json", payload("noop", List.of(
                exactTemplate("donor.xml", "НетОбласти", "НоваяОбласть"))));
        ProcessResult noop = runMain("skd", "import-templates", schema.toString(),
                "--json", noopPayload.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);

        Path lfDonor = tempDir.resolve("donor-lf.xml");
        writeDesigner(lfDonor, decode(donorBytes).replace("\r\n", "\n"));
        Path mismatchPayload = writePayload("mismatch.json", payload("fail", List.of(
                exactTemplate("donor-lf.xml", SOURCE_NAME, TARGET_NAME))));
        ProcessResult mismatch = runMain("skd", "import-templates", schema.toString(),
                "--json", mismatchPayload.toString());
        assertThat(mismatch.exitCode()).as(mismatch.output()).isNotZero();
        assertThat(mismatch.output()).containsIgnoringCase("line endings do not match");
        assertThat(Files.readAllBytes(schema)).isEqualTo(baselineBytes);
        assertNoTempFiles();
    }

    private byte[] fixture(String resource) throws Exception {
        try (InputStream raw = getClass().getResourceAsStream(resource)) {
            assertThat(raw).as(resource).isNotNull();
            try (GZIPInputStream gzip = new GZIPInputStream(raw)) {
                return gzip.readAllBytes();
            }
        }
    }

    private Path writePayload(String name, String json) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private static Map<String, String> exactTemplate(String sourceFile, String sourceName,
                                                      String targetName) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("sourceFile", sourceFile);
        result.put("sourceName", sourceName);
        result.put("targetName", targetName);
        return result;
    }

    private static String payload(String ifAbsent, List<Map<String, String>> templates)
            throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ifAbsent", ifAbsent);
        result.put("exactTemplates", templates);
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
    }

    private static String insertBeforeBindings(String xml, String template) {
        int insertion = firstPositive(xml.indexOf("\t<groupTemplate>"),
                xml.indexOf("\t<groupHeaderTemplate>"),
                xml.indexOf("\t<settingsVariant>"),
                xml.indexOf("</DataCompositionSchema>"));
        assertThat(insertion).isGreaterThanOrEqualTo(0);
        return xml.substring(0, insertion) + template + xml.substring(insertion);
    }

    private static int firstPositive(int... values) {
        int result = Integer.MAX_VALUE;
        for (int value : values) if (value >= 0) result = Math.min(result, value);
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private static String rootTemplate(String xml, String name) {
        String nameToken = "<name>" + name + "</name>";
        int nameAt = xml.indexOf(nameToken);
        assertThat(nameAt).as(name).isGreaterThanOrEqualTo(0);
        int tagStart = xml.lastIndexOf("\t<template>", nameAt);
        assertThat(tagStart).as(name).isGreaterThanOrEqualTo(0);
        int elementStart = tagStart + 1;
        int elementEnd = matchingElementEnd(xml, elementStart, "template");
        int end = elementEnd;
        if (xml.startsWith("\r\n", end)) end += 2;
        else if (xml.startsWith("\n", end)) end++;
        return xml.substring(tagStart, end);
    }

    private static int matchingElementEnd(String xml, int start, String localName) {
        int depth = 0;
        for (int position = start; position < xml.length();) {
            int lt = xml.indexOf('<', position);
            assertThat(lt).isGreaterThanOrEqualTo(0);
            if (xml.startsWith("<!--", lt)) {
                position = xml.indexOf("-->", lt + 4) + 3;
                continue;
            }
            if (xml.startsWith("<![CDATA[", lt)) {
                position = xml.indexOf("]]>", lt + 9) + 3;
                continue;
            }
            int gt = tagEnd(xml, lt + 1);
            boolean closing = xml.startsWith("</", lt);
            boolean declaration = xml.startsWith("<?", lt) || xml.startsWith("<!", lt);
            String qName = readName(xml, lt + (closing ? 2 : 1));
            String actual = qName.contains(":")
                    ? qName.substring(qName.indexOf(':') + 1) : qName;
            if (!declaration && localName.equals(actual)) {
                if (closing) {
                    depth--;
                    if (depth == 0) return gt + 1;
                } else if (!isSelfClosing(xml, lt, gt)) {
                    depth++;
                }
            }
            position = gt + 1;
        }
        throw new AssertionError("Unclosed " + localName);
    }

    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int index = from; index < xml.length(); index++) {
            char current = xml.charAt(index);
            if (quote != 0) {
                if (current == quote) quote = 0;
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '>') {
                return index;
            }
        }
        throw new AssertionError("Unterminated XML tag");
    }

    private static String readName(String xml, int from) {
        while (from < xml.length() && Character.isWhitespace(xml.charAt(from))) from++;
        int end = from;
        while (end < xml.length()) {
            char current = xml.charAt(end);
            if (Character.isWhitespace(current) || current == '>' || current == '/') break;
            end++;
        }
        return xml.substring(from, end);
    }

    private static boolean isSelfClosing(String xml, int lt, int gt) {
        int position = gt - 1;
        while (position > lt && Character.isWhitespace(xml.charAt(position))) position--;
        return xml.charAt(position) == '/';
    }

    private static String renameDirectName(String template, String oldName, String newName) {
        String token = "<name>" + oldName + "</name>";
        int start = template.indexOf(token);
        assertThat(start).isGreaterThanOrEqualTo(0);
        return template.substring(0, start) + "<name>" + newName + "</name>"
                + template.substring(start + token.length());
    }

    private static String withoutV8Content(String xml) {
        int start = xml.indexOf("<v8:content");
        while (start >= 0) {
            int openEnd = xml.indexOf('>', start);
            int end = xml.indexOf("</v8:content>", openEnd);
            if (openEnd < 0 || end < 0) break;
            xml = xml.substring(0, openEnd + 1) + xml.substring(end);
            start = xml.indexOf("<v8:content", openEnd + 1);
        }
        return xml;
    }

    private static void writeDesigner(Path path, String xml) throws Exception {
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
    }

    private static String decode(byte[] bytes) {
        int offset = bytes.length >= 3 && bytes[0] == BOM[0]
                && bytes[1] == BOM[1] && bytes[2] == BOM[2] ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private void assertNoTempFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            assertThat(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")))
                    .isTrue();
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

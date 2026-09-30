package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [12.07.2026 00:00:00]
/** Regression XG-93: root parameters are patched by exact byte spans, without SKD serialization. */
class SkdRootParameterPatchXg93Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String RESOURCE = "/skd/xg93-task204-sha1795.xml.gz";
    private static final String BASELINE_SHA =
            "1795d047e5cc4cc667bd21c2be4ff706dde63d4996542ddc230dacb5b88483a6";

    @TempDir
    Path tempDir;

    @Test
    void task204Sha1795ChangesOnlyTwoExactParameterSubnodesAndPreservesHistory() throws Exception {
        Path schema = writeFixture("task204-sha1795.xml");
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        setMode(schema, mode);
        byte[] beforeBytes = Files.readAllBytes(schema);
        assertThat(sha256(beforeBytes)).isEqualTo(BASELINE_SHA);
        String before = decode(beforeBytes);
        String historyBefore = rootDataSet(before, "ИсторияОпераций");
        String historyC14nBefore = dataSetC14n(beforeBytes, "ИсторияОпераций");
        String accountBefore = rootParameter(before, "Аккаунт");
        String portfolioBefore = rootParameter(before, "Портфель");
        Path payload = writePayload("typed-defaults.json", task204Payload());

        ProcessResult dryRun = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]").contains("2 root parameter");
        assertThat(Files.readAllBytes(schema)).isEqualTo(beforeBytes);

        ProcessResult first = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.output()).isZero();
        assertThat(first.output()).contains("Patched 2 SKD root parameter");

        byte[] afterBytes = Files.readAllBytes(schema);
        String after = decode(afterBytes);
        String accountAfter = expectedTypedEmptyRef(accountBefore,
                "Справочник.big_MarketAccounts.ПустаяСсылка");
        String portfolioAfter = expectedTypedEmptyRef(portfolioBefore,
                "Справочник.биг_Портфели.ПустаяСсылка");
        String expected = before.replace(accountBefore, accountAfter)
                .replace(portfolioBefore, portfolioAfter);
        assertThat(after).isEqualTo(expected);
        assertThat(rootParameter(after, "Аккаунт")).isEqualTo(accountAfter);
        assertThat(rootParameter(after, "Портфель")).isEqualTo(portfolioAfter);
        assertThat(rootDataSet(after, "ИсторияОпераций")).isEqualTo(historyBefore);
        assertThat(dataSetC14n(afterBytes, "ИсторияОпераций")).isEqualTo(historyC14nBefore);
        assertThat(afterBytes).startsWith(BOM);
        assertCrLfOnlyOutsideQuery(after);
        assertThat(mode(schema)).isEqualTo(mode);

        ProcessResult repeat = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterBytes);
        assertThat(mode(schema)).isEqualTo(mode);
        assertNoTempFiles();
    }

    @Test
    void exactSelectorMissingDuplicatePayloadAndMixedFailureAreAtomic() throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase(minimalSchema(), """
                        {"parameters":[{"name":"Нет","value":"Catalog.EmptyRef",
                          "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                          "availableAsField":false}]}
                        """, "root parameter not found"),
                new InvalidCase(minimalSchema().replace("</DataCompositionSchema>", """
                        \t<parameter><name>Аккаунт</name><value xsi:nil="true"/>
                        \t\t<useRestriction>true</useRestriction></parameter>
                        </DataCompositionSchema>""").replace("\n", "\r\n"), """
                        {"parameters":[{"name":"Аккаунт","value":"Catalog.Accounts.EmptyRef",
                          "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                          "availableAsField":false}]}
                        """, "exactly one SKD root parameter"),
                new InvalidCase(minimalSchema(), """
                        {"parameters":[
                          {"name":"Аккаунт","value":"Catalog.Accounts.EmptyRef",
                           "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                           "availableAsField":false},
                          {"name":"Аккаунт","value":"Catalog.Accounts.EmptyRef",
                           "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                           "availableAsField":false}]}
                        """, "duplicate root parameter selector"),
                new InvalidCase(minimalSchema(), """
                        {"parameters":[
                          {"name":"Аккаунт","value":"Catalog.Accounts.EmptyRef",
                           "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                           "availableAsField":false},
                          {"name":"Нет","value":"Catalog.Missing.EmptyRef",
                           "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                           "availableAsField":false}]}
                        """, "root parameter not found")
        );

        for (int index = 0; index < cases.size(); index++) {
            InvalidCase invalid = cases.get(index);
            Path schema = writeSchema("invalid-" + index + ".xml", invalid.xml());
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("invalid-" + index + ".json", invalid.payload());

            ProcessResult result = runMain("skd", "patch-template-parts", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output()).containsIgnoringCase(invalid.message());
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void ifAbsentNoopAndAlreadyCanonicalFlagsDoNotRewrite() throws Exception {
        Path schema = writeSchema("noop.xml", minimalSchema());
        byte[] before = Files.readAllBytes(schema);
        Path missing = writePayload("missing-noop.json", """
                {"ifAbsent":"noop","parameters":[{"name":"Нет",
                  "value":"Catalog.Missing.EmptyRef","xsiType":"dcscor:DesignTimeValue",
                  "useRestriction":false,"availableAsField":false}]}
                """);

        ProcessResult absent = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", missing.toString());
        assertThat(absent.exitCode()).as(absent.output()).isZero();
        assertThat(absent.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);

        Path patch = writePayload("canonical.json", """
                {"parameters":[{"name":"Аккаунт","value":"Catalog.Accounts.EmptyRef",
                  "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                  "availableAsField":false}]}
                """);
        assertThat(runMain("skd", "patch-template-parts", schema.toString(),
                "--json", patch.toString()).exitCode()).isZero();
        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", patch.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    private static String task204Payload() {
        return """
                {"ifAbsent":"fail","parameters":[
                  {"name":"Аккаунт","value":"Справочник.big_MarketAccounts.ПустаяСсылка",
                   "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                   "availableAsField":false},
                  {"name":"Портфель","value":"Справочник.биг_Портфели.ПустаяСсылка",
                   "xsiType":"dcscor:DesignTimeValue","useRestriction":false,
                   "availableAsField":false}
                ]}
                """;
    }

    private static String minimalSchema() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core"
                  xmlns:dcscor="http://v8.1c.ru/8.1/data-composition-system/core"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<parameter>
                \t\t<name>Аккаунт</name>
                \t\t<valueType><v8:Type>CatalogRef.Accounts</v8:Type></valueType>
                \t\t<value xsi:nil="true"/>
                \t\t<useRestriction>true</useRestriction>
                \t</parameter>
                </DataCompositionSchema>
                """.replace("\n", "\r\n");
    }

    private static String expectedTypedEmptyRef(String parameter, String value) {
        String changed = parameter.replace("<value xsi:nil=\"true\"/>",
                "<value xsi:type=\"dcscor:DesignTimeValue\">" + value + "</value>");
        changed = changed.replace("<useRestriction>true</useRestriction>",
                "<useRestriction>false</useRestriction>");
        String restriction = "\t\t<useRestriction>false</useRestriction>";
        assertThat(changed).contains(restriction).doesNotContain("<availableAsField>");
        return changed.replace(restriction, restriction
                + "\r\n\t\t<availableAsField>false</availableAsField>");
    }

    private static String rootParameter(String xml, String name) {
        return exactRootBlock(xml, "parameter", name);
    }

    private static String rootDataSet(String xml, String name) {
        return exactRootBlock(xml, "dataSet", name);
    }

    private static String exactRootBlock(String xml, String tag, String name) {
        String token = "<name>" + name + "</name>";
        int search = 0;
        List<String> matches = new ArrayList<>();
        while ((search = xml.indexOf(token, search)) >= 0) {
            int start = xml.lastIndexOf("\t<" + tag, search);
            int end = xml.indexOf("\t</" + tag + ">\r\n", search);
            if (start >= 0 && end >= 0 && xml.substring(start, search).indexOf('\n') >= 0) {
                String block = xml.substring(start, end + ("\t</" + tag + ">\r\n").length());
                if (directName(block).equals(name)) matches.add(block);
            }
            search += token.length();
        }
        assertThat(matches).as(tag + "/" + name).hasSize(1);
        return matches.get(0);
    }

    private static String directName(String block) {
        int start = block.indexOf("\r\n\t\t<name>");
        if (start < 0) return "";
        start += "\r\n\t\t<name>".length();
        int end = block.indexOf("</name>", start);
        return block.substring(start, end);
    }

    private static String dataSetC14n(byte[] xml, String dataSetName) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        NodeList dataSets = document.getElementsByTagNameNS("*", "dataSet");
        Node found = null;
        for (int index = 0; index < dataSets.getLength(); index++) {
            Node candidate = dataSets.item(index);
            if (dataSetName.equals(directChildText(candidate, "name"))) {
                assertThat(found).as("ambiguous dataSet " + dataSetName).isNull();
                found = candidate;
            }
        }
        assertThat(found).as(dataSetName).isNotNull();
        StringBuilder result = new StringBuilder();
        appendC14n(found, result);
        return result.toString();
    }

    private static String directChildText(Node parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && localName.equals(child.getLocalName())) {
                return child.getTextContent();
            }
        }
        return null;
    }

    private static void appendC14n(Node node, StringBuilder result) {
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            result.append("#").append(node.getNodeValue());
            return;
        }
        if (node.getNodeType() != Node.ELEMENT_NODE) return;
        result.append('<').append(node.getNamespaceURI()).append('|').append(node.getLocalName());
        NamedNodeMap attributes = node.getAttributes();
        List<Node> sorted = new ArrayList<>();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (!"http://www.w3.org/2000/xmlns/".equals(attribute.getNamespaceURI())) {
                sorted.add(attribute);
            }
        }
        sorted.sort(Comparator.comparing(attribute ->
                String.valueOf(attribute.getNamespaceURI()) + "|" + attribute.getLocalName()));
        for (Node attribute : sorted) {
            result.append('@').append(attribute.getNamespaceURI()).append('|')
                    .append(attribute.getLocalName()).append('=').append(attribute.getNodeValue());
        }
        result.append('>');
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendC14n(child, result);
        }
        result.append("</").append(node.getNamespaceURI()).append('|')
                .append(node.getLocalName()).append('>');
    }

    private Path writeFixture(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, gunzipResource());
        return path;
    }

    private static byte[] gunzipResource() throws Exception {
        try (InputStream input = SkdRootParameterPatchXg93Test.class.getResourceAsStream(RESOURCE);
             GZIPInputStream gzip = new GZIPInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            assertThat(input).as(RESOURCE).isNotNull();
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private Path writeSchema(String name, String xml) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, encodeWithBom(xml));
        return path;
    }

    private Path writePayload(String name, String json) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private static String decode(byte[] bytes) {
        assertThat(bytes).startsWith(BOM);
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static byte[] encodeWithBom(String xml) {
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = Arrays.copyOf(BOM, BOM.length + body.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        return bytes;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void assertCrLfOnlyOutsideQuery(String xml) {
        String withoutQueries = xml.replaceAll("(?s)<query>.*?</query>", "");
        assertThat(withoutQueries.replace("\r\n", "")).doesNotContain("\n");
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
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record InvalidCase(String xml, String payload, String message) {
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String output() { return stdout + stderr; }
    }
}
//++agent TASK-174

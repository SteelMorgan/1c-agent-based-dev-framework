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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [12.07.2026 00:20:00]
/** Regression XG-91: exact order внутри группы и полный порядок корневой структуры СКД. */
class SkdStructureOrderXg91Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String BASELINE_RESOURCE = "/skd/xg89-task204-cc332cd0.xml.gz";
    private static final String DONOR_RESOURCE = "/skd/xg89-task204-6f35421e.xml.gz";
    private static final List<String> BASELINE_ORDER = List.of(
            "ИнформацияОПортфеле",
            "ШапкаТаблицыМонет",
            "ГруппировкаМонета",
            "ГруппировкаВидДвиженияКапитала",
            "Диаграмма");
    private static final List<String> RESTORED_ORDER = List.of(
            "ИнформацияОПортфеле",
            "ШапкаТаблицыМонет",
            "ГруппировкаМонета",
            "ШапкаТаблицыОпераций",
            "ГруппировкаИнструменты",
            "ГруппировкаВидДвиженияКапитала",
            "Диаграмма");
    private static final Pattern TOP_LEVEL_STRUCTURE = Pattern.compile(
            "(?m)^\\t\\t\\t<dcsset:item xsi:type=\"dcsset:StructureItem[^\"]+\">\\r?$");

    @TempDir
    Path tempDir;

    @Test
    void task204RestoredFixture_replacesCapitalOrderAndReordersRootLosslessly() throws Exception {
        Path schema = writeRestoredStructureFixture();
        setMode(schema, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ));
        byte[] before = Files.readAllBytes(schema);
        Set<PosixFilePermission> modeBefore = mode(schema);
        String beforeText = decode(before);
        Map<String, String> blocksBefore = topLevelBlocks(beforeText);
        String detailBefore = structureBlock(beforeText, "ГруппировкаДеталиДвиженийКапитала");
        String groupItemsBefore = directNode(detailBefore, "groupItems");
        String selectionBefore = directNode(detailBefore, "selection");
        Path payload = writePayload(restoredPayload(RESTORED_ORDER));

        ProcessResult dryRun = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString(), "--dry-run");

        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(mode(schema)).isEqualTo(modeBefore);

        ProcessResult first = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(first.exitCode()).as(first.output()).isZero();
        byte[] after = Files.readAllBytes(schema);
        String afterText = decode(after);
        Map<String, String> blocksAfter = topLevelBlocks(afterText);
        assertThat(after).startsWith(BOM).isNotEqualTo(before);
        assertThat(mode(schema)).isEqualTo(modeBefore);
        assertThat(new ArrayList<>(blocksAfter.keySet())).containsExactlyElementsOf(RESTORED_ORDER);

        for (String name : RESTORED_ORDER) {
            if (!"ГруппировкаВидДвиженияКапитала".equals(name)) {
                assertThat(blocksAfter.get(name)).as("byte subtree " + name)
                        .isEqualTo(blocksBefore.get(name));
            }
        }

        String detailAfter = structureBlock(afterText, "ГруппировкаДеталиДвиженийКапитала");
        String orderAfter = directNode(detailAfter, "order");
        assertThat(orderAfter)
                .containsSubsequence(
                        "<dcsset:field>КапиталДата</dcsset:field>",
                        "<dcsset:orderType>Asc</dcsset:orderType>",
                        "<dcsset:field>ДокументВводаВывода</dcsset:field>",
                        "<dcsset:orderType>Asc</dcsset:orderType>")
                .doesNotContain("OrderItemAuto");
        assertThat(count(orderAfter, "OrderItemField")).isEqualTo(2);
        assertThat(directNode(detailAfter, "groupItems")).isEqualTo(groupItemsBefore);
        assertThat(directNode(detailAfter, "selection")).isEqualTo(selectionBefore);
        assertThat(withOrderPlaceholder(detailAfter, orderAfter))
                .isEqualTo(withOrderPlaceholder(detailBefore, directNode(detailBefore, "order")));
        assertThat(orderAfter.replace("\r\n", "")).doesNotContain("\n");

        ProcessResult second = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(second.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(after);
    }

    @Test
    void cc332Baseline_orderTriStatePreservesClearsAndReplacesOnlyDirectOrder() throws Exception {
        Path schema = writeBaselineFixture("cc332-order.xml");
        byte[] original = Files.readAllBytes(schema);

        assertSuccess(schema, """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"ГруппировкаВидДвиженияКапитала","children":[
                    {"kind":"group","name":"ГруппировкаДеталиДвиженийКапитала"}
                  ]}
                ]}
                """);
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        String beforeDetail = structureBlock(decode(original),
                "ГруппировкаДеталиДвиженийКапитала");
        String selection = directNode(beforeDetail, "selection");
        String groupItems = directNode(beforeDetail, "groupItems");
        String clear = """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"ГруппировкаВидДвиженияКапитала","children":[
                    {"kind":"group","name":"ГруппировкаДеталиДвиженийКапитала","order":[]}
                  ]}
                ],"topLevelOrder":%s}
                """.formatted(jsonArray(BASELINE_ORDER));
        assertSuccess(schema, clear);
        byte[] afterClear = Files.readAllBytes(schema);
        String cleared = structureBlock(decode(afterClear),
                "ГруппировкаДеталиДвиженийКапитала");
        assertThat(cleared).doesNotContain("<dcsset:order>")
                .contains(selection).contains(groupItems);
        assertSuccess(schema, clear);
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterClear);

        String replace = """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"ГруппировкаВидДвиженияКапитала","children":[
                    {"kind":"group","name":"ГруппировкаДеталиДвиженийКапитала","order":[
                      {"field":"ДокументВводаВывода","direction":"Desc"}
                    ]}
                  ]}
                ],"topLevelOrder":%s}
                """.formatted(jsonArray(BASELINE_ORDER));
        assertSuccess(schema, replace);
        String replaced = structureBlock(decode(Files.readAllBytes(schema)),
                "ГруппировкаДеталиДвиженийКапитала");
        assertThat(directNode(replaced, "order"))
                .contains("<dcsset:field>ДокументВводаВывода</dcsset:field>")
                .contains("<dcsset:orderType>Desc</dcsset:orderType>")
                .doesNotContain("OrderItemAuto");
        assertThat(directNode(replaced, "selection")).isEqualTo(selection);
        assertThat(directNode(replaced, "groupItems")).isEqualTo(groupItems);
    }

    @Test
    void topLevelOrder_acceptsItemAddedBySameAtomicPayloadAndPreservesExistingBlocks() throws Exception {
        Path schema = writeBaselineFixture("cc332-add-reorder.xml");
        Map<String, String> before = topLevelBlocks(decode(Files.readAllBytes(schema)));
        List<String> expected = List.of(
                "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                "НоваяГруппа", "ГруппировкаВидДвиженияКапитала", "Диаграмма");
        String payload = """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"НоваяГруппа","selection":[]}
                ],"topLevelOrder":%s}
                """.formatted(jsonArray(expected));

        assertSuccess(schema, payload);

        Map<String, String> after = topLevelBlocks(decode(Files.readAllBytes(schema)));
        assertThat(new ArrayList<>(after.keySet())).containsExactlyElementsOf(expected);
        for (String name : BASELINE_ORDER) {
            assertThat(after.get(name)).as("existing subtree " + name).isEqualTo(before.get(name));
        }
        assertThat(after.get("НоваяГруппа")).contains("OrderItemAuto")
                .doesNotContain("<dcsset:selection>");
    }

    @Test
    void topLevelOrderOnly_doesNotRequireDatasetAndMovesExactSubtrees() throws Exception {
        Path schema = writeBaselineFixture("cc332-reorder-only.xml");
        Map<String, String> before = topLevelBlocks(decode(Files.readAllBytes(schema)));
        List<String> expected = List.of(
                "ИнформацияОПортфеле", "ГруппировкаВидДвиженияКапитала",
                "ШапкаТаблицыМонет", "ГруппировкаМонета", "Диаграмма");
        String payload = """
                {"variant":"Основной_1","topLevelOrder":%s}
                """.formatted(jsonArray(expected));

        assertSuccess(schema, payload);

        byte[] afterFirst = Files.readAllBytes(schema);
        Map<String, String> after = topLevelBlocks(decode(afterFirst));
        assertThat(new ArrayList<>(after.keySet())).containsExactlyElementsOf(expected);
        for (String name : BASELINE_ORDER) {
            assertThat(after.get(name)).as("reordered subtree " + name).isEqualTo(before.get(name));
        }
        assertSuccess(schema, payload);
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterFirst);
    }

    @Test
    void invalidOrderAndExactRootPoliciesRejectWholeBatchWithoutMutation() throws Exception {
        List<String> invalidPayloads = List.of(
                restoredPayload(RESTORED_ORDER).replace("КапиталДата\",\"direction\":\"Asc",
                        "НетПоля\",\"direction\":\"Asc"),
                restoredPayload(RESTORED_ORDER).replace("\"direction\":\"Asc\"",
                        "\"direction\":\"Ascending\""),
                restoredPayload(RESTORED_ORDER).replace(
                        "{\"field\":\"ДокументВводаВывода\",\"direction\":\"Asc\"}",
                        "{\"field\":\"КапиталДата\",\"direction\":\"Asc\"}"),
                restoredPayload(List.of(
                        "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                        "ШапкаТаблицыОпераций", "ГруппировкаИнструменты",
                        "ГруппировкаВидДвиженияКапитала")),
                restoredPayload(List.of(
                        "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                        "ШапкаТаблицыОпераций", "ГруппировкаИнструменты",
                        "ГруппировкаВидДвиженияКапитала", "НетТакойГруппы")),
                restoredPayload(List.of(
                        "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                        "ШапкаТаблицыОпераций", "ГруппировкаИнструменты",
                        "ГруппировкаВидДвиженияКапитала", "Диаграмма", "Диаграмма")),
                restoredPayload(List.of(
                        "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                        "ШапкаТаблицыОпераций", "ГруппировкаИнструменты",
                        "ГруппировкаДеталиДвиженийКапитала", "Диаграмма")));

        for (int index = 0; index < invalidPayloads.size(); index++) {
            Path schema = writeRestoredStructureFixture("invalid-" + index + ".xml");
            byte[] before = Files.readAllBytes(schema);
            Set<PosixFilePermission> modeBefore = mode(schema);
            Path payload = writePayload(invalidPayloads.get(index));

            ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as("case " + index + ": " + result.output()).isNotZero();
            assertThat(Files.readAllBytes(schema)).as(result.output()).isEqualTo(before);
            assertThat(mode(schema)).isEqualTo(modeBefore);
            assertNoTempFiles();
        }
    }

    @Test
    void duplicateExistingTopLevelIdentityIsRejectedBeforeOrderMutation() throws Exception {
        Path schema = writeRestoredStructureFixture("duplicate-existing.xml");
        String text = decode(Files.readAllBytes(schema));
        String duplicate = topLevelBlocks(text).get("ШапкаТаблицыОпераций");
        int capital = text.indexOf(topLevelBlocks(text).get("ГруппировкаВидДвиженияКапитала"));
        writeWithBom(schema, text.substring(0, capital) + duplicate + text.substring(capital));
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload(restoredPayload(RESTORED_ORDER));

        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    @Test
    void duplicateDirectOrderIsRejectedByExactGroupSelector() throws Exception {
        Path schema = writeBaselineFixture("duplicate-order.xml");
        String text = decode(Files.readAllBytes(schema));
        String detail = structureBlock(text, "ГруппировкаДеталиДвиженийКапитала");
        String order = directNode(detail, "order");
        String duplicatedDetail = detail.replace(order, order + order);
        writeWithBom(schema, text.replace(detail, duplicatedDetail));
        byte[] before = Files.readAllBytes(schema);
        String payload = """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"ГруппировкаВидДвиженияКапитала","children":[
                    {"kind":"group","name":"ГруппировкаДеталиДвиженийКапитала","order":[
                      {"field":"ДокументВводаВывода","direction":"Asc"}
                    ]}
                  ]}
                ]}
                """;
        Path patch = writePayload(payload);

        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", patch.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).contains("at most one direct order");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    private String restoredPayload(List<String> topLevelOrder) {
        return """
                {"variant":"Основной_1","dataSet":"ДвиженияКапитала","items":[
                  {"kind":"group","name":"ГруппировкаВидДвиженияКапитала","children":[
                    {"kind":"group","name":"ГруппировкаДеталиДвиженийКапитала","order":[
                      {"field":"КапиталДата","direction":"Asc"},
                      {"field":"ДокументВводаВывода","direction":"Asc"}
                    ]}
                  ]}
                ],"topLevelOrder":%s}
                """.formatted(jsonArray(topLevelOrder));
    }

    private Path writeRestoredStructureFixture() throws Exception {
        return writeRestoredStructureFixture("task204-restored-structure.xml");
    }

    private Path writeRestoredStructureFixture(String name) throws Exception {
        String baseline = decode(gunzipResource(BASELINE_RESOURCE));
        String donor = decode(gunzipResource(DONOR_RESOURCE));
        String history = rootDataSetBlock(donor, "ИсторияОпераций");
        int firstLink = baseline.indexOf("\t<dataSetLink>");
        assertThat(firstLink).isPositive();
        String withHistory = baseline.substring(0, firstLink) + history + baseline.substring(firstLink);
        String withDate = addCapitalDateField(withHistory);
        Map<String, String> donorBlocks = topLevelBlocks(donor);
        String trading = donorBlocks.get("ШапкаТаблицыОпераций")
                + donorBlocks.get("ГруппировкаИнструменты");
        Map<String, String> currentBlocks = topLevelBlocks(withDate);
        String chart = currentBlocks.get("Диаграмма");
        int afterChart = withDate.indexOf(chart) + chart.length();
        String restored = withDate.substring(0, afterChart) + trading + withDate.substring(afterChart);
        Path path = tempDir.resolve(name);
        writeWithBom(path, restored);
        assertThat(new ArrayList<>(topLevelBlocks(restored).keySet())).containsExactly(
                "ИнформацияОПортфеле", "ШапкаТаблицыМонет", "ГруппировкаМонета",
                "ГруппировкаВидДвиженияКапитала", "Диаграмма",
                "ШапкаТаблицыОпераций", "ГруппировкаИнструменты");
        return path;
    }

    private Path writeBaselineFixture(String name) throws Exception {
        Path path = tempDir.resolve(name);
        Files.write(path, gunzipResource(BASELINE_RESOURCE));
        assertThat(Files.readAllBytes(path)).startsWith(BOM);
        return path;
    }

    private static String addCapitalDateField(String xml) {
        String dataSet = rootDataSetBlock(xml, "ДвиженияКапитала");
        int dataSource = dataSet.indexOf("\t\t<dataSource>");
        assertThat(dataSource).isPositive();
        String field = "\t\t<field xsi:type=\"DataSetFieldField\">\r\n"
                + "\t\t\t<dataPath>КапиталДата</dataPath>\r\n"
                + "\t\t\t<field>КапиталДата</field>\r\n"
                + "\t\t</field>\r\n";
        String changed = dataSet.substring(0, dataSource) + field + dataSet.substring(dataSource);
        return xml.replace(dataSet, changed);
    }

    private static String rootDataSetBlock(String xml, String name) {
        String token = "<name>" + name + "</name>";
        int nameAt = xml.indexOf(token);
        assertThat(nameAt).as(name).isPositive();
        int start = xml.lastIndexOf("\t<dataSet ", nameAt);
        int end = xml.indexOf("\t</dataSet>\r\n", nameAt);
        assertThat(start).as(name).isNotNegative();
        assertThat(end).as(name).isPositive();
        return xml.substring(start, end + "\t</dataSet>\r\n".length());
    }

    private static Map<String, String> topLevelBlocks(String xml) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        Matcher matcher = TOP_LEVEL_STRUCTURE.matcher(xml);
        while (matcher.find()) {
            int elementStart = xml.indexOf('<', matcher.start());
            int elementEnd = matchingElementEnd(xml, elementStart, "dcsset:item");
            int end = includeFollowingLineBreak(xml, elementEnd);
            String block = xml.substring(matcher.start(), end);
            String name = between(block, "<dcsset:name>", "</dcsset:name>");
            assertThat(result.put(name, block)).as("duplicate fixture identity " + name).isNull();
        }
        return result;
    }

    private static String structureBlock(String xml, String name) {
        String token = "<dcsset:name>" + name + "</dcsset:name>";
        int nameAt = xml.indexOf(token);
        assertThat(nameAt).as(name).isPositive();
        int elementStart = xml.lastIndexOf("<dcsset:item xsi:type=\"dcsset:StructureItem", nameAt);
        assertThat(elementStart).as(name).isNotNegative();
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
            } else {
                break;
            }
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
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '>') {
                return index;
            }
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
        while (result < xml.length() && (xml.charAt(result) == ' ' || xml.charAt(result) == '\t')) {
            result++;
        }
        if (result < xml.length() && xml.charAt(result) == '\r') result++;
        if (result < xml.length() && xml.charAt(result) == '\n') result++;
        return result;
    }

    private static String between(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from + start.length());
        assertThat(from).isNotNegative();
        assertThat(to).isPositive();
        return text.substring(from + start.length(), to);
    }

    private static String withOrderPlaceholder(String detail, String order) {
        return detail.replace(order, "__ORDER__\r\n");
    }

    private static String jsonArray(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"")
                .reduce((left, right) -> left + "," + right).map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private Path writePayload(String json) throws Exception {
        Path path = tempDir.resolve("payload-" + System.nanoTime() + ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private void assertSuccess(Path schema, String json) throws Exception {
        Path payload = writePayload(json);
        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    private static byte[] gunzipResource(String resource) throws Exception {
        try (InputStream input = SkdStructureOrderXg91Test.class.getResourceAsStream(resource);
             GZIPInputStream gzip = new GZIPInputStream(input);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            gzip.transferTo(output);
            return output.toByteArray();
        }
    }

    private static String decode(byte[] bytes) {
        assertThat(bytes).startsWith(BOM);
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static void writeWithBom(Path path, String text) throws Exception {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = Arrays.copyOf(BOM, BOM.length + body.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
    }

    private static int count(String text, String token) {
        int result = 0;
        for (int index = 0; (index = text.indexOf(token, index)) >= 0; index += token.length()) {
            result++;
        }
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

package io.github.onec.xmlgen.cli;

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

//++agent TASK-174 [11.07.2026 11:00:00]
class SkdTemplatePartsPatchXg88Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String FIXTURE_COMMIT =
            "c3fcb115132ec5bb1a6538abdf9c9bebe3166930";
    private static final String FIXTURE_PATH =
            "src/xml/Reports/биг_РезультатыУправления/Templates/"
                    + "ОсновнаяСхемаКомпоновкиДанных/Ext/Template.xml";
    private static final String FIXTURE_SHA =
            "fb39525e09a609fe37c2b3944d88f67dcb27dff35982e37c0c24c24fb5be628a";

    @TempDir
    Path tempDir;

    @Test
    void task204ManualBaselineChangesOnlyNineExpressionsAndOneExactBinding() throws Exception {
        byte[] productionBytes = historicalFixture();
        assertThat(sha256(productionBytes)).isEqualTo(FIXTURE_SHA);

        Path isolated = tempDir.resolve("task204-manual-baseline.xml");
        Files.write(isolated, productionBytes);
        Set<PosixFilePermission> mode = Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ);
        Files.setPosixFilePermissions(isolated, mode);
        Path payload = writePayload("task204-xg88.json", task204Payload());
        String before = decode(productionBytes);
        String maket1Body = areaTemplateBody(before, "Макет1");
        String capitalBody = areaTemplateBody(before, "МакетДвижениеКапитала");

        ProcessResult result = runMain("skd", "patch-template-parts", isolated.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("Patched 9 SKD template parameter expression")
                .contains("1 group binding");
        String after = read(isolated);
        String expected = before;
        expected = expectedExpression(expected, "Макет1", "ВесьП_Вводы_Сумма",
                "ВесьП_ВводыСумма");
        expected = expectedExpression(expected, "Макет1", "ВесьП_Выводы_Сумма",
                "ВесьП_ВыводыСумма");
        expected = expectedExpression(expected, "Макет1", "Отч_Вводы_Сумма",
                "Отч_ВводыСумма");
        expected = expectedExpression(expected, "Макет1", "Отч_Total_PNL",
                "Отч_Total_PnL");
        expected = expectedExpression(expected, "Макет1", "Отч_Выводы_Сумма",
                "Отч_ВыводыСумма");
        for (String parameter : List.of("ДокументВводаВывода", "ВидОперации",
                "ОбъемОперации", "МонетаОперации")) {
            expected = expectedExpression(expected, "МакетДвижениеКапитала", parameter, parameter);
        }
        expected = expectedBindingGroup(expected, "ГруппировкаДеталиОпераций", "Header",
                "Макет7", "ГруппировкаВидДвиженияКапитала");
        assertThat(after).isEqualTo(expected);
        assertThat(expression(after, "Макет1", "ВесьП_Вводы_Сумма"))
                .isEqualTo("ВесьП_ВводыСумма");
        assertThat(expression(after, "Макет1", "ВесьП_Выводы_Сумма"))
                .isEqualTo("ВесьП_ВыводыСумма");
        assertThat(expression(after, "Макет1", "Отч_Вводы_Сумма"))
                .isEqualTo("Отч_ВводыСумма");
        assertThat(expression(after, "Макет1", "Отч_Total_PNL"))
                .isEqualTo("Отч_Total_PnL");
        assertThat(expression(after, "Макет1", "Отч_Выводы_Сумма"))
                .isEqualTo("Отч_ВыводыСумма");
        for (String parameter : List.of("ДокументВводаВывода", "ВидОперации",
                "ОбъемОперации", "МонетаОперации")) {
            assertThat(expression(after, "МакетДвижениеКапитала", parameter))
                    .isEqualTo(parameter);
        }
        assertThat(bindingCount(after, "ГруппировкаДеталиОпераций", "Header", "Макет7"))
                .isZero();
        assertThat(bindingCount(after, "ГруппировкаВидДвиженияКапитала", "Header", "Макет7"))
                .isOne();
        assertThat(bindingCount(after, "ГруппировкаДеталиДвиженийКапитала", "Header",
                "МакетДвижениеКапитала")).isOne();

        // Геометрия, cells и appearance находятся только во внутреннем AreaTemplate:
        // его полное побайтовое равенство сильнее отдельных style/merge hashes.
        assertThat(areaTemplateBody(after, "Макет1")).isEqualTo(maket1Body);
        assertThat(areaTemplateBody(after, "МакетДвижениеКапитала")).isEqualTo(capitalBody);
        assertThat(Files.readAllBytes(isolated)).startsWith(BOM);
        assertThat(after.replace("\r\n", "")).doesNotContain("\n");
        assertThat(Files.getPosixFilePermissions(isolated)).isEqualTo(mode);
        assertThat(sha256(historicalFixture())).isEqualTo(FIXTURE_SHA);
        assertThat(runMain("validate", "--type", "skd", isolated.toString()).exitCode()).isZero();
    }

    @Test
    void dryRunRepeatAndIfAbsentNoopAreByteStable() throws Exception {
        Path schema = writeSchema("repeat.xml", schema());
        Path payload = writePayload("repeat.json", """
                {
                  "ifAbsent":"fail",
                  "parameterExpressions":[
                    {"template":"МакетА","parameter":"ПараметрА","expression":"Новое & Значение"}
                  ],
                  "groupBindings":[
                    {"action":"replace","groupName":"СтараяГруппа","templateType":"Header","template":"МакетА",
                     "newGroupName":"НоваяГруппа","newTemplateType":"Header","newTemplate":"МакетА"}
                  ]
                }
                """);
        byte[] original = Files.readAllBytes(schema);

        ProcessResult dryRun = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString(), "--dry-run");
        assertThat(dryRun.exitCode()).as(dryRun.output()).isZero();
        assertThat(dryRun.output()).contains("[DRY-RUN]").contains("1 SKD template parameter expression")
                .contains("1 group binding");
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        ProcessResult first = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.output()).isZero();
        byte[] once = Files.readAllBytes(schema);
        ProcessResult repeat = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString());
        assertThat(repeat.exitCode()).as(repeat.output()).isZero();
        assertThat(repeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
        assertThat(expression(read(schema), "МакетА", "ПараметрА"))
                .isEqualTo("Новое & Значение");

        Path missingNoop = writePayload("missing-noop.json", """
                {"ifAbsent":"noop","parameterExpressions":[
                  {"template":"НетМакета","parameter":"НетПараметра","expression":"Значение"}
                ],"groupBindings":[
                  {"action":"remove","groupName":"НетГруппы","templateType":"Header","template":"МакетА"}
                ]}
                """);
        ProcessResult noop = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", missingNoop.toString());
        assertThat(noop.exitCode()).as(noop.output()).isZero();
        assertThat(noop.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
        assertNoTempFiles();
    }

    @Test
    void dependencySafeRemoveAndUpsertArePreflightedAsOneFinalBindingSet() throws Exception {
        Path schema = writeSchema("dependency-safe.xml", schema());
        Path payload = writePayload("dependency-safe.json", """
                {"groupBindings":[
                  {"action":"remove","groupName":"СтараяГруппа","templateType":"Header","template":"МакетА"},
                  {"action":"upsert","groupName":"СтараяГруппа","templateType":"Header","template":"МакетБ"}
                ]}
                """);

        ProcessResult result = runMain("skd", "patch-template-parts", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        String after = read(schema);
        assertThat(bindingCount(after, "СтараяГруппа", "Header", "МакетА")).isZero();
        assertThat(bindingCount(after, "СтараяГруппа", "Header", "МакетБ")).isOne();
    }

    @Test
    void missingAmbiguousCollisionAndInvalidMixedBatchLeaveOriginalUntouched() throws Exception {
        List<InvalidCase> cases = List.of(
                new InvalidCase(schema(), """
                        {"parameterExpressions":[{"template":"Нет","parameter":"ПараметрА","expression":"X"}]}
                        """, "AreaTemplate not found"),
                new InvalidCase(schema().replace("</template>\r\n\t<template>\r\n\t\t<name>МакетБ</name>",
                        "\t\t<parameter xmlns:dcsat=\"http://v8.1c.ru/8.1/data-composition-system/area-template\" xsi:type=\"dcsat:ExpressionAreaTemplateParameter\"><dcsat:name>ПараметрА</dcsat:name></parameter>\r\n\t</template>\r\n\t<template>\r\n\t\t<name>МакетБ</name>"), """
                        {"parameterExpressions":[{"template":"МакетА","parameter":"ПараметрА","expression":"X"}]}
                        """, "exactly one AreaTemplate parameter"),
                new InvalidCase(schema(), """
                        {"groupBindings":[{"action":"replace","groupName":"СтараяГруппа","templateType":"Header","template":"МакетА",
                          "newGroupName":"НетГруппы","newTemplateType":"Header","newTemplate":"МакетА"}]}
                        """, "referenced structure group not found"),
                new InvalidCase(schema(), """
                        {"groupBindings":[{"action":"upsert","groupName":"СтараяГруппа","templateType":"Header","template":"МакетБ"}]}
                        """, "binding collision"),
                new InvalidCase(schema(), """
                        {"parameterExpressions":[
                          {"template":"МакетА","parameter":"ПараметрА","expression":"X"},
                          {"template":"Нет","parameter":"ПараметрА","expression":"Y"}
                        ]}
                        """, "AreaTemplate not found"),
                new InvalidCase(schema(), """
                        {"groupBindings":[
                          {"action":"remove","groupName":"СтараяГруппа","templateType":"Header","template":"МакетА"},
                          {"action":"upsert","groupName":"НоваяГруппа","templateType":"Header","template":"НетМакета"}
                        ]}
                        """, "referenced AreaTemplate not found")
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

    private static String task204Payload() {
        return """
                {
                  "ifAbsent":"fail",
                  "parameterExpressions":[
                    {"template":"Макет1","parameter":"ВесьП_Вводы_Сумма","expression":"ВесьП_ВводыСумма"},
                    {"template":"Макет1","parameter":"ВесьП_Выводы_Сумма","expression":"ВесьП_ВыводыСумма"},
                    {"template":"Макет1","parameter":"Отч_Вводы_Сумма","expression":"Отч_ВводыСумма"},
                    {"template":"Макет1","parameter":"Отч_Total_PNL","expression":"Отч_Total_PnL"},
                    {"template":"Макет1","parameter":"Отч_Выводы_Сумма","expression":"Отч_ВыводыСумма"},
                    {"template":"МакетДвижениеКапитала","parameter":"ДокументВводаВывода","expression":"ДокументВводаВывода"},
                    {"template":"МакетДвижениеКапитала","parameter":"ВидОперации","expression":"ВидОперации"},
                    {"template":"МакетДвижениеКапитала","parameter":"ОбъемОперации","expression":"ОбъемОперации"},
                    {"template":"МакетДвижениеКапитала","parameter":"МонетаОперации","expression":"МонетаОперации"}
                  ],
                  "groupBindings":[
                    {"action":"replace","groupName":"ГруппировкаДеталиОпераций","templateType":"Header","template":"Макет7",
                     "newGroupName":"ГруппировкаВидДвиженияКапитала","newTemplateType":"Header","newTemplate":"Макет7"},
                    {"action":"upsert","groupName":"ГруппировкаДеталиДвиженийКапитала","templateType":"Header","template":"МакетДвижениеКапитала"}
                  ]
                }
                """;
    }

    private static String schema() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:dcsat="http://v8.1c.ru/8.1/data-composition-system/area-template"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<template>
                \t\t<name>МакетА</name>
                \t\t<template xsi:type="dcsat:AreaTemplate">
                \t\t\t<dcsat:item xsi:type="dcsat:TableRow"><dcsat:tableCell><dcsat:appearance/></dcsat:tableCell></dcsat:item>
                \t\t</template>
                \t\t<parameter xsi:type="dcsat:ExpressionAreaTemplateParameter">
                \t\t\t<dcsat:name>ПараметрА</dcsat:name>
                \t\t\t<dcsat:expression>Старое</dcsat:expression>
                \t\t</parameter>
                \t</template>
                \t<template>
                \t\t<name>МакетБ</name>
                \t\t<template xsi:type="dcsat:AreaTemplate"><dcsat:item xsi:type="dcsat:TableRow"/></template>
                \t</template>
                \t<groupTemplate>
                \t\t<groupName>СтараяГруппа</groupName>
                \t\t<templateType>Header</templateType>
                \t\t<template>МакетА</template>
                \t</groupTemplate>
                \t<settingsVariant>
                \t\t<dcsset:name>Основной</dcsset:name>
                \t\t<dcsset:settings>
                \t\t\t<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>СтараяГруппа</dcsset:name></dcsset:item>
                \t\t\t<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>НоваяГруппа</dcsset:name></dcsset:item>
                \t\t</dcsset:settings>
                \t</settingsVariant>
                </DataCompositionSchema>
                """.replace("\n", "\r\n");
    }

    private static String areaTemplateBody(String xml, String templateName) {
        String outer = templateBlock(xml, templateName);
        int type = outer.indexOf("AreaTemplate");
        assertThat(type).isGreaterThanOrEqualTo(0);
        int start = outer.lastIndexOf('<', type);
        int end = matchingElementEnd(outer, start);
        return outer.substring(start, end);
    }

    private static String expectedExpression(String xml, String templateName,
                                             String parameterName, String newExpression) {
        int outerName = xml.indexOf("<name>" + templateName + "</name>");
        assertThat(outerName).isGreaterThanOrEqualTo(0);
        int outerStart = xml.lastIndexOf("<template>", outerName);
        int outerEnd = matchingElementEnd(xml, outerStart);
        String exactName = "<dcsat:name>" + parameterName + "</dcsat:name>";
        int name = xml.indexOf(exactName, outerStart);
        assertThat(name).isBetween(outerStart, outerEnd);
        int parameterStart = xml.lastIndexOf("<parameter", name);
        int parameterEnd = xml.indexOf("</parameter>", name) + "</parameter>".length();
        assertThat(parameterStart).isGreaterThan(outerStart);
        assertThat(parameterEnd).isLessThanOrEqualTo(outerEnd);
        String parameter = xml.substring(parameterStart, parameterEnd);
        String open = "<dcsat:expression>";
        String close = "</dcsat:expression>";
        int expression = parameter.indexOf(open);
        if (expression >= 0) {
            int valueStart = parameterStart + expression + open.length();
            int valueEnd = xml.indexOf(close, valueStart);
            return xml.substring(0, valueStart) + newExpression + xml.substring(valueEnd);
        }
        int insert = name + exactName.length();
        int lineStart = xml.lastIndexOf('\n', name) + 1;
        String indent = xml.substring(lineStart, name);
        String addition = "\r\n" + indent + open + newExpression + close;
        return xml.substring(0, insert) + addition + xml.substring(insert);
    }

    private static String expectedBindingGroup(String xml, String oldGroup, String type,
                                               String template, String newGroup) {
        String oldText = "<groupName>" + oldGroup + "</groupName>";
        int group = xml.indexOf(oldText);
        assertThat(group).isGreaterThanOrEqualTo(0);
        int start = xml.lastIndexOf("<groupTemplate>", group);
        int end = xml.indexOf("</groupTemplate>", group) + "</groupTemplate>".length();
        String block = xml.substring(start, end);
        assertThat(block).contains("<templateType>" + type + "</templateType>")
                .contains("<template>" + template + "</template>");
        String replaced = block.replace(oldText, "<groupName>" + newGroup + "</groupName>");
        return xml.substring(0, start) + replaced + xml.substring(end);
    }

    private static String expression(String xml, String templateName, String parameterName) {
        String outer = templateBlock(xml, templateName);
        int name = outer.indexOf("<dcsat:name>" + parameterName + "</dcsat:name>");
        assertThat(name).isGreaterThanOrEqualTo(0);
        int parameterStart = outer.lastIndexOf("<parameter", name);
        int parameterEnd = outer.indexOf("</parameter>", name) + "</parameter>".length();
        String parameter = outer.substring(parameterStart, parameterEnd);
        int start = parameter.indexOf("<dcsat:expression>");
        if (start < 0) return null;
        start += "<dcsat:expression>".length();
        int end = parameter.indexOf("</dcsat:expression>", start);
        return unescape(parameter.substring(start, end));
    }

    private static String templateBlock(String xml, String templateName) {
        int name = xml.indexOf("<name>" + templateName + "</name>");
        assertThat(name).isGreaterThanOrEqualTo(0);
        int start = xml.lastIndexOf("<template>", name);
        int end = matchingElementEnd(xml, start);
        return xml.substring(start, end);
    }

    private static int matchingElementEnd(String xml, int start) {
        int depth = 0;
        for (int pos = start; pos >= 0 && pos < xml.length();) {
            int open = xml.indexOf("<template", pos);
            int close = xml.indexOf("</template>", pos);
            if (close < 0) throw new AssertionError("Unclosed template");
            if (open >= 0 && open < close) {
                depth++;
                pos = xml.indexOf('>', open) + 1;
            } else {
                depth--;
                pos = close + "</template>".length();
                if (depth == 0) return pos;
            }
        }
        throw new AssertionError("Unbalanced template");
    }

    private static int bindingCount(String xml, String group, String type, String template) {
        int count = 0;
        for (String tag : List.of("groupTemplate", "groupHeaderTemplate")) {
            int from = 0;
            while (true) {
                int start = xml.indexOf("<" + tag + ">", from);
                if (start < 0) break;
                int end = xml.indexOf("</" + tag + ">", start);
                String block = xml.substring(start, end);
                if (block.contains("<groupName>" + group + "</groupName>")
                        && block.contains("<templateType>" + type + "</templateType>")
                        && block.contains("<template>" + template + "</template>")) count++;
                from = end + tag.length() + 3;
            }
        }
        return count;
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
        int offset = bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1]
                && bytes[2] == BOM[2] ? 3 : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /**
     * XG-88 проверяет immutable manual baseline c3fcb115: mutable production уже
     * перешёл на cc332cd0 и не может служить fixture исторической регрессии.
     */
    private static byte[] historicalFixture() throws Exception {
        Path project = locateProjectRoot();
        assertThat(project).as("GBIG PAM repository for immutable XG-88 fixture")
                .isNotNull().exists();
        Process process = new ProcessBuilder("git", "-C", project.toString(), "show",
                FIXTURE_COMMIT + ":" + FIXTURE_PATH)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        byte[] fixture = process.getInputStream().readAllBytes();
        byte[] error = process.getErrorStream().readAllBytes();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        assertThat(exited).as(new String(error, StandardCharsets.UTF_8)).isTrue();
        assertThat(process.exitValue()).as(new String(error, StandardCharsets.UTF_8)).isZero();
        return fixture;
    }

    private static Path locateProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !"repos".equals(current.getFileName() == null
                ? "" : current.getFileName().toString())) current = current.getParent();
        if (current == null) return null;
        return current.resolve("1C Projects/GBIG PAM");
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
//++agent TASK-174

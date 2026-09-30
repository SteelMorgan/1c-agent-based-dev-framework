package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [10.07.2026 18:25:00]
class SkdStructureUpsertXg75Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void upsertStructure_addsNestedGroupSelection_preservesSiblingsAndIsIdempotent() throws Exception {
        Path schema = writeSchema();
        Path payload = writePayload("""
                {
                  "variant": "Основной",
                  "dataSet": "Движения",
                  "items": [{
                    "kind": "group",
                    "name": "НоваяГруппа",
                    "groupItems": ["Вид"],
                    "children": [{
                      "kind": "group",
                      "name": "ДеталиГруппы",
                      "groupItems": ["details"],
                      "selection": ["Дата", "Сумма"]
                    }]
                  }]
                }
                """);
        byte[] before = Files.readAllBytes(schema);

        ProcessResult first = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(first.exitCode()).as(first.output()).isZero();
        byte[] after = Files.readAllBytes(schema);
        String xml = decodeBom(after);
        assertThat(after).startsWith(BOM).isNotEqualTo(before);
        assertThat(xml)
                .contains("<dcsset:name>НоваяГруппа</dcsset:name>")
                .contains("<dcsset:field>Вид</dcsset:field>")
                .contains("<dcsset:name>ДеталиГруппы</dcsset:name>")
                .contains("xsi:type=\"dcsset:GroupItemAuto\"")
                .doesNotContain("GroupItemDetails")
                .contains("<dcsset:field>Дата</dcsset:field>")
                .contains("<dcsset:field>Сумма</dcsset:field>")
                .contains("<dcsset:name>СоседняяГруппа</dcsset:name>")
                .contains("<v8:content>bare\nLF</v8:content>")
                .doesNotContain("<v8:content>bare\r\nLF</v8:content>");
        assertThat(xml.replace("<v8:content>bare\nLF</v8:content>", "")
                .replace("\r\n", ""))
                .doesNotContain("\n")
                .as("Designer CRLF сохраняется вне текстового content");

        ProcessResult second = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(after);

        Files.writeString(payload, """
                {
                  "variant": "Основной", "dataSet": "Движения",
                  "items": [{"kind":"group", "name":"НоваяГруппа",
                    "groupItems":["Дата"], "selection":["Сумма"]}]
                }
                """, StandardCharsets.UTF_8);
        ProcessResult update = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(update.exitCode()).as(update.output()).isZero();
        String updated = decodeBom(Files.readAllBytes(schema));
        assertThat(count(updated, "<dcsset:name>НоваяГруппа</dcsset:name>")).isOne();
        assertThat(updated).contains("<dcsset:field>Дата</dcsset:field>")
                .contains("<dcsset:name>ДеталиГруппы</dcsset:name>")
                .contains("<dcsset:name>СоседняяГруппа</dcsset:name>");
    }

    @Test
    void upsertStructure_updatesOnlyOwnedAspectsAndPreservesUnmanagedNodes() throws Exception {
        Path schema = writeLosslessUpdateSchema();
        String before = decodeBom(Files.readAllBytes(schema));
        String customOrder = extract(before, "\t\t\t\t<dcsset:order>", "\t\t\t\t</dcsset:order>\r\n");
        String filter = extract(before, "\t\t\t\t<dcsset:filter>", "\t\t\t\t</dcsset:filter>\r\n");
        String outputParameters = extract(before, "\t\t\t\t<dcsset:outputParameters>",
                "\t\t\t\t</dcsset:outputParameters>\r\n");
        String extensionNode = extract(before, "\t\t\t\t<dcsset:extensionNode",
                "\t\t\t\t</dcsset:extensionNode>\r\n");
        String nestedUnmanaged = extract(before, "\t\t\t\t\t<dcsset:userSettings>",
                "\t\t\t\t\t</dcsset:userSettings>\r\n");
        Path payload = writePayload("""
                {"variant":"Основной", "dataSet":"Движения", "items":[{
                  "kind":"group", "name":"СоседняяГруппа",
                  "groupItems":["Дата"], "selection":["Сумма"],
                  "children":[{"kind":"group", "name":"ВложеннаяГруппа",
                    "groupItems":["details"], "selection":["Дата"]}]
                }]}
                """);

        ProcessResult first = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(first.exitCode()).as(first.output()).isZero();
        byte[] afterBytes = Files.readAllBytes(schema);
        String after = decodeBom(afterBytes);
        assertThat(after)
                .contains(customOrder)
                .contains(filter)
                .contains(outputParameters)
                .contains(extensionNode)
                .contains(nestedUnmanaged)
                .contains("<dcsset:field>Дата</dcsset:field>")
                .contains("<dcsset:field>Сумма</dcsset:field>")
                .contains("xsi:type=\"dcsset:GroupItemAuto\"")
                .doesNotContain("GroupItemDetails")
                .doesNotContain("<dcsset:field>Вид</dcsset:field>");
        assertThat(count(after, "xsi:type=\"dcsset:OrderItemAuto\"")).isZero();

        ProcessResult second = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterBytes);
    }

    @Test
    void upsertStructure_rejectsInvalidPayloadsBeforeMutation() throws Exception {
        List<String> payloads = List.of(
                "{\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"group\",\"name\":\"X\"}]}",
                "{\"variant\":\"НетВарианта\",\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"group\",\"name\":\"X\"}]}",
                "{\"variant\":\"Основной\",\"dataSet\":\"НетНабора\",\"items\":[{\"kind\":\"group\",\"name\":\"X\"}]}",
                "{\"variant\":\"Основной\",\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"chart\",\"name\":\"X\"}]}",
                "{\"variant\":\"Основной\",\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"table\",\"name\":\"X\"}]}",
                "{\"variant\":\"Основной\",\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"group\",\"name\":\"X\"},{\"kind\":\"group\",\"name\":\"X\"}]}",
                "{\"variant\":\"Основной\",\"dataSet\":\"Движения\",\"items\":[{\"kind\":\"group\",\"name\":\"Валидная\"},{\"kind\":\"bad\",\"name\":\"Невалидная\"}]}"
        );
        for (int i = 0; i < payloads.size(); i++) {
            Path schema = writeSchema("invalid-" + i + ".xml");
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload(payloads.get(i));
            ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                    "--json", payload.toString());
            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(Files.readAllBytes(schema)).as(result.output()).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void upsertStructure_rejectsUnknownDatasetFieldBeforeMutation() throws Exception {
        Path schema = writeSchema();
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("""
                {"variant":"Основной", "dataSet":"Движения", "items":[
                  {"kind":"group", "name":"X", "groupItems":["НеизвестноеПоле"]}
                ]}
                """);
        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.output()).contains("НеизвестноеПоле");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    @Test
    void upsertStructure_rejectsNestedIdentityOwnedBySiblingBeforeMutation() throws Exception {
        Path schema = writeSchema();
        byte[] original = Files.readAllBytes(schema);
        String xml = decodeBom(original).replace(
                "<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>\r\n\t\t\t</dcsset:item>",
                "<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>\r\n"
                        + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                        + "\t\t\t\t\t<dcsset:name>ЗанятоеИмя</dcsset:name>\r\n"
                        + "\t\t\t\t</dcsset:item>\r\n\t\t\t</dcsset:item>");
        writeWithBom(schema, xml);
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("""
                {"variant":"Основной", "dataSet":"Движения", "items":[
                  {"kind":"group", "name":"НоваяГруппа", "children":[
                    {"kind":"group", "name":"ЗанятоеИмя"}
                  ]}
                ]}
                """);

        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).contains("ЗанятоеИмя");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertNoTempFiles();
    }

    private Path writeLosslessUpdateSchema() throws Exception {
        Path schema = writeSchema("lossless-update.xml");
        String xml = decodeBom(Files.readAllBytes(schema)).replace(
                "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                        + "\t\t\t\t<dcsset:name>СоседняяГруппа</dcsset:name>\r\n"
                        + "\t\t\t\t<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>\r\n"
                        + "\t\t\t</dcsset:item>\r\n",
                "\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                        + "\t\t\t\t<dcsset:name>СоседняяГруппа</dcsset:name>\r\n"
                        + "\t\t\t\t<dcsset:groupItems>\r\n"
                        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:GroupItemField\"><dcsset:field>Вид</dcsset:field></dcsset:item>\r\n"
                        + "\t\t\t\t</dcsset:groupItems>\r\n"
                        + "\t\t\t\t<dcsset:order>\r\n"
                        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:OrderItemField\"><dcsset:field>Сумма</dcsset:field><dcsset:orderType>Desc</dcsset:orderType></dcsset:item>\r\n"
                        + "\t\t\t\t</dcsset:order>\r\n"
                        + "\t\t\t\t<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemField\"><dcsset:field>Вид</dcsset:field></dcsset:item></dcsset:selection>\r\n"
                        + "\t\t\t\t<dcsset:filter>\r\n"
                        + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:FilterItemComparison\"><dcsset:use>false</dcsset:use></dcsset:item>\r\n"
                        + "\t\t\t\t</dcsset:filter>\r\n"
                        + "\t\t\t\t<dcsset:outputParameters>\r\n"
                        + "\t\t\t\t\t<dcsset:item><dcsset:parameter>KeepOutput</dcsset:parameter></dcsset:item>\r\n"
                        + "\t\t\t\t</dcsset:outputParameters>\r\n"
                        + "\t\t\t\t<dcsset:extensionNode data-test=\"keep-exact\">\r\n"
                        + "\t\t\t\t\t<dcsset:value>opaque</dcsset:value>\r\n"
                        + "\t\t\t\t</dcsset:extensionNode>\r\n"
                        + "\t\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                        + "\t\t\t\t\t<dcsset:name>ВложеннаяГруппа</dcsset:name>\r\n"
                        + "\t\t\t\t\t<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>\r\n"
                        + "\t\t\t\t\t<dcsset:userSettings>\r\n"
                        + "\t\t\t\t\t\t<dcsset:value>keep-nested</dcsset:value>\r\n"
                        + "\t\t\t\t\t</dcsset:userSettings>\r\n"
                        + "\t\t\t\t</dcsset:item>\r\n"
                        + "\t\t\t</dcsset:item>\r\n");
        writeWithBom(schema, xml);
        return schema;
    }

    private Path writeSchema() throws Exception {
        return writeSchema("Template.xml");
    }

    private Path writeSchema(String name) throws Exception {
        String lf = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                	<dataSet xsi:type="DataSetObject">
                		<name>Движения</name>
                		<field xsi:type="DataSetFieldField"><dataPath>Вид</dataPath><field>Вид</field></field>
                		<field xsi:type="DataSetFieldField"><dataPath>Дата</dataPath><field>Дата</field></field>
                		<field xsi:type="DataSetFieldField"><dataPath>Сумма</dataPath><field>Сумма</field></field>
                		<objectName>Движения</objectName>
                	</dataSet>
                	<template><name>РучнойМакет</name><template><v8:content>bare__BARE_LF__LF</v8:content></template></template>
                	<settingsVariant>
                		<dcsset:name>Основной</dcsset:name>
                		<dcsset:settings>
                			<dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemAuto"/></dcsset:selection>
                			<dcsset:item xsi:type="dcsset:StructureItemGroup">
                				<dcsset:name>СоседняяГруппа</dcsset:name>
                				<dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemAuto"/></dcsset:selection>
                			</dcsset:item>
                		</dcsset:settings>
                	</settingsVariant>
                </DataCompositionSchema>
                """;
        String crlf = lf.replace("\r\n", "\n").replace("\n", "\r\n")
                .replace("__BARE_LF__", "\n");
        byte[] body = crlf.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Path schema = tempDir.resolve(name);
        Files.write(schema, bytes);
        return schema;
    }

    private Path writePayload(String json) throws Exception {
        Path path = tempDir.resolve("payload-" + System.nanoTime() + ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private static void writeWithBom(Path path, String content) throws Exception {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Files.write(path, bytes);
    }

    private static String decodeBom(byte[] bytes) {
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    private static int count(String text, String token) {
        int count = 0;
        for (int i = 0; (i = text.indexOf(token, i)) >= 0; i += token.length()) count++;
        return count;
    }

    private static String extract(String text, String startToken, String endToken) {
        int start = text.indexOf(startToken);
        int end = text.indexOf(endToken, start);
        assertThat(start).isNotNegative();
        assertThat(end).isNotNegative();
        return text.substring(start, end + endToken.length());
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
        boolean done = process.waitFor(30, TimeUnit.SECONDS);
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

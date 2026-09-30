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

//++agent TASK-174 [10.07.2026 22:24:00]
class SkdStructureUpsertXg80Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void selectionTriState_preservesClearsAndReplacesOnlyDirectSelection() throws Exception {
        Path schema = writeSchema();
        byte[] original = Files.readAllBytes(schema);

        assertSuccess(schema, """
                {"variant":"Основной","dataSet":"Движения","items":[
                  {"kind":"group","name":"Капитал"}]}
                """);
        assertThat(Files.readAllBytes(schema)).isEqualTo(original);

        String clearPayload = """
                {"variant":"Основной","dataSet":"Движения","items":[
                  {"kind":"group","name":"Капитал","selection":[]}]}
                """;
        assertSuccess(schema, clearPayload);
        byte[] afterClear = Files.readAllBytes(schema);
        String capital = capital(decode(afterClear));
        assertThat(capital).doesNotContain("SelectedItemAuto")
                .contains("<dcsset:name>Детали</dcsset:name>")
                .contains("<dcsset:field>Сумма</dcsset:field>")
                .contains("<dcsset:order>")
                .contains("<dcsset:filter>");

        assertSuccess(schema, clearPayload);
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterClear);

        assertSuccess(schema, """
                {"variant":"Основной","dataSet":"Движения","items":[
                  {"kind":"group","name":"Капитал","selection":["Вид"]}]}
                """);
        String replaced = capital(decode(Files.readAllBytes(schema)));
        assertThat(replaced).contains("xsi:type=\"dcsset:SelectedItemField\"")
                .contains("<dcsset:field>Вид</dcsset:field>")
                .doesNotContain("SelectedItemAuto")
                .contains("<dcsset:name>Детали</dcsset:name>");
    }

    @Test
    void managedEmptyCollections_clearGroupItemsButPreserveChildren() throws Exception {
        Path schema = writeSchema();

        assertSuccess(schema, """
                {"variant":"Основной","dataSet":"Движения","items":[
                  {"kind":"group","name":"Капитал","groupItems":[],"children":[]}]}
                """);

        String capital = capital(decode(Files.readAllBytes(schema)));
        assertThat(capital).doesNotContain("<dcsset:groupItems>")
                .contains("<dcsset:name>Детали</dcsset:name>")
                .contains("SelectedItemAuto");
    }

    @Test
    void mixedInvalidPayload_isRejectedBeforeClearMutation() throws Exception {
        Path schema = writeSchema();
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("""
                {"variant":"Основной","dataSet":"Движения","items":[
                  {"kind":"group","name":"Капитал","selection":[]},
                  {"kind":"group","name":"Новая","selection":["НетПоля"]}]}
                """);

        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output()).contains("НетПоля");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        try (var files = Files.list(tempDir)) {
            assertThat(files.filter(path -> path.getFileName().toString().endsWith(".tmp")).toList())
                    .isEmpty();
        }
    }

    private void assertSuccess(Path schema, String json) throws Exception {
        Path payload = writePayload(json);
        ProcessResult result = runMain("skd", "upsert-structure", schema.toString(),
                "--json", payload.toString());
        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    private Path writeSchema() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Движения</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Вид</dataPath><field>Вид</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Сумма</dataPath><field>Сумма</field></field>
                \t\t<objectName>Движения</objectName>
                \t</dataSet>
                \t<settingsVariant>
                \t\t<dcsset:name>Основной</dcsset:name>
                \t\t<dcsset:settings>
                \t\t\t<dcsset:item xsi:type="dcsset:StructureItemGroup">
                \t\t\t\t<dcsset:name>Капитал</dcsset:name>
                \t\t\t\t<dcsset:groupItems><dcsset:item xsi:type="dcsset:GroupItemField"><dcsset:field>Вид</dcsset:field></dcsset:item></dcsset:groupItems>
                \t\t\t\t<dcsset:order><dcsset:item xsi:type="dcsset:OrderItemAuto"/></dcsset:order>
                \t\t\t\t<dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemAuto"/></dcsset:selection>
                \t\t\t\t<dcsset:filter><dcsset:item xsi:type="dcsset:FilterItemComparison"><dcsset:use>false</dcsset:use></dcsset:item></dcsset:filter>
                \t\t\t\t<dcsset:item xsi:type="dcsset:StructureItemGroup">
                \t\t\t\t\t<dcsset:name>Детали</dcsset:name>
                \t\t\t\t\t<dcsset:selection><dcsset:item xsi:type="dcsset:SelectedItemField"><dcsset:field>Сумма</dcsset:field></dcsset:item></dcsset:selection>
                \t\t\t\t</dcsset:item>
                \t\t\t</dcsset:item>
                \t\t</dcsset:settings>
                \t</settingsVariant>
                </DataCompositionSchema>
                """.replace("\r\n", "\n").replace("\n", "\r\n");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, bytes, 0, BOM.length);
        System.arraycopy(body, 0, bytes, BOM.length, body.length);
        Path schema = tempDir.resolve("Template.xml");
        Files.write(schema, bytes);
        return schema;
    }

    private Path writePayload(String json) throws Exception {
        Path path = tempDir.resolve("payload-" + System.nanoTime() + ".json");
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private static String decode(byte[] bytes) {
        return new String(bytes, BOM.length, bytes.length - BOM.length, StandardCharsets.UTF_8);
    }

    private static String capital(String text) {
        int start = text.indexOf("<dcsset:name>Капитал</dcsset:name>");
        int end = text.indexOf("</dcsset:item>\r\n\t\t\t</dcsset:item>", start);
        assertThat(start).isNotNegative();
        assertThat(end).isNotNegative();
        return text.substring(start, end);
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
//--agent TASK-174

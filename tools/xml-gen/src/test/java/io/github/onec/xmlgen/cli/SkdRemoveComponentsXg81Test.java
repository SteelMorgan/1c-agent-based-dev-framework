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

//++agent TASK-174 [10.07.2026 23:20:00]
class SkdRemoveComponentsXg81Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void removesTopLevelAndRecursiveStructureByExactPath() throws Exception {
        Path schema = writeSchema("structure.xml");
        Path payload = writePayload("structure.json", """
                {"structureItems":[
                  {"variant":"Основной","path":["Капитал"]},
                  {"variant":"Дополнительный","path":["Родитель","Дочерняя"]}
                ]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        String after = read(schema);
        assertThat(after).doesNotContain("<dcsset:name>Капитал</dcsset:name>")
                .doesNotContain("<dcsset:name>Дочерняя</dcsset:name>")
                .contains("<dcsset:name>Соседняя</dcsset:name>")
                .contains("<dcsset:name>Родитель</dcsset:name>")
                .contains("<v8:content>bare\nLF</v8:content>");
        assertDesignerLineEndings(after);
    }

    @Test
    void removesExactBindingsAndPreservesAreaTemplatesAndSiblings() throws Exception {
        Path schema = writeSchema("bindings.xml");
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("bindings.json", """
                {"groupTemplates":[
                  {"groupName":"Капитал","templateType":"Header","template":"ОбластьКапитала"},
                  {"groupName":"Капитал","templateType":"GroupHeader","template":"ЗаголовокКапитала"}
                ]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.output()).isZero();
        byte[] afterBytes = Files.readAllBytes(schema);
        String after = read(schema);
        assertThat(afterBytes).startsWith(BOM).isNotEqualTo(before);
        assertThat(count(after, "\t<template>\r\n")).isEqualTo(3);
        assertThat(after).contains("<name>ОбластьКапитала</name>")
                .contains("<name>ЗаголовокКапитала</name>")
                .contains("<name>СоседняяОбласть</name>")
                .contains("<groupName>Соседняя</groupName>")
                .doesNotContain("<groupName>Капитал</groupName>\r\n\t\t<templateType>Header")
                .doesNotContain("<groupName>Капитал</groupName>\r\n\t\t<template>ЗаголовокКапитала");
        assertThat(count(after, "<dataSet ")).isEqualTo(1);
        assertThat(count(after, "<settingsVariant>")).isEqualTo(4);
    }

    @Test
    void rejectsMissingAmbiguousAndMixedInvalidBatchWithoutMutation() throws Exception {
        List<String> payloads = List.of(
                "{\"structureItems\":[{\"variant\":\"Основной\",\"path\":[\"Нет\"]}]}",
                "{\"groupTemplates\":[{\"groupName\":\"Нет\",\"templateType\":\"Header\",\"template\":\"ОбластьКапитала\"}]}",
                "{\"structureItems\":[{\"variant\":\"Основной\",\"path\":[\"Капитал\"]}],"
                        + "\"groupTemplates\":[{\"groupName\":\"Нет\",\"templateType\":\"Header\",\"template\":\"ОбластьКапитала\"}]}",
                "{\"structureItems\":[{\"variant\":\"Дубликаты\",\"path\":[\"Одинаковая\"]}]}",
                "{\"structureItems\":[{\"variant\":\"Единственная\",\"path\":[\"Последняя\"]}]}"
        );
        for (int i = 0; i < payloads.size(); i++) {
            Path schema = writeSchema("invalid-" + i + ".xml");
            byte[] before = Files.readAllBytes(schema);
            Path payload = writePayload("invalid-" + i + ".json", payloads.get(i));

            ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                    "--json", payload.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(Files.readAllBytes(schema)).isEqualTo(before);
            assertNoTempFiles();
        }
    }

    @Test
    void repeatFailsByDefaultAndExplicitNoopKeepsBytes() throws Exception {
        Path schema = writeSchema("repeat.xml");
        Path payload = writePayload("repeat.json", """
                {"groupTemplates":[
                  {"groupName":"Капитал","templateType":"Header","template":"ОбластьКапитала"}
                ]}
                """);
        assertThat(runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString()).exitCode()).isZero();
        byte[] once = Files.readAllBytes(schema);

        ProcessResult strictRepeat = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());
        assertThat(strictRepeat.exitCode()).isNotZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);

        Files.writeString(payload, """
                {"ifAbsent":"noop","groupTemplates":[
                  {"groupName":"Капитал","templateType":"Header","template":"ОбластьКапитала"}
                ]}
                """, StandardCharsets.UTF_8);
        ProcessResult noopRepeat = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString());
        assertThat(noopRepeat.exitCode()).as(noopRepeat.output()).isZero();
        assertThat(noopRepeat.output()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    @Test
    void dryRunPreflightsButDoesNotWrite() throws Exception {
        Path schema = writeSchema("dry.xml");
        byte[] before = Files.readAllBytes(schema);
        Path payload = writePayload("dry.json", """
                {"structureItems":[{"variant":"Основной","path":["Капитал"]}]}
                """);

        ProcessResult result = runMain("skd", "remove-components", schema.toString(),
                "--json", payload.toString(), "--dry-run");

        assertThat(result.exitCode()).as(result.output()).isZero();
        assertThat(result.output()).contains("[DRY-RUN]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
    }

    private Path writeSchema(String name) throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:dcsset="http://v8.1c.ru/8.1/data-composition-system/settings"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                	<dataSet xsi:type="DataSetObject"><name>Данные</name><objectName>Данные</objectName></dataSet>
                	<template>
                		<name>ОбластьКапитала</name><template><v8:content>bare__LF__LF</v8:content></template>
                	</template>
                	<template>
                		<name>ЗаголовокКапитала</name><template><v8:content>header</v8:content></template>
                	</template>
                	<template>
                		<name>СоседняяОбласть</name><template><v8:content>sibling</v8:content></template>
                	</template>
                	<groupTemplate>
                		<groupName>Капитал</groupName>
                		<templateType>Header</templateType>
                		<template>ОбластьКапитала</template>
                	</groupTemplate>
                	<groupTemplate>
                		<groupName>Соседняя</groupName>
                		<templateType>Header</templateType>
                		<template>СоседняяОбласть</template>
                	</groupTemplate>
                	<groupHeaderTemplate>
                		<groupName>Капитал</groupName>
                		<template>ЗаголовокКапитала</template>
                	</groupHeaderTemplate>
                	<settingsVariant><name>Основной</name><settings>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Капитал</dcsset:name></dcsset:item>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Соседняя</dcsset:name></dcsset:item>
                	</settings></settingsVariant>
                	<settingsVariant><name>Дополнительный</name><settings>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Родитель</dcsset:name>
                			<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Дочерняя</dcsset:name></dcsset:item>
                		</dcsset:item>
                	</settings></settingsVariant>
                	<settingsVariant><name>Дубликаты</name><settings>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Одинаковая</dcsset:name></dcsset:item>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Одинаковая</dcsset:name></dcsset:item>
                	</settings></settingsVariant>
                	<settingsVariant><name>Единственная</name><settings>
                		<dcsset:item xsi:type="dcsset:StructureItemGroup"><dcsset:name>Последняя</dcsset:name></dcsset:item>
                	</settings></settingsVariant>
                </DataCompositionSchema>
                """.replace("\r\n", "\n").replace("\n", "\r\n")
                .replace("bare__LF__LF", "bare\nLF");
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
        byte[] bytes = Files.readAllBytes(path);
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    private static int count(String value, String token) {
        int count = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) count++;
        return count;
    }

    private static void assertDesignerLineEndings(String value) {
        assertThat(value.replace("bare\nLF", "").replace("\r\n", "")).doesNotContain("\n");
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

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String output() { return stdout + stderr; }
    }
}
//--agent TASK-174

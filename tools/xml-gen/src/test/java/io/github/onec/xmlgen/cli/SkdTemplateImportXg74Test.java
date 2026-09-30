package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [10.07.2026 17:19:29]
class SkdTemplateImportXg74Test {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void importTemplates_preservesExistingSchemaAndIsIdempotent() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        Path payload = tempDir.resolve("template-patch.json");
        Files.writeString(payload, """
                {
                  "templates": [{
                    "name": "НоваяОбласть",
                    "rows": [["Новая\\nСтрока", "{Значение}"]],
                    "parameters": [{"name": "Значение", "expression": "Значение"}]
                  }],
                  "groupTemplates": [{
                    "groupName": "Детали",
                    "templateType": "Header",
                    "template": "НоваяОбласть"
                  }]
                }
                """, StandardCharsets.UTF_8);

        byte[] before = Files.readAllBytes(schema);
        ProcessResult first = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.combinedOutput()).isZero();

        byte[] afterFirst = Files.readAllBytes(schema);
        String beforeText = new String(before, 3, before.length - 3, StandardCharsets.UTF_8);
        String text = new String(afterFirst, 3, afterFirst.length - 3, StandardCharsets.UTF_8);
        assertThat(afterFirst).startsWith(UTF8_BOM);
        assertThat(text)
                .contains("<name>СуществующаяОбласть</name>")
                .contains("<groupName>СуществующаяГруппа</groupName>")
                .contains("<dcsset:name>Основной</dcsset:name>")
                .contains("<query>ВЫБРАТЬ\r\n\t1 КАК Значение</query>")
                .contains("<name>НоваяОбласть</name>")
                .contains("<groupName>Детали</groupName>")
                .contains("<template>НоваяОбласть</template>")
                .contains("<v8:content>Новая\nСтрока</v8:content>")
                .doesNotContain("<v8:content>Новая\r\nСтрока</v8:content>");
        assertThat(count(text, "<name>НоваяОбласть</name>")).isEqualTo(1);
        assertThat(count(text, "<template>НоваяОбласть</template>")).isEqualTo(1);
        assertThat(text.substring(0, text.indexOf("\t<template>")))
                .as("datasets до первой области сохраняются байт-в-байт")
                .isEqualTo(beforeText.substring(0, beforeText.indexOf("\t<template>")));
        assertThat(topLevelBlock(text, "\t<template>\r\n\t\t<name>СуществующаяОбласть</name>\r\n",
                "\r\n\t</template>\r\n"))
                .as("существующая ручная AreaTemplate сохраняется байт-в-байт")
                .isEqualTo(topLevelBlock(beforeText,
                        "\t<template>\r\n\t\t<name>СуществующаяОбласть</name>\r\n",
                        "\r\n\t</template>\r\n"));
        assertThat(text.substring(text.indexOf("\t<settingsVariant>")))
                .as("settingsVariants после привязок сохраняются байт-в-байт")
                .isEqualTo(beforeText.substring(beforeText.indexOf("\t<settingsVariant>")));
        assertThat(afterFirst).isNotEqualTo(before);

        ProcessResult second = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(second.exitCode()).as(second.combinedOutput()).isZero();
        assertThat(Files.readAllBytes(schema))
                .as("повторный import не дублирует и не перезаписывает канон")
                .isEqualTo(afterFirst);

        Files.writeString(payload, """
                {
                  "templates": [{"name": "НоваяОбласть", "rows": [["Обновленная область"]]}],
                  "groupTemplates": [{
                    "groupName": "Детали", "templateType": "Header", "template": "НоваяОбласть"
                  }]
                }
                """, StandardCharsets.UTF_8);
        ProcessResult update = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(update.exitCode()).as(update.combinedOutput()).isZero();
        String updated = Files.readString(schema, StandardCharsets.UTF_8);
        assertThat(updated)
                .contains("Обновленная область")
                .doesNotContain("<v8:content>Новая\nСтрока</v8:content>");
        assertThat(count(updated, "<name>НоваяОбласть</name>")).isEqualTo(1);
        assertThat(count(updated, "<template>НоваяОбласть</template>")).isEqualTo(1);
    }

    @Test
    void importTemplates_rejectsUnknownTemplateTypeWithoutMutation() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        Path payload = tempDir.resolve("invalid-type.json");
        Files.writeString(payload, """
                {
                  "groupTemplates": [{
                    "groupName": "Детали",
                    "templateType": "DefinitelyNotAPlatformType",
                    "template": "СуществующаяОбласть"
                  }]
                }
                """, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(schema);

        ProcessResult result = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("Unknown SKD group templateType");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(hasImportTempFiles()).isFalse();
    }

    @Test
    void importTemplates_rejectsDanglingBindingWithoutMutation() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        Path payload = tempDir.resolve("dangling-binding-only.json");
        Files.writeString(payload, """
                {
                  "groupTemplates": [{
                    "groupName": "Детали",
                    "templateType": "Header",
                    "template": "AreaThatDoesNotExist"
                  }]
                }
                """, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(schema);

        ProcessResult result = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("references missing AreaTemplate: AreaThatDoesNotExist");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(hasImportTempFiles()).isFalse();
    }

    @Test
    void importTemplates_rejectsMixedPayloadWithDanglingBindingTransactionally() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        Path payload = tempDir.resolve("dangling-binding.json");
        Files.writeString(payload, """
                {
                  "templates": [{"name": "ВалиднаяНоваяОбласть", "rows": [["Не должна записаться"]]}],
                  "groupTemplates": [{
                    "groupName": "Детали",
                    "templateType": "Header",
                    "template": "AreaThatDoesNotExist"
                  }]
                }
                """, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(schema);

        ProcessResult result = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("references missing AreaTemplate: AreaThatDoesNotExist");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(hasImportTempFiles()).isFalse();
    }

    //++agent TASK-174 [10.07.2026 19:45:00]
    @Test
    void importTemplates_canonicalizesMixedBindingsRegardlessOfPayloadOrder() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        Path seed = tempDir.resolve("seed-group-header.json");
        Files.writeString(seed, """
                {
                  "templates": [{"name": "СуществующаяШапкаГруппы", "rows": [["Шапка группы"]]}],
                  "groupTemplates": [{
                    "groupName": "СуществующаяГруппаЗаголовка", "templateType": "GroupHeader",
                    "template": "СуществующаяШапкаГруппы"
                  }]
                }
                """, StandardCharsets.UTF_8);
        assertThat(runMain("skd", "import-templates", schema.toString(),
                "--json", seed.toString()).exitCode()).isZero();

        String seeded = Files.readString(schema, StandardCharsets.UTF_8);
        String dataSet = topLevelBlock(seeded, "\t<dataSet", "\r\n\t</dataSet>\r\n");
        String settings = seeded.substring(seeded.indexOf("\t<settingsVariant>"));
        int templateCount = count(seeded, "\t<template>\r\n");
        Path payload = tempDir.resolve("mixed-reverse-order.json");
        Files.writeString(payload, """
                {
                  "templates": [
                    {"name": "НоваяШапкаГруппы", "rows": [["Новая шапка группы"]]},
                    {"name": "НоваяОбычнаяШапка", "rows": [["Новая обычная шапка"]]}
                  ],
                  "groupTemplates": [
                    {"groupName": "НоваяГруппа", "templateType": "GroupHeader", "template": "НоваяШапкаГруппы"},
                    {"groupName": "НоваяДетальнаяГруппа", "templateType": "Header", "template": "НоваяОбычнаяШапка"}
                  ]
                }
                """, StandardCharsets.UTF_8);

        ProcessResult first = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(first.exitCode()).as(first.combinedOutput()).isZero();
        byte[] afterFirst = Files.readAllBytes(schema);
        String imported = Files.readString(schema, StandardCharsets.UTF_8);
        assertThat(imported.lastIndexOf("\t<groupTemplate>"))
                .isLessThan(imported.indexOf("\t<groupHeaderTemplate>"));
        assertThat(count(imported, "\t<groupTemplate>\r\n")).isEqualTo(2);
        assertThat(count(imported, "\t<groupHeaderTemplate>\r\n")).isEqualTo(2);
        assertThat(count(imported, "\t<template>\r\n")).isEqualTo(templateCount + 2);
        assertThat(topLevelBlock(imported, "\t<dataSet", "\r\n\t</dataSet>\r\n")).isEqualTo(dataSet);
        assertThat(imported.substring(imported.indexOf("\t<settingsVariant>"))).isEqualTo(settings);

        ProcessResult second = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());
        assertThat(second.exitCode()).as(second.combinedOutput()).isZero();
        assertThat(second.stdout()).contains("[NO-OP]");
        assertThat(Files.readAllBytes(schema)).isEqualTo(afterFirst);
    }

    @Test
    void importTemplates_relocatesUpdatedOrdinaryBindingFromInvalidGroupHeaderPrefix() throws Exception {
        Path schema = copyFixtureAsDesignerCrlf();
        String original = Files.readString(schema, StandardCharsets.UTF_8);
        int ordinaryStart = original.indexOf("\t<groupTemplate>");
        String invalidHeader = "\t<groupHeaderTemplate>\r\n"
                + "\t\t<groupName>ПредшествующаяГруппа</groupName>\r\n"
                + "\t\t<template>СуществующаяОбласть</template>\r\n"
                + "\t</groupHeaderTemplate>\r\n";
        Files.writeString(schema, original.substring(0, ordinaryStart) + invalidHeader
                + original.substring(ordinaryStart), StandardCharsets.UTF_8);
        Path payload = tempDir.resolve("relocate-update.json");
        Files.writeString(payload, """
                {"groupTemplates": [{
                  "groupName": "СуществующаяГруппа", "templateType": "Header",
                  "template": "СуществующаяОбласть"
                }]}
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("skd", "import-templates", schema.toString(),
                "--json", payload.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        String repaired = Files.readString(schema, StandardCharsets.UTF_8);
        assertThat(repaired.indexOf("\t<groupTemplate>"))
                .isLessThan(repaired.indexOf("\t<groupHeaderTemplate>"));
        assertThat(count(repaired, "<groupName>СуществующаяГруппа</groupName>")).isEqualTo(1);
    }
    //--agent TASK-174

    private Path copyFixtureAsDesignerCrlf() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/skd/xg74-existing-custom-layout.xml")) {
            assertThat(in).isNotNull();
            String fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String crlf = fixture.replace("\r\n", "\n").replace("\n", "\r\n")
                    .replace("Существующая\r\nгеометрия", "Существующая\nгеометрия");
            Path schema = tempDir.resolve("Template.xml");
            byte[] body = crlf.getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[UTF8_BOM.length + body.length];
            System.arraycopy(UTF8_BOM, 0, bytes, 0, UTF8_BOM.length);
            System.arraycopy(body, 0, bytes, UTF8_BOM.length, body.length);
            Files.write(schema, bytes);
            return schema;
        }
    }

    private static int count(String value, String token) {
        int result = 0;
        for (int from = 0; (from = value.indexOf(token, from)) >= 0; from += token.length()) {
            result++;
        }
        return result;
    }

    private static String topLevelBlock(String value, String startToken, String endToken) {
        int start = value.indexOf(startToken);
        int end = value.indexOf(endToken, start) + endToken.length();
        return value.substring(start, end);
    }

    private boolean hasImportTempFiles() throws Exception {
        try (var files = Files.list(tempDir)) {
            return files.anyMatch(path -> path.getFileName().toString().startsWith("Template.xml")
                    && path.getFileName().toString().endsWith(".tmp"));
        }
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return stdout + stderr;
        }
    }
}
//++agent TASK-174

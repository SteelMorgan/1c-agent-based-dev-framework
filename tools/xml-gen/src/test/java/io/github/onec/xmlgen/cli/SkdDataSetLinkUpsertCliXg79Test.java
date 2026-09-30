package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 [10.07.2026 22:15:00]
class SkdDataSetLinkUpsertCliXg79Test {

    @TempDir
    Path tempDir;

    @Test
    void commandIsAtomicAndRepeatedPayloadIsByteNoOp() throws Exception {
        Path schema = tempDir.resolve("Template.xml");
        Files.writeString(schema, schema(), StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(schema);
        Path invalid = tempDir.resolve("invalid.json");
        Files.writeString(invalid, """
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"НетПоля","destinationExpression":"Портфель"}]}]}
                """, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Commands.execute("skd", new String[]{
                "upsert-dataset-link", schema.toString(), "--json", invalid.toString()}))
                .hasMessageContaining("Unknown source field");
        assertThat(Files.readAllBytes(schema)).isEqualTo(before);
        assertThat(Files.list(tempDir).map(path -> path.getFileName().toString())
                .noneMatch(name -> name.endsWith(".tmp"))).isTrue();

        Path valid = tempDir.resolve("valid.json");
        Files.writeString(valid, """
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Портфель","destinationExpression":"Портфель"},
                  {"sourceExpression":"Аккаунт","destinationExpression":"Аккаунт"}]}]}
                """, StandardCharsets.UTF_8);
        Commands.execute("skd", new String[]{
                "upsert-dataset-link", schema.toString(), "--json", valid.toString()});
        byte[] first = Files.readAllBytes(schema);
        assertThat(first).isNotEqualTo(before);

        Path mixed = tempDir.resolve("mixed-invalid.json");
        Files.writeString(mixed, """
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Дата","destinationExpression":"Дата"},
                  {"sourceExpression":"НетПоля","destinationExpression":"Дата"}]}]}
                """, StandardCharsets.UTF_8);
        assertThatThrownBy(() -> Commands.execute("skd", new String[]{
                "upsert-dataset-link", schema.toString(), "--json", mixed.toString()}))
                .hasMessageContaining("Unknown source field");
        assertThat(Files.readAllBytes(schema)).isEqualTo(first);

        Commands.execute("skd", new String[]{
                "upsert-dataset-link", schema.toString(), "--json", valid.toString()});
        assertThat(Files.readAllBytes(schema)).isEqualTo(first);
    }

    private static String schema() {
        return """
                \uFEFF<?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Источник</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Портфель</dataPath><field>Портфель</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Аккаунт</dataPath><field>Аккаунт</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Дата</dataPath><field>Дата</field></field>
                \t\t<objectName>Источник</objectName>
                \t</dataSet>
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Назначение</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Портфель</dataPath><field>Портфель</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Аккаунт</dataPath><field>Аккаунт</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Дата</dataPath><field>Дата</field></field>
                \t\t<objectName>Назначение</objectName>
                \t</dataSet>
                </DataCompositionSchema>
                """.replace("\n", "\r\n");
    }
}
//--agent TASK-174

package io.github.onec.xmlgen.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdDataSetLinkUpsertDsl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 [10.07.2026 22:00:00]
class SkdDataSetLinkUpsertXg79Test {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SkdDataSetLinkUpsertEditor editor = new SkdDataSetLinkUpsertEditor();

    @Test
    void addUpdateNoOpPreserveSiblingsAndDesignerLineEndings() throws Exception {
        String original = schema(false);
        String siblingDataSet = block(original, "\t<dataSet xsi:type=\"DataSetObject\">\r\n\t\t<name>Сосед</name>", "\t</dataSet>\r\n");
        String siblingLink = block(original, "\t<dataSetLink>\r\n\t\t<sourceDataSet>Источник</sourceDataSet>\r\n\t\t<destinationDataSet>Сосед</destinationDataSet>", "\t</dataSetLink>\r\n");
        String tail = original.substring(original.indexOf("\t<template>"));

        SkdDataSetLinkUpsertEditor.Result added = editor.apply(original, payload("Портфель", "Аккаунт"));
        assertThat(added.changed()).isTrue();
        assertThat(added.content()).startsWith("\uFEFF");
        assertThat(added.content()).contains("<v8:content>bare\nLF</v8:content>");
        assertThat(added.content()).contains(siblingDataSet).contains(siblingLink).endsWith(tail);
        assertThat(added.content()).containsSubsequence(
                "<sourceExpression>Портфель</sourceExpression>",
                "<destinationExpression>Портфель</destinationExpression>",
                "<sourceExpression>Аккаунт</sourceExpression>",
                "<destinationExpression>Аккаунт</destinationExpression>");
        assertThat(count(added.content(), "<sourceDataSet>Источник</sourceDataSet>")).isEqualTo(3);

        SkdDataSetLinkUpsertEditor.Result noOp = editor.apply(added.content(), payload("Портфель", "Аккаунт"));
        assertThat(noOp.changed()).isFalse();
        assertThat(noOp.content()).isEqualTo(added.content());

        String accountMapping = block(added.content(),
                "\t<dataSetLink>\r\n\t\t<sourceDataSet>Источник</sourceDataSet>\r\n"
                        + "\t\t<destinationDataSet>Назначение</destinationDataSet>\r\n"
                        + "\t\t<sourceExpression>Аккаунт</sourceExpression>",
                "\t</dataSetLink>\r\n");
        SkdDataSetLinkUpsertEditor.Result updated = editor.apply(added.content(), payloadJson("""
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Портфель","destinationExpression":"ПортфельОбновленный"}]}]}
                """));
        assertThat(updated.changed()).isTrue();
        assertThat(count(updated.content(), "<sourceDataSet>Источник</sourceDataSet>")).isEqualTo(3);
        assertThat(updated.content())
                .contains("<sourceExpression>Портфель</sourceExpression>\r\n"
                        + "\t\t<destinationExpression>ПортфельОбновленный</destinationExpression>")
                .contains(accountMapping).contains(siblingDataSet).contains(siblingLink).endsWith(tail);

        SkdDataSetLinkUpsertEditor.Result partialNoOp = editor.apply(updated.content(), payloadJson("""
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Портфель","destinationExpression":"ПортфельОбновленный"}]}]}
                """));
        assertThat(partialNoOp.changed()).isFalse();
        assertThat(partialNoOp.content()).isEqualTo(updated.content());

        SkdDataSetLinkUpsertEditor.Result third = editor.apply(updated.content(), payload("Дата"));
        assertThat(third.changed()).isTrue();
        assertThat(count(third.content(), "<sourceDataSet>Источник</sourceDataSet>")).isEqualTo(4);
        assertThat(third.content()).contains(accountMapping)
                .contains("<sourceExpression>Дата</sourceExpression>")
                .contains("<destinationExpression>Дата</destinationExpression>");
    }

    @Test
    void rejectsInvalidPayloadBeforeMutation() throws Exception {
        String original = schema(false);
        assertThatThrownBy(() -> editor.apply(original, payloadJson("""
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"НетПоля","destinationExpression":"Портфель"}]}]}
                """))).hasMessageContaining("Unknown source field");
        assertThatThrownBy(() -> editor.apply(original, payloadJson("""
                {"links":[{"source":"Источник","destination":"Источник","mappings":[
                  {"sourceExpression":"Портфель","destinationExpression":"Портфель"}]}]}
                """))).hasMessageContaining("Self-link");
        assertThatThrownBy(() -> editor.apply(original, payloadJson("""
                {"links":[
                  {"source":"Источник","destination":"Назначение","mappings":[{"sourceExpression":"Портфель","destinationExpression":"Портфель"}]},
                  {"source":"Источник","destination":"Назначение","mappings":[{"sourceExpression":"Аккаунт","destinationExpression":"Аккаунт"}]}
                ]}
                """))).hasMessageContaining("Duplicate link identity");
        assertThatThrownBy(() -> editor.apply(original, payloadJson("""
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Портфель","destinationExpression":"Портфель"},
                  {"sourceExpression":"Портфель","destinationExpression":"Портфель"}]}]}
                """))).hasMessageContaining("Duplicate mapping");
        String seeded = editor.apply(original, payload("Портфель", "Аккаунт")).content();
        assertThatThrownBy(() -> editor.apply(seeded, payloadJson("""
                {"links":[{"source":"Источник","destination":"Назначение","mappings":[
                  {"sourceExpression":"Дата","destinationExpression":"Дата"},
                  {"sourceExpression":"НетПоля","destinationExpression":"Дата"}]}]}
                """))).hasMessageContaining("Unknown source field");
        assertThat(seeded).isEqualTo(editor.apply(original, payload("Портфель", "Аккаунт")).content());
        assertThat(original).isEqualTo(schema(false));
    }

    @Test
    void preservesBareLfSchema() throws Exception {
        String original = schema(true);
        String result = editor.apply(original, payload("Портфель")).content();
        assertThat(result).doesNotContain("\r\n");
        assertThat(result).contains("\n\t<dataSetLink>\n");
    }

    private SkdDataSetLinkUpsertDsl payload(String... fields) throws Exception {
        StringBuilder mappings = new StringBuilder();
        for (String field : fields) {
            if (!mappings.isEmpty()) mappings.append(',');
            mappings.append("{\"sourceExpression\":\"").append(field)
                    .append("\",\"destinationExpression\":\"").append(field).append("\"}");
        }
        return payloadJson("{\"links\":[{\"source\":\"Источник\",\"destination\":\"Назначение\",\"mappings\":["
                + mappings + "]}]}");
    }

    private SkdDataSetLinkUpsertDsl payloadJson(String json) throws Exception {
        return mapper.readValue(json, SkdDataSetLinkUpsertDsl.class);
    }

    private static String schema(boolean lfOnly) {
        String lf = """
                \uFEFF<?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema" xmlns:v8="http://v8.1c.ru/8.1/data/core" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
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
                \t\t<field xsi:type="DataSetFieldField"><dataPath>ПортфельОбновленный</dataPath><field>ПортфельОбновленный</field></field>
                \t\t<objectName>Назначение</objectName>
                \t</dataSet>
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Сосед</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Портфель</dataPath><field>Портфель</field></field>
                \t\t<objectName>Сосед</objectName>
                \t</dataSet>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Сосед</destinationDataSet>
                \t\t<sourceExpression>Портфель</sourceExpression>
                \t\t<destinationExpression>Портфель</destinationExpression>
                \t</dataSetLink>
                \t<template><name>РучнойМакет</name><template><v8:content>bare
                LF</v8:content></template></template>
                \t<settingsVariant><settings/></settingsVariant>
                </DataCompositionSchema>
                """;
        return lfOnly ? lf : lf.replace("\n", "\r\n").replace("bare\r\nLF", "bare\nLF");
    }

    private static String block(String text, String start, String end) {
        int from = text.indexOf(start);
        int to = text.indexOf(end, from) + end.length();
        return text.substring(from, to);
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }
}
//--agent TASK-174

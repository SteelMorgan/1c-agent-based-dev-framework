package io.github.onec.xmlgen.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdRemoveComponentsDsl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 [10.07.2026 23:30:00]
class SkdRemoveDataSetLinksXg82Test {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SkdRemoveComponentsEditor editor = new SkdRemoveComponentsEditor();

    @Test
    void removesOneExactMappingAndPreservesSameEndpointSiblingByteForByte() throws Exception {
        String original = schema();
        String account = link(original, "Аккаунт", "Аккаунт", null);

        SkdRemoveComponentsEditor.Result result = editor.apply(original, payload("""
                {"dataSetLinks":[{"sourceDataSet":"Источник","destinationDataSet":"Назначение",
                  "sourceExpression":"Портфель","destinationExpression":"Портфель"}]}
                """));

        assertThat(result.changed()).isTrue();
        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.content()).doesNotContain("<sourceExpression>Портфель</sourceExpression>")
                .contains(account).contains("<sourceExpression>Дата</sourceExpression>")
                .contains("<v8:content>bare\nLF</v8:content>")
                .startsWith("\uFEFF");
        assertDesignerCrlf(result.content());
    }

    @Test
    void removesBothMappingsInOnePreflightedBatch() throws Exception {
        SkdRemoveComponentsEditor.Result result = editor.apply(schema(), payload("""
                {"dataSetLinks":[
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Портфель"},
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Аккаунт"}
                ]}
                """));

        assertThat(result.removed()).isEqualTo(2);
        assertThat(result.content()).doesNotContain("<sourceExpression>Портфель</sourceExpression>")
                .doesNotContain("<sourceExpression>Аккаунт</sourceExpression>")
                .contains("<sourceExpression>Дата</sourceExpression>");
    }

    @Test
    void strictMissingFailsAndNoopMissingDoesNotRewrite() throws Exception {
        String original = schema();
        SkdRemoveComponentsDsl missing = payload("""
                {"dataSetLinks":[{"sourceDataSet":"Источник","destinationDataSet":"Назначение",
                  "sourceExpression":"Нет"}]}
                """);
        assertThatThrownBy(() -> editor.apply(original, missing)).hasMessageContaining("not found");

        SkdRemoveComponentsEditor.Result noop = editor.apply(original, payload("""
                {"ifAbsent":"noop","dataSetLinks":[{"sourceDataSet":"Источник",
                  "destinationDataSet":"Назначение","sourceExpression":"Нет"}]}
                """));
        assertThat(noop.changed()).isFalse();
        assertThat(noop.removed()).isZero();
        assertThat(noop.content()).isSameAs(original);
    }

    @Test
    void rejectsDuplicateAmbiguousAndMixedInvalidWithoutPartialResult() throws Exception {
        String original = schema();
        assertThatThrownBy(() -> editor.apply(original, payload("""
                {"dataSetLinks":[
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Портфель"},
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Портфель"}
                ]}
                """))).hasMessageContaining("Duplicate dataSetLink selector");
        assertThatThrownBy(() -> editor.apply(original, payload("""
                {"dataSetLinks":[{"sourceDataSet":"Источник","destinationDataSet":"Назначение",
                  "sourceExpression":"Ключ"}]}
                """))).hasMessageContaining("Ambiguous dataSetLink mapping");
        assertThatThrownBy(() -> editor.apply(original, payload("""
                {"dataSetLinks":[
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Портфель"},
                  {"sourceDataSet":"Источник","destinationDataSet":"Назначение","sourceExpression":"Нет"}
                ]}
                """))).hasMessageContaining("not found");
        assertThat(original).isEqualTo(schema());
    }

    @Test
    void optionalParameterParticipatesInMappingIdentity() throws Exception {
        String original = schema();
        SkdRemoveComponentsEditor.Result result = editor.apply(original, payload("""
                {"dataSetLinks":[{"sourceDataSet":"Источник","destinationDataSet":"Назначение",
                  "sourceExpression":"КлючП","parameter":"ПараметрКлюча"}]}
                """));

        assertThat(result.removed()).isEqualTo(1);
        assertThat(result.content()).doesNotContain("<parameter>ПараметрКлюча</parameter>")
                .contains("<sourceExpression>КлючП</sourceExpression>\r\n"
                        + "\t\t<destinationExpression>БезПараметра</destinationExpression>");
    }

    private SkdRemoveComponentsDsl payload(String json) throws Exception {
        return mapper.readValue(json, SkdRemoveComponentsDsl.class);
    }

    private static String schema() {
        return """
                \uFEFF<?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema" xmlns:v8="http://v8.1c.ru/8.1/data/core">
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>Портфель</sourceExpression>
                \t\t<destinationExpression>Портфель</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>Аккаунт</sourceExpression>
                \t\t<destinationExpression>Аккаунт</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Сосед</destinationDataSet>
                \t\t<sourceExpression>Дата</sourceExpression>
                \t\t<destinationExpression>Дата</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>Ключ</sourceExpression>
                \t\t<destinationExpression>Ключ1</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>Ключ</sourceExpression>
                \t\t<destinationExpression>Ключ2</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>КлючП</sourceExpression>
                \t\t<destinationExpression>БезПараметра</destinationExpression>
                \t</dataSetLink>
                \t<dataSetLink>
                \t\t<sourceDataSet>Источник</sourceDataSet>
                \t\t<destinationDataSet>Назначение</destinationDataSet>
                \t\t<sourceExpression>КлючП</sourceExpression>
                \t\t<destinationExpression>СПараметром</destinationExpression>
                \t\t<parameter>ПараметрКлюча</parameter>
                \t</dataSetLink>
                \t<template><template><v8:content>bare
                LF</v8:content></template></template>
                </DataCompositionSchema>
                """.replace("\r\n", "\n").replace("\n", "\r\n").replace("bare\r\nLF", "bare\nLF");
    }

    private static String link(String xml, String sourceExpression, String destinationExpression, String parameter) {
        String start = "\t<dataSetLink>\r\n\t\t<sourceDataSet>Источник</sourceDataSet>\r\n"
                + "\t\t<destinationDataSet>Назначение</destinationDataSet>\r\n"
                + "\t\t<sourceExpression>" + sourceExpression + "</sourceExpression>\r\n"
                + "\t\t<destinationExpression>" + destinationExpression + "</destinationExpression>\r\n";
        int from = xml.indexOf(start);
        int to = xml.indexOf("\t</dataSetLink>\r\n", from) + "\t</dataSetLink>\r\n".length();
        return xml.substring(from, to);
    }

    private static void assertDesignerCrlf(String xml) {
        assertThat(xml.replace("bare\nLF", "").replace("\r\n", "")).doesNotContain("\n");
    }
}
//--agent TASK-174

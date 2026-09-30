package io.github.onec.xmlgen.writer;

//++agent TASK-174 [14.07.2026 05:55:37] XG-102

import io.github.onec.xmlgen.validator.MetaValidator;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MetaFieldIndexingXg102Test {

    @TempDir
    Path tempDir;

    @Test
    void acceptedIndexingAliasIsEmittedForEveryRegisterFieldKindAndSubtype() throws Exception {
        for (RegisterFixture fixture : registerFixtures()) {
            Path xml = compile(fixture.id(), fixture.type(), fixture.extra(), """
                    "dimensions":["Ключ: String(64) | indexing"],
                    "resources":[{"name":"Сумма","type":"Number(15,2)","indexing":true}],
                    "attributes":[{"name":"Идентификатор","type":"String(64)","indexing":"Index"}]
                    """);
            String content = Files.readString(xml, StandardCharsets.UTF_8);

            assertFieldIndexing(content, "Dimension", "Ключ", "Index");
            //**agent XG-62 [28.09.2026 21:00:00] канон Designer 8.3.27: Indexing у Resource есть
            // только у регистра сведений; у накопления/бухгалтерии/расчёта - XDTO-отказ Designer.
            //assertFieldIndexing(content, "Resource", "Сумма", "Index");
            if ("InformationRegister".equals(fixture.type())) {
                assertFieldIndexing(content, "Resource", "Сумма", "Index");
                assertCanonicalIndexingOrder(content, "Resource", "Сумма");
            } else {
                int at = content.indexOf("<Name>Сумма</Name>");
                assertThat(content.substring(at, content.indexOf("</Resource>", at)))
                        .as(fixture.type()).doesNotContain("<Indexing>");
            }
            //**agent XG-62
            assertFieldIndexing(content, "Attribute", "Идентификатор", "Index");
            assertCanonicalIndexingOrder(content, "Dimension", "Ключ");
            assertCanonicalIndexingOrder(content, "Attribute", "Идентификатор");
            assertThat(validationErrors(xml)).as(fixture.type()).isEmpty();
        }
    }

    @Test
    void omittedAndExplicitFalseIndexingEmitDontIndex() throws Exception {
        Path xml = compile("defaults", "InformationRegister",
                "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\"", """
                        "dimensions":["БезФлага: String(20)"],
                        "resources":[{"name":"ЯвноЛожь","type":"Number(10,0)","indexing":false}],
                        "attributes":[{"name":"ЯвноEnum","type":"String(20)","indexing":"DontIndex"}]
                        """);
        String content = Files.readString(xml, StandardCharsets.UTF_8);

        assertFieldIndexing(content, "Dimension", "БезФлага", "DontIndex");
        assertFieldIndexing(content, "Resource", "ЯвноЛожь", "DontIndex");
        assertFieldIndexing(content, "Attribute", "ЯвноEnum", "DontIndex");
        assertThat(validationErrors(xml)).isEmpty();
    }

    @Test
    void attributeSupportsAdditionalOrderButDimensionAndResourceFailClosed() throws Exception {
        Path xml = compile("additional", "InformationRegister",
                "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\"", """
                        "attributes":["Порядок: String(20) | indexadditional"]
                        """);
        assertFieldIndexing(Files.readString(xml, StandardCharsets.UTF_8),
                "Attribute", "Порядок", "IndexWithAdditionalOrder");

        for (String fieldKind : List.of("dimensions", "resources")) {
            String field = "dimensions".equals(fieldKind)
                    ? "\"Ключ: String(20) | indexadditional\""
                    : "\"Сумма: Number(10,0) | indexadditional\"";
            Path root = tempDir.resolve("reject-additional-" + fieldKind);
            Files.createDirectories(root);
            Path configuration = writeMinimalConfiguration(root);
            String before = sha256(configuration);
            Path json = tempDir.resolve("reject-additional-" + fieldKind + ".json");
            Files.writeString(json, "{\"type\":\"InformationRegister\",\"name\":\"Probe\",\""
                    + fieldKind + "\":[" + field + "]}", StandardCharsets.UTF_8);

            assertThatThrownBy(() -> new MetaWriter().compile(json, root))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("INVALID_FIELD_INDEXING")
                    .hasMessageContaining("IndexWithAdditionalOrder");
            assertThat(root.resolve("InformationRegisters")).doesNotExist();
            assertThat(sha256(configuration)).isEqualTo(before);
        }
    }

    @Test
    void invalidOrUnapplicableIndexingFailsBeforeOutputAndConfigurationMutation() throws Exception {
        assertAtomicIndexingFailure("garbage", """
                {"type":"InformationRegister","name":"Probe",
                 "attributes":[{"name":"Ключ","type":"String(20)","indexing":"Fast"}]}
                """, "Fast");
        assertAtomicIndexingFailure("non-storable", """
                {"type":"DataProcessor","name":"Probe",
                 "attributes":[{"name":"Ключ","type":"String(20)","indexing":true}]}
                """, "not applicable");
        assertAtomicIndexingFailure("conflict", """
                {"type":"InformationRegister","name":"Probe",
                 "attributes":["Ключ: String(20) | index, indexadditional"]}
                """, "conflicting");
    }

    @Test
    void structurallyRepeatedCompileIsDeterministicAndAcceptedCommissionFieldsAreIndexed() throws Exception {
        String fields = """
                "dimensions":[
                  "Биржа: String(20) | indexing","Аккаунт: String(20) | indexing",
                  "КлючИдентичности: String(64) | indexing","Портфель: String(20) | indexing"],
                "attributes":["ИдентификаторЭффекта: String(64) | indexing"]
                """;
        Path first = compile("commission-a", "InformationRegister",
                "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\"", fields);
        Path second = compile("commission-b", "InformationRegister",
                "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\"", fields);
        String firstXml = Files.readString(first, StandardCharsets.UTF_8);
        String secondXml = Files.readString(second, StandardCharsets.UTF_8);

        for (String name : List.of("Биржа", "Аккаунт", "КлючИдентичности", "Портфель")) {
            assertFieldIndexing(firstXml, "Dimension", name, "Index");
        }
        assertFieldIndexing(firstXml, "Attribute", "ИдентификаторЭффекта", "Index");
        assertThat(normalizeGeneratedIdentity(firstXml))
                .isEqualTo(normalizeGeneratedIdentity(secondXml));
    }

    @Test
    void validatorRejectsInvalidAndMissingRegisterFieldIndexing() throws Exception {
        Path valid = compile("validate", "InformationRegister",
                "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\"", """
                        "dimensions":["Ключ: String(20) | indexing"]
                        """);
        String content = Files.readString(valid, StandardCharsets.UTF_8);
        String beforeValidation = sha256(valid);
        assertThat(validationErrors(valid)).isEmpty();
        assertThat(sha256(valid)).isEqualTo(beforeValidation);

        Path invalid = tempDir.resolve("invalid-indexing.xml");
        Files.writeString(invalid, content.replaceFirst("<Indexing>Index</Indexing>",
                "<Indexing>Garbage</Indexing>"), StandardCharsets.UTF_8);
        assertThat(validationErrors(invalid)).anyMatch(message -> message.contains("INVALID_FIELD_INDEXING")
                && message.contains("Garbage") && message.contains("Dimension[Ключ]"));

        Path missing = tempDir.resolve("missing-indexing.xml");
        Files.writeString(missing, content.replaceFirst("<Indexing>Index</Indexing>", ""),
                StandardCharsets.UTF_8);
        assertThat(validationErrors(missing)).anyMatch(message -> message.contains("INVALID_FIELD_INDEXING")
                && message.contains("required") && message.contains("Dimension[Ключ]"));
    }

    private List<RegisterFixture> registerFixtures() {
        return List.of(
                new RegisterFixture("info", "InformationRegister",
                        "\"periodicity\":\"Nonperiodical\",\"writeMode\":\"Independent\""),
                new RegisterFixture("accum", "AccumulationRegister", "\"registerType\":\"Turnovers\""),
                new RegisterFixture("accounting", "AccountingRegister",
                        "\"chartOfAccounts\":\"ChartOfAccounts.Probe\",\"correspondence\":false"),
                new RegisterFixture("calculation", "CalculationRegister",
                        "\"chartOfCalculationTypes\":\"ChartOfCalculationTypes.Probe\""));
    }

    private Path compile(String id, String type, String extra, String fields) throws Exception {
        Path root = tempDir.resolve(id);
        Files.createDirectories(root);
        writeMinimalConfiguration(root);
        String commaExtra = extra.isBlank() ? "" : "," + extra;
        String commaFields = fields.isBlank() ? "" : "," + fields;
        Path json = tempDir.resolve(id + ".json");
        Files.writeString(json, "{\"type\":\"" + type + "\",\"name\":\"Probe\""
                + commaExtra + commaFields + "}", StandardCharsets.UTF_8);
        new MetaWriter().compile(json, root);
        return root.resolve(directory(type)).resolve("Probe.xml");
    }

    private String directory(String type) {
        return switch (type) {
            case "InformationRegister" -> "InformationRegisters";
            case "AccumulationRegister" -> "AccumulationRegisters";
            case "AccountingRegister" -> "AccountingRegisters";
            case "CalculationRegister" -> "CalculationRegisters";
            default -> throw new IllegalArgumentException(type);
        };
    }

    private void assertAtomicIndexingFailure(String id, String body, String message) throws Exception {
        Path root = tempDir.resolve("reject-" + id);
        Files.createDirectories(root);
        Path configuration = writeMinimalConfiguration(root);
        String before = sha256(configuration);
        Path json = tempDir.resolve("reject-" + id + ".json");
        Files.writeString(json, body, StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new MetaWriter().compile(json, root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVALID_FIELD_INDEXING")
                .hasMessageContaining(message);
        assertThat(root.resolve("InformationRegisters")).doesNotExist();
        assertThat(root.resolve("DataProcessors")).doesNotExist();
        assertThat(sha256(configuration)).isEqualTo(before);
    }

    private Path writeMinimalConfiguration(Path root) throws Exception {
        Path configuration = root.resolve("Configuration.xml");
        Files.writeString(configuration, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Configuration uuid="11111111-1111-1111-1111-111111111111">
                    <Properties><Name>Test</Name></Properties><ChildObjects/>
                  </Configuration>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        return configuration;
    }

    private void assertFieldIndexing(String xml, String kind, String name, String expected) {
        String block = fieldBlock(xml, kind, name);
        assertThat(block).contains("<Indexing>" + expected + "</Indexing>");
    }

    private void assertCanonicalIndexingOrder(String xml, String kind, String name) {
        String block = fieldBlock(xml, kind, name);
        assertThat(block.indexOf("<Indexing>")).isGreaterThan(block.indexOf("<ChoiceHistoryOnInput>"));
        assertThat(block.indexOf("<Indexing>")).isLessThan(block.indexOf("<FullTextSearch>"));
    }

    private String fieldBlock(String xml, String kind, String name) {
        int nameAt = xml.indexOf("<Name>" + name + "</Name>");
        assertThat(nameAt).as(kind + " " + name).isNotNegative();
        int start = xml.lastIndexOf("<" + kind + " uuid=", nameAt);
        int end = xml.indexOf("</" + kind + ">", nameAt);
        assertThat(start).isNotNegative();
        assertThat(end).isGreaterThan(nameAt);
        return xml.substring(start, end);
    }

    private List<String> validationErrors(Path xml) throws Exception {
        return new MetaValidator().validate(new XmlStructureReader().parse(xml), null).stream()
                .filter(message -> "ERROR".equals(message.level))
                .map(message -> message.message)
                .toList();
    }

    private String normalizeGeneratedIdentity(String xml) {
        return xml.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                "<UUID>");
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private record RegisterFixture(String id, String type, String extra) {
    }
}

//++agent TASK-174 XG-102

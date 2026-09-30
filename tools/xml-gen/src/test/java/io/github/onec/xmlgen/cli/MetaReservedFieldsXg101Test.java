package io.github.onec.xmlgen.cli;

//++agent TASK-174 XG-101 2026-07-14

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class MetaReservedFieldsXg101Test {

    @TempDir
    Path tempDir;

    @Test
    void compileRejectsRecorderAliasesCaseInsensitivelyBeforeAnyOutput() throws Exception {
        for (String fieldName : List.of("Регистратор", "рЕгИсТрАтОр", "Recorder", "rEcOrDeR")) {
            Path root = tempDir.resolve("out-" + Integer.toUnsignedString(fieldName.hashCode()));
            Files.createDirectories(root);
            Path configuration = writeMinimalConfiguration(root);
            String before = sha256(configuration);
            Path json = tempDir.resolve("invalid-" + Integer.toUnsignedString(fieldName.hashCode()) + ".json");
            Files.writeString(json, """
                    {"type":"InformationRegister","name":"биг_Аудит",
                     "writeMode":"Independent","periodicity":"Nonperiodical",
                     "attributes":[{"name":"%s","type":"DocumentRef.big_Order_OKX"}]}
                    """.formatted(fieldName), StandardCharsets.UTF_8);

            ProcessResult result = runMain("meta", "compile", json.toString(), root.toString());

            assertThat(result.exitCode()).as(result.output()).isNotZero();
            assertThat(result.output())
                    .contains("RESERVED_PLATFORM_FIELD_NAME")
                    .contains("Reserved standard field name")
                    .contains("object=InformationRegister.биг_Аудит")
                    .contains("/ChildObjects/Attribute[" + fieldName + "]")
                    .contains("name='" + fieldName + "'")
                    .contains("standard='Recorder'");
            assertThat(root.resolve("InformationRegisters")).doesNotExist();
            assertThat(sha256(configuration)).isEqualTo(before);
        }
    }

    @Test
    void compileAllowsSameWordsWhenTheyAreNotStandardForThatObjectType() throws Exception {
        Path infoRoot = tempDir.resolve("valid-info");
        Path catalogRoot = tempDir.resolve("valid-catalog");
        Files.createDirectories(infoRoot);
        Files.createDirectories(catalogRoot);
        writeMinimalConfiguration(infoRoot);
        writeMinimalConfiguration(catalogRoot);
        Path infoJson = tempDir.resolve("valid-info.json");
        Path catalogJson = tempDir.resolve("valid-catalog.json");
        Files.writeString(infoJson, """
                {"type":"InformationRegister","name":"биг_Сведения",
                 "writeMode":"Independent","periodicity":"Nonperiodical",
                 "attributes":["Дата: Date", "ИсходныйДокумент: DocumentRef.big_Order_OKX"]}
                """, StandardCharsets.UTF_8);
        Files.writeString(catalogJson, """
                {"type":"Catalog","name":"биг_Объекты",
                 "attributes":["Регистратор: String(40)"]}
                """, StandardCharsets.UTF_8);

        ProcessResult infoCompile = runMain("meta", "compile", infoJson.toString(), infoRoot.toString());
        ProcessResult catalogCompile = runMain("meta", "compile", catalogJson.toString(), catalogRoot.toString());

        assertThat(infoCompile.exitCode()).as(infoCompile.output()).isZero();
        assertThat(catalogCompile.exitCode()).as(catalogCompile.output()).isZero();
        assertThat(runMain("meta", "validate",
                infoRoot.resolve("InformationRegisters/биг_Сведения.xml").toString()).exitCode()).isZero();
        assertThat(runMain("meta", "validate",
                catalogRoot.resolve("Catalogs/биг_Объекты.xml").toString()).exitCode()).isZero();
    }

    @Test
    void validateRejectsExistingDirectAttributeButNotNestedTabularAttribute() throws Exception {
        Path invalid = tempDir.resolve("InformationRegisters/биг_Аудит.xml");
        Files.createDirectories(invalid.getParent());
        writeBom(invalid, minimalObject("InformationRegister", "биг_Аудит", """
                <Attribute uuid="22222222-2222-2222-2222-222222222222">
                  <Properties><Name>Регистратор</Name><Type><v8:Type>xs:string</v8:Type></Type></Properties>
                </Attribute>
                """));

        ProcessResult result = runMain("meta", "validate", invalid.toString());

        assertThat(result.exitCode()).as(result.output()).isNotZero();
        assertThat(result.output())
                .contains("RESERVED_PLATFORM_FIELD_NAME")
                .contains("Reserved standard field name")
                .contains("object=InformationRegister.биг_Аудит")
                .contains("/ChildObjects/Attribute[Регистратор]")
                .contains("standard='Recorder'");

        Path catalog = tempDir.resolve("Catalogs/биг_Каталог.xml");
        Files.createDirectories(catalog.getParent());
        writeBom(catalog, minimalObject("Catalog", "биг_Каталог", """
                <TabularSection uuid="33333333-3333-3333-3333-333333333333">
                  <Properties><Name>Строки</Name></Properties>
                  <ChildObjects>
                    <Attribute uuid="44444444-4444-4444-4444-444444444444">
                      <Properties><Name>Код</Name><Type><v8:Type>xs:string</v8:Type></Type></Properties>
                    </Attribute>
                  </ChildObjects>
                </TabularSection>
                """));
        ProcessResult nested = runMain("meta", "validate", catalog.toString());
        assertThat(nested.output()).doesNotContain("Reserved standard field name");
    }

    @Test
    void compileAppliesRegisterSubtypeMatrixAndLocalizedAliases() throws Exception {
        assertCompileRejected("AccumulationRegister", "\"registerType\":\"Balance\"",
                "RecordType", "RecordType");
        assertCompileRejected("AccumulationRegister", "\"registerType\":\"Balance\"",
                "вИдДвИжЕнИя", "RecordType");
        assertCompileRejected("AccountingRegister",
                "\"chartOfAccounts\":\"ChartOfAccounts.X\",\"correspondence\":false",
                "RecordType", "RecordType");
        assertCompileRejected("AccountingRegister",
                "\"chartOfAccounts\":\"ChartOfAccounts.X\",\"correspondence\":true",
                "вИдСуБкОнТо2", "ExtDimensionType2");
        assertCompileRejected("CalculationRegister",
                "\"chartOfCalculationTypes\":\"ChartOfCalculationTypes.X\","
                        + "\"actionPeriod\":true,\"basePeriod\":true",
                "нАчАлОпЕрИоДаДеЙсТвИя", "BegOfActionPeriod");
        assertCompileRejected("CalculationRegister",
                "\"chartOfCalculationTypes\":\"ChartOfCalculationTypes.X\","
                        + "\"actionPeriod\":true,\"basePeriod\":true",
                "EndOfBasePeriod", "EndOfBasePeriod");

        Path turnoverRoot = compileValid("turnover", """
                {"type":"AccumulationRegister","name":"биг_Обороты",
                 "registerType":"Turnovers","attributes":["RecordType: String(20)"]}
                """);
        String turnoverXml = Files.readString(
                turnoverRoot.resolve("AccumulationRegisters/биг_Обороты.xml"), StandardCharsets.UTF_8);
        assertThat(turnoverXml).doesNotContain("<xr:StandardAttribute name=\"RecordType\">");
        assertThat(runMain("meta", "validate",
                turnoverRoot.resolve("AccumulationRegisters/биг_Обороты.xml").toString()).exitCode()).isZero();

        Path calcRoot = compileValid("calc-no-periods", """
                {"type":"CalculationRegister","name":"биг_Расчет",
                 "chartOfCalculationTypes":"ChartOfCalculationTypes.X",
                 "actionPeriod":false,"basePeriod":false,
                 "attributes":["ActionPeriod: String(20)"]}
                """);
        String calcXml = Files.readString(
                calcRoot.resolve("CalculationRegisters/биг_Расчет.xml"), StandardCharsets.UTF_8);
        assertThat(calcXml).doesNotContain("<xr:StandardAttribute name=\"ActionPeriod\">");
    }

    //++agent TASK-174 XG-112 2026-07-19
    @Test
    void independentInformationRegisterRejectsLineNumberAliasesInCompileAndValidate() throws Exception {
        for (String fieldName : List.of("НомерСтроки", "нОмЕрСтРоКи", "LineNumber", "lInEnUmBeR")) {
            String suffix = Integer.toUnsignedString(fieldName.hashCode());
            Path root = tempDir.resolve("reject-info-line-number-" + suffix);
            Files.createDirectories(root);
            Path configuration = writeMinimalConfiguration(root);
            String before = sha256(configuration);
            Path json = tempDir.resolve("reject-info-line-number-" + suffix + ".json");
            Files.writeString(json, """
                    {"type":"InformationRegister","name":"биг_Манифест",
                     "writeMode":"Independent","periodicity":"Nonperiodical",
                     "dimensions":[{"name":"%s","type":"Number(15,0,nonneg)"}]}
                    """.formatted(fieldName), StandardCharsets.UTF_8);

            ProcessResult compile = runMain("meta", "compile", json.toString(), root.toString());

            assertThat(compile.exitCode()).as(compile.output()).isEqualTo(1);
            assertThat(compile.output()).contains("RESERVED_PLATFORM_FIELD_NAME")
                    .contains("/ChildObjects/Dimension[" + fieldName + "]")
                    .contains("name='" + fieldName + "'")
                    .contains("standard='LineNumber'");
            assertThat(root.resolve("InformationRegisters")).doesNotExist();
            assertThat(sha256(configuration)).isEqualTo(before);

            Path object = tempDir.resolve("validate-info-line-number-" + suffix)
                    .resolve("InformationRegisters/биг_Манифест.xml");
            Files.createDirectories(object.getParent());
            writeBom(object, minimalObject("InformationRegister", "биг_Манифест", """
                    <Dimension uuid="22222222-2222-2222-2222-222222222222">
                      <Properties><Name>%s</Name><Type><v8:Type>xs:decimal</v8:Type></Type>
                        <Indexing>DontIndex</Indexing></Properties>
                    </Dimension>
                    """.formatted(fieldName))
                    .replace("<Synonym/>", "<Synonym/><InformationRegisterPeriodicity>Nonperiodical"
                            + "</InformationRegisterPeriodicity><WriteMode>Independent</WriteMode>"));

            ProcessResult validate = runMain("meta", "validate", object.toString());

            assertThat(validate.exitCode()).as(validate.output()).isEqualTo(1);
            assertThat(validate.output()).contains("RESERVED_PLATFORM_FIELD_NAME")
                    .contains("/ChildObjects/Dimension[" + fieldName + "]")
                    .contains("standard='LineNumber'");
        }

        // Имя бизнес-порядка выбирается отдельно и считается легальным только после Designer live-gate.
        Path legalRoot = compileValid("independent-business-order", """
                {"type":"InformationRegister","name":"биг_Манифест",
                 "writeMode":"Independent","periodicity":"Nonperiodical",
                 "dimensions":[{"name":"ПорядковыйНомер","type":"Number(15,0,nonneg)"}]}
                """);
        assertThat(runMain("meta", "validate",
                legalRoot.resolve("InformationRegisters/биг_Манифест.xml").toString()).exitCode()).isZero();
    }
    //--agent TASK-174 XG-112

    @Test
    void writerEmitsDesignerConditionalFieldsInCanonicalOrder() throws Exception {
        Path accountingRoot = compileValid("accounting", """
                {"type":"AccountingRegister","name":"биг_Проводки",
                 "chartOfAccounts":"ChartOfAccounts.X","correspondence":false}
                """);
        String accounting = Files.readString(
                accountingRoot.resolve("AccountingRegisters/биг_Проводки.xml"), StandardCharsets.UTF_8);
        assertInOrder(accounting, "name=\"Account\"", "name=\"RecordType\"", "name=\"Active\"",
                "name=\"LineNumber\"", "name=\"Recorder\"", "name=\"Period\"",
                "name=\"ExtDimension1\"", "name=\"ExtDimensionType1\"",
                "name=\"ExtDimension2\"", "name=\"ExtDimensionType2\"",
                "name=\"ExtDimension3\"", "name=\"ExtDimensionType3\"");

        Path calculationRoot = compileValid("calculation", """
                {"type":"CalculationRegister","name":"биг_Начисления",
                 "chartOfCalculationTypes":"ChartOfCalculationTypes.X",
                 "actionPeriod":true,"basePeriod":true}
                """);
        String calculation = Files.readString(
                calculationRoot.resolve("CalculationRegisters/биг_Начисления.xml"), StandardCharsets.UTF_8);
        assertInOrder(calculation, "name=\"RegistrationPeriod\"", "name=\"ReversingEntry\"",
                "name=\"Active\"", "name=\"EndOfBasePeriod\"", "name=\"BegOfBasePeriod\"",
                "name=\"EndOfActionPeriod\"", "name=\"BegOfActionPeriod\"",
                "name=\"ActionPeriod\"", "name=\"CalculationType\"",
                "name=\"LineNumber\"", "name=\"Recorder\"");
    }

    @Test
    void postingModeIsReservedOnlyAndNeverEmittedByDocumentWriter() throws Exception {
        assertCompileRejected("Document", "", "PostingMode", "PostingMode");
        assertCompileRejected("Document", "", "рЕжИмПрОвЕдЕнИя", "PostingMode");

        Path root = compileValid("document", """
                {"type":"Document","name":"биг_Документ","attributes":["КомментарийX: String(20)"]}
                """);
        String xml = Files.readString(root.resolve("Documents/биг_Документ.xml"), StandardCharsets.UTF_8);
        assertThat(xml).doesNotContain("<xr:StandardAttribute name=\"PostingMode\">");
    }

    private void assertCompileRejected(String type, String extraProperties,
                                       String fieldName, String canonical) throws Exception {
        String suffix = Integer.toUnsignedString((type + extraProperties + fieldName).hashCode());
        Path output = tempDir.resolve("reject-" + suffix);
        Path json = tempDir.resolve("reject-" + suffix + ".json");
        String comma = extraProperties.isBlank() ? "" : "," + extraProperties;
        Files.writeString(json, "{\"type\":\"" + type + "\",\"name\":\"биг_Probe\""
                + comma + ",\"attributes\":[{\"name\":\"" + fieldName
                + "\",\"type\":\"String(20)\"}]}", StandardCharsets.UTF_8);

        ProcessResult result = runMain("meta", "compile", json.toString(), output.toString());

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("RESERVED_PLATFORM_FIELD_NAME")
                .contains("name='" + fieldName + "'")
                .contains("standard='" + canonical + "'");
        assertThat(output).doesNotExist();
    }

    private Path compileValid(String id, String jsonBody) throws Exception {
        Path root = tempDir.resolve("valid-" + id);
        Files.createDirectories(root);
        writeMinimalConfiguration(root);
        Path json = tempDir.resolve("valid-" + id + ".json");
        Files.writeString(json, jsonBody, StandardCharsets.UTF_8);
        ProcessResult result = runMain("meta", "compile", json.toString(), root.toString());
        assertThat(result.exitCode()).as(result.output()).isZero();
        return root;
    }

    private void assertInOrder(String content, String... tokens) {
        int previous = -1;
        for (String token : tokens) {
            int current = content.indexOf(token);
            assertThat(current).as("missing token %s", token).isGreaterThan(previous);
            previous = current;
        }
    }

    private Path writeMinimalConfiguration(Path root) throws Exception {
        Path configuration = root.resolve("Configuration.xml");
        writeBom(configuration, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Configuration uuid="11111111-1111-1111-1111-111111111111">
                    <Properties><Name>Test</Name></Properties><ChildObjects/>
                  </Configuration>
                </MetaDataObject>
                """);
        return configuration;
    }

    private String minimalObject(String type, String name, String children) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core" version="2.20">
                  <%s uuid="11111111-1111-1111-1111-111111111111">
                    <Properties><Name>%s</Name><Synonym/></Properties>
                    <ChildObjects>%s</ChildObjects>
                  </%s>
                </MetaDataObject>
                """.formatted(type, name, children, type);
    }

    private void writeBom(Path path, String content) throws Exception {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(body, 0, bytes, 3, body.length);
        Files.write(path, bytes);
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private ProcessResult runMain(String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectErrorStream(true)
                .start();
        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(output).isTrue();
        return new ProcessResult(process.exitValue(), output);
    }

    private record ProcessResult(int exitCode, String output) {
    }
}

//--agent TASK-174 XG-101

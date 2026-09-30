package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("XG-97: составной ссылочный тип поля СКД")
class SkdCompositeFieldTypeXg97Test {

    private static final String TYPES = "DocumentRef.биг_ПринятиеАктивовПодУправление"
            + "|DocumentRef.big_Positions_OKX"
            + "|DocumentRef.big_Order_OKX"
            + "|DocumentRef.big_AlgoOrders_OKX";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("modify-field пишет четыре v8:Type по порядку и повторяется byte-NO-OP")
    void modifyField_writesFourOrderedTypesAndRepeatsAsByteNoop() throws Exception {
        Path schema = tempDir.resolve("Template.xml");
        String xml = "\uFEFF<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\">\r\n"
                + "\t<dataSet xsi:type=\"DataSetQuery\">\r\n"
                + "\t\t<name>ОстаткиМонет</name>\r\n"
                + "\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>До</dataPath><field>До</field></field>\r\n"
                + "\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>Партия</dataPath><field>Партия</field>"
                + "<valueType><v8:Type>xs:string</v8:Type></valueType></field>\r\n"
                + "\t\t<field xsi:type=\"DataSetFieldField\"><dataPath>После</dataPath><field>После</field></field>\r\n"
                + "\t\t<query>ВЫБРАТЬ NULL КАК Партия</query>\r\n"
                + "\t</dataSet>\r\n"
                + "</DataCompositionSchema>\r\n";
        Files.write(schema, xml.getBytes(StandardCharsets.UTF_8));

        ProcessResult first = runMain("skd", "edit", schema.toString(), "modify-field",
                "Партия: " + TYPES, "--dataSet", "ОстаткиМонет");
        assertThat(first.exitCode()).as(first.output()).isZero();

        byte[] once = Files.readAllBytes(schema);
        String actual = new String(once, StandardCharsets.UTF_8);
        assertThat(once).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(actual.replace("\r\n", "")).doesNotContain("\n");
        assertThat(actual).containsSubsequence(
                "d5p1:DocumentRef.биг_ПринятиеАктивовПодУправление",
                "d5p1:DocumentRef.big_Positions_OKX",
                "d5p1:DocumentRef.big_Order_OKX",
                "d5p1:DocumentRef.big_AlgoOrders_OKX");
        assertThat(actual).containsSubsequence("<dataPath>До</dataPath>",
                "<dataPath>Партия</dataPath>", "<dataPath>После</dataPath>");

        ProcessResult second = runMain("skd", "edit", schema.toString(), "modify-field",
                "Партия: " + TYPES, "--dataSet", "ОстаткиМонет");
        assertThat(second.exitCode()).as(second.output()).isZero();
        assertThat(Files.readAllBytes(schema)).isEqualTo(once);
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(tempDir.toFile()).start();
        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) process.destroyForcibly();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                + new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(output).isTrue();
        return new ProcessResult(process.exitValue(), output);
    }

    private record ProcessResult(int exitCode, String output) {}
}

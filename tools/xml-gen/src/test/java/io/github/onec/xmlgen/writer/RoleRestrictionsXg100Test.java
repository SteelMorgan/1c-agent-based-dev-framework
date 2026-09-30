package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 [13.07.2026 00:00:00]
class RoleRestrictionsXg100Test {
    private static final String CONDITION = """
            #Если &ОграничениеДоступаНаУровнеЗаписейУниверсально #Тогда
            #ДляРегистра("ИдентификаторыОбъектовМетаданных.РегистрСведенийбиг_ТорговыеКомиссии", "Аккаунт", "Портфель", "", "", "")
            #Иначе
            #ПоЗначениям( "РегистрСведений.биг_ТорговыеКомиссии", "", "", "биг_Аккаунты", "Аккаунт", "биг_Портфели", "Портфель", "","", "","", "","", "","", "","", "","", "","", "","", "","", "","", "","", "","")
            #КонецЕсли""";

    @TempDir Path tempDir;

    @Test
    void task208CompilesTypedReadRestrictionUnderExactRightAndPreservesOrder() throws Exception {
        String json = """
                {
                  "name":"биг_ЧтениеТорговыхКомиссий",
                  "setForNewObjects":false,
                  "setForAttributesByDefault":false,
                  "objects":[
                    {"name":"InformationRegister.биг_ТорговыеКомиссии",
                     "rights":["Read","View"],
                     "restrictions":[{"right":"Read","condition":%s}]},
                    {"name":"Report.биг_РезультатыУправления","rights":["Read","View"]}
                  ],
                  "templates":[]
                }
                """.formatted(new ObjectMapper().writeValueAsString(CONDITION));
        RoleDsl dsl = new ObjectMapper().readValue(json, RoleDsl.class);

        new RoleWriter(OutputFormat.DESIGNER).create(dsl, tempDir);

        Path rightsPath = tempDir.resolve(
                "Roles/биг_ЧтениеТорговыхКомиссий/Ext/Rights.xml");
        byte[] bytes = Files.readAllBytes(rightsPath);
        String rights = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(rights).contains("<name>InformationRegister.биг_ТорговыеКомиссии</name>")
                .contains("<restrictionByCondition>")
                .contains("#ДляРегистра(\"ИдентификаторыОбъектовМетаданных.РегистрСведенийбиг_ТорговыеКомиссии\"")
                .contains("#ПоЗначениям(")
                .contains("&amp;ОграничениеДоступаНаУровнеЗаписейУниверсально")
                .contains("<name>Report.биг_РезультатыУправления</name>");
        assertThat(count(rights, "<restrictionByCondition>")).isEqualTo(1);
        assertThat(rights.indexOf("<name>Read</name>"))
                .isLessThan(rights.indexOf("<restrictionByCondition>"));
        assertThat(rights.indexOf("<restrictionByCondition>"))
                .isLessThan(rights.indexOf("<name>View</name>"));
        assertThat(rights.replace("\r\n", "\n"))
                .contains(CONDITION.replace("&", "&amp;"));
    }

    @Test
    void acceptsOrderedNestedAndElseIfBranches() throws Exception {
        String condition = """
                #Если &A #Тогда
                #Если &B #Тогда
                ГДЕ Истина
                #ИначеЕсли &C #Тогда
                ГДЕ Ложь
                #Иначе
                ГДЕ Истина
                #КонецЕсли
                #Иначе
                ГДЕ Ложь
                #КонецЕсли""";
        String restriction = new ObjectMapper().writeValueAsString(condition);
        RoleDsl dsl = new ObjectMapper().readValue(jsonRestriction(
                "{\"right\":\"Read\",\"condition\":" + restriction + "}"), RoleDsl.class);

        new RoleWriter(OutputFormat.DESIGNER).create(dsl, tempDir.resolve("nested-valid"));

        assertThat(tempDir.resolve("nested-valid/Roles/R/Ext/Rights.xml")).exists();
    }

    @Test
    void malformedUnknownDuplicateAndUngrantableRestrictionsFailBeforeAnyOutput() throws Exception {
        List<Invalid> invalid = List.of(
                new Invalid("{\"name\":\"R\",\"objects\":[{\"name\":\"InformationRegister.R\",\"rights\":[\"Read\"],\"restrictionz\":[]}]}",
                        "Unknown role object field"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"ГДЕ Истина\",\"extra\":1}"),
                        "Unknown role restriction field"),
                new Invalid(jsonRestriction("{\"right\":\"Update\",\"condition\":\"ГДЕ Истина\"}"),
                        "is not granted"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"\"}"),
                        "Empty restriction condition"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"   \\n  \\t\"}"),
                        "Empty restriction condition"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"#Если &X #Тогда ГДЕ Истина\"}"),
                        "Malformed restriction condition"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"#КонецЕсли\\n#Если &X #Тогда\\nГДЕ Истина\"}"),
                        "#КонецЕсли before matching #Если"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"#Если &X #Тогда\\n#Иначе\\n#Иначе\\n#КонецЕсли\"}"),
                        "duplicate or misplaced #Иначе"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"#Если &X #Тогда\\n#Иначе\\n#ИначеЕсли &Y #Тогда\\n#КонецЕсли\"}"),
                        "#ИначеЕсли without an open #Если branch"),
                new Invalid(jsonRestriction("{\"right\":\"Read\",\"condition\":\"ГДЕ Истина\"},{\"right\":\"Read\",\"condition\":\"ГДЕ Ложь\"}"),
                        "Duplicate restriction"),
                new Invalid("{\"name\":\"R\",\"objects\":[{\"name\":\"InformationRegister.R\",\"rights\":[\"Read\"]},{\"name\":\"InformationRegister.R\",\"rights\":[\"Read\"]}]}",
                        "Duplicate role object"));

        for (int i = 0; i < invalid.size(); i++) {
            Path output = tempDir.resolve("invalid-" + i);
            Invalid test = invalid.get(i);
            assertThatThrownBy(() -> {
                RoleDsl dsl = new ObjectMapper().readValue(test.json, RoleDsl.class);
                new RoleWriter(OutputFormat.DESIGNER).create(dsl, output);
            }).hasMessageContaining(test.message);
            assertThat(output.resolve("Roles")).doesNotExist();
            assertThat(output.resolve("Configuration.xml")).doesNotExist();
        }
    }

    private static String jsonRestriction(String restrictions) {
        return "{\"name\":\"R\",\"objects\":[{\"name\":\"InformationRegister.R\","
                + "\"rights\":[\"Read\"],\"restrictions\":[" + restrictions + "]}]}";
    }

    private static int count(String value, String token) {
        int result = 0;
        for (int at = 0; (at = value.indexOf(token, at)) >= 0; at += token.length()) result++;
        return result;
    }

    private record Invalid(String json, String message) { }
}
//++agent TASK-174

package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.validator.FormValidator;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationLevel;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-208 [16.07.2026] XG-110
/** Causal regression for ValueTable schema loss accepted by form compile and validate. */
class FormValueTableSchemaXg110Test {

    @TempDir
    Path tempDir;

    @Test
    void compileRejectsBoundValueTableColumnMissingFromAttributeSchemaBeforeOutput() throws Exception {
        FormDsl dsl = new ObjectMapper().readValue("""
                {
                  "attributes": [{"name":"Детали","type":"ValueTable"}],
                  "elements": [{
                    "type":"table", "name":"ТаблицаДетали", "dataPath":"Детали",
                    "columns":[{
                      "type":"input", "name":"ДеталиПорядок",
                      "dataPath":"Детали.Порядок", "readOnly":true
                    }]
                  }]
                }
                """, FormDsl.class);
        Path output = tempDir.resolve("Form.xml");

        assertThatThrownBy(() -> new FormWriter(OutputFormat.DESIGNER).create(dsl, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORM-133")
                .hasMessageContaining("Детали.Порядок");
        assertThat(output).doesNotExist();
    }

    @Test
    void compileRejectsCaseInsensitiveValueTableAndValueTreeAliasesBeforeOutput() throws Exception {
        for (String collectionType : List.of("valuetable", "VaLuEtAbLe", "valuetree")) {
            FormDsl dsl = new ObjectMapper().readValue("""
                    {
                      "attributes": [{"name":"Данные","type":"%s"}],
                      "elements": [{"type":"table","name":"ТаблицаДанные","dataPath":"Данные",
                        "columns":[{"type":"input","name":"ДанныеКод","dataPath":"Данные.Код"}]}]
                    }
                    """.formatted(collectionType), FormDsl.class);
            Path output = tempDir.resolve(collectionType + ".xml");

            assertThatThrownBy(() -> new FormWriter(OutputFormat.DESIGNER).create(dsl, output))
                    .as(collectionType)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FORM-133")
                    .hasMessageContaining("Данные.Код");
            assertThat(output).doesNotExist();
        }
    }

    @Test
    void validatorRejectsExistingXmlWithBoundValueTableColumnMissingFromSchema() throws Exception {
        Path broken = tempDir.resolve("broken-value-table.xml");
        Files.writeString(broken, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
                      xmlns:v8="http://v8.1c.ru/8.1/data/core" version="2.17">
                  <Title><v8:item><v8:lang>ru</v8:lang><v8:content>Тест</v8:content></v8:item></Title>
                  <AutoCommandBar name="ФормаКоманднаяПанель" id="-1"/>
                  <ChildItems>
                    <Table name="ТаблицаДетали" id="1">
                      <DataPath>Детали</DataPath>
                      <ContextMenu name="ТаблицаДеталиКонтекстноеМеню" id="2"/>
                      <AutoCommandBar name="ТаблицаДеталиКоманднаяПанель" id="3"/>
                      <ExtendedTooltip name="ТаблицаДеталиРасширеннаяПодсказка" id="4"/>
                      <ChildItems>
                        <InputField name="ДеталиПорядок" id="5">
                          <DataPath>Детали.Порядок</DataPath>
                          <ContextMenu name="ДеталиПорядокКонтекстноеМеню" id="6"/>
                          <ExtendedTooltip name="ДеталиПорядокРасширеннаяПодсказка" id="7"/>
                        </InputField>
                      </ChildItems>
                    </Table>
                  </ChildItems>
                  <Attributes>
                    <Attribute name="Детали" id="1">
                      <Type><v8:Type>v8:ValueTable</v8:Type></Type>
                    </Attribute>
                  </Attributes>
                </Form>
                """, StandardCharsets.UTF_8);

        XmlDocument document = new XmlStructureReader().parse(broken);
        List<ValidationIssue> issues = new FormValidator().validate(document, ValidationLevel.SEMANTIC);

        assertThat(issues).anySatisfy(issue -> {
            assertThat(issue.getCode()).isEqualTo("FORM-133");
            assertThat(issue.getMessage()).contains("Детали.Порядок");
        });
    }

    @Test
    void compileSerializesTwoTypedValueTableSchemasInDeclaredOrderAndValidatorAcceptsThem()
            throws Exception {
        FormDsl dsl = new ObjectMapper().readValue("""
                {
                  "attributes": [
                    {"name":"Детали","type":"ValueTable","columns":[
                      {"name":"Порядок","type":"number(10,0)"},
                      {"name":"Идентичность","type":"string(100)"},
                      {"name":"EventKey","type":"uuid"},
                      {"name":"ДатаУчета","type":"date"},
                      {"name":"Направление","type":"string(20)"},
                      {"name":"Сумма","type":"number(18,8)"},
                      {"name":"КачествоИсточника","type":"string(30)"},
                      {"name":"Подтверждено","type":"boolean"}
                    ]},
                    {"name":"Периоды","type":"ValueTable","columns":[
                      {"name":"Порядок","type":"number(10,0)"},
                      {"name":"КодПериода","type":"string(20)"},
                      {"name":"ДатаНачала","type":"date"},
                      {"name":"ДатаОкончания","type":"date"},
                      {"name":"Ввод","type":"number(18,8)"},
                      {"name":"Вывод","type":"number(18,8)"},
                      {"name":"Residual","type":"number(18,8)"},
                      {"name":"КачествоИсточника","type":"string(30)"},
                      {"name":"КачествоВремени","type":"string(30)"},
                      {"name":"ЗначениеEOD","type":"number(18,8)"},
                      {"name":"РазницаТочности","type":"number(18,8)"},
                      {"name":"РазницаНормативногоПотока","type":"number(18,8)"},
                      {"name":"ДиагностическийКод","type":"string(64)"}
                    ]}
                  ],
                  "elements": [
                    {"type":"table","name":"ТаблицаДетали","dataPath":"Детали","columns":[
                      {"type":"input","name":"Д1","dataPath":"Детали.Порядок"},
                      {"type":"input","name":"Д2","dataPath":"Детали.Идентичность"},
                      {"type":"input","name":"Д3","dataPath":"Детали.EventKey"},
                      {"type":"input","name":"Д4","dataPath":"Детали.ДатаУчета"},
                      {"type":"input","name":"Д5","dataPath":"Детали.Направление"},
                      {"type":"input","name":"Д6","dataPath":"Детали.Сумма"},
                      {"type":"input","name":"Д7","dataPath":"Детали.КачествоИсточника"},
                      {"type":"input","name":"Д8","dataPath":"Детали.Подтверждено"}
                    ]},
                    {"type":"table","name":"ТаблицаПериоды","dataPath":"Периоды","columns":[
                      {"type":"input","name":"П1","dataPath":"Периоды.Порядок"},
                      {"type":"input","name":"П2","dataPath":"Периоды.КодПериода"},
                      {"type":"input","name":"П3","dataPath":"Периоды.ДатаНачала"},
                      {"type":"input","name":"П4","dataPath":"Периоды.ДатаОкончания"},
                      {"type":"input","name":"П5","dataPath":"Периоды.Ввод"},
                      {"type":"input","name":"П6","dataPath":"Периоды.Вывод"},
                      {"type":"input","name":"П7","dataPath":"Периоды.Residual"},
                      {"type":"input","name":"П8","dataPath":"Периоды.КачествоИсточника"},
                      {"type":"input","name":"П9","dataPath":"Периоды.КачествоВремени"},
                      {"type":"input","name":"П10","dataPath":"Периоды.ЗначениеEOD"},
                      {"type":"input","name":"П11","dataPath":"Периоды.РазницаТочности"},
                      {"type":"input","name":"П12","dataPath":"Периоды.РазницаНормативногоПотока"},
                      {"type":"input","name":"П13","dataPath":"Периоды.ДиагностическийКод"}
                    ]}
                  ]
                }
                """, FormDsl.class);
        Path output = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl, output);
        XmlDocument document = new XmlStructureReader().parse(output);
        XmlNode attributes = document.getRoot().child("Attributes");
        List<XmlNode> valueTables = attributes.children("Attribute");

        assertThat(columnNames(valueTables.get(0))).containsExactly(
                "Порядок", "Идентичность", "EventKey", "ДатаУчета", "Направление",
                "Сумма", "КачествоИсточника", "Подтверждено");
        assertThat(columnNames(valueTables.get(1))).containsExactly(
                "Порядок", "КодПериода", "ДатаНачала", "ДатаОкончания", "Ввод", "Вывод",
                "Residual", "КачествоИсточника", "КачествоВремени", "ЗначениеEOD",
                "РазницаТочности", "РазницаНормативногоПотока", "ДиагностическийКод");
        assertThat(valueTables.get(0).child("Columns").children("Column"))
                .extracting(column -> column.attr("id"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8");
        assertThat(valueTables.get(1).child("Columns").children("Column"))
                .extracting(column -> column.attr("id"))
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                        "11", "12", "13");
        assertThat(columnTypes(valueTables.get(0))).containsExactly(
                "xs:decimal;Number(10,0,Any)",
                "xs:string;String(100,Variable)",
                "v8:UUID",
                "xs:dateTime;Date(Date)",
                "xs:string;String(20,Variable)",
                "xs:decimal;Number(18,8,Any)",
                "xs:string;String(30,Variable)",
                "xs:boolean");
        assertThat(columnTypes(valueTables.get(1))).containsExactly(
                "xs:decimal;Number(10,0,Any)",
                "xs:string;String(20,Variable)",
                "xs:dateTime;Date(Date)",
                "xs:dateTime;Date(Date)",
                "xs:decimal;Number(18,8,Any)",
                "xs:decimal;Number(18,8,Any)",
                "xs:decimal;Number(18,8,Any)",
                "xs:string;String(30,Variable)",
                "xs:string;String(30,Variable)",
                "xs:decimal;Number(18,8,Any)",
                "xs:decimal;Number(18,8,Any)",
                "xs:decimal;Number(18,8,Any)",
                "xs:string;String(64,Variable)");
        assertThat(boundDataPaths(document.getRoot())).containsExactly(
                "Детали.Порядок", "Детали.Идентичность", "Детали.EventKey",
                "Детали.ДатаУчета", "Детали.Направление", "Детали.Сумма",
                "Детали.КачествоИсточника", "Детали.Подтверждено",
                "Периоды.Порядок", "Периоды.КодПериода", "Периоды.ДатаНачала",
                "Периоды.ДатаОкончания", "Периоды.Ввод", "Периоды.Вывод",
                "Периоды.Residual", "Периоды.КачествоИсточника",
                "Периоды.КачествоВремени", "Периоды.ЗначениеEOD",
                "Периоды.РазницаТочности", "Периоды.РазницаНормативногоПотока",
                "Периоды.ДиагностическийКод");

        List<ValidationIssue> issues = new FormValidator().validate(document, ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(issue -> "FORM-133".equals(issue.getCode()));
    }

    @Test
    void compileRejectsDuplicateValueTableSchemaColumnBeforeOutput() throws Exception {
        FormDsl dsl = new ObjectMapper().readValue("""
                {"attributes":[{"name":"Детали","type":"ValueTable","columns":[
                  {"name":"Порядок","type":"number(10,0)"},
                  {"name":"Порядок","type":"number(10,0)"}
                ]}]}
                """, FormDsl.class);
        Path output = tempDir.resolve("duplicate.xml");

        assertThatThrownBy(() -> new FormWriter(OutputFormat.DESIGNER).create(dsl, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORM-133")
                .hasMessageContaining("duplicate");
        assertThat(output).doesNotExist();
    }

    @Test
    void compileRejectsBlankValueTreeSchemaColumnBeforeOutput() throws Exception {
        FormDsl dsl = new ObjectMapper().readValue("""
                {"attributes":[{"name":"Дерево","type":"ValueTree","columns":[
                  {"name":" ","type":"string(10)"}
                ]}]}
                """, FormDsl.class);
        Path output = tempDir.resolve("blank-valuetree.xml");

        assertThatThrownBy(() -> new FormWriter(OutputFormat.DESIGNER).create(dsl, output))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FORM-133")
                .hasMessageContaining("blank");
        assertThat(output).doesNotExist();
    }

    private List<String> columnNames(XmlNode attribute) {
        return attribute.child("Columns").children("Column").stream()
                .map(column -> column.attr("name"))
                .toList();
    }

    private List<String> columnTypes(XmlNode attribute) {
        return attribute.child("Columns").children("Column").stream()
                .map(this::columnType)
                .toList();
    }

    private String columnType(XmlNode column) {
        XmlNode type = column.child("Type");
        String xmlTypes = type.children("Type").stream()
                .map(XmlNode::getText)
                .collect(Collectors.joining("|"));
        XmlNode string = type.child("StringQualifiers");
        if (string != null) {
            return xmlTypes + ";String(" + string.childText("Length") + ","
                    + string.childText("AllowedLength") + ")";
        }
        XmlNode number = type.child("NumberQualifiers");
        if (number != null) {
            return xmlTypes + ";Number(" + number.childText("Digits") + ","
                    + number.childText("FractionDigits") + ","
                    + number.childText("AllowedSign") + ")";
        }
        XmlNode date = type.child("DateQualifiers");
        if (date != null) {
            return xmlTypes + ";Date(" + date.childText("DateFractions") + ")";
        }
        return xmlTypes;
    }

    private List<String> boundDataPaths(XmlNode root) {
        List<String> result = new ArrayList<>();
        collectBoundDataPaths(root.child("ChildItems"), result);
        return result;
    }

    private void collectBoundDataPaths(XmlNode node, List<String> result) {
        if (node == null) {
            return;
        }
        if ("DataPath".equals(node.getName()) && node.getText() != null
                && (node.getText().startsWith("Детали.") || node.getText().startsWith("Периоды."))) {
            result.add(node.getText());
        }
        for (XmlNode child : node.getChildren()) {
            collectBoundDataPaths(child, result);
        }
    }
}
//--agent TASK-208 XG-110

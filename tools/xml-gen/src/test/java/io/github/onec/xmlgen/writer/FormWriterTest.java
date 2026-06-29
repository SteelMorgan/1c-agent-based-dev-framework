package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты для FormWriter.
 */
class FormWriterTest {
    
    @TempDir
    Path tempDir;
    
    /**
     * Тест 1: Минимальная форма (только title).
     */
    @Test
    void testMinimalForm() throws Exception {
        FormDsl dsl = new FormDsl(
                "Тестовая форма",
                null, null, null, null, null, null, null
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверки
        assertThat(content).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("<Form");
        assertThat(content).contains("version=\"2.17\"");
        // TASK-171: корневой Title теперь мультиязычный v8:item (LocalStringType), не плоский текст
        assertThat(content).contains("<v8:content>Тестовая форма</v8:content>");
        assertThat(content).contains("<AutoCommandBar");
        assertThat(content).contains("name=\"ФормаКоманднаяПанель\"");
        assertThat(content).contains("id=\"-1\"");
        
        // TASK-171: Designer-формат Form.xml ДОЛЖЕН быть с UTF-8 BOM (иначе XDTO-отказ платформы)
        byte[] bytes = Files.readAllBytes(outputXml);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
    }
    
    /**
     * Тест 2: Форма с реквизитами.
     */
    @Test
    void testFormWithAttributes() throws Exception {
        List<FormDsl.Attribute> attributes = Arrays.asList(
                new FormDsl.Attribute("Объект", "Объект", "ExternalDataProcessorObject.ТестоваяОбработка", true, null),
                new FormDsl.Attribute("Параметр1", "Параметр 1", "string(100)", false, null),
                new FormDsl.Attribute("Число", "Число", "number(10,2)", false, null)
        );
        
        FormDsl dsl = new FormDsl(
                "Форма с реквизитами",
                null, null, null, null,
                attributes,
                null, null
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки реквизитов
        assertThat(content).contains("<Attributes>");
        assertThat(content).contains("<Attribute name=\"Объект\" id=\"1\">");
        assertThat(content).contains("<MainAttribute>true</MainAttribute>");
        assertThat(content).contains("<Attribute name=\"Параметр1\" id=\"2\">");
        assertThat(content).contains("<v8:Type>xs:string</v8:Type>");
        assertThat(content).contains("<v8:Length>100</v8:Length>");
        assertThat(content).contains("<Attribute name=\"Число\" id=\"3\">");
        assertThat(content).contains("<v8:Digits>10</v8:Digits>");
        assertThat(content).contains("<v8:FractionDigits>2</v8:FractionDigits>");
    }

    //++agent TASK-199 [27.06.2026 23:30:00]
    /**
     * TASK-199 / XG-64: DynamicList must use Designer canonical Settings,
     * not XDTO-invalid DynamicListExtInfo.
     */
    @Test
    void task199_dynamicListAttribute_emitsSettingsInsteadOfExtInfo() throws Exception {
        Map<String, Object> settings = new HashMap<>();
        settings.put("mainTable", "Document.биг_КорректировкаЗаписейРегистров");
        settings.put("dynamicDataRead", true);

        List<FormDsl.Attribute> attributes = List.of(
                new FormDsl.Attribute("Список", null, "DynamicList", true,
                        null, settings, null));

        FormDsl dsl = new FormDsl(
                "Форма списка",
                null, null, null, null,
                attributes,
                null, null
        );

        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);

        String content = Files.readString(outputXml);

        assertThat(content).contains("<Settings xsi:type=\"DynamicList\">");
        assertThat(content).contains("<ManualQuery>false</ManualQuery>");
        assertThat(content).contains("<DynamicDataRead>true</DynamicDataRead>");
        assertThat(content).contains("<MainTable>Document.биг_КорректировкаЗаписейРегистров</MainTable>");
        assertThat(content).doesNotContain("DynamicListExtInfo");
        assertThat(content).doesNotContain("<ExtInfo");
    }

    @Test
    void task199_labelFieldUserVisible_emitsXrCommonStructure() throws Exception {
        Map<String, Object> hiddenLabel = new HashMap<>();
        hiddenLabel.put("type", "labelField");
        hiddenLabel.put("name", "Ссылка");
        hiddenLabel.put("dataPath", "Список.Ref");
        hiddenLabel.put("userVisible", false);

        FormDsl dsl = new FormDsl(
                "Форма списка",
                null, null, null,
                List.of(hiddenLabel),
                null,
                null, null
        );

        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);

        String content = Files.readString(outputXml);

        assertThat(content).contains("<UserVisible>");
        assertThat(content).contains("<xr:Common>false</xr:Common>");
        assertThat(content).doesNotContain("<UserVisible>false</UserVisible>");
    }
    //++agent TASK-199
    
    /**
     * Тест 3: Форма с командами.
     */
    @Test
    void testFormWithCommands() throws Exception {
        List<FormDsl.Command> commands = Arrays.asList(
                new FormDsl.Command("Выполнить", "Выполнить", "Выполнить", "Выполнить обработку"),
                new FormDsl.Command("Закрыть", "Закрыть", "Закрыть", null)
        );
        
        FormDsl dsl = new FormDsl(
                "Форма с командами",
                null, null, null, null, null, null,
                commands
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки команд
        assertThat(content).contains("<Commands>");
        assertThat(content).contains("<Command name=\"Выполнить\" id=\"1\">");
        assertThat(content).contains("<Action>Выполнить</Action>");
        assertThat(content).contains("<ToolTip>");
        assertThat(content).contains("<v8:content>Выполнить обработку</v8:content>");
        assertThat(content).contains("<Command name=\"Закрыть\" id=\"2\">");
    }
    
    /**
     * Тест 4: Форма с событиями.
     */
    @Test
    void testFormWithEvents() throws Exception {
        Map<String, String> events = new HashMap<>();
        events.put("OnCreateAtServer", "ПриСозданииНаСервере");
        events.put("OnOpen", "ПриОткрытии");
        
        FormDsl dsl = new FormDsl(
                "Форма с событиями",
                null, null,
                events,
                null, null, null, null
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки событий
        assertThat(content).contains("<Events>");
        assertThat(content).contains("<Event name=\"OnCreateAtServer\">ПриСозданииНаСервере</Event>");
        assertThat(content).contains("<Event name=\"OnOpen\">ПриОткрытии</Event>");
    }
    
    /**
     * Тест 5: Форма с коллекцией (ValueTable).
     */
    @Test
    void testFormWithValueTable() throws Exception {
        List<FormDsl.Column> columns = Arrays.asList(
                new FormDsl.Column("Наименование", "Наименование", "string(100)"),
                new FormDsl.Column("Количество", "Количество", "number(10,2)")
        );
        
        List<FormDsl.Attribute> attributes = Arrays.asList(
                new FormDsl.Attribute("Товары", "Товары", "ValueTable", false, columns)
        );
        
        FormDsl dsl = new FormDsl(
                "Форма с таблицей",
                null, null, null, null,
                attributes,
                null, null
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки коллекции
        assertThat(content).contains("<Attribute name=\"Товары\" id=\"1\">");
        assertThat(content).contains("<Columns>");
        assertThat(content).contains("<Column name=\"Наименование\" id=\"1\">");
        assertThat(content).contains("<Column name=\"Количество\" id=\"2\">");
        assertThat(content).contains("<v8:Type>xs:decimal</v8:Type>");
    }
    
    /**
     * Тест 6: Полная форма (все секции).
     */
    @Test
    void testCompleteForm() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("autoTitle", false);
        properties.put("windowOpeningMode", "LockOwnerWindow");
        
        Map<String, String> events = new HashMap<>();
        events.put("OnCreateAtServer", "ПриСозданииНаСервере");
        
        List<FormDsl.Attribute> attributes = Arrays.asList(
                new FormDsl.Attribute("Объект", "Объект", "ExternalDataProcessorObject.Тест", true, null),
                new FormDsl.Attribute("Параметр", "Параметр", "string(50)", false, null)
        );
        
        List<FormDsl.Command> commands = Arrays.asList(
                new FormDsl.Command("Выполнить", "Выполнить", "Выполнить", "Выполнить действие")
        );
        
        FormDsl dsl = new FormDsl(
                "Полная форма",
                properties,
                null,
                events,
                null,
                attributes,
                null,
                commands
        );
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        String content = Files.readString(outputXml);
        
        // Проверки всех секций
        assertThat(content).contains("<v8:content>Полная форма</v8:content>");
        assertThat(content).contains("<AutoTitle>false</AutoTitle>");
        assertThat(content).contains("<WindowOpeningMode>LockOwnerWindow</WindowOpeningMode>");
        assertThat(content).contains("<Events>");
        assertThat(content).contains("<Attributes>");
        assertThat(content).contains("<Commands>");
        assertThat(content).contains("<ChildItems>");
    }
    
    /**
     * Тест 7: Форма с UI-элементами.
     */
    @Test
    void testFormWithUIElements() throws Exception {
        String json = """
                {
                  "title": "Форма с элементами",
                  "attributes": [
                    {
                      "name": "Объект",
                      "type": "DocumentObject.Реализация",
                      "main": true
                    }
                  ],
                  "elements": [
                    {
                      "group": "vertical",
                      "name": "ГруппаШапка",
                      "children": [
                        {
                          "input": "Организация",
                          "path": "Объект.Организация",
                          "title": "Организация"
                        },
                        {
                          "input": "Дата",
                          "path": "Объект.Дата",
                          "title": "Дата"
                        }
                      ]
                    },
                    {
                      "pages": "Страницы",
                      "children": [
                        {
                          "page": "Товары",
                          "title": "Товары",
                          "children": [
                            {
                              "table": "Товары",
                              "path": "Объект.Товары",
                              "columns": [
                                {
                                  "input": "Номенклатура",
                                  "path": "Объект.Товары.Номенклатура"
                                },
                                {
                                  "input": "Количество",
                                  "path": "Объект.Товары.Количество"
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "button": "Провести",
                      "command": "Провести",
                      "title": "Провести"
                    }
                  ]
                }
                """;
        
        ObjectMapper mapper = new ObjectMapper();
        FormDsl dsl = mapper.readValue(json, FormDsl.class);
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // Проверки структуры
        assertThat(content).contains("<ChildItems>");
        assertThat(content).contains("<UsualGroup name=\"ГруппаШапка\"");
        assertThat(content).contains("<InputField name=\"Организация\"");
        assertThat(content).contains("<DataPath>Объект.Организация</DataPath>");
        assertThat(content).contains("<InputField name=\"Дата\"");
        assertThat(content).contains("<Pages name=\"Страницы\"");
        assertThat(content).contains("<Page name=\"Товары\"");
        assertThat(content).contains("<Table name=\"Товары\"");
        assertThat(content).contains("<DataPath>Объект.Товары</DataPath>");
        assertThat(content).contains("<Button name=\"Провести\"");
        assertThat(content).contains("<CommandName>Form.Command.Провести</CommandName>");
        
        // Проверка автоматических элементов
        assertThat(content).contains("КонтекстноеМеню");
        assertThat(content).contains("РасширеннаяПодсказка");
    }
    
    /**
     * TASK-174 XG-01: DSL c дискриминатором "type" в значении (каноничная форма из
     * form-dsl/SKILL.md и из fixtures). Прежняя детекция искала ключ-как-тип и для
     * {"type":"input",...} не находила тип → молча роняла сиблинг после первой группы
     * и любой вложенный child. Проверяем, что:
     *   - сиблинг-input ПОСЛЕ группы не теряется;
     *   - вложенный child внутри группы не теряется.
     */
    @Test
    void task174_xg01_typeAsValueDiscriminator_keepsSiblingsAndChildren() throws Exception {
        String json = """
                {
                  "title": "Frm_OP006_001",
                  "attributes": [
                    {"name": "Реквизит1", "type": "string(100)"}
                  ],
                  "elements": [
                    {
                      "type": "group",
                      "name": "ГруппаШапка",
                      "group": "Vertical",
                      "children": [
                        {"type": "input", "name": "ВложенноеПоле", "dataPath": "Реквизит1"}
                      ]
                    },
                    {"type": "input", "name": "Поле1", "dataPath": "Реквизит1"}
                  ]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        FormDsl dsl = mapper.readValue(json, FormDsl.class);

        Path outputXml = tempDir.resolve("Form.xml");
        new FormWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        assertThat(content).contains("<UsualGroup name=\"ГруппаШапка\"");
        // Сиблинг-input после группы (главный симптом XG-01) — больше не теряется.
        assertThat(content).as("сиблинг Поле1 после группы должен присутствовать")
                .contains("<InputField name=\"Поле1\"");
        // Вложенный child внутри группы — тоже не теряется.
        assertThat(content).as("вложенный child ВложенноеПоле должен присутствовать")
                .contains("<InputField name=\"ВложенноеПоле\"");
    }

    /**
     * Тест 8: JSON DSL roundtrip.
     */
    @Test
    void testJsonDslRoundtrip() throws Exception {
        String json = """
                {
                  "title": "Тестовая форма",
                  "properties": {
                    "autoTitle": false
                  },
                  "events": {
                    "OnCreateAtServer": "ПриСозданииНаСервере"
                  },
                  "attributes": [
                    {
                      "name": "Параметр1",
                      "title": "Параметр 1",
                      "type": "string(100)"
                    }
                  ],
                  "commands": [
                    {
                      "name": "Выполнить",
                      "title": "Выполнить",
                      "action": "Выполнить"
                    }
                  ]
                }
                """;
        
        ObjectMapper mapper = new ObjectMapper();
        FormDsl dsl = mapper.readValue(json, FormDsl.class);
        
        Path outputXml = tempDir.resolve("Form.xml");
        FormWriter writer = new FormWriter(OutputFormat.DESIGNER);
        writer.create(dsl, outputXml);
        
        assertThat(outputXml).exists();
        String content = Files.readString(outputXml);
        
        // TASK-171: корневой Title теперь мультиязычный v8:item (LocalStringType), не плоский текст
        assertThat(content).contains("<v8:content>Тестовая форма</v8:content>");
        assertThat(content).contains("<AutoTitle>false</AutoTitle>");
        assertThat(content).contains("<Event name=\"OnCreateAtServer\">ПриСозданииНаСервере</Event>");
        assertThat(content).contains("<Attribute name=\"Параметр1\" id=\"1\">");
        assertThat(content).contains("<Command name=\"Выполнить\" id=\"1\">");
    }

    @Test
    void formCompile_supportsButtonGroupSimpleFieldsAndChoiceControls() throws Exception {
        String json = """
                {
                  "title": "Расширенная форма",
                  "elements": [
                    {
                      "type": "input",
                      "name": "ПолеВыбора",
                      "path": "Объект.Контрагент",
                      "choiceList": [
                        {"value": "A", "presentation": "Вариант A"}
                      ],
                      "choiceParameters": [
                        {"name": "Owner", "value": "Объект.Владелец"}
                      ],
                      "choiceParameterLinks": [
                        {"name": "Owner", "dataPath": "Объект.Владелец", "valueChange": "Clear"}
                      ],
                      "typeLink": {"dataPath": "Объект.Вид", "linkItem": 1}
                    },
                    {
                      "type": "buttonGroup",
                      "name": "ГруппаКоманд",
                      "commandSource": "Form",
                      "children": [
                        {"type": "button", "name": "Кнопка", "command": "Выполнить"}
                      ]
                    },
                    {"type": "spreadsheet", "name": "ПолеТабличногоДокумента", "path": "Объект.Макет"},
                    {"type": "progressBar", "name": "Прогресс", "path": "Объект.Процент", "minValue": 0, "maxValue": 100}
                  ]
                }
                """;

        FormDsl dsl = new ObjectMapper().readValue(json, FormDsl.class);
        Path outputXml = tempDir.resolve("Form.xml");

        new FormWriter(OutputFormat.DESIGNER).create(dsl, outputXml);

        String content = Files.readString(outputXml);
        assertThat(content).contains("<ChoiceList>");
        assertThat(content).contains("<ChoiceParameters>");
        assertThat(content).contains("<ChoiceParameterLinks>");
        assertThat(content).contains("<TypeLink>");
        assertThat(content).contains("<ButtonGroup name=\"ГруппаКоманд\"");
        assertThat(content).contains("<CommandSource>Form</CommandSource>");
        assertThat(content).contains("<SpreadsheetDocumentField name=\"ПолеТабличногоДокумента\"");
        assertThat(content).contains("<ProgressBarField name=\"Прогресс\"");
        assertThat(content).contains("<MinValue>0</MinValue>");
        assertThat(content).contains("<MaxValue>100</MaxValue>");
    }
}

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
}

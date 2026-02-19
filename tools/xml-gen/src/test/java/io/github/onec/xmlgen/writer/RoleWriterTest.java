package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.RoleDsl;
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
 * Тесты для RoleWriter.
 */
class RoleWriterTest {

    @TempDir
    Path tempDir;

    /**
     * Тест 1: Минимальная роль (без объектов).
     */
    @Test
    void testMinimalRole() throws Exception {
        RoleDsl dsl = new RoleDsl(
                "ТестоваяРоль",
                "Тестовая роль",
                null,
                null,
                null,
                null,
                null,
                null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.DESIGNER);
        writer.create(dsl, tempDir);

        // Проверка метаданных роли
        Path roleXml = tempDir.resolve("Roles/ТестоваяРоль.xml");
        assertThat(roleXml).exists();
        String metaContent = Files.readString(roleXml);
        assertThat(metaContent).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(metaContent).contains("<MetaDataObject");
        assertThat(metaContent).contains("<Role");
        assertThat(metaContent).contains("<Name>ТестоваяРоль</Name>");
        assertThat(metaContent).contains("<v8:content>Тестовая роль</v8:content>");

        // Проверка BOM в метаданных
        byte[] metaBytes = Files.readAllBytes(roleXml);
        assertThat(metaBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(metaBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(metaBytes[2]).isEqualTo((byte) 0xBF);

        // Проверка Rights.xml
        Path rightsXml = tempDir.resolve("Roles/ТестоваяРоль/Ext/Rights.xml");
        assertThat(rightsXml).exists();
        String rightsContent = Files.readString(rightsXml);
        assertThat(rightsContent).contains("<Rights");
        assertThat(rightsContent).contains("http://v8.1c.ru/8.2/roles");
        assertThat(rightsContent).contains("<setForNewObjects>false</setForNewObjects>");
        assertThat(rightsContent).contains("<setForAttributesByDefault>true</setForAttributesByDefault>");
        assertThat(rightsContent).contains("<independentRightsOfChildObjects>false</independentRightsOfChildObjects>");

        // BOM в Rights.xml
        byte[] rightsBytes = Files.readAllBytes(rightsXml);
        assertThat(rightsBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(rightsBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(rightsBytes[2]).isEqualTo((byte) 0xBF);
    }

    /**
     * Тест 2: Роль с несколькими объектами и пресетами.
     */
    @Test
    void testRoleWithMultipleRights() throws Exception {
        List<RoleDsl.ObjectRights> objects = Arrays.asList(
                new RoleDsl.ObjectRights("Document.РеализацияТоваровУслуг", "edit", null, null),
                new RoleDsl.ObjectRights("Catalog.Контрагенты", "view", null, null)
        );

        RoleDsl dsl = new RoleDsl(
                "МенеджерПродаж",
                "Менеджер продаж",
                "Роль для менеджеров",
                false,
                true,
                false,
                objects,
                null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.DESIGNER);
        writer.create(dsl, tempDir);

        Path rightsXml = tempDir.resolve("Roles/МенеджерПродаж/Ext/Rights.xml");
        assertThat(rightsXml).exists();
        String content = Files.readString(rightsXml);

        // Проверка прав документа (preset=edit → Read, Insert, Update, Delete, Posting, ...)
        assertThat(content).contains("<name>Document.РеализацияТоваровУслуг</name>");
        assertThat(content).contains("<name>Read</name>");
        assertThat(content).contains("<name>Insert</name>");
        assertThat(content).contains("<name>Update</name>");
        assertThat(content).contains("<name>Delete</name>");
        assertThat(content).contains("<name>Posting</name>");
        assertThat(content).contains("<name>UndoPosting</name>");

        // Проверка прав справочника (preset=view → Read, View)
        assertThat(content).contains("<name>Catalog.Контрагенты</name>");
        assertThat(content).contains("<name>View</name>");
    }

    /**
     * Тест 3: Роль с явным списком прав (Map и List форматы).
     */
    @Test
    void testRoleWithAllObjectTypes() throws Exception {
        Map<String, Object> explicitRights = new HashMap<>();
        explicitRights.put("Use", true);
        explicitRights.put("View", true);

        List<RoleDsl.ObjectRights> objects = Arrays.asList(
                new RoleDsl.ObjectRights("DataProcessor.ЗагрузкаДанных", null, explicitRights, null),
                new RoleDsl.ObjectRights("Report.ОтчётПоПродажам", null, List.of("Use", "View"), null),
                new RoleDsl.ObjectRights("InformationRegister.КурсыВалют", "view", null, null)
        );

        RoleDsl dsl = new RoleDsl(
                "ПолныеПрава",
                "Полные права",
                null,
                null,
                null,
                null,
                objects,
                null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.DESIGNER);
        writer.create(dsl, tempDir);

        Path rightsXml = tempDir.resolve("Roles/ПолныеПрава/Ext/Rights.xml");
        String content = Files.readString(rightsXml);

        // Map-стиль прав
        assertThat(content).contains("<name>DataProcessor.ЗагрузкаДанных</name>");
        assertThat(content).contains("<name>Use</name>");

        // List-стиль прав
        assertThat(content).contains("<name>Report.ОтчётПоПродажам</name>");

        // InformationRegister с preset view
        assertThat(content).contains("<name>InformationRegister.КурсыВалют</name>");
        assertThat(content).contains("<name>Read</name>");
    }

    /**
     * Тест 4: JSON DSL roundtrip (чтение из JSON и генерация).
     */
    @Test
    void testJsonDslRoundtrip() throws Exception {
        String json = """
                {
                  "name": "ТестРоундтрип",
                  "synonym": "Тест роундтрип",
                  "comment": "Тестовый комментарий",
                  "setForNewObjects": false,
                  "setForAttributesByDefault": true,
                  "independentRightsOfChildObjects": false,
                  "objects": [
                    {
                      "name": "Document.ПриходнаяНакладная",
                      "preset": "edit"
                    },
                    {
                      "name": "Catalog.Номенклатура",
                      "preset": "view"
                    },
                    {
                      "name": "DataProcessor.Загрузка",
                      "rights": {"Use": true, "View": true}
                    }
                  ],
                  "templates": [
                    {
                      "name": "ДляОбъекта(Модификатор)",
                      "condition": "#Если &Модификатор = \\"Организация\\" #Тогда ОрганизацияОбъекта = &Организация #КонецЕсли"
                    }
                  ]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        RoleDsl dsl = mapper.readValue(json, RoleDsl.class);

        RoleWriter writer = new RoleWriter(OutputFormat.DESIGNER);
        writer.create(dsl, tempDir);

        // Проверка метаданных
        Path roleXml = tempDir.resolve("Roles/ТестРоундтрип.xml");
        assertThat(roleXml).exists();
        String metaContent = Files.readString(roleXml);
        assertThat(metaContent).contains("<Name>ТестРоундтрип</Name>");
        assertThat(metaContent).contains("<Comment>Тестовый комментарий</Comment>");

        // Проверка Rights.xml
        Path rightsXml = tempDir.resolve("Roles/ТестРоундтрип/Ext/Rights.xml");
        assertThat(rightsXml).exists();
        String rightsContent = Files.readString(rightsXml);

        // Объекты
        assertThat(rightsContent).contains("<name>Document.ПриходнаяНакладная</name>");
        assertThat(rightsContent).contains("<name>Catalog.Номенклатура</name>");
        assertThat(rightsContent).contains("<name>DataProcessor.Загрузка</name>");

        // Шаблон ограничения (RLS)
        assertThat(rightsContent).contains("<restrictionTemplate>");
        assertThat(rightsContent).contains("<name>ДляОбъекта(Модификатор)</name>");
        assertThat(rightsContent).contains("<condition>");
        // & в условии должен быть экранирован
        assertThat(rightsContent).contains("&amp;Модификатор");
    }
}

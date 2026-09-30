package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты EDT формата для всех writer'ов.
 */
class EdtFormatTest {

    @TempDir
    Path tempDir;

    // ==================== EPF (EDT) ====================

    @Test
    void testEdtEpfInit() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.EDT);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);

        // EDT-структура: src/ExternalDataProcessors/<name>/<name>.mdo
        Path mdoFile = tempDir.resolve("src/ExternalDataProcessors/ТестоваяОбработка/ТестоваяОбработка.mdo");
        assertThat(mdoFile).exists();

        String mdo = Files.readString(mdoFile);
        assertThat(mdo).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(mdo).contains("mdclass:ExternalDataProcessor");
        assertThat(mdo).contains("http://g5.1c.ru/v8/dt/metadata/mdclass");
        assertThat(mdo).contains("<name>ТестоваяОбработка</name>");
        assertThat(mdo).contains("uuid=");
        assertThat(mdo).contains("<producedTypes>");
        assertThat(mdo).contains("objectType");

        // synonym
        assertThat(mdo).contains("<key>ru</key>");
        assertThat(mdo).contains("<value>Тестовая обработка</value>");

        // Без BOM
        byte[] bytes = Files.readAllBytes(mdoFile);
        assertThat(bytes[0]).isEqualTo((byte) '<');

        // ObjectModule.bsl
        Path objectModule = tempDir.resolve("src/ExternalDataProcessors/ТестоваяОбработка/ObjectModule.bsl");
        assertThat(objectModule).exists();
    }

    @Test
    void testEdtEpfAddForm() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.EDT);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addForm("ТестоваяОбработка", "Форма", "Основная форма", tempDir, false);

        // Form.form (EDT format)
        Path formFile = tempDir.resolve("src/ExternalDataProcessors/ТестоваяОбработка/Forms/Форма/Form.form");
        assertThat(formFile).exists();

        String form = Files.readString(formFile);
        assertThat(form).contains("form:Form");
        assertThat(form).contains("http://g5.1c.ru/v8/dt/form");

        // Module.bsl
        Path module = tempDir.resolve("src/ExternalDataProcessors/ТестоваяОбработка/Forms/Форма/Module.bsl");
        assertThat(module).exists();
    }

    @Test
    void testEdtEpfAddTemplate() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.EDT);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Макет", "Макет", "SpreadsheetDocument", tempDir);

        // EDT использует .mxlx для SpreadsheetDocument
        Path templateBody = tempDir.resolve("src/ExternalDataProcessors/ТестоваяОбработка/Templates/Макет/Template.mxlx");
        assertThat(templateBody).exists();
    }

    // ==================== Role (EDT) ====================

    @Test
    void testEdtRoleMinimal() throws Exception {
        RoleDsl dsl = new RoleDsl(
                "ТестоваяРоль",
                "Тестовая роль",
                null, null, null, null, null, null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.EDT);
        writer.create(dsl, tempDir);

        // .mdo файл (EDT формат)
        Path mdoFile = tempDir.resolve("Roles/ТестоваяРоль/ТестоваяРоль.mdo");
        assertThat(mdoFile).exists();
        String mdo = Files.readString(mdoFile);
        assertThat(mdo).contains("mdclass:Role");
        assertThat(mdo).contains("http://g5.1c.ru/v8/dt/metadata/mdclass");
        assertThat(mdo).contains("<name>ТестоваяРоль</name>");
        assertThat(mdo).contains("uuid=");

        // Без BOM
        byte[] mdoBytes = Files.readAllBytes(mdoFile);
        assertThat(mdoBytes[0]).isEqualTo((byte) '<');

        // Rights.rights (EDT формат)
        Path rightsFile = tempDir.resolve("Roles/ТестоваяРоль/Rights.rights");
        assertThat(rightsFile).exists();
        String rights = Files.readString(rightsFile);
        assertThat(rights).contains("<Rights");
        assertThat(rights).contains("http://v8.1c.ru/8.2/roles");
        assertThat(rights).contains("<setForNewObjects>false</setForNewObjects>");
        assertThat(rights).contains("<setForAttributesByDefault>true</setForAttributesByDefault>");

        // Без BOM
        byte[] rightsBytes = Files.readAllBytes(rightsFile);
        assertThat(rightsBytes[0]).isEqualTo((byte) '<');
    }

    @Test
    void testEdtRoleWithRights() throws Exception {
        List<RoleDsl.ObjectRights> objects = Arrays.asList(
                new RoleDsl.ObjectRights("Document.ТестовыйДокумент", "edit", null, null),
                new RoleDsl.ObjectRights("Catalog.ТестовыйСправочник", "view", null, null)
        );

        RoleDsl dsl = new RoleDsl(
                "Редактор",
                "Редактор",
                "Роль редактора",
                false, true, false,
                objects, null
        );

        RoleWriter writer = new RoleWriter(OutputFormat.EDT);
        writer.create(dsl, tempDir);

        Path rightsFile = tempDir.resolve("Roles/Редактор/Rights.rights");
        String rights = Files.readString(rightsFile);

        // Права документа (edit)
        assertThat(rights).contains("<name>Document.ТестовыйДокумент</name>");
        assertThat(rights).contains("<name>Read</name>");
        assertThat(rights).contains("<name>Insert</name>");
        assertThat(rights).contains("<name>Posting</name>");

        // Права справочника (view)
        assertThat(rights).contains("<name>Catalog.ТестовыйСправочник</name>");
        assertThat(rights).contains("<name>View</name>");
    }

    @Test
    void testEdtRoleWithRlsTemplate() throws Exception {
        List<RoleDsl.RestrictionTemplate> templates = List.of(
                new RoleDsl.RestrictionTemplate(
                        "ДляОбъекта(Модификатор)",
                        "#Если &Модификатор = \"Организация\" #Тогда ОрганизацияОбъекта = &Организация #КонецЕсли"
                )
        );

        RoleDsl dsl = new RoleDsl(
                "СОграничениями",
                "С ограничениями",
                null, null, null, null, null,
                templates
        );

        RoleWriter writer = new RoleWriter(OutputFormat.EDT);
        writer.create(dsl, tempDir);

        Path rightsFile = tempDir.resolve("Roles/СОграничениями/Rights.rights");
        String rights = Files.readString(rightsFile);

        assertThat(rights).contains("<restrictionTemplate>");
        assertThat(rights).contains("<name>ДляОбъекта(Модификатор)</name>");
        assertThat(rights).contains("<condition>");
        // & экранируется XMLStreamWriter'ом
        assertThat(rights).contains("&amp;Модификатор");
    }

    // ==================== MXL (EDT) ====================

    @Test
    void testEdtMxlFormat() throws Exception {
        String json = """
                {
                  "columns": 2,
                  "areas": [
                    {
                      "name": "Шапка",
                      "rows": [
                        {
                          "cells": [
                            {"col": 1, "text": "Заголовок"},
                            {"col": 2, "text": "Значение"}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        MxlDsl dsl = mapper.readValue(json, MxlDsl.class);

        Path outputPath = tempDir.resolve("Template.mxlx");
        MxlWriter writer = new MxlWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);

        // Тот же XML что и Designer
        assertThat(content).contains("<document");
        assertThat(content).contains("http://v8.1c.ru/8.2/data/spreadsheet");
        assertThat(content).contains("<languageSettings>");
        assertThat(content).contains("<size>2</size>");
        assertThat(content).contains("<templateMode>true</templateMode>");
    }

    // ==================== SKD (EDT) ====================

    @Test
    void testEdtSkdFormat() throws Exception {
        String json = """
                {
                  "dataSets": [
                    {
                      "name": "НаборДанных1",
                      "query": "ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура",
                      "fields": [
                        {"field": "Ссылка", "title": "Ссылка"}
                      ]
                    }
                  ]
                }
                """;

        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);

        Path outputPath = tempDir.resolve("Template.dcs");
        SkdWriter writer = new SkdWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);

        // Тот же XML что и Designer
        assertThat(content).contains("<DataCompositionSchema");
        assertThat(content).contains("http://v8.1c.ru/8.1/data-composition-system/schema");
        assertThat(content).contains("<name>НаборДанных1</name>");
        assertThat(content).contains("ВЫБРАТЬ Ссылка ИЗ Справочник.Номенклатура");
    }

    // ==================== Form (EDT) ====================

    @Test
    void testEdtFormMinimal() throws Exception {
        FormDsl dsl = new FormDsl(null, null, null, null, null, null, null, null);

        Path outputPath = tempDir.resolve("Form.form");
        FormWriter writer = new FormWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        assertThat(outputPath).exists();
        String content = Files.readString(outputPath);

        assertThat(content).contains("form:Form");
        assertThat(content).contains("http://g5.1c.ru/v8/dt/form");
        assertThat(content).contains("http://g5.1c.ru/v8/dt/mcore");
        assertThat(content).contains("<autoCommandBar>");
        assertThat(content).contains("<name>ФормаКоманднаяПанель</name>");
        assertThat(content).contains("<id>-1</id>");
        assertThat(content).contains("<autoTitle>true</autoTitle>");
        assertThat(content).contains("<group>Vertical</group>");
        assertThat(content).contains("<commandInterface>");

        // Без BOM
        byte[] bytes = Files.readAllBytes(outputPath);
        assertThat(bytes[0]).isEqualTo((byte) '<');
    }

    @Test
    void testEdtFormWithElements() throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        Map<String, Object> inputField = new LinkedHashMap<>();
        inputField.put("input", "ПолеВвода1");
        inputField.put("path", "Объект.Реквизит1");
        elements.add(inputField);

        FormDsl dsl = new FormDsl(null, null, null, null, elements, null, null, null);

        Path outputPath = tempDir.resolve("Form.form");
        FormWriter writer = new FormWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        String content = Files.readString(outputPath);

        // EDT items с xsi:type
        assertThat(content).contains("items");
        assertThat(content).contains("<name>ПолеВвода1</name>");
        assertThat(content).contains("form:DataPath");
        assertThat(content).contains("<segments>Объект.Реквизит1</segments>");
    }

    @Test
    void testEdtFormWithCommands() throws Exception {
        List<FormDsl.Command> commands = List.of(
                new FormDsl.Command("Команда1", "Выполнить", "Команда1", null)
        );

        FormDsl dsl = new FormDsl(null, null, null, null, null, null, null, commands);

        Path outputPath = tempDir.resolve("Form.form");
        FormWriter writer = new FormWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        String content = Files.readString(outputPath);

        assertThat(content).contains("<formCommands>");
        assertThat(content).contains("<name>Команда1</name>");
        assertThat(content).contains("<handler>");
    }

    @Test
    void testEdtFormWithAttributes() throws Exception {
        List<FormDsl.Attribute> attributes = List.of(
                new FormDsl.Attribute("Объект", null, "cfg:ExternalDataProcessorObject.Обработка1", true, null)
        );

        FormDsl dsl = new FormDsl(null, null, null, null, null, attributes, null, null);

        Path outputPath = tempDir.resolve("Form.form");
        FormWriter writer = new FormWriter(OutputFormat.EDT);
        writer.create(dsl, outputPath);

        String content = Files.readString(outputPath);

        assertThat(content).contains("<attributes>");
        assertThat(content).contains("<name>Объект</name>");
        assertThat(content).contains("<main>true</main>");
    }
}

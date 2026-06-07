package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Roundtrip-тесты для EpfWriter.
 * 
 * Сравниваем сгенерированный XML с эталонными фикстурами из mdclasses.
 */
class EpfWriterTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    void testInitCreatesValidStructure() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        
        // Проверяем созданные файлы
        Path rootXml = tempDir.resolve("ТестоваяОбработка.xml");
        Path objectModule = tempDir.resolve("ТестоваяОбработка/Ext/ObjectModule.bsl");
        
        assertThat(rootXml).exists();
        assertThat(objectModule).exists();
        
        // Проверяем содержимое корневого XML
        String content = Files.readString(rootXml);
        assertThat(content).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(content).contains("<ExternalDataProcessor uuid=");
        assertThat(content).contains("<Name>ТестоваяОбработка</Name>");
        assertThat(content).contains("<v8:content>Тестовая обработка</v8:content>");
        assertThat(content).contains("<xr:ClassId>c3831ec8-d8d5-4f93-8a22-f9bfae07327f</xr:ClassId>");
        assertThat(content).contains("<ChildObjects>");
    }
    
    @Test
    void testAddFormCreatesValidStructure() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addForm("ТестоваяОбработка", "Форма", "Основная форма", tempDir, true);
        
        // Проверяем созданные файлы формы
        Path formMetadata = tempDir.resolve("ТестоваяОбработка/Forms/Форма.xml");
        Path formDefinition = tempDir.resolve("ТестоваяОбработка/Forms/Форма/Ext/Form.xml");
        Path formModule = tempDir.resolve("ТестоваяОбработка/Forms/Форма/Ext/Form/Module.bsl");
        
        assertThat(formMetadata).exists();
        assertThat(formDefinition).exists();
        assertThat(formModule).exists();
        
        // Проверяем метаданные формы
        String metadata = Files.readString(formMetadata);
        assertThat(metadata).contains("<Form uuid=");
        assertThat(metadata).contains("<Name>Форма</Name>");
        assertThat(metadata).contains("<v8:content>Основная форма</v8:content>");
        assertThat(metadata).contains("<FormType>Managed</FormType>");
        assertThat(metadata).contains("<UsePurposes>");
        assertThat(metadata).contains("PlatformApplication");
        
        // Проверяем описание формы
        String definition = Files.readString(formDefinition);
        assertThat(definition).contains("<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\"");
        assertThat(definition).contains("<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>");
        assertThat(definition).contains("<Attribute name=\"Объект\" id=\"1\">");
        assertThat(definition).contains("<v8:Type>cfg:ExternalDataProcessorObject.ТестоваяОбработка</v8:Type>");
        assertThat(definition).contains("<MainAttribute>true</MainAttribute>");
        
        // Проверяем обновление корневого XML
        String rootContent = Files.readString(tempDir.resolve("ТестоваяОбработка.xml"));
        assertThat(rootContent).contains("<Form>Форма</Form>");
        assertThat(rootContent).contains("<DefaultForm>ExternalDataProcessor.ТестоваяОбработка.Form.Форма</DefaultForm>");
    }
    
    @Test
    void testAddTemplateSpreadsheetDocument() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Макет", "Табличный документ", "SpreadsheetDocument", tempDir);
        
        // Проверяем созданные файлы макета
        Path templateMetadata = tempDir.resolve("ТестоваяОбработка/Templates/Макет.xml");
        Path templateBody = tempDir.resolve("ТестоваяОбработка/Templates/Макет/Ext/Template.xml");
        
        assertThat(templateMetadata).exists();
        assertThat(templateBody).exists();
        
        // Проверяем метаданные макета
        String metadata = Files.readString(templateMetadata);
        assertThat(metadata).contains("<Template uuid=");
        assertThat(metadata).contains("<Name>Макет</Name>");
        assertThat(metadata).contains("<v8:content>Табличный документ</v8:content>");
        assertThat(metadata).contains("<TemplateType>SpreadsheetDocument</TemplateType>");
        
        // Проверяем тело макета
        // TASK-171 D1: корень тела SpreadsheetDocument — <document> (как в реальном демо-макете
        // и как требует наш же validate --type mxl), а НЕ <SpreadsheetDocument>.
        String body = readStrippingBom(templateBody);
        assertThat(body).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(body).contains("<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">");
        assertThat(body).doesNotContain("<SpreadsheetDocument");

        // Проверяем обновление корневого XML
        String rootContent = Files.readString(tempDir.resolve("ТестоваяОбработка.xml"));
        assertThat(rootContent).contains("<Template>Макет</Template>");
    }
    
    @Test
    void testAddTemplateHTMLDocument() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Справка", "Справка", "HTMLDocument", tempDir);
        
        Path templateBody = tempDir.resolve("ТестоваяОбработка/Templates/Справка/Ext/Template.html");
        assertThat(templateBody).exists();

        // TASK-171 D1/W5: тело HTML теперь из единого источника ObjectContainerEditor.getTemplateBody
        // (минимальный валидный каркас; персональный <title> в каноне не предусмотрен).
        String body = readStrippingBom(templateBody);
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("<meta charset=\"UTF-8\">");
        assertThat(body).contains("<html>");
    }
    
    @Test
    void testCompleteEpfWithFormAndTemplates() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        
        // Создаём полную обработку
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addForm("ТестоваяОбработка", "Форма", "Основная форма", tempDir, true);
        writer.addTemplate("ТестоваяОбработка", "Макет", "Табличный документ", "SpreadsheetDocument", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Справка", "Справка", "HTMLDocument", tempDir);
        
        // Проверяем корневой XML
        String rootContent = Files.readString(tempDir.resolve("ТестоваяОбработка.xml"));
        
        // Проверяем порядок элементов в ChildObjects
        int formIndex = rootContent.indexOf("<Form>Форма</Form>");
        int template1Index = rootContent.indexOf("<Template>Макет</Template>");
        int template2Index = rootContent.indexOf("<Template>Справка</Template>");
        
        assertThat(formIndex).isGreaterThan(0);
        assertThat(template1Index).isGreaterThan(formIndex);
        assertThat(template2Index).isGreaterThan(template1Index);
        
        // Проверяем DefaultForm
        assertThat(rootContent).contains("<DefaultForm>ExternalDataProcessor.ТестоваяОбработка.Form.Форма</DefaultForm>");
    }
    
    @Test
    void testBomInMetadataFiles() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addForm("ТестоваяОбработка", "Форма", "Форма", tempDir, false);
        
        // Проверяем BOM в метаданных (корневой XML, Forms/*.xml)
        byte[] rootBytes = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка.xml"));
        assertThat(rootBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(rootBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(rootBytes[2]).isEqualTo((byte) 0xBF);
        
        byte[] formMetadataBytes = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Forms/Форма.xml"));
        assertThat(formMetadataBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(formMetadataBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(formMetadataBytes[2]).isEqualTo((byte) 0xBF);
        
        // TASK-172: Form.xml тоже с BOM — канон _Демо (все Ext/Form.xml = ef bb bf),
        // как и standalone FormWriter. Прежний ассерт закреплял баг (отсутствие BOM).
        byte[] formDefBytes = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Forms/Форма/Ext/Form.xml"));
        assertThat(formDefBytes[0]).isEqualTo((byte) 0xEF);
        assertThat(formDefBytes[1]).isEqualTo((byte) 0xBB);
        assertThat(formDefBytes[2]).isEqualTo((byte) 0xBF);
    }

    // ==================== TASK-171 D7: BOM в телах макетов ====================

    @Test
    void task171_templateBodiesHaveBom() throws Exception {
        // TASK-171: тела макетов (Template.xml/.html/.txt) должны писаться с UTF-8 BOM,
        // как реальные демо-макеты Designer. Раньше Files.writeString писал без BOM.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Макет", "Табличный документ", "SpreadsheetDocument", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Справка", "Справка", "HTMLDocument", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Текст", "Текст", "TextDocument", tempDir);

        byte[] mxlBody = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Templates/Макет/Ext/Template.xml"));
        assertBom(mxlBody);
        byte[] htmlBody = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Templates/Справка/Ext/Template.html"));
        assertBom(htmlBody);
        byte[] txtBody = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Templates/Текст/Ext/Template.txt"));
        assertBom(txtBody);
    }

    @Test
    void task171_textTemplateBodyIsEmpty() throws Exception {
        // TASK-171: текстовый макет — пустой (после BOM), а не псевдо-BSL-комментарий.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тестовая обработка", tempDir);
        writer.addTemplate("ТестоваяОбработка", "Текст", "Текст", "TextDocument", tempDir);

        byte[] txtBody = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Templates/Текст/Ext/Template.txt"));
        // только 3 байта BOM, без содержимого
        assertThat(txtBody.length).isEqualTo(3);
        assertBom(txtBody);
        String text = new String(txtBody, java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).doesNotContain("Текстовый документ");
    }

    private static void assertBom(byte[] bytes) {
        assertThat(bytes.length).isGreaterThanOrEqualTo(3);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
    }

    private static String readStrippingBom(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        if (raw.length >= 3 && raw[0] == (byte) 0xEF && raw[1] == (byte) 0xBB && raw[2] == (byte) 0xBF) {
            return new String(raw, 3, raw.length - 3, java.nio.charset.StandardCharsets.UTF_8);
        }
        return new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ==================== TASK-171 D1: корректный корень тела SpreadsheetDocument ====================

    @Test
    void task171_d1_epfSpreadsheetBodyRootIsDocument() throws Exception {
        // TASK-171 D1: epf add-template SpreadsheetDocument должен писать корень <document>
        // (NS http://v8.1c.ru/8.2/data/spreadsheet), как реальный демо-макет и как требует
        // наш собственный validate --type mxl (MXL-001: Expected root 'document').
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тест", tempDir);
        writer.addTemplate("ТестоваяОбработка", "ПФ_Макет", "Печатная форма", "SpreadsheetDocument", tempDir);

        Path body = tempDir.resolve("ТестоваяОбработка/Templates/ПФ_Макет/Ext/Template.xml");
        String content = readStrippingBom(body);
        assertThat(content).contains("<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">");
        assertThat(content).doesNotContain("<SpreadsheetDocument");
    }

    @Test
    void task171_d1_epfSpreadsheetViaMxlAlias() throws Exception {
        // Алиас "mxl" должен нормализоваться и тоже давать корень <document>.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тест", tempDir);
        writer.addTemplate("ТестоваяОбработка", "ПФ_Макет", "ПФ", "mxl", tempDir);

        Path body = tempDir.resolve("ТестоваяОбработка/Templates/ПФ_Макет/Ext/Template.xml");
        String content = readStrippingBom(body);
        assertThat(content).contains("<document xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">");
        // Метаданные макета должны нести канонический тип, а не алиас.
        String meta = Files.readString(tempDir.resolve("ТестоваяОбработка/Templates/ПФ_Макет.xml"));
        assertThat(meta).contains("<TemplateType>SpreadsheetDocument</TemplateType>");
    }

    // ==================== TASK-171 D3: DataCompositionSchema в EPF/ERF-ветке ====================

    @Test
    void task171_d3_epfDataCompositionSchemaDoesNotThrow() throws Exception {
        // TASK-171 D3: раньше epf add-template --type DataCompositionSchema падал
        // "Unknown template type". Теперь должен создаваться макет со схемой компоновки.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("ТестоваяОбработка", "Тест", tempDir);

        writer.addTemplate("ТестоваяОбработка", "Схема", "Схема компоновки", "DataCompositionSchema", tempDir);

        Path body = tempDir.resolve("ТестоваяОбработка/Templates/Схема/Ext/Template.xml");
        assertThat(body).exists();
        String content = readStrippingBom(body);
        assertThat(content).contains("<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\">");

        String meta = Files.readString(tempDir.resolve("ТестоваяОбработка/Templates/Схема.xml"));
        assertThat(meta).contains("<TemplateType>DataCompositionSchema</TemplateType>");
    }

    // ==================== TASK-171 D3/D6: MainDataCompositionSchema для ERF — префикс ExternalReport. ====================

    @Test
    void task171_d6_erfMainDcsPrefixIsExternalReport() throws Exception {
        // TASK-171 D6: для внешнего отчёта (ERF) при добавлении DCS-макета MainDataCompositionSchema
        // должна начинаться с ExternalReport. (а НЕ Report.), т.к. это EPF/ERF-раскладка.
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER, true); // isReport = true → ERF
        writer.init("ТестовыйОтчет", "Тестовый отчёт", tempDir);

        writer.addTemplate("ТестовыйОтчет", "ОсновнаяСхемаКомпоновкиДанных", "Схема",
                "DataCompositionSchema", tempDir);

        String root = Files.readString(tempDir.resolve("ТестовыйОтчет.xml"));
        assertThat(root).contains(
                "<MainDataCompositionSchema>ExternalReport.ТестовыйОтчет.Template.ОсновнаяСхемаКомпоновкиДанных</MainDataCompositionSchema>");
        assertThat(root).doesNotContain("<MainDataCompositionSchema>Report.");
        assertThat(root).contains("<Template>ОсновнаяСхемаКомпоновкиДанных</Template>");
    }

    @Test
    void task171_d3_epfNonReportSpreadsheetDoesNotTouchMainDcs() throws Exception {
        // Для обычной EPF (не отчёт) MainDataCompositionSchema нет вообще — проверяем, что
        // добавление DCS-макета не ломает корневой XML (элемент просто отсутствует).
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER, false); // обычная обработка
        writer.init("ОбычнаяОбработка", "Обработка", tempDir);
        writer.addTemplate("ОбычнаяОбработка", "Схема", "Схема", "DataCompositionSchema", tempDir);

        String root = Files.readString(tempDir.resolve("ОбычнаяОбработка.xml"));
        assertThat(root).doesNotContain("MainDataCompositionSchema");
        assertThat(root).contains("<Template>Схема</Template>");
    }

    @Test
    void addFormAndTemplateInheritExistingExternalObjectFormatVersion() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("Версия220", "Версия 2.20", tempDir);

        Path rootXml = tempDir.resolve("Версия220.xml");
        String root = io.github.onec.xmlgen.model.ConfigurationXmlReader.readContent(rootXml)
                .replace("version=\"2.17\"", "version=\"2.20\"");
        Files.write(rootXml, io.github.onec.xmlgen.io.Crlf.withBom(root));

        writer.addForm("Версия220", "Форма", "Форма", tempDir, true);
        writer.addTemplate("Версия220", "ПФ_Макет", "Макет", "SpreadsheetDocument", tempDir);

        assertThat(Files.readString(tempDir.resolve("Версия220/Forms/Форма.xml")))
                .contains("version=\"2.20\"");
        assertThat(Files.readString(tempDir.resolve("Версия220/Forms/Форма/Ext/Form.xml")))
                .contains("version=\"2.20\"");
        assertThat(Files.readString(tempDir.resolve("Версия220/Templates/ПФ_Макет.xml")))
                .contains("version=\"2.20\"");
    }
}

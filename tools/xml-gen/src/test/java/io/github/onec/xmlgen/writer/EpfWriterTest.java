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
        String body = Files.readString(templateBody);
        assertThat(body).contains("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(body).contains("<SpreadsheetDocument xmlns=\"http://v8.1c.ru/8.2/data/spreadsheet\">");
        
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
        
        String body = Files.readString(templateBody);
        assertThat(body).contains("<!DOCTYPE html>");
        assertThat(body).contains("<meta charset=\"UTF-8\">");
        assertThat(body).contains("<title>Справка</title>");
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
        
        // Проверяем ОТСУТСТВИЕ BOM в Form.xml
        byte[] formDefBytes = Files.readAllBytes(tempDir.resolve("ТестоваяОбработка/Forms/Форма/Ext/Form.xml"));
        assertThat(formDefBytes[0]).isNotEqualTo((byte) 0xEF);
        assertThat(formDefBytes[0]).isEqualTo((byte) '<'); // Начинается с '<'
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
}

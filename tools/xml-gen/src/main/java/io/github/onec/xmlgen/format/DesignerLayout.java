package io.github.onec.xmlgen.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Создание структуры каталогов формата Designer.
 */
public class DesignerLayout {
    
    /**
     * Создать структуру каталогов для EPF (Designer).
     * 
     * @param outputDir корневой каталог
     * @param epfName имя обработки
     * @return путь к корневому XML файлу
     */
    public static Path createEpfStructure(Path outputDir, String epfName) throws IOException {
        Files.createDirectories(outputDir);
        
        Path epfDir = outputDir.resolve(epfName);
        Files.createDirectories(epfDir);
        Files.createDirectories(epfDir.resolve("Ext"));
        Files.createDirectories(epfDir.resolve("Forms"));
        Files.createDirectories(epfDir.resolve("Templates"));
        
        return outputDir.resolve(epfName + ".xml");
    }
    
    /**
     * Создать структуру каталогов для формы (Designer).
     * 
     * @param formsDir каталог Forms
     * @param formName имя формы
     * @return путь к Form.xml
     */
    public static Path createFormStructure(Path formsDir, String formName) throws IOException {
        Files.createDirectories(formsDir);
        
        Path formDir = formsDir.resolve(formName);
        Files.createDirectories(formDir);
        
        Path extDir = formDir.resolve("Ext");
        Files.createDirectories(extDir);
        
        Path formSubDir = extDir.resolve("Form");
        Files.createDirectories(formSubDir);
        
        // Возвращаем путь к Form.xml
        return extDir.resolve("Form.xml");
    }
    
    /**
     * Создать структуру каталогов для макета (Designer).
     * 
     * @param templatesDir каталог Templates
     * @param templateName имя макета
     * @return путь к Template.xml
     */
    public static Path createTemplateStructure(Path templatesDir, String templateName) throws IOException {
        Files.createDirectories(templatesDir);
        
        Path templateDir = templatesDir.resolve(templateName);
        Files.createDirectories(templateDir);
        
        Path extDir = templateDir.resolve("Ext");
        Files.createDirectories(extDir);
        
        return extDir.resolve("Template.xml");
    }
}

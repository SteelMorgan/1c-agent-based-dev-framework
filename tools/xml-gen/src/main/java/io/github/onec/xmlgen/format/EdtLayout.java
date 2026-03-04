package io.github.onec.xmlgen.format;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Создание структуры каталогов формата EDT.
 */
public class EdtLayout {
    
    /**
     * Создать структуру каталогов для EPF (EDT).
     * 
     * @param outputDir корневой каталог
     * @param epfName имя обработки
     * @return путь к .mdo файлу
     */
    public static Path createEpfStructure(Path outputDir, String epfName) throws IOException {
        Path epfDir = outputDir.resolve("src/ExternalDataProcessors").resolve(epfName);
        Files.createDirectories(epfDir);
        Files.createDirectories(epfDir.resolve("Forms"));
        Files.createDirectories(epfDir.resolve("Templates"));
        
        return epfDir.resolve(epfName + ".mdo");
    }
    
    /**
     * Создать структуру каталогов для формы (EDT).
     * 
     * @param formsDir каталог Forms
     * @param formName имя формы
     * @return путь к Form.form
     */
    public static Path createFormStructure(Path formsDir, String formName) throws IOException {
        Files.createDirectories(formsDir);
        
        Path formDir = formsDir.resolve(formName);
        Files.createDirectories(formDir);
        
        return formDir.resolve("Form.form");
    }
    
    /**
     * Создать структуру каталогов для макета (EDT).
     * 
     * @param templatesDir каталог Templates
     * @param templateName имя макета
     * @return путь к Template.xml
     */
    public static Path createTemplateStructure(Path templatesDir, String templateName) throws IOException {
        Files.createDirectories(templatesDir);
        
        Path templateDir = templatesDir.resolve(templateName);
        Files.createDirectories(templateDir);
        
        return templateDir.resolve("Template.xml");
    }
}

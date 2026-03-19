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
        return createEpfStructure(outputDir, epfName, "ExternalDataProcessors");
    }

    /**
     * Создать структуру каталогов для EPF/ERF (EDT).
     *
     * @param outputDir корневой каталог
     * @param name имя обработки/отчёта
     * @param subdir подкаталог (ExternalDataProcessors или ExternalReports)
     * @return путь к .mdo файлу
     */
    public static Path createEpfStructure(Path outputDir, String name, String subdir) throws IOException {
        Path dir = outputDir.resolve("src/" + subdir).resolve(name);
        Files.createDirectories(dir);
        Files.createDirectories(dir.resolve("Forms"));
        Files.createDirectories(dir.resolve("Templates"));

        return dir.resolve(name + ".mdo");
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

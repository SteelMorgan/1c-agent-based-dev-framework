package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.UuidGenerator;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Генератор XML для роли 1С.
 */
public class RoleWriter extends XmlWriter {
    
    private final OutputFormat format;
    
    public RoleWriter(OutputFormat format) {
        this.format = format;
    }
    
    /**
     * Создать роль из DSL.
     * 
     * @param dsl JSON DSL роли
     * @param outputDir выходной каталог
     */
    public void create(RoleDsl dsl, Path outputDir) throws IOException, XMLStreamException {
        if (format == OutputFormat.DESIGNER) {
            createDesigner(dsl, outputDir);
        } else {
            throw new UnsupportedOperationException("EDT format not implemented yet");
        }
    }
    
    private void createDesigner(RoleDsl dsl, Path outputDir) throws IOException, XMLStreamException {
        String name = dsl.getName();
        String synonym = dsl.getSynonym() != null ? dsl.getSynonym() : name;
        String uuid = UuidGenerator.generate();
        
        // Создать структуру каталогов
        Path roleDir = outputDir.resolve("Roles").resolve(name);
        Files.createDirectories(roleDir.resolve("Ext"));
        
        // 1. Создать метаданные роли (Roles/<Name>.xml)
        createRoleMetadata(outputDir.resolve("Roles").resolve(name + ".xml"), name, synonym, 
                          dsl.getComment(), uuid);
        
        // 2. Создать Rights.xml
        createRightsXml(roleDir.resolve("Ext/Rights.xml"), dsl);
        
        System.out.println("Created role: " + name);
        System.out.println("  Metadata: " + outputDir.resolve("Roles").resolve(name + ".xml"));
        System.out.println("  Rights: " + roleDir.resolve("Ext/Rights.xml"));
    }
    
    /**
     * Создать метаданные роли (Roles/<Name>.xml).
     */
    private void createRoleMetadata(Path outputPath, String name, String synonym, String comment, String uuid) 
            throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();
        
        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");
        
        Map<String, String> rootAttrs = new HashMap<>();
        rootAttrs.put("version", "2.17");
        writeRootElement("MetaDataObject", allNamespaces, rootAttrs);
        
        // Role
        writer.writeCharacters("\t");
        writer.writeStartElement("Role");
        writer.writeAttribute("uuid", uuid);
        writer.writeCharacters("\n");
        indentLevel = 2;
        
        startElement("Properties");
        writeElement("Name", name);
        writeSynonym(synonym);
        writeElement("Comment", comment != null ? comment : "");
        endElement(); // Properties
        
        indentLevel = 1;
        writer.writeCharacters("\t");
        writer.writeEndElement(); // Role
        writer.writeCharacters("\n");
        
        writer.writeEndElement(); // MetaDataObject
        close();
    }
    
    /**
     * Создать Rights.xml.
     */
    private void createRightsXml(Path outputPath, RoleDsl dsl) throws IOException, XMLStreamException {
        createWriter(outputPath, true, new HashMap<>()); // С BOM
        writeXmlDeclaration();
        
        // Корневой элемент Rights
        writer.writeStartElement("Rights");
        writer.writeDefaultNamespace("http://v8.1c.ru/8.2/roles");
        writer.writeNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "Rights");
        writer.writeAttribute("version", "2.17");
        writer.writeCharacters("\n");
        indentLevel = 1;
        
        // Глобальные флаги
        writeElement("setForNewObjects", 
                    String.valueOf(dsl.getSetForNewObjects() != null ? dsl.getSetForNewObjects() : false));
        writeElement("setForAttributesByDefault", 
                    String.valueOf(dsl.getSetForAttributesByDefault() != null ? dsl.getSetForAttributesByDefault() : true));
        writeElement("independentRightsOfChildObjects", 
                    String.valueOf(dsl.getIndependentRightsOfChildObjects() != null ? dsl.getIndependentRightsOfChildObjects() : false));
        
        // Объекты с правами
        if (dsl.getObjects() != null) {
            for (RoleDsl.ObjectRights obj : dsl.getObjects()) {
                writeObjectRights(obj);
            }
        }
        
        // Шаблоны ограничений
        if (dsl.getTemplates() != null) {
            for (RoleDsl.RestrictionTemplate template : dsl.getTemplates()) {
                writeRestrictionTemplate(template);
            }
        }
        
        writer.writeEndElement(); // Rights
        close();
    }
    
    /**
     * Записать права объекта.
     */
    private void writeObjectRights(RoleDsl.ObjectRights obj) throws XMLStreamException {
        startElement("object");
        writeElement("name", obj.getName());
        
        // Получить список прав
        Map<String, Boolean> rights = resolveRights(obj);
        
        // Записать права
        for (Map.Entry<String, Boolean> right : rights.entrySet()) {
            startElement("right");
            writeElement("name", right.getKey());
            writeElement("value", String.valueOf(right.getValue()));
            
            // RLS (если есть)
            if (obj.getRls() != null && obj.getRls().containsKey(right.getKey())) {
                startElement("restrictionByCondition");
                writeElement("condition", obj.getRls().get(right.getKey()));
                endElement(); // restrictionByCondition
            }
            
            endElement(); // right
        }
        
        endElement(); // object
    }
    
    /**
     * Разрешить права объекта (применить пресет + переопределения).
     */
    private Map<String, Boolean> resolveRights(RoleDsl.ObjectRights obj) {
        Map<String, Boolean> result = new HashMap<>();
        
        // Применить пресет
        if (obj.getPreset() != null) {
            result.putAll(getPresetRights(obj.getName(), obj.getPreset()));
        }
        
        // Применить переопределения
        if (obj.getRights() != null) {
            if (obj.getRights() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rightsMap = (Map<String, Object>) obj.getRights();
                for (Map.Entry<String, Object> entry : rightsMap.entrySet()) {
                    result.put(entry.getKey(), Boolean.valueOf(entry.getValue().toString()));
                }
            } else if (obj.getRights() instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> rightsList = (List<String>) obj.getRights();
                for (String right : rightsList) {
                    result.put(right, true);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Получить права пресета для типа объекта.
     */
    private Map<String, Boolean> getPresetRights(String objectName, String preset) {
        Map<String, Boolean> rights = new HashMap<>();
        
        String objectType = objectName.split("\\.")[0];
        
        switch (preset.toLowerCase()) {
            case "view":
                rights.put("Read", true);
                rights.put("View", true);
                if (objectType.equals("Catalog") || objectType.equals("Document")) {
                    rights.put("InputByString", true);
                }
                if (objectType.equals("DataProcessor") || objectType.equals("Report")) {
                    rights.put("Use", true);
                }
                break;
                
            case "edit":
                // CRUD
                rights.put("Read", true);
                rights.put("Insert", true);
                rights.put("Update", true);
                rights.put("Delete", true);
                rights.put("View", true);
                rights.put("Edit", true);
                
                // Interactive
                rights.put("InteractiveInsert", true);
                rights.put("InteractiveDelete", true);
                rights.put("InteractiveSetDeletionMark", true);
                rights.put("InteractiveClearDeletionMark", true);
                
                if (objectType.equals("Catalog") || objectType.equals("Document")) {
                    rights.put("InputByString", true);
                    rights.put("InteractiveDeleteMarked", true);
                }
                
                // Posting для документов
                if (objectType.equals("Document")) {
                    rights.put("Posting", true);
                    rights.put("UndoPosting", true);
                    rights.put("InteractivePosting", true);
                    rights.put("InteractivePostingRegular", true);
                    rights.put("InteractiveUndoPosting", true);
                    rights.put("InteractiveChangeOfPosted", true);
                }
                break;
                
            case "full":
                // Все права для типа (упрощённая версия)
                rights.putAll(getPresetRights(objectName, "edit"));
                break;
        }
        
        return rights;
    }
    
    /**
     * Записать шаблон ограничения.
     */
    private void writeRestrictionTemplate(RoleDsl.RestrictionTemplate template) throws XMLStreamException {
        startElement("restrictionTemplate");
        writeElement("name", template.getName());
        
        // Экранировать & в условии
        String condition = template.getCondition().replace("&", "&amp;");
        writeElement("condition", condition);
        
        endElement(); // restrictionTemplate
    }
    
    /**
     * Записать синоним (многоязычный).
     */
    private void writeSynonym(String text) throws XMLStreamException {
        startElement("Synonym");
        startElement("v8:item");
        writeElement("v8:lang", "ru");
        writeElement("v8:content", text);
        endElement(); // v8:item
        endElement(); // Synonym
    }
}

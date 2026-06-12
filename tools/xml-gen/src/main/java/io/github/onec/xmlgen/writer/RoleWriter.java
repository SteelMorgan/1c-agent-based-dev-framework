package io.github.onec.xmlgen.writer;

import com.github._1c_syntax.bsl.mdo.support.RoleRight;
import com.github._1c_syntax.bsl.types.MDOType;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.editor.ConfigEditor;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.UuidGenerator;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
            createEdt(dsl, outputDir);
        }
    }
    
    private void createDesigner(RoleDsl dsl, Path outputDir) throws IOException, XMLStreamException {
        String name = dsl.getName();
        String synonym = dsl.getSynonym() != null ? dsl.getSynonym() : name;
        String uuid = UuidGenerator.generate();

        // TASK-171: версия формата сериализации берётся детерминированно из Configuration.xml
        // конфигурации, а не хардкодится 2.17. Несовпадение версии роли с конфигурацией
        // (на этом проекте — 2.20) — потенциальный отказ Конфигуратора при загрузке/сравнении.
        // Образец резолва — FormWriter.resolveFormatVersion; читаем через ConfigurationXmlReader.
        // outputDir — корень конфигурации (тут же лежит Configuration.xml и сюда пишутся Roles/).
        String formatVersion = ConfigurationXmlReader.readFormatVersion(
                outputDir.resolve("Configuration.xml"));
        preflightRegistration(outputDir.resolve("Configuration.xml"), name);

        // Создать структуру каталогов
        Path roleDir = outputDir.resolve("Roles").resolve(name);
        Files.createDirectories(roleDir.resolve("Ext"));

        // 1. Создать метаданные роли (Roles/<Name>.xml)
        createRoleMetadata(outputDir.resolve("Roles").resolve(name + ".xml"), name, synonym,
                          dsl.getComment(), uuid, formatVersion);

        // 2. Создать Rights.xml
        createRightsXml(roleDir.resolve("Ext/Rights.xml"), dsl, formatVersion);

        // 3. Зарегистрировать роль в Configuration.xml, если компиляция идёт из корня выгрузки.
        registerInConfiguration(outputDir.resolve("Configuration.xml"), name);
        
        System.out.println("Created role: " + name);
        System.out.println("  Metadata: " + outputDir.resolve("Roles").resolve(name + ".xml"));
        System.out.println("  Rights: " + roleDir.resolve("Ext/Rights.xml"));
    }
    
    /**
     * Создать метаданные роли (Roles/<Name>.xml).
     */
    private void createRoleMetadata(Path outputPath, String name, String synonym, String comment, String uuid,
                                    String formatVersion)
            throws IOException, XMLStreamException {
        createWriter(outputPath, true, METADATA_NAMESPACES);
        writeXmlDeclaration();

        Map<String, String> allNamespaces = new HashMap<>(METADATA_NAMESPACES);
        allNamespaces.put("xr", "http://v8.1c.ru/8.3/xcf/readable");
        allNamespaces.put("xen", "http://v8.1c.ru/8.3/xcf/enums");
        allNamespaces.put("xpr", "http://v8.1c.ru/8.3/xcf/predef");

        Map<String, String> rootAttrs = new HashMap<>();
        // TASK-171: версия формата — из Configuration.xml (раньше хардкод 2.17).
        rootAttrs.put("version", formatVersion);
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
    private void createRightsXml(Path outputPath, RoleDsl dsl, String formatVersion)
            throws IOException, XMLStreamException {
        createWriter(outputPath, true, new HashMap<>()); // С BOM
        writeXmlDeclaration();

        // Корневой элемент Rights
        writer.writeStartElement("Rights");
        writer.writeDefaultNamespace("http://v8.1c.ru/8.2/roles");
        writer.writeNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "Rights");
        // TASK-171: версия формата — из Configuration.xml (раньше хардкод 2.17).
        writer.writeAttribute("version", formatVersion);
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
     * Зарегистрировать роль в {@code Configuration.xml/ChildObjects}, если файл конфигурации
     * доступен рядом с {@code outputDir}. Без этой записи Designer не видит созданную роль
     * как объект конфигурации.
     */
    private void registerInConfiguration(Path configurationXml, String name) {
        if (!Files.isRegularFile(configurationXml)) {
            System.err.println("WARN: Configuration.xml not found at " + configurationXml
                    + " — role Role." + name + " was not registered in ChildObjects.");
            return;
        }
        try {
            ConfigEditor editor = new ConfigEditor(configurationXml);
            editor.addChildObject("Role." + name);
            editor.save();
        } catch (IOException e) {
            throw new RuntimeException("Failed to register Role." + name
                    + " in Configuration.xml: " + e.getMessage(), e);
        }
    }

    private void preflightRegistration(Path configurationXml, String name) {
        if (!Files.isRegularFile(configurationXml)) {
            return;
        }
        try {
            ConfigEditor editor = new ConfigEditor(configurationXml);
            editor.setSkipFileCheck(true);
            editor.addChildObject("Role." + name);
        } catch (IOException e) {
            throw new RuntimeException("Failed to preflight Role." + name
                    + " registration in Configuration.xml: " + e.getMessage(), e);
        }
    }
    
    /**
     * Записать права объекта.
     */
    private void writeObjectRights(RoleDsl.ObjectRights obj) throws XMLStreamException {
        String objectName = RoleDsl.normalizeObjectName(obj.getName());
        Map<String, String> rls = normalizeRls(obj.getRls());

        startElement("object");
        writeElement("name", objectName);
        
        // Получить список прав
        Map<String, Boolean> rights = resolveRights(objectName, obj);
        
        // Записать права
        for (Map.Entry<String, Boolean> right : rights.entrySet()) {
            startElement("right");
            writeElement("name", right.getKey());
            writeElement("value", String.valueOf(right.getValue()));
            
            // RLS (если есть)
            if (rls != null && rls.containsKey(right.getKey())) {
                startElement("restrictionByCondition");
                writeElement("condition", rls.get(right.getKey()));
                endElement(); // restrictionByCondition
            }
            
            endElement(); // right
        }
        
        endElement(); // object
    }
    
    /**
     * Разрешить права объекта (применить пресет + переопределения).
     */
    private Map<String, Boolean> resolveRights(String objectName, RoleDsl.ObjectRights obj) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        
        // Применить пресет
        if (obj.getPreset() != null) {
            result.putAll(getPresetRights(objectName, obj.getPreset()));
        }
        
        // Применить переопределения
        if (obj.getRights() != null) {
            if (obj.getRights() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rightsMap = (Map<String, Object>) obj.getRights();
                List<Map.Entry<String, Object>> entries = new ArrayList<>(rightsMap.entrySet());
                entries.sort(Comparator.comparingInt(e ->
                        roleRightOrder(RoleDsl.normalizeRightNameStrict(e.getKey()))));
                for (Map.Entry<String, Object> entry : entries) {
                    result.put(RoleDsl.normalizeRightNameStrict(entry.getKey()),
                            Boolean.valueOf(entry.getValue().toString()));
                }
            } else if (obj.getRights() instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> rightsList = (List<String>) obj.getRights();
                for (String right : rightsList) {
                    result.put(RoleDsl.normalizeRightNameStrict(right), true);
                }
            }
        }
        
        return result;
    }
    
    /** Вспомогательный метод: добавить право в карту по XML-имени. */
    private static void grant(Map<String, Boolean> rights, RoleRight... roleRights) {
        for (RoleRight r : roleRights) {
            rights.put(r.fullName().getEn(), true);
        }
    }

    /** Определить MDOType из имени объекта (Catalog.Товары → CATALOG). */
    private static MDOType resolveObjectType(String objectName) {
        String typePart = objectName.split("\\.")[0];
        return MDOType.fromValue(typePart).orElse(MDOType.UNKNOWN);
    }
    
    /**
     * Получить права пресета для типа объекта.
     * Использует enum-ы RoleRight и MDOType из mdclasses.
     */
    private Map<String, Boolean> getPresetRights(String objectName, String preset) {
        Map<String, Boolean> rights = new LinkedHashMap<>();
        MDOType mdoType = resolveObjectType(objectName);
        String typePart = objectName.split("\\.")[0];
        
        // Keep presets within the top-level rights matrix from 1c-role-spec.md.
        // Some types do not have Read/View or CRUD rights at all.
        boolean isRegister = (mdoType == MDOType.INFORMATION_REGISTER
                || mdoType == MDOType.ACCUMULATION_REGISTER
                || mdoType == MDOType.ACCOUNTING_REGISTER);

        switch (preset.toLowerCase()) {
            case "view":
                if (grantSimplePreset(rights, typePart, false)) {
                    break;
                }
                grant(rights, RoleRight.READ, RoleRight.VIEW);
                if (mdoType == MDOType.CATALOG || mdoType == MDOType.DOCUMENT) {
                    grant(rights, RoleRight.INPUT_BY_STRING);
                }
                break;

            case "edit":
                if (grantSimplePreset(rights, typePart, true)) {
                    break;
                }
                if (isRegister) {
                    grant(rights, RoleRight.READ, RoleRight.UPDATE, RoleRight.VIEW, RoleRight.EDIT);
                    break;
                }
                grant(rights,
                    RoleRight.READ, RoleRight.INSERT, RoleRight.UPDATE, RoleRight.DELETE,
                    RoleRight.VIEW, RoleRight.EDIT,
                    RoleRight.INTERACTIVE_INSERT, RoleRight.INTERACTIVE_DELETE,
                    RoleRight.INTERACTIVE_SET_DELETION_MARK, RoleRight.INTERACTIVE_CLEAR_DELETION_MARK
                );

                if (mdoType == MDOType.CATALOG || mdoType == MDOType.DOCUMENT) {
                    grant(rights, RoleRight.INPUT_BY_STRING, RoleRight.INTERACTIVE_DELETE_MARKED);
                }

                if (mdoType == MDOType.DOCUMENT) {
                    grant(rights,
                        RoleRight.POSTING, RoleRight.UNDO_POSTING,
                        RoleRight.INTERACTIVE_POSTING, RoleRight.INTERACTIVE_POSTING_REGULAR,
                        RoleRight.INTERACTIVE_UNDO_POSTING, RoleRight.INTERACTIVE_CHANGE_OF_POSTED
                    );
                }
                break;
                
            case "full":
                rights.putAll(getPresetRights(objectName, "edit"));
                break;
        }
        
        return rights;
    }

    private boolean grantSimplePreset(Map<String, Boolean> rights, String typePart, boolean editPreset) {
        switch (typePart) {
            case "DataProcessor", "Report" -> grant(rights, RoleRight.USE, RoleRight.VIEW);
            case "CommonForm", "CommonCommand", "Subsystem", "FilterCriterion" -> grant(rights, RoleRight.VIEW);
            case "DocumentJournal" -> grant(rights, RoleRight.READ, RoleRight.VIEW);
            case "Sequence" -> {
                grant(rights, RoleRight.READ);
                if (editPreset) {
                    grant(rights, RoleRight.UPDATE);
                }
            }
            case "WebService", "HTTPService", "IntegrationService" -> grant(rights, RoleRight.USE);
            case "SessionParameter" -> {
                grant(rights, RoleRight.GET);
                if (editPreset) {
                    grant(rights, RoleRight.SET);
                }
            }
            case "CommonAttribute" -> {
                grant(rights, RoleRight.VIEW);
                if (editPreset) {
                    grant(rights, RoleRight.EDIT);
                }
            }
            case "Constant" -> {
                grant(rights, RoleRight.READ, RoleRight.VIEW);
                if (editPreset) {
                    grant(rights, RoleRight.UPDATE, RoleRight.EDIT);
                }
            }
            case "CalculationRegister" -> grant(rights, RoleRight.READ, RoleRight.VIEW);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static Map<String, String> normalizeRls(Map<String, String> rls) {
        if (rls == null) {
            return null;
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rls.entrySet()) {
            result.put(RoleDsl.normalizeRightNameStrict(entry.getKey()), entry.getValue());
        }
        return result;
    }

    private static int roleRightOrder(String rightName) {
        RoleRight[] values = RoleRight.values();
        for (int i = 0; i < values.length; i++) {
            if (values[i] != RoleRight.UNKNOWN && values[i].fullName().getEn().equals(rightName)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }
    
    /**
     * Записать шаблон ограничения.
     */
    private void writeRestrictionTemplate(RoleDsl.RestrictionTemplate template) throws XMLStreamException {
        startElement("restrictionTemplate");
        writeElement("name", template.getName());
        writeElement("condition", template.getCondition());
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
    
    // ==================== EDT format ====================
    
    private void createEdt(RoleDsl dsl, Path outputDir) throws IOException, XMLStreamException {
        String name = dsl.getName();
        String uuid = UuidGenerator.generate();
        
        // Создать структуру каталогов EDT: Roles/<Name>/
        Path roleDir = outputDir.resolve("Roles").resolve(name);
        Files.createDirectories(roleDir);
        
        // 1. Создать .mdo файл (Roles/<Name>/<Name>.mdo)
        createEdtMdo(roleDir.resolve(name + ".mdo"), name, uuid);
        
        // 2. Создать Rights.rights (Roles/<Name>/Rights.rights)
        createRightsRights(roleDir.resolve("Rights.rights"), dsl);
        
        System.out.println("Created role (EDT): " + name);
        System.out.println("  MDO: " + roleDir.resolve(name + ".mdo"));
        System.out.println("  Rights: " + roleDir.resolve("Rights.rights"));
    }
    
    /**
     * Создать EDT .mdo файл для роли.
     * Формат: <mdclass:Role xmlns:mdclass="..." uuid="..."><name>...</name></mdclass:Role>
     */
    private void createEdtMdo(Path outputPath, String name, String uuid) throws IOException, XMLStreamException {
        createWriter(outputPath, false, new HashMap<>()); // БЕЗ BOM для EDT
        writeXmlDeclaration();
        
        writer.writeStartElement("mdclass", "Role", "http://g5.1c.ru/v8/dt/metadata/mdclass");
        writer.writeNamespace("mdclass", "http://g5.1c.ru/v8/dt/metadata/mdclass");
        writer.writeAttribute("uuid", uuid);
        writer.writeCharacters("\n");
        indentLevel = 1;
        
        writeElement("name", name);
        
        writer.writeEndElement(); // mdclass:Role
        close();
    }
    
    /**
     * Создать Rights.rights (EDT формат прав).
     * Структурно идентичен Designer Rights.xml, но без атрибута version.
     */
    private void createRightsRights(Path outputPath, RoleDsl dsl) throws IOException, XMLStreamException {
        createWriter(outputPath, false, new HashMap<>()); // БЕЗ BOM для EDT
        writeXmlDeclaration();
        
        writer.writeStartElement("Rights");
        writer.writeDefaultNamespace("http://v8.1c.ru/8.2/roles");
        writer.writeNamespace("xsi", "http://www.w3.org/2001/XMLSchema-instance");
        writer.writeAttribute("http://www.w3.org/2001/XMLSchema-instance", "type", "Rights");
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
}

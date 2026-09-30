package io.github.onec.xmlgen.writer;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Базовый класс для генерации XML метаданных 1С.
 * 
 * Обеспечивает:
 * - UTF-8 BOM для метаданных (опционально)
 * - Namespace registry
 * - Indent (форматирование)
 * - Wrapper методы для XMLStreamWriter
 */
public abstract class XmlWriter {
    
    // UTF-8 BOM
    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    
    // Стандартные namespaces для метаданных 1С
    protected static final Map<String, String> METADATA_NAMESPACES = new HashMap<>();
    static {
        METADATA_NAMESPACES.put("", "http://v8.1c.ru/8.3/MDClasses");
        METADATA_NAMESPACES.put("app", "http://v8.1c.ru/8.2/managed-application/core");
        METADATA_NAMESPACES.put("cfg", "http://v8.1c.ru/8.1/data/enterprise/current-config");
        METADATA_NAMESPACES.put("cmi", "http://v8.1c.ru/8.2/managed-application/cmi");
        METADATA_NAMESPACES.put("ent", "http://v8.1c.ru/8.1/data/enterprise");
        METADATA_NAMESPACES.put("lf", "http://v8.1c.ru/8.2/managed-application/logform");
        METADATA_NAMESPACES.put("style", "http://v8.1c.ru/8.1/data/ui/style");
        METADATA_NAMESPACES.put("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        METADATA_NAMESPACES.put("v8", "http://v8.1c.ru/8.1/data/core");
        METADATA_NAMESPACES.put("v8ui", "http://v8.1c.ru/8.1/data/ui");
        METADATA_NAMESPACES.put("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        METADATA_NAMESPACES.put("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        METADATA_NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
        METADATA_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }
    
    // Namespaces для Form.xml
    protected static final Map<String, String> FORM_NAMESPACES = new HashMap<>();
    static {
        FORM_NAMESPACES.put("", "http://v8.1c.ru/8.3/xcf/logform");
        FORM_NAMESPACES.put("app", "http://v8.1c.ru/8.2/managed-application/core");
        FORM_NAMESPACES.put("cfg", "http://v8.1c.ru/8.1/data/enterprise/current-config");
        FORM_NAMESPACES.put("cmi", "http://v8.1c.ru/8.2/managed-application/cmi");
        FORM_NAMESPACES.put("ent", "http://v8.1c.ru/8.1/data/enterprise");
        FORM_NAMESPACES.put("style", "http://v8.1c.ru/8.1/data/ui/style");
        FORM_NAMESPACES.put("sys", "http://v8.1c.ru/8.1/data/ui/fonts/system");
        FORM_NAMESPACES.put("v8", "http://v8.1c.ru/8.1/data/core");
        FORM_NAMESPACES.put("v8ui", "http://v8.1c.ru/8.1/data/ui");
        FORM_NAMESPACES.put("web", "http://v8.1c.ru/8.1/data/ui/colors/web");
        FORM_NAMESPACES.put("win", "http://v8.1c.ru/8.1/data/ui/colors/windows");
        FORM_NAMESPACES.put("xs", "http://www.w3.org/2001/XMLSchema");
        FORM_NAMESPACES.put("xsi", "http://www.w3.org/2001/XMLSchema-instance");
    }
    
    protected XMLStreamWriter writer;
    protected int indentLevel = 0;
    private static final String INDENT = "\t";
    
    /**
     * Создать writer с BOM и namespaces.
     * 
     * @param outputPath путь к выходному файлу
     * @param withBom добавить UTF-8 BOM
     * @param namespaces namespaces для корневого элемента
     */
    protected void createWriter(Path outputPath, boolean withBom, Map<String, String> namespaces) throws IOException, XMLStreamException {
        // Создать родительские директории, если они есть
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        
        OutputStream rawOs = Files.newOutputStream(outputPath);

        // Записать BOM если нужно — ДО обёртки фильтра, чтобы сам BOM не трогался.
        if (withBom) {
            rawOs.write(UTF8_BOM);
        }

        //++agent TASK-172 [02.06.2026 07:12:00]
        // Канон Designer (_Демо) — CRLF. StAX эмитит переводы строк как writeCharacters("\n")
        // (одиночный LF); навешиваем CRLF на байтовой границе единым фильтром, а не правкой
        // каждого литерала "\n" в XmlWriter/подклассах.
        OutputStream os = io.github.onec.xmlgen.io.Crlf.wrapLfToCrlf(rawOs);
        //++agent TASK-172

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        writer = factory.createXMLStreamWriter(os, "UTF-8");
    }
    
    /**
     * Записать XML declaration.
     */
    protected void writeXmlDeclaration() throws XMLStreamException {
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать корневой элемент с namespaces.
     */
    protected void writeRootElement(String elementName, Map<String, String> namespaces, Map<String, String> attributes) throws XMLStreamException {
        writer.writeStartElement(elementName);
        
        // Записать namespaces
        for (Map.Entry<String, String> ns : namespaces.entrySet()) {
            if (ns.getKey().isEmpty()) {
                writer.writeDefaultNamespace(ns.getValue());
            } else {
                writer.writeNamespace(ns.getKey(), ns.getValue());
            }
        }
        
        // Записать атрибуты
        if (attributes != null) {
            for (Map.Entry<String, String> attr : attributes.entrySet()) {
                writer.writeAttribute(attr.getKey(), attr.getValue());
            }
        }
        
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать простой элемент с текстом.
     */
    protected void writeElement(String name, String text) throws XMLStreamException {
        writeIndent();
        writer.writeStartElement(name);
        if (text != null && !text.isEmpty()) {
            writer.writeCharacters(text);
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
    
    /**
     * Начать элемент с отступом.
     */
    protected void startElement(String name) throws XMLStreamException {
        writeIndent();
        writer.writeStartElement(name);
        writer.writeCharacters("\n");
        indentLevel++;
    }
    
    /**
     * Закрыть элемент с отступом.
     */
    protected void endElement() throws XMLStreamException {
        indentLevel--;
        writeIndent();
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
    
    /**
     * Записать отступ.
     */
    private void writeIndent() throws XMLStreamException {
        for (int i = 0; i < indentLevel; i++) {
            writer.writeCharacters(INDENT);
        }
    }
    
    /**
     * Записать пустой элемент.
     */
    protected void writeEmptyElement(String name) throws XMLStreamException {
        writer.writeEmptyElement(name);
        writer.writeCharacters("\n");
    }
    
    /**
     * Закрыть writer.
     */
    protected void close() throws XMLStreamException {
        if (writer != null) {
            writer.writeEndDocument();
            writer.flush();
            writer.close();
        }
    }
}

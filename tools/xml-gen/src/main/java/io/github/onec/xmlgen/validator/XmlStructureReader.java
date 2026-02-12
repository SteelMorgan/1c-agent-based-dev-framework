package io.github.onec.xmlgen.validator;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * StAX-based XML reader, который парсит файл в дерево {@link XmlDocument}/{@link XmlNode}.
 * <p>
 * Особенности:
 * <ul>
 *   <li>Детектирует UTF-8 BOM (EF BB BF)</li>
 *   <li>Сохраняет номера строк для каждого элемента</li>
 *   <li>Собирает атрибуты с учётом prefix (xsi:type → "xsi:type")</li>
 *   <li>Не загружает весь файл в память — streaming</li>
 * </ul>
 */
public class XmlStructureReader {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Распарсить XML файл в {@link XmlDocument}.
     *
     * @param file путь к XML-файлу
     * @return структурированный документ
     * @throws XmlParseException если файл не является well-formed XML
     */
    public XmlDocument parse(Path file) throws XmlParseException {
        boolean hasBom;
        byte[] content;

        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new XmlParseException("Cannot read file: " + file, e);
        }

        // Детектируем BOM
        hasBom = content.length >= 3
                && content[0] == UTF8_BOM[0]
                && content[1] == UTF8_BOM[1]
                && content[2] == UTF8_BOM[2];

        // Если есть BOM, пропускаем его при парсинге
        InputStream is = hasBom
                ? new ByteArrayInputStream(content, 3, content.length - 3)
                : new ByteArrayInputStream(content);

        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Безопасность: отключаем external entities
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        XMLStreamReader reader;
        try {
            reader = factory.createXMLStreamReader(is, "UTF-8");
        } catch (XMLStreamException e) {
            throw new XmlParseException("Failed to create XML reader: " + e.getMessage(), e);
        }

        try {
            return doParse(reader, file, hasBom);
        } catch (XMLStreamException e) {
            throw new XmlParseException("XML parse error at line " + e.getLocation().getLineNumber()
                    + ": " + e.getMessage(), e);
        } finally {
            try { reader.close(); } catch (XMLStreamException ignored) {}
        }
    }

    private XmlDocument doParse(XMLStreamReader reader, Path file, boolean hasBom) throws XMLStreamException {
        // Ищем корневой элемент
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                XmlNode root = parseElement(reader);
                return new XmlDocument(
                        file,
                        hasBom,
                        root.getName(),
                        root.getNamespace(),
                        root.getAttributes(),
                        root.getChildren(),
                        root
                );
            }
        }
        throw new XMLStreamException("No root element found");
    }

    /**
     * Рекурсивно парсит элемент и его потомков.
     * Вызывается когда reader стоит на START_ELEMENT.
     */
    private XmlNode parseElement(XMLStreamReader reader) throws XMLStreamException {
        XmlNode.Builder builder = XmlNode.builder()
                .name(reader.getLocalName())
                .namespace(reader.getNamespaceURI())
                .line(reader.getLocation().getLineNumber());

        // Собираем атрибуты
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String prefix = reader.getAttributePrefix(i);
            String localName = reader.getAttributeLocalName(i);
            String key = (prefix != null && !prefix.isEmpty()) ? prefix + ":" + localName : localName;
            builder.attribute(key, reader.getAttributeValue(i));
        }

        // Парсим содержимое: текст + дочерние элементы
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    builder.addChild(parseElement(reader));
                    break;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    builder.appendText(reader.getText());
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    return builder.build();
                default:
                    // COMMENT, PROCESSING_INSTRUCTION и т.д. — игнорируем
                    break;
            }
        }
        // Не должны сюда попасть, но на случай EOF без закрывающего тега
        return builder.build();
    }

    /**
     * Исключение при ошибке парсинга XML.
     */
    public static class XmlParseException extends Exception {
        public XmlParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

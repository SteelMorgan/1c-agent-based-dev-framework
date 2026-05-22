package io.github.onec.xmlgen.writer.skd;

import io.github.onec.xmlgen.dsl.SkdDsl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.util.Map;

/**
 * Записывает {@code <role>...</role>} блок поля СКД на основе DSL.
 *
 * <p>Поддерживает роли: {@code @account}, {@code @balance}, {@code @period},
 * {@code @dimension}, {@code @resource} плюс key-value атрибуты
 * (например, {@code balanceGroupName=ОстаткиСчета}).</p>
 */
public final class SkdFieldRoleWriter {

    private SkdFieldRoleWriter() {
    }

    /**
     * Сгенерировать {@code <dcscom:role>} для поля.
     *
     * @param writer    XML writer
     * @param role      имя роли (с/без префикса {@code @})
     * @param attrs     дополнительные атрибуты роли (или {@code null})
     * @param indent    префикс отступа
     */
    public static void write(XMLStreamWriter writer, String role,
                             Map<String, Object> attrs, String indent)
            throws XMLStreamException {
        if (role == null || role.isEmpty()) {
            return;
        }
        String normalized = role.startsWith("@") ? role.substring(1) : role;

        writer.writeCharacters(indent);
        writer.writeStartElement("role");
        writer.writeCharacters("\n");

        // Базовый флаг роли.
        writeRoleFlag(writer, normalized, indent + "\t");

        // Дополнительные атрибуты роли.
        if (attrs != null && !attrs.isEmpty()) {
            for (Map.Entry<String, Object> e : attrs.entrySet()) {
                writeKvElement(writer, e.getKey(), e.getValue(), indent + "\t");
            }
        }

        writer.writeCharacters(indent);
        writer.writeEndElement(); // role
        writer.writeCharacters("\n");
    }

    private static void writeRoleFlag(XMLStreamWriter writer, String roleName, String indent)
            throws XMLStreamException {
        switch (roleName.toLowerCase()) {
            case "dimension":
                writeSimple(writer, "dimension", "true", indent);
                break;
            case "resource":
                writeSimple(writer, "ignoreNullValues", "true", indent);
                break;
            case "account":
                writeSimple(writer, "accountFieldName", "Счет", indent);
                break;
            case "balance":
                writeSimple(writer, "balance", "true", indent);
                break;
            case "period":
                writeSimple(writer, "periodNumber", "1", indent);
                writeSimple(writer, "periodType", "Main", indent);
                break;
            default:
                writeSimple(writer, "name", roleName, indent);
                break;
        }
    }

    private static void writeKvElement(XMLStreamWriter writer, String key, Object value, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement(key);
        if (value != null) {
            writer.writeCharacters(value.toString());
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeSimple(XMLStreamWriter writer, String name, String text, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement(name);
        writer.writeCharacters(text);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
}

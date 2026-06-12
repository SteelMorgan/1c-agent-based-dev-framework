package io.github.onec.xmlgen.writer.skd;

import io.github.onec.xmlgen.dsl.SkdDsl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

/**
 * Сериализация {@link SkdDsl.Template} и {@link SkdDsl.GroupTemplate}.
 *
 * <p>Реализует табличный DSL макетов вывода СКД:
 * <ul>
 *   <li>{@code rows} — строки ячеек ({@code "{Имя}"}, {@code "|"}, {@code ">"}, текст);</li>
 *   <li>{@code widths} — ширины колонок ({@code Integer} или {@code "min-max"} диапазон);</li>
 *   <li>{@code parameters} — параметры макета (с поддержкой {@code drilldown});</li>
 *   <li>raw {@code template} — XML-fallback.</li>
 * </ul>
 *
 * <p>Эмитирует упрощённую структуру {@code <template>} достаточную для
 * валидатора СКД и режима {@code info --mode templates}.</p>
 */
public final class SkdTemplateWriter {

    private static final String DCS_AREA_TEMPLATE_NS =
            "http://v8.1c.ru/8.1/data-composition-system/area-template";
    private static final String DCS_CORE_NS =
            "http://v8.1c.ru/8.1/data-composition-system/core";
    private static final String DCS_SETTINGS_NS =
            "http://v8.1c.ru/8.1/data-composition-system/settings";
    private static final String DCS_COMMON_NS =
            "http://v8.1c.ru/8.1/data-composition-system/common";
    private static final String V8_CORE_NS =
            "http://v8.1c.ru/8.1/data/core";
    private static final String XS_NS =
            "http://www.w3.org/2001/XMLSchema";
    private static final String XSI_NS =
            "http://www.w3.org/2001/XMLSchema-instance";

    private SkdTemplateWriter() {
    }

    public static void writeTemplate(XMLStreamWriter writer, SkdDsl.Template tpl, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("template");
        if (tpl.getType() != null) {
            writer.writeAttribute("type", tpl.getType());
        }
        writer.writeCharacters("\n");
        String inner = indent + "\t";

        if (tpl.getName() != null) {
            writeSimple(writer, "name", tpl.getName(), inner);
        }
        if (tpl.getStyle() != null) {
            writeSimple(writer, "style", tpl.getStyle(), inner);
        }
        if (tpl.getMinHeight() != null) {
            writeSimple(writer, "minHeight", tpl.getMinHeight().toString(), inner);
        }

        // Ширины колонок.
        if (tpl.getWidths() != null && !tpl.getWidths().isEmpty()) {
            writer.writeCharacters(inner);
            writer.writeStartElement("widths");
            writer.writeCharacters("\n");
            for (Object w : tpl.getWidths()) {
                writeWidth(writer, w, inner + "\t");
            }
            writer.writeCharacters(inner);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        // Параметры шаблона.
        if (tpl.getParameters() != null) {
            for (SkdDsl.TemplateParameter p : tpl.getParameters()) {
                writeParameter(writer, p, inner);
            }
        }

        // Raw dcsat:AreaTemplate XML — insert as XML subtree, not as escaped text/CDATA.
        if (tpl.getTemplate() != null) {
            writer.writeCharacters(inner);
            writeXmlFragment(writer, tpl.getTemplate());
            writer.writeCharacters("\n");
        }

        // Строки.
        if (tpl.getTemplate() == null && tpl.getRows() != null) {
            writer.writeCharacters(inner);
            writer.writeStartElement("rows");
            writer.writeCharacters("\n");
            for (Object row : tpl.getRows()) {
                writeRow(writer, row, inner + "\t");
            }
            writer.writeCharacters(inner);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        writer.writeCharacters(indent);
        writer.writeEndElement(); // template
        writer.writeCharacters("\n");
    }

    public static void writeGroupTemplate(XMLStreamWriter writer, SkdDsl.GroupTemplate gt, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("groupTemplate");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        if (gt.getGroupField() != null) {
            writeSimple(writer, "groupField", gt.getGroupField(), inner);
        }
        if (gt.getGroupName() != null) {
            writeSimple(writer, "groupName", gt.getGroupName(), inner);
        }
        if (gt.getTemplateType() != null) {
            writeSimple(writer, "templateType", gt.getTemplateType(), inner);
        }
        if (gt.getTemplate() != null) {
            writeSimple(writer, "template", gt.getTemplate(), inner);
        }
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    // ---- helpers --------------------------------------------------------

    private static void writeWidth(XMLStreamWriter writer, Object w, String indent)
            throws XMLStreamException {
        String value = w.toString();
        writer.writeCharacters(indent);
        writer.writeStartElement("width");
        if (value.contains("-")) {
            String[] parts = value.split("-", 2);
            writer.writeAttribute("min", parts[0].trim());
            writer.writeAttribute("max", parts[1].trim());
        } else {
            writer.writeCharacters(value);
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeParameter(XMLStreamWriter writer, SkdDsl.TemplateParameter p, String indent)
            throws XMLStreamException {
        // ExpressionAreaTemplateParameter
        writer.writeCharacters(indent);
        writer.writeStartElement("parameter");
        writer.writeNamespace("dcsat", DCS_AREA_TEMPLATE_NS);
        writer.writeAttribute("xsi:type", "dcsat:ExpressionAreaTemplateParameter");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        if (p.getName() != null) writeSimple(writer, "dcsat:name", p.getName(), inner);
        if (p.getExpression() != null) writeSimple(writer, "dcsat:expression", p.getExpression(), inner);
        if (p.getFormat() != null) writeSimple(writer, "dcsat:format", p.getFormat(), inner);
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");

        // Парный DetailsAreaTemplateParameter — для расшифровки.
        if (p.getDrilldown() != null) {
            writer.writeCharacters(indent);
            writer.writeStartElement("parameter");
            writer.writeNamespace("dcsat", DCS_AREA_TEMPLATE_NS);
            writer.writeAttribute("xsi:type", "dcsat:DetailsAreaTemplateParameter");
            writer.writeCharacters("\n");
            writeSimple(writer, "dcsat:name", "Расшифровка_" + p.getDrilldown(), inner);
            writeSimple(writer, "dcsat:fieldExpression", p.getDrilldown(), inner);
            writeSimple(writer, "dcsat:mainAction", "DrillDown", inner);
            writer.writeCharacters(indent);
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }
    }

    private static void writeXmlFragment(XMLStreamWriter writer, String xml)
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

        String wrapped = "<fragment"
                + " xmlns:dcsat=\"" + DCS_AREA_TEMPLATE_NS + "\""
                + " xmlns:dcscor=\"" + DCS_CORE_NS + "\""
                + " xmlns:dcsset=\"" + DCS_SETTINGS_NS + "\""
                + " xmlns:dcscom=\"" + DCS_COMMON_NS + "\""
                + " xmlns:v8=\"" + V8_CORE_NS + "\""
                + " xmlns:xs=\"" + XS_NS + "\""
                + " xmlns:xsi=\"" + XSI_NS + "\">"
                + xml
                + "</fragment>";

        XMLStreamReader reader = factory.createXMLStreamReader(new StringReader(wrapped));
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("fragment".equals(reader.getLocalName())) {
                        continue;
                    }
                    writeStartElementFromReader(writer, reader);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("fragment".equals(reader.getLocalName())) {
                        break;
                    }
                    writer.writeEndElement();
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    writer.writeCharacters(reader.getText());
                } else if (event == XMLStreamConstants.CDATA) {
                    writer.writeCData(reader.getText());
                }
            }
        } finally {
            reader.close();
        }
    }

    private static void writeStartElementFromReader(XMLStreamWriter writer, XMLStreamReader reader)
            throws XMLStreamException {
        String prefix = reader.getPrefix();
        String namespace = reader.getNamespaceURI();
        String local = reader.getLocalName();

        if (prefix != null && !prefix.isEmpty()) {
            writer.writeStartElement(prefix, local, namespace != null ? namespace : "");
            writer.writeNamespace(prefix, namespace != null ? namespace : "");
        } else if (namespace != null && !namespace.isEmpty()) {
            writer.writeStartElement("", local, namespace);
        } else {
            writer.writeStartElement(local);
        }

        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            String nsPrefix = reader.getNamespacePrefix(i);
            String nsUri = reader.getNamespaceURI(i);
            if (nsPrefix == null || nsPrefix.isEmpty()) {
                writer.writeDefaultNamespace(nsUri);
            } else {
                writer.writeNamespace(nsPrefix, nsUri);
            }
        }

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attrPrefix = reader.getAttributePrefix(i);
            String attrNamespace = reader.getAttributeNamespace(i);
            String attrLocal = reader.getAttributeLocalName(i);
            String attrValue = reader.getAttributeValue(i);
            if (attrPrefix != null && !attrPrefix.isEmpty()) {
                if (attrNamespace != null && !attrNamespace.isEmpty()) {
                    writer.writeNamespace(attrPrefix, attrNamespace);
                    writer.writeAttribute(attrPrefix, attrNamespace, attrLocal, attrValue);
                } else {
                    writer.writeAttribute(attrPrefix + ":" + attrLocal, attrValue);
                }
            } else {
                writer.writeAttribute(attrLocal, attrValue);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void writeRow(XMLStreamWriter writer, Object row, String indent)
            throws XMLStreamException {
        List<Object> cells;
        if (row instanceof List) {
            cells = (List<Object>) row;
        } else if (row instanceof Map) {
            Object c = ((Map<String, Object>) row).get("cells");
            cells = c instanceof List ? (List<Object>) c : List.of();
        } else {
            return;
        }
        writer.writeCharacters(indent);
        writer.writeStartElement("row");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        for (Object cell : cells) {
            writeCell(writer, cell, inner);
        }
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    @SuppressWarnings("unchecked")
    private static void writeCell(XMLStreamWriter writer, Object cell, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("cell");

        if (cell == null) {
            writer.writeAttribute("type", "empty");
        } else if (cell instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) cell;
            String type = m.get("type") != null ? m.get("type").toString() : "text";
            writer.writeAttribute("type", type);
            if (m.get("name") != null) {
                writer.writeAttribute("name", m.get("name").toString());
            }
            if (m.get("value") != null) {
                writer.writeCharacters(m.get("value").toString());
            }
            if (m.get("format") != null) {
                writer.writeAttribute("format", m.get("format").toString());
            }
        } else {
            String s = cell.toString();
            if ("|".equals(s)) {
                writer.writeAttribute("type", "mergeUp");
            } else if (">".equals(s)) {
                writer.writeAttribute("type", "mergeLeft");
            } else if (s.startsWith("{") && s.endsWith("}")) {
                writer.writeAttribute("type", "param");
                writer.writeAttribute("name", s.substring(1, s.length() - 1));
            } else {
                writer.writeAttribute("type", "text");
                writer.writeCharacters(s);
            }
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeSimple(XMLStreamWriter writer, String name, String text, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement(name);
        if (text != null) writer.writeCharacters(text);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }
}

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
        // Raw dcsat:AreaTemplate XML — insert as XML subtree, not as escaped text/CDATA.
        if (tpl.getTemplate() != null) {
            writer.writeCharacters(inner);
            writeXmlFragment(writer, tpl.getTemplate());
            writer.writeCharacters("\n");
        }

        // Строки DSL должны превращаться в канонический AreaTemplate, который читает Designer.
        if (tpl.getTemplate() == null && tpl.getRows() != null) {
            writeAreaTemplate(writer, tpl.getRows(), inner);
        }

        // Параметры шаблона в каноне идут рядом с AreaTemplate внутри <template>.
        if (tpl.getParameters() != null) {
            for (SkdDsl.TemplateParameter p : tpl.getParameters()) {
                writeParameter(writer, p, inner);
            }
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

    private static void writeAreaTemplate(XMLStreamWriter writer, List<Object> rows, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("template");
        writer.writeNamespace("dcsat", DCS_AREA_TEMPLATE_NS);
        writer.writeAttribute("xsi:type", "dcsat:AreaTemplate");
        writer.writeCharacters("\n");
        for (Object row : rows) {
            writeAreaTemplateRow(writer, row, indent + "\t");
        }
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
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
    private static void writeAreaTemplateRow(XMLStreamWriter writer, Object row, String indent)
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
        writer.writeStartElement("dcsat", "item", DCS_AREA_TEMPLATE_NS);
        writer.writeAttribute("xsi:type", "dcsat:TableRow");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        for (Object cell : cells) {
            writeAreaTemplateCell(writer, cell, inner);
        }
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    @SuppressWarnings("unchecked")
    private static void writeAreaTemplateCell(XMLStreamWriter writer, Object cell, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcsat", "tableCell", DCS_AREA_TEMPLATE_NS);

        if (cell != null) {
            CellValue cellValue = parseCellValue(cell);
            if (cellValue != null) {
                writer.writeCharacters("\n");
                writeAreaTemplateField(writer, cellValue, indent + "\t");
                writer.writeCharacters(indent);
            }
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    @SuppressWarnings("unchecked")
    private static CellValue parseCellValue(Object cell) {
        if (cell instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) cell;
            Object type = m.get("type");
            Object name = m.get("name");
            Object value = m.get("value");
            if ("param".equals(type) && name != null) {
                return new CellValue(true, name.toString());
            }
            if (value != null) {
                return new CellValue(false, value.toString());
            }
            return null;
        }

        String s = cell.toString();
        if ("|".equals(s) || ">".equals(s)) {
            return null;
        }
        if (s.startsWith("{") && s.endsWith("}")) {
            return new CellValue(true, s.substring(1, s.length() - 1));
        }
        return new CellValue(false, s);
    }

    private static void writeAreaTemplateField(XMLStreamWriter writer, CellValue cellValue, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcsat", "item", DCS_AREA_TEMPLATE_NS);
        writer.writeAttribute("xsi:type", "dcsat:Field");
        writer.writeCharacters("\n");
        writer.writeCharacters(indent + "\t");
        writer.writeStartElement("dcsat", "value", DCS_AREA_TEMPLATE_NS);
        if (cellValue.parameter) {
            writer.writeAttribute("xsi:type", "dcscor:Parameter");
            writer.writeCharacters(cellValue.value);
        } else {
            writer.writeAttribute("xsi:type", "v8:LocalStringType");
            writer.writeCharacters("\n");
            writeLocalStringItem(writer, cellValue.value, indent + "\t\t");
            writer.writeCharacters(indent + "\t");
        }
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeLocalStringItem(XMLStreamWriter writer, String value, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("v8", "item", V8_CORE_NS);
        writer.writeCharacters("\n");
        writeSimple(writer, "v8:lang", "ru", indent + "\t");
        writeSimple(writer, "v8:content", value, indent + "\t");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static final class CellValue {
        private final boolean parameter;
        private final String value;

        private CellValue(boolean parameter, String value) {
            this.parameter = parameter;
            this.value = value;
        }
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

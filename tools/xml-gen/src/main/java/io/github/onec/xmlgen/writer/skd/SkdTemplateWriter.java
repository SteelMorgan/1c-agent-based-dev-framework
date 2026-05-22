package io.github.onec.xmlgen.writer.skd;

import io.github.onec.xmlgen.dsl.SkdDsl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
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

        // Raw template — используем как есть.
        if (tpl.getTemplate() != null && tpl.getRows() == null) {
            writer.writeCharacters(inner);
            writer.writeStartElement("rawTemplate");
            writer.writeCData(tpl.getTemplate());
            writer.writeEndElement();
            writer.writeCharacters("\n");
        }

        // Строки.
        if (tpl.getRows() != null) {
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
        String elementName = "groupTemplate";
        if ("GroupHeader".equalsIgnoreCase(gt.getTemplateType())) {
            elementName = "groupHeaderTemplate";
        }
        writer.writeCharacters(indent);
        writer.writeStartElement(elementName);
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
        writer.writeAttribute("xsi:type", "ExpressionAreaTemplateParameter");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        if (p.getName() != null) writeSimple(writer, "name", p.getName(), inner);
        if (p.getExpression() != null) writeSimple(writer, "expression", p.getExpression(), inner);
        if (p.getFormat() != null) writeSimple(writer, "format", p.getFormat(), inner);
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");

        // Парный DetailsAreaTemplateParameter — для расшифровки.
        if (p.getDrilldown() != null) {
            writer.writeCharacters(indent);
            writer.writeStartElement("parameter");
            writer.writeAttribute("xsi:type", "DetailsAreaTemplateParameter");
            writer.writeCharacters("\n");
            writeSimple(writer, "name", "Расшифровка_" + p.getDrilldown(), inner);
            writeSimple(writer, "fieldExpression", p.getDrilldown(), inner);
            writeSimple(writer, "mainAction", "DrillDown", inner);
            writer.writeCharacters(indent);
            writer.writeEndElement();
            writer.writeCharacters("\n");
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

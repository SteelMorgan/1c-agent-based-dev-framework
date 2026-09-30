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
    private static final String V8_UI_NS =
            "http://v8.1c.ru/8.1/data/ui";
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
        // GroupHeader — это семантический тип DSL, но Designer хранит его отдельным
        // элементом с платформенным типом Header (XG-71).
        boolean isGroupHeader = "GroupHeader".equals(gt.getTemplateType());
        writer.writeCharacters(indent);
        writer.writeStartElement(isGroupHeader ? "groupHeaderTemplate" : "groupTemplate");
        writer.writeCharacters("\n");
        String inner = indent + "\t";
        if (gt.getGroupField() != null) {
            writeSimple(writer, "groupField", gt.getGroupField(), inner);
        }
        if (gt.getGroupName() != null) {
            writeSimple(writer, "groupName", gt.getGroupName(), inner);
        }
        if (gt.getTemplateType() != null) {
            writeSimple(writer, "templateType", isGroupHeader ? "Header" : gt.getTemplateType(), inner);
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
                + " xmlns:v8ui=\"" + V8_UI_NS + "\""
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
                writeCellAppearance(writer, cell, indent + "\t");
                writer.writeCharacters(indent);
            } else {
                writeCellAppearance(writer, cell, indent + "\t");
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

    @SuppressWarnings("unchecked")
    private static void writeCellAppearance(XMLStreamWriter writer, Object cell, String indent)
            throws XMLStreamException {
        if (!(cell instanceof Map)) {
            return;
        }
        Map<String, Object> m = (Map<String, Object>) cell;
        Object appearance = m.get("appearance");
        Map<String, Object> a = appearance instanceof Map ? (Map<String, Object>) appearance : Map.of();

        Object width = firstNonNull(m.get("width"), m.get("minWidth"));
        Object minWidth = firstNonNull(a.get("minWidth"), width);
        Object maxWidth = firstNonNull(a.get("maxWidth"), width);
        Object minHeight = firstNonNull(a.get("minHeight"), m.get("minHeight"));
        Object align = firstNonNull(a.get("align"), m.get("align"));
        Object valign = firstNonNull(a.get("valign"), m.get("valign"));
        Object backColor = firstNonNull(a.get("backColor"), m.get("backColor"));
        Object textColor = firstNonNull(a.get("textColor"), m.get("textColor"));
        Object mergeRight = firstNonNull(a.get("mergeRight"), m.get("mergeRight"));
        Object border = firstNonNull(a.get("border"), m.get("border"));
        Object borderColor = firstNonNull(a.get("borderColor"), m.get("borderColor"));
        Object borderSides = firstNonNull(firstNonNull(a.get("borderSides"), a.get("sides")),
                firstNonNull(m.get("borderSides"), m.get("sides")));
        Object font = firstNonNull(a.get("font"), m.get("font"));
        Object padding = firstNonNull(a.get("padding"), m.get("padding"));
        Object paddingLeft = firstNonNull(a.get("paddingLeft"), m.get("paddingLeft"));
        Object paddingRight = firstNonNull(a.get("paddingRight"), m.get("paddingRight"));
        Object paddingTop = firstNonNull(a.get("paddingTop"), m.get("paddingTop"));
        Object paddingBottom = firstNonNull(a.get("paddingBottom"), m.get("paddingBottom"));

        if (minWidth == null && maxWidth == null && minHeight == null && align == null && valign == null
                && backColor == null && textColor == null && mergeRight == null && border == null
                && borderColor == null && borderSides == null && font == null && padding == null
                && paddingLeft == null && paddingRight == null && paddingTop == null && paddingBottom == null) {
            return;
        }

        writer.writeCharacters(indent);
        writer.writeStartElement("dcsat", "appearance", DCS_AREA_TEMPLATE_NS);
        writer.writeCharacters("\n");
        String inner = indent + "\t";

        if (backColor != null) writeColorAppearance(writer, "ЦветФона", backColor.toString(), inner);
        if (textColor != null) writeColorAppearance(writer, "ЦветТекста", textColor.toString(), inner);
        if (font instanceof Map) writeFontAppearance(writer, (Map<String, Object>) font, inner);
        if (border != null) {
            writeBorderAppearance(writer, border, borderColor, borderSides, inner);
        }
        writePaddingAppearance(writer, padding, paddingLeft, paddingRight, paddingTop, paddingBottom, inner);
        if (align != null) writeEnumAppearance(writer, "ГоризонтальноеПоложение", "v8ui:HorizontalAlign",
                normalizeHorizontalAlign(align.toString()), inner);
        if (valign != null) writeEnumAppearance(writer, "ВертикальноеПоложение", "v8ui:VerticalAlign",
                normalizeVerticalAlign(valign.toString()), inner);
        if (minWidth != null) writeSimpleAppearance(writer, "МинимальнаяШирина", "xs:decimal", minWidth.toString(), inner);
        if (maxWidth != null) writeSimpleAppearance(writer, "МаксимальнаяШирина", "xs:decimal", maxWidth.toString(), inner);
        if (minHeight != null) writeSimpleAppearance(writer, "МинимальнаяВысота", "xs:decimal", minHeight.toString(), inner);
        if (mergeRight != null && Boolean.parseBoolean(mergeRight.toString())) {
            writeSimpleAppearance(writer, "ОбъединятьПоГоризонтали", "xs:boolean", "true", inner);
        }

        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private static void writeSimpleAppearance(XMLStreamWriter writer, String parameter, String xsiType,
                                              String value, String indent) throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcscor", "item", DCS_CORE_NS);
        writer.writeCharacters("\n");
        writeSimple(writer, "dcscor:parameter", parameter, indent + "\t");
        writer.writeCharacters(indent + "\t");
        writer.writeStartElement("dcscor", "value", DCS_CORE_NS);
        writer.writeAttribute("xsi:type", xsiType);
        writer.writeCharacters(value);
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeColorAppearance(XMLStreamWriter writer, String parameter, String value, String indent)
            throws XMLStreamException {
        writeSimpleAppearance(writer, parameter, "v8ui:Color", value, indent);
    }

    private static void writeEnumAppearance(XMLStreamWriter writer, String parameter, String type,
                                            String value, String indent) throws XMLStreamException {
        writeSimpleAppearance(writer, parameter, type, value, indent);
    }

    private static void writeFontAppearance(XMLStreamWriter writer, Map<String, Object> font, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcscor", "item", DCS_CORE_NS);
        writer.writeCharacters("\n");
        writeSimple(writer, "dcscor:parameter", "Шрифт", indent + "\t");
        writer.writeCharacters(indent + "\t");
        writer.writeEmptyElement("dcscor", "value", DCS_CORE_NS);
        writer.writeAttribute("xsi:type", "v8ui:Font");
        writer.writeAttribute("faceName", stringValue(font.get("face"), "Arial"));
        writer.writeAttribute("height", stringValue(font.get("size"), "10"));
        writer.writeAttribute("bold", stringValue(font.get("bold"), "false"));
        writer.writeAttribute("italic", stringValue(font.get("italic"), "false"));
        writer.writeAttribute("underline", stringValue(font.get("underline"), "false"));
        writer.writeAttribute("strikeout", stringValue(font.get("strikeout"), "false"));
        writer.writeAttribute("kind", "Absolute");
        writer.writeAttribute("scale", "100");
        writer.writeCharacters("\n");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static String stringValue(Object value, String fallback) {
        return value != null ? value.toString() : fallback;
    }

    private static void writeLineAppearance(XMLStreamWriter writer, String parameter, String border, String indent)
            throws XMLStreamException {
        writeLineAppearance(writer, parameter, lineWidth(border, null), lineStyle(border), indent);
    }

    private static void writeLineAppearance(XMLStreamWriter writer, String parameter, int width, String style, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcscor", "item", DCS_CORE_NS);
        writer.writeCharacters("\n");
        writeSimple(writer, "dcscor:parameter", parameter, indent + "\t");
        writeLineValue(writer, width, style, indent + "\t");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeLineValue(XMLStreamWriter writer, int width, String style, String indent)
            throws XMLStreamException {
        writer.writeCharacters(indent);
        writer.writeStartElement("dcscor", "value", DCS_CORE_NS);
        writer.writeAttribute("xsi:type", "v8ui:Line");
        writer.writeAttribute("width", String.valueOf(width));
        writer.writeAttribute("gap", "false");
        writer.writeCharacters("\n");
        writer.writeCharacters(indent + "\t");
        writer.writeStartElement("v8ui", "style", V8_UI_NS);
        writer.writeAttribute("xsi:type", "v8ui:SpreadsheetDocumentCellLineType");
        writer.writeCharacters(style);
        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    @SuppressWarnings("unchecked")
    private static void writeBorderAppearance(XMLStreamWriter writer, Object border, Object fallbackColor,
                                              Object fallbackSides, String indent) throws XMLStreamException {
        Object style = border;
        Object width = null;
        Object color = fallbackColor;
        Object sides = fallbackSides;
        if (border instanceof Map) {
            Map<String, Object> borderMap = (Map<String, Object>) border;
            style = firstNonNull(borderMap.get("style"), borderMap.get("type"));
            width = firstNonNull(borderMap.get("width"), borderMap.get("borderWidth"));
            color = firstNonNull(borderMap.get("color"), color);
            sides = firstNonNull(firstNonNull(borderMap.get("sides"), borderMap.get("borderSides")), sides);
        }

        String styleName = style != null ? style.toString() : "thin";
        if (color != null) {
            writeColorAppearance(writer, "ЦветГраницы", color.toString(), indent);
        }

        int lineWidth = lineWidth(styleName, width);
        String lineStyle = lineStyle(styleName);
        if (sides == null || hasSide(sides, "all") || hasSide(sides, "все")) {
            writeLineAppearance(writer, "СтильГраницы", lineWidth, lineStyle, indent);
            return;
        }
        if (sides instanceof Map) {
            writer.writeCharacters(indent);
            writer.writeStartElement("dcscor", "item", DCS_CORE_NS);
            writer.writeCharacters("\n");
            writeSimple(writer, "dcscor:parameter", "СтильГраницы", indent + "\t");
            writeLineValue(writer, 0, "None", indent + "\t");
            writeMappedSideLine(writer, (Map<String, Object>) sides, "top", "верх", "СтильГраницы.Сверху",
                    lineWidth, lineStyle, indent + "\t");
            writeMappedSideLine(writer, (Map<String, Object>) sides, "left", "слева", "СтильГраницы.Слева",
                    lineWidth, lineStyle, indent + "\t");
            writeMappedSideLine(writer, (Map<String, Object>) sides, "right", "справа", "СтильГраницы.Справа",
                    lineWidth, lineStyle, indent + "\t");
            writeMappedSideLine(writer, (Map<String, Object>) sides, "bottom", "снизу", "СтильГраницы.Снизу",
                    lineWidth, lineStyle, indent + "\t");
            writer.writeCharacters(indent);
            writer.writeEndElement();
            writer.writeCharacters("\n");
            return;
        }
        writer.writeCharacters(indent);
        writer.writeStartElement("dcscor", "item", DCS_CORE_NS);
        writer.writeCharacters("\n");
        writeSimple(writer, "dcscor:parameter", "СтильГраницы", indent + "\t");
        writeLineValue(writer, 0, "None", indent + "\t");
        if (hasSide(sides, "top") || hasSide(sides, "верх")) {
            writeLineAppearance(writer, "СтильГраницы.Сверху", lineWidth, lineStyle, indent + "\t");
        }
        if (hasSide(sides, "left") || hasSide(sides, "лево") || hasSide(sides, "слева")) {
            writeLineAppearance(writer, "СтильГраницы.Слева", lineWidth, lineStyle, indent + "\t");
        }
        if (hasSide(sides, "right") || hasSide(sides, "право") || hasSide(sides, "справа")) {
            writeLineAppearance(writer, "СтильГраницы.Справа", lineWidth, lineStyle, indent + "\t");
        }
        if (hasSide(sides, "bottom") || hasSide(sides, "низ") || hasSide(sides, "снизу")) {
            writeLineAppearance(writer, "СтильГраницы.Снизу", lineWidth, lineStyle, indent + "\t");
        }
        writer.writeCharacters(indent);
        writer.writeEndElement();
        writer.writeCharacters("\n");
    }

    private static void writeMappedSideLine(XMLStreamWriter writer, Map<String, Object> sides,
                                            String englishName, String russianName, String parameter,
                                            int defaultWidth, String defaultStyle, String indent)
            throws XMLStreamException {
        Object value = firstNonNull(firstNonNull(sides.get(englishName), sides.get(russianName)),
                sides.get(parameter));
        if (value == null) {
            return;
        }
        if (value instanceof Boolean && !((Boolean) value)) {
            return;
        }
        if (value instanceof Boolean) {
            writeLineAppearance(writer, parameter, defaultWidth, defaultStyle, indent);
            return;
        }
        String styleName = value.toString();
        writeLineAppearance(writer, parameter, lineWidth(styleName, null), lineStyle(styleName), indent);
    }

    @SuppressWarnings("unchecked")
    private static boolean hasSide(Object sides, String side) {
        String normalized = side.toLowerCase();
        if (sides instanceof List) {
            for (Object item : (List<Object>) sides) {
                if (item != null && normalized.equals(item.toString().toLowerCase())) {
                    return true;
                }
            }
            return false;
        }
        if (sides instanceof Map) {
            Object value = ((Map<String, Object>) sides).get(side);
            return value != null && Boolean.parseBoolean(value.toString());
        }
        String text = sides.toString().toLowerCase();
        return text.equals(normalized) || text.contains(normalized);
    }

    private static int lineWidth(String border, Object explicitWidth) {
        if (explicitWidth != null) {
            try {
                return Math.max(1, (int) Math.round(Double.parseDouble(explicitWidth.toString())));
            } catch (NumberFormatException ignored) {
                // Use the style-derived fallback below.
            }
        }
        if ("none".equalsIgnoreCase(border)) {
            return 0;
        }
        return "thick".equalsIgnoreCase(border) ? 2 : 1;
    }

    private static String lineStyle(String border) {
        return "none".equalsIgnoreCase(border) ? "None" : "Solid";
    }

    @SuppressWarnings("unchecked")
    private static void writePaddingAppearance(XMLStreamWriter writer, Object padding,
                                               Object paddingLeft, Object paddingRight,
                                               Object paddingTop, Object paddingBottom,
                                               String indent) throws XMLStreamException {
        Object left = paddingLeft;
        Object right = paddingRight;
        Object top = paddingTop;
        Object bottom = paddingBottom;
        if (padding instanceof Map) {
            Map<String, Object> p = (Map<String, Object>) padding;
            left = firstNonNull(left, p.get("left"));
            right = firstNonNull(right, p.get("right"));
            top = firstNonNull(top, p.get("top"));
            bottom = firstNonNull(bottom, p.get("bottom"));
        } else if (padding != null) {
            left = firstNonNull(left, padding);
            right = firstNonNull(right, padding);
            top = firstNonNull(top, padding);
            bottom = firstNonNull(bottom, padding);
        }
        if (left != null) writeSimpleAppearance(writer, "ОтступСлева", "xs:decimal", left.toString(), indent);
        if (right != null) writeSimpleAppearance(writer, "ОтступСправа", "xs:decimal", right.toString(), indent);
        if (top != null) writeSimpleAppearance(writer, "ОтступСверху", "xs:decimal", top.toString(), indent);
        if (bottom != null) writeSimpleAppearance(writer, "ОтступСнизу", "xs:decimal", bottom.toString(), indent);
    }

    private static String normalizeHorizontalAlign(String value) {
        return switch (value.toLowerCase()) {
            case "left", "лево", "слева" -> "Left";
            case "right", "право", "справа" -> "Right";
            default -> "Center";
        };
    }

    private static String normalizeVerticalAlign(String value) {
        return switch (value.toLowerCase()) {
            case "top", "верх", "сверху" -> "Top";
            case "bottom", "низ", "снизу" -> "Bottom";
            default -> "Center";
        };
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

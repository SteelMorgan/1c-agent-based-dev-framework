package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.dsl.SkdTemplateImportDsl;
import io.github.onec.xmlgen.writer.skd.SkdTemplateWriter;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//++agent TASK-174 [10.07.2026 17:19:29]
/**
 * Lossless-редактор верхнеуровневых {@code template}/group-template узлов СКД.
 *
 * <p>В отличие от общего tree writer он заменяет только целевые байтовые диапазоны:
 * вручную доработанная геометрия соседних AreaTemplate, datasets и settings не
 * сериализуются заново. Идентичность области — {@code name}; идентичность привязки —
 * {@code (groupName, groupField, templateType)}.</p>
 */
public final class SkdTemplateImportEditor {

    private static final Pattern V8_CONTENT = Pattern.compile(
            "(<v8:content(?:\\s[^>]*)?>)(.*?)(</v8:content\\s*>)", Pattern.DOTALL);
    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "Header", "OverallHeader", "GroupHeader", "Footer", "OverallFooter");

    public record Result(String content, boolean changed) {
    }

    public Result apply(String original, SkdTemplateImportDsl patch) {
        validatePatch(original, patch);
        String result = original;

        if (patch.getTemplates() != null) {
            for (SkdDsl.Template template : patch.getTemplates()) {
                result = upsertTemplate(result, template);
            }
        }
        if (patch.getGroupTemplates() != null) {
            for (SkdDsl.GroupTemplate binding : patch.getGroupTemplates()) {
                result = upsertBinding(result, binding);
            }
        }

        // XDTO-схема допускает сначала только обычные привязки, затем групповые
        // заголовки. Стабильная канонизация отвязывает результат от порядка payload
        // и одновременно чинит ранее созданный неверный порядок при повторном upsert.
        result = canonicalizeBindingOrder(result);

        // Финальный parse выполняет CLI до atomic move; здесь scanner дополнительно
        // гарантирует единственный корень и сбалансированные верхнеуровневые элементы.
        scan(result);
        return new Result(result, !result.equals(original));
    }

    private String upsertTemplate(String xml, SkdDsl.Template template) {
        Scan scan = scan(xml);
        List<Span> matches = scan.children.stream()
                .filter(s -> "template".equals(s.localName))
                .filter(s -> template.getName().equals(directText(xml, s, "name")))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Duplicate SKD template name: " + template.getName());
        }

        String fragment = renderTemplate(template, scan.indent, scan.lineSeparator);
        if (!matches.isEmpty()) {
            Span target = matches.get(0);
            return replace(xml, target.start, target.end, fragment);
        }

        int insert = scan.children.stream()
                .filter(s -> "groupTemplate".equals(s.localName)
                        || "groupHeaderTemplate".equals(s.localName)
                        || "settingsVariant".equals(s.localName))
                .mapToInt(s -> s.start)
                .min()
                .orElse(scan.rootCloseStart);
        return replace(xml, insert, insert, fragment);
    }

    private String upsertBinding(String xml, SkdDsl.GroupTemplate binding) {
        Scan scan = scan(xml);
        BindingKey wanted = BindingKey.fromDsl(binding);
        List<Span> matches = scan.children.stream()
                .filter(s -> "groupTemplate".equals(s.localName)
                        || "groupHeaderTemplate".equals(s.localName))
                .filter(s -> wanted.equals(BindingKey.fromXml(xml, s)))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Duplicate SKD group template binding: " + wanted);
        }

        String fragment = renderBinding(binding, scan.indent, scan.lineSeparator);
        if (!matches.isEmpty()) {
            Span target = matches.get(0);
            return replace(xml, target.start, target.end, fragment);
        }

        boolean groupHeader = "GroupHeader".equals(binding.getTemplateType());
        int insert = scan.children.stream()
                .filter(s -> (groupHeader && "settingsVariant".equals(s.localName))
                        || (!groupHeader && ("groupHeaderTemplate".equals(s.localName)
                        || "settingsVariant".equals(s.localName))))
                .mapToInt(s -> s.start)
                .min()
                .orElse(scan.rootCloseStart);
        return replace(xml, insert, insert, fragment);
    }

    /**
     * Стабильно переносит только нарушающие порядок обычные привязки перед первым
     * {@code groupHeaderTemplate}. Остальные узлы и порядок внутри каждого класса
     * остаются неизменными.
     */
    private static String canonicalizeBindingOrder(String xml) {
        String result = xml;
        while (true) {
            Scan current = scan(result);
            Span firstHeader = current.children.stream()
                    .filter(span -> "groupHeaderTemplate".equals(span.localName))
                    .findFirst()
                    .orElse(null);
            if (firstHeader == null) return result;

            Span misplacedOrdinary = current.children.stream()
                    .filter(span -> "groupTemplate".equals(span.localName))
                    .filter(span -> span.start > firstHeader.start)
                    .findFirst()
                    .orElse(null);
            if (misplacedOrdinary == null) return result;

            String fragment = result.substring(misplacedOrdinary.start, misplacedOrdinary.end);
            result = replace(result, misplacedOrdinary.start, misplacedOrdinary.end, "");
            Scan afterRemoval = scan(result);
            int beforeFirstHeader = afterRemoval.children.stream()
                    .filter(span -> "groupHeaderTemplate".equals(span.localName))
                    .findFirst()
                    .orElseThrow()
                    .start;
            result = replace(result, beforeFirstHeader, beforeFirstHeader, fragment);
        }
    }

    private static void validatePatch(String original, SkdTemplateImportDsl patch) {
        if (patch == null) throw new IllegalArgumentException("Template import payload is empty");
        boolean noTemplates = patch.getTemplates() == null || patch.getTemplates().isEmpty();
        boolean noBindings = patch.getGroupTemplates() == null || patch.getGroupTemplates().isEmpty();
        if (noTemplates && noBindings) {
            throw new IllegalArgumentException("Template import requires templates or groupTemplates");
        }

        Set<String> names = new HashSet<>();
        Scan current = scan(original);
        for (Span span : current.children) {
            if ("template".equals(span.localName)) {
                String existingName = directText(original, span, "name");
                if (!blank(existingName)) names.add(existingName);
            }
        }
        if (patch.getTemplates() != null) {
            for (SkdDsl.Template template : patch.getTemplates()) {
                if (template == null || blank(template.getName())) {
                    throw new IllegalArgumentException("Every imported template requires a non-empty name");
                }
                if (template.getRows() == null && blank(template.getTemplate())) {
                    throw new IllegalArgumentException(
                            "Template '" + template.getName() + "' requires rows or raw template XML");
                }
                if (names.contains(template.getName())
                        && patch.getTemplates().stream()
                        .filter(item -> item != null && template.getName().equals(item.getName()))
                        .count() > 1) {
                    throw new IllegalArgumentException("Duplicate template in payload: " + template.getName());
                }
                names.add(template.getName());
            }
        }

        Set<BindingKey> keys = new HashSet<>();
        if (patch.getGroupTemplates() != null) {
            for (SkdDsl.GroupTemplate binding : patch.getGroupTemplates()) {
                if (binding == null || blank(binding.getTemplate()) || blank(binding.getTemplateType())) {
                    throw new IllegalArgumentException(
                            "Every groupTemplates item requires template and templateType");
                }
                if (!TEMPLATE_TYPES.contains(binding.getTemplateType())) {
                    throw new IllegalArgumentException("Unknown SKD group templateType '"
                            + binding.getTemplateType() + "'. Expected one of: " + TEMPLATE_TYPES);
                }
                if (blank(binding.getGroupName()) && blank(binding.getGroupField())) {
                    throw new IllegalArgumentException(
                            "Every groupTemplates item requires groupName or groupField");
                }
                BindingKey key = BindingKey.fromDsl(binding);
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Duplicate group template binding in payload: " + key);
                }
                if (!names.contains(binding.getTemplate())) {
                    throw new IllegalArgumentException("SKD group template binding references missing AreaTemplate: "
                            + binding.getTemplate());
                }
            }
        }
    }

    private static String renderTemplate(SkdDsl.Template template, String indent, String lineSeparator) {
        return render(writer -> SkdTemplateWriter.writeTemplate(writer, template, indent), lineSeparator);
    }

    private static String renderBinding(SkdDsl.GroupTemplate binding, String indent, String lineSeparator) {
        return render(writer -> SkdTemplateWriter.writeGroupTemplate(writer, binding, indent), lineSeparator);
    }

    private static String render(WriterAction action, String lineSeparator) {
        try {
            StringWriter out = new StringWriter();
            XMLStreamWriter writer = XMLOutputFactory.newFactory().createXMLStreamWriter(out);
            action.write(writer);
            writer.flush();
            writer.close();
            return normalizeStructuralLineEndings(out.toString(), lineSeparator);
        } catch (XMLStreamException e) {
            throw new IllegalArgumentException("Failed to serialize SKD template import: " + e.getMessage(), e);
        }
    }

    /** Designer хранит CRLF между тегами, но bare LF внутри v8:content. */
    private static String normalizeStructuralLineEndings(String value, String lineSeparator) {
        Matcher matcher = V8_CONTENT.matcher(value);
        StringBuilder result = new StringBuilder(value.length() + 64);
        int from = 0;
        while (matcher.find()) {
            result.append(normalizeLines(value.substring(from, matcher.start()), lineSeparator));
            result.append(matcher.group(1)).append(matcher.group(2)).append(matcher.group(3));
            from = matcher.end();
        }
        result.append(normalizeLines(value.substring(from), lineSeparator));
        return result.toString();
    }

    private static String normalizeLines(String value, String lineSeparator) {
        return value.replace("\r\n", "\n").replace('\r', '\n').replace("\n", lineSeparator);
    }

    private static String replace(String value, int start, int end, String replacement) {
        return value.substring(0, start) + replacement + value.substring(end);
    }

    private static String directText(String xml, Span span, String localName) {
        String fragment = xml.substring(span.start, span.end);
        Pattern p = Pattern.compile("<(?:[A-Za-z_][\\w.-]*:)?" + Pattern.quote(localName)
                + "(?:\\s[^>]*)?>([^<]*)</(?:[A-Za-z_][\\w.-]*:)?"
                + Pattern.quote(localName) + "\\s*>", Pattern.DOTALL);
        Matcher m = p.matcher(fragment);
        return m.find() ? unescape(m.group(1)) : null;
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static Scan scan(String xml) {
        List<Span> children = new ArrayList<>();
        int depth = 0;
        int topStart = -1;
        String topName = null;
        int rootClose = -1;

        for (int pos = 0; pos < xml.length();) {
            int lt = xml.indexOf('<', pos);
            if (lt < 0) break;
            if (xml.startsWith("<!--", lt)) {
                pos = requireEnd(xml, lt + 4, "-->") + 3;
                continue;
            }
            if (xml.startsWith("<![CDATA[", lt)) {
                pos = requireEnd(xml, lt + 9, "]]>") + 3;
                continue;
            }
            if (xml.startsWith("<?", lt)) {
                pos = requireEnd(xml, lt + 2, "?>") + 2;
                continue;
            }

            int gt = tagEnd(xml, lt + 1);
            boolean closing = lt + 1 < xml.length() && xml.charAt(lt + 1) == '/';
            boolean declaration = lt + 1 < xml.length() && xml.charAt(lt + 1) == '!';
            if (declaration) {
                pos = gt + 1;
                continue;
            }
            String qName = readName(xml, lt + (closing ? 2 : 1));
            String local = localName(qName);

            if (closing) {
                if (depth == 2 && topStart >= 0) {
                    int end = includeFollowingLineBreak(xml, gt + 1);
                    children.add(new Span(lineStart(xml, topStart), end, topName));
                    topStart = -1;
                    topName = null;
                } else if (depth == 1) {
                    rootClose = lineStart(xml, lt);
                }
                depth--;
                if (depth < 0) throw new IllegalArgumentException("Malformed XML: unexpected closing tag " + qName);
            } else {
                boolean selfClosing = isSelfClosing(xml, lt, gt);
                if (depth == 1) {
                    topStart = lt;
                    topName = local;
                    if (selfClosing) {
                        children.add(new Span(lineStart(xml, topStart),
                                includeFollowingLineBreak(xml, gt + 1), topName));
                        topStart = -1;
                        topName = null;
                    }
                }
                if (!selfClosing) depth++;
            }
            pos = gt + 1;
        }

        if (depth != 0 || rootClose < 0) {
            throw new IllegalArgumentException("Malformed XML: unbalanced DataCompositionSchema");
        }
        String lineSeparator = xml.contains("\r\n") ? "\r\n" : "\n";
        String indent = children.isEmpty() ? "\t" : xml.substring(children.get(0).start,
                xml.indexOf('<', children.get(0).start));
        if (indent.isEmpty()) indent = "\t";
        return new Scan(children, rootClose, indent, lineSeparator);
    }

    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int i = from; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        throw new IllegalArgumentException("Malformed XML: unterminated tag");
    }

    private static String readName(String xml, int from) {
        while (from < xml.length() && Character.isWhitespace(xml.charAt(from))) from++;
        int end = from;
        while (end < xml.length()) {
            char c = xml.charAt(end);
            if (Character.isWhitespace(c) || c == '>' || c == '/') break;
            end++;
        }
        return xml.substring(from, end);
    }

    private static boolean isSelfClosing(String xml, int lt, int gt) {
        int pos = gt - 1;
        while (pos > lt && Character.isWhitespace(xml.charAt(pos))) pos--;
        return xml.charAt(pos) == '/';
    }

    private static int lineStart(String xml, int position) {
        int lf = xml.lastIndexOf('\n', position - 1);
        int start = lf < 0 ? 0 : lf + 1;
        for (int i = start; i < position; i++) {
            if (!Character.isWhitespace(xml.charAt(i))) return position;
        }
        return start;
    }

    private static int includeFollowingLineBreak(String xml, int position) {
        int pos = position;
        while (pos < xml.length() && (xml.charAt(pos) == ' ' || xml.charAt(pos) == '\t')) pos++;
        if (pos < xml.length() && xml.charAt(pos) == '\r') pos++;
        if (pos < xml.length() && xml.charAt(pos) == '\n') pos++;
        return pos;
    }

    private static int requireEnd(String xml, int from, String token) {
        int end = xml.indexOf(token, from);
        if (end < 0) throw new IllegalArgumentException("Malformed XML: unterminated " + token);
        return end;
    }

    private static String localName(String qName) {
        int colon = qName.indexOf(':');
        return colon >= 0 ? qName.substring(colon + 1) : qName;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private interface WriterAction {
        void write(XMLStreamWriter writer) throws XMLStreamException;
    }

    private record Span(int start, int end, String localName) {
    }

    private record Scan(List<Span> children, int rootCloseStart, String indent, String lineSeparator) {
    }

    private record BindingKey(String groupName, String groupField, String templateType) {
        static BindingKey fromDsl(SkdDsl.GroupTemplate binding) {
            return new BindingKey(binding.getGroupName(), binding.getGroupField(), binding.getTemplateType());
        }

        static BindingKey fromXml(String xml, Span span) {
            String type = "groupHeaderTemplate".equals(span.localName)
                    ? "GroupHeader" : directText(xml, span, "templateType");
            return new BindingKey(directText(xml, span, "groupName"),
                    directText(xml, span, "groupField"), type);
        }

        @Override
        public String toString() {
            return "(" + Objects.toString(groupName, "") + ","
                    + Objects.toString(groupField, "") + "," + templateType + ")";
        }
    }
}
//++agent TASK-174

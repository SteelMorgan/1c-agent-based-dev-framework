package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.dsl.SkdTemplateImportDsl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

//++agent TASK-174 [11.07.2026 22:44:23]
/**
 * Переносит root AreaTemplate из donor СКД без сериализации его содержимого.
 * Единственная допустимая правка donor span — текст direct {@code name}; так
 * historical rows/cells/appearance остаются проверяемо побайтовыми.
 */
public final class SkdExactTemplateImportEditor {

    public record Result(String content, boolean changed, int imported) {
    }

    public Result apply(String original, SkdTemplateImportDsl patch, Path sourceBase)
            throws IOException {
        if (patch == null || patch.getExactTemplates() == null
                || patch.getExactTemplates().isEmpty()) {
            return new Result(original, false, 0);
        }
        String ifAbsent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        if (!"fail".equals(ifAbsent) && !"noop".equals(ifAbsent)) {
            throw new IllegalArgumentException("ifAbsent must be 'fail' or 'noop'");
        }

        Element root = parse(original);
        requireSchemaRoot(root);
        Map<String, List<Element>> existing = templatesByName(original, root);
        Set<String> renderedTargets = renderedTemplateNames(patch.getTemplates());
        Set<String> payloadTargets = new HashSet<>();
        Map<Path, SourceDocument> sources = new HashMap<>();
        StringBuilder additions = new StringBuilder();
        int imported = 0;

        for (SkdTemplateImportDsl.ExactTemplate item : patch.getExactTemplates()) {
            validateItem(item);
            if (!payloadTargets.add(item.getTargetName())) {
                throw new IllegalArgumentException("Duplicate exact AreaTemplate target in payload: "
                        + item.getTargetName());
            }
            if (renderedTargets.contains(item.getTargetName())) {
                throw new IllegalArgumentException("Duplicate template target across exactTemplates/templates: "
                        + item.getTargetName());
            }

            List<Element> targetMatches = existing.getOrDefault(item.getTargetName(), List.of());
            if (targetMatches.size() > 1) {
                throw new IllegalArgumentException("Expected at most one target AreaTemplate named '"
                        + item.getTargetName() + "', found " + targetMatches.size());
            }

            SourceTemplate source = resolveSource(item, sourceBase, sources, ifAbsent);
            if (source == null) continue;
            String fragment = renamedFragment(source, item.getTargetName());
            Element target = targetMatches.isEmpty() ? null : targetMatches.get(0);
            ensureCompatibleFraming(original, root, target, fragment);
            if (target != null) {
                String current = original.substring(target.lineStart, target.endWithLineBreak);
                if (current.equals(fragment)) continue;
                throw new IllegalArgumentException("Target AreaTemplate name collision for '"
                        + item.getTargetName() + "': existing subtree differs from donor");
            }
            additions.append(fragment);
            imported++;
        }

        if (additions.isEmpty()) {
            return new Result(original, false, 0);
        }
        int insertion = templateInsertionPoint(root);
        String result = original.substring(0, insertion) + additions
                + original.substring(insertion);
        validateFinalReferences(result);
        return new Result(result, true, imported);
    }

    private static Set<String> renderedTemplateNames(List<SkdDsl.Template> templates) {
        Set<String> result = new HashSet<>();
        if (templates == null) return result;
        for (SkdDsl.Template template : templates) {
            if (template != null && !blank(template.getName())) {
                if (!result.add(template.getName())) {
                    throw new IllegalArgumentException(
                            "Duplicate template target in templates: " + template.getName());
                }
            }
        }
        return result;
    }

    private static void validateItem(SkdTemplateImportDsl.ExactTemplate item) {
        if (item == null || blank(item.getSourceFile()) || blank(item.getSourceName())
                || blank(item.getTargetName())) {
            throw new IllegalArgumentException("Every exactTemplates item requires sourceFile, "
                    + "sourceName and targetName");
        }
    }

    private static SourceTemplate resolveSource(SkdTemplateImportDsl.ExactTemplate item,
                                                Path sourceBase,
                                                Map<Path, SourceDocument> cache,
                                                String ifAbsent) throws IOException {
        Path sourcePath = Path.of(item.getSourceFile());
        if (!sourcePath.isAbsolute()) {
            sourcePath = (sourceBase == null ? Path.of("") : sourceBase).resolve(sourcePath);
        }
        sourcePath = sourcePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(sourcePath)) {
            throw new IllegalArgumentException(
                    "SKD AreaTemplate source file not found: " + sourcePath);
        }
        SourceDocument document = cache.get(sourcePath);
        if (document == null) {
            String content = ByteSafeFileHandler.open(sourcePath).getContent();
            Element root = parse(content);
            requireSchemaRoot(root);
            document = new SourceDocument(content, root);
            cache.put(sourcePath, document);
        }

        SourceDocument resolved = document;
        List<Element> matches = directChildren(resolved.root(), "template").stream()
                .filter(template -> item.getSourceName().equals(
                        directText(resolved.content(), template, "name"))).toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Expected exactly one source AreaTemplate named '"
                    + item.getSourceName() + "', found " + matches.size());
        }
        if (matches.isEmpty()) {
            if ("noop".equals(ifAbsent)) return null;
            throw new IllegalArgumentException("Source AreaTemplate not found: "
                    + item.getSourceName() + " in " + sourcePath);
        }
        Element outer = matches.get(0);
        requireAreaTemplateBody(outer);
        Element name = requireDirectChild(outer, "name", "source AreaTemplate "
                + item.getSourceName());
        if (name.selfClosing || blank(directText(document.content(), outer, "name"))) {
            throw new IllegalArgumentException(
                    "Source AreaTemplate has an empty direct name: " + item.getSourceName());
        }
        return new SourceTemplate(document.content(), outer, name);
    }

    private static void requireAreaTemplateBody(Element outer) {
        List<Element> bodies = directChildren(outer, "template").stream()
                .filter(body -> "AreaTemplate".equals(localType(
                        attribute(body.startTag, "type")))).toList();
        if (bodies.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one AreaTemplate body in source '"
                    + Objects.toString(outer, "") + "', found " + bodies.size());
        }
    }

    private static String renamedFragment(SourceTemplate source, String targetName) {
        int fragmentStart = source.outer().lineStart;
        int fragmentEnd = source.outer().endWithLineBreak;
        int nameStart = source.name().startTagEnd + 1;
        int nameEnd = source.name().closeStart;
        String xml = source.content();
        return xml.substring(fragmentStart, nameStart) + escape(targetName)
                + xml.substring(nameEnd, fragmentEnd);
    }

    /**
     * Exact import не нормализует donor: несовместимый framing отклоняется, иначе
     * требование byte-equivalence было бы подменено скрытой пересериализацией.
     */
    private static void ensureCompatibleFraming(String targetXml, Element root,
                                                Element existingTarget,
                                                String fragment) {
        String targetEol = targetXml.contains("\r\n") ? "\r\n" : "\n";
        String sourceEol = fragment.contains("\r\n") ? "\r\n"
                : fragment.contains("\n") ? "\n" : "";
        if (!targetEol.equals(sourceEol) || !fragment.endsWith(targetEol)) {
            throw new IllegalArgumentException(
                    "AreaTemplate source line endings do not match target Designer XML");
        }

        Element sample = existingTarget;
        if (sample == null) {
            sample = directChildren(root, "template").stream().findFirst().orElse(null);
        }
        String expectedIndent = sample == null ? "\t"
                : targetXml.substring(sample.lineStart, sample.tagStart);
        int firstTag = fragment.indexOf('<');
        String sourceIndent = firstTag < 0 ? fragment : fragment.substring(0, firstTag);
        if (!expectedIndent.equals(sourceIndent)) {
            throw new IllegalArgumentException("AreaTemplate source indentation does not match target: "
                    + "expected '" + visible(expectedIndent) + "', found '"
                    + visible(sourceIndent) + "'");
        }
    }

    private static int templateInsertionPoint(Element root) {
        for (Element child : root.children) {
            if ("groupTemplate".equals(child.localName)
                    || "groupHeaderTemplate".equals(child.localName)
                    || "settingsVariant".equals(child.localName)) {
                return child.lineStart;
            }
        }
        return root.lineCloseStart;
    }

    private static void validateFinalReferences(String xml) {
        Element root = parse(xml);
        requireSchemaRoot(root);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element template : directChildren(root, "template")) {
            String name = directText(xml, template, "name");
            if (blank(name)) {
                throw new IllegalArgumentException("Root AreaTemplate has no direct name");
            }
            counts.merge(name, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() != 1) {
                throw new IllegalArgumentException("Duplicate root AreaTemplate name: "
                        + entry.getKey());
            }
        }
        for (Element child : root.children) {
            if (!"groupTemplate".equals(child.localName)
                    && !"groupHeaderTemplate".equals(child.localName)) continue;
            String reference = directText(xml, child, "template");
            int count = counts.getOrDefault(reference, 0);
            if (count != 1) {
                throw new IllegalArgumentException("Referenced AreaTemplate "
                        + (count == 0 ? "not found: " : "is ambiguous: ")
                        + Objects.toString(reference, ""));
            }
        }
    }

    private static Map<String, List<Element>> templatesByName(String xml, Element root) {
        Map<String, List<Element>> result = new LinkedHashMap<>();
        for (Element template : directChildren(root, "template")) {
            String name = directText(xml, template, "name");
            result.computeIfAbsent(Objects.toString(name, ""), ignored -> new ArrayList<>())
                    .add(template);
        }
        return result;
    }

    private static Element requireDirectChild(Element parent, String localName, String context) {
        List<Element> matches = directChildren(parent, localName);
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one direct " + localName
                    + " in " + context + ", found " + matches.size());
        }
        return matches.get(0);
    }

    private static List<Element> directChildren(Element parent, String localName) {
        return parent.children.stream()
                .filter(child -> localName.equals(child.localName)).toList();
    }

    private static String directText(String xml, Element parent, String localName) {
        List<Element> matches = directChildren(parent, localName);
        if (matches.isEmpty()) return null;
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Expected exactly one direct '" + localName
                    + "' in " + parent.localName + ", found " + matches.size());
        }
        Element child = matches.get(0);
        if (child.selfClosing) return null;
        return unescape(xml.substring(child.startTagEnd + 1, child.closeStart).trim());
    }

    private static void requireSchemaRoot(Element root) {
        if (!"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException("Expected root <DataCompositionSchema>");
        }
    }

    private static String attribute(String startTag, String wantedLocalName) {
        int position = 1;
        while (position < startTag.length()) {
            while (position < startTag.length()
                    && Character.isWhitespace(startTag.charAt(position))) position++;
            int nameStart = position;
            while (position < startTag.length()) {
                char current = startTag.charAt(position);
                if (Character.isWhitespace(current) || current == '='
                        || current == '>' || current == '/') break;
                position++;
            }
            String qName = startTag.substring(nameStart, position);
            if (qName.isEmpty()) break;
            while (position < startTag.length()
                    && Character.isWhitespace(startTag.charAt(position))) position++;
            if (position >= startTag.length() || startTag.charAt(position) != '=') continue;
            position++;
            while (position < startTag.length()
                    && Character.isWhitespace(startTag.charAt(position))) position++;
            if (position >= startTag.length()
                    || (startTag.charAt(position) != '\'' && startTag.charAt(position) != '"')) continue;
            char quote = startTag.charAt(position++);
            int valueStart = position;
            while (position < startTag.length() && startTag.charAt(position) != quote) position++;
            String value = startTag.substring(valueStart, Math.min(position, startTag.length()));
            if (wantedLocalName.equals(localName(qName))) return unescape(value);
            if (position < startTag.length()) position++;
        }
        return null;
    }

    private static String localType(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String visible(String value) {
        return value.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Element parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("AreaTemplate source XML is empty");
        }
        Deque<Element> stack = new ArrayDeque<>();
        Element root = null;
        for (int position = 0; position < xml.length();) {
            int lt = xml.indexOf('<', position);
            if (lt < 0) break;
            if (xml.startsWith("<!--", lt)) {
                position = requireEnd(xml, lt + 4, "-->") + 3;
                continue;
            }
            if (xml.startsWith("<![CDATA[", lt)) {
                position = requireEnd(xml, lt + 9, "]]>") + 3;
                continue;
            }
            if (xml.startsWith("<?", lt)) {
                position = requireEnd(xml, lt + 2, "?>") + 2;
                continue;
            }
            int gt = tagEnd(xml, lt + 1);
            if (xml.startsWith("<!", lt)) {
                position = gt + 1;
                continue;
            }
            boolean closing = xml.startsWith("</", lt);
            boolean selfClosing = !closing && isSelfClosing(xml, lt, gt);
            String qName = readName(xml, lt + (closing ? 2 : 1));
            if (closing) {
                if (stack.isEmpty() || !stack.peek().qName.equals(qName)) {
                    throw new IllegalArgumentException("Malformed XML near closing tag " + qName);
                }
                Element element = stack.pop();
                element.lineCloseStart = lineStart(xml, lt);
                element.closeStart = lt;
                element.endWithLineBreak = includeFollowingLineBreak(xml, gt + 1);
            } else {
                Element parent = stack.peek();
                Element element = new Element(qName, localName(qName), parent, lt,
                        lineStart(xml, lt), gt, xml.substring(lt, gt + 1), selfClosing);
                if (parent != null) parent.children.add(element);
                else if (root == null) root = element;
                else throw new IllegalArgumentException("Malformed XML: multiple roots");
                if (selfClosing) {
                    element.lineCloseStart = element.lineStart;
                    element.closeStart = gt;
                    element.endWithLineBreak = includeFollowingLineBreak(xml, gt + 1);
                } else {
                    stack.push(element);
                }
            }
            position = gt + 1;
        }
        if (root == null || !stack.isEmpty()) {
            throw new IllegalArgumentException("Malformed XML: unbalanced document");
        }
        return root;
    }

    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int index = from; index < xml.length(); index++) {
            char current = xml.charAt(index);
            if (quote != 0) {
                if (current == quote) quote = 0;
            } else if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '>') {
                return index;
            }
        }
        throw new IllegalArgumentException("Malformed XML: unterminated tag");
    }

    private static String readName(String xml, int from) {
        while (from < xml.length() && Character.isWhitespace(xml.charAt(from))) from++;
        int end = from;
        while (end < xml.length()) {
            char current = xml.charAt(end);
            if (Character.isWhitespace(current) || current == '>' || current == '/') break;
            end++;
        }
        return xml.substring(from, end);
    }

    private static int requireEnd(String xml, int from, String token) {
        int end = xml.indexOf(token, from);
        if (end < 0) throw new IllegalArgumentException("Malformed XML: unterminated " + token);
        return end;
    }

    private static boolean isSelfClosing(String xml, int lt, int gt) {
        int position = gt - 1;
        while (position > lt && Character.isWhitespace(xml.charAt(position))) position--;
        return xml.charAt(position) == '/';
    }

    private static int lineStart(String xml, int position) {
        int lf = xml.lastIndexOf('\n', Math.max(0, position - 1));
        int start = lf < 0 ? 0 : lf + 1;
        for (int index = start; index < position; index++) {
            if (!Character.isWhitespace(xml.charAt(index))) return position;
        }
        return start;
    }

    private static int includeFollowingLineBreak(String xml, int position) {
        int result = position;
        while (result < xml.length()
                && (xml.charAt(result) == ' ' || xml.charAt(result) == '\t')) result++;
        if (result < xml.length() && xml.charAt(result) == '\r') result++;
        if (result < xml.length() && xml.charAt(result) == '\n') result++;
        return result;
    }

    private static String localName(String qName) {
        int colon = qName.indexOf(':');
        return colon < 0 ? qName : qName.substring(colon + 1);
    }

    private static final class Element {
        final String qName;
        final String localName;
        final Element parent;
        final int tagStart;
        final int lineStart;
        final int startTagEnd;
        final String startTag;
        final boolean selfClosing;
        final List<Element> children = new ArrayList<>();
        int lineCloseStart;
        int closeStart;
        int endWithLineBreak;

        Element(String qName, String localName, Element parent, int tagStart,
                int lineStart, int startTagEnd, String startTag, boolean selfClosing) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.tagStart = tagStart;
            this.lineStart = lineStart;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
            this.selfClosing = selfClosing;
        }

        @Override
        public String toString() {
            return localName;
        }
    }

    private record SourceDocument(String content, Element root) {
    }

    private record SourceTemplate(String content, Element outer, Element name) {
    }
}
//++agent TASK-174

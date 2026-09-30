package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDataSetUpsertDsl;

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

//++agent TASK-174 [11.07.2026 21:57:04]
/**
 * Byte-lossless upsert целых root {@code dataSet} существующей Designer СКД.
 * Редактор переносит exact span из donor/inline XML и не сериализует соседние
 * datasets, settings или ручные AreaTemplates.
 */
public final class SkdDataSetUpsertEditor {

    private static final Set<String> SUPPORTED_TYPES = Set.of("DataSetQuery", "DataSetObject");
    private static final Set<String> EXISTING_TYPES = Set.of(
            "DataSetQuery", "DataSetObject", "DataSetUnion");

    public record Result(String content, boolean changed, int upserted) {
    }

    public Result apply(String original, SkdDataSetUpsertDsl patch, Path sourceBase)
            throws IOException {
        Element root = parse(original);
        requireSchemaRoot(root);
        Request request = validateRequest(patch);
        Map<String, List<Element>> existingByName = dataSetsByName(original, root);
        Set<String> payloadNames = new HashSet<>();
        Map<Path, SourceDocument> sourceCache = new HashMap<>();
        List<Replacement> replacements = new ArrayList<>();
        StringBuilder additions = new StringBuilder();
        int changed = 0;

        for (SkdDataSetUpsertDsl.DataSet item : patch.getDataSets()) {
            validateItem(item, patch.getSourceFile());
            String identity = item.getName() + "\u0000" + item.getType();
            if (!payloadNames.add(item.getName())) {
                throw new IllegalArgumentException(
                        "Duplicate dataSet identity in payload: " + identity);
            }

            List<Element> nameMatches = existingByName.getOrDefault(item.getName(), List.of());
            if (nameMatches.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one root dataSet named '"
                        + item.getName() + "', found " + nameMatches.size());
            }
            Element existing = nameMatches.isEmpty() ? null : nameMatches.get(0);
            if (existing != null) {
                String existingType = dataSetType(existing);
                if (!item.getType().equals(existingType)) {
                    throw new IllegalArgumentException("Root dataSet name collision for '"
                            + item.getName() + "': requested type '" + item.getType()
                            + "', existing type '" + Objects.toString(existingType, "") + "'");
                }
            } else if ("replace".equals(request.mode())) {
                if ("fail".equals(request.ifAbsent())) {
                    throw new IllegalArgumentException(
                            "Target dataSet not found for replace: " + item.getName());
                }
                continue;
            }

            String sourceFragment = resolveSourceFragment(
                    item, patch.getSourceFile(), sourceBase, sourceCache);
            ensureCompatibleFraming(original, root, existing, sourceFragment);
            if (existing == null) {
                additions.append(sourceFragment);
                changed++;
                continue;
            }

            String current = original.substring(existing.lineStart, existing.endWithLineBreak);
            if (!current.equals(sourceFragment)) {
                replacements.add(new Replacement(
                        existing.lineStart, existing.endWithLineBreak, sourceFragment));
                changed++;
            }
        }

        if (!additions.isEmpty()) {
            int insertion = dataSetInsertionPoint(root);
            replacements.add(new Replacement(insertion, insertion, additions.toString()));
        }
        if (replacements.isEmpty()) {
            return new Result(original, false, 0);
        }

        replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        String result = original;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start()) + replacement.value()
                    + result.substring(replacement.end());
        }

        validateFinalReferences(result);
        return new Result(result, true, changed);
    }

    private static Request validateRequest(SkdDataSetUpsertDsl patch) {
        if (patch == null || patch.getDataSets() == null || patch.getDataSets().isEmpty()) {
            throw new IllegalArgumentException("SKD dataSet upsert requires non-empty dataSets");
        }
        String mode = patch.getMode() == null ? "upsert" : patch.getMode();
        if (!"upsert".equals(mode) && !"replace".equals(mode)) {
            throw new IllegalArgumentException("mode must be 'upsert' or 'replace'");
        }
        String ifAbsent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        if (!"fail".equals(ifAbsent) && !"noop".equals(ifAbsent)) {
            throw new IllegalArgumentException("ifAbsent must be 'fail' or 'noop'");
        }
        if (patch.getSourceFile() != null && patch.getSourceFile().isBlank()) {
            throw new IllegalArgumentException("sourceFile must be non-blank when specified");
        }
        return new Request(mode, ifAbsent);
    }

    private static void validateItem(SkdDataSetUpsertDsl.DataSet item,
                                     String defaultSourceFile) {
        if (item == null || blank(item.getName()) || blank(item.getType())) {
            throw new IllegalArgumentException("Every dataSets item requires name and type");
        }
        if (!SUPPORTED_TYPES.contains(item.getType())) {
            throw new IllegalArgumentException("Unsupported dataSet type '" + item.getType()
                    + "'; expected DataSetQuery or DataSetObject");
        }
        if (item.getSourceFile() != null && item.getSourceFile().isBlank()) {
            throw new IllegalArgumentException("dataSets sourceFile must be non-blank when specified");
        }
        String sourceFile = item.getSourceFile() != null
                ? item.getSourceFile() : defaultSourceFile;
        boolean hasXml = item.getXml() != null;
        boolean hasFile = sourceFile != null;
        if (hasXml == hasFile) {
            throw new IllegalArgumentException("DataSet '" + item.getName()
                    + "' requires exactly one source: xml or sourceFile");
        }
        if (hasXml && item.getXml().isBlank()) {
            throw new IllegalArgumentException(
                    "Inline dataSet XML must be non-blank: " + item.getName());
        }
    }

    private static String resolveSourceFragment(SkdDataSetUpsertDsl.DataSet item,
                                                String defaultSourceFile,
                                                Path sourceBase,
                                                Map<Path, SourceDocument> cache)
            throws IOException {
        if (item.getXml() != null) {
            Element inline = parse(item.getXml());
            if (!"dataSet".equals(inline.localName)) {
                throw new IllegalArgumentException(
                        "Inline XML root must be dataSet: " + item.getName());
            }
            ensureOnlyWhitespaceOutsideRoot(item.getXml(), inline);
            verifyIdentity(item, item.getXml(), inline, "inline XML");
            return item.getXml().substring(inline.lineStart, inline.endWithLineBreak);
        }

        String fileSpec = item.getSourceFile() != null
                ? item.getSourceFile() : defaultSourceFile;
        Path source = Path.of(fileSpec);
        if (!source.isAbsolute()) {
            source = (sourceBase == null ? Path.of("") : sourceBase).resolve(source);
        }
        source = source.toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("SKD dataSet source file not found: " + source);
        }
        SourceDocument cachedDocument = cache.get(source);
        if (cachedDocument == null) {
            String content = ByteSafeFileHandler.open(source).getContent();
            cachedDocument = new SourceDocument(content, parse(content));
            cache.put(source, cachedDocument);
        }
        SourceDocument document = cachedDocument;
        Element sourceRoot = document.root();
        if ("dataSet".equals(sourceRoot.localName)) {
            ensureOnlyWhitespaceOutsideRoot(document.content(), sourceRoot);
            verifyIdentity(item, document.content(), sourceRoot, "source file " + source);
            return document.content().substring(sourceRoot.lineStart, sourceRoot.endWithLineBreak);
        }
        if (!"DataCompositionSchema".equals(sourceRoot.localName)) {
            throw new IllegalArgumentException("SKD dataSet source must have root dataSet or "
                    + "DataCompositionSchema: " + source);
        }
        List<Element> nameMatches = sourceRoot.children.stream()
                .filter(child -> "dataSet".equals(child.localName))
                .filter(child -> item.getName().equals(
                        directText(document.content(), child, "name")))
                .toList();
        if (nameMatches.isEmpty()) {
            throw new IllegalArgumentException("Source dataSet not found: " + item.getName()
                    + " in " + source);
        }
        if (nameMatches.size() > 1) {
            throw new IllegalArgumentException("Expected exactly one source dataSet named '"
                    + item.getName() + "', found " + nameMatches.size());
        }
        Element sourceDataSet = nameMatches.get(0);
        verifyIdentity(item, document.content(), sourceDataSet, "source file " + source);
        return document.content().substring(
                sourceDataSet.lineStart, sourceDataSet.endWithLineBreak);
    }

    private static void verifyIdentity(SkdDataSetUpsertDsl.DataSet requested,
                                       String xml, Element source, String origin) {
        String sourceName = directText(xml, source, "name");
        String sourceType = dataSetType(source);
        if (!requested.getName().equals(sourceName)
                || !requested.getType().equals(sourceType)) {
            throw new IllegalArgumentException("dataSet identity mismatch in " + origin
                    + ": requested ('" + requested.getName() + "','" + requested.getType()
                    + "'), found ('" + Objects.toString(sourceName, "") + "','"
                    + Objects.toString(sourceType, "") + "')");
        }
    }

    /**
     * Exact fragment сохраняется без преобразований, поэтому его framing обязан
     * уже совпадать с целевым Designer-файлом; иначе смешанные EOL/indent стали бы
     * скрытой сериализацией, а не lossless import.
     */
    private static void ensureCompatibleFraming(String target, Element root,
                                                Element existing, String fragment) {
        String targetEol = target.contains("\r\n") ? "\r\n" : "\n";
        String sourceEol = fragment.contains("\r\n") ? "\r\n"
                : fragment.contains("\n") ? "\n" : "";
        if (!targetEol.equals(sourceEol) || !fragment.endsWith(targetEol)) {
            throw new IllegalArgumentException(
                    "dataSet source line endings do not match target Designer XML");
        }

        String expectedIndent;
        if (existing != null) {
            expectedIndent = target.substring(existing.lineStart, existing.tagStart);
        } else {
            Element sample = root.children.stream()
                    .filter(child -> "dataSet".equals(child.localName)).findFirst().orElse(null);
            if (sample != null) {
                expectedIndent = target.substring(sample.lineStart, sample.tagStart);
            } else {
                Element child = root.children.stream().findFirst().orElse(null);
                expectedIndent = child == null ? "\t"
                        : target.substring(child.lineStart, child.tagStart);
            }
        }
        int firstTag = fragment.indexOf('<');
        String sourceIndent = firstTag < 0 ? fragment : fragment.substring(0, firstTag);
        if (!expectedIndent.equals(sourceIndent)) {
            throw new IllegalArgumentException("dataSet source indentation does not match target: expected '"
                    + visible(expectedIndent) + "', found '" + visible(sourceIndent) + "'");
        }
    }

    private static int dataSetInsertionPoint(Element root) {
        List<Element> dataSets = root.children.stream()
                .filter(child -> "dataSet".equals(child.localName)).toList();
        if (!dataSets.isEmpty()) return dataSets.get(dataSets.size() - 1).endWithLineBreak;
        List<Element> dataSources = root.children.stream()
                .filter(child -> "dataSource".equals(child.localName)).toList();
        if (!dataSources.isEmpty()) return dataSources.get(dataSources.size() - 1).endWithLineBreak;
        if (!root.children.isEmpty()) return root.children.get(0).lineStart;
        return root.lineCloseStart;
    }

    private static void validateFinalReferences(String xml) {
        Element root = parse(xml);
        requireSchemaRoot(root);
        Map<String, Element> dataSources = uniqueNamedChildren(xml, root, "dataSource");
        Map<String, Element> dataSets = uniqueNamedChildren(xml, root, "dataSet");
        Map<String, Set<String>> fieldsByDataSet = new LinkedHashMap<>();

        for (Map.Entry<String, Element> entry : dataSets.entrySet()) {
            String name = entry.getKey();
            Element dataSet = entry.getValue();
            String type = dataSetType(dataSet);
            if (!EXISTING_TYPES.contains(type)) {
                throw new IllegalArgumentException("Unknown root dataSet type '"
                        + Objects.toString(type, "") + "' for '" + name + "'");
            }
            Set<String> fields = new HashSet<>();
            Set<String> dataPaths = new HashSet<>();
            for (Element field : dataSet.children.stream()
                    .filter(child -> "field".equals(child.localName)).toList()) {
                String dataPath = directText(xml, field, "dataPath");
                String physical = directText(xml, field, "field");
                if (!blank(dataPath) && !dataPaths.add(dataPath)) {
                    throw new IllegalArgumentException("Duplicate dataPath '" + dataPath
                            + "' in root dataSet '" + name + "'");
                }
                if (!blank(dataPath)) fields.add(dataPath);
                if (!blank(physical)) fields.add(physical);
            }
            for (Element calculated : dataSet.children.stream()
                    .filter(child -> "calculatedField".equals(child.localName)).toList()) {
                String dataPath = directText(xml, calculated, "dataPath");
                if (!blank(dataPath)) fields.add(dataPath);
            }
            fieldsByDataSet.put(name, fields);

            if ("DataSetQuery".equals(type)) {
                String dataSource = directText(xml, dataSet, "dataSource");
                if (blank(dataSource) || !dataSources.containsKey(dataSource)) {
                    throw new IllegalArgumentException("Unknown dataSource '"
                            + Objects.toString(dataSource, "") + "' in DataSetQuery '" + name + "'");
                }
                if (blank(directRawText(xml, dataSet, "query"))) {
                    throw new IllegalArgumentException("DataSetQuery '" + name + "' has no query");
                }
            } else if ("DataSetObject".equals(type)
                    && blank(directText(xml, dataSet, "objectName"))) {
                throw new IllegalArgumentException("DataSetObject '" + name + "' has no objectName");
            }
        }

        for (Element link : root.children.stream()
                .filter(child -> "dataSetLink".equals(child.localName)).toList()) {
            String source = directText(xml, link, "sourceDataSet");
            String destination = directText(xml, link, "destinationDataSet");
            String sourceExpression = directText(xml, link, "sourceExpression");
            String destinationExpression = directText(xml, link, "destinationExpression");
            if (!dataSets.containsKey(source)) {
                throw new IllegalArgumentException(
                        "dataSetLink source dataSet not found: " + source);
            }
            if (!dataSets.containsKey(destination)) {
                throw new IllegalArgumentException(
                        "dataSetLink destination dataSet not found: " + destination);
            }
            if (blank(sourceExpression)
                    || !fieldsByDataSet.get(source).contains(sourceExpression)) {
                throw new IllegalArgumentException("dataSetLink source field '"
                        + Objects.toString(sourceExpression, "") + "' is missing in dataSet '"
                        + source + "'");
            }
            if (blank(destinationExpression)
                    || !fieldsByDataSet.get(destination).contains(destinationExpression)) {
                throw new IllegalArgumentException("dataSetLink destination field '"
                        + Objects.toString(destinationExpression, "")
                        + "' is missing in dataSet '" + destination + "'");
            }
        }
    }

    private static Map<String, Element> uniqueNamedChildren(String xml, Element root,
                                                            String localName) {
        Map<String, Element> result = new LinkedHashMap<>();
        for (Element child : root.children) {
            if (!localName.equals(child.localName)) continue;
            String name = directText(xml, child, "name");
            if (blank(name)) {
                throw new IllegalArgumentException("Unnamed root " + localName);
            }
            if (result.putIfAbsent(name, child) != null) {
                throw new IllegalArgumentException("Duplicate root " + localName
                        + " name: " + name);
            }
        }
        return result;
    }

    private static Map<String, List<Element>> dataSetsByName(String xml, Element root) {
        Map<String, List<Element>> result = new LinkedHashMap<>();
        for (Element child : root.children) {
            if (!"dataSet".equals(child.localName)) continue;
            String name = directText(xml, child, "name");
            result.computeIfAbsent(Objects.toString(name, ""), ignored -> new ArrayList<>())
                    .add(child);
        }
        return result;
    }

    private static String dataSetType(Element dataSet) {
        return localType(attribute(dataSet.startTag, "type"));
    }

    private static String directText(String xml, Element parent, String localName) {
        String raw = directRawText(xml, parent, localName);
        return raw == null ? null : unescape(raw.trim());
    }

    private static String directRawText(String xml, Element parent, String localName) {
        List<Element> matches = parent.children.stream()
                .filter(child -> localName.equals(child.localName)).toList();
        if (matches.isEmpty()) return null;
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Expected one direct '" + localName
                    + "' in " + parent.localName + ", found " + matches.size());
        }
        Element child = matches.get(0);
        return child.closeStart < child.startTagEnd ? null
                : xml.substring(child.startTagEnd + 1, child.closeStart);
    }

    private static void requireSchemaRoot(Element root) {
        if (!"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException("Expected root <DataCompositionSchema>");
        }
    }

    private static void ensureOnlyWhitespaceOutsideRoot(String xml, Element root) {
        if (!xml.substring(0, root.tagStart).isBlank()
                || !xml.substring(root.endWithLineBreak).isBlank()) {
            throw new IllegalArgumentException("DataSet source XML must contain exactly one root element");
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
            throw new IllegalArgumentException("DataSet source XML is empty");
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
                        lineStart(xml, lt), gt, xml.substring(lt, gt + 1));
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
        final List<Element> children = new ArrayList<>();
        int lineCloseStart;
        int closeStart;
        int endWithLineBreak;

        Element(String qName, String localName, Element parent, int tagStart,
                int lineStart, int startTagEnd, String startTag) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.tagStart = tagStart;
            this.lineStart = lineStart;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
        }
    }

    private record Request(String mode, String ifAbsent) {
    }

    private record Replacement(int start, int end, String value) {
    }

    private record SourceDocument(String content, Element root) {
    }
}
//++agent TASK-174

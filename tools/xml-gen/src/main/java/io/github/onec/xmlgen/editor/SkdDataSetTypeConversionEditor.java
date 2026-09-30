package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDataSetTypeConversionDsl;

import java.math.BigDecimal;
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

//++agent TASK-174 [13.07.2026 00:00:00]
/**
 * XG-99: byte-lossless Query→Object migration with exact direct-filter cleanup.
 * Изменяются только заранее проверенные spans, чтобы не сериализовать живые ссылки,
 * настройки и физические AreaTemplate.
 */
public final class SkdDataSetTypeConversionEditor {

    public record Result(String content, boolean changed, int converted, int removedFilters) { }

    public Result apply(String original, SkdDataSetTypeConversionDsl patch) {
        Element root = parse(original);
        require("DataCompositionSchema".equals(root.localName),
                "Expected root <DataCompositionSchema>");
        Request request = validateRequest(patch);
        Map<String, List<Element>> dataSets = namedDirectChildren(original, root, "dataSet");
        List<Replacement> replacements = new ArrayList<>();
        Set<String> requestedNames = new HashSet<>();
        int converted = 0;
        boolean allAlreadyConverted = true;

        for (SkdDataSetTypeConversionDsl.DataSet item : patch.getDataSets()) {
            validateDataSetItem(item);
            require(requestedNames.add(item.getName()),
                    "Duplicate dataSet selector: " + item.getName());
            List<Element> matches = dataSets.getOrDefault(item.getName(), List.of());
            if (matches.isEmpty()) {
                if ("noop".equals(request.ifAbsent())) continue;
                throw new IllegalArgumentException("Target dataSet not found: " + item.getName());
            }
            require(matches.size() == 1, "Expected exactly one root dataSet named '"
                    + item.getName() + "', found " + matches.size());
            Element dataSet = matches.get(0);
            int fields = directChildren(dataSet, "field").size();
            require(fields == item.getExpectedFieldCount(), "Field count mismatch for dataSet '"
                    + item.getName() + "': expected " + item.getExpectedFieldCount()
                    + ", found " + fields);
            String actualType = localType(attribute(dataSet.startTag, "type"));
            if (item.getToType().equals(actualType)) {
                validateConverted(original, dataSet, item);
                continue;
            }
            allAlreadyConverted = false;
            require(item.getFromType().equals(actualType), "dataSet type mismatch for '"
                    + item.getName() + "': expected " + item.getFromType()
                    + " or already " + item.getToType() + ", found " + actualType);
            List<Element> dataSources = directChildren(dataSet, "dataSource");
            List<Element> queries = directChildren(dataSet, "query");
            require(dataSources.size() == 1 && queries.size() == 1,
                    "DataSetQuery '" + item.getName()
                            + "' must have exactly one direct dataSource and query");
            require(!blank(directText(original, dataSet, "dataSource")),
                    "DataSetQuery '" + item.getName()
                            + "' has empty direct dataSource; no default is inferred");
            require(directChildren(dataSet, "objectName").isEmpty(),
                    "DataSetQuery '" + item.getName() + "' already has objectName");

            String newStartTag = replaceType(dataSet.startTag,
                    item.getFromType(), item.getToType());
            replacements.add(new Replacement(dataSet.tagStart, dataSet.startTagEnd + 1,
                    newStartTag));
            Element query = queries.get(0);
            String indent = original.substring(query.lineStart, query.tagStart);
            String eol = original.contains("\r\n") ? "\r\n" : "\n";
            String objectName = indent + "<objectName>" + escape(item.getObjectName())
                    + "</objectName>" + eol;
            // dataSource — общий обязательный direct node Query/Object. Его exact span
            // сохраняется; только query заменяется objectName в каноническом порядке.
            replacements.add(new Replacement(query.lineStart, query.endWithLineBreak, objectName));
            converted++;
        }

        int removedFilters = collectFilterReplacements(original, root, patch.getRemoveFilters(),
                allAlreadyConverted, replacements);
        validateReferences(original, root);
        if (replacements.isEmpty()) return new Result(original, false, 0, 0);
        replacements.sort((a, b) -> Integer.compare(b.start, a.start));
        assertNonOverlapping(replacements);
        String result = original;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start) + replacement.value
                    + result.substring(replacement.end);
        }
        Element finalRoot = parse(result);
        validateReferences(result, finalRoot);
        return new Result(result, true, converted, removedFilters);
    }

    private static Request validateRequest(SkdDataSetTypeConversionDsl patch) {
        require(patch != null && patch.getDataSets() != null && !patch.getDataSets().isEmpty(),
                "SKD type conversion requires non-empty dataSets");
        require(patch.getRemoveFilters() != null && !patch.getRemoveFilters().isEmpty(),
                "SKD type conversion requires non-empty removeFilters");
        String ifAbsent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        require("fail".equals(ifAbsent) || "noop".equals(ifAbsent),
                "ifAbsent must be 'fail' or 'noop'");
        return new Request(ifAbsent);
    }

    private static void validateDataSetItem(SkdDataSetTypeConversionDsl.DataSet item) {
        require(item != null && !blank(item.getName()) && !blank(item.getObjectName())
                        && item.getExpectedFieldCount() != null,
                "Every dataSets item requires name, objectName and expectedFieldCount");
        require("DataSetQuery".equals(item.getFromType())
                        && "DataSetObject".equals(item.getToType()),
                "Only DataSetQuery to DataSetObject conversion is supported");
        require(item.getExpectedFieldCount() >= 0, "expectedFieldCount must be non-negative");
    }

    private static void validateConverted(String xml, Element dataSet,
                                          SkdDataSetTypeConversionDsl.DataSet item) {
        List<Element> dataSources = directChildren(dataSet, "dataSource");
        require(dataSources.size() == 1
                        && !blank(directText(xml, dataSet, "dataSource")),
                "Already converted DataSetObject '" + item.getName()
                        + "' must have exactly one non-empty dataSource");
        require(directChildren(dataSet, "query").isEmpty(),
                "Already converted DataSetObject '" + item.getName() + "' still has query");
        List<Element> objectNames = directChildren(dataSet, "objectName");
        require(objectNames.size() == 1
                        && item.getObjectName().equals(directText(xml, dataSet, "objectName")),
                "objectName mismatch for already converted dataSet '" + item.getName() + "'");
    }

    private static int collectFilterReplacements(
            String xml, Element root, List<SkdDataSetTypeConversionDsl.Filter> selectors,
            boolean allowAbsent, List<Replacement> replacements) {
        Set<String> identities = new HashSet<>();
        int removed = 0;
        for (SkdDataSetTypeConversionDsl.Filter selector : selectors) {
            validateFilter(selector);
            String identity = selector.getVariant() + "\u0000" + selector.getStructurePath()
                    + "\u0000" + selector.getField() + "\u0000" + selector.getComparison()
                    + "\u0000" + selector.getRightValue().stripTrailingZeros().toPlainString();
            String diagnostic = filterSelectorDiagnostic(selector);
            require(identities.add(identity), "Duplicate filter selector: " + diagnostic);
            List<Element> variants = root.children.stream()
                    .filter(e -> "settingsVariant".equals(e.localName))
                    .filter(e -> selector.getVariant().equals(directText(xml, e, "name"))).toList();
            require(variants.size() == 1, "Expected exactly one settings variant '"
                    + selector.getVariant() + "', found " + variants.size());
            Element group = findGroupByPath(xml, variants.get(0), selector.getStructurePath());
            List<Element> matchingFilters = new ArrayList<>();
            for (Element filter : directChildren(group, "filter")) {
                List<Element> items = directChildren(filter, "item");
                if (items.size() != 1) continue;
                Element item = items.get(0);
                if (!"FilterItemComparison".equals(localType(attribute(item.startTag, "type")))) continue;
                if (selector.getField().equals(directText(xml, item, "left"))
                        && selector.getComparison().equals(directText(xml, item, "comparisonType"))
                        && decimalEquals(selector.getRightValue(), directText(xml, item, "right"))) {
                    matchingFilters.add(filter);
                }
            }
            if (matchingFilters.isEmpty() && allowAbsent) continue;
            require(matchingFilters.size() == 1, "Expected exactly one direct filter for selector "
                    + diagnostic + ", found " + matchingFilters.size());
            Element filter = matchingFilters.get(0);
            replacements.add(new Replacement(filter.lineStart, filter.endWithLineBreak, ""));
            removed++;
        }
        return removed;
    }

    private static String filterSelectorDiagnostic(SkdDataSetTypeConversionDsl.Filter selector) {
        return "{variant='" + selector.getVariant() + "', structurePath='"
                + selector.getStructurePath() + "', field='" + selector.getField()
                + "', comparison='" + selector.getComparison() + "', rightValue="
                + selector.getRightValue().stripTrailingZeros().toPlainString() + "}";
    }

    private static void validateFilter(SkdDataSetTypeConversionDsl.Filter selector) {
        require(selector != null && !blank(selector.getVariant())
                        && !blank(selector.getStructurePath()) && !blank(selector.getField())
                        && !blank(selector.getComparison()) && selector.getRightValue() != null,
                "Every removeFilters item requires variant, structurePath, field, comparison and rightValue");
    }

    private static Element findGroupByPath(String xml, Element variant, String path) {
        String[] segments = path.split("/", -1);
        for (String segment : segments) require(!segment.isBlank(), "Invalid structurePath: " + path);
        List<Element> candidates = descendants(variant).stream()
                .filter(SkdDataSetTypeConversionEditor::isStructureGroup)
                .filter(e -> segments[segments.length - 1].equals(directText(xml, e, "name")))
                .filter(e -> groupPath(xml, e).equals(path)).toList();
        require(candidates.size() == 1, "Expected exactly one structurePath '" + path
                + "', found " + candidates.size());
        return candidates.get(0);
    }

    private static String groupPath(String xml, Element group) {
        Deque<String> names = new ArrayDeque<>();
        Element current = group;
        while (current != null) {
            if (isStructureGroup(current)) names.addFirst(directText(xml, current, "name"));
            current = current.parent;
        }
        return String.join("/", names);
    }

    private static boolean isStructureGroup(Element element) {
        return "item".equals(element.localName)
                && "StructureItemGroup".equals(localType(attribute(element.startTag, "type")));
    }

    /** Same-name conversion preserves identities; validation prevents mutating an already broken graph. */
    private static void validateReferences(String xml, Element root) {
        Map<String, List<Element>> dataSets = namedDirectChildren(xml, root, "dataSet");
        Map<String, Set<String>> fields = new LinkedHashMap<>();
        for (Map.Entry<String, List<Element>> entry : dataSets.entrySet()) {
            require(entry.getValue().size() == 1, "Duplicate root dataSet name: " + entry.getKey());
            Set<String> names = new HashSet<>();
            for (Element field : directChildren(entry.getValue().get(0), "field")) {
                String dataPath = directText(xml, field, "dataPath");
                String physical = directText(xml, field, "field");
                if (!blank(dataPath)) names.add(dataPath);
                if (!blank(physical)) names.add(physical);
            }
            fields.put(entry.getKey(), names);
        }
        for (Element link : directChildren(root, "dataSetLink")) {
            String source = directText(xml, link, "sourceDataSet");
            String destination = directText(xml, link, "destinationDataSet");
            String sourceField = directText(xml, link, "sourceExpression");
            String destinationField = directText(xml, link, "destinationExpression");
            require(fields.containsKey(source), "dataSetLink source dataSet not found: " + source);
            require(fields.containsKey(destination), "dataSetLink destination dataSet not found: " + destination);
            require(fields.get(source).contains(sourceField), "dataSetLink source field missing: " + sourceField);
            require(fields.get(destination).contains(destinationField),
                    "dataSetLink destination field missing: " + destinationField);
        }
    }

    private static String replaceType(String startTag, String from, String to) {
        String marker = "xsi:type=\"" + from + "\"";
        int at = startTag.indexOf(marker);
        require(at >= 0 && startTag.indexOf(marker, at + 1) < 0,
                "Expected exact xsi:type=\"" + from + "\"");
        return startTag.substring(0, at) + "xsi:type=\"" + to + "\""
                + startTag.substring(at + marker.length());
    }

    private static void assertNonOverlapping(List<Replacement> replacements) {
        int previousStart = Integer.MAX_VALUE;
        for (Replacement item : replacements) {
            require(item.end <= previousStart, "Internal error: overlapping mutation spans");
            previousStart = item.start;
        }
    }

    private static Map<String, List<Element>> namedDirectChildren(String xml, Element root, String name) {
        Map<String, List<Element>> result = new HashMap<>();
        for (Element child : directChildren(root, name)) {
            result.computeIfAbsent(Objects.toString(directText(xml, child, "name"), ""),
                    ignored -> new ArrayList<>()).add(child);
        }
        return result;
    }

    private static List<Element> directChildren(Element parent, String name) {
        return parent.children.stream().filter(e -> name.equals(e.localName)).toList();
    }

    private static List<Element> descendants(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Element child : parent.children) {
            result.add(child);
            result.addAll(descendants(child));
        }
        return result;
    }

    private static String directText(String xml, Element parent, String name) {
        List<Element> matches = directChildren(parent, name);
        if (matches.isEmpty()) return null;
        require(matches.size() == 1, "Expected one direct '" + name + "', found " + matches.size());
        Element child = matches.get(0);
        return unescape(xml.substring(child.startTagEnd + 1, child.closeStart).trim());
    }

    private static boolean decimalEquals(BigDecimal expected, String actual) {
        try { return actual != null && expected.compareTo(new BigDecimal(actual)) == 0; }
        catch (NumberFormatException ignored) { return false; }
    }

    private static String attribute(String tag, String localName) {
        String[] quotes = {"\"", "'"};
        for (String quote : quotes) {
            String marker = "xsi:" + localName + "=" + quote;
            int start = tag.indexOf(marker);
            if (start >= 0) {
                start += marker.length();
                int end = tag.indexOf(quote, start);
                return end < 0 ? null : unescape(tag.substring(start, end));
            }
        }
        return null;
    }

    private static String localType(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    private static Element parse(String xml) {
        require(xml != null && !xml.isBlank(), "SKD XML is empty");
        Deque<Element> stack = new ArrayDeque<>();
        Element root = null;
        for (int position = 0; position < xml.length();) {
            int lt = xml.indexOf('<', position);
            if (lt < 0) break;
            if (xml.startsWith("<!--", lt)) { position = end(xml, lt + 4, "-->") + 3; continue; }
            if (xml.startsWith("<![CDATA[", lt)) { position = end(xml, lt + 9, "]]>") + 3; continue; }
            if (xml.startsWith("<?", lt)) { position = end(xml, lt + 2, "?>") + 2; continue; }
            int gt = tagEnd(xml, lt + 1);
            if (xml.startsWith("<!", lt)) { position = gt + 1; continue; }
            boolean closing = xml.startsWith("</", lt);
            boolean selfClosing = !closing && selfClosing(xml, lt, gt);
            String qName = readName(xml, lt + (closing ? 2 : 1));
            if (closing) {
                require(!stack.isEmpty() && stack.peek().qName.equals(qName),
                        "Malformed XML near closing tag " + qName);
                Element element = stack.pop();
                element.closeStart = lt;
                element.endWithLineBreak = includeEol(xml, gt + 1);
            } else {
                Element parent = stack.peek();
                Element element = new Element(qName, localName(qName), parent, lt,
                        lineStart(xml, lt), gt, xml.substring(lt, gt + 1));
                if (parent != null) parent.children.add(element);
                else { require(root == null, "Malformed XML: multiple roots"); root = element; }
                if (selfClosing) {
                    element.closeStart = gt;
                    element.endWithLineBreak = includeEol(xml, gt + 1);
                } else stack.push(element);
            }
            position = gt + 1;
        }
        require(root != null && stack.isEmpty(), "Malformed XML: unbalanced document");
        return root;
    }

    private static int tagEnd(String xml, int from) {
        char quote = 0;
        for (int i = from; i < xml.length(); i++) {
            char c = xml.charAt(i);
            if (quote != 0) { if (c == quote) quote = 0; }
            else if (c == '\'' || c == '"') quote = c;
            else if (c == '>') return i;
        }
        throw new IllegalArgumentException("Malformed XML: unterminated tag");
    }

    private static String readName(String xml, int from) {
        while (from < xml.length() && Character.isWhitespace(xml.charAt(from))) from++;
        int end = from;
        while (end < xml.length() && !Character.isWhitespace(xml.charAt(end))
                && xml.charAt(end) != '>' && xml.charAt(end) != '/') end++;
        return xml.substring(from, end);
    }

    private static int end(String xml, int from, String token) {
        int result = xml.indexOf(token, from);
        require(result >= 0, "Malformed XML: unterminated " + token);
        return result;
    }

    private static boolean selfClosing(String xml, int lt, int gt) {
        int i = gt - 1;
        while (i > lt && Character.isWhitespace(xml.charAt(i))) i--;
        return xml.charAt(i) == '/';
    }

    private static int lineStart(String xml, int position) {
        int lf = xml.lastIndexOf('\n', Math.max(0, position - 1));
        int start = lf < 0 ? 0 : lf + 1;
        for (int i = start; i < position; i++) if (!Character.isWhitespace(xml.charAt(i))) return position;
        return start;
    }

    private static int includeEol(String xml, int position) {
        int result = position;
        while (result < xml.length() && (xml.charAt(result) == ' ' || xml.charAt(result) == '\t')) result++;
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
        int closeStart;
        int endWithLineBreak;

        Element(String qName, String localName, Element parent, int tagStart,
                int lineStart, int startTagEnd, String startTag) {
            this.qName = qName; this.localName = localName; this.parent = parent;
            this.tagStart = tagStart; this.lineStart = lineStart;
            this.startTagEnd = startTagEnd; this.startTag = startTag;
        }
    }

    private record Request(String ifAbsent) { }
    private record Replacement(int start, int end, String value) { }
}
//++agent TASK-174

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDataSetFieldRenameDsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

//++agent TASK-174 [11.07.2026 09:45:00]
/**
 * Переименовывает только пару dataPath/field у direct DataSetFieldField.
 * Ссылки settings/resources/links/templates намеренно сохраняются: их изменение требует
 * отдельных доменных операций и не должно быть неявным побочным эффектом rename.
 */
public final class SkdDataSetFieldRenameEditor {

    public record Result(String content, boolean changed, int renamed,
                         Set<String> preservedReferenceNames) {
    }

    public Result apply(String original, SkdDataSetFieldRenameDsl patch) {
        Element root = parse(original);
        Plan plan = preflight(original, root, patch);
        List<Replacement> replacements = new ArrayList<>(plan.replacements());
        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        String result = original;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start()) + replacement.value()
                    + result.substring(replacement.end());
        }
        parse(result);
        return new Result(result, !result.equals(original), plan.renamed(),
                plan.preservedReferenceNames());
    }

    private static Plan preflight(String xml, Element root, SkdDataSetFieldRenameDsl patch) {
        if (patch == null || !"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException(
                    "Expected non-empty SKD rename payload and root DataCompositionSchema");
        }
        if (patch.getFields() == null || patch.getFields().isEmpty()) {
            throw new IllegalArgumentException("SKD field rename requires non-empty fields");
        }
        String absent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        if (!"fail".equals(absent) && !"noop".equals(absent)) {
            throw new IllegalArgumentException("ifAbsent must be 'fail' or 'noop'");
        }
        String referencePolicy = patch.getReferencePolicy() == null
                ? "preserve" : patch.getReferencePolicy();
        if (!"preserve".equals(referencePolicy)) {
            throw new IllegalArgumentException(
                    "referencePolicy must be 'preserve'; use dedicated SKD operations for references");
        }

        List<Replacement> replacements = new ArrayList<>();
        Set<String> preservedReferenceNames = new HashSet<>();
        Set<String> sourceSelectors = new HashSet<>();
        Set<String> targetSelectors = new HashSet<>();
        int renamed = 0;
        for (SkdDataSetFieldRenameDsl.Field selector : patch.getFields()) {
            validateSelector(selector);
            String oldField = selector.getOldField() == null
                    ? selector.getOldDataPath() : selector.getOldField();
            String sourceKey = selector.getDataSet() + "\u0000" + selector.getOldDataPath()
                    + "\u0000" + oldField;
            if (!sourceSelectors.add(sourceKey)) {
                throw new IllegalArgumentException("Duplicate field rename selector in payload: "
                        + selector.getDataSet() + "/" + selector.getOldDataPath() + "/" + oldField);
            }
            String targetKey = selector.getDataSet() + "\u0000" + selector.getDataPath();
            if (!targetSelectors.add(targetKey)) {
                throw new IllegalArgumentException("Duplicate target dataPath in payload: "
                        + selector.getDataSet() + "/" + selector.getDataPath());
            }

            List<Element> dataSets = root.children.stream()
                    .filter(child -> "dataSet".equals(child.localName))
                    .filter(child -> selector.getDataSet().equals(directText(xml, child, "name"))).toList();
            if (dataSets.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one root dataSet named '"
                        + selector.getDataSet() + "', found " + dataSets.size());
            }
            if (dataSets.isEmpty()) {
                absent(absent, "Root dataSet not found: " + selector.getDataSet());
                continue;
            }
            Element dataSet = dataSets.get(0);
            String actualType = localType(attribute(dataSet.startTag, "type"));
            if (selector.getType() != null && !selector.getType().equals(actualType)) {
                throw new IllegalArgumentException("Root dataSet '" + selector.getDataSet()
                        + "' type mismatch: expected '" + selector.getType()
                        + "', found '" + Objects.toString(actualType, "") + "'");
            }

            List<Element> oldPathMatches = directFields(dataSet).stream()
                    .filter(field -> selector.getOldDataPath().equals(directText(xml, field, "dataPath")))
                    .toList();
            if (oldPathMatches.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one field declaration with dataPath '"
                        + selector.getOldDataPath() + "' in root dataSet '" + selector.getDataSet()
                        + "', found " + oldPathMatches.size());
            }
            if (oldPathMatches.isEmpty()) {
                if (isAlreadyApplied(xml, dataSet, selector)) {
                    continue;
                }
                absent(absent, "Field declaration not found: " + selector.getDataSet()
                        + "/" + selector.getOldDataPath() + "/" + oldField);
                continue;
            }
            Element target = oldPathMatches.get(0);
            requireFieldType(target, selector.getDataSet(), selector.getOldDataPath());
            String actualOldField = directText(xml, target, "field");
            if (!oldField.equals(actualOldField)) {
                throw new IllegalArgumentException("Field declaration old field mismatch at '"
                        + selector.getDataSet() + "/" + selector.getOldDataPath()
                        + "': expected '" + oldField + "', found '"
                        + Objects.toString(actualOldField, "") + "'");
            }

            List<Element> collisions = directFields(dataSet).stream()
                    .filter(field -> field != target)
                    .filter(field -> selector.getDataPath().equals(directText(xml, field, "dataPath")))
                    .toList();
            if (!collisions.isEmpty()) {
                throw new IllegalArgumentException("Target dataPath collision in root dataSet '"
                        + selector.getDataSet() + "': " + selector.getDataPath());
            }
            Element dataPathElement = requireDirectChild(target, "dataPath", selector.getDataSet());
            Element fieldElement = requireDirectChild(target, "field", selector.getDataSet());
            addTextReplacement(xml, replacements, dataPathElement, selector.getDataPath());
            addTextReplacement(xml, replacements, fieldElement, selector.getField());
            if (!selector.getOldDataPath().equals(selector.getDataPath())
                    || !oldField.equals(selector.getField())) {
                renamed++;
                preservedReferenceNames.add(selector.getOldDataPath());
            }
        }
        return new Plan(replacements, renamed, Set.copyOf(preservedReferenceNames));
    }

    private static boolean isAlreadyApplied(String xml, Element dataSet,
                                            SkdDataSetFieldRenameDsl.Field selector) {
        List<Element> matches = directFields(dataSet).stream()
                .filter(field -> selector.getDataPath().equals(directText(xml, field, "dataPath")))
                .toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Target dataPath collision in root dataSet '"
                    + selector.getDataSet() + "': " + selector.getDataPath());
        }
        if (matches.isEmpty()) return false;
        Element field = matches.get(0);
        requireFieldType(field, selector.getDataSet(), selector.getDataPath());
        String physicalField = directText(xml, field, "field");
        if (!selector.getField().equals(physicalField)) {
            throw new IllegalArgumentException("Target dataPath collision in root dataSet '"
                    + selector.getDataSet() + "': " + selector.getDataPath()
                    + " maps to field '" + Objects.toString(physicalField, "") + "'");
        }
        return true;
    }

    private static void validateSelector(SkdDataSetFieldRenameDsl.Field selector) {
        if (selector == null || blank(selector.getDataSet()) || blank(selector.getOldDataPath())
                || blank(selector.getDataPath()) || blank(selector.getField())) {
            throw new IllegalArgumentException(
                    "Every fields selector requires dataSet, oldDataPath, dataPath and field");
        }
        if (selector.getType() != null && blank(selector.getType())) {
            throw new IllegalArgumentException("fields type must be non-blank when specified");
        }
        if (selector.getOldField() != null && blank(selector.getOldField())) {
            throw new IllegalArgumentException("oldField must be non-blank when specified");
        }
    }

    private static void absent(String policy, String message) {
        if ("fail".equals(policy)) throw new IllegalArgumentException(message);
    }

    private static List<Element> directFields(Element dataSet) {
        return dataSet.children.stream().filter(child -> "field".equals(child.localName)).toList();
    }

    private static void requireFieldType(Element field, String dataSet, String dataPath) {
        String type = localType(attribute(field.startTag, "type"));
        if (!"DataSetFieldField".equals(type)) {
            throw new IllegalArgumentException("Field declaration type mismatch at '" + dataSet
                    + "/" + dataPath + "': expected 'DataSetFieldField', found '"
                    + Objects.toString(type, "") + "'");
        }
    }

    private static Element requireDirectChild(Element parent, String localName, String dataSet) {
        List<Element> matches = parent.children.stream()
                .filter(child -> localName.equals(child.localName)).toList();
        if (matches.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one direct " + localName
                    + " in field declaration of root dataSet '" + dataSet
                    + "', found " + matches.size());
        }
        return matches.get(0);
    }

    private static void addTextReplacement(String xml, List<Replacement> replacements,
                                           Element element, String newValue) {
        int start = element.startTagEnd + 1;
        int end = element.closeStart;
        while (start < end && Character.isWhitespace(xml.charAt(start))) start++;
        while (end > start && Character.isWhitespace(xml.charAt(end - 1))) end--;
        String escaped = escape(newValue);
        if (!xml.substring(start, end).equals(escaped)) {
            replacements.add(new Replacement(start, end, escaped));
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String attribute(String startTag, String localName) {
        int pos = 1;
        while (pos < startTag.length()) {
            while (pos < startTag.length() && !Character.isWhitespace(startTag.charAt(pos))) pos++;
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            int nameStart = pos;
            while (pos < startTag.length() && startTag.charAt(pos) != '='
                    && !Character.isWhitespace(startTag.charAt(pos)) && startTag.charAt(pos) != '>') pos++;
            String qName = startTag.substring(nameStart, pos);
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            if (qName.isEmpty() || pos >= startTag.length() || startTag.charAt(pos) != '=') {
                pos++;
                continue;
            }
            pos++;
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            if (pos >= startTag.length() || (startTag.charAt(pos) != '\'' && startTag.charAt(pos) != '"')) continue;
            char quote = startTag.charAt(pos++);
            int valueStart = pos;
            while (pos < startTag.length() && startTag.charAt(pos) != quote) pos++;
            String value = startTag.substring(valueStart, pos);
            if (localName.equals(localName(qName))) return unescape(value);
            pos++;
        }
        return null;
    }

    private static String localType(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static Element directChild(Element parent, String localName) {
        return parent.children.stream().filter(child -> localName.equals(child.localName))
                .findFirst().orElse(null);
    }

    private static String directText(String xml, Element parent, String localName) {
        Element child = directChild(parent, localName);
        if (child == null || child.closeStart < child.startTagEnd) return null;
        return unescape(xml.substring(child.startTagEnd + 1, child.closeStart).trim());
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static Element parse(String xml) {
        Deque<Element> stack = new ArrayDeque<>();
        Element root = null;
        for (int pos = 0; pos < xml.length();) {
            int lt = xml.indexOf('<', pos);
            if (lt < 0) break;
            if (xml.startsWith("<!--", lt)) { pos = requireEnd(xml, lt + 4, "-->") + 3; continue; }
            if (xml.startsWith("<![CDATA[", lt)) { pos = requireEnd(xml, lt + 9, "]]>") + 3; continue; }
            if (xml.startsWith("<?", lt)) { pos = requireEnd(xml, lt + 2, "?>") + 2; continue; }
            int gt = tagEnd(xml, lt + 1);
            if (xml.startsWith("<!", lt)) { pos = gt + 1; continue; }
            boolean closing = xml.startsWith("</", lt);
            boolean selfClosing = !closing && isSelfClosing(xml, lt, gt);
            String qName = readName(xml, lt + (closing ? 2 : 1));
            if (closing) {
                if (stack.isEmpty() || !stack.peek().qName.equals(qName)) {
                    throw new IllegalArgumentException("Malformed XML near closing tag " + qName);
                }
                Element element = stack.pop();
                element.closeStart = lt;
            } else {
                Element parent = stack.peek();
                Element element = new Element(qName, localName(qName), parent, gt,
                        xml.substring(lt, gt + 1));
                if (parent != null) parent.children.add(element);
                else if (root == null) root = element;
                else throw new IllegalArgumentException("Malformed XML: multiple roots");
                if (selfClosing) element.closeStart = gt;
                else stack.push(element);
            }
            pos = gt + 1;
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
            if (quote != 0) { if (current == quote) quote = 0; }
            else if (current == '\'' || current == '"') quote = current;
            else if (current == '>') return index;
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
        int pos = gt - 1;
        while (pos > lt && Character.isWhitespace(xml.charAt(pos))) pos--;
        return xml.charAt(pos) == '/';
    }

    private static String localName(String qName) {
        int colon = qName.indexOf(':');
        return colon < 0 ? qName : qName.substring(colon + 1);
    }

    private static final class Element {
        final String qName;
        final String localName;
        final Element parent;
        final int startTagEnd;
        final String startTag;
        final List<Element> children = new ArrayList<>();
        int closeStart;

        Element(String qName, String localName, Element parent, int startTagEnd, String startTag) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
        }
    }

    private record Replacement(int start, int end, String value) {
    }

    private record Plan(List<Replacement> replacements, int renamed,
                        Set<String> preservedReferenceNames) {
    }
}
//--agent TASK-174

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdRemoveComponentsDsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

//++agent TASK-174 [10.07.2026 23:24:00]
/**
 * Lossless-удаление structure item и group-template binding по полной идентичности.
 * Вся партия разрешается до первой замены, поэтому ошибка не создаёт частичный результат.
 */
public final class SkdRemoveComponentsEditor {

    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "Header", "OverallHeader", "GroupHeader", "Footer", "OverallFooter");

    public record Result(String content, boolean changed, int removed) {
    }

    public Result apply(String original, SkdRemoveComponentsDsl patch) {
        Element root = parse(original);
        Preflight plan = preflight(original, root, patch);
        List<Element> targets = plan.targets();
        targets.sort((left, right) -> Integer.compare(right.lineStart, left.lineStart));
        String result = original;
        for (Element target : targets) {
            result = result.substring(0, target.lineStart) + result.substring(target.endWithLineBreak);
        }
        Element resultRoot = parse(result);
        validateNoDanglingReferences(result, resultRoot, plan.removedDataSets());
        return new Result(result, !result.equals(original), targets.size());
    }

    private static Preflight preflight(String xml, Element root, SkdRemoveComponentsDsl patch) {
        if (patch == null || !"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException("Expected non-empty SKD remove payload and root DataCompositionSchema");
        }
        boolean noStructures = patch.getStructureItems() == null || patch.getStructureItems().isEmpty();
        boolean noBindings = patch.getGroupTemplates() == null || patch.getGroupTemplates().isEmpty();
        boolean noLinks = patch.getDataSetLinks() == null || patch.getDataSetLinks().isEmpty();
        boolean noDataSets = patch.getDataSets() == null || patch.getDataSets().isEmpty();
        boolean noDataSetFields = patch.getDataSetFields() == null || patch.getDataSetFields().isEmpty();
        if (noStructures && noBindings && noLinks && noDataSets && noDataSetFields) {
            throw new IllegalArgumentException(
                    "SKD remove requires structureItems, groupTemplates, dataSetLinks, dataSets or dataSetFields");
        }
        String absent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        if (!"fail".equals(absent) && !"noop".equals(absent)) {
            throw new IllegalArgumentException("ifAbsent must be 'fail' or 'noop'");
        }

        List<Element> targets = new ArrayList<>();
        List<String> removedDataSets = new ArrayList<>();
        Set<Element> uniqueTargets = new HashSet<>();
        if (!noStructures) {
            Set<String> selectors = new HashSet<>();
            for (SkdRemoveComponentsDsl.StructureItem selector : patch.getStructureItems()) {
                validateStructureSelector(selector);
                String key = selector.getVariant() + "\u0000" + String.join("\u0000", selector.getPath());
                if (!selectors.add(key)) {
                    throw new IllegalArgumentException("Duplicate structure selector in payload: " + key);
                }
                List<Element> variants = root.children.stream()
                        .filter(child -> "settingsVariant".equals(child.localName))
                        .filter(child -> selector.getVariant().equals(directText(xml, child, "name"))).toList();
                if (variants.size() != 1) {
                    throw new IllegalArgumentException("Expected exactly one settingsVariant named '"
                            + selector.getVariant() + "', found " + variants.size());
                }
                Element settings = directChild(variants.get(0), "settings");
                if (settings == null) {
                    throw new IllegalArgumentException("settingsVariant '" + selector.getVariant() + "' has no settings");
                }
                Element parent = settings;
                Element target = null;
                for (String segment : selector.getPath()) {
                    List<Element> matches = parent.children.stream().filter(SkdRemoveComponentsEditor::isStructureItem)
                            .filter(child -> segment.equals(directText(xml, child, "name"))).toList();
                    if (matches.size() > 1) {
                        throw new IllegalArgumentException("Ambiguous structure path in variant '"
                                + selector.getVariant() + "': " + String.join("/", selector.getPath()));
                    }
                    if (matches.isEmpty()) {
                        target = null;
                        break;
                    }
                    target = matches.get(0);
                    parent = target;
                }
                if (target == null) {
                    absent(absent, "Structure target not found: " + selector.getVariant() + "/"
                            + String.join("/", selector.getPath()));
                    continue;
                }
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException("Overlapping or duplicate structure removal target: " + key);
                }
                targets.add(target);
            }
        }

        if (!noBindings) {
            Set<BindingKey> selectors = new HashSet<>();
            for (SkdRemoveComponentsDsl.GroupTemplate selector : patch.getGroupTemplates()) {
                BindingKey wanted = validateBindingSelector(selector);
                if (!selectors.add(wanted)) {
                    throw new IllegalArgumentException("Duplicate group template selector in payload: " + wanted);
                }
                List<Element> matches = root.children.stream()
                        .filter(child -> "groupTemplate".equals(child.localName)
                                || "groupHeaderTemplate".equals(child.localName))
                        .filter(child -> wanted.equals(BindingKey.fromXml(xml, child))).toList();
                if (matches.size() > 1) {
                    throw new IllegalArgumentException("Ambiguous group template binding: " + wanted);
                }
                if (matches.isEmpty()) {
                    absent(absent, "Group template binding not found: " + wanted);
                    continue;
                }
                Element target = matches.get(0);
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException("Duplicate removal target: " + wanted);
                }
                targets.add(target);
            }
        }
        if (!noLinks) {
            Set<DataSetLinkKey> selectors = new HashSet<>();
            for (SkdRemoveComponentsDsl.DataSetLink selector : patch.getDataSetLinks()) {
                DataSetLinkKey wanted = validateDataSetLinkSelector(selector);
                if (!selectors.add(wanted)) {
                    throw new IllegalArgumentException("Duplicate dataSetLink selector in payload: " + wanted);
                }
                List<Element> matches = root.children.stream()
                        .filter(child -> "dataSetLink".equals(child.localName))
                        .filter(child -> wanted.matches(DataSetLinkKey.fromXml(xml, child))).toList();
                if (matches.size() > 1) {
                    throw new IllegalArgumentException("Ambiguous dataSetLink mapping: " + wanted
                            + "; specify destinationExpression");
                }
                if (matches.isEmpty()) {
                    absent(absent, "dataSetLink mapping not found: " + wanted);
                    continue;
                }
                Element target = matches.get(0);
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException("Duplicate removal target: " + wanted);
                }
                targets.add(target);
            }
        }
        if (!noDataSets) {
            Set<String> selectors = new HashSet<>();
            for (SkdRemoveComponentsDsl.DataSet selector : patch.getDataSets()) {
                validateDataSetSelector(selector);
                if (!selectors.add(selector.getName())) {
                    throw new IllegalArgumentException(
                            "Duplicate dataSet selector in payload: " + selector.getName());
                }
                List<Element> matches = root.children.stream()
                        .filter(child -> "dataSet".equals(child.localName))
                        .filter(child -> selector.getName().equals(directText(xml, child, "name"))).toList();
                if (matches.size() > 1) {
                    throw new IllegalArgumentException("Expected exactly one root dataSet named '"
                            + selector.getName() + "', found " + matches.size());
                }
                if (matches.isEmpty()) {
                    absent(absent, "Root dataSet not found: " + selector.getName());
                    continue;
                }
                Element target = matches.get(0);
                String actualType = localType(attribute(target.startTag, "type"));
                if (selector.getType() != null && !selector.getType().equals(actualType)) {
                    throw new IllegalArgumentException("Root dataSet '" + selector.getName()
                            + "' type mismatch: expected '" + selector.getType()
                            + "', found '" + Objects.toString(actualType, "") + "'");
                }
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException(
                            "Duplicate removal target: root dataSet " + selector.getName());
                }
                targets.add(target);
                removedDataSets.add(selector.getName());
            }
        }
        if (!noDataSetFields) {
            Set<String> selectors = new HashSet<>();
            for (SkdRemoveComponentsDsl.DataSetField selector : patch.getDataSetFields()) {
                validateDataSetFieldSelector(selector);
                String key = selector.getDataSet() + "\u0000" + selector.getDataPath();
                if (!selectors.add(key)) {
                    throw new IllegalArgumentException(
                            "Duplicate dataSetFields selector in payload: " + selector.getDataSet()
                                    + "/" + selector.getDataPath());
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
                List<Element> fields = dataSet.children.stream()
                        .filter(child -> "field".equals(child.localName))
                        .filter(child -> selector.getDataPath().equals(directText(xml, child, "dataPath"))).toList();
                if (fields.size() > 1) {
                    throw new IllegalArgumentException("Expected exactly one field declaration with dataPath '"
                            + selector.getDataPath() + "' in root dataSet '" + selector.getDataSet()
                            + "', found " + fields.size());
                }
                if (fields.isEmpty()) {
                    absent(absent, "Field declaration not found: " + selector.getDataSet()
                            + "/" + selector.getDataPath());
                    continue;
                }
                Element target = fields.get(0);
                if (!uniqueTargets.add(target)) {
                    throw new IllegalArgumentException("Duplicate removal target: field declaration "
                            + selector.getDataSet() + "/" + selector.getDataPath());
                }
                targets.add(target);
            }
        }
        for (int left = 0; left < targets.size(); left++) {
            for (int right = left + 1; right < targets.size(); right++) {
                if (isAncestor(targets.get(left), targets.get(right))
                        || isAncestor(targets.get(right), targets.get(left))) {
                    throw new IllegalArgumentException("Overlapping removal targets are not allowed");
                }
            }
        }
        Set<Element> settingsParents = new HashSet<>();
        for (Element target : targets) {
            if (target.parent != null && "settings".equals(target.parent.localName)) {
                settingsParents.add(target.parent);
            }
        }
        for (Element settings : settingsParents) {
            long directCount = settings.children.stream().filter(SkdRemoveComponentsEditor::isStructureItem).count();
            long removedCount = targets.stream().filter(target -> target.parent == settings).count();
            if (directCount - removedCount < 1) {
                throw new IllegalArgumentException("Cannot remove the last top-level structure item from a settingsVariant");
            }
        }
        return new Preflight(targets, removedDataSets);
    }

    private static void validateDataSetSelector(SkdRemoveComponentsDsl.DataSet selector) {
        if (selector == null || blank(selector.getName())) {
            throw new IllegalArgumentException("Every dataSets selector requires name");
        }
        if (selector.getType() != null && blank(selector.getType())) {
            throw new IllegalArgumentException("dataSets type must be non-blank when specified");
        }
    }

    private static void validateDataSetFieldSelector(SkdRemoveComponentsDsl.DataSetField selector) {
        if (selector == null || blank(selector.getDataSet()) || blank(selector.getDataPath())) {
            throw new IllegalArgumentException("Every dataSetFields selector requires dataSet and dataPath");
        }
        if (selector.getType() != null && blank(selector.getType())) {
            throw new IllegalArgumentException("dataSetFields type must be non-blank when specified");
        }
    }

    /**
     * Проверяем уже спланированный результат, чтобы ссылки, удаляемые тем же batch,
     * не считались висячими, а любая оставшаяся ссылка блокировала всю операцию.
     */
    private static void validateNoDanglingReferences(String xml, Element root, List<String> dataSetNames) {
        for (String dataSetName : dataSetNames) {
            Reference reference = findReference(xml, root, dataSetName);
            if (reference != null) {
                throw new IllegalArgumentException("Dangling reference to removed root dataSet '"
                        + dataSetName + "' remains at " + reference.path()
                        + " (line " + reference.line() + ")");
            }
        }
    }

    private static Reference findReference(String xml, Element element, String name) {
        if (containsIdentifier(element.startTag, name)) {
            return reference(xml, element);
        }
        int textStart = element.startTagEnd + 1;
        for (Element child : element.children) {
            if (containsIdentifier(xml.substring(textStart, child.tagStart), name)) {
                return reference(xml, element);
            }
            Reference nested = findReference(xml, child, name);
            if (nested != null) return nested;
            textStart = child.endWithLineBreak;
        }
        if (textStart <= element.closeStart
                && containsIdentifier(xml.substring(textStart, element.closeStart), name)) {
            return reference(xml, element);
        }
        return null;
    }

    private static Reference reference(String xml, Element element) {
        return new Reference(path(element), 1 + countBefore(xml, element.tagStart, '\n'));
    }

    private static String path(Element element) {
        Deque<String> segments = new ArrayDeque<>();
        for (Element current = element; current != null; current = current.parent) {
            segments.push(current.qName);
        }
        return "/" + String.join("/", segments);
    }

    private static int countBefore(String value, int end, char token) {
        int count = 0;
        for (int index = 0; index < end; index++) {
            if (value.charAt(index) == token) count++;
        }
        return count;
    }

    private static boolean containsIdentifier(String value, String identifier) {
        for (int at = value.indexOf(identifier); at >= 0; at = value.indexOf(identifier, at + 1)) {
            int after = at + identifier.length();
            boolean leftBoundary = at == 0 || !identifierChar(value.charAt(at - 1));
            boolean rightBoundary = after == value.length() || !identifierChar(value.charAt(after));
            if (leftBoundary && rightBoundary) return true;
        }
        return false;
    }

    private static boolean identifierChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
    }

    private static String attribute(String startTag, String localName) {
        int position = 1;
        while (position < startTag.length()) {
            while (position < startTag.length() && Character.isWhitespace(startTag.charAt(position))) position++;
            int nameStart = position;
            while (position < startTag.length()) {
                char current = startTag.charAt(position);
                if (Character.isWhitespace(current) || current == '=' || current == '>' || current == '/') break;
                position++;
            }
            String qName = startTag.substring(nameStart, position);
            if (qName.isEmpty()) break;
            while (position < startTag.length() && Character.isWhitespace(startTag.charAt(position))) position++;
            if (position >= startTag.length() || startTag.charAt(position) != '=') {
                if (position == nameStart) position++;
                continue;
            }
            position++;
            while (position < startTag.length() && Character.isWhitespace(startTag.charAt(position))) position++;
            if (position >= startTag.length() || (startTag.charAt(position) != '\'' && startTag.charAt(position) != '"')) {
                continue;
            }
            char quote = startTag.charAt(position++);
            int valueStart = position;
            while (position < startTag.length() && startTag.charAt(position) != quote) position++;
            String value = startTag.substring(valueStart, Math.min(position, startTag.length()));
            if (localName.equals(localName(qName))) return unescape(value);
            if (position < startTag.length()) position++;
        }
        return null;
    }

    private static String localType(String value) {
        if (value == null) return null;
        int colon = value.indexOf(':');
        return colon < 0 ? value : value.substring(colon + 1);
    }

    private static void validateStructureSelector(SkdRemoveComponentsDsl.StructureItem selector) {
        if (selector == null || blank(selector.getVariant()) || selector.getPath() == null
                || selector.getPath().isEmpty() || selector.getPath().stream().anyMatch(SkdRemoveComponentsEditor::blank)) {
            throw new IllegalArgumentException("Every structureItems selector requires variant and non-empty path[]");
        }
    }

    private static BindingKey validateBindingSelector(SkdRemoveComponentsDsl.GroupTemplate selector) {
        if (selector == null || blank(selector.getTemplateType()) || blank(selector.getTemplate())) {
            throw new IllegalArgumentException("Every groupTemplates selector requires templateType and template");
        }
        if (!TEMPLATE_TYPES.contains(selector.getTemplateType())) {
            throw new IllegalArgumentException("Unknown SKD templateType '" + selector.getTemplateType() + "'");
        }
        if (blank(selector.getGroupName()) && blank(selector.getGroupField())) {
            throw new IllegalArgumentException("Every groupTemplates selector requires groupName or groupField");
        }
        return new BindingKey(emptyToNull(selector.getGroupName()), emptyToNull(selector.getGroupField()),
                selector.getTemplateType(), selector.getTemplate());
    }

    private static DataSetLinkKey validateDataSetLinkSelector(SkdRemoveComponentsDsl.DataSetLink selector) {
        if (selector == null || blank(selector.getSourceDataSet()) || blank(selector.getDestinationDataSet())
                || blank(selector.getSourceExpression())) {
            throw new IllegalArgumentException("Every dataSetLinks selector requires sourceDataSet, "
                    + "destinationDataSet and sourceExpression");
        }
        if (selector.getParameter() != null && blank(selector.getParameter())) {
            throw new IllegalArgumentException("dataSetLinks parameter must be non-blank when specified");
        }
        if (selector.getDestinationExpression() != null && blank(selector.getDestinationExpression())) {
            throw new IllegalArgumentException(
                    "dataSetLinks destinationExpression must be non-blank when specified");
        }
        return new DataSetLinkKey(selector.getSourceDataSet(), selector.getDestinationDataSet(),
                selector.getSourceExpression(), selector.getParameter(), selector.getDestinationExpression());
    }

    private static void absent(String mode, String message) {
        if ("fail".equals(mode)) throw new IllegalArgumentException(message);
    }

    private static boolean isAncestor(Element ancestor, Element element) {
        for (Element current = element.parent; current != null; current = current.parent) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private static boolean isStructureItem(Element element) {
        return "item".equals(element.localName) && element.startTag.contains("xsi:type")
                && element.startTag.contains("StructureItem");
    }

    private static Element directChild(Element parent, String localName) {
        return parent.children.stream().filter(child -> localName.equals(child.localName)).findFirst().orElse(null);
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

    private static String emptyToNull(String value) {
        return blank(value) ? null : value;
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
                element.endWithLineBreak = includeFollowingLineBreak(xml, gt + 1);
            } else {
                Element parent = stack.peek();
                Element element = new Element(qName, localName(qName), parent, lt, lineStart(xml, lt), gt,
                        xml.substring(lt, gt + 1));
                if (parent != null) parent.children.add(element);
                else if (root == null) root = element;
                else throw new IllegalArgumentException("Malformed XML: multiple roots");
                if (selfClosing) {
                    element.closeStart = gt;
                    element.endWithLineBreak = includeFollowingLineBreak(xml, gt + 1);
                } else stack.push(element);
            }
            pos = gt + 1;
        }
        if (root == null || !stack.isEmpty()) throw new IllegalArgumentException("Malformed XML: unbalanced document");
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
        while (end < xml.length()) {
            char c = xml.charAt(end);
            if (Character.isWhitespace(c) || c == '>' || c == '/') break;
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

    private static int lineStart(String xml, int position) {
        int lf = xml.lastIndexOf('\n', Math.max(0, position - 1));
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

        Element(String qName, String localName, Element parent, int tagStart, int lineStart,
                int startTagEnd, String startTag) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.tagStart = tagStart;
            this.lineStart = lineStart;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
        }
    }

    private record Preflight(List<Element> targets, List<String> removedDataSets) {
    }

    private record Reference(String path, int line) {
    }

    private record BindingKey(String groupName, String groupField, String templateType, String template) {
        static BindingKey fromXml(String xml, Element element) {
            String type = "groupHeaderTemplate".equals(element.localName)
                    ? "GroupHeader" : directText(xml, element, "templateType");
            return new BindingKey(emptyToNull(directText(xml, element, "groupName")),
                    emptyToNull(directText(xml, element, "groupField")), type,
                    directText(xml, element, "template"));
        }

        @Override
        public String toString() {
            return "(" + Objects.toString(groupName, "") + "," + Objects.toString(groupField, "")
                    + "," + templateType + "," + template + ")";
        }
    }

    private record DataSetLinkKey(String sourceDataSet, String destinationDataSet,
                                  String sourceExpression, String parameter,
                                  String destinationExpression) {
        static DataSetLinkKey fromXml(String xml, Element element) {
            return new DataSetLinkKey(directText(xml, element, "sourceDataSet"),
                    directText(xml, element, "destinationDataSet"),
                    directText(xml, element, "sourceExpression"),
                    emptyToNull(directText(xml, element, "parameter")),
                    directText(xml, element, "destinationExpression"));
        }

        boolean matches(DataSetLinkKey existing) {
            return Objects.equals(sourceDataSet, existing.sourceDataSet)
                    && Objects.equals(destinationDataSet, existing.destinationDataSet)
                    && Objects.equals(sourceExpression, existing.sourceExpression)
                    && Objects.equals(parameter, existing.parameter)
                    && (destinationExpression == null
                    || Objects.equals(destinationExpression, existing.destinationExpression));
        }

        @Override
        public String toString() {
            return "(" + sourceDataSet + "," + destinationDataSet + "," + sourceExpression + ","
                    + Objects.toString(parameter, "") + "," + Objects.toString(destinationExpression, "*") + ")";
        }
    }
}
//--agent TASK-174

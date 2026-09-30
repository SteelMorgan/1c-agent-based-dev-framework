package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdTemplatePartsPatchDsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

//++agent TASK-174 [11.07.2026 11:00:00]
/**
 * Меняет только default/flags корневых параметров, expression AreaTemplate-параметров
 * и точные group bindings.
 * Внутренний AreaTemplate с rows/cells/appearance не попадает в writer и сохраняется побайтово.
 */
public final class SkdTemplatePartsPatchEditor {

    private static final Set<String> TEMPLATE_TYPES = Set.of(
            "Header", "OverallHeader", "GroupHeader", "Footer", "OverallFooter");

    public record Result(String content, boolean changed, int parameterChanges,
                         int expressionChanges, int bindingChanges) {
    }

    public Result apply(String original, SkdTemplatePartsPatchDsl patch) {
        Element root = parse(original);
        if (patch == null || !"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException(
                    "Expected non-empty SKD template parts payload and root DataCompositionSchema");
        }
        boolean noParameters = patch.getParameters() == null || patch.getParameters().isEmpty();
        boolean noExpressions = patch.getParameterExpressions() == null
                || patch.getParameterExpressions().isEmpty();
        boolean noBindings = patch.getGroupBindings() == null || patch.getGroupBindings().isEmpty();
        if (noParameters && noExpressions && noBindings) {
            throw new IllegalArgumentException(
                    "SKD template parts patch requires parameters, parameterExpressions or groupBindings");
        }
        String absent = patch.getIfAbsent() == null ? "fail" : patch.getIfAbsent();
        if (!"fail".equals(absent) && !"noop".equals(absent)) {
            throw new IllegalArgumentException("ifAbsent must be 'fail' or 'noop'");
        }

        List<Replacement> replacements = new ArrayList<>();
        int parameterChanges = planParameters(original, root, patch, absent, replacements);
        int expressionChanges = planExpressions(original, root, patch, absent, replacements);
        BindingPlan bindingPlan = planBindings(original, root, patch, absent, replacements);

        replacements.sort(Comparator.comparingInt(Replacement::start).reversed());
        String result = original;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start()) + replacement.value()
                    + result.substring(replacement.end());
        }
        for (BindingValue binding : bindingPlan.insertions()) {
            result = insertBinding(result, binding);
        }

        Element finalRoot = parse(result);
        validateFinalReferences(result, finalRoot);
        return new Result(result, !result.equals(original), parameterChanges,
                expressionChanges, bindingPlan.changed());
    }

    //++agent TASK-174 [12.07.2026 03:00:00]
    /**
     * Планирует только замены внутри exact root parameter. Все селекторы проверяются
     * до применения первого span, поэтому смешанный batch остаётся атомарным.
     */
    private static int planParameters(String xml, Element root,
                                      SkdTemplatePartsPatchDsl patch, String absent,
                                      List<Replacement> replacements) {
        if (patch.getParameters() == null) return 0;
        Set<String> selectors = new HashSet<>();
        int changed = 0;
        for (SkdTemplatePartsPatchDsl.RootParameter selector : patch.getParameters()) {
            validateRootParameterSelector(selector);
            if (!selectors.add(selector.getName())) {
                throw new IllegalArgumentException(
                        "Duplicate root parameter selector in payload: " + selector.getName());
            }

            List<Element> parameters = directChildren(root, "parameter").stream()
                    .filter(parameter -> selector.getName().equals(
                            directText(xml, parameter, "name"))).toList();
            if (parameters.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one SKD root parameter named '"
                        + selector.getName() + "', found " + parameters.size());
            }
            if (parameters.isEmpty()) {
                absent(absent, "SKD root parameter not found: " + selector.getName());
                continue;
            }

            Element parameter = parameters.get(0);
            Element value = requireDirectChild(parameter, "value",
                    "SKD root parameter '" + selector.getName() + "'");
            Element useRestriction = requireDirectChild(parameter, "useRestriction",
                    "SKD root parameter '" + selector.getName() + "'");
            List<Element> available = directChildren(parameter, "availableAsField");
            if (available.size() > 1) {
                throw new IllegalArgumentException("Expected at most one direct availableAsField in "
                        + "SKD root parameter '" + selector.getName() + "', found "
                        + available.size());
            }

            int before = replacements.size();
            String canonicalValue = "<" + value.qName + " xsi:type=\""
                    + escape(selector.getXsiType()) + "\">" + escape(selector.getValue())
                    + "</" + value.qName + ">";
            if (!xml.substring(value.start, value.end).equals(canonicalValue)) {
                replacements.add(new Replacement(value.start, value.end, canonicalValue));
            }
            addDirectTextReplacement(xml, replacements, parameter, "useRestriction",
                    selector.getUseRestriction().toString());
            if (available.isEmpty()) {
                String qName = qualifiedSiblingName(useRestriction.qName, "availableAsField");
                replacements.add(new Replacement(useRestriction.end, useRestriction.end,
                        lineBreakAndIndentAfter(xml, useRestriction) + "<" + qName + ">"
                                + selector.getAvailableAsField() + "</" + qName + ">"));
            } else {
                addDirectTextReplacement(xml, replacements, parameter, "availableAsField",
                        selector.getAvailableAsField().toString());
            }
            if (replacements.size() > before) changed++;
        }
        return changed;
    }

    private static void validateRootParameterSelector(
            SkdTemplatePartsPatchDsl.RootParameter selector) {
        if (selector == null || blank(selector.getName()) || blank(selector.getValue())
                || blank(selector.getXsiType()) || selector.getUseRestriction() == null
                || selector.getAvailableAsField() == null) {
            throw new IllegalArgumentException("Every parameters item requires name, value, xsiType, "
                    + "useRestriction and availableAsField");
        }
        String xsiType = selector.getXsiType();
        int colon = xsiType.indexOf(':');
        if (colon <= 0 || colon != xsiType.lastIndexOf(':') || colon == xsiType.length() - 1
                || xsiType.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Root parameter xsiType must be a qualified XML name: "
                    + xsiType);
        }
    }
    //--agent TASK-174

    private static int planExpressions(String xml, Element root,
                                       SkdTemplatePartsPatchDsl patch, String absent,
                                       List<Replacement> replacements) {
        if (patch.getParameterExpressions() == null) return 0;
        Set<String> selectors = new HashSet<>();
        int changed = 0;
        for (SkdTemplatePartsPatchDsl.ParameterExpression selector
                : patch.getParameterExpressions()) {
            if (selector == null || blank(selector.getTemplate())
                    || blank(selector.getParameter()) || blank(selector.getExpression())) {
                throw new IllegalArgumentException(
                        "Every parameterExpressions item requires template, parameter and non-empty expression");
            }
            String key = selector.getTemplate() + "\u0000" + selector.getParameter();
            if (!selectors.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate template parameter selector in payload: "
                                + selector.getTemplate() + "/" + selector.getParameter());
            }

            List<Element> templates = directChildren(root, "template").stream()
                    .filter(template -> selector.getTemplate().equals(
                            directText(xml, template, "name"))).toList();
            if (templates.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one AreaTemplate named '"
                        + selector.getTemplate() + "', found " + templates.size());
            }
            if (templates.isEmpty()) {
                absent(absent, "AreaTemplate not found: " + selector.getTemplate());
                continue;
            }
            Element template = templates.get(0);
            List<Element> areaBodies = directChildren(template, "template").stream()
                    .filter(body -> "AreaTemplate".equals(localType(
                            attribute(body.startTag, "type")))).toList();
            if (areaBodies.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one AreaTemplate body in '"
                        + selector.getTemplate() + "', found " + areaBodies.size());
            }

            List<Element> parameters = directChildren(template, "parameter").stream()
                    .filter(parameter -> selector.getParameter().equals(
                            directText(xml, parameter, "name"))).toList();
            if (parameters.size() > 1) {
                throw new IllegalArgumentException(
                        "Expected exactly one AreaTemplate parameter '" + selector.getParameter()
                                + "' in '" + selector.getTemplate() + "', found "
                                + parameters.size());
            }
            if (parameters.isEmpty()) {
                absent(absent, "AreaTemplate parameter not found: " + selector.getTemplate()
                        + "/" + selector.getParameter());
                continue;
            }
            Element parameter = parameters.get(0);
            String parameterType = localType(attribute(parameter.startTag, "type"));
            if (!"ExpressionAreaTemplateParameter".equals(parameterType)) {
                throw new IllegalArgumentException("AreaTemplate parameter type mismatch at '"
                        + selector.getTemplate() + "/" + selector.getParameter()
                        + "': expected ExpressionAreaTemplateParameter, found '"
                        + Objects.toString(parameterType, "") + "'");
            }
            List<Element> expressions = directChildren(parameter, "expression");
            if (expressions.size() > 1) {
                throw new IllegalArgumentException("Expected at most one expression in AreaTemplate parameter '"
                        + selector.getTemplate() + "/" + selector.getParameter()
                        + "', found " + expressions.size());
            }
            String escaped = escape(selector.getExpression());
            if (expressions.isEmpty()) {
                Element name = requireDirectChild(parameter, "name",
                        selector.getTemplate() + "/" + selector.getParameter());
                String qName = qualifiedSiblingName(name.qName, "expression");
                String prefix = lineBreakAndIndentAfter(xml, name);
                replacements.add(new Replacement(name.end, name.end,
                        prefix + "<" + qName + ">" + escaped + "</" + qName + ">"));
                changed++;
            } else {
                Element expression = expressions.get(0);
                if (expression.selfClosing) {
                    String qName = expression.qName;
                    replacements.add(new Replacement(expression.start, expression.end,
                            "<" + qName + ">" + escaped + "</" + qName + ">"));
                    changed++;
                } else {
                    int start = expression.startTagEnd + 1;
                    int end = expression.closeStart;
                    while (start < end && Character.isWhitespace(xml.charAt(start))) start++;
                    while (end > start && Character.isWhitespace(xml.charAt(end - 1))) end--;
                    if (!xml.substring(start, end).equals(escaped)) {
                        replacements.add(new Replacement(start, end, escaped));
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static BindingPlan planBindings(String xml, Element root,
                                            SkdTemplatePartsPatchDsl patch, String absent,
                                            List<Replacement> replacements) {
        List<BindingState> states = new ArrayList<>();
        for (Element child : root.children) {
            if ("groupTemplate".equals(child.localName)
                    || "groupHeaderTemplate".equals(child.localName)) {
                BindingValue value = readBinding(xml, child);
                requireCanonicalBindingTag(child, value);
                states.add(new BindingState(child, value));
            }
        }
        if (patch.getGroupBindings() == null) return new BindingPlan(List.of(), 0);

        Set<String> sourceSelectors = new HashSet<>();
        List<SkdTemplatePartsPatchDsl.GroupBinding> upserts = new ArrayList<>();
        int changed = 0;

        // Сначала снимаются все source bindings. Поэтому remove+upsert одного identity
        // проверяется по конечному набору, а не падает на временной коллизии.
        for (SkdTemplatePartsPatchDsl.GroupBinding operation : patch.getGroupBindings()) {
            validateBindingOperation(operation);
            String action = operation.getAction().toLowerCase();
            if ("upsert".equals(action)) {
                upserts.add(operation);
                continue;
            }
            BindingValue source = currentBinding(operation);
            String sourceKey = source.tripleKey();
            if (!sourceSelectors.add(sourceKey)) {
                throw new IllegalArgumentException("Duplicate group binding selector in payload: " + source);
            }
            List<BindingState> matches = states.stream()
                    .filter(state -> !state.removed && source.equals(state.current)).toList();
            if (matches.size() > 1) {
                throw new IllegalArgumentException("Expected exactly one SKD group binding '"
                        + source + "', found " + matches.size());
            }
            if (matches.isEmpty()) {
                if ("replace".equals(action)) {
                    BindingValue target = replacementBinding(operation);
                    List<BindingState> alreadyApplied = states.stream()
                            .filter(state -> !state.removed)
                            .filter(state -> target.equals(state.finalValue())).toList();
                    if (alreadyApplied.size() > 1) {
                        throw new IllegalArgumentException("SKD group binding collision: " + target);
                    }
                    if (alreadyApplied.size() == 1) continue;
                }
                absent(absent, "SKD group binding not found: " + source);
                continue;
            }
            BindingState state = matches.get(0);
            if ("remove".equals(action)) {
                state.removed = true;
                changed++;
            } else {
                BindingValue target = replacementBinding(operation);
                state.target = target;
                if (!source.equals(target)) changed++;
            }
        }

        for (SkdTemplatePartsPatchDsl.GroupBinding operation : upserts) {
            BindingValue target = currentBinding(operation);
            List<BindingState> exact = states.stream()
                    .filter(state -> !state.removed)
                    .filter(state -> target.equals(state.finalValue())).toList();
            if (exact.size() > 1) {
                throw new IllegalArgumentException("SKD group binding collision: " + target);
            }
            if (exact.isEmpty()) {
                states.add(new BindingState(null, target));
                changed++;
            }
        }

        Map<String, BindingValue> identities = new HashMap<>();
        for (BindingState state : states) {
            if (state.removed) continue;
            BindingValue value = state.finalValue();
            BindingValue previous = identities.putIfAbsent(value.identityKey(), value);
            if (previous != null) {
                throw new IllegalArgumentException("SKD group binding collision for group/templateType: "
                        + value.groupName() + "/" + value.templateType());
            }
        }
        validateReferences(xml, root, states);

        List<BindingValue> insertions = new ArrayList<>();
        for (BindingState state : states) {
            if (state.element == null) {
                insertions.add(state.finalValue());
            } else if (state.removed) {
                replacements.add(new Replacement(bindingSpanStart(xml, state.element),
                        bindingSpanEnd(xml, state.element), ""));
            } else if (state.target != null && !state.current.equals(state.target)) {
                if (bindingTag(state.current).equals(bindingTag(state.target))) {
                    addDirectTextReplacement(xml, replacements, state.element, "groupName",
                            state.target.groupName());
                    addDirectTextReplacement(xml, replacements, state.element, "templateType",
                            state.target.templateType());
                    addDirectTextReplacement(xml, replacements, state.element, "template",
                            state.target.template());
                } else {
                    replacements.add(new Replacement(bindingSpanStart(xml, state.element),
                            bindingSpanEnd(xml, state.element), ""));
                    insertions.add(state.target);
                }
            }
        }
        return new BindingPlan(List.copyOf(insertions), changed);
    }

    private static void validateBindingOperation(SkdTemplatePartsPatchDsl.GroupBinding operation) {
        if (operation == null || blank(operation.getAction()) || blank(operation.getGroupName())
                || blank(operation.getTemplateType()) || blank(operation.getTemplate())) {
            throw new IllegalArgumentException(
                    "Every groupBindings item requires action, groupName, templateType and template");
        }
        String action = operation.getAction().toLowerCase();
        if (!Set.of("replace", "remove", "upsert").contains(action)) {
            throw new IllegalArgumentException(
                    "groupBindings action must be replace, remove or upsert");
        }
        validateTemplateType(operation.getTemplateType());
        if ("replace".equals(action)) {
            if (blank(operation.getNewTemplateType()) || blank(operation.getNewTemplate())) {
                throw new IllegalArgumentException(
                        "replace group binding requires newTemplateType and newTemplate");
            }
            validateTemplateType(operation.getNewTemplateType());
        }
    }

    private static BindingValue currentBinding(SkdTemplatePartsPatchDsl.GroupBinding operation) {
        return new BindingValue(operation.getGroupName(), operation.getTemplateType(),
                operation.getTemplate());
    }

    private static BindingValue replacementBinding(SkdTemplatePartsPatchDsl.GroupBinding operation) {
        String group = blank(operation.getNewGroupName())
                ? operation.getGroupName() : operation.getNewGroupName();
        return new BindingValue(group, operation.getNewTemplateType(), operation.getNewTemplate());
    }

    private static void validateTemplateType(String templateType) {
        if (!TEMPLATE_TYPES.contains(templateType)) {
            throw new IllegalArgumentException("Unknown SKD group templateType '" + templateType
                    + "'. Expected one of: " + TEMPLATE_TYPES);
        }
    }

    private static BindingValue readBinding(String xml, Element element) {
        return new BindingValue(
                requiredDirectText(xml, element, "groupName", "SKD group binding"),
                requiredDirectText(xml, element, "templateType", "SKD group binding"),
                requiredDirectText(xml, element, "template", "SKD group binding"));
    }

    private static void requireCanonicalBindingTag(Element element, BindingValue binding) {
        validateTemplateType(binding.templateType());
        if (!bindingTag(binding).equals(element.localName)) {
            throw new IllegalArgumentException("SKD group binding tag/type mismatch for " + binding);
        }
    }

    private static String bindingTag(BindingValue binding) {
        return "GroupHeader".equals(binding.templateType())
                ? "groupHeaderTemplate" : "groupTemplate";
    }

    private static void validateReferences(String xml, Element root, List<BindingState> states) {
        Map<String, Integer> templateCounts = new HashMap<>();
        for (Element template : directChildren(root, "template")) {
            String name = directText(xml, template, "name");
            if (!blank(name)) templateCounts.merge(name, 1, Integer::sum);
        }
        Map<String, Integer> groupCounts = structureGroupCounts(xml, root);
        for (BindingState state : states) {
            if (state.removed) continue;
            BindingValue binding = state.finalValue();
            int templates = templateCounts.getOrDefault(binding.template(), 0);
            if (templates != 1) {
                throw new IllegalArgumentException("Referenced AreaTemplate "
                        + (templates == 0 ? "not found: " : "is ambiguous: ")
                        + binding.template());
            }
            int groups = groupCounts.getOrDefault(binding.groupName(), 0);
            if (groups != 1) {
                throw new IllegalArgumentException("Referenced structure group "
                        + (groups == 0 ? "not found: " : "is ambiguous: ")
                        + binding.groupName());
            }
        }
    }

    private static void validateFinalReferences(String xml, Element root) {
        List<BindingState> states = new ArrayList<>();
        Map<String, BindingValue> identities = new HashMap<>();
        for (Element child : root.children) {
            if (!"groupTemplate".equals(child.localName)
                    && !"groupHeaderTemplate".equals(child.localName)) continue;
            BindingValue value = readBinding(xml, child);
            requireCanonicalBindingTag(child, value);
            BindingValue previous = identities.putIfAbsent(value.identityKey(), value);
            if (previous != null) {
                throw new IllegalArgumentException("SKD group binding collision for group/templateType: "
                        + value.groupName() + "/" + value.templateType());
            }
            states.add(new BindingState(child, value));
        }
        validateReferences(xml, root, states);
    }

    private static Map<String, Integer> structureGroupCounts(String xml, Element root) {
        Map<String, Integer> result = new HashMap<>();
        Deque<Element> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Element element = queue.removeFirst();
            queue.addAll(element.children);
            String type = localType(attribute(element.startTag, "type"));
            if (!"item".equals(element.localName) || type == null
                    || !type.startsWith("StructureItem")) continue;
            String name = directText(xml, element, "name");
            if (!blank(name)) result.merge(name, 1, Integer::sum);
        }
        return result;
    }

    private static String insertBinding(String xml, BindingValue binding) {
        Element root = parse(xml);
        int insert = root.closeStart;
        for (Element child : root.children) {
            boolean target = "GroupHeader".equals(binding.templateType())
                    ? "settingsVariant".equals(child.localName)
                    : "groupHeaderTemplate".equals(child.localName)
                    || "settingsVariant".equals(child.localName);
            if (target) {
                insert = bindingSpanStart(xml, child);
                break;
            }
        }
        String lineSeparator = lineSeparator(xml);
        String indent = childIndent(xml, root);
        String childIndent = indent + indentUnit(indent);
        String tag = bindingTag(binding);
        String fragment = indent + "<" + tag + ">" + lineSeparator
                + childIndent + "<groupName>" + escape(binding.groupName()) + "</groupName>"
                + lineSeparator
                + childIndent + "<templateType>" + escape(binding.templateType())
                + "</templateType>" + lineSeparator
                + childIndent + "<template>" + escape(binding.template()) + "</template>"
                + lineSeparator
                + indent + "</" + tag + ">" + lineSeparator;
        return xml.substring(0, insert) + fragment + xml.substring(insert);
    }

    private static String childIndent(String xml, Element root) {
        if (!root.children.isEmpty()) {
            Element first = root.children.get(0);
            int lineStart = lineStart(xml, first.start);
            String candidate = xml.substring(lineStart, first.start);
            if (candidate.isBlank()) return candidate;
        }
        return "\t";
    }

    private static String indentUnit(String parentIndent) {
        return parentIndent.contains("\t") ? "\t" : "    ";
    }

    private static void addDirectTextReplacement(String xml, List<Replacement> replacements,
                                                 Element parent, String localName,
                                                 String newValue) {
        Element child = requireDirectChild(parent, localName, "SKD group binding");
        int start = child.startTagEnd + 1;
        int end = child.closeStart;
        while (start < end && Character.isWhitespace(xml.charAt(start))) start++;
        while (end > start && Character.isWhitespace(xml.charAt(end - 1))) end--;
        String escaped = escape(newValue);
        if (!xml.substring(start, end).equals(escaped)) {
            replacements.add(new Replacement(start, end, escaped));
        }
    }

    private static int bindingSpanStart(String xml, Element element) {
        int lineStart = lineStart(xml, element.start);
        return xml.substring(lineStart, element.start).isBlank() ? lineStart : element.start;
    }

    private static int bindingSpanEnd(String xml, Element element) {
        int pos = element.end;
        int lineEnd = pos;
        while (lineEnd < xml.length() && xml.charAt(lineEnd) != '\r'
                && xml.charAt(lineEnd) != '\n') lineEnd++;
        if (!xml.substring(pos, lineEnd).isBlank()) return pos;
        if (lineEnd < xml.length() && xml.charAt(lineEnd) == '\r'
                && lineEnd + 1 < xml.length() && xml.charAt(lineEnd + 1) == '\n') return lineEnd + 2;
        if (lineEnd < xml.length()) return lineEnd + 1;
        return lineEnd;
    }

    private static String lineBreakAndIndentAfter(String xml, Element element) {
        String separator = lineSeparator(xml);
        if (xml.startsWith(separator, element.end)) {
            int start = lineStart(xml, element.start);
            String indent = xml.substring(start, element.start);
            return separator + (indent.isBlank() ? indent : "");
        }
        return "";
    }

    private static String qualifiedSiblingName(String qName, String localName) {
        int colon = qName.indexOf(':');
        return colon < 0 ? localName : qName.substring(0, colon + 1) + localName;
    }

    private static String lineSeparator(String xml) {
        int lf = xml.indexOf('\n');
        return lf > 0 && xml.charAt(lf - 1) == '\r' ? "\r\n" : "\n";
    }

    private static int lineStart(String xml, int at) {
        int lf = xml.lastIndexOf('\n', Math.max(0, at - 1));
        return lf < 0 ? 0 : lf + 1;
    }

    private static String requiredDirectText(String xml, Element parent, String localName,
                                             String context) {
        Element child = requireDirectChild(parent, localName, context);
        if (child.selfClosing) {
            throw new IllegalArgumentException("Expected non-empty " + localName + " in " + context);
        }
        String value = unescape(xml.substring(child.startTagEnd + 1, child.closeStart).trim());
        if (blank(value)) throw new IllegalArgumentException("Expected non-empty " + localName
                + " in " + context);
        return value;
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
        return parent.children.stream().filter(child -> localName.equals(child.localName)).toList();
    }

    private static String directText(String xml, Element parent, String localName) {
        List<Element> children = directChildren(parent, localName);
        if (children.size() != 1 || children.get(0).selfClosing) return null;
        Element child = children.get(0);
        return unescape(xml.substring(child.startTagEnd + 1, child.closeStart).trim());
    }

    private static void absent(String policy, String message) {
        if ("fail".equals(policy)) throw new IllegalArgumentException(message);
    }

    private static String attribute(String startTag, String localName) {
        int pos = 1;
        while (pos < startTag.length()) {
            while (pos < startTag.length() && !Character.isWhitespace(startTag.charAt(pos))) pos++;
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            int nameStart = pos;
            while (pos < startTag.length() && startTag.charAt(pos) != '='
                    && !Character.isWhitespace(startTag.charAt(pos))
                    && startTag.charAt(pos) != '>') pos++;
            String qName = startTag.substring(nameStart, pos);
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            if (qName.isEmpty() || pos >= startTag.length() || startTag.charAt(pos) != '=') {
                pos++;
                continue;
            }
            pos++;
            while (pos < startTag.length() && Character.isWhitespace(startTag.charAt(pos))) pos++;
            if (pos >= startTag.length()
                    || (startTag.charAt(pos) != '\'' && startTag.charAt(pos) != '"')) continue;
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

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
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
                element.end = gt + 1;
            } else {
                Element parent = stack.peek();
                Element element = new Element(qName, localName(qName), parent, lt, gt,
                        xml.substring(lt, gt + 1), selfClosing);
                if (parent != null) parent.children.add(element);
                else if (root == null) root = element;
                else throw new IllegalArgumentException("Malformed XML: multiple roots");
                if (selfClosing) {
                    element.closeStart = gt;
                    element.end = gt + 1;
                } else stack.push(element);
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
        final int start;
        final int startTagEnd;
        final String startTag;
        final boolean selfClosing;
        final List<Element> children = new ArrayList<>();
        int closeStart;
        int end;

        Element(String qName, String localName, Element parent, int start, int startTagEnd,
                String startTag, boolean selfClosing) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.start = start;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
            this.selfClosing = selfClosing;
        }
    }

    private static final class BindingState {
        final Element element;
        final BindingValue current;
        BindingValue target;
        boolean removed;

        BindingState(Element element, BindingValue current) {
            this.element = element;
            this.current = current;
        }

        BindingValue finalValue() {
            return target == null ? current : target;
        }
    }

    private record BindingValue(String groupName, String templateType, String template) {
        String tripleKey() { return groupName + "\u0000" + templateType + "\u0000" + template; }
        String identityKey() { return groupName + "\u0000" + templateType; }

        @Override
        public String toString() {
            return groupName + "/" + templateType + "/" + template;
        }
    }

    private record BindingPlan(List<BindingValue> insertions, int changed) {
    }

    private record Replacement(int start, int end, String value) {
    }
}
//++agent TASK-174

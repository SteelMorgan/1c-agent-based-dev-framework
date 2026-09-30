package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdStructureUpsertDsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

//++agent TASK-174 [10.07.2026 18:25:00]
/**
 * Lossless upsert именованных top-level групп структуры варианта СКД.
 *
 * <p>Редактор строит span-AST XML, валидирует payload целиком и заменяет только
 * адресованные {@code StructureItemGroup}. Остальные datasets/templates/settings
 * остаются байтово неизменными.</p>
 */
public final class SkdStructureUpsertEditor {

    public record Result(String content, boolean changed) {
    }

    public Result apply(String original, SkdStructureUpsertDsl patch) {
        Element root = parse(original);
        Context context = validateAndResolve(original, root, patch);
        String result = original;

        List<Replacement> replacements = new ArrayList<>();
        List<SkdStructureUpsertDsl.Item> items = patch.getItems() == null
                ? List.of() : patch.getItems();
        for (int index = 0; index < items.size(); index++) {
            SkdStructureUpsertDsl.Item item = items.get(index);
            Element existing = context.targetByName.get(item.getName());
            int start = existing == null ? context.settings.closeLineStart : existing.lineStart;
            int end = existing == null ? start : existing.endWithLineBreak;
            String value = existing == null
                    ? render(item, context.itemIndent, context.lineSeparator)
                    : mergeExisting(original, existing, item, context.lineSeparator);
            replacements.add(new Replacement(start, end, value, index));
        }
        replacements.sort((left, right) -> {
            int byPosition = Integer.compare(right.start, left.start);
            return byPosition != 0 ? byPosition : Integer.compare(right.payloadIndex, left.payloadIndex);
        });
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start) + replacement.value
                    + result.substring(replacement.end);
        }
        if (patch.getTopLevelOrder() != null) {
            result = reorderTopLevel(result, patch.getVariant(), patch.getTopLevelOrder());
        }
        parse(result);
        return new Result(result, !result.equals(original));
    }

    private static Context validateAndResolve(String xml, Element root, SkdStructureUpsertDsl patch) {
        if (patch == null || blank(patch.getVariant())) {
            throw new IllegalArgumentException(
                    "Structure upsert requires a non-empty variant");
        }
        boolean hasItems = patch.getItems() != null && !patch.getItems().isEmpty();
        boolean hasTopLevelOrder = patch.getTopLevelOrder() != null;
        if (!hasItems && !hasTopLevelOrder) {
            throw new IllegalArgumentException(
                    "Structure upsert requires non-empty items or topLevelOrder");
        }
        if (hasItems && blank(patch.getDataSet())) {
            throw new IllegalArgumentException(
                    "Structure upsert with items requires a non-empty dataSet");
        }
        if (!"DataCompositionSchema".equals(root.localName)) {
            throw new IllegalArgumentException("Expected root <DataCompositionSchema>");
        }

        List<Element> variants = root.children.stream()
                .filter(child -> "settingsVariant".equals(child.localName))
                .filter(child -> patch.getVariant().equals(directText(xml, child, "name")))
                .toList();
        if (variants.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one settingsVariant named '"
                    + patch.getVariant() + "', found " + variants.size());
        }
        Element settings = directChild(variants.get(0), "settings");
        if (settings == null) {
            throw new IllegalArgumentException("settingsVariant '" + patch.getVariant() + "' has no settings");
        }

        Set<String> fields = new HashSet<>();
        Map<String, Integer> selectionFieldOwners = new HashMap<>();
        if (hasItems) {
            Map<String, List<Element>> dataSetsByName = new LinkedHashMap<>();
            for (Element child : root.children) {
                if ("dataSet".equals(child.localName)) {
                    dataSetsByName.computeIfAbsent(directText(xml, child, "name"), ignored -> new ArrayList<>())
                            .add(child);
                }
            }
            List<Element> dataSets = dataSetsByName.getOrDefault(patch.getDataSet(), List.of());
            if (dataSets.size() != 1) {
                throw new IllegalArgumentException("Expected exactly one dataSet named '"
                        + patch.getDataSet() + "', found " + dataSets.size());
            }
            addDataSetFields(xml, dataSets.get(0), fields, null);

            Set<String> reachable = reachableDataSets(xml, root, patch.getDataSet(), dataSetsByName);
            for (String dataSetName : reachable) {
                List<Element> matches = dataSetsByName.getOrDefault(dataSetName, List.of());
                if (matches.size() != 1) {
                    throw new IllegalArgumentException("Expected exactly one linked dataSet named '"
                            + dataSetName + "', found " + matches.size());
                }
                addDataSetFields(xml, matches.get(0), null, selectionFieldOwners);
            }
        }

        Set<String> payloadNames = new HashSet<>();
        if (hasItems) {
            for (SkdStructureUpsertDsl.Item item : patch.getItems()) {
                validateItem(item, fields, selectionFieldOwners, payloadNames);
            }
        }

        Map<String, List<Element>> existingByName = new HashMap<>();
        collectNamedStructureItems(xml, settings, existingByName);
        Map<String, Element> topLevelByName = new HashMap<>();
        for (Element child : settings.children) {
            if (isStructureItem(child)) {
                String name = directText(xml, child, "name");
                if (!blank(name)) topLevelByName.put(name, child);
            }
        }
        for (SkdStructureUpsertDsl.Item item : hasItems ? patch.getItems() : List.<SkdStructureUpsertDsl.Item>of()) {
            List<Element> targetMatches = existingByName.getOrDefault(item.getName(), List.of());
            if (targetMatches.size() > 1) {
                throw new IllegalArgumentException(
                        "Duplicate existing structure identity: " + item.getName());
            }
            Element existingTarget = targetMatches.isEmpty() ? null : targetMatches.get(0);
            if (existingTarget != null && !isGroupStructureItem(existingTarget)) {
                throw new IllegalArgumentException(
                        "Existing structure item is not a group: " + item.getName());
            }
            if (existingTarget == null && hasOutputParametersPatch(item)) {
                throw new IllegalArgumentException(
                        "Missing exact structure group requested for outputParameters: "
                                + firstOutputParametersPatchName(item));
            }

            Set<String> subtreeNames = new HashSet<>();
            collectPayloadNames(item, subtreeNames);
            for (String name : subtreeNames) {
                List<Element> matches = existingByName.getOrDefault(name, List.of());
                if (matches.size() > 1) {
                    throw new IllegalArgumentException("Duplicate existing structure identity: " + name);
                }
                if (matches.size() == 1
                        && (existingTarget == null || !isDescendantOrSelf(matches.get(0), existingTarget))) {
                    throw new IllegalArgumentException(
                            "Structure identity is already owned by another top-level item: " + name);
                }
            }
            if (existingTarget != null) {
                validateExistingTopology(xml, existingTarget, item);
            }
        }
        if (hasTopLevelOrder) {
            validateTopLevelOrder(xml, settings, patch);
        }

        String lineSeparator = xml.contains("\r\n") ? "\r\n" : "\n";
        String settingsIndent = indentation(xml, settings.lineStart, settings.start);
        String itemIndent = settingsIndent + "\t";
        //++agent TASK-174 XG-98 [12.07.2026 00:00:00]
        // Уникальная identity адресует существующую группу на любой глубине, чтобы
        // lossless merge не переносил вложенный subtree на верхний уровень.
        Map<String, Element> targetByName = new HashMap<>(topLevelByName);
        for (SkdStructureUpsertDsl.Item item : hasItems ? patch.getItems()
                : List.<SkdStructureUpsertDsl.Item>of()) {
            List<Element> matches = existingByName.getOrDefault(item.getName(), List.of());
            if (matches.size() == 1) targetByName.put(item.getName(), matches.get(0));
        }
        //--agent TASK-174 XG-98
        return new Context(settings, targetByName, itemIndent, lineSeparator);
    }

    private static void validateItem(SkdStructureUpsertDsl.Item item, Set<String> fields,
                                     Map<String, Integer> selectionFieldOwners, Set<String> names) {
        if (item == null || !"group".equals(item.getKind()) || blank(item.getName())) {
            throw new IllegalArgumentException(
                    "Every structure item requires kind='group' and a non-empty name");
        }
        if (!names.add(item.getName())) {
            throw new IllegalArgumentException("Duplicate structure identity in payload: " + item.getName());
        }
        boolean details = false;
        boolean regular = false;
        if (item.getGroupItems() != null) {
            for (String field : item.getGroupItems()) {
                if ("details".equalsIgnoreCase(field)) {
                    details = true;
                } else {
                    regular = true;
                    requireField(fields, field, "groupItems", item.getName());
                }
            }
        }
        if (details && regular) {
            throw new IllegalArgumentException(
                    "Details grouping cannot be mixed with fields in " + item.getName());
        }
        if (item.getOrder() != null) {
            Set<String> orderFields = new HashSet<>();
            for (SkdStructureUpsertDsl.OrderItem orderItem : item.getOrder()) {
                if (orderItem == null) {
                    throw new IllegalArgumentException(
                            "Null order item in structure item '" + item.getName() + "'");
                }
                requireField(fields, orderItem.getField(), "order", item.getName());
                if (!orderFields.add(orderItem.getField())) {
                    throw new IllegalArgumentException("Duplicate order field '"
                            + orderItem.getField() + "' in structure item '" + item.getName() + "'");
                }
                if (!"Asc".equals(orderItem.getDirection())
                        && !"Desc".equals(orderItem.getDirection())) {
                    throw new IllegalArgumentException("Unknown order direction '"
                            + orderItem.getDirection() + "' in structure item '" + item.getName()
                            + "'; expected Asc or Desc");
                }
            }
        }
        if (item.getSelection() != null) {
            Set<String> selectedFields = new HashSet<>();
            for (String field : item.getSelection()) {
                if (!"Auto".equalsIgnoreCase(field)) {
                    int owners = selectionFieldOwners.getOrDefault(field, 0);
                    if (owners == 0) {
                        throw new IllegalArgumentException("Unknown reachable dataSet field '" + field
                                + "' in selection of structure item '" + item.getName() + "'");
                    }
                    if (owners > 1) {
                        throw new IllegalArgumentException("Ambiguous reachable dataSet field '" + field
                                + "' in selection of structure item '" + item.getName() + "'");
                    }
                    if (!selectedFields.add(field)) {
                        throw new IllegalArgumentException("Duplicate selection field '" + field
                                + "' in structure item '" + item.getName() + "'");
                    }
                }
            }
        }
        //++agent TASK-174 [12.07.2026 03:25:00]
        if (item.getOutputParameters() != null) {
            Set<String> parameters = new HashSet<>();
            for (SkdStructureUpsertDsl.OutputParameter output : item.getOutputParameters()) {
                if (output == null || blank(output.getParameter())) {
                    throw new IllegalArgumentException(
                            "Every outputParameters item requires a non-empty parameter in structure item '"
                                    + item.getName() + "'");
                }
                if (!parameters.add(output.getParameter())) {
                    throw new IllegalArgumentException("Duplicate output parameter '"
                            + output.getParameter() + "' in structure item '" + item.getName() + "'");
                }
                if (blank(output.getValueType()) || !isXmlTypeName(output.getValueType())) {
                    throw new IllegalArgumentException("Invalid output parameter valueType '"
                            + output.getValueType() + "' in structure item '" + item.getName() + "'");
                }
                if (output.getValue() == null) {
                    throw new IllegalArgumentException("Output parameter '" + output.getParameter()
                            + "' requires value in structure item '" + item.getName() + "'");
                }
            }
        }
        //--agent TASK-174
        if (item.getChildren() != null) {
            for (SkdStructureUpsertDsl.Item child : item.getChildren()) {
                validateItem(child, fields, selectionFieldOwners, names);
            }
        }
    }

    //++agent TASK-174 XG-96 [12.07.2026 00:00:00]
    /** Selection группы может законно читать поля только из связной компоненты anchor dataset. */
    private static Set<String> reachableDataSets(String xml, Element root, String anchor,
                                                  Map<String, List<Element>> dataSetsByName) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Element child : root.children) {
            if (!"dataSetLink".equals(child.localName)) continue;
            String source = directText(xml, child, "sourceDataSet");
            String destination = directText(xml, child, "destinationDataSet");
            if (blank(source) || blank(destination)
                    || !dataSetsByName.containsKey(source) || !dataSetsByName.containsKey(destination)) {
                continue;
            }
            graph.computeIfAbsent(source, ignored -> new HashSet<>()).add(destination);
            graph.computeIfAbsent(destination, ignored -> new HashSet<>()).add(source);
        }
        Set<String> reachable = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(anchor);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reachable.add(current)) continue;
            pending.addAll(graph.getOrDefault(current, Set.of()));
        }
        return reachable;
    }

    private static void addDataSetFields(String xml, Element dataSet, Set<String> fields,
                                         Map<String, Integer> owners) {
        Set<String> owned = new HashSet<>();
        for (Element field : dataSet.children) {
            if (!"field".equals(field.localName)) continue;
            String dataPath = directText(xml, field, "dataPath");
            String fieldName = directText(xml, field, "field");
            if (!blank(dataPath)) owned.add(dataPath);
            if (!blank(fieldName)) owned.add(fieldName);
        }
        if (fields != null) fields.addAll(owned);
        if (owners != null) {
            for (String name : owned) owners.merge(name, 1, Integer::sum);
        }
    }
    //--agent TASK-174 XG-96

    private static void validateTopLevelOrder(String xml, Element settings,
                                              SkdStructureUpsertDsl patch) {
        Map<String, List<Element>> existing = new LinkedHashMap<>();
        for (Element child : settings.children) {
            if (!isStructureItem(child)) continue;
            String name = directText(xml, child, "name");
            if (blank(name)) {
                throw new IllegalArgumentException(
                        "topLevelOrder requires every direct structure item to have a name");
            }
            existing.computeIfAbsent(name, ignored -> new ArrayList<>()).add(child);
        }
        for (Map.Entry<String, List<Element>> entry : existing.entrySet()) {
            if (entry.getValue().size() != 1) {
                throw new IllegalArgumentException("Duplicate existing top-level structure identity: "
                        + entry.getKey());
            }
        }

        Set<String> finalNames = new HashSet<>(existing.keySet());
        Map<String, List<Element>> allExisting = new HashMap<>();
        collectNamedStructureItems(xml, settings, allExisting);
        if (patch.getItems() != null) {
            for (SkdStructureUpsertDsl.Item item : patch.getItems()) {
                //++agent TASK-174 XG-98 [12.07.2026 00:00:00]
                // Nested exact-identity update не создаёт новый top-level slot.
                List<Element> matches = allExisting.getOrDefault(item.getName(), List.of());
                if (matches.isEmpty() || existing.containsKey(item.getName())) {
                    finalNames.add(item.getName());
                }
                //--agent TASK-174 XG-98
            }
        }

        Set<String> requested = new HashSet<>();
        for (String name : patch.getTopLevelOrder()) {
            if (blank(name)) {
                throw new IllegalArgumentException(
                        "topLevelOrder contains a null or blank structure identity");
            }
            if (!requested.add(name)) {
                throw new IllegalArgumentException(
                        "Duplicate structure identity in topLevelOrder: " + name);
            }
            if (!finalNames.contains(name)) {
                throw new IllegalArgumentException(
                        "Missing top-level structure identity requested by topLevelOrder: " + name);
            }
        }
        Set<String> unlisted = new HashSet<>(finalNames);
        unlisted.removeAll(requested);
        if (!unlisted.isEmpty()) {
            throw new IllegalArgumentException(
                    "topLevelOrder must list every named top-level structure item; unlisted: "
                            + unlisted.stream().sorted().toList());
        }
    }

    private static void requireField(Set<String> fields, String field, String section, String item) {
        if (blank(field) || !fields.contains(field)) {
            throw new IllegalArgumentException("Unknown dataSet field '" + field + "' in "
                    + section + " of structure item '" + item + "'");
        }
    }

    private static void collectPayloadNames(SkdStructureUpsertDsl.Item item, Set<String> result) {
        result.add(item.getName());
        if (item.getChildren() != null) {
            for (SkdStructureUpsertDsl.Item child : item.getChildren()) {
                collectPayloadNames(child, result);
            }
        }
    }

    private static boolean hasOutputParametersPatch(SkdStructureUpsertDsl.Item item) {
        if (item.getOutputParameters() != null) return true;
        if (item.getChildren() == null) return false;
        return item.getChildren().stream().anyMatch(SkdStructureUpsertEditor::hasOutputParametersPatch);
    }

    private static String firstOutputParametersPatchName(SkdStructureUpsertDsl.Item item) {
        if (item.getOutputParameters() != null) return item.getName();
        if (item.getChildren() != null) {
            for (SkdStructureUpsertDsl.Item child : item.getChildren()) {
                if (hasOutputParametersPatch(child)) return firstOutputParametersPatchName(child);
            }
        }
        throw new IllegalArgumentException("Internal outputParameters selector resolution error");
    }

    private static boolean isDescendantOrSelf(Element element, Element ancestor) {
        for (Element current = element; current != null; current = current.parent) {
            if (current == ancestor) return true;
        }
        return false;
    }

    private static void validateExistingTopology(String xml, Element existing,
                                                 SkdStructureUpsertDsl.Item patch) {
        if (patch.getOrder() != null) {
            long orderNodes = existing.children.stream()
                    .filter(child -> "order".equals(child.localName)).count();
            if (orderNodes > 1) {
                throw new IllegalArgumentException("Expected at most one direct order in structure item '"
                        + patch.getName() + "', found " + orderNodes);
            }
        }
        //++agent TASK-174 [12.07.2026 03:25:00]
        if (patch.getOutputParameters() != null) {
            List<Element> outputNodes = existing.children.stream()
                    .filter(child -> "outputParameters".equals(child.localName)).toList();
            if (outputNodes.size() > 1) {
                throw new IllegalArgumentException(
                        "Expected at most one direct outputParameters in structure item '"
                                + patch.getName() + "', found " + outputNodes.size());
            }
            if (outputNodes.size() == 1) {
                Set<String> existingParameters = new HashSet<>();
                for (Element child : outputNodes.get(0).children) {
                    if (!"item".equals(child.localName)) continue;
                    String parameter = directText(xml, child, "parameter");
                    if (!blank(parameter) && !existingParameters.add(parameter)) {
                        throw new IllegalArgumentException("Duplicate existing output parameter '"
                                + parameter + "' in structure item '" + patch.getName() + "'");
                    }
                }
            }
        }
        //--agent TASK-174
        if (patch.getChildren() == null) return;
        for (SkdStructureUpsertDsl.Item childPatch : patch.getChildren()) {
            List<Element> directMatches = existing.children.stream()
                    .filter(SkdStructureUpsertEditor::isStructureItem)
                    .filter(child -> childPatch.getName().equals(directText(xml, child, "name")))
                    .toList();
            if (directMatches.size() > 1) {
                throw new IllegalArgumentException(
                        "Duplicate direct child structure identity: " + childPatch.getName());
            }
            if (directMatches.isEmpty()) {
                if (childPatch.getOutputParameters() != null) {
                    throw new IllegalArgumentException(
                            "Missing exact structure group requested for outputParameters: "
                                    + childPatch.getName());
                }
                if (containsNamedStructureDescendant(xml, existing, childPatch.getName())) {
                    throw new IllegalArgumentException("Structure identity exists at another recursive path: "
                            + childPatch.getName());
                }
                continue;
            }
            Element direct = directMatches.get(0);
            if (!isGroupStructureItem(direct)) {
                throw new IllegalArgumentException(
                        "Existing child structure item is not a group: " + childPatch.getName());
            }
            validateExistingTopology(xml, direct, childPatch);
        }
    }

    private static boolean containsNamedStructureDescendant(String xml, Element parent, String name) {
        for (Element child : parent.children) {
            if (isStructureItem(child) && name.equals(directText(xml, child, "name"))) return true;
            if (containsNamedStructureDescendant(xml, child, name)) return true;
        }
        return false;
    }

    private static String reorderTopLevel(String xml, String variant, List<String> requestedOrder) {
        Element root = parse(xml);
        List<Element> variants = root.children.stream()
                .filter(child -> "settingsVariant".equals(child.localName))
                .filter(child -> variant.equals(directText(xml, child, "name")))
                .toList();
        if (variants.size() != 1) {
            throw new IllegalArgumentException("Expected exactly one settingsVariant named '"
                    + variant + "', found " + variants.size());
        }
        Element settings = directChild(variants.get(0), "settings");
        if (settings == null) {
            throw new IllegalArgumentException("settingsVariant '" + variant + "' has no settings");
        }

        List<Element> slots = settings.children.stream()
                .filter(SkdStructureUpsertEditor::isStructureItem)
                .toList();
        Map<String, Element> byName = new LinkedHashMap<>();
        List<String> currentOrder = new ArrayList<>();
        for (Element slot : slots) {
            String name = directText(xml, slot, "name");
            if (blank(name) || byName.put(name, slot) != null) {
                throw new IllegalArgumentException(
                        "topLevelOrder lost exact-one identity after structure upsert: " + name);
            }
            currentOrder.add(name);
        }
        if (currentOrder.equals(requestedOrder)) return xml;
        if (slots.size() != requestedOrder.size() || !byName.keySet().containsAll(requestedOrder)) {
            throw new IllegalArgumentException(
                    "topLevelOrder does not match final top-level structure identities");
        }

        List<Replacement> replacements = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            Element targetSlot = slots.get(index);
            Element source = byName.get(requestedOrder.get(index));
            String byteExactSubtree = xml.substring(source.lineStart, source.endWithLineBreak);
            replacements.add(new Replacement(targetSlot.lineStart, targetSlot.endWithLineBreak,
                    byteExactSubtree, index));
        }
        replacements.sort((left, right) -> Integer.compare(right.start, left.start));
        String result = xml;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start) + replacement.value
                    + result.substring(replacement.end);
        }
        return result;
    }

    /**
     * Merge существующей группы меняет только аспекты, явно присутствующие в payload.
     * Неадресованные direct children остаются исходными byte spans.
     */
    private static String mergeExisting(String xml, Element existing,
                                        SkdStructureUpsertDsl.Item patch, String eol) {
        int base = existing.lineStart;
        int end = existing.endWithLineBreak;
        String itemIndent = indentation(xml, existing.lineStart, existing.start);
        String childIndent = itemIndent + "\t";
        List<Replacement> replacements = new ArrayList<>();
        int sequence = 0;

        if (patch.getGroupItems() != null) {
            Element groupItems = directChild(existing, "groupItems");
            String rendered = patch.getGroupItems().isEmpty()
                    ? "" : renderGroupItems(patch.getGroupItems(), childIndent, eol);
            if (groupItems != null) {
                replacements.add(localReplacement(base, groupItems.lineStart,
                        groupItems.endWithLineBreak, rendered, sequence++));
            } else if (!rendered.isEmpty()) {
                replacements.add(localReplacement(base, insertionAfterName(existing),
                        insertionAfterName(existing), rendered, sequence++));
            }
        }

        if (patch.getOrder() != null) {
            Element order = directChild(existing, "order");
            String rendered = patch.getOrder().isEmpty()
                    ? "" : renderOrder(patch.getOrder(), childIndent, eol);
            if (order != null) {
                replacements.add(localReplacement(base, order.lineStart,
                        order.endWithLineBreak, rendered, sequence++));
            } else if (!rendered.isEmpty()) {
                int insertion = insertionForOrder(existing);
                replacements.add(localReplacement(base, insertion, insertion,
                        rendered, sequence++));
            }
        }

        if (patch.getSelection() != null) {
            Element selection = directChild(existing, "selection");
            //++agent TASK-174 [10.07.2026 22:24:00]
            // Пустой список является явной командой снять auto/field selection,
            // а отсутствие поля оставляет исходный byte span без изменений.
            String rendered = patch.getSelection().isEmpty()
                    ? "" : renderSelection(patch.getSelection(), childIndent, eol);
            if (selection != null) {
                replacements.add(localReplacement(base, selection.lineStart,
                        selection.endWithLineBreak, rendered, sequence++));
            } else if (!rendered.isEmpty()) {
                int insertion = firstDirectStructureStart(existing);
                replacements.add(localReplacement(base, insertion, insertion,
                        rendered, sequence++));
            }
            //--agent TASK-174
        }

        //++agent TASK-174 [12.07.2026 03:25:00]
        if (patch.getOutputParameters() != null) {
            Element outputParameters = directChild(existing, "outputParameters");
            String rendered = patch.getOutputParameters().isEmpty()
                    ? "" : renderOutputParameters(patch.getOutputParameters(), childIndent, eol);
            if (outputParameters != null) {
                replacements.add(localReplacement(base, outputParameters.lineStart,
                        outputParameters.endWithLineBreak, rendered, sequence++));
            } else if (!rendered.isEmpty()) {
                int insertion = firstDirectStructureStart(existing);
                replacements.add(localReplacement(base, insertion, insertion,
                        rendered, sequence++));
            }
        }
        //--agent TASK-174

        if (patch.getChildren() != null) {
            for (SkdStructureUpsertDsl.Item childPatch : patch.getChildren()) {
                Element child = existing.children.stream()
                        .filter(SkdStructureUpsertEditor::isStructureItem)
                        .filter(candidate -> childPatch.getName().equals(directText(xml, candidate, "name")))
                        .findFirst().orElse(null);
                if (child == null) {
                    replacements.add(localReplacement(base, existing.closeLineStart,
                            existing.closeLineStart, render(childPatch, childIndent, eol), sequence++));
                } else {
                    replacements.add(localReplacement(base, child.lineStart, child.endWithLineBreak,
                            mergeExisting(xml, child, childPatch, eol), sequence++));
                }
            }
        }

        replacements.sort((left, right) -> {
            int byPosition = Integer.compare(right.start, left.start);
            return byPosition != 0 ? byPosition : Integer.compare(right.payloadIndex, left.payloadIndex);
        });
        String result = xml.substring(base, end);
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start) + replacement.value
                    + result.substring(replacement.end);
        }
        return result;
    }

    private static Replacement localReplacement(int base, int start, int end,
                                                String value, int sequence) {
        return new Replacement(start - base, end - base, value, sequence);
    }

    private static int insertionAfterName(Element existing) {
        Element name = directChild(existing, "name");
        return name == null ? existing.startTagEnd + 1 : name.endWithLineBreak;
    }

    private static int insertionForOrder(Element existing) {
        Element filter = directChild(existing, "filter");
        if (filter != null) return filter.endWithLineBreak;
        Element groupItems = directChild(existing, "groupItems");
        if (groupItems != null) return groupItems.endWithLineBreak;
        return insertionAfterName(existing);
    }

    private static int firstDirectStructureStart(Element existing) {
        return existing.children.stream().filter(SkdStructureUpsertEditor::isStructureItem)
                .mapToInt(child -> child.lineStart).min().orElse(existing.closeLineStart);
    }

    private static String render(SkdStructureUpsertDsl.Item item, String indent, String eol) {
        StringBuilder out = new StringBuilder();
        renderItem(out, item, indent, eol);
        return out.toString();
    }

    private static void renderItem(StringBuilder out, SkdStructureUpsertDsl.Item item,
                                   String indent, String eol) {
        line(out, indent, "<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">", eol);
        line(out, indent + "\t", "<dcsset:name>" + escape(item.getName()) + "</dcsset:name>", eol);
        if (item.getGroupItems() != null && !item.getGroupItems().isEmpty()) {
            out.append(renderGroupItems(item.getGroupItems(), indent + "\t", eol));
        }
        if (item.getOrder() == null) {
            line(out, indent + "\t", "<dcsset:order>", eol);
            line(out, indent + "\t\t", "<dcsset:item xsi:type=\"dcsset:OrderItemAuto\"/>", eol);
            line(out, indent + "\t", "</dcsset:order>", eol);
        } else if (!item.getOrder().isEmpty()) {
            out.append(renderOrder(item.getOrder(), indent + "\t", eol));
        }
        //++agent TASK-174 [10.07.2026 22:24:00]
        // Для новой группы omitted сохраняет прежний default Auto, explicit [] означает no selection.
        List<String> selection = item.getSelection() == null ? List.of("Auto") : item.getSelection();
        if (!selection.isEmpty()) {
            out.append(renderSelection(selection, indent + "\t", eol));
        }
        //--agent TASK-174
        if (item.getOutputParameters() != null && !item.getOutputParameters().isEmpty()) {
            out.append(renderOutputParameters(item.getOutputParameters(), indent + "\t", eol));
        }
        if (item.getChildren() != null) {
            for (SkdStructureUpsertDsl.Item child : item.getChildren()) {
                renderItem(out, child, indent + "\t", eol);
            }
        }
        line(out, indent, "</dcsset:item>", eol);
    }

    private static String renderOrder(List<SkdStructureUpsertDsl.OrderItem> orderItems,
                                      String indent, String eol) {
        StringBuilder out = new StringBuilder();
        line(out, indent, "<dcsset:order>", eol);
        for (SkdStructureUpsertDsl.OrderItem orderItem : orderItems) {
            line(out, indent + "\t", "<dcsset:item xsi:type=\"dcsset:OrderItemField\">", eol);
            line(out, indent + "\t\t", "<dcsset:field>" + escape(orderItem.getField())
                    + "</dcsset:field>", eol);
            line(out, indent + "\t\t", "<dcsset:orderType>" + orderItem.getDirection()
                    + "</dcsset:orderType>", eol);
            line(out, indent + "\t", "</dcsset:item>", eol);
        }
        line(out, indent, "</dcsset:order>", eol);
        return out.toString();
    }

    private static String renderGroupItems(List<String> fields, String indent, String eol) {
        StringBuilder out = new StringBuilder();
        line(out, indent, "<dcsset:groupItems>", eol);
        for (String field : fields) {
            if ("details".equalsIgnoreCase(field)) {
                //++agent TASK-174 [10.07.2026 20:16:00]
                // XDTO settings schema представляет детальные записи типом GroupItemAuto;
                // отдельного платформенного типа GroupItemDetails не существует.
                line(out, indent + "\t", "<dcsset:item xsi:type=\"dcsset:GroupItemAuto\"/>", eol);
                //--agent TASK-174
            } else {
                line(out, indent + "\t", "<dcsset:item xsi:type=\"dcsset:GroupItemField\">", eol);
                line(out, indent + "\t\t", "<dcsset:field>" + escape(field) + "</dcsset:field>", eol);
                line(out, indent + "\t\t", "<dcsset:groupType>Items</dcsset:groupType>", eol);
                line(out, indent + "\t\t", "<dcsset:periodAdditionType>None</dcsset:periodAdditionType>", eol);
                line(out, indent + "\t\t", "<dcsset:periodAdditionBegin xsi:type=\"xs:dateTime\">0001-01-01T00:00:00</dcsset:periodAdditionBegin>", eol);
                line(out, indent + "\t\t", "<dcsset:periodAdditionEnd xsi:type=\"xs:dateTime\">0001-01-01T00:00:00</dcsset:periodAdditionEnd>", eol);
                line(out, indent + "\t", "</dcsset:item>", eol);
            }
        }
        line(out, indent, "</dcsset:groupItems>", eol);
        return out.toString();
    }

    private static String renderSelection(List<String> fields, String indent, String eol) {
        StringBuilder out = new StringBuilder();
        line(out, indent, "<dcsset:selection>", eol);
        for (String field : fields) {
            if ("Auto".equalsIgnoreCase(field)) {
                line(out, indent + "\t", "<dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/>", eol);
            } else {
                line(out, indent + "\t", "<dcsset:item xsi:type=\"dcsset:SelectedItemField\">", eol);
                line(out, indent + "\t\t", "<dcsset:field>" + escape(field) + "</dcsset:field>", eol);
                line(out, indent + "\t", "</dcsset:item>", eol);
            }
        }
        line(out, indent, "</dcsset:selection>", eol);
        return out.toString();
    }

    //++agent TASK-174 [12.07.2026 03:25:00]
    private static String renderOutputParameters(
            List<SkdStructureUpsertDsl.OutputParameter> parameters, String indent, String eol) {
        StringBuilder out = new StringBuilder();
        line(out, indent, "<dcsset:outputParameters>", eol);
        for (SkdStructureUpsertDsl.OutputParameter parameter : parameters) {
            line(out, indent + "\t",
                    "<dcscor:item xsi:type=\"dcsset:SettingsParameterValue\">", eol);
            // true является каноническим default и не сериализуется; false обязан быть явным.
            if (Boolean.FALSE.equals(parameter.getUse())) {
                line(out, indent + "\t\t", "<dcscor:use>false</dcscor:use>", eol);
            }
            line(out, indent + "\t\t", "<dcscor:parameter>" + escape(parameter.getParameter())
                    + "</dcscor:parameter>", eol);
            String valueType = parameter.getValueType().contains(":")
                    ? parameter.getValueType() : "dcsset:" + parameter.getValueType();
            line(out, indent + "\t\t", "<dcscor:value xsi:type=\"" + valueType + "\">"
                    + escape(parameter.getValue()) + "</dcscor:value>", eol);
            line(out, indent + "\t", "</dcscor:item>", eol);
        }
        line(out, indent, "</dcsset:outputParameters>", eol);
        return out.toString();
    }

    private static boolean isXmlTypeName(String value) {
        return value.matches("(?:[A-Za-z_][A-Za-z0-9_.-]*:)?[A-Za-z_][A-Za-z0-9_.-]*");
    }
    //--agent TASK-174

    private static void line(StringBuilder out, String indent, String value, String eol) {
        out.append(indent).append(value).append(eol);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void collectNamedStructureItems(String xml, Element parent,
                                                   Map<String, List<Element>> result) {
        for (Element child : parent.children) {
            if (isStructureItem(child)) {
                String name = directText(xml, child, "name");
                if (!blank(name)) result.computeIfAbsent(name, ignored -> new ArrayList<>()).add(child);
            }
            collectNamedStructureItems(xml, child, result);
        }
    }

    private static boolean isStructureItem(Element element) {
        return "item".equals(element.localName)
                && element.startTag.contains("xsi:type")
                && element.startTag.contains("StructureItem");
    }

    private static boolean isGroupStructureItem(Element element) {
        return isStructureItem(element) && element.startTag.contains("StructureItemGroup");
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
                Element element = new Element(qName, localName(qName), parent, lineStart(xml, lt), lt, gt,
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
        root.closeLineStart = lineStart(xml, root.closeStart);
        setCloseLineStarts(xml, root);
        return root;
    }

    private static void setCloseLineStarts(String xml, Element element) {
        element.closeLineStart = lineStart(xml, element.closeStart);
        for (Element child : element.children) setCloseLineStarts(xml, child);
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

    private static String indentation(String xml, int lineStart, int elementStart) {
        return xml.substring(lineStart, elementStart);
    }

    private static String localName(String qName) {
        int colon = qName.indexOf(':');
        return colon < 0 ? qName : qName.substring(colon + 1);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Element {
        final String qName;
        final String localName;
        final Element parent;
        final int lineStart;
        final int start;
        final int startTagEnd;
        final String startTag;
        final List<Element> children = new ArrayList<>();
        int closeStart;
        int closeLineStart;
        int endWithLineBreak;

        Element(String qName, String localName, Element parent, int lineStart, int start,
                int startTagEnd, String startTag) {
            this.qName = qName;
            this.localName = localName;
            this.parent = parent;
            this.lineStart = lineStart;
            this.start = start;
            this.startTagEnd = startTagEnd;
            this.startTag = startTag;
        }
    }

    private record Context(Element settings, Map<String, Element> targetByName,
                           String itemIndent, String lineSeparator) {
    }

    private record Replacement(int start, int end, String value, int payloadIndex) {
    }
}
//++agent TASK-174

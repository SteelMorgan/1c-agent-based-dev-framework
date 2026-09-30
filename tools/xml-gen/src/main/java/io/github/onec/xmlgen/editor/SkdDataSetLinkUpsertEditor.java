package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdDataSetLinkUpsertDsl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//++agent TASK-174 [10.07.2026 22:05:00]
/**
 * Byte-lossless редактор отношений наборов данных существующей Designer СКД.
 * Меняются только адресованные top-level {@code dataSetLink}; остальные spans
 * (включая ручные макеты и bare LF внутри {@code v8:content}) не нормализуются.
 */
public final class SkdDataSetLinkUpsertEditor {

    private static final Pattern DATA_SET = Pattern.compile(
            "(?m)^\\t<dataSet(?:\\s[^>]*)?>\\R(?s:.*?)^\\t</dataSet>(?:\\r?\\n|$)");
    private static final Pattern DATA_SET_LINK = Pattern.compile(
            "(?m)^\\t<dataSetLink>\\R(?s:.*?)^\\t</dataSetLink>(?:\\r?\\n|$)");
    private static final Pattern FIELD = Pattern.compile("(?s)<field(?:\\s[^>]*)?>(.*?)</field>");

    public record Result(String content, boolean changed) {
    }

    public Result apply(String original, SkdDataSetLinkUpsertDsl patch) {
        if (original == null || !original.contains("<DataCompositionSchema")) {
            throw new IllegalArgumentException("Expected root <DataCompositionSchema>");
        }
        Map<String, Set<String>> fields = readDataSets(original);
        List<Requested> requested = validate(patch, fields);
        List<Existing> existing = readLinks(original);
        Map<MappingKey, List<Existing>> byIdentity = new LinkedHashMap<>();
        for (Existing link : existing) {
            byIdentity.computeIfAbsent(mappingKey(link.key(), link.mapping()), ignored -> new ArrayList<>())
                    .add(link);
        }

        String eol = original.contains("\r\n") ? "\r\n" : "\n";
        List<Replacement> replacements = new ArrayList<>();
        StringBuilder additions = new StringBuilder();
        for (Requested request : requested) {
            for (MappingValue mapping : request.mappings()) {
                MappingKey identity = mappingKey(request.key(), mapping);
                List<Existing> matches = byIdentity.getOrDefault(identity, List.of());
                if (matches.size() > 1) {
                    throw new IllegalArgumentException("Duplicate existing mapping identity: " + identity);
                }
                if (matches.size() == 1 && matches.get(0).mapping().equals(mapping)) {
                    continue;
                }
                String rendered = render(request.key(), mapping, eol);
                if (matches.isEmpty()) {
                    additions.append(rendered);
                } else {
                    replacements.add(new Replacement(matches.get(0).start(), matches.get(0).end(), rendered));
                }
            }
        }
        if (!additions.isEmpty()) {
            int insertion = existing.isEmpty() ? lastDataSetEnd(original) : existing.get(existing.size() - 1).end();
            replacements.add(new Replacement(insertion, insertion, additions.toString()));
        }
        if (replacements.isEmpty()) {
            return new Result(original, false);
        }
        replacements.sort((left, right) -> Integer.compare(right.start(), left.start()));
        String result = original;
        for (Replacement replacement : replacements) {
            result = result.substring(0, replacement.start()) + replacement.value()
                    + result.substring(replacement.end());
        }
        return new Result(result, !result.equals(original));
    }

    private static Map<String, Set<String>> readDataSets(String xml) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        Matcher matcher = DATA_SET.matcher(xml);
        while (matcher.find()) {
            String block = matcher.group();
            String name = tagText(block, "name");
            if (blank(name) || result.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate or unnamed dataSet: " + name);
            }
            Set<String> names = new HashSet<>();
            Matcher fieldMatcher = FIELD.matcher(block);
            while (fieldMatcher.find()) {
                String fieldBlock = fieldMatcher.group(1);
                addNotBlank(names, tagText(fieldBlock, "dataPath"));
                addNotBlank(names, tagText(fieldBlock, "field"));
            }
            result.put(name, names);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("SKD contains no top-level dataSet");
        }
        return result;
    }

    private static List<Requested> validate(SkdDataSetLinkUpsertDsl patch,
                                             Map<String, Set<String>> dataSets) {
        if (patch == null || patch.getLinks() == null || patch.getLinks().isEmpty()) {
            throw new IllegalArgumentException("DataSet link upsert requires non-empty links");
        }
        Set<LinkKey> identities = new HashSet<>();
        List<Requested> result = new ArrayList<>();
        for (SkdDataSetLinkUpsertDsl.Link link : patch.getLinks()) {
            if (link == null || blank(link.getSource()) || blank(link.getDestination())) {
                throw new IllegalArgumentException("Every link requires source and destination");
            }
            LinkKey key = new LinkKey(link.getSource(), link.getDestination());
            if (key.source().equals(key.destination())) {
                throw new IllegalArgumentException("Self-link is not allowed: " + key.source());
            }
            if (!identities.add(key)) {
                throw new IllegalArgumentException("Duplicate link identity in payload: " + key);
            }
            Set<String> sourceFields = dataSets.get(key.source());
            Set<String> destinationFields = dataSets.get(key.destination());
            if (sourceFields == null) {
                throw new IllegalArgumentException("Unknown source dataSet: " + key.source());
            }
            if (destinationFields == null) {
                throw new IllegalArgumentException("Unknown destination dataSet: " + key.destination());
            }
            if (link.getMappings() == null || link.getMappings().isEmpty()) {
                throw new IllegalArgumentException("Link requires non-empty mappings: " + key);
            }
            Set<MappingKey> unique = new HashSet<>();
            List<MappingValue> mappings = new ArrayList<>();
            for (SkdDataSetLinkUpsertDsl.Mapping mapping : link.getMappings()) {
                if (mapping == null || blank(mapping.getSourceExpression())
                        || blank(mapping.getDestinationExpression())) {
                    throw new IllegalArgumentException("Every mapping requires sourceExpression and destinationExpression");
                }
                if (!sourceFields.contains(mapping.getSourceExpression())) {
                    throw new IllegalArgumentException("Unknown source field '" + mapping.getSourceExpression()
                            + "' in dataSet '" + key.source() + "'");
                }
                if (!destinationFields.contains(mapping.getDestinationExpression())) {
                    throw new IllegalArgumentException("Unknown destination field '"
                            + mapping.getDestinationExpression() + "' in dataSet '" + key.destination() + "'");
                }
                if (mapping.getParameter() != null && blank(mapping.getParameter())) {
                    throw new IllegalArgumentException("Mapping parameter must not be blank");
                }
                MappingValue value = new MappingValue(mapping.getSourceExpression(),
                        mapping.getDestinationExpression(), mapping.getParameter(),
                        mapping.getParameterListAllowed());
                MappingKey mappingIdentity = mappingKey(key, value);
                if (!unique.add(mappingIdentity)) {
                    throw new IllegalArgumentException("Duplicate mapping identity in payload: "
                            + value.sourceExpression() + " / parameter=" + value.parameter());
                }
                mappings.add(value);
            }
            result.add(new Requested(key, mappings));
        }
        return result;
    }

    private static List<Existing> readLinks(String xml) {
        List<Existing> result = new ArrayList<>();
        Matcher matcher = DATA_SET_LINK.matcher(xml);
        while (matcher.find()) {
            String block = matcher.group();
            LinkKey key = new LinkKey(tagText(block, "sourceDataSet"),
                    tagText(block, "destinationDataSet"));
            MappingValue mapping = new MappingValue(tagText(block, "sourceExpression"),
                    tagText(block, "destinationExpression"), optionalTagText(block, "parameter"),
                    optionalBoolean(block, "parameterListAllowed"));
            result.add(new Existing(matcher.start(), matcher.end(), key, mapping));
        }
        return result;
    }

    private static int lastDataSetEnd(String xml) {
        Matcher matcher = DATA_SET.matcher(xml);
        int end = -1;
        while (matcher.find()) end = matcher.end();
        if (end < 0) throw new IllegalArgumentException("SKD contains no top-level dataSet");
        return end;
    }

    private static String render(LinkKey key, MappingValue mapping, String eol) {
        StringBuilder result = new StringBuilder();
        result.append("\t<dataSetLink>").append(eol)
                    .append("\t\t<sourceDataSet>").append(escape(key.source()))
                    .append("</sourceDataSet>").append(eol)
                    .append("\t\t<destinationDataSet>").append(escape(key.destination()))
                    .append("</destinationDataSet>").append(eol)
                    .append("\t\t<sourceExpression>").append(escape(mapping.sourceExpression()))
                    .append("</sourceExpression>").append(eol)
                    .append("\t\t<destinationExpression>").append(escape(mapping.destinationExpression()))
                    .append("</destinationExpression>").append(eol);
            if (mapping.parameter() != null) {
                result.append("\t\t<parameter>").append(escape(mapping.parameter()))
                        .append("</parameter>").append(eol);
            }
            if (mapping.parameterListAllowed() != null) {
                result.append("\t\t<parameterListAllowed>").append(mapping.parameterListAllowed())
                        .append("</parameterListAllowed>").append(eol);
            }
            result.append("\t</dataSetLink>").append(eol);
        return result.toString();
    }

    private static MappingKey mappingKey(LinkKey key, MappingValue mapping) {
        return new MappingKey(key.source(), key.destination(), mapping.sourceExpression(), mapping.parameter());
    }

    private static String tagText(String block, String tag) {
        String value = optionalTagText(block, tag);
        return value == null ? "" : value;
    }

    private static String optionalTagText(String block, String tag) {
        Matcher matcher = Pattern.compile("(?s)<" + tag + ">(.*?)</" + tag + ">").matcher(block);
        return matcher.find() ? unescape(matcher.group(1).trim()) : null;
    }

    private static Boolean optionalBoolean(String block, String tag) {
        String value = optionalTagText(block, tag);
        if (value == null) return null;
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid boolean " + tag + ": " + value);
        }
        return Boolean.valueOf(value);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static void addNotBlank(Set<String> values, String value) {
        if (!blank(value)) values.add(value);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record LinkKey(String source, String destination) { }
    /** destinationExpression и parameterListAllowed — изменяемые значения mapping. */
    private record MappingKey(String source, String destination, String sourceExpression, String parameter) { }
    private record MappingValue(String sourceExpression, String destinationExpression,
                                String parameter, Boolean parameterListAllowed) { }
    private record Requested(LinkKey key, List<MappingValue> mappings) { }
    private record Existing(int start, int end, LinkKey key, MappingValue mapping) { }
    private record Replacement(int start, int end, String value) { }
}
//--agent TASK-174

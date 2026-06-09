package io.github.onec.xmlgen.oracle;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

public class CanonicalRuleMiner {

    private static final int DEFAULT_MAX_ENUM_VALUES = 24;
    private static final int MAX_ORDER_SIGNATURES_PER_PATH = 16;
    private static final int MAX_TEXT_CHARS = 160;
    private static final int MAX_FACT_SAMPLES = 200;

    public RuleMiningReport mine(Path source, int limit, int minSupport) throws IOException {
        Path sourceRoot = source.toAbsolutePath().normalize();
        List<Path> files = discover(sourceRoot, limit);
        List<DocumentFacts> documents = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        List<CanonicalFact> factSamples = new ArrayList<>();
        int factCount = 0;

        for (Path file : files) {
            try {
                XmlDocument document = parseForMining(file);
                String relative = relativePath(sourceRoot, file);
                String bucket = bucketOf(file, sourceRoot, document);
                DocumentFacts docFacts = new DocumentFacts(file, relative, document, bucket, factSamples);
                extractDocumentFacts(docFacts, document);
                documents.add(docFacts);
                factCount += docFacts.factCount;
            } catch (Exception e) {
                errors.add(new LinkedHashMap<>(Map.of(
                        "file", relativePath(sourceRoot, file),
                        "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()
                )));
            }
        }

        List<RuleCandidate> candidates = mineCandidates(sourceRoot, documents, Math.max(1, minSupport));
        Map<String, RuleMiningReport.BucketSummary> buckets = bucketSummaries(documents, candidates);
        return new RuleMiningReport(
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-'),
                sourceRoot.toString(),
                files.size(),
                documents.size(),
                errors.size(),
                factCount,
                buckets,
                factSamples,
                candidates,
                errors
        );
    }

    private List<Path> discover(Path sourceRoot, int limit) throws IOException {
        List<Path> result = new ArrayList<>();
        if (Files.isRegularFile(sourceRoot)) {
            result.add(sourceRoot);
            return result;
        }
        try (Stream<Path> stream = Files.walk(sourceRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".xml"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(p -> {
                        if (limit <= 0 || result.size() < limit) {
                            result.add(p);
                        }
                    });
        }
        return result;
    }

    private void extractDocumentFacts(DocumentFacts doc, XmlDocument document) {
        String rootPath = "/" + document.getRootElement();
        doc.addFact(rootPath, "root", document.getRootElement(), document.getRootNamespace(),
                Map.of("version", document.getRootAttributes().getOrDefault("version", "")));
        walk(doc, document.getRoot(), rootPath);
        captureWrapperDiscriminators(doc, document);
        addWrapperBodyFact(doc);
    }

    private void captureWrapperDiscriminators(DocumentFacts doc, XmlDocument document) {
        if ("FormWrapper".equals(doc.bucket)) {
            String formType = childTextAt(document.getRoot(), "Form", "Properties", "FormType");
            if (formType != null && !formType.isBlank()) {
                doc.discriminators.put("FormType", formType);
            }
        } else if ("TemplateWrapper".equals(doc.bucket)) {
            String templateType = childTextAt(document.getRoot(), "Template", "Properties", "TemplateType");
            if (templateType != null && !templateType.isBlank()) {
                doc.discriminators.put("TemplateType", templateType);
            }
        }
    }

    private void walk(DocumentFacts doc, XmlNode node, String path) {
        doc.pathCounts.merge(path, 1, Integer::sum);
        doc.paths.add(path);
        doc.namespaces.putIfAbsent(path, node.getNamespace());
        doc.addFact(path, "element", node.getName(), node.getNamespace(), Map.of(
                "attributes", new ArrayList<>(node.getAttributes().keySet()),
                "childCount", node.getChildren().size()
        ));

        if (!node.getAttributes().isEmpty()) {
            Set<String> attrs = doc.attributesByPath.computeIfAbsent(path, ignored -> new LinkedHashSet<>());
            attrs.addAll(node.getAttributes().keySet());
            for (Map.Entry<String, String> entry : node.getAttributes().entrySet()) {
                if (isAttributeValueDomainCandidate(entry.getKey(), entry.getValue())) {
                    addLimitedValue(doc.attributeValues, path + "/@" + entry.getKey(), entry.getValue());
                }
                doc.addFact(path, "attribute", entry.getKey(), entry.getValue(), Map.of());
            }
        }

        List<String> childNames = new ArrayList<>(node.getChildren().size());
        for (XmlNode child : node.getChildren()) {
            childNames.add(child.getName());
        }
        if (childNames.size() > 1) {
            Set<List<String>> signatures = doc.childOrders.computeIfAbsent(path, ignored -> new LinkedHashSet<>());
            if (signatures.size() < MAX_ORDER_SIGNATURES_PER_PATH) {
                signatures.add(List.copyOf(childNames));
            }
        }
        for (String childName : childNames) {
            doc.childNamesByParent.computeIfAbsent(path, ignored -> new LinkedHashSet<>()).add(childName);
        }

        String text = node.getText();
        if (node.getChildren().isEmpty() && text != null && isValueDomainCandidate(node.getName(), text)) {
            addLimitedValue(doc.leafValues, path, text);
            doc.addFact(path, "leaf-text", node.getName(), text, Map.of());
        }

        if ("FormBody".equals(doc.bucket) && "Item".equals(node.getName())) {
            String type = node.childText("Type");
            if (type != null && !type.isBlank()) {
                Set<String> childSet = new LinkedHashSet<>(childNames);
                doc.discriminatorObservations.add(new DiscriminatorObservation(
                        "FormItem.Type", type, "/Form//Item[Type=" + type + "]", childSet, doc.relative));
            }
        }

        for (XmlNode child : node.getChildren()) {
            String childPath = path + "/" + child.getName();
            walk(doc, child, childPath);
        }
    }

    private XmlDocument parseForMining(Path file) throws IOException, XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        try (InputStream input = Files.newInputStream(file)) {
            XMLStreamReader xml = factory.createXMLStreamReader(input, "UTF-8");
            try {
                while (xml.hasNext()) {
                    int event = xml.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        XmlNode root = parseMiningElement(xml);
                        return new XmlDocument(file, false, null, root.getName(), root.getNamespace(),
                                root.getAttributes(), root.getChildren(), root);
                    }
                }
                throw new XMLStreamException("No root element found");
            } finally {
                try {
                    xml.close();
                } catch (XMLStreamException ignored) {
                    // ignore close errors after a parse failure
                }
            }
        }
    }

    private XmlNode parseMiningElement(XMLStreamReader reader) throws XMLStreamException {
        XmlNode.Builder builder = XmlNode.builder()
                .name(reader.getLocalName())
                .prefix(reader.getPrefix())
                .namespace(reader.getNamespaceURI())
                .line(reader.getLocation().getLineNumber());

        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String prefix = reader.getAttributePrefix(i);
            String localName = reader.getAttributeLocalName(i);
            String key = prefix != null && !prefix.isEmpty() ? prefix + ":" + localName : localName;
            builder.attribute(key, reader.getAttributeValue(i));
        }
        for (int i = 0; i < reader.getNamespaceCount(); i++) {
            String prefix = reader.getNamespacePrefix(i);
            String uri = reader.getNamespaceURI(i);
            builder.attribute(prefix == null || prefix.isEmpty() ? "xmlns" : "xmlns:" + prefix, uri);
        }

        int textChars = 0;
        boolean textTruncated = false;
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> builder.addChild(parseMiningElement(reader));
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    int len = reader.getTextLength();
                    if (!textTruncated && textChars + len <= MAX_TEXT_CHARS) {
                        builder.appendText(reader.getText());
                        textChars += len;
                    } else {
                        textTruncated = true;
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    return builder.build();
                }
                default -> {
                    // comments, processing instructions and whitespace-only metadata are irrelevant for mining
                }
            }
        }
        return builder.build();
    }

    private boolean isValueDomainCandidate(String elementName, String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 80) {
            return false;
        }
        if (Set.of("Type", "View", "Use", "AutoRecord", "TemplateType", "CommandParameterUseMode",
                "ExtendedPresentation", "CheckType", "EditMode", "Representation", "FillChecking")
                .contains(elementName)) {
            return true;
        }
        return trimmed.equals("true") || trimmed.equals("false")
                || trimmed.matches("[A-Za-z][A-Za-z0-9_]*")
                || trimmed.matches("[А-ЯЁа-яёA-Za-z_][А-ЯЁа-яёA-Za-z0-9_]*");
    }

    private boolean isAttributeValueDomainCandidate(String attrName, String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 120) {
            return false;
        }
        return Set.of("version", "xsi:type", "type", "format", "xmlns", "xmlns:xsi", "xmlns:xr", "xmlns:xs",
                "xmlns:v8", "xmlns:app").contains(attrName);
    }

    private void addLimitedValue(Map<String, Set<String>> valuesByPath, String path, String value) {
        Set<String> values = valuesByPath.computeIfAbsent(path, ignored -> new LinkedHashSet<>());
        if (values.size() <= DEFAULT_MAX_ENUM_VALUES) {
            values.add(value);
        }
    }

    private void addWrapperBodyFact(DocumentFacts doc) {
        String rel = doc.relative.replace('\\', '/');
        if ("FormWrapper".equals(doc.bucket)) {
            String base = rel.substring(0, rel.length() - ".xml".length());
            doc.expectedLinkedBody = Path.of(base, "Ext", "Form.xml").toString().replace('\\', '/');
            doc.addFact("/filesystem", "linked-body", "Form.xml", doc.expectedLinkedBody, Map.of());
        } else if ("TemplateWrapper".equals(doc.bucket)) {
            String base = rel.substring(0, rel.length() - ".xml".length());
            doc.expectedLinkedBody = Path.of(base, "Ext", "Template.xml").toString().replace('\\', '/');
            doc.addFact("/filesystem", "linked-body", "Template.xml", doc.expectedLinkedBody, Map.of());
        }
    }

    private List<RuleCandidate> mineCandidates(Path sourceRoot, List<DocumentFacts> documents, int minSupport) {
        Map<String, List<DocumentFacts>> byBucket = new TreeMap<>();
        for (DocumentFacts doc : documents) {
            byBucket.computeIfAbsent(doc.bucket, ignored -> new ArrayList<>()).add(doc);
        }

        List<RuleCandidate> result = new ArrayList<>();
        int[] seq = {1};
        for (Map.Entry<String, List<DocumentFacts>> entry : byBucket.entrySet()) {
            String bucket = entry.getKey();
            List<DocumentFacts> docs = entry.getValue();
            if (docs.size() < minSupport) {
                continue;
            }
            result.addAll(rootCandidates(seq, bucket, docs, minSupport));
            result.addAll(cardinalityCandidates(seq, bucket, docs, minSupport));
            result.addAll(requiredChildCandidates(seq, bucket, docs, minSupport));
            result.addAll(requiredAttributeCandidates(seq, bucket, docs, minSupport));
            result.addAll(orderCandidates(seq, bucket, docs, minSupport));
            result.addAll(valueDomainCandidates(seq, bucket, docs, minSupport));
            result.addAll(wrapperBodyCandidates(seq, bucket, docs, sourceRoot, minSupport));
            result.addAll(discriminatorCandidates(seq, bucket, docs, sourceRoot, minSupport));
        }
        result.sort(Comparator.comparing(RuleCandidate::bucket)
                .thenComparing(RuleCandidate::kind)
                .thenComparing(RuleCandidate::subject));
        return result;
    }

    private List<RuleCandidate> rootCandidates(int[] seq, String bucket, List<DocumentFacts> docs, int minSupport) {
        Map<String, List<DocumentFacts>> byRoot = new LinkedHashMap<>();
        for (DocumentFacts doc : docs) {
            String key = doc.rootElement + "|" + doc.rootNamespace;
            byRoot.computeIfAbsent(key, ignored -> new ArrayList<>()).add(doc);
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (Map.Entry<String, List<DocumentFacts>> entry : byRoot.entrySet()) {
            if (entry.getValue().size() == docs.size()) {
                String[] parts = entry.getKey().split("\\|", -1);
                result.add(candidate(seq, "ROOT_CONTRACT", bucket, entry.getValue().size(), docs.size(),
                        "/" + parts[0], "root element and namespace are stable",
                        Map.of("root", parts[0], "namespace", parts[1]),
                        examples(entry.getValue(), minSupport)));
            }
        }
        return result;
    }

    private List<RuleCandidate> cardinalityCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                      int minSupport) {
        Set<String> allPaths = new TreeSetLike();
        for (DocumentFacts doc : docs) {
            allPaths.addAll(doc.paths);
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (String path : allPaths) {
            int support = 0;
            int min = Integer.MAX_VALUE;
            int max = 0;
            for (DocumentFacts doc : docs) {
                int count = doc.pathCounts.getOrDefault(path, 0);
                if (count > 0) {
                    support++;
                    min = Math.min(min, count);
                    max = Math.max(max, count);
                } else {
                    min = 0;
                }
            }
            if (support < minSupport) {
                continue;
            }
            if (support == docs.size() && min == 1 && max == 1) {
                result.add(candidate(seq, "CARDINALITY", bucket, support, docs.size(), path,
                        "path appears exactly once in every document of the bucket",
                        Map.of("min", min, "max", max, "cardinality", "exactly_one"),
                        examples(docsWithPath(docs, path), minSupport)));
            } else if (max > 1) {
                result.add(candidate(seq, "CARDINALITY", bucket, support, docs.size(), path,
                        "path is repeatable in this bucket",
                        Map.of("min", min == Integer.MAX_VALUE ? 0 : min, "max", max, "cardinality", "many"),
                        examples(docsWithPath(docs, path), minSupport)));
            }
        }
        return result;
    }

    private List<RuleCandidate> requiredChildCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                        int minSupport) {
        Set<String> parentPaths = new TreeSetLike();
        for (DocumentFacts doc : docs) {
            parentPaths.addAll(doc.childNamesByParent.keySet());
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (String parentPath : parentPaths) {
            List<DocumentFacts> parentsPresent = docs.stream()
                    .filter(d -> d.childNamesByParent.containsKey(parentPath))
                    .toList();
            if (parentsPresent.size() < minSupport) {
                continue;
            }
            Set<String> intersection = null;
            for (DocumentFacts doc : parentsPresent) {
                Set<String> names = doc.childNamesByParent.get(parentPath);
                if (intersection == null) {
                    intersection = new LinkedHashSet<>(names);
                } else {
                    intersection.retainAll(names);
                }
            }
            if (intersection == null) {
                continue;
            }
            for (String child : intersection) {
                result.add(candidate(seq, "REQUIRED_CHILD", bucket, parentsPresent.size(), docs.size(),
                        parentPath + "/" + child,
                        "child element is present whenever the parent path appears",
                        Map.of("parent", parentPath, "child", child),
                        examples(parentsPresent, minSupport)));
            }
        }
        return result;
    }

    private List<RuleCandidate> requiredAttributeCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                           int minSupport) {
        Set<String> paths = new TreeSetLike();
        for (DocumentFacts doc : docs) {
            paths.addAll(doc.attributesByPath.keySet());
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (String path : paths) {
            List<DocumentFacts> present = docs.stream().filter(d -> d.attributesByPath.containsKey(path)).toList();
            if (present.size() < minSupport) {
                continue;
            }
            Set<String> intersection = null;
            for (DocumentFacts doc : present) {
                if (intersection == null) {
                    intersection = new LinkedHashSet<>(doc.attributesByPath.get(path));
                } else {
                    intersection.retainAll(doc.attributesByPath.get(path));
                }
            }
            if (intersection == null) {
                continue;
            }
            for (String attr : intersection) {
                result.add(candidate(seq, "REQUIRED_ATTRIBUTE", bucket, present.size(), docs.size(),
                        path + "/@" + attr,
                        "attribute is present whenever the element path appears",
                        Map.of("path", path, "attribute", attr),
                        examples(present, minSupport)));
            }
        }
        return result;
    }

    private List<RuleCandidate> orderCandidates(int[] seq, String bucket, List<DocumentFacts> docs, int minSupport) {
        Set<String> paths = new TreeSetLike();
        for (DocumentFacts doc : docs) {
            paths.addAll(doc.childOrders.keySet());
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (String path : paths) {
            Map<List<String>, Integer> signatures = new LinkedHashMap<>();
            List<DocumentFacts> present = new ArrayList<>();
            for (DocumentFacts doc : docs) {
                Set<List<String>> orders = doc.childOrders.get(path);
                if (orders == null || orders.isEmpty()) {
                    continue;
                }
                present.add(doc);
                for (List<String> order : orders) {
                    signatures.merge(order, 1, Integer::sum);
                }
            }
            if (present.size() < minSupport || signatures.size() != 1) {
                continue;
            }
            List<String> order = signatures.keySet().iterator().next();
            result.add(candidate(seq, "CHILD_ORDER", bucket, present.size(), docs.size(), path,
                    "child element order is stable for this parent path",
                    Map.of("order", order),
                    examples(present, minSupport)));
        }
        return result;
    }

    private List<RuleCandidate> valueDomainCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                      int minSupport) {
        Map<String, Set<String>> valuesByPath = new TreeMap<>();
        Map<String, Integer> supportByPath = new HashMap<>();
        for (DocumentFacts doc : docs) {
            for (Map.Entry<String, Set<String>> entry : doc.leafValues.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    continue;
                }
                supportByPath.merge(entry.getKey(), 1, Integer::sum);
                valuesByPath.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
            for (Map.Entry<String, Set<String>> entry : doc.attributeValues.entrySet()) {
                supportByPath.merge(entry.getKey(), 1, Integer::sum);
                valuesByPath.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<>()).addAll(entry.getValue());
            }
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : valuesByPath.entrySet()) {
            int support = supportByPath.getOrDefault(entry.getKey(), 0);
            Set<String> values = entry.getValue();
            if (support < minSupport || values.isEmpty() || values.size() > DEFAULT_MAX_ENUM_VALUES) {
                continue;
            }
            if (looksLikeFreeText(values)) {
                continue;
            }
            result.add(candidate(seq, "VALUE_DOMAIN", bucket, support, docs.size(), entry.getKey(),
                    "observed finite value domain",
                    Map.of("values", new ArrayList<>(values), "maxValues", DEFAULT_MAX_ENUM_VALUES),
                    examples(docs, minSupport)));
        }
        return result;
    }

    private List<RuleCandidate> wrapperBodyCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                      Path sourceRoot, int minSupport) {
        if (!"FormWrapper".equals(bucket) && !"TemplateWrapper".equals(bucket)) {
            return List.of();
        }
        List<DocumentFacts> checked = docs.stream().filter(d -> d.expectedLinkedBody != null).toList();
        if (checked.size() < minSupport) {
            return List.of();
        }
        int exists = 0;
        for (DocumentFacts doc : checked) {
            if (Files.exists(sourceRoot.resolve(doc.expectedLinkedBody))) {
                exists++;
            }
        }
        String body = "FormWrapper".equals(bucket) ? "Ext/Form.xml" : "Ext/Template.xml";
        return List.of(candidate(seq, "LINKED_BODY", bucket, exists, checked.size(), body,
                "wrapper has a linked body file in canonical Designer layout",
                Map.of("body", body, "missing", checked.size() - exists),
                examples(checked, minSupport)));
    }

    private List<RuleCandidate> discriminatorCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                        Path sourceRoot, int minSupport) {
        if ("FormWrapper".equals(bucket)) {
            return formTypeBodyCandidates(seq, docs, sourceRoot, minSupport);
        }
        if ("TemplateWrapper".equals(bucket)) {
            return templateTypeBodyCandidates(seq, docs, sourceRoot, minSupport);
        }
        if ("FormBody".equals(bucket)) {
            return formItemTypeCandidates(seq, bucket, docs, minSupport);
        }
        return List.of();
    }

    private List<RuleCandidate> formTypeBodyCandidates(int[] seq, List<DocumentFacts> docs, Path sourceRoot,
                                                       int minSupport) {
        Map<String, List<DocumentFacts>> byType = groupByDiscriminator(docs, "FormType");
        List<RuleCandidate> result = new ArrayList<>();
        for (Map.Entry<String, List<DocumentFacts>> entry : byType.entrySet()) {
            if (entry.getValue().size() < minSupport) {
                continue;
            }
            int withBody = 0;
            for (DocumentFacts doc : entry.getValue()) {
                if (doc.expectedLinkedBody != null && Files.exists(sourceRoot.resolve(doc.expectedLinkedBody))) {
                    withBody++;
                }
            }
            String expected = withBody == entry.getValue().size() ? "Ext/Form.xml" : "none";
            int support = "none".equals(expected) ? entry.getValue().size() - withBody : withBody;
            result.add(candidate(seq, "DISCRIMINATOR_LINKED_BODY", "FormWrapper", support,
                    entry.getValue().size(), "FormType=" + entry.getKey() + " -> " + expected,
                    "form body linkage depends on FormType",
                    Map.of("discriminator", "FormType", "value", entry.getKey(), "body", expected,
                            "withBody", withBody, "withoutBody", entry.getValue().size() - withBody),
                    examples(entry.getValue(), minSupport)));
        }
        return result;
    }

    private List<RuleCandidate> templateTypeBodyCandidates(int[] seq, List<DocumentFacts> docs, Path sourceRoot,
                                                           int minSupport) {
        Map<String, List<DocumentFacts>> byType = groupByDiscriminator(docs, "TemplateType");
        List<RuleCandidate> result = new ArrayList<>();
        for (Map.Entry<String, List<DocumentFacts>> entry : byType.entrySet()) {
            if (entry.getValue().size() < minSupport) {
                continue;
            }
            Map<String, Integer> bodyCounts = new LinkedHashMap<>();
            for (DocumentFacts doc : entry.getValue()) {
                bodyCounts.merge(templateBodyKind(sourceRoot, doc), 1, Integer::sum);
            }
            Map.Entry<String, Integer> best = bodyCounts.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(Map.entry("none", 0));
            result.add(candidate(seq, "DISCRIMINATOR_LINKED_BODY", "TemplateWrapper", best.getValue(),
                    entry.getValue().size(), "TemplateType=" + entry.getKey() + " -> " + best.getKey(),
                    "template body file depends on TemplateType",
                    Map.of("discriminator", "TemplateType", "value", entry.getKey(), "body", best.getKey(),
                            "bodyCounts", bodyCounts),
                    examples(entry.getValue(), minSupport)));
        }
        return result;
    }

    private String templateBodyKind(Path sourceRoot, DocumentFacts doc) {
        String rel = doc.relative.replace('\\', '/');
        String base = rel.substring(0, rel.length() - ".xml".length());
        Path ext = sourceRoot.resolve(base).resolve("Ext");
        if (Files.exists(ext.resolve("Template.xml"))) {
            return "Ext/Template.xml";
        }
        if (Files.exists(ext.resolve("Template.txt"))) {
            return "Ext/Template.txt";
        }
        if (Files.exists(ext.resolve("Template.bin"))) {
            return "Ext/Template.bin";
        }
        return "none";
    }

    private List<RuleCandidate> formItemTypeCandidates(int[] seq, String bucket, List<DocumentFacts> docs,
                                                       int minSupport) {
        Map<String, DiscriminatorAggregate> aggregates = new TreeMap<>();
        for (DocumentFacts doc : docs) {
            for (DiscriminatorObservation observation : doc.discriminatorObservations) {
                aggregates.computeIfAbsent(observation.name + "=" + observation.value,
                        ignored -> new DiscriminatorAggregate(observation.name, observation.value,
                                observation.subject))
                        .add(observation);
            }
        }
        List<RuleCandidate> result = new ArrayList<>();
        for (DiscriminatorAggregate aggregate : aggregates.values()) {
            if (aggregate.total < minSupport) {
                continue;
            }
            Set<String> required = aggregate.requiredChildren();
            if (required.isEmpty()) {
                continue;
            }
            result.add(candidate(seq, "DISCRIMINATOR_NODE_CONTRACT", bucket, aggregate.total, aggregate.total,
                    aggregate.subject, "form item child contract depends on Type discriminator",
                    Map.of("discriminator", aggregate.name, "value", aggregate.value,
                            "requiredChildren", new ArrayList<>(required), "occurrences", aggregate.total),
                    aggregate.examples.stream().limit(Math.max(2, minSupport)).toList()));
        }
        return result;
    }

    private Map<String, List<DocumentFacts>> groupByDiscriminator(List<DocumentFacts> docs, String name) {
        Map<String, List<DocumentFacts>> result = new TreeMap<>();
        for (DocumentFacts doc : docs) {
            String value = doc.discriminators.get(name);
            if (value != null && !value.isBlank()) {
                result.computeIfAbsent(value, ignored -> new ArrayList<>()).add(doc);
            }
        }
        return result;
    }

    private RuleCandidate candidate(int[] seq, String kind, String bucket, int support, int total, String subject,
                                    String rule, Map<String, Object> details, List<String> examples) {
        RuleConfidence confidence;
        if (support == total && support >= 5) {
            confidence = RuleConfidence.OBSERVED_HIGH;
        } else if (support == total) {
            confidence = RuleConfidence.OBSERVED_LOW;
        } else if (support >= 5) {
            confidence = RuleConfidence.CONDITIONAL;
        } else {
            confidence = RuleConfidence.SUSPICIOUS;
        }
        return new RuleCandidate("MINE-%04d".formatted(seq[0]++), kind, bucket, confidence, support, total,
                subject, rule, details, examples);
    }

    private List<DocumentFacts> docsWithPath(List<DocumentFacts> docs, String path) {
        return docs.stream().filter(d -> d.paths.contains(path)).toList();
    }

    private List<String> examples(List<DocumentFacts> docs, int limit) {
        return docs.stream()
                .map(d -> d.relative)
                .sorted()
                .limit(Math.max(2, limit))
                .toList();
    }

    private Map<String, RuleMiningReport.BucketSummary> bucketSummaries(List<DocumentFacts> documents,
                                                                        List<RuleCandidate> candidates) {
        Map<String, List<DocumentFacts>> docsByBucket = new TreeMap<>();
        Map<String, Integer> candidatesByBucket = new HashMap<>();
        for (DocumentFacts doc : documents) {
            docsByBucket.computeIfAbsent(doc.bucket, ignored -> new ArrayList<>()).add(doc);
        }
        for (RuleCandidate candidate : candidates) {
            candidatesByBucket.merge(candidate.bucket(), 1, Integer::sum);
        }
        Map<String, RuleMiningReport.BucketSummary> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<DocumentFacts>> entry : docsByBucket.entrySet()) {
            int facts = entry.getValue().stream().mapToInt(d -> d.factCount).sum();
            result.put(entry.getKey(), new RuleMiningReport.BucketSummary(
                    entry.getValue().size(),
                    facts,
                    candidatesByBucket.getOrDefault(entry.getKey(), 0),
                    examples(entry.getValue(), 3)
            ));
        }
        return result;
    }

    private boolean looksLikeFreeText(Set<String> values) {
        for (String value : values) {
            if (value.length() > 80) {
                return true;
            }
            if (value.contains(" ") && value.length() > 30) {
                return true;
            }
            if (value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
                return true;
            }
        }
        return false;
    }

    private String bucketOf(Path file, Path sourceRoot, XmlDocument doc) {
        String rel = relativePath(sourceRoot, file).replace('\\', '/');
        String root = doc.getRootElement();
        if ("MetaDataObject".equals(root)) {
            if (rel.matches(".*/Forms/[^/]+\\.xml$")) {
                return "FormWrapper";
            }
            if (rel.matches(".*/Templates/[^/]+\\.xml$")) {
                return "TemplateWrapper";
            }
            XmlNode typeNode = firstElementChild(doc.getRoot());
            return typeNode == null ? "MetaDataObject" : "MetaDataObject." + typeNode.getName();
        }
        if ("Form".equals(root)) {
            return "FormBody";
        }
        if ("PredefinedData".equals(root)) {
            String type = doc.getRootAttributes().getOrDefault("xsi:type", "");
            return type.isBlank() ? "PredefinedData" : "PredefinedData." + type;
        }
        if ("ExtPicture".equals(root)) {
            return "PictureBody";
        }
        if ("GraphicalSchema".equals(root)) {
            return "FlowchartBody";
        }
        if ("document".equals(root)) {
            return "MxlBody";
        }
        return root;
    }

    private XmlNode firstElementChild(XmlNode root) {
        return root.getChildren().isEmpty() ? null : root.getChildren().get(0);
    }

    private String childTextAt(XmlNode node, String... path) {
        XmlNode current = node;
        for (String name : path) {
            if (current == null) {
                return null;
            }
            current = current.child(name);
        }
        return current == null ? null : current.getText();
    }

    private String relativePath(Path sourceRoot, Path file) {
        Path absolute = file.toAbsolutePath().normalize();
        if (Files.isRegularFile(sourceRoot)) {
            return absolute.getFileName().toString();
        }
        try {
            return sourceRoot.relativize(absolute).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return absolute.toString();
        }
    }

    private static final class TreeSetLike extends java.util.TreeSet<String> {
    }

    private static final class DocumentFacts {
        final Path file;
        final String relative;
        final String rootElement;
        final String rootNamespace;
        final String bucket;
        int factCount = 0;
        final Map<String, Integer> pathCounts = new LinkedHashMap<>();
        final Set<String> paths = new LinkedHashSet<>();
        final Map<String, String> namespaces = new LinkedHashMap<>();
        final Map<String, Set<String>> childNamesByParent = new LinkedHashMap<>();
        final Map<String, Set<List<String>>> childOrders = new LinkedHashMap<>();
        final Map<String, Set<String>> attributesByPath = new LinkedHashMap<>();
        final Map<String, Set<String>> attributeValues = new LinkedHashMap<>();
        final Map<String, Set<String>> leafValues = new LinkedHashMap<>();
        final Map<String, String> discriminators = new LinkedHashMap<>();
        final List<DiscriminatorObservation> discriminatorObservations = new ArrayList<>();
        final List<CanonicalFact> factSamples;
        String expectedLinkedBody;

        DocumentFacts(Path file, String relative, XmlDocument document, String bucket,
                      List<CanonicalFact> factSamples) {
            this.file = file;
            this.relative = relative;
            this.rootElement = document.getRootElement();
            this.rootNamespace = document.getRootNamespace();
            this.bucket = bucket;
            this.factSamples = factSamples;
        }

        void addFact(String path, String kind, String name, String value, Map<String, Object> context) {
            factCount++;
            if (factSamples.size() < MAX_FACT_SAMPLES) {
                factSamples.add(new CanonicalFact(bucket, relative, path, kind, name, value, context));
            }
        }
    }

    private record DiscriminatorObservation(
            String name,
            String value,
            String subject,
            Set<String> children,
            String example
    ) {}

    private static final class DiscriminatorAggregate {
        final String name;
        final String value;
        final String subject;
        final List<Set<String>> childSets = new ArrayList<>();
        final Set<String> examples = new LinkedHashSet<>();
        int total = 0;

        DiscriminatorAggregate(String name, String value, String subject) {
            this.name = name;
            this.value = value;
            this.subject = subject;
        }

        void add(DiscriminatorObservation observation) {
            total++;
            childSets.add(observation.children);
            examples.add(observation.example);
        }

        Set<String> requiredChildren() {
            Set<String> result = null;
            for (Set<String> children : childSets) {
                if (result == null) {
                    result = new LinkedHashSet<>(children);
                } else {
                    result.retainAll(children);
                }
            }
            return result == null ? Set.of() : result;
        }
    }
}

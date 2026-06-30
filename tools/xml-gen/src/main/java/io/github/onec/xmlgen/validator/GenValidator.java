package io.github.onec.xmlgen.validator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Общие проверки (GEN-001..006), применимые ко всем типам объектов.
 * <p>
 * Проверяет:
 * <ul>
 *   <li>GEN-001: XML well-formed (проверяется до вызова — при парсинге)</li>
 *   <li>GEN-002: XML declaration (encoding="UTF-8")</li>
 *   <li>GEN-003: BOM-политика (Designer metadata → BOM, остальное → без BOM)</li>
 *   <li>GEN-004: Root element соответствует типу объекта</li>
 *   <li>GEN-005: Namespace root-элемента</li>
 *   <li>GEN-006: UUID-атрибуты — валидный формат</li>
 * </ul>
 */
public class GenValidator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern XML_DECL_ATTR_PATTERN = Pattern.compile(
            "(\\w+)\\s*=\\s*(['\"])(.*?)\\2"
    );

    // Ожидаемые root-элементы и namespace-ы для каждого типа
    private static final java.util.Map<String, java.util.List<RootExpectation>> TYPE_EXPECTATIONS = java.util.Map.of(
            "role", java.util.List.of(new RootExpectation("Rights", "http://v8.1c.ru/8.2/roles")),
            "form", java.util.List.of(new RootExpectation("Form", "http://v8.1c.ru/8.3/xcf/logform")),
            "skd", java.util.List.of(new RootExpectation("DataCompositionSchema", "http://v8.1c.ru/8.1/data-composition-system/schema")),
            "mxl", java.util.List.of(new RootExpectation("document", "http://v8.1c.ru/8.2/data/spreadsheet")),
            "config", java.util.List.of(new RootExpectation("MetaDataObject", "http://v8.1c.ru/8.3/MDClasses")),
            "epf", java.util.List.of(
                    new RootExpectation("MetaDataObject", "http://v8.1c.ru/8.3/MDClasses"),
                    new RootExpectation("ExternalDataProcessor", "http://v8.1c.ru/8.3/MDClasses"),
                    new RootExpectation("ExternalReport", "http://v8.1c.ru/8.3/MDClasses")),
            "client-interface", java.util.List.of(new RootExpectation(
                    "ClientApplicationInterface", PlatformXsdFacts.NS_MANAGED_APPLICATION_CORE))
    );

    private final MetadataTypeValidator metadataValidator;

    public GenValidator() {
        this(null);
    }

    public GenValidator(MetadataTypeValidator metadataValidator) {
        this.metadataValidator = metadataValidator;
    }

    /**
     * Выполнить общие проверки GEN-001..006 + SEM-001 (types).
     *
     * @param document      распарсенный документ
     * @param objectType    тип объекта ("form", "role", "skd", "mxl", "epf")
     * @param expectBom     ожидается ли BOM для данного файла
     * @return список проблем
     */
    public List<ValidationIssue> validate(XmlDocument document, String objectType, boolean expectBom) {
        return validate(document, objectType, expectBom, "designer", true);
    }

    public List<ValidationIssue> validate(XmlDocument document, String objectType, boolean expectBom,
                                          String format, boolean validateSemanticTypes) {
        List<ValidationIssue> issues = new ArrayList<>();

        // GEN-001: XML well-formed — уже проверено при парсинге (XmlStructureReader бросит XmlParseException)

        // GEN-002: XML declaration и UTF-8 encoding
        validateXmlDeclaration(document, issues);

        // GEN-003: BOM-политика
        if (expectBom && !document.isHasBom()) {
            issues.add(ValidationIssue.error("GEN-003",
                    "Expected UTF-8 BOM for Designer metadata file, but BOM not found",
                    0, "/"));
        }
        if (!expectBom && document.isHasBom()) {
            issues.add(ValidationIssue.warning("GEN-003",
                    "Unexpected UTF-8 BOM in this file type",
                    0, "/"));
        }

        // GEN-004 + GEN-005: Root element и namespace
        java.util.List<RootExpectation> expected = expectationsFor(objectType, format);
        if (expected != null) {
            RootExpectation matchedRoot = expected.stream()
                    .filter(e -> e.rootElement.equals(document.getRootElement()))
                    .findFirst()
                    .orElse(null);
            if (matchedRoot == null) {
                issues.add(ValidationIssue.error("GEN-004",
                        "Expected root element " + expectedRoots(expected)
                                + ", found '" + document.getRootElement() + "'",
                        1, "/"));
            }
            String actualNs = document.getRootNamespace();
            boolean namespaceMatches = expected.stream()
                    .filter(e -> matchedRoot == null || e.rootElement.equals(document.getRootElement()))
                    .anyMatch(e -> e.namespace.equals(actualNs));
            if (!namespaceMatches) {
                issues.add(ValidationIssue.error("GEN-005",
                        "Expected namespace " + expectedNamespaces(expected, matchedRoot)
                                + ", found '" + (actualNs != null ? actualNs : "(none)") + "'",
                        1, "/"));
            }
        }

        // GEN-006: UUID-атрибуты
        checkUuids(document.getRoot(), "/", issues);

        // SEM-001: Проверка типов (если включено)
        if (metadataValidator != null && validateSemanticTypes) {
            checkTypes(document.getRoot(), "/", issues);
        }

        return issues;
    }

    private java.util.List<RootExpectation> expectationsFor(String objectType, String format) {
        if ("form".equals(objectType) && "edt".equals(format)) {
            return java.util.List.of(new RootExpectation("Form", "http://g5.1c.ru/v8/dt/form"));
        }
        return TYPE_EXPECTATIONS.get(objectType);
    }

    private void validateXmlDeclaration(XmlDocument document, List<ValidationIssue> issues) {
        String declaration = document.getXmlDeclaration();
        if (declaration == null || declaration.isBlank()) {
            issues.add(ValidationIssue.error("GEN-002",
                    "Missing XML declaration; expected <?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                    1, "/"));
            return;
        }

        java.util.Map<String, String> attrs = new java.util.HashMap<>();
        java.util.regex.Matcher matcher = XML_DECL_ATTR_PATTERN.matcher(declaration);
        while (matcher.find()) {
            attrs.put(matcher.group(1), matcher.group(3));
        }

        String version = attrs.get("version");
        String encoding = attrs.get("encoding");
        if (!"1.0".equals(version) || encoding == null || !"UTF-8".equalsIgnoreCase(encoding)) {
            issues.add(ValidationIssue.error("GEN-002",
                    "XML declaration must specify version=\"1.0\" and encoding=\"UTF-8\"",
                    1, "/"));
        }
    }
    
    private void checkTypes(XmlNode node, String path, List<ValidationIssue> issues) {
        // Если это элемент <Type>, <v8:Type> или <v8:TypeSet> - проверяем содержимое
        if ("Type".equalsIgnoreCase(node.getName())
                || "TypeSet".equalsIgnoreCase(node.getName())
                || "v8:Type".equalsIgnoreCase(node.getName())
                || "v8:TypeSet".equalsIgnoreCase(node.getName())) {
            String typeName = node.getText();
            if (typeName != null && !typeName.isEmpty()) {
                issues.addAll(metadataValidator.validateType(typeName, node, path));
            }
        }
        
        // Рекурсия
        int idx = 0;
        for (XmlNode child : node.getChildren()) {
            idx++;
            checkTypes(child, path + child.getName() + "[" + idx + "]/", issues);
        }
    }

    private String expectedRoots(java.util.List<RootExpectation> expectations) {
        return expectations.stream()
                .map(e -> "'" + e.rootElement + "'")
                .distinct()
                .collect(java.util.stream.Collectors.joining(" or "));
    }

    private String expectedNamespaces(java.util.List<RootExpectation> expectations,
                                      RootExpectation matchedRoot) {
        return expectations.stream()
                .filter(e -> matchedRoot == null || e.rootElement.equals(matchedRoot.rootElement))
                .map(e -> "'" + e.namespace + "'")
                .distinct()
                .collect(java.util.stream.Collectors.joining(" or "));
    }

    /**
     * Рекурсивно проверяет UUID-атрибуты (uuid, id с форматом UUID).
     */
    private void checkUuids(XmlNode node, String path, List<ValidationIssue> issues) {
        String uuid = node.attr("uuid");
        if (uuid != null && !UUID_PATTERN.matcher(uuid).matches()) {
            issues.add(ValidationIssue.warning("GEN-006",
                    "Invalid UUID format: '" + uuid + "'",
                    node.getLine(), path + "@uuid"));
        }

        int idx = 0;
        for (XmlNode child : node.getChildren()) {
            idx++;
            String childPath = path + child.getName() + "[" + idx + "]/";

            // Проверяем атрибут uuid у дочерних
            String childUuid = child.attr("uuid");
            if (childUuid != null && !UUID_PATTERN.matcher(childUuid).matches()) {
                issues.add(ValidationIssue.warning("GEN-006",
                        "Invalid UUID format: '" + childUuid + "'",
                        child.getLine(), childPath + "@uuid"));
            }

            // Рекурсивно — но неглубоко, чтобы не тормозить на огромных формах
            // UUID обычно на верхних уровнях
            if (child.getChildren().size() < 1000) {
                checkUuids(child, childPath, issues);
            }
        }
    }

    /**
     * Ожидаемый root-element и namespace для типа объекта.
     */
    private static class RootExpectation {
        final String rootElement;
        final String namespace;

        RootExpectation(String rootElement, String namespace) {
            this.rootElement = rootElement;
            this.namespace = namespace;
        }
    }
}

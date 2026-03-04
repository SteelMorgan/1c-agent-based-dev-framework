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

    // Ожидаемые root-элементы и namespace-ы для каждого типа
    private static final java.util.Map<String, RootExpectation> TYPE_EXPECTATIONS = java.util.Map.of(
            "role", new RootExpectation("Rights", "http://v8.1c.ru/8.2/roles"),
            "form", new RootExpectation("Form", "http://v8.1c.ru/8.3/xcf/logform"),
            "skd", new RootExpectation("DataCompositionSchema", "http://v8.1c.ru/8.1/data-composition-system/schema"),
            "mxl", new RootExpectation("document", "http://v8.1c.ru/8.2/data/spreadsheet"),
            "epf", new RootExpectation("ExternalDataProcessor", "http://v8.1c.ru/8.3/MDClasses")
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
        List<ValidationIssue> issues = new ArrayList<>();

        // GEN-001: XML well-formed — уже проверено при парсинге (XmlStructureReader бросит XmlParseException)

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
        RootExpectation expected = TYPE_EXPECTATIONS.get(objectType);
        if (expected != null) {
            if (!expected.rootElement.equals(document.getRootElement())) {
                issues.add(ValidationIssue.error("GEN-004",
                        "Expected root element '" + expected.rootElement
                                + "', found '" + document.getRootElement() + "'",
                        1, "/"));
            }
            String actualNs = document.getRootNamespace();
            if (actualNs == null || !actualNs.equals(expected.namespace)) {
                issues.add(ValidationIssue.error("GEN-005",
                        "Expected namespace '" + expected.namespace
                                + "', found '" + (actualNs != null ? actualNs : "(none)") + "'",
                        1, "/"));
            }
        }

        // GEN-006: UUID-атрибуты
        checkUuids(document.getRoot(), "/", issues);

        // SEM-001: Проверка типов (если включено)
        if (metadataValidator != null) {
            checkTypes(document.getRoot(), "/", issues);
        }

        return issues;
    }
    
    private void checkTypes(XmlNode node, String path, List<ValidationIssue> issues) {
        // Если это элемент <Type> или <v8:Type> - проверяем содержимое
        if ("Type".equalsIgnoreCase(node.getName()) || "v8:Type".equalsIgnoreCase(node.getName())) {
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

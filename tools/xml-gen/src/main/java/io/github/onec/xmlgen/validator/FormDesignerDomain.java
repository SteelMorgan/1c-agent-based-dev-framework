package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.dsl.FormDsl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

//++agent TASK-174 [15.07.2026 23:35:00] XG-109 BUG-013
/**
 * Reject-only public domain Designer form properties and excluded standard commands.
 * The same table validates DSL before serialization and generated Form.xml afterwards.
 */
public final class FormDesignerDomain {

    public static final String PROPERTY_CODE = "FORM-131";
    public static final String COMMAND_CODE = "FORM-132";
    //++agent TASK-208 [16.07.2026] XG-110
    public static final String VALUE_TABLE_SCHEMA_CODE = "FORM-133";
    //--agent TASK-208 XG-110

    private static final String DOCUMENT_OBJECT = "DocumentObject";
    private static final String DOCUMENT_LIST = "DocumentListDynamicList";
    private static final String UNKNOWN_CONTEXT = "Unknown";

    private static final Map<String, PropertyDomain> PROPERTY_DOMAINS = new LinkedHashMap<>();
    private static final Map<String, Set<String>> COMMAND_DOMAINS = new LinkedHashMap<>();

    static {
        registerProperty("windowOpeningMode", "WindowOpeningMode",
                List.of("LockOwnerWindow", "LockWholeInterface"), "Modeless");
        registerProperty("autoSaveDataInSettings", "AutoSaveDataInSettings",
                List.of("Use"), "DontUse");
        registerProperty("saveDataInSettings", "SaveDataInSettings",
                List.of("UseList"), "DontUse");
        registerProperty("usePostingMode", "UsePostingMode",
                List.of("Auto"), "Postings");

        COMMAND_DOMAINS.put(DOCUMENT_OBJECT, orderedSet(
                "Copy", "Write", "Post", "UndoPosting", "SetDeletionMark", "Delete"));
        COMMAND_DOMAINS.put(DOCUMENT_LIST, orderedSet(
                "Create", "Copy", "Delete", "SetDeletionMark", "Post", "UndoPosting"));
    }

    private FormDesignerDomain() {
    }

    public static List<Violation> validate(FormDsl dsl) {
        List<Violation> violations = new ArrayList<>();
        String context = detectContext(dsl);
        validateDslProperties(dsl, context, violations);
        validateCommands(dsl.getExcludedCommands(), context, 0, "/Form/CommandSet", violations);
        //++agent TASK-208 [16.07.2026] XG-110
        // Designer resolves a bound ValueTable field only when the form attribute carries
        // the matching Column schema. Reject before opening output to prevent silent loss.
        validateDslValueTableSchemas(dsl, violations);
        //--agent TASK-208 XG-110
        return violations;
    }

    public static List<Violation> validate(XmlNode root) {
        List<Violation> violations = new ArrayList<>();
        String context = detectContext(root);
        validateXmlProperties(root, context, violations);

        XmlNode commandSet = root.child("CommandSet");
        if (commandSet != null) {
            for (XmlNode command : commandSet.children("ExcludedCommand")) {
                //**agent XG-111 [28.09.2026 21:30:00]
                // Закрытый домен команд - контракт DSL/writer. Designer-XML исключает сотни
                // стандартных команд (канон src/xml: Abort, OK, CustomizeForm, PostAndClose...),
                // закрытый домен давал 1487 ложных ERROR на 285 рабочих формах. Для готового XML
                // ловим только класс алиасов: не латинский идентификатор стандартной команды.
                //validateCommand(command.getText(), context, command.getLine(),
                //        "/Form/CommandSet/ExcludedCommand", violations);
                String text = command.getText() == null ? "" : command.getText().trim();
                if (!text.matches("[A-Z][A-Za-z0-9]*")) {
                    validateCommand(text, context, command.getLine(),
                            "/Form/CommandSet/ExcludedCommand", violations);
                }
                //**agent XG-111
            }
        }
        //++agent TASK-208 [16.07.2026] XG-110
        validateXmlValueTableSchemas(root, violations);
        //--agent TASK-208 XG-110
        return violations;
    }

    //++agent TASK-208 [16.07.2026] XG-110
    private static void validateDslValueTableSchemas(FormDsl dsl,
                                                     List<Violation> violations) {
        Map<String, Set<String>> schemas = new LinkedHashMap<>();
        if (dsl.getAttributes() != null) {
            for (FormDsl.Attribute attribute : dsl.getAttributes()) {
                if (!isValueCollectionType(attribute.getType()) || attribute.getName() == null) {
                    continue;
                }
                Set<String> columns = new LinkedHashSet<>();
                if (attribute.getColumns() != null) {
                    for (FormDsl.Column column : attribute.getColumns()) {
                        String name = column.getName();
                        if (name == null || name.isBlank() || !columns.add(name)) {
                            violations.add(new Violation(VALUE_TABLE_SCHEMA_CODE,
                                    "ValueTable attribute '" + attribute.getName()
                                            + "' has a blank or duplicate schema column '" + name + "'.",
                                    0, "/Form/attributes/" + attribute.getName() + "/columns"));
                        }
                    }
                }
                schemas.put(attribute.getName(), columns);
            }
        }
        if (schemas.isEmpty() || dsl.getElements() == null) {
            return;
        }
        collectDslBindings(dsl.getElements(), schemas, violations);
    }

    @SuppressWarnings("unchecked")
    private static void collectDslBindings(Object value, Map<String, Set<String>> schemas,
                                           List<Violation> violations) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                collectDslBindings(item, schemas, violations);
            }
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            return;
        }
        Object dataPath = map.containsKey("dataPath") ? map.get("dataPath") : map.get("path");
        if (dataPath instanceof String path) {
            validateBoundColumn(path, schemas, 0, "/Form/elements", violations);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if ("dataPath".equals(entry.getKey()) || "path".equals(entry.getKey())) {
                continue;
            }
            collectDslBindings(entry.getValue(), schemas, violations);
        }
    }

    private static void validateXmlValueTableSchemas(XmlNode root,
                                                     List<Violation> violations) {
        Map<String, Set<String>> schemas = new LinkedHashMap<>();
        XmlNode attributes = root.child("Attributes");
        if (attributes != null) {
            for (XmlNode attribute : attributes.children("Attribute")) {
                String name = attribute.attr("name");
                if (name == null || !looksLikeValueCollectionType(attribute)) {
                    continue;
                }
                Set<String> columns = new LinkedHashSet<>();
                XmlNode columnContainer = attribute.child("Columns");
                if (columnContainer != null) {
                    for (XmlNode column : columnContainer.children("Column")) {
                        String columnName = column.attr("name");
                        if (columnName == null || columnName.isBlank() || !columns.add(columnName)) {
                            violations.add(new Violation(VALUE_TABLE_SCHEMA_CODE,
                                    "ValueTable attribute '" + name
                                            + "' has a blank or duplicate schema column '"
                                            + columnName + "'.",
                                    column.getLine(), "/Form/Attributes/Attribute[@name='"
                                            + name + "']/Columns"));
                        }
                    }
                }
                schemas.put(name, columns);
            }
        }
        XmlNode childItems = root.child("ChildItems");
        if (!schemas.isEmpty() && childItems != null) {
            collectXmlBindings(childItems, "/Form/ChildItems", schemas, violations);
        }
    }

    private static void collectXmlBindings(XmlNode node, String path,
                                           Map<String, Set<String>> schemas,
                                           List<Violation> violations) {
        if ("DataPath".equals(node.getName()) && node.getText() != null) {
            validateBoundColumn(node.getText().trim(), schemas, node.getLine(), path,
                    violations);
        }
        for (XmlNode child : node.getChildren()) {
            collectXmlBindings(child, path + "/" + child.getName(), schemas, violations);
        }
    }

    private static void validateBoundColumn(String rawPath, Map<String, Set<String>> schemas,
                                            int line, String path,
                                            List<Violation> violations) {
        if (rawPath == null || rawPath.isBlank() || rawPath.startsWith("Items.")) {
            return;
        }
        String normalized = rawPath.startsWith("~") ? rawPath.substring(1) : rawPath;
        String[] segments = normalized.split("\\.");
        if (segments.length < 2) {
            return;
        }
        String root = stripIndex(segments[0]);
        Set<String> columns = schemas.get(root);
        if (columns == null) {
            return;
        }
        String column = stripIndex(segments[1]);
        if (!columns.contains(column)) {
            violations.add(new Violation(VALUE_TABLE_SCHEMA_CODE,
                    "DataPath '" + rawPath + "' refers to missing schema column '" + column
                            + "' of ValueTable attribute '" + root
                            + "'. Declare it in attributes[].columns before binding a UI field.",
                    line, path));
        }
    }

    private static String stripIndex(String segment) {
        int bracket = segment.indexOf('[');
        return bracket >= 0 ? segment.substring(0, bracket) : segment;
    }

    private static boolean isValueCollectionType(String type) {
        //**agent TASK-208 [16.07.2026] XG-110 REVIEW-1
        // TypeResolver/Writer принимают обе коллекции case-insensitive; домен preflight
        // обязан совпадать с ними, иначе lower/mixed-case обходит fail-before-output.
        // equalsIgnoreCase locale-independent, в отличие от toLowerCase() без Locale.ROOT.
        return "ValueTable".equalsIgnoreCase(type) || "ValueTree".equalsIgnoreCase(type);
        //**agent TASK-208 XG-110 REVIEW-1
    }

    private static boolean looksLikeValueCollectionType(XmlNode attribute) {
        XmlNode type = attribute.child("Type");
        if (type == null) {
            return false;
        }
        for (XmlNode value : type.children("Type")) {
            String text = value.getText();
            if (text != null && (text.trim().endsWith(":ValueTable")
                    || text.trim().endsWith(":ValueTree"))) {
                return true;
            }
        }
        return false;
    }
    //--agent TASK-208 XG-110

    public static void requireValid(FormDsl dsl) {
        List<Violation> violations = validate(dsl);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.code() + ": " + v.message())
                    .reduce((left, right) -> left + System.lineSeparator() + right)
                    .orElse("Invalid form DSL Designer domain"));
        }
    }

    private static void validateDslProperties(FormDsl dsl, String context,
                                              List<Violation> violations) {
        if (dsl.getProperties() == null) {
            return;
        }
        for (Map.Entry<String, Object> entry : dsl.getProperties().entrySet()) {
            PropertyDomain domain = PROPERTY_DOMAINS.get(entry.getKey());
            if (domain != null) {
                validateProperty(domain, String.valueOf(entry.getValue()), context, 0,
                        "/Form/properties/" + entry.getKey(), violations);
                continue;
            }

            for (PropertyDomain known : PROPERTY_DOMAINS.values()) {
                if (isDslPropertyAlias(entry.getKey(), known)) {
                    violations.add(new Violation(PROPERTY_CODE,
                            "Property alias '" + entry.getKey() + "' is not allowed in context '"
                                    + context + "'; use exact DSL property '" + known.dslName()
                                    + "'. Aliases are rejected and never normalized silently.",
                            0, "/Form/properties/" + entry.getKey()));
                    break;
                }
            }
        }
    }

    private static void validateXmlProperties(XmlNode root, String context,
                                              List<Violation> violations) {
        // XML names are case-sensitive. A case variant must not bypass the exact-name lookup,
        // otherwise writer and validator both accept an extra XDTO-invalid root property.
        for (XmlNode child : root.getChildren()) {
            for (PropertyDomain domain : PROPERTY_DOMAINS.values()) {
                if (!child.getName().equals(domain.xmlName())
                        && child.getName().equalsIgnoreCase(domain.xmlName())) {
                    violations.add(new Violation(PROPERTY_CODE,
                            "Property tag alias '" + child.getName()
                                    + "' is not allowed in context '" + context
                                    + "'; use exact XML tag '" + domain.xmlName()
                                    + "'. Aliases are rejected and never normalized silently.",
                            child.getLine(), "/Form/" + child.getName()));
                    break;
                }
            }
        }

        for (PropertyDomain domain : PROPERTY_DOMAINS.values()) {
            for (XmlNode property : root.children(domain.xmlName())) {
                validateProperty(domain, property.getText(), context, property.getLine(),
                        "/Form/" + domain.xmlName(), violations);
            }
        }
    }

    private static void validateProperty(PropertyDomain domain, String value, String context,
                                         int line, String path, List<Violation> violations) {
        if (domain.allowed().contains(value)) {
            return;
        }
        String omission = domain.defaultLiteral().equals(value)
                ? " Value '" + value + "' is a Designer default and its canonical encoding is omission of the property/tag."
                : " Omit the property/tag only when the Designer default '" + domain.defaultLiteral()
                        + "' is intended.";
        violations.add(new Violation(PROPERTY_CODE,
                "Property '" + domain.dslName() + "' has invalid explicit value '" + value
                        + "' in context '" + context + "'; allowed explicit domain: "
                        + domain.allowed() + "." + omission
                        + " Aliases are rejected and never normalized silently.",
                line, path));
    }

    private static void validateCommands(List<String> commands, String context, int line,
                                         String path, List<Violation> violations) {
        if (commands == null) {
            return;
        }
        for (String command : commands) {
            validateCommand(command, context, line, path, violations);
        }
    }

    private static void validateCommand(String command, String context, int line,
                                        String path, List<Violation> violations) {
        Set<String> allowed = COMMAND_DOMAINS.get(context);
        if (allowed != null && allowed.contains(command)) {
            return;
        }
        String allowedText = allowed != null ? allowed.toString()
                : "DocumentObject=" + COMMAND_DOMAINS.get(DOCUMENT_OBJECT)
                        + ", DocumentListDynamicList=" + COMMAND_DOMAINS.get(DOCUMENT_LIST);
        violations.add(new Violation(COMMAND_CODE,
                "ExcludedCommand '" + command + "' is invalid in main-attribute context '"
                        + context + "'; allowed domain: " + allowedText
                        + ". Aliases are rejected; the writer never normalizes or drops commands silently.",
                line, path));
    }

    private static String detectContext(FormDsl dsl) {
        if (dsl.getAttributes() == null) {
            return UNKNOWN_CONTEXT;
        }
        List<FormDsl.Attribute> main = dsl.getAttributes().stream()
                .filter(attribute -> Boolean.TRUE.equals(attribute.getMain()))
                .toList();
        if (main.size() != 1) {
            return UNKNOWN_CONTEXT;
        }
        FormDsl.Attribute attribute = main.get(0);
        if (attribute.getType() != null && attribute.getType().startsWith("DocumentObject.")) {
            return DOCUMENT_OBJECT;
        }
        if ("DynamicList".equals(attribute.getType()) && attribute.getSettings() != null) {
            Object mainTable = attribute.getSettings().get("mainTable");
            if (mainTable instanceof String text && text.startsWith("Document.")) {
                return DOCUMENT_LIST;
            }
        }
        return UNKNOWN_CONTEXT;
    }

    private static String detectContext(XmlNode root) {
        XmlNode attributes = root.child("Attributes");
        if (attributes == null) {
            return UNKNOWN_CONTEXT;
        }
        List<XmlNode> main = attributes.children("Attribute").stream()
                .filter(attribute -> "true".equals(attribute.childText("MainAttribute")))
                .toList();
        if (main.size() != 1) {
            return UNKNOWN_CONTEXT;
        }

        XmlNode attribute = main.get(0);
        XmlNode type = attribute.child("Type");
        if (type != null) {
            for (XmlNode value : type.children("Type")) {
                if ("v8".equals(value.getPrefix()) && value.getText() != null
                        && value.getText().startsWith("cfg:DocumentObject.")) {
                    return DOCUMENT_OBJECT;
                }
                if ("v8".equals(value.getPrefix()) && "cfg:DynamicList".equals(value.getText())) {
                    XmlNode settings = attribute.child("Settings");
                    String mainTable = settings != null ? settings.childText("MainTable") : null;
                    if (mainTable != null && mainTable.startsWith("Document.")) {
                        return DOCUMENT_LIST;
                    }
                }
            }
        }
        return UNKNOWN_CONTEXT;
    }

    private static void registerProperty(String dslName, String xmlName, List<String> allowed,
                                         String defaultLiteral) {
        PROPERTY_DOMAINS.put(dslName, new PropertyDomain(
                dslName, xmlName, new LinkedHashSet<>(allowed), defaultLiteral));
    }

    private static Set<String> orderedSet(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static boolean isDslPropertyAlias(String candidate, PropertyDomain domain) {
        if (candidate == null || candidate.equals(domain.dslName())) {
            return false;
        }
        // DSL uses lowerCamel while XML uses UpperCamel. Check both spellings ignoring case,
        // but never rewrite them: only the documented exact DSL key is accepted.
        return candidate.equalsIgnoreCase(domain.dslName())
                || candidate.equalsIgnoreCase(domain.xmlName());
    }

    private record PropertyDomain(String dslName, String xmlName, Set<String> allowed,
                                  String defaultLiteral) {
    }

    public record Violation(String code, String message, int line, String path) {
    }
}
//--agent TASK-174 XG-109 BUG-013

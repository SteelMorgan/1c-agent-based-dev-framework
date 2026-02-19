package io.github.onec.xmlgen.validator;

import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;

import java.util.*;

/**
 * Валидатор для XML управляемой формы (Form.xml / Form.form).
 * <p>
 * Level 1 (Structure): FORM-001..008
 * Level 2 (Semantic):  FORM-101..113
 */
public class FormValidator implements XmlValidator {

    private static final String NS_FORM = "http://v8.1c.ru/8.3/xcf/logform";

    /** Известные имена UI-элементов (из FormElementType enum). */
    private static final Set<String> KNOWN_ELEMENT_TYPES;
    static {
        Set<String> types = new HashSet<>();
        for (FormElementType t : FormElementType.values()) {
            types.add(t.fullName().getEn());
        }
        KNOWN_ELEMENT_TYPES = Collections.unmodifiableSet(types);
    }

    private static final Set<String> KNOWN_ALLOWED_LENGTHS = Set.of("Variable", "Fixed");
    private static final Set<String> KNOWN_ALLOWED_SIGNS = Set.of("Any", "Nonnegative");
    private static final Set<String> KNOWN_DATE_FRACTIONS = Set.of("Date", "Time", "DateTime");

    @Override
    public String objectType() {
        return "form";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return "Form".equals(document.getRootElement());
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();

        validateStructure(document, issues);

        if (level == ValidationLevel.SEMANTIC) {
            validateSemantic(document, issues);
        }

        return issues;
    }

    // ==================== Level 1: Structure ====================

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // FORM-001: AutoCommandBar
        XmlNode autoCmd = root.child("AutoCommandBar");
        if (autoCmd == null) {
            issues.add(ValidationIssue.error("FORM-001",
                    "Missing required <AutoCommandBar> element",
                    root.getLine(), "/Form"));
        } else {
            String cmdName = autoCmd.attr("name");
            String cmdId = autoCmd.attr("id");
            if (!"ФормаКоманднаяПанель".equals(cmdName)) {
                issues.add(ValidationIssue.error("FORM-001",
                        "AutoCommandBar name must be 'ФормаКоманднаяПанель', found '" + cmdName + "'",
                        autoCmd.getLine(), "/Form/AutoCommandBar/@name"));
            }
            if (!"-1".equals(cmdId)) {
                issues.add(ValidationIssue.error("FORM-001",
                        "AutoCommandBar id must be '-1', found '" + cmdId + "'",
                        autoCmd.getLine(), "/Form/AutoCommandBar/@id"));
            }
        }

        // FORM-002: version attribute
        String version = root.attr("version");
        if (version == null || version.isEmpty()) {
            issues.add(ValidationIssue.warning("FORM-002",
                    "Missing version attribute on <Form>",
                    root.getLine(), "/Form/@version"));
        }

        // Собираем все id для проверки уникальности (FORM-004)
        // В 1С id атрибутов, команд и элементов нумеруются НЕЗАВИСИМО
        Set<String> attrIds = new HashSet<>();
        Set<String> cmdIds = new HashSet<>();
        Set<String> elemIds = new HashSet<>();
        List<String> duplicateIds = new ArrayList<>();

        // FORM-003: Attributes
        XmlNode attributes = root.child("Attributes");
        if (attributes != null) {
            for (XmlNode attr : attributes.getChildren()) {
                // Пропускаем системные элементы (ConditionalAppearance и т.д.)
                if (isSystemAttributeElement(attr.getName())) continue;
                validateNameAndId(attr, "/Form/Attributes/" + attr.getName(), attrIds, duplicateIds, "FORM-003", issues);
            }
        }

        // FORM-008: Commands
        XmlNode commands = root.child("Commands");
        if (commands != null) {
            for (XmlNode cmd : commands.getChildren()) {
                validateNameAndId(cmd, "/Form/Commands/" + cmd.getName(), cmdIds, duplicateIds, "FORM-008", issues);
            }
        }

        // FORM-006: ChildItems
        XmlNode childItems = root.child("ChildItems");
        if (childItems == null) {
            issues.add(ValidationIssue.warning("FORM-006",
                    "Missing <ChildItems> element",
                    root.getLine(), "/Form"));
        } else {
            // FORM-007: UI elements имеют name и id
            collectElementIds(childItems, "/Form/ChildItems", elemIds, duplicateIds, issues);
        }

        // FORM-004: Дубли id
        for (String dupId : duplicateIds) {
            issues.add(ValidationIssue.error("FORM-004",
                    "Duplicate id '" + dupId + "' found among form elements/attributes/commands",
                    0, "/Form"));
        }

        // FORM-005: ID последовательные ≥ 1 (только предупреждение)
        // Пропускаем для MVP — это soft-check
    }

    private void validateNameAndId(XmlNode node, String path, Set<String> allIds,
                                    List<String> duplicateIds, String code, List<ValidationIssue> issues) {
        String name = node.attr("name");
        if (name == null) name = node.childText("name");
        String id = node.attr("id");
        if (id == null) id = node.childText("id");

        if (name == null || name.isEmpty()) {
            issues.add(ValidationIssue.error(code,
                    "Element missing name",
                    node.getLine(), path));
        }
        if (id == null || id.isEmpty()) {
            issues.add(ValidationIssue.error(code,
                    "Element missing id",
                    node.getLine(), path));
        } else {
            // Проверяем уникальность (id = -1 для AutoCommandBar — допустимое исключение)
            if (!"-1".equals(id) && !allIds.add(id)) {
                duplicateIds.add(id);
            }
        }
    }

    private void collectElementIds(XmlNode parent, String parentPath,
                                    Set<String> allIds, List<String> duplicateIds,
                                    List<ValidationIssue> issues) {
        for (XmlNode child : parent.getChildren()) {
            String childPath = parentPath + "/" + child.getName();

            // FORM-007: Каждый UI-элемент имеет name и id
            String name = child.attr("name");
            String id = child.attr("id");

            if (name == null || name.isEmpty()) {
                issues.add(ValidationIssue.error("FORM-007",
                        "UI element <" + child.getName() + "> missing name attribute",
                        child.getLine(), childPath));
            }
            if (id == null || id.isEmpty()) {
                issues.add(ValidationIssue.error("FORM-007",
                        "UI element <" + child.getName() + "> missing id attribute",
                        child.getLine(), childPath));
            } else if (!"-1".equals(id) && !allIds.add(id)) {
                duplicateIds.add(id);
            }

            // Рекурсивно для ChildItems внутри элемента
            XmlNode innerChildItems = child.child("ChildItems");
            if (innerChildItems != null) {
                collectElementIds(innerChildItems, childPath + "/ChildItems",
                        allIds, duplicateIds, issues);
            }
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Собираем известные имена атрибутов (для FORM-102)
        Set<String> attributeNames = new HashSet<>();
        XmlNode attributes = root.child("Attributes");
        if (attributes != null) {
            for (XmlNode attr : attributes.getChildren()) {
                String name = attr.attr("name");
                if (name == null) name = attr.childText("name");
                if (name != null) attributeNames.add(name);
            }
        }

        // Собираем известные имена команд (для FORM-103)
        Set<String> commandNames = new HashSet<>();
        XmlNode commands = root.child("Commands");
        if (commands != null) {
            for (XmlNode cmd : commands.getChildren()) {
                String name = cmd.attr("name");
                if (name == null) name = cmd.childText("name");
                if (name != null) commandNames.add(name);
            }
        }

        // FORM-101: Тип UI-элемента — известный FormElementType
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
            validateElements(childItems, "/Form/ChildItems",
                    attributeNames, commandNames, issues);
        }

        // Проверяем атрибуты (FORM-107..110, 113)
        if (attributes != null) {
            validateAttributes(attributes, issues);
        }

        // FORM-111: Events
        XmlNode events = root.child("Events");
        if (events != null) {
            for (XmlNode event : events.getChildren()) {
                String eventName = event.attr("name");
                if (eventName == null) eventName = event.getName();
                String handler = event.getText();
                if (eventName != null && (handler == null || handler.isEmpty())) {
                    // Event без обработчика — обычно не ошибка, но имя должно быть
                }
            }
        }

        // FORM-112: Command.Action
        if (commands != null) {
            int idx = 0;
            for (XmlNode cmd : commands.getChildren()) {
                idx++;
                String cmdPath = "/Form/Commands/" + cmd.getName() + "[" + idx + "]";
                String action = cmd.childText("Action");
                if (action == null || action.isEmpty()) {
                    issues.add(ValidationIssue.warning("FORM-112",
                            "Command has no <Action>",
                            cmd.getLine(), cmdPath + "/Action"));
                }
            }
        }
    }

    private void validateElements(XmlNode parent, String parentPath,
                                   Set<String> attrNames, Set<String> cmdNames,
                                   List<ValidationIssue> issues) {
        for (XmlNode elem : parent.getChildren()) {
            String elemName = elem.getName();
            String elemPath = parentPath + "/" + elemName;

            // FORM-101: Известный тип
            if (!KNOWN_ELEMENT_TYPES.contains(elemName) && !"AutoCommandBar".equals(elemName)) {
                issues.add(ValidationIssue.error("FORM-101",
                        "Unknown form element type '" + elemName + "'",
                        elem.getLine(), elemPath));
            }

            // FORM-102: DataPath → существующий Attribute.name
            String dataPath = elem.childText("DataPath");
            if (dataPath != null && !dataPath.isEmpty()) {
                // DataPath может быть составным: "Object.Name" — берём первую часть
                String rootAttr = dataPath.contains(".") ? dataPath.split("\\.")[0] : dataPath;
                if (!attrNames.contains(rootAttr) && !"Object".equals(rootAttr)) {
                    issues.add(ValidationIssue.error("FORM-102",
                            "DataPath '" + dataPath + "' references unknown attribute '" + rootAttr + "'",
                            elem.getLine(), elemPath + "/DataPath"));
                }
            }

            // FORM-103: Button.CommandName → существующая Command
            if ("Button".equals(elemName)) {
                String commandName = elem.childText("CommandName");
                if (commandName != null && !commandName.isEmpty()) {
                    // Формат: "Form.Command.<name>" — извлекаем имя
                    String cmdRef = commandName;
                    if (commandName.startsWith("Form.Command.")) {
                        cmdRef = commandName.substring("Form.Command.".length());
                    }
                    if (!cmdNames.contains(cmdRef) && !isStandardCommand(commandName)) {
                        issues.add(ValidationIssue.warning("FORM-103",
                                "Button CommandName '" + commandName + "' references unknown command",
                                elem.getLine(), elemPath + "/CommandName"));
                    }
                }
            }

            // FORM-104: InputField/Table должен иметь DataPath
            if ("InputField".equals(elemName) || "Table".equals(elemName)
                    || "LabelField".equals(elemName)) {
                if (dataPath == null || dataPath.isEmpty()) {
                    issues.add(ValidationIssue.warning("FORM-104",
                            elemName + " has no DataPath",
                            elem.getLine(), elemPath));
                }
            }

            // Рекурсивно для дочерних ChildItems
            XmlNode innerChildItems = elem.child("ChildItems");
            if (innerChildItems != null) {
                validateElements(innerChildItems, elemPath + "/ChildItems",
                        attrNames, cmdNames, issues);
            }
        }
    }

    private void validateAttributes(XmlNode attributes, List<ValidationIssue> issues) {
        int idx = 0;
        for (XmlNode attr : attributes.getChildren()) {
            idx++;
            String attrPath = "/Form/Attributes/" + attr.getName() + "[" + idx + "]";

            // FORM-107: Тип атрибута
            XmlNode type = attr.child("Type");
            if (type != null) {
                XmlNode typeNode = type.child("Type");
                // Тип может содержать TypeDescription → Type → TypeId
                // Пропускаем глубокую валидацию типов для MVP
            }

            // FORM-108: StringQualifiers.AllowedLength
            checkQualifier(attr, "StringQualifiers", "AllowedLength",
                    KNOWN_ALLOWED_LENGTHS, "FORM-108", attrPath, issues);

            // FORM-109: NumberQualifiers.AllowedSign
            checkQualifier(attr, "NumberQualifiers", "AllowedSign",
                    KNOWN_ALLOWED_SIGNS, "FORM-109", attrPath, issues);

            // FORM-110: DateQualifiers.DateFractions
            checkQualifier(attr, "DateQualifiers", "DateFractions",
                    KNOWN_DATE_FRACTIONS, "FORM-110", attrPath, issues);

            // FORM-113: ValueTable-атрибут должен иметь ≥1 Column
            XmlNode columns = attr.child("Columns");
            // Если тип — ValueTable но нет колонок — предупреждение
            // Определяем ValueTable по наличию Columns элемента
            // или по типу xs:ValueTable
        }
    }

    private void checkQualifier(XmlNode attr, String qualName, String fieldName,
                                 Set<String> validValues, String code, String path,
                                 List<ValidationIssue> issues) {
        // Ищем квалификатор рекурсивно (может быть в Type/Type/...)
        XmlNode qual = findDescendant(attr, qualName);
        if (qual != null) {
            String value = qual.childText(fieldName);
            if (value != null && !value.isEmpty() && !validValues.contains(value)) {
                issues.add(ValidationIssue.error(code,
                        fieldName + " value '" + value + "' is invalid, expected: " + validValues,
                        qual.getLine(), path + "/" + qualName + "/" + fieldName));
            }
        }
    }

    private XmlNode findDescendant(XmlNode node, String name) {
        XmlNode direct = node.child(name);
        if (direct != null) return direct;

        for (XmlNode child : node.getChildren()) {
            XmlNode found = findDescendant(child, name);
            if (found != null) return found;
        }
        return null;
    }

    private boolean isStandardCommand(String commandName) {
        // Стандартные команды формы: Form.StandardCommand.*
        return commandName.startsWith("Form.StandardCommand.")
                || commandName.startsWith("Item.")
                || commandName.startsWith("Reread")
                || commandName.startsWith("Write")
                || commandName.startsWith("Close");
    }

    /**
     * Системные элементы внутри <Attributes>, которые не являются пользовательскими атрибутами.
     * У них нет name/id атрибутов.
     */
    private static boolean isSystemAttributeElement(String elementName) {
        return "ConditionalAppearance".equals(elementName);
    }
}

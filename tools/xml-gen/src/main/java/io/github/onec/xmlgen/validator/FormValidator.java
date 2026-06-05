package io.github.onec.xmlgen.validator;

import com.github._1c_syntax.bsl.mdo.storage.form.FormElementType;

import java.util.*;

/**
 * Валидатор для XML управляемой формы (Form.xml / Form.form).
 * <p>
 * Level 1 (Structure): FORM-001..008
 * Level 2 (Semantic):  FORM-101..114, FORM-115, FORM-116
 *
 * <p><b>FORM-115</b> и <b>FORM-116</b> добавлены в задаче err-form-xml-tooling-fix
 * (происходит из эскалации OC-22444 Phase 3c F-01 BLOCK):
 * <ul>
 *   <li>FORM-115 — non-canonical wrapper типа в {@code <Attribute>} или {@code <Column>}
 *       ({@code <ValueType>...</ValueType>} или {@code <Type><Type>...</Type></Type>}
 *       без префикса {@code v8:}). Canonical: {@code <Type><v8:Type>X</v8:Type></Type>}.</li>
 *   <li>FORM-116 — cross-check между {@code Attribute.Columns} и UI {@code Table.ChildItems}.
 *       Если у атрибута есть {@code <Columns>} с колонками и в форме есть
 *       {@code <Table><DataPath>X</DataPath>}, то в её {@code <ChildItems>}
 *       (в т.ч. вложенные {@code <ColumnGroup>}) обязан присутствовать
 *       {@code <InputField>}/{@code <CheckBoxField>}/{@code <LabelField>}
 *       с {@code <DataPath>X.col</DataPath>} для каждой колонки.</li>
 * </ul>
 */
public class FormValidator implements XmlValidator {

    private static final String NS_FORM = "http://v8.1c.ru/8.3/xcf/logform";

    /** Канонический префикс v8 (namespace {@code http://v8.1c.ru/8.1/data/core}). */
    private static final String V8_PREFIX = "v8";

    /** Имена UI-элементов, которые могут привязываться к колонке атрибута через DataPath
     *  (FORM-116): берём только те, что используются в Drive-эталонах. */
    private static final Set<String> COLUMN_UI_FIELD_TYPES =
            Set.of("InputField", "CheckBoxField", "LabelField");

    /**
     * Дополнительные валидные типы полей формы, отсутствующие в enum
     * {@code FormElementType} библиотеки 1c_syntax (TASK-171 V-2).
     * <p>Белый список enum неполон: например {@code SpreadSheetDocumentField} —
     * стандартное валидное поле табличного документа, встречается в реальной выгрузке БСП
     * ({@code _ДемоГенерацияШтрихкода/Forms/Форма}) и давало ложный ERROR FORM-101.
     * Перечень полей платформы, которые enum может не знать, добавляем хардкодом поверх enum.
     */
    private static final Set<String> EXTRA_KNOWN_ELEMENT_TYPES = Set.of(
            "SpreadSheetDocumentField", "HTMLDocumentField", "GanttChartField",
            "PlannerField", "FormattedDocumentField", "ChartField",
            "GraphicalSchemaField", "GeographicalSchemaField", "DendrogramField",
            "TextDocumentField", "TrackBarField", "ProgressBarField"
    );

    /** Известные имена UI-элементов (из FormElementType enum + платформенные поля сверх enum). */
    private static final Set<String> KNOWN_ELEMENT_TYPES;
    static {
        Set<String> types = new HashSet<>();
        for (FormElementType t : FormElementType.values()) {
            types.add(t.fullName().getEn());
        }
        types.addAll(EXTRA_KNOWN_ELEMENT_TYPES); // TASK-171 V-2
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
            // TASK-171 V-1: имя главной AutoCommandBar НЕ фиксировано платформой.
            // Реальные выгрузки Конфигуратора используют и 'ФормаКоманднаяПанель', и
            // 'Форма_КоманднаяПанель' (11 из 145 _Демо-форм) — проверка имени давала ложный
            // ERROR на валидных формах. Николай имя не проверяет вовсе, только id == -1.
            // Проверяем ТОЛЬКО id (это инвариант главной панели формы).
            String cmdId = autoCmd.attr("id");
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

        // FORM-006: ChildItems.
        // TASK-171 V-5: отсутствие <ChildItems> — НЕ ошибка. Валидные служебные формы обработок
        // без UI-дерева (работают через код/параметры) штатно не имеют <ChildItems> (5 из 145
        // _Демо-форм). Прежний WARN был ложным; у Николая такой проверки нет вовсе. WARNING убран,
        // при наличии <ChildItems> по-прежнему проверяем name/id вложенных элементов (FORM-007).
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
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

        // Собираем карту Table-элементов (имя → DataPath) для резолва Items.X.CurrentData.*
        Map<String, String> tableDataPaths = collectTableDataPaths(root);

        // FORM-101: Тип UI-элемента — известный FormElementType
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
            validateElements(childItems, "/Form/ChildItems",
                    attributeNames, commandNames, tableDataPaths, issues);
        }

        // Проверяем атрибуты (FORM-107..110, 114, 115)
        if (attributes != null) {
            validateAttributes(attributes, issues);
        }

        // FORM-116: cross-check Attribute.Columns ↔ Table.ChildItems
        validateValueTableUiColumns(root, issues);

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

        // FORM-118: Event-хэндлер не должен быть пустой строкой (при наличии name)
        validateEventHandlersNonEmpty(root, issues);

        // FORM-117: Companions для UI-элементов
        if (childItems != null) {
            validateElementCompanions(childItems, "/Form/ChildItems", issues);
        }

        // FORM-119: MainAttribute должен быть только у одного Attribute
        validateMainAttributeCount(attributes, issues);

        // FORM-120: Title должен быть multilingual XML (v8:item), а не plain text
        validateMultilingualTitles(root, issues);
    }

    /**
     * Коллекция UI-элементов типа Table в форме (для резолва Items.X.CurrentData.*).
     * Заполняется один раз при семантической валидации и передаётся контекстом.
     */
    private Map<String, String> collectTableDataPaths(XmlNode root) {
        Map<String, String> result = new LinkedHashMap<>();
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
            collectTableDataPathsRecursive(childItems, result);
        }
        return result;
    }

    private void collectTableDataPathsRecursive(XmlNode parent, Map<String, String> tableMap) {
        for (XmlNode child : parent.getChildren()) {
            if ("Table".equals(child.getName())) {
                String name = child.attr("name");
                if (name == null) name = child.childText("name");
                String dp = child.childText("DataPath");
                if (name != null) {
                    tableMap.put(name, dp); // dp may be null
                }
            }
            XmlNode inner = child.child("ChildItems");
            if (inner != null) {
                collectTableDataPathsRecursive(inner, tableMap);
            }
        }
    }

    /**
     * Резолв DataPath по алгоритму канона Широкова (SPEC §10.3):
     * <ol>
     *   <li>Числовые индексы {@code ^\d+$} или UUID-ссылки {@code \d+/\d+:[0-9a-fA-F-]+} → silent skip (null = skip).</li>
     *   <li>{@code Items.<TableName>.CurrentData.<Field>} → найти Table, взять корневой реквизит её DataPath.</li>
     *   <li>{@code ~<Attr>.*} → снять {@code ~}, взять первый сегмент как имя реквизита.</li>
     *   <li>Иначе → взять первый сегмент через точку.</li>
     * </ol>
     *
     * @param dataPath  значение DataPath из XML
     * @param attrNames имена реквизитов формы
     * @param tableMap  имена Table-элементов → DataPath таблицы (из ChildItems)
     * @param elemLine  строка для отчёта
     * @param elemPath  путь для отчёта
     * @param issues    список для добавления ошибок
     * @return true = резолв выполнен (не нужна дальнейшая проверка), false = стандартная проверка не нужна
     */
    private boolean resolveDataPath(String dataPath, Set<String> attrNames,
                                    Map<String, String> tableMap,
                                    int elemLine, String elemPath,
                                    List<ValidationIssue> issues) {
        if (dataPath == null || dataPath.isEmpty()) {
            return true; // ничего не проверяем
        }

        // 1. Числовой индекс: "10", "1000003"
        if (dataPath.matches("^\\d+$")) {
            return true; // silent skip
        }
        // UUID-ссылка: "1/0:a917a122-f663-4c45-8de0-fd5104007de3"
        if (dataPath.matches("^\\d+/\\d+:[0-9a-fA-F\\-]+$")) {
            return true; // silent skip
        }

        // 2. Items.<TableName>.CurrentData.<Field>
        if (dataPath.startsWith("Items.")) {
            String[] parts = dataPath.split("\\.");
            if (parts.length >= 3 && "CurrentData".equals(parts[2])) {
                String tableName = parts[1];
                if (!tableMap.containsKey(tableName)) {
                    issues.add(ValidationIssue.error("FORM-102",
                            "DataPath '" + dataPath + "': Items table '" + tableName + "' not found in form ChildItems",
                            elemLine, elemPath + "/DataPath"));
                    return true;
                }
                String tableDataPath = tableMap.get(tableName);
                if (tableDataPath == null || tableDataPath.isEmpty()) {
                    // Таблица без DataPath (динамическая форма) — принять молча
                    return true;
                }
                // Снять ~ и [N] от DataPath таблицы, взять первый сегмент
                String cleaned = stripLeadingTilde(stripNumericSegments(tableDataPath));
                String rootAttr = cleaned.contains(".") ? cleaned.split("\\.")[0] : cleaned;
                if (!rootAttr.isEmpty() && !attrNames.contains(rootAttr) && !"Object".equals(rootAttr)) {
                    issues.add(ValidationIssue.error("FORM-102",
                            "DataPath '" + dataPath + "': table '" + tableName + "' DataPath='"
                                    + tableDataPath + "' references unknown attribute '" + rootAttr + "'",
                            elemLine, elemPath + "/DataPath"));
                }
                return true;
            } else {
                // Items.* но не Items.<T>.CurrentData — предупреждение (unknown shape)
                issues.add(ValidationIssue.warning("FORM-102",
                        "DataPath '" + dataPath + "' has unknown Items.* shape; expected Items.<Table>.CurrentData.<Field>",
                        elemLine, elemPath + "/DataPath"));
                return true;
            }
        }

        // 3. ~<Attr>.* — относительная ссылка
        if (dataPath.startsWith("~")) {
            String withoutTilde = dataPath.substring(1);
            String rootAttr = withoutTilde.contains(".") ? withoutTilde.split("\\.")[0] : withoutTilde;
            if (!rootAttr.isEmpty() && !attrNames.contains(rootAttr) && !"Object".equals(rootAttr)) {
                issues.add(ValidationIssue.error("FORM-102",
                        "DataPath '" + dataPath + "' references unknown attribute '" + rootAttr + "' (relative ~path)",
                        elemLine, elemPath + "/DataPath"));
            }
            return true;
        }

        // 4. Default — стандартная логика (берём первый сегмент)
        return false;
    }

    /** Убрать ведущий {@code ~} если присутствует. */
    private static String stripLeadingTilde(String s) {
        return s.startsWith("~") ? s.substring(1) : s;
    }

    /** Убрать числовые сегменты вида {@code [N]} из пути. Не используется пока, но готово к расширению. */
    private static String stripNumericSegments(String s) {
        // DataPath таблицы типа "Список[0]" или чистое "Список" — убираем [N]
        return s.replaceAll("\\[\\d+\\]", "");
    }

    private void validateElements(XmlNode parent, String parentPath,
                                   Set<String> attrNames, Set<String> cmdNames,
                                   Map<String, String> tableMap,
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

            // FORM-102: DataPath → существующий Attribute.name (с расширенным резолвом)
            String dataPath = elem.childText("DataPath");
            if (dataPath != null && !dataPath.isEmpty()) {
                boolean handled = resolveDataPath(dataPath, attrNames, tableMap,
                        elem.getLine(), elemPath, issues);
                if (!handled) {
                    // Стандартная логика: берём первый сегмент
                    String rootAttr = dataPath.contains(".") ? dataPath.split("\\.")[0] : dataPath;
                    if (!attrNames.contains(rootAttr) && !"Object".equals(rootAttr)) {
                        issues.add(ValidationIssue.error("FORM-102",
                                "DataPath '" + dataPath + "' references unknown attribute '" + rootAttr + "'",
                                elem.getLine(), elemPath + "/DataPath"));
                    }
                }
            }

            // FORM-103: Button.CommandName → существующая Command формы.
            // TASK-171 V-3: проверяем ТОЛЬКО ссылки вида "Form.Command.<name>" (команды самой формы).
            // Всё остальное (Form.StandardCommand.*, DataProcessor.X.StandardCommand.*, CommonCommand.*,
            // Catalog.*, Document.*, ExternalDataProcessor.*, Item.* и т.п.) — silent skip, как у Николая:
            // это команды менеджера объекта / общие команды, резолвящиеся вне Form.xml, и из формы их
            // проверить невозможно. Прежний хрупкий isStandardCommand давал ложный WARN на 3 _Демо-формах.
            if ("Button".equals(elemName)) {
                String commandName = elem.childText("CommandName");
                if (commandName != null && commandName.startsWith("Form.Command.")) {
                    String cmdRef = commandName.substring("Form.Command.".length());
                    if (!cmdNames.contains(cmdRef)) {
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
                        attrNames, cmdNames, tableMap, issues);
            }
        }
    }

    private void validateAttributes(XmlNode attributes, List<ValidationIssue> issues) {
        int idx = 0;
        for (XmlNode attr : attributes.getChildren()) {
            // Системные элементы внутри <Attributes> (например ConditionalAppearance) не являются
            // пользовательскими атрибутами — пропускаем.
            if (isSystemAttributeElement(attr.getName())) continue;

            idx++;
            String attrName = attr.attr("name");
            if (attrName == null) attrName = attr.childText("name");
            String attrLabel = attrName != null ? attrName : attr.getName();
            String attrPath = "/Form/Attributes/" + attr.getName() + "[" + idx + "]";

            // FORM-107: Тип атрибута
            XmlNode type = attr.child("Type");
            if (type != null) {
                // FORM-114: runtime-типы (FormDataStructure/Collection/Tree) не валидны в XML-схеме.
                for (XmlNode t : type.children("Type")) {
                    String typeText = t.getText();
                    if (typeText == null) continue;
                    String ts = typeText.trim();
                    if ("FormDataStructure".equals(ts)
                            || "FormDataCollection".equals(ts)
                            || "FormDataTree".equals(ts)
                            || ts.endsWith(":FormDataStructure")
                            || ts.endsWith(":FormDataCollection")
                            || ts.endsWith(":FormDataTree")) {
                        issues.add(ValidationIssue.error("FORM-114",
                                "Runtime type '" + ts
                                + "' запрещён в реквизите формы (не существует в XML-схеме). "
                                + "Используйте CatalogObject/DocumentObject/DataProcessorObject/ValueTable/ValueTree.",
                                t.getLine(), attrPath + "/Type"));
                    }
                }
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

            // FORM-115: non-canonical type wrapper в самом атрибуте
            checkNonCanonicalTypeWrapper(attr, attrLabel, attrPath, issues);

            // FORM-115: non-canonical type wrapper в каждой колонке атрибута
            XmlNode columns = attr.child("Columns");
            if (columns != null) {
                int colIdx = 0;
                for (XmlNode col : columns.getChildren()) {
                    if (!"Column".equals(col.getName())) continue;
                    colIdx++;
                    String colName = col.attr("name");
                    if (colName == null) colName = col.childText("name");
                    String colLabel = (colName != null ? colName : col.getName());
                    String colPath = attrPath + "/Columns/Column[" + colIdx + "]";
                    String contextLabel = attrLabel + "." + colLabel;
                    checkNonCanonicalTypeWrapper(col, contextLabel, colPath, issues);
                }
            }
        }
    }

    /**
     * FORM-115: детектирует non-canonical обёртку типа в {@code <Attribute>} или {@code <Column>}.
     *
     * <p>Canonical schema (источник истины — эталоны Drive Form.xml):
     * <pre>{@code
     * <Type>
     *     <v8:Type>X</v8:Type>
     * </Type>
     * }</pre>
     *
     * Non-canonical (срабатывает FORM-115):
     * <ul>
     *   <li>{@code <ValueType>...</ValueType>} — внешний тег ValueType вместо Type
     *       (например, кейс OC-22444: {@code <ValueType><Type>v8:ValueTable</Type></ValueType>}).</li>
     *   <li>{@code <Type><Type>X</Type></Type>} — внутренний {@code <Type>} без префикса {@code v8:}.</li>
     * </ul>
     *
     * <p>Допустимо:
     * <ul>
     *   <li>Отсутствие {@code <Type>} — атрибут «без типа».</li>
     *   <li>Пустой {@code <Type/>} — допустимо для произвольного типа (см. эталон 1, колонка
     *       «Питомец» в ВыборКонтрагентаУВЦ/Form.xml).</li>
     *   <li>{@code <Type>} с одним или несколькими дочерними {@code <v8:Type>} —
     *       полностью canonical (включая composite-type из нескольких {@code v8:Type}).</li>
     * </ul>
     *
     * <p>Severity = ERROR (ADR-3 technical-design).
     *
     * @param node           узел {@code <Attribute>} или {@code <Column>}
     * @param contextLabel   человекочитаемое имя (для сообщений), например «СообщенияБезОбъектаКонтекста.Аккаунт»
     * @param nodePath       XPath-подобный путь к узлу (для поля element ValidationIssue)
     * @param issues         список нарушений (добавление в место)
     */
    private void checkNonCanonicalTypeWrapper(XmlNode node, String contextLabel,
                                              String nodePath, List<ValidationIssue> issues) {
        // Случай 1: прямой потомок <ValueType>
        XmlNode valueType = node.child("ValueType");
        if (valueType != null) {
            issues.add(ValidationIssue.error("FORM-115",
                    "Non-canonical type wrapper <ValueType> in '" + contextLabel
                            + "'. Use canonical <Type><v8:Type>X</v8:Type></Type> instead. "
                            + "Use /form-edit skill or /form-dsl + xml-gen form compile.",
                    valueType.getLine(), nodePath + "/ValueType"));
        }

        // Случаи 2 и 3: перебираем все прямые Type-потомки (независимо от префикса).
        // Допустимо: outer <Type> (без префикса) с inner <v8:Type>. Недопустимо:
        //   — outer <v8:Type> (или любой другой prefix) вместо чистого <Type>
        //   — inner <Type> без префикса v8 внутри outer <Type>.
        for (XmlNode typeNode : node.getChildren()) {
            if (!"Type".equals(typeNode.getName())) continue;

            String outerPrefix = typeNode.getPrefix();
            if (outerPrefix != null && !outerPrefix.isEmpty()) {
                issues.add(ValidationIssue.error("FORM-115",
                        "Non-canonical outer type wrapper <" + outerPrefix + ":Type> in '"
                                + contextLabel + "'. Canonical: <Type><v8:Type>X</v8:Type></Type> "
                                + "(outer element must be <Type> without namespace prefix).",
                        typeNode.getLine(), nodePath + "/" + outerPrefix + ":Type"));
                // Не проверяем внутренности: outer уже некорректен, сообщение
                // о вложенном <Type> запутает пользователя.
                continue;
            }

            // outer <Type> — проверяем inner
            for (XmlNode child : typeNode.getChildren()) {
                if (!"Type".equals(child.getName())) continue;
                String prefix = child.getPrefix();
                if (!V8_PREFIX.equals(prefix)) {
                    issues.add(ValidationIssue.error("FORM-115",
                            "Non-canonical inner type tag <" + (prefix == null || prefix.isEmpty()
                                    ? "Type" : prefix + ":Type")
                                    + "> inside <Type> in '" + contextLabel
                                    + "'. Canonical: <Type><v8:Type>X</v8:Type></Type>.",
                            child.getLine(), nodePath + "/Type/Type"));
                }
            }
        }
    }

    /**
     * FORM-116: cross-check между {@code Attribute.Columns} и {@code Table.ChildItems}.
     *
     * <p>Алгоритм:
     * <ol>
     *   <li>Собрать список атрибутов с их колонками. Колонками считаются прямые потомки
     *       {@code <Columns>/<Column>} в {@code <Attribute>}. Признак ValueTable/ValueTree
     *       определяется liberal: либо явно по {@code <v8:Type>v8:ValueTable</v8:Type>}
     *       (или {@code v8:ValueTree}) в {@code <Type>}, либо по наличию непустого
     *       {@code <Columns>}. Это позволяет FORM-115 и FORM-116 срабатывать одновременно
     *       на failed Form.xml DSSL_Коммуникатор, где schema атрибута non-canonical.</li>
     *   <li>Рекурсивно собрать все UI-элементы {@code <Table>} в {@code /Form/ChildItems}
     *       и для каждого — множество DataPath потомков (рекурсивно через ChildItems
     *       и ColumnGroup) с {@code localName} в {InputField, CheckBoxField, LabelField}.</li>
     *   <li>Для каждой пары (атрибут X с колонками, Table.dataPath = X): если в
     *       {@code <ChildItems>} этой Table НЕТ НИ ОДНОГО UI-поля с
     *       {@code DataPath = "X.<любая колонка атрибута>"} — ERROR FORM-116.</li>
     *   <li>Если хотя бы одна колонка покрыта — НЕ срабатывает (это разрешает легитимный
     *       паттерн «часть колонок видима, часть скрыта», встречающийся в эталонах Drive:
     *       <ul>
     *         <li>{@code ВыборКонтрагентаУВЦ/Form.xml} — у {@code СписокПитомцев}
     *             в UI отображается только колонка {@code Питомец}, колонка {@code Умер} скрыта.</li>
     *         <li>{@code ObjectRegistrationNodes/Form.xml} — у {@code ExchangeNodesTree}
     *             отображены только {@code Description}, {@code Code}, {@code Check};
     *             {@code PictureIndex}, {@code Ref}, {@code NotExported} и др. служебные.</li>
     *       </ul>
     *       </li>
     *   <li>Если для атрибута X нет UI-Table с {@code DataPath = X} — НЕ срабатывает
     *       (атрибут может использоваться только программно через {@code Items.Add()}).</li>
     * </ol>
     *
     * <p>Severity = ERROR (ADR-3 technical-design).
     *
     * <p>Это ослабленная формулировка по сравнению с REQ-M-05 спецификации
     * (которая говорила «для каждой колонки»). Решение принято в Phase 3c
     * Developer-Code на основании source-of-truth-policy: эталоны Drive (L1)
     * демонстрируют легитимный паттерн «часть колонок без UI», а Acceptance
     * Scenario 2 спеки (§10) сформулирована именно как «нет ни одного
     * InputField/CheckBoxField/LabelField» — что согласуется с этой реализацией.
     * Главный кейс OC-22444 (полное отсутствие {@code <ChildItems>}) детектируется
     * корректно.
     */
    private void validateValueTableUiColumns(XmlNode root, List<ValidationIssue> issues) {
        // Шаг 1: собрать атрибуты с колонками.
        Map<String, AttributeColumns> attrColumns = collectAttributeColumns(root);
        if (attrColumns.isEmpty()) return;

        // Шаг 2: собрать все UI-таблицы <Table> и для каждой — DataPath её потомков.
        XmlNode childItems = root.child("ChildItems");
        if (childItems == null) return;

        List<TableInfo> tables = new ArrayList<>();
        collectTablesRecursive(childItems, tables);
        if (tables.isEmpty()) return;

        // Шаг 3: для каждой пары (атрибут с колонками, UI Table.dataPath = attrName) —
        // проверить, что хотя бы одна колонка атрибута связана через DataPath с UI-полем.
        for (Map.Entry<String, AttributeColumns> entry : attrColumns.entrySet()) {
            String attrName = entry.getKey();
            AttributeColumns ac = entry.getValue();
            if (ac.columnNames.isEmpty()) continue;

            for (TableInfo tbl : tables) {
                if (!attrName.equals(tbl.dataPath)) continue;

                boolean atLeastOneColumnLinked = false;
                for (String colName : ac.columnNames) {
                    String fullDataPath = attrName + "." + colName;
                    if (tbl.childDataPaths.contains(fullDataPath)) {
                        atLeastOneColumnLinked = true;
                        break;
                    }
                }
                if (!atLeastOneColumnLinked) {
                    String tableLabel = tbl.tableName != null ? tbl.tableName : "<unnamed>";
                    issues.add(ValidationIssue.error("FORM-116",
                            "UI Table '" + tableLabel + "' (DataPath='" + attrName + "')"
                                    + " has no UI columns linked to ValueTable attribute '" + attrName
                                    + "' (columns: " + ac.columnNames + ")."
                                    + " Add at least one <InputField>/<CheckBoxField>/<LabelField>"
                                    + " with DataPath='" + attrName + ".<column>' inside <ChildItems>"
                                    + " (use /form-edit skill).",
                            tbl.line, "/Form/ChildItems//Table[@name='" + tableLabel + "']"));
                }
            }
        }
    }

    /** Собрать имена атрибутов и их колонок (для FORM-116). */
    private Map<String, AttributeColumns> collectAttributeColumns(XmlNode root) {
        Map<String, AttributeColumns> result = new LinkedHashMap<>();
        XmlNode attributes = root.child("Attributes");
        if (attributes == null) return result;

        for (XmlNode attr : attributes.getChildren()) {
            if (isSystemAttributeElement(attr.getName())) continue;
            String name = attr.attr("name");
            if (name == null) name = attr.childText("name");
            if (name == null || name.isEmpty()) continue;

            AttributeColumns ac = new AttributeColumns();
            ac.attrLine = attr.getLine();

            // Признак ValueTable/ValueTree (liberal — учитываем canonical и non-canonical schema)
            ac.hasValueTableType = looksLikeValueTableType(attr);

            // Колонки: прямые потомки <Columns>/<Column>
            XmlNode columns = attr.child("Columns");
            if (columns != null) {
                for (XmlNode col : columns.getChildren()) {
                    if (!"Column".equals(col.getName())) continue;
                    String colName = col.attr("name");
                    if (colName == null) colName = col.childText("name");
                    if (colName != null && !colName.isEmpty()) {
                        ac.columnNames.add(colName);
                    }
                }
            }

            result.put(name, ac);
        }
        return result;
    }

    /**
     * Проверяет, похож ли тип атрибута на ValueTable/ValueTree.
     * Учитывает canonical {@code <Type><v8:Type>v8:ValueTable</v8:Type></Type>}
     * и non-canonical {@code <ValueType><Type>v8:ValueTable</Type></ValueType>}.
     */
    private boolean looksLikeValueTableType(XmlNode attr) {
        // Canonical: <Type><v8:Type>v8:ValueTable</v8:Type></Type>
        XmlNode typeNode = attr.child("Type");
        if (typeNode != null) {
            for (XmlNode t : typeNode.getChildren()) {
                if (!"Type".equals(t.getName())) continue;
                String text = t.getText();
                if (text == null) continue;
                String s = text.trim();
                if (s.endsWith(":ValueTable") || s.endsWith(":ValueTree")
                        || "ValueTable".equals(s) || "ValueTree".equals(s)) {
                    return true;
                }
            }
        }
        // Non-canonical: <ValueType><Type>v8:ValueTable</Type></ValueType>
        XmlNode valueType = attr.child("ValueType");
        if (valueType != null) {
            for (XmlNode t : valueType.getChildren()) {
                if (!"Type".equals(t.getName())) continue;
                String text = t.getText();
                if (text == null) continue;
                String s = text.trim();
                if (s.endsWith(":ValueTable") || s.endsWith(":ValueTree")
                        || "ValueTable".equals(s) || "ValueTree".equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Рекурсивный обход ChildItems для сбора всех UI {@code <Table>}. */
    private void collectTablesRecursive(XmlNode parent, List<TableInfo> tables) {
        for (XmlNode child : parent.getChildren()) {
            if ("Table".equals(child.getName())) {
                TableInfo info = new TableInfo();
                info.tableName = child.attr("name");
                if (info.tableName == null) info.tableName = child.childText("name");
                info.dataPath = child.childText("DataPath");
                info.line = child.getLine();
                info.childDataPaths = new HashSet<>();
                XmlNode tableChildItems = child.child("ChildItems");
                if (tableChildItems != null) {
                    collectColumnDataPathsRecursive(tableChildItems, info.childDataPaths);
                }
                tables.add(info);
            }
            // Углубляемся в любые ChildItems (Pages, Page, UsualGroup, Form root recursion)
            XmlNode innerChildItems = child.child("ChildItems");
            if (innerChildItems != null) {
                collectTablesRecursive(innerChildItems, tables);
            }
        }
    }

    /** Собрать DataPath потомков таблицы (рекурсивно через ColumnGroup и любую вложенность). */
    private void collectColumnDataPathsRecursive(XmlNode parent, Set<String> dataPaths) {
        for (XmlNode child : parent.getChildren()) {
            if (COLUMN_UI_FIELD_TYPES.contains(child.getName())) {
                String dp = child.childText("DataPath");
                if (dp != null && !dp.isEmpty()) {
                    dataPaths.add(dp);
                }
            }
            // Любая вложенность через ChildItems (включая ColumnGroup) — рекурсивно.
            XmlNode innerChildItems = child.child("ChildItems");
            if (innerChildItems != null) {
                collectColumnDataPathsRecursive(innerChildItems, dataPaths);
            }
        }
    }

    /** Информация об атрибуте формы (для FORM-116). */
    private static final class AttributeColumns {
        boolean hasValueTableType;
        int attrLine;
        final List<String> columnNames = new ArrayList<>();
    }

    /** Информация об UI-таблице формы (для FORM-116). */
    private static final class TableInfo {
        String tableName;
        String dataPath;
        int line;
        Set<String> childDataPaths;
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

    // TASK-171 V-3: метод isStandardCommand удалён — FORM-103 больше не пытается распознавать
    // «стандартные» команды по хрупкому белому списку. Теперь проверяются только ссылки
    // Form.Command.* (команды самой формы), остальные префиксы пропускаются (см. validateElements).

    /**
     * Системные элементы внутри <Attributes>, которые не являются пользовательскими атрибутами.
     * У них нет name/id атрибутов.
     */
    private static boolean isSystemAttributeElement(String elementName) {
        return "ConditionalAppearance".equals(elementName);
    }

    // ==================== FORM-117: Companions ====================

    /** Элементы → ожидаемый набор companion-тегов (parity с form-edit.py emit_element). */
    private static final Map<String, List<String>> COMPANIONS_BY_TAG = Map.ofEntries(
            Map.entry("InputField", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("CheckBoxField", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("LabelDecoration", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("LabelField", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("PictureField", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("CalendarField", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("PictureDecoration", List.of("ContextMenu", "ExtendedTooltip")),
            Map.entry("Table", List.of("ContextMenu", "AutoCommandBar",
                    "SearchStringAddition", "ViewStatusAddition", "SearchControlAddition")),
            Map.entry("Button", List.of("ExtendedTooltip")),
            Map.entry("UsualGroup", List.of("ExtendedTooltip")),
            Map.entry("Pages", List.of("ExtendedTooltip")),
            Map.entry("Page", List.of("ExtendedTooltip"))
    );

    private void validateElementCompanions(XmlNode parent, String parentPath, List<ValidationIssue> issues) {
        for (XmlNode elem : parent.getChildren()) {
            String tag = elem.getName();
            String elemPath = parentPath + "/" + tag;
            List<String> expected = COMPANIONS_BY_TAG.get(tag);
            if (expected != null) {
                for (String companion : expected) {
                    if (elem.child(companion) == null) {
                        issues.add(ValidationIssue.warning("FORM-117",
                                tag + " '" + elem.attr("name")
                                        + "' is missing required companion <" + companion + ">",
                                elem.getLine(), elemPath));
                    }
                }
            }
            XmlNode innerChildItems = elem.child("ChildItems");
            if (innerChildItems != null) {
                validateElementCompanions(innerChildItems, elemPath + "/ChildItems", issues);
            }
        }
    }

    // ==================== FORM-118: Event handler non-empty ====================

    private void validateEventHandlersNonEmpty(XmlNode root, List<ValidationIssue> issues) {
        // Form-level events
        XmlNode formEvents = root.child("Events");
        if (formEvents != null) {
            for (XmlNode evt : formEvents.getChildren()) {
                if (!"Event".equals(evt.getName())) continue;
                String name = evt.attr("name");
                String handler = evt.getText();
                if (name != null && (handler == null || handler.trim().isEmpty())) {
                    issues.add(ValidationIssue.warning("FORM-118",
                            "Form event '" + name + "' has empty handler name",
                            evt.getLine(), "/Form/Events/Event[" + name + "]"));
                }
            }
        }
        // Element-level events (рекурсивно)
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
            scanElementEvents(childItems, "/Form/ChildItems", issues);
        }
    }

    private void scanElementEvents(XmlNode parent, String parentPath, List<ValidationIssue> issues) {
        for (XmlNode elem : parent.getChildren()) {
            String elemPath = parentPath + "/" + elem.getName();
            XmlNode events = elem.child("Events");
            if (events != null) {
                for (XmlNode evt : events.getChildren()) {
                    if (!"Event".equals(evt.getName())) continue;
                    String name = evt.attr("name");
                    String handler = evt.getText();
                    if (name != null && (handler == null || handler.trim().isEmpty())) {
                        issues.add(ValidationIssue.warning("FORM-118",
                                "Element '" + elem.attr("name") + "' event '" + name
                                        + "' has empty handler name",
                                evt.getLine(), elemPath + "/Events/Event[" + name + "]"));
                    }
                }
            }
            XmlNode inner = elem.child("ChildItems");
            if (inner != null) {
                scanElementEvents(inner, elemPath + "/ChildItems", issues);
            }
        }
    }

    // ==================== FORM-119: MainAttribute count ====================

    private void validateMainAttributeCount(XmlNode attributes, List<ValidationIssue> issues) {
        if (attributes == null) return;
        int mainCount = 0;
        String firstMainName = null;
        List<String> extraMains = new ArrayList<>();
        for (XmlNode attr : attributes.getChildren()) {
            if (isSystemAttributeElement(attr.getName())) continue;
            if ("true".equalsIgnoreCase(attr.childText("MainAttribute"))) {
                mainCount++;
                String n = attr.attr("name");
                if (firstMainName == null) firstMainName = n;
                else if (n != null) extraMains.add(n);
            }
        }
        if (mainCount > 1) {
            issues.add(ValidationIssue.error("FORM-119",
                    "Form has " + mainCount + " MainAttribute entries; expected at most 1 "
                            + "(first='" + firstMainName + "', duplicates=" + extraMains + ")",
                    0, "/Form/Attributes"));
        }
    }

    // ==================== FORM-120: multilingual Title ====================

    private void validateMultilingualTitles(XmlNode root, List<ValidationIssue> issues) {
        // Проверяем Title у Attributes, Commands, элементов
        XmlNode attributes = root.child("Attributes");
        if (attributes != null) {
            for (XmlNode attr : attributes.getChildren()) {
                if (isSystemAttributeElement(attr.getName())) continue;
                checkTitleShape(attr, "/Form/Attributes/" + attr.attr("name"), issues);
            }
        }
        XmlNode commands = root.child("Commands");
        if (commands != null) {
            for (XmlNode cmd : commands.getChildren()) {
                checkTitleShape(cmd, "/Form/Commands/" + cmd.attr("name"), issues);
            }
        }
        XmlNode childItems = root.child("ChildItems");
        if (childItems != null) {
            checkTitlesRecursive(childItems, "/Form/ChildItems", issues);
        }
    }

    private void checkTitlesRecursive(XmlNode parent, String path, List<ValidationIssue> issues) {
        for (XmlNode elem : parent.getChildren()) {
            String p = path + "/" + elem.getName();
            checkTitleShape(elem, p, issues);
            XmlNode inner = elem.child("ChildItems");
            if (inner != null) checkTitlesRecursive(inner, p + "/ChildItems", issues);
        }
    }

    private void checkTitleShape(XmlNode owner, String ownerPath, List<ValidationIssue> issues) {
        XmlNode title = owner.child("Title");
        if (title == null) return;
        // Title либо пуст, либо имеет v8:item дочерний. Если есть только текст — ошибка.
        String rawText = title.getText();
        boolean hasItem = title.getChildren().stream()
                .anyMatch(c -> "item".equals(c.getName()) && V8_PREFIX.equals(c.getPrefix()));
        if (!hasItem && rawText != null && !rawText.trim().isEmpty()) {
            issues.add(ValidationIssue.warning("FORM-120",
                    "Title for '" + owner.attr("name") + "' is plain text; expected multilingual "
                            + "<Title><v8:item><v8:lang>ru</v8:lang><v8:content>...</v8:content></v8:item></Title>",
                    title.getLine(), ownerPath + "/Title"));
        }
    }
}

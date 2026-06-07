package io.github.onec.xmlgen.dsl;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.onec.xmlgen.editor.skd.SkdShorthandParser;
import io.github.onec.xmlgen.editor.skd.SkdTypeParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON DSL для схемы компоновки данных 1С (DataCompositionSchema).
 *
 * <p>Расширенная модель — соответствует skill <code>skd-dsl</code>:
 * наборы Query/Object/Union, calculatedFields, dataSetLinks, templates
 * (rows DSL), groupTemplates, расширенные параметры/поля с ролями,
 * conditionalAppearance с группами Or/And/Not.</p>
 *
 * <p>Совместим с предыдущим минимальным API (старые конструкторы сохранены
 * через {@code @JsonCreator} на каждом классе).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SkdDsl {

    private String name;
    private List<DataSource> dataSources;
    private List<DataSet> dataSets;
    private List<CalculatedField> calculatedFields;
    private List<TotalField> totalFields;
    private List<Parameter> parameters;
    private List<Template> templates;
    private List<GroupTemplate> groupTemplates;
    private List<DataSetLink> dataSetLinks;
    private List<SettingsVariant> settingsVariants;

    // ============================================================
    // Legacy 5-arg constructor — сохранён для существующих тестов.
    // ============================================================

    public SkdDsl(List<DataSource> dataSources,
                  List<DataSet> dataSets,
                  List<Parameter> parameters,
                  List<TotalField> totalFields,
                  List<SettingsVariant> settingsVariants) {
        this.dataSources = dataSources;
        this.dataSets = dataSets;
        this.parameters = parameters;
        this.totalFields = totalFields;
        this.settingsVariants = settingsVariants;
    }

    // ============================================================
    // Источник данных.
    // ============================================================

    @Value
    public static class DataSource {
        String name;
        String type;

        @JsonCreator
        public DataSource(@JsonProperty("name") String name,
                          @JsonProperty("type") String type) {
            this.name = name;
            this.type = type;
        }
    }

    // ============================================================
    // Набор данных (Query / Object / Union).
    // ============================================================

    /**
     * Набор данных СКД.
     *
     * <p>Тип определяется по присутствию полей:
     * <ul>
     *   <li>{@code query} → DataSetQuery</li>
     *   <li>{@code objectName} → DataSetObject</li>
     *   <li>{@code items} или {@code sourceDataSets} → DataSetUnion</li>
     * </ul>
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSet {
        private String name;
        private String source;
        /** Тип набора (опциональная явная подсказка: {@code query|object|union}). */
        private String type;

        // DataSetQuery
        private String query;
        private Boolean autoFillFields;

        // DataSetObject
        private String objectName;

        // DataSetUnion: один из двух способов задать источники.
        private List<DataSet> items;
        private List<String> sourceDataSets;

        // Поля + вычисляемые на уровне набора
        private List<Field> fields;
        private List<CalculatedField> calculatedFields;

        // Произвольные дополнительные ключи (фолбэк для редких полей).
        @JsonIgnore
        private Map<String, Object> extra;

        public DataSet(String name,
                       String source,
                       String query,
                       String objectName,
                       List<DataSet> items,
                       List<Field> fields,
                       Boolean autoFillFields) {
            this.name = name;
            this.source = source;
            this.query = query;
            this.objectName = objectName;
            this.items = items;
            this.fields = fields;
            this.autoFillFields = autoFillFields;
        }

        @JsonCreator
        public DataSet(@JsonProperty("name") String name,
                       @JsonProperty("source") String source,
                       @JsonProperty("type") String type,
                       @JsonProperty("query") String query,
                       @JsonProperty("autoFillFields") Boolean autoFillFields,
                       @JsonProperty("objectName") String objectName,
                       @JsonProperty("items") List<DataSet> items,
                       @JsonProperty("sourceDataSets") List<String> sourceDataSets,
                       @JsonProperty("fields") List<Field> fields,
                       @JsonProperty("calculatedFields") List<CalculatedField> calculatedFields) {
            this.name = name;
            this.source = source;
            this.type = type;
            this.query = query;
            this.autoFillFields = autoFillFields;
            this.objectName = objectName;
            this.items = items;
            this.sourceDataSets = sourceDataSets;
            this.fields = fields;
            this.calculatedFields = calculatedFields;
        }

        /**
         * Возвращает xsi:type для XML: {@code DataSetQuery|DataSetObject|DataSetUnion}.
         */
        @JsonIgnore
        public String getXsiType() {
            if (type != null) {
                switch (type.toLowerCase()) {
                    case "query": return "DataSetQuery";
                    case "object": return "DataSetObject";
                    case "union": return "DataSetUnion";
                    default: return type;
                }
            }
            if (query != null) return "DataSetQuery";
            if (objectName != null) return "DataSetObject";
            if (items != null || sourceDataSets != null) return "DataSetUnion";
            return "DataSetQuery";
        }

        /** Legacy alias — устаревший getter, оставлен для совместимости. */
        @JsonIgnore
        public String getTypeAlias() {
            return getXsiType();
        }
    }

    // ============================================================
    // Поле набора данных.
    // ============================================================

    /**
     * Поле набора данных. Поддерживает расширенные роли с key-value
     * аргументами ({@code balanceGroupName}, {@code dimension} и т.п.).
     *
     * <p>Тип может быть строкой ({@code "decimal(15,2),nonneg"}) или
     * списком строк для составного типа ({@code ["CatalogRef.А", "CatalogRef.Б"]}).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Field {
        private String dataPath;
        private String field;
        private Object title; // String или Map<String,String> (multilang)
        /** Строка или List<String> (составной тип). */
        private Object type;
        /** Роль: {@code "@account"}, {@code "@balance"}, {@code "@period"}, {@code "@dimension"}, {@code "@resource"}. */
        private String role;
        /** Дополнительные key-value атрибуты роли (например, balanceGroupName). */
        private Map<String, Object> roleAttributes;
        private String presentationExpression;
        private Map<String, Object> appearance;
        /** Ограничения использования: {@code {"field": true, "condition": true, "group": true, "order": true}}. */
        private Map<String, Boolean> useRestriction;
        private List<String> restrict;
        private List<Object> availableValues;

        public Field(@JsonProperty("dataPath") String dataPath,
                     @JsonProperty("field") String field,
                     @JsonProperty("title") Object title,
                     @JsonProperty("type") Object type,
                     @JsonProperty("role") String role,
                     @JsonProperty("roleAttributes") Map<String, Object> roleAttributes,
                     @JsonProperty("presentationExpression") String presentationExpression,
                     @JsonProperty("appearance") Map<String, Object> appearance,
                     @JsonProperty("useRestriction") Map<String, Boolean> useRestriction,
                     @JsonProperty("restrict") List<String> restrict,
                     @JsonProperty("availableValues") List<Object> availableValues) {
            this.dataPath = dataPath;
            this.field = field;
            this.title = title;
            this.type = type;
            this.role = role;
            this.roleAttributes = roleAttributes;
            this.presentationExpression = presentationExpression;
            this.appearance = appearance;
            this.useRestriction = useRestriction;
            this.restrict = restrict;
            this.availableValues = availableValues;
        }

        /** Legacy 4-arg конструктор. */
        public Field(String dataPath, String field, String title, String type) {
            this.dataPath = dataPath;
            this.field = field;
            this.title = title;
            this.type = type;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Field(String shorthand) {
            SkdShorthandParser.FieldDescriptor parsed = SkdShorthandParser.parseField(shorthand);
            this.dataPath = parsed.name;
            this.field = parsed.name;
            this.title = parsed.title;
            this.type = rawType(parsed.type);
            this.role = parsed.role;
            this.restrict = parsed.restrictions;
        }

        /** Удобный геттер — возвращает title как простую строку (или ru-вариант). */
        @JsonIgnore
        public String getTitleString() {
            if (title == null) return null;
            if (title instanceof String) return (String) title;
            if (title instanceof Map) {
                Object ru = ((Map<?, ?>) title).get("ru");
                return ru != null ? ru.toString() : null;
            }
            return title.toString();
        }

        /** Удобный геттер — type как строка (для одиночного типа). */
        @JsonIgnore
        public String getTypeString() {
            if (type instanceof String) return (String) type;
            return null;
        }

        /** Удобный геттер — type как список (для составного типа). */
        @JsonIgnore
        @SuppressWarnings("unchecked")
        public List<String> getTypeList() {
            if (type instanceof List) {
                List<String> out = new ArrayList<>();
                for (Object o : (List<Object>) type) out.add(o.toString());
                return out;
            }
            return null;
        }
    }

    // ============================================================
    // Calculated field.
    // ============================================================

    /**
     * Вычисляемое поле. Может задаваться объектно или строкой
     * (shorthand: {@code "Имя [Заголовок]: тип = Выражение"}).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CalculatedField {
        private String name;
        private Object title;
        private String expression;
        private Object type;
        private String role;
        private Map<String, Object> roleAttributes;
        private Object useRestriction;

        public CalculatedField(@JsonProperty("name") String name,
                               @JsonProperty("title") Object title,
                               @JsonProperty("expression") String expression,
                               @JsonProperty("type") Object type,
                               @JsonProperty("role") String role,
                               @JsonProperty("roleAttributes") Map<String, Object> roleAttributes,
                               @JsonProperty("useRestriction") Object useRestriction) {
            this.name = name;
            this.title = title;
            this.expression = expression;
            this.type = type;
            this.role = role;
            this.roleAttributes = roleAttributes;
            this.useRestriction = useRestriction;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public CalculatedField(String shorthand) {
            ParsedCalculatedField parsed = parseCalculatedField(shorthand);
            this.name = parsed.name;
            this.title = parsed.title;
            this.expression = parsed.expression;
            this.type = parsed.type;
            this.useRestriction = parsed.useRestriction;
        }

        @JsonIgnore
        public String getTypeString() {
            if (type instanceof String) return (String) type;
            return null;
        }
    }

    // ============================================================
    // Параметр.
    // ============================================================

    /**
     * Параметр схемы. Поддерживает флаги ({@code hidden}, {@code valueListAllowed},
     * {@code denyIncompleteValues}, {@code use}) и {@code availableValues}.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Parameter {
        private String name;
        private Object title;
        private String type;
        private Object value;
        private Boolean hidden;
        private Boolean valueListAllowed;
        private Boolean availableAsField;
        private Boolean denyIncompleteValues;
        private Boolean autoDates;
        /** {@code Always | Auto | DontUse}. */
        private String use;
        private List<Object> availableValues;

        public Parameter(@JsonProperty("name") String name,
                         @JsonProperty("title") Object title,
                         @JsonProperty("type") String type,
                         @JsonProperty("value") Object value,
                         @JsonProperty("hidden") Boolean hidden,
                         @JsonProperty("valueListAllowed") Boolean valueListAllowed,
                         @JsonProperty("availableAsField") Boolean availableAsField,
                         @JsonProperty("denyIncompleteValues") Boolean denyIncompleteValues,
                         @JsonProperty("autoDates") Boolean autoDates,
                         @JsonProperty("use") String use,
                         @JsonProperty("availableValues") List<Object> availableValues) {
            this.name = name;
            this.title = title;
            this.type = type;
            this.value = value;
            this.hidden = hidden;
            this.valueListAllowed = valueListAllowed;
            this.availableAsField = availableAsField;
            this.denyIncompleteValues = denyIncompleteValues;
            this.autoDates = autoDates;
            this.use = use;
            this.availableValues = availableValues;
        }

        /** Legacy 4-arg конструктор. */
        public Parameter(String name, String title, String type, Object value) {
            this.name = name;
            this.title = title;
            this.type = type;
            this.value = value;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Parameter(String shorthand) {
            SkdShorthandParser.ParameterDescriptor parsed = SkdShorthandParser.parseParameter(shorthand);
            this.name = parsed.name;
            this.title = parsed.title;
            this.type = rawType(parsed.type);
            this.value = parsed.value;
            this.hidden = parsed.flags.contains("hidden") ? true : null;
            this.valueListAllowed = parsed.flags.contains("valueList") ? true : null;
            this.autoDates = parsed.flags.contains("autoDates") ? true : null;
            this.use = parsed.flags.contains("always") ? "Always" : null;
        }

        @JsonIgnore
        public String getTitleString() {
            if (title == null) return null;
            if (title instanceof String) return (String) title;
            if (title instanceof Map) {
                Object ru = ((Map<?, ?>) title).get("ru");
                return ru != null ? ru.toString() : null;
            }
            return title.toString();
        }
    }

    // ============================================================
    // Итоговое поле.
    // ============================================================

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TotalField {
        private String dataPath;
        private String expression;
        private List<String> group;

        public TotalField(@JsonProperty("dataPath") String dataPath,
                          @JsonProperty("expression") String expression,
                          @JsonProperty("group") List<String> group) {
            this.dataPath = dataPath;
            this.expression = expression;
            this.group = group;
        }

        /** Legacy 2-arg конструктор. */
        public TotalField(String dataPath, String expression) {
            this.dataPath = dataPath;
            this.expression = expression;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public TotalField(String shorthand) {
            SkdShorthandParser.TotalDescriptor parsed = SkdShorthandParser.parseTotal(shorthand);
            this.dataPath = parsed.dataPath;
            this.expression = parsed.expression;
        }
    }

    // ============================================================
    // Template — DSL шаблонов вывода.
    // ============================================================

    /**
     * Шаблон вывода. Может быть raw XML ({@code template}: строка с XML),
     * либо табличным DSL ({@code rows}: список строк ячеек).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Template {
        private String name;
        /** Тип шаблона: {@code group|header|footer}. По умолчанию — без атрибута. */
        private String type;
        /** Имя предустановленного стиля ({@code header|data|subheader|total}). */
        private String style;
        /**
         * Строки шаблона. Каждый элемент — либо список ячеек ({@code List<Object>}),
         * либо объект с ключом {@code cells}.
         */
        private List<Object> rows;
        /** Ширины колонок. Каждый элемент — {@code Integer} или {@code String} ("10-20"). */
        private List<Object> widths;
        private Double minHeight;
        /** Параметры шаблона ({@code drilldown}, {@code expression}, {@code format}, ...). */
        private List<TemplateParameter> parameters;
        /** Raw XML — если DSL не используется. */
        private String template;

        @JsonCreator
        public Template(@JsonProperty("name") String name,
                        @JsonProperty("type") String type,
                        @JsonProperty("style") String style,
                        @JsonProperty("rows") List<Object> rows,
                        @JsonProperty("widths") List<Object> widths,
                        @JsonProperty("minHeight") Double minHeight,
                        @JsonProperty("parameters") List<TemplateParameter> parameters,
                        @JsonProperty("template") String template) {
            this.name = name;
            this.type = type;
            this.style = style;
            this.rows = rows;
            this.widths = widths;
            this.minHeight = minHeight;
            this.parameters = parameters;
            this.template = template;
        }
    }

    /**
     * Параметр шаблона (ExpressionAreaTemplateParameter или DetailsAreaTemplateParameter).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TemplateParameter {
        private String name;
        private String expression;
        private String format;
        /** Если задан — генерируется парный DetailsAreaTemplateParameter с расшифровкой. */
        private String drilldown;

        @JsonCreator
        public TemplateParameter(@JsonProperty("name") String name,
                                 @JsonProperty("expression") String expression,
                                 @JsonProperty("format") String format,
                                 @JsonProperty("drilldown") String drilldown) {
            this.name = name;
            this.expression = expression;
            this.format = format;
            this.drilldown = drilldown;
        }
    }

    /**
     * Привязка макета к группировке.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GroupTemplate {
        private String groupField;
        private String groupName;
        /** {@code Header | OverallHeader | GroupHeader | Footer | OverallFooter}. */
        private String templateType;
        private String template;

        @JsonCreator
        public GroupTemplate(@JsonProperty("groupField") String groupField,
                             @JsonProperty("groupName") String groupName,
                             @JsonProperty("templateType") String templateType,
                             @JsonProperty("template") String template) {
            this.groupField = groupField;
            this.groupName = groupName;
            this.templateType = templateType;
            this.template = template;
        }
    }

    // ============================================================
    // Связи наборов.
    // ============================================================

    /**
     * Связь между наборами данных. Поддерживает обе формы:
     * <ul>
     *   <li>плоская: {@code sourceExpression}/{@code destExpression};</li>
     *   <li>через {@code items}: несколько пар выражений.</li>
     * </ul>
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSetLink {
        @JsonAlias({"source", "sourceDataSet"})
        private String source;
        @JsonAlias({"dest", "target", "destDataSet"})
        private String dest;
        @JsonAlias("sourceExpr")
        private String sourceExpression;
        @JsonAlias({"destExpr", "targetExpr", "destExpression", "targetExpression"})
        private String destExpression;
        private String parameter;
        private Boolean parameterListAllowed;
        private List<DataSetLinkItem> items;

        public DataSetLink(@JsonProperty("source") String source,
                           @JsonProperty("dest") String dest,
                           @JsonProperty("sourceExpression") String sourceExpression,
                           @JsonProperty("destExpression") String destExpression,
                           @JsonProperty("parameter") String parameter,
                           @JsonProperty("parameterListAllowed") Boolean parameterListAllowed,
                           @JsonProperty("items") List<DataSetLinkItem> items) {
            this.source = source;
            this.dest = dest;
            this.sourceExpression = sourceExpression;
            this.destExpression = destExpression;
            this.parameter = parameter;
            this.parameterListAllowed = parameterListAllowed;
            this.items = items;
        }
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DataSetLinkItem {
        @JsonAlias("sourceExpr")
        private String sourceExpression;
        @JsonAlias({"destExpr", "targetExpr", "destExpression", "targetExpression"})
        private String destExpression;
        private String parameter;
        private Boolean parameterListAllowed;

        public DataSetLinkItem(@JsonProperty("sourceExpression") String sourceExpression,
                               @JsonProperty("destExpression") String destExpression,
                               @JsonProperty("parameter") String parameter,
                               @JsonProperty("parameterListAllowed") Boolean parameterListAllowed) {
            this.sourceExpression = sourceExpression;
            this.destExpression = destExpression;
            this.parameter = parameter;
            this.parameterListAllowed = parameterListAllowed;
        }
    }

    // ============================================================
    // Вариант настроек.
    // ============================================================

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SettingsVariant {
        private String name;
        private Object presentation; // String или Map (multilang)
        private Settings settings;

        @JsonCreator
        public SettingsVariant(@JsonProperty("name") String name,
                               @JsonProperty("presentation") Object presentation,
                               @JsonProperty("settings") Settings settings) {
            this.name = name;
            this.presentation = presentation;
            this.settings = settings;
        }

        /** Legacy 3-arg(String). */
        public SettingsVariant(String name, String presentation, Settings settings) {
            this.name = name;
            this.presentation = presentation;
            this.settings = settings;
        }

        @JsonIgnore
        public String getPresentationString() {
            if (presentation == null) return null;
            if (presentation instanceof String) return (String) presentation;
            if (presentation instanceof Map) {
                Object ru = ((Map<?, ?>) presentation).get("ru");
                return ru != null ? ru.toString() : null;
            }
            return presentation.toString();
        }
    }

    // ============================================================
    // Условное оформление.
    // ============================================================

    /**
     * Элемент условного оформления. Поддерживает либо плоский фильтр
     * ({@code filter}: список строк), либо группы Or/And/Not
     * ({@code filterGroup}: вложенная структура).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConditionalAppearanceItem {
        private List<String> selection;
        private List<String> filter;
        private FilterGroup filterGroup;
        private Map<String, Object> appearance;
        private Object presentation;
        private String viewMode;
        private String userSettingID;

        @JsonCreator
        public ConditionalAppearanceItem(@JsonProperty("selection") List<String> selection,
                                          @JsonProperty("filter") List<String> filter,
                                          @JsonProperty("filterGroup") FilterGroup filterGroup,
                                          @JsonProperty("appearance") Map<String, Object> appearance,
                                          @JsonProperty("presentation") Object presentation,
                                          @JsonProperty("viewMode") String viewMode,
                                          @JsonProperty("userSettingID") String userSettingID) {
            this.selection = selection;
            this.filter = filter;
            this.filterGroup = filterGroup;
            this.appearance = appearance;
            this.presentation = presentation;
            this.viewMode = viewMode;
            this.userSettingID = userSettingID;
        }

        /** Legacy 4-arg конструктор. */
        public ConditionalAppearanceItem(List<String> selection,
                                          List<String> filter,
                                          Map<String, Object> appearance,
                                          String presentation) {
            this.selection = selection;
            this.filter = filter;
            this.appearance = appearance;
            this.presentation = presentation;
        }

        @JsonIgnore
        public String getPresentationString() {
            if (presentation == null) return null;
            if (presentation instanceof String) return (String) presentation;
            return presentation.toString();
        }
    }

    /**
     * Группа фильтров (Or / And / Not). Может содержать вложенные
     * группы и плоские элементы (строки или {@link FilterItem}).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FilterGroup {
        /** {@code Or | And | Not}. */
        private String group;
        /** Элементы группы — строка ({@code "Поле op value"}) или объект {@link FilterItem}/{@link FilterGroup}. */
        private List<Object> items;

        @JsonCreator
        public FilterGroup(@JsonProperty("group") String group,
                           @JsonProperty("items") List<Object> items) {
            this.group = group;
            this.items = items;
        }
    }

    /**
     * Объектная форма элемента фильтра. Если задана — генерируется
     * напрямую без shorthand-разбора.
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FilterItem {
        private String field;
        private String op;
        private Object value;
        private String viewMode;
        private String userSettingID;

        @JsonCreator
        public FilterItem(@JsonProperty("field") String field,
                          @JsonProperty("op") String op,
                          @JsonProperty("value") Object value,
                          @JsonProperty("viewMode") String viewMode,
                          @JsonProperty("userSettingID") String userSettingID) {
            this.field = field;
            this.op = op;
            this.value = value;
            this.viewMode = viewMode;
            this.userSettingID = userSettingID;
        }
    }

    // ============================================================
    // Настройки варианта.
    // ============================================================

    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Settings {
        private List<String> selection;
        private List<String> filter;
        private List<String> order;
        private List<ConditionalAppearanceItem> conditionalAppearance;
        private Map<String, Object> outputParameters;
        private List<Structure> structure;
        /** Может быть {@code "auto"} (String) либо список строк/объектов. */
        private Object dataParameters;

        @JsonCreator
        public Settings(@JsonProperty("selection") List<String> selection,
                        @JsonProperty("filter") List<String> filter,
                        @JsonProperty("order") List<String> order,
                        @JsonProperty("conditionalAppearance") List<ConditionalAppearanceItem> conditionalAppearance,
                        @JsonProperty("outputParameters") Map<String, Object> outputParameters,
                        @JsonProperty("structure") List<Structure> structure,
                        @JsonProperty("dataParameters") Object dataParameters) {
            this.selection = selection;
            this.filter = filter;
            this.order = order;
            this.conditionalAppearance = conditionalAppearance;
            this.outputParameters = outputParameters;
            this.structure = structure;
            this.dataParameters = dataParameters;
        }

        /** Legacy 6-arg конструктор. */
        public Settings(List<String> selection,
                        List<String> filter,
                        List<String> order,
                        List<ConditionalAppearanceItem> conditionalAppearance,
                        Map<String, Object> outputParameters,
                        List<Structure> structure) {
            this.selection = selection;
            this.filter = filter;
            this.order = order;
            this.conditionalAppearance = conditionalAppearance;
            this.outputParameters = outputParameters;
            this.structure = structure;
        }
    }

    /**
     * Элемент структуры варианта (группировка / таблица / диаграмма).
     */
    @Data
    @NoArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Structure {
        /** {@code group | table | chart}. */
        private String type;
        private String name;
        @JsonAlias({"groupBy", "groupFields"})
        private List<String> groupBy;
        private List<String> selection;
        private List<String> order;
        private List<String> filter;
        private Map<String, Object> outputParameters;
        private List<Structure> children;

        public Structure(@JsonProperty("type") String type,
                         @JsonProperty("name") String name,
                         @JsonProperty("groupBy") List<String> groupBy,
                         @JsonProperty("groupFields") List<String> groupFields,
                         @JsonProperty("selection") List<String> selection,
                         @JsonProperty("order") List<String> order,
                         @JsonProperty("filter") List<String> filter,
                         @JsonProperty("outputParameters") Map<String, Object> outputParameters,
                         @JsonProperty("children") List<Structure> children) {
            this.type = type;
            this.name = name;
            this.groupBy = groupBy != null ? groupBy : groupFields;
            this.selection = selection;
            this.order = order;
            this.filter = filter;
            this.outputParameters = outputParameters;
            this.children = children;
        }

        /** Legacy 3-arg конструктор. */
        public Structure(String type, List<String> groupBy, List<String> selection) {
            this.type = type;
            this.groupBy = groupBy;
            this.selection = selection;
        }

        @JsonProperty("groupFields")
        public void setGroupFields(List<String> groupFields) {
            this.groupBy = groupFields;
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Structure(String shorthand) {
            Structure parsed = parseStructureShorthand(shorthand);
            this.type = parsed.type;
            this.name = parsed.name;
            this.groupBy = parsed.groupBy;
            this.selection = parsed.selection;
            this.order = parsed.order;
            this.filter = parsed.filter;
            this.outputParameters = parsed.outputParameters;
            this.children = parsed.children;
        }
    }

    private static String rawType(List<SkdTypeParser.TypePart> parts) {
        if (parts == null || parts.isEmpty()) return null;
        List<String> raw = new ArrayList<>();
        for (SkdTypeParser.TypePart part : parts) {
            raw.add(part.raw);
        }
        return String.join("|", raw);
    }

    private static ParsedCalculatedField parseCalculatedField(String shorthand) {
        String rest = shorthand == null ? "" : shorthand.trim();
        ParsedCalculatedField parsed = new ParsedCalculatedField();
        int nameEnd = findNameEnd(rest);
        if (nameEnd <= 0) {
            throw new IllegalArgumentException("calculatedField shorthand: expected name");
        }
        parsed.name = rest.substring(0, nameEnd).trim();
        rest = rest.substring(nameEnd).trim();

        if (rest.startsWith("[")) {
            int close = rest.indexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("calculatedField shorthand: unmatched '['");
            }
            parsed.title = rest.substring(1, close);
            rest = rest.substring(close + 1).trim();
        }

        int equals = rest.indexOf('=');
        String beforeExpr = equals >= 0 ? rest.substring(0, equals).trim() : rest;
        parsed.expression = equals >= 0 ? rest.substring(equals + 1).trim() : null;
        if (beforeExpr.startsWith(":")) {
            parsed.type = beforeExpr.substring(1).trim();
        }
        parsed.useRestriction = parseRestrictFlags(parsed.type);
        if (parsed.type instanceof String) {
            String type = (String) parsed.type;
            int hash = type.indexOf('#');
            if (hash >= 0) {
                parsed.type = type.substring(0, hash).trim();
            }
        }
        if (parsed.expression != null) {
            int hash = parsed.expression.indexOf('#');
            if (hash >= 0) {
                parsed.useRestriction = parseRestrictFlags(parsed.expression.substring(hash));
                parsed.expression = parsed.expression.substring(0, hash).trim();
            }
        }
        return parsed;
    }

    private static int findNameEnd(String value) {
        int end = 0;
        while (end < value.length()) {
            char ch = value.charAt(end);
            if (Character.isWhitespace(ch) || ch == '[' || ch == ':' || ch == '=') break;
            end++;
        }
        return end;
    }

    private static Map<String, Boolean> parseRestrictFlags(Object value) {
        if (!(value instanceof String)) return null;
        String text = (String) value;
        if (!text.contains("#")) return null;
        Map<String, Boolean> out = new LinkedHashMap<>();
        for (String token : text.split("\\s+")) {
            if (!token.startsWith("#")) continue;
            switch (token.substring(1)) {
                case "noField": out.put("field", true); break;
                case "noFilter":
                case "noCondition": out.put("condition", true); break;
                case "noGroup": out.put("group", true); break;
                case "noOrder": out.put("order", true); break;
                default: break;
            }
        }
        return out.isEmpty() ? null : out;
    }

    private static Structure parseStructureShorthand(String shorthand) {
        String spec = shorthand == null ? "" : shorthand.trim();
        if (spec.isEmpty()) {
            throw new IllegalArgumentException("structure shorthand is empty");
        }
        String[] parts = spec.split(">");
        Structure root = null;
        Structure current = null;
        for (String part : parts) {
            String token = part.trim();
            if (token.isEmpty()) continue;
            Structure item = new Structure();
            item.type = "group";
            item.groupBy = "details".equalsIgnoreCase(token) ? List.of() : List.of(token);
            if (root == null) {
                root = item;
            } else {
                current.children = List.of(item);
            }
            current = item;
        }
        if (root == null) {
            throw new IllegalArgumentException("structure shorthand has no items");
        }
        return root;
    }

    private static final class ParsedCalculatedField {
        String name;
        Object title;
        String expression;
        Object type;
        Object useRestriction;
    }
}

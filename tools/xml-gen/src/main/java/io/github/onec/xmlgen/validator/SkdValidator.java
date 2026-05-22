package io.github.onec.xmlgen.validator;

import java.util.*;

/**
 * Валидатор для XML схемы компоновки данных (DataCompositionSchema).
 * <p>
 * Level 1 (Structure): SKD-001..005
 * Level 2 (Semantic):  SKD-101..107
 */
public class SkdValidator implements XmlValidator {

    private static final String NS_DCS = "http://v8.1c.ru/8.1/data-composition-system/schema";

    private static final Set<String> KNOWN_DATASET_TYPES = Set.of(
            "DataSetQuery", "DataSetObject", "DataSetUnion"
    );

    private static final Set<String> KNOWN_COMPARISON_TYPES = Set.of(
            "Equal", "NotEqual", "Greater", "GreaterOrEqual",
            "Less", "LessOrEqual", "InList", "NotInList",
            "Contains", "NotContains", "BeginsWith",
            "Filled", "NotFilled", "InHierarchy", "NotInHierarchy",
            "InListByHierarchy", "NotInListByHierarchy"
    );

    private static final Set<String> KNOWN_ORDER_TYPES = Set.of("Asc", "Desc");

    @Override
    public String objectType() {
        return "skd";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return "DataCompositionSchema".equals(document.getRootElement());
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

        // SKD-001: Root <DataCompositionSchema>
        if (!"DataCompositionSchema".equals(root.getName())) {
            issues.add(ValidationIssue.error("SKD-001",
                    "Expected root element 'DataCompositionSchema', found '" + root.getName() + "'",
                    root.getLine(), "/"));
            return;
        }

        // SKD-002: ≥1 <dataSource>
        List<XmlNode> dataSources = root.children("dataSource");
        if (dataSources.isEmpty()) {
            issues.add(ValidationIssue.warning("SKD-002",
                    "No <dataSource> elements found",
                    root.getLine(), "/DataCompositionSchema"));
        }

        // SKD-003 + SKD-004: DataSets
        List<XmlNode> dataSets = root.children("dataSet");
        for (int i = 0; i < dataSets.size(); i++) {
            XmlNode ds = dataSets.get(i);
            String dsPath = "/DataCompositionSchema/dataSet[" + (i + 1) + "]";

            // SKD-003: xsi:type обязательный
            String xsiType = ds.attr("xsi:type");
            if (xsiType == null || xsiType.isEmpty()) {
                issues.add(ValidationIssue.error("SKD-003",
                        "DataSet missing required attribute xsi:type",
                        ds.getLine(), dsPath));
            }

            // SKD-004: DataSetQuery должен иметь <query>
            if ("DataSetQuery".equals(xsiType)) {
                String query = ds.childText("query");
                if (query == null || query.isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-004",
                            "DataSetQuery missing <query> element",
                            ds.getLine(), dsPath));
                }
            }

            // Рекурсивно проверяем вложенные dataSets (DataSetUnion)
            validateNestedDataSets(ds, dsPath, issues);
        }

        // SKD-005: ≥1 <settingsVariant>
        List<XmlNode> settingsVariants = root.children("settingsVariant");
        if (settingsVariants.isEmpty()) {
            issues.add(ValidationIssue.warning("SKD-005",
                    "No <settingsVariant> elements found",
                    root.getLine(), "/DataCompositionSchema"));
        }
    }

    private void validateNestedDataSets(XmlNode parent, String parentPath, List<ValidationIssue> issues) {
        List<XmlNode> items = parent.children("item");
        for (int i = 0; i < items.size(); i++) {
            XmlNode item = items.get(i);
            String itemPath = parentPath + "/item[" + (i + 1) + "]";

            String xsiType = item.attr("xsi:type");
            if (xsiType == null || xsiType.isEmpty()) {
                issues.add(ValidationIssue.error("SKD-003",
                        "Nested DataSet missing required attribute xsi:type",
                        item.getLine(), itemPath));
            }

            if ("DataSetQuery".equals(xsiType)) {
                String query = item.childText("query");
                if (query == null || query.isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-004",
                            "Nested DataSetQuery missing <query> element",
                            item.getLine(), itemPath));
                }
            }
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Соберём все известные имена наборов и полей — для проверки ссылочной целостности.
        Set<String> dataSetNames = new HashSet<>();
        Set<String> allFieldNames = new HashSet<>();
        List<XmlNode> dataSets = root.children("dataSet");
        for (XmlNode ds : dataSets) {
            String name = ds.childText("name");
            if (name != null) dataSetNames.add(name);
            for (XmlNode f : ds.children("field")) {
                String fld = f.childText("field");
                if (fld != null) allFieldNames.add(fld);
                String dp = f.childText("dataPath");
                if (dp != null) allFieldNames.add(dp);
            }
            for (XmlNode cf : ds.children("calculatedField")) {
                String n = cf.childText("dataPath");
                if (n != null) allFieldNames.add(n);
            }
        }
        for (XmlNode cf : root.children("calculatedField")) {
            String n = cf.childText("dataPath");
            if (n != null) allFieldNames.add(n);
        }

        // SKD-101: DataSet.xsi:type — известный тип
        for (int i = 0; i < dataSets.size(); i++) {
            XmlNode ds = dataSets.get(i);
            String dsPath = "/DataCompositionSchema/dataSet[" + (i + 1) + "]";

            String xsiType = ds.attr("xsi:type");
            if (xsiType != null && !xsiType.isEmpty() && !KNOWN_DATASET_TYPES.contains(xsiType)) {
                issues.add(ValidationIssue.error("SKD-101",
                        "Unknown DataSet type '" + xsiType + "', expected: " + KNOWN_DATASET_TYPES,
                        ds.getLine(), dsPath + "/@xsi:type"));
            }

            // SKD-107: DataSetQuery должен иметь <dataSource>
            if ("DataSetQuery".equals(xsiType)) {
                String dataSource = ds.childText("dataSource");
                if (dataSource == null || dataSource.isEmpty()) {
                    issues.add(ValidationIssue.warning("SKD-107",
                            "DataSetQuery has no <dataSource> reference",
                            ds.getLine(), dsPath));
                }
            }

            // SKD-108: calculatedField должен иметь expression + valueType.
            List<XmlNode> calcFields = ds.children("calculatedField");
            for (int j = 0; j < calcFields.size(); j++) {
                XmlNode cf = calcFields.get(j);
                String cfPath = dsPath + "/calculatedField[" + (j + 1) + "]";
                if (cf.childText("expression") == null || cf.childText("expression").isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-108",
                            "calculatedField missing <expression>",
                            cf.getLine(), cfPath));
                }
                if (cf.child("valueType") == null) {
                    issues.add(ValidationIssue.error("SKD-108",
                            "calculatedField missing <valueType> (type required for calculated fields)",
                            cf.getLine(), cfPath));
                }
            }

            // Проверяем поля в settings/filter
            validateFields(ds, dsPath, issues);
        }

        // SKD-109: dataSetLink ссылается на существующие наборы.
        List<XmlNode> links = root.children("dataSetLink");
        for (int i = 0; i < links.size(); i++) {
            XmlNode link = links.get(i);
            String linkPath = "/DataCompositionSchema/dataSetLink[" + (i + 1) + "]";
            String src = link.childText("sourceDataSet");
            String dst = link.childText("destDataSet");
            if (src != null && !src.isEmpty() && !dataSetNames.contains(src)) {
                issues.add(ValidationIssue.error("SKD-109",
                        "dataSetLink references unknown source dataSet '" + src + "'",
                        link.getLine(), linkPath + "/sourceDataSet"));
            }
            if (dst != null && !dst.isEmpty() && !dataSetNames.contains(dst)) {
                issues.add(ValidationIssue.error("SKD-109",
                        "dataSetLink references unknown dest dataSet '" + dst + "'",
                        link.getLine(), linkPath + "/destDataSet"));
            }
        }

        // Проверяем settingsVariants
        List<XmlNode> settingsVariants = root.children("settingsVariant");
        for (int i = 0; i < settingsVariants.size(); i++) {
            XmlNode sv = settingsVariants.get(i);
            String svPath = "/DataCompositionSchema/settingsVariant[" + (i + 1) + "]";

            XmlNode settings = sv.child("settings");
            if (settings != null) {
                validateSettings(settings, svPath + "/settings", issues);
            }
        }
    }

    private void validateSettings(XmlNode settings, String path, List<ValidationIssue> issues) {
        // Проверяем filter
        XmlNode filter = settings.child("filter");
        if (filter != null) {
            validateFilterItems(filter, path + "/filter", issues);
        }

        // Проверяем order
        XmlNode order = settings.child("order");
        if (order != null) {
            List<XmlNode> orderItems = order.children("item");
            for (int i = 0; i < orderItems.size(); i++) {
                XmlNode item = orderItems.get(i);
                String itemPath = path + "/order/item[" + (i + 1) + "]";

                // SKD-103: orderType
                String orderType = item.childText("orderType");
                if (orderType != null && !KNOWN_ORDER_TYPES.contains(orderType)) {
                    issues.add(ValidationIssue.error("SKD-103",
                            "Unknown orderType '" + orderType + "', expected: Asc or Desc",
                            item.getLine(), itemPath + "/orderType"));
                }

                // SKD-106: Поле непустое
                String field = item.childText("field");
                if (field == null || field.isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-106",
                            "Order item has empty <field>",
                            item.getLine(), itemPath + "/field"));
                }
            }
        }

        // Проверяем selection
        XmlNode selection = settings.child("selection");
        if (selection != null) {
            List<XmlNode> selItems = selection.children("item");
            for (int i = 0; i < selItems.size(); i++) {
                XmlNode item = selItems.get(i);
                String itemPath = path + "/selection/item[" + (i + 1) + "]";

                String field = item.childText("field");
                if (field == null || field.isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-106",
                            "Selection item has empty <field>",
                            item.getLine(), itemPath + "/field"));
                }
            }
        }
    }

    private void validateFilterItems(XmlNode filter, String filterPath, List<ValidationIssue> issues) {
        List<XmlNode> items = filter.children("item");
        for (int i = 0; i < items.size(); i++) {
            XmlNode item = items.get(i);
            String itemPath = filterPath + "/item[" + (i + 1) + "]";

            // SKD-102: comparisonType
            String comparisonType = item.childText("comparisonType");
            if (comparisonType != null && !comparisonType.isEmpty()
                    && !KNOWN_COMPARISON_TYPES.contains(comparisonType)) {
                issues.add(ValidationIssue.error("SKD-102",
                        "Unknown comparisonType '" + comparisonType + "'",
                        item.getLine(), itemPath + "/comparisonType"));
            }

            // SKD-106: Поле непустое
            String leftValue = item.childText("leftValue");
            if (leftValue == null || leftValue.isEmpty()) {
                // Проверяем альтернативный элемент <field>
                String field = item.childText("field");
                if (field == null || field.isEmpty()) {
                    issues.add(ValidationIssue.error("SKD-106",
                            "Filter item has no <leftValue> or <field>",
                            item.getLine(), itemPath));
                }
            }
        }
    }

    private void validateFields(XmlNode dataSet, String dsPath, List<ValidationIssue> issues) {
        List<XmlNode> fields = dataSet.children("field");
        for (int i = 0; i < fields.size(); i++) {
            XmlNode field = fields.get(i);
            String fieldPath = dsPath + "/field[" + (i + 1) + "]";

            // Проверяем xsi:type на значениях
            String xsiType = field.attr("xsi:type");
            if (xsiType != null && !xsiType.isEmpty()) {
                // SKD-104: xsi:type должен быть валидным
                // Допустимые: DataSetFieldField, DataSetFieldFolder
                Set<String> knownFieldTypes = Set.of("DataSetFieldField", "DataSetFieldFolder");
                if (!knownFieldTypes.contains(xsiType)) {
                    issues.add(ValidationIssue.warning("SKD-104",
                            "Unknown field xsi:type '" + xsiType + "'",
                            field.getLine(), fieldPath + "/@xsi:type"));
                }
            }
        }
    }
}

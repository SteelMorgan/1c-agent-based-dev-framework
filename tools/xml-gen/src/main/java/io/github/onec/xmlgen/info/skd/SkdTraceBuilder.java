package io.github.onec.xmlgen.info.skd;

import io.github.onec.xmlgen.validator.XmlNode;

import java.util.*;

/**
 * Builds a trace graph: DataSet → CalculatedField → Resource/Total → Variant.Selection.
 *
 * <p>Given a field name (dataPath or title substring), this class resolves the full chain:</p>
 * <ol>
 *   <li>Which dataset(s) contain the field.</li>
 *   <li>Which calculatedFields reference it in their expression.</li>
 *   <li>Which totalFields (resources) are defined for it.</li>
 *   <li>Which settingsVariants use it in selection/order.</li>
 * </ol>
 */
public class SkdTraceBuilder {

    // ============================================================
    // Public model
    // ============================================================

    public static class TraceNode {
        public final String dataSetName;
        public final String dataSetType;
        public final List<FieldNode> fields = new ArrayList<>();

        public TraceNode(String dataSetName, String dataSetType) {
            this.dataSetName = dataSetName;
            this.dataSetType = dataSetType;
        }
    }

    public static class FieldNode {
        public final String fieldName;
        public final String fieldType;
        public final String role;
        public final List<TotalNode> totals = new ArrayList<>();
        public final List<CalcNode> calcFields = new ArrayList<>();
        public final List<VariantRef> variantRefs = new ArrayList<>();

        public FieldNode(String fieldName, String fieldType, String role) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.role = role;
        }
    }

    public static class TotalNode {
        public final String expression;

        public TotalNode(String expression) {
            this.expression = expression;
        }
    }

    public static class CalcNode {
        public final String name;
        public final String expression;
        public final List<VariantRef> variantRefs = new ArrayList<>();

        public CalcNode(String name, String expression) {
            this.name = name;
            this.expression = expression;
        }
    }

    public static class VariantRef {
        public final String variantName;
        /** "selection", "order", "filter" */
        public final String kind;

        public VariantRef(String variantName, String kind) {
            this.variantName = variantName;
            this.kind = kind;
        }
    }

    // ============================================================
    // Build entry
    // ============================================================

    /**
     * Build trace for the given field name (exact dataPath or title substring).
     *
     * @param root   root XML node of the SKD Template.xml
     * @param query  field name (exact) or title substring to search
     * @return list of TraceNodes (one per matching dataset/field combination)
     */
    public List<TraceNode> build(XmlNode root, String query) {
        List<TraceNode> result = new ArrayList<>();
        if (query == null || query.isEmpty()) return result;

        // Collect all datasets (flat + union children)
        List<XmlNode[]> datasets = collectDatasets(root);

        // Collect all totalFields indexed by dataPath
        Map<String, List<String>> totalsMap = buildTotalsMap(root);

        // Collect all calculatedFields (schema-level)
        List<XmlNode> calcFields = root.children("calculatedField");

        // Collect all variants
        List<XmlNode> variants = root.children("settingsVariant");

        for (XmlNode[] dsEntry : datasets) {
            XmlNode ds = dsEntry[0];
            String dsName = safeText(ds.childText("name"));
            String dsType = getDatasetType(ds);

            for (XmlNode fieldNode : ds.children("field")) {
                String dataPath = safeText(fieldNode.childText("dataPath"));
                String title = getMlText(fieldNode.child("title"));

                if (!matchesQuery(dataPath, title, query)) continue;

                String fieldType = getFieldType(fieldNode);
                String role = getFieldRole(fieldNode);

                FieldNode fn = new FieldNode(dataPath, fieldType, role);

                // Totals for this field
                List<String> totalExprs = totalsMap.getOrDefault(dataPath, Collections.emptyList());
                for (String expr : totalExprs) {
                    fn.totals.add(new TotalNode(expr));
                }

                // CalcFields that reference this field
                for (XmlNode cf : calcFields) {
                    String cfName = safeText(cf.childText("name"));
                    String cfExpr = safeText(cf.childText("expression"));
                    if (expressionContains(cfExpr, dataPath)) {
                        CalcNode cn = new CalcNode(cfName, cfExpr);
                        // Variant refs for this calculated field
                        for (XmlNode v : variants) {
                            String vName = safeText(v.childText("name"));
                            XmlNode settings = v.child("settings");
                            if (settings == null) continue;
                            if (selectionContains(settings.children("selection"), cfName)) {
                                cn.variantRefs.add(new VariantRef(vName, "selection"));
                            }
                            if (selectionContains(settings.children("order"), cfName)) {
                                cn.variantRefs.add(new VariantRef(vName, "order"));
                            }
                        }
                        fn.calcFields.add(cn);
                    }
                }

                // Direct variant refs for this field
                for (XmlNode v : variants) {
                    String vName = safeText(v.childText("name"));
                    XmlNode settings = v.child("settings");
                    if (settings == null) continue;
                    if (selectionContains(settings.children("selection"), dataPath)) {
                        fn.variantRefs.add(new VariantRef(vName, "selection"));
                    }
                    if (selectionContains(settings.children("order"), dataPath)) {
                        fn.variantRefs.add(new VariantRef(vName, "order"));
                    }
                }

                TraceNode tn = new TraceNode(dsName, dsType);
                tn.fields.add(fn);
                result.add(tn);
            }
        }

        // Also check schema-level calculatedFields (not in any dataset)
        for (XmlNode cf : calcFields) {
            String cfName = safeText(cf.childText("name"));
            String cfTitle = getMlText(cf.child("title"));
            if (!matchesQuery(cfName, cfTitle, query)) continue;

            // Create a virtual "schema-level" trace node
            TraceNode tn = new TraceNode("(schema-level)", "CalculatedField");
            String cfExpr = safeText(cf.childText("expression"));
            String cfType = getCalculatedFieldType(cf);
            String cfRole = safeText(cf.childText("role"));

            FieldNode fn = new FieldNode(cfName, cfType, cfRole);

            // Totals
            List<String> totalExprs = totalsMap.getOrDefault(cfName, Collections.emptyList());
            for (String expr : totalExprs) {
                fn.totals.add(new TotalNode(expr));
            }

            // Variant refs
            for (XmlNode v : variants) {
                String vName = safeText(v.childText("name"));
                XmlNode settings = v.child("settings");
                if (settings == null) continue;
                if (selectionContains(settings.children("selection"), cfName)) {
                    fn.variantRefs.add(new VariantRef(vName, "selection"));
                }
                if (selectionContains(settings.children("order"), cfName)) {
                    fn.variantRefs.add(new VariantRef(vName, "order"));
                }
            }

            // Track expression source operands
            CalcNode cn = new CalcNode(cfName, cfExpr);
            fn.calcFields.add(cn);

            tn.fields.add(fn);
            result.add(tn);
        }

        return result;
    }

    // ============================================================
    // Helpers
    // ============================================================

    /** Collect all dataset nodes as [node] pairs (union members included). */
    private List<XmlNode[]> collectDatasets(XmlNode root) {
        List<XmlNode[]> result = new ArrayList<>();
        for (XmlNode ds : root.children("dataSet")) {
            result.add(new XmlNode[]{ds});
            if ("Union".equals(getDatasetType(ds))) {
                for (XmlNode sub : ds.children("item")) {
                    result.add(new XmlNode[]{sub});
                }
            }
        }
        return result;
    }

    /** Build map: dataPath → list of expressions from totalFields. */
    private Map<String, List<String>> buildTotalsMap(XmlNode root) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (XmlNode tf : root.children("totalField")) {
            String dp = safeText(tf.childText("dataPath"));
            String expr = safeText(tf.childText("expression"));
            map.computeIfAbsent(dp, k -> new ArrayList<>()).add(expr);
        }
        return map;
    }

    private boolean matchesQuery(String dataPath, String title, String query) {
        if (query.equals(dataPath)) return true;
        if (title != null && title.toLowerCase().contains(query.toLowerCase())) return true;
        if (dataPath != null && dataPath.toLowerCase().contains(query.toLowerCase())) return true;
        return false;
    }

    /** Check if expression contains field reference (case-insensitive word match). */
    private boolean expressionContains(String expression, String fieldName) {
        if (expression == null || expression.isEmpty() || fieldName == null || fieldName.isEmpty()) return false;
        // Simple check: field name appears in expression
        return expression.contains(fieldName);
    }

    /** Check if a list of selection/order nodes contain a field reference. */
    private boolean selectionContains(List<XmlNode> nodes, String fieldName) {
        for (XmlNode n : nodes) {
            String text = safeText(n.getText());
            if (text.contains(fieldName)) return true;
            // Also check child item nodes
            for (XmlNode item : n.children("item")) {
                String field = safeText(item.childText("field"));
                if (fieldName.equals(field)) return true;
            }
        }
        return false;
    }

    private String getDatasetType(XmlNode ds) {
        String xsiType = ds.attr("xsi:type");
        if (xsiType == null) xsiType = ds.attr("type");
        if (xsiType == null) xsiType = "";
        if (xsiType.contains("DataSetQuery")) return "Query";
        if (xsiType.contains("DataSetObject")) return "Object";
        if (xsiType.contains("DataSetUnion")) return "Union";
        return "Unknown";
    }

    private String getFieldType(XmlNode field) {
        XmlNode vt = field.child("valueType");
        if (vt == null) return "";
        List<String> types = new ArrayList<>();
        for (XmlNode t : vt.children("Type")) {
            String raw = safeText(t.getText()).trim();
            types.add(simplifyType(raw));
        }
        return String.join("|", types);
    }

    private String getCalculatedFieldType(XmlNode cf) {
        XmlNode vt = cf.child("valueType");
        if (vt == null) return "";
        List<String> types = new ArrayList<>();
        for (XmlNode t : vt.children("Type")) {
            types.add(simplifyType(safeText(t.getText()).trim()));
        }
        return String.join("|", types);
    }

    private String simplifyType(String raw) {
        switch (raw) {
            case "xs:string": return "String";
            case "xs:decimal": return "Number";
            case "xs:boolean": return "Boolean";
            case "xs:dateTime": return "DateTime";
            default: return raw.replaceFirst("^[a-zA-Z0-9]+:", "");
        }
    }

    private String getFieldRole(XmlNode field) {
        XmlNode role = field.child("role");
        if (role == null) return "";
        List<String> roles = new ArrayList<>();
        for (XmlNode child : role.getChildren()) {
            if ("true".equals(safeText(child.getText()).trim())) {
                roles.add("@" + child.getName().toLowerCase());
            }
        }
        return String.join(",", roles);
    }

    private static String getMlText(XmlNode node) {
        if (node == null) return "";
        XmlNode item = node.child("item");
        if (item != null) {
            XmlNode content = item.child("content");
            if (content != null && content.getText() != null && !content.getText().isEmpty()) {
                return content.getText();
            }
        }
        String text = node.getText();
        return text != null ? text.trim() : "";
    }

    private static String safeText(String s) {
        return s != null ? s : "";
    }
}

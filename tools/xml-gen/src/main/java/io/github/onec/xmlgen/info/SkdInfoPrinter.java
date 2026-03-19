package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализ структуры СКД (DataCompositionSchema / Template.xml).
 * Режимы: overview (default), query, fields, params.
 */
public class SkdInfoPrinter {

    private static final Pattern BATCH_SEPARATOR = Pattern.compile(";\\s*\\r?\\n\\s*/{16,}\\s*\\r?\\n");

    /**
     * @param document распарсенный Template.xml
     * @param mode     режим: overview, query, fields, params
     * @param name     имя набора/поля (опционально)
     * @param limit    макс строк (0 = без ограничения)
     * @param offset   смещение
     * @param out      поток вывода
     */
    public void print(XmlDocument document, String mode, String name,
                      int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();
        List<String> lines = new ArrayList<>();

        switch (mode) {
            case "overview":
                showOverview(root, document.getFile(), lines);
                break;
            case "query":
                showQuery(root, name, lines);
                break;
            case "fields":
                showFields(root, name, lines);
                break;
            case "params":
                showParams(root, lines);
                break;
            default:
                throw new IllegalArgumentException("Unknown skd info mode: '" + mode
                        + "'. Supported modes: overview, query, fields, params");
        }

        // Pagination
        int totalLines = lines.size();
        if (offset > 0) {
            if (offset >= totalLines) {
                out.println("[INFO] Offset " + offset + " exceeds total lines (" + totalLines + "). Nothing to show.");
                return;
            }
            lines = lines.subList(offset, totalLines);
        }

        int effectiveLimit = limit > 0 ? limit : lines.size();
        if (lines.size() > effectiveLimit) {
            for (int i = 0; i < effectiveLimit; i++) {
                out.println(lines.get(i));
            }
            out.println();
            out.println("[TRUNCATED] Shown " + effectiveLimit + " of " + totalLines
                    + " lines. Use --offset " + (offset + effectiveLimit) + " to continue.");
        } else {
            for (String line : lines) {
                out.println(line);
            }
        }
    }

    // ==================== Overview ====================

    private void showOverview(XmlNode root, Path file, List<String> lines) {
        String templateName = resolveTemplateName(file);
        lines.add("=== DCS: " + templateName + " ===");
        lines.add("");

        // Sources
        List<String> sources = new ArrayList<>();
        for (XmlNode ds : root.children("dataSource")) {
            String dsName = ds.childText("name");
            String dsType = ds.childText("dataSourceType");
            sources.add((dsName != null ? dsName : "") + " (" + (dsType != null ? dsType : "") + ")");
        }
        lines.add("Sources: " + String.join(", ", sources));
        lines.add("");

        // Datasets
        lines.add("Datasets:");
        for (XmlNode ds : root.children("dataSet")) {
            String dsType = getDatasetType(ds);
            String dsName = safeText(ds.childText("name"));
            int fieldCount = ds.children("field").size();

            if ("Query".equals(dsType)) {
                int queryLines = countQueryLines(ds);
                lines.add("  [Query]  " + dsName + "   " + fieldCount + " fields, query " + queryLines + " lines");
            } else if ("Object".equals(dsType)) {
                String objName = ds.childText("objectName");
                String objStr = objName != null ? "  objectName=" + objName : "";
                lines.add("  [Object] " + dsName + objStr + "  " + fieldCount + " fields");
            } else if ("Union".equals(dsType)) {
                lines.add("  [Union]  " + dsName + "  " + fieldCount + " fields");
                for (XmlNode sub : ds.children("item")) {
                    String subType = getDatasetType(sub);
                    String subName = safeText(sub.childText("name"));
                    int subFields = sub.children("field").size();
                    if ("Query".equals(subType)) {
                        int subQueryLines = countQueryLines(sub);
                        lines.add("    \u251C\u2500 [Query] " + subName + "   " + subFields + " fields, query " + subQueryLines + " lines");
                    } else {
                        lines.add("    \u251C\u2500 [" + subType + "] " + subName + "  " + subFields + " fields");
                    }
                }
            }
        }

        // Links
        List<XmlNode> links = root.children("dataSetLink");
        if (!links.isEmpty()) {
            Map<String, Integer> linkPairs = new LinkedHashMap<>();
            for (XmlNode lnk : links) {
                String src = safeText(lnk.childText("sourceDataSet"));
                String dst = safeText(lnk.childText("destinationDataSet"));
                String key = src + " -> " + dst;
                linkPairs.merge(key, 1, Integer::sum);
            }
            List<String> linkStrs = new ArrayList<>();
            for (Map.Entry<String, Integer> e : linkPairs.entrySet()) {
                linkStrs.add(e.getValue() > 1 ? e.getKey() + " (" + e.getValue() + " fields)" : e.getKey());
            }
            lines.add("Links: " + String.join(", ", linkStrs));
        }

        // Calculated fields
        List<XmlNode> calcFields = root.children("calculatedField");
        if (!calcFields.isEmpty()) {
            lines.add("Calculated: " + calcFields.size());
        }

        // Resources (totalField)
        List<XmlNode> totalFields = root.children("totalField");
        if (!totalFields.isEmpty()) {
            Set<String> uniquePaths = new LinkedHashSet<>();
            boolean hasGrouped = false;
            for (XmlNode tf : totalFields) {
                String dp = safeText(tf.childText("dataPath"));
                uniquePaths.add(dp);
                if (tf.child("group") != null) hasGrouped = true;
            }
            String groupNote = hasGrouped ? ", with group formulas" : "";
            if (uniquePaths.size() == totalFields.size()) {
                lines.add("Resources: " + totalFields.size() + groupNote);
            } else {
                lines.add("Resources: " + totalFields.size() + " (" + uniquePaths.size() + " fields" + groupNote + ")");
            }
        }

        // Parameters
        List<XmlNode> params = root.children("parameter");
        if (!params.isEmpty()) {
            List<String> visibleNames = new ArrayList<>();
            int hiddenCount = 0;
            for (XmlNode p : params) {
                String pName = safeText(p.childText("name"));
                boolean isHidden = "true".equals(safeText(p.childText("useRestriction")).trim());
                if (isHidden) {
                    hiddenCount++;
                } else {
                    visibleNames.add(pName);
                }
            }
            StringBuilder paramLine = new StringBuilder("Params: " + params.size());
            if (hiddenCount > 0 && !visibleNames.isEmpty()) {
                paramLine.append(" (").append(visibleNames.size()).append(" visible, ").append(hiddenCount).append(" hidden)");
            } else if (hiddenCount == params.size()) {
                paramLine.append(" (all hidden)");
            }
            if (!visibleNames.isEmpty() && visibleNames.size() <= 8) {
                paramLine.append(": ").append(String.join(", ", visibleNames));
            }
            lines.add(paramLine.toString());
        } else {
            lines.add("Params: (none)");
        }

        lines.add("");

        // Variants
        List<XmlNode> variants = root.children("settingsVariant");
        if (!variants.isEmpty()) {
            lines.add("Variants:");
            int idx = 0;
            for (XmlNode v : variants) {
                idx++;
                String vName = safeText(v.childText("name"));
                XmlNode vPres = v.child("presentation");
                String presStr = "";
                if (vPres != null) {
                    String pt = getMlText(vPres);
                    if (!pt.isEmpty()) presStr = "  \"" + pt + "\"";
                }

                XmlNode settings = v.child("settings");
                List<String> structItems = new ArrayList<>();
                if (settings != null) {
                    for (XmlNode si : settings.children("item")) {
                        String siType = getStructureItemType(si);
                        List<String> gf = getGroupFields(si);
                        String gStr = !gf.isEmpty() ? "(" + String.join(",", gf) + ")" : "(detail)";
                        structItems.add(siType + gStr);
                    }
                }
                String structStr = !structItems.isEmpty() ? "  " + String.join(", ", structItems) : "";

                int filterCount = 0;
                if (settings != null) {
                    XmlNode filter = settings.child("filter");
                    if (filter != null) filterCount = filter.children("item").size();
                }
                String filterStr = filterCount > 0 ? "  " + filterCount + " filters" : "";

                lines.add("  [" + idx + "] " + vName + presStr + structStr + filterStr);
            }
        }

        // Hints
        lines.add("");
        lines.add("Next:");
        lines.add("  --mode query             query text");
        lines.add("  --mode fields            field tables by dataset");
        lines.add("  --mode params            parameter details");
    }

    // ==================== Query ====================

    private void showQuery(XmlNode root, String name, List<String> lines) {
        XmlNode targetDs = findQueryDataset(root, name);
        if (targetDs == null) {
            lines.add("No Query dataset found" + (name != null ? " with name '" + name + "'" : ""));
            return;
        }

        XmlNode queryNode = targetDs.child("query");
        if (queryNode == null) {
            lines.add("Dataset has no query element");
            return;
        }

        String rawQuery = safeText(queryNode.getText());
        String dsName = safeText(targetDs.childText("name"));
        int totalQueryLines = rawQuery.split("\n").length;

        lines.add("=== Query: " + dsName + " (" + totalQueryLines + " lines) ===");
        lines.add("");
        for (String ql : rawQuery.split("\n")) {
            lines.add(ql.replaceFirst("\\s+$", ""));
        }
    }

    // ==================== Fields ====================

    private void showFields(XmlNode root, String name, List<String> lines) {
        if (name != null && !name.isEmpty()) {
            showFieldDetail(root, name, lines);
            return;
        }

        lines.add("=== Fields map ===");
        for (XmlNode ds : root.children("dataSet")) {
            showDatasetFieldMap(ds, "", lines);
            if ("Union".equals(getDatasetType(ds))) {
                for (XmlNode sub : ds.children("item")) {
                    showDatasetFieldMap(sub, "  ", lines);
                }
            }
        }
        lines.add("");
        lines.add("Use --name <field> for details.");
    }

    private void showDatasetFieldMap(XmlNode ds, String indent, List<String> lines) {
        String dsType = getDatasetType(ds);
        String dsName = safeText(ds.childText("name"));
        List<XmlNode> fields = ds.children("field");
        List<String> fieldNames = new ArrayList<>();
        for (XmlNode f : fields) {
            String dp = safeText(f.childText("dataPath"));
            fieldNames.add(dp);
        }
        String nameList = String.join(", ", fieldNames);
        if (nameList.length() > 100) nameList = nameList.substring(0, 97) + "...";
        lines.add(indent + dsName + " [" + dsType + "] (" + fields.size() + "): " + nameList);
    }

    private void showFieldDetail(XmlNode root, String fieldName, List<String> lines) {
        // Search all datasets for the field
        for (XmlNode ds : root.children("dataSet")) {
            XmlNode found = findFieldByPath(ds, fieldName);
            if (found != null) {
                showSingleFieldDetail(found, fieldName, safeText(ds.childText("name")), getDatasetType(ds), lines);
                return;
            }
            if ("Union".equals(getDatasetType(ds))) {
                for (XmlNode sub : ds.children("item")) {
                    found = findFieldByPath(sub, fieldName);
                    if (found != null) {
                        showSingleFieldDetail(found, fieldName, safeText(sub.childText("name")), getDatasetType(sub), lines);
                        return;
                    }
                }
            }
        }
        lines.add("Field '" + fieldName + "' not found in any dataset");
    }

    private XmlNode findFieldByPath(XmlNode ds, String fieldName) {
        for (XmlNode f : ds.children("field")) {
            if (fieldName.equals(safeText(f.childText("dataPath")))) return f;
        }
        return null;
    }

    private void showSingleFieldDetail(XmlNode field, String fieldName, String dsName, String dsType, List<String> lines) {
        XmlNode titleNode = field.child("title");
        String title = titleNode != null ? getMlText(titleNode) : "";
        String titleStr = !title.isEmpty() ? " \"" + title + "\"" : "";

        lines.add("=== Field: " + fieldName + titleStr + " ===");
        lines.add("");
        lines.add("Dataset: " + dsName + " [" + dsType + "]");

        XmlNode vt = field.child("valueType");
        if (vt != null) {
            String typeStr = getCompactType(vt);
            if (!typeStr.isEmpty()) lines.add("Type: " + typeStr);
        }

        XmlNode role = field.child("role");
        if (role != null) {
            List<String> roleParts = new ArrayList<>();
            for (XmlNode child : role.getChildren()) {
                if ("true".equals(safeText(child.getText()).trim())) {
                    roleParts.add(child.getName());
                }
            }
            if (!roleParts.isEmpty()) lines.add("Role: " + String.join(", ", roleParts));
        }

        XmlNode restrict = field.child("useRestriction");
        if (restrict != null) {
            List<String> rParts = new ArrayList<>();
            for (XmlNode child : restrict.getChildren()) {
                if ("true".equals(safeText(child.getText()).trim())) {
                    rParts.add(child.getName());
                }
            }
            if (!rParts.isEmpty()) lines.add("Restrict: " + String.join(", ", rParts));
        }
    }

    // ==================== Params ====================

    private void showParams(XmlNode root, List<String> lines) {
        List<XmlNode> params = root.children("parameter");
        if (params.isEmpty()) {
            lines.add("(no parameters)");
            return;
        }

        lines.add("=== Parameters (" + params.size() + ") ===");
        lines.add("");

        for (XmlNode p : params) {
            String pName = safeText(p.childText("name"));
            XmlNode vt = p.child("valueType");
            String typeStr = vt != null ? getCompactType(vt) : "";

            boolean isHidden = "true".equals(safeText(p.childText("useRestriction")).trim());
            String hiddenStr = isHidden ? " [hidden]" : "";

            XmlNode titleNode = p.child("title");
            String title = titleNode != null ? getMlText(titleNode) : "";
            String titleStr = !title.isEmpty() ? "  \"" + title + "\"" : "";

            // Default value
            XmlNode defValue = p.child("value");
            String defStr = "";
            if (defValue != null) {
                String dv = safeText(defValue.getText());
                if (!dv.isEmpty()) defStr = "  default=" + dv;
            }

            // Expression
            XmlNode expr = p.child("expression");
            String exprStr = "";
            if (expr != null) {
                String ev = safeText(expr.getText());
                if (!ev.isEmpty()) exprStr = "  expr=" + ev;
            }

            String typePart = !typeStr.isEmpty() ? ": " + typeStr : "";
            lines.add("  " + pName + typePart + titleStr + defStr + exprStr + hiddenStr);
        }
    }

    // ==================== Helpers ====================

    private String getDatasetType(XmlNode ds) {
        // Check xsi:type attribute
        String xsiType = ds.attr("xsi:type");
        if (xsiType == null) xsiType = ds.attr("type");
        if (xsiType == null) xsiType = "";
        if (xsiType.contains("DataSetQuery")) return "Query";
        if (xsiType.contains("DataSetObject")) return "Object";
        if (xsiType.contains("DataSetUnion")) return "Union";
        return "Unknown";
    }

    private String getStructureItemType(XmlNode item) {
        String xsiType = item.attr("xsi:type");
        if (xsiType == null) xsiType = "";
        if (xsiType.contains("StructureItemGroup")) return "Group";
        if (xsiType.contains("StructureItemTable")) return "Table";
        if (xsiType.contains("StructureItemChart")) return "Chart";
        return "Unknown";
    }

    private List<String> getGroupFields(XmlNode item) {
        List<String> fields = new ArrayList<>();
        XmlNode groupItems = item.child("groupItems");
        if (groupItems == null) return fields;
        for (XmlNode gi : groupItems.children("item")) {
            String f = safeText(gi.childText("field"));
            XmlNode gt = gi.child("groupType");
            String gtText = gt != null ? safeText(gt.getText()) : "";
            if (!gtText.isEmpty() && !"Items".equals(gtText)) {
                f += "(" + gtText + ")";
            }
            fields.add(f);
        }
        return fields;
    }

    private int countQueryLines(XmlNode ds) {
        XmlNode queryNode = ds.child("query");
        if (queryNode == null) return 0;
        String text = safeText(queryNode.getText());
        return text.split("\n").length;
    }

    private XmlNode findQueryDataset(XmlNode root, String name) {
        for (XmlNode ds : root.children("dataSet")) {
            if (name != null && !name.isEmpty()) {
                // Search by name: nested first, then top-level
                for (XmlNode sub : ds.children("item")) {
                    if (name.equals(safeText(sub.childText("name")))) return sub;
                }
                if (name.equals(safeText(ds.childText("name")))) return ds;
            } else {
                // First Query dataset
                if ("Query".equals(getDatasetType(ds))) return ds;
                if ("Union".equals(getDatasetType(ds))) {
                    for (XmlNode sub : ds.children("item")) {
                        if ("Query".equals(getDatasetType(sub))) return sub;
                    }
                }
            }
        }
        return null;
    }

    private String getCompactType(XmlNode typeNode) {
        if (typeNode == null) return "";
        List<String> types = new ArrayList<>();
        for (XmlNode t : typeNode.children("Type")) {
            String raw = safeText(t.getText()).trim();
            switch (raw) {
                case "xs:string": types.add("String"); break;
                case "xs:decimal": types.add("Number"); break;
                case "xs:boolean": types.add("Boolean"); break;
                case "xs:dateTime": types.add("DateTime"); break;
                case "v8:StandardPeriod": types.add("StandardPeriod"); break;
                case "v8:StandardBeginningDate": types.add("StandardBeginningDate"); break;
                case "v8:AccountType": types.add("AccountType"); break;
                case "v8:Null": types.add("Null"); break;
                default: types.add(raw.replaceFirst("^[a-zA-Z0-9]+:", "")); break;
            }
        }
        return String.join(" | ", types);
    }

    private String resolveTemplateName(Path file) {
        if (file == null) return "unknown";
        String resolved = file.toAbsolutePath().toString().replace("\\", "/");
        String[] parts = resolved.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if ("Ext".equals(parts[i]) && i >= 1) {
                return parts[i - 1];
            }
        }
        return file.getFileName().toString();
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

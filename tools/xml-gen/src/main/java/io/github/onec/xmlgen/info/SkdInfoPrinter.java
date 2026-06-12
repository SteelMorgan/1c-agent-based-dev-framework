package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.info.skd.SkdTraceBuilder;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализ структуры СКД (DataCompositionSchema / Template.xml).
 * Режимы: overview, query, fields, links, calculated, resources, params,
 *         variant, templates, trace, full.
 */
public class SkdInfoPrinter {

    private static final Pattern BATCH_SEPARATOR = Pattern.compile(";\\s*\\r?\\n\\s*/{16,}\\s*\\r?\\n");

    /**
     * @param document   распарсенный Template.xml
     * @param mode       режим: overview, query, fields, links, calculated, resources,
     *                   params, variant, templates, trace, full
     * @param nameFilter имя набора/поля/варианта (опционально)
     * @param limit      макс строк (0 = без ограничения)
     * @param offset     смещение
     * @param out        поток вывода
     */
    public void print(XmlDocument document, String mode, String nameFilter,
                      int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();
        List<String> lines = new ArrayList<>();
        printByMode(root, document.getFile(), mode, nameFilter, lines);

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

    /**
     * Dispatch to mode-specific method.
     */
    public void printByMode(XmlNode root, Path file, String mode, String nameFilter, List<String> lines) {
        switch (mode) {
            case "overview":
                printOverview(root, file, lines);
                break;
            case "query":
                printQuery(root, nameFilter, lines);
                break;
            case "fields":
                printFields(root, nameFilter, lines);
                break;
            case "links":
                printLinks(root, lines);
                break;
            case "calculated":
                printCalculated(root, nameFilter, lines);
                break;
            case "resources":
                printResources(root, nameFilter, lines);
                break;
            case "params":
                printParams(root, lines);
                break;
            case "variant":
                printVariant(root, nameFilter, lines);
                break;
            case "templates":
                printTemplates(root, nameFilter, lines);
                break;
            case "trace":
                printTrace(root, nameFilter, lines);
                break;
            case "full":
                printFull(root, file, lines);
                break;
            default:
                throw new IllegalArgumentException("Unknown skd info mode: '" + mode
                        + "'. Supported modes: overview, query, fields, links, calculated, resources, "
                        + "params, variant, templates, trace, full");
        }
    }

    // ==================== Overview ====================

    private void printOverview(XmlNode root, Path file, List<String> lines) {
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
        if (!sources.isEmpty()) {
            lines.add("Sources: " + String.join(", ", sources));
            lines.add("");
        }

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
                        lines.add("    ├─ [Query] " + subName + "   " + subFields + " fields, query " + subQueryLines + " lines");
                    } else {
                        lines.add("    ├─ [" + subType + "] " + subName + "  " + subFields + " fields");
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

        // Templates
        List<XmlNode> templates = root.children("template");
        List<XmlNode> groupTemplates = root.children("groupTemplate");
        if (!templates.isEmpty() || !groupTemplates.isEmpty()) {
            lines.add("Templates: " + templates.size() + " templates, " + groupTemplates.size() + " group bindings");
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
        lines.add("  --mode query              query text");
        lines.add("  --mode fields             field tables by dataset");
        lines.add("  --mode calculated         calculated field expressions");
        lines.add("  --mode resources          resource aggregation");
        lines.add("  --mode params             parameter details");
        lines.add("  --mode variant            variant structure");
        lines.add("  --mode links              dataset links");
        lines.add("  --mode templates          template bindings");
        lines.add("  --mode trace --name <f>   field origin chain");
    }

    // ==================== Query ====================

    //++agent TASK-176 [08.06.2026 12:50:00]
    // S-06 (XG-48, upstream 9877fe40): raw-режим печатает текст запроса БАЙТ-В-БАЙТ — без
    // заголовков "=== Query"/"--- Batch" и без дробления по батчам, которые добавляет
    // printQuery. Нужно для lossless round-trip `skd info --raw | skd edit set-query`:
    // строка-разделитель батчей //// сохраняется как есть, запрос не нормализуется.
    public void printRawQuery(XmlNode root, String name, PrintStream out) {
        XmlNode targetDs = findQueryDataset(root, name);
        if (targetDs == null) {
            out.println("No Query dataset found" + (name != null ? " with name '" + name + "'" : ""));
            return;
        }
        XmlNode queryNode = targetDs.child("query");
        if (queryNode == null) {
            out.println("Dataset has no query element");
            return;
        }
        out.println(safeText(queryNode.getText()));
    }
    //++agent TASK-176

    private void printQuery(XmlNode root, String name, List<String> lines) {
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

        // Detect batches
        String[] batches = BATCH_SEPARATOR.split(rawQuery);
        int totalQueryLines = rawQuery.split("\n").length;

        if (batches.length > 1) {
            lines.add("=== Query: " + dsName + " (" + totalQueryLines + " lines, " + batches.length + " batches) ===");
            // Table of contents
            int lineNum = 1;
            for (int i = 0; i < batches.length; i++) {
                String[] batchLines = batches[i].split("\n");
                int batchEnd = lineNum + batchLines.length - 1;
                String tempTableName = extractTempTableName(batches[i]);
                String ttStr = tempTableName != null ? " → " + tempTableName : "";
                lines.add("  Batch " + (i + 1) + ": lines " + lineNum + "-" + batchEnd + ttStr);
                lineNum = batchEnd + 2;
            }
            lines.add("");
            for (int i = 0; i < batches.length; i++) {
                lines.add("--- Batch " + (i + 1) + " ---");
                for (String ql : batches[i].split("\n")) {
                    lines.add(ql.replaceFirst("\\s+$", ""));
                }
                lines.add("");
            }
        } else {
            lines.add("=== Query: " + dsName + " (" + totalQueryLines + " lines) ===");
            lines.add("");
            for (String ql : rawQuery.split("\n")) {
                lines.add(ql.replaceFirst("\\s+$", ""));
            }
        }
    }

    private String extractTempTableName(String batch) {
        // Look for "ПОМЕСТИТЬ <Name>" pattern
        Pattern p = Pattern.compile("(?i)\\bПОМЕСТИТЬ\\s+(\\w+)");
        Matcher m = p.matcher(batch);
        if (m.find()) return m.group(1);
        return null;
    }

    // ==================== Fields ====================

    private void printFields(XmlNode root, String name, List<String> lines) {
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

        XmlNode presExpr = field.child("presentationExpression");
        if (presExpr != null && presExpr.getText() != null && !presExpr.getText().trim().isEmpty()) {
            lines.add("Presentation: " + presExpr.getText().trim());
        }
    }

    // ==================== Links ====================

    private void printLinks(XmlNode root, List<String> lines) {
        List<XmlNode> links = root.children("dataSetLink");
        if (links.isEmpty()) {
            lines.add("=== Links (0) ===");
            lines.add("(no dataset links defined)");
            return;
        }

        lines.add("=== Links (" + links.size() + ") ===");
        lines.add("");

        // Group by source->dest pair
        Map<String, List<XmlNode>> grouped = new LinkedHashMap<>();
        for (XmlNode lnk : links) {
            String src = safeText(lnk.childText("sourceDataSet"));
            String dst = safeText(lnk.childText("destinationDataSet"));
            String key = src + " -> " + dst;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(lnk);
        }

        for (Map.Entry<String, List<XmlNode>> entry : grouped.entrySet()) {
            lines.add(entry.getKey() + " :");
            for (XmlNode lnk : entry.getValue()) {
                String srcExpr = safeText(lnk.childText("sourceExpression"));
                //**agent TASK-174 [07.06.2026 11:38:00]
                //String dstExpr = safeText(lnk.childText("destExpression"));
                // Платформенный элемент — destinationExpression; на реальных схемах прежнее
                // чтение destExpression всегда давало пустоту. Fallback оставлен для файлов,
                // сгенерированных старым writer'ом до фикса.
                String dstExpr = safeText(lnk.childText("destinationExpression"));
                if (dstExpr.isEmpty()) dstExpr = safeText(lnk.childText("destExpression"));
                //**agent TASK-174
                if (!srcExpr.isEmpty() || !dstExpr.isEmpty()) {
                    // Align columns
                    lines.add("  " + padRight(srcExpr, 20) + " -> " + dstExpr);
                }
            }
            lines.add("");
        }
    }

    // ==================== Calculated ====================

    private void printCalculated(XmlNode root, String name, List<String> lines) {
        List<XmlNode> calcFields = root.children("calculatedField");
        if (calcFields.isEmpty()) {
            lines.add("(no calculated fields)");
            return;
        }

        if (name != null && !name.isEmpty()) {
            // Find specific field
            for (XmlNode cf : calcFields) {
                String cfName = safeText(cf.childText("name"));
                if (name.equals(cfName)) {
                    showCalculatedFieldDetail(cf, lines);
                    return;
                }
            }
            lines.add("Calculated field '" + name + "' not found");
            return;
        }

        lines.add("=== Calculated fields (" + calcFields.size() + ") ===");
        for (XmlNode cf : calcFields) {
            String cfName = safeText(cf.childText("name"));
            String title = getMlText(cf.child("title"));
            String titleStr = !title.isEmpty() ? "  \"" + title + "\"" : "";
            lines.add("  " + cfName + titleStr);
        }
        lines.add("");
        lines.add("Use --name <field> for expression details.");
    }

    private void showCalculatedFieldDetail(XmlNode cf, List<String> lines) {
        String cfName = safeText(cf.childText("name"));
        lines.add("=== Calculated: " + cfName + " ===");
        lines.add("");

        String title = getMlText(cf.child("title"));
        if (!title.isEmpty()) lines.add("Title: " + title);

        XmlNode vt = cf.child("valueType");
        if (vt != null) {
            String typeStr = getCompactType(vt);
            if (!typeStr.isEmpty()) lines.add("Type: " + typeStr);
        }

        String expr = safeText(cf.childText("expression"));
        if (!expr.isEmpty()) {
            lines.add("Expression:");
            for (String eLine : expr.split("\n")) {
                lines.add("  " + eLine.replaceFirst("\\s+$", ""));
            }
        }

        XmlNode restrict = cf.child("useRestriction");
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

    // ==================== Resources ====================

    private void printResources(XmlNode root, String name, List<String> lines) {
        List<XmlNode> totalFields = root.children("totalField");
        if (totalFields.isEmpty()) {
            lines.add("(no resources / total fields)");
            return;
        }

        // Build map: dataPath -> list of totalField nodes
        Map<String, List<XmlNode>> byPath = new LinkedHashMap<>();
        for (XmlNode tf : totalFields) {
            String dp = safeText(tf.childText("dataPath"));
            byPath.computeIfAbsent(dp, k -> new ArrayList<>()).add(tf);
        }

        if (name != null && !name.isEmpty()) {
            // Specific resource detail
            List<XmlNode> tfs = byPath.get(name);
            if (tfs == null) {
                lines.add("Resource '" + name + "' not found");
                return;
            }
            lines.add("=== Resource: " + name + " ===");
            lines.add("");
            for (XmlNode tf : tfs) {
                XmlNode groupNode = tf.child("group");
                String groupStr = groupNode != null ? "[" + safeText(groupNode.getText()) + "] " : "";
                String expr = safeText(tf.childText("expression"));
                lines.add("  " + groupStr + expr);
            }
            return;
        }

        lines.add("=== Resources (" + byPath.size() + ") ===");
        for (Map.Entry<String, List<XmlNode>> entry : byPath.entrySet()) {
            String dp = entry.getKey();
            boolean hasGroup = entry.getValue().stream().anyMatch(tf -> tf.child("group") != null);
            lines.add("  " + dp + (hasGroup ? " *" : ""));
        }

        boolean anyHasGroup = totalFields.stream().anyMatch(tf -> tf.child("group") != null);
        if (anyHasGroup) {
            lines.add("");
            lines.add("  * = has group-level formulas");
        }
        lines.add("");
        lines.add("Use --name <field> for aggregation details.");
    }

    // ==================== Params ====================

    private void printParams(XmlNode root, List<String> lines) {
        List<XmlNode> params = root.children("parameter");
        if (params.isEmpty()) {
            lines.add("(no parameters)");
            return;
        }

        lines.add("=== Parameters (" + params.size() + ") ===");
        lines.add("");
        lines.add("  " + padRight("Name", 24) + padRight("Type", 24) + padRight("Default", 12) + "Visible  Expression");
        lines.add("  " + repeat("-", 80));

        for (XmlNode p : params) {
            String pName = safeText(p.childText("name"));
            XmlNode vt = p.child("valueType");
            String typeStr = vt != null ? getCompactType(vt) : "";

            boolean isHidden = "true".equals(safeText(p.childText("useRestriction")).trim());
            String visStr = isHidden ? "hidden" : "yes";

            // Default value
            XmlNode defValue = p.child("value");
            String defStr = "-";
            if (defValue != null) {
                String dv = safeText(defValue.getText());
                if (!dv.isEmpty()) defStr = dv;
            }

            // Expression
            XmlNode expr = p.child("expression");
            String exprStr = "-";
            if (expr != null) {
                String ev = safeText(expr.getText());
                if (!ev.isEmpty()) exprStr = ev;
            }

            lines.add("  " + padRight(pName, 24) + padRight(typeStr.length() > 22 ? typeStr.substring(0, 22) : typeStr, 24)
                    + padRight(defStr.length() > 10 ? defStr.substring(0, 10) : defStr, 12)
                    + padRight(visStr, 9) + exprStr);
        }
    }

    // ==================== Variant ====================

    private void printVariant(XmlNode root, String name, List<String> lines) {
        List<XmlNode> variants = root.children("settingsVariant");
        if (variants.isEmpty()) {
            lines.add("(no variants)");
            return;
        }

        if (name == null || name.isEmpty()) {
            // List all
            lines.add("=== Variants (" + variants.size() + ") ===");
            int idx = 0;
            for (XmlNode v : variants) {
                idx++;
                String vName = safeText(v.childText("name"));
                String pres = getMlText(v.child("presentation"));
                String presStr = !pres.isEmpty() ? "  \"" + pres + "\"" : "";

                XmlNode settings = v.child("settings");
                int filterCount = 0;
                if (settings != null) {
                    XmlNode filter = settings.child("filter");
                    if (filter != null) filterCount = filter.children("item").size();
                }
                String filterStr = filterCount > 0 ? "  " + filterCount + " filters" : "";

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

                lines.add("  [" + idx + "] " + vName + presStr + structStr + filterStr);
            }
            lines.add("");
            lines.add("Use --name <N|name> for variant structure details.");
            return;
        }

        // Find specific variant by name or index
        XmlNode targetVariant = null;
        int targetIdx = -1;
        int idx = 0;
        for (XmlNode v : variants) {
            idx++;
            String vName = safeText(v.childText("name"));
            if (name.equals(vName) || name.equals(String.valueOf(idx))) {
                targetVariant = v;
                targetIdx = idx;
                break;
            }
        }

        if (targetVariant == null) {
            lines.add("Variant '" + name + "' not found");
            return;
        }

        String vName = safeText(targetVariant.childText("name"));
        String pres = getMlText(targetVariant.child("presentation"));
        String presStr = !pres.isEmpty() ? " \"" + pres + "\"" : "";
        lines.add("=== Variant [" + targetIdx + "]: " + vName + presStr + " ===");
        lines.add("");

        XmlNode settings = targetVariant.child("settings");
        if (settings == null) {
            lines.add("(no settings)");
            return;
        }

        // Structure
        List<XmlNode> structItems = settings.children("item");
        if (!structItems.isEmpty()) {
            lines.add("Structure:");
            for (XmlNode si : structItems) {
                String siType = getStructureItemType(si);
                String siName = safeText(si.childText("name"));
                String siNameStr = !siName.isEmpty() ? " \"" + siName + "\"" : "";
                lines.add("  " + siType + siNameStr);

                // Selection
                XmlNode sel = si.child("selection");
                if (sel != null) {
                    List<String> selFields = new ArrayList<>();
                    for (XmlNode item : sel.children("item")) {
                        selFields.add(safeText(item.childText("field")));
                    }
                    if (!selFields.isEmpty()) {
                        lines.add("    Selection: " + String.join(", ", selFields));
                    }
                }

                // GroupBy
                XmlNode groupItems = si.child("groupItems");
                if (groupItems != null) {
                    List<String> groupFields = new ArrayList<>();
                    for (XmlNode gi : groupItems.children("item")) {
                        groupFields.add(safeText(gi.childText("field")));
                    }
                    if (!groupFields.isEmpty()) {
                        lines.add("    GroupBy: " + String.join(", ", groupFields));
                    }
                }
            }
            lines.add("");
        }

        // Filter
        XmlNode filter = settings.child("filter");
        if (filter != null) {
            List<XmlNode> filterItems = filter.children("item");
            if (!filterItems.isEmpty()) {
                lines.add("Filter:");
                for (XmlNode fi : filterItems) {
                    String field = safeText(fi.childText("leftValue"));
                    if (field.isEmpty()) field = safeText(fi.childText("field"));
                    String op = safeText(fi.childText("comparisonType"));
                    String val = safeText(fi.childText("rightValue"));
                    String use = safeText(fi.childText("use"));
                    String useStr = "true".equals(use) ? "[x]" : "[ ]";
                    XmlNode viewMode = fi.child("viewMode");
                    String vmStr = viewMode != null ? " [" + safeText(viewMode.getText()) + "]" : "";
                    String titleNode = getMlText(fi.child("presentation"));
                    String titleStr = !titleNode.isEmpty() ? " \"" + titleNode + "\"" : "";
                    lines.add("  " + useStr + " " + field + " " + op + " " + val + titleStr + vmStr);
                }
                lines.add("");
            }
        }

        // Selection
        List<XmlNode> selNodes = settings.children("selection");
        if (!selNodes.isEmpty()) {
            List<String> selFields = new ArrayList<>();
            for (XmlNode sel : selNodes) {
                for (XmlNode item : sel.children("item")) {
                    selFields.add(safeText(item.childText("field")));
                }
            }
            if (!selFields.isEmpty()) {
                lines.add("Selection: " + String.join(", ", selFields));
            }
        }

        // Order
        List<XmlNode> orderNodes = settings.children("order");
        if (!orderNodes.isEmpty()) {
            List<String> orderFields = new ArrayList<>();
            for (XmlNode ord : orderNodes) {
                for (XmlNode item : ord.children("item")) {
                    String field = safeText(item.childText("field"));
                    String dir = safeText(item.childText("orderType"));
                    orderFields.add(field + (dir.isEmpty() ? "" : " " + dir));
                }
            }
            if (!orderFields.isEmpty()) {
                lines.add("Order: " + String.join(", ", orderFields));
            }
        }

        // DataParameters
        XmlNode dataParams = settings.child("dataParameters");
        if (dataParams != null) {
            lines.add("");
            lines.add("DataParams:");
            for (XmlNode item : dataParams.children("item")) {
                String pName = safeText(item.childText("parameterName"));
                String pVal = safeText(item.childText("value"));
                lines.add("  " + pName + " = " + pVal);
            }
        }

        // OutputParameters
        XmlNode outputParams = settings.child("outputParameters");
        if (outputParams != null && !outputParams.getChildren().isEmpty()) {
            lines.add("");
            lines.add("Output:");
            for (XmlNode op : outputParams.getChildren()) {
                String opVal = safeText(op.getText());
                if (!opVal.isEmpty()) {
                    lines.add("  " + op.getName() + "=" + opVal);
                }
            }
        }
    }

    // ==================== Templates ====================

    private void printTemplates(XmlNode root, String name, List<String> lines) {
        List<XmlNode> templates = root.children("template");
        List<XmlNode> groupTemplates = root.children("groupTemplate");

        if (templates.isEmpty() && groupTemplates.isEmpty()) {
            lines.add("(no templates defined)");
            return;
        }

        if (name == null || name.isEmpty()) {
            lines.add("=== Templates (" + templates.size() + " defined: "
                    + templates.size() + " field, " + groupTemplates.size() + " group) ===");
            lines.add("");

            if (!templates.isEmpty()) {
                lines.add("Field bindings (" + templates.size() + "):");
                List<String> names = new ArrayList<>();
                for (XmlNode t : templates) {
                    names.add(safeText(t.childText("name")));
                }
                String nameList = String.join(", ", names);
                if (nameList.length() > 120) {
                    lines.add("  (all: " + nameList.substring(0, 117) + "...)");
                } else {
                    lines.add("  " + nameList);
                }
                lines.add("");
            }

            if (!groupTemplates.isEmpty()) {
                lines.add("Group bindings (" + groupTemplates.size() + "):");
                // Group by groupField/groupName
                Map<String, List<XmlNode>> byGroup = new LinkedHashMap<>();
                for (XmlNode gt : groupTemplates) {
                    String gf = safeText(gt.childText("groupField"));
                    if (gf.isEmpty()) gf = safeText(gt.childText("groupName"));
                    byGroup.computeIfAbsent(gf, k -> new ArrayList<>()).add(gt);
                }
                for (Map.Entry<String, List<XmlNode>> entry : byGroup.entrySet()) {
                    lines.add("  " + entry.getKey());
                    for (XmlNode gt : entry.getValue()) {
                        String tplType = safeText(gt.childText("templateType"));
                        String tplName = safeText(gt.childText("template"));
                        lines.add("    " + tplType + " -> " + tplName);
                    }
                }
            }
            return;
        }

        // Specific template/groupfield
        // Check group templates first
        boolean found = false;
        lines.add("=== Templates: " + name + " ===");
        lines.add("");

        List<XmlNode> matchedGroupTemplates = new ArrayList<>();
        for (XmlNode gt : groupTemplates) {
            String gf = safeText(gt.childText("groupField"));
            if (gf.isEmpty()) gf = safeText(gt.childText("groupName"));
            if (name.equals(gf)) {
                matchedGroupTemplates.add(gt);
                found = true;
            }
        }

        for (XmlNode gt : matchedGroupTemplates) {
            String tplType = safeText(gt.childText("templateType"));
            String tplName = safeText(gt.childText("template"));
            lines.add(tplType + " -> " + tplName);
        }

        // Check field templates
        for (XmlNode t : templates) {
            String tName = safeText(t.childText("name"));
            if (name.equals(tName)) {
                found = true;
                String tplName = safeText(t.childText("template"));
                lines.add("Field -> " + tplName);
            }
        }

        if (!found) {
            lines.add("No templates found for '" + name + "'");
        }
    }

    // ==================== Trace ====================

    private void printTrace(XmlNode root, String name, List<String> lines) {
        if (name == null || name.isEmpty()) {
            lines.add("Usage: --mode trace --name <fieldName>");
            lines.add("Find field by dataPath or title substring, show origin chain.");
            return;
        }

        SkdTraceBuilder builder = new SkdTraceBuilder();
        List<SkdTraceBuilder.TraceNode> traceNodes = builder.build(root, name);

        if (traceNodes.isEmpty()) {
            lines.add("=== Trace: " + name + " ===");
            lines.add("");
            lines.add("Field '" + name + "' not found in datasets or calculated fields.");
            return;
        }

        for (SkdTraceBuilder.TraceNode tn : traceNodes) {
            for (SkdTraceBuilder.FieldNode fn : tn.fields) {
                lines.add("=== Trace: " + fn.fieldName + " ===");
                lines.add("");
                lines.add("Dataset: " + tn.dataSetName + " (" + tn.dataSetType + ")");
                if (!fn.fieldType.isEmpty()) lines.add("Type: " + fn.fieldType);
                if (!fn.role.isEmpty()) lines.add("Role: " + fn.role);
                lines.add("");

                String fieldPrefix = "DataSet \"" + tn.dataSetName + "\" (" + tn.dataSetType + ")";
                lines.add(fieldPrefix);
                renderFieldNode(fn, "  ", lines);
                lines.add("");
            }
        }
    }

    private void renderFieldNode(SkdTraceBuilder.FieldNode fn, String indent, List<String> lines) {
        // Determine children count for tree chars
        int totalChildren = fn.totals.size() + fn.calcFields.size() + fn.variantRefs.size();
        int childIdx = 0;

        String roleStr = !fn.role.isEmpty() ? ", " + fn.role : "";
        String typeStr = !fn.fieldType.isEmpty() ? " (" + fn.fieldType + roleStr + ")" : (!fn.role.isEmpty() ? " (" + fn.role + ")" : "");

        lines.add(indent + "└── Field \"" + fn.fieldName + "\"" + typeStr);

        String childIndent = indent + "    ";

        for (SkdTraceBuilder.TotalNode tn : fn.totals) {
            childIdx++;
            String treeChar = childIdx < totalChildren ? "├── " : "└── ";
            lines.add(childIndent + treeChar + "Total: " + tn.expression);
        }

        for (SkdTraceBuilder.CalcNode cn : fn.calcFields) {
            childIdx++;
            String treeChar = childIdx < totalChildren ? "├── " : "└── ";
            // Truncate long expressions
            String exprDisplay = cn.expression;
            if (exprDisplay.length() > 80) exprDisplay = exprDisplay.substring(0, 77) + "...";
            lines.add(childIndent + treeChar + "CalculatedField \"" + cn.name + "\" = " + exprDisplay);

            if (!cn.variantRefs.isEmpty()) {
                String calcChildIndent = childIndent + (childIdx < totalChildren ? "│   " : "    ");
                for (SkdTraceBuilder.VariantRef vr : cn.variantRefs) {
                    lines.add(calcChildIndent + "└── Variant \"" + vr.variantName + "\" → " + vr.kind + "[\"" + cn.name + "\"]");
                }
            }
        }

        for (SkdTraceBuilder.VariantRef vr : fn.variantRefs) {
            childIdx++;
            String treeChar = childIdx < totalChildren ? "├── " : "└── ";
            lines.add(childIndent + treeChar + "Variant \"" + vr.variantName + "\" → " + vr.kind + "[\"" + fn.fieldName + "\"]");
        }
    }

    // ==================== Full ====================

    private void printFull(XmlNode root, Path file, List<String> lines) {
        printOverview(root, file, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printQuery(root, null, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printFields(root, null, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printLinks(root, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printCalculated(root, null, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printResources(root, null, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printParams(root, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printVariant(root, null, lines);
        lines.add("");
        lines.add("────────────────────────────────────────");
        lines.add("");
        printTemplates(root, null, lines);
    }

    // ==================== Helpers ====================

    private String getDatasetType(XmlNode ds) {
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
                for (XmlNode sub : ds.children("item")) {
                    if (name.equals(safeText(sub.childText("name")))) return sub;
                }
                if (name.equals(safeText(ds.childText("name")))) return ds;
            } else {
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

    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s + " ";
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    private static String repeat(String s, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(s);
        return sb.toString();
    }
}

package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Анализ прав роли 1С (Rights.xml).
 * Выводит: свойства, разрешённые/запрещённые права по типам, RLS, шаблоны.
 */
public class RoleInfoPrinter {

    /**
     * @param document    распарсенный Rights.xml
     * @param showDenied  показывать запрещённые права
     * @param limit       макс строк (0 = без ограничения)
     * @param offset      смещение
     * @param out         поток вывода
     */
    public void print(XmlDocument document, boolean showDenied, int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();
        List<String> lines = new ArrayList<>();

        // --- Role name from path ---
        String roleName = resolveRoleName(document.getFile());

        // --- Global flags ---
        String setForNew = safe(root.attr("setForNewObjects"));
        String setForAttrs = safe(root.attr("setForAttributesByDefault"));
        String independentChild = safe(root.attr("independentRightsOfChildObjects"));

        // --- Header ---
        lines.add("=== Role: " + roleName + " ===");
        lines.add("");
        lines.add("Properties: setForNewObjects=" + setForNew
                + ", setForAttributesByDefault=" + setForAttrs
                + ", independentRightsOfChildObjects=" + independentChild);
        lines.add("");

        // --- Collect rights ---
        Map<String, Map<String, List<String>>> allowed = new LinkedHashMap<>();
        Map<String, Map<String, List<String>>> denied = new LinkedHashMap<>();
        List<String> rlsObjects = new ArrayList<>();
        int totalAllowed = 0;
        int totalDenied = 0;

        for (XmlNode obj : root.children("object")) {
            String objName = obj.childText("name");
            if (objName == null || objName.isEmpty()) continue;

            int dotIdx = objName.indexOf('.');
            if (dotIdx < 0) continue;
            String typePrefix = objName.substring(0, dotIdx);
            String shortName = objName.substring(dotIdx + 1);

            for (XmlNode right : obj.children("right")) {
                String rName = right.childText("name");
                String rValue = right.childText("value");
                if (rName == null || rValue == null) continue;

                boolean hasRls = right.child("restrictionByCondition") != null;

                if ("true".equals(rValue)) {
                    totalAllowed++;
                    String suffix = rName;
                    if (hasRls) {
                        suffix += " [RLS]";
                        rlsObjects.add(typePrefix + "." + shortName + " (" + rName + ")");
                    }
                    allowed.computeIfAbsent(typePrefix, k -> new LinkedHashMap<>())
                            .computeIfAbsent(shortName, k -> new ArrayList<>())
                            .add(suffix);
                } else {
                    totalDenied++;
                    denied.computeIfAbsent(typePrefix, k -> new LinkedHashMap<>())
                            .computeIfAbsent(shortName, k -> new ArrayList<>())
                            .add(rName);
                }
            }
        }

        // --- Allowed ---
        if (!allowed.isEmpty()) {
            lines.add("Allowed rights:");
            lines.add("");
            for (Map.Entry<String, Map<String, List<String>>> entry : allowed.entrySet()) {
                lines.add("  " + entry.getKey() + " (" + entry.getValue().size() + "):");
                for (Map.Entry<String, List<String>> obj : entry.getValue().entrySet()) {
                    lines.add("    " + obj.getKey() + ": " + String.join(", ", obj.getValue()));
                }
                lines.add("");
            }
        } else {
            lines.add("(no allowed rights)");
            lines.add("");
        }

        // --- Denied ---
        if (showDenied && !denied.isEmpty()) {
            lines.add("Denied rights:");
            lines.add("");
            for (Map.Entry<String, Map<String, List<String>>> entry : denied.entrySet()) {
                lines.add("  " + entry.getKey() + " (" + entry.getValue().size() + "):");
                for (Map.Entry<String, List<String>> obj : entry.getValue().entrySet()) {
                    lines.add("    " + obj.getKey() + ": " + obj.getValue().stream()
                            .map(r -> "-" + r).reduce((a, b) -> a + ", " + b).orElse(""));
                }
                lines.add("");
            }
        } else if (totalDenied > 0) {
            lines.add("Denied: " + totalDenied + " rights (use --show-denied to list)");
            lines.add("");
        }

        // --- RLS ---
        if (!rlsObjects.isEmpty()) {
            lines.add("RLS: " + rlsObjects.size() + " restrictions");
        }

        // --- Templates ---
        List<String> templates = new ArrayList<>();
        for (XmlNode tpl : root.children("restrictionTemplate")) {
            String tName = tpl.childText("name");
            if (tName != null) {
                int paren = tName.indexOf('(');
                templates.add(paren > 0 ? tName.substring(0, paren) : tName);
            }
        }
        if (!templates.isEmpty()) {
            lines.add("Templates: " + String.join(", ", templates));
        }

        lines.add("");
        lines.add("---");
        lines.add("Total: " + totalAllowed + " allowed, " + totalDenied + " denied");

        // --- Pagination ---
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

    private String resolveRoleName(Path file) {
        if (file == null) return "unknown";
        // Rights.xml is in .../RoleName/Ext/Rights.xml
        Path ext = file.getParent();
        if (ext != null) {
            Path roleDir = ext.getParent();
            if (roleDir != null) return roleDir.getFileName().toString();
        }
        return file.getFileName().toString();
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}

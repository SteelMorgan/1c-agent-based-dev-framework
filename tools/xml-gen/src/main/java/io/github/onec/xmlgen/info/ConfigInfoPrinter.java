package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.*;

/**
 * Анализ структуры конфигурации 1С (Configuration.xml).
 * Режимы: brief (одна строка), overview (по умолчанию), full (все свойства + объекты).
 */
public class ConfigInfoPrinter {

    /** Канонический порядок типов в ChildObjects (44 типа). */
    static final List<String> CHILD_TYPE_ORDER = List.of(
            "Language", "Subsystem", "StyleItem", "Style", "CommonPicture",
            "SessionParameter", "Role", "CommonTemplate", "FilterCriterion",
            "CommonModule", "CommonAttribute", "ExchangePlan", "XDTOPackage",
            "WebService", "HTTPService", "WSReference", "EventSubscription",
            "ScheduledJob", "SettingsStorage", "FunctionalOption",
            "FunctionalOptionsParameter", "DefinedType", "CommonCommand",
            "CommandGroup", "Constant", "CommonForm", "Catalog", "Document",
            "DocumentNumerator", "Sequence", "DocumentJournal", "Enum",
            "Report", "DataProcessor", "InformationRegister",
            "AccumulationRegister", "ChartOfCharacteristicTypes",
            "ChartOfAccounts", "AccountingRegister", "ChartOfCalculationTypes",
            "CalculationRegister", "BusinessProcess", "Task",
            "IntegrationService"
    );

    /**
     * @param document распарсенный Configuration.xml
     * @param mode     режим: brief, overview, full
     * @param limit    макс строк
     * @param offset   смещение
     * @param out      поток вывода
     */
    public void print(XmlDocument document, String mode, int limit, int offset, PrintStream out) {
        XmlNode root = document.getRoot();

        // Navigate to Configuration element
        XmlNode config = root;
        if (!"Configuration".equals(root.getName())) {
            config = root.child("Configuration");
        }
        if (config == null) {
            out.println("ERROR: <Configuration> element not found");
            return;
        }

        switch (mode) {
            case "brief":
                printBrief(config, out);
                return;
            case "full":
                break;
            case "overview":
                break;
            default:
                throw new IllegalArgumentException("Unknown config info mode: '" + mode
                        + "'. Supported modes: brief, overview, full");
        }

        List<String> lines = new ArrayList<>();
        if ("full".equals(mode)) {
            buildFull(config, lines);
        } else {
            buildOverview(config, lines);
        }

        paginate(lines, limit, offset, out);
    }

    // ==================== Brief ====================

    private void printBrief(XmlNode config, PrintStream out) {
        XmlNode props = config.child("Properties");
        if (props == null) {
            out.println("ERROR: <Properties> not found");
            return;
        }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        String version = safe(props.childText("Version"));
        String compat = safe(props.childText("CompatibilityMode"));

        // Count objects
        int totalObjects = countChildObjects(config);

        String synStr = !synonym.isEmpty() ? " — \"" + synonym + "\"" : "";
        String verStr = !version.isEmpty() ? " v" + version : "";

        out.println("Конфигурация: " + name + synStr + verStr + " | "
                + totalObjects + " объектов | " + compat);
    }

    // ==================== Overview ====================

    private void buildOverview(XmlNode config, List<String> lines) {
        XmlNode props = config.child("Properties");
        if (props == null) {
            lines.add("ERROR: <Properties> not found");
            return;
        }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        String synStr = !synonym.isEmpty() ? " — \"" + synonym + "\"" : "";

        lines.add("Конфигурация: " + name + synStr);

        // Key properties
        addPropLine(lines, "Version", props.childText("Version"));
        addPropLine(lines, "Vendor", props.childText("Vendor"));
        addPropLine(lines, "Compatibility", props.childText("CompatibilityMode"));
        addPropLine(lines, "DefaultLanguage", props.childText("DefaultLanguage"));
        addPropLine(lines, "DefaultRunMode", props.childText("DefaultRunMode"));
        addPropLine(lines, "ScriptVariant", props.childText("ScriptVariant"));

        // Object counts
        Map<String, List<String>> childMap = getChildObjectsMap(config);
        int total = childMap.values().stream().mapToInt(List::size).sum();

        lines.add("");
        lines.add("Объекты (" + total + " шт.):");
        for (String type : CHILD_TYPE_ORDER) {
            List<String> items = childMap.get(type);
            if (items != null && !items.isEmpty()) {
                lines.add("  " + pad(type, 30) + items.size());
            }
        }
        // Any unknown types
        for (Map.Entry<String, List<String>> e : childMap.entrySet()) {
            if (!CHILD_TYPE_ORDER.contains(e.getKey())) {
                lines.add("  " + pad(e.getKey(), 30) + e.getValue().size());
            }
        }
    }

    // ==================== Full ====================

    private void buildFull(XmlNode config, List<String> lines) {
        XmlNode props = config.child("Properties");
        if (props == null) {
            lines.add("ERROR: <Properties> not found");
            return;
        }

        String name = safe(props.childText("Name"));
        String synonym = getMlText(props.child("Synonym"));
        String synStr = !synonym.isEmpty() ? " — \"" + synonym + "\"" : "";

        lines.add("=== Конфигурация: " + name + synStr + " ===");
        lines.add("");

        // All scalar/enum properties
        lines.add("--- Свойства ---");
        String[] scalarProps = {"Name", "NamePrefix", "Vendor", "Version", "UpdateCatalogAddress", "Comment"};
        for (String p : scalarProps) {
            String val = props.childText(p);
            if (val != null && !val.isEmpty()) {
                lines.add("  " + pad(p, 40) + val);
            }
        }

        // LocalString properties
        String[] lsProps = {"Synonym", "BriefInformation", "DetailedInformation", "Copyright",
                "VendorInformationAddress", "ConfigurationInformationAddress"};
        for (String p : lsProps) {
            XmlNode node = props.child(p);
            String text = getMlText(node);
            if (!text.isEmpty()) {
                lines.add("  " + pad(p, 40) + text);
            }
        }

        lines.add("");
        lines.add("--- Режимы ---");
        String[] enumProps = {"ConfigurationExtensionCompatibilityMode", "DefaultRunMode",
                "ScriptVariant", "CompatibilityMode", "DataLockControlMode",
                "ObjectAutonumerationMode", "ModalityUseMode",
                "SynchronousPlatformExtensionAndAddInCallUseMode",
                "InterfaceCompatibilityMode", "DatabaseTablespacesUseMode",
                "MainClientApplicationWindowMode"};
        for (String p : enumProps) {
            String val = props.childText(p);
            if (val != null && !val.isEmpty()) {
                lines.add("  " + pad(p, 50) + val);
            }
        }

        // Ref properties
        lines.add("");
        lines.add("--- Ссылки ---");
        String[] refProps = {"DefaultLanguage", "CommonSettingsStorage",
                "ReportsUserSettingsStorage", "ReportsVariantsStorage",
                "FormDataSettingsStorage", "DynamicListsUserSettingsStorage",
                "DefaultReportForm", "DefaultReportVariantForm",
                "DefaultReportSettingsForm", "DefaultSearchForm",
                "DefaultConstantsForm"};
        for (String p : refProps) {
            String val = props.childText(p);
            if (val != null && !val.isEmpty()) {
                lines.add("  " + pad(p, 50) + val);
            }
        }

        // Boolean properties
        String[] boolProps = {"IncludeHelpInContents", "UseManagedFormInOrdinaryApplication",
                "UseOrdinaryFormInManagedApplication"};
        boolean hasBool = false;
        for (String p : boolProps) {
            String val = props.childText(p);
            if (val != null && !val.isEmpty()) {
                if (!hasBool) { lines.add(""); lines.add("--- Флаги ---"); hasBool = true; }
                lines.add("  " + pad(p, 50) + val);
            }
        }

        // UsePurposes
        XmlNode usePurposes = props.child("UsePurposes");
        if (usePurposes != null) {
            List<String> purposes = new ArrayList<>();
            for (XmlNode v : usePurposes.children("Value")) {
                if (v.getText() != null) purposes.add(v.getText());
            }
            if (!purposes.isEmpty()) {
                lines.add("");
                lines.add("--- Назначения ---");
                for (String p : purposes) lines.add("  " + p);
            }
        }

        // DefaultRoles
        XmlNode defaultRoles = props.child("DefaultRoles");
        if (defaultRoles != null) {
            List<String> roles = new ArrayList<>();
            for (XmlNode item : defaultRoles.children("Item")) {
                if (item.getText() != null) roles.add(item.getText());
            }
            if (!roles.isEmpty()) {
                lines.add("");
                lines.add("--- Роли по умолчанию ---");
                for (String r : roles) lines.add("  " + r);
            }
        }

        // ChildObjects — full list
        Map<String, List<String>> childMap = getChildObjectsMap(config);
        int total = childMap.values().stream().mapToInt(List::size).sum();

        lines.add("");
        lines.add("--- Объекты (" + total + " шт.) ---");
        for (String type : CHILD_TYPE_ORDER) {
            List<String> items = childMap.get(type);
            if (items != null && !items.isEmpty()) {
                lines.add("  " + type + " (" + items.size() + "):");
                for (String item : items) {
                    lines.add("    " + item);
                }
            }
        }
    }

    // ==================== Helpers ====================

    private Map<String, List<String>> getChildObjectsMap(XmlNode config) {
        Map<String, List<String>> map = new LinkedHashMap<>();
        XmlNode co = config.child("ChildObjects");
        if (co == null) return map;

        for (XmlNode child : co.getChildren()) {
            String type = child.getName();
            String name = child.getText();
            if (name == null) name = "";
            map.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
        }
        return map;
    }

    private int countChildObjects(XmlNode config) {
        XmlNode co = config.child("ChildObjects");
        if (co == null) return 0;
        return co.getChildren().size();
    }

    private void addPropLine(List<String> lines, String label, String value) {
        if (value != null && !value.isEmpty()) {
            lines.add("  " + pad(label + ":", 20) + value);
        }
    }

    static String getMlText(XmlNode node) {
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

    private static String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }

    private static String safe(String s) { return s != null ? s : ""; }

    private void paginate(List<String> lines, int limit, int offset, PrintStream out) {
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
            for (int i = 0; i < effectiveLimit; i++) out.println(lines.get(i));
            out.println();
            out.println("[TRUNCATED] Shown " + effectiveLimit + " of " + totalLines
                    + " lines. Use --offset " + (offset + effectiveLimit) + " to continue.");
        } else {
            for (String line : lines) out.println(line);
        }
    }
}

package io.github.onec.xmlgen.info;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Анализ расширения конфигурации (CFE).
 *
 * Mode A — Overview: [BORROWED]/[OWN] с декораторами и ChildObjects.
 * Mode B — Transfer check: проверка переноса #Вставка блоков.
 */
public class ExtensionDiffPrinter {

    private static final Map<String, String> TYPE_TO_DIR = Map.ofEntries(
            Map.entry("Language", "Languages"), Map.entry("Subsystem", "Subsystems"),
            Map.entry("StyleItem", "StyleItems"), Map.entry("Style", "Styles"),
            Map.entry("CommonPicture", "CommonPictures"), Map.entry("SessionParameter", "SessionParameters"),
            Map.entry("Role", "Roles"), Map.entry("CommonTemplate", "CommonTemplates"),
            Map.entry("FilterCriterion", "FilterCriteria"), Map.entry("CommonModule", "CommonModules"),
            Map.entry("CommonAttribute", "CommonAttributes"), Map.entry("ExchangePlan", "ExchangePlans"),
            Map.entry("XDTOPackage", "XDTOPackages"), Map.entry("WebService", "WebServices"),
            Map.entry("HTTPService", "HTTPServices"), Map.entry("WSReference", "WSReferences"),
            Map.entry("EventSubscription", "EventSubscriptions"), Map.entry("ScheduledJob", "ScheduledJobs"),
            Map.entry("SettingsStorage", "SettingsStorages"), Map.entry("FunctionalOption", "FunctionalOptions"),
            Map.entry("FunctionalOptionsParameter", "FunctionalOptionsParameters"),
            Map.entry("DefinedType", "DefinedTypes"), Map.entry("CommonCommand", "CommonCommands"),
            Map.entry("CommandGroup", "CommandGroups"), Map.entry("Constant", "Constants"),
            Map.entry("CommonForm", "CommonForms"), Map.entry("Catalog", "Catalogs"),
            Map.entry("Document", "Documents"), Map.entry("DocumentNumerator", "DocumentNumerators"),
            Map.entry("Sequence", "Sequences"), Map.entry("DocumentJournal", "DocumentJournals"),
            Map.entry("Enum", "Enums"), Map.entry("Report", "Reports"),
            Map.entry("DataProcessor", "DataProcessors"), Map.entry("InformationRegister", "InformationRegisters"),
            Map.entry("AccumulationRegister", "AccumulationRegisters"),
            Map.entry("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes"),
            Map.entry("ChartOfAccounts", "ChartsOfAccounts"), Map.entry("AccountingRegister", "AccountingRegisters"),
            Map.entry("ChartOfCalculationTypes", "ChartsOfCalculationTypes"),
            Map.entry("CalculationRegister", "CalculationRegisters"),
            Map.entry("BusinessProcess", "BusinessProcesses"), Map.entry("Task", "Tasks"),
            Map.entry("IntegrationService", "IntegrationServices")
    );

    /** BSL interceptor decorator pattern: &Перед/После/ИзменениеИКонтроль/Вместо("MethodName") */
    private static final Pattern INTERCEPTOR_PATTERN = Pattern.compile(
            "^&(Перед|После|ИзменениеИКонтроль|Вместо)\\(\"([^\"]+)\"\\)");

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final PrintStream out;

    public ExtensionDiffPrinter() { this(System.out); }
    public ExtensionDiffPrinter(PrintStream out) { this.out = out; }

    /**
     * Run extension diff analysis.
     *
     * @param extDir    extension root directory
     * @param configDir base config root directory (needed for mode B)
     * @param mode      "A" = overview, "B" = transfer check
     */
    public void diff(Path extDir, Path configDir, String mode) throws IOException {
        // Resolve file paths to directories
        if (Files.isRegularFile(extDir)) extDir = extDir.getParent();
        if (configDir != null && Files.isRegularFile(configDir)) configDir = configDir.getParent();

        Path extCfgFile = extDir.resolve("Configuration.xml");
        if (!Files.isRegularFile(extCfgFile)) {
            throw new IllegalArgumentException("Extension Configuration.xml not found: " + extCfgFile);
        }

        XmlDocument extDoc;
        try {
            extDoc = new XmlStructureReader().parse(extCfgFile);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new IOException("Failed to parse extension Configuration.xml: " + e.getMessage(), e);
        }
        XmlNode root = extDoc.getRoot();
        XmlNode config = "MetaDataObject".equals(root.getName()) ? root.child("Configuration") : root;
        if (config == null) {
            throw new IllegalArgumentException("No <Configuration> in extension");
        }

        // Print header
        XmlNode props = config.child("Properties");
        String extName = props != null ? props.childText("Name") : "?";
        String prefix = props != null ? props.childText("NamePrefix") : "";
        String purpose = props != null ? props.childText("ConfigurationExtensionPurpose") : "?";

        out.println("=== cfe-diff Mode " + mode + ": " + extName + " (" + purpose + ") ===");
        out.println("    NamePrefix: " + (prefix != null ? prefix : ""));
        out.println();

        // Collect objects from ChildObjects
        XmlNode childObjects = config.child("ChildObjects");
        if (childObjects == null) {
            out.println("[WARN] No ChildObjects in extension");
            return;
        }

        List<ObjEntry> objects = new ArrayList<>();
        for (XmlNode child : childObjects.getChildren()) {
            String typeName = child.getName();
            if ("Language".equals(typeName)) continue;
            String objName = child.getText() != null ? child.getText() : "";
            objects.add(new ObjEntry(typeName, objName));
        }

        if (objects.isEmpty()) {
            out.println("No objects (besides Language) in extension.");
            return;
        }

        if ("A".equals(mode)) {
            modeA(objects, extDir);
        } else if ("B".equals(mode)) {
            if (configDir == null || !Files.isDirectory(configDir)) {
                throw new IllegalArgumentException("Config path required for Mode B");
            }
            modeB(objects, extDir, configDir);
        }
    }

    // ─── Mode A: Overview ─────────────────────────────────────────────

    private void modeA(List<ObjEntry> objects, Path extDir) {
        int borrowed = 0, own = 0;

        for (ObjEntry obj : objects) {
            String dirName = TYPE_TO_DIR.get(obj.type);
            if (dirName == null) {
                out.println("  [?] " + obj.type + "." + obj.name + " — unknown type");
                continue;
            }

            ObjInfo info = getObjectInfo(extDir, dirName, obj.type, obj.name);
            if (!info.exists) {
                out.println("  [?] " + obj.type + "." + obj.name + " — file not found");
                continue;
            }

            if (info.borrowed) {
                borrowed++;
                out.println("  [BORROWED] " + obj.type + "." + obj.name);

                // Find BSL files and interceptors
                List<BslFile> bslFiles = findBslFiles(extDir, dirName, obj.name);
                for (BslFile bsl : bslFiles) {
                    String relPath = extDir.relativize(bsl.path).toString();
                    List<Interceptor> interceptors = getInterceptors(bsl.path);
                    if (!interceptors.isEmpty()) {
                        for (Interceptor ic : interceptors) {
                            out.println("             &" + ic.type + "(\"" + ic.method + "\") — line " + ic.line + " in " + relPath);
                        }
                    } else {
                        out.println("             " + relPath + " (no interceptors)");
                    }
                }

                // ChildObjects analysis
                printChildObjectsAnalysis(extDir, dirName, obj.name, info);
            } else {
                own++;
                out.println("  [OWN]      " + obj.type + "." + obj.name);
                printOwnChildObjectsInfo(info);
            }
        }

        out.println();
        out.println("=== Summary: " + borrowed + " borrowed, " + own + " own objects ===");
    }

    // ─── Mode B: Transfer check ──────────────────────────────────────

    private void modeB(List<ObjEntry> objects, Path extDir, Path configDir) {
        int transferred = 0, notTransferred = 0, needsReview = 0;

        for (ObjEntry obj : objects) {
            String dirName = TYPE_TO_DIR.get(obj.type);
            if (dirName == null) continue;

            ObjInfo info = getObjectInfo(extDir, dirName, obj.type, obj.name);
            if (!info.exists || !info.borrowed) continue;

            List<BslFile> bslFiles = findBslFiles(extDir, dirName, obj.name);
            for (BslFile bsl : bslFiles) {
                List<Interceptor> interceptors = getInterceptors(bsl.path);
                List<Interceptor> macInterceptors = interceptors.stream()
                        .filter(ic -> "ИзменениеИКонтроль".equals(ic.type))
                        .toList();

                if (macInterceptors.isEmpty()) continue;

                for (Interceptor ic : macInterceptors) {
                    List<InsertionBlock> blocks = getInsertionBlocks(bsl.path);

                    if (blocks.isEmpty()) {
                        out.println("  [NEEDS_REVIEW] " + obj.type + "." + obj.name
                                + " — &ИзменениеИКонтроль(\"" + ic.method + "\") — no #Вставка blocks");
                        needsReview++;
                        continue;
                    }

                    // Find corresponding module in config
                    Path configBsl = configDir.resolve(extDir.relativize(bsl.path));
                    if (!Files.isRegularFile(configBsl)) {
                        out.println("  [NEEDS_REVIEW] " + obj.type + "." + obj.name
                                + " — &ИзменениеИКонтроль(\"" + ic.method + "\") — config module not found");
                        needsReview++;
                        continue;
                    }

                    try {
                        String configContent = readString(configBsl);
                        String configNorm = configContent.replaceAll("\\s+", " ");

                        boolean allTransferred = true;
                        for (InsertionBlock block : blocks) {
                            if (block.code.isEmpty()) continue;
                            String codeNorm = block.code.replaceAll("\\s+", " ");
                            if (!configNorm.contains(codeNorm)) {
                                allTransferred = false;
                                break;
                            }
                        }

                        if (allTransferred) {
                            out.println("  [TRANSFERRED]     " + obj.type + "." + obj.name
                                    + " — &ИзменениеИКонтроль(\"" + ic.method + "\") — " + blocks.size() + " block(s)");
                            transferred++;
                        } else {
                            out.println("  [NOT_TRANSFERRED] " + obj.type + "." + obj.name
                                    + " — &ИзменениеИКонтроль(\"" + ic.method + "\") — some blocks not found in config");
                            notTransferred++;
                        }
                    } catch (IOException e) {
                        out.println("  [NEEDS_REVIEW] " + obj.type + "." + obj.name
                                + " — error reading config module: " + e.getMessage());
                        needsReview++;
                    }
                }
            }
        }

        out.println();
        out.println("=== Transfer check: " + transferred + " transferred, " + notTransferred
                + " not transferred, " + needsReview + " needs review ===");
    }

    // ─── Object info ──────────────────────────────────────────────────

    private ObjInfo getObjectInfo(Path extDir, String dirName, String typeName, String objName) {
        Path objFile = extDir.resolve(dirName).resolve(objName + ".xml");
        if (!Files.isRegularFile(objFile)) {
            return new ObjInfo(false, false, null);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(objFile);
            XmlNode root = doc.getRoot();

            // Navigate to type element
            XmlNode typeEl = null;
            if ("MetaDataObject".equals(root.getName())) {
                for (XmlNode c : root.getChildren()) {
                    if (!"MetaDataObject".equals(c.getName())) {
                        typeEl = c;
                        break;
                    }
                }
            }
            if (typeEl == null) return new ObjInfo(true, false, null);

            XmlNode objProps = typeEl.child("Properties");
            if (objProps == null) return new ObjInfo(true, false, typeEl);

            String ob = objProps.childText("ObjectBelonging");
            return new ObjInfo(true, "Adopted".equals(ob), typeEl);
        } catch (Exception e) {
            return new ObjInfo(true, false, null);
        }
    }

    // ─── BSL analysis ─────────────────────────────────────────────────

    private List<BslFile> findBslFiles(Path extDir, String dirName, String objName) {
        List<BslFile> result = new ArrayList<>();

        // Object-level BSL (Ext/*.bsl)
        Path extPath = extDir.resolve(dirName).resolve(objName).resolve("Ext");
        if (Files.isDirectory(extPath)) {
            try (var stream = Files.list(extPath)) {
                stream.filter(p -> p.toString().toLowerCase().endsWith(".bsl"))
                        .forEach(p -> result.add(new BslFile(p)));
            } catch (IOException ignored) {}
        }

        // Form BSL (Forms/*/Ext/Form/Module.bsl)
        Path formsDir = extDir.resolve(dirName).resolve(objName).resolve("Forms");
        if (Files.isDirectory(formsDir)) {
            try (var stream = Files.walk(formsDir)) {
                stream.filter(p -> "Module.bsl".equals(p.getFileName().toString()))
                        .forEach(p -> result.add(new BslFile(p)));
            } catch (IOException ignored) {}
        }

        return result;
    }

    private List<Interceptor> getInterceptors(Path bslPath) {
        List<Interceptor> result = new ArrayList<>();
        try {
            List<String> lines = readBslLines(bslPath);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                Matcher m = INTERCEPTOR_PATTERN.matcher(line);
                if (m.find()) {
                    result.add(new Interceptor(m.group(1), m.group(2), i + 1));
                }
            }
        } catch (IOException ignored) {}
        return result;
    }

    private List<InsertionBlock> getInsertionBlocks(Path bslPath) {
        List<InsertionBlock> result = new ArrayList<>();
        try {
            List<String> lines = readBslLines(bslPath);
            boolean inBlock = false;
            List<String> blockLines = new ArrayList<>();
            int startLine = 0;

            for (int i = 0; i < lines.size(); i++) {
                String stripped = lines.get(i).trim();
                if ("#Вставка".equals(stripped)) {
                    inBlock = true;
                    blockLines.clear();
                    startLine = i + 1;
                } else if ("#КонецВставки".equals(stripped) && inBlock) {
                    inBlock = false;
                    result.add(new InsertionBlock(startLine, i + 1, String.join("\n", blockLines).trim()));
                } else if (inBlock) {
                    blockLines.add(lines.get(i).stripTrailing());
                }
            }
        } catch (IOException ignored) {}
        return result;
    }

    // ─── ChildObjects analysis ────────────────────────────────────────

    private void printChildObjectsAnalysis(Path extDir, String dirName, String objName, ObjInfo info) {
        if (info.typeElement == null) return;
        XmlNode childObj = info.typeElement.child("ChildObjects");
        if (childObj == null) return;

        int ownAttrs = 0, ownForms = 0, ownTs = 0, borrowedItems = 0;
        List<String> formNames = new ArrayList<>();

        for (XmlNode c : childObj.getChildren()) {
            String ln = c.getName();
            String text = c.getText() != null ? c.getText() : "";
            // Check if child elements have ObjectBelonging=Adopted
            // For simple text entries (in extension ChildObjects), they're typically references
            if ("Attribute".equals(ln)) ownAttrs++;
            else if ("TabularSection".equals(ln)) ownTs++;
            else if ("Form".equals(ln)) {
                formNames.add(text);
                ownForms++;
            }
        }

        List<String> parts = new ArrayList<>();
        if (ownAttrs > 0) parts.add(ownAttrs + " own attrs");
        if (ownTs > 0) parts.add(ownTs + " own TS");
        if (ownForms > 0) parts.add(ownForms + " own forms");
        if (borrowedItems > 0) parts.add(borrowedItems + " borrowed items");
        if (!parts.isEmpty()) {
            out.println("             ChildObjects: " + String.join(", ", parts));
        }

        // Analyze forms
        for (String fn : formNames) {
            Path formXmlPath = extDir.resolve(dirName).resolve(objName)
                    .resolve("Forms").resolve(fn).resolve("Ext").resolve("Form.xml");
            if (!Files.isRegularFile(formXmlPath)) {
                out.println("             Form." + fn + " (?)");
                continue;
            }

            try {
                String formContent = readString(formXmlPath);
                boolean isBorrowed = formContent.contains("<BaseForm");
                String formTag = isBorrowed ? "borrowed" : "own";
                out.println("             Form." + fn + " (" + formTag + ")");
            } catch (IOException e) {
                out.println("             Form." + fn + " (?)");
            }
        }
    }

    private void printOwnChildObjectsInfo(ObjInfo info) {
        if (info.typeElement == null) return;
        XmlNode childObj = info.typeElement.child("ChildObjects");
        if (childObj == null) return;

        int attrs = 0, forms = 0, ts = 0;
        for (XmlNode c : childObj.getChildren()) {
            String ln = c.getName();
            if ("Attribute".equals(ln)) attrs++;
            else if ("TabularSection".equals(ln)) ts++;
            else if ("Form".equals(ln)) forms++;
        }

        List<String> parts = new ArrayList<>();
        if (attrs > 0) parts.add(attrs + " attrs");
        if (ts > 0) parts.add(ts + " TS");
        if (forms > 0) parts.add(forms + " forms");
        if (!parts.isEmpty()) {
            out.println("             " + String.join(", ", parts));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────

    /** Read BSL file lines, stripping BOM from the first line if present. */
    private List<String> readBslLines(Path bslPath) throws IOException {
        List<String> lines = Files.readAllLines(bslPath, StandardCharsets.UTF_8);
        if (!lines.isEmpty()) {
            String first = lines.get(0);
            if (first.length() > 0 && first.charAt(0) == '\uFEFF') {
                lines.set(0, first.substring(1));
            }
        }
        return lines;
    }

    private String readString(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // ─── Records ──────────────────────────────────────────────────────

    record ObjEntry(String type, String name) {}
    record ObjInfo(boolean exists, boolean borrowed, XmlNode typeElement) {}
    record BslFile(Path path) {}
    record Interceptor(String type, String method, int line) {}
    record InsertionBlock(int startLine, int endLine, String code) {}
}

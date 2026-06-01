package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.FormEditDsl;
import io.github.onec.xmlgen.form.edit.FormEditApplier;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.editor.*;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.info.ConfigInfoPrinter;
import io.github.onec.xmlgen.info.FormInfoPrinter;
import io.github.onec.xmlgen.info.MxlDecompiler;
import io.github.onec.xmlgen.info.MxlInfoPrinter;
import io.github.onec.xmlgen.info.RoleInfoPrinter;
import io.github.onec.xmlgen.info.SkdInfoPrinter;
import io.github.onec.xmlgen.validator.*;
import io.github.onec.xmlgen.validator.report.JsonReporter;
import io.github.onec.xmlgen.validator.report.TextReporter;
import io.github.onec.xmlgen.writer.ConfigWriter;
import io.github.onec.xmlgen.writer.EpfWriter;
import io.github.onec.xmlgen.writer.FormWriter;
import io.github.onec.xmlgen.writer.MxlWriter;
import io.github.onec.xmlgen.writer.RoleWriter;
import io.github.onec.xmlgen.writer.SkdWriter;
import io.github.onec.xmlgen.writer.SubsystemWriter;
import io.github.onec.xmlgen.writer.MetaWriter;
import io.github.onec.xmlgen.writer.MetaRemover;
import io.github.onec.xmlgen.writer.MetaEditor;
import io.github.onec.xmlgen.writer.ExtensionWriter;
import io.github.onec.xmlgen.editor.ExtensionEditor;
import io.github.onec.xmlgen.validator.ExtensionValidator;
import io.github.onec.xmlgen.info.SubsystemInfoPrinter;
import io.github.onec.xmlgen.info.MetaInfoPrinter;
import io.github.onec.xmlgen.info.ExtensionDiffPrinter;

import io.github.onec.xmlgen.editor.ReplaceTextEditor;

//++agent TASK-155 [22.05.2026 00:00:00]
// TASK-155 A2 iter-3: import RoleRight for compile-path rights validation (bug-T-154-role-002).
import com.github._1c_syntax.bsl.mdo.support.RoleRight;
//++agent TASK-155

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Диспетчер команд CLI.
 */
public class Commands {

    // TASK-155 A2: whitelist for --type in validate command.
    // Includes schema-backed types (form, role, skd, mxl, epf) AND
    // auto-detect types that go through GenValidator without a schema validator
    // (meta, config, extension, subsystem, interface, template).
    // erf is normalised to epf before the check.
    private static final Set<String> KNOWN_VALIDATE_TYPES = new HashSet<>(Arrays.asList(
        "form", "role", "skd", "mxl", "epf", "erf",
        "meta", "config", "extension", "subsystem", "interface", "template"
    ));
    
    public static void execute(String command, String[] args) {
        switch (command.toLowerCase()) {
            case "epf":
                executeEpf(args);
                break;
            case "form":
                executeForm(args);
                break;
            case "role":
                executeRole(args);
                break;
            case "mxl":
                executeMxl(args);
                break;
            case "skd":
                executeSkd(args);
                break;
            case "template":
                executeTemplate(args);
                break;
            case "help":
                executeHelp(args);
                break;
            case "config":
                executeConfig(args);
                break;
            case "subsystem":
                executeSubsystem(args);
                break;
            case "interface":
                executeInterface(args);
                break;
            case "meta":
                executeMeta(args);
                break;
            case "extension":
                executeExtension(args);
                break;
            case "edit":
                executeEdit(args);
                break;
            case "validate":
                executeValidate(args);
                break;
            case "--help":
            case "-h":
                throw new IllegalArgumentException("Use without arguments to see help");
            default:
                throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void executeEpf(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EPF subcommand required: init [--type report], add-form, add-template, add-attribute, add-tabular-section, bsp-init, bsp-add-command");
        }

        String subcommand = args[0];
        switch (subcommand.toLowerCase()) {
            case "init":
                epfInit(args);
                break;
            case "add-form":
                epfAddForm(args);
                break;
            case "add-template":
                epfAddTemplate(args);
                break;
            case "add-attribute":
            case "add-tabular-section":
                epfEdit(args);
                break;
            case "bsp-init":
                epfBspInit(args);
                break;
            case "bsp-add-command":
                epfBspAddCommand(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown EPF subcommand: " + subcommand);
        }
    }

    private static void epfBspInit(String[] args) {
        // xml-gen epf bsp-init <epfPath> --kind <вид> [--target <Type.Name>...]
        //                                [--command-type <тип>] [--api-version <v>] [--version <v>]
        java.nio.file.Path epfPath = null;
        String kindStr = null;
        java.util.List<String> targets = new java.util.ArrayList<>();
        String cmdTypeStr = null;
        String apiVersion = null;
        String version = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--kind".equals(a) && i + 1 < args.length) {
                kindStr = args[++i];
            } else if ("--target".equals(a) && i + 1 < args.length) {
                targets.add(args[++i]);
            } else if ("--command-type".equals(a) && i + 1 < args.length) {
                cmdTypeStr = args[++i];
            } else if ("--api-version".equals(a) && i + 1 < args.length) {
                apiVersion = args[++i];
            } else if ("--version".equals(a) && i + 1 < args.length) {
                version = args[++i];
            } else if (epfPath == null && !a.startsWith("--")) {
                epfPath = Paths.get(a);
            } else if (!a.startsWith("--")) {
                // дополнительный позиционный — игнор
            }
        }

        if (epfPath == null) {
            throw new IllegalArgumentException("Usage: xml-gen epf bsp-init <epfPath> --kind <вид> [--target <Class.Name>]...");
        }
        if (kindStr == null) {
            throw new IllegalArgumentException("--kind is required");
        }

        io.github.onec.xmlgen.model.BspKind kind = io.github.onec.xmlgen.model.BspKind.parse(kindStr);
        io.github.onec.xmlgen.model.BspCommandType cmdType =
                cmdTypeStr != null ? io.github.onec.xmlgen.model.BspCommandType.parse(cmdTypeStr) : null;

        if (kind.requiresTarget() && targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "Kind '" + kind + "' is assignable: at least one --target <Class.Name> is required");
        }

        java.util.List<io.github.onec.xmlgen.model.BspTarget> parsedTargets = new java.util.ArrayList<>();
        for (String t : targets) {
            parsedTargets.add(io.github.onec.xmlgen.model.BspTarget.parse(t));
        }

        io.github.onec.xmlgen.writer.EpfBspApplier.InitOptions opts =
                new io.github.onec.xmlgen.writer.EpfBspApplier.InitOptions();
        opts.kind = kind;
        opts.targets = parsedTargets;
        opts.commandType = cmdType;
        opts.apiVersion = apiVersion;
        opts.version = version;

        try {
            new io.github.onec.xmlgen.writer.EpfBspApplier().init(epfPath, opts);
            System.out.println("BSP-init applied: kind=" + kind + ", targets=" + parsedTargets
                    + " → " + io.github.onec.xmlgen.writer.EpfBspApplier.resolveObjectModule(epfPath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply bsp-init: " + e.getMessage(), e);
        }
    }

    private static void epfBspAddCommand(String[] args) {
        // xml-gen epf bsp-add-command <epfPath> --id <id> --label "<label>" [--type <type>] [--form <FormName>]
        java.nio.file.Path epfPath = null;
        String id = null;
        String label = null;
        String typeStr = null;
        String form = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--id".equals(a) && i + 1 < args.length) {
                id = args[++i];
            } else if ("--label".equals(a) && i + 1 < args.length) {
                label = args[++i];
            } else if ("--type".equals(a) && i + 1 < args.length) {
                typeStr = args[++i];
            } else if ("--form".equals(a) && i + 1 < args.length) {
                form = args[++i];
            } else if (epfPath == null && !a.startsWith("--")) {
                epfPath = Paths.get(a);
            }
        }

        if (epfPath == null || id == null || label == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen epf bsp-add-command <epfPath> --id <id> --label \"<label>\" [--type <type>] [--form <FormName>]");
        }

        io.github.onec.xmlgen.writer.EpfBspApplier.AddCommandOptions opts =
                new io.github.onec.xmlgen.writer.EpfBspApplier.AddCommandOptions();
        opts.identifier = id;
        opts.label = label;
        opts.type = typeStr != null ? io.github.onec.xmlgen.model.BspCommandType.parse(typeStr) : null;
        opts.form = form;

        try {
            new io.github.onec.xmlgen.writer.EpfBspApplier().addCommand(epfPath, opts);
            System.out.println("BSP add-command: " + id + " (" + label + ")");
        } catch (Exception e) {
            throw new RuntimeException("Failed to apply bsp-add-command: " + e.getMessage(), e);
        }
    }
    
    private static void epfInit(String[] args) {
        // Парсинг аргументов: --format <designer|edt> --name <Name> [--type <processor|report>] <output_dir>
        OutputFormat format = OutputFormat.DESIGNER;
        String name = null;
        String synonym = null;
        boolean isReport = false;
        Path outputDir = null;

        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--name".equals(args[i]) && i + 1 < args.length) {
                name = args[++i];
            } else if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                synonym = args[++i];
            } else if ("--type".equals(args[i]) && i + 1 < args.length) {
                String typeArg = args[++i].toLowerCase();
                if ("report".equals(typeArg)) {
                    isReport = true;
                } else if (!"processor".equals(typeArg)) {
                    throw new IllegalArgumentException("--type must be 'processor' or 'report'");
                }
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }

        if (name == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("output directory is required");
        }

        //++agent TASK-155 [22.05.2026 00:00:00]
        // TASK-155 A2 iter-3: name validation for EPF init (bug-T-154-epf-002).
        // 1C metadata names must match [A-Za-z_][A-Za-z0-9_]* (latin only, no spaces/special chars).
        // Names with spaces or special characters produce paths invalid on some 1C Designer versions.
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                "Invalid 1C name: '" + name + "'. " +
                "EPF/ERF names must match [A-Za-z_][A-Za-z0-9_]* " +
                "(Latin letters, digits, and underscores only; must not start with a digit).");
        }
        //++agent TASK-155

        try {
            EpfWriter writer = new EpfWriter(format, isReport);
            writer.init(name, synonym, outputDir);
        } catch (Exception e) {
            String kind = isReport ? "ERF" : "EPF";
            throw new RuntimeException("Failed to create " + kind + ": " + e.getMessage(), e);
        }
    }
    
    private static void epfAddForm(String[] args) {
        // Парсинг: --format <designer|edt> --epf <EpfName> --name <FormName> [--synonym <Synonym>] [--default] <output_dir>
        OutputFormat format = OutputFormat.DESIGNER;
        String epfName = null;
        String formName = null;
        String formSynonym = null;
        boolean setAsDefault = false;
        Path outputDir = null;
        
        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--epf".equals(args[i]) && i + 1 < args.length) {
                epfName = args[++i];
            } else if ("--name".equals(args[i]) && i + 1 < args.length) {
                formName = args[++i];
            } else if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                formSynonym = args[++i];
            } else if ("--default".equals(args[i])) {
                setAsDefault = true;
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }
        
        if (epfName == null) {
            throw new IllegalArgumentException("--epf is required");
        }
        if (formName == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("output directory is required");
        }
        
        //++agent TASK-155 [22.05.2026 00:00:00]
        // TASK-155 A2 iter-3: duplicate form detection (bug-T-154-epf-002 obs #1).
        // Check if <epfName>/Forms/<formName>.xml already exists — if so, refuse to overwrite.
        Path formsMetaXml = outputDir.resolve(epfName).resolve("Forms").resolve(formName + ".xml");
        if (Files.exists(formsMetaXml)) {
            throw new IllegalArgumentException(
                "Form '" + formName + "' already exists in EPF '" + epfName + "' " +
                "(" + formsMetaXml + "). Use a different name or remove the existing form first.");
        }
        //++agent TASK-155

        try {
            EpfWriter writer = new EpfWriter(format);
            writer.addForm(epfName, formName, formSynonym, outputDir, setAsDefault);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add form: " + e.getMessage(), e);
        }
    }
    
    private static void epfAddTemplate(String[] args) {
        // Парсинг: --format <designer|edt> --epf <EpfName> --name <TemplateName> --type <Type> [--synonym <Synonym>] <output_dir>
        OutputFormat format = OutputFormat.DESIGNER;
        String epfName = null;
        String templateName = null;
        String templateSynonym = null;
        String templateType = null;
        Path outputDir = null;
        
        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--epf".equals(args[i]) && i + 1 < args.length) {
                epfName = args[++i];
            } else if ("--name".equals(args[i]) && i + 1 < args.length) {
                templateName = args[++i];
            } else if ("--type".equals(args[i]) && i + 1 < args.length) {
                templateType = args[++i];
            } else if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                templateSynonym = args[++i];
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }
        
        if (epfName == null) {
            throw new IllegalArgumentException("--epf is required");
        }
        if (templateName == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (templateType == null) {
            throw new IllegalArgumentException("--type is required");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("output directory is required");
        }
        
        try {
            EpfWriter writer = new EpfWriter(format);
            writer.addTemplate(epfName, templateName, templateSynonym, templateType, outputDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add template: " + e.getMessage(), e);
        }
    }

    private static void epfEdit(String[] args) {
        Path file = getFileArg(args);
        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            EpfEditor editor = new EpfEditor(doc);
            String cmd = args[0];
            
            if ("add-attribute".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: --type is required for epf add-attribute (bug-T-154-epf-002 obs #2).
                 // Without type the attribute gets a default type silently, making the EPF metadata inconsistent.
                 // Duplicate check: scan existing attributes by name (bug-T-154-epf-002 obs #3).
                 String attrType = getArg(args, "--type", false);
                 if (attrType == null) {
                     throw new IllegalArgumentException(
                         "--type is required for epf add-attribute " +
                         "(e.g. --type String, --type Number, --type Boolean, --type CatalogRef.X)");
                 }
                 String attrName = getArg(args, "--name", true);
                 // Check for duplicate attribute in the EPF XML
                 XmlNode epfAttribs = doc.getRoot().child("ExternalDataProcessor") != null
                     ? doc.getRoot().child("ExternalDataProcessor")
                     : doc.getRoot();
                 // Find Attributes section at any depth by scanning root's Attributes child
                 XmlNode attrSection = doc.getRoot().child("Attributes");
                 if (attrSection == null) {
                     // Try nested ExternalDataProcessor / ExternalReport root pattern
                     for (String childName : new String[]{"ExternalDataProcessor", "ExternalReport"}) {
                         XmlNode container = doc.getRoot().child(childName);
                         if (container != null) {
                             attrSection = container.child("Attributes");
                             break;
                         }
                     }
                 }
                 if (attrSection != null) {
                     for (XmlNode existingAttr : attrSection.children("Attribute")) {
                         XmlNode props = existingAttr.child("Properties");
                         if (props != null) {
                             XmlNode nameNode = props.child("Name");
                             if (nameNode != null && attrName.equals(nameNode.getText())) {
                                 throw new IllegalArgumentException(
                                     "Attribute '" + attrName + "' already exists in EPF. " +
                                     "Use a different name or remove the existing attribute first.");
                             }
                         }
                     }
                 }
                 //++agent TASK-155
                 editor.addAttribute(
                     attrName,
                     attrType,
                     getArg(args, "--synonym", false)
                 );
            } else if ("add-tabular-section".equals(cmd)) {
                 editor.addTabularSection(
                     getArg(args, "--name", true),
                     getArg(args, "--synonym", false)
                 );
            }
            saveAndValidate(doc, file, "epf", args);
        } catch (Exception e) {
            throw new RuntimeException("EPF editor failed: " + e.getMessage(), e);
        }
    }

    private static void executeForm(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Form subcommand required: info, add, remove, compile, edit, add-attribute, add-element, add-command, remove-element, move-element");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            formInfo(args);
        } else if ("add".equals(subcommand.toLowerCase())) {
            formAdd(args);
        } else if ("remove".equals(subcommand.toLowerCase())) {
            formRemove(args);
        } else if ("compile".equals(subcommand.toLowerCase())) {
            formCompile(args);
        } else if ("edit".equals(subcommand.toLowerCase())) {
            formEditJson(args);
        } else if (subcommand.startsWith("add-") || subcommand.endsWith("-element")) {
            formEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown Form subcommand: " + subcommand);
        }
    }
    
    private static void formInfo(String[] args) {
        Path file = null;
        int limit = 150;
        int offset = 0;

        for (int i = 1; i < args.length; i++) {
            if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("Form XML file is required: xml-gen form info <file.xml>");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2: root-element guard — reject non-Form XML before printing.
            // Managed form files (Form.xml) have root localName "Form".
            String rootEl = doc.getRootElement();
            if (!"Form".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <Form> (managed form), got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C managed form.");
            }
            new FormInfoPrinter().print(doc, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse form XML: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen form add <objectXml> <formName> [--synonym <syn>] [--default]
     */
    private static void formAdd(String[] args) {
        Path objectXml = null;
        String formName = null;
        String synonym = null;
        boolean setAsDefault = false;

        for (int i = 1; i < args.length; i++) {
            if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                synonym = args[++i];
            } else if ("--default".equals(args[i])) {
                setAsDefault = true;
            } else if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (formName == null) {
                formName = args[i];
            }
        }

        if (objectXml == null || formName == null) {
            throw new IllegalArgumentException("Usage: xml-gen form add <objectXml> <formName> [--synonym <syn>] [--default]");
        }

        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (editor.hasForm(formName)) {
                throw new IllegalArgumentException("Form '" + formName + "' already exists in ChildObjects");
            }

            String objectType = editor.detectObjectType();
            String objectName = editor.getObjectName();

            // Create scaffold
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            ObjectContainerEditor.createFormScaffold(baseDir, formName, synonym, objectType, objectName);

            // Update ChildObjects
            boolean isFirstForm = !editor.hasAnyForm();
            editor.addForm(formName);
            if (setAsDefault || isFirstForm) {
                String dfValue = objectType + "." + objectName + ".Form." + formName;
                editor.setDefaultForm(dfValue);
            }
            editor.save();

            System.out.println("Added form: " + formName);
            System.out.println("  Metadata: " + baseDir.resolve("Forms").resolve(formName + ".xml"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to add form: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen form remove <objectXml> <formName>
     */
    private static void formRemove(String[] args) {
        Path objectXml = null;
        String formName = null;

        for (int i = 1; i < args.length; i++) {
            if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (formName == null) {
                formName = args[i];
            }
        }

        if (objectXml == null || formName == null) {
            throw new IllegalArgumentException("Usage: xml-gen form remove <objectXml> <formName>");
        }

        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (!editor.removeForm(formName)) {
                //++agent TASK-155 [22.05.2026 00:00:00]
                // TASK-155 A2 iter-3: fail-fast on missing form (bug-T-154-form-002 obs #6).
                // Previously: print "Form not found" + exit=0 (silent no-op).
                // Now: throw → caught by outer try/catch → RuntimeException → Main catches + exit=1.
                throw new IllegalArgumentException(
                    "Form '" + formName + "' not found in ChildObjects of '" + objectXml + "'. " +
                    "Cannot remove a non-existing form.");
                //++agent TASK-155
            }

            editor.clearDefaultFormIfMatches(formName);
            editor.save();

            // Delete form files
            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            Path formMeta = baseDir.resolve("Forms").resolve(formName + ".xml");
            Path formDir = baseDir.resolve("Forms").resolve(formName);

            if (Files.exists(formMeta)) Files.delete(formMeta);
            if (Files.exists(formDir)) {
                Files.walk(formDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }

            System.out.println("Removed form: " + formName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove form: " + e.getMessage(), e);
        }
    }

    private static void formCompile(String[] args) {
        OutputFormat format = OutputFormat.DESIGNER;
        Path inputJson = null;
        Path outputXml = null;

        boolean fromObject = false;
        String presetName = "erp-standard";
        Path presetDir = null;
        Path explicitObject = null;

        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--from-object".equals(args[i])) {
                fromObject = true;
            } else if ("--preset".equals(args[i]) && i + 1 < args.length) {
                presetName = args[++i];
            } else if ("--preset-dir".equals(args[i]) && i + 1 < args.length) {
                presetDir = Paths.get(args[++i]);
            } else if ("--object".equals(args[i]) && i + 1 < args.length) {
                explicitObject = Paths.get(args[++i]);
            } else if (!fromObject && inputJson == null) {
                inputJson = Paths.get(args[i]);
            } else if (outputXml == null) {
                outputXml = Paths.get(args[i]);
            }
        }

        if (fromObject) {
            if (outputXml == null) {
                throw new IllegalArgumentException("output XML file is required");
            }
            try {
                io.github.onec.xmlgen.form.fromobject.FormFromObjectGenerator gen =
                        new io.github.onec.xmlgen.form.fromobject.FormFromObjectGenerator();
                FormDsl dsl = gen.generate(explicitObject, outputXml, presetName, presetDir);
                FormWriter writer = new FormWriter(format);
                writer.create(dsl, outputXml);
            } catch (Exception e) {
                throw new RuntimeException("Failed to compile form from object: " + e.getMessage(), e);
            }
            return;
        }

        if (inputJson == null) {
            throw new IllegalArgumentException("input JSON file is required (or pass --from-object)");
        }
        if (outputXml == null) {
            throw new IllegalArgumentException("output XML file is required");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            FormDsl dsl = mapper.readValue(inputJson.toFile(), FormDsl.class);
            FormWriter writer = new FormWriter(format);
            writer.create(dsl, outputXml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile form: " + e.getMessage(), e);
        }
    }

    private static void formEdit(String[] args) {
        Path file = getFileArg(args);
        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            FormEditor editor = new FormEditor(doc);
            String cmd = args[0];
            
            if ("add-attribute".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: duplicate attribute detection for form add-attribute
                 // (bug-T-154-form-002 main reproducer). Two attributes with the same name
                 // in Form.xml are semantically invalid (1C Designer would reject the form).
                 String addAttrName = getArg(args, "--name", true);
                 XmlNode formAttribs = doc.getRoot().child("Attributes");
                 if (formAttribs != null) {
                     for (XmlNode existingAttr : formAttribs.children("Attribute")) {
                         String existingName = existingAttr.attr("name");
                         if (addAttrName.equals(existingName)) {
                             throw new IllegalArgumentException(
                                 "Attribute '" + addAttrName + "' already exists in form. " +
                                 "Use a different name or remove the existing attribute first.");
                         }
                     }
                 }
                 //++agent TASK-155
                 editor.addAttribute(addAttrName, getArg(args, "--type", true));
            } else if ("add-element".equals(cmd)) {
                 editor.addElement(
                     getArg(args, "--type", true),
                     getArg(args, "--name", true),
                     getArg(args, "--path", false),
                     getArg(args, "--parent", false),
                     getArg(args, "--after", false)
                 );
            } else if ("add-command".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: --action is required for form add-command (bug-T-154-form-002 obs #1).
                 // A command without action is semantically broken (1C cannot call it).
                 // Duplicate command detection (bug-T-154-form-002 obs #2).
                 String addCmdAction = getArg(args, "--action", false);
                 if (addCmdAction == null) {
                     throw new IllegalArgumentException(
                         "--action is required for form add-command " +
                         "(e.g. --action SaveObj specifies the form module procedure to call)");
                 }
                 String addCmdName = getArg(args, "--name", true);
                 XmlNode formCmds = doc.getRoot().child("Commands");
                 if (formCmds != null) {
                     for (XmlNode existingCmd : formCmds.children("Command")) {
                         String existingName = existingCmd.attr("name");
                         if (addCmdName.equals(existingName)) {
                             throw new IllegalArgumentException(
                                 "Command '" + addCmdName + "' already exists in form. " +
                                 "Use a different name or remove the existing command first.");
                         }
                     }
                 }
                 //++agent TASK-155
                 editor.addCommand(
                     addCmdName,
                     getArg(args, "--title", false),
                     addCmdAction
                 );
            } else if ("remove-element".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: fail-fast on missing element for remove-element
                 // (bug-T-154-form-002 obs #3: no-op exit=0 was misleading).
                 String removeElName = getArg(args, "--name", true);
                 XmlNode removeChildItems = doc.getRoot().child("ChildItems");
                 boolean removeElFound = false;
                 if (removeChildItems != null) {
                     removeElFound = findElementByNameRecursive(removeChildItems, removeElName);
                 }
                 if (!removeElFound) {
                     throw new IllegalArgumentException(
                         "Element '" + removeElName + "' not found in form. " +
                         "Cannot remove a non-existing element.");
                 }
                 //++agent TASK-155
                 editor.removeElement(removeElName);
            } else if ("move-element".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: --parent is an alias for --into (bug-T-154-form-002 obs #4).
                 // When the target parent does not exist, FormEditor already throws — so the
                 // fix is just mapping --parent → --into so it reaches the existing check.
                 String moveInto = getArg(args, "--into", false);
                 if (moveInto == null) {
                     moveInto = getArg(args, "--parent", false);
                 }
                 //++agent TASK-155
                 editor.moveElement(
                     getArg(args, "--name", true),
                     getArg(args, "--after", false),
                     getArg(args, "--before", false),
                     moveInto
                 );
            }
            saveAndValidate(doc, file, "form", args);
        } catch (Exception e) {
            throw new RuntimeException("Form editor failed: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen form edit <form.xml> --json <spec.json>
     *
     * Применяет JSON-спецификацию мутаций к существующей Form.xml
     * (атрибуты, элементы, команды, события). Замена Python form-edit.py.
     */
    private static void formEditJson(String[] args) {
        Path formFile = null;
        Path jsonFile = null;

        for (int i = 1; i < args.length; i++) {
            if ("--json".equals(args[i]) && i + 1 < args.length) {
                jsonFile = Paths.get(args[++i]);
            } else if (formFile == null) {
                formFile = Paths.get(args[i]);
            }
        }

        if (formFile == null || jsonFile == null) {
            throw new IllegalArgumentException(
                "Usage: xml-gen form edit <form.xml> --json <spec.json>");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(formFile);
            // Snapshot pre-existing errors, чтобы diff-gate не блокировался на них
            Set<String> preEditErrors = snapshotErrors(doc, "form", args);
            FormEditor editor = new FormEditor(doc);
            ObjectMapper mapper = new ObjectMapper();
            FormEditDsl spec = mapper.readValue(jsonFile.toFile(), FormEditDsl.class);
            // formFile передаётся, чтобы BslStubWriter мог найти соседний Module.bsl
            new FormEditApplier(editor, formFile).apply(spec);
            saveAndValidate(doc, formFile, "form", args, preEditErrors);
        } catch (Exception e) {
            throw new RuntimeException("Form edit failed: " + e.getMessage(), e);
        }
    }

    private static void executeRole(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Role subcommand required: info, compile, add-object, add-right");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            roleInfo(args);
        } else if ("compile".equals(subcommand.toLowerCase())) {
            roleCompile(args);
        } else if (subcommand.startsWith("add-")) {
            roleEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown Role subcommand: " + subcommand);
        }
    }
    
    private static void roleInfo(String[] args) {
        Path file = null;
        boolean showDenied = false;
        int limit = 150;
        int offset = 0;

        for (int i = 1; i < args.length; i++) {
            if ("--show-denied".equals(args[i])) {
                showDenied = true;
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("Rights XML file is required: xml-gen role info <Rights.xml>");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            //++agent TASK-155 [22.05.2026 00:00:00]
            // TASK-155 A2 iter-3: root-element guard for role info.
            // bug-T-154-role-002 obs #1: <root> file printed "(no allowed rights)" + exit=0.
            String roleRoot = doc.getRootElement();
            if (!"Rights".equals(roleRoot)) {
                throw new IllegalArgumentException(
                    "Not a Rights.xml file (expected root <Rights>, got <" + roleRoot + ">). " +
                    "The file does not appear to be a 1C role rights descriptor.");
            }
            //++agent TASK-155
            new RoleInfoPrinter().print(doc, showDenied, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse role XML: " + e.getMessage(), e);
        }
    }

    private static void roleCompile(String[] args) {
        OutputFormat format = OutputFormat.DESIGNER;
        Path inputJson = null;
        Path outputDir = null;
        
        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if (inputJson == null) {
                inputJson = Paths.get(args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }
        
        if (inputJson == null) {
            throw new IllegalArgumentException("input JSON file is required");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("output directory is required");
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            RoleDsl dsl = mapper.readValue(inputJson.toFile(), RoleDsl.class);
            //++agent TASK-155 [22.05.2026 00:00:00]
            // TASK-155 A2 iter-3: validate rights enum before writing Rights.xml.
            // bug-T-154-role-002: "view" (lowercase) was silently written to XML and
            // 1C Designer would reject the resulting Rights.xml. The validator (ROLE-101)
            // already catches this — but compile-path must fail fast too.
            if (dsl.getObjects() != null) {
                for (RoleDsl.ObjectRights obj : dsl.getObjects()) {
                    if (obj.getRights() instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<String> rightsList = (List<String>) obj.getRights();
                        for (String right : rightsList) {
                            if (!isValidRoleRightName(right)) {
                                throw new IllegalArgumentException(
                                    "Invalid right name '" + right + "' for object '" + obj.getName() + "'. " +
                                    "Right names are case-sensitive XML identifiers (e.g. Read, View, Insert, " +
                                    "Update, Delete, Edit, InteractiveInsert, InteractiveDelete, " +
                                    "Posting, UndoPosting). Got: '" + right + "'.");
                            }
                        }
                    }
                }
            }
            //++agent TASK-155
            RoleWriter writer = new RoleWriter(format);
            writer.create(dsl, outputDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile role: " + e.getMessage(), e);
        }
    }

    private static void roleEdit(String[] args) {
        Path file = getFileArg(args);
        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            RoleEditor editor = new RoleEditor(doc);
            String cmd = args[0];
            
            if ("add-object".equals(cmd)) {
                 String rightsStr = getArg(args, "--rights", true);
                 List<String> rights = Arrays.asList(rightsStr.split(","));
                 editor.addObject(getArg(args, "--name", true), rights);
            } else if ("add-right".equals(cmd)) {
                 editor.addRight(
                     getArg(args, "--object", true),
                     getArg(args, "--name", true),
                     getArg(args, "--value", true)
                 );
            }
            saveAndValidate(doc, file, "role", args);
        } catch (Exception e) {
            throw new RuntimeException("Role editor failed: " + e.getMessage(), e);
        }
    }

    //++agent TASK-155 [22.05.2026 00:00:00]
    /**
     * TASK-155 A2 iter-3: helper to check if a right name is a valid 1C RoleRight XML name.
     * Used by roleCompile to validate rights before writing Rights.xml (bug-T-154-role-002).
     * Mirrors the same check in RoleValidator.isKnownRoleRight().
     */
    private static boolean isValidRoleRightName(String name) {
        try {
            for (RoleRight rr : RoleRight.values()) {
                if (rr.fullName().getEn().equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // If enum not available, do not block compilation
            return true;
        }
    }
    //++agent TASK-155

    private static void executeMxl(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("MXL subcommand required: info, compile");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            mxlInfo(args);
        } else if ("decompile".equals(subcommand.toLowerCase())) {
            mxlDecompile(args);
        } else if ("compile".equals(subcommand.toLowerCase())) {
            mxlCompile(args);
        } else {
            throw new IllegalArgumentException("Unknown MXL subcommand: " + subcommand);
        }
    }
    
    private static void mxlDecompile(String[] args) {
        Path inputXml = null;
        Path outputJson = null;

        for (int i = 1; i < args.length; i++) {
            if (inputXml == null) {
                inputXml = Paths.get(args[i]);
            } else if (outputJson == null) {
                outputJson = Paths.get(args[i]);
            }
        }

        if (inputXml == null) {
            throw new IllegalArgumentException("MXL XML file is required: xml-gen mxl decompile <Template.xml> [output.json]");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(inputXml);
            //++agent TASK-155 [22.05.2026 00:00:00]
            // TASK-155 A2 iter-3: reject non-MXL XML before decompiling.
            // bug-T-154-mxl-002 obs #1: <root> file produced exit=0 + broken JSON.
            String mxlRoot = doc.getRootElement();
            if (!"document".equals(mxlRoot)) {
                throw new IllegalArgumentException(
                    "Not an MXL template (expected root <document>, got <" + mxlRoot + ">). " +
                    "The file does not appear to be a 1C spreadsheet document.");
            }
            //++agent TASK-155
            new MxlDecompiler().decompile(doc, outputJson);
        } catch (XmlStructureReader.XmlParseException | IOException e) {
            throw new RuntimeException("Failed to decompile MXL: " + e.getMessage(), e);
        }
    }

    private static void mxlInfo(String[] args) {
        Path file = null;
        boolean withText = false;
        int limit = 150;
        int offset = 0;

        for (int i = 1; i < args.length; i++) {
            if ("--with-text".equals(args[i])) {
                withText = true;
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("MXL XML file is required: xml-gen mxl info <Template.xml>");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2: root-element guard — reject non-MXL XML before printing.
            // MXL files generated by 1C Designer use localName "document" (namespace http://v8.1c.ru/8.2/data/spreadsheet).
            String rootEl = doc.getRootElement();
            if (!"document".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <document> (MXL/SpreadsheetDocument), got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C spreadsheet document.");
            }
            new MxlInfoPrinter().print(doc, withText, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse MXL XML: " + e.getMessage(), e);
        }
    }

    private static void mxlCompile(String[] args) {
        OutputFormat format = OutputFormat.DESIGNER;
        Path inputJson = null;
        Path outputXml = null;
        
        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if (inputJson == null) {
                inputJson = Paths.get(args[i]);
            } else if (outputXml == null) {
                outputXml = Paths.get(args[i]);
            }
        }
        
        if (inputJson == null) {
            throw new IllegalArgumentException("input JSON file is required");
        }
        if (outputXml == null) {
            throw new IllegalArgumentException("output XML file is required");
        }
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            MxlDsl dsl = mapper.readValue(inputJson.toFile(), MxlDsl.class);
            //++agent TASK-155 [22.05.2026 00:00:00]
            // TASK-155 A2 iter-3: empty DSL {} produces a meaningless 724-byte skeleton.
            // bug-T-154-mxl-002: require at least one structural field.
            boolean hasContent = (dsl.getAreas() != null && !dsl.getAreas().isEmpty())
                || dsl.getColumns() != null
                || (dsl.getColumnWidths() != null && !dsl.getColumnWidths().isEmpty())
                || dsl.getPage() != null;
            if (!hasContent) {
                throw new IllegalArgumentException(
                    "MXL DSL requires at least one of: areas, columns, columnWidths, page. " +
                    "Got an empty DSL object {}.");
            }
            //++agent TASK-155
            MxlWriter writer = new MxlWriter(format);
            writer.create(dsl, outputXml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile MXL: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // template command (universal)
    // ============================================================

    private static void executeTemplate(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Template subcommand required: add, remove, add-help");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "add":
                templateAdd(args);
                break;
            case "remove":
                templateRemove(args);
                break;
            case "add-help":
                templateAddHelp(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown Template subcommand: " + subcommand
                        + ". Supported: add, remove, add-help");
        }
    }

    /**
     * xml-gen template add --object &lt;Type.Name&gt; --name &lt;T&gt; --type &lt;TemplateType&gt;
     *                       [--synonym &lt;S&gt;] [--src &lt;dir&gt;] [--set-main-dcs] &lt;configDir&gt;
     *
     * <p>Legacy positional form (for backward compat with pre-existing EPF workflow):
     * xml-gen template add &lt;objectXml&gt; &lt;templateName&gt; [--type &lt;type&gt;]
     */
    private static void templateAdd(String[] args) {
        // Detect which form: new (--object present) or legacy (no --object)
        boolean hasObjectFlag = false;
        for (String a : args) {
            if ("--object".equals(a)) { hasObjectFlag = true; break; }
        }

        if (hasObjectFlag) {
            templateAddNew(args);
        } else {
            templateAddLegacy(args);
        }
    }

    /** New universal form: xml-gen template add --object Type.Name --name T --type TT ... configDir */
    private static void templateAddNew(String[] args) {
        String objectSpec = null;
        String name = null;
        String typeStr = null;
        String synonym = null;
        String srcDir = "src";
        boolean setMainDcs = false;
        Path configDir = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--object".equals(a) && i + 1 < args.length) {
                objectSpec = args[++i];
            } else if ("--name".equals(a) && i + 1 < args.length) {
                name = args[++i];
            } else if ("--type".equals(a) && i + 1 < args.length) {
                typeStr = args[++i];
            } else if ("--synonym".equals(a) && i + 1 < args.length) {
                synonym = args[++i];
            } else if ("--src".equals(a) && i + 1 < args.length) {
                srcDir = args[++i];
            } else if ("--set-main-dcs".equals(a)) {
                setMainDcs = true;
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            }
        }

        if (objectSpec == null) {
            throw new IllegalArgumentException("--object is required (e.g. --object Document.ЗаказКлиента)");
        }
        if (name == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (typeStr == null) {
            throw new IllegalArgumentException("--type is required");
        }
        if (configDir == null) {
            throw new IllegalArgumentException("configDir (positional) is required");
        }

        io.github.onec.xmlgen.model.MdoPath object = io.github.onec.xmlgen.model.MdoPath.parse(objectSpec);
        try {
            new io.github.onec.xmlgen.writer.TemplateWriter()
                    .addTemplate(configDir, object, name, typeStr, synonym, setMainDcs, srcDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add template: " + e.getMessage(), e);
        }
    }

    /**
     * Legacy form: xml-gen template add &lt;objectXml&gt; &lt;templateName&gt; [--type &lt;type&gt;] [--synonym &lt;syn&gt;]
     *
     * <p>Kept for backward compatibility with scripts using the old positional API.
     */
    private static void templateAddLegacy(String[] args) {
        Path objectXml = null;
        String templateName = null;
        String templateType = "SpreadsheetDocument";
        String synonym = null;

        for (int i = 1; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                templateType = args[++i];
            } else if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                synonym = args[++i];
            } else if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (templateName == null) {
                templateName = args[i];
            }
        }

        if (objectXml == null || templateName == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen template add --object Type.Name --name T --type TT [--synonym S] [--src dir] [--set-main-dcs] configDir\n"
                    + "  or (legacy): xml-gen template add <objectXml> <templateName> [--type <type>]");
        }

        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (editor.hasTemplate(templateName)) {
                throw new IllegalArgumentException("Template '" + templateName + "' already exists in ChildObjects");
            }

            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            ObjectContainerEditor.createTemplateScaffold(baseDir, templateName, synonym, templateType);

            editor.addTemplate(templateName);

            // For ERF: auto-set MainDataCompositionSchema when adding DCS template
            if ("DataCompositionSchema".equals(templateType) && "ExternalReport".equals(editor.detectObjectType())) {
                String dcsValue = editor.detectObjectType() + "." + objectName + ".Template." + templateName;
                editor.setMainDataCompositionSchemaIfEmpty(dcsValue);
            }

            editor.save();

            System.out.println("Added template: " + templateName + " (" + templateType + ")");
            System.out.println("  Metadata: " + baseDir.resolve("Templates").resolve(templateName + ".xml"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to add template: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen template remove --object &lt;Type.Name&gt; --name &lt;T&gt; [--src &lt;dir&gt;] &lt;configDir&gt;
     *
     * <p>Legacy positional form:
     * xml-gen template remove &lt;objectXml&gt; &lt;templateName&gt;
     */
    private static void templateRemove(String[] args) {
        // Detect new vs legacy
        boolean hasObjectFlag = false;
        for (String a : args) {
            if ("--object".equals(a)) { hasObjectFlag = true; break; }
        }

        if (hasObjectFlag) {
            templateRemoveNew(args);
        } else {
            templateRemoveLegacy(args);
        }
    }

    /** New form: xml-gen template remove --object Type.Name --name T [--src dir] configDir */
    private static void templateRemoveNew(String[] args) {
        String objectSpec = null;
        String name = null;
        String srcDir = "src";
        Path configDir = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--object".equals(a) && i + 1 < args.length) {
                objectSpec = args[++i];
            } else if ("--name".equals(a) && i + 1 < args.length) {
                name = args[++i];
            } else if ("--src".equals(a) && i + 1 < args.length) {
                srcDir = args[++i];
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            }
        }

        if (objectSpec == null) {
            throw new IllegalArgumentException("--object is required");
        }
        if (name == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (configDir == null) {
            throw new IllegalArgumentException("configDir (positional) is required");
        }

        io.github.onec.xmlgen.model.MdoPath object = io.github.onec.xmlgen.model.MdoPath.parse(objectSpec);

        // TASK-155 A2 iter-2: fail-fast when the template does not exist.
        // TemplateWriter.removeTemplate() silently prints [WARN] and exits 0 for missing
        // templates. Check existence in the CLI layer before delegating so we get exit=1 + ERROR.
        // Use srcDir to resolve the object XML (same logic as TemplateWriter.resolveSrcDir).
        try {
            Path effectiveSrc = "src".equals(srcDir) ? configDir : configDir.resolve(srcDir);
            // Also handle absolute srcDir paths passed by tests
            if (Paths.get(srcDir).isAbsolute()) effectiveSrc = Paths.get(srcDir);
            Path objectXmlForCheck = effectiveSrc.resolve(object.getObjectXmlRelPath());
            if (Files.exists(objectXmlForCheck)) {
                io.github.onec.xmlgen.editor.ObjectContainerEditor checkEditor =
                    new io.github.onec.xmlgen.editor.ObjectContainerEditor(objectXmlForCheck);
                if (!checkEditor.hasTemplate(name)) {
                    throw new IllegalArgumentException(
                        "Template '" + name + "' not found in object " + objectSpec
                        + ". Cannot remove a template that does not exist.");
                }
            }
        } catch (IOException e) {
            // If we can't read the file for the pre-check, let TemplateWriter handle it
        }

        try {
            new io.github.onec.xmlgen.writer.TemplateWriter()
                    .removeTemplate(configDir, object, name, srcDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove template: " + e.getMessage(), e);
        }
    }

    /** Legacy form: xml-gen template remove &lt;objectXml&gt; &lt;templateName&gt; */
    private static void templateRemoveLegacy(String[] args) {
        Path objectXml = null;
        String templateName = null;

        for (int i = 1; i < args.length; i++) {
            if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (templateName == null) {
                templateName = args[i];
            }
        }

        if (objectXml == null || templateName == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen template remove --object Type.Name --name T [--src dir] configDir\n"
                    + "  or (legacy): xml-gen template remove <objectXml> <templateName>");
        }

        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (!editor.removeTemplate(templateName)) {
                System.out.println("Template '" + templateName + "' not found in ChildObjects");
                return;
            }
            editor.save();

            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            Path tplMeta = baseDir.resolve("Templates").resolve(templateName + ".xml");
            Path tplDir = baseDir.resolve("Templates").resolve(templateName);

            if (Files.exists(tplMeta)) Files.delete(tplMeta);
            if (Files.exists(tplDir)) {
                Files.walk(tplDir)
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            }

            System.out.println("Removed template: " + templateName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove template: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen template add-help --object &lt;Type.Name&gt; [--lang &lt;lang&gt;] [--src &lt;dir&gt;] &lt;configDir&gt;
     */
    private static void templateAddHelp(String[] args) {
        String objectSpec = null;
        String lang = "ru";
        String srcDir = "src";
        Path configDir = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--object".equals(a) && i + 1 < args.length) {
                objectSpec = args[++i];
            } else if ("--lang".equals(a) && i + 1 < args.length) {
                lang = args[++i];
            } else if ("--src".equals(a) && i + 1 < args.length) {
                srcDir = args[++i];
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            }
        }

        if (objectSpec == null) {
            throw new IllegalArgumentException("--object is required");
        }
        if (configDir == null) {
            throw new IllegalArgumentException("configDir (positional) is required");
        }

        io.github.onec.xmlgen.model.MdoPath object = io.github.onec.xmlgen.model.MdoPath.parse(objectSpec);
        try {
            new io.github.onec.xmlgen.writer.TemplateWriter()
                    .addHelp(configDir, object, lang, srcDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to add help: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // help command (universal)
    // ============================================================

    private static void executeHelp(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Help subcommand required: add");
        }

        if ("add".equals(args[0].toLowerCase())) {
            helpAdd(args);
        } else {
            throw new IllegalArgumentException("Unknown Help subcommand: " + args[0]);
        }
    }

    /**
     * xml-gen help add <objectXml> [--lang <lang>]
     */
    private static void helpAdd(String[] args) {
        Path objectXml = null;
        String lang = "ru";

        for (int i = 1; i < args.length; i++) {
            if ("--lang".equals(args[i]) && i + 1 < args.length) {
                lang = args[++i];
            } else if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            }
        }

        if (objectXml == null) {
            throw new IllegalArgumentException("Usage: xml-gen help add <objectXml> [--lang <lang>]");
        }

        if (!Files.exists(objectXml)) {
            throw new IllegalArgumentException("Object XML not found: " + objectXml);
        }

        // TASK-155 A2 iter-2: root-element guard — reject non-meta XML before adding help.
        // A valid 1C metadata object XML must have root element <MetaDataObject>,
        // <ExternalDataProcessor>, or <ExternalReport>. Applying help add to arbitrary XML
        // creates files in the wrong directory (BOUNDARY LEAK).
        try {
            XmlDocument helpGuardDoc = new XmlStructureReader().parse(objectXml);
            String helpRootEl = helpGuardDoc.getRootElement();
            if (!"MetaDataObject".equals(helpRootEl)
                    && !"ExternalDataProcessor".equals(helpRootEl)
                    && !"ExternalReport".equals(helpRootEl)) {
                throw new IllegalArgumentException(
                    "Expected a 1C metadata object XML (root <MetaDataObject>, <ExternalDataProcessor>, "
                    + "or <ExternalReport>), got <" + helpRootEl + ">. "
                    + "The file does not appear to be a 1C metadata object descriptor.");
            }
        } catch (XmlStructureReader.XmlParseException e) {
            throw new IllegalArgumentException("Cannot parse XML file: " + objectXml + " — " + e.getMessage());
        }

        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            ObjectContainerEditor.createHelpScaffold(baseDir, lang);

            System.out.println("Added help for: " + objectName);
            System.out.println("  Help.xml: " + baseDir.resolve("Ext").resolve("Help.xml"));
            System.out.println("  HTML: " + baseDir.resolve("Ext").resolve("Help").resolve(lang + ".html"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to add help: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // skd command
    // ============================================================

    private static void executeSkd(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("SKD subcommand required: info, compile, edit, add-field, add-parameter");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            skdInfo(args);
        } else if ("compile".equals(subcommand.toLowerCase())) {
            skdCompile(args);
        } else if ("edit".equals(subcommand.toLowerCase())) {
            skdEditOperation(args);
        } else if (subcommand.startsWith("add-")) {
            skdEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown SKD subcommand: " + subcommand);
        }
    }
    
    private static void skdInfo(String[] args) {
        Path file = null;
        String mode = "overview";
        String name = null;
        int limit = 150;
        int offset = 0;

        for (int i = 1; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if ("--name".equals(args[i]) && i + 1 < args.length) {
                name = args[++i];
            } else if ("--dataSet".equals(args[i]) && i + 1 < args.length) {
                // --dataSet is an alias for --name when used with query/fields modes
                name = args[++i];
            } else if ("--variant".equals(args[i]) && i + 1 < args.length) {
                // --variant is an alias for --name when used with variant mode
                name = args[++i];
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("SKD XML file is required: xml-gen skd info <file.xml> [--mode <m>] [--name <n>]");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2: root-element guard — reject non-SKD XML before printing
            String rootEl = doc.getRootElement();
            if (!"DataCompositionSchema".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <DataCompositionSchema>, got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C data composition schema.");
            }
            new SkdInfoPrinter().print(doc, mode, name, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse SKD XML: " + e.getMessage(), e);
        }
    }

    private static void skdCompile(String[] args) {
        OutputFormat format = OutputFormat.DESIGNER;
        Path inputJson = null;
        Path outputXml = null;
        Path includeBase = null;

        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--include-base".equals(args[i]) && i + 1 < args.length) {
                includeBase = Paths.get(args[++i]);
            } else if (inputJson == null) {
                inputJson = Paths.get(args[i]);
            } else if (outputXml == null) {
                outputXml = Paths.get(args[i]);
            }
        }

        if (inputJson == null) {
            throw new IllegalArgumentException("input JSON file is required");
        }
        if (outputXml == null) {
            throw new IllegalArgumentException("output XML file is required");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            SkdDsl dsl = mapper.readValue(inputJson.toFile(), SkdDsl.class);
            //++agent TASK-155 [22.05.2026 00:00:00]
            // TASK-155 A2 iter-3: fail-fast — DSL without the dataSets field is invalid.
            // bug-T-154-skd-002: {"name": "MissingDataSets"} (no dataSets key) → expected exit=1.
            // Note: dataSets:[] (empty array) is a valid empty schema → allowed (exit=0).
            if (dsl.getDataSets() == null) {
                throw new IllegalArgumentException(
                    "SKD DSL requires 'dataSets' field (field is absent). " +
                    "Add \"dataSets\": [] for an empty schema or populate it with dataset objects.");
            }
            //++agent TASK-155
            // По умолчанию резолвим @file:-include относительно директории JSON.
            Path base = includeBase != null ? includeBase : inputJson.toAbsolutePath().getParent();
            SkdWriter writer = new SkdWriter(format).withIncludeBase(base);
            writer.create(dsl, outputXml);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compile SKD: " + e.getMessage(), e);
        }
    }

    private static void skdEdit(String[] args) {
        Path file = getFileArg(args);
        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            SkdEditor editor = new SkdEditor(doc);
            String cmd = args[0];

            if ("add-parameter".equals(cmd)) {
                 //++agent TASK-155 [22.05.2026 00:00:00]
                 // TASK-155 A2 iter-3: --type is required for add-parameter (bug-T-154-skd-002 obs #1).
                 // Without a type the parameter XML would be written without <valueType> — invalid for 1C.
                 String paramType = getArg(args, "--type", false);
                 if (paramType == null) {
                     throw new IllegalArgumentException(
                         "--type is required for skd add-parameter " +
                         "(e.g. --type string, --type decimal, --type date, --type boolean, --type CatalogRef.X)");
                 }
                 //++agent TASK-155
                 editor.addParameter(
                     getArg(args, "--name", true),
                     getArg(args, "--title", false),
                     paramType
                 );
            } else if ("add-field".equals(cmd)) {
                 editor.addField(
                     getArg(args, "--dataset", true),
                     getArg(args, "--name", true),
                     getArg(args, "--path", true),
                     getArg(args, "--title", false)
                 );
            }
            saveAndValidate(doc, file, "skd", args);
        } catch (Exception e) {
            throw new RuntimeException("SKD editor failed: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen skd edit &lt;SchemaPath&gt; &lt;operation&gt; "&lt;value&gt;"
     *                  [--dataSet &lt;name&gt;] [--variant &lt;name&gt;] [--no-selection]
     *
     * <p>Реализация SPEC §5: полный набор patch-операций.
     */
    private static void skdEditOperation(String[] args) {
        // Positional: args[0]="edit", args[1]=<SchemaPath>, args[2]=<operation>, args[3]=<value>
        Path schemaPath = null;
        String operation = null;
        String value = null;
        String dataSet = null;
        String variant = null;
        boolean noSelection = false;

        int positional = 0;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--dataSet".equals(a) && i + 1 < args.length) {
                dataSet = args[++i];
            } else if ("--variant".equals(a) && i + 1 < args.length) {
                variant = args[++i];
            } else if ("--no-selection".equals(a)) {
                noSelection = true;
            } else if (!a.startsWith("--")) {
                if (positional == 0) schemaPath = Paths.get(a);
                else if (positional == 1) operation = a;
                else if (positional == 2) value = a;
                positional++;
            } else {
                throw new IllegalArgumentException("Unknown option: " + a);
            }
        }

        if (schemaPath == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen skd edit <SchemaPath> <operation> \"<value>\" "
                            + "[--dataSet <n>] [--variant <n>] [--no-selection]");
        }

        // Папка достраивается до Ext/Template.xml
        if (Files.isDirectory(schemaPath)) {
            Path ext = schemaPath.resolve("Ext").resolve("Template.xml");
            if (Files.exists(ext)) {
                schemaPath = ext;
            } else {
                Path direct = schemaPath.resolve("Template.xml");
                if (Files.exists(direct)) schemaPath = direct;
                else throw new IllegalArgumentException(
                        "Schema file not found: " + schemaPath + "/Ext/Template.xml");
            }
        }
        if (!Files.exists(schemaPath)) {
            throw new IllegalArgumentException("Schema file not found: " + schemaPath);
        }

        try {
            // Read raw bytes once for rollback
            byte[] originalBytes = Files.readAllBytes(schemaPath);
            try {
                io.github.onec.xmlgen.validator.XmlDocument doc =
                        new io.github.onec.xmlgen.validator.XmlStructureReader().parse(schemaPath);
                io.github.onec.xmlgen.editor.SkdEditor editor =
                        new io.github.onec.xmlgen.editor.SkdEditor(doc);

                applySkdOperation(editor, operation, value, dataSet, variant, noSelection, schemaPath);

                saveAndValidate(doc, schemaPath, "skd", args);
            } catch (Exception e) {
                // Rollback: restore bytes
                try {
                    Files.write(schemaPath, originalBytes);
                } catch (Exception rollbackEx) {
                    // best-effort
                }
                throw e;
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("SKD edit failed: " + e.getMessage(), e);
        }
    }

    /**
     * Применить одну операцию SKD edit (с возможным batch через {@code ;;}).
     * Некоторые операции batch не поддерживают (set-query, modify-structure,
     * patch-query без @once в составе spec → проверяется отдельно).
     */
    private static void applySkdOperation(io.github.onec.xmlgen.editor.SkdEditor editor,
                                          String operation, String value,
                                          String dataSet, String variant, boolean noSelection,
                                          Path schemaPath) {
        java.util.List<String> parts;
        boolean allowBatch = !operation.equals("set-query")
                && !operation.equals("modify-structure")
                && !operation.equals("patch-query"); // patch-query has its own batch logic
        if (allowBatch) {
            parts = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.splitBatch(value);
            if (parts.isEmpty()) parts = java.util.List.of(value);
        } else {
            parts = java.util.List.of(value);
        }

        for (String spec : parts) {
            applySingleSkdOp(editor, operation, spec, dataSet, variant, noSelection, schemaPath);
        }
    }

    private static void applySingleSkdOp(io.github.onec.xmlgen.editor.SkdEditor editor,
                                         String op, String spec,
                                         String dataSet, String variant, boolean noSelection,
                                         Path schemaPath) {
        switch (op) {
            case "add-field": {
                var fd = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseField(spec);
                editor.addField(fd, dataSet, variant, noSelection);
                return;
            }
            case "modify-field": {
                var fd = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseField(spec);
                editor.modifyField(fd, dataSet);
                return;
            }
            case "remove-field": {
                editor.removeField(spec.trim(), dataSet, variant);
                return;
            }
            case "set-field-role": {
                var d = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseFieldRole(spec);
                editor.setFieldRole(d, dataSet);
                return;
            }
            case "add-parameter": {
                var p = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseParameter(spec);
                editor.addParameter(p);
                return;
            }
            case "modify-parameter": {
                var p = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseModifyParameter(spec);
                editor.modifyParameter(p);
                return;
            }
            case "remove-parameter": {
                editor.removeParameter(spec.trim());
                return;
            }
            case "rename-parameter": {
                var arrow = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseArrow(spec, false);
                editor.renameParameter(arrow.oldText.trim(), arrow.newText.trim());
                return;
            }
            case "reorder-parameters": {
                var order = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseReorderParameters(spec);
                editor.reorderParameters(order);
                return;
            }
            case "add-total": {
                var t = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseTotal(spec);
                editor.addTotal(t);
                return;
            }
            case "remove-total": {
                editor.removeTotal(spec.trim());
                return;
            }
            case "modify-structure": {
                var s = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseStructureSpec(spec);
                editor.modifyStructure(s, variant);
                return;
            }
            case "set-query": {
                String text = spec;
                if (text.startsWith("@")) {
                    // file reference: resolve relative to schema, then cwd
                    String pathSpec = text.substring(1);
                    Path resolved = schemaPath.getParent() != null
                            ? schemaPath.getParent().resolve(pathSpec)
                            : Paths.get(pathSpec);
                    if (!Files.exists(resolved)) {
                        resolved = Paths.get(pathSpec);
                    }
                    if (!Files.exists(resolved)) {
                        throw new RuntimeException("query file not found: " + pathSpec);
                    }
                    try {
                        text = Files.readString(resolved, java.nio.charset.StandardCharsets.UTF_8);
                    } catch (java.io.IOException ioe) {
                        throw new RuntimeException("failed to read query file: " + ioe.getMessage(), ioe);
                    }
                }
                editor.setQuery(text, dataSet);
                return;
            }
            case "patch-query": {
                // patch-query batch via ;; is allowed (per skill query.md "batch supported").
                java.util.List<String> patches =
                        io.github.onec.xmlgen.editor.skd.SkdShorthandParser.splitBatch(spec);
                if (patches.isEmpty()) patches = java.util.List.of(spec);
                for (String p : patches) editor.patchQuery(p, dataSet);
                return;
            }
            case "clear-conditionalAppearance": {
                if (!"*".equals(spec.trim())) {
                    throw new IllegalArgumentException(
                            "clear-conditionalAppearance: only '*' wildcard is supported");
                }
                editor.clearConditionalAppearance(variant);
                return;
            }
            default:
                throw new IllegalArgumentException("Unknown SKD edit operation: " + op);
        }
    }

    // ============================================================
    // validate command
    // ============================================================

    private static MetadataTypeValidator createMetadataValidator(String[] args) {
        String srcRootStr = getArg(args, "--src-root", false);
        Path srcRoot = srcRootStr != null ? Paths.get(srcRootStr) : null;
        return new MetadataTypeValidator(srcRoot);
    }

    private static void executeValidate(String[] args) {
        // Парсинг: [--type <form|role|skd|mxl|epf>] [--format <designer|edt>] [--src-root <path>]
        //          [--level <structure|semantic>] [--output <text|json>] <file1> [file2] ...
        String type = null;
        String formatStr = "designer";
        ValidationLevel level = ValidationLevel.SEMANTIC;
        String output = "text";
        List<Path> files = new ArrayList<>();
        
        MetadataTypeValidator metadataValidator = createMetadataValidator(args);

        for (int i = 0; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                type = args[++i].toLowerCase();
                if ("erf".equals(type)) type = "epf"; // ERF uses same validator as EPF
                // TASK-155 A2: whitelist --type — reject unknown type values early
                if (!KNOWN_VALIDATE_TYPES.contains(type)) {
                    throw new IllegalArgumentException(
                        "Unknown --type value: \"" + type + "\". Expected one of: " +
                        "form, role, skd, mxl, epf, meta, config, extension, subsystem, interface, template");
                }
            } else if ("--format".equals(args[i]) && i + 1 < args.length) {
                formatStr = args[++i].toLowerCase();
            } else if ("--level".equals(args[i]) && i + 1 < args.length) {
                String lvl = args[++i].toLowerCase();
                level = "structure".equals(lvl) ? ValidationLevel.STRUCTURE : ValidationLevel.SEMANTIC;
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                output = args[++i].toLowerCase();
            } else if ("--src-root".equals(args[i]) && i + 1 < args.length) {
                i++; // Skip value (already handled)
            } else if (!args[i].startsWith("--")) {
                files.add(Paths.get(args[i]));
            }
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: validate [--type <form|role|skd|mxl|epf>] [--output <text|json>] [--src-root <path>] <file> [files...]");
        }

        XmlStructureReader reader = new XmlStructureReader();
        ValidatorFactory factory = new ValidatorFactory();
        GenValidator genValidator = new GenValidator(metadataValidator);
        TextReporter textReporter = new TextReporter();
        JsonReporter jsonReporter = new JsonReporter();

        boolean hasErrors = false;
        boolean hasWarnings = false;

        for (Path file : files) {
            XmlDocument document;
            try {
                document = reader.parse(file);
            } catch (XmlStructureReader.XmlParseException e) {
                List<ValidationIssue> parseIssues = List.of(
                        ValidationIssue.error("GEN-001", e.getMessage(), 0, "/")
                );
                ValidationResult parseResult = new ValidationResult(
                        file, type != null ? type : "unknown", formatStr, parseIssues);
                System.out.println("text".equals(output)
                        ? textReporter.format(parseResult)
                        : jsonReporter.format(parseResult));
                hasErrors = true;
                continue;
            }

            String objectType = type;
            if (objectType == null) {
                Optional<XmlValidator> detected = factory.detectValidator(document);
                objectType = detected.map(XmlValidator::objectType).orElse(detectTypeByRoot(document));
            }

            boolean expectBom = "designer".equals(formatStr) && isMetadataFile(objectType);
            List<ValidationIssue> allIssues = new ArrayList<>(genValidator.validate(document, objectType, expectBom));

            Optional<XmlValidator> validator = type != null
                    ? factory.getValidator(type)
                    : factory.detectValidator(document);
            if (validator.isPresent()) {
                allIssues.addAll(validator.get().validate(document, level));
            }

            ValidationResult result = new ValidationResult(file, objectType, formatStr, allIssues);

            if (!result.isValid()) hasErrors = true;
            if (result.warningCount() > 0) hasWarnings = true;

            System.out.println("text".equals(output)
                    ? textReporter.format(result)
                    : jsonReporter.format(result));
        }

        if (hasErrors) {
            System.exit(1);
        } else if (hasWarnings) {
            System.exit(2);
        }
    }
    
    // --- Helpers ---

    private static String detectTypeByRoot(XmlDocument doc) {
        switch (doc.getRootElement()) {
            case "Rights": return "role";
            case "Form": return "form";
            case "DataCompositionSchema": return "skd";
            case "document": return "mxl";
            case "ExternalDataProcessor": return "epf";
            case "ExternalReport": return "epf";
            default: return "unknown";
        }
    }

    private static boolean isMetadataFile(String type) {
        return "role".equals(type) || "form".equals(type) || "epf".equals(type);
    }
    
    private static String getArg(String[] args, String key, boolean required) {
        for (int i = 1; i < args.length; i++) {
            if (key.equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        if (required) throw new IllegalArgumentException("Argument " + key + " is required");
        return null;
    }
    
    private static Path getFileArg(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("File argument required");
        // File is the last positional argument (not starting with --)
        for (int i = args.length - 1; i >= 1; i--) {
            if (!args[i].startsWith("--")) {
                // Skip values of named arguments
                if (i > 0 && args[i - 1].startsWith("--")) {
                    continue;
                }
                Path file = Paths.get(args[i]);
                if (!Files.exists(file)) throw new IllegalArgumentException("File not found: " + file);
                return file;
            }
        }
        throw new IllegalArgumentException("File argument required");
    }
    
    private static void saveAndValidate(XmlDocument doc, Path file, String type, String[] args) throws Exception {
        saveAndValidate(doc, file, type, args, null);
    }

    /**
     * Сохранить документ и отвалидировать. Если {@code preEditSnapshot != null},
     * gate срабатывает только на НОВЫЕ ошибки, появившиеся после редактирования
     * (pre-existing issues выводятся как warning). Это позволяет редактировать
     * формы, в которых уже есть известные проблемы, не теряя блокировку регрессий.
     *
     * <p>Если {@code preEditSnapshot == null} — старое поведение (строгий gate).</p>
     */
    private static void saveAndValidate(XmlDocument doc, Path file, String type, String[] args,
                                         Set<String> preEditSnapshot) throws Exception {
        MetadataTypeValidator metadataValidator = createMetadataValidator(args);
        GenValidator genValidator = new GenValidator(metadataValidator);

        List<ValidationIssue> issues = new ArrayList<>();
        boolean expectBom = isMetadataFile(type);
        issues.addAll(genValidator.validate(doc, type, expectBom));
        issues.addAll(new ValidatorFactory().getValidator(type)
                .map(v -> v.validate(doc, ValidationLevel.SEMANTIC))
                .orElse(List.of()));

        List<ValidationIssue> blockers;
        if (preEditSnapshot != null) {
            blockers = issues.stream()
                    .filter(i -> i.getSeverity() == Severity.ERROR)
                    .filter(i -> !preEditSnapshot.contains(issueKey(i)))
                    .toList();
            long preExistingErrors = issues.stream()
                    .filter(i -> i.getSeverity() == Severity.ERROR)
                    .filter(i -> preEditSnapshot.contains(issueKey(i)))
                    .count();
            if (preExistingErrors > 0) {
                System.err.println("[WARN] " + preExistingErrors
                        + " pre-existing validation error(s) present in " + file.getFileName()
                        + " — kept as-is (use xmlgen validate to inspect).");
            }
        } else {
            blockers = issues.stream()
                    .filter(i -> i.getSeverity() == Severity.ERROR)
                    .toList();
        }

        if (!blockers.isEmpty()) {
            System.err.println(preEditSnapshot != null
                    ? "Edit introduced new validation errors:"
                    : "Validation failed after modification:");
            ValidationResult result = new ValidationResult(file, type, "designer", blockers);
            System.err.println(new TextReporter().format(result));
            System.exit(1);
        }

        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            new XmlDocumentWriter().write(doc, tmpFile);
            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }
        System.out.println("Modified " + file);
    }

    /** Уникальный ключ ошибки для diff pre/post (code + element + message). */
    private static String issueKey(ValidationIssue i) {
        return i.getCode() + "\u0001" + i.getElement() + "\u0001" + i.getMessage();
    }

    /**
     * Сделать snapshot текущих errors для последующего diff-based gate в
     * {@link #saveAndValidate(XmlDocument, Path, String, String[], Set)}.
     */
    private static Set<String> snapshotErrors(XmlDocument doc, String type, String[] args) {
        MetadataTypeValidator metadataValidator = createMetadataValidator(args);
        GenValidator genValidator = new GenValidator(metadataValidator);
        boolean expectBom = isMetadataFile(type);
        List<ValidationIssue> issues = new ArrayList<>(genValidator.validate(doc, type, expectBom));
        new ValidatorFactory().getValidator(type)
                .ifPresent(v -> issues.addAll(v.validate(doc, ValidationLevel.SEMANTIC)));
        Set<String> keys = new HashSet<>();
        for (ValidationIssue i : issues) {
            if (i.getSeverity() == Severity.ERROR) {
                keys.add(issueKey(i));
            }
        }
        return keys;
    }

    // ============================================================
    // config command
    // ============================================================

    private static void executeConfig(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Config subcommand required: init, info, edit, validate");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "init":
                configInit(args);
                break;
            case "info":
                configInfo(args);
                break;
            case "edit":
                configEdit(args);
                break;
            case "validate":
                configValidate(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown config subcommand: " + args[0]);
        }
    }

    /**
     * xml-gen config init <outputDir> <name> [--synonym <syn>] [--version <ver>] [--vendor <vendor>]
     *                                        [--lang-name <langName>] [--lang-code <langCode>]
     */
    private static void configInit(String[] args) {
        Path outputDir = null;
        String name = null;
        String synonym = null;
        String version = null;
        String vendor = null;
        String langName = null;
        String langCode = null;
        String compat = null;          // TASK-171 D-10: CompatibilityMode
        String formatVersion = null;   // TASK-171 D-10: версия формата (атрибут version)

        for (int i = 1; i < args.length; i++) {
            if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                synonym = args[++i];
            } else if ("--version".equals(args[i]) && i + 1 < args.length) {
                version = args[++i];
            } else if ("--vendor".equals(args[i]) && i + 1 < args.length) {
                vendor = args[++i];
            } else if ("--lang-name".equals(args[i]) && i + 1 < args.length) {
                langName = args[++i];
            } else if ("--lang-code".equals(args[i]) && i + 1 < args.length) {
                langCode = args[++i];
            } else if ("--compat".equals(args[i]) && i + 1 < args.length) {
                compat = args[++i];
            } else if ("--format-version".equals(args[i]) && i + 1 < args.length) {
                formatVersion = args[++i];
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            } else if (name == null) {
                name = args[i];
            }
        }

        if (outputDir == null || name == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen config init <outputDir> <name> [--synonym <syn>] [--version <ver>] "
                    + "[--vendor <vendor>] [--compat <Version8_3_NN>] [--format-version <2.NN>]");
        }

        //++agent TASK-155 [22.05.2026 00:00:00]
        // TASK-155 A2 iter-3: config init validations (bug-T-154-config-002).
        // (1) Name validation — same pattern as epf init and extension init.
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException(
                "Invalid configuration name: '" + name + "'. " +
                "Configuration names must match [A-Za-z_][A-Za-z0-9_]* " +
                "(Latin letters, digits, and underscores only; must not start with a digit).");
        }
        // (2) Existing-dir guard — refuse to silently overwrite an existing Configuration.xml.
        // Use --force flag to allow overwrite (currently not implemented, fail by default).
        Path existingConfigXml = outputDir.resolve("Configuration.xml");
        if (Files.exists(existingConfigXml)) {
            throw new IllegalArgumentException(
                "Configuration.xml already exists in '" + outputDir + "'. " +
                "Remove the existing configuration first or choose a different output directory.");
        }
        //++agent TASK-155

        try {
            new ConfigWriter().create(outputDir, name, synonym, version, vendor, langName, langCode,
                    compat, formatVersion);
            System.out.println("Created configuration: " + name);
            System.out.println("  Configuration.xml: " + outputDir.resolve("Configuration.xml"));
            System.out.println("  ConfigDumpInfo.xml: " + outputDir.resolve("ConfigDumpInfo.xml"));
            System.out.println("  Languages/: " + outputDir.resolve("Languages"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create configuration: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen config info <configXml> [--mode overview|brief|full] [--limit N] [--offset N]
     */
    private static void configInfo(String[] args) {
        Path file = null;
        String mode = "overview";
        int limit = 150;
        int offset = 0;

        for (int i = 1; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("Usage: xml-gen config info <Configuration.xml> [--mode overview|brief|full]");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2: root-element guard — reject non-Configuration XML before printing.
            // Configuration.xml has root localName "MetaDataObject" with a child <Configuration>.
            String rootEl = doc.getRootElement();
            if (!"MetaDataObject".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <MetaDataObject> (Configuration.xml), got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C configuration descriptor.");
            }
            new ConfigInfoPrinter().print(doc, mode, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse Configuration XML: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen config edit <configXml> --op <operation> --value <value>
     *
     * Operations:
     *   modify-property  --value "Prop=Value ;; Prop2=Value2"
     *   add-childObject  --value "Type.Name ;; Type.Name2"
     *   remove-childObject --value "Type.Name"
     *   add-defaultRole  --value "RoleName"
     *   remove-defaultRole --value "RoleName"
     *   set-defaultRoles --value "Role1 ;; Role2"
     */
    private static void configEdit(String[] args) {
        Path file = null;
        String operation = null;
        String value = null;
        boolean noFileCheck = false;

        for (int i = 1; i < args.length; i++) {
            if ("--op".equals(args[i]) && i + 1 < args.length) {
                operation = args[++i];
            } else if ("--value".equals(args[i]) && i + 1 < args.length) {
                value = args[++i];
            } else if ("--no-file-check".equals(args[i])) {
                noFileCheck = true;
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen config edit <Configuration.xml> --op <operation> --value <value> [--no-file-check]");
        }

        try {
            ConfigEditor editor = new ConfigEditor(file);
            editor.setSkipFileCheck(noFileCheck);

            switch (operation) {
                case "modify-property":
                    editor.modifyProperty(value);
                    break;
                case "add-childObject":
                    editor.addChildObject(value);
                    break;
                case "remove-childObject":
                    editor.removeChildObject(value);
                    break;
                case "add-defaultRole":
                    editor.addDefaultRole(value);
                    break;
                case "remove-defaultRole":
                    editor.removeDefaultRole(value);
                    break;
                case "set-defaultRoles":
                    editor.setDefaultRoles(value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation
                            + ". Supported: modify-property, add-childObject, remove-childObject, "
                            + "add-defaultRole, remove-defaultRole, set-defaultRoles");
            }

            editor.save();
            System.out.println("Configuration updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit configuration: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen config validate <Configuration.xml|configDir>
     */
    private static void configValidate(String[] args) {
        Path target = null;
        int maxErrors = 30;

        for (int i = 1; i < args.length; i++) {
            if ("--max-errors".equals(args[i]) && i + 1 < args.length) {
                maxErrors = Integer.parseInt(args[++i]);
            } else if (target == null) {
                target = Paths.get(args[i]);
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Usage: xml-gen config validate <Configuration.xml|configDir>");
        }

        // Resolve Configuration.xml
        Path configXml = target;
        Path configDir = null;
        if (Files.isDirectory(target)) {
            configXml = target.resolve("Configuration.xml");
            configDir = target;
        } else {
            configDir = target.getParent();
        }

        if (!Files.exists(configXml)) {
            throw new IllegalArgumentException("Configuration.xml not found: " + configXml);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(configXml);
            ConfigValidator validator = new ConfigValidator();
            List<ConfigValidator.ValidationMessage> messages = validator.validate(doc, configDir);

            if (messages.isEmpty()) {
                System.out.println("OK: Configuration is valid");
                return;
            }

            int errors = 0;
            int warnings = 0;
            int shown = 0;
            for (ConfigValidator.ValidationMessage msg : messages) {
                if ("ERROR".equals(msg.level)) errors++;
                else warnings++;

                if (shown < maxErrors) {
                    System.out.println(msg);
                    shown++;
                }
            }

            if (shown < messages.size()) {
                System.out.println("... and " + (messages.size() - shown) + " more");
            }

            System.out.println();
            System.out.println("Summary: " + errors + " errors, " + warnings + " warnings");

            if (errors > 0) {
                System.exit(1);
            }
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse Configuration XML: " + e.getMessage(), e);
        }
    }

    // ==================== Subsystem ====================

    private static void executeSubsystem(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Subsystem subcommand required: compile, info, edit, validate");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "compile":
                subsystemCompile(args);
                break;
            case "info":
                subsystemInfo(args);
                break;
            case "edit":
                subsystemEdit(args);
                break;
            case "validate":
                subsystemValidate(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown subsystem subcommand: " + args[0]);
        }
    }

    /**
     * xml-gen subsystem compile <jsonPath> <outputDir> [--parent &lt;parentSubsystem.xml&gt;] [--no-stubs]
     */
    private static void subsystemCompile(String[] args) {
        Path jsonPath = null;
        Path outputDir = null;
        Path parentPath = null;
        boolean noStubs = false;

        for (int i = 1; i < args.length; i++) {
            if ("--parent".equals(args[i]) && i + 1 < args.length) {
                parentPath = Paths.get(args[++i]);
            } else if ("--no-stubs".equals(args[i])) {
                noStubs = true;
            } else if (jsonPath == null) {
                jsonPath = Paths.get(args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }

        if (jsonPath == null || outputDir == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen subsystem compile <jsonPath> <outputDir> [--parent <parentSubsystem.xml>] [--no-stubs]");
        }

        try {
            SubsystemWriter writer = new SubsystemWriter();
            writer.setWriteStubs(!noStubs);
            writer.compile(jsonPath, outputDir, parentPath);
            System.out.println("Subsystem created in: " + outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compile subsystem: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen subsystem info <subsystemXml> [--mode brief|overview|full|tree|ci]
     */
    private static void subsystemInfo(String[] args) {
        Path file = null;
        String mode = "overview";

        for (int i = 1; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen subsystem info <Subsystem.xml> [--mode brief|overview|full|tree|ci]");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2: root-element guard — reject non-Subsystem XML before printing.
            // Subsystem.xml has root localName "MetaDataObject" with a child <Subsystem>.
            String rootEl = doc.getRootElement();
            if (!"MetaDataObject".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <MetaDataObject> (Subsystem.xml), got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C subsystem descriptor.");
            }
            new SubsystemInfoPrinter().print(doc, mode, file, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse Subsystem XML: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen subsystem edit <subsystemXml> --op <operation> --value <value>
     *
     * Operations:
     *   add-content     --value '["Type.Name1","Type.Name2"]' or "Type.Name"
     *   remove-content  --value '["Type.Name"]' or "Type.Name"
     *   add-child       --value "ChildName"
     *   remove-child    --value "ChildName"
     *   set-property    --value "PropName=Value"
     */
    private static void subsystemEdit(String[] args) {
        Path file = null;
        String operation = null;
        String value = null;

        for (int i = 1; i < args.length; i++) {
            if ("--op".equals(args[i]) && i + 1 < args.length) {
                operation = args[++i];
            } else if ("--value".equals(args[i]) && i + 1 < args.length) {
                value = args[++i];
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen subsystem edit <Subsystem.xml> --op <operation> --value <value>");
        }

        try {
            SubsystemEditor editor = new SubsystemEditor(file);

            switch (operation) {
                case "add-content":
                    editor.addContent(value);
                    break;
                case "remove-content":
                    editor.removeContent(value);
                    break;
                case "add-child":
                    editor.addChild(value);
                    break;
                case "remove-child":
                    editor.removeChild(value);
                    break;
                case "set-property":
                    editor.setProperty(value);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation
                            + ". Supported: add-content, remove-content, add-child, remove-child, set-property");
            }

            editor.save();
            System.out.println("Subsystem updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit subsystem: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen subsystem validate <subsystemXml> [--max-errors N]
     */
    private static void subsystemValidate(String[] args) {
        Path target = null;
        int maxErrors = 30;

        for (int i = 1; i < args.length; i++) {
            if ("--max-errors".equals(args[i]) && i + 1 < args.length) {
                maxErrors = Integer.parseInt(args[++i]);
            } else if (target == null) {
                target = Paths.get(args[i]);
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen subsystem validate <Subsystem.xml|subsystemDir> [--max-errors N]");
        }

        Path subsystemXml = target;
        Path subsystemDir = target.getParent();

        if (!Files.exists(subsystemXml)) {
            throw new IllegalArgumentException("Subsystem XML not found: " + subsystemXml);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(subsystemXml);
            SubsystemValidator validator = new SubsystemValidator();
            List<SubsystemValidator.ValidationMessage> messages = validator.validate(doc, subsystemDir);

            if (messages.isEmpty()) {
                System.out.println("OK: Subsystem is valid");
                return;
            }

            int errors = 0, warnings = 0, shown = 0;
            for (SubsystemValidator.ValidationMessage msg : messages) {
                if ("ERROR".equals(msg.level)) errors++;
                else warnings++;
                if (shown < maxErrors) { System.out.println(msg); shown++; }
            }
            if (shown < messages.size()) {
                System.out.println("... and " + (messages.size() - shown) + " more");
            }
            System.out.println();
            System.out.println("Summary: " + errors + " errors, " + warnings + " warnings");
            if (errors > 0) System.exit(1);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse Subsystem XML: " + e.getMessage(), e);
        }
    }

    // ==================== Interface ====================

    private static void executeInterface(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Interface subcommand required: edit, validate");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "edit":
                interfaceEdit(args);
                break;
            case "validate":
                interfaceValidate(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown interface subcommand: " + args[0]);
        }
    }

    /**
     * xml-gen interface edit <CommandInterface.xml> --op <operation> --value <value>
     *
     * Operations:
     *   hide                 --value "Cmd.Name" or '["Cmd1","Cmd2"]'
     *   show                 --value "Cmd.Name" or '["Cmd1","Cmd2"]'
     *   place                --value "command=Cmd.Name group=CommandGroup.X"
     *                        or '{"command":"...","group":"CommandGroup.X"}' (legacy JSON)
     *   order / set-order    --value '{"group":"...","commands":["A","B"]}'
     *   subsystem-order / set-subsystem-order  --value '["Subsystem.X.Subsystem.A",...]'
     *   group-order / set-group-order          --value '["NavigationPanelOrdinary",...]'
     */
    private static void interfaceEdit(String[] args) {
        Path file = null;
        String operation = null;
        String value = null;

        for (int i = 1; i < args.length; i++) {
            if ("--op".equals(args[i]) && i + 1 < args.length) {
                operation = args[++i];
            } else if ("--value".equals(args[i]) && i + 1 < args.length) {
                value = args[++i];
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen interface edit <CommandInterface.xml> --op <operation> --value <value>");
        }

        try {
            InterfaceEditor editor = new InterfaceEditor(file);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

            switch (operation) {
                case "hide":
                    editor.hide(value);
                    break;
                case "show":
                    editor.show(value);
                    break;
                case "place": {
                    // Support both canonical key=value format and legacy JSON format
                    String command;
                    String group;
                    if (value.trim().startsWith("{")) {
                        com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                        command = json.get("command").asText();
                        group = json.get("group").asText();
                    } else {
                        command = parsePlaceParam(value, "command");
                        group = parsePlaceParam(value, "group");
                        if (command == null || group == null) {
                            throw new IllegalArgumentException(
                                    "place value must be 'command=<cmd> group=<grp>' or JSON {\"command\":\"...\",\"group\":\"...\"}");
                        }
                    }
                    editor.place(command, group);
                    break;
                }
                // Canonical name: set-order; legacy alias: order
                case "set-order":
                case "order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String group = json.get("group").asText();
                    com.fasterxml.jackson.databind.JsonNode cmds = json.get("commands");
                    String[] commands = new String[cmds.size()];
                    for (int j = 0; j < cmds.size(); j++) commands[j] = cmds.get(j).asText();
                    editor.setOrder(group, commands);
                    break;
                }
                // Canonical name: set-subsystem-order; legacy alias: subsystem-order
                case "set-subsystem-order":
                case "subsystem-order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String[] subs = new String[json.size()];
                    for (int j = 0; j < json.size(); j++) subs[j] = json.get(j).asText();
                    editor.setSubsystemOrder(subs);
                    break;
                }
                // Canonical name: set-group-order; legacy alias: group-order
                case "set-group-order":
                case "group-order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String[] groups = new String[json.size()];
                    for (int j = 0; j < json.size(); j++) groups[j] = json.get(j).asText();
                    editor.setGroupOrder(groups);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation
                            + ". Supported: hide, show, place, "
                            + "set-order (alias: order), "
                            + "set-subsystem-order (alias: subsystem-order), "
                            + "set-group-order (alias: group-order)");
            }

            editor.save();
            System.out.println("CommandInterface updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit CommandInterface: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a key=value pair from a string like "command=Foo.Bar group=CommandGroup.X".
     * Returns null if the key is not found.
     */
    private static String parsePlaceParam(String spec, String key) {
        // Match "key=value" where value runs until the next " key=" or end of string
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:^|\\s)" + java.util.regex.Pattern.quote(key) + "=(\\S+)");
        java.util.regex.Matcher m = p.matcher(spec);
        return m.find() ? m.group(1) : null;
    }

    /**
     * xml-gen interface validate <CommandInterface.xml> [--max-errors N]
     */
    private static void interfaceValidate(String[] args) {
        Path target = null;
        int maxErrors = 30;

        for (int i = 1; i < args.length; i++) {
            if ("--max-errors".equals(args[i]) && i + 1 < args.length) {
                maxErrors = Integer.parseInt(args[++i]);
            } else if (target == null) {
                target = Paths.get(args[i]);
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen interface validate <CommandInterface.xml> [--max-errors N]");
        }

        if (!Files.exists(target)) {
            throw new IllegalArgumentException("CommandInterface.xml not found: " + target);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(target);
            // TASK-155 A2 iter-2: pass configRoot so validator can check object existence.
            // CommandInterface.xml lives at <configRoot>/<SubsystemName>/Ext/CommandInterface.xml
            // → configRoot = target.getParent().getParent().getParent()
            Path configRoot = null;
            Path p2 = target.getParent();   // Ext/
            Path p3 = (p2 != null) ? p2.getParent() : null;  // SubsystemName/
            if (p3 != null) configRoot = p3.getParent();      // configRoot
            InterfaceValidator validator = new InterfaceValidator();
            List<InterfaceValidator.ValidationMessage> messages = validator.validate(doc, configRoot);

            if (messages.isEmpty()) {
                System.out.println("OK: CommandInterface is valid");
                return;
            }

            int errors = 0, warnings = 0, shown = 0;
            for (InterfaceValidator.ValidationMessage msg : messages) {
                if ("ERROR".equals(msg.level)) errors++;
                else warnings++;
                if (shown < maxErrors) { System.out.println(msg); shown++; }
            }
            if (shown < messages.size()) {
                System.out.println("... and " + (messages.size() - shown) + " more");
            }
            System.out.println();
            System.out.println("Summary: " + errors + " errors, " + warnings + " warnings");
            if (errors > 0) System.exit(1);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse CommandInterface XML: " + e.getMessage(), e);
        }
    }

    // ==================== Meta ====================

    private static void executeMeta(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Meta subcommand required: compile, info, validate, remove, edit");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "compile":
                metaCompile(args);
                break;
            case "info":
                metaInfo(args);
                break;
            case "validate":
                metaValidate(args);
                break;
            case "remove":
                metaRemove(args);
                break;
            case "edit":
                metaEdit(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown meta subcommand: " + args[0]
                        + ". Supported: compile, info, validate, remove, edit");
        }
    }

    /**
     * xml-gen meta compile <jsonPath> <outputDir>
     */
    private static void metaCompile(String[] args) {
        Path jsonPath = null;
        Path outputDir = null;

        for (int i = 1; i < args.length; i++) {
            if (jsonPath == null) {
                jsonPath = Paths.get(args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            }
        }

        if (jsonPath == null || outputDir == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta compile <jsonPath> <outputDir>");
        }

        if (!Files.exists(jsonPath)) {
            throw new IllegalArgumentException("JSON file not found: " + jsonPath);
        }

        try {
            new MetaWriter().compile(jsonPath, outputDir);
            System.out.println("Metadata object created in: " + outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to compile metadata: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]
     */
    private static void metaRemove(String[] args) {
        Path configDir = null;
        String objectSpec = null;
        boolean dryRun = false;
        boolean keepFiles = false;
        boolean force = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run" -> dryRun = true;
                case "--keep-files" -> keepFiles = true;
                case "--force" -> force = true;
                default -> {
                    if (configDir == null) {
                        configDir = Paths.get(args[i]);
                    } else if (objectSpec == null) {
                        objectSpec = args[i];
                    }
                }
            }
        }

        if (configDir == null || objectSpec == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta remove <configDir> <Type.Name> [--dry-run] [--keep-files] [--force]");
        }

        if (!Files.isDirectory(configDir)) {
            throw new IllegalArgumentException("Config directory not found: " + configDir);
        }

        // TASK-155 A2 iter-2: fail-fast when the target object does not exist.
        // MetaRemover silently prints [WARN] and exits 0 for missing objects; we need exit=1.
        // Check object existence before delegating to remover.
        String[] objParts = objectSpec.split("\\.", 2);
        if (objParts.length == 2) {
            io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor td =
                io.github.onec.xmlgen.model.MetadataTypeRegistry.get(objParts[0]);
            if (td != null) {
                Path typeDir = configDir.resolve(td.directory());
                Path objXml = typeDir.resolve(objParts[1] + ".xml");
                Path objSubDir = typeDir.resolve(objParts[1]);
                if (!Files.exists(objXml) && !Files.isDirectory(objSubDir)) {
                    throw new IllegalArgumentException(
                        "Object '" + objectSpec + "' not found in configuration directory: " + configDir
                        + ". Expected: " + objXml.getFileName() + " or " + objSubDir.getFileName() + "/ in "
                        + typeDir);
                }
            }
        }

        try {
            new MetaRemover().remove(configDir, objectSpec, dryRun, keepFiles, force);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove metadata: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen meta edit <objectPath> --op <operation> --value <value>
     * xml-gen meta edit <objectPath> --batch <file.json>
     */
    private static void metaEdit(String[] args) {
        Path objectPath = null;
        String operation = null;
        String value = null;
        Path batchFile = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--op", "-op" -> { if (i + 1 < args.length) operation = args[++i]; }
                case "--value", "-value", "-v" -> { if (i + 1 < args.length) value = args[++i]; }
                case "--batch" -> { if (i + 1 < args.length) batchFile = Paths.get(args[++i]); }
                default -> { if (objectPath == null && !args[i].startsWith("-")) objectPath = Paths.get(args[i]); }
            }
        }

        // ── Batch mode ──────────────────────────────────────────────────
        if (batchFile != null) {
            if (!Files.exists(batchFile)) {
                throw new IllegalArgumentException("Batch file not found: " + batchFile);
            }
            if (objectPath == null) {
                throw new IllegalArgumentException(
                        "Usage: xml-gen meta edit <objectPath> --batch <file.json>");
            }
            try {
                new MetaEditor().applyBatch(objectPath, batchFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to apply batch: " + e.getMessage(), e);
            }
            return;
        }

        // ── Inline mode ─────────────────────────────────────────────────
        if (objectPath == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta edit <objectPath> --op <operation> --value <value>\n"
                    + "       xml-gen meta edit <objectPath> --batch <file.json>\n"
                    + "Operations: add-attribute, add-ts, add-dimension, add-resource, add-enumValue,\n"
                    + "  add-predefined (--value \"Имя[|Описание[|Код[|folder]]]\", батч через ;;),\n"
                    + "  add-column, add-form, add-template, add-command, add-ts-attribute,\n"
                    + "  remove-attribute, remove-ts, remove-dimension, ..., remove-ts-attribute,\n"
                    + "  modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column");
        }

        // TASK-155 A2 iter-2: fail-fast on unknown --op value.
        // MetaEditor.edit() splits operation by "-" into action+target and silently warns
        // for unknown actions; validate the format upfront to give exit=1 + ERROR.
        if (!operation.contains("-")) {
            throw new IllegalArgumentException(
                "Invalid --op value: \"" + operation + "\". Expected format: verb-target "
                + "(e.g. add-attribute, remove-ts, modify-dimension). "
                + "Supported verbs: add, remove, modify.");
        }
        String[] opParts = operation.split("-", 2);
        String opAction = opParts[0];
        if (!opAction.equals("add") && !opAction.equals("remove") && !opAction.equals("modify")) {
            throw new IllegalArgumentException(
                "Unknown --op value: \"" + operation + "\". "
                + "Supported operations: add-attribute, add-ts, add-dimension, add-resource, "
                + "add-enumValue, add-column, add-form, add-template, add-command, add-ts-attribute, "
                + "remove-attribute, remove-ts, remove-dimension, remove-resource, remove-enumValue, "
                + "remove-column, remove-form, remove-template, remove-command, remove-ts-attribute, "
                + "modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column.");
        }

        try {
            new MetaEditor().edit(objectPath, operation, value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit metadata: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen meta info <ObjectXml> [--mode brief|overview|full]
     */
    private static void metaInfo(String[] args) {
        Path file = null;
        String mode = "overview";

        for (int i = 1; i < args.length; i++) {
            if ("--mode".equals(args[i]) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta info <Object.xml> [--mode brief|overview|full]");
        }

        if (!Files.exists(file)) {
            throw new IllegalArgumentException("Object XML not found: " + file);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            // TASK-155 A2 iter-2: root-element guard — reject non-meta XML before printing.
            // Meta objects use <MetaDataObject> as the root wrapper element.
            String rootEl = doc.getRootElement();
            if (!"MetaDataObject".equals(rootEl)) {
                throw new IllegalArgumentException(
                    "Expected root <MetaDataObject> for a 1C metadata object XML, got <" + rootEl + ">. " +
                    "The file does not appear to be a 1C metadata object descriptor.");
            }
            new MetaInfoPrinter().print(doc, mode, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse metadata XML: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen meta validate <ObjectXml> [--max-errors N]
     */
    private static void metaValidate(String[] args) {
        Path target = null;
        int maxErrors = 30;

        for (int i = 1; i < args.length; i++) {
            if ("--max-errors".equals(args[i]) && i + 1 < args.length) {
                maxErrors = Integer.parseInt(args[++i]);
            } else if (target == null) {
                target = Paths.get(args[i]);
            }
        }

        if (target == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta validate <Object.xml> [--max-errors N]");
        }

        if (!Files.exists(target)) {
            throw new IllegalArgumentException("Object XML not found: " + target);
        }

        Path objectDir = target.getParent();

        try {
            XmlDocument doc = new XmlStructureReader().parse(target);
            MetaValidator validator = new MetaValidator();
            List<MetaValidator.ValidationMessage> messages = validator.validate(doc, objectDir);

            if (messages.isEmpty()) {
                System.out.println("OK: metadata object is valid");
                return;
            }

            int errors = 0, warnings = 0, shown = 0;
            for (MetaValidator.ValidationMessage msg : messages) {
                if ("ERROR".equals(msg.level)) errors++;
                else warnings++;
                if (shown < maxErrors) { System.out.println(msg); shown++; }
            }
            if (shown < messages.size()) {
                System.out.println("... and " + (messages.size() - shown) + " more");
            }
            System.out.println();
            System.out.println("Summary: " + errors + " errors, " + warnings + " warnings");
            if (errors > 0) System.exit(1);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse metadata XML: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // EXTENSION domain
    // ═══════════════════════════════════════════════════════════════════

    private static void executeExtension(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException(
                    "Extension subcommand required: init, validate, borrow, diff, patch-method");
        }

        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "init":
                extensionInit(args);
                break;
            case "validate":
                extensionValidate(args);
                break;
            case "borrow":
                extensionBorrow(args);
                break;
            case "diff":
                extensionDiff(args);
                break;
            case "patch-method":
                extensionPatchMethod(args);
                break;
            default:
                throw new IllegalArgumentException("Unknown extension subcommand: " + args[0]
                        + ". Supported: init, validate, borrow, diff, patch-method");
        }
    }

    /**
     * xml-gen extension init <outputDir> <name>
     *     [--synonym <syn>] [--prefix <pfx>] [--purpose <P>]
     *     [--compat <V>] [--version <ver>] [--vendor <vnd>]
     *     [--config-path <path>] [--no-role]
     */
    private static void extensionInit(String[] args) {
        Path outputDir = null;
        String name = null;
        String synonym = null;
        String prefix = null;
        String purpose = null;
        String compat = null;
        String version = null;
        String vendor = null;
        Path configPath = null;
        boolean noRole = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--synonym" -> { if (i + 1 < args.length) synonym = args[++i]; }
                case "--prefix" -> { if (i + 1 < args.length) prefix = args[++i]; }
                case "--purpose" -> { if (i + 1 < args.length) purpose = args[++i]; }
                case "--compat" -> { if (i + 1 < args.length) compat = args[++i]; }
                case "--version" -> { if (i + 1 < args.length) version = args[++i]; }
                case "--vendor" -> { if (i + 1 < args.length) vendor = args[++i]; }
                case "--config-path" -> { if (i + 1 < args.length) configPath = Paths.get(args[++i]); }
                case "--no-role" -> noRole = true;
                default -> {
                    if (outputDir == null) outputDir = Paths.get(args[i]);
                    else if (name == null) name = args[i];
                }
            }
        }

        if (outputDir == null || name == null) {
            throw new IllegalArgumentException("Usage: xml-gen extension init <outputDir> <name> [options]");
        }

        // TASK-155 A2 iter-2: validate that the extension name matches the 1C identifier rules.
        // 1C metadata names must match [A-Za-z_][A-Za-z0-9_]* (Latin/Cyrillic letters,
        // digits, underscore; no spaces or special characters).
        if (!name.matches("[A-Za-z_А-ЯЁа-яё][A-Za-z0-9_А-ЯЁа-яё]*")) {
            throw new IllegalArgumentException(
                "Invalid extension name: \"" + name + "\". "
                + "Extension name must match [A-Za-z_][A-Za-z0-9_]* "
                + "(letters, digits, underscore only; no spaces or special characters).");
        }

        try {
            new ExtensionWriter().create(outputDir, name, synonym, prefix, purpose,
                    compat, version, vendor, configPath, noRole);
        } catch (IOException e) {
            throw new RuntimeException("Extension init failed: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen extension validate <extensionPath> [--max-errors N]
     */
    private static void extensionValidate(String[] args) {
        Path extPath = null;
        int maxErrors = 30;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--max-errors" -> { if (i + 1 < args.length) maxErrors = Integer.parseInt(args[++i]); }
                default -> { if (extPath == null) extPath = Paths.get(args[i]); }
            }
        }

        if (extPath == null) {
            throw new IllegalArgumentException("Usage: xml-gen extension validate <extensionPath>");
        }

        // Resolve to Configuration.xml
        Path cfgFile = extPath;
        if (Files.isDirectory(cfgFile)) {
            cfgFile = cfgFile.resolve("Configuration.xml");
        }
        if (!Files.isRegularFile(cfgFile)) {
            throw new IllegalArgumentException("Configuration.xml not found: " + cfgFile);
        }
        Path extDir = cfgFile.getParent();

        try {
            XmlDocument document = new XmlStructureReader().parse(cfgFile);
            ExtensionValidator validator = new ExtensionValidator();
            var result = validator.validate(document, extDir);

            // Output
            int errors = 0;
            int warnings = 0;
            int shown = 0;
            for (var msg : result) {
                if ("ERROR".equals(msg.level)) errors++;
                else warnings++;
                if (shown < maxErrors || "ERROR".equals(msg.level)) {
                    System.out.println(msg);
                    shown++;
                }
            }
            if (result.size() > shown) {
                System.out.println("... and " + (result.size() - shown) + " more");
            }
            System.out.println();
            System.out.println("Summary: " + errors + " errors, " + warnings + " warnings");
            if (errors > 0) System.exit(1);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse extension XML: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen extension borrow {@literal <extensionPath> <configPath> <objectSpec>}
     *     [--borrow-main-attribute form|all]
     */
    private static void extensionBorrow(String[] args) {
        Path extPath = null;
        Path configPath = null;
        String objectSpec = null;
        ExtensionEditor.MainAttributeMode mainAttrMode = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--borrow-main-attribute".equals(a) && i + 1 < args.length) {
                String v = args[++i].trim().toLowerCase();
                switch (v) {
                    case "form" -> mainAttrMode = ExtensionEditor.MainAttributeMode.FORM;
                    case "all" -> mainAttrMode = ExtensionEditor.MainAttributeMode.ALL;
                    default -> throw new IllegalArgumentException(
                            "--borrow-main-attribute requires 'form' or 'all', got: " + v);
                }
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option: " + a);
            } else if (extPath == null) {
                extPath = Paths.get(a);
            } else if (configPath == null) {
                configPath = Paths.get(a);
            } else if (objectSpec == null) {
                objectSpec = a;
            } else {
                objectSpec = objectSpec + " ;; " + a;
            }
        }

        if (extPath == null || configPath == null || objectSpec == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen extension borrow <extensionPath> <configPath> <objectSpec> "
                            + "[--borrow-main-attribute form|all]");
        }

        try {
            new ExtensionEditor().borrow(extPath, configPath, objectSpec, mainAttrMode);
        } catch (IOException e) {
            throw new RuntimeException("Extension borrow failed: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen extension patch-method {@literal <extensionPath>}
     *     --module "{@literal <Type.Name[.Sub]>}"
     *     --method "{@literal <MethodName>}"
     *     --type Before|After|Instead|ModificationAndControl
     *     [--config {@literal <baseConfigPath>}]
     *     [--context {@literal <bsl-context>}]
     *     [--function]
     */
    private static void extensionPatchMethod(String[] args) {
        Path extPath = null;
        String modulePath = null;
        String methodName = null;
        String typeStr = null;
        Path configPath = null;
        String context = null;
        boolean asFunction = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--module" -> { if (i + 1 < args.length) modulePath = args[++i]; }
                case "--method" -> { if (i + 1 < args.length) methodName = args[++i]; }
                case "--type" -> { if (i + 1 < args.length) typeStr = args[++i]; }
                case "--config" -> { if (i + 1 < args.length) configPath = Paths.get(args[++i]); }
                case "--context" -> { if (i + 1 < args.length) context = args[++i]; }
                case "--function" -> asFunction = true;
                default -> {
                    if (a.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + a);
                    }
                    if (extPath == null) extPath = Paths.get(a);
                }
            }
        }

        if (extPath == null || modulePath == null || methodName == null || typeStr == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen extension patch-method <extensionPath> "
                            + "--module <Type.Name.ObjectModule|Type.Name.Form.Y|CommonModule.X> "
                            + "--method <MethodName> "
                            + "--type Before|After|Instead|ModificationAndControl "
                            + "[--config <baseConfigPath>] [--context <bsl-context>] [--function]");
        }

        ExtensionEditor.InterceptorType type = ExtensionEditor.InterceptorType.parse(typeStr);
        try {
            ExtensionEditor.PatchMethodResult r = new ExtensionEditor().patchMethod(
                    extPath, modulePath, methodName, type, configPath, context, asFunction);
            if (r.skipped) {
                System.out.println("Skipped (already present): " + r.procedureName + " in " + r.bslFile);
            } else if (r.created) {
                System.out.println("Created " + r.bslFile + " with " + r.procedureName);
            } else {
                System.out.println("Appended " + r.procedureName + " to " + r.bslFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Extension patch-method failed: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
     */
    private static void extensionDiff(String[] args) {
        Path extPath = null;
        Path configPath = null;
        String mode = "A";

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--mode" -> { if (i + 1 < args.length) mode = args[++i].toUpperCase(); }
                default -> {
                    if (extPath == null) extPath = Paths.get(args[i]);
                    else if (configPath == null) configPath = Paths.get(args[i]);
                }
            }
        }

        if (extPath == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen extension diff <extensionPath> <configPath> [--mode A|B]");
        }
        // configPath can be null for mode A (though B requires it)
        if (configPath == null && "B".equals(mode)) {
            throw new IllegalArgumentException("Config path required for Mode B");
        }

        // TASK-155 A2 iter-2: fail-fast when the config path is provided but does not exist.
        // ExtensionDiffPrinter silently ignores a missing configPath in Mode A → exit=0.
        if (configPath != null && !Files.exists(configPath)) {
            throw new IllegalArgumentException(
                "Config path not found: " + configPath
                + ". The specified base configuration directory or file does not exist.");
        }

        try {
            new ExtensionDiffPrinter().diff(extPath, configPath, mode);
        } catch (IOException e) {
            throw new RuntimeException("Extension diff failed: " + e.getMessage(), e);
        }
    }

    // ── edit ──────────────────────────────────────────────────────────────

    private static void executeEdit(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Edit subcommand required: replace-text");
        }
        String subcommand = args[0].toLowerCase();
        switch (subcommand) {
            case "replace-text":
                editReplaceText(args);
                break;
            default:
                throw new IllegalArgumentException(
                        "Unknown edit subcommand: " + args[0] + ". Supported: replace-text");
        }
    }

    /**
     * xml-gen edit replace-text {@literal <file>} --old "old" --new "new" [--all] [--dry-run]
     *         [--backup] [--validate] [--encoding utf-8-sig|utf-8]
     */
    private static void editReplaceText(String[] args) {
        List<String> oldTexts = new ArrayList<>();
        List<String> newTexts = new ArrayList<>();
        boolean replaceAll = false;
        boolean dryRun = false;
        boolean backup = false;
        boolean validate = false;
        String encoding = "utf-8-sig";
        Path file = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                // TASK-171 D-7: --search/--replace — алиасы к --old/--new (эргономика).
                case "--old", "--search" -> {
                    if (i + 1 < args.length) oldTexts.add(args[++i]);
                    else throw new IllegalArgumentException(args[i] + " requires a value");
                }
                case "--new", "--replace" -> {
                    if (i + 1 < args.length) newTexts.add(args[++i]);
                    else throw new IllegalArgumentException(args[i] + " requires a value");
                }
                case "--all" -> replaceAll = true;
                case "--dry-run" -> dryRun = true;
                case "--backup" -> backup = true;
                case "--validate" -> validate = true;
                case "--encoding" -> {
                    if (i + 1 < args.length) encoding = args[++i].toLowerCase();
                    else throw new IllegalArgumentException("--encoding requires a value");
                }
                default -> {
                    if (!args[i].startsWith("--")) {
                        file = Paths.get(args[i]);
                    } else {
                        throw new IllegalArgumentException("Unknown option: " + args[i]);
                    }
                }
            }
        }

        if (oldTexts.isEmpty()) {
            throw new IllegalArgumentException("--old is required");
        }
        if (oldTexts.size() != newTexts.size()) {
            throw new IllegalArgumentException(
                    "Each --old must have a matching --new (got "
                            + oldTexts.size() + " old, " + newTexts.size() + " new)");
        }
        if (file == null) {
            throw new IllegalArgumentException("File argument required");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File not found: " + file);
        }

        List<ReplaceTextEditor.Replacement> pairs = new ArrayList<>();
        for (int i = 0; i < oldTexts.size(); i++) {
            pairs.add(new ReplaceTextEditor.Replacement(oldTexts.get(i), newTexts.get(i)));
        }

        try {
            ReplaceTextEditor editor = new ReplaceTextEditor();
            ReplaceTextEditor.Result result = editor.execute(
                    file, pairs, replaceAll, encoding, dryRun, backup, validate);

            // Dry-run: show replacement info to stderr
            if (dryRun && result.replacements() > 0) {
                System.err.println("[DRY-RUN] Would replace " + result.replacements()
                        + " occurrence(s) in " + result.file());
            }

            // JSON output to stdout
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("file", result.file().toString());
            json.put("replacements", result.replacements());
            json.put("bytes_before", result.bytesBefore());
            json.put("bytes_after", result.bytesAfter());
            if (dryRun) {
                json.put("dry_run", true);
            }
            ObjectMapper mapper = new ObjectMapper();
            System.out.println(mapper.writeValueAsString(json));

            if (result.replacements() == 0) {
                System.err.println("Text not found in " + file);
                System.exit(1);
            }
        } catch (IOException e) {
            throw new RuntimeException("Replace-text failed: " + e.getMessage(), e);
        }
    }

    //++agent TASK-155 [22.05.2026 00:00:00]
    /**
     * TASK-155 A2 iter-3: helper for checking if a named UI element exists anywhere in the subtree.
     * Mirrors FormEditor.findElementRecursive — checks every node's "name" attribute recursively.
     * Used by form remove-element to fail fast on missing elements (bug-T-154-form-002 obs #3).
     */
    private static boolean findElementByNameRecursive(XmlNode root, String name) {
        if (name.equals(root.attr("name"))) {
            return true;
        }
        for (XmlNode child : root.getChildren()) {
            if (findElementByNameRecursive(child, name)) {
                return true;
            }
        }
        return false;
    }
    //++agent TASK-155
}

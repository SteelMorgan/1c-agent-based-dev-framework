package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Диспетчер команд CLI.
 */
public class Commands {
    
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
            throw new IllegalArgumentException("EPF subcommand required: init [--type report], add-form, add-template, add-attribute, add-tabular-section");
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
            default:
                throw new IllegalArgumentException("Unknown EPF subcommand: " + subcommand);
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
                 editor.addAttribute(
                     getArg(args, "--name", true),
                     getArg(args, "--type", false),
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
            throw new IllegalArgumentException("Form subcommand required: info, add, remove, compile, add-attribute, add-element, add-command, remove-element, move-element");
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
                System.out.println("Form '" + formName + "' not found in ChildObjects");
                return;
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
                 editor.addAttribute(getArg(args, "--name", true), getArg(args, "--type", true));
            } else if ("add-element".equals(cmd)) {
                 editor.addElement(
                     getArg(args, "--type", true),
                     getArg(args, "--name", true),
                     getArg(args, "--path", false),
                     getArg(args, "--parent", false),
                     getArg(args, "--after", false)
                 );
            } else if ("add-command".equals(cmd)) {
                 editor.addCommand(
                     getArg(args, "--name", true),
                     getArg(args, "--title", false),
                     getArg(args, "--action", false)
                 );
            } else if ("remove-element".equals(cmd)) {
                 editor.removeElement(getArg(args, "--name", true));
            } else if ("move-element".equals(cmd)) {
                 editor.moveElement(
                     getArg(args, "--name", true),
                     getArg(args, "--after", false),
                     getArg(args, "--before", false),
                     getArg(args, "--into", false)
                 );
            }
            saveAndValidate(doc, file, "form", args);
        } catch (Exception e) {
            throw new RuntimeException("Form editor failed: " + e.getMessage(), e);
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
            throw new IllegalArgumentException("Template subcommand required: add, remove");
        }

        String subcommand = args[0].toLowerCase();
        if ("add".equals(subcommand)) {
            templateAdd(args);
        } else if ("remove".equals(subcommand)) {
            templateRemove(args);
        } else {
            throw new IllegalArgumentException("Unknown Template subcommand: " + subcommand);
        }
    }

    /**
     * xml-gen template add <objectXml> <templateName> --type <type> [--synonym <syn>]
     */
    private static void templateAdd(String[] args) {
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
            throw new IllegalArgumentException("Usage: xml-gen template add <objectXml> <templateName> [--type <type>]");
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
     * xml-gen template remove <objectXml> <templateName>
     */
    private static void templateRemove(String[] args) {
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
            throw new IllegalArgumentException("Usage: xml-gen template remove <objectXml> <templateName>");
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
            throw new IllegalArgumentException("SKD subcommand required: info, compile, add-field, add-parameter");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            skdInfo(args);
        } else if ("compile".equals(subcommand.toLowerCase())) {
            skdCompile(args);
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
            } else if ("--limit".equals(args[i]) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--offset".equals(args[i]) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("SKD XML file is required: xml-gen skd info <file.xml>");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            new SkdInfoPrinter().print(doc, mode, name, limit, offset, System.out);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse SKD XML: " + e.getMessage(), e);
        }
    }

    private static void skdCompile(String[] args) {
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
            SkdDsl dsl = mapper.readValue(inputJson.toFile(), SkdDsl.class);
            SkdWriter writer = new SkdWriter(format);
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
                 editor.addParameter(
                     getArg(args, "--name", true),
                     getArg(args, "--title", false),
                     getArg(args, "--type", false)
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
        // Validate in-memory before writing
        MetadataTypeValidator metadataValidator = createMetadataValidator(args);
        GenValidator genValidator = new GenValidator(metadataValidator);
        
        List<ValidationIssue> issues = new ArrayList<>();
        
        // 1. Run general validation (including type checks)
        // Assume default format "designer" for now if not specified in args logic, 
        // but here we just need to know if we expect BOM. 
        // In "edit" commands we usually preserve format, but for validation we can be strict.
        // Let's assume designer format (expect BOM) for metadata files.
        boolean expectBom = isMetadataFile(type);
        issues.addAll(genValidator.validate(doc, type, expectBom));

        // 2. Run specific validator
        issues.addAll(new ValidatorFactory().getValidator(type)
                .map(v -> v.validate(doc, ValidationLevel.SEMANTIC))
                .orElse(List.of()));
                
        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == Severity.ERROR);
        
        if (hasErrors) {
            System.err.println("Validation failed after modification:");
            ValidationResult result = new ValidationResult(file, type, "designer", issues);
            System.err.println(new TextReporter().format(result));
            System.exit(1);
        }
        
        // Atomic save: write to temp file, then move (as per SPEC-004 rollback protocol)
        Path tmpFile = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            new XmlDocumentWriter().write(doc, tmpFile);
            Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            // Cleanup temp file on failure
            try { Files.deleteIfExists(tmpFile); } catch (Exception ignored) {}
            throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
        }
        System.out.println("Modified " + file);
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
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            } else if (name == null) {
                name = args[i];
            }
        }

        if (outputDir == null || name == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen config init <outputDir> <name> [--synonym <syn>] [--version <ver>] [--vendor <vendor>]");
        }

        try {
            new ConfigWriter().create(outputDir, name, synonym, version, vendor, langName, langCode);
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
     *   hide              --value "Cmd.Name" or '["Cmd1","Cmd2"]'
     *   show              --value "Cmd.Name" or '["Cmd1","Cmd2"]'
     *   place             --value '{"command":"...","group":"CommandGroup.X"}'
     *   order             --value '{"group":"...","commands":["A","B"]}'
     *   subsystem-order   --value '["Subsystem.X.Subsystem.A",...]'
     *   group-order       --value '["NavigationPanelOrdinary",...]'
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
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String command = json.get("command").asText();
                    String group = json.get("group").asText();
                    editor.place(command, group);
                    break;
                }
                case "order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String group = json.get("group").asText();
                    com.fasterxml.jackson.databind.JsonNode cmds = json.get("commands");
                    String[] commands = new String[cmds.size()];
                    for (int j = 0; j < cmds.size(); j++) commands[j] = cmds.get(j).asText();
                    editor.setOrder(group, commands);
                    break;
                }
                case "subsystem-order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String[] subs = new String[json.size()];
                    for (int j = 0; j < json.size(); j++) subs[j] = json.get(j).asText();
                    editor.setSubsystemOrder(subs);
                    break;
                }
                case "group-order": {
                    com.fasterxml.jackson.databind.JsonNode json = mapper.readTree(value);
                    String[] groups = new String[json.size()];
                    for (int j = 0; j < json.size(); j++) groups[j] = json.get(j).asText();
                    editor.setGroupOrder(groups);
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown operation: " + operation
                            + ". Supported: hide, show, place, order, subsystem-order, group-order");
            }

            editor.save();
            System.out.println("CommandInterface updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit CommandInterface: " + e.getMessage(), e);
        }
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
            InterfaceValidator validator = new InterfaceValidator();
            List<InterfaceValidator.ValidationMessage> messages = validator.validate(doc);

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

        try {
            new MetaRemover().remove(configDir, objectSpec, dryRun, keepFiles, force);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove metadata: " + e.getMessage(), e);
        }
    }

    /**
     * xml-gen meta edit <objectPath> --op <operation> --value <value>
     */
    private static void metaEdit(String[] args) {
        Path objectPath = null;
        String operation = null;
        String value = null;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--op", "-op" -> { if (i + 1 < args.length) operation = args[++i]; }
                case "--value", "-value", "-v" -> { if (i + 1 < args.length) value = args[++i]; }
                default -> { if (objectPath == null) objectPath = Paths.get(args[i]); }
            }
        }

        if (objectPath == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta edit <objectPath> --op <operation> --value <value>\n"
                    + "Operations: add-attribute, add-ts, add-dimension, add-resource, add-enumValue,\n"
                    + "  add-column, add-form, add-template, add-command, add-ts-attribute,\n"
                    + "  remove-attribute, remove-ts, remove-dimension, ..., remove-ts-attribute,\n"
                    + "  modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column");
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
            throw new IllegalArgumentException("Extension subcommand required: init, validate, borrow, diff");
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
            default:
                throw new IllegalArgumentException("Unknown extension subcommand: " + args[0]
                        + ". Supported: init, validate, borrow, diff");
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
     * xml-gen extension borrow <extensionPath> <configPath> <objectSpec>
     */
    private static void extensionBorrow(String[] args) {
        Path extPath = null;
        Path configPath = null;
        String objectSpec = null;

        for (int i = 1; i < args.length; i++) {
            if (extPath == null) extPath = Paths.get(args[i]);
            else if (configPath == null) configPath = Paths.get(args[i]);
            else if (objectSpec == null) objectSpec = args[i];
            else objectSpec = objectSpec + " ;; " + args[i]; // allow multiple positional args
        }

        if (extPath == null || configPath == null || objectSpec == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen extension borrow <extensionPath> <configPath> <objectSpec>");
        }

        try {
            new ExtensionEditor().borrow(extPath, configPath, objectSpec);
        } catch (IOException e) {
            throw new RuntimeException("Extension borrow failed: " + e.getMessage(), e);
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
                case "--old" -> {
                    if (i + 1 < args.length) oldTexts.add(args[++i]);
                    else throw new IllegalArgumentException("--old requires a value");
                }
                case "--new" -> {
                    if (i + 1 < args.length) newTexts.add(args[++i]);
                    else throw new IllegalArgumentException("--new requires a value");
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
}

package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.editor.*;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.validator.*;
import io.github.onec.xmlgen.validator.report.JsonReporter;
import io.github.onec.xmlgen.validator.report.TextReporter;
import io.github.onec.xmlgen.writer.EpfWriter;
import io.github.onec.xmlgen.writer.FormWriter;
import io.github.onec.xmlgen.writer.MxlWriter;
import io.github.onec.xmlgen.writer.RoleWriter;
import io.github.onec.xmlgen.writer.SkdWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
            throw new IllegalArgumentException("EPF subcommand required: init, add-form, add-template, add-attribute, add-tabular-section");
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
        // Парсинг аргументов: --format <designer|edt> --name <Name> <output_dir>
        OutputFormat format = OutputFormat.DESIGNER;
        String name = null;
        String synonym = null;
        Path outputDir = null;
        
        for (int i = 1; i < args.length; i++) {
            if ("--format".equals(args[i]) && i + 1 < args.length) {
                format = OutputFormat.fromString(args[++i]);
            } else if ("--name".equals(args[i]) && i + 1 < args.length) {
                name = args[++i];
            } else if ("--synonym".equals(args[i]) && i + 1 < args.length) {
                synonym = args[++i];
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
            EpfWriter writer = new EpfWriter(format);
            writer.init(name, synonym, outputDir);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create EPF: " + e.getMessage(), e);
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
            saveAndValidate(doc, file, "epf");
        } catch (Exception e) {
            throw new RuntimeException("EPF editor failed: " + e.getMessage(), e);
        }
    }

    private static void executeForm(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Form subcommand required: compile, add-attribute, add-element, add-command, remove-element, move-element");
        }
        
        String subcommand = args[0];
        if ("compile".equals(subcommand.toLowerCase())) {
            formCompile(args);
        } else if (subcommand.startsWith("add-") || subcommand.endsWith("-element")) {
            formEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown Form subcommand: " + subcommand);
        }
    }
    
    private static void formCompile(String[] args) {
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
            saveAndValidate(doc, file, "form");
        } catch (Exception e) {
            throw new RuntimeException("Form editor failed: " + e.getMessage(), e);
        }
    }

    private static void executeRole(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Role subcommand required: compile, add-object, add-right");
        }
        
        String subcommand = args[0];
        if ("compile".equals(subcommand.toLowerCase())) {
            roleCompile(args);
        } else if (subcommand.startsWith("add-")) {
            roleEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown Role subcommand: " + subcommand);
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
            saveAndValidate(doc, file, "role");
        } catch (Exception e) {
            throw new RuntimeException("Role editor failed: " + e.getMessage(), e);
        }
    }

    private static void executeMxl(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("MXL subcommand required: compile");
        }
        
        String subcommand = args[0];
        if ("compile".equals(subcommand.toLowerCase())) {
            mxlCompile(args);
        } else {
            throw new IllegalArgumentException("Unknown MXL subcommand: " + subcommand);
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

    private static void executeSkd(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("SKD subcommand required: compile, add-field, add-parameter");
        }
        
        String subcommand = args[0];
        if ("compile".equals(subcommand.toLowerCase())) {
            skdCompile(args);
        } else if (subcommand.startsWith("add-")) {
            skdEdit(args);
        } else {
            throw new IllegalArgumentException("Unknown SKD subcommand: " + subcommand);
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
            saveAndValidate(doc, file, "skd");
        } catch (Exception e) {
            throw new RuntimeException("SKD editor failed: " + e.getMessage(), e);
        }
    }

    // ============================================================
    // validate command
    // ============================================================

    private static void executeValidate(String[] args) {
        // Парсинг: [--type <form|role|skd|mxl|epf>] [--format <designer|edt>]
        //          [--level <structure|semantic>] [--output <text|json>] <file1> [file2] ...
        String type = null;
        String formatStr = "designer";
        ValidationLevel level = ValidationLevel.SEMANTIC;
        String output = "text";
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            if ("--type".equals(args[i]) && i + 1 < args.length) {
                type = args[++i].toLowerCase();
            } else if ("--format".equals(args[i]) && i + 1 < args.length) {
                formatStr = args[++i].toLowerCase();
            } else if ("--level".equals(args[i]) && i + 1 < args.length) {
                String lvl = args[++i].toLowerCase();
                level = "structure".equals(lvl) ? ValidationLevel.STRUCTURE : ValidationLevel.SEMANTIC;
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                output = args[++i].toLowerCase();
            } else if (!args[i].startsWith("--")) {
                files.add(Paths.get(args[i]));
            }
        }

        if (files.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: validate [--type <form|role|skd|mxl|epf>] [--output <text|json>] <file> [files...]");
        }

        XmlStructureReader reader = new XmlStructureReader();
        ValidatorFactory factory = new ValidatorFactory();
        GenValidator genValidator = new GenValidator();
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
            default: return "unknown";
        }
    }

    private static boolean isMetadataFile(String type) {
        return "role".equals(type) || "form".equals(type) || "epf".equals(type);
    }
    
    private static String getArg(String[] args, String key, boolean required) {
        for (int i = 1; i < args.length - 1; i++) {
            if (key.equals(args[i]) && i + 1 < args.length) {
                return args[i+1];
            }
        }
        if (required) throw new IllegalArgumentException("Argument " + key + " is required");
        return null;
    }
    
    private static Path getFileArg(String[] args) {
        if (args.length < 2) throw new IllegalArgumentException("File argument required");
        Path file = Paths.get(args[args.length - 1]);
        if (!Files.exists(file)) throw new IllegalArgumentException("File not found: " + file);
        return file;
    }
    
    private static void saveAndValidate(XmlDocument doc, Path file, String type) throws Exception {
        // Validate
        List<ValidationIssue> issues = new ValidatorFactory().getValidator(type)
                .map(v -> v.validate(doc, ValidationLevel.SEMANTIC))
                .orElse(List.of());
                
        boolean hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == Severity.ERROR);
        
        if (hasErrors) {
            System.err.println("Validation failed after modification:");
            ValidationResult result = new ValidationResult(file, type, "designer", issues);
            System.err.println(new TextReporter().format(result));
            System.exit(1);
        }
        
        // Save
        new XmlDocumentWriter().write(doc, file);
        System.out.println("Modified " + file);
    }
}

package io.github.onec.xmlgen.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github._1c_syntax.bsl.mdo.support.TemplateType;
import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.dsl.FormEditDsl;
import io.github.onec.xmlgen.form.edit.BslStubWriter;
import io.github.onec.xmlgen.form.edit.FormEditApplier;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.editor.*;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.info.ConfigInfoPrinter;
import io.github.onec.xmlgen.info.FormDecompiler;
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
import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.oracle.CanonicalRuleMiner;
import io.github.onec.xmlgen.oracle.DemoOracleRunner;
import io.github.onec.xmlgen.oracle.ExchangePlanContentOracleRunner;
import io.github.onec.xmlgen.oracle.MxlOracleRunner;
import io.github.onec.xmlgen.oracle.OracleOptions;
import io.github.onec.xmlgen.oracle.PredefinedDataOracleRunner;
import io.github.onec.xmlgen.oracle.RuleMiningReport;
import io.github.onec.xmlgen.oracle.RuleMiningReportWriter;
import io.github.onec.xmlgen.support.SupportDecision;
import io.github.onec.xmlgen.support.SupportGuard;
import io.github.onec.xmlgen.support.SupportRequirement;

import io.github.onec.xmlgen.editor.ReplaceTextEditor;

//++agent TASK-155 [22.05.2026 00:00:00]
// TASK-155/TASK-174: role compile validates right names before writing Rights.xml.
//++agent TASK-155

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        "meta", "config", "extension", "subsystem", "interface", "template", "xcf-body",
        "client-interface", "platform-xsd"
    ));

    //++agent TASK-174 [05.06.2026 00:00:00]
    // XG-03: единый шаблон допустимого имени метаданных 1С — латиница ИЛИ кириллица,
    // далее буквы/цифры/подчёркивание, не начинается с цифры. Совпадает с EpfValidator.IDENT_RE
    // и с регуляркой пути meta/extension. Прежняя EPF/config-регулярка [A-Za-z_][A-Za-z0-9_]*
    // отвергала кириллицу — это была ошибочная посылка TASK-155 (bug-T-154-epf-002):
    // 1С полностью поддерживает кириллические идентификаторы (вся конфигурация GBIG PAM —
    // имена с префиксом биг_). Из-за этого `epf init --name биг_X` падал, а обход через
    // латинский плейсхолдер + edit replace-text давал структурно битый корневой XML (XG-03/XG-04).
    private static final String ONEC_NAME_PATTERN = "[A-Za-z_А-ЯЁа-яё][A-Za-z0-9_А-ЯЁа-яё]*";
    //++agent TASK-174

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
            case "oracle":
                executeOracle(args);
                break;
            case "support":
                executeSupport(args);
                break;
            case "--help":
            case "-h":
                throw new IllegalArgumentException("Use without arguments to see help");
                default:
                throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static void executeSupport(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Support subcommand required: check|info");
        }
        switch (args[0].toLowerCase()) {
            case "check" -> supportCheck(args, true);
            case "info" -> supportCheck(args, false);
            default -> throw new IllegalArgumentException("Unknown support subcommand: " + args[0]);
        }
    }

    private static void supportCheck(String[] args, boolean failOnBlocked) {
        Path target = null;
        SupportRequirement requirement = SupportRequirement.EDITABLE;
        String output = "text";

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--require".equals(a) && i + 1 < args.length) {
                requirement = SupportRequirement.fromCli(args[++i]);
            } else if ("--output".equals(a) && i + 1 < args.length) {
                output = args[++i].toLowerCase();
            } else if (target == null && !a.startsWith("--")) {
                target = Paths.get(a);
            } else {
                throw new IllegalArgumentException("Unknown option for support " + args[0] + ": " + a);
            }
        }

        if (target == null) {
            throw new IllegalArgumentException("Usage: xml-gen support " + args[0]
                    + " <path> [--require editable|removed] [--output text|json]");
        }
        if (!"text".equals(output) && !"json".equals(output)) {
            throw new IllegalArgumentException("--output must be one of: text, json");
        }

        try {
            SupportDecision decision = SupportGuard.check(target, requirement);
            if ("json".equals(output)) {
                Map<String, Object> json = new LinkedHashMap<>();
                json.put("blocked", decision.blocked());
                json.put("requirement", requirement.cliName());
                json.put("code", decision.code() == null ? null : decision.code().name());
                json.put("reason", decision.reason());
                json.put("targetPath", decision.targetPath().toString());
                json.put("configDir", decision.configDir() == null ? null : decision.configDir().toString());
                json.put("objectUuid", decision.objectUuid());
                json.put("supportRule", decision.supportRule());
                System.out.println(new ObjectMapper().writeValueAsString(json));
            } else if (decision.blocked()) {
                System.out.println("BLOCKED: " + decision.reason());
                if (decision.configDir() != null) {
                    System.out.println("Config: " + decision.configDir());
                }
                if (decision.objectUuid() != null) {
                    System.out.println("Object UUID: " + decision.objectUuid());
                }
            } else {
                System.out.println("ALLOWED");
                if (decision.configDir() != null) {
                    System.out.println("Config: " + decision.configDir());
                }
                if (decision.objectUuid() != null) {
                    System.out.println("Object UUID: " + decision.objectUuid());
                }
            }
            if (failOnBlocked && decision.blocked()) {
                throw new IllegalArgumentException(SupportGuard.diagnostic(decision, requirement));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect support state: " + e.getMessage(), e);
        }
    }

    private static void executeOracle(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Oracle subcommand required: mxl|demo|predefined-data|exchange-plan-content|mine-rules");
        }
        String subcommand = args[0].toLowerCase();
        if ("demo".equals(subcommand)) {
            executeDemoOracle(args);
            return;
        }
        if ("predefined-data".equals(subcommand)) {
            executePredefinedDataOracle(args);
            return;
        }
        if ("exchange-plan-content".equals(subcommand)) {
            executeExchangePlanContentOracle(args);
            return;
        }
        if ("mine-rules".equals(subcommand)) {
            executeMineRulesOracle(args);
            return;
        }
        if (!"mxl".equals(subcommand)) {
            throw new IllegalArgumentException("Unknown oracle subcommand: " + args[0]
                    + ". Supported: mxl, demo, predefined-data, exchange-plan-content, mine-rules");
        }

        Path source = null;
        Path out = Paths.get("build/oracle");
        String mode = "both";
        int limit = 0;
        Path allowlist = null;
        Path xgRegistry = null;
        boolean includeAll = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--source".equals(a) && i + 1 < args.length) {
                source = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            } else if ("--mode".equals(a) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--allowlist".equals(a) && i + 1 < args.length) {
                allowlist = Paths.get(args[++i]);
            } else if ("--xg-registry".equals(a) && i + 1 < args.length) {
                xgRegistry = Paths.get(args[++i]);
            } else if ("--include-all".equals(a)) {
                includeAll = true;
            } else {
                throw new IllegalArgumentException("Unknown option for oracle mxl: " + a);
            }
        }

        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }
        if (!List.of("dsl", "cli", "both").contains(mode)) {
            throw new IllegalArgumentException("--mode must be one of: dsl, cli, both");
        }

        try {
            new MxlOracleRunner().run(new OracleOptions(source, out, mode, limit, allowlist, xgRegistry, includeAll));
        } catch (Exception e) {
            throw new RuntimeException("Failed to run MXL oracle: " + e.getMessage(), e);
        }
    }

    private static void executeDemoOracle(String[] args) {
        Path source = null;
        Path out = Paths.get("build/oracle-demo");
        int limit = 0;
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        boolean includeMxl = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--source".equals(a) && i + 1 < args.length) {
                source = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--threads".equals(a) && i + 1 < args.length) {
                threads = Integer.parseInt(args[++i]);
            } else if ("--include-mxl".equals(a)) {
                includeMxl = true;
            } else {
                throw new IllegalArgumentException("Unknown option for oracle demo: " + a);
            }
        }

        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }

        try {
            new DemoOracleRunner().run(source, out, limit, threads, includeMxl);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run demo oracle: " + e.getMessage(), e);
        }
    }

    private static void executePredefinedDataOracle(String[] args) {
        Path source = null;
        Path out = Paths.get("build/oracle-predefined-data");
        int limit = 0;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--source".equals(a) && i + 1 < args.length) {
                source = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else {
                throw new IllegalArgumentException("Unknown option for oracle predefined-data: " + a);
            }
        }

        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }

        try {
            new PredefinedDataOracleRunner().run(source, out, limit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run PredefinedData oracle: " + e.getMessage(), e);
        }
    }

    private static void executeExchangePlanContentOracle(String[] args) {
        Path source = null;
        Path out = Paths.get("build/oracle-exchange-plan-content");
        int limit = 0;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--source".equals(a) && i + 1 < args.length) {
                source = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else {
                throw new IllegalArgumentException("Unknown option for oracle exchange-plan-content: " + a);
            }
        }

        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }

        try {
            new ExchangePlanContentOracleRunner().run(source, out, limit);
        } catch (Exception e) {
            throw new RuntimeException("Failed to run ExchangePlanContent oracle: " + e.getMessage(), e);
        }
    }

    private static void executeMineRulesOracle(String[] args) {
        Path source = null;
        Path out = Paths.get("build/oracle-rule-mining");
        int limit = 0;
        int minSupport = 2;
        int digestLimit = io.github.onec.xmlgen.oracle.RuleCandidateReducer.DEFAULT_DIGEST_LIMIT;
        Path disposition = null;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--source".equals(a) && i + 1 < args.length) {
                source = Paths.get(args[++i]);
            } else if ("--out".equals(a) && i + 1 < args.length) {
                out = Paths.get(args[++i]);
            } else if ("--limit".equals(a) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if ("--min-support".equals(a) && i + 1 < args.length) {
                minSupport = Integer.parseInt(args[++i]);
            } else if ("--digest-limit".equals(a) && i + 1 < args.length) {
                digestLimit = Integer.parseInt(args[++i]);
            } else if ("--disposition".equals(a) && i + 1 < args.length) {
                disposition = Paths.get(args[++i]);
            } else {
                throw new IllegalArgumentException("Unknown option for oracle mine-rules: " + a);
            }
        }

        if (source == null) {
            throw new IllegalArgumentException("--source is required");
        }

        try {
            RuleMiningReport report = new CanonicalRuleMiner().mine(source, limit, minSupport);
            io.github.onec.xmlgen.oracle.RuleDispositionRegistry registry =
                    io.github.onec.xmlgen.oracle.RuleDispositionRegistry.load(disposition);
            new RuleMiningReportWriter().write(out, report, digestLimit, registry);
            System.out.println("Rule mining report: " + out.resolve("rule-mining-report.json"));
            System.out.println("Rule digest: " + out.resolve("rule-digest.json"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to run rule mining oracle: " + e.getMessage(), e);
        }
    }

    private static void executeEpf(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("EPF subcommand required: init [--type report] [--with-skd], add-form, add-template, add-attribute, add-tabular-section, bsp-init, bsp-add-command");
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
            } else {
                throw new IllegalArgumentException("Unknown option for epf bsp-init: " + a);
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
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for epf bsp-add-command: " + a);
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
        boolean withSkd = false; //++agent TASK-171 [01.06.2026 12:00:00] флаг основной СКД для ERF //++agent TASK-171
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
            //++agent TASK-171 [01.06.2026 12:00:00]
            // --with-skd: за один шаг создать ERF с основной схемой компоновки данных.
            // Это булев флаг без значения, поэтому отдельная ветка (не "--opt <value>").
            } else if ("--with-skd".equals(args[i])) {
                withSkd = true;
            //++agent TASK-171
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for epf init: " + args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for epf init: " + args[i]);
            }
        }

        if (name == null) {
            throw new IllegalArgumentException("--name is required");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("output directory is required");
        }

        //++agent TASK-171 [01.06.2026 12:00:00]
        // Ранняя валидация: --with-skd осмыслен только для отчёта. Проверяем до создания
        // файлов, чтобы не оставить на диске половину артефакта (init без схемы).
        if (withSkd && !isReport) {
            throw new IllegalArgumentException(
                "--with-skd is only valid with --type report "
                + "(external data processors have no MainDataCompositionSchema).");
        }
        //++agent TASK-171

        //**agent TASK-174 [05.06.2026 00:00:00]
        // XG-03: имя обработки/отчёта валидируется единым ONEC_NAME_PATTERN (латиница+кириллица).
        // Прежняя латиница-только регулярка [A-Za-z_][A-Za-z0-9_]* блокировала кириллический init,
        // вынуждая обход (латинский плейсхолдер + edit replace-text), ломавший корневой XML.
        // Имя по-прежнему отвергает пробелы/спецсимволы (валидный путь Designer), но кириллицу пропускает.
        //// TASK-155 A2 iter-3: name validation for EPF init (bug-T-154-epf-002).
        //if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        //    throw new IllegalArgumentException(
        //        "Invalid 1C name: '" + name + "'. " +
        //        "EPF/ERF names must match [A-Za-z_][A-Za-z0-9_]* " +
        //        "(Latin letters, digits, and underscores only; must not start with a digit).");
        //}
        if (!name.matches(ONEC_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                "Invalid 1C name: '" + name + "'. " +
                "EPF/ERF names must match " + ONEC_NAME_PATTERN + " " +
                "(Latin or Cyrillic letters, digits, and underscores only; must not start with a digit).");
        }
        //**agent TASK-174

        try {
            EpfWriter writer = new EpfWriter(format, isReport);
            //++agent TASK-171 [01.06.2026 12:00:00]
            // С флагом --with-skd создаём ERF сразу с основной схемой компоновки данных
            // (init + add-template DataCompositionSchema + MainDataCompositionSchema) — единый
            // путь через initWithSkd, переиспользующий проверенную связку add-template (D3/D6).
            if (withSkd) {
                writer.initWithSkd(name, synonym, outputDir);
            } else {
                writer.init(name, synonym, outputDir);
            }
            //++agent TASK-171
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
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for epf add-form: " + args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for epf add-form: " + args[i]);
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
        assertValidOneCName(formName, "form");
        
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
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for epf add-template: " + args[i]);
            } else if (outputDir == null) {
                outputDir = Paths.get(args[i]);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for epf add-template: " + args[i]);
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
        assertValidOneCName(templateName, "template");
        
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
                 // Check for duplicate attribute in the canonical EPF ChildObjects section.
                 XmlNode epfRoot = doc.getRoot();
                 for (String childName : new String[]{"ExternalDataProcessor", "ExternalReport"}) {
                     XmlNode container = doc.getRoot().child(childName);
                     if (container != null) {
                         epfRoot = container;
                         break;
                     }
                 }
                 XmlNode childObjects = epfRoot.child("ChildObjects");
                 if (childObjects != null) {
                     for (XmlNode existingAttr : childObjects.children("Attribute")) {
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
            throw new IllegalArgumentException("Form subcommand required: info, decompile, add, remove, compile, edit, add-attribute, add-element, add-command, remove-element, move-element");
        }

        String subcommand = args[0];
        if ("info".equals(subcommand.toLowerCase())) {
            formInfo(args);
        } else if ("decompile".equals(subcommand.toLowerCase())) {
            formDecompile(args);
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

    private static void formDecompile(String[] args) {
        Path file = null;
        Path output = null;

        for (int i = 1; i < args.length; i++) {
            if (("--output".equals(args[i]) || "-o".equals(args[i]) || "-OutputPath".equals(args[i]))
                    && i + 1 < args.length) {
                output = Paths.get(args[++i]);
            } else if (file == null) {
                file = Paths.get(args[i]);
            } else if (output == null) {
                output = Paths.get(args[i]);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for form decompile: " + args[i]);
            }
        }

        if (file == null) {
            throw new IllegalArgumentException("Form XML file is required: xml-gen form decompile <Form.xml> [output.json]");
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(file);
            new FormDecompiler().decompile(doc, output);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse form XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to decompile form XML: " + e.getMessage(), e);
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
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for form add: " + args[i]);
            } else if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (formName == null) {
                formName = args[i];
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for form add: " + args[i]);
            }
        }

        if (objectXml == null || formName == null) {
            throw new IllegalArgumentException("Usage: xml-gen form add <objectXml> <formName> [--synonym <syn>] [--default]");
        }
        assertValidOneCName(formName, "form");
        guardMutation(objectXml);

        Path formMeta = null;
        Path formDir = null;
        boolean ownsScaffold = false;
        try {
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (editor.hasForm(formName)) {
                throw new IllegalArgumentException("Form '" + formName + "' already exists in ChildObjects");
            }

            String objectType = editor.detectObjectType();
            String objectName = editor.getObjectName();
            requireKnownObjectType(objectType, objectXml);

            // Create scaffold
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            formMeta = baseDir.resolve("Forms").resolve(formName + ".xml");
            formDir = baseDir.resolve("Forms").resolve(formName);
            if (Files.exists(formMeta) || Files.exists(formDir)) {
                throw new IllegalArgumentException("Form scaffold already exists for '" + formName + "'");
            }
            String formatVersion = ConfigurationXmlReader.readFormatVersion(objectXml);
            ownsScaffold = true;
            ObjectContainerEditor.createFormScaffold(baseDir, formName, synonym, objectType, objectName,
                    formatVersion);

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
        } catch (IOException | RuntimeException e) {
            if (ownsScaffold) {
                cleanupCreatedFormScaffold(formMeta, formDir, e);
            }
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
            if (!editor.hasForm(formName)) {
                //++agent TASK-155 [22.05.2026 00:00:00]
                // TASK-155 A2 iter-3: fail-fast on missing form (bug-T-154-form-002 obs #6).
                // Previously: print "Form not found" + exit=0 (silent no-op).
                // Now: throw → caught by outer try/catch → RuntimeException → Main catches + exit=1.
                throw new IllegalArgumentException(
                    "Form '" + formName + "' not found in ChildObjects of '" + objectXml + "'. " +
                    "Cannot remove a non-existing form.");
                //++agent TASK-155
            }

            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            Path formMeta = baseDir.resolve("Forms").resolve(formName + ".xml");
            Path formDir = baseDir.resolve("Forms").resolve(formName);
            if (Files.exists(formMeta) && !Files.isRegularFile(formMeta)) {
                throw new IOException("Expected form metadata file, got non-file path: " + formMeta);
            }
            if (Files.exists(formDir) && !Files.isDirectory(formDir)) {
                throw new IOException("Expected form directory, got non-directory path: " + formDir);
            }
            guardMutation(objectXml);
            guardMutation(formMeta, SupportRequirement.REMOVED);

            byte[] originalObjectXml = Files.readAllBytes(objectXml);
            Path backupRoot = createFormRemoveBackupRoot(baseDir);
            boolean backupCommitted = false;
            try {
                moveIfExists(formMeta, backupRoot.resolve(formName + ".xml"));
                moveIfExists(formDir, backupRoot.resolve(formName));

                editor.removeForm(formName);
                editor.clearDefaultFormIfMatches(formName);
                editor.save();
                backupCommitted = true;
            } catch (IOException e) {
                restoreFormRemoveBackup(backupRoot, formMeta, formDir, formName, e);
                try {
                    Files.write(objectXml, originalObjectXml);
                } catch (IOException restoreError) {
                    e.addSuppressed(restoreError);
                }
                throw e;
            } finally {
                if (backupCommitted) {
                    cleanupCommittedFormRemoveBackup(backupRoot);
                }
            }

            System.out.println("Removed form: " + formName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove form: " + e.getMessage(), e);
        }
    }

    private static Path createFormRemoveBackupRoot(Path baseDir) throws IOException {
        Path formsDir = baseDir.resolve("Forms");
        Files.createDirectories(formsDir);
        return Files.createTempDirectory(formsDir, ".xml-gen-remove-");
    }

    private static void moveIfExists(Path source, Path target) throws IOException {
        if (!Files.exists(source)) return;
        Files.createDirectories(target.getParent());
        try {
            Files.move(source, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void restoreFormRemoveBackup(Path backupRoot, Path formMeta, Path formDir,
                                                String formName, Exception cause) {
        try {
            moveIfExists(backupRoot.resolve(formName), formDir);
            moveIfExists(backupRoot.resolve(formName + ".xml"), formMeta);
            Files.deleteIfExists(backupRoot);
        } catch (IOException restoreError) {
            cause.addSuppressed(restoreError);
        }
    }

    private static void cleanupCommittedFormRemoveBackup(Path backupRoot) {
        try {
            deleteDirectoryTree(backupRoot);
        } catch (IOException e) {
            System.err.println("Warning: cannot delete temporary form backup " + backupRoot
                    + ": " + e.getMessage());
        }
    }

    private static void cleanupCreatedFormScaffold(Path formMeta, Path formDir, Exception cause) {
        try {
            if (formMeta != null && Files.exists(formMeta)) {
                Files.deleteIfExists(formMeta);
            }
            deleteDirectoryTree(formDir);
        } catch (IOException cleanupError) {
            cause.addSuppressed(cleanupError);
        }
    }

    private static void deleteDirectoryTree(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(dir)) {
            List<Path> toDelete = paths.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path path : toDelete) {
                Files.delete(path);
            }
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
                guardMutation(outputXml);
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
            guardMutation(outputXml);
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
            //++agent TASK-174 [05.06.2026 12:55:00]
            // Diff-gate (как в formEditJson): pre-existing ошибки валидации (включая
            // новый FORM-121 на рукописных формах без корневого Title) НЕ блокируют
            // точечную правку — блокируются только НОВЫЕ ошибки, внесённые правкой.
            Set<String> preEditErrors = snapshotErrors(doc, "form", args);
            //++agent TASK-174
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
                 //**agent TASK-174 [05.06.2026 00:00:00]
                 // XG-02: добавлен --command для привязки кнопки к команде формы через edit-путь
                 // (раньше CommandName умел только compile). Значение — короткое имя команды
                 // или полная ссылка Form.Command.X.
                 editor.addElement(
                     getArg(args, "--type", true),
                     getArg(args, "--name", true),
                     getArg(args, "--path", false),
                     getArg(args, "--parent", false),
                     getArg(args, "--after", false),
                     getArg(args, "--before", false),
                     getArg(args, "--command", false)
                 );
                 //**agent TASK-174
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
            //**agent TASK-174 [05.06.2026 12:55:00] diff-gate вместо строгого gate
            saveAndValidate(doc, file, "form", args, preEditErrors);
            //**agent TASK-174
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
            guardMutation(formFile);
            byte[] originalFormBytes = Files.readAllBytes(formFile);
            BslStubWriter bslWriter = new BslStubWriter(formFile);
            Path modulePath = bslWriter.getModulePath();
            boolean moduleExisted = modulePath != null && Files.exists(modulePath);
            byte[] originalModuleBytes = moduleExisted ? Files.readAllBytes(modulePath) : null;

            XmlDocument doc = new XmlStructureReader().parse(formFile);
            // Snapshot pre-existing errors, чтобы diff-gate не блокировался на них
            Set<String> preEditErrors = snapshotErrors(doc, "form", args);
            FormEditor editor = new FormEditor(doc);
            ObjectMapper mapper = new ObjectMapper();
            FormEditDsl spec = mapper.readValue(jsonFile.toFile(), FormEditDsl.class);
            // formFile передаётся, чтобы BslStubWriter мог найти соседний Module.bsl
            FormEditApplier applier = new FormEditApplier(editor, formFile);
            applier.apply(spec, false);
            try {
                saveAndValidate(doc, formFile, "form", args, preEditErrors);
                applier.flushBslStubs();
            } catch (Exception e) {
                restoreBytes(formFile, originalFormBytes);
                restoreOptionalBytes(modulePath, moduleExisted, originalModuleBytes);
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException("Form edit failed: " + e.getMessage(), e);
        }
    }

    private static void restoreBytes(Path file, byte[] bytes) {
        if (file == null || bytes == null) return;
        try {
            Files.write(file, bytes);
        } catch (Exception ignored) {
            // best effort rollback
        }
    }

    private static void restoreOptionalBytes(Path file, boolean existed, byte[] bytes) {
        if (file == null) return;
        try {
            if (existed) {
                Files.write(file, bytes);
            } else {
                Files.deleteIfExists(file);
            }
        } catch (Exception ignored) {
            // best effort rollback
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
                    } else if (obj.getRights() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> rightsMap = (Map<String, Object>) obj.getRights();
                        for (String right : rightsMap.keySet()) {
                            if (!isValidRoleRightName(right)) {
                                throw new IllegalArgumentException(
                                    "Invalid right name '" + right + "' for object '" + obj.getName() + "'. " +
                                    "Right names are case-sensitive XML identifiers (e.g. Read, View, Insert, " +
                                    "Update, Delete, Edit, InteractiveInsert, InteractiveDelete, " +
                                    "Posting, UndoPosting). Got: '" + right + "'.");
                            }
                        }
                    }
                    if (obj.getRls() != null) {
                        for (String right : obj.getRls().keySet()) {
                            if (!isValidRoleRightName(right)) {
                                throw new IllegalArgumentException(
                                    "Invalid RLS right name '" + right + "' for object '" + obj.getName() + "'. " +
                                    "Right names are case-sensitive XML identifiers (e.g. Read, Update, Insert, Delete).");
                            }
                        }
                    }
                }
            }
            //++agent TASK-155
            guardMutation(outputDir);
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
            return RoleDsl.isKnownRightName(name);
        } catch (Exception e) {
            // If enum not available, do not block compilation
            return true;
        }
    }
    //++agent TASK-155

    private static void assertValidOneCName(String name, String kind) {
        if (name == null || !name.matches(ONEC_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                    "Invalid 1C name for " + kind + ": '" + name + "'. "
                            + "Names must match " + ONEC_NAME_PATTERN + " "
                            + "(Latin or Cyrillic letters, digits, and underscores only; must not start with a digit).");
        }
    }

    private static void requireKnownObjectType(String objectType, Path objectXml) {
        if (objectType == null || "Unknown".equals(objectType)) {
            throw new IllegalArgumentException("Expected a supported 1C metadata object XML, got unknown object type: "
                    + objectXml);
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
                || dsl.getPage() != null
                || (dsl.getLosslessXmlBase64() != null && !dsl.getLosslessXmlBase64().isBlank());
            if (!hasContent) {
                throw new IllegalArgumentException(
                    "MXL DSL requires at least one of: areas, columns, columnWidths, page. " +
                    "Got an empty DSL object {}.");
            }
            //++agent TASK-155
            guardMutation(outputXml);
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
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for template add: " + a);
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for template add: " + a);
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
        assertValidOneCName(name, "template");

        io.github.onec.xmlgen.model.MdoPath object = io.github.onec.xmlgen.model.MdoPath.parse(objectSpec);
        try {
            guardMutation(resolveObjectXml(configDir, srcDir, object));
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
            } else if (args[i].startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for template add: " + args[i]);
            } else if (objectXml == null) {
                objectXml = Paths.get(args[i]);
            } else if (templateName == null) {
                templateName = args[i];
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for template add: " + args[i]);
            }
        }

        if (objectXml == null || templateName == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen template add --object Type.Name --name T --type TT [--synonym S] [--src dir] [--set-main-dcs] configDir\n"
                    + "  or (legacy): xml-gen template add <objectXml> <templateName> [--type <type>]");
        }
        assertValidOneCName(templateName, "template");

        try {
            guardMutation(objectXml);
            ObjectContainerEditor editor = new ObjectContainerEditor(objectXml);
            if (editor.hasTemplate(templateName)) {
                throw new IllegalArgumentException("Template '" + templateName + "' already exists in ChildObjects");
            }

            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            String formatVersion = ConfigurationXmlReader.readFormatVersion(objectXml);
            ObjectContainerEditor.createTemplateScaffold(baseDir, templateName, synonym, templateType,
                    formatVersion);

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
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for template remove: " + a);
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for template remove: " + a);
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
            Path srcPath = Paths.get(srcDir);
            Path effectiveSrc = srcPath.isAbsolute() ? srcPath : configDir.resolve(srcDir);
            Path objectXmlForCheck = effectiveSrc.resolve(object.getObjectXmlRelPath());
            guardMutation(objectXmlForCheck);
            guardMutation(effectiveSrc.resolve(object.getRelativeDir())
                    .resolve("Templates").resolve(name + ".xml"), SupportRequirement.REMOVED);
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
                throw new IllegalArgumentException("Template '" + templateName
                        + "' not found in ChildObjects of '" + objectXml + "'. Cannot remove a non-existing template.");
            }

            String objectName = editor.getObjectName();
            Path baseDir = objectXml.getParent().resolve(objectName != null ? objectName : "");
            Path tplMeta = baseDir.resolve("Templates").resolve(templateName + ".xml");
            Path tplDir = baseDir.resolve("Templates").resolve(templateName);
            guardMutation(objectXml);
            guardMutation(tplMeta, SupportRequirement.REMOVED);

            editor.save();

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
            } else if (a.startsWith("--")) {
                throw new IllegalArgumentException("Unknown option for template add-help: " + a);
            } else if (!a.startsWith("--") && configDir == null) {
                configDir = Paths.get(a);
            } else {
                throw new IllegalArgumentException("Unexpected positional argument for template add-help: " + a);
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
            guardMutation(resolveObjectXml(configDir, srcDir, object));
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
            String formatVersion = ConfigurationXmlReader.readFormatVersion(objectXml);
            ObjectContainerEditor.createHelpScaffold(baseDir, lang, formatVersion);

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
        Integer batch = null;
        Path outfile = null;
        //++agent TASK-176 [08.06.2026 12:50:00]
        // S-06 (XG-48): флаг --raw для печати запроса verbatim (lossless round-trip).
        boolean raw = false;
        //++agent TASK-176

        for (int i = 1; i < args.length; i++) {
            String option = args[i].toLowerCase(Locale.ROOT);
            if ("--raw".equals(option) || "-raw".equals(option)) { //++agent TASK-176
                raw = true;                //++agent TASK-176
            } else if (("--mode".equals(option) || "-mode".equals(option)) && i + 1 < args.length) {
                mode = args[++i].toLowerCase();
            } else if (("--name".equals(option) || "-name".equals(option)) && i + 1 < args.length) {
                name = args[++i];
            } else if (("--dataset".equals(option) || "-dataset".equals(option)) && i + 1 < args.length) {
                // --dataSet is an alias for --name when used with query/fields modes
                name = args[++i];
            } else if (("--variant".equals(option) || "-variant".equals(option)) && i + 1 < args.length) {
                // --variant is an alias for --name when used with variant mode
                name = args[++i];
            } else if (("--limit".equals(option) || "-limit".equals(option)) && i + 1 < args.length) {
                limit = Integer.parseInt(args[++i]);
            } else if (("--offset".equals(option) || "-offset".equals(option)) && i + 1 < args.length) {
                offset = Integer.parseInt(args[++i]);
            } else if (("--batch".equals(option) || "-batch".equals(option)) && i + 1 < args.length) {
                batch = Integer.parseInt(args[++i]);
            } else if (("--outfile".equals(option) || "-outfile".equals(option)) && i + 1 < args.length) {
                outfile = Paths.get(args[++i]);
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
            PrintStream out = System.out;
            if (outfile != null) {
                Path parent = outfile.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (PrintStream fileOut = new PrintStream(
                        Files.newOutputStream(outfile), true, StandardCharsets.UTF_8)) {
                    printSkdInfo(doc, mode, name, limit, offset, batch, raw, fileOut);
                }
            } else {
                printSkdInfo(doc, mode, name, limit, offset, batch, raw, out);
            }
        } catch (XmlStructureReader.XmlParseException e) {
            throw new RuntimeException("Failed to parse SKD XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write SKD info output: " + e.getMessage(), e);
        }
    }

    private static void printSkdInfo(XmlDocument doc, String mode, String name, int limit,
                                     int offset, Integer batch, boolean raw, PrintStream out) {
        // S-06 (XG-48): --raw с режимом query печатает запрос verbatim, минуя
        // пагинацию/декорации; иначе обычный режим.
        SkdInfoPrinter printer = new SkdInfoPrinter();
        if (raw && "query".equals(mode)) {
            printer.printRawQuery(doc.getRoot(), name, batch, out);
        } else {
            printer.print(doc, mode, name, limit, offset, batch, out);
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
            guardMutation(outputXml);
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
            guardMutation(schemaPath);
            // Read raw bytes once for rollback
            byte[] originalBytes = Files.readAllBytes(schemaPath);
            try {
                io.github.onec.xmlgen.validator.XmlDocument doc =
                        new io.github.onec.xmlgen.validator.XmlStructureReader().parse(schemaPath);
                io.github.onec.xmlgen.editor.SkdEditor editor =
                        new io.github.onec.xmlgen.editor.SkdEditor(doc);

                //**agent TASK-176 [08.06.2026 13:00:00]
                // S-09 (XG-46): changed-гейт. Записываем (tmp+atomic-move в saveAndValidate)
                // ТОЛЬКО при фактическом изменении — NO-OP edit не трогает байты файла.
                // saveAndValidate-инвариант не нарушен: он просто не вызывается при NO-OP;
                // rollback (внешний catch) для changed-пути сохранён. Правдивость changed
                // обеспечена аудитом OpResult (removeField-починка — предусловие гейта).
                boolean changed = applySkdOperation(editor, operation, value, dataSet, variant,
                        noSelection, schemaPath);

                if (changed) {
                    saveAndValidate(doc, schemaPath, "skd", args);
                } else {
                    System.out.println("[NO-OP] No changes detected; file not rewritten.");
                }
                //**agent TASK-176
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
    //**agent TASK-176 [08.06.2026 13:00:00]
    // S-09 (XG-46): агрегируем changed по batch-частям — гейт записи опирается на правду.
    private static boolean applySkdOperation(io.github.onec.xmlgen.editor.SkdEditor editor,
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

        boolean anyChanged = false;
        for (String spec : parts) {
            anyChanged |= applySingleSkdOp(editor, operation, spec, dataSet, variant, noSelection, schemaPath);
        }
        return anyChanged;
    }
    //**agent TASK-176

    //**agent TASK-176 [08.06.2026 13:00:00]
    // S-09 (XG-46): возвращаем агрегированный признак фактического изменения каждой операции
    // (OpResult.changed editor'а). Раньше возвраты editor.*() отбрасывались (void), из-за чего
    // вышестоящий гейт не мог отличить NO-OP от реальной правки. Правдивость OpResult по всем
    // операциям проаудирована (см. dispositions.md): единственный «лгущий unchanged» —
    // removeField selection-путь — починен (removeFromSelectionRecursive теперь сообщает
    // о мутации). Прочие операции возвращают unchanged только без мутации XML.
    private static boolean applySingleSkdOp(io.github.onec.xmlgen.editor.SkdEditor editor,
                                            String op, String spec,
                                            String dataSet, String variant, boolean noSelection,
                                            Path schemaPath) {
        switch (op) {
            case "add-field": {
                var fd = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseField(spec);
                return editor.addField(fd, dataSet, variant, noSelection).changed;
            }
            case "modify-field": {
                var fd = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseField(spec);
                return editor.modifyField(fd, dataSet).changed;
            }
            case "remove-field": {
                return editor.removeField(spec.trim(), dataSet, variant).changed;
            }
            case "set-field-role": {
                var d = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseFieldRole(spec);
                return editor.setFieldRole(d, dataSet).changed;
            }
            case "add-parameter": {
                var p = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseParameter(spec);
                return editor.addParameter(p).changed;
            }
            case "modify-parameter": {
                var p = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseModifyParameter(spec);
                return editor.modifyParameter(p).changed;
            }
            case "remove-parameter": {
                return editor.removeParameter(spec.trim()).changed;
            }
            case "rename-parameter": {
                var arrow = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseArrow(spec, false);
                return editor.renameParameter(arrow.oldText.trim(), arrow.newText.trim()).changed;
            }
            case "reorder-parameters": {
                var order = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseReorderParameters(spec);
                return editor.reorderParameters(order).changed;
            }
            case "add-total": {
                var t = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseTotal(spec);
                return editor.addTotal(t).changed;
            }
            case "remove-total": {
                return editor.removeTotal(spec.trim()).changed;
            }
            case "modify-structure": {
                var s = io.github.onec.xmlgen.editor.skd.SkdShorthandParser.parseStructureSpec(spec);
                return editor.modifyStructure(s, variant).changed;
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
                return editor.setQuery(text, dataSet).changed;
            }
            case "patch-query": {
                // patch-query batch via ;; is allowed (per skill query.md "batch supported").
                java.util.List<String> patches =
                        io.github.onec.xmlgen.editor.skd.SkdShorthandParser.splitBatch(spec);
                if (patches.isEmpty()) patches = java.util.List.of(spec);
                boolean changed = false;
                for (String p : patches) changed |= editor.patchQuery(p, dataSet).changed;
                return changed;
            }
            case "clear-conditionalAppearance": {
                if (!"*".equals(spec.trim())) {
                    throw new IllegalArgumentException(
                            "clear-conditionalAppearance: only '*' wildcard is supported");
                }
                return editor.clearConditionalAppearance(variant).changed;
            }
            default:
                throw new IllegalArgumentException("Unknown SKD edit operation: " + op);
        }
    }
    //**agent TASK-176

    // ============================================================
    // validate command
    // ============================================================

    static record ValidateOptions(String type, String format, ValidationLevel level,
                                  String output, List<Path> files, Path srcRoot) {
    }

    private static MetadataTypeValidator createMetadataValidator(String[] args) {
        String srcRootStr = getArg(args, "--src-root", false);
        Path srcRoot = srcRootStr != null ? Paths.get(srcRootStr) : null;
        return new MetadataTypeValidator(srcRoot);
    }

    private static MetadataTypeValidator createMetadataValidator(Path srcRoot) {
        return new MetadataTypeValidator(srcRoot);
    }

    private static void executeValidate(String[] args) {
        ValidateOptions options = parseValidateOptions(args);

        if (options.files().isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: validate [--type <form|role|skd|mxl|epf|client-interface|platform-xsd>] [--output <text|json>] [--src-root <path>] <file> [files...]");
        }
        if (options.srcRoot() != null && !Files.isDirectory(options.srcRoot())) {
            throw new IllegalArgumentException("--src-root does not exist or is not a directory: "
                    + options.srcRoot());
        }

        XmlStructureReader reader = new XmlStructureReader();
        ValidatorFactory factory = new ValidatorFactory();
        MetadataTypeValidator metadataValidator = createMetadataValidator(options.srcRoot());
        GenValidator genValidator = new GenValidator(metadataValidator);
        TextReporter textReporter = new TextReporter();
        JsonReporter jsonReporter = new JsonReporter();

        boolean hasErrors = false;
        boolean hasWarnings = false;

        for (Path file : options.files()) {
            XmlDocument document;
            try {
                document = reader.parse(file);
            } catch (XmlStructureReader.XmlParseException e) {
                List<ValidationIssue> parseIssues = List.of(
                        ValidationIssue.error("GEN-001", e.getMessage(), 0, "/")
                );
                ValidationResult parseResult = new ValidationResult(
                        file, options.type() != null ? options.type() : "unknown", options.format(), parseIssues);
                System.out.println("text".equals(options.output())
                        ? textReporter.format(parseResult)
                        : jsonReporter.format(parseResult));
                hasErrors = true;
                continue;
            }

            String objectType = options.type();
            if (objectType == null) {
                Optional<XmlValidator> detected = factory.detectValidator(document);
                objectType = detected.map(XmlValidator::objectType).orElse(detectTypeByRoot(document));
            }

            List<ValidationIssue> allIssues = validateDocumentForType(
                    document, objectType, file, options.format(), options.level(), genValidator, factory);

            ValidationResult result = new ValidationResult(file, objectType, options.format(), allIssues);

            if (!result.isValid()) hasErrors = true;
            if (result.warningCount() > 0) hasWarnings = true;

            System.out.println("text".equals(options.output())
                    ? textReporter.format(result)
                    : jsonReporter.format(result));
        }

        exitForValidationSummary(hasErrors ? 1 : 0, hasWarnings ? 1 : 0);
    }

    static ValidateOptions parseValidateOptions(String[] args) {
        // Парсинг: [--type <form|role|skd|mxl|epf>] [--format <designer|edt>] [--src-root <path>]
        //          [--level <structure|semantic>] [--output <text|json>] <file1> [file2] ...
        String type = null;
        String formatStr = "designer";
        ValidationLevel level = ValidationLevel.SEMANTIC;
        String output = "text";
        Path srcRoot = null;
        List<Path> files = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--type" -> {
                    String rawType = requireOptionValue(args, i, "--type").toLowerCase();
                    i++;
                    type = "erf".equals(rawType) ? "epf" : rawType;
                    if (!KNOWN_VALIDATE_TYPES.contains(rawType) && !KNOWN_VALIDATE_TYPES.contains(type)) {
                        throw new IllegalArgumentException(
                                "Unknown --type value: \"" + rawType + "\". Expected one of: "
                                        + "form, role, skd, mxl, epf, erf, meta, config, extension, "
                                        + "subsystem, interface, template, xcf-body, client-interface, platform-xsd");
                    }
                }
                case "--format" -> {
                    formatStr = requireOptionValue(args, i, "--format").toLowerCase();
                    i++;
                    if (!"designer".equals(formatStr) && !"edt".equals(formatStr)) {
                        throw new IllegalArgumentException(
                                "Unknown --format value: \"" + formatStr + "\". Expected designer or edt");
                    }
                }
                case "--level" -> {
                    String lvl = requireOptionValue(args, i, "--level").toLowerCase();
                    i++;
                    if ("structure".equals(lvl)) {
                        level = ValidationLevel.STRUCTURE;
                    } else if ("semantic".equals(lvl)) {
                        level = ValidationLevel.SEMANTIC;
                    } else {
                        throw new IllegalArgumentException(
                                "Unknown --level value: \"" + lvl + "\". Expected structure or semantic");
                    }
                }
                case "--output" -> {
                    output = requireOptionValue(args, i, "--output").toLowerCase();
                    i++;
                    if (!"text".equals(output) && !"json".equals(output)) {
                        throw new IllegalArgumentException(
                                "Unknown --output value: \"" + output + "\". Expected text or json");
                    }
                }
                case "--src-root" -> {
                    srcRoot = Paths.get(requireOptionValue(args, i, "--src-root"));
                    i++;
                }
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown validate option: " + arg);
                    }
                    files.add(Paths.get(arg));
                }
            }
        }

        return new ValidateOptions(type, formatStr, level, output, List.copyOf(files), srcRoot);
    }

    private static String requireOptionValue(String[] args, int index, String option) {
        if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
            throw new IllegalArgumentException(option + " requires a value");
        }
        return args[index + 1];
    }
    
    // --- Helpers ---

    static String detectTypeByRoot(XmlDocument doc) {
        switch (doc.getRootElement()) {
            case "Rights": return "role";
            case "Form": return "form";
            case "DataCompositionSchema": return "skd";
            case "document": return "mxl";
            case "ExternalDataProcessor": return "epf";
            case "ExternalReport": return "epf";
            case "CommandInterface": return "interface";
            case "ClientApplicationInterface": return "client-interface";
            case "Subsystem": return "subsystem";
            case "section": return "platform-xsd";
            case "MetaDataObject": return detectMetaDataObjectType(doc.getRoot());
            case "ExtPicture":
            case "ExchangePlanContent":
            case "PredefinedData":
            case "AccumulationRegisterAggregates":
            case "GraphicalSchema":
            case "AppearanceTemplate":
            case "Help":
                return "xcf-body";
            default: return "unknown";
        }
    }

    private static String detectMetaDataObjectType(XmlNode root) {
        if (root.child("ExternalDataProcessor") != null || root.child("ExternalReport") != null) {
            return "epf";
        }
        XmlNode configuration = root.child("Configuration");
        if (configuration != null) {
            XmlNode props = configuration.child("Properties");
            if (props != null
                    && (props.child("ConfigurationExtensionPurpose") != null
                    || props.child("NamePrefix") != null
                    || props.child("ConfigurationExtensionCompatibilityMode") != null)) {
                return "extension";
            }
            return "config";
        }
        if (root.child("Subsystem") != null) {
            return "subsystem";
        }
        for (XmlNode child : root.getChildren()) {
            if (MetadataTypeRegistry.byXmlElement(child.getName()) != null) {
                return "meta";
            }
        }
        return "unknown";
    }

    static int validationExitCode(int errors, int warnings) {
        if (errors > 0) return 1;
        if (warnings > 0) return 2;
        return 0;
    }

    private static void exitForValidationSummary(int errors, int warnings) {
        int exitCode = validationExitCode(errors, warnings);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    static List<ValidationIssue> validateDocumentForType(XmlDocument document, String objectType, Path file,
                                                          String formatStr, ValidationLevel level,
                                                          GenValidator genValidator,
                                                          ValidatorFactory factory) {
        String validationType = effectiveValidationType(document, objectType);
        boolean expectBom = "designer".equals(formatStr) && isMetadataFile(validationType);
        boolean validateSemanticTypes = level == ValidationLevel.SEMANTIC;
        List<ValidationIssue> allIssues = new ArrayList<>(
                genValidator.validate(document, validationType, expectBom, formatStr, validateSemanticTypes));
        allIssues.addAll(validateFormatSpecificShape(document, validationType, formatStr));

        Optional<XmlValidator> validator = factory.getValidator(validationType);
        if (validator.isPresent()) {
            allIssues.addAll(validator.get().validate(document, level));
            return allIssues;
        }

        Path contextDir = validationContextDir(objectType, file);
        switch (objectType) {
            case "config" -> allIssues.addAll(convertMessages("CONFIG",
                    new ConfigValidator().validate(document, contextDir)));
            case "meta" -> allIssues.addAll(convertMessages("META",
                    new MetaValidator().validate(document, contextDir)));
            case "template" -> allIssues.addAll(validateTemplateMetadataWrapper(document));
            case "subsystem" -> allIssues.addAll(convertMessages("SUBSYSTEM",
                    new SubsystemValidator().validate(document, contextDir, file)));
            case "interface" -> allIssues.addAll(convertMessages("INTERFACE",
                    new InterfaceValidator().validate(document, contextDir)));
            case "extension" -> allIssues.addAll(convertMessages("EXTENSION",
                    new ExtensionValidator().validate(document, contextDir)));
            default -> {
                // Unknown and schema-less types intentionally get only GEN checks.
            }
        }
        return allIssues;
    }

    private static List<ValidationIssue> validateFormatSpecificShape(XmlDocument document,
                                                                     String validationType,
                                                                     String formatStr) {
        if (!"form".equals(validationType) || !"edt".equals(formatStr)) {
            return List.of();
        }
        String ns = document.getRoot().getNamespace();
        String expected = "http://g5.1c.ru/v8/dt/form";
        if (expected.equals(ns)) {
            return List.of();
        }
        return List.of(ValidationIssue.error("GEN-005",
                "EDT managed form must use namespace '" + expected
                        + "', got '" + (ns != null ? ns : "(none)") + "'",
                document.getRoot().getLine(), "/Form"));
    }

    private static List<ValidationIssue> validateTemplateMetadataWrapper(XmlDocument document) {
        List<ValidationIssue> issues = new ArrayList<>();
        XmlNode root = document.getRoot();

        if (!"MetaDataObject".equals(root.getName())) {
            issues.add(ValidationIssue.error("TEMPLATE-001",
                    "Expected root element 'MetaDataObject' or template body XML, found '"
                            + root.getName() + "'",
                    root.getLine(), "/"));
            return issues;
        }

        String version = root.attr("version");
        if (version == null || version.isEmpty()) {
            issues.add(ValidationIssue.error("TEMPLATE-002",
                    "Structure: version attribute missing on <MetaDataObject>",
                    root.getLine(), "/MetaDataObject"));
        } else if (!"2.17".equals(version) && !"2.20".equals(version)) {
            issues.add(ValidationIssue.warning("TEMPLATE-002",
                    "Structure: unexpected version '" + version + "' (expected 2.17 or 2.20)",
                    root.getLine(), "/MetaDataObject/@version"));
        }

        XmlNode template = root.child("Template");
        if (template == null) {
            issues.add(ValidationIssue.error("TEMPLATE-003",
                    "Structure: <Template> element missing inside <MetaDataObject>",
                    root.getLine(), "/MetaDataObject"));
            return issues;
        }

        String uuid = template.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            issues.add(ValidationIssue.error("TEMPLATE-004",
                    "Structure: uuid attribute missing on <Template>",
                    template.getLine(), "/MetaDataObject/Template"));
        } else if (!uuid.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            issues.add(ValidationIssue.error("TEMPLATE-004",
                    "Structure: invalid uuid format '" + uuid + "'",
                    template.getLine(), "/MetaDataObject/Template/@uuid"));
        }

        XmlNode props = template.child("Properties");
        if (props == null) {
            issues.add(ValidationIssue.error("TEMPLATE-005",
                    "Properties: section missing",
                    template.getLine(), "/MetaDataObject/Template"));
            return issues;
        }

        String name = props.childText("Name");
        if (name == null || name.isBlank()) {
            issues.add(ValidationIssue.error("TEMPLATE-006",
                    "Properties: Name is required",
                    props.getLine(), "/MetaDataObject/Template/Properties/Name"));
        }

        String templateType = props.childText("TemplateType");
        if (templateType == null || templateType.isBlank()) {
            issues.add(ValidationIssue.error("TEMPLATE-007",
                    "Properties: TemplateType is required",
                    props.getLine(), "/MetaDataObject/Template/Properties/TemplateType"));
        } else if (!"Help".equals(templateType) && TemplateType.valueByName(templateType) == TemplateType.UNKNOWN) {
            issues.add(ValidationIssue.error("TEMPLATE-007",
                    "Properties: unknown TemplateType '" + templateType + "'",
                    props.getLine(), "/MetaDataObject/Template/Properties/TemplateType"));
        }

        if (props.child("Synonym") == null) {
            issues.add(ValidationIssue.warning("TEMPLATE-008",
                    "Properties: Synonym is missing",
                    props.getLine(), "/MetaDataObject/Template/Properties/Synonym"));
        }

        return issues;
    }

    private static String effectiveValidationType(XmlDocument document, String objectType) {
        if ("template".equals(objectType) && "document".equals(document.getRootElement())) {
            return "mxl";
        }
        if ("template".equals(objectType) && "DataCompositionSchema".equals(document.getRootElement())) {
            return "skd";
        }
        return objectType;
    }

    private static Path validationContextDir(String objectType, Path file) {
        if (file == null) return null;
        if ("subsystem".equals(objectType) || "interface".equals(objectType)) {
            Path root = locateConfigRoot(file);
            if (root != null) return root;
        }
        if ("config".equals(objectType) || "extension".equals(objectType)) {
            return Files.isDirectory(file) ? file : file.getParent();
        }
        return file.getParent();
    }

    private static List<ValidationIssue> convertMessages(String codePrefix,
                                                         List<?> messages) {
        List<ValidationIssue> issues = new ArrayList<>();
        int idx = 0;
        for (Object message : messages) {
            idx++;
            String level = readMessageField(message, "level");
            String text = readMessageField(message, "message");
            Severity severity = "ERROR".equals(level) ? Severity.ERROR : Severity.WARNING;
            issues.add(new ValidationIssue(
                    severity,
                    codePrefix + "-" + String.format("%03d", idx),
                    text,
                    0,
                    "/"));
        }
        return issues;
    }

    private static String readMessageField(Object message, String fieldName) {
        try {
            Object value = message.getClass().getField(fieldName).get(message);
            return value != null ? value.toString() : "";
        } catch (ReflectiveOperationException e) {
            return message != null ? message.toString() : "";
        }
    }

    private static boolean isMetadataFile(String type) {
        // TASK-171: добавлены skd и mxl. Все платформенные Template.xml для СКД и MXL
        // Конфигуратор пишет С UTF-8 BOM (грунт-труф: все 7 _Демо СКД и ПФ_MXL — ef bb bf).
        // Раньше skd/mxl не входили в metadata-файлы → GEN-003 их не проверял на BOM,
        // и одновременно ложно ворнил «Unexpected UTF-8 BOM» на каноничных файлах.
        return "role".equals(type) || "form".equals(type) || "epf".equals(type)
                || "skd".equals(type) || "mxl".equals(type)
                || "meta".equals(type) || "config".equals(type) || "extension".equals(type)
                || "subsystem".equals(type) || "interface".equals(type) || "template".equals(type)
                || "xcf-body".equals(type) || "client-interface".equals(type)
                || "platform-xsd".equals(type);
    }

    /**
     * TASK-171: надёжное определение корня конфигурации — подъём по дереву каталогов
     * до первого {@code Configuration.xml}. Заменяет хрупкое фиксированное число
     * {@code .getParent()}, которое промахивалось мимо корня из-за каталога
     * {@code Subsystems/} и вложенных подсистем ({@code Parent/Subsystems/Child.xml}).
     *
     * <p>Возвращает {@code null}, если {@code Configuration.xml} не найден (например,
     * подсистема в плоском extension-layout либо изолированный тестовый файл) — тогда
     * вызывающий код использует прежний fallback, сохраняя совместимость.
     */
    private static Path locateConfigRoot(Path start) {
        if (start == null) return null;
        Path dir = start.toAbsolutePath().normalize();
        // start обычно файл (Subsystem.xml / CommandInterface.xml) — поднимаемся от родителя.
        if (Files.isRegularFile(dir)) {
            dir = dir.getParent();
        }
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("Configuration.xml"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
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

    private static void guardMutation(Path target) {
        guardMutation(target, SupportRequirement.EDITABLE);
    }

    private static void guardMutation(Path target, SupportRequirement requirement) {
        try {
            SupportGuard.require(target, requirement);
        } catch (IOException e) {
            throw new RuntimeException("Failed to inspect support state: " + e.getMessage(), e);
        }
    }

    private static Path resolveObjectXml(Path configDir, String srcDir, io.github.onec.xmlgen.model.MdoPath object) {
        Path srcPath = Paths.get(srcDir);
        Path effectiveSrc = srcPath.isAbsolute() ? srcPath : configDir.resolve(srcDir);
        return effectiveSrc.resolve(object.getObjectXmlRelPath());
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

        guardMutation(file);
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

        //**agent TASK-174 [05.06.2026 00:00:00]
        // XG-03 (родственное): имя конфигурации тоже валидируем единым ONEC_NAME_PATTERN
        // (латиница+кириллица). Прежняя латиница-только регулярка — та же ошибочная посылка
        // TASK-155, что 1С-имена только латинские. Комментарий «same pattern as epf init»
        // теперь снова верен — оба пути используют ONEC_NAME_PATTERN.
        //// TASK-155 A2 iter-3: config init validations (bug-T-154-config-002).
        //if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
        //    throw new IllegalArgumentException(
        //        "Invalid configuration name: '" + name + "'. " +
        //        "Configuration names must match [A-Za-z_][A-Za-z0-9_]* " +
        //        "(Latin letters, digits, and underscores only; must not start with a digit).");
        //}
        if (!name.matches(ONEC_NAME_PATTERN)) {
            throw new IllegalArgumentException(
                "Invalid configuration name: '" + name + "'. " +
                "Configuration names must match " + ONEC_NAME_PATTERN + " " +
                "(Latin or Cyrillic letters, digits, and underscores only; must not start with a digit).");
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
            guardMutation(file);
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

            validateConfigPreviewNoNewErrors(file, editor.previewContent());
            editor.save();
            System.out.println("Configuration updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit configuration: " + e.getMessage(), e);
        }
    }

    private static void validateConfigPreviewNoNewErrors(Path file, String previewContent) throws IOException {
        Set<String> preExistingErrors;
        try {
            preExistingErrors = configErrorKeys(validateConfigContent(
                    file, ConfigurationXmlReader.readContent(file)));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot validate existing Configuration.xml before edit: " + e.getMessage(), e);
        }

        List<ConfigValidator.ValidationMessage> afterMessages;
        try {
            afterMessages = validateConfigContent(file, previewContent);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Config edit would produce unparsable Configuration.xml: " + e.getMessage(), e);
        }

        List<ConfigValidator.ValidationMessage> newErrors = afterMessages.stream()
                .filter(m -> "ERROR".equals(m.level))
                .filter(m -> !preExistingErrors.contains(configMessageKey(m)))
                .toList();
        if (!newErrors.isEmpty()) {
            String first = newErrors.get(0).message;
            throw new IllegalArgumentException(
                    "Config edit would introduce validation errors; file was not changed. First error: "
                            + first);
        }
    }

    private static List<ConfigValidator.ValidationMessage> validateConfigContent(Path file, String content)
            throws Exception {
        Path dir = file.toAbsolutePath().normalize().getParent();
        Path tmp = Files.createTempFile(dir, ".xmlgen-config-edit-", ".xml");
        try {
            Files.writeString(tmp, content, java.nio.charset.StandardCharsets.UTF_8);
            XmlDocument doc = new XmlStructureReader().parse(tmp);
            return new ConfigValidator().validate(doc, dir);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    private static Set<String> configErrorKeys(List<ConfigValidator.ValidationMessage> messages) {
        Set<String> keys = new HashSet<>();
        for (ConfigValidator.ValidationMessage message : messages) {
            if ("ERROR".equals(message.level)) {
                keys.add(configMessageKey(message));
            }
        }
        return keys;
    }

    private static String configMessageKey(ConfigValidator.ValidationMessage message) {
        return message.level + "\u0001" + message.message;
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

            exitForValidationSummary(errors, warnings);
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
            guardMutation(outputDir);
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
            guardMutation(file);
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
        // TASK-171: корень конфигурации = walk-up до Configuration.xml, а НЕ target.getParent().
        // Прежний getParent() давал .../Subsystems (а для вложенных подсистем — ещё глубже),
        // из-за чего existence-чек Content искал объекты в Subsystems/Catalogs/... и ложно
        // ругался ERROR на существующих объектах (9/10 _Демо валились, exit=1).
        // Fallback на getParent() — для extension-layout без Configuration.xml.
        Path subsystemDir = locateConfigRoot(target);
        if (subsystemDir == null) {
            subsystemDir = target.getParent();
        }

        if (!Files.exists(subsystemXml)) {
            throw new IllegalArgumentException("Subsystem XML not found: " + subsystemXml);
        }

        try {
            XmlDocument doc = new XmlStructureReader().parse(subsystemXml);
            SubsystemValidator validator = new SubsystemValidator();
            List<SubsystemValidator.ValidationMessage> messages =
                    validator.validate(doc, subsystemDir, subsystemXml);

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
            exitForValidationSummary(errors, warnings);
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
            guardMutation(file);
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

            validateInterfacePreviewNoNewErrors(file, editor.previewContent());
            editor.save();
            System.out.println("CommandInterface updated: " + operation);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit CommandInterface: " + e.getMessage(), e);
        }
    }

    private static void validateInterfacePreviewNoNewErrors(Path file, String previewContent) throws IOException {
        Set<String> preExistingErrors;
        try {
            preExistingErrors = interfaceErrorKeys(validateInterfaceContent(file, Files.readString(file)));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Cannot validate existing CommandInterface.xml before edit: " + e.getMessage(), e);
        }

        List<InterfaceValidator.ValidationMessage> afterMessages;
        try {
            afterMessages = validateInterfaceContent(file, previewContent);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Interface edit would produce unparsable CommandInterface.xml: " + e.getMessage(), e);
        }

        List<InterfaceValidator.ValidationMessage> newErrors = afterMessages.stream()
                .filter(m -> "ERROR".equals(m.level))
                .filter(m -> !preExistingErrors.contains(interfaceMessageKey(m)))
                .toList();
        if (!newErrors.isEmpty()) {
            String details = newErrors.stream()
                    .map(m -> m.message)
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalArgumentException(
                    "Interface edit would introduce validation errors; file was not changed. Errors: "
                            + details);
        }
    }

    private static List<InterfaceValidator.ValidationMessage> validateInterfaceContent(Path file, String content)
            throws Exception {
        Path dir = file.toAbsolutePath().normalize().getParent();
        Path tmp = Files.createTempFile(dir, ".xmlgen-interface-edit-", ".xml");
        try {
            Files.writeString(tmp, content, java.nio.charset.StandardCharsets.UTF_8);
            XmlDocument doc = new XmlStructureReader().parse(tmp);
            Path configRoot = locateConfigRoot(file);
            if (configRoot == null) {
                Path p2 = file.getParent();
                Path p3 = (p2 != null) ? p2.getParent() : null;
                if (p3 != null) configRoot = p3.getParent();
            }
            return new InterfaceValidator().validate(doc, configRoot);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (Exception ignored) {
            }
        }
    }

    private static Set<String> interfaceErrorKeys(List<InterfaceValidator.ValidationMessage> messages) {
        Set<String> keys = new HashSet<>();
        for (InterfaceValidator.ValidationMessage message : messages) {
            if ("ERROR".equals(message.level)) {
                keys.add(interfaceMessageKey(message));
            }
        }
        return keys;
    }

    private static String interfaceMessageKey(InterfaceValidator.ValidationMessage message) {
        return message.level + "\u0001" + message.message;
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
            // TASK-171: корень конфигурации = walk-up до Configuration.xml.
            // Прежние 3× .getParent() (Ext → SubsystemName → Subsystems) давали .../Subsystems
            // вместо корня; для вложенных подсистем промах ещё больше. Итог — ложные ERROR
            // на существующих объектах команд (6/8 _Демо CI валились, exit=1).
            // Fallback на 3×getParent() — для extension-layout без Configuration.xml.
            Path configRoot = locateConfigRoot(target);
            if (configRoot == null) {
                Path p2 = target.getParent();   // Ext/
                Path p3 = (p2 != null) ? p2.getParent() : null;  // SubsystemName/
                if (p3 != null) configRoot = p3.getParent();      // configRoot
            }
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
            exitForValidationSummary(errors, warnings);
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
            guardMutation(outputDir);
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
                guardMutation(Files.exists(objXml) ? objXml : objSubDir, SupportRequirement.REMOVED);
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
                guardMutation(objectPath);
                new MetaEditor().applyBatch(objectPath, batchFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to apply batch: " + e.getMessage(), e);
            }
            return;
        }

        // ── Normalize mode (XG-51) ───────────────────────────────────────
        // Идемпотентная нормализация объекта (DataProcessor/Report): вычистка
        // рантайм-невалидных под-свойств реквизитов. --value НЕ требуется —
        // операция работает на всём объекте.
        if ("normalize-runtime-attributes".equals(operation)) {
            if (objectPath == null) {
                throw new IllegalArgumentException(
                        "Usage: xml-gen meta edit <objectPath> --op normalize-runtime-attributes");
            }
            try {
                guardMutation(objectPath);
                new MetaEditor().edit(objectPath, operation, value == null ? "" : value);
            } catch (IOException e) {
                throw new RuntimeException("Failed to normalize metadata: " + e.getMessage(), e);
            }
            return;
        }

        // ── Inline mode ─────────────────────────────────────────────────
        if (objectPath == null || operation == null || value == null) {
            throw new IllegalArgumentException(
                    "Usage: xml-gen meta edit <objectPath> --op <operation> --value <value>\n"
                    + "       xml-gen meta edit <objectPath> --op normalize-runtime-attributes\n"
                    + "       xml-gen meta edit <objectPath> --batch <file.json>\n"
                    + "Operations: add-attribute, add-ts, add-dimension, add-resource, add-enumValue,\n"
                    + "  add-predefined (--value \"Имя[|Описание[|Код[|folder]]]\", батч через ;;),\n"
                    + "  add-exchange-content (--value \"Metadata[|AutoRecord]\", батч через ;; или @items.json),\n"
                    + "  add-column, add-form, add-template, add-command, add-ts-attribute,\n"
                    + "  remove-attribute, remove-ts, remove-dimension, ..., remove-ts-attribute,\n"
                    + "  modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column,\n"
                    + "  modify-ts (rename TС + согласованные GeneratedType; --value \"OldTS: name=NewTS\")");
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
        String opTarget = opParts[1];
        if (!opAction.equals("add") && !opAction.equals("remove") && !opAction.equals("modify")) {
            throw new IllegalArgumentException(
                "Unknown --op value: \"" + operation + "\". "
                + "Supported operations: add-attribute, add-ts, add-dimension, add-resource, "
                + "add-enumValue, add-column, add-form, add-template, add-command, add-ts-attribute, "
                + "remove-attribute, remove-ts, remove-dimension, remove-resource, remove-enumValue, "
                + "remove-column, remove-form, remove-template, remove-command, remove-ts-attribute, "
                + "modify-attribute, modify-dimension, modify-resource, modify-enumValue, modify-column.");
        }
        validateMetaEditTarget(opAction, opTarget);

        try {
            guardMutation(objectPath);
            new MetaEditor().edit(objectPath, operation, value);
        } catch (IOException e) {
            throw new RuntimeException("Failed to edit metadata: " + e.getMessage(), e);
        }
    }

    private static void validateMetaEditTarget(String action, String target) {
        Set<String> supported = switch (action) {
            case "add" -> Set.of("attribute", "ts", "dimension", "resource", "enumValue",
                    "predefined", "exchange-content", "column", "form", "template", "command",
                    "ts-attribute", "property");
            case "remove" -> Set.of("attribute", "ts", "dimension", "resource", "enumValue",
                    "column", "form", "template", "command", "ts-attribute");
            case "modify" -> Set.of("attribute", "dimension", "resource", "enumValue", "column", "ts", "property");
            default -> Set.of();
        };
        if (!supported.contains(target)) {
            throw new IllegalArgumentException("Unknown --op target: \"" + target
                    + "\" for action \"" + action + "\".");
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
            exitForValidationSummary(errors, warnings);
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
            exitForValidationSummary(errors, warnings);
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
            if (!dryRun) {
                guardMutation(file);
            }
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

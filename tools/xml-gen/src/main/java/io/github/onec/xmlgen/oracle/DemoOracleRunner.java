package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.onec.xmlgen.cli.Main;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.validator.EdtDerivedInvariantChecker;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class DemoOracleRunner {

    private static final String SPEC_ID = "SPEC-177";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final XmlStructureReader reader = new XmlStructureReader();
    private final PictureBodyOracle pictureBodyOracle = new PictureBodyOracle();
    private final EdtDerivedInvariantChecker edtInvariantChecker = new EdtDerivedInvariantChecker();

    public Map<String, Object> run(Path source, Path out, int limit, int threads, boolean includeMxl)
            throws Exception {
        validateOutputOutsideSource(source, out);
        Path sourceRoot = source.toAbsolutePath().normalize();
        Path outRoot = out.toAbsolutePath().normalize();
        Files.createDirectories(outRoot);
        String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path runDir = outRoot.resolve(runId);
        Files.createDirectories(runDir);

        List<Path> corpus = discover(sourceRoot, limit, includeMxl);
        List<DemoValidationResult> results = runValidate(corpus, sourceRoot, runDir, Math.max(1, threads));
        results.add(runFormGenerationEditOracle(runDir));
        results.sort(Comparator.comparing(DemoValidationResult::relativePath));
        Map<String, Object> report = report(runId, sourceRoot, runDir, results);
        mapper.writeValue(runDir.resolve("demo-oracle-report.json").toFile(), report);
        mapper.writeValue(outRoot.resolve("latest-demo-oracle-report.json").toFile(), report);
        Files.writeString(runDir.resolve("demo-oracle-summary.md"), summary(report), StandardCharsets.UTF_8);
        System.out.println("Demo oracle report: " + runDir.resolve("demo-oracle-report.json"));
        return report;
    }

    private List<Path> discover(Path sourceRoot, int limit, boolean includeMxl) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (Files.isRegularFile(sourceRoot)) {
            candidates.add(sourceRoot);
        } else {
            try (Stream<Path> stream = Files.walk(sourceRoot)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".xml"))
                        .filter(p -> p.toString().contains("_Демо"))
                        .forEach(candidates::add);
            }
        }
        candidates.sort(Comparator.comparing(Path::toString));
        List<Path> result = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!includeMxl && isMxl(candidate)) {
                continue;
            }
            result.add(candidate);
            if (limit > 0 && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<DemoValidationResult> runValidate(List<Path> corpus, Path sourceRoot, Path runDir, int threads)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<DemoValidationResult>> futures = new ArrayList<>();
            for (Path file : corpus) {
                futures.add(executor.submit(() -> validateOne(sourceRoot, runDir, file)));
            }
            List<DemoValidationResult> results = new ArrayList<>();
            for (Future<DemoValidationResult> future : futures) {
                results.add(future.get());
            }
            results.sort(Comparator.comparing(DemoValidationResult::relativePath));
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private DemoValidationResult validateOne(Path sourceRoot, Path runDir, Path file) {
        String relative = relativePath(sourceRoot, file);
        Path objectDir = runDir.resolve(safeId(relative));
        try {
            Files.createDirectories(objectDir);
            XmlDocument document = reader.parse(file);
            String type = detectType(document);
            String artifactKind = artifactKind(file, document, type);
            String capability = capability(document, type, artifactKind);
            if ("cli_registration_oracle_available".equals(capability)) {
                return runRegistrationOracle(sourceRoot, runDir, file, relative, document, type, artifactKind,
                        capability);
            }
            if ("picture_body_lossless_oracle_available".equals(capability)) {
                return runPictureBodyOracle(sourceRoot, runDir, file, relative, document, type, artifactKind,
                        capability);
            }
            if ("registration_coverage_gap".equals(capability)) {
                return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                        List.of("xml-gen", "oracle", "demo", "coverage-gap", file.toString()),
                        0, "COVERAGE_GAP", null, null, "", withEdtDetails(document, sourceRoot, Map.of()));
            }
            List<String> command = new ArrayList<>();
            command.add("xml-gen");
            command.add("validate");
            command.add("--output");
            command.add("json");
            command.add("--level");
            command.add("semantic");
            if (!"unknown".equals(type)) {
                command.add("--type");
                command.add(type);
            }
            if (Files.isDirectory(sourceRoot)) {
                command.add("--src-root");
                command.add(sourceRoot.toString());
            }
            command.add(file.toString());
            ProcessResult process = runCommand(command, objectDir);
            String status = switch (process.exitCode()) {
                case 0 -> "PASS";
                case 2 -> "WARN";
                default -> "FAIL";
            };
            if ("unsupported_behavioral_oracle".equals(capability)) {
                status = "UNSUPPORTED";
            } else if ("PASS".equals(status) && "validation_only_no_decompiler".equals(capability)) {
                status = "VALIDATION_ONLY";
            }
            return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability, command,
                    process.exitCode(), status, process.stdout(), process.stderr(), "",
                    withEdtDetails(document, sourceRoot, Map.of()));
        } catch (Exception e) {
            return new DemoValidationResult(relative, "unparsed", "unknown", "unparsed", "unsupported_behavioral_oracle",
                    List.of("xml-gen", "validate", file.toString()), -1, "ERROR", null, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), Map.of());
        }
    }

    private DemoValidationResult runPictureBodyOracle(Path sourceRoot, Path runDir, Path file, String relative,
                                                      XmlDocument document, String type, String artifactKind,
                                                      String capability) {
        Path objectDir = runDir.resolve(safeId(relative));
        try {
            List<String> command = List.of("xml-gen", "oracle", "demo", "picture-body-lossless", file.toString());
            Path validationDir = objectDir.resolve("validation");
            List<String> validateCommand = new ArrayList<>();
            validateCommand.add("xml-gen");
            validateCommand.add("validate");
            validateCommand.add("--output");
            validateCommand.add("json");
            validateCommand.add("--level");
            validateCommand.add("semantic");
            validateCommand.add("--type");
            validateCommand.add(type);
            if (Files.isDirectory(sourceRoot)) {
                validateCommand.add("--src-root");
                validateCommand.add(sourceRoot.toString());
            }
            validateCommand.add(file.toString());
            ProcessResult validation = runCommand(validateCommand, validationDir);
            if (validation.exitCode() != 0 && validation.exitCode() != 2) {
                return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                        command, validation.exitCode(), "FAIL", validation.stdout(), validation.stderr(),
                        "validation command exit code " + validation.exitCode(),
                        withEdtDetails(document, sourceRoot, Map.of()));
            }

            PictureBodyOracle.Result probe = pictureBodyOracle.probe(sourceRoot, file, document,
                    objectDir.resolve("lossless-sandbox"));
            String status = probe.passed() ? "PASS" : "FAIL";
            return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                    command, validation.exitCode(), status, validation.stdout(), validation.stderr(),
                    probe.error(), withEdtDetails(document, sourceRoot, probe.details()));
        } catch (Exception e) {
            return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                    List.of("xml-gen", "oracle", "demo", "picture-body-lossless", file.toString()),
                    -1, "ERROR", null, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    withEdtDetails(document, sourceRoot, Map.of()));
        }
    }

    private DemoValidationResult runRegistrationOracle(Path sourceRoot, Path runDir, Path file, String relative,
                                                       XmlDocument document, String type, String artifactKind,
                                                       String capability) {
        Path objectDir = runDir.resolve(safeId(relative));
        Path sandbox = objectDir.resolve("registration-sandbox");
        try {
            Files.createDirectories(sandbox);
            Path parentXml = parentObjectXml(file, artifactKind);
            if (parentXml == null || !Files.exists(parentXml)) {
                return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                        List.of("xml-gen", "oracle", "demo", "registration-check", file.toString()),
                        -1, "FAIL", null, null, "Parent metadata object XML not found",
                        withEdtDetails(document, sourceRoot, Map.of()));
            }

            Path sandboxObjectXml = sandbox.resolve(parentXml.getFileName().toString());
            Files.copy(parentXml, sandboxObjectXml, StandardCopyOption.REPLACE_EXISTING);

            String probeName = switch (artifactKind) {
                case "form-wrapper" -> "OracleForm";
                case "template-wrapper" -> "OracleTemplate";
                case "help" -> "ru";
                default -> "OracleProbe";
            };
            List<String> command = registrationCommand(artifactKind, sandboxObjectXml, probeName);
            ProcessResult process = runCommand(command, objectDir);
            String error = registrationError(artifactKind, sandboxObjectXml, probeName, process.exitCode());
            String status = error.isEmpty() ? "PASS" : "FAIL";
            return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                    command, process.exitCode(), status, process.stdout(), process.stderr(), error,
                    withEdtDetails(document, sourceRoot, Map.of()));
        } catch (Exception e) {
            return new DemoValidationResult(relative, document.getRootElement(), type, artifactKind, capability,
                    List.of("xml-gen", "oracle", "demo", "registration-check", file.toString()),
                    -1, "ERROR", null, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    withEdtDetails(document, sourceRoot, Map.of()));
        }
    }

    private DemoValidationResult runFormGenerationEditOracle(Path runDir) {
        String relative = "__oracle__/form-generation-edit/Form.xml";
        String artifactKind = "form-generation-edit";
        String capability = "form_generation_edit_oracle_available";
        Path objectDir = runDir.resolve("form-generation-edit-oracle");
        Path sandbox = objectDir.resolve("sandbox");
        Path dslJson = sandbox.resolve("seed-form.json");
        Path formXml = sandbox.resolve("Forms").resolve("OracleForm").resolve("Ext").resolve("Form.xml");
        try {
            Files.createDirectories(formXml.getParent());
            Files.writeString(dslJson, """
                    {
                      "title": "Oracle generated form",
                      "attributes": [
                        {
                          "name": "Список",
                          "type": "ValueTable",
                          "columns": [
                            {"name": "Активен", "type": "boolean"}
                          ]
                        }
                      ],
                      "elements": [
                        {
                          "type": "table",
                          "name": "Список",
                          "dataPath": "Список",
                          "userSettingsGroup": "ГруппаПользовательскихНастроек",
                          "columns": [
                            {"type": "check", "name": "СписокАктивен", "dataPath": "Список.Активен"}
                          ]
                        }
                      ]
                    }
                    """, StandardCharsets.UTF_8);

            List<OracleStep> steps = List.of(
                    new OracleStep("compile", List.of("xml-gen", "form", "compile",
                            dslJson.toString(), formXml.toString())),
                    new OracleStep("add-attribute", List.of("xml-gen", "form", "add-attribute",
                            "--name", "Комментарий", "--type", "string(100)", formXml.toString())),
                    new OracleStep("add-command", List.of("xml-gen", "form", "add-command",
                            "--name", "Выполнить", "--title", "Выполнить", "--action", "Выполнить",
                            formXml.toString())),
                    new OracleStep("add-group", List.of("xml-gen", "form", "add-element",
                            "--type", "group", "--name", "ГруппаОсновная", formXml.toString())),
                    new OracleStep("add-input", List.of("xml-gen", "form", "add-element",
                            "--type", "input", "--name", "ПолеКомментарий", "--path", "Комментарий",
                            "--parent", "ГруппаОсновная", formXml.toString())),
                    new OracleStep("add-movable-input", List.of("xml-gen", "form", "add-element",
                            "--type", "input", "--name", "ПолеПереносимое", "--path", "Комментарий",
                            formXml.toString())),
                    new OracleStep("move-input", List.of("xml-gen", "form", "move-element",
                            "--name", "ПолеПереносимое", "--parent", "ГруппаОсновная", formXml.toString())),
                    new OracleStep("add-popup", List.of("xml-gen", "form", "add-element",
                            "--type", "popup", "--name", "Подменю", "--parent", "ФормаКоманднаяПанель",
                            formXml.toString())),
                    new OracleStep("add-button-group", List.of("xml-gen", "form", "add-element",
                            "--type", "ButtonGroup", "--name", "ГруппаКнопок",
                            "--parent", "ФормаКоманднаяПанель", formXml.toString())),
                    new OracleStep("add-form-button", List.of("xml-gen", "form", "add-element",
                            "--type", "button", "--name", "КомандаВыполнить",
                            "--parent", "ФормаКоманднаяПанель", "--command", "Выполнить",
                            formXml.toString())),
                    new OracleStep("add-popup-button", List.of("xml-gen", "form", "add-element",
                            "--type", "button", "--name", "КомандаВМеню",
                            "--parent", "Подменю", "--command", "Выполнить", formXml.toString())),
                    new OracleStep("add-group-button", List.of("xml-gen", "form", "add-element",
                            "--type", "button", "--name", "КомандаВГруппе",
                            "--parent", "ГруппаКнопок", "--command", "Выполнить", formXml.toString())),
                    new OracleStep("validate", List.of("xml-gen", "validate", "--output", "json",
                            "--level", "semantic", "--type", "form", formXml.toString()))
            );

            ProcessResult last = null;
            List<String> executed = new ArrayList<>();
            for (OracleStep step : steps) {
                executed.add(String.join(" ", step.command()));
                last = runCommand(step.command(), objectDir, step.id());
                if (last.exitCode() != 0) {
                    return new DemoValidationResult(relative, "Form", "form", artifactKind, capability,
                            List.of("xml-gen", "oracle", "demo", "form-generation-edit"),
                            last.exitCode(), "FAIL", last.stdout(), last.stderr(),
                            "step '" + step.id() + "' failed", formGenerationEditDetails(formXml));
                }
            }

            String structuralError = formGenerationEditStructuralError(formXml);
            String status = structuralError.isEmpty() ? "PASS" : "FAIL";
            Path stdout = last != null ? last.stdout() : null;
            Path stderr = last != null ? last.stderr() : null;
            return new DemoValidationResult(relative, "Form", "form", artifactKind, capability,
                    List.of("xml-gen", "oracle", "demo", "form-generation-edit"),
                    last != null ? last.exitCode() : 0, status, stdout, stderr, structuralError,
                    formGenerationEditDetails(formXml));
        } catch (Exception e) {
            return new DemoValidationResult(relative, "Form", "form", artifactKind, capability,
                    List.of("xml-gen", "oracle", "demo", "form-generation-edit"),
                    -1, "ERROR", null, null,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), Map.of());
        }
    }

    private Map<String, Object> formGenerationEditDetails(Path formXml) {
        try {
            if (Files.isRegularFile(formXml)) {
                return withEdtDetails(reader.parse(formXml), null, Map.of());
            }
        } catch (Exception ignored) {
            // Primary form-generation failure stays in the main error field.
        }
        return Map.of();
    }

    private String formGenerationEditStructuralError(Path formXml) {
        try {
            XmlNode root = reader.parse(formXml).getRoot();
            List<String> errors = new ArrayList<>();
            XmlNode rootItems = root.child("ChildItems");
            if (rootItems == null) {
                errors.add("root ChildItems missing");
            }

            XmlNode userSettings = findElementByName(rootItems, "ГруппаПользовательскихНастроек");
            XmlNode table = findElementByName(rootItems, "Список");
            XmlNode group = findElementByName(rootItems, "ГруппаОсновная");
            XmlNode formBar = root.child("AutoCommandBar");
            XmlNode popup = findElementByName(root, "Подменю");
            XmlNode buttonGroup = findElementByName(root, "ГруппаКнопок");

            if (userSettings == null || !"UsualGroup".equals(userSettings.getName())) {
                errors.add("implicit UserSettingsGroup placeholder missing");
            }
            if (table == null || !"Table".equals(table.getName())) {
                errors.add("table missing");
            } else {
                if (!"ГруппаПользовательскихНастроек".equals(table.childText("UserSettingsGroup"))) {
                    errors.add("table UserSettingsGroup reference missing");
                }
                requireTableAddition(table, "SearchStringAddition", "SearchStringRepresentation", errors);
                requireTableAddition(table, "ViewStatusAddition", "ViewStatusRepresentation", errors);
                requireTableAddition(table, "SearchControlAddition", "SearchControl", errors);
                if (table.child("ChildItems") == null) {
                    errors.add("table ChildItems missing");
                }
            }
            if (rootItems != null && userSettings != null && table != null
                    && rootItems.getChildren().indexOf(userSettings) > rootItems.getChildren().indexOf(table)) {
                errors.add("UserSettingsGroup placeholder must be emitted before referenced table");
            }
            if (group == null || group.child("ChildItems") == null) {
                errors.add("group ChildItems missing");
            } else {
                if (findElementByName(group.child("ChildItems"), "ПолеКомментарий") == null) {
                    errors.add("input was not added into group ChildItems");
                }
                if (findElementByName(group.child("ChildItems"), "ПолеПереносимое") == null) {
                    errors.add("move-element did not move input into group ChildItems");
                }
            }
            if (formBar == null || formBar.child("ChildItems") == null) {
                errors.add("form AutoCommandBar ChildItems missing");
            } else {
                if (findElementByName(formBar.child("ChildItems"), "КомандаВыполнить") == null) {
                    errors.add("button was not added into form AutoCommandBar");
                }
                if (findElementByName(formBar.child("ChildItems"), "Подменю") == null) {
                    errors.add("popup was not added into form AutoCommandBar");
                }
                if (findElementByName(formBar.child("ChildItems"), "ГруппаКнопок") == null) {
                    errors.add("ButtonGroup was not added into form AutoCommandBar");
                }
            }
            requireButtonCommand(findElementByName(root, "КомандаВыполнить"), errors);
            requireButtonCommand(findElementByName(root, "КомандаВМеню"), errors);
            requireButtonCommand(findElementByName(root, "КомандаВГруппе"), errors);
            if (popup == null || popup.child("ChildItems") == null
                    || findElementByName(popup.child("ChildItems"), "КомандаВМеню") == null) {
                errors.add("popup ChildItems/button missing");
            }
            if (buttonGroup == null || buttonGroup.child("ChildItems") == null
                    || findElementByName(buttonGroup.child("ChildItems"), "КомандаВГруппе") == null) {
                errors.add("ButtonGroup ChildItems/button missing");
            }
            return String.join("; ", errors);
        } catch (Exception e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private void requireTableAddition(XmlNode table, String tag, String type, List<String> errors) {
        XmlNode addition = table.child(tag);
        if (addition == null) {
            errors.add(tag + " missing");
            return;
        }
        XmlNode source = addition.child("AdditionSource");
        if (source == null) {
            errors.add(tag + " AdditionSource missing");
            return;
        }
        if (!"Список".equals(source.childText("Item"))) {
            errors.add(tag + " AdditionSource Item mismatch");
        }
        if (!type.equals(source.childText("Type"))) {
            errors.add(tag + " AdditionSource Type mismatch");
        }
    }

    private void requireButtonCommand(XmlNode button, List<String> errors) {
        if (button == null) {
            errors.add("button missing");
            return;
        }
        if (!"UsualButton".equals(button.childText("Type"))) {
            errors.add("button Type UsualButton missing: " + button.attr("name"));
        }
        if (!"Form.Command.Выполнить".equals(button.childText("CommandName"))) {
            errors.add("button CommandName missing: " + button.attr("name"));
        }
    }

    private XmlNode findElementByName(XmlNode node, String name) {
        if (node == null) {
            return null;
        }
        if (name.equals(node.attr("name"))) {
            return node;
        }
        for (XmlNode child : node.getChildren()) {
            XmlNode found = findElementByName(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<String> registrationCommand(String artifactKind, Path sandboxObjectXml, String probeName) {
        return switch (artifactKind) {
            case "form-wrapper" -> List.of("xml-gen", "meta", "edit", sandboxObjectXml.toString(),
                    "--op", "add-form", "--value", probeName);
            case "template-wrapper" -> List.of("xml-gen", "meta", "edit", sandboxObjectXml.toString(),
                    "--op", "add-template", "--value", probeName);
            case "help" -> List.of("xml-gen", "help", "add", sandboxObjectXml.toString(), "--lang", probeName);
            default -> List.of("xml-gen", "oracle", "demo", "registration-check", sandboxObjectXml.toString());
        };
    }

    private String registrationError(String artifactKind, Path sandboxObjectXml, String probeName, int exitCode)
            throws IOException {
        List<String> errors = new ArrayList<>();
        if (exitCode != 0) {
            errors.add("registration command exit code " + exitCode);
        }

        String objectName = stripXmlExtension(sandboxObjectXml.getFileName().toString());
        Path objectDir = sandboxObjectXml.getParent().resolve(objectName);
        String objectXml = Files.exists(sandboxObjectXml)
                ? Files.readString(sandboxObjectXml, StandardCharsets.UTF_8)
                : "";

        if ("form-wrapper".equals(artifactKind)) {
            if (!objectXml.contains("<Form>" + probeName + "</Form>")) {
                errors.add("parent ChildObjects has no Form reference");
            }
            if (!Files.exists(objectDir.resolve("Forms").resolve(probeName + ".xml"))) {
                errors.add("form wrapper was not created");
            }
            if (!Files.exists(objectDir.resolve("Forms").resolve(probeName).resolve("Ext").resolve("Form.xml"))) {
                errors.add("form body was not created");
            }
            if (!Files.exists(objectDir.resolve("Forms").resolve(probeName)
                    .resolve("Ext").resolve("Form").resolve("Module.bsl"))) {
                errors.add("form module was not created");
            }
        } else if ("template-wrapper".equals(artifactKind)) {
            if (!objectXml.contains("<Template>" + probeName + "</Template>")) {
                errors.add("parent ChildObjects has no Template reference");
            }
            if (!Files.exists(objectDir.resolve("Templates").resolve(probeName + ".xml"))) {
                errors.add("template wrapper was not created");
            }
        } else if ("help".equals(artifactKind)) {
            if (!Files.exists(objectDir.resolve("Ext").resolve("Help.xml"))) {
                errors.add("Help.xml was not created");
            }
            if (!Files.exists(objectDir.resolve("Ext").resolve("Help").resolve(probeName + ".html"))) {
                errors.add("help html page was not created");
            }
        }
        return String.join("; ", errors);
    }

    private Path parentObjectXml(Path file, String artifactKind) {
        Path normalized = file.toAbsolutePath().normalize();
        if ("form-wrapper".equals(artifactKind) || "template-wrapper".equals(artifactKind)) {
            Path collectionDir = normalized.getParent();
            Path objectDir = collectionDir == null ? null : collectionDir.getParent();
            return objectXmlFromObjectDir(objectDir);
        }
        if ("help".equals(artifactKind)) {
            Path extDir = normalized.getParent();
            Path objectDir = extDir == null ? null : extDir.getParent();
            return objectXmlFromObjectDir(objectDir);
        }
        return null;
    }

    private Path objectXmlFromObjectDir(Path objectDir) {
        if (objectDir == null || objectDir.getParent() == null || objectDir.getFileName() == null) {
            return null;
        }
        return objectDir.getParent().resolve(objectDir.getFileName().toString() + ".xml");
    }

    private ProcessResult runCommand(List<String> displayCommand, Path objectDir)
            throws IOException, InterruptedException {
        return runCommand(displayCommand, objectDir, "validate");
    }

    private ProcessResult runCommand(List<String> displayCommand, Path objectDir, String logPrefix)
            throws IOException, InterruptedException {
        Files.createDirectories(objectDir);
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(absoluteClasspath());
        command.add(Main.class.getName());
        command.addAll(displayCommand.subList(1, displayCommand.size()));
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        boolean exited = process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        String safePrefix = safeId(logPrefix == null || logPrefix.isBlank() ? "command" : logPrefix);
        Path stdout = objectDir.resolve(safePrefix + ".stdout.txt");
        Path stderr = objectDir.resolve(safePrefix + ".stderr.txt");
        Files.write(stdout, process.getInputStream().readAllBytes());
        Files.write(stderr, process.getErrorStream().readAllBytes());
        return new ProcessResult(exited ? process.exitValue() : -1, stdout, stderr);
    }

    private String detectType(XmlDocument doc) {
        return switch (doc.getRootElement()) {
            case "Rights" -> "role";
            case "Form" -> "form";
            case "DataCompositionSchema" -> "skd";
            case "document" -> "mxl";
            case "ExternalDataProcessor", "ExternalReport" -> "epf";
            case "CommandInterface" -> "interface";
            case "Subsystem" -> "subsystem";
            case "ExtPicture", "ExchangePlanContent", "PredefinedData",
                    "AccumulationRegisterAggregates", "GraphicalSchema",
                    "AppearanceTemplate", "Help" -> "xcf-body";
            case "MetaDataObject" -> detectMetaDataObjectType(doc.getRoot());
            default -> "unknown";
        };
    }

    private String detectMetaDataObjectType(XmlNode root) {
        if (root.child("ExternalDataProcessor") != null || root.child("ExternalReport") != null) {
            return "epf";
        }
        if (root.child("Configuration") != null) {
            return "config";
        }
        if (root.child("Subsystem") != null) {
            return "subsystem";
        }
        if (root.child("Template") != null) {
            return "template";
        }
        for (XmlNode child : root.getChildren()) {
            if (MetadataTypeRegistry.byXmlElement(child.getName()) != null) {
                return "meta";
            }
        }
        return "unknown";
    }

    private String artifactKind(Path file, XmlDocument document, String type) {
        String path = file.toString().replace('\\', '/');
        String root = document.getRootElement();
        if (path.endsWith("/Ext/Form.xml")) return "form-body";
        if (path.endsWith("/Ext/Rights.xml")) return "role-rights";
        if (path.endsWith("/Ext/CommandInterface.xml")) return "command-interface";
        if (path.endsWith("/Ext/Help.xml")) return "help";
        if (path.endsWith("/Ext/Picture.xml")) return "picture";
        if (path.endsWith("/Ext/Content.xml")) return "exchange-plan-content";
        if (path.endsWith("/Ext/Predefined.xml")) return "predefined-data";
        if (path.endsWith("/Ext/Aggregates.xml")) return "accumulation-register-aggregates";
        if (path.endsWith("/Ext/Flowchart.xml")) return "flowchart";
        if (path.endsWith("/Ext/Template.xml")) {
            return switch (root) {
                case "document" -> "mxl-template-body";
                case "DataCompositionSchema" -> "skd-template-body";
                case "AppearanceTemplate" -> "appearance-template-body";
                case "Help" -> "help-template-body";
                default -> "template-body-unsupported";
            };
        }
        if ("MetaDataObject".equals(root) && path.contains("/Forms/")) return "form-wrapper";
        if ("MetaDataObject".equals(root) && path.contains("/Templates/")) return "template-wrapper";
        if ("MetaDataObject".equals(root) && path.contains("/Subsystems/")) return "subsystem-wrapper";
        if ("MetaDataObject".equals(root)) return "metadata-root";
        if ("Form".equals(root)) return "form-body";
        if ("document".equals(root)) return "mxl-template-body";
        if ("DataCompositionSchema".equals(root)) return "skd-template-body";
        if ("Rights".equals(root)) return "role-rights";
        if ("CommandInterface".equals(root)) return "command-interface";
        if ("Help".equals(root)) return "help";
        if ("ExtPicture".equals(root)) return "picture";
        if ("ExchangePlanContent".equals(root)) return "exchange-plan-content";
        if ("PredefinedData".equals(root)) return "predefined-data";
        if ("AccumulationRegisterAggregates".equals(root)) return "accumulation-register-aggregates";
        if ("GraphicalSchema".equals(root)) return "flowchart";
        if ("AppearanceTemplate".equals(root)) return "appearance-template-body";
        return type;
    }

    private String capability(XmlDocument document, String type, String artifactKind) {
        if ("mxl".equals(type)) {
            return "full_behavioral_oracle_available";
        }
        if (List.of("form-wrapper", "template-wrapper", "help").contains(artifactKind)) {
            return "cli_registration_oracle_available";
        }
        if ("picture".equals(artifactKind)) {
            return "picture_body_lossless_oracle_available";
        }
        if (List.of("exchange-plan-content", "predefined-data",
                "accumulation-register-aggregates", "flowchart",
                "appearance-template-body", "help-template-body").contains(artifactKind)) {
            return "validation_only_no_decompiler";
        }
        if (List.of("form", "skd", "role", "epf", "meta", "config", "extension",
                "subsystem", "interface", "template", "xcf-body").contains(type)) {
            return "validation_only_no_decompiler";
        }
        return "registration_coverage_gap";
    }

    private Map<String, Object> report(String runId, Path sourceRoot, Path runDir,
                                       List<DemoValidationResult> results) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runId", runId);
        report.put("specId", SPEC_ID);
        report.put("mode", "demo-validation");
        report.put("source", sourceRoot.toString());
        report.put("artifactDir", runDir.toString());
        report.put("summary", summarize(results));
        report.put("byType", summarizeBy(results, DemoValidationResult::type));
        report.put("byArtifactKind", summarizeBy(results, DemoValidationResult::artifactKind));
        report.put("byCapability", summarizeBy(results, DemoValidationResult::capability));
        report.put("byFailureBucket", summarizeBy(results, DemoValidationResult::failureBucket));
        report.put("results", results);
        return report;
    }

    private Map<String, Object> withEdtDetails(XmlDocument document, Path sourceRoot,
                                               Map<String, Object> baseDetails) {
        Map<String, Object> details = new LinkedHashMap<>(baseDetails);
        details.put("edtDerivedInvariants", edtInvariantChecker.check(document, sourceRoot).toDetails());
        return details;
    }

    private Map<String, Integer> summarize(List<DemoValidationResult> results) {
        return summarizeBy(results, DemoValidationResult::status);
    }

    private Map<String, Integer> summarizeBy(List<DemoValidationResult> results,
                                             java.util.function.Function<DemoValidationResult, String> keyFn) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (DemoValidationResult result : results) {
            summary.merge(keyFn.apply(result), 1, Integer::sum);
        }
        return summary;
    }

    private String summary(Map<String, Object> report) {
        return "# xml-gen demo oracle summary\n\n"
                + "- run_id: `" + report.get("runId") + "`\n"
                + "- source: `" + report.get("source") + "`\n"
                + "- summary: `" + report.get("summary") + "`\n"
                + "- by_type: `" + report.get("byType") + "`\n"
                + "- by_artifact_kind: `" + report.get("byArtifactKind") + "`\n"
                + "- by_capability: `" + report.get("byCapability") + "`\n"
                + "- by_failure_bucket: `" + report.get("byFailureBucket") + "`\n";
    }

    private boolean isMxl(Path path) {
        try {
            return "document".equals(reader.parse(path).getRootElement());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void validateOutputOutsideSource(Path source, Path out) {
        Path normalizedOut = out.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedSource)) {
            if (normalizedOut.equals(normalizedSource)) {
                throw new IllegalArgumentException("--out must be outside --source: " + normalizedOut);
            }
            return;
        }
        if (normalizedOut.equals(normalizedSource) || normalizedOut.startsWith(normalizedSource)) {
            throw new IllegalArgumentException("--out must be outside --source: " + normalizedOut);
        }
    }

    private String relativePath(Path sourceRoot, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        if (Files.isDirectory(sourceRoot) && normalized.startsWith(sourceRoot)) {
            return sourceRoot.relativize(normalized).toString().replace('\\', '/');
        }
        return file.getFileName().toString();
    }

    private String safeId(String value) {
        return value.replace('\\', '/').replace('/', '_')
                .replaceAll("[^A-Za-z0-9_А-ЯЁа-яё.-]", "_");
    }

    private String stripXmlExtension(String name) {
        return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    private String absoluteClasspath() {
        String separator = System.getProperty("path.separator");
        String[] entries = System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(separator));
        List<String> absolute = new ArrayList<>();
        Path userDir = Path.of(System.getProperty("user.dir"));
        for (String entry : entries) {
            Path path = Path.of(entry);
            absolute.add(path.isAbsolute() ? path.toString() : userDir.resolve(path).normalize().toString());
        }
        return String.join(separator, absolute);
    }

    private static String classifyFailureBucket(String status, String error, Path stdoutPath, Path stderrPath) {
        if (!List.of("WARN", "FAIL", "ERROR").contains(status)) {
            return PlatformFailureBucket.NONE.reportId();
        }
        String text = (error == null ? "" : error)
                + "\n" + readForClassification(stdoutPath)
                + "\n" + readForClassification(stderrPath);
        return PlatformFailureClassifier.classifyReportId(text);
    }

    private static String readForClassification(Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
    }

    private record OracleStep(String id, List<String> command) {}

    private record ProcessResult(int exitCode, Path stdout, Path stderr) {}

    public record DemoValidationResult(
            String relativePath,
            String rootElement,
            String type,
            String artifactKind,
            String capability,
            List<String> command,
            int exitCode,
            String status,
            Path stdoutPath,
            Path stderrPath,
            String error,
            String failureBucket,
            Map<String, Object> details
    ) {
        public DemoValidationResult(String relativePath, String rootElement, String type,
                                    String artifactKind, String capability, List<String> command,
                                    int exitCode, String status, Path stdoutPath, Path stderrPath,
                                    String error, Map<String, Object> details) {
            this(relativePath, rootElement, type, artifactKind, capability, command, exitCode, status,
                    stdoutPath, stderrPath, error, classifyFailureBucket(status, error, stdoutPath, stderrPath),
                    details);
        }
    }
}

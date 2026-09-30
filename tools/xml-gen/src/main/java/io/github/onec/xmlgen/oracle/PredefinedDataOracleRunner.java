package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class PredefinedDataOracleRunner {

    private static final String SPEC_ID = "platform-resource-clues";
    private final XmlStructureReader reader = new XmlStructureReader();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final OracleComparator comparator = new OracleComparator();

    public OracleReport run(Path source, Path out, int limit) throws Exception {
        validateOutputOutsideSource(source, out);
        Path normalizedOut = out.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOut);
        String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path runDir = normalizedOut.resolve(runId);
        Files.createDirectories(runDir);

        List<CanonObject> corpus = discover(source, limit);
        List<CmpResult> results = new ArrayList<>();
        List<CoverageMatrix.CoverageRow> coverageRows = new ArrayList<>();
        for (CanonObject canon : corpus) {
            Path sandbox = runDir.resolve(canon.objectId()).resolve("cli");
            CmpResult result = runCliMode(canon, sandbox);
            results.add(result);
            coverageRows.addAll(coverage(canon, result));
        }

        CoverageMatrix coverageMatrix = new CoverageMatrix(1, "predefined-data", coverageRows);
        OracleReport report = new OracleReport(runId, SPEC_ID, "predefined-data", summaries(results),
                results, List.of(), "coverage-matrix.json");
        new OracleReportWriter().write(runDir, report, coverageMatrix, List.of());
        mapper.writeValue(normalizedOut.resolve("latest-predefined-data-oracle-report.json").toFile(), report);
        System.out.println("PredefinedData oracle report: " + runDir.resolve("oracle-report.json"));
        return report;
    }

    private CmpResult runCliMode(CanonObject canon, Path sandbox) {
        try {
            Files.createDirectories(sandbox);
            CanonPredefinedData data = readCanon(canon);
            Path dslJson = sandbox.resolve("object.json");
            Path predefinedJson = sandbox.resolve("predefined-items.json");
            writeObjectDsl(data, dslJson);
            writePredefinedItemsDsl(data, predefinedJson);
            CommandPlan plan = commandPlan(canon, sandbox, dslJson, predefinedJson, data);
            Path commandPlanPath = sandbox.resolve("CommandPlan.json");
            Path executionResultPath = sandbox.resolve("ExecutionResult.json");
            mapper.writeValue(commandPlanPath.toFile(), plan);
            ExecutionResult execution = new CommandPlanExecutor().execute(plan);
            mapper.writeValue(executionResultPath.toFile(), execution);
            if (!execution.passed()) {
                DiffEntry diff = new DiffEntry("/command-plan/" + execution.failedStep(), "execution",
                        "passed", "failed", execution.message());
                return CmpResult.fail(OracleMode.CLI.id(), canon.objectId(), List.of(diff),
                        FailureClass.C_OR_EXEC_BUG, context(plan, commandPlanPath, executionResultPath, execution));
            }
            CmpResult result = comparator.compareStructure(OracleMode.CLI.id(), canon.objectId(), canon.path(),
                    plan.sandbox().resolve(plan.resultArtifact()).normalize(), IgnoreAllowlist.empty("predefined-data"));
            return withContext(result, context(plan, commandPlanPath, executionResultPath, execution));
        } catch (Exception e) {
            return CmpResult.error(OracleMode.CLI.id(), canon.objectId(), "predefined-data-cli", e);
        }
    }

    private void writeObjectDsl(CanonPredefinedData data, Path dslJson) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", data.type());
        root.put("name", data.objectName());
        mapper.writeValue(dslJson.toFile(), root);
    }

    private void writePredefinedItemsDsl(CanonPredefinedData data, Path predefinedJson) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.set("items", data.items());
        mapper.writeValue(predefinedJson.toFile(), root);
    }

    private CommandPlan commandPlan(CanonObject canon, Path sandbox, Path dslJson, Path predefinedJson,
                                    CanonPredefinedData data) {
        MetadataTypeRegistry.TypeDescriptor descriptor = MetadataTypeRegistry.get(data.type());
        Path result = Path.of("out").resolve(descriptor.directory()).resolve(data.objectName())
                .resolve("Ext").resolve("Predefined.xml");
        Path objectXml = Path.of("out").resolve(descriptor.directory()).resolve(data.objectName() + ".xml");
        return new CommandPlan(
                "predefined-data/" + canon.objectId(),
                OracleMode.CLI.id(),
                sandbox,
                List.of(
                        new CommandStep("init-config",
                                List.of("xml-gen", "config", "init", "out", "OraclePredefined",
                                        "--format-version", data.version()),
                                List.of(CommandAssertion.exitCode(0))),
                        new CommandStep("compile-object",
                                List.of("xml-gen", "meta", "compile", dslJson.toAbsolutePath().toString(), "out"),
                                List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists(objectXml.toString()))),
                        new CommandStep("add-predefined",
                                List.of("xml-gen", "meta", "edit", objectXml.toString(),
                                        "--op", "add-predefined",
                                        "--value", "@" + predefinedJson.toAbsolutePath()),
                                List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists(result.toString()))),
                        new CommandStep("validate-predefined",
                                List.of("xml-gen", "validate", "--type", "xcf-body", result.toString()),
                                List.of(CommandAssertion.exitCodes(0, 2)))
                ),
                result.toString()
        );
    }

    private CanonPredefinedData readCanon(CanonObject canon) throws Exception {
        XmlDocument document = reader.parse(canon.path());
        XmlNode root = document.getRoot();
        String xsiType = root.attr("xsi:type");
        String type = typeForXsiType(xsiType);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported PredefinedData xsi:type: " + xsiType);
        }
        String objectName = objectName(canon.path());
        String version = root.attr("version") == null || root.attr("version").isBlank() ? "2.20" : root.attr("version");
        ArrayNode items = mapper.createArrayNode();
        for (XmlNode item : root.children("Item")) {
            String name = item.childText("Name");
            if (name == null || name.isBlank()) {
                continue;
            }
            items.add(itemDsl(item));
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("PredefinedData has no top-level Item entries: " + canon.path());
        }
        return new CanonPredefinedData(type, objectName, version, items);
    }

    private ObjectNode itemDsl(XmlNode item) {
        ObjectNode node = mapper.createObjectNode();
        String name = item.childText("Name");
        node.put("name", name);
        node.put("code", valueOrEmpty(item.childText("Code")));
        node.put("description", item.childText("Description") == null ? name : item.childText("Description"));
        if (item.child("IsFolder") != null) {
            node.put("isFolder", "true".equalsIgnoreCase(valueOrEmpty(item.childText("IsFolder"))));
        }
        XmlNode type = item.child("Type");
        if (type != null) {
            ArrayNode types = mapper.createArrayNode();
            for (XmlNode typeValue : type.children("Type")) {
                types.add(typeValue.getText());
            }
            node.set("types", types);
        }
        putIfPresent(node, item, "AccountType", "accountType");
        if (item.child("OffBalance") != null) {
            node.put("offBalance", "true".equalsIgnoreCase(valueOrEmpty(item.childText("OffBalance"))));
        }
        putIfPresent(node, item, "Order", "order");
        XmlNode accountingFlags = item.child("AccountingFlags");
        if (accountingFlags != null) {
            node.set("accountingFlags", flagsDsl(accountingFlags));
        }
        XmlNode extDimensionTypes = item.child("ExtDimensionTypes");
        if (extDimensionTypes != null) {
            ArrayNode dimensions = mapper.createArrayNode();
            for (XmlNode extDimensionType : extDimensionTypes.children("ExtDimensionType")) {
                ObjectNode dimension = mapper.createObjectNode();
                dimension.put("name", valueOrEmpty(extDimensionType.attr("name")));
                dimension.put("turnover", "true".equalsIgnoreCase(valueOrEmpty(extDimensionType.childText("Turnover"))));
                XmlNode flags = extDimensionType.child("AccountingFlags");
                if (flags != null) {
                    dimension.set("accountingFlags", flagsDsl(flags));
                }
                dimensions.add(dimension);
            }
            node.set("extDimensionTypes", dimensions);
        }
        if (item.child("ActionPeriodIsBase") != null) {
            node.put("actionPeriodIsBase",
                    "true".equalsIgnoreCase(valueOrEmpty(item.childText("ActionPeriodIsBase"))));
        }
        XmlNode displaced = item.child("Displaced");
        if (displaced != null) {
            ArrayNode calculationTypes = mapper.createArrayNode();
            for (XmlNode calculationType : displaced.children("CalculationType")) {
                calculationTypes.add(calculationType.getText());
            }
            node.set("displaced", calculationTypes);
        }
        XmlNode childItems = item.child("ChildItems");
        if (childItems != null) {
            ArrayNode children = mapper.createArrayNode();
            for (XmlNode child : childItems.children("Item")) {
                children.add(itemDsl(child));
            }
            node.set("childItems", children);
        }
        return node;
    }

    private ObjectNode flagsDsl(XmlNode accountingFlags) {
        ObjectNode flags = mapper.createObjectNode();
        for (XmlNode flag : accountingFlags.children("Flag")) {
            flags.put(valueOrEmpty(flag.attr("ref")), "true".equalsIgnoreCase(valueOrEmpty(flag.getText())));
        }
        return flags;
    }

    private void putIfPresent(ObjectNode node, XmlNode source, String childName, String fieldName) {
        String value = source.childText(childName);
        if (value != null) {
            node.put(fieldName, value);
        }
    }

    private String typeForXsiType(String xsiType) {
        return switch (valueOrEmpty(xsiType)) {
            case "CatalogPredefinedItems" -> "Catalog";
            case "ChartOfAccountsPredefinedItems" -> "ChartOfAccounts";
            case "CalculationTypePredefinedItems", "ChartOfCalculationTypesPredefinedItems" -> "ChartOfCalculationTypes";
            case "PlanOfCharacteristicKindPredefinedItems", "ChartOfCharacteristicTypesPredefinedItems" ->
                    "ChartOfCharacteristicTypes";
            default -> null;
        };
    }

    private String objectName(Path predefinedXml) {
        Path objectDir = predefinedXml.getParent() != null ? predefinedXml.getParent().getParent() : null;
        if (objectDir == null || objectDir.getFileName() == null) {
            return "OraclePredefinedObject";
        }
        return objectDir.getFileName().toString();
    }

    private List<CanonObject> discover(Path source, int limit) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        if (Files.isRegularFile(root)) {
            candidates.add(root);
        } else {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("Predefined.xml"))
                        .forEach(candidates::add);
            }
        }
        candidates.sort(Comparator.comparing(Path::toString));
        List<CanonObject> result = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!isPredefinedData(candidate)) {
                continue;
            }
            result.add(new CanonObject(objectId(root, candidate), candidate));
            if (limit > 0 && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean isPredefinedData(Path path) {
        try {
            return "PredefinedData".equals(reader.parse(path).getRootElement());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String objectId(Path root, Path path) {
        Path rel = Files.isRegularFile(root) ? path.getFileName() : root.relativize(path.toAbsolutePath().normalize());
        return rel.toString().replace('\\', '/')
                .replace("/Ext/Predefined.xml", "")
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9_А-ЯЁа-яё.-]", "_");
    }

    private void validateOutputOutsideSource(Path source, Path out) {
        Path normalizedOut = out.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedSource)) {
            if (normalizedOut.equals(normalizedSource)) {
                throw new IllegalArgumentException("--out must be outside --source to keep canon Predefined.xml read-only: "
                        + normalizedOut);
            }
            return;
        }
        if (normalizedOut.equals(normalizedSource) || normalizedOut.startsWith(normalizedSource)) {
            throw new IllegalArgumentException("--out must be outside --source to keep canon Predefined.xml read-only: "
                    + normalizedOut);
        }
    }

    private List<CoverageMatrix.CoverageRow> coverage(CanonObject canon, CmpResult result) {
        try {
            XmlNode root = reader.parse(canon.path()).getRoot();
            boolean pass = result.status() == CmpStatus.PASS;
            ArrayNode constructs = mapper.createArrayNode();
            for (String construct : List.of("Name", "Code", "Description", "IsFolder")) {
                constructs.add(construct);
            }
            ObjectNode evidence = mapper.createObjectNode();
            evidence.put("items", root.children("Item").size());
            evidence.set("expressedFields", constructs);
            return List.of(new CoverageMatrix.CoverageRow("predefined-items",
                    "/PredefinedData/Item", OracleMode.CLI.id(), true, true, pass, pass,
                    pass ? "covered" : "cmp_or_exec_gap", canon.objectId() + ": " + evidence));
        } catch (Exception e) {
            return List.of(new CoverageMatrix.CoverageRow("predefined-items", "/PredefinedData/Item",
                    OracleMode.CLI.id(), true, true, false, false, "parse-error", e.getMessage()));
        }
    }

    private Map<String, OracleModeSummary> summaries(List<CmpResult> results) {
        int[] cli = new int[5];
        for (CmpResult result : results) {
            cli[0]++;
            switch (result.status()) {
                case PASS -> cli[1]++;
                case FAIL -> cli[2]++;
                case COVERAGE_GAP -> cli[3]++;
                case ERROR -> cli[4]++;
            }
        }
        Map<String, OracleModeSummary> summaries = new LinkedHashMap<>();
        summaries.put(OracleMode.CLI.id(), new OracleModeSummary(cli[0], cli[1], cli[2], cli[3], cli[4]));
        return summaries;
    }

    private CmpResult withContext(CmpResult result, Map<String, Object> context) {
        return new CmpResult(result.mode(), result.objectId(), result.status(), result.diffs(),
                result.normalized(), result.coverageGaps(), result.failureClass(), context);
    }

    private Map<String, Object> context(CommandPlan plan, Path commandPlanPath, Path executionResultPath,
                                        ExecutionResult execution) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reproduce_command", "xml-gen oracle predefined-data --source <src/xml-or-Predefined.xml> --out <out>");
        context.put("commandPlanPath", commandPlanPath.toString());
        context.put("executionResultPath", executionResultPath.toString());
        context.put("resultArtifact", plan.sandbox().resolve(plan.resultArtifact()).normalize().toString());
        context.put("commands", plan.steps().stream().map(CommandStep::command).toList());
        if (execution != null && !execution.steps().isEmpty()) {
            ExecutionStepResult last = execution.steps().get(execution.steps().size() - 1);
            context.put("lastCommandStep", last.id());
            context.put("stdoutPath", last.stdout().toString());
            context.put("stderrPath", last.stderr().toString());
        }
        return context;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private record CanonPredefinedData(String type, String objectName, String version, ArrayNode items) {}
}

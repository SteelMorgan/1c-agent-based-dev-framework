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

public class ExchangePlanContentOracleRunner {

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
            coverageRows.add(coverage(canon, result));
        }

        CoverageMatrix coverageMatrix = new CoverageMatrix(1, "exchange-plan-content", coverageRows);
        OracleReport report = new OracleReport(runId, SPEC_ID, "exchange-plan-content", summaries(results),
                results, List.of(), "coverage-matrix.json");
        new OracleReportWriter().write(runDir, report, coverageMatrix, List.of());
        mapper.writeValue(normalizedOut.resolve("latest-exchange-plan-content-oracle-report.json").toFile(), report);
        System.out.println("ExchangePlanContent oracle report: " + runDir.resolve("oracle-report.json"));
        return report;
    }

    private CmpResult runCliMode(CanonObject canon, Path sandbox) {
        try {
            Files.createDirectories(sandbox);
            CanonExchangeContent data = readCanon(canon);
            Path objectJson = sandbox.resolve("object.json");
            Path itemsJson = sandbox.resolve("exchange-content-items.json");
            writeObjectDsl(data, objectJson);
            writeItemsDsl(data, itemsJson);
            CommandPlan plan = commandPlan(canon, sandbox, objectJson, itemsJson, data);
            Path commandPlanPath = sandbox.resolve("CommandPlan.json");
            Path executionResultPath = sandbox.resolve("ExecutionResult.json");
            mapper.writeValue(commandPlanPath.toFile(), plan);
            ExecutionResult execution = new CommandPlanExecutor().execute(plan);
            mapper.writeValue(executionResultPath.toFile(), execution);
            Map<String, Object> context = context(plan, commandPlanPath, executionResultPath, execution);
            if (!execution.passed()) {
                DiffEntry diff = new DiffEntry("/command-plan/" + execution.failedStep(), "execution",
                        "passed", "failed", execution.message());
                return CmpResult.fail(OracleMode.CLI.id(), canon.objectId(), List.of(diff),
                        FailureClass.C_OR_EXEC_BUG, context);
            }
            CmpResult result = comparator.compareStructure(OracleMode.CLI.id(), canon.objectId(), canon.path(),
                    plan.sandbox().resolve(plan.resultArtifact()).normalize(),
                    IgnoreAllowlist.empty("exchange-plan-content"));
            return new CmpResult(result.mode(), result.objectId(), result.status(), result.diffs(),
                    result.normalized(), result.coverageGaps(), result.failureClass(), result.failureBucket(),
                    context);
        } catch (Exception e) {
            return CmpResult.error(OracleMode.CLI.id(), canon.objectId(), "exchange-plan-content-cli", e);
        }
    }

    private void writeObjectDsl(CanonExchangeContent data, Path objectJson) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "ExchangePlan");
        root.put("name", data.objectName());
        mapper.writeValue(objectJson.toFile(), root);
    }

    private void writeItemsDsl(CanonExchangeContent data, Path itemsJson) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.set("items", data.items());
        mapper.writeValue(itemsJson.toFile(), root);
    }

    private CommandPlan commandPlan(CanonObject canon, Path sandbox, Path objectJson, Path itemsJson,
                                    CanonExchangeContent data) {
        MetadataTypeRegistry.TypeDescriptor descriptor = MetadataTypeRegistry.get("ExchangePlan");
        Path result = Path.of("out").resolve(descriptor.directory()).resolve(data.objectName())
                .resolve("Ext").resolve("Content.xml");
        Path objectXml = Path.of("out").resolve(descriptor.directory()).resolve(data.objectName() + ".xml");
        return new CommandPlan(
                "exchange-plan-content/" + canon.objectId(),
                OracleMode.CLI.id(),
                sandbox,
                List.of(
                        new CommandStep("init-config",
                                List.of("xml-gen", "config", "init", "out", "OracleExchange",
                                        "--format-version", data.version()),
                                List.of(CommandAssertion.exitCode(0))),
                        new CommandStep("compile-object",
                                List.of("xml-gen", "meta", "compile", objectJson.toAbsolutePath().toString(), "out"),
                                List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists(objectXml.toString()))),
                        new CommandStep("add-exchange-content",
                                List.of("xml-gen", "meta", "edit", objectXml.toString(),
                                        "--op", "add-exchange-content", "--value", "@" + itemsJson.toAbsolutePath()),
                                List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists(result.toString()))),
                        new CommandStep("validate-content",
                                List.of("xml-gen", "validate", "--type", "xcf-body", result.toString()),
                                List.of(CommandAssertion.exitCodes(0, 2)))
                ),
                result.toString());
    }

    private CanonExchangeContent readCanon(CanonObject canon) throws Exception {
        XmlDocument document = reader.parse(canon.path());
        XmlNode root = document.getRoot();
        String version = root.attr("version") == null || root.attr("version").isBlank() ? "2.20" : root.attr("version");
        ArrayNode items = mapper.createArrayNode();
        for (XmlNode item : root.children("Item")) {
            String metadata = item.childText("Metadata");
            if (metadata == null || metadata.isBlank()) {
                continue;
            }
            ObjectNode row = mapper.createObjectNode();
            row.put("metadata", metadata);
            row.put("autoRecord", item.childText("AutoRecord") == null ? "Deny" : item.childText("AutoRecord"));
            items.add(row);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("ExchangePlanContent has no Item entries: " + canon.path());
        }
        return new CanonExchangeContent(objectName(canon.path()), version, items);
    }

    private List<CanonObject> discover(Path source, int limit) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        if (Files.isRegularFile(root)) {
            candidates.add(root);
        } else {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("Content.xml"))
                        .forEach(candidates::add);
            }
        }
        candidates.sort(Comparator.comparing(Path::toString));
        List<CanonObject> result = new ArrayList<>();
        for (Path candidate : candidates) {
            if (!isExchangePlanContent(candidate)) {
                continue;
            }
            result.add(new CanonObject(objectId(root, candidate), candidate));
            if (limit > 0 && result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private boolean isExchangePlanContent(Path path) {
        try {
            return "ExchangePlanContent".equals(reader.parse(path).getRootElement());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String objectName(Path contentXml) {
        Path objectDir = contentXml.getParent() != null ? contentXml.getParent().getParent() : null;
        return objectDir == null || objectDir.getFileName() == null
                ? "OracleExchangePlan" : objectDir.getFileName().toString();
    }

    private String objectId(Path root, Path path) {
        Path rel = Files.isRegularFile(root) ? path.getFileName() : root.relativize(path.toAbsolutePath().normalize());
        return rel.toString().replace('\\', '/')
                .replace("/Ext/Content.xml", "")
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9_А-ЯЁа-яё.-]", "_");
    }

    private CoverageMatrix.CoverageRow coverage(CanonObject canon, CmpResult result) {
        boolean pass = result.status() == CmpStatus.PASS;
        return new CoverageMatrix.CoverageRow("exchange-plan-content-items",
                "/ExchangePlanContent/Item", OracleMode.CLI.id(), true, true, pass, pass,
                pass ? "covered" : "cmp_or_exec_gap", canon.objectId());
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

    private Map<String, Object> context(CommandPlan plan, Path commandPlanPath, Path executionResultPath,
                                        ExecutionResult execution) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reproduce_command",
                "xml-gen oracle exchange-plan-content --source <src/xml-or-Content.xml> --out <out>");
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

    private void validateOutputOutsideSource(Path source, Path out) {
        Path normalizedOut = out.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedSource)) {
            if (normalizedOut.equals(normalizedSource)) {
                throw new IllegalArgumentException("--out must be outside --source to keep canon Content.xml read-only: "
                        + normalizedOut);
            }
            return;
        }
        if (normalizedOut.equals(normalizedSource) || normalizedOut.startsWith(normalizedSource)) {
            throw new IllegalArgumentException("--out must be outside --source to keep canon Content.xml read-only: "
                    + normalizedOut);
        }
    }

    private record CanonExchangeContent(String objectName, String version, ArrayNode items) {}
}

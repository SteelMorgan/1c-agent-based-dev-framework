package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.info.MxlDecompiler;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import io.github.onec.xmlgen.writer.MxlWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class MxlOracleRunner {

    private static final String SPEC_ID = "SPEC-177";
    private final XmlStructureReader reader = new XmlStructureReader();
    private final MxlDecompiler decompiler = new MxlDecompiler();
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final OracleComparator comparator = new OracleComparator();

    public OracleReport run(OracleOptions options) throws Exception {
        validateOutputOutsideSource(options.source(), options.out());
        Path out = options.out().toAbsolutePath().normalize();
        Files.createDirectories(out);
        String runId = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(':', '-');
        Path runDir = out.resolve(runId);
        Files.createDirectories(runDir);
        IgnoreAllowlist allowlist = IgnoreAllowlist.load(options.allowlist(), "mxl");
        List<CanonObject> corpus = discover(options.source(), options.limit(), options.includeAll());
        Map<Path, String> beforeHashes = hashCanon(corpus);

        List<CmpResult> results = new ArrayList<>();
        List<CoverageMatrix.CoverageRow> coverageRows = new ArrayList<>();
        boolean runDsl = "both".equals(options.mode()) || "dsl".equals(options.mode());
        boolean runCli = "both".equals(options.mode()) || "cli".equals(options.mode());
        for (CanonObject canon : corpus) {
            if (runDsl) {
                CmpResult result = runDslMode(canon, runDir.resolve(canon.objectId()).resolve("dsl"));
                results.add(result);
                coverageRows.addAll(coverage(canon, OracleMode.DSL.id()));
            }
            if (runCli) {
                CmpResult result = runCliMode(canon, runDir.resolve(canon.objectId()).resolve("cli"), allowlist);
                results.add(result);
                coverageRows.addAll(coverage(canon, OracleMode.CLI.id()));
            }
        }
        results.addAll(checkCanonUnchanged(beforeHashes));

        List<CoverageMatrix.CoverageRow> matrixRows = aggregateCoverage(coverageRows);
        CoverageMatrix coverageMatrix = new CoverageMatrix(1, "mxl", matrixRows);
        results.addAll(coverageGapResults(matrixRows));
        List<Map<String, Object>> xgCandidates = xgCandidates(results, options.xgRegistry());
        OracleReport report = new OracleReport(runId, SPEC_ID, "mxl", summaries(results), results,
                xgCandidates, "coverage-matrix.json");
        new OracleReportWriter().write(runDir, report, coverageMatrix, xgCandidates);
        mapper.writeValue(out.resolve("latest-oracle-report.json").toFile(), report);
        System.out.println("Oracle report: " + runDir.resolve("oracle-report.json"));
        return report;
    }

    private CmpResult runDslMode(CanonObject canon, Path sandbox) {
        try {
            Files.createDirectories(sandbox);
            Path dslJson = sandbox.resolve("dsl.json");
            Path generated = sandbox.resolve("generated").resolve("Template.xml");
            Files.createDirectories(generated.getParent());
            XmlDocument doc = reader.parse(canon.path());
            decompiler.decompile(doc, dslJson);
            MxlDsl dsl = mapper.readValue(dslJson.toFile(), MxlDsl.class);
            new MxlWriter(OutputFormat.DESIGNER).create(dsl, generated);
            CmpResult result = comparator.compareBytes(OracleMode.DSL.id(), canon.objectId(), canon.path(), generated);
            return withContext(result, Map.of(
                    "reproduce_command", "xml-gen mxl decompile \"" + canon.path() + "\" dsl.json && xml-gen mxl compile dsl.json Template.xml",
                    "dslPath", dslJson.toString(),
                    "generatedPath", generated.toString()
            ));
        } catch (Exception e) {
            return CmpResult.error(OracleMode.DSL.id(), canon.objectId(), "dsl-mode", e);
        }
    }

    private CmpResult runCliMode(CanonObject canon, Path sandbox, IgnoreAllowlist allowlist) {
        try {
            Files.createDirectories(sandbox);
            Path dslJson = sandbox.resolve("dsl.json");
            XmlDocument doc = reader.parse(canon.path());
            decompiler.decompile(doc, dslJson);
            CommandPlan plan = commandPlan(canon, sandbox, dslJson);
            Path commandPlanPath = sandbox.resolve("CommandPlan.json");
            Path executionResultPath = sandbox.resolve("ExecutionResult.json");
            mapper.writeValue(commandPlanPath.toFile(), plan);
            ExecutionResult execution = new CommandPlanExecutor().execute(plan);
            mapper.writeValue(executionResultPath.toFile(), execution);
            if (!execution.passed()) {
                DiffEntry diff = new DiffEntry("/command-plan/" + execution.failedStep(), "execution",
                        "passed", "failed", execution.message());
                Map<String, Object> context = cliContext(plan, commandPlanPath, executionResultPath, execution, "execution");
                return CmpResult.fail(OracleMode.CLI.id(), canon.objectId(), List.of(diff),
                        FailureClass.C_OR_EXEC_BUG, context);
            }
            CmpResult result = comparator.compareStructure(OracleMode.CLI.id(), canon.objectId(), canon.path(),
                    plan.sandbox().resolve(plan.resultArtifact()).normalize(), allowlist);
            return withContext(result, cliContext(plan, commandPlanPath, executionResultPath, execution, "compare-structure"));
        } catch (Exception e) {
            return CmpResult.error(OracleMode.CLI.id(), canon.objectId(), "cli-mode", e);
        }
    }

    private CommandPlan commandPlan(CanonObject canon, Path sandbox, Path dslJson) {
        String host = "OracleMxlHost";
        String template = "ПФ_Канон";
        Path result = Path.of("out").resolve(host).resolve("Templates").resolve(template).resolve("Ext")
                .resolve("Template.xml");
        return new CommandPlan(
                "mxl-epf-template/" + canon.objectId(),
                OracleMode.CLI.id(),
                sandbox,
                List.of(
                        new CommandStep("init-epf",
                                List.of("xml-gen", "epf", "init", "--name", host, "out/"),
                                List.of(CommandAssertion.exitCode(0))),
                        new CommandStep("add-template",
                                List.of("xml-gen", "epf", "add-template", "--epf", host, "--name", template,
                                        "--type", "SpreadsheetDocument", "out/"),
                                List.of(CommandAssertion.exitCode(0), CommandAssertion.fileExists(result.toString()))),
                        new CommandStep("compile-mxl",
                                List.of("xml-gen", "mxl", "compile", dslJson.toAbsolutePath().toString(),
                                        result.toString()),
                                List.of(CommandAssertion.exitCode(0))),
                        new CommandStep("validate",
                                List.of("xml-gen", "validate", "--type", "mxl", result.toString()),
                                List.of(CommandAssertion.exitCodes(0, 2)))
                ),
                result.toString()
        );
    }

    private List<CanonObject> discover(Path source, int limit, boolean includeAll) throws IOException {
        Path root = source.toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        boolean singleFile = Files.isRegularFile(root);
        if (singleFile) {
            candidates.add(root);
        } else {
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals("Template.xml"))
                        .forEach(candidates::add);
            }
        }
        candidates.sort(Comparator.comparing(Path::toString));
        List<CanonObject> result = new ArrayList<>();
        for (Path path : candidates) {
            if (isMxl(path) && (singleFile || includeAll || isDemoPath(path))) {
                result.add(new CanonObject(objectId(root, path), path));
                if (limit > 0 && result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    private void validateOutputOutsideSource(Path source, Path out) {
        Path normalizedOut = out.toAbsolutePath().normalize();
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (Files.isRegularFile(normalizedSource)) {
            if (normalizedOut.equals(normalizedSource)) {
                throw new IllegalArgumentException("--out must be outside --source to keep canon src/xml read-only: "
                        + normalizedOut);
            }
            return;
        }
        if (normalizedSource != null
                && (normalizedOut.equals(normalizedSource) || normalizedOut.startsWith(normalizedSource))) {
            throw new IllegalArgumentException("--out must be outside --source to keep canon src/xml read-only: "
                    + normalizedOut);
        }
    }

    private boolean isDemoPath(Path path) {
        return path.toString().contains("_Демо");
    }

    private boolean isMxl(Path path) {
        try {
            return "document".equals(reader.parse(path).getRootElement());
        } catch (Exception ignored) {
            return false;
        }
    }

    private String objectId(Path root, Path path) {
        Path rel = Files.isRegularFile(root) ? path.getFileName() : root.relativize(path.toAbsolutePath().normalize());
        return rel.toString().replace('\\', '/')
                .replace("/Ext/Template.xml", "")
                .replace('/', '_')
                .replaceAll("[^A-Za-z0-9_А-ЯЁа-яё.-]", "_");
    }

    private Map<Path, String> hashCanon(List<CanonObject> corpus) throws Exception {
        Map<Path, String> hashes = new HashMap<>();
        for (CanonObject object : corpus) {
            hashes.put(object.path(), sha256(object.path()));
        }
        return hashes;
    }

    private List<CmpResult> checkCanonUnchanged(Map<Path, String> before) throws Exception {
        List<CmpResult> changed = new ArrayList<>();
        for (Map.Entry<Path, String> entry : before.entrySet()) {
            String after = sha256(entry.getKey());
            if (!entry.getValue().equals(after)) {
                DiffEntry diff = new DiffEntry(entry.getKey().toString(), "canon-hash",
                        entry.getValue(), after, "source canon changed during oracle run");
                changed.add(CmpResult.fail("guard", entry.getKey().toString(), List.of(diff), FailureClass.CANON_CHANGED));
            }
        }
        return changed;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(Files.readAllBytes(path));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private List<CoverageMatrix.CoverageRow> coverage(CanonObject canon, String mode) {
        try {
            XmlNode root = reader.parse(canon.path()).getRoot();
            List<CoverageMatrix.CoverageRow> rows = new ArrayList<>();
            rows.add(coverageRow("columns", "/document/columns", mode, count(root, "columns"), canon.objectId()));
            rows.add(coverageRow("rowsItem", "/document/rowsItem", mode, count(root, "rowsItem"), canon.objectId()));
            rows.add(coverageRow("merge", "/document/merge", mode, count(root, "merge"), canon.objectId()));
            rows.add(coverageRow("format", "/document/format", mode, count(root, "format"), canon.objectId()));
            rows.add(coverageRow("drawing", "/document/drawing", mode, count(root, "drawing"), canon.objectId()));
            rows.add(coverageRow("picture", "/document/picture", mode, count(root, "picture"), canon.objectId()));
            return rows;
        } catch (Exception e) {
            return List.of(new CoverageMatrix.CoverageRow("parse-error", "/", mode,
                    true, false, false, false, "d_gap", e.getMessage()));
        }
    }

    private CoverageMatrix.CoverageRow coverageRow(String construct, String path, String mode, int seen,
                                                   String objectId) {
        boolean present = seen > 0;
        boolean supported = present;
        String status = present ? "covered" : "not_in_corpus";
        return new CoverageMatrix.CoverageRow(construct, path, mode, present, supported, supported, supported,
                status, objectId + ": seen=" + seen);
    }

    private int count(XmlNode node, String name) {
        int total = node.getName().equals(name) ? 1 : 0;
        for (XmlNode child : node.getChildren()) {
            total += count(child, name);
        }
        return total;
    }

    private List<CoverageMatrix.CoverageRow> aggregateCoverage(List<CoverageMatrix.CoverageRow> rows) {
        Map<String, CoverageAggregate> sums = new LinkedHashMap<>();
        for (CoverageMatrix.CoverageRow row : rows) {
            String key = row.construct() + "|" + row.mode();
            CoverageAggregate value = sums.computeIfAbsent(key, k -> new CoverageAggregate(row.construct(), row.path(), row.mode()));
            value.presentInDemo |= row.presentInDemo();
            value.expressibleByD |= row.expressibleByD();
            value.emittedByCOrExec |= row.emittedByCOrExec();
            value.checkedByCmp |= row.checkedByCmp();
            value.evidence.add(row.evidence());
        }
        List<CoverageMatrix.CoverageRow> result = new ArrayList<>();
        for (CoverageAggregate aggregate : sums.values()) {
            String status;
            if (!aggregate.presentInDemo) {
                status = "not_in_corpus";
            } else if (!aggregate.expressibleByD) {
                status = "d_gap";
            } else if (!aggregate.emittedByCOrExec) {
                status = "c_gap";
            } else if (!aggregate.checkedByCmp) {
                status = "cmp_gap";
            } else {
                status = "covered";
            }
            result.add(new CoverageMatrix.CoverageRow(aggregate.construct, aggregate.path, aggregate.mode,
                    aggregate.presentInDemo, aggregate.expressibleByD, aggregate.emittedByCOrExec,
                    aggregate.checkedByCmp, status, String.join("; ", aggregate.evidence)));
        }
        return result;
    }

    private List<CmpResult> coverageGapResults(List<CoverageMatrix.CoverageRow> rows) {
        List<CmpResult> gaps = new ArrayList<>();
        for (CoverageMatrix.CoverageRow row : rows) {
            if ("covered".equals(row.status())) {
                continue;
            }
            CoverageGap gap = new CoverageGap(row.mode(), "coverage/" + row.construct(), row.construct(),
                    row.path(), row.status());
            gaps.add(CmpResult.coverageGap(row.mode(), "coverage/" + row.construct(), List.of(gap),
                    Map.of("coverageStatus", row.status(), "coverageEvidence", row.evidence())));
        }
        return gaps;
    }

    private Map<String, OracleModeSummary> summaries(List<CmpResult> results) {
        Map<String, int[]> counters = new LinkedHashMap<>();
        counters.put("dsl", new int[5]);
        counters.put("cli", new int[5]);
        for (CmpResult result : results) {
            int[] c = counters.computeIfAbsent(result.mode(), k -> new int[5]);
            c[0]++;
            switch (result.status()) {
                case PASS -> c[1]++;
                case FAIL -> c[2]++;
                case COVERAGE_GAP -> c[3]++;
                case ERROR -> c[4]++;
            }
        }
        Map<String, OracleModeSummary> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, int[]> entry : counters.entrySet()) {
            int[] c = entry.getValue();
            summaries.put(entry.getKey(), new OracleModeSummary(c[0], c[1], c[2], c[3], c[4]));
        }
        return summaries;
    }

    private List<Map<String, Object>> xgCandidates(List<CmpResult> results, Path registry) throws IOException {
        String registryText = registry != null && Files.exists(registry) ? Files.readString(registry) : "";
        int next = nextXg(registryText);
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (CmpResult result : results) {
            if (result.status() == CmpStatus.PASS) {
                continue;
            }
            DiffEntry first = primaryCandidateEntry(result);
            String key = xgKey(result, first);
            Map<String, Object> existing = byKey.get(key);
            if (existing != null) {
                @SuppressWarnings("unchecked")
                List<String> objects = (List<String>) existing.get("objects");
                objects.add(result.objectId());
                continue;
            }
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("candidate_id", String.format(Locale.ROOT, "XG-%02d", next++));
            candidate.put("mode", result.mode());
            candidate.put("object", result.objectId());
            candidate.put("objects", new ArrayList<>(List.of(result.objectId())));
            candidate.put("stage", first.kind());
            candidate.put("failureClass", result.failureClass().name());
            candidate.put("symptom", first.message());
            candidate.put("xdto_path", first.path());
            candidate.put("expected", first.expected());
            candidate.put("actual", first.actual());
            candidate.put("severity", result.status() == CmpStatus.ERROR ? "High" : "Medium");
            candidate.put("source", "TASK-177");
            candidate.putAll(result.context());
            String duplicate = duplicateOf(registryText, first);
            candidate.put("duplicate_of", duplicate);
            if (duplicate != null) {
                candidate.put("append_source_patch", duplicate + ": append '; TASK-177' to source/evidence cell");
            }
            candidate.put("proposed_registry_row", "|" + candidate.get("candidate_id")
                    + "|MXL oracle " + result.mode() + "|" + first.message() + "|TASK-177|");
            byKey.put(key, candidate);
            candidates.add(candidate);
        }
        return candidates;
    }

    private DiffEntry primaryCandidateEntry(CmpResult result) {
        if (!result.diffs().isEmpty()) {
            return result.diffs().get(0);
        }
        if (!result.coverageGaps().isEmpty()) {
            CoverageGap gap = result.coverageGaps().get(0);
            return new DiffEntry(gap.path(), "coverage", "covered", gap.reason(),
                    gap.construct() + ": " + gap.reason());
        }
        return new DiffEntry("/", "unknown", "", "", result.status().name());
    }

    private String xgKey(CmpResult result, DiffEntry diff) {
        return "mxl|" + result.mode() + "|" + diff.kind() + "|" + result.failureClass()
                + "|" + normalize(diff.message()) + "|" + diff.path();
    }

    private int nextXg(String registryText) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("XG-(\\d+)").matcher(registryText);
        int max = 49;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max + 1;
    }

    private String duplicateOf(String registryText, DiffEntry diff) {
        if (registryText.isBlank() || diff.message() == null || diff.message().isBlank()) {
            return null;
        }
        String needle = normalize(diff.message());
        for (String line : registryText.split("\\R")) {
            String normalizedLine = normalize(line);
            if (!needle.isBlank() && normalizedLine.contains(needle)) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("XG-(\\d+)").matcher(line);
                return m.find() ? "XG-" + m.group(1) : "manual-review";
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private CmpResult withContext(CmpResult result, Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return result;
        }
        return new CmpResult(result.mode(), result.objectId(), result.status(), result.diffs(),
                result.normalized(), result.coverageGaps(), result.failureClass(), context);
    }

    private Map<String, Object> cliContext(CommandPlan plan, Path commandPlanPath, Path executionResultPath,
                                           ExecutionResult execution, String failureStage) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("reproduce_command", "xml-gen oracle mxl --source <src/xml-or-Template.xml> --mode cli");
        context.put("failureStage", failureStage);
        context.put("commandPlanPath", commandPlanPath.toString());
        context.put("executionResultPath", executionResultPath.toString());
        context.put("resultArtifact", plan.sandbox().resolve(plan.resultArtifact()).normalize().toString());
        if (execution != null && !execution.steps().isEmpty()) {
            ExecutionStepResult last = execution.steps().get(execution.steps().size() - 1);
            context.put("lastCommandStep", last.id());
            context.put("commandPlanStep", "execution".equals(failureStage) ? last.id() : failureStage);
            context.put("stdoutPath", last.stdout().toString());
            context.put("stderrPath", last.stderr().toString());
        }
        return context;
    }

    private static final class CoverageAggregate {
        final String construct;
        final String path;
        final String mode;
        boolean presentInDemo;
        boolean expressibleByD;
        boolean emittedByCOrExec;
        boolean checkedByCmp;
        final List<String> evidence = new ArrayList<>();

        CoverageAggregate(String construct, String path, String mode) {
            this.construct = construct;
            this.path = path;
            this.mode = mode;
        }
    }
}

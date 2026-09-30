package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class RuleMiningReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final RuleCandidateReducer reducer = new RuleCandidateReducer();

    public void write(Path out, RuleMiningReport report) throws IOException {
        write(out, report, RuleCandidateReducer.DEFAULT_DIGEST_LIMIT);
    }

    public void write(Path out, RuleMiningReport report, int digestLimit) throws IOException {
        write(out, report, digestLimit, RuleDispositionRegistry.empty());
    }

    public void write(Path out, RuleMiningReport report, int digestLimit,
                      RuleDispositionRegistry dispositionRegistry) throws IOException {
        Files.createDirectories(out);
        RuleDigest digest = reducer.reduce(report, digestLimit, dispositionRegistry);
        mapper.writeValue(out.resolve("rule-mining-report.json").toFile(), report);
        mapper.writeValue(out.resolve("rule-candidates.json").toFile(), report.candidates());
        mapper.writeValue(out.resolve("rule-digest.json").toFile(), digest);
        Files.writeString(out.resolve("rule-mining-summary.md"), markdown(report, digest), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("rule-digest.md"), digestMarkdown(digest), StandardCharsets.UTF_8);
    }

    private String markdown(RuleMiningReport report, RuleDigest digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("# xml-gen rule mining summary\n\n");
        sb.append("- run_id: `").append(report.runId()).append("`\n");
        sb.append("- source_root: `").append(report.sourceRoot()).append("`\n");
        sb.append("- files_scanned: ").append(report.filesScanned()).append("\n");
        sb.append("- files_parsed: ").append(report.filesParsed()).append("\n");
        sb.append("- parse_errors: ").append(report.parseErrors()).append("\n");
        sb.append("- facts: ").append(report.factCount()).append("\n");
        sb.append("- fact_samples: ").append(report.factSamples().size()).append("\n");
        sb.append("- candidates: ").append(report.candidates().size()).append("\n\n");
        sb.append("- digest_bundles: ").append(digest.digestCount()).append("/")
                .append(digest.bundleCountBeforeLimit()).append("\n");
        sb.append("- digest_limit: ").append(digest.digestLimit()).append("\n\n");

        sb.append("## Noise Summary\n\n");
        sb.append("| Reason | Count |\n");
        sb.append("|---|---:|\n");
        for (Map.Entry<String, Integer> entry : digest.noiseSummary().entrySet()) {
            sb.append("| `").append(entry.getKey()).append("` | ").append(entry.getValue()).append(" |\n");
        }
        sb.append("\n");

        sb.append("## Buckets\n\n");
        sb.append("| Bucket | Documents | Facts | Candidates |\n");
        sb.append("|---|---:|---:|---:|\n");
        for (Map.Entry<String, RuleMiningReport.BucketSummary> entry : report.buckets().entrySet()) {
            RuleMiningReport.BucketSummary s = entry.getValue();
            sb.append("| `").append(entry.getKey()).append("` | ")
                    .append(s.documents()).append(" | ")
                    .append(s.facts()).append(" | ")
                    .append(s.candidates()).append(" |\n");
        }

        sb.append("\n## Top Candidates\n\n");
        if (report.candidates().isEmpty()) {
            sb.append("No candidates.\n");
            return sb.toString();
        }
        sb.append("| Id | Kind | Bucket | Confidence | Support | Subject |\n");
        sb.append("|---|---|---|---|---:|---|\n");
        report.candidates().stream().limit(80).forEach(c -> sb.append("| `")
                .append(c.id()).append("` | `")
                .append(c.kind()).append("` | `")
                .append(c.bucket()).append("` | `")
                .append(c.confidence()).append("` | ")
                .append(c.support()).append("/").append(c.total()).append(" | `")
                .append(c.subject().replace("|", "\\|")).append("` |\n"));
        return sb.toString();
    }

    private String digestMarkdown(RuleDigest digest) {
        StringBuilder sb = new StringBuilder();
        sb.append("# xml-gen rule digest\n\n");
        sb.append("- run_id: `").append(digest.runId()).append("`\n");
        sb.append("- raw_candidates: ").append(digest.rawCandidateCount()).append("\n");
        sb.append("- bundles_before_limit: ").append(digest.bundleCountBeforeLimit()).append("\n");
        sb.append("- digest_count: ").append(digest.digestCount()).append("\n");
        sb.append("- digest_limit: ").append(digest.digestLimit()).append("\n\n");

        sb.append("## Raw Candidate Counts\n\n");
        appendCounts(sb, "By kind", digest.rawByKind());
        appendCounts(sb, "By confidence", digest.rawByConfidence());
        appendCounts(sb, "Noise", digest.noiseSummary());
        appendCounts(sb, "Feedback", digest.feedbackSummary());

        sb.append("## Digest Bundles\n\n");
        if (digest.bundles().isEmpty()) {
            sb.append("No digest bundles.\n");
            return sb.toString();
        }
        sb.append("| Id | Key | Score | Bucket | Confidence | Support | Kinds | Subject |\n");
        sb.append("|---|---|---:|---|---|---:|---|---|\n");
        for (RuleBundle b : digest.bundles()) {
            sb.append("| `").append(b.id()).append("` | ")
                    .append("`").append(b.key().replace("|", "\\|")).append("` | ")
                    .append(b.score()).append(" | `")
                    .append(b.bucket()).append("` | `")
                    .append(b.confidence()).append("` | ")
                    .append(b.support()).append("/").append(b.total()).append(" | `")
                    .append(String.join(",", b.kinds())).append("` | `")
                    .append(b.generalizedSubject().replace("|", "\\|")).append("` |\n");
        }
        return sb.toString();
    }

    private void appendCounts(StringBuilder sb, String title, Map<String, Integer> counts) {
        sb.append("### ").append(title).append("\n\n");
        if (counts.isEmpty()) {
            sb.append("No entries.\n\n");
            return;
        }
        List<Map.Entry<String, Integer>> entries = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();
        sb.append("| Key | Count |\n");
        sb.append("|---|---:|\n");
        for (Map.Entry<String, Integer> entry : entries) {
            sb.append("| `").append(entry.getKey()).append("` | ").append(entry.getValue()).append(" |\n");
        }
        sb.append("\n");
    }
}

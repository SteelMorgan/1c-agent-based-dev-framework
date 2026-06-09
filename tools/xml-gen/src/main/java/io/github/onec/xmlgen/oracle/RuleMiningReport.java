package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record RuleMiningReport(
        String runId,
        String sourceRoot,
        int filesScanned,
        int filesParsed,
        int parseErrors,
        int factCount,
        Map<String, BucketSummary> buckets,
        List<CanonicalFact> factSamples,
        List<RuleCandidate> candidates,
        List<Map<String, Object>> errors
) {
    public record BucketSummary(
            int documents,
            int facts,
            int candidates,
            List<String> examples
    ) {}
}

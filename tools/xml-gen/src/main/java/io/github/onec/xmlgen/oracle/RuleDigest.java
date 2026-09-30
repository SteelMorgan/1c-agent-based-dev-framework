package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record RuleDigest(
        String runId,
        String sourceRoot,
        int rawCandidateCount,
        int bundleCountBeforeLimit,
        int digestCount,
        int digestLimit,
        Map<String, Integer> rawByKind,
        Map<String, Integer> rawByConfidence,
        Map<String, Integer> noiseSummary,
        Map<String, Integer> feedbackSummary,
        List<RuleBundle> bundles
) {}

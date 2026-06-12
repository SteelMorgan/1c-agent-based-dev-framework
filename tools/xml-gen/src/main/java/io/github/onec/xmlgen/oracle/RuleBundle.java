package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record RuleBundle(
        String id,
        String key,
        String bucket,
        String subject,
        String generalizedSubject,
        double score,
        RuleConfidence confidence,
        int support,
        int total,
        double ratio,
        List<String> kinds,
        List<String> candidateIds,
        List<String> examples,
        String rationale,
        Map<String, Object> details
) {}

package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record RuleCandidate(
        String id,
        String kind,
        String bucket,
        RuleConfidence confidence,
        int support,
        int total,
        String subject,
        String rule,
        Map<String, Object> details,
        List<String> examples
) {}

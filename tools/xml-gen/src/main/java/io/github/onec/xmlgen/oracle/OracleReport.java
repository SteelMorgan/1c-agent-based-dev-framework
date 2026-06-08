package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record OracleReport(
        String runId,
        String specId,
        String pilot,
        Map<String, OracleModeSummary> modes,
        List<CmpResult> objects,
        List<Map<String, Object>> xgCandidates,
        String coverageMatrix
) {}

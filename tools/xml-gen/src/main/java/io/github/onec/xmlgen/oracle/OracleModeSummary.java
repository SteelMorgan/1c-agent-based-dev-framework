package io.github.onec.xmlgen.oracle;

public record OracleModeSummary(
        int checked,
        int pass,
        int fail,
        int coverageGaps,
        int error
) {}

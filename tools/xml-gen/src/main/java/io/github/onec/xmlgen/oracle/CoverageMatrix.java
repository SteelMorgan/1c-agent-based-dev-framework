package io.github.onec.xmlgen.oracle;

import java.util.List;

public record CoverageMatrix(
        int version,
        String domain,
        List<CoverageRow> rows
) {
    public record CoverageRow(
            String construct,
            String path,
            String mode,
            boolean presentInDemo,
            boolean expressibleByD,
            boolean emittedByCOrExec,
            boolean checkedByCmp,
            String status,
            String evidence
    ) {}
}

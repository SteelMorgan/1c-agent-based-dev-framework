package io.github.onec.xmlgen.oracle;

public record CoverageGap(
        String mode,
        String objectId,
        String construct,
        String path,
        String reason
) {}

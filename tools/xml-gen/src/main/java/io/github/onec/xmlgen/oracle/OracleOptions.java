package io.github.onec.xmlgen.oracle;

import java.nio.file.Path;

public record OracleOptions(
        Path source,
        Path out,
        String mode,
        int limit,
        Path allowlist,
        Path xgRegistry,
        boolean includeAll
) {}

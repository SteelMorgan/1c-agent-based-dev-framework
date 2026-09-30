package io.github.onec.xmlgen.oracle;

import java.nio.file.Path;
import java.util.List;

public record ExecutionStepResult(
        String id,
        List<String> command,
        int exitCode,
        Path stdout,
        Path stderr,
        boolean passed,
        String message
) {}

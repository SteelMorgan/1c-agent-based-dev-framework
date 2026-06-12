package io.github.onec.xmlgen.oracle;

import java.util.List;

public record CommandPlan(
        String planId,
        String mode,
        java.nio.file.Path sandbox,
        List<CommandStep> steps,
        String resultArtifact
) {}

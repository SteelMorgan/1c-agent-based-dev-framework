package io.github.onec.xmlgen.oracle;

import java.util.List;

public record ExecutionResult(
        CommandPlan plan,
        List<ExecutionStepResult> steps,
        boolean passed,
        String failedStep,
        String message
) {}

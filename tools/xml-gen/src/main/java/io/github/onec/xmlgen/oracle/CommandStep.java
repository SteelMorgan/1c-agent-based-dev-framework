package io.github.onec.xmlgen.oracle;

import java.util.List;

public record CommandStep(
        String id,
        List<String> command,
        List<CommandAssertion> assertions
) {}

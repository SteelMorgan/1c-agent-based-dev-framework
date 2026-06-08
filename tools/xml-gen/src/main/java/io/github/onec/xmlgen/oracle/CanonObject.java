package io.github.onec.xmlgen.oracle;

import java.nio.file.Path;

public record CanonObject(
        String objectId,
        Path path
) {}

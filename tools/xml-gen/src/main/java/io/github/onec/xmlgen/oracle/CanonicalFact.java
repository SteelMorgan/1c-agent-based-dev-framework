package io.github.onec.xmlgen.oracle;

import java.util.Map;

public record CanonicalFact(
        String bucket,
        String file,
        String path,
        String kind,
        String name,
        String value,
        Map<String, Object> context
) {}

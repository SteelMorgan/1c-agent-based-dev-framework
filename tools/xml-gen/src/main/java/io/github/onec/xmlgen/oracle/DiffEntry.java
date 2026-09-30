package io.github.onec.xmlgen.oracle;

public record DiffEntry(
        String path,
        String kind,
        String expected,
        String actual,
        String message
) {}

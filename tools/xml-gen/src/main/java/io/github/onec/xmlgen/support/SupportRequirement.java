package io.github.onec.xmlgen.support;

public enum SupportRequirement {
    EDITABLE,
    REMOVED;

    public static SupportRequirement fromCli(String value) {
        if (value == null || value.isBlank() || "editable".equalsIgnoreCase(value)) {
            return EDITABLE;
        }
        if ("removed".equalsIgnoreCase(value) || "off-support".equalsIgnoreCase(value)) {
            return REMOVED;
        }
        throw new IllegalArgumentException("--require must be one of: editable, removed");
    }

    public String cliName() {
        return this == REMOVED ? "removed" : "editable";
    }
}

package io.github.onec.xmlgen.oracle;

public enum OracleMode {
    DSL("dsl"),
    CLI("cli");

    private final String id;

    OracleMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

package io.github.onec.xmlgen.oracle;

import java.util.List;

public record CommandAssertion(
        String type,
        String path,
        Integer value,
        List<Integer> values
) {
    public static CommandAssertion exitCode(int value) {
        return new CommandAssertion("exitCode", null, value, null);
    }

    public static CommandAssertion exitCodes(int... values) {
        return new CommandAssertion("exitCodes", null, null,
                java.util.Arrays.stream(values).boxed().toList());
    }

    public static CommandAssertion fileExists(String path) {
        return new CommandAssertion("fileExists", path, null, null);
    }
}

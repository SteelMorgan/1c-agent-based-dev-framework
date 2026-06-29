package io.github.onec.xmlgen.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParentConfigurationsParser {
    private static final Pattern HEADER = Pattern.compile("^\\s*\\{\\s*6\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,", Pattern.DOTALL);
    private static final Pattern OBJECT_RECORD = Pattern.compile(
            "(?i)([0-2])\\s*,\\s*0\\s*,\\s*([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");

    private ParentConfigurationsParser() {
    }

    public static Optional<SupportState> parseIfPresent(Path binPath) throws IOException {
        if (binPath == null || !Files.isRegularFile(binPath)) {
            return Optional.empty();
        }

        byte[] bytes = Files.readAllBytes(binPath);
        if (bytes.length <= 32) {
            return Optional.empty();
        }
        int offset = hasUtf8Bom(bytes) ? 3 : 0;
        String text = new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);

        Matcher header = HEADER.matcher(text);
        if (!header.find()) {
            throw new IOException("Invalid ParentConfigurations.bin header: " + binPath);
        }

        int globalFlag = Integer.parseInt(header.group(1));
        int vendorCount = Integer.parseInt(header.group(2));
        Map<String, Integer> rules = new HashMap<>();

        Matcher record = OBJECT_RECORD.matcher(text);
        while (record.find()) {
            int rule = Integer.parseInt(record.group(1));
            String uuid = record.group(2).toLowerCase();
            rules.merge(uuid, rule, Math::min);
        }

        return Optional.of(new SupportState(globalFlag, vendorCount, rules));
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }
}

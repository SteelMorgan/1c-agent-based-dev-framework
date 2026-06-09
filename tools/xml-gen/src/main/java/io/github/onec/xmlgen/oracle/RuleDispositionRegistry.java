package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RuleDispositionRegistry(List<Entry> entries) {

    public static RuleDispositionRegistry empty() {
        return new RuleDispositionRegistry(List.of());
    }

    public static RuleDispositionRegistry load(Path path) throws IOException {
        if (path == null) {
            return empty();
        }
        if (!Files.exists(path)) {
            throw new IOException("Disposition file does not exist: " + path);
        }
        return new ObjectMapper().readValue(path.toFile(), RuleDispositionRegistry.class);
    }

    public Map<String, Entry> byKey() {
        return entries == null ? Map.of() : entries.stream()
                .filter(e -> e.key() != null && !e.key().isBlank())
                .filter(e -> !isGlob(e.key()))
                .collect(Collectors.toMap(Entry::key, Function.identity(), (a, b) -> b));
    }

    public Entry find(String key) {
        if (key == null || entries == null || entries.isEmpty()) {
            return null;
        }
        Entry exact = byKey().get(key);
        if (exact != null) {
            return exact;
        }
        for (Entry entry : entries) {
            String pattern = entry.key();
            if (pattern == null || pattern.isBlank() || !isGlob(pattern)) {
                continue;
            }
            if (globMatches(pattern, key)) {
                return entry;
            }
        }
        return null;
    }

    public boolean suppress(Entry entry) {
        if (entry == null || entry.status() == null) {
            return false;
        }
        String status = entry.status().toLowerCase(Locale.ROOT);
        return status.equals("promoted")
                || status.equals("implemented")
                || status.equals("accepted")
                || status.equals("rejected")
                || status.equals("ignored");
    }

    private static boolean isGlob(String key) {
        return key.indexOf('*') >= 0 || key.indexOf('?') >= 0;
    }

    private static boolean globMatches(String glob, String value) {
        StringBuilder regex = new StringBuilder(glob.length() * 2);
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                default -> {
                    if ("\\.[]{}()+-^$|".indexOf(ch) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(ch);
                }
            }
        }
        return Pattern.matches(regex.toString(), value);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(
            String key,
            String status,
            String reason,
            String target,
            String updatedAt
    ) {}
}

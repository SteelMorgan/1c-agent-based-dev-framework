package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public record IgnoreAllowlist(
        int version,
        String domain,
        List<Entry> entries
) {
    public static IgnoreAllowlist empty(String domain) {
        return new IgnoreAllowlist(1, domain, List.of());
    }

    public static IgnoreAllowlist load(Path path, String domain) throws IOException {
        if (path == null || !Files.exists(path)) {
            return empty(domain);
        }
        return new ObjectMapper().readValue(path.toFile(), IgnoreAllowlist.class);
    }

    public boolean ignores(String mode, String path) {
        return matches(mode, path, "ignore");
    }

    public boolean normalizes(String mode, String path) {
        return matches(mode, path, "normalize") || matches(mode, path, "uuid-bijection");
    }

    private boolean matches(String mode, String path, String action) {
        if (entries == null) {
            return false;
        }
        for (Entry entry : entries) {
            if (!action.equals(entry.action())) {
                continue;
            }
            if (!entry.modeMatches(mode)) {
                continue;
            }
            if (entry.pathMatches(path)) {
                return true;
            }
        }
        return false;
    }

    public record Entry(
            String path,
            List<String> mode,
            String action,
            String reason,
            String instanceSpecificProof,
            String testProof,
            String reviewStatus,
            String acceptedBy,
            String date
    ) {
        @JsonCreator
        public Entry(
                @JsonProperty("path") String path,
                @JsonProperty("mode") List<String> mode,
                @JsonProperty("action") String action,
                @JsonProperty("reason") String reason,
                @JsonProperty("instance_specific_proof") String instanceSpecificProof,
                @JsonProperty("test_proof") String testProof,
                @JsonProperty("review_status") String reviewStatus,
                @JsonProperty("accepted_by") String acceptedBy,
                @JsonProperty("date") String date) {
            this.path = path;
            this.mode = mode == null ? List.of() : List.copyOf(mode);
            this.action = action == null ? "" : action.toLowerCase(Locale.ROOT);
            this.reason = reason;
            this.instanceSpecificProof = instanceSpecificProof;
            this.testProof = testProof;
            this.reviewStatus = reviewStatus;
            this.acceptedBy = acceptedBy;
            this.date = date;
        }

        boolean modeMatches(String currentMode) {
            return mode.isEmpty() || mode.stream().anyMatch(m -> m.equalsIgnoreCase(currentMode));
        }

        boolean pathMatches(String currentPath) {
            if (path == null || path.isBlank()) {
                return false;
            }
            if (path.endsWith("/*")) {
                return currentPath.startsWith(path.substring(0, path.length() - 1));
            }
            return path.equals(currentPath);
        }
    }
}

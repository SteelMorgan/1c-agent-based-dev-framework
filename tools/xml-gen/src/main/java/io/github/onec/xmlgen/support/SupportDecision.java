package io.github.onec.xmlgen.support;

import java.nio.file.Path;
import java.util.Optional;

public record SupportDecision(
        boolean blocked,
        SupportBlockCode code,
        String reason,
        Path configDir,
        Path targetPath,
        String objectUuid,
        Integer supportRule) {

    public static SupportDecision allowed(Path targetPath, Path configDir, String objectUuid, Integer supportRule) {
        return new SupportDecision(false, null, "", configDir, targetPath, objectUuid, supportRule);
    }

    public static SupportDecision blocked(Path targetPath, Path configDir, String objectUuid,
                                          Integer supportRule, SupportBlockCode code, String reason) {
        return new SupportDecision(true, code, reason, configDir, targetPath, objectUuid, supportRule);
    }

    public Optional<Integer> supportRuleOptional() {
        return Optional.ofNullable(supportRule);
    }
}

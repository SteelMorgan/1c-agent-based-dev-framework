package io.github.onec.xmlgen.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;

public final class SupportGuard {
    private SupportGuard() {
    }

    public static SupportDecision check(Path targetPath, SupportRequirement requirement) throws IOException {
        Path target = targetPath.toAbsolutePath().normalize();
        Optional<SupportPathResolver.ConfigRoot> root = SupportPathResolver.findConfigRoot(target);
        if (root.isEmpty() || root.get().extension()) {
            return SupportDecision.allowed(target, root.map(SupportPathResolver.ConfigRoot::directory).orElse(null), null, null);
        }

        SupportPathResolver.ConfigRoot config = root.get();
        Optional<SupportState> state = ParentConfigurationsParser.parseIfPresent(config.parentConfigurationsBin());
        if (state.isEmpty()) {
            return SupportDecision.allowed(target, config.directory(), null, null);
        }

        Optional<String> objectUuid = SupportPathResolver.resolveObjectUuid(target, config.directory());
        OptionalInt rule = objectUuid.isPresent() ? state.get().ruleFor(objectUuid.get()) : OptionalInt.empty();
        Integer boxedRule = rule.isPresent() ? rule.getAsInt() : null;

        if (state.get().capabilityOff()) {
            return SupportDecision.blocked(target, config.directory(), objectUuid.orElse(null), boxedRule,
                    SupportBlockCode.CAPABILITY_OFF,
                    "configuration support edit capability is disabled; the configuration is read-only");
        }

        if (requirement == SupportRequirement.REMOVED) {
            if (rule.isPresent() && rule.getAsInt() != 2) {
                return SupportDecision.blocked(target, config.directory(), objectUuid.orElse(null), boxedRule,
                        SupportBlockCode.NOT_REMOVED,
                        "object is still on vendor support and must be explicitly removed from support before deletion");
            }
            return SupportDecision.allowed(target, config.directory(), objectUuid.orElse(null), boxedRule);
        }

        if (rule.isPresent() && rule.getAsInt() == 0) {
            return SupportDecision.blocked(target, config.directory(), objectUuid.orElse(null), boxedRule,
                    SupportBlockCode.LOCKED,
                    "object is locked by vendor support; direct XML mutation would break future updates");
        }

        return SupportDecision.allowed(target, config.directory(), objectUuid.orElse(null), boxedRule);
    }

    public static void require(Path targetPath, SupportRequirement requirement) throws IOException {
        SupportDecision decision = check(targetPath, requirement);
        if (decision.blocked()) {
            throw new IllegalArgumentException(diagnostic(decision, requirement));
        }
    }

    public static String diagnostic(SupportDecision decision, SupportRequirement requirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("[support-guard] Mutation rejected: vendor-supported object is protected.\n");
        sb.append("Target: ").append(decision.targetPath()).append('\n');
        if (decision.configDir() != null) {
            sb.append("Config: ").append(decision.configDir()).append('\n');
        }
        if (decision.objectUuid() != null) {
            sb.append("Object UUID: ").append(decision.objectUuid()).append('\n');
        }
        if (decision.supportRule() != null) {
            sb.append("Support rule f1: ").append(decision.supportRule()).append('\n');
        }
        sb.append("Reason: ").append(decision.reason()).append('\n');
        if (decision.code() == SupportBlockCode.CAPABILITY_OFF) {
            sb.append("Use an extension, or explicitly enable support editing for the configuration and then open the object.");
        } else if (decision.code() == SupportBlockCode.NOT_REMOVED || requirement == SupportRequirement.REMOVED) {
            sb.append("Use explicit support state change to off-support before deleting this object.");
        } else {
            sb.append("Use an extension, or explicitly set this object editable/off-support before mutating XML.");
        }
        return sb.toString();
    }
}

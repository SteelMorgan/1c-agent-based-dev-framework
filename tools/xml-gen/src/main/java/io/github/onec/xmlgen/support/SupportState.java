package io.github.onec.xmlgen.support;

import java.util.Collections;
import java.util.Map;
import java.util.OptionalInt;

public final class SupportState {
    private final int globalFlag;
    private final int vendorCount;
    private final Map<String, Integer> rulesByLocalUuid;

    SupportState(int globalFlag, int vendorCount, Map<String, Integer> rulesByLocalUuid) {
        this.globalFlag = globalFlag;
        this.vendorCount = vendorCount;
        this.rulesByLocalUuid = Collections.unmodifiableMap(rulesByLocalUuid);
    }

    public int globalFlag() {
        return globalFlag;
    }

    public int vendorCount() {
        return vendorCount;
    }

    public boolean capabilityOff() {
        return globalFlag == 1;
    }

    public OptionalInt ruleFor(String uuid) {
        if (uuid == null) {
            return OptionalInt.empty();
        }
        Integer value = rulesByLocalUuid.get(uuid.toLowerCase());
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}

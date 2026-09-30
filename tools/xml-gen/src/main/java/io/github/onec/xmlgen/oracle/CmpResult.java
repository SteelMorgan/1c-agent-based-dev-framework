package io.github.onec.xmlgen.oracle;

import java.util.List;
import java.util.Map;

public record CmpResult(
        String mode,
        String objectId,
        CmpStatus status,
        List<DiffEntry> diffs,
        List<NormalizedDimension> normalized,
        List<CoverageGap> coverageGaps,
        FailureClass failureClass,
        String failureBucket,
        Map<String, Object> context
) {
    public CmpResult(String mode, String objectId, CmpStatus status, List<DiffEntry> diffs,
                     List<NormalizedDimension> normalized, List<CoverageGap> coverageGaps,
                     FailureClass failureClass, Map<String, Object> context) {
        this(mode, objectId, status, diffs, normalized, coverageGaps, failureClass,
                defaultBucket(status, diffs, failureClass), context);
    }

    public static CmpResult pass(String mode, String objectId, List<NormalizedDimension> normalized) {
        return new CmpResult(mode, objectId, CmpStatus.PASS, List.of(), normalized, List.of(), FailureClass.NONE,
                PlatformFailureBucket.NONE.reportId(), Map.of());
    }

    public static CmpResult fail(String mode, String objectId, List<DiffEntry> diffs, FailureClass failureClass) {
        return fail(mode, objectId, diffs, failureClass, Map.of());
    }

    public static CmpResult fail(String mode, String objectId, List<DiffEntry> diffs, FailureClass failureClass,
                                 Map<String, Object> context) {
        return new CmpResult(mode, objectId, CmpStatus.FAIL, diffs, List.of(), List.of(), failureClass,
                classifyDiffs(diffs), context == null ? Map.of() : Map.copyOf(context));
    }

    public static CmpResult coverageGap(String mode, String objectId, List<CoverageGap> gaps,
                                        Map<String, Object> context) {
        return new CmpResult(mode, objectId, CmpStatus.COVERAGE_GAP, List.of(), List.of(), gaps,
                FailureClass.COVERAGE_GAP, PlatformFailureBucket.COVERAGE_GAP.reportId(),
                context == null ? Map.of() : Map.copyOf(context));
    }

    public static CmpResult error(String mode, String objectId, String step, Exception error) {
        DiffEntry diff = new DiffEntry(step, "error", "", error.getClass().getSimpleName(),
                error.getMessage() == null ? "" : error.getMessage());
        return new CmpResult(mode, objectId, CmpStatus.ERROR, List.of(diff), List.of(), List.of(),
                FailureClass.UNCLASSIFIED, classifyDiffs(List.of(diff)), Map.of());
    }

    private static String classifyDiffs(List<DiffEntry> diffs) {
        if (diffs == null || diffs.isEmpty()) {
            return PlatformFailureBucket.NONE.reportId();
        }
        StringBuilder text = new StringBuilder();
        for (DiffEntry diff : diffs) {
            text.append(diff.kind()).append('\n')
                    .append(diff.path()).append('\n')
                    .append(diff.expected()).append('\n')
                    .append(diff.actual()).append('\n')
                    .append(diff.message()).append('\n');
        }
        return PlatformFailureClassifier.classifyReportId(text.toString());
    }

    private static String defaultBucket(CmpStatus status, List<DiffEntry> diffs, FailureClass failureClass) {
        if (failureClass == FailureClass.COVERAGE_GAP || status == CmpStatus.COVERAGE_GAP) {
            return PlatformFailureBucket.COVERAGE_GAP.reportId();
        }
        if (status == CmpStatus.PASS) {
            return PlatformFailureBucket.NONE.reportId();
        }
        return classifyDiffs(diffs);
    }
}

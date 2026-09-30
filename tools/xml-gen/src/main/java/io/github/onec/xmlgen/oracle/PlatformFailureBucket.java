package io.github.onec.xmlgen.oracle;

public enum PlatformFailureBucket {
    NONE("none"),
    FORMAT_VERSION("format_version"),
    UNKNOWN_PROPERTY("unknown_property"),
    XDTO_READ_ERROR("xDTO_read_error"),
    BAD_REFERENCE("bad_reference"),
    UNDEFINED_TYPE("undefined_type"),
    MISSING_FILE("missing_file"),
    ARCHIVE_OR_DIRECTORY_MODE("archive_or_directory_mode"),
    COVERAGE_GAP("coverage_gap"),
    UNCLASSIFIED("unclassified");

    private final String reportId;

    PlatformFailureBucket(String reportId) {
        this.reportId = reportId;
    }

    public String reportId() {
        return reportId;
    }
}

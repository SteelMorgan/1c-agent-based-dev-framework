package io.github.onec.xmlgen.oracle;

import java.util.Locale;

final class PlatformFailureClassifier {

    private PlatformFailureClassifier() {
    }

    static PlatformFailureBucket classify(String text) {
        if (text == null || text.isBlank()) {
            return PlatformFailureBucket.NONE;
        }
        String value = text.toLowerCase(Locale.ROOT);

        if (containsAny(value, "ids_xcf_err_format_version_too_high",
                "ids_xcf_err_different_format_version",
                "ids_xcf_err_undefined_format_version",
                "ids_xcf_err_unknown_format",
                "ids_xcf_err_invalid_file_format")
                || (value.contains("format") && value.contains("version"))) {
            return PlatformFailureBucket.FORMAT_VERSION;
        }
        if (containsAny(value, "ids_xcf_err_xdto_property_unknown",
                "ids_xcf_err_xdto_property_mismatch",
                "ids_xcf_err_wrong_md_property",
                "ids_xcf_err_unknown_property",
                "unknown property")) {
            return PlatformFailureBucket.UNKNOWN_PROPERTY;
        }
        if (containsAny(value, "ids_xcf_err_xdto_read_error", "xdto read", "xdto")) {
            return PlatformFailureBucket.XDTO_READ_ERROR;
        }
        if (containsAny(value, "ids_xcf_err_bad_reference",
                "ids_xcf_err_undefined_object",
                "ids_xcf_err_undefined_field_ref",
                "bad reference",
                "undefined object",
                "undefined field")) {
            return PlatformFailureBucket.BAD_REFERENCE;
        }
        if (containsAny(value, "ids_xcf_err_undefined_mdobject_class",
                "ids_xcf_err_undefined_type_name",
                "undefined type",
                "undefined mdobject class")) {
            return PlatformFailureBucket.UNDEFINED_TYPE;
        }
        if (containsAny(value, "no such file", "not found", "missing file", "file not found")) {
            return PlatformFailureBucket.MISSING_FILE;
        }
        if (containsAny(value, "ids_xcf_err_xml_source_is_not_archive",
                "ids_xcf_err_xml_source_is_not_dir",
                "ids_xcf_err_not_empty_dump_dir",
                "ids_xcf_err_archive_file_name",
                "xcf_format_plain",
                "xcf_format_hierarchical",
                "archive",
                "directory mode",
                "dump dir")) {
            return PlatformFailureBucket.ARCHIVE_OR_DIRECTORY_MODE;
        }
        return PlatformFailureBucket.UNCLASSIFIED;
    }

    static String classifyReportId(String text) {
        return classify(text).reportId();
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}

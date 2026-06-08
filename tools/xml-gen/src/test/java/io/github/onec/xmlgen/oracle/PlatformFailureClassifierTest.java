package io.github.onec.xmlgen.oracle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformFailureClassifierTest {

    @Test
    void mapsXcfIdentifiersToPlatformLikeBuckets() {
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_FORMAT_VERSION_TOO_HIGH"))
                .isEqualTo("format_version");
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_UNKNOWN_PROPERTY"))
                .isEqualTo("unknown_property");
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_XDTO_READ_ERROR"))
                .isEqualTo("xDTO_read_error");
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_BAD_REFERENCE"))
                .isEqualTo("bad_reference");
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_UNDEFINED_TYPE_NAME"))
                .isEqualTo("undefined_type");
        assertThat(PlatformFailureClassifier.classifyReportId("No such file: Ext/Template/ru.html"))
                .isEqualTo("missing_file");
        assertThat(PlatformFailureClassifier.classifyReportId("IDS_XCF_ERR_XML_SOURCE_IS_NOT_ARCHIVE"))
                .isEqualTo("archive_or_directory_mode");
    }

    @Test
    void cmpResultKeepsFailureClassAndAddsFailureBucket() {
        CmpResult result = CmpResult.fail("demo", "obj",
                List.of(new DiffEntry("/Help", "error", "", "IDS_XCF_ERR_UNKNOWN_PROPERTY", "")),
                FailureClass.C_OR_EXEC_BUG);

        assertThat(result.failureClass()).isEqualTo(FailureClass.C_OR_EXEC_BUG);
        assertThat(result.failureBucket()).isEqualTo("unknown_property");
    }
}

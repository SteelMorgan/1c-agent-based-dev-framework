package io.github.onec.xmlgen.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class OracleComparatorTest {

    @TempDir
    Path tempDir;

    @Test
    void byteCompareReportsFirstMismatch() throws Exception {
        Path expected = tempDir.resolve("expected.xml");
        Path actual = tempDir.resolve("actual.xml");
        Files.writeString(expected, "abc", StandardCharsets.UTF_8);
        Files.writeString(actual, "axc", StandardCharsets.UTF_8);

        CmpResult result = new OracleComparator().compareBytes("dsl", "obj", expected, actual);

        assertThat(result.status()).isEqualTo(CmpStatus.FAIL);
        assertThat(result.diffs()).isNotEmpty();
        assertThat(result.diffs().get(0).path()).isEqualTo("/bytes/1");
    }

    @Test
    void structuralCompareAllowsConsistentUuidBijection() throws Exception {
        Path expected = tempDir.resolve("expected.xml");
        Path actual = tempDir.resolve("actual.xml");
        Files.writeString(expected, """
                <?xml version="1.0" encoding="UTF-8"?>
                <root uuid="11111111-1111-1111-1111-111111111111">
                  <ref>11111111-1111-1111-1111-111111111111</ref>
                </root>
                """, StandardCharsets.UTF_8);
        Files.writeString(actual, """
                <?xml version="1.0" encoding="UTF-8"?>
                <root uuid="22222222-2222-2222-2222-222222222222">
                  <ref>22222222-2222-2222-2222-222222222222</ref>
                </root>
                """, StandardCharsets.UTF_8);

        CmpResult result = new OracleComparator().compareStructure(
                "cli", "obj", expected, actual, IgnoreAllowlist.empty("mxl"));

        assertThat(result.status()).isEqualTo(CmpStatus.PASS);
    }

    @Test
    void structuralCompareRejectsBrokenUuidBijection() throws Exception {
        Path expected = tempDir.resolve("expected.xml");
        Path actual = tempDir.resolve("actual.xml");
        Files.writeString(expected, """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <a>11111111-1111-1111-1111-111111111111</a>
                  <b>11111111-1111-1111-1111-111111111111</b>
                </root>
                """, StandardCharsets.UTF_8);
        Files.writeString(actual, """
                <?xml version="1.0" encoding="UTF-8"?>
                <root>
                  <a>22222222-2222-2222-2222-222222222222</a>
                  <b>33333333-3333-3333-3333-333333333333</b>
                </root>
                """, StandardCharsets.UTF_8);

        CmpResult result = new OracleComparator().compareStructure(
                "cli", "obj", expected, actual, IgnoreAllowlist.empty("mxl"));

        assertThat(result.status()).isEqualTo(CmpStatus.FAIL);
        assertThat(result.diffs()).anyMatch(d -> d.kind().equals("uuid-bijection"));
    }
}

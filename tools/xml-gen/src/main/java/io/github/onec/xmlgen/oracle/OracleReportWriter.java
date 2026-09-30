package io.github.onec.xmlgen.oracle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class OracleReportWriter {

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public void write(Path out, OracleReport report, CoverageMatrix coverageMatrix,
                      List<Map<String, Object>> xgCandidates) throws IOException {
        Files.createDirectories(out);
        mapper.writeValue(out.resolve("oracle-report.json").toFile(), report);
        mapper.writeValue(out.resolve("coverage-matrix.json").toFile(), coverageMatrix);
        mapper.writeValue(out.resolve("xg-candidates.json").toFile(), xgCandidates);
        Files.writeString(out.resolve("oracle-summary.md"), summary(report), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("xg-candidates.md"), xgMarkdown(xgCandidates), StandardCharsets.UTF_8);
    }

    private String summary(OracleReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("# xml-gen oracle summary\n\n");
        sb.append("- run_id: `").append(report.runId()).append("`\n");
        sb.append("- pilot: `").append(report.pilot()).append("`\n");
        for (Map.Entry<String, OracleModeSummary> entry : report.modes().entrySet()) {
            OracleModeSummary s = entry.getValue();
            sb.append("- ").append(entry.getKey()).append(": checked=").append(s.checked())
                    .append(", pass=").append(s.pass())
                    .append(", fail=").append(s.fail())
                    .append(", coverage_gaps=").append(s.coverageGaps())
                    .append(", error=").append(s.error()).append("\n");
        }
        return sb.toString();
    }

    private String xgMarkdown(List<Map<String, Object>> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("# XG candidates\n\n");
        if (candidates.isEmpty()) {
            sb.append("No candidates.\n");
            return sb.toString();
        }
        for (Map<String, Object> c : candidates) {
            sb.append("- ").append(c.getOrDefault("candidate_id", "XG-?"))
                    .append(" ").append(c.getOrDefault("symptom", ""))
                    .append(" mode=").append(c.getOrDefault("mode", ""))
                    .append(" object=").append(c.getOrDefault("object", ""))
                    .append("\n");
        }
        return sb.toString();
    }
}

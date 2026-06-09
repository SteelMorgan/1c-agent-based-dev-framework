package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OracleMineRulesCliTest {

    @TempDir
    Path tempDir;

    @Test
    void oracleMineRulesWritesJsonAndMarkdownReports() throws Exception {
        writeTemplateFixture("Documents/Sales/Templates/Print", "Print", "SpreadsheetDocument");
        writeTemplateFixture("Documents/Return/Templates/Print", "Print", "SpreadsheetDocument");

        ProcessResult result = runMain("oracle", "mine-rules",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("rules").toString(),
                "--min-support", "2");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        Path reportPath = tempDir.resolve("rules/rule-mining-report.json");
        Path candidatesPath = tempDir.resolve("rules/rule-candidates.json");
        Path digestPath = tempDir.resolve("rules/rule-digest.json");
        Path digestMdPath = tempDir.resolve("rules/rule-digest.md");
        Path summaryPath = tempDir.resolve("rules/rule-mining-summary.md");
        assertThat(reportPath).exists();
        assertThat(candidatesPath).exists();
        assertThat(digestPath).exists();
        assertThat(digestMdPath).exists();
        assertThat(summaryPath).exists();

        String report = Files.readString(reportPath, StandardCharsets.UTF_8);
        assertThat(report).contains("\"TemplateWrapper\"");
        assertThat(report).contains("\"LINKED_BODY\"");
        assertThat(report).contains("\"ROOT_CONTRACT\"");
        assertThat(report).contains("\"VALUE_DOMAIN\"");
        String digest = Files.readString(digestPath, StandardCharsets.UTF_8);
        assertThat(digest).contains("\"bundles\"", "\"rawCandidateCount\"", "\"noiseSummary\"");
        assertThat(Files.readString(summaryPath, StandardCharsets.UTF_8)).contains("xml-gen rule mining summary");
        assertThat(Files.readString(digestMdPath, StandardCharsets.UTF_8)).contains("xml-gen rule digest");
    }

    @Test
    void oracleMineRulesSuppressesDisposedBundles() throws Exception {
        writeTemplateFixture("Documents/Sales/Templates/Print", "Print", "SpreadsheetDocument");
        writeTemplateFixture("Documents/Return/Templates/Print", "Print", "SpreadsheetDocument");
        String key = "bundle:TemplateWrapper:TemplateType=SpreadsheetDocument -> Ext/Template.xml:"
                + "DISCRIMINATOR_LINKED_BODY";
        Path disposition = tempDir.resolve("rules-disposition.json");
        Files.writeString(disposition, """
                {
                  "entries": [
                    {
                      "key": "%s",
                      "status": "implemented",
                      "reason": "covered by template body generator",
                      "target": "xml-gen",
                      "updatedAt": "2026-06-08"
                    }
                  ]
                }
                """.formatted(key), StandardCharsets.UTF_8);

        ProcessResult result = runMain("oracle", "mine-rules",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("rules-disposed").toString(),
                "--min-support", "2",
                "--disposition", disposition.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        String digest = Files.readString(tempDir.resolve("rules-disposed/rule-digest.json"), StandardCharsets.UTF_8);
        assertThat(digest).contains("\"suppressed_implemented\" : 1");
        assertThat(digest).doesNotContain("\"key\" : \"" + key + "\"");
        String digestMd = Files.readString(tempDir.resolve("rules-disposed/rule-digest.md"), StandardCharsets.UTF_8);
        assertThat(digestMd).contains("| Id | Key |");
    }

    private void writeTemplateFixture(String basePath, String templateName, String templateType) throws Exception {
        Path wrapper = tempDir.resolve("src/xml").resolve(basePath + ".xml");
        Path body = tempDir.resolve("src/xml").resolve(basePath).resolve("Ext/Template.xml");
        Files.createDirectories(wrapper.getParent());
        Files.createDirectories(body.getParent());
        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Template uuid="00000000-0000-0000-0000-000000000001">
                    <Properties>
                      <Name>%s</Name>
                      <TemplateType>%s</TemplateType>
                    </Properties>
                  </Template>
                </MetaDataObject>
                """.formatted(templateName, templateType), StandardCharsets.UTF_8);
        Files.writeString(body, """
                <?xml version="1.0" encoding="UTF-8"?>
                <document xmlns="http://v8.1c.ru/8.2/spreadsheet"
                          version="2.20">
                  <config>
                    <grid>true</grid>
                  </config>
                </document>
                """, StandardCharsets.UTF_8);
    }

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        boolean exited = process.waitFor(20, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

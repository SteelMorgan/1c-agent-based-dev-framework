package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateTemplateAndSourceRootCliTest {

    @TempDir
    Path tempDir;

    @Test
    void validateFormWithNonexistentSrcRootDoesNotSilentlySkipSemanticTypes() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        writeBom(form, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
                      xmlns:v8="http://v8.1c.ru/8.1/data/core"
                      xmlns:cfg="http://v8.1c.ru/8.1/data/enterprise/current-config"
                      version="2.17">
                    <AutoCommandBar name="FormCommandBar" id="-1"/>
                    <Attributes>
                        <Attribute name="Ref" id="1">
                            <Type>
                                <v8:Type>cfg:CatalogRef.Nope</v8:Type>
                            </Type>
                        </Attribute>
                    </Attributes>
                    <ChildItems/>
                </Form>
                """);

        ProcessResult result = runMain(
                "validate",
                "--type", "form",
                "--src-root", tempDir.resolve("missing-src-root").toString(),
                form.toString());

        String output = result.combinedOutput();
        assertThat(result.exitCode()).as(output).isNotZero();
        assertThat(hasSemanticTypeOrSourceRootDiagnostic(output))
                .as(output)
                .isTrue();
    }

    @Test
    void validateTypeTemplateRoutesSpreadsheetDocumentBodyLikeMxl() throws Exception {
        Path template = tempDir.resolve("Template.xml");
        writeBom(template, validMxlBody());

        ProcessResult mxl = runMain("validate", "--type", "mxl", template.toString());
        assertThat(mxl.exitCode()).as(mxl.combinedOutput()).isZero();

        ProcessResult templateResult = runMain("validate", "--type", "template", template.toString());
        assertThat(templateResult.exitCode()).as(templateResult.combinedOutput()).isZero();
        assertThat(templateResult.combinedOutput()).doesNotContain("META-001", "Unexpected UTF-8 BOM");
    }

    @Test
    void validateTypeTemplateReportsMxlDiagnosticsForInvalidSpreadsheetDocumentBody() throws Exception {
        Path template = tempDir.resolve("Template.xml");
        writeBom(template, """
                <?xml version="1.0" encoding="UTF-8"?>
                <document xmlns="http://v8.1c.ru/8.2/data/spreadsheet">
                    <templateMode>true</templateMode>
                    <columns><size>1</size></columns>
                    <height>1</height>
                    <rowsItem>
                        <c>
                            <horizontalAlignment>InvalidAlign</horizontalAlignment>
                        </c>
                    </rowsItem>
                </document>
                """);

        ProcessResult result = runMain("validate", "--type", "template", template.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("MXL-101");
        assertThat(result.combinedOutput()).doesNotContain("META-001");
    }

    @Test
    void validateTypeTemplateAcceptsTemplateMetadataWrapper() throws Exception {
        Path template = tempDir.resolve("Main.xml");
        writeBom(template, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                                xmlns:v8="http://v8.1c.ru/8.1/data/core"
                                version="2.17">
                    <Template uuid="00000000-0000-0000-0000-000000000001">
                        <Properties>
                            <Name>Main</Name>
                            <Synonym>
                                <v8:item>
                                    <v8:lang>ru</v8:lang>
                                    <v8:content>Main</v8:content>
                                </v8:item>
                            </Synonym>
                            <Comment></Comment>
                            <TemplateType>SpreadsheetDocument</TemplateType>
                        </Properties>
                    </Template>
                </MetaDataObject>
                """);

        ProcessResult result = runMain("validate", "--type", "template", template.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isZero();
        assertThat(result.combinedOutput()).doesNotContain("GEN-003", "META-001");
    }

    @Test
    void validateTypeTemplateRoutesDataCompositionSchemaBodyLikeSkd() throws Exception {
        Path template = tempDir.resolve("Template.xml");
        writeBom(template, """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                </DataCompositionSchema>
                """);

        ProcessResult result = runMain("validate", "--type", "template", template.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(2);
        assertThat(result.combinedOutput()).contains("SKD-002", "SKD-005");
        assertThat(result.combinedOutput()).doesNotContain("META-001");
    }

    private static boolean hasSemanticTypeOrSourceRootDiagnostic(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        return output.contains("SEM-001")
                || lower.contains("src-root")
                || lower.contains("source root")
                || lower.contains("source-root");
    }

    private ProcessResult runMain(String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private static String validMxlBody() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <document xmlns="http://v8.1c.ru/8.2/data/spreadsheet">
                    <templateMode>true</templateMode>
                    <columns><size>1</size></columns>
                    <height>1</height>
                    <rowsItem>
                        <c>
                            <horizontalAlignment>Left</horizontalAlignment>
                        </c>
                    </rowsItem>
                </document>
                """;
    }

    private static void writeBom(Path file, String content) throws Exception {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xef;
        bytes[1] = (byte) 0xbb;
        bytes[2] = (byte) 0xbf;
        System.arraycopy(body, 0, bytes, 3, body.length);
        Files.write(file, bytes);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {

        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

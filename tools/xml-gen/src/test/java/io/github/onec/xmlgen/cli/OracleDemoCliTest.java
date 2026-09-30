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

class OracleDemoCliTest {

    @TempDir
    Path tempDir;

    @Test
    void oracleDemoReportsArtifactKindsAndValidateCommandTrace() throws Exception {
        Path catalog = tempDir.resolve("src/xml/Catalogs/_ДемоCatalog.xml");
        Path formWrapper = tempDir.resolve("src/xml/Catalogs/_ДемоCatalog/Forms/ФормаЭлемента.xml");
        Path formBody = tempDir.resolve("src/xml/Catalogs/_ДемоCatalog/Forms/ФормаЭлемента/Ext/Form.xml");
        Path help = tempDir.resolve("src/xml/Catalogs/_ДемоCatalog/Ext/Help.xml");
        Path picture = tempDir.resolve("src/xml/CommonPictures/_ДемоPicture/Ext/Picture.xml");
        Path pictureWrapper = tempDir.resolve("src/xml/CommonPictures/_ДемоPicture.xml");
        Path picturePayload = tempDir.resolve("src/xml/CommonPictures/_ДемоPicture/Ext/Picture/Picture.png");
        Files.createDirectories(catalog.getParent());
        Files.createDirectories(formWrapper.getParent());
        Files.createDirectories(formBody.getParent());
        Files.createDirectories(help.getParent());
        Files.createDirectories(picture.getParent());
        Files.createDirectories(picturePayload.getParent());

        Files.writeString(catalog, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                                xmlns:v8="http://v8.1c.ru/8.1/data/core"
                                xmlns:app="http://v8.1c.ru/8.2/managed-application/core"
                                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                version="2.20">
                  <Catalog uuid="00000000-0000-0000-0000-000000000010">
                    <Properties>
                      <Name>_ДемоCatalog</Name>
                      <Synonym/>
                      <Comment/>
                      <CodeLength>9</CodeLength>
                      <DescriptionLength>25</DescriptionLength>
                      <ChildObjects/>
                    </Properties>
                    <ChildObjects/>
                  </Catalog>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        Files.writeString(formWrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject><Form uuid="00000000-0000-0000-0000-000000000001"/></MetaDataObject>
                """, StandardCharsets.UTF_8);
        Files.writeString(formBody, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form/>
                """, StandardCharsets.UTF_8);
        Files.writeString(help, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Help><Page>ru</Page></Help>
                """, StandardCharsets.UTF_8);
        Files.writeString(pictureWrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <CommonPicture uuid="00000000-0000-0000-0000-000000000002">
                    <Properties>
                      <Name>_ДемоPicture</Name>
                    </Properties>
                  </CommonPicture>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        Files.writeString(picture, "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExtPicture xmlns="http://v8.1c.ru/8.3/xcf/extrnprops"
                            xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            version="2.20">
                  <Picture>
                    <xr:Abs>Picture.png</xr:Abs>
                    <xr:LoadTransparent>false</xr:LoadTransparent>
                  </Picture>
                </ExtPicture>
                """, StandardCharsets.UTF_8);
        Files.write(picturePayload, new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
                0, 0, 0, '\r', 'I', 'H', 'D', 'R'
        });

        ProcessResult result = runMain("oracle", "demo",
                "--source", tempDir.resolve("src/xml").toString(),
                "--out", tempDir.resolve("oracle-demo").toString(),
                "--threads", "2");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(0);
        String report = Files.readString(tempDir.resolve("oracle-demo/latest-demo-oracle-report.json"),
                StandardCharsets.UTF_8);
        assertThat(report).contains("\"byArtifactKind\"");
        assertThat(report).contains("\"byFailureBucket\"");
        assertThat(report).contains("\"artifactKind\" : \"form-wrapper\"");
        assertThat(report).contains("\"artifactKind\" : \"form-body\"");
        assertThat(report).contains("\"artifactKind\" : \"help\"");
        assertThat(report).contains("\"artifactKind\" : \"picture\"");
        assertThat(report).contains("\"capability\" : \"cli_registration_oracle_available\"");
        assertThat(report).contains("\"capability\" : \"picture_body_lossless_oracle_available\"");
        assertThat(report).contains("\"capability\" : \"validation_only_no_decompiler\"");
        assertThat(report).contains("\"capability\" : \"form_generation_edit_oracle_available\"");
        assertThat(report).contains("\"failureBucket\" : \"none\"");
        assertThat(report).contains("\"artifactKind\" : \"form-generation-edit\"");
        assertThat(report).doesNotContain("\"status\" : \"COVERAGE_GAP\"");
        assertThat(report).contains("\"status\" : \"PASS\"");
        assertThat(report).contains("\"meta\"", "\"edit\"", "\"add-form\"");
        assertThat(report).contains("\"xml-gen\"", "\"validate\"", "\"--level\"", "\"semantic\"");
        assertThat(report).contains("\"payloadFormat\" : \"png\"");
        assertThat(report).contains("\"wrapperMatchesName\" : true");
        assertThat(report).contains("\"edtDerivedInvariants\"");
        assertThat(report).contains("\"source\" : \"edt-xcore+dt-project-checks\"");
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

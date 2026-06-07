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

class ValidateFormatContractTest {

    @TempDir
    Path tempDir;

    @Test
    void validateEdtFormRejectsDesignerNamespaceWithoutBom() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        Files.writeString(form, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
                      xmlns:v8="http://v8.1c.ru/8.1/data/core"
                      version="2.20">
                    <Title>
                        <v8:item>
                            <v8:lang>ru</v8:lang>
                            <v8:content>Форма</v8:content>
                        </v8:item>
                    </Title>
                    <AutoCommandBar name="ФормаКоманднаяПанель" id="-1"/>
                    <ChildItems/>
                </Form>
                """, StandardCharsets.UTF_8);
        assertThat(Files.readAllBytes(form)[0]).isEqualTo((byte) '<');

        ProcessResult result = runMain(
                "validate",
                "--type", "form",
                "--format", "edt",
                form.toString());

        String output = result.combinedOutput();
        assertThat(result.exitCode()).as(output).isNotZero();
        assertThat(output)
                .contains("GEN-005")
                .contains("http://g5.1c.ru/v8/dt/form");
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

    private record ProcessResult(int exitCode, String stdout, String stderr) {

        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

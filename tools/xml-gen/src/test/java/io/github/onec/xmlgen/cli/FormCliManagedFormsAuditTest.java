package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FormCliManagedFormsAuditTest {

    @TempDir
    Path tempDir;

    @Test
    void formAddElementCliBeforeInsertsBeforeNamedSibling() throws Exception {
        Path form = tempDir.resolve("Forms/Main/Ext/Form.xml");
        writeCanonicalForm(form,
                "\t<ChildItems>\n"
                        + "\t\t<InputField name=\"First\" id=\"1\"/>\n"
                        + "\t\t<InputField name=\"Third\" id=\"2\"/>\n"
                        + "\t</ChildItems>\n");

        Commands.execute("form", new String[]{
                "add-element",
                "--type", "InputField",
                "--name", "Second",
                "--before", "Third",
                form.toString()
        });

        String xml = Files.readString(form, StandardCharsets.UTF_8);
        assertThat(xml.indexOf("name=\"First\"")).isLessThan(xml.indexOf("name=\"Second\""));
        assertThat(xml.indexOf("name=\"Second\"")).isLessThan(xml.indexOf("name=\"Third\""));
    }

    @Test
    void failedFormEditJsonDoesNotWriteBslStubsOrWrongModulePath() throws Exception {
        Path form = tempDir.resolve("Forms/Main/Ext/Form.xml");
        writeCanonicalForm(form,
                "\t<Attributes/>\n"
                        + "\t<ChildItems/>\n");
        Path module = tempDir.resolve("Forms/Main/Ext/Form/Module.bsl");
        Files.createDirectories(module.getParent());
        byte[] originalModule = withBom("// original\r\n");
        Files.write(module, originalModule);

        Path spec = tempDir.resolve("edit.json");
        Files.writeString(spec, """
                {
                  "elements": [
                    {
                      "kind": "input",
                      "name": "BrokenField",
                      "dataPath": "MissingAttribute",
                      "on": ["OnChange"]
                    }
                  ]
                }
                """, StandardCharsets.UTF_8);

        ProcessResult result = runMain("form", "edit", form.toString(), "--json", spec.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("FORM-102");
        assertThat(Files.readAllBytes(module)).isEqualTo(originalModule);
        assertThat(tempDir.resolve("Forms/Main/Ext/Module.bsl")).doesNotExist();
    }

    private ProcessResult runMain(String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("java.class.path");
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add("io.github.onec.xmlgen.cli.Main");
        command.addAll(java.util.List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        return new ProcessResult(exit, stdout, stderr);
    }

    private void writeCanonicalForm(Path form, String body) throws Exception {
        Files.createDirectories(form.getParent());
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" "
                + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.17\">\n"
                + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                + body
                + "</Form>\n";
        Files.write(form, withBom(xml));
    }

    private static byte[] withBom(String text) {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[body.length + 3];
        bytes[0] = (byte) 0xef;
        bytes[1] = (byte) 0xbb;
        bytes[2] = (byte) 0xbf;
        System.arraycopy(body, 0, bytes, 3, body.length);
        return bytes;
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

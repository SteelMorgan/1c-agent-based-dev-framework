package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateCliContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-template remove missing with default src fails without mutation")
    void removeMissingTemplateWithDefaultSrcFailsAndDoesNotMutateTree() throws Exception {
        createObject("Documents", "Document", "X", false);
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain(
                "template", "remove",
                "--object", "Document.X",
                "--name", "Missing",
                tempDir.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("ERROR: Template 'Missing' not found");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("integr-template add rejects set-main-dcs for DataProcessor before mutation")
    void addDcsTemplateWithSetMainDcsForDataProcessorFailsBeforeMutation() throws Exception {
        createObject("DataProcessors", "DataProcessor", "X", false);
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain(
                "template", "add",
                "--object", "DataProcessor.X",
                "--name", "Main",
                "--type", "DataCompositionSchema",
                "--set-main-dcs",
                tempDir.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains("ERROR: --set-main-dcs is only valid with Report");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("integr-template add set-main-dcs for Report updates MainDataCompositionSchema")
    void addDcsTemplateWithSetMainDcsForReportSetsMainSchema() throws Exception {
        createObject("Reports", "Report", "X", true);

        ProcessResult result = runMain(
                "template", "add",
                "--object", "Report.X",
                "--name", "Main",
                "--type", "DataCompositionSchema",
                "--set-main-dcs",
                tempDir.toString());

        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stderr()).isBlank();

        String root = Files.readString(tempDir.resolve("src/Reports/X.xml"), StandardCharsets.UTF_8);
        assertThat(root)
                .contains("<Template>Main</Template>")
                .contains("<MainDataCompositionSchema>Report.X.Template.Main</MainDataCompositionSchema>");
        assertThat(tempDir.resolve("src/Reports/X/Templates/Main.xml")).exists();
        assertThat(tempDir.resolve("src/Reports/X/Templates/Main/Ext/Template.xml")).exists();
    }

    @Test
    @DisplayName("integr-template add Help creates wrapper, XCF body, and ru.html payload")
    void addHelpTemplateCreatesBodyAndHtmlPayload() throws Exception {
        createObject("DataProcessors", "DataProcessor", "X", false);

        ProcessResult result = runMain(
                "template", "add",
                "--object", "DataProcessor.X",
                "--name", "Инструкция",
                "--type", "Help",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isBlank();

        Path wrapper = tempDir.resolve("src/DataProcessors/X/Templates/Инструкция.xml");
        Path body = tempDir.resolve("src/DataProcessors/X/Templates/Инструкция/Ext/Template.xml");
        Path html = tempDir.resolve("src/DataProcessors/X/Templates/Инструкция/Ext/Template/ru.html");
        assertThat(wrapper).exists();
        assertThat(body).exists();
        assertThat(html).exists();

        String root = Files.readString(tempDir.resolve("src/DataProcessors/X.xml"), StandardCharsets.UTF_8);
        assertThat(root).contains("<Template>Инструкция</Template>");
        assertThat(Files.readString(wrapper, StandardCharsets.UTF_8))
                .contains("<TemplateType>Help</TemplateType>");
        assertThat(Files.readString(body, StandardCharsets.UTF_8))
                .contains("<Help xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"")
                .contains("<Page>ru</Page>");
        assertThat(Files.readString(html, StandardCharsets.UTF_8))
                .contains("v8help://service_book/service_style");

        assertThat(runMain("validate", "--type", "template", wrapper.toString()).exitCode()).isZero();
        assertThat(runMain("validate", body.toString()).exitCode()).isZero();
    }

    private void createObject(String typePlural, String typeSingular, String name, boolean includeMainDcs)
            throws Exception {
        Path objectDir = tempDir.resolve("src").resolve(typePlural);
        Files.createDirectories(objectDir);

        String mainDcs = includeMainDcs
                ? "\t\t\t<MainDataCompositionSchema></MainDataCompositionSchema>\n"
                : "";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\">\n"
                + "\t<" + typeSingular + " uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>" + name + "</Name>\n"
                + mainDcs
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects/>\n"
                + "\t</" + typeSingular + ">\n"
                + "</MetaDataObject>\n";

        Files.writeString(objectDir.resolve(name + ".xml"), xml, StandardCharsets.UTF_8);
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private Map<String, String> snapshotTree(Path root) throws Exception {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                snapshot.put(root.relativize(path).toString(),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return snapshot;
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

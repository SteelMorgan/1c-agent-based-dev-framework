package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

@DisplayName("integr-form/epf/template input contracts")
class FormEpfTemplateInputContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("epf init rejects unknown option before creating output")
    void epfInitUnknownOption_failsBeforeOutput() throws Exception {
        ProcessResult result = runMain("epf", "init", "--bogus", "--name", "X", tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown option").contains("--bogus");
        assertThat(tempDir.resolve("X.xml")).doesNotExist();
        assertThat(tempDir.resolve("--bogus")).doesNotExist();
    }

    @Test
    @DisplayName("epf add-form rejects invalid form name before mutation")
    void epfAddFormInvalidName_failsBeforeMutation() throws Exception {
        assertThat(runMain("epf", "init", "--name", "Proc", tempDir.toString()).exitCode()).isZero();
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("epf", "add-form",
                "--epf", "Proc",
                "--name", "Bad&Name",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Invalid 1C name").contains("Bad&Name");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("form add rejects invalid form name before mutation")
    void formAddInvalidName_failsBeforeMutation() throws Exception {
        Path catalog = createObject("Catalogs", "Catalog", "Goods");
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("form", "add", catalog.toString(), "Bad&Name");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Invalid 1C name").contains("Bad&Name");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("form add creates structurally valid Form.xml scaffold")
    void formAddCreatesValidFormXmlScaffold() throws Exception {
        Path catalog = createObject("Catalogs", "Catalog", "Goods");

        ProcessResult add = runMain("form", "add", catalog.toString(), "ItemForm");
        assertThat(add.exitCode()).as(add.combinedOutput()).isZero();

	    Path formXml = tempDir.resolve("src/Catalogs/Goods/Forms/ItemForm/Ext/Form.xml");
	    Path wrapperXml = tempDir.resolve("src/Catalogs/Goods/Forms/ItemForm.xml");
	    ProcessResult validate = runMain("validate", "--type", "form", "--level", "structure", formXml.toString());

	    assertThat(validate.exitCode()).as(validate.combinedOutput()).isZero();
	    assertThat(Files.readString(wrapperXml, StandardCharsets.UTF_8))
	            .contains("<FormType>Managed</FormType>")
	            .contains("<IncludeHelpInContents>false</IncludeHelpInContents>")
	            .contains("<UsePurposes>")
	            .contains("PlatformApplication")
	            .contains("MobilePlatformApplication");
	}

    @Test
    @DisplayName("template add rejects unknown option before mutation")
    void templateAddUnknownOption_failsBeforeMutation() throws Exception {
        createObject("Documents", "Document", "Order");
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("template", "add",
                "--object", "Document.Order",
                "--name", "Print",
                "--type", "SpreadsheetDocument",
                "--bogus",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown option").contains("--bogus");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("template add-help rejects unknown option before mutation")
    void templateAddHelpUnknownOption_failsBeforeMutation() throws Exception {
        createObject("Catalogs", "Catalog", "Goods");
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("template", "add-help",
                "--object", "Catalog.Goods",
                "--bogus",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown option").contains("--bogus");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("template remove rejects unknown option before mutation")
    void templateRemoveUnknownOption_failsBeforeMutation() throws Exception {
        createObject("Catalogs", "Catalog", "Goods");
        assertThat(runMain("template", "add",
                "--object", "Catalog.Goods",
                "--name", "ПФ_Print",
                "--type", "SpreadsheetDocument",
                tempDir.toString()).exitCode()).isZero();
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("template", "remove",
                "--object", "Catalog.Goods",
                "--name", "ПФ_Print",
                "--bogus",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Unknown option").contains("--bogus");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("legacy template remove missing fails without mutation")
    void templateRemoveLegacyMissing_failsWithoutMutation() throws Exception {
        Path catalog = createObject("Catalogs", "Catalog", "Goods");
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("template", "remove", catalog.toString(), "Missing");

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("Template 'Missing' not found");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    @Test
    @DisplayName("template add rejects existing unregistered scaffold before overwrite")
    void templateAddExistingUnregisteredScaffold_failsWithoutOverwrite() throws Exception {
        createObject("Catalogs", "Catalog", "Goods");
        Path templates = tempDir.resolve("src/Catalogs/Goods/Templates");
        Files.createDirectories(templates.resolve("Existing/Ext"));
        Files.writeString(templates.resolve("Existing.xml"), "KEEP META", StandardCharsets.UTF_8);
        Files.writeString(templates.resolve("Existing/Ext/Template.xml"), "KEEP BODY", StandardCharsets.UTF_8);
        Map<String, String> before = snapshotTree(tempDir);

        ProcessResult result = runMain("template", "add",
                "--object", "Catalog.Goods",
                "--name", "Existing",
                "--type", "SpreadsheetDocument",
                tempDir.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput()).contains("already exists on disk");
        assertThat(snapshotTree(tempDir)).isEqualTo(before);
    }

    private Path createObject(String typePlural, String typeSingular, String name) throws Exception {
        Path objectDir = tempDir.resolve("src").resolve(typePlural);
        Files.createDirectories(objectDir);
        Path objectXml = objectDir.resolve(name + ".xml");
        Files.writeString(objectXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<" + typeSingular + " uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>" + name + "</Name><DefaultForm/></Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</" + typeSingular + ">\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        return objectXml;
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

package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FormScaffoldRollbackCliTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unit-form add read-only object XML fails without orphan scaffold")
    void formAddReadOnlyObjectXml_rollsBackScaffold() throws Exception {
        Path catalogXml = writeCatalogXml("Контрагенты", false);
        byte[] before = Files.readAllBytes(catalogXml);
        catalogXml.toFile().setReadOnly();

        ProcessResult result = runMain("form", "add", catalogXml.toString(), "ФормаЭлемента");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readAllBytes(catalogXml)).isEqualTo(before);
        assertNoFormScaffold("Контрагенты", "ФормаЭлемента");
    }

    @Test
    @DisplayName("unit-form add default read-only object XML fails without orphan scaffold")
    void formAddDefaultReadOnlyObjectXml_rollsBackScaffold() throws Exception {
        Path catalogXml = writeCatalogXml("Контрагенты", false);
        byte[] before = Files.readAllBytes(catalogXml);
        catalogXml.toFile().setReadOnly();

        ProcessResult result = runMain("form", "add", "--default", catalogXml.toString(), "ФормаЭлемента");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readAllBytes(catalogXml)).isEqualTo(before);
        assertNoFormScaffold("Контрагенты", "ФормаЭлемента");
    }

    @Test
    @DisplayName("unit-form remove delete failure keeps object XML registration")
    void formRemoveDeleteFailure_rollsBackObjectXml() throws Exception {
        Path catalogXml = writeCatalogXml("Контрагенты", true);
        Path formsDir = tempDir.resolve("src/Catalogs/Контрагенты/Forms");
        Files.createDirectories(formsDir.resolve("ФормаЭлемента/Ext"));
        Files.createDirectory(formsDir.resolve("ФормаЭлемента.xml"));
        Files.writeString(formsDir.resolve("ФормаЭлемента.xml/locked.txt"), "locked", StandardCharsets.UTF_8);
        Files.writeString(formsDir.resolve("ФормаЭлемента/Ext/Form.xml"), "<Form/>\n", StandardCharsets.UTF_8);

        byte[] before = Files.readAllBytes(catalogXml);

        ProcessResult result = runMain("form", "remove", catalogXml.toString(), "ФормаЭлемента");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readAllBytes(catalogXml)).isEqualTo(before);
        assertThat(formsDir.resolve("ФормаЭлемента.xml")).exists();
        assertThat(formsDir.resolve("ФормаЭлемента/Ext/Form.xml")).exists();
    }

    @Test
    @DisplayName("unit-form add existing unregistered scaffold is not deleted")
    void formAddExistingUnregisteredScaffold_doesNotDeleteExistingFiles() throws Exception {
        Path catalogXml = writeCatalogXml("Контрагенты", false);
        Path formsDir = tempDir.resolve("src/Catalogs/Контрагенты/Forms");
        Files.createDirectories(formsDir.resolve("ФормаЭлемента/Ext/Form"));
        Files.writeString(formsDir.resolve("ФормаЭлемента.xml"), "existing metadata", StandardCharsets.UTF_8);
        Files.writeString(formsDir.resolve("ФормаЭлемента/Ext/Form.xml"), "existing form", StandardCharsets.UTF_8);
        Files.writeString(formsDir.resolve("ФормаЭлемента/Ext/Form/Module.bsl"), "existing module", StandardCharsets.UTF_8);

        ProcessResult result = runMain("form", "add", catalogXml.toString(), "ФормаЭлемента");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента.xml"), StandardCharsets.UTF_8))
                .isEqualTo("existing metadata");
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента/Ext/Form.xml"), StandardCharsets.UTF_8))
                .isEqualTo("existing form");
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента/Ext/Form/Module.bsl"), StandardCharsets.UTF_8))
                .isEqualTo("existing module");
    }

    @Test
    @DisplayName("unit-form remove read-only object XML restores moved scaffold")
    void formRemoveReadOnlyObjectXml_restoresMovedScaffold() throws Exception {
        Path catalogXml = writeCatalogXml("Контрагенты", true);
        Path formsDir = tempDir.resolve("src/Catalogs/Контрагенты/Forms");
        Files.createDirectories(formsDir.resolve("ФормаЭлемента/Ext/Form"));
        Files.writeString(formsDir.resolve("ФормаЭлемента.xml"), "metadata", StandardCharsets.UTF_8);
        Files.writeString(formsDir.resolve("ФормаЭлемента/Ext/Form.xml"), "form", StandardCharsets.UTF_8);
        Files.writeString(formsDir.resolve("ФормаЭлемента/Ext/Form/Module.bsl"), "module", StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(catalogXml);
        catalogXml.toFile().setReadOnly();

        ProcessResult result = runMain("form", "remove", catalogXml.toString(), "ФормаЭлемента");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(Files.readAllBytes(catalogXml)).isEqualTo(before);
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента.xml"), StandardCharsets.UTF_8))
                .isEqualTo("metadata");
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента/Ext/Form.xml"), StandardCharsets.UTF_8))
                .isEqualTo("form");
        assertThat(Files.readString(formsDir.resolve("ФормаЭлемента/Ext/Form/Module.bsl"), StandardCharsets.UTF_8))
                .isEqualTo("module");
    }

    private Path writeCatalogXml(String name, boolean withForm) throws Exception {
        Path catalogsDir = tempDir.resolve("src/Catalogs");
        Files.createDirectories(catalogsDir);
        Path catalogXml = catalogsDir.resolve(name + ".xml");
        String defaultForm = withForm
                ? "<DefaultForm>Catalog." + name + ".Form.ФормаЭлемента</DefaultForm>"
                : "<DefaultForm/>";
        String childObjects = withForm
                ? "<ChildObjects>\n\t\t\t<Form>ФормаЭлемента</Form>\n\t\t</ChildObjects>"
                : "<ChildObjects/>";

        Files.writeString(catalogXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Catalog uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>" + name + "</Name>\n"
                        + "\t\t\t" + defaultForm + "\n"
                        + "\t\t</Properties>\n"
                        + "\t\t" + childObjects + "\n"
                        + "\t</Catalog>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        return catalogXml;
    }

    private void assertNoFormScaffold(String objectName, String formName) {
        Path formsDir = tempDir.resolve("src/Catalogs").resolve(objectName).resolve("Forms");
        assertThat(formsDir.resolve(formName + ".xml")).doesNotExist();
        assertThat(formsDir.resolve(formName).resolve("Ext").resolve("Form.xml")).doesNotExist();
        assertThat(formsDir.resolve(formName).resolve("Ext").resolve("Form").resolve("Module.bsl")).doesNotExist();
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
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(exited).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

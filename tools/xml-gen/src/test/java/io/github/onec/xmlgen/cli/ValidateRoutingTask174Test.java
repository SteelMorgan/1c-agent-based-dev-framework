package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.validator.GenValidator;
import io.github.onec.xmlgen.validator.Severity;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationLevel;
import io.github.onec.xmlgen.validator.ValidatorFactory;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ValidateRoutingTask174Test {

    @TempDir
    Path tempDir;

    private final XmlStructureReader reader = new XmlStructureReader();

    @Test
    void validateTypeConfigRunsConfigValidator() throws Exception {
        Path file = write("Configuration.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");

        List<ValidationIssue> issues = validate(file, "config");

        assertThat(issues).anyMatch(i -> i.getSeverity() == Severity.ERROR
                && i.getCode().startsWith("CONFIG-")
                && i.getMessage().contains("Properties"));
    }

    @Test
    void validateTypeSubsystemRunsSubsystemValidator() throws Exception {
        Path file = write("Subsystem.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\">\n"
                        + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Subsystem>\n"
                        + "</MetaDataObject>\n");

        List<ValidationIssue> issues = validate(file, "subsystem");

        assertThat(issues).anyMatch(i -> i.getSeverity() == Severity.ERROR
                && i.getCode().startsWith("SUBSYSTEM-")
                && i.getMessage().contains("Properties"));
    }

    @Test
    void validateTypeInterfaceRunsInterfaceValidator() throws Exception {
        Path file = write("CommandInterface.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<CommandInterface version=\"2.17\">\n"
                        + "\t<CommandsVisibility>\n"
                        + "\t\t<Command/>\n"
                        + "\t</CommandsVisibility>\n"
                        + "</CommandInterface>\n");

        List<ValidationIssue> issues = validate(file, "interface");

        assertThat(issues).anyMatch(i -> i.getSeverity() == Severity.ERROR
                && i.getCode().startsWith("INTERFACE-")
                && i.getMessage().contains("missing 'name'"));
    }

    @Test
    void validateParserAcceptsSrcRootAtFirstPosition() {
        Path srcRoot = tempDir.resolve("src");
        Path file = tempDir.resolve("Form.xml");

        Commands.ValidateOptions options = Commands.parseValidateOptions(new String[]{
                "--src-root", srcRoot.toString(),
                "--type", "form",
                file.toString()
        });

        assertThat(options.srcRoot()).isEqualTo(srcRoot);
        assertThat(options.type()).isEqualTo("form");
        assertThat(options.files()).containsExactly(file);
    }

    @Test
    void validateParserRejectsUnknownOptionsAndValues() {
        assertThatThrownBy(() -> Commands.parseValidateOptions(new String[]{"--output", "yaml", "Form.xml"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown --output value");
        assertThatThrownBy(() -> Commands.parseValidateOptions(new String[]{"--level", "deep", "Form.xml"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown --level value");
        assertThatThrownBy(() -> Commands.parseValidateOptions(new String[]{"--format", "plain", "Form.xml"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown --format value");
        assertThatThrownBy(() -> Commands.parseValidateOptions(new String[]{"--bogus", "Form.xml"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown validate option");
        assertThatThrownBy(() -> Commands.parseValidateOptions(new String[]{"--type"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--type requires a value");
    }

    @Test
    void genericValidateDetectsConfigAndRunsConfigValidator() throws Exception {
        Path file = write("Configuration.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        XmlDocument document = reader.parse(file);

        String detected = Commands.detectTypeByRoot(document);
        List<ValidationIssue> issues = validate(file, detected);

        assertThat(detected).isEqualTo("config");
        assertThat(issues).anyMatch(i -> i.getSeverity() == Severity.ERROR
                && i.getCode().startsWith("CONFIG-")
                && i.getMessage().contains("Properties"));
    }

    @Test
    void genericValidateDetectsExtensionMetaAndInterface() throws Exception {
        Path extension = write("ExtensionConfiguration.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose></Properties>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        Path meta = write("Catalog.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Catalog uuid=\"00000000-0000-0000-0000-000000000001\"/>\n"
                        + "</MetaDataObject>\n");
        Path ci = write("CommandInterface.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\" version=\"2.20\"/>\n");
        Path clientInterface = write("ClientApplicationInterface.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<ClientApplicationInterface xmlns=\"http://v8.1c.ru/8.2/managed-application/core\"/>\n");
        Path cmiSection = write("section.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<section xmlns=\"http://v8.1c.ru/8.2/managed-application/cmi\"/>\n");

        assertThat(Commands.detectTypeByRoot(reader.parse(extension))).isEqualTo("extension");
        assertThat(Commands.detectTypeByRoot(reader.parse(meta))).isEqualTo("meta");
        assertThat(Commands.detectTypeByRoot(reader.parse(ci))).isEqualTo("interface");
        assertThat(Commands.detectTypeByRoot(reader.parse(clientInterface))).isEqualTo("client-interface");
        assertThat(Commands.detectTypeByRoot(reader.parse(cmiSection))).isEqualTo("platform-xsd");
    }

    @Test
    void validationExitCodeKeepsWarningsOnlyAsCode2() {
        assertThat(Commands.validationExitCode(0, 0)).isZero();
        assertThat(Commands.validationExitCode(1, 0)).isEqualTo(1);
        assertThat(Commands.validationExitCode(1, 3)).isEqualTo(1);
        assertThat(Commands.validationExitCode(0, 2)).isEqualTo(2);
    }

    @Test
    void validateEdtFormNamespaceDoesNotUseDesignerExpectation() throws Exception {
        Path file = write("Form.form",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form xmlns=\"http://g5.1c.ru/v8/dt/form\" version=\"2.20\">\n"
                        + "\t<ChildItems/>\n"
                        + "</Form>\n");
        XmlDocument document = reader.parse(file);

        List<ValidationIssue> issues = Commands.validateDocumentForType(
                document,
                "form",
                file,
                "edt",
                ValidationLevel.STRUCTURE,
                new GenValidator(),
                new ValidatorFactory());

        assertThat(issues).noneMatch(i -> i.getCode().equals("GEN-005"));
    }

    @Test
    void structureLevelDoesNotRunSemanticTypeChecks() throws Exception {
        Path srcRoot = tempDir.resolve("src");
        Files.createDirectories(srcRoot);
        Path file = write("Form.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" "
                        + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
                        + "xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\">\n"
                        + "\t<Attributes><Attribute name=\"Ref\"><Type><v8:Type>cfg:CatalogRef.Missing</v8:Type></Type></Attribute></Attributes>\n"
                        + "\t<ChildItems/>\n"
                        + "</Form>\n");
        XmlDocument document = reader.parse(file);

        List<ValidationIssue> issues = Commands.validateDocumentForType(
                document,
                "form",
                file,
                "designer",
                ValidationLevel.STRUCTURE,
                new GenValidator(new io.github.onec.xmlgen.validator.MetadataTypeValidator(srcRoot)),
                new ValidatorFactory());

        assertThat(issues).noneMatch(i -> i.getCode().equals("SEM-001"));
    }

    @Test
    void validateConfigWrongNamespaceIsGen005Error() throws Exception {
        Path file = write("Configuration.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://wrong.namespace\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>AuditCfg</Name></Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        XmlDocument document = reader.parse(file);

        List<ValidationIssue> issues = Commands.validateDocumentForType(
                document,
                "config",
                file,
                "designer",
                ValidationLevel.STRUCTURE,
                new GenValidator(),
                new ValidatorFactory());

        assertThat(issues).anyMatch(i -> i.getCode().equals("GEN-005")
                && i.getSeverity() == Severity.ERROR);
    }

    private List<ValidationIssue> validate(Path file, String type) throws Exception {
        XmlDocument document = reader.parse(file);
        return Commands.validateDocumentForType(
                document,
                type,
                file,
                "edt",
                ValidationLevel.SEMANTIC,
                new GenValidator(),
                new ValidatorFactory());
    }

    private Path write(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

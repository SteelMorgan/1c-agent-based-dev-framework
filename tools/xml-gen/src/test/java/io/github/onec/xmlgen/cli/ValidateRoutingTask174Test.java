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

import static org.assertj.core.api.Assertions.assertThat;

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

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

class SubsystemInterfaceRollbackCliTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unit-subsystem edit invalid boolean fails and leaves bytes unchanged")
    void subsystemEditInvalidBoolean_rollsBackWithoutChangingFile() throws Exception {
        Path subsystemXml = writeSubsystemXml();
        byte[] before = Files.readAllBytes(subsystemXml);

        ProcessResult result = runMain(
                "subsystem", "edit", subsystemXml.toString(),
                "--op", "set-property",
                "--value", "IncludeInCommandInterface=maybe");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr())
                .contains("IncludeInCommandInterface")
                .contains("maybe")
                .contains("invalid");
        assertThat(Files.readAllBytes(subsystemXml)).isEqualTo(before);
    }

    @Test
    @DisplayName("unit-interface edit place with broken refs fails and leaves bytes unchanged")
    void interfaceEditPlaceBrokenRefs_rollsBackWithoutChangingFile() throws Exception {
        Path commandInterfaceXml = writeCommandInterfaceXml();
        byte[] before = Files.readAllBytes(commandInterfaceXml);

        ProcessResult result = runMain(
                "interface", "edit", commandInterfaceXml.toString(),
                "--op", "place",
                "--value", "command=CommonCommand.Nope group=CommandGroup.X");

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr())
                .contains("CommonCommand.Nope")
                .contains("CommandGroup.X");
        assertThat(Files.readAllBytes(commandInterfaceXml)).isEqualTo(before);
    }

    private Path writeSubsystemXml() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("Subsystems"));
        Files.writeString(configRoot.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\"/>\n",
                StandardCharsets.UTF_8);

        Path subsystemXml = configRoot.resolve("Subsystems").resolve("Main.xml");
        Files.writeString(subsystemXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                        + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" version=\"2.17\">\n"
                        + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>Main</Name>\n"
                        + "\t\t\t<Synonym>Main</Synonym>\n"
                        + "\t\t\t<IncludeInCommandInterface>true</IncludeInCommandInterface>\n"
                        + "\t\t\t<UseOneCommand>false</UseOneCommand>\n"
                        + "\t\t\t<IncludeHelpInContents>true</IncludeHelpInContents>\n"
                        + "\t\t\t<Content/>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Subsystem>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        return subsystemXml;
    }

    private Path writeCommandInterfaceXml() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("Subsystems/Main/Ext"));
        Files.createDirectories(configRoot.resolve("CommonCommands"));
        Files.createDirectories(configRoot.resolve("CommandGroups"));
        Files.writeString(configRoot.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.17\"/>\n",
                StandardCharsets.UTF_8);

        Path commandInterfaceXml = configRoot.resolve("Subsystems/Main/Ext/CommandInterface.xml");
        Files.writeString(commandInterfaceXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\" version=\"2.17\">\n"
                        + "</CommandInterface>\n",
                StandardCharsets.UTF_8);
        return commandInterfaceXml;
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

package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceValidatorTask174Test {

    private static final String CI_NS = "http://v8.1c.ru/8.3/xcf/extrnprops";
    private static final String XR_NS = "http://v8.1c.ru/8.3/xcf/readable";

    private final XmlStructureReader reader = new XmlStructureReader();
    private final InterfaceValidator validator = new InterfaceValidator();

    @TempDir
    Path tempDir;

    @Test
    void validatesCommandInterfaceNamespace() throws Exception {
        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="http://v8.1c.ru/8.1/meta/ordinary" version="2.17">
                </CommandInterface>
                """);

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), null);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("namespace"));
    }

    @Test
    void warnsAboutCommandReferencesWithWrongSegmentCount() throws Exception {
        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="%s" xmlns:xr="%s" version="2.17">
                    <CommandsVisibility>
                        <Command name="Catalog.Товары.Command">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                        <Command name="Catalog.Товары.Command.Печать.Extra">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                    </CommandsVisibility>
                </CommandInterface>
                """.formatted(CI_NS, XR_NS));

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), null);

        assertThat(messages).anyMatch(m ->
                "WARN".equals(m.level) && m.message.contains("Catalog.Товары.Command"));
        assertThat(messages).anyMatch(m ->
                "WARN".equals(m.level) && m.message.contains("Catalog.Товары.Command.Печать.Extra"));
    }

    @Test
    void checksCommonCommandReferenceExistenceWhenConfigRootIsKnown() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("CommonCommands"));
        Files.writeString(configRoot.resolve("CommonCommands/Настройки.xml"),
                "<MetaDataObject/>", StandardCharsets.UTF_8);

        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="%s" xmlns:xr="%s" version="2.17">
                    <CommandsVisibility>
                        <Command name="CommonCommand.Настройки">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                        <Command name="CommonCommand.НетТакой">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                    </CommandsVisibility>
                </CommandInterface>
                """.formatted(CI_NS, XR_NS));

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), configRoot);

        assertThat(messages).noneMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommonCommand.Настройки"));
        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommonCommand.НетТакой"));
    }

    @Test
    void checksCustomCommandGroupReferenceExistenceWhenConfigRootIsKnown() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("CommonCommands"));
        Files.createDirectories(configRoot.resolve("CommandGroups"));
        Files.writeString(configRoot.resolve("CommonCommands/Настройки.xml"),
                "<MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(configRoot.resolve("CommonCommands/Сервис.xml"),
                "<MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(configRoot.resolve("CommandGroups/Отчеты.xml"),
                "<MetaDataObject/>", StandardCharsets.UTF_8);

        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="%s" version="2.17">
                    <CommandsPlacement>
                        <Command name="CommonCommand.Настройки">
                            <CommandGroup>CommandGroup.Отчеты</CommandGroup>
                            <Placement>Auto</Placement>
                        </Command>
                        <Command name="CommonCommand.Сервис">
                            <CommandGroup>CommandGroup.НетТакой</CommandGroup>
                            <Placement>Auto</Placement>
                        </Command>
                    </CommandsPlacement>
                </CommandInterface>
                """.formatted(CI_NS));

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), configRoot);

        assertThat(messages).noneMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommandGroup.Отчеты"));
        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommandGroup.НетТакой"));
    }

    @Test
    void checksCommonFormCommandReferenceExistenceWhenConfigRootIsKnown() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Files.createDirectories(configRoot.resolve("CommonForms"));
        Files.writeString(configRoot.resolve("CommonForms/Настройки.xml"),
                "<MetaDataObject/>", StandardCharsets.UTF_8);

        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="%s" xmlns:xr="%s" version="2.17">
                    <CommandsVisibility>
                        <Command name="CommonForm.Настройки.Command.Открыть">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                        <Command name="CommonForm.НетТакой.Command.Открыть">
                            <Visibility><xr:Common>true</xr:Common></Visibility>
                        </Command>
                    </CommandsVisibility>
                </CommandInterface>
                """.formatted(CI_NS, XR_NS));

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), configRoot);

        assertThat(messages).noneMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommonForm.Настройки"));
        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("CommonForm.НетТакой"));
    }

    @Test
    void warnsAboutMalformedSubsystemOrderPath() throws Exception {
        Path ci = writeCi("""
                <?xml version="1.0" encoding="UTF-8"?>
                <CommandInterface xmlns="%s" version="2.17">
                    <SubsystemsOrder>
                        <Subsystem>Subsystem.Продажи.Bad.Розница</Subsystem>
                    </SubsystemsOrder>
                </CommandInterface>
                """.formatted(CI_NS));

        List<InterfaceValidator.ValidationMessage> messages =
                validator.validate(reader.parse(ci), null);

        assertThat(messages).anyMatch(m ->
                "WARN".equals(m.level) && m.message.contains("Subsystem.Продажи.Bad.Розница"));
    }

    private Path writeCi(String content) throws Exception {
        Path file = tempDir.resolve("CommandInterface-" + System.nanoTime() + ".xml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

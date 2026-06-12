package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TASK-171: регрессия на вычисление configRoot для существование-чека в
 * SubsystemValidator и InterfaceValidator.
 *
 * <p>Корень дефекта (Commands.java): configRoot брался как target.getParent()
 * (= .../Subsystems), а для вложенных подсистем — ещё глубже. Из-за этого
 * existence-чек Content/command-ref искал объекты в Subsystems/Catalogs/... и
 * ложно ругался ERROR на существующих объектах (9/10 _Демо-подсистем, 6/8 CI).
 *
 * <p>Фикс — walk-up до Configuration.xml. Этот тест воспроизводит config-layout
 * (включая критический кейс вложенной подсистемы Parent/Subsystems/Child.xml) и
 * доказывает: с КОРРЕКТНЫМ корнем (walk-up) ложных ERROR нет, а со СТАРЫМ корнем
 * (getParent) они появляются — то есть фикс действительно нужен.
 *
 * <p>Резолвер ниже повторяет логику Commands.locateConfigRoot (private static,
 * недоступен из теста) — это контракт, на который опирается фикс в Commands.
 */
class SubsystemInterfaceConfigRootTask171Test {

    @TempDir
    Path tempDir;

    // --- повтор контракта Commands.locateConfigRoot ---
    private static Path locateConfigRoot(Path start) {
        Path dir = start.toAbsolutePath().normalize();
        if (Files.isRegularFile(dir)) dir = dir.getParent();
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("Configuration.xml"))) return dir;
            dir = dir.getParent();
        }
        return null;
    }

    private Path configRoot;

    private Path buildConfig() throws IOException {
        configRoot = tempDir.resolve("src").resolve("xml");
        Files.createDirectories(configRoot.resolve("Catalogs"));
        Files.createDirectories(configRoot.resolve("DataProcessors"));
        Files.writeString(configRoot.resolve("Configuration.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(configRoot.resolve("Catalogs").resolve("Партнеры.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(configRoot.resolve("DataProcessors").resolve("КонсольЗапросов.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        return configRoot;
    }

    private XmlDocument parse(Path file) throws Exception {
        return new XmlStructureReader().parse(file);
    }

    private long errors(List<SubsystemValidator.ValidationMessage> msgs) {
        return msgs.stream().filter(m -> "ERROR".equals(m.level)).count();
    }

    private long ifaceErrors(List<InterfaceValidator.ValidationMessage> msgs) {
        return msgs.stream().filter(m -> "ERROR".equals(m.level)).count();
    }

    // ============================================================
    // SubsystemValidator (Task 1 + 3)
    // ============================================================

    private static final String SUBSYSTEM_WITH_CONTENT =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
            + "\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
            + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.17\">\n"
            + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000001\">\n"
            + "\t\t<Properties>\n"
            + "\t\t\t<Name>Тест</Name>\n"
            + "\t\t\t<Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>Тест</v8:content></v8:item></Synonym>\n"
            + "\t\t\t<Content>\n"
            + "\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">Catalog.Партнеры</xr:Item>\n"
            + "\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">DataProcessor.КонсольЗапросов</xr:Item>\n"
            + "\t\t\t</Content>\n"
            + "\t\t</Properties>\n"
            + "\t\t<ChildObjects/>\n"
            + "\t</Subsystem>\n"
            + "</MetaDataObject>\n";

    private static final String SUBSYSTEM_WITH_CHILD =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
            + "\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
            + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.17\">\n"
            + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000002\">\n"
            + "\t\t<Properties>\n"
            + "\t\t\t<Name>Родитель</Name>\n"
            + "\t\t\t<Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>Родитель</v8:content></v8:item></Synonym>\n"
            + "\t\t\t<Content/>\n"
            + "\t\t</Properties>\n"
            + "\t\t<ChildObjects>\n"
            + "\t\t\t<Subsystem>Ребёнок</Subsystem>\n"
            + "\t\t</ChildObjects>\n"
            + "\t</Subsystem>\n"
            + "</MetaDataObject>\n";

    @Test
    void subsystemValidate_topLevel_correctRoot_noFalseErrors() throws Exception {
        buildConfig();
        Path ssFile = configRoot.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_WITH_CONTENT, StandardCharsets.UTF_8);

        Path root = locateConfigRoot(ssFile);
        assertThat(root).isEqualTo(configRoot);

        List<SubsystemValidator.ValidationMessage> msgs =
                new SubsystemValidator().validate(parse(ssFile), root);
        assertThat(errors(msgs)).as("no false 'not found' errors with correct root").isZero();
    }

    @Test
    void subsystemValidate_topLevel_oldGetParentRoot_producesFalseErrors() throws Exception {
        buildConfig();
        Path ssFile = configRoot.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_WITH_CONTENT, StandardCharsets.UTF_8);

        // Старый (баговый) корень = getParent() = .../Subsystems → промах мимо src/xml.
        Path buggyRoot = ssFile.getParent();
        List<SubsystemValidator.ValidationMessage> msgs =
                new SubsystemValidator().validate(parse(ssFile), buggyRoot);
        assertThat(errors(msgs)).as("buggy getParent root falsely flags existing objects").isPositive();
    }

    @Test
    void subsystemValidate_nestedSubsystem_correctRoot_noFalseErrors() throws Exception {
        // Критический walk-up кейс: Parent/Subsystems/Child.xml → 3 уровня до корня.
        buildConfig();
        Path ssFile = configRoot.resolve("Subsystems").resolve("Родитель")
                .resolve("Subsystems").resolve("Ребёнок.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_WITH_CONTENT, StandardCharsets.UTF_8);

        Path root = locateConfigRoot(ssFile);
        assertThat(root).isEqualTo(configRoot);

        List<SubsystemValidator.ValidationMessage> msgs =
                new SubsystemValidator().validate(parse(ssFile), root);
        assertThat(errors(msgs)).isZero();
    }

    @Test
    void subsystemValidate_topLevelChildren_usesSubsystemXmlParentForFileChecks() throws Exception {
        buildConfig();
        Path ssFile = configRoot.resolve("Subsystems").resolve("Родитель.xml");
        Path childFile = configRoot.resolve("Subsystems").resolve("Родитель")
                .resolve("Subsystems").resolve("Ребёнок.xml");
        Files.createDirectories(childFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_WITH_CHILD, StandardCharsets.UTF_8);
        Files.writeString(childFile, SUBSYSTEM_WITH_CONTENT, StandardCharsets.UTF_8);

        List<SubsystemValidator.ValidationMessage> msgs =
                new SubsystemValidator().validate(parse(ssFile), configRoot, ssFile);

        assertThat(msgs)
                .noneMatch(m -> m.message.contains("File missing"));
    }

    // ============================================================
    // InterfaceValidator (Task 2 + 3)
    // ============================================================

    private static final String CI_WITH_REFS =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
            + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.17\">\n"
            + "\t<CommandsVisibility>\n"
            + "\t\t<Command name=\"Catalog.Партнеры.StandardCommand.OpenList\">\n"
            + "\t\t\t<Visibility><xr:Common>true</xr:Common></Visibility>\n"
            + "\t\t</Command>\n"
            + "\t\t<Command name=\"DataProcessor.КонсольЗапросов.Command.КонсольЗапросов\">\n"
            + "\t\t\t<Visibility><xr:Common>true</xr:Common></Visibility>\n"
            + "\t\t</Command>\n"
            + "\t</CommandsVisibility>\n"
            + "</CommandInterface>\n";

    private static final String CI_WITH_MISSING_COMMON_COMMAND =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" version=\"2.17\">\n"
            + "\t<CommandsVisibility>\n"
            + "\t\t<Command name=\"CommonCommand.НетТакойКоманды\">\n"
            + "\t\t\t<Visibility><xr:Common>true</xr:Common></Visibility>\n"
            + "\t\t</Command>\n"
            + "\t</CommandsVisibility>\n"
            + "</CommandInterface>\n";

    private static final String CI_WITH_MISSING_GROUP =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\" version=\"2.17\">\n"
            + "\t<CommandsPlacement>\n"
            + "\t\t<Command name=\"Catalog.Партнеры.StandardCommand.OpenList\">\n"
            + "\t\t\t<CommandGroup>CommandGroup.НетТакойГруппы</CommandGroup>\n"
            + "\t\t\t<Placement>Auto</Placement>\n"
            + "\t\t</Command>\n"
            + "\t</CommandsPlacement>\n"
            + "</CommandInterface>\n";

    private static final String CI_WITH_MISSING_SUBSYSTEM =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\" version=\"2.17\">\n"
            + "\t<SubsystemsOrder>\n"
            + "\t\t<Subsystem>Subsystem.НетТакойПодсистемы</Subsystem>\n"
            + "\t</SubsystemsOrder>\n"
            + "</CommandInterface>\n";

    private Path writeCI(Path subsystemDir) throws IOException {
        Path ext = subsystemDir.resolve("Ext");
        Files.createDirectories(ext);
        Path ci = ext.resolve("CommandInterface.xml");
        Files.writeString(ci, CI_WITH_REFS, StandardCharsets.UTF_8);
        return ci;
    }

    @Test
    void interfaceValidate_topLevel_correctRoot_noFalseErrors() throws Exception {
        buildConfig();
        Path ci = writeCI(configRoot.resolve("Subsystems").resolve("Тест"));

        Path root = locateConfigRoot(ci);
        assertThat(root).isEqualTo(configRoot);

        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), root);
        assertThat(ifaceErrors(msgs)).isZero();
    }

    @Test
    void interfaceValidate_topLevel_oldGetParentChainRoot_producesFalseErrors() throws Exception {
        buildConfig();
        Path ci = writeCI(configRoot.resolve("Subsystems").resolve("Тест"));

        // Старый корень = Ext → SubsystemName → Subsystems (3× getParent) = .../Subsystems.
        Path buggyRoot = ci.getParent().getParent().getParent();
        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), buggyRoot);
        assertThat(ifaceErrors(msgs)).as("buggy root falsely flags existing command objects").isPositive();
    }

    @Test
    void interfaceValidate_nestedSubsystem_correctRoot_noFalseErrors() throws Exception {
        buildConfig();
        Path ci = writeCI(configRoot.resolve("Subsystems").resolve("Родитель")
                .resolve("Subsystems").resolve("Ребёнок"));

        Path root = locateConfigRoot(ci);
        assertThat(root).isEqualTo(configRoot);

        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), root);
        assertThat(ifaceErrors(msgs)).isZero();
    }

    @Test
    void interfaceValidate_missingCommonCommand_isError() throws Exception {
        buildConfig();
        Path ci = tempDir.resolve("CommandInterface.xml");
        Files.writeString(ci, CI_WITH_MISSING_COMMON_COMMAND, StandardCharsets.UTF_8);

        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), configRoot);

        assertThat(msgs)
                .anyMatch(m -> "ERROR".equals(m.level)
                        && m.message.contains("non-existent CommonCommand"));
    }

    @Test
    void interfaceValidate_missingCommandGroup_isError() throws Exception {
        buildConfig();
        Path ci = tempDir.resolve("CommandInterface.xml");
        Files.writeString(ci, CI_WITH_MISSING_GROUP, StandardCharsets.UTF_8);

        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), configRoot);

        assertThat(msgs)
                .anyMatch(m -> "ERROR".equals(m.level)
                        && m.message.contains("non-existent CommandGroup"));
    }

    @Test
    void interfaceValidate_subsystemsOrderChecksExistingPath() throws Exception {
        buildConfig();
        Path ci = tempDir.resolve("CommandInterface.xml");
        Files.writeString(ci, CI_WITH_MISSING_SUBSYSTEM, StandardCharsets.UTF_8);

        List<InterfaceValidator.ValidationMessage> msgs =
                new InterfaceValidator().validate(parse(ci), configRoot);

        assertThat(msgs)
                .anyMatch(m -> "ERROR".equals(m.level)
                        && m.message.contains("non-existent subsystem"));
    }
}

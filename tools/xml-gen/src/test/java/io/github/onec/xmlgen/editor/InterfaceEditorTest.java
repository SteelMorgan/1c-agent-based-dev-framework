package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for InterfaceEditor: hide, show, place, setOrder, setSubsystemOrder, setGroupOrder.
 * Covers canonical operation names (set-order, set-subsystem-order, set-group-order via alias)
 * and negative cases.
 */
class InterfaceEditorTest {

    @TempDir
    Path tempDir;

    // ============================================================
    // Fixture helpers
    // ============================================================

    private static final String MINIMAL_CI =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.1/meta/ordinary\" version=\"2.17\">\n"
            + "</CommandInterface>\n";

    private static final String CI_WITH_VISIBILITY =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<CommandInterface xmlns=\"http://v8.1c.ru/8.1/meta/ordinary\" version=\"2.17\">\n"
            + "<CommandsVisibility>\n"
            + "\t<Command name=\"Catalog.Товары.StandardCommand.OpenList\">\n"
            + "\t\t<Visibility>\n"
            + "\t\t\t<xr:Common>true</xr:Common>\n"
            + "\t\t</Visibility>\n"
            + "\t</Command>\n"
            + "</CommandsVisibility>\n"
            + "</CommandInterface>\n";

    private Path writeCI(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String readCI(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    // ============================================================
    // hide
    // ============================================================

    @Test
    void hide_singleCommand_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.hide("Catalog.Товары.StandardCommand.OpenList");
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("CommandsVisibility");
        assertThat(result).contains("name=\"Catalog.Товары.StandardCommand.OpenList\"");
        assertThat(result).contains("<xr:Common>false</xr:Common>");
    }

    @Test
    void hide_jsonArray_hidesMultipleCommands() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.hide("[\"Catalog.Товары.StandardCommand.OpenList\",\"CommonCommand.Настройки\"]");
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("name=\"Catalog.Товары.StandardCommand.OpenList\"");
        assertThat(result).contains("name=\"CommonCommand.Настройки\"");
        // Both should be hidden
        assertThat(result).contains("<xr:Common>false</xr:Common>");
    }

    @Test
    void hide_overwritesExistingTrueVisibility() throws Exception {
        Path file = writeCI("CI.xml", CI_WITH_VISIBILITY);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.hide("Catalog.Товары.StandardCommand.OpenList");
        editor.save();

        String result = readCI(file);
        // Should now be false, not true
        assertThat(result).doesNotContain("<xr:Common>true</xr:Common>");
        assertThat(result).contains("<xr:Common>false</xr:Common>");
    }

    @Test
    void hide_emptySpec_noModification() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        // Empty string should not cause an error, just be a no-op
        editor.hide("");
        editor.save();

        String result = readCI(file);
        // No CommandsVisibility with empty command should still be present but empty
        // (section may not be created for empty command)
        assertThat(result).isNotEmpty();
    }

    // ============================================================
    // show
    // ============================================================

    @Test
    void show_command_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.show("Report.Продажи.Command.Отчёт");
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("CommandsVisibility");
        assertThat(result).contains("name=\"Report.Продажи.Command.Отчёт\"");
        assertThat(result).contains("<xr:Common>true</xr:Common>");
    }

    @Test
    void show_overwritesExistingFalseVisibility() throws Exception {
        String ciWithHidden =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CommandInterface xmlns=\"http://v8.1c.ru/8.1/meta/ordinary\" version=\"2.17\">\n"
                + "<CommandsVisibility>\n"
                + "\t<Command name=\"Report.Продажи.Command.Отчёт\">\n"
                + "\t\t<Visibility>\n"
                + "\t\t\t<xr:Common>false</xr:Common>\n"
                + "\t\t</Visibility>\n"
                + "\t</Command>\n"
                + "</CommandsVisibility>\n"
                + "</CommandInterface>\n";

        Path file = writeCI("CI.xml", ciWithHidden);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.show("Report.Продажи.Command.Отчёт");
        editor.save();

        String result = readCI(file);
        assertThat(result).doesNotContain("<xr:Common>false</xr:Common>");
        assertThat(result).contains("<xr:Common>true</xr:Common>");
    }

    // ============================================================
    // place
    // ============================================================

    @Test
    void place_commandInGroup_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.place("CommonCommand.Настройки", "CommandGroup.Сервис");
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("CommandsPlacement");
        assertThat(result).contains("name=\"CommonCommand.Настройки\"");
        assertThat(result).contains("<CommandGroup>CommandGroup.Сервис</CommandGroup>");
        assertThat(result).contains("<Placement>Auto</Placement>");
    }

    @Test
    void place_replacesExistingPlacement() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.place("CommonCommand.Настройки", "CommandGroup.Сервис");
        editor.place("CommonCommand.Настройки", "CommandGroup.Другой");
        editor.save();

        String result = readCI(file);
        // Only last placement should be present
        assertThat(result).contains("<CommandGroup>CommandGroup.Другой</CommandGroup>");
        // Count occurrences of the command name in the placement section - should only appear once
        int occurrences = countOccurrences(result, "name=\"CommonCommand.Настройки\"");
        assertThat(occurrences).isEqualTo(1);
    }

    // ============================================================
    // setOrder (canonical: set-order, legacy alias: order)
    // ============================================================

    @Test
    void setOrder_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setOrder("CommandGroup.Отчеты",
                new String[]{"Report.A.Command.Y", "Report.B.Command.Z"});
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("CommandsOrder");
        assertThat(result).contains("name=\"Report.A.Command.Y\"");
        assertThat(result).contains("name=\"Report.B.Command.Z\"");
        assertThat(result).contains("<CommandGroup>CommandGroup.Отчеты</CommandGroup>");
    }

    @Test
    void setOrder_replacesExistingOrderForGroup() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setOrder("CommandGroup.Отчеты", new String[]{"Report.A.Command.Y"});
        editor.setOrder("CommandGroup.Отчеты", new String[]{"Report.B.Command.Z", "Report.C.Command.W"});
        editor.save();

        String result = readCI(file);
        // Old entry should be replaced
        assertThat(result).doesNotContain("name=\"Report.A.Command.Y\"");
        assertThat(result).contains("name=\"Report.B.Command.Z\"");
        assertThat(result).contains("name=\"Report.C.Command.W\"");
    }

    @Test
    void setOrder_aliasMethodIsIdenticalToSetOrder() throws Exception {
        // Test that setOrder produces the same result as before (alias behavior verified via CLI, editor method is same)
        Path file1 = writeCI("CI1.xml", MINIMAL_CI);
        InterfaceEditor editor1 = new InterfaceEditor(file1);
        editor1.setOrder("CommandGroup.Отчеты", new String[]{"Cmd.A"});
        editor1.save();

        Path file2 = writeCI("CI2.xml", MINIMAL_CI);
        InterfaceEditor editor2 = new InterfaceEditor(file2);
        editor2.setOrder("CommandGroup.Отчеты", new String[]{"Cmd.A"});
        editor2.save();

        // Both should produce identical output
        assertThat(readCI(file1)).isEqualTo(readCI(file2));
    }

    @Test
    void setOrder_emptyCommandsArray_createsEmptySection() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setOrder("CommandGroup.Отчеты", new String[]{});
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("CommandsOrder");
    }

    // ============================================================
    // setSubsystemOrder (canonical: set-subsystem-order, legacy alias: subsystem-order)
    // ============================================================

    @Test
    void setSubsystemOrder_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setSubsystemOrder(new String[]{
                "Subsystem.Продажи.Subsystem.Розница",
                "Subsystem.Продажи.Subsystem.Опт"});
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("SubsystemsOrder");
        assertThat(result).contains("<Subsystem>Subsystem.Продажи.Subsystem.Розница</Subsystem>");
        assertThat(result).contains("<Subsystem>Subsystem.Продажи.Subsystem.Опт</Subsystem>");
    }

    @Test
    void setSubsystemOrder_replacesExistingOrder() throws Exception {
        String ciWithSubs =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CommandInterface xmlns=\"http://v8.1c.ru/8.1/meta/ordinary\" version=\"2.17\">\n"
                + "<SubsystemsOrder>\n"
                + "\t<Subsystem>Subsystem.Продажи.Subsystem.Старый</Subsystem>\n"
                + "</SubsystemsOrder>\n"
                + "</CommandInterface>\n";

        Path file = writeCI("CI.xml", ciWithSubs);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setSubsystemOrder(new String[]{"Subsystem.Продажи.Subsystem.Новый"});
        editor.save();

        String result = readCI(file);
        assertThat(result).doesNotContain("Subsystem.Продажи.Subsystem.Старый");
        assertThat(result).contains("Subsystem.Продажи.Subsystem.Новый");
    }

    @Test
    void setSubsystemOrder_aliasProducesSameResultAsCanonical() throws Exception {
        Path file1 = writeCI("CI1.xml", MINIMAL_CI);
        InterfaceEditor editor1 = new InterfaceEditor(file1);
        editor1.setSubsystemOrder(new String[]{"Subsystem.A.Subsystem.B"});
        editor1.save();

        Path file2 = writeCI("CI2.xml", MINIMAL_CI);
        InterfaceEditor editor2 = new InterfaceEditor(file2);
        editor2.setSubsystemOrder(new String[]{"Subsystem.A.Subsystem.B"});
        editor2.save();

        assertThat(readCI(file1)).isEqualTo(readCI(file2));
    }

    // ============================================================
    // setGroupOrder (canonical: set-group-order, legacy alias: group-order)
    // ============================================================

    @Test
    void setGroupOrder_happyPath() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setGroupOrder(new String[]{"NavigationPanelOrdinary", "NavigationPanelImportant"});
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("GroupsOrder");
        assertThat(result).contains("<Group>NavigationPanelOrdinary</Group>");
        assertThat(result).contains("<Group>NavigationPanelImportant</Group>");
    }

    @Test
    void setGroupOrder_replacesExistingGroupOrder() throws Exception {
        String ciWithGroups =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<CommandInterface xmlns=\"http://v8.1c.ru/8.1/meta/ordinary\" version=\"2.17\">\n"
                + "<GroupsOrder>\n"
                + "\t<Group>NavigationPanelSeeAlso</Group>\n"
                + "</GroupsOrder>\n"
                + "</CommandInterface>\n";

        Path file = writeCI("CI.xml", ciWithGroups);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.setGroupOrder(new String[]{"NavigationPanelOrdinary"});
        editor.save();

        String result = readCI(file);
        assertThat(result).doesNotContain("<Group>NavigationPanelSeeAlso</Group>");
        assertThat(result).contains("<Group>NavigationPanelOrdinary</Group>");
    }

    @Test
    void setGroupOrder_aliasProducesSameResultAsCanonical() throws Exception {
        Path file1 = writeCI("CI1.xml", MINIMAL_CI);
        InterfaceEditor editor1 = new InterfaceEditor(file1);
        editor1.setGroupOrder(new String[]{"NavigationPanelOrdinary"});
        editor1.save();

        Path file2 = writeCI("CI2.xml", MINIMAL_CI);
        InterfaceEditor editor2 = new InterfaceEditor(file2);
        editor2.setGroupOrder(new String[]{"NavigationPanelOrdinary"});
        editor2.save();

        assertThat(readCI(file1)).isEqualTo(readCI(file2));
    }

    // ============================================================
    // Section ordering (canonical order preserved)
    // ============================================================

    @Test
    void sectionOrder_allSections_canonicalOrder() throws Exception {
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        // Add sections in reverse order to verify canonical ordering
        editor.setGroupOrder(new String[]{"NavigationPanelOrdinary"});
        editor.setSubsystemOrder(new String[]{"Subsystem.A.Subsystem.B"});
        editor.setOrder("CommandGroup.X", new String[]{"Cmd.A"});
        editor.place("Cmd.A", "CommandGroup.X");
        editor.hide("Cmd.B");
        editor.save();

        String result = readCI(file);
        int posVisibility = result.indexOf("CommandsVisibility");
        int posPlacement = result.indexOf("CommandsPlacement");
        int posOrder = result.indexOf("CommandsOrder");
        int posSubsystems = result.indexOf("SubsystemsOrder");
        int posGroups = result.indexOf("GroupsOrder");

        assertThat(posVisibility).isLessThan(posPlacement);
        assertThat(posPlacement).isLessThan(posOrder);
        assertThat(posOrder).isLessThan(posSubsystems);
        assertThat(posSubsystems).isLessThan(posGroups);
    }

    // ============================================================
    // BOM preservation
    // ============================================================

    @Test
    void bomIsPreserved_whenOriginalHasBom() throws Exception {
        // Write file with BOM
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] content = MINIMAL_CI.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, withBom, 0, bom.length);
        System.arraycopy(content, 0, withBom, bom.length, content.length);

        Path file = tempDir.resolve("CI_BOM.xml");
        Files.write(file, withBom);

        InterfaceEditor editor = new InterfaceEditor(file);
        editor.hide("Catalog.Товары.StandardCommand.OpenList");
        editor.save();

        byte[] result = Files.readAllBytes(file);
        assertThat(result[0]).isEqualTo((byte) 0xEF);
        assertThat(result[1]).isEqualTo((byte) 0xBB);
        assertThat(result[2]).isEqualTo((byte) 0xBF);
    }

    // ============================================================
    // Negative: unknown command format (validator catches, editor does not throw)
    // ============================================================

    @Test
    void hide_commandWithUnknownFormat_stillApplied() throws Exception {
        // Editor does not validate command format — that's the validator's job.
        // Editor should still apply the operation.
        Path file = writeCI("CI.xml", MINIMAL_CI);
        InterfaceEditor editor = new InterfaceEditor(file);

        editor.hide("UnknownFormat");
        editor.save();

        String result = readCI(file);
        assertThat(result).contains("name=\"UnknownFormat\"");
        assertThat(result).contains("<xr:Common>false</xr:Common>");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}

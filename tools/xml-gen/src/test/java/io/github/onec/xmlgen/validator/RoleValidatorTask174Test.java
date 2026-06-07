package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.dsl.RoleDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.writer.RoleWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174 (аудит порта, домен роли/подсистемы/расширения/валидатор):
 * матрица применимости право↔тип (ROLE-103), которая раньше не проверялась
 * вовсе (класс XG-04 — validate давал PASS на правах, не существующих у типа),
 * и фикс пресетов RoleWriter (view на DataProcessor/Report эмитил невалидный Read).
 */
class RoleValidatorTask174Test {

    private final RoleValidator validator = new RoleValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private List<ValidationIssue> validateRights(String objectsXml) throws Exception {
        Path file = tempDir.resolve("Rights" + System.nanoTime() + ".xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Rights xmlns=\"http://v8.1c.ru/8.2/roles\" "
                        + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:type=\"Rights\" version=\"2.17\">\n"
                        + "\t<setForNewObjects>false</setForNewObjects>\n"
                        + "\t<setForAttributesByDefault>true</setForAttributesByDefault>\n"
                        + "\t<independentRightsOfChildObjects>false</independentRightsOfChildObjects>\n"
                        + objectsXml
                        + "</Rights>\n",
                StandardCharsets.UTF_8);
        return validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
    }

    private static String objectBlock(String name, String... rights) {
        StringBuilder sb = new StringBuilder();
        sb.append("\t<object>\n\t\t<name>").append(name).append("</name>\n");
        for (String r : rights) {
            sb.append("\t\t<right>\n\t\t\t<name>").append(r)
              .append("</name>\n\t\t\t<value>true</value>\n\t\t</right>\n");
        }
        sb.append("\t</object>\n");
        return sb.toString();
    }

    // ==================== ROLE-103: матрица применимости ====================

    @Test
    void role103_readOnDataProcessor_isWarning() throws Exception {
        // Спека: DataProcessor — только Use, View. Read не существует.
        List<ValidationIssue> issues =
                validateRights(objectBlock("DataProcessor.Загрузка", "Read", "Use", "View"));

        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Read'")
                && i.getSeverity() == Severity.WARNING);
        // Валидные права предупреждений не дают
        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode())
                && (i.getMessage().contains("'Use'") || i.getMessage().contains("'View'")));
    }

    @Test
    void role103_insertOnInformationRegister_isWarning() throws Exception {
        // У регистров нет Insert/Delete/Interactive* — только Read/Update/View/Edit/TotalsControl.
        List<ValidationIssue> issues = validateRights(
                objectBlock("InformationRegister.Цены", "Read", "Update", "Insert", "InteractiveInsert"));

        assertThat(issues).filteredOn(i -> "ROLE-103".equals(i.getCode())).hasSize(2);
        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Insert'"));
        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'InteractiveInsert'"));
    }

    @Test
    void role103_rightsOnEnum_isWarning() throws Exception {
        // Спека: Enum не фигурирует в Rights.xml (тип без прав).
        List<ValidationIssue> issues = validateRights(objectBlock("Enum.ВидыОпераций", "View"));

        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("Enum"));
    }

    @Test
    void role103_nestedAttribute_editAllowed_useNot() throws Exception {
        // Вложенные Attribute/StandardAttribute: только View, Edit.
        List<ValidationIssue> issues = validateRights(
                objectBlock("Catalog.Контрагенты.Attribute.ИНН", "View", "Edit")
                        + objectBlock("Catalog.Контрагенты.StandardAttribute.Code", "Use"));

        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("Catalog.Контрагенты.Attribute.ИНН"));
        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Use'")
                && i.getMessage().contains("StandardAttribute"));
    }

    @Test
    void role103_nestedCommand_onlyView() throws Exception {
        List<ValidationIssue> issues = validateRights(
                objectBlock("Catalog.Контрагенты.Command.Открыть", "View", "Edit"));

        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Edit'"));
        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'View'"));
    }

    @Test
    void role103_validCatalogAndConfigurationRights_noWarning() throws Exception {
        // Полные права Catalog и права Configuration — без ложных срабатываний.
        List<ValidationIssue> issues = validateRights(
                objectBlock("Catalog.Номенклатура", "Read", "Insert", "Update", "Delete",
                        "View", "Edit", "InputByString", "InteractiveInsert")
                        + objectBlock("Configuration.Тест", "Administration", "ThinClient")
                        + objectBlock("Subsystem.Продажи", "View")
                        + objectBlock("Subsystem.Продажи.Subsystem.Розница", "View")
                        + objectBlock("WebService.Exchange.Operation.GetIBParameters", "Use")
                        + objectBlock("SessionParameter.Пользователь", "Get", "Set"));

        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode()));
    }

    @Test
    void role103_tabularSectionNestedAttribute() throws Exception {
        // Document.X.TabularSection.Y.Attribute.Z → View/Edit (берётся ПОСЛЕДНИЙ вид вложенности).
        List<ValidationIssue> issues = validateRights(
                objectBlock("Document.Реализация.TabularSection.Товары.Attribute.Цена", "Edit", "Read"));

        assertThat(issues).anyMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Read'"));
        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode())
                && i.getMessage().contains("'Edit'"));
    }

    // ==================== RoleWriter: пресеты по типам ====================

    @Test
    void presetView_onDataProcessor_emitsUseViewWithoutRead() throws Exception {
        RoleDsl dsl = new RoleDsl("TestRoleDp", null, null, null, null, null,
                List.of(new RoleDsl.ObjectRights("DataProcessor.Загрузка", "view", null, null)),
                null);
        new RoleWriter(OutputFormat.DESIGNER).create(dsl, tempDir);

        String rights = Files.readString(
                tempDir.resolve("Roles/TestRoleDp/Ext/Rights.xml"), StandardCharsets.UTF_8);
        assertThat(rights).contains("<name>Use</name>").contains("<name>View</name>");
        assertThat(rights).doesNotContain("<name>Read</name>");

        // и validate на результате — без ROLE-103
        List<ValidationIssue> issues = validator.validate(
                reader.parse(tempDir.resolve("Roles/TestRoleDp/Ext/Rights.xml")),
                ValidationLevel.SEMANTIC);
        assertThat(issues).noneMatch(i -> "ROLE-103".equals(i.getCode()));
    }

    @Test
    void presetEdit_onInformationRegister_emitsNoInsertDelete() throws Exception {
        RoleDsl dsl = new RoleDsl("TestRoleReg", null, null, null, null, null,
                List.of(new RoleDsl.ObjectRights("InformationRegister.Цены", "edit", null, null)),
                null);
        new RoleWriter(OutputFormat.DESIGNER).create(dsl, tempDir);

        String rights = Files.readString(
                tempDir.resolve("Roles/TestRoleReg/Ext/Rights.xml"), StandardCharsets.UTF_8);
        assertThat(rights).contains("<name>Read</name>").contains("<name>Update</name>")
                .contains("<name>View</name>").contains("<name>Edit</name>");
        assertThat(rights).doesNotContain("<name>Insert</name>")
                .doesNotContain("<name>Delete</name>")
                .doesNotContain("InteractiveInsert");
    }
}

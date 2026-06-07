package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174 (аудит порта, валидатор-трек): FORM-123 (Button без &lt;Type&gt; —
 * прецедент XG-14, Designer молча обрезает кнопку) и FORM-124 (контейнер без
 * ChildItems — прецедент XG-15, Designer молча обрезает контейнер). До этих
 * проверок validate давал PASS на обоих классах поломок (XG-04).
 */
class FormValidatorButtonsContainersTask174Test {

    private final FormValidator validator = new FormValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private static final String NS = "xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\"";

    private List<ValidationIssue> validate(String childItemsBody) throws Exception {
        Path file = tempDir.resolve("Form" + System.nanoTime() + ".xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form " + NS + " version=\"2.17\">\n"
                        + "\t<Title><v8:item><v8:lang>ru</v8:lang><v8:content>Т</v8:content></v8:item></Title>\n"
                        + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                        + "\t<ChildItems>\n" + childItemsBody + "\t</ChildItems>\n"
                        + "</Form>\n",
                StandardCharsets.UTF_8);
        return validator.validate(reader.parse(file), ValidationLevel.STRUCTURE);
    }

    // ==================== FORM-123 (XG-14) ====================

    @Test
    void form123_buttonWithoutType_isError() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<Button name=\"Кнопка1\" id=\"5\">\n"
                        + "\t\t\t<CommandName>Form.Command.Сделать</CommandName>\n"
                        + "\t\t</Button>\n");

        assertThat(issues).anyMatch(i -> "FORM-123".equals(i.getCode())
                && i.getSeverity() == Severity.ERROR
                && i.getMessage().contains("Кнопка1"));
    }

    @Test
    void form123_buttonWithType_noIssue() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<Button name=\"Кнопка1\" id=\"5\">\n"
                        + "\t\t\t<Type>UsualButton</Type>\n"
                        + "\t\t\t<CommandName>Form.Command.Сделать</CommandName>\n"
                        + "\t\t</Button>\n");

        assertThat(issues).noneMatch(i -> "FORM-123".equals(i.getCode()));
    }

    @Test
    void form123_buttonInsideNestedGroup_isFound() throws Exception {
        // Обход рекурсивный: кнопка без Type внутри группы тоже ловится.
        List<ValidationIssue> issues = validate(
                "\t\t<UsualGroup name=\"Группа1\" id=\"2\">\n"
                        + "\t\t\t<ChildItems>\n"
                        + "\t\t\t\t<Button name=\"Вложенная\" id=\"6\"/>\n"
                        + "\t\t\t</ChildItems>\n"
                        + "\t\t</UsualGroup>\n");

        assertThat(issues).anyMatch(i -> "FORM-123".equals(i.getCode())
                && i.getMessage().contains("Вложенная"));
    }

    // ==================== FORM-124 (XG-15) ====================

    @Test
    void form124_usualGroupWithoutChildItems_isWarning() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<UsualGroup name=\"ПустаяГруппа\" id=\"3\"/>\n");

        assertThat(issues).anyMatch(i -> "FORM-124".equals(i.getCode())
                && i.getSeverity() == Severity.WARNING
                && i.getMessage().contains("ПустаяГруппа"));
    }

    @Test
    void form124_pagesWithEmptyChildItems_isWarning() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<Pages name=\"Страницы\" id=\"3\">\n"
                        + "\t\t\t<ChildItems/>\n"
                        + "\t\t</Pages>\n");

        assertThat(issues).anyMatch(i -> "FORM-124".equals(i.getCode())
                && i.getMessage().contains("Страницы"));
    }

    @Test
    void form124_popupWithoutChildItems_isWarning() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<Popup name=\"Подменю\" id=\"7\"/>\n");

        assertThat(issues).anyMatch(i -> "FORM-124".equals(i.getCode())
                && i.getSeverity() == Severity.WARNING
                && i.getMessage().contains("Подменю"));
    }

    @Test
    void form124_filledGroup_noIssue() throws Exception {
        List<ValidationIssue> issues = validate(
                "\t\t<UsualGroup name=\"Группа\" id=\"3\">\n"
                        + "\t\t\t<ChildItems>\n"
                        + "\t\t\t\t<InputField name=\"Поле\" id=\"4\"/>\n"
                        + "\t\t\t</ChildItems>\n"
                        + "\t\t</UsualGroup>\n");

        assertThat(issues).noneMatch(i -> "FORM-124".equals(i.getCode()));
    }

    @Test
    void form124_emptyAutoCommandBar_isAllowed() throws Exception {
        // Спека 1c-form-spec §6: командная панель «может быть пустой (самозакрывающийся тег)».
        List<ValidationIssue> issues = validate("");

        assertThat(issues).noneMatch(i -> "FORM-124".equals(i.getCode()));
    }
}

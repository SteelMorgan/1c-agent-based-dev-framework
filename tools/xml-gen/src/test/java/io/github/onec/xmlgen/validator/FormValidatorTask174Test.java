package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174: FORM-121 (корневой Title обязателен и мультиязычен — класс XG-11)
 * и FORM-122 (пайп внутри одного v8:Type — класс XG-10). Раньше validate давал
 * PASS на формах, которые Designer-batch отвергал (прецедент XG-04).
 */
class FormValidatorTask174Test {

    private final FormValidator validator = new FormValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private static final String NS = "xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" "
            + "xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" "
            + "xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\" "
            + "xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"";

    private static final String GOOD_TITLE =
            "\t<Title>\n"
            + "\t\t<v8:item>\n"
            + "\t\t\t<v8:lang>ru</v8:lang>\n"
            + "\t\t\t<v8:content>Тест</v8:content>\n"
            + "\t\t</v8:item>\n"
            + "\t</Title>\n";

    private List<ValidationIssue> validate(String body) throws Exception {
        Path file = tempDir.resolve("Form" + System.nanoTime() + ".xml");
        Files.writeString(file,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form " + NS + " version=\"2.17\">\n"
                        + body
                        + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                        + "\t<ChildItems/>\n"
                        + "</Form>\n",
                StandardCharsets.UTF_8);
        return validator.validate(reader.parse(file), ValidationLevel.STRUCTURE);
    }

    // ==================== FORM-121 (XG-11) ====================

    @Test
    void form121_missingRootTitle_isAllowedInDesignerCanon() throws Exception {
        // Реальные Designer-формы без корневого Title массовы; валидатор
        // не должен шуметь на каноне. Плоский Title, если он есть, всё ещё
        // ошибка XDTO-сериализации.
        List<ValidationIssue> issues = validate("");

        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-121"));
    }

    @Test
    void form121_plainTextRootTitle_isError() throws Exception {
        List<ValidationIssue> issues = validate("\t<Title>Плоский текст</Title>\n");

        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-121")
                && i.getSeverity() == Severity.ERROR);
    }

    @Test
    void form121_multilingualRootTitle_passes() throws Exception {
        List<ValidationIssue> issues = validate(GOOD_TITLE);

        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-121"));
    }

    // ==================== FORM-122 (XG-10) ====================

    @Test
    void form122_pipeInsideSingleV8Type_isError() throws Exception {
        String body = GOOD_TITLE
                + "\t<Attributes>\n"
                + "\t\t<Attribute name=\"Ссылка\" id=\"1\">\n"
                + "\t\t\t<Type>\n"
                + "\t\t\t\t<v8:Type>cfg:CatalogRef.А | CatalogRef.Б</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t</Attribute>\n"
                + "\t</Attributes>\n";

        List<ValidationIssue> issues = validate(body);

        assertThat(issues).anyMatch(i -> i.getCode().equals("FORM-122")
                && i.getSeverity() == Severity.ERROR);
    }

    @Test
    void form122_separateAdjacentV8Types_passes() throws Exception {
        String body = GOOD_TITLE
                + "\t<Attributes>\n"
                + "\t\t<Attribute name=\"Ссылка\" id=\"1\">\n"
                + "\t\t\t<Type>\n"
                + "\t\t\t\t<v8:Type>cfg:CatalogRef.А</v8:Type>\n"
                + "\t\t\t\t<v8:Type>cfg:CatalogRef.Б</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t</Attribute>\n"
                + "\t</Attributes>\n";

        List<ValidationIssue> issues = validate(body);

        assertThat(issues).noneMatch(i -> i.getCode().equals("FORM-122"));
    }
}

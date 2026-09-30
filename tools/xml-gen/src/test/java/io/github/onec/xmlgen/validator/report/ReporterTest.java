package io.github.onec.xmlgen.validator.report;

import io.github.onec.xmlgen.validator.Severity;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Тесты TextReporter и JsonReporter.
 */
class ReporterTest {

    private final TextReporter textReporter = new TextReporter();
    private final JsonReporter jsonReporter = new JsonReporter();

    @Test
    void testTextReporterNoIssues() {
        ValidationResult result = new ValidationResult(
                Path.of("Rights.xml"), "role", "designer", List.of());

        String output = textReporter.format(result);

        assertThat(output).contains("Validating: Rights.xml");
        assertThat(output).contains("Designer");
        assertThat(output).contains("Role");
        assertThat(output).contains("No issues found");
    }

    @Test
    void testTextReporterWithErrors() {
        ValidationResult result = new ValidationResult(
                Path.of("Rights.xml"), "role", "designer", List.of(
                ValidationIssue.error("GEN-004", "Wrong root element", 1, "/"),
                ValidationIssue.warning("GEN-006", "Invalid UUID", 5, "/@uuid")
        ));

        String output = textReporter.format(result);

        assertThat(output).contains("✗ GEN-004 [ERROR]");
        assertThat(output).contains("Wrong root element");
        assertThat(output).contains("⚠ GEN-006 [WARNING]");
        assertThat(output).contains("line 5");
        assertThat(output).contains("1 error(s)");
        assertThat(output).contains("1 warning(s)");
    }

    @Test
    void testJsonReporterNoIssues() {
        ValidationResult result = new ValidationResult(
                Path.of("Form.xml"), "form", "designer", List.of());

        String output = jsonReporter.format(result);

        assertThat(output).contains("\"valid\": true");
        assertThat(output).contains("\"errors\": 0");
        assertThat(output).contains("\"issues\": [");
    }

    @Test
    void testJsonReporterWithErrors() {
        ValidationResult result = new ValidationResult(
                Path.of("Rights.xml"), "role", "designer", List.of(
                ValidationIssue.error("ROLE-101", "Unknown right 'Чтение'", 15, "/Rights/object[1]/right[1]/name")
        ));

        String output = jsonReporter.format(result);

        assertThat(output).contains("\"valid\": false");
        assertThat(output).contains("\"errors\": 1");
        assertThat(output).contains("\"code\": \"ROLE-101\"");
        assertThat(output).contains("\"severity\": \"error\"");
        assertThat(output).contains("\"line\": 15");
    }

    @Test
    void testJsonReporterEscapesSpecialChars() {
        ValidationResult result = new ValidationResult(
                Path.of("Test.xml"), "role", "designer", List.of(
                ValidationIssue.error("GEN-001", "Parse error at line 5: unexpected \"<\" char", 5, "/")
        ));

        String output = jsonReporter.format(result);

        // Кавычки внутри сообщения должны быть экранированы
        assertThat(output).contains("\\\"<\\\"");
    }
}

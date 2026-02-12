package io.github.onec.xmlgen.validator.report;

import io.github.onec.xmlgen.validator.Severity;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationResult;

/**
 * Текстовый репортер для вывода результатов валидации в консоль.
 */
public class TextReporter {

    /**
     * Отформатировать результат валидации в человекочитаемый текст.
     */
    public String format(ValidationResult result) {
        StringBuilder sb = new StringBuilder();

        // Заголовок
        sb.append("Validating: ").append(result.getFile().getFileName())
                .append(" (").append(capitalize(result.getFormat()))
                .append(", ").append(capitalize(result.getType())).append(")\n");

        if (result.getIssues().isEmpty()) {
            sb.append("\n✓ No issues found\n");
            return sb.toString();
        }

        sb.append('\n');

        // Проблемы
        for (ValidationIssue issue : result.getIssues()) {
            String icon = issue.getSeverity() == Severity.ERROR ? "✗"
                    : issue.getSeverity() == Severity.WARNING ? "⚠" : "ℹ";

            sb.append(icon).append(' ')
                    .append(issue.getCode()).append(" [")
                    .append(issue.getSeverity().name()).append("]");

            if (issue.getLine() > 0) {
                sb.append(" line ").append(issue.getLine());
            }

            sb.append(": ").append(issue.getMessage()).append('\n');

            if (issue.getElement() != null && !issue.getElement().isEmpty()) {
                sb.append("  at: ").append(issue.getElement()).append('\n');
            }

            sb.append('\n');
        }

        // Сводка
        sb.append("Result: ")
                .append(result.errorCount()).append(" error(s), ")
                .append(result.warningCount()).append(" warning(s)");

        long info = result.infoCount();
        if (info > 0) {
            sb.append(", ").append(info).append(" info");
        }
        sb.append('\n');

        return sb.toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}

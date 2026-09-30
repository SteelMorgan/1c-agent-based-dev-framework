package io.github.onec.xmlgen.validator.report;

import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationResult;

/**
 * JSON-репортер для CI / автоматического потребления результатов.
 * <p>
 * Простая реализация без зависимости на Jackson (чтобы не тянуть runtime-зависимость
 * при использовании как библиотеки). Генерирует корректный JSON вручную.
 */
public class JsonReporter {

    /**
     * Отформатировать результат валидации в JSON.
     */
    public String format(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"file\": ").append(jsonString(result.getFile().toString())).append(",\n");
        sb.append("  \"format\": ").append(jsonString(result.getFormat())).append(",\n");
        sb.append("  \"type\": ").append(jsonString(result.getType())).append(",\n");
        sb.append("  \"valid\": ").append(result.isValid()).append(",\n");

        // Summary
        sb.append("  \"summary\": {\n");
        sb.append("    \"errors\": ").append(result.errorCount()).append(",\n");
        sb.append("    \"warnings\": ").append(result.warningCount()).append(",\n");
        sb.append("    \"info\": ").append(result.infoCount()).append("\n");
        sb.append("  },\n");

        // Issues
        sb.append("  \"issues\": [\n");
        for (int i = 0; i < result.getIssues().size(); i++) {
            ValidationIssue issue = result.getIssues().get(i);
            sb.append("    {\n");
            sb.append("      \"severity\": ").append(jsonString(issue.getSeverity().name().toLowerCase())).append(",\n");
            sb.append("      \"code\": ").append(jsonString(issue.getCode())).append(",\n");
            sb.append("      \"message\": ").append(jsonString(issue.getMessage())).append(",\n");
            sb.append("      \"line\": ").append(issue.getLine()).append(",\n");
            sb.append("      \"element\": ").append(jsonString(issue.getElement())).append("\n");
            sb.append("    }");
            if (i < result.getIssues().size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private static String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}

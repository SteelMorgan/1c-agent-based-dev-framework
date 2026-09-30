package io.github.onec.xmlgen.validator;

import lombok.Value;

import java.nio.file.Path;
import java.util.List;

/**
 * Результат валидации одного файла.
 */
@Value
public class ValidationResult {
    /** Путь к проверенному файлу */
    Path file;
    /** Тип объекта: "form", "role", "skd", "mxl", "epf" */
    String type;
    /** Формат: "designer", "edt" */
    String format;
    /** Список обнаруженных проблем */
    List<ValidationIssue> issues;

    /**
     * Файл валиден (нет ошибок уровня ERROR).
     */
    public boolean isValid() {
        return issues.stream().noneMatch(i -> i.getSeverity() == Severity.ERROR);
    }

    /**
     * Количество ошибок.
     */
    public long errorCount() {
        return issues.stream().filter(i -> i.getSeverity() == Severity.ERROR).count();
    }

    /**
     * Количество предупреждений.
     */
    public long warningCount() {
        return issues.stream().filter(i -> i.getSeverity() == Severity.WARNING).count();
    }

    /**
     * Количество INFO.
     */
    public long infoCount() {
        return issues.stream().filter(i -> i.getSeverity() == Severity.INFO).count();
    }
}

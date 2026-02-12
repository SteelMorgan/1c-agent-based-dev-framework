package io.github.onec.xmlgen.validator;

import lombok.Value;

/**
 * Одна проблема, обнаруженная валидатором.
 */
@Value
public class ValidationIssue {
    /** Уровень серьёзности */
    Severity severity;
    /** Код правила, например "GEN-001", "ROLE-101" */
    String code;
    /** Человекочитаемое описание проблемы */
    String message;
    /** Номер строки в файле (0 = неизвестно) */
    int line;
    /** XPath-подобный путь к элементу */
    String element;

    /**
     * Создать ERROR-issue.
     */
    public static ValidationIssue error(String code, String message, int line, String element) {
        return new ValidationIssue(Severity.ERROR, code, message, line, element);
    }

    /**
     * Создать WARNING-issue.
     */
    public static ValidationIssue warning(String code, String message, int line, String element) {
        return new ValidationIssue(Severity.WARNING, code, message, line, element);
    }

    /**
     * Создать INFO-issue.
     */
    public static ValidationIssue info(String code, String message, int line, String element) {
        return new ValidationIssue(Severity.INFO, code, message, line, element);
    }
}

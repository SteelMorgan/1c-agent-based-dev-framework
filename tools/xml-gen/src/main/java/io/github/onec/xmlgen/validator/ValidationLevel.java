package io.github.onec.xmlgen.validator;

/**
 * Уровень валидации.
 */
public enum ValidationLevel {
    /** Level 1: XML-каркас, обязательные элементы, id, BOM */
    STRUCTURE,
    /** Level 2: enum-ы mdclasses, внутренние ссылки, значения */
    SEMANTIC
}

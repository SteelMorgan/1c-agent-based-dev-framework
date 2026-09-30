package io.github.onec.xmlgen.validator;

import java.nio.file.Path;
import java.util.List;

/**
 * Интерфейс валидатора XML-объектов 1С.
 * Каждый тип объекта (Form, Role, SKD, MXL, EPF) реализует свой валидатор.
 */
public interface XmlValidator {

    /**
     * Тип объекта, который валидирует этот валидатор.
     * Например: "form", "role", "skd", "mxl", "epf".
     */
    String objectType();

    /**
     * Выполнить валидацию файла.
     *
     * @param document  распарсенный XML-документ
     * @param level     максимальный уровень проверок (STRUCTURE или SEMANTIC включает оба)
     * @return список обнаруженных проблем
     */
    List<ValidationIssue> validate(XmlDocument document, ValidationLevel level);

    /**
     * Может ли этот валидатор обработать данный документ.
     * Проверяет root element / namespace.
     */
    boolean supports(XmlDocument document);
}

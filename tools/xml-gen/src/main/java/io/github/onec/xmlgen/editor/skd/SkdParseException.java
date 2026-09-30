package io.github.onec.xmlgen.editor.skd;

/**
 * Структурированная ошибка парсинга shorthand-форм SKD edit.
 *
 * <p>Содержит позицию (column, 0-based) для удобной диагностики.
 * Месседж в формате {@code "unexpected token at column 12: expected ':'"}.
 */
public class SkdParseException extends RuntimeException {
    private final int column;

    public SkdParseException(String message, int column) {
        super(message + (column >= 0 ? " (column " + column + ")" : ""));
        this.column = column;
    }

    public int getColumn() {
        return column;
    }
}

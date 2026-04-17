package io.github.onec.xmlgen.form.fromobject;

/**
 * Ошибка режима {@code form compile --from-object}.
 */
public class FromObjectException extends RuntimeException {
    public FromObjectException(String message) {
        super(message);
    }

    public FromObjectException(String message, Throwable cause) {
        super(message, cause);
    }
}

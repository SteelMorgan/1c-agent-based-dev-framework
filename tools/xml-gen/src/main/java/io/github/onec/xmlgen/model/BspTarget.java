package io.github.onec.xmlgen.model;

import java.util.Objects;

/**
 * Объект назначения для назначаемых видов БСП.
 * <p>
 * Формат: {@code ИмяКлассаОбъектаМетаданного.ИмяОбъекта}, например {@code Документ.СчетНаОплату}.
 */
public final class BspTarget {

    private final String objectClass;
    private final String objectName;

    public BspTarget(String objectClass, String objectName) {
        this.objectClass = Objects.requireNonNull(objectClass, "objectClass");
        this.objectName = Objects.requireNonNull(objectName, "objectName");
        if (objectClass.isBlank() || objectName.isBlank()) {
            throw new IllegalArgumentException("BspTarget parts must be non-blank");
        }
    }

    public String objectClass() {
        return objectClass;
    }

    public String objectName() {
        return objectName;
    }

    /** Строковое представление как требуется в BSL: {@code "Документ.СчетНаОплату"}. */
    public String asBslString() {
        return objectClass + "." + objectName;
    }

    /** Парсер. */
    public static BspTarget parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("target is required");
        }
        String s = input.trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("target is blank");
        }
        int dot = s.indexOf('.');
        if (dot < 0 || dot == s.length() - 1) {
            throw new IllegalArgumentException(
                    "target must be in form <Class>.<Name>, e.g. 'Документ.СчетНаОплату' (got '" + input + "')");
        }
        String cls = s.substring(0, dot).trim();
        String nm = s.substring(dot + 1).trim();
        if (cls.isEmpty() || nm.isEmpty()) {
            throw new IllegalArgumentException("target parts blank: '" + input + "'");
        }
        return new BspTarget(cls, nm);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BspTarget)) return false;
        BspTarget that = (BspTarget) o;
        return objectClass.equals(that.objectClass) && objectName.equals(that.objectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(objectClass, objectName);
    }

    @Override
    public String toString() {
        return asBslString();
    }
}

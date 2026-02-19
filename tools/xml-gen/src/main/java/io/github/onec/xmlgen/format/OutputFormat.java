package io.github.onec.xmlgen.format;

/**
 * Формат вывода XML метаданных.
 */
public enum OutputFormat {
    /**
     * Формат Конфигуратора (Designer).
     * Один XML-файл на объект + подкаталоги.
     * UUID-пары в InternalInfo.
     */
    DESIGNER,
    
    /**
     * Формат EDT (1C:Enterprise Development Tools).
     * .mdo файлы, упрощённая структура.
     */
    EDT;
    
    public static OutputFormat fromString(String format) {
        if (format == null) {
            return DESIGNER; // default
        }
        
        switch (format.toLowerCase()) {
            case "designer":
                return DESIGNER;
            case "edt":
                return EDT;
            default:
                throw new IllegalArgumentException("Unknown format: " + format + ". Use 'designer' or 'edt'");
        }
    }
}

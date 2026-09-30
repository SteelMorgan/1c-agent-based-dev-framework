package io.github.onec.xmlgen.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Резолвер {@code @file:}-include для строковых полей DSL.
 *
 * <p>Если строка начинается с {@code "@"} и указывает на существующий файл —
 * содержимое подменяется. Поиск:
 * <ol>
 *   <li>относительно {@code baseDir} (директория JSON-файла);</li>
 *   <li>относительно CWD;</li>
 *   <li>как абсолютный путь.</li>
 * </ol>
 *
 * <p>Поддерживаемые форматы:
 * <pre>
 *   "@queries/sales.sql"         — относительный путь после @
 *   "@/abs/path/file.txt"        — абсолютный путь
 * </pre>
 *
 * <p>Если файл не найден — возвращается исходная строка (без изменения),
 * чтобы не ломать поведение для строк, начинающихся с {@code @} по другой причине
 * (например, ссылка-параметр запроса). Это решение по дизайну skill.
 */
public class SkdInclude {

    private final Path baseDir;

    /**
     * @param baseDir директория, относительно которой ищутся include-файлы.
     *                Обычно — директория JSON DSL.
     */
    public SkdInclude(Path baseDir) {
        this.baseDir = baseDir != null ? baseDir : Paths.get(".");
    }

    /**
     * Резолвить строковое значение. Если оно начинается с {@code @} и
     * указывает на существующий файл — вернуть содержимое; иначе вернуть как есть.
     */
    public String resolve(String value) {
        if (value == null || value.length() < 2 || value.charAt(0) != '@') {
            return value;
        }
        // Чтобы не интерпретировать "@autoDates"/"@hidden" и прочие шорткоды
        // как пути — проверяем наличие слэша или расширения.
        String rest = value.substring(1);
        if (!rest.contains("/") && !rest.contains("\\") && !rest.contains(".")) {
            return value;
        }
        Path candidate;
        if (rest.startsWith("/")) {
            candidate = Paths.get(rest);
        } else {
            candidate = baseDir.resolve(rest).normalize();
        }
        if (!Files.exists(candidate)) {
            candidate = Paths.get(".").resolve(rest).normalize();
        }
        if (!Files.exists(candidate)) {
            return value;
        }
        try {
            return Files.readString(candidate, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read include file: " + candidate + " — " + e.getMessage(), e);
        }
    }
}

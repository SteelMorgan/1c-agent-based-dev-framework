package io.github.onec.xmlgen.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Текстовый ридер ключевых полей из {@code Configuration.xml} (с учётом BOM).
 * Используется в CFE-операциях, где нужно знать только конкретное поле
 * (например, {@code NamePrefix}) — без полного XML-парсинга.
 */
public final class ConfigurationXmlReader {

    private ConfigurationXmlReader() {}

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Прочитать содержимое файла как UTF-8, отрезая BOM. */
    public static String readContent(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /**
     * Прочитать {@code <NamePrefix>...</NamePrefix>} из {@code Configuration.xml} расширения.
     * Возвращает {@code null}, если элемент отсутствует или пуст.
     */
    public static String readNamePrefix(Path configurationXml) throws IOException {
        String content = readContent(configurationXml);
        // Прямой матч: <NamePrefix>...</NamePrefix>
        Matcher m = Pattern.compile("<NamePrefix>([^<]*)</NamePrefix>").matcher(content);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isEmpty() ? null : v;
        }
        // self-closing → нет значения
        return null;
    }
}

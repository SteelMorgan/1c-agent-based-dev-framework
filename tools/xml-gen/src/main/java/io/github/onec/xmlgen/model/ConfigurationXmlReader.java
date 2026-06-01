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
     * Дефолтная версия формата сериализации метаданных. Используется, когда
     * {@code Configuration.xml} недоступен. Совпадает с тем, что платформа
     * 8.3.2x пишет по умолчанию.
     */
    public static final String DEFAULT_FORMAT_VERSION = "2.17";

    /**
     * Прочитать версию формата метаданных — атрибут {@code version} корневого
     * элемента {@code <MetaDataObject ... version="2.20">} в {@code Configuration.xml}.
     *
     * <p>Версия формата ДОЛЖНА совпадать у всех объектов конфигурации и у файлов
     * {@code Ext/Predefined.xml}: иначе платформа при full-load падает с
     * «Версия формата ... отличается» (TASK-171 D-6). Поэтому генератор берёт
     * версию из конфигурации, а не хардкодит.
     *
     * @return значение атрибута {@code version}, либо {@link #DEFAULT_FORMAT_VERSION},
     *         если файл недоступен/не распарсился.
     */
    public static String readFormatVersion(Path configurationXml) {
        try {
            String content = readContent(configurationXml);
            // Версия — атрибут именно открывающего тега <MetaDataObject ...>, а НЕ
            // декларации <?xml version="1.0"?>. Ограничиваем поиск открывающим тегом
            // MetaDataObject (его атрибуты могут идти на нескольких строках).
            int mdoStart = content.indexOf("<MetaDataObject");
            if (mdoStart >= 0) {
                int tagEnd = content.indexOf('>', mdoStart);
                String head = tagEnd > mdoStart
                        ? content.substring(mdoStart, tagEnd)
                        : content.substring(mdoStart);
                Matcher m = Pattern.compile("\\sversion=\"([0-9]+\\.[0-9]+)\"").matcher(head);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (IOException e) {
            // нет файла/не читается — отдаём дефолт
        }
        return DEFAULT_FORMAT_VERSION;
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

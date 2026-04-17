package io.github.onec.xmlgen.editor;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Побайтовая замена текста в XML файлах без нормализации line endings.
 * <p>
 * Гарантирует сохранение:
 * <ul>
 *   <li>UTF-8 BOM (EF BB BF) если он был в оригинале</li>
 *   <li>bare LF (0x0A), CRLF (0x0D 0x0A) и смешанных line endings</li>
 *   <li>encoding оригинала</li>
 * </ul>
 */
public class ReplaceTextEditor {

    public record Replacement(String oldText, String newText) {}

    public record Result(Path file, int replacements, int bytesBefore, int bytesAfter, boolean dryRun) {}

    /**
     * Выполнить замену текста в файле.
     *
     * @param file       путь к файлу
     * @param pairs      список пар (old, new) для замены
     * @param replaceAll true = заменить все вхождения, false = только первое
     * @param encoding   "utf-8-sig" (default) или "utf-8"
     * @param dryRun     true = не записывать файл
     * @param backup     true = создать .bak перед записью
     * @param validate   true = проверить XML well-formedness после замены
     * @return результат с количеством замен и размерами
     */
    public Result execute(Path file, List<Replacement> pairs,
                          boolean replaceAll, String encoding,
                          boolean dryRun, boolean backup, boolean validate) throws IOException {
        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file, encoding);
        String content = handler.getContent();
        int totalReplacements = 0;

        for (Replacement pair : pairs) {
            if (replaceAll) {
                int count = countOccurrences(content, pair.oldText());
                if (count > 0) {
                    content = content.replace(pair.oldText(), pair.newText());
                    totalReplacements += count;
                }
            } else {
                int idx = content.indexOf(pair.oldText());
                if (idx >= 0) {
                    content = content.substring(0, idx)
                            + pair.newText()
                            + content.substring(idx + pair.oldText().length());
                    totalReplacements++;
                }
            }
        }

        if (validate && totalReplacements > 0) {
            validateXml(content);
        }

        int bytesAfter = handler.computeSize(content);

        if (!dryRun && totalReplacements > 0) {
            if (backup) {
                handler.backup();
            }
            handler.writeBack(content);
        }

        return new Result(file, totalReplacements, handler.getOriginalSize(), bytesAfter, dryRun);
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) >= 0) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private static void validateXml(String content) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IOException("XML validation failed after replacement: " + e.getMessage(), e);
        }
    }
}

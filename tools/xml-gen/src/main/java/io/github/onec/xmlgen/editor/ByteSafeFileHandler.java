package io.github.onec.xmlgen.editor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Побайтовое чтение/запись файлов с сохранением BOM и line endings.
 * <p>
 * Читает файл как raw bytes, детектирует UTF-8 BOM (EF BB BF),
 * конвертирует в String без нормализации line endings.
 * При записи восстанавливает BOM если он был в оригинале.
 */
public class ByteSafeFileHandler {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Path filePath;
    private final byte[] originalBytes;
    private final boolean hasBom;
    private final String content;

    private ByteSafeFileHandler(Path filePath, byte[] originalBytes, boolean hasBom, String content) {
        this.filePath = filePath;
        this.originalBytes = originalBytes;
        this.hasBom = hasBom;
        this.content = content;
    }

    /**
     * Открыть файл с автоопределением BOM. Encoding: utf-8-sig (BOM-aware).
     */
    public static ByteSafeFileHandler open(Path path) throws IOException {
        return open(path, "utf-8-sig");
    }

    /**
     * Открыть файл с указанным encoding.
     *
     * @param path     путь к файлу
     * @param encoding "utf-8-sig" (default, BOM-aware) или "utf-8" (игнорировать BOM)
     */
    public static ByteSafeFileHandler open(Path path, String encoding) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        boolean hasBom = raw.length >= 3
                && raw[0] == BOM[0]
                && raw[1] == BOM[1]
                && raw[2] == BOM[2];

        String content;
        if ("utf-8-sig".equalsIgnoreCase(encoding) && hasBom) {
            content = new String(raw, 3, raw.length - 3, StandardCharsets.UTF_8);
        } else {
            content = new String(raw, StandardCharsets.UTF_8);
            if ("utf-8".equalsIgnoreCase(encoding)) {
                hasBom = false; // utf-8 mode: не восстанавливать BOM при записи
            }
        }

        return new ByteSafeFileHandler(path, raw, hasBom, content);
    }

    public String getContent() {
        return content;
    }

    public boolean hasBom() {
        return hasBom;
    }

    public int getOriginalSize() {
        return originalBytes.length;
    }

    public Path getFilePath() {
        return filePath;
    }

    /**
     * Записать новое содержимое в файл, сохраняя BOM если он был в оригинале.
     */
    public void writeBack(String newContent) throws IOException {
        byte[] contentBytes = newContent.getBytes(StandardCharsets.UTF_8);
        if (hasBom) {
            byte[] result = new byte[BOM.length + contentBytes.length];
            System.arraycopy(BOM, 0, result, 0, BOM.length);
            System.arraycopy(contentBytes, 0, result, BOM.length, contentBytes.length);
            Files.write(filePath, result);
        } else {
            Files.write(filePath, contentBytes);
        }
    }

    /**
     * Вычислить размер результата в байтах (с BOM если применимо).
     */
    public int computeSize(String newContent) {
        int size = newContent.getBytes(StandardCharsets.UTF_8).length;
        return hasBom ? size + BOM.length : size;
    }

    /**
     * Создать резервную копию файла (.bak).
     */
    public void backup() throws IOException {
        Path backupPath = filePath.resolveSibling(filePath.getFileName() + ".bak");
        Files.copy(filePath, backupPath, StandardCopyOption.REPLACE_EXISTING);
    }
}

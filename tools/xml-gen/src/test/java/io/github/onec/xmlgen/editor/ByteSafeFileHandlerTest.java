package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ByteSafeFileHandlerTest {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void readFileWithBom() throws Exception {
        Path file = tempDir.resolve("with-bom.xml");
        byte[] content = "<root/>".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        Files.write(file, withBom);

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);

        assertTrue(handler.hasBom());
        assertEquals("<root/>", handler.getContent());
        assertEquals(withBom.length, handler.getOriginalSize());
    }

    @Test
    void readFileWithoutBom() throws Exception {
        Path file = tempDir.resolve("no-bom.xml");
        Files.writeString(file, "<root/>");

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);

        assertFalse(handler.hasBom());
        assertEquals("<root/>", handler.getContent());
    }

    @Test
    void writeBackPreservesBom() throws Exception {
        Path file = tempDir.resolve("bom-preserve.xml");
        byte[] content = "<root>old</root>".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        Files.write(file, withBom);

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);
        handler.writeBack("<root>new</root>");

        byte[] result = Files.readAllBytes(file);
        // BOM preserved
        assertEquals((byte) 0xEF, result[0]);
        assertEquals((byte) 0xBB, result[1]);
        assertEquals((byte) 0xBF, result[2]);
        // Content correct
        String written = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);
        assertEquals("<root>new</root>", written);
    }

    @Test
    void writeBackNoBomWhenOriginalHadNone() throws Exception {
        Path file = tempDir.resolve("no-bom-write.xml");
        Files.writeString(file, "<root>old</root>");

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);
        handler.writeBack("<root>new</root>");

        byte[] result = Files.readAllBytes(file);
        // No BOM
        assertNotEquals((byte) 0xEF, result[0]);
        String written = new String(result, StandardCharsets.UTF_8);
        assertEquals("<root>new</root>", written);
    }

    @Test
    void utf8EncodingIgnoresBom() throws Exception {
        Path file = tempDir.resolve("utf8-mode.xml");
        byte[] content = "<root/>".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        Files.write(file, withBom);

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file, "utf-8");

        assertFalse(handler.hasBom()); // utf-8 mode disables BOM handling
    }

    @Test
    void unsupportedEncodingIsRejected() throws Exception {
        Path file = tempDir.resolve("bad-encoding.xml");
        Files.writeString(file, "<root/>");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ByteSafeFileHandler.open(file, "utf8"));

        assertTrue(ex.getMessage().contains("Expected utf-8-sig or utf-8"));
    }

    @Test
    void backupCreatesFile() throws Exception {
        Path file = tempDir.resolve("original.xml");
        Files.writeString(file, "<root/>");

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);
        handler.backup();

        Path backupFile = tempDir.resolve("original.xml.bak");
        assertTrue(Files.exists(backupFile));
        assertEquals("<root/>", Files.readString(backupFile));
    }

    @Test
    void computeSizeWithBom() throws Exception {
        Path file = tempDir.resolve("size-test.xml");
        byte[] content = "<r/>".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + content.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(content, 0, withBom, BOM.length, content.length);
        Files.write(file, withBom);

        ByteSafeFileHandler handler = ByteSafeFileHandler.open(file);
        int size = handler.computeSize("<root/>");

        assertEquals("<root/>".getBytes(StandardCharsets.UTF_8).length + BOM.length, size);
    }
}

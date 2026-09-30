package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplaceTextEditorTest {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Path createFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private Path createFileWithBom(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(contentBytes, 0, withBom, BOM.length, contentBytes.length);
        Files.write(file, withBom);
        return file;
    }

    private Path createFileWithBytes(String name, byte[] bytes) throws Exception {
        Path file = tempDir.resolve(name);
        Files.write(file, bytes);
        return file;
    }

    // ── Test 1: BOM preservation ──

    @Test
    void bomPreservation() throws Exception {
        Path file = createFileWithBom("bom.xml", "<root>old</root>");

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, false, false);

        assertEquals(1, result.replacements());

        byte[] written = Files.readAllBytes(file);
        // BOM present
        assertEquals((byte) 0xEF, written[0]);
        assertEquals((byte) 0xBB, written[1]);
        assertEquals((byte) 0xBF, written[2]);
        // Content correct
        String content = new String(written, 3, written.length - 3, StandardCharsets.UTF_8);
        assertEquals("<root>new</root>", content);
    }

    // ── Test 2: bare LF preservation ──

    @Test
    void bareLfPreservation() throws Exception {
        // <a>text1\ntext2</a>\r\n<b>old</b>\r\n
        // bare LF inside <a>, CRLF between tags
        byte[] bytes = concat(
                "<a>text1".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0A}, // bare LF
                "text2</a>".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A}, // CRLF
                "<b>old</b>".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A}  // CRLF
        );
        Path file = createFileWithBytes("mixed-le.xml", bytes);

        ReplaceTextEditor editor = new ReplaceTextEditor();
        editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, false, false);

        byte[] result = Files.readAllBytes(file);
        String content = new String(result, StandardCharsets.UTF_8);

        // bare LF preserved
        assertTrue(content.contains("text1\ntext2"));
        // CRLF preserved between tags
        assertTrue(content.contains("</a>\r\n<b>"));
        // Replacement done
        assertTrue(content.contains("<b>new</b>"));
        assertFalse(content.contains("<b>old</b>"));
    }

    // ── Test 3: mixed line endings with BOM (integration) ──

    @Test
    void mixedLineEndingsWithBom() throws Exception {
        // BOM + mixed line endings
        byte[] contentBytes = concat(
                "<root>".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A}, // CRLF
                "<v8:content>line1".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0A}, // bare LF
                "line2".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0A}, // bare LF
                "line3</v8:content>".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A}, // CRLF
                "<type>old</type>".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A}, // CRLF
                "</root>".getBytes(StandardCharsets.UTF_8)
        );
        byte[] withBom = new byte[BOM.length + contentBytes.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(contentBytes, 0, withBom, BOM.length, contentBytes.length);

        Path file = createFileWithBytes("critical.xml", withBom);

        // Count bare LF before
        int bareLfBefore = countBareLf(withBom);

        ReplaceTextEditor editor = new ReplaceTextEditor();
        editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, false, false);

        byte[] result = Files.readAllBytes(file);

        // BOM preserved
        assertEquals((byte) 0xEF, result[0]);
        assertEquals((byte) 0xBB, result[1]);
        assertEquals((byte) 0xBF, result[2]);

        // Count bare LF after — must be identical
        int bareLfAfter = countBareLf(result);
        assertEquals(bareLfBefore, bareLfAfter, "Bare LF count must not change");

        // Replacement done
        String content = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);
        assertTrue(content.contains("<type>new</type>"));
    }

    // ── Test 4: not found → replacements=0 ──

    @Test
    void notFoundReturnsZeroReplacements() throws Exception {
        Path file = createFile("notfound.xml", "<root>content</root>");

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("nonexistent", "x")),
                false, "utf-8-sig", false, false, false);

        assertEquals(0, result.replacements());
        // File not modified
        assertEquals("<root>content</root>", Files.readString(file));
    }

    // ── Test 5: --all flag ──

    @Test
    void replaceAllFlag() throws Exception {
        Path file = createFile("all.xml", "<a>old</a><b>old</b><c>old</c>");

        ReplaceTextEditor editor = new ReplaceTextEditor();

        // Without --all: replace first only
        ReplaceTextEditor.Result first = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, false, false);
        assertEquals(1, first.replacements());
        assertEquals("<a>new</a><b>old</b><c>old</c>", Files.readString(file));

        // Restore and test with --all
        Files.writeString(file, "<a>old</a><b>old</b><c>old</c>");
        ReplaceTextEditor.Result all = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                true, "utf-8-sig", false, false, false);
        assertEquals(3, all.replacements());
        assertEquals("<a>new</a><b>new</b><c>new</c>", Files.readString(file));
    }

    // ── Test 6: --dry-run ──

    @Test
    void dryRunDoesNotModifyFile() throws Exception {
        Path file = createFile("dryrun.xml", "<root>old</root>");
        byte[] before = Files.readAllBytes(file);

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", true, false, false);

        assertEquals(1, result.replacements());
        assertTrue(result.dryRun());
        // File unchanged
        assertArrayEquals(before, Files.readAllBytes(file));
    }

    // ── Test 7: multiple replacement pairs ──

    @Test
    void multipleReplacementPairs() throws Exception {
        Path file = createFile("multi.xml",
                "<type>TypeA</type><ref>RefB</ref>");

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(
                        new ReplaceTextEditor.Replacement("TypeA", "TypeX"),
                        new ReplaceTextEditor.Replacement("RefB", "RefY")
                ),
                false, "utf-8-sig", false, false, false);

        assertEquals(2, result.replacements());
        assertEquals("<type>TypeX</type><ref>RefY</ref>", Files.readString(file));
    }

    // ── Test 8: --backup ──

    @Test
    void backupCreatesFile() throws Exception {
        Path file = createFile("backup.xml", "<root>old</root>");

        ReplaceTextEditor editor = new ReplaceTextEditor();
        editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, true, false);

        Path backupFile = tempDir.resolve("backup.xml.bak");
        assertTrue(Files.exists(backupFile));
        assertEquals("<root>old</root>", Files.readString(backupFile));
        assertEquals("<root>new</root>", Files.readString(file));
    }

    // ── Test 9: bytes_before / bytes_after in result ──

    @Test
    void resultContainsByteSizes() throws Exception {
        Path file = createFile("sizes.xml", "<root>old</root>");

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "longer-text")),
                false, "utf-8-sig", false, false, false);

        assertEquals("<root>old</root>".getBytes(StandardCharsets.UTF_8).length, result.bytesBefore());
        assertEquals("<root>longer-text</root>".getBytes(StandardCharsets.UTF_8).length, result.bytesAfter());
    }

    // ── Test 10: byte-exact comparison ──

    @Test
    void onlyReplacedBytesChange() throws Exception {
        // "old" is 3 bytes, "new" is 3 bytes — same length, easy to verify
        byte[] original = concat(
                "prefix".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A},
                "old".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A},
                "suffix".getBytes(StandardCharsets.UTF_8)
        );
        Path file = createFileWithBytes("exact.xml", original);

        ReplaceTextEditor editor = new ReplaceTextEditor();
        editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("old", "new")),
                false, "utf-8-sig", false, false, false);

        byte[] result = Files.readAllBytes(file);

        // Same length
        assertEquals(original.length, result.length);

        // Only bytes at position of "old" changed
        byte[] expected = concat(
                "prefix".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A},
                "new".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x0D, 0x0A},
                "suffix".getBytes(StandardCharsets.UTF_8)
        );
        assertArrayEquals(expected, result);
    }

    // ── Helpers ──

    private static int countBareLf(byte[] data) {
        int count = 0;
        for (int i = 0; i < data.length; i++) {
            if (data[i] == 0x0A && (i == 0 || data[i - 1] != 0x0D)) {
                count++;
            }
        }
        return count;
    }

    private static byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] a : arrays) totalLen += a.length;
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, offset, a.length);
            offset += a.length;
        }
        return result;
    }

    // ── Test N: multiline pattern with --all ──
    @Test
    void multilinePatternWithReplaceAll_replacesAllOccurrences() throws Exception {
        String content =
                "<a>\n\t<b>old</b>\n</a>\n" +
                "<c>\n\t<b>old</b>\n</c>\n" +
                "<d>\n\t<b>old</b>\n</d>\n";
        Path file = createFile("multiline.xml", content);

        ReplaceTextEditor editor = new ReplaceTextEditor();
        ReplaceTextEditor.Result result = editor.execute(
                file,
                List.of(new ReplaceTextEditor.Replacement("\n\t<b>old</b>\n", "\n\t<b>new</b>\n")),
                true, "utf-8-sig", false, false, false);

        assertEquals(3, result.replacements());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.contains("<b>old</b>"), "all old patterns should be replaced");
        assertEquals(3, written.split("<b>new</b>", -1).length - 1);
    }
}

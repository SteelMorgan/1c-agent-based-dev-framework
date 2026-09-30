package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Пишет XmlDocument обратно в файл с сохранением форматирования (BOM, indentation).
 */
public class XmlDocumentWriter {

    private static final String INDENT = "\t"; // 1C style usually uses tabs
    private String eol = "\r\n";
    private boolean formFlavor;

    public void write(XmlDocument document, Path path) throws IOException {
        // XG-107 [28.09.2026]: признак финального перевода строки берём у исходника ДО записи
        // (path может совпадать с document.getFile()).
        boolean keepTrailingNewline = sourceEndsWithNewline(document);
        // XG-107: перевод строки разметки = как в исходнике (канон Designer - CRLF); переводы
        // строк ВНУТРИ текста (многострочные v8:content, QueryText) пишутся как есть (bare LF),
        // поэтому прежний сквозной LF->CRLF фильтр больше не применяется.
        formFlavor = document.getRoot() != null && "Form".equals(document.getRoot().getName());
        int[] eolStats = sourceEolStats(document);
        eol = eolStats[0] == 0 && eolStats[1] > 0 ? "\n" : "\r\n";
        // Исходник чисто CRLF (или новый файл): переводы строк в тексте тоже CRLF (парсер
        // нормализует их в LF). Смешанный исходник (Designer: разметка CRLF, текст LF) - текст как есть.
        boolean textCrlf = eolStats[1] == 0;
        StringWriter buffer = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(buffer)) {
            // XML Declaration — use original if available
            String decl = document.getXmlDeclaration();
            bw.write(decl != null ? decl : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            bw.write(eol);
            if (document.getRoot() != null) {
                writeNode(document.getRoot(), bw, 0);
            }
        }
        String text = buffer.toString();
        if (textCrlf) {
            text = text.replace("\r\n", "\n").replace("\n", "\r\n");
        }
        if (!keepTrailingNewline) {
            while (text.endsWith("\n") || text.endsWith("\r")) {
                text = text.substring(0, text.length() - 1);
            }
        }

        try (OutputStream rawOs = Files.newOutputStream(path)) {
            // Write BOM if needed — ДО фильтра, BOM не нормализуется.
            if (document.isHasBom()) {
                rawOs.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            }
            try (Writer writer = new OutputStreamWriter(rawOs, StandardCharsets.UTF_8)) {
                writer.write(text);
            }
        }
    }

    /** [число CRLF, число bare LF] в исходнике; нет файла - {0,0}. */
    private static int[] sourceEolStats(XmlDocument document) {
        int[] stats = new int[2];
        Path source = document.getFile();
        if (source == null || !Files.isRegularFile(source)) {
            return stats;
        }
        try {
            byte[] b = Files.readAllBytes(source);
            for (int i = 0; i < b.length; i++) {
                if (b[i] == '\n') {
                    if (i > 0 && b[i - 1] == '\r') stats[0]++; else stats[1]++;
                }
            }
        } catch (IOException e) {
            // по умолчанию - канон CRLF
        }
        return stats;
    }

    /** Исходный файл документа заканчивается переводом строки (нет файла - по умолчанию да). */
    private static boolean sourceEndsWithNewline(XmlDocument document) {
        Path source = document.getFile();
        if (source == null || !Files.isRegularFile(source)) {
            return true;
        }
        try (RandomAccessFile raf = new RandomAccessFile(source.toFile(), "r")) {
            long len = raf.length();
            if (len == 0) return true;
            raf.seek(len - 1);
            int last = raf.read();
            return last == '\n' || last == '\r';
        } catch (IOException e) {
            return true;
        }
    }

    private void writeNode(XmlNode node, BufferedWriter writer, int level) throws IOException {
        // Indentation
        for (int i = 0; i < level; i++) {
            writer.write(INDENT);
        }

        writer.write("<");
        if (node.getPrefix() != null && !node.getPrefix().isEmpty()) {
            writer.write(node.getPrefix());
            writer.write(":");
        }
        writer.write(node.getName());

        // XG-107 [28.09.2026]: в Form.xml Designer пишет xmlns-объявления ПЕРЕД атрибутами
        // (<Form xmlns=... version="2.20">, <Settings xmlns:d4p1=... xsi:type=...>); в СКД и
        // прочих - наоборот (<template xsi:type=... xmlns:dcsat=...>), как читает XmlStructureReader.
        java.util.List<Map.Entry<String, String>> attrs = new java.util.ArrayList<>(node.getAttributes().entrySet());
        if (formFlavor) {
            attrs.sort(java.util.Comparator.comparingInt(e -> isNamespaceDecl(e.getKey()) ? 0 : 1));
        }
        for (Map.Entry<String, String> entry : attrs) {
            writer.write(" ");
            writer.write(entry.getKey());
            writer.write("=\"");
            writer.write(escapeAttr(entry.getValue()));
            writer.write("\"");
        }

        boolean hasChildren = !node.getChildren().isEmpty();
        boolean hasText = node.getText() != null && !node.getText().isEmpty();

        if (!hasChildren && !hasText) {
            writer.write("/>");
            writer.write(eol);
        } else {
            writer.write(">");

            if (hasChildren) {
                writer.write(eol);
                for (XmlNode child : node.getChildren()) {
                    writeNode(child, writer, level + 1);
                }
                // Closing tag indent
                for (int i = 0; i < level; i++) {
                    writer.write(INDENT);
                }
            } else {
                // Text content inline
                writer.write(escape(node.getText()));
            }

            writer.write("</");
            if (node.getPrefix() != null && !node.getPrefix().isEmpty()) {
                writer.write(node.getPrefix());
                writer.write(":");
            }
            writer.write(node.getName());
            writer.write(">");
            writer.write(eol);
        }
    }

    private static boolean isNamespaceDecl(String key) {
        return "xmlns".equals(key) || key.startsWith("xmlns:");
    }

    // XG-107: в Form.xml Designer экранирует в тексте только & < > (кавычки/апострофы - как есть);
    // прочие форматы (СКД и др.) - прежнее поведение с &quot;/&apos;.
    private String escape(String s) {
        if (s == null) return "";
        String r = s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return formFlavor ? r : r.replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String escapeAttr(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace("\"", "&quot;");
    }
}

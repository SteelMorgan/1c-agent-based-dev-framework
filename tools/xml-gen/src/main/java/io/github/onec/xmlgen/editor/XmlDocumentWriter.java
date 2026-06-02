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

    public void write(XmlDocument document, Path path) throws IOException {
        try (OutputStream rawOs = Files.newOutputStream(path)) {
            // Write BOM if needed — ДО фильтра, BOM не нормализуется.
            if (document.isHasBom()) {
                rawOs.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            }

            //++agent TASK-172 [02.06.2026 07:13:00]
            // Канон Designer (_Демо) — CRLF. bw.newLine() даёт платформенный разделитель
            // (LF на Linux), что ломало CRLF канон-файла при правке. Навешиваем CRLF единым
            // байтовым фильтром, как в StAX-пути.
            OutputStream os = io.github.onec.xmlgen.io.Crlf.wrapLfToCrlf(rawOs);
            //++agent TASK-172

            // Используем BufferedWriter для эффективности
            try (Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 BufferedWriter bw = new BufferedWriter(writer)) {
                
                // XML Declaration — use original if available
                String decl = document.getXmlDeclaration();
                bw.write(decl != null ? decl : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
                bw.newLine();

                if (document.getRoot() != null) {
                    writeNode(document.getRoot(), bw, 0);
                }
            }
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

        for (Map.Entry<String, String> entry : node.getAttributes().entrySet()) {
            writer.write(" ");
            writer.write(entry.getKey());
            writer.write("=\"");
            writer.write(escape(entry.getValue()));
            writer.write("\"");
        }

        boolean hasChildren = !node.getChildren().isEmpty();
        boolean hasText = node.getText() != null && !node.getText().isEmpty();

        if (!hasChildren && !hasText) {
            writer.write("/>");
            writer.newLine();
        } else {
            writer.write(">");

            if (hasChildren) {
                writer.newLine();
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
            writer.newLine();
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

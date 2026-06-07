package io.github.onec.xmlgen.form.edit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BslStubWriterTest {

    @Test
    void appendStubs_createsModuleWithBomAndClientProcedure(@TempDir Path tmp) throws IOException {
        Path formXml = tmp.resolve("Ext/Form/Form.xml");
        Files.createDirectories(formXml.getParent());
        BslStubWriter writer = new BslStubWriter(formXml);

        List<String> added = writer.appendStubs(List.of(
                new FormEventsWriter.HandlerRef(
                        "\u041f\u043e\u043b\u0435\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438",
                        false, "OnChange")
        ));

        Path modulePath = tmp.resolve("Ext/Form/Module.bsl");
        assertTrue(Files.exists(modulePath));
        assertEquals(1, added.size());

        byte[] bytes = Files.readAllBytes(modulePath);
        // BOM
        assertEquals((byte) 0xef, bytes[0]);
        assertEquals((byte) 0xbb, bytes[1]);
        assertEquals((byte) 0xbf, bytes[2]);

        String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(body.contains("&\u041d\u0430\u041a\u043b\u0438\u0435\u043d\u0442\u0435"), body);
        assertTrue(body.contains("\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u0430 \u041f\u043e\u043b\u0435\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438(\u042d\u043b\u0435\u043c\u0435\u043d\u0442)"), body);
        assertTrue(body.contains("\u041a\u043e\u043d\u0435\u0446\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u044b"), body);
    }

    @Test
    void resolveModulePath_canonicalExtFormXmlUsesExtFormModule(@TempDir Path tmp) throws IOException {
        Path formXml = tmp.resolve("Forms/Main/Ext/Form.xml");
        Files.createDirectories(formXml.getParent());
        BslStubWriter writer = new BslStubWriter(formXml);

        writer.appendStubs(List.of(
                new FormEventsWriter.HandlerRef("FieldOnChange", false, "OnChange")
        ));

        assertTrue(Files.exists(tmp.resolve("Forms/Main/Ext/Form/Module.bsl")));
        assertFalse(Files.exists(tmp.resolve("Forms/Main/Ext/Module.bsl")));
    }

    @Test
    void appendStubs_formEventUsesCorrectDirectiveAndSignature(@TempDir Path tmp) throws IOException {
        Path formXml = tmp.resolve("Ext/Form/Form.xml");
        Files.createDirectories(formXml.getParent());
        BslStubWriter writer = new BslStubWriter(formXml);

        writer.appendStubs(List.of(
                new FormEventsWriter.HandlerRef(
                        "\u041f\u0440\u0438\u0421\u043e\u0437\u0434\u0430\u043d\u0438\u0438\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435",
                        true, "OnCreateAtServer")
        ));

        Path modulePath = tmp.resolve("Ext/Form/Module.bsl");
        byte[] bytes = Files.readAllBytes(modulePath);
        String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertTrue(body.contains("&\u041d\u0430\u0421\u0435\u0440\u0432\u0435\u0440\u0435"), body);
        assertTrue(body.contains("\u041e\u0442\u043a\u0430\u0437, \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442\u043d\u0430\u044f\u041e\u0431\u0440\u0430\u0431\u043e\u0442\u043a\u0430"), body);
    }

    @Test
    void appendStubs_skipsExistingProcedures(@TempDir Path tmp) throws IOException {
        Path formXml = tmp.resolve("Ext/Form/Form.xml");
        Path modulePath = tmp.resolve("Ext/Form/Module.bsl");
        Files.createDirectories(formXml.getParent());
        String existing = "\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u0430 \u041f\u043e\u043b\u0435\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438(\u042d\u043b\u0435\u043c\u0435\u043d\u0442)\n\n// уже есть\n\u041a\u043e\u043d\u0435\u0446\u041f\u0440\u043e\u0446\u0435\u0434\u0443\u0440\u044b\n";
        Files.writeString(modulePath, existing);

        BslStubWriter writer = new BslStubWriter(formXml);
        List<String> added = writer.appendStubs(List.of(
                new FormEventsWriter.HandlerRef(
                        "\u041f\u043e\u043b\u0435\u041f\u0440\u0438\u0418\u0437\u043c\u0435\u043d\u0435\u043d\u0438\u0438",
                        false, "OnChange")
        ));

        assertTrue(added.isEmpty(), "existing procedure should be skipped");
        // Оригинальный файл не перезаписан
        String body = Files.readString(modulePath);
        assertEquals(existing, body);
    }

    @Test
    void appendStubs_deduplicatesWithinSameCall(@TempDir Path tmp) throws IOException {
        Path formXml = tmp.resolve("Ext/Form/Form.xml");
        Files.createDirectories(formXml.getParent());
        BslStubWriter writer = new BslStubWriter(formXml);

        List<String> added = writer.appendStubs(List.of(
                new FormEventsWriter.HandlerRef("HandlerA", false, "OnChange"),
                new FormEventsWriter.HandlerRef("HandlerA", false, "OnChange")
        ));

        assertEquals(1, added.size());
    }
}

package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigEditorTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void addChildObject_acceptsWebSocketClientInCanonicalOrder() throws Exception {
        Path configXml = tempDir.resolve("Configuration.xml");
        Files.writeString(configXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Test</Name></Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<HTTPService>Сервис</HTTPService>\n"
                        + "\t\t\t<WSReference>Ссылка</WSReference>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        Files.createDirectories(tempDir.resolve("WebSocketClients"));
        Files.writeString(tempDir.resolve("WebSocketClients/Клиент.xml"), "", StandardCharsets.UTF_8);

        ConfigEditor editor = new ConfigEditor(configXml);
        editor.addChildObject("WebSocketClient.Клиент");
        editor.save();

        String xml = Files.readString(configXml, StandardCharsets.UTF_8);
        assertThat(xml).contains("<WebSocketClient>Клиент</WebSocketClient>");
        assertThat(xml.indexOf("<HTTPService>Сервис</HTTPService>"))
                .isLessThan(xml.indexOf("<WebSocketClient>Клиент</WebSocketClient>"));
        assertThat(xml.indexOf("<WebSocketClient>Клиент</WebSocketClient>"))
                .isLessThan(xml.indexOf("<WSReference>Ссылка</WSReference>"));
    }
}

package io.github.onec.xmlgen.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MdoPathResolverTest {

    @Test
    void resolvesWebSocketClientDirectoryForObjectPaths() {
        assertThat(MdoPathResolver.dirForType("WebSocketClient")).isEqualTo("WebSocketClients");

        MdoPathResolver.ParsedModule module = new MdoPathResolver.ParsedModule(
                "WebSocketClient", "Клиент", "ObjectModule", null,
                List.of("WebSocketClient", "Клиент", "ObjectModule"));

        assertThat(MdoPathResolver.objectXmlPath(Path.of("src"), module))
                .isEqualTo(Path.of("src", "WebSocketClients", "Клиент.xml"));
    }
}

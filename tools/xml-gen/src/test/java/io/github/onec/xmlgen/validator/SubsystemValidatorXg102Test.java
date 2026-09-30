package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия XG-102: WebSocketClient — допустимый тип Content подсистемы.
 */
class SubsystemValidatorXg102Test {

    @TempDir
    Path tempDir;

    @Test
    void nestedSubsystemAcceptsExistingWebSocketClientContent() throws Exception {
        Path configRoot = tempDir.resolve("src/xml");
        Path objectFile = configRoot.resolve("WebSocketClients/биг_ВебСокет_ОКХ.xml");
        Path subsystemFile = configRoot.resolve("Subsystems/GBIG/Subsystems/big_OKX.xml");
        Files.createDirectories(objectFile.getParent());
        Files.createDirectories(subsystemFile.getParent());
        Files.writeString(objectFile, "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(subsystemFile, subsystemWithWebSocketClient(), StandardCharsets.UTF_8);

        List<SubsystemValidator.ValidationMessage> messages = new SubsystemValidator().validate(
                new XmlStructureReader().parse(subsystemFile), configRoot, subsystemFile);

        assertThat(messages)
                .as("допустимый существующий WebSocketClient не даёт диагностик")
                .noneMatch(message -> message.message.contains("WebSocketClient"));
    }

    private static String subsystemWithWebSocketClient() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                        xmlns:v8="http://v8.1c.ru/8.1/data/core"
                        xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" version="2.17">
                    <Subsystem uuid="00000000-0000-0000-0000-000000000102">
                        <Properties>
                            <Name>big_OKX</Name>
                            <Synonym><v8:item><v8:lang>ru</v8:lang><v8:content>OKX</v8:content></v8:item></Synonym>
                            <Content>
                                <xr:Item xsi:type="xr:MDObjectRef">WebSocketClient.биг_ВебСокет_ОКХ</xr:Item>
                            </Content>
                        </Properties>
                        <ChildObjects/>
                    </Subsystem>
                </MetaDataObject>
                """;
    }
}

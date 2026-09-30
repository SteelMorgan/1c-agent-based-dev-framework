package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubsystemValidatorTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void validatesSubsystemMetadataNamespace() throws Exception {
        Path file = tempDir.resolve("Subsystem.xml");
        Files.writeString(file, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://wrong.namespace" version="2.17">
                    <Subsystem uuid="00000000-0000-0000-0000-000000000001">
                        <Properties>
                            <Name>Тест</Name>
                            <Synonym/>
                            <IncludeInCommandInterface>true</IncludeInCommandInterface>
                            <UseOneCommand>false</UseOneCommand>
                            <IncludeHelpInContents>true</IncludeHelpInContents>
                            <Content/>
                        </Properties>
                        <ChildObjects/>
                    </Subsystem>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);

        XmlDocument document = new XmlStructureReader().parse(file);
        List<SubsystemValidator.ValidationMessage> messages =
                new SubsystemValidator().validate(document, tempDir);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("namespace"));
    }
}

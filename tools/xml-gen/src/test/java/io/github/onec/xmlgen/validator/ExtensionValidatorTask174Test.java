package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionValidatorTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void validatesExtensionMetadataNamespace() throws Exception {
        Path file = tempDir.resolve("Configuration.xml");
        Files.writeString(file, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://wrong.namespace" version="2.17">
                    <Configuration uuid="00000000-0000-0000-0000-000000000001">
                        <InternalInfo/>
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>Расширение</Name>
                            <ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose>
                            <NamePrefix>Расш_</NamePrefix>
                        </Properties>
                        <ChildObjects/>
                    </Configuration>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);

        XmlDocument document = new XmlStructureReader().parse(file);
        List<ExtensionValidator.ValidationMessage> messages =
                new ExtensionValidator().validate(document, tempDir);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("namespace"));
    }
}

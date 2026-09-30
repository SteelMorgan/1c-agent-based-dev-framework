package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigValidatorCommonTemplateXg105Test {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unit-XG105 validator accepts canonical CommonTemplate child")
    void validatorAcceptsCommonTemplateChild() throws Exception {
        List<ConfigValidator.ValidationMessage> messages = validate(
                "\t\t\t<CommonTemplate>BinaryFixture</CommonTemplate>\n");

        assertThat(messages).noneMatch(message ->
                message.message.contains("unknown type") || message.message.contains("canonical order"));
    }

    @Test
    @DisplayName("unit-XG105 validator rejects legacy Template child at configuration root")
    void validatorRejectsLegacyTemplateChild() throws Exception {
        List<ConfigValidator.ValidationMessage> messages = validate(
                "\t\t\t<Template>BinaryFixture</Template>\n");

        assertThat(messages).anyMatch(message ->
                "ERROR".equals(message.level)
                        && message.message.contains("Template")
                        && message.message.contains("CommonTemplate"));
    }

    private List<ConfigValidator.ValidationMessage> validate(String childObject) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000105\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>FixtureConfig</Name>\n"
                + "\t\t\t<DefaultLanguage>Language.English</DefaultLanguage>\n"
                + "\t\t\t<DefaultRunMode>ManagedApplication</DefaultRunMode>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t\t<Language>English</Language>\n"
                + childObject
                + "\t\t</ChildObjects>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
        Path configuration = tempDir.resolve("Configuration-" + Math.abs(childObject.hashCode()) + ".xml");
        Files.writeString(configuration, xml, StandardCharsets.UTF_8);
        XmlDocument document = new XmlStructureReader().parse(configuration);
        return new ConfigValidator().validate(document, null);
    }
}

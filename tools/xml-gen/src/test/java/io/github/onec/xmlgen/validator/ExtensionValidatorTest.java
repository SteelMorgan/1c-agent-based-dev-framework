package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionValidatorTest {

    @TempDir
    Path tempDir;

    private final XmlStructureReader reader = new XmlStructureReader();

    @Test
    void detectsChildObjectVersionMismatch() throws Exception {
        Path ext = makeExtension();
        writeXml(ext.resolve("Languages/Русский.xml"), languageXml("2.20"));
        writeXml(ext.resolve("CommonModules/X.xml"), commonModuleXml("2.17"));

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level)
                        && m.message.contains("Version mismatch in CommonModules/X.xml")
                        && m.message.contains("2.17")
                        && m.message.contains("2.20"));
    }

    @Test
    void detectsMissingChildObjectFile() throws Exception {
        Path ext = makeExtension();
        writeXml(ext.resolve("Languages/Русский.xml"), languageXml("2.20"));
        Files.createDirectories(ext.resolve("CommonModules"));

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level)
                        && m.message.contains("Missing object file: CommonModules/X.xml"));
    }

    @Test
    void detectsRoleWithoutRightsXml() throws Exception {
        Path ext = tempDir.resolve("roleExt");
        writeXml(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                        + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000099\">\n"
                        + "\t\t<InternalInfo/>\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>Ext1</Name>\n"
                        + "\t\t\t<ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose>\n"
                        + "\t\t\t<KeepMappingToExtendedConfigurationObjectsByIDs>true</KeepMappingToExtendedConfigurationObjectsByIDs>\n"
                        + "\t\t\t<NamePrefix>Ext1_</NamePrefix>\n"
                        + "\t\t\t<ConfigurationExtensionCompatibilityMode>Version8_3_24</ConfigurationExtensionCompatibilityMode>\n"
                        + "\t\t\t<ScriptVariant>Russian</ScriptVariant>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Role>Ext1_ОсновнаяРоль</Role>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        writeXml(ext.resolve("Roles/Ext1_ОсновнаяРоль.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Role uuid=\"00000000-0000-0000-0000-000000000003\">\n"
                        + "\t\t<Properties><Name>Ext1_ОсновнаяРоль</Name></Properties>\n"
                        + "\t</Role>\n"
                        + "</MetaDataObject>\n");

        List<ExtensionValidator.ValidationMessage> messages = validate(ext);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level)
                        && m.message.contains("Missing role rights file")
                        && m.message.contains("Ext1_ОсновнаяРоль"));
    }

    private List<ExtensionValidator.ValidationMessage> validate(Path ext) throws Exception {
        XmlDocument doc = reader.parse(ext.resolve("Configuration.xml"));
        return new ExtensionValidator().validate(doc, ext);
    }

    private Path makeExtension() throws Exception {
        Path ext = tempDir.resolve("ext");
        writeXml(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" "
                        + "xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000099\">\n"
                        + "\t\t<InternalInfo/>\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>Ext1</Name>\n"
                        + "\t\t\t<ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose>\n"
                        + "\t\t\t<KeepMappingToExtendedConfigurationObjectsByIDs>true</KeepMappingToExtendedConfigurationObjectsByIDs>\n"
                        + "\t\t\t<NamePrefix>Ext1_</NamePrefix>\n"
                        + "\t\t\t<ConfigurationExtensionCompatibilityMode>Version8_3_24</ConfigurationExtensionCompatibilityMode>\n"
                        + "\t\t\t<ScriptVariant>Russian</ScriptVariant>\n"
                        + "\t\t\t<DefaultLanguage>Language.Русский</DefaultLanguage>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects>\n"
                        + "\t\t\t<Language>Русский</Language>\n"
                        + "\t\t\t<CommonModule>X</CommonModule>\n"
                        + "\t\t</ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        return ext;
    }

    private String languageXml(String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"" + version + "\">\n"
                + "\t<Language uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                + "\t\t\t<Name>Русский</Name>\n"
                + "\t\t\t<ExtendedConfigurationObject>00000000-0000-0000-0000-000000000002</ExtendedConfigurationObject>\n"
                + "\t\t</Properties>\n"
                + "\t</Language>\n"
                + "</MetaDataObject>\n";
    }

    private String commonModuleXml(String version) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"" + version + "\">\n"
                + "\t<CommonModule uuid=\"00000000-0000-0000-0000-000000000003\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                + "\t\t\t<Name>X</Name>\n"
                + "\t\t\t<ExtendedConfigurationObject>00000000-0000-0000-0000-000000000004</ExtendedConfigurationObject>\n"
                + "\t\t</Properties>\n"
                + "\t</CommonModule>\n"
                + "</MetaDataObject>\n";
    }

    private void writeXml(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}

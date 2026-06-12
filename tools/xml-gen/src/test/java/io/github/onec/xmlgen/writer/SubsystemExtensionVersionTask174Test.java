package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-174 (аудит порта): версия формата сериализации в SubsystemWriter и
 * ExtensionWriter. Раньше — хардкод 2.17 (RoleWriter/FormWriter переведены на
 * резолв из Configuration.xml ещё в TASK-171, эти два писателя были пропущены).
 * На конфигурации формата 2.20 (платформа 8.3.27) рассинхрон версии — отказ
 * Конфигуратора при full-load («Версия формата ... отличается», TASK-171 D-6).
 */
class SubsystemExtensionVersionTask174Test {

    @TempDir
    Path tempDir;

    private static final String CFG_220 =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                    + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                    + "\t\t<Properties>\n"
                    + "\t\t\t<Name>Тест</Name>\n"
                    + "\t\t\t<CompatibilityMode>Version8_3_24</CompatibilityMode>\n"
                    + "\t\t</Properties>\n"
                    + "\t\t<ChildObjects/>\n"
                    + "\t</Configuration>\n"
                    + "</MetaDataObject>\n";

    @Test
    void subsystemWriter_resolvesVersionFromConfigurationXml() throws Exception {
        Path configRoot = tempDir.resolve("cfg");
        Path subsystemsDir = configRoot.resolve("Subsystems");
        Files.createDirectories(subsystemsDir);
        Files.writeString(configRoot.resolve("Configuration.xml"), CFG_220, StandardCharsets.UTF_8);

        Path json = tempDir.resolve("sub.json");
        Files.writeString(json, "{\"name\": \"МояПодсистема\"}", StandardCharsets.UTF_8);

        new SubsystemWriter().compile(json, subsystemsDir);

        String xml = Files.readString(subsystemsDir.resolve("МояПодсистема.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("version=\"2.20\"");
        assertThat(xml).doesNotContain("version=\"2.17\"");

        String ci = Files.readString(
                subsystemsDir.resolve("МояПодсистема/Ext/CommandInterface.xml"), StandardCharsets.UTF_8);
        assertThat(ci).contains("version=\"2.20\"");
    }

    @Test
    void subsystemWriter_withoutConfigurationXml_fallsBackTo217() throws Exception {
        Path subsystemsDir = tempDir.resolve("isolated/Subsystems");
        Files.createDirectories(subsystemsDir);

        Path json = tempDir.resolve("sub2.json");
        Files.writeString(json, "{\"name\": \"Одинокая\"}", StandardCharsets.UTF_8);

        new SubsystemWriter().compile(json, subsystemsDir);

        String xml = Files.readString(subsystemsDir.resolve("Одинокая.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("version=\"2.17\"");
    }

    @Test
    void extensionWriter_resolvesVersionFromBaseConfig() throws Exception {
        Path baseDir = tempDir.resolve("base");
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("Configuration.xml"), CFG_220, StandardCharsets.UTF_8);

        Path extDir = tempDir.resolve("ext");
        new ExtensionWriter().create(extDir, "МоёРасширение", null, null,
                null, null, null, null, baseDir, true);

        String cfg = Files.readString(extDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(cfg).contains("version=\"2.20\"");
        String lang = Files.readString(extDir.resolve("Languages/Русский.xml"), StandardCharsets.UTF_8);
        assertThat(lang).contains("version=\"2.20\"");
    }

    @Test
    void extensionWriter_withoutConfigPath_fallsBackTo217() throws Exception {
        Path extDir = tempDir.resolve("ext2");
        new ExtensionWriter().create(extDir, "Расш2", null, null,
                null, null, null, null, null, true);

        String cfg = Files.readString(extDir.resolve("Configuration.xml"), StandardCharsets.UTF_8);
        assertThat(cfg).contains("version=\"2.17\"");
    }

    @Test
    void extensionWriter_createsRightsXmlForDefaultRole() throws Exception {
        Path baseDir = tempDir.resolve("baseWithRole");
        Files.createDirectories(baseDir);
        Files.writeString(baseDir.resolve("Configuration.xml"), CFG_220, StandardCharsets.UTF_8);

        Path extDir = tempDir.resolve("extWithRole");
        new ExtensionWriter().create(extDir, "РасшРоль", null, null,
                null, null, null, null, baseDir, false);

        Path rights = extDir.resolve("Roles/РасшРоль_ОсновнаяРоль/Ext/Rights.xml");
        assertThat(rights).exists();
        String xml = Files.readString(rights, StandardCharsets.UTF_8);
        assertThat(xml).contains("<Rights");
        assertThat(xml).contains("http://v8.1c.ru/8.2/roles");
        assertThat(xml).contains("version=\"2.20\"");
        assertThat(xml).contains("<setForNewObjects>false</setForNewObjects>");
    }
}

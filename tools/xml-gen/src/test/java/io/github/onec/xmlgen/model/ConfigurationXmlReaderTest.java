package io.github.onec.xmlgen.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Тесты ConfigurationXmlReader.readFormatVersion (TASK-171 D-6). */
class ConfigurationXmlReaderTest {

    @TempDir
    Path tempDir;

    private Path write(String content) throws IOException {
        Path p = tempDir.resolve("Configuration.xml");
        Files.writeString(p, content, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void readsMetaDataObjectVersion_notXmlDeclaration() throws IOException {
        // version="1.0" в декларации НЕ должен перебить version="2.20" у MetaDataObject
        Path cfg = write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                + "</MetaDataObject>\n");
        assertThat(ConfigurationXmlReader.readFormatVersion(cfg)).isEqualTo("2.20");
    }

    @Test
    void readsVersion_whenAttributesSpanMultipleLines() throws IOException {
        Path cfg = write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"2.17\">\n"
                + "</MetaDataObject>\n");
        assertThat(ConfigurationXmlReader.readFormatVersion(cfg)).isEqualTo("2.17");
    }

    @Test
    void fallsBackToDefault_whenFileMissing() {
        Path missing = tempDir.resolve("nope/Configuration.xml");
        assertThat(ConfigurationXmlReader.readFormatVersion(missing))
                .isEqualTo(ConfigurationXmlReader.DEFAULT_FORMAT_VERSION);
    }

    @Test
    void fallsBackToDefault_whenNoVersionAttribute() throws IOException {
        Path cfg = write("<?xml version=\"1.0\"?>\n<MetaDataObject xmlns=\"x\">\n</MetaDataObject>\n");
        assertThat(ConfigurationXmlReader.readFormatVersion(cfg))
                .isEqualTo(ConfigurationXmlReader.DEFAULT_FORMAT_VERSION);
    }
}

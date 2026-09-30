package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.model.ConfigurationXmlReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** XG-42: версия формата нового объекта вне корня выгрузки не должна молча становиться 2.17. */
class MetaWriterFormatVersionXg42Test {

    @TempDir
    Path tempDir;

    private static void writeMdo(Path file, String version) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "﻿<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\""
                + version + "\">\r\n</MetaDataObject>", StandardCharsets.UTF_8);
    }

    @Test
    void outputDirConfiguration_wins() throws Exception {
        writeMdo(tempDir.resolve("Configuration.xml"), "2.20");
        assertThat(ConfigurationXmlReader.resolveFormatVersion(tempDir, tempDir.resolve("CommonModules"))).isEqualTo("2.20");
    }

    @Test
    void ancestorConfiguration_isFound() throws Exception {
        writeMdo(tempDir.resolve("Configuration.xml"), "2.20");
        Path nested = tempDir.resolve("sub/out");
        Files.createDirectories(nested);
        assertThat(ConfigurationXmlReader.resolveFormatVersion(nested, nested.resolve("CommonModules"))).isEqualTo("2.20");
    }

    @Test
    void siblingObjects_giveVersion_whenNoConfiguration() throws Exception {
        Path typeDir = tempDir.resolve("CommonModules");
        writeMdo(typeDir.resolve("Сосед.xml"), "2.20");
        assertThat(ConfigurationXmlReader.resolveFormatVersion(tempDir, typeDir)).isEqualTo("2.20");
    }

    @Test
    void metaCompile_withoutRootConfiguration_usesSiblingVersion() throws Exception {
        Path typeDir = tempDir.resolve("CommonModules");
        writeMdo(typeDir.resolve("Сосед.xml"), "2.20");
        Path json = tempDir.resolve("m.json");
        Files.writeString(json, "{\"type\":\"CommonModule\",\"name\":\"НовыйМодуль\",\"server\":true}", StandardCharsets.UTF_8);
        new MetaWriter().compile(json, tempDir);
        String xml = Files.readString(typeDir.resolve("НовыйМодуль.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("version=\"2.20\"");
        assertThat(xml).contains("<Server>true</Server>");
    }
}

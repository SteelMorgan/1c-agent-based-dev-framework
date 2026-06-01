package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * TASK-171: регрессия на root-resolution в {@link SubsystemWriter#compile}.
 *
 * <p>Корень дефекта: для config-layout (src/xml/Subsystems/X.xml) объекты Content лежат
 * соседями в корне конфигурации (src/xml/Catalogs/...), а не внутри Subsystems/.
 * Прежний резолв (configRoot = outputDir = .../Subsystems) + boundary-guard ложно
 * падали с «target object does not exist» / «escapes extension boundary» на существующих
 * объектах. Фикс — walk-up до Configuration.xml.
 */
class SubsystemWriterTask171Test {

    @TempDir
    Path tempDir;

    /** Сборка config-layout: tempDir/src/xml/{Configuration.xml, Subsystems/, Catalogs/}. */
    private Path buildConfigLayout() throws IOException {
        Path configRoot = tempDir.resolve("src").resolve("xml");
        Files.createDirectories(configRoot.resolve("Subsystems"));
        Files.createDirectories(configRoot.resolve("Catalogs"));
        Files.writeString(configRoot.resolve("Configuration.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        // Реальный объект-сосед в корне конфигурации.
        Files.writeString(configRoot.resolve("Catalogs").resolve("Товары.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        return configRoot;
    }

    private Path writeSubsystemJson(String content) throws IOException {
        Path json = tempDir.resolve("ss.json");
        Files.writeString(json,
                "{\"name\":\"ТестоваяПодсистема\",\"content\":[" + content + "]}",
                StandardCharsets.UTF_8);
        return json;
    }

    @Test
    void compile_configLayout_existingContentObject_doesNotThrow() throws Exception {
        Path configRoot = buildConfigLayout();
        Path outputDir = configRoot.resolve("Subsystems");
        Path json = writeSubsystemJson("\"Catalog.Товары\"");

        // Объект src/xml/Catalogs/Товары.xml существует → ensureContentStub НЕ должен падать,
        // хотя файл лежит вне outputDir (Subsystems/). Раньше падал boundary-guard / not-found.
        SubsystemWriter writer = new SubsystemWriter();
        assertThatCode(() -> writer.compile(json, outputDir))
                .doesNotThrowAnyException();

        assertThat(Files.exists(outputDir.resolve("ТестоваяПодсистема.xml"))).isTrue();
    }

    @Test
    void compile_configLayout_missingContentObject_stillThrows() throws Exception {
        Path configRoot = buildConfigLayout();
        Path outputDir = configRoot.resolve("Subsystems");
        // Ссылаемся на несуществующий объект — fail-fast должен сохраниться (existence-чек полезен).
        Path json = writeSubsystemJson("\"Catalog.НетТакого\"");

        SubsystemWriter writer = new SubsystemWriter();
        assertThatThrownBy(() -> writer.compile(json, outputDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void compile_extensionLayout_fallbackBehaviorPreserved() throws Exception {
        // Extension-layout: НЕТ Configuration.xml → используется прежний fallback (configRoot=outputDir).
        Path extRoot = tempDir.resolve("exts").resolve("XMLGEN_TEST");
        Path outputDir = extRoot.resolve("Subsystems");
        Files.createDirectories(outputDir.resolve("Catalogs"));
        // Объект внутри Subsystems/Catalogs/ (плоский extension-layout).
        Files.writeString(outputDir.resolve("Catalogs").resolve("ОбъектРасширения.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        Path json = tempDir.resolve("ext.json");
        Files.writeString(json,
                "{\"name\":\"РасширеннаяПодсистема\",\"content\":[\"Catalog.ОбъектРасширения\"]}",
                StandardCharsets.UTF_8);

        SubsystemWriter writer = new SubsystemWriter();
        assertThatCode(() -> writer.compile(json, outputDir))
                .doesNotThrowAnyException();
    }
}

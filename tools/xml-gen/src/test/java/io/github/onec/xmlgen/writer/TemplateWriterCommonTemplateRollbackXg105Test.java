package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.editor.ConfigEditor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class TemplateWriterCommonTemplateRollbackXg105Test {

    private static final byte[] PAYLOAD = new byte[] {
            0x00, 0x01, 0x02, 0x0A, 0x0D, 0x1A, 0x7F, (byte) 0x80, (byte) 0xFE, (byte) 0xFF
    };

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unit-XG105 add compensates every artifact when final and rollback config saves fail")
    void addCommonTemplateRestoresExactTreeWhenConfigSaveFailsAfterMoves() throws Exception {
        Path configuration = createConfigurationFixture("add");
        Path configRoot = configuration.getParent();
        Path payload = writePayload("add-payload.bin");
        Map<String, String> before = snapshotTree(configRoot);
        AtomicInteger saveAttempts = new AtomicInteger();

        TemplateWriter writer = failingWriter(saveAttempts, editor -> {
            Path wrapper = configRoot.resolve("CommonTemplates/RollbackAdd.xml");
            Path body = configRoot.resolve("CommonTemplates/RollbackAdd/Ext/Template.bin");
            assertThat(wrapper).isRegularFile();
            assertThat(body).isRegularFile();
            assertThat(Files.readAllBytes(body)).containsExactly(PAYLOAD);
            assertThat(editor.previewContent()).contains("<CommonTemplate>RollbackAdd</CommonTemplate>");
            assertThat(Files.readString(configuration, StandardCharsets.UTF_8))
                    .doesNotContain("<CommonTemplate>RollbackAdd</CommonTemplate>");
        });

        IOException failure = catchThrowableOfType(
                () -> writer.addCommonTemplate(configuration, "RollbackAdd", "binary", null, payload),
                IOException.class);

        assertPrimaryAndSuppressedSaveFailures(failure, saveAttempts);
        assertThat(snapshotTree(configRoot)).isEqualTo(before);
        assertThat(configRoot.resolve("CommonTemplates/RollbackAdd.xml")).doesNotExist();
        assertThat(configRoot.resolve("CommonTemplates/RollbackAdd")).doesNotExist();
    }

    @Test
    @DisplayName("unit-XG105 remove restores registration, files and payload before staging cleanup")
    void removeCommonTemplateRestoresExactTreeWhenConfigSaveFailsAfterMoves() throws Exception {
        Path configuration = createConfigurationFixture("remove");
        Path configRoot = configuration.getParent();
        Path payload = writePayload("remove-payload.bin");
        new TemplateWriter().addCommonTemplate(configuration, "RollbackRemove", "binary", null, payload);

        Path wrapper = configRoot.resolve("CommonTemplates/RollbackRemove.xml");
        Path body = configRoot.resolve("CommonTemplates/RollbackRemove/Ext/Template.bin");
        Map<String, String> before = snapshotTree(configRoot);
        AtomicInteger saveAttempts = new AtomicInteger();

        TemplateWriter writer = failingWriter(saveAttempts, editor -> {
            assertThat(wrapper).doesNotExist();
            assertThat(body).doesNotExist();
            assertThat(editor.previewContent()).doesNotContain("<CommonTemplate>RollbackRemove</CommonTemplate>");
            assertThat(Files.readString(configuration, StandardCharsets.UTF_8))
                    .contains("<CommonTemplate>RollbackRemove</CommonTemplate>");

            try (Stream<Path> paths = Files.walk(configRoot)) {
                assertThat(paths.filter(Files::isRegularFile)
                        .filter(path -> "Template.bin".equals(path.getFileName().toString()))
                        .filter(path -> !path.equals(body))
                        .map(path -> readBytesUnchecked(path))
                        .toList())
                        .as("payload must remain staged until restoration succeeds")
                        .anySatisfy(bytes -> assertThat(bytes).containsExactly(PAYLOAD));
            }
        });

        IOException failure = catchThrowableOfType(
                () -> writer.removeCommonTemplate(configuration, "RollbackRemove"),
                IOException.class);

        assertPrimaryAndSuppressedSaveFailures(failure, saveAttempts);
        assertThat(snapshotTree(configRoot)).isEqualTo(before);
        assertThat(wrapper).isRegularFile();
        assertThat(body).isRegularFile();
        assertThat(Files.readAllBytes(body)).containsExactly(PAYLOAD);
        assertThat(Files.readString(configuration, StandardCharsets.UTF_8))
                .containsOnlyOnce("<CommonTemplate>RollbackRemove</CommonTemplate>");
    }

    private TemplateWriter failingWriter(AtomicInteger saveAttempts, SaveProbe firstSaveProbe) {
        return new TemplateWriter(path -> new ConfigEditor(path) {
            @Override
            public void save() throws IOException {
                int attempt = saveAttempts.incrementAndGet();
                if (attempt == 1) {
                    firstSaveProbe.verify(this);
                }
                throw new IOException("XG105_INJECTED_CONFIG_SAVE_FAILURE_" + attempt);
            }
        });
    }

    private void assertPrimaryAndSuppressedSaveFailures(IOException failure, AtomicInteger saveAttempts) {
        assertThat(failure).isNotNull();
        assertThat(failure).hasMessageContaining("XG105_INJECTED_CONFIG_SAVE_FAILURE_1");
        assertThat(saveAttempts.get()).as("rollback configuration save must be attempted").isGreaterThanOrEqualTo(2);
        assertThat(failure.getSuppressed())
                .extracting(Throwable::getMessage)
                .anySatisfy(message -> assertThat(message).contains("XG105_INJECTED_CONFIG_SAVE_FAILURE_2"));
    }

    private Path createConfigurationFixture(String suffix) throws Exception {
        Path configRoot = tempDir.resolve("config-" + suffix);
        Files.createDirectories(configRoot);
        Path configuration = configRoot.resolve("Configuration.xml");
        try (InputStream input = getClass().getResourceAsStream("/xg105/configuration-root.xml")) {
            assertThat(input).isNotNull();
            String fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\n", "\r\n");
            Files.writeString(configuration, fixture, StandardCharsets.UTF_8);
        }
        return configuration;
    }

    private Path writePayload(String filename) throws IOException {
        Path payload = tempDir.resolve(filename);
        Files.write(payload, PAYLOAD);
        return payload;
    }

    private Map<String, String> snapshotTree(Path root) throws IOException {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted().toList()) {
                if (path.equals(root)) {
                    continue;
                }
                String relative = root.relativize(path).toString();
                if (Files.isDirectory(path)) {
                    snapshot.put(relative, "D");
                } else if (Files.isRegularFile(path)) {
                    snapshot.put(relative, "F:" + Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
                }
            }
        }
        return snapshot;
    }

    private byte[] readBytesUnchecked(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new AssertionError("Cannot read staged payload " + path, exception);
        }
    }

    @FunctionalInterface
    private interface SaveProbe {
        void verify(ConfigEditor editor) throws IOException;
    }
}

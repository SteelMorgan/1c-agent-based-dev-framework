package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CommonTemplateCliXg105Test {

    private static final byte[] PAYLOAD = new byte[] {
            0x00, 0x01, 0x02, 0x0A, 0x0D, 0x1A, 0x7F, (byte) 0x80, (byte) 0xFE, (byte) 0xFF
    };

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-XG105 configuration-root binary add creates canonical CommonTemplate")
    void addBinaryToConfigurationRootCreatesCanonicalCommonTemplate() throws Exception {
        Path configuration = createConfigurationFixture();
        Path payload = writePayload("payload.bin");

        ProcessResult result = runMain(
                "template", "add", configuration.toString(),
                "--name", "BinaryFixture", "--type", "binary", "--input", payload.toString());

        assertThat(result.exitCode()).as(result.stderr()).isZero();
        assertThat(result.stderr()).isBlank();

        Path configRoot = configuration.getParent();
        Path wrapper = configRoot.resolve("CommonTemplates/BinaryFixture.xml");
        Path body = configRoot.resolve("CommonTemplates/BinaryFixture/Ext/Template.bin");
        assertThat(wrapper).isRegularFile();
        assertThat(body).isRegularFile();
        assertThat(Files.readAllBytes(body)).containsExactly(PAYLOAD);
        assertThat(configRoot.resolve("FixtureConfig/Templates")).doesNotExist();
        assertThat(configRoot.resolve("Templates")).doesNotExist();

        assertCanonicalBinaryWrapper(wrapper, "BinaryFixture");

        String rootXml = Files.readString(configuration, StandardCharsets.UTF_8);
        assertThat(rootXml).containsOnlyOnce("<CommonTemplate>BinaryFixture</CommonTemplate>");
        assertThat(rootXml).doesNotContain("<Template>BinaryFixture</Template>");
        assertThat(rootXml.indexOf("<Role>FixtureRole</Role>"))
                .isLessThan(rootXml.indexOf("<CommonTemplate>BinaryFixture</CommonTemplate>"));
        assertThat(rootXml.indexOf("<CommonTemplate>BinaryFixture</CommonTemplate>"))
                .isLessThan(rootXml.indexOf("<CommonModule>FixtureModule</CommonModule>"));
    }

    @Test
    @DisplayName("integr-XG105 configuration-root remove restores exact tree and repeat is non-mutating")
    void removeCommonTemplateRestoresTreeAndRepeatDoesNotMutate() throws Exception {
        Path configuration = createConfigurationFixture();
        Path payload = writePayload("payload-remove.bin");
        Map<String, String> beforeAdd = snapshotTree(configuration.getParent());

        assertThat(runMain(
                "template", "add", configuration.toString(),
                "--name", "ToRemove", "--type", "binary", "--input", payload.toString()).exitCode())
                .isZero();

        ProcessResult removed = runMain(
                "template", "remove", configuration.toString(), "--name", "ToRemove");

        assertThat(removed.exitCode()).as(removed.stderr()).isZero();
        assertThat(snapshotTree(configuration.getParent())).isEqualTo(beforeAdd);

        Map<String, String> beforeRepeat = snapshotTree(configuration.getParent());
        ProcessResult repeated = runMain(
                "template", "remove", configuration.toString(), "--name", "ToRemove");

        assertThat(repeated.exitCode()).as(repeated.stderr()).isZero();
        assertThat(snapshotTree(configuration.getParent())).isEqualTo(beforeRepeat);
    }

    @Test
    @DisplayName("unit-XG105 missing binary payload fails before configuration mutation")
    void missingPayloadFailsBeforeMutation() throws Exception {
        Path configuration = createConfigurationFixture();
        Map<String, String> before = snapshotTree(configuration.getParent());

        ProcessResult result = runMain(
                "template", "add", configuration.toString(),
                "--name", "MissingPayload", "--type", "binary",
                "--input", tempDir.resolve("does-not-exist.bin").toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(snapshotTree(configuration.getParent())).isEqualTo(before);
    }

    @Test
    @DisplayName("unit-XG105 binary payload directory is rejected before mutation")
    void nonRegularPayloadFailsBeforeMutation() throws Exception {
        Path configuration = createConfigurationFixture();
        Path directoryPayload = Files.createDirectory(tempDir.resolve("payload-directory"));
        Map<String, String> before = snapshotTree(configuration.getParent());

        ProcessResult result = runMain(
                "template", "add", configuration.toString(),
                "--name", "DirectoryPayload", "--type", "binary",
                "--input", directoryPayload.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(snapshotTree(configuration.getParent())).isEqualTo(before);
    }

    @Test
    @DisplayName("unit-XG105 invalid CommonTemplate name fails before mutation")
    void invalidNameFailsBeforeMutation() throws Exception {
        Path configuration = createConfigurationFixture();
        Path payload = writePayload("payload-invalid-name.bin");
        Map<String, String> before = snapshotTree(configuration.getParent());

        ProcessResult result = runMain(
                "template", "add", configuration.toString(),
                "--name", "../Escaped", "--type", "binary", "--input", payload.toString());

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(snapshotTree(configuration.getParent())).isEqualTo(before);
        assertThat(tempDir.resolve("Escaped.xml")).doesNotExist();
    }

    private Path createConfigurationFixture() throws Exception {
        Path configRoot = tempDir.resolve("config");
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

    private Path writePayload(String filename) throws Exception {
        Path payload = tempDir.resolve(filename);
        Files.write(payload, PAYLOAD);
        return payload;
    }

    private void assertCanonicalBinaryWrapper(Path wrapper, String expectedName) throws Exception {
        byte[] raw = Files.readAllBytes(wrapper);
        assertThat(raw).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String xml = new String(raw, StandardCharsets.UTF_8);
        assertThat(xml).contains("\r\n");
        assertThat(xml.replace("\r\n", "")).doesNotContain("\n");

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(wrapper.toFile());
        Element metadata = document.getDocumentElement();
        assertThat(metadata.getLocalName()).isEqualTo("MetaDataObject");
        assertThat(metadata.getNamespaceURI()).isEqualTo("http://v8.1c.ru/8.3/MDClasses");
        assertThat(metadata.getAttribute("version")).isEqualTo("2.20");

        Element commonTemplate = directElement(metadata, "CommonTemplate");
        assertThat(commonTemplate).isNotNull();
        assertThatCodeUuid(commonTemplate.getAttribute("uuid"));
        Element properties = directElement(commonTemplate, "Properties");
        assertThat(directElementNames(properties))
                .containsExactly("Name", "Synonym", "Comment", "TemplateType");
        assertThat(directElement(properties, "Name").getTextContent()).isEqualTo(expectedName);
        Element synonym = directElement(properties, "Synonym");
        Element synonymItem = directElement(synonym, "item");
        assertThat(directElement(synonymItem, "lang").getTextContent()).isEqualTo("ru");
        assertThat(directElement(synonymItem, "content").getTextContent()).isEqualTo(expectedName);
        assertThat(directElement(properties, "Comment").getTextContent()).isEmpty();
        assertThat(directElement(properties, "TemplateType").getTextContent()).isEqualTo("BinaryData");
        assertThat(directElement(metadata, "Template")).isNull();
    }

    private void assertThatCodeUuid(String value) {
        assertThat(value).isNotBlank();
        assertThat(UUID.fromString(value).toString()).isEqualTo(value.toLowerCase());
    }

    private Element directElement(Element parent, String localName) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    private List<String> directElementNames(Element parent) {
        List<String> names = new ArrayList<>();
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element) {
                names.add(element.getLocalName());
            }
        }
        return names;
    }

    private ProcessResult runMain(String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Path stdout = tempDir.resolve("stdout-" + UUID.randomUUID() + ".log");
        Path stderr = tempDir.resolve("stderr-" + UUID.randomUUID() + ".log");
        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();
        try {
            boolean exited = process.waitFor(15, TimeUnit.SECONDS);
            if (!exited) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            assertThat(exited).as("xml-gen child process timed out").isTrue();
            return new ProcessResult(
                    process.exitValue(),
                    Files.readString(stdout, StandardCharsets.UTF_8),
                    Files.readString(stderr, StandardCharsets.UTF_8));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stderr);
        }
    }

    private Map<String, String> snapshotTree(Path root) throws Exception {
        Map<String, String> snapshot = new TreeMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                snapshot.put(root.relativize(path).toString(),
                        Base64.getEncoder().encodeToString(Files.readAllBytes(path)));
            }
        }
        return snapshot;
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
    }
}

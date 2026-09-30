package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.editor.SkdArtifactReplacer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-204 [10.07.2026 23:55:00]
class SkdReplaceFromFileXg83Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;
    Path stateDir;

    @BeforeEach
    void prepareDirectories() throws Exception {
        Files.createDirectories(tempDir.resolve("metadata"));
        stateDir = Files.createDirectory(tempDir.resolve("state"));
    }

    @Test
    void replacesBacksUpAndRestoresExactBytes() throws Exception {
        Path target = writeSkd("target.xml", "target", true, "\r\n");
        Path source = writeSkd("source.xml", "source", true, "\n");
        Path backup = tempDir.resolve("target.xg83.bak");
        Path manifest = SkdArtifactReplacer.manifestPath(target, stateDir);
        byte[] original = Files.readAllBytes(target);
        byte[] replacement = Files.readAllBytes(source);
        Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"));

        String swapOutput = execute("skd", "replace-from-file", target.toString(),
                "--source", source.toString(), "--expect-target-sha", sha(target),
                "--backup", backup.toString(), "--state-dir", stateDir.toString());

        assertThat(Files.readAllBytes(target)).isEqualTo(replacement);
        assertThat(Files.readAllBytes(backup)).isEqualTo(original);
        assertThat(swapOutput).contains("Old SHA-256:", "New SHA-256:", "Backup SHA-256:",
                "State: SWAPPED");
        assertThat(manifest).exists();
        assertThat(Files.readString(manifest)).contains("\"state\" : \"SWAPPED\"")
                .contains("\"permissions\" : \"rw-r--r--\"");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
                .isEqualTo("rw-r--r--");

        assertThatThrownBy(() -> execute("skd", "replace-from-file", target.toString(),
                "--source", backup.toString(), "--expect-target-sha", sha(target),
                "--backup", tempDir.resolve("replacement.bak").toString(),
                "--state-dir", stateDir.toString()))
                .hasMessageContaining("Active XG-83 manifest");

        String restoreOutput = execute("skd", "restore-from-manifest", manifest.toString(),
                "--state-dir", stateDir.toString());

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
                .isEqualTo("rw-r--r--");
        assertThat(manifest).doesNotExist();
        assertThat(restoreOutput).contains("State: RESTORED", "Archived manifest:");
        try (var paths = Files.list(stateDir)) {
            assertThat(paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.contains("manifest.json.restored-")).toList()).hasSize(1);
        }
        try (var paths = Files.list(target.getParent())) {
            assertThat(paths.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("target.xml", "source.xml");
        }
        assertNoTempFiles();
    }

    @Test
    void rejectsShaMismatchInvalidSourceSelfAndBackupCollisionWithoutMutation() throws Exception {
        Path target = writeSkd("guard-target.xml", "target", true, "\r\n");
        Path source = writeSkd("guard-source.xml", "source", true, "\r\n");
        byte[] before = Files.readAllBytes(target);
        Path collision = tempDir.resolve("collision.bak");
        Files.writeString(collision, "occupied", StandardCharsets.UTF_8);
        Path invalid = tempDir.resolve("invalid.xml");
        Files.writeString(invalid, "<not-skd/>", StandardCharsets.UTF_8);

        assertFailure(target, source, "0".repeat(64), tempDir.resolve("sha.bak"));
        assertFailure(target, invalid, sha(target), tempDir.resolve("invalid.bak"));
        assertFailure(target, target, sha(target), tempDir.resolve("self.bak"));
        assertFailure(target, source, sha(target), collision);

        assertThat(Files.readAllBytes(target)).isEqualTo(before);
        assertThat(Files.exists(tempDir.resolve("sha.bak"))).isFalse();
        assertThat(Files.exists(tempDir.resolve("invalid.bak"))).isFalse();
        assertNoTempFiles();
    }

    @Test
    void dryRunValidatesButWritesNothingAndAcceptsBomCrLf() throws Exception {
        Path target = writeSkd("dry-target.xml", "target", true, "\r\n");
        Path source = writeSkd("dry-source.xml", "source", true, "\r\n");
        Path backup = tempDir.resolve("dry.bak");
        byte[] before = Files.readAllBytes(target);

        String output = execute("skd", "replace-from-file", target.toString(),
                "--source", source.toString(), "--expect-target-sha", sha(target),
                "--backup", backup.toString(), "--state-dir", stateDir.toString(), "--dry-run");

        assertThat(output).contains("[DRY-RUN]");
        assertThat(Files.readAllBytes(target)).isEqualTo(before);
        assertThat(backup).doesNotExist();
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir))
                .doesNotExist();
        assertNoTempFiles();
    }

    @Test
    void restoreRejectsTamperedTargetAndBackupAndKeepsActiveManifest() throws Exception {
        Path target = writeSkd("tamper-target.xml", "target", true, "\r\n");
        Path source = writeSkd("tamper-source.xml", "source", true, "\r\n");
        Path backup = tempDir.resolve("tamper.bak");
        execute("skd", "replace-from-file", target.toString(), "--source", source.toString(),
                "--expect-target-sha", sha(target), "--backup", backup.toString(),
                "--state-dir", stateDir.toString());
        Path manifest = SkdArtifactReplacer.manifestPath(target, stateDir);

        Path foreign = writeSkd("foreign.xml", "foreign", true, "\r\n");
        Files.copy(foreign, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        assertThatThrownBy(() -> execute("skd", "restore-from-manifest", manifest.toString(),
                "--state-dir", stateDir.toString()))
                .hasMessageContaining("neither active replacement nor original");
        assertThat(manifest).exists();

        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(backup, "tampered", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> execute("skd", "restore-from-manifest", manifest.toString(),
                "--state-dir", stateDir.toString()))
                .hasMessageContaining("Backup SHA-256 mismatch");
        assertThat(manifest).exists();
    }

    private void assertFailure(Path target, Path source, String sha, Path backup) {
        assertThatThrownBy(() -> execute("skd", "replace-from-file", target.toString(),
                "--source", source.toString(), "--expect-target-sha", sha,
                "--backup", backup.toString(), "--state-dir", stateDir.toString()))
                .isInstanceOf(RuntimeException.class);
    }

    private String execute(String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try {
            System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
            Commands.execute(args[0], java.util.Arrays.copyOfRange(args, 1, args.length));
            return bytes.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(previous);
        }
    }

    private Path writeSkd(String name, String marker, boolean bom, String eol) throws Exception {
        String xml = ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dataSet xsi:type="DataSetObject"><name>%s</name><objectName>%s</objectName></dataSet>
                  <settingsVariant><name>Main</name><settings/></settingsVariant>
                </DataCompositionSchema>
                """).formatted(marker, marker).replace("\n", eol);
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = bom ? java.nio.ByteBuffer.allocate(BOM.length + body.length).put(BOM).put(body).array() : body;
        Path path = tempDir.resolve("metadata").resolve(name);
        Files.write(path, bytes);
        return path;
    }

    private String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private void assertNoTempFiles() throws Exception {
        try (var paths = Files.walk(tempDir)) {
            assertThat(paths.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(".xml-gen-xg83-")).toList()).isEmpty();
        }
    }
}
//++agent TASK-204

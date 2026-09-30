package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.editor.SkdArtifactReplacer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-204 [12.07.2026 00:00:00]
class SkdReplaceFromFileXg95Test {

    @TempDir
    Path tempDir;
    Path metadataDir;
    Path stateDir;

    @BeforeEach
    void prepareDirectories() throws Exception {
        metadataDir = Files.createDirectory(tempDir.resolve("metadata"));
        stateDir = Files.createDirectory(tempDir.resolve("state"));
    }

    @Test
    void replacementWithoutExplicitBackupLeavesNoBackupOrManifestAndCanRepeat() throws Exception {
        Path target = writeSkd(metadataDir.resolve("Template.xml"), "original", "\r\n");
        Path first = writeSkd(metadataDir.resolve("first.xml"), "first", "\n");
        Path second = writeSkd(metadataDir.resolve("second.xml"), "second", "\r\n");

        Commands.execute("skd", new String[]{"replace-from-file", target.toString(),
                "--source", first.toString(), "--expect-target-sha", sha(target),
                "--state-dir", stateDir.toString()});
        Commands.execute("skd", new String[]{"replace-from-file", target.toString(),
                "--source", second.toString(), "--expect-target-sha", sha(target),
                "--state-dir", stateDir.toString()});

        assertThat(Files.readAllBytes(target)).isEqualTo(Files.readAllBytes(second));
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
        try (var files = Files.list(metadataDir)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("Template.xml", "first.xml", "second.xml");
        }
    }

    @Test
    void explicitBackupInsideTargetMetadataDirectoryIsRejectedWithoutMutation() throws Exception {
        Path target = writeSkd(metadataDir.resolve("guarded.xml"), "original", "\r\n");
        Path source = writeSkd(metadataDir.resolve("guarded-source.xml"), "replacement", "\r\n");
        byte[] original = Files.readAllBytes(target);
        Path insideBackup = metadataDir.resolve("guarded.bak");

        assertThatThrownBy(() -> Commands.execute("skd", new String[]{"replace-from-file",
                target.toString(), "--source", source.toString(), "--expect-target-sha", sha(target),
                "--backup", insideBackup.toString(), "--state-dir", stateDir.toString()}))
                .hasMessageContaining("outside the Designer metadata tree");

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(insideBackup).doesNotExist();
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
    }

    @Test
    void backupInSiblingDirectoryUnderDesignerMetadataRootIsRejectedBeforeMutation() throws Exception {
        Path designerRoot = Files.createDirectory(tempDir.resolve("designer-root"));
        Files.writeString(designerRoot.resolve("Configuration.xml"), "configuration marker");
        Path targetDir = Files.createDirectories(designerRoot.resolve("Reports/R/Templates/Main/Ext"));
        Path target = writeSkd(targetDir.resolve("Template.xml"), "original", "\r\n");
        Path source = writeSkd(targetDir.resolve("source.xml"), "replacement", "\r\n");
        Path escapedBackup = designerRoot.resolve("escaped.bak");
        byte[] original = Files.readAllBytes(target);

        assertThatThrownBy(() -> Commands.execute("skd", new String[]{"replace-from-file",
                target.toString(), "--source", source.toString(), "--expect-target-sha", sha(target),
                "--backup", escapedBackup.toString(), "--state-dir", stateDir.toString()}))
                .hasMessageContaining("Designer metadata tree");

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(escapedBackup).doesNotExist();
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
        assertStateDirectoryEmpty();
    }

    @Test
    void externalSymlinkParentResolvingInsideDesignerMetadataRootIsRejectedBeforeMutation() throws Exception {
        Path designerRoot = Files.createDirectory(tempDir.resolve("symlink-designer-root"));
        Files.writeString(designerRoot.resolve("Configuration.xml"), "configuration marker");
        Path targetDir = Files.createDirectories(designerRoot.resolve("Reports/R/Templates/Main/Ext"));
        Path target = writeSkd(targetDir.resolve("Template.xml"), "original", "\r\n");
        Path source = writeSkd(targetDir.resolve("source.xml"), "replacement", "\r\n");
        Path external = Files.createDirectory(tempDir.resolve("external"));
        Path link = Files.createSymbolicLink(external.resolve("link-to-metadata"), designerRoot);
        Path escapedBackup = link.resolve("escaped.bak");
        byte[] original = Files.readAllBytes(target);

        assertThatThrownBy(() -> Commands.execute("skd", new String[]{"replace-from-file",
                target.toString(), "--source", source.toString(), "--expect-target-sha", sha(target),
                "--backup", escapedBackup.toString(), "--state-dir", stateDir.toString()}))
                .hasMessageContaining("Designer metadata tree");

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(designerRoot.resolve("escaped.bak")).doesNotExist();
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
        assertStateDirectoryEmpty();
    }

    private Path writeSkd(Path path, String marker, String eol) throws Exception {
        String xml = ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dataSet xsi:type="DataSetObject"><name>%s</name><objectName>%s</objectName></dataSet>
                  <settingsVariant><name>Main</name><settings/></settingsVariant>
                </DataCompositionSchema>
                """).formatted(marker, marker).replace("\n", eol);
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        Files.write(path, java.nio.ByteBuffer.allocate(body.length + 3)
                .put(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}).put(body).array());
        return path;
    }

    private String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private void assertStateDirectoryEmpty() throws Exception {
        try (var files = Files.list(stateDir)) {
            assertThat(files.toList()).isEmpty();
        }
    }
}
//++agent TASK-204

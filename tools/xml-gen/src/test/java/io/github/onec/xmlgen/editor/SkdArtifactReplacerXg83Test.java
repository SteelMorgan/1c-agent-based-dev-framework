package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-204 [11.07.2026 00:20:00]
class SkdArtifactReplacerXg83Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;
    Path stateDir;

    @BeforeEach
    void prepareDirectories() throws Exception {
        Files.createDirectory(tempDir.resolve("metadata"));
        stateDir = Files.createDirectory(tempDir.resolve("state"));
    }

    @Test
    void stableSiblingLockBlocksConcurrentReplaceUntilRelease() throws Exception {
        Path target = writeSkd("locked.xml", "original");
        Path source = writeSkd("locked-source.xml", "replacement");
        Path backup = tempDir.resolve("locked.bak");
        Path lockPath = SkdArtifactReplacer.lockPath(target, stateDir);
        Files.createFile(lockPath);
        var executor = Executors.newSingleThreadExecutor();

        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.READ,
                StandardOpenOption.WRITE); var lock = channel.lock()) {
            var replace = executor.submit(() -> new SkdArtifactReplacer().replace(
                    target, source, sha(target), backup, stateDir, false));
            assertThatThrownBy(() -> replace.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            lock.release();
            SkdArtifactReplacer.Result swapped = replace.get(5, TimeUnit.SECONDS);
            assertThat(swapped.state()).isEqualTo(SkdArtifactReplacer.State.SWAPPED);
            new SkdArtifactReplacer().restore(swapped.manifest(), stateDir);
        } finally {
            executor.shutdownNow();
        }
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
        assertThat(lockPath).exists();
    }

    @Test
    void advisoryLockBypassMutationIsDetectedImmediatelyBeforeCommit() throws Exception {
        Path target = writeSkd("race.xml", "original");
        Path source = writeSkd("race-source.xml", "replacement");
        Path concurrent = writeSkd("race-concurrent.xml", "newer");
        byte[] newer = Files.readAllBytes(concurrent);
        String expected = sha(target);
        SkdArtifactReplacer replacer = new SkdArtifactReplacer(() -> {
            try {
                Files.write(target, newer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, () -> { });

        assertThatThrownBy(() -> replacer.replace(target, source, expected,
                tempDir.resolve("race.bak"), stateDir, false))
                .hasMessageContaining("changed during locked transaction");

        assertThat(Files.readAllBytes(target)).isEqualTo(newer);
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).exists();
        assertThat(Files.readString(SkdArtifactReplacer.manifestPath(target, stateDir)))
                .contains("PREPARED");
    }

    @Test
    void crashAfterActivationLeavesRecoverableManifestAndExactRestore() throws Exception {
        Path target = writeSkd("crash.xml", "original");
        Path source = writeSkd("crash-source.xml", "replacement");
        Path backup = tempDir.resolve("crash.bak");
        byte[] original = Files.readAllBytes(target);
        byte[] replacement = Files.readAllBytes(source);
        SkdArtifactReplacer replacer = new SkdArtifactReplacer(() -> {
            throw new SimulatedCrash();
        });

        assertThatThrownBy(() -> replacer.replace(target, source, sha(target), backup, stateDir, false))
                .isInstanceOf(SimulatedCrash.class);
        Path manifest = SkdArtifactReplacer.manifestPath(target, stateDir);
        assertThat(Files.readAllBytes(target)).isEqualTo(replacement);
        assertThat(Files.readAllBytes(backup)).isEqualTo(original);
        assertThat(manifest).exists();
        assertThat(Files.readString(manifest)).contains("PREPARED");

        SkdArtifactReplacer.Result restored = new SkdArtifactReplacer().restore(manifest, stateDir);

        assertThat(restored.state()).isEqualTo(SkdArtifactReplacer.State.RESTORED);
        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(manifest).doesNotExist();
        assertThat(restored.archivedManifest()).exists();
    }

    @Test
    void backupFreeFailureAfterActivationRollsBackExactOriginalWithoutManifest() throws Exception {
        Path target = writeSkd("backup-free-rollback.xml", "original");
        Path source = writeSkd("backup-free-rollback-source.xml", "replacement");
        byte[] original = Files.readAllBytes(target);
        SkdArtifactReplacer replacer = new SkdArtifactReplacer(() -> {
            throw new SimulatedCrash();
        });

        assertThatThrownBy(() -> replacer.replace(target, source, sha(target), null, stateDir, false))
                .isInstanceOf(SimulatedCrash.class);

        assertThat(Files.readAllBytes(target)).isEqualTo(original);
        assertThat(SkdArtifactReplacer.manifestPath(target, stateDir)).doesNotExist();
    }

    @Test
    void restoreAndSecondReplaceBlockAcrossActivationUntilManifestIsSwapped() throws Exception {
        Path target = writeSkd("barrier.xml", "original");
        Path source = writeSkd("barrier-source.xml", "replacement");
        Path secondSource = writeSkd("barrier-second.xml", "second");
        Path backup = tempDir.resolve("barrier.bak");
        byte[] original = Files.readAllBytes(target);
        CountDownLatch activated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SkdArtifactReplacer pausing = new SkdArtifactReplacer(() -> {
            activated.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("barrier timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
        var executor = Executors.newFixedThreadPool(3);
        try {
            var swap = executor.submit(() -> pausing.replace(
                    target, source, sha(target), backup, stateDir, false));
            assertThat(activated.await(5, TimeUnit.SECONDS)).isTrue();
            Path manifest = SkdArtifactReplacer.manifestPath(target, stateDir);
            assertThat(Files.readString(manifest)).contains("PREPARED");

            var restore = executor.submit(() -> new SkdArtifactReplacer().restore(manifest, stateDir));
            var second = executor.submit(() -> new SkdArtifactReplacer().replace(target, secondSource,
                    sha(source), tempDir.resolve("barrier-second.bak"), stateDir, false));
            assertThatThrownBy(() -> restore.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            assertThatThrownBy(() -> second.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            release.countDown();
            assertThat(swap.get(5, TimeUnit.SECONDS).state())
                    .isEqualTo(SkdArtifactReplacer.State.SWAPPED);
            SkdArtifactReplacer.Result restored = restore.get(5, TimeUnit.SECONDS);
            assertThat(restored.state()).isEqualTo(SkdArtifactReplacer.State.RESTORED);
            assertThatThrownBy(() -> second.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
            assertThat(Files.readAllBytes(target)).isEqualTo(original);
            assertThat(manifest).doesNotExist();
            assertThat(restored.archivedManifest()).exists();
            assertThat(SkdArtifactReplacer.lockPath(target, stateDir)).exists();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void externalStateKeysSeparateTargetsAndMetadataTreeStaysClean() throws Exception {
        Path first = writeSkd("first.xml", "first");
        Path second = writeSkd("second.xml", "second");
        assertThat(SkdArtifactReplacer.lockPath(first, stateDir))
                .isNotEqualTo(SkdArtifactReplacer.lockPath(second, stateDir));
        assertThat(SkdArtifactReplacer.manifestPath(first, stateDir).getParent()).isEqualTo(stateDir);

        Path source = writeSkd("state-source.xml", "replacement");
        var result = new SkdArtifactReplacer().replace(first, source, sha(first),
                tempDir.resolve("state.bak"), stateDir, false);
        assertThat(java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(stateDir))).isEqualTo("rwx------");
        assertThat(java.nio.file.attribute.PosixFilePermissions.toString(
                Files.getPosixFilePermissions(SkdArtifactReplacer.lockPath(first, stateDir))))
                .isEqualTo("rw-------");
        try (var files = Files.list(first.getParent())) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("first.xml", "second.xml", "state-source.xml");
        }
        new SkdArtifactReplacer().restore(result.manifest(), stateDir);
        try (var files = Files.list(first.getParent())) {
            assertThat(files.map(p -> p.getFileName().toString()).toList())
                    .containsExactlyInAnyOrder("first.xml", "second.xml", "state-source.xml");
        }
    }

    @Test
    void rejectsInTargetSymlinkTraversalAndLegacySiblingState() throws Exception {
        Path target = writeSkd("unsafe.xml", "original");
        Path source = writeSkd("unsafe-source.xml", "replacement");
        Path inside = Files.createDirectory(target.getParent().resolve("state"));
        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("inside.bak"), inside, false)).hasMessageContaining("external");

        Path link = tempDir.resolve("state-link");
        Files.createSymbolicLink(link, stateDir);
        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("link.bak"), link, false)).hasMessageContaining("Symlink");

        Path traversal = stateDir.resolve("missing").resolve("..");
        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("traversal.bak"), traversal, false)).hasMessageContaining("traversal");

        Path legacy = target.resolveSibling(target.getFileName() + ".xml-gen-xg83-manifest.json");
        Files.writeString(legacy, "legacy");
        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("legacy.bak"), stateDir, false)).hasMessageContaining("Legacy sibling");
    }

    @Test
    void rejectsRepositoryAndMetadataParentAncestorsOfTarget() throws Exception {
        Path repository = Files.createDirectory(tempDir.resolve("repository"));
        Path src = Files.createDirectory(repository.resolve("src"));
        Path metadataRoot = Files.createDirectory(src.resolve("xml"));
        Files.writeString(metadataRoot.resolve("Configuration.xml"), "<Configuration/>");
        Path ext = Files.createDirectories(metadataRoot.resolve("Reports/R/Templates/T/Ext"));
        Path target = writeSkdAt(ext.resolve("Template.xml"), "target");
        Path source = writeSkd("ancestor-source.xml", "source");

        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("ancestor-src.bak"), src, true))
                .hasMessageContaining("ancestor");
        assertThatThrownBy(() -> new SkdArtifactReplacer().replace(target, source, sha(target),
                tempDir.resolve("ancestor-repo.bak"), repository, true))
                .hasMessageContaining("ancestor");
        assertThat(SkdArtifactReplacer.manifestPath(target, src)).doesNotExist();
        assertThat(SkdArtifactReplacer.lockPath(target, src)).doesNotExist();
    }

    private Path writeSkd(String name, String marker) throws Exception {
        return writeSkdAt(tempDir.resolve("metadata").resolve(name), marker);
    }

    private Path writeSkdAt(Path path, String marker) throws Exception {
        String xml = ("""
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <dataSet xsi:type="DataSetObject"><name>%s</name><objectName>%s</objectName></dataSet>
                  <settingsVariant><name>Main</name><settings/></settingsVariant>
                </DataCompositionSchema>
                """).formatted(marker, marker).replace("\n", "\r\n");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        Files.write(path, ByteBuffer.allocate(BOM.length + body.length).put(BOM).put(body).array());
        return path;
    }

    private String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static final class SimulatedCrash extends RuntimeException { }
}
//++agent TASK-204

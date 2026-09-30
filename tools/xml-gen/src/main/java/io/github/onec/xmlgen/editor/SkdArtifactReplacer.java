package io.github.onec.xmlgen.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.validator.GenValidator;
import io.github.onec.xmlgen.validator.Severity;
import io.github.onec.xmlgen.validator.SkdValidator;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationLevel;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Диагностическая транзакция полной подмены существующего SKD Template.xml.
 * Manifest остаётся активным до отдельного restore и не позволяет начать вторую подмену.
 */
//++agent TASK-204 [11.07.2026 00:20:00]
public final class SkdArtifactReplacer {

    private static final String SKD_ROOT = "DataCompositionSchema";
    private static final String SKD_NS = "http://v8.1c.ru/8.1/data-composition-system/schema";
    private static final ObjectMapper JSON = new ObjectMapper();

    public enum State { PREPARED, SWAPPED, RESTORED }

    public record Manifest(int version, String target, String source, String backup,
                           String originalSha, String replacementSha, String permissions,
                           String owner, String group, State state, String createdAt,
                           String updatedAt) {
        Manifest withState(State newState) {
            return new Manifest(version, target, source, backup, originalSha, replacementSha,
                    permissions, owner, group, newState, createdAt, Instant.now().toString());
        }
    }

    public record Result(Path target, Path source, Path backup, Path manifest, Path archivedManifest,
                         String oldSha, String newSha, String backupSha, boolean dryRun,
                         State state) { }

    private record Attributes(String permissions, UserPrincipal owner, GroupPrincipal group) { }

    private final Runnable beforeCommit;
    private final Runnable afterActivation;

    public SkdArtifactReplacer() {
        this(() -> { }, () -> { });
    }

    SkdArtifactReplacer(Runnable afterActivation) {
        this(() -> { }, afterActivation);
    }

    SkdArtifactReplacer(Runnable beforeCommit, Runnable afterActivation) {
        this.beforeCommit = Objects.requireNonNull(beforeCommit);
        this.afterActivation = Objects.requireNonNull(afterActivation);
    }

    public Result replace(Path targetArg, Path sourceArg, String expectedTargetSha,
                          Path backupArg, Path stateDirArg, boolean dryRun) throws IOException {
        requireSha(expectedTargetSha);
        Path target = existingRegularFile(targetArg, "Target").toRealPath();
        Path source = existingRegularFile(sourceArg, "Source").toRealPath();
        if (Files.isSameFile(target, source)) {
            throw new IllegalArgumentException("Source and target must be different files");
        }
        Path preflightBackup = backupArg == null ? null : validateBackupPath(target, source, backupArg);
        Path stateDir = prepareStateDir(target, stateDirArg);
        Path manifestPath = manifestPath(target, stateDir);
        Path lockPath = lockPath(target, stateDir);
        try (FileChannel lockChannel = openStableLock(lockPath);
             FileLock ignored = acquireLock(lockChannel, lockPath)) {
            rejectLegacySiblingState(target);
            if (Files.exists(manifestPath)) {
                Manifest active = readManifest(manifestPath);
                throw new IllegalArgumentException("Active XG-83 manifest blocks replacement: "
                        + manifestPath + " (state=" + active.state() + "). Restore it first.");
            }

            byte[] targetBytes = Files.readAllBytes(target);
            byte[] sourceBytes = Files.readAllBytes(source);
            String oldSha = sha256(targetBytes);
            String newSha = sha256(sourceBytes);
            if (!oldSha.equalsIgnoreCase(expectedTargetSha)) {
                throw new IllegalArgumentException("Target SHA-256 mismatch under exclusive lock: expected "
                        + expectedTargetSha.toLowerCase() + ", actual " + oldSha);
            }

            XmlDocument targetDocument = parseAndValidate(target, "target");
            XmlDocument sourceDocument = parseAndValidate(source, "source");
            requireSameSkdType(targetDocument, sourceDocument);

            Attributes attributes = readAttributes(target);

            if (backupArg == null) {
                if (dryRun) {
                    return new Result(target, source, null, null, null,
                            oldSha, newSha, null, true, State.PREPARED);
                }
                return replaceWithoutBackup(target, source, targetBytes, sourceBytes,
                        oldSha, newSha, attributes);
            }

            Path backup = validateBackupPath(target, source, backupArg);
            if (!backup.equals(preflightBackup)) {
                throw new IllegalArgumentException("Backup parent changed after preflight: " + backupArg);
            }

            if (dryRun) {
                return new Result(target, source, backup, manifestPath, null,
                        oldSha, newSha, oldSha, true, State.PREPARED);
            }

            writeAtomicNew(backup, targetBytes, attributes);
            String backupSha = sha256(Files.readAllBytes(backup));
            if (!oldSha.equals(backupSha)) {
                throw new IOException("Backup SHA verification failed: " + backup);
            }

            Manifest manifest = new Manifest(1, target.toString(), source.toString(), backup.toString(),
                    oldSha, newSha, attributes.permissions(), principalName(attributes.owner()),
                    principalName(attributes.group()), State.PREPARED, Instant.now().toString(),
                    Instant.now().toString());
            writeManifestNew(manifestPath, manifest);

            Path targetTemp = null;
            try {
                targetTemp = createDurableTemp(target.getParent(), ".xml-gen-xg83-target-",
                        sourceBytes, attributes);
                parseAndValidate(targetTemp, "replacement candidate");

                beforeCommit.run();
                // Re-read immediately before commit while the exclusive target lock is held.
                String commitSha = sha256(Files.readAllBytes(target));
                if (!oldSha.equals(commitSha)) {
                    throw new IllegalArgumentException("Target changed during locked transaction: expected "
                            + oldSha + ", actual " + commitSha);
                }
                Files.move(targetTemp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                targetTemp = null;
                fsyncDirectory(target.getParent());
                verifyPermissions(target, attributes.permissions());

                afterActivation.run();

                String activeSha = sha256(Files.readAllBytes(target));
                if (!newSha.equals(activeSha)) {
                    throw new IOException("Post-swap SHA verification failed; manifest and backup preserved");
                }
                manifest = manifest.withState(State.SWAPPED);
                writeManifestReplace(manifestPath, manifest);
                return new Result(target, source, backup, manifestPath, null,
                        oldSha, activeSha, backupSha, false, State.SWAPPED);
            } finally {
                deleteQuietly(targetTemp);
            }
        }
    }

    private Result replaceWithoutBackup(Path target, Path source, byte[] originalBytes,
                                        byte[] sourceBytes, String oldSha, String newSha,
                                        Attributes attributes) throws IOException {
        Path targetTemp = null;
        boolean activated = false;
        try {
            targetTemp = createDurableTemp(target.getParent(), ".xml-gen-xg83-target-",
                    sourceBytes, attributes);
            parseAndValidate(targetTemp, "replacement candidate");
            beforeCommit.run();
            String commitSha = sha256(Files.readAllBytes(target));
            if (!oldSha.equals(commitSha)) {
                throw new IllegalArgumentException("Target changed during locked transaction: expected "
                        + oldSha + ", actual " + commitSha);
            }
            Files.move(targetTemp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            targetTemp = null;
            activated = true;
            fsyncDirectory(target.getParent());
            verifyPermissions(target, attributes.permissions());
            afterActivation.run();
            String activeSha = sha256(Files.readAllBytes(target));
            if (!newSha.equals(activeSha)) {
                throw new IOException("Post-swap SHA verification failed");
            }
            return new Result(target, source, null, null, null,
                    oldSha, activeSha, null, false, State.SWAPPED);
        } catch (Throwable failure) {
            if (activated) {
                try {
                    Path rollback = createDurableTemp(target.getParent(), ".xml-gen-xg83-rollback-",
                            originalBytes, attributes);
                    Files.move(rollback, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    fsyncDirectory(target.getParent());
                    verifyPermissions(target, attributes.permissions());
                } catch (Throwable rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            if (failure instanceof IOException ioFailure) throw ioFailure;
            if (failure instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (failure instanceof Error error) throw error;
            throw new IOException("Backup-free replacement failed", failure);
        } finally {
            deleteQuietly(targetTemp);
        }
    }

    public Result restore(Path manifestArg, Path stateDirArg) throws IOException {
        Path manifestPath = manifestArg.toAbsolutePath().normalize();
        if (!manifestPath.equals(manifestArg.toAbsolutePath())) {
            throw new IllegalArgumentException("Path traversal is not allowed in manifest path: " + manifestArg);
        }
        Path stateDir = prepareStateDirWithoutTarget(stateDirArg);
        if (!Objects.equals(manifestPath.getParent(), stateDir)) {
            throw new IllegalArgumentException("Manifest must be directly inside --state-dir: " + manifestPath);
        }
        String key = keyFromManifestName(manifestPath);
        Path lockPath = stateDir.resolve(key + ".lock");

        try (FileChannel lockChannel = openStableLock(lockPath);
             FileLock ignored = acquireLock(lockChannel, lockPath)) {
            manifestPath = existingRegularFile(manifestPath, "Manifest").toRealPath();
            Manifest manifest = readManifest(manifestPath);
            Path target = existingRegularFile(Path.of(manifest.target()), "Target").toRealPath();
            prepareStateDir(target, stateDir);
            if (!key.equals(targetKey(target))) {
                throw new IllegalArgumentException("Manifest key does not match canonical target: " + target);
            }
            rejectLegacySiblingState(target);
            if (!target.equals(Path.of(manifest.target()).toAbsolutePath().normalize())) {
                throw new IllegalArgumentException("Manifest target changed while acquiring lock");
            }
            if (manifest.state() != State.PREPARED && manifest.state() != State.SWAPPED
                    && manifest.state() != State.RESTORED) {
                throw new IllegalArgumentException("Manifest is not restorable: state=" + manifest.state());
            }
            Path backup = existingRegularFile(Path.of(manifest.backup()), "Backup").toRealPath();
            byte[] backupBytes = Files.readAllBytes(backup);
            String backupSha = sha256(backupBytes);
            if (!manifest.originalSha().equals(backupSha)) {
                throw new IllegalArgumentException("Backup SHA-256 mismatch: expected "
                        + manifest.originalSha() + ", actual " + backupSha);
            }

            String currentSha = sha256(Files.readAllBytes(target));
            if (!currentSha.equals(manifest.replacementSha())
                    && !currentSha.equals(manifest.originalSha())) {
                throw new IllegalArgumentException("Target SHA is neither active replacement nor original: "
                        + currentSha);
            }

            if (currentSha.equals(manifest.replacementSha())) {
                Path temp = null;
                try {
                    Attributes attrs = attributesFromManifest(manifest, target);
                    temp = createDurableTemp(target.getParent(), ".xml-gen-xg83-restore-",
                            backupBytes, attrs);
                    parseAndValidate(temp, "restore candidate");
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                    temp = null;
                    fsyncDirectory(target.getParent());
                    verifyPermissions(target, manifest.permissions());
                } finally {
                    deleteQuietly(temp);
                }
            }

            String restoredSha = sha256(Files.readAllBytes(target));
            if (!manifest.originalSha().equals(restoredSha)) {
                throw new IOException("Exact restore SHA verification failed; active manifest retained");
            }
            Manifest restored = manifest.withState(State.RESTORED);
            writeManifestReplace(manifestPath, restored);
            Path archive = archiveManifest(manifestPath);
            return new Result(target, Path.of(manifest.source()), backup, manifestPath, archive,
                    manifest.replacementSha(), restoredSha, backupSha, false, State.RESTORED);
        }
    }

    public static Path manifestPath(Path target, Path stateDir) {
        return stateDir.toAbsolutePath().normalize().resolve(targetKey(target) + ".manifest.json");
    }

    public static Path lockPath(Path target, Path stateDir) {
        return stateDir.toAbsolutePath().normalize().resolve(targetKey(target) + ".lock");
    }

    public static String targetKey(Path target) {
        try {
            Path canonical = target.toRealPath();
            return sha256(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot canonicalize target for XG-84 state key: " + target, e);
        }
    }

    private static String keyFromManifestName(Path manifest) {
        String name = manifest.getFileName().toString();
        String suffix = ".manifest.json";
        String key = name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : "";
        if (!key.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid XG-84 manifest filename: " + manifest);
        }
        return key;
    }

    private static Path prepareStateDir(Path target, Path stateDirArg) throws IOException {
        Path stateDir = prepareStateDirWithoutTarget(stateDirArg);
        Path canonicalTarget = target.toRealPath();
        Path targetDir = target.toRealPath().getParent();
        if (canonicalTarget.equals(stateDir) || canonicalTarget.startsWith(stateDir)) {
            throw new IllegalArgumentException("--state-dir must not equal or be an ancestor of target: "
                    + stateDir);
        }
        if (stateDir.equals(targetDir) || stateDir.startsWith(targetDir)) {
            throw new IllegalArgumentException("--state-dir must be external to target directory: " + stateDir);
        }
        Path metadataRoot = findMetadataRoot(targetDir);
        if (metadataRoot != null && (stateDir.startsWith(metadataRoot)
                || metadataRoot.equals(stateDir) || metadataRoot.startsWith(stateDir))) {
            throw new IllegalArgumentException("--state-dir must be external to 1C metadata tree "
                    + metadataRoot + ": " + stateDir);
        }
        return stateDir;
    }

    private static Path prepareStateDirWithoutTarget(Path stateDirArg) throws IOException {
        if (stateDirArg == null) {
            throw new IllegalArgumentException("--state-dir is required for XG-84 transaction state");
        }
        Path raw = stateDirArg.toAbsolutePath();
        Path normalized = raw.normalize();
        if (!raw.equals(normalized)) {
            throw new IllegalArgumentException("Path traversal is not allowed in --state-dir: " + stateDirArg);
        }
        rejectSymlinkComponents(normalized);
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("--state-dir must be an existing directory: " + normalized);
        }
        Path real = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
        PosixFileAttributeView view = Files.getFileAttributeView(real, PosixFileAttributeView.class);
        if (view != null) {
            view.setPermissions(PosixFilePermissions.fromString("rwx------"));
            String actual = PosixFilePermissions.toString(view.readAttributes().permissions());
            if (!"rwx------".equals(actual)) {
                throw new IOException("Cannot enforce 0700 permissions on --state-dir: " + real);
            }
        }
        return real;
    }

    private static void rejectSymlinkComponents(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path component : path) {
            current = current == null ? component : current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("Symlink is not allowed in --state-dir path: " + current);
            }
        }
    }

    private static Path findMetadataRoot(Path start) throws IOException {
        Path dir = start;
        while (dir != null) {
            if (Files.isRegularFile(dir.resolve("Configuration.xml"), LinkOption.NOFOLLOW_LINKS)) {
                return dir.toRealPath();
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static void rejectLegacySiblingState(Path target) throws IOException {
        Path parent = target.getParent();
        Path legacyManifest = target.resolveSibling(target.getFileName()
                + ".xml-gen-xg83-manifest.json");
        Path legacyLock = target.resolveSibling("." + target.getFileName() + ".xml-gen-xg83.lock");
        List<Path> found = new ArrayList<>();
        if (Files.exists(legacyManifest, LinkOption.NOFOLLOW_LINKS)) found.add(legacyManifest);
        if (Files.exists(legacyLock, LinkOption.NOFOLLOW_LINKS)) found.add(legacyLock);
        try (var paths = Files.newDirectoryStream(parent,
                target.getFileName() + ".xml-gen-xg83-manifest.json.restored-*")) {
            for (Path path : paths) found.add(path);
        }
        if (!found.isEmpty()) {
            throw new IllegalArgumentException("Legacy sibling XG-83 state is inside the 1C metadata tree: "
                    + found + ". Verify recovery, then move/archive it outside the metadata tree before retry.");
        }
    }

    private static FileChannel openStableLock(Path lockPath) throws IOException {
        if (!Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)) {
            try {
                PosixFileAttributeView posix = Files.getFileAttributeView(
                        lockPath.getParent(), PosixFileAttributeView.class);
                if (posix != null) {
                    Files.createFile(lockPath, PosixFilePermissions.asFileAttribute(
                            PosixFilePermissions.fromString("rw-------")));
                } else {
                    Files.createFile(lockPath);
                }
            } catch (java.nio.file.FileAlreadyExistsException ignored) { }
        }
        if (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(lockPath)) {
            throw new IllegalArgumentException("Unsafe XG-83 lock path: " + lockPath);
        }
        PosixFileAttributeView view = Files.getFileAttributeView(lockPath, PosixFileAttributeView.class);
        if (view != null) {
            view.setPermissions(PosixFilePermissions.fromString("rw-------"));
        }
        return FileChannel.open(lockPath, StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
    }

    private static FileLock acquireLock(FileChannel channel, Path lockPath) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) return lock;
            } catch (OverlappingFileLockException ignored) {
                // Same-JVM transaction: retry on the stable sibling inode just like an external process.
            }
            if (System.nanoTime() >= deadline) {
                throw new IllegalArgumentException("Timed out waiting for XG-83 lock: " + lockPath);
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for XG-83 lock: " + lockPath, e);
            }
        }
    }

    private static Manifest readManifest(Path path) {
        try {
            Manifest manifest = JSON.readValue(path.toFile(), Manifest.class);
            if (manifest.version() != 1 || manifest.target() == null || manifest.backup() == null
                    || manifest.originalSha() == null || manifest.replacementSha() == null
                    || manifest.state() == null) {
                throw new IllegalArgumentException("Incomplete XG-83 manifest: " + path);
            }
            return manifest;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read XG-83 manifest " + path + ": "
                    + e.getMessage(), e);
        }
    }

    private static void writeManifestNew(Path path, Manifest manifest) throws IOException {
        if (Files.exists(path)) throw new IllegalArgumentException("Manifest already exists: " + path);
        writeAtomic(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest), null, false);
    }

    private static void writeManifestReplace(Path path, Manifest manifest) throws IOException {
        writeAtomic(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest), null, true);
    }

    private static Path archiveManifest(Path manifestPath) throws IOException {
        Path archive = manifestPath.resolveSibling(manifestPath.getFileName() + ".restored-"
                + Instant.now().toEpochMilli() + ".json");
        Files.move(manifestPath, archive, StandardCopyOption.ATOMIC_MOVE);
        fsyncDirectory(manifestPath.getParent());
        return archive;
    }

    private static Path validateBackupPath(Path target, Path source, Path backupArg) throws IOException {
        Path backup = backupArg.toAbsolutePath().normalize();
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Backup path already exists: " + backup);
        }
        if (backup.equals(target) || backup.equals(source)) {
            throw new IllegalArgumentException("Backup path must differ from target and source: " + backup);
        }
        if (backup.getParent() == null || !Files.isDirectory(backup.getParent())) {
            throw new IllegalArgumentException("Backup parent directory does not exist: " + backup.getParent());
        }
        Path canonicalParent = backup.getParent().toRealPath();
        Path canonicalBackup = canonicalParent.resolve(backup.getFileName()).normalize();
        Path metadataRoot = findDesignerMetadataRoot(target);
        if (canonicalBackup.startsWith(metadataRoot)) {
            throw new IllegalArgumentException("Backup path must be outside the Designer metadata tree "
                    + metadataRoot + ": " + backup);
        }
        return canonicalBackup;
    }

    private static Path findDesignerMetadataRoot(Path target) throws IOException {
        Path targetParent = target.toRealPath().getParent();
        for (Path current = targetParent; current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("Configuration.xml"))) {
                return current.toRealPath();
            }
        }
        // Standalone EPF/ERF and minimal fixtures have no Configuration.xml; their containing
        // directory is the narrowest Designer tree boundary that can be proven from the target.
        return targetParent;
    }

    private static Attributes readAttributes(Path target) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (view == null) return new Attributes(null, Files.getOwner(target), null);
        PosixFileAttributes attrs = view.readAttributes();
        return new Attributes(PosixFilePermissions.toString(attrs.permissions()),
                attrs.owner(), attrs.group());
    }

    private static Attributes attributesFromManifest(Manifest manifest, Path target) throws IOException {
        UserPrincipal owner = Files.getOwner(target);
        GroupPrincipal group = null;
        PosixFileAttributeView view = Files.getFileAttributeView(target, PosixFileAttributeView.class);
        if (view != null) group = view.readAttributes().group();
        try {
            if (manifest.owner() != null) {
                owner = FileSystems.getDefault().getUserPrincipalLookupService()
                        .lookupPrincipalByName(manifest.owner());
            }
        } catch (IOException ignored) { }
        try {
            if (manifest.group() != null) {
                group = FileSystems.getDefault().getUserPrincipalLookupService()
                        .lookupPrincipalByGroupName(manifest.group());
            }
        } catch (IOException ignored) { }
        return new Attributes(manifest.permissions(), owner, group);
    }

    private static String principalName(Object principal) {
        if (principal instanceof UserPrincipal p) return p.getName();
        if (principal instanceof GroupPrincipal p) return p.getName();
        return null;
    }

    private static Path createDurableTemp(Path parent, String prefix, byte[] bytes,
                                          Attributes attrs) throws IOException {
        Path temp = Files.createTempFile(parent, prefix, ".tmp");
        boolean complete = false;
        try {
            writeAndForce(temp, bytes);
            applyAttributes(temp, attrs);
            complete = true;
            return temp;
        } finally {
            if (!complete) deleteQuietly(temp);
        }
    }

    private static void writeAtomicNew(Path path, byte[] bytes, Attributes attrs) throws IOException {
        writeAtomic(path, bytes, attrs, false);
    }

    private static void writeAtomic(Path path, byte[] bytes, Attributes attrs,
                                    boolean replace) throws IOException {
        Path temp = createDurableTemp(path.getParent(), ".xml-gen-xg83-atomic-", bytes, attrs);
        try {
            if (replace) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } else {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE);
            }
            temp = null;
            fsyncDirectory(path.getParent());
        } finally {
            deleteQuietly(temp);
        }
    }

    private static void applyAttributes(Path path, Attributes attrs) throws IOException {
        if (attrs == null) return;
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null && attrs.permissions() != null) {
            // owner/group are best-effort: an unprivileged process cannot chown arbitrary identities.
            try { if (attrs.owner() != null) view.setOwner(attrs.owner()); } catch (IOException ignored) { }
            try { if (attrs.group() != null) view.setGroup(attrs.group()); } catch (IOException ignored) { }
            view.setPermissions(PosixFilePermissions.fromString(attrs.permissions()));
        }
    }

    private static void verifyPermissions(Path path, String expected) throws IOException {
        if (expected == null) return; // Non-POSIX filesystem: no portable mode contract.
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view == null) return;
        String actual = PosixFilePermissions.toString(view.readAttributes().permissions());
        if (!expected.equals(actual)) {
            throw new IOException("POSIX permissions changed: expected " + expected + ", actual " + actual);
        }
    }

    private static void writeAndForce(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    private static void fsyncDirectory(Path directory) {
        if (directory == null) return;
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Directory fsync is unavailable on some non-POSIX providers; file fsync remains enforced.
        }
    }

    private static XmlDocument parseAndValidate(Path file, String role) {
        XmlDocument document;
        try {
            document = new XmlStructureReader().parse(file);
        } catch (XmlStructureReader.XmlParseException e) {
            throw new IllegalArgumentException("Invalid SKD " + role + ": " + e.getMessage(), e);
        }
        List<ValidationIssue> issues = new ArrayList<>(new GenValidator().validate(document, "skd", true));
        issues.addAll(new SkdValidator().validate(document, ValidationLevel.SEMANTIC));
        List<ValidationIssue> errors = issues.stream()
                .filter(issue -> issue.getSeverity() == Severity.ERROR).toList();
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid SKD " + role + ": " + errors.stream()
                    .map(issue -> issue.getCode() + " " + issue.getMessage()).toList());
        }
        if (!SKD_ROOT.equals(document.getRootElement()) || !SKD_NS.equals(document.getRootNamespace())) {
            throw new IllegalArgumentException("Invalid SKD " + role + ": expected " + SKD_ROOT
                    + " root in namespace " + SKD_NS);
        }
        return document;
    }

    private static void requireSameSkdType(XmlDocument target, XmlDocument source) {
        if (!target.getRootElement().equals(source.getRootElement())
                || !Objects.equals(target.getRootNamespace(), source.getRootNamespace())) {
            throw new IllegalArgumentException("Source and target XML root/type differ");
        }
    }

    private static Path existingRegularFile(Path path, String role) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(role + " file not found or is not regular: " + path);
        }
        return path.toAbsolutePath().normalize();
    }

    private static void requireSha(String sha) {
        if (sha == null || !sha.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("--expect-target-sha is required and must be 64 hex characters");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
//++agent TASK-204

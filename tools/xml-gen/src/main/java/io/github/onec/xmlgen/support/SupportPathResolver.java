package io.github.onec.xmlgen.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupportPathResolver {
    private static final Pattern UUID_ATTR = Pattern.compile("(?i)\\buuid\\s*=\\s*\"([0-9a-f-]{36})\"");

    private SupportPathResolver() {
    }

    public static Optional<ConfigRoot> findConfigRoot(Path startPath) {
        if (startPath == null) {
            return Optional.empty();
        }

        Path current = initialDirectory(startPath).toAbsolutePath().normalize();
        for (int i = 0; i < 20 && current != null; i++) {
            Path bin = current.resolve("Ext").resolve("ParentConfigurations.bin");
            Path configXml = current.resolve("Configuration.xml");
            if (Files.exists(bin) || Files.exists(configXml)) {
                return Optional.of(new ConfigRoot(current, bin, isExtension(configXml)));
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    public static Optional<String> resolveObjectUuid(Path targetPath, Path configDir) throws IOException {
        Optional<String> direct = firstUuid(targetPath);
        if (direct.isPresent()) {
            return direct;
        }

        Path current = initialDirectory(targetPath).toAbsolutePath().normalize();
        for (int i = 0; i < 20 && current != null; i++) {
            Path sidecar = current.resolveSibling(current.getFileName() + ".xml");
            Optional<String> sidecarUuid = firstUuid(sidecar);
            if (sidecarUuid.isPresent()) {
                return sidecarUuid;
            }
            if (configDir != null && current.equals(configDir.toAbsolutePath().normalize())) {
                break;
            }
            current = current.getParent();
        }

        if (configDir != null) {
            return firstUuid(configDir.resolve("Configuration.xml"));
        }
        return Optional.empty();
    }

    private static Path initialDirectory(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return path;
            }
        } catch (RuntimeException ignored) {
            // Path validity is checked by callers; fallback below handles non-existing targets.
        }
        Path parent = path.getParent();
        return parent != null ? parent : Path.of(".");
    }

    private static Optional<String> firstUuid(Path file) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher matcher = UUID_ATTR.matcher(text);
        if (matcher.find()) {
            return Optional.of(matcher.group(1).toLowerCase());
        }
        return Optional.empty();
    }

    private static boolean isExtension(Path configXml) {
        if (configXml == null || !Files.isRegularFile(configXml)) {
            return false;
        }
        try {
            return Files.readString(configXml, StandardCharsets.UTF_8)
                    .contains("ConfigurationExtensionPurpose");
        } catch (IOException e) {
            return false;
        }
    }

    public record ConfigRoot(Path directory, Path parentConfigurationsBin, boolean extension) {
    }
}

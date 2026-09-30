package io.github.onec.xmlgen.oracle;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PictureBodyOracle {

    private static final String XCF_EXTRNPROPS_NS = "http://v8.1c.ru/8.3/xcf/extrnprops";

    private final XmlStructureReader reader = new XmlStructureReader();

    Result probe(Path sourceRoot, Path pictureXml, XmlDocument document, Path sandbox) throws Exception {
        Files.createDirectories(sandbox);
        List<String> errors = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();

        validateShape(document, errors);

        XmlNode picture = document.getRoot().child("Picture");
        String abs = picture == null ? null : picture.childText("Abs");
        String loadTransparent = picture == null ? null : picture.childText("LoadTransparent");
        details.put("loadTransparent", loadTransparent);
        if (abs == null || abs.isBlank()) {
            errors.add("Picture/xr:Abs is missing");
            return new Result(false, String.join("; ", errors), details);
        }

        details.put("abs", abs);
        Path payload = resolvePayload(pictureXml, abs);
        details.put("payloadPath", relative(sourceRoot, payload));
        if (!Files.isRegularFile(payload)) {
            errors.add("Payload file not found: " + payload);
            return new Result(false, String.join("; ", errors), details);
        }

        byte[] xmlBytes = Files.readAllBytes(pictureXml);
        byte[] payloadBytes = Files.readAllBytes(payload);
        details.put("xmlSha256", sha256(xmlBytes));
        details.put("payloadSha256", sha256(payloadBytes));
        details.put("payloadSize", payloadBytes.length);
        details.put("payloadFormat", detectFormat(payloadBytes));

        Path copiedXml = sandbox.resolve(pictureXml.getFileName().toString());
        Path copiedPayload = sandbox.resolve("payload").resolve(abs);
        Files.createDirectories(copiedPayload.getParent());
        Files.copy(pictureXml, copiedXml, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(payload, copiedPayload, StandardCopyOption.REPLACE_EXISTING);
        if (!bytesEqual(xmlBytes, Files.readAllBytes(copiedXml))) {
            errors.add("Picture.xml bytes changed during sandbox preservation");
        }
        if (!bytesEqual(payloadBytes, Files.readAllBytes(copiedPayload))) {
            errors.add("Payload bytes changed during sandbox preservation");
        }

        details.putAll(commonPictureAssociation(sourceRoot, pictureXml, errors));
        return new Result(errors.isEmpty(), String.join("; ", errors), details);
    }

    private void validateShape(XmlDocument document, List<String> errors) {
        XmlNode root = document.getRoot();
        if (!"ExtPicture".equals(root.getName())) {
            errors.add("Expected ExtPicture root, got " + root.getName());
        }
        if (!XCF_EXTRNPROPS_NS.equals(root.getNamespace())) {
            errors.add("Expected ExtPicture namespace " + XCF_EXTRNPROPS_NS + ", got " + root.getNamespace());
        }
        String version = root.attr("version");
        if (!"2.20".equals(version)) {
            errors.add("Expected ExtPicture version 2.20, got " + version);
        }
        if (root.child("Picture") == null) {
            errors.add("Picture element is missing");
        }
    }

    private Path resolvePayload(Path pictureXml, String abs) {
        Path xmlDir = pictureXml.toAbsolutePath().normalize().getParent();
        String stem = stripXmlExtension(pictureXml.getFileName().toString());
        Path designerPayload = xmlDir.resolve(stem).resolve(abs).normalize();
        if (Files.exists(designerPayload)) {
            return designerPayload;
        }
        return xmlDir.resolve(abs).normalize();
    }

    private Map<String, Object> commonPictureAssociation(Path sourceRoot, Path pictureXml,
                                                         List<String> errors) throws Exception {
        Map<String, Object> details = new LinkedHashMap<>();
        Path normalized = pictureXml.toAbsolutePath().normalize();
        int commonPicturesIndex = indexOf(normalized, "CommonPictures");
        if (commonPicturesIndex < 0 || normalized.getNameCount() < commonPicturesIndex + 4) {
            details.put("association", "not_common_picture");
            return details;
        }

        Path objectNamePath = normalized.getName(commonPicturesIndex + 1);
        String objectName = objectNamePath.toString();
        Path commonPicturesDir = normalized.getRoot();
        for (int i = 0; i <= commonPicturesIndex; i++) {
            commonPicturesDir = commonPicturesDir == null
                    ? normalized.getName(i)
                    : commonPicturesDir.resolve(normalized.getName(i).toString());
        }
        Path wrapper = commonPicturesDir.resolve(objectName + ".xml").normalize();
        details.put("association", "common_picture");
        details.put("wrapperPath", relative(sourceRoot, wrapper));
        if (!Files.isRegularFile(wrapper)) {
            details.put("wrapperMatchesName", false);
            errors.add("CommonPicture wrapper not found: " + wrapper);
            return details;
        }

        XmlDocument wrapperDocument = reader.parse(wrapper);
        XmlNode commonPicture = wrapperDocument.getRoot().child("CommonPicture");
        String wrapperName = commonPicture == null || commonPicture.child("Properties") == null
                ? null
                : commonPicture.child("Properties").childText("Name");
        details.put("wrapperName", wrapperName);
        boolean matches = objectName.equals(wrapperName);
        details.put("wrapperMatchesName", matches);
        if (!matches) {
            errors.add("CommonPicture wrapper Name '" + wrapperName
                    + "' does not match directory '" + objectName + "'");
        }
        return details;
    }

    private int indexOf(Path path, String name) {
        for (int i = 0; i < path.getNameCount(); i++) {
            if (name.equals(path.getName(i).toString())) {
                return i;
            }
        }
        return -1;
    }

    private String detectFormat(byte[] bytes) {
        if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'})) {
            return "png";
        }
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) {
            return "jpeg";
        }
        if (startsWith(bytes, new byte[]{'G', 'I', 'F', '8'})) {
            return "gif";
        }
        if (startsWith(bytes, new byte[]{'B', 'M'})) {
            return "bmp";
        }
        if (startsWith(bytes, new byte[]{0, 0, 1, 0}) || startsWith(bytes, new byte[]{0, 0, 2, 0})) {
            return "ico";
        }
        if (startsWith(bytes, new byte[]{'P', 'K', 3, 4})
                || startsWith(bytes, new byte[]{'P', 'K', 5, 6})
                || startsWith(bytes, new byte[]{'P', 'K', 7, 8})) {
            return "zip";
        }
        String prefix = new String(bytes, 0, Math.min(bytes.length, 256), StandardCharsets.UTF_8).stripLeading();
        if (prefix.startsWith("<svg") || prefix.startsWith("<?xml") && prefix.contains("<svg")) {
            return "svg";
        }
        return "unknown";
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean bytesEqual(byte[] left, byte[] right) {
        return MessageDigest.isEqual(left, right);
    }

    private String relative(Path root, Path file) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (Files.isDirectory(normalizedRoot) && normalizedFile.startsWith(normalizedRoot)) {
            return normalizedRoot.relativize(normalizedFile).toString().replace('\\', '/');
        }
        return normalizedFile.toString();
    }

    private String stripXmlExtension(String name) {
        return name.endsWith(".xml") ? name.substring(0, name.length() - 4) : name;
    }

    record Result(boolean passed, String error, Map<String, Object> details) {}
}

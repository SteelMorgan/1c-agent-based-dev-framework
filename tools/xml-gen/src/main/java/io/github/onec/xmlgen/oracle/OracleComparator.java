package io.github.onec.xmlgen.oracle;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class OracleComparator {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final XmlStructureReader reader = new XmlStructureReader();

    public CmpResult compareBytes(String mode, String objectId, Path expected, Path actual) {
        try {
            byte[] left = Files.readAllBytes(expected);
            byte[] right = Files.readAllBytes(actual);
            if (java.util.Arrays.equals(left, right)) {
                return CmpResult.pass(mode, objectId, List.of());
            }
            List<DiffEntry> diffs = new ArrayList<>();
            diffs.add(firstByteDiff(left, right));
            try {
                diffs.addAll(compareStructureDiffs(mode, expected, actual, IgnoreAllowlist.empty("mxl"), 20));
            } catch (Exception ignored) {
                // Byte mismatch is already a valid oracle result; XML diagnostics are best-effort.
            }
            return CmpResult.fail(mode, objectId, diffs, FailureClass.C_OR_EXEC_BUG);
        } catch (Exception e) {
            return CmpResult.error(mode, objectId, "byte-compare", e);
        }
    }

    public CmpResult compareStructure(String mode, String objectId, Path expected, Path actual,
                                      IgnoreAllowlist allowlist) {
        try {
            List<DiffEntry> diffs = compareStructureDiffs(mode, expected, actual, allowlist, 50);
            if (diffs.isEmpty()) {
                return CmpResult.pass(mode, objectId,
                        List.of(new NormalizedDimension("/", "structural-xml", "attribute order and XML trivia")));
            }
            return CmpResult.fail(mode, objectId, diffs, FailureClass.C_OR_EXEC_BUG);
        } catch (Exception e) {
            return CmpResult.error(mode, objectId, "structural-compare", e);
        }
    }

    private DiffEntry firstByteDiff(byte[] expected, byte[] actual) {
        int max = Math.min(expected.length, actual.length);
        for (int i = 0; i < max; i++) {
            if (expected[i] != actual[i]) {
                return new DiffEntry("/bytes/" + i, "byte",
                        String.format("0x%02X", expected[i] & 0xff),
                        String.format("0x%02X", actual[i] & 0xff),
                        "first byte mismatch");
            }
        }
        return new DiffEntry("/bytes/length", "length", String.valueOf(expected.length),
                String.valueOf(actual.length), "byte length mismatch");
    }

    private List<DiffEntry> compareStructureDiffs(String mode, Path expected, Path actual,
                                                  IgnoreAllowlist allowlist, int limit)
            throws XmlStructureReader.XmlParseException {
        XmlDocument left = reader.parse(expected);
        XmlDocument right = reader.parse(actual);
        List<DiffEntry> diffs = new ArrayList<>();
        UuidBijection uuids = new UuidBijection();
        compareNode(mode, "/" + left.getRoot().getName(), left.getRoot(), right.getRoot(), allowlist, uuids, diffs, limit);
        return diffs;
    }

    private void compareNode(String mode, String path, XmlNode left, XmlNode right, IgnoreAllowlist allowlist,
                             UuidBijection uuids, List<DiffEntry> diffs, int limit) {
        if (diffs.size() >= limit || allowlist.ignores(mode, path)) {
            return;
        }
        if (right == null) {
            add(diffs, path, "node-missing", left.getName(), "", "actual node is missing", limit);
            return;
        }
        if (!left.getName().equals(right.getName())) {
            add(diffs, path, "name", left.getName(), right.getName(), "element name mismatch", limit);
            return;
        }
        compareAttributes(mode, path, left, right, allowlist, uuids, diffs, limit);
        compareText(mode, path, left.getText(), right.getText(), allowlist, uuids, diffs, limit);

        List<XmlNode> lc = left.getChildren();
        List<XmlNode> rc = right.getChildren();
        if (lc.size() != rc.size()) {
            add(diffs, path + "/*", "child-count", String.valueOf(lc.size()), String.valueOf(rc.size()),
                    "child element count mismatch", limit);
        }
        int count = Math.min(lc.size(), rc.size());
        for (int i = 0; i < count && diffs.size() < limit; i++) {
            XmlNode child = lc.get(i);
            compareNode(mode, path + "/" + child.getName() + "[" + (i + 1) + "]",
                    child, rc.get(i), allowlist, uuids, diffs, limit);
        }
    }

    private void compareAttributes(String mode, String path, XmlNode left, XmlNode right, IgnoreAllowlist allowlist,
                                   UuidBijection uuids, List<DiffEntry> diffs, int limit) {
        Map<String, String> la = sorted(left.getAttributes());
        Map<String, String> ra = sorted(right.getAttributes());
        for (String key : la.keySet()) {
            String attrPath = path + "/@" + key;
            if (allowlist.ignores(mode, attrPath)) {
                continue;
            }
            if (!ra.containsKey(key)) {
                add(diffs, attrPath, "attr-missing", la.get(key), "", "actual attribute is missing", limit);
                continue;
            }
            compareValue(attrPath, la.get(key), ra.get(key), uuids, diffs, limit);
        }
        for (String key : ra.keySet()) {
            String attrPath = path + "/@" + key;
            if (!la.containsKey(key) && !allowlist.ignores(mode, attrPath)) {
                add(diffs, attrPath, "attr-extra", "", ra.get(key), "actual attribute is extra", limit);
            }
        }
    }

    private void compareText(String mode, String path, String left, String right, IgnoreAllowlist allowlist,
                             UuidBijection uuids, List<DiffEntry> diffs, int limit) {
        if (allowlist.ignores(mode, path + "/text()")) {
            return;
        }
        compareValue(path + "/text()", left == null ? "" : left, right == null ? "" : right, uuids, diffs, limit);
    }

    private void compareValue(String path, String expected, String actual, UuidBijection uuids,
                              List<DiffEntry> diffs, int limit) {
        if (expected.equals(actual)) {
            return;
        }
        if (UUID_PATTERN.matcher(expected).matches() && UUID_PATTERN.matcher(actual).matches()) {
            String error = uuids.put(expected.toLowerCase(), actual.toLowerCase());
            if (error != null) {
                add(diffs, path, "uuid-bijection", expected, actual, error, limit);
            }
            return;
        }
        add(diffs, path, "value", expected, actual, "text/value mismatch", limit);
    }

    private Map<String, String> sorted(Map<String, String> source) {
        Map<String, String> sorted = new LinkedHashMap<>();
        source.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(e -> sorted.put(e.getKey(), e.getValue()));
        return sorted;
    }

    private void add(List<DiffEntry> diffs, String path, String kind, String expected, String actual,
                     String message, int limit) {
        if (diffs.size() < limit) {
            diffs.add(new DiffEntry(path, kind, expected, actual, message));
        }
    }

    private static final class UuidBijection {
        private final Map<String, String> forward = new HashMap<>();
        private final Map<String, String> reverse = new HashMap<>();

        String put(String expected, String actual) {
            String previousActual = forward.putIfAbsent(expected, actual);
            if (previousActual != null && !previousActual.equals(actual)) {
                return "expected UUID maps to two actual UUIDs: " + previousActual + " and " + actual;
            }
            String previousExpected = reverse.putIfAbsent(actual, expected);
            if (previousExpected != null && !previousExpected.equals(expected)) {
                return "actual UUID maps from two expected UUIDs: " + previousExpected + " and " + expected;
            }
            return null;
        }
    }
}

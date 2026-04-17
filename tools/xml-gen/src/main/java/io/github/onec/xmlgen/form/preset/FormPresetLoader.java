package io.github.onec.xmlgen.form.preset;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Загружает пресет формы: hardcoded defaults → built-in JSON из classpath → project-level JSON.
 * Все уровни склеиваются deep-merge'ем, затем резолвятся {@code basedOn}-ссылки.
 */
public final class FormPresetLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public FormPreset load(String presetName, Path outputPath, Path explicitPresetDir) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();

        // 1) hardcoded defaults
        for (Map.Entry<String, Map<String, Object>> e : defaults().entrySet()) {
            merged.put(e.getKey(), e.getValue());
        }

        // 2) built-in preset from resources (if exists)
        Map<String, Object> builtIn = loadBuiltInResource(presetName);
        if (builtIn != null) {
            applyTopLevelMerge(merged, builtIn);
        }

        // 3) project-level preset, scan up from outputPath
        Path projectFile = findProjectPreset(presetName, outputPath, explicitPresetDir);
        if (projectFile != null) {
            try {
                String json = new String(Files.readAllBytes(projectFile), StandardCharsets.UTF_8);
                if (json.startsWith("\uFEFF")) json = json.substring(1);
                @SuppressWarnings("unchecked")
                Map<String, Object> proj = MAPPER.readValue(json, Map.class);
                applyTopLevelMerge(merged, proj);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to load project preset: " + projectFile, ex);
            }
        }

        // 4) resolve basedOn
        FormPresetMerger.resolveBasedOn(merged);

        String name = (builtIn != null && builtIn.get("name") instanceof String) ? (String) builtIn.get("name") : presetName;
        String desc = (builtIn != null && builtIn.get("description") instanceof String) ? (String) builtIn.get("description") : null;
        return new FormPreset(name, desc, merged);
    }

    @SuppressWarnings("unchecked")
    private void applyTopLevelMerge(Map<String, Map<String, Object>> target, Map<String, Object> src) {
        for (Map.Entry<String, Object> e : src.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (!(v instanceof Map)) continue; // skip scalar meta like name/description
            if ("name".equals(k) || "description".equals(k)) continue;
            Map<String, Object> sectionOverlay = (Map<String, Object>) v;
            Map<String, Object> baseSection = target.get(k);
            target.put(k, FormPresetMerger.merge(baseSection, sectionOverlay));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadBuiltInResource(String presetName) {
        String path = "/presets/form/" + presetName + ".json";
        try (InputStream in = FormPresetLoader.class.getResourceAsStream(path)) {
            if (in == null) return null;
            return (Map<String, Object>) MAPPER.readValue(in, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load built-in preset resource: " + path, e);
        }
    }

    private Path findProjectPreset(String presetName, Path outputPath, Path explicitPresetDir) {
        Path start;
        if (explicitPresetDir != null) {
            start = explicitPresetDir.toAbsolutePath();
        } else if (outputPath != null) {
            start = outputPath.toAbsolutePath().getParent();
        } else {
            return null;
        }

        Path cur = start;
        while (cur != null) {
            Path candidate = cur.resolve("presets").resolve("skills").resolve("form").resolve(presetName + ".json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Hardcoded defaults. Портировано 1:1 из Shirokov form-compile.py.
    // ---------------------------------------------------------------------

    private static Map<String, Map<String, Object>> defaults() {
        Map<String, Map<String, Object>> m = new LinkedHashMap<>();

        m.put("document.item", map(
                "header", map("position", "insidePage", "layout", "2col", "distribute", "even", "dateTitle", "от"),
                "footer", map("fields", list("Комментарий"), "position", "insidePage"),
                "tabularSections", map("container", "pages", "exclude", list("ДополнительныеРеквизиты"), "lineNumber", true),
                "additional", map("position", "page", "layout", "2col", "bspGroup", true),
                "fieldDefaults", map("ref", map("choiceButton", true), "boolean", map("element", "check")),
                "commandBar", "auto",
                "properties", map("autoTitle", false)
        ));

        m.put("document.list", map(
                "columns", "all", "columnType", "labelField", "hiddenRef", true,
                "tableCommandBar", "none", "commandBar", "auto",
                "properties", map()
        ));

        m.put("document.choice", map(
                "basedOn", "document.list",
                "properties", map("windowOpeningMode", "LockOwnerWindow")
        ));

        m.put("catalog.item", map(
                "header", map("layout", "1col", "distribute", "left"),
                "codeDescription", map("layout", "horizontal", "order", "descriptionFirst"),
                "parent", map("title", "Входит в группу", "position", "afterCodeDescription"),
                "owner", map("readOnly", true, "position", "first"),
                "tabularSections", map("container", "inline", "exclude", list("ДополнительныеРеквизиты", "Представления"), "lineNumber", true),
                "footer", map("fields", list(), "position", "none"),
                "additional", map("position", "none", "bspGroup", true),
                "fieldDefaults", map("ref", map("choiceButton", true), "boolean", map("element", "check")),
                "commandBar", "auto",
                "properties", map()
        ));

        m.put("catalog.folder", map(
                "parent", map("title", "Входит в группу"),
                "properties", map("windowOpeningMode", "LockOwnerWindow")
        ));

        m.put("catalog.list", map(
                "columns", "all", "columnType", "labelField", "hiddenRef", true,
                "tableCommandBar", "none", "commandBar", "auto",
                "properties", map()
        ));

        m.put("catalog.choice", map(
                "basedOn", "catalog.list", "choiceMode", true,
                "properties", map("windowOpeningMode", "LockOwnerWindow")
        ));

        m.put("informationRegister.record", map(
                "fieldDefaults", map("ref", map("choiceButton", true), "boolean", map("element", "check")),
                "properties", map("windowOpeningMode", "LockOwnerWindow")
        ));

        m.put("informationRegister.list", map(
                "columns", "all", "columnType", "labelField",
                "tableCommandBar", "none", "commandBar", "auto",
                "properties", map()
        ));

        m.put("accumulationRegister.list", map(
                "columns", "all", "columnType", "labelField",
                "tableCommandBar", "none", "commandBar", "auto",
                "properties", map()
        ));

        m.put("chartOfCharacteristicTypes.item", map("basedOn", "catalog.item"));
        m.put("chartOfCharacteristicTypes.folder", map("basedOn", "catalog.folder"));
        m.put("chartOfCharacteristicTypes.list", map("basedOn", "catalog.list"));
        m.put("chartOfCharacteristicTypes.choice", map("basedOn", "catalog.choice"));

        m.put("exchangePlan.item", map("basedOn", "catalog.item"));
        m.put("exchangePlan.list", map("basedOn", "catalog.list"));
        m.put("exchangePlan.choice", map("basedOn", "catalog.choice"));

        m.put("chartOfAccounts.item", map(
                "parent", map("title", "Подчинен счету"),
                "fieldDefaults", map("ref", map("choiceButton", true), "boolean", map("element", "check")),
                "properties", map()
        ));
        m.put("chartOfAccounts.folder", map(
                "parent", map("title", "Подчинен счету"),
                "properties", map("windowOpeningMode", "LockOwnerWindow")
        ));
        m.put("chartOfAccounts.list", map("basedOn", "catalog.list"));
        m.put("chartOfAccounts.choice", map("basedOn", "catalog.choice"));

        return m;
    }

    // ---------- helpers ----------

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    private static List<Object> list(Object... items) {
        List<Object> r = new ArrayList<>();
        for (Object o : items) r.add(o);
        return r;
    }
}

package io.github.onec.xmlgen.form.preset;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormPresetMergerTest {

    @Test
    void mergeScalarOverlayWins() {
        Map<String, Object> base = mapOf("k", "a");
        Map<String, Object> overlay = mapOf("k", "b");
        Map<String, Object> r = FormPresetMerger.merge(base, overlay);
        assertThat(r).containsEntry("k", "b");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeNestedMapsRecursively() {
        Map<String, Object> base = mapOf("header", mapOf("layout", "2col", "dateTitle", "от"));
        Map<String, Object> overlay = mapOf("header", mapOf("layout", "1col"));
        Map<String, Object> r = FormPresetMerger.merge(base, overlay);
        Map<String, Object> header = (Map<String, Object>) r.get("header");
        assertThat(header).containsEntry("layout", "1col");
        assertThat(header).containsEntry("dateTitle", "от");
    }

    @Test
    @SuppressWarnings("unchecked")
    void mergeListsOverwrittenNotMerged() {
        Map<String, Object> base = mapOf("footer", mapOf("fields", Arrays.asList("a", "b")));
        Map<String, Object> overlay = mapOf("footer", mapOf("fields", Arrays.asList("c")));
        Map<String, Object> r = FormPresetMerger.merge(base, overlay);
        Map<String, Object> footer = (Map<String, Object>) r.get("footer");
        List<Object> fields = (List<Object>) footer.get("fields");
        assertThat(fields).containsExactly("c");
    }

    @Test
    void resolveBasedOnInheritsKeys() {
        Map<String, Map<String, Object>> sections = new LinkedHashMap<>();
        sections.put("catalog.list", mapOf("columns", "all", "hiddenRef", true));
        sections.put("catalog.choice", mapOf("basedOn", "catalog.list", "choiceMode", true));

        FormPresetMerger.resolveBasedOn(sections);

        Map<String, Object> choice = sections.get("catalog.choice");
        assertThat(choice).containsEntry("columns", "all");
        assertThat(choice).containsEntry("hiddenRef", true);
        assertThat(choice).containsEntry("choiceMode", true);
        assertThat(choice).doesNotContainKey("basedOn");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }
}

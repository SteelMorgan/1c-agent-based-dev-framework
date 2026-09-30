package io.github.onec.xmlgen.editor.skd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PatchQueryEngineTest {

    @Test
    void testReplaceAllOccurrences() {
        PatchQueryEngine.Result r = PatchQueryEngine.replace(
                "SELECT a, a, a FROM t", "a", "b", PatchQueryEngine.OnceMode.OFF);
        assertThat(r.text).isEqualTo("SELECT b, b, b FROM t");
        assertThat(r.replacements).isEqualTo(3);
    }

    @Test
    void testReplaceOnceSingleMatch() {
        PatchQueryEngine.Result r = PatchQueryEngine.replace(
                "SELECT a FROM t", "a ", "z ", PatchQueryEngine.OnceMode.ON);
        assertThat(r.text).isEqualTo("SELECT z FROM t");
        assertThat(r.replacements).isEqualTo(1);
    }

    @Test
    void testReplaceOnceFailsOnDoubleMatch() {
        SkdParseException ex = assertThrows(SkdParseException.class,
                () -> PatchQueryEngine.replace("SELECT a, a", "a", "b", PatchQueryEngine.OnceMode.ON));
        assertThat(ex.getMessage()).contains("ambiguous").contains("2");
    }

    @Test
    void testReplaceOnceFailsOnZero() {
        SkdParseException ex = assertThrows(SkdParseException.class,
                () -> PatchQueryEngine.replace("SELECT b", "a", "z", PatchQueryEngine.OnceMode.ON));
        assertThat(ex.getMessage()).contains("not found");
    }

    @Test
    void testReplaceWithoutOnceFailsOnZero() {
        SkdParseException ex = assertThrows(SkdParseException.class,
                () -> PatchQueryEngine.replace("SELECT b", "a", "z", PatchQueryEngine.OnceMode.OFF));
        assertThat(ex.getMessage()).contains("no matches");
    }

    @Test
    void testMultilineReplace() {
        String text = "ГДЕ\n    Дата >= &НП";
        PatchQueryEngine.Result r = PatchQueryEngine.replace(
                text, "ГДЕ\n    Дата", "ГДЕ\n    Период", PatchQueryEngine.OnceMode.ON);
        assertThat(r.text).isEqualTo("ГДЕ\n    Период >= &НП");
    }
}

package io.github.onec.xmlgen.info;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.MxlDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import io.github.onec.xmlgen.writer.MxlWriter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-171 — golden round-trip тесты MXL на РЕАЛЬНЫХ макетах _Демо платформы 1С.
 *
 * <p>Назначение: поймать silent-loss, который 6 синтетических тестов нашего диалекта
 * НЕ ловили (они round-trip-или наш собственный формат сами с собой). Здесь проверяем,
 * что декомпиляция → компиляция реального макета НЕ теряет:
 *   - объединения ячеек (document-level &lt;merge&gt;);
 *   - бордюры (скалярные индексы &lt;leftBorder&gt; и т.д. через палитру &lt;line&gt;);
 *   - ширины колонок (&lt;columnsItem&gt;).
 *
 * <p>До TASK-171 на этих макетах терялось 70-95% форматирования (см.
 * tasks/171-xmlgen-tool-defects/.context/crosscheck/05-mxl.md §6).
 *
 * <p>Тесты используют макеты из проекта GBIG PAM. Если каталог проекта недоступен
 * (например, фреймворк собирают изолированно) — тест помечается как skipped через
 * {@link Assumptions}, чтобы не ломать сборку фреймворка вне проекта.
 */
class MxlGoldenRoundTripTest {

    private static final Path PROJECT_XML = Path.of(
            "/workspaces/work/repos/1C Projects/GBIG PAM/src/xml");

    private final XmlStructureReader reader = new XmlStructureReader();
    private final MxlDecompiler decompiler = new MxlDecompiler();
    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    // --- Реальные макеты с известными счётчиками merge/border/width ---

    @Test
    void kvitanciya_preservesMergesBordersWidths() throws Exception {
        roundTripPreserves(
                "Documents/_ДемоСчетНаОплатуПокупателю/Templates/ПФ_MXL_Квитанция/Ext/Template.xml",
                /*minMergeRatio*/ 0.9, /*expectBorders*/ true, /*expectWidths*/ true);
    }

    @Test
    void schetNaOplatu_preservesMergesBordersWidths() throws Exception {
        roundTripPreserves(
                "Documents/_ДемоСчетНаОплатуПокупателю/Templates/ПФ_MXL_СчетНаОплату/Ext/Template.xml",
                0.9, true, true);
    }

    @Test
    void realizaciyaTovarov_preservesMergesBordersWidths() throws Exception {
        roundTripPreserves(
                "Documents/_ДемоРеализацияТоваров/Templates/ПФ_MXL_РеализацияТоваров/Ext/Template.xml",
                0.85, true, true);
    }

    @Test
    void opisatel_preservesBordersWidths() throws Exception {
        // У этого макета нет merge, но есть бордюры и ширины — историческая 100% потеря.
        roundTripPreserves(
                "DataProcessors/ИнформацияПриЗапуске/Templates/_ДемоОписатель/Ext/Template.xml",
                0.0, true, true);
    }

    /**
     * Общая проверка: decompile(original) → JSON должен содержать объединения/бордюры/ширины;
     * compile(JSON) → XML должен сохранить долю document-level merge не ниже minMergeRatio
     * и сохранить бордюры/ширины (если ожидаются). Повторная декомпиляция должна быть
     * структурно стабильной (RT == RT2 по числу merge).
     */
    private void roundTripPreserves(String relPath, double minMergeRatio,
                                    boolean expectBorders, boolean expectWidths) throws Exception {
        Path orig = PROJECT_XML.resolve(relPath);
        Assumptions.assumeTrue(Files.exists(orig),
                "Реальный макет недоступен (сборка вне проекта GBIG PAM): " + orig);

        int origMerge = count(Files.readString(orig, StandardCharsets.UTF_8), "<merge>");

        // 1. decompile original
        XmlDocument doc = reader.parse(orig);
        Path json = tempDir.resolve("dsl.json");
        decompiler.decompile(doc, json);
        String jsonText = Files.readString(json, StandardCharsets.UTF_8);

        // JSON должен НЕ быть «голым»: если в оригинале были merge — должны появиться span/rowspan.
        if (origMerge > 0) {
            int spans = count(jsonText, "\"span\"") + count(jsonText, "\"rowspan\"");
            assertThat(spans)
                    .as("JSON должен содержать объединения (span/rowspan) для " + relPath
                            + " — было " + origMerge + " merge в оригинале")
                    .isGreaterThan(0);
        }
        if (expectBorders) {
            assertThat(jsonText)
                    .as("JSON должен содержать бордюры (styles.border) для " + relPath)
                    .contains("\"border\"");
        }
        if (expectWidths) {
            assertThat(jsonText)
                    .as("JSON должен содержать columnWidths для " + relPath)
                    .contains("columnWidths");
        }

        // 2. compile JSON back to XML
        MxlDsl dsl = mapper.readValue(jsonText, MxlDsl.class);
        Path rtXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, rtXml);
        String rtText = Files.readString(rtXml, StandardCharsets.UTF_8);

        // Document-level merge сохранены не ниже заданной доли.
        if (origMerge > 0) {
            int rtMerge = count(rtText, "<merge>");
            assertThat((double) rtMerge / origMerge)
                    .as("Доля сохранённых merge для " + relPath
                            + " (orig=" + origMerge + ", rt=" + rtMerge + ")")
                    .isGreaterThanOrEqualTo(minMergeRatio);
        }
        if (expectBorders) {
            int borders = count(rtText, "<leftBorder>") + count(rtText, "<topBorder>")
                    + count(rtText, "<rightBorder>") + count(rtText, "<bottomBorder>");
            assertThat(borders)
                    .as("RT XML должен содержать скалярные border-индексы для " + relPath)
                    .isGreaterThan(0);
            assertThat(rtText)
                    .as("RT XML должен содержать палитру линий для " + relPath)
                    .contains("<line ");
        }
        if (expectWidths) {
            assertThat(rtText)
                    .as("RT XML должен содержать ширины колонок для " + relPath)
                    .contains("<width>");
        }

        // 3. Повторная декомпиляция RT → структурно стабильно (число merge не меняется).
        XmlDocument doc2 = reader.parse(rtXml);
        Path json2 = tempDir.resolve("dsl2.json");
        decompiler.decompile(doc2, json2);
        String json2Text = Files.readString(json2, StandardCharsets.UTF_8);

        int spans1 = count(jsonText, "\"span\"") + count(jsonText, "\"rowspan\"");
        int spans2 = count(json2Text, "\"span\"") + count(json2Text, "\"rowspan\"");
        assertThat(spans2)
                .as("Повторная декомпиляция должна быть стабильной по числу объединений для " + relPath)
                .isEqualTo(spans1);
    }

    private static int count(String haystack, String needle) {
        int n = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}

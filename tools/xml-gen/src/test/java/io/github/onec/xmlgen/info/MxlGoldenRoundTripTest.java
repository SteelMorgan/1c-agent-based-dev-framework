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

    // ─── TASK-171 (остаточный долг W2): цвета / verticalUnmerge / drawings / Nx ───

    /**
     * R5 — цвета: Описатель содержит textColor (#000080, #666699), backColor (#BBEEC7)
     * и borderColor (style:FormTextColor). До TASK-171 они терялись на 100%. Проверяем,
     * что каждое distinct-значение цвета переживает decompile→compile (через JSON и RT XML).
     */
    @Test
    void opisatel_preservesColorValues() throws Exception {
        Path orig = PROJECT_XML.resolve(
                "DataProcessors/ИнформацияПриЗапуске/Templates/_ДемоОписатель/Ext/Template.xml");
        Assumptions.assumeTrue(Files.exists(orig), "Реальный макет недоступен: " + orig);

        XmlDocument doc = reader.parse(orig);
        Path json = tempDir.resolve("dsl.json");
        decompiler.decompile(doc, json);
        String jsonText = Files.readString(json, StandardCharsets.UTF_8);

        // Все distinct color-значения должны быть в JSON.
        assertThat(jsonText).contains("#000080");
        assertThat(jsonText).contains("#666699");
        assertThat(jsonText).contains("#BBEEC7");
        assertThat(jsonText).contains("style:FormTextColor");
        assertThat(jsonText).contains("\"textColor\"");
        assertThat(jsonText).contains("\"backColor\"");
        assertThat(jsonText).contains("\"borderColor\"");

        // compile обратно — цвета должны попасть в XML дословно.
        MxlDsl dsl = mapper.readValue(jsonText, MxlDsl.class);
        Path rtXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, rtXml);
        String rt = Files.readString(rtXml, StandardCharsets.UTF_8);
        assertThat(rt).contains("<textColor>#000080</textColor>");
        assertThat(rt).contains("<textColor>#666699</textColor>");
        assertThat(rt).contains("<backColor>#BBEEC7</backColor>");
        assertThat(rt).contains("<borderColor>style:FormTextColor</borderColor>");
    }

    /**
     * Квитанция: verticalUnmerge (38 шт), document-wide column merge (&lt;r&gt;-1, 2 шт),
     * drawings + pictures — до TASK-171 все терялись на 100%. Проверяем точное сохранение
     * count'ов orig→RT и 100% merge (раньше было 96% из-за r=-1).
     */
    @Test
    void kvitanciya_preservesUnmergeColumnMergeDrawings() throws Exception {
        Path orig = PROJECT_XML.resolve(
                "Documents/_ДемоСчетНаОплатуПокупателю/Templates/ПФ_MXL_Квитанция/Ext/Template.xml");
        Assumptions.assumeTrue(Files.exists(orig), "Реальный макет недоступен: " + orig);

        String ot = Files.readString(orig, StandardCharsets.UTF_8);
        int origUnmerge = count(ot, "<verticalUnmerge>");
        int origColMerge = count(ot, "<r>-1</r>");
        int origDrawing = count(ot, "<drawing>");
        int origPicture = count(ot, "<picture>");
        int origMerge = count(ot, "<merge>");

        XmlDocument doc = reader.parse(orig);
        Path json = tempDir.resolve("dsl.json");
        decompiler.decompile(doc, json);
        String jsonText = Files.readString(json, StandardCharsets.UTF_8);
        assertThat(jsonText).contains("\"verticalUnmerges\"");
        assertThat(jsonText).contains("\"columnMerges\"");
        assertThat(jsonText).contains("\"drawings\"");
        assertThat(jsonText).contains("\"pictures\"");

        MxlDsl dsl = mapper.readValue(jsonText, MxlDsl.class);
        Path rtXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, rtXml);
        String rt = Files.readString(rtXml, StandardCharsets.UTF_8);

        assertThat(count(rt, "<verticalUnmerge>")).as("verticalUnmerge orig→RT").isEqualTo(origUnmerge);
        assertThat(count(rt, "<r>-1</r>")).as("column merge r=-1 orig→RT").isEqualTo(origColMerge);
        assertThat(count(rt, "<drawing>")).as("drawing orig→RT").isEqualTo(origDrawing);
        assertThat(count(rt, "<picture>")).as("picture orig→RT").isEqualTo(origPicture);
        // Теперь merge сохраняется на 100% (r=-1 больше не теряется).
        assertThat(count(rt, "<merge>")).as("merge orig→RT (100%)").isEqualTo(origMerge);
    }

    /**
     * R8 — "Nx" пропорции: синтетический DSL с page+"Nx" → авто-расчёт ширин.
     * Не зависит от проекта (не требует реального макета).
     */
    @Test
    void nxProportions_autoComputeWidths() throws Exception {
        String json = "{\"columns\":3,\"page\":\"600\",\"columnWidths\":{\"1\":\"2x\"},"
                + "\"areas\":[{\"name\":\"X\",\"rows\":[{\"cells\":[{\"col\":1,\"text\":\"A\"}]}]}]}";
        MxlDsl dsl = mapper.readValue(json, MxlDsl.class);
        Path rtXml = tempDir.resolve("Template.xml");
        new MxlWriter(OutputFormat.DESIGNER).create(dsl, rtXml);
        String rt = Files.readString(rtXml, StandardCharsets.UTF_8);
        // units=2+1+1=4 → defaultWidth=150; col1=2x=300.
        assertThat(rt).contains("<width>150</width>");
        assertThat(rt).contains("<width>300</width>");
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

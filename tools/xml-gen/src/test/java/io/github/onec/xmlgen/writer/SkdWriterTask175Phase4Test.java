package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 Phase 4 (Tester): edge-кейсы W-06 (XG-40) и MAY-регрессии R-M.2,
 * НЕ покрытые тестами Phase 3b/3d ({@code SkdWriterTask175Test}, {@code SkdWriterTest}).
 *
 * <p>Кейс 1 — {@code hidden:false}: upstream skd-compile.py @ 32e06cbc проверяет
 * строго {@code parsed.get('hidden') is True} — явный {@code hidden:false}
 * НЕ активирует деривацию (useRestriction остаётся дефолтным false,
 * availableAsField не эмитится).</p>
 *
 * <p>Кейс 2 — R-M.2 (backlog.md §5): companions {@code @autoDates}
 * (ДатаНачала/ДатаОкончания) жёстко несут {@code useRestriction=true} +
 * {@code availableAsField=false} (SkdWriter.writeDerivedDateParameter:612-614,
 * канон skd-dsl-spec.md §6 + _ДемоФайлы). W-06 менял emit-путь обычных
 * параметров — регресс-тест страхует, что companions фиксом не задеты.</p>
 */
class SkdWriterTask175Phase4Test {

    @TempDir
    Path tempDir;

    /** Компиляция SKD JSON → текст Template.xml (паттерн SkdWriterTask175Test.compile). */
    private String compile(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        Path outputXml = tempDir.resolve("Template_" + System.nanoTime() + ".xml");
        new SkdWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return Files.readString(outputXml);
    }

    private static String paramJson(String extraFragment) {
        return """
                {
                  "dataSets": [{ "type": "query", "name": "НаборДанных1", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "parameters": [
                    { "name": "Период", "type": "StandardPeriod"%s }
                  ]
                }
                """.formatted(extraFragment);
    }

    /**
     * Edge-кейс W-06: явный {@code hidden:false} без {@code useRestriction} —
     * деривация hidden НЕ активируется (upstream: {@code is True} строго),
     * эмитится дефолт {@code useRestriction=false}; {@code availableAsField}
     * не эмитится вовсе (нет ни hidden-деривации, ни явного false).
     */
    @Test
    void w06_hiddenFalse_useRestrictionStaysDefaultFalse() throws Exception {
        String content = compile(paramJson(", \"hidden\": false"));

        assertThat(content)
                .as("hidden:false не активирует деривацию hidden (upstream skd-compile.py "
                        + "@ 32e06cbc: «if parsed.get('hidden') is True» — строгая проверка); "
                        + "дефолт useRestriction=false сохраняется")
                .contains("<useRestriction>false</useRestriction>");
        assertThat(content).doesNotContain("<useRestriction>true</useRestriction>");
        assertThat(content)
                .as("hidden:false не должен эмитить availableAsField — "
                        + "деривация availableAsField=false привязана только к hidden:true")
                .doesNotContain("<availableAsField>");
    }

    /** Блок производного параметра по имени: от {@code <name>X</name>} до закрытия parameter. */
    private static String derivedParamBlock(String content, String name) {
        int nameIdx = content.indexOf("<name>" + name + "</name>");
        assertThat(nameIdx).as("производный параметр %s должен присутствовать", name).isGreaterThan(0);
        int end = content.indexOf("</parameter>", nameIdx);
        assertThat(end).isGreaterThan(nameIdx);
        return content.substring(nameIdx, end);
    }

    /**
     * R-M.2 регрессия (backlog.md §5): companions {@code @autoDates} —
     * ДатаНачала/ДатаОкончания обязаны нести жёсткие {@code useRestriction=true},
     * {@code availableAsField=false} и вычисляться через {@code expression}
     * (а не несуществующий dataPath). W-06 (XG-40) менял эмиссию обычных
     * параметров рядом — пиннингуем, что derived-путь не задет.
     */
    @Test
    void rm2_autoDatesCompanions_keepHardUseRestrictionAndAvailableAsField() throws Exception {
        String content = compile(paramJson(", \"autoDates\": true"));

        String start = derivedParamBlock(content, "ДатаНачала");
        assertThat(start)
                .as("companion ДатаНачала: useRestriction жёстко true (канон skd-dsl-spec.md §6)")
                .contains("<useRestriction>true</useRestriction>")
                .contains("<availableAsField>false</availableAsField>")
                .contains("<expression>&amp;Период.ДатаНачала</expression>");

        String end = derivedParamBlock(content, "ДатаОкончания");
        assertThat(end)
                .as("companion ДатаОкончания: useRestriction жёстко true (канон skd-dsl-spec.md §6)")
                .contains("<useRestriction>true</useRestriction>")
                .contains("<availableAsField>false</availableAsField>")
                .contains("<expression>&amp;Период.ДатаОкончания</expression>");

        // Главный параметр Период при этом получает дефолт W-06 (false) — не companions.
        String mainBlock = content.substring(
                content.indexOf("<name>Период</name>"),
                content.indexOf("</parameter>", content.indexOf("<name>Период</name>")));
        assertThat(mainBlock).contains("<useRestriction>false</useRestriction>");
    }
}

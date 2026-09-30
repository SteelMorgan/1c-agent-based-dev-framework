package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * TASK-175 W-06 (XG-40): {@code skd compile} обязан БЕЗУСЛОВНО эмитить
 * {@code <useRestriction>true|false</useRestriction>} у каждого параметра
 * (коммит Широкова 32e06cbc: платформа пишет тег всегда; не задано → false;
 * прежнее поведение давало LOST {@code useRestriction false} в roundtrip).
 *
 * <p>Текущие дефекты: {@code SkdWriter.writeParameter} (520-571) не эмитит тег
 * ни при каком значении; {@code SkdDsl.Parameter} не имеет поля
 * {@code useRestriction} — JSON с ключом падает Jackson-исключением.</p>
 *
 * <p>НЕ путать (B-9 technical-design): {@code writeUseRestriction} (строка 372) —
 * useRestriction ПОЛЯ набора данных; derived-параметры autoDates (582) — уже true.
 * Эти конструкции тут не тестируются и фиксом задеваться не должны.</p>
 */
class SkdWriterTask175Test {

    @TempDir
    Path tempDir;

    /** Компиляция SKD JSON → текст Template.xml (паттерн SkdWriterTest.compile). */
    private String compile(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        Path outputXml = tempDir.resolve("Template_" + System.nanoTime() + ".xml");
        new SkdWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return Files.readString(outputXml);
    }

    private static String paramJson(String useRestrictionFragment) {
        return """
                {
                  "dataSets": [{ "type": "query", "name": "НаборДанных1", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "parameters": [
                    { "name": "Период", "type": "StandardPeriod"%s }
                  ]
                }
                """.formatted(useRestrictionFragment);
    }

    /**
     * Red-кейс: {@code useRestriction:false} не должен теряться.
     * Сегодня падает уже на разборе DSL (поле отсутствует в SkdDsl.Parameter) —
     * это часть того же дефекта XG-40, оборачиваем в ассерт.
     */
    @Test
    void w06_useRestrictionFalse_emittedExplicitly() throws Exception {
        AtomicReference<String> content = new AtomicReference<>();
        assertThatCode(() -> content.set(compile(paramJson(", \"useRestriction\": false"))))
                .as("DSL-ключ useRestriction параметра должен приниматься (32e06cbc)")
                .doesNotThrowAnyException();

        assertThat(content.get())
                .as("useRestriction:false эмитится явно — иначе LOST в roundtrip (32e06cbc)")
                .contains("<useRestriction>false</useRestriction>");
    }

    /** Red-кейс: {@code useRestriction:true} эмитится со значением true. */
    @Test
    void w06_useRestrictionTrue_emitted() throws Exception {
        AtomicReference<String> content = new AtomicReference<>();
        assertThatCode(() -> content.set(compile(paramJson(", \"useRestriction\": true"))))
                .as("DSL-ключ useRestriction параметра должен приниматься (32e06cbc)")
                .doesNotThrowAnyException();

        assertThat(content.get())
                .contains("<useRestriction>true</useRestriction>");
    }

    /**
     * Red-кейс: ключ не задан → платформа всё равно пишет тег, значение по
     * умолчанию {@code false} (upstream: «не задано → false»).
     */
    @Test
    void w06_useRestrictionAbsent_defaultsToFalse() throws Exception {
        String content = compile(paramJson(""));

        assertThat(content)
                .as("без ключа useRestriction тег обязан эмититься со значением false")
                .contains("<useRestriction>false</useRestriction>");
        assertThat(content).doesNotContain("<useRestriction>true</useRestriction>");
    }

    // ─── Кейсы F-01 (cross-review кода 3d, BLOCK): семантика hidden из
    // upstream skd-compile.py @ 32e06cbc (строки ~1174-1177):
    //
    //   # Hidden implies useRestriction=true + availableAsField=false
    //   if parsed.get('hidden') is True:
    //       parsed['availableAsField'] = False
    //       parsed['useRestriction'] = True
    //
    // Перезапись БЕЗУСЛОВНАЯ — явный useRestriction:false пользователя
    // hidden'ом перекрывается. Фиксируем семантику upstream, не свою. ───

    /**
     * Red-кейс F-01: {@code hidden:true} БЕЗ явного useRestriction обязан
     * давать {@code <useRestriction>true</useRestriction>} (hidden ⇒
     * useRestriction=true + availableAsField=false, upstream ~1174).
     * Сегодня Java эмитит только из {@code param.getUseRestriction()} →
     * {@code false} — регресс семантики hidden относительно upstream.
     */
    @Test
    void w06_hiddenWithoutUseRestriction_impliesUseRestrictionTrue() throws Exception {
        String content = compile(paramJson(", \"hidden\": true"));

        assertThat(content)
                .as("hidden:true подразумевает useRestriction=true (skd-compile.py ~1174: "
                        + "«Hidden implies useRestriction=true + availableAsField=false»)")
                .contains("<useRestriction>true</useRestriction>");
        assertThat(content)
                .as("hidden:true не должен давать явный useRestriction=false — "
                        + "это хуже прежнего отсутствия тега")
                .doesNotContain("<useRestriction>false</useRestriction>");
    }

    /**
     * Red-кейс F-01 (контр-кейс приоритета): {@code hidden:true} +
     * явный {@code useRestriction:false}. По upstream-коду перезапись
     * {@code parsed['useRestriction'] = True} БЕЗУСЛОВНА — выполняется до
     * вычисления {@code ur_emit}, явное false пользователя НЕ побеждает.
     * Ожидание = семантика upstream: {@code true}.
     */
    @Test
    void w06_hiddenOverridesExplicitUseRestrictionFalse() throws Exception {
        String content = compile(paramJson(", \"hidden\": true, \"useRestriction\": false"));

        assertThat(content)
                .as("hidden:true безусловно перекрывает явный useRestriction:false "
                        + "(upstream 32e06cbc: перезапись parsed['useRestriction']=True "
                        + "до ur_emit, ветки для явного false нет)")
                .contains("<useRestriction>true</useRestriction>");
        assertThat(content).doesNotContain("<useRestriction>false</useRestriction>");
    }
}

package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.dsl.FormDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 Phase 4 (Tester): edge-кейсы W-04 (XG-39), не покрытые
 * {@code FormWriterTask175Test} (Phase 3b).
 *
 * <p>Семантика upstream form-compile.py @ HEAD (строки ~3010-3022):</p>
 * <pre>
 *   form_title = defn.get('title')
 *   if not form_title and defn['properties'].get('title'): form_title = properties.title
 *   if form_title: emit Title
 *   if form_title and 'autoTitle' not in props: autoTitle=false
 * </pre>
 * <ul>
 *   <li>пустой title — falsy в Python ({@code if form_title:}) / {@code isBlank()}
 *       в Java → НЕ явный заголовок, AutoTitle не подавляется;</li>
 *   <li>оба канала сразу — {@code defn.title} приоритетнее, properties.title
 *       исключается из Properties и НЕ попадает в вывод.</li>
 * </ul>
 */
class FormWriterTask175Phase4Test {

    @TempDir
    Path tempDir;

    private static FormDsl dsl(String title, Map<String, Object> properties) {
        return new FormDsl(title, properties, null, null, null, null, null, null);
    }

    private String compileDesigner(FormDsl dsl, String fileName) throws Exception {
        Path outputXml = tempDir.resolve(fileName);
        new FormWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return Files.readString(outputXml);
    }

    /**
     * Edge-кейс W-04: {@code properties.title = ""} (пустая строка) — НЕ явный
     * заголовок (upstream: пустая строка falsy в {@code if form_title:}).
     * AutoTitle НЕ подавляется (платформенный AutoTitle=true осмыслен,
     * как на fallback-ветке XG-11); Title эмитится из fallback-имени формы.
     */
    @Test
    void w04_emptyPropertiesTitle_doesNotSuppressAutoTitle() throws Exception {
        String content = compileDesigner(dsl(null, Map.of("title", "")), "FormEmptyTitle.xml");

        assertThat(content)
                .as("пустая строка properties.title — не явный заголовок "
                        + "(upstream: «if form_title:» — falsy), AutoTitle=false не эмитится")
                .doesNotContain("<AutoTitle>false</AutoTitle>");
        assertThat(content)
                .as("Title всё равно эмитится (XG-11 fallback — имя формы из пути)")
                .contains("<Title>");
    }

    /**
     * Edge-кейс W-04: title в ОБОИХ каналах сразу — приоритет у {@code dsl.title}
     * (upstream: {@code form_title = defn.get('title')} проверяется первым,
     * properties.title подхватывается ТОЛЬКО при пустом defn.title);
     * properties.title исключается из Properties и не должен попасть в вывод.
     */
    @Test
    void w04_bothTitleChannels_dslTitleWins() throws Exception {
        String content = compileDesigner(
                dsl("ИзDsl", Map.of("title", "ИзProperties")), "FormBothTitles.xml");

        assertThat(content)
                .as("корневой Title — из dsl.title (приоритетный канал upstream)")
                .contains("<v8:content>ИзDsl</v8:content>");
        assertThat(content)
                .as("properties.title при заданном dsl.title никуда не эмитится "
                        + "(upstream: skip 'title' в emit_properties; продвижение — "
                        + "только при пустом defn.title)")
                .doesNotContain("ИзProperties");
        assertThat(content)
                .as("явный Title (любым каналом) → AutoTitle=false (36cd63d8)")
                .contains("<AutoTitle>false</AutoTitle>");
    }

    /**
     * R-M.2 регрессия (backlog.md §5, EDT-сосед W-04): форма БЕЗ явного title —
     * EDT-путь обязан сохранить {@code autoTitle=true} (платформенный заголовок).
     * Пиннинг ветки {@code edtExplicitTitle == false} (FormWriter.createEdt:2102-2112);
     * 3b-тест покрыл только ветку явного title → false.
     */
    @Test
    void w04_edt_noTitle_autoTitleStaysTrue() throws Exception {
        Path outputPath = tempDir.resolve("FormEdtNoTitle.form");
        new FormWriter(OutputFormat.EDT).create(dsl(null, null), outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .as("EDT-путь без явного title: autoTitle=true сохраняется "
                        + "(подавление 36cd63d8 привязано только к явному заголовку)")
                .contains("<autoTitle>true</autoTitle>");
        assertThat(content).doesNotContain("<autoTitle>false</autoTitle>");
    }
}

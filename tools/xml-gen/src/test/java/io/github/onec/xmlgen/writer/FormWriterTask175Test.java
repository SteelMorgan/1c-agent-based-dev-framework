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
 * TASK-175 W-04 (XG-39): {@code form compile} обязан эмитить {@code AutoTitle=false}
 * при явно заданном Title формы, если пользователь явно не указал {@code autoTitle}
 * (коммит Широкова 36cd63d8: иначе платформа добавляет суффикс синонима →
 * двойной заголовок «Номенклатура: Номенклатура»; ~95% форм ERP).
 *
 * <p>Коррекция B-1/B-2 technical-design: дефект Designer-пути {@code create()} —
 * ОТСУТСТВИЕ тега {@code <AutoTitle>false</AutoTitle>} (PascalCase, позиция сразу
 * после {@code </Title>}); EDT-путь {@code createEdt} (строка 2065, безусловный
 * {@code autoTitle=true}) — сосед того же класса (lowercase).</p>
 *
 * <p>Взаимодействие с XG-11: Java всегда эмитит Title (fallback — имя формы из
 * пути). Для fallback-случая AutoTitle НЕ подавляется — upstream-семантика
 * привязана к ЯВНО заданному title.</p>
 */
class FormWriterTask175Test {

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
     * Кейс (a) Test Plan W-04 (Red): явный title без явного autoTitle →
     * {@code <AutoTitle>false</AutoTitle>} сразу после {@code </Title>}
     * (PascalCase — канон Designer, фикстура valid-vyborkontragenta.xml:10).
     */
    @Test
    void w04_designer_explicitTitle_emitsAutoTitleFalseAfterTitle() throws Exception {
        String content = compileDesigner(dsl("Моя форма", null), "FormA.xml");

        assertThat(content)
                .as("при явном title должен эмитироваться AutoTitle=false (36cd63d8)")
                .contains("<AutoTitle>false</AutoTitle>");

        int titleEnd = content.indexOf("</Title>");
        int autoTitle = content.indexOf("<AutoTitle>false</AutoTitle>");
        int acb = content.indexOf("<AutoCommandBar");
        assertThat(titleEnd).as("корневой Title обязан присутствовать (XG-11)").isPositive();
        assertThat(autoTitle)
                .as("позиция AutoTitle — после </Title> и до AutoCommandBar (канон Designer)")
                .isGreaterThan(titleEnd)
                .isLessThan(acb);
    }

    /**
     * Кейс (b) Test Plan W-04: DSL БЕЗ title (Title — fallback XG-11 из имени пути) →
     * AutoTitle НЕ эмитится (платформенный AutoTitle=true сохраняется).
     * Регрессионный кейс: проходит и сейчас; защищает фикс от подавления
     * AutoTitle на fallback-ветке (риск 3 §7.2 technical-design).
     */
    @Test
    void w04_designer_fallbackTitle_noAutoTitle() throws Exception {
        String content = compileDesigner(dsl(null, null), "FormB.xml");

        assertThat(content)
                .as("корневой Title всегда эмитится (fallback XG-11)")
                .contains("</Title>");
        assertThat(content)
                .as("для fallback-Title AutoTitle подавлять нельзя — форма без явного title "
                        + "должна сохранить платформенный AutoTitle=true")
                .doesNotContain("<AutoTitle>");
    }

    /**
     * Кейс (c) Test Plan W-04: явный {@code properties.autoTitle=true} + явный title →
     * приоритет у пользователя, эмитится {@code <AutoTitle>true</AutoTitle>}.
     */
    @Test
    void w04_designer_explicitAutoTitleTrue_respected() throws Exception {
        String content = compileDesigner(
                dsl("Моя форма", Map.of("autoTitle", true)), "FormC.xml");

        assertThat(content)
                .as("явный properties.autoTitle уважается всегда (upstream: hasAutoTitle → не трогаем)")
                .contains("<AutoTitle>true</AutoTitle>");
        assertThat(content).doesNotContain("<AutoTitle>false</AutoTitle>");
    }

    /**
     * Кейс (a2) — второй триггер-канал upstream (Red, добавлен по F-01 cross-review):
     * Title задан ТОЛЬКО через {@code properties.title} (НЕ через dsl.title) →
     * тоже обязан эмитироваться {@code <AutoTitle>false</AutoTitle>}.
     *
     * <p>Семантика сверена с form-compile.py @ 36cd63d8 (строки 2677-2679):
     * {@code form_title = defn.get('title') || defn['properties']['title']} —
     * properties.title равноправный источник form-level Title (он продвигается
     * в корневой Title и исключается из Properties; полная семантика продвижения —
     * объём фикса 3d, здесь пиннингуется только триггер AutoTitle=false).
     * В Java DSL канал достижим: {@code FormDsl.properties} — {@code Map<String,Object>},
     * ключ {@code "title"} выражается штатно. Фикс, обрабатывающий только
     * {@code dsl.getTitle()}, этот кейс не пройдёт.</p>
     */
    @Test
    void w04_designer_titleViaProperties_emitsAutoTitleFalse() throws Exception {
        String content = compileDesigner(
                dsl(null, Map.of("title", "Заголовок из properties")), "FormD.xml");

        assertThat(content)
                .as("properties.title — равноправный триггер AutoTitle=false "
                        + "(form-compile.py:2677-2679 @ 36cd63d8)")
                .contains("<AutoTitle>false</AutoTitle>");
    }

    /**
     * EDT-сосед того же класса (Red): createEdt:2065 эмитит безусловный
     * {@code <autoTitle>true</autoTitle>} — при явном title обязан быть {@code false}
     * (lowercase — канон EDT, образец EdtFormatTest).
     */
    @Test
    void w04_edt_explicitTitle_emitsAutoTitleFalse() throws Exception {
        Path outputPath = tempDir.resolve("FormEdt.form");
        new FormWriter(OutputFormat.EDT).create(dsl("Моя форма", null), outputPath);
        String content = Files.readString(outputPath);

        assertThat(content)
                .as("EDT-путь: при явном title autoTitle=false (сосед класса 36cd63d8)")
                .contains("<autoTitle>false</autoTitle>");
        assertThat(content).doesNotContain("<autoTitle>true</autoTitle>");
    }
}

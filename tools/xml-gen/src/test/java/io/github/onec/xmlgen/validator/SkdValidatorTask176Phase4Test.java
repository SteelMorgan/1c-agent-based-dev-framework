package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-176 Phase 4 (Tester) — расширение покрытия валидатора (S-07 / SKD-108),
 * НЕ дублирующее Phase 3b.
 *
 * <p>Покрывает:
 * <ul>
 *   <li><b>S-07 edge</b> (efdf5669 / XG-47): полностью ОТСУТСТВУЮЩИЙ
 *       {@code <expression>} (а не только пустой {@code <expression></expression>}
 *       из 3b) — диспозиция формулирует «пустой/отсутствующий», SKD-108 условие
 *       {@code expression == null || isEmpty}; обе ветки = WARNING, не ERROR.</li>
 *   <li><b>S-07 граница B-02</b>: calculatedField с пустым expression + сосед
 *       {@code totalField} того же dataPath. Подавление warning при наличии
 *       totalField-близнеца НЕ реализовано (вынесено в backlog как новая
 *       корреляционная проверка). Тест-страж фиксирует ГРАНИЦУ: близнец НЕ меняет
 *       исход — SKD-108 остаётся WARNING (не ERROR, не подавлен, без падения).</li>
 * </ul>
 *
 * <p>Стратегия — inline DCS-XML (паттерн {@link SkdValidatorTask176Test}). src/main
 * НЕ трогается.</p>
 */
class SkdValidatorTask176Phase4Test {

    private final SkdValidator validator = new SkdValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private Path writeXml(String content) throws Exception {
        Path file = tempDir.resolve("Template.xml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private List<ValidationIssue> skd108(List<ValidationIssue> issues) {
        return issues.stream().filter(i -> "SKD-108".equals(i.getCode())).toList();
    }

    /** DCS-схема: dataSet с одним calculatedField (фрагмент тела задаётся) + опциональный root-totalField. */
    private static String schema(String calcFieldBody, String rootTotalFieldOrEmpty) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<dataSource>\n\t\t<name>DS1</name>\n\t</dataSource>\n"
                + "\t<dataSet xsi:type=\"DataSetQuery\">\n"
                + "\t\t<name>DS1</name>\n"
                + "\t\t<dataSource>DS1</dataSource>\n"
                + "\t\t<query>SELECT 1</query>\n"
                + "\t\t<calculatedField>\n"
                + "\t\t\t<dataPath>ВычислимоеПоле</dataPath>\n"
                + calcFieldBody
                + "\t\t</calculatedField>\n"
                + "\t</dataSet>\n"
                + rootTotalFieldOrEmpty
                + "</DataCompositionSchema>\n";
    }

    /**
     * S-07 edge: calculatedField БЕЗ элемента {@code <expression>} вовсе. Условие
     * SKD-108 — {@code null || isEmpty}; ветка {@code null} (отсутствие элемента) в
     * 3b не покрыта. Должно дать WARNING, не ERROR.
     */
    @Test
    @DisplayName("unit-P4-S07: отсутствующий <expression> даёт SKD-108 WARNING (ветка null)")
    void s07_missingExpressionElement_isWarning() throws Exception {
        // тело без <expression> вовсе
        Path file = writeXml(schema("", ""));
        XmlDocument doc = reader.parse(file);

        List<ValidationIssue> only108 = skd108(validator.validate(doc, ValidationLevel.SEMANTIC));

        assertThat(only108)
                .as("отсутствующий <expression> — поверхность SKD-108 есть").isNotEmpty();
        assertThat(only108)
                .as("отсутствующий <expression> декларативного поля — WARNING (efdf5669)")
                .allMatch(i -> i.getSeverity() == Severity.WARNING);
        assertThat(only108)
                .as("ни одного ERROR на отсутствующем expression").noneMatch(i -> i.getSeverity() == Severity.ERROR);
    }

    /**
     * S-07 граница B-02: пустой {@code <expression>} + сосед {@code totalField} того же
     * dataPath. Логика подавления warning при totalField-близнеце НЕ портирована
     * (backlog). Тест-страж: близнец НЕ влияет — SKD-108 остаётся WARNING (граница
     * зафиксирована как регрессия; если кто-то «допилит» подавление — тест среагирует).
     */
    @Test
    @DisplayName("unit-P4-S07: пустой expression при наличии totalField-близнеца остаётся WARNING (граница B-02)")
    void s07_emptyExpressionWithTotalFieldTwin_remainsWarningNotSuppressed() throws Exception {
        String totalFieldTwin = "\t<totalField>\n"
                + "\t\t<dataPath>ВычислимоеПоле</dataPath>\n"
                + "\t\t<expression>СУММА(Сумма)</expression>\n"
                + "\t</totalField>\n";
        Path file = writeXml(schema("\t\t\t<expression></expression>\n", totalFieldTwin));
        XmlDocument doc = reader.parse(file);

        List<ValidationIssue> only108 = skd108(validator.validate(doc, ValidationLevel.SEMANTIC));

        assertThat(only108)
                .as("totalField-близнец НЕ подавляет SKD-108 (suppression — backlog B-02)")
                .isNotEmpty();
        assertThat(only108)
                .as("исход неизменен близнецом: WARNING, не ERROR")
                .allMatch(i -> i.getSeverity() == Severity.WARNING);
    }
}

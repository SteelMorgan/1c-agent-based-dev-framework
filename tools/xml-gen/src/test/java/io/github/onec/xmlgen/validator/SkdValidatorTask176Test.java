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
 * TASK-176 (контур B+C, домен SKD) — Red-тест для {@code skd validate} (S-07).
 *
 * <p>ЕДИНСТВЕННЫЙ живой фикс S-07 (technical-design rev.2, R-176-V.4): {@code SKD-108}
 * (SkdValidator.java:191) флагует пустой/отсутствующий {@code <expression>}
 * декларативного {@code calculatedField} как {@code ERROR}, тогда как итоговый
 * upstream после RELAX (efdf5669) даёт {@code WARNING} — пустое выражение
 * легитимно у vendor-схем (declarative-only calculatedField). Java здесь СТРОЖЕ
 * upstream → реальный false positive.</p>
 *
 * <p>Остальные 4 из 6 explorer-кандидатов (composite valueType, system-namespace,
 * {@code v8:Null/Type/ValueStorage}, qualifier) — проверок в Java НЕТ вовсе →
 * диспозиция «не подтверждён» (НЕ Red-тест: тестировать нечего). Реверченный
 * {@code 3ef4f440} НЕ портируется (R-176-N.6).</p>
 *
 * <p>Фикстура — минимальная валидная DCS-схема (паттерн {@code SkdValidatorTest},
 * inline XML); при корпусном прогоне R-176-V.1 исполнитель дополняет байт-копией
 * Designer-выгрузки с пустым {@code <expression>} (R-176-N.5, ступень 2).</p>
 */
class SkdValidatorTask176Test {

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

    /** DCS-схема с одним dataSet, содержащим calculatedField с заданным expression-фрагментом. */
    private static String schema(String calcFieldExpressionElement) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<dataSource>\n"
                + "\t\t<name>DS1</name>\n"
                + "\t</dataSource>\n"
                + "\t<dataSet xsi:type=\"DataSetQuery\">\n"
                + "\t\t<name>DS1</name>\n"
                + "\t\t<dataSource>DS1</dataSource>\n"
                + "\t\t<query>SELECT 1</query>\n"
                + "\t\t<calculatedField>\n"
                + "\t\t\t<dataPath>ВычислимоеПоле</dataPath>\n"
                + "\t\t\t" + calcFieldExpressionElement + "\n"
                + "\t\t</calculatedField>\n"
                + "\t</dataSet>\n"
                + "</DataCompositionSchema>\n";
    }

    /**
     * Red (S-07 / SKD-108): декларативный calculatedField с ПУСТЫМ
     * {@code <expression></expression>}. Сегодня валидатор выдаёт {@code SKD-108}
     * уровня {@code ERROR} (SkdValidator.java:191) — ложный позитив на легитимном
     * vendor-паттерне. После фикса (efdf5669: downgrade error→warning) — уровень
     * должен стать {@code WARNING}.
     */
    @Test
    @DisplayName("unit-S07: пустой <expression> декларативного calculatedField даёт WARNING, не ERROR")
    void s07_emptyExpression_isWarningNotError() throws Exception {
        Path file = writeXml(schema("<expression></expression>"));
        XmlDocument doc = reader.parse(file);

        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);
        List<ValidationIssue> only108 = skd108(issues);

        assertThat(only108)
                .as("SKD-108 должен сработать на пустом <expression> (поверхность есть)")
                .isNotEmpty();
        assertThat(only108)
                .as("пустой <expression> декларативного calculatedField — WARNING, не ERROR "
                        + "(efdf5669: declarative-only calculatedField легитимен у vendor)")
                .allMatch(i -> i.getSeverity() == Severity.WARNING);
        assertThat(only108)
                .as("ни одного SKD-108 уровня ERROR на пустом expression не остаётся")
                .noneMatch(i -> i.getSeverity() == Severity.ERROR);
    }

    /**
     * Регрессионный негатив (S-07): осмысленный calculatedField с непустым
     * {@code <expression>} — SKD-108 НЕ срабатывает вовсе. Проходит и до, и после
     * фикса (легитимные кейсы валидатора сохраняются, R-176-V.2).
     */
    @Test
    @DisplayName("unit-S07-neg: непустой <expression> не порождает SKD-108")
    void s07_nonEmptyExpression_noIssue() throws Exception {
        Path file = writeXml(schema("<expression>Цена * 2</expression>"));
        XmlDocument doc = reader.parse(file);

        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(skd108(issues))
                .as("calculatedField с осмысленным выражением не должен флагаться SKD-108")
                .isEmpty();
    }
}

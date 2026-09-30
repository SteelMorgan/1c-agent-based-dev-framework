package io.github.onec.xmlgen.cli;

import io.github.onec.xmlgen.validator.EpfValidator;
import io.github.onec.xmlgen.validator.Severity;
import io.github.onec.xmlgen.validator.ValidationIssue;
import io.github.onec.xmlgen.validator.ValidationLevel;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TASK-174 XG-03: {@code epf init} с кириллическим именем обработки должен работать
 * без обходов. Прежняя CLI-валидация имени ({@code Commands.epfInit}) отвергала
 * кириллицу регуляркой {@code [A-Za-z_][A-Za-z0-9_]*} (ошибочная посылка TASK-155),
 * что вынуждало латинский плейсхолдер + edit replace-text и давало битый корневой XML.
 *
 * <p>Тест гоняет публичный диспетчер {@code Commands.execute("epf", ...)} (реальный
 * CLI-путь, где живёт фикс регулярки) с кириллическим именем и доказывает: артефакт
 * создан и проходит EpfValidator без ошибок.
 */
class EpfInitCyrillicTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void epfInit_cyrillicName_succeedsAndValidates() throws Exception {
        // Реальный CLI-путь: epf init --name биг_ПробнаяОбработка <outputDir>
        Commands.execute("epf", new String[]{
                "init", "--name", "биг_ПробнаяОбработка", tempDir.toString()
        });

        Path rootXml = tempDir.resolve("биг_ПробнаяОбработка.xml");
        assertThat(rootXml).as("корневой XML создан под кириллическим именем").exists();

        // Корневой XML структурно корректен: один потомок ExternalDataProcessor, Name = имя.
        String content = Files.readString(rootXml);
        assertThat(content).contains("<ExternalDataProcessor uuid=");
        assertThat(content).contains("<Name>биг_ПробнаяОбработка</Name>");

        XmlStructureReader reader = new XmlStructureReader();
        XmlDocument doc = reader.parse(rootXml);
        List<ValidationIssue> issues =
                new EpfValidator().validate(doc, ValidationLevel.SEMANTIC);
        List<ValidationIssue> errors = issues.stream()
                .filter(i -> i.getSeverity() == Severity.ERROR).toList();
        assertThat(errors).as("ошибки валидатора на кириллическом EPF: " + errors).isEmpty();
    }

    @Test
    void epfInit_latinName_stillWorks() throws Exception {
        // Регресс: латинское имя продолжает работать.
        Commands.execute("epf", new String[]{
                "init", "--name", "TestProc174", tempDir.toString()
        });
        assertThat(tempDir.resolve("TestProc174.xml")).exists();
    }

    @Test
    void epfInit_nameWithSpace_stillRejected() throws Exception {
        // Имя с пробелом по-прежнему отвергается (валидный путь Designer): фикс расширяет
        // допустимый алфавит на кириллицу, но НЕ снимает запрет на пробелы/спецсимволы.
        assertThatThrownBy(() -> Commands.execute("epf", new String[]{
                "init", "--name", "Имя С Пробелом", tempDir.toString()
        })).hasMessageContaining("Invalid 1C name");
    }
}

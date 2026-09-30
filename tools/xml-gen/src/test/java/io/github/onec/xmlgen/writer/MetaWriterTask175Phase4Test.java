package io.github.onec.xmlgen.writer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 Phase 4 (Tester): R-M.2 регрессия W-13 (backlog.md §5) —
 * дефолты {@code QuickChoice} объектного уровня в MetaWriter.
 *
 * <p>Аудит Phase 3d (dispositions.md, T-W13) доказал грепом совпадение
 * 6 пар «тип → дефолт» с upstream meta-compile.py @ HEAD:
 * Enum — единственный тип с дефолтом {@code true} (py:1162), остальные
 * пять — {@code false}. Тестов на эмиссию не было — этот класс пиннит
 * таблицу аудита как исполняемую регрессию.</p>
 *
 * <p>Проверяется НЕпрефиксный объектный {@code <QuickChoice>} (Properties);
 * {@code <xr:QuickChoice>Auto</xr:QuickChoice>} стандартных атрибутов —
 * другая конструкция, к дефолту объекта отношения не имеет.</p>
 */
class MetaWriterTask175Phase4Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private String read(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        int off = (b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2]) ? 3 : 0;
        return new String(b, off, b.length - off, StandardCharsets.UTF_8);
    }

    /**
     * Таблица W-13 из dispositions.md (двухфакторный аудит 3d):
     * upstream meta-compile.py :1029/:1162/:1477/:1564/:1681/:1773 ↔
     * MetaWriter.java :445/:564/:822/:701/:619/:749.
     */
    @ParameterizedTest(name = "{0}: дефолт QuickChoice={1}")
    @CsvSource({
            "Catalog,                     false",
            "Enum,                        true",
            "ChartOfAccounts,             false",
            "ChartOfCharacteristicTypes,  false",
            "ChartOfCalculationTypes,     false",
            "ExchangePlan,                false",
    })
    void rm2_w13_quickChoiceDefault_matchesUpstream(String type, boolean expected)
            throws Exception {
        Path dir = tempDir.resolve(type);
        Files.createDirectories(dir);
        Path json = dir.resolve("obj.json");
        Files.writeString(json,
                "{\"type\":\"" + type + "\",\"name\":\"test_Объект\"}", StandardCharsets.UTF_8);

        new MetaWriter().compile(json, dir);

        Path xmlFile;
        try (var stream = Files.walk(dir)) {
            xmlFile = stream
                    .filter(p -> p.getFileName().toString().equals("test_Объект.xml"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "compile не создал test_Объект.xml для типа " + type));
        }
        String xml = read(xmlFile);

        assertThat(xml)
                .as("%s: объектный дефолт QuickChoice=%s (W-13, dispositions.md; "
                        + "Enum — единственный true)", type, expected)
                .contains("<QuickChoice>" + expected + "</QuickChoice>");
        assertThat(xml)
                .as("%s: противоположное значение дефолта эмититься не должно", type)
                .doesNotContain("<QuickChoice>" + !expected + "</QuickChoice>");
    }
}

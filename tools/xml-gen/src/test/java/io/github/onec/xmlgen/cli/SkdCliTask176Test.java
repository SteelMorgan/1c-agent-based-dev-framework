package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-176 (контур B+C, домен SKD) — интеграционные Red-тесты CLI (форк JVM через
 * {@link Main}, паттерн {@code MetaSubsystemSkdMxlCliContractTest}):
 * S-06 ({@code skd info --mode query --raw} — lossless round-trip запроса) и
 * S-09 ядро NO-OP (идемпотентный {@code skd edit} НЕ перезаписывает файл).
 *
 * <p>Локации (technical-design rev.2): S-06 — флаг {@code --raw} в Commands.java:1849
 * отсутствует (сейчас молча игнорируется), {@code printQuery} SkdInfoPrinter.java:288
 * дробит на батчи + per-batch заголовки; S-09 — {@code applySkdOperation}:2040 void →
 * результат отброшен :2016, {@code saveAndValidate}:2624 безусловно tmp+atomic-move
 * даже при NO-OP. После фикса — гейт по агрегированному {@code changed}.</p>
 */
@DisplayName("integr-skd CLI TASK-176 (S-06 --raw, S-09 NO-OP)")
class SkdCliTask176Test {

    @TempDir
    Path tempDir;

    // ════════════════════════════════════════════════════════════════════
    // S-06 — skd info --mode query --raw (upstream 9877fe40)
    // ════════════════════════════════════════════════════════════════════

    /** Точный текст запроса в фикстуре (байт-в-байт эталон для --raw, F-03). */
    private static final String EXPECTED_RAW_QUERY =
            "ВЫБРАТЬ 1 КАК А ПОМЕСТИТЬ Врем;\n"
            + "////////////////////\n"
            + "ВЫБРАТЬ 2 КАК Б ИЗ Врем";

    /** Схема с запросом из двух батчей (разделитель ;\n////...\n, см. BATCH_SEPARATOR). */
    private Path writeBatchedSchema() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n"
                + "\t<dataSet xsi:type=\"DataSetQuery\">\n"
                + "\t\t<name>Главный</name>\n"
                + "\t\t<query>" + EXPECTED_RAW_QUERY + "</query>\n"
                + "\t</dataSet>\n"
                + "</DataCompositionSchema>\n";
        Path file = tempDir.resolve("Template.xml");
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }

    /** Один хвостовой перевод строки (от println) допустим; внутренний контент — байт-в-байт. */
    private static String stripSingleTrailingNewline(String s) {
        if (s.endsWith("\r\n")) {
            return s.substring(0, s.length() - 2);
        }
        if (s.endsWith("\n")) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * Red (S-06): {@code skd info --mode query --raw} обязан печатать текст запроса
     * байт-в-байт (с разделителем {@code ////...} и без декораций). Сегодня флаг
     * {@code --raw} не существует (молча игнорируется) → печатается батчевый вид с
     * заголовками "=== Query" / "--- Batch", а строка-разделитель поглощается split.
     */
    @Test
    @DisplayName("integr-S06: skd info --raw печатает запрос verbatim без батч-декораций")
    void s06_rawQuery_printsVerbatimWithoutDecorations() throws Exception {
        Path schema = writeBatchedSchema();

        ProcessResult r = runMain("skd", "info", schema.toString(), "--mode", "query", "--raw");
        assertThat(r.exitCode()).as(r.combinedOutput()).isEqualTo(0);

        String out = r.stdout();
        assertThat(out)
                .as("--raw не должен печатать заголовок-декорацию '=== Query'")
                .doesNotContain("=== Query");
        assertThat(out)
                .as("--raw не должен дробить на '--- Batch N ---'")
                .doesNotContain("--- Batch");
        assertThat(out)
                .as("--raw сохраняет строку-разделитель батчей verbatim (lossless round-trip)")
                .contains("////////////////////");
        assertThat(out)
                .as("--raw содержит обе строки запроса дословно")
                .contains("ВЫБРАТЬ 1 КАК А ПОМЕСТИТЬ Врем;")
                .contains("ВЫБРАТЬ 2 КАК Б ИЗ Врем");

        // F-03: точное равенство тексту запроса (без нормализации пробелов/разделителя);
        // допускается ровно один хвостовой перевод строки от println — внутри байт-в-байт.
        assertThat(stripSingleTrailingNewline(out))
                .as("--raw печатает текст запроса БАЙТ-В-БАЙТ (без декораций, без дробления "
                        + "батчей, разделитель //// сохранён) — спека spec.md:233")
                .isEqualTo(EXPECTED_RAW_QUERY);
    }

    @Test
    @DisplayName("integr-S06: skd info -OutFile пишет UTF-8 файл с кириллическим именем")
    void s06_infoOutfile_writesUtf8File() throws Exception {
        Path schema = writeBatchedSchema();
        Path outFile = tempDir.resolve("выгрузка.txt");

        ProcessResult r = runMain("skd", "info", schema.toString(),
                "-Mode", "query", "-OutFile", outFile.toString());

        assertThat(r.exitCode()).as(r.combinedOutput()).isEqualTo(0);
        assertThat(r.stdout()).isEmpty();
        assertThat(Files.readString(outFile, StandardCharsets.UTF_8))
                .contains("=== Query: Главный")
                .contains("ВЫБРАТЬ 1 КАК А ПОМЕСТИТЬ Врем");
    }

    @Test
    @DisplayName("integr-S06: skd info --batch --raw печатает один пакет запроса")
    void s06_infoBatchRaw_printsOnlyRequestedBatch() throws Exception {
        Path schema = writeBatchedSchema();

        ProcessResult r = runMain("skd", "info", schema.toString(),
                "--mode", "query", "--batch", "2", "--raw");

        assertThat(r.exitCode()).as(r.combinedOutput()).isEqualTo(0);
        assertThat(stripSingleTrailingNewline(r.stdout()))
                .isEqualTo("ВЫБРАТЬ 2 КАК Б ИЗ Врем");
        assertThat(r.stdout())
                .doesNotContain("ВЫБРАТЬ 1 КАК А")
                .doesNotContain("////////////////////")
                .doesNotContain("=== Query");
    }

    /**
     * Red (S-06, F-03): lossless round-trip {@code --raw → set-query → --raw}. Вывод
     * первого {@code info --raw} подаётся обратно через {@code set-query @file}, затем
     * повторный {@code info --raw} обязан дать ТОТ ЖЕ текст (внутренний контент байт-в-байт)
     * — доказательство, что raw-режим не теряет/не нормализует запрос ни на печати, ни на
     * записи. Сегодня флаг {@code --raw} не существует → Red на первом же info.
     */
    @Test
    @DisplayName("integr-S06: --raw → set-query → --raw lossless round-trip")
    void s06_rawQuery_roundTripLossless() throws Exception {
        Path schema = writeBatchedSchema();

        ProcessResult r1 = runMain("skd", "info", schema.toString(), "--mode", "query", "--raw");
        assertThat(r1.exitCode()).as(r1.combinedOutput()).isEqualTo(0);
        String raw1 = stripSingleTrailingNewline(r1.stdout());

        // записываем raw1 в файл и возвращаем как запрос (set-query @file — без shell-эскейпа)
        Path queryFile = tempDir.resolve("roundtrip-query.txt");
        Files.writeString(queryFile, raw1, StandardCharsets.UTF_8);
        ProcessResult setRes = runMain("skd", "edit", schema.toString(),
                "set-query", "@" + queryFile);
        assertThat(setRes.exitCode()).as(setRes.combinedOutput()).isEqualTo(0);

        ProcessResult r2 = runMain("skd", "info", schema.toString(), "--mode", "query", "--raw");
        assertThat(r2.exitCode()).as(r2.combinedOutput()).isEqualTo(0);
        String raw2 = stripSingleTrailingNewline(r2.stdout());

        assertThat(raw2)
                .as("round-trip --raw→set-query→--raw обязан быть lossless (внутренний контент "
                        + "запроса не теряется и не нормализуется)")
                .isEqualTo(raw1)
                .isEqualTo(EXPECTED_RAW_QUERY);
    }

    // ════════════════════════════════════════════════════════════════════
    // S-09 — NO-OP edit не перезаписывает файл (upstream 511bfe7f)
    // ════════════════════════════════════════════════════════════════════

    /** Валидная (компилятором gen'а) схема — для гарантии exit 0 на edit. */
    private Path compileValidSchema() throws Exception {
        Path json = tempDir.resolve("skd.json");
        Files.writeString(json, """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Основной",
                    "query": "ВЫБРАТЬ Цена ИЗ Справочник.Номенклатура",
                    "fields": [{ "field": "Цена", "type": "decimal(15,2)" }]
                  }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": { "selection": ["Цена"] }
                  }]
                }
                """, StandardCharsets.UTF_8);
        Path template = tempDir.resolve("Template.xml");
        ProcessResult c = runMain("skd", "compile", json.toString(), template.toString());
        assertThat(c.exitCode()).as("предусловие: компиляция валидной схемы " + c.combinedOutput())
                .isEqualTo(0);
        return template;
    }

    /**
     * Red (S-09): подлинный NO-OP edit ({@code remove-field} несуществующего поля)
     * НЕ должен перезаписывать файл. Детектор — фиксированный «старый» mtime: если
     * файл переписан, mtime станет «сейчас»; при работающем гейте mtime сохранится.
     * Сегодня {@code saveAndValidate}:2624 пишет безусловно → mtime меняется → Red.
     */
    @Test
    @DisplayName("integr-S09: NO-OP skd edit не перезаписывает файл (mtime неизменен)")
    void s09_noopEdit_doesNotRewriteFile() throws Exception {
        Path schema = compileValidSchema();

        // Фиксированный «старый» штамп — заведомо отличим от времени записи.
        FileTime oldStamp = FileTime.fromMillis(1_000_000_000_000L); // 2001-09-09
        Files.setLastModifiedTime(schema, oldStamp);
        byte[] before = Files.readAllBytes(schema);

        ProcessResult r = runMain("skd", "edit", schema.toString(),
                "remove-field", "НесуществующееПоле_XYZ");
        assertThat(r.exitCode()).as(r.combinedOutput()).isEqualTo(0);

        FileTime after = Files.getLastModifiedTime(schema);
        assertThat(after)
                .as("NO-OP edit (поле не найдено) НЕ должен перезаписывать файл — "
                        + "mtime обязан остаться прежним (511bfe7f). Сегодня файл пишется "
                        + "безусловно (saveAndValidate:2624) → mtime сдвигается")
                .isEqualTo(oldStamp);
        assertThat(Files.readAllBytes(schema))
                .as("содержимое файла при NO-OP не должно меняться")
                .isEqualTo(before);
    }

    /** Валидная схема, где «ЛишнееПоле» присутствует ТОЛЬКО в selection варианта. */
    private Path compileSelectionOnlyFieldSchema() throws Exception {
        Path json = tempDir.resolve("skd-sel.json");
        Files.writeString(json, """
                {
                  "dataSets": [{
                    "type": "query",
                    "name": "Основной",
                    "query": "ВЫБРАТЬ Цена, ЛишнееПоле ИЗ Справочник.Номенклатура",
                    "fields": [{ "field": "Цена", "type": "decimal(15,2)" }]
                  }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": { "selection": ["Цена", "ЛишнееПоле"] }
                  }]
                }
                """, StandardCharsets.UTF_8);
        Path template = tempDir.resolve("Template.xml");
        ProcessResult c = runMain("skd", "compile", json.toString(), template.toString());
        assertThat(c.exitCode()).as("предусловие: компиляция схемы " + c.combinedOutput()).isEqualTo(0);
        // предусловие: selection-item для ЛишнееПоле существует на диске до удаления
        String pre = Files.readString(template, StandardCharsets.UTF_8);
        assertThat(pre)
                .as("предусловие: selection содержит ЛишнееПоле до удаления")
                .contains("<dcsset:field>ЛишнееПоле</dcsset:field>");
        return template;
    }

    /**
     * F-02 (зеркало к NO-OP): {@code remove-field} поля, присутствующего ЛИШЬ в
     * selection варианта — реальная правка ОБЯЗАНА быть записана на диск (selection-item
     * удалён), это НЕ NO-OP. Тестирует сквозной проброс {@code changed} через
     * {@code applySingleSkdOp/applySkdOperation → saveAndValidate}, а не только OpResult
     * editor'а: под changed-гейтом S-09, опирающимся на «лгущий» unchanged removeField
     * (F-01), реальная мутация selection молча потерялась бы (потеря данных). Тест-страж:
     * зелёный на текущем коде (безусловная запись) И после ПРАВИЛЬНОГО фикса (removeField
     * правдиво возвращает changed при touch selection); красный при НАИВНОМ гейте,
     * подавляющем запись по лгущему unchanged.
     */
    @Test
    @DisplayName("integr-S09: remove-field поля-только-в-selection РЕАЛЬНО пишет файл (не NO-OP)")
    void s09_removeSelectionOnlyField_writesChangeToDisk() throws Exception {
        Path schema = compileSelectionOnlyFieldSchema();
        byte[] before = Files.readAllBytes(schema);

        ProcessResult r = runMain("skd", "edit", schema.toString(), "remove-field", "ЛишнееПоле");
        assertThat(r.exitCode()).as(r.combinedOutput()).isEqualTo(0);

        String after = Files.readString(schema, StandardCharsets.UTF_8);
        assertThat(after)
                .as("selection-item ЛишнееПоле ОБЯЗАН быть удалён из файла на диске "
                        + "(сквозной changed через saveAndValidate; под наивным гейтом по "
                        + "лгущему unchanged правка потерялась бы — F-01/F-02)")
                .doesNotContain("<dcsset:field>ЛишнееПоле</dcsset:field>");
        assertThat(after)
                .as("файл не вычищен целиком — поле остаётся в тексте запроса (доказательство "
                        + "реальной, а не разрушительной правки)")
                .contains("ВЫБРАТЬ Цена, ЛишнееПоле ИЗ Справочник.Номенклатура");
        assertThat(Files.readAllBytes(schema))
                .as("содержимое файла при реальной правке ДОЛЖНО измениться")
                .isNotEqualTo(before);
    }

    // ─── форк JVM с Main (паттерн MetaSubsystemSkdMxlCliContractTest) ───

    private ProcessResult runMain(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        boolean exited = process.waitFor(30, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {
        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

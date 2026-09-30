package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BslModuleEditorTest {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Test
    void preservesIndentationAndBom() throws Exception {
        // Файл с BOM, табами и LF
        String src = "#Область ПрограммныйИнтерфейс\n\n"
                + "Процедура Проба()\n"
                + "\tСообщить(\"x\");\n"
                + "КонецПроцедуры\n\n"
                + "#КонецОбласти\n";
        Path file = tempDir.resolve("Module.bsl");
        byte[] body = src.getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, withBom, 0, BOM.length);
        System.arraycopy(body, 0, withBom, BOM.length, body.length);
        Files.write(file, withBom);

        BslModuleEditor ed = new BslModuleEditor(file);
        ed.insertIntoRegion("ПрограммныйИнтерфейс",
                "Процедура НоваяПроцедура()\n\tВозврат;\nКонецПроцедуры\n",
                BslModuleEditor.InsertPosition.END);
        ed.save();

        byte[] result = Files.readAllBytes(file);
        // BOM сохраняется
        assertThat(result[0]).isEqualTo(BOM[0]);
        assertThat(result[1]).isEqualTo(BOM[1]);
        assertThat(result[2]).isEqualTo(BOM[2]);
        // Табы сохраняются
        String txt = new String(result, 3, result.length - 3, StandardCharsets.UTF_8);
        assertThat(txt).contains("\tСообщить(\"x\");");
        assertThat(txt).contains("Процедура НоваяПроцедура()");
        // Структура области не нарушена
        assertThat(txt).contains("#Область ПрограммныйИнтерфейс");
        assertThat(txt).contains("#КонецОбласти");
    }

    @Test
    void findProcedureAndFunction() throws Exception {
        String src = "Процедура Один()\nКонецПроцедуры\n\n"
                + "Функция Два()\n\tВозврат 1;\nКонецФункции\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);

        Optional<BslModuleEditor.Range> p = ed.findProcedure("Один");
        Optional<BslModuleEditor.Range> f = ed.findFunction("Два");
        Optional<BslModuleEditor.Range> nope = ed.findProcedure("НетТакой");

        assertThat(p).isPresent();
        assertThat(p.get().name).isEqualTo("Один");
        assertThat(f).isPresent();
        assertThat(f.get().name).isEqualTo("Два");
        assertThat(nope).isEmpty();
    }

    @Test
    void insertIntoRegion_CreatesRegionIfMissing() throws Exception {
        String src = "// header\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);

        ed.insertIntoRegion("ПрограммныйИнтерфейс", "Процедура X()\nКонецПроцедуры\n",
                BslModuleEditor.InsertPosition.END);
        ed.save();

        String result = Files.readString(file);
        assertThat(result).contains("#Область ПрограммныйИнтерфейс");
        assertThat(result).contains("Процедура X()");
        assertThat(result).contains("#КонецОбласти");
    }

    @Test
    void appendBeforeReturn_InsertsBeforeReturn() throws Exception {
        String src = "Функция F()\n"
                + "\tA = 1;\n"
                + "\tВозврат A;\n"
                + "КонецФункции\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);

        ed.appendBeforeReturn("F", "\tB = 2;\n");
        ed.save();

        String result = Files.readString(file);
        int b = result.indexOf("B = 2;");
        int ret = result.indexOf("Возврат A;");
        assertThat(b).isPositive();
        assertThat(b).isLessThan(ret);
    }

    @Test
    void appendBranchToIfChain_AddsIliEsli() throws Exception {
        String src = "Процедура P()\n"
                + "\n"
                + "\tЕсли ИдентификаторКоманды = \"A\" Тогда\n"
                + "\t\t// тело A\n"
                + "\tКонецЕсли;\n"
                + "\n"
                + "КонецПроцедуры\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);

        ed.appendBranchToIfChain("P", "ИдентификаторКоманды = \"B\"", "\t\t// тело B");
        ed.save();

        String r = Files.readString(file);
        assertThat(r).contains("Если ИдентификаторКоманды = \"A\" Тогда");
        assertThat(r).contains("ИначеЕсли ИдентификаторКоманды = \"B\" Тогда");
        assertThat(r).contains("// тело B");
        // КонецЕсли остался один
        assertThat(r.split("КонецЕсли")).hasSize(2);
    }

    @Test
    void appendBranchToIfChain_ThrowsIfNoIf() throws Exception {
        String src = "Процедура P()\n"
                + "\t// just a comment\n"
                + "КонецПроцедуры\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);
        assertThatThrownBy(() ->
                ed.appendBranchToIfChain("P", "x = 1", "// body"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void findOrCreateProcedure_CreatesInRegion() throws Exception {
        String src = "#Область ПрограммныйИнтерфейс\n\n#КонецОбласти\n";
        Path file = tempDir.resolve("M.bsl");
        Files.writeString(file, src);
        BslModuleEditor ed = new BslModuleEditor(file);

        ed.findOrCreateProcedure(
                "NewProc",
                "Процедура NewProc()\n\tA = 1;\nКонецПроцедуры\n",
                "ПрограммныйИнтерфейс");
        ed.save();

        String r = Files.readString(file);
        assertThat(r).contains("Процедура NewProc()");
        // должно быть внутри области
        int regBegin = r.indexOf("#Область ПрограммныйИнтерфейс");
        int proc = r.indexOf("Процедура NewProc()");
        int regEnd = r.indexOf("#КонецОбласти");
        assertThat(regBegin).isLessThan(proc);
        assertThat(proc).isLessThan(regEnd);
    }
}

//++agent TASK-172 [02.06.2026 07:10:00]
package io.github.onec.xmlgen.io;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Единая I/O-граница нормализации канона Designer для всего вывода xml-gen.
 *
 * <p>Эталон (грунт-труф) — выгрузка типового решения {@code _Демо}: ВСЕ
 * Designer-файлы (метаданные {@code .xml}, тела макетов {@code Template.xml},
 * модули {@code .bsl} {@code Ext/ObjectModule.bsl}) хранятся с переводом строк
 * <b>CRLF</b> ({@code \r\n}) и UTF-8 BOM ({@code ef bb bf}). xml-gen исторически
 * писал LF, а {@code .bsl} — ещё и без BOM. Эта точка сводит весь вывод к канону.</p>
 *
 * <p>ЗАЧЕМ единый чокпоинт, а не россыпь {@code \r\n} по сотням литералов: канон
 * переводов строк — свойство I/O-границы, а не текста шаблонов. Литералы в коде
 * остаются {@code \n} (читаемость, идемпотентность round-trip), а CRLF
 * навешивается ровно один раз — перед записью байтов на диск.</p>
 */
public final class Crlf {

    /** UTF-8 BOM — жёсткое требование Designer для всех генерируемых файлов. */
    public static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private Crlf() {}

    /**
     * Привести переводы строк к CRLF идемпотентно: сначала схлопываем любые
     * {@code \r\n} в {@code \n}, затем разворачиваем все {@code \n} в {@code \r\n}.
     * Двойной проход не плодит {@code \r\r\n} на уже-CRLF входе и чинит смешанные
     * раскладки (LF-фрагменты, вставленные в CRLF-файл при правке).
     *
     * <p>Одиночные {@code \r} (старый Mac-стиль) в Designer-выводе не встречаются,
     * поэтому отдельно не обрабатываются — это упростило бы только теоретический кейс.</p>
     */
    public static String normalize(String s) {
        if (s == null) {
            return null;
        }
        return s.replace("\r\n", "\n").replace("\n", "\r\n");
    }

    /**
     * Сформировать байты файла: BOM + содержимое, нормализованное к CRLF.
     * Единый builder для всех {@code writeWithBom}-хелперов генераторов.
     */
    public static byte[] withBom(String content) {
        byte[] body = normalize(content).getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        return out;
    }

    /**
     * Записать новый файл с BOM и CRLF (генерация .xml/.bsl/Template.xml).
     * Funnel-точка для всех {@code Files.write(path, BOM+content)} новых файлов.
     */
    public static void writeWithBom(Path path, String content) throws IOException {
        Files.write(path, withBom(content));
    }

    /**
     * Обернуть {@link OutputStream} фильтром LF→CRLF на байтовом уровне.
     * Применяется к StAX-потоку ({@code XmlWriter}) и {@code XmlDocumentWriter},
     * где переводы строк эмитятся как одиночные {@code \n} ({@code writeCharacters("\n")}
     * / {@code BufferedWriter.newLine()} = LF на Linux). BOM пишется ДО оборачивания.
     */
    public static OutputStream wrapLfToCrlf(OutputStream out) {
        return new LfToCrlfOutputStream(out);
    }

    /**
     * Байтовый фильтр: каждый {@code 0x0A}, перед которым НЕ стоит {@code 0x0D},
     * превращается в пару {@code 0x0D 0x0A}. Уже идущий {@code \r\n} не дублируется —
     * идемпотентность на байтовом уровне (зеркало {@link #normalize(String)}).
     */
    static final class LfToCrlfOutputStream extends FilterOutputStream {
        private static final int CR = 0x0D;
        private static final int LF = 0x0A;
        private boolean prevWasCr = false;

        LfToCrlfOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            int v = b & 0xFF;
            if (v == LF && !prevWasCr) {
                out.write(CR);
                out.write(LF);
            } else {
                out.write(v);
            }
            prevWasCr = (v == CR);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }
    }
}
//++agent TASK-172

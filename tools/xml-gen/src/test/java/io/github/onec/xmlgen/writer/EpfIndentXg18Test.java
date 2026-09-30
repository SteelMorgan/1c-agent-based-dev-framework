package io.github.onec.xmlgen.writer;

import io.github.onec.xmlgen.format.OutputFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XG-18: отступы корневого EPF XML по канону Designer (эталон: выгрузка Designer
 * vanessa-automation/plugins/ЗапросыИзБД.xml): GeneratedType — 3 таба, Form в ChildObjects — 3,
 * {@code </ChildObjects>} — 2, закрывающий корень — 1; без голых LF в CRLF-файле.
 */
class EpfIndentXg18Test {

    @TempDir
    Path tempDir;

    @Test
    void epfInitAndAddForm_followDesignerIndentation() throws Exception {
        EpfWriter writer = new EpfWriter(OutputFormat.DESIGNER);
        writer.init("Обр", "Обр", tempDir);
        writer.addForm("Обр", "Форма", "Форма", tempDir, true);
        writer.addTemplate("Обр", "Макет", "Макет", "SpreadsheetDocument", tempDir);
        String xml = Files.readString(tempDir.resolve("Обр.xml"), StandardCharsets.UTF_8);

        assertThat(xml).contains("\r\n\t\t\t<xr:GeneratedType name=");
        assertThat(xml).contains("\r\n\t\t\t</xr:GeneratedType>\r\n");
        assertThat(xml).contains("\r\n\t\t<ChildObjects>\r\n\t\t\t<Form>Форма</Form>\r\n\t\t\t<Template>Макет</Template>\r\n\t\t</ChildObjects>\r\n");
        assertThat(xml).contains("\r\n\t</ExternalDataProcessor>\r\n</MetaDataObject>");
        assertThat(xml.replace("\r\n", "")).doesNotContain("\n");
    }
}

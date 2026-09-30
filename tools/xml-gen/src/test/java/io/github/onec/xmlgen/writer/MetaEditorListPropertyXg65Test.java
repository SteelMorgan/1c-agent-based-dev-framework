package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** XG-65: batch modify-property RegisterRecords давал «Modified: 1» без изменения XML. */
class MetaEditorListPropertyXg65Test {

    @TempDir
    Path tempDir;

    @Test
    void registerRecords_listIsReplacedWithMdObjectRefItems() throws Exception {
        Path xml = tempDir.resolve("Documents/big_Positions_OKX.xml");
        Files.createDirectories(xml.getParent());
        try (InputStream in = getClass().getResourceAsStream("/xg65-document.xml")) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("[ \\t]*<xr:Item xsi:type=\"xr:MDObjectRef\">AccumulationRegister\\.биг_СебестоимостьАктивов</xr:Item>\\r?\\n", "");
            Files.writeString(xml, content, StandardCharsets.UTF_8);
        }
        assertThat(Files.readString(xml)).doesNotContain("биг_СебестоимостьАктивов</xr:Item>");
        Path batch = tempDir.resolve("b.json");
        Files.writeString(batch, "{\"operations\":[{\"op\":\"modify-property\",\"name\":\"RegisterRecords\",\"value\":"
                + "[\"AccumulationRegister.биг_ПрибылиИУбытки\",\"AccumulationRegister.биг_СебестоимостьАктивов\"]}]}",
                StandardCharsets.UTF_8);

        new MetaEditor().applyBatch(xml, batch, false);

        String result = Files.readString(xml, StandardCharsets.UTF_8);
        assertThat(result).contains("\t\t\t<RegisterRecords>\r\n"
                + "\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">AccumulationRegister.биг_ПрибылиИУбытки</xr:Item>\r\n"
                + "\t\t\t\t<xr:Item xsi:type=\"xr:MDObjectRef\">AccumulationRegister.биг_СебестоимостьАктивов</xr:Item>\r\n"
                + "\t\t\t</RegisterRecords>");
        assertThat(result).contains("<RegisterRecordsDeletion>AutoDelete</RegisterRecordsDeletion>");
    }
}

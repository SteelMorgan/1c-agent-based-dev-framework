package io.github.onec.xmlgen.oracle;

import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PictureBodyOracleTest {

    @TempDir
    Path tempDir;

    @Test
    void probePreservesExtPictureXmlAndPayloadBytesAndChecksCommonPictureWrapper() throws Exception {
        Path pictureXml = tempDir.resolve("src/xml/CommonPictures/DemoPicture/Ext/Picture.xml");
        Path payload = tempDir.resolve("src/xml/CommonPictures/DemoPicture/Ext/Picture/Picture.png");
        Path wrapper = tempDir.resolve("src/xml/CommonPictures/DemoPicture.xml");
        Files.createDirectories(payload.getParent());
        Files.createDirectories(wrapper.getParent());

        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <CommonPicture uuid="00000000-0000-0000-0000-000000000001">
                    <Properties>
                      <Name>DemoPicture</Name>
                    </Properties>
                  </CommonPicture>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        Files.writeString(pictureXml, "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExtPicture xmlns="http://v8.1c.ru/8.3/xcf/extrnprops"
                            xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                            xmlns:xs="http://www.w3.org/2001/XMLSchema"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            version="2.20">
                  <Picture>
                    <xr:Abs>Picture.png</xr:Abs>
                    <xr:LoadTransparent>false</xr:LoadTransparent>
                  </Picture>
                </ExtPicture>
                """, StandardCharsets.UTF_8);
        byte[] png = new byte[]{
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n',
                0, 0, 0, '\r', 'I', 'H', 'D', 'R'
        };
        Files.write(payload, png);

        XmlDocument document = new XmlStructureReader().parse(pictureXml);
        PictureBodyOracle.Result result = new PictureBodyOracle().probe(
                tempDir.resolve("src/xml"), pictureXml, document, tempDir.resolve("oracle"));

        assertThat(result.passed()).as(result.error()).isTrue();
        assertThat(result.details())
                .containsEntry("abs", "Picture.png")
                .containsEntry("payloadFormat", "png")
                .containsEntry("payloadSize", png.length)
                .containsEntry("association", "common_picture")
                .containsEntry("wrapperMatchesName", true);
        assertThat(Files.readAllBytes(tempDir.resolve("oracle/Picture.xml")))
                .isEqualTo(Files.readAllBytes(pictureXml));
        assertThat(Files.readAllBytes(tempDir.resolve("oracle/payload/Picture.png")))
                .isEqualTo(png);
    }

    @Test
    void probeFailsWhenCommonPictureWrapperNameDoesNotMatchObjectDirectory() throws Exception {
        Path pictureXml = tempDir.resolve("src/xml/CommonPictures/DemoPicture/Ext/Picture.xml");
        Path payload = tempDir.resolve("src/xml/CommonPictures/DemoPicture/Ext/Picture/Picture.svg");
        Path wrapper = tempDir.resolve("src/xml/CommonPictures/DemoPicture.xml");
        Files.createDirectories(payload.getParent());
        Files.createDirectories(wrapper.getParent());

        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <CommonPicture uuid="00000000-0000-0000-0000-000000000001">
                    <Properties>
                      <Name>OtherPicture</Name>
                    </Properties>
                  </CommonPicture>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);
        Files.writeString(pictureXml, "\uFEFF" + """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExtPicture xmlns="http://v8.1c.ru/8.3/xcf/extrnprops"
                            xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                            version="2.20">
                  <Picture>
                    <xr:Abs>Picture.svg</xr:Abs>
                    <xr:LoadTransparent>false</xr:LoadTransparent>
                  </Picture>
                </ExtPicture>
                """, StandardCharsets.UTF_8);
        Files.writeString(payload, "<svg xmlns=\"http://www.w3.org/2000/svg\"/>", StandardCharsets.UTF_8);

        XmlDocument document = new XmlStructureReader().parse(pictureXml);
        PictureBodyOracle.Result result = new PictureBodyOracle().probe(
                tempDir.resolve("src/xml"), pictureXml, document, tempDir.resolve("oracle"));

        assertThat(result.passed()).isFalse();
        assertThat(result.error()).contains("does not match directory");
        assertThat(result.details())
                .containsEntry("payloadFormat", "svg")
                .containsEntry("wrapperMatchesName", false);
    }
}

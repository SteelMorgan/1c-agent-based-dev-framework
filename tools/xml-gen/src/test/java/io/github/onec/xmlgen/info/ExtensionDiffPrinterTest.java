package io.github.onec.xmlgen.info;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionDiffPrinterTest {

    @TempDir
    Path tempDir;

    @Test
    void modeAReportsBorrowedFormsInChildObjectsSummary() throws Exception {
        Path ext = tempDir.resolve("ext");
        write(ext.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Ext</Name><NamePrefix>Ext_</NamePrefix></Properties>\n"
                        + "\t\t<ChildObjects><Catalog>X</Catalog></ChildObjects>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        write(ext.resolve("Catalogs/X.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Catalog uuid=\"00000000-0000-0000-0000-000000000002\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>X</Name>\n"
                        + "\t\t\t<ExtendedConfigurationObject>00000000-0000-0000-0000-000000000003</ExtendedConfigurationObject>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects><Form>ФормаЭлемента</Form></ChildObjects>\n"
                        + "\t</Catalog>\n"
                        + "</MetaDataObject>\n");
        write(ext.resolve("Catalogs/X/Forms/ФормаЭлемента.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Form uuid=\"00000000-0000-0000-0000-000000000004\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<ObjectBelonging>Adopted</ObjectBelonging>\n"
                        + "\t\t\t<Name>ФормаЭлемента</Name>\n"
                        + "\t\t\t<ExtendedConfigurationObject>00000000-0000-0000-0000-000000000005</ExtendedConfigurationObject>\n"
                        + "\t\t</Properties>\n"
                        + "\t</Form>\n"
                        + "</MetaDataObject>\n");
        write(ext.resolve("Catalogs/X/Forms/ФормаЭлемента/Ext/Form.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.20\">\n"
                        + "\t<BaseForm/>\n"
                        + "</Form>\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new ExtensionDiffPrinter(new PrintStream(out, true, StandardCharsets.UTF_8))
                .diff(ext, null, "A");

        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("ChildObjects: 1 borrowed forms");
        assertThat(output).contains("Form.ФормаЭлемента (borrowed)");
        assertThat(output).doesNotContain("own forms");
    }

    private void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}

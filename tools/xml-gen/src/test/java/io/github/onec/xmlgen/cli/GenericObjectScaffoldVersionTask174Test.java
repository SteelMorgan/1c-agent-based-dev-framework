package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GenericObjectScaffoldVersionTask174Test {

    @TempDir
    Path tempDir;

    private Path createCatalog(String name, String formatVersion) throws Exception {
        Path catalogsDir = tempDir.resolve("src/Catalogs");
        Files.createDirectories(catalogsDir);
        Path catalogXml = catalogsDir.resolve(name + ".xml");
        Files.writeString(catalogXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\""
                        + formatVersion + "\">\n"
                        + "\t<Catalog uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties>\n"
                        + "\t\t\t<Name>" + name + "</Name>\n"
                        + "\t\t\t<DefaultForm/>\n"
                        + "\t\t</Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Catalog>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        return catalogXml;
    }

    @Test
    void formAddInheritsParentObjectFormatVersion() throws Exception {
        Path catalogXml = createCatalog("Контрагенты", "2.20");

        Commands.execute("form", new String[]{
                "add", catalogXml.toString(), "ФормаЭлемента", "--default"
        });

        String formMeta = Files.readString(
                tempDir.resolve("src/Catalogs/Контрагенты/Forms/ФормаЭлемента.xml"),
                StandardCharsets.UTF_8);
        String formXml = Files.readString(
                tempDir.resolve("src/Catalogs/Контрагенты/Forms/ФормаЭлемента/Ext/Form.xml"),
                StandardCharsets.UTF_8);

        assertThat(formMeta).contains("version=\"2.20\"");
        assertThat(formXml)
                .contains("version=\"2.20\"")
                .contains("<v8:Type>cfg:CatalogObject.Контрагенты</v8:Type>");
    }

    @Test
    void templateAddByObjectInheritsParentObjectFormatVersion() throws Exception {
        createCatalog("Контрагенты", "2.20");

        Commands.execute("template", new String[]{
                "add", "--object", "Catalog.Контрагенты", "--name", "ПФ_Печать",
                "--type", "SpreadsheetDocument", tempDir.toString()
        });

        String templateMeta = Files.readString(
                tempDir.resolve("src/Catalogs/Контрагенты/Templates/ПФ_Печать.xml"),
                StandardCharsets.UTF_8);

        assertThat(templateMeta).contains("version=\"2.20\"");
    }

    @Test
    void templateAddHelpInheritsParentObjectFormatVersion() throws Exception {
        createCatalog("Контрагенты", "2.20");

        Commands.execute("template", new String[]{
                "add-help", "--object", "Catalog.Контрагенты", tempDir.toString()
        });

        String helpXml = Files.readString(
                tempDir.resolve("src/Catalogs/Контрагенты/Ext/Help.xml"),
                StandardCharsets.UTF_8);

        assertThat(helpXml).contains("version=\"2.20\"");
    }
}

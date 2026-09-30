package io.github.onec.xmlgen.oracle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CanonicalRuleMinerTest {

    @TempDir
    Path tempDir;

    @Test
    void minesRuleCandidatesFromCanonicalXmlCorpus() throws Exception {
        writeFormFixture("Catalogs/Goods/Forms/ItemForm", "GoodsItemForm", "InputField");
        writeFormFixture("Catalogs/Partners/Forms/ItemForm", "PartnersItemForm", "InputField");

        RuleMiningReport report = new CanonicalRuleMiner().mine(tempDir.resolve("src/xml"), 0, 2);

        assertThat(report.filesParsed()).isEqualTo(4);
        assertThat(report.buckets()).containsKeys("FormWrapper", "FormBody");
        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo("LINKED_BODY");
            assertThat(c.bucket()).isEqualTo("FormWrapper");
            assertThat(c.support()).isEqualTo(2);
            assertThat(c.details()).containsEntry("missing", 0);
        });
        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo("ROOT_CONTRACT");
            assertThat(c.bucket()).isEqualTo("FormBody");
            assertThat(c.details()).containsEntry("root", "Form");
        });
        assertThat(report.candidates()).anyMatch(c ->
                c.kind().equals("REQUIRED_CHILD") && c.subject().equals("/Form/Items"));
        assertThat(report.candidates()).anyMatch(c ->
                c.kind().equals("VALUE_DOMAIN") && c.subject().equals("/Form/Items/Item/Type"));

        RuleDigest digest = new RuleCandidateReducer().reduce(report, 25);
        assertThat(digest.digestCount()).isLessThanOrEqualTo(25);
        assertThat(digest.bundles()).anyMatch(b ->
                b.bucket().equals("FormWrapper") && b.kinds().contains("LINKED_BODY"));
    }

    @Test
    void minesDiscriminatorBodyRulesForFormsAndTemplates() throws Exception {
        writeFormFixture("Catalogs/Goods/Forms/ManagedForm", "ManagedForm", "InputField");
        writeOrdinaryFormWrapper("Catalogs/Goods/Forms/OrdinaryForm", "OrdinaryForm");
        writeTemplateFixture("Catalogs/Goods/Templates/Mxl", "Mxl", "SpreadsheetDocument", "Template.xml");
        writeTemplateFixture("Catalogs/Goods/Templates/Text", "Text", "TextDocument", "Template.txt");
        writeTemplateFixture("Catalogs/Goods/Templates/Binary", "Binary", "BinaryData", "Template.bin");

        RuleMiningReport report = new CanonicalRuleMiner().mine(tempDir.resolve("src/xml"), 0, 1);

        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo("DISCRIMINATOR_LINKED_BODY");
            assertThat(c.subject()).isEqualTo("FormType=Managed -> Ext/Form.xml");
            assertThat(c.support()).isEqualTo(1);
        });
        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.kind()).isEqualTo("DISCRIMINATOR_LINKED_BODY");
            assertThat(c.subject()).isEqualTo("FormType=Ordinary -> none");
            assertThat(c.support()).isEqualTo(1);
        });
        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.subject()).isEqualTo("TemplateType=TextDocument -> Ext/Template.txt");
            assertThat(c.details()).containsEntry("body", "Ext/Template.txt");
        });
        assertThat(report.candidates()).anySatisfy(c -> {
            assertThat(c.subject()).isEqualTo("TemplateType=BinaryData -> Ext/Template.bin");
            assertThat(c.details()).containsEntry("body", "Ext/Template.bin");
        });
    }

    private void writeFormFixture(String basePath, String formName, String itemType) throws Exception {
        Path wrapper = tempDir.resolve("src/xml").resolve(basePath + ".xml");
        Path body = tempDir.resolve("src/xml").resolve(basePath).resolve("Ext/Form.xml");
        Files.createDirectories(wrapper.getParent());
        Files.createDirectories(body.getParent());
        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Form uuid="00000000-0000-0000-0000-000000000001">
                    <Properties>
	                      <Name>%s</Name>
	                      <Synonym/>
	                      <Comment/>
	                      <FormType>Managed</FormType>
	                      <IncludeHelpInContents>false</IncludeHelpInContents>
	                    </Properties>
                  </Form>
                </MetaDataObject>
                """.formatted(formName), StandardCharsets.UTF_8);
        Files.writeString(body, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form xmlns="http://v8.1c.ru/8.3/xcf/logform"
                      xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                      version="2.20">
                  <Items>
                    <Item>
                      <Name>Field%s</Name>
                      <Type>%s</Type>
                      <DataPath>Object.Name</DataPath>
                    </Item>
                  </Items>
                </Form>
                """.formatted(formName, itemType), StandardCharsets.UTF_8);
    }

    private void writeOrdinaryFormWrapper(String basePath, String formName) throws Exception {
        Path wrapper = tempDir.resolve("src/xml").resolve(basePath + ".xml");
        Files.createDirectories(wrapper.getParent());
        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Form uuid="00000000-0000-0000-0000-000000000002">
                    <Properties>
                      <Name>%s</Name>
                      <FormType>Ordinary</FormType>
                      <IncludeHelpInContents>false</IncludeHelpInContents>
                    </Properties>
                  </Form>
                </MetaDataObject>
                """.formatted(formName), StandardCharsets.UTF_8);
    }

    private void writeTemplateFixture(String basePath, String name, String templateType, String bodyFile)
            throws Exception {
        Path wrapper = tempDir.resolve("src/xml").resolve(basePath + ".xml");
        Path body = tempDir.resolve("src/xml").resolve(basePath).resolve("Ext").resolve(bodyFile);
        Files.createDirectories(wrapper.getParent());
        Files.createDirectories(body.getParent());
        Files.writeString(wrapper, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Template uuid="00000000-0000-0000-0000-000000000003">
                    <Properties>
                      <Name>%s</Name>
                      <TemplateType>%s</TemplateType>
                    </Properties>
                  </Template>
                </MetaDataObject>
                """.formatted(name, templateType), StandardCharsets.UTF_8);
        Files.writeString(body, bodyFile.endsWith(".xml") ? """
                <?xml version="1.0" encoding="UTF-8"?>
                <document xmlns="http://v8.1c.ru/8.2/spreadsheet" version="2.20"/>
                """ : "payload", StandardCharsets.UTF_8);
    }
}

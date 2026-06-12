package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EdtDerivedInvariantCheckerTest {

    @TempDir
    Path tempDir;

    private final XmlStructureReader reader = new XmlStructureReader();
    private final EdtDerivedInvariantChecker checker = new EdtDerivedInvariantChecker();

    @Test
    void reportsFormItemIdAndNameIssuesDerivedFromDtProjectChecks() throws Exception {
        Path form = tempDir.resolve("Form.xml");
        Files.writeString(form, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Form xmlns="http://v8.1c.ru/8.3/xcf/logform">
                  <AutoCommandBar id="-1"/>
                  <Attributes>
                    <Attribute name="1BadAttribute" id="1"/>
                  </Attributes>
                  <ChildItems>
                    <InputField name="Поле" id="1">
                      <ContextMenu name="1BadMenu" id="2"/>
                    </InputField>
                    <InputField name="ДругоеПоле" id="1"/>
                    <InputField name="Нулевой" id="0"/>
                    <InputField id="3"/>
                  </ChildItems>
                </Form>
                """, StandardCharsets.UTF_8);

        EdtDerivedInvariantChecker.Result result = checker.check(reader.parse(form), null);

        assertThat(codes(result.issues())).contains(
                "EDT-FORM-NAME-002",
                "EDT-FORM-NAME-004",
                "EDT-FORM-ID-005",
                "EDT-FORM-ID-003",
                "EDT-FORM-NAME-003");
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.toDetails()).containsKey("summary");
    }

    @Test
    void checksConfigurationReferencesAgainstDesignerSourceTree() throws Exception {
        Path sourceRoot = tempDir.resolve("src/xml");
        Files.createDirectories(sourceRoot.resolve("Catalogs"));
        Files.writeString(sourceRoot.resolve("Catalogs/Existing.xml"), """
                <MetaDataObject><Catalog uuid="00000000-0000-0000-0000-000000000001">
                  <Properties><Name>Existing</Name></Properties>
                </Catalog></MetaDataObject>
                """, StandardCharsets.UTF_8);
        Path configuration = sourceRoot.resolve("Configuration.xml");
        Files.writeString(configuration, """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses">
                  <Configuration uuid="00000000-0000-0000-0000-000000000000">
                    <Properties><Name>Cfg</Name></Properties>
                    <ChildObjects>
                      <Catalog>Existing</Catalog>
                      <Document>MissingDocument</Document>
                    </ChildObjects>
                  </Configuration>
                </MetaDataObject>
                """, StandardCharsets.UTF_8);

        EdtDerivedInvariantChecker.Result result = checker.check(reader.parse(configuration), sourceRoot);

        assertThat(codes(result.issues())).containsExactly("EDT-MD-REF-001");
        assertThat(result.issues().get(0).getMessage()).contains("Document.MissingDocument");
    }

    @Test
    void checksExchangePlanContentMetadataReferences() throws Exception {
        Path sourceRoot = tempDir.resolve("src/xml");
        Files.createDirectories(sourceRoot);
        Path content = tempDir.resolve("Content.xml");
        Files.writeString(content, """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExchangePlanContent xmlns="http://v8.1c.ru/8.3/xcf/extrnprops">
                  <Item>
                    <Metadata>Catalog.Missing</Metadata>
                    <AutoRecord>true</AutoRecord>
                  </Item>
                </ExchangePlanContent>
                """, StandardCharsets.UTF_8);

        EdtDerivedInvariantChecker.Result result = checker.check(reader.parse(content), sourceRoot);

        assertThat(codes(result.issues())).containsExactly("EDT-MD-REF-003");
    }

    private List<String> codes(List<ValidationIssue> issues) {
        return issues.stream().map(ValidationIssue::getCode).toList();
    }
}

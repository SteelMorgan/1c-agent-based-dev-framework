package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionValidateCliContractTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("integr-extension validate reports parseable role Rights.xml semantic errors")
    void integrExtensionValidateReportsRoleRightsSemanticErrors() throws Exception {
        Path ext = tempDir.resolve("role-rights-extension");
        writeBaseExtension(ext, """
                        <Language>Русский</Language>
                        <Role>ОсновнаяРоль</Role>
                """);
        writeLanguage(ext);
        writeRole(ext, "ОсновнаяРоль");
        writeRoleRights(ext, "ОсновнаяРоль", """
                    <object>
                        <name>Catalog.Товары</name>
                        <right>
                            <name>DefinitelyUnknownRight</name>
                            <value>true</value>
                        </right>
                    </object>
                """);

        ProcessResult result = runMain("extension", "validate", ext.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput())
                .contains("ERROR")
                .contains("Roles/ОсновнаяРоль/Ext/Rights.xml")
                .contains("DefinitelyUnknownRight");
    }

    @Test
    @DisplayName("integr-extension validate reports borrowed child Attribute without ExtendedConfigurationObject")
    void integrExtensionValidateReportsBorrowedChildAttributeWithoutExtendedConfigurationObject() throws Exception {
        Path ext = tempDir.resolve("child-attribute-extension");
        writeBaseExtension(ext, """
                        <Language>Русский</Language>
                        <Catalog>Товары</Catalog>
                """);
        writeLanguage(ext);
        writeCatalogWithChildObjects(ext, """
                    <ChildObjects>
                        <Attribute uuid="33333333-3333-3333-3333-333333333333">
                            <Properties>
                                <ObjectBelonging>Adopted</ObjectBelonging>
                                <Name>Артикул</Name>
                            </Properties>
                        </Attribute>
                    </ChildObjects>
                """);

        ProcessResult result = runMain("extension", "validate", ext.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput())
                .contains("ERROR")
                .contains("Catalogs/Товары.xml")
                .contains("Attribute.Артикул")
                .contains("ExtendedConfigurationObject");
    }

    @Test
    @DisplayName("integr-extension validate reports borrowed form metadata without ExtendedConfigurationObject")
    void integrExtensionValidateReportsBorrowedFormMetadataWithoutExtendedConfigurationObject() throws Exception {
        Path ext = tempDir.resolve("borrowed-form-extension");
        writeBaseExtension(ext, """
                        <Language>Русский</Language>
                        <Catalog>Товары</Catalog>
                """);
        writeLanguage(ext);
        writeCatalogWithChildObjects(ext, """
                    <ChildObjects>
                        <Form>ФормаЭлемента</Form>
                    </ChildObjects>
                """);
        writeXml(ext.resolve("Catalogs/Товары/Forms/ФормаЭлемента.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Form uuid="44444444-4444-4444-4444-444444444444">
                        <InternalInfo/>
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>ФормаЭлемента</Name>
                            <FormType>Managed</FormType>
                        </Properties>
                    </Form>
                </MetaDataObject>
                """);

        ProcessResult result = runMain("extension", "validate", ext.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isEqualTo(1);
        assertThat(result.combinedOutput())
                .contains("ERROR")
                .contains("Catalogs/Товары/Forms/ФормаЭлемента.xml")
                .contains("Form.ФормаЭлемента")
                .contains("ExtendedConfigurationObject");
    }

    @Test
    @DisplayName("integr-extension validate accepts borrowed child and form with ExtendedConfigurationObject")
    void integrExtensionValidateAcceptsBorrowedChildAndFormWithExtendedConfigurationObject() throws Exception {
        Path ext = tempDir.resolve("valid-borrowed-children-extension");
        writeBaseExtension(ext, """
                        <Language>Русский</Language>
                        <Catalog>Товары</Catalog>
                """);
        writeLanguage(ext);
        writeCatalogWithChildObjects(ext, """
                    <ChildObjects>
                        <Attribute uuid="33333333-3333-3333-3333-333333333333">
                            <Properties>
                                <ObjectBelonging>Adopted</ObjectBelonging>
                                <Name>Артикул</Name>
                                <ExtendedConfigurationObject>33333333-3333-3333-3333-333333333334</ExtendedConfigurationObject>
                            </Properties>
                        </Attribute>
                        <Form>ФормаЭлемента</Form>
                    </ChildObjects>
                """);
        writeXml(ext.resolve("Catalogs/Товары/Forms/ФормаЭлемента.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Form uuid="44444444-4444-4444-4444-444444444444">
                        <InternalInfo/>
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>ФормаЭлемента</Name>
                            <ExtendedConfigurationObject>44444444-4444-4444-4444-444444444445</ExtendedConfigurationObject>
                            <FormType>Managed</FormType>
                        </Properties>
                    </Form>
                </MetaDataObject>
                """);

        ProcessResult result = runMain("extension", "validate", ext.toString());

        assertThat(result.exitCode()).as(result.combinedOutput()).isNotEqualTo(1);
        assertThat(result.combinedOutput()).doesNotContain("ERROR");
    }

    private void writeBaseExtension(Path ext, String childObjects) throws Exception {
        writeXml(ext.resolve("Configuration.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                                xmlns:xr="http://v8.1c.ru/8.3/xcf/readable"
                                version="2.20">
                    <Configuration uuid="00000000-0000-0000-0000-000000000099">
                %s
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>Расширение</Name>
                            <ConfigurationExtensionPurpose>Customization</ConfigurationExtensionPurpose>
                            <KeepMappingToExtendedConfigurationObjectsByIDs>true</KeepMappingToExtendedConfigurationObjectsByIDs>
                            <NamePrefix>Расш_</NamePrefix>
                            <ConfigurationExtensionCompatibilityMode>Version8_3_24</ConfigurationExtensionCompatibilityMode>
                            <ScriptVariant>Russian</ScriptVariant>
                            <DefaultLanguage>Language.Русский</DefaultLanguage>
                        </Properties>
                        <ChildObjects>
                %s
                        </ChildObjects>
                    </Configuration>
                </MetaDataObject>
                """.formatted(internalInfo(), childObjects));
    }

    private String internalInfo() {
        return """
                        <InternalInfo>
                            <ContainedObject>
                                <ClassId>9cd510cd-abfc-11d4-9434-004095e12fc7</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000001</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>9fcd25a0-4822-11d4-9414-008048da11f9</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000002</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>e3687481-0a87-462c-a166-9f34594f9bba</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000003</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>9de14907-ec23-4a07-96f0-85521cb6b53b</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000004</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>51f2d5d8-ea4d-4064-8892-82951750031e</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000005</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>e68182ea-4237-4383-967f-90c1e3370bc7</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000006</ObjectId>
                            </ContainedObject>
                            <ContainedObject>
                                <ClassId>fb282519-d103-4dd3-bc12-cb271d631dfc</ClassId>
                                <ObjectId>00000000-0000-0000-0000-000000000007</ObjectId>
                            </ContainedObject>
                        </InternalInfo>
                """;
    }

    private void writeLanguage(Path ext) throws Exception {
        writeXml(ext.resolve("Languages/Русский.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Language uuid="11111111-1111-1111-1111-111111111111">
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>Русский</Name>
                            <ExtendedConfigurationObject>11111111-1111-1111-1111-111111111112</ExtendedConfigurationObject>
                        </Properties>
                    </Language>
                </MetaDataObject>
                """);
    }

    private void writeRole(Path ext, String roleName) throws Exception {
        writeXml(ext.resolve("Roles/" + roleName + ".xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Role uuid="22222222-2222-2222-2222-222222222222">
                        <Properties>
                            <Name>%s</Name>
                        </Properties>
                    </Role>
                </MetaDataObject>
                """.formatted(roleName));
    }

    private void writeRoleRights(Path ext, String roleName, String objectBlocks) throws Exception {
        writeXml(ext.resolve("Roles/" + roleName + "/Ext/Rights.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Rights xmlns="http://v8.1c.ru/8.2/roles"
                        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        xsi:type="Rights"
                        version="2.20">
                    <setForNewObjects>false</setForNewObjects>
                    <setForAttributesByDefault>true</setForAttributesByDefault>
                    <independentRightsOfChildObjects>false</independentRightsOfChildObjects>
                %s
                </Rights>
                """.formatted(objectBlocks));
    }

    private void writeCatalogWithChildObjects(Path ext, String childObjects) throws Exception {
        writeXml(ext.resolve("Catalogs/Товары.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                    <Catalog uuid="33333333-3333-3333-3333-333333333330">
                        <InternalInfo/>
                        <Properties>
                            <ObjectBelonging>Adopted</ObjectBelonging>
                            <Name>Товары</Name>
                            <ExtendedConfigurationObject>33333333-3333-3333-3333-333333333331</ExtendedConfigurationObject>
                        </Properties>
                %s
                    </Catalog>
                </MetaDataObject>
                """.formatted(childObjects));
    }

    private ProcessResult runMain(String... args) throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Main.class.getName());
        command.addAll(List.of(args));

        Process process = new ProcessBuilder(command)
                .directory(tempDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        boolean exited = process.waitFor(10, TimeUnit.SECONDS);
        if (!exited) {
            process.destroyForcibly();
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(exited).as(stdout + stderr).isTrue();
        return new ProcessResult(process.exitValue(), stdout, stderr);
    }

    private void writeXml(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private record ProcessResult(int exitCode, String stdout, String stderr) {

        String combinedOutput() {
            return stdout + stderr;
        }
    }
}

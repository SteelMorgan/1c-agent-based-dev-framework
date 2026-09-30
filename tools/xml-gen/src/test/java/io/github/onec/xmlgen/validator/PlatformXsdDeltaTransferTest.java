package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformXsdDeltaTransferTest {

    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    @Test
    void importsAllXsdOnlyDeltaFactsFromReport() {
        assertThat(PlatformXsdFacts.XSD_ONLY_NAMESPACES).containsExactlyInAnyOrder(
                "http://v8.1c.ru/8.2/data/bsl",
                "http://v8.1c.ru/8.2/managed-application/modules",
                "http://v8.1c.ru/8.2/uobjects");

        assertThat(PlatformXsdFacts.XSD_GLOBAL_ELEMENTS_IMPORTED_FROM_DELTA)
                .containsExactlyInAnyOrder("ClientApplicationInterface", "section");

        assertThat(PlatformXsdFacts.REQUIRED_ATTRIBUTES_IMPORTED_FROM_DELTA)
                .containsAll(Set.of(
                        "clsid", "count", "delta", "formatVersion", "helpTopic", "index1",
                        "index2", "itemType", "lastId", "md", "nameInt", "nameRus",
                        "pattern", "remoteKey", "seq", "seqDe", "seqUo", "sin",
                        "sinDe", "sinUo", "startId", "total", "trackChanges", "url"));

        assertThat(PlatformXsdFacts.REQUIRED_ELEMENTS_IMPORTED_FROM_DELTA)
                .containsExactlyInAnyOrder(
                        "longAloneMonthNames", "longDayNames", "longMonthNames",
                        "shortAloneMonthNames", "shortDayNames", "shortMonthNames");
    }

    @Test
    void validatesObservedClientApplicationInterface() throws Exception {
        Path file = write("ClientApplicationInterface.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <ClientApplicationInterface xmlns="http://v8.1c.ru/8.2/managed-application/core"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="InterfaceLayouter">
                    <left>
                        <group id="d24b61e4-1bf6-4b60-ad05-532cd68073ce">
                            <group>
                                <panel id="3ac4273b-d162-491f-9e7f-c92c86446562">
                                    <uuid>8e10648b-f52d-4ec2-b4dd-87de33778d95</uuid>
                                </panel>
                            </group>
                        </group>
                    </left>
                    <panelDef id="8e10648b-f52d-4ec2-b4dd-87de33778d95"/>
                </ClientApplicationInterface>
                """);

        XmlDocument doc = reader.parse(file);
        ClientApplicationInterfaceValidator validator = new ClientApplicationInterfaceValidator();
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(validator.supports(doc)).isTrue();
        assertThat(issues).noneMatch(i -> i.getSeverity() == Severity.ERROR);
    }

    @Test
    void warnsWhenClientInterfacePanelReferencesMissingPanelDef() throws Exception {
        Path file = write("ClientApplicationInterface.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <ClientApplicationInterface xmlns="http://v8.1c.ru/8.2/managed-application/core"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="InterfaceLayouter">
                    <left><panel id="3ac4273b-d162-491f-9e7f-c92c86446562"><uuid>missing-panel</uuid></panel></left>
                </ClientApplicationInterface>
                """);

        List<ValidationIssue> issues = new ClientApplicationInterfaceValidator()
                .validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("CLIENT-IFACE-011")
                && i.getSeverity() == Severity.WARNING);
    }

    @Test
    void validatesXsdOnlyCmiSectionRequiredAttributes() throws Exception {
        Path file = write("section.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <section xmlns="http://v8.1c.ru/8.2/managed-application/cmi" name="Main">
                    <command id="cmd-1" nameInt="Open" md="Catalog.Items"/>
                </section>
                """);

        XmlDocument doc = reader.parse(file);
        PlatformXsdHintValidator validator = new PlatformXsdHintValidator();
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(validator.supports(doc)).isTrue();
        assertThat(issues).anyMatch(i -> i.getMessage().contains("required attribute 'id'"));
        assertThat(issues).anyMatch(i -> i.getMessage().contains("required attribute 'helpTopic'"));
        assertThat(issues).anyMatch(i -> i.getMessage().contains("required attribute 'nameRus'"));
        assertThat(issues).anyMatch(i -> i.getMessage().contains("required attribute 'url'"));
    }

    @Test
    void recognizesXsdOnlyNamespacesWithoutFailingValidation() throws Exception {
        Path file = write("uobjects.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <uobject xmlns="http://v8.1c.ru/8.2/uobjects"/>
                """);

        XmlDocument doc = reader.parse(file);
        PlatformXsdHintValidator validator = new PlatformXsdHintValidator();
        List<ValidationIssue> issues = validator.validate(doc, ValidationLevel.SEMANTIC);

        assertThat(validator.supports(doc)).isTrue();
        assertThat(issues).anyMatch(i -> i.getCode().equals("PLATFORM-XSD-001")
                && i.getSeverity() == Severity.INFO);
    }

    private Path write(String fileName, String content) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

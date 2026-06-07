package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MetadataTypeValidatorTask174Round3Test {

    @TempDir
    Path tempDir;

    @Test
    void validatesAllPipeSeparatedReferenceTypes() throws Exception {
        Files.createDirectories(tempDir.resolve("Catalogs"));
        Files.writeString(tempDir.resolve("Catalogs/Есть.xml"), "<MetaDataObject/>");

        MetadataTypeValidator validator = new MetadataTypeValidator(tempDir);
        XmlNode typeNode = XmlNode.builder()
                .name("Type")
                .appendText("cfg:CatalogRef.Есть | cfg:CatalogRef.Нет")
                .line(7)
                .build();

        List<ValidationIssue> issues = validator.validateType(typeNode.getText(), typeNode, "/Type");

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.getCode()).isEqualTo("SEM-001");
            assertThat(issue.getMessage()).contains("CatalogRef.Нет");
            assertThat(issue.getLine()).isEqualTo(7);
        });
    }

    @Test
    void acceptsD5p1PrefixInCompositeReferenceTypes() throws Exception {
        Files.createDirectories(tempDir.resolve("Catalogs"));
        Files.writeString(tempDir.resolve("Catalogs/Контрагенты.xml"), "<MetaDataObject/>");
        Files.writeString(tempDir.resolve("Catalogs/Организации.xml"), "<MetaDataObject/>");

        MetadataTypeValidator validator = new MetadataTypeValidator(tempDir);
        XmlNode typeNode = XmlNode.builder()
                .name("Type")
                .appendText("d5p1:CatalogRef.Контрагенты | d5p1:CatalogRef.Организации")
                .line(3)
                .build();

        assertThat(validator.validateType(typeNode.getText(), typeNode, "/Type")).isEmpty();
    }
}

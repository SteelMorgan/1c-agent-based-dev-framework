package io.github.onec.xmlgen.form.fromobject;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class PurposeResolverTest {

    @Test
    void catalogListForm() {
        Path p = Paths.get("/repo/src/Catalogs/Контрагенты/Forms/ФормаСписка/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "Catalog")).isEqualTo("list");
    }

    @Test
    void documentItemForm() {
        Path p = Paths.get("/repo/src/Documents/Продажи/Forms/ФормаДокумента/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "Document")).isEqualTo("item");
    }

    @Test
    void catalogChoiceForm() {
        Path p = Paths.get("/repo/src/Catalogs/Номенклатура/Forms/ФормаВыбора/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "Catalog")).isEqualTo("choice");
    }

    @Test
    void catalogFolderForm() {
        Path p = Paths.get("/repo/src/Catalogs/Номенклатура/Forms/ФормаГруппы/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "Catalog")).isEqualTo("folder");
    }

    @Test
    void informationRegisterRecordForm() {
        Path p = Paths.get("/repo/InformationRegisters/Курсы/Forms/ФормаЗаписи/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "InformationRegister")).isEqualTo("record");
    }

    @Test
    void unknownFormNameFallsToDefault() {
        Path p = Paths.get("/repo/Catalogs/X/Forms/SomeCustom/Ext/Form.xml");
        assertThat(PurposeResolver.resolve(p, "Catalog")).isEqualTo("item");
    }

    @Test
    void extractObjectAndType() {
        Path p = Paths.get("/repo/Catalogs/Контрагенты/Forms/ФормаСписка/Ext/Form.xml");
        assertThat(PurposeResolver.extractObjectName(p)).isEqualTo("Контрагенты");
        assertThat(PurposeResolver.extractTypePlural(p)).isEqualTo("Catalogs");
    }
}

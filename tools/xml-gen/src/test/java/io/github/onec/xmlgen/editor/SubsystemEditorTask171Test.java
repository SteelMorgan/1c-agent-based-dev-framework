package io.github.onec.xmlgen.editor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * TASK-171: регрессия на root-resolution и нормализацию типов в {@link SubsystemEditor}.
 *
 * <p>Корень дефекта: addContent → checkTargetObjectExists резолвил объект от
 * filePath.getParent() (= Subsystems/), промахиваясь мимо корня конфигурации, и ложно
 * падал на существующих объектах. Особо важен кейс вложенной подсистемы
 * (Parent/Subsystems/Child.xml), где до корня нужно подняться на 3 уровня.
 */
class SubsystemEditorTask171Test {

    @TempDir
    Path tempDir;

    private static final String SUBSYSTEM_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
            + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.17\">\n"
            + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000001\">\n"
            + "\t\t<Properties>\n"
            + "\t\t\t<Name>Тест</Name>\n"
            + "\t\t\t<Content/>\n"
            + "\t\t</Properties>\n"
            + "\t\t<ChildObjects/>\n"
            + "\t</Subsystem>\n"
            + "</MetaDataObject>\n";

    private static final String SUBSYSTEM_XML_220_WITH_PICTURE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
            + "\txmlns:v8=\"http://v8.1c.ru/8.1/data/core\"\n"
            + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
            + "\txmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" version=\"2.20\">\n"
            + "\t<Subsystem uuid=\"00000000-0000-0000-0000-000000000001\">\n"
            + "\t\t<Properties>\n"
            + "\t\t\t<Name>Тест</Name>\n"
            + "\t\t\t<Picture/>\n"
            + "\t\t\t<Content/>\n"
            + "\t\t</Properties>\n"
            + "\t\t<ChildObjects/>\n"
            + "\t</Subsystem>\n"
            + "</MetaDataObject>\n";

    private Path configRoot() throws IOException {
        Path root = tempDir.resolve("src").resolve("xml");
        Files.createDirectories(root.resolve("Catalogs"));
        Files.writeString(root.resolve("Configuration.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("Catalogs").resolve("УчетныеЗаписиЭлектроннойПочты.xml"),
                "<?xml version=\"1.0\"?><MetaDataObject/>", StandardCharsets.UTF_8);
        return root;
    }

    @Test
    void addContent_topLevelSubsystem_existingObject_doesNotThrow() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        // Объект существует в src/xml/Catalogs/ — добавление НЕ должно падать.
        assertThatCode(() -> editor.addContent("Catalog.УчетныеЗаписиЭлектроннойПочты"))
                .doesNotThrowAnyException();
    }

    @Test
    void addContent_nestedSubsystem_existingObject_doesNotThrow() throws Exception {
        // Критический кейс walk-up: Parent/Subsystems/Child.xml → до корня 3 уровня.
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Родитель")
                .resolve("Subsystems").resolve("Ребёнок.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        assertThatCode(() -> editor.addContent("Catalog.УчетныеЗаписиЭлектроннойПочты"))
                .doesNotThrowAnyException();
    }

    @Test
    void addContent_missingObject_stillThrows() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        // existence-чек должен остаться: несуществующий объект → ошибка.
        assertThatThrownBy(() -> editor.addContent("Catalog.НетТакого"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void addContent_normalizesTypeToCanonical() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        // Подаём plural-English "Catalogs.X" — должно записаться каноническое "Catalog.X".
        editor.addContent("Catalogs.УчетныеЗаписиЭлектроннойПочты");
        editor.save();
        String written = Files.readString(ssFile, StandardCharsets.UTF_8);
        assertThat(written).contains("Catalog.УчетныеЗаписиЭлектроннойПочты");
        assertThat(written).doesNotContain("Catalogs.УчетныеЗаписиЭлектроннойПочты");
    }

    @Test
    void removeContent_normalizesTypeToCanonical() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        editor.addContent("Catalogs.УчетныеЗаписиЭлектроннойПочты");
        editor.removeContent("Справочник.УчетныеЗаписиЭлектроннойПочты");
        editor.save();

        String written = Files.readString(ssFile, StandardCharsets.UTF_8);
        assertThat(written).doesNotContain("Catalog.УчетныеЗаписиЭлектроннойПочты");
    }

    @Test
    void normalizeContentType_pluralAndRussian() {
        assertThat(SubsystemEditor.normalizeContentType("Catalogs.Товары"))
                .isEqualTo("Catalog.Товары");
        assertThat(SubsystemEditor.normalizeContentType("Справочник.Товары"))
                .isEqualTo("Catalog.Товары");
        assertThat(SubsystemEditor.normalizeContentType("Документы.Заказ"))
                .isEqualTo("Document.Заказ");
        // Уже канонический — без изменений.
        assertThat(SubsystemEditor.normalizeContentType("Catalog.Товары"))
                .isEqualTo("Catalog.Товары");
        // Неизвестный тип — без изменений.
        assertThat(SubsystemEditor.normalizeContentType("Неизвестный.X"))
                .isEqualTo("Неизвестный.X");
    }

    @Test
    void addChild_stubInheritsSubsystemFormatVersion() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML_220_WITH_PICTURE, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        editor.addChild("Ребёнок");
        editor.save();

        String child = Files.readString(
                root.resolve("Subsystems/Тест/Subsystems/Ребёнок.xml"), StandardCharsets.UTF_8);
        assertThat(child).contains("version=\"2.20\"");
        assertThat(child).doesNotContain("version=\"2.17\"");
    }

    @Test
    void setPicture_writesCanonicalLoadTransparentFlag() throws Exception {
        Path root = configRoot();
        Path ssFile = root.resolve("Subsystems").resolve("Тест.xml");
        Files.createDirectories(ssFile.getParent());
        Files.writeString(ssFile, SUBSYSTEM_XML_220_WITH_PICTURE, StandardCharsets.UTF_8);

        SubsystemEditor editor = new SubsystemEditor(ssFile);
        editor.setProperty("Picture=CommonPicture.big_btc");
        editor.save();

        String written = Files.readString(ssFile, StandardCharsets.UTF_8);
        assertThat(written).contains("<xr:Ref>CommonPicture.big_btc</xr:Ref>");
        assertThat(written).contains("<xr:LoadTransparent>false</xr:LoadTransparent>");
    }
}

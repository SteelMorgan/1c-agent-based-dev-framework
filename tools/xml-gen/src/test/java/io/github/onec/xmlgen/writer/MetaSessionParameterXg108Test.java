package io.github.onec.xmlgen.writer;

//++agent TASK-174 XG-108 [20.07.2026 00:00:00]

import io.github.onec.xmlgen.validator.MetaValidator;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регрессия XG-108: meta compile должен поддерживать класс SessionParameter,
 * а не только упоминать его в общем реестре и Configuration.xml editor.
 */
class MetaSessionParameterXg108Test {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @TempDir
    Path tempDir;

    @Test
    void compileBooleanWritesCanonicalObjectAndRegistersInConfiguration() throws Exception {
        Path configuration = writeMinimalConfiguration();
        Path dsl = writeDsl("boolean.json", """
                {
                  "type": "SessionParameter",
                  "name": "биг_ВыполняетсяРасчет",
                  "synonym": "Выполняется расчет",
                  "valueType": "Boolean"
                }
                """);

        new MetaWriter().compile(dsl, tempDir);

        Path object = tempDir.resolve("SessionParameters/биг_ВыполняетсяРасчет.xml");
        assertDesignerEncoding(object);
        assertDesignerEncoding(configuration);
        assertThat(tempDir.resolve("SessionParameters/биг_ВыполняетсяРасчет")).doesNotExist();

        XmlDocument document = new XmlStructureReader().parse(object);
        XmlNode sessionParameter = document.getRoot().child("SessionParameter");
        assertThat(sessionParameter).isNotNull();
        assertThat(sessionParameter.child("InternalInfo")).isNull();
        assertThat(sessionParameter.child("ChildObjects")).isNull();

        XmlNode properties = sessionParameter.child("Properties");
        assertThat(properties.getChildren()).extracting(XmlNode::getName)
                .containsExactly("Name", "Synonym", "Comment", "Type");
        assertThat(properties.child("Type").childText("Type")).isEqualTo("xs:boolean");
        assertValid(document);

        String config = readUtf8(configuration);
        assertThat(config).contains("<SessionParameter>биг_ВыполняетсяРасчет</SessionParameter>");
        // Первый ChildObject нового типа добавляется в конец контейнера; существующий
        // отступ закрывающего тега не должен приклеиваться к новому элементу.
        assertThat(config).doesNotContain("\t\t\t\t<SessionParameter>");
        assertThat(config.indexOf("<CommonPicture>Картинка</CommonPicture>"))
                .isLessThan(config.indexOf("<SessionParameter>биг_ВыполняетсяРасчет</SessionParameter>"));
        assertThat(config.indexOf("<SessionParameter>биг_ВыполняетсяРасчет</SessionParameter>"))
                .isLessThan(config.indexOf("<Role>ПолныеПрава</Role>"));
    }

    @Test
    void compileRussianValueStorageWritesCanonicalTypeAndPassesValidation() throws Exception {
        writeMinimalConfiguration();
        Path dsl = writeDsl("storage.json", """
                {
                  "type": "ПараметрСеанса",
                  "name": "биг_КэшРасчета",
                  "valueType": "ХранилищеЗначения"
                }
                """);

        new MetaWriter().compile(dsl, tempDir);

        Path object = tempDir.resolve("SessionParameters/биг_КэшРасчета.xml");
        assertDesignerEncoding(object);
        XmlDocument document = new XmlStructureReader().parse(object);
        XmlNode properties = document.getRoot().child("SessionParameter").child("Properties");
        assertThat(properties.child("Type").childText("Type")).isEqualTo("v8:ValueStorage");
        assertValid(document);
    }

    @Test
    void compileAppendsFirstSessionParameterWithCanonicalChildIndent() throws Exception {
        Path configuration = tempDir.resolve("Configuration.xml");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" version="2.20">
                  <Configuration uuid="aaaaaaaa-0000-0000-0000-000000000000">
                    <Properties><Name>TestCfg</Name></Properties>
                    <ChildObjects>
                      <Language>Русский</Language>
                    </ChildObjects>
                  </Configuration>
                </MetaDataObject>
                """;
        Files.write(configuration, withBomAndCrlf(xml));
        Path dsl = writeDsl("tail.json", """
                {"type":"SessionParameter","name":"биг_Хвостовой","valueType":"Boolean"}
                """);

        new MetaWriter().compile(dsl, tempDir);

        String config = readUtf8(configuration);
        assertThat(config).contains("\r\n\t\t\t<SessionParameter>биг_Хвостовой</SessionParameter>\r\n");
        assertThat(config).doesNotContain("\t\t\t\t<SessionParameter>");
    }

    @Test
    void validateRejectsSessionParameterWithoutType() throws Exception {
        Path object = tempDir.resolve("SessionParameters/БезТипа.xml");
        Files.createDirectories(object.getParent());
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                  xmlns:v8="http://v8.1c.ru/8.1/data/core" version="2.20">
                  <SessionParameter uuid="11111111-1111-1111-1111-111111111111">
                    <Properties>
                      <Name>БезТипа</Name>
                      <Synonym/>
                      <Comment/>
                    </Properties>
                  </SessionParameter>
                </MetaDataObject>
                """;
        Files.write(object, withBomAndCrlf(xml));

        List<MetaValidator.ValidationMessage> messages = new MetaValidator().validate(
                new XmlStructureReader().parse(object), null);

        assertThat(messages).anySatisfy(message -> {
            assertThat(message.level).isEqualTo("ERROR");
            assertThat(message.message).contains("SessionParameter: Type is required");
        });
    }

    private Path writeDsl(String name, String json) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, json, StandardCharsets.UTF_8);
        return path;
    }

    private Path writeMinimalConfiguration() throws Exception {
        Path path = tempDir.resolve("Configuration.xml");
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses"
                  xmlns:xr="http://v8.1c.ru/8.3/xcf/readable" version="2.20">
                  <Configuration uuid="aaaaaaaa-0000-0000-0000-000000000000">
                    <Properties><Name>TestCfg</Name></Properties>
                    <ChildObjects>
                      <CommonPicture>Картинка</CommonPicture>
                      <Role>ПолныеПрава</Role>
                    </ChildObjects>
                  </Configuration>
                </MetaDataObject>
                """;
        Files.write(path, withBomAndCrlf(xml));
        return path;
    }

    private static void assertValid(XmlDocument document) {
        assertThat(new MetaValidator().validate(document, null))
                .noneMatch(message -> "ERROR".equals(message.level));
    }

    private static void assertDesignerEncoding(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        assertThat(bytes).startsWith(BOM);
        for (int i = BOM.length; i < bytes.length; i++) {
            if (bytes[i] == '\n') {
                assertThat(i).isGreaterThan(0);
                assertThat(bytes[i - 1]).as("bare LF at byte %s in %s", i, path).isEqualTo((byte) '\r');
            }
        }
    }

    private static byte[] withBomAndCrlf(String value) {
        byte[] body = value.replace("\r\n", "\n").replace("\n", "\r\n")
                .getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, result, 0, BOM.length);
        System.arraycopy(body, 0, result, BOM.length, body.length);
        return result;
    }

    private static String readUtf8(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        int offset = bytes.length >= BOM.length
                && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2] ? BOM.length : 0;
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }
}

//++agent TASK-174 XG-108

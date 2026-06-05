package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.writer.ConfigWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты ConfigValidator (TASK-171): дефекты D-1/D-2/D-4/D-5.
 * <ul>
 *   <li>D-2 — удалена ложная проверка алфавитного порядка объектов внутри типа;</li>
 *   <li>D-4 — добавлена проверка ЗНАЧЕНИЙ ClassId в InternalInfo;</li>
 *   <li>D-1 — ConfigWriter генерит корректные ClassId (round-trip через D-4-проверку);</li>
 *   <li>D-5 — тип платформы WebSocketClient (8.3.27) не даёт ложный WARN «unknown type».</li>
 * </ul>
 */
class ConfigValidatorTest {

    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Каркас валидного Configuration.xml с подставляемым блоком ChildObjects. */
    private String configWithChildObjects(String childObjects) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"2.20\">\n"
                + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>ТестКонфиг</Name>\n"
                + "\t\t\t<DefaultLanguage>Language.Русский</DefaultLanguage>\n"
                + "\t\t\t<DefaultRunMode>ManagedApplication</DefaultRunMode>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t\t<Language>Русский</Language>\n"
                + childObjects
                + "\t\t</ChildObjects>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
    }

    // ==================== D-2: алфавитный порядок объектов внутри типа ====================

    @Test
    void testNonAlphabeticalObjectsWithinTypeNoWarn() throws Exception {
        // TASK-171 D-2 регресс: Designer экспортирует объекты в порядке создания, а НЕ по алфавиту.
        // CommonModule.биг_КоннекторHTTP после биг_СтратегииСервер — валидно, ложного WARN быть не должно.
        Path file = writeXml("Configuration.xml", configWithChildObjects(
                "\t\t\t<CommonModule>биг_СтратегииСервер</CommonModule>\n"
                + "\t\t\t<CommonModule>биг_КоннекторHTTP</CommonModule>\n"));

        XmlDocument doc = reader.parse(file);
        List<ConfigValidator.ValidationMessage> messages =
                new ConfigValidator().validate(doc, null);

        assertThat(messages).noneMatch(m -> m.message.contains("alphabetical"));
    }

    @Test
    void testTypeOrderViolationStillWarned() throws Exception {
        // Порядок ТИПОВ по-прежнему проверяется: Catalog раньше CommonModule в каноне,
        // поэтому CommonModule после Catalog — нарушение канонического порядка типов.
        Path file = writeXml("Configuration.xml", configWithChildObjects(
                "\t\t\t<Catalog>Товары</Catalog>\n"
                + "\t\t\t<CommonModule>МойМодуль</CommonModule>\n"));

        XmlDocument doc = reader.parse(file);
        List<ConfigValidator.ValidationMessage> messages =
                new ConfigValidator().validate(doc, null);

        assertThat(messages).anyMatch(m -> m.message.contains("canonical order"));
    }

    // ==================== D-5: WebSocketClient — валидный тип платформы ====================

    @Test
    void testWebSocketClientNotUnknownType() throws Exception {
        // TASK-171 D-5 регресс: тип платформы 8.3.27, легитимно присутствует в реальной
        // Configuration.xml. Не должно быть WARN «unknown type 'WebSocketClient'».
        Path file = writeXml("Configuration.xml", configWithChildObjects(
                "\t\t\t<HTTPService>биг_Сервис</HTTPService>\n"
                + "\t\t\t<WebSocketClient>биг_ВебСокет_ОКХ</WebSocketClient>\n"));

        XmlDocument doc = reader.parse(file);
        List<ConfigValidator.ValidationMessage> messages =
                new ConfigValidator().validate(doc, null);

        assertThat(messages).noneMatch(m -> m.message.contains("WebSocketClient"));
    }

    // ==================== D-4: проверка значений ClassId ====================

    @Test
    void testInvalidClassIdReportedAsError() throws Exception {
        // TASK-171 D-4 регресс: битый ClassId в InternalInfo должен давать ERROR (раньше
        // проверялось только количество, значения пропускались).
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"2.20\">\n"
                + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                + "\t\t<InternalInfo>\n"
                + "\t\t\t<xr:ContainedObject>\n"
                + "\t\t\t\t<xr:ClassId>deadbeef-0000-0000-0000-000000000000</xr:ClassId>\n"
                + "\t\t\t\t<xr:ObjectId>11111111-1111-1111-1111-111111111111</xr:ObjectId>\n"
                + "\t\t\t</xr:ContainedObject>\n"
                + "\t\t</InternalInfo>\n"
                + "\t\t<Properties>\n"
                + "\t\t\t<Name>ТестКонфиг</Name>\n"
                + "\t\t\t<DefaultLanguage>Language.Русский</DefaultLanguage>\n"
                + "\t\t\t<DefaultRunMode>ManagedApplication</DefaultRunMode>\n"
                + "\t\t</Properties>\n"
                + "\t\t<ChildObjects>\n"
                + "\t\t\t<Language>Русский</Language>\n"
                + "\t\t</ChildObjects>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
        Path file = writeXml("Configuration.xml", xml);

        XmlDocument doc = reader.parse(file);
        List<ConfigValidator.ValidationMessage> messages =
                new ConfigValidator().validate(doc, null);

        assertThat(messages).anyMatch(m ->
                "ERROR".equals(m.level) && m.message.contains("unknown ClassId"));
    }

    // ==================== D-1: ConfigWriter генерит валидные ClassId ====================

    @Test
    void testConfigWriterGeneratesValidClassIds() throws Exception {
        // TASK-171 D-1 регресс: ранее ConfigWriter писал 4 неверных ClassId из 7 → битый InternalInfo.
        // Теперь сгенерированная конфигурация проходит проверку значений ClassId (D-4) без ERROR.
        Path outDir = tempDir.resolve("cfg");
        new ConfigWriter().create(outDir, "ТестКонфиг", null, null, null, null, null);

        XmlDocument doc = reader.parse(outDir.resolve("Configuration.xml"));
        List<ConfigValidator.ValidationMessage> messages =
                new ConfigValidator().validate(doc, null);

        assertThat(messages).noneMatch(m -> m.message.contains("unknown ClassId"));
    }
}

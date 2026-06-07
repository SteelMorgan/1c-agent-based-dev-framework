package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Тесты MetaValidator, в частности enum-словарей D1-D5 (TASK-171).
 *
 * <p>Регрессии строятся на грунт-труф объектах {@code _Демо*} конфигурации GBIG PAM —
 * это объекты, которые пишет/читает сама платформа 1С, т.е. заведомо валидные. Любое
 * ERROR/WARN на них = ложное срабатывание валидатора (баг).</p>
 *
 * <p>Реальные файлы лежат вне репозитория фреймворка, поэтому такие тесты помечены
 * {@link org.junit.jupiter.api.Assumptions#assumeTrue} по наличию файла (как
 * {@code SkdValidatorTest#testRealSkdIfAvailable}): в окружениях без GBIG PAM они
 * skip-аются, а не падают.</p>
 */
class MetaValidatorTest {

    private static final Path GBIG_SRC =
            Path.of("/workspaces/work/repos/1C Projects/GBIG PAM/src/xml");

    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    // ==================== D1: RegisterType='Balance' ====================

    @Test
    void d1_realAccumulationRegisterBalance_noRegisterTypeWarn() throws Exception {
        // Грунт-труф: _ДемоОстаткиТоваровВМестахХранения — RegisterType='Balance' (ед.ч.).
        // До TASK-171 валидатор ждал 'Balances' и давал ложный WARN.
        List<MetaValidator.ValidationMessage> msgs =
                validateReal("AccumulationRegisters/_ДемоОстаткиТоваровВМестахХранения.xml");
        assertThat(msgs)
                .as("RegisterType='Balance' не должен давать WARN: " + msgs)
                .noneMatch(m -> m.message.contains("RegisterType"));
    }

    @Test
    void d1_balanceAndBalancesAndTurnoversAccepted_balancesXyzWarns() {
        MetaValidator v = new MetaValidator();
        // 'Balance' и 'Turnovers' — платформенные; 'Balances' принимаем как алиас.
        assertThat(enumWarns(v, "AccumulationRegister", "RegisterType", "Balance")).isEmpty();
        assertThat(enumWarns(new MetaValidator(), "AccumulationRegister", "RegisterType", "Turnovers")).isEmpty();
        assertThat(enumWarns(new MetaValidator(), "AccumulationRegister", "RegisterType", "Balances")).isEmpty();
        // Мусорное значение по-прежнему ловим.
        assertThat(enumWarns(new MetaValidator(), "AccumulationRegister", "RegisterType", "Garbage"))
                .anyMatch(m -> m.message.contains("RegisterType"));
    }

    // ==================== D2: RegisterRecordsDeletion='AutoDeleteOnUnpost' ====================

    @Test
    void d2_realDocumentAutoDeleteOnUnpost_noDeletionWarn() throws Exception {
        // Грунт-труф: _ДемоЗаказПокупателя — RegisterRecordsDeletion='AutoDeleteOnUnpost'.
        // До TASK-171 валидатор не знал это значение (зато имел выдуманный 'DoNotDelete').
        List<MetaValidator.ValidationMessage> msgs =
                validateReal("Documents/_ДемоЗаказПокупателя.xml");
        assertThat(msgs)
                .as("RegisterRecordsDeletion='AutoDeleteOnUnpost' не должен давать WARN: " + msgs)
                .noneMatch(m -> m.message.contains("RegisterRecordsDeletion"));
    }

    @Test
    void d2_autoDeleteOnUnpostAccepted_doNotDeleteRejected() {
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsDeletion", "AutoDeleteOnUnpost"))
                .isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsDeletion", "AutoDelete"))
                .isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsDeletion", "AutoDeleteOff"))
                .isEmpty();
        // 'DoNotDelete' — несуществующее значение: оно больше не в белом списке, т.е. ловится как WARN.
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsDeletion", "DoNotDelete"))
                .anyMatch(m -> m.message.contains("RegisterRecordsDeletion"));
    }

    // ==================== D3: ChoiceMode='FromForm' ====================

    @Test
    void d3_fromFormAccepted_fromChoiceFormRejected() {
        assertThat(enumWarns(new MetaValidator(), "Catalog", "ChoiceMode", "FromForm")).isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Catalog", "ChoiceMode", "BothWays")).isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Catalog", "ChoiceMode", "QuickChoice")).isEmpty();
        // 'FromChoiceForm' — выдуманное прежнее значение: теперь даёт WARN.
        assertThat(enumWarns(new MetaValidator(), "Catalog", "ChoiceMode", "FromChoiceForm"))
                .anyMatch(m -> m.message.contains("ChoiceMode"));
    }

    // ==================== D4: ActionPeriodUse — Boolean у ВПР ====================

    @Test
    void d4_realChartOfCalculationTypesActionPeriodUseTrue_noWarn() throws Exception {
        // Грунт-труф: _ДемоОсновныеНачисления — <ActionPeriodUse>true</ActionPeriodUse> (Boolean).
        // До TASK-171 валидатор валидировал ActionPeriodUse как enum DontUse/Use под CalculationRegister.
        List<MetaValidator.ValidationMessage> msgs =
                validateReal("ChartsOfCalculationTypes/_ДемоОсновныеНачисления.xml");
        assertThat(msgs)
                .as("ActionPeriodUse=true (Boolean) и DependenceOnCalculationTypes=OnActionPeriod не должны давать WARN: " + msgs)
                .noneMatch(m -> m.message.contains("ActionPeriodUse"))
                .noneMatch(m -> m.message.contains("DependenceOnCalculationTypes"));
    }

    @Test
    void d4_actionPeriodUseValidatedAsBooleanForChart() {
        // Boolean true/false — без WARN.
        assertThat(boolWarns("ChartOfCalculationTypes", "ActionPeriodUse", "true")).isEmpty();
        assertThat(boolWarns("ChartOfCalculationTypes", "ActionPeriodUse", "false")).isEmpty();
        // Нечисловое значение ловится как нарушение Boolean (ERROR), а не как enum.
        assertThat(boolWarns("ChartOfCalculationTypes", "ActionPeriodUse", "DontUse"))
                .anyMatch(m -> m.message.contains("ActionPeriodUse"));
        // DependenceOnCalculationTypes: платформенные DontUse/OnActionPeriod — без WARN.
        assertThat(enumWarns(new MetaValidator(), "ChartOfCalculationTypes",
                "DependenceOnCalculationTypes", "OnActionPeriod")).isEmpty();
        assertThat(enumWarns(new MetaValidator(), "ChartOfCalculationTypes",
                "DependenceOnCalculationTypes", "DontUse")).isEmpty();
        // 'NotUsed' (ошибка таблички spec) — не платформенное → WARN.
        assertThat(enumWarns(new MetaValidator(), "ChartOfCalculationTypes",
                "DependenceOnCalculationTypes", "NotUsed"))
                .anyMatch(m -> m.message.contains("DependenceOnCalculationTypes"));
    }

    @Test
    void d4_calculationRegisterNoLongerValidatesActionPeriodUse() {
        // ActionPeriodUse под CalculationRegister больше не валидируется (проверка удалена) —
        // значение 'true' там не должно давать ни enum-WARN, ни Boolean-ошибки.
        List<MetaValidator.ValidationMessage> msgs = enumWarns(new MetaValidator(),
                "CalculationRegister", "ActionPeriodUse", "true");
        assertThat(msgs).noneMatch(m -> m.message.contains("ActionPeriodUse"));
    }

    // ==================== D5: RegisterRecordsWritingOnPost += WriteAll ====================

    @Test
    void d5_writeAllAccepted() {
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsWritingOnPost", "WriteAll"))
                .isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsWritingOnPost", "WriteSelected"))
                .isEmpty();
        assertThat(enumWarns(new MetaValidator(), "Document", "RegisterRecordsWritingOnPost", "WriteModified"))
                .isEmpty();
    }

    // ==================== TASK-174 audit: Report/DataProcessor semantic gaps ====================

    @Test
    void dataProcessorObjectAttributeWithIndexing_reported() throws Exception {
        XmlDocument doc = reader.parse(writeXml("dp-indexing.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                        + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.20\">\n"
                        + "  <DataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "    <Properties><Name>Обр</Name><Synonym/></Properties>\n"
                        + "    <ChildObjects>\n"
                        + "      <Attribute uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "        <Properties>\n"
                        + "          <Name>Парам</Name>\n"
                        + "          <Type><v8:Type>xs:string</v8:Type></Type>\n"
                        + "          <Indexing>DontIndex</Indexing>\n"
                        + "        </Properties>\n"
                        + "      </Attribute>\n"
                        + "    </ChildObjects>\n"
                        + "  </DataProcessor>\n"
                        + "</MetaDataObject>\n"));

        List<MetaValidator.ValidationMessage> msgs = new MetaValidator().validate(doc, null);

        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("Attribute 'Парам'")
                && m.message.contains("Indexing"));
    }

    @Test
    void dataProcessorTabularSectionAttributeMissingFillValueAndWithIndexing_reported() throws Exception {
        XmlDocument doc = reader.parse(writeXml("dp-ts.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                        + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\" version=\"2.20\">\n"
                        + "  <DataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "    <Properties><Name>Обр</Name><Synonym/></Properties>\n"
                        + "    <ChildObjects>\n"
                        + "      <TabularSection uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "        <Properties><Name>Строки</Name></Properties>\n"
                        + "        <ChildObjects>\n"
                        + "          <Attribute uuid=\"33333333-3333-3333-3333-333333333333\">\n"
                        + "            <Properties>\n"
                        + "              <Name>Колонка</Name>\n"
                        + "              <Type><v8:Type>xs:string</v8:Type></Type>\n"
                        + "              <Indexing>DontIndex</Indexing>\n"
                        + "            </Properties>\n"
                        + "          </Attribute>\n"
                        + "        </ChildObjects>\n"
                        + "      </TabularSection>\n"
                        + "    </ChildObjects>\n"
                        + "  </DataProcessor>\n"
                        + "</MetaDataObject>\n"));

        List<MetaValidator.ValidationMessage> msgs = new MetaValidator().validate(doc, null);

        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("FillFromFillingValue"));
        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("FillValue"));
        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("Indexing"));
    }

    @Test
    void reportMainDcsTemplateNotDeclared_reported() throws Exception {
        Path reportsDir = tempDir.resolve("Reports");
        Files.createDirectories(reportsDir.resolve("Отчет"));
        Path reportXml = reportsDir.resolve("Отчет.xml");
        Files.writeString(reportXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "  <Report uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "    <Properties>\n"
                        + "      <Name>Отчет</Name>\n"
                        + "      <Synonym/>\n"
                        + "      <MainDataCompositionSchema>Report.Отчет.Template.Схема</MainDataCompositionSchema>\n"
                        + "    </Properties>\n"
                        + "    <ChildObjects/>\n"
                        + "  </Report>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);

        List<MetaValidator.ValidationMessage> msgs =
                new MetaValidator().validate(reader.parse(reportXml), reportsDir);

        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("MainDataCompositionSchema")
                && m.message.contains("not declared"));
    }

    @Test
    void reportMainDcsTemplateWrongType_reported() throws Exception {
        Path reportsDir = tempDir.resolve("Reports2");
        Files.createDirectories(reportsDir.resolve("Отчет/Templates"));
        Files.writeString(reportsDir.resolve("Отчет/Templates/Схема.xml"),
                "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "  <Template uuid=\"22222222-2222-2222-2222-222222222222\">\n"
                        + "    <Properties><Name>Схема</Name><TemplateType>SpreadsheetDocument</TemplateType></Properties>\n"
                        + "  </Template>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);
        Path reportXml = reportsDir.resolve("Отчет.xml");
        Files.writeString(reportXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "  <Report uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "    <Properties>\n"
                        + "      <Name>Отчет</Name>\n"
                        + "      <Synonym/>\n"
                        + "      <MainDataCompositionSchema>Report.Отчет.Template.Схема</MainDataCompositionSchema>\n"
                        + "    </Properties>\n"
                        + "    <ChildObjects><Template>Схема</Template></ChildObjects>\n"
                        + "  </Report>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);

        List<MetaValidator.ValidationMessage> msgs =
                new MetaValidator().validate(reader.parse(reportXml), reportsDir);

        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("SpreadsheetDocument")
                && m.message.contains("DataCompositionSchema"));
    }

    @Test
    void dataProcessorLocalDefaultFormMissing_reported() throws Exception {
        Path dpDir = tempDir.resolve("DataProcessors");
        Files.createDirectories(dpDir.resolve("Обр"));
        Path dpXml = dpDir.resolve("Обр.xml");
        Files.writeString(dpXml,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "  <DataProcessor uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                        + "    <Properties>\n"
                        + "      <Name>Обр</Name>\n"
                        + "      <Synonym/>\n"
                        + "      <DefaultForm>DataProcessor.Обр.Form.Форма</DefaultForm>\n"
                        + "    </Properties>\n"
                        + "    <ChildObjects/>\n"
                        + "  </DataProcessor>\n"
                        + "</MetaDataObject>\n",
                StandardCharsets.UTF_8);

        List<MetaValidator.ValidationMessage> msgs =
                new MetaValidator().validate(reader.parse(dpXml), dpDir);

        assertThat(msgs).anyMatch(m -> "ERROR".equals(m.level)
                && m.message.contains("DefaultForm")
                && m.message.contains("висячая ссылка"));
    }

    // ==================== Helpers ====================

    /**
     * Валидирует реальный {@code _Демо}-объект GBIG PAM. {@code objectDir=null} — чтобы
     * ФС-проверки фантом-форм (другие правила, не D1-D5) не вносили посторонний шум.
     * Тест skip-ается, если файла нет (окружение без GBIG PAM).
     */
    private List<MetaValidator.ValidationMessage> validateReal(String relative) throws Exception {
        Path file = GBIG_SRC.resolve(relative);
        assumeTrue(Files.exists(file), "Грунт-труф файл недоступен: " + file);
        XmlDocument doc = reader.parse(file);
        return new MetaValidator().validate(doc, null);
    }

    /**
     * Прогоняет валидатор на синтетическом минимальном объекте с одним enum-свойством
     * и возвращает все сообщения. Удобно для проверки белого/чёрного списка enum без ФС.
     */
    private List<MetaValidator.ValidationMessage> enumWarns(MetaValidator v, String type,
                                                            String propName, String value) {
        // TASK-171: фильтруем по имени проверяемого свойства — минимальный синтетический
        // объект даёт несвязанные WARN (Synonym missing, StandardAttributes missing),
        // которые к проверке конкретного enum-правила не относятся.
        return v.validate(buildMinimal(type, propName, value), null).stream()
                .filter(m -> m.message.contains(propName))
                .collect(java.util.stream.Collectors.toList());
    }

    private List<MetaValidator.ValidationMessage> boolWarns(String type, String propName, String value) {
        return new MetaValidator().validate(buildMinimal(type, propName, value), null).stream()
                .filter(m -> m.message.contains(propName))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Минимальный валидный по структуре объект {@code <MetaDataObject version=2.20>}
     * с одним проверяемым свойством. Парсится через тот же XmlStructureReader.
     */
    private XmlDocument buildMinimal(String type, String propName, String value) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                + "  <" + type + " uuid=\"11111111-1111-1111-1111-111111111111\">\n"
                + "    <Properties>\n"
                + "      <Name>ТестОбъект</Name>\n"
                + "      <" + propName + ">" + value + "</" + propName + ">\n"
                + "    </Properties>\n"
                + "  </" + type + ">\n"
                + "</MetaDataObject>\n";
        try {
            Path tmp = Files.createTempFile("meta-d-test-", ".xml");
            Files.writeString(tmp, xml, StandardCharsets.UTF_8);
            tmp.toFile().deleteOnExit();
            return reader.parse(tmp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path writeXml(String filename, String content) throws Exception {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }
}

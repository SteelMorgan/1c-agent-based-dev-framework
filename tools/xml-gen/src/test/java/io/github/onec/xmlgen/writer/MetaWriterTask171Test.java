package io.github.onec.xmlgen.writer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Регрессионные тесты дефектов генерации метаданных TASK-171 (D-1..D-6).
 */
class MetaWriterTask171Test {

    @TempDir
    Path tempDir;

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Прочитать файл, отрезая BOM. */
    private String read(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        int off = (b.length >= 3 && b[0] == BOM[0] && b[1] == BOM[1] && b[2] == BOM[2]) ? 3 : 0;
        return new String(b, off, b.length - off, StandardCharsets.UTF_8);
    }

    private Path writeJson(String name, String json) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, json, StandardCharsets.UTF_8);
        return p;
    }

    /** Минимальный Configuration.xml с заданной версией формата и пустым ChildObjects. */
    private Path writeMinimalConfig(Path dir, String formatVersion) throws IOException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\"\n"
                + "\txmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"\n"
                + "\tversion=\"" + formatVersion + "\">\n"
                + "\t<Configuration uuid=\"aaaaaaaa-0000-0000-0000-000000000000\">\n"
                + "\t\t<Properties>\n\t\t\t<Name>TestCfg</Name>\n\t\t</Properties>\n"
                + "\t\t<ChildObjects/>\n"
                + "\t</Configuration>\n"
                + "</MetaDataObject>\n";
        Path cfg = dir.resolve("Configuration.xml");
        byte[] body = xml.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[BOM.length + body.length];
        System.arraycopy(BOM, 0, out, 0, BOM.length);
        System.arraycopy(body, 0, out, BOM.length, body.length);
        Files.write(cfg, out);
        return cfg;
    }

    // ─── D-2: namespace ──────────────────────────────────────────────────

    @Test
    void compile_usesCanonicalNamespace_notBrokenV83() throws IOException {
        Path json = writeJson("c.json",
                "{\"type\":\"Catalog\",\"name\":\"test_Кат\",\"synonym\":\"Кат\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/test_Кат.xml"));
        assertThat(xml).contains("xmlns:xen=\"http://v8.1c.ru/8.3/xcf/enums\"");
        assertThat(xml).contains("xmlns:xpr=\"http://v8.1c.ru/8.3/xcf/predef\"");
        assertThat(xml).contains("xmlns:xr=\"http://v8.1c.ru/8.3/xcf/readable\"");
        assertThat(xml).doesNotContain("http://v8.3/xcf/");
    }

    // ─── D-4: CommonModule без InternalInfo/ChildObjects ─────────────────

    @Test
    void compile_commonModule_hasNoInternalInfoOrChildObjects() throws IOException {
        Path json = writeJson("cm.json",
                "{\"type\":\"CommonModule\",\"name\":\"test_Хелпер\",\"context\":\"server\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("CommonModules/test_Хелпер.xml"));
        assertThat(xml).doesNotContain("<InternalInfo");
        assertThat(xml).doesNotContain("<ChildObjects");
        assertThat(xml).contains("<CommonModule uuid=");
        assertThat(xml).contains("<Server>true</Server>");
    }

    @Test
    void compile_scheduledJobCreatesJobScheduleBody() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("sj.json", """
                {
                  "type": "ScheduledJob",
                  "name": "test_НочноеЗадание",
                  "methodName": "РегламентныеЗадания.НочноеЗадание"
                }
                """);
        new MetaWriter().compile(json, tempDir);

        Path schedule = tempDir.resolve("ScheduledJobs/test_НочноеЗадание/Ext/Schedule.xml");
        assertThat(schedule).exists();
        String xml = read(schedule);
        assertThat(xml).contains("<JobSchedule xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"");
        assertThat(xml).contains("version=\"2.20\"");
        assertThat(xml).contains("<ent:WeekDays>1 2 3 4 5 6 7</ent:WeekDays>");
        assertThat(xml).contains("<ent:Months>1 2 3 4 5 6 7 8 9 10 11 12</ent:Months>");
    }

    // ─── D-5: enumValues алиас ───────────────────────────────────────────

    @Test
    void compile_enum_acceptsEnumValuesAlias() throws IOException {
        Path json = writeJson("e.json",
                "{\"type\":\"Enum\",\"name\":\"test_Стат\",\"enumValues\":[{\"name\":\"Новый\"},{\"name\":\"Закрыт\"}]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Enums/test_Стат.xml"));
        assertThat(xml).contains("<EnumValue uuid=");
        assertThat(xml).contains("<Name>Новый</Name>");
        assertThat(xml).contains("<Name>Закрыт</Name>");
    }

    @Test
    void compile_enum_stillAcceptsValuesKey() throws IOException {
        Path json = writeJson("e2.json",
                "{\"type\":\"Enum\",\"name\":\"test_Стат2\",\"values\":[{\"name\":\"A\"}]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Enums/test_Стат2.xml"));
        assertThat(xml).contains("<Name>A</Name>");
    }

    // ─── D-1: предопределённые в compile ─────────────────────────────────

    @Test
    void compile_catalog_writesPredefinedXml() throws IOException {
        Path json = writeJson("p.json",
                "{\"type\":\"Catalog\",\"name\":\"test_Дог\",\"codeLength\":9,"
                + "\"predefinedItems\":[{\"name\":\"Аренда\",\"description\":\"Договор аренды\"},\"Прочее\"]}");
        new MetaWriter().compile(json, tempDir);
        Path predefined = tempDir.resolve("Catalogs/test_Дог/Ext/Predefined.xml");
        assertThat(Files.exists(predefined)).isTrue();
        String xml = read(predefined);
        assertThat(xml).contains("xsi:type=\"CatalogPredefinedItems\"");
        assertThat(xml).contains("xmlns=\"http://v8.1c.ru/8.3/xcf/predef\"");
        assertThat(xml).contains("<Name>Аренда</Name>");
        assertThat(xml).contains("<Code>000000001</Code>");
        assertThat(xml).contains("<Description>Договор аренды</Description>");
        // строковый элемент: description = name, авто-код
        assertThat(xml).contains("<Name>Прочее</Name>");
        assertThat(xml).contains("<Code>000000002</Code>");
    }

    @Test
    void compile_enum_doesNotWritePredefined() throws IOException {
        Path json = writeJson("ep.json",
                "{\"type\":\"Enum\",\"name\":\"test_E3\",\"predefinedItems\":[{\"name\":\"X\"}]}");
        new MetaWriter().compile(json, tempDir);
        // Enum не поддерживает предопределённые — файла быть не должно
        assertThat(Files.exists(tempDir.resolve("Enums/test_E3/Ext/Predefined.xml"))).isFalse();
    }

    // ─── D-6: версия формата из Configuration.xml ────────────────────────

    @Test
    void compile_objectAndPredefined_inheritConfigVersion() throws IOException {
        writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("v.json",
                "{\"type\":\"Catalog\",\"name\":\"test_V\",\"predefinedItems\":[{\"name\":\"A\"}]}");
        new MetaWriter().compile(json, tempDir);
        String obj = read(tempDir.resolve("Catalogs/test_V.xml"));
        assertThat(obj).contains("version=\"2.20\"");
        String pre = read(tempDir.resolve("Catalogs/test_V/Ext/Predefined.xml"));
        assertThat(pre).contains("version=\"2.20\"");
    }

    @Test
    void compile_exchangePlanContentAndFlowchartStubsInheritConfigVersionAndCanonicalRoots()
            throws IOException {
        writeMinimalConfig(tempDir, "2.20");

        Path exchangeJson = writeJson("xp.json", "{\"type\":\"ExchangePlan\",\"name\":\"test_ПО\"}");
        new MetaWriter().compile(exchangeJson, tempDir);
        String content = read(tempDir.resolve("ExchangePlans/test_ПО/Ext/Content.xml"));
        assertThat(content).contains("<ExchangePlanContent xmlns=\"http://v8.1c.ru/8.3/xcf/extrnprops\"");
        assertThat(content).contains("version=\"2.20\"");

        Path bpJson = writeJson("bp.json", "{\"type\":\"BusinessProcess\",\"name\":\"test_БП\"}");
        new MetaWriter().compile(bpJson, tempDir);
        String flowchart = read(tempDir.resolve("BusinessProcesses/test_БП/Ext/Flowchart.xml"));
        assertThat(flowchart).contains("<GraphicalSchema xmlns=\"http://v8.1c.ru/8.3/xcf/scheme\"");
        assertThat(flowchart).contains("version=\"2.20\"");
        assertThat(flowchart).doesNotContain("<Flowchart");
        assertThat(flowchart).doesNotContain("http://v8.1c.ru/8.3/flowchart");
    }

    @Test
    void compile_fallsBackToDefaultVersion_whenNoConfig() throws IOException {
        Path json = writeJson("nf.json", "{\"type\":\"Catalog\",\"name\":\"test_NF\"}");
        new MetaWriter().compile(json, tempDir);
        String obj = read(tempDir.resolve("Catalogs/test_NF.xml"));
        assertThat(obj).contains("version=\"2.17\"");
    }

    // ─── D-3: регистрация в Configuration.xml ────────────────────────────

    @Test
    void compile_registersObjectInConfiguration() throws IOException {
        Path cfg = writeMinimalConfig(tempDir, "2.20");
        Path json = writeJson("r.json", "{\"type\":\"Catalog\",\"name\":\"test_Reg\"}");
        new MetaWriter().compile(json, tempDir);
        String config = read(cfg);
        assertThat(config).contains("<Catalog>test_Reg</Catalog>");
    }

    @Test
    void compile_withoutConfig_doesNotThrow() throws IOException {
        Path json = writeJson("nc.json", "{\"type\":\"Catalog\",\"name\":\"test_NoCfg\"}");
        // нет Configuration.xml — должен пройти с предупреждением, без исключения
        new MetaWriter().compile(json, tempDir);
        assertThat(Files.exists(tempDir.resolve("Catalogs/test_NoCfg.xml"))).isTrue();
    }

    // ─── D-1 (edit): meta edit add-predefined ────────────────────────────

    @Test
    void edit_addPredefined_createsAndAppends() throws IOException {
        // компилируем каталог без предопределённых
        Path json = writeJson("cat.json", "{\"type\":\"Catalog\",\"name\":\"test_Ed\"}");
        new MetaWriter().compile(json, tempDir);
        Path objXml = tempDir.resolve("Catalogs/test_Ed.xml");

        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        editor.edit(objXml, "add-predefined", "Один;;Два|Второй");
        Path pre = tempDir.resolve("Catalogs/test_Ed/Ext/Predefined.xml");
        assertThat(Files.exists(pre)).isTrue();
        String xml = read(pre);
        assertThat(xml).contains("<Name>Один</Name>").contains("<Code>000000001</Code>");
        assertThat(xml).contains("<Name>Два</Name>").contains("<Description>Второй</Description>")
                .contains("<Code>000000002</Code>");

        // повторный вызов: дозапись со следующим кодом, дубликат пропускается
        editor.edit(objXml, "add-predefined", "Три;;Один");
        String xml2 = read(pre);
        assertThat(xml2).contains("<Name>Три</Name>").contains("<Code>000000003</Code>");
        // "Один" не задублирован
        assertThat(xml2.split("<Name>Один</Name>", -1).length - 1).isEqualTo(1);
    }

    @Test
    void edit_addPredefined_onUnsupportedType_throws() throws IOException {
        Path json = writeJson("en.json", "{\"type\":\"Enum\",\"name\":\"test_EnEd\"}");
        new MetaWriter().compile(json, tempDir);
        Path objXml = tempDir.resolve("Enums/test_EnEd.xml");
        MetaEditor editor = new MetaEditor(new PrintStream(new ByteArrayOutputStream()));
        assertThatThrownBy(() -> editor.edit(objXml, "add-predefined", "X"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не поддерживает предопределённые");
    }

    // ─── W1 / D-6: полнота и порядок Properties ──────────────────────────
    // Эталон набора/порядка — платформенные _Демо. Старый writer выпускал
    // ~19 элементов для Catalog и ~20 для Document без StandardAttributes и
    // в неверном порядке xs:sequence (риск отказа full-load).

    /** Индекс открывающего тега {@code <name>} или {@code <name/>} в XML (или -1). */
    private static int idx(String xml, String tag) {
        int open = xml.indexOf("<" + tag + ">");
        int selfClose = xml.indexOf("<" + tag + "/>");
        if (open < 0) return selfClose;
        if (selfClose < 0) return open;
        return Math.min(open, selfClose);
    }

    @Test
    void compile_catalog_emitsKeyPropertiesIncludingStandardAttributes() throws IOException {
        Path json = writeJson("kc.json", "{\"type\":\"Catalog\",\"name\":\"test_Полн\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Catalogs/test_Полн.xml"));
        // Ключевые недостающие ранее свойства
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<xr:StandardAttribute name=\"Ref\">");
        assertThat(xml).contains("<xr:StandardAttribute name=\"Description\">");
        assertThat(xml).contains("<InputByString>");
        assertThat(xml).contains("<DefaultObjectForm/>");
        assertThat(xml).contains("<AuxiliaryFolderChoiceForm/>");
        assertThat(xml).contains("<DataHistory>DontUse</DataHistory>");
        assertThat(xml).contains("<ExecuteAfterWriteDataHistoryVersionProcessing>false");
        // Каталог требует минимум 6 стандартных реквизитов (Ref/DeletionMark/Code/...)
        assertThat(countOccurrences(xml, "<xr:StandardAttribute name=")).isGreaterThanOrEqualTo(6);
    }

    @Test
    void compile_document_registerRecordsBeforePrivilegedModeFlags() throws IOException {
        // КРИТИЧНО (D-6): RegisterRecords ДОЛЖЕН идти ПОСЛЕ SequenceFilling и
        // ПЕРЕД PostInPrivilegedMode/UnpostInPrivilegedMode. Старый writer ставил
        // Post*/Unpost* до RegisterRecords — платформа отвергает такой xs:sequence.
        Path json = writeJson("kd.json",
                "{\"type\":\"Document\",\"name\":\"test_Док\",\"registerRecords\":[\"AccumulationRegister.X\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Documents/test_Док.xml"));

        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");

        int seqFilling = idx(xml, "SequenceFilling");
        int regRecords = idx(xml, "RegisterRecords");
        int postPriv = idx(xml, "PostInPrivilegedMode");
        int unpostPriv = idx(xml, "UnpostInPrivilegedMode");

        assertThat(seqFilling).isGreaterThan(0);
        assertThat(regRecords).isGreaterThan(seqFilling);
        assertThat(postPriv).isGreaterThan(regRecords);
        assertThat(unpostPriv).isGreaterThan(postPriv);
    }

    @Test
    void compile_document_satisfiesStandardAttributesMinimum() throws IOException {
        Path json = writeJson("ds.json", "{\"type\":\"Document\",\"name\":\"test_ДокСА\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Documents/test_ДокСА.xml"));
        // Document требует минимум 4 (Ref/DeletionMark/Number/Date) — у нас 5
        assertThat(countOccurrences(xml, "<xr:StandardAttribute name=")).isGreaterThanOrEqualTo(4);
    }

    @Test
    void compile_accumulationRegister_registerTypeDefaultIsBalance() throws IOException {
        // D-8: дефолт RegisterType=Balance (не "Balances" — фантомное значение)
        Path json = writeJson("ar.json", "{\"type\":\"AccumulationRegister\",\"name\":\"test_РН\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("AccumulationRegisters/test_РН.xml"));
        assertThat(xml).contains("<RegisterType>Balance</RegisterType>");
        assertThat(xml).doesNotContain("<RegisterType>Balances</RegisterType>");
        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
    }

    @Test
    void compile_accumulationRegister_normalizesBalancesAlias() throws IOException {
        Path json = writeJson("ar2.json",
                "{\"type\":\"AccumulationRegister\",\"name\":\"test_РН2\",\"registerType\":\"Balances\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("AccumulationRegisters/test_РН2.xml"));
        assertThat(xml).contains("<RegisterType>Balance</RegisterType>");
    }

    @Test
    void compile_accumulationRegister_balanceEmitsRecordTypeFirst() throws IOException {
        // Грунт-труф _ДемоОстаткиТоваровВМестахХранения: у balance-регистра
        // RecordType — первый StandardAttribute (перед Active).
        Path json = writeJson("arBal.json",
                "{\"type\":\"AccumulationRegister\",\"name\":\"test_РНОст\",\"registerType\":\"Balance\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("AccumulationRegisters/test_РНОст.xml"));
        assertThat(xml).contains("<xr:StandardAttribute name=\"RecordType\">");
        assertThat(xml.indexOf("name=\"RecordType\""))
                .as("RecordType перед Active")
                .isLessThan(xml.indexOf("name=\"Active\""));
    }

    @Test
    void compile_accumulationRegister_turnoverHasNoRecordType() throws IOException {
        // Оборотный регистр (_ДемоОборотыПоСчетамНаОплату) RecordType НЕ пишет.
        Path json = writeJson("arTurn.json",
                "{\"type\":\"AccumulationRegister\",\"name\":\"test_РНОб\",\"registerType\":\"Turnovers\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("AccumulationRegisters/test_РНОб.xml"));
        assertThat(xml).contains("<RegisterType>Turnovers</RegisterType>");
        assertThat(xml).doesNotContain("name=\"RecordType\"");
        assertThat(xml).contains("name=\"Active\"");
    }

    @Test
    void compile_informationRegister_emitsFormsAndStandardAttributes() throws IOException {
        Path json = writeJson("ir.json", "{\"type\":\"InformationRegister\",\"name\":\"test_РС\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("InformationRegisters/test_РС.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<DefaultRecordForm/>");
        assertThat(xml).contains("<StandardAttributes>");
        // EditType и формы идут ДО периодичности (порядок _Демо)
        assertThat(idx(xml, "EditType")).isLessThan(idx(xml, "InformationRegisterPeriodicity"));
        assertThat(idx(xml, "StandardAttributes")).isLessThan(idx(xml, "InformationRegisterPeriodicity"));
    }

    @Test
    void compile_calculationRegister_chartAfterScheduleBlock() throws IOException {
        // Порядок _Демо: Periodicity → ActionPeriod/BasePeriod → Schedule* →
        // ChartOfCalculationTypes (а не Chart первым, как было раньше).
        Path json = writeJson("cr.json",
                "{\"type\":\"CalculationRegister\",\"name\":\"test_РР\","
                + "\"chartOfCalculationTypes\":\"ChartOfCalculationTypes.X\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("CalculationRegisters/test_РР.xml"));
        assertThat(xml).contains("<Schedule/>");
        assertThat(xml).contains("<ScheduleValue/>");
        assertThat(xml).contains("<ScheduleDate/>");
        assertThat(idx(xml, "Periodicity")).isLessThan(idx(xml, "Schedule"));
        assertThat(idx(xml, "Schedule")).isLessThan(idx(xml, "ChartOfCalculationTypes"));
        assertThat(xml).contains("<StandardAttributes>");
    }

    @Test
    void compile_enum_emitsUseStandardCommandsAndStandardAttributes() throws IOException {
        Path json = writeJson("enf.json",
                "{\"type\":\"Enum\",\"name\":\"test_ПерП\",\"values\":[\"A\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Enums/test_ПерП.xml"));
        assertThat(xml).contains("<UseStandardCommands>false</UseStandardCommands>");
        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<DefaultListForm/>");
    }

    private static int countOccurrences(String haystack, String needle) {
        int c = 0, i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) { c++; i += needle.length(); }
        return c;
    }

    // ─── W1 остаточный долг: редкие типы (ChartOf*/ExchangePlan/BP/Task/Journal/
    //     Report/DataProcessor/Constant/WebService) — набор + порядок Properties и
    //     StandardAttributes по грунт-труфу _Демо. ────────────────────────────────

    @Test
    void compile_chartOfCharacteristicTypes_emitsStandardAttributesAndForms() throws IOException {
        Path json = writeJson("cct.json",
                "{\"type\":\"ChartOfCharacteristicTypes\",\"name\":\"test_ВидыХар\","
                + "\"valueTypes\":[\"CatalogRef.X\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("ChartsOfCharacteristicTypes/test_ВидыХар.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<StandardAttributes>");
        // минимум: первым стандартным реквизитом грунт-труфа идёт PredefinedDataName, затем ValueType
        assertThat(idx(xml, "StandardAttributes")).isLessThan(idx(xml, "PredefinedDataUpdate"));
        // Type/CharacteristicExtValues идут ДО блока кода
        assertThat(idx(xml, "CharacteristicExtValues")).isLessThan(idx(xml, "CodeLength"));
        // у плана видов характеристик есть папочные формы
        assertThat(xml).contains("<DefaultFolderForm/>");
        // CodeType отсутствует (грунт-труф его не пишет)
        assertThat(xml).doesNotContain("<CodeType>");
    }

    @Test
    void compile_chartOfAccounts_emitsStandardTabularSectionsAndAttributes() throws IOException {
        Path json = writeJson("coa.json", "{\"type\":\"ChartOfAccounts\",\"name\":\"test_План\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("ChartsOfAccounts/test_План.xml"));
        assertThat(xml).contains("<StandardAttributes>");
        // фиксированная стандартная ТЧ ExtDimensionTypes (вербатим по грунт-труфу)
        assertThat(xml).contains("<xr:StandardTabularSection name=\"ExtDimensionTypes\">");
        assertThat(xml).contains("<xr:StandardAttribute name=\"ExtDimensionType\">");
        // StandardTabularSections идёт ПОСЛЕ StandardAttributes/Characteristics и ДО PredefinedDataUpdate
        assertThat(idx(xml, "StandardTabularSections")).isLessThan(idx(xml, "PredefinedDataUpdate"));
        // у плана счетов НЕТ Hierarchical/Autonumbering (грунт-труф их не пишет)
        assertThat(xml).doesNotContain("<Hierarchical>");
        assertThat(xml).doesNotContain("<Autonumbering>");
        // AutoOrderByCode/OrderLength присутствуют
        assertThat(xml).contains("<AutoOrderByCode>");
        assertThat(xml).contains("<OrderLength>");
    }

    @Test
    void compile_chartOfCalculationTypes_chartTabularSectionsAndDependenceDefault() throws IOException {
        Path json = writeJson("ccalc.json",
                "{\"type\":\"ChartOfCalculationTypes\",\"name\":\"test_ВидыРасч\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("ChartsOfCalculationTypes/test_ВидыРасч.xml"));
        // D-7: дефолт DontUse, не фантомное NotUsed
        assertThat(xml).contains("<DependenceOnCalculationTypes>DontUse</DependenceOnCalculationTypes>");
        assertThat(xml).doesNotContain("NotUsed");
        // три фиксированные стандартные ТЧ
        assertThat(xml).contains("<xr:StandardTabularSection name=\"LeadingCalculationTypes\">");
        assertThat(xml).contains("<xr:StandardTabularSection name=\"DisplacingCalculationTypes\">");
        assertThat(xml).contains("<xr:StandardTabularSection name=\"BaseCalculationTypes\">");
        assertThat(xml).contains("<StandardAttributes>");
        // BaseCalculationTypes(prop) и ActionPeriodUse идут ДО StandardAttributes
        assertThat(idx(xml, "ActionPeriodUse")).isLessThan(idx(xml, "StandardAttributes"));
    }

    @Test
    void compile_exchangePlan_emitsStandardAttributesBeforeDistributedFlags() throws IOException {
        Path json = writeJson("ep.json", "{\"type\":\"ExchangePlan\",\"name\":\"test_Обмен\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("ExchangePlans/test_Обмен.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<StandardAttributes>");
        // КРИТИЧНО: StandardAttributes/Characteristics/BasedOn идут ДО DistributedInfoBase
        assertThat(idx(xml, "StandardAttributes")).isLessThan(idx(xml, "DistributedInfoBase"));
        assertThat(idx(xml, "BasedOn")).isLessThan(idx(xml, "DistributedInfoBase"));
        // минимум стандартного реквизита — ThisNode (первый в грунт-труфе)
        assertThat(xml).contains("<xr:StandardAttribute name=\"ThisNode\">");
    }

    @Test
    void compile_businessProcess_emitsStandardAttributesAndCreateTaskPrivileged() throws IOException {
        Path json = writeJson("bp.json", "{\"type\":\"BusinessProcess\",\"name\":\"test_БП\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("BusinessProcesses/test_БП.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<xr:StandardAttribute name=\"Started\">");
        assertThat(xml).contains("<CreateTaskInPrivilegedMode>true</CreateTaskInPrivilegedMode>");
        // CreateTaskInPrivilegedMode идёт сразу после Task и ДО DataLockControlMode
        assertThat(idx(xml, "Task")).isLessThan(idx(xml, "CreateTaskInPrivilegedMode"));
        assertThat(idx(xml, "CreateTaskInPrivilegedMode")).isLessThan(idx(xml, "DataLockControlMode"));
        // формы идут ДО блока нумерации (порядок грунт-труфа)
        assertThat(idx(xml, "DefaultObjectForm")).isLessThan(idx(xml, "NumberType"));
    }

    @Test
    void compile_task_emitsStandardAttributesAndAddressingBlock() throws IOException {
        // У типа Task НЕТ _Демо-объекта в репозитории; образец — Tasks/ЗадачаИсполнителя.
        Path json = writeJson("task.json", "{\"type\":\"Task\",\"name\":\"test_Задача\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Tasks/test_Задача.xml"));
        assertThat(xml).contains("<StandardAttributes>");
        assertThat(xml).contains("<xr:StandardAttribute name=\"Executed\">");
        // адресные поля присутствуют всегда (пустыми), ДО StandardAttributes
        assertThat(xml).contains("<Addressing/>");
        assertThat(xml).contains("<CurrentPerformer/>");
        assertThat(idx(xml, "Addressing")).isLessThan(idx(xml, "StandardAttributes"));
        assertThat(xml).contains("<TaskNumberAutoPrefix>");
    }

    @Test
    void compile_documentJournal_emitsRegisteredDocsAndPresentations() throws IOException {
        Path json = writeJson("dj.json",
                "{\"type\":\"DocumentJournal\",\"name\":\"test_Журнал\","
                + "\"registeredDocuments\":[\"Document.X\"]}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("DocumentJournals/test_Журнал.xml"));
        // формы идут ДО UseStandardCommands (порядок грунт-труфа), затем RegisteredDocuments
        assertThat(idx(xml, "DefaultForm")).isLessThan(idx(xml, "UseStandardCommands"));
        assertThat(idx(xml, "UseStandardCommands")).isLessThan(idx(xml, "RegisteredDocuments"));
        assertThat(xml).contains("<xr:Item xsi:type=\"xr:MDObjectRef\">Document.X</xr:Item>");
        // хвост: IncludeHelpInContents + презентации списка ПОСЛЕ RegisteredDocuments
        assertThat(idx(xml, "RegisteredDocuments")).isLessThan(idx(xml, "ListPresentation"));
        // журнал НЕ содержит StandardAttributes в Properties (грунт-труф его не пишет)
        assertThat(xml).doesNotContain("<StandardAttributes>");
    }

    @Test
    void compile_report_emitsFormsAlwaysAndVariantsStorage() throws IOException {
        Path json = writeJson("rep.json", "{\"type\":\"Report\",\"name\":\"test_Отчет\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Reports/test_Отчет.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        // формы/хранилища присутствуют всегда (пустыми), даже если не заданы
        assertThat(xml).contains("<DefaultForm/>");
        assertThat(xml).contains("<MainDataCompositionSchema/>");
        assertThat(xml).contains("<VariantsStorage/>");
        assertThat(xml).contains("<SettingsStorage/>");
        assertThat(xml).contains("<ExtendedPresentation/>");
        assertThat(idx(xml, "UseStandardCommands")).isLessThan(idx(xml, "DefaultForm"));
    }

    @Test
    void compile_dataProcessor_emitsUseStandardCommandsAndForms() throws IOException {
        Path json = writeJson("dp.json", "{\"type\":\"DataProcessor\",\"name\":\"test_Обработка\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("DataProcessors/test_Обработка.xml"));
        assertThat(xml).contains("<UseStandardCommands>true</UseStandardCommands>");
        assertThat(xml).contains("<DefaultForm/>");
        assertThat(xml).contains("<AuxiliaryForm/>");
        assertThat(xml).contains("<IncludeHelpInContents>false</IncludeHelpInContents>");
        assertThat(xml).contains("<ExtendedPresentation/>");
    }

    @Test
    void compile_constant_emitsChoiceFoldersAndItemsAndDataHistoryTail() throws IOException {
        Path json = writeJson("const.json",
                "{\"type\":\"Constant\",\"name\":\"test_Конст\",\"valueType\":\"String\",\"length\":50}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("Constants/test_Конст.xml"));
        // голова: UseStandardCommands/DefaultForm/ExtendedPresentation/Explanation между Type и PasswordMode
        assertThat(idx(xml, "Type")).isLessThan(idx(xml, "UseStandardCommands"));
        assertThat(idx(xml, "UseStandardCommands")).isLessThan(idx(xml, "PasswordMode"));
        // ChoiceFoldersAndItems между FillChecking и ChoiceParameterLinks
        assertThat(xml).contains("<ChoiceFoldersAndItems>Items</ChoiceFoldersAndItems>");
        assertThat(idx(xml, "FillChecking")).isLessThan(idx(xml, "ChoiceFoldersAndItems"));
        assertThat(idx(xml, "ChoiceFoldersAndItems")).isLessThan(idx(xml, "ChoiceParameterLinks"));
        // хвост DataHistory
        assertThat(xml).contains("<DataHistory>DontUse</DataHistory>");
        assertThat(idx(xml, "DataLockControlMode")).isLessThan(idx(xml, "DataHistory"));
    }

    @Test
    void compile_webService_emitsDescriptorFileNameBetweenXdtoAndReuse() throws IOException {
        Path json = writeJson("ws.json",
                "{\"type\":\"WebService\",\"name\":\"test_ВебСервис\",\"namespace\":\"http://x\"}");
        new MetaWriter().compile(json, tempDir);
        String xml = read(tempDir.resolve("WebServices/test_ВебСервис.xml"));
        // XDTOPackages и DescriptorFileName присутствуют всегда (пустыми)
        assertThat(xml).contains("<XDTOPackages/>");
        assertThat(xml).contains("<DescriptorFileName/>");
        // порядок: XDTOPackages → DescriptorFileName → ReuseSessions
        assertThat(idx(xml, "XDTOPackages")).isLessThan(idx(xml, "DescriptorFileName"));
        assertThat(idx(xml, "DescriptorFileName")).isLessThan(idx(xml, "ReuseSessions"));
    }
}

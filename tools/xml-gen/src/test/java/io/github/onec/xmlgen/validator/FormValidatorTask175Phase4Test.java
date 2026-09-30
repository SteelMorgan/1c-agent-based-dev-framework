package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 Phase 4 (Tester): edge-кейсы W-02 (XG-38) и W-03 (XG-37),
 * не покрытые {@code FormValidatorTask175Test} (Phase 3b).
 *
 * <p>Кейс 1 — граница W-02: upstream 5f7ee6fc — {@code [int]$el.Id -lt 1000000}
 * (СТРОГО меньше). Элемент с id ровно 1000000 — СОБСТВЕННЫЙ элемент расширения
 * (платформа выдаёт own-элементам id от 1000000), skip FORM-102 на нём
 * НЕ активируется.</p>
 *
 * <p>Кейс 2 — W-03: форма с ОБОИМИ External*-типами сразу
 * (ExternalDataProcessorObject + ExternalReportObject) в config-контексте —
 * FORM-128 обязан сработать на каждый тип независимо (upstream Check 12
 * итерирует все {@code v8:Type} узлы).</p>
 *
 * <p>Синтетические минимальные формы — по прецеденту защитных кейсов F-02/F-03
 * Phase 3b (test fixtures for tools, исключение no-manual-xml-edit).</p>
 */
class FormValidatorTask175Phase4Test {

    private final FormValidator validator = new FormValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    /** Минимальная форма с одним InputField (паттерн F-02 из 3b). */
    private List<ValidationIssue> validateSyntheticObjectDataPathForm(
            long elementId, boolean withBaseForm) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\" version=\"2.20\">\n"
                + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                + "\t<ChildItems>\n"
                + "\t\t<InputField name=\"ПолеОбъекта\" id=\"" + elementId + "\">\n"
                + "\t\t\t<DataPath>Объект.НетТакогоРеквизита</DataPath>\n"
                + "\t\t</InputField>\n"
                + "\t</ChildItems>\n"
                + "\t<Attributes/>\n"
                + (withBaseForm
                        ? "\t<BaseForm version=\"2.17\">\n"
                        + "\t\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                        + "\t\t<ChildItems/>\n"
                        + "\t\t<Attributes/>\n"
                        + "\t</BaseForm>\n"
                        : "")
                + "</Form>\n";
        Path formFile = tempDir.resolve("synthetic-" + elementId + "-" + withBaseForm + ".xml");
        Files.writeString(formFile, xml);
        return validator.validate(reader.parse(formFile), ValidationLevel.SEMANTIC);
    }

    /**
     * Граничный кейс W-02: id РОВНО 1000000 при {@code <BaseForm>} —
     * это уже own-элемент ({@code -lt 1000000} строго, A-6), FORM-102 СОХРАНЯЕТСЯ.
     * Защита от смещения границы на {@code <=} при будущих правках.
     */
    @Test
    void w02_elementIdExactly1000000_isOwnElement_form102Kept() throws Exception {
        List<ValidationIssue> issues = validateSyntheticObjectDataPathForm(1_000_000L, true);

        assertThat(issues)
                .as("id=1000000 — граница СТРОГОГО сравнения upstream "
                        + "([int]$el.Id -lt 1000000): элемент собственный, "
                        + "FORM-102 обязан сохраниться")
                .anyMatch(i -> "FORM-102".equals(i.getCode())
                        && i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("Объект.НетТакогоРеквизита"));
    }

    /**
     * Минимальная форма с ДВУМЯ реквизитами External*-типов в config-контексте
     * (паттерн validateSyntheticTypeInConfigContext из 3b, расширенный до двух
     * Attributes).
     */
    private List<ValidationIssue> validateFormWithBothExternalTypes() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Form xmlns=\"http://v8.1c.ru/8.3/xcf/logform\""
                + " xmlns:v8=\"http://v8.1c.ru/8.1/data/core\""
                + " xmlns:cfg=\"http://v8.1c.ru/8.1/data/enterprise/current-config\""
                + " version=\"2.20\">\n"
                + "\t<AutoCommandBar name=\"ФормаКоманднаяПанель\" id=\"-1\"/>\n"
                + "\t<ChildItems/>\n"
                + "\t<Attributes>\n"
                + "\t\t<Attribute name=\"Объект\" id=\"1\">\n"
                + "\t\t\t<Type>\n"
                + "\t\t\t\t<v8:Type>cfg:ExternalDataProcessorObject.биг_Обработка</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t\t<MainAttribute>true</MainAttribute>\n"
                + "\t\t</Attribute>\n"
                + "\t\t<Attribute name=\"Отчет\" id=\"2\">\n"
                + "\t\t\t<Type>\n"
                + "\t\t\t\t<v8:Type>cfg:ExternalReportObject.биг_Отчет</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t</Attribute>\n"
                + "\t</Attributes>\n"
                + "</Form>\n";
        Path root = tempDir.resolve("cfg-ctx-both-external");
        Path formFile = root.resolve("DataProcessors/биг_Обработка/Forms/Форма/Ext/Form.xml");
        Files.createDirectories(formFile.getParent());
        Files.writeString(formFile, xml);
        Files.writeString(root.resolve("Configuration.xml"),
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                        + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                        + "\t\t<Properties><Name>Тест</Name></Properties>\n"
                        + "\t\t<ChildObjects/>\n"
                        + "\t</Configuration>\n"
                        + "</MetaDataObject>\n");
        return validator.validate(reader.parse(formFile), ValidationLevel.SEMANTIC);
    }

    /**
     * Edge-кейс W-03: оба External*-типа в одной форме config-контекста —
     * FORM-128 на КАЖДЫЙ тип (Check 12 обходит все v8:Type, ошибки независимы).
     */
    @Test
    void w03_bothExternalTypesInConfigContext_bothFlagged() throws Exception {
        List<ValidationIssue> issues = validateFormWithBothExternalTypes();

        assertThat(issues)
                .as("FORM-128 для cfg:ExternalDataProcessorObject.* — первый тип")
                .anyMatch(i -> "FORM-128".equals(i.getCode())
                        && i.getMessage().contains("ExternalDataProcessorObject.биг_Обработка"));
        assertThat(issues)
                .as("FORM-128 для cfg:ExternalReportObject.* — второй тип, независимо от первого")
                .anyMatch(i -> "FORM-128".equals(i.getCode())
                        && i.getMessage().contains("ExternalReportObject.биг_Отчет"));
        assertThat(issues.stream().filter(i -> "FORM-128".equals(i.getCode())).count())
                .as("ровно два FORM-128 — по одному на каждый External*-тип")
                .isEqualTo(2);
    }
}

package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-175 W-03 (XG-37): Check 12 upstream (form-validate v1.3) в минимальном объёме —
 * контекстная валидация External*-типов (коммиты Широкова dd88f789, 3bd69baa, d5aacc9e).
 *
 * <p>Семантика по d5aacc9e: контекст определяется подъёмом от файла формы вверх
 * (до 15 уровней) в поисках {@code Configuration.xml}:</p>
 * <ul>
 *   <li>config-контекст + {@code cfg:ExternalDataProcessorObject.*} /
 *       {@code cfg:ExternalReportObject.*} → <b>ERROR</b> («use
 *       DataProcessorObject/ReportObject instead») — XDTO-исключение платформы;</li>
 *   <li>EPF/ERF-контекст (нет Configuration.xml выше) — тип валиден, замечаний нет.</li>
 * </ul>
 *
 * <p>Фикстура {@code /forms/epf-external-object-form.xml} — байт-в-байт копия
 * Designer-цикловой формы {@code биг_УборщикТестовыхДанных} (артефакт TASK-173)
 * с {@code <v8:Type>cfg:ExternalDataProcessorObject.биг_УборщикТестовыхДанных</v8:Type>}.</p>
 *
 * <p>W-02 (skip FORM-102 для base-элементов borrowed-форм) добавляется в этот же
 * класс отдельными методами {@code w02_*}.</p>
 */
class FormValidatorTask175Test {

    private final FormValidator validator = new FormValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @TempDir
    Path tempDir;

    private Path fixture(String name) throws Exception {
        return Path.of(Objects.requireNonNull(
                getClass().getResource("/forms/" + name),
                "Fixture not found in test resources: /forms/" + name).toURI());
    }

    /**
     * Разместить фикстуру формы в каталожной структуре конфигурации/EPF(ERF)
     * и провалидировать на уровне SEMANTIC.
     *
     * @param fixtureName          имя фикстуры в {@code /forms/}
     * @param relativeFormPath     путь формы относительно корня контекста
     * @param withConfigurationXml true — создать Configuration.xml в корне (config-контекст)
     */
    private List<ValidationIssue> validateInContext(String fixtureName, String relativeFormPath,
                                                    boolean withConfigurationXml) throws Exception {
        Path root = tempDir.resolve((withConfigurationXml ? "cfg-ctx-" : "epf-ctx-")
                + fixtureName.replace(".xml", ""));
        Path formFile = root.resolve(relativeFormPath);
        Files.createDirectories(formFile.getParent());
        Files.copy(fixture(fixtureName), formFile);
        if (withConfigurationXml) {
            Files.writeString(root.resolve("Configuration.xml"),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                            + "<MetaDataObject xmlns=\"http://v8.1c.ru/8.3/MDClasses\" version=\"2.20\">\n"
                            + "\t<Configuration uuid=\"00000000-0000-0000-0000-000000000001\">\n"
                            + "\t\t<Properties><Name>Тест</Name></Properties>\n"
                            + "\t\t<ChildObjects/>\n"
                            + "\t</Configuration>\n"
                            + "</MetaDataObject>\n");
        }
        return validator.validate(reader.parse(formFile), ValidationLevel.SEMANTIC);
    }

    /**
     * Red-кейс (b) Test Plan W-03: форма с {@code cfg:ExternalDataProcessorObject.*}
     * под каталогом с Configuration.xml → обязана быть ERROR (d5aacc9e:
     * «External* type in configuration context»). Сегодня — ложный PASS (XG-37).
     */
    @Test
    void w03_externalTypeInConfigContext_isError() throws Exception {
        List<ValidationIssue> issues = validateInContext("epf-external-object-form.xml",
                "DataProcessors/биг_Обработка/Forms/Форма/Ext/Form.xml", true);

        assertThat(issues)
                .as("cfg:ExternalDataProcessorObject в контексте конфигурации — XDTO-исключение "
                        + "платформы, валидатор обязан давать ERROR (Check 12, d5aacc9e)")
                .anyMatch(i -> i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("ExternalDataProcessorObject"));
    }

    /**
     * Регрессионный кейс (a) Test Plan W-03: та же форма БЕЗ Configuration.xml выше
     * (EPF-контекст) → External*-тип валиден, ни ERROR, ни WARNING по нему нет.
     * Кейс проходит и сегодня (проверки нет вовсе); фиксируется, чтобы порт Check 12
     * не сломал EPF-контекст.
     */
    @Test
    void w03_externalTypeInEpfContext_noIssue() throws Exception {
        List<ValidationIssue> issues = validateInContext("epf-external-object-form.xml",
                "DataProcessors/биг_Обработка/Forms/Форма/Ext/Form.xml", false);

        assertThat(issues)
                .as("в EPF/ERF-контексте cfg:ExternalDataProcessorObject — корректный тип "
                        + "главного реквизита, замечаний быть не должно")
                .noneMatch(i -> i.getMessage().contains("ExternalDataProcessorObject"));
    }

    /**
     * Red-кейс (b2) — второй External*-тип (добавлен по F-03 cross-review, минимум A-7
     * spec: «ОБА типа»): форма с {@code cfg:ExternalReportObject.*} под каталогом
     * с Configuration.xml → обязана быть ERROR («use ReportObject instead», d5aacc9e).
     * Сегодня — ложный PASS (XG-37). Фикстура — синтетическая копия EPF-фикстуры
     * с заменённым типом (источник в заголовке файла, существующая байт-копия не тронута).
     */
    @Test
    void w03_externalReportTypeInConfigContext_isError() throws Exception {
        List<ValidationIssue> issues = validateInContext("erf-external-report-form.xml",
                "Reports/биг_Отчет/Forms/Форма/Ext/Form.xml", true);

        assertThat(issues)
                .as("cfg:ExternalReportObject в контексте конфигурации — XDTO-исключение "
                        + "платформы, валидатор обязан давать ERROR (Check 12, d5aacc9e); "
                        + "фикс только для ExternalDataProcessorObject — половина минимума A-7")
                .anyMatch(i -> i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("ExternalReportObject"));
    }

    /**
     * Регрессионный кейс (a2): та же report-форма БЕЗ Configuration.xml выше
     * (ERF-контекст) → {@code ExternalReportObject} валиден, замечаний по нему нет.
     * Проходит и сегодня; защищает порт Check 12 от поломки ERF-контекста.
     */
    @Test
    void w03_externalReportTypeInErfContext_noIssue() throws Exception {
        List<ValidationIssue> issues = validateInContext("erf-external-report-form.xml",
                "Reports/биг_Отчет/Forms/Форма/Ext/Form.xml", false);

        assertThat(issues)
                .as("в ERF-контексте cfg:ExternalReportObject — корректный тип "
                        + "главного реквизита, замечаний быть не должно")
                .noneMatch(i -> i.getMessage().contains("ExternalReportObject"));
    }

    // ─── Кейс F-03 (cross-review кода 3d, WARN): upstream Check 12
    // (form-validate.py ~689) сравнивает ТОЧНЫЙ префикс до точки:
    //   suffix = tv[4:]; prefix = suffix.split(".")[0]
    //   if is_config_context and prefix in ('ExternalDataProcessorObject',
    //                                       'ExternalReportObject'): ERROR
    // Java использует startsWith("cfg:ExternalDataProcessorObject") — шире:
    // суффиксное имя типа cfg:ExternalDataProcessorObjectФу.Что зацепится
    // ложно. Upstream для такого префикса дал бы WARN «unrecognized cfg
    // prefix» (словари Check 12 не портированы — D-4), но НЕ FORM-128. ───

    /**
     * Минимальная синтетическая форма (по образцу w02-синтеза) с одним
     * реквизитом заданного {@code v8:Type}, размещённая в config-контексте
     * (Configuration.xml в корне). Грабля reader'а: xmlns-декларации
     * (default logform + v8) на корне обязательны.
     */
    private List<ValidationIssue> validateSyntheticTypeInConfigContext(String typeValue)
            throws Exception {
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
                + "\t\t\t\t<v8:Type>" + typeValue + "</v8:Type>\n"
                + "\t\t\t</Type>\n"
                + "\t\t\t<MainAttribute>true</MainAttribute>\n"
                + "\t\t</Attribute>\n"
                + "\t</Attributes>\n"
                + "</Form>\n";
        Path root = tempDir.resolve("cfg-ctx-synthetic-type");
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
     * Red-кейс F-03 (негативный): суффиксное имя типа
     * {@code cfg:ExternalDataProcessorObjectФу.Что} в config-контексте
     * НЕ должно флажиться FORM-128 — точный префикс до точки
     * («ExternalDataProcessorObjectФу») не равен «ExternalDataProcessorObject»
     * (form-validate.py ~689: {@code suffix.split(".")[0]}). Сегодня Java
     * startsWith ловит его ложно. Отсутствие иных замечаний (upstream-WARN
     * «unrecognized cfg prefix») НЕ проверяется — словари вне объёма D-4.
     */
    @Test
    void w03_suffixedExternalTypeName_notForm128() throws Exception {
        List<ValidationIssue> issues =
                validateSyntheticTypeInConfigContext("cfg:ExternalDataProcessorObjectФу.Что");

        assertThat(issues)
                .as("FORM-128 обязан матчить ТОЧНЫЙ префикс до точки, а не startsWith: "
                        + "cfg:ExternalDataProcessorObjectФу.Что — НЕ External*-тип "
                        + "(upstream Check 12: prefix = suffix.split(\".\")[0])")
                .noneMatch(i -> "FORM-128".equals(i.getCode()));
    }

    // ════════════════════════════════════════════════════════════════════
    // W-02 (XG-38): FORM-102 skip для base-элементов borrowed-форм
    // (коммит Широкова 5f7ee6fc, только .ps1 — эталон семантики A-5;
    // граница base-элемента id < 1000000 — A-6, подтверждена diff'ом)
    // ════════════════════════════════════════════════════════════════════

    private List<ValidationIssue> validateBorrowedFixture() throws Exception {
        return validator.validate(
                reader.parse(fixture("borrowed-baseform.xml")), ValidationLevel.SEMANTIC);
    }

    /**
     * Red-кейс W-02: в форме с {@code <BaseForm>} элементы с id &lt; 1000000 —
     * базовые; их DataPath ({@code Объект.*}) отсутствуют в пустых Attributes
     * расширения ПО ПОСТРОЕНИЮ. FORM-102 на них — ложная ошибка, должна
     * пропускаться (skip, 5f7ee6fc). Сегодня — ложные ERROR (XG-38).
     */
    @Test
    void w02_borrowedForm_noForm102OnBaseElements() throws Exception {
        List<ValidationIssue> issues = validateBorrowedFixture();

        assertThat(issues)
                .as("FORM-102 не должен срабатывать на base-элементах (id < 1000000) "
                        + "формы с BaseForm — их реквизиты живут в базовой конфигурации")
                .noneMatch(i -> "FORM-102".equals(i.getCode())
                        && i.getMessage().contains("'Объект'"));
    }

    /**
     * Негативный кейс W-02 (обязателен по Test Plan): СОБСТВЕННЫЙ элемент
     * расширения (id ≥ 1000000) с битым DataPath — FORM-102 СОХРАНЯЕТСЯ.
     * Защита от чрезмерного skip: «borrowed-форма» не означает «всё валидно».
     */
    @Test
    void w02_borrowedForm_form102KeptForOwnElement() throws Exception {
        List<ValidationIssue> issues = validateBorrowedFixture();

        assertThat(issues)
                .as("для собственного элемента id=1000001 с DataPath 'НетТакогоРеквизита' "
                        + "ошибка FORM-102 обязана сохраниться")
                .anyMatch(i -> "FORM-102".equals(i.getCode())
                        && i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("НетТакогоРеквизита"));
    }

    // ─── Защитные кейсы F-02 (cross-review): фикс «разрешить все Объект.*» —
    // НЕВЕРНЫЙ. Семантика 5f7ee6fc — skip ЧИСТО по двум осям:
    // hasBaseForm && [int]$el.Id -lt 1000000; содержимое DataPath в условии
    // НЕ участвует. Оба кейса проходят сегодня, обязаны проходить после
    // правильного фикса и падают при сверхшироком. Минимальные формы
    // синтезируются в тесте (новых байт-копий не требуется). ───

    /**
     * Минимальная синтетическая форма для защитных кейсов F-02:
     * один InputField с DataPath {@code Объект.НетТакогоРеквизита}
     * при пустых {@code <Attributes/>}.
     *
     * @param elementId    id элемента (ось id &lt; 1000000 / ≥ 1000000)
     * @param withBaseForm добавить блок {@code <BaseForm>} (ось hasBaseForm)
     */
    private List<ValidationIssue> validateSyntheticObjectDataPathForm(
            int elementId, boolean withBaseForm) throws Exception {
        // ГРАБЛЯ (из контекста 3b): XmlStructureReader namespace-aware —
        // xmlns-декларация на корне обязательна, иначе XmlParseException.
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
     * Защитный кейс F-02 (ось id): СОБСТВЕННЫЙ элемент (id=1000001 ≥ 1000000)
     * с DataPath {@code Объект.НетТакогоРеквизита} в форме С {@code <BaseForm>} —
     * FORM-102 СОХРАНЯЕТСЯ: skip 5f7ee6fc распространяется только на id &lt; 1000000,
     * own-элементы валидируются всегда, даже если DataPath начинается с «Объект.».
     */
    @Test
    void w02_ownElementWithObjectDataPath_form102Kept() throws Exception {
        List<ValidationIssue> issues = validateSyntheticObjectDataPathForm(1000001, true);

        assertThat(issues)
                .as("own-элемент (id ≥ 1000000) с 'Объект.НетТакогоРеквизита' в borrowed-форме: "
                        + "FORM-102 обязан остаться — иначе фикс шире семантики 5f7ee6fc")
                .anyMatch(i -> "FORM-102".equals(i.getCode())
                        && i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("Объект.НетТакогоРеквизита"));
    }

    /**
     * Защитный кейс F-02 (ось BaseForm): форма БЕЗ {@code <BaseForm>} с тем же
     * DataPath {@code Объект.НетТакогоРеквизита} (id=5 &lt; 1000000) при пустых
     * Attributes — FORM-102 СОХРАНЯЕТСЯ: без BaseForm skip не активируется
     * ни для каких id.
     */
    @Test
    void w02_noBaseForm_objectDataPath_form102Kept() throws Exception {
        List<ValidationIssue> issues = validateSyntheticObjectDataPathForm(5, false);

        assertThat(issues)
                .as("форма без BaseForm: 'Объект.НетТакогоРеквизита' при пустых Attributes — "
                        + "FORM-102 обязан остаться (skip 5f7ee6fc активен только при BaseForm)")
                .anyMatch(i -> "FORM-102".equals(i.getCode())
                        && i.getSeverity() == Severity.ERROR
                        && i.getMessage().contains("Объект.НетТакогоРеквизита"));
    }
}

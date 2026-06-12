package io.github.onec.xmlgen.writer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.onec.xmlgen.dsl.SkdDsl;
import io.github.onec.xmlgen.format.OutputFormat;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import io.github.onec.xmlgen.validator.XmlStructureReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-176 (контур B+C, домен SKD) — Red-тесты для {@code skd compile} (writer):
 * S-01 (DesignTimeValue в правом значении фильтра), S-02 (авто-inject
 * SelectedItemAuto/OrderItemAuto в группах структуры) и доказательная диспозиция
 * S-03 (structure-tree explicit xsi:type канон-корректен; table-axis поверхности нет).
 *
 * <p>Источник истины семантики — diff коммитов Широкова (R-S.3) + канон
 * Designer-корпуса {@code src/xml/Reports} (R-176-K.1/K.2). Тесты компилируют
 * SKD из DSL (паттерн {@link SkdWriterTask175Test}) — байт-копии-фикстуры не нужны:
 * поверхность эмиссии проверяется напрямую.</p>
 *
 * <p>Локации (technical-design rev.2, подтверждены чтением кода 2026-06-08):
 * S-01 {@code detectValueType} SkdWriter.java:1081 (единая точка эмиссии типа правой
 * части фильтра, обе call-site :936/:948 сходятся в :975); S-02 {@code writeStructure}
 * ветки :833/:843; S-03 {@code writeStructure}:810 (безусловный xsi:type — канон).</p>
 */
class SkdWriterTask176Test {

    @TempDir
    Path tempDir;

    /** Компиляция SKD JSON → текст Template.xml (паттерн SkdWriterTest/Task175). */
    private String compile(String json) throws Exception {
        return Files.readString(compileToFile(json));
    }

    /** Компиляция SKD JSON → Path к Template.xml (для повторного структурного разбора). */
    private Path compileToFile(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        Path outputXml = tempDir.resolve("Template_" + System.nanoTime() + ".xml");
        new SkdWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return outputXml;
    }

    /**
     * Структурный разбор скомпилированной схемы (F-04: усиление S-01 с substring до
     * структурного — доказываем тип узла {@code dcsset:right}, а не вхождение подстроки).
     */
    private XmlDocument compileDoc(String json) throws Exception {
        return new XmlStructureReader().parse(compileToFile(json));
    }

    /** Рекурсивно собирает все узлы по локальному имени {@code right} (dcsset:right). */
    private static List<XmlNode> collectByLocalName(XmlNode node, String localName) {
        List<XmlNode> acc = new ArrayList<>();
        collectByLocalName(node, localName, acc);
        return acc;
    }

    private static void collectByLocalName(XmlNode node, String localName, List<XmlNode> acc) {
        if (localName.equals(node.getName())) {
            acc.add(node);
        }
        for (XmlNode c : node.getChildren()) {
            collectByLocalName(c, localName, acc);
        }
    }

    /**
     * Структурное доказательство S-01 (F-04): ссылочное правое значение фильтра
     * эмитится РОВНО одним узлом {@code dcsset:right} с {@code xsi:type=
     * "dcscor:DesignTimeValue"} И НЕ как {@code xs:string} одновременно. Проверяется
     * тип узла и его текст через распарсенное дерево, а не наличие подстроки.
     */
    private void assertSingleDesignTimeRight(String json, String expectedValue) throws Exception {
        XmlDocument doc = compileDoc(json);
        List<XmlNode> rights = collectByLocalName(doc.getRoot(), "right");

        assertThat(rights)
                .as("узел dcsset:right должен быть РОВНО один (уникальность, F-04)")
                .hasSize(1);
        XmlNode right = rights.get(0);
        assertThat(right.attr("xsi:type"))
                .as("ссылочное правое значение фильтра — структурно dcscor:DesignTimeValue "
                        + "(a9deeee2), а НЕ xs:string")
                .isEqualTo("dcscor:DesignTimeValue");
        assertThat(right.attr("xsi:type"))
                .as("одновременно как xs:string эмититься НЕ должно")
                .isNotEqualTo("xs:string");
        assertThat(right.getText())
                .as("текст dcsset:right — само ссылочное значение")
                .isEqualTo(expectedValue);
    }

    /** SKD с одним фильтром в основном варианте настроек. */
    private static String filterJson(String filterShorthand) {
        return """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": { "filter": ["%s"] }
                  }]
                }
                """.formatted(filterShorthand);
    }

    // ════════════════════════════════════════════════════════════════════
    // S-01 — dcscor:DesignTimeValue в правом значении фильтра
    // upstream e4dcef8c + 87e636f6 + a9deeee2 (auto-detect DesignTimeValue в
    // filter right, обе ветки Emit-FilterItem). Канон: src/xml/Reports/
    // ПраваРолей/Templates/Макет/Ext/Template.xml — <dcsset:right
    // xsi:type="dcscor:DesignTimeValue">Справочник.ПрофилиГруппДоступа.ПустаяСсылка
    // ════════════════════════════════════════════════════════════════════

    /**
     * Red (S-01 позитив): правая часть фильтра — ссылка на справочник.
     * Сегодня {@code detectValueType} (:1081) возвращает xs:string → платформа
     * не интерпретирует значение как ОМД-ссылку, фильтр не работает в режиме
     * дизайна. После фикса — {@code xsi:type="dcscor:DesignTimeValue"}.
     */
    @Test
    @DisplayName("unit-S01: фильтр со ссылкой на справочник эмитит dcscor:DesignTimeValue")
    void s01_catalogRefRightValue_emitsDesignTimeValue() throws Exception {
        String json = filterJson("Контрагент = Справочник.Контрагенты.ПустаяСсылка");

        // F-04: структурное доказательство (тип узла + уникальность), не только substring.
        assertSingleDesignTimeRight(json, "Справочник.Контрагенты.ПустаяСсылка");

        assertThat(compile(json))
                .as("ссылочное правое значение фильтра должно эмититься как "
                        + "dcscor:DesignTimeValue (a9deeee2), а не xs:string")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "Справочник.Контрагенты.ПустаяСсылка</dcsset:right>");
    }

    /** Red (S-01 позитив): ссылка на план счетов. */
    @Test
    @DisplayName("unit-S01: фильтр со ссылкой на план счетов эмитит dcscor:DesignTimeValue")
    void s01_chartOfAccountsRefRightValue_emitsDesignTimeValue() throws Exception {
        String json = filterJson("Счет = ПланСчетов.Основной.ПустаяСсылка");

        assertSingleDesignTimeRight(json, "ПланСчетов.Основной.ПустаяСсылка");

        assertThat(compile(json))
                .as("ПланСчетов.* в правой части — DesignTimeValue (перечень префиксов по diff, A-3/D-1)")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "ПланСчетов.Основной.ПустаяСсылка</dcsset:right>");
    }

    /** Red (S-01 позитив): значение перечисления (форма {@code .Значение}). */
    @Test
    @DisplayName("unit-S01: фильтр со значением перечисления эмитит dcscor:DesignTimeValue")
    void s01_enumValueRightValue_emitsDesignTimeValue() throws Exception {
        String json = filterJson("Статус = Перечисление.СтатусыЗаказов.Новый");

        assertSingleDesignTimeRight(json, "Перечисление.СтатусыЗаказов.Новый");

        assertThat(compile(json))
                .as("Перечисление.* в правой части — DesignTimeValue (a9deeee2)")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "Перечисление.СтатусыЗаказов.Новый</dcsset:right>");
    }

    /**
     * Регрессионный негатив (S-01, MUST по Test Plan): обычная строка остаётся
     * {@code xs:string} — ссылочный паттерн НЕ перехватывает простые литералы.
     * Проходит и до, и после фикса (страховка от over-match, риск 1 §7.2).
     */
    @Test
    @DisplayName("unit-S01-neg: обычная строка остаётся xs:string")
    void s01_plainStringRightValue_staysString() throws Exception {
        String content = compile(filterJson("Имя = Привет"));

        assertThat(content)
                .as("простой строковый литерал не должен стать DesignTimeValue")
                .contains("<dcsset:right xsi:type=\"xs:string\">Привет</dcsset:right>");
        assertThat(content)
                .doesNotContain("xsi:type=\"dcscor:DesignTimeValue\">Привет");
    }

    /** Регрессионный негатив (S-01): число остаётся xs:decimal. */
    @Test
    @DisplayName("unit-S01-neg: число остаётся xs:decimal")
    void s01_numberRightValue_staysDecimal() throws Exception {
        String content = compile(filterJson("Количество > 0"));

        assertThat(content)
                .contains("<dcsset:right xsi:type=\"xs:decimal\">0</dcsset:right>");
        assertThat(content).doesNotContain("xsi:type=\"dcscor:DesignTimeValue\">0");
    }

    // ════════════════════════════════════════════════════════════════════
    // S-02 — авто-inject SelectedItemAuto / OrderItemAuto в группах структуры
    // upstream 6781bb3e. Канон: группы StructureItemGroup всегда содержат
    // SelectedItemAuto + OrderItemAuto, даже без явных selection/order.
    // ════════════════════════════════════════════════════════════════════

    private static final String STRUCTURE_GROUP_NO_SELECTION = """
            {
              "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
              "settingsVariants": [{
                "name": "Основной",
                "settings": {
                  "structure": [{ "name": "ГруппаОрг", "groupFields": ["Организация"] }]
                }
              }]
            }
            """;

    /**
     * Red (S-02): группа структуры БЕЗ явных selection/order. Сегодня
     * {@code writeStructure} эмитит {@code dcsset:selection} только при non-empty
     * (:833) и {@code dcsset:order} только при non-empty (:843) → Auto-элементы
     * отсутствуют. После фикса — каждая StructureItemGroup содержит
     * {@code SelectedItemAuto} И {@code OrderItemAuto}.
     */
    @Test
    @DisplayName("unit-S02: группа без явных selection/order получает SelectedItemAuto и OrderItemAuto")
    void s02_groupWithoutExplicitSelectionOrder_injectsAuto() throws Exception {
        String content = compile(STRUCTURE_GROUP_NO_SELECTION);

        assertThat(content)
                .as("StructureItemGroup без явной выборки должна получить SelectedItemAuto (6781bb3e)")
                .contains("xsi:type=\"dcsset:SelectedItemAuto\"");
        assertThat(content)
                .as("StructureItemGroup без явного порядка должна получить OrderItemAuto (6781bb3e)")
                .contains("xsi:type=\"dcsset:OrderItemAuto\"");
    }

    /**
     * Регрессионный негатив (S-02): при ЯВНО заданной выборке поля Auto НЕ
     * дублируется. {@code "selection": ["Организация"]} → SelectedItemField, без
     * добавочного SelectedItemAuto. Проходит и до, и после фикса.
     */
    @Test
    @DisplayName("unit-S02-neg: явная selection не дублируется Auto-элементом")
    void s02_explicitSelection_notDuplicatedByAuto() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{
                        "name": "ГруппаОрг",
                        "groupFields": ["Организация"],
                        "selection": ["Организация"]
                      }]
                    }
                  }]
                }
                """;
        String content = compile(json);

        assertThat(content)
                .as("явная выборка поля эмитится как SelectedItemField")
                .contains("xsi:type=\"dcsset:SelectedItemField\"");
        assertThat(content)
                .as("явная выборка НЕ должна заменяться/дублироваться SelectedItemAuto")
                .doesNotContain("xsi:type=\"dcsset:SelectedItemAuto\"");
    }

    /**
     * Регрессионный негатив (S-02, F-05): симметрия к explicit selection — при ЯВНО
     * заданном порядке {@code "order"} группа эмитит {@code OrderItemField}, а
     * {@code OrderItemAuto} НЕ добавляется. Гарантирует, что auto-inject S-02 трогает
     * только ПУСТУЮ ветку order (:843), не перетирая явный порядок. Проходит и до,
     * и после фикса (страховка от over-inject в order-ветке, параллель selection).
     */
    @Test
    @DisplayName("unit-S02-neg: явный order не дублируется OrderItemAuto")
    void s02_explicitOrder_notDuplicatedByAuto() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{
                        "name": "ГруппаОрг",
                        "groupFields": ["Организация"],
                        "order": ["Организация asc"]
                      }]
                    }
                  }]
                }
                """;
        String content = compile(json);

        assertThat(content)
                .as("явный порядок эмитится как OrderItemField")
                .contains("xsi:type=\"dcsset:OrderItemField\"");
        assertThat(content)
                .as("явный порядок НЕ должен заменяться/дублироваться OrderItemAuto")
                .doesNotContain("xsi:type=\"dcsset:OrderItemAuto\"");
    }

    // ════════════════════════════════════════════════════════════════════
    // S-03 — ДОКАЗАТЕЛЬНАЯ ДИСПОЗИЦИЯ «не подтверждён» (НЕ Red-тест)
    // technical-design C-4 / spec rev.4 R-176-V.4-параллель: short form
    // <dcsset:item> без xsi:type нужна ТОЛЬКО внутри table-axis блоков
    // (dcsset:row/column/points/series), которых Java НЕ эмитит вовсе.
    // Для structure-tree безусловный xsi:type:810 КАНОН-КОРРЕКТЕН.
    // → S-03 = «не подтверждён» + new-feature бэклог «table-axis блоки».
    // Тест фиксирует уже корректное поведение (как W-13 в TASK-175).
    // ════════════════════════════════════════════════════════════════════

    /**
     * Диспозиция-доказательство (S-03, проходит СЕЙЧАС): вложенная структура-tree
     * эмитит explicit {@code xsi:type="dcsset:StructureItemGroup"} на каждом узле
     * (канон Designer, корпус биг_ДетализацияОпераций) — это НЕ дефект, фикс не
     * требуется. Дополнительно подтверждается отсутствие table-axis поверхности.
     */
    @Test
    @DisplayName("unit-S03-disp: structure-tree эмитит explicit xsi:type (канон-корректно)")
    void s03_structureTree_emitsExplicitGroupType() throws Exception {
        String json = """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{
                        "name": "Внешняя",
                        "groupFields": ["Организация"],
                        "children": [{ "name": "Внутренняя", "groupFields": ["Номенклатура"] }]
                      }]
                    }
                  }]
                }
                """;
        String content = compile(json);

        assertThat(content)
                .as("узлы structure-tree канонично несут explicit xsi:type "
                        + "(C-4: фикс short-form здесь сломал бы канон Designer)")
                .contains("xsi:type=\"dcsset:StructureItemGroup\"");
    }

    /**
     * Диспозиция-доказательство (S-03): писатель НЕ эмитит ни одного table-axis
     * блока ({@code dcsset:row/column/points/series}) — поверхность отсутствует
     * целиком (0 вхождений в writer/), поэтому регрессия «лишний xsi:type в
     * table axis» опровергнута: чинить нечего. Реальный пробель (поддержка
     * table-axis + short-form дети) — new-feature → бэклог (53536b72), НЕ фикс.
     */
    @Test
    @DisplayName("unit-S03-disp: writer не эмитит table-axis блоки (поверхность отсутствует)")
    void s03_noTableAxisSurface() throws Exception {
        String content = compile("""
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{ "type": "table", "name": "Т", "groupFields": ["Организация"] }]
                    }
                  }]
                }
                """);

        assertThat(content)
                .as("table-axis блоки в Java не эмитятся — short-form-регрессия опровергнута, "
                        + "table-axis поддержка = new-feature бэклог (53536b72)")
                .doesNotContain("<dcsset:row")
                .doesNotContain("<dcsset:column")
                .doesNotContain("<dcsset:points")
                .doesNotContain("<dcsset:series");
    }

    // ════════════════════════════════════════════════════════════════════
    // S-08 — верификационная диспозиция «перенесён» (R-176-4.2, R-176-M.2)
    // Тест фиксирует уже корректное поведение (как W-13 в TASK-175) и
    // одновременно страхует регион detectValueType от over-match фикса S-01:
    // примитивы (449f814d/cbad0fe7) должны сохранять xs:dateTime/xs:boolean.
    // ════════════════════════════════════════════════════════════════════

    /**
     * Верификация S-08 (проходит СЕЙЧАС): {@code detectValueType} корректно
     * распознаёт примитивы — дата → {@code xs:dateTime}, булево → {@code xs:boolean}.
     * Перенесено корректно; фикс S-01 (ссылочный паттерн) НЕ должен это сломать.
     */
    @Test
    @DisplayName("unit-S08-verify: detectValueType примитивов перенесён (dateTime/boolean)")
    void s08_detectValueTypePrimitives_transferred() throws Exception {
        String dateContent = compile(filterJson("Дата >= 2024-01-01T00:00:00"));
        assertThat(dateContent)
                .as("значение-дата в правой части → xs:dateTime (449f814d/cbad0fe7)")
                .contains("<dcsset:right xsi:type=\"xs:dateTime\">2024-01-01T00:00:00</dcsset:right>");

        String boolContent = compile(filterJson("Активен = true"));
        assertThat(boolContent)
                .as("булево значение в правой части → xs:boolean")
                .contains("<dcsset:right xsi:type=\"xs:boolean\">true</dcsset:right>");
    }

    /**
     * S-08 / F-01 (rework #1): мультиязычное значение оформления (be9ebedf).
     * Значение-словарь {@code {ru:.., en:..}} в {@code appearance} ДЛЯ ЛЮБОГО ключа
     * (Формат/Текст/...) MUST эмитироваться как {@code v8:LocalStringType} с отдельным
     * {@code v8:item} на каждый язык. Старое поведение: уцелевший Map проваливался в
     * {@code value.toString()} → "{ru=.., en=..}" как {@code xs:string} (Формат) или одним
     * ru-айтемом с тем же мусором (Текст) — порт-регрессия be9ebedf, в Java не
     * воспроизведённая до этого фикса.
     */
    @Test
    @DisplayName("unit-S08-F01: multilang appearance value → LocalStringType (be9ebedf)")
    void s08_multilangAppearanceValue_emitsLocalStringType() throws Exception {
        String content = compile("""
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "conditionalAppearance": [{
                        "selection": ["Сумма"],
                        "appearance": {
                          "Формат": { "ru": "ДЛФ=D", "en": "DLF=D" },
                          "Текст":  { "ru": "Привет", "en": "Hello" }
                        },
                        "presentation": "Тест"
                      }]
                    }
                  }]
                }
                """);

        assertThat(content)
                .as("значение-словарь оформления НЕ должно эмититься как toString() Map")
                .doesNotContain("{ru=");
        assertThat(content)
                .as("ключ Формат со словарём → LocalStringType с обоими языками (не xs:string)")
                .contains("<dcscor:parameter>Формат</dcscor:parameter>")
                .contains("<v8:content>ДЛФ=D</v8:content>")
                .contains("<v8:content>DLF=D</v8:content>");
        assertThat(content)
                .as("ключ Текст со словарём → оба языка как отдельные v8:item")
                .contains("<v8:content>Привет</v8:content>")
                .contains("<v8:content>Hello</v8:content>")
                .contains("<v8:lang>en</v8:lang>");
    }
}

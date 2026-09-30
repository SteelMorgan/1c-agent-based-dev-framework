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
 * TASK-176 Phase 4 (Tester) — расширение покрытия writer-домена SKD-дельты:
 * edge-кейсы и регрессионные стражи, НЕ дублирующие Phase 3b/3d.
 *
 * <p>Покрывает:
 * <ul>
 *   <li><b>be9ebedf / XG-49</b> (multilang appearance) — прямой пиннинг
 *       <b>field-appearance</b> call-site {@code SkdWriter.java:341} (residual
 *       cross-review: 3d-тест {@code s08_multilangAppearanceValue} пинит ТОЛЬКО
 *       conditionalAppearance call-site :1196; field-appearance использует тот же
 *       хелпер {@code writeSettingsParameterValue}, но отдельная точка вызова);
 *       плюс edge — 3+ языка, одиночный язык-словарь, пустой словарь.</li>
 *   <li><b>R-M.2</b> регрессия XG-49: СТРОКОВОЕ значение оформления НЕ должно
 *       попадать в multilang-ветку ({@code value instanceof Map}) — сохраняет
 *       путь {@code detectAppearanceValueType} (Формат→xs:string, Текст→mono-ru
 *       LocalStringType).</li>
 *   <li><b>S-01 edge</b> (DesignTimeValue): смешанный фильтр (ссылка + примитив
 *       в одном варианте — независимая типизация, over-match guard на уровне
 *       нескольких filter-item); расширенные префиксы и формы {@code .Ссылка}/
 *       {@code .EmptyRef} (Документ / РегистрСведений / EN Catalog).</li>
 *   <li><b>S-02 edge</b> (auto-inject): частичная структура — явная selection БЕЗ
 *       order (инъекция только OrderItemAuto) и явный order БЕЗ selection (инъекция
 *       только SelectedItemAuto). 3b-негативы проверяли только «не дублируется»,
 *       не комплементарную ветку.</li>
 * </ul>
 *
 * <p>Стратегия — компиляция SKD из DSL (паттерн {@link SkdWriterTask176Test}).
 * src/main НЕ трогается; фиксы Phase 3d проверяются как чёрный ящик.</p>
 */
class SkdWriterTask176Phase4Test {

    @TempDir
    Path tempDir;

    private String compile(String json) throws Exception {
        return Files.readString(compileToFile(json));
    }

    private Path compileToFile(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SkdDsl dsl = mapper.readValue(json, SkdDsl.class);
        Path outputXml = tempDir.resolve("Template_" + System.nanoTime() + ".xml");
        new SkdWriter(OutputFormat.DESIGNER).create(dsl, outputXml);
        return outputXml;
    }

    private XmlDocument compileDoc(String json) throws Exception {
        return new XmlStructureReader().parse(compileToFile(json));
    }

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

    /** SKD с одним dataSet-полем «Сумма», у которого задан appearance-фрагмент (JSON). */
    private static String fieldAppearanceJson(String appearanceJson) {
        return """
                {
                  "dataSets": [{
                    "type": "query", "name": "Н", "query": "ВЫБРАТЬ Сумма ИЗ Т",
                    "fields": [{ "field": "Сумма", "appearance": %s }]
                  }]
                }
                """.formatted(appearanceJson);
    }

    private static String filterJson(String... filters) {
        StringBuilder arr = new StringBuilder();
        for (int i = 0; i < filters.length; i++) {
            if (i > 0) {
                arr.append(", ");
            }
            arr.append('"').append(filters[i]).append('"');
        }
        return """
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": { "filter": [%s] }
                  }]
                }
                """.formatted(arr.toString());
    }

    // ════════════════════════════════════════════════════════════════════
    // be9ebedf / XG-49 — multilang appearance на FIELD-appearance call-site (:341)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Residual cross-review: значение-словарь {@code {ru,en}} в <b>field-appearance</b>
     * (call-site :341, НЕ conditionalAppearance :1196) MUST эмитироваться как
     * {@code v8:LocalStringType} с отдельным {@code v8:item} на язык. Прямой пиннинг
     * второй точки вызова общего хелпера {@code writeSettingsParameterValue}.
     */
    @Test
    @DisplayName("unit-P4-XG49: field-appearance со словарём {ru,en} → LocalStringType (call-site :341)")
    void fieldAppearanceMultilangValue_emitsLocalStringType() throws Exception {
        String content = compile(fieldAppearanceJson(
                "{ \"Формат\": { \"ru\": \"ДЛФ=D\", \"en\": \"DLF=D\" } }"));

        assertThat(content)
                .as("field-appearance: значение-словарь НЕ должно эмититься как toString() Map")
                .doesNotContain("{ru=");
        assertThat(content)
                .as("ключ Формат со словарём → оба языка как отдельные v8:item")
                .contains("<dcscor:parameter>Формат</dcscor:parameter>")
                .contains("<dcscor:value xsi:type=\"v8:LocalStringType\">")
                .contains("<v8:content>ДЛФ=D</v8:content>")
                .contains("<v8:content>DLF=D</v8:content>")
                .contains("<v8:lang>en</v8:lang>");
    }

    /** be9ebedf edge: словарь оформления с 3+ языками — каждый язык отдельным v8:item. */
    @Test
    @DisplayName("unit-P4-XG49: field-appearance со словарём из 3 языков эмитит все три")
    void fieldAppearanceThreeLanguages_allEmitted() throws Exception {
        String content = compile(fieldAppearanceJson(
                "{ \"Формат\": { \"ru\": \"ДЛФ=D\", \"en\": \"DLF=D\", \"uk\": \"ДЛФ=У\" } }"));

        assertThat(content).doesNotContain("{ru=");
        assertThat(content)
                .as("словарь 3 языков → три v8:item (ru/en/uk), все content сохранены")
                .contains("<v8:lang>ru</v8:lang>").contains("<v8:content>ДЛФ=D</v8:content>")
                .contains("<v8:lang>en</v8:lang>").contains("<v8:content>DLF=D</v8:content>")
                .contains("<v8:lang>uk</v8:lang>").contains("<v8:content>ДЛФ=У</v8:content>");

        // структурно: ровно 3 v8:item внутри значения оформления
        XmlDocument doc = compileDoc(fieldAppearanceJson(
                "{ \"Формат\": { \"ru\": \"ДЛФ=D\", \"en\": \"DLF=D\", \"uk\": \"ДЛФ=У\" } }"));
        List<XmlNode> values = collectByLocalName(doc.getRoot(), "value");
        assertThat(values).as("ровно один узел dcscor:value у appearance-параметра").hasSize(1);
        List<XmlNode> items = collectByLocalName(values.get(0), "item");
        assertThat(items).as("ровно три v8:item (по языку)").hasSize(3);

        // F-03 (структурное усиление): каждый язык несёт СВОЮ пару lang↔content внутри
        // собственного v8:item. Substring-проверки выше доказывают лишь присутствие
        // фрагментов где-то в выводе; эта проверка доказывает, что значения РАЗНЕСЕНЫ
        // по языкам (нет склейки в один toString и нет перемешивания content между языками).
        java.util.Map<String, String> contentByLang = new java.util.LinkedHashMap<>();
        for (XmlNode item : items) {
            contentByLang.put(item.childText("lang"), item.childText("content"));
        }
        assertThat(contentByLang)
                .as("каждый язык имеет ровно свой content (ru→ДЛФ=D, en→DLF=D, uk→ДЛФ=У)")
                .containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                        "ru", "ДЛФ=D", "en", "DLF=D", "uk", "ДЛФ=У"));
    }

    /**
     * be9ebedf edge / граница: одиночный язык, но в форме СЛОВАРЯ {@code {ru:..}} —
     * это Map → multilang-ветка → {@code LocalStringType} (НЕ {@code xs:string}).
     * Отличает dict-single от строкового значения «Формат» (которое идёт в xs:string).
     */
    @Test
    @DisplayName("unit-P4-XG49: одиночный язык-словарь {ru} → LocalStringType, не xs:string")
    void fieldAppearanceSingleLangDict_isLocalStringTypeNotString() throws Exception {
        String content = compile(fieldAppearanceJson(
                "{ \"Формат\": { \"ru\": \"ДЛФ=D\" } }"));

        assertThat(content).doesNotContain("{ru=");
        assertThat(content)
                .as("словарь-одиночка (Map) → LocalStringType (multilang-ветка), не xs:string")
                .contains("<dcscor:value xsi:type=\"v8:LocalStringType\">")
                .contains("<v8:lang>ru</v8:lang>")
                .contains("<v8:content>ДЛФ=D</v8:content>");
        assertThat(content)
                .as("значение-словарь Формат НЕ должно стать xs:string")
                .doesNotContain("<dcscor:value xsi:type=\"xs:string\">");
    }

    /**
     * be9ebedf edge: пустой словарь {@code {}} — деградирует мягко (LocalStringType
     * без v8:item), без мусора {@code toString()} и без падения компиляции.
     */
    @Test
    @DisplayName("unit-P4-XG49: пустой словарь оформления компилируется без мусора и падения")
    void fieldAppearanceEmptyDict_gracefulNoGarbage() throws Exception {
        String content = compile(fieldAppearanceJson("{ \"Текст\": {} }"));

        assertThat(content)
                .as("пустой словарь не должен эмитить ни {ru=, ни {} как текст")
                .doesNotContain("{ru=")
                .doesNotContain(">{}<")
                .doesNotContain("<v8:content>{}</v8:content>");
        assertThat(content)
                .as("параметр оформления всё равно эмитится (структура цела)")
                .contains("<dcscor:parameter>Текст</dcscor:parameter>");

        // F-03 (структурное усиление): пустой словарь НЕ должен деградировать в мусорное
        // строковое значение (<dcscor:value xsi:type="xs:string">) и НЕ должен породить
        // ни одного фантомного v8:item. Проверяем именно префикс dcscor:value — xs:string
        // на dcsset:presentation варианта легитимен и не относится к значению оформления.
        assertThat(content)
                .as("пустой словарь оформления не деградирует в строковое значение xs:string")
                .doesNotContain("<dcscor:value xsi:type=\"xs:string\">");

        XmlDocument doc = compileDoc(fieldAppearanceJson("{ \"Текст\": {} }"));
        List<XmlNode> values = collectByLocalName(doc.getRoot(), "value");
        assertThat(values)
                .as("ровно один узел dcscor:value у appearance-параметра").hasSize(1);
        assertThat(collectByLocalName(values.get(0), "item"))
                .as("пустой словарь → НОЛЬ v8:item (нет фантомных языков)")
                .isEmpty();
    }

    // ════════════════════════════════════════════════════════════════════
    // R-M.2 — XG-49 не должен перехватывать СТРОКОВЫЕ значения оформления
    // (страховка: multilangValue = value instanceof Map; строка → прежний путь)
    // ════════════════════════════════════════════════════════════════════

    /** R-M.2: строковый «Формат» сохраняет xs:string (detectAppearanceValueType), не LocalStringType. */
    @Test
    @DisplayName("unit-P4-RM2: строковый Формат остаётся xs:string (XG-49 не трогает строки)")
    void rmm2_stringAppearanceFormat_staysXsString() throws Exception {
        String content = compile(fieldAppearanceJson("{ \"Формат\": \"ЧДЦ=2\" }"));

        assertThat(content)
                .as("строковый Формат → xs:string (XG-27 / detectAppearanceValueType, не задет XG-49)")
                .contains("<dcscor:value xsi:type=\"xs:string\">ЧДЦ=2</dcscor:value>");
        assertThat(content)
                .as("строковое значение НЕ должно уходить в multilang-ветку LocalStringType")
                .doesNotContain("<dcscor:value xsi:type=\"v8:LocalStringType\">");
    }

    /** R-M.2: строковый «Текст» сохраняет mono-ru LocalStringType (через detectAppearanceValueType). */
    @Test
    @DisplayName("unit-P4-RM2: строковый Текст остаётся mono-ru LocalStringType")
    void rmm2_stringAppearanceText_staysMonoLangLocalString() throws Exception {
        String json = fieldAppearanceJson("{ \"Текст\": \"Не указано\" }");
        String content = compile(json);

        assertThat(content)
                .as("строковый Текст → LocalStringType с единственным ru-айтемом (detectAppearanceValueType)")
                .contains("<dcscor:value xsi:type=\"v8:LocalStringType\">")
                .contains("<v8:lang>ru</v8:lang>")
                .contains("<v8:content>Не указано</v8:content>");
        assertThat(content).doesNotContain("{ru=");

        XmlDocument doc = compileDoc(json);
        List<XmlNode> values = collectByLocalName(doc.getRoot(), "value");
        assertThat(collectByLocalName(values.get(0), "item"))
                .as("строковый Текст → РОВНО один v8:item (mono-ru), не размножается")
                .hasSize(1);
    }

    // ════════════════════════════════════════════════════════════════════
    // S-01 edge — DesignTimeValue
    // ════════════════════════════════════════════════════════════════════

    /**
     * S-01 edge: смешанный фильтр в одном варианте — ссылочное правое значение и
     * примитив. Каждый filter-item типизируется НЕЗАВИСИМО: ссылка →
     * {@code dcscor:DesignTimeValue}, число → {@code xs:decimal}. Over-match guard
     * на уровне нескольких filter-item (3b-негативы проверяли по одному item).
     */
    @Test
    @DisplayName("unit-P4-S01: смешанный фильтр (ссылка + число) типизируется независимо")
    void s01_mixedFilter_refAndPrimitive_typedIndependently() throws Exception {
        String content = compile(filterJson(
                "Контрагент = Справочник.Контрагенты.ПустаяСсылка",
                "Количество > 5"));

        assertThat(content)
                .as("ссылочный item → DesignTimeValue")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "Справочник.Контрагенты.ПустаяСсылка</dcsset:right>");
        assertThat(content)
                .as("примитивный item в том же варианте остаётся xs:decimal (не over-match)")
                .contains("<dcsset:right xsi:type=\"xs:decimal\">5</dcsset:right>");

        XmlDocument doc = compileDoc(filterJson(
                "Контрагент = Справочник.Контрагенты.ПустаяСсылка", "Количество > 5"));
        assertThat(collectByLocalName(doc.getRoot(), "right"))
                .as("ровно два узла dcsset:right (по одному на filter-item)")
                .hasSize(2);
    }

    /**
     * S-01 edge: расширенные префиксы метаданных и формы {@code .Ссылка}/{@code .EmptyRef}.
     * Документ / РегистрСведений (RU) и Catalog (EN) — все распознаются как
     * {@code DesignTimeValue} (паттерн {@code prefix\\..+} не зависит от хвостового
     * члена: {@code .Ссылка}, {@code .EmptyRef}, {@code .ПустаяСсылка} классифицируются
     * одинаково). 3b-позитивы покрывали Справочник/ПланСчетов/Перечисление.
     */
    @Test
    @DisplayName("unit-P4-S01: Документ/РегистрСведений/EN-Catalog и формы .Ссылка/.EmptyRef → DesignTimeValue")
    void s01_widerPrefixesAndRefForms_allDesignTimeValue() throws Exception {
        String docRef = compile(filterJson("Рег = Документ.РеализацияТоваров.ПустаяСсылка"));
        assertThat(docRef).as("Документ.* → DesignTimeValue")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "Документ.РеализацияТоваров.ПустаяСсылка</dcsset:right>");

        String irRef = compile(filterJson("Период = РегистрСведений.КурсыВалют.Ссылка"));
        assertThat(irRef).as("РегистрСведений.* в форме .Ссылка → DesignTimeValue")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "РегистрСведений.КурсыВалют.Ссылка</dcsset:right>");

        String enRef = compile(filterJson("Acc = Catalog.Items.EmptyRef"));
        assertThat(enRef).as("EN Catalog.* в форме .EmptyRef → DesignTimeValue")
                .contains("<dcsset:right xsi:type=\"dcscor:DesignTimeValue\">"
                        + "Catalog.Items.EmptyRef</dcsset:right>");
    }

    // ════════════════════════════════════════════════════════════════════
    // S-02 edge — частичная структура (одна ветка явная, другая пустая)
    // ════════════════════════════════════════════════════════════════════

    /**
     * S-02 edge: группа с ЯВНОЙ selection, но БЕЗ order. Явная selection →
     * {@code SelectedItemField} (не дублируется Auto), а ПУСТАЯ order ветка →
     * инъекция {@code OrderItemAuto}. 3b-негатив {@code s02_explicitSelection_*}
     * проверял лишь «SelectedItemAuto не добавлен», не комплементарную order-ветку.
     */
    @Test
    @DisplayName("unit-P4-S02: явная selection + пустой order → SelectedItemField И OrderItemAuto")
    void s02_explicitSelectionEmptyOrder_injectsOrderAutoOnly() throws Exception {
        String content = compile("""
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{
                        "name": "Г", "groupFields": ["Организация"],
                        "selection": ["Организация"]
                      }]
                    }
                  }]
                }
                """);

        assertThat(content)
                .as("явная selection → SelectedItemField, без дубль-SelectedItemAuto")
                .contains("xsi:type=\"dcsset:SelectedItemField\"")
                .doesNotContain("xsi:type=\"dcsset:SelectedItemAuto\"");
        assertThat(content)
                .as("пустая order-ветка группы → инъекция OrderItemAuto (S-02, 6781bb3e)")
                .contains("xsi:type=\"dcsset:OrderItemAuto\"");
    }

    /**
     * S-02 edge: симметрия — группа с ЯВНЫМ order, но БЕЗ selection. Явный order →
     * {@code OrderItemField}, а ПУСТАЯ selection ветка → инъекция
     * {@code SelectedItemAuto}.
     */
    @Test
    @DisplayName("unit-P4-S02: явный order + пустая selection → OrderItemField И SelectedItemAuto")
    void s02_explicitOrderEmptySelection_injectsSelectionAutoOnly() throws Exception {
        String content = compile("""
                {
                  "dataSets": [{ "type": "query", "name": "Н", "query": "ВЫБРАТЬ 1 КАК Х" }],
                  "settingsVariants": [{
                    "name": "Основной",
                    "settings": {
                      "structure": [{
                        "name": "Г", "groupFields": ["Организация"],
                        "order": ["Организация asc"]
                      }]
                    }
                  }]
                }
                """);

        assertThat(content)
                .as("явный order → OrderItemField, без дубль-OrderItemAuto")
                .contains("xsi:type=\"dcsset:OrderItemField\"")
                .doesNotContain("xsi:type=\"dcsset:OrderItemAuto\"");
        assertThat(content)
                .as("пустая selection-ветка группы → инъекция SelectedItemAuto (S-02, 6781bb3e)")
                .contains("xsi:type=\"dcsset:SelectedItemAuto\"");
    }
}

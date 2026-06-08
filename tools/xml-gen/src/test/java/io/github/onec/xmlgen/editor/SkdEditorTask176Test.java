package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.editor.skd.SkdShorthandParser;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-176 (контур B+C, домен SKD) — Red-тесты для {@code skd edit} (SkdEditor,
 * in-memory): S-04 (preserve multi-lang title в modify-field/modify-parameter) и
 * S-09 правдивость OpResult (F-01: removeField лжёт {@code unchanged} при реальной
 * мутации selection варианта).
 *
 * <p>Стратегия — in-memory {@link XmlDocument} с фикстурой DCS-схемы (паттерн
 * {@code SkdEditorTest}). Локации (technical-design rev.2, чтение кода 2026-06-08):
 * S-04 {@code modifyField}:285 / {@code modifyParameter}:461 (полная замена
 * {@code <title>} через {@code buildLocalStringType}:973 = mono-ru); S-09 правдивость
 * {@code removeField}:300-334 (возврат по флагу {@code removed}, но независимая
 * мутация selection через {@code removeFromSelectionRecursive}:327 → :1075).</p>
 */
class SkdEditorTask176Test {

    // ─── helpers для построения фикстур (мирроринг EditorUtils.createNode) ───

    private static XmlNode el(String qname, String text, XmlNode... children) {
        String prefix = qname.contains(":") ? qname.substring(0, qname.indexOf(':')) : "";
        String local = qname.contains(":") ? qname.substring(qname.indexOf(':') + 1) : qname;
        XmlNode.Builder b = XmlNode.builder().name(local).prefix(prefix);
        if (text != null) {
            b.appendText(text);
        }
        for (XmlNode c : children) {
            b.addChild(c);
        }
        return b.build();
    }

    private static XmlDocument wrap(XmlNode root) {
        return new XmlDocument(null, false, null, "DataCompositionSchema", "",
                Map.of(), root.getChildren(), root);
    }

    private static XmlNode findField(XmlDocument doc, String dataPath) {
        for (XmlNode ds : doc.getRoot().children("dataSet")) {
            for (XmlNode f : ds.children("field")) {
                if (dataPath.equals(f.childText("dataPath"))) {
                    return f;
                }
            }
        }
        return null;
    }

    private static XmlNode findParameter(XmlDocument doc, String name) {
        for (XmlNode p : doc.getRoot().children("parameter")) {
            if (name.equals(p.childText("name"))) {
                return p;
            }
        }
        return null;
    }

    /** Текст {@code <v8:content>} внутри {@code <v8:item>} с заданным {@code <v8:lang>}. */
    private static String contentForLang(XmlNode title, String lang) {
        if (title == null) {
            return null;
        }
        for (XmlNode item : title.children("item")) {
            if (lang.equals(item.childText("lang"))) {
                return item.childText("content");
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════════════
    // S-04 — preserve multi-lang title (upstream 79db5de6)
    // ════════════════════════════════════════════════════════════════════

    /** Схема с одним полем «Цена», у которого мультиязычный title (ru + en). */
    private static XmlDocument schemaWithMultiLangFieldTitle() {
        XmlNode title = el("title", null,
                el("v8:item", null,
                        el("v8:lang", "ru"),
                        el("v8:content", "Цена")),
                el("v8:item", null,
                        el("v8:lang", "en"),
                        el("v8:content", "Price")));
        title.setAttribute("xsi:type", "v8:LocalStringType");

        XmlNode field = el("field", null,
                el("dataPath", "Цена"),
                title);

        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"),
                el("query", "ВЫБРАТЬ Цена ИЗ Таблица"),
                field);

        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"),
                el("dcsset:settings", null));

        XmlNode root = el("DataCompositionSchema", null, dataSet, variant);
        return wrap(root);
    }

    /**
     * Red (S-04): {@code modify-field --title "Новый"} на поле с мультиязычным
     * заголовком. Сегодня {@code buildLocalStringType} (:973) строит mono-ru блок
     * и полностью заменяет {@code <title>} → en-заголовок теряется. После фикса
     * (79db5de6) — патчится только ru-{@code <v8:content>}, en-{@code <v8:item>}
     * сохраняется.
     */
    @Test
    @DisplayName("unit-S04: modify-field сохраняет en-заголовок multi-lang title")
    void s04_modifyFieldTitle_preservesEnglishLang() {
        XmlDocument doc = schemaWithMultiLangFieldTitle();
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.modifyField(
                SkdShorthandParser.parseField("Цена [Новый]: decimal(15,2)"), null);
        assertThat(r.changed).as("modify-field с новым title должен дать changed").isTrue();

        XmlNode title = findField(doc, "Цена").child("title");
        assertThat(contentForLang(title, "ru"))
                .as("ru-content должен обновиться на новое значение")
                .isEqualTo("Новый");
        assertThat(contentForLang(title, "en"))
                .as("en-заголовок ОБЯЗАН сохраниться (79db5de6: preserve multi-lang title), "
                        + "сегодня buildLocalStringType:973 затирает его mono-ru блоком")
                .isEqualTo("Price");
    }

    /**
     * Регрессионный негатив (S-04): моноязычный (только ru) title — поведение
     * прежнее: ru-content заменяется, лишних языков не появляется. Проходит и до,
     * и после фикса (ADD/mono-ru путь корректен, C-5).
     */
    @Test
    @DisplayName("unit-S04-neg: моноязычный title — поведение прежнее")
    void s04_monoLangTitle_behaviourUnchanged() {
        XmlNode title = el("title", null,
                el("v8:item", null,
                        el("v8:lang", "ru"),
                        el("v8:content", "Цена")));
        title.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode field = el("field", null, el("dataPath", "Цена"), title);
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"), el("query", "ВЫБРАТЬ Цена ИЗ Т"), field);
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), el("dcsset:settings", null));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, variant));
        SkdEditor editor = new SkdEditor(doc);

        editor.modifyField(SkdShorthandParser.parseField("Цена [Новый]: decimal(15,2)"), null);

        XmlNode result = findField(doc, "Цена").child("title");
        assertThat(contentForLang(result, "ru")).isEqualTo("Новый");
        assertThat(contentForLang(result, "en"))
                .as("в моноязычной фикстуре чужие языки не появляются")
                .isNull();
    }

    // ─── F-01: modify-parameter — тот же класс дефекта (затирание multi-lang) ───

    /** Схема с корневым параметром «Валюта», у которого мультиязычный title (ru + en). */
    private static XmlDocument schemaWithMultiLangParameterTitle() {
        XmlNode title = el("title", null,
                el("v8:item", null,
                        el("v8:lang", "ru"),
                        el("v8:content", "Валюта")),
                el("v8:item", null,
                        el("v8:lang", "en"),
                        el("v8:content", "Currency")));
        title.setAttribute("xsi:type", "v8:LocalStringType");

        XmlNode param = el("parameter", null,
                el("name", "Валюта"),
                title);

        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"),
                el("query", "ВЫБРАТЬ Цена ИЗ Таблица"));

        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"),
                el("dcsset:settings", null));

        XmlNode root = el("DataCompositionSchema", null, dataSet, param, variant);
        return wrap(root);
    }

    /**
     * Red (S-04 / F-01): {@code modify-parameter --title "Новый"} на параметре с
     * мультиязычным заголовком. Сегодня {@code modifyParameter}:461 заменяет
     * {@code <title>} целиком через {@code buildLocalStringType}:973 (mono-ru) →
     * en-заголовок теряется — ТОТ ЖЕ класс дефекта, что и в modify-field. После
     * фикса (79db5de6, применяется и к modifyParameter) — патчится только
     * ru-{@code <v8:content>}, en-{@code <v8:item>} сохраняется.
     */
    @Test
    @DisplayName("unit-S04: modify-parameter сохраняет en-заголовок multi-lang title")
    void s04_modifyParameterTitle_preservesEnglishLang() {
        XmlDocument doc = schemaWithMultiLangParameterTitle();
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.modifyParameter(
                SkdShorthandParser.parseModifyParameter("Валюта [Новый]"));
        assertThat(r.changed).as("modify-parameter с новым title должен дать changed").isTrue();

        XmlNode title = findParameter(doc, "Валюта").child("title");
        assertThat(contentForLang(title, "ru"))
                .as("ru-content должен обновиться на новое значение")
                .isEqualTo("Новый");
        assertThat(contentForLang(title, "en"))
                .as("en-заголовок параметра ОБЯЗАН сохраниться (79db5de6: тот же класс, "
                        + "что modify-field), сегодня buildLocalStringType:973 затирает его mono-ru")
                .isEqualTo("Currency");
    }

    /**
     * Регрессионный негатив (S-04 / F-01): моноязычный (только ru) параметр —
     * ru-content заменяется, лишних языков не появляется (ADD/mono-ru путь
     * :973 корректен и не должен ломаться фиксом). Проходит и до, и после фикса.
     */
    @Test
    @DisplayName("unit-S04-neg: моноязычный title параметра — поведение прежнее")
    void s04_monoLangParameterTitle_behaviourUnchanged() {
        XmlNode title = el("title", null,
                el("v8:item", null,
                        el("v8:lang", "ru"),
                        el("v8:content", "Валюта")));
        title.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode param = el("parameter", null, el("name", "Валюта"), title);
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"), el("query", "ВЫБРАТЬ Цена ИЗ Т"));
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), el("dcsset:settings", null));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, param, variant));
        SkdEditor editor = new SkdEditor(doc);

        editor.modifyParameter(SkdShorthandParser.parseModifyParameter("Валюта [Новый]"));

        XmlNode result = findParameter(doc, "Валюта").child("title");
        assertThat(contentForLang(result, "ru")).isEqualTo("Новый");
        assertThat(contentForLang(result, "en"))
                .as("в моноязычной фикстуре параметра чужие языки не появляются")
                .isNull();
    }

    // ════════════════════════════════════════════════════════════════════
    // S-09 (F-01) — правдивость OpResult: removeField лжёт unchanged при
    // реальной мутации selection. ПРЕДУСЛОВИЕ changed-гейта (risk 3a §7.2):
    // под гейтом такая «лгущая» правка молча потеряется (потеря данных).
    // ════════════════════════════════════════════════════════════════════

    /**
     * Схема, где поле «ЛишнееПоле» присутствует ТОЛЬКО в selection варианта,
     * но отсутствует в наборе данных. Это сценарий лжи removeField (C-8 / F-01).
     */
    private static XmlDocument schemaWithFieldOnlyInSelection() {
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"),
                el("query", "ВЫБРАТЬ Цена ИЗ Таблица"));

        XmlNode selItem = el("dcsset:item", null, el("dcsset:field", "ЛишнееПоле"));
        selItem.setAttribute("xsi:type", "dcsset:SelectedItemField");

        XmlNode selection = el("dcsset:selection", null, selItem);
        XmlNode settings = el("dcsset:settings", null, selection);
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), settings);

        return wrap(el("DataCompositionSchema", null, dataSet, variant));
    }

    private static boolean selectionContainsField(XmlDocument doc, String fieldName) {
        XmlNode variant = doc.getRoot().child("settingsVariant");
        XmlNode settings = variant.child("settings");
        XmlNode selection = settings.child("selection");
        if (selection == null) {
            return false;
        }
        for (XmlNode item : selection.getChildren()) {
            if (fieldName.equals(item.childText("field"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Red (S-09 / F-01): удаление поля, присутствующего ЛИШЬ в selection варианта.
     * {@code removeField} реально мутирует selection (помощник
     * {@code removeFromSelectionRecursive}), но возвращает {@code OpResult.unchanged}
     * — флаг решается только по наличию поля в наборе данных (:309). Это
     * «лгущий» unchanged: под changed-гейтом S-09 правка молча потеряется
     * (saveAndValidate пропущен → потеря данных, risk 3a). После фикса side-helper
     * возвращает признак (selectionTouched), агрегируется → {@code changed}.
     */
    @Test
    @DisplayName("unit-S09: removeField с полем только в selection возвращает changed (правдивость OpResult)")
    void s09_removeFieldOnlyInSelection_reportsChanged() {
        XmlDocument doc = schemaWithFieldOnlyInSelection();
        SkdEditor editor = new SkdEditor(doc);

        assertThat(selectionContainsField(doc, "ЛишнееПоле"))
                .as("предусловие: поле есть в selection до удаления").isTrue();

        SkdEditor.OpResult r = editor.removeField("ЛишнееПоле", null, "Основной");

        // Мутация реально произошла (доказательство, проходит уже сейчас):
        assertThat(selectionContainsField(doc, "ЛишнееПоле"))
                .as("removeField реально удалил item из selection (мутация XML)")
                .isFalse();
        // Но OpResult сегодня лжёт unchanged — Red:
        assertThat(r.changed)
                .as("OpResult ОБЯЗАН быть changed при реальной мутации selection "
                        + "(F-01): иначе changed-гейт S-09 молча потеряет правку")
                .isTrue();
    }

    /**
     * Регрессионный негатив (S-09): подлинный NO-OP — remove-field
     * несуществующего нигде поля → {@code unchanged}. Проходит и до, и после
     * фикса (правдивость в обе стороны).
     */
    @Test
    @DisplayName("unit-S09-neg: removeField отсутствующего везде поля остаётся unchanged")
    void s09_removeFieldAbsentEverywhere_staysUnchanged() {
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"), el("query", "ВЫБРАТЬ Цена ИЗ Т"));
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"),
                el("dcsset:settings", null, el("dcsset:selection", null)));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, variant));
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.removeField("НетТакого", null, "Основной");

        assertThat(r.changed)
                .as("подлинный NO-OP (поле нигде не найдено) должен оставаться unchanged")
                .isFalse();
    }
}

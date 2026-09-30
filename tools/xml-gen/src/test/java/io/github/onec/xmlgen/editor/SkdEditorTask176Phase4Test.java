package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.editor.skd.SkdShorthandParser;
import io.github.onec.xmlgen.validator.XmlDocument;
import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-176 Phase 4 (Tester) — расширение покрытия editor-домена SKD-дельты:
 * edge-кейсы S-04 (multilang title) и S-09 (правдивость OpResult), НЕ дублирующие
 * Phase 3b/3d.
 *
 * <p>Покрывает:
 * <ul>
 *   <li><b>S-04 edge</b> (79db5de6 / XG-45): title где ровно ОДИН язык в multi-
 *       структуре, причём НЕ ru (только en) — фикс должен ДОБАВИТЬ ru, сохранив
 *       чужой язык (3b проверял ru+en и mono-ru); и title из 3 языков —
 *       сохранение всех «прочих» языков-детей при патче ru.</li>
 *   <li><b>S-09 edge</b> (511bfe7f / XG-46): двойная мутация — поле присутствует
 *       И в наборе данных, И в selection (агрегация {@code removed||selectionTouched});
 *       removeParameter существующего параметра (правдивость {@code changed}).</li>
 * </ul>
 *
 * <p>Стратегия — in-memory {@link XmlDocument}-фикстуры (паттерн
 * {@link SkdEditorTask176Test}). src/main НЕ трогается.</p>
 */
class SkdEditorTask176Phase4Test {

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

    private static XmlNode langItem(String lang, String content) {
        return el("v8:item", null,
                el("v8:lang", lang),
                el("v8:content", content));
    }

    // ════════════════════════════════════════════════════════════════════
    // S-04 edge — multilang title (preserve «прочие» языки при патче ru)
    // ════════════════════════════════════════════════════════════════════

    /**
     * S-04 edge: title-структура содержит РОВНО ОДИН язык, и это НЕ ru (только en).
     * При {@code modify-field --title} ru-элемента нет → фикс ДОБАВЛЯЕТ ru, не трогая
     * чужой en. 3b покрывал ru+en и mono-ru; ветка «multi-структура без ru» — здесь.
     */
    @Test
    @DisplayName("unit-P4-S04: modify-field на title только-en добавляет ru и сохраняет en")
    void s04_modifyFieldTitleOnlyEnglishLang_addsRuPreservesEn() {
        XmlNode title = el("title", null, langItem("en", "Price"));
        title.setAttribute("xsi:type", "v8:LocalStringType");
        XmlNode field = el("field", null, el("dataPath", "Цена"), title);
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"), el("query", "ВЫБРАТЬ Цена ИЗ Т"), field);
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), el("dcsset:settings", null));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, variant));
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.modifyField(
                SkdShorthandParser.parseField("Цена [Новый]: decimal(15,2)"), null);
        assertThat(r.changed).as("modify-field с новым title → changed").isTrue();

        XmlNode result = findField(doc, "Цена").child("title");
        assertThat(contentForLang(result, "ru"))
                .as("ru добавлен с новым значением (ru-элемента не было)").isEqualTo("Новый");
        assertThat(contentForLang(result, "en"))
                .as("чужой en сохранён, не затёрт mono-ru заменой").isEqualTo("Price");
    }

    /**
     * S-04 edge: title из ТРЁХ языков (ru+en+uk). Патч ru НЕ должен трогать прочие
     * языки-дети — en и uk сохраняются дословно (preserve «прочих» детей).
     */
    @Test
    @DisplayName("unit-P4-S04: modify-field на 3-язычном title правит только ru, сохраняя en+uk")
    void s04_modifyFieldThreeLangTitle_preservesNonRuLangs() {
        XmlNode title = el("title", null,
                langItem("ru", "Цена"), langItem("en", "Price"), langItem("uk", "Ціна"));
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
        assertThat(contentForLang(result, "ru")).as("ru обновлён").isEqualTo("Новый");
        assertThat(contentForLang(result, "en")).as("en сохранён").isEqualTo("Price");
        assertThat(contentForLang(result, "uk")).as("uk (третий язык) сохранён").isEqualTo("Ціна");
    }

    // ════════════════════════════════════════════════════════════════════
    // S-09 edge — правдивость OpResult при ДВОЙНОЙ мутации и removeParameter
    // ════════════════════════════════════════════════════════════════════

    /**
     * S-09 edge: поле присутствует И в наборе данных (dataSet.field), И в selection
     * варианта. {@code removeField} мутирует ОБА места — агрегированный
     * {@code changed} = {@code removed (dataSet) || selectionTouched}. 3b покрывал
     * только «лишь в selection» (removed=false, selectionTouched=true); здесь обе
     * стороны истинны одновременно.
     */
    @Test
    @DisplayName("unit-P4-S09: removeField поля и в наборе, и в selection → changed, удалено из обоих")
    void s09_removeFieldInBothDataSetAndSelection_reportsChanged() {
        XmlNode field = el("field", null, el("dataPath", "Цена"));
        XmlNode dataSet = el("dataSet", null,
                el("name", "MainDS"), el("query", "ВЫБРАТЬ Цена ИЗ Т"), field);

        XmlNode selItem = el("dcsset:item", null, el("dcsset:field", "Цена"));
        selItem.setAttribute("xsi:type", "dcsset:SelectedItemField");
        XmlNode settings = el("dcsset:settings", null, el("dcsset:selection", null, selItem));
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), settings);

        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, variant));
        SkdEditor editor = new SkdEditor(doc);

        // предусловия
        assertThat(findField(doc, "Цена")).as("поле есть в наборе до удаления").isNotNull();

        SkdEditor.OpResult r = editor.removeField("Цена", null, "Основной");

        assertThat(r.changed)
                .as("двойная мутация (набор + selection) → changed (агрегация removed||selectionTouched)")
                .isTrue();
        assertThat(findField(doc, "Цена"))
                .as("поле удалено из набора данных").isNull();
        XmlNode selection = doc.getRoot().child("settingsVariant").child("settings").child("selection");
        boolean stillInSelection = selection != null && selection.getChildren().stream()
                .anyMatch(it -> "Цена".equals(it.childText("field")));
        assertThat(stillInSelection).as("поле удалено и из selection").isFalse();
    }

    /**
     * S-09 edge: {@code removeParameter} существующего параметра — правдивый
     * {@code changed} (страж: операция числится «правдивой» в аудите S-09, фиксируем
     * её исполняемой регрессией наряду с removeField).
     */
    @Test
    @DisplayName("unit-P4-S09: removeParameter существующего параметра → changed, параметр удалён")
    void s09_removeParameterExisting_reportsChanged() {
        XmlNode param = el("parameter", null, el("name", "Валюта"));
        XmlNode dataSet = el("dataSet", null, el("name", "MainDS"), el("query", "ВЫБРАТЬ 1"));
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), el("dcsset:settings", null));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, param, variant));
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.removeParameter("Валюта");

        assertThat(r.changed).as("удаление существующего параметра → changed").isTrue();
        boolean stillPresent = doc.getRoot().children("parameter").stream()
                .anyMatch(p -> "Валюта".equals(p.childText("name")));
        assertThat(stillPresent).as("параметр удалён из схемы").isFalse();
    }

    /**
     * S-09 negative companion: {@code removeParameter} ОТСУТСТВУЮЩЕГО параметра —
     * правдивый {@code unchanged} (подлинный NO-OP, обе стороны правдивости).
     */
    @Test
    @DisplayName("unit-P4-S09-neg: removeParameter отсутствующего параметра остаётся unchanged")
    void s09_removeParameterAbsent_staysUnchanged() {
        XmlNode dataSet = el("dataSet", null, el("name", "MainDS"), el("query", "ВЫБРАТЬ 1"));
        XmlNode variant = el("settingsVariant", null,
                el("dcsset:name", "Основной"), el("dcsset:settings", null));
        XmlDocument doc = wrap(el("DataCompositionSchema", null, dataSet, variant));
        SkdEditor editor = new SkdEditor(doc);

        SkdEditor.OpResult r = editor.removeParameter("НетТакого");

        assertThat(r.changed).as("подлинный NO-OP removeParameter → unchanged").isFalse();
    }
}

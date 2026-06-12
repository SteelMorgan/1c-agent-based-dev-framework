package io.github.onec.xmlgen.form.edit;

import com.github._1c_syntax.bsl.types.AllowedLength;
import io.github.onec.xmlgen.model.TypeResolver;
import io.github.onec.xmlgen.validator.XmlNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.onec.xmlgen.editor.EditorUtils.createNode;

/**
 * Генератор канонического {@code <Type><v8:Type>…</v8:Type>[<v8:Qualifiers>…</v8:Qualifiers>]</Type>}
 * для Form.xml. Принимает DSL-строку в формате Python {@code form-edit} и эмитит результат
 * как {@link XmlNode}-дерево, пригодное для вставки в атрибут / колонку.
 *
 * <p>Поддерживаемые формы DSL:
 * <ul>
 *   <li>{@code string}, {@code string(100)}, {@code строка(100)}</li>
 *   <li>{@code decimal(10,2)}, {@code decimal(10,2,nonneg)}, {@code число(10,2)}</li>
 *   <li>{@code boolean}, {@code булево}</li>
 *   <li>{@code date}, {@code dateTime}, {@code time}, {@code дата}, {@code датаВремя}</li>
 *   <li>{@code CatalogRef.X}, {@code CatalogObject.X}, {@code DocumentRef.X}, и аналоги</li>
 *   <li>{@code справочникСсылка.X} → {@code CatalogRef.X} и т.п. (русские синонимы)</li>
 *   <li>{@code DynamicList}, {@code ValueTable}, {@code ValueTree}, {@code ValueList}</li>
 *   <li>Union: {@code string(50)|boolean|CatalogRef.X} (разделители {@code |} или {@code +})</li>
 * </ul>
 */
public class FormTypeEmitter {

    private static final Map<String, String> RUSSIAN_SYNONYMS = new HashMap<>();

    static {
        RUSSIAN_SYNONYMS.put("строка", "string");
        RUSSIAN_SYNONYMS.put("число", "decimal");
        RUSSIAN_SYNONYMS.put("булево", "boolean");
        RUSSIAN_SYNONYMS.put("дата", "date");
        RUSSIAN_SYNONYMS.put("датавремя", "dateTime");
        RUSSIAN_SYNONYMS.put("number", "decimal");
        RUSSIAN_SYNONYMS.put("bool", "boolean");
        RUSSIAN_SYNONYMS.put("справочникссылка", "CatalogRef");
        RUSSIAN_SYNONYMS.put("справочникобъект", "CatalogObject");
        RUSSIAN_SYNONYMS.put("документссылка", "DocumentRef");
        RUSSIAN_SYNONYMS.put("документобъект", "DocumentObject");
        RUSSIAN_SYNONYMS.put("перечислениессылка", "EnumRef");
        RUSSIAN_SYNONYMS.put("плансчетовссылка", "ChartOfAccountsRef");
        RUSSIAN_SYNONYMS.put("планвидовхарактеристикссылка", "ChartOfCharacteristicTypesRef");
        RUSSIAN_SYNONYMS.put("планвидоврасчётассылка", "ChartOfCalculationTypesRef");
        RUSSIAN_SYNONYMS.put("планвидоврасчетассылка", "ChartOfCalculationTypesRef");
        RUSSIAN_SYNONYMS.put("планобменассылка", "ExchangePlanRef");
        RUSSIAN_SYNONYMS.put("бизнеспроцессссылка", "BusinessProcessRef");
        RUSSIAN_SYNONYMS.put("задачассылка", "TaskRef");
        RUSSIAN_SYNONYMS.put("определяемыйтип", "DefinedType");
    }

    private static final Map<String, String> V8_TYPES = new LinkedHashMap<>();
    private static final Map<String, String> V8UI_TYPES = new LinkedHashMap<>();
    private static final Map<String, String> DCS_TYPES = new LinkedHashMap<>();

    static {
        V8_TYPES.put("ValueTable", "v8:ValueTable");
        V8_TYPES.put("ValueTree", "v8:ValueTree");
        V8_TYPES.put("ValueList", "v8:ValueListType");
        V8_TYPES.put("TypeDescription", "v8:TypeDescription");
        V8_TYPES.put("Universal", "v8:Universal");
        V8_TYPES.put("FixedArray", "v8:FixedArray");
        V8_TYPES.put("FixedStructure", "v8:FixedStructure");
        V8_TYPES.put("UUID", "v8:UUID");
        V8_TYPES.put("FillChecking", "v8:FillChecking");
        V8_TYPES.put("Null", "v8:Null");
        V8_TYPES.put("StandardBeginningDate", "v8:StandardBeginningDate");
        V8_TYPES.put("StandardPeriod", "v8:StandardPeriod");
        V8_TYPES.put("Type", "v8:Type");

        V8_TYPES.put("ConstantsSet", "cfg:ConstantsSet");

        V8UI_TYPES.put("FormattedString", "v8ui:FormattedString");
        V8UI_TYPES.put("Picture", "v8ui:Picture");
        V8UI_TYPES.put("Color", "v8ui:Color");
        V8UI_TYPES.put("Font", "v8ui:Font");
        V8UI_TYPES.put("SizeChangeMode", "v8ui:SizeChangeMode");
        V8UI_TYPES.put("VerticalAlign", "v8ui:VerticalAlign");
        V8UI_TYPES.put("HorizontalAlign", "v8ui:HorizontalAlign");

        DCS_TYPES.put("DataCompositionSettings", "dcsset:DataCompositionSettings");
        DCS_TYPES.put("DataCompositionSchema", "dcssch:DataCompositionSchema");
        DCS_TYPES.put("DataCompositionComparisonType", "dcscor:DataCompositionComparisonType");
    }

    /**
     * Построить {@code <Type>…</Type>} узел для заданного DSL-типа.
     * Null/пустая строка → пустой {@code <Type/>}.
     */
    public XmlNode emit(String dslType) {
        XmlNode type = createNode("Type");
        if (dslType == null || dslType.isBlank()) {
            return type;
        }
        //**agent TASK-174 [05.06.2026 12:32:00]
        // XG-10: (а) сплит paren-aware (CompositeType), чтобы не рвать "number+(15,2)" /
        // "number(15,2)"; (б) канонический порядок — сперва ВСЕ <v8:Type>, затем
        // квалификаторы (эталон: НастройкиВерсионированияОбъектов — xs:string +
        // CatalogRef, StringQualifiers после обоих типов). Раньше квалификаторы
        // интерливились с типами (type, qual, type) — не канон.
        java.util.List<XmlNode> qualifiers = new java.util.ArrayList<>();
        for (String part : io.github.onec.xmlgen.model.CompositeType.splitCompositeTypes(dslType)) {
            appendSingle(type, part.trim(), qualifiers);
        }
        for (XmlNode q : qualifiers) {
            type.addChild(q);
        }
        //**agent TASK-174
        return type;
    }

    private void appendSingle(XmlNode type, String raw, java.util.List<XmlNode> qualifiersOut) {
        String resolved = applySynonyms(raw);

        // Bare-types не через TypeResolver (без qualifiers)
        if ("DynamicList".equals(resolved)) {
            type.addChild(v8TypeText("cfg:DynamicList"));
            return;
        }
        if (V8_TYPES.containsKey(resolved)) {
            type.addChild(v8TypeText(V8_TYPES.get(resolved)));
            return;
        }
        if (V8UI_TYPES.containsKey(resolved)) {
            type.addChild(v8TypeText(V8UI_TYPES.get(resolved)));
            return;
        }
        if (DCS_TYPES.containsKey(resolved)) {
            type.addChild(v8TypeText(DCS_TYPES.get(resolved)));
            return;
        }

        TypeResolver.TypeInfo info = TypeResolver.resolve(resolved);
        type.addChild(v8TypeText(info.getXmlType()));
        Object q = info.getQualifiers();
        if (q instanceof TypeResolver.StringQualifiers) {
            qualifiersOut.add(stringQualifiers((TypeResolver.StringQualifiers) q));
        } else if (q instanceof TypeResolver.NumberQualifiers) {
            qualifiersOut.add(numberQualifiers((TypeResolver.NumberQualifiers) q));
        } else if (q instanceof TypeResolver.DateQualifiers) {
            qualifiersOut.add(dateQualifiers((TypeResolver.DateQualifiers) q));
        }
    }

    /**
     * Русские синонимы: "СправочникСсылка.Товары" → "CatalogRef.Товары",
     * "строка(100)" → "string(100)", "дата" → "date".
     * Если синоним не найден — возвращается исходная строка.
     */
    private String applySynonyms(String dslType) {
        if (dslType == null) return null;

        // Generic form "base(params)" — синоним base, params сохраняем
        int paren = dslType.indexOf('(');
        if (paren > 0) {
            String base = dslType.substring(0, paren);
            String rest = dslType.substring(paren);
            String syn = RUSSIAN_SYNONYMS.get(base.toLowerCase());
            return syn != null ? syn + rest : dslType;
        }
        // Form "base.name" — синоним только base
        int dot = dslType.indexOf('.');
        if (dot > 0) {
            String base = dslType.substring(0, dot);
            String rest = dslType.substring(dot);
            String syn = RUSSIAN_SYNONYMS.get(base.toLowerCase());
            return syn != null ? syn + rest : dslType;
        }
        // Bare
        String syn = RUSSIAN_SYNONYMS.get(dslType.toLowerCase());
        return syn != null ? syn : dslType;
    }

    private static XmlNode v8TypeText(String text) {
        XmlNode n = createNode("v8:Type");
        n.setText(text);
        return n;
    }

    private static XmlNode stringQualifiers(TypeResolver.StringQualifiers sq) {
        XmlNode q = createNode("v8:StringQualifiers");
        XmlNode len = createNode("v8:Length");
        len.setText(String.valueOf(sq.getLength()));
        q.addChild(len);
        XmlNode allowed = createNode("v8:AllowedLength");
        allowed.setText(sq.getAllowedLength() == AllowedLength.FIXED ? "Fixed" : "Variable");
        q.addChild(allowed);
        return q;
    }

    private static XmlNode numberQualifiers(TypeResolver.NumberQualifiers nq) {
        XmlNode q = createNode("v8:NumberQualifiers");
        XmlNode digits = createNode("v8:Digits");
        digits.setText(String.valueOf(nq.getDigits()));
        q.addChild(digits);
        XmlNode fraction = createNode("v8:FractionDigits");
        fraction.setText(String.valueOf(nq.getFractionDigits()));
        q.addChild(fraction);
        XmlNode sign = createNode("v8:AllowedSign");
        sign.setText(nq.getAllowedSign());
        q.addChild(sign);
        return q;
    }

    private static XmlNode dateQualifiers(TypeResolver.DateQualifiers dq) {
        XmlNode q = createNode("v8:DateQualifiers");
        XmlNode fractions = createNode("v8:DateFractions");
        fractions.setText(dq.getDateFractionsXml());
        q.addChild(fractions);
        return q;
    }
}

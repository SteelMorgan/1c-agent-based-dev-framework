package io.github.onec.xmlgen.form.edit;

import io.github.onec.xmlgen.validator.XmlNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies parity с Python form-edit emit_type():
 * canonical {@code <Type><v8:Type>…</v8:Type>[<v8:Qualifiers>…]</Type>} output.
 */
class FormTypeEmitterTest {

    private final FormTypeEmitter emitter = new FormTypeEmitter();

    @Test
    void emit_stringWithLength() {
        XmlNode t = emitter.emit("string(100)");
        assertEquals("Type", t.getName());
        assertEquals(2, t.getChildren().size());
        assertEquals("Type", t.getChildren().get(0).getName());
        assertEquals("v8", t.getChildren().get(0).getPrefix());
        assertEquals("xs:string", t.getChildren().get(0).getText());

        XmlNode qualifiers = t.getChildren().get(1);
        assertEquals("StringQualifiers", qualifiers.getName());
        assertEquals("v8", qualifiers.getPrefix());
        assertEquals("100", qualifiers.child("Length").getText());
        assertEquals("Variable", qualifiers.child("AllowedLength").getText());
    }

    @Test
    void emit_decimalWithNonneg() {
        XmlNode t = emitter.emit("decimal(10,2,nonneg)");
        assertEquals("xs:decimal", t.getChildren().get(0).getText());

        XmlNode qualifiers = t.getChildren().get(1);
        assertEquals("NumberQualifiers", qualifiers.getName());
        assertEquals("10", qualifiers.child("Digits").getText());
        assertEquals("2", qualifiers.child("FractionDigits").getText());
        assertEquals("Nonnegative", qualifiers.child("AllowedSign").getText());
    }

    @Test
    void emit_dateTime() {
        XmlNode t = emitter.emit("dateTime");
        assertEquals("xs:dateTime", t.getChildren().get(0).getText());

        XmlNode qualifiers = t.getChildren().get(1);
        assertEquals("DateQualifiers", qualifiers.getName());
        assertEquals("DateTime", qualifiers.child("DateFractions").getText());
    }

    @Test
    void emit_boolean_noQualifiers() {
        XmlNode t = emitter.emit("boolean");
        assertEquals(1, t.getChildren().size());
        assertEquals("xs:boolean", t.getChildren().get(0).getText());
    }

    @Test
    void emit_catalogRef() {
        XmlNode t = emitter.emit("CatalogRef.Товары");
        assertEquals(1, t.getChildren().size());
        assertEquals("cfg:CatalogRef.Товары", t.getChildren().get(0).getText());
    }

    @Test
    void emit_dynamicList() {
        XmlNode t = emitter.emit("DynamicList");
        assertEquals(1, t.getChildren().size());
        assertEquals("cfg:DynamicList", t.getChildren().get(0).getText());
    }

    @Test
    void emit_valueTable() {
        XmlNode t = emitter.emit("ValueTable");
        assertEquals("v8:ValueTable", t.getChildren().get(0).getText());
    }

    @Test
    void emit_task174_newFormBareTypes() {
        assertEquals("v8:StandardPeriod", emitter.emit("StandardPeriod").getChildren().get(0).getText());
        assertEquals("v8:StandardBeginningDate", emitter.emit("StandardBeginningDate").getChildren().get(0).getText());
        assertEquals("v8:FillChecking", emitter.emit("FillChecking").getChildren().get(0).getText());
        assertEquals("cfg:ConstantsSet", emitter.emit("ConstantsSet").getChildren().get(0).getText());
        assertEquals("v8ui:VerticalAlign", emitter.emit("VerticalAlign").getChildren().get(0).getText());
    }

    @Test
    void emit_russianSynonyms() {
        XmlNode str = emitter.emit("строка(50)");
        assertEquals("xs:string", str.getChildren().get(0).getText());
        assertEquals("50", str.getChildren().get(1).child("Length").getText());

        XmlNode ref = emitter.emit("СправочникСсылка.Товары");
        assertEquals("cfg:CatalogRef.Товары", ref.getChildren().get(0).getText());

        XmlNode num = emitter.emit("число(10,2)");
        assertEquals("xs:decimal", num.getChildren().get(0).getText());
    }

    @Test
    void emit_unionTypes() {
        XmlNode t = emitter.emit("string(50)|boolean|CatalogRef.Товары");
        //**agent TASK-174 [05.06.2026 13:10:00]
        // XG-10: канонический порядок — сперва ВСЕ v8:Type подряд, затем квалификаторы
        // (эталон конфигурации: НастройкиВерсионированияОбъектов — xs:string + CatalogRef,
        // StringQualifiers ПОСЛЕ обоих типов). Прежнее ожидание (qualifier сразу после
        // своего типа) закрепляло неканоническую сериализацию.
        assertEquals(4, t.getChildren().size());
        assertEquals("xs:string", t.getChildren().get(0).getText());
        assertEquals("xs:boolean", t.getChildren().get(1).getText());
        assertEquals("cfg:CatalogRef.Товары", t.getChildren().get(2).getText());
        assertEquals("StringQualifiers", t.getChildren().get(3).getName());
        //**agent TASK-174
    }

    @Test
    void emit_nullType_empty() {
        XmlNode t = emitter.emit(null);
        assertEquals("Type", t.getName());
        assertTrue(t.getChildren().isEmpty());
    }

    @Test
    void emit_rawXmlTypeStringNotSupported_fallsThroughTypeResolver() {
        // Raw "xs:string" — не поддерживается FormTypeEmitter напрямую (он ожидает DSL).
        // FormEditor.emitType() обходит это через raw-prefix branch. Здесь просто проверяем
        // что TypeResolver выкидывает исключение.
        assertThrows(IllegalArgumentException.class, () -> emitter.emit("xs:string"));
    }
}

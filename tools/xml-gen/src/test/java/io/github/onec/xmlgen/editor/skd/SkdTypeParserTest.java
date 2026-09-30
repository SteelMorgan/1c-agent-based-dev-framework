package io.github.onec.xmlgen.editor.skd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkdTypeParserTest {

    @Test
    void testParseString() {
        List<SkdTypeParser.TypePart> parts = SkdTypeParser.parse("string");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).xmlType).isEqualTo("xs:string");
        assertThat(parts.get(0).stringLength).isNull();
    }

    @Test
    void testParseStringWithLength() {
        List<SkdTypeParser.TypePart> parts = SkdTypeParser.parse("string(50)");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).stringLength).isEqualTo(50);
    }

    @Test
    void testParseDecimal() {
        List<SkdTypeParser.TypePart> parts = SkdTypeParser.parse("decimal(15,2)");
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).xmlType).isEqualTo("xs:decimal");
        assertThat(parts.get(0).numberDigits).isEqualTo(15);
        assertThat(parts.get(0).numberFractionDigits).isEqualTo(2);
        assertThat(parts.get(0).nonneg).isFalse();
    }

    @Test
    void testParseDecimalNonneg() {
        List<SkdTypeParser.TypePart> parts = SkdTypeParser.parse("decimal(15,2),nonneg");
        assertThat(parts.get(0).nonneg).isTrue();
    }

    @Test
    void testParseNumberAlias() {
        List<SkdTypeParser.TypePart> parts = SkdTypeParser.parse("number(10,2)");
        assertThat(parts.get(0).xmlType).isEqualTo("xs:decimal");
    }

    @Test
    void testParseDate() {
        assertThat(SkdTypeParser.parse("date").get(0).xmlType).isEqualTo("xs:dateTime");
    }

    @Test
    void testParseBoolean() {
        assertThat(SkdTypeParser.parse("boolean").get(0).xmlType).isEqualTo("xs:boolean");
    }

    @Test
    void testParseUuid() {
        assertThat(SkdTypeParser.parse("uuid").get(0).xmlType).isEqualTo("v8:UUID");
    }

    @Test
    void testParseCatalogRef() {
        List<SkdTypeParser.TypePart> p = SkdTypeParser.parse("CatalogRef.Контрагенты");
        assertThat(p.get(0).xmlType).isEqualTo("d5p1:CatalogRef.Контрагенты");
    }

    @Test
    void testParseDocumentRef() {
        List<SkdTypeParser.TypePart> p = SkdTypeParser.parse("DocumentRef.ЗаказКлиента");
        assertThat(p.get(0).xmlType).isEqualTo("d5p1:DocumentRef.ЗаказКлиента");
    }

    @Test
    void testParseRefAlias() {
        List<SkdTypeParser.TypePart> p = SkdTypeParser.parse("ref:Catalog.Контрагенты");
        assertThat(p.get(0).xmlType).isEqualTo("d5p1:CatalogRef.Контрагенты");
    }

    @Test
    void testParseStandardPeriod() {
        List<SkdTypeParser.TypePart> p = SkdTypeParser.parse("StandardPeriod");
        assertThat(p.get(0).xmlType).isEqualTo("v8:StandardPeriod");
    }

    @Test
    void testParseCompositeType() {
        List<SkdTypeParser.TypePart> p = SkdTypeParser.parse("decimal(15,2)|string(50)");
        assertThat(p).hasSize(2);
        assertThat(p.get(0).xmlType).isEqualTo("xs:decimal");
        assertThat(p.get(1).xmlType).isEqualTo("xs:string");
    }

    @Test
    void testParseUnclosedParen() {
        assertThrows(SkdParseException.class, () -> SkdTypeParser.parse("decimal(15,2"));
    }

    @Test
    void testParseUnknownType() {
        assertThrows(SkdParseException.class, () -> SkdTypeParser.parse("string@"));
    }

    @Test
    void testParseEmpty() {
        assertThrows(SkdParseException.class, () -> SkdTypeParser.parse(""));
    }
}

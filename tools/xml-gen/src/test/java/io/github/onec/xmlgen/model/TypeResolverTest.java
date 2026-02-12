package io.github.onec.xmlgen.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeResolverTest {
    
    @Test
    void testStringTypes() {
        // string
        TypeResolver.TypeInfo type = TypeResolver.resolve("string");
        assertThat(type.getXmlType()).isEqualTo("xs:string");
        assertThat(type.getQualifiers()).isInstanceOf(TypeResolver.StringQualifiers.class);
        TypeResolver.StringQualifiers sq = (TypeResolver.StringQualifiers) type.getQualifiers();
        assertThat(sq.getLength()).isEqualTo(0);
        assertThat(sq.getAllowedLength()).isEqualTo("Variable");
        
        // string(100)
        type = TypeResolver.resolve("string(100)");
        sq = (TypeResolver.StringQualifiers) type.getQualifiers();
        assertThat(sq.getLength()).isEqualTo(100);
        assertThat(sq.getAllowedLength()).isEqualTo("Variable");
        
        // string!(50)
        type = TypeResolver.resolve("string!(50)");
        sq = (TypeResolver.StringQualifiers) type.getQualifiers();
        assertThat(sq.getLength()).isEqualTo(50);
        assertThat(sq.getAllowedLength()).isEqualTo("Fixed");
    }
    
    @Test
    void testNumberTypes() {
        // number(10,2)
        TypeResolver.TypeInfo type = TypeResolver.resolve("number(10,2)");
        assertThat(type.getXmlType()).isEqualTo("xs:decimal");
        assertThat(type.getQualifiers()).isInstanceOf(TypeResolver.NumberQualifiers.class);
        TypeResolver.NumberQualifiers nq = (TypeResolver.NumberQualifiers) type.getQualifiers();
        assertThat(nq.getDigits()).isEqualTo(10);
        assertThat(nq.getFractionDigits()).isEqualTo(2);
        assertThat(nq.getAllowedSign()).isEqualTo("Any");
        
        // number+(10,2)
        type = TypeResolver.resolve("number+(10,2)");
        nq = (TypeResolver.NumberQualifiers) type.getQualifiers();
        assertThat(nq.getAllowedSign()).isEqualTo("Nonnegative");
        
        // number(10)
        type = TypeResolver.resolve("number(10)");
        nq = (TypeResolver.NumberQualifiers) type.getQualifiers();
        assertThat(nq.getDigits()).isEqualTo(10);
        assertThat(nq.getFractionDigits()).isEqualTo(0);
    }
    
    @Test
    void testBooleanType() {
        TypeResolver.TypeInfo type = TypeResolver.resolve("boolean");
        assertThat(type.getXmlType()).isEqualTo("xs:boolean");
        assertThat(type.getQualifiers()).isNull();
    }
    
    @Test
    void testDateTypes() {
        TypeResolver.TypeInfo type = TypeResolver.resolve("date");
        assertThat(type.getXmlType()).isEqualTo("xs:dateTime");
        TypeResolver.DateQualifiers dq = (TypeResolver.DateQualifiers) type.getQualifiers();
        assertThat(dq.getDateFractions()).isEqualTo("Date");
        
        type = TypeResolver.resolve("time");
        dq = (TypeResolver.DateQualifiers) type.getQualifiers();
        assertThat(dq.getDateFractions()).isEqualTo("Time");
        
        type = TypeResolver.resolve("datetime");
        dq = (TypeResolver.DateQualifiers) type.getQualifiers();
        assertThat(dq.getDateFractions()).isEqualTo("DateTime");
    }
    
    @Test
    void testUuidType() {
        TypeResolver.TypeInfo type = TypeResolver.resolve("uuid");
        assertThat(type.getXmlType()).isEqualTo("v8:UUID");
        assertThat(type.getQualifiers()).isNull();
    }
    
    @Test
    void testCollectionTypes() {
        TypeResolver.TypeInfo type = TypeResolver.resolve("valuetable");
        assertThat(type.getXmlType()).isEqualTo("v8:ValueTable");
        
        type = TypeResolver.resolve("valuetree");
        assertThat(type.getXmlType()).isEqualTo("v8:ValueTree");
    }
    
    @Test
    void testReferenceTypes() {
        TypeResolver.TypeInfo type = TypeResolver.resolve("ref:Catalog.Номенклатура");
        assertThat(type.getXmlType()).isEqualTo("cfg:CatalogRef.Номенклатура");
        assertThat(type.getQualifiers()).isNull();
    }
    
    @Test
    void testUnknownType() {
        assertThatThrownBy(() -> TypeResolver.resolve("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown type");
    }
    
    @Test
    void testNullType() {
        assertThatThrownBy(() -> TypeResolver.resolve(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }
}

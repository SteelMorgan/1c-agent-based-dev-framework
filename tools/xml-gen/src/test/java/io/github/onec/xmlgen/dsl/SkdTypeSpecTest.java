package io.github.onec.xmlgen.dsl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Тесты парсера {@link SkdTypeSpec}.
 */
class SkdTypeSpecTest {

    @Test
    void parsesStringWithLength() {
        SkdTypeSpec.Parsed p = SkdTypeSpec.parse("string(50)");
        assertThat(p.isComposite()).isFalse();
        SkdTypeSpec.Component c = p.first();
        assertThat(c.getKind()).isEqualTo(SkdTypeSpec.Component.Kind.STRING);
        assertThat(c.getXmlType()).isEqualTo("xs:string");
        assertThat(c.getLength()).isEqualTo(50);
        assertThat(c.getFixedLength()).isFalse();
    }

    @Test
    void parsesStringWithFixedLength() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("string!(10)").first();
        assertThat(c.getFixedLength()).isTrue();
    }

    @Test
    void parsesDecimalWithNonNegSuffix() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("decimal(15,2),nonneg").first();
        assertThat(c.getKind()).isEqualTo(SkdTypeSpec.Component.Kind.DECIMAL);
        assertThat(c.getDigits()).isEqualTo(15);
        assertThat(c.getFractionDigits()).isEqualTo(2);
        assertThat(c.getNonNegative()).isTrue();
    }

    @Test
    void parsesDecimalWithInnerNonNeg() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("decimal(10,3,nonneg)").first();
        assertThat(c.getNonNegative()).isTrue();
        assertThat(c.getDigits()).isEqualTo(10);
        assertThat(c.getFractionDigits()).isEqualTo(3);
    }

    @Test
    void parsesBareDecimalAsMoney() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("decimal").first();
        assertThat(c.getDigits()).isEqualTo(10);
        assertThat(c.getFractionDigits()).isEqualTo(2);
    }

    @Test
    void parsesBoolean() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("boolean").first();
        assertThat(c.getKind()).isEqualTo(SkdTypeSpec.Component.Kind.BOOLEAN);
        assertThat(c.getXmlType()).isEqualTo("xs:boolean");
    }

    @Test
    void parsesDateAndDateTime() {
        SkdTypeSpec.Component d = SkdTypeSpec.parse("date").first();
        assertThat(d.getDateFractions()).isEqualTo("Date");

        SkdTypeSpec.Component dt = SkdTypeSpec.parse("dateTime").first();
        assertThat(dt.getDateFractions()).isEqualTo("DateTime");
    }

    @Test
    void parsesCatalogRef() {
        SkdTypeSpec.Component c = SkdTypeSpec.parse("CatalogRef.Контрагенты").first();
        assertThat(c.getKind()).isEqualTo(SkdTypeSpec.Component.Kind.REFERENCE);
        assertThat(c.getXmlType()).contains("CatalogRef.Контрагенты");
    }

    @Test
    void parsesCompositeViaPipe() {
        SkdTypeSpec.Parsed p = SkdTypeSpec.parse("decimal(15,2)|string(50)");
        assertThat(p.isComposite()).isTrue();
        assertThat(p.getComponents()).hasSize(2);
        assertThat(p.getComponents().get(0).getKind()).isEqualTo(SkdTypeSpec.Component.Kind.DECIMAL);
        assertThat(p.getComponents().get(1).getKind()).isEqualTo(SkdTypeSpec.Component.Kind.STRING);
    }

    @Test
    void parsesCompositeViaList() {
        SkdTypeSpec.Parsed p = SkdTypeSpec.parse(List.of("CatalogRef.А", "CatalogRef.Б"));
        assertThat(p.isComposite()).isTrue();
        assertThat(p.getComponents()).hasSize(2);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> SkdTypeSpec.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

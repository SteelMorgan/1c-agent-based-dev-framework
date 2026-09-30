package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

// XG-111/XG-112/XG-113/XG-114 [28.09.2026]
/**
 * Проверки FormValidator на испорченных копиях рабочей формы (мутанты) и отсутствие
 * ложных ERROR на канонических формах Designer 8.3.27.
 */
class FormValidatorXg113Test {

    private static final Path RES = Path.of("src/test/resources/xg113");

    private static List<ValidationIssue> errors(String file) throws Exception {
        XmlDocument doc = new XmlStructureReader().parse(RES.resolve(file));
        return new FormValidator().validate(doc, ValidationLevel.SEMANTIC).stream()
                .filter(i -> i.getSeverity() == Severity.ERROR)
                .collect(Collectors.toList());
    }

    @Test
    void baselineHasNoErrors() throws Exception {
        assertThat(errors("BASELINE.xml")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "XG-15_group_no_ChildItems.xml,FORM-134",
            "XG-19_Pages_no_ChildItems.xml,FORM-134",
            "XG-57_CommandName_bare.xml,FORM-135",
            "XG-105_Multiline_case.xml,FORM-136",
            "NS_prefix_undeclared.xml,FORM-137",
            "Duplicate_element_name.xml,FORM-138"
    })
    void mutantIsRejected(String file, String code) throws Exception {
        assertThat(errors(file)).extracting(ValidationIssue::getCode).contains(code);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "canon_addition_dup.xml",      // три компаньона 'Addition' - канон
            "canon_hyperlink.xml",         // свойство Hyperlink у декорации, не элемент
            "canon_excluded_cmd.xml",      // латинские ExcludedCommand (FORM-132 FP)
            "canon_items_currentdata.xml"  // Items.X.CurrentData (FORM-102 FP)
    })
    void canonicalDesignerFormHasNoErrors(String file) throws Exception {
        assertThat(errors(file)).isEmpty();
    }
}

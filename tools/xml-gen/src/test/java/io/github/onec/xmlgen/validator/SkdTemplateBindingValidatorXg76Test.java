package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [12.07.2026 00:00:00]
class SkdTemplateBindingValidatorXg76Test {

    private final SkdValidator validator = new SkdValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @Test
    void unknownTemplateTypeIsRejected() throws Exception {
        List<ValidationIssue> issues = validate("xg76-unknown-template-type.xml");

        assertThat(issues).anyMatch(issue -> "SKD-008".equals(issue.getCode())
                && issue.getSeverity() == Severity.ERROR
                && issue.getMessage().equals("Unknown SKD group templateType 'UnknownType', "
                + "expected one of: Header, OverallHeader, GroupHeader, Footer, OverallFooter"));
    }

    @Test
    void bindingToMissingAreaTemplateIsRejected() throws Exception {
        List<ValidationIssue> issues = validate("xg76-missing-area-template.xml");

        assertThat(issues).anyMatch(issue -> "SKD-009".equals(issue.getCode())
                && issue.getSeverity() == Severity.ERROR
                && issue.getMessage().contains("ОтсутствующаяОбласть"));
    }

    @Test
    void canonicalBindingToExistingAreaTemplatePasses() throws Exception {
        List<ValidationIssue> issues = validate("xg76-valid-binding.xml");

        assertThat(issues).noneMatch(issue -> "SKD-008".equals(issue.getCode())
                || "SKD-009".equals(issue.getCode()));
    }

    @Test
    void fullSchemaWithoutTemplateBlockRejectsMissingArea() throws Exception {
        List<ValidationIssue> issues = validate("xg76-full-zero-template-missing-binding.xml");

        assertThat(issues).anyMatch(issue -> "SKD-009".equals(issue.getCode())
                && issue.getSeverity() == Severity.ERROR
                && issue.getMessage().contains("НесуществующаяОбласть"));
    }

    private List<ValidationIssue> validate(String fixture) throws Exception {
        Path path = Path.of("src/test/resources/skd", fixture);
        return validator.validate(reader.parse(path), ValidationLevel.STRUCTURE);
    }
}
//--agent TASK-174

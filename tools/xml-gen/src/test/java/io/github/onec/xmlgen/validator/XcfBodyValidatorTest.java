package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XcfBodyValidatorTest {

    @TempDir
    Path tempDir;

    private final XmlStructureReader reader = new XmlStructureReader();
    private final XcfBodyValidator validator = new XcfBodyValidator();

    @Test
    void graphicialSchemaFlowchartUsesXcfSchemeNamespaceAndVersion() throws Exception {
        Path file = write("Flowchart.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <GraphicalSchema xmlns="http://v8.1c.ru/8.3/xcf/scheme"
                                 xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                 xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                 version="2.20"/>
                """);

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).isEmpty();
    }

    @Test
    void graphicialSchemaWrongNamespaceIsError() throws Exception {
        Path file = write("Flowchart.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <GraphicalSchema xmlns="http://v8.1c.ru/8.3/flowchart" version="2.20"/>
                """);

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("XCF-002"));
    }

    @Test
    void extPropsBodiesRequireVersion() throws Exception {
        Path file = write("Picture.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <ExtPicture xmlns="http://v8.1c.ru/8.3/xcf/extrnprops"/>
                """);

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).anyMatch(i -> i.getCode().equals("XCF-003"));
    }

    @Test
    void appearanceTemplateDoesNotRequireRootVersion() throws Exception {
        Path file = write("Template.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <AppearanceTemplate xmlns="http://v8.1c.ru/8.1/data-composition-system/appearance-template"/>
                """);

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).isEmpty();
    }

    @Test
    void jobScheduleUsesExtPropsNamespaceAndVersion() throws Exception {
        Path file = write("Schedule.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <JobSchedule xmlns="http://v8.1c.ru/8.3/xcf/extrnprops"
                             xmlns:ent="http://v8.1c.ru/8.1/data/enterprise"
                             version="2.20">
                  <Schedule>
                    <ent:WeekDays>1 2 3 4 5 6 7</ent:WeekDays>
                    <ent:Months>1 2 3 4 5 6 7 8 9 10 11 12</ent:Months>
                  </Schedule>
                </JobSchedule>
                """);

        List<ValidationIssue> issues = validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);

        assertThat(issues).isEmpty();
    }

    private Path write(String name, String xml) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, xml, StandardCharsets.UTF_8);
        return file;
    }
}

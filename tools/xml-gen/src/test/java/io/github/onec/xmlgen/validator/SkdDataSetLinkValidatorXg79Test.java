package io.github.onec.xmlgen.validator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//++agent TASK-174 [10.07.2026 22:45:00]
class SkdDataSetLinkValidatorXg79Test {

    @TempDir
    Path tempDir;

    private final SkdValidator validator = new SkdValidator();
    private final XmlStructureReader reader = new XmlStructureReader();

    @Test
    void skd110RequiresAllCanonicalLinkElements() throws Exception {
        List<ValidationIssue> issues = validate(link("А", null));
        assertThat(issues).anyMatch(issue -> "SKD-110".equals(issue.getCode()));
    }

    @Test
    void skd111ChecksFieldInItsOwnDataSet() throws Exception {
        List<ValidationIssue> issues = validate(link("НетПоля", "А"));
        assertThat(issues).anyMatch(issue -> "SKD-111".equals(issue.getCode())
                && issue.getMessage().contains("unknown source field"));
    }

    @Test
    void skd112RejectsOnlyExactDuplicateMapping() throws Exception {
        String first = link("А", "А");
        List<ValidationIssue> duplicate = validate(first + first);
        assertThat(duplicate).anyMatch(issue -> "SKD-112".equals(issue.getCode()));

        List<ValidationIssue> distinct = validate(first + link("Б", "Б"));
        assertThat(distinct).noneMatch(issue -> "SKD-110".equals(issue.getCode())
                || "SKD-111".equals(issue.getCode()) || "SKD-112".equals(issue.getCode()));
    }

    private List<ValidationIssue> validate(String links) throws Exception {
        Path file = tempDir.resolve("Template-" + System.nanoTime() + ".xml");
        Files.writeString(file, schema(links), StandardCharsets.UTF_8);
        return validator.validate(reader.parse(file), ValidationLevel.SEMANTIC);
    }

    private static String schema(String links) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <DataCompositionSchema xmlns="http://v8.1c.ru/8.1/data-composition-system/schema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Источник</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>А</dataPath><field>А</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Б</dataPath><field>Б</field></field>
                \t\t<objectName>Источник</objectName>
                \t</dataSet>
                \t<dataSet xsi:type="DataSetObject">
                \t\t<name>Назначение</name>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>А</dataPath><field>А</field></field>
                \t\t<field xsi:type="DataSetFieldField"><dataPath>Б</dataPath><field>Б</field></field>
                \t\t<objectName>Назначение</objectName>
                \t</dataSet>
                """ + links + "</DataCompositionSchema>\n";
    }

    private static String link(String sourceExpression, String destinationExpression) {
        return "\t<dataSetLink>\n"
                + "\t\t<sourceDataSet>Источник</sourceDataSet>\n"
                + "\t\t<destinationDataSet>Назначение</destinationDataSet>\n"
                + "\t\t<sourceExpression>" + sourceExpression + "</sourceExpression>\n"
                + (destinationExpression == null ? "" : "\t\t<destinationExpression>"
                + destinationExpression + "</destinationExpression>\n")
                + "\t</dataSetLink>\n";
    }
}
//--agent TASK-174

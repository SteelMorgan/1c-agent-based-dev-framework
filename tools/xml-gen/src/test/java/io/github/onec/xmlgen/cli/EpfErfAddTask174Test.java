package io.github.onec.xmlgen.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EpfErfAddTask174Test {

    @TempDir
    Path tempDir;

    @Test
    void addFormToExistingErfUsesExternalReportPathsAndObjectType() throws Exception {
        Commands.execute("epf", new String[]{
                "init", "--type", "report", "--name", "ТестовыйОтчет", tempDir.toString()
        });

        Commands.execute("epf", new String[]{
                "add-form", "--epf", "ТестовыйОтчет", "--name", "ФормаОтчета", "--default",
                tempDir.toString()
        });

        String root = Files.readString(tempDir.resolve("ТестовыйОтчет.xml"), StandardCharsets.UTF_8);
        assertThat(root)
                .contains("<ExternalReport uuid=")
                .contains("<DefaultForm>ExternalReport.ТестовыйОтчет.Form.ФормаОтчета</DefaultForm>")
                .doesNotContain("ExternalDataProcessor.ТестовыйОтчет.Form.ФормаОтчета");

        String form = Files.readString(
                tempDir.resolve("ТестовыйОтчет/Forms/ФормаОтчета/Ext/Form.xml"),
                StandardCharsets.UTF_8);
        assertThat(form)
                .contains("<v8:Type>cfg:ExternalReportObject.ТестовыйОтчет</v8:Type>")
                .doesNotContain("cfg:ExternalDataProcessorObject.ТестовыйОтчет");
    }

    @Test
    void addDcsTemplateToExistingErfSetsExternalReportMainDcs() throws Exception {
        Commands.execute("epf", new String[]{
                "init", "--type", "report", "--name", "ТестовыйОтчет", tempDir.toString()
        });

        Commands.execute("epf", new String[]{
                "add-template", "--epf", "ТестовыйОтчет", "--name", "ОсновнаяСхема",
                "--type", "DataCompositionSchema", tempDir.toString()
        });

        String root = Files.readString(tempDir.resolve("ТестовыйОтчет.xml"), StandardCharsets.UTF_8);
        assertThat(root)
                .contains("<MainDataCompositionSchema>ExternalReport.ТестовыйОтчет.Template.ОсновнаяСхема</MainDataCompositionSchema>")
                .doesNotContain("<MainDataCompositionSchema>ExternalDataProcessor.");
    }

    @Test
    void addTemplateRejectsDuplicateChildObjectBeforeWriting() throws Exception {
        Commands.execute("epf", new String[]{
                "init", "--name", "ТестоваяОбработка", tempDir.toString()
        });
        Commands.execute("epf", new String[]{
                "add-template", "--epf", "ТестоваяОбработка", "--name", "Макет",
                "--type", "SpreadsheetDocument", tempDir.toString()
        });

        assertThatThrownBy(() -> Commands.execute("epf", new String[]{
                "add-template", "--epf", "ТестоваяОбработка", "--name", "Макет",
                "--type", "SpreadsheetDocument", tempDir.toString()
        })).hasMessageContaining("already exists");
    }
}

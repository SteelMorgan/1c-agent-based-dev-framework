package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdStructureUpsertDsl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 XG-96 [12.07.2026 00:00:00]
class SkdStructureUpsertXg96Test {

    @Test
    void directSelectionAcceptsUniqueFieldFromLinkedDataSetAndIsByteNoOpOnRepeat() {
        String original = schema(true, false);
        SkdStructureUpsertDsl patch = patch("ПолеB");

        SkdStructureUpsertEditor.Result first = new SkdStructureUpsertEditor().apply(original, patch);
        SkdStructureUpsertEditor.Result second = new SkdStructureUpsertEditor().apply(first.content(), patch);

        assertThat(first.changed()).isTrue();
        assertThat(first.content()).contains("<dcsset:field>ПолеB</dcsset:field>")
                .contains("<opaque>byte-exact sibling</opaque>");
        assertThat(second.changed()).isFalse();
        assertThat(second.content()).isEqualTo(first.content());
        assertThat(first.content().replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void directSelectionRejectsUnrelatedMissingAmbiguousAndDuplicateFieldsFailClosed() {
        assertThatThrownBy(() -> new SkdStructureUpsertEditor().apply(schema(false, false), patch("ПолеB")))
                .hasMessageContaining("Unknown reachable");
        assertThatThrownBy(() -> new SkdStructureUpsertEditor().apply(schema(true, false), patch("НетПоля")))
                .hasMessageContaining("Unknown reachable");
        assertThatThrownBy(() -> new SkdStructureUpsertEditor().apply(schema(true, true), patch("Общее")))
                .hasMessageContaining("Ambiguous reachable");
        SkdStructureUpsertDsl duplicate = patch("ПолеB");
        duplicate.getItems().get(0).setSelection(List.of("ПолеB", "ПолеB"));
        assertThatThrownBy(() -> new SkdStructureUpsertEditor().apply(schema(true, false), duplicate))
                .hasMessageContaining("Duplicate selection field");
    }

    private static SkdStructureUpsertDsl patch(String field) {
        SkdStructureUpsertDsl patch = new SkdStructureUpsertDsl();
        patch.setVariant("Основной");
        patch.setDataSet("A");
        SkdStructureUpsertDsl.Item item = new SkdStructureUpsertDsl.Item();
        item.setKind("group");
        item.setName("Смешанная");
        item.setSelection(List.of(field));
        patch.setItems(List.of(item));
        return patch;
    }

    private static String schema(boolean linked, boolean duplicateCommon) {
        String commonB = duplicateCommon
                ? "\t\t<field><dataPath>Общее</dataPath><field>Общее</field></field>\r\n" : "";
        String link = linked ? "\t<dataSetLink>\r\n"
                + "\t\t<sourceDataSet>A</sourceDataSet><destinationDataSet>B</destinationDataSet>\r\n"
                + "\t\t<sourceExpression>КлючA</sourceExpression><destinationExpression>КлючB</destinationExpression>\r\n"
                + "\t</dataSetLink>\r\n" : "";
        return "<DataCompositionSchema xmlns:dcsset=\"urn:dcsset\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
                + "\t<dataSet><name>A</name>\r\n"
                + "\t\t<field><dataPath>КлючA</dataPath><field>КлючA</field></field>\r\n"
                + "\t\t<field><dataPath>Общее</dataPath><field>Общее</field></field>\r\n\t</dataSet>\r\n"
                + "\t<dataSet><name>B</name>\r\n"
                + "\t\t<field><dataPath>КлючB</dataPath><field>КлючB</field></field>\r\n"
                + "\t\t<field><dataPath>ПолеB</dataPath><field>ПолеB</field></field>\r\n"
                + commonB + "\t</dataSet>\r\n" + link
                + "\t<settingsVariant><name>Основной</name><settings>\r\n"
                + "\t\t<item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                + "\t\t\t<name>Смешанная</name>\r\n"
                + "\t\t\t<selection><item xsi:type=\"dcsset:SelectedItemField\"><field>КлючA</field></item></selection>\r\n"
                + "\t\t\t<opaque>byte-exact sibling</opaque>\r\n"
                + "\t\t</item>\r\n\t</settings></settingsVariant>\r\n</DataCompositionSchema>\r\n";
    }
}
//--agent TASK-174 XG-96

package io.github.onec.xmlgen.editor;

import io.github.onec.xmlgen.dsl.SkdStructureUpsertDsl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

//++agent TASK-174 XG-98 [12.07.2026 00:00:00]
class SkdStructureNestedUpsertXg98Test {

    @Test
    void updatesUniqueNestedGroupInPlaceAndPreservesUnmanagedSpans() {
        String source = schema(false);
        SkdStructureUpsertDsl patch = patch();

        SkdStructureUpsertEditor.Result first = new SkdStructureUpsertEditor().apply(source, patch);

        assertThat(first.changed()).isTrue();
        assertThat(first.content())
                .contains("<dcsset:name>Родитель</dcsset:name>\r\n")
                .contains("<dcsset:name>ГруппировкаДеталиПартий</dcsset:name>\r\n")
                .contains("<dcsset:field>ПартияДокумент</dcsset:field>")
                .contains("<dcsset:field>Монета</dcsset:field>")
                .contains("<dcsset:filter><dcsset:item xsi:type=\"dcsset:FilterItemComparison\"/></dcsset:filter>")
                .contains("<dcsset:conditionalAppearance><dcsset:item/></dcsset:conditionalAppearance>")
                .contains("<dcsset:outputParameters><dcsset:item/></dcsset:outputParameters>")
                .contains("<dcsset:name>ФизическийМакет</dcsset:name>")
                .contains("<dcsset:name>Сосед</dcsset:name>");
        assertThat(count(first.content(), "<dcsset:name>ГруппировкаДеталиПартий</dcsset:name>"))
                .isOne();
        assertThat(first.content().replace("\r\n", "")).doesNotContain("\n");

        SkdStructureUpsertEditor.Result repeat =
                new SkdStructureUpsertEditor().apply(first.content(), patch);
        assertThat(repeat.changed()).isFalse();
        assertThat(repeat.content()).isEqualTo(first.content());
    }

    @Test
    void duplicateNestedIdentityFailsClosed() {
        String source = schema(true);

        assertThatThrownBy(() -> new SkdStructureUpsertEditor().apply(source, patch()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate existing structure identity")
                .hasMessageContaining("ГруппировкаДеталиПартий");
    }

    private static SkdStructureUpsertDsl patch() {
        SkdStructureUpsertDsl patch = new SkdStructureUpsertDsl();
        patch.setVariant("Основной_1");
        patch.setDataSet("Движения");
        SkdStructureUpsertDsl.Item item = new SkdStructureUpsertDsl.Item();
        item.setKind("group");
        item.setName("ГруппировкаДеталиПартий");
        item.setGroupItems(List.of("ПартияДокумент", "ПартияНомер", "ПартияДата",
                "ПартияВид", "Монета"));
        item.setSelection(List.of("ПартияДокумент", "ПартияНомер", "ПартияДата",
                "ПартияВид", "Монета"));
        patch.setItems(List.of(item));
        return patch;
    }

    private static String schema(boolean duplicate) {
        String nested = "\t\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                + "\t\t\t\t\t<dcsset:name>ГруппировкаДеталиПартий</dcsset:name>\r\n"
                + "\t\t\t\t\t<dcsset:groupItems><dcsset:item xsi:type=\"dcsset:GroupItemAuto\"/></dcsset:groupItems>\r\n"
                + "\t\t\t\t\t<dcsset:filter><dcsset:item xsi:type=\"dcsset:FilterItemComparison\"/></dcsset:filter>\r\n"
                + "\t\t\t\t\t<dcsset:selection><dcsset:item xsi:type=\"dcsset:SelectedItemAuto\"/></dcsset:selection>\r\n"
                + "\t\t\t\t\t<dcsset:conditionalAppearance><dcsset:item/></dcsset:conditionalAppearance>\r\n"
                + "\t\t\t\t\t<dcsset:outputParameters><dcsset:item/></dcsset:outputParameters>\r\n"
                + "\t\t\t\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\"><dcsset:name>ФизическийМакет</dcsset:name></dcsset:item>\r\n"
                + "\t\t\t\t</dcsset:item>\r\n";
        String fields = List.of("ПартияДокумент", "ПартияНомер", "ПартияДата", "ПартияВид", "Монета")
                .stream().map(name -> "\t<field xsi:type=\"DataSetFieldField\"><dataPath>" + name
                        + "</dataPath><field>" + name + "</field></field>\r\n")
                .reduce("", String::concat);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<DataCompositionSchema xmlns=\"http://v8.1c.ru/8.1/data-composition-system/schema\" "
                + "xmlns:dcsset=\"http://v8.1c.ru/8.1/data-composition-system/settings\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\r\n"
                + "\t<dataSet xsi:type=\"DataSetObject\"><name>Движения</name>\r\n" + fields
                + "\t</dataSet>\r\n"
                + "\t<settingsVariant><name>Основной_1</name><settings>\r\n"
                + "\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\">\r\n"
                + "\t\t\t<dcsset:name>Родитель</dcsset:name>\r\n" + nested
                + "\t\t</dcsset:item>\r\n"
                + (duplicate ? nested : "")
                + "\t\t<dcsset:item xsi:type=\"dcsset:StructureItemGroup\"><dcsset:name>Сосед</dcsset:name></dcsset:item>\r\n"
                + "\t</settings></settingsVariant>\r\n</DataCompositionSchema>\r\n";
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }
}
//++agent TASK-174 XG-98

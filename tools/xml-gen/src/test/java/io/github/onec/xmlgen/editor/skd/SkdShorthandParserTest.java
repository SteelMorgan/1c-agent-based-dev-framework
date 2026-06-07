package io.github.onec.xmlgen.editor.skd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkdShorthandParserTest {

    // ===== fields =====

    @Test
    void testParseFieldSimple() {
        var fd = SkdShorthandParser.parseField("Цена: decimal(15,2)");
        assertThat(fd.name).isEqualTo("Цена");
        assertThat(fd.title).isNull();
        assertThat(fd.type).isNotNull();
        assertThat(fd.type).hasSize(1);
        assertThat(fd.type.get(0).xmlType).isEqualTo("xs:decimal");
    }

    @Test
    void testParseFieldWithTitleAndRole() {
        var fd = SkdShorthandParser.parseField("Организация [Орг-ция]: CatalogRef.Организации @dimension");
        assertThat(fd.name).isEqualTo("Организация");
        assertThat(fd.title).isEqualTo("Орг-ция");
        assertThat(fd.role).isEqualTo("dimension");
        assertThat(fd.type.get(0).xmlType).isEqualTo("d5p1:CatalogRef.Организации");
    }

    @Test
    void testParseFieldWithRestrictions() {
        var fd = SkdShorthandParser.parseField("Служебное: string #noFilter #noOrder #noGroup #noField");
        assertThat(fd.restrictions).containsExactly("noFilter", "noOrder", "noGroup", "noField");
    }

    @Test
    void testParseFieldErrorAtColumn() {
        SkdParseException ex = assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseField("Имя !"));
        assertThat(ex.getColumn()).isGreaterThanOrEqualTo(0);
    }

    // ===== field-role =====

    @Test
    void testParseFieldRoleSimple() {
        var d = SkdShorthandParser.parseFieldRole("СуммаОстаток @balance");
        assertThat(d.dataPath).isEqualTo("СуммаОстаток");
        assertThat(d.flags).contains("balance");
        assertThat(d.kv).isEmpty();
    }

    @Test
    void testParseFieldRoleWithKv() {
        var d = SkdShorthandParser.parseFieldRole(
                "СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance");
        assertThat(d.dataPath).isEqualTo("СуммаНач");
        assertThat(d.flags).contains("balance");
        assertThat(d.kv).containsEntry("balanceGroupName", "Сумма");
        assertThat(d.kv).containsEntry("balanceType", "OpeningBalance");
    }

    @Test
    void testParseFieldRoleEmpty() {
        var d = SkdShorthandParser.parseFieldRole("Сумма");
        assertThat(d.dataPath).isEqualTo("Сумма");
        assertThat(d.flags).isEmpty();
        assertThat(d.kv).isEmpty();
    }

    // ===== parameters =====

    @Test
    void testParseParameterSimple() {
        var p = SkdShorthandParser.parseParameter("Период: StandardPeriod");
        assertThat(p.name).isEqualTo("Период");
        assertThat(p.type.get(0).xmlType).isEqualTo("v8:StandardPeriod");
    }

    @Test
    void testParseParameterWithValueAndFlags() {
        var p = SkdShorthandParser.parseParameter(
                "Период [Отчетный период]: StandardPeriod = LastMonth @autoDates");
        assertThat(p.name).isEqualTo("Период");
        assertThat(p.title).isEqualTo("Отчетный период");
        assertThat(p.value).isEqualTo("LastMonth");
        assertThat(p.flags).contains("autoDates");
    }

    @Test
    void testParseParameterAvailableValues() {
        var p = SkdShorthandParser.parseParameter(
                "Округление: EnumRef.Округления = Окр1 "
                        + "availableValue=Перечисление.Округления.Окр1: руб., "
                        + "Перечисление.Округления.Окр1000: тыс.");
        assertThat(p.availableValues).isNotNull();
        assertThat(p.availableValues).hasSize(2);
        assertThat(p.availableValues.get(0).value).isEqualTo("Перечисление.Округления.Окр1");
        assertThat(p.availableValues.get(0).presentation).isEqualTo("руб.");
    }

    @Test
    void testParseAvailableValuesWithQuotes() {
        var p = SkdShorthandParser.parseParameter(
                "Округление: EnumRef.Округления = Окр1 "
                        + "availableValue=Окр1_00: 'руб., коп.', Окр1: руб.");
        assertThat(p.availableValues).hasSize(2);
        assertThat(p.availableValues.get(0).presentation).isEqualTo("руб., коп.");
    }

    @Test
    void testParseModifyParameterOnlyTitle() {
        var p = SkdShorthandParser.parseModifyParameter("ПериодОтчета [Отчетный период]");
        assertThat(p.name).isEqualTo("ПериодОтчета");
        assertThat(p.title).isEqualTo("Отчетный период");
        assertThat(p.kv).isEmpty();
        assertThat(p.flags).isEmpty();
    }

    @Test
    void testParseModifyParameterKv() {
        var p = SkdShorthandParser.parseModifyParameter("ПорядокОкругления use=Always");
        assertThat(p.kv).containsEntry("use", "Always");
    }

    // ===== totals =====

    @Test
    void testParseTotalAutoWrap() {
        var t = SkdShorthandParser.parseTotal("Цена: Среднее");
        assertThat(t.dataPath).isEqualTo("Цена");
        assertThat(t.expression).isEqualTo("Среднее(Цена)");
    }

    @Test
    void testParseTotalAlreadyAggregate() {
        var t = SkdShorthandParser.parseTotal("Сумма: Сумма(Цена * Кол)");
        assertThat(t.expression).isEqualTo("Сумма(Цена * Кол)");
    }

    @Test
    void testParseTotalIdentity() {
        var t = SkdShorthandParser.parseTotal("Маржа: Маржа");
        assertThat(t.expression).isEqualTo("Маржа");
    }

    @Test
    void testParseTotalNoColon() {
        assertThrows(SkdParseException.class, () -> SkdShorthandParser.parseTotal("Цена Среднее"));
    }

    // ===== structure =====

    @Test
    void testParseStructure() {
        var s = SkdShorthandParser.parseStructureSpec("Валюта, НаименованиеБанка @name=ДанныеОтчета");
        assertThat(s.groupName).isEqualTo("ДанныеОтчета");
        assertThat(s.groupItems).containsExactly("Валюта", "НаименованиеБанка");
    }

    @Test
    void testParseStructureDetails() {
        var s = SkdShorthandParser.parseStructureSpec("details @name=ДанныеОтчета");
        assertThat(s.groupItems).containsExactly("details");
    }

    @Test
    void testParseStructureMissingName() {
        assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseStructureSpec("Валюта"));
    }

    // ===== arrow / patch / rename =====

    @Test
    void testParseArrowSimple() {
        var a = SkdShorthandParser.parseArrow("oldName => newName", false);
        assertThat(a.oldText).isEqualTo("oldName");
        assertThat(a.newText).isEqualTo("newName");
        assertThat(a.once).isFalse();
    }

    @Test
    void testParseArrowOnce() {
        var a = SkdShorthandParser.parseArrow("a => b @once", true);
        assertThat(a.once).isTrue();
        assertThat(a.newText).isEqualTo("b");
    }

    @Test
    void testParseArrowMultiline() {
        var a = SkdShorthandParser.parseArrow("ГДЕ\n    Дата =>ГДЕ\n    Период @once", true);
        assertThat(a.once).isTrue();
    }

    // ===== reorder =====

    @Test
    void testParseReorder() {
        List<String> order = SkdShorthandParser.parseReorderParameters("A, B, C");
        assertThat(order).containsExactly("A", "B", "C");
    }

    @Test
    void testParseReorderEmpty() {
        assertThrows(SkdParseException.class,
                () -> SkdShorthandParser.parseReorderParameters("   "));
    }

    // ===== batch =====

    @Test
    void testSplitBatch() {
        List<String> parts = SkdShorthandParser.splitBatch(
                "Цена: decimal(15,2) ;; Количество: decimal(15,3) ;; Сумма: decimal(15,2)");
        assertThat(parts).hasSize(3);
        assertThat(parts.get(0)).isEqualTo("Цена: decimal(15,2)");
    }

    @Test
    void testSplitBatchSingleNoSep() {
        assertThat(SkdShorthandParser.splitBatch("Имя")).containsExactly("Имя");
    }

    // ===== Skill examples (smoke from references/*.md) =====

    @Test
    void testShorthandParser_AllGrammarExamplesFromSkill() {
        // Field examples
        SkdShorthandParser.parseField("Цена: decimal(15,2)");
        SkdShorthandParser.parseField("Организация [Орг-ция]: CatalogRef.Организации @dimension");
        SkdShorthandParser.parseField("Служебное: string #noFilter #noOrder #noGroup #noField");
        SkdShorthandParser.parseField("Цена [Цена USD]: decimal(10,4) @dimension");

        // Field role examples
        SkdShorthandParser.parseFieldRole("Сумма");
        SkdShorthandParser.parseFieldRole("СуммаОстаток @balance");
        SkdShorthandParser.parseFieldRole("СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance");
        SkdShorthandParser.parseFieldRole("Период @period periodNumber=1 periodType=Second");
        SkdShorthandParser.parseFieldRole("Количество @autoOrder orderType=Desc");

        // Parameter examples
        SkdShorthandParser.parseParameter("Период [Отчетный период]: StandardPeriod = LastMonth @autoDates");
        SkdShorthandParser.parseParameter("Организация: CatalogRef.Организации");
        SkdShorthandParser.parseParameter("Период: StandardPeriod = LastMonth @always");
        SkdShorthandParser.parseModifyParameter("ПорядокОкругления use=Always");
        SkdShorthandParser.parseModifyParameter("ПериодОтчета [Отчетный период]");
        SkdShorthandParser.parseModifyParameter("Контрагент @hidden @always");

        // Totals
        SkdShorthandParser.parseTotal("Цена: Среднее");
        SkdShorthandParser.parseTotal("Количество: Сумма");
        SkdShorthandParser.parseTotal("Стоимость: Сумма(Кол * Цена)");
        SkdShorthandParser.parseTotal("Маржа: Маржа");
        SkdShorthandParser.parseTotal("Проверка: ЕстьNULL(СуммаОстаток, 0)");

        // Structure
        SkdShorthandParser.parseStructureSpec("Валюта @name=ДанныеОтчета");
        SkdShorthandParser.parseStructureSpec("Валюта, НаименованиеБанка @name=ДанныеОтчета");
        SkdShorthandParser.parseStructureSpec("details @name=ДанныеОтчета");
        SkdShorthandParser.parseStructureSpec("Организация > Номенклатура @name=Основная");

        // Arrow
        SkdShorthandParser.parseArrow("Период => ПериодОтчета", false);
        SkdShorthandParser.parseArrow("СубконтоДт1) В => СубконтоКт1) В", true);
        SkdShorthandParser.parseArrow("КАК ВТ_СтароеИмя => КАК ВТ_НовоеИмя @once", true);
    }
}

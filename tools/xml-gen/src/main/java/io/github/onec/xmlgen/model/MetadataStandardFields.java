package io.github.onec.xmlgen.model;

//++agent TASK-174 XG-101 2026-07-14

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Стандартные поля объектов метаданных, имена которых нельзя повторять прямыми
 * пользовательскими реквизитами, измерениями и ресурсами того же объекта.
 *
 * <p>Матрица намеренно привязана к типу объекта: например, {@code Дата} является
 * стандартным полем документа, но допустимым пользовательским реквизитом регистра
 * сведений. Вложенные реквизиты табличных частей имеют собственное пространство имён
 * и этой матрицей не проверяются.</p>
 */
public final class MetadataStandardFields {

    public static final String RESERVED_PLATFORM_FIELD_NAME = "RESERVED_PLATFORM_FIELD_NAME";

    private static final Map<String, List<FieldName>> BY_TYPE = new LinkedHashMap<>();

    static {
        put("Catalog",
                field("PredefinedDataName", "ИмяПредопределенныхДанных", "ИмяПредопределённыхДанных"),
                field("Predefined", "Предопределенный", "Предопределённый"),
                field("Ref", "Ссылка"), field("DeletionMark", "ПометкаУдаления"),
                field("IsFolder", "ЭтоГруппа"), field("Owner", "Владелец"),
                field("Parent", "Родитель"), field("Description", "Наименование"),
                field("Code", "Код"));
        put("Document",
                field("Posted", "Проведен", "Проведён"), field("Ref", "Ссылка"),
                field("DeletionMark", "ПометкаУдаления"), field("Date", "Дата"),
                field("Number", "Номер"), reservedField("PostingMode", "РежимПроведения"));
        put("Enum", field("Order", "Порядок"), field("Ref", "Ссылка"));
        put("InformationRegister",
                field("Active", "Активность"), field("LineNumber", "НомерСтроки"),
                field("Recorder", "Регистратор"), field("Period", "Период"));
        put("AccumulationRegister",
                conditionalField("RecordType", Condition.BALANCE, "ВидДвижения"),
                field("Active", "Активность"), field("LineNumber", "НомерСтроки"),
                field("Recorder", "Регистратор"), field("Period", "Период"));
        put("AccountingRegister",
                field("Account", "Счет", "Счёт"),
                conditionalField("RecordType", Condition.NO_CORRESPONDENCE, "ВидДвижения"),
                field("Active", "Активность"), field("LineNumber", "НомерСтроки"),
                field("Recorder", "Регистратор"), field("Period", "Период"),
                field("ExtDimension1", "Субконто1"), field("ExtDimensionType1", "ВидСубконто1"),
                field("ExtDimension2", "Субконто2"), field("ExtDimensionType2", "ВидСубконто2"),
                field("ExtDimension3", "Субконто3"), field("ExtDimensionType3", "ВидСубконто3"));
        put("CalculationRegister",
                field("RegistrationPeriod", "ПериодРегистрации"),
                field("ReversingEntry", "Сторно"), field("Active", "Активность"),
                conditionalField("EndOfBasePeriod", Condition.BASE_PERIOD, "КонецБазовогоПериода"),
                conditionalField("BegOfBasePeriod", Condition.BASE_PERIOD, "НачалоБазовогоПериода"),
                conditionalField("EndOfActionPeriod", Condition.ACTION_PERIOD, "КонецПериодаДействия"),
                conditionalField("BegOfActionPeriod", Condition.ACTION_PERIOD, "НачалоПериодаДействия"),
                conditionalField("ActionPeriod", Condition.ACTION_PERIOD, "ПериодДействия"),
                field("CalculationType", "ВидРасчета", "ВидРасчёта"),
                field("LineNumber", "НомерСтроки"), field("Recorder", "Регистратор"));
        put("ChartOfAccounts",
                field("PredefinedDataName", "ИмяПредопределенныхДанных", "ИмяПредопределённыхДанных"),
                field("Order", "Порядок"), field("OffBalance", "Забалансовый"),
                field("Type", "Вид"), field("Description", "Наименование"),
                field("Code", "Код"), field("Parent", "Родитель"),
                field("Predefined", "Предопределенный", "Предопределённый"),
                field("DeletionMark", "ПометкаУдаления"), field("Ref", "Ссылка"));
        put("ChartOfCharacteristicTypes",
                field("PredefinedDataName", "ИмяПредопределенныхДанных", "ИмяПредопределённыхДанных"),
                field("ValueType", "ТипЗначения"), field("Description", "Наименование"),
                field("Code", "Код"), field("IsFolder", "ЭтоГруппа"),
                field("Parent", "Родитель"),
                field("Predefined", "Предопределенный", "Предопределённый"),
                field("DeletionMark", "ПометкаУдаления"), field("Ref", "Ссылка"));
        put("ChartOfCalculationTypes",
                field("PredefinedDataName", "ИмяПредопределенныхДанных", "ИмяПредопределённыхДанных"),
                field("Predefined", "Предопределенный", "Предопределённый"),
                field("Ref", "Ссылка"), field("DeletionMark", "ПометкаУдаления"),
                field("ActionPeriodIsBasic", "ПериодДействияБазовый"),
                field("Description", "Наименование"), field("Code", "Код"));
        put("BusinessProcess",
                field("Started", "Стартован"), field("HeadTask", "ГоловнаяЗадача"),
                field("Completed", "Завершен", "Завершён"), field("Ref", "Ссылка"),
                field("DeletionMark", "ПометкаУдаления"), field("Date", "Дата"),
                field("Number", "Номер"));
        put("Task",
                field("Executed", "Выполнена"), field("Description", "Наименование"),
                field("RoutePoint", "ТочкаМаршрута"), field("BusinessProcess", "БизнесПроцесс"),
                field("Ref", "Ссылка"), field("DeletionMark", "ПометкаУдаления"),
                field("Date", "Дата"), field("Number", "Номер"));
        put("ExchangePlan",
                field("ThisNode", "ЭтотУзел"), field("ReceivedNo", "НомерПринятого"),
                field("SentNo", "НомерОтправленного"), field("Ref", "Ссылка"),
                field("DeletionMark", "ПометкаУдаления"), field("Description", "Наименование"),
                field("Code", "Код"));
        put("DocumentJournal",
                field("Type", "Тип"), field("Ref", "Ссылка"), field("Date", "Дата"),
                field("Posted", "Проведен", "Проведён"),
                field("DeletionMark", "ПометкаУдаления"), field("Number", "Номер"));
    }

    private MetadataStandardFields() {
    }

    public static List<String> canonicalNames(String objectType) {
        return canonicalNames(objectType, Context.defaults());
    }

    public static List<String> canonicalNames(String objectType, Context context) {
        return BY_TYPE.getOrDefault(objectType, List.of()).stream()
                .filter(FieldName::emitted)
                .filter(field -> field.condition().applies(context))
                .map(FieldName::canonical)
                .toList();
    }

    public static Optional<String> canonicalName(String objectType, String candidate) {
        return canonicalName(objectType, candidate, Context.defaults());
    }

    public static Optional<String> canonicalName(String objectType, String candidate, Context context) {
        if (candidate == null) {
            return Optional.empty();
        }
        String normalized = candidate.toLowerCase(Locale.ROOT);
        return BY_TYPE.getOrDefault(objectType, List.of()).stream()
                .filter(field -> field.condition().applies(context))
                .filter(field -> field.normalizedAliases().contains(normalized))
                .map(FieldName::canonical)
                .findFirst();
    }

    private static void put(String type, FieldName... fields) {
        BY_TYPE.put(type, List.of(fields));
    }

    private static FieldName field(String canonical, String... localizedAliases) {
        List<String> aliases = new ArrayList<>();
        aliases.add(canonical.toLowerCase(Locale.ROOT));
        for (String alias : localizedAliases) {
            aliases.add(alias.toLowerCase(Locale.ROOT));
        }
        return new FieldName(canonical, List.copyOf(aliases), true, Condition.ALWAYS);
    }

    private static FieldName conditionalField(String canonical, Condition condition,
                                              String... localizedAliases) {
        FieldName field = field(canonical, localizedAliases);
        return new FieldName(field.canonical(), field.normalizedAliases(), true, condition);
    }

    private static FieldName reservedField(String canonical, String... localizedAliases) {
        FieldName field = field(canonical, localizedAliases);
        return new FieldName(field.canonical(), field.normalizedAliases(), false, Condition.ALWAYS);
    }

    public record Context(String registerType, boolean correspondence,
                          boolean actionPeriod, boolean basePeriod) {
        public static Context defaults() {
            return new Context("Balance", false, false, false);
        }
    }

    private enum Condition {
        ALWAYS {
            @Override boolean applies(Context context) { return true; }
        },
        BALANCE {
            @Override boolean applies(Context context) {
                return "Balance".equalsIgnoreCase(context.registerType())
                        || "Balances".equalsIgnoreCase(context.registerType());
            }
        },
        NO_CORRESPONDENCE {
            @Override boolean applies(Context context) { return !context.correspondence(); }
        },
        ACTION_PERIOD {
            @Override boolean applies(Context context) { return context.actionPeriod(); }
        },
        BASE_PERIOD {
            @Override boolean applies(Context context) { return context.basePeriod(); }
        };

        abstract boolean applies(Context context);
    }

    private record FieldName(String canonical, List<String> normalizedAliases,
                             boolean emitted, Condition condition) {
    }
}

//--agent TASK-174 XG-101

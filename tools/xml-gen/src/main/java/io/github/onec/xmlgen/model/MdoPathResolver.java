package io.github.onec.xmlgen.model;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Утилитный резолвер: маппинг "имя типа метаданных" → имя каталога в конфигурации/расширении,
 * плюс парсинг module-paths вида {@code Catalog.X.ObjectModule} или {@code Catalog.X.Form.Y}
 * в относительный путь к BSL-файлу.
 *
 * <p>Используется в {@code ExtensionEditor.borrow} (через TYPE_TO_DIR константу,
 * см. там же) и в {@code extension patch-method} для маппинга {@code --module} в файл.</p>
 *
 * <p>Парсинг — текстовый, без подключения mdclasses. Русские синонимы поддерживаются.</p>
 */
public final class MdoPathResolver {

    private MdoPathResolver() {}

    /** Русские → английские синонимы типов метаданных. */
    public static final Map<String, String> SYNONYM_MAP = Map.ofEntries(
            Map.entry("Справочник", "Catalog"), Map.entry("Документ", "Document"),
            Map.entry("Перечисление", "Enum"), Map.entry("ОбщийМодуль", "CommonModule"),
            Map.entry("ОбщаяКартинка", "CommonPicture"), Map.entry("ОбщаяКоманда", "CommonCommand"),
            Map.entry("ОбщийМакет", "CommonTemplate"), Map.entry("ПланОбмена", "ExchangePlan"),
            Map.entry("Отчет", "Report"), Map.entry("Отчёт", "Report"),
            Map.entry("Обработка", "DataProcessor"),
            Map.entry("РегистрСведений", "InformationRegister"),
            Map.entry("РегистрНакопления", "AccumulationRegister"),
            Map.entry("ПланВидовХарактеристик", "ChartOfCharacteristicTypes"),
            Map.entry("ПланСчетов", "ChartOfAccounts"),
            Map.entry("РегистрБухгалтерии", "AccountingRegister"),
            Map.entry("ПланВидовРасчета", "ChartOfCalculationTypes"),
            Map.entry("РегистрРасчета", "CalculationRegister"),
            Map.entry("БизнесПроцесс", "BusinessProcess"), Map.entry("Задача", "Task"),
            Map.entry("Подсистема", "Subsystem"), Map.entry("Роль", "Role"),
            Map.entry("Константа", "Constant"),
            Map.entry("ФункциональнаяОпция", "FunctionalOption"),
            Map.entry("ОпределяемыйТип", "DefinedType"),
            Map.entry("ОбщаяФорма", "CommonForm"),
            Map.entry("ЖурналДокументов", "DocumentJournal"),
            Map.entry("ПараметрСеанса", "SessionParameter"),
            Map.entry("ГруппаКоманд", "CommandGroup"),
            Map.entry("ПодпискаНаСобытие", "EventSubscription"),
            Map.entry("РегламентноеЗадание", "ScheduledJob"),
            Map.entry("ОбщийРеквизит", "CommonAttribute"),
            Map.entry("ПакетXDTO", "XDTOPackage"),
            Map.entry("HTTPСервис", "HTTPService"),
            Map.entry("СервисИнтеграции", "IntegrationService")
    );

    /** Имя типа → имя каталога в src. */
    public static final Map<String, String> TYPE_TO_DIR = Map.ofEntries(
            Map.entry("Language", "Languages"), Map.entry("Subsystem", "Subsystems"),
            Map.entry("StyleItem", "StyleItems"), Map.entry("Style", "Styles"),
            Map.entry("CommonPicture", "CommonPictures"), Map.entry("SessionParameter", "SessionParameters"),
            Map.entry("Role", "Roles"), Map.entry("CommonTemplate", "CommonTemplates"),
            Map.entry("FilterCriterion", "FilterCriteria"), Map.entry("CommonModule", "CommonModules"),
            Map.entry("CommonAttribute", "CommonAttributes"), Map.entry("ExchangePlan", "ExchangePlans"),
            Map.entry("XDTOPackage", "XDTOPackages"), Map.entry("WebService", "WebServices"),
            Map.entry("HTTPService", "HTTPServices"), Map.entry("WebSocketClient", "WebSocketClients"),
            Map.entry("WSReference", "WSReferences"),
            Map.entry("EventSubscription", "EventSubscriptions"), Map.entry("ScheduledJob", "ScheduledJobs"),
            Map.entry("SettingsStorage", "SettingsStorages"), Map.entry("FunctionalOption", "FunctionalOptions"),
            Map.entry("FunctionalOptionsParameter", "FunctionalOptionsParameters"),
            Map.entry("DefinedType", "DefinedTypes"), Map.entry("CommonCommand", "CommonCommands"),
            Map.entry("CommandGroup", "CommandGroups"), Map.entry("Constant", "Constants"),
            Map.entry("CommonForm", "CommonForms"), Map.entry("Catalog", "Catalogs"),
            Map.entry("Document", "Documents"), Map.entry("DocumentNumerator", "DocumentNumerators"),
            Map.entry("Sequence", "Sequences"), Map.entry("DocumentJournal", "DocumentJournals"),
            Map.entry("Enum", "Enums"), Map.entry("Report", "Reports"),
            Map.entry("DataProcessor", "DataProcessors"), Map.entry("InformationRegister", "InformationRegisters"),
            Map.entry("AccumulationRegister", "AccumulationRegisters"),
            Map.entry("ChartOfCharacteristicTypes", "ChartsOfCharacteristicTypes"),
            Map.entry("ChartOfAccounts", "ChartsOfAccounts"), Map.entry("AccountingRegister", "AccountingRegisters"),
            Map.entry("ChartOfCalculationTypes", "ChartsOfCalculationTypes"),
            Map.entry("CalculationRegister", "CalculationRegisters"),
            Map.entry("BusinessProcess", "BusinessProcesses"), Map.entry("Task", "Tasks"),
            Map.entry("IntegrationService", "IntegrationServices")
    );

    /** Канонизировать имя типа: «Справочник» → «Catalog»; «Catalog» → «Catalog». */
    public static String canonicalType(String typeName) {
        if (typeName == null) return null;
        return SYNONYM_MAP.getOrDefault(typeName, typeName);
    }

    /** Имя каталога для типа или {@code null}, если тип неизвестен. */
    public static String dirForType(String typeName) {
        String canon = canonicalType(typeName);
        return canon == null ? null : TYPE_TO_DIR.get(canon);
    }

    /** Описание разобранного модуля. */
    public static final class ParsedModule {
        public final String typeName;     // canonical, e.g. "Catalog"
        public final String objectName;   // e.g. "Контрагенты"; null для CommonModule shorthand? нет, всегда есть
        public final String moduleKind;   // "ObjectModule" | "ManagerModule" | "RecordSetModule" | "Module" (common) | "Form"
        public final String formName;     // если moduleKind == "Form"
        public final List<String> segments;

        public ParsedModule(String typeName, String objectName, String moduleKind,
                            String formName, List<String> segments) {
            this.typeName = typeName;
            this.objectName = objectName;
            this.moduleKind = moduleKind;
            this.formName = formName;
            this.segments = segments;
        }
    }

    /**
     * Парсинг {@code --module}-строки.
     * Поддерживаемые форматы:
     * <ul>
     *   <li>{@code Catalog.X.ObjectModule}</li>
     *   <li>{@code Catalog.X.ManagerModule}</li>
     *   <li>{@code Catalog.X.Form.Y} (модуль формы)</li>
     *   <li>{@code CommonModule.X}</li>
     *   <li>{@code Document.X.ObjectModule}, {@code Document.X.Form.Y}</li>
     *   <li>{@code InformationRegister.X.RecordSetModule}</li>
     *   <li>{@code Report.X.ObjectModule}, {@code Report.X.Form.Y}</li>
     *   <li>{@code DataProcessor.X.ObjectModule}, {@code DataProcessor.X.Form.Y}</li>
     * </ul>
     */
    public static ParsedModule parseModule(String moduleSpec) {
        if (moduleSpec == null || moduleSpec.isBlank()) {
            throw new IllegalArgumentException("--module is empty. Example: Catalog.Контрагенты.ObjectModule");
        }
        List<String> parts = List.of(moduleSpec.split("\\."));
        if (parts.size() < 2) {
            throw new IllegalArgumentException(
                    "Invalid --module '" + moduleSpec
                            + "'. Examples: Catalog.X.ObjectModule, Catalog.X.Form.Y, CommonModule.X");
        }
        String typeRaw = parts.get(0);
        String typeName = canonicalType(typeRaw);
        if (!TYPE_TO_DIR.containsKey(typeName)) {
            throw new IllegalArgumentException(
                    "Unknown metadata type '" + typeRaw + "' in --module '" + moduleSpec
                            + "'. Examples of valid: Catalog, Document, CommonModule, InformationRegister.");
        }
        // CommonModule.X  → ровно 2 части
        if ("CommonModule".equals(typeName)) {
            if (parts.size() != 2) {
                throw new IllegalArgumentException(
                        "Invalid --module '" + moduleSpec + "'. CommonModule format: CommonModule.<Name>");
            }
            return new ParsedModule(typeName, parts.get(1), "Module", null, parts);
        }
        // Forms: ...Form.Y
        if (parts.size() == 4 && "Form".equalsIgnoreCase(parts.get(2))) {
            return new ParsedModule(typeName, parts.get(1), "Form", parts.get(3), parts);
        }
        // Объектные модули: <Type>.<Name>.<Kind>
        if (parts.size() == 3) {
            String kind = parts.get(2);
            if (!isKnownObjectModuleKind(kind)) {
                throw new IllegalArgumentException(
                        "Unknown module kind '" + kind + "' in --module '" + moduleSpec
                                + "'. Known: ObjectModule, ManagerModule, RecordSetModule.");
            }
            return new ParsedModule(typeName, parts.get(1), kind, null, parts);
        }
        throw new IllegalArgumentException(
                "Invalid --module '" + moduleSpec
                        + "'. Examples: Catalog.X.ObjectModule, Catalog.X.Form.Y, CommonModule.X");
    }

    private static boolean isKnownObjectModuleKind(String kind) {
        return "ObjectModule".equals(kind)
                || "ManagerModule".equals(kind)
                || "RecordSetModule".equals(kind)
                || "ValueManagerModule".equals(kind)
                || "CommandModule".equals(kind);
    }

    /** Разрешить относительный путь к BSL-файлу модуля. */
    public static Path resolveBslPath(Path root, ParsedModule m) {
        String dir = TYPE_TO_DIR.get(m.typeName);
        if (dir == null) {
            throw new IllegalArgumentException("No dir mapping for type " + m.typeName);
        }
        if ("CommonModule".equals(m.typeName)) {
            // CommonModules/<Name>/Ext/Module.bsl
            return root.resolve(dir).resolve(m.objectName).resolve("Ext").resolve("Module.bsl");
        }
        if ("Form".equals(m.moduleKind)) {
            // <Dir>/<Obj>/Forms/<Form>/Ext/Form/Module.bsl
            return root.resolve(dir).resolve(m.objectName)
                    .resolve("Forms").resolve(m.formName)
                    .resolve("Ext").resolve("Form").resolve("Module.bsl");
        }
        // ObjectModule / ManagerModule / RecordSetModule
        return root.resolve(dir).resolve(m.objectName).resolve("Ext").resolve(m.moduleKind + ".bsl");
    }

    /** Проверка: является ли модуль модулем формы. */
    public static boolean isFormModule(ParsedModule m) {
        return "Form".equals(m.moduleKind);
    }

    /** Имя «объектного контейнера» (для проверки заимствования): расширение требует <Dir>/<Obj>.xml. */
    public static Path objectXmlPath(Path root, ParsedModule m) {
        String dir = TYPE_TO_DIR.get(m.typeName);
        if (dir == null) return null;
        if ("CommonModule".equals(m.typeName)) {
            return root.resolve(dir).resolve(m.objectName + ".xml");
        }
        return root.resolve(dir).resolve(m.objectName + ".xml");
    }
}

package io.github.onec.xmlgen.validator;

import com.github._1c_syntax.bsl.mdo.support.RoleRight;
import com.github._1c_syntax.bsl.types.MDOType;

import java.util.*;

/**
 * Валидатор для XML прав роли (Rights.xml / Rights.rights).
 * <p>
 * Level 1 (Structure): ROLE-001..005
 * Level 2 (Semantic):  ROLE-101..107
 */
public class RoleValidator implements XmlValidator {

    private static final String NS_ROLES = "http://v8.1c.ru/8.2/roles";

    private static final Set<String> CATALOG_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeleteMarked", "InteractiveDeletePredefinedData",
            "InteractiveSetDeletionMarkPredefinedData", "InteractiveClearDeletionMarkPredefinedData",
            "InteractiveDeleteMarkedPredefinedData", "ReadDataHistory", "ViewDataHistory",
            "UpdateDataHistory", "UpdateDataHistoryOfMissingData", "ReadDataHistoryOfMissingData",
            "UpdateDataHistorySettings", "UpdateDataHistoryVersionComment",
            "EditDataHistoryVersionComment", "SwitchToDataHistoryVersion");

    private static final Set<String> DOCUMENT_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeleteMarked", "ReadDataHistory", "ViewDataHistory",
            "UpdateDataHistory", "UpdateDataHistoryOfMissingData", "ReadDataHistoryOfMissingData",
            "UpdateDataHistorySettings", "UpdateDataHistoryVersionComment",
            "EditDataHistoryVersionComment", "SwitchToDataHistoryVersion",
            "Posting", "UndoPosting", "InteractivePosting", "InteractivePostingRegular",
            "InteractiveUndoPosting", "InteractiveChangeOfPosted");

    private static final Set<String> INFORMATION_REGISTER_RIGHTS = rights(
            "Read", "Update", "View", "Edit", "TotalsControl", "ReadDataHistory",
            "ViewDataHistory", "UpdateDataHistory", "UpdateDataHistoryOfMissingData",
            "ReadDataHistoryOfMissingData", "UpdateDataHistorySettings",
            "UpdateDataHistoryVersionComment", "EditDataHistoryVersionComment",
            "SwitchToDataHistoryVersion");

    private static final Set<String> REGISTER_RIGHTS = rights(
            "Read", "Update", "View", "Edit", "TotalsControl");

    private static final Set<String> CONSTANT_RIGHTS = rights(
            "Read", "Update", "View", "Edit", "ReadDataHistory", "ViewDataHistory",
            "UpdateDataHistory", "UpdateDataHistorySettings", "UpdateDataHistoryVersionComment",
            "EditDataHistoryVersionComment", "SwitchToDataHistoryVersion");

    private static final Set<String> CHART_OF_ACCOUNTS_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeletePredefinedData",
            "InteractiveSetDeletionMarkPredefinedData", "InteractiveClearDeletionMarkPredefinedData",
            "InteractiveDeleteMarkedPredefinedData", "ReadDataHistory", "ReadDataHistoryOfMissingData",
            "UpdateDataHistory", "UpdateDataHistoryOfMissingData", "UpdateDataHistorySettings",
            "UpdateDataHistoryVersionComment");

    private static final Set<String> CHART_OF_CHARACTERISTIC_TYPES_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeletePredefinedData",
            "InteractiveSetDeletionMarkPredefinedData", "InteractiveClearDeletionMarkPredefinedData",
            "InteractiveDeleteMarkedPredefinedData", "ReadDataHistory", "ReadDataHistoryOfMissingData",
            "UpdateDataHistory", "UpdateDataHistoryOfMissingData", "UpdateDataHistorySettings",
            "UpdateDataHistoryVersionComment", "InteractiveDeleteMarked",
            "EditDataHistoryVersionComment", "SwitchToDataHistoryVersion", "ViewDataHistory");

    private static final Set<String> CHART_OF_CALCULATION_TYPES_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeletePredefinedData",
            "InteractiveSetDeletionMarkPredefinedData", "InteractiveClearDeletionMarkPredefinedData",
            "InteractiveDeleteMarkedPredefinedData");

    private static final Set<String> EXCHANGE_PLAN_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "InteractiveInsert", "InteractiveSetDeletionMark", "InteractiveClearDeletionMark",
            "InteractiveDelete", "InteractiveDeleteMarked", "ReadDataHistory", "ViewDataHistory",
            "UpdateDataHistory", "ReadDataHistoryOfMissingData", "UpdateDataHistoryOfMissingData",
            "UpdateDataHistorySettings", "UpdateDataHistoryVersionComment",
            "EditDataHistoryVersionComment", "SwitchToDataHistoryVersion");

    private static final Set<String> BUSINESS_PROCESS_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "Start", "InteractiveInsert", "InteractiveSetDeletionMark",
            "InteractiveClearDeletionMark", "InteractiveDelete", "InteractiveActivate",
            "InteractiveStart");

    private static final Set<String> TASK_RIGHTS = rights(
            "Read", "Insert", "Update", "Delete", "View", "Edit", "InputByString",
            "Execute", "InteractiveInsert", "InteractiveSetDeletionMark",
            "InteractiveClearDeletionMark", "InteractiveDelete", "InteractiveActivate",
            "InteractiveExecute");

    private static final Set<String> CONFIGURATION_RIGHTS = rights(
            "Administration", "DataAdministration", "UpdateDataBaseConfiguration",
            "ConfigurationExtensionsAdministration", "ActiveUsers", "EventLog", "ExclusiveMode",
            "ThinClient", "ThickClient", "WebClient", "MobileClient", "ExternalConnection",
            "Automation", "Output", "SaveUserData", "TechnicalSpecialistMode",
            "InteractiveOpenExtDataProcessors", "InteractiveOpenExtReports",
            "AnalyticsSystemClient", "CollaborationSystemInfoBaseRegistration",
            "MainWindowModeNormal", "MainWindowModeWorkplace", "MainWindowModeEmbeddedWorkplace",
            "MainWindowModeFullscreenWorkplace", "MainWindowModeKiosk",
            "AllFunctionsMode", "RemoteDesktopClient", "RemoteDesktopHost",
            "SessionStandardAuthenticationChange", "SessionOSAuthenticationChange",
            "StandardAuthenticationChange", "ExclusiveModeTerminationAtSessionStart");

    private static final Map<String, Set<String>> TOP_LEVEL_TYPE_RIGHTS = Map.ofEntries(
            Map.entry("Configuration", CONFIGURATION_RIGHTS),
            Map.entry("Catalog", CATALOG_RIGHTS),
            Map.entry("Document", DOCUMENT_RIGHTS),
            Map.entry("InformationRegister", INFORMATION_REGISTER_RIGHTS),
            Map.entry("AccumulationRegister", REGISTER_RIGHTS),
            Map.entry("AccountingRegister", REGISTER_RIGHTS),
            Map.entry("CalculationRegister", rights("Read", "View")),
            Map.entry("Constant", CONSTANT_RIGHTS),
            Map.entry("ChartOfAccounts", CHART_OF_ACCOUNTS_RIGHTS),
            Map.entry("ChartOfCharacteristicTypes", CHART_OF_CHARACTERISTIC_TYPES_RIGHTS),
            Map.entry("ChartOfCalculationTypes", CHART_OF_CALCULATION_TYPES_RIGHTS),
            Map.entry("ExchangePlan", EXCHANGE_PLAN_RIGHTS),
            Map.entry("BusinessProcess", BUSINESS_PROCESS_RIGHTS),
            Map.entry("Task", TASK_RIGHTS),
            Map.entry("DataProcessor", rights("Use", "View")),
            Map.entry("Report", rights("Use", "View")),
            Map.entry("CommonForm", rights("View")),
            Map.entry("CommonCommand", rights("View")),
            Map.entry("Subsystem", rights("View")),
            Map.entry("FilterCriterion", rights("View")),
            Map.entry("DocumentJournal", rights("Read", "View")),
            Map.entry("Sequence", rights("Read", "Update")),
            Map.entry("WebService", rights("Use")),
            Map.entry("HTTPService", rights("Use")),
            Map.entry("IntegrationService", rights("Use")),
            Map.entry("SessionParameter", rights("Get", "Set")),
            Map.entry("CommonAttribute", rights("View", "Edit"))
    );

    // Типы объектов БЕЗ прав в ролях (спека: «не фигурируют в Rights.xml»).
    private static final Set<String> TYPES_WITHOUT_RIGHTS = Set.of(
            "Enum", "FunctionalOption", "DefinedType", "CommonModule",
            "CommonPicture", "CommonTemplate", "SettingsStorage", "ExternalDataSource");

    // Вложенные части объекта (предпоследний сегмент имени) → допустимые права.
    // Спека 1c-role-spec §«Полная таблица: вложенные объекты и их права».
    private static final Map<String, Set<String>> NESTED_KIND_RIGHTS = Map.ofEntries(
            Map.entry("Attribute", Set.of("View", "Edit")),
            Map.entry("StandardAttribute", Set.of("View", "Edit")),
            Map.entry("TabularSection", Set.of("View", "Edit")),
            Map.entry("Dimension", Set.of("View", "Edit")),
            Map.entry("Resource", Set.of("View", "Edit")),
            Map.entry("AddressingAttribute", Set.of("View", "Edit")),
            Map.entry("Command", Set.of("View")),
            Map.entry("Operation", Set.of("Use")),
            Map.entry("URLTemplate", Set.of("Use")),
            Map.entry("Method", Set.of("Use"))
    );
    private static Set<String> rights(String... rights) {
        return Set.of(rights);
    }

    @Override
    public String objectType() {
        return "role";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return "Rights".equals(document.getRootElement());
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Level 1: Structure
        validateStructure(document, issues);

        // Level 2: Semantic
        if (level == ValidationLevel.SEMANTIC) {
            validateSemantic(document, issues);
        }

        return issues;
    }

    // ==================== Level 1: Structure ====================

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // ROLE-001: Root <Rights> с namespace
        if (!"Rights".equals(root.getName())) {
            issues.add(ValidationIssue.error("ROLE-001",
                    "Expected root element 'Rights', found '" + root.getName() + "'",
                    root.getLine(), "/"));
            return; // Нет смысла проверять дальше
        }

        String ns = root.getNamespace();
        if (ns == null || !ns.equals(NS_ROLES)) {
            issues.add(ValidationIssue.error("ROLE-001",
                    "Expected namespace '" + NS_ROLES + "', found '" + (ns != null ? ns : "(none)") + "'",
                    root.getLine(), "/Rights"));
        }

        // ROLE-002: Глобальные флаги
        checkGlobalFlag(root, "setForNewObjects", issues);
        checkGlobalFlag(root, "setForAttributesByDefault", issues);
        checkGlobalFlag(root, "independentRightsOfChildObjects", issues);
        checkObjectTemplateOrder(root, issues);

        // Проверяем каждый <object>
        List<XmlNode> objects = root.children("object");
        for (int i = 0; i < objects.size(); i++) {
            XmlNode obj = objects.get(i);
            String objPath = "/Rights/object[" + (i + 1) + "]";

            // ROLE-003: Каждый <object> имеет <name>
            String objName = obj.childText("name");
            if (objName == null || objName.isEmpty()) {
                issues.add(ValidationIssue.error("ROLE-003",
                        "Object element missing <name>",
                        obj.getLine(), objPath));
            }

            // Проверяем каждый <right> внутри <object>
            List<XmlNode> rights = obj.children("right");
            for (int j = 0; j < rights.size(); j++) {
                XmlNode right = rights.get(j);
                String rightPath = objPath + "/right[" + (j + 1) + "]";

                // ROLE-004: Каждый <right> имеет <name> и <value>
                String rightName = right.childText("name");
                String rightValue = right.childText("value");

                if (rightName == null || rightName.isEmpty()) {
                    issues.add(ValidationIssue.error("ROLE-004",
                            "Right element missing <name>",
                            right.getLine(), rightPath));
                }
                if (rightValue == null || rightValue.isEmpty()) {
                    issues.add(ValidationIssue.error("ROLE-004",
                            "Right element missing <value>",
                            right.getLine(), rightPath));
                }

                // ROLE-005: <value> — строго "true" или "false"
                if (rightValue != null && !rightValue.isEmpty()
                        && !"true".equals(rightValue) && !"false".equals(rightValue)) {
                    issues.add(ValidationIssue.error("ROLE-005",
                            "Right value must be 'true' or 'false', found '" + rightValue + "'",
                            right.getLine(), rightPath + "/value"));
                }
            }
        }

        // Проверяем <restrictionTemplate>
        List<XmlNode> templates = root.children("restrictionTemplate");
        for (int i = 0; i < templates.size(); i++) {
            XmlNode tmpl = templates.get(i);
            String tmplPath = "/Rights/restrictionTemplate[" + (i + 1) + "]";

            // ROLE-107: name и condition непустые
            String tmplName = tmpl.childText("name");
            String tmplCondition = tmpl.childText("condition");

            if (tmplName == null || tmplName.isEmpty()) {
                issues.add(ValidationIssue.error("ROLE-107",
                        "Restriction template missing <name>",
                        tmpl.getLine(), tmplPath));
            }
            if (tmplCondition == null || tmplCondition.isEmpty()) {
                issues.add(ValidationIssue.error("ROLE-107",
                        "Restriction template missing <condition>",
                        tmpl.getLine(), tmplPath));
            }
        }
    }

    private void checkGlobalFlag(XmlNode root, String flagName, List<ValidationIssue> issues) {
        if (!root.hasChild(flagName)) {
            issues.add(ValidationIssue.warning("ROLE-002",
                    "Missing global flag <" + flagName + ">",
                    root.getLine(), "/Rights"));
        }
    }

    private void checkObjectTemplateOrder(XmlNode root, List<ValidationIssue> issues) {
        boolean templateSeen = false;
        int objectIndex = 0;
        for (XmlNode child : root.getChildren()) {
            if ("object".equals(child.getName())) {
                objectIndex++;
                if (templateSeen) {
                    issues.add(ValidationIssue.warning("ROLE-108",
                            "<object> must appear before all <restrictionTemplate> elements",
                            child.getLine(), "/Rights/object[" + objectIndex + "]"));
                }
            } else if ("restrictionTemplate".equals(child.getName())) {
                templateSeen = true;
            }
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();
        List<XmlNode> objects = root.children("object");

        for (int i = 0; i < objects.size(); i++) {
            XmlNode obj = objects.get(i);
            String objPath = "/Rights/object[" + (i + 1) + "]";
            String objName = obj.childText("name");
            if (objName == null) continue;

            // ROLE-105: Формат object.name: <MDOType>.<Name>[.<ВложенныйТип>.<ВложенноеИмя>...]
            // TASK-171: модель Николая Широкова — count('.') >= 2 (т.е. >=1 разделитель) считается
            // валидным составным именем. Реальная выгрузка БСП содержит вложенные имена с 2-4 точками:
            //   Catalog.X.Command.Y, Task.X.Command.Y, InformationRegister.X.Command.Y,
            //   CalculationRegister.X.Recalculation.Y, Document.X.TabularSection.Y.Attribute.Z.
            // Прежняя проверка требовала РОВНО одну точку и валила 15/36 заведомо корректных _Демо-ролей
            // ложным ERROR (exit=1), делая validate непригодным для реальных ролей. Вложенность
            // (.Command. / .Attribute. / .Recalculation. и т.п.) — НЕ ошибка формата.
            // ERROR оставляем только для имени без единого разделителя (нет <Тип>.<Имя>),
            // кроме белого списка Configuration./Subsystem.*.
            //**agent TASK-174 [07.06.2026 13:35:00]
            // Прежний вложенный whitelist startsWith("Configuration.")/("Subsystem.") был
            // недостижим (имя с точкой не попадает в ветку !contains(".")) — убран как dead code.
            // Поведение не изменилось: ERROR только для имени без единого разделителя.
            if (!objName.contains(".")) {
                issues.add(ValidationIssue.error("ROLE-105",
                        "Object name must be in format '<MDOType>.<Name>', found '" + objName + "'",
                        obj.getLine(), objPath + "/name"));
            }
            //**agent TASK-174

            // ROLE-102: Тип объекта — известный MDOType
            String typePart = objName.split("\\.")[0];
            Optional<MDOType> mdoType = MDOType.fromValue(typePart);
            if (mdoType.isEmpty() || mdoType.get() == MDOType.UNKNOWN) {
                issues.add(ValidationIssue.warning("ROLE-102",
                        "Unknown MDOType '" + typePart + "' in object name '" + objName + "'",
                        obj.getLine(), objPath + "/name"));
            }

            // Проверяем права
            List<XmlNode> rights = obj.children("right");
            Set<String> seenRights = new HashSet<>();

            for (int j = 0; j < rights.size(); j++) {
                XmlNode right = rights.get(j);
                String rightPath = objPath + "/right[" + (j + 1) + "]";
                String rightName = right.childText("name");
                if (rightName == null) continue;

                // ROLE-101: right.name — известный RoleRight
                if (!isKnownRoleRight(rightName)) {
                    issues.add(ValidationIssue.error("ROLE-101",
                            "Unknown right name '" + rightName + "'",
                            right.getLine(), rightPath + "/name"));
                }

                // ROLE-104: Нет дублей
                if (!seenRights.add(rightName)) {
                    issues.add(ValidationIssue.error("ROLE-104",
                            "Duplicate right '" + rightName + "' for object '" + objName + "'",
                            right.getLine(), rightPath + "/name"));
                }

                // ROLE-103: Право применимо к типу объекта
                if (mdoType.isPresent() && mdoType.get() != MDOType.UNKNOWN) {
                    checkRightApplicability(objName, typePart, rightName,
                            right.getLine(), rightPath, issues);
                }

                // ROLE-106: restrictionByCondition.condition непустой
                XmlNode restriction = right.child("restrictionByCondition");
                if (restriction != null) {
                    String condition = restriction.childText("condition");
                    if (condition == null || condition.isEmpty()) {
                        issues.add(ValidationIssue.warning("ROLE-106",
                                "Restriction by condition has empty <condition>",
                                restriction.getLine(), rightPath + "/restrictionByCondition/condition"));
                    }
                }
            }
        }
    }

    /**
     * ROLE-103: применимость права к типу/части объекта по спеке 1c-role-spec.
     * Все находки — WARNING: Designer часть таких прав молча игнорирует, часть отвергает;
     * ERROR не ставим, чтобы не валить validate на нестандартных, но загружаемых ролях.
     * Порядок проверок:
     * 1) вложенная часть (предпоследний сегмент имени — Attribute/Command/...) → View/Edit | View;
     * 2) тип без прав вовсе (Enum, CommonModule, ...) → любое право подозрительно;
     * 3) верхнеуровневый тип из таблицы спеки → строгий whitelist.
     */
    private void checkRightApplicability(String objName, String typePart, String rightName,
                                         int line, String rightPath,
                                         List<ValidationIssue> issues) {
        // 1) Вложенные части: ...<Kind>.<Имя> — предпоследний сегмент.
        String[] segments = objName.split("\\.");
        if (segments.length >= 4) {
            String kind = segments[segments.length - 2];
            Set<String> allowed = NESTED_KIND_RIGHTS.get(kind);
            if (allowed != null) {
                if (!allowed.contains(rightName)) {
                    issues.add(ValidationIssue.warning("ROLE-103",
                            "Right '" + rightName + "' is not applicable to nested " + kind
                                    + " ('" + objName + "'); allowed: " + String.join(", ", allowed),
                            line, rightPath + "/name"));
                }
                return; // вложенная часть обработана — проверки типа не нужны
            }
            // Неизвестный вид вложенности (Recalculation, Parameter...) —
            // прав по спеке не сверяем, пропускаем без предупреждения.
            return;
        }

        // 2) Типы без прав в ролях.
        if (TYPES_WITHOUT_RIGHTS.contains(typePart)) {
            issues.add(ValidationIssue.warning("ROLE-103",
                    "Object type '" + typePart + "' does not carry rights in roles "
                            + "(per 1c-role-spec), but right '" + rightName + "' is set for '"
                            + objName + "'",
                    line, rightPath + "/name"));
            return;
        }

        // 3) Верхнеуровневые объекты — строгий whitelist по 1c-role-spec.md.
        Set<String> allowed = TOP_LEVEL_TYPE_RIGHTS.get(typePart);
        if (allowed != null) {
            if (!allowed.contains(rightName)) {
                issues.add(ValidationIssue.warning("ROLE-103",
                        "Right '" + rightName + "' is not applicable to " + typePart
                                + " objects; allowed: " + String.join(", ", allowed),
                        line, rightPath + "/name"));
            }
        }
    }

    /**
     * Проверяет, является ли имя права известным RoleRight.
     * Используем enum из mdclasses.
     */
    private boolean isKnownRoleRight(String name) {
        try {
            // RoleRight.fullName().getEn() возвращает XML-имя
            for (RoleRight rr : RoleRight.values()) {
                if (rr.fullName().getEn().equals(name)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // Fallback: если enum не загрузился, считаем известным
            return true;
        }
    }
}

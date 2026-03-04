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

    // Права, применимые только к Document
    private static final Set<String> DOCUMENT_ONLY_RIGHTS = Set.of(
            "Posting", "UndoPosting",
            "InteractivePosting", "InteractivePostingRegular",
            "InteractiveUndoPosting", "InteractiveChangeOfPosted"
    );

    // Права, применимые только к objects с подчинёнными (Catalog, ChartOfCharacteristicTypes и т.д.)
    private static final Set<String> SUBORDINATE_ONLY_RIGHTS = Set.of(
            "InputByString", "InteractiveDeleteMarked",
            "InteractiveClearDeletionMark", "InteractiveSetDeletionMark",
            "InteractiveDeletePredefinedData", "InteractiveSetDeletionMarkPredefinedData",
            "InteractiveClearDeletionMarkPredefinedData"
    );

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

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();
        List<XmlNode> objects = root.children("object");

        for (int i = 0; i < objects.size(); i++) {
            XmlNode obj = objects.get(i);
            String objPath = "/Rights/object[" + (i + 1) + "]";
            String objName = obj.childText("name");
            if (objName == null) continue;

            // ROLE-105: Формат object.name: <MDOType>.<Name>
            if (!objName.contains(".") || objName.indexOf('.') != objName.lastIndexOf('.')) {
                // Допускаем формат без точки для Configuration и Subsystem.*
                if (!objName.startsWith("Configuration.") && !objName.startsWith("Subsystem.")) {
                    issues.add(ValidationIssue.error("ROLE-105",
                            "Object name must be in format '<MDOType>.<Name>', found '" + objName + "'",
                            obj.getLine(), objPath + "/name"));
                }
            }

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
                    MDOType type = mdoType.get();

                    // Posting-права только для Document
                    if (DOCUMENT_ONLY_RIGHTS.contains(rightName) && type != MDOType.DOCUMENT) {
                        issues.add(ValidationIssue.warning("ROLE-103",
                                "Right '" + rightName + "' is only applicable to Document objects, " +
                                        "but object type is " + typePart,
                                right.getLine(), rightPath + "/name"));
                    }
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

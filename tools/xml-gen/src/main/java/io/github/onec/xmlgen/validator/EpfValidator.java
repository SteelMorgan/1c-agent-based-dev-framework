package io.github.onec.xmlgen.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Валидатор для корневого XML внешней обработки (ExternalDataProcessor)
 * и внешнего отчёта (ExternalReport).
 * <p>
 * Level 1 (Structure): EPF-001..006
 * Level 2 (Semantic):  EPF-007..010
 *
 * <p>Дополнительно (EPF-007..010, parity с Python epf-validate):
 * <ul>
 *   <li>EPF-007 — дубли child-имён в одном parent.</li>
 *   <li>EPF-008 — identifier pattern (латиница+кириллица+цифры+underscore, не начинается с цифры).</li>
 *   <li>EPF-009 — Form.xml существует для declared Form.</li>
 *   <li>EPF-010 — uuid / ClassId имеет формат GUID (8-4-4-4-12 hex).</li>
 * </ul>
 */
public class EpfValidator implements XmlValidator {

    private static final String EPF_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";
    private static final String ERF_CLASS_ID = "e41aff26-25cf-4bb6-b6c1-3f478a75f374";

    private static final Pattern GUID_RE = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Идентификатор 1С: начинается с буквы (рус/англ) или {@code _}, далее буквы/цифры/{@code _}. */
    private static final Pattern IDENT_RE = Pattern.compile(
            "^[A-Za-z\u0410-\u042f\u0430-\u044f\u0401\u0451_][A-Za-z0-9\u0410-\u042f\u0430-\u044f\u0401\u0451_]*$");

    @Override
    public String objectType() {
        return "epf";
    }

    @Override
    public boolean supports(XmlDocument document) {
        String root = document.getRootElement();
        if ("ExternalDataProcessor".equals(root) || "ExternalReport".equals(root)) {
            return true;
        }
        if ("MetaDataObject".equals(root)) {
            XmlNode mdo = document.getRoot();
            return mdo.child("ExternalDataProcessor") != null
                    || mdo.child("ExternalReport") != null;
        }
        return false;
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateStructure(document, issues);
        if (level == ValidationLevel.SEMANTIC) {
            validateSemantic(document, issues);
        }
        return issues;
    }

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Навигация: MetaDataObject → ExternalDataProcessor/ExternalReport
        XmlNode epfNode;
        boolean isReport;
        if ("MetaDataObject".equals(root.getName())) {
            epfNode = root.child("ExternalDataProcessor");
            isReport = false;
            if (epfNode == null) {
                epfNode = root.child("ExternalReport");
                isReport = true;
            }
            if (epfNode == null) {
                issues.add(ValidationIssue.error("EPF-001",
                        "MetaDataObject missing <ExternalDataProcessor> or <ExternalReport> child",
                        root.getLine(), "/MetaDataObject"));
                return;
            }
        } else if ("ExternalDataProcessor".equals(root.getName())) {
            epfNode = root;
            isReport = false;
        } else if ("ExternalReport".equals(root.getName())) {
            epfNode = root;
            isReport = true;
        } else {
            issues.add(ValidationIssue.error("EPF-001",
                    "Expected root element 'MetaDataObject', 'ExternalDataProcessor' or 'ExternalReport', found '"
                            + root.getName() + "'",
                    root.getLine(), "/"));
            return;
        }

        String elementName = isReport ? "ExternalReport" : "ExternalDataProcessor";
        String expectedClassId = isReport ? ERF_CLASS_ID : EPF_CLASS_ID;

        // EPF-001: uuid присутствует
        String uuid = epfNode.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            issues.add(ValidationIssue.error("EPF-001",
                    elementName + " missing uuid attribute",
                    epfNode.getLine(), "/" + elementName));
        }

        // Навигация: Properties → Name / InternalInfo → ClassId
        XmlNode properties = epfNode.child("Properties");

        if (properties != null) {
            // EPF-002: Name непустой
            String name = properties.childText("Name");
            if (name == null || name.isEmpty()) {
                issues.add(ValidationIssue.error("EPF-002",
                        "Missing or empty <Name> in Properties",
                        properties.getLine(), "/" + elementName + "/Properties/Name"));
            }

            // EPF-003: ClassId
            XmlNode internalInfo = properties.child("InternalInfo");
            if (internalInfo != null) {
                String classId = findClassId(internalInfo);
                if (classId != null && !classId.isEmpty()) {
                    if (!expectedClassId.equals(classId)) {
                        issues.add(ValidationIssue.error("EPF-003",
                                "Expected ClassId '" + expectedClassId + "', found '" + classId + "'",
                                internalInfo.getLine(), "/" + elementName + "/Properties/InternalInfo/ClassId"));
                    }
                }
            }
        } else {
            issues.add(ValidationIssue.error("EPF-002",
                    "Missing <Properties> element",
                    epfNode.getLine(), "/" + elementName));
        }

        // EPF-004: ChildObjects присутствует
        XmlNode childObjects = epfNode.child("ChildObjects");
        if (childObjects == null) {
            issues.add(ValidationIssue.error("EPF-004",
                    "Missing <ChildObjects> element",
                    epfNode.getLine(), "/" + elementName));
            return;
        }

        // EPF-005: Forms перед Templates в ChildObjects
        boolean foundTemplate = false;
        boolean formAfterTemplate = false;
        for (XmlNode child : childObjects.getChildren()) {
            if ("Template".equals(child.getName())) {
                foundTemplate = true;
            }
            if ("Form".equals(child.getName()) && foundTemplate) {
                formAfterTemplate = true;
            }
        }
        if (formAfterTemplate) {
            issues.add(ValidationIssue.warning("EPF-005",
                    "Forms should be declared before Templates in ChildObjects",
                    childObjects.getLine(), "/" + elementName + "/ChildObjects"));
        }

        // EPF-006: Файлы из ChildObjects существуют (если документ — файл на диске)
        Path docDir = document.getFile() != null ? document.getFile().getParent() : null;
        if (docDir != null) {
            validateChildFiles(childObjects, docDir, elementName, issues);
        }
    }

    private String findClassId(XmlNode internalInfo) {
        // Ищем xr:ClassId или ClassId
        for (XmlNode child : internalInfo.getChildren()) {
            if (child.getName().equals("ClassId") || child.getName().endsWith(":ClassId")) {
                return child.getText();
            }
        }
        return null;
    }

    private void validateChildFiles(XmlNode childObjects, Path baseDir, String elementName, List<ValidationIssue> issues) {
        for (XmlNode child : childObjects.getChildren()) {
            String childName = child.getName();
            String objName = child.getText();
            if (objName == null || objName.isEmpty()) continue;

            // Проверяем наличие каталога дочернего объекта
            Path childDir;
            if ("Form".equals(childName)) {
                childDir = baseDir.resolve("Forms").resolve(objName);
            } else if ("Template".equals(childName)) {
                childDir = baseDir.resolve("Templates").resolve(objName);
            } else {
                continue;
            }

            if (!Files.exists(childDir)) {
                issues.add(ValidationIssue.error("EPF-006",
                        childName + " '" + objName + "' directory not found: " + childDir,
                        child.getLine(), "/" + elementName + "/ChildObjects/" + childName));
            }

            // EPF-009: для Form — также проверим наличие Form.xml внутри каталога
            if ("Form".equals(childName) && Files.exists(childDir)) {
                Path formXml = childDir.resolve("Ext").resolve("Form").resolve("Form.xml");
                if (!Files.exists(formXml)) {
                    issues.add(ValidationIssue.error("EPF-009",
                            "Form '" + objName + "' declared but Form.xml not found: " + formXml,
                            child.getLine(), "/" + elementName + "/ChildObjects/Form"));
                }
            }
        }
    }

    // ==================== Level 2: Semantic ====================

    private void validateSemantic(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();
        XmlNode epfNode;
        if ("MetaDataObject".equals(root.getName())) {
            epfNode = root.child("ExternalDataProcessor");
            if (epfNode == null) epfNode = root.child("ExternalReport");
            if (epfNode == null) return;
        } else {
            epfNode = root;
        }
        String elementName = epfNode.getName();

        // EPF-010: uuid — GUID-формат
        String uuid = epfNode.attr("uuid");
        if (uuid != null && !uuid.isEmpty() && !GUID_RE.matcher(uuid).matches()) {
            issues.add(ValidationIssue.error("EPF-010",
                    elementName + " uuid '" + uuid + "' is not a valid GUID (expected 8-4-4-4-12 hex)",
                    epfNode.getLine(), "/" + elementName));
        }

        // EPF-010: ClassId — тоже GUID
        XmlNode props = epfNode.child("Properties");
        if (props != null) {
            XmlNode internalInfo = props.child("InternalInfo");
            if (internalInfo != null) {
                String classId = findClassId(internalInfo);
                if (classId != null && !classId.isEmpty() && !GUID_RE.matcher(classId).matches()) {
                    issues.add(ValidationIssue.error("EPF-010",
                            "ClassId '" + classId + "' is not a valid GUID",
                            internalInfo.getLine(),
                            "/" + elementName + "/Properties/InternalInfo/ClassId"));
                }
            }
        }

        // EPF-007, EPF-008: child name uniqueness + identifier pattern
        XmlNode childObjects = epfNode.child("ChildObjects");
        if (childObjects != null) {
            Map<String, Set<String>> seenByKind = new HashMap<>();
            for (XmlNode child : childObjects.getChildren()) {
                String kind = child.getName();
                String objName = child.getText();
                if (objName == null || objName.isEmpty()) continue;

                // EPF-008: identifier pattern
                if (!IDENT_RE.matcher(objName).matches()) {
                    issues.add(ValidationIssue.error("EPF-008",
                            kind + " '" + objName + "' is not a valid 1C identifier",
                            child.getLine(),
                            "/" + elementName + "/ChildObjects/" + kind));
                }

                // EPF-007: duplicates per kind
                Set<String> seen = seenByKind.computeIfAbsent(kind, k -> new HashSet<>());
                if (!seen.add(objName)) {
                    issues.add(ValidationIssue.error("EPF-007",
                            "Duplicate " + kind + " name '" + objName + "' in ChildObjects",
                            child.getLine(),
                            "/" + elementName + "/ChildObjects/" + kind));
                }
            }
        }

        // EPF-008: Name (Properties) — тоже identifier
        if (props != null) {
            String name = props.childText("Name");
            if (name != null && !name.isEmpty() && !IDENT_RE.matcher(name).matches()) {
                issues.add(ValidationIssue.error("EPF-008",
                        "Object Name '" + name + "' is not a valid 1C identifier",
                        props.getLine(), "/" + elementName + "/Properties/Name"));
            }
        }
    }
}

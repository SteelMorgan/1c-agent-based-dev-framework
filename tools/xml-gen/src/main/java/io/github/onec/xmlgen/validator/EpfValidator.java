package io.github.onec.xmlgen.validator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Валидатор для корневого XML внешней обработки (ExternalDataProcessor).
 * <p>
 * Level 1 (Structure): EPF-001..006
 */
public class EpfValidator implements XmlValidator {

    private static final String NS_MDO = "http://v8.1c.ru/8.3/MDClasses";
    private static final String EPF_CLASS_ID = "c3831ec8-d8d5-4f93-8a22-f9bfae07327f";

    @Override
    public String objectType() {
        return "epf";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return "MetaDataObject".equals(document.getRootElement())
                || "ExternalDataProcessor".equals(document.getRootElement());
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        validateStructure(document, issues);
        return issues;
    }

    private void validateStructure(XmlDocument document, List<ValidationIssue> issues) {
        XmlNode root = document.getRoot();

        // Навигация: MetaDataObject → ExternalDataProcessor или напрямую ExternalDataProcessor
        XmlNode epfNode;
        if ("MetaDataObject".equals(root.getName())) {
            epfNode = root.child("ExternalDataProcessor");
            if (epfNode == null) {
                issues.add(ValidationIssue.error("EPF-001",
                        "MetaDataObject missing <ExternalDataProcessor> child",
                        root.getLine(), "/MetaDataObject"));
                return;
            }
        } else if ("ExternalDataProcessor".equals(root.getName())) {
            epfNode = root;
        } else {
            issues.add(ValidationIssue.error("EPF-001",
                    "Expected root element 'MetaDataObject' or 'ExternalDataProcessor', found '"
                            + root.getName() + "'",
                    root.getLine(), "/"));
            return;
        }

        // EPF-001: uuid присутствует
        String uuid = epfNode.attr("uuid");
        if (uuid == null || uuid.isEmpty()) {
            issues.add(ValidationIssue.error("EPF-001",
                    "ExternalDataProcessor missing uuid attribute",
                    epfNode.getLine(), "/ExternalDataProcessor"));
        }

        // Навигация: Properties → Name / InternalInfo → ClassId
        XmlNode properties = epfNode.child("Properties");

        if (properties != null) {
            // EPF-002: Name непустой
            String name = properties.childText("Name");
            if (name == null || name.isEmpty()) {
                issues.add(ValidationIssue.error("EPF-002",
                        "Missing or empty <Name> in Properties",
                        properties.getLine(), "/ExternalDataProcessor/Properties/Name"));
            }

            // EPF-003: ClassId
            XmlNode internalInfo = properties.child("InternalInfo");
            if (internalInfo != null) {
                // ClassId может быть в xr:ClassId или просто ClassId
                String classId = findClassId(internalInfo);
                if (classId != null && !classId.isEmpty()) {
                    if (!EPF_CLASS_ID.equals(classId)) {
                        issues.add(ValidationIssue.error("EPF-003",
                                "Expected ClassId '" + EPF_CLASS_ID + "', found '" + classId + "'",
                                internalInfo.getLine(), "/ExternalDataProcessor/Properties/InternalInfo/ClassId"));
                    }
                }
            }
        } else {
            issues.add(ValidationIssue.error("EPF-002",
                    "Missing <Properties> element",
                    epfNode.getLine(), "/ExternalDataProcessor"));
        }

        // EPF-004: ChildObjects присутствует
        XmlNode childObjects = epfNode.child("ChildObjects");
        if (childObjects == null) {
            issues.add(ValidationIssue.error("EPF-004",
                    "Missing <ChildObjects> element",
                    epfNode.getLine(), "/ExternalDataProcessor"));
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
                    childObjects.getLine(), "/ExternalDataProcessor/ChildObjects"));
        }

        // EPF-006: Файлы из ChildObjects существуют (если документ — файл на диске)
        Path docDir = document.getFile() != null ? document.getFile().getParent() : null;
        if (docDir != null) {
            validateChildFiles(childObjects, docDir, issues);
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

    private void validateChildFiles(XmlNode childObjects, Path baseDir, List<ValidationIssue> issues) {
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
                        child.getLine(), "/ExternalDataProcessor/ChildObjects/" + childName));
            }
        }
    }
}

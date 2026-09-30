package io.github.onec.xmlgen.validator;

import io.github.onec.xmlgen.model.MetadataTypeRegistry;
import io.github.onec.xmlgen.model.MetadataTypeRegistry.TypeDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fast local checks derived from EDT Xcore models and dt-project-checks.
 * <p>
 * The checker intentionally does not run EDT. It captures invariants that are
 * useful for xml-gen oracles: form item identifiers/names and metadata
 * reference integrity for Designer XML trees.
 */
public class EdtDerivedInvariantChecker {

    private static final Set<String> NON_FORM_ITEM_WITH_ID = Set.of(
            "Attribute", "Column", "Command", "Parameter", "FormParameter");

    private static final Set<String> FORM_ITEM_ELEMENT_TYPES = Set.of(
            "AutoCommandBar", "ContextMenu", "ExtendedTooltip",
            "InputField", "CheckBoxField", "LabelField", "Button",
            "Table", "UsualGroup", "Pages", "Page", "Popup", "PopupGroup",
            "ButtonGroup", "ColumnGroup", "CommandBar",
            "SpreadSheetDocumentField", "HTMLDocumentField", "GanttChartField",
            "PlannerField", "FormattedDocumentField", "ChartField",
            "GraphicalSchemaField", "GeographicalSchemaField", "DendrogramField",
            "TextDocumentField", "TrackBarField", "ProgressBarField",
            "CalendarField", "PictureDecoration", "TextDecoration",
            "SelectedItemsActionsPanel", "RowActionsPanel");

    private static final Set<String> FORM_NAMED_SECTIONS = Set.of(
            "Attributes", "Commands", "Parameters");

    private static final Map<String, String> MD_REF_TAG_TO_DIRECTORY = new HashMap<>();

    static {
        for (TypeDescriptor descriptor : MetadataTypeRegistry.all()) {
            MD_REF_TAG_TO_DIRECTORY.put(descriptor.xmlElement(), descriptor.directory());
        }
    }

    public Result check(XmlDocument document, Path sourceRoot) {
        List<ValidationIssue> issues = new ArrayList<>();
        if ("Form".equals(document.getRootElement())) {
            checkForm(document.getRoot(), issues);
        } else if ("MetaDataObject".equals(document.getRootElement())) {
            checkMetadataObject(document.getRoot(), sourceRoot, issues);
        } else if ("ExchangePlanContent".equals(document.getRootElement())) {
            checkExchangePlanContent(document.getRoot(), sourceRoot, issues);
        }
        return Result.from(issues);
    }

    private void checkForm(XmlNode root, List<ValidationIssue> issues) {
        checkFormNamedSections(root, issues);
        List<FormItemRef> items = new ArrayList<>();
        collectFormItems(root, "/Form", false, items);
        checkFormItemIds(items, issues);
        checkFormItemNames(items, issues);
    }

    private void checkFormNamedSections(XmlNode root, List<ValidationIssue> issues) {
        for (String sectionName : FORM_NAMED_SECTIONS) {
            XmlNode section = root.child(sectionName);
            if (section == null) {
                continue;
            }
            for (XmlNode child : section.getChildren()) {
                String name = child.attr("name");
                if (name == null) {
                    name = child.childText("name");
                }
                if (isBlank(name)) {
                    issues.add(ValidationIssue.error("EDT-FORM-NAME-001",
                            "Named form element in <" + sectionName + "> has empty name",
                            child.getLine(), "/Form/" + sectionName + "/" + child.getName()));
                } else if (!isValidIdentifier(name)) {
                    issues.add(ValidationIssue.error("EDT-FORM-NAME-002",
                            "Named form element name '" + name + "' is not a valid 1C identifier",
                            child.getLine(), "/Form/" + sectionName + "/" + child.getName() + "/@name"));
                }
            }
        }
    }

    private void collectFormItems(XmlNode node, String path, boolean insideFormItem,
                                  List<FormItemRef> items) {
        boolean formItem = isFormItemCandidate(node, insideFormItem);
        if (formItem) {
            items.add(new FormItemRef(node, path));
        }
        boolean childInsideFormItem = insideFormItem || formItem || "ChildItems".equals(node.getName());
        for (XmlNode child : node.getChildren()) {
            collectFormItems(child, path + "/" + child.getName(), childInsideFormItem, items);
        }
    }

    private boolean isFormItemCandidate(XmlNode node, boolean insideFormItem) {
        if ("Form".equals(node.getName()) || "ChildItems".equals(node.getName())) {
            return false;
        }
        if (NON_FORM_ITEM_WITH_ID.contains(node.getName())) {
            return false;
        }
        return FORM_ITEM_ELEMENT_TYPES.contains(node.getName());
    }

    private void checkFormItemIds(List<FormItemRef> items, List<ValidationIssue> issues) {
        Set<Integer> seen = new HashSet<>();
        for (FormItemRef item : items) {
            String rawId = item.node.attr("id");
            if (isBlank(rawId)) {
                issues.add(ValidationIssue.error("EDT-FORM-ID-001",
                        "Form item <" + item.node.getName() + "> has no id",
                        item.node.getLine(), item.path + "/@id"));
                continue;
            }
            Integer id = parseInteger(rawId);
            if (id == null) {
                issues.add(ValidationIssue.error("EDT-FORM-ID-002",
                        "Form item id '" + rawId + "' is not an integer",
                        item.node.getLine(), item.path + "/@id"));
                continue;
            }
            if (id == 0) {
                issues.add(ValidationIssue.error("EDT-FORM-ID-003",
                        "Form item id must not be 0",
                        item.node.getLine(), item.path + "/@id"));
                continue;
            }
            if (id < -1) {
                issues.add(ValidationIssue.warning("EDT-FORM-ID-004",
                        "Form item id is negative; EDT does not flag this, but xml-gen must not generate it",
                        item.node.getLine(), item.path + "/@id"));
                continue;
            }
            if (id == -1 && "AutoCommandBar".equals(item.node.getName())) {
                continue;
            }
            if (!seen.add(id)) {
                issues.add(ValidationIssue.error("EDT-FORM-ID-005",
                        "Form item id '" + id + "' duplicates another form item id",
                        item.node.getLine(), item.path + "/@id"));
            }
        }
    }

    private void checkFormItemNames(List<FormItemRef> items, List<ValidationIssue> issues) {
        for (FormItemRef item : items) {
            String name = item.node.attr("name");
            if (isBlank(name)) {
                if ("AutoCommandBar".equals(item.node.getName())) {
                    continue;
                }
                issues.add(ValidationIssue.error("EDT-FORM-NAME-003",
                        "Form item <" + item.node.getName() + "> has empty name",
                        item.node.getLine(), item.path + "/@name"));
            } else if (!isValidIdentifier(name)) {
                issues.add(ValidationIssue.error("EDT-FORM-NAME-004",
                        "Form item name '" + name + "' is not a valid 1C identifier",
                        item.node.getLine(), item.path + "/@name"));
            }
        }
    }

    private void checkMetadataObject(XmlNode root, Path sourceRoot, List<ValidationIssue> issues) {
        XmlNode typeNode = firstMetadataType(root);
        if (typeNode == null) {
            return;
        }
        if ("Configuration".equals(typeNode.getName())) {
            checkConfigurationReferences(typeNode, sourceRoot, issues);
        }
        if ("CommonAttribute".equals(typeNode.getName())) {
            checkMdObjectRefItems(typeNode, sourceRoot, issues, "EDT-MD-REF-002",
                    "CommonAttribute content metadata reference is unresolved");
        }
    }

    private void checkConfigurationReferences(XmlNode configuration, Path sourceRoot,
                                              List<ValidationIssue> issues) {
        XmlNode childObjects = configuration.child("ChildObjects");
        if (childObjects == null) {
            return;
        }
        for (XmlNode child : childObjects.getChildren()) {
            String directory = MD_REF_TAG_TO_DIRECTORY.get(child.getName());
            String name = child.getText();
            if (directory == null || isBlank(name)) {
                continue;
            }
            if (!metadataObjectExists(sourceRoot, directory, name)) {
                issues.add(ValidationIssue.error("EDT-MD-REF-001",
                        "Configuration references missing metadata object " + child.getName() + "." + name,
                        child.getLine(), "/MetaDataObject/Configuration/ChildObjects/" + child.getName()));
            }
        }
    }

    private void checkExchangePlanContent(XmlNode root, Path sourceRoot, List<ValidationIssue> issues) {
        checkMdObjectRefItems(root, sourceRoot, issues, "EDT-MD-REF-003",
                "ExchangePlanContent item metadata reference is unresolved");
    }

    private void checkMdObjectRefItems(XmlNode node, Path sourceRoot, List<ValidationIssue> issues,
                                       String code, String message) {
        for (XmlNode child : node.getChildren()) {
            if (isMetadataRefElement(child)) {
                String ref = child.getText();
                if (!isBlank(ref) && !metadataRefExists(sourceRoot, ref)) {
                    issues.add(ValidationIssue.error(code,
                            message + ": " + ref,
                            child.getLine(), child.getName()));
                }
            }
            checkMdObjectRefItems(child, sourceRoot, issues, code, message);
        }
    }

    private boolean isMetadataRefElement(XmlNode node) {
        String text = node.getText();
        return ("Metadata".equals(node.getName()) || "metadata".equals(node.getName())
                || "MDObjectRef".equals(typeAttribute(node)))
                && !isBlank(text) && text.contains(".");
    }

    private String typeAttribute(XmlNode node) {
        String value = node.attr("xsi:type");
        return value == null ? node.attr("type") : value;
    }

    private XmlNode firstMetadataType(XmlNode root) {
        for (XmlNode child : root.getChildren()) {
            if (MetadataTypeRegistry.byXmlElement(child.getName()) != null
                    || "Configuration".equals(child.getName())) {
                return child;
            }
        }
        return null;
    }

    private boolean metadataRefExists(Path sourceRoot, String ref) {
        if (sourceRoot == null) {
            return true;
        }
        String[] parts = ref.split("\\.", 2);
        if (parts.length != 2) {
            return true;
        }
        TypeDescriptor descriptor = MetadataTypeRegistry.get(parts[0]);
        if (descriptor == null && "Configuration".equals(parts[0])) {
            return Files.isRegularFile(sourceRoot.resolve("Configuration.xml"));
        }
        if (descriptor == null) {
            return true;
        }
        return metadataObjectExists(sourceRoot, descriptor.directory(), parts[1]);
    }

    private boolean metadataObjectExists(Path sourceRoot, String directory, String name) {
        if (sourceRoot == null) {
            return true;
        }
        return Files.isRegularFile(sourceRoot.resolve(directory).resolve(name + ".xml"));
    }

    private Integer parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isValidIdentifier(String name) {
        return name.matches("[а-яА-ЯёЁa-zA-Z_][а-яА-ЯёЁa-zA-Z0-9_]*");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private record FormItemRef(XmlNode node, String path) {}

    public record Result(List<ValidationIssue> issues, Map<String, Integer> summary) {
        static Result from(List<ValidationIssue> issues) {
            Map<String, Integer> summary = new LinkedHashMap<>();
            summary.put("ERROR", 0);
            summary.put("WARNING", 0);
            summary.put("INFO", 0);
            for (ValidationIssue issue : issues) {
                summary.merge(issue.getSeverity().name(), 1, Integer::sum);
            }
            return new Result(List.copyOf(issues), summary);
        }

        public boolean hasErrors() {
            return summary.getOrDefault("ERROR", 0) > 0;
        }

        public Map<String, Object> toDetails() {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("source", "edt-xcore+dt-project-checks");
            details.put("summary", summary);
            details.put("hasErrors", hasErrors());
            details.put("issues", issues.stream()
                    .map(EdtDerivedInvariantChecker::issueToMap)
                    .toList());
            return details;
        }
    }

    private static Map<String, Object> issueToMap(ValidationIssue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", issue.getSeverity().name());
        map.put("code", issue.getCode());
        map.put("message", issue.getMessage());
        map.put("line", issue.getLine());
        map.put("element", issue.getElement());
        return map;
    }
}

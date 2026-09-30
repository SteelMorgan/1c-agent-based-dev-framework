package io.github.onec.xmlgen.validator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for Ext/ClientApplicationInterface.xml.
 */
public class ClientApplicationInterfaceValidator implements XmlValidator {

    private static final String ROOT = "ClientApplicationInterface";
    private static final String NS = PlatformXsdFacts.NS_MANAGED_APPLICATION_CORE;
    private static final Set<String> ROOT_CHILDREN = Set.of(
            "top", "left", "right", "bottom", "panelDef"
    );
    private static final Set<String> LAYOUT_CHILDREN = Set.of("panel", "group");
    private static final Set<String> PANEL_CHILDREN = Set.of("uuid", "height");

    @Override
    public String objectType() {
        return "client-interface";
    }

    @Override
    public boolean supports(XmlDocument document) {
        return ROOT.equals(document.getRootElement())
                && NS.equals(document.getRootNamespace());
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        XmlNode root = document.getRoot();

        if (!ROOT.equals(root.getName())) {
            issues.add(ValidationIssue.error("CLIENT-IFACE-001",
                    "Expected root <ClientApplicationInterface>, got <" + root.getName() + ">",
                    root.getLine(), "/"));
            return issues;
        }
        if (!NS.equals(root.getNamespace())) {
            issues.add(ValidationIssue.error("CLIENT-IFACE-002",
                    "ClientApplicationInterface namespace must be '" + NS + "', got '"
                            + (root.getNamespace() != null ? root.getNamespace() : "(none)") + "'",
                    root.getLine(), "/ClientApplicationInterface"));
        }

        String xsiType = root.attr("xsi:type");
        if (xsiType == null || xsiType.isBlank()) {
            issues.add(ValidationIssue.warning("CLIENT-IFACE-003",
                    "ClientApplicationInterface usually declares xsi:type=\"InterfaceLayouter\"",
                    root.getLine(), "/ClientApplicationInterface/@xsi:type"));
        } else if (!"InterfaceLayouter".equals(xsiType)) {
            issues.add(ValidationIssue.error("CLIENT-IFACE-003",
                    "Expected xsi:type=\"InterfaceLayouter\", got \"" + xsiType + "\"",
                    root.getLine(), "/ClientApplicationInterface/@xsi:type"));
        }

        Set<String> panelDefs = new HashSet<>();
        collectPanelDefs(root, panelDefs, issues);
        validateRootChildren(root, panelDefs, issues);
        return issues;
    }

    private void collectPanelDefs(XmlNode root, Set<String> panelDefs, List<ValidationIssue> issues) {
        for (XmlNode child : root.getChildren()) {
            if (!"panelDef".equals(child.getName())) {
                continue;
            }
            String id = child.attr("id");
            if (id == null || id.isBlank()) {
                issues.add(ValidationIssue.error("CLIENT-IFACE-004",
                        "panelDef must have id attribute",
                        child.getLine(), "/ClientApplicationInterface/panelDef/@id"));
            } else if (!panelDefs.add(id)) {
                issues.add(ValidationIssue.error("CLIENT-IFACE-005",
                        "Duplicate panelDef id '" + id + "'",
                        child.getLine(), "/ClientApplicationInterface/panelDef/@id"));
            }
        }
    }

    private void validateRootChildren(XmlNode root, Set<String> panelDefs, List<ValidationIssue> issues) {
        for (XmlNode child : root.getChildren()) {
            if (!ROOT_CHILDREN.contains(child.getName())) {
                issues.add(ValidationIssue.error("CLIENT-IFACE-006",
                        "Unexpected ClientApplicationInterface child <" + child.getName() + ">",
                        child.getLine(), "/ClientApplicationInterface/" + child.getName()));
                continue;
            }
            if (!"panelDef".equals(child.getName())) {
                validateLayout(child, "/" + ROOT + "/" + child.getName(), panelDefs, issues);
            }
        }
    }

    private void validateLayout(XmlNode node, String path, Set<String> panelDefs, List<ValidationIssue> issues) {
        for (XmlNode child : node.getChildren()) {
            if (!LAYOUT_CHILDREN.contains(child.getName())) {
                issues.add(ValidationIssue.error("CLIENT-IFACE-007",
                        "Unexpected layouter child <" + child.getName() + ">",
                        child.getLine(), path + "/" + child.getName()));
                continue;
            }
            if ("panel".equals(child.getName())) {
                validatePanel(child, path + "/panel", panelDefs, issues);
            } else {
                validateGroup(child, path + "/group", panelDefs, issues);
            }
        }
    }

    private void validateGroup(XmlNode group, String path, Set<String> panelDefs, List<ValidationIssue> issues) {
        validateLayout(group, path, panelDefs, issues);
    }

    private void validatePanel(XmlNode panel, String path, Set<String> panelDefs, List<ValidationIssue> issues) {
        String id = panel.attr("id");
        if (id == null || id.isBlank()) {
            issues.add(ValidationIssue.error("CLIENT-IFACE-009",
                    "panel must have id attribute",
                    panel.getLine(), path + "/@id"));
        }
        for (XmlNode child : panel.getChildren()) {
            if (!PANEL_CHILDREN.contains(child.getName())) {
                issues.add(ValidationIssue.error("CLIENT-IFACE-010",
                        "Unexpected panel child <" + child.getName() + ">",
                        child.getLine(), path + "/" + child.getName()));
            }
        }
        String uuid = panel.childText("uuid");
        if (uuid != null && !uuid.isBlank() && !panelDefs.contains(uuid)) {
            issues.add(ValidationIssue.warning("CLIENT-IFACE-011",
                    "panel uuid '" + uuid + "' has no matching panelDef id",
                    panel.getLine(), path + "/uuid"));
        }
    }
}

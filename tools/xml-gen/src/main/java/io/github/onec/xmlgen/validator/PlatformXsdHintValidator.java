package io.github.onec.xmlgen.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lightweight validator for platform XSD facts that were not otherwise covered
 * by xml-gen.
 */
public class PlatformXsdHintValidator implements XmlValidator {

    private static final Set<String> CMI_ROOTS = Set.of("section", "group", "command");

    @Override
    public String objectType() {
        return "platform-xsd";
    }

    @Override
    public boolean supports(XmlDocument document) {
        String namespace = document.getRootNamespace();
        return PlatformXsdFacts.XSD_ONLY_NAMESPACES.contains(namespace)
                || (PlatformXsdFacts.NS_MANAGED_APPLICATION_CMI.equals(namespace)
                && CMI_ROOTS.contains(document.getRootElement()));
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        XmlNode root = document.getRoot();
        String namespace = root.getNamespace();

        if (PlatformXsdFacts.NS_MANAGED_APPLICATION_CMI.equals(namespace)) {
            validateCmiNode(root, "/" + root.getName(), issues);
        } else if (PlatformXsdFacts.XSD_ONLY_NAMESPACES.contains(namespace)) {
            issues.add(ValidationIssue.info("PLATFORM-XSD-001",
                    "Namespace is known from platform XSD delta: " + namespace,
                    root.getLine(), "/" + root.getName()));
        } else {
            issues.add(ValidationIssue.error("PLATFORM-XSD-001",
                    "Unsupported platform XSD hint namespace: "
                            + (namespace != null ? namespace : "(none)"),
                    root.getLine(), "/" + root.getName()));
        }
        return issues;
    }

    private void validateCmiNode(XmlNode node, String path, List<ValidationIssue> issues) {
        Set<String> required = PlatformXsdFacts.REQUIRED_ATTRIBUTES_BY_CMI_ROOT.get(node.getName());
        if (required != null) {
            for (String attr : required) {
                if (node.attr(attr) == null || node.attr(attr).isBlank()) {
                    issues.add(ValidationIssue.error("PLATFORM-XSD-002",
                            "CMI <" + node.getName() + "> must have required attribute '" + attr + "'",
                            node.getLine(), path + "/@" + attr));
                }
            }
        }

        for (XmlNode child : node.getChildren()) {
            if (CMI_ROOTS.contains(child.getName())) {
                validateCmiNode(child, path + "/" + child.getName(), issues);
            }
        }
    }
}

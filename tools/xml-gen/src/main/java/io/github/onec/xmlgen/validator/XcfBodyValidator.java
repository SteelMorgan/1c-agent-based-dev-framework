package io.github.onec.xmlgen.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Validator for schema-less XCF body artifacts such as Ext/Picture.xml,
 * Ext/Predefined.xml, Ext/Content.xml, Ext/Aggregates.xml, Flowchart.xml,
 * appearance-template bodies, and help bodies.
 *
 * <p>The contracts are intentionally small and come from safe platform resource
 * clues plus exported Designer XML: root element, namespace, and version policy.
 * Domain semantics stay in dedicated validators/oracles.</p>
 */
public class XcfBodyValidator implements XmlValidator {

    private static final String XCF_EXTRNPROPS = "http://v8.1c.ru/8.3/xcf/extrnprops";
    private static final String XCF_PREDEF = "http://v8.1c.ru/8.3/xcf/predef";
    private static final String XCF_SCHEME = "http://v8.1c.ru/8.3/xcf/scheme";
    private static final String DCS_APPEARANCE =
            "http://v8.1c.ru/8.1/data-composition-system/appearance-template";

    private static final Map<String, Contract> CONTRACTS = Map.of(
            "ExtPicture", new Contract(XCF_EXTRNPROPS, true),
            "ExchangePlanContent", new Contract(XCF_EXTRNPROPS, true),
            "PredefinedData", new Contract(XCF_PREDEF, true),
            "AccumulationRegisterAggregates", new Contract(XCF_EXTRNPROPS, true),
            "GraphicalSchema", new Contract(XCF_SCHEME, true),
            "AppearanceTemplate", new Contract(DCS_APPEARANCE, false),
            "Help", new Contract(XCF_EXTRNPROPS, true)
    );

    @Override
    public String objectType() {
        return "xcf-body";
    }

    @Override
    public List<ValidationIssue> validate(XmlDocument document, ValidationLevel level) {
        List<ValidationIssue> issues = new ArrayList<>();
        XmlNode root = document.getRoot();
        Contract contract = CONTRACTS.get(root.getName());
        if (contract == null) {
            issues.add(ValidationIssue.error("XCF-001",
                    "Unsupported XCF body root '" + root.getName()
                            + "', expected one of: " + CONTRACTS.keySet(),
                    root.getLine(), "/" + root.getName()));
            return issues;
        }

        String namespace = root.getNamespace();
        if (!contract.namespace().equals(namespace)) {
            issues.add(ValidationIssue.error("XCF-002",
                    root.getName() + " namespace must be '" + contract.namespace()
                            + "', got '" + (namespace == null || namespace.isEmpty() ? "(none)" : namespace) + "'",
                    root.getLine(), "/" + root.getName()));
        }

        String version = root.attr("version");
        if (contract.requiresVersion() && (version == null || version.isBlank())) {
            issues.add(ValidationIssue.error("XCF-003",
                    root.getName() + " must have a non-empty version attribute",
                    root.getLine(), "/" + root.getName() + "/@version"));
        }
        if (!contract.requiresVersion() && version != null && version.isBlank()) {
            issues.add(ValidationIssue.error("XCF-003",
                    root.getName() + " version attribute must not be empty when present",
                    root.getLine(), "/" + root.getName() + "/@version"));
        }
        return issues;
    }

    @Override
    public boolean supports(XmlDocument document) {
        return CONTRACTS.containsKey(document.getRootElement());
    }

    private record Contract(String namespace, boolean requiresVersion) {
    }
}

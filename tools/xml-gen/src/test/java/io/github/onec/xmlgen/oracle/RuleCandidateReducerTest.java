package io.github.onec.xmlgen.oracle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCandidateReducerTest {

    @Test
    void reducesRawCandidatesIntoPrioritizedBundles() {
        RuleMiningReport report = new RuleMiningReport(
                "run",
                "src",
                3,
                3,
                0,
                100,
                Map.of(),
                List.of(),
                List.of(
                        candidate("MINE-0001", "CARDINALITY", RuleConfidence.OBSERVED_HIGH,
                                "/Form/Items/Item/Items/Item/Type", 10, 10),
                        candidate("MINE-0002", "REQUIRED_CHILD", RuleConfidence.OBSERVED_HIGH,
                                "/Form/Items/Item/Items/Item/Type", 10, 10),
                        candidate("MINE-0003", "VALUE_DOMAIN", RuleConfidence.OBSERVED_HIGH,
                                "/Form/@xmlns:xr", 10, 10),
                        candidate("MINE-0004", "ROOT_CONTRACT", RuleConfidence.SUSPICIOUS,
                                "/Form", 1, 10)
                ),
                List.of()
        );

        RuleDigest digest = new RuleCandidateReducer().reduce(report, 10);

        assertThat(digest.rawCandidateCount()).isEqualTo(4);
        assertThat(digest.noiseSummary()).containsEntry("dropped_suspicious", 1);
        assertThat(digest.noiseSummary()).containsEntry("dropped_noisy_namespace", 1);
        assertThat(digest.bundles()).hasSize(1);
        RuleBundle bundle = digest.bundles().get(0);
        assertThat(bundle.generalizedSubject()).isEqualTo("/Form/Items/Item+/Type");
        assertThat(bundle.kinds()).containsExactly("CARDINALITY", "REQUIRED_CHILD");
        assertThat(bundle.details().get("dominance").toString()).contains("subsumes");
    }

    @Test
    void suppressesProcessedBundlesFromDispositionRegistry() {
        RuleMiningReport report = new RuleMiningReport(
                "run",
                "src",
                1,
                1,
                0,
                10,
                Map.of(),
                List.of(),
                List.of(candidate("MINE-0001", "DISCRIMINATOR_LINKED_BODY", RuleConfidence.OBSERVED_HIGH,
                        "FormType=Managed -> Ext/Form.xml", 10, 10)),
                List.of()
        );
        RuleDigest first = new RuleCandidateReducer().reduce(report, 10);
        assertThat(first.bundles()).hasSize(1);
        String key = first.bundles().get(0).key();

        RuleDispositionRegistry registry = new RuleDispositionRegistry(List.of(
                new RuleDispositionRegistry.Entry(key, "promoted", "covered by validator", "FormValidator", "today")
        ));
        RuleDigest second = new RuleCandidateReducer().reduce(report, 10, registry);

        assertThat(second.bundles()).isEmpty();
        assertThat(second.feedbackSummary()).containsEntry("suppressed_promoted", 1);
    }

    @Test
    void suppressesProcessedBundlesByGlobDispositionKey() {
        RuleMiningReport report = new RuleMiningReport(
                "run",
                "src",
                1,
                1,
                0,
                10,
                Map.of(),
                List.of(),
                List.of(candidate("MINE-0001", "REQUIRED_CHILD", "MetaDataObject.Catalog",
                        RuleConfidence.OBSERVED_HIGH,
                        "/MetaDataObject/Catalog/Properties/Autonumbering", 10, 10)),
                List.of()
        );

        RuleDispositionRegistry registry = new RuleDispositionRegistry(List.of(
                new RuleDispositionRegistry.Entry(
                        "bundle:MetaDataObject.Catalog:/MetaDataObject/Catalog/Properties*:*",
                        "accepted", "property set analyzed", "xml-gen", "today")
        ));
        RuleDigest digest = new RuleCandidateReducer().reduce(report, 10, registry);

        assertThat(digest.bundles()).isEmpty();
        assertThat(digest.feedbackSummary()).containsEntry("suppressed_accepted", 1);
    }

    @Test
    void collapsesMetadataPropertySiblingsIntoOnePropertySetBundle() {
        RuleMiningReport report = new RuleMiningReport(
                "run",
                "src",
                2,
                2,
                0,
                10,
                Map.of(),
                List.of(),
                List.of(
                        candidate("MINE-0001", "REQUIRED_CHILD", "MetaDataObject.Catalog",
                                RuleConfidence.OBSERVED_HIGH,
                                "/MetaDataObject/Catalog/Properties/Autonumbering", 10, 10),
                        candidate("MINE-0002", "VALUE_DOMAIN", "MetaDataObject.Catalog",
                                RuleConfidence.OBSERVED_HIGH,
                                "/MetaDataObject/Catalog/Properties/ChoiceMode", 10, 10),
                        candidate("MINE-0003", "CARDINALITY", "MetaDataObject.Catalog",
                                RuleConfidence.OBSERVED_HIGH,
                                "/MetaDataObject/Catalog/ChildObjects/Attribute/Properties/FillChecking", 10, 10)
                ),
                List.of()
        );

        RuleDigest digest = new RuleCandidateReducer().reduce(report, 10);

        assertThat(digest.bundles()).extracting(RuleBundle::generalizedSubject)
                .contains("/MetaDataObject/Catalog/Properties/*")
                .contains("/MetaDataObject/Catalog/ChildObjects/Attribute/Properties/*");
        assertThat(digest.bundles()).noneMatch(b ->
                b.generalizedSubject().endsWith("/Autonumbering")
                        || b.generalizedSubject().endsWith("/ChoiceMode"));
    }

    private RuleCandidate candidate(String id, String kind, RuleConfidence confidence, String subject,
                                    int support, int total) {
        return new RuleCandidate(id, kind, "FormBody", confidence, support, total, subject,
                "rule", Map.of(), List.of("Forms/A/Ext/Form.xml"));
    }

    private RuleCandidate candidate(String id, String kind, String bucket, RuleConfidence confidence, String subject,
                                    int support, int total) {
        return new RuleCandidate(id, kind, bucket, confidence, support, total, subject,
                "rule", Map.of(), List.of("Catalogs/A.xml"));
    }
}

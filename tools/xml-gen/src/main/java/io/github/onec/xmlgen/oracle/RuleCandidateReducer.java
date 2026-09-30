package io.github.onec.xmlgen.oracle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class RuleCandidateReducer {

    public static final int DEFAULT_DIGEST_LIMIT = 500;
    private static final int DEFAULT_PER_BUCKET_LIMIT = 25;

    public RuleDigest reduce(RuleMiningReport report, int digestLimit) {
        return reduce(report, digestLimit, RuleDispositionRegistry.empty());
    }

    public RuleDigest reduce(RuleMiningReport report, int digestLimit, RuleDispositionRegistry dispositionRegistry) {
        int limit = digestLimit <= 0 ? DEFAULT_DIGEST_LIMIT : digestLimit;
        Map<String, Integer> rawByKind = new TreeMap<>();
        Map<String, Integer> rawByConfidence = new TreeMap<>();
        Map<String, Integer> noise = new LinkedHashMap<>();
        Map<String, Integer> feedback = new LinkedHashMap<>();
        Map<String, BundleAccumulator> byKey = new LinkedHashMap<>();

        for (RuleCandidate candidate : report.candidates()) {
            rawByKind.merge(candidate.kind(), 1, Integer::sum);
            rawByConfidence.merge(candidate.confidence().name(), 1, Integer::sum);

            DropReason dropReason = dropReason(candidate);
            if (dropReason != null) {
                noise.merge(dropReason.key, 1, Integer::sum);
                continue;
            }

            String generalized = generalizeSubject(candidate.bucket(), candidate.subject());
            String key = candidate.bucket() + "|" + generalized;
            byKey.computeIfAbsent(key, ignored -> new BundleAccumulator(candidate.bucket(), generalized))
                    .add(candidate);
        }

        List<RuleBundle> bundles = byKey.values().stream()
                .map(BundleAccumulator::toBundle)
                .sorted(Comparator.comparingDouble(RuleBundle::score).reversed()
                        .thenComparing(RuleBundle::bucket)
                        .thenComparing(RuleBundle::generalizedSubject))
                .toList();

        List<RuleBundle> limited = new ArrayList<>();
        Map<String, Integer> selectedByBucket = new LinkedHashMap<>();
        int omittedByBucketCap = 0;
        int suppressedByFeedback = 0;
        for (RuleBundle b : bundles) {
            if (limited.size() >= limit) {
                break;
            }
            RuleDispositionRegistry.Entry disposition = dispositionRegistry == null
                    ? null
                    : dispositionRegistry.find(b.key());
            if (dispositionRegistry != null && dispositionRegistry.suppress(disposition)) {
                feedback.merge("suppressed_" + disposition.status().toLowerCase(Locale.ROOT), 1, Integer::sum);
                suppressedByFeedback++;
                continue;
            }
            int selected = selectedByBucket.getOrDefault(b.bucket(), 0);
            if (selected >= DEFAULT_PER_BUCKET_LIMIT && !isPriorityBundle(b)) {
                omittedByBucketCap++;
                continue;
            }
            selectedByBucket.put(b.bucket(), selected + 1);
            limited.add(new RuleBundle(
                    "DIGEST-%04d".formatted(limited.size() + 1),
                    b.key(),
                    b.bucket(),
                    b.subject(),
                    b.generalizedSubject(),
                    b.score(),
                    b.confidence(),
                    b.support(),
                    b.total(),
                    b.ratio(),
                    b.kinds(),
                    b.candidateIds(),
                    b.examples(),
                    b.rationale(),
                    b.details()
            ));
        }
        int omitted = Math.max(0, bundles.size() - limited.size() - omittedByBucketCap - suppressedByFeedback);
        noise.put("omitted_by_bucket_cap", omittedByBucketCap);
        noise.put("omitted_by_digest_limit", omitted);

        return new RuleDigest(report.runId(), report.sourceRoot(), report.candidates().size(), bundles.size(),
                limited.size(), limit, rawByKind, rawByConfidence, noise, feedback, limited);
    }

    private boolean isPriorityBundle(RuleBundle bundle) {
        return bundle.kinds().contains("LINKED_BODY")
                || bundle.kinds().contains("DISCRIMINATOR_LINKED_BODY");
    }

    private DropReason dropReason(RuleCandidate c) {
        if (c.confidence() == RuleConfidence.SUSPICIOUS) {
            return DropReason.SUSPICIOUS;
        }
        if (isNoisyNamespaceCandidate(c)) {
            return DropReason.NOISY_NAMESPACE;
        }
        double ratio = ratio(c);
        if (c.confidence() == RuleConfidence.CONDITIONAL && c.support() < 20 && ratio < 0.80) {
            return DropReason.WEAK_CONDITIONAL;
        }
        if (c.confidence() == RuleConfidence.OBSERVED_LOW && c.total() > 4) {
            return DropReason.WEAK_LOW_SUPPORT;
        }
        return null;
    }

    private boolean isNoisyNamespaceCandidate(RuleCandidate c) {
        if (!c.subject().contains("/@xmlns")) {
            return false;
        }
        return "VALUE_DOMAIN".equals(c.kind()) || "REQUIRED_ATTRIBUTE".equals(c.kind());
    }

    private String generalizeSubject(String bucket, String subject) {
        String s = subject;
        if ("FormBody".equals(bucket)) {
            s = s.replaceAll("(?:/Items/Item){2,}", "/Items/Item+");
            s = s.replaceAll("(?:/Columns/Column){2,}", "/Columns/Column+");
            s = s.replaceAll("(?:/Commands/Command){2,}", "/Commands/Command+");
        }
        if (bucket.startsWith("PredefinedData")) {
            s = s.replaceAll("(?:/Item){2,}", "/Item+");
        }
        if (bucket.startsWith("MetaDataObject.")) {
            s = s.replaceAll("(/Properties)/[^/]+(?:/.*)?$", "$1/*");
            s = s.replaceAll("(/ChildObjects/[^/]+/Properties)/[^/]+(?:/.*)?$", "$1/*");
        }
        if ("Rights".equals(bucket)) {
            s = s.replaceAll("(?:/object){2,}", "/object+");
            s = s.replaceAll("(?:/right){2,}", "/right+");
        }
        return s;
    }

    private double ratio(RuleCandidate c) {
        return c.total() == 0 ? 0.0 : (double) c.support() / (double) c.total();
    }

    private enum DropReason {
        SUSPICIOUS("dropped_suspicious"),
        NOISY_NAMESPACE("dropped_noisy_namespace"),
        WEAK_CONDITIONAL("dropped_weak_conditional"),
        WEAK_LOW_SUPPORT("dropped_weak_low_support");

        private final String key;

        DropReason(String key) {
            this.key = key;
        }
    }

    private final class BundleAccumulator {
        private final String bucket;
        private final String generalizedSubject;
        private final List<RuleCandidate> candidates = new ArrayList<>();
        private final Set<String> kinds = new LinkedHashSet<>();
        private final Set<String> candidateIds = new LinkedHashSet<>();
        private final Set<String> examples = new LinkedHashSet<>();
        private final Set<String> subjects = new LinkedHashSet<>();
        private RuleConfidence confidence = RuleConfidence.SUSPICIOUS;
        private int support = 0;
        private int total = 0;
        private double score = 0.0;

        private BundleAccumulator(String bucket, String generalizedSubject) {
            this.bucket = bucket;
            this.generalizedSubject = generalizedSubject;
        }

        private void add(RuleCandidate c) {
            candidates.add(c);
            kinds.add(c.kind());
            candidateIds.add(c.id());
            subjects.add(c.subject());
            examples.addAll(c.examples());
            if (confidenceRank(c.confidence()) > confidenceRank(confidence)) {
                confidence = c.confidence();
            }
            if (c.support() > support || (c.support() == support && c.total() > total)) {
                support = c.support();
                total = c.total();
            }
            score += score(c);
        }

        private RuleBundle toBundle() {
            score += bundleBonus();
            List<String> kindList = new ArrayList<>(kinds);
            List<String> idList = candidateIds.stream().limit(20).toList();
            List<String> exampleList = examples.stream().limit(5).toList();
            List<String> subjectList = subjects.stream().limit(8).toList();
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("candidateCount", candidates.size());
            details.put("subjects", subjectList);
            details.put("dominance", dominanceNote(kindList));
            String key = stableKey(bucket, generalizedSubject, kindList);
            return new RuleBundle("", key, bucket, subjectList.isEmpty() ? generalizedSubject : subjectList.get(0),
                    generalizedSubject, round(score), confidence, support, total,
                    total == 0 ? 0.0 : round((double) support / (double) total),
                    kindList, idList, exampleList, rationale(kindList), details);
        }

        private double score(RuleCandidate c) {
            double s = switch (c.confidence()) {
                case OBSERVED_HIGH -> 120.0;
                case OBSERVED_LOW -> 70.0;
                case CONDITIONAL -> 40.0;
                case SUSPICIOUS -> 0.0;
            };
            s += ratio(c) * 45.0;
            s += Math.min(35.0, Math.log10(Math.max(1, c.support())) * 18.0);
            s += kindWeight(c.kind());
            s += bucketWeight(c.bucket());
            s -= pathPenalty(c.subject());
            return Math.max(0.0, s);
        }

        private double kindWeight(String kind) {
            return switch (kind) {
                case "LINKED_BODY" -> 900.0;
                case "DISCRIMINATOR_LINKED_BODY" -> 880.0;
                case "DISCRIMINATOR_NODE_CONTRACT" -> 520.0;
                case "ROOT_CONTRACT" -> 360.0;
                case "REQUIRED_CHILD" -> 42.0;
                case "CARDINALITY" -> 34.0;
                case "REQUIRED_ATTRIBUTE" -> 28.0;
                case "VALUE_DOMAIN" -> 24.0;
                case "CHILD_ORDER" -> 16.0;
                default -> 0.0;
            };
        }

        private double bucketWeight(String bucket) {
            if (bucket.equals("FormWrapper") || bucket.equals("TemplateWrapper")) {
                return 30.0;
            }
            if (bucket.equals("FormBody") || bucket.equals("DataCompositionSchema")
                    || bucket.equals("ExchangePlanContent") || bucket.startsWith("PredefinedData")) {
                return 20.0;
            }
            if (bucket.startsWith("MetaDataObject.")) {
                return 16.0;
            }
            return 0.0;
        }

        private double pathPenalty(String subject) {
            long depth = subject.chars().filter(ch -> ch == '/').count();
            double penalty = Math.max(0, depth - 7) * 8.0;
            if (subject.contains("/@uuid") || subject.contains("/@name")) {
                penalty += 8.0;
            }
            return penalty;
        }

        private double bundleBonus() {
            double bonus = 0.0;
            if (kinds.contains("ROOT_CONTRACT") && kinds.contains("REQUIRED_ATTRIBUTE")) {
                bonus += 25.0;
            }
            if (kinds.contains("CARDINALITY") && kinds.contains("REQUIRED_CHILD")) {
                bonus += 20.0;
            }
            if (kinds.contains("REQUIRED_ATTRIBUTE") && kinds.contains("VALUE_DOMAIN")) {
                bonus += 18.0;
            }
            return bonus;
        }

        private String dominanceNote(List<String> kindList) {
            if (kindList.contains("CARDINALITY") && kindList.contains("REQUIRED_CHILD")) {
                return "CARDINALITY subsumes REQUIRED_CHILD for the same generalized subject";
            }
            if (kindList.contains("VALUE_DOMAIN") && kindList.contains("REQUIRED_ATTRIBUTE")) {
                return "VALUE_DOMAIN is bundled with REQUIRED_ATTRIBUTE for the same generalized subject";
            }
            return "no dominance collapse";
        }

        private String rationale(List<String> kindList) {
            return "Bundled " + candidates.size() + " raw candidates by bucket+generalizedSubject; kinds="
                    + kindList + "; confidence=" + confidence + "; support=" + support + "/" + total;
        }

        private String stableKey(String bucket, String subject, List<String> kindList) {
            return "bundle:" + bucket + ":" + subject + ":" + String.join("+", kindList);
        }

        private int confidenceRank(RuleConfidence c) {
            return switch (c) {
                case OBSERVED_HIGH -> 4;
                case OBSERVED_LOW -> 3;
                case CONDITIONAL -> 2;
                case SUSPICIOUS -> 1;
            };
        }

        private double round(double value) {
            return Math.round(value * 1000.0) / 1000.0;
        }
    }
}

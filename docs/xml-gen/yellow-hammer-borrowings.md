# Yellow Hammer Borrowings for xml-gen

Last reviewed: 2026-06-30.

Latest checked upstream snapshot:

- `md-sparrow`: `5e49dc7` (`chore(deps): update changelog`)
- `namespace-forest`: see local review commands below

Scope:

- `yellow-hammer/md-sparrow`
- `yellow-hammer/namespace-forest`
- local `tools/xml-gen`

Purpose: keep borrowing decisions explicit, repeatable, and auditable. This file is not a praise/critique log; it is the decision table for what we adopted, rejected, postponed, and how to re-run the analysis when Yellow Hammer changes.

## Borrowing Table

| Source | Idea / artifact | Decision | xml-gen state | Evidence / verification | Notes |
|---|---|---|---|---|---|
| `namespace-forest` | Platform XSD facts as an external audit source | Adopted as audit input, not runtime dependency | `tools/xml-gen/scripts/xsd_coverage_delta.py` | `xsdNotReferencedByXmlGen: 3 -> 0`, `globalElementsNotMentioned: 2 -> 0`, `requiredAttributesNotMentioned: 24 -> 0`, `requiredElementsNotMentioned: 6 -> 0` after transfer | Forest is useful to discover gaps. It must not become required for normal xml-gen execution. |
| `namespace-forest` | Confirmed corpus gap: `ClientApplicationInterface` | Adopted | `ClientApplicationInterfaceValidator`, `client-interface` validate type, autodetect by root | Full corpus delta after transfer: `xsdGlobalElementsObservedInCorpusButNotMentionedByXmlGen: 0` | Supports real files like `src/xml/Ext/ClientApplicationInterface.xml`. |
| `namespace-forest` | XSD-only namespaces: `data/bsl`, `managed-application/modules`, `uobjects` | Adopted as platform hints | `PlatformXsdFacts.XSD_ONLY_NAMESPACES`, `PlatformXsdHintValidator` | Delta after transfer: `xsdNotReferencedByXmlGen: 0` | No local corpus hits during review, but valid platform XSD facts. Keep as low-cost recognition/hints. |
| `namespace-forest` | XSD-only global element `section` | Adopted as platform hint | `PlatformXsdFacts.XSD_GLOBAL_ELEMENTS_IMPORTED_FROM_DELTA`, `platform-xsd` autodetect for root `section` | Delta after transfer: `globalElementsNotMentioned: 0` | Corpus did not contain `section`; implemented as CMI structural hint, not generator. |
| `namespace-forest` | Required attributes from XSD delta: `clsid`, `count`, `delta`, `formatVersion`, `helpTopic`, `index1`, `index2`, `itemType`, `lastId`, `md`, `nameInt`, `nameRus`, `pattern`, `remoteKey`, `seq`, `seqDe`, `seqUo`, `sin`, `sinDe`, `sinUo`, `startId`, `total`, `trackChanges`, `url` | Adopted as transferred facts | `PlatformXsdFacts.REQUIRED_ATTRIBUTES_IMPORTED_FROM_DELTA`; CMI subset enforced where root contract is known | Delta after transfer: `requiredAttributesNotMentioned: 0`; tests assert list completeness | Not every attr has enough context for strict validation. Store all; enforce only when the target root/type is known. |
| `namespace-forest` | Required elements from XSD delta: `longAloneMonthNames`, `longDayNames`, `longMonthNames`, `shortAloneMonthNames`, `shortDayNames`, `shortMonthNames` | Adopted as transferred facts | `PlatformXsdFacts.REQUIRED_ELEMENTS_IMPORTED_FROM_DELTA` | Delta after transfer: `requiredElementsNotMentioned: 0`; tests assert list completeness | No real corpus use found in review. Keep as facts until a concrete validator context appears. |
| `namespace-forest` | Full XSD/JAXB model generation | Rejected for now | No runtime dependency added | `./gradlew test` passes without forest; no generated JAXB classes | xml-gen remains practical writer/editor/validator. Full XSD validation can be reconsidered only with a concrete runtime use case. |
| `md-sparrow` | Golden template scaffolding | Rejected | No golden-template dependency added | Decision from review: golden output can miss invalid mutations and does not prove semantic correctness | Prefer corpus/oracle plus targeted validators. |
| `md-sparrow` | Structural validation before mutation | Already aligned / continue | Existing preview/diff gates, rollback, validators; expanded with `client-interface` and `platform-xsd` | `./gradlew test` | Borrow the discipline, not the exact implementation. |
| `md-sparrow` | `project-metadata-tree`: JSON tree over `src/cf`, `src/cfe`, `src/erf`, `src/epf` without relying on `ConfigDumpInfo.xml` | Rejected | Covered outside xml-gen by BSL LS MCP | Source review: `ProjectMetadataTreeBuilder`; user decision 2026-06-30 | Do not add a duplicate `xml-gen project tree` command. Use BSL LS MCP/project model for metadata tree/navigation. |
| `md-sparrow` | `cf-md-graph`: typed metadata graph with relation kinds such as owners, based-on, subsystem membership, register dimensions/resources, role rights | Rejected | Covered outside xml-gen by BSL LS MCP | Source review: `ProjectMetadataGraphBuilder`, `MdObjectGraphExtractor`, `RelationKind`, `RoleRightsGraphReader`; user decision 2026-06-30 | Do not add a duplicate `xml-gen project graph` command. Use BSL LS MCP for project graph/navigation/dependency analysis. |
| `md-sparrow` | Cross-version `transcode` between metadata format versions via generated JAXB classes | Watching | xml-gen preserves/propagates versions but has no generic transcode command | Source review: `VersionTranscoder`; purpose is seed/golden generation and import-gated fixtures | Potentially useful for fixture/tooling, not for normal mutation workflow yet. Needs platform/oracle confirmation before adoption. |
| `md-sparrow` | Granular JAXB-backed property DTO get/set with splice fallback prevention | Already aligned / partial inspiration | xml-gen already uses focused editors, preview validation, rollback, byte-safe replace; no JAXB dependency | Source review: `MdObjectPropertiesEdit`, `MdObjectPropertiesGranularPatch`, `MdObjectPropertiesSplice` | Good engineering pattern: avoid whole-file rewrite, verify after patch. Keep as design influence; do not port JAXB layer. |
| `md-sparrow` | Metadata object properties DTO (`cf-md-object-get/set`) and child mutations | Mostly covered | `meta info`, `meta edit`, `config edit`, `template`, `form`, `role`, `extension` commands cover the practical mutation surface | Source review: CLI commands and `docs/cf-md-object.md` | Gaps should be discovered through corpus/oracle, not by replacing xml-gen DSL with md-sparrow DTOs. |
| `md-sparrow` | External artifact CRUD for standalone `src/epf` / `src/erf` | Covered / watch details | `xml-gen epf init`, `epf add-*`, `template`, `help`, `extension` cover current needs | Source review: `ExternalArtifactCommands`, `ExternalArtifactMutations` | Re-check only if we need rename/delete/duplicate workflows for standalone EPF/ERF. |
| `namespace-forest` + local corpus | Repeatable delta report | Adopted | `xsd_coverage_delta.py --forest ... --xmlgen-root ... --corpus-root ...` | Reports in `tools/xml-gen/build/xsd-coverage-delta-*` | This is the main mechanism for future Yellow Hammer reviews. |

## Current Transfer Result

The 2026-06-30 transfer closed the targeted XSD/corpus gaps from the report:

| Delta bucket | Before | After |
|---|---:|---:|
| `xsdNotReferencedByXmlGen` | 3 | 0 |
| `globalElementsNotMentioned` | 2 | 0 |
| `requiredAttributesNotMentioned` | 24 | 0 |
| `requiredElementsNotMentioned` | 6 | 0 |
| `xsdGlobalElementsObservedInCorpusButNotMentionedByXmlGen` | 1 | 0 |
| `xsdRequiredAttributesObservedInCorpusButNotMentionedByXmlGen` | 0 | 0 |
| `xsdNamespacesObservedInCorpusButNotReferencedByXmlGen` | 0 | 0 |

Verification commands:

```bash
cd tools/xml-gen
./gradlew test
```

```bash
python3 tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /tmp/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --corpus-root "/workspaces/work/repos/1C Projects" \
  --out-dir tools/xml-gen/build/xsd-coverage-delta-after-transfer-full \
  --limit 30
```

## Borrowing Methodology

Use this method when reviewing Yellow Hammer changes again.

1. Refresh external sources.

```bash
git -C /tmp/md-sparrow pull --ff-only
git -C /tmp/namespace-forest pull --ff-only
```

If the repositories are absent, clone them first:

```bash
git clone https://github.com/yellow-hammer/md-sparrow /tmp/md-sparrow
git clone https://github.com/yellow-hammer/namespace-forest /tmp/namespace-forest
```

2. Record upstream movement.

```bash
git -C /tmp/md-sparrow log --oneline --decorate -20
git -C /tmp/namespace-forest log --oneline --decorate -20
find /tmp/namespace-forest/schemas -maxdepth 1 -mindepth 1 -type d | sort
```

3. Re-run XSD/corpus delta.

```bash
python3 tools/xml-gen/scripts/xsd_coverage_delta.py \
  --forest /tmp/namespace-forest \
  --xmlgen-root tools/xml-gen \
  --version latest \
  --corpus-root "/workspaces/work/repos/1C Projects" \
  --out-dir tools/xml-gen/build/xsd-coverage-delta-review-$(date +%Y-%m-%d) \
  --limit 80
```

4. Classify every new item.

| Class | Meaning | Default action |
|---|---|---|
| `XSD + corpus + missing` | Platform schema fact, observed in real XML, not covered by xml-gen | Implement validator/editor/generator support depending on use case |
| `corpus + missing`, not in forest | Real XML format not represented by namespace-forest | Treat as real gap; use corpus/oracle/platform resource clues |
| `XSD + missing`, not in corpus | Platform schema fact not observed locally | Transfer as fact/hint if cheap; implement strict behavior only with context |
| `md-sparrow project API` | Project-level tree/graph/property surface not covered by namespace-forest | First check BSL LS MCP coverage. Reject duplicate tree/graph/navigation features when MCP already provides them; adopt only xml-gen-specific mutation/validation gaps |
| `already covered` | Delta item maps to existing xml-gen source/test | Mark as covered with file/test reference |
| `rejected` | Idea conflicts with xml-gen architecture or lacks correctness value | Record reason; do not revisit unless premise changes |

5. Apply borrowing rule.

Adopt behavior, not code shape. A Yellow Hammer artifact is borrowable only if it improves at least one of:

- real XML coverage;
- mutation safety;
- validation quality;
- oracle evidence;
- platform version awareness;
- explainability of unsupported structures.

Do not borrow if the only evidence is that an XSD type exists or a golden template can be produced. XSD-only facts can enter `PlatformXsdFacts`; behavior needs corpus or oracle evidence.

6. Update this table in the same change.

Every accepted/rejected/postponed item must update the Borrowing Table with:

- source repo and artifact;
- decision;
- xml-gen file/test location;
- verification result;
- reason for rejection or postponement.

## Re-Review Checklist

- [ ] Upstream commits checked for `md-sparrow`.
- [ ] Upstream commits checked for `namespace-forest`.
- [ ] Latest schema versions recorded.
- [ ] `xsd_coverage_delta.py` run against latest forest.
- [ ] Corpus delta run against available project corpus.
- [ ] New delta items classified.
- [ ] Borrowing Table updated.
- [ ] Accepted items implemented or linked to backlog.
- [ ] Rejected items have explicit reasons.
- [ ] `./gradlew test` run for xml-gen changes.

# Review Report (Review Report)

> Based on: IEEE 1028-2008 (Software Reviews and Audits), ISO/IEC 20246:2017 (Work Product Reviews),
> Fagan Inspection report format, Architecture Review Board practice.

## Metadata

- Review ID: `<REVIEW-ID>`
- Task: `<TASK-ID>`
- Date: `<YYYY-MM-DD>`
- Reviewer: `<reviewer profile / family>`
- Review type: internal | independent-second-opinion | final-completion
- Reviewed artifacts: `<list of files/artifacts>`

## Scope

What was reviewed, boundaries, applicable standards, and evaluation criteria.

## Findings

| ID | Severity | Location | Category | Description | Resolution |
| --- | --- | --- | --- | --- | --- |
| F-01 | block | | | | open/fixed/withdrawn/out-of-scope |
| F-02 | warn | | | | |
| F-03 | note | | | | |

### Severity Classification (Severity Classification)

- **block**: Blocks acceptance. Must be fixed before the artifact or phase can proceed.
- **warn**: Must be fixed before final task acceptance. Does not block the current phase.
- **note**: Suggestion for improvement. Does not block acceptance.

### Finding Categories

correctness | completeness | consistency | compliance | architecture | security | performance | usability |
accessibility | maintainability | testability | documentation

## Primary Agent Position

For each finding, the primary agent / orchestrator records its position.

| Finding ID | Position | Rationale | Overengineering Risk Assessment | Evidence of Overengineering Assessment |
| --- | --- | --- | --- | --- |
| F-01 | agree / partially agree / disagree | | N/A / proportionate / overengineering risk | `<artifacts/evidence or N/A>` |

In the Claude → Codex/GPT workflow, the field «Overengineering Risk Assessment» is required for each finding. If the assessment
indicates «overengineering risk», the adjacent field must include artifacts/evidence sufficient to support such a
conclusion. The absence of evidence is not grounds to forgo the measure necessary to eliminate the confirmed risk.

## Disposition

**accept** / **conditional accept** / **reject** / **re-review required**

Conditions for conditional accept:

## Action Items

| ID | Finding ref | Action | Owner | Priority |
| --- | --- | --- | --- | --- |
| A-01 | F-01 | | | |

## Summary (Summary)

| Severity | Count |
| --- | --- |
| block | |
| warn | |
| note | |
| **Total** | |

Overall assessment:

## Delta Review

Use this section when artifacts have changed since the initial review.

- Delta review date:
- Changes since the initial review:
- New/changed findings:
- Resolved findings:
- Disposition update:

## Acceptance Trace

Record for final synthesis per `{{runtime-ref:framework/rules/cross-provider-review/SKILL.md}}`:

- `internal_review_completed`: yes/no/deferred
- `cross_family_review_completed`: yes/no
- `cross_family_reviewer_id`: `<participant id>`
- `cross_family_reviewer_family`: Claude/GPT/Kimi
- `cross_family_review_id`: `<REVIEW-ID>`
- `cross_family_findings_summary`:
- `primary_position_summary`:
- `rework_completed`: yes/no/N-A

The `cross_family_*` prefix is preserved for compatibility with existing review
trace. The family field is metadata, not proof of family separation and not a
selection filter; independence is confirmed by `cross_family_reviewer_id`,
which must differ from the caller id.

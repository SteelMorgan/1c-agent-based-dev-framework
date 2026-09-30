# Swarm review finding schema (FR-05, AC-05, TD §5.1)

Each round 1 finding is a structured record in the fenced block ` ```swarm-structured `
of the participant response (the `structured.py` harness mechanism; free text is allowed only in
`rationale`, FR-03). The field schema belongs to the tool (swarm), not the harness.

```json
{
  "finding_id": "F-001",
  "author_id": "claude-opus",
  "location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
  "category": "correctness | security | concurrency | data_integrity | api_contract | performance | tests",
  "severity": "P1 | P2 | P3 | P4 | P5",
  "in_lens": true,
  "claim": "что не так",
  "evidence": "фрагмент кода / наблюдение с file:line",
  "rationale": "свободный текст (единственное допустимое место)"
}
```

## Fields

- `location` — **required**: a finding without location is rejected by the core
  (mode boundary: “controversial decision without location” → review board, RISK-09).
  `path` — relative path in the checked set (diff + focused paths);
  `line_start` — integer ≥ 1; `line_end` — optional, defaults to
  `line_start` (a point location is normalized to the `line..line` range).
- `category` — taxonomy (decision No. 8, bug bounty sample as a guide
  CWE/OWASP/Bugcrowd VRT): `correctness`, `security`, `concurrency`
  (incl. reliability), `data_integrity` (incl. migrations), `api_contract`,
  `performance`, `tests`. The core canonicalizes case/separators/synonyms;
  unknown category — fail-closed rejection.
- `severity` — full table (see below).
- `in_lens` — mandatory bool tag: findings outside the lens are accepted (the checklist does not
  constrain the model, decision No. 7) and are tagged `in_lens: false`; the split
  in-lens / out-of-lens is recorded in statistics (FR-12).
- `claim`, `evidence` — required non-empty strings.
- `rationale` — optional free text.
- `finding_id` — assigned by the core, session-wide (`F-NNN`);
  only accepted findings are numbered.
- `author_id` — in the core transport, the participant's real id (traceability, linking
  re-review to the author, FR-09); in the payload of rounds 2–4 participants, `anon_id`
  is passed (TD §5.2). `review_id` in the FR-05 spec = pair (`author_id`, adapter
  review id of the participant session).

## Severity Table

| Code | Label | In value metric |
| --- | --- | --- |
| P1 | blocker | yes |
| P2 | major | yes |
| P3 | minor | yes |
| P4 | nit | no — separate counter (not prohibited) |
| P5 | note/question | no — informational, without metric |

## Mechanical validation of location (precision oversight)

The core resolves `location` to a real file in the reviewed set
(sandbox representation): the path must exist in the set, `line_start`/
`line_end` must be valid (≥ 1, `line_end ≥ line_start`, `line_end` ≤
the number of lines in the file). A finding with an invalid location is **rejected BEFORE scoring and
dedup**, and the rejection is recorded in the session log (entry: index, author,
reason, detail).

## Dedup (FR-06) — briefly

Grouping by (location, category): location normalization, overlap window of
lines/ranges (`OVERLAP_WINDOW_LINES = 4` by default), category canonicalization.
Three groups: non-unique (≥2 blind models — auto-confirmed),
unique unconfirmed (→ round 2), borderline (location overlap with
different categories — resolved by the Orchestrator with a record in the session dedup-journal,
TD §5.6). Override of auto-confirmation — with marker `auto_confirmed_overridden`
and mandatory rationale.

## Example fenced response block from a participant (round 1)

````markdown
```swarm-structured
{"findings": [
  {"location": {"path": "services/x/y.py", "line_start": 120, "line_end": 135},
   "category": "correctness", "severity": "P2", "in_lens": true,
   "claim": "деление на ноль при пустом списке",
   "evidence": "services/x/y.py:127 — total / len(items)",
   "rationale": "пустой items доходит сюда из ветки early-return"}
]}
```
````

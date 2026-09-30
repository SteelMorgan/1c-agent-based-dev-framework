# Moderator Playbook: human-critic Mode (CONS-06, E12)

The mode is enabled with `convene --human-critic` (disabled by default). The human
participates on an equal footing ONLY in the attack waves (attack phase B, redteam phase D):
they do not propose models, do not respond to criticism, and do not enter the quorum,
track-record, or kill metrics. For LLM participants, the human is just another anon_id (`M#`) from the shared
`create_anon_map` space; human authorship is not disclosed anywhere.

## Cycle of One Attack Wave

1. **The core enters awaiting_human.** On a pending attack (B) / redteam (D) wave
   `round <id>` without a turn file does NOT start an LLM wave (anti-anchoring: the human
   takes their turn without seeing the current wave's LLM attacks). The state is visible in `status` and `watch`.
2. **Show the bundle to the human verbatim.** The source for criticism is the anonymized
   bundle of the current attack: after phase A this is `bundles/wave-*.bundle.md` (positions),
   in phase D it is the moderator's synthesis from the transcript (the `synthesis` record). The bundle
   is shown to the human as-is, without your assessments and without hints about who the author is.
3. **Collect the criticism in a turn file** (see the schema below). The Structured block
   is filled in MECHANICALLY according to the human's words; fill gaps in their answer by asking
   QUESTIONS to the human ("which element?", "disagree or refine?"),
   not by adding anything from yourself. The moderator is not a coauthor of the criticism.
4. **Set `human_approved: true`** - this is the moderator's confirmation that
   the text transcribes the human's criticism verbatim (an audit function, not
   identity verification: the core records the attestation in the transcript; an explicitly false
   attestation becomes accountable).
5. **Submit the turn:** `round <id> --digest-file <digest> --human-turn-file <path>`.
   The core will record the turn first and start the LLM wave - the human's turn will go into the participants' bundle for this wave, and they will respond to it on equal footing with model attacks.

## Turn-file schema

```json
{
  "content": "<дословный текст критики человека>",
  "structured": {
    "elements": [],
    "new_findings": [{"id": "F-01", "text": "..."}],
    "position_changes": [{"element": "M2:E3", "action": "disagree", "refs": [9]}],
    "borrowed": [],
    "risk_checklist_responses": []
  },
  "refs": [9],
  "human_approved": true
}
```

- `content` — required (non-empty); the criticism addresses other people's elements as
  `<anon_id>:<element>` (anon_id only, never real ids).
- `structured` — an object; empty sections are allowed. The human does not fill out
  the role checklist (they have no role).
- `human_approved: true` — mandatory attestation; without it, the core rejects the file
  (fail-closed).
- In `content` and `structured`, internal identifiers are forbidden: real participant ids
  and the service id of the human record (`human-critic`) — lint rejects the
  file. Any ordinary words are allowed (there is no broad marker dictionary).
- `--human-keep-raw` — forensic option: save the original file in the session directory
  (`human/turn-<seq>.json`); by default the raw file is not copied.

## Waiting, Cap, and Moderator Decisions

Waiting for a human turn pauses the session's wall-clock budget
(`wall_clock.paused_sec`; `status` shows `awaiting_human`,
`paused_total_sec`, `real_elapsed_sec`, `active_elapsed_sec`). Upon reaching
the cap (`--human-wait-cap-sec`, default 1800) the session transitions to
`awaiting_moderator_decision`: `round` refuses until you explicitly submit
one of the following (each is a system entry in the transcript):

- `--human-continue-wait` — a new limited waiting interval;
- `--human-skip-wave` — the human skips ONLY the current wave (one-time);
- `--human-withdraw --reason "..."` — the mode is disabled, the session continues
  without the human until the end;
- `--human-turn-file` — the turn did arrive after all.

There is no implicit continuation. If the human made ≥1 turn in phase B, phase D
is mandatory (the skip predicate is canceled, and the reason is recorded in the transcript).

## Prohibitions

- **Do not disclose authorship.** In digests, bundle, and conversations with participants,
  a human turn is an anon_id turn. Forbidden: "the human said", "our human-critic",
  any markers of contribution provenance. Digest-lint rejects the service id
  `human-critic`, but protection against stylistic hints is the moderator's responsibility.
- **Do not label human contributions in digests** (even neutrally: "item M4
  added later") - that is time-based deanonymization.
- **Do not add criticism of your own** - your conflict of interest is accounted for through
  certification, but not eliminated; transcribe verbatim.

## Residual Risks (declared, not closed)

- **Content injection (F-25):** the text of a human turn enters the context of all
  participants without semantic filtering; lint checks identifiers, not
  intent. Responsibility for the content lies with the transcribing moderator:
  do not carry instructions like "ignore the protocol" into the turn file.
- **Semantic deanonymization is not guaranteed:** a stable anon_id protects
  against direct disclosure, but the number of turns, style, and timings may reveal the human.
- **Kill metrics:** human criticism is not counted in upheld/metrics (policy 2,
  E4); influence on kill is only through the model's sovereign withdraw by its
  own decision.

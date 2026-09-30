# Criticality Map — review-swarm tariff escalator (FR-15, TD §8.1)

Purpose. This map is a tariff escalator (control-plane), not a repository taxonomy:
critical is declared only on paths owning the executable or
governance invariant, at the smallest ownership granularity. Paths
in the diff/focused set that intersect the patterns below ALWAYS go full
swarm (AC-15); the gray zone (no intersection) is the Orchestrator rubric. Binding
the pattern to a lens yields a forced lens for the critical path (FR-11: the lens is assigned
forcibly, the observation is marked forced and excluded from
strength statistics, TD §5.7).

Provenance. Verdict of the CMAP-01 council (session cons-20260730-065455-3dc36dc4,
2026-07-30, ADOPTED with red-team amendments RT-01..RT-06, synthesis E1–E12):
tasks/agentic-operations/CMAP-01-criticality-map/.context/verdict-consilium.md.
The justification layer for each pattern (invariant owner, both error costs),
the candidate queue and the full update policy —
tasks/agentic-operations/CMAP-01-criticality-map/map-proposal.md. This file is —
an executable artifact with minimal grammar: the only bullet list
below contains all critical patterns; the generator never writes to it (E2).

Known blind spots and topology (required context during regeneration):

The secrets/** pattern is dormant by name: the gitignored directory is
absent from the git tree, and the generator cannot restore it; the regular-file rule does NOT apply
to it by owner decision (RT-03); removing the pattern during regeneration is forbidden
(E10). The principle applies: absence from the tree does not mean absence of the invariant.

Agent framework mirrors: .claude — actual executable bytes (regular
files per git object mode, modes 100644/100755), .codex/skills — symlinks
(mode 120000) and are NOT covered by globs (E9, RT-01). Git object modes
are rechecked at every map regeneration.

Freshness hash of the input snapshot (E12): sha256 of the list of top-level
areas of the working tree, command
git ls-files | cut -d/ -f1 | sort -u | sha256sum, truncated to 16 hex:
7b7c2ab70c4339bc (computed and recorded on 2026-07-30 on branch
agent-cons-01-agent-consilium-20260727). A hash mismatch is a trigger
for map review and a warning in doctor, not automatic escalation.

Format. A bulleted list with the pattern in backticks is a mandatory
full-swarm pattern; the lens binding separator is an arrow (Unicode or ASCII
hyphen-greater-than, F-015); any other trailing text on the pattern line
is rejected fail-closed at load time. Glob semantics: prefix **/ matches any
prefix, including empty (root directories match); comparison is by
normalized paths (backslashes are converted to forward slashes, without ./). Lenses —
only from the code-review directory (security, correctness, concurrency,
performance, data-contracts, tests). An exact duplicate glob with different lenses —
fail-closed (E8). Overlapping different globs are resolved by order of appearance
(the first matching binding wins), so the narrow pattern
managed-secret-families.yaml comes before the broader infra/platform-registry/**
(RT-05).

Update policy (brief; full version is in map-proposal.md). Triggers
for review: a new top-level area, merging a branch with new areas,
periodic audit by track record (E12). Guard blocks only
unclassified areas affected by this diff; others are warning in the
classification queue (RT-06). Automatic level escalation is forbidden. A mirror change is
legitimate only if it is reproduced byte-for-byte by a reruntools/agent-framework-i18n-sync.py from the current .framework (RT-03). Before
each write to this file, a mandatory core load test (E2) is required. Coverage
is governed by a directed invariant: the new map does not increase the share of
full-swarm relative to the one currently in effect on the same diff window without a named
reason in map-proposal.md (E4).

## Mandatory full-swarm patterns

- `**/auth*/**` → security
- `**/crypto*/**` → security
- `**/payment*/**` → data-contracts
- `**/billing*/**` → data-contracts
- `**/migrations/**` → data-contracts
- `.framework/skills/review-swarm/**` → security
- `.framework/skills/review-harness/**` → security
- `.claude/skills/review-swarm/**` → security
- `.claude/skills/review-harness/**` → security
- `packages/contracts/**` → data-contracts
- `infra/platform-registry/managed-secret-families.yaml` → security
- `infra/platform-registry/**` → data-contracts
- `packages/pdn-core/**` → security
- `packages/delegated-actor-authority/**` → security
- `services/identity/**` → security
- `services/platform-secret-provisioner/**` → security
- `infra/openbao/**` → security
- `infra/pii-vault/**` → security
- `services/data-core/**` → data-contracts
- `agents-fleet/crypto/**` → security
- `plugins/pdn-152fz-compliance-agent/**` → security
- `secrets/**` → security

The code of the gate protocol itself (review-swarm, review-harness) in both mirrors is
in the map (F-014, E9): its defect silently disables the mandatory cross-family gate
for all other artifacts, so there can be no gray area here; the security lens
is primary, because this code implements the security invariant
of cross-family control (NFR-01). The .codex/skills/review-* paths are not
included in the list on purpose: by git object mode they are symlinks, resolving to
the .claude paths, which are already covered.

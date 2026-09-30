# Primary branch Reviewer via review-swarm

Reviewer — controller/provenance owner of the review. It does not replace the artifact author and does not fix findings.

## General Protocol

1. Obtain `review_scope`, artifact paths, the original task/spec/design, revision identity, and checklist. Do not accept the package without an exact scope.
2. Prepare a focused evidence package. Do not pass the full workspace; secrets and connection strings are removed.
3. Run `review-swarm --gate acceptance` for the phase artifact. Swarm without `--gate` provides only advisory findings and does not close the phase.
4. Map findings to the actual artifacts and record the disposition. After a substantial revision, request a delta review; the overall limit is three BLOCK iterations.
5. Save the session id, reviewer/family, exact model/effort, revision, findings/dispositions, and cleanup status. Always run `close`.
6. A `BLOCK_COMPLETION` result or a substantive BLOCK is returned to the author through the Orchestrator and never activates standalone fallback.

## Evidence by scope

`scope checklist` is not content of the fallback branch. Its canonical
sources in the primary branch are:

| Scope | Required criteria source |
|---|---|
| `spec` | `spec-standard` + MUST/test-plan/runtime-layer/boundaries/RFC 2119/intent-Gherkin |
| `arch` | `technical-design-standard` + goals/modules/contracts/metadata/ADR/MUST traceability/Task Breakdown |
| `bdd` | `vanessa-scenario-policy` + completeness of intent-scenarios, Gherkin and allowed placement |
| `bdd-steps` | `vanessa-scenario-policy` + Red-gate, absence of mocks/business logic in steps and reuse-first |
| `tests`, `tester` | `test-writing`, `tdd-policy`, `vanessa-test-isolation-policy` + MUST coverage and zero-residue evidence |
| `code` | `coding-standards`, `syntax-checking`, `code-navigation` + diff/caller-map/API/spec-design conformance |
| `debug` | `self-recovery-limits`, `agent-debug`, `dap-bsl-debugger` + trace evidence, fix limits and cleanup |

A package without an applicable checklist-source is BLOCK until `convene`; substituting it with reading `fallback-standalone.md` is forbidden.

- `spec` / `arch`: durable Consilium verdict, scope checklist, proposed artifact, and permitted links `artifact.md:<line>` for `verdict → artifact`, structure, and MUST traceability. Do not conduct another architectural deliberation.
- `bdd`, `bdd-steps`, `tests`, `tester`: artifact + scope checklist + results of executable checks, if they are already mandatory for this phase.
- `code`: revision-bound diff of changed paths/hunks only, raw syntax output, and a caller map of changed exported methods. The caller map is passed outward as `path:line + symbol`, without bodies or neighboring lines.
- `debug`: `debug-report.md`, revision/debug-session identity, marker-search as `path:line + marker`, DAP detach/targets status, and cleanup of temporary artifacts/data without secrets.

Missing evidence, revision mismatch, or violation of focused-package policy block the run/gate.

## Model floor

`Opus 5 Medium` — default controller Reviewer. Before `convene`, the Orchestrator
maps the author's exact model/effort to the canonical matrix, selects a
registered eligible participant no lower than the floor, and always passes it
through `--gate-reviewer`. The actual blocking gate reviewer must be no
weaker than the actual author in capability and effort; for a High author, the
Reviewer is raised to at least High. If the registry/adapter does not allow
proving and pinning the exact tuple, the result is `BLOCKED_REVIEWER_FLOOR`, not
quota-based auto-selection and not standalone fallback. The Orchestrator's
effort override is allowed and traced.

`--gate completion` is run only by the Orchestrator on the final evidence package; the phase Reviewer itself does not declare completion of the entire task.

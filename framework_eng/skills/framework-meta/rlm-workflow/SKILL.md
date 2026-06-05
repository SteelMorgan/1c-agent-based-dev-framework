---
name: rlm-workflow
description: MUST use WHEN you are writing reusable knowledge into RLM (pattern / architectural decision / stable domain fact) OR reading it before a non-trivial task/solution in the domain. Provides the breakdown of native-push vs RLM-pull, tools for writing and reading RLM, H-MEM levels, and hygiene.
alwaysApply: false
---
# RLM Workflow — writing and reading knowledge

> RLM is a **pull storage for universal reusable knowledge**: patterns, architectural decisions, domain facts for 1С/БСП, module findings. It is retrieved by topic through semantic search (embeddings fine-tuned for 1С) and does NOT stay in the context. The triggers for "when to write / when to read" are in the `rlm-workflow` rule; here is how to do it.

## Memory layout

| Knowledge | Layer | Tool |
|---|---|---|
| Always-on core (security, invariants, standing preferences) | native push | `MEMORY.md` + `memory/*.md` |
| Universal reusable knowledge (patterns, solutions, domain facts) | **RLM pull** | this skill |
| Transient task state (PENDING, next step, WIP) | agent-context | `task_dir/.context/*.md` |

## Writing to RLM

**Precondition (MUST):** `rlm_start_session()` before any writes — otherwise silent failure (you only learn about it through `rlm_get_hierarchy_stats`).

| What to write | Call |
|---|---|
| Universal pattern / anti-pattern | `rlm_add_hierarchical_fact(content, level=1, domain="retrospective")` |
| Detail of a specific module/file | `rlm_add_hierarchical_fact(content, level=2, domain="<domain>", module="<path>", code_ref="<file:line>")` |
| Temporary note / hypothesis | `rlm_add_hierarchical_fact(content, level=3, ttl_days=7)` — auto-consolidation |
| Decision with alternatives | `rlm_record_causal_decision(decision, reasons, consequences, constraints, alternatives)` |

Finalizing the write — `rlm_sync_state()`.

**H-MEM levels:**

| `level` | Name | When |
|---|---|---|
| `0` | `L0_PROJECT` | Loaded always. Critical global knowledge, project pitfalls. Write **rarely** (bloats every start) |
| `1` | `L1_DOMAIN` | By context. Stable patterns, session decisions |
| `2` | `L2_MODULE` | On request. Implementation details, findings at the module level |
| `3` | `L3_CODE` | Temporary. Auto-collapses into L2/L1 via `rlm_consolidate_facts` |

## Reading from RLM

At the boundary of work (entering a non-trivial task / before an architectural decision / when a recurring problem appears) — **one** call, not a tool sweep:

| Goal | Call |
|---|---|
| Topic context (recommended, one-call) | `rlm_enterprise_context(query="<domain/symptom>", max_tokens=3000)` — auto-routing of L0 + relevant L1/L2 + causal chains |
| Only relevant facts within budget | `rlm_route_context(query, max_tokens=2000)` — L0 always + L1/L2 by similarity |
| Targeted hybrid search | `rlm_search_facts(query, semantic_weight, keyword_weight, recency_weight, top_k)` |

Weights in `rlm_search_facts`: semantic query → raise `semantic_weight`; search by exact term/identifier → raise `keyword_weight`; "what was changed recently" → `recency_weight`.

## Hygiene

- `rlm_get_stale_facts()` → if needed `rlm_delete_fact()`.
- ≥5 granular facts on one topic → `rlm_consolidate_facts(min_facts=5)` (L3→L2→L1 + dedup).
- TTL: L3 → 7..14 days; L2 → 30..90; L1/L0 — no TTL.
- The first work with a project — `rlm_discover_project(project_root, task_hint)` once (seeds the L0 structure).

## Anti-patterns

| Anti-pattern | Consequence |
|---|---|
| Writing without `rlm_start_session` | Silent failure |
| Writing everything into L0 "just in case" | L0 is loaded always → every start becomes more expensive |
| Keeping universal knowledge in the native always-on core | Bloats context on every turn — this belongs in RLM (pull) |
| Putting transient (PENDING, WIP) into RLM | Pollutes the knowledge store; transient → agent-context |
| Reading RLM on every turn | Pull degrades into push; read at the boundary of work |

---
depends_on:
  - framework/rules/rlm-workflow.md
  - framework/rules/agent-context-protocol.md
  - framework/rules/search-before-write.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

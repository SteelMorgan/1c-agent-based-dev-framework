---
name: rlm-workflow
description: Rules for working with RLM-Toolkit (persistent memory across Claude Code sessions). Defines the "context" / "summarize" / "new task" rituals, H-MEM levels, and the PENDING fact format. Mandatory for the orchestrator and any agent that manages session lifecycle.
alwaysApply: true
---

# RLM Workflow

## When to apply

| Situation | Ritual |
|---|---|
| User opens the session with **"context"** | ["context"](#the-context-ritual) |
| User says **"summarize"** | ["summarize"](#the-summarize-ritual) |
| User says **"new task"** | ["new task"](#the-new-task-ritual) |
| CRIT injection from `context-monitor.sh` (`>=80%` or `>=300k tokens`) | "summarize" |
| `pre-compact.sh` fires before a compact | "summarize", short form: only steps 1, 3, 4 |
| Stable pattern learned / architectural decision made during work | [Inline writes](#inline-writes) |

## The "context" ritual

1. `rlm_start_session(restore=true)` — MANDATORY before any other RLM call
2. `rlm_enterprise_context(query="<short description of the current task or project status>", task_hint="<task type>")`
3. `rlm_search_facts(query="PENDING tasks next session", keyword_weight=0.8, semantic_weight=0.1, recency_weight=0.1, top_k=15)`
   - Filter: facts without a `[project: ...]` tag OR with `[project: <current project>]`. Hide `[project: <other>]`
4. If the task belongs to a domain — `rlm_get_facts_by_domain(domain="<name>")`
5. Output to the user: **Pending tasks** (from step 3) first, then **Recent decisions and key facts** (steps 2, 4)
6. PENDING non-empty → announce the first task and start it immediately. Ask only if PENDING is empty or tasks need clarification

## The "summarize" ritual

1. `cat ~/.claude/autocapture-buffer.jsonl 2>/dev/null || echo "empty"` → group `Edit/Write` (files) and `Bash` (commands); then clear: `: > ~/.claude/autocapture-buffer.jsonl`
2. Hygiene: `rlm_get_stale_facts()` (with `rlm_delete_fact` if needed); if 5+ facts — `rlm_consolidate_facts(min_facts=5)`
3. Write:
   - facts — `rlm_add_hierarchical_fact(content, level, domain, …)`
   - decisions — `rlm_record_causal_decision(decision, reasons, consequences, constraints, alternatives)`
   - final — `rlm_sync_state()`
4. **MANDATORY** PENDING fact:
   ```
   rlm_add_hierarchical_fact(
     content="PENDING tasks next session [task_id: <id>]: 1) <task1>. 2) <task2>.",
     domain="workflow", level=1, ttl_days=30
   )
   ```
   - Prefix `PENDING tasks next session:` — without it the "context" ritual won't find the fact
   - Mark uncertain with ❓; cross-project — separate fact tagged `[project: <name>]`; nothing pending → `PENDING tasks next session: none`
5. `git status`: complete coherent changes → commit; WIP → into PENDING; nothing → skip
6. Brief report: what was saved + git status
7. End the message with EXACTLY `Контекст сохранён в RLM. Жми /clear.` No additions after that line. On step 1-6 failure — a short note about the failure right before the line, but the line is mandatory

## The "new task" ritual

1. `rlm_start_session(restore=false)` + clear the autocapture buffer
2. Brainstorm if new feature; bugfix — skip
3. `task_id = <project>-<feature>-YYYY-MM-DD`
4. `rlm_add_hierarchical_fact(content="TASK START [task_id]: <description>. Approach: <solo/team>. Expected files: <list>.", domain="retrospective", level=1)`
5. Complex tasks → `TeamCreate` + RLM block in spawned agents' prompts

## Inline writes

| What you learned/did | Where |
|---|---|
| Universal pattern / anti-pattern | `rlm_add_hierarchical_fact(level=1, domain="retrospective")` |
| Detail of a specific module/file | `rlm_add_hierarchical_fact(level=2, domain="<domain>", module="<path>", code_ref="<file:line>")` |
| Decision with alternatives | `rlm_record_causal_decision(decision, reasons, consequences, alternatives, constraints)` |
| Temporary note / hypothesis | `rlm_add_hierarchical_fact(level=3, ttl_days=7)` — auto-consolidated |

## H-MEM levels

| `level` | Name | When |
|---|---|---|
| `0` | `L0_PROJECT` | Always loaded. Critical global knowledge, project pitfalls, invariant constraints. Write rarely |
| `1` | `L1_DOMAIN` | By context. PENDING, session decisions, stable patterns |
| `2` | `L2_MODULE` | On demand. Implementation details, configs, module-level findings |
| `3` | `L3_CODE` | Temporary. Code-level notes, debugging. Auto-collapses to L2/L1 via `rlm_consolidate_facts` |

## SHOULD

- Cross-project facts — `[project: <name>]` prefix in `content`, so the "context" filter separates own project from others
- TTL: L3 → 7..14 days; L2 → 30..90; L1/L0 — no TTL
- Architectural decisions — via `rlm_record_causal_decision`, not solo `add_hierarchical_fact` (gives a causal chain)
- After a CRIT save — `rlm_get_hierarchy_stats`, `total_facts` must grow; otherwise the save did not actually go through
- First time on a project — run `rlm_discover_project(project_root, task_hint)` once. Without it there are no L0 seeds, and facts are written into an empty structure

## Anti-patterns

| Anti-pattern | Consequence |
|---|---|
| Recording a fact without `rlm_start_session` | Silent failure; you only learn at the next `get_hierarchy_stats` |
| PENDING without the `PENDING tasks next session:` prefix | Step 3 of the "context" ritual will miss it — the next session thinks everything is done |
| Saving to RLM without clearing `autocapture-buffer.jsonl` | The next CRIT will reprocess the old buffer — duplicates |
| Ending "summarize" with an arbitrary phrase | The user won't see the sentinel and won't know whether `/clear` is safe |
| Writing everything to L0 "just in case" | L0 will bloat and load into every future session — every start gets more expensive |

---
depends_on:
  - framework/rules/agent-context-protocol.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

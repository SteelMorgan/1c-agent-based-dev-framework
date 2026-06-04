---
name: rlm-workflow
description: Step-by-step rituals of RLM-Toolkit (persistent memory between sessions) - "context", "summarize", "new task", write as you go, H-MEM levels, PENDING format. Apply when the trigger from the `rlm-workflow` rule fires.
alwaysApply: false
---

# RLM Workflow — rituals

> The body has been extracted from the `rlm-workflow` rule. Triggers (when to apply) stay in the rule; here are the step-by-step procedures for each ritual.

## When to apply (trigger map)

| Situation | Ritual |
|---|---|
| The user started the session with the word **"context"** / **"context"** | ["context"](#ritual-context) |
| The user said **"summarize"** / **"summarize"** | ["summarize"](#ritual-summarize) |
| The user said **"new task"** / **"new task"** | ["new task"](#ritual-new-task) |
| CRIT inject from `context-monitor.sh` (`≥80%` or `≥300k tokens`) | "summarize" |
| `pre-compact.sh` fires before compaction | "summarize", short form: only steps 1, 3, 4 |
| A stable pattern was learned / an architectural decision was made during the work | [Recording as you go](#recording-as-you-go) |

## Ritual "context"

1. `rlm_start_session(restore=true)` - REQUIRED before any other RLM calls
2. `rlm_enterprise_context(query="<brief description of the current task or project status>", task_hint="<task type>")`
3. `rlm_search_facts(query="PENDING tasks next session", keyword_weight=0.8, semantic_weight=0.1, recency_weight=0.1, top_k=15)`
   - Filter: show facts without the `[project: ...]` tag OR with `[project: <current project>]`. Hide `[project: <other>]`
4. If the task belongs to the domain - `rlm_get_facts_by_domain(domain="<name>")`
5. Output to the user: **Pending tasks** (from step 3) as the first list, then **Recent decisions and key facts** (steps 2, 4)
6. Non-empty PENDING -> announce the first task and start working on it immediately. Ask for clarification only if PENDING is empty or the tasks require clarification

## Ritual "summarize"

1. `cat ~/.claude/autocapture-buffer.jsonl 2>/dev/null || echo "empty"` -> group `Edit/Write` (files) and `Bash` (commands); then clear the buffer: `: > ~/.claude/autocapture-buffer.jsonl`
2. Hygiene: `rlm_get_stale_facts()` (if needed `rlm_delete_fact`); if there are ≥5 facts - `rlm_consolidate_facts(min_facts=5)`
3. Recording:
   - facts - `rlm_add_hierarchical_fact(content, level, domain, …)`
   - decisions - `rlm_record_causal_decision(decision, reasons, consequences, constraints, alternatives)`
   - final - `rlm_sync_state()`
4. **MANDATORY** PENDING fact:
   ```
   rlm_add_hierarchical_fact(
     content="PENDING tasks next session [task_id: <id>]: 1) <task1>. 2) <task2>.",
     domain="workflow", level=1, ttl_days=30
   )
   ```
   - The prefix `PENDING tasks next session:` is required - without it the "context" ritual will not find the fact
   - Mark uncertain items with ❓; cross-project items as a separate fact with `[project: <name>]`; empty -> `PENDING tasks next session: none`
5. `git status`: completed coherent changes -> commit; WIP -> into PENDING; empty -> skip
6. Briefly report: what was saved + git status
7. End the message EXACTLY with the line `Context saved in RLM. Press /clear.` No additions after it. If steps 1-6 fail, add a short failure note before this line, but the line is mandatory

## Ritual "new task"

1. `rlm_start_session(restore=false)` + clear the autocapture buffer
2. Brainstorm if this is a new feature; bugfix - skip
3. `task_id = <project>-<feature>-YYYY-MM-DD`
4. `rlm_add_hierarchical_fact(content="TASK START [task_id]: <description>. Approach: <solo/team>. Expected files: <list>.", domain="retrospective", level=1)`
5. Complex tasks -> `TeamCreate` + an RLM block in the subagent prompt

## Recording as you go

| What was learned/done | Where |
|---|---|
| Universal pattern / anti-pattern | `rlm_add_hierarchical_fact(level=1, domain="retrospective")` |
| Detail of a specific module/file | `rlm_add_hierarchical_fact(level=2, domain="<domain>", module="<path>", code_ref="<file:line>")` |
| Decision with alternatives | `rlm_record_causal_decision(decision, reasons, consequences, alternatives, constraints)` |
| Temporary note / hypothesis | `rlm_add_hierarchical_fact(level=3, ttl_days=7)` - auto-consolidates |

## H-MEM Levels

| `level` | Name | When |
|---|---|---|
| `0` | `L0_PROJECT` | Loaded always. Critical global knowledge, project pitfalls, invariant constraints. Write rarely |
| `1` | `L1_DOMAIN` | By context. PENDING, session decisions, stable patterns |
| `2` | `L2_MODULE` | On request. Implementation details, configs, findings at the module level |
| `3` | `L3_CODE` | Temporary. Code notes, debugging. Auto-collapses into L2/L1 via `rlm_consolidate_facts` |

## SHOULD

- Cross-project facts - prefix `[project: <name>]` in `content` so the filter in "context" separates its project from others
- TTL: L3 -> 7..14 days; L2 -> 30..90; L1/L0 - no TTL
- Architectural decisions - via `rlm_record_causal_decision`, not isolated `add_hierarchical_fact` (gives a cause-and-effect chain)
- After CRIT and saving - `rlm_get_hierarchy_stats`, `total_facts` should increase; otherwise the save did not actually happen
- The first work with a project - `rlm_discover_project(project_root, task_hint)` once. Without it there are no L0 seeds, facts are written into an empty structure

## Anti-patterns

| Anti-pattern | Consequence |
|---|---|
| Recording a fact without `rlm_start_session` | Silent failure; you only find out at `get_hierarchy_stats` |
| PENDING without the `PENDING tasks next session:` prefix | Step 3 of the "context" ritual will not find the fact - the next session thinks everything is done |
| Saving to RLM without clearing `autocapture-buffer.jsonl` | The next CRIT will rewrite the old buffer again - duplicates |
| Ending "summarize" with an arbitrary phrase | The user will not see the signal marker and will not know whether `/clear` is safe |
| Putting everything into L0 "just in case" | L0 will bloat and end up in all future sessions - every start will become more expensive |

---
depends_on:
  - framework/rules/agent-context-protocol.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

---
name: rlm-workflow
description: Triggers for RLM memory between sessions - "context" / "summarize" / "new task" / CRIT inject / pre-compact -> ritual from the rlm-workflow skill. Mandatory for the orchestrator and any agent that manages the session lifecycle.
alwaysApply: true
---
# RLM Workflow

> **Trigger:** one of the events below. When it fires, apply the `rlm-workflow` skill (`framework/skills/framework-meta/rlm-workflow/SKILL.md`) and perform the corresponding ritual.

| Event (trigger) | Ritual in the skill |
|---|---|
| User started the session with **"context"** / **"context"** | "context" |
| User said **"summarize"** / **"summarize"** | "summarize" |
| User said **"new task"** / **"new task"** | "new task" |
| CRIT inject from `context-monitor.sh` (`>=80%` or `>=300k tokens`) | "summarize" |
| `pre-compact.sh` fires before a compact | "summarize" (short form: steps 1, 3, 4) |
| Learned a stable pattern / made an architectural decision during work | "inline write" |

## MUST (invariant, always)

- The "summarize" ritual must end with EXACTLY the line `Context saved in RLM. Press /clear.` - without it the user will not know whether `/clear` is safe.
- A PENDING fact is mandatory and is written with the prefix `PENDING tasks next session:` - without the prefix, the "context" ritual will not find it.
- Any write to RLM only after `rlm_start_session` (otherwise silent failure).

Step-by-step ritual procedures, H-MEM levels, formats, and anti-patterns are in the `rlm-workflow` skill.

---
depends_on:
  - rlm-workflow
  - framework/rules/agent-context-protocol.md
upstream:
  - Arman-Kudaibergenov/rlm-workflow (examples/CLAUDE.md.example)
---

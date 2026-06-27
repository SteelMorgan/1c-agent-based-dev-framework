---
name: dap-bsl-debugger
description: "When runtime path is unclear, use BSL DAP debugging"
alwaysApply: true
---
# DAP BSL Debugger

> **Trigger:** there is a reproducible BSL code execution scenario, and you need to see the actual stop in the procedure, the stack, variable values, or step-by-step execution. When it triggers, apply the `dap-bsl-code-debug-procedure` skill (`framework/skills/tool-usage/diagnostics/dap-bsl-code-debug-procedure/SKILL.md`).

Use the interactive debugger only when its benefit is greater than the risk of stopping execution. For production or dangerous scenarios, first obtain a safe window/environment; if there is none, do not connect the debugger and use less invasive diagnostics.

**GUARD:** before finishing, be sure to remove all breakpoints, release the stopped thread, perform `detach`, and report that the debugger state has been cleaned up. If `detach` did not work or `ibInDebug` / a hung debug session remains, perform `force_detach` and re-check targets.

---
depends_on:
  - dap-bsl-code-debug-procedure
---

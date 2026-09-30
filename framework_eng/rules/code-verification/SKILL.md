---
name: code-verification
description: "After BSL changes, run verification and syntax checks"
alwaysApply: true
---
# BSL verification after changes

> **Trigger:** after any BSL code change. When triggered, apply the `code-verification` skill (`framework/skills/tool-usage/code-analysis/code-verification/SKILL.md`) and `syntax-checking` (`framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md`).

**GUARD:** zero LSP errors are required before commit.

---
depends_on:
  - code-verification
  - syntax-checking
---

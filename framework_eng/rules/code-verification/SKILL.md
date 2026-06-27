---
name: code-verification
description: "After BSL changes, run verification and syntax checks"
alwaysApply: true
---
# BSL Verification After Changes

> **Trigger:** after any BSL code change. When triggered, apply the `code-verification` skill (`framework/skills/tool-usage/code-analysis/code-verification/SKILL.md`) and `syntax-checking` (`framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md`).

**GUARD:** zero LSP errors are mandatory before committing.

---
depends_on:
  - code-verification
  - syntax-checking
---

---
name: code-verification
description: "After modifying BSL → apply code-verification + syntax-checking skills"
alwaysApply: true
---
# BSL Verification After Changes

> **Trigger:** after any BSL code change. When triggered, apply the `code-verification` (`framework/skills/tool-usage/code-analysis/code-verification/SKILL.md`) and `syntax-checking` (`framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md`) skills.

**GUARD:** zero LSP errors are mandatory before commit.

---
depends_on:
  - code-verification
  - syntax-checking
---

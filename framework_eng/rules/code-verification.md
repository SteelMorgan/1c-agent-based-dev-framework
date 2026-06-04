---
name: code-verification
description: "After modifying BSL -> apply the code-verification + syntax-checking skills"
alwaysApply: true
---
# BSL Verification After Changes

> **Trigger:** after any change to BSL code. When triggered, apply the `code-verification` (`framework/skills/tool-usage/code-analysis/code-verification/SKILL.md`) and `syntax-checking` (`framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md`) skills.

Sequence: first `syntax-checking` (quick LSP diagnostics), then `code-verification` (Buddy + bsl-platform-context). Zero LSP errors are mandatory before commit.

---
depends_on:
  - code-verification
  - syntax-checking
---

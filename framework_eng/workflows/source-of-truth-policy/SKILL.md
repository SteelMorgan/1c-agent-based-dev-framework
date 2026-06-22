---
name: source-of-truth-policy
description: Pointer redirect. The always-on trigger has moved to framework/rules/source-of-truth/SKILL.md, and the method is in the source-of-truth skill.
alwaysApply: false
---
# Source of Truth Policy - Pointer

> This file is preserved as a stable entry point for existing `depends_on` links. The content is split into two parts:
>
> - **Always-on trigger + invariant** (L1→L6 hierarchy, “verify the chain from top to bottom”, prohibition of the binary conclusion “the test/code is to blame”) — `framework/rules/source-of-truth/SKILL.md`.
> - **Full method** (end-to-end verification, classification of the first broken link, implications for roles, typical uses) — the `source-of-truth` skill (`framework/skills/agent-process/source-of-truth/SKILL.md`).
>
> The heavy always-on rule must live in `framework/rules/` (the installer routes always-on by the root folder — see `tools/install.py`), so the trigger moved there. This file is only a redirect.

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
  - framework/skills/agent-process/source-of-truth/SKILL.md
---

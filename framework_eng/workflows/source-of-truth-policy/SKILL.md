---
name: source-of-truth-policy
description: "Redirect: use source-of-truth rule and skill"
alwaysApply: false
---
# Source of Truth Policy — pointer

> This file is kept as a stable entry point for existing `depends_on` links. The content has been split into two parts:
>
> - **Always-on trigger + invariant** (L1→L6 hierarchy, "check the chain from top to bottom", ban on the binary conclusion "the test/code is at fault") — `framework/rules/source-of-truth/SKILL.md`.
> - **Full method** (end-to-end verification, classification of the first broken link, consequences for roles, typical applications) — the `source-of-truth` skill (`framework/skills/agent-process/source-of-truth/SKILL.md`).
>
> The heavy always-on rule must live in `framework/rules/` (the installer routes always-on rules by the root folder — see `tools/install.py`), so the trigger moved there. Here — only a redirect.

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
  - framework/skills/agent-process/source-of-truth/SKILL.md
---

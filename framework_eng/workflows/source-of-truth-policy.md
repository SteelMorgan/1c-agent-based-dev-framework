---
name: source-of-truth-policy
description: Redirect pointer. The always-on trigger has moved to framework/rules/source-of-truth.md, and the method to the source-of-truth skill.
alwaysApply: false
---
# Source of Truth Policy - Pointer

> This file is kept as a stable entry point for existing `depends_on` links. The content has been split into two parts:
>
> - **Always-on trigger + invariant** (L1→L6 hierarchy, “verify the chain from top to bottom”, prohibition on the binary conclusion “the test/code is to blame”) - `framework/rules/source-of-truth.md`.
> - **Full method** (end-to-end verification, classification of the first broken link, implications for roles, typical applications) - the `source-of-truth` skill (`framework/skills/framework-meta/source-of-truth/SKILL.md`).
>
> The heavy always-on rule must live in `framework/rules/` (the installer routes always-on by the root folder - see `tools/install.py`), so the trigger moved there. This file is only a redirect.

---
depends_on:
  - framework/rules/source-of-truth.md
  - source-of-truth
---

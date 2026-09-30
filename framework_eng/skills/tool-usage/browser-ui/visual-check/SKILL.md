---
name: visual-check
description: "Deprecated: visual checking of 1C forms has been moved to va-visual-check"
alwaysApply: false
---

# Deprecated: visual-check

This skill is retained as a compatibility pointer for old links. For visual checking of 1C forms, use the dedicated skill `va-visual-check`:

- the main Vanessa/TestClient + VA MCP path;
- the Linux headless X11/Xvfb recipe for black VA screenshots;
- browser fallback and residual-risk capture rules.

Evaluate form quality according to `form-visual-requirements`.

---
depends_on:
  - framework/skills/tool-usage/vanessa/va-visual-check/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
---

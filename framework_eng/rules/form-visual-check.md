---
name: form-visual-check
description: "After changing a managed form or form screenshot -> apply visual-check + form-visual-requirements"
alwaysApply: true
---
# Visual Form Check

> **Trigger:** after changing a managed form OR after receiving a form screenshot for review. When triggered, apply the `visual-check` (`framework/skills/tool-usage/browser-ui/visual-check/SKILL.md`) and `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`) skills.

`visual-check` takes a screenshot and checks the JS console. `form-visual-requirements` is a checklist for layout, alignment, and readability. Apply both together.

---
depends_on:
  - visual-check
  - form-visual-requirements
---

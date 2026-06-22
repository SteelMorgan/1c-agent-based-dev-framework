---
name: form-visual-check
description: "After changing a managed form or screenshot of the form -> apply visual-check + form-visual-requirements"
alwaysApply: true
---
# Form Visual Check

> **Trigger:** after changing a managed form OR after receiving a form screenshot for review. When triggered, apply the `visual-check` (`framework/skills/tool-usage/browser-ui/visual-check/SKILL.md`) and `form-visual-requirements` (`framework/skills/bsl-practices/form-visual-requirements/SKILL.md`) skills.

---
depends_on:
  - visual-check
  - form-visual-requirements
---

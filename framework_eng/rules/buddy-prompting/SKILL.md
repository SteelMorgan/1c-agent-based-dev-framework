---
name: buddy-prompting
description: "Before contacting 1C Buddy -> apply the buddy-prompting skill"
alwaysApply: true
---
# Prompts for 1C Buddy

> **Trigger:** before calling `ask_ai_assistant` (1C Buddy / Buddy). When triggered, apply the `buddy-prompting` skill (`framework/skills/tool-usage/code-analysis/buddy-prompting/SKILL.md`).

Buddy is a weak LLM with a good knowledge base. Calling it without the correct template produces junk output.

---
depends_on:
  - buddy-prompting
---

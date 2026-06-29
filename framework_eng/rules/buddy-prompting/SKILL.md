---
name: buddy-prompting
description: "Prepare a prompt before querying 1C Buddy"
alwaysApply: true
---
# Prompts for 1C Buddy

> **Trigger:** before calling `ask_ai_assistant` (1C Buddy / Buddy). When triggered, apply the `buddy-prompting` skill (`framework/skills/tool-usage/code-analysis/buddy-prompting/SKILL.md`).

Buddy is a weak LLM with a good knowledge base. Using it without the proper template produces a noisy answer.

---
depends_on:
  - buddy-prompting
---

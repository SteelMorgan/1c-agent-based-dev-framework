---
name: buddy-prompting
description: "Перед обращением к 1С Напарнику → применить навык buddy-prompting"
alwaysApply: true
---
# Промпты к 1С Напарнику

> **Триггер:** перед вызовом `ask_ai_assistant` (1С Напарник / Buddy). При срабатывании — применить навык `buddy-prompting` (`framework/skills/tool-usage/code-analysis/buddy-prompting/SKILL.md`).

Напарник — слабая LLM с хорошей базой знаний. Обращение без правильного шаблона даёт мусорный ответ.

---
depends_on:
  - buddy-prompting
---

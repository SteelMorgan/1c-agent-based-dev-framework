---
name: agent-context-protocol
description: "На старте/выходе агента читать и писать role context"
alwaysApply: true
---
# Протокол контекста агента

> **Триггер:** старт любого агента (оркестратор или сабагент) и любое его завершение (`completed`, `clarification_needed`, `implementation_error`). При срабатывании — применить навык `agent-context` (`framework/skills/agent-process/agent-context/SKILL.md`): расположение `.context/`, структура файла, таблица имён по ролям, механизм resume, экономия контекста через делегирование.

## Инвариант (всегда)

- Каждый агент — **и оркестратор, и сабагенты** — MUST как **первый шаг** прочитать `task_dir/.context/{role}-context.md` (если есть) и продолжить без повтора выполненных шагов.
- Каждый агент MUST записать `task_dir/.context/{role}-context.md` **перед любым завершением**. Нет записи = ошибка агента.
- Файл по ролям: оркестратор — `orchestrator-context.md`, сабагент — `{role}-context.md`; хранится в `task_dir/.context/`.
- Все артефакты задачи (контексты, спека, дизайн, отчёты, комментарии `.feature`) — на **русском языке**; идентификаторы кода остаются как есть.

---
depends_on:
  - framework/skills/agent-process/agent-context/SKILL.md
---

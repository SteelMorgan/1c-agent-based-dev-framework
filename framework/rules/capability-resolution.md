---
name: capability-resolution
description: Резолв capability → реализация (MCP tool или CLI). Агент использует registry.yaml для вызова.
alwaysApply: true
---

# Capability Resolution

## Модель

- Capability — стабильный контракт, по которому навыки ссылаются на возможность («что нужно сделать»).
- Реализация capability фиксируется в `framework/capabilities/registry.yaml`:
  - `kind: mcp` — MCP-сервер + tool;
  - `kind: cli` — CLI-команда.
- Связь capability ↔ навык — двусторонняя:
  - прикладной навык во frontmatter указывает `uses_capabilities: [<name>, …]`;
  - реализующий навык во frontmatter указывает `provides_capabilities: [<name>, …]`.

## Правило вызова

1. Открой навык — в его frontmatter перечислены capability через `uses_capabilities`.
2. Для каждой capability найди запись в `framework/capabilities/registry.yaml`.
3. По полю `kind` выбери способ вызова:
   - **`kind: mcp`** — возьми `server` + `tool`, схему аргументов получи из `tools/list` соответствующего MCP-сервера (IDE/Claude Code tool list).
   - **`kind: cli`** — открой указанный в `skill` SKILL.md, оттуда возьми точный синтаксис команды и аргументов; шаблон в `command` — только подсказка по подкоманде.
4. Если capability отсутствует в registry — сообщи пользователю и не подменяй вызов «похожим» tool.
5. Если MCP-сервер недоступен (нет в tool list) или CLI-бинарь не найден — сообщи пользователю; не пытайся обходными путями.

## v8-session-manager: рантайм-витрина

Часть tools v8-session-manager (`session_list`) — встроенные и доступны всегда, пока менеджер поднят. Остальные tools проксируются от подключённых 1С-клиентов и появляются на витрине только когда клиент с нужным расширением подключён к менеджеру через WS. Если capability с `server: v8-session-manager` не доступна в `tools/list` — это означает, что соответствующий клиент не подключён. Подъём клиента — задача `v8-runner` (см. SKILL.md в `framework/skills/tool-usage/v8-runner/`).

## Замена реализации

Через CLI:

```bash
python tools/capability-registry.py set <capability> --server <server> --tool <tool>
python tools/capability-registry.py set <capability> --runner <runner> --command "<cmd>"
```

Прямое редактирование `registry.yaml` тоже допускается; CLI сохраняет порядок и форматирование.

---
depends_on:
  - framework/capabilities/registry.yaml
---

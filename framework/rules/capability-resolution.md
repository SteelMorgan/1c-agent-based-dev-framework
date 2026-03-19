---
name: capability-resolution
description: Резолв capability → MCP tool. Агент использует registry.yaml для вызова.
alwaysApply: true
---

# Capability Resolution

## Правило вызова

1. Навык указывает capability — используй `capabilities/registry.yaml` для выбора server + tool.
2. Схему вызова (arguments) бери из IDE tool list.
3. Если capability отсутствует или tool недоступен — сообщи пользователю.

## Замена MCP tool

CLI: `python tools/capability-registry.py set <capability> --server <server> --tool <tool>`.

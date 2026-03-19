---
name: vanessa-diagnostics-policy
description: Политика диагностики проблем Vanessa Automation. Основной источник — event log, техжурнал используется только как последний fallback.
---

# Политика диагностики Vanessa Automation

> Приоритеты источников диагностики после неуспешного сценарного прогона.

## MUST

| Требование | Описание |
|------------|----------|
| Event Log first | Основной источник ошибок — журнал регистрации (`event-log`) |
| Техжурнал последним | Технологический журнал использовать только если `event-log` и визуальная диагностика не дали ответа |
| Учитывать timezone drift | Не полагаться слепо на локальные временные окна: ClickHouse и локальное время могут расходиться |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---

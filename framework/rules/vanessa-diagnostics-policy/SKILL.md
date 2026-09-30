---
name: vanessa-diagnostics-policy
description: "При падении Vanessa: ЖР -> visual -> tech log"
alwaysApply: true
---

# Политика диагностики Vanessa Automation

> **Триггер:** неуспешный или подозрительный сценарный прогон. При срабатывании — применить навык `vanessa-diagnostics` (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST (приоритет источников)

| Приоритет | Источник | Условие |
|-----------|----------|---------|
| 1-й | Журнал регистрации (`event-log`) | Основной источник ошибок — смотреть первым |
| 2-й | Визуальная диагностика | Для состояния формы тест-клиента, GUI-блокировки, modal/manager window и `Предупреждение безопасности` — визуальный артефакт по `va-visual-check`: сначала VA MCP-скриншот, при необходимости fallback с фиксацией причины |
| 3-й | Технологический журнал | Только если `event-log` и визуал не дали ответа |

- Не полагаться слепо на локальные временные окна: ClickHouse и локальное время могут расходиться (timezone drift).

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---

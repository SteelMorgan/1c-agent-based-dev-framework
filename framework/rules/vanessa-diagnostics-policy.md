---
name: vanessa-diagnostics-policy
description: Прогон Vanessa не прошёл → диагностировать в порядке ЖР → визуал → техжурнал. Применить навык vanessa-diagnostics.
alwaysApply: true
---

# Политика диагностики Vanessa Automation

> **Триггер:** неуспешный или подозрительный сценарный прогон. При срабатывании — применить навык `vanessa-diagnostics` (`framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md`).

## MUST (приоритет источников)

| Приоритет | Источник | Условие |
|-----------|----------|---------|
| 1-й | Журнал регистрации (`event-log`) | Основной источник ошибок — смотреть первым |
| 2-й | Визуальная диагностика (noVNC / скриншот) | При подозрении на GUI-блокировку или `Предупреждение безопасности` |
| 3-й | Технологический журнал | Только если `event-log` и визуал не дали ответа |

- Не полагаться слепо на локальные временные окна: ClickHouse и локальное время могут расходиться (timezone drift).

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-diagnostics/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
---

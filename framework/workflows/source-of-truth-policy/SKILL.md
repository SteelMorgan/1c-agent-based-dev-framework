---
name: source-of-truth-policy
description: "Redirect: использовать source-of-truth rule и skill"
alwaysApply: false
---
# Политика источников правды — указатель

> Этот файл сохранён как стабильная точка для существующих `depends_on`-ссылок. Содержимое разнесено на две части:
>
> - **Always-on триггер + инвариант** (иерархия L1→L6, «проверяй цепочку сверху вниз», запрет бинарного вывода «виноват тест/код») — `framework/rules/source-of-truth/SKILL.md`.
> - **Полный метод** (сквозная проверка, классификация первого сломанного звена, следствия для ролей, типовые применения) — навык `source-of-truth` (`framework/skills/agent-process/source-of-truth/SKILL.md`).
>
> Тяжёлое always-on правило должно жить в `framework/rules/` (установщик роутит always-on по корневой папке — см. `tools/install.py`), поэтому триггер переехал туда. Здесь — только редирект.

---
depends_on:
  - framework/rules/source-of-truth/SKILL.md
  - framework/skills/agent-process/source-of-truth/SKILL.md
---

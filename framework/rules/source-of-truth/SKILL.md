---
name: source-of-truth
description: Конфликт / падение теста / спор артефактов → проверь цепочку источников правды сверху вниз (L1→L6). Метод — в навыке source-of-truth.
alwaysApply: true
---
# Политика источников правды (Source of Truth)

> **Триггер:** любой конфликт, падение теста, расхождение поведения или спор между артефактами. При срабатывании — применить навык `source-of-truth` (`framework/skills/agent-process/source-of-truth/SKILL.md`): полная иерархия L1→L6, метод сквозной проверки, классификация первого сломанного звена, следствия для ролей, типовые применения.

---
depends_on:
  - framework/skills/agent-process/source-of-truth/SKILL.md
  - framework/rules/sdd-policy/SKILL.md
  - framework/rules/tdd-policy/SKILL.md
---

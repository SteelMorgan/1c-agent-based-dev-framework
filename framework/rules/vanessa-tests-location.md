---
name: vanessa-tests-location
description: Создаёшь / обновляешь Vanessa feature-файл → соблюдать конвенцию расположения. Применить навык vanessa-authoring для деталей.
alwaysApply: true
---

# Расположение сценариев Vanessa

> **Триггер:** создание или изменение `.feature`-файла в проекте. При срабатывании — применить навык `vanessa-authoring` (`framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md`) для деталей оформления.

## MUST (конвенция расположения)

| Требование | Правило |
|------------|---------|
| Project feature files | MUST храниться в `<project_root>/vanessa-tests/features` |
| Project support files | MUST храниться в `<project_root>/vanessa-tests/support` |
| Ссылка в документации задачи | Если создан/изменён feature-файл — в документации задачи MUST быть прямая ссылка или явный путь |
| Не смешивать с shared | Project-specific сценарии нельзя хранить в shared runtime/template каталоге framework |

## MUST (библиотека шагов)

| Требование | Правило |
|------------|---------|
| Переиспользуемые шаги | MUST размещаться в `vanessa-tests/features/steps/<функциональность>.feature` (имя файла — по предметной области, не по task-ID) |
| Reuse-first | Перед созданием шага MUST искать: стандартная библиотека Vanessa → проектные `features/**` → `support/`. Совпадение ≥ 80% — параметризовать, не дублировать |
| BSL-шаги | Шаги-функции в `support/` MUST использоваться только когда композицией подсценариев выразить нельзя; обоснование — в контексте автора шага |
| `@exportscenarios` без task-ID | Нельзя ставить `@task-<ID>` на переиспользуемые шаги — шаг живёт между задачами |

---
depends_on:
  - framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md
---

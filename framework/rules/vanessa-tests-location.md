---
name: vanessa-tests-location
description: Политика расположения project-specific feature-файлов Vanessa Automation и ссылок на них в документации задач.
---

# Расположение project-specific сценариев Vanessa

## MUST

| Требование | Описание |
|------------|----------|
| Project feature files в `vanessa-tests/` | Сценарии конкретного проекта MUST храниться в `<project_root>/vanessa-tests/features` |
| Project support files рядом | Project-specific support/fixtures MUST храниться в `<project_root>/vanessa-tests/support` |
| Ссылки в документации задачи | Если в рамках задачи создан или изменён feature-файл, в документации задачи MUST быть ссылка на этот файл |
| Не смешивать с shared templates | Project-specific сценарии нельзя хранить в shared runtime/template каталоге framework |

## Разделение

### Shared / universal

- `tools/runtime/vanessa/*.json`
- библиотечные шаги Vanessa в каталоге инструментов

### Project-local

- `<project_root>/vanessa-tests/features`
- `<project_root>/vanessa-tests/support`

## Step-library проекта

В Vanessa шаг — это экспортированный подсценарий (`@exportscenarios`) в обычном `.feature`-файле. Отдельной «обработки шагов» нет; проектная библиотека шагов = сами `.feature` в `vanessa-tests/features/`.

| Требование | Описание |
|------------|----------|
| Проектные `@exportscenarios` в `features/steps/` | Новые переиспользуемые шаги MUST размещаться в `<project_root>/vanessa-tests/features/steps/<функциональность>.feature`, если в проекте не сложилась другая раскладка (тогда — следовать существующей) |
| Группировка по функциональности | Имя файла и тело шага MUST отражать предметную область (например, `заказ-клиента.feature`), НЕ task-ID |
| Имя шага без task-ID | На `@exportscenarios` нельзя ставить `@task-<ID>` и упоминать ID в формулировке — шаг переиспользуется между задачами |
| BSL-шаги — escape hatch | Шаги-функции в `vanessa-tests/support/` MUST использоваться только когда композицией подсценариев выразить нельзя (парсинг, ФС, нетривиальные вычисления); обоснование — в контексте автора шага |
| Reuse-first | Перед созданием нового шага MUST искать совпадения: стандартная библиотека Vanessa → проектные `features/**` → `support/`. Совпадение ≥ ~80% — параметризовать существующий, не дублировать |

## Что считается ссылкой в документации задачи

Достаточно прямой ссылки или явного пути к созданному/обновлённому `.feature`, чтобы следующий агент мог быстро открыть сценарий без повторного поиска.

---
depends_on: []
requires:
  - tools
---

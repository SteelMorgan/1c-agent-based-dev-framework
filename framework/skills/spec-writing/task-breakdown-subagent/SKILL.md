---
name: task-breakdown-subagent
description: "Task decomposition for multi-agent subagent mode with Architect/Reviewer cross-review. Creates Task Breakdown JSON with BLOCK iteration support. Use when breaking down a spec for parallel execution with dedicated reviewer agent and cross-review cycles."
user-invocable: true
triggers:
  - break down this task for subagents
  - multi-agent task decomposition
  - create task breakdown with reviewer
  - subagent mode decomposition
  - plan with cross-review
  - architect and reviewer decomposition
  - parallel agent task breakdown
  - decompose with BLOCK iteration
---

# Навык декомпозиции задач (subagent mode)

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| Full-cycle процесс с ролями Architect/Reviewer | Создать Task Breakdown JSON и отправить на cross-review |
| Нужна декомпозиция спеки перед реализацией | Использовать template + example для JSON (без JSON Schema) |
| Reviewer вернул BLOCK | Запустить цикл исправления с учетом лимита итераций |

---

## Обязательный артефакт

Декомпозиция оформляется как **отдельный JSON-файл** (рядом со спецификацией или в согласованной папке проекта).

Требования к формату:
- использовать **template + example**;
- **не использовать JSON Schema**;
- сохранять единые поля:
  - `task_id`
  - `task_type`
  - `depends_on`
  - `spec_refs`

В самой спецификации должна быть:
- ссылка на этот JSON-файл, и/или
- краткая выжимка по этапам и зависимостям.

---

## Шаблон JSON (template)

```json
{
  "spec_id": "SPEC-NNN",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Краткое название задачи",
      "description": "Что должно быть сделано",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1"],
      "deliverables": ["Список ожидаемых артефактов"]
    }
  ]
}
```

## Пример JSON (example)

```json
{
  "spec_id": "SPEC-002",
  "tasks": [
    {
      "task_id": "T1",
      "task_type": "analysis",
      "title": "Проверка metadata-объектов",
      "description": "Сверить состав объектов с разделом Technical Design",
      "depends_on": [],
      "spec_refs": ["Technical Design.Metadata Objects", "Requirements.MUST-1"],
      "deliverables": ["Список проверенных объектов", "Перечень расхождений"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Реализация проведения документа",
      "description": "Реализовать движения и проверки остатков",
      "depends_on": ["T1"],
      "spec_refs": ["Requirements.MUST-2", "Requirements.MUST-3"],
      "deliverables": ["Код модуля объекта", "Тесты по MUST-требованиям"]
    }
  ]
}
```

---

## Процесс (architecture + JSON → review → BLOCK loop)

1. Architect формирует структуру работ на основе спецификации.
2. Агент готовит отдельный Task Breakdown JSON (template + example, без JSON Schema).
3. Reviewer выполняет cross-review JSON относительно спецификации и зависимостей.
4. Если вердикт **BLOCK**:
   - возврат на доработку;
   - максимум **3 итерации возврата**.
5. Если после 3 возвратов замечания остаются критичными:
   - фиксируется статус **BLOCK > 3**;
   - выполняется **эскалация** (архитектор/пользователь принимает решение о пересборке декомпозиции или уточнении спеки).

---

## Чеклист качества JSON (режим с review)

- [ ] Каждая задача имеет уникальный `task_id`.
- [ ] `task_type` отражает фактический этап (analysis/design/implementation/test и т.п.).
- [ ] `depends_on` не содержит циклических зависимостей.
- [ ] `spec_refs` есть у каждой задачи и ссылаются на конкретные разделы/требования спеки.
- [ ] Покрыты все критичные MUST-требования спецификации.
- [ ] Порядок задач реализуем с учетом зависимостей.
- [ ] В спецификации добавлена ссылка/выжимка по отдельному JSON.

---

## Типичные ошибки

| Ошибка | Последствие |
|--------|------------|
| Пропущены `spec_refs` | Потеря трассируемости |
| Несогласованные `depends_on` | Невалидный порядок исполнения |
| Изменение формата между итерациями | Рост дефектов ревью |
| Игнорирование BLOCK-лимита | Бесконечные итерации → эскалация не происходит |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---

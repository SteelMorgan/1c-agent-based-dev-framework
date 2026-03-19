---
name: task-breakdown-linear
description: Декомпозиция задач для линейного single-agent режима. Определяет отдельный Task Breakdown JSON и self-check без review-агента.
---

# Навык декомпозиции задач (linear single-agent mode)

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| FREE/linear execution без Reviewer-agent | Создать Task Breakdown JSON и провести self-check |
| Нужна декомпозиция спеки перед реализацией | Использовать template + example для JSON (без JSON Schema) |
| Выполнение идет одним агентом по шагам | Построить и выполнить линейный порядок задач по `depends_on` |

---

## Обязательный артефакт

Декомпозиция оформляется как **отдельный JSON-файл**.

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
      "title": "Проверка соответствия MUST-требованиям",
      "description": "Сопоставить MUST из спецификации с задачами реализации",
      "depends_on": [],
      "spec_refs": ["Requirements.MUST-1", "Requirements.MUST-2"],
      "deliverables": ["Матрица покрытия MUST", "Список пробелов"]
    },
    {
      "task_id": "T2",
      "task_type": "implementation",
      "title": "Реализация основной логики",
      "description": "Выполнить реализацию в соответствии с Technical Design",
      "depends_on": ["T1"],
      "spec_refs": ["Technical Design.Modules", "Requirements.MUST-3"],
      "deliverables": ["Изменения кода", "Локальные проверки"]
    },
    {
      "task_id": "T3",
      "task_type": "test",
      "title": "Проверка тест-плана",
      "description": "Проверить, что MUST покрыты тестами из Test Plan",
      "depends_on": ["T2"],
      "spec_refs": ["Test Plan (TDD)"],
      "deliverables": ["Результаты тестов", "Список отклонений"]
    }
  ]
}
```

---

## Процесс (self-check вместо review)

1. На основе спецификации сформировать отдельный Task Breakdown JSON.
2. Выполнить **self-check согласованности**:
   - все MUST отражены в задачах;
   - `depends_on` образует валидную последовательность;
   - `spec_refs` указывают на конкретные разделы/пункты.
3. Зафиксировать допущения (если в спецификации есть неопределенности):
   - явно перечислить assumptions;
   - указать влияние assumptions на порядок задач.
4. Выполнять задачи линейно в порядке зависимостей (single-agent execution).
5. Перед завершением повторить self-check на фактическое покрытие требований.

---

## Чеклист self-check (режим без review-агента)

- [ ] У каждой задачи есть уникальный `task_id`.
- [ ] `task_type` соответствует реальному этапу работ.
- [ ] `depends_on` задает исполнимый линейный порядок без циклов.
- [ ] `spec_refs` присутствуют и привязаны к спецификации.
- [ ] Все MUST-требования имеют задачи реализации/проверки.
- [ ] Допущения (assumptions) явно зафиксированы и не противоречат Scope.
- [ ] В спецификации добавлена ссылка/выжимка по отдельному JSON.

---

## Типичные ошибки

| Ошибка | Последствие |
|--------|------------|
| Нет self-check перед исполнением | Выполнение по дефектному плану |
| Нефиксированные допущения | Скрытые расхождения с ожиданиями |
| Неполные `spec_refs` | Потеря трассируемости |
| Нарушение порядка `depends_on` | Повторная работа на поздних шагах |

---
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
---

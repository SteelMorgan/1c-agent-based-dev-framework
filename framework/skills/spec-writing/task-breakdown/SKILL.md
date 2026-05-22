---
name: task-breakdown
description: "Декомпозиция задач в Task Breakdown JSON. Два режима: linear (self-check, single-agent) и subagent (cross-review + BLOCK-итерации, architect/reviewer pipeline)."
depends_on:
  - framework/skills/spec-writing/spec-standard/SKILL.md
metadata:
  category: spec-writing
---

# Навык декомпозиции задач (Task Breakdown)

---

## §1 Когда применять

| Триггер | Режим |
|---------|-------|
| FREE/linear execution без Reviewer-agent | **Linear** — self-check |
| Full-cycle процесс с ролями Architect/Reviewer | **Subagent** — cross-review + BLOCK-итерации |
| Нужна декомпозиция спеки перед реализацией | Любой режим — использовать template + example |
| Выполнение идёт одним агентом по шагам | **Linear** |
| Reviewer вернул BLOCK | **Subagent** — запустить цикл исправления |

Если контекст оркестрации неизвестен — использовать **Linear** по умолчанию.

---

## §2 JSON-формат разбиения задач

### Обязательный артефакт

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

### Шаблон JSON (template)

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

### Допустимые значения `task_type`

`analysis` | `design` | `implementation` | `test`

---

## §3 Режим Linear (self-check)

### Пример JSON

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

### Процесс (self-check вместо review)

1. На основе спецификации сформировать отдельный Task Breakdown JSON.
2. Выполнить **self-check согласованности**:
   - все MUST отражены в задачах;
   - `depends_on` образует валидную последовательность;
   - `spec_refs` указывают на конкретные разделы/пункты.
3. Зафиксировать допущения (если в спецификации есть неопределённости):
   - явно перечислить assumptions;
   - указать влияние assumptions на порядок задач.
4. Выполнять задачи линейно в порядке зависимостей (single-agent execution).
5. Перед завершением повторить self-check на фактическое покрытие требований.

### Чеклист self-check

- [ ] У каждой задачи есть уникальный `task_id`.
- [ ] `task_type` соответствует реальному этапу работ.
- [ ] `depends_on` задаёт исполнимый линейный порядок без циклов.
- [ ] `spec_refs` присутствуют и привязаны к спецификации.
- [ ] Все MUST-требования имеют задачи реализации/проверки.
- [ ] Допущения (assumptions) явно зафиксированы и не противоречат Scope.
- [ ] В спецификации добавлена ссылка/выжимка по отдельному JSON.

### Типичные ошибки (Linear)

| Ошибка | Последствие |
|--------|------------|
| Нет self-check перед исполнением | Выполнение по дефектному плану |
| Нефиксированные допущения | Скрытые расхождения с ожиданиями |
| Неполные `spec_refs` | Потеря трассируемости |
| Нарушение порядка `depends_on` | Повторная работа на поздних шагах |

---

## §4 Режим Subagent (cross-review + BLOCK-итерации)

### Пример JSON

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

### Процесс (architecture + JSON → review → BLOCK loop)

1. Architect формирует структуру работ на основе спецификации.
2. Агент готовит отдельный Task Breakdown JSON (template + example, без JSON Schema).
3. Reviewer выполняет cross-review JSON относительно спецификации и зависимостей.
4. Если вердикт **BLOCK**:
   - возврат на доработку;
   - максимум **3 итерации возврата**.
5. Если после 3 возвратов замечания остаются критичными:
   - фиксируется статус **BLOCK > 3**;
   - выполняется **эскалация** (архитектор/пользователь принимает решение о пересборке декомпозиции или уточнении спеки).

### Чеклист качества JSON (режим с review)

- [ ] Каждая задача имеет уникальный `task_id`.
- [ ] `task_type` отражает фактический этап (analysis/design/implementation/test и т.п.).
- [ ] `depends_on` не содержит циклических зависимостей.
- [ ] `spec_refs` есть у каждой задачи и ссылаются на конкретные разделы/требования спеки.
- [ ] Покрыты все критичные MUST-требования спецификации.
- [ ] Порядок задач реализуем с учётом зависимостей.
- [ ] В спецификации добавлена ссылка/выжимка по отдельному JSON.

### Типичные ошибки (Subagent)

| Ошибка | Последствие |
|--------|------------|
| Пропущены `spec_refs` | Потеря трассируемости |
| Несогласованные `depends_on` | Невалидный порядок исполнения |
| Изменение формата между итерациями | Рост дефектов ревью |
| Игнорирование BLOCK-лимита | Бесконечные итерации → эскалация не происходит |

---

## §5 Когда какой режим выбирать

| Критерий | Linear | Subagent |
|----------|--------|---------|
| Наличие Reviewer-агента | Нет | Да |
| Наличие роли Architect | Необязательно | Да |
| Способ контроля качества | Self-check | Cross-review |
| Итерации при ошибках | Нет (исправить самостоятельно) | До 3 BLOCK-возвратов, затем эскалация |
| Типичный контекст | Простые задачи, one-shot выполнение | Сложные спеки, full-cycle pipeline |
| Используется агентами | analyst, developer-code | architect |

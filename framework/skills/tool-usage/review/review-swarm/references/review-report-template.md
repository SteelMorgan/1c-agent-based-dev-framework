# Отчёт о ревью (Review Report)

> Based on: IEEE 1028-2008 (Software Reviews and Audits), ISO/IEC 20246:2017 (Work Product Reviews),
> Fagan Inspection report format, Architecture Review Board practice.

## Метаданные

- Review ID: `<REVIEW-ID>`
- Задача: `<TASK-ID>`
- Дата: `<YYYY-MM-DD>`
- Ревьюер: `<reviewer profile / family>`
- Тип ревью: internal | independent-second-opinion | final-completion
- Проверенные артефакты: `<список файлов/артефактов>`

## Scope

Что было проверено, границы, применимые стандарты и критерии оценки.

## Findings

| ID | Severity | Location | Category | Description | Resolution |
| --- | --- | --- | --- | --- | --- |
| F-01 | block | | | | open/fixed/withdrawn/out-of-scope |
| F-02 | warn | | | | |
| F-03 | note | | | | |

### Классификация серьёзности (Severity Classification)

- **block**: Блокирует acceptance. Должен быть исправлен до того, как артефакт или фаза может продолжиться.
- **warn**: Должен быть исправлен до final task acceptance. Не блокирует текущую фазу.
- **note**: Предложение по улучшению. Не блокирует acceptance.

### Категории findings

correctness | completeness | consistency | compliance | architecture | security | performance | usability |
accessibility | maintainability | testability | documentation

## Позиция primary agent

Для каждого finding primary agent / orchestrator фиксирует свою позицию.

| Finding ID | Position | Rationale | Оценка риска оверинжиниринга | Evidence оценки оверинжиниринга |
| --- | --- | --- | --- | --- |
| F-01 | agree / partially agree / disagree | | N/A / соразмерно / риск оверинжиниринга | `<artifacts/evidence или N/A>` |

В маршруте Claude → Codex/GPT поле «Оценка риска оверинжиниринга» обязательно для каждого finding. Если оценка
указывает «риск оверинжиниринга», в соседнем поле обязательно приведите artifacts/evidence, достаточные для такого
вывода. Отсутствие evidence не является основанием отказаться от меры, необходимой для устранения подтверждённого риска.

## Disposition

**accept** / **conditional accept** / **reject** / **re-review required**

Условия для conditional accept:

## Action Items

| ID | Finding ref | Action | Owner | Priority |
| --- | --- | --- | --- | --- |
| A-01 | F-01 | | | |

## Сводка (Summary)

| Severity | Count |
| --- | --- |
| block | |
| warn | |
| note | |
| **Total** | |

Общая оценка:

## Delta Review

Используй этот раздел, когда артефакты изменились после начального ревью.

- Дата delta review:
- Изменения с момента начального ревью:
- Новые/изменённые findings:
- Разрешённые findings:
- Обновление disposition:

## Acceptance Trace

Фиксируй для final synthesis по `{{runtime-ref:framework/rules/cross-provider-review/SKILL.md}}`:

- `internal_review_completed`: yes/no/deferred
- `cross_family_review_completed`: yes/no
- `cross_family_reviewer_id`: `<participant id>`
- `cross_family_reviewer_family`: Claude/GPT/Kimi
- `cross_family_review_id`: `<REVIEW-ID>`
- `cross_family_findings_summary`:
- `primary_position_summary`:
- `rework_completed`: yes/no/N-A

Префикс `cross_family_*` сохранён для совместимости существующих review
trace. Поле family — metadata, не доказательство family separation и не
selection-фильтр; независимость подтверждает `cross_family_reviewer_id`,
который обязан отличаться от caller id.

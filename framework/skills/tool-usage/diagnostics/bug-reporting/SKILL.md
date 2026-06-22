---
name: bug-reporting
description: "MUST use WHEN сабагент исчерпал лимит самовосстановления и должен передать проблему оркестратору на расследование. Provides стандарт формы bug-report.json и критерии «это баг для дебаггера»."
alwaysApply: false
---

# Bug Reporting — стандарт формы и критерии

## 1. Когда заводить bug-report

Проблема становится **багом для дебаггера** только если выполнены ВСЕ условия:

1. **Наблюдается в рантайме** — упал тест, упал сценарий, исключение, неожиданный результат. Статические замечания (стиль, покрытие, оформление) — это BLOCK от Reviewer, не баг.
2. **Есть расхождение «ожидание ≠ факт»**, причём ожидание берётся из **конкретного источника** (спека, дизайн, ассерт теста, Acceptance Scenario), не из догадки.
3. **Агент исчерпал собственный лимит самовосстановления** (см. §2 — у каждого агента свой).
4. **Это НЕ неоднозначность требований** — иначе путь `clarification_needed` → пользователь.
5. **Это НЕ отсутствие API в дизайне** — иначе `clarification_needed` → Architect через оркестратор.
6. **Это НЕ инфраструктурная проблема** (БД не запущена, сервер не отвечает, файл не найден) — это `environment_error`, идёт оркестратору как infra-проблема, не дебаггеру.

Если хотя бы одно условие не выполнено — bug-report НЕ заводится.

| Ситуация | Куда идёт |
|---|---|
| Опечатка в формуле своего кода, видна сразу | self-fix |
| Тест не компилируется после своей же правки | self-fix |
| Стиль/покрытие/оформление | Reviewer BLOCK |
| Спека противоречива/неполна | clarification → пользователь |
| В дизайне нет нужной API | clarification → Architect |
| Тест/сценарий упал, причина неочевидна за лимит попыток | **bug-report → debugger** |
| Vanessa Red-гейт зелёный, мок не очевиден | **bug-report → debugger** |
| Поведение в коде расходится с ассертом, причина неясна | **bug-report → debugger** |
| 1С не запущена, фикстуры не подняты | environment_error → оркестратор |

---

## 2. Лимиты самовосстановления (когда «исчерпал»)

| Агент | Что чинит сам | Лимит | Когда заводит bug-report |
|---|---|---|---|
| `developer-code` | Свой синтаксис/логика в своём коде | 2 попытки | Тест падает не из-за моего кода ИЛИ 2 попытки исчерпаны без понимания причины |
| `tester` | Технические ошибки в коде теста (логика теста не меняется) | 3 попытки | Падение не лечится правкой кода теста ИЛИ 3 попытки исчерпаны |
| `scenario-coder` | Свои step-implementations | 2 попытки | Red-гейт зелёный без объяснения; шаг падает с неочевидной причиной; 2 попытки исчерпаны |

Остальные агенты (`developer-tests`, `scenario-author`, `analyst`, `architect`, `explorer`, `reviewer`) bug-report **не создают** — они либо не запускают код, либо обрабатывают замечания через цикл Reviewer.

---

## 3. Структура `bug-report.json`

**Расположение:** `task_dir/.context/bugs/<bug-id>.json`

**ID:** `bug-<task-id>-<seq>`, например `bug-T-042-001`. Нумерация в рамках задачи, sequential.

**Обязательные поля помечены `*`.**

```json
{
  "id": "bug-T-042-001",                                    // *
  "status": "open",                                         // * open | in_investigation | fixed_locally | returned_to_author | escalated_to_user

  "reporter": {                                             // *
    "agent": "developer-code",                              // *
    "phase": "3d",                                          // *
    "timestamp": "2026-04-27T14:32:00Z"                     // *
  },

  "symptom": {                                              // *
    "what_ran": "unit-test 'РасчётСкидки_GivenVIP_Returns20'", // *
    "command": "1c-ai-agent-cli test ...",                  // *
    "fail_location": "tests/unit-РасчётСкидки.bsl:42",      // *
    "error_message": "Expected 20, got 15",                 // * verbatim, не пересказ
    "log_path": "task_dir/runs/2026-04-27T14-30/test.log",  // *
    "deterministic": true                                   // * true | false | unknown
  },

  "expectation": {                                          // *
    "source": "spec.md §3.2",                               // * file + section
    "quote": "Для VIP-клиентов скидка MUST составлять 20%." // * verbatim из источника
  },

  "scenario_context": {                                     // * (см. §4 — может быть incomplete)
    "incomplete": false,                                    // если true — указать reason
    "incomplete_reason": null,
    "action": "проведение документа РасходТовара",
    "user": {
      "name": "Иванов И.И.",
      "roles": ["Менеджер"],
      "is_admin": false
    },
    "input_data": {
      "kind": "document",                                   // document | processor | function_call | report
      "document": {
        "type": "Документ.РасходТовара",
        "is_new": true,
        "header": {
          "Дата": "2026-04-27",
          "Организация": "ООО Ромашка",
          "Контрагент": "<пусто>"
        },
        "tabular_sections": {
          "Товары": {
            "rows_count": 2,
            "rows_sample": [
              {"Номенклатура": "Товар А", "Количество": 5, "Цена": 100}
            ]
          }
        }
      }
    },
    "system_state": {
      "current_date": "2026-04-27",
      "active_session_params": {"ТекущаяОрганизация": "ООО Ромашка"},
      "relevant_db_state": "не проверялось"
    }
  },

  "debug_trigger": {                                        // recommended, * если репортёр знает как вызвать код
    "context": "server",                                    // client | server | unknown
    "preferred_method": "yaxunit",                          // yaxunit | vanessa | ui_mcp | mcp_tool | http | scheduled_job | unknown
    "run_after_breakpoint": "запустить один unit-test РасчётСкидки_GivenVIP_Returns20",
    "entry_point": {
      "module": "ОбщийМодуль.Скидки",
      "procedure": "РассчитатьСкидку",
      "line_hint": null
    },
    "target_hint": {
      "user": "AgentAI",
      "infobase_session_number": null,
      "client_kind": "unit-test"
    },
    "timeout_hint_seconds": 30
  },

  "self_fix_attempts": [                                    // * минимум одна запись
    {"what_tried": "проверил формулу в РассчитатьСкидку()",  "result": "формула совпадает со спекой"},
    {"what_tried": "перепрогнал тест после rebuild",         "result": "то же значение 15"}
  ],
  "stopping_reason": "after_2_attempts",                    // * after_N_attempts | suspected_other_layer | out_of_scope

  "hypotheses": [                                           // опционально, но если есть — с reasoning
    {
      "layer": "data",                                      // code | test | scenario | step | data | spec | unknown
      "agent": "developer-tests",                           // подозреваемый владелец
      "reasoning": "тест ожидает VIP-категорию у клиента, но в фикстуре её может не быть"
    }
  ],

  "context": {                                              // *
    "files_touched_this_phase": [                           // * что менялось в этой фазе
      "src/CommonModules/Скидки/Module.bsl"
    ],
    "related_artifacts": [                                  // *
      "spec.md",
      "tests/unit-РасчётСкидки.bsl"
    ],
    "protected_paths": [],                                  // что дебаггеру НЕ трогать
    "blocked_paths": []                                     // занято другими задачами
  }
}
```

---

## 4. Правила заполнения

### 4.1 Общие

- **`expectation.source` + `quote` обязательны.** Без явной цитаты из источника правды bug-report не принимается. Это снимает «по моему мнению должно быть иначе».
- **`symptom.error_message` — verbatim.** Прямая цитата ассерта/исключения/лога, не пересказ.
- **`self_fix_attempts` — минимум одна запись.** Даже «прочитал код, причины не вижу» — артефакт. Это блокирует «выкинул через стенку».
- **`hypotheses` — опциональны, но если указаны — с `reasoning`.** Гипотеза без обоснования = шум.
- **`context.files_touched_this_phase` обязательно.** Дебаггер должен знать, что менялось недавно.
- **Конкретные значения, не «обычный документ».** Цитировать фактические данные из теста/фикстуры/прогона.
- **`debug_trigger` заполнять всегда, когда известно как вызвать код.** Это не инструкция дебаггеру обязательно использовать DAP; это стартовая подсказка для выбора между DAP и trace через ЖР.

### 4.2 `scenario_context` — что и как заполнять

Дебаггер не сможет воспроизвести и смоделировать без понимания, какое действие выполнялось, под каким пользователем и с какими данными.

**`action`** — конкретное действие в системе: «проведение документа X», «запуск обработки Y», «вызов функции Z», «формирование отчёта».

**`user`** — обязательно. В 1С много ветвлений по правам.

**`input_data.kind`** определяет, какие подполя заполнять:

- `document` → `document.type`, `is_new` (важно — у нового документа нет ссылки), `header` (реквизиты шапки), `tabular_sections` (количество строк + первая/проблемная строка).
- `processor` → `processor.name`, `form_fields` (значения на форме).
- `function_call` → `module`, `procedure`, `arguments` (фактические значения).
- `report` → `name`, `parameters`.

**Что НЕ дампить:**

- Объекты целиком (`Объект формы`, `СправочникОбъект.<ВсеПоля>`) — только релевантные реквизиты.
- ТаблицаЗначений целиком — только `rows_count` + первая/проблемная строка.
- Бинарные данные, пароли, токены, пользовательские персональные данные кроме служебной идентификации.
- Метаданные (`Метаданные.Документы.X.<всё>`) — только имя типа.

**`is_new: true/false` критично** — у нового документа нет ссылки и многих реквизитов.

**`relevant_db_state`** — заполняется только если репортёр уже проверил состояние БД (`platform-data-core` § Query Execution); иначе `"не проверялось"`. Дебаггер сам проверит.

### 4.3 `debug_trigger` — как дебаггеру инициировать код

`debug_trigger` описывает, как воспроизвести выполнение после того, как Debugger поставит breakpoint или подготовит trace.

Заполнять:

- `context` — где исполняется основной код: `client`, `server`, `unknown`.
- `preferred_method` — самый узкий способ запуска: `yaxunit`, `vanessa`, `ui_mcp`, `mcp_tool`, `http`, `scheduled_job`, `unknown`.
- `run_after_breakpoint` — что именно запускать после установки breakpoint: команда, тест, сценарий, UI-действие, tool-вызов.
- `entry_point` — модуль/процедура/строка, если репортёр знает предполагаемую точку входа.
- `target_hint` — пользователь, номер сеанса ИБ, тип клиента или другие признаки target, если видны из прогона.
- `timeout_hint_seconds` — 30 для быстрого кода; для тяжёлой операции указать осознанный предел или `null` с пояснением в `self_fix_attempts`.

Если способ запуска неизвестен, ставить `preferred_method: "unknown"` и объяснить, что именно неизвестно. Не выдумывать target или строку breakpoint.

### 4.4 Когда `scenario_context.incomplete: true`

Если репортёр не может заполнить контекст полностью (например, Developer-Code не видит, как тест готовит документ):

- Указать `incomplete: true` и `incomplete_reason` (что именно не удалось установить).
- Заполнить тот минимум, который виден (например, только `function_call`).
- Дебаггер первым делом сам реконструирует контекст.

**Лучше неполный отчёт с пометкой `incomplete`, чем выдуманные данные.**

---

## 5. Протоколы заполнения по агентам

### 5.1 `developer-code` (Phase 3d)

**Триггер:** unit-тест не проходит, причина не в моём коде ИЛИ 2 self-fix попытки исчерпаны.

Заполнение:
- `symptom.what_ran` — имя теста + полный путь.
- `symptom.error_message` — assertion verbatim из stdout/event-log.
- `expectation.source` — секция спеки или ассерт-строка из теста.
- `scenario_context.input_data.kind = "function_call"` если упал unit на конкретной функции; добавить `document` если тест прогоняется на документе.
- `debug_trigger.preferred_method = "yaxunit"`; `run_after_breakpoint` — команда запуска одного теста; `entry_point` — функция/процедура из падающего стека, если известна.
- `hypotheses` — если подозрение на test/data/scenario/step, указать с reasoning.
- `context.files_touched_this_phase` — все BSL/XML, изменённые в Phase 3d.

### 5.2 `tester` (Phase 4)

**Триггер:** после 3 попыток исправить тест не помогло ИЛИ падение не лечится правкой теста.

Заполнение:
- Полный `scenario_context` — Tester видит сценарий end-to-end и обязан заполнить максимум.
- `symptom.what_ran` — имя теста / `.feature` / scenario name.
- `expectation.source` — спека ИЛИ Acceptance Scenario из спеки ИЛИ ассерт.
- `debug_trigger` — заполнить по фактическому способу запуска: `yaxunit` для unit, `vanessa` для сценария, `ui_mcp` если действие воспроизводилось через тестовый клиент.
- `hypotheses` — текущая классификация Tester (`test_error` / `implementation_error` / `spec_mismatch`) перекладывается в `hypotheses[].layer`.
- `self_fix_attempts` — все 3 попытки с описанием что меняли и результатом.

### 5.3 `scenario-coder` (Phase 3c)

**Триггер:**
- Red-гейт зелёный без прод-кода (мок не очевиден), или
- Шаг падает с неочевидной причиной после 2 попыток.

Заполнение:
- `symptom.what_ran` — имя `.feature` + конкретный сценарий + шаг.
- `expectation.source` — Acceptance Scenario из спеки + ожидаемое поведение Red-гейта (должен быть красным).
- `scenario_context.action` — что делает сценарий (Given-блоки `.feature` дают данные).
- `scenario_context.input_data` — из Given-шагов сценария.
- `debug_trigger.preferred_method = "vanessa"`; если шаг реализован через клиентские UI-действия — добавить target_hint тест-клиента, если он известен.
- `hypotheses` — например `layer: step` если подозрение на скрытый мок в реализации шага.

---

## 6. Жизненный цикл bug-report

| Статус | Кто меняет | Когда |
|---|---|---|
| `open` | reporter | При создании |
| `in_investigation` | orchestrator | При запуске debugger |
| `fixed_locally` | debugger | После локального фикса + верификации |
| `returned_to_author` | debugger | Если фикс масштабный, возврат профильному агенту |
| `escalated_to_user` | orchestrator | После исчерпания гипотез или 2-х циклов bug→fix→bug |

**Контроль дублей:** если тот же симптом (совпадение `symptom.fail_location` + `symptom.error_message`) — обновляется существующий bug-report (новый `self_fix_attempts` запись, новые `hypotheses`), новый НЕ создаётся.

---

## 7. Антипаттерны

| Антипаттерн | Почему плохо |
|---|---|
| `error_message` пересказан своими словами | Теряются точные signature и stack trace |
| `expectation.quote` отсутствует или «ну, по логике…» | Нет источника правды → дебаггер не знает, с чем сравнивать |
| Дамп всего объекта в `scenario_context` | Засоряет отчёт, может содержать чувствительные данные |
| Гипотеза без `reasoning` | Шум, дебаггер не может расставить приоритет |
| `self_fix_attempts: []` пустой | Не было даже попытки разобраться → значит не баг для дебаггера |
| Создание нового bug-report при том же симптоме | Дубли мешают трекингу; обновлять существующий |
| `scenario_context` с выдуманными данными вместо `incomplete: true` | Дебаггер пойдёт по ложному следу |
| `debug_trigger` пустой при известном способе запуска | Дебаггер тратит время на восстановление того, что репортёр уже знает |

---

depends_on:
  - framework/rules/source-of-truth/SKILL.md
---

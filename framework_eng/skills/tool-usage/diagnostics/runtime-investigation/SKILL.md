---
name: runtime-investigation
description: "Runtime investigation algorithm for bug-report: call graph + key variables → probes → trace → hypothesis loop."
---

# Runtime Investigation — investigating bugs at runtime

## 1. When to use

The purpose of the skill is to answer three questions in strict order:

1. **What actually happens?** Is the procedure called? With what arguments? What are the variable values? Which if/else branch is taken? What did the query return?
2. **Does this match the expectation?** (from the spec/design/test assert — `bug-report.expectation`)
3. **Where is the source of the discrepancy?**
   - **The code is wrong** — the behavior does not match the requirement
   - **The code is correct, the data is wrong** — the contract is violated on the caller/data-preparation side
   - **The code matches the spec, the spec is wrong/incomplete**
   - **The test/scenario checks the wrong thing**

Without step 1, steps 2-3 are impossible.

**Launch trigger:** the orchestrator passed `bug-report.json` with status `open`.

---

## 2. Tool hierarchy (from cheap to expensive)

| Level | Tool | When |
|---|---|---|
| **L0** | Reading source code + specs/design (`code-navigation`) | Always first |
| **L1** | `event-log-analysis` — event log via ClickHouse | A run has already completed, and there is an Error/Warning |
| **L2** | `platform-data-core` § Query Execution — database queries | Check the data state independently of the code |
| **L3** | `agent-debug` points in code | L0-L2 did not answer: call fact, if/else path, variable value/type |
| **L4** | Rerunning the scenario/test after insertions | After L3 — collect observations |
| **L5** | `gui-control` + `screenshot` | The symptom is in the UI; it is unclear what is on the form |
| **L6** | `syntax-checking` (`get_diagnostics` / `v8-runner syntax …`) | After any code change |
| **L7** | `tech-log-analysis` — technical log | **ONLY with the user's explicit consent.** Heavy, slow. When L0-L6 did not answer: locks, deadlock, hidden platform exceptions, slow SQL |

The debugger uses L0-L6 autonomously. Moving to L7 requires going back to the orchestrator with a **structured request**:
- Which hypothesis cannot be checked through L0-L6 and why
- Which technical log events are needed (EXCP / DBMSSQL / TLOCK / TDEADLOCK / TTIMEOUT / CALL)
- Approximate collection time

The orchestrator asks the user again. Without consent — DO NOT raise it.

---

## 3. Full algorithm

```
ФАЗА 1. Подготовка
  1.1  Прочитать bug-report.json. Перевести status → in_investigation.
  1.2  Воспроизвести баг детерминированно (запустить указанный тест/сценарий).
       - Не воспроизводится → flaky, эскалация оркестратору.
  1.3  Прочитать код вокруг точки симптома + спеку/дизайн (L0).
  1.4  Построить ГРАФ ВЫЗОВОВ от точки входа сценария/теста до точки симптома (см. §4).
  1.5  Выделить КЛЮЧЕВЫЕ ПЕРЕМЕННЫЕ (см. §5).

ФАЗА 2. Первая проходка (БЕЗ гипотез)
  2.1  Расставить пробы H0 на каждом узле графа (префикс `AGENTDEBUG-<bug-id>-H0-NNN`):
       - маркер EXECUTED
       - снимок ключевых переменных (безопасная сериализация — §6)
  2.2  Прогнать сценарий/тест.
  2.3  Прочитать ЖР, собрать трассу: какие узлы прошли, состояние переменных.
       Сохранить в task_dir/.context/debug/<bug-id>/trace-run-1.md.
  2.4  Сравнить трассу с ожиданием. Локализовать первое расхождение «ожидание ≠ факт».
       Если трассы достаточно, чтобы сразу определить причину → переход к Фазе 4.

ФАЗА 3. Цикл гипотез (≤ 5 итераций; +3 расширение, max 8 — см. §7)
  Для гипотезы N (1..5, при расширении 6..8):

    3.N.1  Сформулировать наиболее вероятную гипотезу НА ОСНОВЕ ТЕКУЩЕЙ ТРАССЫ
           (не из головы). Записать в debug-report.md:
           - формулировка
           - evidence_from_trace (на каком факте из трассы основана)

    3.N.2  Выбрать способ проверки:
           (a) пробный фикс — узкое изменение в коде/тесте/сценарии,
               которое легко откатить;
           (b) дополнительные пробы (префикс `AGENTDEBUG-<bug-id>-H<N>-NNN`) —
               новые ключевые переменные, узлы между размеченными,
               состояние данных через platform-data-core § Query Execution.

    3.N.3  Применить, прогнать, прочитать трассу. Сохранить trace-run-<N+1>.md.

    3.N.4  Развилка:
           ✓ ПОДТВЕРЖДЕНА → переход к Фазе 4 (фикс по правилам)
           ✗ НЕ подтверждена:
               - откатить пробный фикс (если был)
               - снять пробы ИМЕННО ЭТОЙ гипотезы (grep H<N>); пробы H0 и
                 предыдущих опровергнутых гипотез ОСТАЮТСЯ
               - зафиксировать в debug-report.md: что проверял, результат,
                 почему опровергнута
               - переход к гипотезе N+1

  Между итерациями допустимо вернуться к Фазе 1 и расширить граф/ключевые
  переменные, добавив новые H0+ пробы (например, появились новые вызывающие
  места). Это не считается отдельной гипотезой.

  После 5 неподтверждённых:
    - если есть конкретная следующая гипотеза с высокой уверенностью →
      обратиться к оркестратору с запросом на расширение +3 (max 8 всего)
    - иначе → Фаза 5 (эскалация)

ФАЗА 4. Фикс (если гипотеза подтвердилась)
  4.1  Оценить масштаб по критерию «локальный vs возврат» (§8).
  4.2  Локальный → применить фикс, прогнать упавший тест/сценарий + смежные.
       - Должно стать зелёным
       - Если не стало — это была ошибочная гипотеза, вернуться в 3.N.4 с откатом
  4.3  Масштабный → возврат оркестратору с пояснением и рекомендацией
       (какому агенту передать).

ФАЗА 5. Эскалация (5/8 гипотез исчерпаны или масштаб слишком большой)
  5.1  Краткий структурированный отчёт оркестратору (см. §9).
  5.2  Оркестратор передаёт пользователю.

ФАЗА 6. Очистка (ВСЕГДА перед завершением — успехом или эскалацией)
  6.1  grep `//[AGENTDEBUG-` → ноль вхождений во ВСЕХ затронутых файлах.
  6.2  Если поднимали техжурнал — восстановить исходный конфиг.
  6.3  syntax-checking по затронутым модулям.
  6.4  Финальный debug-report.md с итоговым статусом и обновление
       bug-report.json (status: fixed_locally / returned_to_author / escalated_to_user).
```

---

## 4. Building the call graph

The starting point is the location of the observed symptom (failed assert, exception, incorrect value from `bug-report.symptom.fail_location`).

**Method:** go BACKWARD from the symptom up the stack:
- Which procedure called it?
- Who called that?
- ... until the scenario/test entry point.

**Tools:** `code-navigation` (symbol navigation), reading the module, searching for `Call` / `Execute` / form event handlers / manager export procedures.

**Result:** a list of graph nodes in the form:
```
[Тест.МойТест]
  → [Документ.РасходТовара.Объект.ОбработкаПроведения]
    → [ОбщийМодуль.РассчитатьСкидку]
      → [ОбщийМодуль.ПолучитьКатегориюКлиента]  ← точка симптома
```

Save as `task_dir/.context/debug/<bug-id>/call-graph.md`.

---

## 5. Extracting key variables

**Definition:** a key variable is one that influences:
1. The execution condition of the problem point (it appears in `If/Else/While/For` on the path to the symptom), or
2. The result of the calculation at the problem point (it appears in the formula/query/return value), or
3. The branching higher up the stack that leads to this point.

**Extraction method — backward traversal:**

1. At the symptom point: which variables participate in the assert/formula? → key.
2. Up the graph: which variables participate in the conditions leading to this point? → key.
3. Procedure parameters that are passed and transformed along the path → key.
4. Global session parameters (current user, validity date, active organization) — **key by default**, unless proven otherwise.

**Not key:** local variables used only for intermediate calculation without affecting branching and not returned.

Save as `task_dir/.context/debug/<bug-id>/instrumentation-plan.md`: which probes to place where, which key variables in each.

---

## 6. Safe serialization when logging

In `agent-debug` probes, record variable values. **DO NOT dump them in full:**

| Type | What NOT to log | What to log instead |
|---|---|---|
| Документ/Справочник Object | The entire object | `ТипЗнч`, `Ссылка`, relevant attributes one by one |
| ТаблицаЗначений | All rows | `Количество()`, fields of the first/problematic row |
| Структура | Serialization | `Количество()`, list of keys separated by commas |
| Соответствие | Serialization | `Количество()`, key-target if looking for a specific one |
| Form object | In full | Specific form attributes one by one |
| Query | Full text | Name, key parameters |
| Метаданные | `Метаданные.X.<everything>` | Only the type name: `Метаданные(Ссылка).Имя` |
| Binary data | Content | `Размер()` |
| Passwords, tokens, PII | Never | Mask or skip |

**Main rule:** log only those object fields that the code actually reads on the path to the symptom (determined by §5). Do not dump the entire object.

**Parameter object as a key variable:** if the key variable is a reference/object, the experiment must be modeled with **exactly the object on which the bug reproduces**. Do not substitute a "similar" one from the database.

---

## 7. Hypothesis limit

**Default: 5 hypotheses.** After the 5th unconfirmed one — escalate.

**+3 extension (8 total max):** allowed once if:
- there is a concrete next hypothesis with **high confidence** (there is direct evidence from the trace),
- the request was sent to the orchestrator with justification,
- the orchestrator approved it.

If confidence is low — DO NOT request an extension, escalate immediately.

**Quality > quantity.** Every hypothesis in `debug-report.md` must have `evidence_from_trace` — which fact from the collected trace it is based on. This blocks "guessing hypotheses".

---

## 8. Criterion for “local fix vs return to orchestrator”

**The debugger fixes it itself if ALL conditions are met:**
- The change is in ≤ 2 production files OR ≤ 1 test/scenario file
- The public API does not change (exported procedures, their signatures)
- The spec and technical design do not change
- It does not affect `protected_paths` from the bug report
- The fix fits into ~30 lines of diff

**Return to the orchestrator in any of the following cases:**
- The spec needs to change → Analyst
- The technical design needs to change or API needs to be added → Architect
- More than 2 files need to be rewritten → Developer-Code
- `.feature` or the step library needs broad changes → Scenario-Author / Scenario-Coder
- The bug is in data and requires revisiting test environment preparation → Developer-Tests or Scenario-Coder

After a local fix — **mandatory verification**:
1. Re-run the failed test/scenario → it must be green.
2. Re-run related module unit tests and Vanessa scenarios with the same task tag.
3. Check that nothing adjacent broke (narrow regression).
4. If verification failed — it was a wrong hypothesis, roll back the fix, return to 3.N.4.

A local fix ALWAYS goes through review (Reviewer scope=`debug` or the artifact-appropriate type) — otherwise it bypasses quality control.

---

## 9. `debug-report.md` template

Saved in `task_dir/.context/debug/<bug-id>/debug-report.md`.

```markdown
# Debug Report — <bug-id>

## Source
- Bug-report: <link to bug-report.json>
- Symptom: <symptom.what_ran> failed at <fail_location>
- Expectation: <expectation.quote> (source: <expectation.source>)

## Reproduction
- Command: <symptom.command>
- Determinism: <yes/no>

## Call Graph
<link to call-graph.md>

## Key Variables
<link to instrumentation-plan.md>

## First Pass (H0)
- Run: <link to trace-run-1.md>
- Discrepancy localization: <graph node + what did not match>

## Hypotheses

### H1: <формулировка>
- Evidence_from_trace: <на каком факте из трассы основана>
- Способ проверки: <фикс / доп.пробы>
- Прогон: <ссылка на trace-run-N.md>
- Результат: ПОДТВЕРЖДЕНА / ОПРОВЕРГНУТА
- Если опровергнута — почему: <...>

### H2: ...
...

## Вердикт
- Класс причины: код / данные / спека / тест/сценарий
- Корневая причина: <...>
- Затронутый слой источников правды (L1-L6): <см. source-of-truth-policy>

## Действие
- ВАРИАНТ A — Локальный фикс:
  - Файл(ы): <...>
  - Дифф: ≤ 30 строк
  - Верификация: упавший тест зелёный, смежные тесты зелёные
  - Подлежит ревью: scope=debug
- ВАРИАНТ B — Возврат оркестратору:
  - Кому передать: <agent>
  - Почему масштаб большой: <...>
  - Рекомендация по фиксу: <...>
- ВАРИАНТ C — Эскалация:
  - 5/8 гипотез не подтверждены
  - Что точно установлено: <...>
  - Что хотелось бы проверить, но не получилось: <...>
  - Рекомендация: к кому идти (Architect / Analyst / пользователь)

## Очистка
- [x] grep `//[AGENTDEBUG-` → 0 вхождений
- [x] техжурнал восстановлен (если поднимался)
- [x] syntax-checking пройден
```

---

## 10. Antipatterns

| Antipattern | Consequence |
|---|---|
| Hypothesis without `evidence_from_trace` | Guessing; investigation resources are wasted |
| Failing to remove probes from a disproven hypothesis before the next one | Noise in the trace, confusion in interpretation |
| Leaving a trial fix in place after a disproven hypothesis | Accumulation of garbage in the code |
| Dumping the entire object at an `agent-debug` point | Event log overflow, data leakage |
| Replacing the test object with a "similar" one from the database | The bug will not reproduce, false negative result |
| Raising the technical log without the user's consent | Policy violation; expensive process for nothing |
| 10+ H0 probes without clear key variables | Broad observation, unclear result → split into hypotheses |
| Skipping cleanup before completion | `AGENTDEBUG` markers will end up in the commit |
| Skipping verification after a local fix | False "fixed"; in reality, adjacent functionality was broken |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/agent-debug/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
---

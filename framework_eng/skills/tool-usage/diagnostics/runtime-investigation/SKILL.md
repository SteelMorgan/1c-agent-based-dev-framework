---
name: runtime-investigation
description: "Runtime bug diagnosis: call graph, DAP, tracing"
---

# Runtime Investigation — investigating bugs in runtime

## 1. When to use

The goal of the skill is to answer three questions in strict order:

1. **What is actually happening?** Is the procedure being called? With what arguments? What are the variable values? What path through if/else? What did the query return?
2. **Does this match the expectation?** (from the spec/design/test assertion — `bug-report.expectation`)
3. **Where is the source of the discrepancy?**
   - **The code is wrong** — behavior does not match the requirement
   - **The code is correct, the data is wrong** — the contract is violated on the caller/data-preparation side
   - **The code matches the spec, the spec is wrong/incomplete**
   - **The test/scenario checks the wrong thing**

Without step 1, steps 2-3 are impossible.

**Launch trigger:** the orchestrator passed `bug-report.json` with status `open`.

---

## 2. Tool hierarchy (from cheap to expensive)

## 2a. Platform is the last hypothesis

During runtime diagnosis, by default assume that the 1C platform is working correctly until proven otherwise by a minimal reproducible case independent of the project, generated artifacts, and auxiliary tools.

In practice, most failures in agent work happen at one of three levels before the platform:
- the agent changed the wrong code, data, scenario, or expectation;
- the generated artifact is structurally incomplete or non-canonical;
- the auxiliary tool (`xml-gen`, `v8-runner`, test harness, MCP bridge, packaging script) generated or executed something other than what the agent intended.

Therefore, a hypothesis like "platform error or instability" cannot be the first explanation. Before escalating to the platform level, check the generated XML/MXL/EPF/ERF skeleton against the baseline exported by the Configurator, verify the required metadata links such as `DefaultForm` and `ChildObjects`, and, if possible, reproduce the same symptom without the auxiliary tool. Only a reduced example that still fails after removing the agent and tool layers can be considered a platform defect.

| Level | Tool | When |
|---|---|---|
| **L0** | Reading source + specs/design (`code-navigation`) | Always first |
| **L1** | `event-log-analysis` — Event log via ClickHouse | A run that has already finished, there is an Error/Warning |
| **L2** | `platform-data-core` § Query Execution — queries to the DB | Check data state independently of code |
| **L3** | `dap-bsl-code-debug-procedure` — interactive DAP/MCP debugger | There is a safe reproducible scenario and stack/locals/step are needed at 1-3 points |
| **L4** | `agent-debug` breakpoints in code + event log | DAP is not suitable or a broad trace is needed: call fact, if/else path, variable value/type |
| **L5** | Re-run the scenario/test after DAP/probes | Collect observations |
| **L6** | `gui-control` + `screenshot` | The symptom is in the UI, it is unclear what is on the form |
| **L7** | `syntax-checking` (`get_diagnostics` / `v8-runner syntax …`) | After any code change |
| **L8** | `tech-log-analysis` — tech journal | **ONLY with the user's explicit consent.** Heavy, slow. When L0-L7 did not provide an answer: locks, deadlock, hidden platform exceptions, slow SQL |

L0-L7 are used by the debugger autonomously. Moving to L8 requires going back to the orchestrator with a **structured request**:
- Which hypothesis cannot be checked through L0-L7 and why
- Which tech journal events are needed (EXCP / DBMSSQL / TLOCK / TDEADLOCK / TTIMEOUT / CALL)
- Estimated collection time

The orchestrator asks the user again. Without consent, DO NOT escalate.

---

## 3. Full Algorithm

```
ФАЗА 1. Подготовка
  1.1  Прочитать bug-report.json. Перевести status → in_investigation.
  1.2  Воспроизвести баг детерминированно (запустить указанный тест/сценарий).
       - Не воспроизводится → flaky, эскалация оркестратору.
  1.3  Прочитать код вокруг точки симптома + speck/design (L0).
  1.4  Построить ГРАФ ВЫЗОВОВ от точки входа сценария/теста до точки симптома (см. §4).
  1.5  Выделить КЛЮЧЕВЫЕ ПЕРЕМЕННЫЕ (см. §5).

ФАЗА 2. Первая проходка (БЕЗ гипотез)
  2.1  Выбрать способ runtime-наблюдения:
       - DAP/MCP-отладчик: если безопасно остановить поток и нужно увидеть stack/locals/step.
       - agent-debug + ЖР: если нужна широкая трасса или остановка потока рискованна.
  2.2  Для DAP: поставить breakpoint в ключевой точке, запустить сценарий, poll `wait_for_stop`
       каждые 5 секунд (быстрый код — до 30 секунд; тяжёлый — по заранее заданному пределу),
       записать stack/locals/шаги в trace-run-1.md, затем очистить breakpoint, отпустить поток и выполнить detach.
  2.3  Для agent-debug: расставить пробы H0 на узлах графа
       (префикс `AGENTDEBUG-<bug-id>-H0-NNN`):
       - маркер EXECUTED
       - снимок ключевых переменных (безопасная сериализация — §6)
       Прогнать сценарий/тест.
  2.4  Прочитать ЖР или DAP-наблюдения, собрать трассу: какие узлы прошли, состояние переменных.
       Сохранить в task_dir/.context/debug/<bug-id>/trace-run-1.md.
  2.5  Сравнить трассу с ожиданием. Локализовать первое расхождение «ожидание ≠ факт».
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
  6.1  Если использовался DAP: `clear_breakpoints`, безопасный `continue`, `detach`;
       при `ibInDebug`/зависшей сессии — `force_detach` и повторная проверка targets.
  6.2  grep `//[AGENTDEBUG-` → ноль вхождений во ВСЕХ затронутых файлах.
  6.3  Если поднимали техжурнал — восстановить исходный конфиг.
  6.4  syntax-checking по затронутым модулям.
  6.5  Финальный debug-report.md с итоговым статусом и обновление
       bug-report.json (status: fixed_locally / returned_to_author / escalated_to_user).
```

---

## 4. Building the Call Graph

The starting point is the location of the observed symptom (failed assert, exception, incorrect value from `bug-report.symptom.fail_location`).

**Method:** walk BACKWARD from the symptom up the stack:
- Which procedure called it?
- Who called that one?
- … up to the scenario/test entry point.

**Tools:** `code-navigation` (symbol navigation), reading the module, searching for `Call` / `Execute` / form event handlers / manager export procedures.

**Result:** a list of graph nodes in the form:
```
[Тест.МойТест]
  → [Документ.РасходТовара.Объект.ОбработкаПроведения]
    → [ОбщийМодуль.РассчитатьСкидку]
      → [ОбщийМодуль.ПолучитьКатегориюКлиента]  ← symptom point
```

Save as `task_dir/.context/debug/<bug-id>/call-graph.md`.

---

## 5. Identifying Key Variables

**Definition:** a key variable is one that affects:
1. The execution condition of the problematic point (appears in `If/Else/While/For` on the path to the symptom), or
2. The result of the calculation at the problematic point (appears in the formula/query/return value), or
3. Branching higher up the stack that leads to this point.

**Identification method - reverse traversal:**

1. At the symptom point: which variables participate in the assert/formula? → key.
2. Up the graph: which variables participate in the conditions leading to this point? → key.
3. Procedure parameters passed and transformed along the path → key.
4. Global session parameters (current user, as-of date, active organization) - **key by default**, unless proven otherwise.

**NOT key:** local variables used only for calculation without affecting branching and not returned.

Save as `task_dir/.context/debug/<bug-id>/instrumentation-plan.md`: which probes go where, which key variables are in each.

---

## 6. Safe Serialization for Logging

In `agent-debug` probes, record variable values. **Do NOT dump them in full:**

| Type | What NOT to log | What to log instead |
|---|---|---|
| Document/Catalog Object | The entire object | `ТипЗнч`, `Ссылка`, relevant attributes one by one |
| ValueTable | All rows | `Количество()`, fields of the first/problem row |
| Structure | Serialization | `Количество()`, list of keys separated by commas |
| Map | Serialization | `Количество()`, key-target if looking for a specific one |
| Form object | In full | Specific form attributes one by one |
| Query | Full text | Name, key parameters |
| Metadata | `Метаданные.X.<всё>` | Only the type name: `Метаданные(Ссылка).Имя` |
| Binary data | Contents | `Размер()` |
| Passwords, tokens, PD | Never | Mask or skip |

**Main rule:** log only those object fields that the code actually reads on the path to the symptom (determined by §5). Do not dump the entire object.

**Parameter object as the key variable:** if the key variable is a reference/object, the experiment must model **exactly the object on which the bug reproduces**. Do not substitute a “similar” one from the database.

---

## 7. Hypothesis Limit

**Default: 5 hypotheses.** After the 5th unconfirmed one, escalate.

**Extension +3 (max 8 total):** allowed once if:
- a concrete next hypothesis appears with **high confidence** (there is direct evidence from the trace),
- the request was sent to the orchestrator with justification,
- the orchestrator approved.

If confidence is low, do NOT request an extension; escalate immediately.

**Quality > quantity.** Every hypothesis in `debug-report.md` must have `evidence_from_trace` - the fact from the collected trace on which it is based. This blocks “guesses at random.”

---

## 8. Criterion "local fix vs return to orchestrator"

**Debugger fixes it itself if ALL conditions are met:**
- Change in ≤ 2 prod code files OR ≤ 1 test/scenario file
- Public API does not change (exported procedures, their signatures)
- Spec and technical-design do not change
- Does not affect `protected_paths` from bug-report
- The fix fits into ~30 lines of diff

**Return to orchestrator in any of the following cases:**
- Need to change the spec -> Analyst
- Need to change the technical design or add API -> Architect
- Need to rewrite > 2 files -> Developer-Code
- Need to change `.feature` or step-library broadly -> Scenario-Author / Scenario-Coder
- Bug in data, requires revisiting preparation of the test environment -> Developer-Tests or Scenario-Coder

After a local fix - **mandatory verification**:
1. Rerun the failed test/scenario -> it must be green.
2. Rerun the module's related unit tests and Vanessa scenarios with the same task tag.
3. Check that nothing adjacent broke (narrow regression).
4. If verification failed - the hypothesis was wrong, roll back the fix, return to 3.N.4.

A local fix ALWAYS goes through review (Reviewer scope=`debug` or the corresponding artifact type) - otherwise it bypasses quality control.

---

## 9. `debug-report.md` template

Stored in `task_dir/.context/debug/<bug-id>/debug-report.md`.

```markdown
# Debug Report — <bug-id>

## Source
- Bug-report: <link to bug-report.json>
- Symptom: <symptom.what_ran> failed at <fail_location>
- Expectation: <expectation.quote> (source: <expectation.source>)

## Reproduction
- Command: <symptom.command>
- Determinism: <yes/no>

## Call graph
<link to call-graph.md>

## Key variables
<link to instrumentation-plan.md>

## First pass (H0)
- Run: <link to trace-run-1.md>
- Divergence localization: <graph node + what did not match>

## Hypotheses

### H1: <formulation>
- Evidence_from_trace: <which trace fact it is based on>
- Verification method: <fix / additional probes>
- Run: <link to trace-run-N.md>
- Result: CONFIRMED / DISPROVED
- If disproved - why: <...>

### H2: ...
...

## Verdict
- Cause class: code / data / spec / test/scenario
- Root cause: <...>
- Affected source-of-truth layer (L1-L6): <see source-of-truth-policy>

## Action
- OPTION A - Local fix:
  - File(s): <...>
  - Diff: ≤ 30 lines
  - Verification: failed test is green, adjacent tests are green
  - Subject to review: scope=debug
- OPTION B - Return to orchestrator:
  - Who to hand off to: <agent>
  - Why the scope is large: <...>
  - Fix recommendation: <...>
- OPTION C - Escalation:
  - 5/8 hypotheses not confirmed
  - What is definitely established: <...>
  - What we would like to check but could not: <...>
  - Recommendation: who to go to (Architect / Analyst / user)

## Cleanup
- [x] DAP breakpoints removed, thread released via `continue`/release, `detach` / `force_detach` executed (if DAP was used)
- [x] grep `//[AGENTDEBUG-` -> 0 occurrences
- [x] technlog restored (if it was started)
- [x] syntax-checking passed
```

---

## 10. Anti-patterns

| Anti-pattern | Consequence |
|---|---|
| Hypothesis without `evidence_from_trace` | Guessing; investigation effort wasted |
| Do not remove disproved hypothesis samples before the next one | Trace noise, confusion in interpretation |
| Leave a trial fix in place for a disproved hypothesis | Accumulation of junk in code |
| Dump the entire object in an `agent-debug` point | Tech log overflow, data leak |
| DAP breakpoint left active | Subsequent runs stop in unexpected places |
| `detach`/`force_detach` not performed when `ibInDebug` | The database remains occupied by a debug session |
| Substitute the test object with a "similar" one from the database | The bug will not reproduce, false negative |
| Raise the tech log without user consent | Policy violation; heavy process for nothing |
| 10+ H0 probes without clear key variables | Broad observation, unclear result -> split into hypotheses |
| Skip cleanup before finishing | `AGENTDEBUG` markers will end up in the commit |
| Skip verification after a local fix | False "fixed"; actually broke adjacent things |

---

depends_on:
  - framework/skills/tool-usage/diagnostics/bug-reporting/SKILL.md
  - framework/skills/tool-usage/diagnostics/dap-bsl-code-debug-procedure/SKILL.md
  - framework/skills/tool-usage/diagnostics/agent-debug/SKILL.md
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
  - framework/skills/tool-usage/diagnostics/tech-log-analysis/SKILL.md
  - framework/skills/tool-usage/platform-data/platform-data-core/SKILL.md
  - framework/skills/tool-usage/code-analysis/code-navigation/SKILL.md
  - framework/skills/tool-usage/code-analysis/syntax-checking/SKILL.md
  - framework/rules/dap-bsl-debugger/SKILL.md
  - framework/rules/source-of-truth/SKILL.md
---

---
name: runtime-investigation
description: "Runtime bug diagnostics: call graph, DAP, tracing"
---

# Runtime Investigation — runtime bug investigation

## 1. When to Use

The goal of this skill is to answer three questions in strict order:

1. **What is actually happening?** Is the procedure being called? With which arguments? What are the variable values? What is the if/else path? What did the query return?
2. **Does this match the expectation?** (from spec/design/test assertion - `bug-report.expectation`)
3. **Where is the source of the mismatch?**
   - **The code is wrong** - behavior does not match the requirement
   - **The code is correct, the data is not** - the contract is violated on the caller/preparation side
   - **The code matches the spec, the spec is wrong/incomplete**
   - **The test/scenario checks the wrong thing**

Without step 1, steps 2-3 are impossible.

**Launch trigger:** the orchestrator passed `bug-report.json` with status `open`.

---

## 2. Tool Hierarchy (from cheap to expensive)

| Level | Tool | When |
|---|---|---|
| **L0** | Reading source code + spec/design (`code-navigation`) | Always first |
| **L1** | `event-log-analysis` - event log through ClickHouse | An already executed run, there is Error/Warning |
| **L2** | `platform-data-core` § Query Execution - database queries | Check data state independently of the code |
| **L3** | `dap-bsl-code-debug-procedure` - interactive DAP/MCP debugger | There is a safe reproducible scenario and stack/locals/step at 1-3 points are needed |
| **L4** | `agent-debug` markers in code + event log | DAP is not suitable or a broad trace is needed: call fact, if/else path, variable value/type |
| **L5** | Re-run the scenario/test after DAP/probes | Collect observations |
| **L6** | `gui-control` + `screenshot` | The symptom is in the UI, it is unclear what is on the form |
| **L7** | `syntax-checking` (`get_diagnostics` / `v8-runner syntax …`) | After any code change |
| **L8** | `tech-log-analysis` - technical log | **ONLY with the user's explicit consent.** Heavy, slow. When L0-L7 did not produce an answer: locks, deadlock, hidden platform exceptions, slow SQL |

The L0-L7 debugger is used autonomously. Moving to L8 requires going back to the orchestrator with a **structured request**:
- Which hypothesis cannot be checked through L0-L7 and why
- Which technical log events are needed (EXCP / DBMSSQL / TLOCK / TDEADLOCK / TTIMEOUT / CALL)
- Approximate collection time

The orchestrator asks the user again. Without consent - DO NOT raise it.

---

## 3. Full Algorithm

```
ФАЗА 1. Подготовка
  1.1  Прочитать bug-report.json. Перевести status → in_investigation.
  1.2  Воспроизвести баг детерминированно (запустить указанный тест/сценарий).
       - Не воспроизводится → flaky, эскалация оркестратору.
  1.3  Прочитать код вокруг точки симптома + спеку/дизайн (L0).
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

**Method:** go BACKWARD from the symptom up the stack:
- Which procedure called it?
- Who called that one?
- ... up to the scenario/test entry point.

**Tools:** `code-navigation` (symbol navigation), reading the module, search for `Call` / `Execute` / form event handlers / manager export procedures.

**Result:** a list of graph nodes in the form:
```
[Test.MyTest]
  -> [Document.GoodsIssue.Object.PostingRoutine]
    -> [CommonModule.CalculateDiscount]
      -> [CommonModule.GetCustomerCategory]  <- symptom point
```

Save as `task_dir/.context/debug/<bug-id>/call-graph.md`.

---

## 5. Identifying Key Variables

**Definition:** a key variable is one that affects:
1. The execution condition of the problematic point (enters `If/Else/While/For` on the path to the symptom), or
2. The result of the computation at the problematic point (participates in the formula/query/return value), or
3. Branching higher up the stack that leads to this point.

**Identification method - reverse traversal:**

1. At the symptom point: which variables participate in the assert/formula? -> key.
2. Up the graph: which variables participate in the conditions leading to this point? -> key.
3. Procedure parameters passed and transformed along the path -> key.
4. Global session parameters (current user, relevance date, active organization) - **key by default**, unless proven otherwise.

**NOT key:** local variables used only for calculation without affecting branching and not returned.

Save as `task_dir/.context/debug/<bug-id>/instrumentation-plan.md`: which probes are placed where, which key variables are in each.

---

## 6. Safe Serialization for Logging

In `agent-debug` probes, record variable values. **Do NOT dump them wholesale:**

| Type | What NOT to log | What to log instead |
|---|---|---|
| Document/Catalog Object | The entire object | `TypeOf`, `Ref`, relevant attributes one by one |
| ValueTable | All rows | `Count()`, fields of the first/problematic row |
| Structure | Serialization | `Count()`, list of keys separated by commas |
| Map | Serialization | `Count()`, key-target if looking for a specific one |
| Form object | As a whole | Specific form attributes one by one |
| Query | Full text | Name, key parameters |
| Metadata | `Metadata.X.<all>` | Only the type name: `Metadata(Ref).Name` |
| Binary data | Contents | `Size()` |
| Passwords, tokens, personal data | Never | Mask or skip |

**Main rule:** log only those object fields that the code actually reads on the path to the symptom (determined by §5). Do not dump the entire object.

**Parameter-object as a key variable:** if the key variable is a reference/object, the experiment must be modeled with **the exact object on which the bug reproduces**. Do not substitute a "similar" one from the database.

---

## 7. Hypothesis Limit

**Default: 5 hypotheses.** After the 5th unconfirmed one - escalation.

**+3 extension (max 8 total):** allowed once if:
- there is a concrete next hypothesis with **high confidence** (there is direct evidence from the trace),
- a request has been sent to the orchestrator with justification,
- the orchestrator agreed.

If confidence is low - DO NOT request an extension, escalate immediately.

**Quality over quantity.** Each hypothesis in `debug-report.md` must have `evidence_from_trace` - which fact from the collected trace it is based on. This blocks "guesswork hypotheses".

---

## 8. "Local Fix vs Return to Orchestrator" Criterion

**The debugger fixes it themselves if ALL conditions are met:**
- Change in <= 2 prod-code files OR <= 1 test/scenario file
- Public API does not change (exported procedures, their signatures)
- Spec and technical design do not change
- Does not affect `protected_paths` from bug-report
- Fix fits within ~30 lines of diff

**Return to the orchestrator in any of these cases:**
- The spec needs to change -> Analyst
- The technical design needs to change or an API needs to be added -> Architect
- More than 2 files need to be rewritten -> Developer-Code
- `.feature` or step-library need broad changes -> Scenario-Author / Scenario-Coder
- The bug is in data and requires revising the test environment preparation -> Developer-Tests or Scenario-Coder

After a local fix - **mandatory verification**:
1. Re-run the failed test/scenario -> must be green.
2. Re-run related unit tests for the module and Vanessa scenarios with the same task tag.
3. Check that nothing adjacent broke (narrow regression).
4. If verification failed - it was a wrong hypothesis, roll back the fix, return to 3.N.4.

A local fix ALWAYS goes through review (Reviewer scope=`debug` or the corresponding artifact type) - otherwise it bypasses quality control.

---

## 9. `debug-report.md` Template

Saved to `task_dir/.context/debug/<bug-id>/debug-report.md`.

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
- Mismatch localization: <graph node + what did not match>

## Hypotheses

### H1: <formulation>
- Evidence_from_trace: <which fact from the trace it is based on>
- Verification method: <fix / additional probes>
- Run: <link to trace-run-N.md>
- Result: CONFIRMED / DISPROVED
- If disproved - why: <...>

### H2: ...
...

## Verdict
- Cause class: code / data / spec / test-scenario
- Root cause: <...>
- Affected truth-source layer (L1-L6): <see source-of-truth-policy>

## Action
- OPTION A - Local Fix:
  - File(s): <...>
  - Diff: <= 30 lines
  - Verification: failed test green, related tests green
  - Requires review: scope=debug
- OPTION B - Return to orchestrator:
  - Who to hand off to: <agent>
  - Why the scope is large: <...>
  - Fix recommendation: <...>
- OPTION C - Escalation:
  - 5/8 hypotheses not confirmed
  - What was established for sure: <...>
  - What we wanted to check but could not: <...>
  - Recommendation: who to go to (Architect / Analyst / user)

## Cleanup
- [x] DAP breakpoints removed, thread released through `continue`/release, `detach` / `force_detach` performed (if DAP was used)
- [x] grep `//[AGENTDEBUG-` -> 0 matches
- [x] technical log restored (if it was enabled)
- [x] syntax-checking passed
```

---

## 10. Anti-Patterns

| Anti-pattern | Consequence |
|---|---|
| Hypothesis without `evidence_from_trace` | Guessing; investigation resources are wasted |
| Not removing probes of a disproved hypothesis before the next one | Noise in the trace, confusion in interpretation |
| Leaving a trial fix in place after the hypothesis was disproved | Accumulation of junk in the code |
| Dumping the whole object in an `agent-debug` point | Event log overflow, data leak |
| DAP breakpoint left active | Subsequent runs stop at unexpected places |
| `detach`/`force_detach` not performed when `ibInDebug` | The database remains occupied by the debug session |
| Replacing the test object with a "similar" one from the database | The bug will not reproduce, false negative |
| Raising the technical log without the user's consent | Policy violation; heavy process for nothing |
| 10+ H0 probes without clear key variables | Broad observation, unclear result -> split into hypotheses |
| Skipping cleanup before finishing | `AGENTDEBUG` markers will end up in the commit |
| Skipping verification after a local fix | False "fixed", while adjacent behavior actually broke |

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

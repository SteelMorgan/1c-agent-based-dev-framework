---
name: agent-debug
description: Pattern of debug messages for 1С BSL. Use when standard diagnostics (event-log, screenshots) do not reveal the actual system behavior — you need to insert temporary logging points into code, run a test, and analyze the ЖР entries.
---

# Debug messages (Agent Debug)

## When to use

| Trigger | Action |
|---------|--------|
| Standard diagnostics do not help understand behavior | Insert debug points |
| Hypothesis about the root cause needs confirmation/refutation | Log key values |
| It is unclear which branch of code is executing | Place markers across branches |

**Do not use** if the answer can be obtained by reading the code, the event-log, or a screenshot.

---

## Debug block format

```bsl
//[AGENTDEBUG-001]
ЗаписьЖурналаРегистрации("AgentDebug",
    УровеньЖурналаРегистрации.Информация, , ,
    "STEP=001 PROC=ОбработкаПроведения MSG=Проверка суммы"
    + " | СуммаДокумента=" + Строка(СуммаДокумента)
    + " | Статус=" + Строка(Статус));
///[AGENTDEBUG-001]
```

### Marker rules

- Opening: `//[AGENTDEBUG-NNN]`
- Closing: `///[AGENTDEBUG-NNN]` (three slashes)
- NNN is the sequential point number (001, 002, ...)
- Only debug code between markers. No production code inside the block.
- Nested blocks are forbidden.

### ЗаписьЖурналаРегистрации parameters

| Parameter | Value | Why |
|-----------|-------|-----|
| ИмяСобытия | `"AgentDebug"` | Filtering: all debug entries with one query |
| Уровень | `Информация` | Persisted reliably in the ЖР (note may be omitted) |
| МетаданныеОбъекта | `Неопределено` or a specific object | Specify if obvious for additional filtering |
| Данные | Reference to an object or `Неопределено` | For correlation with a specific document/element |
| Комментарий | `STEP=NNN PROC=... MSG=... \| key=value` | Structured format, easy to parse |

### Comment format

```
STEP=001 PROC=ОбработкаПроведения MSG=Brief description of the hypothesis | Key1=Value1 | Key2=Value2
```

- `STEP` is the point number (matches the marker)
- `PROC` is the name of the procedure/function
- `MSG` describes what is being verified (the hypothesis)
- After `|` list key values in `key=value` format
- Do not log: large structures, value tables, binary data, passwords

---

## Procedure

1. **Formulate the hypothesis** — what exactly is being verified and why
2. **Identify points** — where in the code to insert logging (1-3 per hypothesis, max 5)
3. **Insert debug blocks** with markers `//[AGENTDEBUG-NNN]` ... `///[AGENTDEBUG-NNN]`
4. **Run the test** — unit test or Vanessa scenario
5. **Read the ЖР** — filter by `ИмяСобытия = "AgentDebug"`, sort by time
6. **Draw the conclusion** — hypothesis confirmed or refuted
7. **Remove ALL debug blocks** (see cleanup checklist)

If one iteration is not enough — adjust the points and repeat (steps 2-6).
If 10+ points are needed — the hypothesis is too broad; split it into several.

---

## Where to insert

Search order for a suitable place:

1. **Form module** — event handlers, ПриИзменении, ПередЗаписью
2. **Object module** — ОбработкаПроведения, ПередЗаписью, ПриЗаписи
3. **Manager module** — if logic resides in a manager
4. **Common modules** — if the call goes into a common module

Delegating code study to a subagent (Explorer / `code-navigation`) is preferable.

---

## Cleanup checklist

**MUST** before finishing the task:

1. Search the code for `AGENTDEBUG` — no occurrences should remain
2. Verify that only the lines between the markers were removed; production code is untouched
3. Check the modules' syntax after the removal
4. Ensure both marker comments are deleted (opening and closing)

**Line-by-line removal:**
- Find the line with `//[AGENTDEBUG-NNN]`
- Delete every line up to and including the matching `///[AGENTDEBUG-NNN]`
- If a matching marker is not found — **STOP**, report the error

---

## Anti-patterns

| Anti-pattern | Consequence |
|--------------|-------------|
| Production code inside the debug block | Removing the block breaks business logic |
| Debug blocks left in the final code | ЖР pollution, data leakage |
| 10+ points for one hypothesis | Hypothesis is too broad; results unclear |
| Logging value tables / large structures | ЖР overflow, slowdown |
| Free text instead of key=value | Hard to parse during analysis |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---

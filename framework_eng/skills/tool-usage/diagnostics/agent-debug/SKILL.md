---
name: agent-debug
description: "Debug-message pattern for 1С BSL. Use when standard diagnostics (event-log, screenshots) do not reveal the actual system behavior - you need to insert temporary logging points into code, run a test, and analyze the registration log entries."
alwaysApply: false
---

# Debug messages (Agent Debug)

## When to use

| Trigger | Action |
|---------|--------|
| Standard diagnostics do not reveal the behavior | Insert debug points |
| A hypothesis about the root cause needs confirmation/refutation | Log key values |
| It is unclear which branch of code is being executed | Place markers across branches |

**Do not use** if the answer can be obtained by reading the code, event-log, or a screenshot.

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
- Between markers - **ONLY** debug code. No production code inside the block
- Nested blocks are forbidden

### Parameters for ЗаписьЖурналаРегистрации

| Parameter | Value | Why |
|-----------|-------|-----|
| ИмяСобытия | `"AgentDebug"` | Filtering: all debug entries with one query |
| Уровень | `Информация` | Persisted reliably in the registration log (note may not be saved) |
| МетаданныеОбъекта | `Неопределено` or a specific object | If obvious, specify it for additional filtering |
| Данные | Reference to an object or `Неопределено` | For correlation with a specific document/element |
| Комментарий | `STEP=NNN PROC=... MSG=... \| key=value` | Structured format, easy to parse |

### Comment format

```
STEP=001 PROC=ОбработкаПроведения MSG=Brief description of the hypothesis | Key1=Value1 | Key2=Value2
```

- `STEP` is the point number (matches the marker)
- `PROC` is the name of the procedure/function
- `MSG` is what is being verified (the hypothesis)
- After `|` - key values in `key=value` format
- Do not log: large structures, value tables, binary data, passwords

---

## Procedure

1. **Formulate the hypothesis** - what exactly is being verified and why
2. **Identify points** - where in the code to insert logging (1-3 per hypothesis, max 5)
3. **Insert debug blocks** with markers `//[AGENTDEBUG-NNN]` ... `///[AGENTDEBUG-NNN]`
4. **Run the test** - unit test or Vanessa scenario
5. **Read the registration log** - filter by `ИмяСобытия = "AgentDebug"`, sort by time
6. **Draw the conclusion** - hypothesis confirmed or refuted
7. **Remove ALL debug blocks** (see cleanup checklist)

If one iteration is not enough - adjust the points and repeat (steps 2-6).
If 10+ points are needed - the hypothesis is too broad; split it into several.

---

## Where to insert

Search order for a suitable place:

1. **Form module** - event handlers, ПриИзменении, ПередЗаписью
2. **Object module** - ОбработкаПроведения, ПередЗаписью, ПриЗаписи
3. **Manager module** - if the logic is in a manager
4. **Common modules** - if the call goes into a common module

It is preferable to delegate code inspection to a subagent (Explorer / `code-navigation`).

---

## Cleanup checklist

**MUST** before finishing the task:

1. Search the code for `AGENTDEBUG` - no occurrences should remain
2. Check that only the lines between the markers were removed, and production code was not touched
3. Check module syntax after removal
4. Make sure the marker comments are also removed (opening and closing)

**Line-by-line removal:**
- Find the line with `//[AGENTDEBUG-NNN]`
- Delete all lines up to and including the matching `///[AGENTDEBUG-NNN]`
- If the matching marker is not found - **STOP**, report the error

---

## Anti-patterns

| Anti-pattern | Consequence |
|--------------|-------------|
| Production code inside the debug block | Removing the block breaks business logic |
| Debug blocks left in the final code | Registration log pollution, data leakage |
| 10+ points for one hypothesis | The hypothesis is too broad; the result is unclear |
| Logging value tables / large structures | Registration log overflow, slowdown |
| Free text instead of key=value | Hard to parse during analysis |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---

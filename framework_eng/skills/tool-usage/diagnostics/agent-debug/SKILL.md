---
name: agent-debug
description: "Trace BSL when the event log/screenshots do not explain the failure"
alwaysApply: false
---

# Debug Messages (Agent Debug)

## When to Use

| Trigger | Action |
|---------|----------|
| Standard diagnostics do not explain the behavior | Insert debug points |
| A hypothesis about the cause of the error needs confirmation or refutation | Log key values |
| It is unclear which code branch is executing | Place markers on branches |

**DO NOT** use if the answer can be obtained by reading the code, event log, or a screenshot.

---

## Debug Block Format

```bsl
//[AGENTDEBUG-001]
ЗаписьЖурналаРегистрации("AgentDebug",
    УровеньЖурналаРегистрации.Информация, , ,
    "STEP=001 PROC=ОбработкаПроведения MSG=Проверка суммы"
    + " | СуммаДокумента=" + Строка(СуммаДокумента)
    + " | Статус=" + Строка(Статус));
///[AGENTDEBUG-001]
```

### Marker Rules

- Opening: `//[AGENTDEBUG-NNN]`
- Closing: `///[AGENTDEBUG-NNN]` (three slashes)
- NNN is the ordinal number of the point (001, 002, ...)
- Between markers there must be **ONLY** debugging code. No production code inside the block
- Nested blocks are prohibited

### Parameters for ЗаписьЖурналаРегистрации

| Parameter | Value | Why |
|----------|----------|-------|
| ИмяСобытия | `"AgentDebug"` | Filtering: all debug records with one query |
| Уровень | `Информация` | Reliably saved in the event log (Note may not be saved) |
| МетаданныеОбъекта | `Неопределено` or a specific object | If obvious, specify for additional filtering |
| Данные | Object reference or `Неопределено` | For correlation with a specific document/item |
| Комментарий | `STEP=NNN PROC=... MSG=... \| key=value` | Structured format, easy to parse |

### Comment Format

```
STEP=001 PROC=ОбработкаПроведения MSG=Краткое описание гипотезы | Ключ1=Значение1 | Ключ2=Значение2
```

- `STEP` — point number (matches the marker)
- `PROC` — procedure/function name
- `MSG` — what is being checked (hypothesis)
- After `|` — key values in `key=value` format
- Do not log: large structures, value tables, binary data, passwords

---

## Procedure

1. **Formulate the hypothesis** — what exactly we are checking and why
2. **Determine the points** — where to insert logging in the code (1-3 per hypothesis, max 5)
3. **Insert debug blocks** with markers `//[AGENTDEBUG-NNN]` ... `///[AGENTDEBUG-NNN]`
4. **Run the test** — unit test or Vanessa scenario
5. **Read the event log** — filter by `ИмяСобытия = "AgentDebug"`, sort by time
6. **Draw a conclusion** — hypothesis confirmed/refuted
7. **Remove ALL debug blocks** (see cleanup checklist)

If one iteration is not enough, adjust the points and repeat (steps 2-6).
If you need 10+ points, the hypothesis is too broad; split it into several.

---

## Where to Insert

Order of finding a suitable place:

1. **Form module** — event handlers, ПриИзменении, ПередЗаписью
2. **Object module** — ОбработкаПроведения, ПередЗаписью, ПриЗаписи
3. **Manager module** — if the logic is in the manager
4. **Common modules** — if the call goes into a common module

It is preferable to delegate code inspection to a sub-agent (Explorer / `code-navigation`).

---

## Cleanup Checklist

**MUST** before completing the task:

1. Search in code: `AGENTDEBUG` — no occurrences should remain
2. Check that only the lines between markers were removed, production code was not affected
3. Check module syntax after removal
4. Make sure marker comments are also removed (opening and closing)

**Remove line by line:**
- Find the line with `//[AGENTDEBUG-NNN]`
- Remove all lines up to the matching `///[AGENTDEBUG-NNN]` inclusive
- If the matching marker is not found — **STOP**, report an error

---

## Anti-Patterns

| Anti-pattern | Consequence |
|-------------|-------------|
| Production code inside a debug block | Removing the block will break business logic |
| Debug blocks left in the final code | Event log clutter, data leakage |
| 10+ points for one hypothesis | Overly broad hypothesis, unclear result |
| Logging value tables / large structures | Event log overflow, slowdown |
| Free text instead of key=value | Hard to parse during analysis |

---
depends_on:
  - framework/skills/tool-usage/diagnostics/event-log-analysis/SKILL.md
---

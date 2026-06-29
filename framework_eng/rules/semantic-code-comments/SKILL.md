---
name: semantic-code-comments
description: "In comments, explain why, not restate the code"
alwaysApply: true
---

# Semantic Code Comments

> A good comment explains **why**, not **what**. Names, structure, and expressions already show what the code does; a comment is needed for business meaning, constraints, trade-offs, and hidden invariants.

## Principle

Code is written once, but read many times. If a future reader will ask "why is it like this?", "what breaks if we remove it?", "what business rule is protected here?" - a semantic comment is needed.

The goal is not comment density, but removing guesswork when reading complex or non-obvious code.

## What to Comment

### Non-obvious business rules

```bsl
// Скидку применяем только после подтверждения лимита, потому что договор
// может запрещать ретроспективное изменение цены.
Если ЛимитПодтвержден И ДоговорРазрешаетИзменениеЦены Тогда
```

### Protection against external failures and edge cases

```bsl
// The external service sometimes returns an empty amount for a closed period.
// Treat it as zero so the report remains buildable instead of failing on conversion.
Сумма = ?(ЗначениеЗаполнено(Ответ.Сумма), Ответ.Сумма, 0);
```

### Workarounds and trade-offs

```bsl
// Do not use batch write: the write handler must run for each object separately,
// otherwise dependent aggregates will not be updated.
Для Каждого Объект Из Объекты Цикл
```

### Hidden invariants and call order

```bsl
// Important to run before total calculation: this procedure fills a temporary table
// that the next query uses to read period boundaries.
ПодготовитьГраницыПериода(Параметры);
```

### Reasons for rejecting the obvious solution

```bsl
// Do not use a left join: downstream logic requires a mandatory reference
// and has no safe branch for an empty value.
ВНУТРЕННЕЕ СОЕДИНЕНИЕ
```

### Magic numbers and constants

```bsl
МаксимумПопыток = 3; // More than three retries frustrates the user more than it helps during a temporary failure.
```

## What NOT to Comment

### Restating the code

```bsl
// Bad: add A and B.
Сумма = A + B;
```

### Obvious operations

```bsl
// Bad: increment the counter.
Счетчик = Счетчик + 1;
```

### Comments that contradict the code

If you change the code, check the comments nearby. An outdated comment is worse than no comment because it creates false confidence.

## SHOULD

- Before a non-trivial block of complex logic, give a brief explanation of the block's purpose.
- Add a comment about the purpose at the start of a procedure if it is not obvious from the name.
- Refer to a specification, ADR, or task when the reason for the decision is unclear without external context.
- Explain the limitations of external systems, the platform, libraries, and data.
- Comment intentionally strange code: why it looks unusual and what breaks if it is "simplified".

## How to Phrase It

| Good | Bad |
|--------|-------|
| "Do not use X because Y" | "Here is X" |
| "Protection against an empty response from an external service" | "Check the value" |
| "If removed, the period invariant will be broken" | "Do not touch" |
| "Fill the cache first because the next query reads it" | "Fill the cache" |

## Relation to Change Marking

`agent-code-marking` shows who changed the code and when. `semantic-code-comments` explains why the code is structured the way it is. These rules complement each other: markers provide auditability, comments provide meaning.

---
depends_on:
  - agent-code-marking
---

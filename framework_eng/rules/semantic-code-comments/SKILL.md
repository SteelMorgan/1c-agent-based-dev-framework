---
name: semantic-code-comments
description: "Explain why in comments, do not restate the code"
alwaysApply: true
---

# Semantic Comments in Code

> A good comment explains **why**, not **what**. Names, structure, and expressions already show what the code does; a comment is needed for business meaning, constraints, tradeoffs, and hidden invariants.

## Principle

Code is written once, but read many times. If a future reader asks “why is it like this?”, “what will break if we remove it?”, “what business rule is being protected here?” - a semantic comment is needed.

## What to comment / what NOT to comment

| Comment (why) | DO NOT comment (what) |
|-------------------------|-------------------------|
| Non-obvious business rules | Code restatement (`// adding A and B`) |
| Protection against external failures and edge cases | Obvious operations (`// incrementing the counter`) |
| Workaround and tradeoffs | |
| Hidden invariants and call order | |
| Reasons for rejecting the obvious solution | |
| Magic numbers and constants | |

A comment that contradicts the code is unacceptable: an outdated comment is worse than none - it creates false confidence. When changing code, check nearby comments.

Good:

```bsl
// Скидку применяем только после подтверждения лимита, потому что договор
// может запрещать ретроспективное изменение цены.
Если ЛимитПодтвержден И ДоговорРазрешаетИзменениеЦены Тогда
```

Bad:

```bsl
// Плохо: складываем A и B.
Сумма = A + B;
```

## SHOULD

- Before a non-trivial block of complex logic, provide a brief explanation of the block's purpose.
- At the start of a procedure, add a purpose comment if it does not follow from the name.
- Refer to a specification, ADR, or task when the reason for a decision is unclear without external context.
- Explain the limitations of external systems, the platform, libraries, and data.
- Comment deliberately strange code: why it looks unusual and what will break if it is "simplified".

## How to Phrase It

| Good | Bad |
|--------|-------|
| "We do not use X because Y" | "X is here" |
| "Protection against an empty response from an external service" | "Checking the value" |
| "If removed, the period invariant will break" | "Do not touch" |
| "First we populate the cache because the next request reads from it" | "Populating the cache" |

## Relationship with Change Marking

`agent-code-marking` shows who changed the code and when; `semantic-code-comments` explains why the code is structured this way. Markers provide auditability, comments provide meaning.

---
depends_on:
  - agent-code-marking
---

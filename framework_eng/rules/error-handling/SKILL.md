---
name: error-handling
description: "BSL code with transactions/Try/blocking → apply the `error-handling` skill"
alwaysApply: true
---
# Error Handling and Transactions

> **Trigger:** when writing BSL code containing transactions (`НачатьТранзакцию`), a `Try/Catch` block, or managed locks. When triggered, apply the `error-handling` skill (`framework/skills/bsl-practices/error-handling/SKILL.md`).

**GUARD:** an unclosed transaction is a critical error; acceptance is blocked.

---
depends_on:
  - error-handling

---
depends_on:
  - error-handling
---

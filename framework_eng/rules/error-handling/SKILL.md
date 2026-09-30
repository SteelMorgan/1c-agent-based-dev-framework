---
name: error-handling
description: "For Try, transactions, or locks, apply error-handling"
alwaysApply: true
---
# Error Handling and Transactions

> **Trigger:** when writing BSL code containing transactions (`НачатьТранзакцию`), the `Попытка/Исключение` block, or managed locks. When triggered, apply the `error-handling` skill (`framework/skills/bsl-practices/error-handling/SKILL.md`).

**GUARD:** an unclosed transaction is a critical error; acceptance is blocked.

---
depends_on:
  - error-handling
---

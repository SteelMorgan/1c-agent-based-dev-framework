---
name: error-handling
description: "BSL code with transactions/Try/blocking locks -> apply the error-handling skill"
alwaysApply: true
---
# Error Handling and Transactions

> **Trigger:** when writing BSL code containing transactions (`НачатьТранзакцию`), a `Try/Except` block, or managed locks. When triggered, apply the `error-handling` skill (`framework/skills/bsl-practices/error-handling/SKILL.md`).

An unclosed transaction is a critical error. Check: `ОткатитьТранзакцию` in `Исключение`, the error stack in the log, and the absence of swallowed exceptions without handling.

---
depends_on:
  - error-handling
---

---
name: error-handling
description: "BSL-код с транзакциями/Попытка/блокировками → применить навык error-handling"
alwaysApply: true
---
# Обработка ошибок и транзакции

> **Триггер:** при написании BSL-кода, содержащего транзакции (`НачатьТранзакцию`), блок `Попытка/Исключение` или управляемые блокировки. При срабатывании — применить навык `error-handling` (`framework/skills/bsl-practices/error-handling/SKILL.md`).

**GUARD:** незакрытая транзакция — критическая ошибка; приёмка блокируется.

---
depends_on:
  - error-handling
---

---
name: semantic-code-comments
description: "In comments, explain why, not what the code does"
alwaysApply: true
---

# Semantic Comments in Code

> A good comment explains **why**, not **what**. Names, structure, and expressions already show what the code does; a comment is needed for business meaning, constraints, tradeoffs, and hidden invariants.

## Principle

Code is written once and read many times. If a future reader will ask "why is it like this?", "what breaks if I remove it?", "which business rule is protected here?" - you need a semantic comment.

The goal is not comment density, but reducing guesswork when reading complex or non-obvious code.

## What to Comment

### Non-obvious Business Rules

```bsl
// Скидку применяем только после подтверждения лимита, потому что договор
// может запрещать ретроспективное изменение цены.
Если ЛимитПодтвержден И ДоговорРазрешаетИзменениеЦены Тогда
```

### Protection Against External Failures and Edge Cases

```bsl
// Внешний сервис иногда возвращает пустую сумму для закрытого периода.
// Считаем ее нулем, чтобы отчет остался построимым, а не падал на преобразовании.
Сумма = ?(ЗначениеЗаполнено(Ответ.Сумма), Ответ.Сумма, 0);
```

### Workarounds and Tradeoffs

```bsl
// Не используем пакетную запись: обработчик записи должен отработать для каждого
// объекта отдельно, иначе не обновятся зависимые агрегаты.
Для Каждого Объект Из Объекты Цикл
```

### Hidden Invariants and Call Order

```bsl
// Важно выполнить до расчета итогов: эта процедура заполняет временную таблицу,
// из которой следующий запрос берет границы периода.
ПодготовитьГраницыПериода(Параметры);
```

### Reasons for Rejecting the Obvious Solution

```bsl
// Не используем левое соединение: downstream-логика требует обязательную ссылку
// и не имеет безопасной ветки для пустого значения.
ВНУТРЕННЕЕ СОЕДИНЕНИЕ
```

### Magic Numbers and Constants

```bsl
МаксимумПопыток = 3; // Больше трех повторов задерживает пользователя сильнее, чем помогает при временном сбое.
```

## What Not to Comment

### Code Recap

```bsl
// Плохо: складываем A и B.
Сумма = A + B;
```

### Obvious Operations

```bsl
// Плохо: увеличиваем счетчик.
Счетчик = Счетчик + 1;
```

### Comments That Contradict the Code

If you change the code, check the nearby comments. An outdated comment is worse than no comment because it creates false confidence.

## SHOULD

- Before a non-trivial block of complex logic, give a short explanation of the block's purpose.
- At the start of a procedure, add a purpose comment if it does not follow from the name.
- Refer to the specification, ADR, or task when the reason for the decision is not clear without external context.
- Explain the limitations of external systems, the platform, libraries, and data.
- Comment intentionally strange code: why it looks unusual and what will break if it is "simplified".

## How to Phrase It

| Good | Bad |
|--------|-------|
| "Do not use X because Y" | "Here X" |
| "Protection against an empty response from an external service" | "Check the value" |
| "If removed, the period invariant will be broken" | "Do not touch" |
| "First fill the cache because the next query reads it" | "Fill the cache" |

## Relationship to Change Markup

`agent-code-marking` shows who changed the code and when. `semantic-code-comments` explains why the code is structured this way. These rules complement each other: markers provide auditability, comments provide meaning.

---
depends_on:
  - agent-code-marking
---

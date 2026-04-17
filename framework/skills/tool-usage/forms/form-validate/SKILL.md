---
name: form-validate
description: Валидация управляемой формы 1С. Используй после создания или модификации формы для проверки корректности
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-validate — Валидатор формы

Проверяет Form.xml управляемой формы на структурные ошибки: уникальность ID, наличие companion-элементов, корректность ссылок DataPath и команд.

## Использование

```
/form-validate <FormPath>
```

## Параметры

| Параметр  | Обязательный | По умолчанию | Описание                    |
|-----------|:------------:|--------------|-----------------------------|
| FormPath  | да           | —            | Путь к файлу Form.xml       |
| MaxErrors | нет          | 30           | Остановиться после N ошибок |

## Команда

```bash
xmlgen validate --type form "<FormPath>"
```

С JSON-отчётом:
```bash
xmlgen validate --type form --output json "<FormPath>"
```

> Реализация: Java-CLI `xmlgen validate` (замена Python-скрипта). Коды ошибок — `FORM-001..008` (структура), `FORM-101..120` (семантика).

## Выполняемые проверки

| # | Проверка | Серьёзность |
|---|---|---|
| 1 | Корневой элемент `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` присутствует, id="-1" | ERROR |
| 3 | Уникальность ID элементов (отдельный пул) | ERROR |
| 4 | Уникальность ID реквизитов (отдельный пул) | ERROR |
| 5 | Уникальность ID команд (отдельный пул) | ERROR |
| 6 | Companion-элементы (ContextMenu, ExtendedTooltip, и др.) | ERROR |
| 7 | DataPath → ссылается на существующий реквизит | ERROR |
| 8 | CommandName кнопок → ссылается на существующую команду | ERROR |
| 9 | События имеют непустые имена обработчиков | ERROR |
| 10 | Команды имеют Action (обработчик) | ERROR |
| 11 | Не более одного MainAttribute | ERROR |
| 12 | BaseForm: наличие и version (при расширении) | OK / WARN |
| 13 | callType значения: Before, After, Override | ERROR |
| 14 | ID расширения >= 1000000 для добавленных attrs/commands | WARN |
| 15 | callType без BaseForm — некорректная структура | WARN |

## Вывод

```
=== Validation: ФормаДокумента ===

[OK]    Root element: Form version=2.17
[OK]    AutoCommandBar: name='ФормаКоманднаяПанель', id=-1
[OK]    Unique element IDs: 96 elements
[OK]    Unique attribute IDs: 38 entries
[OK]    Unique command IDs: 5 entries
[OK]    Companion elements: 86 elements checked
[OK]    DataPath references: 53 paths checked
[OK]    Command references: 2 buttons checked
[OK]    Event handlers: 41 events checked
[OK]    Command actions: 5 commands checked
[OK]    MainAttribute: 1 main attribute

---
Total: 96 elements, 38 attributes, 5 commands
All checks passed.
```

Код возврата: 0 = все проверки пройдены, 1 = есть ошибки.

Проверки 12–15 активируются автоматически при обнаружении `<BaseForm>`.

Использовать после `/form-compile`, `/form-edit`, ручного редактирования Form.xml — для выявления структурных ошибок до сборки EPF.

---
name: skd-edit
description: "Use for атомарного изменения существующей Schema.xml СКД: добавить/удалить поля, итоги, параметры, переписать запрос набора данных, изменить структуру варианта. Helps точечно дорабатывать СКД без полной перекомпиляции через xml-gen skd edit."
---

# SKD Edit — точечное редактирование Schema.xml

## Когда применять

| Триггер | Действие |
|---------|----------|
| Создать новую СКД с нуля | `xml-gen skd compile` → [skd-dsl](../skd-dsl/) |
| Добавить поле/итог/параметр в существующую Schema.xml | `xml-gen skd edit ... add-field/add-total/add-parameter` |
| Изменить роль поля (баланс, измерение, период) | `set-field-role` |
| Полностью переписать запрос набора данных | `set-query` |
| Точечно поправить кусок текста запроса | `patch-query @once` |
| Переименовать/переставить параметры | `rename-parameter`, `reorder-parameters` |
| Изменить поля группировки структуры без потери Selection/CA | `modify-structure` с `@name=` |
| Снести всё условное оформление варианта | `clear-conditionalAppearance` |

## Команда

```bash
xml-gen skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]
```

| Параметр | Описание |
|----------|----------|
| `SchemaPath` | Путь к `Template.xml` / `Schema.xml`. Папка достраивается до `Ext/Template.xml`. |
| `--dataSet` | Имя целевого набора. По умолчанию — первый. |
| `--variant` | Имя варианта настроек. По умолчанию — первый. |
| `--no-selection` | Для `add-field` — не добавлять поле в `selection` варианта. |

## Операции — шпаргалка

| Группа | Shorthand | Reference |
|--------|-----------|-----------|
| `add-field`, `modify-field`, `remove-field` | `"Имя [Заголовок]: тип @роль #ограничение"` | [fields.md](references/fields.md) |
| `set-field-role` | `"dataPath [@флаги] [kv=значение]"` | [fields.md](references/fields.md) |
| `add-parameter`, `modify-parameter`, `remove-parameter` | `"Имя [Заголовок]: тип = значение [@флаги]"` | [parameters.md](references/parameters.md) |
| `rename-parameter` | `"СтароеИмя => НовоеИмя"` | [parameters.md](references/parameters.md) |
| `reorder-parameters` | `"Имя1, Имя2, Имя3"` | [parameters.md](references/parameters.md) |
| `add-total`, `remove-total` | `"<dataPath>: <выражение>"` / `"<dataPath>"` | [totals.md](references/totals.md) |
| `modify-structure` | `"Поле1, Поле2 @name=ИмяГруппы"` | [structure.md](references/structure.md) |
| `set-query` | текст запроса или `"@path/query.sql"` | [query.md](references/query.md) |
| `patch-query` | `"старое => новое [@once]"` | [query.md](references/query.md) |
| `clear-conditionalAppearance` | `"*"` | (ниже) |

## Пакетный режим (batch)

Несколько значений через разделитель `;;`:
```bash
xml-gen skd edit Schema.xml add-field "Цена: decimal(15,2) ;; Количество: decimal(15,3)"
```
**Не поддерживают batch:** `set-query`, `patch-query` без `@once`, `modify-structure`. Запрос может содержать `;;` буквально — поэтому `set-query` всегда одиночный.

## clear-conditionalAppearance

```bash
xml-gen skd edit Schema.xml clear-conditionalAppearance "*"
```
Удаляет все правила УО в указанном варианте. Значение всегда `*`. Идемпотентна.

## Инварианты и контракт

1. **Атомарность.** Читает → меняет → валидирует well-formedness → пишет атомарно. При ошибке — файл не меняется.
2. **Идемпотентность.** `set-field-role`, `@hidden`/`@always`, `clear-*`, `remove-*` — повторный вызов не меняет файл. `remove-*`: отсутствие цели = noop с warning, не error.
3. **Дубликаты при `add-*`.** Если объект с таким именем уже есть — warning + skip. Для обновления — используй `modify-*`.
4. **`@once` для `patch-query`.** Если в тексте 0 или ≥2 совпадений — ошибка, файл не меняется. Без флага — заменяет все вхождения.
5. **`availableValue=` в `modify-parameter` — полная замена,** не merge. Старые значения удаляются.
6. **Списочное значение параметра** задаётся через `value=A, B` или `@valueList`; при нескольких default-значениях пишутся несколько `<value>` и `valueListAllowed=true`.
7. **`set-query` против `patch-query`.** Полная замена против точечной правки. Большие изменения — через `set-query` (можно из файла `@path`). Локальный fix — `patch-query @once`.
8. **`modify-structure` требует `@name=`.** Без явного имени операция падает. Имя задаётся при создании структуры в skd-dsl (`set-structure "... @name=ДанныеОтчета"`).

## Правила для агента

1. **`patch-query @once` по умолчанию.** Если правишь запрос и не уверен в уникальности подстроки — поставь `@once`.
2. **Не путай `set-field-role` и `modify-field`.** `modify-field` НЕ трогает роль (она в `<role>`, свойства поля — в `<field>`).
3. **Перед `modify-structure`** убедись, что группировка имеет имя. Иначе — `set-structure` из skd-dsl (полная замена).
4. **`@hidden`/`@always` идемпотентны.** Типовой паттерн для constant-параметров запроса.
5. **`availableValue=` в `modify-parameter` — destructive.** Чтобы добавить одно значение — прочитай файл, перечисли все значения в новой строке.

## Типовой workflow

```bash
xml-gen validate --type skd Schema.xml                                          # 1. валидировать
xml-gen skd edit Schema.xml add-field "Цена: decimal(15,2) ;; Количество: decimal(15,3)"
xml-gen skd edit Schema.xml add-total "Цена: Среднее ;; Количество: Сумма"
xml-gen skd edit Schema.xml set-field-role "СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance"
xml-gen skd edit Schema.xml patch-query "СубконтоДт1) В => СубконтоКт1) В @once"
xml-gen validate --type skd --level semantic Schema.xml                         # 3. финальная валидация
```

## Связанные skills

- [skd-dsl](../skd-dsl/) — генерация СКД с нуля, `set-structure` с `@name=`.
- [xml-generation](../SKILL.md) — `validate`, `replace-text`, §3.

---
depends_on:
  - skd-dsl
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---

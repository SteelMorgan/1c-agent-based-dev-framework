# Поля и роли полей

Операции: `add-field`, `modify-field`, `remove-field`, `set-field-role`.

## add-field — добавить поле в набор данных

Shorthand: `"Имя [Заголовок]: тип @роль #ограничение"`.

```
"Цена: decimal(15,2)"
"Организация [Орг-ция]: CatalogRef.Организации @dimension"
"Служебное: string #noFilter #noOrder #noGroup #noField"
```

Семантика:

- `Имя` — `dataPath` (обязательное).
- `[Заголовок]` — опциональный `<title>`. Скобки `[ ]` — часть синтаксиса, не «опционально».
- `: тип` — `<valueType>`. Типы: `string[(N)]`, `decimal(D,F)` / `number(D,F)`, `boolean`, `date`, `CatalogRef.*`, `DocumentRef.*`, `EnumRef.*`, `ChartOfAccountsRef.*` и т.п.
- `@роль` — короткое имя роли (`@dimension`, `@balance`, `@period`, `@account`, …). Для сложных ролей с kv-параметрами используй [`set-field-role`](#set-field-role) отдельной операцией.
- `#ограничение` — `<useRestriction>`: `#noFilter`, `#noOrder`, `#noGroup`, `#noField`.

Поведение:

- Поле добавляется в `<dataSet>` и **в `<selection>` варианта** (если не передан флаг `--no-selection`).
- Дубликат `dataPath` — warning + skip, не ошибка.
- Поддерживает batch через `;;`.

## modify-field — изменить существующее поле

Тот же shorthand, что и у `add-field`. Находит поле по `dataPath`, **сливает свойства** (непустые в новой строке переопределяют существующие, не указанные — сохраняются). Позиция поля в наборе сохраняется.

```
"Цена [Цена USD]: decimal(10,4) @dimension"
```

Если поля с таким `dataPath` нет — ошибка (в отличие от `add-field`, который при дубликате делает skip). Для роли используй `set-field-role` — `modify-field` намеренно не трогает `<role>`.

## remove-field — удалить поле

Value — `dataPath`.

```
"Цена"
"Организация ;; СубконтоДт1 ;; СубконтоКт1"
```

Удаляет поле из `<dataSet>` и **из `<selection>` варианта** (все упоминания). Отсутствующее поле — warning + skip.

## set-field-role — установить роль поля

Shorthand: `"<dataPath> [@флаги] [kv=значение]..."`.

**Полностью заменяет** содержимое `<role>` поля. Если значение содержит только `dataPath` без флагов и kv — роль удаляется целиком.

```
"Сумма"                                                                 # снять роль
"СуммаОстаток @balance"                                                 # простая балансовая роль
"СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance"   # балансовое + уточнение
"СуммаКон @balance balanceGroupName=Сумма balanceType=ClosingBalance"
"Контрагент @dimension parentDimension=Группа"
"Период @period periodNumber=1 periodType=Second"
"Счет @account accountTypeExpression=ВЫРАЗИТЬ(Счет.Вид КАК Строка)"
"Количество @autoOrder orderType=Desc"
```

Флаги:

| Флаг | Значение |
|------|----------|
| `@balance` | Поле — балансовый ресурс. |
| `@dimension` | Поле — измерение. |
| `@account` | Поле — счёт. |
| `@period` | Поле — период (компонент периода). |
| `@required` | Обязательное поле. |
| `@autoOrder` | Автосортировка по этому полю. |
| `@ignoreNullValues` | Игнорировать NULL при агрегации. |

Ключ-значение:

| KV | Семантика |
|----|-----------|
| `balanceGroupName` | Имя балансовой группы (объединяет `СуммаНач`/`СуммаКон` в одну группу). |
| `balanceType` | `OpeningBalance` / `ClosingBalance`. |
| `parentDimension` | Родительское измерение для иерархических представлений. |
| `accountTypeExpression` | Выражение определения типа счёта. |
| `orderType` | `Asc` / `Desc`. |
| `expression` | Выражение для составной роли. |
| `periodNumber` | Номер периода (1, 2, …). |
| `periodType` | `Year`, `HalfYear`, `Quarter`, `Month`, `Week`, `Day`, `Hour`, `Minute`, `Second`. |

Поведение:

- Идемпотентна: повторный вызов с теми же параметрами не меняет файл.
- Поддерживает batch (`;;`).
- Не пересекается с `modify-field`: `modify-field` правит свойства поля, `set-field-role` — только `<role>`.

## Edge cases

| Случай | Поведение |
|--------|-----------|
| `add-field` для существующего поля | Warning + skip. Для обновления — `modify-field`. |
| `modify-field` для несуществующего поля | Error, файл не меняется. |
| `remove-field` для несуществующего поля | Warning + skip (идемпотентно). |
| `set-field-role "X"` (без флагов/kv) | Снимает роль полностью. |
| `set-field-role` для поля без `<role>` | Создаёт `<role>` с указанными свойствами. |
| Поле в `Folder(...)` selection | `remove-field` чистит и из вложенных групп выбора. |
| Тип `decimal` vs `number` | Эквивалентны; маппятся на `<v8:Number>` с заданной precision/scale. |

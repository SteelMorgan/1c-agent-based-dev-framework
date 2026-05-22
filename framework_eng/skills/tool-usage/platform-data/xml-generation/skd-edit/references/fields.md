# Fields and Field Roles

Operations: `add-field`, `modify-field`, `remove-field`, `set-field-role`.

## add-field — add a field to the data set

Shorthand: `"Name [Title]: type @role #restriction"`.

```
"Цена: decimal(15,2)"
"Организация [Орг-ция]: CatalogRef.Организации @dimension"
"Служебное: string #noFilter #noOrder #noGroup #noField"
```

Semantics:

- `Name` — `dataPath` (required).
- `[Title]` — optional `<title>`. The brackets `[ ]` are part of the syntax, not "optional".
- `: type` — `<valueType>`. Types: `string[(N)]`, `decimal(D,F)` / `number(D,F)`, `boolean`, `date`, `CatalogRef.*`, `DocumentRef.*`, `EnumRef.*`, `ChartOfAccountsRef.*` and so on.
- `@role` — short role name (`@dimension`, `@balance`, `@period`, `@account`, …). For complex roles with kv parameters, use [`set-field-role`](#set-field-role) as a separate operation.
- `#restriction` — `<useRestriction>`: `#noFilter`, `#noOrder`, `#noGroup`, `#noField`.

Behavior:

- The field is added to `<dataSet>` and **to the variant `<selection>`** (unless the `--no-selection` flag is passed).
- A duplicate `dataPath` is a warning + skip, not an error.
- Supports batch processing via `;;`.

## modify-field — modify an existing field

The same shorthand as `add-field`. Finds the field by `dataPath`, **merges properties** (non-empty values in the new string override existing ones, unspecified values are preserved). The field position in the data set is kept.

```
"Цена [Цена USD]: decimal(10,4) @dimension"
```

If there is no field with that `dataPath`, it is an error (unlike `add-field`, which skips on duplicates). For the role, use `set-field-role` — `modify-field` intentionally does not touch `<role>`.

## remove-field — remove a field

Value — `dataPath`.

```
"Цена"
"Организация ;; СубконтоДт1 ;; СубконтоКт1"
```

Removes the field from `<dataSet>` and **from the variant `<selection>`** (all references). A missing field is a warning + skip.

## set-field-role — set a field role

Shorthand: `"<dataPath> [@flags] [kv=value]..."`.

**Completely replaces** the contents of the field's `<role>`. If the value contains only `dataPath` without flags and kv, the role is removed entirely.

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

Flags:

| Flag | Meaning |
|------|----------|
| `@balance` | The field is a balance resource. |
| `@dimension` | The field is a dimension. |
| `@account` | The field is an account. |
| `@period` | The field is a period (a period component). |
| `@required` | Required field. |
| `@autoOrder` | Auto-sort by this field. |
| `@ignoreNullValues` | Ignore NULL during aggregation. |

Key-value:

| KV | Semantics |
|----|-----------|
| `balanceGroupName` | Name of the balance group (combines `СуммаНач`/`СуммаКон` into one group). |
| `balanceType` | `OpeningBalance` / `ClosingBalance`. |
| `parentDimension` | Parent dimension for hierarchical views. |
| `accountTypeExpression` | Expression that determines the account type. |
| `orderType` | `Asc` / `Desc`. |
| `expression` | Expression for a composite role. |
| `periodNumber` | Period number (1, 2, …). |
| `periodType` | `Year`, `HalfYear`, `Quarter`, `Month`, `Week`, `Day`, `Hour`, `Minute`, `Second`. |

Behavior:

- Idempotent: repeating the call with the same parameters does not change the file.
- Supports batch processing (`;;`).
- Does not overlap with `modify-field`: `modify-field` edits field properties, `set-field-role` only edits `<role>`.

## Edge cases

| Case | Behavior |
|--------|-----------|
| `add-field` for an existing field | Warning + skip. Use `modify-field` for updates. |
| `modify-field` for a non-existent field | Error, the file does not change. |
| `remove-field` for a non-existent field | Warning + skip (idempotent). |
| `set-field-role "X"` (without flags/kv) | Removes the role completely. |
| `set-field-role` for a field without `<role>` | Creates `<role>` with the specified properties. |
| Field in `Folder(...)` selection | `remove-field` also cleans it from nested selection groups. |
| `decimal` vs `number` type | Equivalent; map to `<v8:Number>` with the specified precision/scale. |

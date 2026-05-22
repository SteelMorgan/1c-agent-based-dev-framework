# Totals (totals)

Operations: `add-total`, `remove-total`.

## add-total — add a total

Shorthand: `"<dataPath>: <expression>"`.

```
"Цена: Среднее"                  # → Среднее(Цена)
"Количество: Сумма"              # → Сумма(Количество)
"Стоимость: Сумма(Кол * Цена)"   # как есть (явная функция со скобками)
"Маржа: Маржа"                   # identity: <expression>=Маржа (для вычисляемого поля)
"Проверка: ЕстьNULL(СуммаОстаток, 0)"  # произвольное выражение
```

### Automatic wrapping of aggregate functions

If the expression is a **known function without parentheses** it is automatically wrapped as `Func(dataPath)`:

| Function | Semantics |
|---------|-----------|
| `Сумма` | `Сумма(<dataPath>)` |
| `Среднее` | `Среднее(<dataPath>)` |
| `Количество` | `Количество(<dataPath>)` |
| `Минимум` | `Минимум(<dataPath>)` |
| `Максимум` | `Максимум(<dataPath>)` |

### Non-aggregate functions and identity

If the expression has **parentheses** it is used as-is (`Сумма(Кол * Цена)`, `ЕстьNULL(СуммаОстаток, 0)`).

If the expression has **no parentheses and is not in the aggregate list** it is used as-is (identity case: `Маржа: Маржа` → `<expression>Маржа</expression>`). This allows calculated fields and constants to be included in totals.

Non-aggregate functions (`ЕстьNULL`, `ВЫРАЗИТЬ`, `ВЫБОР КОГДА...`) should be written with parentheses so the parser does not try to wrap them.

### Behavior

- Supports batch via `;;`.
- Duplicate `<dataPath>` in `<totalFields>` results in warning + skip. To override the expression, first `remove-total`, then `add-total`.
- The field on which the total is set **must exist** in the set (as a `field` or `calculatedField`). Otherwise the SKD will not compile - `xml-gen validate --type skd --level semantic` will catch it.

## remove-total — remove a total

Value — `dataPath`.

```
"Цена"
"Цена ;; Количество ;; Сумма"
```

Removes the entry from `<totalFields>`. Missing total — warning + skip (idempotent).

## Edge cases

| Case | Behavior |
|--------|-----------|
| `add-total "X: Сумма"` for non-existent field X | The script will add the entry; the error will appear during `validate --level semantic`. |
| `add-total "X: SUM"` (English function) | Identity - expression `<expression>SUM</expression>`, which is incorrect. Use Russian names. |
| `add-total "X: Сумма(X)"` | Equivalent to `add-total "X: Сумма"` - both forms produce `Сумма(X)`. |
| `add-total "X: Среднее(Y)"` for different fields | Allowed - `dataPath=X`, `expression=Среднее(Y)`. |
| Total for a calculated field | Works if the field is `calculatedField` and is present in the set. |

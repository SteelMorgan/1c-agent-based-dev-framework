# Example of a completed data contract

A generalized example of a contract for tabular output from five document rows
(a report on an imported Excel framework). Shows how each layout column
is linked to a source and how the verification context is recorded.

## Contract by column

| Layout column | Source | Empty value rule | Row key | Expected cardinality | Checked total | Knowledge status |
|---|---|---|---|---|---|---|
| `Дата` | `Документ.Дата` (string representation) | prohibited | `Ссылка` | `5` | not applicable | proven by query |
| `Аккаунт` | `ПРЕДСТАВЛЕНИЕ(Документ.Аккаунт)` | prohibited | `Ссылка` | `5` | not applicable | proven by query |
| `Инструмент` | `Документ.Инструмент` | prohibited | `Ссылка` | `5` | not applicable | proven by query |
| `Сторона` | `ПРЕДСТАВЛЕНИЕ(Документ.Сторона)` | allowed | `Ссылка` | `5` | not applicable | proven by query |
| `Количество` | `ВЫБОР КОГДА Документ.ФактическоеКоличество <> 0 ТОГДА ФактическоеКоличество ИНАЧЕ ЗаявленноеКоличество КОНЕЦ` | prohibited (`0` is allowed as a value) | `Ссылка` | `5` | `Сумма(Количество)` | proven by query |
| `Цена` | `ВЫБОР КОГДА СредняяЦена <> 0 ТОГДА СредняяЦена ИНАЧЕ Цена КОНЕЦ` | prohibited | `Ссылка` | `5` | not applicable | proven by query |
| `Сумма` | `Количество * Цена` | prohibited | `Ссылка` | `5` | `Сумма(Сумма)` | proven by query |
| `Статус` | `ВЫБОР КОГДА Документ.КодСтатуса <> "" ТОГДА КодСтатуса ИНАЧЕ ПРЕДСТАВЛЕНИЕ(Статус) КОНЕЦ` | allowed | `Ссылка` | `5` | not applicable | proven by query |

## Evidence context

| Set | Period | Parameters | Active filters | Row count | Empty values | Duplicates | Totals | Sampling | Verdict |
|---|---|---|---|---:|---|---|---|---|---|
| `Строки отчёта` | latest 5 in descending date order | none | `НЕ ПометкаУдаления` | 5 | required fields populated | none by `Ссылка` | `ИтогКоличество` and `ИтогСумма` matched recalculation | all 5 rows checked individually | `PASS` |

## What is fundamental here

- `СУММА` is treated as a platform query/code result, not taken from the scaffold’s
  Excel formula — there are no formulas in the layout after import.
- Verification was performed before filling the scaffold: values in the final
  `ТабличныйДокумент` were checked point by point against the query result
  (coordinates `R6C1..R10C8`, total row `R11`).
- Empty values are classified: `Сторона`/`Статус` allow emptiness,
  other fields do not.
- Cardinality `5` is a scenario requirement (`ПЕРВЫЕ 5`), not a coincidental
  result size.

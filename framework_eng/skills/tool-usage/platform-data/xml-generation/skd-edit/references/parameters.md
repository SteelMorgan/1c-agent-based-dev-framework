# SKD Parameters

Operations: `add-parameter`, `modify-parameter`, `remove-parameter`, `rename-parameter`, `reorder-parameters`.

## add-parameter — add a parameter

Shorthand: `"Name [Title]: type = value [availableValue=list] [@flags]"`.

```
"Период [Отчетный период]: StandardPeriod = LastMonth @autoDates"
"Организация: CatalogRef.Организации"
"ПС: CatalogRef.Контрагенты = Справочник.Контрагенты.ПустаяСсылка @hidden"
"Период: StandardPeriod = LastMonth @always"
"ПСчет: ChartOfAccountsRef.Хозрасчетный = ПланСчетов.Хозрасчетный.X @hidden @always"
"Округление: EnumRef.Округления = Окр1 availableValue=Перечисление.Округления.Окр1: руб., Перечисление.Округления.Окр1000: тыс."
```

Semantics:

- `Name` — parameter name. Required.
- `[Title]` — `<title>`. Optional; brackets are part of the syntax.
- `type` — `<valueType>`, formats like fields (see [fields.md](fields.md)).
- `= value` — `<value>`. The value type is selected according to the declared parameter type.
- `availableValue=v1[: p1], v2[: p2], …` — initial list of allowed values. Element separator is `,`; representation follows `:`.

### Quotes in availableValue

If `,` or `:` appears in the value or representation, wrap it in single quotes `'...'`:

```
"Округление: EnumRef.Округления = Окр1 availableValue=Окр1_00: 'руб., коп.', Окр1: руб."
```

### Parameter Flags

| Flag | Semantics |
|------|-----------|
| `@hidden` | The parameter is hidden from user settings. Typically used for query constants. |
| `@always` | The parameter is always substituted into the query. Used separately or together with `@hidden` (for visible required parameters, such as the reporting period). |
| `@autoDates` | For `StandardPeriod` — generates a pair of hidden parameters `ДатаНачала` / `ДатаОкончания`, tied to the main period. |

Idempotence: `@hidden`/`@always` can be applied repeatedly — behavior does not change.

## modify-parameter — modify an existing parameter

Shorthand: `"Name [Title] [key=value]... [@flags]"`. Finds the parameter by name, updates the specified properties, preserves the rest.

```
"ПорядокОкругления use=Always"
"ПорядокОкругления [Округление сумм] denyIncompleteValues=true"
"ПериодОтчета [Отчетный период]"                                  # only title
"ПорядокОкругления availableValue=Перечисление.Округления.Окр1: руб., Перечисление.Округления.Окр1000: тыс."
"СчетПС value=ПланСчетов.Хозрасчетный.КассаПредприятия"
"Контрагент @hidden @always"
```

Available kv:

| KV | Semantics |
|----|-----------|
| `value=` | Replaces the parameter's `<value>` (the value type is inferred automatically from the parameter type). |
| `availableValue=` | **Full replacement** of the allowed values list (see below). |
| `use=` | `Always` / `Auto`. |
| `denyIncompleteValues=` | `true` / `false`. |

### availableValue= in modify-parameter — destructive

`availableValue=` in `modify-parameter` **replaces the entire list** of allowed values. Old items are removed. This is intentional: the list is usually changed wholesale.

If you need to add one value to an existing list, first read the file, then list all elements in the new `availableValue=...` line.

### Title only

`modify-parameter "Name [New title]"` is the only operation that updates only the title. This is valid — it can be called without other kv pairs.

## remove-parameter — remove a parameter

Value — parameter name.

```
"СтарыйПараметр"
"Парам1 ;; Парам2 ;; Парам3"
```

Deletes the parameter from `<parameters>`. References `&Name` in query texts and in expressions of other parameters are **not cleaned up** — that is the caller's responsibility (use `patch-query @once` to clean up the query).

Nonexistent parameter — warning + skip.

## rename-parameter — rename a parameter

Shorthand: `"OldName => NewName"`. Atomic operation:

1. Renames the parameter in `<parameters>`.
2. Updates references `&OldName` in the value expressions of other parameters — **only exact matches** (`&ПериодX` is not affected).
3. Updates records in `dataParameters` of all settings variants.

The query text is **not touched**. If the parameter is used in the query as `&OldName`, call `patch-query "&OldName => &NewName"` separately (optionally with `@once`).

```
"Период => ПериодОтчета"
```

Batch is supported, but be careful: with a chain `A => B ;; B => C`, the second patch will find the already renamed parameter.

## reorder-parameters — reorder parameters

Shorthand: `"Name1, Name2, Name3"` — partial list. The specified parameters go first in the given order, the rest keep their original relative order and go at the end.

```
"ПериодОтчета, НачалоПериода, КонецПериода"
```

A parameter from the list that is not in the schema — warning + skip. Duplicates in the list — error. It is not necessary to provide the full list — only those whose order matters.

## Edge cases

| Case | Behavior |
|--------|-----------|
| `add-parameter` for an existing name | Warning + skip. Use `modify-parameter`. |
| `@autoDates` on a parameter other than `StandardPeriod` | Error. |
| `availableValue=` with a single quote inside a value | Escape by doubling it: `''` inside `'...'`. |
| `modify-parameter` without kv and without `[Title]` | No-op with warning. |
| `rename-parameter` for a target name that is already taken | Error. |
| `value=` with a type that does not match the parameter type | Error (value type validation). |

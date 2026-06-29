---
name: skd-edit
description: "xml-gen atomic edits of existing SKD Schema.xml"
---

# SKD Edit — targeted editing of Schema.xml

## When to use

| Trigger | Action |
|---------|----------|
| Create a new SKD from scratch | `xml-gen skd compile` → [skd-dsl](../skd-dsl/) |
| Add a field/total/parameter to an existing Schema.xml | `xml-gen skd edit ... add-field/add-total/add-parameter` |
| Change a field role (balance, dimension, period) | `set-field-role` |
| Completely rewrite the data set query | `set-query` |
| Patch a specific piece of query text | `patch-query @once` |
| Rename/reorder parameters | `rename-parameter`, `reorder-parameters` |
| Change grouping fields in the structure without losing Selection/CA | `modify-structure` with `@name=` |
| Remove all conditional appearance from a variant | `clear-conditionalAppearance` |

## Command

```bash
xml-gen skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]
```

| Parameter | Description |
|----------|----------|
| `SchemaPath` | Path to `Template.xml` / `Schema.xml`. The folder is extended to `Ext/Template.xml`. |
| `--dataSet` | Name of the target data set. By default — the first one. |
| `--variant` | Name of the settings variant. By default — the first one. |
| `--no-selection` | For `add-field` — do not add the field to the variant `selection`. |

## Operations — cheat sheet

| Group | Shorthand | Reference |
|--------|-----------|-----------|
| `add-field`, `modify-field`, `remove-field` | `"Name [Caption]: type @role #constraint"` | [fields.md](references/fields.md) |
| `set-field-role` | `"dataPath [@flags] [kv=value]"` | [fields.md](references/fields.md) |
| `add-parameter`, `modify-parameter`, `remove-parameter` | `"Name [Caption]: type = value [@flags]"` | [parameters.md](references/parameters.md) |
| `rename-parameter` | `"OldName => NewName"` | [parameters.md](references/parameters.md) |
| `reorder-parameters` | `"Name1, Name2, Name3"` | [parameters.md](references/parameters.md) |
| `add-total`, `remove-total` | `"<dataPath>: <expression>"` / `"<dataPath>"` | [totals.md](references/totals.md) |
| `modify-structure` | `"Field1, Field2 @name=GroupName"` | [structure.md](references/structure.md) |
| `set-query` | query text or `"@path/query.sql"` | [query.md](references/query.md) |
| `patch-query` | `"old => new [@once]"` | [query.md](references/query.md) |
| `clear-conditionalAppearance` | `"*"` | (below) |

## Batch mode (batch)

Multiple values separated by `;;`:
```bash
xml-gen skd edit Schema.xml add-field "Цена: decimal(15,2) ;; Количество: decimal(15,3)"
```
**Batch is not supported by:** `set-query`, `patch-query` without `@once`, `modify-structure`. A query may literally contain `;;`, so `set-query` is always single.

## clear-conditionalAppearance

```bash
xml-gen skd edit Schema.xml clear-conditionalAppearance "*"
```
Removes all conditional appearance rules in the specified variant. The value is always `*`. Idempotent.

## Invariants and contract

1. **Atomicity.** Reads → changes → validates well-formedness → writes atomically. On error, the file does not change.
2. **Idempotency.** `set-field-role`, `@hidden`/`@always`, `clear-*`, `remove-*` - a repeated call does not change the file. `remove-*`: absence of the target = noop with warning, not an error.
3. **Duplicates in `add-*`.** If an object with that name already exists - warning + skip. To update it, use `modify-*`.
4. **`@once` for `patch-query`.** If the text has 0 or ≥2 matches - error, the file does not change. Without the flag - replaces all occurrences.
5. **`availableValue=` in `modify-parameter` - full replacement,** not merge. Old values are removed.
6. **Parameter value lists** are set with `value=A, B` or `@valueList`; multiple default values are written as repeated `<value>` plus `valueListAllowed=true`.
7. **`set-query` vs `patch-query`.** Full replacement versus targeted edit. Large changes - via `set-query` (can be loaded from a file `@path`). A local fix - `patch-query @once`.
8. **`modify-structure` requires `@name=`.** Without an explicit name the operation fails. The name is set when creating the structure in skd-dsl (`set-structure "... @name=ДанныеОтчета"`).

## Rules for the agent

1. **`patch-query @once` by default.** If you are editing query text and are not sure the substring is unique, set `@once`.
2. **Do not confuse `set-field-role` and `modify-field`.** `modify-field` does NOT touch the role (it is in `<role>`, while field properties are in `<field>`).
3. **Before `modify-structure`** make sure the grouping has a name. Otherwise - `set-structure` from skd-dsl (full replacement).
4. **`@hidden`/`@always` are idempotent.** Typical pattern for constant query parameters.
5. **`availableValue=` in `modify-parameter` - destructive.** To add one value - read the file, enumerate all values in a new line.

## Typical workflow

```bash
xml-gen validate --type skd Schema.xml                                          # 1. валидировать
xml-gen skd edit Schema.xml add-field "Цена: decimal(15,2) ;; Количество: decimal(15,3)"
xml-gen skd edit Schema.xml add-total "Цена: Среднее ;; Количество: Сумма"
xml-gen skd edit Schema.xml set-field-role "СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance"
xml-gen skd edit Schema.xml patch-query "СубконтоДт1) В => СубконтоКт1) В @once"
xml-gen validate --type skd --level semantic Schema.xml                         # 3. финальная валидация
```

## Related skills

- [skd-dsl](../skd-dsl/) - generating SKD from scratch, `set-structure` with `@name=`.
- [xml-generation](../SKILL.md) - `validate`, `replace-text`, §3.

---
depends_on:
  - skd-dsl
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---

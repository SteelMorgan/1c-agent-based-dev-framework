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
| Change a field's role (balance, dimension, period) | `set-field-role` |
| Completely rewrite a data set query | `set-query` |
| Make a targeted change to part of the query text | `patch-query @once` |
| Rename/reorder parameters | `rename-parameter`, `reorder-parameters` |
| Change structure grouping fields without losing Selection/CA | `modify-structure` with `@name=` |
| Remove all conditional formatting from a variant | `clear-conditionalAppearance` |

## Command

```bash
xml-gen skd edit <SchemaPath> <operation> "<value>" [--dataSet <name>] [--variant <name>] [--no-selection]
```

| Parameter | Description |
|----------|----------|
| `SchemaPath` | Path to `Template.xml` / `Schema.xml`. A folder is expanded to `Ext/Template.xml`. |
| `--dataSet` | Target data set name. Defaults to the first. |
| `--variant` | Settings variant name. Defaults to the first. |
| `--no-selection` | For `add-field` — do not add the field to the variant's `selection`. |

## Operations — quick reference

| Group | Shorthand | Reference |
|--------|-----------|-----------|
| `add-field`, `modify-field`, `remove-field` | `"Name [Title]: type @role #restriction"` | [fields.md](references/fields.md) |
| `set-field-role` | `"dataPath [@flags] [kv=value]"` | [fields.md](references/fields.md) |
| `add-parameter`, `modify-parameter`, `remove-parameter` | `"Name [Title]: type = value [@flags]"` | [parameters.md](references/parameters.md) |
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
**Do not support batch:** `set-query`, `patch-query` without `@once`, `modify-structure`. A query may contain `;;` literally — therefore `set-query` is always a single operation.

## clear-conditionalAppearance

```bash
xml-gen skd edit Schema.xml clear-conditionalAppearance "*"
```
Removes all УО rules in the specified variant. The value is always `*`. Idempotent.

## Invariants and contract

1. **Atomicity.** Reads → changes → validates well-formedness → writes atomically. On error, the file is not changed.
2. **Idempotency.** `set-field-role`, `@hidden`/`@always`, `clear-*`, `remove-*` — repeated calls do not change the file. `remove-*`: target not found = noop with warning, not error.
3. **Duplicates with `add-*`.** If an object with that name already exists — warning + skip. To update, use `modify-*`.
4. **`@once` for `patch-query`.** If there are 0 or ≥2 matches in the text — error, file is not changed. Without the flag, replaces all occurrences.
5. **`availableValue=` in `modify-parameter` is a full replacement,** not a merge. Old values are removed.
6. **A parameter list value** is specified via `value=A, B` or `@valueList`; with multiple default values, multiple `<value>` elements are written and `valueListAllowed=true`.
7. **`set-query` versus `patch-query`.** Full replacement versus targeted edit. For large changes, use `set-query` (can use a file via `@path`). For a local fix, use `patch-query @once`.
8. **`modify-structure` requires `@name=`.** The operation fails without an explicit name. The name is set when creating the structure in skd-dsl (`set-structure "... @name=ДанныеОтчета"`).

## Rules for the agent

1. **Use `patch-query @once` by default.** If you are editing a query and are unsure whether the substring is unique, add `@once`.
2. **Do not confuse `set-field-role` and `modify-field`.** `modify-field` does NOT touch the role (it is in `<role>`; field properties are in `<field>`).
3. **Before `modify-structure`,** make sure the grouping has a name. Otherwise, use `set-structure` from skd-dsl (full replacement).
4. **`@hidden`/`@always` are idempotent.** Typical pattern for constant query parameters.
5. **`availableValue=` in `modify-parameter` is destructive.** To add one value, read the file and list all values on the new line.

## Typical workflow

```bash
xml-gen validate --type skd Schema.xml                                          # 1. validate
xml-gen skd edit Schema.xml add-field "Цена: decimal(15,2) ;; Количество: decimal(15,3)"
xml-gen skd edit Schema.xml add-total "Цена: Среднее ;; Количество: Сумма"
xml-gen skd edit Schema.xml set-field-role "СуммаНач @balance balanceGroupName=Сумма balanceType=OpeningBalance"
xml-gen skd edit Schema.xml patch-query "СубконтоДт1) В => СубконтоКт1) В @once"
xml-gen validate --type skd --level semantic Schema.xml                         # 3. final validation
```

After final validation, if the `1c-form-viewer` MCP is connected, run `open_preview` + `capture_preview` on the schema: this is a visual check of the datasets, fields, and structure. View only: [../references/form-viewer-mcp.md](../references/form-viewer-mcp.md).

## Related skills

- [skd-dsl](../skd-dsl/) — generate SKD from scratch, `set-structure` with `@name=`.
- [xml-generation](../SKILL.md) — `validate`, `replace-text`, §3.

---
depends_on:
  - skd-dsl
  - framework/skills/tool-usage/platform-data/xml-generation/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---

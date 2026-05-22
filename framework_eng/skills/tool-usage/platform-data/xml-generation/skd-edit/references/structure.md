# Variant Structure

Operation: `modify-structure`.

For **creating** a structure from scratch, use `set-structure` from skd-dsl (compile). This Skill covers only targeted modification of grouping fields in an already existing named group.

## modify-structure — change the grouping fields of an existing group

Shorthand: `"Field1, Field2, ... @name=GroupName"`.

```
"Валюта @name=ДанныеОтчета"
"Валюта, НаименованиеБанка @name=ДанныеОтчета"
"details @name=ДанныеОтчета"
"Организация > Номенклатура @name=Основная"
```

### Semantics

- Finds the group by `<dcsset:name>GroupName</dcsset:name>`.
- **Replaces only `<groupItems>`** — the list of fields used for grouping.
- **Preserves** unchanged:
  - `<selection>` — group selection items;
  - `<order>` — group sorting;
  - `<filter>` — group filter;
  - `<conditionalAppearance>` — conditional appearance of the group;
  - `<outputParameters>` — group output parameters.
- `@name=` is **required**. Without an explicit group name, the operation fails with an error — it is ambiguous which group to patch.

### groupItems Syntax

| Form | Semantics |
|-------|-----------|
| `Field1, Field2` | Multiple fields in one grouping (one group groups by multiple dimensions). |
| `Field1 > Field2` | Nested level — `>` creates a sub-group. |
| `details` | Detail records (instead of grouping fields). |

> **Note.** The notation `Field1 > Field2 @name=X` modifies group `X` and replaces its `groupItems` with a chain of nested groupings. This is a significant change — usually `modify-structure` is applied to a single flat group.

### When to use

- Change the dimension by which the report is built without losing formatting, filters, or selection settings.
- Switch the group to `details` mode or vice versa.

### When NOT to use

- Rebuild the entire structure tree from scratch -> `set-structure` from skd-dsl (full replacement with loss of all group settings).
- Give a group a name if it did not have one -> the structure must be rebuilt through `set-structure ... @name=...`.

## Edge cases

| Case | Behavior |
|--------|-----------|
| `@name=` is not specified | Error: «name is required for modify-structure». |
| No group with the specified name exists | Error: «group not found». |
| Two variants with the same group name | Uses `--variant` (the first one by default). Use an explicit `--variant <name>`. |
| The group had `details`, the new value is `Field1` | `groupItems` are replaced: details → one dimension. Selection/CA are preserved. |
| The group had `Field1`, the new value is `details` | Similarly: groupItems become detail records. |
| The field in `groupItems` is missing from `dataSet` | It will be written as-is; the error will appear during `validate --level semantic`. |

## Not included in this skill

| Task | Solution |
|--------|---------|
| Create a structure from scratch | `set-structure` in skd-dsl. |
| Fully rewrite the variant structure | `set-structure` in skd-dsl. |
| Change Selection/CA/order of one group | Currently — rebuild the variant through skd-dsl. There are no point operations on the contents of a group (except `groupItems`) in this skill. |

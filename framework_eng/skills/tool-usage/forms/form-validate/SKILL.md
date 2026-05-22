---
name: form-validate
description: "Validation of a managed 1C form. Use after creating or modifying a form to verify correctness"
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-validate — Form Validator

Checks a managed form's Form.xml for structural errors: ID uniqueness, presence of companion elements, correctness of DataPath and command references.

## Usage

```
/form-validate <FormPath>
```

## Parameters

| Parameter | Required | Default | Description |
|-----------|:--------:|---------|-------------|
| FormPath  | yes      | —       | Path to the Form.xml file |
| MaxErrors | no       | 30      | Stop after N errors |

## Command

```bash
xmlgen validate --type form "<FormPath>"
```

With a JSON report:
```bash
xmlgen validate --type form --output json "<FormPath>"
```

> Implementation: Java-CLI `xmlgen validate` (replacement for the Python script). Error codes are `FORM-001..008` (structure), `FORM-101..120` (semantics).

## Checks Performed

| # | Check | Severity |
|---|---|---|
| 1 | Root element `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` is present, id="-1" | ERROR |
| 3 | Unique element IDs (separate pool) | ERROR |
| 4 | Unique attribute IDs (separate pool) | ERROR |
| 5 | Unique command IDs (separate pool) | ERROR |
| 6 | Companion elements (ContextMenu, ExtendedTooltip, etc.) | ERROR |
| 7 | DataPath → refers to an existing attribute (with resolution taken into account) | ERROR |
| 8 | Button CommandName → refers to an existing command | ERROR |
| 9 | Events have non-empty handler names | ERROR |
| 10 | Commands have Action (handler) | ERROR |
| 11 | No more than one MainAttribute | ERROR |
| 12 | BaseForm: presence and version (when extending) | OK / WARN |
| 13 | callType values: Before, After, Override | ERROR |
| 14 | Extension ID >= 1000000 for added attrs/commands | WARN |
| 15 | callType without BaseForm — invalid structure | WARN |

## Resolving Complex DataPath

Check #7 performs multi-stage path resolution before looking up the attribute. Processing order:

### 1. Numeric indices and UUIDs — silent-skip

The platform generates opaque DataPath values that cannot be resolved from Form.xml:

| Form | Example | Action |
|------|---------|--------|
| Numeric index | `10`, `1000003` | Skip without error |
| UUID reference | `1/0:a917a122-f663-4c45-8de0-fd5104007de3` | Skip without error |

Skip pattern: `^\d+$` or `^\d+/\d+:[0-9a-fA-F-]+$`.

> Engineering task: implement this in `xmlgen validate` before segment parsing.

### 2. `~<Attr>.*` — current row of the dynamic list

The `~` prefix is shorthand for "the current row of the element". It is used together with `DynamicList`:

```
~Список.Ссылка  →  корневой реквизит: Список
```

Algorithm: remove `~`, split by `.`, take the first segment as the attribute name. The remaining segments are fields of the list object itself; the validator does not check that they exist (they are resolved by the platform at runtime).

> Engineering task: after removing `~`, resolution is identical to the standard `Attr.*` path.

### 3. `Items.<Table>.CurrentData.*` — field from the current row of a table

Access to the current row of a form table element through the `Items` collection:

```
Items.Список.CurrentData.Ссылка
```

Resolution algorithm:
1. If the first segment is `Items`: expected form is `Items.<TableName>.CurrentData.<Field>`.
2. Find the form element with tag `Table` and name `<TableName>`.
   - Not found → ERROR: `table element '<TableName>' not found`.
3. Read the `DataPath` of the found table; remove `[N]` and `~`.
   - Table without `DataPath` (possible in dynamic forms) → accept silently, without error.
4. Take the first segment of the resulting path as the root attribute and check that it exists in `attrMap`.

Other `Items.*` forms (not `Items.<T>.CurrentData`) → WARN: `unknown Items.* shape`.

> Engineering task: implement this in `xmlgen validate` in the DataPath-resolution block before the general `attrMap` check.

### 4. Overall Resolution Order (Summary)

```
DataPath
  ├─ числовой / UUID  →  silent-skip
  ├─ начинается с '~'  →  снять '~', перейти к п.5
  ├─ начинается с 'Items.'  →  резолв через элемент-таблицу (п.3)
  └─ иначе
5. Снять индексы [N], взять первый сегмент → проверить в attrMap
```

## Output

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

Return code: 0 = all checks passed, 1 = errors present.

Checks 12–15 are activated automatically when `<BaseForm>` is detected.

Use after `/form-compile`, `/form-edit`, or manual editing of Form.xml to detect structural errors before building the EPF.

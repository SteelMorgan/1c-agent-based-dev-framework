---
name: form-validate
description: Validation of a 1C managed form. Use after creating or modifying a form to verify correctness
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-validate — Form validator

Validates the Form.xml of a managed form for structural issues: ID uniqueness, presence of companion elements, and correctness of DataPath and command references.

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
python3 scripts/form-validate.py -FormPath "<path>"
```

## Checks performed

| # | Check | Severity |
|---|---|---|
| 1 | Root element `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` present, id="-1" | ERROR |
| 3 | Unique element IDs (separate pool) | ERROR |
| 4 | Unique attribute IDs (separate pool) | ERROR |
| 5 | Unique command IDs (separate pool) | ERROR |
| 6 | Companion elements (ContextMenu, ExtendedTooltip, etc.) | ERROR |
| 7 | DataPath → references an existing attribute | ERROR |
| 8 | CommandName of buttons → references an existing command | ERROR |
| 9 | Events have non-empty handler names | ERROR |
| 10 | Commands have an Action (handler) | ERROR |
| 11 | No more than one MainAttribute | ERROR |
| 12 | BaseForm: presence and version (for extensions) | OK / WARN |
| 13 | callType values: Before, After, Override | ERROR |
| 14 | Extension ID ≥ 1000000 for added attrs/commands | WARN |
| 15 | callType without BaseForm — incorrect structure | WARN |

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

Exit code: 0 = all checks passed, 1 = there are errors.

### Extensions

When `<BaseForm>` is detected, additional checks are automatically enabled:
- Validity of `callType` values (Before/After/Override)
- Extension ID ≥ 1000000 for added attributes and commands
- Presence of version on `<BaseForm>`

Forms without `<BaseForm>` are only run through the standard checks.

## When to use

- **After `/form-compile`**: ensure the generated form is correct
- **After `/form-edit`**: verify added elements, especially in extension forms
- **After manual editing of Form.xml**: confirm IDs are unique, companions are present, and references are valid
- **During debugging**: catch form structure issues before building the EPF

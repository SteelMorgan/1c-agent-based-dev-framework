---
name: form-validate
description: Validation of a managed 1C form. Use after creating or modifying the form to verify correctness
argument-hint: <FormPath>
allowed-tools:
  - Bash
  - Read
  - Glob
---

# /form-validate — Form validator

Checks the Form.xml of a managed form for structural errors: unique IDs, presence of companion elements, correctness of DataPath references, and commands.

## Usage

```
/form-validate <FormPath>
```

## Parameters

| Parameter  | Required | Default | Description                    |
|-----------|:------------:|--------------|-----------------------------|
| FormPath  | yes           | —            | Path to the Form.xml file       |
| MaxErrors | no          | 30           | Stop after N errors |

## Command

```bash
xmlgen validate --type form "<FormPath>"
```

With a JSON report:
```bash
xmlgen validate --type form --output json "<FormPath>"
```

> Implementation: Java CLI `xmlgen validate` (replacement for the Python script). Error codes are `FORM-001..008` (structure), `FORM-101..120` (semantics).

## Checks

| # | Check | Severity |
|---|---|---|
| 1 | Root element `<Form>`, version="2.17" | ERROR / WARN |
| 2 | `<AutoCommandBar>` is present, id="-1" | ERROR |
| 3 | Unique element IDs (separate pool) | ERROR |
| 4 | Unique attribute IDs (separate pool) | ERROR |
| 5 | Unique command IDs (separate pool) | ERROR |
| 6 | Companion elements (ContextMenu, ExtendedTooltip, etc.) | ERROR |
| 7 | DataPath → refers to an existing attribute | ERROR |
| 8 | CommandName of buttons → refers to an existing command | ERROR |
| 9 | Events have non-empty handler names | ERROR |
| 10 | Commands have an Action (handler) | ERROR |
| 11 | No more than one MainAttribute | ERROR |
| 12 | BaseForm: presence and version (when extending) | OK / WARN |
| 13 | callType values: Before, After, Override | ERROR |
| 14 | Extension IDs >= 1000000 for added attrs/commands | WARN |
| 15 | callType without BaseForm — invalid structure | WARN |

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

Exit code: 0 = all checks passed, 1 = errors found.

Checks 12–15 are activated automatically when `<BaseForm>` is detected.

Use after `/form-compile`, `/form-edit`, or manual edits to Form.xml to catch structural errors before building an EPF.

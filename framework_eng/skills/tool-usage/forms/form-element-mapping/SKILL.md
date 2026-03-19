---
name: form-element-mapping
description: Finding the programmatic name of a form element by its visible text (synonym). Title → Name mapping algorithm for elements created in the Designer and programmatically.
---

# Form Element Mapping: visible text → programmatic name

## When to apply

- Writing Vanessa scenarios: steps use programmatic names (`Name`), but the screen shows synonyms (`Title`)
- Programmatic form modification: need to find an element by what the user sees
- Form analysis: understand which element corresponds to a specific field on screen

## Search algorithm

### Step 1: Form XML (Designer elements)

Elements added via the Designer are stored in `Form.xml`. Each element has `Name` (programmatic) and `Title` (visible text, may be multilingual).

**Search:** grep by `Title` in `Form.xml`, take `Name` from the same block.

```bash
grep -B5 "Customer" path/to/Form.xml | grep "<Name>"
```

### Step 2: Metadata object attribute synonyms

If the form element is bound to an object attribute (document, catalog), its `Title` may be inherited from the attribute synonym in the metadata object XML.

```bash
grep -A10 "<Name>Counterparty" path/to/Documents/Quote.xml
# → <Synonym> ... <v8:content>Customer</v8:content>
```

### Step 3: Form module (programmatically created elements)

If not found in `Form.xml` — the element is created programmatically. Search in the form module (`Module.bsl`):

```bash
grep -n "Customer" path/to/Form/Module.bsl
```

### Step 4: OnCreateAtServer call tree

If not found directly in the form module — the element is created in nested procedures called from `ПриСозданииНаСервере` / `OnCreateAtServer`.

> The event name is usually in Russian (`ПриСозданииНаСервере`), but in English-language configurations it may be in English (`OnCreateAtServer`).

Algorithm:
1. Find the `ПриСозданииНаСервере` (or `OnCreateAtServer`) handler
2. Trace all calls downward (nested procedures, common module calls)
3. Search for `Items.Add(...)` and `.Title` assignments in each called procedure

### Step 5: LSP (if available)

Use `definition` / `references` to trace calls from `ПриСозданииНаСервере`.

## Common pitfalls

| Pitfall | Solution |
|---|---|
| Field synonym on screen ≠ form element name | Always check `Name`, don't use `Title` as the name |
| Table column inherits synonym from tabular section attribute | Search in metadata object XML, not just Form.xml |
| Element added via `DSSL_DFI` | Look for `DSSL_DFI.AddField(...)` in the `OnCreateAtServer` call stack |
| Value format in tables: `10,00` instead of `10` | Numeric fields display with formatting — use formatted value in Vanessa assertions |
| Platform buttons in Russian, metadata in English | Buttons like `Записать`, `Провести`, `Создать` always in session interface language |

## Value formats in 1C tables

When checking values in Vanessa via `таблица содержит строки:`:

| Type | On screen | In Vanessa assertion |
|---|---|---|
| Number | `15,00` | `'15,00'` (with comma and decimals) |
| Boolean | checkbox ☑/☐ | `'Да'`/`'Нет'` (ru) or `'Yes'`/`'No'` (en) |
| Date | `19.03.2026` | `'19.03.2026'` |
| Empty ref | empty | `''` |

---
depends_on:
  - framework/skills/tool-usage/forms/form-info/SKILL.md
---

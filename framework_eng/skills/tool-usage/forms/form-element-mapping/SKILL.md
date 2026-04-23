---
name: form-element-mapping
description: "Finding the programming name of a form element by its visible text (synonym). Title → Name mapping algorithm for elements created in the configurator and programmatically."
---

# Form element mapping: visible text → programming name

## When to use

- Writing Vanessa scenarios: steps use programming names (`Name`), while synonyms (`Title`) are visible on screen
- Programmatic form modification: you need to find an element by what the user sees
- Form analysis: understand which element corresponds to a specific field on the screen

## Search algorithm

### Step 1: Form XML (configurator elements)

Elements added through the configurator are stored in `Form.xml`. Each element has `Name` (programming name) and `Title` (visible text, can be multilingual).

```
Form.xml:
  <AutoCommandBar>  → Name="FormCommandBar"
  <InputField>      → Name="Counterparty", Title="Customer" (en) / "Контрагент" (ru)
```

**Search:** grep for `Title` in `Form.xml`, take `Name` from the same block.

```bash
# Search for an element by visible text
grep -B5 "Customer" path/to/Form.xml | grep "<Name>"
```

If `Title` is multilingual, search by the required language:
```xml
<Title>
  <v8:item>
    <v8:lang>en</v8:lang>
    <v8:content>Customer</v8:content>
  </v8:item>
  <v8:item>
    <v8:lang>ru</v8:lang>
    <v8:content>Контрагент</v8:content>
  </v8:item>
</Title>
```

### Step 2: Synonyms of metadata object attributes

If a form element is bound to an object attribute (Документ, Справочник), its `Title` may be inherited from the attribute synonym in the metadata object XML.

```bash
# Search for the attribute synonym in the document metadata
grep -A10 "<Name>Counterparty" path/to/Documents/Quote.xml
# → <Synonym> ... <v8:content>Customer</v8:content>
```

### Step 3: Form module (programmatically created elements)

If the element is not found in `Form.xml`, it was created programmatically. Search in the form module (`Module.bsl`):

```bsl
// Typical pattern for creating an element:
Item = Items.Add("DSSL_MyField", Type("FormField"), ...);
Item.Title = NStr("en='My Field';ru='Моё поле'");
```

**Search:** grep for the synonym text in the form `Module.bsl`.

```bash
grep -n "Customer\|Контрагент" path/to/Form/Module.bsl
```

### Step 4: Call tree of the form creation event

If it is not found directly in the form module, the element is created in nested procedures called from the `ПриСозданииНаСервере` / `OnCreateAtServer` handler.

> The event name is usually in Russian (`ПриСозданииНаСервере`), but in English configurations it may be in English (`OnCreateAtServer`).

Algorithm:
1. Find the `ПриСозданииНаСервере` handler (or `OnCreateAtServer`) in the form module
2. Trace all calls downward (nested procedures, calls to common modules)
3. Search for `Items.Add(...)` and `.Title` assignments in each called procedure

Common places where elements are created programmatically:
- `DSSL_DFI` — the project's library for programmatic form modification
- Procedures such as `DSSL_OnCreateAtServer`, `DSSL_ДополнитьФорму`
- Common modules with the `Переопределяемый` / `Overridable` suffix

### Step 5: LSP (if available)

If an LSP server is connected, use `definition` / `references` to trace calls from `ПриСозданииНаСервере`.

## Common pitfalls

| Pitfall | Solution |
|---|---|
| Field synonym on screen ≠ form element name | Always check `Name`, do not use `Title` as the name |
| Table column inherits synonym from a table-part attribute | Search in the metadata object XML, not only in `Form.xml` |
| Element added through `DSSL_DFI` | Search for the `DSSL_DFI.AddField(...)` call in the `ПриСозданииНаСервере` stack |
| Value format in a table: `10,00` instead of `10` | Numeric fields are displayed with formatting - use the formatted value in Vanessa checks |
| Platform buttons are in Russian, metadata is in English | Buttons `Save`, `Post`, `Create`, `Add` - always use the session UI language |

## Value format in 1C tables

When checking values in Vanessa with `table contains rows:`, take the format into account:

| Type | On screen | In the Vanessa check |
|---|---|---|
| Number | `15,00` | `'15,00'` (with a comma and decimals) |
| Boolean | checkmark ☑/☐ | `'Yes'`/`'No'` |
| Date | `19.03.2026` | `'19.03.2026'` |
| Empty reference | empty | `''` |

---
depends_on:
  - framework/skills/tool-usage/forms/form-info/SKILL.md
---

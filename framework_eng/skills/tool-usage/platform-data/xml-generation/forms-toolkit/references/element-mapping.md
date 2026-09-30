# Form element mapping: visible text → programmatic name

## When to apply

- Writing Vanessa scenarios: steps use programmatic names (`Name`), while synonyms (`Title`) are visible on screen
- Programmatic form modification: need to find an element by what the user sees
- Form analysis: understand which element is responsible for a specific field on the screen

## Search algorithm

### Step 1: Form XML (configuration elements)

Elements added through the configurator are stored in `Form.xml`. Each element has `Name` (programmatic name) and `Title` (visible text, can be multilingual).

```
Form.xml:
  <AutoCommandBar>  → Name="FormCommandBar"
  <InputField>      → Name="Counterparty", Title="Customer" (en) / "Контрагент" (ru)
```

**Search:** grep by `Title` in `Form.xml`, take `Name` from the same block.

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

### Step 2: Metadata object attribute synonyms

If a form element is bound to an object attribute (document, catalog), its `Title` can inherit from the attribute synonym in the metadata object XML.

```bash
# Search for the attribute synonym in the document metadata
grep -A10 "<Name>Counterparty" path/to/Documents/Quote.xml
# → <Synonym> ... <v8:content>Customer</v8:content>
```

### Step 3: Form module (programmatically created elements)

If it is not found in `Form.xml`, it was created programmatically. Search in the form module (`Module.bsl`):

```bsl
// Typical pattern for creating an element:
Item = Items.Add("DSSL_MyField", Type("FormField"), ...);
Item.Title = NStr("en='My Field';ru='Моё поле'");
```

**Search:** grep by the synonym text in the form `Module.bsl`.

```bash
grep -n "Customer\|Контрагент" path/to/Form/Module.bsl
```

### Step 4: Event call tree for form creation

If it is not found directly in the form module, the element is created in nested procedures called from `ПриСозданииНаСервере` / `OnCreateAtServer`.

> The event name is usually in Russian (`ПриСозданииНаСервере`), but in English-language configurations it may be in English (`OnCreateAtServer`).

Algorithm:
1. Find the `ПриСозданииНаСервере` handler (or `OnCreateAtServer`) in the form module
2. Trace all calls downward (nested procedures, common module calls)
3. Look for `Items.Add(...)` and setting `.Title` in each called procedure

Common places where elements are created programmatically:
- `DSSL_DFI` — project form modification library
- Procedures like `DSSL_OnCreateAtServer`, `DSSL_ДополнитьФорму`
- Common modules with the suffix `Переопределяемый` / `Overridable`

### Step 5: LSP (if available)

If an LSP server is connected, use `definition` / `references` to trace calls from `ПриСозданииНаСервере`.

## Common pitfalls

| Pitfall | Solution |
|---|---|
| Field synonym on screen ≠ form element name | Always check `Name`, do not use `Title` as the name |
| Table column inherits synonym from the tabular section attribute | Search in the metadata object XML, not only in Form.xml |
| Element added through `DSSL_DFI` | Search for the `DSSL_DFI.AddField(...)` call in the `ПриСозданииНаСервере` stack |
| Value format in the table: `10,00` instead of `10` | Numeric fields are displayed with formatting — use the formatted value in Vanessa checks |
| Platform buttons in Russian, metadata in English | Buttons `Записать`, `Провести`, `Создать`, `Добавить` — always in the session UI language |

## Value format in 1C tables

When checking values in Vanessa via `table contains rows:`, account for the format:

| Type | On screen | In Vanessa check |
|---|---|---|
| Number | `15,00` | `'15,00'` (with comma and decimals) |
| Boolean | checkmark ☑/☐ | `'Да'`/`'Нет'` (ru) or `'Yes'`/`'No'` (en) |
| Date | `19.03.2026` | `'19.03.2026'` |
| Empty reference | blank | `''` |

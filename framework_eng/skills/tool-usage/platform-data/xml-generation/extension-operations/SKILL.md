---
name: extension-operations
description: Operations with 1С configuration extensions (CFE) — init, borrow, diff, validate. Use when creating extensions, borrowing objects, analyzing the composition and interceptors.
---

# Extension Operations (CFE)

Working with 1С configuration extensions.

## When to apply

| Trigger | Action |
|---------|----------|
| Need to create an extension | `extension init --name <Name> --config <configPath> <output_dir>` |
| Need to borrow an object from a configuration | `extension borrow <extPath> <configPath> "Type.Name"` |
| Need to borrow a form | `extension borrow <extPath> <configPath> "Catalog.Name.Form.FormName"` |
| Need to analyze an extension | `extension diff <extPath> <configPath>` |
| Need to validate an extension | `extension validate <extPath>` |

## Commands

### extension init

Create a configuration extension.

```bash
xml-gen extension init --name <Name> --config <configPath> [--purpose Patch|Customization|AddOn] [--prefix <Prefix>] <output_dir>
```

**Parameters:**
- `--name` — the extension name
- `--config` — the path to the base configuration (used to read CompatibilityMode and DefaultLanguage)
- `--purpose` — the purpose (default: Customization)
- `--prefix` — the name prefix (default: derived from the name)

### extension borrow

Borrowing an object from the base configuration.

```bash
xml-gen extension borrow <extensionPath> <configPath> "<objectSpec>"
```

**objectSpec format:**
- `Catalog.Товары` — borrow the object
- `Catalog.Товары.Form.ФормаЭлемента` — borrow the form
- `Справочник.Товары` — Russian synonyms are supported
- `Catalog.Товары ;; Document.Заказ` — batch (separator `;;`)

**What happens during borrowing:**
1. Reads the UUID of the object from the base configuration
2. Generates XML with ObjectBelonging=Adopted
3. Creates an ExtendedConfigurationObject link
4. Registers in ChildObjects of the extension (canonical order)
5. When borrowing a form: copies Form.xml as BaseForm and creates Module.bsl

### extension diff

Extension analysis: composition, interceptors, and transfer validation.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

**Mode A (overview):**
- List of all objects: [BORROWED] / [OWN]
- BSL interceptors (&Перед, &После, &ИзменениеИКонтроль, &Вместо)
- Form analysis (borrowed vs own)

**Mode B (transfer validation):**
- Search for `&ИзменениеИКонтроль` decorators
- Verification of `#Вставка` / `#КонецВставки` blocks
- Comparison with modules of the base configuration

### extension validate

Extension validation (9 checks).

```bash
xml-gen extension validate <extensionPath>
```

**Checks:** MetaDataObject/Configuration, InternalInfo (7 ClassId), Properties (ObjectBelonging, Name, Purpose, Prefix), enum values, ChildObjects, DefaultLanguage, language files, object catalogs, borrowed objects (ObjectBelonging=Adopted + ExtendedConfigurationObject).

## Key CFE concepts

### ObjectBelonging
- `Adopted` — a borrowed object (a copy from the base configuration)
- `Own` (missing) — the extension's own object

### ID ranges
- Base elements: 1–999999
- Extension elements: 1000000+

### BSL interceptors
```bsl
&Перед("ПриСозданииНаСервере")
Процедура ДССЛ_ПриСозданииНаСервере(Отказ, СтандартнаяОбработка)
    // Код перехвата
КонецПроцедуры
```

### Transfer markers
```bsl
#Область ДССЛ_Вставка  // или #Вставка
    // Собственный код
#КонецОбласти
```

## Russian type synonyms
Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule and others (25 mappings).

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

---
name: extension-operations
description: "Operations with 1С configuration extensions (CFE) — init, borrow, diff, validate. Use when creating extensions, borrowing objects, analyzing contents, and interceptors."
---

# Extension Operations (CFE)

Working with 1С configuration extensions.

## When to use

| Trigger | Action |
|---------|----------|
| Need to create an extension | `extension init --name <Name> --config <configPath> <output_dir>` |
| Need to borrow an object from the configuration | `extension borrow <extPath> <configPath> "Type.Name"` |
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
- `--name` — extension name
- `--config` — path to the base configuration (to read CompatibilityMode and DefaultLanguage)
- `--purpose` — purpose (default: Customization)
- `--prefix` — name prefix (default: from the name)

### extension borrow

Borrowing an object from the base configuration.

```bash
xml-gen extension borrow <extensionPath> <configPath> "<objectSpec>"
```

**objectSpec format:**
- `Catalog.Товары` — borrow an object
- `Catalog.Товары.Form.ФормаЭлемента` — borrow a form
- `Справочник.Товары` — Russian synonyms are supported
- `Catalog.Товары ;; Document.Заказ` — batch (separator `;;`)

**What happens during borrowing:**
1. Reads the object UUID from the base configuration
2. Generates XML with ObjectBelonging=Adopted
3. Creates an ExtendedConfigurationObject reference
4. Registers in the extension ChildObjects (canonical order)
5. When borrowing a form: copies Form.xml as BaseForm, creates Module.bsl

### extension diff

Extension analysis: contents, interceptors, transfer check.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

**Mode A (overview):**
- List of all objects: [BORROWED] / [OWN]
- BSL interceptors (&Перед, &После, &ИзменениеИКонтроль, &Вместо)
- Form analysis (borrowed vs own)

**Mode B (transfer check):**
- Search for `&ИзменениеИКонтроль` decorators
- Check `#Вставка` / `#КонецВставки` blocks
- Compare with base configuration modules

### extension validate

Validate an extension (9 checks).

```bash
xml-gen extension validate <extensionPath>
```

**Checks:** MetaDataObject/Configuration, InternalInfo (7 ClassId), Properties (ObjectBelonging, Name, Purpose, Prefix), enum values, ChildObjects, DefaultLanguage, language files, object directories, borrowed objects (ObjectBelonging=Adopted + ExtendedConfigurationObject).

## Key CFE concepts

### ObjectBelonging
- `Adopted` — borrowed object (copy from the base configuration)
- `Own` (absent) — own extension object

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

Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule, etc. (25 mappings).

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

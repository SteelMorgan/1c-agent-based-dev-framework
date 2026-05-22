---
name: extension-operations
description: "Operations with 1C configuration extensions (CFE) — init, borrow, diff, validate. Use when creating extensions, borrowing objects, analyzing composition, and interceptors."
---

# Extension Operations (CFE)

Working with 1C configuration extensions.

## When to use

| Trigger | Action |
|---------|----------|
| Need to create an extension | `extension init --name <Name> --config <configPath> <output_dir>` |
| Need to borrow an object from the configuration | `extension borrow <extPath> <configPath> "Type.Name"` |
| Need to borrow a form | `extension borrow <extPath> <configPath> "Catalog.Name.Form.FormName"` |
| Need to add a property and display it on a standard form | `extension borrow ... "Type.Name.Form.X" --borrow-main-attribute form` |
| Need to generate a method interceptor | `extension patch-method <extPath> --module "Catalog.X.Form.Y" --method "ПриЗаписи" --type Before` |
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

### extension diff

Extension analysis: composition, interceptors, transfer check.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

- **Mode A** (default): list of objects [BORROWED]/[OWN], BSL interceptors, form analysis.
- **Mode B**: search for `&ИзменениеИКонтроль`, check `#Вставка`/`#КонецВставки`, compare with the base configuration.

### extension borrow --borrow-main-attribute

Without the flag, the borrowed form does not have DataPath — `form-edit` will fail when trying to display an extension attribute.

```bash
# Реквизиты, уже выведенные на форме (рекомендуется)
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute form

# Все реквизиты и табличные части объекта
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute all
```

- `form` — only attributes already displayed on the form (optimal in most cases).
- `all` — all object attributes (needed if you plan to display attributes that are not yet on the form).

Typical scenario: borrow with `--borrow-main-attribute form` → add an attribute → `form-edit`.

**Important:** if the object has already been borrowed, the CLI adds what is missing and does not overwrite it.

### extension patch-method

Generation of a method interceptor in the extension module.

```bash
xml-gen extension patch-method <extPath> \
  --module "Catalog.X.Form.Y" \
  --method "ПриЗаписи" \
  --type Before|After|Instead|ModificationAndControl \
  [--config <configPath>]
```

**Parameters:**
- `<extPath>` — path to the extension directory
- `--module` — module path in the format `Type.Name.ModuleType` or `Type.Name.Form.FormName`
- `--method` — name of the intercepted method in the standard configuration
- `--type` — interception type (see table)
- `--config` — path to the base configuration (required for `ModificationAndControl`)

**Interception types:**

| `--type` | BSL decorator | Purpose |
|----------|--------------|------------|
| `Before` | `&Перед` | Code before the original method call |
| `After` | `&После` | Code after the original method call |
| `Instead` | `&Вместо` | Full replacement of the original method |
| `ModificationAndControl` | `&ИзменениеИКонтроль` | Copy of the original method body with insertion markers |

**ModulePath → BSL file mapping:**

| ModulePath | File in the extension |
|------------|-------------------|
| `Catalog.X.ObjectModule` | `Catalogs/X/Ext/ObjectModule.bsl` |
| `Catalog.X.ManagerModule` | `Catalogs/X/Ext/ManagerModule.bsl` |
| `Catalog.X.Form.Y` | `Catalogs/X/Forms/Y/Ext/Form/Module.bsl` |
| `CommonModule.X` | `CommonModules/X/Ext/Module.bsl` |
| `Document.X.ObjectModule` | `Documents/X/Ext/ObjectModule.bsl` |
| `Document.X.Form.Y` | `Documents/X/Forms/Y/Ext/Form/Module.bsl` |
| `InformationRegister.X.RecordSetModule` | `InformationRegisters/X/Ext/RecordSetModule.bsl` |

Similarly for Report, DataProcessor, and other object types.

**The interceptor procedure name** is formed with the extension NamePrefix: `Расш1_ПриЗаписи` (read from the extension `Configuration.xml`).

**For `ModificationAndControl`:** the CLI reads the body of the original method from `--config` and inserts it with `#Вставка`/`#КонецВставки` markers. Requires `--config`.

**If the file already exists:** appends the procedure to the end of the module, does not overwrite it.

### extension validate

Validation of the extension (9 checks).

```bash
xml-gen extension validate <extensionPath>
```

**Checks:** MetaDataObject/Configuration, InternalInfo (7 ClassId), Properties (ObjectBelonging, Name, Purpose, Prefix), enum values, ChildObjects, DefaultLanguage, language files, object directories, borrowed objects (ObjectBelonging=Adopted + ExtendedConfigurationObject).

## Key CFE concepts

- **ObjectBelonging:** `Adopted` — borrowed object; absence of the attribute — own.
- **ID ranges:** Base 1–999999, Extension 1000000+.
- **BSL interceptors:** decorators `&Перед`, `&После`, `&Вместо`, `&ИзменениеИКонтроль` above a procedure with the extension prefix.
- **Transfer markers:** `#Вставка` / `#КонецВставки` (or `#Область <Префикс>_Вставка`).
- **Russian synonyms:** Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule, etc. — supported in all commands.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

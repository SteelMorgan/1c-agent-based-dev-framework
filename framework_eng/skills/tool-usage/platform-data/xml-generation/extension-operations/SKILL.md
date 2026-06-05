---
name: extension-operations
description: "Operations with 1C configuration extensions (CFE) - init, borrowing objects, generating method interceptors, and analyzing extension composition. Helps manage CFE via xml-gen extension init/borrow/diff/validate."
---

# Extension Operations (CFE)

Working with 1C configuration extensions.

## When to use

| Trigger | Action |
|---------|----------|
| Need to create an extension | `extension init <output_dir> <Name> [--config-path <configPath>]` |
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
xml-gen extension init <output_dir> <Name> [--config-path <configPath>] [--purpose Patch|Customization|AddOn] [--prefix <Prefix>]
```

**Parameters (positional):**
- `<output_dir>` — directory where the extension will be created (first positional argument)
- `<Name>` — extension name (second positional argument)

**Parameters (flags):**
- `--config-path` — path to the base configuration (to read CompatibilityMode, DefaultLanguage, and Language UUID). Without it, `[WARN] Language ExtendedConfigurationObject set to zeros`.
- `--purpose` — purpose (default: Customization)
- `--prefix` — name prefix (default: from the name with `_` suffix)

**Anti-pattern (old syntax):** `--name <Name>` and `--config <Path>` do NOT work — the CLI accepts name and output as positional arguments, and the flag for the base configuration is called `--config-path`.

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

Extension analysis: composition, interceptors, transfer verification.

```bash
xml-gen extension diff <extensionPath> <configPath> [--mode A|B]
```

- **Mode A** (default): list of objects [BORROWED]/[OWN], BSL interceptors, form analysis.
- **Mode B**: search for `&ИзменениеИКонтроль`, check `#Вставка`/`#КонецВставки`, compare with the base configuration.

### extension borrow --borrow-main-attribute

Without the flag, the borrowed form does not have DataPath — `form-edit` will fail when trying to display an extension attribute.

```bash
# Attributes already displayed on the form (recommended)
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute form

# All attributes and tabular sections of the object
xml-gen extension borrow <extPath> <configPath> "Catalog.Номенклатура.Form.ФормаЭлемента" --borrow-main-attribute all
```

- `form` — only attributes already displayed on the form (optimal in most cases).
- `all` — all object attributes and tabular sections (needed if you plan to display attributes that are not yet on the form).

Typical scenario: borrow with `--borrow-main-attribute form` → add an attribute → `form-edit`.

**Important:** if the object has already been borrowed, the CLI adds the missing parts and does not overwrite them.

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
- **Russian synonyms:** Справочник → Catalog, Документ → Document, РегистрСведений → InformationRegister, ОбщийМодуль → CommonModule, and others — supported in all commands.

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

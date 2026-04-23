---
name: xml-gen-cli
description: "Правила работы с XmlGen CLI — validate, edit-команды (form/role/epf/skd), edit replace-text (побайтовая замена) и универсальные операции (form/template/help add/remove). Используй при валидации XML и модификации существующих файлов."
---

# XmlGen CLI — validate и edit-команды

## Команда validate

```bash
xml-gen validate [--type <form|role|skd|mxl|epf>] [--format designer|edt] [--level structure|semantic] [--output text|json] <file> [file2 ...]
```

Exit codes: 0=ok, 1=errors, 2=warnings (можно продолжать)

Доменные validate:
```bash
xml-gen config validate <configPath>
xml-gen subsystem validate <subsystemPath>
xml-gen interface validate <ciPath>
xml-gen meta validate <objectPath>
xml-gen extension validate <extensionPath>
```

## Универсальные операции

```bash
xml-gen form add <objectPath> <formName>
xml-gen form remove <objectPath> <formName>
xml-gen template add <objectPath> <name> --type <spreadsheet|html|text|dcs|binary>
xml-gen template remove <objectPath> <name>
xml-gen help add <objectPath>
```

## Побайтовая замена текста (edit replace-text)

Безопасная замена текста в XML без нормализации line endings. Сохраняет bare LF (0x0A) внутри `<v8:content>`, CRLF между тегами, UTF-8 BOM.

**Используй вместо Claude Code Edit tool** когда файл содержит мультилайн контент в тегах `<v8:content>` (тултипы, описания).

```bash
xml-gen edit replace-text <file> --old "<old_text>" --new "<new_text>" [--all] [--dry-run] [--backup] [--validate] [--encoding utf-8-sig|utf-8]
```

| Флаг | Описание |
|------|----------|
| `--old` / `--new` | Пара для замены. Можно указать несколько: `--old "A" --new "B" --old "C" --new "D"` |
| `--all` | Заменить все вхождения (по умолчанию — только первое) |
| `--dry-run` | Показать результат без записи файла |
| `--backup` | Создать .bak перед записью |
| `--validate` | Проверить XML well-formedness после замены |
| `--encoding` | `utf-8-sig` (default, сохраняет BOM) или `utf-8` (без BOM) |

Exit codes: 0=замена выполнена, 1=текст не найден, 2=ошибка.

Вывод (stdout): JSON `{"file": "...", "replacements": N, "bytes_before": N, "bytes_after": N}`

```bash
# Замена Type на TypeSet
xml-gen edit replace-text src/xml/Documents/биг_Операция.xml \
  --old '<v8:Type>cfg:DocumentRef.big_Order_OKX</v8:Type>' \
  --new '<v8:TypeSet>cfg:DefinedType.биг_ОрдерБиржи</v8:TypeSet>'

# Множественная замена во всех вхождениях с dry-run
xml-gen edit replace-text Form.xml \
  --old 'cfg:DefinedType.биг_ДокументыПозиций' \
  --new 'cfg:DefinedType.биг_ПозицияБиржи' \
  --all --dry-run
```

## Edit-команды

### Form

```bash
xml-gen form add-attribute --name <Name> --type <Type> <Form.xml>
xml-gen form add-element --type <XmlType> --name <Name> [--path <DataPath>] [--parent <ParentName>] [--after <AfterName>] <Form.xml>
xml-gen form add-command --name <Name> [--title <Title>] [--action <Action>] <Form.xml>
xml-gen form remove-element --name <Name> <Form.xml>
xml-gen form move-element --name <Name> [--after <Name>] [--before <Name>] [--into <ParentName>] <Form.xml>
```

**XmlType:** `InputField`, `CheckBoxField`, `Button`, `UsualGroup`, `Table`, `LabelDecoration`, `Page`, `Pages` и др.

### Role (Rights.xml)

```bash
xml-gen role add-object --name <ObjectName> --rights <Right1,Right2,...> <Rights.xml>
xml-gen role add-right --object <ObjectName> --name <RightName> --value <true|false> <Rights.xml>
```

### EPF (корневой XML)

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

### SKD

```bash
xml-gen skd add-parameter --name <Name> [--title <Title>] [--type <Type>] <Schema.xml>
xml-gen skd add-field --dataset <DataSetName> --name <FieldName> --path <DataPath> [--title <Title>] <Schema.xml>
```

## Правила для агента

1. **Перед модификацией** — `validate` для проверки текущего состояния.
2. **После модификации** — авто-валидация; при ошибке rollback автоматический.
3. **EPF** — корневой XML: `output/MyProcessor.xml`. Form в EPF: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`.

## Правильно / Неправильно

```bash
# ❌ form add-element без --path (DataPath не создастся, элемент не отобразит данные)
xml-gen form add-element --type InputField --name Наименование Form.xml

# ✅ --path связывает элемент с реквизитом
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

```bash
# ❌ role add-object с "view" (нужен enum: Read,View)
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# ✅ права через запятую, регистр из enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

## Workarounds

| Проблема | Решение |
|----------|---------|
| "Parent element not found" | Проверь точное имя родителя в Form.xml (регистр важен) |
| "Object already exists" (role) | `role add-right` вместо `add-object` |
| "DataSet not found" (skd) | Проверь имя набора данных в Schema.xml |
| Edit tool ломает line endings в XML | Используй `xml-gen edit replace-text` вместо Claude Code Edit |

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

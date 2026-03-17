---
name: xml-gen-cli
description: Правила работы с XmlGen CLI — validate (form/role/skd/mxl/epf/config/subsystem/interface/meta/extension), edit-команды и универсальные операции (form/template/help add/remove). Используй при валидации XML и модификации существующих Form, Role, EPF, SKD.
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

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

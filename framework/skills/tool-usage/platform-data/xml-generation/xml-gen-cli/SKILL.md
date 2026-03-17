---
name: xml-gen-cli
description: Правила работы с XmlGen CLI — validate (form/role/skd/mxl/epf/config/subsystem/interface/meta/extension), edit-команды и универсальные операции (form/template/help add/remove). Используй при валидации XML и модификации существующих Form, Role, EPF, SKD.
---

# XmlGen CLI — validate и edit-команды

Правила вызова xml-gen для валидации и модификации существующих XML-файлов.

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно проверить Form.xml перед коммитом | `validate form Form.xml` |
| Нужно проверить Rights.xml, Template.xml | `validate role <path>` или `validate skd <path>` |
| Нужно добавить реквизит в существующую форму | `form add-attribute --name ... --type ... Form.xml` |
| Нужно добавить UI-элемент (поле, кнопку) | `form add-element --type ... --name ... [--path ...] [--parent ...] Form.xml` |
| Нужно добавить права на объект в роль | `role add-object --name ... --rights ... Rights.xml` |
| Нужно добавить реквизит в обработку | `epf add-attribute --name ... <EpfRoot.xml>` |
| Нужно добавить параметр/поле в SKD | `skd add-parameter` или `skd add-field` |
| Нужно валидировать конфигурацию | `config validate` |
| Нужно валидировать объект метаданных | `meta validate` |
| Нужно валидировать расширение | `extension validate` |
| Нужно добавить форму к справочнику/документу | `form add` |
| Нужно добавить шаблон к объекту | `template add` |
| Нужно добавить справку | `help add` |
| Перед edit-командой — проверить текущее состояние | Сначала `validate`, потом edit |

## Вызов

```bash
xml-gen <command> [args...]
```

> `xml-gen` устанавливается автоматически при установке фреймворка (`python tools/install.py`).
> Если команда недоступна — запустите: `python tools/install.py --install-xml-gen`

## Команда validate

Проверка XML-файлов метаданных 1С (Form, Role, SKD, MXL, EPF).

**Синтаксис:**
```bash
xml-gen validate [--type <form|role|skd|mxl|epf>] [--format designer|edt] [--level structure|semantic] [--output text|json] [--src-root <path>] <file> [file2 ...]
```

**Exit codes:** 0=ok, 1=errors, 2=warnings

**Примеры:**
```bash
xml-gen validate form Form.xml
xml-gen validate role output/Roles/МояРоль/Ext/Rights.xml
xml-gen validate --type skd --output json Template.xml

# Валидация конфигурации, подсистемы, интерфейса, объекта метаданных, расширения
xml-gen config validate <configPath>
xml-gen subsystem validate <subsystemPath>
xml-gen interface validate <ciPath>
xml-gen meta validate <objectPath>
xml-gen extension validate <extensionPath>
```

## Универсальные операции

Операции, применимые к любым объектам метаданных (Catalog, Document, EPF и т.д.).

```bash
# Добавить/удалить форму (любой объект: Catalog, Document, EPF и т.д.)
xml-gen form add <objectPath> <formName>
xml-gen form remove <objectPath> <formName>

# Добавить/удалить шаблон
xml-gen template add <objectPath> <name> --type <spreadsheet|html|text|dcs|binary>
xml-gen template remove <objectPath> <name>

# Добавить справку
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

**Rights:** `Read`, `Insert`, `Update`, `Delete`, `View`, `Edit`, `Posting`, `UndoPosting` и др.

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

## Сценарии

**Сценарий: Добавить реквизит и элемент в форму**
1. `validate form Form.xml` — проверить текущее состояние
2. `form add-attribute --name IsFavorite --type boolean Form.xml`
3. `form add-element --type CheckBoxField --name IsFavorite --path IsFavorite --parent ГруппаОсновное Form.xml`
4. При ошибке "Parent element not found" — проверь имя родителя в Form.xml (регистр важен)

**Сценарий: Добавить права в роль**
1. `validate role Rights.xml`
2. `role add-object --name Catalog.Номенклатура --rights Read,Insert,Update Rights.xml`
3. При "Object already exists" — используй `role add-right` для изменения существующего объекта

## Workarounds

| Проблема | Причина | Решение |
|----------|---------|---------|
| "Parent element not found" | Имя родителя в `--parent` не совпадает с XML | Проверь точное имя в Form.xml (ChildItems, группа) |
| "Object already exists" (role) | Объект уже в Rights.xml | Используй `role add-right` вместо `add-object` |
| "DataSet not found" (skd) | Имя DataSet в `--dataset` неверно | Проверь имя набора данных в Schema.xml |
| "Validation failed after modification" | Edit-команда создала невалидный XML | Rollback автоматический; исправь аргументы и повтори |
| Exit code 2 от validate | Есть WARNING, нет ERROR | Обычно можно продолжать; проверь вывод |

## Правильно / Неправильно

```bash
# ❌ Неправильно — form add-element без --path для поля с данными (DataPath не создастся)
xml-gen form add-element --type InputField --name Наименование Form.xml

# ✅ Правильно — --path связывает элемент с реквизитом
xml-gen form add-element --type InputField --name Наименование --path Наименование Form.xml
```

> Без `--path` элемент не будет отображать данные. InputField, CheckBoxField и др. требуют DataPath для привязки к реквизиту.

```bash
# ❌ Неправильно — role add-object с preset "view" (CLI ожидает список через запятую: Read,View)
xml-gen role add-object --name Catalog.Номенклатура --rights view Rights.xml

# ✅ Правильно — права через запятую, регистр из enum RoleRight
xml-gen role add-object --name Catalog.Номенклатура --rights Read,View Rights.xml
```

> CLI парсит `--rights` как строку и разбивает по запятой. Значения должны совпадать с enum (Read, Insert, Update, Delete, View, Edit и т.д.).

## Правила для агента

1. **Перед модификацией** — запусти `validate` для проверки текущего состояния.
2. **После модификации** — edit-команды выполняют авто-валидацию; при ошибке изменения не сохраняются (rollback).
3. **Пути к файлам** — используй абсолютные или относительные пути к конкретному файлу.
4. **EPF** — корневой XML: `output/MyProcessor.xml`
5. **Form в EPF** — путь: `output/MyProcessor/Forms/MainForm/Ext/Form.xml`

## См. также

- [epf-operations](../epf-operations/) — epf init, add-form, add-template
- [form-dsl](../form-dsl/) — form compile
- [role-dsl](../role-dsl/) — role compile

---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

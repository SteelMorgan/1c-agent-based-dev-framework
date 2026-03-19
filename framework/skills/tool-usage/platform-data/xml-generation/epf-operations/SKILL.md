---
name: epf-operations
description: Операции с внешними обработками 1С (EPF) — создание, добавление форм и шаблонов. Используй при epf init, add-form, add-template, add-attribute, add-tabular-section.
---

# EPF Operations

Работа с внешними обработками 1С (ExternalDataProcessor).

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать новую внешнюю обработку | `epf init --name <Name> <output_dir>` |
| Нужно добавить форму к обработке | `epf add-form --epf <EpfName> --name <FormName> <output_dir>` |
| Нужно добавить печатную форму (табличный документ) | `epf add-template --epf ... --name ... --type spreadsheet <output_dir>` |
| Нужно добавить HTML/текстовый шаблон | `epf add-template --type html` или `--type text` |
| Нужно добавить реквизит в существующую обработку | `epf add-attribute --name ... --type ... <EpfRoot.xml>` |
| Нужно создать внешний отчёт (ERF) | `epf init --type report --name ... <output_dir>` |
| Нужно добавить ТЧ в существующую обработку | `epf add-tabular-section --name ... <EpfRoot.xml>` |

## Команды

### epf init

Создать новую внешнюю обработку.

**Синтаксис:**
```bash
xml-gen epf init --name <Name> [--type processor|report] [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Параметры:**
- `--name <Name>` — имя обработки/отчёта (обязательно)
- `--type processor|report` — тип: `processor` (обработка, по умолчанию) или `report` (внешний отчёт, ERF)
- `--format designer|edt` — формат вывода (по умолчанию: designer)
- `--synonym <Synonym>` — синоним (опционально)
- `<output_dir>` — директория вывода (обязательно, позиционный аргумент)

**Пример:**
```bash
xml-gen epf init --name MyProcessor output/
xml-gen epf init --format designer --name DataImport --synonym "Импорт данных" .
xml-gen epf init --type report --name SalesReport --synonym "Отчёт по продажам" output/
```

### epf add-form

Добавить форму к обработке.

**Синтаксис:**
```bash
xml-gen epf add-form --epf <EpfName> --name <FormName> [--format designer|edt] [--synonym <Synonym>] [--default] <output_dir>
```

**Пример:**
```bash
xml-gen epf add-form --epf MyProcessor --name MainForm output/
xml-gen epf add-form --epf MyProcessor --name SettingsForm --default output/
```

### epf add-template

Добавить шаблон к обработке.

**Синтаксис:**
```bash
xml-gen epf add-template --epf <EpfName> --name <TemplateName> --type <Type> [--format designer|edt] [--synonym <Synonym>] <output_dir>
```

**Типы:** `spreadsheet`, `html`, `text`

**Пример:**
```bash
xml-gen epf add-template --epf MyProcessor --name PrintForm --type spreadsheet output/
xml-gen epf add-template --epf MyProcessor --name WebPage --type html output/
```

### epf add-attribute / add-tabular-section (редактирование)

Модификация существующего EPF (добавление реквизитов, ТЧ). Работает с корневым XML обработки.

```bash
xml-gen epf add-attribute --name <Name> [--type <Type>] [--synonym <Synonym>] <EpfRoot.xml>
xml-gen epf add-tabular-section --name <Name> [--synonym <Synonym>] <EpfRoot.xml>
```

**Пример:**
```bash
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники --synonym "Сотрудник" output/MyProcessor.xml
```

## Ключевые пути (Designer)

- Корневой XML: `output/MyProcessor.xml`
- Модуль объекта: `output/MyProcessor/Ext/ObjectModule.bsl`
- Form.xml: `output/MyProcessor/Forms/<FormName>/Ext/Form.xml`
- Template: `output/MyProcessor/Templates/<Name>/Ext/Template.xml`

Интеграция: `form compile form.json <Form.xml path>`, `mxl compile template.json <Template path>`.

## Правильно / Неправильно

```bash
# ❌ Неправильно — epf add-form с позиционными аргументами (CLI ожидает --epf, --name)
xml-gen epf add-form MyProcessor MainForm

# ✅ Правильно — именованные аргументы, output_dir в конце
xml-gen epf add-form --epf MyProcessor --name MainForm output/
```

> CLI парсит только именованные аргументы. `output_dir` — последний позиционный аргумент (каталог, где лежит `<EpfName>.xml`).

```bash
# ❌ Неправильно — epf add-attribute к Form.xml (add-attribute редактирует корневой XML обработки)
xml-gen epf add-attribute --name Employee MyProcessor/Forms/MainForm/Ext/Form.xml

# ✅ Правильно — путь к корневому XML обработки (MyProcessor.xml)
xml-gen epf add-attribute --name Employee --type CatalogRef.Сотрудники output/MyProcessor.xml
```

> `epf add-attribute` добавляет реквизит в **обработку**, а не в форму. Для формы используй `form add-attribute` с Form.xml.


---
depends_on: []
metadata:
  category: 1c-development
  version: "1.0"
---

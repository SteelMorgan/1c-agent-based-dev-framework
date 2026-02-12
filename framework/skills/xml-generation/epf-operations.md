---
name: epf-operations
description: Операции с внешними обработками 1С (EPF) — создание, добавление форм и шаблонов
category: 1c-development
tags: [1c, epf, external-data-processor]
version: 1.0.0
---

# EPF Operations

Работа с внешними обработками 1С (ExternalDataProcessor).

## Команды

### epf init

Создать новую внешнюю обработку.

**Синтаксис:**
```bash
java -jar xml-gen.jar epf init <name> [--output <dir>]
```

**Параметры:**
- `<name>` — имя обработки (обязательно)
- `--output <dir>` — директория вывода (по умолчанию: текущая)

**Пример:**
```bash
java -jar xml-gen.jar epf init MyProcessor
```

**Результат:**
```
MyProcessor/
├── MyProcessor.xml          # Метаданные обработки
└── Ext/
    └── ObjectModule.bsl     # Модуль объекта (пустой)
```

**MyProcessor.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<MetaDataObject xmlns="http://v8.1c.ru/8.3/MDClasses" ...>
  <ExternalDataProcessor uuid="...">
    <Properties>
      <Name>MyProcessor</Name>
      <Synonym>
        <v8:item>
          <v8:lang>ru</v8:lang>
          <v8:content>My Processor</v8:content>
        </v8:item>
      </Synonym>
    </Properties>
    <ChildObjects/>
  </ExternalDataProcessor>
</MetaDataObject>
```

### epf add-form

Добавить форму к обработке.

**Синтаксис:**
```bash
java -jar xml-gen.jar epf add-form <epf-dir> <form-name>
```

**Параметры:**
- `<epf-dir>` — директория обработки
- `<form-name>` — имя формы

**Пример:**
```bash
java -jar xml-gen.jar epf add-form MyProcessor MainForm
```

**Результат:**
```
MyProcessor/
├── MyProcessor.xml          # Обновлён (добавлена ссылка на форму)
└── Forms/
    └── MainForm/
        ├── Form.xml         # Метаданные формы
        └── Ext/
            └── Form/
                └── Module.bsl  # Модуль формы (пустой)
```

**Обновление MyProcessor.xml:**
```xml
<ChildObjects>
  <Form>MainForm</Form>
</ChildObjects>
```

**Form.xml:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<Form xmlns="http://v8.1c.ru/8.3/xcf/logform" ...>
  <Properties>
    <Name>MainForm</Name>
  </Properties>
  <Items/>
  <Attributes/>
  <Commands/>
</Form>
```

### epf add-template

Добавить шаблон к обработке.

**Синтаксис:**
```bash
java -jar xml-gen.jar epf add-template <epf-dir> <template-name> <type>
```

**Параметры:**
- `<epf-dir>` — директория обработки
- `<template-name>` — имя шаблона
- `<type>` — тип шаблона: `spreadsheet`, `html`, `text`

**Пример 1: Табличный документ**
```bash
java -jar xml-gen.jar epf add-template MyProcessor PrintForm spreadsheet
```

**Результат:**
```
MyProcessor/
├── MyProcessor.xml
└── Templates/
    └── PrintForm/
        ├── Template.xml     # Метаданные шаблона
        └── Ext/
            └── Template.mxl # Табличный документ (пустой)
```

**Пример 2: HTML документ**
```bash
java -jar xml-gen.jar epf add-template MyProcessor WebPage html
```

**Результат:**
```
MyProcessor/
└── Templates/
    └── WebPage/
        ├── Template.xml
        └── Ext/
            └── Template.html  # HTML документ (пустой)
```

## Структура EPF

### Полная структура обработки

```
MyProcessor/
├── MyProcessor.xml              # Корневой файл метаданных
├── Ext/
│   └── ObjectModule.bsl         # Модуль объекта
├── Forms/
│   ├── MainForm/
│   │   ├── Form.xml             # Метаданные формы
│   │   └── Ext/
│   │       └── Form/
│   │           └── Module.bsl   # Модуль формы
│   └── SettingsForm/
│       ├── Form.xml
│       └── Ext/
│           └── Form/
│               └── Module.bsl
└── Templates/
    ├── PrintForm/
    │   ├── Template.xml
    │   └── Ext/
    │       └── Template.mxl     # Табличный документ
    └── WebPage/
        ├── Template.xml
        └── Ext/
            └── Template.html    # HTML документ
```

### Метаданные обработки (MyProcessor.xml)

**Основные элементы:**
```xml
<ExternalDataProcessor uuid="...">
  <Properties>
    <Name>MyProcessor</Name>
    <Synonym>...</Synonym>
    <Comment>Описание обработки</Comment>
  </Properties>
  <ChildObjects>
    <Form>MainForm</Form>
    <Form>SettingsForm</Form>
    <Template>PrintForm</Template>
    <Template>WebPage</Template>
  </ChildObjects>
</ExternalDataProcessor>
```

**Важно:**
- UUID генерируется автоматически
- ChildObjects обновляется при добавлении форм/шаблонов
- Файл имеет UTF-8 BOM (EF BB BF)

## Примеры использования

### Пример 1: Простая обработка с формой

```bash
# 1. Создать обработку
java -jar xml-gen.jar epf init DataImport

# 2. Добавить форму
java -jar xml-gen.jar epf add-form DataImport MainForm

# 3. Результат готов к использованию в 1С
```

### Пример 2: Обработка с печатной формой

```bash
# 1. Создать обработку
java -jar xml-gen.jar epf init ReportGenerator

# 2. Добавить форму настроек
java -jar xml-gen.jar epf add-form ReportGenerator SettingsForm

# 3. Добавить шаблон печати
java -jar xml-gen.jar epf add-template ReportGenerator PrintTemplate spreadsheet

# 4. Структура готова
```

### Пример 3: Обработка с HTML шаблоном

```bash
# 1. Создать обработку
java -jar xml-gen.jar epf init WebExport

# 2. Добавить HTML шаблон
java -jar xml-gen.jar epf add-template WebExport PageTemplate html

# 3. Добавить текстовый шаблон
java -jar xml-gen.jar epf add-template WebExport EmailTemplate text
```

## Интеграция с другими модулями

### Генерация формы из JSON

После создания формы через `epf add-form`, можно заполнить её содержимое:

```bash
# 1. Создать форму
java -jar xml-gen.jar epf add-form MyProcessor MainForm

# 2. Создать JSON DSL для формы
cat > form.json <<EOF
{
  "attributes": [
    {"name": "Наименование", "type": "string(100)"},
    {"name": "Количество", "type": "number(15,2)"}
  ],
  "elements": [
    {"type": "input", "name": "Наименование", "dataPath": "Наименование"},
    {"type": "input", "name": "Количество", "dataPath": "Количество"}
  ]
}
EOF

# 3. Сгенерировать Form.xml
java -jar xml-gen.jar form compile form.json MyProcessor/Forms/MainForm/Form.xml
```

### Генерация табличного документа из JSON

```bash
# 1. Создать шаблон
java -jar xml-gen.jar epf add-template MyProcessor PrintForm spreadsheet

# 2. Создать JSON DSL для MXL
cat > template.json <<EOF
{
  "areas": [
    {"name": "Header", "rows": [
      {"cells": [{"text": "Отчёт", "span": 3}]}
    ]},
    {"name": "Row", "rows": [
      {"cells": [
        {"text": "[Наименование]"},
        {"text": "[Количество]"},
        {"text": "[Сумма]"}
      ]}
    ]}
  ]
}
EOF

# 3. Сгенерировать Template.xml
java -jar xml-gen.jar mxl compile template.json MyProcessor/Templates/PrintForm/Template.xml
```

## Особенности формата Designer

### BOM (Byte Order Mark)

- **MyProcessor.xml** — UTF-8 с BOM (EF BB BF)
- **Form.xml** — UTF-8 без BOM
- **Template.xml** — UTF-8 без BOM

### UUID

Каждый объект имеет уникальный UUID:
```xml
<ExternalDataProcessor uuid="12345678-1234-1234-1234-123456789abc">
```

UUID генерируется автоматически при создании.

### ChildObjects

Порядок элементов в ChildObjects важен:
1. Сначала формы (Form)
2. Потом шаблоны (Template)

```xml
<ChildObjects>
  <Form>Form1</Form>
  <Form>Form2</Form>
  <Template>Template1</Template>
  <Template>Template2</Template>
</ChildObjects>
```

## Ограничения

### Текущие ограничения

1. **Только Designer формат** — EDT не поддерживается
2. **Пустые модули** — модули создаются пустыми, код нужно писать вручную
3. **Нет команд** — команды обработки не генерируются автоматически
4. **Нет реквизитов** — реквизиты обработки не генерируются

### Workaround

- **Модули** — пиши код вручную в .bsl файлах
- **Команды** — добавляй вручную в MyProcessor.xml
- **Реквизиты** — добавляй вручную в MyProcessor.xml

## Troubleshooting

### Проблема: "Directory already exists"

**Причина:** Обработка с таким именем уже существует

**Решение:**
```bash
# Удали старую или используй другое имя
rm -rf MyProcessor
java -jar xml-gen.jar epf init MyProcessor
```

### Проблема: "EPF directory not found"

**Причина:** Указан неверный путь к обработке

**Решение:**
```bash
# Проверь путь
ls -la MyProcessor/MyProcessor.xml

# Используй правильный путь
java -jar xml-gen.jar epf add-form ./MyProcessor MainForm
```

### Проблема: "Form already exists"

**Причина:** Форма с таким именем уже добавлена

**Решение:**
```bash
# Используй другое имя или удали старую
rm -rf MyProcessor/Forms/MainForm
java -jar xml-gen.jar epf add-form MyProcessor MainForm
```

## См. также

- [XML Generation](./xml-generation.md) — общее описание модуля
- [Form DSL](./form-dsl.md) — генерация содержимого форм
- [MXL DSL](./mxl-dsl.md) — генерация табличных документов
- [SPEC-002](../../docs/SPEC-002-xml-generation.md) — полная спецификация

## Версия

**Текущая версия:** 1.0.0  
**Статус:** Production Ready  
**Последнее обновление:** 2026-02-12

---
name: xml-generation
description: Генерация XML метаданных 1С из компактного JSON DSL. Поддержка 11 доменов — EPF, Form, MXL, SKD, Role, Config, Subsystem, Interface, Meta (23 типа объектов), Extension (CFE) + утилиты (template, help). ~45 операций CLI в формате Designer. Используй при создании конфигураций, внешних обработок, объектов метаданных, форм, ролей, отчётов, печатных форм, расширений.
---

# XML Generation Module

Модуль для генерации XML метаданных 1С из компактного JSON DSL. 11 доменов, ~45 операций.

## Быстрый старт

### Установка

`xml-gen` устанавливается автоматически при установке фреймворка:

```bash
python tools/1c-ai-agent-cli.py
```

Инсталлер соберёт JAR (требуется JDK 17+) и создаст команду `xml-gen` в `~/.local/bin/`.

Если `xml-gen` недоступен — переустановить вручную:
```bash
python tools/1c-ai-agent-cli.py --install-xml-gen
```

### Использование

```bash
# Создать внешнюю обработку (--name и output_dir обязательны)
xml-gen epf init --name MyProcessor output/

# Скомпилировать форму
xml-gen form compile form.json Form.xml

# Скомпилировать табличный документ
xml-gen mxl compile template.json Template.xml

# Скомпилировать схему компоновки данных
xml-gen skd compile schema.json Template.xml

# Скомпилировать роль (создаёт Roles/<Name>/Ext/Rights.xml)
xml-gen role compile role.json output/

# Валидация XML
xml-gen validate form Form.xml

# Конфигурация
xml-gen config init --name МояКонфигурация output/
xml-gen config info output/
xml-gen config validate output/

# Объекты метаданных (23 типа)
xml-gen meta compile meta.json output/
xml-gen meta info Catalogs/Товары
xml-gen meta edit Catalogs/Товары --op add-attribute "Вес: Number(15,3)"

# Подсистемы
xml-gen subsystem compile subsystem.json output/
xml-gen subsystem info Subsystems/Основное

# Расширения (CFE)
xml-gen extension init --name МоёРасширение --config output/ output_ext/
xml-gen extension borrow output_ext/ output/ "Catalog.Товары"
xml-gen extension diff output_ext/ output/

# Универсальные операции
xml-gen form add output/Catalogs/Товары MainForm
xml-gen template add output/Catalogs/Товары PrintForm --type spreadsheet
xml-gen help add output/Catalogs/Товары
```

## Поддерживаемые типы метаданных

| Тип | Статус | Навык |
|-----|--------|-------|
| Внешняя обработка (EPF) | ✅ | [epf-operations](../epf-operations/) |
| Управляемая форма (Form) | ✅ | [form-dsl](../form-dsl/) |
| Табличный документ (MXL) | ✅ | [mxl-dsl](../mxl-dsl/) |
| Схема компоновки данных (SKD) | ✅ | [skd-dsl](../skd-dsl/) |
| Роль (Role) | ✅ | [role-dsl](../role-dsl/) |
| Конфигурация (CF) | ✅ | [config-operations](../config-operations/) |
| Подсистема (Subsystem) + Интерфейс | ✅ | [subsystem-operations](../subsystem-operations/) |
| Объект метаданных (Meta, 23 типа) | ✅ | [meta-operations](../meta-operations/) |
| Расширение (CFE) | ✅ | [extension-operations](../extension-operations/) |
| Утилиты (template, help) | ✅ | [xml-gen-cli](../xml-gen-cli/) |

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать внешнюю обработку | `epf init` → `epf add-form` → [epf-operations](../epf-operations/) |
| Нужно создать форму с UI-элементами | `form compile` с JSON DSL → [form-dsl](../form-dsl/) |
| Нужно создать печатную форму (табличный документ) | `epf add-template --type spreadsheet` → `mxl compile` → [mxl-dsl](../mxl-dsl/) |
| Нужно создать отчёт (СКД) | `skd compile` с JSON DSL → [skd-dsl](../skd-dsl/) |
| Нужно создать роль с правами | `role compile` с JSON DSL → [role-dsl](../role-dsl/) |
| Нужно создать/изменить конфигурацию | `config init` / `config edit` → [config-operations](../config-operations/) |
| Нужно создать объект метаданных (Справочник, Документ, Регистр...) | `meta compile` → [meta-operations](../meta-operations/) |
| Нужно создать/редактировать подсистему | `subsystem compile` / `subsystem edit` → [subsystem-operations](../subsystem-operations/) |
| Нужно создать расширение | `extension init` → [extension-operations](../extension-operations/) |
| Нужно заимствовать объект в расширение | `extension borrow` → [extension-operations](../extension-operations/) |
| Нужно добавить форму/шаблон/справку к любому объекту | `form add` / `template add` / `help add` → [xml-gen-cli](../xml-gen-cli/) |
| Нужно изменить существующий XML (добавить реквизит, элемент) | `form add-attribute`, `epf add-attribute` и др. → [xml-gen-cli](../xml-gen-cli/) |
| Нужно проверить корректность XML | `validate` → [xml-gen-cli](../xml-gen-cli/) |

**Не используй** когда: нужен формат EDT (пока не поддерживается), нужны DataSetUnion/CalculatedFields в SKD (используй workaround в запросах).

## Детальная документация

- **[xml-gen-cli](../xml-gen-cli/)** — CLI: validate, edit-команды, утилиты (template, help)
- **[epf-operations](../epf-operations/)** — операции с внешними обработками
- **[form-dsl](../form-dsl/)** — JSON DSL для форм
- **[mxl-dsl](../mxl-dsl/)** — JSON DSL для табличных документов
- **[skd-dsl](../skd-dsl/)** — JSON DSL для схем компоновки данных
- **[role-dsl](../role-dsl/)** — JSON DSL для ролей
- **[config-operations](../config-operations/)** — операции с конфигурациями (init, info, edit, validate)
- **[subsystem-operations](../subsystem-operations/)** — подсистемы и интерфейсы
- **[meta-operations](../meta-operations/)** — объекты метаданных (23 типа: справочники, документы, регистры и др.)
- **[extension-operations](../extension-operations/)** — расширения (CFE): init, borrow, diff

## Сценарии использования

### Сценарий 1: Обработка с формой

```bash
# 1. Создать обработку
xml-gen epf init --name DataImport output/

# 2. Добавить форму
xml-gen epf add-form --epf DataImport --name MainForm output/

# 3. Создать JSON DSL для формы
cat > form.json <<EOF
{
  "attributes": [
    {"name": "ИмяФайла", "type": "string(255)"}
  ],
  "elements": [
    {"type": "input", "name": "ИмяФайла", "dataPath": "ИмяФайла"}
  ]
}
EOF

# 4. Сгенерировать Form.xml
xml-gen form compile form.json output/DataImport/Forms/MainForm/Ext/Form.xml
```

### Сценарий 2: Отчёт (SKD)

```bash
# Создать JSON DSL
cat > report.json <<EOF
{
  "dataSets": [{
    "name": "Продажи",
    "query": "ВЫБРАТЬ Организация, Сумма ИЗ РегистрНакопления.Продажи",
    "fields": [
      {"dataPath": "Организация", "title": "Организация"},
      {"dataPath": "Сумма", "title": "Сумма", "type": "number(15,2)"}
    ]
  }],
  "settingsVariants": [{
    "name": "Основной",
    "settings": {
      "selection": ["Организация", "Сумма"],
      "filter": ["Сумма > 0"],
      "order": ["Сумма desc"]
    }
  }]
}
EOF

# Сгенерировать Template.xml
xml-gen skd compile report.json Template.xml
```

## Архитектура

```
io.github.onec.xmlgen/
├── cli/           # CLI команды
├── dsl/           # JSON DSL классы
├── writer/        # XML генераторы (MetaWriter, ConfigWriter, SubsystemWriter, ExtensionWriter)
├── editor/        # XML редакторы (ConfigEditor, SubsystemEditor, InterfaceEditor, ExtensionEditor, ObjectContainerEditor)
├── validator/     # Валидаторы (MetaValidator, ConfigValidator, SubsystemValidator, InterfaceValidator, ExtensionValidator)
├── info/          # Info-принтеры (MetaInfoPrinter, ConfigInfoPrinter, SubsystemInfoPrinter, ExtensionDiffPrinter, SkdInfoPrinter, FormInfoPrinter, MxlInfoPrinter, RoleInfoPrinter)
├── model/         # Модели (MetadataTypeRegistry, UuidGenerator)
└── format/        # Форматы вывода (Designer/EDT)
```

## Ограничения

1. **Только Designer формат** — EDT будет добавлен позже
2. **SKD на 90%** — нет DataSetObject/Union, CalculatedFields
3. **Нет валидации ссылок** — не проверяются ссылки между объектами

### Workaround

- **DataSetObject/Union** → используй DataSetQuery с запросами
- **CalculatedFields** → используй вычисления в запросах
- **EDT формат** → конвертируй Designer → EDT через 1С

## Правильно / Неправильно

```bash
# ❌ Неправильно — epf init без --name и output_dir (CLI вернёт "--name is required")
xml-gen epf init MyProcessor

# ✅ Правильно — --name и output_dir обязательны, CLI парсит именованные аргументы
xml-gen epf init --name MyProcessor output/
```

> CLI требует явные `--name` и позиционный `output_dir`, т.к. не поддерживает старый синтаксис с позиционными аргументами.

```bash
# ❌ Неправильно — role compile с одним файлом на выход (создаётся структура каталогов)
xml-gen role compile role.json Roles/МояРоль.xml

# ✅ Правильно — output_dir, создаётся Roles/<Name>/Ext/Rights.xml
xml-gen role compile role.json output/
```

> RoleWriter создаёт полную структуру роли (метаданные + Rights.xml), а не один файл.

---
depends_on:
  - framework/skills/tool-usage/platform-data/xml-generation/epf-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/form-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/mxl-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/role-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/skd-dsl/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/xml-gen-cli/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/config-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/subsystem-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/meta-operations/SKILL.md
  - framework/skills/tool-usage/platform-data/xml-generation/extension-operations/SKILL.md
metadata:
  category: 1c-development
  version: "1.0"
---

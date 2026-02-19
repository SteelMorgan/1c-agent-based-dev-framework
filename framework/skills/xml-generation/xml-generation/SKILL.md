---
name: xml-generation
description: Генерация XML метаданных 1С из компактного JSON DSL. Поддержка EPF, Form, MXL, SKD, Role в формате Designer. Используй при создании внешних обработок, форм, ролей, отчётов, печатных форм.
metadata:
  category: 1c-development
  version: "1.0"
---

# XML Generation Module

Модуль для генерации XML метаданных 1С из компактного JSON DSL.

## Быстрый старт

### Установка

```bash
cd tools/xml-gen
./gradlew shadowJar
```

Результат: `build/libs/xml-gen-0.1.0-SNAPSHOT.jar`

**Вызов:** `java -jar build/libs/xml-gen-0.1.0-SNAPSHOT.jar <command> ...` или скопируй JAR как `xml-gen.jar`

### Использование

```bash
# Создать внешнюю обработку (--name и output_dir обязательны)
java -jar xml-gen.jar epf init --name MyProcessor output/

# Скомпилировать форму
java -jar xml-gen.jar form compile form.json Form.xml

# Скомпилировать табличный документ
java -jar xml-gen.jar mxl compile template.json Template.xml

# Скомпилировать схему компоновки данных
java -jar xml-gen.jar skd compile schema.json Template.xml

# Скомпилировать роль (создаёт Roles/<Name>/Ext/Rights.xml)
java -jar xml-gen.jar role compile role.json output/

# Валидация XML
java -jar xml-gen.jar validate form Form.xml
```

## Поддерживаемые типы метаданных

| Тип | Статус | Навык |
|-----|--------|-------|
| Внешняя обработка (EPF) | ✅ 100% | [epf-operations](../epf-operations/) |
| Управляемая форма (Form) | ✅ 100% | [form-dsl](../form-dsl/) |
| Табличный документ (MXL) | ✅ 100% | [mxl-dsl](../mxl-dsl/) |
| Схема компоновки данных (SKD) | ✅ 85% | [skd-dsl](../skd-dsl/) |
| Роль (Role) | ✅ 100% | [role-dsl](../role-dsl/) |

## Когда применять

| Триггер | Действие |
|---------|----------|
| Нужно создать внешнюю обработку | `epf init` → `epf add-form` → [epf-operations](../epf-operations/) |
| Нужно создать форму с UI-элементами | `form compile` с JSON DSL → [form-dsl](../form-dsl/) |
| Нужно создать печатную форму (табличный документ) | `epf add-template --type spreadsheet` → `mxl compile` → [mxl-dsl](../mxl-dsl/) |
| Нужно создать отчёт (СКД) | `skd compile` с JSON DSL → [skd-dsl](../skd-dsl/) |
| Нужно создать роль с правами | `role compile` с JSON DSL → [role-dsl](../role-dsl/) |
| Нужно изменить существующий XML (добавить реквизит, элемент) | `form add-attribute`, `epf add-attribute` и др. → [xml-gen-cli](../xml-gen-cli/) |
| Нужно проверить корректность XML | `validate` → [xml-gen-cli](../xml-gen-cli/) |

**Не используй** когда: нужен формат EDT (пока не поддерживается), нужны DataSetUnion/CalculatedFields в SKD (используй workaround в запросах).

## Детальная документация

- **[xml-gen-cli](../xml-gen-cli/)** — CLI: validate, edit-команды (add-attribute, add-element и др.)
- **[epf-operations](../epf-operations/)** — операции с внешними обработками
- **[form-dsl](../form-dsl/)** — JSON DSL для форм
- **[mxl-dsl](../mxl-dsl/)** — JSON DSL для табличных документов
- **[skd-dsl](../skd-dsl/)** — JSON DSL для схем компоновки данных
- **[role-dsl](../role-dsl/)** — JSON DSL для ролей

## Сценарии использования

### Сценарий 1: Обработка с формой

```bash
# 1. Создать обработку
java -jar xml-gen.jar epf init --name DataImport output/

# 2. Добавить форму
java -jar xml-gen.jar epf add-form --epf DataImport --name MainForm output/

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
java -jar xml-gen.jar form compile form.json output/DataImport/Forms/MainForm/Ext/Form.xml
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
java -jar xml-gen.jar skd compile report.json Template.xml
```

## Архитектура

```
io.github.onec.xmlgen/
├── cli/           # CLI команды
├── dsl/           # JSON DSL классы
├── writer/        # XML генераторы
├── model/         # Вспомогательные модели
└── format/        # Форматы вывода (Designer/EDT)
```

## Ограничения

1. **Только Designer формат** — EDT будет добавлен позже
2. **SKD на 85%** — нет DataSetObject/Union, CalculatedFields
3. **Нет валидации ссылок** — не проверяются ссылки между объектами
4. **Нет обратной конвертации** — только JSON → XML

### Workaround

- **DataSetObject/Union** → используй DataSetQuery с запросами
- **CalculatedFields** → используй вычисления в запросах
- **EDT формат** → конвертируй Designer → EDT через 1С

## Правильно / Неправильно

```bash
# ❌ Неправильно — epf init без --name и output_dir (CLI вернёт "--name is required")
java -jar xml-gen.jar epf init MyProcessor

# ✅ Правильно — --name и output_dir обязательны, CLI парсит именованные аргументы
java -jar xml-gen.jar epf init --name MyProcessor output/
```

> CLI требует явные `--name` и позиционный `output_dir`, т.к. не поддерживает старый синтаксис с позиционными аргументами.

```bash
# ❌ Неправильно — role compile с одним файлом на выход (создаётся структура каталогов)
java -jar xml-gen.jar role compile role.json Roles/МояРоль.xml

# ✅ Правильно — output_dir, создаётся Roles/<Name>/Ext/Rights.xml
java -jar xml-gen.jar role compile role.json output/
```

> RoleWriter создаёт полную структуру роли (метаданные + Rights.xml), а не один файл.

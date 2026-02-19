---
name: xml-generation
description: Генерация XML метаданных 1С из компактного JSON DSL. Поддержка EPF, Form, MXL, SKD, Role в формате Designer.
category: 1c-development
tags: [1c, xml, metadata, code-generation, dsl]
version: 1.0.0
---

# XML Generation Module

Модуль для генерации XML метаданных 1С из компактного JSON DSL.

## Возможности

### Поддерживаемые типы метаданных

| Тип | Команда | Формат | Статус |
|-----|---------|--------|--------|
| Внешняя обработка (EPF) | `epf` | Designer | ✅ 100% |
| Роль (Role) | `role` | Designer | ✅ 100% |
| Управляемая форма (Form) | `form` | Designer | ✅ 100% |
| Табличный документ (MXL) | `mxl` | Designer | ✅ 100% |
| Схема компоновки данных (SKD) | `skd` | Designer | ✅ 85% |

### Основные преимущества

1. **Компактный DSL** — JSON вместо многословного XML
2. **Автоматизация** — автогенерация UUID, ID, BOM, структуры файлов
3. **Типобезопасность** — автоопределение типов и квалификаторов
4. **Валидация** — проверка корректности на этапе генерации
5. **Читаемость** — понятный JSON вместо сложного XML

## Когда использовать

### ✅ Используй этот модуль когда:

- Нужно создать новую внешнюю обработку (EPF)
- Нужно создать управляемую форму с UI-элементами
- Нужно создать табличный документ (печатную форму)
- Нужно создать схему компоновки данных (отчёт)
- Нужно создать роль с правами доступа
- Нужно программно генерировать метаданные 1С
- Нужно автоматизировать создание типовых объектов

### ❌ Не используй когда:

- Нужно изменить существующий XML (используй ручное редактирование)
- Нужен формат EDT (пока не поддерживается)
- Нужны сложные вычисляемые поля в SKD (используй запросы)
- Нужно объединение наборов данных (DataSetUnion)

## Быстрый старт

### 1. Расположение модуля

```
tools/xml-gen/
├── build.gradle.kts
├── src/
│   ├── main/java/io/github/onec/xmlgen/
│   └── test/java/io/github/onec/xmlgen/
└── build/libs/xml-gen.jar
```

### 2. Сборка JAR

```bash
cd tools/xml-gen
./gradlew shadowJar
```

Результат: `build/libs/xml-gen-all.jar`

### 3. Базовое использование

```bash
# Создать внешнюю обработку
java -jar xml-gen.jar epf init MyProcessor

# Скомпилировать форму
java -jar xml-gen.jar form compile form.json Form.xml

# Скомпилировать табличный документ
java -jar xml-gen.jar mxl compile template.json Template.xml

# Скомпилировать схему компоновки данных
java -jar xml-gen.jar skd compile schema.json Template.xml

# Скомпилировать роль
java -jar xml-gen.jar role compile role.json Role.xml
```

## Детальная документация

Для каждого типа метаданных есть отдельный skill с примерами DSL:

- **[epf-operations](./epf-operations.md)** — внешние обработки
- **[form-dsl](./form-dsl.md)** — управляемые формы
- **[mxl-dsl](./mxl-dsl.md)** — табличные документы
- **[skd-dsl](./skd-dsl.md)** — схемы компоновки данных
- **[role-dsl](./role-dsl.md)** — роли и права

## Архитектура

### Компоненты модуля

```
io.github.onec.xmlgen/
├── cli/           # CLI команды
├── dsl/           # JSON DSL классы
├── writer/        # XML генераторы
├── model/         # Вспомогательные модели
└── format/        # Форматы вывода (Designer/EDT)
```

### Процесс генерации

```
JSON DSL → Десериализация → Валидация → XML Writer → Файлы метаданных
```

### Ключевые классы

- **Commands** — диспетчер CLI команд
- **EpfWriter** — генератор EPF
- **FormWriter** — генератор форм
- **MxlWriter** — генератор табличных документов
- **SkdWriter** — генератор схем компоновки
- **RoleWriter** — генератор ролей
- **TypeResolver** — резолвер типов 1С

## Примеры использования

### Пример 1: Создание простой обработки

```bash
# 1. Создать структуру
java -jar xml-gen.jar epf init MyProcessor

# 2. Добавить форму
java -jar xml-gen.jar epf add-form MyProcessor MainForm

# 3. Добавить табличный документ
java -jar xml-gen.jar epf add-template MyProcessor PrintForm spreadsheet
```

Результат:
```
MyProcessor/
├── MyProcessor.xml
└── Forms/
    └── MainForm/
        ├── Form.xml
        └── Ext/
            └── Form/
                └── Module.bsl
```

### Пример 2: Генерация формы из JSON

**form.json:**
```json
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
```

**Команда:**
```bash
java -jar xml-gen.jar form compile form.json Form.xml
```

### Пример 3: Генерация отчёта (SKD)

**report.json:**
```json
{
  "dataSets": [{
    "name": "Продажи",
    "query": "ВЫБРАТЬ Организация, Сумма ИЗ Продажи",
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
```

**Команда:**
```bash
java -jar xml-gen.jar skd compile report.json Template.xml
```

## Ограничения

### Текущие ограничения

1. **Только Designer формат** — EDT будет добавлен позже
2. **SKD на 85%** — нет DataSetObject/Union, CalculatedFields
3. **Нет валидации ссылок** — не проверяются ссылки между объектами
4. **Нет обратной конвертации** — только JSON → XML, не XML → JSON

### Workaround для ограничений

- **DataSetObject/Union** → используй DataSetQuery с запросами
- **CalculatedFields** → используй вычисления в запросах
- **EDT формат** → конвертируй Designer → EDT через 1С

## Тестирование

Модуль полностью покрыт тестами:

```bash
cd tools/xml-gen
./gradlew test
```

**Результат:** 36 тестов, все проходят

## Troubleshooting

### Проблема: "Cannot find java"

**Решение:** Установи Java 17+
```bash
java -version  # Должна быть 17+
```

### Проблема: "Invalid JSON"

**Решение:** Проверь синтаксис JSON
```bash
# Используй валидатор
cat form.json | jq .
```

### Проблема: "Unknown type"

**Решение:** Проверь поддерживаемые типы в TypeResolver
- Примитивы: string, number, boolean, date, uuid
- Ссылки: CatalogRef.Name, DocumentRef.Name
- Объекты: CatalogObject.Name, DocumentObject.Name

## См. также

- [SPEC-002: XML Generation Module](../../docs/SPEC-002-xml-generation.md)
- [EPF Operations](./epf-operations.md)
- [Form DSL](./form-dsl.md)
- [MXL DSL](./mxl-dsl.md)
- [SKD DSL](./skd-dsl.md)
- [Role DSL](./role-dsl.md)

## Версия

**Текущая версия:** 1.0.0  
**Статус:** Production Ready  
**Последнее обновление:** 2026-02-12

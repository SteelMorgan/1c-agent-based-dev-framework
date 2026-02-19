---
name: xml-generation
description: Генерация XML метаданных 1С из компактного JSON DSL. Поддержка EPF, Form, MXL, SKD, Role в формате Designer.
category: 1c-development
tags: [1c, xml, metadata, code-generation, dsl]
version: 1.0.0
status: production-ready
---

# XML Generation Module

Модуль для генерации XML метаданных 1С из компактного JSON DSL.

## Быстрый старт

### Установка

```bash
cd tools/xml-gen
./gradlew shadowJar
```

Результат: `build/libs/xml-gen-all.jar`

### Использование

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

## Поддерживаемые типы метаданных

| Тип | Статус | Документация |
|-----|--------|--------------|
| Внешняя обработка (EPF) | ✅ 100% | [epf-operations.md](./epf-operations.md) |
| Управляемая форма (Form) | ✅ 100% | [form-dsl.md](./form-dsl.md) |
| Табличный документ (MXL) | ✅ 100% | [mxl-dsl.md](./mxl-dsl.md) |
| Схема компоновки данных (SKD) | ✅ 85% | [skd-dsl.md](./skd-dsl.md) |
| Роль (Role) | ✅ 100% | [role-dsl.md](./role-dsl.md) |

## Когда использовать

### ✅ Используй этот модуль когда:

- Нужно создать новую внешнюю обработку
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

## Детальная документация

- **[xml-generation.md](./xml-generation.md)** — общее описание и архитектура
- **[epf-operations.md](./epf-operations.md)** — операции с внешними обработками
- **[form-dsl.md](./form-dsl.md)** — JSON DSL для форм
- **[mxl-dsl.md](./mxl-dsl.md)** — JSON DSL для табличных документов
- **[skd-dsl.md](./skd-dsl.md)** — JSON DSL для схем компоновки данных
- **[role-dsl.md](./role-dsl.md)** — JSON DSL для ролей

## Примеры

### Создание обработки с формой

```bash
# 1. Создать обработку
java -jar xml-gen.jar epf init DataImport

# 2. Добавить форму
java -jar xml-gen.jar epf add-form DataImport MainForm

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
java -jar xml-gen.jar form compile form.json DataImport/Forms/MainForm/Form.xml
```

### Создание отчёта (SKD)

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

## Технические детали

### Архитектура

```
io.github.onec.xmlgen/
├── cli/           # CLI команды
├── dsl/           # JSON DSL классы
├── writer/        # XML генераторы
├── model/         # Вспомогательные модели
└── format/        # Форматы вывода (Designer/EDT)
```

### Тестирование

```bash
cd tools/xml-gen
./gradlew test
```

**Результат:** 36 тестов, все проходят

### Статистика

- **LOC (production):** ~4120
- **LOC (tests):** ~1190
- **Тесты:** 36
- **Покрытие:** ~85%

## Ограничения

### Текущие ограничения

1. **Только Designer формат** — EDT будет добавлен позже
2. **SKD на 85%** — нет DataSetObject/Union, CalculatedFields
3. **Нет валидации ссылок** — не проверяются ссылки между объектами
4. **Нет обратной конвертации** — только JSON → XML

### Workaround

- **DataSetObject/Union** → используй DataSetQuery с запросами
- **CalculatedFields** → используй вычисления в запросах
- **EDT формат** → конвертируй Designer → EDT через 1С

## См. также

- [SPEC-002: XML Generation Module](../../../docs/SPEC-002-xml-generation.md) — полная спецификация
- [Framework Architecture](../../../docs/SPEC-001-framework-architecture.md) — архитектура фреймворка

## Версия

**Текущая версия:** 1.0.0  
**Статус:** Production Ready  
**Последнее обновление:** 2026-02-12  
**Автор:** 1C Agent Framework Team

# Преобразование навыков framework/skills в формат agentskills.io

**Дата:** 2026-02-12  
**Спецификация:** https://agentskills.io/specification

## Выполненные работы

### 1. Преобразовано навыков: 13

Все .md файлы из категорийных директорий преобразованы в навыки формата agentskills.io **с сохранением категорийной структуры**:

#### bsl-practices/ (6 навыков)
- `anti-patterns.md` → `anti-patterns/SKILL.md`
- `coding-standards.md` → `coding-standards/SKILL.md`
- `error-handling.md` → `error-handling/SKILL.md`
- `form-patterns.md` → `form-patterns/SKILL.md`
- `query-patterns.md` → `query-patterns/SKILL.md`
- `ssl-patterns.md` → `ssl-patterns/SKILL.md`

#### tool-usage/ (6 навыков)
- `code-navigation.md` → `code-navigation/SKILL.md`
- `log-analysis.md` → `log-analysis/SKILL.md`
- `metadata-discovery.md` → `metadata-discovery/SKILL.md`
- `search-before-write.md` → `search-before-write/SKILL.md`
- `syntax-checking.md` → `syntax-checking/SKILL.md`
- `test-execution.md` → `test-execution/SKILL.md`

#### spec-writing/ (1 навык)
- `spec-standard.md` → `spec-standard/SKILL.md`

### 2. Переименовано навыков: 2

Исправлены имена с underscore на дефисы:
- `agent-development_ext/` → `agent-development-ext/`
- `skill-creator_ext/` → `skill-creator-ext/`

### 3. Удалены служебные файлы

- `tool-usage/_capability-index.md` (индексный файл, не навык)

## Итоговая структура framework/skills/

```
framework/skills/
├── agent-development-ext/
│   └── SKILL.md
├── bsl-practices/
│   ├── anti-patterns/
│   │   └── SKILL.md
│   ├── coding-standards/
│   │   └── SKILL.md
│   ├── error-handling/
│   │   └── SKILL.md
│   ├── form-patterns/
│   │   └── SKILL.md
│   ├── query-patterns/
│   │   └── SKILL.md
│   └── ssl-patterns/
│       └── SKILL.md
├── skill-creator-ext/
│   └── SKILL.md
├── spec-writing/
│   └── spec-standard/
│       └── SKILL.md
├── tool-usage/
│   ├── code-navigation/
│   │   └── SKILL.md
│   ├── log-analysis/
│   │   └── SKILL.md
│   ├── metadata-discovery/
│   │   └── SKILL.md
│   ├── search-before-write/
│   │   └── SKILL.md
│   ├── syntax-checking/
│   │   └── SKILL.md
│   └── test-execution/
│       └── SKILL.md
├── xml-generation/          (пустая директория)
└── _template-skill.md       (шаблон)
```

**Всего навыков:** 15

## Процесс преобразования

Для каждого файла выполнено:

1. **Извлечение метаданных:**
   - `id: skill/xxx` → `name: xxx`
   - Заголовок + секция "Назначение" → `description`

2. **Преобразование frontmatter:**
   ```yaml
   # Было (старый формат фреймворка):
   ---
   id: skill/coding-standards
   type: skill
   depends_on: []
   ---
   
   # Стало (agentskills.io):
   ---
   name: coding-standards
   description: Стандарты кодирования BSL (1С). Этот навык учит агента писать код на встроенном языке 1С (BSL) в соответствии со стандартами платформы 1С:Предприятие и рекомендациями ИТС.
   ---
   ```

3. **Создание структуры:**
   - Создана поддиректория `{category}/{name}/`
   - Файл переименован в `SKILL.md`
   - Содержимое (body) сохранено без изменений

4. **Валидация:**
   - Имя поддиректории = поле `name` ✓
   - Формат `name`: lowercase + дефисы ✓
   - Обязательные поля присутствуют ✓

## Преимущества новой структуры

### ✓ Сохранена категоризация
Навыки остались сгруппированы по категориям:
- `bsl-practices/` — практики кодирования BSL
- `tool-usage/` — использование MCP-инструментов
- `spec-writing/` — написание спецификаций

### ✓ Соответствие спецификации agentskills.io
Каждый навык — это директория с `SKILL.md`:
- `category/skill-name/SKILL.md`

### ✓ Возможность расширения
В каждую директорию навыка можно добавить:
- `scripts/` — исполняемые скрипты
- `references/` — дополнительная документация
- `assets/` — статические ресурсы

## Соответствие спецификации agentskills.io

### ✓ Структура директорий
```
skill-name/
└── SKILL.md
```
Все 15 навыков соответствуют (внутри категорий).

### ✓ Формат SKILL.md
- YAML frontmatter с `name` и `description`
- Markdown body с инструкциями
- Все навыки соответствуют.

### ✓ Правила для поля `name`
- 1-64 символа
- Только lowercase буквы, цифры, дефисы
- Не начинается/не заканчивается дефисом
- Совпадает с именем поддиректории
- Все навыки соответствуют.

### ✓ Правила для поля `description`
- 1-1024 символа
- Описывает что делает навык и когда его использовать
- Все навыки соответствуют.

## Примеры преобразованных навыков

### bsl-practices/coding-standards/SKILL.md
```yaml
---
name: coding-standards
description: Стандарты кодирования BSL (1С). Этот навык учит агента писать код на встроенном языке 1С (BSL) в соответствии со стандартами платформы 1С:Предприятие и рекомендациями ИТС.
---
```

### tool-usage/syntax-checking/SKILL.md
```yaml
---
name: syntax-checking
description: Проверка синтаксиса (Syntax Checking). Навык учит агента **правильно использовать возможности проверки синтаксиса** BSL-кода.
---
```

### spec-writing/spec-standard/SKILL.md
```yaml
---
name: spec-standard
description: Навык написания спецификаций (SDD)...
---
```

## Не преобразованные файлы

- `_template-skill.md` — шаблон для создания новых навыков (не является навыком)
- `xml-generation/` — пустая директория (нет SKILL.md)

## Изменения в git

```
Переименовано: 15 файлов
  - 13 навыков: .md → поддиректория/SKILL.md
  - 2 навыка: _ext → -ext
Удалено: 1 файл (_capability-index.md)
```

## Заключение

✓ Все навыки в `framework/skills/` приведены в соответствие со спецификацией https://agentskills.io/specification:

- ✓ Единая структура: `category/skill-name/SKILL.md`
- ✓ Стандартный frontmatter: `name`, `description`
- ✓ Имена поддиректорий совпадают с полем `name`
- ✓ Формат имён: lowercase + дефисы (без underscore)
- ✓ Сохранена категоризация навыков
- ✓ Все навыки независимы и самодостаточны

Теперь каждый навык — это отдельная поддиректория внутри категории с SKILL.md, что соответствует стандарту agentskills.io и сохраняет логическую группировку навыков по категориям.

# Аудит навыков framework/skills по спецификации agentskills.io

**Дата:** 2026-02-12  
**Спецификация:** https://agentskills.io/specification  
**Область аудита:** `framework/skills/`

## Результаты аудита

### Навыки, соответствующие спецификации agentskills.io

По спецификации, навык = директория с файлом `SKILL.md`, где:
- Имя директории должно совпадать с полем `name` в frontmatter
- Поле `name`: только lowercase буквы, цифры, дефисы (без underscore)
- Обязательные поля: `name`, `description`

**Найдено навыков:** 2

| Директория | name в SKILL.md | Статус до | Статус после |
|------------|-----------------|-----------|--------------|
| `agent-development_ext/` | `agent-development_ext` | ✗ underscore | ✓ `agent-development-ext/` |
| `skill-creator_ext/` | `skill-creator_ext` | ✗ underscore | ✓ `skill-creator-ext/` |

### Выполненные исправления

#### 1. Переименование директорий

**Проблема:** Underscore в именах директорий нарушает спецификацию (допустимы только дефисы).

**Исправлено:**
- `framework/skills/agent-development_ext/` → `framework/skills/agent-development-ext/`
- `framework/skills/skill-creator_ext/` → `framework/skills/skill-creator-ext/`

#### 2. Обновление поля `name` в frontmatter

**Исправлено в SKILL.md:**
- `name: agent-development_ext` → `name: agent-development-ext`
- `name: skill-creator_ext` → `name: skill-creator-ext`

#### 3. Обновление упоминаний в документации

**Исправлено в `skill-creator-ext/SKILL.md`:**
- Таблица категорий: `*_ext/` → `*-ext/`
- Примеры: `agent-development_ext` → `agent-development-ext`
- Примеры: `skill-creator_ext` → `skill-creator-ext`
- Секция "Расширения": `_ext` → `-ext`

### Другие директории в framework/skills

Следующие директории **не являются навыками** по спецификации agentskills.io (нет SKILL.md):

| Директория | Содержимое | Назначение |
|------------|------------|------------|
| `bsl-practices/` | 6 .md файлов | Коллекция практик BSL |
| `spec-writing/` | 1 .md файл | Стандарты спецификаций |
| `tool-usage/` | 7 .md файлов | Руководства по MCP-инструментам |
| `xml-generation/` | пусто | Заготовка |

Эти директории используют внутренний формат фреймворка и не требуют соответствия спецификации agentskills.io.

## Проверка соответствия спецификации

### ✓ Структура директорий
```
skill-name/
└── SKILL.md
```
Оба навыка соответствуют.

### ✓ Формат SKILL.md
- YAML frontmatter с `name` и `description`
- Markdown body с инструкциями
- Оба навыка соответствуют.

### ✓ Правила для поля `name`
- 1-64 символа
- Только lowercase буквы, цифры, дефисы
- Не начинается/не заканчивается дефисом
- Совпадает с именем директории
- Оба навыка соответствуют.

### ✓ Frontmatter

**agent-development-ext:**
```yaml
---
name: agent-development-ext
description: >
  1C BSL Framework extension for agent-development skill.
  Use together with the base agent-development skill when creating or modifying
  framework agents (analyst, architect, developer, reviewer, tester, explorer, formatter).
  Covers: universal agent format (Cursor + Claude Code), model tier mapping,
  framework-specific frontmatter fields, 1C BSL domain context.
---
```

**skill-creator-ext:**
```yaml
---
name: skill-creator-ext
description: >
  1C BSL Framework extension for skill-creator skill.
  Use together with the base skill-creator skill when creating or modifying
  framework skills (bsl-practices, tool-usage, spec-writing, agent-development-ext, etc.).
  Covers: framework skill categories, tool-usage skills replacing tool-registry,
  BSL content patterns, install.py integration, project-specific skills.
---
```

Оба frontmatter корректны и соответствуют спецификации.

## Конвенция расширений (-ext)

Фреймворк использует суффикс `-ext` для расширений базовых навыков:
- `agent-development-ext` расширяет внешний навык `agent-development`
- `skill-creator-ext` расширяет внешний навык `skill-creator`

Это соответствует спецификации (дефисы допустимы в именах).

## Заключение

✓ Все навыки в `framework/skills/` приведены в соответствие со спецификацией https://agentskills.io/specification:

- ✓ Имена директорий используют только lowercase, цифры и дефисы
- ✓ Имена директорий совпадают с полем `name` в SKILL.md
- ✓ Структура файлов соответствует спецификации
- ✓ Frontmatter содержит обязательные поля `name` и `description`
- ✓ Обновлены все упоминания старых имён в документации

## Изменения в git

```
R  framework/skills/agent-development_ext/SKILL.md -> framework/skills/agent-development-ext/SKILL.md
R  framework/skills/skill-creator_ext/SKILL.md -> framework/skills/skill-creator-ext/SKILL.md
```

Изменения: переименование директорий + обновление содержимого файлов.

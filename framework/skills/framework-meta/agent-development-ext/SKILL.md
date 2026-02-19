---
name: agent-development-ext
description: >
  1C BSL Framework extension for agent-development skill.
  Use together with the base agent-development skill when creating or modifying
  framework agents (analyst, architect, developer, reviewer, tester, explorer, formatter).
  Covers: universal agent format (Cursor + Claude Code), model tier mapping,
  framework-specific frontmatter fields, 1C BSL domain context.
---

# Agent Development — 1C BSL Framework Extension

> **Базовый навык:** `agent-development` (Anthropic).
> Сначала прочитай базовый навык — он содержит общие принципы создания агентов.
> Этот файл добавляет **только** 1С-специфику и адаптацию под наш фреймворк.

---

## 1. Универсальный формат агента фреймворка

Один `.md` файл работает и в Cursor, и в Claude Code без трансформации.

### Frontmatter

```yaml
---
name: agent-name          # lowercase, hyphens, 3-50 chars
description: >
  One-liner + trigger conditions.
  Use proactively when...

  <<example>>
  Context: ...
  user: "..."
  assistant: "..."
  <<commentary>>...<</commentary>>
  <</example>>

model: sonnet              # haiku | sonnet | opus (алиас, install.py подставит конкретную модель для Cursor)
readonly: true             # true для read-only агентов (analyst, explorer, reviewer)
skills:                    # Claude Code подгрузит автоматически; Cursor проигнорирует
  - spec-standard
  - search-before-write
---
```

### Body (System Prompt)

Пишется от второго лица (`You are...`). Структура:

```markdown
You are [роль] specializing in [домен] for 1C:Enterprise (BSL).

**Навыки и правила (для Cursor):**
- `skill-name` — краткое назначение
- `rule-name` — краткое назначение

**Your Core Responsibilities:**
1. [Ответственность 1]
2. [Ответственность 2]

**Input:**
- [Что агент получает на вход]

**Output:**
- [Что агент производит]

**Protocol:**
1. [Шаг 1]
2. [Шаг 2]

**Quality Standards:**
- [Критерий 1]
- [Критерий 2]

**Boundaries:**
- [Что агент НЕ делает]
```

### Зачем секция "Навыки и правила" в body?

Claude Code подгрузит skills из frontmatter автоматически. Cursor — нет, он просто проигнорирует поле `skills`. Поэтому в body дублируем **только имена и одну строку назначения** — чтобы Cursor-агент знал, какие навыки ему релевантны и мог их найти.

---

## 2. Роли и модели фреймворка

### Таблица маппинга ролей → модели

| Роль | model | readonly | Обоснование |
|------|-------|----------|-------------|
| explorer | haiku | true | Детерминированная работа, инструменты дают точные результаты |
| formatter | haiku | false | Простые правки, шаблоны, не требуют рассуждений |
| analyst | sonnet | true | Анализ требований, создание спецификаций |
| tester | sonnet | false | Написание и запуск тестов |
| architect | sonnet | true | Технические решения, trade-offs |
| developer | sonnet | false | Реализация кода, TDD |
| reviewer | opus | true | Критическая роль — оценка артефактов, tier ≥ автор |

### Правило ревьюера

`model` ревьюера MUST быть ≥ модели автора артефакта. Если автор `sonnet` — ревьюер `sonnet` или `opus`.

### install.py: интерактивный выбор моделей

При запуске `install.py` пользователь выбирает конкретную модель для каждого агента (или принимает дефолты).

**Дефолты** хранятся в `tools/model-defaults.json` — по IDE:

```json
{
  "cursor": {
    "haiku":  "claude-4.5-haiku",
    "sonnet": "claude-4.5-sonnet-thinking",
    "opus":   "claude-4.6-opus-high-thinking"
  }
}
```

Пользователь может:
- Отредактировать `model-defaults.json` под свой набор моделей
- Выбрать модель per-agent при установке (режим `[a]`)
- Использовать не-Anthropic модели (gpt, gemini, grok — всё что доступно в IDE)

---

## 3. Особенности формата для Cursor vs Claude Code

| Поле | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ идентификатор агента | ✓ идентификатор правила |
| `description` | ✓ trigger-описание с examples | ✓ используется как `description` правила |
| `model` | ✓ нативно: haiku/sonnet/opus | ✓ install.py записывает конкретную модель в agent-файл |
| `readonly` | — (используют tools/disallowedTools) | ✓ нативно поддерживается |
| `skills` | ✓ preload навыков в контекст | ✗ игнорируется (навыки в body) |
| `color` | ✓ визуальный идентификатор | ✗ не поддерживается |
| `tools` | ✓ ограничение инструментов | ✗ не поддерживается |

**Стратегия совместимости:**
- Поля, которые IDE не понимает, просто игнорируются — это не ошибка
- `readonly: true` — Cursor понимает нативно; Claude Code можно заменить на `tools` в adapter-слое
- В body дублируем имена skills — дешёвая страховка для Cursor

---

## 4. Домен 1C BSL: контекст для system prompt

При написании system prompt для 1С-агентов учитывай:

### Ключевые ограничения платформы
- Агент **НЕ создаёт объекты метаданных** (справочники, документы, регистры) — только код в модулях `.bsl`
- Метаданные управляются конфигуратором или EDT, не кодом
- BSL — серверный и клиентский код с директивами компиляции (`&НаСервере`, `&НаКлиенте`)

### Инструменты через MCP
- Агент видит tools через MCP (`tools/list`) — это актуальный источник
- Секция "Используемые capability" в agent-файле **не нужна** — агент обнаруживает tools динамически
- Навыки `tool-usage/*` содержат подсказки когда и как использовать конкретные MCP-инструменты

### Стандарты и паттерны
- MADR 4.0 — формат спецификаций
- RFC 2119 — уровни обязательности (MUST/SHOULD/MAY)
- YaxUnit — тестовый фреймворк
- БСП (BSL Subsystem Library) — стандартные паттерны подсистем

---

## 5. Чек-лист создания агента фреймворка

1. [ ] `name` — lowercase, hyphens, 3-50 chars
2. [ ] `description` — trigger-условия + 2-3 `<<example>>` блока
3. [ ] `model` — алиас из таблицы (haiku/sonnet/opus)
4. [ ] `readonly` — true для read-only ролей
5. [ ] `skills` — список навыков для preload
6. [ ] Body — system prompt от 2-го лица (You are...)
7. [ ] В body — секция "Навыки и правила" с именами и назначением
8. [ ] В body — Core Responsibilities, Protocol, Quality Standards, Boundaries
9. [ ] Нет секции "Используемые capability" — tools через MCP
10. [ ] Нет отдельных таблиц "Входные/выходные данные" — встроены в body

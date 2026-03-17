---
name: agent-development-ext
description: >
  1C BSL Framework extension for agent-development skill.
  Use together with the base agent-development skill when creating or modifying
  framework agents (analyst, architect, developer, reviewer, tester, explorer).
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

model: sonnet              # haiku | sonnet | opus (алиас, CLI подставит конкретную модель для Cursor)
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

Секция "Навыки и правила" в body нужна для Cursor — он игнорирует `skills` из frontmatter. Дублируем только имена и одну строку назначения.

---

## 2. Роли и модели фреймворка

### Таблица маппинга ролей → модели

| Роль | model | readonly | Обоснование |
|------|-------|----------|-------------|
| explorer | haiku | true | Детерминированная работа, инструменты дают точные результаты |
| analyst | sonnet | true | Анализ требований, создание спецификаций |
| tester | sonnet | false | Написание и запуск тестов |
| architect | sonnet | true | Технические решения, trade-offs |
| developer | sonnet | false | Реализация кода, TDD |
| reviewer | opus | true | Критическая роль — оценка артефактов, tier ≥ автор |

### Правило ревьюера

`model` ревьюера MUST быть ≥ модели автора артефакта. Если автор `sonnet` — ревьюер `sonnet` или `opus`.

### CLI: маппинг моделей

Дефолты в `tools/model-defaults.json`. При `python tools/install.py` пользователь может принять дефолты, выбрать per-agent (режим `[a]`), или использовать не-Anthropic модели.

---

## 3. Совместимость Cursor / Claude Code

| Поле | Claude Code | Cursor |
|------|-------------|--------|
| `name` | ✓ | ✓ |
| `description` | ✓ trigger + examples | ✓ description правила |
| `model` | ✓ алиас | ✓ CLI подставит конкретную модель |
| `readonly` | — (tools/disallowedTools) | ✓ нативно |
| `skills` | ✓ preload | ✗ игнорируется → дублируем имена в body |
| `color` / `tools` | ✓ | ✗ |

Неизвестные поля игнорируются — это не ошибка.

---

## 4. Домен 1C BSL: контекст для system prompt

При написании system prompt для 1С-агентов учитывай:

### Ключевые ограничения
- Агент **НЕ создаёт объекты метаданных** — только код в модулях `.bsl`
- BSL — серверный и клиентский код с директивами компиляции (`&НаСервере`, `&НаКлиенте`)
- Tools обнаруживаются через MCP (`tools/list`); секция "capability" в agent-файле не нужна
- Навыки `tool-usage/*` описывают когда и как использовать MCP-инструменты
- Стандарты: MADR 4.0, RFC 2119, YaxUnit, БСП

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

---
depends_on: []
---

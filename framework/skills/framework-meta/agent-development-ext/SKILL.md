---
name: agent-development-ext
description: MUST load together with `agent-development` in GBIG context. Adds универсальный формат агентов фреймворка (analyst, architect, developer, reviewer, tester, explorer), маппинг тиров моделей и 1С BSL-специфику.
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

model: opus                # статический IDE-алиас; runtime controller использует exact tuple из канонической матрицы
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

### Каноническая матрица role → model/effort

Это единый источник рекомендуемых default-маршрутов для всех 10 сабагентов. Запись
`Primary / fallback` означает operational route при технической недоступности, а не benchmark
challenger. Exact tuple проверяется по фактической schema harness перед запуском и сохраняется в
runtime trace. `tools/model-defaults.json` содержит только IDE-алиасы и не переопределяет эту матрицу.

| Роль | Primary | Operational fallback | readonly |
|------|---------|----------------------|----------|
| analyst | Claude Opus 5 / high | GPT-5.6 Sol / high | true |
| architect | Claude Opus 5 / high | GPT-5.6 Sol / high | true |
| explorer | GPT-5.6 Luna / max | — | true |
| reviewer | Claude Opus 5 / medium | динамическое повышение до floor автора | true |
| scenario-author | Claude Opus 5 / medium | GPT-5.6 Sol / medium | false |
| scenario-coder | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| developer-tests | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| developer-code | GPT-5.6 Luna / max | Claude Opus 5 / medium | false |
| tester | Claude Opus 5 / medium | GPT-5.6 Sol / medium | false |
| debugger | Claude Opus 5 / high | GPT-5.6 Sol / high | false |

Общий аварийный fallback при исчерпании лимитов — Kimi K3 с максимальным поддерживаемым режимом,
но только после role-specific qualification и при наличии разрешённого маршрута в текущей среде.
Kimi не является silent fallback и не понижает capability floor.

Оркестратор вправе менять effort по сложности, критичности и допустимости ошибки. Значения таблицы —
defaults, не потолок; override обязан иметь rationale и runtime trace. Вне Herdr применяются только
qualified модели семейства текущего harness; внутри Herdr межсемейный маршрут выполняется по
`herdr-agent-routing`.

### Правило ревьюера

`Opus 5 / medium` — default контроллера Reviewer. Фактический blocking gate reviewer MUST быть не
слабее фактического автора по capability и effort; для автора `high` Reviewer повышается минимум до
`high`. Если schema не позволяет сохранить floor, gate блокируется.

### CLI: маппинг моделей

`tools/model-defaults.json` обслуживает только статические алиасы конкретной
IDE. Он не выражает effort, cross-provider Herdr route и аварийный Kimi K3,
поэтому не является способом применить каноническую матрицу. Exact tuple
выбирает Оркестратор/контроллер в runtime; инсталлятор лишь материализует
профиль и может подставить поддерживаемый данной IDE статический alias.

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
3. [ ] `model` — поддерживаемый текущей IDE статический alias; для динамической роли канонический exact tuple задаётся runtime-маршрутом, а не этим полем
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

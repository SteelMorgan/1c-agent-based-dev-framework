# Руководство по 1c-ai-agent-cli

CLI фреймворка (clone, install). Выбирает компоненты, настраивает модели агентов и размещает всё в каталоге проекта — симлинками (по умолчанию) или копиями.

**Требования:** Python 3.7+, Git (для clone).

---

## Быстрый старт

```bash
# Клонировать репозиторий
python tools/1c-ai-agent-cli.py clone

# Интерактивный режим — TUI с навигацией стрелками
python tools/1c-ai-agent-cli.py

# CLI — выбрать IDE и компоненты явно
python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer workflow/quick-fix

# Установить всё
python tools/1c-ai-agent-cli.py --ide cursor --all

# Посмотреть дерево без установки
python tools/1c-ai-agent-cli.py --ide cursor --list

# Пробный прогон
python tools/1c-ai-agent-cli.py --ide cursor --all --dry-run
```

---

## Поддерживаемые IDE

| IDE ключ          | Название           | Правила/агенты/воркфлоу    | Навыки               |
|-------------------|--------------------|----------------------------|----------------------|
| `cursor`          | Cursor             | `.cursor/rules/`           | `.cursor/skills/`    |
| `claude-code`     | Claude Code        | `.claude/`                 | `.claude/skills/`    |
| `windsurf`        | Windsurf           | `.windsurf/rules/`         | `.windsurf/skills/`  |
| `vscode-continue` | VS Code + Continue | `.continue/rules/`         | `.continue/skills/`  |
| `generic`         | Generic            | `framework/rules/`         | `framework/skills/`  |

Выбор IDE определяет:
- куда попадут файлы (правила, навыки, агенты, воркфлоу)
- какие модели будут доступны для агентов (из `model-defaults.json`)

---

## Каталог проекта

В интерактивном режиме (без `--include` / `--all`) после выбора IDE открывается **directory browser** — навигация по файловой системе стрелками:

```
  Каталог проекта
  IDE:     Cursor
  Правила: .cursor/rules/
  Навыки:  .cursor/skills/

  /home/user/projects/

   ► ..  (на уровень выше)
     my-1c-project/
     another-project/
     shared-lib/

  ↑↓ навигация  Enter войти  Space выбрать текущий каталог
  Backspace назад  t ввести путь  h скрытые  q отмена
```

**Управление:**

| Клавиша        | Действие                              |
|----------------|---------------------------------------|
| `↑` / `↓`     | Навигация по каталогам                |
| `Enter`        | Войти в выбранный каталог             |
| `Space`        | **Выбрать текущий каталог** как проект|
| `Backspace`/`←`| На уровень выше                      |
| `h`            | Показать/скрыть скрытые каталоги (`.git`, `.cursor` и т.д.) |
| `t`            | Переключиться на ручной ввод пути     |
| `q` / `Esc`    | Отмена                                |

**Текстовый fallback** (при нажатии `t` или если TUI недоступен):

```
  Каталог: /home/user/projects
  Enter — принять, или введите путь к проекту:
  >
```

Если введённый каталог не существует — предложит создать.

**CLI:**

```bash
# Указать каталог явно
python tools/1c-ai-agent-cli.py --ide cursor --all --project-dir /path/to/my-project
```

Если `--project-dir` не указан и используется `--include` или `--all`, каталогом проекта будет текущая директория.

---

## Компоненты фреймворка

Каждый компонент — это `.md` файл с YAML frontmatter, расположенный в `framework/`.

| Тип          | Каталог              | Назначение                                | Куда ставится       |
|--------------|----------------------|-------------------------------------------|---------------------|
| **agent**    | `framework/subagents/`  | Роли: analyst, architect, developer и др. | `rules_dir`         |
| **rule**     | `framework/rules/`   | Политики: TDD, SDD, кросс-ревью          | `rules_dir`         |
| **skill**    | `framework/skills/`  | Навыки: BSL-практики, tool-usage, spec    | `skills_dir`        |
| **workflow** | `framework/workflows/`| Процессы: full-cycle, quick-fix           | `rules_dir`         |

### Зависимости

Компоненты могут зависеть друг от друга. Например, `agent/developer` зависит от навыков `coding-standards`, `error-handling` и др. При выборе агента все его зависимости подтягиваются автоматически.

---

## Режимы выбора компонентов

### 1. Интерактивный TUI (по умолчанию)

Если терминал поддерживает raw input, запускается псевдо-графический интерфейс:

```
  Выберите компоненты:
  Выбрано: 5

  🤖 Агенты
   ► [✓]  agent/analyst                      analyst
     [✓]  agent/developer                    developer
     [ ]  agent/explorer                     explorer

  📋 Правила
     [✓]  rule/tdd-policy                    Политика TDD
     ...

  ↑↓ навигация  Space выбор  a всё  n ничего  Enter готово  q отмена
```

**Управление:**

| Клавиша       | Действие                       |
|---------------|--------------------------------|
| `↑` / `↓`    | Навигация по списку            |
| `Space`       | Выбрать / снять компонент      |
| `a`           | Выбрать всё                    |
| `n`           | Снять всё                      |
| `Enter`       | Подтвердить выбор              |
| `q` / `Esc`   | Отмена и выход                 |

### 2. Текстовый fallback

Если TUI недоступен (pipe, перенаправление, старый терминал), скрипт автоматически переключается на текстовый ввод с номерами:

```
  Команды:
    1,3,5-8    — выбрать/снять компоненты
    all        — выбрать всё
    none       — снять всё
    done       — завершить выбор
    quit       — выйти
```

### 3. CLI-флаги (без интерактива)

```bash
# Конкретные компоненты — зависимости подтянутся
python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer agent/reviewer

# Всё сразу
python tools/1c-ai-agent-cli.py --ide cursor --all
```

---

## Настройка моделей агентов

Каждый агент имеет поле `model` в frontmatter с алиасом уровня (`haiku`, `sonnet`, `opus`). При установке алиасы маппятся на конкретные модели для выбранной IDE.

### Файл конфигурации: `tools/model-defaults.json`

```json
{
  "cursor": {
    "aliases": {
      "haiku":  "claude-4.5-haiku",
      "sonnet": "claude-4.5-sonnet-thinking",
      "opus":   "claude-4.6-opus-high-thinking"
    },
    "available": [
      "claude-4.6-opus-high-thinking",
      "claude-4.5-sonnet-thinking",
      "claude-4.5-haiku",
      "gpt-5.3-codex-xhigh",
      "gpt-5.2-xhigh",
      "gemini-3-pro",
      "grok-code-fast-1"
    ]
  }
}
```

**Структура для каждой IDE:**

| Поле        | Назначение                                                  |
|-------------|-------------------------------------------------------------|
| `aliases`   | Маппинг `haiku`/`sonnet`/`opus` → конкретная модель         |
| `available` | Список моделей, доступных в TUI для переключения стрелками  |

Отредактируйте этот файл под свой набор доступных моделей до запуска CLI.

### Тиры моделей

| Алиас    | Назначение                                    | Типичные агенты                   |
|----------|-----------------------------------------------|-----------------------------------|
| `haiku`  | Быстрые, простые задачи                       | explorer, formatter               |
| `sonnet` | Основная рабочая лошадка                      | analyst, architect, developer, tester |
| `opus`   | Сложные задачи, ревью, финальная проверка     | reviewer                          |

### Интерактивный выбор модели (TUI)

При наличии TUI, после выбора компонентов показывается экран настройки моделей:

```
  Настройка моделей для агентов

   ► analyst     (sonnet)     ◄ claude-4.5-sonnet-thinking     ►
     architect   (sonnet)       claude-4.5-sonnet-thinking
     developer   (sonnet)       claude-4.5-sonnet-thinking
     explorer    (haiku)        claude-4.5-haiku
     reviewer    (opus)         claude-4.6-opus-high-thinking

  ↑↓ агент  ←→ модель  Enter подтвердить  q отмена

  Доступные: claude-4.6-opus-high-thinking  claude-4.5-sonnet-thinking  ...
```

**Управление:**

| Клавиша       | Действие                             |
|---------------|--------------------------------------|
| `↑` / `↓`    | Переключение между агентами          |
| `←` / `→`    | Переключение модели из списка available |
| `Enter`       | Подтвердить                          |
| `q` / `Esc`   | Отмена                               |

### Текстовый fallback для моделей

Без TUI доступны три режима:

| Команда   | Описание                                    |
|-----------|---------------------------------------------|
| `[Enter]` | Принять маппинг по умолчанию                |
| `c`       | Настроить по алиасам (все haiku → X)        |
| `a`       | Настроить каждого агента индивидуально      |

### Куда записываются модели

Выбранные модели записываются **в оригинальные файлы** `framework/subagents/*.md` (поле `model:` в frontmatter). Это позволяет устанавливать агентов симлинками, без копирования.

---

## Метод установки

### Симлинки (по умолчанию)

- Создаются относительные симлинки из каталога проекта на файлы в `framework/`
- Обновление фреймворка автоматически видно в проекте
- На Windows требуется Developer Mode или права администратора

### Копирование (fallback)

- Используется при `--copy` или если симлинки недоступны
- Файлы копируются — при обновлении фреймворка нужно перезапустить CLI

### Пересоздание симлинков

```bash
python tools/1c-ai-agent-cli.py --relink
```

Проверяет все `.md`-симлинки в проекте и сообщает о сломанных (например, если `framework/` переместили).

---

## Справочник CLI

```
python tools/1c-ai-agent-cli.py [OPTIONS]
```

| Флаг                     | Описание                                         |
|--------------------------|--------------------------------------------------|
| `--ide IDE`              | Целевая IDE: cursor, claude-code, windsurf, vscode-continue, generic |
| `--project-dir PATH`     | Каталог проекта (по умолчанию: текущий)          |
| `--include ID [ID ...]`  | ID компонентов (зависимости подтянутся)          |
| `--all`                  | Установить все компоненты                        |
| `--list`                 | Показать дерево без установки                    |
| `--copy`                 | Принудительно копировать (не симлинки)            |
| `--dry-run`              | Показать план без реальных изменений             |
| `--relink`               | Проверить/пересоздать симлинки                   |

### Примеры

```bash
# Минимальный набор для быстрого старта
python tools/1c-ai-agent-cli.py --ide cursor --include agent/developer workflow/quick-fix

# Полный набор для Cursor с пробным прогоном
python tools/1c-ai-agent-cli.py --ide cursor --all --dry-run

# Claude Code — полный набор
python tools/1c-ai-agent-cli.py --ide claude-code --all

# Windsurf — конкретные компоненты
python tools/1c-ai-agent-cli.py --ide windsurf --include agent/developer agent/reviewer rule/tdd-policy

# Показать что доступно
python tools/1c-ai-agent-cli.py --ide cursor --list
```

---

## Типичный сценарий

```
1. python tools/1c-ai-agent-cli.py
2. ← Выбор IDE (стрелками)
3. ← Каталог проекта (Enter — принять текущий, или ввести путь)
4. ← Выбор компонентов (Space — toggle, Enter — готово)
5. ← Настройка моделей (←→ — переключить модель)
6. ← Подтверждение установки (Y/n)
7. → Симлинки созданы, модели записаны
```

**Время: ~2 минуты.**

---

## Кастомизация

### Добавить модель в список доступных

Отредактируйте `tools/model-defaults.json`, добавив модель в `available` для нужной IDE:

```json
{
  "cursor": {
    "available": [
      "claude-4.6-opus-high-thinking",
      "my-custom-model-v2",
      "..."
    ]
  }
}
```

### Изменить дефолтный маппинг алиасов

В `aliases` поменяйте значения:

```json
{
  "cursor": {
    "aliases": {
      "haiku":  "grok-code-fast-1",
      "sonnet": "gpt-5.2-xhigh",
      "opus":   "claude-4.6-opus-high-thinking"
    }
  }
}
```

### Добавить новую IDE

Добавьте секцию в `model-defaults.json` и конфигурацию в `IDE_CONFIGS` внутри `tools/1c-ai-agent-cli.py`.

---
name: 1c-ai-agent-cli
description: CLI 1C BSL Agent Framework — tools/install.py (clone, install). Используй при клонировании репозитория, установке компонентов в проект, настройке IDE (Cursor, Claude Code, Windsurf, VS Code+Continue).
---

# 1c-ai-agent CLI — install.py

Правила работы с CLI 1C BSL Agent Framework. Команды: **clone** (получение репозитория) и **install** (установка компонентов в проект).

---

## Команда clone — получение фреймворка

Репозиторий: **https://github.com/SteelMorgan/1c-agent-based-dev-framework**

**Требование:** Git должен быть установлен.

### Базовое клонирование

```bash
python tools/install.py clone
```

Клонирует в `./1c-agent-based-dev-framework` (текущая директория).

### С указанием целевой директории

```bash
python tools/install.py clone -t ./my-framework
python tools/install.py clone --target /path/to/dir
```

### Shallow clone (быстрее, только последний коммит)

```bash
python tools/install.py clone --depth 1
```

### Конкретная ветка

```bash
python tools/install.py clone -b agent-framework-bootstrap-20260211
```

### Клонирование + установка

```bash
python tools/install.py clone -t ./fw --install
```

После клонирования автоматически запускается установщик в интерактивном режиме.

### Справка по clone

```bash
python tools/install.py clone --help
```

---

## Альтернатива: ручное клонирование

Если CLI недоступен (например, первый запуск):

```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/install.py
```

ZIP (без git): скачать архив с GitHub и распаковать.

---

## Команда install — установка компонентов

Интерактивный установщик компонентов фреймворка в проект, с учётом целевой IDE.

**Запуск:** из корня репозитория фреймворка (или после `clone`):

```bash
python tools/install.py [опции]
python tools/install.py install [опции]   # явно
```

### Базовый вызов

| Команда | Описание |
|---------|----------|
| `python tools/install.py` | Интерактивный режим — выбор IDE, проекта, компонентов |
| `python tools/install.py --ide cursor --list` | Показать дерево компонентов без установки |
| `python tools/install.py --ide cursor --all` | Установить все компоненты |
| `python tools/install.py --ide cursor --include agent/developer workflow/full-cycle` | Установить указанные компоненты (зависимости подтянутся автоматически) |
| `python tools/install.py --ide cursor --include agent/developer --dry-run` | Показать, что будет сделано, без изменений |
| `python tools/install.py --relink` | Проверить и пересоздать сломанные симлинки |
| `python tools/install.py --ide cursor --all --sync` | Синхронизация: удалить симлинки снятых компонентов |

### Поддерживаемые IDE

`--ide` принимает одно или несколько значений через пробел (установка сразу в несколько IDE):

```bash
python tools/install.py --ide claude-code codex --all
```

| `--ide` | Описание |
|---------|----------|
| `cursor` | Cursor — правила в `.cursor/rules/` (авто), навыки в `.cursor/skills/` |
| `claude-code` | Claude Code — `CLAUDE.md` + `.claude/rules/` ⚠ требует `@import` в CLAUDE.md |
| `windsurf` | Windsurf — `.windsurf/rules/` (авто), навыки в `.windsurf/skills/` |
| `vscode-continue` | VS Code + Continue — `.continue/rules/` |
| `roocode` | RooCode — `.roo/rules/` (авто, все файлы), навыки в `.roo/skills/` |
| `kilocode` | Kilo Code — `AGENTS.md` + `.kilocode/rules/` ⚠ требует ссылок в AGENTS.md |
| `kiro` | Kiro — `.kiro/steering/` (авто), навыки в `.kiro/skills/` |
| `codex` | Codex CLI (OpenAI) — `AGENTS.md` + `.codex/rules/` ⚠ требует ссылок в AGENTS.md |
| `antigravity` | Antigravity — `.agents/rules/`, навыки в `.agents/skills/` (skill.md) |
| `generic` | Без привязки к IDE — копирование в `framework/` |

> ⚠ **IDE с ручным импортом правил** (claude-code, codex, kilocode): после установки инсталлер
> выведет напоминание с конкретными строками для добавления в `CLAUDE.md` / `AGENTS.md`.

### Флаги

| Флаг | Описание |
|------|----------|
| `--ide <IDE> [IDE ...]` | Целевая IDE (можно несколько через пробел) |
| `--project-dir <path>` | Каталог проекта (по умолчанию: текущий) |
| `--include ID [ID ...]` | ID компонентов для установки |
| `--all` | Установить все компоненты |
| `--list` | Показать дерево компонентов без установки |
| `--copy` | Принудительно копировать файлы (не симлинки) |
| `--dry-run` | Показать, что будет сделано, без реальных изменений |
| `--relink` | Проверить и пересоздать сломанные симлинки |
| `--sync` | Синхронизировать установку: удалить симлинки снятых компонентов |

### Требования

- Python 3.7+
- Без внешних зависимостей (стандартная библиотека)

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| Пользователь хочет получить фреймворк | `python tools/install.py clone` или `git clone <URL>` |
| Пользователь хочет установить фреймворк в проект | `python tools/install.py clone -t ./fw --install` или clone + install |
| Нужно проверить доступные компоненты | `python tools/install.py --ide cursor --list` |
| Пользователь спрашивает, как установить фреймворк | Дать `python tools/install.py clone` и `python tools/install.py` |
| Пользователь просит установить конкретные агенты/правила | `python tools/install.py --ide cursor --include <id1> <id2>` |
| Симлинки сломаны после перемещения framework/ | `python tools/install.py --relink` |
| Нужно проверить, что будет установлено | `python tools/install.py --ide cursor --include ... --dry-run` |
| Windows без Developer Mode (симлинки недоступны) | `python tools/install.py --ide cursor --copy --include ...` |

---

## Сценарии

### Сценарий 1: Первый запуск фреймворка

**Вариант A (есть Git, с нуля):**
```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/install.py   # интерактивно, или python tools/install.py --ide cursor --all
```

**Вариант B (уже есть репозиторий, клонировать в другой каталог):**
```bash
cd 1c-agent-based-dev-framework
python tools/install.py clone -t ../другой-проект/fw --install
```

**Вариант C (установка в текущий проект):**
```bash
python tools/install.py --ide cursor --all
```

### Сценарий 2: Установка в существующий проект

1. Клонировать фреймворк в отдельную папку (или использовать уже существующую).
2. Указать целевой проект: `python tools/install.py --ide cursor --project-dir /path/to/project --include agent/developer workflow/full-cycle`
3. При необходимости: `--dry-run` для проверки.

### Сценарий 3: Восстановление симлинков

После перемещения фреймворка в другое место:

```bash
cd /path/to/project
python /path/to/framework/tools/install.py --relink
```

---

## См. также

- [agent-development](../agent-development/) — создание агентов
- [skill-creator](../skill-creator/) — создание навыков

---
depends_on: []
metadata:
  category: framework-meta
  version: "1.0"
---

---
name: 1c-ai-agent-cli
description: Use for клонирования фреймворка и установки компонентов в проект через tools/install.py. Helps настроить IDE (Cursor, Claude Code, Windsurf, VS Code+Continue) с нужными симлинками и профилями.
---

# 1c-ai-agent CLI — install.py

## Команда clone — получение фреймворка

Репозиторий: `SteelMorgan/1c-agent-based-dev-framework`. Git должен быть установлен.

```bash
python tools/install.py clone                              # в ./1c-agent-based-dev-framework
python tools/install.py clone -t ./my-framework            # целевая директория
python tools/install.py clone --depth 1                    # shallow clone
python tools/install.py clone -b <branch>                  # конкретная ветка
python tools/install.py clone -t ./fw --install            # clone + интерактивная установка
```

Альтернатива (первый запуск без CLI):
```bash
git clone https://github.com/SteelMorgan/1c-agent-based-dev-framework.git
cd 1c-agent-based-dev-framework
python tools/install.py
```

---

## Команда install — установка компонентов

Запуск из корня репозитория фреймворка:

```bash
python tools/install.py [опции]
```

### Примеры вызова

| Команда | Описание |
|---------|----------|
| `python tools/install.py` | Интерактивный режим |
| `python tools/install.py --ide cursor --list` | Показать дерево компонентов |
| `python tools/install.py --ide cursor --all` | Установить все компоненты |
| `python tools/install.py --ide cursor --include agent/developer workflow/full-cycle` | Конкретные компоненты (зависимости подтянутся) |
| `python tools/install.py --ide cursor --include agent/developer --dry-run` | Показать план без изменений |
| `python tools/install.py --relink` | Пересоздать сломанные симлинки |
| `python tools/install.py --ide cursor --all --sync` | Удалить симлинки снятых компонентов |

### Поддерживаемые IDE

`--ide` принимает несколько значений: `python tools/install.py --ide claude-code codex --all`

| `--ide` | Описание |
|---------|----------|
| `cursor` | `.cursor/rules/` (авто), навыки в `.cursor/skills/` |
| `claude-code` | `CLAUDE.md` + `.claude/rules/` — требует `@import` в CLAUDE.md |
| `windsurf` | `.windsurf/rules/` (авто), навыки в `.windsurf/skills/` |
| `vscode-continue` | `.continue/rules/` |
| `roocode` | `.roo/rules/` (авто), навыки в `.roo/skills/` |
| `kilocode` | `AGENTS.md` + `.kilocode/rules/` — требует ссылок в AGENTS.md |
| `kiro` | `.kiro/steering/` (авто), навыки в `.kiro/skills/` |
| `codex` | `AGENTS.md` + `.codex/rules/` — требует ссылок в AGENTS.md |
| `antigravity` | `.agents/rules/`, навыки в `.agents/skills/` |
| `generic` | Копирование в `framework/` |

IDE с ручным импортом (claude-code, codex, kilocode): инсталлер выведет строки для добавления в `CLAUDE.md` / `AGENTS.md`.

### Флаги

| Флаг | Описание |
|------|----------|
| `--ide <IDE> [IDE ...]` | Целевая IDE (несколько через пробел) |
| `--project-dir <path>` | Каталог проекта (по умолчанию: текущий) |
| `--include ID [ID ...]` | ID компонентов для установки |
| `--all` | Установить все компоненты |
| `--list` | Показать дерево без установки |
| `--copy` | Копировать файлы вместо симлинков (Windows без Developer Mode) |
| `--dry-run` | Показать план без изменений |
| `--relink` | Пересоздать сломанные симлинки |
| `--sync` | Удалить симлинки снятых компонентов |

Требования: Python 3.7+, без внешних зависимостей.

---

## Когда применять

| Триггер | Действие |
|---------|----------|
| Получить фреймворк | `python tools/install.py clone` |
| Установить в проект | `clone -t ./fw --install` или `--ide cursor --all` |
| Проверить компоненты | `--ide cursor --list` или `--dry-run` |
| Установить конкретные компоненты | `--ide cursor --include <id1> <id2>` |
| Сломанные симлинки | `--relink` |
| Windows без симлинков | `--copy` |

---
depends_on: []
metadata:
  category: framework-meta
  version: "1.0"
---

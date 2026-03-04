---
name: skills-i18n-sync
description: >
  Managing synchronization of Russian-language skills (`framework/`) with the
  English mirror (`framework_eng/`). Use it when you need to check sync
  status, manually trigger a translation, or understand why a commit is blocked.
---

# skills-i18n-sync — синхронизация RU→EN навыков

## Architecture

All framework skills exist in two copies:

| Каталог | Язык | Кто редактирует |
|---------|------|-----------------|
| `framework/` | Русский | Люди и агенты (источник правды) |
| `framework_eng/` | Английский | **Только автоматически** через этот навык |

Symlinks in `.claude/skills/` and `.cursor/skills/` point to `framework_eng/`.
Agents work with the English versions — they use fewer tokens.

**Never edit `framework_eng/` directly** — changes will be overwritten during the
next synchronization.

---

## When to apply

| Триггер | Действие |
|---------|----------|
| Ты изменил любой файл в `framework/` (кроме `README.md`) | Немедленно синхронизировать изменённый файл через `/sync-skills <path>` |
| Видишь статус `dirty` или `pending` в реестре | Запустить `/sync-skills` перед использованием навыка |
| Коммит заблокирован хуком с ошибкой перевода | Запустить `python3 tools/sync-skill.py --all`, затем повторить коммит |
| Добавлен новый файл в `framework/` | Запустить `/sync-skills <path>` для создания EN-версии |
| Нужно проверить состояние всех навыков | Запустить `/sync-skills check` |

---

## Commands

### `/sync-skills check`
Shows a table of statuses for all files:
- `✓ synced` — RU and EN are synchronized
- `○ pending` — the EN version has not been created yet
- `✗ dirty` — RU has changed, EN is stale
- `! error` — the last translation failed

```bash
python3 tools/sync-skill.py --check
```

### `/sync-skills`
Synchronize all `pending` and `dirty` files:

```bash
python3 tools/sync-skill.py --all
```

### `/sync-skills <path>`
Synchronize a specific file:

```bash
python3 tools/sync-skill.py framework/skills/bsl-practices/coding-standards/SKILL.md
```

### `/sync-skills init-all`
Initial synchronization — translate everything that does not yet have an EN
mirror. Run once during setup or after adding a large block of files:

```bash
python3 tools/sync-skill.py --init-all
```

---

## Rules for agents

1. **After any change to a file in `framework/`** — run synchronization right away.
   Do not wait until commit: the hook will block it if the EN version is missing.

2. **Exception:** `README.md` files are not translated or synchronized.

3. **Before using a skill** — make sure its status is `synced`. If it is
   `dirty`, the files in `.claude/skills/` are outdated.

4. **`framework_eng/` is read-only**, never write to it directly.

5. **When RU and EN content conflict** — RU always wins (source of truth).

---

## How the pre-commit hook works

```
git commit
    ↓
.git/hooks/pre-commit runs
    ↓
Finds staged files in framework/ (except README.md)
    ↓
Calls: python3 tools/sync-skill.py <list of files>
    ↓
sync-skill.py invokes the Claude Haiku CLI to translate each file
    ↓
Writes the result to framework_eng/ (mirror path)
Updates .skills-sync-state.json
    ↓
The hook adds the framework_eng/ files and state to the commit (git add)
    ↓
The commit continues (RU + EN are always in the same commit)
    ↓
On error: the commit is BLOCKED with instructions on how to fix it
```

---

## Synchronization registry

The `.skills-sync-state.json` file at the repo root is the source of truth about
synchronization status:

```json
{
  "version": 1,
  "rules": {
    "watch_dir": "framework/",
    "mirror_dir": "framework_eng/",
    "exclude": ["README.md"]
  },
  "files": {
    "framework/skills/bsl-practices/coding-standards/SKILL.md": {
      "ru_hash": "sha256:abc...",
      "en_hash": "sha256:def...",
      "synced_at": "2026-03-02T10:00:00Z",
      "status": "synced"
    }
  }
}
```

---

## Installing the hook on a new machine

The hook lives in `.git/hooks/` — it is **not added to git** automatically.
After cloning the repo install the hook manually:

```bash
cp tools/hooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

> Source of the hook for distribution: `tools/hooks/pre-commit`
> (symlink or copy of `.git/hooks/pre-commit`)

---
depends_on: []
---

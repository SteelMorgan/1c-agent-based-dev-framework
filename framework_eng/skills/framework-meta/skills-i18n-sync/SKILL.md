---
name: skills-i18n-sync
description: >
  Manage synchronization of Russian-language skills (framework/) with
  the English mirror (framework_eng/). Use when you need to
  check the synchronization status, manually trigger a translation,
  or understand why a commit is blocked.
---

# skills-i18n-sync — RU→EN skill synchronization

`framework/` (RU, source of truth) → `framework_eng/` (EN, automatic mirror). IDE symlinks point to `framework_eng/`. **Never edit `framework_eng/` directly.**

---

## When to use

| Trigger | Action |
|---------|--------|
| You modified any file in `framework/` (except `README.md`) | Immediately synchronize the changed file via `/sync-skills <path>` |
| You see a `dirty` or `pending` status in the registry | Run `/sync-skills` before using the skill |
| A commit is blocked by a translation error in the hook | Run `python3 tools/sync-skill.py --all`, then retry the commit |
| A new file was added to `framework/` | Run `/sync-skills <path>` to create the EN version |
| You need to check the state of every skill | Run `/sync-skills check` |

---

## Commands

### `/sync-skills check`
Shows a table of statuses for all files:
- `✓ synced` — RU and EN are synchronized
- `○ pending` — the EN version has not been created yet
- `✗ dirty` — RU was changed, EN is outdated
- `! error` — the last translation failed with an error

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
Initial synchronization — translate everything that still lacks an EN mirror. Run once during setup or after adding a large batch of files:

```bash
python3 tools/sync-skill.py --init-all
```

---

## Rules for agents

1. **After any change to a file in `framework/`** — run synchronization immediately. Do not wait until commit: the hook will block it if the EN version is missing.

2. **Exception:** `README.md` files are not translated or synchronized.

3. **Before using a skill** — ensure its status is `synced`. If it is `dirty`, the data in `.claude/skills/` is outdated.

4. **`framework_eng/` is read-only**; never write there directly.

5. **If RU and EN content conflict** — RU always wins (it is the source of truth).

---

## How the pre-commit hook works

```
git commit
    ↓
.git/hooks/pre-commit runs
    ↓
Finds staged files in framework/ (except README.md)
    ↓
Invokes: python3 tools/sync-skill.py <list of files>
    ↓
sync-skill.py calls the Codex CLI (gpt-5.1-codex-mini) to translate each file
    ↓
Writes the result to framework_eng/ (mirror path)
Updates .skills-sync-state.json
    ↓
Hook adds framework_eng/ files and state to the commit (git add)
    ↓
Commit continues (RU + EN always in the same commit)
    ↓
On error: BLOCKS the commit with instructions on how to fix
```

---

## Synchronization registry

The `.skills-sync-state.json` file at the repo root stores the statuses and hashes of each file.

---
depends_on: []
---

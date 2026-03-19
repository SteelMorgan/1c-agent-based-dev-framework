---
name: skill-editing-from-project
installable: true
description: >
  How to edit framework skills while working from a 1С project directory.
  Use when the user asks to change, extend, or fix a framework skill/rule while
  located outside the framework repository and inside a project where the
  framework is installed via symlinks.
---

# Editing framework skills from a project

Skills are connected via symlinks to `framework_eng/` (EN mirror). Edits are applied in `framework/` (RU source). The mapping reference is stored in `.install-session.json`.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user asks to modify a framework skill/rule from the project directory | Follow the procedure below |
| A bug needs fixing in a skill, but the working directory is the 1С project | Open `.install-session.json`, locate the RU path, make the change, then synchronize |

---

## Procedure

### 1. Open the component map

Read the `.install-session.json` file at the project root.

Key fields:

| Field | Purpose |
|-------|---------|
| `sync_script` | Absolute path to the RU→EN synchronization script |
| `framework_dir` | Absolute path to `framework/` (RU source) |
| `component_map` | Map of all installed components |

Each `component_map` entry looks like this:

```json
{
  "skill/vanessa-run": {
    "type": "skill",
    "ru_path": "/path/to/fw/framework/skills/tool-usage/vanessa/vanessa-run/SKILL.md",
    "en_path": "/path/to/fw/framework_eng/skills/tool-usage/vanessa/vanessa-run/SKILL.md"
  }
}
```

### 2. Find the required component

Use `component_map` to locate the component by its name (the key is `type/name`).
The `ru_path` value is the file you must edit.

### 3. Make edits to the RU file

Open the file at `ru_path` and apply the changes.

**Mandatory rules:**

- **The edit language must remain Russian.** All skill text must stay in Russian.
- **Do not edit `en_path`.** The file is overwritten during synchronization.
- **Do not translate manually.** The script performs translation automatically.

### 4. Synchronize the EN mirror

Run the synchronization script immediately after editing—**do not postpone it until commit time**.

```bash
# Script path comes from the sync_script field in .install-session.json
python3 <sync_script> <ru_path>
```

If multiple files were touched:

```bash
python3 <sync_script> --all
```

---

## Common mistakes

| Mistake | Consequence |
|--------|-------------|
| Editing `en_path` directly | Changes are lost during synchronization |
| Writing text in English | Violates the language policy |
| Forgetting to synchronize | The EN version becomes outdated and agents work with the stale skill |

---
depends_on: []
---

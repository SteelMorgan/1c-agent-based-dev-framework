---
name: skill-editing-from-project
installable: true
description: >
  How to edit framework skills while working from a 1С project directory.
  Use this when the user asks to change, extend, or fix a framework skill/rule
  while not being in the framework repository, but in a project where the
  framework is installed through symlinks.
---

# Editing framework skills from a project

Skills are connected through symlinks to `framework_eng/` (EN mirror). Changes are made in `framework/` (RU source). The mapping is in `.install-session.json`.

---

## When to use

| Trigger | Action |
|---------|----------|
| The user asks to change a framework skill/rule from the project directory | Follow the procedure below |
| A skill bug needs to be fixed, but the working directory is the 1С project | Open `.install-session.json`, find the RU path, make the change, sync |

---

## Procedure

### 1. Open the component map

Read the `.install-session.json` file at the project root.

Key fields:

| Field | Purpose |
|------|-----------|
| `sync_script` | Absolute path to the RU→EN sync script |
| `framework_dir` | Absolute path to `framework/` (RU source) |
| `component_map` | Map of all installed components |

Each `component_map` item:

```json
{
  "skill/vanessa-authoring": {
    "type": "skill",
    "ru_path": "/path/to/fw/framework/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md",
    "en_path": "/path/to/fw/framework_eng/skills/tool-usage/vanessa/vanessa-authoring/SKILL.md"
  }
}
```

### 2. Find the required component

Use `component_map` to find the component by name (the key is `type/name`).
The `ru_path` value is the file that needs to be edited.

### 3. Make changes in the RU file

Open the file at the path from `ru_path` and make the changes.

**Required rules:**

- **The language of changes is Russian.** All skill text is written in Russian.
- **Do not edit `en_path`** — the file will be overwritten during sync.
- **Do not translate manually** — translation is performed automatically by the script.

### 4. Sync the EN mirror

Immediately after the changes, run the sync script — **do not wait until commit**.

```bash
# The script path comes from the sync_script field in .install-session.json
python3 <sync_script> <ru_path>
```

If multiple files were changed:

```bash
python3 <sync_script> --all
```

---

## Typical mistakes

| Error | Consequence |
|--------|------------|
| Editing `en_path` directly | Changes will be lost during sync |
| Writing text in English | Violates the language policy |
| Forgetting to sync | The EN version becomes outdated, and agents work with an outdated skill |

---
depends_on: []
---

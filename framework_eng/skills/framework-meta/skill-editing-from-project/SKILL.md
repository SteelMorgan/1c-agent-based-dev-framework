---
name: skill-editing-from-project
installable: true
description: "Editing framework skills from a project via install-session"
---

# Editing framework skills from a project

Skills are connected through symlinks to `framework_eng/` (EN mirror). Changes are made in `framework/` (RU source). The mapping map is in `.install-session.json`.

---

## When to use

| Trigger | Action |
|---------|----------|
| The user asks to change a framework skill/rule from the project directory | Follow the procedure below |
| You need to fix a bug in a skill, but the working directory is the 1C project | Open `.install-session.json`, find the RU path, make the change, synchronize |

---

## Procedure

### 1. Open the component map

Read the `.install-session.json` file in the project root.

Key fields:

| Field | Purpose |
|------|-----------|
| `sync_script` | Absolute path to the RU→EN synchronization script |
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
The `ru_path` value is the file you need to edit.

### 3. Make changes in the RU file

Open the file at the path from `ru_path` and make the changes.

**Mandatory rules:**

- **The edit language is Russian.** The entire skill text is written in Russian.
- **Do not edit `en_path`** - the file will be overwritten during synchronization.
- **Do not translate manually** - the script performs the translation automatically.
- **Universality - no project references (MUST).** Framework skills and rules are reused across different projects. It is **PROHIBITED** to include in their body: specific project task numbers (`TASK-XXX`, `@task-NNN`, tickets like `OC-NNNNN`), names of project objects/metadata/symbols (`Справочник.Ххх`, `ИмяФормы`, `ИмяРеквизита`), one-off cases ("in task X it was like this"). Formulate at the **class** level (generalized principle/prohibition/check), not as an incident. A specific case/`file:line`/name belongs to the PROJECT: its place is in the project's `.claude/rules/`, `{project}/.context/learned-patterns.md` or memory, NOT in the universal body of the framework. Self-check before saving: grep your change for `TASK-|task-|OC-|прецедент` - if found, generalize it or move it to the project. (The same principle, "higher abstraction level than the incident", for learned-patterns is in `skill-learning-policy`.)

### 4. Synchronize the EN mirror

Immediately after making changes, run the synchronization script - **do not wait until commit**.

```bash
# Path to the script - from the sync_script field in .install-session.json
python3 <sync_script> <ru_path>
```

If multiple files were changed:

```bash
python3 <sync_script> --all
```

---

## Common mistakes

| Mistake | Consequence |
|--------|------------|
| Editing `en_path` directly | Changes will be lost during synchronization |
| Writing text in English | Violates the language policy |
| Forgetting to synchronize | The EN version becomes stale, agents work with an outdated skill |
| Putting a task number / project object name / one-off case into the universal body | The framework stops being generic; the case should live in the project (`.claude/rules/`, `{project}/.context/learned-patterns.md`, memory). Generalize to the class |

---
depends_on: []
---

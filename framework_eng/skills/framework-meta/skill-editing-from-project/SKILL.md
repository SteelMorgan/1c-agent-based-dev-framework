---
name: skill-editing-from-project
installable: true
description: Use when editing framework skills while in a 1C project directory (not in the framework repository). Helps locate the RU source through symlinks and `.install-session.json` without switching repositories.
---

# Editing Framework Skills from a Project

Skills are linked through symlinks to `framework_eng/` (EN mirror). Changes are made in `framework/` (RU source). The mapping is in `.install-session.json`.

---

## When to Use

| Trigger | Action |
|---------|----------|
| The user asks to change a framework skill/rule from the project directory | Follow the procedure below |
| You need to fix a bug in a skill, but the working directory is a 1C project | Open `.install-session.json`, find the RU path, make the edit, synchronize |

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

Using `component_map`, find the component by name (the key is `type/name`).
The `ru_path` value is the file you need to edit.

### 3. Make changes in the RU file

Open the file at the `ru_path` location and make the changes.

**Required rules:**

- **The language of edits is Russian.** All skill text is written in Russian.
- **Do not edit `en_path`** — the file will be overwritten during synchronization.
- **Do not translate manually** — translation is performed automatically by the script.
- **Universality - no project-specific references (MUST).** Framework skills and rules are reused by different projects. It is **FORBIDDEN** to hard-code into them: task numbers from a specific project (`TASK-XXX`, `@task-NNN`, tickets like `OC-NNNNN`), names of project objects/metadata/symbols (`Справочник.Ххх`, `ИмяФормы`, `ИмяРеквизита`), one-off cases ("in task X it was like this"). Formulate at the level of a **class** (a generalized principle/prohibition/check), not an incident. A specific incident, `file:line`, or name is PROJECT knowledge: it belongs in the project `.claude/rules/`, `{project}/.context/learned-patterns.md`, or memory, NOT in the universal framework body. Self-check before saving: grep your change for `TASK-`, `task-`, `OC-`, `incident` - found one → generalize it or move it to the project. (The same "abstraction level above the incident" principle for learned-patterns is in `skill-learning-policy`.)

### 4. Synchronize the EN mirror

Immediately after the edits, run the synchronization script — **do not postpone it until commit time**.

```bash
# Путь к скрипту — из поля sync_script в .install-session.json
python3 <sync_script> <ru_path>
```

If multiple files were changed:

```bash
python3 <sync_script> --all
```

---

## Typical Errors

| Error | Consequence |
|--------|------------|
| Editing `en_path` directly | Changes will be lost during synchronization |
| Writing text in English | Violates the language policy |
| Forgetting to synchronize | The EN version becomes stale, and agents work with an outdated skill |
| Putting a task number / project object name / one-off case into the universal body | The framework stops being generic; the incident must live in the project (`.claude/rules/`, `{project}/.context/learned-patterns.md`, memory). Generalize to a class |

---
depends_on: []
---

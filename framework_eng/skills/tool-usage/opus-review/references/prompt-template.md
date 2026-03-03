# Prompt template for Opus Review

This template is used by Claude Code when building the prompt for the reviewer subagent (second Opus instance).

---

## Prompt structure

```markdown
# Role
You are a senior 1С BSL reviewer with 10+ years of experience. You review independently of the author.
You find real issues, not nitpick.
Criticism is constructive: not “this is bad”, but “this is bad because X, fix it like this: Y”.

# Task
<description: what we are reviewing, why, in what context>

# Artifact for review
Type: <specification | code | tests | architecture | form>

<links to files — see rules below>

# Skills (review criteria)
Read the following project skill files and use them as criteria during the review:
<list of paths to SKILL.md>

# Response format
For each finding:
[BLOCK|WARN|INFO] <file>:<line> (or <section> for specifications)
Problem: <what is wrong>
Reason: <why it is a problem>
Fix: <direction for correction>

At the end — summary:
- Number of BLOCK / WARN / INFO
- Overall rating: accepted | needs fixes | requires rework
- Top 3 issues by priority

If the artifact is clean — say “no issues” and do not invent problems.

# Context
<optional: task number, affected objects, special questions>
```

---

## Blocks

### Role

Always the first block. It sets the critic mode, not assistant mode. Without it the subagent tends to be soft.

### Task

Brief, 2–5 sentences. What exactly is being reviewed and why.

**Good:**
> Review the specification for refactoring the printing subsystem. The goal is to migrate
> printed forms to the BСП "Печать" mechanism. Task #87.

**Bad:**
> See what is wrong here.

---

### Artifact

**Principle: the subagent works in the project context and reads files by itself. Never paste file contents into the prompt — only paths.**

If there are no files (the artifact exists only in chat) — provide the text as an exception.

#### Specification / plan

```
Read the specification: docs/specs/SPEC-резервирование-товаров.md
Source materials (task, analysis): docs/tasks/task-42.md
```

If there is no specification file — provide the plan text in the prompt (exception).

#### Code

```
Change basis: docs/specs/SPEC-резервирование-товаров.md
List of changed files: run `git diff --name-only HEAD~1` or `git status`
Key change files:
- src/Документы/ЗаказПокупателя/МодульОбъекта.bsl
- src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
```

If the specification was not produced — a link to the task:
```
Change basis: docs/tasks/task-42.md
```

#### Tests

```
Tests: tests/ДССЛ_ТестРезервирование/
Tested module: src/ОбщиеМодули/ДССЛ_РезервированиеТоваров/Module.bsl
Basis (specification): docs/specs/SPEC-резервирование-товаров.md
```

#### Form (UI)

```
Read the form module: src/Документы/ЗаказПокупателя/Формы/ФормаДокумента/Module.bsl
Change basis: docs/specs/SPEC-резервирование-товаров.md
```

#### Architecture

```
Architectural decision: docs/specs/ARCH-интеграция-crm.md
Key implementation files:
- src/ОбщиеМодули/ДССЛ_ИнтеграцияCRM/Module.bsl
- src/ОбщиеМодули/ДССЛ_ИнтеграцияCRM_Клиент/Module.bsl
```

---

### Skills

**For BSL code:**
```markdown
Read the following project skill files and use them as criteria during the review:
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/ssl-patterns/SKILL.md
- framework/skills/bsl-practices/form-patterns/SKILL.md
```

**For specification / plan:**
```markdown
Read the following project skill files and use them as criteria during the review:
- framework/skills/spec-writing/spec-standard/SKILL.md
```

**For form (UI):**
```markdown
- framework/skills/bsl-practices/form-patterns/SKILL.md
- framework/skills/bsl-practices/form-visual-requirements/SKILL.md
```

**For architecture:**
```markdown
- framework/skills/bsl-practices/ssl-patterns/SKILL.md
- framework/skills/bsl-practices/query-patterns/SKILL.md
- framework/skills/bsl-practices/coding-standards/SKILL.md
```

**For tests:**
```markdown
- framework/skills/bsl-practices/coding-standards/SKILL.md
- framework/skills/bsl-practices/error-handling/SKILL.md
```

---

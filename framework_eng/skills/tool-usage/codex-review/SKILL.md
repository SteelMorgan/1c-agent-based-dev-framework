---
name: codex-review
description: Review via external LLMs (Codex Review). The skill teaches an agent to run an independent review of artifacts through the Codex CLI (GPT) and gather the outcome. Use it when requesting a second opinion, invoking /review-gpt, /review-gemini, /review-all, or when the user asks to check a plan, specification, code, or architecture with an alternative model.
---

# Review via external LLMs (Codex Review)

## Purpose

The skill teaches an agent to obtain an **independent review** of artifacts from alternative LLMs by running them through CLI tools. The reviewer operates in a read-only sandbox, reads project files and framework skills, and returns a structured feedback.

**Principle:** Claude Code is the orchestrator. It formulates the task, passes the artifact and criteria (skills), and the external agent analyzes and responds on its own.

---

## When to apply

| Trigger | Action |
|---------|--------|
| The user invokes `/review-gpt` | Run the review through Codex CLI |
| The user invokes `/review-all` | Run GPT + Opus in parallel (see `opus-review`) |
| The user asks for a “second opinion” | Suggest `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Suggest reviewing the plan |

---

## Prompt construction

The prompt for the reviewer consists of three mandatory blocks. Detailed template: [references/prompt-template.md](references/prompt-template.md).

### Block 1: Task

What needs to be checked and in what context. Example:

> Conduct a review of the implementation plan for the integration with the external system.
> Context: customization of the standard UT configuration, task #42.

### Block 2: Artifact

The specific object under review. **Always provide paths to files — the reviewer reads them independently. Never insert file contents into the prompt.**

Rules by type:

- **Specification** — path to the specification file + paths to source materials (task, analysis)
- **Code** — basis for the changes (spec or task) + hint to use `git diff --name-only HEAD~1` + key files
- **Tests** — path to test files/directory + the module under test + the basis (spec)
- **Form** — path to the form module + basis for the changes
- **Architecture** — path to the architecture document + key implementation files
- **If there are no files** (artifact only in chat) — provide it as text as an exception

### Block 3: Skills (review criteria)

Links to `SKILL.md` files that the reviewer **will read itself** and use as criteria. Skill selection depends on artifact type:

| Artifact type | Skills |
|---------------|--------|
| BSL code | `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns` |
| Specification / plan | `spec-standard` |
| Form (UI) | `form-patterns`, `form-visual-requirements` |
| Architecture | `ssl-patterns`, `query-patterns`, `coding-standards` |
| Tests | `coding-standards`, `error-handling` |

Paths to skills are relative to the project root:
```
framework/skills/bsl-practices/<name>/SKILL.md
framework/skills/spec-writing/<name>/SKILL.md
```

For mixed artifacts (code + form) — combine skills from both groups.

---

## Running Codex CLI

### Command

```bash
codex exec \
  -p cx_gpt-5_3-codex-high \
  --sandbox read-only \
  --ephemeral \
  -o /tmp/codex-review-$(date +%s).txt \
  "<prompt>"
```

### Passing a long prompt

Prompts for the reviewer are almost always multi-line and contain special characters.
**Always** write the prompt to a temporary file and pass it via stdin:

```bash
# 1. Write the prompt to a temporary file
cat <<'EOF' > /tmp/codex-prompt-$(date +%s).txt
<prompt text — any length, with quotes, markdown, etc.>
EOF

# 2. Pass it through stdin
codex exec \
  -p cx_gpt-5_3-codex-high \
  --sandbox read-only \
  --ephemeral \
  -o /tmp/codex-review-$(date +%s).txt \
  - < /tmp/codex-prompt-*.txt
```

**Do not use** `echo "<prompt>"` — it breaks on quotes and special characters.

### Running in the background

Use the Bash tool with `run_in_background: true`. This allows monitoring progress without blocking the main conversation.

**Important:** remember the path to the `-o` file — it is the main way to obtain the result.

---

## Monitoring and collecting the result

### Step 1: Start

Write the prompt to a temporary file, then start Codex:

```
REVIEW_TS=$(date +%s)
PROMPT_FILE=/tmp/codex-prompt-${REVIEW_TS}.txt
RESULT_FILE=/tmp/codex-review-${REVIEW_TS}.txt

# Write the prompt (via Bash or Write tool)
cat <<'EOF' > $PROMPT_FILE
<prompt>
EOF

# Run in the background (via Bash with run_in_background: true)
codex exec -p cx_gpt-5_3-codex-high --sandbox read-only --ephemeral \
  -o $RESULT_FILE - < $PROMPT_FILE
```

Obtain the task_id from the output. Remember the `RESULT_FILE` path.

### Step 2: Monitor progress

Use **two methods in parallel**:

**Method A — TaskOutput (primary):**
```
TaskOutput(task_id, block=false)  — check status
```
If TaskOutput returns data — determine the status by keywords:
- `exec` — Codex is executing shell commands (reading files)
- `codex` — Codex is formulating the response
- `tokens used` — completion

**Method B — checking the `-o` file (fallback):**
```
Read(RESULT_FILE)  — if the file exists → Codex has finished
```

> ⚠️ **Important:** TaskOutput can lose the task (return "no task found").
> In that case **do not wait** — immediately check the `-o` file via Read.
> The `-o` file is created **atomically upon completion** of the Codex process —
> it does not exist while Codex is working, and appears only when the process
> has fully finished. If the file exists — Codex has definitely finished.

Report a brief status to the user:
- “GPT is reading project files...”
- “GPT is analyzing the artifact...”
- “GPT is forming the feedback...”

### Step 3: Retrieve the result

```
Read(RESULT_FILE)  — read the clean result
```

The `-o` file contains **only the final reviewer’s answer**, without Codex headers or logs.

If the `-o` file is empty or not created — read the last message from stdout via `TaskOutput(task_id, block=true)`.

### Step 4: Present to the user

- Show the reviewer’s feedback
- If there are critical issues — suggest correcting the artifact
- The feedback is an **opinion**; the final decision is up to the user

---

## Error handling

| Situation | Action |
|----------|--------|
| Codex CLI is not installed | Inform: `npm install -g @openai/codex` |
| Timeout (process did not finish) | Show partial output from stdout |
| API error (exit code ≠ 0) | Show stderr, suggest retrying |
| The `-o` file is empty | Take the last message from stdout |
| Prompt is too long | Pass it through stdin with `-` |

---

## Common mistakes

| Mistake | Consequence | How to avoid |
|--------|------------|--------------|
| Not including skills in the prompt | The reviewer gives a general response without referencing project standards | Always include the “Skills” block with paths to SKILL.md |
| Passing skill contents instead of paths | Prompt ballooning, token limit | Pass only paths — Codex will read files itself |
| Running without `--sandbox read-only` | The reviewer might try to modify files | Always specify `--sandbox read-only` |
| Forgetting `--ephemeral` | Disk pollution with sessions | Always specify `--ephemeral` |
| Not using `-o` | Need to parse noisy stdout | Always specify `-o` for a clean result |
| Providing the artifact without the task | The reviewer lacks context | Always start the prompt with the “Task” block |

---

## Related resources

- [Prompt template](references/prompt-template.md) — detailed template with examples
- [Opus review skill](../opus-review/SKILL.md) — review through the second Opus instance (Task tool)
- [Codex Review specification](../../../../docs/SPEC-codex-review.md) — full system specification
- [Framework reviewer](../../../subagents/reviewer.md) — internal reviewer agent (for comparing approaches)

---
depends_on:
  - framework/skills/bsl-practices/coding-standards/SKILL.md
  - framework/skills/bsl-practices/error-handling/SKILL.md
  - framework/skills/bsl-practices/query-patterns/SKILL.md
  - framework/skills/bsl-practices/ssl-patterns/SKILL.md
  - framework/skills/bsl-practices/form-patterns/SKILL.md
  - framework/skills/bsl-practices/form-visual-requirements/SKILL.md
  - framework/skills/spec-writing/spec-standard/SKILL.md
---

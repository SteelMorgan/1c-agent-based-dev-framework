---
name: codex-review
description: Review via external LLMs (Codex). The skill teaches the agent to launch an independent review of artifacts through the Codex CLI (GPT) and collect the result. Use it when requesting a second opinion, invoking /review-gpt, /review-all, or when the user asks to check a plan, specification, code, or architecture with an alternative model. The skill can also be used to forward an arbitrary task to another LLM at the user’s request.
---

# Review via Codex CLI

## When to use

| Trigger | Action |
|---------|--------|
| `/review-gpt` | Run a review through the Codex CLI |
| `/review-all` | GPT + Opus in parallel (see `opus-review`) |
| “second opinion” | Suggest `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Propose reviewing the plan |

---

## Step 0: Determine Codex mode

Before invoking, determine the Codex operating mode:

```bash
if [[ "${CUSTOM_CODEX_ENABLED:-0}" == "1" ]]; then
  echo true   # custom mode with profiles
else
  echo false  # standard mode
fi
```

| Result | Mode | Command |
|--------|------|---------|
| `true` | Custom API server | see [Mode: custom](#mode-custom) |
| `false` | Standard ChatGPT auth | see [Mode: default](#mode-default) |

---

## Mode: custom

The custom server is configured via `base_url` — use profiles from `config.toml`.

```bash
codex exec \
  -p cx_gpt-5_3-codex-high \
  --sandbox read-only \
  --ephemeral \
  -o /tmp/codex-review-$(date +%s).txt \
  - < /tmp/codex-prompt.txt
```

---

## Mode: default

Standard ChatGPT auth. The model and effort are passed via flags.

```bash
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --sandbox read-only \
  --ephemeral \
  -o /tmp/codex-review-$(date +%s).txt \
  - < /tmp/codex-prompt.txt
```

---

## Passing the prompt: directly or via file

### Directly in the argument (single quotes only)

Use this when the prompt is **short** (up to ~100 characters), **single-line**, and **does not contain backticks or `$`**:

```bash
codex exec -m gpt-5.4 -c 'model_reasoning_effort="high"' \
  --sandbox read-only --ephemeral \
  'Объясни назначение функции РассчитатьСумму в файле Module.bsl'
```

**Rules for quotes:**
- The prompt must always be enclosed in **single** quotes `'...'`
- Double quotes `"..."` trigger `Invalid JSON body`
- Characters such as `!`, `-`, `#`, and spaces are safe inside single quotes
- Pass a single quote inside the prompt via a file

### Via file (main approach for reviews)

Always use when the prompt is:
- **Multi-line** (any review prompt from a template)
- Contains backticks `` ` ``, `$`, `\`, or single quotes
- Longer than ~100 characters

```bash
# 1. Record the prompt
REVIEW_TS=$(date +%s)
PROMPT_FILE=/tmp/codex-prompt-${REVIEW_TS}.txt
RESULT_FILE=/tmp/codex-review-${REVIEW_TS}.txt

cat <<'EOF' > "$PROMPT_FILE"
<текст промпта — любой длины и символы без ограничений>
EOF

# 2. Run (default mode)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --sandbox read-only \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < "$PROMPT_FILE"
```

---

## Crafting the prompt

### Arbitrary task

If the task is not a review, the agent creates the prompt on its own. Principles:

- **One task — one prompt.** Clearly articulate what needs to be done.
- **Context via files.** If data from the project is required, mention the paths instead of pasting the content into the prompt. Codex will read the files itself.
- **Result via file.** If output (text, code) is expected, ask to write it to a specific path.

An example — analyzing a file:
```
Прочитай файл src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
Найди все места где выполняется запрос к базе данных вне транзакции.
Запиши список в /tmp/codex-result.txt в формате: имя функции, строка, описание проблемы.
```

An example — generating code:
```
Прочитай спецификацию docs/specs/SPEC-резервирование.md.
Сгенерируй заготовку модуля BSL согласно спецификации.
Запиши результат в src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
```

### Review

A review prompt consists of three blocks. Full template: [references/prompt-template.md](references/prompt-template.md).

**Block 1 — Task:** what is being verified and in what context (2–5 sentences).

**Block 2 — Artifact:** paths to files — the reviewer reads them directly. Never insert file content into the prompt.

**Block 3 — Skills:** paths to `SKILL.md` for each artifact type:

| Type | Skills |
|-----|--------|
| BSL code | `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns` |
| Specification | `spec-standard` |
| Form (UI) | `form-patterns`, `form-visual-requirements` |
| Architecture | `ssl-patterns`, `query-patterns`, `coding-standards` |
| Tests | `coding-standards`, `error-handling` |

Paths: `framework/skills/bsl-practices/<name>/SKILL.md`

---

## Monitoring and collecting the result

Run in the background (`run_in_background: true`). Remember the `RESULT_FILE`.

**Checking completion:**

```
# Method A — TaskOutput
TaskOutput(task_id, block=false)

# Method B — file -o (created atomically upon completion)
Read(RESULT_FILE)  — if it exists → Codex finished
```

> If `TaskOutput` returned “no task found” — immediately check the `-o` file.

Report status to the user based on keywords in stdout:
- `exec` → “GPT is reading project files..."
- `codex` → “GPT is forming the answer..."
- `tokens used` → completed

**Obtaining the result:**
```
Read(RESULT_FILE)  — clean answer without logs
```

If the file is empty → `TaskOutput(task_id, block=true)`.

---

## Error handling

| Situation | Action |
|-----------|--------|
| `Invalid JSON body` | The prompt was passed in double quotes — switch to single quotes or use a file |
| Codex CLI is not installed | `npm install -g @openai/codex` |
| Timeout | Show the partial result from stdout |
| File `-o` is empty | Take the last message from `TaskOutput` |

---
name: codex-review
description: Review via external LLM (Codex). The skill teaches the agent to launch an independent review of artifacts through the Codex CLI (GPT) and collect the result. Use it when requesting a second opinion, invoking /review-gpt, /review-all, or when the user asks to check a plan, specification, code, or architecture with an alternative model. The skill can also be used to hand off an arbitrary task to another LLM at the user's request.
---

# Review via Codex CLI

## When to use

| Trigger | Action |
|---------|--------|
| `/review-gpt` | Run a review through the Codex CLI |
| `/review-all` | GPT + Opus in parallel (see `opus-review`) |
| “second opinion” | Offer `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Suggest reviewing the plan |

---

## Step 0: determine Codex mode

Before invoking, identify the Codex operating mode:

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
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -p cx_gpt-5_3-codex-high \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

---

## Mode: default

Standard ChatGPT auth. The model and effort are passed via flags.

```bash
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

---

## Passing the prompt: directly or via file

### Directly in the argument (single quotes only)

Use this when the prompt is **short** (up to ~100 characters), **single-line**, and **does not include backticks or `$`:**

```bash
codex exec -m gpt-5.4 -c 'model_reasoning_effort="high"' \
  --ephemeral \
  'Объясни назначение функции РассчитатьСумму в файле Module.bsl'
```

**Why single quotes:**
- When the AI agent generates a command with double quotes, it often forgets to escape `$`, `` ` ``, `!`, so the shell expands them, the argument is garbled, and the API returns `Invalid JSON body`
- Single quotes `'...'` pass the content as-is, which is safe for `!`, `-`, `#`, `$`, spaces
- Pass a single quote inside the prompt via a file

### Via file (main approach for reviews)

Always use this method when the prompt is:
- **Multi-line** (any review prompt from the template)
- Contains backticks `` ` ``, `$`, `\`, or single quotes
- Longer than ~100 characters

```bash
# 1. Record the prompt
PROMPT_FILE=$(mktemp /tmp/codex-prompt-XXXXXX.txt)
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)

cat <<'EOF' > "$PROMPT_FILE"
<текст промпта — любой длины и символы без ограничений>
EOF

# 2. Run (default mode)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < "$PROMPT_FILE"
```

---

## Prompt construction

### Arbitrary task

If the task is not a review, the agent builds the prompt independently. Principles:

- **One task — one prompt.** Clearly state what needs to be done.
- **Context via files.** If the project data is required, list the paths instead of pasting the contents into the prompt. Codex will read the files itself.
- **Result via file.** If you expect output (text, code), ask to write it to a specific path.

Example — analyzing a file:
```
Read the file src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
Find all places where a database query is executed outside a transaction.
Write the list to /tmp/codex-result.txt in the following format: function name, line, issue description.
```

Example — generating code:
```
Read the specification docs/specs/SPEC-резервирование.md.
Generate a BSL module skeleton according to the specification.
Write the result to src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
```

### Review

A review prompt consists of three blocks. Full template: [references/prompt-template.md](references/prompt-template.md).

**Block 1 — Task:** what is being checked and in what context (2–5 sentences).

**Block 2 — Artifact:** paths to files — the reviewer reads them on their own. Prefer paths; insert text only if the artifact does not exist on disk (diff, generated fragment) or a short critical context is required.

**Block 3 — Skills:** paths to `SKILL.md` by artifact type:

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

Run in the background (`run_in_background: true`). Keep track of the `RESULT_FILE`.

**Checking completion:**

```
# Method A — TaskOutput
TaskOutput(task_id, block=false)

# Method B — file -o (created atomically upon completion)
Read(RESULT_FILE)  — if it exists → Codex has finished
```

> If `TaskOutput` returned “no task found” — immediately check the `-o` file.

Optional UX hints — if stdout contains characteristic words, you can inform the user about an approximate status (these words may change between CLI versions):
- `exec` → “GPT is reading project files...”
- `codex` → “GPT is forming the answer...”
- `tokens used` → completed

**Obtaining the result:**
```
Read(RESULT_FILE)  — the clean answer without logs
```

If the file is empty → `TaskOutput(task_id, block=true)`.

---

## Error handling

| Situation | Action |
|----------|--------|
| `Invalid JSON body` | The prompt was distorted by shell expansion of double quotes — switch to single quotes or pass via a file |
| Codex CLI is not installed | `npm install -g @openai/codex` |
| Auth failure / `login required` | Run `codex login`; if using custom mode — verify `config.toml` and the API key |
| Unknown profile `-p` | Check the profile name in `~/.codex/config.toml` |
| Rate limit / 429 | Tell the user, wait 30–60 seconds, retry |
| Non-zero exit, file `-o` not created | Show stderr from `TaskOutput`; check cwd, paths, model |
| Timeout | Show the partial result from stdout |

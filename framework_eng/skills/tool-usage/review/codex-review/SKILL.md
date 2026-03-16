---
name: codex-review
description: Reviews via external LLM (Codex). The skill teaches the agent to launch an independent review of artifacts through Codex CLI (GPT) and collect the result. Use it when requesting a second opinion, invoking /review-gpt, /review-all, or when the user asks to verify a plan, specification, code, or architecture with an alternative model. The skill can also be used to delegate an arbitrary task to another LLM at the user's request.
---

# Review via Codex CLI

## When to apply

| Trigger | Action |
|---------|--------|
| `/review-gpt` | Launch a review via Codex CLI |
| `/review-all` | GPT + Opus in parallel (see `opus-review`) |
| “second opinion” | Suggest `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Suggest reviewing the plan |

---

## Step 0: determine Codex mode

Before invoking Codex determine which mode is active:

```bash
if [[ "${CUSTOM_CODEX_ENABLED:-0}" == "1" ]]; then
  echo true   # custom mode with profiles
else
  echo false  # default mode
fi
```

| Result | Mode | Command |
|--------|------|---------|
| `true` | Custom API server | see [Mode: custom](#режим-custom) |
| `false` | Standard ChatGPT auth | see [Mode: default](#режим-default) |

---

## Mode: custom

A custom server is configured via `base_url` — use the profiles from `config.toml`.

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

Standard ChatGPT auth. The model and effort are passed with flags.

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

## Passing the prompt: inline or via file

### Direct in the argument (single quotes only)

Use this when the prompt is **short** (up to ~100 characters), **single-line**, and **does not contain backticks or `$`**:

```bash
codex exec -m gpt-5.4 -c 'model_reasoning_effort="high"' \
  --ephemeral \
  'Explain the purpose of the РассчитатьСумму function in Module.bsl'
```

**Why single quotes:**
- When the AI agent generates commands in double quotes it often forgets to escape `$`, `` ` ``, `!` — the shell expands them, the argument is mangled, and the API returns `Invalid JSON body`
- Single quotes `'...'` transmit the content as-is — safe for `!`, `-`, `#`, `$`, spaces
- Pass a single quote inside the prompt through a file

### Via file (the main method for reviews)

Always use this when the prompt is:
- **Multiline** (any review prompt from the template)
- Contains backticks `` ` ``, `$`, `\`, or single quotes
- Longer than ~100 characters

```bash
# 1. Write the prompt
PROMPT_FILE=$(mktemp /tmp/codex-prompt-XXXXXX.txt)
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)

cat <<'EOF' > "$PROMPT_FILE"
<prompt text — any length and characters without restrictions>
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

## Crafting the prompt

### Arbitrary task

If the task is not a review, the agent builds the prompt independently. Principles:

- **One task — one prompt.** Clearly state what needs to be done.
- **Context via files.** If project data is required, reference the paths instead of embedding the contents in the prompt. Codex will read the files itself.
- **Output via file.** If you expect output (text, code), ask to write it to a specific path.

Example — analyzing a file:
```
Read the file src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
Find all places where a database query is executed outside a transaction.
Write the list to /tmp/codex-result.txt in the format: function name, line, issue description.
```

Example — code generation:
```
Read the specification docs/specs/SPEC-резервирование.md.
Generate a BSL module stub according to the specification.
Write the result to src/ОбщиеМодули/ДССЛ_Резервирование/Module.bsl.
```

### Review

The review prompt consists of three blocks. Full template: [references/prompt-template.md](references/prompt-template.md).

**Block 1 — Task:** what is being checked and in what context (2-5 sentences).

**Block 2 — Artifact:** paths to the files — the reviewer reads them itself. Prefer paths; include inline text only if the artifact does not exist on disk (diff, generated fragment) or a short critical context is needed.

**Block 3 — Skills:** paths to the `SKILL.md` relevant to the artifact type:

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

**Completion check:**

```
# Method A — TaskOutput
TaskOutput(task_id, block=false)

# Method B — the `-o` file (created atomically on completion)
Read(RESULT_FILE)  — if it exists → Codex is done
```

> If `TaskOutput` returned "no task found," immediately check the `-o` file.

Optional UX hints — if stdout contains characteristic words, you can inform the user of an approximate status (words may change between CLI versions):
- `exec` → "GPT is reading the project files..."
- `codex` → "GPT is crafting the response..."
- `tokens used` → completed

**Getting the result:**
```
Read(RESULT_FILE)  — clean response without logs
```

If the file is empty → `TaskOutput(task_id, block=true)`.

---

## Error handling

| Situation | Action |
|----------|--------|
| `Invalid JSON body` | The prompt was mangled by shell expansion (double quotes) — send it via a file or single quotes |
| Codex CLI is not installed | `npm install -g @openai/codex` |
| Auth failure / `login required` | Run `codex login`; if in custom mode — check `config.toml` and the API key |
| Unknown profile `-p` | Verify the profile name in `~/.codex/config.toml` |
| Rate limit / 429 | Inform the user, wait 30-60 seconds, retry |
| Non-zero exit, `-o` file not created | Show stderr from `TaskOutput`; check cwd, paths, model |
| Timeout | Show the partial result from stdout |
| `-o` file is empty | Take the last message from `TaskOutput` |

---

## Related resources

- [Prompt template](references/prompt-template.md)
- [opus-review](../opus-review/SKILL.md) — review using the second Opus instance

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

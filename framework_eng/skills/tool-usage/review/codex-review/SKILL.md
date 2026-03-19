---
name: codex-review
description: Review via external LLM (Codex). The skill teaches the agent to run an independent review of artifacts via the Codex CLI (GPT) and gather the result. Use when requesting a second opinion, invoking /review-gpt or /review-all, or when the user asks to check a plan, specification, code, or architecture with an alternative model. The skill can be used to hand off an arbitrary task to another LLM at the user's request.
---

# Review via Codex CLI

## When to apply

| Trigger | Action |
|---------|--------|
| `/review-gpt` | Launch a review via the Codex CLI |
| `/review-all` | GPT + Opus in parallel (see `opus-review`) |
| “second opinion” | Suggest `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing a specification | Offer to review the plan |

---

## Step 0: determine Codex mode

Before invoking, determine Codex operation mode:

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
| `false` | Default ChatGPT auth | see [Mode: default](#mode-default) |

---

## Mode: custom

The custom server is configured via `base_url` — use the profiles from `config.toml`.

```bash
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -p cx_gpt-5_3-codex-high \
  --dangerously-bypass-approvals-and-sandbox \
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
  --dangerously-bypass-approvals-and-sandbox \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

> **Sandbox is not needed.** The entire environment (devcontainer) is already sandboxed.
> `--dangerously-bypass-approvals-and-sandbox` is safe: codex is ephemeral,
> review is a read-only task, project files are not modified.

---

## Sending prompts: directly or via file

### Direct argument (short single-line prompt without `` ` `` and `$`)

```bash
codex exec -m gpt-5.4 -c 'model_reasoning_effort="high"' \
  --dangerously-bypass-approvals-and-sandbox --ephemeral \
  'Explain the purpose of the РассчитатьСумму function in Module.bsl'
```

Only single quotes — double quotes trigger shell expansion and `Invalid JSON body`.

### Via file (main method for reviews — multiline, >100 characters, or special characters)

```bash
# 1. Save the prompt
PROMPT_FILE=$(mktemp /tmp/codex-prompt-XXXXXX.txt)
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)

cat <<'EOF' > "$PROMPT_FILE"
<prompt text — any length and characters with no limits>
EOF

# 2. Run (default mode)
codex exec \
  -m gpt-5.4 \
  -c 'model_reasoning_effort="high"' \
  --dangerously-bypass-approvals-and-sandbox \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < "$PROMPT_FILE"
```

---

## Prompt construction

### Arbitrary task

Principles: one task — one prompt; context via file paths (Codex will read them itself); result — to a specific path.

### Review

A review prompt consists of three blocks. Full template: [references/prompt-template.md](references/prompt-template.md).

**Block 1 — Task:** what is being checked and in what context (2-5 sentences).

**Block 2 — Artifact:** paths to files — the reviewer reads them directly. Prefer paths; include text only if the artifact does not exist on disk (diff, generated fragment) or a short critical context is required.

**Block 3 — Skills:** paths to the relevant `SKILL.md` for the artifact type:

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

Run in the background (`run_in_background: true`). Check via `TaskOutput(task_id, block=false)` or `Read(RESULT_FILE)` (the `-o` file is created atomically upon completion). If the file is empty → `TaskOutput(task_id, block=true)`.

---

## Error handling

| Situation | Action |
|----------|--------|
| `Invalid JSON body` | The prompt was mangled by shell expansion (double quotes) — send via file or single quotes |
| Codex CLI not installed | `npm install -g @openai/codex` |
| Auth failure / `login required` | Run `codex login`; if in custom mode — check `config.toml` and the API key |
| Unknown profile `-p` | Verify the profile name in `~/.codex/config.toml` |
| Rate limit / 429 | Notify the user, wait 30-60 sec, retry |
| Non-zero exit, `-o` file not created | Show stderr from `TaskOutput`; check cwd, paths, model |
| Timeout | Show partial result from stdout |
| `-o` file is empty | Take the last message from `TaskOutput` |

---

Template prompt: `references/prompt-template.md`. Parallel reviewer: `opus-review`.

---
depends_on:

---

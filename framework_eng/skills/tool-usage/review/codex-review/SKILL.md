---
name: codex-review
description: Iterative review via Codex CLI (GPT). The agent and Codex discuss the artifact in a loop of up to five iterations, verifying findings and producing a unified opinion. Use when invoking /review-gpt, /review-all, requesting a second opinion, or handing over an arbitrary task to another LLM.
---

# Iterative review via Codex CLI

## When to apply

| Trigger | Action |
|---------|--------|
| Review request | Launch an iterative review via Codex CLI |
| "second opinion" | Suggest `/review-gpt` or `/review-all` |
| Complex architecture, > 5 files | Recommend a review |
| Before implementing the specification | Offer to review the plan |

---

## Iterative review protocol

The review is a dialogue between the agent (Claude) and the reviewer (Codex). A maximum of **5 iterations**.

### Iteration 1 — initial request

**Artifact review:**

```
1. Task — what business goal was set and the context
2. Artifact — paths to the files
3. Rules — the skills and guidelines that should have been applied during creation
```

**Second opinion (arbitrary question):**

```
1. Artifact — paths or text
2. Question — what exactly to evaluate
3. Context — relevant project rules (at the agent's discretion)
```

Add to the beginning of the prompt:
```
IMPORTANT: Do NOT create, modify, or delete any project files. You may only READ files for analysis. Your final text response will be captured automatically.
```

Send Codex in the background and wait for a response.

### Iteration 2 — verification and comments

1. **Read Codex's response**
2. **Assign each finding an ID** (F-01, F-02, ...)
3. **Verify** each finding against the actual code/artifact:

| Status | Meaning |
|--------|---------|
| `agree` | Confirmed through verification |
| `partial` | Partially correct, needs clarification |
| `disagree` | Refuted (cite file:line + fact) |
| `withdrawn` | Codex retracted it in the next iteration |
| `out_of_scope` | Outside the scope of the current review |

4. **Add your own findings** (ID: C-01, C-02, ...) — items Codex missed
5. **Compose a follow-up** (see "Follow-up format")
6. **Report progress to the user:** `Round 2/5: N agreed, M disputed`
7. **Send Codex**

### Iterations 3-5 — convergence

Repeat the same verification cycle. Additional rules:

- **Scope freeze:** starting from iteration 3, new findings are only allowed if they are BLOCK/WARN level and there is verifiable evidence
- **Stylistics are not discussed:** INFO/style findings are noted without further iterations
- **Report progress to the user** after each iteration

### Completion

| Condition | Action |
|-----------|--------|
| **Consensus** — all points `agree`/`withdrawn` | Finish |
| **Stalemate** — `unresolved` unchanged for two cycles in a row | Finish and present both positions |
| **Limit** — 5 iterations | Finish with the current state |

### Final report to the user

```
Unified opinion: [agreed findings]
Disagreements: [Claude's position vs Codex's position] (if any)
Iterations: N out of 5
Recommendation: [what to do]
```

---

## Follow-up prompt format

```markdown
# Review Follow-up (Round N)

## Agreed (closed)
- F-01: [short description] → agree
- F-03: [short description] → withdrawn

## Disputed (discuss)

### F-02
Your finding: [essence]
My status: disagree
My arguments: at <file:line> the code does <fact>, which complies with the specification <quote>
Question: reconsider or provide a counterargument with a concrete reference to code

### F-04
Your finding: [essence]
My status: partial
My arguments: [what is right, what is not]

## My additions
- C-01: [new finding + file:line + justification]

## Discussion rules
- Discuss only the listed IDs
- Do not return to closed items
- New findings — only BLOCK/WARN with evidence
- For each disputed item: KEEP / REVISE / WITHDRAW + rationale
```

---

## Constructing the initial prompt

Full template: [references/prompt-template.md](references/prompt-template.md).

**Block 1 — Task:** what is being checked and why (2-5 sentences).

**Block 2 — Artifact:** paths to the files (the reviewer reads them directly).

**Block 3 — Rules:** paths to the relevant skills by artifact type:

| Type | Skills |
|-----|--------|
| BSL code | `coding-standards`, `error-handling`, `query-patterns`, `ssl-patterns`, `form-patterns` |
| Specification | `spec-standard` |
| Form (UI) | `form-patterns`, `form-visual-requirements` |
| Architecture | `ssl-patterns`, `query-patterns`, `coding-standards` |
| Tests | `coding-standards`, `error-handling` |

Paths: `framework/skills/bsl-practices/<name>/SKILL.md`

---

## Launching Codex

### Determine the mode

```bash
if [[ "${CUSTOM_CODEX_ENABLED:-0}" == "1" ]]; then
  echo "custom"
else
  echo "default"
fi
```

### Custom

```bash
RESULT_FILE=$(mktemp /tmp/codex-review-XXXXXX.txt)
codex exec \
  -p cx_gpt-5_3-codex-high \
  --dangerously-bypass-approvals-and-sandbox \
  --ephemeral \
  -o "$RESULT_FILE" \
  - < /tmp/codex-prompt.txt
```

### Default

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

Run it in the background (`run_in_background: true`). Result: `Read(RESULT_FILE)`.

---

## Error handling

| Situation | Action |
|----------|--------|
| `Invalid JSON body` | Send the prompt through a file |
| Codex CLI not installed | `npm install -g @openai/codex` |
| Auth failure | `codex login`; custom → check `config.toml` |
| Rate limit / 429 | Wait 30-60 seconds and retry |
| Non-zero exit, file not created | Show stderr; check cwd, paths, and model |
| `-o` file is empty | Take the last message from `TaskOutput` |

---

Prompt template: `references/prompt-template.md`. Parallel reviewer: `opus-review`.

---
depends_on:

---

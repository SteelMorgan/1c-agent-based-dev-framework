---
name: codex-image-gen
description: "Use when you need to generate or edit a raster image (UI mockup, wireframe, illustration for a document/spec, diagram, icon, test fixture). Claude/Opus do not draw images themselves, so this skill delegates the work to Codex/GPT through `codex exec` with the `image_generation` tool, placing the result in `tasks/<id>/assets/`. TRIGGERS: the user asks to \"make an image / draw / generate an image / need a mockup / need a screenshot sketch / illustration / mockup / wireframe / diagram / fix this image / edit PNG\"."
capabilities: content-generation,image-generation,cross-provider,delegation
---

# Codex Image Generation

A thin wrapper around the Codex CLI for generating and editing raster images. Claude/Opus as the primary agent **cannot** draw images, so when such a task appears you must explicitly delegate it to Codex through the wrapper described below. This is not a review scenario: image_gen really creates a file on disk, so Codex is launched with `--sandbox workspace-write`, unlike cross-provider-review.

## When to use

| Situation | Action |
|----------|--------|
| A UI mockup / wireframe of a 1С form is needed for UX discussion before implementation | Apply the "UI / app mockup" template from references/prompt-guide.md |
| An illustration is needed for a spec, ADR, article, or presentation | Apply the "Office / business illustration" or "Lifestyle scene" template |
| A diagram is needed as a raster image (not a vector mermaid/PlantUML diagram) | Apply the "Diagram / explainer visual" template |
| A test fixture is needed: icon, avatar, placeholder for a `Picture`/`AttachedFile` field | Apply the "Icon / sticker" or "Product photo" template |
| An existing PNG needs editing (change color, remove an object, replace the background) | Pass the original through `--reference-image`, apply the editing template |
| The user asks, "can you draw N images and compare them" | Make N independent wrapper calls - Codex keeps image state only within one session |

## When not to use

- A vector diagram is needed (mermaid, PlantUML, dot) - write it as text, image_gen is not needed.
- A screenshot of a real 1С form is needed - use `web-test-1c` / `playwright` / `gui-control`, not generation.
- ASCII art or a unicode diagram is needed in a document - write it by hand, there is no need to delegate it.

## Anti-patterns

- **"Describe the image in words instead of generating it"** - if the user explicitly asked for an image, a description does not replace it.
- **Trying to generate through cross-provider-review / `codex_review.py`** - there `--sandbox read-only` is hard-coded, so no file will appear on disk. Use `codex_image_gen.py` specifically.
- **Passing an unstructured prompt** ("make it beautiful") - image_gen will return junk. Take a template from references/prompt-guide.md and fill in the axes.
- **Putting the output in an arbitrary path** - the wrapper always writes to `tasks/<id>/assets/`, so the artifact lives next to the task. If there is no task, discuss with the user whether one needs to be created.

## How to call it

The wrapper is located next to SKILL.md:

```bash
.claude/skills/codex-image-gen/scripts/codex_image_gen.py \
  --task-id <id> \
  --filename <name>.png \
  --prompt-file path/to/prompt.txt
```

or as a short one-liner:

```bash
.claude/skills/codex-image-gen/scripts/codex_image_gen.py \
  --task-id <id> \
  --filename <name>.png \
  --prompt "High-fidelity UI mockup for ..."
```

For editing an existing image:

```bash
.claude/skills/codex-image-gen/scripts/codex_image_gen.py \
  --task-id <id> \
  --filename <name>-v2.png \
  --reference-image tasks/<id>/assets/<name>.png \
  --prompt "Keep the original composition. Change the background to ..."
```

Wrapper options:

| Flag | Purpose |
|------|---------|
| `--task-id` | Required. Output goes to `tasks/<task-id>/assets/`. The directory is created automatically. |
| `--filename` | Required. Name of the final file (`*.png`/`*.jpg`/`*.webp`). |
| `--prompt` or `--prompt-file` | Prompt content. One of the two is required. |
| `--reference-image` | Path to the source image for editing mode. Can be repeated. |
| `--model` | Codex model. Default is `gpt-5`. |
| `--reasoning-effort` | `low`/`medium`/`high`. Default is `medium`. |
| `--timeout-sec` | Timeout for one call. Default is 600. |
| `--dry-run` | Shows the final `codex exec` command without running it. Useful for debugging. |

The wrapper prints a JSON block to stdout: `{"status": "ok", "files": ["abs/path/...png", ...]}` or `{"status": "error", "reason": "..."}`. Consume the result programmatically from this JSON.

## How to compose the prompt

`references/prompt-guide.md` is a working guide with 8 sections: general principle, prompt axes (story/style/light/background/constraints), a generation constructor template, an editing constructor template, 10 ready-made templates for common generation scenarios, 10 templates for editing, common mistakes, and an iterative protocol. You MUST read the section relevant to your task before composing the prompt - image_gen is nonlinearly sensitive to structure.

Hard prompt requirements specific to this wrapper, not to the general image_gen:

- The prompt MUST include the instruction `Save the resulting image as <filename> in the current working directory.` - the wrapper appends it automatically to the end of the prompt, but if you are writing a prompt file, do not add anything extra about saving.
- Do not specify absolute paths in the prompt, and do not jump outside the CWD - Codex is launched with `--sandbox workspace-write -C <output_dir>`, and any attempt to write outside `tasks/<id>/assets/` will be blocked by the kernel sandbox.
- If you ask for multiple variants in one session, Codex can save only one file (`--filename`). For a series, make several separate wrapper calls.

## How to accept the result

1. The wrapper returned `status: ok` plus a list of files -> check `ls tasks/<id>/assets/<filename>` (`stat` is enough). If the file is missing, it is `status: error` regardless of what Codex said.
2. Open the image (or ask the user), compare it with the prompt across the axes: story/composition/style/light/background/constraints. If there is a mismatch on 1-2 axes, make an iterative correction with a short prompt: "Keep everything else unchanged. <delta>". If there are many mismatches, rebuild the prompt from scratch.
3. If the result is good, mention the file path in the response to the user. If the file is intended for a task document (spec, ADR, README), add a markdown link and leave the image inline.

## Known limitations

- Availability of the `image_generation` tool depends on the Codex CLI model and settings. If the wrapper fails with a message like "model does not support image_generation", tell the user and suggest switching the model via `--model` (for example, `gpt-5`/`gpt-5-codex`) or opening a ticket to update the Codex CLI.
- Codex may "go into reasoning" and return only a text answer without a file. The wrapper checks whether the file exists and reports `status: error` with a hint to retry, explicitly requiring the file to be saved.
- The default image size is determined by the model; the wrapper passes the hint `Use 1024x1024 unless the user explicitly asked for another size.` in the prompt - override it with an explicit instruction in your own prompt.

## Why a separate skill, not cross-provider-review

`cross-provider-review` creates a sandbox in `.review-sandboxes/`, hard-codes `--sandbox read-only`, and is intended for a second opinion on artifacts - with no writes to disk and no side effects. Image generation, by contrast, is a target write to the project's result folder. The semantics and security modes are different, so `codex_image_gen.py` is a standalone wrapper, not a flag in `codex_review.py`.

---
depends_on:
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
---

---
name: codex-image-gen
description: "For generating and editing images and mockups"
capabilities: content-generation,image-generation,cross-provider,delegation
---

# Codex Image Generation

A thin wrapper around Codex CLI for generating and editing raster images. Claude/Opus as the primary agent **cannot** draw images, so when such a task appears you must explicitly delegate it to Codex through the wrapper described below. This is not a review scenario: image_gen really creates a file on disk, so Codex runs in `--sandbox workspace-write`, unlike cross-provider-review.

## When to use

| Situation | Action |
|----------|--------|
| A UI mockup / wireframe of a 1C form is needed for UX discussion before implementation | Apply the "UI / app mockup" template from references/prompt-guide.md |
| An illustration is needed for a spec, ADR, article, or presentation | Apply the "Office / business illustration" or "Lifestyle scene" template |
| A diagram is needed as a raster image (not vector mermaid/PlantUML) | Apply the "Diagram / explainer visual" template |
| A test fixture is needed: an icon, avatar, placeholder for a `Picture`/`AttachedFile` attribute | Apply the "Icon / sticker" or "Product photo" template |
| An existing PNG needs editing (change color, remove an object, replace the background) | Pass the original through `--reference-image`, apply the editing template |
| The user asks "can you draw N images and compare them" | Make N independent wrapper calls - Codex keeps image state only within a single session |

## When not to use

- A vector diagram is needed (mermaid, PlantUML, dot) - write it as text, image_gen is not needed.
- A screenshot of a real 1C form is needed - use `web-test-1c` / `playwright` / `gui-control`, not generation.
- ASCII art or a unicode diagram is needed in a document - write it by hand; there is no need to delegate it.

## Anti-patterns

- **"Describe the image in words instead of generating it"** - if the user explicitly asked for an image, a description does not replace it.
- **Trying to generate through cross-provider-review / `codex_review.py`** - there `--sandbox read-only` is hardcoded, so no file will appear on disk. Use `codex_image_gen.py` specifically.
- **Passing an unstructured prompt** ("make it beautiful") - image_gen will return junk. Take a template from references/prompt-guide.md and fill in the axes.
- **Writing output to an arbitrary path** - the wrapper always writes to `tasks/<id>/assets/`, so the artifact lives next to the task. If there is no task, discuss with the user whether one should be created.

## How to call it

The wrapper lives next to SKILL.md:

```bash
.claude/skills/codex-image-gen/scripts/codex_image_gen.py \
  --task-id <id> \
  --filename <name>.png \
  --prompt-file path/to/prompt.txt
```

or as a short inline string:

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
| `--model` | Codex model. Defaults to `gpt-5`. |
| `--reasoning-effort` | `low`/`medium`/`high`. Defaults to `medium`. |
| `--timeout-sec` | Timeout for a single call. Defaults to 600. |
| `--dry-run` | Shows the final `codex exec` command without running it. Useful for debugging. |

The wrapper prints a JSON block to stdout: `{"status": "ok", "files": ["abs/path/...png", ...]}` or `{"status": "error", "reason": "..."}`. Consume the result programmatically from this JSON.

## How to compose a prompt

`references/prompt-guide.md` is a working guide with 8 sections: general principle, prompt axes (plot/style/light/background/constraints), a template builder for generation, a template builder for editing, 10 ready-made templates for common generation scenarios, 10 templates for editing, common mistakes, and an iterative protocol. You MUST read the section relevant to your task before composing the prompt - image_gen is nonlinearly sensitive to structure.

Strict prompt requirements specific to this wrapper (not to image_gen in general):

- The prompt MUST contain the instruction `Save the resulting image as <filename> in the current working directory.` - the wrapper appends it automatically to the end of the prompt, but if you are writing a prompt file, do not add anything extra about saving.
- Do not specify absolute paths in the prompt; do not step outside the CWD - Codex runs with `--sandbox workspace-write -C <output_dir>`, and any attempt to write outside `tasks/<id>/assets/` will be blocked by the kernel sandbox.
- If you ask for multiple variants in one session, Codex can save only one file (`--filename`). For a series, make multiple separate wrapper calls.

## How to accept the result

1. The wrapper returned `status: ok` + a list of files -> check `ls tasks/<id>/assets/<filename>` (`stat` is enough). If the file is missing, it is `status: error` regardless of what Codex said.
2. Open the image (or ask the user), compare it against the prompt along the axes: plot/composition/style/light/background/constraints. If there is a mismatch along 1-2 axes, iteratively correct it with a short prompt like "Keep everything else unchanged. <delta>". If there are many mismatches, rebuild the prompt from scratch.
3. If the result is acceptable, mention the file path in the response to the user. If the file is intended for a task document (spec, ADR, README), add a markdown link and keep the image inline.

## Known limitations

- The availability of the `image_generation` tool depends on the model and the Codex CLI settings. If the wrapper fails with a message like "model does not support image_generation" - tell the user and suggest switching the model via `--model` (for example, `gpt-5`/`gpt-5-codex`) or opening a ticket to update the Codex CLI.
- Codex may "go into reasoning" and return only a text response without a file. The wrapper checks for the file and reports `status: error` with a hint to retry, explicitly requiring the file to be saved.
- The default image size is determined by the model; the wrapper passes the hint `Use 1024x1024 unless the user explicitly asked for another size.` in the prompt - override it with an explicit instruction in your own prompt.

## Why a separate skill, not cross-provider-review

`cross-provider-review` creates a sandbox in `.review-sandboxes/`, hardcodes `--sandbox read-only`, and is intended for a second opinion on artifacts - without writing to disk and without side effects. Image generation, by contrast, is a targeted write to disk in the project's output folder. The semantics and security modes are different, so `codex_image_gen.py` is a standalone wrapper, not a flag in `codex_review.py`.

---
depends_on:
  - framework/skills/tool-usage/review/cross-provider-review/SKILL.md
---

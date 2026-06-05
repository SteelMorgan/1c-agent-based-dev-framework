---
name: docx-convert
description: "Use for converting Word documents (.docx) to Markdown with extracted images (requirements, spec, instructions, vendor documentation). Helps obtain GFM text via pandoc with post-processing of HTML tables and image paths."
capabilities: content-generation,document-conversion
---

# Word → Markdown Conversion

A thin wrapper around `pandoc` for converting `.docx` to GitHub-Flavored Markdown with extraction of embedded images. It also post-processes the result: fixes image paths and turns HTML tables (which pandoc leaves as-is in complex cases) into Markdown pipe tables.

## When to use

| Situation | Action |
|----------|----------|
| The client sent requirements in `.docx`, and you need to put them in the repository as md | `docx2md.sh input.docx` |
| Vendor documentation is in Word, and you need to feed it to the agent | `docx2md.sh input.docx output_dir` |
| The document has complex tables and styles — pandoc skips them | mammoth (see below) |
| You only need the text content without images | `pandoc input.docx --to=gfm -o out.md` directly |

## When NOT to use

- You need HTML — use `pandoc --to=html`, the script is not needed.
- You need PDF — use `pandoc --to=pdf` (requires LaTeX), the script is not needed.
- The document was created with WordArt/SmartArt/shapes — these are lost during conversion, this is a limitation of pandoc.

## Dependencies

- `pandoc` ≥ 3.x — the main converter
- `python3` — post-processing (`html_tables_to_md.py`)
- `mammoth` (python) — an optional alternative for complex tables

## Quick Start

```bash
# The result is placed next to the file, in a directory with the same name without the extension
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx"

# With an explicit output directory
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx" "/path/to/output"
```

Result:
- `output/document.md` — text in GFM with pipe tables
- `output/images/` — all images from the document (png/jpeg/emf/wmf)

> When used from a project where the framework is installed via symlinks, the script path is: `.claude/skills/docx-convert/docx2md.sh`.

## Manual Commands (without the script)

### Text only
```bash
pandoc input.docx --from=docx --to=gfm --wrap=none -o output.md
```

### Text + images
```bash
pandoc input.docx --from=docx --to=gfm --wrap=none \
    --extract-media=./images \
    -o output.md
```

### Via mammoth (for complex tables/styles)
```bash
python3 -c "
import mammoth, pathlib
result = mammoth.convert_to_markdown(open('input.docx', 'rb'))
pathlib.Path('output.md').write_text(result.value)
"
```

## Anti-Patterns

- **Converting `.doc` (legacy format)** — pandoc accepts only `.docx`. First resave it through LibreOffice/Word.
- **Expecting formulas to be preserved** — OMML is converted to LaTeX only partially, complex formulas are better rebuilt manually.
- **Applying it to scans/PDFs** — this is not a docx-convert task; use other tools for OCR.

## Notes

- Complex Word elements (WordArt, SmartArt, shapes) are lost — this is normal for pandoc conversion.
- Embedded images are extracted correctly in png, jpeg, emf, wmf formats.
- Post-processing (`html_tables_to_md.py`) handles only HTML tables and `<img>` tags left by pandoc; the rest of the HTML is kept as-is.

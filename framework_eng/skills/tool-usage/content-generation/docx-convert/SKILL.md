---
name: docx-convert
description: "Use this when you need to convert a Word document (.docx) to Markdown with extracted images: a client brief in .docx, a specification, instructions, article text, vendor documentation. The skill uses pandoc and post-processing (HTML tables → MD pipe tables, renaming image paths) through a local script. TRIGGERS: the user asks to \"translate docx to md / convert Word / extract text from .docx / parse a Word document / extract markdown from docx\"."
capabilities: content-generation,document-conversion
---

# Word → Markdown Conversion

A thin wrapper around `pandoc` for converting `.docx` to GitHub-Flavored Markdown with embedded images extracted. It also post-processes the result: fixes image paths and turns HTML tables (which pandoc leaves as-is for complex cases) into Markdown pipe tables.

## When to Use

| Situation | Action |
|----------|----------|
| The client sent a brief in `.docx`, and it needs to be added to the repository as md | `docx2md.sh input.docx` |
| Vendor documentation is in Word, and it needs to be fed to the agent | `docx2md.sh input.docx output_dir` |
| The document has complex tables and styles - pandoc skips them | mammoth (see below) |
| Only the text content is needed, without images | `pandoc input.docx --to=gfm -o out.md` directly |

## When Not to Use

- HTML is needed - use `pandoc --to=html`; the script is not required.
- PDF is needed - use `pandoc --to=pdf` (LaTeX is required); the script is not required.
- The document was created with WordArt/SmartArt/shapes - they are lost during conversion; this is a pandoc limitation.

## Dependencies

- `pandoc` ≥ 3.x - the main converter
- `python3` - post-processing (`html_tables_to_md.py`)
- `mammoth` (python) - an optional alternative for complex tables

## Quick Start

```bash
# Result next to the file, in a directory with the same name without the extension
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx"

# With an explicit output directory
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx" "/path/to/output"
```

Result:
- `output/document.md` - text in GFM with pipe tables
- `output/images/` - all images from the document (png/jpeg/emf/wmf)

> When used from a project where the framework is installed via symlinks, the script path is: `.claude/skills/docx-convert/docx2md.sh`.

## Manual Commands (Without the Script)

### Text Only
```bash
pandoc input.docx --from=docx --to=gfm --wrap=none -o output.md
```

### Text + Images
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

- **Converting `.doc` (legacy format)** - pandoc accepts only `.docx`. Save it again first through LibreOffice/Word.
- **Expecting formulas to be preserved** - OMML is converted to LaTeX only partially; complex formulas are better rebuilt manually.
- **Applying it to scans/PDFs** - this is not a docx-convert task; use other tools for OCR.

## Notes

- Complex Word elements (WordArt, SmartArt, shapes) are lost - this is normal for pandoc conversion.
- Embedded images are extracted correctly in png, jpeg, emf, and wmf formats.
- Post-processing (`html_tables_to_md.py`) handles only HTML tables and `<img>` tags left by pandoc; the rest of the HTML is preserved as-is.

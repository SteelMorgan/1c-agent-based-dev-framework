---
name: docx-convert
description: "For converting DOCX to Markdown with images"
capabilities: content-generation,document-conversion
---

# Word → Markdown Conversion

A thin wrapper around `pandoc` for converting `.docx` to GitHub-Flavored Markdown while extracting embedded images. It also post-processes the result: fixes image paths and converts HTML tables (which pandoc leaves unchanged for complex cases) into Markdown pipe tables.

## When to Use

| Situation | Action |
|----------|----------|
| The customer sent a spec in `.docx`, and it needs to be put into the repository as md | `docx2md.sh input.docx` |
| Vendor documentation is in Word, and it needs to be fed to the agent | `docx2md.sh input.docx output_dir` |
| A document with complex tables and styles - pandoc skips them | mammoth (see below) |
| Only the text portion is needed, without images | `pandoc input.docx --to=gfm -o out.md` directly |

## When NOT to Use

- HTML is needed - `pandoc --to=html`, the script is not required.
- PDF is needed - `pandoc --to=pdf` (LaTeX is required), the script is not required.
- The document was created with WordArt/SmartArt/shapes - they are lost during conversion; this is a pandoc limitation.

## Dependencies

- `pandoc` ≥ 3.x - the main converter
- `python3` - post-processing (`html_tables_to_md.py`)
- `mammoth` (python) - an optional alternative for complex tables

## Quick Start

```bash
# Результат рядом с файлом, в каталоге с тем же именем без расширения
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx"

# С указанием выходного каталога
bash framework/skills/tool-usage/content-generation/docx-convert/docx2md.sh "/path/to/file.docx" "/path/to/output"
```

Result:
- `output/document.md` - text in GFM with pipe tables
- `output/images/` - all images from the document (png/jpeg/emf/wmf)

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

- **Converting `.doc` (old format)** - pandoc accepts only `.docx`. Resave it first through LibreOffice/Word.
- **Expecting formulas to be preserved** - OMML is converted to LaTeX only partially; complex formulas are better rebuilt manually.
- **Applying it to scans/PDFs** - this is not a docx-convert task; use other tools for OCR.

## Notes

- Complex Word elements (WordArt, SmartArt, shapes) are lost - this is normal for pandoc conversion.
- Embedded images are extracted correctly in png, jpeg, emf, wmf formats.
- Post-processing (`html_tables_to_md.py`) handles only HTML tables and `<img>` tags left by pandoc; the rest of the HTML is kept as-is.

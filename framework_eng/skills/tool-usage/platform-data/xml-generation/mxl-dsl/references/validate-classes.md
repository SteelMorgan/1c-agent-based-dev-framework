# `mxl validate` — error classes

The `xml-gen mxl validate <Template.xml>` command checks the structural correctness of the assembled layout - the kind of thing the 1C platform will not forgive when opening it.

## Options

| Option | Default | Purpose |
|-------|-----------|------------|
| `--detailed` | off | Detailed output (including successful checks) |
| `--max-errors N` | 20 | Stop after N errors |

## Error Classes

### 1. `bad-column-index`

A cell references a column index that does not exist in the layout.

**Causes:**
- `col` is greater than `columns` in the DSL
- After compile, `span` runs beyond the right edge of the layout
- In the source XML - missing `Column` entries in the column set

**How to fix (for compile output):** check `columns` and the maximum `col + span - 1` in the DSL.

### 2. `bad-format-index`

A cell references a format (`<f>N</f>`) that does not exist in the layout format palette.

**Causes:**
- Manual XML editing after which the palette lost an entry
- Merging two layouts
- In the DSL: a typo in the style `format` (we use format names, but in XML this is the palette index - the compiler must assemble the palette)

**How to fix:** recompile from the DSL - it will build the palette from scratch.

### 3. `bad-height`

The row height is defined incorrectly: negative, zero for a non-empty row, or it references a missing height index.

**Causes:**
- In the DSL: `height: 0` or `height: -5`
- In the source XML - a corrupted height palette

### 4. `palette-refs` (palette references)

Combines integrity checks for all layout palettes:
- `font` → whether a font exists by index
- `border` → whether a border description exists
- `color` → whether a color exists
- `style` → whether a style exists

Each palette is numbered, and cells reference it by index. If the index exceeds the palette size or points to a gap, that is a `palette-refs` error.

### 5. `area-ranges` (named area ranges)

A named area is defined by a range (`top..bottom` for Rows, `left..right` for Columns, `top..bottom, left..right` for Rectangle).

**Checks:**
- `top <= bottom`, `left <= right`
- the range does not go beyond the layout boundaries (`bottom <= maxRow`, `right <= columns`)
- two same-named areas do not define conflicting ranges
- Rectangle: `columnsID` exists in the layout

### 6. `merge-ranges` (merge ranges)

Merged cells (`<merged>`) are defined by a rectangle `top..bottom, left..right`.

**Checks:**
- coordinates are valid (see above)
- merges do not overlap
- a single merge is not degenerate (`1×1`)

### 7. Other structural

In addition to the classes above, validate catches:
- missing required attributes in `Cell`
- references to non-existent column sets (`columnsID`)
- broken references to drawings (`Drawing` without binary content)
- incorrect XML namespace

## Output

### Brief (default)

```
Template.xml — 3 errors
  [palette-refs]  row 14, col 9: font index 12 out of range (palette size 8)
  [bad-column-index]  area "Строка": cell col=11 exceeds columns=10
  [merge-ranges]  row 16: merge 16:8-16:11 exceeds columns=10
```

### Detailed (`--detailed`)

```
Template.xml — validating...
  [ok]  palette/fonts: 8 entries
  [ok]  palette/borders: 4 entries
  [ok]  palette/formats: 5 entries
  [err] palette-refs: row 14, col 9: font index 12 out of range
  ...
```

## When each subagent calls validate

| Subagent | Scenario |
|----------|----------|
| `reviewer` | Part of code review for print forms - required after any layout changes |
| `tester` | Smoke check before running print tests |
| `developer-code` | Immediately after `mxl compile` before committing |
| `debugger` | When "the layout does not open" / "1C crashes on GetLayout" - validate points to specific palettes |

## Typical mapping of errors to the DSL

| Error class | What to look for in the DSL |
|--------------|------------------|
| `bad-column-index` | `col`, `span`, `columns` |
| `bad-format-index` | `format` in styles (typos in tokens `ЧДЦ=`/`ДФ=`) |
| `bad-height` | `height` in rows |
| `palette-refs` | names in `font`, references `style.font` |
| `area-ranges` | overlapping `name`, empty `rows` |
| `merge-ranges` | `span`/`rowspan` (exceeding edges, intersections of two explicit cells) |

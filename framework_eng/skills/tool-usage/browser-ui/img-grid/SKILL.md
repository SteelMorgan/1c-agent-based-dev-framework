---
name: "img-grid"
description: "Measuring the grid and columns from a printed form MXL screenshot"
argument-hint: "<ImagePath> [--cell-size 50] [--cols N] [-o OUTPUT]"
allowed-tools:
  - Bash
  - Read
---

# img-grid — Grid for layout analysis

Overlays a numbered grid on an image of a printed form.
Allows you to accurately determine column boundaries, their proportions, and spans for generating an MXL document layout.

The numbers are drawn in separate fields outside the image (top and left margins), so they never overlap the form content.

## Usage

```bash
python3 tools/img-grid/grid.py <ImagePath> [--cell-size 50] [--cols N] [--rows N] [-o OUTPUT]
```

## Parameters

| Parameter | Required | Default | Description |
|---|:---:|---|---|
| `ImagePath` | yes | — | Path to the image (PNG, JPG) |
| `--cell-size N` | no | `50` | Cell size in pixels (determines cols/rows automatically) |
| `-c`, `--cols N` | no | auto | Number of vertical divisions (overrides --cell-size) |
| `-r`, `--rows N` | no | auto | Number of horizontal divisions (0 = square cells) |
| `-o OUTPUT` | no | `<name>-grid.<ext>` | Output path |

## Dependencies

```bash
pip install Pillow
```

## What the script does

1. Adds margins (20 px top, 24 px left) for labels so the content is never covered.
2. Draws vertical lines (red) and horizontal lines (blue) on the grid.
3. Every 5th line is brighter, every 10th line is the brightest (easy to count).
4. Numbers the lines in the margins (top for column numbers, left for row numbers).
5. Saves the result as an RGB PNG.

## How to use the result

### 1. Determine column boundaries

Look at the image with the grid and record the numbers of the vertical lines that mark the boundaries of each table column.

### 2. Find the base grid

If a form has several tables with different layouts (header + main table), combine all boundary points. Each segment between neighboring boundaries is one base MXL column.

Example for form M-11 (`--cols 48`):
- Header: boundaries `0, 2, 4, 9, 14, 21, 28, 34, 40, 48`
- Table: boundaries `0, 2, 4, 11, 16, 19, 23, 28, 32, 36, 42, 48`
- Union: `0, 2, 4, 9, 11, 14, 16, 19, 21, 23, 28, 32, 34, 36, 40, 42, 48`
- Result: **16 base columns** with proportions `2, 2, 5, 2, 3, 2, 3, 2, 2, 5, 4, 2, 2, 4, 2, 6`

### 3. Record proportions

```json
{
  "columns": 16,
  "page": "A4-landscape",
  "columnWidths": {
    "1": "2x", "2": "2x", "3": "5x", "4": "2x", "5": "3x",
    "6": "2x", "7": "3x", "8": "2x", "9": "2x", "10": "5x",
    "11": "4x", "12": "2x", "13": "2x", "14": "4x", "15": "2x", "16": "6x"
  }
}
```

## Typical workflow (reverse-engineering MXL)

```bash
# 1. Take a screenshot of the form
# 2. Overlay a grid with a step of about 50px
python3 tools/img-grid/grid.py form-screenshot.png --cell-size 50 -o form-grid.png

# 3. Study the form, experiment with the number of divisions
python3 tools/img-grid/grid.py form-screenshot.png --cols 48 -o form-grid-48.png

# 4. Hand it over to an agent for analysis: name the column boundaries by number
```

## Target agents

- **developer-code** — when working with an MXL form from a screenshot
- **explorer / debugger** — when reverse-engineering unknown printed forms

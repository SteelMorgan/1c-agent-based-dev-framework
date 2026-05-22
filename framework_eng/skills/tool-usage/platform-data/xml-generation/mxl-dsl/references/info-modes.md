# `mxl info` — how to read the output

The `xml-gen mxl info <Template.xml>` command removes the need to read hundreds of XML lines. It returns a compact summary: named areas, parameters, drill-down references, column sets, static text.

## Options

| Option | Default | Purpose |
|-------|-----------|------------|
| `--format text\|json` | `text` | Output format. `json` is for programmatic processing |
| `--with-text` | off | Include static cell text and template strings |
| `--max-params N` | 10 | Maximum number of parameters listed per area |
| `--limit N` | 150 | Maximum number of output lines (protection against context overflow) |
| `--offset N` | 0 | Skip the first N lines (pagination with `--limit`) |

## Area Order

Areas are output in **document position** order (by top row), not alphabetically. This matches the natural order in which areas are emitted when generating a print form - top to bottom.

```
--- Named areas ---
  Заголовок          Rows         rows 1-4     (1 params)
  Поставщик          Rows         rows 5-6     (1 params)
  ШапкаТаблицы       Rows         rows 12-13   (0 params)
  Строка             Rows         rows 14-14   (8 params)
  Итого              Rows         rows 16-17   (1 params)
  ВысотаЭтикетки     Rows         rows 20-25
  ШиринаЭтикетки     Columns      cols 1-5
```

## Area Types

| Type | What it is | How to get it in BSL |
|-----|---------|-----|
| `Rows` | Horizontal area - a row range (`rows N-M`). The most common case is a header, table row, or footer | `Макет.ПолучитьОбласть("Имя")` |
| `Columns` | Vertical area - a column range (`cols N-M`). Used for labels and price tags, where "width" exists as a named entity | `Макет.ПолучитьОбласть("Имя")` (only in a pair with Rows through `\|`) |
| `Rectangle` | Rectangle: explicit rows + columns + its own column set (`columnsID`). Used in complex layouts with variable width | `Макет.ПолучитьОбласть("Имя")` |
| `Drawing` | Named drawing: image, barcode, QR code | Access via `Макет.Рисунки.Найти("Имя")` |

## Intersections

If the layout contains both `Rows` and `Columns` areas at the same time (a typical pattern for labels and price tags), info outputs **intersection pairs** exactly as they should be requested in BSL:

```
--- Intersections (use with GetArea) ---
  ВысотаЭтикетки|ШиринаЭтикетки
```

BSL:

```bsl
Этикетка = Макет.ПолучитьОбласть("ВысотаЭтикетки|ШиринаЭтикетки");
```

## Parameters by Area

```
--- Parameters by area ---
  Заголовок: ТекстЗаголовка
  Поставщик: ПредставлениеПоставщика
    detail: ПредставлениеПоставщика->Поставщик
  Строка: НомерСтроки, Товар, Количество, Цена, Сумма, ... (+3)
    detail: Товар->Номенклатура
  Итого: Всего
```

- The `detail: A->B` entry means: a cell with parameter `A` has a drill-down reference to parameter `B`. In BSL, fill **both**: `A` is what is displayed, `B` is the link for drilling down.
- If there are more parameters than `--max-params`, `... (+N)` is shown.

## Parameters from Templates (suffix `[tpl]`)

Parameters embedded in template text (`fillType=Template`, for example `"Инв № [ИнвентарныйНомер]"`) are marked with the `[tpl]` suffix:

```
  НумерацияЛистов: Номер [tpl], Дата [tpl], НомерЛиста [tpl]
```

In BSL, they are filled exactly like ordinary parameters:

```bsl
Область.Параметры.Номер     = НомерДокумента;
Область.Параметры.Дата      = ДатаДокумента;
Область.Параметры.НомерЛиста = Лист;
```

Numeric substitutions like `[5]`, `[6]` (used in official forms as footnote references) are **ignored** - they are not parameters.

## Text Content (`--with-text`)

The option shows static text and template strings. Useful for understanding column purpose and finding the needed parameter.

```
--- Text content ---
  ШапкаТаблицы:
    Text: "№", "Товар", "Ед. изм.", "Кол-во", "Цена", "Сумма"
  Строка:
    Templates: "Инв № [ИнвентарныйНомер]"
  Итого:
    Text: "Итого:"
```

| Label | Meaning |
|-------|------------|
| `Text` | Static captions - `fillType=Text` |
| `Templates` | Text with `[Name]` substitutions - `fillType=Template` |

## Pagination

For large layouts:

```bash
xml-gen mxl info Template.xml --limit 150           # first 150 lines
xml-gen mxl info Template.xml --offset 150 --limit 150  # next 150
```

## JSON Output

```bash
xml-gen mxl info Template.xml --format json
```

Structure:

```json
{
  "areas": [
    { "name": "Заголовок", "type": "Rows", "rows": [1, 4], "params": ["ТекстЗаголовка"] },
    { "name": "Строка", "type": "Rows", "rows": [14, 14],
      "params": ["НомерСтроки", "Товар", ...],
      "detail": { "Товар": "Номенклатура" } }
  ],
  "intersections": ["ВысотаЭтикетки|ШиринаЭтикетки"],
  "drawings": []
}
```

Used by agents in reverse-engineering pipelines and when inventorying configuration layouts.

## Typical Usage Scenarios

| Subagent | Why info |
|----------|-----------|
| `developer-code` | Before filling a layout in BSL - find parameter names and detail |
| `debugger` | When a form does not print - check which areas actually exist and what they are called |
| `explorer` | Print form map by processing object (quick audit) |
| `analyst` | Understand what data is needed for printing |
| `architect` | Inventory configuration templates |

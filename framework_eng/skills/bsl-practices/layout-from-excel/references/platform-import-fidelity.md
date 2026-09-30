# What survives platform import `Excel -> ТабличныйДокумент`

The summary was reconstructed from a verified runtime experiment on platform
`8.3.27` (Linux x86-64): one `.xlsx` sheet was read with `ТабличныйДокумент.Прочитать`,
the result was written back to `MXL` and `XLSX`, and compared with the source.
The original fidelity report for the task was lost; the figures below are actual measurements from that
run, included here as a reference rather than a guarantee for every file.

## Measured scope

- Input: `.xlsx`, one sheet, grid `A1:H13`, 5 merged areas, 53 non-formula
  values, fills, fonts (bold/italic), borders, number formats,
  freeze panes, auto filter, print area.
- Route: `ТабличныйДокумент.Прочитать(xlsx)` -> `Записать(mxl)` /
  `Записать(xlsx)` -> structural comparison of input and output.

## Preserved (confirmed by measurement)

| Property | Fact |
|---|---|
| Row/column grid | `A1:H13` preserved (`dimension=H13`) |
| Merged areas | all 5 areas remain in place |
| Non-formula values | all 53 values are present at their coordinates |
| Fills, fonts, alignment | displayed fills/fonts/alignment transferred |
| Borders | visual table border transferred |

## Lost or distorted

| Property | What happens |
|---|---|
| Column widths | do not match: Excel and MXL use different width scales; reset in code with a coefficient of ~`0.864` (calibrated for the specific template) |
| Row heights | fractional heights are distorted; reset using `АвтоВысотаСтроки = Ложь` + `ВысотаСтроки` |
| Border colors | some border colors do not survive import |
| Number formats | some number formats are simplified; reset with `Область(...).Формат` |
| Formulas | become the last calculated values — totals must be calculated in code |
| `freeze panes` | lost |
| `auto filter` | lost |
| `print area` | lost |
| Sheet name | may change |

## Client limitation

`ТабличныйДокумент.Прочитать` worked in this route only on the **thick client**: the thin client returned `Метод недоступен на тонком клиенте` when opening a form with client-side import. When selecting a runtime route, explicitly record the client type in the proof — a false positive `run_scenario=Success` does not prove import if the form displayed `ErrorWindow`.

## Practical conclusion

Imported MXL is a visual and structural framework, not an exact copy of the workbook.
The following are built on top of it in code:

- data binding to coordinates (`Область(R,C).Значение/Текст`);
- recalculation of totals (there are no formulas — only values);
- geometry: `ВосстановитьГеометриюExcel` from `platform-import-roundtrip.bsl`;
- service labels and print mode (`ОтображатьСетку = Ложь`).

The input and round-trip result are compared using the `compare_xlsx_fidelity.py` script from this same directory.

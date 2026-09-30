#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Сравнение fidelity исходного .xlsx и результата round-trip через
ТабличныйДокумент.Прочитать/Записать.

ВОССТАНОВЛЕН по описанию fidelity-матрицы в proof-материалах: оригинальный
скрипт задачи не сохранился в истории git (файл жил только на диске и был
удалён финальной очисткой артефактов). Поведение ниже воспроизводит
проверенные категории сравнения: сетка, merge-области, неформульные значения,
ширины/высоты, числовые форматы, формулы, freeze panes, auto filter,
print area, наличие заливок/шрифтов/выравнивания/границ.

Использование:
    python3 compare_xlsx_fidelity.py source.xlsx roundtrip.xlsx [--json out.json]

Зависимость: openpyxl.
"""

import argparse
import json
import sys

from openpyxl import load_workbook
from openpyxl.utils import get_column_letter


def sheet_features(ws):
    """Снимок сравниваемых свойств одного листа."""
    merges = sorted(str(rng) for rng in ws.merged_cells.ranges)

    values = {}
    formulas = {}
    number_formats = {}
    styled_cells = 0
    for row in ws.iter_rows():
        for cell in row:
            if cell.value is None and not cell.has_style:
                continue
            coord = cell.coordinate
            if isinstance(cell.value, str) and cell.value.startswith("="):
                formulas[coord] = cell.value
            elif cell.value is not None:
                values[coord] = cell.value
            if cell.number_format and cell.number_format != "General":
                number_formats[coord] = cell.number_format
            if cell.has_style:
                styled_cells += 1

    col_widths = {
        letter: dim.width
        for letter, dim in ws.column_dimensions.items()
        if dim.width is not None
    }
    row_heights = {
        idx: dim.height for idx, dim in ws.row_dimensions.items() if dim.height is not None
    }

    freeze = ws.freeze_panes
    auto_filter = ws.auto_filter.ref if ws.auto_filter is not None else None
    print_area = ws.print_area

    return {
        "dimension": ws.calculate_dimension(),
        "merges": merges,
        "values": values,
        "formulas": formulas,
        "number_formats": number_formats,
        "styled_cells": styled_cells,
        "col_widths": col_widths,
        "row_heights": row_heights,
        "freeze_panes": freeze,
        "auto_filter": auto_filter,
        "print_area": print_area,
    }


def compare(source_path, result_path):
    src_wb = load_workbook(source_path)
    res_wb = load_workbook(result_path)

    report = {"source": source_path, "result": result_path, "categories": {}}

    src_sheets = src_wb.sheetnames
    res_sheets = res_wb.sheetnames
    report["categories"]["sheet_names"] = {
        "source": src_sheets,
        "result": res_sheets,
        "preserved": src_sheets == res_sheets,
    }

    src_ws = src_wb[src_sheets[0]]
    res_ws = res_wb[res_sheets[0]]
    src = sheet_features(src_ws)
    res = sheet_features(res_ws)

    report["categories"]["dimension"] = {
        "source": src["dimension"],
        "result": res["dimension"],
        "preserved": src["dimension"] == res["dimension"],
    }

    src_merges = set(src["merges"])
    res_merges = set(res["merges"])
    report["categories"]["merges"] = {
        "source_count": len(src_merges),
        "result_count": len(res_merges),
        "missing": sorted(src_merges - res_merges),
        "added": sorted(res_merges - src_merges),
        "preserved": src_merges <= res_merges,
    }

    value_diffs = {}
    for coord, val in src["values"].items():
        res_val = res["values"].get(coord)
        if res_val is None and coord in res["formulas"]:
            res_val = res["formulas"][coord]
        if res_val != val:
            value_diffs[coord] = {"source": val, "result": res_val}
    report["categories"]["values"] = {
        "source_count": len(src["values"]),
        "result_count": len(res["values"]),
        "diffs": value_diffs,
        "diff_count": len(value_diffs),
        "preserved": not value_diffs,
    }

    report["categories"]["formulas"] = {
        "source_count": len(src["formulas"]),
        "result_count": len(res["formulas"]),
        "note": "платформенный импорт заменяет формулы вычисленными значениями",
        "preserved": len(src["formulas"]) == len(res["formulas"]),
    }

    width_diffs = {}
    for letter, width in src["col_widths"].items():
        res_width = res["col_widths"].get(letter)
        if res_width is None or abs(res_width - width) > max(0.5, width * 0.1):
            width_diffs[letter] = {"source": width, "result": res_width}
    report["categories"]["col_widths"] = {
        "diffs": width_diffs,
        "diff_count": len(width_diffs),
        "note": "шкалы ширины Excel и MXL различаются; восстановление — кодом (~x0.864)",
        "preserved": not width_diffs,
    }

    height_diffs = {}
    for idx, height in src["row_heights"].items():
        res_height = res["row_heights"].get(idx)
        if res_height is None or abs(res_height - height) > max(0.5, height * 0.1):
            height_diffs[str(idx)] = {"source": height, "result": res_height}
    report["categories"]["row_heights"] = {
        "diffs": height_diffs,
        "diff_count": len(height_diffs),
        "preserved": not height_diffs,
    }

    fmt_diffs = {}
    for coord, fmt in src["number_formats"].items():
        res_fmt = res["number_formats"].get(coord)
        if res_fmt != fmt:
            fmt_diffs[coord] = {"source": fmt, "result": res_fmt}
    report["categories"]["number_formats"] = {
        "diffs": fmt_diffs,
        "diff_count": len(fmt_diffs),
        "preserved": not fmt_diffs,
    }

    report["categories"]["styled_cells"] = {
        "source_count": src["styled_cells"],
        "result_count": res["styled_cells"],
        "note": "наличие fills/fonts/alignment/borders по ячейкам",
    }

    for key, label in (
        ("freeze_panes", "freeze panes"),
        ("auto_filter", "auto filter"),
        ("print_area", "print area"),
    ):
        report["categories"][key] = {
            "source": src[key],
            "result": res[key],
            "label": label,
            "preserved": src[key] == res[key],
        }

    return report


def print_summary(report):
    print("Fidelity compare: %s -> %s" % (report["source"], report["result"]))
    for name, cat in report["categories"].items():
        if "preserved" in cat:
            mark = "PRESERVED" if cat["preserved"] else "CHANGED/LOST"
        else:
            mark = "INFO"
        line = "  %-16s %s" % (name, mark)
        if "source_count" in cat:
            line += " (src=%s res=%s)" % (cat["source_count"], cat["result_count"])
        if cat.get("diff_count"):
            line += " diffs=%s" % cat["diff_count"]
        print(line)
        if "note" in cat:
            print("      note: %s" % cat["note"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", help="исходный .xlsx")
    parser.add_argument("result", help="xlsx, записанный после импорта в ТабличныйДокумент")
    parser.add_argument("--json", dest="json_out", help="путь для JSON-отчёта")
    args = parser.parse_args()

    report = compare(args.source, args.result)
    print_summary(report)

    if args.json_out:
        with open(args.json_out, "w", encoding="utf-8") as f:
            json.dump(report, f, ensure_ascii=False, indent=2, default=str)
        print("JSON written: %s" % args.json_out)

    changed = [
        name
        for name, cat in report["categories"].items()
        if cat.get("preserved") is False
    ]
    if changed:
        print("Changed/lost categories: %s" % ", ".join(changed))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())

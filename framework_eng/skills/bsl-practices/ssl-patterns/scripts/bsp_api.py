#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""bsp_api.py — lookup methods/modules in a 1C configuration export.

Three commands, all require --src <path> (root containing CommonModules/):
  method <Имя>     — module + full signature + region name + doc-comment + path
  module <Имя>      — all export methods of one module (any region) with signatures
  modules           — modules with stable API (region == ПрограммныйИнтерфейс + export)

Region is reported by its real #Область name (ПрограммныйИнтерфейс /
СлужебныйПрограммныйИнтерфейс / СлужебныеПроцедурыИФункции /
УстаревшиеПроцедурыИФункции / Переопределение). The agent classifies stability
from the region name; the script does not collapse it into a tag.
"""

import argparse
import re
import sys
from pathlib import Path

# Force UTF-8 on Windows console (Cyrillic otherwise mojibakes).
for _stream in (sys.stdout, sys.stderr):
    _reconf = getattr(_stream, "reconfigure", None)
    if _reconf is not None:
        try:
            _reconf(encoding="utf-8")
        except (TypeError, ValueError):
            pass

# Region names that mark an export method's stability tier.
STABLE_REGION = "ПрограммныйИнтерфейс"
SERV_REG = ("СлужебныйПрограммныйИнтерфейс", "СлужебныеПроцедурыИФункции")
DEPRECATED_REGION = "УстаревшиеПроцедурыИФункции"
# Real override-flavored regions in BSP 3.1.11 (the plan's "#Область
# Переопределение" does not exist). Most override hooks live in *Переопределяемый
# modules under ПрограммныйИнтерфейс — detected by module name, see below.
OVERRIDE_REGIONS = ("ПереопределениеВызовов", "ПереопределениеТекстаЗапросаНабораДанных")
OVERRIDE_MODULE_SUFFIX = "Переопределяемый"

DOC_MAX_LINES = 15  # truncate doc-comment to this many lines if no // Пример:


def find_src_or_exit(src_arg):
    """Return validated --src path. No auto-detect: caller must pass --src."""
    if not src_arg:
        print("Error: --src <path> is required (path to configuration export root).",
              file=sys.stderr)
        sys.exit(2)
    if not Path(src_arg).is_dir():
        print(f"Error: --src path does not exist: {src_arg}", file=sys.stderr)
        sys.exit(1)
    cm = Path(src_arg) / "CommonModules"
    if not cm.is_dir():
        print(f"Error: --src path has no CommonModules/ subdir: {src_arg}",
              file=sys.stderr)
        sys.exit(1)
    return src_arg


def list_common_modules(src):
    """All common modules in the export (no prefix filter). Returns (name, bsl)."""
    cm_dir = Path(src) / "CommonModules"
    result = []
    for entry in sorted(cm_dir.iterdir()):
        if entry.is_dir():
            bsl = entry / "Ext" / "Module.bsl"
            if bsl.is_file():
                result.append((entry.name, bsl))
    return result


def _region_for_stack_name(name):
    """Map a #Область name to the stability region name we track, or None.

    Only the meaningful regions are tracked by name; any other region is a
    plain grouping whose methods inherit the parent region (handled by caller
    pushing stack[-1] for unknown names)."""
    if name in (STABLE_REGION, *SERV_REG, DEPRECATED_REGION, *OVERRIDE_REGIONS):
        return name
    return None


def _is_override_module(mod_name):
    """*Переопределяемый modules hold override hooks (БСП calls, прикладной
    code реализует) under #Область ПрограммныйИнтерфейс — detected by suffix,
    since the plan's #Область Переопределение does not exist in BSP 3.1.11."""
    return mod_name.endswith(OVERRIDE_MODULE_SUFFIX)


def parse_export_methods(bsl_path):
    """Parse .bsl, return list of (method_name, region_name, sig_text, doc_lines).

    Two passes:
      1. Compute region name per line with a stack; nested sub-regions inherit
         the parent's tracked region name.
      2. Scan Функция/Процедура declarations, accumulate the multi-line signature
         until Экспорт (export) or Конец… (non-export), and collect the doc
         block of `//` lines directly above the declaration.
    """
    text = bsl_path.read_text(encoding="utf-8-sig")
    lines = text.splitlines()
    n = len(lines)

    # Pass 1: region name per line.
    region_names = [None] * n
    stack = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("#Область"):
            name = stripped.replace("#Область", "").strip()
            tracked = _region_for_stack_name(name)
            # Unknown region name: inherit parent's tracked region (or None).
            stack.append(tracked if tracked is not None else (stack[-1] if stack else None))
            region_names[i] = None
        elif stripped.startswith("#КонецОбласти"):
            if stack:
                stack.pop()
            region_names[i] = None
        else:
            region_names[i] = stack[-1] if stack else None

    # Pass 2: export methods + signatures + doc.
    methods = []
    i = 0
    while i < n:
        stripped = lines[i].strip()
        if stripped.startswith("#") or not stripped:
            i += 1
            continue
        m = re.match(r"^(Функция|Процедура)\s+(\w+)\s*\(", stripped)
        if not m:
            i += 1
            continue
        keyword = m.group(1)
        method_name = m.group(2)
        end_keyword = "Конец" + keyword
        region = region_names[i]

        sig_text = stripped
        j = i
        is_export = "Экспорт" in stripped
        if not is_export:
            for j2 in range(i + 1, min(i + 30, n)):
                s2 = lines[j2].strip()
                if s2 == end_keyword or s2.startswith(end_keyword):
                    j = j2
                    break
                m2 = re.match(r"^(Функция|Процедура)\s+\w+\s*\(", s2)
                if m2:  # a new declaration started -> previous was non-export
                    is_export = False
                    j = j2 - 1
                    break
                sig_text += "\n" + s2
                if "Экспорт" in s2:
                    is_export = True
                    j = j2
                    break
                j = j2
        if is_export:
            doc_lines = _collect_doc(lines, i)
            methods.append((method_name, region, sig_text, doc_lines))
        i = j + 1
    return methods


def _collect_doc(lines, decl_idx):
    """Collect `//` doc lines directly above declaration decl_idx.

    Skip blank lines between the doc block and the declaration. Stop at the
    first non-`//` line above the block. Then truncate: output lines up to (but
    excluding) the first `// Пример:` line, or the first DOC_MAX_LINES lines —
    whichever is shorter. Skip a leading copyright header if present.
    """
    doc = []
    k = decl_idx - 1
    # Skip blank lines between doc and declaration.
    while k >= 0 and not lines[k].strip():
        k -= 1
    while k >= 0:
        s = lines[k]
        if not s.strip().startswith("//"):
            break
        doc.append(s.rstrip("\n"))
        k -= 1
    doc.reverse()
    # Drop a copyright header if it led the block.
    while doc and ("Copyright" in doc[0] or doc[0].strip().startswith("// //")
                   or set(doc[0].strip()) <= set("/= ")):
        doc.pop(0)
    # Truncate before the // Пример: section, or to DOC_MAX_LINES lines.
    out = []
    for line in doc:
        if line.strip().startswith("// Пример:"):
            break
        out.append(line)
        if len(out) >= DOC_MAX_LINES:
            break
    return out


def _stability_warning(mod_name, region):
    """Warning string (if any) for an export method, combining region and the
    *Переопределяемый module signal."""
    if region in OVERRIDE_REGIONS or _is_override_module(mod_name):
        who = ("модуль *Переопределяемый" if _is_override_module(mod_name)
               else f"регион #Область {region}")
        return (f"⚠️ Хук переопределения ({who}).\n"
                "БСП вызывает этот метод, прикладной код РЕАЛИЗУЕТ его "
                "(копирует модуль-переопределитель в конфигурацию и\n"
                "переопределяет тело). НЕ вызывается из прикладного кода напрямую.")
    if region == DEPRECATED_REGION:
        return "⚠️ Region: УстаревшиеПроцедурыИФункции (deprecated) — не использовать в новом коде."
    if region in SERV_REG:
        return "⚠️ Region: служебный — обратная совместимость не гарантируется."
    return None


def _print_method(mod_name, bsl_path, region, sig_text, doc_lines):
    print(f"Module: {mod_name}")
    print(f"Region: #Область {region}" if region else "Region: (вне отслеживаемых областей)")
    print(f"Path:   {bsl_path}")
    print("\nSignature:")
    print(sig_text)
    warn = _stability_warning(mod_name, region)
    if warn:
        print("\n" + warn)
    if doc_lines:
        print(f"\nDoc-comment (до // Пример: / перв. {DOC_MAX_LINES} строк):")
        print("\n".join(doc_lines))


def _collect_matches(src, target):
    """All modules defining export method `target`. Returns list of dicts."""
    target = target.lower()
    matches = []
    for mod_name, bsl_path in list_common_modules(src):
        for method_name, region, sig_text, doc_lines in parse_export_methods(bsl_path):
            if method_name.lower() == target:
                matches.append({
                    "mod": mod_name, "bsl": bsl_path, "region": region,
                    "sig": sig_text, "doc": doc_lines,
                })
    return matches


def cmd_method(args):
    src = find_src_or_exit(args.src)
    target = args.name

    # --module pins the search to one module (disambiguation when a method
    # exists in several modules with different signatures/regions).
    if args.module:
        mod_target = args.module.lower()
        for mod_name, bsl_path in list_common_modules(src):
            if mod_name.lower() != mod_target:
                continue
            for method_name, region, sig_text, doc_lines in parse_export_methods(bsl_path):
                if method_name.lower() == target.lower():
                    _print_method(mod_name, bsl_path, region, sig_text, doc_lines)
                    return
        print(f"Method '{target}' not found in module '{args.module}' at {src}.")
        return

    matches = _collect_matches(src, target)
    if not matches:
        print(f"Method '{target}' not found in CommonModules at {src}.")
        return

    # Prefer stable (ПрограммныйИнтерфейс) definitions; fall back to any.
    stable = [m for m in matches if m["region"] == STABLE_REGION]
    candidates = stable if stable else matches

    def _norm_sig(s):
        return re.sub(r"\s+", " ", s).strip()

    if len(candidates) == 1:
        m = candidates[0]
        _print_method(m["mod"], m["bsl"], m["region"], m["sig"], m["doc"])
        return

    # Several candidates. If they all share the same signature they are just
    # context variants (e.g. server ОбщегоНазначения vs client
    # ОбщегоНазначенияКлиент) — pick the shortest-named (the base module) and
    # note the rest. If signatures differ, that is a real collision (different
    # API) — list them and require --module to avoid picking the wrong one.
    distinct_sigs = {_norm_sig(m["sig"]) for m in candidates}
    if len(distinct_sigs) == 1:
        chosen = min(candidates, key=lambda m: len(m["mod"]))
        others = [m["mod"] for m in candidates if m["mod"] != chosen["mod"]]
        _print_method(chosen["mod"], chosen["bsl"], chosen["region"],
                      chosen["sig"], chosen["doc"])
        if others:
            print(f"\nТакже определён в (вариант контекста исполнения): {', '.join(others)}")
        return

    label = "stable" if stable else "export"
    print(f"Method '{target}' found in {len(candidates)} {label} modules with "
          f"different signatures. Pass --module <ИмяМодуля> for the full detail:")
    for m in candidates:
        sig_one = m["sig"].replace("\n", " ")
        region = m["region"] or "—"
        print(f"  [{region}] {m['mod']}.{target}  ->  {sig_one}")


def cmd_module(args):
    src = find_src_or_exit(args.src)
    target = args.name.lower()
    for mod_name, bsl_path in list_common_modules(src):
        if mod_name.lower() == target:
            methods = parse_export_methods(bsl_path)
            print(f"Module: {mod_name}")
            print(f"Path:   {bsl_path}")
            if _is_override_module(mod_name):
                print("\n⚠️ Модуль *Переопределяемый: методы ниже — хуки переопределения "
                      "(БСП вызывает, прикладной код реализует, не вызывается напрямую).")
            if not methods:
                print("No export methods found.")
                return
            print(f"\nExport methods ({len(methods)}):")
            for method_name, region, sig_text, _doc in methods:
                tag = region or "—"
                # Collapse multi-line signature to one line for the listing.
                sig_one = sig_text.replace("\n", " ")
                print(f"  [{tag}] {sig_one}")
            return
    print(f"Module '{args.name}' not found in CommonModules at {src}.")


def cmd_modules(args):
    src = find_src_or_exit(args.src)
    stable = []
    for mod_name, bsl_path in list_common_modules(src):
        methods = parse_export_methods(bsl_path)
        if any(region == STABLE_REGION for _name, region, _sig, _doc in methods):
            stable.append(mod_name)
    if stable:
        print(f"Modules with stable API (region #Область {STABLE_REGION} + export): {len(stable)}")
        for name in stable:
            print(f"  {name}")
    else:
        print(f"No modules with stable API found at {src}.")


def main():
    parent = argparse.ArgumentParser(add_help=False)
    parent.add_argument("--src", required=True,
                        help="Path to configuration export root (must contain CommonModules/)")

    parser = argparse.ArgumentParser(description="BSP module/method search")
    sub = parser.add_subparsers(dest="command")

    p_method = sub.add_parser("method", parents=[parent],
                              help="Find module + signature + region + doc for an export method")
    p_method.add_argument("name", help="Method name (e.g. СообщитьПользователю)")
    p_method.add_argument("--module", default=None,
                          help="Pin to a specific module when a method exists in several")

    p_module = sub.add_parser("module", parents=[parent],
                              help="All export methods of a module with signatures + region")
    p_module.add_argument("name", help="Module name (e.g. ОбщегоНазначения)")

    sub.add_parser("modules", parents=[parent],
                   help="List modules with stable API (ПрограммныйИнтерфейс + export)")

    args = parser.parse_args()
    if args.command == "method":
        cmd_method(args)
    elif args.command == "module":
        cmd_module(args)
    elif args.command == "modules":
        cmd_modules(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
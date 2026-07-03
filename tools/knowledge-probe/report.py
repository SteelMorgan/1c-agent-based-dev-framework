#!/usr/bin/env python3
"""
report.py — сводные отчёты из results/grades.jsonl.

Строит:
  matrix.csv   — вопрос × модель -> агрегатный вердикт (knows/partial/...).
  summary.md   — по категориям и файлам-источникам: % knows по каждой модели,
                 топ галлюцинаций, вопросы «Opus знает, Haiku нет».
  per-file.md  — для каждого source_file: вердикт по каждой модели и
                 интерпретация «знание в весах избыточно / нужно оставить в навыке».

Логика интерпретации (см. METHODOLOGY.md):
  - решение принимается по ХУДШЕЙ модели-исполнителю, которой навык реально грузится;
  - «knows» + expected_in_weights=«да» → знание в весах → в навыке может быть избыточно;
  - «knows_not»/«hallucinates» → знание НЕ в весах → навык нужен (страхует от выдумки);
  - «Opus знает, Haiku нет» → знание оставить для Haiku-ролей.

Пример:
  python3 report.py --grades results/grades.jsonl --out results/
"""
import argparse
import csv
import json
import os
import sys

AGG_ORDER = ["knows", "partial", "unstable", "knows_not", "hallucinates"]
# порядок «худшести» для выбора worst-case (лучшее -> худшее)
WORST_ORDER = ["knows", "partial", "unstable", "knows_not", "hallucinates"]


def load_grades(path):
    rows = []
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                continue
    return rows


def pct(part, whole):
    return f"{100.0 * part / whole:.0f}%" if whole else "-"


def build_matrix(rows, models, questions, out_dir):
    grid = {}
    for r in rows:
        grid[(r["id"], r["model"])] = r.get("aggregate", "")
    path = os.path.join(out_dir, "matrix.csv")
    with open(path, "w", encoding="utf-8", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["question_id", "category", "source_file", "tier",
                    "expected_in_weights"] + models)
        for qid in questions:
            meta = questions[qid]
            w.writerow([
                qid, meta.get("category", ""), meta.get("source_file", ""),
                meta.get("tier", ""), meta.get("expected_in_weights", ""),
            ] + [grid.get((qid, m), "") for m in models])
    return path


def counts_by(rows, key_fn, models):
    """{(key, model): {aggregate: n}}."""
    acc = {}
    for r in rows:
        key = key_fn(r)
        m = r["model"]
        agg = r.get("aggregate", "unstable")
        acc.setdefault((key, m), {}).setdefault(agg, 0)
        acc[(key, m)][agg] += 1
    return acc


def knows_pct_table(rows, key_fn, key_name, models):
    """Markdown-таблица: key × model -> % knows (и n)."""
    acc = counts_by(rows, key_fn, models)
    keys = sorted({k for (k, _m) in acc})
    lines = [f"| {key_name} | " + " | ".join(models) + " |",
             "|" + "---|" * (len(models) + 1)]
    for key in keys:
        cells = []
        for m in models:
            c = acc.get((key, m), {})
            tot = sum(c.values())
            cells.append(f"{pct(c.get('knows', 0), tot)} ({tot})" if tot else "-")
        lines.append(f"| {key or '(нет)'} | " + " | ".join(cells) + " |")
    return "\n".join(lines)


def build_summary(rows, models, out_dir):
    lines = ["# Knowledge Probe — сводка\n"]
    lines.append(f"Оценённых пар (вопрос×модель): {len(rows)}. "
                 f"Модели: {', '.join(models)}.\n")

    # % knows по всем вопросам на модель
    lines.append("## Доля knows по каждой модели (все вопросы)\n")
    per_model = {}
    for r in rows:
        per_model.setdefault(r["model"], {}).setdefault(r.get("aggregate"), 0)
        per_model[r["model"]][r.get("aggregate")] += 1
    lines.append("| модель | knows | partial | knows_not | hallucinates | unstable | % knows |")
    lines.append("|---|---|---|---|---|---|---|")
    for m in models:
        c = per_model.get(m, {})
        tot = sum(c.values())
        lines.append(
            f"| {m} | {c.get('knows',0)} | {c.get('partial',0)} | "
            f"{c.get('knows_not',0)} | {c.get('hallucinates',0)} | "
            f"{c.get('unstable',0)} | {pct(c.get('knows',0), tot)} |"
        )

    lines.append("\n## % knows по категориям\n")
    lines.append(knows_pct_table(rows, lambda r: r.get("category"), "категория", models))

    lines.append("\n## % knows по файлам-источникам\n")
    lines.append(knows_pct_table(rows, lambda r: r.get("source_file"), "source_file", models))

    # Топ галлюцинаций
    lines.append("\n## Топ галлюцинаций (aggregate=hallucinates)\n")
    halluc = [r for r in rows if r.get("aggregate") == "hallucinates"]
    if halluc:
        lines.append("| question_id | модель | source_file | причина |")
        lines.append("|---|---|---|---|")
        for r in halluc:
            reason = (r.get("aggregate_reason") or "").replace("\n", " ")[:80]
            lines.append(f"| {r['id']} | {r['model']} | {r.get('source_file','')} | {reason} |")
    else:
        lines.append("_Галлюцинаций (устойчивых) не зафиксировано._")

    # Opus знает, Haiku нет
    lines.append("\n## Вопросы: Opus знает, а Haiku — нет\n")
    if "opus" in models and "haiku" in models:
        by_q = {}
        for r in rows:
            by_q.setdefault(r["id"], {})[r["model"]] = r.get("aggregate")
        found = []
        for qid, mm in by_q.items():
            if mm.get("opus") == "knows" and mm.get("haiku") in ("knows_not", "hallucinates", "unstable", "partial"):
                found.append((qid, mm.get("haiku")))
        if found:
            lines.append("| question_id | вердикт Haiku |")
            lines.append("|---|---|")
            for qid, hv in sorted(found):
                lines.append(f"| {qid} | {hv} |")
            lines.append("\n> Такое знание НУЖНО оставить в навыке для Haiku-ролей "
                         "(worst-case исполнитель не знает).")
        else:
            lines.append("_Не найдено (или не хватает данных opus/haiku)._")
    else:
        lines.append("_Нужны обе модели opus и haiku в прогоне._")

    path = os.path.join(out_dir, "summary.md")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return path


def worst_verdict(verdicts):
    """Худший (самый «незнающий») вердикт из набора по правилу WORST_ORDER."""
    present = [v for v in WORST_ORDER if v in verdicts]
    return present[-1] if present else "unstable"


def build_per_file(rows, models, out_dir):
    acc = counts_by(rows, lambda r: r.get("source_file"), models)
    # expected_in_weights по файлу (берём модальное значение из строк)
    exp_by_file = {}
    for r in rows:
        f = r.get("source_file")
        exp_by_file.setdefault(f, {}).setdefault(r.get("expected_in_weights"), 0)
        exp_by_file[f][r.get("expected_in_weights")] += 1

    files = sorted({k for (k, _m) in acc})
    lines = ["# Knowledge Probe — вердикт по файлам-источникам\n"]
    lines.append("Для каждого файла: доминирующий вердикт по каждой модели и "
                 "рекомендация. Решение — по ХУДШЕЙ модели-исполнителю.\n")
    for f in files:
        exp = exp_by_file.get(f, {})
        exp_val = max(exp, key=exp.get) if exp else "-"
        lines.append(f"\n## {f or '(нет)'}")
        lines.append(f"expected_in_weights (ожидание): **{exp_val}**\n")
        lines.append("| модель | knows | partial | knows_not | halluc | unstable | доминирующий |")
        lines.append("|---|---|---|---|---|---|---|")
        model_dom = {}
        for m in models:
            c = acc.get((f, m), {})
            tot = sum(c.values())
            if not tot:
                lines.append(f"| {m} | - | - | - | - | - | нет данных |")
                continue
            dom = max(c, key=c.get)
            model_dom[m] = dom
            lines.append(
                f"| {m} | {c.get('knows',0)} | {c.get('partial',0)} | "
                f"{c.get('knows_not',0)} | {c.get('hallucinates',0)} | "
                f"{c.get('unstable',0)} | **{dom}** |"
            )
        # интерпретация по худшей модели
        if model_dom:
            worst = worst_verdict(list(model_dom.values()))
            if worst == "knows":
                rec = ("ВСЕ модели (включая худшую) знают это — знание в весах, "
                       "в навыке может быть ИЗБЫТОЧНО (кандидат на сжатие).")
            elif worst in ("knows_not", "unstable"):
                rec = ("Худшая модель НЕ знает / нестабильна — знание НУЖНО оставить "
                       "в навыке (страхует worst-case исполнителя).")
            elif worst == "hallucinates":
                rec = ("Худшая модель ГАЛЛЮЦИНИРУЕТ — навык КРИТИЧЕСКИ нужен "
                       "(без него будет уверенная ложь).")
            else:  # partial
                rec = ("Худшая модель знает лишь частично — знание полезно оставить "
                       "(уточняет/дополняет частичное знание в весах).")
            lines.append(f"\n**Вывод (worst-case):** {rec}")
    path = os.path.join(out_dir, "per-file.md")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")
    return path


def main(argv=None):
    ap = argparse.ArgumentParser(description="Отчёты из результатов оценки")
    ap.add_argument("--grades", default="results/grades.jsonl")
    ap.add_argument("--out", default="results")
    args = ap.parse_args(argv)

    if not os.path.exists(args.grades):
        print(f"Нет файла оценок: {args.grades}", file=sys.stderr)
        return 2
    rows = load_grades(args.grades)
    if not rows:
        print("Файл оценок пуст.", file=sys.stderr)
        return 2

    models = sorted({r["model"] for r in rows})
    questions = {}
    for r in rows:
        questions.setdefault(r["id"], {
            "category": r.get("category"),
            "source_file": r.get("source_file"),
            "tier": r.get("tier"),
            "expected_in_weights": r.get("expected_in_weights"),
        })

    os.makedirs(args.out, exist_ok=True)
    p1 = build_matrix(rows, models, questions, args.out)
    p2 = build_summary(rows, models, args.out)
    p3 = build_per_file(rows, models, args.out)
    print("Готово:")
    for p in (p1, p2, p3):
        print(f"  {p}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

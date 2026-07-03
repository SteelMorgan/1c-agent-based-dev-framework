#!/usr/bin/env python3
"""
grader.py — семантическая оценка ответов относительно эталона.

Для каждой пары (вопрос, модель) собирает все успешные ответы (2 или 3 —
адаптивные повторы probe.py), и вызывает грейдер (config.GRADER_MODEL —
GPT-5.5 с высоким усилием рассуждения, через адаптер из реестра, с отдельным
системным промптом config.GRADER_SYSTEM). Грейдер оценивает каждый ответ
(correct|partial|incorrect|unknown_admitted|hallucination) и выдаёт агрегат
по вопросу (knows|partial|knows_not|hallucinates|unstable).

Грейдер ОБЯЗАН вернуть строгий JSON; при невалидном ответе делаем 1 retry
с ужесточённой инструкцией, затем помечаем вердикт как "unstable"/parse_error.

Вход:  results/answers.jsonl (от probe.py) + банк вопросов (--questions, для
       reference_answer / grading_notes).
Выход: results/grades.jsonl — по строке на (id, model).

Примеры:
  python3 grader.py --questions samples.jsonl --answers results/answers.jsonl
  python3 grader.py --questions 'q/*.jsonl' --models haiku,opus --resume
"""
import argparse
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import config  # noqa: E402
from adapters import AdapterError, get_adapter  # noqa: E402

GRADES_FILE = "grades.jsonl"
VALID_VERDICTS = {"correct", "partial", "incorrect", "unknown_admitted", "hallucination"}
VALID_AGG = {"knows", "partial", "knows_not", "hallucinates", "unstable"}


def load_questions(patterns):
    idx = {}
    for pat in patterns:
        for p in pat.split(","):
            p = p.strip()
            if not p:
                continue
            for f in sorted(glob.glob(p)):
                with open(f, "r", encoding="utf-8") as fh:
                    for line in fh:
                        line = line.strip()
                        if not line or line.startswith("#"):
                            continue
                        try:
                            q = json.loads(line)
                        except json.JSONDecodeError:
                            continue
                        if "id" in q:
                            idx[q["id"]] = q
    return idx


def load_answers(path):
    """Группировка успешных ответов: {(id, model): [answer, ...]} (по repeat)."""
    grouped = {}
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            if not rec.get("ok"):
                continue
            key = (rec.get("id"), rec.get("model"))
            grouped.setdefault(key, []).append((rec.get("repeat", 0), rec.get("answer", "")))
    # сортируем по repeat, оставляем только тексты
    for key in grouped:
        grouped[key] = [a for _, a in sorted(grouped[key], key=lambda x: x[0])]
    return grouped


def load_graded(out_dir):
    done = set()
    path = os.path.join(out_dir, GRADES_FILE)
    if not os.path.exists(path):
        return done
    with open(path, "r", encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError:
                continue
            done.add((rec.get("id"), rec.get("model")))
    return done


def extract_json(text):
    """Пытается вытащить JSON-объект из текста (снимает возможные ``` заборы)."""
    t = text.strip()
    if t.startswith("```"):
        # снять первую строку с ``` и завершающие ```
        t = t.split("\n", 1)[1] if "\n" in t else t
        if t.rstrip().endswith("```"):
            t = t.rstrip()[:-3]
    t = t.strip()
    # если есть лишний текст — берём от первой { до последней }
    if not t.startswith("{"):
        i, j = t.find("{"), t.rfind("}")
        if i != -1 and j != -1 and j > i:
            t = t[i:j + 1]
    return json.loads(t)


def validate_verdict(obj, n_answers):
    """Проверяет структуру вердикта грейдера; бросает ValueError при проблеме."""
    if not isinstance(obj, dict):
        raise ValueError("не объект")
    agg = obj.get("aggregate")
    if agg not in VALID_AGG:
        raise ValueError(f"aggregate вне множества: {agg}")
    per = obj.get("per_answer")
    if not isinstance(per, list) or not per:
        raise ValueError("per_answer пуст/не список")
    for item in per:
        if item.get("verdict") not in VALID_VERDICTS:
            raise ValueError(f"verdict вне множества: {item.get('verdict')}")
    return obj


def grade_one(question, answers):
    """Возвращает (verdict_dict, raw_text). Один retry при невалидном JSON."""
    user = config.build_grader_prompt(
        question=question["question"],
        reference_answer=question.get("reference_answer", ""),
        grading_notes=question.get("grading_notes", ""),
        answers=answers,
    )
    prompt = config.GRADER_SYSTEM + "\n\n" + user
    last_raw = ""
    for attempt in range(2):  # первая попытка + 1 retry
        if attempt == 1:
            prompt = (config.GRADER_SYSTEM
                      + "\n\nПРЕДЫДУЩИЙ ОТВЕТ БЫЛ НЕВАЛИДНЫМ JSON. "
                      + "Верни СТРОГО валидный JSON по схеме, без markdown, без пояснений.\n\n"
                      + user)
        adapter_name, grader_model_id = config.MODELS[config.GRADER_MODEL]
        raw = get_adapter(adapter_name).ask(
            grader_model_id, prompt,
            timeout=getattr(config, "GRADER_TIMEOUT", config.CALL_TIMEOUT),
            retries=config.CALL_RETRIES,
        )
        last_raw = raw
        try:
            obj = extract_json(raw)
            return validate_verdict(obj, len(answers)), raw
        except (ValueError, json.JSONDecodeError):
            continue
    # не удалось распарсить — фиксируем как unstable/parse_error
    return {
        "aggregate": "unstable",
        "aggregate_reason": "грейдер не вернул валидный JSON после retry",
        "per_answer": [],
        "parse_error": True,
    }, last_raw


def main(argv=None):
    ap = argparse.ArgumentParser(description="Семантическая оценка ответов моделей")
    ap.add_argument("--questions", nargs="+", required=True)
    ap.add_argument("--answers", default="results/answers.jsonl")
    ap.add_argument("--out", default="results")
    ap.add_argument("--models", default=None, help="ограничить оценку этими моделями")
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args(argv)

    if not os.path.exists(args.answers):
        print(f"Нет файла ответов: {args.answers}", file=sys.stderr)
        return 2

    qidx = load_questions(args.questions)
    grouped = load_answers(args.answers)
    only = set(m.strip() for m in args.models.split(",")) if args.models else None

    keys = sorted(k for k in grouped if (only is None or k[1] in only))
    os.makedirs(args.out, exist_ok=True)
    done = load_graded(args.out) if args.resume else set()
    todo = [k for k in keys if k not in done]

    print(f"Пар (вопрос,модель) с ответами: {len(keys)} | к оценке: {len(todo)} | "
          f"пропуск: {len(keys) - len(todo)}")

    if args.dry_run:
        for k in todo[:15]:
            print(f"  {k[0]} | {k[1]} | ответов: {len(grouped[k])}")
        return 0

    out_path = os.path.join(args.out, GRADES_FILE)
    n = 0
    for (qid, model) in todo:
        n += 1
        answers = grouped[(qid, model)]
        q = qidx.get(qid)
        if q is None:
            print(f"[{n}/{len(todo)}] {qid} | {model} — нет в банке вопросов, пропуск",
                  file=sys.stderr)
            continue
        try:
            verdict, raw = grade_one(q, answers)
        except AdapterError as e:
            print(f"[{n}/{len(todo)}] {qid} | {model} ERROR грейдера: {str(e)[:120]}",
                  file=sys.stderr)
            verdict, raw = {
                "aggregate": "unstable",
                "aggregate_reason": f"сбой грейдера: {e}",
                "per_answer": [], "grader_error": True,
            }, ""
        rec = {
            "id": qid, "model": model,
            "n_answers": len(answers),
            "aggregate": verdict.get("aggregate"),
            "aggregate_reason": verdict.get("aggregate_reason"),
            "per_answer": verdict.get("per_answer", []),
            "category": q.get("category"),
            "source_file": q.get("source_file"),
            "tier": q.get("tier"),
            "expected_in_weights": q.get("expected_in_weights"),
        }
        if verdict.get("parse_error"):
            rec["parse_error"] = True
        if verdict.get("grader_error"):
            rec["grader_error"] = True
        with open(out_path, "a", encoding="utf-8") as fh:
            fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
        print(f"[{n}/{len(todo)}] {qid} | {model} -> {rec['aggregate']}")

    print(f"\nОценка завершена. Записано в {out_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

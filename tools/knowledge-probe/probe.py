#!/usr/bin/env python3
"""
probe.py — раннер опроса LLM-моделей по банку вопросов о 1С/BSL.

Прогоняет каждый вопрос по каждой модели до N раз (--repeats, по умолчанию 3),
АДАПТИВНО: после двух ответов судья эквивалентности (config.EQUIV_MODEL, одно
слово SAME/DIFF) решает, совпали ли они семантически; при SAME третий повтор
не задаётся (в answers.jsonl пишется skip-маркер, чтобы --resume не переспрашивал).
Дословно совпавшие ответы распознаются без вызова судьи. Любое сомнение или сбой
судьи трактуется в пользу третьего повтора. Отключение: --no-adaptive.

Каждый ответ сохраняется немедленно (append в results/answers.jsonl). Последовательно,
с прогресс-выводом. Поддерживает --resume (пропуск уже полученных ответов) и
--dry-run (показать план без вызовов).

Примеры:
  python3 probe.py --questions 'samples.jsonl' --models haiku --repeats 2 --limit 2
  python3 probe.py --questions '../../tasks/.../questions/questions-*.jsonl' \
                   --models haiku,sonnet,opus,gpt-5.5 --tier 1 --repeats 3 --resume

Схема строки вопроса (JSONL):
  {"id","category","source_file","source_section","knowledge_type",
   "expected_in_weights","tier","question","reference_answer","grading_notes"}

Формат строки ответа (results/answers.jsonl):
  {"id","model","repeat","ok",("answer"|"error"),"ts","question",
   "category","source_file","tier","expected_in_weights"}
"""
import argparse
import glob
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import config  # noqa: E402
from adapters import AdapterError, get_adapter, claude_cli  # noqa: E402

ANSWERS_FILE = "answers.jsonl"


def _norm(s):
    return " ".join((s or "").lower().split())


def answers_equivalent(question, answer_a, answer_b):
    """True, если два ответа семантически одинаковы (тогда третий повтор лишний).

    Дословное совпадение (после нормализации пробелов/регистра) — SAME без вызова
    судьи. Иначе — судья EQUIV_MODEL одним словом; сбой/невнятный ответ = DIFF
    (сомнение в пользу третьего повтора)."""
    if _norm(answer_a) == _norm(answer_b):
        return True
    prompt = config.build_equiv_prompt(question, answer_a, answer_b)
    try:
        raw = claude_cli.ask(
            config.MODELS[config.EQUIV_MODEL][1], prompt,
            timeout=config.CALL_TIMEOUT, retries=config.CALL_RETRIES,
        )
    except AdapterError:
        return False
    return raw.strip().upper().startswith("SAME")


def load_questions(patterns):
    """Загружает вопросы из glob-паттернов (можно несколько через запятую)."""
    files = []
    for pat in patterns:
        for p in pat.split(","):
            p = p.strip()
            if p:
                files.extend(sorted(glob.glob(p)))
    # уникализируем, сохраняя порядок
    seen, ordered = set(), []
    for f in files:
        if f not in seen:
            seen.add(f)
            ordered.append(f)
    questions = []
    for f in ordered:
        with open(f, "r", encoding="utf-8") as fh:
            for ln, line in enumerate(fh, 1):
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                try:
                    q = json.loads(line)
                except json.JSONDecodeError as e:
                    print(f"WARN: {f}:{ln} невалидный JSON пропущен: {e}", file=sys.stderr)
                    continue
                if "id" not in q or "question" not in q:
                    print(f"WARN: {f}:{ln} нет id/question — пропуск", file=sys.stderr)
                    continue
                q["_source_file_probe"] = f
                questions.append(q)
    return ordered, questions


def load_done(out_dir):
    """(id, model, repeat) -> текст ответа (ok-записи) или None (skip-маркер).

    Тексты нужны адаптивному режиму при --resume: судья эквивалентности должен
    видеть ответы из предыдущего запуска."""
    done = {}
    path = os.path.join(out_dir, ANSWERS_FILE)
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
            key = (rec.get("id"), rec.get("model"), rec.get("repeat"))
            if rec.get("ok"):
                done[key] = rec.get("answer", "")
            elif rec.get("skipped"):
                done[key] = None
    return done


def append_record(out_dir, rec):
    path = os.path.join(out_dir, ANSWERS_FILE)
    with open(path, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(rec, ensure_ascii=False) + "\n")


def main(argv=None):
    ap = argparse.ArgumentParser(description="Опрос LLM по банку вопросов 1С/BSL")
    ap.add_argument("--questions", nargs="+", required=True,
                    help="glob(ы) файлов вопросов (можно несколько / через запятую)")
    ap.add_argument("--models", default=",".join(config.DEFAULT_MODELS),
                    help="список alias моделей через запятую")
    ap.add_argument("--tier", type=int, default=None, help="фильтр по ярусу (tier)")
    ap.add_argument("--limit", type=int, default=None, help="макс. число вопросов")
    ap.add_argument("--repeats", type=int, default=config.DEFAULT_REPEATS,
                    help="макс. число повторов на (вопрос,модель)")
    ap.add_argument("--adaptive", action=argparse.BooleanOptionalAction,
                    default=config.DEFAULT_ADAPTIVE,
                    help="после двух семантически совпавших ответов не задавать третий")
    ap.add_argument("--out", default="results", help="каталог результатов")
    ap.add_argument("--resume", action="store_true",
                    help="пропускать уже полученные ответы")
    ap.add_argument("--dry-run", action="store_true",
                    help="показать план без вызова моделей")
    args = ap.parse_args(argv)

    models = [m.strip() for m in args.models.split(",") if m.strip()]
    for m in models:
        if m not in config.MODELS:
            ap.error(f"неизвестная модель '{m}'. Доступны: {', '.join(config.MODELS)}")

    src_files, questions = load_questions(args.questions)
    if not src_files:
        print("Файлы вопросов не найдены по указанным паттернам.", file=sys.stderr)
        return 2
    if args.tier is not None:
        questions = [q for q in questions if q.get("tier") == args.tier]
    if args.limit is not None:
        questions = questions[:args.limit]

    if not questions:
        print("После фильтров вопросов не осталось.", file=sys.stderr)
        return 2

    os.makedirs(args.out, exist_ok=True)
    done = load_done(args.out) if args.resume else {}

    total = len(questions) * len(models) * args.repeats
    planned = sum(
        1
        for q in questions for m in models for r in range(args.repeats)
        if (q["id"], m, r) not in done
    )

    print(f"Файлов вопросов: {len(src_files)} | вопросов: {len(questions)} | "
          f"моделей: {len(models)} | повторов: до {args.repeats}"
          f"{' (адаптивно)' if args.adaptive else ''}")
    print(f"Всего ячеек: {total} | к выполнению: до {planned} | "
          f"пропуск (resume): {total - planned}")
    print(f"Модели: {', '.join(models)}")
    print(f"Результаты: {os.path.join(args.out, ANSWERS_FILE)}")

    if args.dry_run:
        print("\n[dry-run] Вызовы не выполняются.")
        for q in questions[:10]:
            print(f"  {q['id']} [tier={q.get('tier')}] {q['question'][:70]}")
        if len(questions) > 10:
            print(f"  ... и ещё {len(questions) - 10}")
        return 0

    done_n, ok_n, err_n, skip_n = 0, 0, 0, 0
    for q in questions:
        for m in models:
            adapter_name, model_id = config.MODELS[m]
            adapter = get_adapter(adapter_name)
            prompt = config.build_probe_prompt(q["question"])
            texts = {}   # repeat -> текст ответа (включая подхваченные из resume)
            equiv = None  # кэш вердикта судьи для этой пары (вопрос, модель)
            for r in range(args.repeats):
                key = (q["id"], m, r)
                if key in done:
                    if done[key] is not None:
                        texts[r] = done[key]
                    continue
                base = {
                    "id": q["id"], "model": m, "repeat": r,
                    "ts": time.time(), "question": q["question"],
                    "category": q.get("category"),
                    "source_file": q.get("source_file"),
                    "tier": q.get("tier"),
                    "expected_in_weights": q.get("expected_in_weights"),
                }
                if args.adaptive and r >= 2 and 0 in texts and 1 in texts:
                    if equiv is None:
                        equiv = answers_equivalent(q["question"], texts[0], texts[1])
                    if equiv:
                        rec = dict(base, ok=False, skipped=True,
                                   skip_reason="first_two_semantically_identical")
                        skip_n += 1
                        print(f"{q['id']} | {m} | rep {r + 1}/{args.repeats} "
                              f"SKIP (первые два ответа семантически совпали)")
                        append_record(args.out, rec)
                        continue
                done_n += 1
                tag = f"[{done_n}/{planned}] {q['id']} | {m} | rep {r + 1}/{args.repeats}"
                t0 = time.time()
                try:
                    answer = adapter.ask(
                        model_id, prompt,
                        timeout=config.CALL_TIMEOUT,
                        retries=config.CALL_RETRIES,
                    )
                    rec = dict(base, ok=True, answer=answer, ts=time.time())
                    texts[r] = answer
                    ok_n += 1
                    print(f"{tag} OK ({time.time() - t0:.0f}s)")
                except AdapterError as e:
                    rec = dict(base, ok=False, error=str(e), ts=time.time())
                    err_n += 1
                    print(f"{tag} ERROR: {str(e)[:120]}", file=sys.stderr)
                append_record(args.out, rec)

    print(f"\nГотово. Выполнено: {done_n} | успешно: {ok_n} | ошибок: {err_n} | "
          f"сэкономлено повторов (адаптивно): {skip_n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

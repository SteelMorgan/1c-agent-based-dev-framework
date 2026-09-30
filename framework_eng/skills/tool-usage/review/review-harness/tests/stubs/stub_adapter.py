#!/usr/bin/env python3
"""Stub *адаптера* review-harness — граница подделки для fork/turn-lineage тестов.

Зачем отдельный стаб адаптера, а не подмена наших функций
=========================================================
Существующие проверки (`review-swarm/tests/unit/test_native_phase_concurrency.py`,
`test_native_fork_lineage_faults.py`) подменяют `ac.fork_participant` /
`ac.ask_participant` лямбдами — то есть выкидывают ровно тот код, где живёт
дефект. Здесь подменяется ТОЛЬКО исполняемый файл адаптера (внешний процесс).
Настоящими остаются: `swarm.run_native_fork_shards`, весь `adapter_contract`
(`fork_participant` / `ask_participant` / `reconcile_participant` / retry-политика
/ парсинг stdout / чтение review.json).

Что моделирует стаб
===================
Две семантики идентичности провайдера (env `STUB_ADAPTER_SEMANTICS`):

* ``coupled``   — `provider_checkpoint_id == provider_turn_id` (как claude/codex
  в живых песочницах: chk `2cc89828…` == turn `2cc89828…`);
* ``divergent`` — `provider_checkpoint_id != provider_turn_id` (как kimi:
  chk `session_b0cdcfc1…` != turn `local:9643dde4…`). Ребёнок форка наследует
  `provider_turn_id` родителя (поведение `kimi_review.py cmd_fork`), а
  `provider_checkpoint_id` остаётся id родительской сессии.

`ask` СТРОГО сверяет присланный `--parent-provider-turn-id` с фактической
головой ревью (как `kimi_review.py:816`) и отказывает при несовпадении. Стаб с
согласованной семантикой (родитель всегда совпадает) бесполезен — он и есть
причина ложной зелени существующих проверок.

Exactly-once: завершённая operation обслуживается из durable-записи
`pending-operations/<operation_id>.json` без нового model call. Журнал
`model-calls.jsonl` считает именно НОВЫЕ ходы провайдера, `invocations.jsonl` —
все запуски адаптера.

Управление через env
====================
``STUB_ADAPTER_SEMANTICS``  coupled|divergent (default coupled);
``STUB_ADAPTER_STATE``      каталог журналов (invocations/model-calls);
``STUB_ADAPTER_CONTROL``    путь к JSON со сценарием отказов:
    {"fail_fork_children": {"<child_review_id>": {"message": "...", "attempts": 1}},
     "fail_ask_operations": {"<operation_id>": "..."},
     "ambiguous_reconcile_operations": {"<operation_id>": "..."},
     "fork_child_inherits": {"<child_review_id>": ["session_id", "workspace_path"]},
     "ask_responses_by_question_marker": {"<подстрока вопроса>": "<шаблон ответа>"}}

`ask_responses_by_question_marker` задаёт текст ответа `ask` по подстроке
вопроса (первое совпадение в лексикографическом порядке ключей). В шаблоне
подставляются `{turn}` — сквозной номер хода провайдера (уникален на каждый
НОВЫЙ вызов, удобно как источник заведомо различного значения) и
`{review_id}`. Рычаг нужен там, где вызывающему требуется РАЗБИРАЕМЫЙ ход:
ответ по умолчанию структурным не является намеренно.

`fork_child_inherits` выражает класс дефекта «ребёнок унаследовал состояние
родителя» (боевой отказ kimi: сервер привязал ребёнка к каталогу родителя и
проигнорировал переданный) — названные поля меты ребёнка берутся у родителя
вместо свежевыведенных. По умолчанию выключено.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path

REVIEW_ROOT = Path(".review-sandboxes")
LOCK_NAME = "invocation.lock"


# --------------------------------------------------------------------------
# состояние / журналы
# --------------------------------------------------------------------------
def state_dir() -> Path:
    directory = Path(os.environ.get("STUB_ADAPTER_STATE", ".stub-adapter-state"))
    directory.mkdir(parents=True, exist_ok=True)
    return directory


def control() -> dict:
    path = os.environ.get("STUB_ADAPTER_CONTROL")
    if not path or not Path(path).is_file():
        return {}
    return json.loads(Path(path).read_text(encoding="utf-8"))


def semantics() -> str:
    return os.environ.get("STUB_ADAPTER_SEMANTICS", "coupled")


def log_line(name: str, payload: dict) -> None:
    with (state_dir() / name).open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False) + "\n")


def bump(counter: str) -> int:
    path = state_dir() / f"{counter}.counter"
    value = int(path.read_text(encoding="utf-8")) if path.exists() else 0
    path.write_text(str(value + 1), encoding="utf-8")
    return value + 1


# --------------------------------------------------------------------------
# песочница
# --------------------------------------------------------------------------
def review_dir(review_id: str) -> Path:
    if review_id in {".", ".."} or "/" in review_id or "\\" in review_id:
        raise ValueError(f"unsafe review_id {review_id!r}")
    return REVIEW_ROOT / review_id


def is_lock_only_tombstone(directory: Path) -> bool:
    if directory.is_symlink() or not directory.is_dir():
        return False
    return [entry.name for entry in directory.iterdir()] == [LOCK_NAME]


def prepare_start_directory(directory: Path) -> None:
    """Совпадает по смыслу с invocation_lock.prepare_start_directory."""
    if directory.exists() and not is_lock_only_tombstone(directory):
        raise RuntimeError(
            f"review sandbox already exists and is not a lock-only tombstone: {directory}"
        )
    directory.mkdir(parents=True, exist_ok=True)
    (directory / LOCK_NAME).touch(exist_ok=True)
    (directory / "workspace").mkdir(exist_ok=True)


def load_meta(review_id: str) -> tuple[Path, dict]:
    directory = review_dir(review_id)
    path = directory / "review.json"
    if not path.is_file():
        raise RuntimeError(f"review {review_id!r} has no durable meta")
    return directory, json.loads(path.read_text(encoding="utf-8"))


def save_meta(directory: Path, meta: dict) -> None:
    (directory / "review.json").write_text(
        json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")


def digest(*parts: str) -> str:
    return hashlib.sha256("\0".join(parts).encode("utf-8")).hexdigest()


def new_turn_identity(seed: str, checkpoint_id: str) -> tuple[str, str]:
    """(provider_checkpoint_id, provider_turn_id) для очередного хода.

    coupled: чекпойнт двигается вместе с ходом и РАВЕН ему (claude/codex);
    divergent: чекпойнт остаётся id сессии, ход — отдельная `local:`-identity
    (kimi). Именно расхождение делает подстановку чекпойнта вместо головы
    ребёнка наблюдаемой.
    """
    if semantics() == "divergent":
        return checkpoint_id, "local:" + digest(seed)
    coupled = "sess-" + digest(seed)[:32]
    return coupled, coupled


# --------------------------------------------------------------------------
# subcommands
# --------------------------------------------------------------------------
def cmd_start(args: argparse.Namespace) -> int:
    review_id = args.review_id or ("stub-" + digest(args.question)[:16])
    directory = review_dir(review_id)
    prepare_start_directory(directory)
    session_id = "session_" + digest("session", review_id)[:24]
    checkpoint_id, turn_id = new_turn_identity(f"start:{review_id}", session_id)
    if semantics() == "divergent":
        checkpoint_id = session_id
    log_line("model-calls.jsonl", {"kind": "start", "review_id": review_id})
    save_meta(directory, {
        "review_id": review_id,
        "session_id": session_id,
        "provider_checkpoint_id": checkpoint_id,
        "provider_turn_id": turn_id,
        "last_response": "stub parent response",
        "workspace_path": str((directory / "workspace").resolve()),
        "status": "open",
    })
    print(f"review_id: {review_id}")
    print(f"session_id: {session_id}")
    print(f"workspace: {(directory / 'workspace').resolve()}")
    print("stub parent response")
    return 0


def cmd_fork(args: argparse.Namespace) -> int:
    parent_dir, parent = load_meta(args.parent_review_id)
    if args.provider_checkpoint_id != parent.get("provider_checkpoint_id"):
        raise RuntimeError("provider_checkpoint_id must match parent canonical provider cursor")
    child_dir = review_dir(args.child_review_id)

    fault = (control().get("fail_fork_children") or {}).get(args.child_review_id)
    if fault:
        attempt = bump(f"fork:{args.child_review_id}")
        if attempt <= int(fault.get("attempts", 1)):
            # Провайдер отказал ПОСЛЕ частичной материализации песочницы:
            # ровно та ситуация, где слепой повтор упрётся в непустой каталог.
            prepare_start_directory(child_dir)
            (child_dir / "partial-fork.json").write_text("{}", encoding="utf-8")
            print(fault.get("message", "stub fork refused"), file=sys.stderr)
            return 1

    prepare_start_directory(child_dir)
    child_session_id = "session_" + digest("fork", args.parent_review_id, args.operation_id)[:24]
    # Голова ребёнка. divergent: наследуется provider_turn_id родителя (kimi);
    # coupled: совпадает с провайдерским чекпойнтом (claude/codex).
    child_head = (parent["provider_turn_id"] if semantics() == "divergent"
                  else args.provider_checkpoint_id)
    log_line("model-calls.jsonl", {"kind": "fork", "review_id": args.child_review_id})
    child = {
        "review_id": args.child_review_id,
        "session_id": child_session_id,
        "parent_review_id": args.parent_review_id,
        "parent_session_id": parent["session_id"],
        "provider_fork_ref": child_session_id,
        "operation_id": args.operation_id,
        "snapshot_digest": args.snapshot_digest,
        "provider_checkpoint_id": args.provider_checkpoint_id,
        "provider_turn_id": child_head,
        "prompt_consumed": False,
        "last_response": "",
        "workspace_path": str((child_dir / "workspace").resolve()),
        "status": "open",
    }
    # Класс дефекта «ребёнок унаследовал состояние родителя» (боевой отказ kimi:
    # сервер привязал ребёнка к каталогу родителя и проигнорировал переданный).
    # Без этого рычага стаб строил мету ребёнка только из свежевыведенных
    # значений, и такой дефект в нём был НЕВЫРАЗИМ. По умолчанию выключен.
    inherit = (control().get("fork_child_inherits") or {}).get(args.child_review_id) or []
    for field in inherit:
        if field not in parent:
            raise ValueError(f"parent meta has no field {field!r} to inherit")
        child[field] = parent[field]
    save_meta(child_dir, child)
    print(f"review_id: {child['review_id']}")
    print(f"session_id: {child['session_id']}")
    print(f"parent_review_id: {args.parent_review_id}")
    print(f"parent_session_id: {parent['session_id']}")
    print(f"provider_fork_ref: {child['provider_fork_ref']}")
    print(f"snapshot_digest: {child['snapshot_digest']}")
    print(f"provider_checkpoint_id: {child['provider_checkpoint_id']}")
    print(f"provider_turn_id: {child['provider_turn_id']}")
    print(f"operation_id: {child['operation_id']}")
    print("prompt_consumed: false")
    print(f"workspace: {child['workspace_path']}")
    return 0


def pending_path(directory: Path, operation_id: str) -> Path:
    pending_dir = directory / "pending-operations"
    pending_dir.mkdir(exist_ok=True)
    return pending_dir / f"{operation_id}.json"


def cmd_ask(args: argparse.Namespace) -> int:
    directory, meta = load_meta(args.review_id)
    if bool(args.operation_id) != bool(args.parent_provider_turn_id):
        raise ValueError("operation_id and parent_provider_turn_id must be supplied together")
    if args.operation_id:
        record_path = pending_path(directory, args.operation_id)
        record = (json.loads(record_path.read_text(encoding="utf-8"))
                  if record_path.exists() else None)
        if record and record.get("state") == "completed":
            # exactly-once: завершённая operation не порождает новый model call
            print(f"provider_turn_id: {record['provider_turn_id']}")
            print(record["response"])
            return 0
        # Строгая сверка родителя: это и есть контракт, который в бою ломается.
        if meta.get("provider_turn_id") != args.parent_provider_turn_id:
            raise RuntimeError(
                "parent_provider_turn_id does not match current provider head "
                f"(head={meta.get('provider_turn_id')!r}, "
                f"received={args.parent_provider_turn_id!r})"
            )
        record_path.write_text(json.dumps({
            "operation_id": args.operation_id,
            "parent_provider_turn_id": args.parent_provider_turn_id,
            "state": "pending",
        }, ensure_ascii=False), encoding="utf-8")
        fault = (control().get("fail_ask_operations") or {}).get(args.operation_id)
        if fault:
            print(fault, file=sys.stderr)
            return 1

    turn_no = bump("turn")
    # Ответ по умолчанию — не structured-ход: он и должен уводить участника в
    # unresponsive там, где вызывающий ждёт разобранный ход. Тест, которому
    # нужен ВАЛИДНЫЙ ход, объявляет его через control-рычаг ниже, а не
    # ослабляет правило разбора у вызывающего.
    response = f"stub turn for {args.review_id}"
    for marker, template in sorted(
            (control().get("ask_responses_by_question_marker") or {}).items()):
        if marker in (args.question or ""):
            response = (str(template).replace("{turn}", str(turn_no))
                        .replace("{review_id}", args.review_id))
            break
    seed = f"turn:{args.review_id}:{args.operation_id or args.question}:{turn_no}"
    checkpoint_id, turn_id = new_turn_identity(seed, meta["provider_checkpoint_id"])
    log_line("model-calls.jsonl", {"kind": "ask", "review_id": args.review_id,
                                   "operation_id": args.operation_id})
    meta["provider_checkpoint_id"] = checkpoint_id
    meta["provider_turn_id"] = turn_id
    meta["last_response"] = response
    save_meta(directory, meta)
    if args.operation_id:
        pending_path(directory, args.operation_id).write_text(json.dumps({
            "operation_id": args.operation_id,
            "parent_provider_turn_id": args.parent_provider_turn_id,
            "state": "completed", "response": response,
            "provider_turn_id": turn_id, "provider_checkpoint_id": checkpoint_id,
        }, ensure_ascii=False), encoding="utf-8")
    print(f"[review {args.review_id}] events=1")
    print(response)
    return 0


def cmd_reconcile(args: argparse.Namespace) -> int:
    directory, meta = load_meta(args.review_id)
    fault = (control().get("ambiguous_reconcile_operations") or {}).get(args.operation_id)
    if fault:
        print(json.dumps({"operation_id": args.operation_id, "state": "ambiguous",
                          "evidence_complete": False, "error": fault}, ensure_ascii=False))
        return 0
    record_path = pending_path(directory, args.operation_id)
    record = (json.loads(record_path.read_text(encoding="utf-8"))
              if record_path.exists() else None)
    if record is None:
        print(json.dumps({"operation_id": args.operation_id, "state": "absent",
                          "evidence_complete": True}, ensure_ascii=False))
        return 0
    if record.get("state") != "completed":
        print(json.dumps({"operation_id": args.operation_id, "state": "absent",
                          "evidence_complete": True}, ensure_ascii=False))
        return 0
    print(json.dumps({
        "operation_id": args.operation_id, "state": "completed",
        "evidence_complete": True, "prompt_consumed": True,
        "provider_turn_id": record["provider_turn_id"],
        "provider_checkpoint_id": record.get("provider_checkpoint_id"),
        "session_id": meta["session_id"], "text": record["response"],
    }, ensure_ascii=False))
    return 0


def cmd_close(args: argparse.Namespace) -> int:
    directory = review_dir(args.review_id)
    if not directory.exists():
        return 0
    if args.keep_sandbox:
        return 0
    for entry in directory.iterdir():
        if entry.name == LOCK_NAME:
            continue
        shutil.rmtree(entry) if entry.is_dir() else entry.unlink()
    (directory / LOCK_NAME).touch(exist_ok=True)
    return 0


def cmd_capabilities(args: argparse.Namespace) -> int:
    print(json.dumps({"native_fork": {
        "supported": True, "route": "stub_native_fork",
        "min_cli_version": "0.0.1", "exact_checkpoint": True,
        "automation_safe": True,
    }}, ensure_ascii=False, sort_keys=True))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Stub review-harness adapter")
    sub = parser.add_subparsers(dest="command", required=True)

    p_start = sub.add_parser("start")
    p_start.add_argument("paths", nargs="*")
    p_start.add_argument("--question", required=True)
    p_start.add_argument("--timeout-sec", type=int, default=900)
    p_start.add_argument("--model")
    p_start.add_argument("--review-id")
    p_start.set_defaults(func=cmd_start)

    p_fork = sub.add_parser("fork")
    p_fork.add_argument("parent_review_id")
    p_fork.add_argument("--child-review-id", required=True)
    p_fork.add_argument("--operation-id", required=True)
    p_fork.add_argument("--provider-checkpoint-id", required=True)
    p_fork.add_argument("--snapshot-digest", required=True)
    p_fork.add_argument("--question")
    p_fork.add_argument("--timeout-sec", type=int, default=900)
    p_fork.set_defaults(func=cmd_fork)

    p_ask = sub.add_parser("ask")
    p_ask.add_argument("review_id")
    p_ask.add_argument("--question", required=True)
    p_ask.add_argument("--timeout-sec", type=int, default=900)
    p_ask.add_argument("--operation-id")
    p_ask.add_argument("--parent-provider-turn-id")
    p_ask.set_defaults(func=cmd_ask)

    p_rec = sub.add_parser("reconcile")
    p_rec.add_argument("review_id")
    p_rec.add_argument("--operation-id", required=True)
    p_rec.add_argument("--parent-provider-turn-id")
    p_rec.add_argument("--timeout-sec", type=int, default=60)
    p_rec.add_argument("--json", action="store_true")
    p_rec.set_defaults(func=cmd_reconcile)

    p_close = sub.add_parser("close")
    p_close.add_argument("review_id")
    p_close.add_argument("--keep-sandbox", action="store_true")
    p_close.set_defaults(func=cmd_close)

    p_cap = sub.add_parser("capabilities")
    p_cap.add_argument("--json", action="store_true")
    p_cap.set_defaults(func=cmd_capabilities)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    log_line("invocations.jsonl", {"command": args.command, "argv": sys.argv[1:]})
    try:
        return args.func(args)
    except Exception as exc:  # адаптерный контракт: exit 1 + текст в stderr
        print(f"{type(exc).__name__}: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

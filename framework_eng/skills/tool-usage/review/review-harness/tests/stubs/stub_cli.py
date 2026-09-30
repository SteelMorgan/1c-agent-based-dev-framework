#!/usr/bin/env python3
"""Stub CLI для contract/integration-слоёв консилиума (TD 1.1/1.2, test-plan §2).

Эмулирует фактические форматы вывода трёх CLI (claude / codex / kimi) в режимах:
  ok      — нормальный ответ (stream-json / jsonl + result-файл);
  timeout — молчит до таймаута адаптера (процесс убивается адаптером);
  error   — немедленный exit 1;
  flaky   — первая попытка error, последующие ok (retry-политика).

Управление через env:
  STUB_MODE        — ok|timeout|error|flaky (default ok);
  STUB_STATE_DIR   — каталог состояния (счётчик flaky, стабильные session id);
  STUB_REPLY_FILE  — файл с текстом ответа (иначе — ответ по умолчанию);
  STUB_TIMEOUT_SLEEP — секунд сна в режиме timeout (default 3600).
Имя CLI определяется по basename(argv[0]): claude | codex | kimi.
"""
from __future__ import annotations

import json
import os
import sys
import time
import uuid
from pathlib import Path

DEFAULT_REPLY_TEMPLATE = """Stub-ответ участника консилиума.

Модель решения: элементы E1 (ядро), E2 (транспорт).

```consilium-structured
{structured}
```
"""

CHECKLIST_ITEM_RE = __import__("re").compile(r"^- \[([a-z][a-z0-9\-]*)\]", __import__("re").M)


def build_default_reply(prompt: str) -> str:
    """Дефолтный ответ: чеклист-ответы генерируются из item_id промпта
    (формат строк промпта `- [item_id] текст`) — валидно для любого домена."""
    item_ids = CHECKLIST_ITEM_RE.findall(prompt or "")
    responses = [{"item_id": item_id, "verdict": "clear"} for item_id in item_ids]
    structured = json.dumps({
        "elements": ["E1", "E2"], "new_findings": [], "position_changes": [],
        "borrowed": [], "risk_checklist_responses": responses,
    }, ensure_ascii=False)
    return DEFAULT_REPLY_TEMPLATE.replace("{structured}", structured)


def env_mode() -> str:
    return os.environ.get("STUB_MODE", "ok")


def state_dir() -> Path:
    raw = os.environ.get("STUB_STATE_DIR")
    return Path(raw) if raw else Path(".stub-state")


def scripted_reply(cli_name: str) -> str | None:
    """Scripted-режим: STUB_REPLY_DIR/<cli>-<n>.md по счётчику вызовов."""
    directory = os.environ.get("STUB_REPLY_DIR")
    if not directory:
        return None
    state = state_dir()
    state.mkdir(parents=True, exist_ok=True)
    counter_path = state / f"{cli_name}.reply-counter"
    count = int(counter_path.read_text(encoding="utf-8").strip()) if counter_path.exists() else 0
    counter_path.write_text(str(count + 1), encoding="utf-8")
    candidate = Path(directory) / f"{cli_name}-{count + 1}.md"
    return candidate.read_text(encoding="utf-8") if candidate.exists() else None


def reply_text(cli_name: str, prompt: str) -> str:
    scripted = scripted_reply(cli_name)
    if scripted is not None:
        return scripted
    reply_file = os.environ.get("STUB_REPLY_FILE")
    if reply_file:
        return Path(reply_file).read_text(encoding="utf-8")
    return build_default_reply(prompt)


def stable_id(name: str) -> str:
    """Стабильный id в пределах STUB_STATE_DIR (сохраняется между вызовами)."""
    directory = state_dir()
    directory.mkdir(parents=True, exist_ok=True)
    marker = directory / f"{name}.id"
    if marker.exists():
        return marker.read_text(encoding="utf-8").strip()
    value = str(uuid.uuid4())
    marker.write_text(value, encoding="utf-8")
    return value


def fork_session_id(cli_name: str, parent_session_id: str) -> str:
    """Детерминированный, но отличный от parent id для native-fork fixtures."""
    return stable_id(f"{cli_name}-fork-{parent_session_id}")


def flaky_should_fail(cli_name: str) -> bool:
    directory = state_dir()
    directory.mkdir(parents=True, exist_ok=True)
    counter_path = directory / f"{cli_name}.counter"
    count = int(counter_path.read_text(encoding="utf-8").strip()) if counter_path.exists() else 0
    counter_path.write_text(str(count + 1), encoding="utf-8")
    return count == 0


def check_mode(cli_name: str) -> None:
    """Применяет режимы timeout/error/flaky; при применении завершает процесс.

    Режим может быть адресным: "timeout:kimi" действует только на CLI kimi.
    """
    mode = env_mode()
    if ":" in mode:
        mode, target = mode.split(":", 1)
        if target != cli_name:
            return
    if mode == "timeout":
        time.sleep(float(os.environ.get("STUB_TIMEOUT_SLEEP", "3600")))
        raise SystemExit(0)
    if mode == "error":
        print(f"stub {cli_name}: forced error (STUB_MODE=error)", file=sys.stderr)
        raise SystemExit(1)
    fork_route = (
        "fork" in sys.argv[1:]
        or "--fork-session" in sys.argv[1:]
        or "app-server" in sys.argv[1:]
    )
    if mode == "fork-unsupported" and fork_route:
        print(f"stub {cli_name}: native fork unsupported", file=sys.stderr)
        raise SystemExit(1)
    if mode == "flaky" and flaky_should_fail(cli_name):
        print(f"stub {cli_name}: forced flaky error (first attempt)", file=sys.stderr)
        raise SystemExit(1)


def read_stdin() -> str:
    try:
        return sys.stdin.read()
    except Exception:
        return ""


def arg_value(args: list[str], flag: str) -> str | None:
    if flag in args:
        idx = args.index(flag)
        if idx + 1 < len(args):
            return args[idx + 1]
    prefix = flag + "="
    for arg in args:
        if arg.startswith(prefix):
            return arg[len(prefix):]
    return None


# --- Хранилище сессий Claude Code (подделка приватного контракта CLI) -------
#
# Настоящий CLI кладёт транскрипт сессии в
#     <CLAUDE_CONFIG_DIR | ~/.claude>/projects/<slug(cwd)>/<session-id>.jsonl
# и `--resume <id>` ищет сессию ТОЛЬКО в проектном каталоге, вычисленном из
# текущего cwd. Стаб, который этого не воспроизводит, делает fail-closed страж
# адаптера (`stage_parent_transcript_for_fork`) неотличимым от боевого отказа
# провайдера — это и есть источник ложной зелени. Поэтому стаб пишет транскрипт
# и на `--resume` требует его наличия ровно там, где его ищет CLI.
#
# Каталог берётся из CLAUDE_CONFIG_DIR; если он не задан — из STUB_STATE_DIR,
# но НИКОГДА из реального ~/.claude: стаб не вправе писать в домашний каталог.


def claude_projects_root() -> Path:
    configured = os.environ.get("CLAUDE_CONFIG_DIR")
    base = Path(configured).expanduser() if configured else state_dir() / "claude-config"
    return base / "projects"


def claude_project_slug(workspace: Path) -> str:
    return __import__("re").sub(r"[^a-zA-Z0-9]", "-", str(Path(workspace).resolve()))


def claude_transcript_path(session_id: str) -> Path:
    return claude_projects_root() / claude_project_slug(Path.cwd()) / f"{session_id}.jsonl"


def claude_require_resumable(session_id: str) -> None:
    """Как настоящий CLI: сессия не видна из чужого проектного каталога."""
    if not claude_transcript_path(session_id).is_file():
        print(f"No conversation found with session ID: {session_id}", file=sys.stderr)
        raise SystemExit(1)


def claude_write_transcript(session_id: str, prompt: str, reply: str) -> None:
    path = claude_transcript_path(session_id)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        for role, text in (("user", prompt), ("assistant", reply)):
            handle.write(json.dumps(
                {"type": role, "sessionId": session_id,
                 "cwd": str(Path.cwd().resolve()),
                 "message": {"role": role, "content": text}},
                ensure_ascii=False) + "\n")


def run_claude(args: list[str]) -> int:
    check_mode("claude")
    prompt = read_stdin()  # prompt приходит через stdin (TD 1.1)
    resume_id = arg_value(args, "--resume")
    forking = "--fork-session" in args
    if resume_id:
        claude_require_resumable(resume_id)
    session_id = (
        fork_session_id("claude", resume_id)
        if forking and resume_id else stable_id("claude-session")
    )
    result = {
        "type": "result",
        "subtype": "success",
        "result": reply_text("claude", prompt),
        "session_id": session_id,
        "total_cost_usd": 0.0,
        "num_turns": 1,
        "usage": {"input_tokens": 10, "output_tokens": 20},
    }
    claude_write_transcript(session_id, prompt, result["result"])
    print(json.dumps({"type": "system", "subtype": "init", "session_id": session_id}))
    print(json.dumps(result))
    return 0


def run_codex(args: list[str]) -> int:
    if args[:1] == ["--version"] or "--version" in args:
        print("codex-stub 0.200.0")
        return 0
    check_mode("codex")
    if args[:1] == ["app-server"]:
        for line in sys.stdin:
            request = json.loads(line)
            if "id" not in request:
                continue
            if request.get("method") == "initialize":
                result = {"userAgent": "codex-stub/0.200.0"}
            elif request.get("method") == "thread/fork":
                parent = request.get("params", {}).get("threadId", "parent")
                child_id = fork_session_id("codex", parent)
                inventory = state_dir() / "codex-children.json"
                children = json.loads(inventory.read_text(encoding="utf-8")) if inventory.exists() else []
                if not any(item.get("id") == child_id for item in children):
                    children.append({"id": child_id, "parentThreadId": parent})
                    inventory.write_text(json.dumps(children), encoding="utf-8")
                result = {"thread": {"id": child_id}}
            elif request.get("method") == "thread/list":
                inventory = state_dir() / "codex-children.json"
                children = json.loads(inventory.read_text(encoding="utf-8")) if inventory.exists() else []
                parent = request.get("params", {}).get("parentThreadId")
                result = {"data": [item for item in children
                                    if item.get("parentThreadId") == parent], "nextCursor": None}
            elif request.get("method") == "thread/read":
                parent = request.get("params", {}).get("threadId", "parent")
                result = {"thread": {"id": parent, "turns": [{
                    "id": stable_id(f"codex-head-{parent}"), "status": "completed",
                }]}}
            else:
                result = {}
            print(json.dumps({"id": request["id"], "result": result}), flush=True)
        return 0
    prompt = read_stdin()
    thread_id = stable_id("codex-thread")
    output_path = arg_value(args, "-o")
    if output_path:
        Path(output_path).write_text(reply_text("codex", prompt), encoding="utf-8")
    print(json.dumps({"type": "thread.started", "thread_id": thread_id}))
    print(json.dumps({
        "type": "turn.completed",
        "usage": {"input_tokens": 10, "output_tokens": 20, "total_tokens": 30},
    }))
    return 0


def run_kimi(args: list[str]) -> int:
    if "--version" in args or "-V" in args:
        print("0.31.0")
        return 0
    if args[:1] == ["doctor"]:
        print("kimi doctor: configuration OK (stub)")
        return 0
    if "--help" in args or "-h" in args:
        print("kimi stub help")
        return 0
    check_mode("kimi")
    # prompt приходит через -p "<prompt>" (TD 1.2)
    prompt = arg_value(args, "-p") or ""
    session_id = "session_" + stable_id("kimi-session").replace("-", "")
    print(json.dumps({"role": "assistant", "content": reply_text("kimi", prompt)}))
    print(json.dumps({
        "role": "meta",
        "type": "session.resume_hint",
        "session_id": session_id,
        "command": f"kimi -r {session_id}",
    }))
    return 0


def log_invocation(cli_name: str, args: list[str]) -> None:
    """Лог argv каждого вызова — contract-тесты проверяют схему аргументов."""
    try:
        directory = state_dir()
        directory.mkdir(parents=True, exist_ok=True)
        with (directory / "invocations.jsonl").open("a", encoding="utf-8") as f:
            f.write(json.dumps({"cli": cli_name, "argv": args}, ensure_ascii=False) + "\n")
    except OSError:
        pass


def main() -> int:
    cli_name = Path(sys.argv[0]).stem
    args = sys.argv[1:]
    log_invocation(cli_name, args)
    if cli_name == "claude":
        return run_claude(args)
    if cli_name == "codex":
        return run_codex(args)
    if cli_name == "kimi":
        return run_kimi(args)
    print(f"stub_cli: unknown cli name {cli_name!r}", file=sys.stderr)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

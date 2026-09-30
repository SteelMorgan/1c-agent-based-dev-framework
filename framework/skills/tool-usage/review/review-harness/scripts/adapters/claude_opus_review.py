#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import uuid
from datetime import UTC, datetime
from pathlib import Path


ADAPTER_DIR = Path(__file__).resolve().parent
HARNESS_SCRIPTS_DIR = ADAPTER_DIR.parent
if str(HARNESS_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(HARNESS_SCRIPTS_DIR))

from invocation_lock import (  # noqa: E402
    SessionBusyError,
    clear_sandbox_contents_preserving_lock,
    invocation_lock,
    prepare_start_directory,
    review_dir_for_id,
)
from runtime_store import write_json_atomically  # noqa: E402


REVIEW_ROOT = Path(".review-sandboxes")
DEFAULT_MODEL = "claude-opus-5"
DEFAULT_TOOLS = "Read,Grep,Glob,LS"
FOCUSED_AGENT_DIR = ".claude"
SCRIPT_DIR = Path(__file__).resolve().parent
SKILLS_DIR = SCRIPT_DIR.parents[2]  # framework/skills/tool-usage/review — адаптеры живут в review-harness/scripts/adapters/
# Промпт ревьюера — reference исполнителя gate (RVSW-01 TD §3.2): каноническая
# локация — review-swarm/references (переезд в T-13; legacy-кандидат на каталог
# удалённого скилла вычищен в T-15, ASM-01).
REVIEW_PROMPT_CANDIDATES = (
    SKILLS_DIR / "review-swarm" / "references" / "review-prompt.md",
)
REVIEW_PROMPT_PATH = next(
    (path for path in REVIEW_PROMPT_CANDIDATES if path.exists()),
    REVIEW_PROMPT_CANDIDATES[0],
)
RUNTIME_LOCK = threading.RLock()
DEFAULT_EXCLUDES = {
    ".git",
    ".venv",
    ".review-sandboxes",
    "node_modules",
    "__pycache__",
    ".next",
    "dist",
    "build",
    ".DS_Store",
    # R-Final P5: secrets-материалы не копируются в sandbox на fallback-пути
    # (fallback идёт через rglob без git-фильтра admitted files и .gitignore).
    "secrets",
    ".env",
}
# Префиксные исключения fallback-пути: .secrets* — любые dot-файлы секретов
# (.secrets, .secrets.json, ...); точное совпадение части пути их не ловит.
DEFAULT_EXCLUDE_PREFIXES = (".secrets",)
FALLBACK_REVIEW_SYSTEM_PROMPT = """You are an advisory reviewer operating in an isolated review context.

IMPORTANT: Do NOT create, modify, or delete any project files. You may only READ files for analysis.

Provide a second opinion, not the final authority. Order findings by severity: BLOCK, WARN, INFO.
Assign stable finding IDs F-01, F-02, ... and include file:line evidence where possible.
"""
DEFAULT_TIMEOUT_SEC = 1800
CLAUDE_FORK_MIN_VERSION = "2.1.220"
SAFE_OPERATION_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
CANONICAL_SNAPSHOT_DIGEST_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
CAPABILITIES = {
    "adapter": "claude_opus_review",
    "provider": "claude",
    "native_fork": {
        "supported": True,
        "route": "claude_cli_fork_session",
        "min_cli_version": CLAUDE_FORK_MIN_VERSION,
        "exact_checkpoint": True,
        "automation_safe": True,
    },
}


def load_review_system_prompt() -> str:
    try:
        return REVIEW_PROMPT_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        return FALLBACK_REVIEW_SYSTEM_PROMPT.strip()


def clean_response(text: str) -> str:
    if not text:
        return text
    lines = text.splitlines()
    cleaned: list[str] = []
    for line in lines:
        stripped = line.strip()
        if stripped in {
            "Auto Skill Bootstrap (always)",
            "**Auto Skill Bootstrap (always)**",
        }:
            continue
        cleaned.append(line)
    return "\n".join(cleaned).strip()


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def make_review_id() -> str:
    return datetime.now(UTC).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]


def safe_rel_to_cwd(path: Path, cwd: Path) -> Path:
    try:
        return path.resolve().relative_to(cwd.resolve())
    except Exception:
        return Path("external") / path.name


def git_admitted_files(src: Path, source_root: Path) -> list[Path] | None:
    try:
        source_rel = src.absolute().relative_to(source_root.absolute())
    except ValueError:
        return None

    pathspec = source_rel.as_posix() if source_rel.parts else "."
    completed = subprocess.run(
        [
            "git",
            "-C",
            str(source_root),
            "ls-files",
            "--cached",
            "--others",
            "--exclude-standard",
            "-z",
            "--",
            pathspec,
        ],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if completed.returncode != 0:
        return None
    return [
        source_root / raw.decode("utf-8", errors="surrogateescape")
        for raw in completed.stdout.split(b"\0")
        if raw
    ]


def fallback_admitted_files(src: Path) -> list[Path]:
    if src.is_file() or src.is_symlink():
        return [src]
    return [path for path in src.rglob("*") if path.is_file() or path.is_symlink()]


def copy_symlink_if_safe(src: Path, dst: Path, source_root: Path) -> bool:
    if not src.is_symlink():
        return False
    raw_target = os.readlink(src)
    if os.path.isabs(raw_target):
        raise ValueError(f"Unsafe absolute symlink is not copied: {src}")

    lexical_target = Path(os.path.abspath(src.parent / raw_target))
    root_lexical = source_root.absolute()
    try:
        lexical_target.relative_to(root_lexical)
        src.resolve(strict=True).relative_to(source_root.resolve(strict=True))
    except (FileNotFoundError, ValueError) as exc:
        raise ValueError(f"Unsafe symlink escapes or resolves outside source root: {src}") from exc

    try:
        source_relative = src.absolute().relative_to(root_lexical)
        destination_root = dst
        for _ in source_relative.parts:
            destination_root = destination_root.parent
        resolved_destination_root = destination_root.resolve(strict=True)
        resolved_destination_parent = dst.parent.resolve(strict=False)
        resolved_destination_parent.relative_to(resolved_destination_root)
        dst.parent.mkdir(parents=True, exist_ok=True)
        destination_target = dst.parent / raw_target
        destination_target.resolve(strict=True).relative_to(resolved_destination_root)
    except ValueError as exc:
        # An existing destination link that redirects the chain outside the sandbox
        # stays unsafe: reproducing it would let the package read beyond its root.
        raise ValueError(
            f"Unsafe symlink destination target escapes destination root: {dst}"
        ) from exc
    except FileNotFoundError:
        # The mapped target is simply absent, which is the normal case for a mirror
        # link that leaves the copied subtree: `.codex/skills/X -> ../../.claude/skills/X`
        # is safe at the source (checked above: it resolves inside the source root), but
        # `.claude/` is not part of the copied set, so the link would dangle. Reproducing a
        # dangling link silently emptied the package of the very rules the review must read.
        # The content is materialised instead — it stays inside the sandbox and the package
        # becomes self-contained.
        resolved_source = src.resolve(strict=True)
        if dst.exists() or dst.is_symlink():
            remove_dest(dst)
        if resolved_source.is_dir():
            shutil.copytree(resolved_source, dst, symlinks=False)
        else:
            shutil.copy2(resolved_source, dst)
        return True

    if dst.exists() or dst.is_symlink():
        remove_dest(dst)
    dst.symlink_to(raw_target)
    return True


def copy_file_if_present(src: Path, dst: Path) -> bool:
    dst.parent.mkdir(parents=True, exist_ok=True)
    try:
        shutil.copy2(src, dst)
    except FileNotFoundError as exc:
        # A Git-admitted transient may disappear after enumeration; unrelated
        # copy failures still surface while the admitted source exists.
        missing_path = Path(exc.filename) if exc.filename else None
        if src.exists() or (
            missing_path is not None
            and missing_path.absolute() != src.absolute()
        ):
            raise
        if dst.exists():
            dst.unlink()
        return False
    return True


def copy_entry_if_present(src: Path, dst: Path, source_root: Path) -> bool:
    if copy_symlink_if_safe(src, dst, source_root):
        return True
    if src.is_dir():
        # Typechange git-индекса и рабочего дерева (E2E-F2): в индексе entry —
        # symlink, на диске — реальный каталог. copy2 на каталоге ронял весь
        # start участника (IsADirectoryError → unresponsive): дерево копируется
        # рекурсивно — содержимое материализуется, пакет самодостаточен.
        if dst.exists() or dst.is_symlink():
            remove_dest(dst)
        dst.mkdir(parents=True, exist_ok=True)
        for child in sorted(src.iterdir()):
            if should_exclude(child, source_root):
                continue
            copy_entry_if_present(child, dst / child.name, source_root)
        return True
    return copy_file_if_present(src, dst)


def copy_path(src: Path, dst: Path, *, source_root: Path) -> None:
    admitted = git_admitted_files(src, source_root)
    files = admitted if admitted is not None else fallback_admitted_files(src)
    exclusion_root = source_root if admitted is not None else src
    if src.is_file() or src.is_symlink():
        # Explicit focused files are already authorized by the caller; .gitignore
        # must not make a named evidence file disappear from the sandbox.
        copy_entry_if_present(src, dst, source_root)
        return

    for source_file in files:
        if should_exclude(source_file, exclusion_root):
            continue
        try:
            relative = source_file.absolute().relative_to(src.absolute())
        except ValueError:
            continue
        copy_entry_if_present(source_file, dst / relative, source_root)


def make_source_entry(src: Path, source_root: Path) -> dict:
    dest_rel = safe_rel_to_cwd(src, source_root)
    return {
        "source": str(src.resolve()),
        "dest_rel": dest_rel.as_posix(),
    }


def register_source(
    sources: list[dict],
    src: Path,
    source_root: Path,
    *,
    workspace: Path | None = None,
    require_exists: bool = True,
    track_missing: bool = False,
) -> None:
    exists = src.exists()
    if not exists and require_exists:
        raise FileNotFoundError(f"Path does not exist: {src}")
    if not exists and not track_missing:
        return

    entry = make_source_entry(src, source_root)
    if any(existing["dest_rel"] == entry["dest_rel"] for existing in sources):
        return
    sources.append(entry)

    if exists and workspace is not None:
        copy_path(src, workspace / entry["dest_rel"], source_root=source_root)


def remove_dest(path: Path) -> None:
    if path.is_symlink():
        path.unlink()
    elif path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


def sync_sources(meta: dict, review_dir: Path) -> None:
    # F-05: запись только внутри sandbox этого review — подмена workspace_path
    # или dest_rel в review.json отклоняется fail-closed.
    review_root = Path(review_dir).resolve()
    workspace = Path(meta["workspace_path"]).resolve()
    if not workspace.is_relative_to(review_root):
        raise ValueError(
            f"workspace_path outside review sandbox is refused: {meta['workspace_path']}"
        )
    source_root = Path(meta["source_root"])
    for entry in meta.get("sources", []):
        src = Path(entry["source"])
        dst = workspace / entry["dest_rel"]
        if not dst.resolve().is_relative_to(workspace):
            raise ValueError(
                f"dest_rel escapes review workspace: {entry['dest_rel']!r}"
            )
        if not src.exists():
            continue
        if dst.exists():
            remove_dest(dst)
        copy_path(src, dst, source_root=source_root)


def should_exclude(path: Path, source_root: Path) -> bool:
    try:
        rel = path.resolve().relative_to(source_root.resolve())
        parts = set(rel.parts)
    except Exception:
        parts = set(path.parts)
    if parts & DEFAULT_EXCLUDES:
        return True
    return any(part.startswith(DEFAULT_EXCLUDE_PREFIXES) for part in parts)


def copy_full_context(source_root: Path, workspace: Path) -> list[dict]:
    sources: list[dict] = []
    for child in sorted(source_root.iterdir()):
        if should_exclude(child, source_root):
            continue
        register_source(sources, child, source_root, workspace=workspace)
    return sources


def build_brief(args: argparse.Namespace) -> str:
    sections: list[str] = []
    if args.task:
        sections.append(f"Task:\n{args.task}")
    if args.goal:
        sections.append(f"Goal:\n{args.goal}")
    if args.requirements:
        sections.append(f"Requirements:\n{args.requirements}")
    if args.constraints:
        sections.append(f"Constraints:\n{args.constraints}")
    if args.primary_target:
        sections.append(f"Primary review target:\n{args.primary_target}")
    if args.changed_files:
        sections.append("Changed files:\n" + "\n".join(f"- {p}" for p in args.changed_files))
    if args.open_concerns:
        sections.append(f"Known concerns:\n{args.open_concerns}")
    review_ask = args.review_ask or args.question
    if review_ask:
        sections.append(f"Specific review ask:\n{review_ask}")
    return "\n\n".join(sections).strip()


def iter_json_objects(text: str) -> list[dict]:
    objects: list[dict] = []
    for line in text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            parsed = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(parsed, dict):
            objects.append(parsed)
    return objects


def event_type_name(raw: dict) -> str:
    raw_type = str(raw.get("type") or "unknown")
    if raw_type == "stream_event" and isinstance(raw.get("event"), dict):
        return f"stream_event:{raw['event'].get('type') or 'unknown'}"
    if isinstance(raw.get("item"), dict):
        return f"{raw_type}:{raw['item'].get('type') or 'unknown'}"
    return raw_type


def tool_name_from(value: dict) -> str:
    function_name = value.get("function", {}).get("name") if isinstance(value.get("function"), dict) else ""
    return str(value.get("name") or value.get("tool_name") or value.get("tool") or function_name).strip() or "unknown"


def collect_tool_calls(value) -> list[dict]:
    calls: list[dict] = []
    if isinstance(value, dict):
        raw_type = str(value.get("type") or "")
        if raw_type in {"tool_use", "tool_call", "function_call", "command_execution"} or raw_type.endswith("_execution"):
            calls.append({
                "id": value.get("id") or value.get("tool_call_id") or value.get("call_id"),
                "name": tool_name_from(value) if raw_type not in {"command_execution"} and not raw_type.endswith("_execution") else raw_type,
            })
        for key in ("tool_calls", "function_calls"):
            nested = value.get(key)
            if isinstance(nested, list):
                for item in nested:
                    calls.extend(collect_tool_calls(item))
        for item in value.values():
            if isinstance(item, (dict, list)):
                calls.extend(collect_tool_calls(item))
    elif isinstance(value, list):
        for item in value:
            calls.extend(collect_tool_calls(item))
    return calls


def count_tool_results(value) -> int:
    if isinstance(value, dict):
        raw_type = str(value.get("type") or "")
        total = 1 if raw_type in {"tool_result", "function_call_output"} or (raw_type.endswith("_execution") and value.get("status") == "completed") else 0
        return total + sum(count_tool_results(item) for item in value.values() if isinstance(item, (dict, list)))
    if isinstance(value, list):
        return sum(count_tool_results(item) for item in value)
    return 0


def count_permission_denials(value) -> int:
    if isinstance(value, dict):
        total = 0
        denials = value.get("permission_denials")
        if isinstance(denials, list):
            total += len(denials)
        return total + sum(count_permission_denials(item) for item in value.values() if isinstance(item, (dict, list)))
    if isinstance(value, list):
        return sum(count_permission_denials(item) for item in value)
    return 0


def extract_server_tool_use(value) -> dict[str, int]:
    counters: dict[str, int] = {}
    if isinstance(value, dict):
        server_tool_use = value.get("server_tool_use")
        if isinstance(server_tool_use, dict):
            for key, raw in server_tool_use.items():
                if isinstance(raw, int):
                    counters[key] = counters.get(key, 0) + raw
        for item in value.values():
            if isinstance(item, (dict, list)):
                nested = extract_server_tool_use(item)
                for key, raw in nested.items():
                    counters[key] = counters.get(key, 0) + raw
    elif isinstance(value, list):
        for item in value:
            nested = extract_server_tool_use(item)
            for key, raw in nested.items():
                counters[key] = counters.get(key, 0) + raw
    return counters


def empty_progress() -> dict:
    return {
        "raw_events": 0,
        "event_types": {},
        "tool_calls_total": 0,
        "tool_calls_by_name": {},
        "tool_call_ids": [],
        "tool_result_events": 0,
        "permission_denials": 0,
        "server_tool_use": {},
        "last_event_type": None,
        "last_tool_name": None,
    }


def merge_progress_event(progress: dict, raw: dict) -> dict:
    progress = {**empty_progress(), **(progress or {})}
    event_type = event_type_name(raw)
    progress["raw_events"] += 1
    progress["event_types"][event_type] = progress["event_types"].get(event_type, 0) + 1
    progress["last_event_type"] = event_type

    seen_ids = set(progress.get("tool_call_ids") or [])
    for call in collect_tool_calls(raw):
        call_id = str(call.get("id") or "")
        if call_id and call_id in seen_ids:
            continue
        if call_id:
            seen_ids.add(call_id)
            progress["tool_call_ids"] = sorted(seen_ids)
        name = str(call.get("name") or "unknown")
        progress["tool_calls_total"] += 1
        progress["tool_calls_by_name"][name] = progress["tool_calls_by_name"].get(name, 0) + 1
        progress["last_tool_name"] = name

    progress["tool_result_events"] += count_tool_results(raw)
    progress["permission_denials"] += count_permission_denials(raw)
    for key, raw_count in extract_server_tool_use(raw).items():
        progress["server_tool_use"][key] = progress["server_tool_use"].get(key, 0) + raw_count
    return progress


def observe_stream_line(review_dir: Path | None, line: str) -> None:
    if review_dir is None:
        return
    try:
        parsed = json.loads(line)
    except json.JSONDecodeError:
        return
    if not isinstance(parsed, dict):
        return
    runtime = load_runtime(review_dir)
    progress = merge_progress_event(runtime.get("progress") or {}, parsed)
    update_runtime(review_dir, progress=progress)
    append_runtime_event(review_dir, {
        "ts": utc_now(),
        "type": "model-event",
        "event_type": progress["last_event_type"],
        "tool_calls_total": progress["tool_calls_total"],
        "last_tool_name": progress["last_tool_name"],
    })


def _stream_reader(stream, log_path: Path, sink: list[str], stamp_key: str, review_dir: Path | None) -> None:
    with log_path.open("a", encoding="utf-8") as log:
        while True:
            chunk = stream.readline()
            if chunk == "":
                break
            sink.append(chunk)
            log.write(chunk)
            log.flush()
            observe_stream_line(review_dir, chunk)
            if review_dir is not None:
                now = utc_now()
                update_runtime(review_dir, last_activity_at=now, **{stamp_key: now})
    try:
        stream.close()
    except Exception:
        pass


# --- ПРИВАТНЫЙ КОНТРАКТ ХРАНЕНИЯ СЕССИЙ CLAUDE CODE (недокументированный) ---
#
# Claude CLI кладёт транскрипт сессии в
#     <CLAUDE_CONFIG_DIR | ~/.claude>/projects/<slug(cwd)>/<session-id>.jsonl
# где slug — абсолютный путь рабочего каталога, в котором каждый
# не-алфанумерический символ заменён на "-". `--resume <id>` ищет сессию ТОЛЬКО
# в проектном каталоге, вычисленном из текущего cwd: сессия, начатая в
# рабочем каталоге родителя, из рабочего каталога ребёнка не видна, и CLI
# отвечает «No conversation found with session ID».
#
# Это НЕ публичный контракт: ни схема кодирования пути в slug, ни имя файла
# `.jsonl`, ни расположение каталога не гарантированы и могут измениться на
# апгрейде Claude Code. Поэтому все обращения к нему сосредоточены в функциях
# ниже, а несовпадение с ожидаемым форматом обязано ОТКАЗАТЬ форк с внятной
# причиной (fail-closed), а не молча провалиться позже.
CLAUDE_SESSION_STORE_CONTRACT = (
    "приватный формат хранения сессий Claude Code "
    "(<config>/projects/<slug(cwd)>/<session-id>.jsonl)"
)


def claude_projects_root() -> Path:
    """Корень проектных каталогов CLI. Приватный контракт, см. блок выше."""
    configured = os.environ.get("CLAUDE_CONFIG_DIR")
    base = Path(configured).expanduser() if configured else Path.home() / ".claude"
    return base / "projects"


def claude_project_slug(workspace: Path) -> str:
    """Имя проектного каталога для cwd. Приватный контракт, см. блок выше."""
    return re.sub(r"[^a-zA-Z0-9]", "-", str(Path(workspace).resolve()))


def claude_session_transcript_path(workspace: Path, session_id: str) -> Path:
    """Путь транскрипта сессии для данного cwd. Приватный контракт, см. выше."""
    return claude_projects_root() / claude_project_slug(workspace) / f"{session_id}.jsonl"


def stage_parent_transcript_for_fork(
    *,
    parent_workspace: Path,
    child_workspace: Path,
    parent_session_id: str,
) -> Path:
    """Сделать родительскую сессию резюмируемой из рабочего каталога ребёнка.

    Копирует транскрипт родителя в проектный каталог ребёнка, чтобы
    `claude --resume <parent> --fork-session` с `cwd=child_workspace` нашёл
    исходную линию. Fail-closed: если приватный контракт хранения сессий не
    подтвердился (нет транскрипта по ожидаемому пути, каталог назначения не
    создаётся, файл пуст или это не JSONL с объектом в первой строке) — форк
    отказывает здесь и называет причину. Тихого фолбэка нет: без транскрипта
    провайдер всё равно упадёт, но уже невнятным «No conversation found».
    """
    source = claude_session_transcript_path(parent_workspace, parent_session_id)
    if not source.is_file():
        raise RuntimeError(
            f"{CLAUDE_SESSION_STORE_CONTRACT} не подтвердился: не найден транскрипт "
            f"родительской сессии {parent_session_id} по ожидаемому пути {source}. "
            "Форк отказан: перенести линию родителя в рабочий каталог ребёнка нечем."
        )
    try:
        first_line = ""
        with source.open("r", encoding="utf-8") as handle:
            for line in handle:
                if line.strip():
                    first_line = line
                    break
        payload = json.loads(first_line) if first_line else None
        if not isinstance(payload, dict):
            raise ValueError("первая непустая строка не является JSON-объектом")
    except (OSError, ValueError) as exc:
        raise RuntimeError(
            f"{CLAUDE_SESSION_STORE_CONTRACT} не подтвердился: транскрипт {source} "
            f"не читается как непустой JSONL ({exc}). Форк отказан."
        ) from exc

    target = claude_session_transcript_path(child_workspace, parent_session_id)
    if target == source:
        # Рабочие каталоги совпали — переносить нечего, линия уже видна.
        return target
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
    except OSError as exc:
        raise RuntimeError(
            f"{CLAUDE_SESSION_STORE_CONTRACT}: не удалось перенести транскрипт "
            f"родительской сессии {parent_session_id} в проектный каталог ребёнка "
            f"{target.parent} ({exc}). Форк отказан."
        ) from exc
    if not target.is_file() or target.stat().st_size == 0:
        raise RuntimeError(
            f"{CLAUDE_SESSION_STORE_CONTRACT}: транскрипт родительской сессии "
            f"{parent_session_id} не появился в проектном каталоге ребёнка {target}. "
            "Форк отказан."
        )
    return target


def run_claude(
    prompt: str,
    *,
    model: str,
    workspace: Path,
    session_id: str | None = None,
    fork_session_id: str | None = None,
    review_dir: Path | None = None,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
) -> dict:
    cmd = [
        "claude",
        "-p",
        "--verbose",
        "--disable-slash-commands",
        "--model",
        model,
        "--output-format",
        "stream-json",
        "--include-partial-messages",
        "--system-prompt",
        load_review_system_prompt(),
        f"--tools={DEFAULT_TOOLS}",
    ]
    if session_id and fork_session_id:
        raise ValueError("session_id and fork_session_id are mutually exclusive")
    if fork_session_id:
        cmd.extend([
            "--resume",
            fork_session_id,
            "--fork-session",
            "--session-id",
            str(uuid.uuid4()),
        ])
    elif session_id:
        cmd.extend(["--resume", session_id])
    stdout_chunks: list[str] = []
    stderr_chunks: list[str] = []
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=workspace,
        bufsize=1,
    )
    if review_dir is not None:
        update_runtime(
            review_dir,
            pid=proc.pid,
            state="running",
            started_at=load_runtime(review_dir).get("started_at") or utc_now(),
            timeout_sec=timeout_sec,
        )
        mark_phase(review_dir, "starting", pid=proc.pid)

    assert proc.stdin is not None
    proc.stdin.write(prompt)
    proc.stdin.close()

    threads: list[threading.Thread] = []
    if proc.stdout is not None:
        t = threading.Thread(
            target=_stream_reader,
            args=(proc.stdout, (review_dir / "stdout.log") if review_dir else Path("/tmp/cross-provider-claude-stdout.log"), stdout_chunks, "last_stdout_at", review_dir),
            daemon=True,
        )
        t.start()
        threads.append(t)
    if proc.stderr is not None:
        t = threading.Thread(
            target=_stream_reader,
            args=(proc.stderr, (review_dir / "stderr.log") if review_dir else Path("/tmp/cross-provider-claude-stderr.log"), stderr_chunks, "last_stderr_at", review_dir),
            daemon=True,
        )
        t.start()
        threads.append(t)

    if review_dir is not None:
        mark_phase(review_dir, "reading")

    started = time.time()
    while True:
        rc = proc.poll()
        if review_dir is not None:
            update_runtime(
                review_dir,
                last_heartbeat_at=utc_now(),
                elapsed_sec=round(time.time() - started, 1),
            )
        if rc is not None:
            break
        if time.time() - started > timeout_sec:
            proc.kill()
            if review_dir is not None:
                update_runtime(
                    review_dir,
                    state="failed",
                    phase="timeout",
                    finished_at=utc_now(),
                    error=f"Claude review exceeded timeout {timeout_sec}s",
                )
                append_runtime_event(review_dir, {
                    "ts": utc_now(),
                    "type": "timeout",
                    "timeout_sec": timeout_sec,
                })
            raise RuntimeError(f"Claude review exceeded timeout {timeout_sec}s")
        time.sleep(1)

    for t in threads:
        t.join(timeout=2)

    stdout_text = "".join(stdout_chunks)
    stderr_text = "".join(stderr_chunks)
    raw_events = iter_json_objects(stdout_text)
    if proc.returncode != 0:
        if review_dir is not None:
            update_runtime(
                review_dir,
                state="failed",
                phase="failed",
                finished_at=utc_now(),
                error=stderr_text.strip() or stdout_text.strip() or f"claude exited with code {proc.returncode}",
            )
        raise RuntimeError(stderr_text.strip() or stdout_text.strip() or f"claude exited with code {proc.returncode}")
    result = next((event for event in reversed(raw_events) if event.get("type") == "result"), None)
    if not result:
        if review_dir is not None:
            update_runtime(
                review_dir,
                state="failed",
                phase="failed",
                finished_at=utc_now(),
                error="Could not find Claude result event in stream-json output.",
            )
        raise RuntimeError(f"Could not find Claude result event in stream-json output.\nRaw output:\n{stdout_text}")
    result = {**result, "raw_events": raw_events}
    if review_dir is not None:
        result_text = clean_response(result.get("result", ""))
        update_runtime(
            review_dir,
            state="completed",
            phase="finished",
            finished_at=utc_now(),
            result_preview=result_text[:400],
        )
        append_runtime_event(review_dir, {
            "ts": utc_now(),
            "type": "finished",
            "returncode": proc.returncode,
        })
    return result


def append_log(review_dir: Path, event: dict) -> None:
    with (review_dir / "messages.ndjson").open("a", encoding="utf-8") as f:
        f.write(json.dumps(event, ensure_ascii=False) + "\n")


def read_log(review_dir: Path) -> list[dict]:
    log_path = review_dir / "messages.ndjson"
    if not log_path.exists():
        return []
    events = []
    for line in log_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return events


def runtime_path(review_dir: Path) -> Path:
    return review_dir / "runtime.json"


def runtime_events_path(review_dir: Path) -> Path:
    return review_dir / "runtime.ndjson"


def load_runtime(review_dir: Path) -> dict:
    path = runtime_path(review_dir)
    with RUNTIME_LOCK:
        if not path.exists():
            return {}
        return json.loads(path.read_text(encoding="utf-8"))


def save_runtime(review_dir: Path, runtime: dict) -> None:
    # Х-15: имя временного файла обязано быть уникальным на писателя — участники
    # роя это разные ПРОЦЕССЫ, RUNTIME_LOCK сериализует только потоки одного.
    with RUNTIME_LOCK:
        write_json_atomically(runtime_path(review_dir), runtime)


def append_runtime_event(review_dir: Path, event: dict) -> None:
    with RUNTIME_LOCK:
        with runtime_events_path(review_dir).open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")


def update_runtime(review_dir: Path, **patch: object) -> dict:
    with RUNTIME_LOCK:
        runtime = load_runtime(review_dir)
        runtime.update(patch)
        runtime["updated_at"] = utc_now()
        save_runtime(review_dir, runtime)
        return runtime


def init_runtime(review_dir: Path, *, review_id: str, action: str, timeout_sec: int) -> dict:
    runtime = {
        "review_id": review_id,
        "action": action,
        "state": "queued",
        "phase": "queued",
        "timeout_sec": timeout_sec,
        "created_at": utc_now(),
        "updated_at": utc_now(),
        "started_at": None,
        "finished_at": None,
        "pid": None,
        "last_heartbeat_at": None,
        "last_activity_at": None,
        "last_stdout_at": None,
        "last_stderr_at": None,
        "stdout_log": str((review_dir / "stdout.log").resolve()),
        "stderr_log": str((review_dir / "stderr.log").resolve()),
        "runtime_events": str(runtime_events_path(review_dir).resolve()),
        "error": None,
        "result_preview": None,
        "progress": empty_progress(),
    }
    save_runtime(review_dir, runtime)
    append_runtime_event(review_dir, {
        "ts": utc_now(),
        "type": "runtime-init",
        "state": runtime["state"],
        "phase": runtime["phase"],
        "timeout_sec": timeout_sec,
    })
    return runtime


def mark_phase(review_dir: Path, phase: str, **extra: object) -> None:
    update_runtime(
        review_dir,
        state="running",
        phase=phase,
        last_heartbeat_at=utc_now(),
        **extra,
    )
    append_runtime_event(review_dir, {
        "ts": utc_now(),
        "type": "phase",
        "phase": phase,
        **extra,
    })


def compute_stats(events: list[dict]) -> dict:
    total_cost = 0.0
    input_tokens = 0
    output_tokens = 0
    cache_read_tokens = 0
    cache_creation_tokens = 0
    turns = 0
    progress = empty_progress()
    for event in events:
        raw = event.get("raw") or {}
        usage = raw.get("usage") or {}
        total_cost += float(raw.get("total_cost_usd") or 0.0)
        input_tokens += int(usage.get("input_tokens") or 0)
        output_tokens += int(usage.get("output_tokens") or 0)
        cache_read_tokens += int(usage.get("cache_read_input_tokens") or 0)
        cache_creation_tokens += int(usage.get("cache_creation_input_tokens") or 0)
        turns += int(raw.get("num_turns") or 0)
        raw_events = raw.get("raw_events") if isinstance(raw, dict) else None
        if isinstance(raw_events, list):
            for raw_event in raw_events:
                if isinstance(raw_event, dict):
                    progress = merge_progress_event(progress, raw_event)
    return {
        "events": len(events),
        "turns": turns,
        "total_cost_usd": round(total_cost, 6),
        "input_tokens": input_tokens,
        "output_tokens": output_tokens,
        "cache_read_input_tokens": cache_read_tokens,
        "cache_creation_input_tokens": cache_creation_tokens,
        "raw_events": progress["raw_events"],
        "event_types": progress["event_types"],
        "tool_calls_total": progress["tool_calls_total"],
        "tool_calls_by_name": progress["tool_calls_by_name"],
        "unique_tool_call_ids": len(progress["tool_call_ids"]),
        "tool_result_events": progress["tool_result_events"],
        "permission_denials": progress["permission_denials"],
        "server_tool_use": progress["server_tool_use"],
    }


def load_meta(review_id: str) -> tuple[Path, dict]:
    review_dir = REVIEW_ROOT / review_id
    meta_path = review_dir / "review.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"Unknown review_id: {review_id}")
    return review_dir, json.loads(meta_path.read_text(encoding="utf-8"))


def save_meta(review_dir: Path, meta: dict) -> None:
    # Х-15: тот же дефект фиксированного `.tmp`, что и в save_runtime.
    write_json_atomically(review_dir / "review.json", meta)


def validate_new_review_id(review_id: str) -> Path:
    if not review_id or Path(review_id).name != review_id or review_id in {".", ".."}:
        raise ValueError(f"Unsafe child review id: {review_id!r}")
    review_dir = REVIEW_ROOT / review_id
    if not review_dir.resolve().is_relative_to(REVIEW_ROOT.resolve()):
        raise ValueError(f"Child review id escapes review root: {review_id!r}")
    return review_dir


def validate_fork_identity(operation_id: str, snapshot_digest: str) -> None:
    if not SAFE_OPERATION_ID_RE.fullmatch(operation_id):
        raise ValueError(
            "operation_id must be a non-empty safe ASCII identifier (max 128 characters)"
        )
    if not CANONICAL_SNAPSHOT_DIGEST_RE.fullmatch(snapshot_digest):
        raise ValueError(
            "snapshot_digest must use canonical sha256:<64 lowercase hex> form"
        )


def operation_marker(operation_id: str) -> str:
    return f"[review-harness-operation:{operation_id}]"


def validate_operation_pair(operation_id: str | None, parent_turn_id: str | None) -> None:
    if bool(operation_id) != bool(parent_turn_id):
        raise ValueError(
            "--operation-id and --parent-provider-turn-id must be supplied together"
        )
    if operation_id and not SAFE_OPERATION_ID_RE.fullmatch(operation_id):
        raise ValueError("operation_id is not a safe identifier")


def reconcile_local_operation(review_dir: Path, meta: dict, operation_id: str) -> dict:
    operation = (meta.get("operations") or {}).get(operation_id)
    if not operation:
        return {"state": "absent", "operation_id": operation_id, "evidence_complete": True}
    if operation.get("state") == "completed":
        return {**operation, "evidence_complete": True}
    if operation.get("state") != "pending":
        return {**operation, "state": "ambiguous", "evidence_complete": False}

    stdout_path = review_dir / "stdout.log"
    try:
        raw = stdout_path.read_text(encoding="utf-8")
    except OSError:
        return {**operation, "state": "ambiguous", "evidence_complete": False, "reason": "provider stream unavailable"}
    offset = operation.get("stdout_offset")
    if not isinstance(offset, int) or offset < 0 or offset > len(raw):
        return {**operation, "state": "ambiguous", "evidence_complete": False, "reason": "invalid provider stream offset"}
    results = [
        event for event in iter_json_objects(raw[offset:])
        if event.get("type") == "result" and event.get("session_id")
    ]
    if len(results) != 1:
        return {
            **operation,
            "state": "ambiguous",
            "evidence_complete": False,
            "reason": f"expected one provider result after pending marker, found {len(results)}",
        }
    result = results[0]
    completed = {
        **operation,
        "state": "completed",
        "completed_at": utc_now(),
        "response": clean_response(result.get("result", "")),
        "text": clean_response(result.get("result", "")),
        "provider_turn_id": str(result["session_id"]),
        "session_id": str(result["session_id"]),
        "provider_checkpoint_id": str(result["session_id"]),
        "evidence_complete": True,
    }
    meta.setdefault("operations", {})[operation_id] = completed
    meta["last_response"] = completed["response"]
    meta["provider_turn_id"] = completed["provider_turn_id"]
    meta["updated_at"] = utc_now()
    save_meta(review_dir, meta)
    return completed


def emit_operation_result(result: dict, *, as_json: bool = False) -> None:
    if as_json:
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return
    print(f"operation_id: {result['operation_id']}")
    print(f"operation_state: {result['state']}")
    if result.get("provider_turn_id"):
        print(f"provider_turn_id: {result['provider_turn_id']}")
    if result.get("response") is not None:
        print()
        print(result["response"])
def cmd_capabilities(args: argparse.Namespace) -> int:
    if args.json:
        print(json.dumps(CAPABILITIES, ensure_ascii=False, sort_keys=True))
    else:
        native = CAPABILITIES["native_fork"]
        print(
            "native_fork: supported "
            f"(route={native['route']}, min_cli_version={native['min_cli_version']})"
        )
    return 0


def cmd_fork(args: argparse.Namespace) -> int:
    validate_fork_identity(args.operation_id, args.snapshot_digest)
    parent_dir, parent = load_meta(args.parent_review_id)
    if parent.get("status") != "open":
        raise ValueError(f"Parent review is not open: {args.parent_review_id}")
    parent_session_id = str(parent.get("session_id") or "").strip()
    if not parent_session_id:
        raise ValueError("Parent review has no provider session_id")
    parent_provider_checkpoint_id = str(
        parent.get("provider_checkpoint_id") or parent_session_id
    ).strip()
    if parent_provider_checkpoint_id != parent_session_id:
        raise ValueError(
            "Parent Claude provider_checkpoint_id does not match its resumable session_id"
        )
    provider_checkpoint_id = args.provider_checkpoint_id
    if provider_checkpoint_id != parent_provider_checkpoint_id:
        raise ValueError(
            "provider_checkpoint_id must exactly match the parent Claude session checkpoint"
        )

    child_dir = validate_new_review_id(args.child_review_id)
    child_workspace = child_dir / "workspace"
    parent_workspace = Path(parent.get("workspace_path") or "").resolve()
    if not parent_workspace.is_relative_to(parent_dir.resolve()) or not parent_workspace.is_dir():
        raise ValueError("Parent workspace_path is missing or outside its review sandbox")

    prepare_start_directory(child_dir, allow_lock_tombstone=True)
    provider_started = False
    try:
        shutil.copytree(parent_workspace, child_workspace, symlinks=False)
        init_runtime(
            child_dir,
            review_id=args.child_review_id,
            action="fork",
            timeout_sec=args.timeout_sec,
        )
        mark_phase(child_dir, "forking", parent_review_id=args.parent_review_id)
        prompt_consumed = bool(args.question)
        prompt = args.question or (
            "Continue as an independent advisory reviewer from the inherited Claude session. "
            "Work only inside this copied workspace and remain strictly read-only. "
            "Re-evaluate the inherited evidence independently; do not modify files or treat the "
            "parent review's conclusion as binding."
        )
        save_meta(child_dir, {
            "review_id": args.child_review_id,
            "created_at": utc_now(),
            "updated_at": utc_now(),
            "model": parent["model"],
            "workspace_path": str(child_workspace.resolve()),
            "source_root": parent.get("source_root"),
            "sources": json.loads(json.dumps(parent.get("sources") or [])),
            "session_id": None,
            "provider_checkpoint_id": None,
            "provider_turn_id": None,
            "status": "fork_pending",
            "last_response": "",
            "lineage": {
                "kind": "native_fork",
                "operation_id": args.operation_id,
                "snapshot_digest": args.snapshot_digest,
                "parent_review_id": args.parent_review_id,
                "parent_session_id": parent_session_id,
                "parent_provider_checkpoint_id": provider_checkpoint_id,
                "prompt_consumed": prompt_consumed,
            },
        })
        # `--resume` виден только из проектного каталога, вычисленного из cwd:
        # родительская сессия лежит в slug'е рабочего каталога РОДИТЕЛЯ, а форк
        # исполняется из каталога ребёнка. Переносим линию родителя в проектный
        # каталог ребёнка ДО запуска провайдера; при несовпадении приватного
        # контракта хранения сессий здесь будет отказ с внятной причиной.
        stage_parent_transcript_for_fork(
            parent_workspace=parent_workspace,
            child_workspace=child_workspace,
            parent_session_id=parent_session_id,
        )
        provider_started = True
        result = run_claude(
            prompt,
            model=parent["model"],
            workspace=child_workspace,
            fork_session_id=provider_checkpoint_id,
            review_dir=child_dir,
            timeout_sec=args.timeout_sec,
        )
        child_session_id = str(result.get("session_id") or "").strip()
        if not child_session_id:
            raise RuntimeError("Claude fork result did not report a child session_id")
        if child_session_id == parent_session_id:
            raise RuntimeError("Claude fork reused the parent session_id; independent fork refused")

        result_text = clean_response(result.get("result", ""))
        snapshot_refs = {
            "parent_review_id": args.parent_review_id,
            "parent_session_id": parent_session_id,
            "operation_id": args.operation_id,
            "snapshot_digest": args.snapshot_digest,
            "parent_updated_at": parent.get("updated_at"),
            "parent_workspace_path": str(parent_workspace),
            "sources": json.loads(json.dumps(parent.get("sources") or [])),
        }
        child = {
            "review_id": args.child_review_id,
            "created_at": utc_now(),
            "updated_at": utc_now(),
            "model": parent["model"],
            "workspace_path": str(child_workspace.resolve()),
            "source_root": parent.get("source_root"),
            "sources": snapshot_refs["sources"],
            "question": prompt,
            "brief": "",
            "full_context": bool(parent.get("full_context")),
            "session_id": child_session_id,
            "provider_checkpoint_id": child_session_id,
            "provider_turn_id": child_session_id,
            "tools": DEFAULT_TOOLS,
            "protocol": json.loads(json.dumps(parent.get("protocol") or {})),
            "status": "open",
            "last_response": result_text,
            "lineage": {
                "kind": "native_fork",
                "parent_review_id": args.parent_review_id,
                "parent_session_id": parent_session_id,
                "operation_id": args.operation_id,
                "snapshot_digest": args.snapshot_digest,
                "parent_provider_checkpoint_id": provider_checkpoint_id,
                "provider_route": CAPABILITIES["native_fork"]["route"],
                "prompt_consumed": prompt_consumed,
            },
            "snapshot_refs": snapshot_refs,
        }
        save_meta(child_dir, child)
        append_log(child_dir, {
            "ts": utc_now(),
            "type": "fork",
            "prompt": prompt,
            "response": result_text,
            "session_id": child_session_id,
            "parent_review_id": args.parent_review_id,
            "parent_session_id": parent_session_id,
            "raw": result,
        })
    except BaseException as exc:
        if provider_started:
            try:
                pending = json.loads((child_dir / "review.json").read_text(encoding="utf-8"))
                pending["status"] = "fork_ambiguous"
                pending["updated_at"] = utc_now()
                pending["reconciliation_error"] = str(exc)
                save_meta(child_dir, pending)
                update_runtime(
                    child_dir,
                    state="failed",
                    phase="ambiguous",
                    error=str(exc),
                )
            except Exception:
                pass
        else:
            clear_sandbox_contents_preserving_lock(child_dir)
        raise

    print(f"review_id: {args.child_review_id}")
    print(f"child_review_id: {args.child_review_id}")
    print(f"session_id: {child_session_id}")
    print(f"parent_review_id: {args.parent_review_id}")
    print(f"parent_session_id: {parent_session_id}")
    print(f"workspace: {child_workspace.resolve()}")
    print(f"provider_fork_ref: {child_session_id}")
    print(f"provider_turn_id: {child_session_id}")
    print(f"operation_id: {args.operation_id}")
    print(f"snapshot_digest: {args.snapshot_digest}")
    print(f"provider_checkpoint_id: {provider_checkpoint_id}")
    print(f"prompt_consumed: {str(prompt_consumed).lower()}")
    print()
    print(result_text)
    return 0


def cmd_start(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    review_id = args.review_id or make_review_id()
    review_dir = review_dir_for_id(REVIEW_ROOT, review_id)
    workspace = review_dir / "workspace"
    prepare_start_directory(review_dir, allow_lock_tombstone=bool(args.review_id))
    with invocation_lock(review_dir, review_id=review_id, action="start"):
        try:
            return start_created_review(args, cwd, review_id, review_dir, workspace)
        except BaseException as start_error:
            # The stable lock inode remains as a tombstone: deleting it while
            # held would allow a concurrent process to lock a replacement inode.
            try:
                clear_sandbox_contents_preserving_lock(review_dir)
            except BaseException as cleanup_error:
                try:
                    diagnostic = f"Review sandbox cleanup failed for {review_dir}: {cleanup_error!r}"
                    add_note = getattr(start_error, "add_note", None)
                    if callable(add_note):
                        add_note(diagnostic)
                    else:
                        print(diagnostic, file=sys.stderr)
                except BaseException:
                    pass
            raise


def start_created_review(
    args: argparse.Namespace,
    cwd: Path,
    review_id: str,
    review_dir: Path,
    workspace: Path,
) -> int:
    workspace.mkdir()
    init_runtime(review_dir, review_id=review_id, action="start", timeout_sec=args.timeout_sec)
    mark_phase(review_dir, "copying")

    if args.full_context or not args.paths:
        sources = copy_full_context(cwd, workspace)
    else:
        sources = []
        for raw in args.paths:
            register_source(sources, Path(raw), cwd, workspace=workspace)
        register_source(
            sources,
            cwd / FOCUSED_AGENT_DIR,
            cwd,
            workspace=workspace,
            require_exists=False,
            track_missing=True,
        )

    brief = build_brief(args)
    prompt = brief or args.question

    result = run_claude(prompt, model=args.model, workspace=workspace, review_dir=review_dir, timeout_sec=args.timeout_sec)
    result_text = clean_response(result.get("result", ""))
    meta = {
        "review_id": review_id,
        "created_at": utc_now(),
        "updated_at": utc_now(),
        "model": args.model,
        "workspace_path": str(workspace.resolve()),
        "source_root": str(cwd.resolve()),
        "sources": sources,
        "question": args.question,
        "brief": brief,
        "full_context": bool(args.full_context or not args.paths),
        "session_id": result["session_id"],
        "provider_checkpoint_id": result["session_id"],
        "provider_turn_id": result["session_id"],
        "tools": DEFAULT_TOOLS,
        "protocol": {
            "advisory_only": True,
            "max_rounds_per_issue": 3,
            "final_decision_by_primary_agent": True,
        },
        "status": "open",
        "last_response": result_text,
    }
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": "start",
        "prompt": prompt,
        "response": result_text,
        "session_id": result.get("session_id"),
        "raw": result,
    })

    print(f"review_id: {review_id}")
    print(f"session_id: {result['session_id']}")
    print(f"provider_checkpoint_id: {result['session_id']}")
    print(f"workspace: {workspace.resolve()}")
    stats = compute_stats(read_log(review_dir))
    print(
        f"cost_usd: {stats['total_cost_usd']} | input_tokens: {stats['input_tokens']} | "
        f"output_tokens: {stats['output_tokens']} | cache_read: {stats['cache_read_input_tokens']} | "
        f"cache_create: {stats['cache_creation_input_tokens']} | tool_calls: {stats['tool_calls_total']} | "
        f"tools: {json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    print()
    print(result_text)
    return 0


def cmd_ask(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    validate_operation_pair(args.operation_id, args.parent_provider_turn_id)
    if args.operation_id:
        current_turn = str(meta.get("provider_turn_id") or meta.get("session_id") or "")
        existing = reconcile_local_operation(review_dir, meta, args.operation_id)
        if existing["state"] == "completed":
            emit_operation_result(existing)
            return 0
        if existing["state"] != "absent":
            raise RuntimeError(
                f"Operation {args.operation_id} is {existing['state']}; blind re-ask refused: "
                f"{existing.get('reason', 'provider completion is not provable')}"
            )
        if args.parent_provider_turn_id != current_turn:
            raise ValueError(
                "parent_provider_turn_id does not match the current Claude provider turn"
            )
        stdout_path = review_dir / "stdout.log"
        stdout_offset = stdout_path.stat().st_size if stdout_path.exists() else 0
        meta.setdefault("operations", {})[args.operation_id] = {
            "operation_id": args.operation_id,
            "state": "pending",
            "created_at": utc_now(),
            "parent_provider_turn_id": args.parent_provider_turn_id,
            "stdout_offset": stdout_offset,
            "question": args.question,
        }
        meta["updated_at"] = utc_now()
        save_meta(review_dir, meta)
        prompt = f"{operation_marker(args.operation_id)}\n\n{args.question}"
    else:
        prompt = args.question
    workspace = Path(meta["workspace_path"])
    init_runtime(review_dir, review_id=args.review_id, action="ask", timeout_sec=args.timeout_sec)
    result = run_claude(
        prompt,
        model=meta["model"],
        workspace=workspace,
        session_id=meta["session_id"],
        review_dir=review_dir,
        timeout_sec=args.timeout_sec,
    )
    result_text = clean_response(result.get("result", ""))
    meta["updated_at"] = utc_now()
    meta["session_id"] = result["session_id"]
    meta["provider_checkpoint_id"] = result["session_id"]
    meta["provider_turn_id"] = result["session_id"]
    meta["last_response"] = result_text
    if args.operation_id:
        meta["operations"][args.operation_id] = {
            **meta["operations"][args.operation_id],
            "state": "completed",
            "completed_at": utc_now(),
            "response": result_text,
            "text": result_text,
            "provider_turn_id": result["session_id"],
            "session_id": result["session_id"],
            "provider_checkpoint_id": result["session_id"],
            "evidence_complete": True,
        }
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": "ask",
        "prompt": prompt,
        "response": result_text,
        "session_id": result.get("session_id"),
        "raw": result,
    })
    stats = compute_stats(read_log(review_dir))
    print(
        f"[review {args.review_id}] cost_usd={stats['total_cost_usd']} "
        f"input_tokens={stats['input_tokens']} output_tokens={stats['output_tokens']} "
        f"cache_read={stats['cache_read_input_tokens']} cache_create={stats['cache_creation_input_tokens']} "
        f"tool_calls={stats['tool_calls_total']} tools={json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    if args.operation_id:
        print(f"operation_id: {args.operation_id}")
        print(f"operation_state: completed")
        print(f"provider_turn_id: {result['session_id']}")
    print(result_text)
    return 0


def cmd_reconcile(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    if not SAFE_OPERATION_ID_RE.fullmatch(args.operation_id):
        raise ValueError("operation_id is not a safe identifier")
    operation = (meta.get("operations") or {}).get(args.operation_id)
    if args.parent_provider_turn_id and operation and (
        operation.get("parent_provider_turn_id") != args.parent_provider_turn_id
    ):
        raise ValueError("parent_provider_turn_id does not match the persisted operation")
    result = reconcile_local_operation(review_dir, meta, args.operation_id)
    if result["state"] == "absent":
        fork_matches: list[dict] = []
        for candidate in REVIEW_ROOT.iterdir():
            candidate_meta_path = candidate / "review.json"
            if not candidate_meta_path.is_file():
                continue
            try:
                candidate_meta = json.loads(candidate_meta_path.read_text(encoding="utf-8"))
            except (OSError, json.JSONDecodeError):
                continue
            lineage = candidate_meta.get("lineage") or {}
            if lineage.get("operation_id") == args.operation_id:
                fork_matches.append(candidate_meta)
        if len(fork_matches) == 1:
            child = fork_matches[0]
            completed = child.get("status") == "open" and bool(child.get("session_id"))
            result = {
                "operation_id": args.operation_id,
                "state": "completed" if completed else "ambiguous",
                "evidence_complete": completed,
                "child_review_id": child.get("review_id"),
                "session_id": child.get("session_id"),
                "provider_turn_id": child.get("provider_turn_id"),
                "provider_checkpoint_id": child.get("provider_checkpoint_id"),
                "text": child.get("last_response") if completed else None,
                "response": child.get("last_response") if completed else None,
                "reason": None if completed else "provider fork may have committed; local reducer is incomplete",
            }
        elif len(fork_matches) > 1:
            result = {
                "operation_id": args.operation_id,
                "state": "ambiguous",
                "evidence_complete": False,
                "reason": "multiple child sandboxes claim the same operation_id",
            }
    emit_operation_result(result, as_json=args.json)
    return 0 if result["state"] in {"completed", "absent"} else 1


def cmd_debate(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    workspace = Path(meta["workspace_path"])
    prompt = (
        "This is round-2 or round-3 issue debate under the agreed review protocol.\n\n"
        f"Issue:\n{args.issue}\n\n"
        f"Opus finding to evaluate:\n{args.finding}\n\n"
        f"Primary agent position:\n{args.position}\n\n"
        "Instructions:\n"
        "- Assess only this issue.\n"
        "- State one of: agree / partial / disagree / withdrawn / out_of_scope.\n"
        "- If you disagree with the primary agent, explain why briefly.\n"
        "- If the primary agent's argument resolves your concern, say so explicitly.\n"
        "- Do not expand into unrelated new findings.\n"
        "- Respect the max-three-rounds-per-issue rule.\n"
    )
    init_runtime(review_dir, review_id=args.review_id, action="debate", timeout_sec=args.timeout_sec)
    result = run_claude(
        prompt,
        model=meta["model"],
        workspace=workspace,
        session_id=meta["session_id"],
        review_dir=review_dir,
        timeout_sec=args.timeout_sec,
    )
    result_text = clean_response(result.get("result", ""))
    meta["updated_at"] = utc_now()
    meta["session_id"] = result["session_id"]
    meta["provider_checkpoint_id"] = result["session_id"]
    meta["last_response"] = result_text
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": "debate",
        "prompt": prompt,
        "response": result_text,
        "session_id": result.get("session_id"),
        "raw": result,
    })
    stats = compute_stats(read_log(review_dir))
    print(
        f"[review {args.review_id}] cost_usd={stats['total_cost_usd']} "
        f"input_tokens={stats['input_tokens']} output_tokens={stats['output_tokens']} "
        f"cache_read={stats['cache_read_input_tokens']} cache_create={stats['cache_creation_input_tokens']} "
        f"tool_calls={stats['tool_calls_total']} tools={json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    print(result_text)
    return 0


def cmd_sync(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    sync_sources(meta, review_dir)
    meta["updated_at"] = utc_now()
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": "sync",
        "prompt": "",
        "response": "Workspace synced from source paths.",
        "session_id": meta["session_id"],
    })
    print(f"Synced review workspace: {meta['workspace_path']}")
    return 0


def cmd_show(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    payload = {
        "meta": meta,
        "stats": compute_stats(read_log(review_dir)),
        "runtime": load_runtime(review_dir),
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


def cmd_status(args: argparse.Namespace) -> int:
    review_dir = REVIEW_ROOT / args.review_id
    if not review_dir.exists():
        raise FileNotFoundError(f"Unknown review_id: {args.review_id}")
    meta_path = review_dir / "review.json"
    meta = json.loads(meta_path.read_text(encoding="utf-8")) if meta_path.exists() else None
    runtime = load_runtime(review_dir)
    payload = {
        "review_id": args.review_id,
        "status": meta.get("status") if meta is not None else "starting",
        "runtime": runtime,
        "stats": compute_stats(read_log(review_dir)),
    }
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    return 0


def cmd_log(args: argparse.Namespace) -> int:
    review_dir, _ = load_meta(args.review_id)
    events = read_log(review_dir)
    if args.json:
        print(json.dumps(events, ensure_ascii=False, indent=2))
        return 0
    for i, event in enumerate(events, 1):
        print(f"[{i}] {event.get('ts','')} {event.get('type','')}")
        prompt = event.get("prompt", "").strip()
        response = event.get("response", "").strip()
        if prompt:
            print("Prompt:")
            print(prompt)
        if response:
            print("Response:")
            print(response)
        print()
    return 0


def cmd_stats(args: argparse.Namespace) -> int:
    review_dir, _ = load_meta(args.review_id)
    print(json.dumps(compute_stats(read_log(review_dir)), ensure_ascii=False, indent=2))
    return 0


def cmd_close(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    meta["status"] = "closed"
    meta["updated_at"] = utc_now()
    save_meta(review_dir, meta)
    if args.keep_sandbox:
        print(f"Marked review as closed: {args.review_id}")
    else:
        clear_sandbox_contents_preserving_lock(review_dir)
        print(f"Closed review sandbox and retained invocation lock: {args.review_id}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Session-based Claude Opus review runner with isolated sandbox workspace."
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_capabilities = sub.add_parser("capabilities", help="Report machine-readable adapter capabilities.")
    p_capabilities.add_argument("--json", action="store_true", help="Emit the capability document as JSON.")
    p_capabilities.set_defaults(func=cmd_capabilities)

    p_fork = sub.add_parser("fork", help="Create an independent native Claude fork from an existing review.")
    p_fork.add_argument("parent_review_id", help="Existing parent review id.")
    p_fork.add_argument("--child-review-id", required=True, help="New review id for the independent child sandbox.")
    p_fork.add_argument("--operation-id", required=True, help="Harness fork operation id preserved in child lineage.")
    p_fork.add_argument("--snapshot-digest", required=True, help="Logical source snapshot digest preserved in child lineage.")
    p_fork.add_argument("--provider-checkpoint-id", required=True, help="Exact parent Claude checkpoint; must equal its provider session id.")
    p_fork.add_argument("--question", help="Optional first task prompt for the child fork.")
    p_fork.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for the fork invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_fork.set_defaults(func=cmd_fork)

    p_start = sub.add_parser("start", help="Create a new review sandbox and start a Claude review session.")
    p_start.add_argument("--question", required=True, help="Initial review question.")
    p_start.add_argument("--model", default=DEFAULT_MODEL, help=f"Claude model alias or full name. Default: {DEFAULT_MODEL}")
    p_start.add_argument("--review-id", help="Optional custom review id.")
    p_start.add_argument("--full-context", action="store_true", help="Copy the whole working directory into the review sandbox, excluding known heavy/generated dirs.")
    p_start.add_argument("--task", help="Short statement of the task being worked on.")
    p_start.add_argument("--goal", help="Desired outcome / business goal.")
    p_start.add_argument("--requirements", help="Important requirements to satisfy.")
    p_start.add_argument("--constraints", help="Important constraints or things that must not change.")
    p_start.add_argument("--primary-target", help="Primary artifact or output to review.")
    p_start.add_argument("--changed-files", nargs="*", help="List of changed files or files most relevant to the review.")
    p_start.add_argument("--open-concerns", help="Known doubts, risks, or unresolved questions.")
    p_start.add_argument("--review-ask", help="Specific type of review requested from Opus.")
    p_start.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Claude invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_start.add_argument("paths", nargs="*", help="Files or directories to copy into the review sandbox. If omitted, full context is used.")
    p_start.set_defaults(func=cmd_start)

    p_ask = sub.add_parser("ask", help="Ask a follow-up question in an existing review session.")
    p_ask.add_argument("review_id", help="Existing review id.")
    p_ask.add_argument("--question", required=True, help="Follow-up question.")
    p_ask.add_argument("--operation-id", help="Idempotency operation id; requires --parent-provider-turn-id.")
    p_ask.add_argument("--parent-provider-turn-id", help="Expected current provider turn; requires --operation-id.")
    p_ask.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Claude invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_ask.set_defaults(func=cmd_ask)

    p_reconcile = sub.add_parser("reconcile", help="Reconcile an operation without issuing a new provider turn.")
    p_reconcile.add_argument("review_id", help="Existing review id.")
    p_reconcile.add_argument("--operation-id", required=True, help="Persisted operation id.")
    p_reconcile.add_argument("--parent-provider-turn-id", help="Optional expected parent provider turn.")
    p_reconcile.add_argument("--json", action="store_true", help="Emit reconciliation result as JSON.")
    p_reconcile.set_defaults(func=cmd_reconcile)

    p_debate = sub.add_parser("debate", help="Run a structured response-to-finding step under the three-round protocol.")
    p_debate.add_argument("review_id", help="Existing review id.")
    p_debate.add_argument("--issue", required=True, help="Short issue identifier or label.")
    p_debate.add_argument("--finding", required=True, help="The Opus finding being challenged or clarified.")
    p_debate.add_argument("--position", required=True, help="Codex's argument or position on the issue.")
    p_debate.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Claude invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_debate.set_defaults(func=cmd_debate)

    p_sync = sub.add_parser("sync", help="Refresh sandbox contents from the original source paths.")
    p_sync.add_argument("review_id", help="Existing review id.")
    p_sync.set_defaults(func=cmd_sync)

    p_show = sub.add_parser("show", help="Show review metadata.")
    p_show.add_argument("review_id", help="Existing review id.")
    p_show.set_defaults(func=cmd_show)

    p_status = sub.add_parser("status", help="Show current runtime status, phase, heartbeat and timeout policy.")
    p_status.add_argument("review_id", help="Existing review id.")
    p_status.set_defaults(func=cmd_status)

    p_log = sub.add_parser("log", help="Show review prompt/response history.")
    p_log.add_argument("review_id", help="Existing review id.")
    p_log.add_argument("--json", action="store_true", help="Print raw event log as JSON.")
    p_log.set_defaults(func=cmd_log)

    p_stats = sub.add_parser("stats", help="Show cumulative token and cost stats for the review session.")
    p_stats.add_argument("review_id", help="Existing review id.")
    p_stats.set_defaults(func=cmd_stats)

    p_close = sub.add_parser("close", help="Close a review and delete its sandbox by default.")
    p_close.add_argument("review_id", help="Existing review id.")
    p_close.add_argument("--keep-sandbox", action="store_true", help="Keep the stored review sandbox after closing.")
    p_close.set_defaults(func=cmd_close)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    REVIEW_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        if args.cmd in {"ask", "debate", "sync", "fork", "close"}:
            review_id = (
                args.parent_review_id if args.cmd == "fork" else args.review_id
            )
            review_dir = review_dir_for_id(REVIEW_ROOT, review_id)
            with invocation_lock(
                review_dir,
                review_id=review_id,
                action=args.cmd,
            ):
                return args.func(args)
        return args.func(args)
    except SessionBusyError as e:
        print("error_kind: session_busy", file=sys.stderr)
        print(f"Error: {e}", file=sys.stderr)
        return 1
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

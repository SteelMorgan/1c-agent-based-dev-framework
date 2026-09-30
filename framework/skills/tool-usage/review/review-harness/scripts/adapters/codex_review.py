#!/usr/bin/env python3
from __future__ import annotations

import argparse
import importlib.util
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
from typing import Any



def _load_local_module(name: str, path: Path):
    """Load one repository sibling without relying on the caller's sys.path."""
    resolved = path.resolve(strict=True)
    spec = importlib.util.spec_from_file_location(name, resolved)
    if spec is None or spec.loader is None:
        raise ImportError(f"Cannot load required local module: {resolved}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


_ADAPTER_DIR = Path(__file__).resolve().parent
_codex_app_server = _load_local_module(
    "_gbig_review_harness_codex_app_server", _ADAPTER_DIR / "codex_app_server.py"
)
fork_thread = _codex_app_server.fork_thread
inspect_thread_operation = _codex_app_server.inspect_thread_operation
list_child_threads = _codex_app_server.list_child_threads
read_thread_checkpoint = _codex_app_server.read_thread_checkpoint

_invocation_lock = _load_local_module(
    "_gbig_review_harness_invocation_lock", _ADAPTER_DIR.parent / "invocation_lock.py"
)
SessionBusyError = _invocation_lock.SessionBusyError
clear_sandbox_contents_preserving_lock = _invocation_lock.clear_sandbox_contents_preserving_lock
invocation_lock = _invocation_lock.invocation_lock
prepare_start_directory = _invocation_lock.prepare_start_directory
review_dir_for_id = _invocation_lock.review_dir_for_id

_runtime_store = _load_local_module(
    "_gbig_review_harness_runtime_store", _ADAPTER_DIR.parent / "runtime_store.py"
)
write_json_atomically = _runtime_store.write_json_atomically


REVIEW_ROOT = Path(".review-sandboxes")
DEFAULT_MODEL = "gpt-5.6-sol"  # DEC-M05-015: cross-family ревью явно на gpt-5.6-sol high, не полагаемся на ~/.codex/config.toml
DEFAULT_REASONING_EFFORT = "high"
DEFAULT_SANDBOX = "read-only"
DEFAULT_TIMEOUT_SEC = 1800
FOCUSED_AGENT_DIR = ".codex"
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
SESSION_KEY_RE = re.compile(r"^(session[_-]?id|conversation[_-]?id|thread[_-]?id)$", re.I)
UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
    re.I,
)

FALLBACK_REVIEW_PROMPT = """You are an advisory reviewer operating in an isolated review context.

IMPORTANT: Do NOT create, modify, or delete any project files. You may only READ files for analysis.

Provide a second opinion, not the final authority. Order findings by severity: BLOCK, WARN, INFO.
Assign stable finding IDs F-01, F-02, ... and include file:line evidence where possible.
"""


def load_review_prompt() -> str:
    try:
        return REVIEW_PROMPT_PATH.read_text(encoding="utf-8").strip()
    except OSError:
        return FALLBACK_REVIEW_PROMPT.strip()


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
        # symlink (зеркало .codex/skills/<skill>), на диске — реальный каталог
        # (зеркала правил .codex/skills/<rule>/SKILL.md). copy2 на каталоге
        # ронял весь start участника (IsADirectoryError → unresponsive): дерево
        # копируется рекурсивно — содержимое материализуется, пакет самодостаточен.
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


def make_source_entry(src: Path, source_root: Path) -> dict[str, str]:
    dest_rel = safe_rel_to_cwd(src, source_root)
    return {
        "source": str(src.resolve()),
        "dest_rel": dest_rel.as_posix(),
    }


def register_source(
    sources: list[dict[str, str]],
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


def should_exclude(path: Path, source_root: Path) -> bool:
    try:
        rel = path.resolve().relative_to(source_root.resolve())
        parts = set(rel.parts)
    except Exception:
        parts = set(path.parts)
    if parts & DEFAULT_EXCLUDES:
        return True
    return any(part.startswith(DEFAULT_EXCLUDE_PREFIXES) for part in parts)


def copy_full_context(source_root: Path, workspace: Path) -> list[dict[str, str]]:
    sources: list[dict[str, str]] = []
    for child in sorted(source_root.iterdir()):
        if should_exclude(child, source_root):
            continue
        register_source(sources, child, source_root, workspace=workspace)
    return sources


def copy_frozen_workspace(source: Path, destination: Path) -> None:
    """Clone a review workspace without retaining links to mutable/external state."""
    source = source.resolve(strict=True)
    destination.mkdir(parents=True, exist_ok=True)
    for current_raw, dir_names, file_names in os.walk(source, followlinks=False):
        current = Path(current_raw)
        relative = current.relative_to(source)
        target_dir = destination / relative
        target_dir.mkdir(parents=True, exist_ok=True)
        for name in list(dir_names):
            entry = current / name
            if entry.is_symlink():
                copy_symlink_if_safe(entry, target_dir / name, source)
                dir_names.remove(name)
        for name in file_names:
            entry = current / name
            target = target_dir / name
            if entry.is_symlink():
                copy_symlink_if_safe(entry, target, source)
            else:
                copy_file_if_present(entry, target)


def sync_sources(meta: dict[str, Any], review_dir: Path) -> None:
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


def build_brief(args: argparse.Namespace) -> str:
    sections: list[str] = []
    if args.task:
        sections.append(f"# Task\n{args.task}")
    if args.goal:
        sections.append(f"# Goal\n{args.goal}")
    artifact_lines: list[str] = []
    if args.artifact_type:
        artifact_lines.append(f"Type: {args.artifact_type}")
    if args.primary_target:
        artifact_lines.append(f"Primary target: {args.primary_target}")
    if args.changed_files:
        artifact_lines.append("Changed or relevant files:\n" + "\n".join(f"- {p}" for p in args.changed_files))
    if artifact_lines:
        sections.append("# Artifact\n" + "\n".join(artifact_lines))
    if args.requirements:
        sections.append(f"# Requirements / Criteria\n{args.requirements}")
    if args.skills:
        sections.append(
            "# Relevant Skills / Rules\nRead these files and use them as review criteria:\n"
            + "\n".join(f"- {p}" for p in args.skills)
        )
    context_lines: list[str] = []
    if args.open_concerns:
        context_lines.append(f"Open concerns:\n{args.open_concerns}")
    if args.constraints:
        context_lines.append(f"Constraints:\n{args.constraints}")
    if context_lines:
        sections.append("# Context\n" + "\n\n".join(context_lines))
    review_ask = args.review_ask or args.question
    if review_ask:
        sections.append(f"# Review Ask\n{review_ask}")
    return "\n\n".join(sections).strip()


def build_prompt(args: argparse.Namespace) -> str:
    brief = build_brief(args)
    prompt = brief or args.question
    return f"{load_review_prompt()}\n\n{prompt}".strip()


def runtime_path(review_dir: Path) -> Path:
    return review_dir / "runtime.json"


def runtime_events_path(review_dir: Path) -> Path:
    return review_dir / "runtime.ndjson"


def load_runtime(review_dir: Path) -> dict[str, Any]:
    path = runtime_path(review_dir)
    with RUNTIME_LOCK:
        if not path.exists():
            return {}
        return json.loads(path.read_text(encoding="utf-8"))


def save_runtime(review_dir: Path, runtime: dict[str, Any]) -> None:
    # Х-15: имя временного файла обязано быть уникальным на писателя — участники
    # роя это разные ПРОЦЕССЫ, RUNTIME_LOCK сериализует только потоки одного.
    with RUNTIME_LOCK:
        write_json_atomically(runtime_path(review_dir), runtime)


def append_runtime_event(review_dir: Path, event: dict[str, Any]) -> None:
    with RUNTIME_LOCK:
        with runtime_events_path(review_dir).open("a", encoding="utf-8") as f:
            f.write(json.dumps(event, ensure_ascii=False) + "\n")


def update_runtime(review_dir: Path, **patch: Any) -> dict[str, Any]:
    with RUNTIME_LOCK:
        runtime = load_runtime(review_dir)
        runtime.update(patch)
        runtime["updated_at"] = utc_now()
        save_runtime(review_dir, runtime)
        return runtime


def init_runtime(review_dir: Path, *, review_id: str, action: str, timeout_sec: int) -> dict[str, Any]:
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
        "stdout_log": str((review_dir / "stdout.jsonl").resolve()),
        "stderr_log": str((review_dir / "stderr.log").resolve()),
        "result_file": str((review_dir / "last-response.md").resolve()),
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


def mark_phase(review_dir: Path, phase: str, **extra: Any) -> None:
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


def append_log(review_dir: Path, event: dict[str, Any]) -> None:
    with (review_dir / "messages.ndjson").open("a", encoding="utf-8") as f:
        f.write(json.dumps(event, ensure_ascii=False) + "\n")


def read_log(review_dir: Path) -> list[dict[str, Any]]:
    log_path = review_dir / "messages.ndjson"
    if not log_path.exists():
        return []
    events: list[dict[str, Any]] = []
    for line in log_path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return events


def load_meta(review_id: str) -> tuple[Path, dict[str, Any]]:
    review_dir = REVIEW_ROOT / review_id
    meta_path = review_dir / "review.json"
    if not meta_path.exists():
        raise FileNotFoundError(f"Unknown review_id: {review_id}")
    return review_dir, json.loads(meta_path.read_text(encoding="utf-8"))


def save_meta(review_dir: Path, meta: dict[str, Any]) -> None:
    # Х-15: тот же дефект фиксированного `.tmp`, что и в save_runtime.
    write_json_atomically(review_dir / "review.json", meta)


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


def iter_json_objects(text: str) -> list[dict[str, Any]]:
    objects: list[dict[str, Any]] = []
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


def find_session_id(value: Any) -> str | None:
    if isinstance(value, dict):
        prioritized: list[str] = []
        fallback: list[str] = []
        for key, item in value.items():
            if isinstance(item, str) and SESSION_KEY_RE.match(str(key)):
                if UUID_RE.match(item):
                    prioritized.append(item)
                else:
                    fallback.append(item)
        if prioritized:
            return prioritized[0]
        for item in value.values():
            found = find_session_id(item)
            if found:
                return found
        if fallback:
            return fallback[0]
    elif isinstance(value, list):
        for item in value:
            found = find_session_id(item)
            if found:
                return found
    return None


def extract_usage(value: Any, stats: dict[str, int]) -> None:
    if isinstance(value, dict):
        usage = value.get("usage")
        if isinstance(usage, dict):
            for source, target in {
                "input_tokens": "input_tokens",
                "output_tokens": "output_tokens",
                "total_tokens": "total_tokens",
                "cached_input_tokens": "cached_input_tokens",
                "reasoning_tokens": "reasoning_tokens",
            }.items():
                raw = usage.get(source)
                if isinstance(raw, int):
                    stats[target] += raw
        for item in value.values():
            extract_usage(item, stats)
    elif isinstance(value, list):
        for item in value:
            extract_usage(item, stats)


def event_type_name(raw: dict[str, Any]) -> str:
    raw_type = str(raw.get("type") or "unknown")
    item = raw.get("item")
    if isinstance(item, dict):
        return f"{raw_type}:{item.get('type') or 'unknown'}"
    return raw_type


def tool_name_from(value: dict[str, Any]) -> str:
    function_name = value.get("function", {}).get("name") if isinstance(value.get("function"), dict) else ""
    return str(value.get("name") or value.get("tool_name") or value.get("tool") or function_name).strip() or "unknown"


def collect_tool_calls(value: Any) -> list[dict[str, Any]]:
    calls: list[dict[str, Any]] = []
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


def count_tool_results(value: Any) -> int:
    if isinstance(value, dict):
        raw_type = str(value.get("type") or "")
        total = 1 if raw_type in {"tool_result", "function_call_output"} or (raw_type.endswith("_execution") and value.get("status") == "completed") else 0
        return total + sum(count_tool_results(item) for item in value.values() if isinstance(item, (dict, list)))
    if isinstance(value, list):
        return sum(count_tool_results(item) for item in value)
    return 0


def count_permission_denials(value: Any) -> int:
    if isinstance(value, dict):
        total = 0
        denials = value.get("permission_denials")
        if isinstance(denials, list):
            total += len(denials)
        return total + sum(count_permission_denials(item) for item in value.values() if isinstance(item, (dict, list)))
    if isinstance(value, list):
        return sum(count_permission_denials(item) for item in value)
    return 0


def extract_server_tool_use(value: Any) -> dict[str, int]:
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


def empty_progress() -> dict[str, Any]:
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


def merge_progress_event(progress: dict[str, Any], raw: dict[str, Any]) -> dict[str, Any]:
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


def compute_stats(events: list[dict[str, Any]]) -> dict[str, Any]:
    stats = {
        "events": len(events),
        "input_tokens": 0,
        "output_tokens": 0,
        "total_tokens": 0,
        "cached_input_tokens": 0,
        "reasoning_tokens": 0,
        "raw_events": 0,
        "event_types": {},
        "tool_calls_total": 0,
        "tool_calls_by_name": {},
        "unique_tool_call_ids": 0,
        "tool_result_events": 0,
        "permission_denials": 0,
        "server_tool_use": {},
    }
    progress = empty_progress()
    for event in events:
        for raw in event.get("raw_events", []):
            extract_usage(raw, stats)
            if isinstance(raw, dict):
                progress = merge_progress_event(progress, raw)
    stats.update({
        "raw_events": progress["raw_events"],
        "event_types": progress["event_types"],
        "tool_calls_total": progress["tool_calls_total"],
        "tool_calls_by_name": progress["tool_calls_by_name"],
        "unique_tool_call_ids": len(progress["tool_call_ids"]),
        "tool_result_events": progress["tool_result_events"],
        "permission_denials": progress["permission_denials"],
        "server_tool_use": progress["server_tool_use"],
    })
    return stats


def write_review_context(workspace: Path, *, review_id: str, brief: str, sources: list[dict[str, str]]) -> None:
    context = [
        "# Review Context",
        "",
        f"Review ID: {review_id}",
        "",
        "This file is generated inside the isolated review workspace.",
        "",
        "## Brief",
        "",
        brief or "(No structured brief supplied.)",
        "",
        "## Copied Sources",
        "",
    ]
    if sources:
        context.extend(f"- `{entry['dest_rel']}` from `{entry['source']}`" for entry in sources)
    else:
        context.append("(No copied sources.)")
    (workspace / "REVIEW_CONTEXT.md").write_text("\n".join(context) + "\n", encoding="utf-8")


def run_codex(
    prompt: str,
    *,
    model: str | None,
    reasoning_effort: str,
    sandbox: str,
    workspace: Path | None,
    session_id: str | None,
    review_dir: Path,
    timeout_sec: int,
) -> dict[str, Any]:
    result_file = review_dir / "last-response.md"
    stdout_log = review_dir / "stdout.jsonl"
    stderr_log = review_dir / "stderr.log"

    # Если модель не задана явно (--model), не навязываем её: codex берёт дефолт из ~/.codex/config.toml.
    model_args = ["-m", model] if model else []

    if session_id:
        if workspace is None:
            raise ValueError("workspace is required when resuming a Codex review")
        cmd = [
            "codex",
            "exec",
            "resume",
            *model_args,
            "-c",
            f'model_reasoning_effort="{reasoning_effort}"',
            "-c",
            f'sandbox_mode="{sandbox}"',
            "--skip-git-repo-check",
            "--json",
            "-o",
            str(result_file.resolve()),
            session_id,
            "-",
        ]
        cwd = workspace
    else:
        if workspace is None:
            raise ValueError("workspace is required when starting a new Codex review")
        cmd = [
            "codex",
            "exec",
            *model_args,
            "-c",
            f'model_reasoning_effort="{reasoning_effort}"',
            "--sandbox",
            sandbox,
            "--skip-git-repo-check",
            "--json",
            "-o",
            str(result_file.resolve()),
            "-C",
            str(workspace.resolve()),
            "-",
        ]
        cwd = workspace

    stdout_chunks: list[str] = []
    stderr_chunks: list[str] = []
    proc = subprocess.Popen(
        cmd,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        cwd=cwd,
        bufsize=1,
        env=os.environ.copy(),
    )
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
            args=(proc.stdout, stdout_log, stdout_chunks, "last_stdout_at", review_dir),
            daemon=True,
        )
        t.start()
        threads.append(t)
    if proc.stderr is not None:
        t = threading.Thread(
            target=_stream_reader,
            args=(proc.stderr, stderr_log, stderr_chunks, "last_stderr_at", review_dir),
            daemon=True,
        )
        t.start()
        threads.append(t)

    mark_phase(review_dir, "running")
    started = time.time()
    while True:
        rc = proc.poll()
        update_runtime(
            review_dir,
            last_heartbeat_at=utc_now(),
            elapsed_sec=round(time.time() - started, 1),
        )
        if rc is not None:
            break
        if time.time() - started > timeout_sec:
            proc.kill()
            update_runtime(
                review_dir,
                state="failed",
                phase="timeout",
                finished_at=utc_now(),
                error=f"Codex review exceeded timeout {timeout_sec}s",
            )
            append_runtime_event(review_dir, {
                "ts": utc_now(),
                "type": "timeout",
                "timeout_sec": timeout_sec,
            })
            raise RuntimeError(f"Codex review exceeded timeout {timeout_sec}s")
        time.sleep(1)

    for thread in threads:
        thread.join(timeout=2)

    stdout_text = "".join(stdout_chunks)
    stderr_text = "".join(stderr_chunks)
    raw_events = iter_json_objects(stdout_text)
    response_text = result_file.read_text(encoding="utf-8").strip() if result_file.exists() else ""
    detected_session_id = session_id or find_session_id(raw_events)

    if proc.returncode != 0:
        error = stderr_text.strip() or stdout_text.strip() or f"codex exited with code {proc.returncode}"
        update_runtime(
            review_dir,
            state="failed",
            phase="failed",
            finished_at=utc_now(),
            error=error[:2000],
        )
        raise RuntimeError(error)

    if not response_text:
        response_text = "(Codex completed without writing a final response file.)"
    if not detected_session_id:
        raise RuntimeError("Codex completed without a provider thread id")
    provider_checkpoint_id = read_thread_checkpoint(detected_session_id)
    update_runtime(
        review_dir,
        state="completed",
        phase="finished",
        finished_at=utc_now(),
        result_preview=response_text[:400],
    )
    append_runtime_event(review_dir, {
        "ts": utc_now(),
        "type": "finished",
        "returncode": proc.returncode,
        "session_id": detected_session_id,
        "provider_checkpoint_id": provider_checkpoint_id,
        "provider_turn_id": provider_checkpoint_id,
    })
    return {
        "session_id": detected_session_id,
        "provider_checkpoint_id": provider_checkpoint_id,
        "provider_turn_id": provider_checkpoint_id,
        "response": response_text,
        "raw_events": raw_events,
        "stderr": stderr_text,
        "command": cmd,
    }


def cmd_start(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    review_id = args.review_id or make_review_id()
    review_dir = REVIEW_ROOT / review_id
    workspace = review_dir / "workspace"
    prepare_start_directory(review_dir, allow_lock_tombstone=bool(args.review_id))
    try:
        with invocation_lock(review_dir, review_id=review_id, action="start"):
            return start_created_review(args, cwd, review_id, review_dir, workspace)
    except BaseException as start_error:
        # Keep the stable invocation.lock inode as a failed-start tombstone;
        # payload rollback must not reopen an inode-replacement race.
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
                # Diagnostics are best-effort; rollback must never replace the
                # exception that made start fail.
                pass
        raise


def cmd_fork(args: argparse.Namespace) -> int:
    parent_dir, parent_meta = load_meta(args.parent_review_id)
    if parent_meta.get("status") != "open":
        raise ValueError(f"Parent review is not open: {args.parent_review_id}")
    parent_session_id = parent_meta.get("session_id")
    if not isinstance(parent_session_id, str) or not parent_session_id:
        raise ValueError("Parent review has no provider session_id")
    parent_checkpoint_id = parent_meta.get("provider_checkpoint_id")
    if not isinstance(parent_checkpoint_id, str) or not parent_checkpoint_id:
        raise ValueError("Parent review has no sealed provider_checkpoint_id")
    if args.provider_checkpoint_id != parent_checkpoint_id:
        raise ValueError(
            "Requested checkpoint does not match the sealed Codex provider head: "
            f"requested={args.provider_checkpoint_id!r}, provider={parent_checkpoint_id!r}"
        )
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", args.snapshot_digest):
        raise ValueError("snapshot_digest must be sha256:<64 lowercase hex>")
    if not args.operation_id.strip():
        raise ValueError("operation_id must be non-empty")

    child_review_id = args.child_review_id
    child_dir = REVIEW_ROOT / child_review_id
    child_workspace = child_dir / "workspace"
    lock_only_tombstone = child_dir.exists() and all(
        entry.name == "invocation.lock" for entry in child_dir.iterdir()
    )
    if child_dir.exists() and not lock_only_tombstone:
        _, existing = load_meta(child_review_id)
        if existing.get("operation_id") != args.operation_id:
            raise RuntimeError("Existing child review belongs to a different operation")
        child_id = existing.get("provider_fork_ref") or existing.get("session_id")
        if not child_id:
            baseline = set(existing.get("provider_children_before") or [])
            current = list_child_threads(parent_session_id, timeout_sec=min(args.timeout_sec, 30))
            candidates = [item.get("id") for item in current if item.get("id") not in baseline]
            candidates = [item for item in candidates if isinstance(item, str)]
            if len(candidates) != 1:
                existing.update({"status": "ambiguous", "updated_at": utc_now(), "reconciliation_candidates": candidates})
                save_meta(child_dir, existing)
                raise RuntimeError("Codex fork outcome is ambiguous; re-fork is forbidden")
            child_id = candidates[0]
            existing.update({"session_id": child_id, "provider_fork_ref": child_id, "status": "forked_unreconciled", "updated_at": utc_now()})
            save_meta(child_dir, existing)
        payload = {
            "review_id": child_review_id, "session_id": child_id,
            "parent_review_id": args.parent_review_id, "parent_session_id": parent_session_id,
            "workspace": str(child_workspace.resolve()), "provider_fork_ref": child_id,
            "checkpoint_id": args.checkpoint_id, "provider_checkpoint_id": parent_checkpoint_id,
            "provider_turn_id": parent_checkpoint_id, "operation_id": args.operation_id,
            "snapshot_digest": args.snapshot_digest, "prompt_consumed": False,
        }
        if args.json:
            print(json.dumps(payload, ensure_ascii=False))
        else:
            for key, value in payload.items():
                print(f"{key}: {str(value).lower() if isinstance(value, bool) else value}")
        return 0
    prepare_start_directory(child_dir, allow_lock_tombstone=True)
    provider_result: dict[str, str] | None = None
    try:
        parent_workspace = Path(parent_meta["workspace_path"])
        if not parent_workspace.resolve().is_relative_to(parent_dir.resolve()):
            raise ValueError("Parent workspace_path escapes its review sandbox")
        # Safe internal links resolve inside the frozen child tree; links
        # escaping the parent workspace are rejected instead of retaining
        # mutable/external aliases.
        copy_frozen_workspace(parent_workspace, child_workspace)
        children_before = list_child_threads(parent_session_id, timeout_sec=min(args.timeout_sec, 30))
        pending_meta = {
            "review_id": child_review_id,
            "created_at": utc_now(),
            "updated_at": utc_now(),
            "workspace_path": str(child_workspace.resolve()),
            "parent_review_id": args.parent_review_id,
            "parent_session_id": parent_session_id,
            "checkpoint_id": args.checkpoint_id,
            "provider_checkpoint_id": parent_checkpoint_id,
            "provider_turn_id": parent_checkpoint_id,
            "operation_id": args.operation_id,
            "snapshot_digest": args.snapshot_digest,
            "fork_route": "native",
            "status": "forking",
            "provider_children_before": [item.get("id") for item in children_before if isinstance(item.get("id"), str)],
        }
        save_meta(child_dir, pending_meta)
        provider_result = fork_thread(
            parent_session_id,
            workspace=child_workspace,
            checkpoint_id=parent_checkpoint_id,
            timeout_sec=args.timeout_sec,
        )
        child_session_id = provider_result["thread_id"]
        if child_session_id == parent_session_id:
            raise RuntimeError("Native fork returned the parent provider session id")
        pending_meta.update({
            "session_id": child_session_id,
            "provider_fork_ref": child_session_id,
            "status": "forked_unreconciled",
            "updated_at": utc_now(),
        })
        save_meta(child_dir, pending_meta)
        now = utc_now()
        child_meta = {
            **parent_meta,
            "review_id": child_review_id,
            "created_at": now,
            "updated_at": now,
            "workspace_path": str(child_workspace.resolve()),
            "session_id": child_session_id,
            "parent_review_id": args.parent_review_id,
            "parent_session_id": parent_session_id,
            "checkpoint_id": args.checkpoint_id,
            "provider_checkpoint_id": parent_checkpoint_id,
            "provider_turn_id": parent_checkpoint_id,
            "operation_id": args.operation_id,
            "snapshot_digest": args.snapshot_digest,
            "fork_route": "native",
            "provider_fork_ref": child_session_id,
            "fork_question": args.question,
            "prompt_consumed": False,
            "codex_app_server": {
                "version": provider_result["codex_version"],
                "user_agent": provider_result["user_agent"],
                "method": "thread/fork",
            },
            "status": "open",
            "last_response": "",
        }
        save_meta(child_dir, child_meta)
        init_runtime(child_dir, review_id=child_review_id, action="fork", timeout_sec=args.timeout_sec)
        update_runtime(child_dir, state="completed", phase="finished", finished_at=utc_now())
        append_log(child_dir, {
            "ts": now, "type": "fork", "prompt": args.question or "", "response": "",
            "session_id": child_session_id, "parent_session_id": parent_session_id,
            "checkpoint_id": args.checkpoint_id, "raw_events": [], "stderr": "",
        })
    except BaseException as exc:
        if provider_result is None:
            clear_sandbox_contents_preserving_lock(child_dir)
        else:
            orphan_meta = load_meta(child_review_id)[1]
            orphan_meta.update({
                "status": "orphaned",
                "updated_at": utc_now(),
                "orphan_reason": str(exc)[:1000],
                "session_id": provider_result.get("thread_id"),
                "provider_fork_ref": provider_result.get("thread_id"),
            })
            save_meta(child_dir, orphan_meta)
        raise

    payload = {
        "review_id": child_review_id,
        "session_id": child_session_id,
        "parent_review_id": args.parent_review_id,
        "parent_session_id": parent_session_id,
        "workspace": str(child_workspace.resolve()),
        "provider_fork_ref": child_session_id,
        "checkpoint_id": args.checkpoint_id,
        "provider_checkpoint_id": parent_checkpoint_id,
        "provider_turn_id": parent_checkpoint_id,
        "operation_id": args.operation_id,
        "snapshot_digest": args.snapshot_digest,
        "prompt_consumed": False,
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False))
    else:
        for key, value in payload.items():
            if value is not None:
                rendered = str(value).lower() if isinstance(value, bool) else value
                print(f"{key}: {rendered}")
    return 0


def cmd_capabilities(args: argparse.Namespace) -> int:
    payload = {
        "native_fork": {
            "supported": True,
            "route": "codex_app_server_thread_fork",
            "min_cli_version": "0.146.0",
            "exact_checkpoint": True,
            "automation_safe": True,
        }
    }
    if args.json:
        print(json.dumps(payload, ensure_ascii=False))
    else:
        fork = payload["native_fork"]
        print("native_fork: supported")
        for key in ("route", "min_cli_version", "exact_checkpoint", "automation_safe"):
            print(f"{key}: {fork[key]}")
    return 0


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
    write_review_context(workspace, review_id=review_id, brief=brief, sources=sources)
    prompt = build_prompt(args)
    result = run_codex(
        prompt,
        model=args.model,
        reasoning_effort=args.reasoning_effort,
        sandbox=DEFAULT_SANDBOX,
        workspace=workspace,
        session_id=None,
        review_dir=review_dir,
        timeout_sec=args.timeout_sec,
    )
    if not result["session_id"]:
        raise RuntimeError(
            "Codex completed, but no session_id was found in JSON events. "
            "Cannot open a resumable review session."
        )
    meta = {
        "review_id": review_id,
        "created_at": utc_now(),
        "updated_at": utc_now(),
        "model": args.model,
        "reasoning_effort": args.reasoning_effort,
        "sandbox": DEFAULT_SANDBOX,
        "workspace_path": str(workspace.resolve()),
        "source_root": str(cwd.resolve()),
        "sources": sources,
        "question": args.question,
        "brief": brief,
        "full_context": bool(args.full_context or not args.paths),
        "session_id": result["session_id"],
        "provider_checkpoint_id": result["provider_checkpoint_id"],
        "provider_turn_id": result["provider_turn_id"],
        "protocol": {
            "advisory_only": True,
            "read_only": True,
            "max_rounds": 5,
            "finding_ids": "F-01...",
            "primary_agent_verifies_findings": True,
        },
        "status": "open",
        "last_response": result["response"],
    }
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": "start",
        "prompt": prompt,
        "response": result["response"],
        "session_id": result["session_id"],
        "provider_checkpoint_id": result["provider_checkpoint_id"],
        "provider_turn_id": result["provider_turn_id"],
        "raw_events": result["raw_events"],
        "stderr": result["stderr"],
    })
    stats = compute_stats(read_log(review_dir))
    print(f"review_id: {review_id}")
    print(f"session_id: {result['session_id']}")
    print(f"provider_checkpoint_id: {result['provider_checkpoint_id']}")
    print(f"provider_turn_id: {result['provider_turn_id']}")
    print(f"workspace: {workspace.resolve()}")
    print(
        f"events: {stats['events']} | input_tokens: {stats['input_tokens']} | "
        f"output_tokens: {stats['output_tokens']} | total_tokens: {stats['total_tokens']} | "
        f"tool_calls: {stats['tool_calls_total']} | tools: {json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    print()
    print(result["response"])
    return 0


def cmd_ask(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    event_type = getattr(args, "event_type", "ask")
    init_runtime(review_dir, review_id=args.review_id, action=event_type, timeout_sec=args.timeout_sec)
    operation_id = getattr(args, "operation_id", None)
    parent_provider_turn_id = getattr(args, "parent_provider_turn_id", None)
    if bool(operation_id) != bool(parent_provider_turn_id):
        raise ValueError("operation_id and parent_provider_turn_id must be supplied together")
    marker = ""
    if operation_id:
        if meta.get("provider_checkpoint_id") != parent_provider_turn_id:
            raise ValueError("parent_provider_turn_id does not match the sealed provider head")
        reconciliation = inspect_thread_operation(meta["session_id"], operation_id, timeout_sec=min(args.timeout_sec, 30))
        if reconciliation["state"] == "completed":
            print(f"provider_checkpoint_id: {reconciliation['provider_turn_id']}")
            print(f"provider_turn_id: {reconciliation['provider_turn_id']}")
            print(reconciliation.get("response") or "(Recovered completed Codex turn.)")
            return 0
        operations = meta.setdefault("operations", {})
        if reconciliation["state"] != "absent":
            raise RuntimeError(f"Codex ask operation is not safely repeatable: {reconciliation['state']}")
        existing_operation = operations.get(operation_id)
        if existing_operation and existing_operation.get("parent_provider_turn_id") != parent_provider_turn_id:
            raise RuntimeError("Codex ask operation parent changed during reconciliation")
        # Local app-server thread/read is the authoritative, strongly-consistent
        # rollout history at this request boundary. Therefore pending+absent
        # proves the previous process crashed before provider acceptance and one
        # retry is safe. This rule must not be reused for eventual-consistent APIs.
        marker = reconciliation["marker"]
        operations[operation_id] = {
            "state": "pending", "parent_provider_turn_id": parent_provider_turn_id,
            "marker": marker,
            "created_at": (existing_operation or {}).get("created_at") or utc_now(),
            "provider_attempted_at": utc_now(),
        }
        save_meta(review_dir, meta)
    prompt = f"{marker}\n\n{load_review_prompt()}\n\n{args.question}".strip()
    result = run_codex(
        prompt,
        model=meta["model"],
        reasoning_effort=meta.get("reasoning_effort") or DEFAULT_REASONING_EFFORT,
        sandbox=meta.get("sandbox") or DEFAULT_SANDBOX,
        workspace=Path(meta["workspace_path"]),
        session_id=meta["session_id"],
        review_dir=review_dir,
        timeout_sec=args.timeout_sec,
    )
    meta["updated_at"] = utc_now()
    if result["session_id"]:
        meta["session_id"] = result["session_id"]
    meta["provider_checkpoint_id"] = result["provider_checkpoint_id"]
    meta["provider_turn_id"] = result["provider_turn_id"]
    meta["last_response"] = result["response"]
    if operation_id:
        meta["operations"][operation_id].update({
            "state": "completed", "provider_turn_id": result["provider_turn_id"],
            "completed_at": utc_now(),
        })
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": event_type,
        "prompt": prompt,
        "response": result["response"],
        "session_id": result["session_id"],
        "provider_checkpoint_id": result["provider_checkpoint_id"],
        "provider_turn_id": result["provider_turn_id"],
        "raw_events": result["raw_events"],
        "stderr": result["stderr"],
    })
    stats = compute_stats(read_log(review_dir))
    print(
        f"[review {args.review_id}] events={stats['events']} input_tokens={stats['input_tokens']} "
        f"output_tokens={stats['output_tokens']} total_tokens={stats['total_tokens']} "
        f"tool_calls={stats['tool_calls_total']} tools={json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    print(f"provider_checkpoint_id: {result['provider_checkpoint_id']}")
    print(f"provider_turn_id: {result['provider_turn_id']}")
    print(result["response"])
    return 0


def cmd_reconcile(args: argparse.Namespace) -> int:
    _, meta = load_meta(args.review_id)
    operation = (meta.get("operations") or {}).get(args.operation_id)
    expected_parent_turn = (
        operation.get("parent_provider_turn_id")
        if isinstance(operation, dict)
        else meta.get("provider_checkpoint_id")
    )
    if args.parent_provider_turn_id and expected_parent_turn != args.parent_provider_turn_id:
        raise ValueError("parent_provider_turn_id does not match persisted operation lineage")
    result = inspect_thread_operation(meta["session_id"], args.operation_id, timeout_sec=args.timeout_sec)
    evidence_complete = result["state"] in {"absent", "completed"}
    payload = {
        "review_id": args.review_id,
        "operation_id": args.operation_id,
        "session_id": meta.get("session_id"),
        "provider_checkpoint_id": meta.get("provider_checkpoint_id"),
        "provider_turn_id": result.get("provider_turn_id"),
        "text": result.get("response") or "",
        "evidence_complete": evidence_complete,
        **result,
    }
    print(json.dumps(payload, ensure_ascii=False) if args.json else "\n".join(f"{k}: {v}" for k, v in payload.items()))
    return 0 if result["state"] in {"absent", "completed"} else 1


def cmd_debate(args: argparse.Namespace) -> int:
    prompt = (
        "This is an issue debate under the Codex review protocol.\n\n"
        f"Issue:\n{args.issue}\n\n"
        f"Reviewer finding to evaluate:\n{args.finding}\n\n"
        f"Primary agent position:\n{args.position}\n\n"
        "Instructions:\n"
        "- Assess only this issue.\n"
        "- State one of: agree / partial / disagree / withdrawn / out_of_scope.\n"
        "- If you disagree with the primary agent, give concrete evidence.\n"
        "- If the primary agent resolves your concern, say so explicitly.\n"
        "- Do not expand into unrelated new findings.\n"
        "- Respect the max-five-round review protocol.\n"
    )
    args.question = prompt
    args.event_type = "debate"
    return cmd_ask(args)


def cmd_sync(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    sync_sources(meta, review_dir)
    write_review_context(
        Path(meta["workspace_path"]),
        review_id=args.review_id,
        brief=meta.get("brief", ""),
        sources=meta.get("sources", []),
    )
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
    payload = {
        "review_id": args.review_id,
        "status": meta.get("status") if meta is not None else "starting",
        "runtime": load_runtime(review_dir),
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
        print(f"[{i}] {event.get('ts', '')} {event.get('type', '')}")
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
        print(f"Closed review sandbox; retained invocation lock tombstone: {args.review_id}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Session-based Codex review runner with isolated sandbox workspace."
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_start = sub.add_parser("start", help="Create a new review sandbox and start a Codex review session.")
    p_start.add_argument("--question", required=True, help="Initial review question.")
    p_start.add_argument("--model", default=DEFAULT_MODEL, help=f"Codex model. Default: {DEFAULT_MODEL}.")
    p_start.add_argument("--reasoning-effort", default=DEFAULT_REASONING_EFFORT, help=f"Reasoning effort. Default: {DEFAULT_REASONING_EFFORT}")
    p_start.add_argument("--review-id", help="Optional custom review id.")
    p_start.add_argument("--full-context", action="store_true", help="Copy the whole working directory into the review sandbox, excluding known heavy/generated dirs.")
    p_start.add_argument("--task", help="Short statement of the task being worked on.")
    p_start.add_argument("--goal", help="Desired outcome / business goal.")
    p_start.add_argument("--artifact-type", help="Artifact type: spec, code, tests, architecture, UI, etc.")
    p_start.add_argument("--requirements", help="Important requirements or criteria to satisfy.")
    p_start.add_argument("--constraints", help="Important constraints or things that must not change.")
    p_start.add_argument("--primary-target", help="Primary artifact or output to review.")
    p_start.add_argument("--changed-files", nargs="*", help="List of changed files or files most relevant to the review.")
    p_start.add_argument("--skills", nargs="*", help="Skill/rule files the reviewer should use as criteria.")
    p_start.add_argument("--open-concerns", help="Known doubts, risks, or unresolved questions.")
    p_start.add_argument("--review-ask", help="Specific type of review requested from Codex.")
    p_start.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Codex invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_start.add_argument("paths", nargs="*", help="Files or directories to copy into the review sandbox. If omitted, full context is used.")
    p_start.set_defaults(func=cmd_start)

    p_capabilities = sub.add_parser("capabilities", help="Report adapter capabilities.")
    p_capabilities.add_argument("--json", action="store_true", help="Print capabilities as JSON.")
    p_capabilities.set_defaults(func=cmd_capabilities)

    p_ask = sub.add_parser("ask", help="Ask a follow-up question in an existing Codex review session.")
    p_ask.add_argument("review_id", help="Existing review id.")
    p_ask.add_argument("--question", required=True, help="Follow-up question.")
    p_ask.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Codex invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_ask.add_argument("--operation-id", help="Deterministic caller operation id (paired with parent provider turn).")
    p_ask.add_argument("--parent-provider-turn-id", help="Sealed provider head before this operation.")
    p_ask.set_defaults(func=cmd_ask)

    p_reconcile = sub.add_parser("reconcile", help="Inspect provider history for an ask operation marker.")
    p_reconcile.add_argument("review_id")
    p_reconcile.add_argument("--operation-id", required=True)
    p_reconcile.add_argument("--parent-provider-turn-id")
    p_reconcile.add_argument("--timeout-sec", type=int, default=30)
    p_reconcile.add_argument("--json", action="store_true")
    p_reconcile.set_defaults(func=cmd_reconcile)

    p_fork = sub.add_parser("fork", help="Fork an existing review through Codex app-server thread/fork.")
    p_fork.add_argument("parent_review_id", help="Existing parent review id.")
    p_fork.add_argument("--child-review-id", required=True, help="New child review id.")
    p_fork.add_argument("--checkpoint-id", help="Logical sealed checkpoint label for lineage.")
    p_fork.add_argument("--provider-checkpoint-id", required=True, help="Sealed Codex provider turn id (lastTurnId).")
    p_fork.add_argument("--operation-id", required=True, help="Caller operation id for fork reconciliation.")
    p_fork.add_argument("--snapshot-digest", required=True, help="Lowercase sha256 digest of the sealed logical snapshot.")
    p_fork.add_argument("--question", help="Optional child-branch context; does not start a model turn.")
    p_fork.add_argument("--timeout-sec", type=int, default=60, help="Bounded app-server fork timeout.")
    p_fork.add_argument("--json", action="store_true", help="Print fork result as one JSON object.")
    p_fork.set_defaults(func=cmd_fork)

    p_debate = sub.add_parser("debate", help="Run a structured response-to-finding step.")
    p_debate.add_argument("review_id", help="Existing review id.")
    p_debate.add_argument("--issue", required=True, help="Short issue identifier or label.")
    p_debate.add_argument("--finding", required=True, help="The reviewer finding being challenged or clarified.")
    p_debate.add_argument("--position", required=True, help="Primary agent's argument or position on the issue.")
    p_debate.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Codex invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
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

    p_stats = sub.add_parser("stats", help="Show cumulative token stats for the review session.")
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
        if args.cmd == "start":
            return args.func(args)
        if args.cmd == "fork":
            parent_dir = review_dir_for_id(REVIEW_ROOT, args.parent_review_id)
            child_dir = review_dir_for_id(REVIEW_ROOT, args.child_review_id)
            # Parent lock comes first and before child filesystem creation, so
            # a busy parent fails without leaving a child tombstone. Forks are
            # always directed parent -> new child; the reverse route is invalid.
            with invocation_lock(parent_dir, review_id=args.parent_review_id, action="fork"):
                prepare_start_directory(child_dir, allow_lock_tombstone=True)
                with invocation_lock(child_dir, review_id=args.child_review_id, action="fork"):
                    return args.func(args)
        if args.cmd in {"ask", "debate", "sync", "close", "reconcile"}:
            review_id = args.review_id
            review_dir = review_dir_for_id(REVIEW_ROOT, review_id)
            with invocation_lock(review_dir, review_id=review_id, action=args.cmd):
                return args.func(args)
        return args.func(args)
    except SessionBusyError as exc:
        print("error_kind: session_busy", file=sys.stderr)
        print(str(exc), file=sys.stderr)
        return 1
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

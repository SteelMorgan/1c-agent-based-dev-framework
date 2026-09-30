#!/usr/bin/env python3
"""Адаптер Kimi CLI для консилиума (CONS-01, TD 1.2) — по образцу claude_opus_review.py.

Контракт: start/ask/debate/sync/show/status/log/stats/close, sandbox
`.review-sandboxes/<review_id>/`, блокирующие вызовы, per-invocation timeout.

Фактические флаги Kimi CLI v0.29.2 (TD 1.2, ASM-02 подтверждён):
- неинтерактивный вызов: `kimi -p "<prompt>" --output-format stream-json`;
- session_id парсится из meta-события `session.resume_hint`;
- resume: `kimi -r <session_id>` (фактический, эмитируемый CLI), fallback `--session`;
- read-only allowlist-флага у CLI нет: граница read-only обеспечивается
  (a) sandbox-копиями материалов, (b) read-only инструкцией в prompt,
  (c) опционально `--agent-file` с ограниченным профилем (RD-2).
"""
from __future__ import annotations

import argparse
import hashlib
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

SCRIPTS_DIR = Path(__file__).resolve().parents[1]
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))
ADAPTERS_DIR = Path(__file__).resolve().parent
if str(ADAPTERS_DIR) not in sys.path:
    sys.path.insert(0, str(ADAPTERS_DIR))

from invocation_lock import (
    SessionBusyError,
    clear_sandbox_contents_preserving_lock,
    invocation_lock,
    prepare_start_directory,
    review_dir_for_id,
)

from runtime_store import write_json_atomically

from kimi_web_fork import (
    KimiWebForkError,
    archive_session,
    fork_session,
    probe as probe_web_fork,
    reconcile_turn,
)


REVIEW_ROOT = Path(".review-sandboxes")
DEFAULT_MODEL = ""  # пусто: берётся default_model из конфигурации kimi CLI
RUNTIME_LOCK = threading.RLock()
DEFAULT_EXCLUDES = {
    ".git",
    ".venv",
    ".review-sandboxes",
    ".consilium-sessions",
    ".consilium-track-record",
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
READ_ONLY_PRELUDE = """You are a read-only consilium participant operating in an isolated sandbox copy.

IMPORTANT: Do NOT create, modify, or delete any files. You may only READ files for analysis.
Work only inside the current sandbox workspace; never touch the real project.
"""
# Механическая read-only граница (NFR-05, F-02): agent profile с allowlist только
# read-инструментов (фактический формат kimi CLI v0.29.2: frontmatter `tools`,
# `disallowedTools`; read-набор по аналогии с claude Read,Grep,Glob,LS — у kimi LS нет).
# Передаётся через --agent-file; работает только в v2 engine — run_kimi выставляет
# KIMI_CODE_EXPERIMENTAL_FLAG=1 при наличии agent_file (RD-2 checkpoint 2026-07-28).
READONLY_AGENT_PROFILE = """---
name: consilium-readonly-participant
description: Read-only consilium participant (NFR-05). Only read tools; no writes, no edits, no shell.
tools: Read, Grep, Glob
---

You are a read-only consilium participant operating in an isolated sandbox copy.

STRICT BOUNDARY: you may only READ files for analysis. You must NOT create, modify,
or delete any files, must NOT run shell commands, and must NOT touch anything outside
the current sandbox workspace. If a task seems to require a write, report it as a
finding instead of performing it.
"""
DEFAULT_TIMEOUT_SEC = 900  # CONS-02 OPT-1: default адаптера выровнен к ядру
MIN_NATIVE_FORK_VERSION = "0.31.0"
SAFE_REVIEW_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
SAFE_OPERATION_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]*$")
SNAPSHOT_DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")


def utc_now() -> str:
    return datetime.now(UTC).isoformat()


def make_review_id() -> str:
    return datetime.now(UTC).strftime("%Y%m%d-%H%M%S") + "-" + uuid.uuid4().hex[:8]


# ---------------------------------------------------------------------------
# Sandbox copy machinery (по образцу claude_opus_review.py)
# ---------------------------------------------------------------------------

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
        ["git", "-C", str(source_root), "ls-files", "--cached", "--others",
         "--exclude-standard", "-z", "--", pathspec],
        stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=False,
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


def should_exclude(path: Path, source_root: Path) -> bool:
    try:
        rel = path.resolve().relative_to(source_root.resolve())
        parts = set(rel.parts)
    except Exception:
        parts = set(path.parts)
    if parts & DEFAULT_EXCLUDES:
        return True
    return any(part.startswith(DEFAULT_EXCLUDE_PREFIXES) for part in parts)


def remove_dest(path: Path) -> None:
    if path.is_symlink():
        path.unlink()
    elif path.is_dir():
        shutil.rmtree(path)
    elif path.exists():
        path.unlink()


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
        # Существующая ссылка в назначении, уводящая цепочку за пределы sandbox,
        # остаётся unsafe: её воспроизведение дало бы пакету чтение за его корнем.
        raise ValueError(
            f"Unsafe symlink destination target escapes destination root: {dst}"
        ) from exc
    except FileNotFoundError:
        # Mapped-цель просто отсутствует — нормальный случай зеркальной ссылки,
        # выходящей за пределы копируемого поддерева:
        # `.codex/skills/X -> ../../.claude/skills/X` безопасна в источнике
        # (проверено выше: резолвится внутри source root), но `.claude/` не
        # входит в копируемый набор, и ссылка повисла бы. Молчаливое
        # воспроизведение dangling-ссылки опустошало пакет ровно тех правил,
        # которые ревью обязано прочитать. Вместо этого содержимое
        # материализуется — оно остаётся внутри sandbox, пакет самодостаточен.
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
        missing_path = Path(exc.filename) if exc.filename else None
        if src.exists() or (missing_path is not None and missing_path.absolute() != src.absolute()):
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
    return {"source": str(src.resolve()), "dest_rel": safe_rel_to_cwd(src, source_root).as_posix()}


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


def copy_full_context(source_root: Path, workspace: Path) -> list[dict]:
    sources: list[dict] = []
    for child in sorted(source_root.iterdir()):
        if should_exclude(child, source_root):
            continue
        register_source(sources, child, source_root, workspace=workspace)
    return sources


def validate_workspace_symlinks(workspace: Path) -> None:
    """Reject mutable/external links before materializing a child workspace."""
    root = workspace.resolve(strict=True)
    for path in workspace.rglob("*"):
        if not path.is_symlink():
            continue
        try:
            path.resolve(strict=True).relative_to(root)
        except (FileNotFoundError, ValueError) as exc:
            raise ValueError(f"parent workspace contains unsafe symlink: {path}") from exc


# ---------------------------------------------------------------------------
# Runtime / log observability (по образцу claude_opus_review.py)
# ---------------------------------------------------------------------------

def runtime_path(review_dir: Path) -> Path:
    return review_dir / "runtime.json"


def runtime_events_path(review_dir: Path) -> Path:
    return review_dir / "runtime.ndjson"


def load_runtime(review_dir: Path) -> dict:
    with RUNTIME_LOCK:
        path = runtime_path(review_dir)
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


def empty_progress() -> dict:
    return {
        "raw_events": 0,
        "event_types": {},
        "tool_calls_total": 0,
        "last_event_type": None,
    }


def merge_progress_event(progress: dict, raw: dict) -> dict:
    progress = {**empty_progress(), **(progress or {})}
    event_type = str(raw.get("type") or raw.get("role") or "unknown")
    progress["raw_events"] += 1
    progress["event_types"][event_type] = progress["event_types"].get(event_type, 0) + 1
    progress["last_event_type"] = event_type
    if isinstance(raw.get("tool_calls"), list):
        progress["tool_calls_total"] += len(raw["tool_calls"])
    return progress


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
        "stdout_log": str((review_dir / "stdout.log").resolve()),
        "stderr_log": str((review_dir / "stderr.log").resolve()),
        "runtime_events": str(runtime_events_path(review_dir).resolve()),
        "error": None,
        "result_preview": None,
        "progress": empty_progress(),
    }
    save_runtime(review_dir, runtime)
    append_runtime_event(review_dir, {"ts": utc_now(), "type": "runtime-init",
                                      "state": "queued", "phase": "queued",
                                      "timeout_sec": timeout_sec})
    return runtime


def mark_phase(review_dir: Path, phase: str, **extra: object) -> None:
    update_runtime(review_dir, state="running", phase=phase,
                   last_heartbeat_at=utc_now(), **extra)
    append_runtime_event(review_dir, {"ts": utc_now(), "type": "phase", "phase": phase, **extra})


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


def compute_stats(events: list[dict]) -> dict:
    progress = empty_progress()
    prompts = 0
    for event in events:
        if event.get("prompt"):
            prompts += 1
        for raw in event.get("raw_events") or []:
            if isinstance(raw, dict):
                progress = merge_progress_event(progress, raw)
    return {
        "events": len(events),
        "turns": prompts,
        "raw_events": progress["raw_events"],
        "event_types": progress["event_types"],
        "tool_calls_total": progress["tool_calls_total"],
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


# ---------------------------------------------------------------------------
# Вызов Kimi CLI (TD 1.2)
# ---------------------------------------------------------------------------

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


def extract_session_id(events: list[dict]) -> str | None:
    """session_id из meta-события session.resume_hint (TD 1.2)."""
    for event in reversed(events):
        if event.get("role") == "meta" and event.get("type") == "session.resume_hint":
            session_id = event.get("session_id")
            if session_id:
                return str(session_id)
    return None


def extract_response_text(events: list[dict]) -> str:
    """Результат — последний assistant.content (строка или список частей)."""
    for event in reversed(events):
        if event.get("role") != "assistant":
            continue
        content = event.get("content")
        if isinstance(content, str) and content.strip():
            return content.strip()
        if isinstance(content, list):
            parts = []
            for part in content:
                if isinstance(part, dict) and part.get("text"):
                    parts.append(str(part["text"]))
                elif isinstance(part, str):
                    parts.append(part)
            if parts:
                return "\n".join(parts).strip()
    return ""


def provider_turn_identity(events: list[dict]) -> str:
    canonical = json.dumps(events, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return "local:" + hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _stream_reader(stream, log_path: Path, sink: list[str], review_dir: Path | None) -> None:
    with log_path.open("a", encoding="utf-8") as log:
        while True:
            chunk = stream.readline()
            if chunk == "":
                break
            sink.append(chunk)
            log.write(chunk)
            log.flush()
            if review_dir is not None:
                try:
                    parsed = json.loads(chunk)
                except json.JSONDecodeError:
                    parsed = None
                if isinstance(parsed, dict):
                    runtime = load_runtime(review_dir)
                    progress = merge_progress_event(runtime.get("progress") or {}, parsed)
                    update_runtime(review_dir, progress=progress,
                                   last_activity_at=utc_now(), last_heartbeat_at=utc_now())
    try:
        stream.close()
    except Exception:
        pass


def build_kimi_cmd(prompt: str, *, model: str, session_id: str | None,
                   resume_flag: str, agent_file: str | None) -> list[str]:
    cmd = ["kimi", "-p", prompt, "--output-format", "stream-json"]
    if model:
        cmd += ["-m", model]
    if agent_file:
        cmd += ["--agent-file", agent_file]
    if session_id:
        cmd += [resume_flag, session_id]
    return cmd


def run_kimi(
    prompt: str,
    *,
    model: str,
    workspace: Path,
    session_id: str | None = None,
    review_dir: Path | None = None,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    agent_file: str | None = None,
    resume_flag: str = "-r",
) -> dict:
    """Один блокирующий вызов kimi -p. Resume через `-r` (fallback `--session`, TD 1.2)."""
    full_prompt = f"{READ_ONLY_PRELUDE}\n\n{prompt}"
    cmd = build_kimi_cmd(full_prompt, model=model, session_id=session_id,
                         resume_flag=resume_flag, agent_file=agent_file)
    env = None
    if agent_file:
        # --agent-file доступен только в v2 engine (RD-2 checkpoint, 2026-07-28)
        env = {**os.environ, "KIMI_CODE_EXPERIMENTAL_FLAG": "1"}
    stdout_chunks: list[str] = []
    stderr_chunks: list[str] = []
    proc = subprocess.Popen(
        cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        text=True, cwd=workspace, bufsize=1, env=env,
    )
    if review_dir is not None:
        update_runtime(review_dir, pid=proc.pid, state="running",
                       started_at=load_runtime(review_dir).get("started_at") or utc_now(),
                       timeout_sec=timeout_sec)
        mark_phase(review_dir, "starting", pid=proc.pid)

    threads: list[threading.Thread] = []
    for stream, name, sink in ((proc.stdout, "stdout.log", stdout_chunks),
                               (proc.stderr, "stderr.log", stderr_chunks)):
        if stream is not None:
            target_dir = review_dir if review_dir else Path("/tmp")
            t = threading.Thread(target=_stream_reader,
                                 args=(stream, target_dir / name, sink, review_dir), daemon=True)
            t.start()
            threads.append(t)

    if review_dir is not None:
        mark_phase(review_dir, "reading")

    started = time.time()
    while True:
        rc = proc.poll()
        if review_dir is not None:
            update_runtime(review_dir, last_heartbeat_at=utc_now(),
                           elapsed_sec=round(time.time() - started, 1))
        if rc is not None:
            break
        if time.time() - started > timeout_sec:
            proc.kill()
            if review_dir is not None:
                update_runtime(review_dir, state="failed", phase="timeout",
                               finished_at=utc_now(),
                               error=f"Kimi invocation exceeded timeout {timeout_sec}s")
                append_runtime_event(review_dir, {"ts": utc_now(), "type": "timeout",
                                                  "timeout_sec": timeout_sec})
            raise RuntimeError(f"Kimi invocation exceeded timeout {timeout_sec}s")
        time.sleep(1)

    for t in threads:
        t.join(timeout=2)

    stdout_text = "".join(stdout_chunks)
    stderr_text = "".join(stderr_chunks)
    if proc.returncode != 0:
        error = stderr_text.strip() or stdout_text.strip() or f"kimi exited with code {proc.returncode}"
        # fallback resume: `-r` → `--session`, если CLI не принял флаг (TD 1.2)
        if session_id and resume_flag == "-r" and (
            "unknown option" in error.lower() or "unrecognized" in error.lower()
        ):
            return run_kimi(prompt, model=model, workspace=workspace, session_id=session_id,
                            review_dir=review_dir, timeout_sec=timeout_sec,
                            agent_file=agent_file, resume_flag="--session")
        if review_dir is not None:
            update_runtime(review_dir, state="failed", phase="failed",
                           finished_at=utc_now(), error=error[:2000])
        raise RuntimeError(error)

    events = iter_json_objects(stdout_text)
    detected_session = extract_session_id(events) or session_id
    response_text = extract_response_text(events)
    if review_dir is not None:
        update_runtime(review_dir, state="completed", phase="finished",
                       finished_at=utc_now(), result_preview=response_text[:400])
        append_runtime_event(review_dir, {"ts": utc_now(), "type": "finished",
                                          "returncode": proc.returncode,
                                          "session_id": detected_session})
    return {
        "session_id": detected_session,
        "response": response_text,
        "raw_events": events,
        "stderr": stderr_text,
        "command": cmd,
    }


# ---------------------------------------------------------------------------
# Команды lifecycle (контракт start/ask/debate/sync/show/status/log/stats/close)
# ---------------------------------------------------------------------------

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


def cmd_start(args: argparse.Namespace) -> int:
    cwd = Path.cwd()
    review_id = args.review_id or make_review_id()
    review_dir = review_dir_for_id(REVIEW_ROOT, review_id)
    workspace = review_dir / "workspace"
    prepare_start_directory(review_dir, allow_lock_tombstone=bool(args.review_id))
    try:
        with invocation_lock(review_dir, review_id=review_id, action="start"):
            return start_created_review(args, cwd, review_id, review_dir, workspace)
    except BaseException as start_error:
        # Preserve the stable lock inode as the only failed-start tombstone.
        try:
            clear_sandbox_contents_preserving_lock(review_dir)
        except BaseException as cleanup_error:
            try:
                add_note = getattr(start_error, "add_note", None)
                diagnostic = f"Review sandbox cleanup failed for {review_dir}: {cleanup_error!r}"
                if callable(add_note):
                    add_note(diagnostic)
                else:
                    print(diagnostic, file=sys.stderr)
            except BaseException:
                pass
        raise


def start_created_review(args, cwd, review_id, review_dir, workspace) -> int:
    workspace.mkdir()
    init_runtime(review_dir, review_id=review_id, action="start", timeout_sec=args.timeout_sec)
    mark_phase(review_dir, "copying")

    if args.full_context or not args.paths:
        sources = copy_full_context(cwd, workspace)
    else:
        sources = []
        for raw in args.paths:
            register_source(sources, Path(raw), cwd, workspace=workspace)

    brief = build_brief(args)
    prompt = brief or args.question
    # read-only граница по умолчанию (NFR-05): встроенный agent profile, если
    # явный --agent-file не задан; профиль материализуется в каталог сессии.
    agent_file = args.agent_file
    if not agent_file:
        agent_file = str((review_dir / "agent-profile.md").resolve())
        (review_dir / "agent-profile.md").write_text(READONLY_AGENT_PROFILE, encoding="utf-8")
    result = run_kimi(prompt, model=args.model, workspace=workspace,
                      review_dir=review_dir, timeout_sec=args.timeout_sec,
                      agent_file=agent_file)
    if not result["session_id"]:
        raise RuntimeError("kimi не вернул session_id (session.resume_hint); resume невозможен")
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
        "provider_turn_id": provider_turn_identity(result["raw_events"]),
        "protocol": {"advisory_only": True, "read_only": True},
        "status": "open",
        "last_response": result["response"],
    }
    save_meta(review_dir, meta)
    append_log(review_dir, {"ts": utc_now(), "type": "start", "prompt": prompt,
                            "response": result["response"],
                            "session_id": result["session_id"],
                            "raw_events": result["raw_events"]})
    print(f"review_id: {review_id}")
    print(f"session_id: {result['session_id']}")
    print(f"workspace: {workspace.resolve()}")
    stats = compute_stats(read_log(review_dir))
    print(f"events: {stats['events']} | raw_events: {stats['raw_events']} | "
          f"tool_calls: {stats['tool_calls_total']}")
    print()
    print(result["response"])
    return 0


def _continue_session(args: argparse.Namespace, prompt: str, event_type: str) -> int:
    review_dir, meta = load_meta(args.review_id)
    with invocation_lock(review_dir, review_id=args.review_id, action=event_type):
        _, meta = load_meta(args.review_id)
        if meta.get("native_fork_seal"):
            raise RuntimeError("sealed_parent: ask/debate is forbidden after native fork sealing")
        operation_id = getattr(args, "operation_id", None)
        parent_turn_id = getattr(args, "parent_provider_turn_id", None)
        if bool(operation_id) != bool(parent_turn_id):
            raise ValueError("operation_id and parent_provider_turn_id must be supplied together")
        if operation_id:
            if not SAFE_OPERATION_ID.fullmatch(operation_id):
                raise ValueError("operation_id must be a safe opaque identity")
            if meta.get("provider_turn_id") != parent_turn_id:
                raise ValueError("parent_provider_turn_id does not match current provider head")
            pending_dir = review_dir / "pending-operations"
            pending_dir.mkdir(exist_ok=True)
            pending_path = pending_dir / f"{operation_id}.json"
            pending = {
                "operation_id": operation_id, "parent_provider_turn_id": parent_turn_id,
                "prompt_sha256": hashlib.sha256(prompt.encode("utf-8")).hexdigest(),
                "state": "pending", "created_at": utc_now(),
            }
            if pending_path.exists():
                recorded = json.loads(pending_path.read_text(encoding="utf-8"))
                if any(recorded.get(k) != pending[k] for k in (
                    "operation_id", "parent_provider_turn_id", "prompt_sha256",
                )):
                    raise RuntimeError("operation_id already belongs to different turn data")
            else:
                pending_path.write_text(json.dumps(pending, ensure_ascii=False, indent=2), encoding="utf-8")
            reconciled = reconcile_turn(meta["session_id"], operation_id, timeout_sec=min(args.timeout_sec, 10))
            if reconciled["state"] == "completed":
                response = reconciled["text"]
                turn_id = "local:" + hashlib.sha256(
                    (operation_id + "\0" + response).encode("utf-8")
                ).hexdigest()
                pending.update({"state": "completed", "response": response,
                                "provider_turn_id": turn_id, "completed_at": utc_now()})
                pending_path.write_text(json.dumps(pending, ensure_ascii=False, indent=2), encoding="utf-8")
                meta["provider_turn_id"] = turn_id
                meta["last_response"] = response
                save_meta(review_dir, meta)
                print(f"operation_id: {operation_id}")
                print(f"provider_turn_id: {turn_id}")
                print(response)
                return 0
            if reconciled["state"] != "absent":
                raise RuntimeError(f"turn reconciliation is {reconciled['state']}; refusing re-ask")
            prompt = f"[review-operation-id:{operation_id}]\n{prompt}"
        workspace = Path(meta["workspace_path"])
        init_runtime(review_dir, review_id=args.review_id, action=event_type, timeout_sec=args.timeout_sec)
        result = run_kimi(prompt, model=meta.get("model") or "", workspace=workspace,
                          session_id=meta["session_id"], review_dir=review_dir,
                          timeout_sec=args.timeout_sec, agent_file=None)
        meta["updated_at"] = utc_now()
        if result["session_id"]:
            meta["session_id"] = result["session_id"]
            meta["provider_checkpoint_id"] = result["session_id"]
        meta["provider_turn_id"] = provider_turn_identity(result["raw_events"])
        meta["last_response"] = result["response"]
        save_meta(review_dir, meta)
        append_log(review_dir, {"ts": utc_now(), "type": event_type, "prompt": prompt,
                                "response": result["response"],
                                "session_id": result["session_id"],
                                "raw_events": result["raw_events"]})
        if operation_id:
            pending.update({"state": "completed", "response": result["response"],
                            "provider_turn_id": meta["provider_turn_id"],
                            "completed_at": utc_now()})
            pending_path.write_text(json.dumps(pending, ensure_ascii=False, indent=2), encoding="utf-8")
    stats = compute_stats(read_log(review_dir))
    print(f"[review {args.review_id}] events={stats['events']} raw_events={stats['raw_events']} "
          f"tool_calls={stats['tool_calls_total']}")
    print(result["response"])
    return 0


def cmd_reconcile(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    if not SAFE_OPERATION_ID.fullmatch(args.operation_id):
        raise ValueError("operation_id must be a safe opaque identity")
    pending_path = review_dir / "pending-operations" / f"{args.operation_id}.json"
    pending = None
    if pending_path.exists():
        pending = json.loads(pending_path.read_text(encoding="utf-8"))
    if args.parent_provider_turn_id:
        if not isinstance(pending, dict):
            raise ValueError("parent_provider_turn_id cannot be verified without pending operation record")
        if pending.get("parent_provider_turn_id") != args.parent_provider_turn_id:
            raise ValueError("parent_provider_turn_id does not match durable pending operation")
    result = reconcile_turn(meta["session_id"], args.operation_id, timeout_sec=args.timeout_sec)
    provider_turn_id = meta.get("provider_turn_id")
    if result["state"] == "completed":
        provider_turn_id = "local:" + hashlib.sha256(
            (args.operation_id + "\0" + result["text"]).encode("utf-8")
        ).hexdigest()
    payload = {
        "review_id": args.review_id,
        "operation_id": args.operation_id,
        "session_id": meta.get("session_id"),
        "provider_checkpoint_id": meta.get("provider_checkpoint_id"),
        "provider_turn_id": provider_turn_id,
        "text": result.get("text", ""),
        **result,
    }
    print(json.dumps(payload, ensure_ascii=False) if args.json else "\n".join(
        f"{key}: {value}" for key, value in payload.items()
    ))
    return 0 if result["state"] in {"completed", "absent"} else 1


def cmd_ask(args: argparse.Namespace) -> int:
    return _continue_session(args, args.question, "ask")


def cmd_debate(args: argparse.Namespace) -> int:
    prompt = (
        "This is a consilium issue debate step.\n\n"
        f"Issue:\n{args.issue}\n\n"
        f"Finding to evaluate:\n{args.finding}\n\n"
        f"Position:\n{args.position}\n\n"
        "Instructions:\n- Assess only this issue.\n"
        "- State one of: agree / partial / disagree / withdrawn / out_of_scope.\n"
        "- Do not expand into unrelated new findings.\n"
    )
    return _continue_session(args, prompt, "debate")


def cmd_sync(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    with invocation_lock(review_dir, review_id=args.review_id, action="sync"):
        _, meta = load_meta(args.review_id)
        if meta.get("native_fork_seal"):
            raise RuntimeError("sealed_parent: sync is forbidden after native fork sealing")
        sync_sources(meta, review_dir)
        meta["updated_at"] = utc_now()
        save_meta(review_dir, meta)
        append_log(review_dir, {"ts": utc_now(), "type": "sync", "prompt": "",
                                "response": "Workspace synced from source paths.",
                                "session_id": meta["session_id"]})
    print(f"Synced review workspace: {meta['workspace_path']}")
    return 0


def cmd_show(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    payload = {"meta": meta, "stats": compute_stats(read_log(review_dir)),
               "runtime": load_runtime(review_dir)}
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
        print(f"[{i}] {event.get('ts','')} {event.get('type','')}")
        if event.get("prompt"):
            print("Prompt:")
            print(event["prompt"].strip())
        if event.get("response"):
            print("Response:")
            print(event["response"].strip())
        print()
    return 0


def cmd_stats(args: argparse.Namespace) -> int:
    review_dir, _ = load_meta(args.review_id)
    print(json.dumps(compute_stats(read_log(review_dir)), ensure_ascii=False, indent=2))
    return 0


def cmd_close(args: argparse.Namespace) -> int:
    review_dir, meta = load_meta(args.review_id)
    with invocation_lock(review_dir, review_id=args.review_id, action="close"):
        meta["status"] = "closed"
        meta["updated_at"] = utc_now()
        save_meta(review_dir, meta)
        if args.keep_sandbox:
            print(f"Marked review as closed: {args.review_id}")
        else:
            clear_sandbox_contents_preserving_lock(review_dir)
            print(f"Closed review sandbox payload: {args.review_id}")
    return 0


def _version_tuple(raw: str) -> tuple[int, ...]:
    parts = raw.strip().split(".")
    try:
        return tuple(int(part) for part in parts[:3])
    except ValueError:
        return ()


def native_fork_capability() -> dict:
    version_proc = subprocess.run(
        ["kimi", "--version"], capture_output=True, text=True, check=False, timeout=5,
    )
    version = version_proc.stdout.strip() if version_proc.returncode == 0 else ""
    web_probe = probe_web_fork() if _version_tuple(version) >= _version_tuple(MIN_NATIVE_FORK_VERSION) else {
        "ok": False, "reason": f"kimi CLI {version or 'unknown'} is below {MIN_NATIVE_FORK_VERSION}",
    }
    version_supported = _version_tuple(version) >= _version_tuple(MIN_NATIVE_FORK_VERSION)
    # REST fork is synchronous latest-head. The driver makes that head immutable
    # by sealing it under the same lock used by ask/debate and fork.
    route_available = version_supported and bool(web_probe.get("ok"))
    return {
        "native_fork": {
            "supported": route_available,
            "route": "kimi_web_rest_fork",
            "min_cli_version": MIN_NATIVE_FORK_VERSION,
            "exact_checkpoint": route_available,
            "automation_safe": route_available,
        }
    }


def cmd_capabilities(args: argparse.Namespace) -> int:
    payload = native_fork_capability()
    if args.json:
        print(json.dumps(payload, ensure_ascii=False, sort_keys=True))
    else:
        item = payload["native_fork"]
        print("native_fork: " + ("supported" if item["supported"] else "unavailable"))
        print(f"route: {item['route']}")
        print(f"min_cli_version: {item['min_cli_version']}")
        print(f"exact_checkpoint: {str(item['exact_checkpoint']).lower()}")
        print(f"automation_safe: {str(item['automation_safe']).lower()}")
    return 0


def cmd_fork(args: argparse.Namespace) -> int:
    parent_dir, parent = load_meta(args.parent_review_id)
    if parent.get("status") != "open":
        raise RuntimeError("parent review is not open")
    parent_session_id = str(parent.get("session_id") or "")
    if not SAFE_OPERATION_ID.fullmatch(args.operation_id):
        raise ValueError("operation_id must be a non-empty safe opaque identity")
    if not SNAPSHOT_DIGEST.fullmatch(args.snapshot_digest):
        raise ValueError("snapshot_digest must be sha256:<64 lowercase hex>")
    if args.provider_checkpoint_id != parent_session_id:
        raise ValueError("provider_checkpoint_id must equal the parent native session id")
    if parent.get("provider_checkpoint_id") != args.provider_checkpoint_id:
        raise ValueError("provider_checkpoint_id must match parent canonical provider cursor")
    provider_turn_id = str(parent.get("provider_turn_id") or "")
    if not provider_turn_id:
        raise ValueError("parent review has no completed provider_turn_id")
    child_review_id = args.child_review_id
    if not SAFE_REVIEW_ID.fullmatch(child_review_id) or child_review_id in {".", ".."}:
        raise ValueError("child review_id must be one safe path component")
    if child_review_id == args.parent_review_id:
        raise ValueError("child review_id must differ from parent review_id")
    child_dir = review_dir_for_id(REVIEW_ROOT, child_review_id)
    child_workspace = child_dir / "workspace"
    parent_workspace = Path(parent.get("workspace_path") or "").resolve()
    if not parent_workspace.is_relative_to(parent_dir.resolve()) or not parent_workspace.is_dir():
        raise ValueError("parent workspace_path must be inside the parent sandbox")
    child_prepared = False
    child_session_id = None
    try:
        with invocation_lock(parent_dir, review_id=args.parent_review_id, action="fork"):
            prepare_start_directory(child_dir, allow_lock_tombstone=True)
            child_prepared = True
            # Establish the stable child coordination inode before any provider
            # mutation; close/retry must keep this exact inode.
            (child_dir / "invocation.lock").touch(exist_ok=True)
            _, parent = load_meta(args.parent_review_id)
            if parent.get("session_id") != parent_session_id:
                raise RuntimeError("parent provider session changed before sealing")
            parent_runtime = load_runtime(parent_dir)
            if parent_runtime.get("state") == "running":
                raise KimiWebForkError(
                    "source_busy: Kimi source session is active; retry after it becomes idle",
                    kind="source_busy", retryable=True,
                )
            seal = parent.get("native_fork_seal")
            expected_seal = {
                "provider_checkpoint_id": args.provider_checkpoint_id,
                "snapshot_digest": args.snapshot_digest,
                "provider_turn_id": provider_turn_id,
            }
            if seal and any(seal.get(k) != v for k, v in expected_seal.items()):
                raise RuntimeError("sealed_parent: requested snapshot differs from durable parent seal")
            validate_workspace_symlinks(parent_workspace)
            if not seal:
                parent["native_fork_seal"] = {**expected_seal, "sealed_at": utc_now()}
                parent["updated_at"] = utc_now()
                save_meta(parent_dir, parent)
            shutil.copytree(parent_workspace, child_workspace, symlinks=False)
            child_session_id = fork_session(
                parent_session_id, operation_id=args.operation_id,
                timeout_sec=args.timeout_sec,
            )
        if child_session_id == parent["session_id"]:
            raise RuntimeError("native fork returned the parent session id")
        runtime = init_runtime(
            child_dir, review_id=child_review_id, action="fork", timeout_sec=args.timeout_sec,
        )
        runtime.update({"state": "completed", "phase": "finished", "finished_at": utc_now()})
        save_runtime(child_dir, runtime)
        # Мета ребёнка строится по ЯВНОМУ белому списку наследуемых полей, а не
        # копированием родительской меты целиком. `{**parent}` протаскивал в
        # ребёнка любое состояние головы родителя — в том числе печать
        # `native_fork_seal`, которая по смыслу принадлежит ТОЛЬКО родителю
        # (иммутабельность головы после форка) и в ребёнке мгновенно закрывала
        # `ask`/`debate`/`sync` через гейт `sealed_parent`. Белый список
        # убирает весь класс «ребёнок вслепую унаследовал состояние родителя»:
        # новое поле состояния родителя попадёт в ребёнка только осознанно.
        # Наследуется описание задания и контекста ревью, а не позиция головы.
        child = {
            **{
                key: parent[key]
                for key in (
                    "model", "source_root", "sources", "question", "brief",
                    "full_context", "protocol", "last_response",
                )
                if key in parent
            },
            "review_id": child_review_id,
            "session_id": child_session_id,
            "workspace_path": str(child_workspace.resolve()),
            "created_at": utc_now(),
            "updated_at": utc_now(),
            "parent_review_id": args.parent_review_id,
            "parent_session_id": parent["session_id"],
            "provider_fork_ref": child_session_id,
            "checkpoint_id": args.checkpoint_id,
            "snapshot_digest": args.snapshot_digest,
            "provider_checkpoint_id": args.provider_checkpoint_id,
            "provider_turn_id": provider_turn_id,
            "operation_id": args.operation_id,
            "exact_checkpoint_verified": True,
            "fork_route": "native",
            "prompt_consumed": False,
            "status": "open",
        }
        save_meta(child_dir, child)
        append_log(child_dir, {
            "ts": utc_now(), "type": "fork", "prompt": "", "response": "",
            "session_id": child_session_id, "parent_session_id": parent["session_id"],
        })
    except BaseException:
        if child_session_id:
            try:
                archive_session(child_session_id, timeout_sec=min(args.timeout_sec, 10))
            except BaseException:
                pass
        if child_prepared:
            try:
                clear_sandbox_contents_preserving_lock(child_dir)
            except BaseException:
                pass
        raise
    print(f"review_id: {child_review_id}")
    print(f"session_id: {child_session_id}")
    print(f"parent_review_id: {args.parent_review_id}")
    print(f"parent_session_id: {parent['session_id']}")
    print(f"provider_fork_ref: {child_session_id}")
    if args.checkpoint_id is not None:
        print(f"checkpoint_id: {args.checkpoint_id}")
    print(f"snapshot_digest: {args.snapshot_digest}")
    print(f"provider_checkpoint_id: {args.provider_checkpoint_id}")
    print(f"provider_turn_id: {provider_turn_id}")
    print(f"operation_id: {args.operation_id}")
    print("exact_checkpoint_verified: true")
    print("prompt_consumed: false")
    print(f"workspace: {child_workspace.resolve()}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Session-based Kimi CLI consilium participant with isolated sandbox workspace."
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_capabilities = sub.add_parser("capabilities", help="Probe adapter capabilities.")
    p_capabilities.add_argument("--json", action="store_true")
    p_capabilities.set_defaults(func=cmd_capabilities)

    p_start = sub.add_parser("start", help="Create a new sandbox and start a Kimi session.")
    p_start.add_argument("--question", required=True, help="Initial question.")
    p_start.add_argument("--model", default=DEFAULT_MODEL,
                         help="Kimi model alias; default берётся из конфигурации CLI.")
    p_start.add_argument("--review-id", help="Optional custom review id.")
    p_start.add_argument("--full-context", action="store_true",
                         help="Copy the whole working directory (по умолчанию — focused paths).")
    p_start.add_argument("--task", help="Short statement of the task.")
    p_start.add_argument("--goal", help="Desired outcome / goal.")
    p_start.add_argument("--requirements", help="Important requirements.")
    p_start.add_argument("--constraints", help="Important constraints.")
    p_start.add_argument("--primary-target", help="Primary artifact to review.")
    p_start.add_argument("--changed-files", nargs="*", help="Relevant changed files.")
    p_start.add_argument("--open-concerns", help="Known doubts, risks, open questions.")
    p_start.add_argument("--review-ask", help="Specific review ask.")
    p_start.add_argument("--agent-file", help="Ограниченный agent profile (RD-2, v2 engine).")
    p_start.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC,
                         help=f"Timeout одного вызова kimi. Default: {DEFAULT_TIMEOUT_SEC}")
    p_start.add_argument("paths", nargs="*",
                         help="Files/dirs to copy into sandbox. Если опущены — full context.")
    p_start.set_defaults(func=cmd_start)

    p_ask = sub.add_parser("ask", help="Follow-up question in an existing session (resume).")
    p_ask.add_argument("review_id")
    p_ask.add_argument("--question", required=True)
    p_ask.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC)
    p_ask.add_argument("--operation-id")
    p_ask.add_argument("--parent-provider-turn-id")
    p_ask.set_defaults(func=cmd_ask)

    p_debate = sub.add_parser("debate", help="Structured response-to-finding step.")
    p_debate.add_argument("review_id")
    p_debate.add_argument("--issue", required=True)
    p_debate.add_argument("--finding", required=True)
    p_debate.add_argument("--position", required=True)
    p_debate.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC)
    p_debate.set_defaults(func=cmd_debate)

    p_sync = sub.add_parser("sync", help="Refresh sandbox from source paths.")
    p_sync.add_argument("review_id")
    p_sync.set_defaults(func=cmd_sync)

    p_show = sub.add_parser("show", help="Show session metadata.")
    p_show.add_argument("review_id")
    p_show.set_defaults(func=cmd_show)

    p_status = sub.add_parser("status", help="Runtime status, heartbeat, progress counters.")
    p_status.add_argument("review_id")
    p_status.set_defaults(func=cmd_status)

    p_log = sub.add_parser("log", help="Prompt/response history.")
    p_log.add_argument("review_id")
    p_log.add_argument("--json", action="store_true")
    p_log.set_defaults(func=cmd_log)

    p_stats = sub.add_parser("stats", help="Cumulative stats.")
    p_stats.add_argument("review_id")
    p_stats.set_defaults(func=cmd_stats)

    p_close = sub.add_parser("close", help="Close session and delete sandbox by default.")
    p_close.add_argument("review_id")
    p_close.add_argument("--keep-sandbox", action="store_true",
                         help="Keep sandbox (только forensic/debug с причиной).")
    p_close.set_defaults(func=cmd_close)

    p_fork = sub.add_parser("fork", help="Create a provider-native child session.")
    p_fork.add_argument("parent_review_id")
    p_fork.add_argument("--child-review-id", required=True)
    p_fork.add_argument("--checkpoint-id")
    p_fork.add_argument("--operation-id", required=True)
    p_fork.add_argument("--snapshot-digest", required=True)
    p_fork.add_argument("--provider-checkpoint-id", required=True)
    p_fork.add_argument("--question")
    p_fork.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC)
    p_fork.set_defaults(func=cmd_fork)

    p_reconcile = sub.add_parser("reconcile", help="Inspect provider history for a durable turn operation.")
    p_reconcile.add_argument("review_id")
    p_reconcile.add_argument("--operation-id", required=True)
    p_reconcile.add_argument("--parent-provider-turn-id")
    p_reconcile.add_argument("--timeout-sec", type=int, default=30)
    p_reconcile.add_argument("--json", action="store_true")
    p_reconcile.set_defaults(func=cmd_reconcile)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    REVIEW_ROOT.mkdir(parents=True, exist_ok=True)
    try:
        return args.func(args)
    except Exception as e:
        if isinstance(e, SessionBusyError):
            print("error_kind: session_busy", file=sys.stderr)
        if isinstance(e, KimiWebForkError):
            print(f"error_kind: {e.kind}", file=sys.stderr)
            print(f"retryable: {str(e.retryable).lower()}", file=sys.stderr)
        print(f"Error: {e}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

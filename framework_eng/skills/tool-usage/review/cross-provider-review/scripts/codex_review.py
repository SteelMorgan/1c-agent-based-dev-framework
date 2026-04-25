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
from typing import Any


REVIEW_ROOT = Path(".review-sandboxes")
DEFAULT_MODEL = "gpt-5.5"
DEFAULT_REASONING_EFFORT = "high"
DEFAULT_SANDBOX = "read-only"
DEFAULT_TIMEOUT_SEC = 1800
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
    ".claude",
    ".codex",
    ".cursor",
    ".windsurf",
    ".idea",
}
DEFAULT_COPY_MODE = "hardlink"
SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
REVIEW_PROMPT_PATH = SKILL_DIR / "references" / "review-prompt.md"
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


def copy_path(src: Path, dst: Path) -> None:
    if src.is_dir():
        shutil.copytree(src, dst, dirs_exist_ok=True, symlinks=True)
    else:
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst, follow_symlinks=False)


def hardlink_path(src: Path, dst: Path) -> None:
    """Mirror src into dst using hardlinks for files. Preserves symlinks as symlinks.
    Recreates directory structure. Raises OSError on cross-device links or unsupported FS."""
    if src.is_symlink():
        dst.parent.mkdir(parents=True, exist_ok=True)
        if dst.is_symlink() or dst.exists():
            remove_dest(dst)
        os.symlink(os.readlink(src), dst)
        return
    if src.is_dir():
        dst.mkdir(parents=True, exist_ok=True)
        for child in src.iterdir():
            hardlink_path(child, dst / child.name)
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    if dst.exists():
        remove_dest(dst)
    os.link(src, dst)


def materialize_path(src: Path, dst: Path, *, mode: str) -> str:
    """Materialize src at dst using requested mode. Returns the actually used mode."""
    if mode == "hardlink":
        try:
            hardlink_path(src, dst)
            return "hardlink"
        except OSError as e:
            print(
                f"warning: hardlink failed for {src} ({e}); falling back to copy",
                file=sys.stderr,
            )
    copy_path(src, dst)
    return "copy"


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
    return bool(parts & DEFAULT_EXCLUDES)


def copy_full_context(source_root: Path, workspace: Path, *, mode: str = DEFAULT_COPY_MODE) -> list[dict[str, str]]:
    sources: list[dict[str, str]] = []
    workspace_resolved = workspace.resolve()
    for child in sorted(source_root.iterdir()):
        if should_exclude(child, source_root):
            continue
        try:
            child_resolved = child.resolve()
            if child_resolved == workspace_resolved or workspace_resolved.is_relative_to(child_resolved):
                continue
        except OSError:
            pass
        dest_rel = safe_rel_to_cwd(child, source_root)
        materialize_path(child, workspace / dest_rel, mode=mode)
        sources.append({
            "source": str(child.resolve()),
            "dest_rel": dest_rel.as_posix(),
        })
    return sources


def sync_sources(meta: dict[str, Any]) -> None:
    workspace = Path(meta["workspace_path"])
    mode = meta.get("copy_mode", DEFAULT_COPY_MODE)
    for entry in meta["sources"]:
        src = Path(entry["source"])
        dst = workspace / entry["dest_rel"]
        if not src.exists():
            continue
        if dst.exists() or dst.is_symlink():
            remove_dest(dst)
        materialize_path(src, dst, mode=mode)


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
    with RUNTIME_LOCK:
        path = runtime_path(review_dir)
        tmp_path = path.with_name(path.name + ".tmp")
        tmp_path.write_text(json.dumps(runtime, ensure_ascii=False, indent=2), encoding="utf-8")
        tmp_path.replace(path)


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
    path = review_dir / "review.json"
    tmp_path = path.with_name(path.name + ".tmp")
    tmp_path.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp_path.replace(path)


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
    model: str,
    reasoning_effort: str,
    workspace: Path | None,
    session_id: str | None,
    review_dir: Path,
    timeout_sec: int,
) -> dict[str, Any]:
    result_file = review_dir / "last-response.md"
    stdout_log = review_dir / "stdout.jsonl"
    stderr_log = review_dir / "stderr.log"

    if session_id:
        if workspace is None:
            raise ValueError("workspace is required when resuming a Codex review")
        cmd = [
            "codex",
            "exec",
            "resume",
            "-m",
            model,
            "-c",
            f'model_reasoning_effort="{reasoning_effort}"',
            "-c",
            f'sandbox_mode="{DEFAULT_SANDBOX}"',
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
            "-m",
            model,
            "-c",
            f'model_reasoning_effort="{reasoning_effort}"',
            "--sandbox",
            DEFAULT_SANDBOX,
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
    })
    return {
        "session_id": detected_session_id,
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
    workspace.mkdir(parents=True, exist_ok=True)
    init_runtime(review_dir, review_id=review_id, action="start", timeout_sec=args.timeout_sec)
    mark_phase(review_dir, "copying")

    copy_mode = args.copy_mode
    if args.full_context or not args.paths:
        sources = copy_full_context(cwd, workspace, mode=copy_mode)
    else:
        sources = []
        for raw in args.paths:
            src = Path(raw)
            if not src.exists():
                raise FileNotFoundError(f"Path does not exist: {raw}")
            dest_rel = safe_rel_to_cwd(src, cwd)
            materialize_path(src, workspace / dest_rel, mode=copy_mode)
            sources.append({
                "source": str(src.resolve()),
                "dest_rel": dest_rel.as_posix(),
            })

    brief = build_brief(args)
    write_review_context(workspace, review_id=review_id, brief=brief, sources=sources)
    prompt = build_prompt(args)
    result = run_codex(
        prompt,
        model=args.model,
        reasoning_effort=args.reasoning_effort,
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
        "copy_mode": copy_mode,
        "question": args.question,
        "brief": brief,
        "full_context": bool(args.full_context or not args.paths),
        "session_id": result["session_id"],
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
        "raw_events": result["raw_events"],
        "stderr": result["stderr"],
    })
    stats = compute_stats(read_log(review_dir))
    print(f"review_id: {review_id}")
    print(f"session_id: {result['session_id']}")
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
    prompt = f"{load_review_prompt()}\n\n{args.question}".strip()
    result = run_codex(
        prompt,
        model=meta["model"],
        reasoning_effort=meta.get("reasoning_effort") or DEFAULT_REASONING_EFFORT,
        workspace=Path(meta["workspace_path"]),
        session_id=meta["session_id"],
        review_dir=review_dir,
        timeout_sec=args.timeout_sec,
    )
    meta["updated_at"] = utc_now()
    if result["session_id"]:
        meta["session_id"] = result["session_id"]
    meta["last_response"] = result["response"]
    save_meta(review_dir, meta)
    append_log(review_dir, {
        "ts": utc_now(),
        "type": event_type,
        "prompt": prompt,
        "response": result["response"],
        "session_id": result["session_id"],
        "raw_events": result["raw_events"],
        "stderr": result["stderr"],
    })
    stats = compute_stats(read_log(review_dir))
    print(
        f"[review {args.review_id}] events={stats['events']} input_tokens={stats['input_tokens']} "
        f"output_tokens={stats['output_tokens']} total_tokens={stats['total_tokens']} "
        f"tool_calls={stats['tool_calls_total']} tools={json.dumps(stats['tool_calls_by_name'], ensure_ascii=False)}"
    )
    print(result["response"])
    return 0


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
    sync_sources(meta)
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
    if meta_path.exists():
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        status = meta.get("status")
    else:
        status = "starting"
    payload = {
        "review_id": args.review_id,
        "status": status,
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
        shutil.rmtree(review_dir, ignore_errors=True)
        print(f"Closed and deleted review sandbox: {args.review_id}")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Session-based Codex review runner with isolated sandbox workspace."
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_start = sub.add_parser("start", help="Create a new review sandbox and start a Codex review session.")
    p_start.add_argument("--question", required=True, help="Initial review question.")
    p_start.add_argument("--model", default=DEFAULT_MODEL, help=f"Codex model. Default: {DEFAULT_MODEL}")
    p_start.add_argument("--reasoning-effort", default=DEFAULT_REASONING_EFFORT, help=f"Reasoning effort. Default: {DEFAULT_REASONING_EFFORT}")
    p_start.add_argument("--review-id", help="Optional custom review id.")
    p_start.add_argument("--full-context", action="store_true", help="Mirror the whole working directory into the review sandbox, excluding known heavy/generated dirs.")
    p_start.add_argument("--copy-mode", choices=["hardlink", "copy"], default=DEFAULT_COPY_MODE, help=f"How to materialize source files in the sandbox. 'hardlink' (default) is near-instant and uses ~0 disk; 'copy' makes a full byte copy. Falls back to 'copy' if hardlinks are unsupported (e.g. cross-device).")
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

    p_ask = sub.add_parser("ask", help="Ask a follow-up question in an existing Codex review session.")
    p_ask.add_argument("review_id", help="Existing review id.")
    p_ask.add_argument("--question", required=True, help="Follow-up question.")
    p_ask.add_argument("--timeout-sec", type=int, default=DEFAULT_TIMEOUT_SEC, help=f"Timeout for a single Codex invocation in seconds. Default: {DEFAULT_TIMEOUT_SEC}")
    p_ask.set_defaults(func=cmd_ask)

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
        return args.func(args)
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

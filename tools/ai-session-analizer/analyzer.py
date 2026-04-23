#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse


ROOT = Path(__file__).resolve().parent
WEB_ROOT = ROOT / "web"
DEFAULT_CLAUDE_ROOT = Path.home() / ".claude" / "projects"
DEFAULT_CODEX_ROOT = Path.home() / ".codex" / "sessions"


TASK_PATTERNS = [
    re.compile(r"/tasks/([0-9]+(?:[-_][a-zA-Z0-9._-]+)?)", re.IGNORECASE),
    re.compile(r"\bTASK[-\s:]?([0-9]+)\b", re.IGNORECASE),
    re.compile(r"\btask[-\s:]?([0-9]+)\b", re.IGNORECASE),
    re.compile(r"\bзадач[аеи]\s*[:#]?\s*([0-9]+)\b", re.IGNORECASE),
]
TASK_DIR_PATTERN = re.compile(r"([/~\w .-]+/tasks/[^\s`'\"<>]+)", re.IGNORECASE)

VANESSA_KEYWORDS = [
    "vanessa",
    "bddrunner",
    "vrunner",
    ".feature",
    "allure",
    "cucumber",
    "json report",
    "junit",
    "feature file",
    "vanessa automation",
    "va-params",
    "scenario",
    "сценар",
    "ванес",
    "bdd",
]
SCREENSHOT_KEYWORDS = [
    "screenshot",
    "screen shot",
    ".png",
    ".jpg",
    ".jpeg",
    ".webp",
    "view_image",
    "take_screenshot",
    "image_query",
    "local_image",
    "imagegrab",
    "скриншот",
    "изображен",
    "картинк",
]
TEST_KEYWORDS = [
    "pytest",
    "unittest",
    "run_all_tests",
    "run_module_tests",
    "yaxunit",
    "check_syntax",
    "test run",
    "test failure",
    "npx playwright test",
    "vanessa-run",
    "feature runner",
]
REVIEW_KEYWORDS = [
    "review",
    "reviewer",
    "codex-review",
    "adversarial",
    "ревью",
    "проверка дизайна",
]
PLANNING_KEYWORDS = [
    "technical-design",
    "task-breakdown",
    "update_plan",
    "plan ",
    "planner",
    "architect",
    "spec.md",
    "спецификац",
    "дизайн",
]
CODE_WRITE_KEYWORDS = [
    "apply_patch",
    "multiedit",
    "edit",
    "write",
    "replace_all",
    "old_string",
    "new_string",
    "cat >",
    "tee ",
    "sed -i",
    "perl -0pi",
]
READ_ONLY_COMMAND_PREFIXES = (
    "rg ",
    "cat ",
    "sed -n",
    "find ",
    "ls ",
    "git diff",
    "git show",
    "git log",
    "pwd",
    "head ",
    "tail ",
)


@dataclass
class SignalBag:
    texts: list[str] = field(default_factory=list)
    tool_names: list[str] = field(default_factory=list)
    commands: list[str] = field(default_factory=list)
    flags: set[str] = field(default_factory=set)

    def add_text(self, value: str | None) -> None:
        if value:
            self.texts.append(value)

    def add_tool(self, value: str | None) -> None:
        if value:
            self.tool_names.append(value)

    def add_command(self, value: str | None) -> None:
        if value:
            self.commands.append(value)

    def flattened(self) -> str:
        return "\n".join(self.texts + self.tool_names + self.commands)

    def preview(self, limit: int = 220) -> str:
        merged = self.flattened().replace("\n", " ").strip()
        return merged[:limit]


def safe_json_loads(text: str) -> Any:
    try:
        return json.loads(text)
    except Exception:
        return None


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except Exception:
                continue
    return rows


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def normalize_task_id(raw: str) -> str:
    cleaned = raw.strip().strip("`'\"")
    digits = re.match(r"^([0-9]+)", cleaned)
    if digits:
        return digits.group(1)
    return cleaned.replace("_", "-")


def extract_task_hints(*texts: str | None) -> tuple[str | None, str | None]:
    task_dir: str | None = None
    task_id: str | None = None
    for text in texts:
        if not text:
            continue
        if task_dir is None:
            match = TASK_DIR_PATTERN.search(text)
            if match:
                task_dir = match.group(1).strip("`'\"")
        if task_id is None:
            for pattern in TASK_PATTERNS:
                match = pattern.search(text)
                if match:
                    task_id = normalize_task_id(match.group(1))
                    break
        if task_dir and task_id:
            break
    if task_id is None and task_dir:
        last = Path(task_dir).name
        digits = re.match(r"([0-9]+(?:[-_][a-zA-Z0-9._-]+)?)", last)
        if digits:
            task_id = normalize_task_id(digits.group(1))
    return task_id, task_dir


def compact_ws(path: str | None) -> str:
    if not path:
        return "<unknown>"
    try:
        resolved = Path(path).resolve()
        current = resolved if resolved.is_dir() else resolved.parent
        while True:
            if (current / ".claude").exists() or (current / ".agents").exists():
                return str(current)
            if current.parent == current:
                break
            current = current.parent
    except Exception:
        pass
    return path


def task_sort_key(value: str) -> tuple[int, str]:
    match = re.match(r"^([0-9]+)", value)
    if match:
        return (int(match.group(1)), value)
    return (sys.maxsize, value)


def aggregate_by(records: list[dict[str, Any]], key: str) -> list[dict[str, Any]]:
    grouped: dict[str, dict[str, Any]] = {}
    for row in records:
        group = str(row.get(key) or "<unknown>")
        bucket = grouped.setdefault(
            group,
            {
                "key": group,
                "records": 0,
                "input_tokens": 0,
                "output_tokens": 0,
                "cache_read_tokens": 0,
                "cache_creation_tokens": 0,
                "total_tokens": 0,
                "providers": Counter(),
            },
        )
        bucket["records"] += 1
        bucket["input_tokens"] += int(row.get("input_tokens", 0))
        bucket["output_tokens"] += int(row.get("output_tokens", 0))
        bucket["cache_read_tokens"] += int(row.get("cache_read_tokens", 0))
        bucket["cache_creation_tokens"] += int(row.get("cache_creation_tokens", 0))
        bucket["total_tokens"] += int(row.get("total_tokens", 0))
        bucket["providers"][row.get("provider", "unknown")] += 1
    result = list(grouped.values())
    for bucket in result:
        bucket["providers"] = dict(bucket["providers"])
    result.sort(key=lambda item: (-item["total_tokens"], item["key"]))
    return result


def summarize_records(records: list[dict[str, Any]]) -> dict[str, int]:
    return {
        "records": len(records),
        "input_tokens": sum(int(row.get("input_tokens", 0)) for row in records),
        "output_tokens": sum(int(row.get("output_tokens", 0)) for row in records),
        "cache_read_tokens": sum(int(row.get("cache_read_tokens", 0)) for row in records),
        "cache_creation_tokens": sum(int(row.get("cache_creation_tokens", 0)) for row in records),
        "total_tokens": sum(int(row.get("total_tokens", 0)) for row in records),
    }


def most_common_value(records: list[dict[str, Any]], key: str, fallback: str = "<unknown>") -> str:
    values = [str(row.get(key) or fallback) for row in records]
    if not values:
        return fallback
    return Counter(values).most_common(1)[0][0]


def build_group_node(
    key: str,
    label: str,
    records: list[dict[str, Any]],
    *,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    node = {
        "key": key,
        "label": label,
        "summary": summarize_records(records),
        "providers": dict(Counter(str(row.get("provider", "unknown")) for row in records)),
        "step_types": dict(Counter(str(row.get("step_type", "unknown")) for row in records)),
    }
    if extra:
        node.update(extra)
    return node


def build_directory_nodes(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in records:
        buckets[str(row.get("working_directory", "<unknown>"))].append(row)
    nodes = [
        build_group_node(
            working_directory,
            working_directory,
            grouped,
            extra={
                "task_count": len({str(row.get("task_id", "<unknown>")) for row in grouped}),
                "agent_count": len({str(row.get("agent_id", "<unknown>")) for row in grouped}),
            },
        )
        for working_directory, grouped in buckets.items()
    ]
    nodes.sort(key=lambda item: (-item["summary"]["total_tokens"], item["label"]))
    return nodes


def build_task_nodes(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in records:
        buckets[str(row.get("task_id", "<unknown>"))].append(row)
    nodes = []
    for task_id, grouped in buckets.items():
        task_dir = most_common_value(grouped, "task_dir")
        label = task_id if task_id != "<unknown>" else task_dir
        nodes.append(
            build_group_node(
                task_id,
                label,
                grouped,
                extra={
                    "task_id": task_id,
                    "task_dir": task_dir,
                    "agent_count": len({str(row.get("agent_id", "<unknown>")) for row in grouped}),
                },
            )
        )
    nodes.sort(key=lambda item: (-item["summary"]["total_tokens"], task_sort_key(item["task_id"])))
    return nodes


def build_agent_nodes(records: list[dict[str, Any]]) -> list[dict[str, Any]]:
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in records:
        buckets[str(row.get("agent_id", "<unknown>"))].append(row)
    nodes = []
    for agent_id, grouped in buckets.items():
        agent_label = most_common_value(grouped, "agent_label")
        agent_type = most_common_value(grouped, "agent_type")
        label = f"{agent_label} · {agent_type}" if agent_label != agent_type else agent_label
        nodes.append(
            build_group_node(
                agent_id,
                label,
                grouped,
                extra={
                    "agent_id": agent_id,
                    "agent_type": agent_type,
                    "agent_label": agent_label,
                    "session_count": len({str(row.get("session_id", "<unknown>")) for row in grouped}),
                },
            )
        )
    nodes.sort(key=lambda item: (-item["summary"]["total_tokens"], item["label"]))
    return nodes


def build_record_rows(records: list[dict[str, Any]], limit: int = 200) -> list[dict[str, Any]]:
    rows = sorted(records, key=lambda row: int(row.get("total_tokens", 0)), reverse=True)[:limit]
    return [
        {
            "provider": row.get("provider"),
            "timestamp": row.get("timestamp"),
            "agent_type": row.get("agent_type"),
            "agent_label": row.get("agent_label"),
            "step_type": row.get("step_type"),
            "step_reason": row.get("step_reason"),
            "signal_preview": row.get("signal_preview"),
            "total_tokens": row.get("total_tokens", 0),
            "input_tokens": row.get("input_tokens", 0),
            "output_tokens": row.get("output_tokens", 0),
            "cache_read_tokens": row.get("cache_read_tokens", 0),
            "cache_creation_tokens": row.get("cache_creation_tokens", 0),
            "session_id": row.get("session_id"),
            "request_id": row.get("request_id"),
            "task_id": row.get("task_id"),
            "task_dir": row.get("task_dir"),
        }
        for row in rows
    ]


def build_summary(records: list[dict[str, Any]]) -> dict[str, Any]:
    totals = summarize_records(records)
    filters = {
        "providers": sorted({row.get("provider", "<unknown>") for row in records}),
        "working_directories": sorted({row.get("working_directory", "<unknown>") for row in records}),
        "task_ids": sorted({row.get("task_id", "<unknown>") for row in records}, key=task_sort_key),
        "task_dirs": sorted({row.get("task_dir", "<unknown>") for row in records}),
        "agent_types": sorted({row.get("agent_type", "<unknown>") for row in records}),
        "step_types": sorted({row.get("step_type", "<unknown>") for row in records}),
        "git_branches": sorted({row.get("git_branch", "<unknown>") for row in records}),
    }
    aggregates = {
        "by_provider": aggregate_by(records, "provider"),
        "by_working_directory": aggregate_by(records, "working_directory"),
        "by_task_id": aggregate_by(records, "task_id"),
        "by_agent_type": aggregate_by(records, "agent_type"),
        "by_step_type": aggregate_by(records, "step_type"),
        "by_git_branch": aggregate_by(records, "git_branch"),
        "by_session_id": aggregate_by(records, "session_id")[:100],
    }
    return {"totals": totals, "filters": filters, "aggregates": aggregates}


def classify_step(signals: SignalBag, *, agent_type: str | None, provider: str) -> tuple[str, list[str]]:
    haystack = signals.flattened().lower()
    reasons: list[str] = []

    def contains_any(words: list[str]) -> list[str]:
        return [word for word in words if word in haystack]

    if matched := contains_any(VANESSA_KEYWORDS):
        reasons.extend(matched[:5])
        return "vanessa_log_analysis", reasons

    if matched := contains_any(SCREENSHOT_KEYWORDS):
        reasons.extend(matched[:5])
        return "screenshot_analysis", reasons

    tool_names = {name.lower() for name in signals.tool_names}
    if any(name in tool_names for name in ("edit", "write", "multiedit", "apply_patch")):
        reasons.extend(sorted(tool_names & {"edit", "write", "multiedit", "apply_patch"}))
        return "code_writing", reasons
    if matched := contains_any(CODE_WRITE_KEYWORDS):
        reasons.extend(matched[:5])
        return "code_writing", reasons

    if matched := contains_any(TEST_KEYWORDS):
        reasons.extend(matched[:5])
        return "test_execution", reasons

    normalized_agent = (agent_type or "").lower()
    if "review" in normalized_agent or "reviewer" in normalized_agent:
        reasons.append(f"agent_type={agent_type}")
        return "review", reasons
    if matched := contains_any(REVIEW_KEYWORDS):
        reasons.extend(matched[:5])
        return "review", reasons

    if normalized_agent in {"plan", "planner", "architect", "analyst"}:
        reasons.append(f"agent_type={agent_type}")
        return "planning", reasons
    if matched := contains_any(PLANNING_KEYWORDS):
        reasons.extend(matched[:5])
        return "planning", reasons

    if provider == "claude" and any(name.lower() in {"read", "grep", "glob", "ls", "toolsearch"} for name in signals.tool_names):
        reasons.append("read_tool")
        return "code_reading", reasons

    commands = [command.strip().lower() for command in signals.commands]
    if any(command.startswith(READ_ONLY_COMMAND_PREFIXES) for command in commands):
        reasons.append("readonly_command")
        return "code_reading", reasons

    if any(name.lower() in {"exec_command", "read", "grep", "glob", "ls", "definition", "hover"} for name in signals.tool_names):
        reasons.append("tool_lookup")
        return "code_reading", reasons

    if signals.texts or signals.commands or signals.tool_names:
        return "other", []
    return "unclassified", []


def content_items_to_text(content: Any) -> list[str]:
    texts: list[str] = []
    if isinstance(content, str):
        texts.append(content)
    elif isinstance(content, list):
        for item in content:
            if isinstance(item, str):
                texts.append(item)
                continue
            if not isinstance(item, dict):
                continue
            item_type = item.get("type")
            if item_type in {"text", "input_text", "output_text"}:
                texts.append(str(item.get("text", "")))
            elif item_type == "tool_use":
                texts.append(json.dumps(item.get("input", {}), ensure_ascii=False))
            elif item_type == "tool_result":
                texts.append(str(item.get("content", "")))
    return texts


def extract_first_texts(rows: list[dict[str, Any]], limit: int = 12) -> list[str]:
    texts: list[str] = []
    for row in rows:
        if len(texts) >= limit:
            break
        row_type = row.get("type")
        payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
        if row_type == "event_msg" and payload.get("message"):
            texts.append(str(payload["message"]))
        if row_type == "response_item" and payload.get("type") == "message":
            texts.extend(content_items_to_text(payload.get("content")))
    return texts[:limit]


def parse_claude_logs(base_dir: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    if not base_dir.exists():
        return records

    for jsonl_path in sorted(base_dir.glob("**/*.jsonl")):
        if jsonl_path.name.endswith(".meta.json"):
            continue

        rows = read_jsonl(jsonl_path)
        if not rows:
            continue

        is_subagent = "/subagents/" in jsonl_path.as_posix()
        meta_path = jsonl_path.with_suffix(".meta.json")
        meta = {}
        if is_subagent and meta_path.exists():
            try:
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
            except Exception:
                meta = {}

        first_texts: list[str] = []
        working_directory = None
        session_id = None
        git_branch = None
        parent_session_id = None
        agent_id = None
        prompt_id = None
        agent_type = meta.get("agentType") if meta else None
        agent_description = meta.get("description") if meta else None

        for row in rows[:20]:
            working_directory = working_directory or row.get("cwd")
            session_id = session_id or row.get("sessionId")
            git_branch = git_branch or row.get("gitBranch")
            agent_id = agent_id or row.get("agentId")
            prompt_id = prompt_id or row.get("promptId")
            message = row.get("message") if isinstance(row.get("message"), dict) else None
            if message:
                first_texts.extend(content_items_to_text(message.get("content")))
            if not parent_session_id and row.get("sourceToolAssistantUUID"):
                parent_session_id = session_id

        task_id, task_dir = extract_task_hints(*first_texts, agent_description)
        if task_id is None:
            task_id = "<unknown>"
        if task_dir is None:
            task_dir = "<unknown>"

        grouped: dict[str, dict[str, Any]] = {}
        for row in rows:
            message = row.get("message") if isinstance(row.get("message"), dict) else None
            if row.get("type") != "assistant" or not message:
                continue
            usage = message.get("usage") if isinstance(message.get("usage"), dict) else None
            request_id = row.get("requestId") or message.get("id") or row.get("uuid")
            if not request_id or not usage:
                continue
            bucket = grouped.setdefault(
                str(request_id),
                {
                    "timestamp": row.get("timestamp"),
                    "model": message.get("model", "<unknown>"),
                    "usage": usage,
                    "signals": SignalBag(),
                },
            )
            content = message.get("content")
            if isinstance(content, list):
                for item in content:
                    if not isinstance(item, dict):
                        continue
                    item_type = item.get("type")
                    if item_type == "tool_use":
                        bucket["signals"].add_tool(str(item.get("name")))
                        bucket["signals"].add_text(json.dumps(item.get("input", {}), ensure_ascii=False))
                        command = item.get("input", {}).get("command") if isinstance(item.get("input"), dict) else None
                        bucket["signals"].add_command(command)
                    elif item_type in {"text", "input_text", "output_text"}:
                        bucket["signals"].add_text(str(item.get("text", "")))
                    elif item_type == "thinking":
                        bucket["signals"].flags.add("thinking")
            else:
                for text in content_items_to_text(content):
                    bucket["signals"].add_text(text)

        for request_id, bucket in grouped.items():
            usage = bucket["usage"]
            input_tokens = int(usage.get("input_tokens", 0))
            output_tokens = int(usage.get("output_tokens", 0))
            cache_read_tokens = int(usage.get("cache_read_input_tokens", 0))
            cache_creation_tokens = int(usage.get("cache_creation_input_tokens", 0))
            step_type, reasons = classify_step(bucket["signals"], agent_type=agent_type, provider="claude")
            records.append(
                {
                    "provider": "claude",
                    "working_directory": compact_ws(working_directory),
                    "task_id": task_id,
                    "task_dir": task_dir,
                    "session_id": session_id or "<unknown>",
                    "parent_session_id": parent_session_id or session_id or "<unknown>",
                    "agent_id": agent_id or ("main" if not is_subagent else "<unknown>"),
                    "agent_type": agent_type or ("main" if not is_subagent else "subagent"),
                    "agent_label": agent_description or agent_type or ("main" if not is_subagent else "subagent"),
                    "git_branch": git_branch or "<unknown>",
                    "model": bucket["model"],
                    "timestamp": bucket["timestamp"],
                    "step_type": step_type,
                    "step_reason": ", ".join(reasons),
                    "signal_preview": bucket["signals"].preview(),
                    "request_id": request_id,
                    "prompt_id": prompt_id or "<unknown>",
                    "input_tokens": input_tokens,
                    "output_tokens": output_tokens,
                    "cache_read_tokens": cache_read_tokens,
                    "cache_creation_tokens": cache_creation_tokens,
                    "total_tokens": input_tokens + output_tokens + cache_read_tokens + cache_creation_tokens,
                }
            )
    return records


def collect_codex_signal(row: dict[str, Any], signals: SignalBag) -> None:
    row_type = row.get("type")
    payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}

    if row_type == "event_msg":
        message = payload.get("message")
        if message:
            signals.add_text(str(message))
        if payload.get("type") == "agent_message":
            signals.flags.add("assistant_commentary")
        return

    if row_type != "response_item":
        return

    payload_type = payload.get("type")
    if payload_type == "message":
        signals.add_text("\n".join(content_items_to_text(payload.get("content"))))
    elif payload_type == "function_call":
        name = str(payload.get("name", ""))
        signals.add_tool(name)
        args = safe_json_loads(str(payload.get("arguments", "")))
        if isinstance(args, dict):
            if "cmd" in args:
                signals.add_command(str(args["cmd"]))
            signals.add_text(json.dumps(args, ensure_ascii=False))
    elif payload_type == "function_call_output":
        output = str(payload.get("output", ""))
        signals.add_text(output[:400])


def parse_codex_logs(base_dir: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    if not base_dir.exists():
        return records

    for jsonl_path in sorted(base_dir.glob("**/*.jsonl")):
        rows = read_jsonl(jsonl_path)
        if not rows:
            continue

        session_meta = next((row for row in rows if row.get("type") == "session_meta"), None)
        payload = session_meta.get("payload", {}) if isinstance(session_meta, dict) else {}
        source = payload.get("source")
        git = payload.get("git", {}) if isinstance(payload.get("git"), dict) else {}
        working_directory = payload.get("cwd")
        session_id = payload.get("id") or jsonl_path.stem
        git_branch = git.get("branch") or "<unknown>"
        parent_session_id = session_id
        agent_id = session_id
        agent_type = "main"
        agent_label = "main"

        if isinstance(source, dict) and isinstance(source.get("subagent"), dict):
            spawn = source["subagent"].get("thread_spawn", {})
            parent_session_id = spawn.get("parent_thread_id") or session_id
            agent_id = session_id
            agent_type = "subagent"
            agent_label = spawn.get("agent_nickname") or "subagent"

        intro_texts = extract_first_texts(rows, limit=20)
        task_id, task_dir = extract_task_hints(*intro_texts)
        if task_id is None:
            task_id = "<unknown>"
        if task_dir is None:
            task_dir = "<unknown>"

        window_signals = SignalBag()
        current_turn_id = None

        for row in rows:
            row_type = row.get("type")
            payload = row.get("payload") if isinstance(row.get("payload"), dict) else {}
            if row_type == "event_msg" and payload.get("turn_id"):
                current_turn_id = payload.get("turn_id")

            if row_type == "event_msg" and payload.get("type") == "token_count":
                info = payload.get("info", {})
                last = info.get("last_token_usage", {}) if isinstance(info, dict) else {}
                input_tokens = int(last.get("input_tokens", 0))
                output_tokens = int(last.get("output_tokens", 0))
                cache_read_tokens = int(last.get("cached_input_tokens", 0))
                cache_creation_tokens = 0
                step_type, reasons = classify_step(window_signals, agent_type=agent_type, provider="codex")
                records.append(
                    {
                        "provider": "codex",
                        "working_directory": compact_ws(working_directory),
                        "task_id": task_id,
                        "task_dir": task_dir,
                        "session_id": session_id,
                        "parent_session_id": parent_session_id,
                        "agent_id": agent_id,
                        "agent_type": agent_type,
                        "agent_label": agent_label,
                        "git_branch": git_branch,
                        "model": payload.get("model") or "<unknown>",
                        "timestamp": row.get("timestamp"),
                        "step_type": step_type,
                        "step_reason": ", ".join(reasons),
                        "signal_preview": window_signals.preview(),
                        "request_id": current_turn_id or session_id,
                        "prompt_id": current_turn_id or "<unknown>",
                        "input_tokens": input_tokens,
                        "output_tokens": output_tokens,
                        "cache_read_tokens": cache_read_tokens,
                        "cache_creation_tokens": cache_creation_tokens,
                        "total_tokens": input_tokens + output_tokens + cache_read_tokens + cache_creation_tokens,
                    }
                )
                window_signals = SignalBag()
                continue

            collect_codex_signal(row, window_signals)

    return records


def build_dataset(claude_root: Path, codex_root: Path) -> dict[str, Any]:
    records = parse_claude_logs(claude_root) + parse_codex_logs(codex_root)
    records.sort(key=lambda item: (item.get("timestamp") or "", item["provider"], item["session_id"]))
    return {
        "generated_at": iso_now(),
        "summary": build_summary(records),
        "records": records,
    }


def write_dataset(dataset: dict[str, Any], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(dataset, ensure_ascii=False, indent=2), encoding="utf-8")


class AppServer(BaseHTTPRequestHandler):
    dataset: dict[str, Any] = {}

    def _send_json(self, payload: dict[str, Any], status: int = 200) -> None:
        encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        try:
            self.wfile.write(encoded)
        except (BrokenPipeError, ConnectionResetError):
            return

    def _send_file(self, path: Path, content_type: str) -> None:
        if not path.exists():
            self.send_error(404)
            return
        body = path.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            return

    def do_GET(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        params = parse_qs(parsed.query)
        if parsed.path == "/api/data":
            self._send_json(
                {
                    "generated_at": self.dataset.get("generated_at"),
                    "summary": self.dataset.get("summary", {}),
                }
            )
            return
        if parsed.path == "/api/directories":
            records = filter_records(self.dataset.get("records", []), params)
            self._send_json(
                {
                    "nodes": build_directory_nodes(records),
                    "summary": build_summary(records),
                }
            )
            return
        if parsed.path == "/api/tasks":
            records = filter_records(self.dataset.get("records", []), params)
            self._send_json(
                {
                    "nodes": build_task_nodes(records),
                    "summary": build_summary(records),
                }
            )
            return
        if parsed.path == "/api/agents":
            records = filter_records(self.dataset.get("records", []), params)
            self._send_json(
                {
                    "nodes": build_agent_nodes(records),
                    "summary": build_summary(records),
                }
            )
            return
        if parsed.path == "/api/records":
            records = filter_records(self.dataset.get("records", []), params)
            self._send_json({"records": build_record_rows(records), "summary": build_summary(records)})
            return
        if parsed.path in {"/", "/index.html"}:
            self._send_file(WEB_ROOT / "index.html", "text/html; charset=utf-8")
            return
        if parsed.path == "/app.js":
            self._send_file(WEB_ROOT / "app.js", "application/javascript; charset=utf-8")
            return
        if parsed.path == "/styles.css":
            self._send_file(WEB_ROOT / "styles.css", "text/css; charset=utf-8")
            return
        self.send_error(404)

    def do_HEAD(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        if parsed.path == "/api/data":
            encoded = json.dumps(
                {
                    "generated_at": self.dataset.get("generated_at"),
                    "summary": self.dataset.get("summary", {}),
                },
                ensure_ascii=False,
            ).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(encoded)))
            self.end_headers()
            return
        if parsed.path in {"/api/directories", "/api/tasks", "/api/agents", "/api/records"}:
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            return
        if parsed.path in {"/", "/index.html"}:
            body = (WEB_ROOT / "index.html").read_bytes()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            return
        self.send_error(404)


def filter_records(records: list[dict[str, Any]], params: dict[str, list[str]]) -> list[dict[str, Any]]:
    filters = {
        "provider": set(params.get("provider", [])),
        "working_directory": set(params.get("working_directory", [])),
        "task_id": set(params.get("task_id", [])),
        "agent_type": set(params.get("agent_type", [])),
        "agent_id": set(params.get("agent_id", [])),
        "session_id": set(params.get("session_id", [])),
        "step_type": set(params.get("step_type", [])),
    }
    result: list[dict[str, Any]] = []
    for row in records:
        keep = True
        for key, values in filters.items():
            if values and str(row.get(key, "<unknown>")) not in values:
                keep = False
                break
        if keep:
            result.append(row)
    return result


def serve_dataset(dataset: dict[str, Any], port: int) -> None:
    AppServer.dataset = dataset
    server = ThreadingHTTPServer(("127.0.0.1", port), AppServer)
    print(f"Serving ai-session-analizer on http://127.0.0.1:{port}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping ai-session-analizer.")
    finally:
        server.server_close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Analyze Claude Code and Codex local session logs.")
    parser.add_argument("--claude-root", default=str(DEFAULT_CLAUDE_ROOT), help="Path to ~/.claude/projects")
    parser.add_argument("--codex-root", default=str(DEFAULT_CODEX_ROOT), help="Path to ~/.codex/sessions")

    subparsers = parser.add_subparsers(dest="command", required=True)

    build = subparsers.add_parser("build", help="Build normalized dataset JSON")
    build.add_argument("--output", default=str(ROOT / "output" / "dataset.json"), help="Output JSON path")

    serve = subparsers.add_parser("serve", help="Build dataset and serve web UI")
    serve.add_argument("--port", type=int, default=8765, help="HTTP port")
    serve.add_argument(
        "--output",
        default=str(ROOT / "output" / "dataset.json"),
        help="Optional cache path for the generated dataset",
    )

    return parser.parse_args()


def main() -> int:
    args = parse_args()
    dataset = build_dataset(Path(args.claude_root), Path(args.codex_root))

    if args.command == "build":
        write_dataset(dataset, Path(args.output))
        print(f"Wrote dataset to {args.output}")
        print(f"Records: {dataset['summary']['totals']['records']}")
        print(f"Total tokens: {dataset['summary']['totals']['total_tokens']}")
        return 0

    if args.command == "serve":
        write_dataset(dataset, Path(args.output))
        serve_dataset(dataset, args.port)
        return 0

    return 1


if __name__ == "__main__":
    raise SystemExit(main())

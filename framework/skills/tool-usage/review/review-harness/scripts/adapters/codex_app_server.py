#!/usr/bin/env python3
"""Bounded stdio client for the Codex app-server thread/fork operation."""

from __future__ import annotations

import json
import re
import selectors
import subprocess
import time
from pathlib import Path
from typing import Any


MIN_CODEX_VERSION = (0, 146, 0)
MIN_CODEX_VERSION_TEXT = "0.146.0"


class CodexAppServerError(RuntimeError):
    pass


def probe_codex_version(timeout_sec: int) -> str:
    try:
        completed = subprocess.run(
            ["codex", "--version"], capture_output=True, text=True,
            timeout=max(1, timeout_sec), check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise CodexAppServerError(f"Codex version probe failed: {exc}") from exc
    match = re.search(r"(\d+)\.(\d+)\.(\d+)", completed.stdout)
    if completed.returncode != 0 or match is None:
        diagnostic = completed.stderr.strip() or completed.stdout.strip() or "no version"
        raise CodexAppServerError(f"Codex version probe failed: {diagnostic}")
    version = tuple(int(part) for part in match.groups())
    if version < MIN_CODEX_VERSION:
        raise CodexAppServerError(
            f"Codex {match.group(0)} is unsupported; thread/fork requires >= {MIN_CODEX_VERSION_TEXT}"
        )
    return match.group(0)


class AppServerClient:
    def __init__(self, timeout_sec: int) -> None:
        self.timeout_sec = max(1, timeout_sec)
        self.proc: subprocess.Popen[str] | None = None
        self._next_id = 1

    def __enter__(self) -> "AppServerClient":
        try:
            self.proc = subprocess.Popen(
                ["codex", "app-server", "--listen", "stdio://", "--strict-config"],
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                text=True, bufsize=1,
            )
        except OSError as exc:
            raise CodexAppServerError(f"Cannot start Codex app-server: {exc}") from exc
        return self

    def __exit__(self, *_: object) -> None:
        if self.proc is None:
            return
        if self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=2)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(timeout=2)

    def _write(self, payload: dict[str, Any]) -> None:
        assert self.proc is not None and self.proc.stdin is not None
        self.proc.stdin.write(json.dumps(payload, separators=(",", ":")) + "\n")
        self.proc.stdin.flush()

    def notify(self, method: str, params: dict[str, Any] | None = None) -> None:
        payload: dict[str, Any] = {"jsonrpc": "2.0", "method": method}
        if params is not None:
            payload["params"] = params
        self._write(payload)

    def request(self, method: str, params: dict[str, Any]) -> dict[str, Any]:
        request_id = self._next_id
        self._next_id += 1
        self._write({"jsonrpc": "2.0", "id": request_id, "method": method, "params": params})
        assert self.proc is not None and self.proc.stdout is not None
        selector = selectors.DefaultSelector()
        selector.register(self.proc.stdout, selectors.EVENT_READ)
        deadline = time.monotonic() + self.timeout_sec
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise CodexAppServerError(
                        f"Codex app-server timed out waiting for {method} after {self.timeout_sec}s"
                    )
                if not selector.select(remaining):
                    continue
                line = self.proc.stdout.readline()
                if line == "":
                    stderr = ""
                    if self.proc.stderr is not None:
                        stderr = self.proc.stderr.read().strip()
                    raise CodexAppServerError(
                        f"Codex app-server exited during {method}: {stderr or self.proc.poll()}"
                    )
                try:
                    message = json.loads(line)
                except json.JSONDecodeError as exc:
                    raise CodexAppServerError(f"Codex app-server emitted invalid JSON: {line[:200]!r}") from exc
                if not isinstance(message, dict) or message.get("id") != request_id:
                    continue
                # Codex app-server 0.146.0 accepts JSON-RPC 2.0 requests but omits
                # the optional-looking marker from its JSONL responses.
                if message.get("jsonrpc") not in (None, "2.0"):
                    raise CodexAppServerError(f"Invalid JSON-RPC response for {method}")
                if "error" in message:
                    raise CodexAppServerError(f"Codex app-server {method} failed: {message['error']}")
                result = message.get("result")
                if not isinstance(result, dict):
                    raise CodexAppServerError(f"Codex app-server {method} returned no object result")
                return result
        finally:
            selector.close()


def fork_thread(
    parent_thread_id: str,
    *,
    workspace: Path,
    checkpoint_id: str | None,
    timeout_sec: int,
) -> dict[str, str]:
    deadline = time.monotonic() + max(1, timeout_sec)

    def remaining() -> int:
        value = int(deadline - time.monotonic())
        if value < 1:
            raise CodexAppServerError(f"Codex thread/fork exceeded bounded timeout {timeout_sec}s")
        return value

    version = probe_codex_version(min(remaining(), 10))
    with AppServerClient(remaining()) as client:
        initialized = client.request("initialize", {
            "clientInfo": {"name": "gbig-review-harness", "title": "GBIG Review Harness", "version": "1"},
            # excludeTurns/lastTurnId are negotiated experimental fields in the
            # pinned 0.146.0 schema. They do not start a turn.
            "capabilities": {"experimentalApi": True},
        })
        if not isinstance(initialized.get("userAgent"), str):
            raise CodexAppServerError("Codex initialize response lacks the pinned userAgent field")
        client.notify("initialized")
        client.timeout_sec = remaining()
        params: dict[str, Any] = {
            "threadId": parent_thread_id,
            "cwd": str(workspace.resolve()),
            "sandbox": "read-only",
            "approvalPolicy": "never",
            "ephemeral": False,
            "excludeTurns": True,
        }
        if checkpoint_id:
            params["lastTurnId"] = checkpoint_id
        result = client.request("thread/fork", params)
    thread = result.get("thread")
    child_id = thread.get("id") if isinstance(thread, dict) else None
    if not isinstance(child_id, str) or not child_id:
        raise CodexAppServerError("Codex thread/fork response lacks thread.id")
    if child_id == parent_thread_id:
        raise CodexAppServerError("Codex thread/fork reused the parent thread id")
    return {"thread_id": child_id, "codex_version": version, "user_agent": initialized["userAgent"]}


def read_thread_checkpoint(thread_id: str, *, timeout_sec: int = 15) -> str:
    """Return the exact completed provider head turn, never a logical checkpoint label."""
    deadline = time.monotonic() + max(1, timeout_sec)

    def remaining() -> int:
        value = int(deadline - time.monotonic())
        if value < 1:
            raise CodexAppServerError(f"Codex thread/read exceeded bounded timeout {timeout_sec}s")
        return value

    version = probe_codex_version(min(remaining(), 10))
    with AppServerClient(remaining()) as client:
        initialized = client.request("initialize", {
            "clientInfo": {"name": "gbig-review-harness", "title": "GBIG Review Harness", "version": "1"},
            "capabilities": {"experimentalApi": True},
        })
        if version not in str(initialized.get("userAgent") or ""):
            raise CodexAppServerError("Codex initialize response does not match the probed CLI version")
        client.notify("initialized")
        client.timeout_sec = remaining()
        result = client.request("thread/read", {"threadId": thread_id, "includeTurns": True})
    thread = result.get("thread")
    turns = thread.get("turns") if isinstance(thread, dict) else None
    if not isinstance(turns, list) or not turns:
        raise CodexAppServerError("Codex thread/read returned no provider turns")
    head = turns[-1]
    checkpoint_id = head.get("id") if isinstance(head, dict) else None
    if not isinstance(checkpoint_id, str) or not checkpoint_id:
        raise CodexAppServerError("Codex provider head has no turn id")
    if head.get("status") != "completed":
        raise CodexAppServerError(f"Codex provider head is not sealed: {head.get('status')!r}")
    return checkpoint_id


def inspect_thread_operation(thread_id: str, operation_id: str, *, timeout_sec: int = 15) -> dict[str, Any]:
    """Reconcile one deterministic operation marker from provider turn history."""
    marker = f"[[GBIG_REVIEW_OPERATION:{operation_id}]]"
    probe_codex_version(min(max(1, timeout_sec), 10))
    with AppServerClient(max(1, timeout_sec)) as client:
        init = client.request("initialize", {
            "clientInfo": {"name": "gbig-review-harness", "title": "GBIG Review Harness", "version": "1"},
            "capabilities": {"experimentalApi": True},
        })
        if not init.get("userAgent"):
            raise CodexAppServerError("Codex initialize response lacks userAgent")
        client.notify("initialized")
        result = client.request("thread/read", {"threadId": thread_id, "includeTurns": True})
    thread = result.get("thread")
    turns = thread.get("turns") if isinstance(thread, dict) else None
    if not isinstance(turns, list):
        raise CodexAppServerError("Codex thread history is uninspectable")
    matches: list[dict[str, Any]] = []
    for turn in turns:
        if not isinstance(turn, dict) or marker not in json.dumps(turn, ensure_ascii=False):
            continue
        responses = [
            item.get("text") for item in turn.get("items", [])
            if isinstance(item, dict) and item.get("type") == "agentMessage" and isinstance(item.get("text"), str)
        ]
        matches.append({"provider_turn_id": turn.get("id"), "status": turn.get("status"), "response": "\n".join(responses).strip()})
    if not matches:
        return {"state": "absent", "marker": marker}
    if len(matches) != 1 or not matches[0].get("provider_turn_id"):
        return {"state": "ambiguous", "marker": marker, "matches": len(matches)}
    match = matches[0]
    if match["status"] != "completed":
        return {"state": "pending", "marker": marker, **match}
    return {"state": "completed", "marker": marker, **match}


def list_child_threads(parent_thread_id: str, *, timeout_sec: int = 15) -> list[dict[str, Any]]:
    """Inventory direct provider children; app-server exposes no operation id on forks."""
    probe_codex_version(min(max(1, timeout_sec), 10))
    with AppServerClient(max(1, timeout_sec)) as client:
        client.request("initialize", {
            "clientInfo": {"name": "gbig-review-harness", "title": "GBIG Review Harness", "version": "1"},
            "capabilities": {"experimentalApi": True},
        })
        client.notify("initialized")
        children: list[dict[str, Any]] = []
        cursor: str | None = None
        for _ in range(100):
            params: dict[str, Any] = {"parentThreadId": parent_thread_id, "limit": 100}
            if cursor:
                params["cursor"] = cursor
            result = client.request("thread/list", params)
            data = result.get("data")
            if not isinstance(data, list):
                raise CodexAppServerError("Codex child thread inventory is uninspectable")
            children.extend(item for item in data if isinstance(item, dict))
            cursor = result.get("nextCursor")
            if cursor is None:
                return children
            if not isinstance(cursor, str) or not cursor:
                raise CodexAppServerError("Codex child thread inventory cursor is invalid")
        raise CodexAppServerError("Codex child thread inventory exceeded pagination bound")

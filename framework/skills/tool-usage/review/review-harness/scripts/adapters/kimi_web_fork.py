"""Authenticated client for Kimi Code's local, provider-native session fork route.

This module never starts or stops ``kimi web``.  The web server is a shared local
service; adapter shard cleanup owns only its review sandbox.
"""
from __future__ import annotations

import json
import os
import socket
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


DEFAULT_ORIGIN = "http://127.0.0.1:58627"


class KimiWebForkError(RuntimeError):
    def __init__(self, message: str, *, kind: str = "error", retryable: bool = False):
        super().__init__(message)
        self.kind = kind
        self.retryable = retryable


def _walk(value: object):
    if isinstance(value, dict):
        yield value
        for nested in value.values():
            yield from _walk(nested)
    elif isinstance(value, list):
        for nested in value:
            yield from _walk(nested)


def _message_text(item: dict) -> str:
    content = item.get("content") or item.get("text") or ""
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        return "\n".join(
            str(part.get("text") or "") if isinstance(part, dict) else str(part)
            for part in content
        )
    return ""


def reconcile_turn(session_id: str, operation_id: str, *, timeout_sec: int = 10) -> dict:
    """Inspect provider history for one durable operation marker."""
    _, payload = _request(
        "GET", f"/api/v1/sessions/{urllib.parse.quote(session_id, safe='')}",
        timeout_sec=timeout_sec,
    )
    messages = [item for item in _walk(payload) if item.get("role") in {"user", "assistant"}]
    if not isinstance(payload, (dict, list)):
        return {"state": "uninspectable", "evidence_complete": False}
    if not messages:
        return {"state": "absent", "evidence_complete": True}
    marker = f"[review-operation-id:{operation_id}]"
    matches = [index for index, item in enumerate(messages) if marker in _message_text(item)]
    if not matches:
        return {"state": "absent", "evidence_complete": True}
    if len(matches) != 1 or messages[matches[0]].get("role") != "user":
        return {"state": "ambiguous", "evidence_complete": False}
    following = messages[matches[0] + 1:]
    assistants = [item for item in following if item.get("role") == "assistant"]
    if not assistants:
        return {"state": "ambiguous", "evidence_complete": False}
    response = _message_text(assistants[0]).strip()
    if not response:
        return {"state": "ambiguous", "evidence_complete": False}
    return {"state": "completed", "evidence_complete": True, "text": response}


def _home() -> Path:
    return Path(os.environ.get("KIMI_CODE_HOME") or (Path.home() / ".kimi-code"))


def _origin() -> str:
    origin = os.environ.get("KIMI_WEB_ORIGIN", DEFAULT_ORIGIN).rstrip("/")
    parsed = urllib.parse.urlparse(origin)
    if parsed.scheme != "http" or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}:
        raise KimiWebForkError("KIMI web origin must be a loopback HTTP endpoint")
    return origin


def _token() -> str:
    path = _home() / "server.token"
    try:
        if os.name != "nt" and path.stat().st_mode & 0o077:
            raise KimiWebForkError("Kimi web bearer token permissions must be 0600")
        token = path.read_text(encoding="utf-8").strip()
    except OSError as exc:
        raise KimiWebForkError("Kimi web bearer token is unavailable; start `kimi web` first") from exc
    if not token:
        raise KimiWebForkError("Kimi web bearer token is empty")
    return token


def _request(method: str, path: str, *, payload: dict | None = None,
             timeout_sec: int = 10) -> tuple[int, object]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        _origin() + path,
        data=body,
        method=method,
        headers={
            "Authorization": f"Bearer {_token()}",
            "Accept": "application/json",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_sec) as response:
            raw = response.read()
            try:
                payload_out = json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                # The caller reconciles an ambiguous successful fork against
                # the provider's child-session inventory.
                payload_out = {}
            return response.status, payload_out
    except urllib.error.HTTPError as exc:
        raw = exc.read()
        try:
            detail = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            detail = {"message": raw.decode("utf-8", errors="replace")[:500]}
        message = detail.get("message") if isinstance(detail, dict) else None
        error_text = json.dumps(detail, ensure_ascii=False).lower()
        if exc.code in {409, 423, 425} or (
            exc.code == 400 and any(word in error_text for word in ("active", "busy", "running"))
        ):
            raise KimiWebForkError(
                "source_busy: Kimi source session is active; retry after it becomes idle",
                kind="source_busy", retryable=True,
            ) from exc
        raise KimiWebForkError(f"Kimi web REST returned HTTP {exc.code}: {message or 'request refused'}") from exc
    except (urllib.error.URLError, TimeoutError, socket.timeout) as exc:
        raise KimiWebForkError("Kimi web REST is unavailable or timed out") from exc


def _session_objects(payload: object) -> list[dict]:
    if isinstance(payload, list):
        return [item for item in payload if isinstance(item, dict)]
    if not isinstance(payload, dict):
        return []
    for key in ("data", "sessions", "items"):
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
        if isinstance(value, dict):
            nested = _session_objects(value)
            if nested:
                return nested
    return []


def _children(parent_session_id: str, *, timeout_sec: int,
              operation_id: str | None = None) -> set[str]:
    _, payload = _request("GET", "/api/v1/sessions", timeout_sec=timeout_sec)
    children: set[str] = set()
    for item in _session_objects(payload):
        parent = item.get("parent_session_id") or item.get("parentSessionId") or item.get("parent_id")
        child = item.get("id") or item.get("session_id")
        item_operation = item.get("operation_id") or item.get("operationId")
        operation_matches = operation_id is None or item_operation == operation_id
        if str(parent or "") == parent_session_id and child and operation_matches:
            children.add(str(child))
    return children


def archive_session(session_id: str, *, timeout_sec: int = 10) -> None:
    """Best-effort provider cleanup for a fork whose local child cannot commit."""
    _request(
        "POST", f"/api/v1/sessions/{urllib.parse.quote(session_id, safe='')}:archive",
        payload={}, timeout_sec=timeout_sec,
    )


def probe() -> dict:
    """Prove authenticated reachability without creating a session."""
    try:
        _request("GET", "/api/v1/meta", timeout_sec=3)
    except KimiWebForkError as exc:
        return {"ok": False, "reason": str(exc)}
    return {"ok": True, "origin": _origin()}


def fork_session(parent_session_id: str, *, operation_id: str,
                 timeout_sec: int = 30) -> str:
    """Fork exactly at the provider checkpoint; never synthesize a fresh session."""
    before = _children(
        parent_session_id, timeout_sec=min(timeout_sec, 10), operation_id=operation_id,
    )
    if len(before) == 1:
        # Durable operation already committed during an earlier crash window.
        return next(iter(before))
    if len(before) > 1:
        raise KimiWebForkError("Kimi fork operation reconciliation is ambiguous")
    try:
        _, payload = _request(
            "POST",
            f"/api/v1/sessions/{urllib.parse.quote(parent_session_id, safe='')}:fork",
            payload={"operation_id": operation_id}, timeout_sec=timeout_sec,
        )
    except KimiWebForkError as exc:
        # A timeout/transport break or idempotency conflict can occur after the
        # server committed this durable operation.
        after = _children(
            parent_session_id, timeout_sec=min(timeout_sec, 10), operation_id=operation_id,
        )
        created = after - before
        if len(created) == 1:
            return created.pop()
        raise

    child = payload.get("data", {}).get("id") if isinstance(payload, dict) else None
    if not child:
        after = _children(
            parent_session_id, timeout_sec=min(timeout_sec, 10), operation_id=operation_id,
        )
        created = after - before
        if len(created) == 1:
            child = created.pop()
    child = str(child or "")
    if not child:
        raise KimiWebForkError("Kimi fork response is ambiguous and child reconciliation found no unique session")
    if child == parent_session_id:
        raise KimiWebForkError("Kimi native fork reused the parent session id")
    return child

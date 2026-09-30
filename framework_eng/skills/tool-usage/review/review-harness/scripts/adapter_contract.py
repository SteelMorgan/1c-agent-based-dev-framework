#!/usr/bin/env python3
"""Общий контракт адаптера review-harness (RVSW-01, FR-01; переезд из консилиума, TD §3.2).

Единственное место, знающее о subprocess: запуск адаптеров (start/ask/status/close/sync),
парсинг stdout (review_id/session_id/workspace), маппинг таймаута и ошибок,
retry-политика, параллельные волны.

Маппинг (наследуется):
  exit 0                        → ok, ход принимается;
  exit 1 + runtime.json phase=timeout → "timeout" → unresponsive БЕЗ retry;
  иной exit 1                   → "error" → ровно один retry → unresponsive.

Новые обязательные элементы контракта (FR-01):
- sync_participant() — обёртка обязательного subcommand sync (доставка
  обновлённых исходников/diff в sandbox участника; re-review, FR-09);
- materialize_diff() — материализация diff файлом внутри sandbox участника
  (FR-01 п.к, FR-04): участник читает diff как обычный файл, а не из промпта;
- read_participant_activity() — канонический доступ к полям активности
  last_activity_at/last_heartbeat_at (FR-13; новый таймстамп НЕ вводится);
- PARALLEL_CAP — потолок параллелизма волн (TD §6.4).
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import time
from collections import Counter, deque
from concurrent.futures import FIRST_COMPLETED, Future, ThreadPoolExecutor, wait
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Hashable, Sequence, TypeVar

from invocation_lock import INVOCATION_LOCK_NAME, validate_review_id

REVIEW_ROOT = Path(".review-sandboxes")
DEFAULT_TIMEOUT_SEC = 900           # per-invocation timeout (наследуется)
WAVE_GRACE_SEC = 120                # grace: subprocess timeout = T + 120
WAVE_TIMEOUT_SEC = DEFAULT_TIMEOUT_SEC + 2 * WAVE_GRACE_SEC  # волна = T + 240 = 1140
PARALLEL_CAP = 8                    # TD §6.4: потолок одновременных вызовов в волне

# Канонические поля активности адаптера (FR-13): last_heartbeat_at — «процесс-
# адаптер жив и следит за CLI», last_activity_at — «CLI эмитит события».
# Оба обязательны у всех адаптеров; новый таймстамп не вводится.
ACTIVITY_FIELDS = ("last_activity_at", "last_heartbeat_at")

# Имя файла материализованного diff в sandbox участника (FR-01 п.к).
DIFF_FILENAME = "review.diff"
NATIVE_FORK_CAPABILITY_FIELDS = frozenset({
    "supported", "route", "min_cli_version", "exact_checkpoint", "automation_safe",
})
_monotonic = time.monotonic


@dataclass
class InvocationResult:
    ok: bool
    kind: str                       # "ok" | "timeout" | "error"
    text: str | None = None
    review_id: str | None = None
    session_id: str | None = None
    attempts: int = 1
    error: str | None = None
    stdout: str = ""
    stderr: str = ""
    parent_review_id: str | None = None
    parent_session_id: str | None = None
    provider_fork_ref: str | None = None
    operation_id: str | None = None
    snapshot_digest: str | None = None
    provider_checkpoint_id: str | None = None
    provider_turn_id: str | None = None
    prompt_consumed: bool | None = None


ShardResult = TypeVar("ShardResult")
_IDENTITY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
_OPERATION_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]*$")


def parse_start_stdout(stdout: str) -> dict:
    """Контракт вывода start: строки review_id:/session_id:/workspace:."""
    parsed: dict = {}
    for line in stdout.splitlines():
        for key in ("review_id", "session_id", "workspace"):
            prefix = f"{key}: "
            if line.startswith(prefix) and key not in parsed:
                parsed[key] = line[len(prefix):].strip()
    return parsed


def _parse_fork_stdout(stdout: str) -> dict:
    """Служебные идентификаторы, доказывающие native parent -> child lineage."""
    parsed: dict = {}
    keys = (
        "review_id", "session_id", "parent_review_id", "parent_session_id",
        "provider_fork_ref", "workspace", "prompt_consumed", "operation_id",
        "snapshot_digest", "provider_checkpoint_id", "provider_turn_id",
    )
    for line in stdout.splitlines():
        for key in keys:
            prefix = f"{key}: "
            if line.startswith(prefix) and key not in parsed:
                parsed[key] = line[len(prefix):].strip()
    return parsed


def _safe_identity(value: str) -> bool:
    """Identity допустима и как ключ, и как один безопасный компонент пути."""
    return bool(_IDENTITY_RE.fullmatch(value)) and value not in {".", ".."}


def is_lock_only_tombstone(review_dir: Path) -> bool:
    """Canonical closed/failed-start state: directory + one stable lock inode."""
    if review_dir.is_symlink() or not review_dir.is_dir():
        return False
    try:
        entries = list(review_dir.iterdir())
    except OSError:
        return False
    stable_lock = review_dir / INVOCATION_LOCK_NAME
    return (
        entries == [stable_lock]
        and not stable_lock.is_symlink()
        and stable_lock.is_file()
    )


# Backward-compatible private alias for in-flight contract tests; consumers use
# the public helper so cleanup classification has one implementation.
_is_lock_only_tombstone = is_lock_only_tombstone


def _runtime_phase(cwd: Path, review_id: str) -> str | None:
    runtime_path = Path(cwd) / REVIEW_ROOT / review_id / "runtime.json"
    if not runtime_path.exists():
        return None
    try:
        return json.loads(runtime_path.read_text(encoding="utf-8")).get("phase")
    except (OSError, json.JSONDecodeError):
        return None


def classify_failure(cwd: Path, review_id: str | None, stderr: str) -> str:
    """"timeout" | "error" по runtime.json сессии участника;
    fallback — маркер таймаута в stderr адаптера."""
    if review_id and _runtime_phase(cwd, review_id) == "timeout":
        return "timeout"
    if "exceeded timeout" in (stderr or ""):
        return "timeout"
    return "error"


def _read_meta(cwd: Path, review_id: str) -> dict:
    meta_path = Path(cwd) / REVIEW_ROOT / review_id / "review.json"
    if not meta_path.exists():
        return {}
    try:
        return json.loads(meta_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}


def _run_adapter(cmd: list[str], cwd: Path, timeout_sec: int) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        cwd=str(cwd),
        capture_output=True,
        text=True,
        timeout=timeout_sec + WAVE_GRACE_SEC,
        check=False,
    )


def _invoke_once(cmd: list[str], cwd: Path, timeout_sec: int, review_id: str | None) -> InvocationResult:
    try:
        proc = _run_adapter(cmd, cwd, timeout_sec)
    except subprocess.TimeoutExpired:
        return InvocationResult(
            ok=False, kind="timeout", review_id=review_id,
            error=f"адаптер превысил внешний таймаут {timeout_sec + WAVE_GRACE_SEC}s",
        )
    if proc.returncode == 0:
        return InvocationResult(ok=True, kind="ok", review_id=review_id,
                                stdout=proc.stdout, stderr=proc.stderr)
    kind = classify_failure(cwd, review_id, proc.stderr)
    return InvocationResult(
        ok=False, kind=kind, review_id=review_id,
        error=(proc.stderr or proc.stdout or f"adapter exited with code {proc.returncode}").strip(),
        stdout=proc.stdout, stderr=proc.stderr,
    )


def _join_failures(primary: str | None, secondary: str | None,
                   label: str = "reconcile") -> str | None:
    """Обе причины отказа, а не последняя из них.

    Первая называет ПРИЧИНУ отказа операции, вторая — почему её состояние не
    удалось доказать. Подмена первой второй теряет диагноз: остаётся «не смогли
    посмотреть», исчезает «почему отказали».
    """
    if not primary:
        return secondary
    if not secondary or secondary == primary or secondary in primary:
        return primary
    return f"{primary}; {label}: {secondary}"


def _with_retry(cmd: list[str], cwd: Path, timeout_sec: int, review_id: str | None) -> InvocationResult:
    """Retry-политика: таймаут — без retry; ошибка — ровно один retry."""
    result = _invoke_once(cmd, cwd, timeout_sec, review_id)
    if result.ok or result.kind == "timeout":
        return result
    retry = _invoke_once(cmd, cwd, timeout_sec, review_id)
    retry.attempts = result.attempts + 1
    if retry.ok:
        retry.error = None
    return retry


def start_participant(
    adapter: str,
    question: str,
    paths: list[str],
    cwd: Path,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    model: str | None = None,
    review_id: str | None = None,
) -> InvocationResult:
    """start адаптера: focused-paths обязательны (--full-context не передаётся).

    Заданный caller-ом ``review_id`` должен быть безопасным одиночным
    компонентом пути; проверка идёт до любого subprocess.
    """
    if review_id is not None:
        validate_review_id(review_id)
    cmd = [sys.executable, str(adapter), "start", "--question", question,
           "--timeout-sec", str(timeout_sec)]
    if model:
        cmd += ["--model", model]
    if review_id is not None:
        cmd += ["--review-id", review_id]
    cmd += list(paths)
    result = _with_retry(cmd, cwd, timeout_sec, review_id=review_id)
    if not result.ok:
        return result
    parsed = parse_start_stdout(result.stdout)
    if review_id is not None and parsed.get("review_id") != review_id:
        return InvocationResult(
            ok=False, kind="error", review_id=review_id,
            attempts=result.attempts,
            error="start: adapter не подтвердил запрошенный review_id",
            stdout=result.stdout, stderr=result.stderr,
        )
    result.review_id = parsed.get("review_id")
    result.session_id = parsed.get("session_id")
    meta = _read_meta(cwd, result.review_id) if result.review_id else {}
    result.text = meta.get("last_response") or _extract_text_from_start_stdout(result.stdout)
    if not result.session_id:
        result.session_id = meta.get("session_id")
    result.provider_checkpoint_id = meta.get("provider_checkpoint_id")
    result.provider_turn_id = meta.get("provider_turn_id")
    result.prompt_consumed = True
    return result


def _extract_text_from_start_stdout(stdout: str) -> str:
    """Fallback: текст ответа после служебных строк start."""
    lines = stdout.splitlines()
    body: list[str] = []
    skip_prefixes = ("review_id: ", "session_id: ", "workspace: ", "cost_usd: ", "events: ")
    for line in lines:
        if line.startswith(skip_prefixes):
            continue
        body.append(line)
    return "\n".join(body).strip()


def ask_participant(
    adapter: str,
    review_id: str,
    question: str,
    cwd: Path,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    *,
    operation_id: str | None = None,
    parent_provider_turn_id: str | None = None,
) -> InvocationResult:
    """ask: продолжает сохранённую сессию адаптера участника (resume)."""
    if (operation_id is None) != (parent_provider_turn_id is None):
        return InvocationResult(
            ok=False, kind="error", review_id=review_id,
            error="ask: operation_id и parent_provider_turn_id передаются только вместе",
        )
    if operation_id is not None and (
        len(operation_id) > 128 or not _OPERATION_ID_RE.fullmatch(operation_id)
    ):
        return InvocationResult(
            ok=False, kind="error", review_id=review_id,
            error="ask: небезопасный operation_id",
        )
    if parent_provider_turn_id is not None and not parent_provider_turn_id.strip():
        return InvocationResult(
            ok=False, kind="error", review_id=review_id,
            operation_id=operation_id,
            error="ask: пустой parent_provider_turn_id",
        )
    cmd = [sys.executable, str(adapter), "ask", review_id,
           "--question", question, "--timeout-sec", str(timeout_sec)]
    if operation_id is not None:
        cmd += [
            "--operation-id", operation_id,
            "--parent-provider-turn-id", parent_provider_turn_id,
        ]
        result = _invoke_once(cmd, cwd, timeout_sec, review_id=review_id)
        if not result.ok:
            reconciled = reconcile_participant(
                adapter, review_id, operation_id, cwd,
                timeout_sec=min(timeout_sec, 120),
                parent_provider_turn_id=parent_provider_turn_id,
            )
            if reconciled.ok:
                return reconciled
            # Только доказанное отсутствие после обычной error разрешает один
            # повтор той же operation; timeout/ambiguous никогда не re-ask.
            if reconciled.kind != "absent" or result.kind == "timeout":
                result.error = _join_failures(result.error, reconciled.error)
                return result
            result = _invoke_once(cmd, cwd, timeout_sec, review_id=review_id)
            result.attempts = 2
            if not result.ok:
                reconciled = reconcile_participant(
                    adapter, review_id, operation_id, cwd,
                    timeout_sec=min(timeout_sec, 120),
                    parent_provider_turn_id=parent_provider_turn_id,
                )
                if reconciled.ok:
                    reconciled.attempts = 2
                    return reconciled
                result.error = _join_failures(result.error, reconciled.error)
                return result
    else:
        result = _with_retry(cmd, cwd, timeout_sec, review_id=review_id)
    if not result.ok:
        return result
    meta = _read_meta(cwd, review_id)
    result.text = meta.get("last_response")
    result.session_id = meta.get("session_id")
    result.provider_checkpoint_id = meta.get("provider_checkpoint_id")
    result.provider_turn_id = meta.get("provider_turn_id")
    result.prompt_consumed = True
    result.operation_id = operation_id
    if operation_id is not None:
        # Даже exit 0 не является proof: процесс мог завершить provider turn и
        # упасть между stdout/local reducer. Канонический результат exact-route
        # всегда строится отдельным non-mutating reconcile.
        reconciled = reconcile_participant(
            adapter, review_id, operation_id, cwd,
            timeout_sec=min(timeout_sec, 120),
            parent_provider_turn_id=parent_provider_turn_id,
        )
        if reconciled.ok:
            return reconciled
        result.ok = False
        result.kind = reconciled.kind
        result.error = reconciled.error or "ask: completion не доказан reconcile-route"
    if result.text is None:
        # fallback: ответ — stdout без первой статистической строки ask
        lines = result.stdout.splitlines()
        if lines and lines[0].startswith("[review "):
            lines = lines[1:]
        result.text = "\n".join(lines).strip()
    return result


def reconcile_participant(
    adapter: str,
    review_id: str,
    operation_id: str,
    cwd: Path,
    timeout_sec: int = 60,
    parent_provider_turn_id: str | None = None,
    allow_same_provider_turn: bool = False,
) -> InvocationResult:
    """Восстановить provider turn по operation marker без нового model call.

    ``absent`` считается доказанным только с ``evidence_complete=true``.
    ``ambiguous``/uninspectable никогда не преобразуется в повторный ask.
    """
    if len(operation_id) > 128 or not _OPERATION_ID_RE.fullmatch(operation_id):
        return InvocationResult(
            ok=False, kind="error", review_id=review_id,
            operation_id=operation_id, error="reconcile: небезопасный operation_id",
        )
    cmd = [
        sys.executable, str(adapter), "reconcile", review_id,
        "--operation-id", operation_id,
    ]
    if parent_provider_turn_id is not None:
        cmd += ["--parent-provider-turn-id", parent_provider_turn_id]
    cmd.append("--json")
    try:
        proc = subprocess.run(
            cmd, cwd=str(cwd), capture_output=True, text=True,
            timeout=timeout_sec, check=False,
        )
    except subprocess.TimeoutExpired:
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id,
            error="reconcile: provider state inspection timeout; blind retry запрещён",
        )
    if proc.returncode != 0:
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id, stdout=proc.stdout, stderr=proc.stderr,
            error=(proc.stderr or proc.stdout or "reconcile failed").strip(),
        )
    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError:
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id, stdout=proc.stdout, stderr=proc.stderr,
            error="reconcile: adapter вернул невалидный JSON; blind retry запрещён",
        )
    if not isinstance(payload, dict) or payload.get("operation_id") != operation_id:
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id,
            error="reconcile: operation identity не доказана",
        )
    state = payload.get("state")
    evidence_complete = payload.get("evidence_complete") is True
    if state == "absent" and evidence_complete:
        return InvocationResult(
            ok=False, kind="absent", review_id=review_id,
            operation_id=operation_id, stdout=proc.stdout, stderr=proc.stderr,
        )
    if state != "completed" or not evidence_complete:
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id,
            error=str(payload.get("error") or payload.get("reason") or
                      "reconcile: provider state ambiguous; blind retry запрещён"),
            stdout=proc.stdout, stderr=proc.stderr,
        )
    provider_turn_id = payload.get("provider_turn_id")
    session_id = payload.get("session_id")
    if not isinstance(provider_turn_id, str) or not provider_turn_id.strip():
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id,
            error="reconcile: completed без provider_turn_id",
        )
    if (
        not allow_same_provider_turn
        and parent_provider_turn_id is not None
        and provider_turn_id == parent_provider_turn_id
    ):
        return InvocationResult(
            ok=False, kind="ambiguous", review_id=review_id,
            operation_id=operation_id,
            error="reconcile: completed turn не продвинул provider lineage",
        )
    prompt_consumed = payload.get("prompt_consumed")
    if not isinstance(prompt_consumed, bool):
        prompt_consumed = not allow_same_provider_turn
    return InvocationResult(
        ok=True, kind="ok", review_id=review_id,
        session_id=session_id if isinstance(session_id, str) else None,
        operation_id=operation_id, provider_turn_id=provider_turn_id,
        provider_checkpoint_id=payload.get("provider_checkpoint_id"),
        prompt_consumed=prompt_consumed,
        text=str(payload.get("text") or payload.get("response") or ""),
        stdout=proc.stdout, stderr=proc.stderr,
    )


def _reconcile_failed_fork(
    adapter: str,
    parent_review_id: str,
    child_review_id: str,
    operation_id: str,
    cwd: Path,
) -> bool:
    """Закрыть ожидаемый child после неуспешного/недоказанного fork.

    Удаление по произвольному stdout identity запрещено: reconciliation знает
    только заранее проверенный ``child_review_id``. Если meta существует, её
    lineage должна совпасть с parent/operation; иначе чужой sandbox не трогаем.
    """
    child_dir = Path(cwd) / REVIEW_ROOT / child_review_id
    if not child_dir.exists():
        return True
    if is_lock_only_tombstone(child_dir):
        return True
    meta = _read_meta(cwd, child_review_id)
    if not meta:
        return False
    lineage = meta.get("lineage") if isinstance(meta.get("lineage"), dict) else meta
    recorded_parent = lineage.get("parent_review_id")
    recorded_operation = lineage.get("operation_id")
    if recorded_parent != parent_review_id or recorded_operation != operation_id:
        return False
    closed = close_participant(adapter, child_review_id, cwd)
    return closed and is_lock_only_tombstone(child_dir)


def _reconcile_existing_fork(
    cwd: Path,
    parent_review_id: str,
    parent_session_id: str,
    child_review_id: str,
    operation_id: str,
    snapshot_digest: str,
    provider_checkpoint_id: str,
) -> InvocationResult:
    """Восстановить результат повторной exactly-once операции без provider call."""
    meta = _read_meta(cwd, child_review_id)
    lineage = meta.get("lineage") if isinstance(meta.get("lineage"), dict) else meta
    expected = {
        "parent_review_id": parent_review_id,
        "parent_session_id": parent_session_id,
        "operation_id": operation_id,
        "snapshot_digest": snapshot_digest,
    }
    mismatched = [key for key, value in expected.items() if lineage.get(key) != value]
    recorded_checkpoint = (
        lineage.get("parent_provider_checkpoint_id")
        or lineage.get("provider_checkpoint_id")
        or meta.get("provider_checkpoint_id")
    )
    if recorded_checkpoint != provider_checkpoint_id:
        mismatched.append("provider_checkpoint_id")
    child_session_id = meta.get("session_id")
    provider_turn_id = meta.get("provider_turn_id")
    prompt_consumed = meta.get("prompt_consumed", lineage.get("prompt_consumed"))
    if mismatched or not isinstance(child_session_id, str) or child_session_id == parent_session_id:
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            parent_review_id=parent_review_id, parent_session_id=parent_session_id,
            operation_id=operation_id, snapshot_digest=snapshot_digest,
            provider_checkpoint_id=provider_checkpoint_id,
            error=f"fork reconciliation lineage mismatch: {sorted(set(mismatched))}",
        )
    if not isinstance(provider_turn_id, str) or not provider_turn_id.strip():
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork reconciliation: provider_turn_id отсутствует",
            parent_review_id=parent_review_id, parent_session_id=parent_session_id,
            operation_id=operation_id, snapshot_digest=snapshot_digest,
            provider_checkpoint_id=provider_checkpoint_id,
        )
    if not isinstance(prompt_consumed, bool):
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork reconciliation: prompt_consumed отсутствует",
            parent_review_id=parent_review_id, parent_session_id=parent_session_id,
            operation_id=operation_id, snapshot_digest=snapshot_digest,
            provider_checkpoint_id=provider_checkpoint_id,
        )
    return InvocationResult(
        ok=True, kind="ok", review_id=child_review_id, session_id=child_session_id,
        parent_review_id=parent_review_id, parent_session_id=parent_session_id,
        provider_fork_ref=meta.get("provider_fork_ref"), operation_id=operation_id,
        snapshot_digest=snapshot_digest, provider_checkpoint_id=provider_checkpoint_id,
        provider_turn_id=provider_turn_id, prompt_consumed=prompt_consumed,
        text=meta.get("last_response") or "",
    )


def fork_participant(
    adapter: str,
    parent_review_id: str,
    child_review_id: str,
    cwd: Path,
    timeout_sec: int = DEFAULT_TIMEOUT_SEC,
    *,
    operation_id: str,
    snapshot_digest: str,
    provider_checkpoint_id: str,
    question: str | None = None,
) -> InvocationResult:
    """Создать provider-native child branch от существующего parent review.

    Это отдельный adapter subcommand: функция принципиально не имеет fallback на
    ``start`` или реконструкцию контекста. Lineage принимается только когда
    адаптер вернул полный и непротиворечивый набор native identifiers.
    """
    lineage = {
        "parent_review_id": parent_review_id,
        "operation_id": operation_id,
        "snapshot_digest": snapshot_digest,
        "provider_checkpoint_id": provider_checkpoint_id,
    }
    if not _safe_identity(parent_review_id) or not _safe_identity(child_review_id):
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork: parent/child review identity небезопасна",
            **lineage,
        )
    if len(operation_id) > 128 or not _OPERATION_ID_RE.fullmatch(operation_id):
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork: operation_id должен быть безопасной неизменяемой identity",
            **lineage,
        )
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", snapshot_digest):
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork: snapshot_digest должен иметь вид sha256:<64 lowercase hex>",
            **lineage,
        )
    if parent_review_id == child_review_id:
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork: child review identity должна отличаться от parent",
            **lineage,
        )
    parent_meta = _read_meta(cwd, parent_review_id)
    parent_session_id = parent_meta.get("session_id")
    lineage["parent_session_id"] = parent_session_id
    if not isinstance(parent_session_id, str) or not parent_session_id.strip():
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error=f"fork: parent {parent_review_id!r} не имеет native session_id",
            **lineage,
        )
    sealed_provider_checkpoint = parent_meta.get("provider_checkpoint_id")
    if (
        not isinstance(sealed_provider_checkpoint, str)
        or not sealed_provider_checkpoint.strip()
        or provider_checkpoint_id != sealed_provider_checkpoint
    ):
        return InvocationResult(
            ok=False, kind="error", review_id=child_review_id,
            error="fork: provider_checkpoint_id не совпадает с sealed parent provider cursor",
            **lineage,
        )
    child_dir = Path(cwd) / REVIEW_ROOT / child_review_id
    child_existed_before = child_dir.exists()
    child_is_tombstone = is_lock_only_tombstone(child_dir)
    if child_existed_before and not child_is_tombstone:
        existing = _reconcile_existing_fork(
            cwd, parent_review_id, parent_session_id, child_review_id,
            operation_id, snapshot_digest, provider_checkpoint_id,
        )
        if existing.ok:
            return existing
        provider_reconciled = reconcile_participant(
            adapter, child_review_id, operation_id, cwd,
            timeout_sec=min(timeout_sec, 120),
            parent_provider_turn_id=provider_checkpoint_id,
            allow_same_provider_turn=True,
        )
        if provider_reconciled.ok:
            provider_reconciled.parent_review_id = parent_review_id
            provider_reconciled.parent_session_id = parent_session_id
            provider_reconciled.snapshot_digest = snapshot_digest
            provider_reconciled.provider_checkpoint_id = provider_checkpoint_id
            return provider_reconciled
        existing.error = provider_reconciled.error or existing.error
        return existing

    cmd = [
        sys.executable, str(adapter), "fork", parent_review_id,
        "--child-review-id", child_review_id,
    ]
    cmd += [
        "--operation-id", operation_id,
        "--provider-checkpoint-id", provider_checkpoint_id,
        "--snapshot-digest", snapshot_digest,
    ]
    if question is not None:
        cmd += ["--question", question]
    cmd += ["--timeout-sec", str(timeout_sec)]
    # Fork НЕ повторяется вслепую: отказ провайдера может оставить частично
    # материализованную песочницу ребёнка, и вторая попытка упрётся в непустой
    # каталог, подменив исходную причину вторичной ошибкой sandbox-а. Повтор
    # легален только после ДОКАЗАННОЙ уборки осиротевшего ребёнка.
    result = _invoke_once(cmd, cwd, timeout_sec, review_id=child_review_id)
    cleanup_reported = False
    if not result.ok and result.kind != "timeout":
        first_error = result.error or "fork failed"
        may_reclaim = not child_existed_before or child_is_tombstone
        if may_reclaim and _reconcile_failed_fork(
                adapter, parent_review_id, child_review_id, operation_id, cwd):
            retry = _invoke_once(cmd, cwd, timeout_sec, review_id=child_review_id)
            retry.attempts = result.attempts + 1
            retry.error = None if retry.ok else _join_failures(
                first_error, retry.error, label="retry after cleanup")
            result = retry
        elif may_reclaim:
            result.error = f"{first_error}; orphan cleanup failed"
            cleanup_reported = True
    result.parent_review_id = parent_review_id
    result.parent_session_id = parent_session_id
    result.operation_id = operation_id
    result.snapshot_digest = snapshot_digest
    result.provider_checkpoint_id = provider_checkpoint_id
    if not result.ok:
        if not cleanup_reported and (not child_existed_before or child_is_tombstone):
            cleanup_ok = _reconcile_failed_fork(
                adapter, parent_review_id, child_review_id, operation_id, cwd,
            )
            if not cleanup_ok:
                result.error = f"{result.error or 'fork failed'}; orphan cleanup failed"
        return result

    parsed = _parse_fork_stdout(result.stdout)
    required = (
        "review_id", "session_id", "parent_review_id", "parent_session_id",
        "workspace", "prompt_consumed", "operation_id", "snapshot_digest",
        "provider_checkpoint_id", "provider_turn_id",
    )
    missing = [key for key in required if not parsed.get(key)]
    invalid_reason: str | None = None
    if missing:
        invalid_reason = f"fork: отсутствуют обязательные lineage markers: {missing}"
    elif parsed["review_id"] != child_review_id:
        invalid_reason = "fork: adapter вернул другой child review_id"
    elif parsed["parent_review_id"] != parent_review_id:
        invalid_reason = "fork: adapter вернул другой parent review_id"
    elif parsed["parent_session_id"] != parent_session_id:
        invalid_reason = "fork: adapter не доказал исходную parent session identity"
    elif parsed["operation_id"] != operation_id:
        invalid_reason = "fork: adapter вернул другой operation_id"
    elif parsed["snapshot_digest"] != snapshot_digest:
        invalid_reason = "fork: adapter вернул другой snapshot_digest"
    elif parsed["provider_checkpoint_id"] != provider_checkpoint_id:
        invalid_reason = "fork: adapter вернул другой provider_checkpoint_id"
    elif parsed["session_id"] == parent_session_id:
        invalid_reason = "fork: child session identity совпадает с parent"
    elif not _safe_identity(parsed["review_id"]):
        invalid_reason = "fork: adapter вернул небезопасную child review identity"
    elif parsed["prompt_consumed"] not in {"true", "false"}:
        invalid_reason = "fork: prompt_consumed должен быть literal true|false"
    elif parsed["prompt_consumed"] == "true" and question is None:
        invalid_reason = "fork: adapter заявил prompt_consumed без переданного question"

    if invalid_reason is not None:
        cleanup_ok = (child_existed_before and not child_is_tombstone) or _reconcile_failed_fork(
            adapter, parent_review_id, child_review_id, operation_id, cwd,
        )
        result.ok = False
        result.kind = "error"
        result.error = invalid_reason if cleanup_ok else f"{invalid_reason}; orphan cleanup failed"
        result.session_id = None
        return result

    result.review_id = parsed["review_id"]
    result.session_id = parsed["session_id"]
    result.provider_fork_ref = parsed.get("provider_fork_ref")
    result.provider_turn_id = parsed["provider_turn_id"]
    result.prompt_consumed = parsed["prompt_consumed"] == "true"
    child_meta = _read_meta(cwd, child_review_id)
    result.text = child_meta.get("last_response") or _extract_text_from_start_stdout(result.stdout)
    return result


def probe_fork_capability(
    adapter: str,
    cwd: Path,
    timeout_sec: int = 60,
    participant_id: str | None = None,
) -> dict:
    """Получить и строго проверить runtime-доказательство native fork.

    Registry сообщает целевую capability, но не заменяет runtime probe.
    Неизвестные/отсутствующие поля отклоняются, чтобы новая семантика не была
    молча истолкована старым harness как безопасная.

    `participant_id` — только диагностика: в смешанном составе отказ обязан
    называть участника, чья возможность не подтвердилась. На семантику проверки
    он не влияет.
    """
    where = f"участник {participant_id}: " if participant_id else ""

    def _reject(detail: str) -> RuntimeError:
        return RuntimeError(where + detail)

    try:
        proc = subprocess.run(
            [sys.executable, str(adapter), "capabilities", "--json"],
            cwd=str(cwd), capture_output=True, text=True,
            timeout=timeout_sec, check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise _reject("native fork capability probe превысил timeout") from exc
    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout or "adapter capability probe failed").strip()
        raise _reject(f"native fork capability probe failed: {detail}")
    try:
        payload = json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise _reject("native fork capability probe вернул невалидный JSON") from exc
    capability = payload.get("native_fork") if isinstance(payload, dict) and "native_fork" in payload else payload
    if not isinstance(capability, dict):
        raise _reject("native fork capability должна быть JSON object")
    actual_fields = set(capability)
    if actual_fields != NATIVE_FORK_CAPABILITY_FIELDS:
        missing = sorted(NATIVE_FORK_CAPABILITY_FIELDS - actual_fields)
        unknown = sorted(actual_fields - NATIVE_FORK_CAPABILITY_FIELDS)
        raise _reject(
            f"native fork capability schema mismatch: missing={missing}, unknown={unknown}"
        )
    if not isinstance(capability["supported"], bool):
        raise _reject("native fork capability supported должна быть bool")
    if not isinstance(capability["route"], str) or not capability["route"].strip():
        raise _reject("native fork capability route должна быть непустой строкой")
    if not isinstance(capability["min_cli_version"], str) or not capability["min_cli_version"].strip():
        raise _reject("native fork capability min_cli_version должна быть непустой строкой")
    for field_name in ("exact_checkpoint", "automation_safe"):
        if not isinstance(capability[field_name], bool):
            raise _reject(f"native fork capability {field_name} должна быть bool")
    return dict(capability)


def read_participant_status(adapter: str, review_id: str, cwd: Path) -> dict:
    """status адаптера → dict {review_id, status, runtime, stats}."""
    proc = subprocess.run(
        [sys.executable, str(adapter), "status", review_id],
        cwd=str(cwd), capture_output=True, text=True, timeout=60, check=False,
    )
    if proc.returncode != 0:
        raise RuntimeError(f"status {review_id} failed: {proc.stderr.strip()}")
    return json.loads(proc.stdout)


def close_participant(adapter: str, review_id: str, cwd: Path, keep_sandbox: bool = False) -> bool:
    """close адаптера: удаляет sandbox участника (парность start↔close)."""
    review_dir = Path(cwd) / REVIEW_ROOT / review_id
    if not keep_sandbox and _safe_identity(review_id) and is_lock_only_tombstone(review_dir):
        # Idempotent close: stable lock inode is the canonical closed state and
        # must not be replaced by launching an adapter over missing metadata.
        return True
    cmd = [sys.executable, str(adapter), "close", review_id]
    if keep_sandbox:
        cmd.append("--keep-sandbox")
    proc = subprocess.run(cmd, cwd=str(cwd), capture_output=True, text=True, timeout=120, check=False)
    if proc.returncode != 0:
        return False
    return True if keep_sandbox else is_lock_only_tombstone(review_dir)


def sync_participant(adapter: str, review_id: str, cwd: Path) -> bool:
    """sync адаптера — ОБЯЗАТЕЛЬНЫЙ элемент контракта (FR-01 п.б): доставка
    обновлённых исходников/diff в sandbox участника (re-review, FR-09)."""
    proc = subprocess.run(
        [sys.executable, str(adapter), "sync", review_id],
        cwd=str(cwd), capture_output=True, text=True, timeout=120, check=False,
    )
    return proc.returncode == 0


def materialize_diff(cwd: Path, review_id: str, diff_content: str,
                     filename: str = DIFF_FILENAME) -> Path:
    """Материализация diff файлом внутри sandbox участника (FR-01 п.к, FR-04).

    Участник читает diff как обычный файл в своём workspace, а не из промпта.
    Sandbox остаётся read-only для участника; запись выполняет сторона
    вызывающего (канал доставки артефакта контракта). Fail-closed: без meta
    с workspace_path — RuntimeError.
    """
    meta = _read_meta(cwd, review_id)
    workspace = meta.get("workspace_path")
    if not workspace:
        raise RuntimeError(
            f"materialize_diff: нет workspace_path в meta участника {review_id!r} "
            f"(sandbox не создан или review.json битый)"
        )
    # F-05: запись только внутри sandbox этого review — подмена workspace_path
    # в review.json на внешний путь отклоняется fail-closed.
    sandbox = (Path(cwd) / REVIEW_ROOT / review_id).resolve()
    resolved_workspace = Path(workspace).resolve()
    if not resolved_workspace.is_relative_to(sandbox):
        raise RuntimeError(
            f"materialize_diff: workspace_path вне sandbox участника {review_id!r} "
            f"отклонён (запись вне {REVIEW_ROOT} запрещена): {workspace}"
        )
    target = resolved_workspace / filename
    target.write_text(diff_content, encoding="utf-8")
    return target


def read_participant_activity(cwd: Path, review_id: str) -> dict:
    """Канонический доступ к полям активности (FR-13): last_activity_at и
    last_heartbeat_at из runtime.json участника. Отсутствующий/битый файл →
    None по обоим полям. Новый таймстамп не вводится."""
    activity = {field: None for field in ACTIVITY_FIELDS}
    runtime_path = Path(cwd) / REVIEW_ROOT / review_id / "runtime.json"
    if not runtime_path.exists():
        return activity
    try:
        runtime = json.loads(runtime_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return activity
    for field in ACTIVITY_FIELDS:
        activity[field] = runtime.get(field)
    return activity


def _wave_workers(task_count: int, max_workers: int | None) -> int:
    if max_workers is not None and (
        not isinstance(max_workers, int) or isinstance(max_workers, bool) or max_workers <= 0
    ):
        raise ValueError("max_workers должен быть положительным int")
    return min(max_workers or task_count, PARALLEL_CAP)


def keyed_wave_slots_bound(
    task_keys: Sequence[Hashable], max_workers: int = PARALLEL_CAP,
) -> int:
    """Upper bound of sequential scheduler slots for a keyed wave.

    The bound combines the global worker-cap batches and the deepest FIFO lane.
    """
    workers = _wave_workers(max(len(task_keys), 1), max_workers)
    if not task_keys:
        return 0
    try:
        lane_depth = max(Counter(task_keys).values())
    except TypeError as exc:
        raise ValueError("task_keys должны быть hashable") from exc
    cap_batches = (len(task_keys) + workers - 1) // workers
    return max(lane_depth, cap_batches)


def run_wave(
    tasks: list,
    wave_timeout_sec: float = WAVE_TIMEOUT_SEC,
    max_workers: int | None = None,
    task_keys: Sequence[Hashable] | None = None,
) -> list:
    """Параллельная волна: tasks — список callable → результат.
    Адаптеры — независимые блокирующие процессы; волна ограничена wave timeout.
    max_workers — потолок параллелизма (TD §6.4, PARALLEL_CAP); None — до cap.
    task_keys задаёт FIFO-lane: callable одного key не перекрываются.
    При deadline новые callable не стартуют; ошибка возвращается только
    после quiescence уже запущенных callable.
    """
    if task_keys is not None and len(task_keys) != len(tasks):
        raise ValueError("task_keys должен содержать ровно один ключ на task")
    workers = _wave_workers(max(len(tasks), 1), max_workers)
    if not tasks:
        return []
    keys = list(task_keys) if task_keys is not None else list(range(len(tasks)))
    try:
        lanes: dict[object, deque[tuple[int, Callable[[], object]]]] = {}
        for index, (key, task) in enumerate(zip(keys, tasks)):
            lanes.setdefault(key, deque()).append((index, task))
    except TypeError as exc:
        raise ValueError("task_keys должны быть hashable") from exc

    deadline = _monotonic() + wave_timeout_sec
    results: list[object] = [None] * len(tasks)
    running: dict[Future, object] = {}
    lane_order = deque(lanes)

    def submit_lane(pool: ThreadPoolExecutor, key: object) -> None:
        index, task = lanes[key].popleft()
        running[pool.submit(task)] = (key, index)

    with ThreadPoolExecutor(max_workers=workers) as pool:
        while lane_order and len(running) < workers:
            submit_lane(pool, lane_order.popleft())

        timed_out = False
        task_error: BaseException | None = None
        while running:
            remaining = deadline - _monotonic()
            if remaining <= 0:
                timed_out = True
                break
            done, _ = wait(running, timeout=remaining, return_when=FIRST_COMPLETED)
            if not done:
                timed_out = True
                break
            completed_lanes = []
            for future in done:
                key, index = running.pop(future)
                try:
                    results[index] = future.result()
                except BaseException as exc:
                    task_error = exc
                if lanes[key]:
                    completed_lanes.append(key)

            if task_error is not None:
                wait(running)
                raise task_error

            # Completion may have been observed just before the deadline while
            # result processing crossed it. Never admit a lane tail afterward.
            if _monotonic() >= deadline:
                timed_out = True
                break

            # Round-robin: a completed hot lane joins behind every lane that
            # has not received its first slot yet.
            lane_order.extend(completed_lanes)
            while lane_order and len(running) < workers:
                submit_lane(pool, lane_order.popleft())

        if timed_out:
            # The executor context waits for every running callable. Lane tails
            # remain unscheduled, so caller cleanup cannot race background work.
            wait(running)
            raise TimeoutError("wave deadline истёк; TimeoutError возвращён после quiescence")
    return results


def run_shards(
    shard_callables: list[Callable[[], ShardResult]],
    max_workers: int | None = None,
) -> list[ShardResult]:
    """Параллельно исполнить независимые shard callable с сохранением порядка.

    Один callable владеет всей последовательной очередью своего shard; harness
    не дробит её на задачи и потому не может запустить два turn одного shard
    одновременно. Исключение shard не маскируется и передаётся вызывающему.
    """
    if not shard_callables:
        return []
    if max_workers is not None and (
        not isinstance(max_workers, int) or isinstance(max_workers, bool) or max_workers <= 0
    ):
        raise ValueError("max_workers должен быть положительным int")
    workers = min(max_workers or len(shard_callables), PARALLEL_CAP)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(shard) for shard in shard_callables]
        return [future.result() for future in futures]

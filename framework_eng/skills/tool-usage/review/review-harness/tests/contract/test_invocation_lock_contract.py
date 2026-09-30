"""Контракт сериализации invocation одного review-harness review (HC-16..HC-18).

Проверяется реальный межпроцессный ``flock`` файла
``.review-sandboxes/<review_id>/invocation.lock``.  Платные или сетевые model
CLI не запускаются: сессии подготавливаются и продолжаются через штатные
stub-бинарники Claude, Codex и Kimi.

Контракт:

* mutating-команда одного review при живом holder сразу возвращает diagnostic
  с ``session_busy``, не ожидая освобождения lock;
* lock принадлежит одному review_id: другой review и read-only команды доступны;
* после normal release или смерти holder следующий ход проходит;
* adapter не удаляет lock-файл: inode служит стабильной точкой координации.
"""
from __future__ import annotations

import fcntl
import json
import multiprocessing
import os
import sys
import threading
import time
from contextlib import contextmanager
from pathlib import Path

import pytest

import adapter_contract as ac
import invocation_lock
from tests.conftest import ADAPTER_PATHS, run_process


ALL_FAMILIES = ("claude", "codex", "kimi")
# Холодный импорт adapter-а в devcontainer занимает ~1.3 s; потолок остаётся
# существенно ниже даже краткого invocation timeout, но не зависит от I/O среды.
FAIL_FAST_SEC = 3.0


def _adapter(family: str) -> str:
    return str(ADAPTER_PATHS[family])


def _start(family: str, cwd: Path) -> str:
    started = ac.start_participant(
        _adapter(family), "Контрактный вопрос", [], cwd, timeout_sec=30,
    )
    assert started.ok, started.error
    assert started.review_id
    return started.review_id


def _invoke(adapter: str, cwd: Path, *args: str):
    return run_process([sys.executable, adapter, *args], cwd, timeout=10)


def _mutating_args(command: str, review_id: str) -> list[str]:
    if command == "ask":
        return ["ask", review_id, "--question", "Повторный ход", "--timeout-sec", "30"]
    if command == "debate":
        return [
            "debate", review_id,
            "--issue", "LOCK-1",
            "--finding", "Нужна сериализация invocation.",
            "--position", "Один review допускает только один mutating ход.",
            "--timeout-sec", "30",
        ]
    if command == "sync":
        return ["sync", review_id]
    if command == "close":
        return ["close", review_id]
    raise AssertionError(f"unknown mutating command: {command}")


def _busy_diagnostic(proc, review_id: str) -> str:
    """Проверяет общий typed diagnostic, а не provider-specific текст ошибки."""
    assert proc.returncode != 0, proc.stdout
    diagnostic = proc.stderr.strip() or proc.stdout.strip()
    assert "error_kind: session_busy" in diagnostic.splitlines()
    assert review_id in diagnostic
    return diagnostic


@contextmanager
def _live_lock_holder(lock_path: Path):
    """Процесс теста удерживает тот же advisory lock, что и будущий adapter."""
    lock_path.parent.mkdir(parents=True, exist_ok=True)
    with lock_path.open("a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def _holder_then_exit(lock_path: str, ready, stop, exit_code: int) -> None:
    """Отдельный holder: normal release либо внезапная смерть процесса."""
    with Path(lock_path).open("a+", encoding="utf-8") as lock_file:
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
        ready.set()
        stop.wait(timeout=5)
        if exit_code:
            os._exit(exit_code)


@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("command", ("ask", "debate", "sync", "close"))
def test_hc16_live_invocation_lock_fails_fast_per_review_without_blocking_reads_or_other_review(
        stub_env, family, command):
    """HC-16: один live holder отклоняет все mutating операции только своего review.

    ``status`` остаётся доступен, второй review не блокируется, а после release
    исходная команда проходит. Это не проверка внутреннего helper-а: каждый ход
    вызывается отдельным adapter process.
    """
    cwd = stub_env["cwd"]
    adapter = _adapter(family)
    blocked_review = _start(family, cwd)
    independent_review = _start(family, cwd)
    review_dir = cwd / ".review-sandboxes" / blocked_review
    lock_path = review_dir / "invocation.lock"
    before_meta = (review_dir / "review.json").read_bytes()

    with _live_lock_holder(lock_path):
        readonly = _invoke(adapter, cwd, "status", blocked_review)
        assert readonly.returncode == 0, readonly.stderr
        assert json.loads(readonly.stdout)["review_id"] == blocked_review

        # Другой review_id не конфликтует: его invocation выполняется через stub CLI.
        other = _invoke(adapter, cwd, *_mutating_args("ask", independent_review))
        assert other.returncode == 0, other.stderr
        invocation_log = stub_env["state_dir"] / "invocations.jsonl"
        before_invocations = invocation_log.read_bytes()

        started_at = time.monotonic()
        busy = _invoke(adapter, cwd, *_mutating_args(command, blocked_review))
        elapsed = time.monotonic() - started_at

        _busy_diagnostic(busy, blocked_review)
        assert elapsed < FAIL_FAST_SEC, f"{family}/{command} waited {elapsed:.3f}s for invocation lock"
        assert lock_path.exists(), "adapter must not unlink the stable invocation lock file"
        assert (review_dir / "review.json").read_bytes() == before_meta
        assert invocation_log.read_bytes() == before_invocations, "busy invocation must not reach the model CLI"

    recovered = _invoke(adapter, cwd, *_mutating_args(command, blocked_review))
    assert recovered.returncode == 0, recovered.stderr
    assert review_dir.is_dir(), "close must leave a per-review tombstone directory"
    assert lock_path.exists(), "successful invocation must retain the lock file"
    if command == "close":
        assert [entry.name for entry in review_dir.iterdir()] == ["invocation.lock"]


@pytest.mark.parametrize("family", ALL_FAMILIES)
@pytest.mark.parametrize("exit_code", (0, 23), ids=("release", "death"))
def test_hc17_invocation_lock_is_released_when_holder_exits(stub_env, family, exit_code):
    """HC-17: kernel освобождает lock и после normal exit, и после смерти holder."""
    cwd = stub_env["cwd"]
    adapter = _adapter(family)
    review_id = _start(family, cwd)
    lock_path = cwd / ".review-sandboxes" / review_id / "invocation.lock"
    context = multiprocessing.get_context("fork")
    ready = context.Event()
    stop = context.Event()
    holder = context.Process(
        target=_holder_then_exit,
        args=(str(lock_path), ready, stop, exit_code),
    )
    holder.start()
    try:
        assert ready.wait(timeout=3), "lock holder did not become ready"
        busy = _invoke(adapter, cwd, *_mutating_args("ask", review_id))
        _busy_diagnostic(busy, review_id)

        stop.set()
        holder.join(timeout=3)
        assert not holder.is_alive(), "lock holder did not exit"
        assert holder.exitcode == exit_code

        recovered = _invoke(adapter, cwd, *_mutating_args("ask", review_id))
        assert recovered.returncode == 0, recovered.stderr
        assert lock_path.exists(), "adapter must retain the lock file after holder exit"
    finally:
        if holder.is_alive():
            holder.terminate()
            holder.join(timeout=3)


def test_hc18_same_process_threads_for_different_review_ids_overlap(tmp_path):
    """HC-18: process-wide helper state не сериализует независимые review_id.

    Внешние adapter process уже покрыты HC-16. Этот тест нужен отдельно,
    потому что helper может ошибочно удерживать process-global mutex на всё
    тело контекста и тем самым блокировать две независимые session в threads.
    """
    review_a = tmp_path / ".review-sandboxes" / "review-a"
    review_b = tmp_path / ".review-sandboxes" / "review-b"
    review_a.mkdir(parents=True)
    review_b.mkdir(parents=True)
    first_ready = threading.Event()
    second_ready = threading.Event()
    release = threading.Event()
    errors: list[BaseException] = []

    def hold(review_dir: Path, review_id: str, ready: threading.Event) -> None:
        try:
            with invocation_lock.invocation_lock(review_dir, review_id=review_id, action="ask"):
                ready.set()
                release.wait(timeout=3)
        except BaseException as exc:  # assertion below reports worker failure
            errors.append(exc)

    first = threading.Thread(target=hold, args=(review_a, "review-a", first_ready))
    second = threading.Thread(target=hold, args=(review_b, "review-b", second_ready))
    first.start()
    try:
        assert first_ready.wait(timeout=1), "first same-process lock holder did not start"
        second.start()
        assert second_ready.wait(timeout=1), "different review_id was serialized by a process-global lock"
    finally:
        release.set()
        first.join(timeout=3)
        second.join(timeout=3)

    assert not first.is_alive()
    assert not second.is_alive()
    assert not errors
    assert (review_a / "invocation.lock").exists()
    assert (review_b / "invocation.lock").exists()

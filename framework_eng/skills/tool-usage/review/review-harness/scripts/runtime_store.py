"""POSIX-safe storage primitives for review runtime state and event logs."""
from __future__ import annotations

import json
import os
import tempfile
import threading
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Callable, Iterator

if os.name != "posix":
    raise RuntimeError("review-harness runtime storage requires POSIX advisory locks")

try:
    import fcntl
except ImportError as exc:
    raise RuntimeError("review-harness runtime storage requires fcntl.flock") from exc


_LOCK_REGISTRY_GUARD = threading.Lock()
_LOCKS_BY_PATH: dict[Path, threading.RLock] = {}


def _thread_lock_for(lock_path: Path) -> threading.RLock:
    resolved_lock_path = lock_path.resolve()
    with _LOCK_REGISTRY_GUARD:
        lock = _LOCKS_BY_PATH.get(resolved_lock_path)
        if lock is None:
            lock = threading.RLock()
            _LOCKS_BY_PATH[resolved_lock_path] = lock
        return lock


@contextmanager
def runtime_lock(review_dir: Path) -> Iterator[None]:
    """Serialize runtime state and event-log mutations for one review directory."""
    lock_path = review_dir / "runtime.json.lock"
    with _thread_lock_for(lock_path):
        with lock_path.open("a+", encoding="utf-8") as lock_file:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def read_json(path: Path, *, default: Any) -> Any:
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def mutate_json(
    review_dir: Path,
    path: Path,
    *,
    default: Any,
    mutator: Callable[[Any], Any],
) -> Any:
    """Apply one JSON state transition while holding the review runtime lock."""
    with runtime_lock(review_dir):
        updated = mutator(read_json(path, default=default))
        write_json_atomically(path, updated)
        return updated


def write_json_atomically(path: Path, payload: Any) -> None:
    tmp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            mode="w",
            encoding="utf-8",
            dir=path.parent,
            prefix=f".{path.name}.",
            suffix=".tmp",
            delete=False,
        ) as tmp_file:
            tmp_path = Path(tmp_file.name)
            json.dump(payload, tmp_file, ensure_ascii=False, indent=2)
            tmp_file.flush()
            os.fsync(tmp_file.fileno())
        os.replace(tmp_path, path)
    except BaseException:
        if tmp_path is not None:
            try:
                tmp_path.unlink(missing_ok=True)
            except OSError:
                pass
        raise


def append_json_line(path: Path, event: Any) -> None:
    encoded_event = json.dumps(event, ensure_ascii=False)
    with path.open("a", encoding="utf-8") as event_file:
        event_file.write(encoded_event + "\n")
        event_file.flush()
        os.fsync(event_file.fileno())

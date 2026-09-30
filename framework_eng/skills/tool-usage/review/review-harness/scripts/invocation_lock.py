"""Fail-fast per-review locks for mutating adapter lifecycle commands.

The lock is deliberately separate from ``runtime.json.lock``: an adapter holds
this lock for its whole provider/session mutation, then takes the runtime lock
only for individual state publications.  That order prevents concurrent
``ask``/``debate``/``sync``/``close`` calls from resuming one provider session
at the same time while keeping read-only status commands non-blocking.
"""
from __future__ import annotations

import os
import re
import shutil
from contextlib import contextmanager
from pathlib import Path
from typing import Callable, Iterator

if os.name != "posix":
    raise RuntimeError("review-harness invocation locking requires POSIX advisory locks")

try:
    import fcntl
except ImportError as exc:
    raise RuntimeError("review-harness invocation locking requires fcntl.flock") from exc


INVOCATION_LOCK_NAME = "invocation.lock"
SAFE_REVIEW_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")


class SessionBusyError(RuntimeError):
    """A concurrent mutating lifecycle command already owns this review."""


def validate_review_id(review_id: str) -> str:
    """Accept one conservative, printable filesystem component only."""
    if not isinstance(review_id, str) or not SAFE_REVIEW_ID_RE.fullmatch(review_id):
        raise ValueError(
            "review_id must be one safe path component "
            "([A-Za-z0-9][A-Za-z0-9._-]{0,127})"
        )
    return review_id


def review_dir_for_id(review_root: Path, review_id: str) -> Path:
    """Resolve a review id to a non-symlink direct child without filesystem writes."""
    validate_review_id(review_id)
    root = Path(review_root)
    if root.is_symlink():
        raise ValueError(f"review root must not be a symlink: {root}")
    resolved_root = root.resolve(strict=False)
    candidate = root / review_id
    if candidate.is_symlink():
        raise ValueError(f"review directory must not be a symlink: {candidate}")
    resolved_candidate = candidate.resolve(strict=False)
    if resolved_candidate.parent != resolved_root:
        raise ValueError("review_id does not resolve to a direct child of review root")
    return candidate


def lock_path(review_dir: Path) -> Path:
    return review_dir / INVOCATION_LOCK_NAME


def prepare_start_directory(review_dir: Path, *, allow_lock_tombstone: bool) -> None:
    """Create a start directory or reuse an explicitly-owned failed-start tombstone.

    Reuse is deliberately narrower than ``exist_ok=True``: only a directory
    containing the stable regular lock file and no payload is admissible.  The
    caller may enable it only when the review id was supplied by its
    coordinator and is therefore already published for eventual cleanup.
    """
    try:
        review_dir.mkdir(parents=True, exist_ok=False)
        return
    except FileExistsError:
        if not allow_lock_tombstone or review_dir.is_symlink() or not review_dir.is_dir():
            raise
    entries = list(review_dir.iterdir())
    stable_lock = lock_path(review_dir)
    if (entries != [stable_lock] or stable_lock.is_symlink()
            or not stable_lock.is_file()):
        raise FileExistsError(
            f"review sandbox already exists and is not a lock-only tombstone: {review_dir}"
        )


@contextmanager
def invocation_lock(review_dir: Path, *, review_id: str, action: str) -> Iterator[None]:
    """Take the stable lock without waiting; process death releases ``flock``."""
    path = lock_path(review_dir)
    with path.open("a+", encoding="utf-8") as lock_file:
        try:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            raise SessionBusyError(
                f"session_busy: review_id={review_id!r} action={action!r}; "
                "another mutating adapter invocation is active"
            ) from exc
        try:
            yield
        finally:
            fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)


def clear_sandbox_contents_preserving_lock(
    review_dir: Path,
    *,
    remove_tree: Callable[..., None] = shutil.rmtree,
) -> None:
    """Delete sandbox payload while its stable lock inode remains held.

    ``close`` calls this before releasing ``invocation.lock``.  Removing the
    lock earlier could let a new process create a different inode and bypass
    the still-active advisory lock.
    The otherwise-empty review directory remains as a tombstone.  A lifecycle
    adapter never unlinks its stable lock: removing it after release opens an
    inode-replacement race in which a third process can bypass a new holder.
    """
    stable_lock = lock_path(review_dir)
    for child in review_dir.iterdir():
        if child == stable_lock:
            continue
        if child.is_dir() and not child.is_symlink():
            remove_tree(child)
        else:
            child.unlink()

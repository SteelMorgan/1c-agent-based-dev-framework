"""Shared assertions for the consilium double-cleanup checkpoint."""
from __future__ import annotations

from pathlib import Path

from adapter_contract import is_lock_only_tombstone


def directory_absent_or_empty(path: Path) -> bool:
    """A session root is clean only when absent or without children."""
    return not path.exists() or (path.is_dir() and not any(path.iterdir()))


def review_root_has_only_closed_tombstones(path: Path) -> bool:
    """Accept absent/empty review root or canonical lock-only tombstones only."""
    if not path.exists():
        return True
    if path.is_symlink() or not path.is_dir():
        return False
    return all(is_lock_only_tombstone(child) for child in path.iterdir())


def assert_cleanup_checkpoint(workdir: Path) -> None:
    """Fail with operator-facing evidence when either cleanup contour is live."""
    session_root = workdir / ".consilium-sessions"
    review_root = workdir / ".review-sandboxes"
    assert directory_absent_or_empty(session_root), (
        f"consilium sessions remain after cleanup: {session_root}"
    )
    assert review_root_has_only_closed_tombstones(review_root), (
        f"live or noncanonical review sandbox remains after cleanup: {review_root}"
    )
